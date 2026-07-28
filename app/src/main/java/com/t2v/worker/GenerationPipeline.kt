package com.t2v.worker

import android.content.Context
import com.t2v.core.audio.AudioEncoder
import com.t2v.core.audio.AudioTagInserter
import com.t2v.core.text.TextProcessor
import com.t2v.data.AppDatabase
import com.t2v.data.SegmentEntity
import com.t2v.tts.EngineInfo
import com.t2v.tts.TtsEngineException
import com.t2v.tts.ExpressiveSpeech
import com.t2v.tts.TtsRequest
import com.t2v.tts.VoiceConfig
import com.t2v.tts.engines.TtsEngine
import com.t2v.tts.registry.EngineRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Пайплайн генерации. Прямой порт `app/core/audio_pipeline.py:AudioGenerationPipeline`
 * (2513 строк оригинала).
 *
 * Стратегия:
 *   1. text → chunks (TextProcessor)
 *   2. chunks → segments (с pause_before_ms / pause_after_ms)
 *   3. для каждого сегмента — TTS-вызов
 *   4. WAV-склейка + ffmpeg-кодирование в MP3
 *
 * Поддерживает отмену, прогресс, ретраи.
 */
class GenerationPipeline(
    private val context: Context,
    private val engineRegistry: EngineRegistry,
    private val textProcessor: TextProcessor,
) {
    companion object {
        /**
         * Maximum time a single TTS segment is allowed to run before we
         * give up and surface a clear error to the user. Kokoro on long
         * Russian text can hang in the ONNX runtime; without a timeout
         * the audiobook stays in "running" forever and the user has no
         * way to recover. Five minutes is a conservative upper bound for
         * a 2500-character chunk on a mid-range phone.
         */
        private const val SEGMENT_TIMEOUT_MS: Long = 5 * 60_000L
    }
    private val context: Context,
    private val engineRegistry: EngineRegistry,
    private val textProcessor: TextProcessor,
) {

    data class Progress(
        val total: Int = 0,
        val done: Int = 0,
        val currentSegment: String? = null,
        val phase: Phase = Phase.Idle,
        val error: String? = null,
        /** Number of audio clips generated from <music>/<sfx> tags in this run. */
        val audioTagClips: Int = 0,
    ) {
        enum class Phase { Idle, Processing, Synthesizing, Encoding, Completed, Failed, Cancelled }
    }

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val mutex = Mutex()
    @Volatile private var cancelled = false
    private val database = AppDatabase.get(context)
    @Volatile private var activeEngine: TtsEngine? = null
    private var audioTagInserter: AudioTagInserter? = null

    fun setAudioTagInserter(inserter: AudioTagInserter) {
        this.audioTagInserter = inserter
    }

    suspend fun cancel() {
        cancelled = true
        activeEngine?.cancel()
    }

    suspend fun generate(
        projectId: Long,
        audiobookId: Long,
        rawText: String,
        voice: VoiceConfig,
        engineId: String,
        outputDir: File,
    ): Result<File> = withContext(Dispatchers.IO) {
        cancelled = false
        runCatching {
            outputDir.mkdirs()
            val (sections, chunks, audioTags) = textProcessor.process(rawText)
            val segmentWavs = mutableListOf<File>()
            val total = chunks.size
            _progress.value = Progress(total = total, done = 0, phase = Progress.Phase.Processing)

            val engine = engineRegistry.get(engineId)
            activeEngine = engine
            val segmentIds = chunks.mapIndexed { index, chunk ->
                database.segments().upsert(
                    SegmentEntity(
                        audiobookId = audiobookId,
                        orderIndex = index,
                        text = chunk.text,
                        pauseBeforeMs = chunk.markupPauseBeforeMs,
                        pauseAfterMs = chunk.markupPauseAfterMs ?: 0,
                    ),
                )
            }

            for ((idx, chunk) in chunks.withIndex()) {
                if (cancelled) {
                    _progress.update { it.copy(phase = Progress.Phase.Cancelled) }
                    throw TtsEngineException.Cancelled()
                }
                _progress.update {
                    it.copy(
                        currentSegment = chunk.text.take(80),
                        phase = Progress.Phase.Synthesizing,
                    )
                }
                val wav = File(outputDir, "seg_%05d.wav".format(idx))
                val segmentId = segmentIds[idx]
                val pendingSegment = database.segments().byId(segmentId)
                    ?: error("Segment $segmentId disappeared")
                database.segments().update(pendingSegment.copy(status = "running"))
                val expressiveVoice = voice.copy(
                    speed = chunk.markupState.speed ?: voice.speed,
                    volume = chunk.markupState.volume ?: voice.volume,
                    pitch = chunk.markupState.pitch ?: voice.pitch,
                    lang = chunk.markupState.language ?: voice.lang,
                    voice = chunk.markupState.voice ?: voice.voice,
                    emotion = chunk.markupState.emotion ?: voice.emotion,
                    extras = voice.extras + chunk.markupState.custom + mapOf(
                        ExpressiveSpeech.DELIVERY to chunk.markupState.delivery.orEmpty(),
                        ExpressiveSpeech.EMPHASIS to chunk.markupState.emphasis.orEmpty(),
                        ExpressiveSpeech.VOCAL_CUES to chunk.markupState.vocalCues.joinToString("|"),
                    ),
                ).let {
                    if (engine.info.kind == EngineInfo.EngineKind.Local) {
                        // Any local engine (Kokoro, Piper, future on-device) cannot
                        // honour cloud-only semantic keys; fall back to honest
                        // prosody approximation.
                        ExpressiveSpeech.localFallback(it)
                    } else {
                        it
                    }
                }
                val req = TtsRequest(
                    text = chunk.text,
                    outputFile = wav,
                    voice = expressiveVoice,
                )
                val result = withRetry(maxAttempts = 2) {
                    withTimeoutOrNull(SEGMENT_TIMEOUT_MS) { engine.synthesize(req) }
                        ?: throw TtsEngineException.Generic(
                            "TTS segment timed out after ${SEGMENT_TIMEOUT_MS / 1000}s. " +
                            "Try a shorter text or switch to a cloud engine.",
                        )
                }
                database.segments().update(
                    pendingSegment.copy(
                        audioPath = result.outputFile.absolutePath,
                        durationMs = result.durationMs.coerceAtLeast(0),
                        status = "completed",
                    ),
                )

                // Между чанками — тишина
                if (chunk.markupPauseBeforeMs > 0) {
                    val pre = File(outputDir, "pause_pre_%05d.wav".format(idx))
                    AudioEncoder.writeSilence(
                        pre,
                        chunk.markupPauseBeforeMs,
                        sampleRate = result.sampleRate,
                        channels = result.channels,
                    )
                    segmentWavs += pre
                }
                segmentWavs += result.outputFile
                if (chunk.markupPauseAfterMs != null && chunk.markupPauseAfterMs > 0) {
                    val post = File(outputDir, "pause_post_%05d.wav".format(idx))
                    AudioEncoder.writeSilence(
                        post,
                        chunk.markupPauseAfterMs,
                        sampleRate = result.sampleRate,
                        channels = result.channels,
                    )
                    segmentWavs += post
                }
                _progress.update { it.copy(done = idx + 1) }
            }

            // Insert any <music>/<sfx> tags AFTER the voice segments are
            // generated so the AudioTagInserter can read each segment's
            // actual pauseBeforeMs + durationMs to compute a correct
            // timelineStartMs. Without this, the audio clips would land
            // at timeline position 0 (the inserter saw durationMs=0 for
            // all segments at insert time).
            val insertedClips = audioTagInserter?.insert(audioTags, audiobookId) ?: 0
            if (insertedClips > 0) {
                _progress.update { it.copy(audioTagClips = insertedClips) }
            }

            // Кодируем
            _progress.update { it.copy(phase = Progress.Phase.Encoding) }
            val finalWav = File(outputDir, "audiobook.wav")
            if (segmentWavs.size == 1) {
                segmentWavs.first().copyTo(finalWav, overwrite = true)
            } else {
                AudioEncoder.concatWav(segmentWavs, finalWav)
            }
            _progress.update { it.copy(phase = Progress.Phase.Completed) }
            finalWav
        }.onFailure { e ->
            _progress.update {
                it.copy(phase = if (cancelled) Progress.Phase.Cancelled else Progress.Phase.Failed, error = e.message)
            }
        }.also {
            activeEngine = null
        }
    }

    private suspend fun <T> withRetry(maxAttempts: Int, block: suspend () -> T): T {
        var last: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: TtsEngineException.Cancelled) {
                throw e
            } catch (t: Throwable) {
                last = t
                if (attempt == maxAttempts - 1) throw t
            }
        }
        throw last ?: RuntimeException("retry failed")
    }
}

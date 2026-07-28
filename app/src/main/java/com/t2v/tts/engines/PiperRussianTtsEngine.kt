package com.t2v.tts.engines

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.t2v.core.audio.AudioChunk
import com.t2v.core.audio.AudioEncoder
import com.t2v.tts.EngineInfo
import com.t2v.tts.TtsEngineException
import com.t2v.tts.TtsRequest
import com.t2v.tts.TtsResult
import com.t2v.tts.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Downloadable Piper/VITS models converted and published by sherpa-onnx. */
class PiperRussianTtsEngine(
    private val modelsDir: File,
) : TtsEngine {
    override val info: EngineInfo = ENGINE_INFO
    private var activeVoiceId: String? = null
    private var tts: OfflineTts? = null

    override fun isAvailable(): Boolean = installedVoices().isNotEmpty()

    override suspend fun listVoices(): List<VoiceInfo> = installedVoices().map { voice ->
        VoiceInfo(
            id = voice.id,
            displayName = voice.displayName,
            language = voice.language,
            gender = voice.gender,
            engineId = info.id,
            isLocal = true,
            sampleRate = 22050,
        )
    }

    override suspend fun preload(): Unit = Unit

    override suspend fun synthesize(request: TtsRequest): TtsResult = withContext(Dispatchers.IO) {
        val installed = installedVoices()
        val voice = installed.firstOrNull { it.id == request.voice.voice }
            ?: installed.firstOrNull()
            ?: throw TtsEngineException.NotInstalled(info.id)
        val runtime = ensureTts(voice)
        val audio = runtime.generate(
            text = request.text,
            sid = 0,
            speed = request.voice.speed.toFloat().coerceIn(0.5f, 2.0f),
        )
        if (audio.samples.isEmpty()) {
            throw TtsEngineException.Generic("${voice.displayName} returned empty audio")
        }
        val volume = request.voice.volume.coerceIn(0.0, 4.0).toFloat()
        val pcm = ShortArray(audio.samples.size) { index ->
            (audio.samples[index] * volume * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        request.outputFile.parentFile?.mkdirs()
        AudioEncoder.writeWav(
            request.outputFile,
            AudioChunk(samples = pcm, sampleRate = audio.sampleRate, channels = 1),
        )
        TtsResult(
            outputFile = request.outputFile,
            sampleRate = audio.sampleRate,
            channels = 1,
            durationMs = ((pcm.size * 1000L) / audio.sampleRate).toInt(),
            bytesWritten = request.outputFile.length(),
        )
    }

    override suspend fun cancel(): Unit = Unit

    override suspend fun close(): Unit {
        tts?.release()
        tts = null
        activeVoiceId = null
    }

    private fun ensureTts(voice: RussianVoice): OfflineTts {
        if (activeVoiceId == voice.id) tts?.let { return it }
        tts?.release()
        val root = findModelRoot(File(modelsDir, voice.id))
            ?: throw TtsEngineException.NotInstalled(voice.id)
        val model = root.walkTopDown().firstOrNull { it.isFile && it.extension == "onnx" }
            ?: throw TtsEngineException.NotInstalled(voice.id)
        val tokens = root.walkTopDown().firstOrNull { it.isFile && it.name == "tokens.txt" }
            ?: throw TtsEngineException.NotInstalled(voice.id)
        val dataDir = root.walkTopDown().firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }
            ?: throw TtsEngineException.NotInstalled(voice.id)
        val vits = OfflineTtsVitsModelConfig(
            model = model.absolutePath,
            tokens = tokens.absolutePath,
            dataDir = dataDir.absolutePath,
        )
        return OfflineTts(
            OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = vits,
                    numThreads = 4,
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
            ),
        ).also {
            tts = it
            activeVoiceId = voice.id
        }
    }

    private fun installedVoices(): List<RussianVoice> =
        RUSSIAN_VOICES.filter { findModelRoot(File(modelsDir, it.id)) != null }

    private fun findModelRoot(directory: File): File? {
        if (!directory.isDirectory) return null
        val hasModel = directory.walkTopDown().any { it.isFile && it.extension == "onnx" }
        val hasTokens = directory.walkTopDown().any { it.isFile && it.name == "tokens.txt" }
        val hasEspeak = directory.walkTopDown().any { it.isDirectory && it.name == "espeak-ng-data" }
        return directory.takeIf { hasModel && hasTokens && hasEspeak }
    }

    data class RussianVoice(
        val id: String,
        val displayName: String,
        val gender: String,
        val language: String,
        val archiveUrl: String,
        val approximateSizeBytes: Long = 65_000_000L,
    )

    companion object {
        val RUSSIAN_VOICES = listOf(
            russianVoice("irina", "Ирина", "female"),
            russianVoice("denis", "Денис", "male"),
            russianVoice("dmitri", "Дмитрий", "male"),
            russianVoice("ruslan", "Руслан", "male"),
            piperVoice(
                id = "en-us-amy",
                displayName = "Amy",
                gender = "female",
                language = "en-US",
                archiveName = "vits-piper-en_US-amy-medium.tar.bz2",
            ),
            piperVoice(
                id = "en-gb-cori",
                displayName = "Cori",
                gender = "female",
                language = "en-GB",
                archiveName = "vits-piper-en_GB-cori-medium.tar.bz2",
            ),
            piperVoice(
                id = "en-gb-jenny",
                displayName = "Jenny (en-GB)",
                gender = "female",
                language = "en-GB",
                archiveName = "vits-piper-en_GB-jenny_dioco-medium.tar.bz2",
            ),
            piperVoice(
                id = "en-us-kathleen",
                displayName = "Kathleen (en-US)",
                gender = "female",
                language = "en-US",
                archiveName = "vits-piper-en_US-kathleen-low.tar.bz2",
                approximateSizeBytes = 35_000_000L,
            ),
            piperVoice(
                id = "en-us-lessac",
                displayName = "Lessac (en-US)",
                gender = "female",
                language = "en-US",
                archiveName = "vits-piper-en_US-lessac-medium.tar.bz2",
            ),
            piperVoice(
                id = "en-us-ryan",
                displayName = "Ryan (en-US)",
                gender = "male",
                language = "en-US",
                archiveName = "vits-piper-en_US-ryan-medium.tar.bz2",
            ),
            piperVoice(
                id = "de-de-thorsten",
                displayName = "Thorsten (de-DE)",
                gender = "male",
                language = "de-DE",
                archiveName = "vits-piper-de_DE-thorsten-medium.tar.bz2",
            ),
            piperVoice(
                id = "de-de-kerstin",
                displayName = "Kerstin (de-DE)",
                gender = "female",
                language = "de-DE",
                archiveName = "vits-piper-de_DE-kerstin-low.tar.bz2",
                approximateSizeBytes = 35_000_000L,
            ),
            piperVoice(
                id = "de-de-eva_k",
                displayName = "Eva K (de-DE)",
                gender = "female",
                language = "de-DE",
                archiveName = "vits-piper-de_DE-eva_k-x_low.tar.bz2",
                approximateSizeBytes = 28_000_000L,
            ),
            piperVoice(
                id = "de-at-magda",
                displayName = "Magda (de-AT)",
                gender = "female",
                language = "de-AT",
                archiveName = "vits-piper-de_AT-magda-medium.tar.bz2",
            ),
            piperVoice(
                id = "fr-fr-siwis",
                displayName = "Siwis (fr-FR)",
                gender = "female",
                language = "fr-FR",
                archiveName = "vits-piper-fr_FR-siwis-medium.tar.bz2",
            ),
            piperVoice(
                id = "fr-fr-tom",
                displayName = "Tom (fr-FR)",
                gender = "male",
                language = "fr-FR",
                archiveName = "vits-piper-fr_FR-tom-medium.tar.bz2",
            ),
            piperVoice(
                id = "es-es-carlfm",
                displayName = "Carlos (es-ES)",
                gender = "male",
                language = "es-ES",
                archiveName = "vits-piper-es_ES-carlfm-x_low.tar.bz2",
                approximateSizeBytes = 30_000_000L,
            ),
            piperVoice(
                id = "es-mx-ald",
                displayName = "Ald (es-MX)",
                gender = "male",
                language = "es-MX",
                archiveName = "vits-piper-es_MX-ald-medium.tar.bz2",
            ),
            piperVoice(
                id = "it-it-riccardo",
                displayName = "Riccardo (it-IT)",
                gender = "male",
                language = "it-IT",
                archiveName = "vits-piper-it_IT-riccardo-x_low.tar.bz2",
                approximateSizeBytes = 30_000_000L,
            ),
            piperVoice(
                id = "zh-cn-huayan",
                displayName = "Huayan (zh-CN)",
                gender = "female",
                language = "zh-CN",
                archiveName = "vits-piper-zh_CN-huayan-medium.tar.bz2",
            ),
            piperVoice(
                id = "ja-jp-kai",
                displayName = "Kai (ja-JP)",
                gender = "male",
                language = "ja-JP",
                archiveName = "vits-piper-ja_JP-kai-medium.tar.bz2",
            ),
            piperVoice(
                id = "hi-in-priyamvada",
                displayName = "Priyamvada (hi-IN)",
                gender = "female",
                language = "hi-IN",
                archiveName = "vits-piper-hi_IN-priyamvada-medium.tar.bz2",
            ),
            piperVoice(
                id = "bn-in-ani_jora",
                displayName = "Ani Jora (bn-IN)",
                gender = "female",
                language = "bn-IN",
                archiveName = "vits-piper-bn_IN-anim_jora-medium.tar.bz2",
            ),
            piperVoice(
                id = "ar-jo-kareem",
                displayName = "Kareem (ar)",
                gender = "male",
                language = "ar",
                archiveName = "vits-piper-ar_JO-kareem-medium.tar.bz2",
            ),
            piperVoice(
                id = "ko-ks-kss",
                displayName = "KSS (ko-KR)",
                gender = "female",
                language = "ko-KR",
                archiveName = "vits-piper-ko_KR-kss-medium.tar.bz2",
            ),
        )

        val ENGINE_INFO = EngineInfo(
            id = "piper_ru",
            displayName = "Piper/VITS (на устройстве)",
            kind = EngineInfo.EngineKind.Local,
            supportsLocal = true,
        )

        private fun russianVoice(id: String, name: String, gender: String): RussianVoice {
            val archiveName = "vits-piper-ru_RU-$id-medium.tar.bz2"
            return RussianVoice(
                id = id,
                displayName = name,
                gender = gender,
                language = "ru-RU",
                archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archiveName",
            )
        }

        private fun piperVoice(
            id: String,
            displayName: String,
            gender: String,
            language: String,
            archiveName: String,
            approximateSizeBytes: Long = 65_000_000L,
        ) = RussianVoice(
            id = id,
            displayName = displayName,
            gender = gender,
            language = language,
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archiveName",
            approximateSizeBytes = approximateSizeBytes,
        )
    }
}

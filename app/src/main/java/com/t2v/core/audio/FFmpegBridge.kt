package com.t2v.core.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Тонкая обёртка вокруг FFmpeg для Android.
 *
 * Используется для финального кодирования PCM→M4A/WAV,
 * склейки сегментов, наложения музыки с ducking через sidechaincompress,
 * обрезки тишины в начале/конце и экспорта.
 *
 * Реализация: проверенный CI нативный бинарник FFmpeg из nativeLibraryDir.
 *
 * Заменяет `com.arthenica.ffmpegkit` (проект Arthenica был удалён с Maven Central)
 * на нативный бинарник FFmpeg, который мы запускаем через Runtime.
 */
object FFmpegBridge {

    /** Returns the read-only executable packaged in the APK native library directory. */
    @Volatile private var ffmpegPath: String? = null

    private fun codecFor(format: String): String = when (format.lowercase()) {
        "m4a", "aac" -> "aac"
        "mp3" -> "libmp3lame"
        "wav" -> "pcm_s16le"
        else -> error("Unsupported audio format in this FFmpeg build: $format")
    }

    suspend fun ensure(context: Context): String = withContext(Dispatchers.IO) {
        ffmpegPath?.let { return@withContext it }
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        require(abi == "arm64-v8a") { "FFmpeg is not available for device ABI: $abi" }
        val executable = File(context.applicationInfo.nativeLibraryDir, "libffmpeg_exec.so")
        require(executable.isFile && executable.length() > 1_000_000) {
            "Verified FFmpeg executable is missing from the APK"
        }
        val probe = ProcessBuilder(executable.absolutePath, "-version")
            .redirectErrorStream(true)
            .start()
        val version = probe.inputStream.bufferedReader().readLine().orEmpty()
        require(probe.waitFor() == 0 && version.startsWith("ffmpeg version")) {
            "Packaged FFmpeg failed its startup check"
        }
        ffmpegPath = executable.absolutePath
        executable.absolutePath
    }

    /**
     * Закодировать WAV → [format] (m4a/aac/wav).
     */
    suspend fun encode(
        context: Context,
        wav: File,
        output: File,
        format: String,
        bitrate: String = "192k",
    ): File = withContext(Dispatchers.IO) {
        val codec = codecFor(format)
        val ext = if (format.equals("wav", true)) "wav" else format
        val target = if (output.extension.isEmpty()) {
            File(output.parentFile, "${output.nameWithoutExtension}.$ext")
        } else output
        val cmd = listOf(
            "-y", "-i", wav.absolutePath,
            "-c:a", codec,
            "-b:a", bitrate,
            target.absolutePath,
        )
        run(context, cmd)
        target
    }

    /**
     * Склеить несколько WAV в один.
     */
    suspend fun concat(
        context: Context,
        wavs: List<File>,
        output: File,
        format: String = "m4a",
        bitrate: String = "192k",
    ): File = withContext(Dispatchers.IO) {
        val listFile = File(output.parentFile, "concat_${System.currentTimeMillis()}.txt")
        listFile.bufferedWriter().use { w ->
            for (f in wavs) {
                val safe = f.absolutePath.replace("'", "'\\''")
                w.write("file '$safe'\n")
            }
        }
        val target = File(output.parentFile, "${output.nameWithoutExtension}.${format}")
        val cmd = listOf(
            "-y", "-f", "concat", "-safe", "0",
            "-i", listFile.absolutePath,
            "-c:a", codecFor(format),
            "-b:a", bitrate,
            target.absolutePath,
        )
        try {
            run(context, cmd)
        } finally {
            listFile.delete()
        }
        target
    }

    /**
     * Наложить музыку с sidechain ducking.
     */
    suspend fun applyMusicDucking(
        context: Context,
        voice: File,
        music: File,
        output: File,
        voiceVolumeDb: Double = 0.0,
        musicVolumeDb: Double = -12.0,
        duckingDb: Double = -6.0,
        format: String = "m4a",
        bitrate: String = "192k",
    ): File = withContext(Dispatchers.IO) {
        val target = File(output.parentFile, "${output.nameWithoutExtension}.${format}")
        val filter = "[0:a]volume=${"%.2f".format(java.util.Locale.US, voiceVolumeDb)}dB[voice];" +
            "[1:a]volume=${"%.2f".format(java.util.Locale.US, musicVolumeDb)}dB[music];" +
            "[voice][music]sidechaincompress=threshold=0.05:ratio=8:attack=20:release=1000:" +
            "makeup=${"%.2f".format(java.util.Locale.US, -duckingDb)}[ducked];" +
            "[ducked]aresample=44100[out]"
        val cmd = listOf(
            "-y",
            "-i", voice.absolutePath,
            "-stream_loop", "-1",
            "-i", music.absolutePath,
            "-filter_complex", filter,
            "-map", "[out]",
            "-c:a", codecFor(format),
            "-b:a", bitrate,
            "-shortest",
            target.absolutePath,
        )
        run(context, cmd)
        target
    }

    /**
     * Обрезать тишину в начале и в конце.
     */
    suspend fun trimSilence(
        context: Context,
        input: File,
        output: File,
        thresholdDb: Double = -40.0,
    ): File = withContext(Dispatchers.IO) {
        val filter = "silenceremove=start_periods=1:start_silence=0:" +
            "start_threshold=${thresholdDb}dB:stop_periods=-1:stop_silence=0:" +
            "stop_threshold=${thresholdDb}dB"
        run(context, listOf("-y", "-i", input.absolutePath, "-af", filter, output.absolutePath))
        output
    }

    suspend fun renderEditedTrack(
        context: Context,
        clips: List<AudioEditClip>,
        output: File,
    ): File = withContext(Dispatchers.IO) {
        require(clips.isNotEmpty()) { "Track has no clips" }
        val tempDir = File(output.parentFile, ".edit-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val rendered = mutableListOf<File>()
        try {
            clips.forEachIndexed { index, clip ->
                require(clip.speed in 0.5..2.0) { "Clip speed must be between 0.5 and 2.0" }
                val source = File(clip.sourcePath)
                require(source.isFile) { "Missing clip source: ${clip.sourcePath}" }
                val part = File(tempDir, "clip-$index.wav")
                val args = buildList {
                    add("-y")
                    add("-ss")
                    add("%.3f".format(java.util.Locale.US, clip.startMs / 1000.0))
                    if (clip.endMs > clip.startMs) {
                        add("-to")
                        add("%.3f".format(java.util.Locale.US, clip.endMs / 1000.0))
                    }
                    add("-i")
                    add(source.absolutePath)
                    add("-af")
                    add("atempo=${"%.4f".format(java.util.Locale.US, clip.speed)}")
                    add("-ac")
                    add("2")
                    add("-ar")
                    add("44100")
                    add(part.absolutePath)
                }
                run(context, args)
                rendered += part
            }
            val listFile = File(tempDir, "concat.txt")
            listFile.bufferedWriter().use { writer ->
                rendered.forEach { writer.appendLine("file '${it.absolutePath.replace("'", "'\\''")}'") }
            }
            output.parentFile?.mkdirs()
            run(
                context,
                listOf(
                    "-y", "-f", "concat", "-safe", "0", "-i", listFile.absolutePath,
                    "-c:a", "pcm_s16le", output.absolutePath,
                ),
            )
            output
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** Renders clips at their saved timeline positions instead of concatenating them. */
    suspend fun renderTimelineTrack(
        context: Context,
        clips: List<AudioEditClip>,
        output: File,
        trackVolumeDb: Double = 0.0,
    ): File = withContext(Dispatchers.IO) {
        require(clips.isNotEmpty()) { "Track has no clips" }
        val command = mutableListOf("-y")
        clips.forEach { command += listOf("-i", it.sourcePath) }
        val filters = clips.mapIndexed { index, clip ->
            val durationMs = (clip.endMs - clip.startMs).takeIf { it > 0 }
            buildString {
                append("[$index:a]")
                append("atrim=start=${seconds(clip.startMs)}")
                durationMs?.let { append(":duration=${seconds(it)}") }
                append(",asetpts=PTS-STARTPTS")
                append(",atempo=${number(clip.speed)}")
                append(",volume=${number(clip.gainDb)}dB")
                if (clip.fadeInMs > 0) append(",afade=t=in:st=0:d=${seconds(clip.fadeInMs)}")
                if (clip.fadeOutMs > 0 && durationMs != null) {
                    val adjustedDuration = durationMs / clip.speed
                    val start = (adjustedDuration - clip.fadeOutMs).coerceAtLeast(0.0)
                    append(",afade=t=out:st=${seconds(start.toLong())}:d=${seconds(clip.fadeOutMs)}")
                }
                if (clip.loop && durationMs != null) append(",aloop=loop=-1:size=2147483647")
                append(",adelay=${clip.timelineStartMs}|${clip.timelineStartMs}")
                append(",aformat=sample_rates=48000:channel_layouts=stereo[c$index]")
            }
        }.toMutableList()
        filters += clips.indices.joinToString("") { "[c$it]" } +
            "amix=inputs=${clips.size}:duration=longest:normalize=0," +
            "volume=${number(trackVolumeDb)}dB[out]"
        command += listOf(
            "-filter_complex", filters.joinToString(";"),
            "-map", "[out]",
            "-c:a", "pcm_s16le",
            "-ar", "48000",
            "-ac", "2",
            output.absolutePath,
        )
        output.parentFile?.mkdirs()
        run(context, command)
        output
    }

    /** Mixes the three production tracks and ducks only music while voice is active. */
    suspend fun mixProduction(
        context: Context,
        voice: File,
        music: File?,
        sound: File?,
        output: File,
        format: String = "mp3",
        bitrate: String = "192k",
    ): File = withContext(Dispatchers.IO) {
        val command = mutableListOf("-y", "-i", voice.absolutePath)
        music?.let { command += listOf("-i", it.absolutePath) }
        sound?.let { command += listOf("-i", it.absolutePath) }
        val filter: String
        val map: String
        when {
            music != null && sound != null -> {
                filter = "[1:a][0:a]sidechaincompress=threshold=0.03:ratio=8:attack=200:release=1000[ducked];" +
                    "[0:a][ducked][2:a]amix=inputs=3:duration=longest:normalize=0," +
                    "alimiter=limit=0.891251,loudnorm=I=-18:TP=-1:LRA=11[out]"
                map = "[out]"
            }
            music != null -> {
                filter = "[1:a][0:a]sidechaincompress=threshold=0.03:ratio=8:attack=200:release=1000[ducked];" +
                    "[0:a][ducked]amix=inputs=2:duration=longest:normalize=0," +
                    "alimiter=limit=0.891251,loudnorm=I=-18:TP=-1:LRA=11[out]"
                map = "[out]"
            }
            sound != null -> {
                filter = "[0:a][1:a]amix=inputs=2:duration=longest:normalize=0," +
                    "alimiter=limit=0.891251,loudnorm=I=-18:TP=-1:LRA=11[out]"
                map = "[out]"
            }
            else -> {
                filter = "[0:a]alimiter=limit=0.891251,loudnorm=I=-18:TP=-1:LRA=11[out]"
                map = "[out]"
            }
        }
        command += listOf(
            "-filter_complex", filter,
            "-map", map,
            "-c:a", codecFor(format),
            "-b:a", bitrate,
            output.absolutePath,
        )
        run(context, command)
        output
    }

    private fun number(value: Double): String = "%.4f".format(java.util.Locale.US, value)
    private fun seconds(valueMs: Long): String = "%.3f".format(java.util.Locale.US, valueMs / 1000.0)

    private suspend fun run(context: Context, args: List<String>) = withContext(Dispatchers.IO) {
        val exe = ensure(context)
        val full = listOf(exe) + args
        val process = ProcessBuilder(full)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val rc = process.waitFor()
        if (rc != 0) {
            throw RuntimeException("ffmpeg exit=$rc:\n$output")
        }
    }
}

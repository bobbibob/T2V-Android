package com.t2v.tts.catalog

import com.t2v.tts.engines.PiperRussianTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class RussianVoiceInstaller(
    private val root: File,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(3600, TimeUnit.SECONDS)
        .build()

    fun isInstalled(voiceId: String): Boolean {
        val directory = File(root, voiceId)
        if (!directory.isDirectory) return false
        return directory.walkTopDown().any { it.isFile && it.extension == "onnx" } &&
            directory.walkTopDown().any { it.isFile && it.name == "tokens.txt" } &&
            directory.walkTopDown().any { it.isDirectory && it.name == "espeak-ng-data" }
    }

    suspend fun install(
        voice: PiperRussianTtsEngine.RussianVoice,
        onProgress: (Long, Long) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        root.mkdirs()
        val archive = File(root, "${voice.id}.tar.bz2.part")
        val destination = File(root, voice.id)
        val staging = File(root, "${voice.id}.installing")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            val request = Request.Builder().url(voice.archiveUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Voice download failed: HTTP ${response.code}")
                val body = response.body ?: error("Empty voice archive")
                val total = body.contentLength().takeIf { it > 0 } ?: voice.approximateSizeBytes
                var downloaded = 0L
                onProgress(0, total)
                body.byteStream().use { input ->
                    archive.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            extract(archive, staging)
            require(
                staging.walkTopDown().any { it.isFile && it.extension == "onnx" } &&
                    staging.walkTopDown().any { it.isFile && it.name == "tokens.txt" } &&
                    staging.walkTopDown().any { it.isDirectory && it.name == "espeak-ng-data" },
            ) { "Downloaded voice package is incomplete" }
            destination.deleteRecursively()
            require(staging.renameTo(destination)) { "Cannot activate downloaded voice" }
        } finally {
            archive.delete()
            staging.takeIf { it.exists() }?.deleteRecursively()
        }
    }

    fun delete(voiceId: String): Boolean = File(root, voiceId).deleteRecursively()

    private fun extract(archive: File, destination: File) {
        TarArchiveInputStream(
            BZip2CompressorInputStream(BufferedInputStream(archive.inputStream())),
        ).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val target = File(destination, entry.name).canonicalFile
                require(target.path.startsWith(destination.canonicalPath + File.separator)) {
                    "Unsafe archive entry: ${entry.name}"
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { tar.copyTo(it, 128 * 1024) }
                }
            }
        }
    }
}

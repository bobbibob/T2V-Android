package com.t2v.tts.catalog

import com.t2v.tts.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Каталог голосов — прямой порт `app/tts/voice_gallery_manager.py`.
 * Урезанная версия для MVP.
 */
class VoiceCatalogStub(
    private val catalogUrl: String = "https://raw.githubusercontent.com/estebanstifli/T2V-VoiceGallery/main/catalog.json",
    private val cacheDir: File,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun fetchCatalog(): List<VoiceInfo> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(catalogUrl).get().build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val text = resp.body?.string().orEmpty()
                parseJson(text)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun downloadVoice(voice: VoiceInfo, onProgress: (Long, Long) -> Unit = { _, _ -> }): File? = withContext(Dispatchers.IO) {
        val url = voice.previewUrl ?: return@withContext null
        val out = File(cacheDir, "voices/${voice.engineId}/${voice.id}.bin")
        out.parentFile?.mkdirs()
        val req = Request.Builder().url(url).get().build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body ?: return@runCatching null
                val total = body.contentLength()
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                    }
                }
                out
            }
        }.getOrNull()
    }

    private fun parseJson(text: String): List<VoiceInfo> {
        val voices = mutableListOf<VoiceInfo>()
        val pattern = Regex("\"id\"\\s*:\\s*\"([^\"]+)\".*?\"engine\"\\s*:\\s*\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
        for (m in pattern.findAll(text)) {
            voices += VoiceInfo(
                id = m.groupValues[1],
                displayName = m.groupValues[1],
                language = "en",
                engineId = m.groupValues[2],
            )
        }
        return voices
    }
}

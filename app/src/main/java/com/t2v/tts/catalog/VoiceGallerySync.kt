package com.t2v.tts.catalog

import com.t2v.tts.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Synchronises a voice gallery catalog from a GitHub-hosted JSON file.
 *
 * The catalog is a JSON array of voice entries. Each entry has:
 *   - id: unique voice id
 *   - name: display name
 *   - engine: engine id ("kokoro", "piper_ru", "elevenlabs", ...)
 *   - language: BCP-47 language code
 *   - gender: "male" | "female" | ""
 *   - previewUrl: optional URL to a short audio preview
 *   - downloadModelId: optional catalog model id for one-tap download
 *   - tags: optional list of free-form tags
 *
 * The catalog URL defaults to the T2V-VoiceGallery repository on GitHub,
 * but can be overridden (e.g. for tests or a self-hosted catalog).
 */
class VoiceGallerySync(
    private val catalogUrl: String = DEFAULT_CATALOG_URL,
    private val cacheDir: File,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class GalleryEntry(
        val voice: VoiceInfo,
        val downloadModelId: String?,
    )

    suspend fun fetchCatalog(): List<GalleryEntry> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(catalogUrl).get().build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val text = resp.body?.string().orEmpty()
                parseCatalog(text)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Caches the catalog JSON locally so it can be shown offline.
     * Returns the cached entries, or an empty list if the cache is missing.
     */
    suspend fun loadCachedCatalog(): List<GalleryEntry> = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheDir, CATALOG_CACHE_NAME)
        if (!cacheFile.isFile) return@withContext emptyList()
        runCatching { parseCatalog(cacheFile.readText()) }.getOrDefault(emptyList())
    }

    /**
     * Downloads the catalog and persists it to [cacheDir].
     * Returns the parsed entries on success, or the cached entries on
     * network failure.
     */
    suspend fun sync(): List<GalleryEntry> = withContext(Dispatchers.IO) {
        val entries = fetchCatalog()
        if (entries.isNotEmpty()) {
            val cacheFile = File(cacheDir, CATALOG_CACHE_NAME)
            cacheFile.parentFile?.mkdirs()
            runCatching {
                val req = Request.Builder().url(catalogUrl).get().build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.string()?.let { cacheFile.writeText(it) }
                    }
                }
            }
        } else {
            return@withContext loadCachedCatalog()
        }
        entries
    }

    internal fun parseCatalog(text: String): List<GalleryEntry> {
        val array = runCatching { json.parseToJsonElement(text) as? JsonArray }
            .getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            val name = obj.string("name").orEmpty()
            val engine = obj.string("engine") ?: return@mapNotNull null
            val language = obj.string("language").orEmpty()
            val gender = obj.string("gender").orEmpty()
            val previewUrl = obj.string("previewUrl")
            val downloadModelId = obj.string("downloadModelId")
            val tags = (obj["tags"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                ?: emptyList()
            val isCloned = obj.boolean("isCloned") ?: false
            GalleryEntry(
                voice = VoiceInfo(
                    id = id,
                    displayName = name.ifBlank { id },
                    language = language,
                    gender = gender,
                    engineId = engine,
                    previewUrl = previewUrl,
                    tags = tags,
                    downloadModelId = downloadModelId,
                    isCloned = isCloned,
                ),
                downloadModelId = downloadModelId,
            )
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.let { if (it.isString) it.content else null }

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

    companion object {
        const val DEFAULT_CATALOG_URL =
            "https://raw.githubusercontent.com/bobbibob/T2V-VoiceGallery/main/catalog.json"
        const val CATALOG_CACHE_NAME = "voice-gallery.json"
    }
}
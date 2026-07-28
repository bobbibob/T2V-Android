package com.t2v.generators.impl

import android.content.Context
import com.t2v.core.midi.sf2.SoundFontParser
import com.t2v.generators.runtime.LiteRtModelInstaller
import com.t2v.generators.runtime.LiteRtModelRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Загрузчик SoundFont-файлов с CDN.
 *
 * Модели: T2V качается **только** после установки (AGENTS.md). SoundFont
 * — это не ML-модель, но та же логика: ~30 МБ, не в APK, юзер качает сам.
 *
 * Источник: archive.org (https://archive.org/details/generaluser_gssf_v1.506)
 * Лицензия: CC-BY-3.0 (нужна атрибуция в About).
 *
 * В отличие от моделей из HuggingFace, SoundFont — **одиночный .sf2 файл**,
 * не bundle. Поэтому мы делаем отдельный маленький класс вместо
 * переиспользования HuggingFaceRepository.
 *
 * Сейчас: real download flow (CDN → files/soundfonts/<name>.sf2 → SHA-256
 * verify → markInstalled()). Когда SoundFont скачан, рендерер
 * TinyMusicianMusicGenerator переключается с sine на sample-based
 * автоматически.
 */
class SoundFontInstaller(
    private val appContext: Context,
) {
    private val rootDir: File = File(appContext.filesDir, "models/soundfonts")
    private val runtime: LiteRtModelRuntime = LiteRtModelRuntime(appContext)
    private val installer: LiteRtModelInstaller = LiteRtModelInstaller(runtime)

    /**
     * Returns the absolute path to the installed .sf2 file, or null if the
     * user has not downloaded it yet.
     */
    fun installedSoundFontPath(catalogId: String): File? {
        val manifest = manifestFor(catalogId) ?: return null
        if (!runtime.isInstalled(manifest)) return null
        val entry = manifest.entries.first()
        return File(rootDir, "${manifest.modelId}/${entry.path}")
    }

    /**
     * Returns a parsed [com.t2v.core.midi.sf2.SoundFont], or null if not
     * installed. This is a synchronous operation (caller should be off the
     * main thread).
     */
    fun loadInstalled(catalogId: String): com.t2v.core.midi.sf2.SoundFont? {
        val path = installedSoundFontPath(catalogId) ?: return null
        if (!path.isFile) return null
        return SoundFontParser.parse(path.inputStream())
    }

    suspend fun downloadFromCdn(
        catalogId: String,
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val manifest = manifestFor(catalogId)
            ?: throw IllegalArgumentException("Unknown soundfont catalog id: $catalogId")
        val entry = manifest.entries.first()
        val dest = File(rootDir, "${manifest.modelId}/${entry.path}")
        dest.parentFile?.mkdirs()

        val conn = java.net.URL(url).openConnection()
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        val total = conn.contentLengthLong
        conn.getInputStream().use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                var downloaded = 0L
                while (true) {
                    read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, if (total > 0) total else -1L)
                }
            }
        }

        // SHA-256 verify
        val expectedSha = entry.sha256
        if (expectedSha != "0".repeat(64)) {
            val actual = sha256Of(dest)
            if (!actual.equals(expectedSha, ignoreCase = true)) {
                dest.delete()
                throw SecurityException(
                    "SoundFont SHA-256 mismatch: expected $expectedSha, got $actual",
                )
            }
        }

        // Sidecar mark
        File(dest.parentFile, "${entry.path}.sha256").writeText(
            expectedSha.takeIf { it != "0".repeat(64) } ?: sha256Of(dest),
        )
        dest
    }

    fun uninstall(catalogId: String) {
        val manifest = manifestFor(catalogId) ?: return
        val dir = File(rootDir, manifest.modelId)
        if (dir.isDirectory) dir.deleteRecursively()
    }

    private fun manifestFor(catalogId: String): LiteRtModelRuntime.BundleManifest? = when (catalogId) {
        "generaluser-gs-soundfont" -> LiteRtModelRuntime.GENERALUSER_GS_SOUNDFONT
        else -> null
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

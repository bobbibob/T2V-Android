package com.t2v.server

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import com.t2v.core.model.GenerationModelCatalog
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Direct client for public and token-protected Hugging Face repositories.
 *
 * Model files are downloaded into app-private storage. A small manifest preserves
 * the original repository id because filesystem-safe directory names are hashes.
 */
class HuggingFaceRepository(
    private val context: Context,
    private val modelsRoot: File,
    private val modelsTreeUri: String = "",
    private val token: String = "",
) {
    data class ModelFile(
        val path: String,
        val sizeBytes: Long = -1,
    ) {
        val isTtsArtifact: Boolean
            get() = path.substringAfterLast('.').lowercase() in SUPPORTED_EXTENSIONS

        val quantization: String
            get() = QUANTIZATION.find(path.uppercase())?.value ?: "Original"
    }

    data class Model(
        val id: String,
        val name: String,
        val downloads: Long,
        val tags: List<String>,
        val files: List<ModelFile>,
    ) {
        val totalSizeBytes: Long
            get() = files
                .filterNot {
                    it.path == ".gitattributes" ||
                        it.path.equals("README.md", ignoreCase = true)
                }
                .takeIf { selected -> selected.isNotEmpty() && selected.all { it.sizeBytes > 0 } }
                ?.sumOf { it.sizeBytes }
                ?: -1L

        val compatibleFiles: List<ModelFile>
            get() = files.filter { it.isTtsArtifact }

        val variants: List<ModelVariant>
            get() = compatibleFiles
                .filter { it.path.substringAfterLast('.').lowercase() in WEIGHT_EXTENSIONS }
                .map { file ->
                    ModelVariant(
                        id = file.path,
                        label = file.path.substringAfterLast('/'),
                        format = file.path.substringAfterLast('.').uppercase(),
                        quantization = file.quantization,
                        sizeBytes = file.sizeBytes,
                        weightFile = file,
                    )
                }
                .sortedBy { it.sizeBytes.takeIf { size -> size > 0 } ?: Long.MAX_VALUE }
    }

    data class ModelVariant(
        val id: String,
        val label: String,
        val format: String,
        val quantization: String,
        val sizeBytes: Long,
        val weightFile: ModelFile,
    )

    data class InstalledModel(
        val id: String,
        val location: String,
        val filesCount: Int,
        val totalSizeBytes: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(3600, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 30): List<Model> = withContext(Dispatchers.IO) {
        if (VERIFIED_ANDROID_MODELS.isEmpty()) return@withContext emptyList()
        val urlBuilder = "https://huggingface.co/api/models".toHttpUrl().newBuilder()
            .addQueryParameter("filter", "text-to-speech")
            .addQueryParameter("limit", limit.coerceIn(1, 100).toString())
            .addQueryParameter("full", "true")
        query.trim().takeIf { it.isNotEmpty() }?.let {
            urlBuilder.addQueryParameter("search", it)
        }
        val url = urlBuilder.build()
        val request = authorized(Request.Builder().url(url)).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Hugging Face search failed: HTTP ${response.code}")
            }
            parseModels(response.body?.string().orEmpty())
                .filter { it.id in VERIFIED_ANDROID_MODELS }
        }
    }

    suspend fun model(repoId: String): Model = withContext(Dispatchers.IO) {
        require(repoId in VERIFIED_ANDROID_MODELS) {
            "This repository has no verified Android TTS runtime"
        }
        val url = "https://huggingface.co/api/models".toHttpUrl().newBuilder()
            .addPathSegments(repoId.trim('/'))
            .addPathSegment("revision")
            .addPathSegment(revisionFor(repoId))
            // Hugging Face exposes per-file sizes (including LFS blobs) only
            // when the model-info endpoint is requested with blobs=true.
            .addQueryParameter("blobs", "true")
            .build()
        val request = authorized(Request.Builder().url(url)).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Hugging Face repository failed: HTTP ${response.code}")
            }
            parseModel(json.parseToJsonElement(response.body?.string().orEmpty()) as JsonObject)
                ?: error("Invalid Hugging Face model response")
        }
    }

    suspend fun install(
        model: Model,
        variant: ModelVariant,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): InstalledModel = withContext(Dispatchers.IO) {
        require(model.id in VERIFIED_ANDROID_MODELS) {
            "Model ${model.id} is not verified for execution on Android"
        }
        require(variant in model.variants) { "Variant does not belong to ${model.id}" }
        // LiteRT bundles (e.g. MusicGen) download only their .tflite weight
        // files: the three-stage encoder/LM/decoder pipeline needs exactly the
        // files listed in its runtime manifest and nothing else. A repo with
        // no .tflite files at all (e.g. an ONNX-only export) is an error, not a
        // silent zero-byte install. Everything else downloads one weight file
        // plus its support files.
        val isLiteRtBundle = GenerationModelCatalog.entries.any {
            it.repository == model.id &&
                it.requirements.runtime == GenerationModelCatalog.Runtime.LiteRt
        }
        val files = if (model.id == KOKORO_REPOSITORY) {
            model.files.filter {
                it.path != ".gitattributes" &&
                    !it.path.equals("README.md", ignoreCase = true)
            }
        } else if (isLiteRtBundle) {
            model.files
                .filter { it.path.substringAfterLast('.').lowercase() in WEIGHT_EXTENSIONS }
                .also { selected ->
                    if (selected.isEmpty()) {
                        error("Репозиторий ${model.id} не содержит .tflite файлов для LiteRT")
                    }
                }
        } else {
            buildList {
                add(variant.weightFile)
                addAll(
                    model.compatibleFiles.filter {
                        it != variant.weightFile &&
                            it.path.substringAfterLast('.').lowercase() in SUPPORT_FILE_EXTENSIONS
                    },
                )
            }
        }.distinctBy { it.path }
        val directory = directoryFor(model.id)
        directory.mkdirs()
        val knownTotal = files
            .takeIf { selected -> selected.all { it.sizeBytes > 0 } }
            ?.sumOf { it.sizeBytes }
            ?: -1L
        var completedBytes = 0L
        onProgress(0L, knownTotal)

        try {
            for (file in files) {
                val output = safeTarget(directory, file.path)
                val partial = File(output.parentFile, "${output.name}.part")
                output.parentFile?.mkdirs()
                val url = "https://huggingface.co".toHttpUrl().newBuilder()
                    .addPathSegments(model.id)
                    .addPathSegment("resolve")
                    .addPathSegment(revisionFor(model.id))
                    .addPathSegments(file.path)
                    .build()
                val request = authorized(Request.Builder().url(url)).get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Download ${file.path} failed: HTTP ${response.code}")
                    }
                    val body = response.body ?: error("Empty response for ${file.path}")
                    body.byteStream().use { input ->
                        partial.outputStream().use { outputStream ->
                            val buffer = ByteArray(128 * 1024)
                            var fileBytes = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                outputStream.write(buffer, 0, read)
                                fileBytes += read
                                onProgress(completedBytes + fileBytes, knownTotal)
                            }
                        }
                    }
                    if (output.exists() && !output.delete()) {
                        error("Cannot replace ${output.name}")
                    }
                    if (!partial.renameTo(output)) {
                        error("Cannot finish ${output.name}")
                    }
                    completedBytes += output.length()
                }
            }
            writeManifest(directory, model.id)
            if (modelsTreeUri.isNotBlank() && model.id != KOKORO_REPOSITORY) {
                val installed = copyToDocumentTree(directory, model.id)
                directory.deleteRecursively()
                installed
            } else {
                installedModel(directory) ?: error("Cannot read installed model")
            }
        } catch (error: Throwable) {
            directory.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".part") }
                .forEach { it.delete() }
            throw error
        }
    }

    fun installed(): List<InstalledModel> {
        documentRoot()?.let { root ->
            return root.listFiles()
                .filter { it.isDirectory }
                .mapNotNull(::installedDocumentModel)
                .sortedBy { it.id.lowercase() }
        }
        if (!modelsRoot.isDirectory) return emptyList()
        return modelsRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull(::installedModel)
            .sortedBy { it.id.lowercase() }
    }

    fun delete(modelId: String): Boolean {
        documentRoot()?.let { root ->
            val hash = sha256(modelId).take(24)
            return root.findFile(hash)?.delete() == true
        }
        val directory = directoryFor(modelId)
        return directory.exists() && directory.deleteRecursively()
    }

    private fun parseModels(text: String): List<Model> {
        val array = json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonObject)?.let(::parseModel) }
    }

    private fun parseModel(obj: JsonObject): Model? {
        val id = obj.string("id") ?: obj.string("modelId") ?: return null
        val tags = (obj["tags"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            .orEmpty()
        val files = (obj["siblings"] as? JsonArray)
            ?.mapNotNull { sibling ->
                val item = sibling as? JsonObject ?: return@mapNotNull null
                val path = item.string("rfilename") ?: return@mapNotNull null
                val size = item.long("size")
                    ?: (item["lfs"] as? JsonObject)?.long("size")
                    ?: -1L
                ModelFile(path, size)
            }
            .orEmpty()
        return Model(
            id = id,
            name = id.substringAfter('/'),
            downloads = obj.long("downloads") ?: 0,
            tags = tags,
            files = files,
        )
    }

    private fun installedModel(directory: File): InstalledModel? {
        val manifest = File(directory, MANIFEST)
        if (!manifest.isFile) return null
        val obj = runCatching {
            json.parseToJsonElement(manifest.readText()) as JsonObject
        }.getOrNull() ?: return null
        val id = obj.string("id") ?: return null
        val files = directory.walkTopDown().filter { it.isFile && it.name != MANIFEST }.toList()
        return InstalledModel(id, directory.absolutePath, files.size, files.sumOf { it.length() })
    }

    private fun documentRoot(): DocumentFile? =
        modelsTreeUri.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?.let { DocumentFile.fromTreeUri(context, it) }
            ?.takeIf { it.canRead() && it.canWrite() }

    private fun copyToDocumentTree(source: File, modelId: String): InstalledModel {
        val root = documentRoot() ?: error("Selected models folder is not writable")
        val name = sha256(modelId).take(24)
        root.findFile(name)?.delete()
        val destination = root.createDirectory(name)
            ?: error("Cannot create model folder in selected location")
        source.listFiles().orEmpty().forEach { copyDocument(it, destination) }
        return installedDocumentModel(destination) ?: error("Cannot verify copied model")
    }

    private fun copyDocument(source: File, destination: DocumentFile) {
        if (source.isDirectory) {
            val child = destination.createDirectory(source.name)
                ?: error("Cannot create ${source.name}")
            source.listFiles().orEmpty().forEach { copyDocument(it, child) }
            return
        }
        val mime = if (source.name.endsWith(".json")) "application/json" else "application/octet-stream"
        val output = destination.createFile(mime, source.name)
            ?: error("Cannot create ${source.name}")
        context.contentResolver.openOutputStream(output.uri, "w").use { stream ->
            requireNotNull(stream) { "Cannot open ${source.name}" }
            source.inputStream().use { input -> input.copyTo(stream, 128 * 1024) }
        }
    }

    private fun installedDocumentModel(directory: DocumentFile): InstalledModel? {
        val manifest = directory.findFile(MANIFEST) ?: return null
        val text = context.contentResolver.openInputStream(manifest.uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return null
        val obj = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return null
        val id = obj.string("id") ?: return null
        val files = documentFiles(directory).filter { it.name != MANIFEST }.toList()
        return InstalledModel(
            id = id,
            location = directory.uri.toString(),
            filesCount = files.size,
            totalSizeBytes = files.sumOf { it.length() },
        )
    }

    private fun documentFiles(root: DocumentFile): Sequence<DocumentFile> = sequence {
        for (child in root.listFiles()) {
            if (child.isDirectory) yieldAll(documentFiles(child)) else yield(child)
        }
    }

    private fun writeManifest(directory: File, id: String) {
        File(directory, MANIFEST).writeText(
            buildJsonObject {
                put("id", id)
                put("installedAt", System.currentTimeMillis())
            }.toString(),
        )
    }

    private fun directoryFor(modelId: String): File =
        File(modelsRoot, directoryNameFor(modelId))

    /** Stable filesystem-safe directory name for a repository id (first 24 hex of SHA-256). */
    private fun directoryNameFor(modelId: String): String = sha256(modelId).take(24)

    private fun safeTarget(root: File, relativePath: String): File {
        val target = File(root, relativePath).canonicalFile
        require(target.path.startsWith(root.canonicalPath + File.separator)) {
            "Unsafe model file path: $relativePath"
        }
        return target
    }

    private fun authorized(builder: Request.Builder): Request.Builder =
        if (token.isBlank()) builder else builder.header("Authorization", "Bearer $token")

    private fun revisionFor(modelId: String): String =
        if (modelId == KOKORO_REPOSITORY) KOKORO_REVISION else "main"

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val MANIFEST = ".ltv-model.json"
        /**
         * A model is added only together with an Android inference adapter and
         * an instrumentation test for its exact repository/revision.
         * GGUF/ONNX/SafeTensors extensions alone never qualify a model.
         */
        const val KOKORO_REPOSITORY = "csukuangfj/kokoro-en-v0_19"
        const val KOKORO_REVISION = "92805c485745946a0d945562d3aba19e7cbb2104"
        private val VERIFIED_ANDROID_MODELS: Set<String> = run {
            val catalog = GenerationModelCatalog.entries
                .filter { entry ->
                    // Только модели, у которых repository указывает на HF в формате author/name
                    entry.repository.isNotBlank() &&
                        !entry.repository.startsWith("http") &&
                        entry.repository.contains('/')
                }
                .map { it.repository }
                .toSet()
            // Гарантируем, что Kokoro всегда доступен, даже если каталог отредактирован
            (catalog + KOKORO_REPOSITORY)
        }
        private val SUPPORTED_EXTENSIONS = setOf(
            "onnx", "bin", "json", "txt", "model", "safetensors", "pt", "pth",
            "yaml", "yml", "tokens", "vocab", "config", "gguf", "tflite",
        )
        private val WEIGHT_EXTENSIONS = setOf("onnx", "safetensors", "pt", "pth", "gguf", "tflite")
        private val SUPPORT_FILE_EXTENSIONS = SUPPORTED_EXTENSIONS - WEIGHT_EXTENSIONS
        private val QUANTIZATION = Regex(
            """(?:^|[._-])(F32|F16|BF16|Q[2-8](?:_[0-9])?(?:_[KMLS]+)?|IQ[1-4](?:_[A-Z]+)?)(?:[._-]|$)""",
        )
    }
}

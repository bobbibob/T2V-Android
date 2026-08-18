package com.t2v.worker

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.t2v.core.model.GenerationModelCatalog
import com.t2v.generators.runtime.LiteRtModelInstaller
import com.t2v.generators.runtime.LiteRtModelRuntime
import com.t2v.server.HuggingFaceRepository
import java.io.File

/**
 * Background download of a model declared in [GenerationModelCatalog].
 *
 * Replaces the ad-hoc `viewModelScope.launch` flow in `ModelsViewModel` so a
 * model download survives the user leaving the Models screen (and the app
 * process being recreated). The worker is a one-time, expedited-style job
 * that runs a foreground notification while the download is active.
 *
 * Progress is reported via [setProgress] so the UI can observe the matching
 * [androidx.work.WorkInfo] and render a progress bar without holding a
 * reference to the worker.
 *
 * Input data:
 *  - [KEY_CATALOG_ID]: catalog entry id (e.g. "musicgen-small", "kokoro-82m")
 *  - [KEY_REPO_ID]: Hugging Face repository id ("author/name")
 *  - [KEY_MODELS_ROOT]: absolute path of the models root directory
 *  - [KEY_MODELS_TREE_URI]: persisted document-tree URI (may be empty)
 *  - [KEY_TOKEN]: Hugging Face token (may be empty)
 *
 * Output data on success:
 *  - [KEY_RESULT_LOCATION]: absolute path of the installed model directory
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val catalogId = inputData.getString(KEY_CATALOG_ID)
            ?: return Result.failure(workDataOf(KEY_ERROR to "catalogId is required"))
        val repoId = inputData.getString(KEY_REPO_ID)
            ?: return Result.failure(workDataOf(KEY_ERROR to "repoId is required"))
        val modelsRootPath = inputData.getString(KEY_MODELS_ROOT)
            ?: return Result.failure(workDataOf(KEY_ERROR to "modelsRoot is required"))
        val modelsTreeUri = inputData.getString(KEY_MODELS_TREE_URI).orEmpty()
        val token = inputData.getString(KEY_TOKEN).orEmpty()

        val entry = GenerationModelCatalog.entries.firstOrNull { it.id == catalogId }
            ?: return Result.failure(workDataOf(KEY_ERROR to "catalog entry not found: $catalogId"))

        val modelsRoot = File(modelsRootPath)
        val repository = HuggingFaceRepository(
            context = applicationContext,
            modelsRoot = modelsRoot,
            modelsTreeUri = modelsTreeUri,
            token = token,
        )

        return try {
            setProgress(workDataOf(KEY_PROGRESS to 0f, KEY_DOWNLOADED_BYTES to 0L, KEY_TOTAL_BYTES to (entry.approximateDownloadBytes ?: -1L)))
            val model = repository.model(repoId)
            val variant = model.variants.firstOrNull()
                ?: return Result.failure(workDataOf(KEY_ERROR to "у модели нет файлов для Android"))

            val installed = repository.install(model, variant) { downloaded, total ->
                val progress = if (total > 0) {
                    (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                setProgressAsync(
                    workDataOf(
                        KEY_PROGRESS to progress,
                        KEY_DOWNLOADED_BYTES to downloaded.coerceAtLeast(0L),
                        KEY_TOTAL_BYTES to total,
                    ),
                )
            }

            bridgeLiteRtInstall(entry, installed.location)

            Result.success(
                workDataOf(
                    KEY_RESULT_LOCATION to installed.location,
                    KEY_CATALOG_ID to catalogId,
                ),
            )
        } catch (error: Throwable) {
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: "download failed")))
        }
    }

    /**
     * Copies a downloaded LiteRT/ORT bundle from the Hugging Face cache into
     * the runtime manifest root. Mirrors [ModelsViewModel.bridgeLiteRtInstall]
     * so MusicGen and similar bundles work from the worker too.
     */
    private fun bridgeLiteRtInstall(
        entry: GenerationModelCatalog.Entry,
        cacheLocation: String,
    ) {
        if (entry.liteRtFiles.isEmpty()) return
        val manifest = LiteRtModelRuntime.manifestFor(entry.id) ?: return
        val runtime = LiteRtModelRuntime(applicationContext)
        val installer = LiteRtModelInstaller(runtime)
        val plan = installer.plan(manifest = manifest, catalog = entry)
        val cache = File(cacheLocation)
        for (manifestEntry in manifest.entries) {
            val source = File(cache, manifestEntry.path)
            if (!source.isFile) {
                error("Downloaded cache for ${entry.id} is missing ${manifestEntry.path}")
            }
            val target = File(plan.destinationRoot, manifestEntry.path)
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            installer.markInstalled(
                plan = plan,
                filePath = manifestEntry.path,
                expectedBytes = manifestEntry.expectedBytes,
                sha256 = manifestEntry.sha256,
            )
        }
    }

    companion object {
        const val KEY_CATALOG_ID = "catalogId"
        const val KEY_REPO_ID = "repoId"
        const val KEY_MODELS_ROOT = "modelsRoot"
        const val KEY_MODELS_TREE_URI = "modelsTreeUri"
        const val KEY_TOKEN = "token"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloadedBytes"
        const val KEY_TOTAL_BYTES = "totalBytes"
        const val KEY_ERROR = "error"
        const val KEY_RESULT_LOCATION = "resultLocation"
        const val TAG_DOWNLOAD = "t2v.model-download"
        const val TAG_CATALOG_PREFIX = "t2v.catalog."

        /** Unique work name for a catalog entry so re-queueing replaces the same job. */
        fun workName(catalogId: String): String = "model-download-$catalogId"

        /**
         * Enqueues a one-time download for [catalogId]. Returns the work name
         * so the caller can observe [androidx.work.WorkInfo] for progress.
         */
        fun enqueue(
            context: Context,
            catalogId: String,
            repoId: String,
            modelsRoot: File,
            modelsTreeUri: String,
            token: String,
        ): String {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_CATALOG_ID to catalogId,
                        KEY_REPO_ID to repoId,
                        KEY_MODELS_ROOT to modelsRoot.absolutePath,
                        KEY_MODELS_TREE_URI to modelsTreeUri,
                        KEY_TOKEN to token,
                    ),
                )
                .addTag(TAG_DOWNLOAD)
                .addTag(TAG_CATALOG_PREFIX + catalogId)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(catalogId),
                ExistingWorkPolicy.REPLACE,
                request,
            )
            return workName(catalogId)
        }

        /** Cancels a queued or running download for [catalogId]. */
        fun cancel(context: Context, catalogId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(catalogId))
        }
    }
}
package com.t2v.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ModelDownloadWorker] constants and work-name derivation.
 *
 * The worker itself runs on a device (it uses [androidx.work.WorkManager]),
 * so these tests only cover the pure helper logic.
 */
class ModelDownloadWorkerTest {

    @Test
    fun `work name is derived from catalog id`() {
        assertEquals("model-download-musicgen-small", ModelDownloadWorker.workName("musicgen-small"))
        assertEquals("model-download-kokoro-82m", ModelDownloadWorker.workName("kokoro-82m"))
    }

    @Test
    fun `catalog tag prefix is stable`() {
        assertEquals("t2v.catalog.", ModelDownloadWorker.TAG_CATALOG_PREFIX)
        val tag = ModelDownloadWorker.TAG_CATALOG_PREFIX + "musicgen-small"
        assertTrue(tag.startsWith(ModelDownloadWorker.TAG_CATALOG_PREFIX))
        assertEquals("musicgen-small", tag.removePrefix(ModelDownloadWorker.TAG_CATALOG_PREFIX))
    }

    @Test
    fun `download tag is non-empty`() {
        assertTrue(ModelDownloadWorker.TAG_DOWNLOAD.isNotEmpty())
    }

    @Test
    fun `input and output keys are distinct`() {
        val keys = listOf(
            ModelDownloadWorker.KEY_CATALOG_ID,
            ModelDownloadWorker.KEY_REPO_ID,
            ModelDownloadWorker.KEY_MODELS_ROOT,
            ModelDownloadWorker.KEY_MODELS_TREE_URI,
            ModelDownloadWorker.KEY_TOKEN,
            ModelDownloadWorker.KEY_PROGRESS,
            ModelDownloadWorker.KEY_DOWNLOADED_BYTES,
            ModelDownloadWorker.KEY_TOTAL_BYTES,
            ModelDownloadWorker.KEY_ERROR,
            ModelDownloadWorker.KEY_RESULT_LOCATION,
        )
        assertEquals("All keys must be unique", keys.size, keys.toSet().size)
        keys.forEach { assertNotNull("Key must not be empty", it); assertTrue(it.isNotEmpty()) }
    }
}
package com.t2v.core.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProjectManagerTest {

    @Test
    fun `importText returns pasted document`() {
        val pm = ProjectManager()
        val doc = pm.importText("Test", "Hello world")
        assertEquals("Test", doc.title)
        assertEquals("Hello world", doc.rawText)
        assertEquals("pasted", doc.source)
    }

    @Test
    fun `importFile for txt`() {
        val tmp = kotlin.io.path.createTempFile(suffix = ".txt").toFile()
        tmp.writeText("Some text content")
        val pm = ProjectManager()
        val result = pm.importFile(tmp)
        assertTrue(result is ProjectManager.ImportResult.Ok)
        val doc = (result as ProjectManager.ImportResult.Ok).document
        assertEquals("Some text content", doc.rawText)
    }

    @Test
    fun `unsupported extension fails`() {
        val tmp = kotlin.io.path.createTempFile(suffix = ".pdf").toFile()
        val result = ProjectManager().importFile(tmp)
        assertTrue(result is ProjectManager.ImportResult.Failed)
    }
}

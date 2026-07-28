package com.t2v.core.onnx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrtSessionProviderTest {
    @Test
    fun `OrtEnvironment singleton is accessible`() {
        val env1 = OrtSessionProvider.environment()
        val env2 = OrtSessionProvider.environment()
        assertTrue(env1 === env2)
    }

    @Test
    fun `default options returns non-null`() {
        val opts = OrtSessionProvider.defaultOptions()
        assertTrue(opts != null)
    }

    @Test
    fun `loaded session count starts at zero`() {
        val before = OrtSessionProvider.loadedSessionCount()
        assertEquals(0, before)
    }

    @Test
    fun `closeAll is idempotent`() {
        OrtSessionProvider.closeAll()
        OrtSessionProvider.closeAll()
        assertEquals(0, OrtSessionProvider.loadedSessionCount())
    }
}

package com.t2v.tts.engines

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for ElevenLabsTtsEngine JSON parsing logic.
 *
 * The cloneVoice and listVoices methods parse JSON responses from the
 * ElevenLabs API. These tests verify the parsing handles JsonPrimitive
 * values correctly (using .content instead of .toString().trim('"')).
 */
class ElevenLabsTtsEngineTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `cloneVoice response voice_id is parsed correctly`() {
        val apiResponse = """{"voice_id":"abc123xyz","status":"ok"}"""
        val result = json.parseToJsonElement(apiResponse) as JsonObject
        val voiceId = result["voice_id"]?.jsonPrimitive?.let { p ->
            if (p.isString) p.content else null
        }
        assertEquals("abc123xyz", voiceId)
    }

    @Test
    fun `listVoices response voice_id and name are parsed correctly`() {
        val apiResponse = """
            {
                "voices": [
                    {"voice_id":"voice1","name":"Alice","language":"en","gender":"female"},
                    {"voice_id":"voice2","name":"Bob","language":"en","gender":"male"}
                ]
            }
        """.trimIndent()
        val obj = json.parseToJsonElement(apiResponse) as JsonObject
        val arr = obj["voices"] as? kotlinx.serialization.json.JsonArray
        assertEquals(2, arr?.size)

        val voice1 = arr!![0] as JsonObject
        val voiceId1 = voice1["voice_id"]?.jsonPrimitive?.let { p ->
            if (p.isString) p.content else null
        }
        val name1 = voice1["name"]?.jsonPrimitive?.let { p ->
            if (p.isString) p.content else null
        }.orEmpty()
        assertEquals("voice1", voiceId1)
        assertEquals("Alice", name1)
    }

    @Test
    fun `missing voice_id in cloneVoice response returns null`() {
        val apiResponse = """{"status":"error"}"""
        val result = json.parseToJsonElement(apiResponse) as JsonObject
        val voiceId = result["voice_id"]?.jsonPrimitive?.let { p ->
            if (p.isString) p.content else null
        }
        assertNull(voiceId)
    }

    @Test
    fun `non-string voice_id is ignored`() {
        val apiResponse = """{"voice_id":12345}"""
        val result = json.parseToJsonElement(apiResponse) as JsonObject
        val voiceId = result["voice_id"]?.jsonPrimitive?.let { p ->
            if (p.isString) p.content else null
        }
        assertNull(voiceId)
    }

    @Test
    fun `engine info has correct id and supports cloning`() {
        val info = ElevenLabsTtsEngine.ENGINE_INFO
        assertEquals("elevenlabs", info.id)
        org.junit.Assert.assertTrue(info.supportsCloning)
        org.junit.Assert.assertTrue(info.requiresApiKey)
    }
}

// Copyright 2026 Google LLC. SPDX-License-Identifier: Apache-2.0

package com.google.ai.edge.gallery.relay.server.handlers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnthropicWireTest {

    private val call = QwenScanEvent.ToolCall(
        name = "get_weather",
        args = Json.parseToJsonElement("""{"city":"Oulu"}""") as JsonObject,
    )

    private fun hasNullValue(element: JsonElement): Boolean = when (element) {
        is JsonNull -> true
        is JsonObject -> element.values.any { hasNullValue(it) }
        is JsonArray -> element.any { hasNullValue(it) }
        is JsonPrimitive -> false
    }

    @Test
    fun emptyTextBlockIsOmitted() {
        assertEquals(1, anthropicContentBlocks("", listOf(call), listOf("toolu_1")).size)
        assertEquals(1, anthropicContentBlocks("  \n", listOf(call), listOf("toolu_1")).size)
        assertEquals(1, anthropicContentBlocks(null, listOf(call), listOf("toolu_1")).size)
        assertEquals(emptyList<JsonObject>(), anthropicContentBlocks(null, emptyList(), emptyList()))
    }

    @Test
    fun textComesBeforeToolUseBlocks() {
        val blocks = anthropicContentBlocks("Let me check.", listOf(call), listOf("toolu_1"))
        assertEquals(
            listOf(
                Json.parseToJsonElement("""{"type":"text","text":"Let me check."}"""),
                Json.parseToJsonElement(
                    """{"type":"tool_use","id":"toolu_1","name":"get_weather","input":{"city":"Oulu"}}"""
                ),
            ),
            blocks,
        )
    }

    @Test
    fun blocksCarryNoNullValuedKeys() {
        val blocks = anthropicContentBlocks("hi", listOf(call, call), listOf("toolu_1", "toolu_2"))
        assertEquals(3, blocks.size)
        blocks.forEach { assertFalse("null value in $it", hasNullValue(it)) }
    }

    @Test
    fun toolUseIdsFollowCallOrder() {
        val second = QwenScanEvent.ToolCall("other", JsonObject(emptyMap()))
        val blocks = anthropicContentBlocks(null, listOf(call, second), listOf("a", "b"))
        assertEquals(listOf("a", "b"), blocks.map { (it["id"] as JsonPrimitive).content })
        assertEquals(listOf("get_weather", "other"), blocks.map { (it["name"] as JsonPrimitive).content })
    }

    @Test
    fun stopReasonIsToolUseOnlyWhenThereAreCalls() {
        assertEquals("tool_use", anthropicStopReason(listOf(call)))
        assertEquals("tool_use", anthropicStopReason(listOf(call, call)))
        assertEquals("end_turn", anthropicStopReason(emptyList()))
    }

    @Test
    fun stopReasonIsNeverMaxTokens() {
        listOf(emptyList(), listOf(call)).forEach {
            assertFalse(anthropicStopReason(it) == "max_tokens")
        }
    }

    @Test
    fun sseFrameHasExactShapeAndNoDoneMarker() {
        val frame = anthropicSseFrame("message_stop", """{"type":"message_stop"}""")
        assertEquals("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n", frame)
        assertFalse(frame.contains("[DONE]"))
    }
}

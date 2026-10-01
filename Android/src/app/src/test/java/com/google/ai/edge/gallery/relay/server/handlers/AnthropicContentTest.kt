// Copyright 2026 Google LLC. SPDX-License-Identifier: Apache-2.0

package com.google.ai.edge.gallery.relay.server.handlers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicContentTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun ok(result: AnthropicParseResult): AnthropicParseResult.Ok {
        assertTrue("expected Ok, got $result", result is AnthropicParseResult.Ok)
        return result as AnthropicParseResult.Ok
    }

    @Test
    fun systemTextFromPlainString() {
        assertEquals("be brief", anthropicSystemText(JsonPrimitive("be brief")))
    }

    @Test
    fun systemTextFromBlockArrayJoinsWithNewlines() {
        val system = json("""[{"type":"text","text":"first"},{"type":"text","text":"second"}]""")
        assertEquals("first\nsecond", anthropicSystemText(system))
    }

    @Test
    fun systemTextAbsentIsNull() {
        assertNull(anthropicSystemText(null))
    }

    @Test
    fun plainStringContentBecomesOneTextPart() {
        val result = ok(parseAnthropicMessage("user", JsonPrimitive("hi")))
        assertEquals("hi", result.text)
        assertEquals(1, result.openAiContent.size)
        assertEquals(json("""{"type":"text","text":"hi"}"""), result.openAiContent[0])
    }

    @Test
    fun base64ImageBecomesOpenAiDataUrlPart() {
        val content = json(
            """[{"type":"image","source":{"type":"base64","media_type":"image/png","data":"QUJD"}}]"""
        )
        val result = ok(parseAnthropicMessage("user", content))
        val part = result.openAiContent.single() as JsonObject
        assertEquals(JsonPrimitive("image_url"), part["type"])
        assertEquals(json("""{"url":"data:image/png;base64,QUJD"}"""), part["image_url"])
    }

    @Test
    fun urlImageSourceIsAnError() {
        val content = json("""[{"type":"image","source":{"type":"url","url":"https://example.com/a.png"}}]""")
        assertTrue(parseAnthropicMessage("user", content) is AnthropicParseResult.Error)
    }

    @Test
    fun toolUseBlockIsExtracted() {
        val content = json(
            """[{"type":"text","text":"checking"},
                {"type":"tool_use","id":"toolu_1","name":"get_weather","input":{"city":"Oulu"}}]"""
        )
        val result = ok(parseAnthropicMessage("assistant", content))
        assertEquals("checking", result.text)
        assertEquals(
            listOf(AnthropicToolUse("toolu_1", "get_weather", json("""{"city":"Oulu"}""") as JsonObject)),
            result.toolUses,
        )
    }

    @Test
    fun toolUseWithNonObjectInputIsAnError() {
        val content = json("""[{"type":"tool_use","id":"toolu_1","name":"x","input":"oops"}]""")
        assertTrue(parseAnthropicMessage("assistant", content) is AnthropicParseResult.Error)
    }

    @Test
    fun toolResultBlockIsExtractedWithFlattenedTextAndErrorFlag() {
        val content = json(
            """[{"type":"tool_result","tool_use_id":"toolu_1","is_error":true,
                 "content":[{"type":"text","text":"line one"},{"type":"text","text":"line two"}]}]"""
        )
        val result = ok(parseAnthropicMessage("user", content))
        assertEquals(listOf(AnthropicToolResult("toolu_1", "line one\nline two", true)), result.toolResults)
    }

    @Test
    fun toolResultWithoutContentOrErrorFlagDefaultsToEmptyAndFalse() {
        val content = json("""[{"type":"tool_result","tool_use_id":"toolu_2"}]""")
        val result = ok(parseAnthropicMessage("user", content))
        assertEquals(listOf(AnthropicToolResult("toolu_2", "", false)), result.toolResults)
    }

    @Test
    fun unknownBlockTypeIsAnErrorNotADrop() {
        val content = json("""[{"type":"text","text":"a"},{"type":"document","source":{}}]""")
        val result = parseAnthropicMessage("user", content)
        assertTrue(result is AnthropicParseResult.Error)
        assertTrue((result as AnthropicParseResult.Error).message.contains("document"))
    }

    @Test
    fun blockWithoutTypeIsAnError() {
        assertTrue(parseAnthropicMessage("user", json("""[{"text":"a"}]""")) is AnthropicParseResult.Error)
    }

    @Test
    fun toolChoiceAbsentIsAuto() {
        assertEquals(AnthropicToolChoice.Auto, anthropicToolChoice(null))
    }

    @Test
    fun toolChoiceObjectShapes() {
        assertEquals(AnthropicToolChoice.Auto, anthropicToolChoice(json("""{"type":"auto"}""")))
        assertEquals(AnthropicToolChoice.Any, anthropicToolChoice(json("""{"type":"any"}""")))
        assertEquals(AnthropicToolChoice.None, anthropicToolChoice(json("""{"type":"none"}""")))
        assertEquals(
            AnthropicToolChoice.Named("get_weather"),
            anthropicToolChoice(json("""{"type":"tool","name":"get_weather"}""")),
        )
    }

    @Test
    fun toolChoiceStringShapes() {
        assertEquals(AnthropicToolChoice.Any, anthropicToolChoice(JsonPrimitive("any")))
        assertEquals(AnthropicToolChoice.None, anthropicToolChoice(JsonPrimitive("none")))
    }

    @Test
    fun unrecognizedToolChoiceDefaultsToAuto() {
        assertEquals(AnthropicToolChoice.Auto, anthropicToolChoice(json("""{"type":"sometimes"}""")))
        assertEquals(AnthropicToolChoice.Auto, anthropicToolChoice(json("""{"type":"tool"}""")))
        assertEquals(AnthropicToolChoice.Auto, anthropicToolChoice(JsonPrimitive("whatever")))
        assertEquals(AnthropicToolChoice.Auto, anthropicToolChoice(json("[1]")))
    }
}

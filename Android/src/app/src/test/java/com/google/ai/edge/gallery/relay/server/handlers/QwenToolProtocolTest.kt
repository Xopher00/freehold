// Copyright 2026 Google LLC. SPDX-License-Identifier: Apache-2.0

package com.google.ai.edge.gallery.relay.server.handlers

import com.google.ai.edge.gallery.relay.server.AnthropicTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenToolProtocolTest {

    private val weather = AnthropicTool(
        name = "get_weather",
        description = "Current weather for a city",
        input_schema = Json.parseToJsonElement(
            """{"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}"""
        ),
    )

    private fun args(text: String) = Json.parseToJsonElement(text) as JsonObject

    private fun text(events: List<QwenScanEvent>): String =
        events.filterIsInstance<QwenScanEvent.Text>().joinToString("") { it.text }

    @Test
    fun preambleListsEachToolSignature() {
        val preamble = renderQwenToolsPreamble(listOf(weather), AnthropicToolChoice.Auto)
        assertTrue(preamble.startsWith("# Tools\n\n"))
        assertTrue(preamble.contains("<tools>\n"))
        assertTrue(preamble.contains("\"name\":\"get_weather\""))
        assertTrue(preamble.contains("\"description\":\"Current weather for a city\""))
        assertTrue(preamble.contains("\"required\":[\"city\"]"))
        assertTrue(preamble.contains("<tool_call></tool_call>"))
    }

    @Test
    fun nullSchemaFallsBackToEmptyObjectSchema() {
        val bare = AnthropicTool(name = "ping")
        val preamble = renderQwenToolsPreamble(listOf(bare), AnthropicToolChoice.Auto)
        assertTrue(preamble.contains("\"parameters\":{\"type\":\"object\",\"properties\":{}}"))
        assertFalse(preamble.contains("\"description\""))
    }

    @Test
    fun autoAndNoneAddNoRequirementLine() {
        listOf(AnthropicToolChoice.Auto, AnthropicToolChoice.None).forEach {
            assertFalse(renderQwenToolsPreamble(listOf(weather), it).contains("You must"))
        }
    }

    @Test
    fun anyRequiresSomeTool() {
        val preamble = renderQwenToolsPreamble(listOf(weather), AnthropicToolChoice.Any)
        assertTrue(preamble.endsWith("You must call one of the tools listed above."))
    }

    @Test
    fun namedRequiresThatTool() {
        val preamble = renderQwenToolsPreamble(listOf(weather), AnthropicToolChoice.Named("get_weather"))
        assertTrue(preamble.endsWith("You must call the tool named \"get_weather\"."))
    }

    @Test
    fun wholeCallInOneDelta() {
        val events = parseQwenToolCalls(
            "Sure.<tool_call>\n{\"name\": \"get_weather\", \"arguments\": {\"city\": \"Oulu\"}}\n</tool_call>"
        )
        assertEquals(
            listOf(QwenScanEvent.Text("Sure."), QwenScanEvent.ToolCall("get_weather", args("""{"city":"Oulu"}"""))),
            events,
        )
    }

    @Test
    fun splitInsideTheOpenTagIsHeldBack() {
        val scanner = QwenToolCallScanner()
        val first = scanner.accept("Checking <tool_")
        assertEquals(listOf(QwenScanEvent.Text("Checking ")), first)
        val second = scanner.accept("call>{\"name\":\"get_weather\",\"arguments\":{}}</tool_call> done")
        assertEquals(QwenScanEvent.ToolCall("get_weather", JsonObject(emptyMap())), second.first())
        assertEquals(" done", text(second + scanner.finish()))
    }

    @Test
    fun splitAcrossManySmallDeltas() {
        val raw = "a<tool_call>{\"name\":\"x\",\"arguments\":{\"n\":1}}</tool_call>b"
        val scanner = QwenToolCallScanner()
        val events = raw.chunked(3).flatMap { scanner.accept(it) } + scanner.finish()
        assertEquals("ab", text(events))
        assertEquals(listOf(QwenScanEvent.ToolCall("x", args("""{"n":1}"""))), events.filterIsInstance<QwenScanEvent.ToolCall>())
    }

    @Test
    fun argumentsGivenAsAJsonStringAreParsed() {
        val events = parseQwenToolCalls("<tool_call>{\"name\":\"x\",\"arguments\":\"{\\\"n\\\":2}\"}</tool_call>")
        assertEquals(listOf(QwenScanEvent.ToolCall("x", args("""{"n":2}"""))), events)
    }

    @Test
    fun invalidJsonBodyIsMalformed() {
        val events = parseQwenToolCalls("<tool_call>{not json</tool_call>")
        assertEquals(listOf(QwenScanEvent.Malformed("{not json")), events)
    }

    @Test
    fun bodyWithoutAStringNameIsMalformed() {
        val events = parseQwenToolCalls("<tool_call>{\"arguments\":{}}</tool_call>")
        assertTrue(events.single() is QwenScanEvent.Malformed)
    }

    @Test
    fun unterminatedCallIsMalformedAtFinish() {
        val scanner = QwenToolCallScanner()
        assertEquals(emptyList<QwenScanEvent>(), scanner.accept("<tool_call>{\"name\":\"x\""))
        assertEquals(listOf(QwenScanEvent.Malformed("{\"name\":\"x\"")), scanner.finish())
    }

    @Test
    fun loneAngleBracketInProseSurvivesAsText() {
        val scanner = QwenToolCallScanner()
        val events = scanner.accept("3 <") + scanner.accept(" 4 and x <") + scanner.finish()
        assertEquals("3 < 4 and x <", text(events))
        assertTrue(events.all { it is QwenScanEvent.Text })
    }
}

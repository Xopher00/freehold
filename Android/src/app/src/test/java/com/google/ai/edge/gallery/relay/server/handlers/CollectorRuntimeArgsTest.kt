// Copyright 2026 Google LLC. SPDX-License-Identifier: Apache-2.0

package com.google.ai.edge.gallery.relay.server.handlers

import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.relay.runtime.TurnUsageStore
import com.google.ai.edge.gallery.relay.runtime.maxOutputTokensFrom
import com.google.ai.edge.gallery.runtime.StructuredOutputRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CollectorRuntimeArgsTest {

    private val model = Model(name = "fake")

    @Before
    fun setUp() {
        TurnUsageStore.clear(model.name)
    }

    private fun collect(maxOutputTokens: Int?, responseFormat: StructuredOutputRequest?): FakeLlmModelHelper {
        val helper = FakeLlmModelHelper(deltas = listOf("ok"), completionTokens = 1)
        runBlocking {
            collectInferenceText(
                model = model,
                prompt = "hi",
                maxOutputTokens = maxOutputTokens,
                responseFormat = responseFormat,
                helper = helper,
            )
        }
        return helper
    }

    @Test
    fun maxTokensReachTheRuntimeThroughExtraContext() {
        val helper = collect(maxOutputTokens = 128, responseFormat = null)
        assertEquals(128, maxOutputTokensFrom(helper.lastExtraContext!!))
    }

    @Test
    fun noMaxTokensSendsEmptyExtraContext() {
        val helper = collect(maxOutputTokens = null, responseFormat = null)
        assertEquals(emptyMap<String, String>(), helper.lastExtraContext)
    }

    @Test
    fun responseSchemaReachesTheRuntimeAsJson() {
        val schema = """{"type":"object","properties":{"a":{"type":"string"}}}"""
        val helper = collect(maxOutputTokens = null, responseFormat = StructuredOutputRequest(schema))
        assertEquals(schema, helper.lastResponseJsonSchema)
    }

    @Test
    fun noResponseFormatSendsNoSchema() {
        assertNull(collect(maxOutputTokens = 64, responseFormat = null).lastResponseJsonSchema)
    }
}

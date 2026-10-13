// Copyright 2026 Google LLC. SPDX-License-Identifier: Apache-2.0

package com.google.ai.edge.gallery.relay.runtime

import com.google.ai.edge.gallery.runtime.EXTRA_CONTEXT_MAX_OUTPUT_TOKENS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaxOutputTokensTest {

    @Test
    fun capIsWrittenUnderUpstreamsKey() {
        assertEquals(mapOf(EXTRA_CONTEXT_MAX_OUTPUT_TOKENS to "256"), maxOutputTokensExtraContext(256))
    }

    @Test
    fun noCapWritesNothing() {
        assertEquals(emptyMap<String, String>(), maxOutputTokensExtraContext(null))
    }

    @Test
    fun capRoundTrips() {
        assertEquals(1, maxOutputTokensFrom(maxOutputTokensExtraContext(1)))
        assertEquals(8192, maxOutputTokensFrom(maxOutputTokensExtraContext(8192)))
    }

    @Test
    fun absentKeyIsNoCap() {
        assertNull(maxOutputTokensFrom(emptyMap()))
        assertNull(maxOutputTokensFrom(mapOf("enable_thinking" to "true")))
    }

    @Test
    fun zeroNegativeAndNonNumericAreNoCap() {
        listOf("0", "-5", "abc", "", "12.5").forEach {
            assertNull(it, maxOutputTokensFrom(mapOf(EXTRA_CONTEXT_MAX_OUTPUT_TOKENS to it)))
        }
    }

    @Test
    fun otherExtraContextKeysAreIgnored() {
        val context = mapOf("enable_thinking" to "true", EXTRA_CONTEXT_MAX_OUTPUT_TOKENS to "64")
        assertEquals(64, maxOutputTokensFrom(context))
    }
}

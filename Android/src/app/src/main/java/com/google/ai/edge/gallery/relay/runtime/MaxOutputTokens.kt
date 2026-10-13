// Copyright 2026 Google LLC. SPDX-License-Identifier: Apache-2.0

package com.google.ai.edge.gallery.relay.runtime

import com.google.ai.edge.gallery.runtime.EXTRA_CONTEXT_MAX_OUTPUT_TOKENS

// Upstream's LlmModelHelper carries a per-request token cap only as a string in extraContext.
fun maxOutputTokensExtraContext(maxOutputTokens: Int?): Map<String, String> =
  maxOutputTokens?.let { mapOf(EXTRA_CONTEXT_MAX_OUTPUT_TOKENS to it.toString()) } ?: emptyMap()

fun maxOutputTokensFrom(extraContext: Map<String, String>): Int? =
  extraContext[EXTRA_CONTEXT_MAX_OUTPUT_TOKENS]?.toIntOrNull()?.takeIf { it > 0 }

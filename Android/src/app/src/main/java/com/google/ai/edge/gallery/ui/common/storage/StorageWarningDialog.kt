// Copyright 2026 Google LLC. SPDX-License-Identifier: Apache-2.0

package com.google.ai.edge.gallery.ui.common.storage

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.relay.device.DeviceProfileEntryPoint
import com.google.ai.edge.gallery.ui.common.humanReadableSize
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import dagger.hilt.android.EntryPointAccessors

/** Warns that a download's size exceeds free device storage; offers proceed-anyway or cancel. */
@Composable
fun StorageWarningDialog(
  requiredBytes: Long,
  freeBytes: Long,
  onProceedAnyway: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    title = { Text(stringResource(R.string.storage_warning_title)) },
    text = {
      Text(
        stringResource(
          R.string.storage_warning_content,
          requiredBytes.humanReadableSize(),
          freeBytes.humanReadableSize(),
        )
      )
    },
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onProceedAnyway) {
        Text(stringResource(R.string.storage_warning_proceed_anyway))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

/**
 * Shows the storage warning of the download waiting for the user's answer in
 * [ModelManagerViewModel.uiState], if any. Hosted once above all screens, because every screen can
 * reach [ModelManagerViewModel.downloadModel].
 */
@Composable
fun StorageConfirmationHost(modelManagerViewModel: ModelManagerViewModel) {
  val uiState by modelManagerViewModel.uiState.collectAsState()
  val context = LocalContext.current
  // Plain @Singleton, not a ViewModel -- no hiltViewModel() entry point for it here.
  val deviceProfile =
    remember(context) {
      EntryPointAccessors.fromApplication(
          context.applicationContext,
          DeviceProfileEntryPoint::class.java,
        )
        .deviceProfile()
    }
  val model =
    uiState.modelNeedingStorageConfirmation?.let { modelManagerViewModel.getModelByName(it) }
      ?: return
  StorageWarningDialog(
    requiredBytes = model.downloadInfo.totalBytes,
    freeBytes = deviceProfile.freeStorageBytes(),
    onProceedAnyway = { modelManagerViewModel.proceedWithDownloadDespiteStorageWarning(model = model) },
    onDismiss = { modelManagerViewModel.dismissStorageConfirmation() },
  )
}

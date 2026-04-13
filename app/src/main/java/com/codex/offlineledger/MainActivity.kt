package com.codex.offlineledger

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codex.offlineledger.ui.LedgerApp
import com.codex.offlineledger.ui.LedgerViewModel
import com.codex.offlineledger.ui.theme.OfflineLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OfflineLedgerTheme {
                val viewModel: LedgerViewModel = viewModel(factory = LedgerViewModel.factory(application))
                val uiState = viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val createDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json"),
                ) { uri ->
                    if (uri != null) {
                        viewModel.exportToUri(uri)
                    }
                }
                val openDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        viewModel.importFromUri(uri)
                    }
                }
                val notificationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { }
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
                }

                LedgerApp(
                    state = uiState.value,
                    snackbarHostState = snackbarHostState,
                    onExport = { createDocumentLauncher.launch(viewModel.exportFileName()) },
                    onImport = { openDocumentLauncher.launch(arrayOf("application/json")) },
                    viewModel = viewModel,
                    context = context,
                )
            }
        }
    }
}

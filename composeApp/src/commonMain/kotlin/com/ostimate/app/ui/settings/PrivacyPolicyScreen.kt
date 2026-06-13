package com.ostimate.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 32.dp,
                    ),
        ) {
            PolicySection("Your data stays on your device") {
                "Ostimate stores all your supply and change event data locally on this device " +
                    "using a private database. Nothing is sent to any server, cloud service, " +
                    "or third party."
            }

            PolicySection("No analytics or tracking") {
                "Ostimate contains no analytics SDKs, no advertising networks, and no " +
                    "telemetry of any kind. We do not know how you use the app."
            }

            PolicySection("No account required") {
                "Ostimate works without creating an account or providing any personal " +
                    "information. Your name, email, and identity are never collected."
            }

            PolicySection("Backups") {
                "The Export feature creates a CSV file on your device that you can save " +
                    "wherever you choose. Ostimate does not upload backups anywhere automatically."
            }

            PolicySection("Data deletion") {
                "Uninstalling Ostimate removes all app data from your device. There is no " +
                    "data stored elsewhere to delete."
            }

            PolicySection("Contact") {
                "Questions about this policy? Email ostomate26@gmail.com."
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Last updated: June 2026",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun PolicySection(
    title: String,
    body: () -> String,
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        body(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
    )
    Spacer(Modifier.height(20.dp))
}

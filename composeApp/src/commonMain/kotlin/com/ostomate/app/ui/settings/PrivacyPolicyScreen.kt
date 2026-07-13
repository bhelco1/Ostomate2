package com.ostomate.app.ui.settings

import androidx.compose.foundation.layout.Column
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
import com.ostomate.app.resources.Res
import com.ostomate.app.resources.cd_back
import com.ostomate.app.resources.privacy_last_updated
import com.ostomate.app.resources.privacy_section_account_body
import com.ostomate.app.resources.privacy_section_account_title
import com.ostomate.app.resources.privacy_section_analytics_body
import com.ostomate.app.resources.privacy_section_analytics_title
import com.ostomate.app.resources.privacy_section_backups_body
import com.ostomate.app.resources.privacy_section_backups_title
import com.ostomate.app.resources.privacy_section_contact_body
import com.ostomate.app.resources.privacy_section_contact_title
import com.ostomate.app.resources.privacy_section_crash_body
import com.ostomate.app.resources.privacy_section_crash_title
import com.ostomate.app.resources.privacy_section_deletion_body
import com.ostomate.app.resources.privacy_section_deletion_title
import com.ostomate.app.resources.privacy_section_local_body
import com.ostomate.app.resources.privacy_section_local_title
import com.ostomate.app.resources.privacy_title
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                        )
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
            PolicySection(
                stringResource(Res.string.privacy_section_local_title),
                stringResource(Res.string.privacy_section_local_body),
            )
            PolicySection(
                stringResource(Res.string.privacy_section_analytics_title),
                stringResource(Res.string.privacy_section_analytics_body),
            )
            PolicySection(
                stringResource(Res.string.privacy_section_account_title),
                stringResource(Res.string.privacy_section_account_body),
            )
            PolicySection(
                stringResource(Res.string.privacy_section_backups_title),
                stringResource(Res.string.privacy_section_backups_body),
            )
            PolicySection(
                stringResource(Res.string.privacy_section_crash_title),
                stringResource(Res.string.privacy_section_crash_body),
            )
            PolicySection(
                stringResource(Res.string.privacy_section_deletion_title),
                stringResource(Res.string.privacy_section_deletion_body),
            )
            PolicySection(
                stringResource(Res.string.privacy_section_contact_title),
                stringResource(Res.string.privacy_section_contact_body),
            )

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.privacy_last_updated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PolicySection(
    title: String,
    body: String,
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
    )
    Spacer(Modifier.height(20.dp))
}

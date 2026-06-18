package com.ostomate.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ostomate.app.data.db.SupplyTypeEntity
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.platform.FileSharer
import com.ostomate.app.platform.rememberQrPrinter
import com.ostomate.app.resources.Res
import com.ostomate.app.resources.cd_back
import com.ostomate.app.resources.cd_qr_code_for
import com.ostomate.app.resources.cd_share_all_links
import com.ostomate.app.resources.qr_labels_no_supplies
import com.ostomate.app.resources.qr_labels_print
import com.ostomate.app.resources.qr_labels_share_link
import com.ostomate.app.resources.qr_labels_title
import com.ostomate.app.resources.qr_links_header
import com.ostomate.app.ui.theme.supplyColor
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrLabelsScreen(
    onBack: () -> Unit,
    viewModel: ManageSuppliesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val fileSharer = koinInject<FileSharer>()
    val qrPrinter = rememberQrPrinter()

    Scaffold(
        topBar = {
            val qrLinksHeader = stringResource(Res.string.qr_links_header)
            TopAppBar(
                title = { Text(stringResource(Res.string.qr_labels_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                        )
                    }
                },
                actions = {
                    if (qrPrinter.isPrintingAvailable()) {
                        TextButton(onClick = { qrPrinter.print(uiState.supplies) }) {
                            Text(stringResource(Res.string.qr_labels_print))
                        }
                    }
                    IconButton(
                        onClick = {
                            val urls =
                                uiState.supplies.joinToString("\n\n") { supply ->
                                    "${supply.name}\n${supplyDeepLinkUrl(supply)}"
                                }
                            fileSharer.shareText(
                                content = "$qrLinksHeader\n\n$urls",
                                fileName = "ostomate-qr-links.txt",
                                mimeType = "text/plain",
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(Res.string.cd_share_all_links))
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        if (uiState.supplies.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(Res.string.qr_labels_no_supplies), style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.supplies, key = { it.id }) { supply ->
                QrLabelCard(supply = supply, fileSharer = fileSharer)
            }
        }
    }
}

@Composable
private fun QrLabelCard(
    supply: SupplyTypeEntity,
    fileSharer: FileSharer,
) {
    val url = supplyDeepLinkUrl(supply)
    val accent = supplyColor(supply.kind, supply.colorIndex)
    val qrPainter = rememberQrCodePainter(url)

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                supply.name,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Image(
                painter = qrPainter,
                contentDescription = stringResource(Res.string.cd_qr_code_for, supply.name),
                modifier = Modifier.size(140.dp),
            )

            Spacer(Modifier.height(6.dp))

            Text(
                url,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            TextButton(
                onClick = {
                    fileSharer.shareText(
                        content = "${supply.name}\n$url",
                        fileName = "ostomate-qr-link.txt",
                        mimeType = "text/plain",
                    )
                },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.qr_labels_share_link), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun supplyDeepLinkUrl(supply: SupplyTypeEntity): String {
    val item =
        when (supply.kind) {
            SupplyKind.BAG -> "bag"
            SupplyKind.FLANGE -> "flange"
            SupplyKind.CUSTOM -> "id:${supply.id}"
        }
    return "ostomate://log?item=$item"
}

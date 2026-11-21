package com.ostomate.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ostomate.app.platform.NfcStatus
import com.ostomate.app.platform.rememberQrPrinter
import com.ostomate.app.resources.Res
import com.ostomate.app.resources.cd_back
import com.ostomate.app.resources.cd_qr_code_for
import com.ostomate.app.resources.cd_setup_sticker_for
import com.ostomate.app.resources.cd_write_sticker_for
import com.ostomate.app.resources.labels_no_supplies
import com.ostomate.app.resources.labels_print
import com.ostomate.app.resources.labels_setup_sticker
import com.ostomate.app.resources.labels_title
import com.ostomate.app.resources.labels_write_sticker
import com.ostomate.app.ui.theme.supplyColor
import com.ostomate.app.ui.theme.supplyPrintColor
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrColors
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings → Labels & stickers (FEAT-05): one row per item with its QR code and its NFC
 * sticker (written in the app on Android; setup steps on iPhone). The top bar's Print is the
 * one way to print codes: a sheet of 25 mm labels for every item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsScreen(
    onBack: () -> Unit,
    viewModel: LabelsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val qrPrinter = rememberQrPrinter()

    StickerWritingLifecycle(viewModel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.labels_title)) },
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
                        TextButton(onClick = { qrPrinter.print(uiState.items.map { it.supply }) }) {
                            Text(stringResource(Res.string.labels_print))
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        if (uiState.items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(Res.string.labels_no_supplies), style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.items, key = { it.supply.id }) { item ->
                LabelRow(
                    item = item,
                    nfcStatus = uiState.nfcStatus,
                    onSticker = { viewModel.openSticker(item) },
                )
            }
        }
    }

    uiState.sheet?.let { sheet ->
        Dialog(onDismissRequest = viewModel::dismissSticker) {
            StickerCard(
                sheet = sheet,
                onClose = viewModel::dismissSticker,
                onWriteAgain = viewModel::writeAgain,
                onOpenNfcSettings = viewModel::openNfcSettings,
            )
        }
    }
}

/** Reader mode follows the screen: off while it is paused (e.g. in NFC settings), back on return. */
@Composable
private fun StickerWritingLifecycle(viewModel: LabelsViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> viewModel.onScreenResumed()
                    Lifecycle.Event.ON_PAUSE -> viewModel.onScreenPaused()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Leaving the screen (or recreating it) must not leave reader mode on; an open
            // dialog survives a rotation and resumes, and onCleared covers leaving for good.
            viewModel.onScreenPaused()
        }
    }
}

@Composable
internal fun LabelRow(
    item: LabelItem,
    nfcStatus: NfcStatus,
    onSticker: () -> Unit,
) {
    val supply = item.supply
    val accent = supplyColor(supply.kind, supply.colorIndex)
    // The print colour, not the theme's: what's on screen matches the printed code.
    val qrColor = supplyPrintColor(supply.kind, supply.colorIndex)
    val qrPainter =
        rememberQrCodePainter(
            item.link,
            colors = QrColors(dark = QrBrush.solid(qrColor), light = QrBrush.solid(Color.White)),
        )

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = qrPainter,
                contentDescription = stringResource(Res.string.cd_qr_code_for, supply.name),
                modifier = Modifier.size(104.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(supply.name, style = MaterialTheme.typography.titleMedium, color = accent)
                Spacer(Modifier.height(2.dp))
                Text(
                    item.link,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (nfcStatus != NfcStatus.NO_HARDWARE) {
                    Spacer(Modifier.height(4.dp))
                    StickerButton(
                        supplyName = supply.name,
                        inApp = nfcStatus != NfcStatus.NOT_IN_APP,
                        onClick = onSticker,
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerButton(
    supplyName: String,
    inApp: Boolean,
    onClick: () -> Unit,
) {
    val label =
        stringResource(if (inApp) Res.string.cd_write_sticker_for else Res.string.cd_setup_sticker_for, supplyName)
    TextButton(
        onClick = onClick,
        modifier = Modifier.testTag("stickerButton").semantics { contentDescription = label },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        Text(stringResource(if (inApp) Res.string.labels_write_sticker else Res.string.labels_setup_sticker))
    }
}

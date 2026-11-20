package com.ostomate.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ostomate.app.domain.StickerWriteOutcome
import com.ostomate.app.resources.Res
import com.ostomate.app.resources.sticker_close
import com.ostomate.app.resources.sticker_copy_link
import com.ostomate.app.resources.sticker_ios_intro
import com.ostomate.app.resources.sticker_ios_step_automation
import com.ostomate.app.resources.sticker_ios_step_test
import com.ostomate.app.resources.sticker_ios_step_write
import com.ostomate.app.resources.sticker_link_copied
import com.ostomate.app.resources.sticker_locked
import com.ostomate.app.resources.sticker_lost_contact
import com.ostomate.app.resources.sticker_nfc_off
import com.ostomate.app.resources.sticker_not_confirmed
import com.ostomate.app.resources.sticker_not_logging
import com.ostomate.app.resources.sticker_open_nfc_settings
import com.ostomate.app.resources.sticker_title
import com.ostomate.app.resources.sticker_too_small
import com.ostomate.app.resources.sticker_try_again
import com.ostomate.app.resources.sticker_unsupported
import com.ostomate.app.resources.sticker_waiting
import com.ostomate.app.resources.sticker_write_another
import com.ostomate.app.resources.sticker_written
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The sticker dialog's card: Android write progress and outcome, or the iPhone setup steps. */
@Composable
internal fun StickerCard(
    sheet: StickerSheet,
    onClose: () -> Unit,
    onWriteAgain: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                stringResource(Res.string.sticker_title, sheet.item.supply.name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(16.dp))
            when (sheet) {
                is StickerSheet.Write -> WriteBody(sheet, onClose, onWriteAgain, onOpenNfcSettings)
                is StickerSheet.Steps -> StepsBody(sheet.item, onClose)
            }
        }
    }
}

@Composable
private fun WriteBody(
    sheet: StickerSheet.Write,
    onClose: () -> Unit,
    onWriteAgain: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    val name = sheet.item.supply.name
    when (val phase = sheet.phase) {
        WritePhase.Waiting -> {
            Text(stringResource(Res.string.sticker_waiting), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.sticker_not_logging),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Buttons(onClose = onClose)
        }
        WritePhase.NfcOff -> {
            Text(stringResource(Res.string.sticker_nfc_off), style = MaterialTheme.typography.bodyLarge)
            Buttons(onClose = onClose, action = Res.string.sticker_open_nfc_settings, onAction = onOpenNfcSettings)
        }
        is WritePhase.Done -> {
            val written = phase.outcome == StickerWriteOutcome.WRITTEN
            Text(
                if (written) {
                    stringResource(
                        Res.string.sticker_written,
                        name,
                    )
                } else {
                    stringResource(phase.outcome.message())
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (written) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("stickerOutcome"),
            )
            Buttons(
                onClose = onClose,
                action = if (written) Res.string.sticker_write_another else Res.string.sticker_try_again,
                onAction = onWriteAgain,
            )
        }
    }
}

private fun StickerWriteOutcome.message(): StringResource =
    when (this) {
        StickerWriteOutcome.WRITTEN -> Res.string.sticker_written
        StickerWriteOutcome.LOCKED -> Res.string.sticker_locked
        StickerWriteOutcome.TOO_SMALL -> Res.string.sticker_too_small
        StickerWriteOutcome.LOST_CONTACT -> Res.string.sticker_lost_contact
        StickerWriteOutcome.UNSUPPORTED -> Res.string.sticker_unsupported
        StickerWriteOutcome.NOT_CONFIRMED -> Res.string.sticker_not_confirmed
    }

@Composable
private fun StepsBody(
    item: LabelItem,
    onClose: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(item.nfcLink) { mutableStateOf(false) }
    val body = MaterialTheme.typography.bodyMedium

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(Res.string.sticker_ios_intro), style = body)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.sticker_ios_step_write), style = body)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.sticker_ios_step_automation), style = body)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.sticker_ios_step_test, item.supply.name), style = body)
        Spacer(Modifier.height(16.dp))
        SelectionContainer {
            Text(
                item.nfcLink,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(plainTextClip(item.nfcLink))
                        copied = true
                    }
                },
                modifier = Modifier.testTag("copyStickerLink"),
            ) { Text(stringResource(Res.string.sticker_copy_link)) }
            if (copied) {
                Text(
                    stringResource(Res.string.sticker_link_copied),
                    style = body,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    Buttons(onClose = onClose)
}

@Composable
private fun Buttons(
    onClose: () -> Unit,
    action: StringResource? = null,
    onAction: () -> Unit = {},
) {
    Spacer(Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClose) { Text(stringResource(Res.string.sticker_close)) }
        if (action != null) {
            TextButton(onClick = onAction) { Text(stringResource(action)) }
        }
    }
}

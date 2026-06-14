package com.ostomate.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.resources.Res
import com.ostomate.app.resources.onboarding_back
import com.ostomate.app.resources.onboarding_bag_count_label
import com.ostomate.app.resources.onboarding_counts_subtitle
import com.ostomate.app.resources.onboarding_counts_title
import com.ostomate.app.resources.onboarding_flange_count_label
import com.ostomate.app.resources.onboarding_get_started
import com.ostomate.app.resources.onboarding_kind_bag
import com.ostomate.app.resources.onboarding_kind_flange
import com.ostomate.app.resources.onboarding_next
import com.ostomate.app.resources.onboarding_print_later
import com.ostomate.app.resources.onboarding_qr_body
import com.ostomate.app.resources.onboarding_qr_hint
import com.ostomate.app.resources.onboarding_qr_title
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ostomate.app.resources.onboarding_skip
import com.ostomate.app.resources.onboarding_step_indicator
import com.ostomate.app.resources.onboarding_supplies_subtitle
import com.ostomate.app.resources.onboarding_supplies_title
import com.ostomate.app.ui.theme.supplyColor
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
    ) {
        // Skip link (top-right)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                viewModel.skip()
                onDone()
            }) {
                Text(stringResource(Res.string.onboarding_skip))
            }
        }

        Spacer(Modifier.height(16.dp))

        when (uiState.step) {
            OnboardingStep.SUPPLIES ->
                SuppliesStep(
                    selectedKinds = uiState.selectedKinds,
                    onToggle = viewModel::toggleKind,
                    onNext = viewModel::nextStep,
                )

            OnboardingStep.COUNTS ->
                CountsStep(
                    selectedKinds = uiState.selectedKinds,
                    bagCount = uiState.bagCount,
                    flangeCount = uiState.flangeCount,
                    onBagCountChange = viewModel::setBagCount,
                    onFlangeCountChange = viewModel::setFlangeCount,
                    onBack = viewModel::prevStep,
                    onNext = viewModel::nextStep,
                )

            OnboardingStep.QR_EXPLAINER ->
                QrExplainerStep(
                    onBack = viewModel::prevStep,
                    onDone = {
                        viewModel.finish()
                        onDone()
                    },
                )
        }
    }
}

@Composable
private fun SuppliesStep(
    selectedKinds: Set<SupplyKind>,
    onToggle: (SupplyKind) -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            StepIndicator(step = 1)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(Res.string.onboarding_supplies_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.onboarding_supplies_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            listOf(
                SupplyKind.BAG to stringResource(Res.string.onboarding_kind_bag),
                SupplyKind.FLANGE to stringResource(Res.string.onboarding_kind_flange),
            ).forEach { (kind, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(
                        Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(supplyColor(kind))
                            .semantics { contentDescription = "" },
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Checkbox(
                        checked = kind in selectedKinds,
                        onCheckedChange = { onToggle(kind) },
                        colors = CheckboxDefaults.colors(checkedColor = supplyColor(kind)),
                    )
                }
            }
        }

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = selectedKinds.isNotEmpty(),
        ) {
            Text(stringResource(Res.string.onboarding_next))
        }
    }
}

@Composable
private fun CountsStep(
    selectedKinds: Set<SupplyKind>,
    bagCount: String,
    flangeCount: String,
    onBagCountChange: (String) -> Unit,
    onFlangeCountChange: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            StepIndicator(step = 2)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(Res.string.onboarding_counts_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.onboarding_counts_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            if (SupplyKind.BAG in selectedKinds) {
                OutlinedTextField(
                    value = bagCount,
                    onValueChange = onBagCountChange,
                    label = { Text(stringResource(Res.string.onboarding_bag_count_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }
            if (SupplyKind.FLANGE in selectedKinds) {
                OutlinedTextField(
                    value = flangeCount,
                    onValueChange = onFlangeCountChange,
                    label = { Text(stringResource(Res.string.onboarding_flange_count_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.onboarding_back)) }
            Button(onClick = onNext, modifier = Modifier.weight(2f).height(52.dp)) { Text(stringResource(Res.string.onboarding_next)) }
        }
    }
}

@Composable
private fun QrExplainerStep(
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            StepIndicator(step = 3)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(Res.string.onboarding_qr_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.onboarding_qr_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(Res.string.onboarding_qr_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(Res.string.onboarding_get_started))
            }
            TextButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.onboarding_print_later))
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    val label = stringResource(Res.string.onboarding_step_indicator, step)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        (1..3).forEach { i ->
            Spacer(
                Modifier
                    .size(width = if (i == step) 24.dp else 8.dp, height = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (i == step) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        },
                    ),
            )
        }
    }
}

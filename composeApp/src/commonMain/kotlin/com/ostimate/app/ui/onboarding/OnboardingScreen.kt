package com.ostimate.app.ui.onboarding

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
import com.ostimate.app.domain.SupplyKind
import com.ostimate.app.ui.theme.supplyColor
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
                Text("Skip")
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
                "What supplies do you use?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Select all that apply. You can add more later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(32.dp))

            listOf(SupplyKind.BAG to "Pouches / Bags", SupplyKind.FLANGE to "Flanges / Wafers").forEach { (kind, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(
                        Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(supplyColor(kind)),
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
            Text("Next →")
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
                "How many do you have on hand?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter your current stock. You can update this anytime in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(32.dp))

            if (SupplyKind.BAG in selectedKinds) {
                OutlinedTextField(
                    value = bagCount,
                    onValueChange = onBagCountChange,
                    label = { Text("Pouches / Bags on hand") },
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
                    label = { Text("Flanges / Wafers on hand") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("← Back") }
            Button(onClick = onNext, modifier = Modifier.weight(2f).height(52.dp)) { Text("Next →") }
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
                "Log in under 2 seconds",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Print a QR sticker and stick it on your supply box. Scan it with your camera and the change is logged instantly — no app navigation required.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "You can print labels anytime from Settings → Print QR Labels.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Get started")
            }
            TextButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Print labels later")
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

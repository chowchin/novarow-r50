package com.chowchin.r50.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun FTMSConfigurationScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    initialFtmsEnabled: Boolean = false,
    initialCadenceRatio: Float = 1.0f,
    onSaveConfig: (enabled: Boolean, cadenceRatio: Float) -> Unit,
) {
    var ftmsEnabled by remember { mutableStateOf(initialFtmsEnabled) }
    var cadenceRatio by remember { mutableStateOf(initialCadenceRatio.toString()) }
    var showSavedMessage by remember { mutableStateOf(false) }
    var showAutoSaveIndicator by remember { mutableStateOf(false) }

    // Helper function to trigger auto-save with visual feedback
    fun triggerAutoSave() {
        val ratio = cadenceRatio.toFloatOrNull() ?: 1.0f
        onSaveConfig(ftmsEnabled, ratio)
        showAutoSaveIndicator = true
    }

    // Auto-hide the auto-save indicator after 1.5 seconds
    LaunchedEffect(showAutoSaveIndicator) {
        if (showAutoSaveIndicator) {
            kotlinx.coroutines.delay(1500)
            showAutoSaveIndicator = false
        }
    }

    Column(
        modifier =
            modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.material3.TextButton(
                onClick = onBack,
            ) {
                Text("← Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "FTMS Configuration",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = "Fitness Machine Service (FTMS)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Text(
                    text = "FTMS allows fitness apps like Zwift, TrainerRoad, and others to connect to your rowing machine data via Bluetooth Low Energy.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Enable FTMS Service",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = ftmsEnabled,
                        onCheckedChange = {
                            ftmsEnabled = it
                            triggerAutoSave()
                        },
                    )
                }

                if (ftmsEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Stroke Rate to Cadence Ratio",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    
                    Text(
                        text = "Multiplier to convert Strokes Per Minute (SPM) to Cadence. Default is 1.0 (1:1 ratio).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    
                    OutlinedTextField(
                        value = cadenceRatio,
                        onValueChange = { newValue ->
                            // Allow only numbers and decimal point
                            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                cadenceRatio = newValue
                                triggerAutoSave()
                            }
                        },
                        label = { Text("Cadence Ratio") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        placeholder = { Text("1.0") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    
                    Text(
                        text = "Example: Ratio of 2.0 means SPM of 20 becomes Cadence of 40",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (ftmsEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "How to use FTMS",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    Text(
                        text = "1. Enable FTMS and save your settings",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )

                    Text(
                        text = "2. Connect your R50 rowing machine to this app",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )

                    Text(
                        text = "3. In your fitness app (e.g., Zwift), look for a Bluetooth rowing machine called 'R50 FTMS Bridge'",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )

                    Text(
                        text = "4. Pair and start your workout!",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Compatible Apps:",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )

                    Text(
                        text = "• Zwift\n• TrainerRoad\n• Kinomap\n• MyWhoosh\n• Any app supporting FTMS rowing machines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Auto-save indicator
        if (showAutoSaveIndicator) {
            Text(
                text = "⚡ FTMS settings saved automatically",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (showSavedMessage) {
            Text(
                text = "✓ FTMS settings saved",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
            )
        }

        // Version display at the bottom
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "R50 Connector with FTMS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

package com.dino.nanoplayground.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun SettingScreen() {
    val viewModel: BleSettingsViewModel = hiltViewModel()

    val isBleRunning by viewModel.isBleRunning.collectAsState()
    val connectedCount by viewModel.connectedCentralsCount.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val advertiseWifi by viewModel.advertiseWifiInfo.collectAsState()
    val advertiseError by viewModel.advertiseError.collectAsState()

    var showNameDialog by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(viewModel.hasRequiredPermissions()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (hasPermissions && !isBleRunning) {
            viewModel.toggleBleServer(true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "BLE GATT Server",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        if (!viewModel.isBleAvailable) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                )
            ) {
                Text(
                    text = "Bluetooth is not available on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            // Enable / disable toggle
            BleSettingRow(
                title = "Enable BLE Server",
                subtitle = if (isBleRunning) "Advertising as \"$deviceName\"" else "Start GATT peripheral",
                icon = if (isBleRunning) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                trailing = {
                    Switch(
                        checked = isBleRunning,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasPermissions) {
                                permissionLauncher.launch(viewModel.requiredPermissions)
                            } else {
                                viewModel.toggleBleServer(enabled)
                            }
                        },
                    )
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Advertising failure banner (shown when advertise returned an error)
            if (advertiseError != null) {
                Card(
                    modifier = Modifier.padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = advertiseError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // BLE device name
            BleSettingRow(
                title = "BLE Device Name",
                subtitle = deviceName,
                icon = Icons.Default.Edit,
                trailing = {
                    TextButton(onClick = { showNameDialog = true }) { Text("Edit") }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Advertise Wi-Fi discovery info
            BleSettingRow(
                title = "Advertise Wi-Fi Info",
                subtitle = "Return HTTP server address via Config characteristic",
                icon = Icons.Default.Wifi,
                trailing = {
                    Switch(
                        checked = advertiseWifi,
                        onCheckedChange = { viewModel.setAdvertiseWifiInfo(it) },
                    )
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Connected centrals counter
            BleSettingRow(
                title = "Connected Centrals",
                subtitle = "$connectedCount / 3 active connections",
                icon = Icons.Default.DeviceHub,
                trailing = {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(
                            text = "$connectedCount",
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
            )

            // Permissions notice (shown when not yet granted)
            if (!hasPermissions) {
                Card(
                    modifier = Modifier.padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Bluetooth permissions required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "BLUETOOTH_ADVERTISE and BLUETOOTH_CONNECT are needed to run the BLE server.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { permissionLauncher.launch(viewModel.requiredPermissions) }
                        ) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }

            // Transport constraints info card
            Card(
                modifier = Modifier.padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BLE Transport Notes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Throughput ~200–400 kbps; 500-token response may take 2–5 s\n" +
                                "• Recommend prompts ≤ 2,000 characters; use HTTP for long contexts\n" +
                                "• Max 3 concurrent clients; inference is serialized\n" +
                                "• Compatible with iOS CoreBluetooth and ESP32/Arduino BLE clients",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    // Edit device name dialog
    if (showNameDialog) {
        var nameInput by remember { mutableStateOf(deviceName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("BLE Device Name") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Device name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDeviceName(nameInput)
                    showNameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BleSettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        trailing()
    }
}

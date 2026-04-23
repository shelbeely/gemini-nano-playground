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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.RouterOutlined
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun SettingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HttpServerSection()
        Spacer(modifier = Modifier.height(8.dp))
        BleServerSection()
    }
}

// ── HTTP Server section ────────────────────────────────────────────────────────

@Composable
private fun HttpServerSection() {
    val viewModel: HttpSettingsViewModel = hiltViewModel()

    val isRunning by viewModel.isServerRunning.collectAsState()
    val port by viewModel.port.collectAsState()
    val wifiIp by viewModel.wifiIp.collectAsState()
    val bearerToken by viewModel.bearerToken.collectAsState()

    var showPortDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenVisible by remember { mutableStateOf(false) }

    SectionHeader("HTTP Server (OpenAI-compatible API)")

    // Enable / disable
    SettingRow(
        title = "Enable HTTP Server",
        subtitle = if (isRunning && wifiIp.isNotEmpty())
            "http://$wifiIp:$port/v1" else "Start Wi-Fi server",
        icon = if (isRunning) Icons.Default.Router else Icons.Default.RouterOutlined,
        trailing = {
            Switch(checked = isRunning, onCheckedChange = { viewModel.toggleServer(it) })
        },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    // Port
    SettingRow(
        title = "Port",
        subtitle = "$port",
        icon = Icons.Default.Settings,
        trailing = {
            TextButton(onClick = { showPortDialog = true }) { Text("Edit") }
        },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    // Wi-Fi IP display
    if (isRunning && wifiIp.isNotEmpty()) {
        SettingRow(
            title = "Wi-Fi Address",
            subtitle = "http://$wifiIp:$port",
            icon = Icons.Default.Wifi,
            trailing = {},
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }

    // Bearer token
    SettingRow(
        title = "Bearer Token",
        subtitle = if (bearerToken.isEmpty()) "No auth (open access)" else "Token set — auth required",
        icon = Icons.Default.Key,
        trailing = {
            TextButton(onClick = { showTokenDialog = true }) { Text("Edit") }
        },
    )

    // Info card
    Card(
        modifier = Modifier.padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "API Endpoints",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "• GET  /v1/models\n" +
                        "• POST /v1/chat/completions\n" +
                        "  – stream:false → single JSON response\n" +
                        "  – stream:true  → text/event-stream (SSE)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    // Port edit dialog
    if (showPortDialog) {
        var portInput by remember { mutableStateOf(port.toString()) }
        AlertDialog(
            onDismissRequest = { showPortDialog = false },
            title = { Text("Server Port") },
            text = {
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Port (1024–65535)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    portInput.toIntOrNull()?.let { viewModel.setPort(it) }
                    showPortDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPortDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Token edit dialog
    if (showTokenDialog) {
        var tokenInput by remember { mutableStateOf(bearerToken) }
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Bearer Token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Leave empty to disable authentication.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Token") },
                        singleLine = true,
                        visualTransformation = if (tokenVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { tokenVisible = !tokenVisible }) {
                                Text(if (tokenVisible) "Hide" else "Show")
                            }
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setBearerToken(tokenInput)
                    showTokenDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ── BLE Server section ─────────────────────────────────────────────────────────

@Composable
private fun BleServerSection() {
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

    SectionHeader("BLE GATT Server")

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
        return
    }

    // Enable / disable toggle
    SettingRow(
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

    // Advertising failure banner
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
    SettingRow(
        title = "BLE Device Name",
        subtitle = deviceName,
        icon = Icons.Default.Edit,
        trailing = {
            TextButton(onClick = { showNameDialog = true }) { Text("Edit") }
        },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    // Advertise Wi-Fi discovery info
    SettingRow(
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
    SettingRow(
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

    // Permissions notice
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

// ── Shared components ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
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


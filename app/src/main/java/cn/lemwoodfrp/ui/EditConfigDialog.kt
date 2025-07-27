package cn.lemwoodfrp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.lemwoodfrp.R
import cn.lemwoodfrp.model.FRPConfig
import cn.lemwoodfrp.model.FRPType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditConfigDialog(
    config: FRPConfig,
    onDismiss: () -> Unit,
    onConfirm: (FRPConfig) -> Unit
) {
    var name by remember { mutableStateOf(config.name) }
    var serverAddr by remember { mutableStateOf(config.serverAddr) }
    var serverPort by remember { mutableStateOf(config.serverPort.toString()) }
    var localPort by remember { mutableStateOf((config.localPort ?: 0).toString()) }
    var remotePort by remember { mutableStateOf((config.remotePort ?: 0).toString()) }
    var protocol by remember { mutableStateOf(config.protocol) }
    var proxyType by remember { mutableStateOf(config.proxyType) }
    var token by remember { mutableStateOf(config.token ?: "") }
    var autoStart by remember { mutableStateOf(config.autoStart) }
    var customDomain by remember { mutableStateOf(config.customDomain) } // 喵～
    var subdomain by remember { mutableStateOf(config.subdomain) } // AWA
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                if (config.type == FRPType.CLIENT) 
                    stringResource(R.string.edit_frpc_config) 
                else 
                    stringResource(R.string.edit_frps_config)
            ) 
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.config_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = serverAddr,
                    onValueChange = { serverAddr = it },
                    label = { Text(stringResource(R.string.server_address)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = serverPort,
                    onValueChange = { serverPort = it },
                    label = { Text(stringResource(R.string.server_port)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                
                // Token字段 - 用于身份验证 喵～
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.token)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                if (config.type == FRPType.CLIENT) {
                    OutlinedTextField(
                        value = localPort,
                        onValueChange = { localPort = it },
                        label = { Text(stringResource(R.string.local_port)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = remotePort,
                        onValueChange = { remotePort = it },
                        label = { Text(stringResource(R.string.remote_port)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    
                    // 代理类型选择 AWA
                    var expanded by remember { mutableStateOf(false) }
                    val proxyTypes = listOf("tcp", "udp", "http", "https", "stcp", "sudp", "xtcp")
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = proxyType,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("代理类型") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            proxyTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        proxyType = type
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = protocol,
                        onValueChange = { protocol = it },
                        label = { Text(stringResource(R.string.protocol)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // HTTP/HTTPS 代理的自定义域名和子域名 qwq
                    if (proxyType == "http" || proxyType == "https") {
                        OutlinedTextField(
                            value = customDomain,
                            onValueChange = { customDomain = it },
                            label = { Text("自定义域名") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = subdomain,
                            onValueChange = { subdomain = it },
                            label = { Text("子域名") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.auto_start),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoStart,
                        onCheckedChange = { autoStart = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedConfig = config.copy(
                        name = name,
                        serverAddr = serverAddr,
                        serverPort = serverPort.toIntOrNull() ?: 0,
                        localPort = localPort.toIntOrNull() ?: 0,
                        remotePort = remotePort.toIntOrNull() ?: 0,
                        protocol = protocol,
                        proxyType = proxyType,
                        token = if (token.isNotBlank()) token else null,
                        autoStart = autoStart,
                        customDomain = customDomain,
                        subdomain = subdomain
                    )
                    onConfirm(updatedConfig)
                },
                enabled = name.isNotBlank() && serverAddr.isNotBlank() && serverPort.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
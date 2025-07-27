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
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConfigDialog(
    type: FRPType,
    onDismiss: () -> Unit,
    onConfirm: (FRPConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var serverAddr by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("") }
    var localPort by remember { mutableStateOf("") }
    var remotePort by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("tcp") }
    var autoStart by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                if (type == FRPType.CLIENT) 
                    stringResource(R.string.add_frpc_config) 
                else 
                    stringResource(R.string.add_frps_config)
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
                
                if (type == FRPType.CLIENT) {
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
                    
                    OutlinedTextField(
                        value = protocol,
                        onValueChange = { protocol = it },
                        label = { Text(stringResource(R.string.protocol)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
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
                    val config = FRPConfig(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        type = type,
                        serverAddr = serverAddr,
                        serverPort = serverPort.toIntOrNull() ?: 0,
                        localPort = localPort.toIntOrNull() ?: 0,
                        remotePort = remotePort.toIntOrNull() ?: 0,
                        protocol = protocol,
                        autoStart = autoStart
                    )
                    onConfirm(config)
                },
                enabled = name.isNotBlank() && serverAddr.isNotBlank() && serverPort.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
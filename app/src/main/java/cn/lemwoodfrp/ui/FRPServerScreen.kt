package cn.lemwoodfrp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.lemwoodfrp.R
import cn.lemwoodfrp.model.FRPConfig
import cn.lemwoodfrp.model.FRPType
import cn.lemwoodfrp.utils.ConfigManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FRPServerScreen() {
    val context = LocalContext.current
    var configs by remember { mutableStateOf(emptyList<FRPConfig>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        configs = ConfigManager.getAllConfigs(context).filter { it.type == FRPType.SERVER }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.frps_title),
                style = MaterialTheme.typography.headlineSmall
            )
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_config))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (configs.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_frps_configs),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(configs) { config ->
                    FRPConfigCard(
                        config = config,
                        onEdit = { /* TODO: 实现编辑功能 */ },
                        onDelete = { 
                            ConfigManager.deleteConfig(context, config.id)
                            configs = ConfigManager.getAllConfigs(context).filter { it.type == FRPType.SERVER }
                        },
                        onToggle = { /* TODO: 实现启动/停止功能 */ }
                    )
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddConfigDialog(
            type = FRPType.SERVER,
            onDismiss = { showAddDialog = false },
            onConfirm = { config ->
                ConfigManager.addConfig(context, config)
                configs = ConfigManager.getAllConfigs(context).filter { it.type == FRPType.SERVER }
                showAddDialog = false
            }
        )
    }
}
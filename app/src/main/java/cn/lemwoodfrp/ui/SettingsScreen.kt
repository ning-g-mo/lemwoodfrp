package cn.lemwoodfrp.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import cn.lemwoodfrp.service.FRPService
import cn.lemwoodfrp.utils.BatteryOptimizationUtils
import cn.lemwoodfrp.utils.ConfigManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var autoStart by remember { mutableStateOf(ConfigManager.getAutoStart(context)) }
    var isBatteryOptimized by remember { mutableStateOf(!BatteryOptimizationUtils.isIgnoringBatteryOptimizations(context)) }
    var showDiagnosisDialog by remember { mutableStateOf(false) }
    var diagnosisReport by remember { mutableStateOf("") }
    var isLoadingDiagnosis by remember { mutableStateOf(false) }
    
    // Service连接状态 qwq
    var frpService by remember { mutableStateOf<FRPService?>(null) }
    var isServiceBound by remember { mutableStateOf(false) }
    
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? FRPService.FRPBinder
                frpService = binder?.getService()
                isServiceBound = true
            }
            
            override fun onServiceDisconnected(name: ComponentName?) {
                frpService = null
                isServiceBound = false
            }
        }
    }
    
    // 绑定服务 AWA
    LaunchedEffect(Unit) {
        val intent = Intent(context, FRPService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            if (isServiceBound) {
                context.unbindService(serviceConnection)
            }
        }
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "环境诊断 qwq",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "检查Termux、PRoot和FRP环境的完整性 AWA",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    OutlinedButton(
                        onClick = {
                            if (frpService != null) {
                                isLoadingDiagnosis = true
                                diagnosisReport = frpService!!.diagnoseAllEnvironments()
                                isLoadingDiagnosis = false
                                showDiagnosisDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = frpService != null && !isLoadingDiagnosis
                    ) {
                        if (isLoadingDiagnosis) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isLoadingDiagnosis) "诊断中..." else "开始环境诊断 喵～")
                    }
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.general_settings),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.auto_start),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.auto_start_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoStart,
                            onCheckedChange = { 
                                autoStart = it
                                ConfigManager.setAutoStart(context, it)
                            }
                        )
                    }
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.battery_optimization),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isBatteryOptimized) 
                                    stringResource(R.string.battery_optimization_enabled) 
                                else 
                                    stringResource(R.string.battery_optimization_disabled),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.battery_optimization_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (isBatteryOptimized) {
                            Button(
                                onClick = {
                                    BatteryOptimizationUtils.requestIgnoreBatteryOptimizations(context)
                                }
                            ) {
                                Text(stringResource(R.string.disable_optimization))
                            }
                        } else {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_management),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    OutlinedButton(
                        onClick = {
                            BatteryOptimizationUtils.openAppDetailsSettings(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.app_settings))
                    }
                }
            }
        }
    }
    
    // 环境诊断结果对话框 qwq
    if (showDiagnosisDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosisDialog = false },
            title = {
                Text("环境诊断报告 AWA")
            },
            text = {
                LazyColumn {
                    item {
                        Text(
                            text = diagnosisReport,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showDiagnosisDialog = false }
                ) {
                    Text("关闭 喵～")
                }
            }
        )
    }
}
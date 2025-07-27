package cn.lemwoodfrp.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.lemwoodfrp.utils.LogManager
import cn.lemwoodfrp.ui.theme.LemwoodFRPTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class LogViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LemwoodFRPTheme {
                LogViewerScreen(
                    onBack = { finish() },
                    onExportLogs = { exportLogs() }
                )
            }
        }
    }
    
    private fun exportLogs() {
        val logFile = LogManager.exportLogs(this)
        if (logFile != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                    this@LogViewerActivity,
                    "${packageName}.fileprovider",
                    logFile
                ))
                putExtra(Intent.EXTRA_SUBJECT, "LemwoodFRP 日志")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "导出日志"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onBack: () -> Unit,
    onExportLogs: () -> Unit
) {
    val context = LocalContext.current
    val logs by LogManager.logs.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 自动滚动到最新日志 qwq
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(logs.size - 1)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志查看器 AWA") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { LogManager.clearLogs(context) }) {
                        Icon(Icons.Default.Clear, contentDescription = "清空日志")
                    }
                    IconButton(onClick = onExportLogs) {
                        Icon(Icons.Default.Share, contentDescription = "导出日志")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无日志 喵～",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                state = listState,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { logEntry ->
                    LogEntryCard(logEntry = logEntry)
                }
            }
        }
    }
}

@Composable
fun LogEntryCard(logEntry: LogManager.LogEntry) {
    val backgroundColor = when (logEntry.level) {
        LogManager.LogLevel.ERROR -> Color(0xFFFFEBEE)
        LogManager.LogLevel.WARN -> Color(0xFFFFF3E0)
        LogManager.LogLevel.SUCCESS -> Color(0xFFE8F5E8)
        LogManager.LogLevel.INFO -> Color(0xFFE3F2FD)
        LogManager.LogLevel.DEBUG -> Color(0xFFF3E5F5)
    }
    
    val textColor = when (logEntry.level) {
        LogManager.LogLevel.ERROR -> Color(0xFFD32F2F)
        LogManager.LogLevel.WARN -> Color(0xFFF57C00)
        LogManager.LogLevel.SUCCESS -> Color(0xFF388E3C)
        LogManager.LogLevel.INFO -> Color(0xFF1976D2)
        LogManager.LogLevel.DEBUG -> Color(0xFF7B1FA2)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = logEntry.level.name,
                    color = textColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = logEntry.formattedTime,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = logEntry.message,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
            if (logEntry.tag.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "[${logEntry.tag}]",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
package cn.lemwoodfrp.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日志管理器 qwq
 * 用于管理FRP服务的日志记录和查看 AWA
 */
object LogManager {
    
    private const val TAG = "LemwoodFRP"
    private const val LOG_FILE_NAME = "frp_logs.txt"
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5MB
    private const val MAX_LOG_LINES = 1000 // 最大日志行数
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    // 内存中的日志列表
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    // 日志级别
    enum class LogLevel(val displayName: String, val color: Long) {
        DEBUG("调试", 0xFF9E9E9E),
        INFO("信息", 0xFF2196F3), 
        WARN("警告", 0xFFFF9800),
        ERROR("错误", 0xFFF44336),
        SUCCESS("成功", 0xFF4CAF50)
    }
    
    // 日志条目
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val configId: String? = null
    ) {
        val formattedTime: String
            get() = dateFormat.format(Date(timestamp))
    }
    
    /**
     * 初始化日志管理器
     */
    fun init(context: Context) {
        loadLogsFromFile(context)
        i("LogManager", "日志管理器初始化完成 喵～")
    }
    
    /**
     * 记录调试日志
     */
    fun d(tag: String, message: String, configId: String? = null) {
        log(LogLevel.DEBUG, tag, message, configId)
        Log.d(TAG, "[$tag] $message")
    }
    
    /**
     * 记录信息日志
     */
    fun i(tag: String, message: String, configId: String? = null) {
        log(LogLevel.INFO, tag, message, configId)
        Log.i(TAG, "[$tag] $message")
    }
    
    /**
     * 记录警告日志
     */
    fun w(tag: String, message: String, configId: String? = null) {
        log(LogLevel.WARN, tag, message, configId)
        Log.w(TAG, "[$tag] $message")
    }
    
    /**
     * 记录错误日志
     */
    fun e(tag: String, message: String, throwable: Throwable? = null, configId: String? = null) {
        val fullMessage = if (throwable != null) {
            "$message: ${throwable.message}"
        } else {
            message
        }
        log(LogLevel.ERROR, tag, fullMessage, configId)
        if (throwable != null) {
            Log.e(TAG, "[$tag] $message", throwable)
        } else {
            Log.e(TAG, "[$tag] $message")
        }
    }
    
    /**
     * 记录成功日志
     */
    fun s(tag: String, message: String, configId: String? = null) {
        log(LogLevel.SUCCESS, tag, message, configId)
        Log.i(TAG, "[$tag] ✓ $message")
    }
    
    /**
     * 记录FRP进程日志
     */
    fun logFRPProcess(configId: String, message: String, isError: Boolean = false) {
        val level = if (isError) LogLevel.ERROR else LogLevel.INFO
        log(level, "FRP-$configId", message, configId)
    }
    
    /**
     * 内部日志记录方法
     */
    private fun log(level: LogLevel, tag: String, message: String, configId: String?) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            configId = configId
        )
        
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, entry) // 添加到开头
        
        // 限制日志数量
        if (currentLogs.size > MAX_LOG_LINES) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        
        _logs.value = currentLogs
    }
    
    /**
     * 获取指定配置的日志
     */
    fun getLogsForConfig(configId: String): List<LogEntry> {
        return _logs.value.filter { it.configId == configId }
    }
    
    /**
     * 获取指定级别的日志
     */
    fun getLogsByLevel(level: LogLevel): List<LogEntry> {
        return _logs.value.filter { it.level == level }
    }
    
    /**
     * 清空日志
     */
    fun clearLogs(context: Context) {
        _logs.value = emptyList()
        clearLogFile(context)
        i("LogManager", "日志已清空 qwq")
    }
    
    /**
     * 保存日志到文件
     */
    fun saveLogsToFile(context: Context) {
        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            val logs = _logs.value.takeLast(500) // 只保存最近500条
            
            logFile.writeText(logs.joinToString("\n") { entry ->
                "${entry.formattedTime} [${entry.level.displayName}] ${entry.tag}: ${entry.message}"
            })
        } catch (e: Exception) {
            Log.e(TAG, "保存日志文件失败", e)
        }
    }
    
    /**
     * 从文件加载日志
     */
    private fun loadLogsFromFile(context: Context) {
        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            if (!logFile.exists()) return
            
            // 检查文件大小
            if (logFile.length() > MAX_LOG_SIZE) {
                logFile.delete()
                return
            }
            
            // 这里可以实现从文件加载日志的逻辑
            // 为了简化，暂时不实现
        } catch (e: Exception) {
            Log.e(TAG, "加载日志文件失败", e)
        }
    }
    
    /**
     * 清空日志文件
     */
    private fun clearLogFile(context: Context) {
        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            if (logFile.exists()) {
                logFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "清空日志文件失败", e)
        }
    }
    
    /**
     * 导出日志
     */
    fun exportLogs(context: Context): File? {
        return try {
            val exportFile = File(context.getExternalFilesDir(null), "frp_logs_export_${System.currentTimeMillis()}.txt")
            val logs = _logs.value
            
            exportFile.writeText(logs.joinToString("\n") { entry ->
                "${entry.formattedTime} [${entry.level.displayName}] ${entry.tag}: ${entry.message}"
            })
            
            exportFile
        } catch (e: Exception) {
            Log.e(TAG, "导出日志失败", e)
            null
        }
    }
}
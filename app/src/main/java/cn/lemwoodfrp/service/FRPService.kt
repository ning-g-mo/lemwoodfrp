package cn.lemwoodfrp.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cn.lemwoodfrp.LemwoodFRPApplication
import cn.lemwoodfrp.R
import cn.lemwoodfrp.model.FRPConfig
import cn.lemwoodfrp.model.FRPStatus
import cn.lemwoodfrp.ui.MainActivity
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class FRPService : Service() {
    
    private val binder = FRPBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val runningProcesses = ConcurrentHashMap<String, Process>()
    private val processStatus = ConcurrentHashMap<String, FRPStatus>()
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START_FRP = "start_frp"
        private const val ACTION_STOP_FRP = "stop_frp"
        private const val EXTRA_CONFIG_ID = "config_id"
    }
    
    inner class FRPBinder : Binder() {
        fun getService(): FRPService = this@FRPService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FRP -> {
                val configId = intent.getStringExtra(EXTRA_CONFIG_ID)
                configId?.let { startFRPProcess(it) }
            }
            ACTION_STOP_FRP -> {
                val configId = intent.getStringExtra(EXTRA_CONFIG_ID)
                configId?.let { stopFRPProcess(it) }
            }
        }
        return START_STICKY
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, LemwoodFRPApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }    
    /**
     * 启动FRP进程
     */
    fun startFRPProcess(configId: String) {
        serviceScope.launch {
            try {
                val config = getConfigById(configId) ?: return@launch
                
                if (runningProcesses.containsKey(configId)) {
                    return@launch // 已经在运行
                }
                
                val configFile = createConfigFile(config)
                val command = buildFRPCommand(config, configFile)
                
                val processBuilder = ProcessBuilder(command)
                processBuilder.directory(getExternalFilesDir("frp"))
                
                val process = processBuilder.start()
                runningProcesses[configId] = process
                
                val status = FRPStatus(
                    configId = configId,
                    isRunning = true,
                    pid = getPid(process),
                    startTime = System.currentTimeMillis()
                )
                processStatus[configId] = status
                
                // 监控进程状态
                monitorProcess(configId, process)
                
            } catch (e: Exception) {
                val status = FRPStatus(
                    configId = configId,
                    isRunning = false,
                    errorMessage = e.message
                )
                processStatus[configId] = status
            }
        }
    }
    
    /**
     * 停止FRP进程
     */
    fun stopFRPProcess(configId: String) {
        runningProcesses[configId]?.let { process ->
            try {
                process.destroy()
                runningProcesses.remove(configId)
                processStatus[configId] = FRPStatus(
                    configId = configId,
                    isRunning = false
                )
            } catch (e: Exception) {
                // 强制杀死进程 qwq
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process.destroyForcibly()
                } else {
                    process.destroy()
                }
                runningProcesses.remove(configId)
            }
        }
    }
    
    /**
     * 获取进程状态
     */
    fun getProcessStatus(configId: String): FRPStatus? {
        return processStatus[configId]
    }
    
    /**
     * 获取所有运行中的进程
     */
    fun getAllRunningProcesses(): Map<String, FRPStatus> {
        return processStatus.filter { it.value.isRunning }
    }
    
    private fun monitorProcess(configId: String, process: Process) {
        serviceScope.launch {
            try {
                val exitCode = process.waitFor()
                runningProcesses.remove(configId)
                
                val status = if (exitCode == 0) {
                    FRPStatus(configId = configId, isRunning = false)
                } else {
                    FRPStatus(
                        configId = configId,
                        isRunning = false,
                        errorMessage = "Process exited with code: $exitCode"
                    )
                }
                processStatus[configId] = status
                
            } catch (e: Exception) {
                runningProcesses.remove(configId)
                processStatus[configId] = FRPStatus(
                    configId = configId,
                    isRunning = false,
                    errorMessage = e.message
                )
            }
        }
    }
    
    private fun getConfigById(configId: String): FRPConfig? {
        // 这里需要从ConfigManager获取配置
        // 为了简化，暂时返回null，实际实现时需要注入Context
        return null
    }
    
    private fun createConfigFile(config: FRPConfig): File {
        // 创建FRP配置文件的逻辑
        val configDir = File(getExternalFilesDir("frp"), "configs")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        
        val configFile = File(configDir, "${config.id}.ini")
        // 这里需要根据config生成FRP配置文件内容
        return configFile
    }
    
    private fun buildFRPCommand(config: FRPConfig, configFile: File): List<String> {
        val frpDir = getExternalFilesDir("frp")
        val executable = if (config.type.name == "CLIENT") "frpc" else "frps"
        
        return listOf(
            File(frpDir, executable).absolutePath,
            "-c",
            configFile.absolutePath
        )
    }
    
    private fun getPid(process: Process): Int? {
        return try {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        } catch (e: Exception) {
            null
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        
        // 停止所有运行中的进程 AWA
        runningProcesses.values.forEach { process ->
            try {
                process.destroy()
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process.destroyForcibly()
                } else {
                    process.destroy()
                }
            }
        }
        runningProcesses.clear()
        processStatus.clear()
    }
}
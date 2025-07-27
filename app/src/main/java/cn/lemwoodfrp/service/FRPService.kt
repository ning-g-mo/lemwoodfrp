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
import cn.lemwoodfrp.model.FRPType
import cn.lemwoodfrp.ui.MainActivity
import cn.lemwoodfrp.utils.ConfigManager
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
        // 初始化FRP二进制文件 qwq
        initializeFRPBinaries()
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
    
    /**
     * 初始化FRP二进制文件 AWA
     * 从assets复制到应用私有目录并设置可执行权限
     */
    private fun initializeFRPBinaries() {
        try {
            val frpDir = File(filesDir, "frp")
            if (!frpDir.exists()) {
                frpDir.mkdirs()
            }
            
            // 复制frpc和frps二进制文件
            copyAssetToFile("frp/frpc", File(frpDir, "frpc"))
            copyAssetToFile("frp/frps", File(frpDir, "frps"))
            
            // 设置可执行权限 喵～
            File(frpDir, "frpc").setExecutable(true)
            File(frpDir, "frps").setExecutable(true)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 从assets复制文件到目标位置
     */
    private fun copyAssetToFile(assetPath: String, targetFile: File) {
        if (targetFile.exists()) {
            return // 文件已存在，跳过复制
        }
        
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        
        try {
            inputStream = assets.open(assetPath)
            outputStream = FileOutputStream(targetFile)
            
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
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
                processBuilder.directory(File(filesDir, "frp"))
                
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
        // 从ConfigManager获取配置 AWA
        return ConfigManager.getAllConfigs(this).find { it.id == configId }
    }
    
    private fun createConfigFile(config: FRPConfig): File {
        val configDir = File(filesDir, "frp/configs")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        
        val configFile = File(configDir, "${config.id}.toml")
        
        // 根据配置类型生成TOML配置文件内容 喵～
        val configContent = when (config.type) {
            FRPType.CLIENT -> generateClientConfig(config)
            FRPType.SERVER -> generateServerConfig(config)
        }
        
        configFile.writeText(configContent)
        return configFile
    }
    
    /**
     * 生成客户端配置文件内容
     */
    private fun generateClientConfig(config: FRPConfig): String {
        return """
            serverAddr = "${config.serverAddr}"
            serverPort = ${config.serverPort}
            
            [[proxies]]
            name = "${config.name}"
            type = "${config.proxyType}"
            localIP = "${config.localIP}"
            localPort = ${config.localPort}
            remotePort = ${config.remotePort}
        """.trimIndent()
    }
    
    /**
     * 生成服务端配置文件内容
     */
    private fun generateServerConfig(config: FRPConfig): String {
        return """
            bindPort = ${config.serverPort}
            
            # 可选配置 qwq
            # dashboardAddr = "0.0.0.0"
            # dashboardPort = 7500
            # dashboardUser = "admin"
            # dashboardPwd = "admin"
        """.trimIndent()
    }
    
    private fun buildFRPCommand(config: FRPConfig, configFile: File): List<String> {
        val frpDir = File(filesDir, "frp")
        val executable = if (config.type == FRPType.CLIENT) "frpc" else "frps"
        
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
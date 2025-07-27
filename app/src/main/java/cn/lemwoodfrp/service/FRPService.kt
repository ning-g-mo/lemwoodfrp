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
import cn.lemwoodfrp.utils.LogManager
import kotlinx.coroutines.*
import java.io.*
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
        private const val TAG = "FRPService"
    }
    
    inner class FRPBinder : Binder() {
        fun getService(): FRPService = this@FRPService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        LogManager.init(this)
        LogManager.i(TAG, "FRP服务启动 qwq")
        
        // 初始化FRP二进制文件 qwq
        initializeFRPBinaries()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FRP -> {
                val configId = intent.getStringExtra(EXTRA_CONFIG_ID)
                configId?.let { 
                    LogManager.i(TAG, "收到启动FRP请求: $it")
                    startFRPProcess(it) 
                }
            }
            ACTION_STOP_FRP -> {
                val configId = intent.getStringExtra(EXTRA_CONFIG_ID)
                configId?.let { 
                    LogManager.i(TAG, "收到停止FRP请求: $it")
                    stopFRPProcess(it) 
                }
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
            LogManager.i(TAG, "开始初始化FRP二进制文件...")
            
            val frpDir = File(filesDir, "frp")
            if (!frpDir.exists()) {
                frpDir.mkdirs()
                LogManager.d(TAG, "创建FRP目录: ${frpDir.absolutePath}")
            }
            
            // 复制frpc和frps二进制文件
            copyAssetToFile("frp/frpc", File(frpDir, "frpc"))
            copyAssetToFile("frp/frps", File(frpDir, "frps"))
            
            // 设置可执行权限 喵～
            val frpcFile = File(frpDir, "frpc")
            val frpsFile = File(frpDir, "frps")
            
            if (frpcFile.setExecutable(true)) {
                LogManager.s(TAG, "frpc可执行权限设置成功")
            } else {
                LogManager.w(TAG, "frpc可执行权限设置失败")
            }
            
            if (frpsFile.setExecutable(true)) {
                LogManager.s(TAG, "frps可执行权限设置成功")
            } else {
                LogManager.w(TAG, "frps可执行权限设置失败")
            }
            
            LogManager.s(TAG, "FRP二进制文件初始化完成 AWA")
            
        } catch (e: Exception) {
            LogManager.e(TAG, "初始化FRP二进制文件失败", e)
        }
    }
    
    /**
     * 从assets复制文件到目标位置
     */
    private fun copyAssetToFile(assetPath: String, targetFile: File) {
        if (targetFile.exists()) {
            LogManager.d(TAG, "文件已存在，跳过复制: ${targetFile.name}")
            return // 文件已存在，跳过复制
        }
        
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        
        try {
            LogManager.d(TAG, "开始复制文件: $assetPath -> ${targetFile.absolutePath}")
            
            inputStream = assets.open(assetPath)
            outputStream = FileOutputStream(targetFile)
            
            val buffer = ByteArray(1024)
            var length: Int
            var totalBytes = 0
            
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
                totalBytes += length
            }
            
            LogManager.s(TAG, "文件复制成功: ${targetFile.name} (${totalBytes} bytes)")
            
        } catch (e: Exception) {
            LogManager.e(TAG, "复制文件失败: $assetPath", e)
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
                LogManager.i(TAG, "开始启动FRP进程", configId)
                
                val config = getConfigById(configId)
                if (config == null) {
                    LogManager.e(TAG, "配置不存在: $configId", configId = configId)
                    return@launch
                }
                
                if (runningProcesses.containsKey(configId)) {
                    LogManager.w(TAG, "进程已在运行中", configId)
                    return@launch // 已经在运行
                }
                
                val configFile = createConfigFile(config)
                val command = buildFRPCommand(config, configFile)
                
                LogManager.d(TAG, "执行命令: ${command.joinToString(" ")}", configId)
                
                val processBuilder = ProcessBuilder(command)
                processBuilder.directory(File(filesDir, "frp"))
                
                // 重定向错误输出到标准输出，便于日志记录
                processBuilder.redirectErrorStream(true)
                
                val process = processBuilder.start()
                runningProcesses[configId] = process
                
                val status = FRPStatus(
                    configId = configId,
                    isRunning = true,
                    pid = getPid(process),
                    startTime = System.currentTimeMillis()
                )
                processStatus[configId] = status
                
                LogManager.s(TAG, "FRP进程启动成功 PID: ${status.pid}", configId)
                
                // 监控进程状态和输出
                monitorProcess(configId, process)
                
            } catch (e: Exception) {
                LogManager.e(TAG, "启动FRP进程失败", e, configId)
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
                LogManager.i(TAG, "停止FRP进程", configId)
                
                process.destroy()
                runningProcesses.remove(configId)
                processStatus[configId] = FRPStatus(
                    configId = configId,
                    isRunning = false
                )
                
                LogManager.s(TAG, "FRP进程已停止", configId)
                
            } catch (e: Exception) {
                LogManager.w(TAG, "正常停止失败，尝试强制停止", configId = configId)
                
                // 强制杀死进程 qwq
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        process.destroyForcibly()
                    } else {
                        process.destroy()
                    }
                    runningProcesses.remove(configId)
                    LogManager.s(TAG, "FRP进程强制停止成功", configId)
                } catch (ex: Exception) {
                    LogManager.e(TAG, "强制停止进程失败", ex, configId)
                }
            }
        } ?: run {
            LogManager.w(TAG, "进程不存在或已停止", configId)
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
    
    /**
     * 监控进程状态和输出
     */
    private fun monitorProcess(configId: String, process: Process) {
        // 监控进程输出
        serviceScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    line?.let { output ->
                        // 根据输出内容判断是否为错误信息
                        val isError = output.contains("error", ignoreCase = true) || 
                                     output.contains("failed", ignoreCase = true) ||
                                     output.contains("panic", ignoreCase = true)
                        
                        LogManager.logFRPProcess(configId, output, isError)
                    }
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "读取进程输出失败", e, configId)
            }
        }
        
        // 监控进程退出
        serviceScope.launch {
            try {
                val exitCode = process.waitFor()
                runningProcesses.remove(configId)
                
                val status = if (exitCode == 0) {
                    LogManager.i(TAG, "进程正常退出", configId)
                    FRPStatus(configId = configId, isRunning = false)
                } else {
                    LogManager.e(TAG, "进程异常退出，退出码: $exitCode", configId = configId)
                    FRPStatus(
                        configId = configId,
                        isRunning = false,
                        errorMessage = "Process exited with code: $exitCode"
                    )
                }
                processStatus[configId] = status
                
            } catch (e: Exception) {
                LogManager.e(TAG, "监控进程失败", e, configId)
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
        LogManager.d(TAG, "配置文件已生成: ${configFile.absolutePath}", config.id)
        
        return configFile
    }
    
    /**
     * 生成客户端配置文件内容
     */
    private fun generateClientConfig(config: FRPConfig): String {
        val configBuilder = StringBuilder()
        
        // 基本服务器配置
        configBuilder.appendLine("serverAddr = \"${config.serverAddr}\"")
        configBuilder.appendLine("serverPort = ${config.serverPort}")
        
        // Token认证 qwq
        if (!config.token.isNullOrBlank()) {
            configBuilder.appendLine("auth.token = \"${config.token}\"")
            LogManager.d(TAG, "客户端配置包含Token认证", config.id)
        }
        
        configBuilder.appendLine()
        
        // 代理配置
        configBuilder.appendLine("[[proxies]]")
        configBuilder.appendLine("name = \"${config.name}\"")
        configBuilder.appendLine("type = \"${config.proxyType}\"")
        
        if (!config.localIP.isNullOrBlank()) {
            configBuilder.appendLine("localIP = \"${config.localIP}\"")
        }
        
        config.localPort?.let {
            configBuilder.appendLine("localPort = $it")
        }
        
        config.remotePort?.let {
            configBuilder.appendLine("remotePort = $it")
        }
        
        return configBuilder.toString()
    }
    
    /**
     * 生成服务端配置文件内容
     */
    private fun generateServerConfig(config: FRPConfig): String {
        val configBuilder = StringBuilder()
        
        // 基本绑定配置
        configBuilder.appendLine("bindPort = ${config.serverPort}")
        
        // Token认证 AWA
        if (!config.token.isNullOrBlank()) {
            configBuilder.appendLine("auth.token = \"${config.token}\"")
            LogManager.d(TAG, "服务端配置包含Token认证", config.id)
        }
        
        configBuilder.appendLine()
        configBuilder.appendLine("# 可选的Web管理界面配置 qwq")
        configBuilder.appendLine("# webServer.addr = \"0.0.0.0\"")
        configBuilder.appendLine("# webServer.port = 7500")
        configBuilder.appendLine("# webServer.user = \"admin\"")
        configBuilder.appendLine("# webServer.password = \"admin\"")
        
        return configBuilder.toString()
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
        LogManager.i(TAG, "FRP服务正在关闭...")
        
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
        
        // 保存日志到文件
        LogManager.saveLogsToFile(this)
        LogManager.i(TAG, "FRP服务已关闭 qwq")
    }
}
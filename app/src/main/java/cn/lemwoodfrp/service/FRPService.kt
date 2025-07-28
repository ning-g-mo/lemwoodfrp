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
        
        // 初始化PRoot和FRP二进制文件 AWA
        initializePRootBinaries()
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
     * 初始化PRoot二进制文件 qwq
     * PRoot用于在Android上提供更好的Linux环境兼容性
     */
    private fun initializePRootBinaries() {
        try {
            LogManager.i(TAG, "开始初始化PRoot二进制文件 AWA")
            LogManager.d(TAG, "系统架构信息: ${Build.SUPPORTED_ABIS.joinToString()}")
            
            // 检测设备架构
            val deviceAbi = getDeviceAbi()
            LogManager.i(TAG, "检测到设备架构: $deviceAbi")
            
            val prootDir = File(filesDir, "proot")
            LogManager.d(TAG, "PRoot目录路径: ${prootDir.absolutePath}")
            
            if (!prootDir.exists()) {
                LogManager.d(TAG, "PRoot目录不存在，正在创建...")
                val created = prootDir.mkdirs()
                LogManager.d(TAG, "目录创建结果: $created")
                if (!created) {
                    LogManager.e(TAG, "无法创建PRoot目录")
                    return
                }
            } else {
                LogManager.d(TAG, "PRoot目录已存在")
            }
            
            // 复制PRoot二进制文件
            val prootFile = File(prootDir, "proot")
            LogManager.d(TAG, "开始处理PRoot文件: ${prootFile.absolutePath}")
            
            if (prootFile.exists()) {
                LogManager.d(TAG, "PRoot文件已存在，大小: ${prootFile.length()} bytes")
                // 检查现有文件是否有效
                try {
                    val existingHeader = prootFile.readBytes().take(4)
                    val elfMagic = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
                    val isValidELF = existingHeader.zip(elfMagic.toList()).all { it.first == it.second }
                    LogManager.d(TAG, "现有PRoot文件格式检查 - ELF格式: $isValidELF")
                    
                    if (!isValidELF) {
                        LogManager.w(TAG, "现有PRoot文件格式无效，将重新复制")
                        prootFile.delete()
                    }
                } catch (e: Exception) {
                    LogManager.w(TAG, "检查现有PRoot文件时出错: ${e.message}")
                    prootFile.delete()
                }
            }
            
            if (!prootFile.exists()) {
                LogManager.d(TAG, "从assets复制PRoot文件...")
                val prootAssetPath = "proot/$deviceAbi/proot"
                LogManager.d(TAG, "PRoot资源路径: $prootAssetPath")
                copyAssetToFile(prootAssetPath, prootFile)
                LogManager.d(TAG, "PRoot复制完成，文件大小: ${prootFile.length()} bytes")
            }
            
            // 设置执行权限
            LogManager.d(TAG, "设置PRoot权限 - 当前权限: 可读=${prootFile.canRead()}, 可执行=${prootFile.canExecute()}")
            if (!prootFile.setExecutable(true)) {
                LogManager.w(TAG, "setExecutable失败，尝试使用chmod设置PRoot权限")
                try {
                    val chmodCommand = "chmod 755 ${prootFile.absolutePath}"
                    LogManager.d(TAG, "执行命令: $chmodCommand")
                    val chmodProcess = Runtime.getRuntime().exec(chmodCommand)
                    val exitCode = chmodProcess.waitFor()
                    LogManager.d(TAG, "chmod PRoot 退出码: $exitCode")
                    
                    if (exitCode != 0) {
                        LogManager.e(TAG, "chmod PRoot 失败，退出码: $exitCode")
                    }
                } catch (e: Exception) {
                    LogManager.w(TAG, "chmod PRoot 失败: ${e.message}")
                }
            } else {
                LogManager.d(TAG, "PRoot setExecutable 成功")
            }
            
            // 验证PRoot文件完整性
            try {
                val prootHeader = prootFile.readBytes().take(4)
                val elfMagic = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
                val prootValid = prootHeader.zip(elfMagic.toList()).all { it.first == it.second }
                
                LogManager.d(TAG, "PRoot文件完整性验证: $prootValid")
                
                if (prootValid) {
                    LogManager.s(TAG, "PRoot二进制文件初始化完成 qwq")
                } else {
                    LogManager.e(TAG, "PRoot二进制文件验证失败")
                }
            } catch (e: Exception) {
                LogManager.w(TAG, "PRoot文件完整性验证时出错: ${e.message}")
            }
            
        } catch (e: Exception) {
            LogManager.e(TAG, "初始化PRoot二进制文件失败", e)
            LogManager.e(TAG, "错误详情: ${e.javaClass.simpleName} - ${e.message}")
        }
    }
    
    /**
     * 初始化FRP二进制文件
     */
    private fun initializeFRPBinaries() {
        try {
            LogManager.i(TAG, "开始初始化FRP二进制文件")
            LogManager.d(TAG, "系统架构信息: ${Build.SUPPORTED_ABIS.joinToString()}")
            LogManager.d(TAG, "应用私有目录: ${filesDir.absolutePath}")
            
            // 检测设备架构 qwq
            val deviceAbi = getDeviceAbi()
            LogManager.i(TAG, "检测到设备架构: $deviceAbi")
            
            val frpDir = File(filesDir, "frp")
            LogManager.d(TAG, "FRP目录路径: ${frpDir.absolutePath}")
            
            if (!frpDir.exists()) {
                LogManager.d(TAG, "FRP目录不存在，正在创建...")
                val created = frpDir.mkdirs()
                LogManager.d(TAG, "目录创建结果: $created")
                if (!created) {
                    LogManager.e(TAG, "无法创建FRP目录")
                    return
                }
            } else {
                LogManager.d(TAG, "FRP目录已存在")
            }
            
            // 检查目录权限 qwq
            LogManager.d(TAG, "目录权限检查 - 可读: ${frpDir.canRead()}, 可写: ${frpDir.canWrite()}, 可执行: ${frpDir.canExecute()}")
            
            // 复制frpc
            val frpcFile = File(frpDir, "frpc")
            LogManager.d(TAG, "开始处理frpc文件: ${frpcFile.absolutePath}")
            
            if (frpcFile.exists()) {
                LogManager.d(TAG, "frpc文件已存在，大小: ${frpcFile.length()} bytes")
                // 检查现有文件是否有效 AWA
                try {
                    val existingHeader = frpcFile.readBytes().take(4)
                    val elfMagic = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
                    val isValidELF = existingHeader.zip(elfMagic.toList()).all { it.first == it.second }
                    LogManager.d(TAG, "现有frpc文件格式检查 - ELF格式: $isValidELF")
                    
                    if (!isValidELF) {
                        LogManager.w(TAG, "现有frpc文件格式无效，将重新复制")
                        frpcFile.delete()
                    }
                } catch (e: Exception) {
                    LogManager.w(TAG, "检查现有frpc文件时出错: ${e.message}")
                    frpcFile.delete()
                }
            }
            
            if (!frpcFile.exists()) {
                LogManager.d(TAG, "从assets复制frpc文件...")
                val frpcAssetPath = "frp/$deviceAbi/frpc"
                LogManager.d(TAG, "frpc资源路径: $frpcAssetPath")
                copyAssetToFile(frpcAssetPath, frpcFile)
                LogManager.d(TAG, "frpc复制完成，文件大小: ${frpcFile.length()} bytes")
            }
            
            // 复制frps
            val frpsFile = File(frpDir, "frps")
            LogManager.d(TAG, "开始处理frps文件: ${frpsFile.absolutePath}")
            
            if (frpsFile.exists()) {
                LogManager.d(TAG, "frps文件已存在，大小: ${frpsFile.length()} bytes")
                // 检查现有文件是否有效 qwq
                try {
                    val existingHeader = frpsFile.readBytes().take(4)
                    val elfMagic = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
                    val isValidELF = existingHeader.zip(elfMagic.toList()).all { it.first == it.second }
                    LogManager.d(TAG, "现有frps文件格式检查 - ELF格式: $isValidELF")
                    
                    if (!isValidELF) {
                        LogManager.w(TAG, "现有frps文件格式无效，将重新复制")
                        frpsFile.delete()
                    }
                } catch (e: Exception) {
                    LogManager.w(TAG, "检查现有frps文件时出错: ${e.message}")
                    frpsFile.delete()
                }
            }
            
            if (!frpsFile.exists()) {
                LogManager.d(TAG, "从assets复制frps文件...")
                val frpsAssetPath = "frp/$deviceAbi/frps"
                LogManager.d(TAG, "frps资源路径: $frpsAssetPath")
                copyAssetToFile(frpsAssetPath, frpsFile)
                LogManager.d(TAG, "frps复制完成，文件大小: ${frpsFile.length()} bytes")
            }
            
            // 设置执行权限 AWA
            LogManager.d(TAG, "开始设置文件执行权限...")
            
            // 设置frpc权限
            LogManager.d(TAG, "设置frpc权限 - 当前权限: 可读=${frpcFile.canRead()}, 可执行=${frpcFile.canExecute()}")
            if (!frpcFile.setExecutable(true)) {
                LogManager.w(TAG, "setExecutable失败，尝试使用chmod设置frpc权限")
                try {
                    val chmodCommand = "chmod 755 ${frpcFile.absolutePath}"
                    LogManager.d(TAG, "执行命令: $chmodCommand")
                    val chmodProcess = Runtime.getRuntime().exec(chmodCommand)
                    val exitCode = chmodProcess.waitFor()
                    LogManager.d(TAG, "chmod frpc 退出码: $exitCode")
                    
                    val errorOutput = chmodProcess.errorStream.bufferedReader().readText()
                    if (errorOutput.isNotEmpty()) {
                        LogManager.w(TAG, "chmod frpc 错误输出: $errorOutput")
                    }
                    
                    if (exitCode != 0) {
                        LogManager.e(TAG, "chmod frpc 失败，退出码: $exitCode")
                    }
                } catch (e: Exception) {
                    LogManager.w(TAG, "chmod frpc 失败: ${e.message}")
                }
            } else {
                LogManager.d(TAG, "frpc setExecutable 成功")
            }
            
            // 设置frps权限
            LogManager.d(TAG, "设置frps权限 - 当前权限: 可读=${frpsFile.canRead()}, 可执行=${frpsFile.canExecute()}")
            if (!frpsFile.setExecutable(true)) {
                LogManager.w(TAG, "setExecutable失败，尝试使用chmod设置frps权限")
                try {
                    val chmodCommand = "chmod 755 ${frpsFile.absolutePath}"
                    LogManager.d(TAG, "执行命令: $chmodCommand")
                    val chmodProcess = Runtime.getRuntime().exec(chmodCommand)
                    val exitCode = chmodProcess.waitFor()
                    LogManager.d(TAG, "chmod frps 退出码: $exitCode")
                    
                    val errorOutput = chmodProcess.errorStream.bufferedReader().readText()
                    if (errorOutput.isNotEmpty()) {
                        LogManager.w(TAG, "chmod frps 错误输出: $errorOutput")
                    }
                    
                    if (exitCode != 0) {
                        LogManager.e(TAG, "chmod frps 失败，退出码: $exitCode")
                    }
                } catch (e: Exception) {
                    LogManager.w(TAG, "chmod frps 失败: ${e.message}")
                }
            } else {
                LogManager.d(TAG, "frps setExecutable 成功")
            }
            
            // 最终权限检查 qwq
            LogManager.d(TAG, "最终权限检查:")
            LogManager.d(TAG, "frpc - 可读: ${frpcFile.canRead()}, 可执行: ${frpcFile.canExecute()}, 大小: ${frpcFile.length()}")
            LogManager.d(TAG, "frps - 可读: ${frpsFile.canRead()}, 可执行: ${frpsFile.canExecute()}, 大小: ${frpsFile.length()}")
            
            // 验证文件完整性
            try {
                val frpcHeader = frpcFile.readBytes().take(4)
                val frpsHeader = frpsFile.readBytes().take(4)
                val elfMagic = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
                
                val frpcValid = frpcHeader.zip(elfMagic.toList()).all { it.first == it.second }
                val frpsValid = frpsHeader.zip(elfMagic.toList()).all { it.first == it.second }
                
                LogManager.d(TAG, "文件完整性验证 - frpc: $frpcValid, frps: $frpsValid")
                
                if (frpcValid && frpsValid) {
                    LogManager.s(TAG, "FRP二进制文件初始化完成 AWA")
                } else {
                    LogManager.e(TAG, "FRP二进制文件验证失败")
                }
            } catch (e: Exception) {
                LogManager.w(TAG, "文件完整性验证时出错: ${e.message}")
            }
            
        } catch (e: Exception) {
            LogManager.e(TAG, "初始化FRP二进制文件失败", e)
            LogManager.e(TAG, "错误详情: ${e.javaClass.simpleName} - ${e.message}")
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
     * 使用PRoot启动FRP进程 AWA
     * PRoot提供更好的Linux环境兼容性
     */
    private fun startFRPWithPRoot(configId: String, config: FRPConfig): Process? {
        try {
            LogManager.i(TAG, "🐧 使用PRoot启动FRP进程", configId)
            
            val prootDir = File(filesDir, "proot")
            val prootFile = File(prootDir, "proot")
            val frpDir = File(filesDir, "frp")
            val executable = if (config.type == FRPType.CLIENT) "frpc" else "frps"
            val executableFile = File(frpDir, executable)
            
            // 检查PRoot是否可用
            if (!prootFile.exists() || !prootFile.canExecute()) {
                LogManager.w(TAG, "PRoot不可用，回退到直接执行", configId)
                return startFRPDirect(configId, config)
            }
            
            // 创建配置文件
            val configFile = createConfigFile(config)
            if (configFile == null) {
                LogManager.e(TAG, "创建配置文件失败", configId = configId)
                return null
            }
            
            LogManager.i(TAG, "📝 配置文件创建成功: ${configFile.absolutePath}", configId)
            
            // 构建PRoot命令 qwq
            val command = mutableListOf<String>().apply {
                add(prootFile.absolutePath)
                add("--rootfs=/")  // 使用根文件系统
                add("--bind=${frpDir.absolutePath}:/frp")  // 绑定FRP目录
                add("--bind=${configFile.parent}:/config")  // 绑定配置目录
                add("--cwd=/frp")  // 设置工作目录
                add("/frp/$executable")  // FRP可执行文件
                add("-c")
                add("/config/${configFile.name}")  // 配置文件路径
            }
            
            LogManager.i(TAG, "🚀 PRoot命令构建完成:", configId)
            LogManager.i(TAG, "命令: ${command.joinToString(" ")}", configId)
            
            // 设置环境变量
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(frpDir)
            processBuilder.redirectErrorStream(true)
            
            // 添加必要的环境变量 AWA
            val env = processBuilder.environment()
            env["PATH"] = "/system/bin:/system/xbin:/vendor/bin"
            env["LD_LIBRARY_PATH"] = "/system/lib:/system/lib64:/vendor/lib:/vendor/lib64"
            env["TMPDIR"] = cacheDir.absolutePath
            
            LogManager.i(TAG, "🌍 环境变量设置完成", configId)
            LogManager.d(TAG, "PATH: ${env["PATH"]}", configId)
            LogManager.d(TAG, "LD_LIBRARY_PATH: ${env["LD_LIBRARY_PATH"]}", configId)
            LogManager.d(TAG, "TMPDIR: ${env["TMPDIR"]}", configId)
            
            // 启动进程
            LogManager.i(TAG, "▶️ 启动PRoot进程...", configId)
            val process = processBuilder.start()
            
            LogManager.s(TAG, "✅ PRoot进程启动成功 qwq", configId)
            return process
            
        } catch (e: Exception) {
            LogManager.e(TAG, "PRoot启动失败，回退到直接执行: ${e.message}", configId = configId)
            return startFRPDirect(configId, config)
        }
    }
    
    /**
     * 直接启动FRP进程（不使用PRoot）
     */
    private fun startFRPDirect(configId: String, config: FRPConfig): Process? {
        try {
            LogManager.i(TAG, "🔧 直接启动FRP进程", configId)
            
            val frpDir = File(filesDir, "frp")
            val executable = if (config.type == FRPType.CLIENT) "frpc" else "frps"
            val executableFile = File(frpDir, executable)
            
            // 创建配置文件
            val configFile = createConfigFile(config)
            if (configFile == null) {
                LogManager.e(TAG, "创建配置文件失败", configId = configId)
                return null
            }
            
            LogManager.i(TAG, "📝 配置文件创建成功: ${configFile.absolutePath}", configId)
            
            // 构建命令
            val command = arrayOf(
                executableFile.absolutePath,
                "-c",
                configFile.absolutePath
            )
            
            LogManager.i(TAG, "🚀 直接启动命令:", configId)
            LogManager.i(TAG, "命令: ${command.joinToString(" ")}", configId)
            
            // 启动进程
            val processBuilder = ProcessBuilder(*command)
            processBuilder.directory(frpDir)
            processBuilder.redirectErrorStream(true)
            
            LogManager.i(TAG, "▶️ 启动进程...", configId)
            val process = processBuilder.start()
            
            LogManager.s(TAG, "✅ 进程启动成功", configId)
            return process
            
        } catch (e: Exception) {
            LogManager.e(TAG, "直接启动FRP进程失败: ${e.message}", configId = configId)
            return null
        }
    }
    
    /**
     * 启动FRP进程
     */
    fun startFRPProcess(configId: String) {
        serviceScope.launch {
            try {
                LogManager.i(TAG, "🚀 开始启动FRP进程", configId)
                LogManager.i(TAG, "=" * 60, configId)
                LogManager.i(TAG, "配置ID: $configId", configId)
                LogManager.d(TAG, "系统信息 - Android版本: ${Build.VERSION.RELEASE}, API: ${Build.VERSION.SDK_INT}, ABI: ${Build.SUPPORTED_ABIS.joinToString()}", configId)
                
                // 详细的配置验证 AWA
                val config = getConfigById(configId)
                if (config == null) {
                    LogManager.e(TAG, "❌ 配置不存在: $configId", configId = configId)
                    LogManager.e(TAG, "可用配置列表: ${ConfigManager.getAllConfigs(this@FRPService).map { "${it.id}(${it.name})" }.joinToString()}", configId = configId)
                    return@launch
                }
                
                LogManager.s(TAG, "✅ 找到配置: ${config.name}, 类型: ${config.type}", configId)
                LogManager.i(TAG, "📋 配置详情:", configId)
                LogManager.i(TAG, "  - 服务器地址: ${config.serverAddr}:${config.serverPort}", configId)
                LogManager.i(TAG, "  - 代理类型: ${config.proxyType}", configId)
                LogManager.i(TAG, "  - 本地端口: ${config.localPort}", configId)
                LogManager.i(TAG, "  - 远程端口: ${config.remotePort}", configId)
                if (config.customDomain.isNotEmpty()) {
                    LogManager.i(TAG, "  - 自定义域名: ${config.customDomain}", configId)
                }
                if (config.subdomain.isNotEmpty()) {
                    LogManager.i(TAG, "  - 子域名: ${config.subdomain}", configId)
                }
                LogManager.i(TAG, "-" * 40, configId)
                
                if (runningProcesses.containsKey(configId)) {
                    LogManager.w(TAG, "⚠️ 进程已在运行中，跳过启动", configId)
                    LogManager.i(TAG, "当前运行的进程数量: ${runningProcesses.size}", configId)
                    LogManager.i(TAG, "运行中的配置: ${runningProcesses.keys.joinToString()}", configId)
                    return@launch // 已经在运行
                }
                
                LogManager.i(TAG, "🔍 开始环境检查...", configId)
                
                // 检查二进制文件是否存在 qwq
                val frpDir = File(filesDir, "frp")
                val executable = if (config.type == FRPType.CLIENT) "frpc" else "frps"
                val executableFile = File(frpDir, executable)
                
                LogManager.i(TAG, "📁 FRP目录检查:", configId)
                LogManager.i(TAG, "  - 目录路径: ${frpDir.absolutePath}", configId)
                LogManager.i(TAG, "  - 目录存在: ${frpDir.exists()}", configId)
                LogManager.i(TAG, "  - 目录可读: ${frpDir.canRead()}", configId)
                LogManager.i(TAG, "  - 目录可写: ${frpDir.canWrite()}", configId)
                
                val dirFiles = frpDir.listFiles()
                if (dirFiles != null) {
                    LogManager.i(TAG, "  - 目录文件列表 (${dirFiles.size}个):", configId)
                    dirFiles.forEach { file ->
                        LogManager.i(TAG, "    * ${file.name} (${file.length()} bytes, 可执行: ${file.canExecute()})", configId)
                    }
                } else {
                    LogManager.w(TAG, "  - 无法读取目录内容", configId)
                }
                
                LogManager.i(TAG, "🔧 可执行文件检查:", configId)
                LogManager.i(TAG, "  - 目标可执行文件: $executable", configId)
                LogManager.i(TAG, "  - 完整路径: ${executableFile.absolutePath}", configId)
                
                if (!executableFile.exists()) {
                    LogManager.e(TAG, "❌ 可执行文件不存在!", configId = configId)
                    LogManager.e(TAG, "  - 文件路径: ${executableFile.absolutePath}", configId = configId)
                    LogManager.e(TAG, "  - 父目录存在: ${executableFile.parentFile?.exists()}", configId = configId)
                    LogManager.e(TAG, "  - 预期文件: $executable", configId = configId)
                    LogManager.e(TAG, "💡 解决方案:", configId = configId)
                    LogManager.e(TAG, "  1. 检查assets/frp目录是否包含正确的二进制文件", configId = configId)
                    LogManager.e(TAG, "  2. 确认二进制文件与设备架构匹配 (当前: ${Build.SUPPORTED_ABIS.joinToString()})", configId = configId)
                    LogManager.e(TAG, "  3. 重新安装应用或清除应用数据", configId = configId)
                    return@launch
                }
                
                LogManager.s(TAG, "✅ 可执行文件存在", configId)
                
                // 详细的文件信息检查 AWA
                LogManager.i(TAG, "📊 文件详细信息:", configId)
                LogManager.i(TAG, "  - 文件大小: ${executableFile.length()} bytes", configId)
                LogManager.i(TAG, "  - 可读权限: ${executableFile.canRead()}", configId)
                LogManager.i(TAG, "  - 可写权限: ${executableFile.canWrite()}", configId)
                LogManager.i(TAG, "  - 可执行权限: ${executableFile.canExecute()}", configId)
                LogManager.i(TAG, "  - 最后修改: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(executableFile.lastModified()))}", configId)
                
                // 检查文件头，确认是否为有效的ELF文件
                LogManager.i(TAG, "🔍 ELF文件格式验证:", configId)
                try {
                    val fileHeader = executableFile.readBytes().take(16)
                    val elfMagic = byteArrayOf(0x7F, 0x45, 0x4C, 0x46) // ELF magic number
                    val isELF = fileHeader.take(4).zip(elfMagic.toList()).all { it.first == it.second }
                    
                    LogManager.i(TAG, "  - ELF格式: $isELF", configId)
                    LogManager.d(TAG, "  - 文件头: ${fileHeader.joinToString(" ") { "0x%02X".format(it) }}", configId)
                    
                    if (!isELF) {
                        LogManager.e(TAG, "❌ 文件不是有效的ELF格式!", configId = configId)
                        LogManager.e(TAG, "  - 预期文件头: 0x7F 0x45 0x4C 0x46 (ELF)", configId = configId)
                        LogManager.e(TAG, "  - 实际文件头: ${fileHeader.take(4).joinToString(" ") { "0x%02X".format(it) }}", configId = configId)
                        return@launch
                    }
                    
                    LogManager.s(TAG, "✅ ELF格式验证通过", configId)
                    
                } catch (e: Exception) {
                    LogManager.w(TAG, "文件头读取失败: ${e.message}", configId)
                }
                
                // 尝试使用PRoot启动，如果失败则回退到直接启动 qwq
                LogManager.i(TAG, "🐧 尝试使用PRoot启动FRP进程...", configId)
                val process = startFRPWithPRoot(configId, config)
                
                if (process == null) {
                    LogManager.e(TAG, "❌ 进程启动失败", configId = configId)
                    return@launch
                }
                
                // 保存进程引用
                runningProcesses[configId] = process
                processStatus[configId] = FRPStatus.RUNNING
                
                LogManager.s(TAG, "🎉 FRP进程启动成功! qwq", configId)
                LogManager.i(TAG, "进程PID: ${getPid(process)}", configId)
                LogManager.i(TAG, "当前运行的进程数量: ${runningProcesses.size}", configId)
                
                // 监控进程输出 AWA
                monitorProcessOutput(configId, process)
                
            } catch (e: Exception) {
                LogManager.e(TAG, "启动FRP进程时发生异常", e, configId)
                processStatus[configId] = FRPStatus.ERROR
            }
        }
    }    
    /**
     * 创建FRP配置文件 qwq
     */
    private fun createConfigFile(config: FRPConfig): File? {
        try {
            val configDir = File(filesDir, "configs")
            if (!configDir.exists()) {
                configDir.mkdirs()
            }
            
            val configFile = File(configDir, "${config.id}.ini")
            val configContent = buildString {
                appendLine("[common]")
                appendLine("server_addr = ${config.serverAddr}")
                appendLine("server_port = ${config.serverPort}")
                
                if (config.token.isNotEmpty()) {
                    appendLine("token = ${config.token}")
                }
                
                appendLine()
                appendLine("[${config.name}]")
                appendLine("type = ${config.proxyType}")
                appendLine("local_ip = 127.0.0.1")
                appendLine("local_port = ${config.localPort}")
                
                when (config.proxyType.lowercase()) {
                    "tcp", "udp" -> {
                        appendLine("remote_port = ${config.remotePort}")
                    }
                    "http", "https" -> {
                        if (config.customDomain.isNotEmpty()) {
                            appendLine("custom_domains = ${config.customDomain}")
                        }
                        if (config.subdomain.isNotEmpty()) {
                            appendLine("subdomain = ${config.subdomain}")
                        }
                    }
                }
            }
            
            configFile.writeText(configContent)
            LogManager.d(TAG, "配置文件内容:\\n$configContent")
            
            return configFile
            
        } catch (e: Exception) {
            LogManager.e(TAG, "创建配置文件失败: ${e.message}")
            return null
        }
    }
    
    /**
     * 监控进程输出 AWA
     */
    private fun monitorProcessOutput(configId: String, process: Process) {
        serviceScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                
                LogManager.i(TAG, "📡 开始监控进程输出", configId)
                
                while (reader.readLine().also { line = it } != null && runningProcesses.containsKey(configId)) {
                    line?.let { output ->
                        LogManager.i(TAG, "[FRP输出] $output", configId)
                        
                        // 检查启动成功的标志 qwq
                        if (output.contains("start frpc success") || 
                            output.contains("start frps success") ||
                            output.contains("login to server success")) {
                            LogManager.s(TAG, "🎉 FRP启动成功!", configId)
                            processStatus[configId] = FRPStatus.RUNNING
                        }
                        
                        // 检查错误信息
                        if (output.contains("error") || output.contains("failed")) {
                            LogManager.w(TAG, "⚠️ 检测到错误信息: $output", configId)
                        }
                    }
                }
                
                LogManager.i(TAG, "📡 进程输出监控结束", configId)
                
            } catch (e: Exception) {
                LogManager.w(TAG, "监控进程输出时出错: ${e.message}", configId)
            }
        }
        
        // 监控进程状态
        serviceScope.launch {
            try {
                val exitCode = process.waitFor()
                LogManager.i(TAG, "🔚 进程结束，退出码: $exitCode", configId)
                
                runningProcesses.remove(configId)
                processStatus[configId] = if (exitCode == 0) FRPStatus.STOPPED else FRPStatus.ERROR
                
                LogManager.i(TAG, "当前运行的进程数量: ${runningProcesses.size}", configId)
                
            } catch (e: Exception) {
                LogManager.w(TAG, "监控进程状态时出错: ${e.message}", configId)
            }
        }
    }
    
    /**
     * 停止FRP进程
     */
    fun stopFRPProcess(configId: String) {
        serviceScope.launch {
            try {
                LogManager.i(TAG, "🛑 停止FRP进程", configId)
                
                val process = runningProcesses[configId]
                if (process == null) {
                    LogManager.w(TAG, "进程不存在或已停止", configId)
                    processStatus[configId] = FRPStatus.STOPPED
                    return@launch
                }
                
                LogManager.i(TAG, "正在终止进程...", configId)
                
                // 尝试优雅关闭
                try {
                    process.destroy()
                    
                    // 等待进程结束，最多等待5秒
                    val terminated = withTimeoutOrNull(5000) {
                        process.waitFor()
                        true
                    }
                    
                    if (terminated == true) {
                        LogManager.s(TAG, "✅ 进程已优雅关闭", configId)
                    } else {
                        LogManager.w(TAG, "进程未在5秒内关闭，强制终止", configId)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            process.destroyForcibly()
                        }
                    }
                    
                } catch (e: Exception) {
                    LogManager.w(TAG, "终止进程时出错: ${e.message}", configId)
                }
                
                runningProcesses.remove(configId)
                processStatus[configId] = FRPStatus.STOPPED
                
                LogManager.s(TAG, "🎯 FRP进程已停止", configId)
                LogManager.i(TAG, "当前运行的进程数量: ${runningProcesses.size}", configId)
                
            } catch (e: Exception) {
                LogManager.e(TAG, "停止FRP进程时发生异常", e, configId)
            }
        }
    }
    
    /**
     * 获取配置信息
     */
    private fun getConfigById(configId: String): FRPConfig? {
        return try {
            ConfigManager.getAllConfigs(this).find { it.id == configId }
        } catch (e: Exception) {
            LogManager.e(TAG, "获取配置失败: ${e.message}")
            null
        }
    }
    
    /**
     * 获取进程状态
     */
    fun getProcessStatus(configId: String): FRPStatus {
        return processStatus[configId] ?: FRPStatus.STOPPED
    }
    
    /**
     * 获取所有运行中的进程
     */
    fun getRunningProcesses(): Map<String, FRPStatus> {
        return processStatus.toMap()
    }
    
    /**
     * 停止所有进程
     */
    fun stopAllProcesses() {
        LogManager.i(TAG, "🛑 停止所有FRP进程")
        
        val configIds = runningProcesses.keys.toList()
        configIds.forEach { configId ->
            stopFRPProcess(configId)
        }
        
        LogManager.i(TAG, "已发送停止信号给 ${configIds.size} 个进程")
    }
    
    /**
     * 获取进程PID AWA
     */
    private fun getPid(process: Process): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.pid().toString()
            } else {
                // API 25及以下的fallback方案
                val field = process.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                field.getInt(process).toString()
            }
        } catch (e: Exception) {
            "未知"
        }
    }
    
    /**
     * 检测设备架构 qwq
     */
    private fun getDeviceAbi(): String {
        val supportedAbis = Build.SUPPORTED_ABIS
        
        return when {
            supportedAbis.contains("arm64-v8a") -> "arm64-v8a"
            supportedAbis.contains("armeabi-v7a") -> "armeabi-v7a"
            supportedAbis.contains("armeabi") -> "armeabi-v7a" // 向后兼容
            supportedAbis.contains("x86_64") -> "x86_64"
            supportedAbis.contains("x86") -> "x86"
            else -> {
                LogManager.w(TAG, "未找到匹配的架构，使用默认架构: arm64-v8a")
                LogManager.w(TAG, "设备支持的架构: ${Build.SUPPORTED_ABIS.joinToString()}")
                "arm64-v8a"
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LogManager.i(TAG, "FRP服务正在销毁 qwq")
        
        // 停止所有进程
        stopAllProcesses()
        
        // 取消所有协程
        serviceScope.cancel()
        
        LogManager.i(TAG, "FRP服务已销毁 AWA")
    }
    
    /**
     * 诊断FRP环境 qwq
     * 用于排查启动问题
     */
    fun diagnoseFRPEnvironment(): String {
        val diagnosis = StringBuilder()
        
        try {
            diagnosis.appendLine("=== FRP环境诊断报告 ===")
            diagnosis.appendLine("生成时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            diagnosis.appendLine()
            
            // 系统信息
            diagnosis.appendLine("【系统信息】")
            diagnosis.appendLine("Android版本: ${Build.VERSION.RELEASE}")
            diagnosis.appendLine("API级别: ${Build.VERSION.SDK_INT}")
            diagnosis.appendLine("设备型号: ${Build.MODEL}")
            diagnosis.appendLine("设备制造商: ${Build.MANUFACTURER}")
            diagnosis.appendLine("支持的ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            diagnosis.appendLine("主要ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "未知"}")
            diagnosis.appendLine()
            
            // 应用信息
            diagnosis.appendLine("【应用信息】")
            diagnosis.appendLine("包名: ${packageName}")
            diagnosis.appendLine("私有目录: ${filesDir.absolutePath}")
            diagnosis.appendLine("缓存目录: ${cacheDir.absolutePath}")
            diagnosis.appendLine()
            
            // PRoot目录检查 AWA
            diagnosis.appendLine("【PRoot目录检查】")
            val prootDir = File(filesDir, "proot")
            diagnosis.appendLine("PRoot目录: ${prootDir.absolutePath}")
            diagnosis.appendLine("目录存在: ${prootDir.exists()}")
            diagnosis.appendLine("目录可读: ${prootDir.canRead()}")
            diagnosis.appendLine("目录可写: ${prootDir.canWrite()}")
            diagnosis.appendLine("目录可执行: ${prootDir.canExecute()}")
            
            if (prootDir.exists()) {
                val files = prootDir.listFiles()
                diagnosis.appendLine("目录内容: ${files?.map { it.name }?.joinToString() ?: "空"}")
            }
            diagnosis.appendLine()
            
            // PRoot二进制文件检查
            diagnosis.appendLine("【PRoot二进制文件检查】")
            val prootFile = File(prootDir, "proot")
            diagnosis.appendLine("proot文件:")
            diagnosis.appendLine("  路径: ${prootFile.absolutePath}")
            diagnosis.appendLine("  存在: ${prootFile.exists()}")
            if (prootFile.exists()) {
                diagnosis.appendLine("  大小: ${prootFile.length()} bytes")
                diagnosis.appendLine("  可读: ${prootFile.canRead()}")
                diagnosis.appendLine("  可执行: ${prootFile.canExecute()}")
                diagnosis.appendLine("  最后修改: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(prootFile.lastModified()))}")
                
                // 检查文件格式
                try {
                    val header = prootFile.readBytes().take(16)
                    val elfMagic = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
                    val isELF = header.take(4).zip(elfMagic.toList()).all { it.first == it.second }
                    diagnosis.appendLine("  ELF格式: $isELF")
                    diagnosis.appendLine("  文件头: ${header.joinToString(" ") { "0x%02X".format(it) }}")
                } catch (e: Exception) {
                    diagnosis.appendLine("  文件头读取失败: ${e.message}")
                }
            }
            diagnosis.appendLine()
            
            // FRP目录检查
            diagnosis.appendLine("【FRP目录检查】")
            val frpDir = File(filesDir, "frp")
            diagnosis.appendLine("FRP目录: ${frpDir.absolutePath}")
            diagnosis.appendLine("目录存在: ${frpDir.exists()}")
            diagnosis.appendLine("目录可读: ${frpDir.canRead()}")
            diagnosis.appendLine("目录可写: ${frpDir.canWrite()}")
            diagnosis.appendLine("目录可执行: ${frpDir.canExecute()}")
            
            if (frpDir.exists()) {
                val files = frpDir.listFiles()
                diagnosis.appendLine("目录内容: ${files?.map { it.name }?.joinToString() ?: "空"}")
            }
            diagnosis.appendLine()
            
            // FRP二进制文件检查
            diagnosis.appendLine("【FRP二进制文件检查】")
            val frpcFile = File(frpDir, "frpc")
            val frpsFile = File(frpDir, "frps")
            
            // frpc检查
            diagnosis.appendLine("frpc文件:")
            diagnosis.appendLine("  路径: ${frpcFile.absolutePath}")
            diagnosis.appendLine("  存在: ${frpcFile.exists()}")
            if (frpcFile.exists()) {
                diagnosis.appendLine("  大小: ${frpcFile.length()} bytes")
                diagnosis.appendLine("  可读: ${frpcFile.canRead()}")
                diagnosis.appendLine("  可执行: ${frpcFile.canExecute()}")
                diagnosis.appendLine("  最后修改: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(frpcFile.lastModified()))}")
                
                // 检查文件格式
                try {
                    val header = frpcFile.readBytes().take(16)
                    val elfMagic = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
                    val isELF = header.take(4).zip(elfMagic.toList()).all { it.first == it.second }
                    diagnosis.appendLine("  ELF格式: $isELF")
                    diagnosis.appendLine("  文件头: ${header.joinToString(" ") { "0x%02X".format(it) }}")
                } catch (e: Exception) {
                    diagnosis.appendLine("  文件头读取失败: ${e.message}")
                }
            }
            
            // frps检查
            diagnosis.appendLine("frps文件:")
            diagnosis.appendLine("  路径: ${frpsFile.absolutePath}")
            diagnosis.appendLine("  存在: ${frpsFile.exists()}")
            if (frpsFile.exists()) {
                diagnosis.appendLine("  大小: ${frpsFile.length()} bytes")
                diagnosis.appendLine("  可读: ${frpsFile.canRead()}")
                diagnosis.appendLine("  可执行: ${frpsFile.canExecute()}")
                diagnosis.appendLine("  最后修改: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(frpsFile.lastModified()))}")
                
                // 检查文件格式
                try {
                    val header = frpsFile.readBytes().take(16)
                    val elfMagic = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
                    val isELF = header.take(4).zip(elfMagic.toList()).all { it.first == it.second }
                    diagnosis.appendLine("  ELF格式: $isELF")
                    diagnosis.appendLine("  文件头: ${header.joinToString(" ") { "0x%02X".format(it) }}")
                } catch (e: Exception) {
                    diagnosis.appendLine("  文件头读取失败: ${e.message}")
                }
            }
            diagnosis.appendLine()
            
            // 运行状态检查
            diagnosis.appendLine("【运行状态检查】")
            diagnosis.appendLine("当前运行的进程数: ${runningProcesses.size}")
            if (runningProcesses.isNotEmpty()) {
                runningProcesses.forEach { (configId, process) ->
                    diagnosis.appendLine("  配置ID: $configId")
                    // API兼容性处理：Process.isAlive需要API 26 AWA
                    diagnosis.appendLine("  进程存活: " + 
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { 
                            process.isAlive 
                        } else { 
                            // API 25及以下的fallback方案 qwq
                            try { 
                                process.exitValue() 
                                false // 进程已退出
                            } catch (e: IllegalThreadStateException) { 
                                true // 进程仍在运行
                            } 
                        }
                    )
                    try {
                        diagnosis.appendLine("  进程PID: ${getPid(process)}")
                    } catch (e: Exception) {
                        diagnosis.appendLine("  进程PID: 获取失败")
                    }
                }
            }
            diagnosis.appendLine()
            
            // 权限测试
            diagnosis.appendLine("【权限测试】")
            if (frpcFile.exists()) {
                try {
                    // 尝试执行简单的命令测试
                    val testCommand = arrayOf(frpcFile.absolutePath, "--help")
                    diagnosis.appendLine("测试命令: ${testCommand.joinToString(" ")}")
                    
                    val processBuilder = ProcessBuilder(*testCommand)
                    processBuilder.directory(frpDir)
                    processBuilder.redirectErrorStream(true)
                    
                    val testProcess = processBuilder.start()
                    val hasOutput = testProcess.inputStream.available() > 0
                    val exitCode = testProcess.waitFor()
                    
                    diagnosis.appendLine("测试结果:")
                    diagnosis.appendLine("  退出码: $exitCode")
                    diagnosis.appendLine("  有输出: $hasOutput")
                    
                    if (exitCode == 0 || exitCode == 1) { // frp --help 通常返回1
                        diagnosis.appendLine("  状态: 可执行 ✓")
                    } else {
                        diagnosis.appendLine("  状态: 执行异常 ✗")
                    }
                    
                } catch (e: Exception) {
                    diagnosis.appendLine("权限测试失败: ${e.message}")
                    diagnosis.appendLine("错误类型: ${e.javaClass.simpleName}")
                }
            }
            diagnosis.appendLine()
            
            // 建议
            diagnosis.appendLine("【诊断建议】")
            if (!prootDir.exists()) {
                diagnosis.appendLine("❌ PRoot目录不存在，请重新初始化")
            } else if (!prootFile.exists()) {
                diagnosis.appendLine("❌ PRoot二进制文件缺失，请重新安装")
            } else if (!prootFile.canExecute()) {
                diagnosis.appendLine("❌ PRoot文件没有执行权限，请检查权限设置")
            }
            
            if (!frpDir.exists()) {
                diagnosis.appendLine("❌ FRP目录不存在，请重新初始化")
            } else if (!frpcFile.exists() || !frpsFile.exists()) {
                diagnosis.appendLine("❌ FRP二进制文件缺失，请重新安装")
            } else if (!frpcFile.canExecute() || !frpsFile.canExecute()) {
                diagnosis.appendLine("❌ FRP文件没有执行权限，请检查权限设置")
            } else {
                diagnosis.appendLine("✅ 基本环境检查通过")
            }
            
            diagnosis.appendLine()
            diagnosis.appendLine("=== 诊断报告结束 ===")
            
        } catch (e: Exception) {
            diagnosis.appendLine("诊断过程中发生异常: ${e.message}")
            diagnosis.appendLine("异常类型: ${e.javaClass.simpleName}")
        }
        
        val result = diagnosis.toString()
        LogManager.i(TAG, "FRP环境诊断完成")
        LogManager.d(TAG, "诊断报告:\\n$result")
        
        return result
    }
}
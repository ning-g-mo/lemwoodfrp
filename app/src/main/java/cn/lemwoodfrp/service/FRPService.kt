package cn.lemwoodfrp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import cn.lemwoodfrp.ui.MainActivity
import cn.lemwoodfrp.R
import cn.lemwoodfrp.utils.ConfigManager
import cn.lemwoodfrp.model.FRPConfig
import cn.lemwoodfrp.model.FRPType
import cn.lemwoodfrp.utils.LogManager
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.ConcurrentHashMap

class FRPService : Service() {
    companion object {
        private const val TAG = "FRPService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "frp_service_channel"
        
        // FRP状态常量 qwq
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_ERROR = "ERROR"
        const val STATUS_STOPPED = "STOPPED"
    }

    private val binder = FRPBinder()
    private val runningProcesses = ConcurrentHashMap<String, Process>()
    private val processStatus = ConcurrentHashMap<String, String>()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var initializationJob: Job? = null  // 跟踪初始化任务 喵～

    inner class FRPBinder : Binder() {
        fun getService(): FRPService = this@FRPService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * 获取应用私有目录 qwq
     * 使用 /data/data/cn.lemwoodfrp/ 目录来避免权限问题
     */
    private fun getAppPrivateDir(): File {
        return applicationContext.filesDir
    }

    /**
     * 获取Termux环境根目录 AWA
     * 内置的完整Linux环境
     */
    private fun getTermuxRootDir(): File {
        return File(getAppPrivateDir(), "termux-rootfs")
    }

    /**
     * 检查是否启用Termux环境 喵～
     * 可以通过配置文件或用户设置来控制
     */
    private fun isTermuxEnvironmentEnabled(): Boolean {
        // 这里可以添加配置检查逻辑
        // 暂时默认启用，如果Termux环境存在的话
        val termuxRoot = getTermuxRootDir()
        return termuxRoot.exists() && File(termuxRoot, "bin").exists()
    }

    override fun onCreate() {
        super.onCreate()
        LogManager.i(TAG, "🎯 FRPService 创建")
        createNotificationChannel()
        
        // 初始化PRoot、Termux和FRP环境 AWA
        initializationJob = serviceScope.launch {
            try {
                initializePRoot()
                initializeTermuxEnvironment()
                initializeFRPBinaries()
                LogManager.s(TAG, "✅ FRP服务初始化完成")
            } catch (e: Exception) {
                LogManager.e(TAG, "❌ FRP服务初始化失败: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogManager.i(TAG, "📨 收到服务命令: ${intent?.action}")
        
        when (intent?.action) {
            "start_frp" -> {
                val configId = intent.getStringExtra("config_id")
                if (configId != null) {
                    // 等待初始化完成后再启动FRP进程 喵～
                    serviceScope.launch {
                        // 等待初始化完成
                        initializationJob?.join()
                        startFRPProcess(configId)
                    }
                } else {
                    LogManager.e(TAG, "❌ 启动FRP失败: 配置ID为空")
                }
            }
            "stop_frp" -> {
                val configId = intent.getStringExtra("config_id")
                if (configId != null) {
                    stopFRPProcess(configId)
                } else {
                    LogManager.e(TAG, "❌ 停止FRP失败: 配置ID为空")
                }
            }
            "stop_all" -> {
                stopAllProcesses()
            }
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    /**
     * 初始化PRoot二进制文件 qwq
     * PRoot用于在Android上提供更好的Linux环境兼容性
     */
    private suspend fun initializePRoot() = withContext(Dispatchers.IO) {
        try {
            LogManager.i(TAG, "🔧 开始初始化PRoot环境")
            
            val prootDir = File(getAppPrivateDir(), "proot")
            if (!prootDir.exists()) {
                prootDir.mkdirs()
                LogManager.d(TAG, "创建PRoot目录: ${prootDir.absolutePath}")
            }
            
            val architecture = detectArchitecture()
            LogManager.i(TAG, "检测到设备架构: $architecture")
            
            val prootAssetPath = when (architecture) {
                "arm64-v8a" -> "proot/arm64-v8a/proot"
                "armeabi-v7a" -> "proot/armeabi-v7a/proot"
                else -> {
                    LogManager.w(TAG, "不支持的架构: $architecture，尝试使用arm64-v8a")
                    "proot/arm64-v8a/proot"
                }
            }
            
            val prootFile = File(prootDir, "proot")
            
            // 检查现有文件是否有效
            if (prootFile.exists()) {
                LogManager.d(TAG, "PRoot文件已存在，大小: ${prootFile.length()} bytes")
                // 简单验证：检查文件大小是否合理
                if (prootFile.length() > 100 * 1024) { // 大于100KB
                    LogManager.d(TAG, "现有PRoot文件看起来有效，跳过复制")
                    // 确保执行权限 - 使用双重权限设置 qwq
                    if (!prootFile.canExecute()) {
                        try {
                            val chmodProcess = Runtime.getRuntime().exec("chmod 755 ${prootFile.absolutePath}")
                            val chmodResult = chmodProcess.waitFor()
                            LogManager.d(TAG, "现有PRoot chmod结果: $chmodResult")
                            
                            // 备用方法：使用Java API
                            val success = prootFile.setExecutable(true, true)
                            LogManager.d(TAG, "现有PRoot setExecutable结果: $success")
                        } catch (e: Exception) {
                            LogManager.w(TAG, "设置现有PRoot权限时出错: ${e.message}")
                            // 尝试备用方法
                            val success = prootFile.setExecutable(true, true)
                            LogManager.d(TAG, "现有PRoot setExecutable备用结果: $success")
                        }
                    }
                    return@withContext
                }
            }
            
            LogManager.i(TAG, "从assets复制PRoot: $prootAssetPath")
            
            try {
                assets.open(prootAssetPath).use { inputStream ->
                    prootFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                LogManager.s(TAG, "✅ PRoot复制完成，大小: ${prootFile.length()} bytes")
                
                // 设置执行权限 - 使用chmod命令确保权限正确 qwq
                try {
                    val chmodProcess = Runtime.getRuntime().exec("chmod 755 ${prootFile.absolutePath}")
                    val chmodResult = chmodProcess.waitFor()
                    val errorOutput = chmodProcess.errorStream.bufferedReader().readText()
                    if (errorOutput.isNotEmpty()) {
                        LogManager.w(TAG, "chmod proot 错误输出: $errorOutput")
                    }
                    LogManager.d(TAG, "proot chmod结果: $chmodResult")
                    
                    // 备用方法：使用Java API
                    val success = prootFile.setExecutable(true, true)
                    LogManager.d(TAG, "proot setExecutable结果: $success")
                    
                    if (success) {
                        LogManager.s(TAG, "✅ PRoot执行权限设置成功")
                    } else {
                        LogManager.w(TAG, "⚠️ PRoot执行权限设置可能失败")
                    }
                } catch (e: Exception) {
                    LogManager.w(TAG, "设置proot权限时出错: ${e.message}")
                    // 尝试备用方法
                    val success = prootFile.setExecutable(true, true)
                    if (success) {
                        LogManager.s(TAG, "✅ PRoot执行权限设置成功（备用方法）")
                    } else {
                        LogManager.w(TAG, "⚠️ PRoot执行权限设置失败")
                    }
                }
                
                // 验证文件
                if (prootFile.exists() && prootFile.canExecute()) {
                    LogManager.s(TAG, "✅ PRoot初始化完成")
                } else {
                    LogManager.e(TAG, "❌ PRoot初始化验证失败")
                }
                
            } catch (e: FileNotFoundException) {
                LogManager.e(TAG, "❌ PRoot资源文件不存在: $prootAssetPath")
                LogManager.e(TAG, "请确保assets目录包含正确的PRoot二进制文件")
            }
            
        } catch (e: Exception) {
            LogManager.e(TAG, "❌ PRoot初始化失败: ${e.message}")
            throw e
        }
    }

    /**
     * 初始化Termux环境 AWA
     * 内置完整的Linux环境，包含bash、coreutils等
     */
    private suspend fun initializeTermuxEnvironment() = withContext(Dispatchers.IO) {
        try {
            LogManager.i(TAG, "🐧 开始初始化Termux环境")
            
            val termuxRoot = getTermuxRootDir()
            if (!termuxRoot.exists()) {
                termuxRoot.mkdirs()
                LogManager.d(TAG, "创建Termux根目录: ${termuxRoot.absolutePath}")
            }
            
            val architecture = detectArchitecture()
            LogManager.i(TAG, "为架构 $architecture 初始化Termux环境")
            
            // 创建基本目录结构 喵～
            val binDir = File(termuxRoot, "bin")
            val libDir = File(termuxRoot, "lib")
            val etcDir = File(termuxRoot, "etc")
            val usrDir = File(termuxRoot, "usr")
            val tmpDir = File(termuxRoot, "tmp")
            
            listOf(binDir, libDir, etcDir, usrDir, tmpDir).forEach { dir ->
                if (!dir.exists()) {
                    dir.mkdirs()
                    LogManager.d(TAG, "创建目录: ${dir.absolutePath}")
                }
            }
            
            // 复制Termux基础文件
            copyTermuxAssets(architecture, termuxRoot)
            
            // 设置环境变量文件
            setupTermuxEnvironment(termuxRoot)
            
            LogManager.s(TAG, "✅ Termux环境初始化完成")
            
        } catch (e: Exception) {
            LogManager.e(TAG, "❌ Termux环境初始化失败: ${e.message}")
            throw e
        }
    }
    
    /**
     * 复制Termux资源文件 qwq
     */
    private suspend fun copyTermuxAssets(architecture: String, termuxRoot: File) = withContext(Dispatchers.IO) {
        try {
            val termuxAssetDir = "termux/$architecture"
            LogManager.i(TAG, "从 $termuxAssetDir 复制Termux资源")
            
            // 复制基础二进制文件
            val binaries = listOf("bash", "sh", "ls", "cat", "echo")
            val binDir = File(termuxRoot, "bin")
            
            binaries.forEach { binary ->
                try {
                    val targetFile = File(binDir, binary)
                    // 修复路径：assets中的二进制文件在bin子目录下 喵～
                    copyAssetFile("$termuxAssetDir/bin/$binary", targetFile)
                    
                    // 设置执行权限 - 使用chmod命令确保权限正确 qwq
                    try {
                        val chmodProcess = Runtime.getRuntime().exec("chmod 755 ${targetFile.absolutePath}")
                        val chmodResult = chmodProcess.waitFor()
                        LogManager.d(TAG, "$binary chmod结果: $chmodResult")
                        
                        // 备用方法：使用Java API
                        targetFile.setExecutable(true, true)
                    } catch (e: Exception) {
                        LogManager.w(TAG, "设置$binary 权限时出错: ${e.message}")
                        // 尝试备用方法
                        targetFile.setExecutable(true, true)
                    }
                    
                } catch (e: FileNotFoundException) {
                    LogManager.w(TAG, "Termux二进制文件不存在: $binary，跳过")
                } catch (e: Exception) {
                    LogManager.w(TAG, "复制Termux二进制文件失败: $binary - ${e.message}")
                }
            }
            
            // 复制库文件 AWA
            try {
                val libDir = File(termuxRoot, "lib")
                // 暂时跳过库文件复制，因为我们使用系统库
                LogManager.d(TAG, "跳过库文件复制，使用系统库 喵～")
            } catch (e: Exception) {
                LogManager.w(TAG, "复制Termux库文件时出错: ${e.message}")
            }
            
            // 复制环境配置文件 qwq
            try {
                val etcDir = File(termuxRoot, "etc")
                copyAssetFile("termux/environment.sh", File(etcDir, "environment.sh"))
                LogManager.d(TAG, "复制环境配置文件成功 AWA")
            } catch (e: Exception) {
                LogManager.w(TAG, "复制环境配置文件失败: ${e.message}")
            }
            
            // 复制启动脚本 喵～
            try {
                copyAssetFile("termux/startup.sh", File(termuxRoot, "startup.sh"))
                val startupFile = File(termuxRoot, "startup.sh")
                
                // 设置执行权限 - 使用chmod命令确保权限正确 qwq
                try {
                    val chmodProcess = Runtime.getRuntime().exec("chmod 755 ${startupFile.absolutePath}")
                    val chmodResult = chmodProcess.waitFor()
                    LogManager.d(TAG, "startup.sh chmod结果: $chmodResult")
                    
                    // 备用方法：使用Java API
                    startupFile.setExecutable(true, true)
                } catch (e: Exception) {
                    LogManager.w(TAG, "设置startup.sh权限时出错: ${e.message}")
                    // 尝试备用方法
                    startupFile.setExecutable(true, true)
                }
                
                LogManager.d(TAG, "复制启动脚本成功 qwq")
            } catch (e: Exception) {
                LogManager.w(TAG, "复制启动脚本失败: ${e.message}")
            }
            
        } catch (e: Exception) {
            LogManager.e(TAG, "❌ 复制Termux资源失败: ${e.message}")
        }
    }
    
    /**
     * 设置Termux环境变量 AWA
     */
    private fun setupTermuxEnvironment(termuxRoot: File) {
        try {
            val etcDir = File(termuxRoot, "etc")
            
            // 创建环境配置文件
            val envFile = File(etcDir, "environment")
            val envContent = """
                export PATH="${termuxRoot.absolutePath}/bin:${'$'}PATH"
                export LD_LIBRARY_PATH="${termuxRoot.absolutePath}/lib:${'$'}LD_LIBRARY_PATH"
                export TERMUX_PREFIX="${termuxRoot.absolutePath}"
                export HOME="${termuxRoot.absolutePath}/home"
                export TMPDIR="${termuxRoot.absolutePath}/tmp"
            """.trimIndent()
            
            envFile.writeText(envContent)
            LogManager.d(TAG, "创建Termux环境配置文件")
            
            // 创建home目录
            val homeDir = File(termuxRoot, "home")
            if (!homeDir.exists()) {
                homeDir.mkdirs()
            }
            
        } catch (e: Exception) {
            LogManager.e(TAG, "❌ 设置Termux环境失败: ${e.message}")
        }
    }
    
    /**
     * 使用Termux环境启动FRP进程 喵～
     */
    private fun startFRPWithTermux(configId: String, config: FRPConfig): Process? {
        try {
            LogManager.i(TAG, "🐧 使用Termux环境启动FRP进程", configId)
            
            val termuxRoot = getTermuxRootDir()
            val frpDir = getAppPrivateDir()
            val executable = if (config.type == FRPType.CLIENT) "frpc" else "frps"
            val frpExecutable = File(frpDir, executable)
            val configFile = File(frpDir, "$configId.toml")
            
            // 验证Termux环境
            if (!isTermuxEnvironmentEnabled()) {
                LogManager.w(TAG, "Termux环境不可用")
                return null
            }
            
            if (!frpExecutable.exists() || !frpExecutable.canExecute()) {
                LogManager.e(TAG, "FRP可执行文件不可用: ${frpExecutable.absolutePath}")
                return null
            }
            
            // 构建Termux启动命令 AWA
            val bashPath = File(termuxRoot, "bin/bash").absolutePath
            val envFile = File(termuxRoot, "etc/environment.sh").absolutePath
            val startupScript = File(termuxRoot, "startup.sh").absolutePath
            
            // 首先运行启动脚本初始化环境 qwq
            val initCommand = arrayOf(
                "/system/bin/sh",
                startupScript
            )
            
            LogManager.d(TAG, "初始化Termux环境: ${initCommand.joinToString(" ")}")
            val initProcess = ProcessBuilder(*initCommand).start()
            val initResult = initProcess.waitFor()
            
            if (initResult != 0) {
                LogManager.w(TAG, "Termux环境初始化失败，退出码: $initResult")
            }
            
            // 构建FRP启动命令 喵～
            val command = arrayOf(
                bashPath,
                "-c",
                "source $envFile && cd ${frpDir.absolutePath} && ${frpExecutable.absolutePath} -c ${configFile.absolutePath}"
            )
            
            LogManager.i(TAG, "Termux命令: ${command.joinToString(" ")}", configId)
            
            val processBuilder = ProcessBuilder(*command)
            processBuilder.directory(frpDir)
            processBuilder.redirectErrorStream(true)
            
            // 设置环境变量 AWA
            val env = processBuilder.environment()
            env["TERMUX_PREFIX"] = termuxRoot.absolutePath
            env["TERMUX_HOME"] = "${termuxRoot.absolutePath}/home"
            env["PATH"] = "${termuxRoot.absolutePath}/bin:/system/bin:/system/xbin"
            env["LD_LIBRARY_PATH"] = "${termuxRoot.absolutePath}/lib:/system/lib:/system/lib64"
            env["HOME"] = "${termuxRoot.absolutePath}/home"
            env["TMPDIR"] = "${termuxRoot.absolutePath}/tmp"
            env["SHELL"] = bashPath
            
            return processBuilder.start()
            
        } catch (e: Exception) {
            LogManager.e(TAG, "❌ Termux启动失败: ${e.message}", e, configId)
            return null
        }
    }

    /**
     * 初始化FRP二进制文件 qwq
     */
    private suspend fun initializeFRPBinaries() = withContext(Dispatchers.IO) {
        try {
            LogManager.i(TAG, "🔧 开始初始化FRP二进制文件")

            val frpDir = getAppPrivateDir()
            // 不需要创建目录，因为nativeLibraryDir已经存在 喵～
            LogManager.d(TAG, "使用FRP目录: ${frpDir.absolutePath}")

            val architecture = detectArchitecture()
            val frpAssetDir = "frp/$architecture"

            LogManager.i(TAG, "使用FRP资源目录: $frpAssetDir")

            // 复制frpc
            val frpcFile = File(frpDir, "frpc")
            LogManager.d(TAG, "开始处理frpc文件: ${frpcFile.absolutePath}")
            
            if (frpcFile.exists()) {
                LogManager.d(TAG, "frpc文件已存在，大小: ${frpcFile.length()} bytes")
                // 检查现有文件是否有效 qwq
                try {
                    val existingHeader = frpcFile.readBytes().take(4)
                    val elfMagic = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
                    val isValidELF = existingHeader.zip(elfMagic.toList()).all { it.first == it.second }
                    LogManager.d(TAG, "现有frpc文件格式检查 - ELF格式: $isValidELF")
                    
                    if (!isValidELF) {
                        LogManager.w(TAG, "现有frpc文件格式无效，重新复制")
                        frpcFile.delete()
                    } else if (frpcFile.length() > 1024 * 1024) { // 大于1MB
                        LogManager.d(TAG, "现有frpc文件看起来有效，跳过复制")
                        // 确保执行权限 - 使用双重权限设置 qwq
                        if (!frpcFile.canExecute()) {
                            try {
                                val chmodProcess = Runtime.getRuntime().exec("chmod 755 ${frpcFile.absolutePath}")
                                val chmodResult = chmodProcess.waitFor()
                                LogManager.d(TAG, "现有frpc chmod结果: $chmodResult")
                                
                                // 备用方法：使用Java API
                                val success = frpcFile.setExecutable(true, true)
                                LogManager.d(TAG, "现有frpc setExecutable结果: $success")
                            } catch (e: Exception) {
                                LogManager.w(TAG, "设置现有frpc权限时出错: ${e.message}")
                                // 尝试备用方法
                                val success = frpcFile.setExecutable(true, true)
                                LogManager.d(TAG, "现有frpc setExecutable备用结果: $success")
                            }
                        }
                    } else {
                        LogManager.w(TAG, "现有frpc文件太小，重新复制")
                        frpcFile.delete()
                    }
                } catch (e: Exception) {
                    LogManager.w(TAG, "检查现有frpc文件时出错: ${e.message}，重新复制")
                    frpcFile.delete()
                }
            }
            
            if (!frpcFile.exists()) {
                copyAssetFile("$frpAssetDir/frpc", frpcFile)
                
                // 设置执行权限 - 使用chmod命令确保权限正确 qwq
                try {
                    val chmodProcess = Runtime.getRuntime().exec("chmod 755 ${frpcFile.absolutePath}")
                    val chmodResult = chmodProcess.waitFor()
                    val errorOutput = chmodProcess.errorStream.bufferedReader().readText()
                    if (errorOutput.isNotEmpty()) {
                        LogManager.w(TAG, "chmod frpc 错误输出: $errorOutput")
                    }
                    LogManager.d(TAG, "frpc chmod结果: $chmodResult")
                    
                    // 备用方法：使用Java API
                    val success = frpcFile.setExecutable(true, true)
                    LogManager.d(TAG, "frpc setExecutable结果: $success")
                } catch (e: Exception) {
                    LogManager.w(TAG, "设置frpc权限时出错: ${e.message}")
                    // 尝试备用方法
                    frpcFile.setExecutable(true, true)
                }
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
                        LogManager.w(TAG, "现有frps文件格式无效，重新复制")
                        frpsFile.delete()
                    } else if (frpsFile.length() > 1024 * 1024) { // 大于1MB
                        LogManager.d(TAG, "现有frps文件看起来有效，跳过复制")
                        // 确保执行权限 - 使用双重权限设置 qwq
                        if (!frpsFile.canExecute()) {
                            try {
                                val chmodProcess = Runtime.getRuntime().exec("chmod 755 ${frpsFile.absolutePath}")
                                val chmodResult = chmodProcess.waitFor()
                                LogManager.d(TAG, "现有frps chmod结果: $chmodResult")
                                
                                // 备用方法：使用Java API
                                val success = frpsFile.setExecutable(true, true)
                                LogManager.d(TAG, "现有frps setExecutable结果: $success")
                            } catch (e: Exception) {
                                LogManager.w(TAG, "设置现有frps权限时出错: ${e.message}")
                                // 尝试备用方法
                                val success = frpsFile.setExecutable(true, true)
                                LogManager.d(TAG, "现有frps setExecutable备用结果: $success")
                            }
                        }
                    } else {
                        LogManager.w(TAG, "现有frps文件太小，重新复制")
                        frpsFile.delete()
                    }
                } catch (e: Exception) {
                    LogManager.w(TAG, "检查现有frps文件时出错: ${e.message}，重新复制")
                    frpsFile.delete()
                }
            }
            
            if (!frpsFile.exists()) {
                copyAssetFile("$frpAssetDir/frps", frpsFile)
                
                // 设置执行权限 - 使用chmod命令确保权限正确 qwq
                try {
                    val chmodProcess = Runtime.getRuntime().exec("chmod 755 ${frpsFile.absolutePath}")
                    val chmodResult = chmodProcess.waitFor()
                    val errorOutput = chmodProcess.errorStream.bufferedReader().readText()
                    if (errorOutput.isNotEmpty()) {
                        LogManager.w(TAG, "chmod frps 错误输出: $errorOutput")
                    }
                    LogManager.d(TAG, "frps chmod结果: $chmodResult")
                    
                    // 备用方法：使用Java API
                    val success = frpsFile.setExecutable(true, true)
                    LogManager.d(TAG, "frps setExecutable结果: $success")
                } catch (e: Exception) {
                    LogManager.w(TAG, "设置frps权限时出错: ${e.message}")
                    // 尝试备用方法
                    frpsFile.setExecutable(true, true)
                }
            }
            
            // 验证所有文件
            LogManager.i(TAG, "🔍 验证FRP二进制文件")
            
            val frpcValid = frpcFile.exists() && frpcFile.canExecute()
            val frpsValid = frpsFile.exists() && frpsFile.canExecute()
            
            LogManager.i(TAG, "frpc状态: 存在=${frpcFile.exists()}, 可执行=${frpcFile.canExecute()}, 大小=${if(frpcFile.exists()) frpcFile.length() else 0}")
            LogManager.i(TAG, "frps状态: 存在=${frpsFile.exists()}, 可执行=${frpsFile.canExecute()}, 大小=${if(frpsFile.exists()) frpsFile.length() else 0}")
            
            if (frpcValid && frpsValid) {
                // 额外的ELF格式验证
                try {
                    val frpcHeader = frpcFile.readBytes().take(4)
                    val frpsHeader = frpsFile.readBytes().take(4)
                    val elfMagic = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
                    
                    val frpcValid = frpcHeader.zip(elfMagic.toList()).all { it.first == it.second }
                    val frpsValid = frpsHeader.zip(elfMagic.toList()).all { it.first == it.second }
                    
                    if (frpcValid && frpsValid) {
                        LogManager.s(TAG, "✅ FRP二进制文件初始化完成并验证通过")
                    } else {
                        LogManager.e(TAG, "❌ FRP二进制文件格式验证失败")
                        LogManager.e(TAG, "frpc ELF: $frpcValid, frps ELF: $frpsValid")
                    }
                } catch (e: Exception) {
                    LogManager.w(TAG, "⚠️ FRP二进制文件格式验证时出错: ${e.message}")
                }
            } else {
                LogManager.e(TAG, "❌ FRP二进制文件验证失败")
                LogManager.e(TAG, "frpc有效: $frpcValid, frps有效: $frpsValid")
            }
            
        } catch (e: Exception) {
            LogManager.e(TAG, "❌ FRP二进制文件初始化失败: ${e.message}")
            throw e
        }
    }

    /**
     * 从assets复制文件到目标位置
     */
    private fun copyAssetFile(assetPath: String, targetFile: File) {
        try {
            LogManager.d(TAG, "复制资源文件: $assetPath -> ${targetFile.absolutePath}")

            // 如果文件已存在，先删除，确保覆盖 qwq
            if (targetFile.exists()) {
                targetFile.delete()
            }

            assets.open(assetPath).use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }

                    LogManager.d(TAG, "文件复制完成: $totalBytes bytes")
                }
            }

            if (targetFile.exists()) {
                LogManager.s(TAG, "✅ 文件复制成功: ${targetFile.name} (${targetFile.length()} bytes)")
            } else {
                LogManager.e(TAG, "❌ 文件复制后不存在: ${targetFile.name}")
            }

        } catch (e: FileNotFoundException) {
            LogManager.e(TAG, "❌ 资源文件不存在: $assetPath")
            LogManager.e(TAG, "请确保assets目录包含正确的FRP二进制文件")
            throw e
        } catch (e: Exception) {
            LogManager.e(TAG, "❌ 复制文件失败: ${e.message}")
            throw e
        }
    }

    /**
     * 使用PRoot启动FRP进程 AWA
     * PRoot提供更好的Linux环境兼容性
     */
    private fun startFRPWithPRoot(configId: String, config: FRPConfig): Process? {
        try {
            LogManager.i(TAG, "🐧 使用PRoot启动FRP进程", configId)

            val prootFile = File(getAppPrivateDir(), "proot/proot")
            val frpDir = getAppPrivateDir()
            val executable = if (config.type == FRPType.CLIENT) "frpc" else "frps"
            val frpExecutable = File(frpDir, executable)
            val configFile = File(frpDir, "$configId.toml")
            
            // 验证文件存在性
            if (!prootFile.exists() || !prootFile.canExecute()) {
                LogManager.w(TAG, "PRoot不可用，文件存在: ${prootFile.exists()}, 可执行: ${prootFile.canExecute()}")
                return null
            }
            
            if (!frpExecutable.exists() || !frpExecutable.canExecute()) {
                LogManager.e(TAG, "FRP可执行文件不可用: ${frpExecutable.absolutePath}")
                return null
            }
            
            // 构建PRoot命令
            val command = arrayOf(
                prootFile.absolutePath,
                "-r", "/",  // 使用根目录作为新的根
                "-w", frpDir.absolutePath,  // 设置工作目录
                frpExecutable.absolutePath,
                "-c", configFile.absolutePath
            )
            
            LogManager.i(TAG, "PRoot命令: ${command.joinToString(" ")}", configId)
            
            val processBuilder = ProcessBuilder(*command)
            processBuilder.directory(frpDir)
            processBuilder.redirectErrorStream(true)
            
            return processBuilder.start()
            
        } catch (e: Exception) {
            LogManager.e(TAG, "❌ PRoot启动失败: ${e.message}", e, configId)
            return null
        }
    }

    /**
     * 直接启动FRP进程（不使用PRoot）
     */
    private fun startFRPDirect(configId: String, config: FRPConfig): Process? {
        try {
            LogManager.i(TAG, "🔧 直接启动FRP进程", configId)

            val frpDir = getAppPrivateDir()
            val executable = if (config.type == FRPType.CLIENT) "frpc" else "frps"
            val frpExecutable = File(frpDir, executable)
            val configFile = File(frpDir, "$configId.toml")
            
            if (!frpExecutable.exists() || !frpExecutable.canExecute()) {
                LogManager.e(TAG, "FRP可执行文件不可用: ${frpExecutable.absolutePath}")
                return null
            }
            
            val command = arrayOf(frpExecutable.absolutePath, "-c", configFile.absolutePath)
            LogManager.i(TAG, "直接命令: ${command.joinToString(" ")}", configId)
            
            val processBuilder = ProcessBuilder(*command)
            processBuilder.directory(frpDir)
            processBuilder.redirectErrorStream(true)
            
            return processBuilder.start()
            
        } catch (e: Exception) {
            LogManager.e(TAG, "❌ 直接启动失败: ${e.message}", e, configId)
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
                LogManager.i(TAG, "=" + "=".repeat(59), configId)  // 修复乘法操作
                LogManager.i(TAG, "配置ID: $configId", configId)
                LogManager.d(TAG, "系统信息 - Android版本: ${Build.VERSION.RELEASE}, API: ${Build.VERSION.SDK_INT}, ABI: ${Build.SUPPORTED_ABIS.joinToString()}", configId)
                
                // 详细的配置验证 AWA
                val config = getConfigById(configId)
                if (config == null) {
                    LogManager.e(TAG, "❌ 配置不存在: $configId", configId = configId)
                    LogManager.e(TAG, "可用配置列表: ${ConfigManager.getAllConfigs(this@FRPService).map { config -> "${config.id}(${config.name})" }.joinToString()}", configId = configId)
                    return@launch
                }
                
                LogManager.s(TAG, "✅ 找到配置: ${config.name}, 类型: ${config.type}", configId)
                LogManager.i(TAG, "📋 配置详情:", configId)
                LogManager.i(TAG, "  - 服务器地址: ${config.serverAddr}:${config.serverPort}", configId)
                LogManager.i(TAG, "  - 代理类型: ${config.proxyType}", configId)
                LogManager.i(TAG, "  - 本地端口: ${config.localPort ?: "未设置"}", configId)
                LogManager.i(TAG, "  - 远程端口: ${config.remotePort ?: "未设置"}", configId)
                
                // 检查是否已经在运行
                if (runningProcesses.containsKey(configId)) {
                    LogManager.w(TAG, "⚠️ 进程已在运行，跳过启动", configId)
                    LogManager.i(TAG, "当前运行的进程数量: ${runningProcesses.size}", configId)
                    LogManager.i(TAG, "运行中的配置: ${runningProcesses.keys.joinToString()}", configId)
                    return@launch
                }
                
                // 环境检查 qwq
                LogManager.i(TAG, "-" + "-".repeat(39), configId)  // 修复乘法操作
                LogManager.i(TAG, "🔍 环境检查", configId)

                val frpDir = getAppPrivateDir()
                val executable = if (config.type == FRPType.CLIENT) "frpc" else "frps"
                val frpExecutable = File(frpDir, executable)
                
                LogManager.i(TAG, "FRP目录: ${frpDir.absolutePath}", configId)
                LogManager.i(TAG, "可执行文件: ${frpExecutable.absolutePath}", configId)
                LogManager.i(TAG, "文件存在: ${frpExecutable.exists()}", configId)
                LogManager.i(TAG, "文件可执行: ${frpExecutable.canExecute()}", configId)
                LogManager.i(TAG, "文件大小: ${if (frpExecutable.exists()) frpExecutable.length() else 0} bytes", configId)
                
                if (frpDir.exists()) {
                    LogManager.i(TAG, "FRP目录内容:", configId)
                    frpDir.listFiles()?.forEach { file ->
                        LogManager.i(TAG, "    * ${file.name} (${file.length()} bytes, 可执行: ${file.canExecute()})", configId)
                    }
                } else {
                    LogManager.e(TAG, "❌ FRP目录不存在", configId = configId)
                }
                
                if (!frpExecutable.exists()) {
                    LogManager.e(TAG, "❌ FRP可执行文件不存在: ${frpExecutable.absolutePath}", configId = configId)
                    LogManager.e(TAG, "请检查以下问题:", configId = configId)
                    LogManager.e(TAG, "  1. 检查assets/frp目录是否包含正确的二进制文件", configId = configId)
                    LogManager.e(TAG, "  2. 确认二进制文件与设备架构匹配 (当前: ${Build.SUPPORTED_ABIS.joinToString()})", configId = configId)
                    LogManager.e(TAG, "  3. 重新安装应用或清除应用数据", configId = configId)
                    return@launch
                }
                
                if (!frpExecutable.canExecute()) {
                    LogManager.e(TAG, "❌ FRP可执行文件没有执行权限", configId = configId)
                    LogManager.i(TAG, "尝试设置执行权限...", configId)
                    try {
                        val chmodProcess = Runtime.getRuntime().exec("chmod 755 ${frpExecutable.absolutePath}")
                        val chmodResult = chmodProcess.waitFor()
                        if (chmodResult == 0) {
                            LogManager.s(TAG, "✅ 执行权限设置成功", configId)
                        } else {
                            LogManager.e(TAG, "❌ 执行权限设置失败，退出码: $chmodResult", configId = configId)
                            return@launch
                        }
                    } catch (e: Exception) {
                        LogManager.e(TAG, "❌ 设置执行权限时出错: ${e.message}", e, configId)
                        return@launch
                    }
                }
                
                // 创建配置文件
                LogManager.i(TAG, "📝 创建配置文件", configId)
                val configContent = createConfigFile(config)
                val configFile = File(getAppPrivateDir(), "$configId.toml")
                configFile.writeText(configContent)
                LogManager.d(TAG, "配置文件路径: ${configFile.absolutePath}", configId)
                LogManager.d(TAG, "配置文件内容:\\n$configContent", configId)
                
                // 智能启动策略：优先使用Termux，然后PRoot，最后直接启动 AWA
                LogManager.i(TAG, "🚀 尝试启动进程", configId)
                
                var process: Process? = null
                var startMethod = ""
                
                // 首先尝试Termux启动
                if (isTermuxEnvironmentEnabled()) {
                    LogManager.i(TAG, "🐧 尝试使用Termux启动", configId)
                    process = startFRPWithTermux(configId, config)
                    startMethod = "Termux"
                }
                
                // 如果Termux失败，尝试PRoot启动
                if (process == null) {
                    val prootFile = File(getAppPrivateDir(), "proot/proot")
                    if (prootFile.exists() && prootFile.canExecute()) {
                        LogManager.i(TAG, "🐧 尝试使用PRoot启动", configId)
                        process = startFRPWithPRoot(configId, config)
                        startMethod = "PRoot"
                    }
                }
                
                // 如果都失败，回退到直接启动
                if (process == null) {
                    LogManager.i(TAG, "🔧 使用直接启动方式", configId)
                    process = startFRPDirect(configId, config)
                    startMethod = "Direct"
                }
                
                if (process != null) {
                    runningProcesses[configId] = process
                    processStatus[configId] = STATUS_RUNNING
                    
                    LogManager.s(TAG, "✅ FRP进程启动成功 (方式: $startMethod)", configId)
                    LogManager.i(TAG, "进程PID: ${getPid(process)}", configId)
                    LogManager.i(TAG, "当前运行的进程数量: ${runningProcesses.size}", configId)
                    
                    // 开始监控进程输出
                    monitorProcessOutput(configId, process)
                    
                } else {
                    LogManager.e(TAG, "❌ FRP进程启动失败", configId = configId)
                    processStatus[configId] = STATUS_ERROR
                }
                
            } catch (e: Exception) {
                LogManager.e(TAG, "❌ 启动FRP进程时发生异常: ${e.message}", configId = configId)
                LogManager.e(TAG, "异常堆栈: ${Log.getStackTraceString(e)}", configId = configId)
                processStatus[configId] = STATUS_ERROR
            }
        }
    }

    /**
     * 创建FRP配置文件 qwq
     */
    private fun createConfigFile(config: FRPConfig): String {
        return when (config.type) {
            FRPType.CLIENT -> {
                buildString {
                    appendLine("serverAddr = \"${config.serverAddr}\"")
                    appendLine("serverPort = ${config.serverPort}")
                    if (!config.token.isNullOrBlank()) {
                        appendLine("auth.token = \"${config.token}\"")
                    }
                    appendLine()
                    appendLine("[[proxies]]")
                    appendLine("name = \"${config.name}\"")
                    appendLine("type = \"${config.proxyType}\"")
                    if (config.localIP?.isNotBlank() == true) {
                        appendLine("localIP = \"${config.localIP}\"")
                    }
                    if (config.localPort != null && config.localPort > 0) {
                        appendLine("localPort = ${config.localPort}")
                    }
                    if (config.remotePort != null && config.remotePort > 0) {
                        appendLine("remotePort = ${config.remotePort}")
                    }
                    if (config.customDomain.isNotBlank()) {
                        appendLine("customDomains = [\"${config.customDomain}\"]")
                    }
                    if (config.subdomain.isNotBlank()) {
                        appendLine("subdomain = \"${config.subdomain}\"")
                    }
                }
            }
            FRPType.SERVER -> {
                buildString {
                    appendLine("bindPort = ${config.serverPort}")
                    if (!config.token.isNullOrBlank()) {
                        appendLine("auth.token = \"${config.token}\"")
                    }
                }
            }
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
                        
                        // 根据输出判断状态
                        when {
                            output.contains("start frps success") || output.contains("start frpc success") -> {
                                LogManager.s(TAG, "✅ FRP启动成功", configId)
                                processStatus[configId] = STATUS_RUNNING
                            }
                            output.contains("login to server success") -> {
                                LogManager.s(TAG, "✅ 服务器连接成功", configId)
                                processStatus[configId] = STATUS_RUNNING
                            }
                            output.contains("error") || output.contains("failed") -> {
                                LogManager.w(TAG, "⚠️ 检测到错误输出", configId)
                                // 不立即设置为错误状态，因为可能是非致命错误
                            }
                        }
                    }
                }
                
                // 进程结束，等待退出码
                val exitCode = process.waitFor()
                LogManager.i(TAG, "📡 进程监控结束，退出码: $exitCode", configId)
                
                // 清理
                runningProcesses.remove(configId)
                processStatus[configId] = if (exitCode == 0) STATUS_STOPPED else STATUS_ERROR
                
                LogManager.i(TAG, "当前运行的进程数量: ${runningProcesses.size}", configId)
                
            } catch (e: Exception) {
                LogManager.e(TAG, "❌ 监控进程输出时出错: ${e.message}", configId = configId)
            }
        }
    }

    /**
     * 停止FRP进程
     */
    fun stopFRPProcess(configId: String) {
        serviceScope.launch {
            try {
                LogManager.i(TAG, "🛑 停止FRP进程: $configId")
                
                val process = runningProcesses[configId]
                if (process != null) {
                    process.destroy()
                    processStatus[configId] = STATUS_STOPPED
                    
                    // 等待进程结束
                    try {
                        val terminated = process.waitFor()
                        LogManager.i(TAG, "✅ 进程已停止，退出码: $terminated", configId)
                    } catch (e: InterruptedException) {
                        LogManager.w(TAG, "⚠️ 等待进程结束时被中断", configId)
                        // 强制终止
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            process.destroyForcibly()
                        }
                    }
                    
                    runningProcesses.remove(configId)
                    processStatus[configId] = STATUS_STOPPED
                    
                    LogManager.s(TAG, "✅ FRP进程已停止", configId)
                    LogManager.i(TAG, "当前运行的进程数量: ${runningProcesses.size}", configId)
                } else {
                    LogManager.w(TAG, "⚠️ 进程不存在或已停止", configId)
                }
                
            } catch (e: Exception) {
                LogManager.e(TAG, "❌ 停止FRP进程时出错: ${e.message}", configId = configId)
            }
        }
    }

    /**
     * 获取配置信息
     */
    private fun getConfigById(configId: String): FRPConfig? {
        return try {
           ConfigManager.getAllConfigs(this).find { config -> config.id == configId }
        } catch (e: Exception) {
            LogManager.e(TAG, "获取配置失败: ${e.message}")
            null
        }
    }

    /**
     * 获取进程状态
     */
    fun getProcessStatus(configId: String): String {
        return processStatus[configId] ?: STATUS_STOPPED
    }

    /**
     * 获取所有运行中的进程
     */
    fun getRunningProcesses(): Map<String, String> {
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
    }

    /**
     * 获取进程PID AWA
     * 使用反射确保在所有Android版本和JVM上的兼容性 qwq
     */
    private fun getPid(process: Process): String {
        return try {
            val method = process.javaClass.methods.firstOrNull { it.name == "pid" && it.parameterCount == 0 }
            if (method != null) {
                method.invoke(process)?.toString() ?: "未知"
            } else {
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
    private fun detectArchitecture(): String {
        val supportedAbis = Build.SUPPORTED_ABIS
        LogManager.d(TAG, "支持的ABI: ${supportedAbis.joinToString()}")
        
        return when {
            supportedAbis.contains("arm64-v8a") -> "arm64-v8a"
            supportedAbis.contains("armeabi-v7a") -> "armeabi-v7a"
            supportedAbis.contains("x86_64") -> "x86_64"
            supportedAbis.contains("x86") -> "x86"
            else -> {
                LogManager.w(TAG, "未知架构，使用默认: arm64-v8a")
                "arm64-v8a"
            }
        }
    }

    /**
     * 智能环境选择器 AWA
     * 根据设备性能和环境可用性选择最佳启动方式
     */
    private fun selectBestEnvironment(): String {
        try {
            LogManager.i(TAG, "🤖 开始智能环境选择")
            
            // 检查Termux环境 喵～
            val termuxRoot = getTermuxRootDir()
            val bashFile = File(termuxRoot, "bin/bash")
            val envFile = File(termuxRoot, "etc/environment.sh")
            val startupScript = File(termuxRoot, "startup.sh")
            
            val termuxAvailable = termuxRoot.exists() && 
                                bashFile.exists() && bashFile.canExecute() &&
                                envFile.exists() &&
                                startupScript.exists() && startupScript.canExecute()
            
            if (termuxAvailable) {
                LogManager.i(TAG, "✅ Termux环境完整可用，优先使用 AWA")
                return "termux"
            } else {
                LogManager.w(TAG, "⚠️ Termux环境不完整: root=${termuxRoot.exists()}, bash=${bashFile.exists()}, env=${envFile.exists()}, startup=${startupScript.exists()}")
            }
            
            // 检查PRoot环境
            val prootFile = File(getAppPrivateDir(), "proot/proot")
            if (prootFile.exists() && prootFile.canExecute()) {
                LogManager.i(TAG, "✅ PRoot环境可用")
                return "proot"
            }
            
            // 回退到直接启动
            LogManager.i(TAG, "⚠️ 使用直接启动模式 qwq")
            return "direct"
            
        } catch (e: Exception) {
            LogManager.e(TAG, "❌ 环境选择失败: ${e.message}")
            return "direct"
        }
    }
    
    /**
     * 环境诊断报告 qwq
     * 包含Termux、PRoot和FRP环境的完整诊断
     */
    fun diagnoseAllEnvironments(): String {
        val report = StringBuilder()
        
        try {
            report.appendLine("=== 🔍 完整环境诊断报告 ===")
            report.appendLine("生成时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            report.appendLine("设备架构: ${detectArchitecture()}")
            report.appendLine("Android版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            report.appendLine("设备型号: ${Build.MODEL}")
            report.appendLine("制造商: ${Build.MANUFACTURER}")
            report.appendLine()
            
            // Termux环境检查 AWA
            report.appendLine("🐧 【Termux环境检查】")
            val termuxRoot = getTermuxRootDir()
            report.appendLine("根目录: ${termuxRoot.absolutePath}")
            report.appendLine("存在: ${termuxRoot.exists()}")
            
            if (termuxRoot.exists()) {
                // 检查基本目录结构
                val binDir = File(termuxRoot, "bin")
                val libDir = File(termuxRoot, "lib")
                val etcDir = File(termuxRoot, "etc")
                val homeDir = File(termuxRoot, "home")
                val tmpDir = File(termuxRoot, "tmp")
                
                report.appendLine("目录结构:")
                report.appendLine("  bin/: ${binDir.exists()}")
                report.appendLine("  lib/: ${libDir.exists()}")
                report.appendLine("  etc/: ${etcDir.exists()}")
                report.appendLine("  home/: ${homeDir.exists()}")
                report.appendLine("  tmp/: ${tmpDir.exists()}")
                
                // 检查关键文件 喵～
                val bashFile = File(binDir, "bash")
                val shFile = File(binDir, "sh")
                val envFile = File(etcDir, "environment.sh")
                val startupScript = File(termuxRoot, "startup.sh")
                
                report.appendLine("关键文件:")
                report.appendLine("  bash: ${bashFile.exists() && bashFile.canExecute()}")
                report.appendLine("  sh: ${shFile.exists() && shFile.canExecute()}")
                report.appendLine("  环境配置: ${envFile.exists()}")
                report.appendLine("  启动脚本: ${startupScript.exists() && startupScript.canExecute()}")
                
                // 检查基础工具 AWA
                if (binDir.exists()) {
                    val tools = listOf("ls", "cat", "echo")
                    val availableTools = tools.filter { tool ->
                        val toolFile = File(binDir, tool)
                        toolFile.exists() && toolFile.canExecute()
                    }
                    report.appendLine("  可用工具: ${availableTools.joinToString(", ")}")
                    
                    val allFiles = binDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
                    report.appendLine("  所有文件: ${allFiles.joinToString(", ")}")
                }
                
                // 检查环境配置内容 qwq
                if (envFile.exists()) {
                    try {
                        val envContent = envFile.readText()
                        val hasTermuxPrefix = envContent.contains("TERMUX_PREFIX")
                        val hasPath = envContent.contains("PATH")
                        report.appendLine("  环境配置完整性: PREFIX=$hasTermuxPrefix, PATH=$hasPath")
                    } catch (e: Exception) {
                        report.appendLine("  环境配置读取失败: ${e.message}")
                    }
                }
            }
            report.appendLine("整体可用性: ${isTermuxEnvironmentEnabled()}")
            report.appendLine()
            
            // PRoot环境检查
            report.appendLine("🐧 【PRoot环境检查】")
            val prootDir = File(getAppPrivateDir(), "proot")
            val prootFile = File(prootDir, "proot")
            report.appendLine("目录: ${prootDir.absolutePath}")
            report.appendLine("存在: ${prootDir.exists()}")
            report.appendLine("可执行文件: ${prootFile.exists() && prootFile.canExecute()}")
            if (prootFile.exists()) {
                report.appendLine("文件大小: ${prootFile.length()} bytes")
            }
            report.appendLine()
            
            // FRP环境检查
            report.appendLine("🚀 【FRP环境检查】")
            val frpDir = File(getAppPrivateDir(), "frp")
            report.appendLine("目录: ${frpDir.absolutePath}")
            report.appendLine("存在: ${frpDir.exists()}")
            if (frpDir.exists()) {
                val frpcFile = File(frpDir, "frpc")
                val frpsFile = File(frpDir, "frps")
                report.appendLine("frpc可用: ${frpcFile.exists() && frpcFile.canExecute()}")
                report.appendLine("frps可用: ${frpsFile.exists() && frpsFile.canExecute()}")
                
                val files = frpDir.listFiles()?.map { "${it.name} (${if(it.isDirectory()) "目录" else "${it.length()} bytes"})" }?.sorted() ?: emptyList()
                report.appendLine("目录内容: ${files.joinToString(", ")}")
            }
            report.appendLine()
            
            // 运行状态
            report.appendLine("📊 【运行状态】")
            report.appendLine("当前运行进程数: ${runningProcesses.size}")
            if (runningProcesses.isNotEmpty()) {
                runningProcesses.forEach { (configId, process) ->
                    val isAlive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { 
                        process.isAlive 
                    } else { 
                        try { 
                            process.exitValue() 
                            false 
                        } catch (e: IllegalThreadStateException) { 
                            true 
                        } 
                    }
                    report.appendLine("  $configId: ${if(isAlive) "运行中" else "已停止"} (PID: ${getPid(process)})")
                }
            }
            report.appendLine()
            
            // 推荐启动方式
            val bestEnv = selectBestEnvironment()
            val envName = when(bestEnv) {
                "termux" -> "Termux环境 (推荐)"
                "proot" -> "PRoot环境"
                "direct" -> "直接启动"
                else -> "未知"
            }
            report.appendLine("🎯 【推荐启动方式】: $envName")
            
            when(bestEnv) {
                "termux" -> report.appendLine("✅ 完整Linux环境，最佳兼容性和稳定性")
                "proot" -> report.appendLine("⚠️ 用户空间chroot，良好兼容性但性能略低")
                "direct" -> report.appendLine("⚠️ 直接启动，可能存在权限问题")
            }
            
        } catch (e: Exception) {
            report.appendLine("❌ 诊断过程出错: ${e.message}")
        }
        
        report.appendLine()
        report.appendLine("=== 诊断报告结束 ===")
        
        val result = report.toString()
        LogManager.i(TAG, "完整环境诊断完成")
        return result
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
            diagnosis.appendLine("检测到的架构: ${detectArchitecture()}")
            diagnosis.appendLine()
            
            // PRoot环境检查
            diagnosis.appendLine("【PRoot环境检查】")
            val prootDir = File(getAppPrivateDir(), "proot")
            val prootFile = File(prootDir, "proot")
            
            diagnosis.appendLine("PRoot目录:")
            diagnosis.appendLine("  路径: ${prootDir.absolutePath}")
            diagnosis.appendLine("  存在: ${prootDir.exists()}")
            
            diagnosis.appendLine("PRoot文件:")
            diagnosis.appendLine("  路径: ${prootFile.absolutePath}")
            diagnosis.appendLine("  存在: ${prootFile.exists()}")
            if (prootFile.exists()) {
                diagnosis.appendLine("  大小: ${prootFile.length()} bytes")
                diagnosis.appendLine("  可读: ${prootFile.canRead()}")
                diagnosis.appendLine("  可执行: ${prootFile.canExecute()}")
                diagnosis.appendLine("  最后修改: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(prootFile.lastModified()))}")
            }
            diagnosis.appendLine()
            
            // FRP环境检查
            diagnosis.appendLine("【FRP环境检查】")
            val frpDir = File(getAppPrivateDir(), "frp")
            diagnosis.appendLine("FRP目录:")
            diagnosis.appendLine("  路径: ${frpDir.absolutePath}")
            diagnosis.appendLine("  存在: ${frpDir.exists()}")
            
            if (frpDir.exists()) {
                diagnosis.appendLine("  目录内容:")
                frpDir.listFiles()?.forEach { file ->
                    diagnosis.appendLine("    - ${file.name} (${if(file.isDirectory()) "目录" else "${file.length()} bytes"})")
                }
            }
            diagnosis.appendLine()
            
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FRP服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "FRP后台服务通知"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, cn.lemwoodfrp.ui.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("柠枺FRP")
            .setContentText("FRP服务正在运行")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        LogManager.i(TAG, "🔚 FRPService 销毁")
        stopAllProcesses()
        serviceScope.cancel()
    }
}
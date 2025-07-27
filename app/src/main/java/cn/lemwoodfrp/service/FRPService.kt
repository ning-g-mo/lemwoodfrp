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
     * 初始化FRP二进制文件
     */
    private fun initializeFRPBinaries() {
        try {
            LogManager.i(TAG, "开始初始化FRP二进制文件")
            LogManager.d(TAG, "系统架构信息: ${Build.SUPPORTED_ABIS.joinToString()}")
            LogManager.d(TAG, "应用私有目录: ${filesDir.absolutePath}")
            
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
                copyAssetToFile("frp/frpc", frpcFile)
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
                copyAssetToFile("frp/frps", frpsFile)
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
                    val fileHeader = executableFile.readBytes().take(4)
                    val elfMagic = byteArrayOf(0x7F, 0x45, 0x4C, 0x46) // ELF magic number
                    val isELF = fileHeader.zip(elfMagic.toList()).all { it.first == it.second }
                    val headerHex = fileHeader.joinToString(" ") { "0x%02X".format(it) }
                    
                    LogManager.i(TAG, "  - 文件头: $headerHex", configId)
                    LogManager.i(TAG, "  - ELF魔数: ${elfMagic.joinToString(" ") { "0x%02X".format(it) }}", configId)
                    LogManager.i(TAG, "  - 格式验证: ${if (isELF) "✅ 有效的ELF文件" else "❌ 无效的ELF文件"}", configId)
                    
                    if (!isELF) {
                        LogManager.e(TAG, "❌ 文件不是有效的ELF可执行文件!", configId = configId)
                        LogManager.e(TAG, "💡 可能的原因:", configId = configId)
                        LogManager.e(TAG, "  1. 文件损坏或不完整", configId = configId)
                        LogManager.e(TAG, "  2. 文件不是为Android平台编译的", configId = configId)
                        LogManager.e(TAG, "  3. 下载或复制过程中出现错误", configId = configId)
                        return@launch
                    }
                    
                    LogManager.s(TAG, "✅ ELF文件格式验证通过", configId)
                } catch (e: Exception) {
                    LogManager.w(TAG, "⚠️ 无法读取文件头进行验证: ${e.message}", configId)
                    LogManager.w(TAG, "继续执行，但可能存在文件问题", configId)
                }
                
                // 权限检查和设置 qwq
                LogManager.i(TAG, "🔐 权限检查和设置:", configId)
                if (!executableFile.canExecute()) {
                    LogManager.w(TAG, "⚠️ 文件没有执行权限，开始设置权限...", configId)
                    
                    // 尝试使用Java API设置权限
                    LogManager.i(TAG, "  - 尝试使用setExecutable()方法", configId)
                    val setExecutableResult = executableFile.setExecutable(true)
                    LogManager.i(TAG, "  - setExecutable()结果: $setExecutableResult", configId)
                    
                    if (!setExecutableResult) {
                        LogManager.w(TAG, "  - Java API设置权限失败，尝试chmod命令", configId)
                        // 尝试使用chmod作为备用方案 qwq
                        try {
                            val chmodCommand = "chmod 755 ${executableFile.absolutePath}"
                            LogManager.i(TAG, "  - 执行命令: $chmodCommand", configId)
                            
                            val chmodProcess = Runtime.getRuntime().exec(chmodCommand)
                            val exitCode = chmodProcess.waitFor()
                            
                            // 读取命令输出
                            val output = chmodProcess.inputStream.bufferedReader().readText()
                            val errorOutput = chmodProcess.errorStream.bufferedReader().readText()
                            
                            LogManager.i(TAG, "  - chmod退出码: $exitCode", configId)
                            if (output.isNotEmpty()) {
                                LogManager.i(TAG, "  - chmod输出: $output", configId)
                            }
                            if (errorOutput.isNotEmpty()) {
                                LogManager.w(TAG, "  - chmod错误输出: $errorOutput", configId)
                            }
                            
                            if (exitCode != 0) {
                                LogManager.e(TAG, "❌ chmod执行失败，退出码: $exitCode", configId = configId)
                                LogManager.e(TAG, "💡 可能的解决方案:", configId = configId)
                                LogManager.e(TAG, "  1. 设备不支持chmod命令", configId = configId)
                                LogManager.e(TAG, "  2. 文件系统不支持执行权限", configId = configId)
                                LogManager.e(TAG, "  3. SELinux策略限制", configId = configId)
                                return@launch
                            }
                            
                            LogManager.s(TAG, "✅ chmod命令执行成功", configId)
                        } catch (e: Exception) {
                            LogManager.e(TAG, "❌ chmod命令执行异常: ${e.message}", configId = configId)
                            LogManager.e(TAG, "异常类型: ${e.javaClass.simpleName}", configId = configId)
                            return@launch
                        }
                    } else {
                        LogManager.s(TAG, "✅ Java API设置权限成功", configId)
                    }
                } else {
                    LogManager.s(TAG, "✅ 文件已具有执行权限", configId)
                }
                
                // 再次检查权限 AWA
                LogManager.i(TAG, "🔄 最终权限验证:", configId)
                LogManager.i(TAG, "  - 可读权限: ${executableFile.canRead()}", configId)
                LogManager.i(TAG, "  - 可执行权限: ${executableFile.canExecute()}", configId)
                
                if (!executableFile.canExecute()) {
                    LogManager.e(TAG, "❌ 最终权限验证失败，文件仍无执行权限", configId = configId)
                    return@launch
                }
                
                LogManager.s(TAG, "✅ 所有文件检查通过，开始创建配置文件", configId)
                
                // 配置文件创建 qwq
                LogManager.i(TAG, "📝 创建配置文件:", configId)
                val configFile = createConfigFile(config)
                LogManager.i(TAG, "  - 配置文件路径: ${configFile.absolutePath}", configId)
                LogManager.i(TAG, "  - 配置文件存在: ${configFile.exists()}", configId)
                LogManager.i(TAG, "  - 配置文件大小: ${configFile.length()} bytes", configId)
                
                // 显示配置文件内容（前几行）用于调试
                try {
                    val configContent = configFile.readText()
                    val lines = configContent.lines()
                    LogManager.i(TAG, "  - 配置文件内容预览 (前5行):", configId)
                    lines.take(5).forEachIndexed { index, line ->
                        LogManager.i(TAG, "    ${index + 1}: $line", configId)
                    }
                    if (lines.size > 5) {
                        LogManager.i(TAG, "    ... (共${lines.size}行)", configId)
                    }
                } catch (e: Exception) {
                    LogManager.w(TAG, "  - 无法读取配置文件内容: ${e.message}", configId)
                }
                
                // 命令构建 AWA
                LogManager.i(TAG, "⚙️ 构建启动命令:", configId)
                val command = buildFRPCommand(config, configFile)
                
                LogManager.i(TAG, "  - 完整命令: ${command.joinToString(" ")}", configId)
                LogManager.i(TAG, "  - 命令组件:", configId)
                command.forEachIndexed { index, part ->
                    LogManager.i(TAG, "    [$index]: $part", configId)
                }
                
                val workingDir = File(filesDir, "frp")
                LogManager.i(TAG, "  - 工作目录: ${workingDir.absolutePath}", configId)
                LogManager.i(TAG, "  - 工作目录存在: ${workingDir.exists()}", configId)
                
                val processBuilder = ProcessBuilder(command)
                processBuilder.directory(workingDir)
                
                // 设置环境变量，确保在Android环境下正确执行 qwq
                LogManager.i(TAG, "🌍 环境变量配置:", configId)
                val env = processBuilder.environment()
                val originalPath = env["PATH"] ?: ""
                val newPath = "${workingDir.absolutePath}:$originalPath"
                env["PATH"] = newPath
                env["LD_LIBRARY_PATH"] = workingDir.absolutePath
                
                LogManager.i(TAG, "  - 原始PATH: $originalPath", configId)
                LogManager.i(TAG, "  - 新PATH: $newPath", configId)
                LogManager.i(TAG, "  - LD_LIBRARY_PATH: ${env["LD_LIBRARY_PATH"]}", configId)
                
                // 显示所有相关环境变量
                val relevantEnvVars = listOf("PATH", "LD_LIBRARY_PATH", "ANDROID_ROOT", "ANDROID_DATA")
                LogManager.i(TAG, "  - 相关环境变量:", configId)
                relevantEnvVars.forEach { varName ->
                    val value = env[varName] ?: "未设置"
                    LogManager.i(TAG, "    $varName = $value", configId)
                }
                
                // 重定向错误输出到标准输出，便于日志记录
                processBuilder.redirectErrorStream(true)
                LogManager.i(TAG, "  - 错误输出重定向: 已启用", configId)
                
                LogManager.i(TAG, "🚀 启动进程...", configId)
                LogManager.i(TAG, "=" * 50, configId)
                
                // 尝试启动进程并捕获详细错误信息
                val process = try {
                    val startTime = System.currentTimeMillis()
                    val result = processBuilder.start()
                    val endTime = System.currentTimeMillis()
                    
                    LogManager.s(TAG, "✅ 进程启动成功!", configId)
                    LogManager.i(TAG, "  - 启动耗时: ${endTime - startTime}ms", configId)
                    LogManager.i(TAG, "  - 进程对象: ${result.javaClass.simpleName}", configId)
                    
                    result
                } catch (e: Exception) {
                    LogManager.e(TAG, "❌ 进程启动失败!", configId = configId)
                    LogManager.e(TAG, "异常类型: ${e.javaClass.simpleName}", configId = configId)
                    LogManager.e(TAG, "异常消息: ${e.message}", configId = configId)
                    LogManager.e(TAG, "💡 可能的原因:", configId = configId)
                    LogManager.e(TAG, "  1. 二进制文件不兼容当前设备架构 (${Build.SUPPORTED_ABIS.joinToString()})", configId = configId)
                    LogManager.e(TAG, "  2. 权限不足或SELinux策略限制", configId = configId)
                    LogManager.e(TAG, "  3. 依赖库缺失或版本不匹配", configId = configId)
                    LogManager.e(TAG, "  4. 系统资源不足", configId = configId)
                    LogManager.e(TAG, "  5. 配置文件格式错误", configId = configId)
                    
                    // 显示异常堆栈的前几行
                    val stackTrace = e.stackTrace.take(3)
                    if (stackTrace.isNotEmpty()) {
                        LogManager.e(TAG, "堆栈跟踪:", configId = configId)
                        stackTrace.forEach { element ->
                            LogManager.e(TAG, "  at $element", configId = configId)
                        }
                    }
                    
                    return@launch
                }
                
                runningProcesses[configId] = process
                
                LogManager.i(TAG, "📊 更新进程状态...", configId)
                val startTime = System.currentTimeMillis()
                val pid = getPid(process)
                
                val status = FRPStatus(
                    configId = configId,
                    isRunning = true,
                    pid = pid,
                    startTime = startTime
                )
                processStatus[configId] = status
                
                LogManager.s(TAG, "✅ FRP进程启动成功!", configId)
                LogManager.i(TAG, "  - 配置ID: $configId", configId)
                LogManager.i(TAG, "  - 进程PID: $pid", configId)
                LogManager.i(TAG, "  - 启动时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(startTime))}", configId)
                LogManager.i(TAG, "  - 进程状态: 运行中 ✨", configId)
                
                // 显示当前所有运行的进程
                val allRunning = runningProcesses.keys
                LogManager.i(TAG, "🔄 当前运行的配置数量: ${allRunning.size}", configId)
                if (allRunning.isNotEmpty()) {
                    LogManager.i(TAG, "  - 运行中的配置: ${allRunning.joinToString(", ")}", configId)
                }
                
                LogManager.i(TAG, "🔍 开始进程监控...", configId)
                LogManager.i(TAG, "=" * 50, configId)
                
                // 监控进程状态和输出
                monitorProcess(configId, process)
                
            } catch (e: Exception) {
                LogManager.e(TAG, "启动FRP进程失败", e, configId)
                LogManager.e(TAG, "详细错误信息: ${e.javaClass.simpleName} - ${e.message}", configId = configId)
                LogManager.e(TAG, "堆栈跟踪: ${e.stackTrace.take(5).joinToString("\n") { "  at $it" }}", configId = configId)
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
     * 监控进程状态
     */
    private fun monitorProcess(configId: String, process: Process) {
        serviceScope.launch {
            try {
                LogManager.i(TAG, "🔍 进程监控启动", configId)
                LogManager.i(TAG, "  - 监控线程: ${Thread.currentThread().name}", configId)
                LogManager.i(TAG, "  - 配置ID: $configId", configId)
                LogManager.i(TAG, "  - 进程对象: ${process.javaClass.simpleName}", configId)
                
                // 检查进程初始状态
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
                
                LogManager.i(TAG, "  - 进程初始状态: ${if (isAlive) "存活 ✅" else "已退出 ❌"}", configId)
                
                if (!isAlive) {
                    LogManager.w(TAG, "⚠️ 进程在监控开始前就已退出!", configId)
                    val exitCode = try {
                        process.exitValue()
                    } catch (e: Exception) {
                        -1
                    }
                    LogManager.w(TAG, "  - 退出码: $exitCode", configId)
                }
                
                // 读取进程输出
                LogManager.i(TAG, "📖 开始读取进程输出...", configId)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                var outputLineCount = 0
                val maxOutputLines = 100 // 限制输出行数，避免日志过多 qwq
                val startReadTime = System.currentTimeMillis()
                
                LogManager.i(TAG, "  - 最大输出行数限制: $maxOutputLines", configId)
                LogManager.i(TAG, "  - 开始读取时间: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(startReadTime))}", configId)
                
                while (reader.readLine().also { line = it } != null && outputLineCount < maxOutputLines) {
                    line?.let { 
                        outputLineCount++
                        LogManager.d(TAG, "📄 FRP输出[$outputLineCount]: $it", configId)
                        
                        // 检查特定的错误模式 AWA
                        when {
                            it.contains("error", ignoreCase = true) -> {
                                LogManager.e(TAG, "❌ FRP错误输出[$outputLineCount]: $it", configId = configId)
                            }
                            it.contains("failed", ignoreCase = true) -> {
                                LogManager.e(TAG, "💥 FRP失败输出[$outputLineCount]: $it", configId = configId)
                            }
                            it.contains("warning", ignoreCase = true) -> {
                                LogManager.w(TAG, "⚠️ FRP警告输出[$outputLineCount]: $it", configId)
                            }
                            it.contains("success", ignoreCase = true) || it.contains("start", ignoreCase = true) -> {
                                LogManager.s(TAG, "✅ FRP成功输出[$outputLineCount]: $it", configId)
                            }
                            it.contains("connect", ignoreCase = true) -> {
                                LogManager.i(TAG, "🔗 FRP连接输出[$outputLineCount]: $it", configId)
                            }
                            it.contains("proxy", ignoreCase = true) -> {
                                LogManager.i(TAG, "🌐 FRP代理输出[$outputLineCount]: $it", configId)
                            }
                        }
                    }
                }
                
                val endReadTime = System.currentTimeMillis()
                LogManager.i(TAG, "📖 输出读取完成", configId)
                LogManager.i(TAG, "  - 总输出行数: $outputLineCount", configId)
                LogManager.i(TAG, "  - 读取耗时: ${endReadTime - startReadTime}ms", configId)
                
                if (outputLineCount >= maxOutputLines) {
                    LogManager.w(TAG, "⚠️ 输出行数达到限制($maxOutputLines)，停止记录更多输出", configId)
                }
                
                // 等待进程结束
                LogManager.i(TAG, "⏳ 等待进程结束...", configId)
                val waitStartTime = System.currentTimeMillis()
                val exitCode = process.waitFor()
                val waitEndTime = System.currentTimeMillis()
                
                LogManager.i(TAG, "🏁 进程已结束", configId)
                LogManager.i(TAG, "  - 退出码: $exitCode", configId)
                LogManager.i(TAG, "  - 等待耗时: ${waitEndTime - waitStartTime}ms", configId)
                
                // 分析退出码 qwq
                when (exitCode) {
                    0 -> LogManager.s(TAG, "✅ 进程正常退出", configId)
                    1 -> LogManager.e(TAG, "❌ 进程异常退出 - 一般错误", configId = configId)
                    2 -> LogManager.e(TAG, "❌ 进程异常退出 - 配置错误", configId = configId)
                    126 -> LogManager.e(TAG, "❌ 进程异常退出 - 权限不足或文件不可执行", configId = configId)
                    127 -> LogManager.e(TAG, "❌ 进程异常退出 - 命令未找到", configId = configId)
                    128 -> LogManager.e(TAG, "❌ 进程异常退出 - 无效的退出参数", configId = configId)
                    else -> {
                        if (exitCode > 128) {
                            val signal = exitCode - 128
                            LogManager.e(TAG, "💀 进程被信号终止 - 信号: $signal", configId = configId)
                        } else {
                            LogManager.e(TAG, "❓ 进程异常退出 - 未知错误码: $exitCode", configId = configId)
                        }
                    }
                }
                
                // 更新状态
                LogManager.i(TAG, "🔄 更新进程状态为已停止...", configId)
                runningProcesses.remove(configId)
                val status = processStatus[configId]?.copy(
                    isRunning = false
                ) ?: FRPStatus(
                    configId = configId,
                    isRunning = false
                )
                processStatus[configId] = status
                
                LogManager.i(TAG, "✅ 进程监控结束", configId)
                LogManager.i(TAG, "  - 最终状态: 已停止", configId)
                LogManager.i(TAG, "  - 剩余运行进程: ${runningProcesses.size}", configId)
                
            } catch (e: Exception) {
                LogManager.e(TAG, "💥 监控进程时发生异常!", configId = configId)
                LogManager.e(TAG, "  - 异常类型: ${e.javaClass.simpleName}", configId = configId)
                LogManager.e(TAG, "  - 异常消息: ${e.message}", configId = configId)
                
                // 显示异常堆栈的前几行
                val stackTrace = e.stackTrace.take(3)
                if (stackTrace.isNotEmpty()) {
                    LogManager.e(TAG, "  - 堆栈跟踪:", configId = configId)
                    stackTrace.forEach { element ->
                        LogManager.e(TAG, "    at $element", configId = configId)
                    }
                }
                
                // 清理状态
                LogManager.i(TAG, "🧹 清理异常状态...", configId)
                runningProcesses.remove(configId)
                val status = processStatus[configId]?.copy(
                    isRunning = false,
                    errorMessage = e.message
                ) ?: FRPStatus(
                    configId = configId,
                    isRunning = false,
                    errorMessage = e.message
                )
                processStatus[configId] = status
                
                LogManager.i(TAG, "✅ 异常状态清理完成", configId)
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
            LogManager.d(TAG, "创建配置目录: ${configDir.absolutePath}", config.id)
        }
        
        val configFile = File(configDir, "${config.id}.toml")
        
        // 根据配置类型生成TOML配置文件内容 喵～
        val configContent = when (config.type) {
            FRPType.CLIENT -> generateClientConfig(config)
            FRPType.SERVER -> generateServerConfig(config)
        }
        
        LogManager.d(TAG, "生成的配置文件内容:\n$configContent", config.id)
        
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
        val executableFile = File(frpDir, executable)
        
        // 在Android环境下，直接执行二进制文件 AWA
        return listOf(
            executableFile.absolutePath,
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
        LogManager.d(TAG, "诊断报告:\n$result")
        
        return result
    }
}
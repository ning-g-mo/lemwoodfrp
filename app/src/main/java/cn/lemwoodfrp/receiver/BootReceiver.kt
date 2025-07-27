package cn.lemwoodfrp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.lemwoodfrp.service.FRPService
import cn.lemwoodfrp.utils.ConfigManager

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                if (ConfigManager.isAutoStartEnabled(context)) {
                    startAutoStartConfigs(context)
                }
            }
        }
    }
    
    private fun startAutoStartConfigs(context: Context) {
        val configs = ConfigManager.getConfigs(context)
        val autoStartConfigs = configs.filter { it.autoStart && it.isEnabled }
        
        if (autoStartConfigs.isNotEmpty()) {
            val serviceIntent = Intent(context, FRPService::class.java)
            
            // API 26+ 使用 startForegroundService，API 25 使用 startService qwq
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            // 启动所有自启动配置 AWA
            autoStartConfigs.forEach { config ->
                val startIntent = Intent(context, FRPService::class.java).apply {
                    action = "start_frp"
                    putExtra("config_id", config.id)
                }
                context.startService(startIntent)
            }
        }
    }
}
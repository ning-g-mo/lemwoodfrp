package cn.lemwoodfrp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import cn.lemwoodfrp.utils.ConfigManager

/**
 * 柠枺frp应用程序类
 * 负责应用程序的全局初始化
 */
class LemwoodFRPApplication : Application() {
    
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "frp_service_channel"
        lateinit var instance: LemwoodFRPApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化配置管理器
        ConfigManager.init(this)
        
        // 创建通知渠道
        createNotificationChannel()
    }
    
    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
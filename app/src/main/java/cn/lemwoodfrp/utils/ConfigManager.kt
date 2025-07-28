package cn.lemwoodfrp.utils

import android.content.Context
import android.content.SharedPreferences
import cn.lemwoodfrp.model.FRPConfig
import cn.lemwoodfrp.network.NetworkManager
import com.google.gson.reflect.TypeToken

object ConfigManager {
    
    private const val PREFS_NAME = "lemwood_frp_prefs"
    private const val KEY_CONFIGS = "frp_configs"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_LAST_VERSION_CHECK = "last_version_check"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 保存FRP配置列表
     */
    fun saveConfigs(context: Context, configs: List<FRPConfig>) {
        val json = NetworkManager.gson.toJson(configs)
        getPrefs(context).edit().putString(KEY_CONFIGS, json).apply()
    }
    
    /**
     * 获取FRP配置列表
     */
    fun getConfigs(context: Context): List<FRPConfig> {
        val json = getPrefs(context).getString(KEY_CONFIGS, null) ?: return emptyList()
        val type = object : TypeToken<List<FRPConfig>>() {}.type
        return try {
            NetworkManager.gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 添加配置
     */
    fun addConfig(context: Context, config: FRPConfig) {
        val configs = getConfigs(context).toMutableList()
        configs.add(config)
        saveConfigs(context, configs)
    }
    
    /**
     * 更新配置
     */
    fun updateConfig(context: Context, config: FRPConfig) {
        val configs = getConfigs(context).toMutableList()
        val index = configs.indexOfFirst { it.id == config.id }
        if (index != -1) {
            configs[index] = config
            saveConfigs(context, configs)
        }
    }
    
    /**
     * 删除配置
     */
    fun deleteConfig(context: Context, configId: String) {
        val configs = getConfigs(context).toMutableList()
        configs.removeAll { it.id == configId }
        saveConfigs(context, configs)
    }
    
    /**
     * 获取开机自启设置
     */
    fun isAutoStartEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_START, false)
    }
    
    /**
     * 设置开机自启
     */
    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }
    
    /**
     * 获取上次版本检查时间
     */
    fun getLastVersionCheckTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_VERSION_CHECK, 0)
    }
    
    /**
     * 设置上次版本检查时间
     */
    fun setLastVersionCheckTime(context: Context, time: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_VERSION_CHECK, time).apply()
    }

    /**
     * 导出配置到JSON字符串
     */
    fun exportConfigs(context: Context): String {
        val configs = getConfigs(context)
        return NetworkManager.gson.toJson(configs)
    }

    /**
     * 从JSON字符串导入配置
     */
    fun importConfigs(context: Context, json: String): Boolean {
        return try {
            val type = object : TypeToken<List<FRPConfig>>() {}.type
            val configs = NetworkManager.gson.fromJson<List<FRPConfig>>(json, type)
            saveConfigs(context, configs)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // 添加方法别名以匹配代码中使用的方法名
    fun getAllConfigs(context: Context): List<FRPConfig> = getConfigs(context)
    fun getAutoStart(context: Context): Boolean = isAutoStartEnabled(context)
    fun setAutoStart(context: Context, enabled: Boolean) = setAutoStartEnabled(context, enabled)
    
    // 初始化方法
    fun init(context: Context) {
        // 初始化逻辑，如果需要的话
    }
}
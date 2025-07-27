package cn.lemwoodfrp.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FRPConfig(
    val id: String,
    val name: String,
    val type: FRPType,
    val serverAddr: String,
    val serverPort: Int,
    val token: String? = null,
    val localIP: String? = null,
    val localPort: Int? = null,
    val remotePort: Int? = null,
    val protocol: String = "tcp",
    val proxyType: String = "tcp", // 代理类型 qwq
    val isEnabled: Boolean = true,
    val autoStart: Boolean = false,
    val isRunning: Boolean = false
) : Parcelable

enum class FRPType {
    CLIENT, SERVER
}

@Parcelize
data class FRPStatus(
    val configId: String,
    val isRunning: Boolean,
    val pid: Int? = null,
    val startTime: Long? = null,
    val errorMessage: String? = null
) : Parcelable
package cn.lemwoodfrp.model

import com.google.gson.annotations.SerializedName

data class GitHubRelease(
    @SerializedName("tag_name")
    val tagName: String,
    val name: String,
    val body: String,
    @SerializedName("published_at")
    val publishedAt: String,
    @SerializedName("html_url")
    val htmlUrl: String,
    val assets: List<GitHubAsset>
)

data class GitHubAsset(
    val name: String,
    @SerializedName("browser_download_url")
    val downloadUrl: String,
    val size: Long
)

data class Announcement(
    val id: String,
    val title: String,
    val content: String,
    val publishTime: String,
    val priority: AnnouncementPriority = AnnouncementPriority.NORMAL
)

enum class AnnouncementPriority {
    LOW, NORMAL, HIGH, URGENT
}
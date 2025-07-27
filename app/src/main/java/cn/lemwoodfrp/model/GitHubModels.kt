package cn.lemwoodfrp.model

data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String,
    val published_at: String,
    val html_url: String,
    val assets: List<GitHubAsset>
)

data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
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
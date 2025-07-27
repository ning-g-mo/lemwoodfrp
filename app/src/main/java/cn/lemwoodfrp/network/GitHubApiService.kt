package cn.lemwoodfrp.network

import cn.lemwoodfrp.model.Announcement
import cn.lemwoodfrp.model.GitHubRelease
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubApiService {
    
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
    
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitHubRelease>
    
    @GET("repos/{owner}/{repo}/contents/announcements.json")
    suspend fun getAnnouncements(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubFileContent>
}

data class GitHubFileContent(
    val content: String,
    val encoding: String
)
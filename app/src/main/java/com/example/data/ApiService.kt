package com.example.data

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class LoginRequest(val username: String, val password: String)

@JsonClass(generateAdapter = true)
data class LoginResponse(val token: String, val isAdmin: Boolean)

@JsonClass(generateAdapter = true)
data class UnlockRequest(val passcode: String)

@JsonClass(generateAdapter = true)
data class UnlockResponse(val url: String, val name: String)

@JsonClass(generateAdapter = true)
data class Announcement(val id: Int, val content: String, val createdAt: String? = null)

@JsonClass(generateAdapter = true)
data class AnnouncementRequest(val content: String)

@JsonClass(generateAdapter = true)
data class AccountRef(val nickname: String?)

@JsonClass(generateAdapter = true)
data class Comment(val id: Int = 0, val content: String, val author: String? = null, val accountId: String? = null, val account: AccountRef? = null, val createdAt: String? = null)

@JsonClass(generateAdapter = true)
data class CommentRequest(val content: String, val author: String? = null)

@JsonClass(generateAdapter = true)
data class AppConfig(val aboutAuthor: String = "No info", val updateInfo: String = "No updates")

@JsonClass(generateAdapter = true)
data class NicknameRequest(val nickname: String)

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("portal/user/nickname")
    suspend fun updateNickname(
        @Header("Authorization") token: String,
        @Body request: NicknameRequest
    ): Response<Unit>

    @POST("portal/unlock")
    suspend fun unlock(
        @Header("Authorization") token: String,
        @Body request: UnlockRequest
    ): Response<UnlockResponse>

    @GET("portal/community")
    suspend fun getComments(
        @Header("Authorization") token: String
    ): Response<List<Comment>>

    @POST("portal/community")
    suspend fun addComment(
        @Header("Authorization") token: String,
        @Body request: CommentRequest
    ): Response<Comment>

    @DELETE("portal/community/{id}")
    suspend fun deleteComment(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Unit>

    @PUT("portal/community/{id}")
    suspend fun updateComment(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: CommentRequest
    ): Response<Comment>

    @GET("portal/config")
    suspend fun getConfig(
        @Header("Authorization") token: String
    ): Response<AppConfig>

    @POST("admin/config")
    suspend fun updateConfig(
        @Header("Authorization") token: String,
        @Body config: AppConfig
    ): Response<AppConfig>

    @GET("portal/announcements")
    suspend fun getAnnouncements(
        @Header("Authorization") token: String
    ): Response<List<Announcement>>

    @POST("admin/announcements")
    suspend fun addAnnouncement(
        @Header("Authorization") token: String,
        @Body request: AnnouncementRequest
    ): Response<Announcement>

    @DELETE("admin/announcements/{id}")
    suspend fun deleteAnnouncement(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Unit>

    @GET("admin/sites")
    suspend fun getSites(@Header("Authorization") token: String): Response<List<Site>>

    @POST("admin/sites")
    suspend fun addSite(
        @Header("Authorization") token: String,
        @Body site: Site
    ): Response<Site>

    @DELETE("admin/sites/{id}")
    suspend fun deleteSite(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Unit>

    @PUT("admin/sites/{id}")
    suspend fun updateSite(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body site: Site
    ): Response<Site>

    @GET("admin/accounts")
    suspend fun getAccounts(@Header("Authorization") token: String): Response<List<Account>>

    @POST("admin/accounts")
    suspend fun addAccount(
        @Header("Authorization") token: String,
        @Body account: Account
    ): Response<Account>

    @PUT("admin/accounts/{id}")
    suspend fun updateAccount(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body account: Account
    ): Response<Account>

    @DELETE("admin/accounts/{id}")
    suspend fun deleteAccount(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Unit>

    @PUT("admin/announcements/{id}")
    suspend fun updateAnnouncement(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: AnnouncementRequest
    ): Response<Announcement>
}

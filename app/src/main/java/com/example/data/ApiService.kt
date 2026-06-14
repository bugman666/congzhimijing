package com.example.data

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class LoginRequest(val username: String, val password: String)

@JsonClass(generateAdapter = true)
data class LoginResponse(val token: String, val isAdmin: Boolean)

@JsonClass(generateAdapter = true)
data class UnlockRequest(val passcode: String)

@JsonClass(generateAdapter = true)
data class UnlockResponse(val url: String, val name: String)

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("portal/unlock")
    suspend fun unlock(
        @Header("Authorization") token: String,
        @Body request: UnlockRequest
    ): Response<UnlockResponse>

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

    @GET("admin/accounts")
    suspend fun getAccounts(@Header("Authorization") token: String): Response<List<Account>>

    @POST("admin/accounts")
    suspend fun addAccount(
        @Header("Authorization") token: String,
        @Body account: Account
    ): Response<Account>

    @DELETE("admin/accounts/{id}")
    suspend fun deleteAccount(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Unit>
}

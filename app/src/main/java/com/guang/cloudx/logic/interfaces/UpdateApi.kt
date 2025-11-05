package com.guang.cloudx.logic.interfaces

import retrofit2.Response
import retrofit2.http.GET

interface UpdateApi {
    @GET("version.php")
    suspend fun checkUpdate(): Response<UpdateResponse>
}

data class UpdateResponse(
    val version: String,
    val build: Int,
    val force: Boolean,
    val download_url: String,
    val changelog: String
)

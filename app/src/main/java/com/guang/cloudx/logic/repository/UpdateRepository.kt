package com.guang.cloudx.logic.repository

import com.guang.cloudx.logic.interfaces.UpdateResponse
import com.guang.cloudx.logic.network.UpdateRetrofitClient

class UpdateRepository {

    private val api = UpdateRetrofitClient.api

    suspend fun getLatestVersion(): UpdateResult {
        return try {
            val res = api.checkUpdate()
            if (res.isSuccessful && res.body() != null) {
                UpdateResult.Success(res.body()!!)
            } else {
                UpdateResult.Error("服务器返回错误")
            }
        } catch (e: Exception) {
            UpdateResult.Error("检查更新失败：${e.message}")
        }
    }
}

sealed class UpdateResult {
    data class Success(val data: UpdateResponse) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
    object Loading : UpdateResult()
}
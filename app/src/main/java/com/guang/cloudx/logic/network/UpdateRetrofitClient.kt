package com.guang.cloudx.logic.network

import com.guang.cloudx.logic.interfaces.UpdateApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object UpdateRetrofitClient {
    private const val BASE_URL = "https://guang.yuexinya.top/"

    val api: UpdateApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UpdateApi::class.java)
    }
}

package com.guang.cloudx.logic.network

import com.guang.cloudx.logic.interfaces.UpdateApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object UpdateRetrofitClient {
    private const val BASE_URL = "https://api.github.com/"

    val api: UpdateApi by lazy {
        Retrofit
            .Builder()
            .baseUrl(BASE_URL)
            .client(
                OkHttpClient
                    .Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .callTimeout(15, TimeUnit.SECONDS)
                    .build(),
            ).addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UpdateApi::class.java)
    }
}

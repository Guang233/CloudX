package com.guang.cloudx.logic.network

import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

object ServiceCreator {
    private  const val BASE_URL = "https://interface.music.163.com/"
    private  val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
    fun <S> createService(serviceClass: Class<S>): S =  retrofit.create(serviceClass)
    inline fun <reified T> createService(): T = createService(T::class.java)
}
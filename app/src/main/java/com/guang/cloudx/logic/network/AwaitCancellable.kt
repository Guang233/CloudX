package com.guang.cloudx.logic.network

import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cancels this lookup only, so stopping one song never cancels another song's API request. */
internal suspend fun <T> Call<T>.awaitCancellable(): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback<T> {
                override fun onResponse(
                    call: Call<T>,
                    response: Response<T>,
                ) {
                    val body = response.body()
                    if (body != null) {
                        continuation.resume(body)
                    } else {
                        continuation.resumeWithException(RuntimeException("response body is null"))
                    }
                }

                override fun onFailure(
                    call: Call<T>,
                    error: Throwable,
                ) {
                    continuation.resumeWithException(error)
                }
            },
        )
    }

package com.guang.cloudx

import com.guang.cloudx.logic.network.awaitCancellable
import kotlinx.coroutines.*
import okhttp3.Request
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

class AwaitCancellableTest {
    private class PendingCall : Call<String> {
        lateinit var callback: Callback<String>
        private var cancelled = false

        override fun enqueue(callback: Callback<String>) {
            this.callback = callback
        }

        override fun cancel() {
            cancelled = true
        }

        override fun isCanceled() = cancelled

        override fun isExecuted() = this::callback.isInitialized

        override fun clone(): Call<String> = PendingCall()

        override fun execute(): Response<String> = error("Synchronous execution is not used")

        override fun request(): Request = Request.Builder().url("https://example.invalid").build()

        override fun timeout() = Timeout()
    }

    @Test fun cancellingOneSongLookupLeavesOthersRunning() =
        runBlocking {
            val first = PendingCall()
            val second = PendingCall()
            val a = async(start = CoroutineStart.UNDISPATCHED) { first.awaitCancellable() }
            val b = async(start = CoroutineStart.UNDISPATCHED) { second.awaitCancellable() }
            a.cancelAndJoin()
            assertTrue(first.isCanceled)
            assertFalse(second.isCanceled)
            first.callback.onFailure(first, IOException("cancelled socket")) // Late cancellation callback is harmless.
            second.callback.onResponse(second, Response.success("second song"))
            assertEquals("second song", b.await())
        }

    @Test fun lateResponseCannotResumeACancelledTask() =
        runBlocking {
            val call = PendingCall()
            var resumed = false
            val task =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    call.awaitCancellable()
                    resumed = true
                }
            task.cancelAndJoin()
            call.callback.onResponse(call, Response.success("late result"))
            assertFalse(resumed)
            assertTrue(task.isCancelled)
        }

    @Test fun lookupFailureStillReachesCaller() =
        runBlocking {
            supervisorScope {
                val call = PendingCall()
                val result = async(start = CoroutineStart.UNDISPATCHED) { call.awaitCancellable() }
                val failure = IOException("network unavailable")
                call.callback.onFailure(call, failure)
                try {
                    result.await()
                    fail("Expected lookup failure")
                } catch (e: IOException) {
                    // Coroutine stack-trace recovery may copy the exception instance.
                    assertEquals(failure.message, e.message)
                }
            }
        }
}

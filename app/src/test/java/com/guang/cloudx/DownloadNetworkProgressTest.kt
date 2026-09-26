package com.guang.cloudx

import com.guang.cloudx.logic.repository.MusicDownloadRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.Closeable
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Local JVM HTTP server only: no device, external server, cookies or real music. */
class DownloadNetworkProgressTest {
    @get:Rule val temporary = TemporaryFolder()

    private class LocalServer(
        respond: (OutputStream) -> Unit,
    ) : Closeable {
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))

        @Volatile private var client: Socket? = null
        val url = "http://127.0.0.1:${listener.localPort}/audio"
        private val worker =
            thread(isDaemon = true) {
                try {
                    listener.accept().use { socket ->
                        client = socket
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { /* Consume HTTP request headers. */ }
                        respond(socket.getOutputStream())
                    }
                } catch (_: java.io.IOException) {
                    // Client cancellation or fixture teardown.
                }
            }

        override fun close() {
            client?.close()
            listener.close()
            worker.join(1500)
        }
    }

    @Test fun twoTransfersRunTogetherAndCancellingOneDoesNotCancelTheOther() = runBlocking {
        val release = CountDownLatch(1)
        fun server(value: Byte) = LocalServer { output ->
            output.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n".toByteArray())
            output.write(byteArrayOf(value))
            output.flush()
            release.await(8, TimeUnit.SECONDS)
            output.write(byteArrayOf(value))
        }
        val firstServer = server(1)
        val secondServer = server(2)
        try {
            val firstStarted = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Unit>()
            val firstFile = temporary.newFile()
            val secondFile = temporary.newFile()
            val repository = MusicDownloadRepository() // Same repository/client as the real service.
            withTimeout(5000) {
                val first = launch(Dispatchers.IO) {
                    repository.downloadFile(url = firstServer.url, file = firstFile,
                        bytesCallback = { bytes, _ -> if (bytes > 0) firstStarted.complete(Unit) })
                }
                val second = launch(Dispatchers.IO) {
                    repository.downloadFile(url = secondServer.url, file = secondFile,
                        bytesCallback = { bytes, _ -> if (bytes > 0) secondStarted.complete(Unit) })
                }
                firstStarted.await()
                secondStarted.await() // Both sockets must transfer before either server is released.
                assertTrue(first.isActive)
                assertTrue(second.isActive)
                first.cancelAndJoin()
                assertTrue(second.isActive)
                assertTrue(firstFile.delete())
                release.countDown()
                second.join()
                assertArrayEquals(byteArrayOf(2, 2), secondFile.readBytes())
            }
        } finally {
            release.countDown()
            firstServer.close()
            secondServer.close()
        }
    }

    @Test fun knownLengthReportsRealByteTotals() =
        runBlocking {
            val payload = ByteArray(150_000) { (it % 127).toByte() }
            LocalServer { output ->
                output.write("HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                output.write(payload)
            }.use { server ->
                val reports = mutableListOf<Pair<Long, Long?>>()
                val file = temporary.newFile()
                MusicDownloadRepository().downloadFile(
                    url = server.url,
                    file = file,
                    bytesCallback = { bytes, total -> reports += bytes to total },
                )
                assertArrayEquals(payload, file.readBytes())
                assertEquals(0L to payload.size.toLong(), reports.first())
                assertEquals(payload.size.toLong() to payload.size.toLong(), reports.last())
                assertTrue(reports.zipWithNext().all { (a, b) -> b.first >= a.first })
            }
        }

    @Test fun chunkedResponseReportsUnknownTotalNotFakePercent() =
        runBlocking {
            val payload = ByteArray(32_000) { 42 }
            LocalServer { output ->
                output.write(
                    "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n${payload.size.toString(
                        16,
                    )}\r\n".toByteArray(),
                )
                output.write(payload)
                output.write("\r\n0\r\n\r\n".toByteArray())
            }.use { server ->
                val reports = mutableListOf<Pair<Long, Long?>>()
                val file = temporary.newFile()
                MusicDownloadRepository().downloadFile(
                    url = server.url,
                    file = file,
                    bytesCallback = { bytes, total -> reports += bytes to total },
                )
                assertArrayEquals(payload, file.readBytes())
                assertEquals(payload.size.toLong(), reports.last().first)
                assertTrue(reports.all { it.second == null })
            }
        }

    @Test fun cancellationClosesAStalledSocketBeforeCleanup() =
        runBlocking {
            val releaseServer = CountDownLatch(1)
            val started = CompletableDeferred<Unit>()
            val server =
                LocalServer { output ->
                    output.write("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(byteArrayOf(1))
                    output.flush()
                    releaseServer.await(10, TimeUnit.SECONDS)
                }
            try {
                val file = temporary.newFile()
                withTimeout(4000) {
                    val job =
                        launch(Dispatchers.IO) {
                            MusicDownloadRepository().downloadFile(
                                url = server.url,
                                file = file,
                                bytesCallback = { bytes, _ -> if (bytes > 0) started.complete(Unit) },
                            )
                        }
                    started.await()
                    job.cancelAndJoin()
                    assertTrue(job.isCancelled)
                    assertEquals(1L, file.length())
                    assertTrue(file.delete())
                }
            } finally {
                releaseServer.countDown()
                server.close()
            }
        }
}

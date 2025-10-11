package com.guang.cloudx.logic.network

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.brotli.BrotliInterceptor
import org.brotli.dec.BrotliInputStream
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

object ServiceCreator {
    private const val BASE_URL = "https://interface.music.163.com/"

    val fuckNeteaseMusicClient = OkHttpClient.Builder()
        .addInterceptor(BrotliInterceptor) // 这个 Brotli 卡了我很长时间，网易云你为什么不用gzip
        .addInterceptor(HexResponseInterceptor())
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.11.203994")
                .build()
            chain.proceed(request)
        }
        .build()
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(fuckNeteaseMusicClient)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
    fun <S> createService(serviceClass: Class<S>): S =  retrofit.create(serviceClass)
    inline fun <reified T> createService(): T = createService(T::class.java)
}


// 这段直接用ai写的，发现如果不手动直接截获hex就会在string转换为hex时出现EF BF BD的UTF8错误字符的问题
class HexResponseInterceptor : Interceptor {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response

        // 获取原始字节
        val rawBytes = body.bytes()

        // 根据内容编码解压
        val encoding = response.header("Content-Encoding", "")
        val decompressedBytes = when {
            encoding?.contains("br") == true  -> decompressBrotli(rawBytes)
            encoding?.contains("gzip") == true  -> decompressGzip(rawBytes)
            else -> rawBytes
        }

        // 转换为 HEX 字符串
        val hexString = bytesToHex(decompressedBytes)

        // 创建新的响应体（包含 HEX 字符串）
        val newBody = hexString
            .toResponseBody(body.contentType())

        // 记录到日志（可选）
//        Log.d("HEX_RESPONSE", "Decompressed HEX (${decompressedBytes.size} bytes):\n${hexString.take(100)}...")

        return response.newBuilder()
            .body(newBody)
            .removeHeader("Content-Encoding") // 移除压缩头
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun decompressBrotli(data: ByteArray): ByteArray {
        return BrotliInputStream(ByteArrayInputStream(data)).use { bis ->
            bis.readAllBytes()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun decompressGzip(data: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(data)).use { gis ->
            gis.readAllBytes()
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }
}
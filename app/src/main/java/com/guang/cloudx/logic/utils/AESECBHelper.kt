package com.guang.cloudx.logic.utils

import android.annotation.SuppressLint
import android.os.Build
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class AESECBHelper {

    enum class Format {
        STRING,  // 普通字符串
        HEX,     // 十六进制字符串
        BASE64   // Base64 编码字符串
    }

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/ECB/PKCS5Padding"
        private val SECRET_KEY = "e82ckenh8dichen8".toByteArray(Charsets.UTF_8)

        /**
         * AES-ECB 加密
         * @param input 输入数据
         * @param inputFormat 输入格式 (默认字符串)
         * @param outputFormat 输出格式 (默认HEX)
         */
        fun encrypt(
            input: String,
            inputFormat: Format = Format.STRING,
            outputFormat: Format = Format.HEX,
            secretKey: ByteArray = SECRET_KEY
        ): String {
            val inputBytes = when (inputFormat) {
                Format.STRING -> input.toByteArray(Charsets.UTF_8)
                Format.HEX -> hexStringToByteArray(input)
                Format.BASE64 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Base64.getDecoder().decode(input)
                } else {
                    throw RuntimeException("VERSION.SDK_INT < O")
                }
            }

            val encrypted = encryptBytes(inputBytes, secretKey)

            return when (outputFormat) {
                Format.STRING -> String(encrypted, Charsets.UTF_8)
                Format.HEX -> byteArrayToHexString(encrypted)
                Format.BASE64 -> Base64.getEncoder().encodeToString(encrypted)
            }
        }

        /**
         * AES-ECB 解密
         * @param input 输入数据
         * @param inputFormat 输入格式 (默认HEX)
         * @param outputFormat 输出格式 (默认字符串)
         */
        fun decrypt(
            input: String,
            inputFormat: Format = Format.HEX,
            outputFormat: Format = Format.STRING,
            secretKey: ByteArray = SECRET_KEY
        ): String {
            val inputBytes = when (inputFormat) {
                Format.STRING -> input.toByteArray(Charsets.UTF_8)
                Format.HEX -> hexStringToByteArray(input)
                Format.BASE64 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Base64.getDecoder().decode(input)
                } else {
                    throw RuntimeException("VERSION.SDK_INT < O")
                }
            }

            val decrypted = decryptBytes(inputBytes, secretKey)

            return when (outputFormat) {
                Format.STRING -> String(decrypted, Charsets.UTF_8)
                Format.HEX -> byteArrayToHexString(decrypted)
                Format.BASE64 -> Base64.getEncoder().encodeToString(decrypted)
            }
        }

        @SuppressLint("GetInstance")
        private fun encryptBytes(input: ByteArray, secretKey: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val keySpec = SecretKeySpec(secretKey, ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            return cipher.doFinal(input)
        }

        @SuppressLint("GetInstance")
        private fun decryptBytes(input: ByteArray, secretKey: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val keySpec = SecretKeySpec(secretKey, ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
//            Log.d("TAG", byteArrayToHexString(input))
            val doFinal = cipher.doFinal(input)
            return doFinal
        }

        private fun byteArrayToHexString(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02x".format(it) }
        }

        private fun hexStringToByteArray(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "Invalid HEX string length" }
            return hex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }
    }
}
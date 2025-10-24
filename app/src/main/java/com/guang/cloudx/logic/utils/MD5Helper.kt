package com.guang.cloudx.logic.utils

import java.security.MessageDigest

object MD5Helper {
    /**
     * 计算字符串的 MD5 哈希值
     * @param input 输入字符串
     * @return 32位小写十六进制 MD5 哈希值
     */
    fun calculateMD5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))

        // 将字节数组转换为十六进制字符串
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
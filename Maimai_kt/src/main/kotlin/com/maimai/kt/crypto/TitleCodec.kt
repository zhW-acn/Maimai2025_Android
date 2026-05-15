package com.maimai.kt.crypto

import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * TitleServer 协议编码器。
 *
 * 请求流程：JSON 字符串 -> zlib 压缩 -> AES-CBC/PKCS5Padding 加密。
 * 响应流程：AES 解密 -> 如有 zlib 头则解压 -> UTF-8 字符串。
 */
class TitleCodec(
    private val key: String,
    private val iv: String,
    private val obfuscateParam: String,
) {
    /** 把逻辑 API 名称转换成服务器路径里的 MD5 hash。 */
    fun apiHash(apiName: String): String = md5("$apiName${"MaimaiChn"}$obfuscateParam")

    /** 编码请求体。 */
    fun encodeRequest(payload: String): ByteArray = encrypt(deflate(payload.toByteArray(Charsets.UTF_8)))

    /** 解码响应体。 */
    fun decodeResponse(content: ByteArray): String {
        val decrypted = decrypt(content)
        val finalBytes = if (decrypted.size >= 2 && decrypted[0] == 0x78.toByte() && decrypted[1] == 0x9c.toByte()) {
            inflate(decrypted)
        } else {
            decrypted
        }
        return finalBytes.toString(Charsets.UTF_8)
    }

    private fun encrypt(content: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        return cipher.doFinal(content)
    }

    private fun decrypt(content: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        return cipher.doFinal(content)
    }

    private fun deflate(content: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(content)
        deflater.finish()
        val buffer = ByteArray(content.size + 256)
        val output = ArrayList<Byte>()
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            for (index in 0 until count) output.add(buffer[index])
        }
        deflater.end()
        return output.toByteArray()
    }

    private fun inflate(content: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(content)
        val buffer = ByteArray(content.size * 4 + 256)
        val output = ArrayList<Byte>()
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && inflater.needsInput()) break
            for (index in 0 until count) output.add(buffer[index])
        }
        inflater.end()
        return output.toByteArray()
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

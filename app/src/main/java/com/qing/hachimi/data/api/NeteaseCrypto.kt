package com.qing.hachimi.data.api

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object NeteaseCrypto {

    private val AES_KEY = "e82ckenh8dichen8".toByteArray(Charsets.UTF_8)
    private const val WEAPI_PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val WEAPI_IV = "0102030405060708"
    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val PUBLIC_KEY = """
-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB
-----END PUBLIC KEY-----
"""
    private val random = SecureRandom()

    init {
        require(AES_KEY.size == 16) { "AES key must be 16 bytes" }
    }

    fun encryptParams(apiPath: String, payload: String): String {
        val urlPath = apiPath.replace("/eapi/", "/api/")
        val digest = md5Hex("nobody${urlPath}use${payload}md5forencrypt")
        val raw = "${urlPath}-36cd479b6b5-${payload}-36cd479b6b5-${digest}"

        val keySpec = SecretKeySpec(AES_KEY, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(raw.toByteArray(Charsets.UTF_8))

        return encrypted.joinToString("") { "%02x".format(it) }
    }

    fun encryptWeapi(payload: String): Map<String, String> {
        val secretKey = randomSecretKey()
        val params = aesCbcEncrypt(
            aesCbcEncrypt(payload, WEAPI_PRESET_KEY),
            secretKey
        )
        val encSecKey = rsaEncrypt(secretKey.reversed())
        return mapOf("params" to params, "encSecKey" to encSecKey)
    }

    private fun randomSecretKey(): String = buildString {
        repeat(16) {
            append(BASE62[random.nextInt(BASE62.length)])
        }
    }

    private fun aesCbcEncrypt(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(WEAPI_IV.toByteArray(Charsets.UTF_8))
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    private fun rsaEncrypt(text: String): String {
        val pem = PUBLIC_KEY
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s"), "")
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pem))) as RSAPublicKey
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun md5Hex(input: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

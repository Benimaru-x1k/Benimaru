package com.benimaru.official

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

internal enum class PremiumStatus { VALID, INVALID, UNREACHABLE }

internal object PremiumRepository {

    fun checkStatus(username: String, storedHash: String, deviceId: String): PremiumStatus {
        if (username.isBlank()) return PremiumStatus.INVALID

        var conn: HttpURLConnection? = null
        try {
            conn = URL("${AppConfig.USERS_URL}?t=${System.currentTimeMillis()}")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.useCaches = false

            if (conn.responseCode != 200) return PremiumStatus.UNREACHABLE

            val jsonObject = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val usersArray = jsonObject.getJSONArray("users")

            for (i in 0 until usersArray.length()) {
                val userObj = usersArray.getJSONObject(i)
                if (!userObj.optString("username").equals(username.trim(), ignoreCase = true)) continue

                if (userObj.has("passwordHash") && storedHash.isNotEmpty()) {
                    val remoteHash = userObj.getString("passwordHash").trim().lowercase()
                    if (!MessageDigest.isEqual(storedHash.toByteArray(), remoteHash.toByteArray())) {
                        return PremiumStatus.INVALID
                    }
                }

                val devices = userObj.optJSONArray("devices") ?: return PremiumStatus.INVALID
                if (devices.length() > 2) return PremiumStatus.INVALID
                for (j in 0 until devices.length()) {
                    if (devices.getString(j) == deviceId) return PremiumStatus.VALID
                }
                return PremiumStatus.INVALID
            }
            return PremiumStatus.INVALID
        } catch (e: Exception) {
            return PremiumStatus.UNREACHABLE
        } finally {
            conn?.disconnect()
        }
    }

    fun hashPassword(username: String, password: String): String {
        val salt = "benimaru:${username.trim().lowercase()}".toByteArray(Charsets.UTF_8)
        val key = pbkdf2HmacSha256(password.toByteArray(Charsets.UTF_8), salt, 100_000, 32)
        return key.joinToString("") { "%02x".format(it) }
    }

    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(password, "HmacSHA256"))
        val result = ByteArray(dkLen)
        var offset = 0
        var block = 1
        while (offset < dkLen) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val len = minOf(t.size, dkLen - offset)
            System.arraycopy(t, 0, result, offset, len)
            offset += len
            block++
        }
        return result
    }
}

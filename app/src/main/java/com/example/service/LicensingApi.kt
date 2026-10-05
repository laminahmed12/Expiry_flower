package com.example.service

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CloudLicense(
    val id: String,
    val plan: String,
    val expiresAt: String?,
    val permanent: Boolean
)

class LicensingApi(private val context: Context) {
    companion object {
        const val BASE_URL = "https://expiry-tracker.lamin-ahmed12.workers.dev"
    }

    private fun deviceId(): String =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )?.takeIf { it.isNotBlank() } ?: "android-${android.os.Build.MODEL}-${android.os.Build.VERSION.SDK_INT}"

    suspend fun activate(code: String): Result<CloudLicense> = withContext(Dispatchers.IO) {
        request(
            method = "POST",
            path = "/v1/activate",
            body = JSONObject()
                .put("code", code.trim().uppercase())
                .put("deviceId", deviceId())
                .toString()
        )
    }

    suspend fun check(): Result<CloudLicense> = withContext(Dispatchers.IO) {
        request(
            method = "GET",
            path = "/v1/license/${java.net.URLEncoder.encode(deviceId(), "UTF-8")}"
        )
    }

    suspend fun create(customerName: String, plan: String, ownerPin: String): Result<CloudLicenseCreation> =
        withContext(Dispatchers.IO) {
            requestCreate(customerName, plan, ownerPin)
        }

    private fun requestCreate(customerName: String, plan: String, ownerPin: String): Result<CloudLicenseCreation> {
        val connection = open("POST", "/v1/admin/licenses")
        return try {
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Owner-Pin", ownerPin.trim())
            connection.doOutput = true
            connection.outputStream.use {
                it.write(
                    JSONObject()
                        .put("customerName", customerName.trim().ifBlank { "عميل" })
                        .put("plan", plan)
                        .toString()
                        .toByteArray(Charsets.UTF_8)
                )
            }
            val body = read(connection)
            if (connection.responseCode !in 200..299) {
                Result.failure(IllegalStateException(messageFor(body)))
            } else {
                val json = JSONObject(body)
                Result.success(
                    CloudLicenseCreation(
                        id = json.optString("id"),
                        code = json.getString("code"),
                        plan = json.getString("plan"),
                        expiresAt = json.optString("expiresAt").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun revoke(code: String, ownerPin: String, revoke: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val connection = open("POST", "/v1/admin/licenses/revoke")
        try {
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Owner-Pin", ownerPin.trim())
            connection.doOutput = true
            connection.outputStream.use {
                it.write(
                    JSONObject()
                        .put("code", code.trim().uppercase())
                        .put("revoke", revoke)
                        .toString()
                        .toByteArray(Charsets.UTF_8)
                )
            }
            val body = read(connection)
            if (connection.responseCode !in 200..299) {
                Result.failure(IllegalStateException(messageFor(body)))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection.disconnect()
        }
    }

    private fun request(method: String, path: String, body: String? = null): Result<CloudLicense> {
        val connection = open(method, path)
        return try {
            connection.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val response = read(connection)
            if (connection.responseCode !in 200..299) {
                Result.failure(IllegalStateException(messageFor(response)))
            } else {
                val json = JSONObject(response).getJSONObject("license")
                Result.success(
                    CloudLicense(
                        id = json.getString("id"),
                        plan = json.getString("plan"),
                        expiresAt = json.optString("expiresAt").takeIf { it.isNotBlank() && it != "null" },
                        permanent = json.optBoolean("permanent")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(method: String, path: String): HttpURLConnection {
        return (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            useCaches = false
        }
    }

    private fun read(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
    }

    private fun messageFor(body: String): String {
        return try {
            when (JSONObject(body).optString("error")) {
                "invalid_license" -> "رمز التفعيل غير صحيح."
                "device_mismatch" -> "هذا الترخيص مرتبط بجهاز آخر."
                "license_expired" -> "انتهت صلاحية الترخيص."
                "unauthorized" -> "رمز المالك غير صحيح."
                "invalid_plan" -> "نوع الترخيص غير صحيح."
                else -> "تعذر الاتصال بخادم الترخيص."
            }
        } catch (_: Exception) {
            "تعذر الاتصال بخادم الترخيص."
        }
    }
}

data class CloudLicenseCreation(
    val id: String,
    val code: String,
    val plan: String,
    val expiresAt: String?
)

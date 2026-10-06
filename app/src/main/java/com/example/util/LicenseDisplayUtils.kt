package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * أدوات عرض بيانات الترخيص للمستخدم.
 *
 * الخادم يعيد expiresAt بصيغة ISO-8601 UTC، لذلك لا يجب عرض القيمة الخام
 * مباشرة في واجهة المستخدم.
 */
object LicenseDisplayUtils {

    private val inputFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    )

    fun formatExpiryDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        val parsed = inputFormats.firstNotNullOfOrNull { formatter ->
            runCatching {
                formatter.timeZone = TimeZone.getTimeZone("UTC")
                formatter.parse(raw)
            }.getOrNull()
        } ?: return raw

        val output = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        return output.format(parsed)
    }

    /**
     * إخفاء معظم رمز الترخيص في شاشة المستخدم.
     * نحتفظ بالبداية فقط للتعرف عليه، ولا نعرض الرمز الكامل.
     */
    fun maskLicenseCode(code: String?): String? {
        if (code.isNullOrBlank()) return null
        val normalized = code.trim()
        val visibleLength = minOf(7, normalized.length)
        return if (normalized.length <= visibleLength) {
            normalized
        } else {
            normalized.take(visibleLength) + "••••••"
        }
    }
}

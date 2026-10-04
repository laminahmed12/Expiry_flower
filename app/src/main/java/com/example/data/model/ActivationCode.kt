package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activation_codes")
data class ActivationCode(
    @PrimaryKey
    val code: String,
    val durationMonths: Int, // 6 = 6 أشهر، 12 = سنة، -1 = دائم
    val createdAt: Long = System.currentTimeMillis(),
    val isRevoked: Boolean = false,
    val isActivated: Boolean = false,
    val activatedAt: Long? = null,
    val note: String = ""
) {
    val durationTitleAr: String
        get() = when (durationMonths) {
            6 -> "6 أشهر"
            12 -> "سنة واحدة"
            -1 -> "ترخيص دائم"
            else -> "$durationMonths أشهر"
        }
}

data class LicenseStatus(
    val isTrialActive: Boolean,
    val trialDaysRemaining: Int,
    val isLicensed: Boolean,
    val licenseType: String, // "تجريبي" أو "6 أشهر" أو "سنة" أو "دائم"
    val licenseExpiryFormatted: String?,
    val isAccessAllowed: Boolean, // هل يسمح للمستخدم بالدخول والتعديل
    val activeCode: String? = null
)

package com.example.util

import com.example.data.model.ItemCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ExpiryStatus {
    EXPIRED,
    EXPIRING_SOON,
    SAFE
}

object ExpiryUtils {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * حساب الأيام المتبقية حتى تاريخ الانتهاء
     * قيمة سالبة تعني أن المادة منتهية الصلاحية
     */
    fun calculateDaysRemaining(expiryDateStr: String): Long {
        return try {
            val expiryDate = dateFormat.parse(expiryDateStr) ?: return 999L

            // تصفير الوقت للمقارنة بالأيام الدقيقة
            val todayCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val expiryCal = Calendar.getInstance().apply {
                time = expiryDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffMs = expiryCal.timeInMillis - todayCal.timeInMillis
            TimeUnit.MILLISECONDS.toDays(diffMs)
        } catch (e: Exception) {
            999L
        }
    }

    /**
     * تحديد حالة الصلاحية بناءً على عتبة التنبيه المختارة من قبل المستخدم
     */
    fun getExpiryStatus(expiryDateStr: String, thresholdDays: Int): ExpiryStatus {
        val days = calculateDaysRemaining(expiryDateStr)
        return when {
            days < 0 -> ExpiryStatus.EXPIRED
            days <= thresholdDays -> ExpiryStatus.EXPIRING_SOON
            else -> ExpiryStatus.SAFE
        }
    }

    /**
     * نص عربي وصفي للصلاحية
     */
    fun getRelativeTimeAr(daysRemaining: Long): String {
        return when {
            daysRemaining < 0 -> "منتهي الصلاحية منذ ${-daysRemaining} يوماً"
            daysRemaining == 0L -> "ينتهي اليوم!"
            daysRemaining == 1L -> "ينتهي غداً"
            daysRemaining == 2L -> "ينتهي بعد يومين"
            daysRemaining in 3..10 -> "متبقي $daysRemaining أيام"
            daysRemaining in 11..30 -> "متبقي $daysRemaining يوماً"
            daysRemaining in 31..60 -> "متبقي قرابة شهرين (${daysRemaining} يوماً)"
            else -> "متبقي ${daysRemaining / 30} شهراً (${daysRemaining} يوماً)"
        }
    }

    /**
     * كتالوج الباركود الذكي للتعبئة التلقائية عند المسح
     */
    data class PresetProduct(
        val name: String,
        val category: ItemCategory,
        val defaultStorage: String,
        val shelfLifeMonths: Int,
        val isSensitive: Boolean = false
    )

    private val presetCatalog = mapOf(
        "6281001234567" to PresetProduct("باراسيتامول 500 ملغ (أقراص)", ItemCategory.MEDICINE, "صيدلية المنزل / خزانة الغرفة", 24, true),
        "6281007890123" to PresetProduct("أموكسيسيلين مضاد حيوي (شراب)", ItemCategory.MEDICINE, "رف الثلاجة الأوسط", 6, true),
        "6281031112223" to PresetProduct("حليب طازج كامل الدسم", ItemCategory.FOOD, "باب الثلاجة", 1),
        "6281044455566" to PresetProduct("زبادي يوناني طبيعي", ItemCategory.FOOD, "درج الألبان بالثلاجة", 1),
        "6281055566677" to PresetProduct("زيت زيتون بكر ممتاز", ItemCategory.FOOD, "مخزن المطبخ السفلي", 18),
        "6281066677788" to PresetProduct("معقم يدين طبي كحولي 70%", ItemCategory.CONSUMABLES, "خزانة المستلزمات الطبية", 36),
        "6281077788899" to PresetProduct("سائل غسيل الأطباق", ItemCategory.CONSUMABLES, "تحت حوض المطبخ", 24),
        "8901030000000" to PresetProduct("أوميغا 3 كبسولات زيت السمك", ItemCategory.MEDICINE, "صيدلية المنزل", 18, true),
        "6287010001111" to PresetProduct("عصير برتقال طبيعي", ItemCategory.FOOD, "الثلاجة", 1),
        "6287020002222" to PresetProduct("مناديل مبللة معقمة", ItemCategory.CONSUMABLES, "خزانة الحمام", 12)
    )

    fun lookupBarcode(barcode: String): PresetProduct? {
        return presetCatalog[barcode.trim()]
    }

    fun getAllPresets(): Map<String, PresetProduct> = presetCatalog
}

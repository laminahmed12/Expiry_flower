package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import com.example.data.model.ItemCategory
import com.example.data.model.StoredItem
import java.io.InputStream

object ImageNotificationUtils {

    /**
     * تحميل وتحجيم صورة المنتج بأمان من مسار URI لتضمينها داخل الإشعار
     */
    fun loadBitmapFromUri(
        context: Context,
        uriString: String?,
        reqWidth: Int = 600,
        reqHeight: Int = 400
    ): Bitmap? {
        if (uriString.isNullOrBlank()) return null

        return try {
            val uri = Uri.parse(uriString)

            // قراءة أبعاد الصورة أولاً لحساب معامل التصغير وتفادي استهلاك الذاكرة
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false

            // فك التشفير بالحجم المطلوب
            context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            Log.e("ImageNotificationUtils", "Failed to load bitmap from uri: $uriString", e)
            null
        }
    }

    /**
     * توليد بطاقة بصرية أنيقة وعالية الدقة للمنتج في حال لم تكن هناك صورة ملتقطة،
     * لتظهر فوراً داخل شريط الإشعارات مع تفاصيل المنتج والمدة المتبقية
     */
    fun createFallbackProductBitmap(
        context: Context,
        item: StoredItem,
        width: Int = 640,
        height: Int = 360
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val daysRemaining = ExpiryUtils.calculateDaysRemaining(item.expiryDate)
        val isExpired = daysRemaining < 0
        val isMedicine = item.category == ItemCategory.MEDICINE.code || item.isSensitive

        // التدرج اللوني للخلفية بحسب طبيعة المنتج وحالته
        val (startColor, endColor) = when {
            isExpired -> Color.rgb(220, 38, 38) to Color.rgb(153, 27, 27) // أحمر منتهي
            daysRemaining <= 3 -> Color.rgb(217, 119, 6) to Color.rgb(180, 83, 9) // برتقالي حرج
            daysRemaining <= 7 -> Color.rgb(202, 138, 4) to Color.rgb(161, 98, 7) // عنبري تحذيري
            isMedicine -> Color.rgb(124, 58, 237) to Color.rgb(91, 33, 182) // بنفسجي للأدوية
            else -> Color.rgb(16, 185, 129) to Color.rgb(5, 150, 105) // أخضر للأغذية
        }

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                startColor, endColor, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // رسم بطاقة داخلية نصف شفافة
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 0, 0, 0)
        }
        val cardRect = RectF(24f, 24f, width - 24f, height - 24f)
        canvas.drawRoundRect(cardRect, 20f, 20f, cardPaint)

        // شارة الفئة (أدوية / أغذية / مستهلكات)
        val categoryText = when (ItemCategory.fromCode(item.category)) {
            ItemCategory.MEDICINE -> "💊 دواء وصيدلية"
            ItemCategory.FOOD -> "🍎 أغذية ومشروبات"
            ItemCategory.CONSUMABLES -> "🧼 مستهلكات ومنظفات"
        }
        val categoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(categoryText, 48f, 75f, categoryPaint)

        // اسم المنتج
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val displayTitle = if (item.name.length > 25) item.name.take(24) + "..." else item.name
        canvas.drawText(displayTitle, 48f, 150f, titlePaint)

        // تاريخ الصلاحية
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("تاريخ الانتهاء: ${item.expiryDate}", 48f, 210f, datePaint)

        // مكان التخزين والكمية
        val detailsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 22f
        }
        val locationText = if (item.storageLocation.isNotBlank()) "الموقع: ${item.storageLocation} | " else ""
        canvas.drawText("${locationText}الكمية: ${item.quantity}", 48f, 260f, detailsPaint)

        // شارة الحالة السفلية (منتهي / اقترب الانتهاء)
        val statusBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        val badgeRect = RectF(48f, 285f, width - 48f, 335f)
        canvas.drawRoundRect(badgeRect, 12f, 12f, statusBadgePaint)

        val statusTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = startColor
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val statusMsg = when {
            isExpired -> "⚠️ منتهي الصلاحية منذ ${-daysRemaining} يوماً - يرجى عدم الاستخدام!"
            daysRemaining == 0L -> "🚨 تنتهي صلاحيته اليوم!"
            daysRemaining <= 7 -> "⏳ تحذير: متبقي فقط $daysRemaining أيام على الانتهاء!"
            else -> "⏰ متبقي $daysRemaining يوماً على موعد الانتهاء"
        }
        canvas.drawText(statusMsg, 68f, 320f, statusTextPaint)

        return bitmap
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

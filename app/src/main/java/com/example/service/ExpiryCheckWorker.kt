package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.model.ItemCategory
import com.example.data.model.StoredItem
import com.example.util.ExpiryStatus
import com.example.util.ExpiryUtils
import com.example.util.ImageNotificationUtils
import java.util.concurrent.TimeUnit

class ExpiryCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME_PERIODIC = "periodic_expiry_check_work"
        const val WORK_NAME_ONCE = "immediate_expiry_check_work"
        const val CHANNEL_ID = "expiry_alerts_channel"
        const val CHANNEL_NAME = "تنبيهات اقتراب الصلاحية"
        const val NOTIFICATION_ID = 2026

        /**
         * جدولة خدمة الفحص الدوري بالخلفية كل 12 ساعة
         */
        fun schedulePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(
                12, TimeUnit.HOURS,
                1, TimeUnit.HOURS // Flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.d("ExpiryCheckWorker", "Periodic WorkManager scheduled successfully")
        }

        /**
         * تشغيل فحص فوري بالخلفية (مفيد للاختبار والتحديث الفوري)
         */
        fun runOnceNow(context: Context) {
            val oneTimeRequest = OneTimeWorkRequestBuilder<ExpiryCheckWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONCE,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }

        /**
         * إنشاء قناة الإشعارات
         */
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "إشعارات مرئية وصوتية لمتابعة المواد والأدوية التي اقترب موعد انتهائها مع صور المنتجات"
                    enableLights(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350, 200, 350)
                    setSound(soundUri, audioAttributes)
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        /**
         * إرسال إشعار فوري لمادة محددة مع صورتها مباشرة داخل شريط الإشعارات
         */
        fun sendProductNotification(context: Context, item: StoredItem) {
            createNotificationChannel(context)
            val days = ExpiryUtils.calculateDaysRemaining(item.expiryDate)

            // تحميل صورة المنتج أو توليد بطاقة بصرية واضحة
            val imageBitmap: Bitmap = ImageNotificationUtils.loadBitmapFromUri(context, item.imageUri)
                ?: ImageNotificationUtils.createFallbackProductBitmap(context, item)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (item.id % 10000).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val isExpired = days < 0
            val isMedicine = item.category == ItemCategory.MEDICINE.code || item.isSensitive

            val title = when {
                isExpired -> "🚨 منتهي الصلاحية: ${item.name}"
                isMedicine -> "⚠️ دواء حساس: ${item.name} أوشك على الانتهاء!"
                days <= 3 -> "⏳ تنبيه حرج: متبقي $days أيام لانتهاء ${item.name}"
                else -> "⏰ تنبيه اقتراب الصلاحية: ${item.name}"
            }

            val contentText = buildString {
                append("تاريخ الانتهاء: ${item.expiryDate}")
                if (item.storageLocation.isNotBlank()) {
                    append(" | الموقع: ${item.storageLocation}")
                }
                append(" | الكمية: ${item.quantity}")
            }

            val summaryText = when {
                days < 0 -> "منتهي الصلاحية منذ ${-days} يوماً!"
                days == 0L -> "ينتهي اليوم!"
                else -> "متبقي $days أيام"
            }

            val bigPictureStyle = NotificationCompat.BigPictureStyle()
                .bigPicture(imageBitmap)
                .bigLargeIcon(null as Bitmap?)
                .setBigContentTitle(title)
                .setSummaryText(summaryText)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(imageBitmap)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(bigPictureStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 350, 200, 350))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notificationManager = NotificationManagerCompat.from(context)
            try {
                val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                if (hasPermission && notificationManager.areNotificationsEnabled()) {
                    val notifId = (NOTIFICATION_ID + (item.id % 5000)).toInt()
                    notificationManager.notify(notifId, notification)
                }
            } catch (e: Exception) {
                Log.w("ExpiryCheckWorker", "Notification cannot be posted", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(context)
            val items = db.itemDao().getAllItemsSnapshot()

            val prefs = context.getSharedPreferences("expiry_guard_prefs", Context.MODE_PRIVATE)
            val thresholdDays = prefs.getInt("alert_threshold_days", 30)

            val expiredItems = mutableListOf<StoredItem>()
            val expiringSoonItems = mutableListOf<StoredItem>()
            val sensitiveMedicines = mutableListOf<StoredItem>()

            for (item in items) {
                val status = ExpiryUtils.getExpiryStatus(item.expiryDate, thresholdDays)

                if (status == ExpiryStatus.EXPIRED) {
                    expiredItems.add(item)
                } else if (status == ExpiryStatus.EXPIRING_SOON) {
                    expiringSoonItems.add(item)
                    if (item.category == ItemCategory.MEDICINE.code || item.isSensitive) {
                        sensitiveMedicines.add(item)
                    }
                }
            }

            val totalAlerts = expiredItems.size + expiringSoonItems.size
            if (totalAlerts > 0) {
                createNotificationChannel(context)

                // تحديد المادة الأكثر إلحاحاً لتضمين صورتها داخل الإشعار
                val primaryItem = sensitiveMedicines.firstOrNull()
                    ?: expiringSoonItems.minByOrNull { ExpiryUtils.calculateDaysRemaining(it.expiryDate) }
                    ?: expiredItems.first()

                sendNotificationWithImage(
                    expiredCount = expiredItems.size,
                    expiringSoonCount = expiringSoonItems.size,
                    sensitiveMedicines = sensitiveMedicines.map { it.name },
                    primaryItem = primaryItem
                )
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("ExpiryCheckWorker", "Error checking expiry dates in background", e)
            Result.retry()
        }
    }

    private fun sendNotificationWithImage(
        expiredCount: Int,
        expiringSoonCount: Int,
        sensitiveMedicines: List<String>,
        primaryItem: StoredItem
    ) {
        val days = ExpiryUtils.calculateDaysRemaining(primaryItem.expiryDate)

        // تحميل صورة المادة أو توليد بطاقة بصرية احترافية
        val imageBitmap: Bitmap = ImageNotificationUtils.loadBitmapFromUri(context, primaryItem.imageUri)
            ?: ImageNotificationUtils.createFallbackProductBitmap(context, primaryItem)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val title = when {
            sensitiveMedicines.isNotEmpty() -> "⚠️ إنذار طبي: ${primaryItem.name} قارب على الانتهاء!"
            expiredCount > 0 && days < 0 -> "🚨 منتهي الصلاحية: ${primaryItem.name}"
            days <= 3 -> "⏳ تنبيه عاجل: ${primaryItem.name} (متبقي $days أيام)"
            else -> "⏰ تنبيه اقتراب الصلاحية: ${primaryItem.name}"
        }

        val contentText = buildString {
            append("الموقع: ${if (primaryItem.storageLocation.isNotBlank()) primaryItem.storageLocation else "المخزن"}")
            append(" | الكمية: ${primaryItem.quantity}")
            if (expiringSoonCount + expiredCount > 1) {
                append(" (+${expiringSoonCount + expiredCount - 1} مواد أخرى)")
            }
        }

        val summaryText = when {
            days < 0 -> "منتهي الصلاحية منذ ${-days} يوماً!"
            days == 0L -> "ينتهي اليوم!"
            else -> "متبقي $days أيام"
        }

        val bigPictureStyle = NotificationCompat.BigPictureStyle()
            .bigPicture(imageBitmap)
            .bigLargeIcon(null as Bitmap?)
            .setBigContentTitle(title)
            .setSummaryText(summaryText)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(imageBitmap)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(bigPictureStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 350, 200, 350))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (hasPermission && notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            }
        } catch (e: Exception) {
            Log.w("ExpiryCheckWorker", "Cannot post notification", e)
        }
    }
}

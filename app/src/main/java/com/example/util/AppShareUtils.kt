package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * مشاركة نسخة التطبيق المثبتة نفسها عبر Android Sharesheet.
 *
 * يسمح للمستخدم بإرسال ملف APK مباشرة عبر WhatsApp أو البريد أو أي تطبيق
 * مشاركة يدعم الملفات، بدون الحاجة إلى Google Play.
 */
object AppShareUtils {

    private const val TAG = "AppShareUtils"
    private const val APK_FILE_NAME = "ADREEMK-ExpiryGuard.apk"

    fun shareInstalledApk(context: Context): Boolean {
        return try {
            val sourceApk = File(context.applicationInfo.sourceDir)
            if (!sourceApk.exists() || !sourceApk.isFile) {
                Log.e(TAG, "Installed APK source not found: " + sourceApk.absolutePath)
                return false
            }

            val shareDir = File(context.cacheDir, "shared_apk").apply { mkdirs() }
            val shareFile = File(shareDir, APK_FILE_NAME)

            FileInputStream(sourceApk).use { input ->
                FileOutputStream(shareFile).use { output ->
                    input.copyTo(output)
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                shareFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, "تطبيق صلاحيات المواد - ADREEMK")
                putExtra(Intent.EXTRA_TEXT, "تطبيق صلاحيات المواد من ADREEMK. يمكنك إرسال ملف APK وتثبيته على جهاز Android آخر.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "إرسال تطبيق صلاحيات المواد")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share installed APK", e)
            false
        }
    }
}

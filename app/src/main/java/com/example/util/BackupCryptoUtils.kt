package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCryptoUtils {

    private const val FORMAT_HEADER = "EXPIRY_GUARD_ENCRYPTED_BACKUP"
    private const val VERSION = 1
    private const val DEFAULT_SECRET_PASSPHRASE = "Adreemk@ExpiryGuard_SecureMasterKey#2026"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val SALT_LENGTH = 16
    private const val ITERATION_COUNT = 10000
    private const val KEY_LENGTH = 256

    /**
     * تشفير نص JSON احتياطي باستخدام خوارزمية AES-256-GCM
     */
    fun encryptBackup(
        plainJson: String,
        userPassword: String? = null,
        itemCount: Int = 0
    ): String {
        val passphrase = if (!userPassword.isNullOrBlank()) userPassword else DEFAULT_SECRET_PASSPHRASE
        val hasCustomPassword = !userPassword.isNullOrBlank()

        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH).also { random.nextBytes(it) }

        // اشتقاق مفتاح التشفير من كلمة المرور عبر PBKDF2
        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val plaintextBytes = plainJson.toByteArray(StandardCharsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintextBytes)

        val checksum = sha256Hex(plainJson)
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

        val wrapper = JSONObject().apply {
            put("format", FORMAT_HEADER)
            put("version", VERSION)
            put("encrypted", true)
            put("hasUserPassword", hasCustomPassword)
            put("algorithm", "AES-256-GCM")
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            put("checksum", checksum)
            put("createdAt", timestamp)
            put("itemCount", itemCount)
        }

        return wrapper.toString(2)
    }

    /**
     * فك تشفير وفحص سلامة ملف النسخة الاحتياطية
     */
    fun decryptBackup(
        rawContent: String,
        passwordInput: String? = null
    ): DecryptResult {
        return try {
            val trimmed = rawContent.trim()

            // التحقق مما إذا كان الملف بصيغة JSON عادية غير مشفرة (توافقية سابقة)
            if (!trimmed.startsWith("{") || !trimmed.contains("\"$FORMAT_HEADER\"")) {
                if (trimmed.startsWith("{") && trimmed.contains("\"items\"")) {
                    return DecryptResult.Success(trimmed, isEncrypted = false, hasUserPassword = false)
                }
                return DecryptResult.Error("صيغة الملف غير مدعومة أو غير صالحة.")
            }

            val json = JSONObject(trimmed)
            val isEncrypted = json.optBoolean("encrypted", false)
            val hasUserPassword = json.optBoolean("hasUserPassword", false)

            if (!isEncrypted) {
                val plain = json.optString("payload", trimmed)
                return DecryptResult.Success(plain, isEncrypted = false, hasUserPassword = false)
            }

            // فحص كلمة المرور المطلوبة
            val passphrase = if (hasUserPassword) {
                if (passwordInput.isNullOrBlank()) {
                    return DecryptResult.RequiresPassword
                }
                passwordInput
            } else {
                DEFAULT_SECRET_PASSPHRASE
            }

            val salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
            val ciphertext = Base64.decode(json.getString("ciphertext"), Base64.NO_WRAP)
            val expectedChecksum = json.optString("checksum", "")

            val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            val decryptedPlain = String(decryptedBytes, StandardCharsets.UTF_8)

            // التحقق من سلامة البيانات
            if (expectedChecksum.isNotEmpty()) {
                val actualChecksum = sha256Hex(decryptedPlain)
                if (actualChecksum != expectedChecksum) {
                    return DecryptResult.Error("فشل التحقق من تكامل البيانات (تطابق الشفرة غير سليم).")
                }
            }

            DecryptResult.Success(decryptedPlain, isEncrypted = true, hasUserPassword = hasUserPassword)
        } catch (e: javax.crypto.AEADBadTagException) {
            DecryptResult.Error("كلمة المرور غير صحيحة أو تم التلاعب بملف النسخة الاحتياطية.")
        } catch (e: Exception) {
            Log.e("BackupCryptoUtils", "Error during decryption", e)
            DecryptResult.Error("فشل في فك تشفير النسخة الاحتياطية: ${e.localizedMessage}")
        }
    }

    /**
     * حفظ النسخة المشفرة في وحدة تخزين الجهاز عبر Storage Access Framework
     */
    fun writeToStorageUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { os: OutputStream ->
                os.write(content.toByteArray(StandardCharsets.UTF_8))
                os.flush()
            }
            true
        } catch (e: Exception) {
            Log.e("BackupCryptoUtils", "Error writing to URI", e)
            false
        }
    }

    /**
     * قراءة النسخة المشفرة من وحدة تخزين الجهاز
     */
    fun readFromStorageUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
                inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e("BackupCryptoUtils", "Error reading from URI", e)
            null
        }
    }

    /**
     * إرسال النسخة المشفرة عبر البريد الإلكتروني أو تطبيقات المشاركة
     */
    fun shareViaEmail(
        context: Context,
        encryptedContent: String,
        itemCount: Int,
        isPasswordProtected: Boolean
    ): Boolean {
        return try {
            val fileName = "ExpiryBackup_Encrypted_${System.currentTimeMillis()}.json"
            val cacheFile = File(context.cacheDir, fileName)
            FileOutputStream(cacheFile).use { fos ->
                fos.write(encryptedContent.toByteArray(StandardCharsets.UTF_8))
            }

            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            val subject = "نسخة احتياطية مشفرة - تطبيق تنبيه الصلاحية ($itemCount مادة)"
            val body = buildString {
                append("مرحباً،\n\n")
                append("مرفق بهذا البريد ملف النسخة الاحتياطية المشفرة لبيانات المخزون وتواريخ الصلاحية من تطبيق تنبيه الصلاحية.\n")
                append("• إجمالي المواد: $itemCount مادة\n")
                append("• تاريخ التصدير: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())}\n")
                append("• حالة التشفير: مشفر بخوارزمية AES-256-GCM الآمنة\n")
                if (isPasswordProtected) {
                    append("• ملاحظة أمنية: هذا الملف محمي بكلمة المرور الخاصة بك، ستحتاج لإدخالها عند الاستيراد.\n")
                } else {
                    append("• التشفير: مشفر بالمفتاح الآمن للتطبيق (سيتم فك التشفير تلقائياً عند الاستيراد في التطبيق).\n")
                }
                append("\nللاستراد: افتح تطبيق تنبيه الصلاحية، انتقل إلى الإعدادات > استيراد، ثم اختر هذا الملف المرفق.")
            }

            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(emailIntent, "إرسال النسخة الاحتياطية المشفرة عبر البريد")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e("BackupCryptoUtils", "Error sharing via email", e)
            false
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    sealed class DecryptResult {
        data class Success(val plainJson: String, val isEncrypted: Boolean, val hasUserPassword: Boolean) : DecryptResult()
        object RequiresPassword : DecryptResult()
        data class Error(val message: String) : DecryptResult()
    }
}

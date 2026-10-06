package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.AppDatabase
import com.example.data.model.ActivationCode
import com.example.data.model.ItemCategory
import com.example.data.model.LicenseStatus
import com.example.data.model.StoredItem
import com.example.service.LicensingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import com.example.util.LicenseDisplayUtils
import java.util.Date
import java.util.Locale
import java.util.UUID

class ItemRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val itemDao = db.itemDao()
    private val codeDao = db.activationCodeDao()
    private val licensingApi = LicensingApi(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("expiry_guard_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_IS_ACTIVATED = "is_activated"
        private const val PREF_ACTIVE_CODE = "active_code"
        private const val PREF_DURATION_MONTHS = "duration_months"
        private const val PREF_ACTIVATION_TIMESTAMP = "activation_timestamp"
        private const val PREF_ALERT_DAYS = "alert_threshold_days"
        private const val PREF_THEME_MODE = "theme_mode" // "SYSTEM", "LIGHT", "DARK"
        private const val PREF_FIRST_RUN = "first_run_completed"
        private const val PREF_LAST_LICENSE_CHECK = "last_license_check"
        private const val PREF_TRIAL_START = "trial_start_timestamp"
        private const val TRIAL_DURATION_DAYS = 10

        val SECRET_ADMIN_PASSCODE = intArrayOf(49, 49, 54, 57, 51, 54).map { it.toChar() }.joinToString("")
    }

    // --- Items Flow & Operations ---
    val allItems: Flow<List<StoredItem>> = itemDao.getAllItems()
    val allActivationCodes: Flow<List<ActivationCode>> = codeDao.getAllCodes()

    fun getItemsByCategory(category: String): Flow<List<StoredItem>> =
        itemDao.getItemsByCategory(category)

    suspend fun getItemById(id: Long): StoredItem? = withContext(Dispatchers.IO) {
        itemDao.getItemById(id)
    }

    suspend fun getItemByBarcode(barcode: String): StoredItem? = withContext(Dispatchers.IO) {
        itemDao.getItemByBarcode(barcode)
    }

    suspend fun saveItem(item: StoredItem): Long = withContext(Dispatchers.IO) {
        if (item.id == 0L) {
            itemDao.insertItem(item)
        } else {
            itemDao.updateItem(item)
            item.id
        }
    }

    suspend fun deleteItem(item: StoredItem) = withContext(Dispatchers.IO) {
        itemDao.deleteItem(item)
    }

    suspend fun deleteItemById(id: Long) = withContext(Dispatchers.IO) {
        itemDao.deleteItemById(id)
    }

    // --- Alert Settings Preferences ---
    fun getAlertThresholdDays(): Int {
        return prefs.getInt(PREF_ALERT_DAYS, 30) // افتراضياً قبل 30 يوماً
    }

    fun setAlertThresholdDays(days: Int) {
        prefs.edit().putInt(PREF_ALERT_DAYS, days).apply()
    }

    fun getThemeMode(): String {
        return prefs.getString(PREF_THEME_MODE, "SYSTEM") ?: "SYSTEM"
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(PREF_THEME_MODE, mode).apply()
    }

    // --- Cloud licensing --- 
    suspend fun getLicenseStatus(): LicenseStatus = withContext(Dispatchers.IO) {
        val cachedCode = prefs.getString(PREF_ACTIVE_CODE, null)
        val cachedMonths = prefs.getInt(PREF_DURATION_MONTHS, 0)
        val cachedActivated = prefs.getBoolean(PREF_IS_ACTIVATED, false)
        val cloud = licensingApi.check().getOrNull()

        if (cloud != null) {
            prefs.edit().putLong(PREF_LAST_LICENSE_CHECK, System.currentTimeMillis()).apply()
            val type = when (cloud.plan) {
                "6_months" -> "ترخيص 6 أشهر"
                "1_year" -> "ترخيص سنة"
                else -> "ترخيص دائم"
            }
            LicenseStatus(
                isTrialActive = false,
                trialDaysRemaining = 0,
                isLicensed = true,
                licenseType = type,
                licenseExpiryFormatted = if (cloud.permanent) "صالح مدى الحياة" else LicenseDisplayUtils.formatExpiryDate(cloud.expiresAt),
                isAccessAllowed = true,
                activeCode = cachedCode
            )
        } else if (cachedActivated && cachedLicenseStillValid(cachedMonths, prefs.getLong(PREF_ACTIVATION_TIMESTAMP, 0L))) {
            val type = when (cachedMonths) {
                6 -> "ترخيص 6 أشهر"
                12 -> "ترخيص سنة"
                -1 -> "ترخيص دائم"
                else -> "ترخيص مفعل"
            }
            LicenseStatus(
                isTrialActive = false,
                trialDaysRemaining = 0,
                isLicensed = true,
                licenseType = type,
                licenseExpiryFormatted = if (cachedMonths == -1) "صالح مدى الحياة" else formatCachedExpiryDate(cachedMonths, prefs.getLong(PREF_ACTIVATION_TIMESTAMP, 0L)),
                isAccessAllowed = true,
                activeCode = cachedCode
            )
        } else if (cachedCode.isNullOrBlank()) {
            // جهاز جديد بلا ترخيص: منح فترة تجريبية مجانية لمدة 10 أيام.
            // يبدأ العداد مرة واحدة فقط ولا يُعاد عند كل تشغيل للتطبيق.
            val now = System.currentTimeMillis()
            val trialStart = prefs.getLong(PREF_TRIAL_START, 0L).let { saved ->
                if (saved > 0L) saved else {
                    prefs.edit().putLong(PREF_TRIAL_START, now).apply()
                    now
                }
            }
            val trialEnd = trialStart + TRIAL_DURATION_DAYS * 24L * 60L * 60L * 1000L
            val remainingMs = trialEnd - now
            val remainingDays = kotlin.math.ceil(remainingMs / (24.0 * 60L * 60L * 1000L)).toInt()
            if (remainingDays > 0) {
                LicenseStatus(
                    isTrialActive = true,
                    trialDaysRemaining = remainingDays,
                    isLicensed = false,
                    licenseType = "فترة تجريبية",
                    licenseExpiryFormatted = null,
                    isAccessAllowed = true
                )
            } else {
                LicenseStatus(
                    isTrialActive = false,
                    trialDaysRemaining = 0,
                    isLicensed = false,
                    licenseType = "انتهت الفترة التجريبية",
                    licenseExpiryFormatted = null,
                    isAccessAllowed = false
                )
            }
        } else {
            // يوجد ترخيص سابق محلياً لكنه غير صالح/منتهي/مسحوب؛ لا نعيد منح التجربة.
            LicenseStatus(
                isTrialActive = false,
                trialDaysRemaining = 0,
                isLicensed = false,
                licenseType = "غير مفعل",
                licenseExpiryFormatted = null,
                isAccessAllowed = false
            )
        }
    }

    suspend fun activateWithCode(inputCode: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmed = inputCode.trim().uppercase()
        val result = licensingApi.activate(trimmed)
        val cloud = result.getOrNull()

        if (cloud == null) {
            return@withContext Pair(false, result.exceptionOrNull()?.message ?: "تعذر تفعيل الترخيص.")
        }

        val months = when (cloud.plan) {
            "6_months" -> 6
            "1_year" -> 12
            else -> -1
        }
        prefs.edit().putLong(PREF_LAST_LICENSE_CHECK, System.currentTimeMillis()).apply()

        val local = ActivationCode(
            code = trimmed,
            durationMonths = months,
            createdAt = System.currentTimeMillis(),
            isRevoked = false,
            isActivated = true,
            activatedAt = System.currentTimeMillis()
        )
        codeDao.insertCode(local)
        applyActivation(local)

        val title = local.durationTitleAr
        Pair(true, "تم تفعيل الترخيص بنجاح ($title)")
    }

    private fun formatCachedExpiryDate(durationMonths: Int, activatedAt: Long): String {
        if (durationMonths == -1) return "صالح مدى الحياة"
        if (durationMonths <= 0 || activatedAt <= 0L) return "غير متاح"

        val calendar = Calendar.getInstance().apply {
            timeInMillis = activatedAt
            add(Calendar.MONTH, durationMonths)
        }
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return formatter.format(Date(calendar.timeInMillis))
    }

    private fun cachedLicenseStillValid(durationMonths: Int, activatedAt: Long): Boolean {
        if (durationMonths == -1) return true
        if (durationMonths <= 0 || activatedAt <= 0L) return false
        val lastCheck = prefs.getLong(PREF_LAST_LICENSE_CHECK, 0L)
        val graceMs = 72L * 60L * 60L * 1000L
        if (lastCheck <= 0L || System.currentTimeMillis() - lastCheck > graceMs) return false
        val calendar = Calendar.getInstance().apply { timeInMillis = activatedAt }
        calendar.add(Calendar.MONTH, durationMonths)
        return System.currentTimeMillis() < calendar.timeInMillis
    }

    private fun applyActivation(code: ActivationCode) {
        prefs.edit()
            .putBoolean(PREF_IS_ACTIVATED, true)
            .putString(PREF_ACTIVE_CODE, code.code)
            .putInt(PREF_DURATION_MONTHS, code.durationMonths)
            .putLong(PREF_ACTIVATION_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    // --- Adreemk Hidden Admin Panel Methods ---
    suspend fun generateActivationCode(
        durationMonths: Int,
        note: String = "",
        ownerPin: String
    ): ActivationCode = withContext(Dispatchers.IO) {
        val plan = when (durationMonths) {
            6 -> "6_months"
            12 -> "1_year"
            -1 -> "permanent"
            else -> throw IllegalArgumentException("مدة الترخيص غير مدعومة.")
        }

        val created = licensingApi.create(
            customerName = note.ifBlank { "عميل" },
            plan = plan,
            ownerPin = ownerPin
        ).getOrElse { throw IllegalStateException(it.message ?: "تعذر إنشاء الترخيص من الخادم.") }

        val activationCode = ActivationCode(
            code = created.code,
            durationMonths = durationMonths,
            createdAt = System.currentTimeMillis(),
            isRevoked = false,
            isActivated = false,
            note = note
        )
        codeDao.insertCode(activationCode)
        activationCode
    }

    suspend fun toggleRevokeCode(code: String, shouldRevoke: Boolean) = withContext(Dispatchers.IO) {
        if (shouldRevoke) {
            codeDao.revokeCode(code)
            if (prefs.getString(PREF_ACTIVE_CODE, null) == code) {
                prefs.edit().putBoolean(PREF_IS_ACTIVATED, false).apply()
            }
        } else {
            codeDao.activateCode(code)
        }
    }

    suspend fun resetTrialPeriod() = withContext(Dispatchers.IO) {
        prefs.edit()
            .putBoolean(PREF_IS_ACTIVATED, false)
            .remove(PREF_ACTIVE_CODE)
            .apply()
    }

    // --- Backup & Restore (JSON) ---
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "ExpiryGuard")
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        val items = itemDao.getAllItemsSnapshot()
        val itemsArray = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("category", item.category)
            obj.put("expiryDate", item.expiryDate)
            obj.put("quantity", item.quantity)
            obj.put("storageLocation", item.storageLocation)
            obj.put("imageUri", item.imageUri ?: "")
            obj.put("barcode", item.barcode ?: "")
            obj.put("notes", item.notes)
            obj.put("isSensitive", item.isSensitive)
            obj.put("isConsumed", item.isConsumed)
            obj.put("createdAt", item.createdAt)
            itemsArray.put(obj)
        }
        root.put("items", itemsArray)

        val codes = codeDao.getAllCodesSnapshot()
        val codesArray = JSONArray()
        for (code in codes) {
            val obj = JSONObject()
            obj.put("code", code.code)
            obj.put("durationMonths", code.durationMonths)
            obj.put("createdAt", code.createdAt)
            obj.put("isRevoked", code.isRevoked)
            obj.put("isActivated", code.isActivated)
            obj.put("activatedAt", code.activatedAt ?: 0L)
            codesArray.put(obj)
        }
        root.put("codes", codesArray)
        root.put("alertDays", getAlertThresholdDays())

        root.toString(2)
    }

    suspend fun importBackupJson(jsonString: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val root = JSONObject(jsonString)
                if (!root.has("items")) {
                    return@withContext Pair(false, "ملف النسخ الاحتياطي غير صالح، يفتقر إلى بيانات المواد.")
                }

                val itemsArray = root.getJSONArray("items")
                val restoredItems = mutableListOf<StoredItem>()
                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.getJSONObject(i)
                    val item = StoredItem(
                        id = obj.optLong("id", 0L),
                        name = obj.getString("name"),
                        category = obj.getString("category"),
                        expiryDate = obj.getString("expiryDate"),
                        quantity = obj.optInt("quantity", 1),
                        storageLocation = obj.optString("storageLocation", ""),
                        imageUri = obj.optString("imageUri", null).takeIf { !it.isNullOrBlank() },
                        barcode = obj.optString("barcode", null).takeIf { !it.isNullOrBlank() },
                        notes = obj.optString("notes", ""),
                        isSensitive = obj.optBoolean("isSensitive", false),
                        isConsumed = obj.optBoolean("isConsumed", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                    restoredItems.add(item)
                }

                if (restoredItems.isNotEmpty()) {
                    itemDao.insertAll(restoredItems)
                }

                if (root.has("codes")) {
                    val codesArray = root.getJSONArray("codes")
                    val restoredCodes = mutableListOf<ActivationCode>()
                    for (i in 0 until codesArray.length()) {
                        val obj = codesArray.getJSONObject(i)
                        val code = ActivationCode(
                            code = obj.getString("code"),
                            durationMonths = obj.getInt("durationMonths"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            isRevoked = obj.optBoolean("isRevoked", false),
                            isActivated = obj.optBoolean("isActivated", false),
                            activatedAt = obj.optLong("activatedAt", 0L).takeIf { it > 0 }
                        )
                        restoredCodes.add(code)
                    }
                    if (restoredCodes.isNotEmpty()) {
                        codeDao.insertAll(restoredCodes)
                    }
                }

                if (root.has("alertDays")) {
                    setAlertThresholdDays(root.getInt("alertDays"))
                }

                Pair(true, "تم استعادة ${restoredItems.size} مادة بنجاح!")
            } catch (e: Exception) {
                Pair(false, "حدث خطأ أثناء قراءة ملف النسخ الاحتياطي: ${e.message}")
            }
        }

    suspend fun exportEncryptedBackupJson(userPassword: String? = null): String = withContext(Dispatchers.IO) {
        val plainJson = exportBackupJson()
        val itemsCount = itemDao.getAllItemsSnapshot().size
        com.example.util.BackupCryptoUtils.encryptBackup(plainJson, userPassword, itemsCount)
    }

    suspend fun importEncryptedBackupJson(
        rawContent: String,
        passwordInput: String? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        when (val result = com.example.util.BackupCryptoUtils.decryptBackup(rawContent, passwordInput)) {
            is com.example.util.BackupCryptoUtils.DecryptResult.RequiresPassword -> {
                Pair(false, "REQUIRES_PASSWORD")
            }
            is com.example.util.BackupCryptoUtils.DecryptResult.Error -> {
                Pair(false, result.message)
            }
            is com.example.util.BackupCryptoUtils.DecryptResult.Success -> {
                importBackupJson(result.plainJson)
            }
        }
    }

    // --- Clean and purge all dummy / sample data ---
    suspend fun populateInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(PREF_FIRST_RUN, true).apply()
        purgeAllSampleData()
    }

    suspend fun purgeAllSampleData() = withContext(Dispatchers.IO) {
        val sampleBarcodes = setOf(
            "6281001234567",
            "6281007890123",
            "6281031112223",
            "6281044455566",
            "6281055566677",
            "6281066677788",
            "6281077788899"
        )
        val allItems = itemDao.getAllItemsSnapshot()
        for (item in allItems) {
            if (item.barcode != null && sampleBarcodes.contains(item.barcode)) {
                itemDao.deleteItem(item)
            }
        }
    }
}

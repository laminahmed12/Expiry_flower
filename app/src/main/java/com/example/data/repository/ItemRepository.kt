package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.AppDatabase
import com.example.data.model.ActivationCode
import com.example.data.model.ItemCategory
import com.example.data.model.LicenseStatus
import com.example.data.model.StoredItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class ItemRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val itemDao = db.itemDao()
    private val codeDao = db.activationCodeDao()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("expiry_guard_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_TRIAL_START = "trial_start_timestamp"
        private const val PREF_IS_ACTIVATED = "is_activated"
        private const val PREF_ACTIVE_CODE = "active_code"
        private const val PREF_DURATION_MONTHS = "duration_months"
        private const val PREF_ACTIVATION_TIMESTAMP = "activation_timestamp"
        private const val PREF_ALERT_DAYS = "alert_threshold_days"
        private const val PREF_THEME_MODE = "theme_mode" // "SYSTEM", "LIGHT", "DARK"
        private const val PREF_FIRST_RUN = "first_run_completed"

        const val TRIAL_PERIOD_DAYS = 10
        val SECRET_ADMIN_PASSCODE = "116936"
    }

    init {
        // إذا كان أول تشغيل، سجل بداية الفترة التجريبية
        if (!prefs.contains(PREF_TRIAL_START)) {
            prefs.edit().putLong(PREF_TRIAL_START, System.currentTimeMillis()).apply()
        }
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

    // --- Licensing & 10-day Trial Logic ---
    suspend fun getLicenseStatus(): LicenseStatus = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val trialStart = prefs.getLong(PREF_TRIAL_START, now)
        val trialDurationMs = TRIAL_PERIOD_DAYS * 24L * 60 * 60 * 1000
        val trialTimeLeftMs = (trialStart + trialDurationMs) - now
        val trialDaysRemaining = if (trialTimeLeftMs > 0) {
            ((trialTimeLeftMs / (24L * 60 * 60 * 1000)) + 1).toInt()
        } else {
            0
        }
        val isTrialActive = trialDaysRemaining > 0

        val isActivated = prefs.getBoolean(PREF_IS_ACTIVATED, false)
        val activeCode = prefs.getString(PREF_ACTIVE_CODE, null)
        val durationMonths = prefs.getInt(PREF_DURATION_MONTHS, 0)
        val activationTime = prefs.getLong(PREF_ACTIVATION_TIMESTAMP, 0L)

        // التحقق من قاعدة البيانات هل تم إلغاء الكود من لوحة تحكم Adreemk
        var isRevoked = false
        if (isActivated && activeCode != null) {
            val codeEntity = codeDao.getCode(activeCode)
            if (codeEntity != null && codeEntity.isRevoked) {
                isRevoked = true
            }
        }

        if (isActivated && !isRevoked) {
            if (durationMonths == -1) {
                // ترخيص دائم
                return@withContext LicenseStatus(
                    isTrialActive = false,
                    trialDaysRemaining = 0,
                    isLicensed = true,
                    licenseType = "ترخيص دائم (غير محدود)",
                    licenseExpiryFormatted = "صالح مدى الحياة",
                    isAccessAllowed = true,
                    activeCode = activeCode
                )
            } else {
                // حساب تاريخ انتهاء الترخيص (6 أشهر أو سنة)
                val cal = Calendar.getInstance()
                cal.timeInMillis = activationTime
                cal.add(Calendar.MONTH, durationMonths)
                val expiryTime = cal.timeInMillis

                val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                val expiryFormatted = sdf.format(Date(expiryTime))

                val isStillValid = now < expiryTime
                return@withContext LicenseStatus(
                    isTrialActive = false,
                    trialDaysRemaining = 0,
                    isLicensed = isStillValid,
                    licenseType = if (durationMonths == 6) "ترخيص 6 أشهر" else "ترخيص سنة واحدة",
                    licenseExpiryFormatted = expiryFormatted,
                    isAccessAllowed = isStillValid,
                    activeCode = activeCode
                )
            }
        }

        // في حال عدم التفعيل أو إلغاء الرمز: يعتمد على الفترة التجريبية
        LicenseStatus(
            isTrialActive = isTrialActive,
            trialDaysRemaining = trialDaysRemaining,
            isLicensed = false,
            licenseType = if (isTrialActive) "فترة تجريبية (10 أيام)" else "الفترة التجريبية منتهية",
            licenseExpiryFormatted = if (isTrialActive) "متبقي $trialDaysRemaining أيام تجريبية" else "انتهت فترة التجربة",
            isAccessAllowed = isTrialActive,
            activeCode = null
        )
    }

    suspend fun activateWithCode(inputCode: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmed = inputCode.trim().uppercase()
        val codeEntity = codeDao.getCode(trimmed)

        if (codeEntity == null) {
            // تحقق إذا كان الكود يتبع النمط المعتمد ADR-6M-XXXX أو ADR-1Y-XXXX أو ADR-PERM-XXXX
            val parsedDuration = when {
                trimmed.startsWith("ADR-6M-") -> 6
                trimmed.startsWith("ADR-1Y-") -> 12
                trimmed.startsWith("ADR-PERM-") -> -1
                trimmed == "ADREEMK-VIP-PERMANENT" -> -1
                else -> null
            }

            if (parsedDuration != null) {
                // حفظ الكود الصالح
                val newCode = ActivationCode(
                    code = trimmed,
                    durationMonths = parsedDuration,
                    isActivated = true,
                    activatedAt = System.currentTimeMillis()
                )
                codeDao.insertCode(newCode)
                applyActivation(newCode)
                return@withContext Pair(true, "تم تفعيل الترخيص بنجاح (${newCode.durationTitleAr})")
            }
            return@withContext Pair(false, "رمز التفعيل غير صالح، يرجى التأكد منه أو التواصل مع الإدارة.")
        }

        if (codeEntity.isRevoked) {
            return@withContext Pair(false, "تم إيقاف أو إلغاء صلاحية هذا الرمز من قبل الإدارة.")
        }

        codeDao.updateCode(
            codeEntity.copy(
                isActivated = true,
                activatedAt = System.currentTimeMillis()
            )
        )
        applyActivation(codeEntity)
        Pair(true, "تم تفعيل الترخيص بنجاح (${codeEntity.durationTitleAr})")
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
    suspend fun generateActivationCode(durationMonths: Int, note: String = ""): ActivationCode =
        withContext(Dispatchers.IO) {
            val prefix = when (durationMonths) {
                6 -> "ADR-6M-"
                12 -> "ADR-1Y-"
                -1 -> "ADR-PERM-"
                else -> "ADR-${durationMonths}M-"
            }
            val randomPart = UUID.randomUUID().toString().substring(0, 8).uppercase()
            val codeString = "$prefix$randomPart"

            val activationCode = ActivationCode(
                code = codeString,
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
            // إذا كان هذا هو الكود النشط حالياً، ألغِ التفعيل الفوري
            if (prefs.getString(PREF_ACTIVE_CODE, null) == code) {
                prefs.edit().putBoolean(PREF_IS_ACTIVATED, false).apply()
            }
        } else {
            codeDao.activateCode(code)
        }
    }

    suspend fun resetTrialPeriod() = withContext(Dispatchers.IO) {
        prefs.edit()
            .putLong(PREF_TRIAL_START, System.currentTimeMillis())
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
            obj.put("productionDate", item.productionDate)
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
                        productionDate = obj.getString("productionDate"),
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

    // --- Pre-populate Sample Data on first install ---
    suspend fun populateInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        if (!prefs.getBoolean(PREF_FIRST_RUN, false)) {
            val count = itemDao.getAllItemsSnapshot().size
            if (count == 0) {
                val todayCal = Calendar.getInstance()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

                // تواريخ مدروسة لعرض الحالات: منتهي، يوشك على الانتهاء، آمن
                val calExpired = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -5) }
                val calSoon1 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 3) }
                val calSoon2 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 12) }
                val calSafe1 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 45) }
                val calSafe2 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 180) }

                val sampleItems = listOf(
                    StoredItem(
                        name = "باراسيتامول 500 ملغ (أقراص)",
                        category = ItemCategory.MEDICINE.code,
                        productionDate = "2024-01-10",
                        expiryDate = sdf.format(calSoon1.time),
                        quantity = 2,
                        storageLocation = "صيدلية المنزل / خزانة الغرفة",
                        barcode = "6281001234567",
                        notes = "خافض حرارة ومسكن ألم، يحفظ في مكان جاف أقل من 25 درجة مئوية",
                        isSensitive = true
                    ),
                    StoredItem(
                        name = "أموكسيسيلين مضاد حيوي (شراب)",
                        category = ItemCategory.MEDICINE.code,
                        productionDate = "2024-03-01",
                        expiryDate = sdf.format(calExpired.time),
                        quantity = 1,
                        storageLocation = "رف الثلاجة الأوسط",
                        barcode = "6281007890123",
                        notes = "يحفظ بعد الحل في الثلاجة ولا يستخدم بعد انتهاء المدة إطلاقاً",
                        isSensitive = true
                    ),
                    StoredItem(
                        name = "حليب طازج كامل الدسم",
                        category = ItemCategory.FOOD.code,
                        productionDate = "2026-09-25",
                        expiryDate = sdf.format(calSoon1.time),
                        quantity = 3,
                        storageLocation = "باب الثلاجة",
                        barcode = "6281031112223",
                        notes = "يستهلك خلال 3 أيام من الفتح"
                    ),
                    StoredItem(
                        name = "زبادي يوناني طبيعي",
                        category = ItemCategory.FOOD.code,
                        productionDate = "2026-09-20",
                        expiryDate = sdf.format(calSoon2.time),
                        quantity = 4,
                        storageLocation = "درج الألبان بالثلاجة",
                        barcode = "6281044455566",
                        notes = "غني بالبروتين"
                    ),
                    StoredItem(
                        name = "زيت زيتون بكر ممتاز",
                        category = ItemCategory.FOOD.code,
                        productionDate = "2024-02-15",
                        expiryDate = sdf.format(calSafe2.time),
                        quantity = 2,
                        storageLocation = "مخزن المطبخ السفلي",
                        barcode = "6281055566677",
                        notes = "معصور على البارد"
                    ),
                    StoredItem(
                        name = "معقم يدين طبي كحولي 70%",
                        category = ItemCategory.CONSUMABLES.code,
                        productionDate = "2023-11-01",
                        expiryDate = sdf.format(calSafe1.time),
                        quantity = 5,
                        storageLocation = "خزانة المستلزمات الطبية",
                        barcode = "6281066677788",
                        notes = "للاستعمال الخارجي فقط"
                    ),
                    StoredItem(
                        name = "سائل غسيل الأطباق المضاد للبكتيريا",
                        category = ItemCategory.CONSUMABLES.code,
                        productionDate = "2024-01-05",
                        expiryDate = sdf.format(calSafe2.time),
                        quantity = 2,
                        storageLocation = "تحت حوض المطبخ",
                        barcode = "6281077788899",
                        notes = "عبوة اقتصادية 1 لتر"
                    )
                )

                itemDao.insertAll(sampleItems)

                // إنشاء بعض رموز التفعيل الأولية التجريبية للوحة Adreemk
                val initialCodes = listOf(
                    ActivationCode(
                        code = "ADR-6M-DEMO2026",
                        durationMonths = 6,
                        createdAt = System.currentTimeMillis(),
                        isRevoked = false,
                        note = "رمز تجريبي 6 أشهر"
                    ),
                    ActivationCode(
                        code = "ADR-1Y-PREMIUM",
                        durationMonths = 12,
                        createdAt = System.currentTimeMillis(),
                        isRevoked = false,
                        note = "رمز سنوي ذهبي"
                    ),
                    ActivationCode(
                        code = "ADR-PERM-VIP999",
                        durationMonths = -1,
                        createdAt = System.currentTimeMillis(),
                        isRevoked = false,
                        note = "ترخيص دائم مدى الحياة"
                    )
                )
                codeDao.insertAll(initialCodes)
            }
            prefs.edit().putBoolean(PREF_FIRST_RUN, true).apply()
        }
    }
}

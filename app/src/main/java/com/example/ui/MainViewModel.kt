package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActivationCode
import com.example.data.model.ItemCategory
import com.example.data.model.LicenseStatus
import com.example.data.model.StoredItem
import com.example.data.repository.ItemRepository
import com.example.service.AlertManager
import com.example.util.ExpiryStatus
import com.example.util.ExpiryUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.service.ExpiryCheckWorker

enum class Screen {
    HOME,
    ADD_EDIT,
    BARCODE_SCANNER,
    SETTINGS,
    ADREEMK_ADMIN
}

data class HomeUiState(
    val items: List<StoredItem> = emptyList(),
    val filteredItems: List<StoredItem> = emptyList(),
    val selectedCategory: String? = null, // null = الكل
    val searchQuery: String = "",
    val totalCount: Int = 0,
    val expiredCount: Int = 0,
    val expiringSoonCount: Int = 0,
    val safeCount: Int = 0,
    val medicineCount: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = ItemRepository(application)
    val alertManager = AlertManager(application)

    // Navigation state
    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Screen back stack
    private val screenStack = mutableListOf<Screen>()

    // Item being edited or created
    private val _editingItem = MutableStateFlow<StoredItem?>(null)
    val editingItem: StateFlow<StoredItem?> = _editingItem.asStateFlow()

    // Pre-filled barcode from scanner
    private val _scannedBarcode = MutableStateFlow<String?>(null)
    val scannedBarcode: StateFlow<String?> = _scannedBarcode.asStateFlow()

    // Theme mode: "SYSTEM", "LIGHT", "DARK"
    private val _themeMode = MutableStateFlow(repository.getThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Alert threshold in days (30, 20, 15)
    private val _alertThresholdDays = MutableStateFlow(repository.getAlertThresholdDays())
    val alertThresholdDays: StateFlow<Int> = _alertThresholdDays.asStateFlow()

    // License Status: Permanently licensed full version
    private val _licenseStatus = MutableStateFlow(
        LicenseStatus(
            isTrialActive = false,
            trialDaysRemaining = 0,
            isLicensed = true,
            licenseType = "نسخة كاملة معتمدة (ترخيص دائم)",
            licenseExpiryFormatted = "صالح مدى الحياة",
            isAccessAllowed = true
        )
    )
    val licenseStatus: StateFlow<LicenseStatus> = _licenseStatus.asStateFlow()

    // Secret Adreemk Logo Taps
    private var lastTapTime = 0L
    private val _logoTapCount = MutableStateFlow(0)
    val logoTapCount: StateFlow<Int> = _logoTapCount.asStateFlow()

    private val _showPasscodeDialog = MutableStateFlow(false)
    val showPasscodeDialog: StateFlow<Boolean> = _showPasscodeDialog.asStateFlow()

    // User Feedback Messages (Snackbar / Toast)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // Search and Category Filter
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Activation codes in admin panel
    val activationCodes: StateFlow<List<ActivationCode>> = repository.allActivationCodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined UI State for Home
    val homeUiState: StateFlow<HomeUiState> = combine(
        repository.allItems,
        _selectedCategory,
        _searchQuery,
        _alertThresholdDays
    ) { items, category, query, threshold ->
        var filtered = items

        if (!category.isNullOrBlank()) {
            filtered = filtered.filter { it.category == category }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            filtered = filtered.filter {
                it.name.lowercase().contains(q) ||
                it.storageLocation.lowercase().contains(q) ||
                (it.barcode?.contains(q) == true) ||
                it.notes.lowercase().contains(q)
            }
        }

        var expired = 0
        var expiringSoon = 0
        var safe = 0
        var medicine = 0

        for (item in items) {
            val status = ExpiryUtils.getExpiryStatus(item.expiryDate, threshold)
            when (status) {
                ExpiryStatus.EXPIRED -> expired++
                ExpiryStatus.EXPIRING_SOON -> expiringSoon++
                ExpiryStatus.SAFE -> safe++
            }
            if (item.category == ItemCategory.MEDICINE.code) {
                medicine++
            }
        }

        HomeUiState(
            items = items,
            filteredItems = filtered,
            selectedCategory = category,
            searchQuery = query,
            totalCount = items.size,
            expiredCount = expired,
            expiringSoonCount = expiringSoon,
            safeCount = safe,
            medicineCount = medicine
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        viewModelScope.launch {
            repository.populateInitialDataIfEmpty()
            refreshLicenseStatus()
        }
    }

    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            screenStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun handleBack(): Boolean {
        if (screenStack.isNotEmpty()) {
            _currentScreen.value = screenStack.removeAt(screenStack.size - 1)
            return true
        }
        return false
    }

    fun onLogoClicked() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 1500) {
            // انقضى وقت طويل بين النقرات، أعد العداد
            _logoTapCount.value = 1
        } else {
            _logoTapCount.value += 1
        }
        lastTapTime = now

        if (_logoTapCount.value >= 3) {
            _logoTapCount.value = 0
            _showPasscodeDialog.value = true
        }
    }

    fun dismissPasscodeDialog() {
        _showPasscodeDialog.value = false
    }

    fun verifyPasscodeAndOpenAdmin(enteredPasscode: String): Boolean {
        return if (enteredPasscode.trim() == ItemRepository.SECRET_ADMIN_PASSCODE) {
            _showPasscodeDialog.value = false
            navigateTo(Screen.ADREEMK_ADMIN)
            true
        } else {
            false
        }
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startAddItem() {
        _editingItem.value = null
        _scannedBarcode.value = null
        navigateTo(Screen.ADD_EDIT)
    }

    fun startEditItem(item: StoredItem) {
        _editingItem.value = item
        _scannedBarcode.value = item.barcode
        navigateTo(Screen.ADD_EDIT)
    }

    fun onBarcodeScanned(barcode: String) {
        _scannedBarcode.value = barcode
        // تحقق هل الباركود موجود مسبقاً في قاعدة البيانات لتعديله
        viewModelScope.launch {
            val existing = repository.getItemByBarcode(barcode)
            if (existing != null) {
                _editingItem.value = existing
                setMessage("تم العثور على منتج مسجل مسبقاً بهذا الباركود!")
            } else {
                _editingItem.value = null
            }
            navigateTo(Screen.ADD_EDIT)
        }
    }

    fun saveItem(item: StoredItem, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.saveItem(item)
            setMessage("تم حفظ المادة بنجاح في قاعدة البيانات!")
            onComplete()
        }
    }

    fun deleteItem(item: StoredItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
            setMessage("تم حذف المادة بنجاح.")
        }
    }

    fun setAlertThreshold(days: Int) {
        repository.setAlertThresholdDays(days)
        _alertThresholdDays.value = days
        setMessage("تم تحديث عتبة التنبيه: قبل $days يوماً")
    }

    fun setTheme(mode: String) {
        repository.setThemeMode(mode)
        _themeMode.value = mode
    }

    fun refreshLicenseStatus() {
        viewModelScope.launch {
            _licenseStatus.value = repository.getLicenseStatus()
        }
    }

    fun activateLicense(code: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (success, msg) = repository.activateWithCode(code)
            if (success) {
                refreshLicenseStatus()
            }
            onResult(success, msg)
        }
    }

    // --- Admin panel actions ---
    fun generateNewCode(months: Int, note: String, onGenerated: (ActivationCode) -> Unit) {
        viewModelScope.launch {
            val code = repository.generateActivationCode(months, note)
            onGenerated(code)
        }
    }

    fun toggleRevokeCode(code: String, shouldRevoke: Boolean) {
        viewModelScope.launch {
            repository.toggleRevokeCode(code, shouldRevoke)
            refreshLicenseStatus()
            setMessage(if (shouldRevoke) "تم إيقاف تفعيل الرمز فوراً" else "تم إعادة تنشيط الرمز")
        }
    }

    fun purgeSampleData() {
        viewModelScope.launch {
            repository.purgeAllSampleData()
            setMessage("تم حذف وإلغاء جميع البيانات والعمليات التجريبية بنجاح.")
        }
    }

    // --- Voice Alerts ---
    fun playItemVoiceAlert(item: StoredItem) {
        val days = ExpiryUtils.calculateDaysRemaining(item.expiryDate)
        alertManager.speakItemAlert(item, days)
    }

    fun playSummaryVoiceAlert() {
        val state = homeUiState.value
        alertManager.speakSummaryAlert(
            expiringCount = state.expiringSoonCount,
            expiredCount = state.expiredCount,
            medicineCount = state.medicineCount
        )
    }

    fun testVoiceAlert() {
        alertManager.speakTestAlert()
    }

    /**
     * إرسال إشعار فوري يحتوي على صورة المنتج الفعلي للتجربة والمعاينة
     */
    fun triggerTestProductNotification(context: Context) {
        viewModelScope.launch {
            val items = repository.allItems.firstOrNull() ?: emptyList()
            val candidateItem = items.firstOrNull {
                ExpiryUtils.calculateDaysRemaining(it.expiryDate) <= alertThresholdDays.value
            } ?: items.firstOrNull()

            if (candidateItem != null) {
                ExpiryCheckWorker.sendProductNotification(context, candidateItem)
                setMessage("تم إرسال إشعار فوري للمنتج: ${candidateItem.name}")
            } else {
                setMessage("لا توجد مواد مخزنة حالياً لإرسال إشعار بها. يرجى إضافة مادة أولاً.")
            }
        }
    }

    // --- Encrypted Backup & Restore Operations ---
    fun exportEncryptedBackup(password: String? = null, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val encryptedJson = repository.exportEncryptedBackupJson(password)
            onResult(encryptedJson)
        }
    }

    fun importEncryptedBackup(
        rawContent: String,
        password: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val (success, msg) = repository.importEncryptedBackupJson(rawContent, password)
            if (success) {
                setMessage(msg)
            }
            onResult(success, msg)
        }
    }

    fun writeBackupToStorage(context: Context, uri: Uri, content: String): Boolean {
        val success = com.example.util.BackupCryptoUtils.writeToStorageUri(context, uri, content)
        if (success) {
            setMessage("تم حفظ ملف النسخة الاحتياطية المشفرة بنجاح في وحدة التخزين!")
        } else {
            setMessage("تعذر كتابة الملف في مسار التخزين المحدد.")
        }
        return success
    }

    fun readBackupFromStorage(context: Context, uri: Uri): String? {
        return com.example.util.BackupCryptoUtils.readFromStorageUri(context, uri)
    }

    fun shareBackupViaEmail(context: Context, content: String, itemCount: Int, isPasswordProtected: Boolean) {
        val success = com.example.util.BackupCryptoUtils.shareViaEmail(context, content, itemCount, isPasswordProtected)
        if (!success) {
            setMessage("تعذر فتح تطبيق البريد لإرسال النسخة.")
        }
    }

    fun setMessage(msg: String?) {
        _message.value = msg
    }

    fun clearMessage() {
        _message.value = null
    }

    override fun onCleared() {
        super.onCleared()
        alertManager.release()
    }
}

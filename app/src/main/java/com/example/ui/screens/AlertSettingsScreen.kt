package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertSettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val alertThreshold by viewModel.alertThresholdDays.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val licenseStatus by viewModel.licenseStatus.collectAsState()

    BackHandler {
        viewModel.handleBack()
    }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }
    var exportPassword by remember { mutableStateOf("") }
    var showExportPassword by remember { mutableStateOf(false) }

    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonInput by remember { mutableStateOf("") }

    var showPasswordPromptDialog by remember { mutableStateOf(false) }
    var pendingImportContent by remember { mutableStateOf("") }
    var passwordPromptInput by remember { mutableStateOf("") }
    var passwordPromptError by remember { mutableStateOf<String?>(null) }

    var showActivationDialog by remember { mutableStateOf(false) }
    var activationInput by remember { mutableStateOf("") }
    var activationMsg by remember { mutableStateOf<String?>(null) }

    // SAF File Creator Launcher (حفظ في وحدة تخزين الجهاز)
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && exportedJsonText.isNotBlank()) {
            val success = viewModel.writeBackupToStorage(context, uri, exportedJsonText)
            if (success) {
                Toast.makeText(context, "تم حفظ النسخة المشفرة في وحدة التخزين بنجاح!", Toast.LENGTH_LONG).show()
                showExportDialog = false
            } else {
                Toast.makeText(context, "فشل في حفظ الملف في المسار المحدد.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // SAF File Picker Launcher (استيراد من وحدة تخزين الجهاز)
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileContent = viewModel.readBackupFromStorage(context, uri)
            if (!fileContent.isNullOrBlank()) {
                viewModel.importEncryptedBackup(fileContent) { success, msg ->
                    if (msg == "REQUIRES_PASSWORD") {
                        pendingImportContent = fileContent
                        passwordPromptInput = ""
                        passwordPromptError = null
                        showPasswordPromptDialog = true
                        showImportDialog = false
                    } else {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (success) {
                            showImportDialog = false
                        }
                    }
                }
            } else {
                Toast.makeText(context, "تعذر قراءة محتوى الملف المحدد.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 1. Export Encrypted Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تصدير نسخة احتياطية مشفرة", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تشفير عالي الأمان AES-256-GCM لحماية سرية المنتجات والتواريخ.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        text = "كلمة مرور حماية إضافية (اختياري - اتركه فارغاً للتشفير بمفتاح التطبيق التلقائي):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { newPass ->
                            exportPassword = newPass
                            // إعادة تشفير المحتوى بكلمة المرور المحددة
                            viewModel.exportEncryptedBackup(newPass.takeIf { it.isNotBlank() }) { encJson ->
                                exportedJsonText = encJson
                            }
                        },
                        placeholder = { Text("أدخل كلمة مرور مخصصة إن رغبت") },
                        singleLine = true,
                        visualTransformation = if (showExportPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showExportPassword = !showExportPassword }) {
                                Icon(
                                    imageVector = if (showExportPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "خيارات حفظ وتصدير النسخة المشفرة:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    // Button 1: Save to Device Storage via SAF
                    Button(
                        onClick = {
                            val defaultFileName = "ExpiryBackup_${System.currentTimeMillis()}.json"
                            saveFileLauncher.launch(defaultFileName)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_to_storage_btn")
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ في وحدة تخزين الجهاز (Storage)")
                    }

                    // Button 2: Send via Email with Attachment
                    OutlinedButton(
                        onClick = {
                            val totalItems = viewModel.homeUiState.value.totalCount
                            viewModel.shareBackupViaEmail(
                                context = context,
                                content = exportedJsonText,
                                itemCount = totalItems,
                                isPasswordProtected = exportPassword.isNotBlank()
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_via_email_btn")
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إرسال عبر البريد الإلكتروني (Email)")
                    }

                    // Button 3: Copy to clipboard
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("ExpiryGuard Encrypted Backup", exportedJsonText))
                            Toast.makeText(context, "تم نسخ محتوى JSON المشفر إلى الحافظة!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("نسخ نص الـ JSON المشفر")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // 2. Import Encrypted Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استيراد نسخة احتياطية", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "اختر ملف النسخة الاحتياطية المشفرة (.json) من ذاكرة الجهاز، أو الصق محتواه أدناه:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Button: Select File from Device Storage
                    Button(
                        onClick = {
                            openFileLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pick_backup_file_btn")
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("اختيار ملف من وحدة التخزين (الملفات)")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text("أو الصق النص يدوياً:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = { importJsonInput = it },
                        placeholder = { Text("الصق نص JSON المشفر هنا...") },
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonInput.isNotBlank()) {
                            viewModel.importEncryptedBackup(importJsonInput) { success, msg ->
                                if (msg == "REQUIRES_PASSWORD") {
                                    pendingImportContent = importJsonInput
                                    passwordPromptInput = ""
                                    passwordPromptError = null
                                    showPasswordPromptDialog = true
                                    showImportDialog = false
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    if (success) {
                                        showImportDialog = false
                                        importJsonInput = ""
                                    }
                                }
                            }
                        }
                    },
                    enabled = importJsonInput.isNotBlank()
                ) {
                    Text("فك التشفير والاستيراد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 3. Password Prompt Dialog for Protected Backups
    if (showPasswordPromptDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordPromptDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("الملف محمي بكلمة مرور", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "هذه النسخة الاحتياطية مشفرة بكلمة مرور مخصصة. يرجى إدخال كلمة المرور لفك التشفير واستعادة البيانات:",
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = passwordPromptInput,
                        onValueChange = {
                            passwordPromptInput = it
                            passwordPromptError = null
                        },
                        placeholder = { Text("كلمة المرور") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordPromptError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    passwordPromptError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passwordPromptInput.isNotBlank()) {
                            viewModel.importEncryptedBackup(pendingImportContent, passwordPromptInput) { success, msg ->
                                if (success) {
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    showPasswordPromptDialog = false
                                    pendingImportContent = ""
                                } else {
                                    passwordPromptError = msg
                                }
                            }
                        }
                    }
                ) {
                    Text("تأكيد وفك التشفير")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordPromptDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Activation Code Dialog
    if (showActivationDialog) {
        AlertDialog(
            onDismissRequest = { showActivationDialog = false },
            title = { Text("إدخال رمز التفعيل", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "أدخل رمز التفعيل الممنوح لك للاستمرار في استخدام التطبيق:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = activationInput,
                        onValueChange = {
                            activationInput = it
                            activationMsg = null
                        },
                        placeholder = { Text("مثال: ADR-1Y-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    activationMsg?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (activationInput.isBlank()) {
                            activationMsg = "الرجاء كتابة رمز التفعيل"
                            return@Button
                        }
                        viewModel.activateLicense(activationInput) { success, msg ->
                            if (success) {
                                showActivationDialog = false
                                activationInput = ""
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                activationMsg = msg
                            }
                        }
                    }
                ) {
                    Text("تفعيل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showActivationDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات والتنبيهات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleBack() },
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Flexible Alert Settings (30 days, 20 days, 15 days)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "موعد التنبيه باقتراب انتهاء الصلاحية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "حدد متى ترغب في أن يصنف التطبيق المواد بأنها أوشكت على الانتهاء:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    // Option 1: 30 days (before a month)
                    AlertOptionRow(
                        title = "قبل شهر كامل (30 يوماً)",
                        subtitle = "الخيار الموصى به للمخزون المنزلي والأغذية المجمدة",
                        isSelected = alertThreshold == 30,
                        onClick = { viewModel.setAlertThreshold(30) }
                    )

                    // Option 2: 20 days
                    AlertOptionRow(
                        title = "قبل 20 يوماً",
                        subtitle = "مناسب للاستهلاك المتوسط",
                        isSelected = alertThreshold == 20,
                        onClick = { viewModel.setAlertThreshold(20) }
                    )

                    // Option 3: 15 days
                    AlertOptionRow(
                        title = "قبل 15 يوماً",
                        subtitle = "تنبيه سريع للمواد سريعة الاستهلاك والألبان",
                        isSelected = alertThreshold == 15,
                        onClick = { viewModel.setAlertThreshold(15) }
                    )
                }
            }

            // 2. Audio & Text-to-Speech (TTS) Alerts
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "نظام التنبيه الصوتي (Audio & TTS)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "تنبيه صوتي وتحذير ناطق باللغة العربية مع صفارة خاصة بالمواد الحساسة والأدوية الطبية.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.testVoiceAlert() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_voice_btn")
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اختبار الصوت والإنذار", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.playSummaryVoiceAlert() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("نطق التقرير الشامل", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            viewModel.triggerTestProductNotification(context)
                            Toast.makeText(context, "تم إرسال إشعار مصور للمنتج إلى شريط الإشعارات!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_image_notification_btn")
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تجربة إشعار فوري بصورة المنتج (Image Notification)", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            com.example.service.ExpiryCheckWorker.runOnceNow(context)
                            Toast.makeText(context, "تم إطلاق فحص WorkManager في الخلفية وإرسال الإشعار الصوتي!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trigger_workmanager_btn")
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تشغيل فحص وإشعار الخلفية فوراً (WorkManager)", fontSize = 12.sp)
                    }
                }
            }

            // 3. Theme Display Settings (Light / Dark / System)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "مظهر التطبيق (Display Theme)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionCard(
                            title = "نهاري",
                            icon = Icons.Default.LightMode,
                            isSelected = themeMode == "LIGHT",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setTheme("LIGHT") }
                        )
                        ThemeOptionCard(
                            title = "داكن",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeMode == "DARK",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setTheme("DARK") }
                        )
                        ThemeOptionCard(
                            title = "تلقائي",
                            icon = Icons.Default.BrightnessMedium,
                            isSelected = themeMode == "SYSTEM",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setTheme("SYSTEM") }
                        )
                    }
                }
            }

            // 4. Backup & Restore (JSON Export & Import)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "النسخ الاحتياطي والاستعادة (Backup)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "احفظ بياناتك من الضياع عبر تصديرها أو استيرادها بصيغة آمنة ومشفرة.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                exportPassword = ""
                                viewModel.exportEncryptedBackup(null) { encJson ->
                                    exportedJsonText = encJson
                                    showExportDialog = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_backup_btn")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تصدير مشفر", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                importJsonInput = ""
                                showImportDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_backup_btn")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("استيراد نسخة", fontSize = 13.sp)
                        }
                    }
                }
            }

            // 5. Licensing & Activation Status
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "حالة الترخيص والاشتراك",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "النوع: ${licenseStatus.licenseType}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            licenseStatus.licenseExpiryFormatted?.let {
                                Text(
                                    text = "الصلاحية: $it",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            licenseStatus.activeCode?.let {
                                Text(
                                    text = "الرمز المفعل: $it",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showActivationDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إدخال رمز تفعيل جديد")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun AlertOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = onClick)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ThemeOptionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.5.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

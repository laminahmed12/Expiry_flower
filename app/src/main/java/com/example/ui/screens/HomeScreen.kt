package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ItemCategory
import com.example.data.model.StoredItem
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ExpiryTheme
import com.example.ui.theme.MedicineIndigo
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.WarningAmber
import com.example.util.ExpiryStatus
import com.example.util.ExpiryUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.homeUiState.collectAsState()
    val licenseStatus by viewModel.licenseStatus.collectAsState()
    val alertThreshold by viewModel.alertThresholdDays.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val showPasscodeDialog by viewModel.showPasscodeDialog.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf<StoredItem?>(null) }
    var showActivationDialog by remember { mutableStateOf(false) }
    var activationCodeInput by remember { mutableStateOf("") }
    var activationError by remember { mutableStateOf<String?>(null) }

    // Dialog for Secret Adreemk Passcode
    if (showPasscodeDialog) {
        var passcodeText by remember { mutableStateOf("") }
        var isPasscodeError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { viewModel.dismissPasscodeDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "لوحة التحكم المخفية (Adreemk)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "الرجاء إدخال الرمز السري المخصص للوصول إلى لوحة توليد وإدارة التراخيص:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passcodeText,
                        onValueChange = {
                            passcodeText = it
                            isPasscodeError = false
                        },
                        label = { Text("الرمز السري") },
                        isError = isPasscodeError,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("secret_passcode_input")
                    )
                    if (isPasscodeError) {
                        Text(
                            text = "الرمز السري غير صحيح!",
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
                        val success = viewModel.verifyPasscodeAndOpenAdmin(passcodeText)
                        if (!success) {
                            isPasscodeError = true
                        }
                    },
                    modifier = Modifier.testTag("secret_passcode_submit")
                ) {
                    Text("دخول")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPasscodeDialog() }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog for Manual License Activation (For normal users)
    if (showActivationDialog) {
        AlertDialog(
            onDismissRequest = { showActivationDialog = false },
            title = { Text("تفعيل ترخيص التطبيق", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "أدخل رمز التفعيل الممنوح لك (6 أشهر، سنة، أو دائم) للاستمرار:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = activationCodeInput,
                        onValueChange = {
                            activationCodeInput = it
                            activationError = null
                        },
                        placeholder = { Text("مثال: ADR-6M-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    activationError?.let {
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
                        if (activationCodeInput.isBlank()) {
                            activationError = "يرجى كتابة رمز التفعيل"
                            return@Button
                        }
                        viewModel.activateLicense(activationCodeInput) { success, msg ->
                            if (success) {
                                showActivationDialog = false
                                activationCodeInput = ""
                            } else {
                                activationError = msg
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

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { itemToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("حذف المادة", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف \"${itemToDelete.name}\" من قاعدة البيانات؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(itemToDelete)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("نعم، حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            // 3-Taps secret logo detection
                            viewModel.onLogoClicked()
                        }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "شعار التطبيق - انقر 3 مرات",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "تنبيه الصلاحية",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = licenseStatus.licenseType,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (licenseStatus.isLicensed) SafeGreen else MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                },
                actions = {
                    // Quick Spoken Audio Summary Alert Button
                    IconButton(
                        onClick = { viewModel.playSummaryVoiceAlert() },
                        modifier = Modifier.testTag("voice_summary_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "تشغيل التنبيه الصوتي الشامل",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Barcode Scanner shortcut
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.BARCODE_SCANNER) },
                        modifier = Modifier.testTag("top_scanner_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "فتح الماسح الضوئي"
                        )
                    }

                    // Light / Dark mode quick switch
                    IconButton(
                        onClick = {
                            val nextMode = when (themeMode) {
                                "LIGHT" -> "DARK"
                                "DARK" -> "SYSTEM"
                                else -> "LIGHT"
                            }
                            viewModel.setTheme(nextMode)
                        }
                    ) {
                        Icon(
                            imageVector = if (themeMode == "DARK") Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "تبديل المظهر"
                        )
                    }

                    // Settings
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.SETTINGS) },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات والنسخ الاحتياطي"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Secondary Barcode Scanner FAB
                FloatingActionButton(
                    onClick = { viewModel.navigateTo(Screen.BARCODE_SCANNER) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("fab_scan_barcode")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "مسح الباركود",
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Add Item FAB
                FloatingActionButton(
                    onClick = { viewModel.startAddItem() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_item")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة مادة")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "إضافة مادة",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Trial / License Banner
            item {
                LicenseBanner(
                    status = licenseStatus,
                    onActivateClick = { showActivationDialog = true }
                )
            }

            // 2. Statistics Grid Cards
            item {
                StatsRow(
                    total = uiState.totalCount,
                    expired = uiState.expiredCount,
                    expiringSoon = uiState.expiringSoonCount,
                    safe = uiState.safeCount,
                    medicine = uiState.medicineCount
                )
            }

            // 3. Search and Quick Category Filter
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Search Bar
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("بحث بالاسم، الباركود، أو مكان التخزين...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Text("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_field")
                    )

                    // Categories Filter Tabs
                    CategoryChips(
                        selectedCategory = uiState.selectedCategory,
                        onSelectCategory = { viewModel.selectCategory(it) }
                    )
                }
            }

            // 4. Section Title with item count
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المخزون المسجل (${uiState.filteredItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "تنبيه قبل ${alertThreshold} يوماً",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 5. Items List
            if (uiState.filteredItems.isEmpty()) {
                item {
                    EmptyStateCard(
                        isFiltered = uiState.searchQuery.isNotEmpty() || uiState.selectedCategory != null,
                        onResetFilters = {
                            viewModel.setSearchQuery("")
                            viewModel.selectCategory(null)
                        },
                        onAddNew = { viewModel.startAddItem() }
                    )
                }
            } else {
                items(uiState.filteredItems, key = { it.id }) { item ->
                    ItemCard(
                        item = item,
                        thresholdDays = alertThreshold,
                        onEdit = { viewModel.startEditItem(item) },
                        onDelete = { showDeleteConfirmDialog = item },
                        onVoiceAlert = { viewModel.playItemVoiceAlert(item) }
                    )
                }
            }

            // Spacer for FAB visibility
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
fun LicenseBanner(
    status: com.example.data.model.LicenseStatus,
    onActivateClick: () -> Unit
) {
    val palette = ExpiryTheme.colors
    val bgColor = when {
        status.isLicensed -> palette.safeContainer
        status.isTrialActive -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        else -> palette.dangerContainer
    }

    val icon = when {
        status.isLicensed -> Icons.Default.CheckCircle
        status.isTrialActive -> Icons.Default.Shield
        else -> Icons.Default.Warning
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (status.isLicensed) palette.safe else if (status.isTrialActive) palette.warning else palette.danger,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (status.isLicensed) {
                            "الترخيص مفعل: ${status.licenseType}"
                        } else if (status.isTrialActive) {
                            "الفترة التجريبية نشطة (متبقي ${status.trialDaysRemaining} أيام)"
                        } else {
                            "انتهت الفترة التجريبية (10 أيام)"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = status.licenseExpiryFormatted ?: "متبقي ${status.trialDaysRemaining} يوماً للتجربة المجانية",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!status.isLicensed) {
                Button(
                    onClick = onActivateClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("activate_license_banner_btn")
                ) {
                    Text("تفعيل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatsRow(
    total: Int,
    expired: Int,
    expiringSoon: Int,
    safe: Int,
    medicine: Int
) {
    val palette = ExpiryTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            title = "الكل",
            count = total,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "منتهي",
            count = expired,
            color = palette.danger,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "قريب",
            count = expiringSoon,
            color = palette.warning,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "سليم",
            count = safe,
            color = palette.safe,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "أدوية",
            count = medicine,
            color = palette.medicine,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatCard(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 4.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = color
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CategoryChips(
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChipItem(
                title = "الكل",
                icon = Icons.Default.FilterList,
                isSelected = selectedCategory == null,
                onClick = { onSelectCategory(null) }
            )
        }
        item {
            FilterChipItem(
                title = ItemCategory.FOOD.titleAr,
                icon = Icons.Default.Fastfood,
                isSelected = selectedCategory == ItemCategory.FOOD.code,
                onClick = { onSelectCategory(ItemCategory.FOOD.code) }
            )
        }
        item {
            FilterChipItem(
                title = ItemCategory.CONSUMABLES.titleAr,
                icon = Icons.Default.CleaningServices,
                isSelected = selectedCategory == ItemCategory.CONSUMABLES.code,
                onClick = { onSelectCategory(ItemCategory.CONSUMABLES.code) }
            )
        }
        item {
            FilterChipItem(
                title = ItemCategory.MEDICINE.titleAr,
                icon = Icons.Default.LocalPharmacy,
                isSelected = selectedCategory == ItemCategory.MEDICINE.code,
                onClick = { onSelectCategory(ItemCategory.MEDICINE.code) }
            )
        }
    }
}

@Composable
fun FilterChipItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = title, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
fun PulsingWarningIcon(
    daysRemaining: Long,
    modifier: Modifier = Modifier
) {
    val palette = ExpiryTheme.colors
    val isExpired = daysRemaining < 0
    val alertColor = if (isExpired) palette.danger else palette.warning
    val iconColor = if (isExpired) Color.White else palette.onWarningBadgeText

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = if (palette.isDark) 0.15f else 0.20f,
        targetValue = if (palette.isDark) 0.55f else 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_halo_alpha"
    )

    Box(
        modifier = modifier.size(26.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glowing animated pulsing halo
        Box(
            modifier = Modifier
                .size(26.dp)
                .scale(scale)
                .alpha(haloAlpha)
                .background(alertColor.copy(alpha = if (palette.isDark) 0.28f else 0.35f), CircleShape)
        )

        // Inner solid circular badge with warning symbol
        Surface(
            shape = CircleShape,
            color = alertColor,
            modifier = Modifier
                .size(18.dp)
                .scale(scale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = if (isExpired) "منتهي الصلاحية" else "تحذير: الصلاحية أقل من 7 أيام",
                    tint = iconColor,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

private data class ItemBadgeInfo(
    val bg: Color,
    val text: String,
    val statusColor: Color,
    val badgeTextColor: Color
)

@Composable
fun ItemCard(
    item: StoredItem,
    thresholdDays: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onVoiceAlert: () -> Unit
) {
    val palette = ExpiryTheme.colors
    val daysRemaining = ExpiryUtils.calculateDaysRemaining(item.expiryDate)
    val status = ExpiryUtils.getExpiryStatus(item.expiryDate, thresholdDays)

    val badgeInfo = when (status) {
        ExpiryStatus.EXPIRED -> ItemBadgeInfo(palette.dangerContainer, "منتهي الصلاحية", palette.danger, palette.onDangerBadgeText)
        ExpiryStatus.EXPIRING_SOON -> ItemBadgeInfo(palette.warningContainer, "اقترب الانتهاء", palette.warning, palette.onWarningBadgeText)
        ExpiryStatus.SAFE -> ItemBadgeInfo(palette.safeContainer, "صالح للاستخدام", palette.safe, palette.onSafeBadgeText)
    }
    val badgeBg = badgeInfo.bg
    val badgeText = badgeInfo.text
    val statusColor = badgeInfo.statusColor
    val badgeTextColor = badgeInfo.badgeTextColor

    val category = ItemCategory.fromCode(item.category)
    val categoryIcon = when (category) {
        ItemCategory.FOOD -> Icons.Default.Fastfood
        ItemCategory.CONSUMABLES -> Icons.Default.CleaningServices
        ItemCategory.MEDICINE -> Icons.Default.LocalPharmacy
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (status == ExpiryStatus.EXPIRED) palette.danger.copy(alpha = 0.5f)
            else if (daysRemaining < 7) palette.warning.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Category + Status Badge + Voice Speaker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Chip
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (category == ItemCategory.MEDICINE) palette.medicineContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = categoryIcon,
                                contentDescription = null,
                                tint = if (category == ItemCategory.MEDICINE) palette.medicine
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = category.titleAr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.isSensitive || category == ItemCategory.MEDICINE) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = palette.medicineContainer
                        ) {
                            Text(
                                text = "مادة حساسة",
                                color = palette.medicine,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (daysRemaining < 7) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (daysRemaining < 0) palette.dangerContainer else palette.warningContainer
                        ) {
                            Text(
                                text = if (daysRemaining < 0) "منتهي الصلاحية!" else "أقل من 7 أيام!",
                                color = if (daysRemaining < 0) palette.danger else palette.warning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Voice Alert Speaker Button
                IconButton(
                    onClick = onVoiceAlert,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "نطق تنبيه الصلاحية",
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title & Quantity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (daysRemaining < 7) {
                        PulsingWarningIcon(
                            daysRemaining = daysRemaining,
                            modifier = Modifier.testTag("pulsing_warning_${item.id}")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "الكمية: ${item.quantity}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expiry Info Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "تاريخ الانتهاء: ${item.expiryDate}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        Text(
                            text = ExpiryUtils.getRelativeTimeAr(daysRemaining),
                            fontSize = 11.sp,
                            color = if (palette.isDark) TextSecondaryDark else statusColor.copy(alpha = 0.85f)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Storage Location & Barcode
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.storageLocation.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.storageLocation,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (!item.barcode.isNullOrBlank()) {
                    Text(
                        text = "الباركود: ${item.barcode}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Notes if available
            if (item.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.notes,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Action Buttons: Edit and Delete
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تعديل", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(6.dp))

                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    isFiltered: Boolean,
    onResetFilters: () -> Unit,
    onAddNew: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isFiltered) Icons.Default.Search else Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isFiltered) "لا توجد مواد مطابقة لنتائج البحث أو التصنيف" else "لا توجد أي مواد مسجلة حالياً",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isFiltered) "جرّب تغيير كلمات البحث أو إعادة ضبط التصنيفات" else "ابدأ بإضافة المواد الغذائية والاستهلاكية أو الأدوية لتتبع تواريخ صلاحيتها",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (isFiltered) {
                OutlinedButton(onClick = onResetFilters) {
                    Text("إلغاء التصفية")
                }
            } else {
                Button(onClick = onAddNew) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة مادة أولى")
                }
            }
        }
    }
}

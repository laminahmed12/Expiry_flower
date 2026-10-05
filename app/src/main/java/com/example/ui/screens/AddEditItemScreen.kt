package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ItemCategory
import com.example.data.model.StoredItem
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.theme.ExpiryTheme
import com.example.ui.theme.MedicineIndigo
import com.example.util.ExpiryUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val editingItem by viewModel.editingItem.collectAsState()
    val scannedBarcode by viewModel.scannedBarcode.collectAsState()

    BackHandler {
        viewModel.handleBack()
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val todayStr = remember { dateFormat.format(Date()) }

    var name by remember { mutableStateOf(editingItem?.name ?: "") }
    var category by remember { mutableStateOf(editingItem?.category ?: ItemCategory.FOOD.code) }
    var productionDate by remember { mutableStateOf(editingItem?.productionDate ?: todayStr) }
    var expiryDate by remember {
        mutableStateOf(
            editingItem?.expiryDate ?: run {
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, 1)
                dateFormat.format(cal.time)
            }
        )
    }
    var quantity by remember { mutableIntStateOf(editingItem?.quantity ?: 1) }
    var storageLocation by remember { mutableStateOf(editingItem?.storageLocation ?: "") }
    var barcode by remember { mutableStateOf(editingItem?.barcode ?: scannedBarcode ?: "") }
    var imageUri by remember { mutableStateOf(editingItem?.imageUri) }
    var notes by remember { mutableStateOf(editingItem?.notes ?: "") }
    var isSensitive by remember {
        mutableStateOf(editingItem?.isSensitive ?: (editingItem?.category == ItemCategory.MEDICINE.code))
    }

    // Validation errors
    var nameError by remember { mutableStateOf(false) }

    // Date Picker States
    var showProductionDatePicker by remember { mutableStateOf(false) }
    var showExpiryDatePicker by remember { mutableStateOf(false) }

    // Apply scanned barcode directly without fake mock presets
    LaunchedEffect(scannedBarcode) {
        if (!scannedBarcode.isNullOrBlank()) {
            barcode = scannedBarcode!!
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri.toString()
        }
    }

    // Production Date Picker Dialog
    if (showProductionDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showProductionDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            productionDate = dateFormat.format(Date(millis))
                        }
                        showProductionDatePicker = false
                    }
                ) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProductionDatePicker = false }) {
                    Text("إلغاء")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Expiry Date Picker Dialog
    if (showExpiryDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
        )
        DatePickerDialog(
            onDismissRequest = { showExpiryDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            expiryDate = dateFormat.format(Date(millis))
                        }
                        showExpiryDatePicker = false
                    }
                ) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExpiryDatePicker = false }) {
                    Text("إلغاء")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (editingItem != null) "تعديل المادة" else "إضافة مادة جديدة",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleBack() },
                        modifier = Modifier.testTag("back_button")
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
            // 1. Category Selector (أغذية، استهلاكية، أدوية)
            Text(
                text = "التصنيف الأساسي للمادة *",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategorySelectOption(
                    title = ItemCategory.FOOD.titleAr,
                    icon = Icons.Default.Fastfood,
                    isSelected = category == ItemCategory.FOOD.code,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        category = ItemCategory.FOOD.code
                        if (!isSensitive) isSensitive = false
                    }
                )
                CategorySelectOption(
                    title = ItemCategory.CONSUMABLES.titleAr,
                    icon = Icons.Default.CleaningServices,
                    isSelected = category == ItemCategory.CONSUMABLES.code,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        category = ItemCategory.CONSUMABLES.code
                    }
                )
                CategorySelectOption(
                    title = ItemCategory.MEDICINE.titleAr,
                    icon = Icons.Default.LocalPharmacy,
                    isSelected = category == ItemCategory.MEDICINE.code,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        category = ItemCategory.MEDICINE.code
                        isSensitive = true // الأدوية حساسة افتراضياً لتشغيل التنبيه الصوتي المميز
                    }
                )
            }

            // 2. Product Name Field
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = false
                },
                label = { Text("اسم المادة / المنتج *") },
                placeholder = { Text("مثال: حليب طازج، مسكن باراسيتامول، معقم...") },
                isError = nameError,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("item_name_input")
            )
            if (nameError) {
                Text(
                    text = "يرجى كتابة اسم المادة للمتابعة",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 3. Barcode with Scanner Trigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("رمز الباركود (Barcode)") },
                    placeholder = { Text("أدخل يدوياً أو امسح بالكاميرا") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("item_barcode_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.navigateTo(Screen.BARCODE_SCANNER) },
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                        .testTag("scan_barcode_icon_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "مسح الباركود",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // 4. Expiry & Production Dates
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "تواريخ الإنتاج والانتهاء *",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    // Expiry Date (Highlighted)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showExpiryDatePicker = true }
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("تاريخ الانتهاء (Expiry Date):", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text(expiryDate, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    // Quick duration presets for Expiry
                    Text("إضافة سريعة لمدة الصلاحية:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presets = listOf(
                            "+7 أيام" to 7,
                            "+15 يوماً" to 15,
                            "+شهر" to 30,
                            "+3 أشهر" to 90,
                            "+6 أشهر" to 180,
                            "+سنة" to 365,
                            "+سنتين" to 730
                        )
                        items(presets) { (title, days) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable {
                                    val cal = Calendar.getInstance()
                                    cal.add(Calendar.DAY_OF_YEAR, days)
                                    expiryDate = dateFormat.format(cal.time)
                                }
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Production Date
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProductionDatePicker = true }
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("تاريخ الإنتاج (Production Date):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(productionDate, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 5. Quantity & Storage Location
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quantity Stepper
                Column(modifier = Modifier.weight(0.45f)) {
                    Text("الكمية:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (quantity > 1) quantity-- },
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = quantity.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Button(
                            onClick = { quantity++ },
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Storage Location
                OutlinedTextField(
                    value = storageLocation,
                    onValueChange = { storageLocation = it },
                    label = { Text("مكان التخزين") },
                    placeholder = { Text("الثلاجة، صيدلية المنزل...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(0.55f)
                        .testTag("item_storage_input")
                )
            }

            // Storage Quick Suggestions
            Text("اقتراحات سريعة لمكان التخزين:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val suggestions = listOf("الثلاجة", "الفريزر", "صيدلية المنزل", "خزانة المطبخ", "المستودع", "الرف الأوسط", "درج الأدوية")
                items(suggestions) { place ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { storageLocation = place }
                    ) {
                        Text(
                            text = place,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 6. Sensitive Item & Voice Alarm Feature
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSensitive) MedicineIndigo.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isSensitive) ExpiryTheme.colors.medicine else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "مادة حساسة (تنبيه صوتي تحذيري خاص)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "يطلق إنذاراً صوتياً مميزاً عند اقتراب الانتهاء (خاص بالأدوية الحساسة)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isSensitive,
                        onCheckedChange = { isSensitive = it }
                    )
                }
            }

            // 7. Product Photo
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("صورة المنتج (اختياري)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (imageUri != null) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = imageUri,
                                    contentDescription = "صورة المنتج",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (imageUri != null) "تغيير الصورة" else "اختيار صورة للمنتج")
                        }
                    }
                }
            }

            // 8. Notes Field
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات إضافية") },
                placeholder = { Text("شروط الحفظ، تعليمات الاستخدام...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // 9. Save Button
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }

                    val itemToSave = StoredItem(
                        id = editingItem?.id ?: 0L,
                        name = name.trim(),
                        category = category,
                        productionDate = productionDate,
                        expiryDate = expiryDate,
                        quantity = quantity,
                        storageLocation = storageLocation.trim(),
                        barcode = barcode.trim().takeIf { it.isNotBlank() },
                        imageUri = imageUri,
                        notes = notes.trim(),
                        isSensitive = isSensitive,
                        createdAt = editingItem?.createdAt ?: System.currentTimeMillis()
                    )

                    viewModel.saveItem(itemToSave) {
                        viewModel.handleBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_item_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (editingItem != null) "حفظ التعديلات" else "إضافة المادة للمخزون",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun CategorySelectOption(
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
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

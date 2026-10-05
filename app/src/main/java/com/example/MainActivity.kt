package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.example.service.ExpiryCheckWorker
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.screens.AddEditItemScreen
import com.example.ui.screens.AdreemkAdminScreen
import com.example.ui.screens.AlertSettingsScreen
import com.example.ui.screens.BarcodeScannerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // بدء جدولة خدمة WorkManager الدورية لفحص الصلاحية في الخلفية
        ExpiryCheckWorker.schedulePeriodicWork(applicationContext)

        setContent {
            val context = LocalContext.current
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val currentScreen by viewModel.currentScreen.collectAsState()
            val message by viewModel.message.collectAsState()

            val snackbarHostState = remember { SnackbarHostState() }

            // Request Notification permission for Android 13+ (API 33+)
            var hasRequestedPermission by remember { mutableStateOf(false) }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    viewModel.setMessage("تم تفعيل إشعارات وتنبيهات الخلفية بنجاح!")
                }
            }

            LaunchedEffect(Unit) {
                if (!hasRequestedPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasRequestedPermission = true
                    val isGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!isGranted) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            LaunchedEffect(message) {
                message?.let {
                    snackbarHostState.showSnackbar(it)
                    viewModel.clearMessage()
                }
            }

            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(1200)
                showSplash = false
            }

            MyApplicationTheme(themeMode = themeMode) {
                if (showSplash) {
                    LegacyAdreemkSplash()
                } else {
                // Ensure natural Arabic RTL orientation
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(snackbarHostState) }
                    ) { innerPadding ->
                        Crossfade(
                            targetState = currentScreen,
                            label = "screen_transition",
                            modifier = Modifier.padding(innerPadding)
                        ) { screen ->
                            when (screen) {
                                Screen.HOME -> HomeScreen(viewModel = viewModel)
                                Screen.ADD_EDIT -> AddEditItemScreen(viewModel = viewModel)
                                Screen.BARCODE_SCANNER -> BarcodeScannerScreen(viewModel = viewModel)
                                Screen.SETTINGS -> AlertSettingsScreen(viewModel = viewModel)
                                Screen.ADREEMK_ADMIN -> AdreemkAdminScreen(viewModel = viewModel)
                            }
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun LegacyAdreemkSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1F3A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AD",
                    color = Color(0xFF0B1F3A),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(22.dp))
            Text("ADREEMK", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("تنبيه الصلاحية", color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp)
            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .height(4.dp),
                color = Color(0xFF2E7D32),
                trackColor = Color.White.copy(alpha = 0.18f)
            )
        }
    }
}

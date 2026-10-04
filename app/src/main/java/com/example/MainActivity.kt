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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
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

            MyApplicationTheme(themeMode = themeMode) {
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

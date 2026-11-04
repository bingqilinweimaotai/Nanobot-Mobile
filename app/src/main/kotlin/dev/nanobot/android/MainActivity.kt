package dev.nanobot.android

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLocale.language = SettingsStore(this).language
        applyDebugIntent(intent)
        PairingLaunch.from(intent)
        GatewayRepository.init(application)
        if (GatewayRepository.configured) {
            runCatching {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, NanobotService::class.java),
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            val viewModel: ChatViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application),
            )
            val state by viewModel.ui.collectAsStateWithLifecycle()
            val dark = state.themeMode.resolveDark(isSystemInDarkTheme())
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            NanobotTheme(
                themeMode = state.themeMode,
                accent = state.accentTheme,
                customHue = state.customAccentHue,
                language = state.language,
                fontSize = state.fontSize,
            ) {
                NanobotApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDebugIntent(intent)
        PairingLaunch.from(intent)
    }

    private fun applyDebugIntent(intent: Intent?) {
        val debuggable =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable || intent == null) return
        intent.getStringExtra("nanobot_endpoint")?.let { DebugLaunch.endpoint = it }
        intent.getStringExtra("nanobot_secret")?.let { DebugLaunch.secret = it }
        intent.getStringExtra("nanobot_chat")?.let { DebugLaunch.chatId = it }
        intent.getStringExtra("nanobot_draft")?.let { DebugLaunch.draft = it }
        DebugLaunch.openDrawer = intent.getStringExtra("nanobot_drawer") == "1"
    }
}

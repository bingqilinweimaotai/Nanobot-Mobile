package dev.nanobot.android

import android.content.Context
import dev.nanobot.protocol.NanobotJson
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** Mirrors the WebUI's system / light / dark theme choice. */
enum class ThemeMode(val storageValue: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("nanobot", Context.MODE_PRIVATE)
    private val secrets = SecretStore(appContext)

    var endpoint: String
        get() = prefs.getString(KEY_ENDPOINT, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_ENDPOINT, value).apply()
        }

    /** Stable per-install identifier used for `channels.websocket.allowFrom`. */
    val clientId: String
        get() {
            prefs.getString(KEY_CLIENT_ID, null)?.let { return it }
            val generated = "android-" + UUID.randomUUID().toString().take(12)
            prefs.edit().putString(KEY_CLIENT_ID, generated).apply()
            return generated
        }

    var lastChatId: String?
        get() = prefs.getString(KEY_LAST_CHAT, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_CHAT, value).apply()
        }

    var secret: String
        get() = secrets.read().orEmpty()
        set(value) {
            secrets.write(value)
        }

    val isConfigured: Boolean get() = endpoint.isNotBlank()

    var themeMode: ThemeMode
        get() = ThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, null))
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.storageValue).apply()
        }

    var accentTheme: AccentTheme
        get() = AccentTheme.fromStorage(prefs.getString(KEY_ACCENT_THEME, null))
        set(value) {
            prefs.edit().putString(KEY_ACCENT_THEME, value.storageValue).apply()
        }

    var customAccentHue: Float
        get() = prefs.getFloat(KEY_CUSTOM_ACCENT_HUE, DEFAULT_CUSTOM_HUE)
        set(value) {
            prefs.edit().putFloat(KEY_CUSTOM_ACCENT_HUE, value).apply()
        }

    /** Interface language chosen in Appearance; defaults to Simplified Chinese. */
    var language: AppLanguage
        get() = AppLanguage.fromStorage(prefs.getString(KEY_LANGUAGE, null))
            ?: AppLanguage.default()
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value.storageValue).apply()
        }

    /** Multiplier applied on top of the system font scale. */
    var fontSize: AppFontSize
        get() = AppFontSize.fromStorage(prefs.getString(KEY_FONT_SIZE, null))
        set(value) {
            prefs.edit().putString(KEY_FONT_SIZE, value.storageValue).apply()
        }

    /** Turn-completion notifications, matching the WebUI's browser notification preference. */
    var turnNotifications: Boolean
        get() = prefs.getBoolean(KEY_TURN_NOTIFICATIONS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_TURN_NOTIFICATIONS, value).apply()
        }

    var notificationSound: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_SOUND, false)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIFICATION_SOUND, value).apply()
        }

    var collapsedGroups: Set<String>
        get() {
            val raw = prefs.getString(KEY_COLLAPSED_GROUPS, null) ?: return emptySet()
            val serializer = ListSerializer(String.serializer())
            return runCatching { NanobotJson.decodeFromString(serializer, raw).toSet() }
                .getOrDefault(emptySet())
        }
        set(value) {
            val serializer = ListSerializer(String.serializer())
            val raw = NanobotJson.encodeToString(serializer, value.sorted())
            prefs.edit().putString(KEY_COLLAPSED_GROUPS, raw).apply()
        }

    private companion object {
        const val KEY_COLLAPSED_GROUPS = "collapsed_groups"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_LAST_CHAT = "last_chat"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT_THEME = "accent_theme"
        const val KEY_CUSTOM_ACCENT_HUE = "custom_accent_hue"
        const val KEY_LANGUAGE = "language"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_TURN_NOTIFICATIONS = "turn_notifications"
        const val KEY_NOTIFICATION_SOUND = "notification_sound"
    }
}

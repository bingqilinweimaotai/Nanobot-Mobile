package dev.nanobot.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.nanobot.protocol.InboundEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the gateway connection alive and surfaces turn completion while the UI is away. */
class NanobotService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        AppLocale.language = preferences.language
        GatewayRepository.init(this)
        createChannels()
        startForeground(SERVICE_NOTIFICATION_ID, serviceNotification())
        scope.launch {
            GatewayRepository.events.collect { event ->
                if (appVisible()) return@collect
                when (event) {
                    is InboundEvent.TurnEnd -> if (preferences.turnNotifications) notifyTurn(event)
                    is InboundEvent.ErrorEvent -> if (preferences.turnNotifications) notifyError(event)
                    else -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun appVisible(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun serviceNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("nanobot")
            .setContentText(AppLocale.strings.connectedToGateway)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun notifyTurn(event: InboundEvent.TurnEnd) {
        val text = when {
            event.outcome == "failed" -> AppLocale.strings.turnFailed
            event.outcome == "cancelled" -> AppLocale.strings.turnCancelled
            else -> AppLocale.strings.turnCompleted
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_TURNS)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("nanobot")
            .setContentText(text)
            .setGroup(GROUP_TURNS)
            .setAutoCancel(true)
            .setSilent(!preferences.notificationSound)
            .setContentIntent(openAppIntent())
            .build()
        notificationManager().notify(event.chatId.hashCode(), notification)
    }

    private fun notifyError(event: InboundEvent.ErrorEvent) {
        val text = event.reason ?: event.detail ?: AppLocale.strings.gatewayError
        val notification = NotificationCompat.Builder(this, CHANNEL_TURNS)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(AppLocale.strings.nanobotError)
            .setContentText(text)
            .setGroup(GROUP_TURNS)
            .setAutoCancel(true)
            .setSilent(!preferences.notificationSound)
            .setContentIntent(openAppIntent())
            .build()
        notificationManager().notify((event.chatId ?: "error").hashCode(), notification)
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun createChannels() {
        val manager = notificationManager()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                AppLocale.strings.channelGatewayConnection,
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TURNS,
                AppLocale.strings.channelTurnResults,
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private companion object {
        const val CHANNEL_SERVICE = "nanobot-service"
        const val CHANNEL_TURNS = "nanobot-turns"
        const val GROUP_TURNS = "nanobot-turn-results"
        const val SERVICE_NOTIFICATION_ID = 1001
    }
}

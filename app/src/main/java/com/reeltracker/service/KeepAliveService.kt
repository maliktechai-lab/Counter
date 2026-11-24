package com.reeltracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.reeltracker.R
import com.reeltracker.ui.MainActivity

/**
 * Foreground service that keeps the app process alive so the accessibility service
 * can continue counting in the background.
 *
 * Declared with `foregroundServiceType="specialUse"` in the manifest, as required
 * by API 34+ for foreground services that do not fit one of the predefined types.
 *
 * This service does no work beyond holding a persistent notification. The OS requires
 * a visible notification for foreground services; this prevents process death on OEM
 * skins with aggressive battery management (MIUI, One UI, ColorOS, etc.).
 *
 * ## Lifecycle
 * - Started by [ReelTrackerAccessibilityService.onServiceConnected].
 * - Stopped by [ReelTrackerAccessibilityService.onDestroy].
 * - Restarted after reboot by [BootReceiver].
 * - [START_STICKY]: the OS restarts the service automatically if it is killed.
 *
 * ## Stop action
 * The notification carries a Stop action that sends ACTION_STOP to this service.
 * [onStartCommand] handles that action by calling [stopSelf], which removes the
 * foreground notification and stops the service cleanly.
 */
class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID       = "reeltracker_keepalive"
        private const val NOTIFICATION_ID  = 1
        private const val ACTION_STOP      = "com.reeltracker.action.STOP_KEEPALIVE"

        /** Start or restart the KeepAliveService from any [Context]. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, KeepAliveService::class.java))
        }

        /** Stop the KeepAliveService. */
        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The notification's Stop action delivers ACTION_STOP here.
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // Sticky: the OS restarts this service if killed so the foreground notification
        // is re-raised and process continuity is maintained.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val name    = getString(R.string.notification_channel_name)
        val desc    = getString(R.string.notification_channel_desc)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
            .apply { description = desc }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = run {
        val tapPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Stop action: sends ACTION_STOP to this service so onStartCommand can call stopSelf().
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, KeepAliveService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_body))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapPi)
            .addAction(0, getString(R.string.notification_stop), stopPi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}

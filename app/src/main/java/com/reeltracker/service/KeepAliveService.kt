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
 * can continue counting in the background without being killed by the OS.
 *
 * Declared with `foregroundServiceType="specialUse"` in the manifest, as required
 * by API 34+ for services that do not fit one of the predefined types.
 *
 * This service itself does no work. Its only purpose is to hold a persistent
 * notification that the OS requires for foreground services, preventing
 * process death on OEM skins with aggressive battery management.
 */
class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "reeltracker_keepalive"
        private const val NOTIFICATION_ID = 1

        /** Start or restart the KeepAliveService from any [Context]. */
        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.startForegroundService(intent)
        }

        /** Stop the KeepAliveService. */
        fun stop(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sticky so the service restarts if killed; the notification will re-appear.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val name = getString(R.string.notification_channel_name)
        val desc = getString(R.string.notification_channel_desc)
        val channel = NotificationChannel(
            CHANNEL_ID,
            name,
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = desc }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification() = run {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, KeepAliveService::class.java).also { it.action = "STOP" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_body))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}

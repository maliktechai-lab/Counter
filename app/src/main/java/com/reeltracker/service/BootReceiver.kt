package com.reeltracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts [KeepAliveService] after the device boots.
 *
 * Receives [Intent.ACTION_BOOT_COMPLETED] (after unlock) and
 * [Intent.ACTION_LOCKED_BOOT_COMPLETED] (direct-boot, before first unlock).
 * Both are declared in the manifest so the service resumes whether or not the
 * user has unlocked their device at boot time.
 *
 * The receiver only starts the service; enabling or disabling the accessibility
 * service itself is left to the user via system Accessibility settings.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> KeepAliveService.start(context)
        }
    }
}

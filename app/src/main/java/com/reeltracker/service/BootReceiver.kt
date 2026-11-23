package com.reeltracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts [KeepAliveService] after the device boots.
 *
 * Receives [Intent.ACTION_BOOT_COMPLETED] and [Intent.ACTION_LOCKED_BOOT_COMPLETED].
 * Both are declared in the manifest so the service resumes after a reboot whether or
 * not the user has unlocked their device.
 *
 * The receiver only starts the service; enabling or disabling the accessibility
 * service itself is left to the user via system Accessibility settings.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) {
            KeepAliveService.start(context)
        }
    }
}

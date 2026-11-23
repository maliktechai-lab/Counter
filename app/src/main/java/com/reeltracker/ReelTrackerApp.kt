package com.reeltracker

import android.app.Application
import com.reeltracker.di.ServiceLocator

class ReelTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}

package com.bellizia.mcmonitor

import android.app.Application
import com.bellizia.mcmonitor.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        Prefs.init(this)
    }
}

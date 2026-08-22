package com.bellizia.mcmonitor

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.bellizia.mcmonitor.data.AppLock
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.ui.LockActivity

class App : Application() {

    /** Quante schermate sono a video: a zero l'app e' passata in secondo piano. */
    private var visibili = 0

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        Prefs.init(this)
        if (!Prefs.lockConfigured) AppLock.unlock()
        watchForLock()
    }

    /**
     * Il blocco vale per tutta l'app, non per una schermata sola: chi lascia il
     * telefono sul tavolo e torna dopo qualche minuto deve ritrovare la porta
     * chiusa, in qualunque scheda fosse rimasto.
     */
    private fun watchForLock() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (visibili == 0) {
                    AppLock.onForeground(System.currentTimeMillis(), Prefs.lockTimeoutMinutes)
                }
                visibili++
            }

            override fun onActivityStopped(activity: Activity) {
                visibili--
                if (visibili <= 0) AppLock.onBackground(System.currentTimeMillis())
            }

            override fun onActivityResumed(activity: Activity) {
                if (activity is LockActivity) return
                if (!Prefs.lockConfigured || !AppLock.locked) return
                // Si copre quello che c'e', senza chiuderlo: sbloccando si torna
                // esattamente dov'era.
                activity.startActivity(
                    Intent(activity, LockActivity::class.java)
                        .putExtra(LockActivity.EXTRA_RESUME, true)
                )
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

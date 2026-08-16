package com.bellizia.mcmonitor.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bellizia.mcmonitor.data.Prefs

/**
 * Dopo un riavvio del telefono il monitoraggio riparte da solo, altrimenti
 * l'utente scoprirebbe di non ricevere più nulla solo quando serve.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Prefs.init(context)
        ServerWatchService.sync(context)
    }
}

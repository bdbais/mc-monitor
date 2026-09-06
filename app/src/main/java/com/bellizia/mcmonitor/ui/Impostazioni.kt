package com.bellizia.mcmonitor.ui

import android.app.Activity
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * L'ingranaggio in alto: la stessa porta in ogni schermata.
 *
 * Prima queste voci stavano solo nel menu a scomparsa della prima pagina, e chi
 * era gia' dentro un server non aveva modo di arrivarci senza tornare indietro.
 * Un ingranaggio si riconosce senza leggere, ed e' li' anche quando non si sa
 * come si chiama la cosa che si sta cercando.
 *
 * Le voci sono poche e dette in italiano corrente: chi non sa cos'e' un profilo
 * salvato deve comunque capire dove sta il manuale.
 */
object Impostazioni {

    /**
     * Una voce in piu' in cima, per chi la apre da dentro un server: le
     * impostazioni di *quel* server sono un'altra cosa dalle impostazioni
     * dell'app, e tenerle separate evita di cercare la password del server
     * fra le opzioni del telefono.
     */
    fun mostra(activity: Activity, primaVoce: Pair<String, () -> Unit>? = null) {
        val voci = buildList {
            primaVoce?.let { add(it) }
            add("Blocco e amministratore" to { LockSettings.show(activity) })
            add("Profili salvati" to {
                activity.startActivity(Intent(activity, ServersActivity::class.java))
            })
            add("Manuale d'uso" to { About.manual(activity) })
            add("Informazioni" to { About.show(activity) })
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Impostazioni")
            .setItems(voci.map { it.first }.toTypedArray()) { _, quale -> voci[quale].second() }
            .setNegativeButton("Chiudi", null)
            .show()
    }
}

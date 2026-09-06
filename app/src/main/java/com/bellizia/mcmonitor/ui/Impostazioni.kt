package com.bellizia.mcmonitor.ui

import android.app.Activity
import android.content.Intent
import com.bellizia.mcmonitor.data.Prefs
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
            add("Facce dei giocatori" to { facce(activity) })
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

    /**
     * Da dove vengono le facce.
     *
     * La scelta è scritta per intero invece che come interruttore «usa skin
     * online», perché chi la legge deve capire cosa scambia: non è una
     * questione di grafica più bella, è che una delle due opzioni manda fuori i
     * nomi di chi gioca sul tuo server. Detta così la si può anche scegliere —
     * ma sapendola.
     */
    private fun facce(activity: Activity) {
        val opzioni = arrayOf(
            "Disegnate dall'app",
            "Skin vere, prese da internet"
        )
        val attuale = if (Prefs.skinDaInternet) 1 else 0
        MaterialAlertDialogBuilder(activity)
            .setTitle("Facce dei giocatori")
            .setMessage(
                "Disegnate dall'app: ogni giocatore ha una faccia sempre uguale, " +
                        "ricavata dal nome. Non somiglia alla sua skin, ma non esce niente " +
                        "dal telefono e funziona anche senza campo.\n\n" +
                        "Skin vere: si scaricano da un servizio esterno, e per chiederle " +
                        "bisogna dirgli i nomi di chi gioca sul tuo server, ogni volta che " +
                        "la schermata si aggiorna. È l'unica cosa nell'app che esca verso " +
                        "qualcuno che non sei tu."
            )
            .setSingleChoiceItems(opzioni, attuale) { dialogo, quale ->
                Prefs.skinDaInternet = quale == 1
                dialogo.dismiss()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}

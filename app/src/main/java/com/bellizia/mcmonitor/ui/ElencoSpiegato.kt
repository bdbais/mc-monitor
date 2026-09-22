package com.bellizia.mcmonitor.ui

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Una scelta fra tante, con scritto sopra perche'.
 *
 * Esiste per un difetto che si vede solo a schermo e mai nel codice: un
 * `MaterialAlertDialogBuilder` con `setMessage` **e** `setItems` mostra il
 * messaggio e **butta via l'elenco**. Compila, non avvisa, e il risultato e'
 * una finestra che fa una domanda e non offre nessuna risposta -- con l'unico
 * bottone disponibile che e' «Lascia stare».
 *
 * Succedeva in due punti: quale porta e' la mappa, e in che giorno del mese
 * fare il backup. Tutte e due le domande erano senza risposte possibili.
 *
 * Qui la spiegazione e' un testo e le scelte sono bottoni veri, dentro la
 * stessa area: due cose che non si contendono lo stesso posto. E la
 * spiegazione si puo' tenere, che era il motivo per cui era stata scritta.
 */
object ElencoSpiegato {

    fun mostra(
        ctx: Context,
        titolo: String,
        nota: String,
        voci: List<String>,
        annulla: String = "Annulla",
        scelta: (Int) -> Unit,
    ) {
        val colonna = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 10, 60, 10)
            addView(TextView(ctx).apply {
                text = nota
                textSize = 14f
                setPadding(0, 0, 0, 24)
            })
        }
        val dialogo = MaterialAlertDialogBuilder(ctx)
            .setTitle(titolo)
            .setView(ScrollView(ctx).apply { addView(colonna) })
            .setNegativeButton(annulla, null)
            .create()
        voci.forEachIndexed { quale, voce ->
            colonna.addView(MaterialButton(ctx).apply {
                text = voce
                setOnClickListener {
                    dialogo.dismiss()
                    scelta(quale)
                }
            })
        }
        dialogo.show()
    }
}

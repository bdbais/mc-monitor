package com.bellizia.mcmonitor.ui

import android.content.Context
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Mostra una pagina di aiuto in un dialogo scorrevole. */
object HelpDialog {

    fun show(context: Context, page: Help.Page) {
        val view = TextView(context).apply {
            text = page.body
            textSize = 14f
            setLineSpacing(6f, 1f)
            setTextIsSelectable(true)
            setPadding(48, 32, 48, 16)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(page.title)
            .setView(ScrollView(context).apply { addView(view) })
            .setPositiveButton("Ho capito", null)
            .show()
    }
}

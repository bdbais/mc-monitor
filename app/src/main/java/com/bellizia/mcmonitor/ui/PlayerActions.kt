package com.bellizia.mcmonitor.ui

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.bellizia.mcmonitor.databinding.DialogCoordsBinding
import com.bellizia.mcmonitor.databinding.DialogPlayerBinding
import com.bellizia.mcmonitor.lgsm.PlayerPos
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.roundToInt

/**
 * Pannello con tutte le operazioni su un singolo giocatore.
 * Non parla direttamente con il server: delega l'invio a [run], così la scheda
 * Giocatori resta l'unica a gestire progress e aggiornamenti.
 */
class PlayerActions(
    private val fragment: Fragment,
    private val name: String,
    private val pos: PlayerPos?,
    private val online: Boolean,
    private val onlineNames: List<String>,
    private val run: (command: String, feedback: String) -> Unit,
    private val showOnMap: (String) -> Unit,
    private val showChat: (String) -> Unit
) {

    private val context: Context get() = fragment.requireContext()

    fun show() {
        val b = DialogPlayerBinding.inflate(fragment.layoutInflater)
        val sheet = BottomSheetDialog(context)
        sheet.setContentView(b.root)

        b.name.text = name
        b.subtitle.text = when {
            pos != null -> "X ${pos.x.roundToInt()}  Y ${pos.y.roundToInt()}  Z ${pos.z.roundToInt()} · ${pos.shortDimension}"
            online -> "online, posizione non disponibile"
            else -> "non collegato: restano solo i comandi che non richiedono la presenza"
        }

        // Senza il giocatore in gioco questi comandi verrebbero rifiutati dal server.
        b.btnMap.isEnabled = pos != null
        b.btnTeleport.isEnabled = online
        b.btnGamemode.isEnabled = online
        b.btnSpawnpoint.isEnabled = pos != null
        b.btnKick.isEnabled = online

        fun act(command: String, feedback: String) {
            sheet.dismiss()
            run(command, feedback)
        }

        b.btnMap.setOnClickListener { sheet.dismiss(); showOnMap(name) }
        b.btnChat.setOnClickListener { sheet.dismiss(); showChat(name) }
        b.btnTeleport.setOnClickListener { sheet.dismiss(); teleportMenu() }
        b.btnGamemode.setOnClickListener { sheet.dismiss(); gamemodeMenu() }
        b.btnSpawnpoint.setOnClickListener {
            val p = pos ?: return@setOnClickListener
            act(
                "spawnpoint $name ${p.x.roundToInt()} ${p.y.roundToInt()} ${p.z.roundToInt()}",
                "Punto di rinascita di $name aggiornato"
            )
        }

        b.btnWhitelistAdd.setOnClickListener { act("whitelist add $name", "$name aggiunto alla whitelist") }
        b.btnWhitelistRemove.setOnClickListener { act("whitelist remove $name", "$name rimosso dalla whitelist") }
        b.btnOp.setOnClickListener { act("op $name", "$name è ora operatore") }
        b.btnDeop.setOnClickListener { act("deop $name", "$name non è più operatore") }
        b.btnPardon.setOnClickListener { act("pardon $name", "Ban rimosso per $name") }

        b.btnKick.setOnClickListener {
            sheet.dismiss()
            askReason("Espelli $name", "Motivo (facoltativo)") { reason ->
                run("kick $name $reason".trim(), "$name espulso")
            }
        }
        b.btnBan.setOnClickListener {
            sheet.dismiss()
            askReason("Banna $name", "Motivo (facoltativo)") { reason ->
                run("ban $name $reason".trim(), "$name bannato")
            }
        }

        sheet.show()
    }

    // ------------------------------------------------------------- sotto-menu

    private fun teleportMenu() {
        val others = onlineNames.filter { !it.equals(name, ignoreCase = true) }
        val options = buildList {
            add("A coordinate…" to { coordinatesDialog() })
            if (others.isNotEmpty()) {
                add("Verso un altro giocatore…" to { pickPlayer(others, "Porta $name da:") { target ->
                    run("tp $name $target", "$name teletrasportato da $target")
                } })
                add("Porta qui un altro giocatore…" to { pickPlayer(others, "Chi portare da $name:") { target ->
                    run("tp $target $name", "$target teletrasportato da $name")
                } })
            }
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Teletrasporto")
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun coordinatesDialog() {
        val b = DialogCoordsBinding.inflate(fragment.layoutInflater)
        pos?.let {
            b.x.setText(it.x.roundToInt().toString())
            b.y.setText(it.y.roundToInt().toString())
            b.z.setText(it.z.roundToInt().toString())
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Teletrasporta $name")
            .setView(b.root)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Teletrasporta") { _, _ ->
                val x = b.x.text?.toString()?.trim()
                val y = b.y.text?.toString()?.trim()
                val z = b.z.text?.toString()?.trim()
                if (x.isNullOrBlank() || y.isNullOrBlank() || z.isNullOrBlank()) {
                    fragment.toast("Servono tutte e tre le coordinate")
                } else {
                    run("tp $name $x $y $z", "$name teletrasportato in $x $y $z")
                }
            }
            .show()
    }

    private fun pickPlayer(players: List<String>, title: String, onPick: (String) -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setItems(players.toTypedArray()) { _, which -> onPick(players[which]) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun gamemodeMenu() {
        val modes = listOf(
            "Sopravvivenza" to "survival",
            "Creativa" to "creative",
            "Avventura" to "adventure",
            "Spettatore" to "spectator"
        )
        MaterialAlertDialogBuilder(context)
            .setTitle("Modalità di gioco")
            .setItems(modes.map { it.first }.toTypedArray()) { _, which ->
                run("gamemode ${modes[which].second} $name", "$name in modalità ${modes[which].first.lowercase()}")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun askReason(title: String, hint: String, onConfirm: (String) -> Unit) {
        val input = EditText(context).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        val container = FrameLayout(context).apply {
            setPadding(60, 20, 60, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Conferma") { _, _ ->
                onConfirm(input.text?.toString()?.trim().orEmpty())
            }
            .show()
    }
}

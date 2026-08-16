package com.bellizia.mcmonitor.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.MainActivity
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.data.Privacy
import com.bellizia.mcmonitor.databinding.FragmentPlayersBinding
import com.bellizia.mcmonitor.databinding.ItemPlayerBinding
import com.bellizia.mcmonitor.databinding.ItemWaitingBinding
import com.bellizia.mcmonitor.lgsm.ChatMessage
import com.bellizia.mcmonitor.lgsm.JoinAttempt
import com.bellizia.mcmonitor.lgsm.PlayerEntry
import com.bellizia.mcmonitor.lgsm.PlayerPos
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Scheda "Giocatori": chi è online (con posizione), whitelist e lista ban. */
class PlayersFragment : Fragment() {

    private var _b: FragmentPlayersBinding? = null
    private val b get() = _b!!

    private var lastNames: List<String> = emptyList()
    private var lastPositions: List<PlayerPos> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentPlayersBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.swipe.setOnRefreshListener { refresh() }
        b.btnWhitelistAdd.setOnClickListener {
            withName(b.whitelistInput.text?.toString()) { name ->
                command("whitelist add $name", "Aggiunto $name alla whitelist")
                b.whitelistInput.setText("")
            }
        }
        b.btnBanAdd.setOnClickListener {
            withName(b.banInput.text?.toString()) { name ->
                command("ban $name", "$name bannato")
                b.banInput.setText("")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.load().isComplete) refresh()
    }

    private fun refresh() {
        if (!Prefs.load().isComplete) {
            b.swipe.isRefreshing = false
            b.onlineTitle.text = "Online — server non configurato"
            return
        }
        b.swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val lists = runCatching { McRepository.whitelist() to McRepository.banlist() }
            val online = runCatching { McRepository.online(withPositions = true) }
            val bind = _b ?: return@launch

            lists.onSuccess { (white, bans) ->
                renderEntries(bind.whitelistList, white, "whitelist remove", "Rimuovi", "whitelist vuota")
                renderEntries(bind.banList, bans, "pardon", "Sbanna", "nessun giocatore bannato")
                bind.whitelistTitle.text = "Whitelist (${white.size})"
                bind.banTitle.text = "Ban (${bans.size})"
                loadWaitingList(white, bans)
            }.onFailure { toast(it.userMessage()) }

            online.onSuccess { snap ->
                lastNames = snap.names
                lastPositions = snap.positions
                val max = snap.maxPlayers?.let { "/$it" } ?: ""
                bind.onlineTitle.text = "Online (${snap.names.size}$max)"
                renderOnline(snap.names, snap.positions)
            }.onFailure {
                bind.onlineTitle.text = "Online — errore"
                bind.onlineList.removeAllViews()
                addEmpty(bind.onlineList, it.userMessage())
            }

            bind.swipe.isRefreshing = false
        }
    }

    /**
     * Lista d'attesa: chi risulta nei log come tentativo di accesso ma non compare
     * né in whitelist.json né in banned-players.json. Non serve memorizzarla:
     * appena decidi, il nome finisce in uno dei due file e sparisce da qui.
     */
    private fun loadWaitingList(white: List<PlayerEntry>, bans: List<PlayerEntry>) {
        val known = (white + bans).map { it.name.lowercase() }.toSet()
        viewLifecycleOwner.lifecycleScope.launch {
            val attempts = runCatching { McRepository.joinAttempts() }.getOrNull()
            val enforced = runCatching { McRepository.whitelistEnforced() }.getOrNull()
            val bind = _b ?: return@launch
            val waiting = attempts?.filter { it.name.lowercase() !in known }.orEmpty()

            bind.waitingCard.visible(waiting.isNotEmpty())
            bind.waitingTitle.text = "In attesa di approvazione (${waiting.size})"
            bind.waitingHint.text = when (enforced) {
                false -> "Attenzione: sul server white-list=false, quindi chiunque può entrare " +
                        "anche senza essere ammesso qui."
                else -> "Giocatori che hanno provato a entrare e non sono né in whitelist né bannati."
            }
            renderWaiting(waiting)
        }
    }

    private fun renderWaiting(waiting: List<JoinAttempt>) {
        val list = b.waitingList
        list.removeAllViews()
        waiting.forEach { attempt ->
            val row = ItemWaitingBinding.inflate(layoutInflater, list, false)
            row.name.text = Privacy.name(attempt.name)
            row.subtitle.text = buildString {
                append(attempt.description)
                if (attempt.stamp.isNotBlank()) append(" · ultimo tentativo ${attempt.stamp}")
                if (attempt.uuid.isNotBlank()) append(" · ${attempt.uuid.take(8)}")
            }
            row.btnWhitelist.setOnClickListener {
                command("whitelist add ${attempt.name}", "${attempt.name} ammesso")
            }
            row.btnBan.setOnClickListener {
                command("ban ${attempt.name}", "${attempt.name} bannato")
            }
            row.root.setOnClickListener { playerActions(attempt.name) }
            list.addView(row.root)
        }
    }

    private fun renderOnline(names: List<String>, positions: List<PlayerPos>) {
        val list = b.onlineList
        list.removeAllViews()
        if (names.isEmpty()) {
            addEmpty(list, "nessun giocatore collegato")
            return
        }
        val byName = positions.associateBy { it.name }
        names.forEach { name ->
            val row = ItemPlayerBinding.inflate(layoutInflater, list, false)
            val pos = byName[name]
            row.name.text = Privacy.name(name)
            row.subtitle.text = if (pos == null) {
                "posizione non disponibile"
            } else {
                "X ${pos.x.roundToInt()}  Y ${pos.y.roundToInt()}  Z ${pos.z.roundToInt()} · ${pos.shortDimension}"
            }
            row.action.text = "Azioni"
            row.action.setOnClickListener { playerActions(name) }
            row.root.setOnClickListener { playerActions(name) }
            list.addView(row.root)
        }
    }

    private fun renderEntries(
        list: LinearLayout,
        entries: List<PlayerEntry>,
        action: String,
        actionLabel: String,
        emptyText: String
    ) {
        list.removeAllViews()
        if (entries.isEmpty()) {
            addEmpty(list, emptyText)
            return
        }
        entries.forEach { entry ->
            val row = ItemPlayerBinding.inflate(layoutInflater, list, false)
            row.name.text = Privacy.name(entry.name)
            row.subtitle.text = listOfNotNull(
                entry.reason.takeIf { it.isNotBlank() && it != "Banned by an operator." },
                entry.created.takeIf { it.isNotBlank() },
                entry.uuid.takeIf { it.isNotBlank() }?.take(8)
            ).joinToString(" · ").ifBlank { "—" }
            row.action.text = actionLabel
            row.action.setOnClickListener {
                command("$action ${entry.name}", "${entry.name}: $actionLabel eseguito")
            }
            row.root.setOnClickListener { playerActions(entry.name) }
            list.addView(row.root)
        }
    }

    private fun addEmpty(list: LinearLayout, text: String) {
        val row = ItemPlayerBinding.inflate(layoutInflater, list, false)
        row.name.text = text
        row.subtitle.visible(false)
        row.action.visible(false)
        list.addView(row.root)
    }

    private fun playerActions(name: String) {
        if (!isAdded) return
        PlayerActions(
            fragment = this,
            name = name,
            pos = lastPositions.firstOrNull { it.name.equals(name, ignoreCase = true) },
            online = lastNames.any { it.equals(name, ignoreCase = true) },
            onlineNames = lastNames,
            run = { cmd, feedback -> command(cmd, feedback) },
            showOnMap = { player -> (requireActivity() as MainActivity).showPlayerOnMap(player) },
            showChat = { player -> showChat(player) }
        ).show()
    }

    /** Chat e comandi del giocatore, presi dai log del server con data e ora. */
    private fun showChat(player: String) {
        if (!configured()) return
        val text = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(40, 24, 40, 8)
            text = "Lettura dei log…"
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Chat di $player")
            .setView(ScrollView(requireContext()).apply { addView(text) })
            .setPositiveButton("Chiudi", null)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { McRepository.chat(player) }
            if (!dialog.isShowing) return@launch
            text.text = result.fold(
                onSuccess = { messages -> formatChat(messages) },
                onFailure = { "Errore nella lettura dei log:\n${it.userMessage()}" }
            )
        }
    }

    private fun formatChat(messages: List<ChatMessage>): String {
        if (messages.isEmpty()) {
            return "Nessun messaggio trovato nei log disponibili.\n\n" +
                    "Minecraft comprime i log dei giorni passati: l'app li legge se sul " +
                    "server è installato zgrep (pacchetto gzip), altrimenti vede solo latest.log."
        }
        return buildString {
            var day = ""
            messages.forEach { m ->
                if (m.date != day) {
                    day = m.date
                    if (isNotEmpty()) append('\n')
                    append("== $day ==\n")
                }
                append(m.time.ifBlank { "--:--:--" })
                append("  ")
                append(if (m.isCommand) "/${m.text}" else m.text)
                append('\n')
            }
            append("\n${messages.size} righe · ")
            append("${messages.count { !it.isCommand }} messaggi, ${messages.count { it.isCommand }} comandi")
        }
    }

    private fun withName(raw: String?, block: (String) -> Unit) {
        val name = raw?.trim().orEmpty()
        if (name.isEmpty()) {
            toast("Inserisci un nome giocatore")
            return
        }
        if (!configured()) return
        block(name)
    }

    private fun command(command: String, success: String) {
        if (!configured()) return
        b.swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { McRepository.send(command) }
                .onSuccess {
                    toast(success)
                    delay(1_500)
                    refresh()
                }
                .onFailure {
                    toast(it.userMessage())
                    _b?.swipe?.isRefreshing = false
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

package com.bellizia.mcmonitor.ui

import android.content.Intent
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
import com.bellizia.mcmonitor.lgsm.Provvedimenti
import com.bellizia.mcmonitor.lgsm.EsitoSuServer
import com.bellizia.mcmonitor.data.ServerConfig
import com.bellizia.mcmonitor.data.PresenceRepository
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
        b.btnPosta.setOnClickListener { apriPosta() }
        b.btnWhitelistAdd.setOnClickListener {
            withName(b.whitelistInput.text?.toString()) { name ->
                esegui("whitelist add $name", "Aggiunto $name alla whitelist")
                b.whitelistInput.setText("")
            }
        }
        b.btnBanAdd.setOnClickListener {
            withName(b.banInput.text?.toString()) { name ->
                esegui("ban $name", "$name bannato")
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
            renderWhitelistSwitch(enforced)
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

    /**
     * Server chiuso o aperto. L'elenco della whitelist da solo non decide niente:
     * se sul server white-list e' false, entra chiunque anche senza esserci
     * dentro, e questo interruttore e' l'unico posto dove si vede e si cambia.
     */
    private fun renderWhitelistSwitch(enforced: Boolean?) {
        val bind = _b ?: return
        val attiva = enforced == true
        bind.whitelistEnforced.setOnCheckedChangeListener(null)
        bind.whitelistEnforced.isChecked = attiva
        bind.whitelistEnforced.isEnabled = enforced != null
        bind.whitelistStato.text = when (enforced) {
            true -> "Server chiuso: entrano solo i nomi in elenco."
            false -> "Server aperto: entra chiunque conosca l'indirizzo, l'elenco non viene guardato."
            else -> "Non riesco a leggere white-list da server.properties."
        }
        bind.whitelistEnforced.setOnCheckedChangeListener { _, checked ->
            if (checked == attiva) return@setOnCheckedChangeListener
            confermaWhitelist(checked)
        }
    }

    private fun confermaWhitelist(attivare: Boolean) {
        val titolo = if (attivare) "Chiudere il server?" else "Aprire il server a tutti?"
        val messaggio = if (attivare) {
            "Da adesso entrano solo i giocatori in whitelist. Chi sta giocando e non c'e' " +
                    "dentro viene disconnesso dal server."
        } else {
            "Da adesso entra chiunque conosca l'indirizzo, anche chi non e' in elenco. " +
                    "La whitelist resta salvata e si puo' riattivare quando vuoi."
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titolo)
            .setMessage(messaggio)
            .setNegativeButton("Annulla") { _, _ -> renderWhitelistSwitch(!attivare) }
            .setOnCancelListener { renderWhitelistSwitch(!attivare) }
            .setPositiveButton(if (attivare) "Chiudi" else "Apri") { _, _ ->
                applicaWhitelist(attivare)
            }
            .show()
    }

    /**
     * Accende o spegne la whitelist scrivendo anche il file.
     *
     * Il comando da console cambia il server che sta girando adesso, ma questo
     * interruttore lo legge da `white-list` in server.properties, che è il file
     * che decide come riparte. Mandando solo il comando, questa schermata e quella
     * delle impostazioni finivano per dire due cose diverse a un tocco di
     * distanza, e dopo un riavvio vinceva quella che nessuno aveva scelto.
     */
    private fun applicaWhitelist(attivare: Boolean) {
        val valore = if (attivare) "true" else "false"
        viewLifecycleOwner.lifecycleScope.launch {
            val esito = runCatching {
                McRepository.setProperties(mapOf("white-list" to valore), versione = null)
            }
            if (!isAdded) return@launch
            esito.fold(
                onSuccess = {
                    toast(
                        if (attivare) "Server chiuso: solo la whitelist"
                        else "Server aperto a tutti"
                    )
                    refresh()
                },
                onFailure = {
                    toast("Non riuscito: ${it.userMessage()}")
                    renderWhitelistSwitch(!attivare)
                }
            )
        }
    }

    private fun renderWaiting(waiting: List<JoinAttempt>) {
        val list = b.waitingList
        list.removeAllViews()
        waiting.forEach { attempt ->
            val row = ItemWaitingBinding.inflate(layoutInflater, list, false)
            // Qui l'amministratore deve decidere se ammettere o bannare: un nome
            // mascherato non si riconosce, e la scelta si fa proprio sul nome.
            row.name.text = attempt.name
            row.subtitle.text = buildString {
                append(attempt.description)
                if (attempt.stamp.isNotBlank()) append(" · ultimo tentativo ${attempt.stamp}")
                if (attempt.uuid.isNotBlank()) append(" · ${attempt.uuid.take(8)}")
            }
            row.btnWhitelist.setOnClickListener {
                commandWithNote(
                    "whitelist add ${attempt.name}",
                    "${attempt.name} ammesso",
                    attempt.name,
                    "ammesso"
                )
            }
            row.btnBan.setOnClickListener {
                commandWithNote("ban ${attempt.name}", "${attempt.name} bannato", attempt.name, "bannato")
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
            run = { cmd, feedback -> esegui(cmd, feedback) },
            showOnMap = { player -> (requireActivity() as MainActivity).showPlayerOnMap(player) },
            showChat = { player -> showChat(player) },
            scriviPosta = { player -> apriPosta(player) }
        ).show()
    }

    /** La posta, gia' puntata su un nome se si arriva dal pannello di un giocatore. */
    private fun apriPosta(player: String? = null) {
        if (!isAdded) return
        startActivity(
            Intent(requireContext(), PostaActivity::class.java)
                .putExtra(PostaActivity.EXTRA_GIOCATORE, player)
        )
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

    /**
     * Ban e whitelist non sono gesti neutri: chi arriva dopo si chiede perche'.
     * Dopo il comando si puo' lasciare due righe agli altri amministratori, che
     * restano legate al giocatore e compaiono nel suo pannello.
     */
    /** Il nome del giocatore dentro un comando di console. */
    private fun name(comando: String) = comando.trim().substringAfterLast(' ')

    /**
     * L'unica porta da cui passano i comandi su un giocatore.
     *
     * Ce n'erano due: il pannello del giocatore e i due campi in cima alla
     * scheda. Quelli in cima scavalcavano la domanda sugli altri server, cioe'
     * proprio dalla strada piu' corta per bannare qualcuno il ban restava su un
     * mondo solo.
     */
    private fun esegui(comando: String, feedback: String) {
        val tipo = Provvedimenti.tipoDi(comando)
        val altri = Provvedimenti.altriServer(Prefs.servers(), Prefs.load())
        if (tipo != null && altri.isNotEmpty()) {
            // Ammettere e bannare riguardano tutti i mondi di chi ne ha piu' di
            // uno: si chiede prima di farlo qui, non dopo.
            chiediDoveApplicare(comando, feedback, tipo, altri)
        } else {
            soloQui(comando, feedback)
        }
    }

    // ------------------------------------ lo stesso provvedimento altrove

    /**
     * Chiede su quali altri server ripetere il provvedimento.
     *
     * Si chiede prima di eseguire, e non dopo: chi banna un vandalo lo vuole
     * fuori da tutti i suoi mondi nello stesso momento, non fra due schermate.
     * Quelli sullo stesso computer arrivano gia' spuntati, perche' quasi sempre
     * sono la stessa comunita'.
     */
    private fun chiediDoveApplicare(
        comando: String,
        feedback: String,
        tipo: Provvedimenti.Tipo,
        altri: List<ServerConfig>
    ) {
        if (!isAdded) return
        val ctx = requireContext()
        val preScelti = Provvedimenti.daSpuntare(altri, Prefs.load())

        // Le caselle stanno in una vista fatta a mano, non in setMultiChoiceItems:
        // un AlertDialog non puo' avere insieme un messaggio e una lista, e la
        // lista sparirebbe in silenzio. Provato: il dialogo compariva senza
        // nessuna casella, e "Applica" non applicava niente.
        val colonna = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val m = (20 * resources.displayMetrics.density).toInt()
            setPadding(m, m / 2, m, 0)
        }
        colonna.addView(TextView(ctx).apply {
            textSize = 13f
            text = "Su ${Prefs.load().displayName} lo faccio comunque. Su quali altri?\n\n" +
                    "Devono essere accesi: il comando passa dalla console, e per mettere " +
                    "qualcuno in whitelist il server deve chiedere a Mojang chi è."
        })
        val caselle = altri.mapIndexed { i, srv ->
            android.widget.CheckBox(ctx).apply {
                text = srv.displayName
                isChecked = preScelti[i]
                colonna.addView(this)
            }
        }

        colonna.addView(TextView(ctx).apply {
            textSize = 12f
            setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
            text = "Tocca qui per sapere cosa non si propaga e perche'."
            setOnClickListener { HelpDialog.show(ctx, Help.PROVVEDIMENTI) }
        })

        MaterialAlertDialogBuilder(ctx)
            .setTitle("${tipo.etichetta}: dove?")
            .setView(android.widget.ScrollView(ctx).apply { addView(colonna) })
            .setNeutralButton("Solo qui") { _, _ -> soloQui(comando, feedback) }
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Applica") { _, _ ->
                val bersagli = altri.filterIndexed { i, _ -> caselle[i].isChecked }
                if (bersagli.isEmpty()) soloQui(comando, feedback)
                else applicaSuTutti(comando, feedback, tipo, bersagli)
            }
            .show()
    }

    private fun soloQui(comando: String, feedback: String) {
        when {
            comando.startsWith("ban ") -> commandWithNote(comando, feedback, name(comando), "bannato")
            comando.startsWith("whitelist add ") ->
                commandWithNote(comando, feedback, name(comando), "ammesso")
            else -> command(comando, feedback)
        }
    }

    private fun applicaSuTutti(
        comando: String,
        feedback: String,
        tipo: Provvedimenti.Tipo,
        altri: List<ServerConfig>
    ) {
        if (!configured()) return
        val giocatore = name(comando)
        b.swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            // Il server aperto adesso per primo: e' quello che l'utente sta
            // guardando, ed e' l'unico di cui vedra' cambiare le liste.
            val tutti = listOf(Prefs.load()) + altri
            val esiti = McRepository.mandaSuPiuServer(comando, tutti)
            if (!isAdded) return@launch
            _b?.swipe?.isRefreshing = false
            mostraEsiti(tipo, giocatore, comando, esiti)
            refresh()
        }
    }

    /**
     * Il riassunto, con la strada per tornare indietro.
     *
     * Sbagliare bersaglio su cinque server insieme deve costare un tocco, non
     * cinque: senza il pulsante, si dovrebbe rifare il giro a mano ricordandosi
     * quali erano andati a buon fine.
     */
    private fun mostraEsiti(
        tipo: Provvedimenti.Tipo,
        giocatore: String,
        comando: String,
        esiti: List<EsitoSuServer>
    ) {
        if (!isAdded) return
        val riusciti = esiti.filter { it.riuscito }.map { it.server }
        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setTitle(tipo.etichetta)
            .setMessage(Provvedimenti.riassunto(tipo, giocatore, esiti))
            .setPositiveButton("Ho capito", null)
        if (riusciti.isNotEmpty()) {
            dialogo.setNegativeButton("Annulla tutto") { _, _ ->
                disfa(tipo, giocatore, riusciti)
            }
        }
        // La nota si chiede DOPO, quando il riassunto e' stato letto e chiuso.
        // Aprendola subito si metteva sopra al riassunto: l'utente non vedeva
        // su quali server era andata, e "Annulla tutto" restava irraggiungibile.
        // E non si chiede affatto se non e' riuscito da nessuna parte: sarebbe
        // una nota su un provvedimento che non esiste.
        val chiedeNota = riusciti.isNotEmpty() &&
                (tipo == Provvedimenti.Tipo.BAN || tipo == Provvedimenti.Tipo.AMMETTI)
        dialogo.setOnDismissListener {
            if (chiedeNota) {
                nota(giocatore, if (tipo == Provvedimenti.Tipo.BAN) "bannato" else "ammesso")
            }
        }
        dialogo.show()
    }

    private fun disfa(
        tipo: Provvedimenti.Tipo,
        giocatore: String,
        dove: List<ServerConfig>
    ) {
        b.swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val esiti = McRepository.mandaSuPiuServer(
                Provvedimenti.perDisfare(tipo, giocatore), dove
            )
            if (!isAdded) return@launch
            _b?.swipe?.isRefreshing = false
            val no = esiti.count { !it.riuscito }
            toast(
                if (no == 0) "Annullato su tutti."
                else "Annullato su ${esiti.size - no} server su ${esiti.size}."
            )
            refresh()
        }
    }

    private fun commandWithNote(command: String, success: String, player: String, azione: String) {
        command(command, success)
        nota(player, azione)
    }

    /** La nota per gli altri amministratori: perche' quel nome sta in quella lista. */
    private fun nota(player: String, azione: String) {
        if (!isAdded) return
        val campo = android.widget.EditText(requireContext()).apply {
            hint = "perche' (facoltativo)"
            setSingleLine()
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nota su $player")
            .setMessage("Gli altri amministratori la vedranno nei messaggi e aprendo $player.")
            .setView(campo)
            .setNegativeButton("Senza nota", null)
            .setPositiveButton("Salva") { _, _ ->
                val nota = campo.text?.toString()?.trim().orEmpty()
                if (nota.isBlank()) return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        PresenceRepository.send("$azione $player: $nota", about = player)
                    }.onFailure { toast("Nota non salvata: ${it.userMessage()}") }
                }
            }
            .show()
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

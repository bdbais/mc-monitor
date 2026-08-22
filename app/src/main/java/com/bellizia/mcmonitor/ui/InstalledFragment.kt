package com.bellizia.mcmonitor.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.R
import com.bellizia.mcmonitor.data.DiscoveryRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.data.Privacy
import com.bellizia.mcmonitor.data.ServerConfig
import com.bellizia.mcmonitor.databinding.FragmentInstalledBinding
import com.bellizia.mcmonitor.databinding.ItemDiscoveredBinding
import com.bellizia.mcmonitor.lgsm.DiscoveredServer
import com.bellizia.mcmonitor.lgsm.Discovery
import com.bellizia.mcmonitor.lgsm.LgsmUpdate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Secondo passo: i mondi già installati sul computer.
 *
 * L'utente non deve sapere in che cartella vivono né come si chiama lo script che
 * li avvia: l'app fruga nella home e mostra quello che trova. Il profilo vero e
 * proprio nasce solo quando se ne apre uno, e se esiste già viene riusato con
 * tutte le sue impostazioni (RCON, notifiche, mappa).
 */
class InstalledFragment : Fragment() {

    private var _b: FragmentInstalledBinding? = null
    private val b get() = _b!!

    private var found: List<DiscoveredServer> = emptyList()
    private var searched = false
    private var latestLgsm: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentInstalledBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.btnHelp.setOnClickListener { HelpDialog.show(requireContext(), Help.INSTALLED) }
        b.btnNuovo.setOnClickListener { (activity as? HomeActivity)?.createServer() }
        b.swipe.setOnRefreshListener { search() }

        render()
        if (!searched) search()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun search() {
        val account = Prefs.account()
        if (!account.hasCredentials) {
            b.swipe.isRefreshing = false
            render()
            return
        }

        searched = true
        b.attesa.visible(true)
        b.btnRicerca.isEnabled = false
        b.intestazione.text = "Cerco i mondi dentro ${Privacy.host(account.host)}…"
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { DiscoveryRepository.discover(account) }
            val bind = _b ?: return@launch
            bind.attesa.visible(false)
            bind.btnRicerca.isEnabled = true
            bind.swipe.isRefreshing = false
            result.fold(
                onSuccess = {
                    found = it
                    render()
                    checkLgsmVersion()
                },
                onFailure = { error ->
                    found = emptyList()
                    render()
                    bind.intestazione.text = "Ricerca non riuscita: ${error.userMessage()}"
                    bind.intestazione.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.danger)
                    )
                }
            )
        }
    }

    /**
     * Controllo dell'infrastruttura: gli script LinuxGSM del computer invecchiano
     * per conto loro, e quando restano indietro i comandi falliscono con errori
     * che non dicono mai qual è la vera causa.
     */
    private fun checkLgsmVersion() {
        if (found.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            latestLgsm = DiscoveryRepository.latestLgsmVersion()
            if (_b != null) renderMaintenance()
        }
    }

    private fun render() {
        val bind = _b ?: return
        val account = Prefs.account()
        val connesso = account.hasCredentials

        bind.intestazione.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_dim))
        bind.intestazione.text = when {
            !connesso -> "Prima serve il collegamento al computer."
            found.isEmpty() && searched -> "Nessun mondo trovato su ${Privacy.host(account.host)}."
            found.isEmpty() -> "Collegato a ${Privacy.host(account.host)}."
            else -> "Trovati ${found.size} su ${Privacy.host(account.host)}. " +
                    "Tocca Apri su quello che vuoi comandare."
        }

        val conflitti = Discovery.portConflicts(found)
        bind.elenco.removeAllViews()
        found.forEachIndexed { index, server ->
            bind.elenco.addView(row(server, conflitti[index].orEmpty()))
        }

        val nessuno = connesso && searched && found.isEmpty()
        bind.vuoto.visible(!connesso || nessuno)
        bind.vuoto.text = if (!connesso) {
            "Apri il menu in alto a sinistra e scegli Collegamento al server Linux."
        } else {
            "Su questo computer non c'è ancora nessun mondo di Minecraft installato, " +
                    "oppure sta in una cartella fuori dalla tua home.\n\n" +
                    "Puoi crearne uno nuovo qui sotto: ci vuole un po' di tempo e di connessione."
        }

        // Senza collegamento cercare non ha senso: il pulsante riporta al passo 1
        // invece di lasciare davanti a un tasto che non fa niente.
        bind.btnRicerca.text = if (connesso) "Trova servers Minecraft" else "Vai al collegamento"
        bind.btnRicerca.setOnClickListener {
            if (connesso) search() else (activity as? HomeActivity)?.show(R.id.nav_connessione)
        }
        bind.btnNuovo.visible(connesso)
        renderMaintenance()
    }

    private fun renderMaintenance() {
        val bind = _b ?: return
        val vecchi = LgsmUpdate.outdatedServers(found, latestLgsm)
        bind.manutenzione.visible(vecchi.isNotEmpty())
        if (vecchi.isEmpty()) return

        bind.manutenzioneTesto.text = LgsmUpdate.banner(vecchi, latestLgsm)
        bind.btnAggiornaLgsm.setOnClickListener { updateLgsm(vecchi) }
    }

    private fun row(server: DiscoveredServer, porteInConflitto: Set<String>): View {
        val item = ItemDiscoveredBinding.inflate(layoutInflater, b.elenco, false)
        item.nome.text = server.displayName
        item.riepilogo.text = riepilogo(server, porteInConflitto)
        item.percorso.text = shortPath(server.directory)
        item.stato.setBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                if (server.running) R.color.grass else R.color.text_dim
            )
        )

        item.mod.visible(server.modded)
        if (server.modded) {
            item.mod.text = server.modsLabel
            item.mod.setOnClickListener { showMods(server) }
        }

        item.card.setOnClickListener { open(server) }
        item.btnApri.setOnClickListener { open(server) }
        item.btnCancella.setOnClickListener { confirmDelete(server) }
        return item.root
    }

    /**
     * Due server sulla stessa porta non possono stare accesi insieme: il secondo
     * parte e muore subito. La porta in rosso lo dice prima che succeda, e vale
     * anche per RCON, che pesca dallo stesso mucchio di numeri.
     */
    private fun riepilogo(server: DiscoveredServer, porteInConflitto: Set<String>): CharSequence {
        val testo = server.summary
        if (porteInConflitto.isEmpty()) return testo

        val rosso = ContextCompat.getColor(requireContext(), R.color.danger)
        val spannable = SpannableString(testo)
        porteInConflitto.forEach { porta ->
            listOf("porta $porta", "RCON $porta").forEach { pezzo ->
                val inizio = testo.indexOf(pezzo)
                if (inizio >= 0) {
                    val fine = inizio + pezzo.length
                    spannable.setSpan(ForegroundColorSpan(rosso), inizio, fine, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(StyleSpan(Typeface.BOLD), inizio, fine, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return spannable
    }

    /** L'elenco delle mod arriva già con la scansione: non serve un altro giro. */
    private fun showMods(server: DiscoveredServer) {
        if (!isAdded) return
        val testo = if (server.mods.isEmpty()) {
            "Il server usa ${server.loader}, ma nella cartella mods non c'è ancora niente."
        } else {
            server.mods.sortedBy { it.lowercase() }.joinToString("\n") { "• $it" }
        }
        val view = TextView(requireContext()).apply {
            text = testo
            textSize = 13f
            setLineSpacing(4f, 1f)
            setTextIsSelectable(true)
            setPadding(48, 32, 48, 16)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${server.displayName} · ${server.modsLabel}")
            .setView(ScrollView(requireContext()).apply { addView(view) })
            .setPositiveButton("Chiudi", null)
            .show()
    }

    /**
     * Cancellare un server significa buttare via il mondo: due conferme, la
     * seconda con il nome da riscrivere a mano. Non è pignoleria — è l'unica
     * operazione dell'app che non si può annullare in nessun modo.
     */
    private fun confirmDelete(server: DiscoveredServer) {
        if (!isAdded) return
        val peso = server.sizeMb?.let { " (%.1f GB)".format(it / 1024.0) }.orEmpty()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancellare ${server.displayName}?")
            .setMessage(
                "Viene cancellata dal computer tutta la cartella ${shortPath(server.directory)}$peso: " +
                        "il mondo, le mod, i backup e la configurazione.\n\n" +
                        "Il server viene prima spento. Non si torna indietro: se il mondo ti " +
                        "interessa, fai prima una copia."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Continua") { _, _ -> confirmDeleteName(server) }
            .show()
    }

    private fun confirmDeleteName(server: DiscoveredServer) {
        if (!isAdded) return
        val campo = EditText(requireContext()).apply {
            hint = server.displayName
            setSingleLine()
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Scrivi ${server.displayName}")
            .setMessage("Per essere sicuri che non sia un tocco per sbaglio, riscrivi il nome del server.")
            .setView(campo)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Cancella per sempre", null)
            .show()
            .also { dialog ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val scritto = campo.text?.toString()?.trim().orEmpty()
                    if (!scritto.equals(server.displayName, ignoreCase = true)) {
                        toast("Il nome non corrisponde")
                    } else {
                        dialog.dismiss()
                        runDelete(server)
                    }
                }
            }
    }

    private fun runDelete(server: DiscoveredServer) {
        val account = Prefs.account()
        val view = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(40, 30, 40, 10)
            text = "Spengo il server e cancello la cartella…"
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancellazione di ${server.displayName}")
            .setView(ScrollView(requireContext()).apply { addView(view) })
            .setCancelable(false)
            .setPositiveButton("Chiudi", null)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { DiscoveryRepository.remove(account, server) }
            dialog.setCancelable(true)
            view.text = result.fold(
                onSuccess = { "Fatto. Il server non c'è più sul computer.\n\n$it" },
                onFailure = { "Non cancellato.\n\n${it.userMessage()}" }
            )
            if (result.isSuccess) {
                // Il profilo sul telefono non ha più un server dietro: via anche lui.
                Prefs.servers().filter { same(it, server, account) }.forEach { Prefs.remove(it.id) }
                found = found.filterNot { it.directory == server.directory && it.script == server.script }
                if (_b != null) render()
            }
        }
    }

    /** Aggiorna gli script LinuxGSM, una istanza alla volta, mostrando a che punto è. */
    private fun updateLgsm(servers: List<DiscoveredServer>) {
        val account = Prefs.account()
        val view = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(40, 30, 40, 10)
            text = "Aggiorno LinuxGSM…"
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Aggiornamento di LinuxGSM")
            .setView(ScrollView(requireContext()).apply { addView(view) })
            .setCancelable(false)
            .setPositiveButton("Chiudi", null)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val report = StringBuilder()
            servers.forEach { server ->
                report.append("— ${server.displayName}\n")
                view.text = report.toString() + "in corso…"
                val esito = runCatching { DiscoveryRepository.updateLgsm(account, server) }
                report.append(
                    esito.fold(
                        onSuccess = { it.lines().takeLast(6).joinToString("\n").ifBlank { "aggiornato" } },
                        onFailure = { "non aggiornato: ${it.userMessage()}" }
                    )
                ).append("\n\n")
                view.text = report.toString()
            }
            dialog.setCancelable(true)
            view.text = report.toString() + "Rifaccio la ricerca per rileggere le versioni."
            if (_b != null) search()
        }
    }

    /**
     * La cartella si mostra come la scrive chi ci lavora: "~/mondo" invece di
     * "/home/mcserver/mondo". Più corta da leggere e senza il nome dell'utente
     * dentro, che in uno screenshot non serve a nessuno.
     */
    private fun shortPath(dir: String): String {
        val home = Regex("^(/home/[^/]+|/root)").find(dir)?.value ?: return dir
        return "~" + dir.removePrefix(home)
    }

    /**
     * Aprire un mondo trovato: se c'era già un profilo per quella cartella lo si
     * riusa, altrimenti se ne crea uno con i dati appena letti dal computer.
     */
    private fun open(server: DiscoveredServer) {
        val account = Prefs.account()
        val existing = Prefs.servers().firstOrNull { same(it, server, account) }
        val base = existing?.withCredentials(account) ?: account
        val profile = Discovery.applyTo(base, server)
        val toSave = if (existing == null) {
            profile.copy(name = server.displayName, createdAt = System.currentTimeMillis())
        } else {
            profile
        }
        Prefs.save(toSave)
        val saved = Prefs.servers().firstOrNull { same(it, server, account) } ?: return
        (activity as? HomeActivity)?.openServer(saved, settings = false)
    }

    /**
     * Stessa istanza: stesso computer, stesso script e stessa cartella. Il
     * percorso salvato può essere scritto con la tilde, quindi si confronta
     * l'ultimo pezzo invece del percorso intero.
     */
    private fun same(profile: ServerConfig, server: DiscoveredServer, account: ServerConfig): Boolean {
        val salvata = profile.lgsmDir.trimEnd('/').substringAfterLast('/')
        val trovata = server.directory.trimEnd('/').substringAfterLast('/')
        return profile.host.equals(account.host, ignoreCase = true) &&
                profile.user == account.user &&
                profile.script == server.script &&
                salvata == trovata
    }
}

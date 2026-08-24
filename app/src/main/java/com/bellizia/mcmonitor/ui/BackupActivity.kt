package com.bellizia.mcmonitor.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TimePicker
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.ActivityBackupBinding
import com.bellizia.mcmonitor.databinding.ItemCopiaBinding
import com.bellizia.mcmonitor.lgsm.Backup
import com.bellizia.mcmonitor.lgsm.BackupState
import com.bellizia.mcmonitor.lgsm.Cadenza
import com.bellizia.mcmonitor.lgsm.Cron
import com.bellizia.mcmonitor.lgsm.Crontab
import com.bellizia.mcmonitor.lgsm.PianoBackup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Le copie di sicurezza del mondo: quando è stata fatta l'ultima, farne una
 * adesso, e farle fare da sole a un orario.
 *
 * La programmazione passa dal cron del computer, perché LinuxGSM non ha un suo
 * modo di farlo. È l'unico punto dell'app che scrive su un file che non è suo:
 * il crontab può contenere righe di qualcun altro, e per questo qui si legge
 * prima, si tiene una copia di com'era, e si rilegge dopo per controllare.
 */
class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding
    private var stato: BackupState? = null
    private var piano: PianoBackup? = null
    private var occupato = false

    private val quando = SimpleDateFormat("d MMM yyyy 'alle' HH:mm", Locale.ITALY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        applyInsets()

        binding.sottotitolo.text = Prefs.load().displayName
        binding.btnHelp.setOnClickListener { HelpDialog.show(this, Help.BACKUP) }
        binding.swipe.setOnRefreshListener { load() }
        binding.btnAdesso.setOnClickListener { confermaAdesso() }
        binding.btnProgramma.setOnClickListener { scegliQuando() }
        binding.btnTogli.setOnClickListener { confermaTogli() }
        binding.btnRipristina.setOnClickListener { confermaRipristino() }
        binding.btnRegistro.setOnClickListener { registro() }
        load()
    }

    private fun load() {
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val copie = runCatching { McRepository.backupState() }
            val crontab = runCatching { McRepository.cronRead() }.getOrNull()
            val demone = runCatching { McRepository.cronDaemon() }.getOrNull()
            binding.swipe.isRefreshing = false

            copie.fold(
                onSuccess = { stato = it; renderCopie(it) },
                onFailure = {
                    binding.ultimo.text = "Non riesco a leggere i backup"
                    binding.dettagli.text = it.userMessage()
                    binding.elenco.removeAllViews()
                }
            )
            renderProgrammazione(crontab, demone)
        }
    }

    private fun renderCopie(s: BackupState) {
        val ultimo = s.last
        binding.ultimo.text = when {
            !s.hasFolder -> "Mai"
            ultimo == null -> "Mai"
            else -> {
                val giorni = s.daysSinceLast ?: 0
                when {
                    giorni == 0L -> "Oggi"
                    giorni == 1L -> "Ieri"
                    else -> "$giorni giorni fa"
                }
            }
        }
        binding.dettagli.text = buildString {
            if (ultimo == null) {
                append("Non c'è nessuna copia in lgsm/backup. ")
                append("Il primo backup lo fai con il pulsante qui sotto.")
            } else {
                append(quando.format(Date(ultimo.epochSeconds * 1000)))
                append(" · ").append(ultimo.sizeLabel)
                append("\n${s.backups.size} copie in tutto, ")
                append(s.spaceLabel(s.usedKb)).append(" occupati")
                if (s.freeKb > 0) append(", ").append(s.spaceLabel(s.freeKb)).append(" liberi sul disco")
                append(".")
            }
        }

        // Se lo spazio libero non basta per un'altra copia grande come l'ultima,
        // il tar si ferma a metà e lascia un archivio rotto che poi conta come
        // "ultimo backup".
        val serve = (ultimo?.sizeBytes ?: 0L) / 1024
        val stretto = ultimo != null && s.freeKb in 1 until (serve * 2)
        binding.avvisoSpazio.visible(stretto)
        if (stretto) {
            binding.avvisoSpazio.text =
                "Lo spazio libero è poco per un'altra copia: se finisce a metà, " +
                        "resta un archivio rotto che sembra un backup buono."
        }

        binding.elenco.removeAllViews()
        s.backups.take(10).forEach { b -> binding.elenco.addView(rigaCopia(b, s)) }
        if (s.backups.size > 10) {
            binding.elenco.addView(TextView(this).apply {
                text = "e altre ${s.backups.size - 10}."
                textSize = 12f
            })
        }
        if (s.backups.isEmpty()) {
            binding.elenco.addView(TextView(this).apply {
                text = "Nessuna."
                textSize = 12f
            })
        }
    }

    private fun renderProgrammazione(crontab: Crontab?, demone: Pair<Boolean, String>?) {
        val cfg = Prefs.load()
        piano = (crontab as? Crontab.Letto)?.let { Cron.leggiPiano(it.testo, cfg) }

        binding.programmazione.text = when {
            crontab is Crontab.Illeggibile -> "Non riesco a leggere il crontab"
            piano != null -> "Sì: ${piano!!.descrizione()}"
            else -> "No, i backup li fai a mano"
        }
        binding.notaProgrammazione.text = when {
            crontab is Crontab.Illeggibile ->
                "${crontab.motivo}\n\nFinché è così l'app non lo tocca: potrebbe cancellare " +
                        "righe che non sono sue."
            piano != null ->
                "La riga è nel crontab dell'utente ${cfg.user}. Tutto il resto di quel file " +
                        "resta com'è."
            else ->
                "Scegli ogni quanto e a che ora: l'app scrive una riga nel crontab del " +
                        "computer, e il backup parte da solo anche a telefono spento."
        }

        val avvisi = buildList {
            demone?.second?.takeIf { it.isNotBlank() }?.let { add(it) }
            (crontab as? Crontab.Letto)?.let { letto ->
                addAll(Cron.avvertenze(letto.testo))
                // Un blocco rimasto di un server rinominato o cancellato continua
                // a girare ogni notte, e l'app non lo riconosce più: non si può
                // toglierlo da qui perché potrebbe essere di un altro telefono,
                // ma nasconderlo sarebbe peggio.
                val orfani = Cron.orfani(letto.testo, Prefs.servers().map { it.slug })
                if (orfani.isNotEmpty()) {
                    add(
                        "Nel crontab ci sono righe scritte da MC Monitor per server che qui non " +
                                "ci sono più (" + orfani.joinToString(", ") { "${it.first} di ${it.second}" } +
                                "). Continuano a girare. Se non servono più vanno tolte a mano " +
                                "sul computer, con crontab -e."
                    )
                }
            }
        }
        binding.avvisoCron.visible(avvisi.isNotEmpty())
        binding.avvisoCron.text = avvisi.joinToString("\n")

        val leggibile = crontab is Crontab.Letto
        binding.btnProgramma.isEnabled = leggibile && demone?.first != false
        binding.btnProgramma.text = if (piano != null) "Cambia orario" else "Programma"
        binding.btnTogli.visible(piano != null && leggibile)
        binding.btnRipristina.visible(Prefs.cronBackup(cfg.id) != null)
    }

    // ------------------------------------------------------------ backup ora

    private fun confermaAdesso() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Fare il backup adesso?")
            .setMessage(
                "Di fabbrica LinuxGSM ferma il server per tutta la copia: chi sta " +
                        "giocando viene disconnesso e rientra quando è finita. Su un mondo " +
                        "grande possono volerci diversi minuti.\n\n" +
                        "Dentro l'archivio finisce tutto il server, compresi i file di " +
                        "configurazione con le password e i token degli avvisi."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Fai il backup") { _, _ -> adesso() }
            .show()
    }

    private fun adesso() {
        if (occupato) return
        occupato = true
        binding.swipe.isRefreshing = true
        binding.btnAdesso.isEnabled = false
        lifecycleScope.launch {
            val esito = runCatching { McRepository.backupNow() }
            occupato = false
            binding.swipe.isRefreshing = false
            binding.btnAdesso.isEnabled = true
            mostra(
                if (esito.isSuccess) "Backup finito" else "Backup non riuscito",
                esito.getOrElse { it.userMessage() }.ifBlank { "Fatto." }
            )
            load()
        }
    }

    // ---------------------------------------------------------- programmare

    /**
     * Ogni quanto e a che ora. Niente sintassi di cron: quella si sbaglia in
     * silenzio, e chi la sbaglia scopre a fine mese che non c'era nessun backup.
     */
    private fun scegliQuando() {
        val partenza = piano ?: PianoBackup(Cadenza.GIORNO, ora = 4, minuto = 0)

        val contenitore = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 8)
        }

        val cadenze = Cadenza.entries
        val gruppo = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        cadenze.forEachIndexed { i, c ->
            gruppo.addView(RadioButton(this).apply {
                id = i
                text = c.etichetta
                isChecked = c == partenza.cadenza
            })
        }
        contenitore.addView(gruppo)

        // Quale giorno: serve solo per settimana e mese, e compare solo allora.
        val giorni = listOf(
            "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica"
        )
        val etichettaGiorno = TextView(this).apply { textSize = 13f }
        val scelta = TextView(this).apply {
            textSize = 15f
            setPadding(0, 6, 0, 10)
            setTextColor(getColor(com.bellizia.mcmonitor.R.color.grass))
        }
        var giornoSettimana = partenza.giornoSettimana
        var giornoMese = partenza.giornoMese

        fun aggiornaGiorno() {
            when (cadenze[gruppo.checkedRadioButtonId.coerceIn(0, cadenze.size - 1)]) {
                Cadenza.GIORNO -> {
                    etichettaGiorno.visible(false)
                    scelta.visible(false)
                }
                Cadenza.SETTIMANA -> {
                    etichettaGiorno.visible(true)
                    scelta.visible(true)
                    etichettaGiorno.text = "In che giorno (tocca per cambiare)"
                    scelta.text = giorni[giornoSettimana - 1]
                }
                Cadenza.MESE -> {
                    etichettaGiorno.visible(true)
                    scelta.visible(true)
                    etichettaGiorno.text = "In che giorno del mese (tocca per cambiare)"
                    scelta.text = "il $giornoMese"
                }
            }
        }
        scelta.setOnClickListener {
            when (cadenze[gruppo.checkedRadioButtonId.coerceIn(0, cadenze.size - 1)]) {
                Cadenza.SETTIMANA -> MaterialAlertDialogBuilder(this)
                    .setTitle("In che giorno")
                    .setItems(giorni.toTypedArray()) { _, quale ->
                        giornoSettimana = quale + 1
                        aggiornaGiorno()
                    }
                    .show()
                Cadenza.MESE -> {
                    val numeri = (1..28).map { "il $it" }.toTypedArray()
                    MaterialAlertDialogBuilder(this)
                        .setTitle("In che giorno del mese")
                        .setMessage("Fino al 28: dal 29 in poi ci sono mesi che quel giorno non ce l'hanno, e il backup salterebbe.")
                        .setItems(numeri) { _, quale ->
                            giornoMese = quale + 1
                            aggiornaGiorno()
                        }
                        .show()
                }
                else -> Unit
            }
        }
        gruppo.setOnCheckedChangeListener { _, _ -> aggiornaGiorno() }
        contenitore.addView(etichettaGiorno)
        contenitore.addView(scelta)

        contenitore.addView(TextView(this).apply {
            text = "A che ora"
            textSize = 13f
        })
        val orologio = TimePicker(this).apply {
            setIs24HourView(true)
            hour = partenza.ora
            minute = partenza.minuto
        }
        contenitore.addView(orologio)

        contenitore.addView(TextView(this).apply {
            text = "L'ora è quella del computer dove vive il server, non quella del telefono. " +
                    "Di notte è meglio: il server si ferma per tutta la copia."
            textSize = 11f
            setPadding(0, 8, 0, 0)
        })

        aggiornaGiorno()

        MaterialAlertDialogBuilder(this)
            .setTitle("Ogni quanto fare il backup")
            .setView(ScrollView(this).apply { addView(contenitore) })
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Avanti") { _, _ ->
                val scelto = PianoBackup(
                    cadenza = cadenze[gruppo.checkedRadioButtonId.coerceIn(0, cadenze.size - 1)],
                    ora = orologio.hour,
                    minuto = orologio.minute,
                    giornoSettimana = giornoSettimana,
                    giornoMese = giornoMese
                )
                confermaProgramma(scelto)
            }
            .show()
    }

    private fun confermaProgramma(scelto: PianoBackup) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Backup ${scelto.descrizione()}?")
            .setMessage(
                "L'app aggiunge una riga nel crontab dell'utente ${Prefs.load().user}. " +
                        "Le altre righe che ci sono non vengono toccate, e di com'era prima " +
                        "resta una copia sul telefono.\n\n" +
                        "Il server si fermerà per tutta la durata della copia."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Programma") { _, _ -> scrivi(scelto) }
            .show()
    }

    private fun confermaTogli() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Togliere il backup automatico?")
            .setMessage(
                "Viene tolta solo la riga scritta dall'app. I backup già fatti restano " +
                        "dove sono, e potrai sempre farne uno a mano."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Togli") { _, _ -> scrivi(null) }
            .show()
    }

    private fun scrivi(scelto: PianoBackup?) {
        if (occupato) return
        occupato = true
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val esito = runCatching { McRepository.cronSchedule(scelto) }
            occupato = false
            binding.swipe.isRefreshing = false
            esito.fold(
                onSuccess = { toast(it); load() },
                onFailure = { mostra("Non fatto", it.userMessage()) }
            )
        }
    }

    private fun confermaRipristino() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Rimettere il crontab com'era?")
            .setMessage(
                "Torna alla copia salvata prima dell'ultima modifica fatta da qui. " +
                        "Quello che è stato scritto nel frattempo da altri va perso."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Rimetti com'era") { _, _ ->
                binding.swipe.isRefreshing = true
                lifecycleScope.launch {
                    val esito = runCatching { McRepository.cronRestore() }
                    binding.swipe.isRefreshing = false
                    esito.fold(
                        onSuccess = { toast(it); load() },
                        onFailure = { mostra("Non riuscito", it.userMessage()) }
                    )
                }
            }
            .show()
    }

    private fun registro() {
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val testo = runCatching { McRepository.cronLog() }.getOrElse { it.userMessage() }
            binding.swipe.isRefreshing = false
            mostra("Registro dei backup automatici", testo.ifBlank { "Vuoto." })
        }
    }

    // -------------------------------------------------------------- utilità

    private fun mostra(titolo: String, testo: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titolo)
            .setMessage(testo.take(4000))
            .setPositiveButton("Chiudi", null)
            .show()
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    // --------------------------------------------- rimettere una copia

    private fun rigaCopia(b: Backup, s: BackupState): android.view.View {
        val item = ItemCopiaBinding.inflate(layoutInflater, binding.elenco, false)
        item.titolo.text = quando.format(Date(b.epochSeconds * 1000))
        item.quando.text = b.sizeLabel
        val giorni = (s.nowEpochSeconds - b.epochSeconds) / 86_400
        item.dettagli.text = when {
            giorni <= 0 -> "oggi"
            giorni == 1L -> "ieri"
            else -> "$giorni giorni fa"
        }
        item.nota.visible(false)
        item.card.setOnClickListener { guardaDentro(b) }
        return item.root
    }

    /**
     * Prima di rimettere una copia si guarda cosa contiene.
     *
     * Costa qualche secondo su un archivio grosso, e vale la pena: un archivio
     * rimasto a metà per il disco pieno sembra un backup buono dall'elenco, e si
     * scopre che non lo era solo dopo aver buttato il mondo di adesso.
     */
    private fun guardaDentro(b: Backup) {
        if (occupato) return
        occupato = true
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val esito = runCatching { McRepository.backupContenuto(b.fileName) }
            binding.swipe.isRefreshing = false
            occupato = false
            if (isFinishing || isDestroyed) return@launch
            esito.fold(
                onSuccess = { (vociMondo, anteprima) -> mostraDentro(b, vociMondo, anteprima) },
                onFailure = { avviso("Non riesco a leggerlo", it.userMessage()) }
            )
        }
    }

    private fun mostraDentro(b: Backup, vociMondo: Int, anteprima: List<String>) {
        val quandoTesto = quando.format(Date(b.epochSeconds * 1000))
        if (vociMondo == 0) {
            avviso(
                "Non c'è il mondo",
                "Dentro questa copia non c'è la cartella serverfiles, cioè il mondo. " +
                        "Può essere un archivio rimasto a metà per il disco pieno. " +
                        "Non c'è niente da rimettere."
            )
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Copia del $quandoTesto")
            .setMessage(
                "Pesa ${b.sizeLabel} e contiene almeno $vociMondo file della cartella " +
                        "del gioco.\n\n" +
                        anteprima.take(8).joinToString("\n") { "· $it" } +
                        (if (anteprima.size > 8) "\n· …" else "") +
                        "\n\nRimettendola torna indietro tutta la cartella del gioco di quel " +
                        "giorno: il mondo, ma anche i mod e server.properties. Tutto quello " +
                        "che è stato fatto dopo sparisce."
            )
            .setPositiveButton("Rimettila…") { _, _ -> confermaRipristino(b, quandoTesto) }
            .setNegativeButton("Lascia stare", null)
            .show()
    }

    /**
     * La seconda conferma non è pignoleria.
     *
     * È l'unica operazione dell'app che butta via il lavoro di qualcuno, e chi la
     * lancia deve aver letto cosa succede, non solo aver toccato due volte.
     */
    private fun confermaRipristino(b: Backup, quandoTesto: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sicuro?")
            .setMessage(
                "La cartella del gioco tornerà quella del $quandoTesto.\n\n" +
                        "· Il server deve essere fermo: se è acceso non faccio niente\n" +
                        "· Quello che c'è adesso non lo cancello: lo sposto di fianco, con " +
                        "la data nel nome, e resta lì finché non lo togli tu\n" +
                        "· Tornano indietro mondo, mod e server.properties: stanno tutti " +
                        "dentro la cartella del gioco\n" +
                        "· Le impostazioni di LinuxGSM restano quelle di adesso, così una " +
                        "riparazione della riga di avvio non se ne va\n\n" +
                        "Su un mondo grande ci vuole qualche minuto."
            )
            .setPositiveButton("Rimetti il mondo") { _, _ -> ripristina(b) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun ripristina(b: Backup) {
        if (occupato) return
        occupato = true
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val esito = runCatching { McRepository.backupRipristina(b.fileName) }
            binding.swipe.isRefreshing = false
            occupato = false
            if (isFinishing || isDestroyed) return@launch
            esito.fold(
                onSuccess = { avviso("Fatto", it) },
                onFailure = { avviso("Non l'ho rimesso", it.userMessage()) }
            )
            load()
        }
    }

    private fun avviso(titolo: String, testo: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titolo)
            .setMessage(testo)
            .setPositiveButton("Ho capito", null)
            .show()
    }

    private fun applyInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appbar.setPadding(0, bars.top, 0, 0)
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}

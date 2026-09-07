package com.bellizia.mcmonitor

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.bellizia.mcmonitor.data.PlayerTracker
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.lgsm.ControlloSettimanale
import com.bellizia.mcmonitor.data.PresenceRepository
import com.bellizia.mcmonitor.lgsm.Presence
import com.bellizia.mcmonitor.notify.Notifications
import com.bellizia.mcmonitor.ui.visible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.bellizia.mcmonitor.databinding.ActivityMainBinding
import com.bellizia.mcmonitor.ui.ConsoleFragment
import com.bellizia.mcmonitor.ui.Help
import com.bellizia.mcmonitor.ui.Impostazioni
import com.bellizia.mcmonitor.ui.HelpDialog
import com.bellizia.mcmonitor.ui.MapFragment
import com.bellizia.mcmonitor.ui.ModsFragment
import com.bellizia.mcmonitor.ui.PlayersFragment
import com.bellizia.mcmonitor.ui.SettingsFragment
import com.bellizia.mcmonitor.ui.StatusFragment
import com.bellizia.mcmonitor.ui.svago.SvagoActivity
import com.bellizia.mcmonitor.ui.UpdateBanner
import com.bellizia.mcmonitor.update.UpdateChecker
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_SETTINGS = "openSettings"
    }

    private lateinit var binding: ActivityMainBinding

    /** Una scheda, con il suo aiuto e se serve solo a chi vuole vedere tutto. */
    private data class Scheda(
        val titolo: String,
        val aiuto: Help.Page,
        val soloEsperto: Boolean,
        val crea: () -> Fragment
    )

    private val tutteLeSchede = listOf(
        Scheda("Stato", Help.STATUS, false) { StatusFragment() },
        // La console grezza \u00e8 la scheda che spaventa di piu' chi comincia, ed \u00e8
        // anche quella da cui si fanno i danni piu' in fretta.
        Scheda("Console", Help.CONSOLE, true) { ConsoleFragment() },
        Scheda("Giocatori", Help.PLAYERS, false) { PlayersFragment() },
        Scheda("Mappa", Help.MAP, false) { MapFragment() },
        Scheda("Mod", Help.MODS, false) { ModsFragment() },
        Scheda("Impostazioni", Help.SETTINGS, false) { SettingsFragment() }
    )

    /**
     * Le schede di questo giro.
     *
     * Si decide una volta sola all'apertura e non si ricalcola: l'adattatore del
     * pager tiene i frammenti per posizione, e cambiare l'elenco sotto di lui
     * mentre \u00e8 vivo mostrerebbe la scheda sbagliata. Chi cambia modalit\u00e0 fa
     * ripartire l'activity.
     */
    private val tabs by lazy {
        tutteLeSchede.filter { Prefs.esperto || !it.soloEsperto }
    }

    /** Porta su una scheda per nome: serve ai collegamenti fra una scheda e l'altra. */
    fun openTab(title: String) {
        val index = tabs.indexOfFirst { it.titolo.equals(title, ignoreCase = true) }
        if (index >= 0) binding.pager.setCurrentItem(index, true)
    }

    /**
     * Nasconde barra del titolo e schede: la usa la Console quando mostra il log
     * a tutto schermo, dove ogni riga di interfaccia e' una riga di log in meno.
     * Anche lo scorrimento fra le schede si ferma, o si cambierebbe scheda
     * trascinando il log.
     */
    fun hideChrome(hidden: Boolean) {
        binding.appbar.visibility = if (hidden) android.view.View.GONE else android.view.View.VISIBLE
        binding.pager.isUserInputEnabled = !hidden
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        // Il titolo è il TextView pixel dentro la toolbar: quello di sistema va spento,
        // altrimenti la scritta compare due volte.
        supportActionBar?.setDisplayShowTitleEnabled(false)
        applyInsets()

        binding.pager.adapter = object : FragmentStateAdapter(this as FragmentActivity) {
            override fun getItemCount() = tabs.size
            override fun createFragment(position: Int) = tabs[position].crea()
        }
        binding.pager.offscreenPageLimit = 2

        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = tabs[position].titolo
        }.attach()

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.version.text = "v${UpdateChecker.currentVersion(this)}"
        UpdateBanner.attach(this, binding.updateBanner)
        // L'aiuto segue la scheda aperta: chi lo tocca vuole sapere di questa pagina.
        // Dentro un server le impostazioni sono due cose diverse: quelle di
        // questo mondo, che stanno in una scheda, e quelle dell'app. La prima
        // voce porta alla scheda, cosi' l'ingranaggio risponde a entrambe le
        // domande invece di rispondere a meta'.
        binding.btnImpostazioni.setOnClickListener {
            Impostazioni.mostra(this, "Impostazioni di questo server" to { openTab("Impostazioni") })
        }

        binding.btnHelp.setOnClickListener {
            // L'aiuto lo porta la scheda: con l'elenco filtrato, contare le
            // posizioni darebbe la pagina di un'altra.
            HelpDialog.show(this, tabs[binding.pager.currentItem].aiuto)
        }

        binding.btnAdmin.setOnClickListener {
            startActivity(Intent(this, com.bellizia.mcmonitor.ui.AdminActivity::class.java))
        }
        watchOtherAdmins()
        controlloSicurezzaSettimanale()

        // Si parte dalle Impostazioni quando si arriva da "Modifica" o quando la
        // configurazione è incompleta; altrimenti direttamente dallo stato del server.
        val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
        if (savedInstanceState == null && (openSettings || !Prefs.load().isComplete)) {
            binding.pager.setCurrentItem(tabs.lastIndex, false)
        }
    }

    /**
     * Il tasto che c'e' solo per chi l'ha trovata.
     *
     * Si guarda a ogni ritorno sulla schermata e non una volta all'apertura:
     * lo sblocco puo' arrivare mentre si guarda la console, e comparire subito
     * dopo -- senza dover chiudere e riaprire l'app -- e' meta' dell'effetto.
     */
    private fun aggiornaTrovato() {
        binding.btnTrovato.visible(Prefs.trovato)
        binding.btnTrovato.setOnClickListener {
            startActivity(Intent(this, SvagoActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        aggiornaTrovato()
        // Il nome può cambiare dalle Impostazioni mentre l'activity è aperta.
        binding.serverName.text = Prefs.load().displayName
    }

    /**
     * Presenza: ogni minuto si lascia un segnale sul computer e si guarda chi
     * altro c'è. Il numero sul pulsante conta solo gli altri — sapere di essere
     * da soli non serve a nessuno, sapere che c'è qualcun altro sì, perché da qui
     * si spegne un server.
     */
    /**
     * Il controllo di sicurezza che si rifà da solo, una volta a settimana.
     *
     * Non gira in sottofondo e non è una scorciatoia: da Android 14 un lavoro
     * periodico di un'app come questa viene rimandato o non eseguito, e sui
     * telefoni Samsung anche prima. «Ogni lunedì» sarebbe una promessa che
     * decide il sistema operativo, non noi. Gira invece alla prima apertura di
     * un server dopo che è passata una settimana, che è una promessa
     * mantenibile: il collegamento è già acceso e si sta già aspettando.
     *
     * Va in fondo alla coda e non blocca niente: se fallisce — server spento,
     * rete assente — non si segna la data e si riproverà alla prossima
     * apertura. Un controllo saltato non deve valere come un controllo fatto.
     */
    private fun controlloSicurezzaSettimanale() {
        val cfg = Prefs.load()
        if (!cfg.isComplete) return
        val quando = System.currentTimeMillis()
        if (!ControlloSettimanale.deveGirare(
                esperto = Prefs.esperto,
                ultimoControllo = Prefs.ultimoControlloSicurezza(cfg.id),
                adesso = quando,
            )
        ) return

        lifecycleScope.launch {
            // Prima si lascia respirare la schermata: aprire un server e
            // trovarlo lento perche' sta facendo un controllo di sicurezza e'
            // il modo di far disattivare il controllo di sicurezza.
            delay(20_000)
            val rapporto = runCatching { McRepository.controlloSicurezza() }.getOrNull() ?: return@launch
            Prefs.segnaControlloSicurezza(cfg.id, quando)
            if (!ControlloSettimanale.vaSegnalato(rapporto)) return@launch
            Notifications.event(
                applicationContext,
                Notifications.CHANNEL_SICUREZZA,
                ControlloSettimanale.titolo(cfg.displayName, rapporto),
                ControlloSettimanale.testo(rapporto),
            )
        }
    }

    private fun watchOtherAdmins() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    if (Prefs.load().isComplete) {
                        PresenceRepository.heartbeat()
                        val altri = PresenceRepository.others()
                        val nuovi = PresenceRepository.unread()
                        // Sul pulsante conta chi c'e' adesso; i messaggi non letti
                        // vanno sul pallino dell'icona, che si vede anche da fuori.
                        binding.adminBadge.visible(altri.isNotEmpty() || nuovi.isNotEmpty())
                        binding.adminBadge.text =
                            if (nuovi.isNotEmpty()) nuovi.size.toString() else altri.size.toString()
                        binding.adminBadge.setBackgroundColor(
                            androidx.core.content.ContextCompat.getColor(
                                this@MainActivity,
                                if (nuovi.isNotEmpty()) R.color.danger else R.color.grass
                            )
                        )
                        Notifications.adminMessages(
                            this@MainActivity,
                            nuovi.size,
                            nuovi.lastOrNull()?.let { "${it.name}: ${it.text}" }.orEmpty()
                        )
                    }
                    delay(Presence.HEARTBEAT_SECONDS * 1000)
                }
            }
        }
    }

    /** Porta la mappa in primo piano centrata su un giocatore, e la tiene agganciata. */
    fun showPlayerOnMap(player: String) {
        PlayerTracker.focus = player
        openTab("Mappa")
    }

    /**
     * Con targetSdk 35 la finestra è edge-to-edge: senza questo, la barra di stato
     * e quella di navigazione coprono il contenuto e la tastiera nasconde i campi.
     */
    private fun applyInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // La barra superiore entra nel padding dell'AppBar, così ne eredita il colore.
            binding.appbar.setPadding(0, bars.top, 0, 0)
            view.setPadding(bars.left, 0, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
    }
}

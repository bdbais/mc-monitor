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
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.data.PresenceRepository
import com.bellizia.mcmonitor.lgsm.Presence
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
import com.bellizia.mcmonitor.ui.HelpDialog
import com.bellizia.mcmonitor.ui.MapFragment
import com.bellizia.mcmonitor.ui.ModsFragment
import com.bellizia.mcmonitor.ui.PlayersFragment
import com.bellizia.mcmonitor.ui.SettingsFragment
import com.bellizia.mcmonitor.ui.StatusFragment
import com.bellizia.mcmonitor.ui.UpdateBanner
import com.bellizia.mcmonitor.update.UpdateChecker
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_SETTINGS = "openSettings"
    }

    private lateinit var binding: ActivityMainBinding

    private val tabs = listOf(
        "Stato" to { StatusFragment() as Fragment },
        "Console" to { ConsoleFragment() as Fragment },
        "Giocatori" to { PlayersFragment() as Fragment },
        "Mappa" to { MapFragment() as Fragment },
        "Mod" to { ModsFragment() as Fragment },
        "Impostazioni" to { SettingsFragment() as Fragment }
    )

    /** Porta su una scheda per nome: serve ai collegamenti fra una scheda e l'altra. */
    fun openTab(title: String) {
        val index = tabs.indexOfFirst { it.first.equals(title, ignoreCase = true) }
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
            override fun createFragment(position: Int) = tabs[position].second()
        }
        binding.pager.offscreenPageLimit = 2

        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = tabs[position].first
        }.attach()

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.version.text = "v${UpdateChecker.currentVersion(this)}"
        UpdateBanner.attach(this, binding.updateBanner)
        // L'aiuto segue la scheda aperta: chi lo tocca vuole sapere di questa pagina.
        binding.btnHelp.setOnClickListener {
            HelpDialog.show(this, Help.forTab(binding.pager.currentItem))
        }

        binding.btnAdmin.setOnClickListener {
            startActivity(Intent(this, com.bellizia.mcmonitor.ui.AdminActivity::class.java))
        }
        watchOtherAdmins()

        // Si parte dalle Impostazioni quando si arriva da "Modifica" o quando la
        // configurazione è incompleta; altrimenti direttamente dallo stato del server.
        val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
        if (savedInstanceState == null && (openSettings || !Prefs.load().isComplete)) {
            binding.pager.setCurrentItem(tabs.lastIndex, false)
        }
    }

    override fun onResume() {
        super.onResume()
        // Il nome può cambiare dalle Impostazioni mentre l'activity è aperta.
        binding.serverName.text = Prefs.load().displayName
    }

    /**
     * Presenza: ogni minuto si lascia un segnale sul computer e si guarda chi
     * altro c'è. Il numero sul pulsante conta solo gli altri — sapere di essere
     * da soli non serve a nessuno, sapere che c'è qualcun altro sì, perché da qui
     * si spegne un server.
     */
    private fun watchOtherAdmins() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    if (Prefs.load().isComplete) {
                        PresenceRepository.heartbeat()
                        val altri = PresenceRepository.others()
                        binding.adminBadge.visible(altri.isNotEmpty())
                        binding.adminBadge.text = altri.size.toString()
                    }
                    delay(Presence.HEARTBEAT_SECONDS * 1000)
                }
            }
        }
    }

    /** Porta la mappa in primo piano centrata su un giocatore, e la tiene agganciata. */
    fun showPlayerOnMap(player: String) {
        PlayerTracker.focus = player
        binding.pager.setCurrentItem(tabs.indexOfFirst { it.first == "Mappa" }, true)
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

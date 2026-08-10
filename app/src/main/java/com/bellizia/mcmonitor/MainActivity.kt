package com.bellizia.mcmonitor

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
import com.bellizia.mcmonitor.databinding.ActivityMainBinding
import com.bellizia.mcmonitor.ui.ConsoleFragment
import com.bellizia.mcmonitor.ui.MapFragment
import com.bellizia.mcmonitor.ui.ModsFragment
import com.bellizia.mcmonitor.ui.PlayersFragment
import com.bellizia.mcmonitor.ui.SettingsFragment
import com.bellizia.mcmonitor.ui.StatusFragment
import com.bellizia.mcmonitor.ui.UpdateBanner
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
        UpdateBanner.attach(this, binding.updateBanner)

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

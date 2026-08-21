package com.bellizia.mcmonitor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.MenuItem
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.bellizia.mcmonitor.CrashReporter
import com.bellizia.mcmonitor.MainActivity
import com.bellizia.mcmonitor.R
import com.bellizia.mcmonitor.data.PlayerTracker
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.data.Privacy
import com.bellizia.mcmonitor.data.ServerConfig
import com.bellizia.mcmonitor.databinding.ActivityHomeBinding
import com.bellizia.mcmonitor.rcon.RconManager
import com.bellizia.mcmonitor.ssh.SshManager
import com.bellizia.mcmonitor.update.UpdateChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Schermata di partenza, divisa nei due passi che una ragazzina di tredici anni
 * riesce a seguire senza saperne di server: prima si entra nel computer, poi si
 * sceglie fra i mondi che ci sono già dentro.
 *
 * Il menu a sinistra tiene separate le due cose, e mette in disparte — non le
 * nasconde — le operazioni da adulti: creare un server da zero e i profili
 * salvati a mano.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        applyInsets()

        binding.version.text = "v${UpdateChecker.currentVersion(this)}"
        UpdateBanner.attach(this, binding.updateBanner)
        showLastCrash()

        binding.btnMenu.setOnClickListener { binding.root.openDrawer(GravityCompat.START) }
        // In XML "@null" non toglie la tinta (il tema rimette la sua): va fatto
        // qui, o le icone a pixel diventano tutte una macchia dello stesso colore.
        binding.nav.itemIconTintList = null
        binding.nav.setNavigationItemSelectedListener { item -> onNavigation(item) }

        // Il tasto indietro chiude prima il cassetto, poi esce: è quello che
        // chiunque si aspetta, ed evita di uscire dall'app per sbaglio.
        onBackPressedDispatcher.addCallback(this) {
            if (binding.root.isDrawerOpen(GravityCompat.START)) {
                binding.root.closeDrawer(GravityCompat.START)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        if (savedInstanceState == null) {
            // Con l'utenza già salvata il primo passo è fatto: si parte dai mondi.
            val start = if (Prefs.account().hasCredentials) R.id.nav_installati else R.id.nav_connessione
            show(start)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHeader()
    }

    private fun onNavigation(item: MenuItem): Boolean {
        binding.root.closeDrawer(GravityCompat.START)
        when (item.itemId) {
            R.id.nav_connessione, R.id.nav_installati -> show(item.itemId)
            R.id.nav_nuovo -> createServer()
            R.id.nav_profili -> startActivity(Intent(this, ServersActivity::class.java))
            R.id.nav_manuale -> About.manual(this)
            R.id.nav_info -> About.show(this)
        }
        return true
    }

    /** Mostra una delle due sezioni e la segna nel menu. */
    fun show(itemId: Int) {
        val fragment: Fragment = when (itemId) {
            R.id.nav_installati -> InstalledFragment()
            else -> ConnectionFragment()
        }
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.contenuto, fragment)
        }
        binding.nav.setCheckedItem(itemId)
        refreshHeader()
    }

    /** Chiamata dal primo passo quando il collegamento è riuscito. */
    fun openInstalled() = show(R.id.nav_installati)

    /**
     * Creare un server da zero è un'operazione lunga: si prepara il profilo con
     * le credenziali già inserite e si apre direttamente la scheda che la guida.
     */
    fun createServer() {
        val account = Prefs.account()
        if (!account.hasCredentials) {
            toast("Prima collegati al computer")
            show(R.id.nav_connessione)
            return
        }
        val fresh = Prefs.add(ServerConfig(name = "Nuovo server").withCredentials(account))
        openServer(fresh, settings = true)
    }

    /** Aprire un server lo rende quello attivo per tutto il resto dell'app. */
    fun openServer(server: ServerConfig, settings: Boolean) {
        if (Prefs.activeId() != server.id) {
            // Cambiare server significa buttare via sessione SSH, RCON e scie in memoria.
            SshManager.disconnect()
            RconManager.disconnect()
            PlayerTracker.clear()
            PlayerTracker.focus = null
            Prefs.setActive(server.id)
        }
        Prefs.markUsed(server.id)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, settings || !server.isComplete)
        )
    }

    private fun refreshHeader() {
        val account = Prefs.account()
        val subtitle = binding.nav.getHeaderView(0)?.findViewById<TextView>(R.id.navSubtitle)
        subtitle?.text = if (account.hasCredentials) {
            Privacy.account(account.user, "${account.host}:${account.port}")
        } else {
            "nessun computer collegato"
        }
    }

    /**
     * Se l'avvio precedente è finito con un crash, la traccia viene mostrata qui:
     * è l'unico modo perché arrivi a chi può correggerla.
     */
    private fun showLastCrash() {
        val report = CrashReporter.lastCrash(this) ?: return
        val view = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 10f
            setTextIsSelectable(true)
            setPadding(40, 24, 40, 8)
            text = report
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("L'app si è chiusa in modo anomalo")
            .setView(ScrollView(this).apply { addView(view) })
            .setNeutralButton("Copia") { _, _ ->
                getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("crash MC Monitor", report))
                toast("Rapporto copiato")
                CrashReporter.clear(this)
            }
            .setPositiveButton("Chiudi") { _, _ -> CrashReporter.clear(this) }
            .show()
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * Un solo ascoltatore sul cassetto: se lo mettessimo sui figli, il primo che
     * consuma i margini lascerebbe il menu laterale sotto la barra di sistema.
     */
    private fun applyInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.appbar.setPadding(0, bars.top, 0, 0)
            binding.contenitore.setPadding(bars.left, 0, bars.right, maxOf(bars.bottom, ime.bottom))
            binding.nav.setPadding(0, bars.top, 0, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}

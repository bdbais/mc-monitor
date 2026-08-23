package com.bellizia.mcmonitor.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.R
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.ActivitySecurityBinding
import com.bellizia.mcmonitor.databinding.ItemComandoBinding
import com.bellizia.mcmonitor.lgsm.Controllo
import com.bellizia.mcmonitor.lgsm.Esito
import com.bellizia.mcmonitor.lgsm.Livello
import com.bellizia.mcmonitor.lgsm.Rapporto
import com.bellizia.mcmonitor.lgsm.SecurityCheck
import kotlinx.coroutines.launch

/**
 * Quanto è chiuso questo server, in una parola e in un elenco.
 *
 * Non è un audit e non pretende di esserlo: guarda le poche cose che su un
 * server piccolo fanno la differenza fra "ci entrano i tuoi amici" e "ci entra
 * chiunque abbia trovato l'indirizzo". Ognuna dice cosa vuol dire e come si
 * sistema, perché un semaforo rosso senza istruzioni serve solo a preoccupare.
 */
class SecurityActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecurityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        applyInsets()

        binding.sottotitolo.text = Prefs.load().displayName
        binding.btnHelp.setOnClickListener { HelpDialog.show(this, Help.SICUREZZA) }
        binding.swipe.setOnRefreshListener { load() }
        load()
    }

    override fun onResume() {
        super.onResume()
        // Tornando qui dopo aver sistemato qualcosa, il voto si rifà da solo.
        load()
    }

    private fun load() {
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val esito = runCatching { McRepository.controlloSicurezza() }
            binding.swipe.isRefreshing = false
            esito.fold(
                onSuccess = { render(it) },
                onFailure = {
                    binding.intestazione.text = it.userMessage()
                    binding.elenco.removeAllViews()
                    binding.esclusi.text = ""
                }
            )
        }
    }

    private fun render(r: Rapporto) {
        binding.intestazione.text = buildString {
            append("Sicurezza: ").append(r.livello.etichetta.uppercase()).append("\n\n")
            append(r.riassunto())
        }

        binding.elenco.removeAllViews()
        // Prima quello che non va: è per quello che si apre questa schermata.
        (r.problemi + r.attenzioni + r.controlli.filter { it.esito == Esito.NON_SO } + r.aPosto)
            .forEach { binding.elenco.addView(riga(it)) }

        binding.esclusi.text =
            "È un controllo veloce sulle cose che contano di più, non un esame completo. " +
                    "Guarda solo la configurazione: non prova a entrare nel server e non " +
                    "manda niente fuori da qui."
    }

    private fun riga(c: Controllo): android.view.View {
        val item = ItemComandoBinding.inflate(layoutInflater, binding.elenco, false)
        item.titolo.text = c.titolo
        item.nome.text = when (c.esito) {
            Esito.BENE -> "a posto"
            Esito.ATTENZIONE -> "da guardare"
            Esito.MALE -> "da sistemare"
            Esito.NON_SO -> "non lo so"
        }
        item.nome.setTextColor(
            ContextCompat.getColor(
                this,
                when (c.esito) {
                    Esito.BENE -> R.color.grass
                    Esito.MALE -> R.color.warning
                    else -> R.color.text_dim
                }
            )
        )
        item.cosaFa.text = c.spiegazione
        item.conseguenza.visible(c.rimedio.isNotBlank())
        item.conseguenza.text = c.rimedio

        // Dove si sistema: si arriva con un tocco, non cercando in giro.
        if (c.impostazione.isNotBlank()) {
            item.card.setOnClickListener {
                startActivity(Intent(this, GameSettingsActivity::class.java))
            }
        }
        return item.root
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

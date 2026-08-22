package com.bellizia.mcmonitor.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bellizia.mcmonitor.R
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.data.PresenceRepository
import com.bellizia.mcmonitor.databinding.ActivityAdminBinding
import com.bellizia.mcmonitor.databinding.ItemAdminBinding
import com.bellizia.mcmonitor.databinding.ItemChatBinding
import com.bellizia.mcmonitor.lgsm.Admin
import com.bellizia.mcmonitor.lgsm.AdminMessage
import com.bellizia.mcmonitor.lgsm.Presence
import com.bellizia.mcmonitor.notify.Notifications
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chi altro sta amministrando questo server, e i messaggi fra amministratori.
 *
 * Serve a non pestarsi i piedi: prima di spegnere un server si vede se c'è
 * qualcun altro dentro, e gli si può scrivere invece di scoprirlo dai giocatori
 * buttati fuori. Tutto vive sul computer del server, in `~/.mcmonitor`.
 */
class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private val orario = SimpleDateFormat("d MMM HH:mm", Locale.ITALY)
    private var ultimoMessaggio = 0L

    /** Un invio alla volta: al campo di testo si arriva da due tasti diversi. */
    private var inviando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        applyInsets()

        binding.sottotitolo.text = Prefs.load().displayName
        binding.btnInvia.setOnClickListener { invia() }
        binding.messaggio.setOnEditorActionListener { _, _, _ -> invia(); true }

        // Finché questa schermata è aperta ci si fa vivi spesso: chi guarda qui
        // sta per fare qualcosa, ed è il momento in cui gli altri devono vederlo.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    aggiorna()
                    delay(Presence.HEARTBEAT_SECONDS * 1000)
                }
            }
        }
    }

    private suspend fun aggiorna() {
        PresenceRepository.heartbeat()
        val admins = runCatching { PresenceRepository.admins() }.getOrDefault(emptyList())
        val messaggi = runCatching { PresenceRepository.messages() }.getOrDefault(emptyList())
        renderAdmins(admins)
        renderMessaggi(messaggi)
        // Aperta la chat, i messaggi sono letti: via il pallino dall'icona.
        PresenceRepository.markRead(messaggi)
        Notifications.clearAdminMessages(this)
    }

    private fun renderAdmins(tutti: List<Admin>) {
        val mio = Presence.safeId(Prefs.deviceId)
        val admins = Presence.withoutOwnGhosts(tutti, Prefs.deviceId, Prefs.adminName)
        val attivi = admins.filter { it.active }
        binding.statoPresenza.text = when {
            attivi.isEmpty() -> "Nessuno, nemmeno tu: il computer non ha ancora ricevuto segnali."
            attivi.size == 1 && attivi[0].id == mio -> "Solo tu."
            else -> "${attivi.size} collegati in questo momento."
        }

        binding.elencoAdmin.removeAllViews()
        admins.forEach { admin ->
            val riga = ItemAdminBinding.inflate(layoutInflater, binding.elencoAdmin, false)
            riga.nome.text = buildString {
                append(admin.name)
                if (admin.id == mio) append(" (tu)")
                if (admin.busy) append(" — ${admin.doing}")
            }
            riga.quando.text = if (admin.active) admin.whenLabel else "non c'è più"
            riga.pallino.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    when {
                        admin.busy -> R.color.warning
                        admin.active -> R.color.grass
                        else -> R.color.text_dim
                    }
                )
            )
            binding.elencoAdmin.addView(riga.root)
        }
    }

    private fun renderMessaggi(messaggi: List<AdminMessage>) {
        binding.nessunMessaggio.visible(messaggi.isEmpty())
        binding.elencoMessaggi.removeAllViews()
        messaggi.forEach { messaggio ->
            val riga = ItemChatBinding.inflate(layoutInflater, binding.elencoMessaggi, false)
            val mio = messaggio.id == Presence.safeId(Prefs.deviceId)
            riga.intestazione.text = buildString {
                append(if (mio) "tu" else messaggio.name)
                append(" · ")
                append(orario.format(Date(messaggio.epochSeconds * 1000)))
                if (messaggio.broadcast) append(" · a tutti")
                if (messaggio.isNote) append(" · su ${messaggio.about}")
            }
            riga.testo.text = messaggio.text
            binding.elencoMessaggi.addView(riga.root)
        }
        // Con messaggi nuovi si scende in fondo: è lì che si guarda.
        val ultimo = messaggi.lastOrNull()?.epochSeconds ?: 0L
        if (ultimo > ultimoMessaggio) {
            ultimoMessaggio = ultimo
            binding.scorrevole.post { binding.scorrevole.fullScroll(View.FOCUS_DOWN) }
        }
    }

    /**
     * Manda il messaggio, una volta sola.
     *
     * Ci arrivano due strade — il pulsante e il tasto della tastiera — e il
     * pulsante disabilitato non chiudeva la seconda. Con l'invio che passa da SSH
     * e ci mette qualche secondo, il testo restava nel campo, sembrava non essere
     * partito, e si premeva di nuovo: in chat comparivano due volte le stesse
     * parole. Per questo il campo si svuota subito, e ci torna solo se l'invio
     * fallisce davvero.
     */
    private fun invia() {
        if (inviando) return
        val testo = binding.messaggio.text?.toString()?.trim().orEmpty()
        if (testo.isBlank()) return
        val aTutti = binding.aTutti.isChecked

        inviando = true
        binding.btnInvia.isEnabled = false
        binding.messaggio.setText("")

        lifecycleScope.launch {
            val esito = runCatching { PresenceRepository.send(testo, broadcast = aTutti) }
            inviando = false
            binding.btnInvia.isEnabled = true
            esito.onSuccess {
                aggiorna()
            }.onFailure {
                // Non è partito: il testo torna dov'era, così non si riscrive.
                binding.messaggio.setText(testo)
                binding.messaggio.setSelection(testo.length)
                android.widget.Toast.makeText(
                    this@AdminActivity,
                    "Messaggio non inviato: ${it.userMessage()}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun applyInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.appbar.setPadding(0, bars.top, 0, 0)
            view.setPadding(bars.left, 0, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
    }
}

package com.bellizia.mcmonitor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.R
import com.bellizia.mcmonitor.data.ConfigTransfer
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.data.Privacy
import com.bellizia.mcmonitor.data.ServerConfig
import com.bellizia.mcmonitor.databinding.DialogPasswordBinding
import com.bellizia.mcmonitor.databinding.FragmentConnectionBinding
import com.bellizia.mcmonitor.lgsm.Lgsm
import com.bellizia.mcmonitor.ssh.SshException
import com.bellizia.mcmonitor.ssh.SshManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Primo passo: entrare nel computer.
 *
 * Qui si chiedono solo le quattro cose che servono per il collegamento. Dove
 * stiano i mondi non lo chiede nessuno: li cerca il passo successivo.
 */
class ConnectionFragment : Fragment() {

    private var _b: FragmentConnectionBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentConnectionBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fill(Prefs.account())

        b.btnHelp.setOnClickListener { HelpDialog.show(requireContext(), Help.CONNECTION) }
        b.btnChiave.setOnClickListener { showKeyFields(b.boxChiave.visibility != View.VISIBLE) }
        b.btnCollegati.setOnClickListener { connect() }
        b.btnDiagnostica.setOnClickListener { diagnose() }
        b.btnCambiaPassword.setOnClickListener { changePassword() }
        b.btnRipristina.setOnClickListener { pickConfigFile.launch(arrayOf("*/*")) }
        showRestoreCard()
    }

    /**
     * Una reinstallazione azzera tutto quello che l'app teneva sul telefono. Chi
     * aveva esportato la configurazione può riprenderla da lì invece di
     * ricompilare a mano; a chi ha già qualcosa dentro la proposta non compare.
     */
    private fun showRestoreCard() {
        val bind = _b ?: return
        bind.ripristino.visible(!Prefs.account().hasCredentials && Prefs.servers().isEmpty())
    }

    private val pickConfigFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) askImportPassword(uri)
        }

    private fun askImportPassword(uri: Uri) {
        val form = DialogPasswordBinding.inflate(layoutInflater)
        form.currentPassword.hint = "Password"
        form.newPassword.visible(false)
        form.repeatPassword.visible(false)
        (form.newPassword.parent.parent as? View)?.visible(false)
        (form.repeatPassword.parent.parent as? View)?.visible(false)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Riprendi la configurazione")
            .setMessage("È la password che avevi scelto quando l'hai esportata o quando hai attivato il backup automatico.")
            .setView(form.root)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Apri il file", null)
            .show()
            .also { dialog ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val password = form.currentPassword.text?.toString().orEmpty()
                    if (password.isBlank()) {
                        toast("Serve la password del file")
                    } else {
                        dialog.dismiss()
                        importConfig(uri, password)
                    }
                }
            }
    }

    private fun importConfig(uri: Uri, password: String) {
        val result = runCatching {
            val content = requireContext().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() } ?: error("file illeggibile")
            ConfigTransfer.import(content, password)
        }
        result.fold(
            onSuccess = { servers ->
                if (servers.isEmpty()) {
                    report("Il file non conteneva nessun server.", ok = false)
                    return@fold
                }
                // save() e non add(): add() assegna un nome tecnico e una cartella
                // nuovi, e un ripristino perderebbe proprio il percorso del server.
                servers.forEach { Prefs.save(it.copy(id = "")) }
                // Le credenziali del primo server diventano l'utenza del passo 1.
                val account = servers.firstOrNull { it.hasCredentials }?.credentials()
                if (account != null) Prefs.saveAccount(account)
                fill(Prefs.account())
                showRestoreCard()
                report(
                    "Ripresi ${servers.size} server dal file. Controlla i dati qui sopra e tocca Collegati.",
                    ok = true
                )
            },
            onFailure = {
                report(
                    "Non sono riuscito a leggere il file: password sbagliata, oppure non è una configurazione di MC Monitor.",
                    ok = false
                )
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun fill(account: ServerConfig) {
        b.host.setText(account.host)
        b.port.setText(account.port.toString())
        b.user.setText(account.user)
        b.password.setText(account.password)
        b.privateKey.setText(account.privateKey)
        b.keyPassphrase.setText(account.keyPassphrase)
        showKeyFields(account.privateKey.isNotBlank())
    }

    private fun showKeyFields(show: Boolean) {
        b.boxChiave.visible(show)
        b.boxPassphrase.visible(show)
        b.btnChiave.text =
            if (show) "Torno alla password" else "Uso una chiave al posto della password"
    }

    /** Legge i campi senza toccare nient'altro della configurazione. */
    private fun collect() = ServerConfig(
        host = b.host.text?.toString()?.trim().orEmpty(),
        port = b.port.text?.toString()?.trim()?.toIntOrNull() ?: 22,
        user = b.user.text?.toString()?.trim().orEmpty(),
        password = b.password.text?.toString().orEmpty(),
        privateKey = if (b.boxChiave.visibility == View.VISIBLE) {
            b.privateKey.text?.toString().orEmpty()
        } else {
            ""
        },
        keyPassphrase = if (b.boxChiave.visibility == View.VISIBLE) {
            b.keyPassphrase.text?.toString().orEmpty()
        } else {
            ""
        },
        hostKeyFingerprint = Prefs.account().hostKeyFingerprint
    )

    private fun connect() {
        val account = collect()
        if (!account.hasCredentials) {
            report("Mancano indirizzo, nome utente e password (o chiave).", ok = false)
            return
        }
        Prefs.saveAccount(account)
        // Le credenziali possono essere cambiate: la vecchia sessione non vale più.
        SshManager.disconnect()

        busy(true)
        report("Provo a entrare in ${account.host}…", ok = null)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { SshManager.test(account) }
            val bind = _b ?: return@launch
            busy(false)
            result.fold(
                onSuccess = {
                    // Un attimo per leggere la conferma, poi si passa ai mondi.
                    report("Collegato.\n$it", ok = true)
                    bind.esito.postDelayed({
                        (activity as? HomeActivity)?.openInstalled()
                    }, 700)
                },
                onFailure = { report("Non sono riuscito a entrare.\n${it.userMessage()}", ok = false) }
            )
        }
    }

    /**
     * Quando non funziona, la diagnostica prova un pezzo alla volta e dice a
     * quale punto si ferma: è più utile di un errore secco da copiare a mano.
     */
    private fun diagnose() {
        val account = collect()
        if (account.host.isBlank()) {
            report("Serve almeno l'indirizzo del computer.", ok = false)
            return
        }
        Prefs.saveAccount(account)
        busy(true)
        report("Controllo un pezzo alla volta…", ok = null)
        viewLifecycleOwner.lifecycleScope.launch {
            val raw = runCatching { SshManager.diagnose(account) }
                .getOrElse { "Diagnostica interrotta: ${it.userMessage()}" }
            val text = Privacy.text(raw, account)
            if (_b == null) return@launch
            busy(false)
            report(text, ok = null)
            showReport(text)
        }
    }

    /** Il rapporto va copiato per intero: a schermo si legge male. */
    private fun showReport(text: String) {
        if (!isAdded) return
        val view = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(40, 30, 40, 10)
            this.text = text
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Diagnostica del collegamento")
            .setView(ScrollView(requireContext()).apply { addView(view) })
            .setNeutralButton("Copia") { _, _ ->
                requireContext().getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("diagnostica MC Monitor", text))
                toast("Rapporto copiato")
            }
            .setPositiveButton("Chiudi", null)
            .show()
    }

    /**
     * Cambia la password dell'utenza sul computer, non solo quella salvata:
     * dopo il cambio il profilo deve seguire, o al giro dopo resta fuori.
     */
    private fun changePassword() {
        val account = collect()
        if (!account.hasCredentials || account.password.isBlank()) {
            toast("Serve prima la password attuale")
            return
        }
        Prefs.saveAccount(account)
        val form = DialogPasswordBinding.inflate(layoutInflater)
        form.currentPassword.setText(account.password)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cambia password di ${account.user}")
            .setView(form.root)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Cambia", null)
            .show()
            .also { dialog ->
                // Il pulsante non chiude il dialogo finché i campi non sono coerenti.
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val current = form.currentPassword.text?.toString().orEmpty()
                    val fresh = form.newPassword.text?.toString().orEmpty()
                    val repeat = form.repeatPassword.text?.toString().orEmpty()
                    when {
                        current.isBlank() -> toast("Serve la password attuale")
                        fresh.length < 8 -> toast("La nuova password è troppo corta (minimo 8)")
                        fresh != repeat -> toast("Le due nuove password non coincidono")
                        fresh == current -> toast("La nuova password è uguale a quella attuale")
                        else -> {
                            dialog.dismiss()
                            runChangePassword(account, current, fresh)
                        }
                    }
                }
            }
    }

    private fun runChangePassword(account: ServerConfig, current: String, fresh: String) {
        busy(true)
        report("Eseguo passwd sul computer…", ok = null)
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = runCatching {
                val result = SshManager.changePassword(account.copy(password = current), current, fresh)
                val text = Lgsm.clean(result.text).trim()
                if (result.exitCode != 0) throw SshException(reasonFor(result.exitCode, text))
                // Da qui la password sul computer è nuova: salvarla è la parte da non sbagliare.
                Prefs.saveAccount(account.copy(password = fresh))
                SshManager.disconnect()
                val verify = runCatching { SshManager.test(Prefs.account()) }
                buildString {
                    append("Password cambiata, e aggiornata anche nell'app.\n")
                    append("Verifica del nuovo accesso: ")
                    append(
                        verify.fold(
                            onSuccess = { "riuscita." },
                            onFailure = { "FALLITA. ${it.userMessage()}\nCorreggila qui prima di chiudere l'app." }
                        )
                    )
                }
            }
            val bind = _b ?: return@launch
            busy(false)
            outcome.fold(
                onSuccess = {
                    fill(Prefs.account())
                    report(it, ok = true)
                },
                onFailure = { report(it.userMessage(), ok = false) }
            )
            bind.esito.visible(true)
        }
    }

    private fun reasonFor(exitCode: Int, text: String): String = "Password non cambiata: " + when {
        text.contains("Authentication token manipulation", true) ->
            "il computer ha rifiutato la modifica (password attuale errata?)"
        text.contains("BAD PASSWORD", true) || text.contains("too short", true) ->
            "la nuova password non rispetta le regole del computer"
        text.contains("do not match", true) || text.contains("non corrispondono", true) ->
            "le due nuove password non coincidono"
        exitCode == -1 -> "nessuna risposta entro 30 secondi"
        else -> "uscita $exitCode"
    } + if (text.isNotBlank()) "\n\n$text" else ""

    private fun busy(working: Boolean) {
        val bind = _b ?: return
        bind.attesa.visible(working)
        bind.btnCollegati.isEnabled = !working
        bind.btnDiagnostica.isEnabled = !working
        bind.btnCambiaPassword.isEnabled = !working
    }

    /** Verde riuscito, rosso fallito, grigio in corso: si capisce senza leggere. */
    private fun report(text: String, ok: Boolean?) {
        val bind = _b ?: return
        bind.esito.visible(true)
        bind.esito.text = text
        val color = when (ok) {
            true -> R.color.grass
            false -> R.color.danger
            null -> R.color.text_dim
        }
        bind.esito.setTextColor(ContextCompat.getColor(requireContext(), color))
    }
}

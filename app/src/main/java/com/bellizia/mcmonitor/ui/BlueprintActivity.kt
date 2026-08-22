package com.bellizia.mcmonitor.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.data.BlueprintChoice
import com.bellizia.mcmonitor.data.BlueprintRepository
import com.bellizia.mcmonitor.data.BlueprintTransfer
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.data.SealedBox
import com.bellizia.mcmonitor.databinding.ActivityBlueprintBinding
import com.bellizia.mcmonitor.lgsm.Blueprint
import com.bellizia.mcmonitor.lgsm.Blueprints
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File

/**
 * Il progetto del server: portarlo via, o rifarne uno uguale qui.
 *
 * Nasce da una domanda pratica: uno prepara il server come gli piace — memoria,
 * regole, mod, chi può entrare — e poi qualcun altro vuole lo stesso server a
 * casa sua. Prima si copiavano i file a mano, sperando di non dimenticarne uno.
 *
 * Il file è cifrato e compresso: viaggia in una chat e lo apre solo chi ha la
 * password. Dentro non ci sono credenziali: quelle restano nel file dei
 * collegamenti, che è un'altra cosa e si esporta dalle Impostazioni.
 */
class BlueprintActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlueprintBinding

    private val apriProgetto =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) chiediPasswordImport(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlueprintBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        applyInsets()

        binding.sottotitolo.text = Prefs.load().displayName
        binding.btnHelp.setOnClickListener { HelpDialog.show(this, Help.BLUEPRINT) }
        binding.btnEsporta.setOnClickListener { esporta() }
        binding.btnImporta.setOnClickListener {
            apriProgetto.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
        }
        anteprima()
    }

    /** Un'occhiata a cosa uscirebbe, prima ancora di chiedere la password. */
    private fun anteprima() {
        lifecycleScope.launch {
            val esito = runCatching { BlueprintRepository.read(includePlayers = false) }
            binding.contenuto.text = esito.fold(
                onSuccess = { "Da questo server: ${it.summary()}." },
                onFailure = { "Non riesco a leggere la configurazione: ${it.userMessage()}" }
            )
        }
    }

    // ------------------------------------------------------------- esporta

    private fun esporta() {
        val conGiocatori = binding.includiGiocatori.isChecked
        occupato(true)
        lifecycleScope.launch {
            val esito = runCatching { BlueprintRepository.read(conGiocatori) }
            occupato(false)
            esito.fold(
                onSuccess = { progetto -> chiediPasswordExport(progetto) },
                onFailure = { mostra("Non esportato", it.userMessage()) }
            )
        }
    }

    private fun chiediPasswordExport(progetto: Blueprint) {
        chiediPassword(
            titolo = "Password del progetto",
            messaggio = "Contiene ${progetto.summary()}.\n\n" +
                    "Chi riceve il file lo apre solo con questa password: mandagliela per " +
                    "un'altra via, non insieme al file.",
            conferma = true
        ) { password ->
            val esito = runCatching {
                val contenuto = BlueprintTransfer.export(progetto, password)
                val nome = "mcmonitor-progetto-${progetto.script}.mcmp"
                val file = File(cacheDir, nome)
                file.writeText(contenuto)
                FileProvider.getUriForFile(this, "$packageName.updates", file)
            }
            esito.fold(
                onSuccess = { uri -> condividi(uri, progetto) },
                onFailure = { mostra("Non esportato", it.userMessage()) }
            )
        }
    }

    private fun condividi(uri: Uri, progetto: Blueprint) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Progetto del server ${progetto.serverName}")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Il progetto del server ${progetto.serverName}. " +
                                "Si apre con MC Monitor, dalla scheda Stato, pulsante Progetto. " +
                                "La password te la mando a parte."
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Manda il progetto"
            )
        )
    }

    // ------------------------------------------------------------- importa

    private fun chiediPasswordImport(uri: Uri) {
        chiediPassword(
            titolo = "Password del progetto",
            messaggio = "Quella che ti ha dato chi ti ha mandato il file.",
            conferma = false
        ) { password ->
            val esito = runCatching {
                val contenuto = contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: error("file illeggibile")
                BlueprintTransfer.import(contenuto, password)
            }
            esito.fold(
                onSuccess = { scegliCosaApplicare(it) },
                onFailure = { mostra("Non importato", it.userMessage()) }
            )
        }
    }

    /**
     * Prima di toccare i file dell'altro server si dice esattamente cosa
     * cambierà, e si lascia scegliere.
     */
    private fun scegliCosaApplicare(progetto: Blueprint) {
        val lgsm = Blueprints.transferable(progetto.lgsm)
        val prop = Blueprints.transferable(progetto.properties)

        val contenitore = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }
        val cLgsm = CheckBox(this).apply {
            text = "${lgsm.size} impostazioni di LinuxGSM"
            isChecked = lgsm.isNotEmpty()
            isEnabled = lgsm.isNotEmpty()
        }
        val cProp = CheckBox(this).apply {
            text = "${prop.size} impostazioni di Minecraft"
            isChecked = prop.isNotEmpty()
            isEnabled = prop.isNotEmpty()
        }
        val cMod = CheckBox(this).apply {
            text = "${progetto.mods.size} mod, ripresi da Modrinth"
            isChecked = progetto.mods.isNotEmpty()
            isEnabled = progetto.mods.isNotEmpty()
        }
        val giocatori = progetto.whitelist.size + progetto.ops.size
        val cGioc = CheckBox(this).apply {
            text = "$giocatori fra whitelist e operatori (server acceso)"
            isChecked = false
            isEnabled = giocatori > 0
        }
        listOf(cLgsm, cProp, cMod, cGioc).forEach { contenitore.addView(it) }

        MaterialAlertDialogBuilder(this)
            .setTitle("Progetto di ${progetto.serverName.ifBlank { "un altro server" }}")
            .setMessage(
                buildString {
                    append("Minecraft ${progetto.minecraft.ifBlank { "non indicato" }}")
                    append(", ${progetto.loader}.\n\n")
                    append("Cosa applicare a ${Prefs.load().displayName}:")
                }
            )
            .setView(contenitore)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Applica") { _, _ ->
                applica(
                    progetto,
                    BlueprintChoice(
                        lgsm = cLgsm.isChecked,
                        properties = cProp.isChecked,
                        mods = cMod.isChecked,
                        players = cGioc.isChecked
                    )
                )
            }
            .show()
    }

    private fun applica(progetto: Blueprint, scelta: BlueprintChoice) {
        occupato(true)
        binding.diario.visible(true)
        binding.diario.text = ""
        lifecycleScope.launch {
            val esito = runCatching {
                BlueprintRepository.apply(progetto, scelta) { passo ->
                    binding.diario.append("$passo\n")
                    binding.scorrevole.post {
                        binding.scorrevole.fullScroll(android.view.View.FOCUS_DOWN)
                    }
                }
            }
            occupato(false)
            mostra(
                if (esito.isSuccess) "Progetto applicato" else "Applicazione interrotta",
                esito.getOrElse { it.userMessage() }
            )
        }
    }

    // -------------------------------------------------------------- utilità

    private fun chiediPassword(
        titolo: String,
        messaggio: String,
        conferma: Boolean,
        onOk: (String) -> Unit
    ) {
        val contenitore = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }
        val prima = EditText(this).apply {
            hint = "password (almeno ${SealedBox.MIN_PASSWORD} caratteri)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val seconda = EditText(this).apply {
            hint = "ripetila"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            visible(conferma)
        }
        contenitore.addView(prima)
        contenitore.addView(seconda)

        MaterialAlertDialogBuilder(this)
            .setTitle(titolo)
            .setMessage(messaggio)
            .setView(contenitore)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Avanti", null)
            .show()
            .also { d ->
                d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val p = prima.text?.toString().orEmpty()
                    val q = seconda.text?.toString().orEmpty()
                    when {
                        p.length < SealedBox.MIN_PASSWORD ->
                            toast("Almeno ${SealedBox.MIN_PASSWORD} caratteri")
                        conferma && p != q -> toast("Le due password non coincidono")
                        else -> {
                            d.dismiss()
                            onOk(p)
                        }
                    }
                }
            }
    }

    private fun occupato(attivo: Boolean) {
        binding.progress.visible(attivo)
        binding.btnEsporta.isEnabled = !attivo
        binding.btnImporta.isEnabled = !attivo
    }

    private fun mostra(titolo: String, testo: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titolo)
            .setMessage(testo)
            .setPositiveButton("Chiudi", null)
            .show()
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
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

package com.bellizia.mcmonitor.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bellizia.mcmonitor.data.AppLock
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.ActivityLockBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * La porta d'ingresso dell'app.
 *
 * Da MC Monitor si spegne un server, si bannano giocatori e si cancella un mondo
 * intero: chi prende in mano il telefono sbloccato non deve trovarsi tutto questo
 * a disposizione. Al primo avvio la schermata si presenta e chiede come ti chiami
 * e una password; dalle volte successive chiede solo di aprire, e l'impronta fa
 * quasi sempre il lavoro al posto della password.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding

    /** Primo avvio: qui si configura invece di sbloccare. */
    private val setup: Boolean get() = !Prefs.lockConfigured && !Prefs.welcomeDone

    /**
     * Vero quando la schermata copre un'app gia' aperta (rientro dal secondo
     * piano): sbloccando si torna dov'era, senza ripartire dalla home.
     */
    private val resuming: Boolean get() = intent.getBooleanExtra(EXTRA_RESUME, false)

    companion object {
        const val EXTRA_RESUME = "resume"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        // Da bloccata non si esce con il tasto indietro: si esce dall'app.
        onBackPressedDispatcher.addCallback(this) { finishAffinity() }

        when {
            setup -> showSetup()
            // Chi ha scelto di restare senza password non deve trovarsi una porta
            // chiusa a ogni avvio.
            !Prefs.lockConfigured -> {
                AppLock.unlock()
                enterApp()
            }
            !AppLock.locked -> enterApp()
            else -> showUnlock()
        }
    }

    override fun onResume() {
        super.onResume()
        // Se il blocco e' stato tolto altrove (o non c'e'), qui non si resta.
        if (!setup && !AppLock.locked) enterApp()
    }

    // ------------------------------------------------------------ primo avvio

    private fun showSetup() {
        binding.titolo.text = if (Prefs.welcomeDone) "Proteggi l'app" else "Prima di cominciare"
        binding.spiegazione.text =
            "Da qui si accende e si spegne il server, si decide chi può entrare e si può " +
                    "anche cancellare un mondo intero. Scegli come ti chiami — comparirà " +
                    "accanto alle cose che fai — e una password che serve ad aprire l'app.\n\n" +
                    "La password non finisce da nessuna parte fuori da questo telefono: " +
                    "se la dimentichi non c'è modo di recuperarla."
        binding.nome.setText(Prefs.adminName)
        binding.boxNome.visible(true)
        binding.boxRipeti.visible(true)
        binding.usaBiometria.visible(biometricAvailable())
        binding.usaBiometria.isChecked = biometricAvailable()
        binding.btnImpronta.visible(false)
        binding.btnPrincipale.text = "Proteggi l'app"
        binding.btnSecondario.text = "Per ora senza password"
        binding.btnPrincipale.setOnClickListener { saveSetup() }
        binding.btnSecondario.setOnClickListener { skipPassword() }
    }

    private fun saveSetup() {
        val nome = binding.nome.text?.toString()?.trim().orEmpty()
        val password = binding.password.text?.toString().orEmpty()
        val ripeti = binding.ripeti.text?.toString().orEmpty()
        when {
            nome.isBlank() -> return error("Scrivi come ti chiami: serve a firmare quello che fai.")
            password.length < 4 -> return error("La password è troppo corta: almeno 4 caratteri.")
            password != ripeti -> return error("Le due password non coincidono.")
        }

        val salt = AppLock.newSalt()
        Prefs.adminName = nome
        Prefs.lockSalt = AppLock.encode(salt)
        Prefs.lockHash = AppLock.hash(password, salt)
        Prefs.lockBiometric = binding.usaBiometria.isChecked && biometricAvailable()
        Prefs.welcomeDone = true
        AppLock.unlock()
        enterApp()
    }

    /**
     * Si può rimandare, ma una volta sola e sapendo cosa si lascia aperto: la
     * protezione si accende quando si vuole dal menu.
     */
    private fun skipPassword() {
        val nome = binding.nome.text?.toString()?.trim().orEmpty()
        if (nome.isBlank()) return error("Scrivi almeno come ti chiami.")
        MaterialAlertDialogBuilder(this)
            .setTitle("Lasciare l'app senza password?")
            .setMessage(
                "Chiunque prenda il telefono potrà spegnere il server, bannare giocatori " +
                        "e cancellare un mondo. Puoi metterla più tardi dal menu, voce " +
                        "\"Blocco e amministratore\"."
            )
            .setNegativeButton("Metto la password", null)
            .setPositiveButton("Lascia aperto") { _, _ ->
                Prefs.adminName = nome
                Prefs.welcomeDone = true
                AppLock.unlock()
                enterApp()
            }
            .show()
    }

    // --------------------------------------------------------------- sblocco

    private fun showUnlock() {
        binding.titolo.text = "MC Monitor è bloccata"
        binding.spiegazione.text = buildString {
            val nome = Prefs.adminName
            if (nome.isNotBlank()) append("Ciao $nome. ")
            append("Scrivi la password per aprire.")
        }
        binding.boxNome.visible(false)
        binding.boxRipeti.visible(false)
        binding.usaBiometria.visible(false)
        binding.btnPrincipale.text = "Sblocca"
        binding.btnSecondario.text = "Password dimenticata"
        binding.btnPrincipale.setOnClickListener { checkPassword() }
        binding.btnSecondario.setOnClickListener { forgotten() }
        binding.password.setOnEditorActionListener { _, _, _ -> checkPassword(); true }

        val conImpronta = Prefs.lockBiometric && biometricAvailable()
        binding.btnImpronta.visible(conImpronta)
        binding.btnImpronta.setOnClickListener { askBiometric() }
        if (conImpronta) askBiometric()
    }

    private fun checkPassword() {
        val password = binding.password.text?.toString().orEmpty()
        if (AppLock.verify(password, Prefs.lockSalt, Prefs.lockHash)) {
            AppLock.unlock()
            enterApp()
        } else {
            error("Password sbagliata.")
            binding.password.setText("")
        }
    }

    /**
     * Non c'è nessun recupero possibile: l'impronta della password sta solo qui e
     * non si torna indietro. L'unica via è ripartire da zero, e vale la pena
     * ricordare che la configurazione esportata si può reimportare.
     */
    private fun forgotten() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Password dimenticata")
            .setMessage(
                "Non posso recuperarla: della password l'app conserva solo un'impronta, " +
                        "non la password stessa.\n\n" +
                        "L'unica strada è cancellare i dati dell'app dalle impostazioni di " +
                        "Android (Impostazioni · App · MC Monitor · Spazio · Cancella dati). " +
                        "Si perdono i server salvati, ma se avevi esportato la configurazione " +
                        "o attivato il backup automatico li ritrovi tutti reimportando il file."
            )
            .setPositiveButton("Ho capito", null)
            .show()
    }

    // ------------------------------------------------------------- biometria

    private fun biometricAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

    private fun askBiometric() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AppLock.unlock()
                    enterApp()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    // Annullare non è un errore da mostrare in rosso: resta la password.
                    if (code != BiometricPrompt.ERROR_USER_CANCELED &&
                        code != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        code != BiometricPrompt.ERROR_CANCELED
                    ) {
                        error(message.toString())
                    }
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Apri MC Monitor")
                .setSubtitle("Usa l'impronta o il volto al posto della password")
                .setNegativeButtonText("Uso la password")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
        )
    }

    // ------------------------------------------------------------------ vario

    private fun enterApp() {
        // Se stavamo solo coprendo l'app aperta, sotto c'e' gia' la schermata
        // giusta: rimetterci sopra la home farebbe perdere il punto in cui si era.
        if (!resuming) startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun error(message: String) {
        binding.esito.visible(true)
        binding.esito.text = message
    }

    private fun applyInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
    }
}

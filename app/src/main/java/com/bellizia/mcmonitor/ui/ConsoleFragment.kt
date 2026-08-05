package com.bellizia.mcmonitor.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import com.bellizia.mcmonitor.R
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.FragmentConsoleBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Scheda "Console": coda del log del server e invio comandi. */
class ConsoleFragment : Fragment() {

    private var _b: FragmentConsoleBinding? = null
    private val b get() = _b!!
    private var busy = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentConsoleBinding.inflate(inflater, container, false)
        return b.root
    }

    /**
     * Comandi rapidi: quelli che finiscono con uno spazio attendono un argomento,
     * gli altri sono completi e basta premere Invia.
     */
    private val quickCommands = listOf(
        "list", "say ", "tp ", "gamemode survival ", "time set day", "time set night",
        "weather clear", "difficulty ", "whitelist list", "whitelist add ", "kick ",
        "ban ", "pardon ", "op ", "deop ", "save-all", "seed", "kill @e[type=item]"
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.swipe.setOnRefreshListener { load() }
        b.btnSend.setOnClickListener { send() }
        b.input.setOnEditorActionListener { _, _, _ -> send(); true }
        buildQuickCommands()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    if (b.autoRefresh.isChecked && Prefs.load().isComplete) load()
                    delay(6_000)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.load().isComplete && b.log.text.isNullOrBlank()) load()
    }

    private fun buildQuickCommands() {
        quickCommands.forEach { command ->
            val chip = layoutInflater.inflate(R.layout.item_command_chip, b.quickCommands, false) as Chip
            chip.text = command.trim()
            chip.setOnClickListener {
                b.input.setText(command)
                b.input.setSelection(command.length)
                b.input.requestFocus()
                if (command.endsWith(" ")) showKeyboard() else hideKeyboard()
            }
            b.quickCommands.addView(chip)
        }
    }

    private fun showKeyboard() {
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.showSoftInput(b.input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(b.input.windowToken, 0)
    }

    private fun load() {
        if (busy) return
        if (!Prefs.load().isComplete) {
            b.swipe.isRefreshing = false
            b.log.text = "Configura il server nella scheda Impostazioni."
            return
        }
        busy = true
        b.swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { McRepository.log(400) }
            busy = false
            val bind = _b ?: return@launch
            bind.log.text = result.getOrElse { "Errore lettura log:\n${it.userMessage()}" }
                .trim().ifBlank { "(log vuoto)" }
            bind.swipe.isRefreshing = false
            bind.scroll.post { bind.scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun send() {
        val command = b.input.text?.toString()?.trim().orEmpty()
        if (command.isEmpty()) return
        if (!configured()) return
        b.btnSend.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { McRepository.send(command) }
            val bind = _b ?: return@launch
            bind.btnSend.isEnabled = true
            result.onSuccess {
                bind.input.setText("")
                toast("Comando inviato: $command")
                delay(1_200)
                load()
            }.onFailure { error ->
                // I messaggi di errore dell'invio sono lunghi e vanno letti per intero.
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Comando non inviato")
                    .setMessage(error.userMessage())
                    .setPositiveButton("Chiudi", null)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

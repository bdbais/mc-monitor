package com.bellizia.mcmonitor.ui

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.ssh.SshException
import kotlinx.coroutines.CancellationException

fun Fragment.toast(message: String) {
    if (isAdded) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
}

fun Fragment.configured(): Boolean {
    val ok = Prefs.load().isComplete
    if (!ok) toast("Configura prima il server nella scheda Impostazioni")
    return ok
}

fun Throwable.userMessage(): String = when (this) {
    is SshException -> message ?: "Errore SSH"
    is CancellationException -> "Operazione annullata"
    else -> message ?: javaClass.simpleName
}

fun View.visible(show: Boolean) {
    visibility = if (show) View.VISIBLE else View.GONE
}

/**
 * In orizzontale la tastiera di Android si prende tutto lo schermo e mostra solo
 * il campo: chi sta scrivendo non vede piu' la domanda a cui sta rispondendo.
 * Su un telefono girato, che e' come si tiene mentre si amministra un server,
 * capita in ogni dialogo.
 */
fun EditText.dialogoVisibile(): EditText = apply {
    imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
}

/**
 * Se questa copia dell'app puo' consegnare un file a un'altra app.
 *
 * Passa tutto dal FileProvider, che la variante di prova non ha: li' condividere
 * un file non fallisce con un messaggio, esplode con un'eccezione di Android in
 * inglese. Meglio chiederlo prima e dire cosa fare.
 */
fun Context.puoCondividereFile(): Boolean =
    packageManager.resolveContentProvider("$packageName.updates", 0) != null

package com.bellizia.mcmonitor.ui

import android.view.View
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

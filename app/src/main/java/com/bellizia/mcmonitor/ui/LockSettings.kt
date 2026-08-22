package com.bellizia.mcmonitor.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.biometric.BiometricManager
import com.bellizia.mcmonitor.data.AppLock
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.DialogLockSettingsBinding
import com.bellizia.mcmonitor.databinding.DialogPasswordBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Nome dell'amministratore e blocco dell'app, dopo il primo avvio.
 *
 * Chi ha rimandato la password la trova qui; chi ce l'ha può cambiarla, togliere
 * l'impronta o decidere quanto in fretta l'app si richiude da sola.
 */
object LockSettings {

    fun show(activity: Activity) {
        val form = DialogLockSettingsBinding.inflate(LayoutInflater.from(activity))
        form.nome.setText(Prefs.adminName)
        form.biometria.isChecked = Prefs.lockBiometric
        form.biometria.isEnabled = biometricAvailable(activity)
        if (!biometricAvailable(activity)) {
            form.biometria.text = "Impronta non disponibile su questo telefono"
        }
        when (Prefs.lockTimeoutMinutes) {
            0 -> form.subito.isChecked = true
            15 -> form.quindiciMinuti.isChecked = true
            -1 -> form.mai.isChecked = true
            else -> form.dueMinuti.isChecked = true
        }
        renderState(activity, form)

        MaterialAlertDialogBuilder(activity)
            .setTitle("Blocco e amministratore")
            .setView(form.root)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Salva") { _, _ ->
                Prefs.adminName = form.nome.text?.toString()?.trim().orEmpty()
                Prefs.lockBiometric = form.biometria.isChecked && biometricAvailable(activity)
                Prefs.lockTimeoutMinutes = when {
                    form.subito.isChecked -> 0
                    form.quindiciMinuti.isChecked -> 15
                    form.mai.isChecked -> -1
                    else -> 2
                }
                toast(activity, "Salvato")
            }
            .show()
    }

    private fun renderState(activity: Activity, form: DialogLockSettingsBinding) {
        val attiva = Prefs.lockConfigured
        form.statoBlocco.text = if (attiva) {
            "L'app si apre solo con la password."
        } else {
            "L'app è aperta a chiunque prenda il telefono: da qui si può spegnere il " +
                    "server e cancellare un mondo."
        }
        form.btnPassword.text = if (attiva) "Cambia la password" else "Metti una password"
        form.btnTogli.visibility = if (attiva) View.VISIBLE else View.GONE
        form.btnPassword.setOnClickListener { changePassword(activity, form) }
        form.btnTogli.setOnClickListener { removePassword(activity, form) }
    }

    private fun changePassword(activity: Activity, parent: DialogLockSettingsBinding) {
        val attiva = Prefs.lockConfigured
        val form = DialogPasswordBinding.inflate(LayoutInflater.from(activity))
        form.currentPassword.hint = "Password attuale"
        (form.currentPassword.parent.parent as? View)?.visibility =
            if (attiva) View.VISIBLE else View.GONE
        form.newPassword.hint = "Nuova password"
        form.repeatPassword.hint = "Ripeti la nuova password"

        MaterialAlertDialogBuilder(activity)
            .setTitle(if (attiva) "Cambia la password" else "Metti una password")
            .setView(form.root)
            .setNegativeButton("Annulla", null)
            .setPositiveButton(if (attiva) "Cambia" else "Attiva", null)
            .show()
            .also { dialog ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val attuale = form.currentPassword.text?.toString().orEmpty()
                    val nuova = form.newPassword.text?.toString().orEmpty()
                    val ripeti = form.repeatPassword.text?.toString().orEmpty()
                    when {
                        attiva && !AppLock.verify(attuale, Prefs.lockSalt, Prefs.lockHash) ->
                            toast(activity, "Password attuale sbagliata")
                        nuova.length < 4 -> toast(activity, "Almeno 4 caratteri")
                        nuova != ripeti -> toast(activity, "Le due non coincidono")
                        else -> {
                            val salt = AppLock.newSalt()
                            Prefs.lockSalt = AppLock.encode(salt)
                            Prefs.lockHash = AppLock.hash(nuova, salt)
                            dialog.dismiss()
                            renderState(activity, parent)
                            toast(activity, if (attiva) "Password cambiata" else "Password attiva")
                        }
                    }
                }
            }
    }

    /**
     * Togliere la password richiede comunque di conoscerla: altrimenti basterebbe
     * un minuto con il telefono in mano per disattivare la protezione.
     */
    private fun removePassword(activity: Activity, parent: DialogLockSettingsBinding) {
        val form = DialogPasswordBinding.inflate(LayoutInflater.from(activity))
        form.currentPassword.hint = "Password attuale"
        (form.newPassword.parent.parent as? View)?.visibility = View.GONE
        (form.repeatPassword.parent.parent as? View)?.visibility = View.GONE

        MaterialAlertDialogBuilder(activity)
            .setTitle("Togliere la password?")
            .setMessage("L'app tornerà ad aprirsi senza chiedere niente a nessuno.")
            .setView(form.root)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Togli", null)
            .show()
            .also { dialog ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val attuale = form.currentPassword.text?.toString().orEmpty()
                    if (!AppLock.verify(attuale, Prefs.lockSalt, Prefs.lockHash)) {
                        toast(activity, "Password sbagliata")
                    } else {
                        Prefs.lockHash = ""
                        Prefs.lockSalt = ""
                        Prefs.lockBiometric = false
                        AppLock.unlock()
                        dialog.dismiss()
                        renderState(activity, parent)
                        parent.biometria.isChecked = false
                        toast(activity, "Password tolta")
                    }
                }
            }
    }

    private fun biometricAvailable(activity: Activity): Boolean =
        BiometricManager.from(activity).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

    private fun toast(activity: Activity, message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}

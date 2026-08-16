package com.bellizia.mcmonitor

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Salva su file la traccia dell'ultimo crash e la ripropone al riavvio.
 *
 * Senza questo, un crash sul telefono si riduce a "si è chiusa": la traccia
 * resta nel logcat, che nessuno ha modo di leggere senza un computer collegato.
 */
object CrashReporter {

    private const val FILE = "ultimo-crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            // La catena va rispettata: senza, il processo resterebbe appeso.
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastCrash(context: Context): String? {
        val file = File(context.filesDir, FILE)
        return if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val when_ = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY).format(Date())
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"

        File(context.filesDir, FILE).writeText(
            buildString {
                appendLine("MC Monitor $version — $when_")
                appendLine("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Thread: ${thread.name}")
                appendLine()
                append(stack)
            }
        )
    }
}

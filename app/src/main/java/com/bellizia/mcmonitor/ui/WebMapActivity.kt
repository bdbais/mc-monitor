package com.bellizia.mcmonitor.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bellizia.mcmonitor.databinding.ActivityWebMapBinding

/**
 * Mostra la mappa web del mondo (Dynmap, BlueMap, Squaremap…) a tutto schermo.
 * È la mappa "vera" renderizzata dal server; quella interna resta per le posizioni live.
 */
class WebMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebMapBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.setPadding(0, bars.top, 0, 0)
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val url = intent.getStringExtra("url").orEmpty().let {
            if (it.startsWith("http", true)) it else "http://$it"
        }
        supportActionBar?.subtitle = url

        with(binding.web) {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding.progress.progress = newProgress
                    binding.progress.visible(newProgress < 100)
                }
            }
            loadUrl(url)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.web.canGoBack()) binding.web.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        binding.web.destroy()
        super.onDestroy()
    }
}

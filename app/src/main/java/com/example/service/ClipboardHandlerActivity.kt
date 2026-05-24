package com.example.service

import android.app.Activity
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class ClipboardHandlerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make the window transparent and 1x1 pixel size to avoid any glitch/flicker
        window.setBackgroundDrawableResource(android.R.color.transparent)
        val params = window.attributes
        params.width = 1
        params.height = 1
        params.alpha = 0f
        window.attributes = params

        // Do the check
        checkClipboardAndProcess()
    }

    private fun checkClipboardAndProcess() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "El portapapeles está vacío", Toast.LENGTH_SHORT).show()
            finish()
            overridePendingTransition(0, 0)
            return
        }

        val description = clipboard.primaryClipDescription
        if (description != null && (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML))) {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val clipText = clip.getItemAt(0).text?.toString()
                if (!clipText.isNullOrBlank()) {
                    val url = extractUrl(clipText)
                    if (url != null) {
                        // Forward the url to FloatingWidgetService to process
                        val serviceIntent = Intent(this, FloatingWidgetService::class.java).apply {
                            action = "com.example.service.PROCESS_URL"
                            putExtra("url", url)
                        }
                        startService(serviceIntent)
                    } else {
                        Toast.makeText(this, "No se encontró enlace búscado en el portapapeles", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Texto del portapapeles vacío", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Portapapeles vacío", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Copia un enlace para continuar (TikTok, Insta, YT...)", Toast.LENGTH_SHORT).show()
        }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun extractUrl(text: String): String? {
        val pattern = "https?://[\\w\\d\\-_]+(\\.[\\w\\d\\-_]+)+[\\w\\d\\-.,@?^=%&:/~+#]*".toRegex()
        val match = pattern.find(text)
        return match?.value
    }
}

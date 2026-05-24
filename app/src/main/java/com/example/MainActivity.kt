package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.service.ReminderReceiver
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

  companion object {
    val sharedUrlFlow = MutableStateFlow<String?>(null)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // Schedule daily reminders to remind them to download videos
    ReminderReceiver.scheduleDailyReminder(this)

    // Handle initial incoming Intent share
    handleIncomingIntent(intent)

    setContent {
      MyApplicationTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainScreen()
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  private fun handleIncomingIntent(intent: Intent?) {
    if (intent == null) return
    if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
      val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.clipData?.getItemAt(0)?.text?.toString()
      extractUrl(sharedText)?.let { url ->
        sharedUrlFlow.value = url
      }
    }
  }

  private fun extractUrl(text: String?): String? {
    if (text == null) return null
    val pattern = "https?://[\\w\\d\\-_]+(\\.[\\w\\d\\-_]+)+[\\w\\d\\-.,@?^=%&:/~+#]*".toRegex()
    val match = pattern.find(text)
    return match?.value
  }
}

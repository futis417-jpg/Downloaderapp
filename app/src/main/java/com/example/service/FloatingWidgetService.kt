package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.NetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingWidgetService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: FrameLayout? = null
    private var bubbleView: FrameLayout? = null
    private var popupTextView: TextView? = null
    private var closeAreaView: FrameLayout? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val CHANNEL_ID = "izi_floating_channel"
        private const val NOTIFICATION_ID = 2468
        const val ACTION_STOP = "com.example.service.STOP_BUBBLE"
        val isOverlayActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isOverlayActive.value = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    buildForegroundNotification(), 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, buildForegroundNotification())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                // Legacy standard fallback
                startForeground(NOTIFICATION_ID, buildForegroundNotification())
            } catch (ex: Exception) {
                ex.printStackTrace()
                // If even legacy fails, we don't crash, we just let the overlay run
            }
        }

        setupFloatingBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun setupFloatingBubble() {
        // Parent View
        floatingView = FrameLayout(this)

        // Bubble (the draggable circle)
        bubbleView = FrameLayout(this).apply {
            val bg = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#00F2FE"), Color.parseColor("#9D4EDD"))
            )
            bg.cornerRadius = dpToPx(28f).toFloat()
            background = bg
            elevation = dpToPx(12f).toFloat()
        }

        // Add Lightning Bolt icon to bubble
        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.stat_sys_download_done)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
        }
        bubbleView?.addView(icon)

        // Add Popup Speech Bubble Text for instructions & loading status
        popupTextView = TextView(this).apply {
            val popupBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1B1F"))
                cornerRadius = dpToPx(12f).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#00F2FE"))
            }
            background = popupBg
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            val p = dpToPx(10)
            setPadding(p * 2, p, p * 2, p)
            visibility = View.GONE
            text = "izi: ¡Toca para descargar!"
        }

        // Floating Layout Params for parent
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        // Add to main layout container
        val parentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Bubble first, then status text popup
            val bParams = FrameLayout.LayoutParams(dpToPx(56), dpToPx(56))
            addView(bubbleView, bParams)

            val textParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dpToPx(8)
            }
            addView(popupTextView, textParams)
        }

        floatingView?.addView(parentLayout)

        // Floating touch gesture listener with drag and snap logic
        bubbleView?.setOnTouchListener(object : View.OnTouchListener {
            private var isMoved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoved = false
                        // Light squeeze animation on press
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isMoved = true
                        }

                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()

                        // Snap to edges of screen
                        val displayMetrics = resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val middle = screenWidth / 2
                        val targetX = if (params.x + dpToPx(28) < middle) 16 else screenWidth - dpToPx(72)

                        // Smooth snap transition using runnable
                        handler.post(object : Runnable {
                            var currentX = params.x
                            override fun run() {
                                val diff = targetX - currentX
                                if (Math.abs(diff) > 5) {
                                    currentX += (diff * 0.25f).toInt()
                                    params.x = currentX
                                    try {
                                        windowManager.updateViewLayout(floatingView, params)
                                        handler.postDelayed(this, 10)
                                    } catch (e: Exception) {}
                                } else {
                                    params.x = targetX
                                    try {
                                        windowManager.updateViewLayout(floatingView, params)
                                    } catch (e: Exception) {}
                                }
                            }
                        })

                        if (!isMoved) {
                            onBubbleClicked()
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, params)

        // Show welcome hint
        showPopupText("¡izi Quick-Save Activo! ⚡", 3500)
    }

    private fun onBubbleClicked() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip() || clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == false) {
            showPopupText("Copia un link (TikTok, YT, IG...)", 3000)
            return
        }

        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (clipText.isNullOrBlank()) {
            showPopupText("El portapapeles está vacío", 3000)
            return
        }

        // Extract any link from clipboard text
        val url = extractUrl(clipText)
        if (url == null) {
            showPopupText("Copia un link válido", 3000)
            return
        }

        // Call background API download
        showPopupText("⚡ Analizando enlace...", 10000)
        
        serviceScope.launch {
            try {
                if (url.contains("tiktok", ignoreCase = true)) {
                    val response = withContext(Dispatchers.IO) {
                        NetworkClient.api.getVideoData(url)
                    }

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        if (body.code == 0 && body.data != null) {
                            val videoData = body.data!!
                            val downloadUrl = videoData.play ?: videoData.wmplay
                            if (!downloadUrl.isNullOrEmpty()) {
                                val downloadService = VideoDownloadService(this@FloatingWidgetService)
                                val title = videoData.title ?: "tiktok_quick_save"
                                downloadService.downloadVideo(downloadUrl, title)
                                showPopupText("¡Descargando video de TikTok! 📥", 4000)
                            } else {
                                showPopupText("❌ Video protegido o inaccesible", 3500)
                            }
                        } else {
                            showPopupText("❌ Falló TikTok: ${body.msg}", 3500)
                        }
                    } else {
                        showPopupText("❌ Error de conexión de TikTok", 3000)
                    }
                } else {
                    // Universal platform download using Cobalt API
                    val response = withContext(Dispatchers.IO) {
                        val cobaltRequest = com.example.data.CobaltRequest(url = url)
                        NetworkClient.cobaltApi.getMediaData(cobaltRequest)
                    }

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        if (body.status == "error") {
                            showPopupText("❌ Cobalt: ${body.text}", 3500)
                        } else {
                            val downloadUrl = body.url ?: body.picker?.firstOrNull()?.url
                            if (!downloadUrl.isNullOrEmpty()) {
                                val downloadService = VideoDownloadService(this@FloatingWidgetService)
                                val title = body.text ?: "universal_quick_save"
                                downloadService.downloadVideo(downloadUrl, title)
                                showPopupText("¡Descargando contenido! 📥", 4000)
                            } else {
                                showPopupText("❌ No se encontró link de descarga", 3500)
                            }
                        }
                    } else {
                        showPopupText("❌ Error de servidor universal (${response.code()})", 3500)
                    }
                }
            } catch (e: Exception) {
                showPopupText("❌ Error: ${e.localizedMessage}", 3500)
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val pattern = "https?://[\\w\\d\\-_]+(\\.[\\w\\d\\-_]+)+[\\w\\d\\-.,@?^=%&:/~+#]*".toRegex()
        val match = pattern.find(text)
        return match?.value
    }

    private fun showPopupText(message: String, durationMs: Long) {
        handler.removeCallbacksAndMessages(null)
        popupTextView?.text = message
        popupTextView?.visibility = View.VISIBLE
        bubbleView?.animate()?.scaleX(1.15f)?.scaleY(1.15f)?.setDuration(150)?.withEndAction {
            bubbleView?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(150)?.start()
        }?.start()

        handler.postDelayed({
            popupTextView?.visibility = View.GONE
        }, durationMs)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return dpToPx(dp.toFloat())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Canal izi Burbuja Flotante",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activo el widget de descarga quick-save de izi."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val stopIntent = Intent(this, FloatingWidgetService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            123,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Widget izi Activo ⚡")
            .setContentText("Copia un link de TikTok y toca el círculo para descargar.")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desactivar Burbuja", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isOverlayActive.value = false
        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {}
        }
    }
}

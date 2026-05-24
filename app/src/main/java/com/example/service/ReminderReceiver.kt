package com.example.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "izi_daily_reminders"
        private const val NOTIFICATION_ID = 5432
        private const val REQUEST_CODE = 888

        fun scheduleDailyReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Set the alarm to execute daily at 19:30 (7:30 PM) local time
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 19)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                // If past 19:30, schedule for tomorrow
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        AlarmManager.INTERVAL_DAY,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                // Background alarm permission limit on newer targets, fallback to simple schedule
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        showNotification(context)
        // Reschedule for next day (required for setExactAndAllowWhileIdle)
        scheduleDailyReminder(context)
    }

    private fun showNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recordatorios Diarios izi",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Envía sugerencias y recordatorios para descargar videos."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Alternate catchy titles and messages based on random index
        val titles = listOf(
            "izi • ¿Viste algún TikTok divertido hoy? ⚡",
            "izi • ¡No pierdas tus videos favoritos! 📥",
            "izi • Guarda sin marca de agua en 1-clic 🔥"
        )
        val messages = listOf(
            "Abra izi para descargar de forma instantánea y ultra rápida en tu galería.",
            "Descarga canciones, audios y videos HD completos sin logos molestos.",
            "Copia el enlace de cualquier TikTok y guárdalo offline de inmediato para siempre."
        )

        val randomIndex = (Math.random() * titles.size).toInt()
        val title = titles[randomIndex]
        val message = messages[randomIndex]

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

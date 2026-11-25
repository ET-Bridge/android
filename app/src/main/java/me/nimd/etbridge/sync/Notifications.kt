package me.nimd.etbridge.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import me.nimd.etbridge.MainActivity
import me.nimd.etbridge.R

object Notifications {
    const val CHANNEL_ATTENDANCE = "attendance"
    const val CHANNEL_UPDATES = "updates"
    const val CHANNEL_CLASSES = "classes"
    const val CHANNEL_APP_UPDATES = "app_updates"

    /** Fixed id for the "ETBridge updated" notification. */
    const val ID_APP_UPDATED = 0x0E7B_0001

    /** Deep-link extra read by MainActivity to open a specific tab. */
    const val EXTRA_DESTINATION = "etbridge.destination"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_ATTENDANCE, "Attendance alerts", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "When a subject drops below your attendance target" },
                NotificationChannel(CHANNEL_UPDATES, "New notices & marks", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "New notices, circulars and evaluation marks" },
                NotificationChannel(CHANNEL_CLASSES, "Class reminders", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "A heads-up a few minutes before each class" },
                NotificationChannel(CHANNEL_APP_UPDATES, "App updates", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "When ETBridge has finished updating itself" },
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        destination: String? = null,
        bigText: String? = null,
    ) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            destination?.let { putExtra(EXTRA_DESTINATION, it) }
        }
        val pi = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_etbridge)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText ?: text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setColor(0xFF3B82F6.toInt())
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}

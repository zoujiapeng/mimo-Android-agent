package com.example.mimoagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper

object Notifier {

    private const val CHANNEL_ID = "mimo_reminder"
    private var counter = 2000

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "MiMo 提醒", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "任务提醒与定时通知"
                    enableVibration(true)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun show(ctx: Context, title: String, text: String) {
        ensureChannel(ctx)
        Handler(Looper.getMainLooper()).post {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return@post
            val builder = if (Build.VERSION.SDK_INT >= 26)
                Notification.Builder(ctx, CHANNEL_ID)
            else
                @Suppress("DEPRECATION") Notification.Builder(ctx)
            val notif = builder
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .build()
            nm.notify(counter++, notif)
        }
    }
}

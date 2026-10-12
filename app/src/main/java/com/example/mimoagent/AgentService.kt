package com.example.mimoagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class AgentService : Service() {

    private var thread: Thread? = null

    @Volatile
    private var engine: AgentEngine? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                engine?.cancel()
                AgentLog.log("用户已停止")
                stopForegroundSafely()
                stopSelf()
            }
            else -> {
                val goal = intent?.getStringExtra(EXTRA_GOAL) ?: Config.load(this).goal
                startForegroundWithNotification()
                startGoal(goal)
            }
        }
        return START_NOT_STICKY
    }

    private fun startGoal(goal: String) {
        engine?.cancel()
        val cfg = Config.load(this)
        if (cfg.apiKey.isBlank()) {
            AgentLog.log("错误：请先在主界面填写 MiMo API Key")
            stopForegroundSafely()
            stopSelf()
            return
        }
        val client = MiMoClient(cfg.baseUrl, cfg.apiKey, cfg.model)
        val e = AgentEngine(applicationContext, client) { AgentLog.log(it) }
        engine = e
        thread = Thread {
            val result = e.run(goal, cfg.maxSteps)
            AgentLog.log("===== 结束：$result =====")
            stopForegroundSafely()
            stopSelf()
        }.also { it.start() }
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= 29) {
            val type = if (Build.VERSION.SDK_INT >= 34)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            startForeground(NOTIF_ID, buildNotification(), type)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
    }

    private fun stopForegroundSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CHANNEL_ID)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)
        return builder
            .setContentTitle("MiMo 助手正在执行任务")
            .setContentText("正在操作手机，请勿手动干预")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "MiMo 助手", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        engine?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "mimo_agent"
        private const val NOTIF_ID = 1001
        const val ACTION_START = "com.example.mimoagent.START"
        const val ACTION_STOP = "com.example.mimoagent.STOP"
        const val EXTRA_GOAL = "goal"

        fun start(ctx: Context, goal: String) {
            val i = Intent(ctx, AgentService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_GOAL, goal)
            }
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, AgentService::class.java).apply { action = ACTION_STOP }
            ctx.startService(i)
        }
    }
}

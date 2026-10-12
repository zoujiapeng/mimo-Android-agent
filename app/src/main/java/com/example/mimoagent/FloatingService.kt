package com.example.mimoagent

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var panel: LinearLayout? = null
    private var status: TextView? = null
    private lateinit var params: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            val last = AgentLog.snapshot().lastOrNull() ?: "空闲"
            status?.text = last
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundWithNotification()
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            AgentLog.log("悬浮窗权限未开启")
            stopSelf()
            return
        }
        addOverlay()
        handler.post(poll)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlay() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(0), dp(0), dp(0), dp(0))
        }

        val bubble = TextView(this).apply {
            text = "AI"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            background = circle(Color.parseColor("#3D5AFE"))
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        }

        val panelLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2FFFFFF"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(210), LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) }
        }

        val statusView = TextView(this).apply {
            text = "空闲"
            setTextColor(Color.parseColor("#333333"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 3
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val runBtn = Button(this).apply {
            text = "▶ 执行当前目标"
            setOnClickListener {
                val cfg = Config.load(this@FloatingService)
                if (cfg.apiKey.isBlank()) {
                    AgentLog.log("请先在主界面填写 API Key")
                } else if (!AgentAccessibilityService.isReady()) {
                    AgentLog.log("请先开启无障碍服务")
                    openApp()
                } else {
                    AgentLog.clear()
                    AgentService.start(this@FloatingService, cfg.goal)
                }
            }
        }
        val stopBtn = Button(this).apply {
            text = "■ 停止"
            setOnClickListener { AgentService.stop(this@FloatingService) }
        }
        val openBtn = Button(this).apply {
            text = "打开主界面"
            setOnClickListener { openApp() }
        }

        panelLayout.addView(statusView)
        panelLayout.addView(runBtn)
        panelLayout.addView(stopBtn)
        panelLayout.addView(openBtn)

        rootLayout.addView(bubble)
        rootLayout.addView(panelLayout)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(200)
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    wm.updateViewLayout(rootLayout, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = kotlin.math.abs(e.rawX - touchX) + kotlin.math.abs(e.rawY - touchY)
                    if (moved < dp(10)) {
                        panelLayout.visibility =
                            if (panelLayout.visibility == View.GONE) View.VISIBLE else View.GONE
                    }
                    true
                }
                else -> false
            }
        }

        wm.addView(rootLayout, params)
        root = rootLayout
        panel = panelLayout
        status = statusView
    }

    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun circle(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), Color.WHITE)
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun startForegroundWithNotification() {
        val channelId = "mimo_float"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null)
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "MiMo 悬浮球", NotificationManager.IMPORTANCE_MIN)
                )
        }
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channelId)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)
        val notif = builder
            .setContentTitle("MiMo 助手悬浮球运行中")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            val type = if (Build.VERSION.SDK_INT >= 34)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            startForeground(3001, notif, type)
        } else {
            startForeground(3001, notif)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, FloatingService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }
}

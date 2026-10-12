package com.example.mimoagent

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var etBaseUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var etGoal: EditText
    private lateinit var etSteps: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        etApiKey = findViewById(R.id.etApiKey)
        etModel = findViewById(R.id.etModel)
        etGoal = findViewById(R.id.etGoal)
        etSteps = findViewById(R.id.etSteps)

        loadConfig()

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                FloatingService.start(this)
                moveTaskToBack(true)
            }
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            saveConfig()
            val cfg = Config.load(this)
            if (cfg.apiKey.isBlank()) {
                AgentLog.log("请先填写 MiMo API Key")
                return@setOnClickListener
            }
            if (!isAccessibilityEnabled()) {
                AgentLog.log("请先开启无障碍服务（点①）")
                return@setOnClickListener
            }
            requestNotifIfNeeded()
            AgentLog.clear()
            AgentService.start(this, cfg.goal)
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener { AgentService.stop(this) }
        findViewById<Button>(R.id.btnClear).setOnClickListener { AgentLog.clear() }

        AgentLog.listener = { lines ->
            runOnUiThread { tvLog.text = lines.joinToString("\n") }
        }
        tvLog.text = AgentLog.snapshot().joinToString("\n")
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        AgentLog.listener = null
        super.onDestroy()
    }

    private fun updateStatus() {
        val on = isAccessibilityEnabled()
        tvStatus.text = if (on) "无障碍服务：已开启 ✓" else "无障碍服务：未开启 ✗"
        tvStatus.setTextColor(if (on) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (AgentAccessibilityService.isReady()) return true
        val am = getSystemService(AccessibilityManager::class.java) ?: return false
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == packageName }
    }

    private fun requestNotifIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun loadConfig() {
        val c = Config.load(this)
        etBaseUrl.setText(c.baseUrl)
        etApiKey.setText(c.apiKey)
        etModel.setText(c.model)
        etGoal.setText(c.goal)
        etSteps.setText(c.maxSteps.toString())
    }

    private fun saveConfig() {
        val steps = etSteps.text.toString().toIntOrNull() ?: 20
        Config.save(
            this,
            ConfigData(
                baseUrl = etBaseUrl.text.toString().trim(),
                apiKey = etApiKey.text.toString().trim(),
                model = etModel.text.toString().trim(),
                goal = etGoal.text.toString().trim().ifEmpty { Config.DEFAULT_GOAL },
                maxSteps = steps.coerceIn(1, 60)
            )
        )
    }
}

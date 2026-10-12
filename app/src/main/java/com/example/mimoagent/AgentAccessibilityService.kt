package com.example.mimoagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AgentAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AgentLog.log("无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun dump(): ScreenDump = ScreenReader.dump(rootInActiveWindow)

    fun tap(x: Int, y: Int) {
        val p = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(p, 0, 60)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun longPress(x: Int, y: Int) {
        val p = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(p, 0, 700)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long = 300) {
        val p = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(p, 0, duration)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun scrollDirection(direction: String): String {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val cx = w / 2
        when (direction) {
            "up" -> swipe(cx, (h * 0.72).toInt(), cx, (h * 0.28).toInt(), 300)
            "down" -> swipe(cx, (h * 0.28).toInt(), cx, (h * 0.72).toInt(), 300)
            "left" -> swipe((w * 0.80).toInt(), h / 2, (w * 0.20).toInt(), h / 2, 300)
            "right" -> swipe((w * 0.20).toInt(), h / 2, (w * 0.80).toInt(), h / 2, 300)
            else -> return "未知方向 $direction"
        }
        return "已向 $direction 滑动"
    }

    fun globalBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun globalHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val target = if (node.isEditable) node else findEditable(node)
        if (target == null) return false
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun openApp(label: String): String {
        val pm = packageManager
        val lower = label.lowercase()
        val known = mapOf(
            "qq" to "com.tencent.mobileqq",
            "微信" to "com.tencent.mm",
            "wechat" to "com.tencent.mm",
            "设置" to "com.android.settings",
            "settings" to "com.android.settings"
        )
        var pkg = known.entries.firstOrNull { lower.contains(it.key) }?.value
        if (pkg == null) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, 0)
            for (ri in apps) {
                val name = ri.loadLabel(pm).toString()
                if (name.equals(label, true) || name.contains(label)) {
                    pkg = ri.activityInfo.packageName
                    break
                }
            }
        }
        if (pkg == null) return "未找到应用「$label」"
        val launch = pm.getLaunchIntentForPackage(pkg) ?: return "无法启动 $pkg"
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
        return "已启动 $pkg"
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val found = findEditable(c)
            if (found != null) return found
        }
        return null
    }

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null

        fun isReady(): Boolean = instance != null

        fun deviceInfo(): String {
            return "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}"
        }
    }
}

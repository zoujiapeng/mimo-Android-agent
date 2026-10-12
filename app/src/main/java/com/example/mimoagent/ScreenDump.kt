package com.example.mimoagent

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class UiNode(
    val id: Int,
    val text: String,
    val desc: String,
    val cls: String,
    val clickable: Boolean,
    val editable: Boolean,
    val centerX: Int,
    val centerY: Int,
    val node: AccessibilityNodeInfo
)

class ScreenDump(val nodes: List<UiNode>) {

    val byId: Map<Int, UiNode> = nodes.associateBy { it.id }

    val isEmpty: Boolean get() = nodes.isEmpty()

    fun describe(): String {
        if (nodes.isEmpty()) return "(当前屏幕未读取到可交互元素)"
        val sb = StringBuilder()
        for (n in nodes) {
            sb.append('[').append(n.id).append("] ")
            sb.append(n.cls).append(' ')
            if (n.text.isNotEmpty()) sb.append("text=\"").append(short(n.text)).append("\" ")
            if (n.desc.isNotEmpty()) sb.append("desc=\"").append(short(n.desc)).append("\" ")
            if (n.editable) sb.append("EDITABLE ")
            if (n.clickable) sb.append("CLICKABLE ")
            sb.append("center=").append(n.centerX).append(',').append(n.centerY)
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun short(s: String): String {
        val one = s.replace('\n', ' ').replace('"', '\'')
        return if (one.length > 60) one.substring(0, 60) + "…" else one
    }
}

object ScreenReader {

    private const val MAX_NODES = 220

    fun dump(root: AccessibilityNodeInfo?): ScreenDump {
        if (root == null) return ScreenDump(emptyList())
        val out = ArrayList<UiNode>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var id = 0
        val rect = Rect()
        while (queue.isNotEmpty() && out.size < MAX_NODES) {
            val n = queue.removeFirst()
            val text = n.text?.toString()?.trim().orEmpty()
            val desc = n.contentDescription?.toString()?.trim().orEmpty()
            val editable = n.isEditable
            val clickable = n.isClickable
            val interesting = text.isNotEmpty() || desc.isNotEmpty() || editable || clickable
            if (interesting) {
                n.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0 && rect.centerX() >= 0 && rect.centerY() >= 0) {
                    out.add(
                        UiNode(
                            id = id,
                            text = text,
                            desc = desc,
                            cls = n.className?.toString()?.substringAfterLast('.') ?: "View",
                            clickable = clickable,
                            editable = editable,
                            centerX = rect.centerX(),
                            centerY = rect.centerY(),
                            node = n
                        )
                    )
                    id++
                }
            }
            val count = n.childCount
            for (i in 0 until count) {
                val c = n.getChild(i) ?: continue
                queue.add(c)
            }
        }
        return ScreenDump(out)
    }
}

package com.example.mimoagent

import org.json.JSONObject

data class Action(
    val type: String,
    val nodeId: Int,
    val text: String,
    val direction: String,
    val app: String,
    val reason: String,
    val waitMs: Long
) {
    override fun toString(): String {
        val parts = mutableListOf("action=$type")
        if (nodeId >= 0) parts.add("node=$nodeId")
        if (text.isNotEmpty()) parts.add("text=\"$text\"")
        if (direction.isNotEmpty()) parts.add("dir=$direction")
        if (app.isNotEmpty()) parts.add("app=$app")
        if (reason.isNotEmpty()) parts.add("reason=\"$reason\"")
        parts.add("wait=${waitMs}ms")
        return parts.joinToString(" ")
    }

    companion object {

        fun parse(raw: String): Action? {
            val json = extractJson(raw) ?: return null
            return try {
                val o = JSONObject(json)
                Action(
                    type = o.optString("action", o.optString("type", "")).trim().lowercase(),
                    nodeId = o.optInt("node_id", o.optInt("node", o.optInt("id", -1))),
                    text = o.optString("text", ""),
                    direction = o.optString("direction", "").trim().lowercase(),
                    app = o.optString("app", ""),
                    reason = o.optString("reason", ""),
                    waitMs = o.optLong("wait_ms", o.optLong("ms", 800L))
                )
            } catch (e: Exception) {
                null
            }
        }

        private fun extractJson(raw: String): String? {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return raw.substring(start, end + 1)
        }
    }
}

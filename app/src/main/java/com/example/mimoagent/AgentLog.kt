package com.example.mimoagent

object AgentLog {

    private val lines = ArrayList<String>()

    @Volatile
    var listener: ((List<String>) -> Unit)? = null

    @Synchronized
    fun log(message: String) {
        lines.add(message)
        while (lines.size > 400) lines.removeAt(0)
        listener?.invoke(ArrayList(lines))
    }

    @Synchronized
    fun clear() {
        lines.clear()
        listener?.invoke(ArrayList())
    }

    @Synchronized
    fun snapshot(): List<String> = ArrayList(lines)
}

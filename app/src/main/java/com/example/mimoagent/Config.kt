package com.example.mimoagent

import android.content.Context

data class ConfigData(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val goal: String,
    val maxSteps: Int
)

object Config {

    private const val PREFS = "mimo_agent"
    private const val K_BASE = "base_url"
    private const val K_KEY = "api_key"
    private const val K_MODEL = "model"
    private const val K_GOAL = "goal"
    private const val K_STEPS = "max_steps"

    const val DEFAULT_BASE = "https://api.xiaomimimo.com/v1"
    const val DEFAULT_MODEL = "mimo-v2.6-flash"
    const val DEFAULT_API_KEY = ""
    const val DEFAULT_GOAL = "打开QQ，找到「我的电脑」，给它发送一条内容为「晚安」的消息"

    fun load(ctx: Context): ConfigData {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ConfigData(
            baseUrl = sp.getString(K_BASE, DEFAULT_BASE) ?: DEFAULT_BASE,
            apiKey = sp.getString(K_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY,
            model = sp.getString(K_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
            goal = sp.getString(K_GOAL, DEFAULT_GOAL) ?: DEFAULT_GOAL,
            maxSteps = sp.getInt(K_STEPS, 20)
        )
    }

    fun save(ctx: Context, data: ConfigData) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_BASE, data.baseUrl)
            .putString(K_KEY, data.apiKey)
            .putString(K_MODEL, data.model)
            .putString(K_GOAL, data.goal)
            .putInt(K_STEPS, data.maxSteps)
            .apply()
    }
}

package com.example.mimoagent

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class MiMoClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String
) {

    @Throws(IOException::class)
    fun chat(system: String, user: String): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))

        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put("stream", false)
            .put("messages", messages)
            .toString()

        val endpoint = if (baseUrl.trimEnd('/').endsWith("/chat/completions"))
            baseUrl.trimEnd('/')
        else
            baseUrl.trimEnd('/') + "/chat/completions"

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 180000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code: ${text.take(300)}")
            }
            val obj = JSONObject(text)
            val choices = obj.optJSONArray("choices")
                ?: throw IOException("返回缺少 choices: ${text.take(300)}")
            val message = choices.getJSONObject(0).optJSONObject("message")
                ?: throw IOException("返回缺少 message")
            return message.optString("content", "")
        } finally {
            conn.disconnect()
        }
    }
}

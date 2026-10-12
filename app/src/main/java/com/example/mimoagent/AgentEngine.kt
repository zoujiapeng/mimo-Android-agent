package com.example.mimoagent

import android.content.Context

class AgentEngine(
    private val appContext: Context,
    private val client: MiMoClient,
    private val onLog: (String) -> Unit
) {

    @Volatile
    var cancelled: Boolean = false
        private set

    fun cancel() {
        cancelled = true
    }

    fun run(goal: String, maxSteps: Int = 20): String {
        val service = AgentAccessibilityService.instance
            ?: return "无障碍服务未开启，请先在系统设置里开启「MiMo 助手」"

        onLog("目标：$goal")
        onLog("设备：${AgentAccessibilityService.deviceInfo()}")

        val history = StringBuilder()
        for (step in 1..maxSteps) {
            if (cancelled) return "已停止"

            val dump = service.dump()
            if (dump.isEmpty) {
                onLog("步骤 $step：屏幕暂无可读元素，等待 1s 重试")
                sleep(1000)
                continue
            }

            val userContent = buildString {
                append("【目标】").append(goal).append('\n')
                if (history.isNotEmpty()) {
                    append("\n【历史操作】\n").append(history)
                }
                append("\n【当前屏幕元素】\n").append(dump.describe())
                append("\n请输出下一步动作的 JSON。")
            }

            val reply = try {
                client.chat(SYSTEM_PROMPT, userContent)
            } catch (e: Exception) {
                onLog("步骤 $step：调用模型失败 - ${e.message}")
                return "模型调用失败：${e.message}"
            }

            onLog("步骤 $step 模型输出：${reply.trim().take(400)}")

            val action = Action.parse(reply)
            if (action == null) {
                history.append("步骤$step：模型输出无法解析为 JSON，请严格只返回一个 JSON 对象。\n")
                onLog("步骤 $step：无法解析动作，重试")
                sleep(500)
                continue
            }

            if (action.type == "finish" || action.type == "done") {
                val reason = action.reason.ifEmpty { "任务完成" }
                onLog("完成：$reason")
                return reason
            }

            val result = execute(service, action, dump)
            onLog("步骤 $step 执行：${action} -> $result")
            history.append("步骤$step：执行 ").append(action).append(" 结果：").append(result).append('\n')

            val extra = if (action.type == "wait") 0L else action.waitMs.coerceIn(300, 5000)
            sleep(extra)
        }
        return "达到最大步数，任务可能未完成"
    }

    private fun execute(
        service: AgentAccessibilityService,
        action: Action,
        dump: ScreenDump
    ): String {
        return when (action.type) {
            "tap", "click" -> {
                val n = dump.byId[action.nodeId]
                    ?: return "节点 ${action.nodeId} 不存在（请重新查看屏幕元素）"
                service.tap(n.centerX, n.centerY)
                "已点击 [${action.nodeId}]"
            }

            "long_press" -> {
                val n = dump.byId[action.nodeId]
                    ?: return "节点 ${action.nodeId} 不存在"
                service.longPress(n.centerX, n.centerY)
                "已长按 [${action.nodeId}]"
            }

            "set_text", "input" -> {
                val n = dump.byId[action.nodeId]
                    ?: return "节点 ${action.nodeId} 不存在"
                val ok = service.setText(n.node, action.text)
                if (ok) "已输入「${action.text}」" else "输入失败（该节点可能不可编辑）"
            }

            "swipe", "scroll" -> service.scrollDirection(action.direction.ifEmpty { "up" })

            "back" -> {
                service.globalBack()
                "返回上一页"
            }

            "home" -> {
                service.globalHome()
                "回到桌面"
            }

            "open_app" -> service.openApp(action.app)

            "notify" -> {
                val text = action.text.ifEmpty { "时间到了" }
                Notifier.show(appContext, "MiMo 提醒", text)
                "已发送通知「$text」"
            }

            "wait" -> {
                sleep(action.waitMs.coerceIn(0, 600000))
                "已等待 ${action.waitMs}ms"
            }

            else -> "未知动作「${action.type}」，可用动作见系统说明"
        }
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private val SYSTEM_PROMPT = """
你是一个 Android 手机自动化 Agent。你会看到当前屏幕上的可交互元素列表，需要决策下一步操作来完成用户目标。

【必须遵守】
1. 只输出一个 JSON 对象，不要任何解释、markdown 或多余文字。
2. 每次只执行一个动作，动作执行后你会看到新的屏幕元素列表。
3. 必须基于真实的屏幕元素 id 操作，不要臆造节点。

【可用动作】
{"action":"open_app","app":"QQ"}
{"action":"tap","node_id":3}
{"action":"long_press","node_id":3}
{"action":"set_text","node_id":5,"text":"晚安"}
{"action":"swipe","direction":"up"}
{"action":"back"}
{"action":"home"}
{"action":"wait","wait_ms":30000}
{"action":"notify","text":"时间到了，该休息啦"}
{"action":"finish","reason":"已发送晚安"}

【决策原则】
- 目标应用没在前台时，先用 open_app 打开。
- 界面出现弹窗、广告、更新提示、权限框时，先用 back 或点击关闭按钮，再继续原任务。
- 目标元素不在视野内时，用 swipe 滑动查找。
- 输入文字前先 tap 输入框使其获得焦点，再用 set_text。
- 发送消息后，屏幕出现自己发送的「晚安」气泡即视为成功，返回 finish。
- 同一动作连续失败两次以上时，换一种方式（如滑动、返回、或换入口）。
- 若用户要求「X 秒/分钟后再提醒我/叫我」，先 wait 对应的毫秒数（wait_ms 可到 600000），再用 notify 发送通知，最后 finish。例如 30 秒后提醒：先 {"action":"wait","wait_ms":30000}，再 {"action":"notify","text":"用户要求的时间到了"}。
- 纯定时提醒类任务不需要操作屏幕，直接用 wait + notify 即可完成。
""".trimIndent()
    }
}

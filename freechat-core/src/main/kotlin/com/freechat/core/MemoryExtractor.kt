package com.freechat.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 记忆摘录器（1.0.91 M1 第二刀从 ChatViewModel.summarizeExchange 平移）：
 * 提示词构建与响应解析是纯逻辑；HTTP 调用留在各端（Android=ChatViewModel，服务器=ModelClient）。
 *
 * 摘录器 prompt 的 JSON schema 值位放说明文本（「YYYY-MM-DD，不确定就留空」）是既有风格，
 * 解析端 [JsonLoose] 对格式宽容——别「顺手修正」引号结构。
 */

/** 一轮记忆摘录的产出（1.0.73）：记忆条目 + 同义词检索词 + 氛围快照三件套 */
data class MemoryDraft(
    val summary: String = "", val kind: String = "detail", val date: String = "",
    val keywords: List<String> = emptyList(),
    val mood: String = "", val atmosphere: String = "", val warmth: Int = 0
)

object MemoryExtractor {

    /** 假名守卫：丢弃含日文假名的总结/日期，防止日文污染记忆 */
    private fun hasKana(s: String): Boolean = s.any { it in '぀'..'ヿ' }

    /**
     * 摘录/归纳两套提示词（role/content 消息表，直接进 chat completions）。
     * highQuality=原文摘录（改写=罪）；普通=归纳。plotMode 附信息边界（剧情文本里只有真说给 AI 的话才算数）
     * 与世界历法日期规则。
     */
    fun buildMessages(
        userText: String, aiReply: String,
        highQuality: Boolean, plotMode: Boolean = false, assocLevel: Float = 1f,
        nowMillis: Long = System.currentTimeMillis(),
        modelManagedAssociation: Boolean = false
    ): List<Map<String, String>> {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowStr = SimpleDateFormat("yyyy年M月d日", Locale.CHINESE).format(nowCal.time)
        // 带星期几：把「上周三」「三天前」换算成绝对日期要靠它
        val nowFull = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE).format(nowCal.time)
        // ★ 剧情模式的信息边界：用户的输入是「剧情文本」，里面大部分不是对 AI 这个角色说的话
        //   （旁白、动作/环境/心理描写、当面对第三个人说的话）。把这些摘成「已知事实」会污染长期记忆，
        //   让角色下一轮就"知道"它本不该知道的事，所以这里明确要求只摘录真正说给 AI 的话。
        val boundaryRule = if (plotMode)
            "\n★ 信息边界（剧情模式，必须严格遵守）：用户发来的是剧情文本，其中只有「真正发给 AI 角色的消息/说给 AI 角色听的话」（微信消息、对话中说出口且对象是 AI 角色）才算 AI 知道的事。用户在场景里对第三个人（比如张三、C）说的话、以及旁白、环境描写、动作描写、心理描写，AI 角色都不在场、听不到、也不知道，绝对不要摘录成「AI 知道的事实」；如果摘录时无法确定某句话是不是说给 AI 的，就不要摘录。" else ""
        // Android opts into model-managed wording. Keep the legacy default unchanged for
        // the already-deployed companion server and older consumers of this shared module.
        val kwSpec = if (modelManagedAssociation) {
            "自主选择少量、准确的事实关键词；只有语义明确等同时才可采用同义说法，不要求扩展别名、上位词或相关概念，不要猜测、过度联想"
        } else {
            val count = if (assocLevel >= 3f) "5-8 个词（必含同义词/俗称/上位词）" else "2-3 个原词"
            "填 ${count}，涵盖原词、同义词、俗称、上位词（例如「猫、喵星人、宠物」）"
        }
        // 叙事档的日期用世界历法（用户提示词里设定的时间体系），不掺现实日期；微信档 = 现实绝对日期
        val dateRuleExtra = if (plotMode) "这件事发生的时间：用世界设定的历法/时间表述（如「大雍三年三月初五」，见用户的世界规则），没有历法就用 YYYY-MM-DD，不确定就留空"
            else "YYYY-MM-DD，不确定就留空"
        val prompt = if (highQuality) {
            listOf(
                mapOf("role" to "system", "content" to "你是记忆摘录器。从用户的消息里摘录「需要长期记住的事实性原文」，原样摘录用户的原话，绝不改写、概括、补充或脑补。判断类型：\n- plot：剧情主线走向、关系转变、重大事件、重要承诺、关键背景设定、未来计划/约定、用户明确的个人信息\n- detail：普通日常闲聊的细节（近期有效即可）\n\n摘录规则（重要）：\n1. 只摘录用户消息里明确说的事实（人物、关系、时间、地点、数字、承诺、计划、喜好、经历），用用户的原话，不要用你自己的话转述。\n2. 时间语境（如「高考后的暑假」「去年」「上周」）要原样保留，不要丢失，也不要擅自换算或脑补成别的时间。\n3. 数字、日期、专有名词必须精确，一字不差。\n4. 用户没说过的信息绝对不要脑补。\n5. summary 里必须保留用户原话中的时间语境（如「高考后的暑假」「去年冬天」），照抄，不要改写或丢掉。\n6. keywords 是给日后检索用的词表：${kwSpec}。\n7. mood 填角色这轮结束时的情绪（开心/兴奋/平静/生气/难过/委屈/敷衍/害羞/无聊 之一）；atmosphere 用一句话写下「这轮聊完时你们之间的气氛」（如「前天吵过架，还冷着」「聊得很甜」）；warmth 给关系温度粗档 -2..2（-2 冷淡、0 平稳、2 热络），拿不准填 0。\n8. 另外判断「这件事本身发生在哪一天」：文中有「昨天」「上周三」「8月20日」「暑假」这类线索时，结合今天（$nowFull）换算成绝对日期，写成 YYYY-MM-DD 填进 date；只是闲聊、没有具体事件日期、或你无法确定时，date 填空字符串——绝不要猜、不要用今天顶替。$boundaryRule\n只输出一行 JSON：{\"summary\":\"摘录的原文片段\",\"kind\":\"plot\"或\"detail\",\"date\":\"${dateRuleExtra},\"keywords\":[\"词\"],\"mood\":\"\",\"atmosphere\":\"\",\"warmth\":0}"),
                mapOf("role" to "user", "content" to "用户说：$userText\nAI回复：${aiReply.take(200)}\n请摘录用户消息里的事实原文：")
            )
        } else {
            listOf(
                mapOf("role" to "system", "content" to "你是记忆归纳器。用 1-3 句中文总结以下对话的关键信息，并判断它属于哪一类：\n- plot：剧情主线走向、关系转变、重大事件、重要承诺、关键背景设定、以及任何「未来某天要做的事」（考试、约定、生日、计划等，长期贯穿，需始终记住）\n- detail：普通日常闲聊的细节（近期有效即可）\n\n今天是 $nowStr。重要规则：\n1. 用户提到的未来事件（如「5天后考试」「下周三见面」）必须换算成具体绝对日期（如「9月4日考试」），绝不能保留「5天后」这类相对说法，否则之后会算错时间。\n2. 用户的个人信息、计划、承诺、喜好等要原样准确记录，数字和日期不要概括丢失。\n3. 再填一个 date：「这件事本身发生在哪一天」（今天是 $nowFull）。用户说「昨天」「上周三」「8月20日」时换算成 YYYY-MM-DD；只是闲聊、没有具体事件日期、或你无法确定就留空——绝不要猜。\n4. keywords 是给日后检索用的词表：${kwSpec}。\n5. mood 填角色这轮结束时的情绪（开心/兴奋/平静/生气/难过/委屈/敷衍/害羞/无聊 之一）；atmosphere 用一句话写下「这轮聊完时你们之间的气氛」；warmth 给关系温度粗档 -2..2，拿不准填 0。$boundaryRule\n只输出一行 JSON：{\"summary\":\"总结内容\",\"kind\":\"plot\"或\"detail\",\"date\":\"${dateRuleExtra},\"keywords\":[\"词\"],\"mood\":\"\",\"atmosphere\":\"\",\"warmth\":0}"),
                mapOf("role" to "user", "content" to "用户说：$userText\nAI回复：${aiReply.take(200)}\n请总结：")
            )
        }
        return prompt
    }

    /**
     * 模型回复 → [MemoryDraft]。假名/空回复=空草稿（不写记忆）；JSON 解析失败退化为纯总结（detail）。
     * [plotMode] 决定日期校验走世界历法 freeform。
     */
    fun parseDraft(raw0: String, plotMode: Boolean = false): MemoryDraft {
        val raw = raw0.trim()
        // 假名守卫：丢弃含日文假名的总结，防止日文污染记忆
        if (raw.isEmpty() || hasKana(raw)) return MemoryDraft()
        val obj = JsonLoose.extractObject(raw)
        return if (obj != null) {
            val summary = obj.get("summary")?.asString?.trim() ?: ""
            val kind = obj.get("kind")?.asString?.trim() ?: "detail"
            val date = MemoryLogic.normalizeEventDate(obj.get("date")?.asString ?: "", freeform = plotMode)
            val kws = obj.get("keywords")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString?.trim() }
                ?.filter { it.isNotBlank() && it.length <= 12 }?.distinct()?.take(8) ?: emptyList()
            val mood = obj.get("mood")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            val atmo = obj.get("atmosphere")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.take(40).orEmpty()
            val warmth = obj.get("warmth")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
                ?.let { runCatching { it.asInt }.getOrNull() }?.coerceIn(-2, 2) ?: 0
            MemoryDraft(summary, if (kind == "plot") "plot" else "detail", date, kws, mood, atmo, warmth)
        } else {
            // 没输出 JSON，退化为纯总结（按 detail）
            MemoryDraft(summary = raw, kind = "detail")
        }
    }
}

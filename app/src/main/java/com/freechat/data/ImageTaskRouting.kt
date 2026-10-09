package com.freechat.data

import com.freechat.model.Message
import com.freechat.model.Role

/** Image work belongs to FreeChat, not to the selected language model's tool-call support. */
object ImageTaskRouting {
    enum class Kind { GENERATE, EDIT, VISION }
    data class Plan(val kind: Kind, val prompt: String, val referenceSources: List<String> = emptyList(),
        val referenceMessageId: String? = null)

    private val pictureWords = "图片|图像|照片|海报|插画|插图|配图|头像|壁纸|绘画|image|picture|photo|poster|illustration"
    private val generate = Regex("(?:生成|制作|创建|做|绘制).{0,24}(?:$pictureWords)|(?:画|绘制)(?:一|个|张|幅|只|些|出)|" +
        "(?:帮我|给我)画|画图|画画|(?:draw|paint|render)\\s+(?:a|an|the)\\b|" +
        "(?:generate|create|make)\\s+(?:a |an |the )?(?:image|picture|photo|poster|illustration)", RegexOption.IGNORE_CASE)
    private val repeat = Regex("再来一张|再(?:生成|画|出|做)(?:一张|一幅|张)?|重新(?:生成|画|绘制)|重画|" +
        "(?:generate|draw|make|create)\\s+(?:another|again)|another\\s+(?:image|picture|one)", RegexOption.IGNORE_CASE)
    private val bareRepeat = Regex("^(?:${repeat.pattern})(?:一下|一次|一遍|一张|一幅|张|吧| one)?[。.!！?？]*$", RegexOption.IGNORE_CASE)
    private val imageReference = Regex("上(?:一|个)张|上一幅|刚才(?:那张|的图)|这张(?:图|照片)|这幅|原图|" +
        "(?:previous|last|this)\\s+(?:image|picture|photo)", RegexOption.IGNORE_CASE)
    private val edit = Regex("修图|[pP]图|改图|换背景|背景.{0,8}(?:换|改)|抠图|加滤镜|去水印|调色|改色|" +
        "变清晰|风格(?:转换|化)|(?:亮|暗|清晰|鲜艳|自然)一点|全身照|大头照|" +
        "改成|换成|修改|重绘|(?:edit|retouch|enhance|change|replace|make).{0,28}(?:image|picture|photo|background|brighter|darker)|" +
        "(?:brighter|darker)", RegexOption.IGNORE_CASE)
    private val analysis = Regex("分析|识别|描述|看看|看图|读图|识图|是什么|有什么|图中|图里|图上|提取文字|翻译|OCR|" +
        "describe|analy[sz]e|what is|what.*(?:image|picture|photo)", RegexOption.IGNORE_CASE)
    private val otherWork = Regex("代码|程序|文章|提示词|随机数|文档|报告|故事|诗歌|表格|PPT|Excel|Python|Kotlin|JavaScript", RegexOption.IGNORE_CASE)

    fun informationOnly(text: String): Boolean {
        val t = text.trim()
        if (Regex("(?:不要|不用|不需要|先别|别)(?:再)?(?:生成|画|绘制)(?:图片|图像|图)|只(?:写|要|给|提供).{0,12}提示词").containsMatchIn(t)) return true
        if (Regex("^(?:怎么|如何|怎样).*(?:$pictureWords|画|绘制)|^how (?:do|can|to).*(?:$pictureWords|draw|paint)", RegexOption.IGNORE_CASE)
                .containsMatchIn(t)) return true
        // Polite requests with a concrete subject are work, not questions about capability.
        if (Regex("(?:画|绘制)(?:一只|一个|一张|一幅|个|只)(?!(?:$pictureWords|图)(?:吗|么|呢|\\?|？|$)).+|" +
                "(?:生成|制作|创建)(?:一张|一幅|一个|个|一只).+(?:$pictureWords)|" +
                "(?:draw|paint)\\s+(?:a|an)\\s+(?!(?:$pictureWords)(?:\\?|$)).+|" +
                "(?:generate|create|make)\\s+(?:(?:a|an|the)\\s+)?(?:$pictureWords)\\s+(?:of|showing|depicting)\\s+.+",
                RegexOption.IGNORE_CASE).containsMatchIn(t)) return false
        return Regex("^(?:你|你们)?(?:能|可以|会|能不能).*(?:吗|么|呢|\\?|？)$|" +
            "^(?:怎么|如何|怎样).*(?:$pictureWords)|^(?:can|could|do) you.*(?:$pictureWords)|^how (?:do|can|to).*(?:$pictureWords)",
            RegexOption.IGNORE_CASE).containsMatchIn(t)
    }

    private fun sources(row: Message): List<String> =
        if (row.failed || row.isStreaming || row.sceneVisualization) emptyList()
        else (row.imageUrls.orEmpty() + row.imagePaths.orEmpty()).filter { it.isNotBlank() }.distinct()

    fun hasReference(history: List<Message>): Boolean = history.any { sources(it).isNotEmpty() }

    fun plan(text: String, uploadedSources: List<String>, history: List<Message>, forceGeneration: Boolean): Plan? {
        val t = text.trim()
        if (!forceGeneration && informationOnly(t)) return null
        val past = if (history.lastOrNull()?.let { it.role == Role.USER && it.content == t } == true) history.dropLast(1) else history
        val previousIndex = past.indexOfLast { sources(it).isNotEmpty() }
        val previous = past.getOrNull(previousIndex)
        val immediate = previous != null && past.lastOrNull { it.role == Role.ASSISTANT }?.id == previous.id
        val references = uploadedSources.filter { it.isNotBlank() }.distinct()
        val linked = imageReference.containsMatchIn(t)
        if (!forceGeneration && references.isEmpty() && !immediate && !linked && bareRepeat.matches(t)) return null
        val followUp = previous != null && ((linked && (generate.containsMatchIn(t) || edit.containsMatchIn(t))) ||
            (!otherWork.containsMatchIn(t) && (linked || (immediate && (repeat.containsMatchIn(t) ||
                (!generate.containsMatchIn(t) && edit.containsMatchIn(t)))))))

        val kind = when {
            !forceGeneration && analysis.containsMatchIn(t) && !edit.containsMatchIn(t) && (references.isNotEmpty() || linked) -> Kind.VISION
            followUp && references.isEmpty() -> if (analysis.containsMatchIn(t) && !edit.containsMatchIn(t)) Kind.VISION else Kind.EDIT
            references.isNotEmpty() && (forceGeneration || edit.containsMatchIn(t) || generate.containsMatchIn(t)) -> Kind.EDIT
            forceGeneration || generate.containsMatchIn(t) -> Kind.GENERATE
            references.isNotEmpty() -> Kind.VISION
            else -> return null
        }
        val selected = references.ifEmpty { if (followUp || (linked && kind == Kind.VISION)) previous?.let(::sources).orEmpty() else emptyList() }
        val previousPrompt = previous?.imagePrompt?.takeIf { it.isNotBlank() }
            ?: past.take(previousIndex.coerceAtLeast(0)).lastOrNull { it.role == Role.USER }?.content.orEmpty()
        val prompt = if (kind == Kind.EDIT && followUp && references.isEmpty() && previousPrompt.isNotBlank() && !t.startsWith(previousPrompt))
            "$previousPrompt\n\n本次画面要求（保留未要求改变的主体和细节，结合参考图）：\n$t" else t
        return Plan(kind, prompt, if (kind == Kind.VISION) selected else selected.take(3),
            previous?.id?.takeIf { references.isEmpty() && selected.isNotEmpty() })
    }

    fun regeneration(text: String, uploadedSources: List<String>, history: List<Message>, reply: Message): Plan {
        val inferred = requireNotNull(plan(text, uploadedSources, history, true))
        val prompt = reply.imagePrompt?.takeIf(String::isNotBlank) ?: inferred.prompt
        val referenceId = reply.imageReferenceMessageId ?: return inferred.copy(prompt = prompt)
        val references = history.find { it.id == referenceId }?.let(::sources).orEmpty().take(3)
        return Plan(Kind.EDIT, prompt, references, referenceId)
    }

    fun forTool(name: String, prompt: String, userText: String, uploadedSources: List<String>, history: List<Message>): Plan? {
        val kind = when (name) { "generate_image" -> Kind.GENERATE; "edit_image" -> Kind.EDIT; "analyze_image" -> Kind.VISION; else -> return null }
        val detected = plan(userText, uploadedSources, history, false)
        if (detected != null) return detected
        val previous = history.lastOrNull { sources(it).isNotEmpty() }
        val refs = if (kind == Kind.GENERATE) emptyList() else uploadedSources.ifEmpty { previous?.let(::sources).orEmpty() }
        return Plan(kind, prompt.ifBlank { userText }, if (kind == Kind.VISION) refs else refs.take(3),
            previous?.id?.takeIf { uploadedSources.isEmpty() && refs.isNotEmpty() })
    }
}

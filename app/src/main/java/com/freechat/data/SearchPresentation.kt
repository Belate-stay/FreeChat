package com.freechat.data

import com.freechat.model.SearchCitation
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object SearchPresentation {
    data class DisplayContent(val answer: String, val sources: List<SearchCitation>)

    /** 不限制为八条：保留本轮取得的全部有效来源，按 URL 去重，与开关状态无关。 */
    fun normalize(sources: List<Pair<String, String>>): List<SearchCitation> = sources.mapNotNull { (title, url) ->
        val address = httpAddress(url) ?: return@mapNotNull null
        SearchCitation(title.trim().ifBlank { address.host }, address.toString())
    }.distinctBy { it.url }

    /** Citation visibility never depends on the settings switch. Code/originals stay byte-for-byte. */
    fun forDisplay(content: String, storedSources: List<SearchCitation>?, answerLinksRequested: Boolean? = null): DisplayContent {
        val legacy = if (answerLinksRequested == true) DisplayContent(content, storedSources.orEmpty())
            else if (storedSources == null) splitLegacyFooter(content)
            else if (answerLinksRequested == false) splitLegacyFooter(content).let {
                DisplayContent(it.answer, storedSources + it.sources)
            } else DisplayContent(content, storedSources)
        val sources = normalize(legacy.sources.filterNotNull().map { it.title.orEmpty() to it.url.orEmpty() })
        if (answerLinksRequested == true) return DisplayContent(legacy.answer, sources)
        val known = sources.mapTo(HashSet()) { it.url }
        val extracted = mutableListOf<Pair<String, String>>()
        val references = mutableMapOf<String, Pair<String, String>>()
        fun move(title: String, url: String): Boolean {
            val normalized = httpAddress(url)?.toString() ?: return false
            if (answerLinksRequested != false && normalized !in known) return false
            extracted.add(title to normalized)
            return true
        }
        // Run only outside fences and inline code. First resolve reference-style Markdown links.
        val withoutDefinitions = mapProse(legacy.answer) { prose ->
            referenceDefinition.replace(prose) { match ->
                val key = match.groupValues[1].lowercase()
                val url = match.groupValues[2]
                if (move(key, url)) { references[key] = key to url; "" } else match.value
            }
        }
        val answer = mapProse(withoutDefinitions) { prose ->
            var text = bodyLink.replace(prose) { match ->
                if (move(match.groupValues[1], match.groupValues[2])) match.groupValues[1] else match.value
            }
            text = referenceLink.replace(text) { match ->
                val ref = references[match.groupValues[2].ifBlank { match.groupValues[1] }.lowercase()]
                if (ref != null) { extracted.add(match.groupValues[1] to ref.second); match.groupValues[1] } else match.value
            }
            text = autoLink.replace(text) { match ->
                val url = match.groupValues[1]
                if (move(plainHost(url), url)) plainHost(url) else match.value
            }
            // Keep a half-arrived citation from flashing a raw URL during streaming.
            if (answerLinksRequested == false) text = unfinishedLink.replace(text) { it.groupValues[1] }
            bareUrl.replace(text) { match ->
                val url = match.value.trimEnd('.', ',', ';', '!', '。', '，', '；', '！', ')', ']', '}')
                val suffix = match.value.substring(url.length)
                val host = plainHost(url)
                if (move(host, url)) host + suffix else match.value
            }
        }
        return DisplayContent(answer, normalize(sources.map { it.title to it.url } + extracted))
    }

    /** URL requests are distinct from asking for verifiable information. Negated requests win. */
    fun linksRequested(request: String): Boolean {
        if (Regex("(?:不要|不用|无需|不需要|别给|不显示).{0,6}(?:链接|网址|URL)|(?:no|without|do not).{0,12}(?:links?|urls?)", RegexOption.IGNORE_CASE).containsMatchIn(request)) return false
        return Regex("网址|网站|链接|官网|下载地址|下载入口|仓库地址|\\b(?:urls?|websites?|links?|homepage|download|repository)\\b", RegexOption.IGNORE_CASE).containsMatchIn(request)
    }

    private val bodyLink = Regex("(?<!!)\\[([^\\]\\r\\n]*)\\]\\(((?:https?://|www\\.)(?:[^\\s()]|\\([^\\r\\n]*?\\))+)(?:\\s+\"[^\"]*\")?\\)", RegexOption.IGNORE_CASE)
    private val autoLink = Regex("<((?:https?://|www\\.)[^<>\\s]+)>", RegexOption.IGNORE_CASE)
    private val bareUrl = Regex("(?:https?://|www\\.)[^\\s<>\"'“”‘’，。；！？、《》]+", RegexOption.IGNORE_CASE)
    private val unfinishedLink = Regex("\\[([^\\]\\r\\n]*)\\]\\((?:https?://|www\\.)[^)\\r\\n]*$", RegexOption.IGNORE_CASE)
    private val referenceDefinition = Regex("^ {0,3}\\[([^\\]\\r\\n]+)\\]:\\s*<?((?:https?://|www\\.)[^\\s>]+)>?(?:\\s+\"[^\"]*\")?\\s*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    private val referenceLink = Regex("(?<!!)\\[([^\\]\\r\\n]+)\\]\\[([^\\]\\r\\n]*)\\]")
    private val protectedInline = Regex("(`+).*?\\1|!\\[[^\\]\\r\\n]*\\]\\([^\\r\\n]*\\)")

    private fun httpAddress(value: String) =
        (if (value.startsWith("www.", ignoreCase = true)) "https://$value" else value).toHttpUrlOrNull()
    // MarkdownText auto-links www. hosts too: never turn a removed citation into another link.
    private fun plainHost(url: String) = httpAddress(url)?.host.orEmpty().removePrefix("www.")

    private fun mapProse(content: String, transform: (String) -> String): String {
        var fence: String? = null
        return content.split('\n').joinToString("\n") { line ->
            val marker = Regex("^\\s{0,3}(`{3,}|~{3,})(.*)$").find(line)
            val open = fence
            if (marker != null) {
                val token = marker.groupValues[1]
                if (open == null) fence = token
                else if (token.first() == open.first() && token.length >= open.length && marker.groupValues[2].isBlank()) fence = null
                line
            } else if (open != null) line
            else buildString {
                var start = 0
                protectedInline.findAll(line).forEach { match ->
                    append(transform(line.substring(start, match.range.first)))
                    append(match.value)
                    start = match.range.last + 1
                }
                append(transform(line.substring(start)))
            }
        }
    }

    private fun splitLegacyFooter(content: String): DisplayContent {
        val separator = Regex("\\r?\\n[ \\t]*\\r?\\n").findAll(content).lastOrNull()
            ?: return DisplayContent(content, emptyList())
        val answer = content.substring(0, separator.range.first).trimEnd()
        val footer = content.substring(separator.range.last + 1).trim()
        if (answer.isBlank() || openFence(answer) != null || !legacyFooter.matches(footer))
            return DisplayContent(content, emptyList())
        val sources = normalize(legacyLink.findAll(footer).map { it.groupValues[1] to it.groupValues[2] }.toList())
        return if (sources.isEmpty()) DisplayContent(content, emptyList()) else DisplayContent(answer, sources)
    }

    fun asMarkdown(sources: List<SearchCitation>): String = sources.joinToString("\n\n") { source ->
        val label = source.title.replace(Regex("[\\[\\]\\r\\n*_`<>]"), " ").trim()
        "[$label](${escapeUrl(source.url)})"
    }

    private val legacyLink = Regex("\\[([^\\]\\r\\n]+)\\]\\((https?://[^\\s)]+)\\)", RegexOption.IGNORE_CASE)
    private val legacyFooter = Regex("${legacyLink.pattern}(?:\\s+·\\s+${legacyLink.pattern})*", RegexOption.IGNORE_CASE)

    private fun openFence(content: String): String? {
        var fence: String? = null
        content.lineSequence().forEach { line ->
            val match = Regex("^\\s{0,3}(`{3,}|~{3,})(.*)$").find(line) ?: return@forEach
            val marker = match.groupValues[1]
            val open = fence
            if (open == null) fence = marker
            else if (marker.first() == open.first() && marker.length >= open.length && match.groupValues[2].isBlank()) fence = null
        }
        return fence
    }

    /** 旧版尾段格式，仅用于兼容性测试；新回复将证据存入 Message.searchSources，不能再追加进正文。 */
    fun withSources(content: String, sources: List<Pair<String, String>>): String {
        if (sources.isEmpty() || content == "(已停止)" || content == "(空回复)") return content
        val links = sources.filter { it.second.toHttpUrlOrNull() != null }.distinctBy { it.second }
            .filterNot { content.contains("](${it.second})") || content.contains("](${escapeUrl(it.second)})") }.take(8)
        if (links.isEmpty()) return content
        // A truncated model fence must not swallow the source links into the copyable original.
        val fence = openFence(content)
        val answer = if (fence == null) content.trimEnd() else content + "\n" + fence
        return answer + "\n\n" + links.joinToString(" · ") { (title, url) ->
            val label = title.ifBlank { url.toHttpUrlOrNull()?.host.orEmpty() }
                .replace(Regex("[\\[\\]\\r\\n*_`<>]"), " ").trim().take(80)
            "[$label](${escapeUrl(url)})"
        }
    }

    private fun escapeUrl(url: String) = url.replace("(", "%28").replace(")", "%29")
}

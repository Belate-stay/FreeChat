package com.freechat.util

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile

/**
 * Bounded local text extraction. JSON/CSV/source files are text, not invisible attachments.
 * 用于「理解文件内容」——先把文件里能读到的文字喂给大模型。
 */
object DocumentParser {
    const val MAX_TEXT_CHARS = 64_000
    private const val MAX_TEXT_BYTES = 2 * 1024 * 1024
    private const val MAX_ARCHIVE_BYTES = 16L * 1024 * 1024
    private val textExtensions = setOf("txt", "md", "markdown", "json", "jsonl", "ndjson", "csv", "tsv",
        "log", "xml", "html", "htm", "yaml", "yml", "toml", "ini", "cfg", "conf", "sql", "rtf",
        "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "css", "scss", "c", "cpp", "h", "hpp",
        "cs", "swift", "go", "rs", "rb", "php", "sh", "ps1", "bat", "tex", "srt", "vtt")
    private val officeExtensions = setOf("docx", "xlsx", "pptx")

    enum class Failure { MISSING, UNSUPPORTED, BINARY, TOO_LARGE, EMPTY, READ_FAILED }
    data class ReadResult(val text: String = "", val failure: Failure? = null, val truncated: Boolean = false)
    private class LimitExceeded : IOException()

    fun isSupported(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        return ext in textExtensions || ext in officeExtensions
    }

    fun importLimitBytes(fileName: String): Int =
        if (fileName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in textExtensions)
            MAX_TEXT_BYTES else MAX_ARCHIVE_BYTES.toInt()

    /** Call on IO. Failure is data, never inferred from punctuation in a valid document. */
    fun read(file: File, displayName: String = file.name, mime: String = ""): ReadResult {
        if (!file.isFile) return ReadResult(failure = Failure.MISSING)
        val ext = displayName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        if (ext in setOf("doc", "xls", "ppt", "pdf")) return ReadResult(failure = Failure.UNSUPPORTED)
        if (file.length() > (if (ext in officeExtensions) MAX_ARCHIVE_BYTES else MAX_TEXT_BYTES.toLong()))
            return ReadResult(failure = Failure.TOO_LARGE)
        return try {
            val text = when (ext) {
                "docx" -> extractDocx(file)
                "xlsx" -> extractXlsx(file)
                "pptx" -> extractPptx(file)
                else -> {
                    if (ext !in textExtensions && (mime.startsWith("image/") || mime.startsWith("audio/") ||
                        mime.startsWith("video/") || ext in setOf("zip", "rar", "7z", "png", "jpg", "jpeg", "webp", "gif", "mp3", "mp4")))
                        return ReadResult(failure = Failure.UNSUPPORTED)
                    val bytes = file.inputStream().use { boundedBytes(it, MAX_TEXT_BYTES) }
                    decodeText(bytes) ?: return ReadResult(failure = Failure.BINARY)
                }
            }.trim()
            if (text.isEmpty()) ReadResult(failure = Failure.EMPTY)
            else ReadResult(text.take(MAX_TEXT_CHARS), truncated = text.length > MAX_TEXT_CHARS)
        } catch (_: LimitExceeded) {
            ReadResult(failure = Failure.TOO_LARGE)
        } catch (_: Exception) {
            ReadResult(failure = Failure.READ_FAILED)
        }
    }

    fun failureText(failure: Failure): String {
        val s = com.freechat.i18n.LocaleManager.strings()
        return when (failure) {
            Failure.MISSING -> s.attachmentMissing
            Failure.UNSUPPORTED, Failure.BINARY -> s.attachmentUnsupported
            Failure.TOO_LARGE -> s.attachmentTooLarge
            Failure.EMPTY -> s.attachmentEmpty
            Failure.READ_FAILED -> s.attachmentReadFailed
        }
    }

    /** Compatibility for the document generator; callers that need status should use [read]. */
    fun extractText(file: File): String = read(file).let { result ->
        result.failure?.let { "（${failureText(it)}）" } ?: result.text
    }

    private fun boundedBytes(input: InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(limit, 8 * 1024))
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) throw LimitExceeded()
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun decodeText(bytes: ByteArray): String? {
        val (charset, offset) = when {
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE to 2
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE to 2
            bytes.size >= 3 && bytes.take(3) == listOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) -> Charsets.UTF_8 to 3
            else -> Charsets.UTF_8 to 0
        }
        fun decode(cs: Charset): String? = runCatching {
            cs.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
        }.getOrNull()
        val text = decode(charset) ?: if (offset == 0) decode(Charset.forName("GB18030")) else null
        return text?.takeUnless { it.any { ch -> ch == '\u0000' || (ch.code < 32 && ch !in "\r\n\t\u000c") } }
    }

    /** Both per-entry and cumulative decompression budgets prevent a small ZIP exhausting memory. */
    private class XmlReader(private val zip: ZipFile) {
        private var remaining = 8 * 1024 * 1024
        fun read(path: String): String? {
            val entry = zip.getEntry(path) ?: return null
            val limit = minOf(remaining, MAX_TEXT_BYTES)
            if (limit <= 0 || entry.size > limit) throw LimitExceeded()
            val bytes = zip.getInputStream(entry).use { boundedBytes(it, limit) }
            remaining -= bytes.size
            return String(bytes, Charsets.UTF_8)
        }
    }

    // ─── docx ───
    private fun extractDocx(file: File): String {
        ZipFile(file).use { zip ->
            val xml = XmlReader(zip).read("word/document.xml") ?: return ""
            val parts = Regex("<w:p[ >][\\s\\S]*?</w:p>").findAll(xml).map { p ->
                Regex("<w:t[^>]*>([\\s\\S]*?)</w:t>")
                    .findAll(p.value)
                    .map { unescape(it.groupValues[1]) }
                    .joinToString("")
            }.filter { it.isNotBlank() }.toList()
            return parts.joinToString("\n")
        }
    }

    // ─── xlsx ───
    private fun extractXlsx(file: File): String {
        ZipFile(file).use { zip ->
            val reader = XmlReader(zip)
            // 共享字符串表（Excel 通常把文本放这里）
            val shared = mutableListOf<String>()
            reader.read("xl/sharedStrings.xml")?.let { xml ->
                Regex("<si>([\\s\\S]*?)</si>").findAll(xml).forEach { si ->
                    val text = Regex("<t[^>]*>([\\s\\S]*?)</t>")
                        .findAll(si.value)
                        .map { unescape(it.groupValues[1]) }
                        .joinToString("")
                    shared.add(text)
                }
            }
            // 逐个 sheet 拼接：共享字符串引用 + 内联字符串
            val lines = mutableListOf<String>()
            val sheets = zip.entries().toList()
                .filter { it.name.startsWith("xl/worksheets/") && it.name.endsWith(".xml") }
                .sortedBy { it.name }
            for (s in sheets) {
                val xml = reader.read(s.name) ?: continue
                // 按行切
                Regex("<row[ >][\\s\\S]*?</row>").findAll(xml).forEach { row ->
                    val cells = Regex("<c [^>]*r=\"([A-Z]+[0-9]+)\"[^>]*>([\\s\\S]*?)</c>|<c r=\"([A-Z]+[0-9]+)\"[^>]*>([\\s\\S]*?)</c>|<c[ >]([\\s\\S]*?)</c>")
                        .findAll(row.value).map { c ->
                            val cell = c.value
                            val t = Regex(" t=\"([^\"]+)\"").find(cell)?.groupValues?.get(1)
                            when (t) {
                                "s" -> { // 共享字符串索引
                                    val idx = Regex("<v>([\\s\\S]*?)</v>").find(cell)?.groupValues?.get(1)?.trim()?.toIntOrNull()
                                    if (idx != null && idx < shared.size) shared[idx] else ""
                                }
                                "inlineStr" -> Regex("<t[^>]*>([\\s\\S]*?)</t>").find(cell)?.groupValues?.get(1)?.let { unescape(it) } ?: ""
                                else -> Regex("<v>([\\s\\S]*?)</v>").find(cell)?.groupValues?.get(1)?.trim() ?: ""
                            }
                        }.filter { it.isNotBlank() }.toList()
                    if (cells.isNotEmpty()) lines.add(cells.joinToString("\t"))
                }
            }
            return lines.joinToString("\n").ifBlank { shared.joinToString("\n") }
        }
    }

    // ─── pptx ───
    private fun extractPptx(file: File): String {
        ZipFile(file).use { zip ->
            val reader = XmlReader(zip)
            val slides = zip.entries().toList()
                .filter { it.name.startsWith("ppt/slides/slide") && it.name.endsWith(".xml") }
                .sortedBy { extractSlideNum(it.name) }
            val out = mutableListOf<String>()
            slides.forEachIndexed { i, s ->
                val xml = reader.read(s.name).orEmpty()
                val texts = Regex("<a:t[^>]*>([\\s\\S]*?)</a:t>")
                    .findAll(xml)
                    .map { unescape(it.groupValues[1]) }
                    .filter { it.isNotBlank() }
                    .toList()
                if (texts.isNotEmpty()) {
                    out.add(com.freechat.i18n.LocaleManager.strings().pageMarker(i + 1) + texts.joinToString("\n"))
                }
            }
            return out.joinToString("\n\n")
        }
    }

    private fun extractSlideNum(name: String): Int {
        return Regex("slide(\\d+)\\.xml").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun unescape(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

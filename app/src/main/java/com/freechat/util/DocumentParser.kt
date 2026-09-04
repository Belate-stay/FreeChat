package com.freechat.util

import java.io.File
import java.util.zip.ZipFile

/**
 * 文档文本提取：txt/md 直读，docx/xlsx/pptx 解 zip 抠出 XML 里的文字。
 * 用于「理解文件内容」——先把文件里能读到的文字喂给大模型。
 */
object DocumentParser {

    fun isSupported(fileName: String): Boolean {
        val n = fileName.lowercase()
        return n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".markdown") ||
            n.endsWith(".docx") || n.endsWith(".xlsx") || n.endsWith(".pptx") ||
            n.endsWith(".doc") || n.endsWith(".xls") || n.endsWith(".ppt")
    }

    fun extractText(file: File): String {
        val n = file.name.lowercase()
        return try {
            when {
                n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".markdown") ->
                    file.readText().trim()
                n.endsWith(".docx") -> extractDocx(file)
                n.endsWith(".xlsx") -> extractXlsx(file)
                n.endsWith(".pptx") -> extractPptx(file)
                // 老二进制 .doc/.xls/.ppt 无法直接解包，返回提示
                n.endsWith(".doc") || n.endsWith(".xls") || n.endsWith(".ppt") ->
                    "（旧版二进制格式，暂不支持直接解析，请另存为 docx/xlsx/pptx 后重试）"
                else -> ""
            }
        } catch (e: Exception) {
            "（读取文件失败：${e.message}）"
        }
    }

    /** 读取 zip 内某个条目的文本内容 */
    private fun readEntry(zip: ZipFile, path: String): String? {
        val entry = zip.getEntry(path) ?: return null
        return zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
    }

    // ─── docx ───
    private fun extractDocx(file: File): String {
        ZipFile(file).use { zip ->
            val xml = readEntry(zip, "word/document.xml") ?: return ""
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
            // 共享字符串表（Excel 通常把文本放这里）
            val shared = mutableListOf<String>()
            readEntry(zip, "xl/sharedStrings.xml")?.let { xml ->
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
                val xml = zip.getInputStream(s).bufferedReader(Charsets.UTF_8).readText()
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
            val slides = zip.entries().toList()
                .filter { it.name.startsWith("ppt/slides/slide") && it.name.endsWith(".xml") }
                .sortedBy { extractSlideNum(it.name) }
            val out = mutableListOf<String>()
            slides.forEachIndexed { i, s ->
                val xml = zip.getInputStream(s).bufferedReader(Charsets.UTF_8).readText()
                val texts = Regex("<a:t[^>]*>([\\s\\S]*?)</a:t>")
                    .findAll(xml)
                    .map { unescape(it.groupValues[1]) }
                    .filter { it.isNotBlank() }
                    .toList()
                if (texts.isNotEmpty()) {
                    out.add("【第 ${i + 1} 页】" + texts.joinToString("\n"))
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

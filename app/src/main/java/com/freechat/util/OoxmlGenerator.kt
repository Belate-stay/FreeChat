package com.freechat.util

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 原生 OOXML 生成器：产出可编辑的 docx / xlsx / pptx（真文字/真表格/真形状，不是图片拼合）。
 * 内置一套现代排版模板（配色 + 字体 + 标题条 + 表格样式），保证输出不丑、不古板。
 */

// ─── 内容模型 ───
data class DocSection(val heading: String? = null, val paragraphs: List<String> = emptyList())
data class DocContent(val title: String, val subtitle: String? = null, val sections: List<DocSection>)

data class SheetContent(val title: String? = null, val headers: List<String>, val rows: List<List<String>>)

data class SlideContent(val title: String, val bullets: List<String>)
data class PresentationContent(val title: String, val subtitle: String? = null, val slides: List<SlideContent>)

// ─── 现代排版主题 ───
object DocTheme {
    const val DARK = "1F2937"        // 深墨色（标题/正文）
    const val ACCENT = "4F46E5"      // 靛蓝主色
    const val ACCENT_SOFT = "EEF2FF" // 主色浅底
    const val MUTED = "64748B"       // 次级灰
    const val LIGHT = "F8FAFC"       // 浅底
    const val BORDER = "E5E7EB"      // 分隔线
    const val ALT_ROW = "F1F5F9"     // 表格隔行浅色
    const val WHITE = "FFFFFF"
    const val FONT_EN = "Segoe UI"
    const val FONT_CN = "Microsoft YaHei"
}

object OoxmlGenerator {

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun buildZip(dir: File, fileName: String, entries: List<Pair<String, String>>): File {
        val out = File(dir, fileName)
        ZipOutputStream(FileOutputStream(out)).use { zos ->
            for ((path, content) in entries) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return out
    }

    private fun coreProps(title: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>${esc(title)}</dc:title>
  <dc:creator>FreeChat</dc:creator>
  <cp:lastModifiedBy>FreeChat</cp:lastModifiedBy>
</cp:coreProperties>"""

    private fun appProps() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">
  <Application>FreeChat</Application>
</Properties>"""

    // ═══════════════════════ DOCX ═══════════════════════
    fun generateDocx(dir: File, fileName: String, doc: DocContent): File {
        val entries = listOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>""",
            "word/document.xml" to documentXml(doc),
            "docProps/core.xml" to coreProps(doc.title),
            "docProps/app.xml" to appProps()
        )
        return buildZip(dir, fileName, entries)
    }

    private fun documentXml(doc: DocContent): String {
        val body = StringBuilder()

        // 标题
        body.append(p(doc.title, size = 48, bold = true, color = DocTheme.DARK, align = "center"))
        if (!doc.subtitle.isNullOrBlank()) {
            body.append(p(doc.subtitle, size = 24, color = DocTheme.MUTED, align = "center"))
        }
        // 标题下的装饰分隔线（底部边框）
        body.append("""<w:p><w:pPr><w:jc w:val="center"/><w:pBdr><w:bottom w:val="single" w:sz="12" w:space="1" w:color="${DocTheme.ACCENT}"/></w:pBdr><w:spacing w:after="240"/></w:pPr></w:p>""")
        body.append(spacer())

        for (section in doc.sections) {
            if (!section.heading.isNullOrBlank()) {
                body.append(p(section.heading, size = 28, bold = true, color = DocTheme.ACCENT, spaceBefore = 240, spaceAfter = 80))
            }
            for (para in section.paragraphs) {
                body.append(p(para, size = 22, color = DocTheme.DARK, spaceAfter = 80))
            }
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
$body
    <w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
  </w:body>
</w:document>"""
    }

    private fun p(text: String, size: Int, bold: Boolean = false, color: String = DocTheme.DARK,
                  align: String? = null, spaceBefore: Int = 0, spaceAfter: Int = 0): String {
        val rPr = StringBuilder()
        if (bold) rPr.append("<w:b/>")
        rPr.append("<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/>")
        rPr.append("<w:color w:val=\"$color\"/>")
        rPr.append("<w:rFonts w:ascii=\"${DocTheme.FONT_EN}\" w:hAnsi=\"${DocTheme.FONT_EN}\" w:eastAsia=\"${DocTheme.FONT_CN}\"/>")
        val pPr = StringBuilder()
        if (align != null) pPr.append("<w:jc w:val=\"$align\"/>")
        if (spaceBefore > 0 || spaceAfter > 0) pPr.append("<w:spacing w:before=\"$spaceBefore\" w:after=\"$spaceAfter\" w:line=\"360\" w:lineRule=\"auto\"/>")
        else pPr.append("<w:spacing w:line=\"360\" w:lineRule=\"auto\"/>")
        return "<w:p><w:pPr>$pPr</w:pPr><w:r><w:rPr>$rPr</w:rPr><w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p>"
    }

    private fun spacer() = "<w:p><w:r><w:t></w:t></w:r></w:p>"

    // ═══════════════════════ XLSX ═══════════════════════
    fun generateXlsx(dir: File, fileName: String, sheet: SheetContent): File {
        val entries = listOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="${esc(sheet.title?.take(28) ?: "Sheet1")}" sheetId="1" r:id="rId1"/></sheets>
</workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""",
            "xl/worksheets/sheet1.xml" to sheetXml(sheet),
            "xl/styles.xml" to stylesXml(),
            "docProps/core.xml" to coreProps(sheet.title ?: "Sheet1"),
            "docProps/app.xml" to appProps()
        )
        return buildZip(dir, fileName, entries)
    }

    private fun sheetXml(sheet: SheetContent): String {
        val rows = StringBuilder()
        var r = 1
        // 表头行
        rows.append(row(r++, sheet.headers, style = 1, header = true))
        // 数据行（隔行浅色）
        sheet.rows.forEachIndexed { i, cells ->
            rows.append(row(r++, cells, style = if (i % 2 == 0) 3 else 4, header = false))
        }
        val colCount = (sheet.headers.size.coerceAtLeast(sheet.rows.maxOfOrNull { it.size } ?: 0)).coerceAtLeast(1)
        val cols = (0 until colCount).joinToString("") { i ->
            val letter = ('A'.code + i).toChar()
            """<col min="${i + 1}" max="${i + 1}" width="18" customWidth="1"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>$cols</cols>
  <sheetData>$rows</sheetData>
</worksheet>"""
    }

    private fun row(r: Int, cells: List<String>, style: Int, header: Boolean): String {
        val cellXml = StringBuilder()
        for ((i, v) in cells.withIndex()) {
            val col = ('A'.code + i).toChar()
            val ref = "$col$r"
            cellXml.append("""<c r="$ref" t="inlineStr" s="$style"><is><t xml:space="preserve">${esc(v)}</t></is></c>""")
        }
        return """<row r="$r">$cellXml</row>"""
    }

    private fun stylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="4">
    <font><sz val="11"/><name val="Segoe UI"/></font>
    <font><b/><sz val="11"/><color rgb="FF${DocTheme.WHITE}"/><name val="Segoe UI"/></font>
    <font><b/><sz val="14"/><color rgb="FF${DocTheme.DARK}"/><name val="Segoe UI"/></font>
    <font><sz val="11"/><name val="Segoe UI"/></font>
  </fonts>
  <fills count="5">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF${DocTheme.ACCENT}"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF${DocTheme.LIGHT}"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF${DocTheme.ALT_ROW}"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border><left style="thin"><color rgb="FF${DocTheme.BORDER}"/></left><right style="thin"><color rgb="FF${DocTheme.BORDER}"/></right><top style="thin"><color rgb="FF${DocTheme.BORDER}"/></top><bottom style="thin"><color rgb="FF${DocTheme.BORDER}"/></bottom><diagonal/></border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="5">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
    <xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>
    <xf numFmtId="0" fontId="3" fillId="4" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
    }

    // ═══════════════════════ PPTX ═══════════════════════
    fun generatePptx(dir: File, fileName: String, pres: PresentationContent): File {
        val slides = pres.slides
        val slideRels = StringBuilder()
        val contentTypes = StringBuilder()
        val presRels = StringBuilder()
        val sldIdLst = StringBuilder()

        for (i in slides.indices) {
            val relId = "rId${i + 2}"  // rId1 = slideMaster
            sldIdLst.append("""<p:sldId id="${256 + i}" r:id="$relId"/>""")
            presRels.append("""<Relationship Id="$relId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${i + 1}.xml"/>""")
            contentTypes.append("""<Override PartName="/ppt/slides/slide${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
            slideRels.append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>""")
        }

        val entries = mutableListOf<Pair<String, String>>()
        entries += "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
  <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
  <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
$contentTypes</Types>"""

        entries += "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""

        entries += "ppt/presentation.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>
  <p:sldIdLst>$sldIdLst</p:sldIdLst>
  <p:sldSz cx="12192000" cy="6858000"/>
  <p:notesSz cx="6858000" cy="9144000"/>
</p:presentation>"""

        entries += "ppt/_rels/presentation.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
$presRels
  <Relationship Id="rId99" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>
</Relationships>"""

        entries += "ppt/slideMasters/slideMaster1.xml" to slideMasterXml()
        entries += "ppt/slideMasters/_rels/slideMaster1.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
</Relationships>"""

        entries += "ppt/slideLayouts/slideLayout1.xml" to slideLayoutXml()
        entries += "ppt/slideLayouts/_rels/slideLayout1.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>"""

        entries += "ppt/theme/theme1.xml" to themeXml()

        // 每页：封面 + 内容页
        for (i in slides.indices) {
            val isTitle = (i == 0)
            entries += "ppt/slides/slide${i + 1}.xml" to (if (isTitle) titleSlideXml(pres, slides[i]) else contentSlideXml(slides[i]))
            entries += "ppt/slides/_rels/slide${i + 1}.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
</Relationships>"""
        }

        entries += "docProps/core.xml" to coreProps(pres.title)
        entries += "docProps/app.xml" to appProps()

        return buildZip(dir, fileName, entries)
    }

    // 封面页：深色标题 + 主色装饰条
    private fun titleSlideXml(pres: PresentationContent, slide: SlideContent): String {
        val title = pres.title.ifBlank { slide.title }
        val subtitle = pres.subtitle
        val shapes = StringBuilder()
        shapes.append(shape(2, "Title", 685800, 2286000, 10795000, 1600200,
            listOf(paragraph(title, 4400, bold = true, color = DocTheme.DARK))))
        // 主色装饰条
        shapes.append(rect(3, 685800, 3987800, 2743200, 182880, DocTheme.ACCENT))
        if (!subtitle.isNullOrBlank()) {
            shapes.append(shape(4, "Subtitle", 685800, 4114800, 10795000, 914400,
                listOf(paragraph(subtitle, 2200, bold = false, color = DocTheme.MUTED))))
        }
        return slideShell(shapes.toString())
    }

    // 内容页：主色标题 + 细装饰条 + 项目符号正文
    private fun contentSlideXml(slide: SlideContent): String {
        val shapes = StringBuilder()
        shapes.append(shape(2, "Title", 685800, 342900, 10795000, 1028700,
            listOf(paragraph(slide.title, 3200, bold = true, color = DocTheme.ACCENT))))
        shapes.append(rect(3, 685800, 1485900, 2743200, 91440, DocTheme.ACCENT))
        // 项目符号正文
        val bullets = slide.bullets.map { bulletParagraph(it) }
        shapes.append(shape(4, "Body", 685800, 1600200, 10795000, 4914900, bullets))
        return slideShell(shapes.toString())
    }

    private fun slideShell(shapes: String): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
$shapes
    </p:spTree>
  </p:cSld>
  <p:clrMapOvr><a:overrideClrMapping bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/></p:clrMapOvr>
</p:sld>"""

    // 文本框形状
    private fun shape(id: Int, name: String, x: Long, y: Long, cx: Long, cy: Long, paras: List<String>): String {
        val body = paras.joinToString("")
        return """<p:sp>
  <p:nvSpPr><p:cNvPr id="$id" name="$name"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr/></p:nvSpPr>
  <p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>
  <p:txBody><a:bodyPr wrap="square" rtlCol="0"/><a:lstStyle/>$body</p:txBody>
</p:sp>"""
    }

    // 矩形装饰条
    private fun rect(id: Int, x: Long, y: Long, cx: Long, cy: Long, color: String): String {
        return """<p:sp>
  <p:nvSpPr><p:cNvPr id="$id" name="Bar"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr/></p:nvSpPr>
  <p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="$color"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr>
  <p:txBody><a:bodyPr rtlCol="0"/><a:lstStyle/><a:p/></p:txBody>
</p:sp>"""
    }

    private fun paragraph(text: String, sz: Int, bold: Boolean, color: String): String {
        val b = if (bold) """ b="1"""" else ""
        return """<a:p><a:pPr algn="l"/><a:r><a:rPr lang="zh-CN" sz="$sz"$b><a:solidFill><a:srgbClr val="$color"/></a:solidFill><a:latin typeface="${DocTheme.FONT_EN}"/><a:ea typeface="${DocTheme.FONT_CN}"/></a:rPr><a:t>${esc(text)}</a:t></a:r></a:p>"""
    }

    private fun bulletParagraph(text: String): String {
        return """<a:p><a:pPr marL="274320" indent="-274320"><a:buFont typeface="Arial"/><a:buChar char="•"/></a:pPr><a:r><a:rPr lang="zh-CN" sz="2000"><a:solidFill><a:srgbClr val="${DocTheme.DARK}"/></a:solidFill><a:latin typeface="${DocTheme.FONT_EN}"/><a:ea typeface="${DocTheme.FONT_CN}"/></a:rPr><a:t>${esc(text)}</a:t></a:r></a:p>"""
    }

    private fun slideMasterXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:bg><p:bgRef idx="1001"><a:schemeClr val="bg1"/></p:bgRef></p:bg>
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
    </p:spTree>
  </p:cSld>
  <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
  <p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>
</p:sldMaster>"""

    private fun slideLayoutXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank">
  <p:cSld name="Blank">
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
    </p:spTree>
  </p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sldLayout>"""

    private fun themeXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="FreeChat">
  <a:themeElements>
    <a:clrScheme name="FreeChat">
      <a:dk1><a:srgbClr val="${DocTheme.DARK}"/></a:dk1>
      <a:lt1><a:srgbClr val="${DocTheme.WHITE}"/></a:lt1>
      <a:dk2><a:srgbClr val="${DocTheme.ACCENT}"/></a:dk2>
      <a:lt2><a:srgbClr val="0EA5E9"/></a:lt2>
      <a:accent1><a:srgbClr val="${DocTheme.ACCENT}"/></a:accent1>
      <a:accent2><a:srgbClr val="0EA5E9"/></a:accent2>
      <a:accent3><a:srgbClr val="10B981"/></a:accent3>
      <a:accent4><a:srgbClr val="F59E0B"/></a:accent4>
      <a:accent5><a:srgbClr val="EF4444"/></a:accent5>
      <a:accent6><a:srgbClr val="8B5CF6"/></a:accent6>
      <a:hlink><a:srgbClr val="${DocTheme.ACCENT}"/></a:hlink>
      <a:folHlink><a:srgbClr val="6366F1"/></a:folHlink>
    </a:clrScheme>
    <a:fontScheme name="FreeChat">
      <a:majorFont><a:latin typeface="${DocTheme.FONT_EN}"/><a:ea typeface="${DocTheme.FONT_CN}"/></a:majorFont>
      <a:minorFont><a:latin typeface="${DocTheme.FONT_EN}"/><a:ea typeface="${DocTheme.FONT_CN}"/></a:minorFont>
    </a:fontScheme>
    <a:fmtScheme name="FreeChat">
      <a:fillStyleLst>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
      </a:fillStyleLst>
      <a:lnStyleLst>
        <a:ln w="6350" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln>
        <a:ln w="12700" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln>
        <a:ln w="19050" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln>
      </a:lnStyleLst>
      <a:effectStyleLst>
        <a:effectStyle><a:effectLst/></a:effectStyle>
        <a:effectStyle><a:effectLst/></a:effectStyle>
        <a:effectStyle><a:effectLst/></a:effectStyle>
      </a:effectStyleLst>
      <a:bgFillStyleLst>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
      </a:bgFillStyleLst>
    </a:fmtScheme>
  </a:themeElements>
</a:theme>"""
}

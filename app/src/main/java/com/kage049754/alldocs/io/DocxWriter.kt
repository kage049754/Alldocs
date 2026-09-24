package com.kage049754.alldocs.io

import android.content.Context
import android.net.Uri
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxWriter {
    fun write(context: Context, uri: Uri, body: String) {
        val out = context.contentResolver.openOutputStream(uri) ?: return
        ZipOutputStream(out).use { zip ->
            entry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>")
            entry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>")
            entry(zip, "word/document.xml", documentXml(body))
        }
    }

    private fun documentXml(html: String): String {
        val source = if (html.trimStart().startsWith("<")) html else "<p>" + escape(html).replace("\n", "<br>") + "</p>"
        val parts = mutableListOf<String>()
        val token = Regex("(?is)<(table)\\b[^>]*>.*?</table>|<(p|h1|h2|h3|li|div|blockquote)\\b[^>]*>.*?</\\2>")
        token.findAll(source).forEach { m ->
            val block = m.value
            if (block.startsWith("<table", true)) parts.add(tableXml(block))
            else {
                val tag = Regex("(?is)^<(p|h1|h2|h3|li|div|blockquote)").find(block)?.groupValues?.get(1)?.lowercase() ?: "p"
                val inner = block.replaceFirst(Regex("(?is)^<[^>]+>"), "").replaceFirst(Regex("(?is)</[^>]+>\\s*$"), "")
                val pPr = when (tag) {
                    "h1" -> "<w:pPr><w:pStyle w:val=\"Title\"/></w:pPr>"
                    "h2" -> "<w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>"
                    "h3" -> "<w:pPr><w:pStyle w:val=\"Heading2\"/></w:pPr>"
                    "blockquote" -> "<w:pPr><w:ind w:left=\"720\"/></w:pPr>"
                    "li" -> "<w:pPr><w:ind w:left=\"360\"/><w:tabs><w:tab w:val=\"num\" w:pos=\"360\"/></w:tabs></w:pPr>"
                    else -> ""
                }
                val prefix = if (tag == "li") "• " else ""
                parts.add("<w:p>" + pPr + runs(prefix + inner) + "</w:p>")
            }
        }
        if (parts.isEmpty()) parts.add("<w:p>" + runs(source) + "</w:p>")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" +
            parts.joinToString("") +
            "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr></w:body></w:document>"
    }

    private fun tableXml(table: String): String {
        val rows = Regex("(?is)<tr\\b[^>]*>(.*?)</tr>").findAll(table).map { row ->
            val cells = Regex("(?is)<(td|th)\\b[^>]*>(.*?)</\\1>").findAll(row.groupValues[1]).map { c ->
                "<w:tc><w:tcPr/>" + "<w:p>" + runs(c.groupValues[2]) + "</w:p></w:tc>"
            }.joinToString("")
            "<w:tr>$cells</w:tr>"
        }.joinToString("")
        return "<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders><w:top w:val=\"single\" w:sz=\"4\"/><w:left w:val=\"single\" w:sz=\"4\"/><w:bottom w:val=\"single\" w:sz=\"4\"/><w:right w:val=\"single\" w:sz=\"4\"/><w:insideH w:val=\"single\" w:sz=\"4\"/><w:insideV w:val=\"single\" w:sz=\"4\"/></w:tblBorders></w:tblPr>$rows</w:tbl>"
    }

    private fun runs(html: String): String {
        val normalized = html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)<strong>(.*?)</strong>"), "<b>$1</b>")
        val out = StringBuilder()
        var pos = 0
        val tag = Regex("(?is)<(b|i|u|s|strike|strong|em|span)(?:\\s+style=\"([^\"]*)\")?>(.*?)</\\1>")
        tag.findAll(normalized).forEach { m ->
            if (m.range.first > pos) out.append(runXml(stripTags(normalized.substring(pos, m.range.first))))
            val name = m.groupValues[1].lowercase()
            val style = m.groupValues[2]
            val text = stripTags(m.groupValues[3])
            val rPr = StringBuilder()
            if (name == "b" || name == "strong") rPr.append("<w:b/>")
            if (name == "i" || name == "em") rPr.append("<w:i/>")
            if (name == "u") rPr.append("<w:u w:val=\"single\"/>")
            if (name == "s" || name == "strike") rPr.append("<w:strike/>")
            Regex("font-size\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE).find(style)?.groupValues?.get(1)?.toIntOrNull()?.let { rPr.append("<w:sz w:val=\"" + (it * 2) + "\"/>") }
            Regex("color\\s*:\\s*#?([0-9a-fA-F]{6})", RegexOption.IGNORE_CASE).find(style)?.groupValues?.get(1)?.let { rPr.append("<w:color w:val=\"$it\"/>") }
            out.append("<w:r><w:rPr>$rPr</w:rPr><w:t xml:space=\"preserve\">").append(escape(decode(text))).append("</w:t></w:r>")
            pos = m.range.last + 1
        }
        if (pos < normalized.length) out.append(runXml(stripTags(normalized.substring(pos))))
        return out.toString()
    }

    private fun runXml(text: String): String =
        "<w:r><w:t xml:space=\"preserve\">" + escape(decode(text)) + "</w:t></w:r>"

    private fun stripTags(s: String): String = s.replace(Regex("(?is)<[^>]+>"), "")
    private fun decode(s: String): String = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")

    private fun entry(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
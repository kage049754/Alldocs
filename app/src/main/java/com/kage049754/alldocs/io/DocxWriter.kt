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
        val blocks = Regex("(?is)<(p|h1|h2|h3|li|div|blockquote)\\b[^>]*>(.*?)</\\1>").findAll(source).map { match ->
            val tag = match.groupValues[1].lowercase()
            val content = match.groupValues[2]
            val style = when (tag) {
                "h1" -> "<w:pPr><w:pStyle w:val=\"Title\"/></w:pPr>"
                "h2" -> "<w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>"
                "h3" -> "<w:pPr><w:pStyle w:val=\"Heading2\"/></w:pPr>"
                "blockquote" -> "<w:pPr><w:ind w:left=\"720\"/></w:pPr>"
                "li" -> "<w:pPr><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr></w:pPr>"
                else -> ""
            }
            "<w:p>" + style + runs(content) + "</w:p>"
        }.toList()
        val tables = Regex("(?is)<table\\b[^>]*>(.*?)</table>").findAll(source).map { table ->
            val rows = Regex("(?is)<tr\\b[^>]*>(.*?)</tr>").findAll(table.groupValues[1]).map { row ->
                val cells = Regex("(?is)<(td|th)\\b[^>]*>(.*?)</\\1>").findAll(row.groupValues[1]).map { c ->
                    "<w:tc><w:tcPr/>" + runs(c.groupValues[2]) + "</w:tc>"
                }.joinToString("")
                "<w:tr>" + cells + "</w:tr>"
            }.joinToString("")
            "<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/></w:tblPr>" + rows + "</w:tbl>"
        }.toList()
        val content = if (blocks.isEmpty() && tables.isEmpty()) "<w:p>" + runs(source) + "</w:p>" else blocks.joinToString("") + tables.joinToString("")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + content + "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr></w:body></w:document>"
    }

    private fun runs(html: String): String {
        val text = html.replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
        val decoded = text.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        return decoded.split("\n").joinToString("") { t -> "<w:r><w:t xml:space=\"preserve\">" + escape(t) + "</w:t></w:r>" }
    }

    private fun entry(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

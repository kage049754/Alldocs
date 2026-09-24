package com.kage049754.alldocs.io

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipInputStream

object DocxReader {
    fun read(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri) ?: return ""
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") return parseDocument(zip)
            }
        }
        return ""
    }

    private fun parseDocument(input: java.io.InputStream): String {
        val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
        val out = StringBuilder()
        var paragraph = StringBuilder()
        var cell = StringBuilder()
        var rowCells = mutableListOf<String>()
        var inParagraph = false
        var inCell = false
        var inTable = false
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var fontSize = 16
        var color = ""
        var align = ""
        var style = ""
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "p" -> { inParagraph = true; paragraph = StringBuilder(); align = ""; style = "" }
                    "pStyle" -> style = parser.getAttributeValue(null, "val") ?: ""
                    "jc" -> align = parser.getAttributeValue(null, "val") ?: ""
                    "tbl" -> { inTable = true; out.append("<table><tbody>") }
                    "tr" -> rowCells = mutableListOf()
                    "tc" -> { inCell = true; cell = StringBuilder() }
                    "b" -> bold = true
                    "i" -> italic = true
                    "u" -> underline = true
                    "strike" -> strike = true
                    "sz" -> fontSize = ((parser.getAttributeValue(null, "val")?.toIntOrNull() ?: 32) / 2).coerceIn(6, 96)
                    "color" -> color = parser.getAttributeValue(null, "val")?.takeIf { !it.equals("auto", true) } ?: ""
                    "br" -> if (inCell) cell.append("<br>") else paragraph.append("<br>")
                    "t" -> {
                        val text = parser.nextText()
                        val wrapped = wrap(text, bold, italic, underline, strike, fontSize, color)
                        if (inCell) cell.append(wrapped) else paragraph.append(wrapped)
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                when (parser.name) {
                    "b" -> bold = false
                    "i" -> italic = false
                    "u" -> underline = false
                    "strike" -> strike = false
                    "sz" -> fontSize = 16
                    "color" -> color = ""
                    "tc" -> { rowCells.add(cell.toString()); inCell = false }
                    "tr" -> out.append("<tr>").append(rowCells.joinToString("") { "<td>$it</td>" }).append("</tr>")
                    "tbl" -> { out.append("</tbody></table><p><br></p>"); inTable = false }
                    "p" -> {
                        if (inParagraph && !inTable) {
                            out.append("<p").append(buildParagraphStyle(style, align)).append(">").append(paragraph).append("</p>")
                        }
                        inParagraph = false
                    }
                }
            }
            event = parser.next()
        }
        return out.toString()
    }

    private fun buildParagraphStyle(style: String, align: String): String {
        val css = StringBuilder()
        when {
            style.contains("Title", true) -> css.append("font-size:28px;font-weight:700;")
            style.contains("Heading1", true) -> css.append("font-size:24px;font-weight:700;")
            style.contains("Heading2", true) -> css.append("font-size:20px;font-weight:700;")
        }
        when (align.lowercase()) {
            "center" -> css.append("text-align:center;")
            "right" -> css.append("text-align:right;")
            "both" -> css.append("text-align:justify;")
        }
        return if (css.isEmpty()) "" else " style=\"" + css.toString() + "\""
    }

    private fun wrap(text: String, bold: Boolean, italic: Boolean, underline: Boolean, strike: Boolean, size: Int, color: String): String {
        var s = escape(text)
        if (underline) s = "<u>$s</u>"
        if (strike) s = "<s>$s</s>"
        if (italic) s = "<i>$s</i>"
        if (bold) s = "<strong>$s</strong>"
        if (color.isNotEmpty()) s = "<span style=\"color:#$color\">$s</span>"
        if (size != 16) s = "<span style=\"font-size:" + size + "px\">$s</span>"
        return s
    }

    private fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
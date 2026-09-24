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
        var bold = false
        var italic = false
        var underline = false
        var inParagraph = false
        var inCell = false
        var cellText = StringBuilder()
        var rowCells = mutableListOf<String>()
        var inTable = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "p" -> inParagraph = true
                    "tbl" -> { inTable = true; out.append("<table><tbody>") }
                    "tr" -> { rowCells = mutableListOf() }
                    "tc" -> { inCell = true; cellText = StringBuilder() }
                    "b" -> bold = true
                    "i" -> italic = true
                    "u" -> underline = true
                    "br" -> if (inCell) cellText.append("<br>") else out.append("<br>")
                    "t" -> {
                        val text = parser.nextText()
                        if (inCell) cellText.append(escape(text))
                        else {
                            val wrapped = wrap(text, bold, italic, underline)
                            out.append(wrapped)
                        }
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                when (parser.name) {
                    "b" -> bold = false
                    "i" -> italic = false
                    "u" -> underline = false
                    "tc" -> { rowCells.add(cellText.toString()); inCell = false }
                    "tr" -> out.append("<tr>").append(rowCells.joinToString("") { "<td>$it</td>" }).append("</tr>")
                    "tbl" -> { out.append("</tbody></table><p><br></p>"); inTable = false }
                    "p" -> { if (!inTable) out.append("</p><p>"); inParagraph = false }
                }
            }
            event = parser.next()
        }
        return out.toString().removeSuffix("<p>").removePrefix("<p>")
    }

    private fun wrap(text: String, bold: Boolean, italic: Boolean, underline: Boolean): String {
        var s = escape(text)
        if (underline) s = "<u>$s</u>"
        if (italic) s = "<i>$s</i>"
        if (bold) s = "<strong>$s</strong>"
        return s
    }

    private fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
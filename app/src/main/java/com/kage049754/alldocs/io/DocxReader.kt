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
                if (entry.name == "word/document.xml") {
                    val parser = Xml.newPullParser()
                    parser.setInput(zip, "UTF-8")
                    val out = StringBuilder()
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG && parser.name == "t") out.append(parser.nextText())
                        else if (event == XmlPullParser.END_TAG && parser.name == "p") out.append("\n")
                        event = parser.next()
                    }
                    return out.toString().trim()
                }
            }
        }
        return ""
    }
}

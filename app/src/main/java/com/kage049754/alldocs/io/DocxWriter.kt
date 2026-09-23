package com.kage049754.alldocs.io

import android.content.Context
import android.net.Uri
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxWriter {
    fun write(context: Context, uri: Uri, body: String) {
        val out = context.contentResolver.openOutputStream(uri) ?: return
        ZipOutputStream(out).use { zip ->
            entry(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            entry(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            entry(zip, "word/_rels/document.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>""")
            val paragraphs = body.split("\n").joinToString("") { paragraph ->
                "<w:p><w:r><w:t xml:space="preserve">"+escape(paragraph)+"</w:t></w:r></w:p>"
            }
            entry(zip, "word/document.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr></w:body></w:document>""")
        }
    }
    private fun entry(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }
    private fun escape(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace(""","&quot;")
}

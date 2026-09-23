package com.kage049754.alldocs.io

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object PdfReader {
    fun read(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri) ?: return ""
        return input.use { stream ->
            runCatching {
                PDDocument.load(stream).use { document ->
                    PDFTextStripper().getText(document).trim()
                }
            }.getOrElse { "Unable to extract editable text from this PDF." }
        }
    }
}

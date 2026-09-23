package com.kage049754.alldocs.io

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri

object PdfWriter {
    fun write(context: Context, uri: Uri, title: String, body: String) {
        val document = PdfDocument()
        val pageWidth = 612
        val pageHeight = 792
        val margin = 48f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
        val lines = body.lines().flatMap { line -> line.chunked(85).ifEmpty { listOf("") } }
        var pageNumber = 1
        var y = 72f
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        val canvas = page.canvas
        canvas.drawText(title, margin, y, Paint(paint).apply { textSize = 18f; isFakeBoldText = true })
        y += 30f
        for (line in lines) {
            if (y > pageHeight - 50f) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                y = 60f
            }
            page.canvas.drawText(line, margin, y, paint)
            y += 18f
        }
        document.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
        document.close()
    }
}

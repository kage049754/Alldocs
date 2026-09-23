package com.kage049754.alldocs.io

import android.content.Context
import android.net.Uri

enum class OfficeType { DOCX, TXT, PDF, IMAGE, UNKNOWN }

object OfficeFile {
    fun typeOf(name: String): OfficeType = when (name.substringAfterLast('.', "").lowercase()) {
        "docx" -> OfficeType.DOCX
        "txt", "md", "csv" -> OfficeType.TXT
        "pdf" -> OfficeType.PDF
        "jpg", "jpeg", "png", "webp" -> OfficeType.IMAGE
        else -> OfficeType.UNKNOWN
    }
    fun displayName(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
    fun readText(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
}

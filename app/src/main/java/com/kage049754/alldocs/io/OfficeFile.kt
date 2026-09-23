package com.kage049754.alldocs.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

enum class OfficeType { DOCX, TXT, PDF, IMAGE, UNKNOWN }

object OfficeFile {
    fun typeOf(name: String): OfficeType = when (name.substringAfterLast('.', "").lowercase()) {
        "docx" -> OfficeType.DOCX
        "txt", "md", "csv", "log" -> OfficeType.TXT
        "pdf" -> OfficeType.PDF
        "jpg", "jpeg", "png", "webp", "gif" -> OfficeType.IMAGE
        else -> OfficeType.UNKNOWN
    }

    fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "Document"
    }

    fun readText(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

    fun mimeFor(type: OfficeType): String = when (type) {
        OfficeType.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        OfficeType.PDF -> "application/pdf"
        OfficeType.TXT -> "text/plain"
        OfficeType.IMAGE -> "image/*"
        OfficeType.UNKNOWN -> "*/*"
    }
}

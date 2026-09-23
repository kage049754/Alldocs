package com.kage049754.alldocs.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kage049754.alldocs.data.Document
import com.kage049754.alldocs.io.DocxReader
import com.kage049754.alldocs.io.DocxWriter
import com.kage049754.alldocs.io.OfficeFile
import com.kage049754.alldocs.io.OfficeType
import com.kage049754.alldocs.io.PdfWriter
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlldocsApp(vm: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val docs by vm.documents.collectAsState()
    var editing by remember { mutableStateOf<Document?>(null) }
    var query by remember { mutableStateOf("") }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val name = OfficeFile.displayName(uri)
            val type = OfficeFile.typeOf(name)
            val body = when (type) {
                OfficeType.DOCX -> DocxReader.read(context, uri)
                OfficeType.TXT -> OfficeFile.readText(context, uri)
                else -> "This file type can be opened, but document editing is not available yet."
            }
            editing = Document("__file__:" + type.name + ":" + uri, name.substringBeforeLast('.'), body, System.currentTimeMillis())
        }
    }

    val saveDocx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri -> if (uri != null && editing != null) DocxWriter.write(context, uri, editing!!.body) }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && editing != null) PdfWriter.write(context, uri, editing!!.title, editing!!.body)
    }

    val saveTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null && editing != null) context.contentResolver.openOutputStream(uri)?.use {
            it.write(editing!!.body.toByteArray())
        }
    }

    if (editing != null) {
        EditorScreen(
            doc = editing!!,
            onBack = { editing = null },
            onSave = { title, body ->
                val current = editing!!
                if (current.id.startsWith("__file__:")) {
                    editing = current.copy(title = title, body = body, updatedAt = System.currentTimeMillis())
                } else {
                    vm.save(current.id.takeUnless { it == "__new__" }, title, body)
                    editing = null
                }
            },
            onSaveOriginal = {
                val current = editing!!
                if (current.id.startsWith("__file__:")) {
                    val parts = current.id.split(":", limit = 3)
                    val type = OfficeType.valueOf(parts[1])
                    val uri = android.net.Uri.parse(parts[2])
                    when (type) {
                        OfficeType.DOCX -> DocxWriter.write(context, uri, current.body)
                        OfficeType.TXT -> context.contentResolver.openOutputStream(uri)?.use {
                            it.write(current.body.toByteArray())
                        }
                        else -> Unit
                    }
                    editing = current.copy(updatedAt = System.currentTimeMillis())
                } else {
                    vm.save(current.id.takeUnless { it == "__new__" }, current.title, current.body)
                    editing = null
                }
            },
            onSaveAsDocx = { saveDocx.launch(editing!!.title.ifBlank { "Document" } + ".docx") },
            onSaveAsPdf = { savePdf.launch(editing!!.title.ifBlank { "Document" } + ".pdf") },
            onSaveAsTxt = { saveTxt.launch(editing!!.title.ifBlank { "Document" } + ".txt") }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Alldocs", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { openFile.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "text/plain", "application/pdf", "image/*"
                    )) }) { Icon(Icons.Default.FolderOpen, "Open file") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Document("__new__", "", "", 0L) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New document") }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text("Your documents", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Open, edit and save documents offline.", style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("Search documents") },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = { editing = Document("__new__", "", "", 0L) }) {
                    Icon(Icons.Default.NoteAdd, null); Spacer(Modifier.width(6.dp)); Text("Blank document")
                }
                OutlinedButton(onClick = { openFile.launch(arrayOf(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain", "application/pdf", "image/*"
                )) }) {
                    Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Open from phone")
                }
            }

            val filtered = docs.filter { it.title.contains(query, true) || it.body.contains(query, true) }
            if (filtered.isEmpty()) EmptyState()
            else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(filtered, key = { it.id }) { d ->
                    DocumentCard(d, { editing = d }, { vm.delete(d.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Description, null, Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("No documents yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Create a document or open a Word/text file from your phone.")
        }
    }
}

@Composable
private fun DocumentCard(d: Document, open: () -> Unit, delete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick = open, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title.ifBlank { "Untitled document" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(d.body.replace("\\n", " ").ifBlank { "Empty document" }, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(3.dp))
                Text(DateFormat.getDateTimeInstance().format(Date(d.updatedAt)), style = MaterialTheme.typography.labelSmall)
            }
            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, null) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Delete") }, { delete(); menu = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    doc: Document,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onSaveOriginal: () -> Unit,
    onSaveAsDocx: () -> Unit,
    onSaveAsPdf: () -> Unit,
    onSaveAsTxt: () -> Unit
) {
    var title by remember { mutableStateOf(doc.title) }
    var body by remember { mutableStateOf(doc.body) }
    var showMore by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(title.ifBlank { "Untitled document" }, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        Text("Editing", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton({ onSaveOriginal() }) { Icon(Icons.Default.Save, "Save") }
                    IconButton({ showMore = !showMore }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(showMore, { showMore = false }) {
                        DropdownMenuItem({ Text("Save as Word (.docx)") }, { onSaveAsDocx(); showMore = false })
                        DropdownMenuItem({ Text("Export PDF (.pdf)") }, { onSaveAsPdf(); showMore = false })
                        DropdownMenuItem({ Text("Save as Text (.txt)") }, { onSaveAsTxt(); showMore = false })
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Document name") },
                leadingIcon = { Icon(Icons.Default.Title, null) }
            )

            Surface(shadowElevation = 2.dp) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EditorTool("B", "Bold") { body += "\\n**bold text**" }
                        EditorTool("I", "Italic") { body += "\\n*italic text*" }
                        EditorTool("U", "Underline") { body += "\\n<u>underlined text</u>" }
                        EditorTool("H1", "Heading") { body += "\\n# Heading" }
                        EditorTool("•", "Bullet list") { body += "\\n• List item" }
                        EditorTool("1.", "Numbered list") { body += "\\n1. List item" }
                        IconButton(onClick = { body += "\\n\\n" }) { Icon(Icons.Default.FormatAlignLeft, "Paragraph") }
                        IconButton(onClick = { body += "\\n" }) { Icon(Icons.Default.FormatClear, "Clear formatting") }
                    }
                    HorizontalDivider()
                }
            }

            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                contentAlignment = Alignment.TopCenter
            ) {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)),
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, lineHeight = 25.sp),
                    label = { Text("Start writing…") },
                    minLines = 22
                )
            }
        }
    }
}

@Composable
private fun EditorTool(text: String, description: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text, fontWeight = if (text == "B" || text == "H1") FontWeight.Bold else FontWeight.Normal)
    }
}

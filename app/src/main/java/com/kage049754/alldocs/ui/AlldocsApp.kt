package com.kage049754.alldocs.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kage049754.alldocs.data.Document
import com.kage049754.alldocs.io.DocxReader
import com.kage049754.alldocs.io.DocxWriter
import com.kage049754.alldocs.io.OfficeFile
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
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            val name = OfficeFile.displayName(uri)
            val type = OfficeFile.typeOf(name)
            val body = when (type) {
                com.kage049754.alldocs.io.OfficeType.DOCX -> DocxReader.read(context, uri)
                com.kage049754.alldocs.io.OfficeType.TXT -> OfficeFile.readText(context, uri)
                else -> "This format can be opened, but text editing is not available yet."
            }
            editing = Document("__file__:$uri", name.substringBeforeLast('.'), body, System.currentTimeMillis())
        }
    }

    val saveDocx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri ->
        if (uri != null && editing != null) DocxWriter.write(context, uri, editing!!.body)
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && editing != null) PdfWriter.write(context, uri, editing!!.title, editing!!.body)
    }
    val saveTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null && editing != null) context.contentResolver.openOutputStream(uri)?.use { it.write(editing!!.body.toByteArray()) }
    }

    if (editing != null) {
        EditorScreen(
            doc = editing!!,
            onBack = { editing = null },
            onSave = { title, body ->
                val current = editing!!
                if (current.id.startsWith("__file__:")) editing = current.copy(title = title, body = body, updatedAt = System.currentTimeMillis())
                else { vm.save(current.id.takeUnless { it == "__new__" }, title, body); editing = null }
            },
            onSaveOriginal = {
                val current = editing!!
                if (current.id.startsWith("__file__:")) {
                    val uri = androidx.core.net.toUri(current.id.removePrefix("__file__:"))
                    when (OfficeFile.typeOf(current.title)) {
                        com.kage049754.alldocs.io.OfficeType.DOCX -> DocxWriter.write(context, uri, current.body)
                        com.kage049754.alldocs.io.OfficeType.TXT -> context.contentResolver.openOutputStream(uri)?.use { it.write(current.body.toByteArray()) }
                        else -> Unit
                    }
                } else onSave(current.title, current.body)
            },
            onSaveAsDocx = { saveDocx.launch(editing!!.title.ifBlank { "Document" } + ".docx") },
            onSaveAsPdf = { savePdf.launch(editing!!.title.ifBlank { "Document" } + ".pdf") },
            onSaveAsTxt = { saveTxt.launch(editing!!.title.ifBlank { "Document" } + ".txt") }
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Alldocs", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = { FloatingActionButton({ editing = Document("__new__", "", "", 0L) }) { Icon(Icons.Default.Add, "New") } }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Button(onClick = { openFile.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain", "application/pdf", "image/*")) }) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Open file from phone")
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 12.dp), singleLine = true,
                placeholder = { Text("Search documents") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            val filtered = docs.filter { it.title.contains(query, true) || it.body.contains(query, true) }
            if (filtered.isEmpty()) EmptyState() else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { d -> DocumentCard(d, { editing = d }, { vm.delete(d.id) }) }
            }
        }
    }
}

@Composable private fun EmptyState() {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Description, null, Modifier.size(64.dp)); Spacer(Modifier.height(16.dp))
        Text("No documents yet", style = MaterialTheme.typography.headlineSmall)
        Text("Create a document or open a file from your phone.")
    }
}

@Composable private fun DocumentCard(d: Document, open: () -> Unit, delete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick = open, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Description, null, Modifier.size(36.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(d.body.replace("\n", " ").take(100), maxLines = 2)
                Text(DateFormat.getDateTimeInstance().format(Date(d.updatedAt)), style = MaterialTheme.typography.labelSmall)
            }
            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, null) }
            DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Delete") }, { delete(); menu = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EditorScreen(
    doc: Document, onBack: () -> Unit, onSave: (String, String) -> Unit,
    onSaveOriginal: () -> Unit, onSaveAsDocx: () -> Unit, onSaveAsPdf: () -> Unit, onSaveAsTxt: () -> Unit
) {
    var title by remember { mutableStateOf(doc.title) }
    var body by remember { mutableStateOf(doc.body) }
    var saveMenu by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Document editor") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            actions = {
                IconButton({ saveMenu = true }) { Icon(Icons.Default.Save, "Save") }
                DropdownMenu(saveMenu, { saveMenu = false }) {
                    DropdownMenuItem({ Text("Save changes") }, { onSaveOriginal(); saveMenu = false })
                    DropdownMenuItem({ Text("Save as Word (.docx)") }, { onSaveAsDocx(); saveMenu = false })
                    DropdownMenuItem({ Text("Export PDF (.pdf)") }, { onSaveAsPdf(); saveMenu = false })
                    DropdownMenuItem({ Text("Save as Text (.txt)") }, { onSaveAsTxt(); saveMenu = false })
                }
            }
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("File name") })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip({ body += "\n# " }, label = { Text("H1") })
                AssistChip({ body += "\n• " }, label = { Text("Bullet") })
                AssistChip({ body += "\n1. " }, label = { Text("Number") })
                AssistChip({ body += "**bold**" }, label = { Text("Bold") })
            }
            OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().weight(1f), label = { Text("Document content") }, minLines = 16)
        }
    }
}

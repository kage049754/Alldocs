package com.kage049754.alldocs.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Base64
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kage049754.alldocs.data.Document
import com.kage049754.alldocs.io.DocxReader
import com.kage049754.alldocs.io.DocxWriter
import com.kage049754.alldocs.io.OfficeFile
import com.kage049754.alldocs.io.OfficeType
import com.kage049754.alldocs.io.PdfWriter
import com.kage049754.alldocs.io.PdfReader
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlldocsApp(vm: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val docs by vm.documents.collectAsState()
    var editing by remember { mutableStateOf<Document?>(null) }
    var query by remember { mutableStateOf("") }
    var templateMenu by remember { mutableStateOf(false) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val name = OfficeFile.displayName(context, uri)
            val type = OfficeFile.typeOf(name)
            val body = when (type) {
                OfficeType.DOCX -> DocxReader.read(context, uri)
                OfficeType.TXT -> OfficeFile.readText(context, uri)
                OfficeType.PDF -> PdfReader.read(context, uri)
                OfficeType.IMAGE -> runCatching {
                    val mime = context.contentResolver.getType(uri) ?: "image/*"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    "<p><img src=\"data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}\" /></p>"
                }.getOrDefault("")
                OfficeType.UNKNOWN -> ""
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
                    val updated = current.copy(title = title, body = body, updatedAt = System.currentTimeMillis())
                    val parts = current.id.split(":", limit = 3)
                    val type = OfficeType.valueOf(parts[1])
                    val uri = android.net.Uri.parse(parts[2])
                    when (type) {
                        OfficeType.DOCX -> DocxWriter.write(context, uri, body)
                        OfficeType.TXT -> context.contentResolver.openOutputStream(uri)?.use {
                            it.write(body.toPlainText().toByteArray(Charsets.UTF_8))
                        }
                        else -> Unit
                    }
                    editing = updated
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
            onSaveAsTxt = { saveTxt.launch(editing!!.title.ifBlank { "Document" } + ".txt") },
            onOpenDefaultViewer = {
                if (editing!!.id.startsWith("__file__:")) {
                    val parts = editing!!.id.split(":", limit = 3)
                    val type = OfficeType.valueOf(parts[1])
                    val uri = android.net.Uri.parse(parts[2])
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = uri
                        this.type = OfficeFile.mimeFor(type)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                }
            }
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
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EditorScreen(
    doc: Document,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onSaveOriginal: () -> Unit,
    onSaveAsDocx: () -> Unit,
    onSaveAsPdf: () -> Unit,
    onSaveAsTxt: () -> Unit,
    onOpenDefaultViewer: () -> Unit
) {
    var title by remember { mutableStateOf(doc.title) }
    var viewMode by remember { mutableStateOf("Print layout") }
    var showMore by remember { mutableStateOf(false) }
    var showInsert by remember { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }
    var showLayout by remember { mutableStateOf(false) }
    var showTable by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var tableRows by remember { mutableStateOf(3) }
    var tableCols by remember { mutableStateOf(3) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("alldocs_editor", android.content.Context.MODE_PRIVATE) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var compactMode by remember { mutableStateOf(prefs.getBoolean("compact_toolbar", false)) }
    var showRuler by remember { mutableStateOf(prefs.getBoolean("show_ruler", true)) }
    var zoomPercent by remember { mutableStateOf(prefs.getInt("zoom_percent", 100).coerceIn(50, 200)) }
    var autosave by remember { mutableStateOf(prefs.getBoolean("autosave", false)) }
    var spellcheck by remember { mutableStateOf(prefs.getBoolean("spellcheck", true)) }
    var wordCount by remember { mutableStateOf(0) }
    var charCount by remember { mutableStateOf(0) }
    var pageCount by remember { mutableStateOf(1) }
    var editorTab by remember { mutableStateOf("Home") }

    fun persistEditorSetting(key: String, value: Any) {
        prefs.edit().apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
            }
        }.apply()
    }

    val saveBridge = remember(title, doc.id) {
        object {
            @JavascriptInterface
            fun save(html: String) {
                onSave(title.ifBlank { "Untitled document" }, html)
            }
        }
    }

    fun exec(js: String) {
        webView?.evaluateJavascript("javascript:$js", null)
    }

    LaunchedEffect(webView) {
        while (webView != null) {
            webView?.evaluateJavascript(
                "(function(){var p=document.getElementById('page');var t=(p?.innerText||'').trim();var n=document.querySelectorAll('.page-sheet').length;return JSON.stringify({w:t?t.split(/\\s+/).length:0,c:t.length,p:Math.max(1,n)});})()"
            ) { value ->
                runCatching {
                    val raw = value.removeSurrounding("\"")
                    val obj = org.json.JSONObject(raw)
                    wordCount = obj.optInt("w", 0)
                    charCount = obj.optInt("c", 0)
                    pageCount = obj.optInt("p", 1).coerceAtLeast(1)
                }
            }
            delay(1000)
        }
    }

    LaunchedEffect(autosave, webView) {
        if (!autosave) return@LaunchedEffect
        while (webView != null) {
            delay(15000)
            if (webView != null && (doc.id != "__new__" || title.isNotBlank())) exec("requestSave()")
        }
    }

    fun insertImage(uri: android.net.Uri) {
        runCatching {
            val mime = context.contentResolver.getType(uri) ?: "image/png"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            exec("insertImage('data:$mime;base64,$b64')")
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) insertImage(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(title.ifBlank { "Untitled document" }, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        Text(viewMode + " • " + pageCount + " page" + if (pageCount == 1) "" else "s", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { exec("undo()") }) { Icon(Icons.Default.Undo, "Undo") }
                    IconButton(onClick = { exec("redo()") }) { Icon(Icons.Default.Redo, "Redo") }
                    IconButton(onClick = { exec("requestSave()") }) { Icon(Icons.Default.Save, "Save") }
                    Box {
                        IconButton(onClick = { showMore = !showMore }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem({ Text("Save as Word (.docx)") }, { onSaveAsDocx(); showMore = false })
                            if (doc.id.startsWith("__file__:")) {
                                DropdownMenuItem({ Text("Open with phone's default viewer") }, { onOpenDefaultViewer(); showMore = false })
                            }
                            DropdownMenuItem({ Text("Export PDF (.pdf)") }, { onSaveAsPdf(); showMore = false })
                            DropdownMenuItem({ Text("Save as Text (.txt)") }, { onSaveAsTxt(); showMore = false })
                            DropdownMenuItem({ Text("Find / Replace") }, { exec("findReplace()"); showMore = false })
                            DropdownMenuItem({ Text("Editor settings") }, { showSettings = true; showMore = false })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Surface(tonalElevation = 1.dp) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Home", "Insert", "Layout", "View").forEach { tab ->
                            FilterChip(
                                selected = editorTab == tab,
                                onClick = { editorTab = tab },
                                label = { Text(tab) },
                                leadingIcon = {
                                    Icon(
                                        when (tab) {
                                            "Insert" -> Icons.Default.Add
                                            "Layout" -> Icons.Default.ViewAgenda
                                            "View" -> Icons.Default.Visibility
                                            else -> Icons.Default.Edit
                                        },
                                        null,
                                        Modifier.size(17.dp)
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text("$wordCount words", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Document name") },
                            leadingIcon = { Icon(Icons.Default.Title, null) }
                        )
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = { exec("requestSave()") },
                            label = { Text("Save") },
                            leadingIcon = { Icon(Icons.Default.Save, null, Modifier.size(16.dp)) }
                        )
                    }
                    HorizontalDivider()
                    when (editorTab) {
                        "Home" -> {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton({ exec("undo()") }) { Icon(Icons.Default.Undo, "Undo") }
                                IconButton({ exec("redo()") }) { Icon(Icons.Default.Redo, "Redo") }
                                EditorTool("B", "Bold") { exec("cmd('bold')") }
                                EditorTool("I", "Italic") { exec("cmd('italic')") }
                                EditorTool("U", "Underline") { exec("cmd('underline')") }
                                EditorTool("S", "Strike") { exec("cmd('strikeThrough')") }
                                EditorTool("12", "12 pt") { exec("fontSize('3')") }
                                EditorTool("16", "16 pt") { exec("fontSize('4')") }
                                EditorTool("20", "20 pt") { exec("fontSize('5')") }
                                EditorTool("H1", "Heading 1") { exec("formatBlock('h1')") }
                                EditorTool("H2", "Heading 2") { exec("formatBlock('h2')") }
                                EditorTool("P", "Normal") { exec("formatBlock('p')") }
                                IconButton({ exec("cmd('justifyLeft')") }) { Icon(Icons.Default.FormatAlignLeft, "Align left") }
                                IconButton({ exec("cmd('justifyCenter')") }) { Icon(Icons.Default.FormatAlignCenter, "Center") }
                                IconButton({ exec("cmd('justifyRight')") }) { Icon(Icons.Default.FormatAlignRight, "Align right") }
                                IconButton({ exec("cmd('justifyFull')") }) { Icon(Icons.Default.FormatAlignJustify, "Justify") }
                                IconButton({ exec("cmd('insertUnorderedList')") }) { Icon(Icons.Default.FormatListBulleted, "Bullets") }
                                IconButton({ exec("cmd('insertOrderedList')") }) { Icon(Icons.Default.FormatListNumbered, "Numbering") }
                            }
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                EditorTool("Black", "Text color") { exec("cmd('foreColor','#202124')") }
                                EditorTool("Blue", "Text color") { exec("cmd('foreColor','#1565c0')") }
                                EditorTool("Red", "Text color") { exec("cmd('foreColor','#c62828')") }
                                EditorTool("Green", "Text color") { exec("cmd('foreColor','#2e7d32')") }
                                EditorTool("Highlight", "Highlight") { exec("cmd('hiliteColor','#fff59d')") }
                                EditorTool("Clear", "Clear formatting") { exec("cmd('removeFormat')") }
                            }
                        }
                        "Insert" -> {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(5.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                EditorTool("Photo", "Insert photo") { imagePicker.launch("image/*") }
                                EditorTool("Table", "Insert table") { showTable = true }
                                EditorTool("Link", "Hyperlink") { exec("addLink()") }
                                EditorTool("HR", "Horizontal rule") { exec("cmd('insertHorizontalRule')") }
                                EditorTool("Image", "Image format") { exec("formatImage()") }
                                EditorTool("Row+", "Add row") { exec("addTableRow()") }
                                EditorTool("Row−", "Delete row") { exec("deleteTableRow()") }
                                EditorTool("Col+", "Add column") { exec("addTableCol()") }
                                EditorTool("Col−", "Delete column") { exec("deleteTableCol()") }
                                EditorTool("Merge", "Merge cells") { exec("mergeCells()") }
                                EditorTool("Break", "Page break") { exec("pageBreak()") }
                            }
                        }
                        "Layout" -> {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(5.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                EditorTool("A4", "A4") { exec("setPage('A4')") }
                                EditorTool("Letter", "Letter") { exec("setPage('Letter')") }
                                EditorTool("Portrait", "Portrait") { exec("setOrientation('portrait')") }
                                EditorTool("Landscape", "Landscape") { exec("setOrientation('landscape')") }
                                EditorTool("Margins", "Margins") { exec("setMargins()") }
                            }
                        }
                        "View" -> {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(5.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                EditorTool("Print", "Print layout") { viewMode = "Print layout"; exec("setViewMode('print-layout')") }
                                EditorTool("Read", "Reading view") { viewMode = "Reading view"; exec("setViewMode('reading-view')") }
                                EditorTool("Phone", "Mobile view") { viewMode = "Mobile view"; exec("setViewMode('mobile-view')") }
                                EditorTool("−", "Zoom out") {
                                    zoomPercent = (zoomPercent - 10).coerceAtLeast(50)
                                    persistEditorSetting("zoom_percent", zoomPercent)
                                    exec("setZoom($zoomPercent)")
                                }
                                EditorTool("$zoomPercent%", "Zoom") { }
                                EditorTool("+", "Zoom in") {
                                    zoomPercent = (zoomPercent + 10).coerceAtMost(200)
                                    persistEditorSetting("zoom_percent", zoomPercent)
                                    exec("setZoom($zoomPercent)")
                                }
                                EditorTool("Settings", "Editor settings") { showSettings = true }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    WebView(it).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = true
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        addJavascriptInterface(saveBridge, "AlldocsEditor")
                        webViewClient = WebViewClient()
                        loadDataWithBaseURL("https://alldocs.local/", editorHtml(doc.body, darkMode, zoomPercent), "text/html", "UTF-8", null)
                    }
                },
                update = { webView = it }
            )
        }
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Editor settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark workspace", Modifier.weight(1f))
                        Switch(checked = darkMode, onCheckedChange = {
                            darkMode = it
                            persistEditorSetting("dark_mode", it)
                            exec("setTheme(" + it + ")")
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Compact toolbar", Modifier.weight(1f))
                        Switch(checked = compactMode, onCheckedChange = {
                            compactMode = it
                            persistEditorSetting("compact_toolbar", it)
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ruler", Modifier.weight(1f))
                        Switch(checked = showRuler, onCheckedChange = {
                            showRuler = it
                            persistEditorSetting("show_ruler", it)
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Autosave", Modifier.weight(1f))
                        Switch(checked = autosave, onCheckedChange = {
                            autosave = it
                            persistEditorSetting("autosave", it)
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Spellcheck", Modifier.weight(1f))
                        Switch(checked = spellcheck, onCheckedChange = {
                            spellcheck = it
                            persistEditorSetting("spellcheck", it)
                            exec("setSpellcheck($it)")
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Zoom", Modifier.weight(1f))
                        IconButton({
                            zoomPercent = (zoomPercent - 10).coerceAtLeast(50)
                            persistEditorSetting("zoom_percent", zoomPercent)
                            exec("setZoom($zoomPercent)")
                        }) { Icon(Icons.Default.Remove, "Zoom out") }
                        Text("$zoomPercent%", modifier = Modifier.width(52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton({
                            zoomPercent = (zoomPercent + 10).coerceAtMost(200)
                            persistEditorSetting("zoom_percent", zoomPercent)
                            exec("setZoom($zoomPercent)")
                        }) { Icon(Icons.Default.Add, "Zoom in") }
                        TextButton({
                            zoomPercent = 100
                            persistEditorSetting("zoom_percent", 100)
                            exec("setZoom(100)")
                        }) { Text("Reset") }
                    }
                    Text("Print layout is the paper view you edit directly. Mobile view gives you a phone-width page, while Reading view removes the paper frame.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton({ showSettings = false }) { Text("Done") } }
        )
    }

    if (showTable) {
        AlertDialog(
            onDismissRequest = { showTable = false },
            title = { Text("Insert table") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose rows and columns")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rows", Modifier.weight(1f))
                        IconButton({ tableRows = (tableRows - 1).coerceAtLeast(1) }) { Icon(Icons.Default.Remove, null) }
                        Text("$tableRows")
                        IconButton({ tableRows = (tableRows + 1).coerceAtMost(20) }) { Icon(Icons.Default.Add, null) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Columns", Modifier.weight(1f))
                        IconButton({ tableCols = (tableCols - 1).coerceAtLeast(1) }) { Icon(Icons.Default.Remove, null) }
                        Text("$tableCols")
                        IconButton({ tableCols = (tableCols + 1).coerceAtMost(10) }) { Icon(Icons.Default.Add, null) }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    exec("insertTable($tableRows,$tableCols)")
                    showTable = false
                }) { Text("Insert") }
            },
            dismissButton = { TextButton({ showTable = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EditorTool(label: String, description: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(38.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

private fun String.toPlainText(): String =
    replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\\n")
        .replace(Regex("</p>|</div>|</h[1-6]>", RegexOption.IGNORE_CASE), "\\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()

private fun editorHtml(initial: String, darkMode: Boolean = false, zoomPercent: Int = 100): String {
    val source = if (initial.trimStart().startsWith("<")) initial else initial
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("\n", "<br>")

    val initialBase64 = Base64.encodeToString(source.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    return """
<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
*{box-sizing:border-box}
body{margin:0;background:#d9dce1;font-family:Arial,sans-serif;color:#202124;padding:22px 0 110px;min-height:100vh;overflow-x:auto}
#page{width:794px;min-height:1123px;margin:0 auto;background:transparent;padding:0;box-shadow:none;font-size:16px;line-height:1.55;outline:none;transition:width .15s ease;overflow:visible;position:relative}
#page.print-layout{background:transparent}
#pages{width:100%}
.page-sheet{width:100%;min-height:1123px;background:#fff;margin:0 0 28px;padding:72px;box-shadow:0 1px 3px rgba(0,0,0,.18),0 8px 28px rgba(0,0,0,.14);overflow:hidden;overflow-wrap:anywhere;outline:none}
.page-sheet:focus{box-shadow:0 3px 18px rgba(0,0,0,.22)}
.page-sheet>*:first-child{margin-top:0}
.page-sheet>*{break-inside:avoid}
#page.a4 .page-sheet{min-height:1123px}
#page.letter .page-sheet{min-height:1056px}
#page.landscape .page-sheet{min-height:794px}
#paginationOverlay{position:absolute;top:22px;left:50%;width:794px;height:1123px;transform:translateX(-50%);pointer-events:none;z-index:5}
.page-boundary{position:absolute;left:0;width:100%;height:18px;background:#d9dce1;border:0;box-shadow:0 -1px 2px rgba(0,0,0,.08),0 1px 2px rgba(0,0,0,.08)}
.page-label{position:absolute;right:12px;transform:translateY(-50%);font:600 11px Arial,sans-serif;color:#687078;background:#d9dce1;padding:3px 8px;border-radius:10px;letter-spacing:.2px}
.page-first-label{position:absolute;top:-1px;right:12px;font:600 11px Arial,sans-serif;color:#687078;background:#d9dce1;padding:3px 8px;border-radius:10px}
@media (max-width:820px){
  body{padding-top:12px}
  #page{margin-left:14px;margin-right:14px}
  #paginationOverlay{top:12px}
}
#page.reading{width:100%;min-height:100vh;box-shadow:none;padding:0;background:#fff}
#page.reading .page-sheet{width:100%;min-height:auto;margin:0;box-shadow:none;padding:24px 22px;overflow:visible}
#page.mobile{width:min(390px,92vw);min-height:844px;padding:0;box-shadow:none;background:transparent}
#page.mobile .page-sheet{width:100%;min-height:844px;margin:0 0 18px;padding:28px 22px;box-shadow:0 2px 14px rgba(0,0,0,.16);overflow:visible}
#page:not(.reading):focus{box-shadow:0 3px 18px rgba(0,0,0,.22)}
p{margin:0 0 10px}
ul,ol{padding-left:28px}
blockquote{margin:12px 0;padding-left:14px;border-left:4px solid #9aa0a6;color:#5f6368}
code{background:#f1f3f4;padding:2px 4px;border-radius:4px}

img{max-width:100%;height:auto;display:block;margin:12px auto}
table{border-collapse:collapse;width:100%;margin:14px 0}
td,th{border:1px solid #777;padding:8px;min-width:45px}
th{background:#e9eef6}
hr{border:0;border-top:1px solid #777;margin:18px 0}
.page-break{page-break-after:always;border-top:2px dashed #aaa;margin:20px 0;height:1px}
h1{font-size:28px}h2{font-size:23px}h3{font-size:19px}#page.a4{width:794px;min-height:1123px}
#page.letter{width:816px;min-height:1056px}
#page.landscape{width:1123px;min-height:794px}

a{color:#1565c0;text-decoration:underline}
@media print{body{background:#fff;padding:0}#page{width:auto;min-height:auto;margin:0;box-shadow:none;padding:20mm}.page-break{page-break-after:always}.page-break{border:0;height:0;margin:0}}
</style></head><body>
<div id="page" class="print-layout"><div id="pages"></div></div>
<div id="paginationOverlay" aria-hidden="true"></div>
<script>
const p=document.getElementById('page');
setTimeout(function(){setTheme($darkMode);setZoom($zoomPercent);setSpellcheck(true);initializePages()},80);
function cmd(c,v=null){const s=currentSheet();if(!s)return;s.focus();document.execCommand(c,false,v)}
function undo(){cmd('undo')} function redo(){cmd('redo')}
function formatBlock(v){cmd('formatBlock',v)}
function insertImage(src){const s=currentSheet();if(!s)return;s.focus();document.execCommand('insertHTML',false,'<img src="'+src+'" alt="Image" style="max-width:100%;height:auto">');reflowPages()}
function formatImage(){let im=document.querySelector('img[data-selected="true"]');if(!im){alert('Tap an image first');return}let w=prompt('Image width (px)',String(im.getBoundingClientRect().width|0));if(w)im.style.width=Math.max(40,parseInt(w)||40)+'px';let a=prompt('Alignment: left, center, right','center');if(a==='left'||a==='center'||a==='right'){im.style.display='block';im.style.margin=a==='center'?'12px auto':a==='right'?'12px 0 12px auto':'12px 0'}}
function addTableRow(){let t=document.querySelector('table:last-of-type');if(!t)return;let r=t.rows[t.rows.length-1],nr=t.insertRow();for(let i=0;i<r.cells.length;i++){let cell=nr.insertCell();cell.innerHTML='<br>'}}
function addTableCol(){let t=document.querySelector('table:last-of-type');if(!t)return;for(let r of t.rows){let cell=r.insertCell();cell.innerHTML='<br>'}}
function selectedTable(){let s=window.getSelection();let n=s&&s.anchorNode;return n?(n.nodeType===3?n.parentElement:n).closest('table'):document.querySelector('table:last-of-type')}
function selectedCell(){let s=window.getSelection();let n=s&&s.anchorNode;return n?(n.nodeType===3?n.parentElement:n).closest('td,th'):null}
function deleteTableRow(){let cell=selectedCell();if(!cell)return;let row=cell.parentElement;if(row.parentElement.rows.length<=1)return;row.remove()}
function deleteTableCol(){let cell=selectedCell(),t=cell&&cell.closest('table');if(!cell||!t)return;let i=cell.cellIndex;if(t.rows[0].cells.length<=1)return;for(let r of t.rows)if(r.cells[i])r.deleteCell(i)}
function mergeCells(){let cell=selectedCell();if(!cell)return;let next=cell.nextElementSibling;if(!next)return;cell.colSpan=(cell.colSpan||1)+(next.colSpan||1);cell.innerHTML+=(cell.innerHTML?' ':'')+next.innerHTML;next.remove()}
function insertTable(r,c){const s=currentSheet();if(!s)return;s.focus();let h='<table><tbody>';for(let i=0;i<r;i++){h+='<tr>';for(let j=0;j<c;j++){h+=(i===0?'<th>':'<td>')+'Cell '+(i+1)+','+(j+1)+(i===0?'</th>':'</td>')}h+='</tr>'}h+='</tbody></table><p><br></p>';document.execCommand('insertHTML',false,h);reflowPages()}
function pageBreak(){const s=currentSheet();if(!s)return;s.focus();document.execCommand('insertHTML',false,'<div class="page-break"></div><p><br></p>');reflowPages()}
function setViewMode(v){p.classList.toggle('reading',v==='reading-view');p.classList.toggle('mobile',v==='mobile-view');p.classList.toggle('print-layout',v==='print-layout');reflowPages();const s=currentSheet();if(s)s.focus()}
function setPage(v){p.dataset.page=v;p.classList.toggle('letter',v==='Letter');p.classList.toggle('a4',v==='A4');reflowPages()}
function setOrientation(v){p.dataset.orientation=v;p.classList.toggle('landscape',v==='landscape');reflowPages();const s=currentSheet();if(s)s.focus()}
function setMargins(){let v=prompt('Margins in px (8-120)','72');if(v){let n=Math.min(120,Math.max(8,parseInt(v)||72));document.querySelectorAll('.page-sheet').forEach(s=>s.style.padding=n+'px');reflowPages()}}
function addLink(){let u=prompt('Enter URL');if(u)cmd('createLink',u)}
function findReplace(){let q=prompt('Find text');if(!q)return;let r=prompt('Replace with','');if(r!==null)p.innerHTML=p.innerHTML.split(q).join(r)}
function setTheme(d){
  document.body.style.background=d?"#202124":"#e5e7eb";
  document.body.style.color=d?"#e8eaed":"#202124";
  p.style.caretColor=d?"#ffffff":"#202124";
}
function setSpellcheck(v){p.spellcheck=!!v;document.querySelectorAll('.page-sheet').forEach(s=>s.spellcheck=!!v)}
function setZoom(v){
  const requested=Math.max(50,Math.min(200,parseInt(v)||100));
  const fit=Math.min(1,Math.max(.42,(window.innerWidth-28)/794));
  document.body.style.zoom=(fit*(requested/100)).toString();
  renderPagination()
}
function pageHeight(){if(p.classList.contains('landscape'))return 794;if(p.classList.contains('letter'))return 1056;return 1123}
function pagePadding(){return 144}
function pageContentHeight(){return pageHeight()-pagePadding()}
function currentSheet(){let s=window.getSelection(),n=s&&s.anchorNode;return (n?(n.nodeType===3?n.parentElement:n):null)?.closest('.page-sheet')||document.querySelector('.page-sheet')}
function blocks(){return Array.from(document.querySelectorAll('.page-sheet > *'))}
function createSheet(){
  const s=document.createElement('div');s.className='page-sheet';s.contentEditable='true';s.spellcheck=p.spellcheck;s.innerHTML='<p><br></p>';
  s.addEventListener('input',function(){requestAnimationFrame(reflowPages)});
  s.addEventListener('click',function(){syncActiveSheet(s)});
  s.addEventListener('keydown',function(e){if((e.ctrlKey||e.metaKey)&&e.key==='s'){e.preventDefault();requestSave()}});
  return s
}
function syncActiveSheet(s){document.querySelectorAll('.page-sheet[data-active]').forEach(x=>x.removeAttribute('data-active'));if(s)s.setAttribute('data-active','true')}
function rawHtml(){return Array.from(document.querySelectorAll('.page-sheet')).map(s=>s.innerHTML).join('')}
function normalizeSheets(){
  const all=Array.from(document.querySelectorAll('.page-sheet'));
  all.forEach(s=>{if(!s.innerHTML.trim())s.innerHTML='<p><br></p>'});
  while(all.length>1 && !all[all.length-1].innerText.trim() && all[all.length-1].querySelectorAll('img,table,hr').length===0){all.pop().remove()}
}
function overflowAmount(s){return s.scrollHeight-(s.clientHeight||pageContentHeight())}
function moveLastBlock(from,to){
  const children=Array.from(from.children);
  if(children.length<=1)return false;
  const last=children[children.length-1];
  if(last.classList.contains('page-break')){last.remove();return true}
  to.insertBefore(last,to.firstChild);
  return true
}
function reflowPages(){
  const sheets=Array.from(document.querySelectorAll('.page-sheet'));
  if(!sheets.length){document.getElementById('pages').appendChild(createSheet())}
  let list=Array.from(document.querySelectorAll('.page-sheet'));
  for(let i=0;i<list.length;i++){
    const s=list[i];
    while(overflowAmount(s)>1){
      let next=list[i+1];
      if(!next){next=createSheet();document.getElementById('pages').appendChild(next);list.push(next)}
      if(!moveLastBlock(s,next))break;
    }
    if(i>0){
      const prev=list[i-1];
      while(prev.scrollHeight < prev.clientHeight-8 && s.children.length>1){
        const first=s.firstElementChild;
        if(first.classList.contains('page-break')){first.remove();continue}
        prev.appendChild(first);
        if(prev.scrollHeight>prev.clientHeight){s.insertBefore(first,s.firstChild);break}
      }
    }
  }
  normalizeSheets();updatePageLabels();requestAnimationFrame(updateStats)
}
function updatePageLabels(){
  const o=document.getElementById('paginationOverlay');if(!o)return;
  if(p.classList.contains('reading')||p.classList.contains('mobile')){o.style.display='none';return}
  o.style.display='none';
}
function initializePages(){
  const holder=document.getElementById('pages');holder.innerHTML='';
  const s=createSheet();
  const initialHtml=decodeURIComponent(escape(atob('$initialBase64')));
  s.innerHTML=initialHtml||'<p><br></p>';
  holder.appendChild(s);
  reflowPages();syncActiveSheet(s)
}
function updateStats(){
  const t=(p.innerText||'').trim(), sheets=document.querySelectorAll('.page-sheet').length;
  if(window.AlldocsStats)window.AlldocsStats(JSON.stringify({w:t?t.split(/\\s+/).length:0,c:t.length,p:Math.max(1,sheets)}))
}
function requestSave(){window.AlldocsEditor.save(rawHtml())}
p.addEventListener('click',e=>{document.querySelectorAll('img[data-selected]').forEach(x=>x.removeAttribute('data-selected'));if(e.target.tagName==='IMG')e.target.setAttribute('data-selected','true')})
window.addEventListener('resize',()=>setZoom($zoomPercent))
</script></body></html>
"""
}

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
@SuppressLint("SetJavaScriptEnabled")
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
    var viewMode by remember { mutableStateOf("Print layout") }
    var showMore by remember { mutableStateOf(false) }
    var showInsert by remember { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }
    var showLayout by remember { mutableStateOf(false) }
    var showTable by remember { mutableStateOf(false) }
    var tableRows by remember { mutableStateOf(3) }
    var tableCols by remember { mutableStateOf(3) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current

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
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(title.ifBlank { "Untitled document" }, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        Text(viewMode, style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton({ exec("requestSave()") }) { Icon(Icons.Default.Save, "Save") }
                    IconButton({ showMore = !showMore }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(showMore, { showMore = false }) {
                        DropdownMenuItem({ Text("Save as Word (.docx)") }, { onSaveAsDocx(); showMore = false })
                        DropdownMenuItem({ Text("Export PDF (.pdf)") }, { onSaveAsPdf(); showMore = false })
                        DropdownMenuItem({ Text("Save as Text (.txt)") }, { onSaveAsTxt(); showMore = false })
                        DropdownMenuItem({
                            Text(if (viewMode == "Reading view") "Print layout" else "Reading view")
                        }, {
                            viewMode = if (viewMode == "Reading view") "Print layout" else "Reading view"
                            val mode = viewMode.lowercase().replace(" ", "-")
                            exec("setViewMode('$mode')")
                            showMore = false
                        })
                        DropdownMenuItem({ Text("Insert photo") }, { imagePicker.launch("image/*"); showMore = false })
                        DropdownMenuItem({ Text("Insert table") }, { showTable = true; showMore = false })
                        DropdownMenuItem({ Text("Find / Replace") }, { exec("findReplace()"); showMore = false })
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                singleLine = true,
                label = { Text("Document name") },
                leadingIcon = { Icon(Icons.Default.Title, null) }
            )
            Surface(shadowElevation = 3.dp) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton({ exec("undo()") }) { Icon(Icons.Default.Undo, "Undo") }
                        IconButton({ exec("redo()") }) { Icon(Icons.Default.Redo, "Redo") }
                        EditorTool("B", "Bold") { exec("cmd('bold')") }
                        EditorTool("I", "Italic") { exec("cmd('italic')") }
                        EditorTool("U", "Underline") { exec("cmd('underline')") }
                        EditorTool("S", "Strike") { exec("cmd('strikeThrough')") }
                        IconButton({ showFormat = !showFormat }) { Icon(Icons.Default.FormatSize, "Font") }
                        IconButton({ exec("cmd('justifyLeft')") }) { Icon(Icons.Default.FormatAlignLeft, "Align left") }
                        IconButton({ exec("cmd('justifyCenter')") }) { Icon(Icons.Default.FormatAlignCenter, "Center") }
                        IconButton({ exec("cmd('justifyRight')") }) { Icon(Icons.Default.FormatAlignRight, "Right") }
                        IconButton({ exec("cmd('justifyFull')") }) { Icon(Icons.Default.FormatAlignJustify, "Justify") }
                        IconButton({ exec("cmd('insertUnorderedList')") }) { Icon(Icons.Default.FormatListBulleted, "Bullets") }
                        IconButton({ exec("cmd('insertOrderedList')") }) { Icon(Icons.Default.FormatListNumbered, "Numbering") }
                        IconButton({ showInsert = !showInsert }) { Icon(Icons.Default.Add, "Insert") }
                        IconButton({ showLayout = !showLayout }) { Icon(Icons.Default.ViewAgenda, "Layout") }
                    }
                    if (showFormat) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            EditorTool("H1", "Heading 1") { exec("formatBlock('h1')") }
                            EditorTool("H2", "Heading 2") { exec("formatBlock('h2')") }
                            EditorTool("P", "Normal") { exec("formatBlock('p')") }
                            EditorTool("12", "12pt") { exec("fontSize('3')") }
                            EditorTool("16", "16pt") { exec("fontSize('4')") }
                            EditorTool("20", "20pt") { exec("fontSize('5')") }
                            EditorTool("Clear", "Clear formatting") { exec("cmd('removeFormat')") }
                        }
                    }
                    if (showInsert) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            EditorTool("Photo", "Insert photo") { imagePicker.launch("image/*") }
                            EditorTool("Table", "Insert table") { showTable = true }
                            EditorTool("Link", "Hyperlink") { exec("addLink()") }
                            EditorTool("HR", "Horizontal rule") { exec("cmd('insertHorizontalRule')") }
                            EditorTool("Img", "Image format") { exec("formatImage()") }
                            EditorTool("Rows", "Add table row") { exec("addTableRow()") }
                            EditorTool("Cols", "Add table column") { exec("addTableCol()") }
                            EditorTool("Break", "Page break") { exec("pageBreak()") }
                        }
                    }
                    if (showLayout) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            EditorTool("A4", "A4 page") { exec("setPage('A4')") }
                            EditorTool("Letter", "Letter page") { exec("setPage('Letter')") }
                            EditorTool("Portrait", "Portrait") { exec("setOrientation('portrait')") }
                            EditorTool("Landscape", "Landscape") { exec("setOrientation('landscape')") }
                            EditorTool("Margins", "Margins") { exec("setMargins()") }
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
                        loadDataWithBaseURL("https://alldocs.local/", editorHtml(doc.body), "text/html", "UTF-8", null)
                    }
                },
                update = { webView = it }
            )
        }
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

private fun editorHtml(initial: String): String {
    val source = if (initial.trimStart().startsWith("<")) initial else initial
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("\n", "<br>")

    return """
<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
*{box-sizing:border-box}
body{margin:0;background:#eef1f5;font-family:Arial,sans-serif;color:#202124;padding:16px 0 80px}
#page{width:min(94vw,760px);min-height:calc(100vh - 110px);margin:0 auto;background:#fff;padding:42px 34px;box-shadow:0 1px 8px rgba(0,0,0,.16);font-size:16px;line-height:1.55;outline:none}
#page.reading{width:100%;min-height:100vh;box-shadow:none;padding:22px}
img{max-width:100%;height:auto;display:block;margin:12px auto}
table{border-collapse:collapse;width:100%;margin:14px 0}
td,th{border:1px solid #777;padding:8px;min-width:45px}
th{background:#e9eef6}
hr{border:0;border-top:1px solid #777;margin:18px 0}
.page-break{page-break-after:always;border-top:2px dashed #aaa;margin:20px 0;height:1px}
h1{font-size:28px}h2{font-size:23px}h3{font-size:19px}
a{color:#1565c0;text-decoration:underline}
@media print{body{background:#fff;padding:0}#page{width:auto;min-height:auto;margin:0;box-shadow:none;padding:20mm}.page-break{page-break-after:always}}
</style></head><body>
<div id="page" contenteditable="true" spellcheck="true">$source</div>
<script>
const p=document.getElementById('page');
function cmd(c,v=null){p.focus();document.execCommand(c,false,v)}
function undo(){cmd('undo')} function redo(){cmd('redo')}
function formatBlock(v){cmd('formatBlock',v)}
function insertImage(src){p.focus();document.execCommand('insertHTML',false,'<img src="'+src+'" alt="Image" style="max-width:100%;height:auto">')}
function formatImage(){let im=document.querySelector('img[data-selected="true"]');if(!im){alert('Tap an image first');return}let w=prompt('Image width (px)',String(im.getBoundingClientRect().width|0));if(w)im.style.width=Math.max(40,parseInt(w)||40)+'px';let a=prompt('Alignment: left, center, right','center');if(a==='left'||a==='center'||a==='right'){im.style.display='block';im.style.margin=a==='center'?'12px auto':a==='right'?'12px 0 12px auto':'12px 0'}}
function addTableRow(){let t=document.querySelector('table:last-of-type');if(!t)return;let r=t.rows[t.rows.length-1],nr=t.insertRow();for(let i=0;i<r.cells.length;i++){let cell=nr.insertCell();cell.innerHTML='<br>'}}
function addTableCol(){let t=document.querySelector('table:last-of-type');if(!t)return;for(let r of t.rows){let cell=r.insertCell();cell.innerHTML='<br>'}}
function insertTable(r,c){let h='<table><tbody>';for(let i=0;i<r;i++){h+='<tr>';for(let j=0;j<c;j++){h+=(i===0?'<th contenteditable="true">':'<td contenteditable="true">')+'Cell '+(i+1)+','+(j+1)+(i===0?'</th>':'</td>')}h+='</tr>'}h+='</tbody></table><p><br></p>';document.execCommand('insertHTML',false,h)}
function pageBreak(){document.execCommand('insertHTML',false,'<div class="page-break"></div><p><br></p>')}
function setViewMode(v){p.classList.toggle('reading',v==='reading-view')}
function setPage(v){p.dataset.page=v}
function setOrientation(v){p.dataset.orientation=v;p.style.transform=v==='landscape'?'rotate(0deg)':''}
function setMargins(){p.style.padding='28px'}
function addLink(){let u=prompt('Enter URL');if(u)cmd('createLink',u)}
function findReplace(){let q=prompt('Find text');if(!q)return;let r=prompt('Replace with','');if(r!==null)p.innerHTML=p.innerHTML.split(q).join(r)}
function requestSave(){window.AlldocsEditor.save(p.innerHTML)}
p.addEventListener('click',e=>{document.querySelectorAll('img[data-selected]').forEach(x=>x.removeAttribute('data-selected'));if(e.target.tagName==='IMG')e.target.setAttribute('data-selected','true')})
p.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key==='s'){e.preventDefault();requestSave()}})
</script></body></html>
"""
}


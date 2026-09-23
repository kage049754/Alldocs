package com.kage049754.alldocs.ui
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kage049754.alldocs.data.Document
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlldocsApp(vm:AppViewModel=viewModel(factory=AppViewModel.factory(androidx.compose.ui.platform.LocalContext.current))){
 val docs by vm.documents.collectAsState(); var editing by remember{mutableStateOf<Document?>(null)};var query by remember{mutableStateOf("")}
 if(editing!=null){EditorScreen(editing!!,{editing=null}){t,b->val id=editing!!.id.takeUnless{it=="__new__"};vm.save(id,t,b);editing=null};return}
 var menu by remember{mutableStateOf(false)}
 Scaffold(topBar={TopAppBar(title={Text("Alldocs",fontWeight=FontWeight.Bold)},actions={IconButton({menu=true}){Icon(Icons.Default.MoreVert,null)};DropdownMenu(menu,{menu=false}){DropdownMenuItem({Text("About")},{menu=false}){Text("Offline-first document workspace.")}}})},
 floatingActionButton={FloatingActionButton({editing=Document("__new__","","",0)}){Icon(Icons.Default.Add,"New")}}){pad->
  Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)){
   OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search documents")},leadingIcon={Icon(Icons.Default.Search,null)})
   Spacer(Modifier.height(12.dp))
   val filtered=docs.filter{it.title.contains(query,true)||it.body.contains(query,true)}
   if(filtered.isEmpty())EmptyState() else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(filtered,key={it.id}){d->DocumentCard(d,{editing=d},{vm.delete(d.id)})}}
  }
 }
}
@Composable private fun EmptyState(){Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center){Icon(Icons.Default.Description,null,Modifier.size(64.dp));Spacer(Modifier.height(16.dp));Text("No documents yet",style=MaterialTheme.typography.headlineSmall);Text("Tap + to create your first offline document.")}}
@Composable private fun DocumentCard(d:Document,open:()->Unit,delete:()->Unit){
 var menu by remember{mutableStateOf(false)}
 Card(onClick=open,modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Default.Description,null,Modifier.size(36.dp));Column(Modifier.weight(1f)){Text(d.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(d.body.replace("\n"," ").take(100),maxLines=2);Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(d.updatedAt)),style=MaterialTheme.typography.labelSmall)};IconButton({menu=true}){Icon(Icons.Default.MoreVert,null)};DropdownMenu(menu,{menu=false}){DropdownMenuItem({Text("Delete")},{delete();menu=false},leadingIcon={Icon(Icons.Default.Delete,null)})}}}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EditorScreen(doc:Document,onBack:()->Unit,onSave:(String,String)->Unit){
 var title by remember{mutableStateOf(if(doc.id=="__new__")"" else doc.title)};var body by remember{mutableStateOf(if(doc.id=="__new__")"" else doc.body)}
 Scaffold(topBar={TopAppBar(title={Text(if(doc.id=="__new__")"New document" else "Edit document")},navigationIcon={IconButton(onBack){Icon(Icons.Default.ArrowBack,"Back")}},actions={TextButton({onSave(title,body)}){Text("Save")}})}){p->
  Column(Modifier.padding(p).fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Title")})
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){AssistChip({body+="\n# "},{Text("Heading")});AssistChip({body+="\n• "},{Text("Bullet")});AssistChip({body+="\n1. "},{Text("Number")})}
   OutlinedTextField(body,{body=it},Modifier.fillMaxWidth().weight(1f),label={Text("Write your document")},minLines=12)
  }
 }
}
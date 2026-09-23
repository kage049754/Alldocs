package com.kage049754.alldocs.ui
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kage049754.alldocs.data.Document
import com.kage049754.alldocs.data.DocumentStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
class AppViewModel(private val store:DocumentStore):ViewModel(){
 private val _documents=MutableStateFlow<List<Document>>(emptyList());val documents:StateFlow<List<Document>>=_documents.asStateFlow()
 init{viewModelScope.launch{store.documents.collect{_documents.value=it}}}
 fun save(id:String?,title:String,body:String){viewModelScope.launch{store.save(id,title,body)}}
 fun delete(id:String){viewModelScope.launch{store.delete(id)}}
 companion object{fun factory(c:Context)=object:ViewModelProvider.Factory{override fun <T:ViewModel>create(k:Class<T>):T{@Suppress("UNCHECKED_CAST") return AppViewModel(DocumentStore(c.applicationContext)) as T}}}}

package com.kage049754.alldocs.data
import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
private val Context.alldocsDataStore by preferencesDataStore("alldocs")
class DocumentStore(private val context:Context){
 private val index=stringPreferencesKey("index")
 val documents:Flow<List<Document>> = context.alldocsDataStore.data.map{p->
  val a=JSONArray(p[index]?:"[]")
  buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);val id=o.getString("id");add(Document(id,o.getString("title"),decode(p[stringPreferencesKey("body_$id")]?:""),o.getLong("updatedAt")))}}.sortedByDescending{it.updatedAt}
 }
 suspend fun save(id:String?,title:String,body:String){
  context.alldocsDataStore.edit{p->
   val actual=id?:UUID.randomUUID().toString();val a=JSONArray(p[index]?:"[]");var found=false
   for(i in 0 until a.length()){val o=a.getJSONObject(i);if(o.getString("id")==actual){o.put("title",title.ifBlank{"Untitled"});o.put("updatedAt",System.currentTimeMillis());found=true}}
   if(!found)a.put(JSONObject().apply{put("id",actual);put("title",title.ifBlank{"Untitled"});put("updatedAt",System.currentTimeMillis())})
   p[index]=a.toString();p[stringPreferencesKey("body_$actual")]=encode(body)
  }
 }
 suspend fun delete(id:String){context.alldocsDataStore.edit{p->val a=JSONArray(p[index]?:"[]");val n=JSONArray();for(i in 0 until a.length()){val o=a.getJSONObject(i);if(o.getString("id")!=id)n.put(o)};p[index]=n.toString();p.remove(stringPreferencesKey("body_$id"))}}
 private fun encode(v:String)=Base64.encodeToString(v.toByteArray(Charsets.UTF_8),Base64.NO_WRAP)
 private fun decode(v:String)=if(v.isBlank())"" else String(Base64.decode(v,Base64.NO_WRAP),Charsets.UTF_8)
}
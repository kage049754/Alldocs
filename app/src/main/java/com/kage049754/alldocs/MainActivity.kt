package com.kage049754.alldocs
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kage049754.alldocs.ui.AlldocsApp
import com.kage049754.alldocs.ui.theme.AlldocsTheme
class MainActivity: ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();setContent{AlldocsTheme{AlldocsApp()}}}
}
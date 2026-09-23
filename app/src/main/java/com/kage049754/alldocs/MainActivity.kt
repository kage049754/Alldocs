package com.kage049754.alldocs
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kage049754.alldocs.ui.AlldocsApp
import com.kage049754.alldocs.ui.AppViewModel
import com.kage049754.alldocs.ui.theme.AlldocsTheme
class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);enableEdgeToEdge();setContent{AlldocsTheme{val vm:AppViewModel=viewModel(factory=AppViewModel.factory(applicationContext));AlldocsApp(vm)}}}}

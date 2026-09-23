package com.kage049754.alldocs.ui.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val Light=lightColorScheme(primary=Color(0xFF6750A4),secondary=Color(0xFF625B71),tertiary=Color(0xFF7D5260))
private val Dark=darkColorScheme(primary=Color(0xFFD0BCFF),secondary=Color(0xFFCCC2DC),tertiary=Color(0xFFEFB8C8))
@Composable fun AlldocsTheme(darkTheme:Boolean=false,content:@Composable()->Unit){MaterialTheme(colorScheme=if(darkTheme)Dark else Light,typography=Typography(),content=content)}
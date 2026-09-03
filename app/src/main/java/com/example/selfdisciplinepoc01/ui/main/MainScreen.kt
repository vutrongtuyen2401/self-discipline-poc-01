package com.example.selfdisciplinepoc01.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun MainScreen(items: List<String>) {
    Column {
        items.forEach { item ->
            Text(text = "Hello $item!")
        }
    }
}

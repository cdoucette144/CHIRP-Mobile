package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun ChoiceDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label,style=MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {
                Text(selected,modifier=Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                options.forEach { option ->
                    DropdownMenuItem(text={Text(option)},onClick={onSelect(option);expanded=false})
                }
            }
        }
    }
}

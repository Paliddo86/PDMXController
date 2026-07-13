package com.paliddo.pdmxcontroller.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CreateOrCopyShowDialog(
    availableShows: List<String>,
    onConfirm: (name: String, sourceToCopy: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var showName by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf<String?>(null) }
    var isCopyMode by remember { mutableStateOf(false) }

    val colorSurface = Color(0xFF161A23)
    val colorTextPrimary = Color(0xFFF8FAFC)
    val colorCyan = Color(0xFF00B4D8)
    val colorSurfaceAccent = Color(0xFF232834)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorSurface,
        title = {
            Text(
                if (isCopyMode) "COPIA SHOWFILE" else "NUOVO SHOWFILE",
                color = colorCyan,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = showName,
                    onValueChange = { showName = it },
                    label = { Text("Nome Show") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = colorTextPrimary),
                    singleLine = true
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isCopyMode,
                        onCheckedChange = { isCopyMode = it },
                        colors = CheckboxDefaults.colors(checkedColor = colorCyan)
                    )
                    Text("Copia da uno show esistente", color = colorTextPrimary, fontSize = 14.sp)
                }

                if (isCopyMode) {
                    Text("Seleziona sorgente:", color = colorTextPrimary.copy(0.6f), fontSize = 12.sp)
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorCyan)
                        ) {
                            Text(selectedSource ?: "Scegli Show...")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(colorSurface)
                        ) {
                            availableShows.forEach { show ->
                                DropdownMenuItem(
                                    text = { Text(show, color = colorTextPrimary) },
                                    onClick = {
                                        selectedSource = show
                                        expanded = false
                                        if (showName.isEmpty()) showName = "${show}_Copy"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (showName.isNotEmpty()) onConfirm(showName, if (isCopyMode) selectedSource else null) },
                enabled = showName.isNotEmpty() && (!isCopyMode || selectedSource != null),
                colors = ButtonDefaults.buttonColors(containerColor = colorCyan)
            ) {
                Text("CREA", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ANNULLA", color = colorTextPrimary)
            }
        }
    )
}

@Composable
fun CreateOrCopySceneDialog(
    existingScenes: List<String>,
    onConfirm: (name: String, sourceIndex: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var sceneName by remember { mutableStateOf("") }
    var selectedSourceIndex by remember { mutableStateOf<Int?>(null) }
    var isCopyMode by remember { mutableStateOf(false) }

    val colorSurface = Color(0xFF161A23)
    val colorTextPrimary = Color(0xFFF8FAFC)
    val colorPurple = Color(0xFF9D4EDD)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorSurface,
        title = {
            Text(
                if (isCopyMode) "COPIA SCENA" else "NUOVA SCENA",
                color = colorPurple,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = sceneName,
                    onValueChange = { sceneName = it },
                    label = { Text("Nome Scena") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = colorTextPrimary),
                    singleLine = true
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isCopyMode,
                        onCheckedChange = { isCopyMode = it },
                        colors = CheckboxDefaults.colors(checkedColor = colorPurple)
                    )
                    Text("Copia da una scena esistente", color = colorTextPrimary, fontSize = 14.sp)
                }

                if (isCopyMode) {
                    Text("Seleziona sorgente:", color = colorTextPrimary.copy(0.6f), fontSize = 12.sp)
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorPurple)
                        ) {
                            val label = selectedSourceIndex?.let { existingScenes.getOrNull(it) } ?: "Scegli Scena..."
                            Text(label)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(colorSurface)
                        ) {
                            existingScenes.forEachIndexed { index, scene ->
                                DropdownMenuItem(
                                    text = { Text(scene, color = colorTextPrimary) },
                                    onClick = {
                                        selectedSourceIndex = index
                                        expanded = false
                                        if (sceneName.isEmpty()) sceneName = "${scene}_Copy"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (sceneName.isNotEmpty()) onConfirm(sceneName, if (isCopyMode) selectedSourceIndex else null) },
                enabled = sceneName.isNotEmpty() && (!isCopyMode || selectedSourceIndex != null),
                colors = ButtonDefaults.buttonColors(containerColor = colorPurple)
            ) {
                Text("CREA", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ANNULLA", color = colorTextPrimary)
            }
        }
    )
}

package com.paliddo.pdmxcontroller.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paliddo.pdmxcontroller.data.model.FixtureProfile

@Composable
fun ProfileSelectorDialog(
    allProfiles: List<FixtureProfile>,
    selectedProfileId: String,
    onProfileSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val colorSurface = Color(0xFF161A23)
    val colorSurfaceAccent = Color(0xFF232834)
    val colorTextPrimary = Color(0xFFF8FAFC)
    val colorCyan = Color(0xFF00B4D8)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorSurface,
        title = {
            Column {
                Text("LIBRERIA FIXTURE", color = colorCyan, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cerca modello o marca...", color = colorTextPrimary.copy(0.4f), fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = colorTextPrimary, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = colorSurfaceAccent,
                        focusedBorderColor = colorCyan,
                        cursorColor = colorCyan
                    ),
                    singleLine = true
                )
            }
        },
        text = {
            val filteredProfiles = allProfiles.filter {
                it.modelName.contains(searchQuery, ignoreCase = true) ||
                it.manufacturer.contains(searchQuery, ignoreCase = true)
            }
            Box(modifier = Modifier.height(300.dp).fillMaxWidth()) {
                if (filteredProfiles.isEmpty()) {
                    Text("Nessun profilo trovato", color = colorTextPrimary.copy(0.5f), modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filteredProfiles) { p ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedProfileId == p.id) colorCyan.copy(0.2f) else colorSurfaceAccent
                                ),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onProfileSelected(p.id)
                                },
                                border = if (selectedProfileId == p.id) BorderStroke(1.dp, colorCyan) else null
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(p.modelName, color = colorTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(p.manufacturer, color = colorCyan, fontSize = 11.sp)
                                    }
                                    Text("${p.channelCount} CH", color = colorTextPrimary.copy(0.6f), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CHIUDI", color = colorTextPrimary)
            }
        }
    )
}

package com.paliddo.pdmxcontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paliddo.pdmxcontroller.ui.screens.FaderScreen
import com.paliddo.pdmxcontroller.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Inizializza il ViewModel legato al contesto dell'applicazione
            val viewModel: MainViewModel = viewModel()
            // Carica la schermata dei fader passandogli il ViewModel sincronizzato
            FaderScreen(viewModel = viewModel)
        }
    }
}
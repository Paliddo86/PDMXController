package com.paliddo.pdmxcontroller

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplashScreenContent(onSplashFinished = {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            })
        }
    }
}

@Composable
fun SplashScreenContent(onSplashFinished: () -> Unit) {
    val colorBackground = Color(0xFF0B0E14)
    val colorPurple = Color(0xFF9D4EDD)
    val colorCyan = Color(0xFF00B4D8)
    val colorTextPrimary = Color(0xFFF8FAFC)
    val colorTextSecondary = Color(0xFFF8FAFC).copy(alpha = 0.6f)

    // Animazione fade-in
    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
        delay(1800)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alphaAnim.value)
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icona
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "PDMX Controller Logo",
                modifier = Modifier.size(180.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Titolo principale
            Text(
                text = "P-DMX",
                color = colorCyan,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                letterSpacing = 4.sp
            )

            Text(
                text = "CONTROLLER",
                color = colorPurple,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 8.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Divisore decorativo
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(2.dp)
                    .background(colorCyan.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Crediti
            Text(
                text = "Creato da Paliddo",
                color = colorTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )

            Text(
                text = "(Luigi Pallante)",
                color = colorTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}
package com.example.proyectwin.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proyectwin.R
import com.example.proyectwin.ui.theme.senaColors
import kotlinx.coroutines.launch

/**
 * Apertura breve con la identidad de ProyecTwin: aparece el logo oficial con
 * una entrada suave (fade + escala) mientras la sesión se hidrata localmente.
 * Sin sonido ni pantallas de carga artificiales; MainActivity la retira en
 * cuanto la sesión está resuelta (con un mínimo corto para que la animación se
 * aprecie). El mismo fondo verde del tema evita parpadeos con el splash del
 * sistema.
 */
@Composable
fun SplashScreen() {
    val alpha = remember { Animatable(0f) }
    val escala = remember { Animatable(0.88f) }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(durationMillis = 550, easing = FastOutSlowInEasing)) }
        escala.animateTo(1f, tween(durationMillis = 750, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(senaColors().darkGreen, senaColors().header),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(32.dp)
                .graphicsLayer {
                    this.alpha = alpha.value
                    scaleX = escala.value
                    scaleY = escala.value
                },
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_proyectwin),
                contentDescription = "Logo de ProyecTwin",
                modifier = Modifier.width(230.dp).aspectRatio(656f / 380f),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "ProyecTwin",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "GESTIÓN DE PROPUESTAS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = senaColors().accent,
                letterSpacing = 3.sp,
                modifier = Modifier.alpha(0.9f),
            )
        }
    }
}

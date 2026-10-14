package com.example.proyectwin.ui.screens.auth

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AuthActionState
import com.example.proyectwin.ui.viewmodel.AuthViewModel

/**
 * Recuperación por correo (`POST /auth/forgot-password`). La respuesta es
 * genérica; en modo local el backend agrega `reset_url`, que se usa para
 * prellenar el token en la pantalla de restablecimiento.
 */
@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    onCodeSent: (correo: String, resetUrl: String?) -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    var email by remember { mutableStateOf("") }
    var errorLocal by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    val forgotState by authViewModel.forgotState.collectAsState()
    val forgotMessage by authViewModel.forgotMessage.collectAsState()

    LaunchedEffect(forgotState) {
        if (forgotState is AuthActionState.Success) {
            val url = forgotMessage?.takeIf { it.startsWith("http") }
            authViewModel.resetForgotState()
            onCodeSent(email.trim().lowercase(), url)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(senaColors().background)
            .verticalScroll(scrollState)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Brush.verticalGradient(colors = listOf(senaColors().header, senaColors().green)))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.LockReset, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Recuperar Acceso", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }
            IconButton(
                onClick = onBackToLogin,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .offset(y = (-32).dp)
        ) {
            SenaCard(elevation = 8.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        "Ingresa tu correo institucional y te enviaremos las instrucciones para restablecer tu contraseña.",
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    SenaTextField(
                        value = email,
                        onValueChange = { email = it; errorLocal = null; authViewModel.resetForgotState() },
                        label = "Correo Electrónico",
                        placeholder = "tu@correo.com",
                        leadingIcon = Icons.Default.Email,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                    )

                    val mensajeError = errorLocal ?: (forgotState as? AuthActionState.Error)?.message
                    if (mensajeError != null) {
                        SenaAlertBanner(
                            title = "Error",
                            message = mensajeError,
                            icon = Icons.Default.Error,
                            color = senaColors().danger
                        )
                    }

                    SenaButton(
                        text = "Enviar Enlace",
                        onClick = {
                            val correo = email.trim().lowercase()
                            errorLocal = when {
                                correo.isBlank() -> "Ingresa tu correo institucional."
                                !Patterns.EMAIL_ADDRESS.matcher(correo).matches() -> "Ingresa un correo válido."
                                else -> null
                            }
                            if (errorLocal == null) authViewModel.forgotPassword(correo)
                        },
                        isLoading = forgotState is AuthActionState.Loading,
                        icon = Icons.AutoMirrored.Filled.Send
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("¿Recordaste tu contraseña? ", style = MaterialTheme.typography.labelSmall, color = senaColors().textSecondary)
                TextButton(onClick = onBackToLogin) {
                    Text("Inicia sesión", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = senaColors().green)
                }
            }
        }
    }
}

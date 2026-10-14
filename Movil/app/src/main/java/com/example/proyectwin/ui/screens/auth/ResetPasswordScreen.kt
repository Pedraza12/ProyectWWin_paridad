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
 * Restablecimiento con el token del correo (`POST /auth/reset-password`).
 * El token viaja en el enlace del correo; aquí se pega junto al correo.
 */
@Composable
fun ResetPasswordScreen(
    correoInicial: String = "",
    tokenInicial: String = "",
    onBackToLogin: () -> Unit,
    onResetSuccess: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    var correo by remember { mutableStateOf(correoInicial) }
    var token by remember { mutableStateOf(tokenInicial) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorLocal by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    val resetState by authViewModel.resetState.collectAsState()

    LaunchedEffect(resetState) {
        if (resetState is AuthActionState.Success) {
            authViewModel.resetResetState()
            onResetSuccess()
        }
    }

    val fieldErrors = (resetState as? AuthActionState.Error)?.fieldErrors.orEmpty()

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
                Text("Nueva Contraseña", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
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
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(
                        "Pega el código que llegó a tu correo y define tu nueva contraseña.",
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    SenaTextField(
                        value = correo,
                        onValueChange = { correo = it; errorLocal = null; authViewModel.resetResetState() },
                        label = "Correo Electrónico",
                        placeholder = "tu@correo.com",
                        leadingIcon = Icons.Default.Email,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                        isError = fieldErrors.containsKey("correo"),
                        supportingText = fieldErrors["correo"]?.takeIf { errorLocal == null }
                    )

                    SenaTextField(
                        value = token,
                        onValueChange = { token = it; errorLocal = null; authViewModel.resetResetState() },
                        label = "Código de Recuperación",
                        placeholder = "Pega aquí el código del correo",
                        leadingIcon = Icons.Default.VpnKey,
                        isError = fieldErrors.containsKey("token"),
                        supportingText = fieldErrors["token"]?.takeIf { errorLocal == null }
                    )

                    SenaTextField(
                        value = password,
                        onValueChange = { password = it; errorLocal = null; authViewModel.resetResetState() },
                        label = "Contraseña Nueva",
                        placeholder = "Mínimo 6 caracteres",
                        isPassword = true,
                        leadingIcon = Icons.Default.Lock,
                        isError = fieldErrors.containsKey("password"),
                        supportingText = fieldErrors["password"]?.takeIf { errorLocal == null }
                    )

                    SenaTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorLocal = null; authViewModel.resetResetState() },
                        label = "Confirmar Contraseña",
                        placeholder = "Escribe de nuevo tu clave",
                        isPassword = true,
                        leadingIcon = Icons.Default.CheckCircle
                    )

                    val mensajeError = errorLocal ?: (resetState as? AuthActionState.Error)
                        ?.takeIf { fieldErrors.isEmpty() }
                        ?.message
                    if (mensajeError != null) {
                        SenaAlertBanner(
                            title = "Error",
                            message = mensajeError,
                            icon = Icons.Default.Error,
                            color = senaColors().danger
                        )
                    }

                    SenaButton(
                        text = "Actualizar Contraseña",
                        onClick = {
                            val correoLimpio = correo.trim().lowercase()
                            errorLocal = when {
                                !Patterns.EMAIL_ADDRESS.matcher(correoLimpio).matches() -> "Ingresa un correo válido."
                                token.isBlank() -> "Ingresa el código que llegó a tu correo."
                                password.length < 6 -> "La contraseña debe tener al menos 6 caracteres."
                                password != confirmPassword -> "La confirmación no coincide con la nueva contraseña."
                                else -> null
                            }
                            if (errorLocal == null) {
                                authViewModel.resetPassword(correoLimpio, token.trim(), password, confirmPassword)
                            }
                        },
                        isLoading = resetState is AuthActionState.Loading,
                        icon = Icons.Default.Save
                    )
                }
            }
        }
    }
}

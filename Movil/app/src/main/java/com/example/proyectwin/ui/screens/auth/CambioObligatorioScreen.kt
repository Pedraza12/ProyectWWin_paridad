package com.example.proyectwin.ui.screens.auth

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AuthActionState
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel

/**
 * Cambio obligatorio de la contraseña temporal (`must_change_password`).
 * Bloquea el acceso al resto de la app hasta completarlo: el backend responde
 * 403 en los demás endpoints y la navegación vive fuera de los grafos de rol.
 */
@Composable
fun CambioObligatorioScreen(
    authViewModel: AuthViewModel = hiltViewModel(),
    onCompleted: (String) -> Unit,
) {
    val uiState by authViewModel.uiState.collectAsState()
    val passwordChange by authViewModel.passwordChange.collectAsState()

    LaunchedEffect(passwordChange) {
        if (passwordChange is AuthActionState.Success) {
            authViewModel.resetPasswordChangeState()
            val rol = (uiState as? AuthUiState.LoggedIn)?.user?.role ?: "aprendiz"
            onCompleted(rol)
        }
    }

    PasswordChangeForm(
        authViewModel = authViewModel,
        titulo = "Cambio Obligatorio",
        descripcion = "Tu cuenta usa una contraseña temporal. Define una contraseña personal para continuar.",
        advertencia = "Debes cambiar tu contraseña temporal antes de continuar.",
        textoBoton = "GUARDAR Y CONTINUAR",
        onBack = null,
    )
}

/** Cambio normal de contraseña desde el perfil (requiere la actual). */
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val passwordChange by authViewModel.passwordChange.collectAsState()

    LaunchedEffect(passwordChange) {
        if (passwordChange is AuthActionState.Success) {
            authViewModel.resetPasswordChangeState()
            onBack()
        }
    }

    PasswordChangeForm(
        authViewModel = authViewModel,
        titulo = "Cambiar Contraseña",
        descripcion = "Ingresa tu contraseña actual y define una nueva.",
        advertencia = null,
        textoBoton = "ACTUALIZAR CONTRASEÑA",
        onBack = onBack,
    )
}

@Composable
private fun PasswordChangeForm(
    authViewModel: AuthViewModel,
    titulo: String,
    descripcion: String,
    advertencia: String?,
    textoBoton: String,
    onBack: (() -> Unit)?,
) {
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var errorLocal by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val action by authViewModel.passwordChange.collectAsState()

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
                Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            }
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
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
                        descripcion,
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (advertencia != null) {
                        SenaAlertBanner(
                            title = "Atención",
                            message = advertencia,
                            icon = Icons.Default.Warning,
                            color = senaColors().warning
                        )
                    }

                    SenaTextField(
                        value = actual,
                        onValueChange = { actual = it; errorLocal = null; authViewModel.resetPasswordChangeState() },
                        label = "Contraseña Actual",
                        placeholder = "••••••••",
                        isPassword = true,
                        leadingIcon = Icons.Default.Lock,
                        isError = (action as? AuthActionState.Error)?.fieldErrors?.containsKey("password_actual") == true
                    )

                    SenaTextField(
                        value = nueva,
                        onValueChange = { nueva = it; errorLocal = null; authViewModel.resetPasswordChangeState() },
                        label = "Nueva Contraseña",
                        placeholder = "Mínimo 6 caracteres",
                        isPassword = true,
                        leadingIcon = Icons.Default.LockOpen,
                        isError = (action as? AuthActionState.Error)?.fieldErrors?.containsKey("password") == true
                    )

                    SenaTextField(
                        value = confirmar,
                        onValueChange = { confirmar = it; errorLocal = null; authViewModel.resetPasswordChangeState() },
                        label = "Confirmar Nueva Contraseña",
                        placeholder = "Repite la nueva clave",
                        isPassword = true,
                        leadingIcon = Icons.Default.CheckCircle
                    )

                    val mensajeError = errorLocal ?: (action as? AuthActionState.Error)?.message
                    if (mensajeError != null) {
                        SenaAlertBanner(
                            title = "Error",
                            message = mensajeError,
                            icon = Icons.Default.Error,
                            color = senaColors().danger
                        )
                    }

                    SenaButton(
                        text = textoBoton,
                        onClick = {
                            errorLocal = when {
                                actual.isBlank() -> "Ingresa tu contraseña actual."
                                nueva.length < 6 -> "La nueva contraseña debe tener al menos 6 caracteres."
                                nueva != confirmar -> "La confirmación no coincide con la nueva contraseña."
                                else -> null
                            }
                            if (errorLocal == null) {
                                authViewModel.changePassword(actual, nueva, confirmar)
                            }
                        },
                        isLoading = action is AuthActionState.Loading,
                        icon = Icons.Default.Save
                    )
                }
            }
        }
    }
}

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.ProfileViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ChangeEmailScreen(
    onBack: () -> Unit,
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val isSaving by profileViewModel.isSaving.collectAsState()
    val saveSuccess by profileViewModel.saveSuccess.collectAsState()
    val saveError by profileViewModel.saveError.collectAsState()

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            profileViewModel.clearSaveSuccess()
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(senaColors().background)
            .verticalScroll(scrollState)
    ) {
        // Decorative Header
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
                        Icon(Icons.Default.Email, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Cambiar Correo Electrónico", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
        }

        // Card Section
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .offset(y = (-32).dp)
        ) {
            SenaCard(elevation = 8.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        "Ingresa tu nuevo correo electrónico y tu contraseña actual para verificar los cambios.",
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    SenaTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Nuevo Correo Electrónico",
                        placeholder = "ejemplo@sena.edu.co",
                        leadingIcon = Icons.Default.Email,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                    )

                    SenaTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Contraseña Actual",
                        placeholder = "••••••••",
                        isPassword = true,
                        leadingIcon = Icons.Default.Lock
                    )

                    if (saveError != null) {
                        SenaAlertBanner(
                            title = "Error",
                            message = saveError!!,
                            icon = Icons.Default.Error,
                            color = senaColors().danger
                        )
                    }

                    SenaButton(
                        text = "Cambiar Correo Electrónico",
                        onClick = {
                            if (email.isNotBlank() && password.isNotBlank()) {
                                profileViewModel.changeEmail(email, password)
                            }
                        },
                        isLoading = isSaving,
                        icon = Icons.Default.Save
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChangeEmailScreenPreview() {
    ProyecTwinTheme {
        ChangeEmailScreen(onBack = {})
    }
}

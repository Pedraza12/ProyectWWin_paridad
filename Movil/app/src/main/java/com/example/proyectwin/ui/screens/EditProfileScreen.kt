package com.example.proyectwin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import com.example.proyectwin.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val authState by authViewModel.uiState.collectAsState()
    val user = (authState as? AuthUiState.LoggedIn)?.user
    val saveSuccess by profileViewModel.saveSuccess.collectAsState()
    val isSaving by profileViewModel.isSaving.collectAsState()
    val saveError by profileViewModel.saveError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember(user) { mutableStateOf(user?.name?.split(" ")?.firstOrNull() ?: "") }
    var lastName by remember(user) { mutableStateOf(user?.name?.split(" ")?.drop(1)?.joinToString(" ") ?: "") }
    var email by remember(user) { mutableStateOf(user?.email ?: "") }


    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            profileViewModel.clearSaveSuccess()
            snackbarHostState.showSnackbar("Perfil actualizado correctamente")
        }
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "ProyecTwin",
                onBack = onBack,
                showProfile = true,
                showNotifications = true
            )
        },
        containerColor = senaColors().background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            SenaBottomBar {
                SenaButton(
                    text = "Cancelar", 
                    onClick = onBack, 
                    isPrimary = false, 
                    modifier = Modifier.weight(1f)
                )
                SenaButton(
                    text = "Guardar Cambios", 
                    onClick = {
                        profileViewModel.updateProfile("$name $lastName".trim())
                    }, 
                    isLoading = isSaving,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Save
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            SenaPageHeader(
                title = "Editar Perfil",
                subtitle = "Actualiza tu información personal y de contacto en el sistema.",
                icon = Icons.Default.Edit
            )

            SenaSectionHeader(title = "Información Básica")
            SenaCard(elevation = 1.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SenaTextField(
                            value = name, 
                            onValueChange = { name = it }, 
                            label = "Nombre *",
                            modifier = Modifier.weight(1f)
                        )
                        SenaTextField(
                            value = lastName, 
                            onValueChange = { lastName = it }, 
                            label = "Apellido *",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    SenaTextField(
                        value = email, 
                        onValueChange = { email = it }, 
                        label = "Correo Electrónico *",
                        leadingIcon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email,
                        enabled = false
                    )
                    

                }
            }

            Text(
                text = "Los cambios se reflejarán en tiempo real.",
                style = MaterialTheme.typography.bodySmall,
                color = senaColors().textMuted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SenaSectionHeader(title = "Seguridad")
            SenaCard(elevation = 1.dp) {
                SenaSettingsItem(
                    icon = Icons.Default.Lock, 
                    title = "Cambiar Contraseña", 
                    description = "Se te redirigirá a la pantalla de cambio de clave.",
                    onClick = { onNavigate(AppNavigation.CHANGE_PASSWORD) }
                )
                SenaSettingsItem(
                    icon = Icons.Default.Email,
                    title = "Cambiar Correo Electrónico",
                    description = "Se te redirigirá a la pantalla de cambio de correo.",
                    onClick = { onNavigate(AppNavigation.CHANGE_EMAIL) }
                )
            }

            SenaAlertBanner(
                title = "Privacidad de Datos",
                message = "Tu información solo es visible para instructores y personal administrativo autorizado.",
                icon = Icons.Default.Shield,
                color = senaColors().info
            )

            if (saveError != null) {
                SenaAlertBanner(
                    title = "Error",
                    message = saveError!!,
                    icon = Icons.Default.Error,
                    color = senaColors().danger
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EditProfileScreenPreview() {
    ProyecTwinTheme {
        EditProfileScreen(onBack = {}, onNavigate = {})
    }
}

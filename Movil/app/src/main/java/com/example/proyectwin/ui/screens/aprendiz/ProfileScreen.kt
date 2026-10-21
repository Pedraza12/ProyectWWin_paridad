package com.example.proyectwin.ui.screens.aprendiz

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.local.ImagenUtils
import com.example.proyectwin.data.model.ProjectStatus
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import com.example.proyectwin.ui.viewmodel.DashboardUiState
import com.example.proyectwin.ui.viewmodel.DashboardViewModel
import com.example.proyectwin.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val authState by authViewModel.uiState.collectAsState()
    val profileState by profileViewModel.uiState.collectAsState()
    val user = (authState as? AuthUiState.LoggedIn)?.user
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveSuccess by profileViewModel.saveSuccess.collectAsState()
    val saveError by profileViewModel.saveError.collectAsState()

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            profileViewModel.clearSaveSuccess()
            snackbarHostState.showSnackbar("Perfil actualizado correctamente")
        }
    }
    LaunchedEffect(saveError) {
        saveError?.let {
            snackbarHostState.showSnackbar(it)
            profileViewModel.clearError()
        }
    }

    var isEditing by remember { mutableStateOf(false) }
    var editName by remember(user) { mutableStateOf(user?.name?.split(" ")?.firstOrNull() ?: "") }
    var editLastName by remember(user) { mutableStateOf(user?.name?.split(" ")?.getOrNull(1) ?: "") }
    var editEmail by remember(user) { mutableStateOf(user?.email ?: "") }

    val dashState by dashboardViewModel.uiState.collectAsState()
    LaunchedEffect(user?.id) {
        if (user != null) dashboardViewModel.loadStudentDashboard(user.id)
    }
    val projectCount = (dashState as? DashboardUiState.Success)?.projects
        ?.count { it.studentId == user?.id || it.equipo.any { miembro -> miembro.id == user?.id } }
        ?: 0

    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val dataUrl = ImagenUtils.uriAFotoDataUrl(context, it)
                if (dataUrl != null) profileViewModel.updateFoto(dataUrl)
            }
        }
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "Mi Perfil",
                showProfile = false,
                showNotifications = true,
                onBack = onBack,
                onNavigateToAlerts = { onNavigate(AppNavigation.APRENDIZ_ALERTS) }
            )
        },
        containerColor = senaColors().background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Brush.verticalGradient(colors = listOf(senaColors().header, senaColors().green)))
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .offset(x = 280.dp, y = 80.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                )
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .offset(y = (-70).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SenaAvatar(
                    fotoBase64 = user?.fotoPerfil,
                    nombre = user?.name ?: "Usuario",
                    modifier = Modifier.size(100.dp),
                    onClick = { photoPickerLauncher.launch("image/*") },
                    showChangeIndicator = true
                )

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                    Text("Cambiar foto", style = MaterialTheme.typography.labelSmall, color = senaColors().green)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = user?.name ?: "Usuario",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = senaColors().text
                )
                Text(
                    text = "${user?.roleDisplayName ?: "Aprendiz"} — ADSO",
                    style = MaterialTheme.typography.bodyMedium,
                    color = senaColors().textLight
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCardAprendiz(icon = Icons.Default.Folder, value = "$projectCount", label = "Proyectos", modifier = Modifier.weight(1f))
                    Surface(
                        modifier = Modifier.weight(0.8f).height(80.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = senaColors().success.copy(alpha = 0.1f)
                    ) {
                        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Activo", fontWeight = FontWeight.Bold, color = senaColors().success, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                SenaSectionHeader(title = "Datos del Aprendiz")
                SenaCard(elevation = 1.dp) {
                    if (isEditing) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SenaTextField(value = editName, onValueChange = { editName = it }, label = "Nombre", modifier = Modifier.weight(1f))
                                SenaTextField(value = editLastName, onValueChange = { editLastName = it }, label = "Apellido", modifier = Modifier.weight(1f))
                            }
                            SenaTextField(value = editEmail, onValueChange = { editEmail = it }, label = "Correo Institucional", leadingIcon = Icons.Default.Email, enabled = false)

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SenaButton(text = "Cerrar", onClick = { isEditing = false }, isPrimary = false, modifier = Modifier.weight(1f))
                                SenaButton(text = "Guardar", onClick = {
                                    profileViewModel.updateProfileNombres(editName.trim(), editLastName.trim())
                                    isEditing = false
                                }, modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        Column {
                            SenaSettingsItem(icon = Icons.Default.Person, title = "Nombre Completo", description = user?.name ?: "-")
                            HorizontalDivider(color = senaColors().borderSoft, modifier = Modifier.padding(start = 56.dp))
                            SenaSettingsItem(icon = Icons.Default.AccountCircle, title = "Usuario", description = user?.username?.ifBlank { "No registrado" } ?: "No registrado")
                            HorizontalDivider(color = senaColors().borderSoft, modifier = Modifier.padding(start = 56.dp))
                            SenaSettingsItem(icon = Icons.Default.Email, title = "Correo Electrónico", description = user?.email ?: "-")
                            HorizontalDivider(color = senaColors().borderSoft, modifier = Modifier.padding(start = 56.dp))
                            SenaSettingsItem(icon = Icons.Default.Badge, title = "Documento de Identidad", description = user?.documentoIdentidad ?: "-")

                            Spacer(modifier = Modifier.height(16.dp))
                            SenaButton(text = "Editar Perfil", onClick = {
                                editName = user?.name?.split(" ")?.firstOrNull() ?: ""
                                editLastName = user?.name?.split(" ")?.getOrNull(1) ?: ""
                                editEmail = user?.email ?: ""
                                isEditing = true
                            }, isPrimary = false, icon = Icons.Default.Edit, modifier = Modifier.height(44.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SenaSectionHeader(title = "Privacidad y Acceso")
                SenaCard(elevation = 1.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SenaAlertBanner(
                            title = "Seguridad",
                            message = "Se recomienda actualizar tu clave periódicamente.",
                            icon = Icons.Default.Lock,
                            color = senaColors().info
                        )

                        SenaSettingsItem(
                            icon = Icons.Default.VpnKey,
                            title = "Cambiar Contraseña",
                            description = "Gestión de credenciales",
                            onClick = { onNavigate(AppNavigation.CHANGE_PASSWORD) }
                        )
                        SenaSettingsItem(
                            icon = Icons.Default.Email,
                            title = "Cambiar Correo Electrónico",
                            description = "Actualiza tu correo de contacto",
                            onClick = { onNavigate(AppNavigation.CHANGE_EMAIL) }
                        )
                        SenaSettingsItem(
                            icon = Icons.Default.SystemUpdate,
                            title = "Actualizaciones",
                            description = "Versión instalada y nuevas versiones de ProyecTwin",
                            onClick = { onNavigate(AppNavigation.APP_UPDATES) }
                        )
                        SenaSettingsItem(
                            icon = Icons.Default.BugReport,
                            title = "Reportar Falla",
                            description = "Informa un problema a los administradores",
                            onClick = { onNavigate(AppNavigation.REPORT_ISSUE) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                SenaButton(
                    text = "Cerrar Sesión",
                    onClick = {
                        authViewModel.logout()
                    },
                    icon = Icons.AutoMirrored.Filled.Logout,
                    containerColor = senaColors().danger,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun MetricCardAprendiz(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = senaColors().backgroundElevated,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, senaColors().borderSoft)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = senaColors().green, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = senaColors().text)
            Text(label, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    ProyecTwinTheme {
        ProfileScreen(onBack = {}, onNavigate = {})
    }
}

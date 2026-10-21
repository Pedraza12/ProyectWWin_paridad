package com.example.proyectwin.ui.screens.admin

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
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AdminUiState
import com.example.proyectwin.ui.viewmodel.AdminViewModel
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import com.example.proyectwin.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@Composable
fun AdminProfileScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    adminViewModel: AdminViewModel = hiltViewModel(),
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val authState by authViewModel.uiState.collectAsState()
    val user = (authState as? AuthUiState.LoggedIn)?.user
    val adminState by adminViewModel.uiState.collectAsState()
    val adminData = adminState as? AdminUiState.Success

    val saveSuccess by profileViewModel.saveSuccess.collectAsState()
    val saveError by profileViewModel.saveError.collectAsState()
    var dialogoEditar by remember { mutableStateOf(false) }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SenaTopBar(
                title = "Perfil Admin",
                onBack = onBack,
                showProfile = false,
                showNotifications = false
            )
        },
        containerColor = senaColors().background,
        bottomBar = bottomBar
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // --- HEADER ADMIN (DEGRADADO PROFUNDO) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(senaColors().darkGreen, senaColors().header)
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .offset(x = 100.dp, y = (-40).dp)
                        .background(Color.White.copy(alpha = 0.03f), CircleShape)
                )
            }

            // --- PERFIL CARD (FLOTANTE) ---
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .offset(y = (-80).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SenaAvatar(
                    fotoBase64 = user?.fotoPerfil,
                    nombre = user?.name ?: "Admin",
                    modifier = Modifier.size(110.dp),
                    onClick = { photoPickerLauncher.launch("image/*") },
                    showChangeIndicator = true
                )

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                    Text("Cambiar foto", style = MaterialTheme.typography.labelSmall, color = senaColors().green)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user?.name ?: "Admin Sistema",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = senaColors().text
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { dialogoEditar = true }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar perfil",
                            tint = senaColors().green,
                        )
                    }
                }
                Text(
                    text = user?.roleDisplayName ?: "Control Maestro de Plataforma",
                    style = MaterialTheme.typography.bodyMedium,
                    color = senaColors().textLight
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Métricas Admin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCardAdmin(icon = Icons.Default.People, value = "${adminData?.users?.size ?: 0}", label = "Usuarios", modifier = Modifier.weight(1f))
                    MetricCardAdmin(icon = Icons.Default.BugReport, value = "${adminData?.bugReports?.size ?: 0}", label = "Reportes", modifier = Modifier.weight(1f))
                    MetricCardAdmin(icon = Icons.Default.Compare, value = "${adminData?.similarities?.size ?: 0}", label = "Similitudes", modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(32.dp))

                // --- SECCIONES ---
                SenaSectionHeader(title = "Gestión Maestra")
                SenaCard(elevation = 1.dp) {
                    Column {
                        SenaSettingsItem(
                            icon = Icons.Default.Badge,
                            title = "Documento",
                            description = user?.documentoIdentidad ?: "No registrado"
                        )
                        HorizontalDivider(color = senaColors().borderSoft, modifier = Modifier.padding(start = 56.dp))
                        SenaSettingsItem(
                            icon = Icons.Default.Email,
                            title = "Correo Institucional",
                            description = user?.email ?: "No registrado"
                        )
                        HorizontalDivider(color = senaColors().borderSoft, modifier = Modifier.padding(start = 56.dp))
                        SenaSettingsItem(
                            icon = Icons.Default.AccountCircle,
                            title = "Usuario",
                            description = user?.username?.ifBlank { "No registrado" } ?: "No registrado"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SenaSectionHeader(title = "Seguridad de la cuenta")
                SenaCard(elevation = 1.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SenaAlertBanner(
                            title = "Cuenta administrativa",
                            message = "Usuario: ${user?.username ?: user?.email ?: "—"}. Las sesiones se gestionan por token en el servidor.",
                            icon = Icons.Default.Shield,
                            color = senaColors().info
                        )

                        SenaSettingsItem(
                            icon = Icons.Default.Lock,
                            title = "Cambiar Contraseña",
                            description = "Actualiza tu clave con la actual",
                            onClick = { onNavigate(AppNavigation.CHANGE_PASSWORD) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SenaSectionHeader(title = "Configuración Global")
                SenaCard(elevation = 1.dp) {
                    Column {
                        SenaSettingsItem(
                            icon = Icons.Default.Tune,
                            title = "Motor de Similitud",
                            description = "Umbral, ventana del corpus y recálculo",
                            onClick = { onNavigate(AppNavigation.ADMIN_MOTOR) }
                        )
                        HorizontalDivider(color = senaColors().borderSoft, modifier = Modifier.padding(start = 56.dp))
                        SenaSettingsItem(
                            icon = Icons.Default.History,
                            title = "Bitácora",
                            description = "Auditoría de acciones sensibles",
                            onClick = { onNavigate(AppNavigation.ADMIN_BITACORA) }
                        )
                        HorizontalDivider(color = senaColors().borderSoft, modifier = Modifier.padding(start = 56.dp))
                        SenaSettingsItem(
                            icon = Icons.Default.SystemUpdate,
                            title = "Actualizaciones",
                            description = "Versión instalada y nuevas versiones de ProyecTwin",
                            onClick = { onNavigate(AppNavigation.APP_UPDATES) }
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

    if (dialogoEditar) {
        EditarPerfilAdminDialog(
            user = user,
            onDismiss = { dialogoEditar = false },
            onGuardar = { nombre, apellido ->
                dialogoEditar = false
                profileViewModel.updateProfileNombres(nombre, apellido)
            },
        )
    }
}

/**
 * Edición de los datos que la API permite: nombre/apellido. Usuario, documento
 * y correo quedan de solo lectura (el correo propio se cambia con su flujo
 * dedicado que exige la contraseña actual).
 */
@Composable
private fun EditarPerfilAdminDialog(
    user: com.example.proyectwin.data.model.GeneralUser?,
    onDismiss: () -> Unit,
    onGuardar: (nombre: String, apellido: String) -> Unit,
) {
    var nombre by remember(user) { mutableStateOf(user?.nombre ?: user?.name?.substringBefore(' ') ?: "") }
    var apellido by remember(user) { mutableStateOf(user?.apellido ?: user?.name?.substringAfter(' ', "") ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = senaColors().green) },
        title = { Text("Editar perfil", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                error?.let { Text(it, color = senaColors().danger, style = MaterialTheme.typography.labelSmall) }
                SenaTextField(value = nombre, onValueChange = { nombre = it }, label = "Nombre")
                SenaTextField(value = apellido, onValueChange = { apellido = it }, label = "Apellido")
                DatoSoloLectura("Usuario", user?.username?.ifBlank { "—" } ?: "—")
                DatoSoloLectura("Documento", user?.documentoIdentidad ?: "—")
                DatoSoloLectura("Correo institucional", user?.email ?: "—")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nombre.isBlank() || apellido.isBlank()) {
                    error = "Nombre y apellido son obligatorios."
                } else {
                    onGuardar(nombre.trim(), apellido.trim())
                }
            }) { Text("Guardar", color = senaColors().green, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun DatoSoloLectura(etiqueta: String, valor: String) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
        Text(valor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = senaColors().textSecondary)
    }
}

@Composable
fun MetricCardAdmin(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = senaColors().surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, senaColors().border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = senaColors().green, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = senaColors().text)
            Text(label, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AdminProfileScreenPreview() {
    ProyecTwinTheme {
        AdminProfileScreen(onBack = {}, onNavigate = {})
    }
}

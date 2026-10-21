package com.example.proyectwin.ui.screens.instructor

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
import com.example.proyectwin.ui.viewmodel.FichasUiState
import com.example.proyectwin.ui.viewmodel.FichasViewModel
import com.example.proyectwin.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorProfileScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    fichasViewModel: FichasViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val authState by authViewModel.uiState.collectAsState()
    val user = (authState as? AuthUiState.LoggedIn)?.user
    val scope = rememberCoroutineScope()
    val saveSuccess by profileViewModel.saveSuccess.collectAsState()
    val saveError by profileViewModel.saveError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val fichasState by fichasViewModel.uiState.collectAsState()

    var isEditing by remember { mutableStateOf(false) }
    // Usa los campos del modelo (nombre/apellido) y solo divide `name` como
    // fallback de cuentas antiguas, conservando apellidos compuestos.
    var name by remember(user) { mutableStateOf(user?.nombre ?: user?.name?.substringBefore(' ') ?: "") }
    var lastName by remember(user) { mutableStateOf(user?.apellido ?: user?.name?.substringAfter(' ', "") ?: "") }
    var email by remember(user) { mutableStateOf(user?.email ?: "") }

    LaunchedEffect(Unit) {
        fichasViewModel.loadAllFichas()
        user?.let { dashboardViewModel.loadInstructorDashboard(it.id) }
    }

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

    val dashState by dashboardViewModel.uiState.collectAsState()
    val instructorProjects = (dashState as? DashboardUiState.Success)?.projects.orEmpty()
    val proyectosCount = instructorProjects.size
    val proyectosAprobados = instructorProjects.count { it.estado == ProjectStatus.APROBADO.value }
    val aprendicesCount = (fichasState as? FichasUiState.Success)?.fichas
        ?.flatMap { it.estudiantes }
        ?.distinctBy { it.id }
        ?.size
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
                title = "Perfil Instructor",
                onBack = onBack,
                showProfile = false,
                showNotifications = true,
                onNavigateToAlerts = { onNavigate(AppNavigation.INSTRUCTOR_ALERTS) }
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
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(senaColors().header, senaColors().green.copy(alpha = 0.8f))
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .offset(x = (-40).dp, y = (-20).dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                )
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .offset(y = (-80).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SenaAvatar(
                    fotoBase64 = user?.fotoPerfil,
                    nombre = user?.name ?: "Instructor",
                    modifier = Modifier.size(110.dp),
                    onClick = { photoPickerLauncher.launch("image/*") },
                    showChangeIndicator = true
                )

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                    Text("Cambiar foto", style = MaterialTheme.typography.labelSmall, color = senaColors().green)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = user?.name ?: "Instructor",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = senaColors().text
                )
                Text(
                    text = user?.roleDisplayName ?: "Instructor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = senaColors().textLight
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCardSmall(icon = Icons.Filled.Tasks, value = "$proyectosCount", label = "Proyectos", modifier = Modifier.weight(1f))
                    MetricCardSmall(icon = Icons.Default.CheckCircle, value = "$proyectosAprobados", label = "Aprobados", modifier = Modifier.weight(1f))
                    MetricCardSmall(icon = Icons.Default.People, value = "$aprendicesCount", label = "Aprendices", modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(32.dp))

                SenaSectionHeader(title = "Información Personal")
                SenaCard(elevation = 1.dp) {
                    if (isEditing) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SenaTextField(value = name, onValueChange = { name = it }, label = "Nombre", modifier = Modifier.weight(1f))
                                SenaTextField(value = lastName, onValueChange = { lastName = it }, label = "Apellido", modifier = Modifier.weight(1f))
                            }
                            SenaTextField(value = email, onValueChange = { email = it }, label = "Correo Institucional", leadingIcon = Icons.Default.Email, enabled = false)

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SenaButton(text = "Cancelar", onClick = { isEditing = false }, isPrimary = false, modifier = Modifier.weight(1f))
                                SenaButton(text = "Guardar", onClick = {
                                    profileViewModel.updateProfileNombres(name.trim(), lastName.trim())
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
                            SenaSettingsItem(icon = Icons.Default.Email, title = "Correo Institucional", description = user?.email ?: "-")
                            HorizontalDivider(color = senaColors().borderSoft, modifier = Modifier.padding(start = 56.dp))
                            SenaSettingsItem(icon = Icons.Default.Badge, title = "Documento de Identidad", description = user?.documentoIdentidad ?: "-")

                            Spacer(modifier = Modifier.height(16.dp))
                            SenaButton(
                                text = "Editar Información",
                                onClick = {
                                    name = user?.nombre ?: user?.name?.substringBefore(' ') ?: ""
                                    lastName = user?.apellido ?: user?.name?.substringAfter(' ', "") ?: ""
                                    email = user?.email ?: ""
                                    isEditing = true
                                },
                                isPrimary = false,
                                icon = Icons.Default.Edit,
                                modifier = Modifier.height(44.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SenaSectionHeader(title = "Fichas a Cargo")
                SenaCard(elevation = 1.dp) {
                    when (fichasState) {
                        is FichasUiState.Loading -> {
                            SenaLoadingState(modifier = Modifier.height(80.dp))
                        }
                        is FichasUiState.Error -> {
                            Text(
                                (fichasState as FichasUiState.Error).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = senaColors().danger
                            )
                        }
                        is FichasUiState.Success -> {
                            val fichas = (fichasState as FichasUiState.Success).fichas
                            if (fichas.isEmpty()) {
                                Text(
                                    "Sin ficha asignada",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = senaColors().textLight
                                )
                            } else {
                                Column {
                                    fichas.forEachIndexed { index, ficha ->
                                        SenaSettingsItem(
                                            icon = Icons.Default.School,
                                            title = ficha.nombre ?: "Ficha ${ficha.codigo}",
                                            description = buildString {
                                                append(ficha.codigo)
                                                if (!ficha.numero.isNullOrBlank()) append(" · N. ${ficha.numero}")
                                                if (ficha.programa.isNotBlank()) append(" · ${ficha.programa}")
                                            }
                                        )
                                        if (index < fichas.size - 1) {
                                            HorizontalDivider(color = senaColors().borderSoft, modifier = Modifier.padding(start = 56.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SenaSectionHeader(title = "Seguridad")
                SenaCard(elevation = 1.dp) {
                    SenaSettingsItem(
                        icon = Icons.Default.Lock,
                        title = "Cambiar Contraseña",
                        description = "Actualiza tu acceso",
                        onClick = { onNavigate(AppNavigation.CHANGE_PASSWORD) }
                    )
                    HorizontalDivider(color = senaColors().borderSoft, modifier = Modifier.padding(start = 56.dp))
                    SenaSettingsItem(
                        icon = Icons.Default.Email,
                        title = "Cambiar Correo Electrónico",
                        description = "Actualiza tu correo de contacto",
                        onClick = { onNavigate(AppNavigation.CHANGE_EMAIL) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                SenaSectionHeader(title = "Soporte")
                SenaCard(elevation = 1.dp) {
                    SenaSettingsItem(
                        icon = Icons.Default.SystemUpdate,
                        title = "Actualizaciones",
                        description = "Versión instalada y nuevas versiones de ProyecTwin",
                        onClick = { onNavigate(AppNavigation.APP_UPDATES) }
                    )
                    HorizontalDivider(color = senaColors().borderSoft, modifier = Modifier.padding(start = 56.dp))
                    SenaSettingsItem(
                        icon = Icons.Default.BugReport,
                        title = "Reportar Errores",
                        description = "Informa fallas técnicas para que el equipo las revise",
                        onClick = { onNavigate(AppNavigation.REPORT_ISSUE) }
                    )
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
fun MetricCardSmall(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = senaColors().text)
            Text(label, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun InstructorProfilePreview() {
    ProyecTwinTheme {
        InstructorProfileScreen(onBack = {}, onNavigate = {})
    }
}

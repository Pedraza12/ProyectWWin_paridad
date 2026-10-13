package com.example.proyectwin.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.ProjectStatus
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.util.formatearFechaHoraLocal
import com.example.proyectwin.ui.viewmodel.AdminUiState
import com.example.proyectwin.ui.viewmodel.AdminViewModel
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onNavigate: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    adminViewModel: AdminViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val authState by authViewModel.uiState.collectAsState()
    val adminState by adminViewModel.uiState.collectAsState()
    val user = (authState as? AuthUiState.LoggedIn)?.user
    var isRefreshing by remember { mutableStateOf(false) }

    val isLoading = adminState is AdminUiState.Loading
    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }

    if (adminState is AdminUiState.Loading) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SenaSkeletonLine(width = 200.dp, height = 24.dp)
                Spacer(Modifier.height(8.dp))
                SenaSkeletonLine(width = 300.dp, height = 14.dp)
                Spacer(Modifier.height(20.dp))
                SenaSkeletonStats(count = 4)
                Spacer(Modifier.height(20.dp))
                repeat(3) { SenaSkeletonCard(lines = 2, hasAvatar = true) }
            }
        }
        return
    }

    if (adminState is AdminUiState.Error) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            SenaErrorState(message = (adminState as AdminUiState.Error).message, onRetry = { adminViewModel.refresh() })
        }
        return
    }

    val adminSuccess = adminState as AdminUiState.Success

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SenaTopBar(
                title = "ProyecTwin Master",
                onNavigateToProfile = { onNavigate(AppNavigation.ADMIN_PROFILE) },
                onNavigateToAlerts = { onNavigate(AppNavigation.ADMIN_ALERTS) }
            )
        },
        containerColor = senaColors().background,
        bottomBar = bottomBar
    ) { paddingValues ->
        SenaPullRefresh(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; adminViewModel.refresh() },
            modifier = Modifier.padding(paddingValues)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // --- ADMIN SYSTEM STATUS HEADER ---
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp))
                        .background(Brush.verticalGradient(colors = listOf(Color(0xFF022C22), Color(0xFF064E3B))))
                        .padding(24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        Text(
                            "Panel de Control Global",
                            style = MaterialTheme.typography.labelMedium,
                            color = senaColors().success,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${user?.name ?: "Sistema"} Operativo",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(Modifier.height(20.dp))

                        // Analytics Row: datos reales del panel.
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val enRevision = adminSuccess.projects.count { it.estado == "pendiente" }
                            SystemStatusPill("$enRevision en revisión", senaColors().warning, Icons.Default.Pending, modifier = Modifier.weight(1f))
                            SystemStatusPill("${adminSuccess.similarities.size} pares", senaColors().info, Icons.Default.Compare, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // --- REAL-TIME METRICS ---
            item {
                Column {
                    PaddingRow {
                        Text("Métricas en Tiempo Real", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            SenaMetricCard(
                                icon = Icons.Default.Groups,
                                value = "${adminSuccess.users.size}",
                                label = "Usuarios",
                                color = senaColors().info,
                                modifier = Modifier.width(160.dp)
                            )
                        }
                        item {
                            SenaMetricCard(
                                icon = Icons.Default.FolderSpecial,
                                value = "${adminSuccess.projects.size}",
                                label = "Proyectos",
                                color = senaColors().success,
                                modifier = Modifier.width(160.dp)
                            )
                        }
                        item {
                            SenaMetricCard(
                                icon = Icons.Default.BugReport,
                                value = "${adminSuccess.bugReports.size}",
                                label = "Reportes",
                                color = senaColors().warning,
                                modifier = Modifier.width(160.dp)
                            )
                        }
                        item {
                            SenaMetricCard(
                                icon = Icons.Default.Plagiarism,
                                value = "${adminSuccess.similarities.size}",
                                label = "Similitudes",
                                color = senaColors().danger,
                                modifier = Modifier.width(160.dp)
                            )
                        }
                    }
                }
            }

            // --- RECENT PROJECTS ---
            item {
                Column {
                    PaddingRow {
                        Text("Proyectos Recientes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        adminSuccess.projects.take(5).forEach { project ->
                            AdminProjectRow(
                                project = project,
                                onClick = {
                                    onNavigate(
                                        AppNavigation.ADMIN_PROJECT_DETAIL.replace("{projectId}", project.id.toString()),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // --- CORE MANAGEMENT ACTIONS ---
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SenaSectionHeader(title = "Gestión Principal")

                    ManagementActionCard(
                        title = "Usuarios & Roles",
                        desc = "Administrar permisos de acceso",
                        icon = Icons.Default.ManageAccounts,
                        color = senaColors().info,
                        onClick = { onNavigate(AppNavigation.ADMIN_USERS) }
                    )
                    ManagementActionCard(
                        title = "Propuestas",
                        desc = "Todas las propuestas y su estado",
                        icon = Icons.Default.FolderSpecial,
                        color = senaColors().success,
                        onClick = { onNavigate(AppNavigation.ADMIN_PROJECTS) }
                    )
                    ManagementActionCard(
                        title = "Similitudes Globales",
                        desc = "Auditoría de originalidad",
                        icon = Icons.Default.Plagiarism,
                        color = senaColors().warning,
                        onClick = { onNavigate(AppNavigation.ADMIN_SIMILARITY_LIST) }
                    )
                    ManagementActionCard(
                        title = "Reportes Técnicos",
                        desc = "Mantenimiento de plataforma",
                        icon = Icons.Default.BugReport,
                        color = senaColors().danger,
                        onClick = { onNavigate(AppNavigation.ADMIN_BUGS) }
                    )
                    ManagementActionCard(
                        title = "Fichas",
                        desc = "Grupos, roster y credenciales",
                        icon = Icons.Default.Groups,
                        color = senaColors().success,
                        onClick = { onNavigate(AppNavigation.ADMIN_FICHAS) }
                    )
                    ManagementActionCard(
                        title = "Catálogos",
                        desc = "Redes y programas de formación",
                        icon = Icons.Default.Category,
                        color = senaColors().accent,
                        onClick = { onNavigate(AppNavigation.ADMIN_CATALOGOS) }
                    )
                    ManagementActionCard(
                        title = "Bitácora",
                        desc = "Auditoría de acciones sensibles",
                        icon = Icons.Default.History,
                        color = senaColors().textSecondary,
                        onClick = { onNavigate(AppNavigation.ADMIN_BITACORA) }
                    )
                }
            }

            // --- SYSTEM CONFIG ---
            item {
                PaddingRow {
                    SenaCard(elevation = 2.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = senaColors().text.copy(alpha = 0.05f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = senaColors().text)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Motor de Similitud", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text("Umbral, ventana del corpus y recálculo", style = MaterialTheme.typography.bodySmall, color = senaColors().textLight)
                            }
                            SenaButton(text = "Abrir", onClick = {
                                onNavigate(AppNavigation.ADMIN_MOTOR)
                            }, isPrimary = false)
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun AdminProjectRow(project: Project, onClick: () -> Unit = {}) {
    SenaCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = senaColors().success.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = senaColors().success, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(project.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = senaColors().text)
                Text(project.studentName ?: "Sin asignar", style = MaterialTheme.typography.bodySmall, color = senaColors().textLight)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatearFechaHoraLocal(project.createdAt), style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                Spacer(Modifier.height(4.dp))
                val colorEstado = when (project.projectStatus) {
                    ProjectStatus.APROBADO -> senaColors().success
                    ProjectStatus.PENDIENTE -> senaColors().warning
                    ProjectStatus.RECHAZADO -> senaColors().danger
                    ProjectStatus.BORRADOR -> senaColors().info
                }
                Surface(shape = RoundedCornerShape(8.dp), color = colorEstado.copy(alpha = 0.15f)) {
                    Text(
                        project.statusDisplay,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorEstado,
                    )
                }
            }
        }
    }
}

@Composable
fun SystemStatusPill(label: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
fun AdminMetricSquare(label: String, value: String, icon: ImageVector, color: Color) {
    SenaCard(modifier = Modifier.width(160.dp), elevation = 4.dp) {
        Column {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(16.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = senaColors().text)
            Text(label, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
        }
    }
}

@Composable
fun ManagementActionCard(title: String, desc: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = senaColors().backgroundElevated,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, senaColors().borderSoft)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(14.dp),
                color = color.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = senaColors().text)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = senaColors().textLight)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = senaColors().border)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AdminDashboardPreview() {
    ProyecTwinTheme {
        AdminDashboardScreen(onNavigate = {})
    }
}

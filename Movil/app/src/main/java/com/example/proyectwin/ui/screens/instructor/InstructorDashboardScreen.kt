package com.example.proyectwin.ui.screens.instructor

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.ProjectStatus
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.util.formatearFechaHoraLocal
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import com.example.proyectwin.ui.viewmodel.DashboardUiState
import com.example.proyectwin.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorDashboardScreen(
    onNavigate: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsState()
    val dashState by dashboardViewModel.uiState.collectAsState()
    val user = (authState as? AuthUiState.LoggedIn)?.user

    var refreshTrigger by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(user?.id, refreshTrigger) {
        user?.let { dashboardViewModel.loadInstructorDashboard(it.id) }
    }

    val isLoading = dashState is DashboardUiState.Loading
    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }

    var busqueda by remember { mutableStateOf("") }
    val projects = (dashState as? DashboardUiState.Success)?.projects ?: emptyList()
    val pendingCount = projects.count { it.estado == ProjectStatus.PENDIENTE.value }
    val aprobadasCount = projects.count { it.estado == ProjectStatus.APROBADO.value }
    val inboxProjects = projects
        .filter { it.estado == ProjectStatus.PENDIENTE.value }
        .filter {
            busqueda.isBlank() ||
                it.title.contains(busqueda, true) ||
                it.studentName.orEmpty().contains(busqueda, true)
        }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "ProyecTwin",
                onNavigateToProfile = { onNavigate(AppNavigation.INSTRUCTOR_PROFILE) },
                onNavigateToAlerts = { onNavigate(AppNavigation.INSTRUCTOR_ALERTS) },
                onLogout = { authViewModel.logout() }
            )
        },
        containerColor = senaColors().background,
        bottomBar = bottomBar
    ) { paddingValues ->
        when (val state = dashState) {
            is DashboardUiState.Loading -> SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is DashboardUiState.Error -> SenaErrorState(
                message = state.message,
                modifier = Modifier.padding(paddingValues),
                onRetry = { user?.let { dashboardViewModel.loadInstructorDashboard(it.id) } },
            )
            is DashboardUiState.Success -> SenaPullRefresh(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; refreshTrigger++ },
            modifier = Modifier.padding(paddingValues)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // --- INSTRUCTOR PRODUCTIVITY HEADER ---
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp))
                        .background(Brush.verticalGradient(colors = listOf(senaColors().background, senaColors().header)))
                        .padding(24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        Text(
                            "Gestión de Proyectos",
                            style = MaterialTheme.typography.labelMedium,
                            color = senaColors().accent,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Instructor ${user?.name ?: "Instructor"}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            InstructorSummaryMetric("$aprobadasCount", "Aprobadas", senaColors().accent)
                            InstructorSummaryMetric("$pendingCount", "Pendientes", senaColors().warning)
                        }
                    }
                }
            }

            // --- INBOX FILTERS / SEARCH ---
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SenaTextField(
                        value = busqueda,
                        onValueChange = { busqueda = it },
                        label = "",
                        placeholder = "Buscar propuesta o aprendiz...",
                        leadingIcon = Icons.Default.Search
                    )
                }
            }

            // --- PRODUCTIVITY INBOX (PENDING PROPOSALS) ---
            item {
                Column {
                    PaddingRow {
                        SenaSectionHeader(
                            title = "Inbox de Revisión",
                            actionText = "Historial",
                            onActionClick = { onNavigate(AppNavigation.INSTRUCTOR_REVISION) }
                        )
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        inboxProjects.forEach { project ->
                            ProductivityInboxCard(project, onClick = { onNavigate("instructor_detail/${project.id}") })
                        }
                    }
                }
            }

            // --- QUICK TOOLS ---
            item {
                Column {
                    PaddingRow {
                        Text("Herramientas Rápidas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            ToolCard(
                                title = "Directorio",
                                subtitle = "Tus aprendices",
                                icon = Icons.Default.People,
                                color = senaColors().info,
                                onClick = { onNavigate(AppNavigation.INSTRUCTOR_FICHAS) }
                            )
                        }
                        item {
                            ToolCard(
                                title = "Gestión Fichas",
                                subtitle = "Administrar grupos",
                                icon = Icons.AutoMirrored.Filled.Assignment,
                                color = senaColors().green,
                                onClick = { onNavigate(AppNavigation.INSTRUCTOR_MANAGE_FICHAS) }
                            )
                        }
                        item {
                            ToolCard(
                                title = "Similitudes",
                                subtitle = "Coincidencias del motor",
                                icon = Icons.Default.Compare,
                                color = senaColors().warning,
                                onClick = { onNavigate(AppNavigation.INSTRUCTOR_SIMILITUDES_BASE) }
                            )
                        }
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
fun InstructorSummaryMetric(value: String, label: String, color: Color) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProductivityInboxCard(project: Project, onClick: () -> Unit) {
    SenaCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(14.dp),
                color = senaColors().borderSoft
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        project.studentName?.take(1) ?: "?",
                        fontWeight = FontWeight.Black,
                        color = senaColors().text,
                        fontSize = 20.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(project.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = senaColors().text)
                Text("Aprendiz: ${project.studentName ?: "Desconocido"}", style = MaterialTheme.typography.bodySmall, color = senaColors().textLight)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatearFechaHoraLocal(project.createdAt), style = MaterialTheme.typography.labelSmall, color = senaColors().textMuted)
                Spacer(Modifier.height(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = senaColors().border, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
fun ToolCard(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(180.dp).height(100.dp),
        shape = RoundedCornerShape(24.dp),
        color = senaColors().backgroundElevated,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, senaColors().borderSoft)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = senaColors().text)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun InstructorDashboardPreview() {
    ProyecTwinTheme {
        InstructorDashboardScreen(onNavigate = {})
    }
}

package com.example.proyectwin.ui.screens.aprendiz

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.util.formatearFechaLocal
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import com.example.proyectwin.ui.viewmodel.DashboardUiState
import com.example.proyectwin.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onNavigate: (String) -> Unit,
    onNewProject: () -> Unit,
    onProjectDetail: (String) -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    bottomBar: @Composable () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Todos") }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val filters = listOf("Todos", "Borrador", "En revisión", "Aprobado", "Rechazado")

    val authState by authViewModel.uiState.collectAsState()
    val dashState by dashboardViewModel.uiState.collectAsState()
    val user = (authState as? AuthUiState.LoggedIn)?.user

    LaunchedEffect(user, refreshTrigger) {
        if (user != null) {
            dashboardViewModel.loadStudentDashboard(user.id)
        }
    }

    val isLoading = dashState is DashboardUiState.Loading
    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }

    val filteredProjects = (dashState as? DashboardUiState.Success)?.projects?.filter { project ->
        val matchesFilter = if (selectedFilter == "Todos") true else {
            project.statusDisplay == selectedFilter
        }
        val matchesSearch = project.title.contains(searchQuery, ignoreCase = true)
        matchesFilter && matchesSearch
    } ?: emptyList()

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "ProyecTwin",
                onBack = { onNavigate(AppNavigation.APRENDIZ_DASHBOARD) },
                onNavigateToProfile = { onNavigate(AppNavigation.APRENDIZ_PROFILE) },
                onNavigateToAlerts = { onNavigate(AppNavigation.APRENDIZ_ALERTS) }
            )
        },
        containerColor = senaColors().background,
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewProject,
                containerColor = senaColors().green,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Proyecto")
            }
        }
    ) { paddingValues ->
        SenaPullRefresh(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; refreshTrigger++ },
            modifier = Modifier.padding(paddingValues)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SenaPageHeader(
                    title = "Mis Proyectos",
                    subtitle = "Gestiona y haz seguimiento a tus propuestas de formación.",
                    icon = Icons.Default.FolderOpen
                )
            }

            item {
                SenaCard(elevation = 1.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Filtros de búsqueda",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = senaColors().textLight,
                            letterSpacing = 0.5.sp
                        )
                        SenaTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = "",
                            placeholder = "Buscar por nombre...",
                            leadingIcon = Icons.Default.Search
                        )

                        Text(
                            "Estado del proyecto",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = senaColors().textLight,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filters.forEach { filter ->
                                SenaChip(
                                    text = filter,
                                    color = if (filter == "Todos") senaColors().green else when(filter) {
                                        "Borrador" -> senaColors().accent
                                        "En revisión" -> senaColors().warning
                                        "Aprobado" -> senaColors().success
                                        else -> senaColors().danger
                                    },
                                    isSelected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter }
                                )
                            }
                        }
                    }
                }
            }

            when (val state = dashState) {
                is DashboardUiState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        SenaLoadingState()
                    }
                }
                is DashboardUiState.Error -> item {
                    SenaErrorState(
                        message = state.message,
                        onRetry = { user?.id?.let { dashboardViewModel.loadStudentDashboard(it) } },
                    )
                }
                is DashboardUiState.Success -> {
                    if (filteredProjects.isEmpty()) {
                        item {
                            SenaEmptyState(
                                message = "No se encontraron proyectos que coincidan con tu búsqueda.",
                                icon = Icons.Default.SearchOff
                            )
                        }
                    } else {
                        items(filteredProjects) { project ->
                    SenaCard(onClick = { onProjectDetail(project.id.toString()) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SenaStatusBadge(status = project.statusDisplay)
                                Text(formatearFechaLocal(project.createdAt), style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                            }
                            Text(
                                project.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = senaColors().text
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = senaColors().textLight, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(project.studentName ?: "Sin aprendiz", style = MaterialTheme.typography.bodySmall, color = senaColors().textSecondary)
                                Spacer(Modifier.weight(1f))
                                if (project.instructorName != null) {
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        shape = CircleShape,
                                        color = senaColors().green.copy(alpha = 0.1f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                project.instructorName.split(" ").joinToString("") { it.take(1) },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = senaColors().green
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
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProjectsScreenPreview() {
    ProyecTwinTheme {
        ProjectsScreen(onNavigate = {}, onNewProject = {}, onProjectDetail = {})
    }
}

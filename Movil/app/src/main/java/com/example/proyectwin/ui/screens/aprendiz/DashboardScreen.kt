package com.example.proyectwin.ui.screens.aprendiz

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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.ProjectStatus
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import com.example.proyectwin.ui.viewmodel.DashboardUiState
import com.example.proyectwin.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsState()
    val dashState by dashboardViewModel.uiState.collectAsState()

    val user = (authState as? AuthUiState.LoggedIn)?.user
    val userName = user?.name?.split(" ")?.firstOrNull() ?: "Usuario"
    val todayDate = remember {
        SimpleDateFormat("d MMM. yyyy", Locale.forLanguageTag("es-CO")).format(Date())
    }

    var refreshTrigger by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(user, refreshTrigger) {
        user?.let { dashboardViewModel.loadStudentDashboard(it.id) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rocket")
    val rocketFloat by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 15f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "rocketFloat"
    )

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "ProyecTwin",
                onNavigateToProfile = { onNavigate(AppNavigation.APRENDIZ_PROFILE) },
                onNavigateToAlerts = { onNavigate(AppNavigation.APRENDIZ_ALERTS) }
            )
        },
        containerColor = senaColors().background,
        bottomBar = bottomBar
    ) { paddingValues ->
        when (val state = dashState) {
            is DashboardUiState.Loading -> SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is DashboardUiState.Error -> SenaErrorState(message = state.message, onRetry = { user?.let { dashboardViewModel.loadStudentDashboard(it.id) } })
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
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp))
                            .background(Brush.verticalGradient(colors = listOf(senaColors().header, senaColors().green)))
                            .padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.RocketLaunch,
                            contentDescription = null,
                            modifier = Modifier
                                .size(240.dp)
                                .align(Alignment.CenterEnd)
                                .offset(x = 60.dp, y = 40.dp)
                                .rotate(-15f + rocketFloat)
                                .scale(1.2f),
                            tint = Color.White.copy(alpha = 0.05f)
                        )

                        Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                            Text(
                                "Centro de Comando",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "¡Hola de nuevo, $userName!",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(Modifier.height(16.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(todayDate, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        PaddingRow {
                            Text("Comandos Rápidos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(16.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                PremiumActionCard(
                                    title = "Nuevo Proyecto",
                                    icon = Icons.Default.AddCircle,
                                    color = senaColors().success,
                                    onClick = { onNavigate(AppNavigation.rutaNuevoProyecto()) }
                                )
                            }
                            item {
                                PremiumActionCard(
                                    title = "Similitudes",
                                    icon = Icons.Default.Compare,
                                    color = senaColors().info,
                                    onClick = { onNavigate(AppNavigation.APRENDIZ_SIMILITUDES_BASE) }
                                )
                            }
                            item {
                                PremiumActionCard(
                                    title = "Mi Ficha",
                                    icon = Icons.Default.School,
                                    color = senaColors().warning,
                                    onClick = { onNavigate(AppNavigation.APRENDIZ_FICHA_DETAIL) }
                                )
                            }
                        }
                    }
                }

                item {
                    Column {
                        PaddingRow {
                            SenaSectionHeader(
                                title = "Proyectos Activos",
                                actionText = "Ver todos",
                                onActionClick = { onNavigate(AppNavigation.APRENDIZ_PROJECTS) }
                            )
                        }

                        if (state.projects.isEmpty()) {
                            PaddingRow {
                                SenaEmptyState(
                                    message = "No hay misiones activas. Inicia una nueva propuesta ahora.",
                                    icon = Icons.Default.Explore
                                )
                            }
                        } else {
                            state.projects.forEach { project ->
                                PaddingRow(Modifier.padding(bottom = 16.dp)) {
                                    DashboardProjectCard(
                                        title = project.title,
                                        status = project.statusDisplay,
                                        onClick = { onNavigate("aprendiz_detail/${project.id}") }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    val misPropuestas = state.projects.filter { proyecto ->
                        proyecto.studentId == user?.id || proyecto.equipo.any { it.id == user?.id }
                    }
                    val aprobadas = misPropuestas.count { it.estado == ProjectStatus.APROBADO.value }
                    val enRevision = misPropuestas.count { it.estado == ProjectStatus.PENDIENTE.value }
                    val progreso = if (misPropuestas.isEmpty()) 0f else aprobadas.toFloat() / misPropuestas.size

                    PaddingRow {
                        SenaCard(containerColor = senaColors().header) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Estado de Propuestas", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        if (misPropuestas.isEmpty()) "Sin propuestas" else "$aprobadas de ${misPropuestas.size} aprobadas",
                                        color = if (aprobadas > 0) senaColors().success else Color.White,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (misPropuestas.isEmpty()) {
                                            "Crea tu primera propuesta para comenzar el seguimiento."
                                        } else {
                                            "$enRevision en revisión · ${misPropuestas.size - aprobadas - enRevision} en borrador o rechazadas."
                                        },
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = { progreso },
                                        modifier = Modifier.size(64.dp),
                                        color = senaColors().success,
                                        strokeWidth = 6.dp,
                                        trackColor = Color.White.copy(alpha = 0.1f),
                                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                    Text(
                                        "${(progreso * 100).toInt()}%",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
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

    val isLoading = dashState is DashboardUiState.Loading
    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }
}

@Composable
fun PremiumActionCard(title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(140.dp),
        shape = RoundedCornerShape(32.dp),
        color = senaColors().backgroundElevated,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, senaColors().borderSoft)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = color.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = senaColors().text, lineHeight = 18.sp)
        }
    }
}

@Composable
fun DashboardProjectCard(title: String, status: String, onClick: () -> Unit) {
    SenaCard(onClick = onClick, elevation = 2.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                SenaStatusBadge(status = status)
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = senaColors().text)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = senaColors().border)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    ProyecTwinTheme {
        DashboardScreen(onNavigate = {})
    }
}

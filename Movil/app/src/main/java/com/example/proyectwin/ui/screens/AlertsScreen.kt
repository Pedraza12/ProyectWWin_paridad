package com.example.proyectwin.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.model.Notification
import com.example.proyectwin.data.model.NotificationType
import com.example.proyectwin.navigation.NotificationRoutes
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.util.formatearFechaLocal
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import com.example.proyectwin.ui.viewmodel.NotificationsUiState
import com.example.proyectwin.ui.viewmodel.NotificationsViewModel

enum class AlertType(val label: String) {
    URGENT("Urgente"),
    WARNING("Advertencia"),
    INFO("Informativa"),
    SUCCESS("Éxito")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit = {},
    profileRoute: String = "aprendiz_profile",
    /** Lista de similitudes del proyecto notificado. */
    similitudesRoute: String = "aprendiz/similitudes?proyectoId={proyectoId}",
    detailRoute: String = "aprendiz_detail/{id}",
    fichaRoute: String = "aprendiz/ficha",
    /** Ruta al reporte; vacía = los no-admin solo marcan la lectura. */
    reporteRoute: String = "",
    bottomBar: @Composable () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    notificationsViewModel: NotificationsViewModel = hiltViewModel()
) {
    val uiState by authViewModel.uiState.collectAsState()
    val user = (uiState as? AuthUiState.LoggedIn)?.user

    var refreshTrigger by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    val notificationsState by notificationsViewModel.uiState.collectAsState()
    val mensaje by notificationsViewModel.mensaje.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(user?.id) {
        user?.id?.let { notificationsViewModel.load(it) }
    }
    LaunchedEffect(mensaje) {
        mensaje?.let {
            snackbarHostState.showSnackbar(it)
            notificationsViewModel.limpiarMensaje()
        }
    }

    var selectedFilter by remember { mutableStateOf("Todos") }
    val filters = listOf("Todos", "Similitud", "Instructor", "Sistema")

    val allAlerts = (notificationsState as? NotificationsUiState.Success)?.notifications
        ?: emptyList()
    val filteredAlerts = if (selectedFilter == "Todos") {
        allAlerts
    } else {
        allAlerts.filter { alert ->
            val category = when (alert.notifType) {
                NotificationType.SISTEMA -> "Sistema"
                NotificationType.SIMILITUD -> "Similitud"
                NotificationType.OBSERVACION, NotificationType.REVISION, NotificationType.MENSAJE -> "Instructor"
            }
            category == selectedFilter
        }
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "Notificaciones",
                onBack = onBack,
                showNotifications = false,
                onNavigateToProfile = { onNavigate(profileRoute) },
            )
        },
        containerColor = senaColors().background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar
    ) { paddingValues ->
        when (notificationsState) {
            is NotificationsUiState.Loading -> {
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            }
            is NotificationsUiState.Error -> {
                SenaErrorState(
                    message = (notificationsState as NotificationsUiState.Error).message,
                    modifier = Modifier.padding(paddingValues),
                    onRetry = { notificationsViewModel.refresh() },
                )
            }
            is NotificationsUiState.Success -> {
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
                    title = "Centro de Alertas",
                    subtitle = "Mantente al día con el estado de tus proyectos y observaciones.",
                    icon = Icons.Default.Notifications
                )
            }

            item {
                val sinLeer = allAlerts.count { !it.leido }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (sinLeer > 0)
                            "Tienes $sinLeer alerta${if (sinLeer != 1) "s" else ""} sin leer."
                        else
                            "Estás al día con tus notificaciones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { notificationsViewModel.marcarTodasComoLeidas() },
                        enabled = sinLeer > 0,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = if (sinLeer > 0) senaColors().green else senaColors().textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Marcar todas como leídas",
                            color = if (sinLeer > 0) senaColors().green else senaColors().textMuted,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filters) { filter ->
                        SenaChip(
                            text = filter,
                            color = senaColors().green,
                            isSelected = selectedFilter == filter,
                            onClick = { selectedFilter = filter }
                        )
                    }
                }
            }

            if (filteredAlerts.isEmpty()) {
                item {
                    SenaEmptyState(
                        message = "No tienes notificaciones.",
                        icon = Icons.Default.NotificationsNone
                    )
                }
            } else {
                items(filteredAlerts, key = { it.id }) { alert ->
                    val esAdmin = user?.role == "admin"
                    NotificationCard(alert, onClick = {
                        notificationsViewModel.marcarComoLeida(alert)
                        refreshTrigger++
                        // Enlace inválido/antiguo → null: solo se marca leída.
                        NotificationRoutes.resolverDestino(
                            tipo = alert.notifType,
                            enlaceModulo = alert.enlaceModulo,
                            enlaceId = alert.enlaceId,
                            similitudesRoute = similitudesRoute,
                            detailRoute = detailRoute,
                            fichaRoute = fichaRoute,
                            reporteRoute = reporteRoute,
                        )?.let(onNavigate)
                    }, onDelete = if (esAdmin) {
                        { notificationsViewModel.eliminar(alert.id) }
                    } else {
                        null
                    })
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
        }
            }
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            notificationsViewModel.refresh()
            kotlinx.coroutines.delay(500)
        }
        isRefreshing = false
    }
}

@Composable
fun NotificationCard(alert: Notification, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    val alertType = when (alert.notifType) {
        NotificationType.REVISION, NotificationType.MENSAJE, NotificationType.SISTEMA -> AlertType.INFO
        NotificationType.SIMILITUD, NotificationType.OBSERVACION -> AlertType.WARNING
    }
    val (icon, color) = when (alertType) {
        AlertType.URGENT -> Icons.Default.Warning to senaColors().danger
        AlertType.WARNING -> Icons.Default.History to senaColors().warning
        AlertType.INFO -> Icons.AutoMirrored.Filled.Comment to senaColors().green
        AlertType.SUCCESS -> Icons.Default.CheckCircle to senaColors().success
    }

    val category = when (alert.notifType) {
        NotificationType.SISTEMA -> "Sistema"
        NotificationType.SIMILITUD -> "Similitud"
        NotificationType.OBSERVACION, NotificationType.REVISION, NotificationType.MENSAJE -> "Instructor"
    }

    val title = when (alert.notifType) {
        NotificationType.SIMILITUD -> "Similitud detectada"
        NotificationType.OBSERVACION -> "Observación"
        NotificationType.REVISION -> "Revisión"
        NotificationType.MENSAJE -> "Mensaje"
        NotificationType.SISTEMA -> "Sistema"
    }

    SenaCard(
        elevation = if (!alert.leido) 2.dp else 0.5.dp,
        onClick = onClick,
        containerColor = senaColors().backgroundElevated
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = color.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = color,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        formatearFechaLocal(alert.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = senaColors().textLight
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    alert.mensaje,
                    style = MaterialTheme.typography.bodySmall,
                    color = senaColors().textSecondary,
                    lineHeight = 18.sp
                )

                if (onDelete != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = senaColors().danger)
                        Spacer(Modifier.width(4.dp))
                        Text("Eliminar", style = MaterialTheme.typography.labelSmall, color = senaColors().danger)
                    }
                }

                if (!alert.leido) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = senaColors().green.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "NUEVO",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = senaColors().green,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AlertsScreenPreview() {
    ProyecTwinTheme {
        AlertsScreen(onNavigate = {})
    }
}

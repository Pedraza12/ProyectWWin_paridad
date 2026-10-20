package com.example.proyectwin.ui.screens.admin

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.model.BugReport
import com.example.proyectwin.data.model.BugReportStatus
import com.example.proyectwin.data.model.BugReportType
import com.example.proyectwin.data.model.typeDisplay
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import com.example.proyectwin.ui.viewmodel.BugReportsUiState
import com.example.proyectwin.ui.viewmodel.BugReportsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    bugReportsViewModel: BugReportsViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<BugReportType?>(null) }
    var selectedStatus by remember { mutableStateOf<BugReportStatus?>(null) }
    var reporteParaEliminar by remember { mutableStateOf<BugReport?>(null) }

    val reportsState by bugReportsViewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { bugReportsViewModel.load() }
    val reports = (reportsState as? BugReportsUiState.Success)?.reports ?: emptyList()

    val typeFilters = remember { listOf(null) + BugReportType.entries.toList() }
    val statusFilters = remember { listOf(null) + BugReportStatus.entries.toList() }

    val filteredReports = reports.filter { report ->
        val matchesType = selectedType == null || report.bugType == selectedType
        val matchesStatus = selectedStatus == null || report.bugStatus == selectedStatus
        val matchesSearch = report.reporterName?.contains(searchQuery, ignoreCase = true) == true ||
                report.descripcion.contains(searchQuery, ignoreCase = true) ||
                report.titulo.contains(searchQuery, ignoreCase = true)
        matchesType && matchesStatus && matchesSearch
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
        containerColor = senaColors().background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SenaPageHeader(
                    title = "Reportes de Fallas",
                    subtitle = "Supervisa y gestiona los errores técnicos reportados por los usuarios.",
                    icon = Icons.Default.BugReport
                )
            }

            // Filter Section
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
                            placeholder = "Buscar por usuario o descripción...",
                            leadingIcon = Icons.Default.Search
                        )

                        Text(
                            "Tipo de falla",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = senaColors().textLight,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            typeFilters.forEach { type ->
                                SenaChip(
                                    text = type?.typeDisplay ?: "Todos",
                                    color = when (type) {
                                        BugReportType.BUG_UI -> senaColors().info
                                        BugReportType.ERROR_DATOS -> senaColors().warning
                                        BugReportType.RENDIMIENTO -> senaColors().danger
                                        BugReportType.SEGURIDAD -> senaColors().danger
                                        else -> senaColors().green
                                    },
                                    isSelected = selectedType == type,
                                    onClick = { selectedType = type }
                                )
                            }
                        }

                        Text(
                            "Estado del reporte",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = senaColors().textLight,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            statusFilters.forEach { status ->
                                SenaChip(
                                    text = status?.let {
                                        when (it) {
                                            BugReportStatus.PENDIENTE -> "Pendiente"
                                            BugReportStatus.EN_REVISION -> "En Revisión"
                                            BugReportStatus.RESUELTO -> "Resuelto"
                                            BugReportStatus.CERRADO -> "Cerrado"
                                            BugReportStatus.RECHAZADO -> "Rechazado"
                                        }
                                    } ?: "Todos",
                                    color = when (status) {
                                        BugReportStatus.RESUELTO -> senaColors().success
                                        BugReportStatus.PENDIENTE -> senaColors().warning
                                        BugReportStatus.EN_REVISION -> senaColors().info
                                        BugReportStatus.CERRADO -> senaColors().textLight
                                        else -> senaColors().green
                                    },
                                    isSelected = selectedStatus == status,
                                    onClick = { selectedStatus = status }
                                )
                            }
                        }
                    }
                }
            }

            when (val state = reportsState) {
                is BugReportsUiState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        SenaLoadingState()
                    }
                }
                is BugReportsUiState.Error -> item {
                    SenaErrorState(message = state.message, onRetry = { bugReportsViewModel.load() })
                }
                is BugReportsUiState.Success -> {
                    if (filteredReports.isEmpty()) {
                        item {
                            SenaEmptyState(
                                message = "No se encontraron reportes que coincidan con la búsqueda.",
                                icon = Icons.Default.SearchOff
                            )
                        }
                    } else {
                        items(filteredReports) { report ->
                    BugReportCard(
                        report = report,
                        onClick = {
                            onNavigate(AppNavigation.ADMIN_BUG_DETAIL.replace("{bugId}", report.id.toString()))
                        },
                        onDelete = { reporteParaEliminar = report },
                    )
                }
            }
            }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }

        reporteParaEliminar?.let { reporte ->
            AlertDialog(
                onDismissRequest = { reporteParaEliminar = null },
                title = { Text("Eliminar reporte") },
                text = { Text("Se eliminará el reporte #${reporte.id} y sus notificaciones asociadas.") },
                confirmButton = {
                    TextButton(onClick = {
                        reporteParaEliminar = null
                        bugReportsViewModel.eliminar(reporte.id)
                    }) { Text("Eliminar", color = senaColors().danger) }
                },
                dismissButton = {
                    TextButton(onClick = { reporteParaEliminar = null }) { Text("Cancelar") }
                },
            )
        }
    }
}

@Composable
fun BugReportCard(
    report: BugReport,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
) {
    SenaCard(elevation = 1.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = senaColors().borderSoft,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "#${report.id}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = senaColors().textSecondary
                    )
                }
                SenaStatusBadge(status = report.statusDisplay)
            }

            Column {
                Text(
                    report.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = senaColors().green, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(report.reporterName ?: "Anónimo", style = MaterialTheme.typography.labelSmall, color = senaColors().textSecondary)
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = when (report.bugType) {
                            BugReportType.BUG_UI -> senaColors().info.copy(alpha = 0.1f)
                            BugReportType.ERROR_DATOS -> senaColors().warning.copy(alpha = 0.1f)
                            BugReportType.RENDIMIENTO -> senaColors().danger.copy(alpha = 0.1f)
                            BugReportType.SEGURIDAD -> senaColors().danger.copy(alpha = 0.1f)
                            else -> senaColors().textLight.copy(alpha = 0.1f)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            report.typeDisplay,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (report.bugType) {
                                BugReportType.BUG_UI -> senaColors().info
                                BugReportType.ERROR_DATOS -> senaColors().warning
                                BugReportType.RENDIMIENTO -> senaColors().danger
                                BugReportType.SEGURIDAD -> senaColors().danger
                                else -> senaColors().textLight
                            },
                            fontSize = 9.sp
                        )
                    }
                }
            }

            HorizontalDivider(color = senaColors().borderSoft)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClick) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ver Detalle", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = senaColors().danger)
                    Spacer(Modifier.width(4.dp))
                    Text("Eliminar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = senaColors().danger)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BugReportsScreenPreview() {
    ProyecTwinTheme {
        BugReportsScreen(onBack = {}, onNavigate = {})
    }
}

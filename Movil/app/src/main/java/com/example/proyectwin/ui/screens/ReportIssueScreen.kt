package com.example.proyectwin.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.model.BugReportType
import com.example.proyectwin.data.model.typeDisplay
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.BugReportsUiState
import com.example.proyectwin.ui.viewmodel.BugReportsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    bugReportsViewModel: BugReportsViewModel = hiltViewModel()
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf(BugReportType.BUG_UI) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    val reportsState by bugReportsViewModel.uiState.collectAsState()
    val creado by bugReportsViewModel.creado.collectAsState()
    val accionError by bugReportsViewModel.accionError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { bugReportsViewModel.load() }
    val reportes = (reportsState as? BugReportsUiState.Success)?.reports ?: emptyList()

    LaunchedEffect(creado) {
        if (creado) {
            title = ""
            description = ""
            tipo = BugReportType.BUG_UI
            isLoading = false
            bugReportsViewModel.limpiarCreado()
            snackbarHostState.showSnackbar("Reporte enviado. Un administrador lo revisará.")
        }
    }
    LaunchedEffect(accionError) {
        if (accionError != null) {
            isLoading = false
            errorMsg = accionError
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
                    text = "Enviar Reporte",
                    onClick = {
                        val titulo = title.trim()
                        val desc = description.trim()
                        if (titulo.isBlank() || desc.isBlank()) {
                            errorMsg = "Completa el título y la descripción del reporte."
                        } else {
                            errorMsg = null
                            isLoading = true
                            bugReportsViewModel.crear(
                                titulo = titulo,
                                descripcion = desc,
                                tipo = tipo.value,
                            )
                        }
                    },
                    isLoading = isLoading,
                    icon = Icons.AutoMirrored.Filled.Send,
                    modifier = Modifier.weight(1f)
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
                title = "Reportar Falla",
                subtitle = "Ayúdanos a mejorar el sistema reportando cualquier error técnico que encuentres.",
                icon = Icons.Default.BugReport
            )

            SenaCard(elevation = 1.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    SenaTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Título de la falla *",
                        placeholder = "Ej: Error al cargar proyectos",
                        leadingIcon = Icons.Default.Title
                    )
                    
                    SenaTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = "Descripción detallada *",
                        placeholder = "Explica paso a paso qué sucedió...",
                        modifier = Modifier.heightIn(min = 120.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Tipo de falla",
                            style = MaterialTheme.typography.labelMedium,
                            color = senaColors().textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BugReportType.opcionesReporte.forEach { opcion ->
                                SenaChip(
                                    text = opcion.typeDisplay,
                                    color = senaColors().green,
                                    isSelected = tipo == opcion,
                                    onClick = { tipo = opcion }
                                )
                            }
                        }
                    }
                }
            }

            SenaSectionHeader(title = "Tus Reportes Recientes")

            errorMsg?.let { mensaje ->
                Text(
                    mensaje,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().danger
                )
            }
            
            when (val state = reportsState) {
                is BugReportsUiState.Loading -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SenaLoadingState()
                }
                is BugReportsUiState.Error -> SenaErrorState(
                    message = state.message,
                    onRetry = { bugReportsViewModel.load() },
                )
                is BugReportsUiState.Success -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (reportes.isEmpty()) {
                        SenaEmptyState(
                            message = "Aún no has registrado reportes de falla.",
                            icon = Icons.Default.BugReport
                        )
                    } else {
                        reportes.forEach { r ->
                            SenaCard(elevation = 0.5.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("#${r.id.toString().padStart(3, '0')}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = senaColors().green)
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(r.titulo, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = senaColors().text)
                                        Text(r.descripcion, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight, maxLines = 1)
                                    }
                                    SenaStatusBadge(status = r.statusDisplay)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReportIssueScreenPreview() {
    ProyecTwinTheme {
        ReportIssueScreen(onBack = {}, onNavigate = {})
    }
}

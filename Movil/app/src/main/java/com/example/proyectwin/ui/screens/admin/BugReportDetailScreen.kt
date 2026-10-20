package com.example.proyectwin.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.model.BugReportStatus
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.util.formatearFechaLocal
import com.example.proyectwin.ui.viewmodel.BugReportsViewModel

/** Detalle real del reporte: estado, respuesta al solicitante y datos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportDetailScreen(
    bugId: String = "",
    onBack: () -> Unit,
    bugReportsViewModel: BugReportsViewModel = hiltViewModel(),
) {
    val bug by bugReportsViewModel.detalle.collectAsState()
    val error by bugReportsViewModel.accionError.collectAsState()
    val detalleError by bugReportsViewModel.detalleError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val bugIdNum = bugId.toIntOrNull() ?: 0

    var estadoSeleccionado by remember { mutableStateOf(BugReportStatus.PENDIENTE) }
    var respuesta by remember { mutableStateOf("") }
    var precargado by remember { mutableStateOf(false) }

    LaunchedEffect(bugIdNum) {
        if (bugIdNum > 0) bugReportsViewModel.cargarDetalle(bugIdNum)
    }

    LaunchedEffect(bug) {
        if (bug != null && !precargado) {
            estadoSeleccionado = BugReportStatus.fromValue(bug!!.estado)
            respuesta = bug!!.respuesta.orEmpty()
            precargado = true
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            bugReportsViewModel.limpiarError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SenaTopBar(
                title = "Detalle del Reporte",
                onBack = onBack,
                showProfile = true,
                showNotifications = true,
            )
        },
        containerColor = senaColors().background,
    ) { paddingValues ->
        val reporte = bug
        if (reporte == null) {
            val mensajeError = detalleError
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                if (mensajeError != null) {
                    SenaErrorState(
                        message = mensajeError,
                        onRetry = { if (bugIdNum > 0) bugReportsViewModel.cargarDetalle(bugIdNum) },
                    )
                } else {
                    SenaLoadingState()
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Brush.verticalGradient(colors = listOf(senaColors().header, senaColors().green)))
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .offset(y = (-40).dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SenaCard(elevation = 8.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#${reporte.id}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = senaColors().textLight)
                            SenaStatusBadge(status = reporte.statusDisplay)
                        }
                        Text(
                            reporte.titulo.ifBlank { "Solicitud de soporte" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = senaColors().text,
                        )
                        HorizontalDivider(color = senaColors().borderSoft)
                        BugDetailRow(Icons.Default.Person, "Reportado por", reporte.reporterName ?: "—")
                        BugDetailRow(Icons.Default.BugReport, "Tipo", reporte.typeDisplay)
                        BugDetailRow(Icons.Default.Event, "Fecha", formatearFechaLocal(reporte.fecha ?: reporte.createdAt))
                        reporte.numeroFicha?.takeIf { it.isNotBlank() }?.let {
                            BugDetailRow(Icons.Default.Numbers, "Ficha", it)
                        }
                        reporte.motivo?.takeIf { it.isNotBlank() }?.let {
                            BugDetailRow(Icons.Default.Info, "Motivo", it)
                        }
                    }
                }

                SenaSectionHeader(title = "Descripción")
                SenaCard {
                    Text(
                        reporte.descripcion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = senaColors().textSecondary,
                        lineHeight = 22.sp,
                    )
                }

                SenaSectionHeader(title = "Gestión")
                SenaCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Estado", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            BugReportStatus.entries.forEach { estado ->
                                SenaChip(
                                    text = etiquetaEstado(estado),
                                    color = colorEstado(estado),
                                    isSelected = estadoSeleccionado == estado,
                                    onClick = { estadoSeleccionado = estado },
                                )
                            }
                        }

                        SenaTextField(
                            value = respuesta,
                            onValueChange = { respuesta = it },
                            label = "Respuesta al solicitante (opcional)",
                            placeholder = "Describe la solución o el motivo del rechazo...",
                            singleLine = false,
                            minLines = 3,
                        )

                        if (reporte.respuesta?.isNotBlank() == true) {
                            SenaAlertBanner(
                                title = "Respuesta enviada",
                                message = reporte.respuesta,
                                icon = Icons.Default.MarkChatRead,
                                color = senaColors().info,
                            )
                        }

                        SenaButton(
                            text = "GUARDAR CAMBIOS",
                            onClick = {
                                bugReportsViewModel.cambiarEstado(
                                    id = reporte.id,
                                    estado = estadoSeleccionado,
                                    respuesta = respuesta.trim().ifBlank { null },
                                )
                            },
                            icon = Icons.Default.Save,
                        )
                    }
                }

                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

private fun etiquetaEstado(estado: BugReportStatus): String = when (estado) {
    BugReportStatus.PENDIENTE -> "Pendiente"
    BugReportStatus.EN_REVISION -> "En revisión"
    BugReportStatus.RESUELTO -> "Resuelto"
    BugReportStatus.CERRADO -> "Cerrado"
    BugReportStatus.RECHAZADO -> "Rechazado"
}

@Composable
private fun colorEstado(estado: BugReportStatus) = when (estado) {
    BugReportStatus.RESUELTO -> senaColors().success
    BugReportStatus.RECHAZADO -> senaColors().danger
    BugReportStatus.EN_REVISION -> senaColors().info
    BugReportStatus.CERRADO -> senaColors().textLight
    BugReportStatus.PENDIENTE -> senaColors().warning
}

@Composable
fun BugDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = senaColors().textMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = senaColors().textLight,
            modifier = Modifier.widthIn(min = 88.dp, max = 110.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = senaColors().text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

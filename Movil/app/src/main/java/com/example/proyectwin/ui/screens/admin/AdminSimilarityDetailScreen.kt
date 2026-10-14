package com.example.proyectwin.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.Comment
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AdminSimilarityAction
import com.example.proyectwin.ui.viewmodel.AdminSimilarityDetailViewModel
import com.example.proyectwin.ui.viewmodel.AdminSimilarityUiState

/**
 * Detalle admin de un par de similitud: ambas propuestas, observaciones de cada
 * una y eliminación del par (el motor puede volver a detectarlo al recalcular).
 */
@Composable
fun AdminSimilarityDetailScreen(
    similarityId: String = "",
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    adminSimilarityViewModel: AdminSimilarityDetailViewModel = hiltViewModel(),
) {
    val uiState by adminSimilarityViewModel.uiState.collectAsState()
    val accion by adminSimilarityViewModel.accion.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmarEliminar by remember { mutableStateOf(false) }
    var comentarioProyecto by remember { mutableIntStateOf(0) }
    var nuevoComentario by remember { mutableStateOf("") }

    LaunchedEffect(similarityId) {
        similarityId.toIntOrNull()?.let { adminSimilarityViewModel.load(it) }
    }

    LaunchedEffect(accion) {
        when (val estado = accion) {
            is AdminSimilarityAction.Success -> adminSimilarityViewModel.resetAccion()
            is AdminSimilarityAction.Error -> {
                snackbarHostState.showSnackbar(estado.message)
                adminSimilarityViewModel.resetAccion()
            }
            is AdminSimilarityAction.Deleted -> {
                adminSimilarityViewModel.resetAccion()
                onBack()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SenaTopBar(
                title = "Detalle de Similitud",
                onBack = onBack,
                showProfile = true,
                showNotifications = true,
            )
        },
        containerColor = senaColors().background,
    ) { paddingValues ->
        when (val estado = uiState) {
            is AdminSimilarityUiState.Loading ->
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is AdminSimilarityUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estado.message, onRetry = { adminSimilarityViewModel.recargar() })
            }
            is AdminSimilarityUiState.Success -> {
                val data = estado.data
                val par = data.similarity

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SenaPageHeader(
                        title = "Similitud #${par.id}",
                        subtitle = "Coincidencia ${par.estadoDisplay.lowercase()} · ${par.similitudPercent}",
                        icon = Icons.Default.Compare,
                    )

                    if (!par.vigente) {
                        SenaAlertBanner(
                            title = "Evidencia histórica",
                            message = "Este par quedó archivado (vigente = false): es evidencia de una versión anterior.",
                            icon = Icons.Default.History,
                            color = senaColors().warning,
                        )
                    }

                    PanelProyectoAdmin(
                        etiqueta = "Propuesta A",
                        proyecto = par.project1,
                        comentarios = data.comentarios1,
                        onVerProyecto = {
                            par.project1?.let {
                                onNavigate(AppNavigation.ADMIN_PROJECT_DETAIL.replace("{projectId}", it.id.toString()))
                            }
                        },
                    )

                    PanelProyectoAdmin(
                        etiqueta = "Propuesta B",
                        proyecto = par.project2,
                        comentarios = data.comentarios2,
                        onVerProyecto = {
                            par.project2?.let {
                                onNavigate(AppNavigation.ADMIN_PROJECT_DETAIL.replace("{projectId}", it.id.toString()))
                            }
                        },
                    )

                    if (data.relacionadas.isNotEmpty()) {
                        SenaSectionHeader(title = "Otras coincidencias (${data.relacionadas.size})")
                        SenaCard(elevation = 1.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                data.relacionadas.forEach { relacionada ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                relacionada.project1Title ?: "Propuesta",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = senaColors().text,
                                            )
                                            Text(
                                                relacionada.project2Title ?: "Propuesta",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = senaColors().textSecondary,
                                            )
                                        }
                                        Text(
                                            relacionada.similitudPercent,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = senaColors().green,
                                        )
                                        IconButton(
                                            onClick = {
                                                onNavigate(
                                                    AppNavigation.ADMIN_SIMILARITY_DETAIL
                                                        .replace("{similarityId}", relacionada.id.toString()),
                                                )
                                            },
                                        ) {
                                            Icon(Icons.Default.ChevronRight, contentDescription = "Ver")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SenaSectionHeader(title = "Agregar observación")
                    SenaCard(elevation = 1.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0, 1).forEach { indice ->
                                    val proyecto = if (indice == 0) par.project1 else par.project2
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SenaChip(
                                            text = if (indice == 0) "A" else "B",
                                            color = senaColors().green,
                                            isSelected = comentarioProyecto == indice,
                                            onClick = { comentarioProyecto = indice },
                                        )
                                        Text(
                                            proyecto?.title ?: "Propuesta ${if (indice == 0) "A" else "B"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = senaColors().textLight,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            SenaTextField(
                                value = nuevoComentario,
                                onValueChange = { nuevoComentario = it },
                                label = "Observación",
                                placeholder = "Escribe una observación para la propuesta seleccionada...",
                                singleLine = false,
                                minLines = 2,
                            )
                            SenaButton(
                                text = "PUBLICAR OBSERVACIÓN",
                                onClick = {
                                    val proyectoId = (if (comentarioProyecto == 0) par.project1 else par.project2)?.id
                                    if (proyectoId != null && nuevoComentario.isNotBlank()) {
                                        adminSimilarityViewModel.comentar(proyectoId, nuevoComentario.trim())
                                        nuevoComentario = ""
                                    }
                                },
                                enabled = nuevoComentario.isNotBlank() && accion !is AdminSimilarityAction.Loading,
                                isLoading = accion is AdminSimilarityAction.Loading,
                                icon = Icons.AutoMirrored.Filled.Send,
                            )
                        }
                    }

                    SenaButton(
                        text = "ELIMINAR PAR DE SIMILITUD",
                        onClick = { confirmarEliminar = true },
                        isPrimary = false,
                        icon = Icons.Default.Delete,
                        containerColor = senaColors().danger10,
                    )

                    Spacer(Modifier.height(40.dp))
                }

                if (confirmarEliminar) {
                    AlertDialog(
                        onDismissRequest = { confirmarEliminar = false },
                        title = { Text("Eliminar par") },
                        text = { Text("El par se elimina de la base; el motor podría volver a detectarlo en un recálculo.") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmarEliminar = false
                                adminSimilarityViewModel.eliminar()
                            }) { Text("Eliminar", color = senaColors().danger) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmarEliminar = false }) { Text("Cancelar") }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelProyectoAdmin(
    etiqueta: String,
    proyecto: Project?,
    comentarios: List<Comment>,
    onVerProyecto: () -> Unit,
) {
    SenaSectionHeader(title = etiqueta)
    SenaCard(elevation = 1.dp) {
        if (proyecto == null) {
            Text("Propuesta no disponible.", color = senaColors().textSecondary)
            return@SenaCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    proyecto.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                    modifier = Modifier.weight(1f),
                )
                SenaStatusBadge(status = proyecto.estado)
            }
            Text(
                "${proyecto.studentName ?: "Sin autor"} · ${proyecto.programa ?: "Sin programa"}",
                style = MaterialTheme.typography.bodySmall,
                color = senaColors().textSecondary,
            )
            if (proyecto.description.isNotBlank()) {
                Text(
                    proyecto.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = senaColors().textSecondary,
                    maxLines = 4,
                )
            }
            SenaButton(
                text = "VER PROPUESTA",
                onClick = onVerProyecto,
                isPrimary = false,
                icon = Icons.Default.OpenInNew,
            )
            HorizontalDivider(color = senaColors().borderSoft)
            Text(
                "Observaciones (${comentarios.size})",
                style = MaterialTheme.typography.labelSmall,
                color = senaColors().textLight,
            )
            if (comentarios.isEmpty()) {
                Text("Sin observaciones.", style = MaterialTheme.typography.bodySmall, color = senaColors().textSecondary)
            } else {
                comentarios.take(6).forEach { comentario ->
                    Column {
                        Text(
                            comentario.autorNombre ?: "Usuario",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = senaColors().green,
                        )
                        Text(comentario.texto, style = MaterialTheme.typography.bodySmall, color = senaColors().textSecondary)
                    }
                }
                if (comentarios.size > 6) {
                    Text(
                        "+${comentarios.size - 6} observaciones más en la propuesta.",
                        style = MaterialTheme.typography.labelSmall,
                        color = senaColors().textLight,
                    )
                }
            }
        }
    }
}

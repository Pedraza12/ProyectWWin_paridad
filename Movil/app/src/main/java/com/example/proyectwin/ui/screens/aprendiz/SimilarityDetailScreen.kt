package com.example.proyectwin.ui.screens.aprendiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.Similarity
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.SimilarityDetailUiState
import com.example.proyectwin.ui.viewmodel.SimilarityDetailViewModel

/**
 * Detalle de una similitud: propuesta propia vs contraparte (solo lectura).
 * La autorización la decide el backend; si responde 403 se muestra el mensaje.
 */
@Composable
fun SimilarityDetailScreen(
    similarityId: String = "",
    detalleProyectoRoute: String = AppNavigation.APRENDIZ_DETAIL,
    /** Ruta del detalle de similitud del rol actual (no cruza de grafo). */
    similarityRoute: String = AppNavigation.APRENDIZ_SIMILARITY,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    similarityDetailViewModel: SimilarityDetailViewModel = hiltViewModel(),
) {
    val uiState by similarityDetailViewModel.uiState.collectAsState()

    LaunchedEffect(similarityId) {
        similarityId.toIntOrNull()?.let { similarityDetailViewModel.load(it) }
    }

    Scaffold(
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
            is SimilarityDetailUiState.Loading ->
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is SimilarityDetailUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estado.message, onRetry = { similarityDetailViewModel.retry() })
            }
            is SimilarityDetailUiState.Success -> {
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
                        title = par.similitudPercent,
                        subtitle = "Similitud entre propuestas · ${par.estadoDisplay}",
                        icon = Icons.Default.Compare,
                    )

                    if (!par.vigente) {
                        SenaAlertBanner(
                            title = "Evidencia histórica",
                            message = "Esta coincidencia pertenece a una versión anterior de la propuesta (rechazada o archivada). No es una coincidencia vigente.",
                            icon = Icons.Default.History,
                            color = senaColors().warning,
                        )
                    } else {
                        SenaAlertBanner(
                            title = "Coincidencia vigente",
                            message = "Revisa la propuesta contraparte para identificar y diferenciar tu contenido.",
                            icon = Icons.Default.Search,
                            color = senaColors().info,
                        )
                    }

                    if (data.miProyecto != null) {
                        SenaSectionHeader(title = if (data.ambasMias) "Tu propuesta (A)" else "Tu propuesta")
                        PanelPropuesta(
                            proyecto = data.miProyecto,
                            onClick = {
                                onNavigate(detalleProyectoRoute.replace("{projectId}", data.miProyecto.id.toString()))
                            },
                        )
                    }

                    if (data.contraparte != null) {
                        SenaSectionHeader(
                            title = if (data.ambasMias) "Tu propuesta (B)" else "Contraparte · solo lectura",
                        )
                        PanelPropuesta(
                            proyecto = data.contraparte,
                            onClick = {
                                onNavigate(detalleProyectoRoute.replace("{projectId}", data.contraparte.id.toString()))
                            },
                        )
                    }

                    if (data.miProyecto == null && data.contraparte == null) {
                        SenaSectionHeader(title = "Propuestas comparadas")
                        PanelPropuesta(
                            proyecto = par.project1,
                            onClick = {
                                par.project1?.let {
                                    onNavigate(detalleProyectoRoute.replace("{projectId}", it.id.toString()))
                                }
                            },
                        )
                        PanelPropuesta(
                            proyecto = par.project2,
                            onClick = {
                                par.project2?.let {
                                    onNavigate(detalleProyectoRoute.replace("{projectId}", it.id.toString()))
                                }
                            },
                        )
                    }

                    SenaSectionHeader(title = "Otras coincidencias relacionadas (${data.relacionadas.size})")
                    SenaCard(elevation = 1.dp) {
                        if (data.relacionadas.isEmpty()) {
                            Text("No hay otras coincidencias entre estas propuestas.", color = senaColors().textSecondary)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                data.relacionadas.forEach { relacionada: Similarity ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                relacionada.project1Title ?: "Propuesta",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
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
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                onNavigate(
                                                    similarityRoute
                                                        .replace("{similarityId}", relacionada.id.toString()),
                                                )
                                            },
                                        ) {
                                            Icon(Icons.Default.ChevronRight, contentDescription = "Ver", tint = senaColors().textMuted)
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
    }
}

@Composable
private fun PanelPropuesta(proyecto: Project?, onClick: () -> Unit) {
    if (proyecto == null) {
        SenaCard(elevation = 0.5.dp) {
            Text("Propuesta no disponible.", color = senaColors().textSecondary)
        }
        return
    }
    SenaCard(elevation = 0.5.dp, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                "${proyecto.studentName ?: "Autor no visible"} · ${proyecto.areaAplicacion.ifBlank { "Área no definida" }}",
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
            Text(
                "Toca para ver el detalle completo",
                style = MaterialTheme.typography.labelSmall,
                color = senaColors().green,
            )
        }
    }
}

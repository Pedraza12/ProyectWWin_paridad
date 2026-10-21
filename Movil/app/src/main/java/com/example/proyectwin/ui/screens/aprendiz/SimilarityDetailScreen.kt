package com.example.proyectwin.ui.screens.aprendiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.ProjectStatus
import com.example.proyectwin.data.model.Similarity
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.util.formatearFechaLocal
import com.example.proyectwin.ui.viewmodel.EstadoObservaciones
import com.example.proyectwin.ui.viewmodel.MotorConfigViewModel
import com.example.proyectwin.ui.viewmodel.SimilarityDetailUiState
import com.example.proyectwin.ui.viewmodel.SimilarityDetailViewModel
import kotlin.math.roundToInt

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
    motorConfigViewModel: MotorConfigViewModel = hiltViewModel(),
) {
    val uiState by similarityDetailViewModel.uiState.collectAsState()
    val refrescando by similarityDetailViewModel.refrescando.collectAsState()
    val motorState by motorConfigViewModel.uiState.collectAsState()

    LaunchedEffect(similarityId) {
        similarityId.toIntOrNull()?.let { similarityDetailViewModel.load(it) }
    }
    LaunchedEffect(Unit) { motorConfigViewModel.cargar() }

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

                // Observaciones: la web (`DetalleSimilitud`) muestra solo las de la
                // propuesta propia del par; sin propuesta propia (instructor) se
                // muestra cada propuesta por separado para no mezclar observaciones
                // de proyectos distintos.
                val seccionesObservaciones: List<Pair<String, Project>> = data.miProyecto?.let { mios ->
                    listOf("Observaciones de tu propuesta" to mios)
                } ?: listOfNotNull(
                    par.project1?.let { "Observaciones · ${it.title}" to it },
                    par.project2?.let { "Observaciones · ${it.title}" to it },
                )

                SenaPullRefresh(
                    isRefreshing = refrescando,
                    onRefresh = { similarityDetailViewModel.refrescar() },
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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

                    // --- Paridad web: umbral real + comparación A/B ---
                    TarjetaUmbral(
                        umbralPct = motorState.umbralPct,
                        cargando = motorState.cargando,
                        error = motorState.error,
                    )
                    TarjetaComparacion(
                        porcentaje = (par.similitud * 100).roundToInt().coerceIn(0, 100),
                    )
                    NotaSimilitud()

                    if (data.miProyecto != null) {
                        SenaSectionHeader(title = "Tu propuesta (A)")
                        PanelPropuesta(
                            proyecto = data.miProyecto,
                            onClick = {
                                onNavigate(detalleProyectoRoute.replace("{projectId}", data.miProyecto.id.toString()))
                            },
                        )
                    }

                    if (data.contraparte != null) {
                        SenaSectionHeader(
                            title = if (data.ambasMias) "Tu propuesta (B)" else "Contraparte (B) · solo lectura",
                        )
                        PanelPropuesta(
                            proyecto = data.contraparte,
                            onClick = {
                                onNavigate(detalleProyectoRoute.replace("{projectId}", data.contraparte.id.toString()))
                            },
                        )
                    }

                    if (data.miProyecto == null && data.contraparte == null) {
                        // Sin propuesta propia (instructor): A = pendiente, B = aprobada,
                        // igual que la perspectiva de la web.
                        val aprobado = ProjectStatus.APROBADO.value
                        val p1Aprobada = par.project1?.estado == aprobado
                        val p2Aprobada = par.project2?.estado == aprobado
                        val proyectoA = if (p1Aprobada && !p2Aprobada) par.project2 else par.project1
                        val proyectoB = if (proyectoA?.id == par.project1?.id) par.project2 else par.project1

                        SenaSectionHeader(title = "Propuestas comparadas")
                        SenaSectionHeader(title = "Propuesta A")
                        PanelPropuesta(
                            proyecto = proyectoA,
                            onClick = {
                                proyectoA?.let {
                                    onNavigate(detalleProyectoRoute.replace("{projectId}", it.id.toString()))
                                }
                            },
                        )
                        SenaSectionHeader(title = "Propuesta B")
                        PanelPropuesta(
                            proyecto = proyectoB,
                            onClick = {
                                proyectoB?.let {
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

                    seccionesObservaciones.forEach { (titulo, proyecto) ->
                        val estadoObs = data.observaciones[proyecto.id]
                        val conConteo = estadoObs != null && !estadoObs.cargando && estadoObs.error == null
                        ObservacionesPropuesta(
                            titulo = if (conConteo) "$titulo (${estadoObs.comentarios.size})" else titulo,
                            estado = estadoObs,
                            onRetry = { similarityDetailViewModel.reintentarObservaciones(proyecto.id) },
                        )
                    }

                    Spacer(Modifier.height(40.dp))
                }
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

/** Observaciones de UNA propuesta del par (solo lectura, con estados). */
@Composable
private fun ObservacionesPropuesta(
    titulo: String,
    estado: EstadoObservaciones?,
    onRetry: () -> Unit,
) {
    val actual = estado ?: EstadoObservaciones()
    SenaSectionHeader(title = titulo)
    SenaCard(elevation = 1.dp) {
        when {
            actual.cargando -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = senaColors().green,
                    )
                    Text(
                        "Cargando observaciones…",
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().textSecondary,
                    )
                }
            }

            actual.error != null -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        actual.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().danger,
                    )
                    TextButton(onClick = onRetry) {
                        Text("Reintentar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            actual.comentarios.isEmpty() -> SenaEmptyState(
                message = "Aún no hay observaciones en esta propuesta.",
                icon = Icons.AutoMirrored.Filled.Chat,
            )

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    actual.comentarios.forEach { comentario ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SenaAvatar(
                                    fotoBase64 = comentario.autorFoto,
                                    nombre = comentario.autorNombre ?: "Usuario",
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        comentario.autorNombre ?: "Usuario",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        formatearFechaLocal(comentario.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = senaColors().textLight,
                                    )
                                }
                            }
                            Text(
                                comentario.texto,
                                style = MaterialTheme.typography.bodyMedium,
                                color = senaColors().textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

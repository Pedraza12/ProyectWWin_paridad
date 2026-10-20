package com.example.proyectwin.ui.screens.instructor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.ProjectStatus
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.util.formatearFechaHoraLocal
import com.example.proyectwin.ui.viewmodel.PropuestaRevision
import com.example.proyectwin.ui.viewmodel.RevisionUiState
import com.example.proyectwin.ui.viewmodel.RevisionViewModel

/**
 * Revisión de propuestas del instructor: excluye borradores (como React) y
 * aprobar/rechazar se realiza en el detalle, donde también viven comentarios,
 * similitudes e historial.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisionPropuestasScreen(
    onBack: () -> Unit,
    onProjectDetail: (Int) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    revisionViewModel: RevisionViewModel = hiltViewModel(),
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("Pendiente") }
    val uiState by revisionViewModel.uiState.collectAsState()
    val statuses = listOf("Todos", "Pendiente", "Aprobado", "Rechazado")

    // Al regresar del detalle de una propuesta no se pierde la lista: el VM
    // refresca en silencio y conserva la posición del scroll.
    LaunchedEffect(Unit) { revisionViewModel.iniciar() }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "ProyecTwin",
                onBack = onBack,
                showProfile = true,
                showNotifications = true,
            )
        },
        containerColor = senaColors().background,
        bottomBar = bottomBar,
    ) { paddingValues ->
        when (val estado = uiState) {
            is RevisionUiState.Loading ->
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is RevisionUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estado.message, onRetry = { revisionViewModel.retry() })
            }
            is RevisionUiState.Success -> {
                val propuestas = estado.propuestas.filter { propuesta ->
                    val matchesEstado = when (selectedStatus) {
                        "Todos" -> true
                        "Pendiente" -> propuesta.proyecto.estado == ProjectStatus.PENDIENTE.value
                        "Aprobado" -> propuesta.proyecto.estado == ProjectStatus.APROBADO.value
                        "Rechazado" -> propuesta.proyecto.estado == ProjectStatus.RECHAZADO.value
                        else -> true
                    }
                    val matchesSearch = propuesta.proyecto.title.contains(searchQuery, ignoreCase = true) ||
                        propuesta.proyecto.studentName.orEmpty().contains(searchQuery, ignoreCase = true)
                    matchesEstado && matchesSearch
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item {
                        SenaPageHeader(
                            title = "Revisión de Propuestas",
                            subtitle = "Evalúa las propuestas enviadas por los aprendices.",
                            icon = Icons.AutoMirrored.Filled.List,
                        )
                    }

                    item {
                        SenaFilterBar(title = "Filtros de revisión") {
                            SenaTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = "",
                                placeholder = "Buscar por proyecto o aprendiz...",
                                leadingIcon = Icons.Default.Search,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                statuses.forEach { status ->
                                    SenaChip(
                                        text = status,
                                        color = when (status) {
                                            "Aprobado" -> senaColors().success
                                            "Pendiente" -> senaColors().warning
                                            "Rechazado" -> senaColors().danger
                                            else -> senaColors().green
                                        },
                                        isSelected = selectedStatus == status,
                                        onClick = { selectedStatus = status },
                                    )
                                }
                            }
                        }
                    }

                    if (propuestas.isEmpty()) {
                        item {
                            SenaEmptyState(
                                message = "No hay propuestas que coincidan con los filtros seleccionados.",
                                icon = Icons.Default.SearchOff,
                            )
                        }
                    } else {
                        items(propuestas, key = { it.proyecto.id }) { propuesta ->
                            ProposalReviewCard(
                                propuesta = propuesta,
                                onDetailClick = { onProjectDetail(propuesta.proyecto.id) },
                            )
                        }
                    }

                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

@Composable
fun ProposalReviewCard(
    propuesta: PropuestaRevision,
    onDetailClick: () -> Unit,
) {
    val proposal: Project = propuesta.proyecto
    SenaCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SenaStatusBadge(status = proposal.statusDisplay)
                Text(
                    formatearFechaHoraLocal(proposal.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = senaColors().textLight,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    proposal.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = senaColors().green, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        proposal.studentName ?: "Sin aprendiz",
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().textSecondary,
                    )
                }
            }

            if (propuesta.maxSimilitud > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = senaColors().danger.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, senaColors().danger.copy(alpha = 0.2f)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = senaColors().danger, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "${(propuesta.maxSimilitud * 100).toInt()}% de similitud detectada",
                            style = MaterialTheme.typography.labelSmall,
                            color = senaColors().danger,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Text(
                text = proposal.description,
                style = MaterialTheme.typography.bodySmall,
                color = senaColors().textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            SenaButton(
                text = "Revisar",
                onClick = onDetailClick,
                icon = Icons.Default.Visibility,
            )
        }
    }
}

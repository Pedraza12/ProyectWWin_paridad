package com.example.proyectwin.ui.screens.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.model.ProjectStatus
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AdminUiState
import com.example.proyectwin.ui.viewmodel.AdminViewModel

/** Proyectos (admin): listado real con filtros y acceso al detalle/intervención. */
@Composable
fun AdminProjectsScreen(
    onBack: () -> Unit,
    onProjectDetail: (Int) -> Unit,
    adminViewModel: AdminViewModel = hiltViewModel(),
) {
    var busqueda by remember { mutableStateOf("") }
    var estadoFiltro by remember { mutableStateOf("Todos") }
    val adminState by adminViewModel.uiState.collectAsState()
    val filtros = listOf("Todos", "Borrador", "En revisión", "Aprobado", "Rechazado")

    val proyectos = (adminState as? AdminUiState.Success)?.projects.orEmpty().filter { proyecto ->
        val porEstado = when (estadoFiltro) {
            "Borrador" -> proyecto.estado == ProjectStatus.BORRADOR.value
            "En revisión" -> proyecto.estado == ProjectStatus.PENDIENTE.value
            "Aprobado" -> proyecto.estado == ProjectStatus.APROBADO.value
            "Rechazado" -> proyecto.estado == ProjectStatus.RECHAZADO.value
            else -> true
        }
        val porTexto = proyecto.title.contains(busqueda, true) ||
            proyecto.studentName.orEmpty().contains(busqueda, true)
        porEstado && porTexto
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "Proyectos",
                onBack = onBack,
                showProfile = true,
                showNotifications = true,
            )
        },
        containerColor = senaColors().background,
    ) { paddingValues ->
        when (val estado = adminState) {
            is AdminUiState.Loading ->
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is AdminUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estado.message, onRetry = { adminViewModel.refresh() })
            }
            is AdminUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        SenaPageHeader(
                            title = "Proyectos",
                            subtitle = "Supervisión e intervención de propuestas (${estado.projects.size}).",
                            icon = Icons.Default.FolderOpen,
                        )
                    }
                    item {
                        SenaCard(elevation = 1.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SenaTextField(
                                    value = busqueda,
                                    onValueChange = { busqueda = it },
                                    label = "",
                                    placeholder = "Buscar por título o aprendiz...",
                                    leadingIcon = Icons.Default.Search,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    filtros.forEach { filtro ->
                                        SenaChip(
                                            text = filtro,
                                            color = when (filtro) {
                                                "Aprobado" -> senaColors().success
                                                "En revisión" -> senaColors().warning
                                                "Rechazado" -> senaColors().danger
                                                "Borrador" -> senaColors().accent
                                                else -> senaColors().green
                                            },
                                            isSelected = estadoFiltro == filtro,
                                            onClick = { estadoFiltro = filtro },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (proyectos.isEmpty()) {
                        item { SenaEmptyState(message = "No hay proyectos que coincidan.", icon = Icons.Default.SearchOff) }
                    } else {
                        items(proyectos, key = { it.id }) { proyecto ->
                            SenaCard(elevation = 0.5.dp, onClick = { onProjectDetail(proyecto.id) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            proyecto.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = senaColors().text,
                                            modifier = Modifier.weight(1f),
                                        )
                                        SenaStatusBadge(status = proyecto.estado)
                                    }
                                    Text(
                                        "${proyecto.studentName ?: "Sin aprendiz"} · ${proyecto.programa ?: "Sin programa"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = senaColors().textLight,
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

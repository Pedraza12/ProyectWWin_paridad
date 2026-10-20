package com.example.proyectwin.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.util.formatearFechaLocal
import com.example.proyectwin.ui.viewmodel.AdminFichaDetailUiState
import com.example.proyectwin.ui.viewmodel.AdminFichaDetailViewModel

/**
 * Detalle admin de una ficha: información, roster (solo lectura) y propuestas
 * de la ficha. Replica el alcance de la vista web del admin; la gestión
 * (editar/credenciales/eliminar) sigue en el listado para no duplicarla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFichaDetailScreen(
    fichaId: String = "",
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    adminFichaDetailViewModel: AdminFichaDetailViewModel = hiltViewModel(),
) {
    val uiState by adminFichaDetailViewModel.uiState.collectAsState()
    val refrescando by adminFichaDetailViewModel.refrescando.collectAsState()

    LaunchedEffect(fichaId) {
        fichaId.toIntOrNull()?.let { adminFichaDetailViewModel.load(it) }
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "Detalle de ficha",
                onBack = onBack,
                showProfile = true,
                showNotifications = true,
            )
        },
        containerColor = senaColors().background,
    ) { paddingValues ->
        when (val estado = uiState) {
            is AdminFichaDetailUiState.Loading ->
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is AdminFichaDetailUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estado.message, onRetry = { adminFichaDetailViewModel.recargar() })
            }
            is AdminFichaDetailUiState.Success -> {
                val data = estado.data
                SenaPullRefresh(
                    isRefreshing = refrescando,
                    onRefresh = { adminFichaDetailViewModel.refrescar() },
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            SenaPageHeader(
                                title = data.ficha.nombre ?: data.ficha.programa.ifBlank { "Ficha de formación" },
                                subtitle = "Código ${data.ficha.codigo} · N° ${data.ficha.numero ?: "—"} · " +
                                    data.ficha.programa.ifBlank { "Sin programa" },
                                icon = Icons.Default.Groups,
                            )
                        }

                        item {
                            PanelInformacionFicha(
                                ficha = data.ficha,
                                propuestasCount = data.propuestas.size,
                            )
                        }

                        item { SenaSectionHeader(title = "Aprendices (${data.ficha.estudiantes.size})") }
                        if (data.ficha.estudiantes.isEmpty()) {
                            item {
                                SenaEmptyState(
                                    message = "Sin aprendices en esta ficha. Usa \"Editar\" en el listado para agregarlos.",
                                    icon = Icons.Default.Groups,
                                )
                            }
                        } else {
                            items(data.ficha.estudiantes, key = { it.id }) { aprendiz ->
                                FilaAprendiz(
                                    aprendiz = aprendiz,
                                    onClick = {
                                        onNavigate(AppNavigation.ADMIN_USER_DETAIL.replace("{userId}", aprendiz.id.toString()))
                                    },
                                )
                            }
                        }

                        item { SenaSectionHeader(title = "Propuestas (${data.propuestas.size})") }
                        when {
                            data.propuestasError -> item {
                                Text(
                                    "No se pudieron cargar las propuestas de la ficha.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = senaColors().textSecondary,
                                )
                            }
                            data.propuestas.isEmpty() -> item {
                                SenaEmptyState(
                                    message = "Esta ficha aún no tiene propuestas asociadas.",
                                    icon = Icons.Default.FolderOpen,
                                )
                            }
                            else -> items(data.propuestas, key = { it.id }) { propuesta ->
                                FilaPropuesta(
                                    propuesta = propuesta,
                                    onClick = {
                                        onNavigate(
                                            AppNavigation.ADMIN_PROJECT_DETAIL.replace("{projectId}", propuesta.id.toString()),
                                        )
                                    },
                                )
                            }
                        }

                        item { Spacer(Modifier.height(60.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelInformacionFicha(
    ficha: Ficha,
    propuestasCount: Int,
) {
    SenaCard(elevation = 1.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Información de la ficha",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                    modifier = Modifier.weight(1f),
                )
                SenaStatusBadge(status = ficha.statusDisplay)
            }

            HorizontalDivider(color = senaColors().borderSoft)

            DetailRow(Icons.Default.School, "Programa de formación", ficha.programa.ifBlank { "Sin programa" })
            DetailRow(Icons.Default.Badge, "Número de ficha", ficha.numero ?: "—")
            DetailRow(Icons.Default.Event, "Creada", formatearFechaLocal(ficha.createdAt))
            DetailRow(Icons.Default.Groups, "Aprendices", ficha.estudiantes.size.toString())
            DetailRow(Icons.Default.FolderOpen, "Propuestas", propuestasCount.toString())
            // Sin enlace al usuario: `instructorId` es la fila de `instructors`,
            // no el id de `general_users` que espera el detalle de usuario.
            DetailRow(Icons.Default.Person, "Instructor a cargo", ficha.instructorName ?: "Sin asignar")
        }
    }
}

@Composable
private fun FilaAprendiz(aprendiz: GeneralUser, onClick: () -> Unit) {
    SenaCard(elevation = 0.5.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SenaAvatar(
                fotoBase64 = aprendiz.fotoPerfil,
                nombre = aprendiz.name,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    aprendiz.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                )
                Text(
                    aprendiz.email,
                    style = MaterialTheme.typography.labelSmall,
                    color = senaColors().textLight,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Ver aprendiz",
                tint = senaColors().textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FilaPropuesta(propuesta: Project, onClick: () -> Unit) {
    SenaCard(elevation = 0.5.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    propuesta.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${propuesta.studentName ?: "Sin autor"} · ${formatearFechaLocal(propuesta.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = senaColors().textLight,
                )
            }
            Spacer(Modifier.width(12.dp))
            SenaStatusBadge(status = propuesta.statusDisplay)
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Ver propuesta",
                tint = senaColors().textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

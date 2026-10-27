package com.example.proyectwin.ui.screens.aprendiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.UserRole
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.CompaneroUiState
import com.example.proyectwin.ui.viewmodel.DetalleCompaneroViewModel

/**
 * Perfil público de un compañero o instructor: datos básicos, su ficha y las
 * propuestas que el backend autoriza ver al solicitante.
 *
 * `tituloPagina = "Mi Instructor"` reutiliza la misma vista para el acceso
 * directo desde "Mi Ficha", igual que la ruta web `/aprendiz/perfil-instructor`.
 */
@Composable
fun DetalleCompaneroScreen(
    userId: String = "",
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    tituloPagina: String = "Perfil",
    detalleProyectoRoute: String = AppNavigation.APRENDIZ_DETAIL,
    companeroViewModel: DetalleCompaneroViewModel = hiltViewModel(),
) {
    val uiState by companeroViewModel.uiState.collectAsState()

    LaunchedEffect(userId) {
        userId.toIntOrNull()?.let { companeroViewModel.load(it) }
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "ProyecTwin",
                onBack = onBack,
                showProfile = false,
                showNotifications = false,
            )
        },
        containerColor = senaColors().background,
    ) { paddingValues ->
        when (val estado = uiState) {
            is CompaneroUiState.Loading -> SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is CompaneroUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estado.message, onRetry = { companeroViewModel.retry() })
            }
            is CompaneroUiState.Success -> {
                val data = estado.data
                val esInstructor = data.usuario.userRole == UserRole.INSTRUCTOR
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item {
                        SenaPageHeader(
                            title = if (tituloPagina == "Mi Instructor") {
                                "Mi Instructor"
                            } else {
                                data.usuario.name
                            },
                            subtitle = if (tituloPagina == "Mi Instructor") {
                                "Conoce a quien acompaña tu proceso de formación"
                            } else {
                                data.usuario.roleDisplayName
                            },
                            icon = Icons.Default.Person,
                        )
                    }

                    item {
                        SenaCard(elevation = 1.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SenaAvatar(
                                    fotoBase64 = data.usuario.fotoPerfil,
                                    nombre = data.usuario.name,
                                    modifier = Modifier.size(96.dp),
                                )
                                Text(
                                    data.usuario.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = senaColors().text,
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = senaColors().green.copy(alpha = 0.1f),
                                ) {
                                    Text(
                                        data.usuario.roleDisplayName,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = senaColors().green,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                if (data.usuario.email.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Email, contentDescription = null, tint = senaColors().textLight, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(data.usuario.email, style = MaterialTheme.typography.bodySmall, color = senaColors().textSecondary)
                                    }
                                }
                            }
                        }
                    }

                    data.ficha?.let { ficha ->
                        item {
                            SenaSectionHeader(
                                title = if (esInstructor) "Ficha en común" else "Ficha",
                            )
                            SenaCard(elevation = 1.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        ficha.nombre ?: ficha.programa.ifBlank { "Ficha de formación" },
                                        fontWeight = FontWeight.Bold,
                                        color = senaColors().text,
                                    )
                                    Text(
                                        "Código: ${ficha.codigo} · ${ficha.statusDisplay}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = senaColors().textSecondary,
                                    )
                                    if (esInstructor) {
                                        Text(
                                            "Programa: ${ficha.programa.ifBlank { "Sin programa" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = senaColors().textSecondary,
                                        )
                                    } else if (ficha.instructorName != null) {
                                        Text(
                                            "Instructor: ${ficha.instructorName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = senaColors().textSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SenaSectionHeader(
                            title = if (esInstructor) {
                                "Propuestas"
                            } else {
                                "Propuestas (${data.proyectos.size})"
                            },
                        )
                    }

                    if (data.proyectos.isEmpty()) {
                        item {
                            SenaEmptyState(
                                message = if (esInstructor) {
                                    "Las propuestas de los instructores no se muestran en esta vista."
                                } else {
                                    "No hay propuestas visibles para este usuario."
                                },
                                icon = Icons.Default.FolderOff,
                            )
                        }
                    } else {
                        items(data.proyectos, key = { it.id }) { proyecto ->
                            ProyectoCompaneroCard(
                                proyecto = proyecto,
                                porcentaje = data.porcentajesSimilitud[proyecto.id],
                                onClick = {
                                    onNavigate(
                                        detalleProyectoRoute.replace("{projectId}", proyecto.id.toString()),
                                    )
                                },
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
private fun ProyectoCompaneroCard(
    proyecto: Project,
    porcentaje: Int?,
    onClick: () -> Unit,
) {
    SenaCard(elevation = 0.5.dp, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    proyecto.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                    modifier = Modifier.weight(1f),
                )
                if (porcentaje != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = senaColors().warning.copy(alpha = 0.15f),
                    ) {
                        Text(
                            "$porcentaje%",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = senaColors().warning,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                SenaStatusBadge(status = proyecto.estado)
            }
            Text(
                proyecto.programa ?: "Sin programa",
                style = MaterialTheme.typography.labelSmall,
                color = senaColors().textLight,
            )
            if (proyecto.description.isNotBlank()) {
                Text(
                    proyecto.description.take(140) + if (proyecto.description.length > 140) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = senaColors().textSecondary,
                )
            }
        }
    }
}

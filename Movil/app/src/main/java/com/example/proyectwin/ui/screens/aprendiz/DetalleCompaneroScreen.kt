package com.example.proyectwin.ui.screens.aprendiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.CompaneroUiState
import com.example.proyectwin.ui.viewmodel.DetalleCompaneroViewModel

/**
 * Perfil público de un compañero o instructor: datos básicos, su ficha y las
 * propuestas que el backend autoriza ver al solicitante.
 */
@Composable
fun DetalleCompaneroScreen(
    userId: String = "",
    onBack: () -> Unit,
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item {
                        SenaPageHeader(
                            title = data.usuario.name,
                            subtitle = data.usuario.roleDisplayName,
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
                            SenaSectionHeader(title = "Ficha")
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
                                    if (ficha.instructorName != null) {
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
                        SenaSectionHeader(title = "Propuestas (${data.proyectos.size})")
                    }

                    if (data.proyectos.isEmpty()) {
                        item {
                            SenaEmptyState(
                                message = "No hay propuestas visibles para este usuario.",
                                icon = Icons.Default.FolderOff,
                            )
                        }
                    } else {
                        items(data.proyectos, key = { it.id }) { proyecto ->
                            ProyectoCompaneroCard(proyecto)
                        }
                    }

                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ProyectoCompaneroCard(proyecto: Project) {
    SenaCard(elevation = 0.5.dp) {
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

package com.example.proyectwin.ui.screens.instructor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.Similarity
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.SimilitudesInstructorUiState
import com.example.proyectwin.ui.viewmodel.SimilitudesInstructorViewModel

/** Similitudes vigentes del alcance del instructor (sus fichas/programas). */
@Composable
fun SimilitudesInstructorScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    /** Filtro por propuesta (viene del detalle o de una notificación). */
    proyectoId: Int? = null,
    similitudesViewModel: SimilitudesInstructorViewModel = hiltViewModel(),
) {
    var busqueda by remember { mutableStateOf("") }
    var umbralMinimo by remember { mutableIntStateOf(0) }
    val uiState by similitudesViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { similitudesViewModel.load() }

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
    ) { paddingValues ->
        when (val estado = uiState) {
            is SimilitudesInstructorUiState.Loading ->
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is SimilitudesInstructorUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estado.message, onRetry = { similitudesViewModel.retry() })
            }
            is SimilitudesInstructorUiState.Success -> {
                val pares = estado.pares.filter { par ->
                    (proyectoId == null || par.projectId1 == proyectoId || par.projectId2 == proyectoId) &&
                        par.similitud * 100 >= umbralMinimo && (
                            par.project1Title.orEmpty().contains(busqueda, true) ||
                                par.project2Title.orEmpty().contains(busqueda, true)
                            )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item {
                        SenaPageHeader(
                            title = "Similitudes",
                            subtitle = if (proyectoId != null) {
                                "Coincidencias del motor para la propuesta #$proyectoId."
                            } else {
                                "Coincidencias del motor en tus fichas a cargo."
                            },
                            icon = Icons.Default.Compare,
                        )
                    }

                    item {
                        SenaCard(elevation = 1.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                SenaTextField(
                                    value = busqueda,
                                    onValueChange = { busqueda = it },
                                    label = "",
                                    placeholder = "Buscar por título...",
                                    leadingIcon = Icons.Default.Search,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0, 30, 50, 75).forEach { umbral ->
                                        SenaChip(
                                            text = if (umbral == 0) "Todas" else "≥ $umbral%",
                                            color = senaColors().green,
                                            isSelected = umbralMinimo == umbral,
                                            onClick = { umbralMinimo = umbral },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (pares.isEmpty()) {
                        item {
                            SenaEmptyState(
                                title = "Sin coincidencias",
                                message = "No hay similitudes vigentes que cumplan los filtros.",
                                icon = Icons.Default.SearchOff,
                            )
                        }
                    } else {
                        items(pares, key = { it.id }) { par ->
                            ParCard(par) {
                                onNavigate(
                                    AppNavigation.INSTRUCTOR_SIMILARITY_DETAIL
                                        .replace("{similarityId}", par.id.toString()),
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ParCard(par: Similarity, onClick: () -> Unit) {
    SenaCard(elevation = 0.5.dp, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Coincidencia #${par.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = senaColors().textLight,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (par.vigente) senaColors().success.copy(alpha = 0.12f) else senaColors().warning.copy(alpha = 0.12f),
                ) {
                    Text(
                        par.estadoDisplay,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (par.vigente) senaColors().success else senaColors().warning,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    par.similitudPercent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = senaColors().green,
                )
            }
            Text(par.project1Title ?: "Propuesta A", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = senaColors().text)
            par.project1Student?.let {
                Text("Aprendiz A: $it", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
            }
            Text(par.project2Title ?: "Propuesta B", style = MaterialTheme.typography.bodySmall, color = senaColors().textSecondary)
            par.project2Student?.let {
                Text("Aprendiz B: $it", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
            }
        }
    }
}

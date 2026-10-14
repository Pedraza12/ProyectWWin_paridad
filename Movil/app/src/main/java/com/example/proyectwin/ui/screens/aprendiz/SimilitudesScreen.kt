package com.example.proyectwin.ui.screens.aprendiz

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.Similarity
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.MisSimilitudesUiState
import com.example.proyectwin.ui.viewmodel.MisSimilitudesViewModel
import com.example.proyectwin.ui.viewmodel.SimilitudesDeProyecto

/** Similitudes vigentes de las propuestas del aprendiz. */
@Composable
fun SimilitudesScreen(
    proyectoId: Int? = null,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    misSimilitudesViewModel: MisSimilitudesViewModel = hiltViewModel(),
) {
    var busqueda by remember { mutableStateOf("") }
    var umbralMinimo by remember { mutableIntStateOf(0) }
    val uiState by misSimilitudesViewModel.uiState.collectAsState()

    LaunchedEffect(proyectoId) {
        misSimilitudesViewModel.load(proyectoId)
    }

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
            is MisSimilitudesUiState.Loading ->
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is MisSimilitudesUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estado.message, onRetry = { misSimilitudesViewModel.retry() })
            }
            is MisSimilitudesUiState.Success -> {
                val filas = estado.filas.mapNotNull { fila ->
                    val pares = fila.pares.filter { par ->
                        par.similitud * 100 >= umbralMinimo &&
                            (fila.proyecto.title.contains(busqueda, true) ||
                                par.project1Title.orEmpty().contains(busqueda, true) ||
                                par.project2Title.orEmpty().contains(busqueda, true))
                    }
                    if (pares.isEmpty()) null else fila.copy(pares = pares)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item {
                        SenaPageHeader(
                            title = "Similitudes",
                            subtitle = "Coincidencias detectadas por el motor entre tu propuesta y las propuestas aprobadas.",
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
                                Text(
                                    "Similitud mínima: $umbralMinimo%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = senaColors().textLight,
                                )
                                SenaChipRow {
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

                    if (filas.isEmpty()) {
                        item {
                            SenaEmptyState(
                                title = "Sin coincidencias",
                                message = if (estado.totalPares == 0) {
                                    "Aún no se han detectado similitudes en tus propuestas. El motor corre al enviarlas a revisión."
                                } else {
                                    "No hay coincidencias que cumplan los filtros actuales."
                                },
                                icon = Icons.Default.SearchOff,
                            )
                        }
                    } else {
                        items(filas, key = { it.proyecto.id }) { fila ->
                            FilaSimilitudes(
                                fila = fila,
                                onNavigate = onNavigate,
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
private fun FilaSimilitudes(
    fila: SimilitudesDeProyecto,
    onNavigate: (String) -> Unit,
) {
    SenaCard(elevation = 1.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = senaColors().green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    fila.proyecto.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                    modifier = Modifier.weight(1f),
                )
                SenaStatusBadge(status = fila.proyecto.estado)
            }
            HorizontalDivider(color = senaColors().borderSoft)
            fila.pares.forEach { par: Similarity ->
                val esMia1 = par.projectId1 == fila.proyecto.id
                val tituloContraparte = if (esMia1) par.project2Title ?: "Propuesta" else par.project1Title ?: "Propuesta"
                val aprendizContraparte = if (esMia1) par.project2Student else par.project1Student
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tituloContraparte,
                            style = MaterialTheme.typography.bodySmall,
                            color = senaColors().textSecondary,
                        )
                        aprendizContraparte?.let {
                            Text(
                                "Aprendiz: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = senaColors().textLight,
                            )
                        }
                        Text(
                            par.estadoDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (par.vigente) senaColors().success else senaColors().warning,
                        )
                    }
                    Text(
                        par.similitudPercent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = senaColors().green,
                    )
                    IconButton(
                        onClick = {
                            onNavigate(
                                AppNavigation.APRENDIZ_SIMILARITY.replace("{similarityId}", par.id.toString()),
                            )
                        },
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Ver detalle")
                    }
                }
            }
        }
    }
}

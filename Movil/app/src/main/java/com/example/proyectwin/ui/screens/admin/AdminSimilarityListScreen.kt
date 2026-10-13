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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.Similarity
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AdminViewModel
import com.example.proyectwin.ui.viewmodel.AdminUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSimilarityListScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    /** Filtro por propuesta (viene del detalle o de una notificación). */
    proyectoId: Int? = null,
    bottomBar: @Composable () -> Unit = {},
    adminViewModel: AdminViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("Todos") }
    val statusFilterItems = listOf(
        "Todos" to "Todos",
        "Vigentes" to "Vigentes",
        "Históricas" to "Históricas",
    )

    val adminState by adminViewModel.uiState.collectAsState()
    val completas by adminViewModel.similitudesCompletas.collectAsState()

    // El listado global del servidor solo trae pares con ambos proyectos
    // aprobados; al abrir la pantalla se completan con los pares por propuesta
    // (vigentes e históricas) usando los endpoints existentes. Se espera al
    // estado Success para no lanzarlo antes de tener los datos ni duplicarlo.
    LaunchedEffect(adminState) {
        if (adminState is AdminUiState.Success) adminViewModel.cargarSimilitudesCompletas()
    }

    val similarityGroups = when (val state = adminState) {
        is AdminUiState.Success -> completas ?: state.similarities
        else -> emptyList()
    }

    val filteredGroups = similarityGroups.filter { group ->
        val matchesProyecto = proyectoId == null ||
            group.projectId1 == proyectoId || group.projectId2 == proyectoId
        val matchesEstado = when (selectedStatus) {
            "Vigentes" -> group.vigente
            "Históricas" -> !group.vigente
            else -> true
        }
        val consulta = searchQuery.trim()
        val matchesSearch = consulta.isEmpty() ||
            group.project1Title.orEmpty().contains(consulta, ignoreCase = true) ||
            group.project2Title.orEmpty().contains(consulta, ignoreCase = true)
        matchesProyecto && matchesEstado && matchesSearch
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "ProyecTwin",
                onBack = onBack,
                showProfile = true,
                showNotifications = true
            )
        },
        containerColor = senaColors().background,
        bottomBar = bottomBar
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SenaPageHeader(
                    title = "Similitudes",
                    subtitle = "Listado de propuestas con similitudes detectadas por el sistema.",
                    icon = Icons.Default.Search
                )
            }

            when (val state = adminState) {
                is AdminUiState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        SenaLoadingState()
                    }
                }
                is AdminUiState.Error -> item {
                    SenaErrorState(message = state.message, onRetry = { adminViewModel.loadAll() })
                }
                is AdminUiState.Success -> {
            item {
                SenaCard(elevation = 1.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Filtrar Similitudes",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = senaColors().textLight,
                            letterSpacing = 0.5.sp
                        )
                        SenaTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = "",
                            placeholder = "Nombre del proyecto...",
                            leadingIcon = Icons.Default.Search
                        )

                        Text(
                            "Estado",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = senaColors().textLight,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            statusFilterItems.forEach { (statusKey, statusLabel) ->
                                SenaChip(
                                    text = statusLabel,
                                    color = when (statusKey) {
                                        "Vigentes" -> senaColors().success
                                        "Históricas" -> senaColors().warning
                                        else -> senaColors().green
                                    },
                                    isSelected = selectedStatus == statusKey,
                                    onClick = { selectedStatus = statusKey }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (completas == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = senaColors().green,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Cargando también los pares de cada propuesta…",
                                style = MaterialTheme.typography.labelSmall,
                                color = senaColors().textSecondary,
                            )
                        }
                    }
                    Text(
                        if (proyectoId != null) {
                            "Mostrando solo los pares de la propuesta #$proyectoId."
                        } else {
                            "${filteredGroups.size} de ${similarityGroups.size} pares · incluye los pares por propuesta " +
                                "(vigentes con contraparte aprobada e históricas), no solo los de ambos lados aprobados."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = senaColors().textLight,
                    )
                }
            }

            if (filteredGroups.isEmpty()) {
                item {
                    SenaEmptyState(
                        title = "Sin similitudes",
                        message = "No se encontraron similitudes registradas.",
                        icon = Icons.Default.Search
                    )
                }
            } else {
                items(filteredGroups, key = { it.id }) { similarity ->
                    SenaCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onNavigate(
                                AppNavigation.ADMIN_SIMILARITY_DETAIL.replace("{similarityId}", similarity.id.toString())
                            )
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Similitud #${similarity.id}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    similarity.similitudPercent,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = senaColors().green,
                                )
                            }
                            Text(
                                "A: ${similarity.project1Title ?: "N/A"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = senaColors().textSecondary
                            )
                            similarity.project1Student?.let {
                                Text(
                                    "Aprendiz A: $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = senaColors().textLight,
                                )
                            }
                            Text(
                                "B: ${similarity.project2Title ?: "N/A"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = senaColors().textSecondary
                            )
                            similarity.project2Student?.let {
                                Text(
                                    "Aprendiz B: $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = senaColors().textLight,
                                )
                            }
                            Text(
                                similarity.estadoDisplay,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (similarity.vigente) senaColors().success else senaColors().warning,
                            )
                        }
                    }
                }
            }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AdminSimilarityListPreview() {
    ProyecTwinTheme {
        AdminSimilarityListScreen(onBack = {}, onNavigate = {})
    }
}

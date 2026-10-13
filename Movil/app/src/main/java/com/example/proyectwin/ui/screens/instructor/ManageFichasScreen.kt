package com.example.proyectwin.ui.screens.instructor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import com.example.proyectwin.ui.viewmodel.DashboardUiState
import com.example.proyectwin.ui.viewmodel.DashboardViewModel
import com.example.proyectwin.ui.viewmodel.FichasUiState
import com.example.proyectwin.ui.viewmodel.FichasViewModel

data class FichaItem(
    val id: Int,
    val code: String,
    val name: String,
    val program: String,
    val students: Int,
    val projects: Int,
    val status: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFichasScreen(
    onBack: () -> Unit,
    onViewDetail: (String) -> Unit,
    /** El directorio es global: no depende de la ficha de origen. */
    onViewDirectory: () -> Unit,
    onCreateFicha: () -> Unit,
    onNavigate: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    fichasViewModel: FichasViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("Todas") }
    // -1 = ninguna expandida (sentinel para rememberSaveable).
    var fichaExpandida by rememberSaveable { mutableIntStateOf(-1) }
    var inicializado by rememberSaveable { mutableStateOf(false) }
    var fichaParaEliminar by remember { mutableStateOf<FichaItem?>(null) }
    val filters = listOf("Todas", "Activo", "Finalizado", "Anulada")
    val uiState by fichasViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val dashState by dashboardViewModel.uiState.collectAsState()
    val detalles by fichasViewModel.detalles.collectAsState()
    val actionState by fichasViewModel.actionState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LaunchedEffect(actionState.message) {
        actionState.message?.let {
            snackbarHostState.showSnackbar(it)
            fichasViewModel.clearError()
        }
    }

    LaunchedEffect(Unit) {
        if (inicializado) {
            // Al volver de editar, refresco silencioso: conserva scroll y expansión.
            fichasViewModel.refrescar()
        } else {
            fichasViewModel.loadAllFichas()
            inicializado = true
        }
        (authState as? AuthUiState.LoggedIn)?.user?.let {
            dashboardViewModel.loadInstructorDashboard(it.id)
        }
    }
    LaunchedEffect(fichaExpandida) {
        if (fichaExpandida != -1) fichasViewModel.cargarDetalle(fichaExpandida)
    }

    val proyectos = (dashState as? DashboardUiState.Success)?.projects.orEmpty()
    val fichas = (uiState as? FichasUiState.Success)?.fichas?.map { ficha ->
        FichaItem(
            id = ficha.id,
            code = ficha.codigo,
            name = ficha.nombre ?: ficha.programa,
            program = ficha.programa,
            students = ficha.estudiantes.size,
            projects = proyectos.count { it.fichaId == ficha.id },
            status = ficha.statusDisplay
        )
    } ?: emptyList()

    val filteredFichas = fichas.filter { ficha ->
        val matchesFilter = if (selectedFilter == "Todas") true else ficha.status == selectedFilter
        val matchesSearch = ficha.code.contains(searchQuery, ignoreCase = true) || ficha.name.contains(searchQuery, ignoreCase = true)
        matchesFilter && matchesSearch
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateFicha,
                containerColor = senaColors().green,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Default.PlusOne, contentDescription = "Nueva Ficha")
            }
        },
        containerColor = senaColors().background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SenaPageHeader(
                    title = "Gestionar Fichas",
                    subtitle = "Administra los grupos de formación y supervisa el progreso de los aprendices.",
                    icon = Icons.Default.LayerGroup
                )
            }

            // Statistics Bar
            item {
                SenaCard(elevation = 1.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${fichas.size} Fichas Registradas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = senaColors().text)
                            Text("Panel de control de instructor", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                        }
                        Surface(
                            color = senaColors().green.copy(alpha = 0.1f),
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.Default.Add, 
                                contentDescription = null, 
                                tint = senaColors().green, 
                                modifier = Modifier.padding(8.dp).size(20.dp)
                            )
                        }
                    }
                }
            }

            // Filters
            item {
                SenaCard(elevation = 0.5.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SenaTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = "Búsqueda rápida",
                            placeholder = "Buscar por código o nombre...",
                            leadingIcon = Icons.Default.Search
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filters.forEach { filter ->
                                SenaChip(
                                    text = filter,
                                    color = when (filter) {
                                        "Activo" -> senaColors().success
                                        "Finalizado" -> senaColors().warning
                                        "Anulada" -> senaColors().danger
                                        else -> senaColors().green
                                    },
                                    isSelected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter }
                                )
                            }
                        }
                    }
                }
            }

            when (val state = uiState) {
                is FichasUiState.Loading -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = senaColors().green)
                    }
                }
                is FichasUiState.Error -> item {
                    SenaErrorState(message = state.message, onRetry = { fichasViewModel.loadAllFichas() })
                }
                is FichasUiState.Success -> {
                    if (filteredFichas.isEmpty()) {
                        item {
                            SenaEmptyState(
                                message = "No se encontraron fichas que coincidan con la b\u00fasqueda.",
                                icon = Icons.Default.SearchOff
                            )
                        }
                    } else {
                        items(filteredFichas, key = { it.id }) { ficha ->
                            InstructorFichaCard(
                                ficha = ficha,
                                detalle = detalles[ficha.id],
                                expandida = fichaExpandida == ficha.id,
                                onToggleRoster = {
                                    fichaExpandida = if (fichaExpandida == ficha.id) -1 else ficha.id
                                },
                                onViewDetail = { onViewDetail(ficha.id.toString()) },
                                onViewDirectory = onViewDirectory,
                                // Una ficha anulada solo la modifica un admin (403):
                                // el instructor no debe ver la acción como disponible.
                                onEdit = if (ficha.status != "Anulada") {
                                    {
                                        onNavigate(
                                            AppNavigation.INSTRUCTOR_CREAR_FICHA
                                                .replace("{fichaId}", ficha.id.toString()),
                                        )
                                    }
                                } else {
                                    null
                                },
                                onEliminar = { fichaParaEliminar = ficha },
                                eliminando = actionState.eliminando,
                            )
                        }
                    }
                }
            }
            
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    fichaParaEliminar?.let { ficha ->
        AlertDialog(
            onDismissRequest = { fichaParaEliminar = null },
            title = { Text("Eliminar ficha") },
            text = { Text("Si tiene aprendices o propuestas quedará anulada; si no, se eliminará.") },
            confirmButton = {
                TextButton(onClick = {
                    fichaParaEliminar = null
                    fichasViewModel.eliminarFicha(ficha.id)
                }) { Text("Eliminar", color = senaColors().danger) }
            },
            dismissButton = { TextButton(onClick = { fichaParaEliminar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun InstructorFichaCard(
    ficha: FichaItem,
    detalle: Ficha? = null,
    expandida: Boolean = false,
    onToggleRoster: () -> Unit = {},
    onViewDetail: () -> Unit,
    onViewDirectory: () -> Unit,
    /** null = la acción no está disponible (ficha anulada para instructor). */
    onEdit: (() -> Unit)?,
    onEliminar: () -> Unit = {},
    eliminando: Boolean = false,
) {
    SenaCard(elevation = 1.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = senaColors().borderSoft,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        ficha.code,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = senaColors().textSecondary
                    )
                }
                SenaStatusBadge(status = ficha.status)
            }

            Column {
                Text(ficha.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = senaColors().text)
                Text(ficha.program, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FichaStatMini(Icons.Default.Groups, "${ficha.students} Aprendices", Modifier.weight(1f))
                FichaStatMini(Icons.Default.FolderOpen, "${ficha.projects} Proyectos", Modifier.weight(1f))
            }

            TextButton(onClick = onToggleRoster) {
                Icon(
                    if (expandida) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (expandida) "Ocultar integrantes" else "Ver integrantes (${detalle?.estudiantes?.size ?: ficha.students})")
            }

            if (expandida) {
                val estudiantes = detalle?.estudiantes ?: emptyList()
                if (estudiantes.isEmpty()) {
                    Text(
                        "Sin aprendices en esta ficha. Usa \"Editar\" para agregarlos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().textSecondary,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        estudiantes.forEach { aprendiz ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SenaAvatar(fotoBase64 = aprendiz.fotoPerfil, nombre = aprendiz.name, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(aprendiz.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(aprendiz.email, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = senaColors().borderSoft)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                FichaActionButton(Icons.Default.Visibility, "Detalle", onViewDetail)
                FichaActionButton(Icons.Default.ContactPage, "Directorio", onViewDirectory)
                onEdit?.let { FichaActionButton(Icons.Default.Edit, "Editar", it) }
                if (ficha.status != "Anulada") {
                    TextButton(onClick = onEliminar, enabled = !eliminando) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = senaColors().danger)
                        Spacer(Modifier.width(6.dp))
                        Text("Eliminar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = senaColors().danger)
                    }
                }
            }
        }
    }
}

@Composable
fun FichaStatMini(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = senaColors().textLight, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = senaColors().textSecondary)
    }
}

@Composable
fun FichaActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

// Missing icons
val Icons.Filled.LayerGroup: ImageVector get() = Icons.Default.Layers

@Preview(showBackground = true)
@Composable
fun ManageFichasScreenPreview() {
    ProyecTwinTheme {
        ManageFichasScreen(onBack = {}, onViewDetail = {}, onViewDirectory = {}, onCreateFicha = {}, onNavigate = {})
    }
}

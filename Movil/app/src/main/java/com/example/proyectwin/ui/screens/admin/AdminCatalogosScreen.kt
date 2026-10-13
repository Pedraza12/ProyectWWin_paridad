package com.example.proyectwin.ui.screens.admin

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.KnowledgeNetwork
import com.example.proyectwin.data.model.TrainingProgram
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.CatalogosAction
import com.example.proyectwin.ui.viewmodel.CatalogosUiState
import com.example.proyectwin.ui.viewmodel.CatalogosViewModel

/** Redes de conocimiento y programas de formación (CRUD admin). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCatalogosScreen(
    onBack: () -> Unit,
    catalogosViewModel: CatalogosViewModel = hiltViewModel(),
) {
    val uiState by catalogosViewModel.uiState.collectAsState()
    val accion by catalogosViewModel.accion.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var dialogoRed by remember { mutableStateOf<KnowledgeNetwork?>(null) }
    var crearRed by remember { mutableStateOf(false) }
    var dialogoPrograma by remember { mutableStateOf<TrainingProgram?>(null) }
    var redParaPrograma by remember { mutableStateOf<KnowledgeNetwork?>(null) }
    var redParaEliminar by remember { mutableStateOf<KnowledgeNetwork?>(null) }
    var programaParaEliminar by remember { mutableStateOf<TrainingProgram?>(null) }

    LaunchedEffect(Unit) { catalogosViewModel.load() }

    LaunchedEffect(accion) {
        when (val estado = accion) {
            is CatalogosAction.Message -> {
                snackbarHostState.showSnackbar(estado.text)
                catalogosViewModel.resetAccion()
            }
            is CatalogosAction.Error -> {
                snackbarHostState.showSnackbar(estado.message)
                catalogosViewModel.resetAccion()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "Catálogos",
                onBack = onBack,
                showProfile = true,
                showNotifications = true,
            )
        },
        containerColor = senaColors().background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when (val estado = uiState) {
            is CatalogosUiState.Loading ->
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is CatalogosUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estado.message, onRetry = { catalogosViewModel.load() })
            }
            is CatalogosUiState.Success -> {
                val data = estado.data
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        SenaPageHeader(
                            title = "Catálogos",
                            subtitle = "Redes de conocimiento y programas de formación.",
                            icon = Icons.Default.Category,
                        )
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Redes (${data.redes.size})", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { crearRed = true }) { Text("+ Red") }
                        }
                    }
                    items(data.redes, key = { "red-${it.id}" }) { red ->
                        val programasDeRed = data.programas.count { it.redId == red.id }
                        SenaCard(elevation = 0.5.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(red.nombre, fontWeight = FontWeight.Bold)
                                    Text("$programasDeRed programas", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                                }
                                TextButton(onClick = { dialogoRed = red }) { Text("Editar") }
                                TextButton(onClick = { redParaEliminar = red }) { Text("Eliminar", color = senaColors().danger) }
                            }
                        }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Programas (${data.programas.size})", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            TextButton(onClick = {
                                redParaPrograma = data.redes.firstOrNull()
                                dialogoPrograma = TrainingProgram(id = 0, nombre = "")
                            }) { Text("+ Programa") }
                        }
                    }
                    if (data.redes.isEmpty()) {
                        item { Text("Primero crea una red de conocimiento.", color = senaColors().textSecondary) }
                    }
                    items(data.programas, key = { "prog-${it.id}" }) { programa ->
                        val red = data.redes.firstOrNull { it.id == programa.redId }
                        SenaCard(elevation = 0.5.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(programa.nombre, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${programa.nivel ?: "Sin nivel"} · ${red?.nombre ?: "Sin red"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = senaColors().textLight,
                                    )
                                }
                                TextButton(onClick = {
                                    redParaPrograma = data.redes.firstOrNull { it.id == programa.redId }
                                    dialogoPrograma = programa
                                }) { Text("Editar") }
                                TextButton(onClick = { programaParaEliminar = programa }) { Text("Eliminar", color = senaColors().danger) }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    if (crearRed || dialogoRed != null) {
        val editando = dialogoRed
        var nombre by remember(editando) { mutableStateOf(editando?.nombre.orEmpty()) }
        AlertDialog(
            onDismissRequest = { crearRed = false; dialogoRed = null },
            title = { Text(if (editando == null) "Nueva red" else "Editar red", fontWeight = FontWeight.Bold) },
            text = { SenaTextField(value = nombre, onValueChange = { nombre = it }, label = "Nombre") },
            confirmButton = {
                TextButton(onClick = {
                    val limpio = nombre.trim()
                    if (limpio.isNotBlank()) {
                        if (editando == null) catalogosViewModel.crearRed(limpio)
                        else catalogosViewModel.actualizarRed(editando.id, limpio)
                    }
                    crearRed = false; dialogoRed = null
                }) { Text("Guardar", color = senaColors().green, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { crearRed = false; dialogoRed = null }) { Text("Cancelar") } },
        )
    }

    dialogoPrograma?.let { programa ->
        val editando = programa.id != 0
        var nombre by remember(programa) { mutableStateOf(programa.nombre) }
        var nivel by remember(programa) { mutableStateOf(programa.nivel.orEmpty()) }
        var redSeleccionada by remember(programa) { mutableStateOf(redParaPrograma) }
        var redesExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { dialogoPrograma = null },
            title = { Text(if (editando) "Editar programa" else "Nuevo programa", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SenaTextField(value = nombre, onValueChange = { nombre = it }, label = "Nombre")
                    SenaTextField(value = nivel, onValueChange = { nivel = it }, label = "Nivel * (ej: Tecnólogo)")
                    Text("Red de conocimiento", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                    Box {
                        TextButton(onClick = { redesExpanded = true }) {
                            Text(redSeleccionada?.nombre ?: "Selecciona una red", color = senaColors().green)
                        }
                        DropdownMenu(expanded = redesExpanded, onDismissRequest = { redesExpanded = false }) {
                            // El menú necesita la lista completa; se toma del estado superior.
                            (uiState as? CatalogosUiState.Success)?.data?.redes.orEmpty().forEach { red ->
                                DropdownMenuItem(
                                    text = { Text(red.nombre) },
                                    onClick = { redSeleccionada = red; redesExpanded = false },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val red = redSeleccionada
                    val nivelLimpio = nivel.trim()
                    // El backend exige `nivel`: sin él, el request daría 422.
                    if (nombre.trim().isNotBlank() && nivelLimpio.isNotBlank() && red != null) {
                        if (editando) {
                            catalogosViewModel.actualizarPrograma(programa.id, nombre.trim(), nivelLimpio, red.id)
                        } else {
                            catalogosViewModel.crearPrograma(nombre.trim(), nivelLimpio, red.id)
                        }
                    }
                    dialogoPrograma = null
                }) { Text("Guardar", color = senaColors().green, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { dialogoPrograma = null }) { Text("Cancelar") } },
        )
    }

    redParaEliminar?.let { red ->
        AlertDialog(
            onDismissRequest = { redParaEliminar = null },
            title = { Text("Eliminar red") },
            text = { Text("Se eliminarán sus programas y fichas asociadas (cascada del backend).") },
            confirmButton = {
                TextButton(onClick = {
                    redParaEliminar = null
                    catalogosViewModel.eliminarRed(red.id)
                }) { Text("Eliminar", color = senaColors().danger) }
            },
            dismissButton = { TextButton(onClick = { redParaEliminar = null }) { Text("Cancelar") } },
        )
    }

    programaParaEliminar?.let { programa ->
        AlertDialog(
            onDismissRequest = { programaParaEliminar = null },
            title = { Text("Eliminar programa") },
            text = { Text("Se eliminarán sus fichas y aprendices asociados (cascada del backend).") },
            confirmButton = {
                TextButton(onClick = {
                    programaParaEliminar = null
                    catalogosViewModel.eliminarPrograma(programa.id)
                }) { Text("Eliminar", color = senaColors().danger) }
            },
            dismissButton = { TextButton(onClick = { programaParaEliminar = null }) { Text("Cancelar") } },
        )
    }
}

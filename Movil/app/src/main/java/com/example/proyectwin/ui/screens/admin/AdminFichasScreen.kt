package com.example.proyectwin.ui.screens.admin

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.local.DescargasArchivos
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AdminFichaAction
import com.example.proyectwin.ui.viewmodel.AdminFichasUiState
import com.example.proyectwin.ui.viewmodel.AdminFichasViewModel

/**
 * Fichas (admin): listado completo con roster expandible (solo lectura) y
 * accesos. La gestión de integrantes vive en "Editar ficha", donde agregar o
 * quitar no reinicia la pantalla ni pierde la posición del usuario. Tocar la
 * tarjeta abre el detalle (información, roster y propuestas de la ficha).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFichasScreen(
    onBack: () -> Unit,
    onCreateFicha: () -> Unit,
    onNavigate: (String) -> Unit,
    onFichaDetail: (Int) -> Unit,
    adminFichasViewModel: AdminFichasViewModel = hiltViewModel(),
) {
    val uiState by adminFichasViewModel.uiState.collectAsState()
    val accion by adminFichasViewModel.accion.collectAsState()
    val detalles by adminFichasViewModel.detalles.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    var busqueda by rememberSaveable { mutableStateOf("") }
    // -1 = ninguna expandida (sentinel para rememberSaveable).
    var fichaExpandida by rememberSaveable { mutableIntStateOf(-1) }
    var fichaParaEliminar by remember { mutableStateOf<Ficha?>(null) }
    var inicializado by rememberSaveable { mutableStateOf(false) }

    // Primera entrada: carga con spinner. Al volver de editar: refresco silencioso
    // que conserva lista, expansión y scroll.
    LaunchedEffect(Unit) {
        if (inicializado) adminFichasViewModel.refrescar() else {
            adminFichasViewModel.load()
            inicializado = true
        }
    }
    LaunchedEffect(fichaExpandida) {
        if (fichaExpandida != -1) adminFichasViewModel.cargarDetalle(fichaExpandida)
    }

    LaunchedEffect(accion) {
        when (val estado = accion) {
            is AdminFichaAction.Message -> {
                snackbarHostState.showSnackbar(estado.text)
                adminFichasViewModel.resetAccion()
            }
            is AdminFichaAction.Error -> {
                snackbarHostState.showSnackbar(estado.message)
                adminFichasViewModel.resetAccion()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "Fichas",
                onBack = onBack,
                showProfile = true,
                showNotifications = true,
            )
        },
        containerColor = senaColors().background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateFicha,
                containerColor = senaColors().green,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nueva ficha")
            }
        },
    ) { paddingValues ->
        when (val estado = uiState) {
            is AdminFichasUiState.Loading ->
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is AdminFichasUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estado.message, onRetry = { adminFichasViewModel.load() })
            }
            is AdminFichasUiState.Success -> {
                val fichas = estado.fichas.filter {
                    busqueda.isBlank() ||
                        it.codigo.contains(busqueda, true) ||
                        (it.nombre ?: it.programa).contains(busqueda, true)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        SenaPageHeader(
                            title = "Fichas",
                            subtitle = "Administra grupos, roster y credenciales de los aprendices.",
                            icon = Icons.Default.Groups,
                        )
                    }
                    item {
                        SenaTextField(
                            value = busqueda,
                            onValueChange = { busqueda = it },
                            label = "",
                            placeholder = "Buscar por código o nombre...",
                            leadingIcon = Icons.Default.Search,
                        )
                    }

                    if (fichas.isEmpty()) {
                        item { SenaEmptyState(message = "No hay fichas que coincidan.", icon = Icons.Default.SearchOff) }
                    } else {
                        items(fichas, key = { it.id }) { ficha ->
                            val detalle = detalles[ficha.id]
                            TarjetaFichaAdmin(
                                ficha = ficha,
                                detalle = detalle,
                                expandida = fichaExpandida == ficha.id,
                                onVerDetalle = { onFichaDetail(ficha.id) },
                                onExpandir = {
                                    fichaExpandida = if (fichaExpandida == ficha.id) -1 else ficha.id
                                },
                                onEditar = {
                                    onNavigate(
                                        AppNavigation.INSTRUCTOR_CREAR_FICHA.replace("{fichaId}", ficha.id.toString()),
                                    )
                                },
                                onCredenciales = {
                                    adminFichasViewModel.credencialesPdf(ficha.id) { resultado ->
                                        val mensaje = resultado.fold(
                                            onSuccess = { bytes ->
                                                DescargasArchivos.guardarPdf(context, bytes, "credenciales-ficha-${ficha.codigo}.pdf")
                                            },
                                            onFailure = { it.message ?: "No se pudo descargar el PDF." },
                                        )
                                        Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
                                    }
                                },
                                onEliminar = { fichaParaEliminar = ficha },
                            )
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        fichaParaEliminar?.let { ficha ->
            // Confirmación tipada: el estado vive con el diálogo y se reinicia al
            // abrirlo/cerrarlo (remember(ficha.id) + salida de composición).
            var confirmacion by remember(ficha.id) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { fichaParaEliminar = null },
                title = { Text("Eliminar ficha") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Si tiene aprendices o propuestas quedará anulada; si no, se eliminará.")
                        SenaTextField(
                            value = confirmacion,
                            onValueChange = { confirmacion = it },
                            label = "Escribe ELIMINAR para confirmar",
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            fichaParaEliminar = null
                            adminFichasViewModel.eliminar(ficha.id)
                        },
                        enabled = confirmacion.trim() == "ELIMINAR",
                    ) { Text("Eliminar", color = senaColors().danger) }
                },
                dismissButton = { TextButton(onClick = { fichaParaEliminar = null }) { Text("Cancelar") } },
            )
        }
    }
}

@Composable
private fun TarjetaFichaAdmin(
    ficha: Ficha,
    detalle: Ficha?,
    expandida: Boolean,
    onVerDetalle: () -> Unit,
    onExpandir: () -> Unit,
    onEditar: () -> Unit,
    onCredenciales: () -> Unit,
    onEliminar: () -> Unit,
) {
    SenaCard(elevation = 1.dp, onClick = onVerDetalle) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(ficha.nombre ?: ficha.programa, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${ficha.codigo} · ${ficha.programa.ifBlank { "Sin programa" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = senaColors().textLight,
                    )
                    ficha.instructorName?.let {
                        Text("Instructor: $it", style = MaterialTheme.typography.labelSmall, color = senaColors().textSecondary)
                    }
                }
                SenaStatusBadge(status = ficha.statusDisplay)
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Ver detalle",
                    tint = senaColors().textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            HorizontalDivider(color = senaColors().borderSoft)

            TextButton(onClick = onExpandir) {
                Icon(
                    if (expandida) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (expandida) {
                        "Ocultar integrantes"
                    } else {
                        "Ver integrantes (${(detalle ?: ficha).estudiantes.size})"
                    },
                )
            }

            if (expandida) {
                val estudiantes = (detalle ?: ficha).estudiantes
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

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEditar) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar")
                }
                TextButton(onClick = onCredenciales) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Credenciales")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEliminar) {
                    Text("Eliminar", color = senaColors().danger)
                }
            }
        }
    }
}

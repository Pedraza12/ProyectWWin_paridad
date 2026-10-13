package com.example.proyectwin.ui.screens.aprendiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.Comment
import com.example.proyectwin.data.model.ProjectHistory
import com.example.proyectwin.data.model.ProjectStatus
import com.example.proyectwin.data.model.TeamMember
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.util.aplanarHiloComentarios
import com.example.proyectwin.ui.util.formatearFechaLocal
import com.example.proyectwin.ui.viewmodel.ProjectActionState
import com.example.proyectwin.ui.viewmodel.ProjectDetailData
import com.example.proyectwin.ui.viewmodel.ProjectDetailUiState
import com.example.proyectwin.ui.viewmodel.ProjectDetailViewModel

/**
 * Detalle real de la propuesta: información, equipo (pivote), observaciones
 * (hilos de `comments`), historial y acciones según rol/estado. Las reglas
 * finas las aplica el backend; aquí solo se ocultan acciones imposibles para
 * no invitar al error (la respuesta 403/422 se muestra igualmente).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String = "",
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    editarRoute: String = AppNavigation.APRENDIZ_NEW_PROJECT,
    similitudesRoute: String = AppNavigation.APRENDIZ_SIMILITUDES,
    similarityRoute: String = AppNavigation.APRENDIZ_SIMILARITY,
    projectDetailViewModel: ProjectDetailViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val uiState by projectDetailViewModel.uiState.collectAsState()
    val accion by projectDetailViewModel.accion.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var confirmarEliminar by remember { mutableStateOf(false) }
    var mostrarHojaEquipo by remember { mutableStateOf(false) }
    var nuevoComentario by remember { mutableStateOf("") }
    var respondiendoA by remember { mutableStateOf<Int?>(null) }
    var dialogoRechazo by remember { mutableStateOf(false) }
    var observacionRechazo by remember { mutableStateOf("") }
    var comentarioEditando by remember { mutableStateOf<Comment?>(null) }
    var textoEdicionComentario by remember { mutableStateOf("") }
    var comentarioParaEliminar by remember { mutableStateOf<Comment?>(null) }

    LaunchedEffect(projectId) {
        projectId.toIntOrNull()?.let { projectDetailViewModel.load(it) }
    }

    LaunchedEffect(accion) {
        when (val estado = accion) {
            is ProjectActionState.Success -> {
                projectDetailViewModel.resetAccion()
            }
            is ProjectActionState.Error -> {
                snackbarHostState.showSnackbar(estado.message)
                projectDetailViewModel.resetAccion()
            }
            is ProjectActionState.Deleted -> {
                projectDetailViewModel.resetAccion()
                onBack()
            }
            else -> Unit
        }
    }

    val enProceso = accion is ProjectActionState.Loading

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when (val estado = uiState) {
            is ProjectDetailUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    SenaLoadingState()
                }
            }
            is ProjectDetailUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    SenaErrorState(
                        message = estado.message,
                        onRetry = { projectId.toIntOrNull()?.let { projectDetailViewModel.load(it) } },
                    )
                }
            }
            is ProjectDetailUiState.Success -> {
                val data = estado.data
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(scrollState)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    CabeceraPropuesta(data)

                    AccionesPropuesta(
                        data = data,
                        enProceso = enProceso,
                        onEnviar = { projectDetailViewModel.enviar() },
                        onEditar = {
                            onNavigate(
                                editarRoute
                                    .replace("{projectId}", projectId)
                                    .replace("{retorno}", "aprendiz"),
                            )
                        },
                        onEliminar = { confirmarEliminar = true },
                        onSimilitudes = {
                            onNavigate(similitudesRoute.replace("{proyectoId}", projectId))
                        },
                        onAprobar = { projectDetailViewModel.revisar(aprobar = true) },
                        onRechazar = { dialogoRechazo = true },
                    )

                    ResumenPropuesta(data)

                    SimilitudesPropuesta(data, similarityRoute, onNavigate)

                    EquipoPropuesta(
                        data = data,
                        enProceso = enProceso,
                        onAgregar = { mostrarHojaEquipo = true },
                        onQuitar = { projectDetailViewModel.quitarIntegrante(it) },
                    )

                    ObservacionesPropuesta(
                        data = data,
                        enProceso = enProceso,
                        nuevoComentario = nuevoComentario,
                        respondiendoA = respondiendoA,
                        onTextoChange = { nuevoComentario = it },
                        onResponder = { respondiendoA = it },
                        onCancelarRespuesta = { respondiendoA = null },
                        onEnviar = {
                            projectDetailViewModel.comentar(nuevoComentario.trim(), respondiendoA)
                            nuevoComentario = ""
                            respondiendoA = null
                        },
                        onEditar = { comentario ->
                            comentarioEditando = comentario
                            textoEdicionComentario = comentario.texto
                        },
                        onEliminar = { comentario -> comentarioParaEliminar = comentario },
                    )

                    HistorialPropuesta(data.historial)

                    Spacer(Modifier.height(60.dp))
                }

                if (confirmarEliminar) {
                    AlertDialog(
                        onDismissRequest = { confirmarEliminar = false },
                        title = { Text("Eliminar propuesta") },
                        text = { Text("Esta acción no se puede deshacer. ¿Eliminar \"${data.project.title}\"?") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmarEliminar = false
                                projectDetailViewModel.eliminar()
                            }) { Text("Eliminar", color = senaColors().danger) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmarEliminar = false }) { Text("Cancelar") }
                        },
                    )
                }

                if (dialogoRechazo) {
                    AlertDialog(
                        onDismissRequest = { dialogoRechazo = false },
                        title = { Text("Rechazar propuesta") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Puedes adjuntar una observación para el aprendiz (opcional).")
                                SenaTextField(
                                    value = observacionRechazo,
                                    onValueChange = { observacionRechazo = it },
                                    label = "",
                                    placeholder = "Motivo del rechazo...",
                                    singleLine = false,
                                    minLines = 3,
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                dialogoRechazo = false
                                projectDetailViewModel.revisar(aprobar = false, observacion = observacionRechazo)
                                observacionRechazo = ""
                            }) { Text("Rechazar", color = senaColors().danger) }
                        },
                        dismissButton = {
                            TextButton(onClick = { dialogoRechazo = false }) { Text("Cancelar") }
                        },
                    )
                }

                comentarioEditando?.let { comentario ->
                    AlertDialog(
                        onDismissRequest = { comentarioEditando = null },
                        title = { Text("Editar observación") },
                        text = {
                            SenaTextField(
                                value = textoEdicionComentario,
                                onValueChange = { textoEdicionComentario = it },
                                label = "",
                                placeholder = "Texto de la observación...",
                                singleLine = false,
                                minLines = 3,
                            )
                        },
                        confirmButton = {
                            TextButton(
                                enabled = textoEdicionComentario.isNotBlank() && !enProceso,
                                onClick = {
                                    projectDetailViewModel.editarComentario(
                                        id = comentario.id,
                                        idProyecto = comentario.proyectoId,
                                        texto = textoEdicionComentario.trim(),
                                        respuestaA = comentario.respuestaA,
                                    )
                                    comentarioEditando = null
                                },
                            ) { Text("Guardar", color = senaColors().green) }
                        },
                        dismissButton = {
                            TextButton(onClick = { comentarioEditando = null }) { Text("Cancelar") }
                        },
                    )
                }

                comentarioParaEliminar?.let { comentario ->
                    // Borrar una raíz arrastra sus respuestas (cascada del backend).
                    val tieneRespuestas = data.comentarios.any { it.respuestaA == comentario.id }
                    AlertDialog(
                        onDismissRequest = { comentarioParaEliminar = null },
                        title = { Text("Eliminar observación") },
                        text = {
                            Text(
                                if (tieneRespuestas) {
                                    "¿Eliminar esta observación? También se eliminarán sus respuestas."
                                } else {
                                    "¿Eliminar esta observación? Esta acción no se puede deshacer."
                                },
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                comentarioParaEliminar = null
                                projectDetailViewModel.eliminarComentario(comentario.id)
                            }) { Text("Eliminar", color = senaColors().danger) }
                        },
                        dismissButton = {
                            TextButton(onClick = { comentarioParaEliminar = null }) { Text("Cancelar") }
                        },
                    )
                }

                if (mostrarHojaEquipo) {
                    ModalBottomSheet(onDismissRequest = { mostrarHojaEquipo = false }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Agregar integrante",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (data.candidatos.isEmpty()) {
                                Text(
                                    "No hay compañeros disponibles para agregar.",
                                    color = senaColors().textSecondary,
                                )
                            } else {
                                data.candidatos.forEach { candidato ->
                                    Surface(
                                        onClick = {
                                            mostrarHojaEquipo = false
                                            projectDetailViewModel.agregarIntegrante(candidato.id)
                                        },
                                        color = senaColors().backgroundElevated,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            val nombre = candidato.nombre.ifBlank { candidato.correo }
                                            SenaAvatar(fotoBase64 = candidato.fotoUrl, nombre = nombre, modifier = Modifier.size(36.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(nombre, fontWeight = FontWeight.Bold)
                                                Text(candidato.correo, style = MaterialTheme.typography.bodySmall, color = senaColors().textLight)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CabeceraPropuesta(data: ProjectDetailData) {
    SenaPageHeader(
        title = data.project.title,
        subtitle = data.project.programa ?: "Propuesta de formación",
        icon = Icons.Default.FolderOpen,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SenaStatusBadge(status = data.project.estado)
        if (!data.fichaActiva) {
            Text(
                "Ficha finalizada · solo lectura",
                style = MaterialTheme.typography.labelSmall,
                color = senaColors().warning,
            )
        }
        if (!data.esCreador && !data.perteneceAlEquipo && !data.esAdmin) {
            Text(
                "Vista de contraparte · solo lectura",
                style = MaterialTheme.typography.labelSmall,
                color = senaColors().info,
            )
        }
    }
}

@Composable
private fun AccionesPropuesta(
    data: ProjectDetailData,
    enProceso: Boolean,
    onEnviar: () -> Unit,
    onEditar: () -> Unit,
    onEliminar: () -> Unit,
    onSimilitudes: () -> Unit,
    onAprobar: () -> Unit,
    onRechazar: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (data.puedeRevisar) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SenaButton(
                    text = "RECHAZAR",
                    onClick = onRechazar,
                    isPrimary = false,
                    enabled = !enProceso,
                    icon = Icons.Default.Close,
                    containerColor = senaColors().danger10,
                    modifier = Modifier.weight(1f),
                )
                SenaButton(
                    text = "APROBAR",
                    onClick = onAprobar,
                    enabled = !enProceso,
                    isLoading = enProceso,
                    icon = Icons.Default.Check,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SenaButton(
            text = "VER SIMILITUDES",
            onClick = onSimilitudes,
            isPrimary = false,
            enabled = !enProceso,
            icon = Icons.Default.Compare,
        )
        if (data.puedeEnviar) {
            SenaButton(
                text = if (data.project.projectStatus == ProjectStatus.RECHAZADO) "REENVIAR PROPUESTA" else "ENVIAR PROPUESTA",
                onClick = onEnviar,
                isLoading = enProceso,
                icon = Icons.AutoMirrored.Filled.Send,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (data.puedeEditar) {
                SenaButton(
                    text = "Editar",
                    onClick = onEditar,
                    isPrimary = false,
                    enabled = !enProceso,
                    icon = Icons.Default.Edit,
                    modifier = Modifier.weight(1f),
                )
            }
            if (data.puedeEliminar) {
                SenaButton(
                    text = "Eliminar",
                    onClick = onEliminar,
                    isPrimary = false,
                    enabled = !enProceso,
                    icon = Icons.Default.Delete,
                    containerColor = senaColors().danger10,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ResumenPropuesta(data: ProjectDetailData) {
    SenaSectionHeader(title = "Información General")
    SenaCard(elevation = 1.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DetailRowItem(Icons.Default.Person, "Aprendiz", data.project.studentName ?: "Sin asignar")
            HorizontalDivider(color = senaColors().borderSoft)
            DetailRowItem(Icons.Default.School, "Instructor", data.project.instructorName ?: "Sin asignar")
            HorizontalDivider(color = senaColors().borderSoft)
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    DetailRowItem(Icons.Default.CalendarToday, "Creada", formatearFechaLocal(data.project.createdAt))
                }
                Column(modifier = Modifier.weight(1f)) {
                    DetailRowItem(Icons.Default.Update, "Actualizada", formatearFechaLocal(data.project.updatedAt))
                }
            }
            HorizontalDivider(color = senaColors().borderSoft)
            DetailRowItem(Icons.Default.Category, "Área", data.project.areaAplicacion.ifBlank { "Sin definir" })
            if (!data.project.palabrasClave.isNullOrBlank()) {
                HorizontalDivider(color = senaColors().borderSoft)
                Column {
                    Text("Palabras clave", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                    Spacer(Modifier.height(6.dp))
                    Text(data.project.palabrasClave, style = MaterialTheme.typography.bodyMedium, color = senaColors().text)
                }
            }
            HorizontalDivider(color = senaColors().borderSoft)
            Column {
                Text("Resumen", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                Spacer(Modifier.height(6.dp))
                Text(
                    data.project.description.ifBlank { "Sin resumen." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = senaColors().textSecondary,
                    lineHeight = 22.sp,
                )
            }
            if (!data.project.objetivoGeneral.isNullOrBlank()) {
                HorizontalDivider(color = senaColors().borderSoft)
                Column {
                    Text("Objetivo general", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                    Spacer(Modifier.height(6.dp))
                    Text(data.project.objetivoGeneral, style = MaterialTheme.typography.bodyMedium, color = senaColors().textSecondary)
                }
            }
            if (data.project.objetivosEspecificos.isNotEmpty()) {
                HorizontalDivider(color = senaColors().borderSoft)
                Column {
                    Text("Objetivos específicos", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                    Spacer(Modifier.height(6.dp))
                    data.project.objetivosEspecificos.forEachIndexed { indice, objetivo ->
                        Text(
                            "${indice + 1}. $objetivo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = senaColors().textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilitudesPropuesta(
    data: ProjectDetailData,
    similarityRoute: String,
    onNavigate: (String) -> Unit,
) {
    var mostrarHistoricas by remember { mutableStateOf(false) }

    SenaSectionHeader(title = "Similitudes (${data.similitudes.size})")
    SenaCard(elevation = 1.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (data.similitudes.isEmpty()) {
                Text(
                    "El motor no ha detectado coincidencias vigentes para esta propuesta.",
                    color = senaColors().textSecondary,
                )
            } else {
                data.similitudes.forEach { par ->
                    val contraparte = if (par.projectId1 == data.project.id) par.project2 else par.project1
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                contraparte?.title ?: "Propuesta contraparte",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = senaColors().text,
                            )
                            Text(
                                contraparte?.studentName ?: "Autor no visible",
                                style = MaterialTheme.typography.labelSmall,
                                color = senaColors().textLight,
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
                                    similarityRoute.replace("{similarityId}", par.id.toString()),
                                )
                            },
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Ver similitud")
                        }
                    }
                }
            }

            if (data.similitudesHistoricas.isNotEmpty()) {
                HorizontalDivider(color = senaColors().borderSoft)
                TextButton(onClick = { mostrarHistoricas = !mostrarHistoricas }) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (mostrarHistoricas) "Ocultar evidencia histórica (${data.similitudesHistoricas.size})"
                        else "Ver evidencia histórica (${data.similitudesHistoricas.size})",
                    )
                }
                if (mostrarHistoricas) {
                    data.similitudesHistoricas.forEach { par ->
                        val contraparte = if (par.projectId1 == data.project.id) par.project2 else par.project1
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    contraparte?.title ?: "Versión anterior",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = senaColors().textSecondary,
                                )
                                Text(
                                    "Histórica · ${formatearFechaLocal(par.fecha)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = senaColors().warning,
                                )
                            }
                            Text(
                                par.similitudPercent,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = senaColors().textLight,
                            )
                            IconButton(
                                onClick = {
                                    onNavigate(
                                        similarityRoute.replace("{similarityId}", par.id.toString()),
                                    )
                                },
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Ver evidencia")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipoPropuesta(
    data: ProjectDetailData,
    enProceso: Boolean,
    onAgregar: () -> Unit,
    onQuitar: (Int) -> Unit,
) {
    SenaSectionHeader(title = "Equipo (${data.equipo.size})")
    SenaCard(elevation = 1.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (data.equipo.isEmpty()) {
                Text("Sin integrantes registrados.", color = senaColors().textSecondary)
            } else {
                data.equipo.forEach { miembro: TeamMember ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SenaAvatar(fotoBase64 = miembro.usuario.fotoPerfil, nombre = miembro.usuario.name, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(miembro.usuario.name, fontWeight = FontWeight.Bold)
                            val esCreador = miembro.usuario.id == data.project.studentId
                            Text(
                                if (esCreador) "Creador" else "Integrante",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (esCreador) senaColors().green else senaColors().textLight,
                            )
                        }
                        if (data.puedeGestionarEquipo &&
                            miembro.usuario.id != data.project.studentId
                        ) {
                            IconButton(
                                onClick = { onQuitar(miembro.idPivote) },
                                enabled = !enProceso,
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Quitar", tint = senaColors().danger)
                            }
                        }
                    }
                }
            }
            if (data.puedeGestionarEquipo) {
                SenaButton(
                    text = "Agregar integrante",
                    onClick = onAgregar,
                    isPrimary = false,
                    enabled = !enProceso,
                    icon = Icons.Default.PersonAdd,
                )
            }
        }
    }
}

@Composable
private fun ObservacionesPropuesta(
    data: ProjectDetailData,
    enProceso: Boolean,
    nuevoComentario: String,
    respondiendoA: Int?,
    onTextoChange: (String) -> Unit,
    onResponder: (Int?) -> Unit,
    onCancelarRespuesta: () -> Unit,
    onEnviar: () -> Unit,
    onEditar: (Comment) -> Unit,
    onEliminar: (Comment) -> Unit,
) {
    // El backend admite responder a cualquier observación del mismo proyecto:
    // se aplana el árbol completo (con protección ante ciclos) para mostrar
    // también los niveles anidados que antes quedaban invisibles.
    val hilo = aplanarHiloComentarios(data.comentarios)

    SenaSectionHeader(title = "Observaciones (${data.comentarios.size})")
    SenaCard(elevation = 1.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (data.comentarios.isEmpty()) {
                SenaEmptyState(message = "No hay observaciones para esta propuesta.", icon = Icons.AutoMirrored.Filled.Chat)
            } else {
                hilo.forEach { entrada ->
                    Row(
                        modifier = Modifier.padding(
                            // Sangría por nivel (acotada para no salirse del ancho).
                            start = (24 * entrada.profundidad.coerceAtMost(5)).dp,
                        ),
                    ) {
                        ComentarioItem(
                            comentario = entrada.comentario,
                            data = data,
                            enProceso = enProceso,
                            onResponder = onResponder,
                            onEditar = onEditar,
                            onEliminar = onEliminar,
                            esRespuesta = entrada.profundidad > 0,
                        )
                    }
                }
            }

            if (data.puedeComentar) {
                HorizontalDivider(color = senaColors().borderSoft)
                if (respondiendoA != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Respondiendo a una observación",
                            style = MaterialTheme.typography.labelSmall,
                            color = senaColors().green,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onCancelarRespuesta) { Text("Cancelar") }
                    }
                }
                SenaTextField(
                    value = nuevoComentario,
                    onValueChange = onTextoChange,
                    label = if (respondiendoA != null) "Tu respuesta" else "Nueva observación",
                    placeholder = "Escribe un comentario...",
                    singleLine = false,
                    minLines = 2,
                )
                SenaButton(
                    text = "PUBLICAR",
                    onClick = onEnviar,
                    enabled = nuevoComentario.isNotBlank() && !enProceso,
                    isLoading = enProceso,
                    icon = Icons.AutoMirrored.Filled.Send,
                )
            } else {
                Text(
                    "Solo lectura: no puedes comentar esta propuesta.",
                    style = MaterialTheme.typography.labelSmall,
                    color = senaColors().textLight,
                )
            }
        }
    }
}

@Composable
private fun ComentarioItem(
    comentario: Comment,
    data: ProjectDetailData,
    enProceso: Boolean,
    onResponder: (Int?) -> Unit,
    onEditar: (Comment) -> Unit,
    onEliminar: (Comment) -> Unit,
    esRespuesta: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SenaAvatar(fotoBase64 = comentario.autorFoto, nombre = comentario.autorNombre ?: "Usuario", modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    comentario.autorNombre ?: "Usuario",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    formatearFechaLocal(comentario.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = senaColors().textLight,
                )
            }
        }
        Text(
            comentario.texto,
            style = MaterialTheme.typography.bodyMedium,
            color = senaColors().textSecondary,
        )
        val puedeGestionar = data.puedeGestionarComentario(comentario)
        if ((!esRespuesta && data.puedeComentar) || puedeGestionar) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!esRespuesta && data.puedeComentar) {
                    TextButton(
                        onClick = { onResponder(comentario.id) },
                        enabled = !enProceso,
                    ) {
                        Text("Responder", style = MaterialTheme.typography.labelSmall, color = senaColors().green)
                    }
                }
                if (puedeGestionar) {
                    TextButton(onClick = { onEditar(comentario) }, enabled = !enProceso) {
                        Text("Editar", style = MaterialTheme.typography.labelSmall, color = senaColors().green)
                    }
                    TextButton(onClick = { onEliminar(comentario) }, enabled = !enProceso) {
                        Text("Eliminar", style = MaterialTheme.typography.labelSmall, color = senaColors().danger)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorialPropuesta(historial: List<ProjectHistory>) {
    SenaSectionHeader(title = "Historial (${historial.size})")
    SenaCard(elevation = 1.dp) {
        if (historial.isEmpty()) {
            SenaEmptyState(message = "Aún no hay movimientos registrados.", icon = Icons.Default.History)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                historial.forEachIndexed { indice, evento ->
                    Row(verticalAlignment = Alignment.Top) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(10.dp),
                                shape = RoundedCornerShape(50),
                                color = senaColors().green,
                            ) {}
                            if (indice != historial.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(36.dp)
                                        .background(senaColors().borderSoft),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(evento.accionDisplay, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${evento.usuarioNombre ?: "Sistema"} · ${formatearFechaLocal(evento.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = senaColors().textLight,
                            )
                            evento.observacion?.takeIf { it.isNotBlank() }?.let { observacion ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "“$observacion”",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = senaColors().danger,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRowItem(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(8.dp),
            color = senaColors().green.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = senaColors().green, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = senaColors().text)
        }
    }
}



package com.example.proyectwin.ui.screens.aprendiz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.ApprenticeProfile
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.ProjectDraft
import com.example.proyectwin.data.model.ProjectStatus
import com.example.proyectwin.data.model.UserRole
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import com.example.proyectwin.ui.viewmodel.ProjectsViewModel
import com.example.proyectwin.ui.viewmodel.TeamSelectionUiState
import com.example.proyectwin.ui.viewmodel.TeamSelectionViewModel

private val AREAS = listOf(
    "Desarrollo Web",
    "Inteligencia Artificial",
    "Ciencia de Datos",
    "Ciberseguridad",
    "Otro",
)

/**
 * Crear o editar una propuesta. En creación permite adjuntar equipo; en edición
 * el equipo se administra desde el detalle. "Guardar" no exige completitud
 * (borrador); "Enviar" valida igual que el backend y el servidor decide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(
    projectId: String = "",
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onSaved: (Int) -> Unit = {},
    projectsViewModel: ProjectsViewModel = hiltViewModel(),
    teamSelectionViewModel: TeamSelectionViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val idEdicion = projectId.toIntOrNull()
    val esEdicion = idEdicion != null

    val authState by authViewModel.uiState.collectAsState()
    val sessionUser = (authState as? AuthUiState.LoggedIn)?.user
    val esAdmin = sessionUser?.role == UserRole.ADMINISTRADOR.value
    // La API exige ficha activa para registrar propuestas: si el aprendiz aún
    // no pertenece a ninguna, se explica y se le lleva a unirse en vez de
    // dejarlo llenar el formulario para fallar al final.
    val sinFicha = !esEdicion &&
        sessionUser != null &&
        sessionUser.role == UserRole.APRENDIZ.value &&
        sessionUser.fichaId == null

    var precargado by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var area by remember { mutableStateOf(AREAS.first()) }
    var generalObjective by remember { mutableStateOf("") }
    var specificObjectives by remember { mutableStateOf("") }
    var estadoActual by remember { mutableStateOf(ProjectStatus.BORRADOR.value) }
    var idCreador by remember { mutableStateOf(0) }
    var idInstructor by remember { mutableStateOf<Int?>(null) }
    var idFicha by remember { mutableStateOf<Int?>(null) }
    var errorLocal by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    val saveState by projectsViewModel.saveState.collectAsState()
    val proyectoEdicion by projectsViewModel.proyectoEdicion.collectAsState()
    val cargandoEdicion by projectsViewModel.cargandoEdicion.collectAsState()
    val teamUiState by teamSelectionViewModel.uiState.collectAsState()

    if (sinFicha) {
        Scaffold(
            topBar = {
                SenaTopBar(
                    title = "Nueva Propuesta",
                    onBack = onBack,
                    showProfile = false,
                    showNotifications = false,
                )
            },
            containerColor = senaColors().background,
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                SenaCard(elevation = 2.dp) {
                    SenaEmptyState(
                        title = "Primero únete a una ficha",
                        message = "Las propuestas se registran dentro de tu ficha de formación. Pídele el código a tu instructor y únete para poder crearla; el sistema la asociará a tu programa.",
                        icon = Icons.Default.GroupAdd,
                        action = {
                            SenaButton(
                                text = "Unirme a una ficha",
                                onClick = { onNavigate(AppNavigation.APRENDIZ_JOIN_FICHA) },
                                icon = Icons.AutoMirrored.Filled.Login,
                            )
                        },
                    )
                }
            }
        }
        return
    }

    // Edición: carga y precarga una sola vez.
    LaunchedEffect(idEdicion) {
        if (idEdicion != null) projectsViewModel.cargarParaEdicion(idEdicion)
    }
    LaunchedEffect(proyectoEdicion) {
        val p = proyectoEdicion
        if (esEdicion && p != null && !precargado) {
            title = p.title
            summary = p.description
            keywords = p.palabrasClave.orEmpty()
            area = p.areaAplicacion.ifBlank { AREAS.first() }
            generalObjective = p.objetivoGeneral.orEmpty()
            specificObjectives = p.objetivosEspecificos.joinToString("\n")
            estadoActual = p.estado
            idCreador = p.studentId ?: 0
            idInstructor = p.instructorId
            idFicha = p.fichaId
            precargado = true
        }
    }

    // Creación: compañeros de la ficha para el equipo.
    LaunchedEffect(esEdicion) {
        if (!esEdicion) teamSelectionViewModel.loadTeam()
    }

    LaunchedEffect(saveState) {
        val estado = saveState
        if (estado is ProjectsViewModel.SaveState.Success) {
            projectsViewModel.resetSaveState()
            projectsViewModel.resetEdicion()
            onSaved(estado.projectId)
        }
    }

    val enviando = saveState is ProjectsViewModel.SaveState.Loading
    val proyectoCargado: Project? = proyectoEdicion
    val puedeGuardarYEnviar = !esEdicion || (proyectoCargado?.puedeEnviar ?: true)
    // Un aprobado no se edita en el flujo normal, pero el admin conserva su
    // intervención (el backend la devuelve a revisión si cambia el contenido).
    val puedeGuardar = !esEdicion || esAdmin || (proyectoCargado?.esEditable ?: true)

    fun draftActual(): ProjectDraft = ProjectDraft(
        titulo = title.trim(),
        resumen = summary.trim(),
        palabrasClave = keywords.trim().ifBlank { null }?.split(",")
            ?.map { it.trim() }?.filter { it.isNotBlank() }?.joinToString(","),
        areaAplicacion = area,
        objetivoGeneral = generalObjective.trim().ifBlank { null },
        objetivosEspecificos = specificObjectives.split("\n")
            .map { it.trim() }.filter { it.isNotBlank() },
        estado = estadoActual,
        idCreador = idCreador,
        idInstructorAsignado = idInstructor,
        idClassGroup = idFicha,
    )

    // Validación local espejo de la del backend (POST /projects/{id}/enviar):
    // cada mensaje dice qué campo está mal y cuánto falta, y se muestra bajo el
    // campo correspondiente en cuanto se intenta enviar.
    var intentoEnvio by remember { mutableStateOf(false) }

    fun errorTitulo(): String? = when {
        title.isBlank() -> "Escribe el título de la propuesta (mínimo 5 caracteres)."
        title.trim().length < 5 -> "Al título le faltan ${5 - title.trim().length} caracteres (mínimo 5)."
        else -> null
    }

    fun errorResumen(): String? = when {
        summary.isBlank() -> "Describe tu propuesta en el resumen (mínimo 20 caracteres)."
        summary.trim().length < 20 -> "Al resumen le faltan ${20 - summary.trim().length} caracteres (mínimo 20)."
        else -> null
    }

    fun errorArea(): String? = when {
        area.isBlank() -> "Selecciona el área de aplicación de tu propuesta."
        area.trim().length < 3 -> "El área de aplicación debe tener al menos 3 caracteres."
        else -> null
    }

    fun errorObjetivoGeneral(): String? = when {
        generalObjective.isBlank() -> "Redacta el objetivo general (mínimo 15 caracteres)."
        generalObjective.trim().length < 15 ->
            "Al objetivo general le faltan ${15 - generalObjective.trim().length} caracteres (mínimo 15)."
        else -> null
    }

    val objetivosValidos = specificObjectives.split("\n").count { it.trim().length >= 8 }
    fun errorObjetivosEspecificos(): String? = if (objetivosValidos < 2) {
        "Agrega al menos 2 objetivos específicos, uno por línea, de 8 caracteres o más (válidos: $objetivosValidos)."
    } else {
        null
    }

    val fieldErrors = (saveState as? ProjectsViewModel.SaveState.Error)?.fieldErrors.orEmpty()

    // El error del servidor manda si existe; el local aparece solo tras
    // intentar enviar y se actualiza en vivo al corregir.
    fun errorCampo(clave: String, local: () -> String?): String? =
        fieldErrors[clave] ?: if (intentoEnvio) local() else null

    val errTitulo = errorCampo("titulo") { errorTitulo() }
    val errResumen = errorCampo("resumen") { errorResumen() }
    val errArea = errorCampo("area_aplicacion") { errorArea() }
    val errObjetivoGeneral = errorCampo("objetivo_general") { errorObjetivoGeneral() }
    val errObjetivosEspecificos = errorCampo("objetivos_especificos") { errorObjetivosEspecificos() }

    val selectedIds = (teamUiState as? TeamSelectionUiState.Success)?.selectedIds.orEmpty()

    Scaffold(
        topBar = {
            SenaTopBar(
                title = if (esEdicion) "Editar Propuesta" else "Nueva Propuesta",
                onBack = onBack,
                showProfile = false,
                showNotifications = false
            )
        },
        containerColor = senaColors().background,
        bottomBar = {
            SenaBottomBar {
                SenaButton(
                    text = "Cancelar",
                    onClick = onBack,
                    isPrimary = false,
                    modifier = Modifier.weight(1f)
                )
                if (!esEdicion) {
                    SenaButton(
                        text = "Borrador",
                        onClick = {
                            errorLocal = null
                            projectsViewModel.guardar(draftActual(), null, selectedIds.toList(), enviarDespues = false)
                        },
                        isPrimary = false,
                        isLoading = enviando,
                        modifier = Modifier.weight(1f)
                    )
                }
                SenaButton(
                    text = when {
                        !esEdicion -> "Enviar"
                        puedeGuardarYEnviar -> "Guardar y enviar"
                        else -> "Guardar cambios"
                    },
                    onClick = {
                        errorLocal = null
                        val enviar = puedeGuardarYEnviar
                        if (enviar) {
                            intentoEnvio = true
                            val pendientes = listOf(
                                errorTitulo(),
                                errorResumen(),
                                errorArea(),
                                errorObjetivoGeneral(),
                                errorObjetivosEspecificos(),
                            ).filterNotNull()
                            if (pendientes.isNotEmpty()) {
                                errorLocal = "No se puede enviar todavía. Corrige los campos marcados y vuelve a intentarlo."
                            } else {
                                projectsViewModel.guardar(
                                    draft = draftActual(),
                                    proyectoExistente = idEdicion,
                                    miembros = if (esEdicion) emptyList() else selectedIds.toList(),
                                    enviarDespues = true,
                                )
                            }
                        } else {
                            projectsViewModel.guardar(
                                draft = draftActual(),
                                proyectoExistente = idEdicion,
                                miembros = if (esEdicion) emptyList() else selectedIds.toList(),
                                enviarDespues = false,
                            )
                        }
                    },
                    isLoading = enviando,
                    enabled = puedeGuardar,
                    icon = if (puedeGuardarYEnviar) Icons.Default.Send else Icons.Default.Save,
                    modifier = Modifier.weight(1.4f)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SenaPageHeader(
                title = if (esEdicion) "Editar Propuesta" else "Nueva Propuesta",
                subtitle = if (esEdicion) {
                    "Ajusta el contenido y guarda los cambios."
                } else {
                    "Describe tu idea; puedes guardarla como borrador y enviarla después."
                },
                icon = if (esEdicion) Icons.Default.Edit else Icons.Default.AddCircle
            )

            if (esEdicion && cargandoEdicion) {
                SenaLoadingState()
            }

            if (esEdicion) {
                SenaAlertBanner(
                    title = "Estado actual",
                    message = when (ProjectStatus.fromValue(estadoActual)) {
                        ProjectStatus.BORRADOR -> "Borrador: puedes editarlo y enviarlo cuando quieras."
                        ProjectStatus.PENDIENTE -> "En revisión: tus cambios se analizarán de nuevo."
                        ProjectStatus.RECHAZADO -> "Rechazado: ajusta y reenvía cuando estés listo."
                        ProjectStatus.APROBADO -> "Aprobado: solo un administrador puede modificarlo; si cambia el contenido, volverá a revisión."
                    },
                    icon = Icons.Default.Info,
                    color = senaColors().info
                )
            }

            errorLocal?.let {
                SenaAlertBanner(
                    title = "Revisa el formulario",
                    message = it,
                    icon = Icons.Default.Error,
                    color = senaColors().danger
                )
            }

            (saveState as? ProjectsViewModel.SaveState.Error)?.let { estado ->
                SenaAlertBanner(
                    title = "Error",
                    message = estado.message,
                    icon = Icons.Default.Error,
                    color = senaColors().danger
                )
                if (estado.projectId != null) {
                    SenaButton(
                        text = "IR A LA PROPUESTA",
                        onClick = { onSaved(estado.projectId) },
                        isPrimary = false,
                        icon = Icons.Default.OpenInNew
                    )
                } else if (estado.message.contains("ficha", ignoreCase = true)) {
                    // Sesión desactualizada (ficha cambiada en otro equipo):
                    // el backend rechazó el alta; se ofrece unirse a una ficha.
                    SenaButton(
                        text = "Unirme a una ficha",
                        onClick = { onNavigate(AppNavigation.APRENDIZ_JOIN_FICHA) },
                        isPrimary = false,
                        icon = Icons.Default.GroupAdd,
                    )
                }
            }

            SenaSectionHeader(title = "Información del Proyecto")
            SenaCard {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    SenaTextField(
                        value = title,
                        onValueChange = { title = it; errorLocal = null },
                        label = "Título del proyecto *",
                        placeholder = "Ej: Sistema de gestión de inventarios",
                        isError = errTitulo != null,
                        supportingText = errTitulo
                    )

                    SenaTextField(
                        value = summary,
                        onValueChange = { summary = it; errorLocal = null },
                        label = "Resumen ejecutivo *",
                        placeholder = "Describe brevemente el alcance de tu propuesta...",
                        singleLine = false,
                        minLines = 4,
                        isError = errResumen != null,
                        supportingText = errResumen
                    )

                    SenaTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = "Palabras clave (separadas por coma)",
                        placeholder = "web, inventario, sena",
                        isError = fieldErrors.containsKey("palabras_clave"),
                        supportingText = fieldErrors["palabras_clave"]
                    )
                }
            }

            SenaSectionHeader(title = "Área de Aplicación")
            SenaCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AREAS.chunked(2).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            fila.forEach { opcion ->
                                SenaChip(
                                    text = opcion,
                                    color = senaColors().green,
                                    isSelected = area == opcion,
                                    onClick = { area = opcion; errorLocal = null },
                                )
                            }
                        }
                    }
                    fieldErrors["area_aplicacion"]?.let {
                        Text(it, color = senaColors().danger, style = MaterialTheme.typography.bodySmall)
                    }
                    if (fieldErrors["area_aplicacion"] == null && errArea != null) {
                        Text(errArea, color = senaColors().danger, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            SenaSectionHeader(title = "Objetivos")
            SenaCard {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    SenaTextField(
                        value = generalObjective,
                        onValueChange = { generalObjective = it; errorLocal = null },
                        label = "Objetivo general *",
                        placeholder = "¿Qué esperas lograr con tu propuesta?",
                        singleLine = false,
                        minLines = 3,
                        isError = errObjetivoGeneral != null,
                        supportingText = errObjetivoGeneral
                    )

                    SenaTextField(
                        value = specificObjectives,
                        onValueChange = { specificObjectives = it; errorLocal = null },
                        label = "Objetivos específicos * (uno por línea, mínimo 2)",
                        placeholder = "Analizar los requerimientos\nDiseñar la solución\nImplementar el prototipo",
                        singleLine = false,
                        minLines = 4,
                        isError = errObjetivosEspecificos != null,
                        supportingText = errObjetivosEspecificos
                    )
                }
            }

            if (!esEdicion) {
                SenaSectionHeader(title = "Equipo (opcional)")
                SenaCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (val state = teamUiState) {
                            is TeamSelectionUiState.Loading -> {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            is TeamSelectionUiState.Error -> {
                                Text(state.message, color = senaColors().textSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            is TeamSelectionUiState.Success -> {
                                if (state.members.isEmpty()) {
                                    Text("No hay compañeros disponibles en tu ficha.", color = senaColors().textSecondary)
                                } else {
                                    TeamMemberList(
                                        members = state.members,
                                        selectedIds = state.selectedIds,
                                        onToggle = { teamSelectionViewModel.toggleMember(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
fun TeamMemberList(
    members: List<ApprenticeProfile>,
    selectedIds: Set<Int>,
    onToggle: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        members.forEach { member ->
            val selected = selectedIds.contains(member.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(member.id) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            if (selected) senaColors().green else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    member.nombre.ifBlank { member.correo },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

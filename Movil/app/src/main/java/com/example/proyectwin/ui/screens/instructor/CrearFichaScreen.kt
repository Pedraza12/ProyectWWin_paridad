package com.example.proyectwin.ui.screens.instructor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.CredencialesUsuario
import com.example.proyectwin.data.model.UserRole
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import com.example.proyectwin.ui.viewmodel.FichaFormUiState
import com.example.proyectwin.ui.viewmodel.FichaFormViewModel
import com.example.proyectwin.ui.viewmodel.FichaSaveState
import kotlinx.coroutines.launch

/**
 * Alta/edición real de ficha: el código lo genera el backend, el instructor
 * del rol instructor es su propia fila y el admin puede elegir entre todos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearFichaScreen(
    fichaId: String = "",
    onBack: () -> Unit,
    onSaved: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    fichaFormViewModel: FichaFormViewModel = hiltViewModel(),
) {
    val idEdicion = fichaId.toIntOrNull()
    val esEdicion = idEdicion != null

    val authState by authViewModel.uiState.collectAsState()
    val user = (authState as? AuthUiState.LoggedIn)?.user
    val esAdmin = user?.role == UserRole.ADMINISTRADOR.value

    val uiState by fichaFormViewModel.uiState.collectAsState()
    val saveState by fichaFormViewModel.saveState.collectAsState()
    val creandoAprendiz by fichaFormViewModel.creandoAprendiz.collectAsState()

    var precargado by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    var numero by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf("activo") }
    var idPrograma by remember { mutableStateOf<Int?>(null) }
    var idInstructor by remember { mutableStateOf<Int?>(null) }

    // Roster en edición: se guarda con el PUT (el backend sincroniza altas/bajas).
    var aprendicesSeleccionados by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var mostrarCandidatos by remember { mutableStateOf(false) }
    var dialogoCrearAprendiz by remember { mutableStateOf(false) }
    var credencialesAprendiz by remember { mutableStateOf<CredencialesUsuario?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(idEdicion) { fichaFormViewModel.load(idEdicion) }

    LaunchedEffect(uiState) {
        val ready = uiState as? FichaFormUiState.Ready ?: return@LaunchedEffect
        if (!precargado) {
            ready.data.ficha?.let { ficha ->
                nombre = ficha.nombre.orEmpty()
                numero = ficha.numero.orEmpty()
                estado = ficha.estado
                idPrograma = ficha.idPrograma
                idInstructor = ficha.instructorId
                aprendicesSeleccionados = ficha.estudiantes.map { it.id }.toSet()
            } ?: run {
                // Alta: el instructor usa su propia fila; el admin elige.
                idInstructor = if (esAdmin) null else ready.data.miInstructor?.id
            }
            precargado = true
        }
    }

    LaunchedEffect(saveState) {
        if (saveState is FichaSaveState.Success) {
            fichaFormViewModel.resetSaveState()
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = if (esEdicion) "Editar Ficha" else "Nueva Ficha",
                onBack = onBack,
                showProfile = true,
                showNotifications = true,
            )
        },
        containerColor = senaColors().background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when (val estadoUi = uiState) {
            is FichaFormUiState.Loading ->
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is FichaFormUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estadoUi.message, onRetry = { fichaFormViewModel.load(idEdicion) })
            }
            is FichaFormUiState.Ready -> {
                val data = estadoUi.data
                val guardando = saveState is FichaSaveState.Loading
                val errores = (saveState as? FichaSaveState.Error)?.fieldErrors.orEmpty()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SenaPageHeader(
                        title = if (esEdicion) "Editar Ficha" else "Nueva Ficha",
                        subtitle = if (esEdicion) {
                            "Actualiza los datos del grupo de formación."
                        } else {
                            "El código único lo genera el servidor al guardar."
                        },
                        icon = if (esEdicion) Icons.Default.Edit else Icons.Default.AddCircle,
                    )

                    if (esEdicion && data.ficha?.codigo?.isNotBlank() == true) {
                        SenaCard(elevation = 1.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Código de ficha", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                                    Text(
                                        data.ficha.codigo,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = senaColors().green,
                                    )
                                }
                                SenaCopyButton(textToCopy = data.ficha.codigo, label = "Copiar")
                            }
                        }
                    }

                    (saveState as? FichaSaveState.Error)?.let {
                        SenaAlertBanner(
                            title = "Error",
                            message = it.message,
                            icon = Icons.Default.Error,
                            color = senaColors().danger,
                        )
                    }

                    SenaCard {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SenaTextField(
                                value = nombre,
                                onValueChange = { nombre = it },
                                label = "Nombre de la ficha *",
                                placeholder = "Ej: Ficha ADSO 2026",
                                isError = errores.containsKey("nombre"),
                                supportingText = errores["nombre"],
                            )
                            SenaTextField(
                                value = numero,
                                onValueChange = { numero = it },
                                label = "Número de ficha *",
                                placeholder = "Ej: 3067030",
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                isError = errores.containsKey("numero"),
                                supportingText = errores["numero"],
                            )

                            Text("Estado", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("activo" to "Activo", "finalizado" to "Finalizado").forEach { (valor, etiqueta) ->
                                    SenaChip(
                                        text = etiqueta,
                                        color = if (valor == "activo") senaColors().success else senaColors().warning,
                                        isSelected = estado == valor,
                                        onClick = { estado = valor },
                                    )
                                }
                            }

                            Text("Programa de formación *", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                            if (data.programas.isEmpty()) {
                                Text("No hay programas disponibles.", color = senaColors().textSecondary)
                            } else {
                                var programasExpanded by remember { mutableStateOf(false) }
                                val seleccionado = data.programas.firstOrNull { it.id == idPrograma }
                                ExposedDropdownMenuBox(
                                    expanded = programasExpanded,
                                    onExpandedChange = { programasExpanded = it },
                                ) {
                                    OutlinedTextField(
                                        value = seleccionado?.nombre ?: "Selecciona un programa",
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = programasExpanded) },
                                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        isError = errores.containsKey("id_programa"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = senaColors().green,
                                            unfocusedBorderColor = if (errores.containsKey("id_programa")) senaColors().danger else senaColors().border,
                                            focusedContainerColor = senaColors().inputBackground,
                                            unfocusedContainerColor = senaColors().inputBackground,
                                        ),
                                    )
                                    ExposedDropdownMenu(
                                        expanded = programasExpanded,
                                        onDismissRequest = { programasExpanded = false },
                                    ) {
                                        data.programas.forEach { programa ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(programa.nombre)
                                                        programa.redNombre?.let {
                                                            Text(it, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    idPrograma = programa.id
                                                    programasExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                                errores["id_programa"]?.let {
                                    Text(it, color = senaColors().danger, style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            if (esAdmin) {
                                Text("Instructor responsable *", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                                var instructoresExpanded by remember { mutableStateOf(false) }
                                val seleccionado = data.instructores.firstOrNull { it.id == idInstructor }
                                ExposedDropdownMenuBox(
                                    expanded = instructoresExpanded,
                                    onExpandedChange = { instructoresExpanded = it },
                                ) {
                                    OutlinedTextField(
                                        value = seleccionado?.nombre ?: "Selecciona un instructor",
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = instructoresExpanded) },
                                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = senaColors().green,
                                            unfocusedBorderColor = senaColors().border,
                                            focusedContainerColor = senaColors().inputBackground,
                                            unfocusedContainerColor = senaColors().inputBackground,
                                        ),
                                    )
                                    ExposedDropdownMenu(
                                        expanded = instructoresExpanded,
                                        onDismissRequest = { instructoresExpanded = false },
                                    ) {
                                        data.instructores.forEach { instructor ->
                                            DropdownMenuItem(
                                                text = { Text(instructor.nombre.ifBlank { "Instructor #${instructor.id}" }) },
                                                onClick = {
                                                    idInstructor = instructor.id
                                                    instructoresExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    "Instructor responsable: ${data.miInstructor?.nombre ?: user?.name.orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = senaColors().textSecondary,
                                )
                            }
                        }
                    }

                    // ---- Roster de aprendices ----
                    // La sincronización con traslados es del admin; el instructor
                    // ve el integrado actual (el backend rechaza mover aprendices
                    // entre fichas si no es administrador).
                    val puedeGestionarRoster = esAdmin
                    SenaSectionHeader(title = "Aprendices (${aprendicesSeleccionados.size})")
                    SenaCard {
                        // Se combinan el roster actual y los candidatos para que
                        // un aprendiz recién agregado se vea al instante (aún sin
                        // guardar), sin esperar a recargar la ficha.
                        val personas = buildList {
                            data.ficha?.estudiantes?.forEach {
                                add(ItemAprendiz(it.id, it.name, it.email, it.fotoPerfil))
                            }
                            data.candidatosAprendices.forEach {
                                add(ItemAprendiz(it.idUsuario, it.nombre.ifBlank { it.correo }, it.correo, it.foto))
                            }
                        }.distinctBy { it.id }
                        val seleccionados = personas.filter { it.id in aprendicesSeleccionados }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (seleccionados.isEmpty()) {
                                Text(
                                    if (puedeGestionarRoster) {
                                        "Aún no hay aprendices en la lista. Agrega desde tu alcance."
                                    } else {
                                        "Sin aprendices registrados en esta ficha."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = senaColors().textSecondary,
                                )
                            } else {
                                seleccionados.forEach { aprendiz ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SenaAvatar(
                                            fotoBase64 = aprendiz.foto,
                                            nombre = aprendiz.nombre,
                                            modifier = Modifier.size(36.dp),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(aprendiz.nombre, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                            Text(aprendiz.correo, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                                        }
                                        if (puedeGestionarRoster) {
                                            IconButton(
                                                onClick = { aprendicesSeleccionados = aprendicesSeleccionados - aprendiz.id },
                                            ) {
                                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Quitar", tint = senaColors().danger)
                                            }
                                        }
                                    }
                                }
                            }
                            if (puedeGestionarRoster) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SenaButton(
                                        text = "Agregar",
                                        onClick = { mostrarCandidatos = true },
                                        isPrimary = false,
                                        icon = Icons.Default.PersonAdd,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (esEdicion) {
                                        SenaButton(
                                            text = "Cuenta nueva",
                                            onClick = { dialogoCrearAprendiz = true },
                                            isPrimary = false,
                                            enabled = !creandoAprendiz,
                                            isLoading = creandoAprendiz,
                                            icon = Icons.Default.PersonAddAlt,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SenaButton(
                            text = "Cancelar",
                            onClick = onBack,
                            isPrimary = false,
                            modifier = Modifier.weight(1f),
                        )
                        SenaButton(
                            text = if (esEdicion) "Guardar cambios" else "Crear ficha",
                            onClick = {
                                if (idPrograma == null || idInstructor == null) {
                                    fichaFormViewModel.resetSaveState()
                                } else {
                                    fichaFormViewModel.guardar(
                                        fichaId = idEdicion,
                                        nombre = nombre.trim(),
                                        numero = numero.trim(),
                                        estado = estado,
                                        idPrograma = idPrograma!!,
                                        idInstructor = idInstructor!!,
                                        aprendices = aprendicesSeleccionados.toList(),
                                    )
                                }
                            },
                            isLoading = guardando,
                            enabled = !guardando && idPrograma != null && idInstructor != null &&
                                nombre.isNotBlank() && numero.isNotBlank(),
                            icon = Icons.Default.Save,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    // ---- Selector de aprendices del alcance (sin red: actualización inmediata) ----
    val dataSheet = (uiState as? FichaFormUiState.Ready)?.data
    if (mostrarCandidatos && dataSheet != null) {
        ModalBottomSheet(onDismissRequest = { mostrarCandidatos = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Agregar aprendices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { fichaFormViewModel.refrescarCandidatos() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar lista", tint = senaColors().green)
                    }
                }
                val candidatos = dataSheet.candidatosAprendices
                    .filterNot { it.idUsuario in aprendicesSeleccionados }
                if (candidatos.isEmpty()) {
                    Text(
                        "No hay más aprendices disponibles en tu alcance. Crea la cuenta nueva o actualiza la lista.",
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().textSecondary,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        items(candidatos, key = { it.idUsuario }) { candidato ->
                            Surface(
                                onClick = {
                                    aprendicesSeleccionados = aprendicesSeleccionados + candidato.idUsuario
                                },
                                color = senaColors().backgroundElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SenaAvatar(
                                        fotoBase64 = candidato.foto,
                                        nombre = candidato.nombre.ifBlank { candidato.correo },
                                        modifier = Modifier.size(36.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            candidato.nombre.ifBlank { candidato.correo },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(candidato.correo, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                                        if (candidato.fichaActualId != null && candidato.fichaActualId != idEdicion) {
                                            Text(
                                                "Pertenece a otra ficha · un administrador puede trasladarlo",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = senaColors().warning,
                                            )
                                        }
                                    }
                                    Icon(Icons.Default.Add, contentDescription = "Agregar", tint = senaColors().green)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // ---- Alta de cuenta nueva desde la edición de la ficha ----
    if (dialogoCrearAprendiz && idEdicion != null) {
        CrearCuentaAprendizDialog(
            onDismiss = { dialogoCrearAprendiz = false },
            onCrear = { nombreA, apellidoA, tipoDoc, numDoc, correo ->
                dialogoCrearAprendiz = false
                fichaFormViewModel.crearAprendizEnFicha(idEdicion, nombreA, apellidoA, tipoDoc, numDoc, correo) { resultado ->
                    resultado.fold(
                        onSuccess = { credenciales ->
                            if (credenciales != null) {
                                aprendicesSeleccionados = aprendicesSeleccionados + credenciales.usuario.id
                                credencialesAprendiz = credenciales
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("Aprendiz matriculado en la ficha.") }
                            }
                        },
                        onFailure = { e ->
                            scope.launch { snackbarHostState.showSnackbar(e.message ?: "No se pudo crear el aprendiz.") }
                        },
                    )
                }
            },
        )
    }

    credencialesAprendiz?.let { datos ->
        AlertDialog(
            onDismissRequest = { credencialesAprendiz = null },
            icon = { Icon(Icons.Default.Key, contentDescription = null, tint = senaColors().green) },
            title = { Text("Credenciales del aprendiz", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Usuario: ${datos.username}", fontWeight = FontWeight.Bold)
                    Text("Contraseña temporal: ${datos.passwordTemporal}", fontWeight = FontWeight.Bold)
                    Text(
                        if (datos.enviadas) "El correo fue enviado." else "El correo no pudo enviarse; reenvíalo desde Usuarios.",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (datos.enviadas) senaColors().success else senaColors().warning,
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        clipboard.setText(
                            AnnotatedString("Usuario: ${datos.username}\nContraseña: ${datos.passwordTemporal}"),
                        )
                    }) { Text("Copiar", color = senaColors().green) }
                    TextButton(onClick = { credencialesAprendiz = null }) {
                        Text("Entendido", color = senaColors().green, fontWeight = FontWeight.Bold)
                    }
                }
            },
        )
    }
}

/** Fila del roster en edición (puede venir de la ficha o de los candidatos). */
private data class ItemAprendiz(
    val id: Int,
    val nombre: String,
    val correo: String,
    val foto: String?,
)

@Composable
private fun CrearCuentaAprendizDialog(
    onDismiss: () -> Unit,
    onCrear: (nombre: String, apellido: String, tipoDoc: String, numDoc: String, correo: String) -> Unit,
) {
    var nombre by remember { mutableStateOf("") }
    var apellido by remember { mutableStateOf("") }
    var tipoDoc by remember { mutableStateOf("CC") }
    var numDoc by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear cuenta de aprendiz", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = senaColors().danger, style = MaterialTheme.typography.labelSmall) }
                SenaTextField(value = nombre, onValueChange = { nombre = it }, label = "Nombre")
                SenaTextField(value = apellido, onValueChange = { apellido = it }, label = "Apellido")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CC", "TI", "CE", "PPT").forEach { tipo ->
                        SenaChip(text = tipo, color = senaColors().green, isSelected = tipoDoc == tipo, onClick = { tipoDoc = tipo })
                    }
                }
                SenaTextField(value = numDoc, onValueChange = { numDoc = it }, label = "Número de documento")
                SenaTextField(value = correo, onValueChange = { correo = it }, label = "Correo")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    nombre.isBlank() || apellido.isBlank() -> "Nombre y apellido son obligatorios."
                    numDoc.isBlank() -> "El documento es obligatorio."
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches() -> "Correo inválido."
                    else -> null
                }
                if (error == null) onCrear(nombre.trim(), apellido.trim(), tipoDoc, numDoc.trim(), correo.trim())
            }) { Text("Crear", color = senaColors().green, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

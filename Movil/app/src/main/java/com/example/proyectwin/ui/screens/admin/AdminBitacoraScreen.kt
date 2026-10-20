package com.example.proyectwin.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.util.formatearFechaHoraLocal
import com.example.proyectwin.ui.viewmodel.BitacoraUiState
import com.example.proyectwin.ui.viewmodel.BitacoraViewModel
import java.time.LocalDate

/** Selector abierto en la hoja inferior de filtros. */
private enum class FiltroBitacora { USUARIO, ACCION, ENTIDAD }

/** Acciones auditables: mismos valores y etiquetas que la bitácora web. */
private val ACCIONES_BITACORA: List<Pair<String?, String>> = listOf(
    null to "Todas",
    "crear_usuario" to "Crear usuario",
    "actualizar_usuario" to "Actualizar usuario",
    "eliminar_usuario" to "Eliminar usuario",
    "reenviar_credenciales" to "Reenviar credenciales",
    "restablecer_credenciales" to "Restablecer credenciales",
    "exportar_credenciales" to "Exportar credenciales",
    "cambiar_correo" to "Cambiar correo",
    "cambiar_clave" to "Cambiar contraseña",
    "solicitar_reset" to "Solicitar recuperación",
    "restablecer_clave" to "Restablecer contraseña",
    "crear_propuesta" to "Crear propuesta",
    "enviar_propuesta" to "Enviar propuesta",
    "revisar_propuesta" to "Revisar propuesta",
    "eliminar_propuesta" to "Eliminar propuesta",
    "crear_ficha" to "Crear ficha",
    "actualizar_ficha" to "Actualizar ficha",
    "finalizar_ficha" to "Finalizar ficha",
    "anular_ficha" to "Anular ficha",
    "reasignar_ficha" to "Reasignar ficha",
    "eliminar_ficha" to "Eliminar ficha",
    "agregar_aprendiz" to "Agregar aprendiz",
    "asignar_aprendices" to "Asignar aprendices",
    "crear_aprendiz" to "Crear aprendiz",
    "actualizar_aprendiz" to "Actualizar aprendiz",
    "eliminar_aprendiz" to "Eliminar aprendiz",
    "actualizar_instructor" to "Actualizar instructor",
    "eliminar_instructor" to "Eliminar instructor",
    "config_motor" to "Configurar motor",
    "recalibrar_motor" to "Recalibrar motor",
    "resolver_soporte" to "Resolver soporte",
    "eliminar_red" to "Eliminar red",
    "eliminar_programa" to "Eliminar programa",
)

/** Entidades auditables: mismos valores y etiquetas que la bitácora web. */
private val ENTIDADES_BITACORA: List<Pair<String?, String>> = listOf(
    null to "Todas",
    "general_users" to "Usuarios",
    "projects" to "Propuestas",
    "class_groups" to "Fichas",
    "similarities" to "Similitudes",
    "motor_configs" to "Motor",
    "apprentices" to "Aprendices",
    "instructors" to "Instructores",
    "bug_reports" to "Soporte",
    "knowledge_networks" to "Redes",
    "training_programs" to "Programas",
)

/** Bitácora de acciones sensibles (solo lectura, admin). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBitacoraScreen(
    onBack: () -> Unit,
    bitacoraViewModel: BitacoraViewModel = hiltViewModel(),
) {
    val uiState by bitacoraViewModel.uiState.collectAsState()
    val usuarios by bitacoraViewModel.usuarios.collectAsState()

    var usuarioId by remember { mutableStateOf<Int?>(null) }
    var accion by remember { mutableStateOf<String?>(null) }
    var entidad by remember { mutableStateOf<String?>(null) }
    var desdeMascara by remember { mutableStateOf("") }
    var hastaMascara by remember { mutableStateOf("") }
    var errorFecha by remember { mutableStateOf<String?>(null) }
    var hojaAbierta by remember { mutableStateOf<FiltroBitacora?>(null) }

    val etiquetaUsuario = usuarios.firstOrNull { it.id == usuarioId }?.name ?: "Todos"
    val etiquetaAccion = ACCIONES_BITACORA.firstOrNull { it.first == accion }?.second ?: "Todas"
    val etiquetaEntidad = ENTIDADES_BITACORA.firstOrNull { it.first == entidad }?.second ?: "Todas"
    val hayFiltrosFormulario = usuarioId != null || accion != null || entidad != null ||
        desdeMascara.isNotBlank() || hastaMascara.isNotBlank()

    /** Restablece el formulario y pide la bitácora sin filtros. */
    val limpiarTodo: () -> Unit = {
        usuarioId = null
        accion = null
        entidad = null
        desdeMascara = ""
        hastaMascara = ""
        errorFecha = null
        bitacoraViewModel.limpiar()
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "Bitácora",
                onBack = onBack,
                showProfile = true,
                showNotifications = true,
            )
        },
        containerColor = senaColors().background,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SenaPageHeader(
                    title = "Bitácora",
                    subtitle = "Registro inmutable de acciones administrativas.",
                    icon = Icons.Default.History,
                )
            }
            item {
                SenaCard(elevation = 1.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SelectorCampo(
                            etiqueta = "Responsable",
                            valor = etiquetaUsuario,
                            onClick = { hojaAbierta = FiltroBitacora.USUARIO },
                        )
                        SelectorCampo(
                            etiqueta = "Acción",
                            valor = etiquetaAccion,
                            onClick = { hojaAbierta = FiltroBitacora.ACCION },
                        )
                        SelectorCampo(
                            etiqueta = "Entidad",
                            valor = etiquetaEntidad,
                            onClick = { hojaAbierta = FiltroBitacora.ENTIDAD },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CampoFechaNumerico(
                                valor = desdeMascara,
                                onValorChange = { desdeMascara = it; errorFecha = null },
                                label = "Desde",
                                modifier = Modifier.weight(1f),
                            )
                            CampoFechaNumerico(
                                valor = hastaMascara,
                                onValorChange = { hastaMascara = it; errorFecha = null },
                                label = "Hasta",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        errorFecha?.let {
                            Text(it, color = senaColors().danger, style = MaterialTheme.typography.labelSmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.weight(1f)) {
                                SenaButton(
                                    text = "APLICAR FILTROS",
                                    onClick = {
                                        val desdeApi = fechaApiDeMascara(desdeMascara)
                                        val hastaApi = fechaApiDeMascara(hastaMascara)
                                        errorFecha = when {
                                            desdeMascara.isNotBlank() && desdeApi == null ->
                                                "La fecha \"Desde\" debe ser una fecha válida DD/MM/AAAA."
                                            hastaMascara.isNotBlank() && hastaApi == null ->
                                                "La fecha \"Hasta\" debe ser una fecha válida DD/MM/AAAA."
                                            desdeApi != null && hastaApi != null && desdeApi > hastaApi ->
                                                "\"Desde\" no puede ser posterior a \"Hasta\"."
                                            else -> null
                                        }
                                        if (errorFecha == null) {
                                            bitacoraViewModel.load(
                                                accion = accion,
                                                entidad = entidad,
                                                idUsuario = usuarioId,
                                                // El backend filtra por fecha ISO (Y-m-d).
                                                desde = desdeApi,
                                                hasta = hastaApi,
                                            )
                                        }
                                    },
                                    icon = Icons.Default.FilterAlt,
                                )
                            }
                            if (hayFiltrosFormulario) {
                                Box(Modifier.weight(1f)) {
                                    SenaButton(
                                        text = "LIMPIAR FILTROS",
                                        onClick = limpiarTodo,
                                        isPrimary = false,
                                        icon = Icons.Default.FilterAltOff,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (val estado = uiState) {
                is BitacoraUiState.Loading -> item { SenaLoadingState() }
                is BitacoraUiState.Error -> item {
                    SenaErrorState(message = estado.message, onRetry = { bitacoraViewModel.recargar() })
                }
                is BitacoraUiState.Success -> {
                    item {
                        Text(
                            "${estado.registros.size} registros",
                            style = MaterialTheme.typography.labelSmall,
                            color = senaColors().textLight,
                        )
                    }
                    if (estado.registros.isEmpty()) {
                        item {
                            val accionLimpiar: (@Composable () -> Unit)? = if (estado.filtros.hayFiltros) {
                                {
                                    TextButton(onClick = limpiarTodo) {
                                        Text("Limpiar filtros", color = senaColors().green)
                                    }
                                }
                            } else {
                                null
                            }
                            SenaEmptyState(
                                message = if (estado.filtros.hayFiltros) {
                                    "Ningún registro coincide con los filtros aplicados."
                                } else {
                                    "Aún no hay acciones registradas."
                                },
                                icon = Icons.Default.SearchOff,
                                action = accionLimpiar,
                            )
                        }
                    } else {
                        items(estado.visibles, key = { it.id }) { registro ->
                            SenaCard(elevation = 0.5.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            registro.accion,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = senaColors().text,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            formatearFechaHoraLocal(registro.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = senaColors().textLight,
                                        )
                                    }
                                    Text(
                                        "${registro.entidad ?: "—"} #${registro.entidadId ?: "—"} · ${registro.usuarioNombre ?: "Sistema"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = senaColors().textSecondary,
                                    )
                                    registro.detalle?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                                    }
                                }
                            }
                        }

                        item {
                            PaginacionBitacora(
                                currentPage = estado.pagina,
                                lastPage = estado.totalPaginas,
                                total = estado.registros.size,
                                onAnterior = { bitacoraViewModel.paginaAnterior() },
                                onSiguiente = { bitacoraViewModel.paginaSiguiente() },
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    hojaAbierta?.let { hoja ->
        ModalBottomSheet(onDismissRequest = { hojaAbierta = null }) {
            val titulo = when (hoja) {
                FiltroBitacora.USUARIO -> "Responsable"
                FiltroBitacora.ACCION -> "Acción"
                FiltroBitacora.ENTIDAD -> "Entidad"
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                when (hoja) {
                    FiltroBitacora.USUARIO -> {
                        OpcionHoja(texto = "Todos", seleccionada = usuarioId == null) {
                            usuarioId = null
                            hojaAbierta = null
                        }
                        usuarios.forEach { usuario ->
                            OpcionHoja(texto = usuario.name, seleccionada = usuarioId == usuario.id) {
                                usuarioId = usuario.id
                                hojaAbierta = null
                            }
                        }
                    }
                    FiltroBitacora.ACCION -> ACCIONES_BITACORA.forEach { (valor, etiqueta) ->
                        OpcionHoja(texto = etiqueta, seleccionada = accion == valor) {
                            accion = valor
                            hojaAbierta = null
                        }
                    }
                    FiltroBitacora.ENTIDAD -> ENTIDADES_BITACORA.forEach { (valor, etiqueta) ->
                        OpcionHoja(texto = etiqueta, seleccionada = entidad == valor) {
                            entidad = valor
                            hojaAbierta = null
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** Campo de solo lectura que abre la hoja de opciones del filtro. */
@Composable
private fun SelectorCampo(
    etiqueta: String,
    valor: String,
    onClick: () -> Unit,
) {
    Column {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = senaColors().textSecondary,
            modifier = Modifier.padding(bottom = 8.dp, start = 8.dp),
        )
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = senaColors().inputBackground,
            border = BorderStroke(1.dp, senaColors().border),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    valor,
                    style = MaterialTheme.typography.bodyMedium,
                    color = senaColors().text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = senaColors().green)
            }
        }
    }
}

/** Fila seleccionable de la hoja de filtros. */
@Composable
private fun OpcionHoja(
    texto: String,
    seleccionada: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = senaColors().backgroundElevated,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                texto,
                fontWeight = if (seleccionada) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (seleccionada) {
                Icon(Icons.Default.Check, contentDescription = null, tint = senaColors().green)
            }
        }
    }
}

/** Controles de paginación en cliente (anterior / página actual / siguiente). */
@Composable
private fun PaginacionBitacora(
    currentPage: Int,
    lastPage: Int,
    total: Int,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onAnterior, enabled = currentPage > 1) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = "Página anterior",
                tint = if (currentPage > 1) senaColors().green else senaColors().border,
            )
        }
        Text(
            "Página $currentPage de $lastPage · $total registro${if (total != 1) "s" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = senaColors().textSecondary,
        )
        IconButton(onClick = onSiguiente, enabled = currentPage < lastPage) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Página siguiente",
                tint = if (currentPage < lastPage) senaColors().green else senaColors().border,
            )
        }
    }
}

/**
 * Entrada de fecha solo con dígitos: el usuario escribe `06102026` y el campo
 * agrega las barras (`06/10/2026`). Sin letras ni separadores manuales.
 *
 * Usa `TextFieldValue` con la selección forzada al final: con `String` a secas,
 * un tecleo rápido podía insertar dígitos en medio de la máscara (cursor
 * "saltarín") y producir fechas corruptas.
 */
@Composable
private fun CampoFechaNumerico(
    valor: String,
    onValorChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var campo by remember {
        mutableStateOf(TextFieldValue(valor, TextRange(valor.length)))
    }
    LaunchedEffect(valor) {
        if (valor != campo.text) campo = TextFieldValue(valor, TextRange(valor.length))
    }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = senaColors().textSecondary,
            modifier = Modifier.padding(bottom = 8.dp, start = 8.dp),
        )
        OutlinedTextField(
            value = campo,
            onValueChange = { nuevo ->
                val digitos = nuevo.text.filter { it.isDigit() }.take(8)
                val formateado = mascaraFecha(digitos)
                campo = TextFieldValue(formateado, TextRange(formateado.length))
                onValorChange(formateado)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("DD/MM/AAAA", color = senaColors().textMuted) },
            leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = senaColors().green) },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            supportingText = if (valor.filter { it.isDigit() }.length in 1..7) {
                { Text("Escribe 8 dígitos: DDMMAAAA", color = senaColors().textMuted) }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = senaColors().green,
                unfocusedBorderColor = senaColors().border,
                focusedContainerColor = senaColors().inputBackground,
                unfocusedContainerColor = senaColors().inputBackground,
                cursorColor = senaColors().green,
            ),
        )
    }
}

private fun mascaraFecha(digitos: String): String {
    val salida = StringBuilder()
    digitos.forEachIndexed { indice, caracter ->
        if (indice == 2 || indice == 4) salida.append('/')
        salida.append(caracter)
    }
    return salida.toString()
}

/** "06/10/2026" → "2026-10-06" (formato que filtra el backend); null si es inválida. */
private fun fechaApiDeMascara(mascara: String): String? {
    val digitos = mascara.filter { it.isDigit() }
    if (digitos.length != 8) return null
    return runCatching {
        val fecha = LocalDate.of(
            digitos.substring(4, 8).toInt(),
            digitos.substring(2, 4).toInt(),
            digitos.substring(0, 2).toInt(),
        )
        fecha.toString()
    }.getOrNull()
}

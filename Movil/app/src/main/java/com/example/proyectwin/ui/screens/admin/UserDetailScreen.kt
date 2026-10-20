package com.example.proyectwin.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.model.CredencialesUsuario
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AdminUserAction
import com.example.proyectwin.ui.viewmodel.AdminUserUiState
import com.example.proyectwin.ui.viewmodel.UserDetailViewModel

/**
 * Detalle real de una cuenta (admin): edición, estado, rol, ficha del aprendiz
 * y credenciales (restablecer/reenviar). Las reglas finas las valida backend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    userId: String = "",
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    userDetailViewModel: UserDetailViewModel = hiltViewModel(),
) {
    val uiState by userDetailViewModel.uiState.collectAsState()
    val accion by userDetailViewModel.accion.collectAsState()
    val fichas by userDetailViewModel.fichasDisponibles.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    var dialogoEditar by remember { mutableStateOf(false) }
    var dialogoEliminar by remember { mutableStateOf(false) }
    var dialogoFicha by remember { mutableStateOf(false) }
    var credenciales by remember { mutableStateOf<CredencialesUsuario?>(null) }

    LaunchedEffect(userId) {
        userId.toIntOrNull()?.let { userDetailViewModel.load(it) }
    }

    LaunchedEffect(accion) {
        when (val estado = accion) {
            is AdminUserAction.Saved -> userDetailViewModel.resetAccion()
            is AdminUserAction.Message -> {
                snackbarHostState.showSnackbar(estado.text)
                userDetailViewModel.resetAccion()
            }
            is AdminUserAction.Error -> {
                snackbarHostState.showSnackbar(estado.message)
                userDetailViewModel.resetAccion()
            }
            is AdminUserAction.Credenciales -> {
                credenciales = estado.credenciales
                userDetailViewModel.resetAccion()
            }
            is AdminUserAction.Deleted -> {
                userDetailViewModel.resetAccion()
                onBack()
            }
            else -> Unit
        }
    }

    if (credenciales != null) {
        val datos = credenciales!!
        AlertDialog(
            onDismissRequest = { credenciales = null },
            icon = { Icon(Icons.Default.Key, contentDescription = null, tint = senaColors().green) },
            title = { Text("Credenciales temporales", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Se muestra una sola vez. El correo no se envía automáticamente.", style = MaterialTheme.typography.bodySmall, color = senaColors().textSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Usuario: ${datos.username}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { clipboard.setText(AnnotatedString(datos.username)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = senaColors().green)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Contraseña: ${datos.passwordTemporal}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { clipboard.setText(AnnotatedString(datos.passwordTemporal)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = senaColors().green)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { credenciales = null }) { Text("Entendido", color = senaColors().green) }
            },
        )
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "Detalle de Usuario",
                onBack = onBack,
                showProfile = true,
                showNotifications = true,
            )
        },
        containerColor = senaColors().background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when (val estado = uiState) {
            is AdminUserUiState.Loading ->
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is AdminUserUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estado.message, onRetry = { userDetailViewModel.recargar() })
            }
            is AdminUserUiState.Success -> {
                val data = estado.data
                val usuario = data.usuario
                val enProceso = accion is AdminUserAction.Loading

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SenaPageHeader(
                        title = usuario.name,
                        subtitle = usuario.roleDisplayName,
                        icon = Icons.Default.Person,
                    )

                    SenaCard(elevation = 1.dp) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SenaAvatar(fotoBase64 = usuario.fotoPerfil, nombre = usuario.name, modifier = Modifier.size(96.dp))
                            Text(usuario.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(usuario.email, style = MaterialTheme.typography.bodySmall, color = senaColors().textSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SenaStatusBadge(status = if (usuario.estado) "Activo" else "Suspendido")
                                Surface(shape = CircleShape, color = senaColors().green.copy(alpha = 0.1f)) {
                                    Text(
                                        usuario.roleDisplayName,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = senaColors().green,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    SenaSectionHeader(title = "Datos de la cuenta")
                    SenaCard(elevation = 1.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DatoCuenta("Usuario", usuario.username.ifBlank { "—" })
                            DatoCuenta("Documento", "${usuario.tipoDocumento ?: ""} ${usuario.documentoIdentidad ?: "—"}".trim())
                            DatoCuenta(
                                "Credenciales",
                                when {
                                    usuario.role == "admin" -> "No aplica para administradores"
                                    !usuario.mustChangePassword -> "Contraseña ya establecida"
                                    usuario.credencialesError != null -> "Falló el envío: ${usuario.credencialesError}"
                                    usuario.credencialesEnviadasEn != null -> "Enviadas (${usuario.credencialesEnviadasEn.take(10)})"
                                    else -> "Pendientes de envío"
                                },
                            )
                            if (data.aprendiz != null) {
                                val ficha = data.fichaActual
                                DatoCuenta(
                                    "Ficha",
                                    ficha?.let { "${it.nombre ?: it.programa} (${it.codigo})" } ?: "Sin ficha",
                                )
                            }
                        }
                    }

                    SenaSectionHeader(title = "Acciones")
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SenaButton(
                            text = "EDITAR DATOS",
                            onClick = { dialogoEditar = true },
                            isPrimary = false,
                            enabled = !enProceso,
                            icon = Icons.Default.Edit,
                        )
                        SenaButton(
                            text = if (usuario.estado) "SUSPENDER CUENTA" else "ACTIVAR CUENTA",
                            onClick = { userDetailViewModel.cambiarEstado(!usuario.estado) },
                            isPrimary = false,
                            enabled = !enProceso,
                            icon = if (usuario.estado) Icons.Default.Block else Icons.Default.CheckCircle,
                            containerColor = if (usuario.estado) senaColors().warning10 else senaColors().success10,
                        )
                        if (usuario.role != "admin") {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SenaButton(
                                    text = "Restablecer",
                                    onClick = { userDetailViewModel.restablecerCredenciales() },
                                    isPrimary = false,
                                    enabled = !enProceso,
                                    icon = Icons.Default.LockReset,
                                    modifier = Modifier.weight(1f),
                                )
                                SenaButton(
                                    text = "Reenviar",
                                    onClick = { userDetailViewModel.reenviarCredenciales() },
                                    isPrimary = false,
                                    enabled = !enProceso && usuario.mustChangePassword,
                                    icon = Icons.Default.Send,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (data.aprendiz != null) {
                            SenaButton(
                                text = "CAMBIAR DE FICHA",
                                onClick = { dialogoFicha = true },
                                isPrimary = false,
                                enabled = !enProceso,
                                icon = Icons.Default.SwapHoriz,
                            )
                        }
                        SenaButton(
                            text = "ELIMINAR USUARIO",
                            onClick = { dialogoEliminar = true },
                            isPrimary = false,
                            enabled = !enProceso,
                            icon = Icons.Default.Delete,
                            containerColor = senaColors().danger10,
                        )
                    }

                    Spacer(Modifier.height(40.dp))
                }

                if (dialogoEditar) {
                    EditarUsuarioDialog(
                        usuario = usuario,
                        onDismiss = { dialogoEditar = false },
                        onGuardar = { editado ->
                            dialogoEditar = false
                            userDetailViewModel.guardar(editado)
                        },
                    )
                }

                if (dialogoFicha) {
                    ModalBottomSheet(onDismissRequest = { dialogoFicha = false }) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Mover a otra ficha", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Actual: ${data.fichaActual?.codigo ?: "sin ficha"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = senaColors().textSecondary,
                            )
                            fichas.filter { it.id != data.fichaActual?.id }.forEach { ficha ->
                                Surface(
                                    onClick = {
                                        dialogoFicha = false
                                        userDetailViewModel.moverAprendiz(ficha.id)
                                    },
                                    color = senaColors().backgroundElevated,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(ficha.nombre ?: ficha.programa, fontWeight = FontWeight.Bold)
                                        Text("${ficha.codigo} · ${ficha.statusDisplay}", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                                    }
                                }
                            }
                            TextButton(onClick = {
                                dialogoFicha = false
                                userDetailViewModel.moverAprendiz(null)
                            }) { Text("Quitar de la ficha actual", color = senaColors().danger) }
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }

                if (dialogoEliminar) {
                    AlertDialog(
                        onDismissRequest = { dialogoEliminar = false },
                        title = { Text("Eliminar usuario") },
                        text = { Text("Se eliminarán sus datos asociados. Esta acción no se puede deshacer.") },
                        confirmButton = {
                            TextButton(onClick = {
                                dialogoEliminar = false
                                userDetailViewModel.eliminar()
                            }) { Text("Eliminar", color = senaColors().danger) }
                        },
                        dismissButton = {
                            TextButton(onClick = { dialogoEliminar = false }) { Text("Cancelar") }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DatoCuenta(etiqueta: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(etiqueta, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
        Text(valor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = senaColors().text)
    }
}

@Composable
private fun EditarUsuarioDialog(
    usuario: GeneralUser,
    onDismiss: () -> Unit,
    onGuardar: (GeneralUser) -> Unit,
) {
    var nombre by remember { mutableStateOf(usuario.nombre ?: usuario.name.substringBefore(' ')) }
    var apellido by remember { mutableStateOf(usuario.apellido ?: usuario.name.substringAfter(' ', "")) }
    var correo by remember { mutableStateOf(usuario.email) }
    var rol by remember { mutableStateOf(usuario.role) }
    var estado by remember { mutableStateOf(usuario.estado) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar usuario", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SenaTextField(value = nombre, onValueChange = { nombre = it }, label = "Nombre")
                SenaTextField(value = apellido, onValueChange = { apellido = it }, label = "Apellido")
                SenaTextField(value = correo, onValueChange = { correo = it }, label = "Correo")
                Text("Rol", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("aprendiz", "instructor", "admin").forEach { valor ->
                        SenaChip(
                            text = valor.replaceFirstChar { it.uppercase() },
                            color = senaColors().green,
                            isSelected = rol == valor,
                            onClick = { rol = valor },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cuenta activa", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(checked = estado, onCheckedChange = { estado = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onGuardar(
                    usuario.copy(
                        nombre = nombre.trim(),
                        apellido = apellido.trim(),
                        name = "$nombre $apellido".trim(),
                        email = correo.trim().lowercase(),
                        role = rol,
                        estado = estado,
                    ),
                )
            }) { Text("Guardar", color = senaColors().green, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

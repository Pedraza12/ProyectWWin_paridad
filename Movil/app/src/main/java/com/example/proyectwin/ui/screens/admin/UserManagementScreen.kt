package com.example.proyectwin.ui.screens.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.UserManagementUiState
import com.example.proyectwin.ui.viewmodel.UserManagementViewModel

data class UserItem(
    val id: Int,
    val name: String,
    val document: String,
    val email: String,
    val role: String,
    val status: String,
    val photo: String? = null,
)

/**
 * Gestión de usuarios (admin): listado con paginación del servidor y filtros
 * reales del contrato (`search`, `role`, `estado`, `ficha_id`, `programa`),
 * como la página web de Usuarios.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    userManagementViewModel: UserManagementViewModel = hiltViewModel()
) {
    val uiState by userManagementViewModel.uiState.collectAsState()
    val filtros by userManagementViewModel.filtros.collectAsState()
    val fichas by userManagementViewModel.fichas.collectAsState()
    val programas by userManagementViewModel.programas.collectAsState()
    val errorAccion by userManagementViewModel.errorAccion.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<UserItem?>(null) }
    val isLoading = uiState is UserManagementUiState.Loading

    LaunchedEffect(errorAccion) {
        errorAccion?.let { mensaje ->
            snackbarHostState.showSnackbar(mensaje)
            userManagementViewModel.limpiarErrorAccion()
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SenaSkeletonLine(width = 180.dp, height = 24.dp)
                SenaSkeletonLine(width = 260.dp, height = 14.dp)
                Spacer(Modifier.height(12.dp))
                repeat(4) { SenaSkeletonCard(lines = 3, hasAvatar = true) }
            }
        }
        return
    }

    if (uiState is UserManagementUiState.Error) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            SenaErrorState(
                message = (uiState as UserManagementUiState.Error).message,
                onRetry = { userManagementViewModel.recargar() },
            )
        }
        return
    }

    val pagina = (uiState as UserManagementUiState.Success).pagina

    val usersList = remember(pagina.items) {
        pagina.items.map { user ->
            UserItem(
                id = user.id,
                name = user.name,
                document = user.documentoIdentidad ?: "",
                email = user.email,
                role = user.roleDisplayName,
                status = if (user.estado) "Activo" else "Inactivo",
                photo = user.fotoPerfil,
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SenaTopBar(
                title = "ProyecTwin",
                onBack = onBack,
                showProfile = true,
                showNotifications = true
            )
        },
        containerColor = senaColors().background,
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(AppNavigation.ADMIN_NEW_USER) },
                containerColor = senaColors().green,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Nuevo Usuario")
            }
        }
    ) { paddingValues ->
        SenaPullRefresh(
            isRefreshing = isLoading,
            onRefresh = { userManagementViewModel.recargar() },
            modifier = Modifier.padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    SenaPageHeader(
                        title = "Gestión de Usuarios",
                        subtitle = "Administra las cuentas, roles y permisos de acceso al sistema.",
                        icon = Icons.Default.ManageAccounts
                    )
                }

                // Filter Section
                item {
                    SenaFilterBar(title = "Buscar y filtrar") {
                        SenaTextField(
                            value = filtros.search,
                            onValueChange = { userManagementViewModel.buscar(it) },
                            label = "",
                            placeholder = "Buscar por nombre, correo o documento...",
                            leadingIcon = Icons.Default.Search
                        )

                        if (filtros.hayFiltros) {
                            TextButton(onClick = { userManagementViewModel.limpiarFiltros() }) {
                                Text(
                                    "Limpiar filtros",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = senaColors().green,
                                )
                            }
                        }

                        ChipsFiltro(
                            etiqueta = "Rol",
                            opciones = listOf(
                                Triple(null, "Todos", senaColors().green),
                                Triple("aprendiz", "Aprendiz", senaColors().success),
                                Triple("instructor", "Instructor", senaColors().warning),
                                Triple("admin", "Administrador", senaColors().danger),
                            ),
                            seleccion = filtros.role,
                            onSelect = { userManagementViewModel.filtrarRol(it) },
                        )

                        ChipsFiltro(
                            etiqueta = "Estado",
                            opciones = listOf(
                                Triple(null, "Todos", senaColors().green),
                                Triple("activo", "Activo", senaColors().success),
                                Triple("suspendido", "Suspendido", senaColors().danger),
                            ),
                            seleccion = filtros.estado,
                            onSelect = { userManagementViewModel.filtrarEstado(it) },
                        )

                        ChipsFiltro(
                            etiqueta = "Ficha",
                            opciones = buildList {
                                add(Triple(null, "Todas", senaColors().green))
                                add(Triple("sin", "Sin ficha", senaColors().warning))
                                fichas.forEach { ficha ->
                                    add(Triple(ficha.id.toString(), ficha.codigo, senaColors().info))
                                }
                            },
                            seleccion = filtros.fichaId,
                            onSelect = { userManagementViewModel.filtrarFicha(it) },
                        )

                        ChipsFiltro(
                            etiqueta = "Programa",
                            opciones = buildList {
                                add(Triple(null, "Todos", senaColors().green))
                                programas.forEach { programa ->
                                    add(Triple(programa.nombre, programa.nombre, senaColors().info))
                                }
                            },
                            seleccion = filtros.programa,
                            onSelect = { userManagementViewModel.filtrarPrograma(it) },
                        )

                        Text(
                            "${pagina.total} usuario${if (pagina.total != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = senaColors().textLight,
                        )
                    }
                }

                if (usersList.isEmpty()) {
                    item {
                        val accionLimpiar: (@Composable () -> Unit)? = if (filtros.hayFiltros) {
                            {
                                TextButton(onClick = { userManagementViewModel.limpiarFiltros() }) {
                                    Text("Limpiar filtros", color = senaColors().green)
                                }
                            }
                        } else {
                            null
                        }
                        SenaEmptyState(
                            message = if (filtros.hayFiltros) {
                                "Ningún usuario coincide con los filtros aplicados."
                            } else {
                                "Aún no hay usuarios registrados. Crea el primero con el botón +."
                            },
                            icon = Icons.Default.GroupOff,
                            action = accionLimpiar,
                        )
                    }
                } else {
                    items(usersList) { user ->
                        UserCard(
                            user = user,
                            onEdit = { onNavigate(AppNavigation.ADMIN_USER_DETAIL.replace("{userId}", user.id.toString())) },
                            onDelete = { userToDelete = user; showDeleteDialog = true }
                        )
                    }

                    item {
                        PaginacionUsuarios(
                            currentPage = pagina.currentPage,
                            lastPage = pagina.lastPage,
                            total = pagina.total,
                            onAnterior = { userManagementViewModel.paginaAnterior() },
                            onSiguiente = { userManagementViewModel.paginaSiguiente() },
                        )
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showDeleteDialog) {
        // Confirmación tipada: el estado se reinicia al abrir/cerrar el diálogo
        // (remember(userToDelete?.id) + salida de composición).
        var confirmacion by remember(userToDelete?.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar Usuario") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("¿Estás seguro de que deseas eliminar a ${userToDelete?.name}? Esta acción no se puede deshacer.")
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
                        showDeleteDialog = false
                        val targetId = userToDelete?.id
                        if (targetId != null) {
                            userManagementViewModel.eliminarUsuario(targetId)
                        }
                    },
                    enabled = confirmacion.trim() == "ELIMINAR",
                ) {
                    Text("Eliminar", color = senaColors().danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

/** Fila de chips con etiqueta para un filtro (valor null = "todos"). */
@Composable
private fun ChipsFiltro(
    etiqueta: String,
    opciones: List<Triple<String?, String, Color>>,
    seleccion: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = senaColors().textLight,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            opciones.forEach { (valor, texto, color) ->
                SenaChip(
                    text = texto,
                    color = color,
                    isSelected = seleccion == valor,
                    onClick = { onSelect(valor) }
                )
            }
        }
    }
}

/** Controles de paginación del servidor (anterior / página actual / siguiente). */
@Composable
private fun PaginacionUsuarios(
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
            "Página $currentPage de $lastPage · $total usuario${if (total != 1) "s" else ""}",
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

@Composable
fun UserCard(user: UserItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    SenaCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    SenaAvatar(
                        fotoBase64 = user.photo,
                        nombre = user.name,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            user.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = senaColors().text
                        )
                        Text(user.email, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = senaColors().green)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = senaColors().danger)
                    }
                }
            }
            SenaStatusBadge(status = user.status)
        }
    }
}

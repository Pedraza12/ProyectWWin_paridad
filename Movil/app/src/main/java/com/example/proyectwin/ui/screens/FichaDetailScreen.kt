package com.example.proyectwin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AuthUiState
import com.example.proyectwin.ui.viewmodel.AuthViewModel
import com.example.proyectwin.ui.viewmodel.FichasUiState
import com.example.proyectwin.ui.viewmodel.FichasViewModel

/**
 * Mi ficha real: datos del `class-groups/{id}` del aprendiz, roster de
 * compañeros y salida de la ficha. Sin ficha muestra el acceso a unirse.
 */
@Composable
fun FichaDetailScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    fichasViewModel: FichasViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val user = (authState as? AuthUiState.LoggedIn)?.user
    val fichasState by fichasViewModel.uiState.collectAsState()
    val actionState by fichasViewModel.actionState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmarSalida by remember { mutableStateOf(false) }

    LaunchedEffect(user?.fichaId) {
        user?.fichaId?.let { fichasViewModel.loadFichaById(it) }
    }

    LaunchedEffect(actionState.message, actionState.leftSuccess) {
        actionState.message?.let {
            snackbarHostState.showSnackbar(it)
            fichasViewModel.clearError()
        }
        if (actionState.leftSuccess) {
            fichasViewModel.clearLeftSuccess()
            snackbarHostState.showSnackbar("Saliste de la ficha.")
        }
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "ProyecTwin",
                onBack = onBack,
                showProfile = true,
                showNotifications = true,
            )
        },
        containerColor = senaColors().background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (user?.fichaId == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp),
            ) {
                SenaPageHeader(
                    title = "Mi Ficha",
                    subtitle = "Aún no perteneces a una ficha de formación.",
                    icon = Icons.Default.Groups,
                )
                SenaEmptyState(
                    message = "Únete con el código que te compartió tu instructor para ver el roster y registrar propuestas.",
                    icon = Icons.Default.GroupAdd,
                )
                SenaButton(
                    text = "UNIRME A UNA FICHA",
                    onClick = { onNavigate(AppNavigation.APRENDIZ_JOIN_FICHA) },
                    icon = Icons.Default.GroupAdd,
                )
            }
            return@Scaffold
        }

        when (val estado = fichasState) {
            is FichasUiState.Loading -> SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is FichasUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estado.message, onRetry = { user?.fichaId?.let { fichasViewModel.loadFichaById(it) } })
            }
            is FichasUiState.Success -> {
                val ficha = estado.fichas.firstOrNull()
                if (ficha == null) {
                    SenaEmptyState(message = "No se encontró la ficha.", icon = Icons.Default.Groups)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item {
                            SenaPageHeader(
                                title = "Mi Ficha",
                                subtitle = "Detalles del programa y compañeros de equipo.",
                                icon = Icons.Default.Groups,
                            )
                        }

                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Brush.linearGradient(colors = listOf(senaColors().header, senaColors().green)))
                                    .padding(24.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                ficha.nombre ?: ficha.programa.ifBlank { "Ficha de formación" },
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                            Text(
                                                ficha.programa.ifBlank { "Programa sin asignar" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.85f),
                                            )
                                        }
                                        Surface(color = Color.White.copy(alpha = 0.2f), shape = CircleShape) {
                                            Text(
                                                ficha.statusDisplay.uppercase(),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.QrCode, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Código:", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
                                        Spacer(Modifier.width(6.dp))
                                        SenaCopyButton(textToCopy = ficha.codigo, label = ficha.codigo, onDark = true)
                                    }
                                    if (!ficha.numero.isNullOrBlank()) {
                                        Text(
                                            "Número: ${ficha.numero}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.8f),
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            SenaSectionHeader(title = "Instructor Encargado")
                            SenaCard(elevation = 1.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(48.dp),
                                        shape = CircleShape,
                                        color = senaColors().green.copy(alpha = 0.1f),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.School, contentDescription = null, tint = senaColors().green, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            ficha.instructorName ?: "No asignado",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = senaColors().text,
                                        )
                                        Text("Líder de ficha", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                                    }
                                }
                            }
                        }

                        item {
                            SenaSectionHeader(title = "Compañeros (${ficha.estudiantes.size})")
                        }

                        if (ficha.estudiantes.isEmpty()) {
                            item {
                                SenaEmptyState(message = "No hay aprendices en esta ficha.", icon = Icons.Default.Groups)
                            }
                        } else {
                            items(ficha.estudiantes, key = { it.id }) { estudiante ->
                                SenaCard(
                                    elevation = 0.5.dp,
                                    onClick = {
                                        onNavigate(
                                            AppNavigation.APRENDIZ_COMPANERO_DETAIL.replace("{userId}", estudiante.id.toString()),
                                        )
                                    },
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SenaAvatar(
                                            fotoBase64 = estudiante.fotoPerfil,
                                            nombre = estudiante.name,
                                            modifier = Modifier.size(44.dp),
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                estudiante.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = senaColors().text,
                                            )
                                            Text(
                                                if (estudiante.id == user?.id) "Tú" else "Aprendiz",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = senaColors().textLight,
                                            )
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = senaColors().textMuted)
                                    }
                                }
                            }
                        }

                        item {
                            if (ficha.estado == "activo") {
                                SenaButton(
                                    text = "SALIR DE LA FICHA",
                                    onClick = { confirmarSalida = true },
                                    isPrimary = false,
                                    icon = Icons.Default.Logout,
                                )
                            }
                        }

                        item { Spacer(Modifier.height(40.dp)) }
                    }
                }
            }
        }

        if (confirmarSalida) {
            AlertDialog(
                onDismissRequest = { confirmarSalida = false },
                title = { Text("Salir de la ficha") },
                text = { Text("Dejarás de ver esta ficha y sus propuestas. ¿Continuar?") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmarSalida = false
                        fichasViewModel.salirDeFicha()
                    }) { Text("Salir", color = senaColors().danger) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmarSalida = false }) { Text("Cancelar") }
                },
            )
        }
    }
}

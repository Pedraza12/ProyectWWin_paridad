package com.example.proyectwin.ui.screens.instructor

import androidx.compose.foundation.background
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.ClassGroupStatus
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.FichasUiState
import com.example.proyectwin.ui.viewmodel.FichasViewModel

/**
 * Detalle de la ficha del instructor: datos reales, roster y edición.
 * Sin botón de exportar: las credenciales PDF por ficha son solo del admin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleFichaInstructorScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    fichaId: String = "",
    fichasViewModel: FichasViewModel = hiltViewModel(),
) {
    val uiState by fichasViewModel.uiState.collectAsState()

    LaunchedEffect(fichaId) {
        val id = fichaId.toIntOrNull()
        if (id != null) fichasViewModel.loadFichaById(id) else fichasViewModel.loadAllFichas()
    }

    val ficha = when (val estado = uiState) {
        is FichasUiState.Success -> estado.fichas.firstOrNull {
            it.id.toString() == fichaId || it.codigo == fichaId
        }
        else -> null
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
        bottomBar = {
            // Una ficha anulada solo la modifica un administrador: el instructor
            // conserva el detalle (histórico) pero no la acción de edición.
            if (ficha != null && ficha.classGroupStatus != ClassGroupStatus.ANULADA) {
                SenaBottomBar {
                    SenaButton(
                        text = "Editar Ficha",
                        onClick = {
                            onNavigate(
                                AppNavigation.INSTRUCTOR_CREAR_FICHA.replace("{fichaId}", ficha.id.toString()),
                            )
                        },
                        icon = Icons.Default.Edit,
                    )
                }
            }
        },
    ) { paddingValues ->
        when (val estado = uiState) {
            is FichasUiState.Loading ->
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is FichasUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(
                    message = estado.message,
                    onRetry = { fichaId.toIntOrNull()?.let { fichasViewModel.loadFichaById(it) } },
                )
            }
            is FichasUiState.Success -> {
                if (ficha == null) {
                    Box(
                        Modifier.fillMaxSize().padding(paddingValues),
                        contentAlignment = Alignment.Center,
                    ) {
                        SenaEmptyState(message = "Ficha no encontrada.", icon = Icons.Default.SearchOff)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item {
                            SenaPageHeader(
                                title = "Gestión de Ficha",
                                subtitle = "Información y roster de aprendices del grupo.",
                                icon = Icons.Default.Groups,
                            )
                        }

                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Brush.linearGradient(colors = listOf(senaColors().header, senaColors().green)))
                                    .padding(24.dp),
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
                                                style = MaterialTheme.typography.labelSmall,
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
                                        SenaCopyButton(textToCopy = ficha.codigo, label = ficha.codigo)
                                    }
                                    if (!ficha.numero.isNullOrBlank()) {
                                        Text(
                                            "Número: ${ficha.numero}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.85f),
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            SenaSectionHeader(title = "Aprendices (${ficha.estudiantes.size})")
                        }

                        if (ficha.estudiantes.isEmpty()) {
                            item {
                                SenaEmptyState(message = "No hay aprendices en esta ficha.", icon = Icons.Default.Groups)
                            }
                        } else {
                            items(ficha.estudiantes, key = { it.id }) { estudiante ->
                                SenaCard(elevation = 0.5.dp) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SenaAvatar(
                                            fotoBase64 = estudiante.fotoPerfil,
                                            nombre = estudiante.name,
                                            modifier = Modifier.size(40.dp),
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
                                                estudiante.email,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = senaColors().textLight,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(60.dp)) }
                    }
                }
            }
        }
    }
}

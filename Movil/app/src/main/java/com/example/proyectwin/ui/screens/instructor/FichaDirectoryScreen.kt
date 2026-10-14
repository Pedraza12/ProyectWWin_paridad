package com.example.proyectwin.ui.screens.instructor

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.navigation.AppNavigation
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.FichasUiState
import com.example.proyectwin.ui.viewmodel.FichasViewModel

/** Directorio: todas las fichas del instructor con su roster de aprendices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FichaDirectoryScreen(
    onBack: () -> Unit,
    onCreateFicha: () -> Unit,
    onNavigate: (String) -> Unit,
    fichasViewModel: FichasViewModel = hiltViewModel(),
) {
    val uiState by fichasViewModel.uiState.collectAsState()
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) {
        fichasViewModel.loadAllFichas()
    }

    val isLoading = uiState is FichasUiState.Loading
    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateFicha,
                containerColor = senaColors().green,
                contentColor = senaColors().onPrimary,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nueva Ficha")
            }
        },
    ) { paddingValues ->
        SenaPullRefresh(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; refreshTrigger++ },
            modifier = Modifier.padding(paddingValues),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    SenaPageHeader(
                        title = "Directorio",
                        subtitle = "Aprendices por ficha a tu cargo.",
                        icon = Icons.Default.ContactPage,
                    )
                }

                when (val state = uiState) {
                    is FichasUiState.Loading -> item { SenaLoadingState() }
                    is FichasUiState.Error -> item {
                        SenaErrorState(message = state.message, onRetry = { fichasViewModel.loadAllFichas() })
                    }
                    is FichasUiState.Success -> {
                        if (state.fichas.isEmpty()) {
                            item {
                                SenaEmptyState(
                                    title = "Sin fichas",
                                    message = "Aún no tienes fichas a cargo. Crea la primera con el botón +.",
                                    icon = Icons.Default.Groups,
                                )
                            }
                        } else {
                            items(state.fichas, key = { it.id }) { ficha ->
                                FichaDirectorySection(
                                    ficha = ficha,
                                    onViewDetail = {
                                        onNavigate(
                                            AppNavigation.INSTRUCTOR_FICHA_DETAIL
                                                .replace("{fichaId}", ficha.id.toString()),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun FichaDirectorySection(ficha: Ficha, onViewDetail: () -> Unit) {
    SenaCard(elevation = 1.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        ficha.nombre ?: ficha.programa.ifBlank { "Ficha sin nombre" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = senaColors().text,
                    )
                    Text(
                        "${ficha.codigo} · ${ficha.programa.ifBlank { "Sin programa" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = senaColors().textLight,
                    )
                }
                SenaStatusBadge(status = ficha.statusDisplay)
            }

            HorizontalDivider(color = senaColors().borderSoft)

            if (ficha.estudiantes.isEmpty()) {
                Text("Sin aprendices en esta ficha.", style = MaterialTheme.typography.bodySmall, color = senaColors().textSecondary)
            } else {
                ficha.estudiantes.forEach { estudiante ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SenaAvatar(
                            fotoBase64 = estudiante.fotoPerfil,
                            nombre = estudiante.name,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(Modifier.width(12.dp))
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

            TextButton(onClick = onViewDetail) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ver ficha")
            }
        }
    }
}

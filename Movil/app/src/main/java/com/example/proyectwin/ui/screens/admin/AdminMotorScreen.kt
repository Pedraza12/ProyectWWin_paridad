package com.example.proyectwin.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.MotorAdminAction
import com.example.proyectwin.ui.viewmodel.MotorAdminUiState
import com.example.proyectwin.ui.viewmodel.MotorAdminViewModel

/** Configuración del motor de similitud y recálculo global (admin). */
@Composable
fun AdminMotorScreen(
    onBack: () -> Unit,
    motorAdminViewModel: MotorAdminViewModel = hiltViewModel(),
) {
    val uiState by motorAdminViewModel.uiState.collectAsState()
    val accion by motorAdminViewModel.accion.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmarRecalculo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { motorAdminViewModel.load() }

    LaunchedEffect(accion) {
        when (val estado = accion) {
            is MotorAdminAction.Message -> {
                snackbarHostState.showSnackbar(estado.text)
                motorAdminViewModel.resetAccion()
            }
            is MotorAdminAction.Error -> {
                snackbarHostState.showSnackbar(estado.message)
                motorAdminViewModel.resetAccion()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "Motor de Similitud",
                onBack = onBack,
                showProfile = true,
                showNotifications = true,
            )
        },
        containerColor = senaColors().background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when (val estado = uiState) {
            is MotorAdminUiState.Loading ->
                SenaLoadingState(modifier = Modifier.padding(paddingValues))
            is MotorAdminUiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                SenaErrorState(message = estado.message, onRetry = { motorAdminViewModel.load() })
            }
            is MotorAdminUiState.Success -> {
                val data = estado.data
                var umbral by remember(data) { mutableFloatStateOf((data.config.umbral * 100).toFloat()) }
                var meses by remember(data) { mutableStateOf(data.config.meses.toString()) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SenaPageHeader(
                        title = "Motor de Similitud",
                        subtitle = "Umbral, ventana de corpus y recálculo global.",
                        icon = Icons.Default.Tune,
                    )

                    SenaCard(elevation = 1.dp) {
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            LecturaMotor("Umbral", "${umbral.toInt()}%")
                            LecturaMotor("Ventana", "${data.config.meses} meses")
                            LecturaMotor("Pares", "${data.totalPares}")
                        }
                    }

                    SenaCard {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Umbral de coincidencia: ${umbral.toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Slider(
                                value = umbral,
                                onValueChange = { umbral = it },
                                valueRange = 5f..95f,
                                steps = 17,
                            )
                            SenaTextField(
                                value = meses,
                                onValueChange = { meses = it.filter { c -> c.isDigit() } },
                                label = "Ventana del corpus (meses, 1-60)",
                                placeholder = "12",
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            )
                            SenaButton(
                                text = "GUARDAR CONFIGURACIÓN",
                                onClick = {
                                    val m = meses.toIntOrNull() ?: 12
                                    motorAdminViewModel.guardar((umbral / 100.0).coerceIn(0.05, 0.95), m.coerceIn(1, 60))
                                },
                                isLoading = accion is MotorAdminAction.Loading,
                                icon = Icons.Default.Save,
                            )
                        }
                    }

                    SenaCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Recálculo global", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Archiva los pares que ya no cumplen el umbral y crea los nuevos. Puede tardar según el volumen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = senaColors().textSecondary,
                            )
                            SenaButton(
                                text = "RECALCULAR AHORA",
                                onClick = { confirmarRecalculo = true },
                                isPrimary = false,
                                enabled = accion !is MotorAdminAction.Loading,
                                icon = Icons.Default.Refresh,
                            )
                        }
                    }

                    Spacer(Modifier.height(40.dp))
                }

                if (confirmarRecalculo) {
                    AlertDialog(
                        onDismissRequest = { confirmarRecalculo = false },
                        title = { Text("Recalcular motor") },
                        text = { Text("Se revisarán todos los pares vigentes e históricos según la configuración actual.") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmarRecalculo = false
                                motorAdminViewModel.recalcular()
                            }) { Text("Recalcular", color = senaColors().green, fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = { TextButton(onClick = { confirmarRecalculo = false }) { Text("Cancelar") } },
                    )
                }
            }
        }
    }
}

@Composable
private fun LecturaMotor(etiqueta: String, valor: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = senaColors().green)
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
    }
}

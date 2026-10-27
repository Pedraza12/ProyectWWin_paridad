package com.example.proyectwin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.ui.theme.senaColors
import com.example.proyectwin.ui.viewmodel.DashboardMetricsUiState
import com.example.proyectwin.ui.viewmodel.DashboardMetricsViewModel

/**
 * Bloque de métricas reales para los paneles de aprendiz e instructor.
 * Replica el `DashboardMetrics` de la web: KPIs del instructor, dona por
 * estado, evolución con selector de periodo y actividad reciente.
 *
 * El backend acota los datos al rol del token; no se envían ni se inventan
 * cifras desde el cliente.
 */
@Composable
fun DashboardMetricsSection(
    rol: String,
    modifier: Modifier = Modifier,
    onProyectoClick: ((Int) -> Unit)? = null,
    viewModel: DashboardMetricsViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()
    val periodo by viewModel.periodo.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                "Métricas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = senaColors().text,
            )
            Text(
                "Datos reales de la plataforma",
                style = MaterialTheme.typography.labelSmall,
                color = senaColors().textLight,
            )
        }

        when (val actual = estado) {
            is DashboardMetricsUiState.Loading -> {
                SenaSkeletonStats(count = 2)
                SenaSkeletonCard(lines = 3, hasImage = true)
            }

            is DashboardMetricsUiState.Error -> {
                SenaAlertBanner(
                    title = "No se pudieron cargar las métricas",
                    message = actual.message,
                    icon = Icons.Default.Error,
                    color = senaColors().danger,
                    actionText = "Reintentar",
                    onAction = { viewModel.reintentar() },
                )
            }

            is DashboardMetricsUiState.Success -> {
                val metrics = actual.metrics

                if (rol == "instructor") {
                    MetricasKpiInstructor(metrics)
                }

                DonaPropuestas(metrics.propuestas)

                EvolucionPropuestas(
                    puntos = metrics.evolucion,
                    periodo = periodo,
                    actualizando = actual.actualizando,
                    onPeriodo = { viewModel.cambiarPeriodo(it) },
                    titulo = if (rol == "instructor") "Revisiones por mes" else "Propuestas por mes",
                )

                ActividadRecienteCard(
                    actividad = metrics.actividad,
                    onProyectoClick = onProyectoClick,
                    titulo = if (rol == "aprendiz") "Observaciones recientes" else "Actividad reciente",
                )
            }
        }
    }
}

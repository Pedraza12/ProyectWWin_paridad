package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.proyectwin.data.model.DashboardMetrics
import com.example.proyectwin.data.model.PropuestasMetricas
import com.example.proyectwin.data.model.PuntoEvolucion
import com.example.proyectwin.data.model.UsuariosMetricas
import com.example.proyectwin.domain.repository.DashboardMetricsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardMetricsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun metricas(periodo: String, total: Int): DashboardMetrics = DashboardMetrics(
        rol = "admin",
        periodo = periodo,
        usuarios = UsuariosMetricas(total = 10, aprendices = 7, instructores = 2, admins = 1),
        fichasTotal = 3,
        aprendicesTotal = 7,
        propuestas = PropuestasMetricas(
            total = total,
            borrador = 1,
            pendiente = 1,
            aprobado = (total - 2).coerceAtLeast(0),
            rechazado = 0,
        ),
        evolucion = listOf(
            PuntoEvolucion(
                clave = "2026-10",
                label = "oct",
                fecha = "octubre de 2026",
                total = total,
                propuestas = listOf("Propuesta de prueba"),
                restantes = 0,
            ),
        ),
        actividad = emptyList(),
    )

    @Test
    fun `carga inicial y cambio de periodo consultan datos reales`() = runTest(dispatcher) {
        val repo = mockk<DashboardMetricsRepository>()
        coEvery { repo.obtener("3m") } returns Result.success(metricas("3m", 4))
        coEvery { repo.obtener("30d") } returns Result.success(metricas("30d", 9))

        val vm = DashboardMetricsViewModel(repo)
        advanceUntilIdle()

        assertEquals(PeriodoMetricas.TRES_MESES, vm.periodo.value)
        assertEquals(4, (vm.uiState.value as DashboardMetricsUiState.Success).metrics.propuestas.total)

        vm.cambiarPeriodo(PeriodoMetricas.TREINTA_DIAS)
        advanceUntilIdle()

        assertEquals(PeriodoMetricas.TREINTA_DIAS, vm.periodo.value)
        assertEquals(9, (vm.uiState.value as DashboardMetricsUiState.Success).metrics.propuestas.total)
    }

    @Test
    fun `un error inicial deja el estado de error sin romper la app`() = runTest(dispatcher) {
        val repo = mockk<DashboardMetricsRepository>()
        coEvery { repo.obtener(any()) } returns Result.failure(IllegalStateException("sin red"))

        val vm = DashboardMetricsViewModel(repo)
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertTrue(estado is DashboardMetricsUiState.Error)
        assertEquals("sin red", (estado as DashboardMetricsUiState.Error).message)
    }
}

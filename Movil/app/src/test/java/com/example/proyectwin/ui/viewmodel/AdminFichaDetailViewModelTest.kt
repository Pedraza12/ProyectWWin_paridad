package com.example.proyectwin.ui.viewmodel

import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.domain.repository.FichasRepository
import com.example.proyectwin.domain.repository.ProjectsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Detalle admin de ficha: información + propuestas por `ficha_id`, degradación
 * cuando las propuestas fallan, refresco silencioso (pull-to-refresh) que
 * conserva el contenido y descarte de respuestas tardías al navegar a otra ficha.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminFichaDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fichas = mockk<FichasRepository>(relaxed = true)
    private val projects = mockk<ProjectsRepository>(relaxed = true)

    private fun viewModel() = AdminFichaDetailViewModel(fichas, projects)

    private fun ficha(id: Int = 1) = Ficha(
        id = id,
        codigo = "ABC$id",
        programa = "ADSO",
        nombre = "Ficha $id",
    )

    private fun propuesta(id: Int, fichaId: Int) = Project(
        id = id,
        title = "Propuesta $id",
        fichaId = fichaId,
    )

    private fun datosDe(vm: AdminFichaDetailViewModel): AdminFichaDetailData =
        (vm.uiState.value as AdminFichaDetailUiState.Success).data

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load expone la ficha con las propuestas de su ficha`() = runTest(dispatcher) {
        coEvery { fichas.obtener(1) } returns Result.success(ficha())
        coEvery { projects.listar(fichaId = 1) } returns Result.success(
            listOf(propuesta(10, 1), propuesta(11, 2)),
        )

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()

        val datos = datosDe(vm)
        assertEquals(1, datos.ficha.id)
        // Una propuesta de otra ficha nunca se muestra en este detalle.
        assertEquals(listOf(10), datos.propuestas.map { it.id })
        assertFalse(datos.propuestasError)
    }

    @Test
    fun `load con 403 del backend muestra el error y no la ficha`() = runTest(dispatcher) {
        coEvery { fichas.obtener(1) } returns Result.failure(
            ApiException.Http(403, "No autorizado."),
        )

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertTrue(estado is AdminFichaDetailUiState.Error)
        assertEquals("No autorizado.", (estado as AdminFichaDetailUiState.Error).message)
    }

    @Test
    fun `propuestas con error no rompe el detalle de la ficha`() = runTest(dispatcher) {
        coEvery { fichas.obtener(1) } returns Result.success(ficha())
        coEvery { projects.listar(fichaId = 1) } returns Result.failure(
            ApiException.Http(500, "Fallo del servidor."),
        )

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()

        val datos = datosDe(vm)
        assertEquals(1, datos.ficha.id)
        assertTrue(datos.propuestas.isEmpty())
        assertTrue(datos.propuestasError)
    }

    @Test
    fun `refrescar conserva el contenido visible y luego lo actualiza`() = runTest(dispatcher) {
        coEvery { fichas.obtener(1) } returns Result.success(ficha())
        coEvery { projects.listar(fichaId = 1) } returns Result.success(listOf(propuesta(10, 1)))

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()
        assertEquals(listOf(10), datosDe(vm).propuestas.map { it.id })

        // La recarga tarda: el contenido previo debe seguir visible mientras tanto.
        coEvery { fichas.obtener(1) } coAnswers {
            delay(50)
            Result.success(ficha())
        }
        coEvery { projects.listar(fichaId = 1) } returns Result.success(
            listOf(propuesta(10, 1), propuesta(12, 1)),
        )

        vm.refrescar()
        runCurrent()

        assertTrue(vm.uiState.value is AdminFichaDetailUiState.Success)
        assertEquals(listOf(10), datosDe(vm).propuestas.map { it.id })
        assertTrue(vm.refrescando.value)

        advanceUntilIdle()

        assertEquals(listOf(10, 12), datosDe(vm).propuestas.map { it.id })
        assertFalse(vm.refrescando.value)
    }

    @Test
    fun `refrescar con error conserva el contenido anterior`() = runTest(dispatcher) {
        coEvery { fichas.obtener(1) } returns Result.success(ficha())
        coEvery { projects.listar(fichaId = 1) } returns Result.success(emptyList())

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()

        coEvery { fichas.obtener(1) } returns Result.failure(ApiException.Http(500, "Fallo temporal"))
        vm.refrescar()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is AdminFichaDetailUiState.Success)
        assertEquals(1, datosDe(vm).ficha.id)
        assertFalse(vm.refrescando.value)
    }

    @Test
    fun `una respuesta tardia no contamina al navegar a otra ficha`() = runTest(dispatcher) {
        coEvery { fichas.obtener(1) } returns Result.success(ficha(1))
        coEvery { fichas.obtener(2) } returns Result.success(ficha(2))
        coEvery { projects.listar(fichaId = 1) } returns Result.success(listOf(propuesta(10, 1)))
        coEvery { projects.listar(fichaId = 2) } returns Result.success(listOf(propuesta(20, 2)))

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()

        // El refresh de la ficha 1 queda pendiente cuando el usuario abre la ficha 2.
        coEvery { fichas.obtener(1) } coAnswers {
            delay(50)
            Result.success(ficha(1))
        }

        vm.refrescar()
        runCurrent()
        vm.load(2)
        advanceUntilIdle()

        val datos = datosDe(vm)
        assertEquals(2, datos.ficha.id)
        assertEquals(listOf(20), datos.propuestas.map { it.id })
        assertFalse(vm.refrescando.value)
    }
}

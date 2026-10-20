package com.example.proyectwin.ui.viewmodel

import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.model.Comment
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.Similarity
import com.example.proyectwin.domain.repository.CommentsRepository
import com.example.proyectwin.domain.repository.SimilaritiesRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Detalle admin del par: refresco silencioso (pull-to-refresh) que conserva el
 * contenido visible, no reemplaza con errores una carga fallida y descarta las
 * respuestas tardías si el usuario ya navegó a otro par.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminSimilarityDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val similarities = mockk<SimilaritiesRepository>(relaxed = true)
    private val comments = mockk<CommentsRepository>(relaxed = true)

    private fun viewModel() = AdminSimilarityDetailViewModel(similarities, comments)

    private fun proyecto(id: Int) = Project(id = id, title = "Propuesta $id")

    private fun par(id: Int = 1, p1: Project, p2: Project) = Similarity(
        id = id,
        projectId1 = p1.id,
        projectId2 = p2.id,
        project1 = p1,
        project2 = p2,
        vigente = true,
    )

    private fun comentario(id: Int, proyectoId: Int, texto: String = "c$id") = Comment(
        id = id,
        texto = texto,
        proyectoId = proyectoId,
        usuarioId = 99,
        autorNombre = "Autor $id",
        createdAt = "2026-10-01T00:00:00.000000Z",
    )

    private fun datosDe(vm: AdminSimilarityDetailViewModel): AdminSimilarityData =
        (vm.uiState.value as AdminSimilarityUiState.Success).data

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { similarities.listar(relatedTo = any()) } returns Result.success(emptyList())
        coEvery { comments.listar(any()) } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refrescar conserva el contenido visible y actualiza los comentarios`() = runTest(dispatcher) {
        coEvery { similarities.obtener(1) } returns Result.success(par(p1 = proyecto(10), p2 = proyecto(20)))
        coEvery { comments.listar(10) } returns Result.success(listOf(comentario(1, 10, "vieja")))

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()
        assertEquals(listOf("vieja"), datosDe(vm).comentarios1.map { it.texto })

        // La recarga tarda: el contenido previo debe seguir visible mientras tanto.
        coEvery { similarities.obtener(1) } coAnswers {
            delay(50)
            Result.success(par(p1 = proyecto(10), p2 = proyecto(20)))
        }
        coEvery { comments.listar(10) } returns Result.success(listOf(comentario(2, 10, "nueva")))
        coEvery { comments.listar(20) } returns Result.success(listOf(comentario(3, 20, "de la B")))

        vm.refrescar()
        runCurrent()

        assertTrue(vm.uiState.value is AdminSimilarityUiState.Success)
        assertEquals(listOf("vieja"), datosDe(vm).comentarios1.map { it.texto })
        assertTrue(vm.refrescando.value)

        advanceUntilIdle()

        val datos = datosDe(vm)
        assertEquals(listOf("nueva"), datos.comentarios1.map { it.texto })
        assertEquals(listOf("de la B"), datos.comentarios2.map { it.texto })
        assertEquals(false, vm.refrescando.value)
    }

    @Test
    fun `refrescar con error conserva el contenido anterior`() = runTest(dispatcher) {
        coEvery { similarities.obtener(1) } returns Result.success(par(p1 = proyecto(10), p2 = proyecto(20)))

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()

        coEvery { similarities.obtener(1) } returns Result.failure(ApiException.Http(500, "Fallo temporal"))
        vm.refrescar()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is AdminSimilarityUiState.Success)
        assertEquals(1, datosDe(vm).similarity.id)
        assertEquals(false, vm.refrescando.value)
    }

    @Test
    fun `una respuesta tardia de refrescar no contamina al navegar a otro par`() = runTest(dispatcher) {
        coEvery { similarities.obtener(1) } returns Result.success(par(1, proyecto(10), proyecto(20)))
        coEvery { similarities.obtener(2) } returns Result.success(par(2, proyecto(30), proyecto(40)))
        coEvery { comments.listar(30) } returns Result.success(listOf(comentario(1, 30)))
        coEvery { comments.listar(40) } returns Result.success(emptyList())

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()

        // El refresh del par 1 queda pendiente cuando el usuario navega al par 2.
        coEvery { similarities.obtener(1) } coAnswers {
            delay(50)
            Result.success(par(1, proyecto(10), proyecto(20)))
        }

        vm.refrescar()
        runCurrent()
        vm.load(2)
        advanceUntilIdle()

        val datos = datosDe(vm)
        assertEquals(2, datos.similarity.id)
        assertEquals(listOf(30), datos.comentarios1.map { it.proyectoId })
        assertEquals(false, vm.refrescando.value)
    }
}

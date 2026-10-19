package com.example.proyectwin.ui.viewmodel

import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.data.model.Comment
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.Similarity
import com.example.proyectwin.domain.repository.CommentsRepository
import com.example.proyectwin.domain.repository.SimilaritiesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Observaciones (solo lectura) en el detalle de similitud: consulta del
 * proyecto correcto, separación por propuesta, estados de carga/vacío/error
 * recuperable y descarte de respuestas tardías al navegar entre pares.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SimilarityDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val similarities = mockk<SimilaritiesRepository>(relaxed = true)
    private val comments = mockk<CommentsRepository>(relaxed = true)
    private val session = mockk<SessionManager>(relaxed = true)

    private fun viewModel() = SimilarityDetailViewModel(similarities, comments, session)

    private fun usuario(id: Int) = GeneralUser(id = id, name = "U$id", email = "u$id@x", role = "aprendiz")

    private fun proyecto(id: Int, studentId: Int? = null, equipo: List<GeneralUser> = emptyList()) =
        Project(id = id, title = "Propuesta $id", studentId = studentId, equipo = equipo)

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

    private fun datosDe(vm: SimilarityDetailViewModel): SimilarityDetailData =
        (vm.uiState.value as SimilarityDetailUiState.Success).data

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
    fun `dueno de un solo lado consulta unicamente su propuesta`() = runTest(dispatcher) {
        every { session.currentUser } returns flowOf(usuario(7))
        val p1 = proyecto(10, studentId = 4)
        val p2 = proyecto(20, equipo = listOf(usuario(7)))
        coEvery { similarities.obtener(1) } returns Result.success(par(p1 = p1, p2 = p2))
        coEvery { comments.listar(20) } returns Result.success(listOf(comentario(1, 20)))

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()

        val datos = datosDe(vm)
        assertEquals(setOf(20), datos.observaciones.keys)
        assertEquals(listOf(1), datos.observaciones.getValue(20).comentarios.map { it.id })
        assertNull(datos.observaciones.getValue(20).error)
        coVerify(exactly = 1) { comments.listar(20) }
        coVerify(exactly = 0) { comments.listar(10) }
    }

    @Test
    fun `ambas propuestas mias consulta solo la primera`() = runTest(dispatcher) {
        every { session.currentUser } returns flowOf(usuario(7))
        val p1 = proyecto(10, studentId = 7)
        val p2 = proyecto(20, studentId = 7)
        coEvery { similarities.obtener(1) } returns Result.success(par(p1 = p1, p2 = p2))

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()

        assertEquals(setOf(10), datosDe(vm).observaciones.keys)
        coVerify(exactly = 1) { comments.listar(10) }
        coVerify(exactly = 0) { comments.listar(20) }
    }

    @Test
    fun `sin propuesta propia consulta cada propuesta por separado sin mezclar`() = runTest(dispatcher) {
        every { session.currentUser } returns flowOf(usuario(7))
        val p1 = proyecto(10, studentId = 4)
        val p2 = proyecto(20, studentId = 5)
        coEvery { similarities.obtener(1) } returns Result.success(par(p1 = p1, p2 = p2))
        coEvery { comments.listar(10) } returns Result.success(listOf(comentario(1, 10, "de la A")))
        coEvery { comments.listar(20) } returns Result.success(listOf(comentario(2, 20, "de la B")))

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()

        val datos = datosDe(vm)
        assertEquals(setOf(10, 20), datos.observaciones.keys)
        assertEquals(listOf("de la A"), datos.observaciones.getValue(10).comentarios.map { it.texto })
        assertEquals(listOf("de la B"), datos.observaciones.getValue(20).comentarios.map { it.texto })
        coVerify(exactly = 1) { comments.listar(10) }
        coVerify(exactly = 1) { comments.listar(20) }
    }

    @Test
    fun `lista vacia queda sin error`() = runTest(dispatcher) {
        every { session.currentUser } returns flowOf(usuario(7))
        val p1 = proyecto(10, studentId = 7)
        val p2 = proyecto(20, studentId = 4)
        coEvery { similarities.obtener(1) } returns Result.success(par(p1 = p1, p2 = p2))

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()

        val estado = datosDe(vm).observaciones.getValue(10)
        assertTrue(estado.comentarios.isEmpty())
        assertNull(estado.error)
        assertEquals(false, estado.cargando)
    }

    @Test
    fun `error de observaciones es recuperable con reintento`() = runTest(dispatcher) {
        every { session.currentUser } returns flowOf(usuario(7))
        val p1 = proyecto(10, studentId = 7)
        val p2 = proyecto(20, studentId = 4)
        coEvery { similarities.obtener(1) } returns Result.success(par(p1 = p1, p2 = p2))
        coEvery { comments.listar(10) } returns Result.failure(ApiException.Http(500, "Fallo temporal"))

        val vm = viewModel()
        vm.load(1)
        advanceUntilIdle()
        assertEquals("Fallo temporal", datosDe(vm).observaciones.getValue(10).error)

        coEvery { comments.listar(10) } returns Result.success(listOf(comentario(1, 10)))
        vm.reintentarObservaciones(10)
        advanceUntilIdle()

        val estado = datosDe(vm).observaciones.getValue(10)
        assertNull(estado.error)
        assertEquals(listOf(1), estado.comentarios.map { it.id })
    }

    @Test
    fun `un fallo de la similitud mantiene el error de pantalla y no pide observaciones`() = runTest(dispatcher) {
        every { session.currentUser } returns flowOf(usuario(7))
        coEvery { similarities.obtener(9) } returns Result.failure(ApiException.Http(403, "Sin acceso."))

        val vm = viewModel()
        vm.load(9)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is SimilarityDetailUiState.Error)
        coVerify(exactly = 0) { comments.listar(any()) }
    }

    @Test
    fun `respuestas tardias de un par anterior no contaminan al navegar`() = runTest(dispatcher) {
        every { session.currentUser } returns flowOf(usuario(7))
        coEvery { similarities.obtener(1) } returns Result.success(
            par(1, proyecto(10, studentId = 7), proyecto(20, studentId = 4)),
        )
        coEvery { similarities.obtener(2) } returns Result.success(
            par(2, proyecto(30, studentId = 7), proyecto(40, studentId = 4)),
        )
        coEvery { comments.listar(30) } returns Result.success(listOf(comentario(2, 30)))
        coEvery { comments.listar(10) } coAnswers {
            delay(50)
            Result.success(listOf(comentario(1, 10)))
        }

        val vm = viewModel()
        vm.load(1)
        runCurrent()
        vm.load(2)
        advanceUntilIdle()

        val datos = datosDe(vm)
        assertEquals(2, datos.similarity.id)
        assertEquals(setOf(30), datos.observaciones.keys)
        assertEquals(listOf(2), datos.observaciones.getValue(30).comentarios.map { it.id })
    }
}

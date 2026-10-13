package com.example.proyectwin.ui.viewmodel

import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.data.model.Comment
import com.example.proyectwin.domain.repository.CommentsRepository
import com.example.proyectwin.domain.repository.FichasRepository
import com.example.proyectwin.domain.repository.MembersRepository
import com.example.proyectwin.domain.repository.ProjectsRepository
import com.example.proyectwin.domain.repository.SimilaritiesRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Before
import org.junit.Test

/**
 * Edición y eliminación de observaciones (fase M1/F5): llamada al backend con
 * los datos exactos, exposición de errores y protección de doble toque.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val projects = mockk<ProjectsRepository>(relaxed = true)
    private val comments = mockk<CommentsRepository>(relaxed = true)
    private val similarities = mockk<SimilaritiesRepository>(relaxed = true)
    private val members = mockk<MembersRepository>(relaxed = true)
    private val fichas = mockk<FichasRepository>(relaxed = true)
    private val session = mockk<SessionManager>(relaxed = true)

    private fun viewModel() = ProjectDetailViewModel(projects, comments, similarities, members, fichas, session)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `editar observacion envia el texto y el hilo correctos`() = runTest(dispatcher) {
        val original = Comment(id = 3, texto = "Viejo", proyectoId = 5, usuarioId = 1, respuestaA = 2)
        coEvery { comments.actualizar(3, 5, "Nuevo", 2) } returns Result.success(original.copy(texto = "Nuevo"))

        val vm = viewModel()
        vm.editarComentario(id = 3, idProyecto = 5, texto = "Nuevo", respuestaA = 2)
        advanceUntilIdle()

        coVerify(exactly = 1) { comments.actualizar(3, 5, "Nuevo", 2) }
        assertEquals(ProjectActionState.Success, vm.accion.value)
    }

    @Test
    fun `eliminar observacion llama al backend`() = runTest(dispatcher) {
        coEvery { comments.eliminar(9) } returns Result.success(Unit)

        val vm = viewModel()
        vm.eliminarComentario(9)
        advanceUntilIdle()

        coVerify(exactly = 1) { comments.eliminar(9) }
        assertEquals(ProjectActionState.Success, vm.accion.value)
    }

    @Test
    fun `un rechazo del backend se expone en el estado de accion`() = runTest(dispatcher) {
        coEvery { comments.actualizar(any(), any(), any(), any()) } returns Result.failure(
            ApiException.Http(403, "Solo puedes editar tus propias observaciones."),
        )

        val vm = viewModel()
        vm.editarComentario(id = 1, idProyecto = 2, texto = "x", respuestaA = null)
        advanceUntilIdle()

        assertEquals(
            ProjectActionState.Error("Solo puedes editar tus propias observaciones."),
            vm.accion.value,
        )
    }

    @Test
    fun `doble toque solo dispara una solicitud`() = runTest(dispatcher) {
        coEvery { comments.eliminar(any()) } coAnswers {
            delay(50)
            Result.success(Unit)
        }

        val vm = viewModel()
        vm.eliminarComentario(1)
        // En producción `viewModelScope` corre en Main.immediate: la primera
        // acción deja el estado en Loading antes de suspender en la red. Aquí
        // se avanza hasta ese punto para reproducir el segundo toque real.
        runCurrent()
        vm.eliminarComentario(2)
        advanceUntilIdle()

        coVerify(exactly = 1) { comments.eliminar(1) }
        coVerify(exactly = 0) { comments.eliminar(2) }
    }
}

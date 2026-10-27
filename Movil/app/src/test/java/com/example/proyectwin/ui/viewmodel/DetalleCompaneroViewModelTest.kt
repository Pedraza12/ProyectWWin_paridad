package com.example.proyectwin.ui.viewmodel

import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.data.model.ApprenticeProfile
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.Similarity
import com.example.proyectwin.domain.repository.FichasRepository
import com.example.proyectwin.domain.repository.MembersRepository
import com.example.proyectwin.domain.repository.ProjectsRepository
import com.example.proyectwin.domain.repository.SimilaritiesRepository
import com.example.proyectwin.domain.repository.UsersRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Paridad de perfiles: la ficha mostrada debe ser la del usuario consultado
 * (no la del solicitante) y las propuestas llevan el máximo de similitud.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetalleCompaneroViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val users = mockk<UsersRepository>(relaxed = true)
    private val fichas = mockk<FichasRepository>(relaxed = true)
    private val projects = mockk<ProjectsRepository>(relaxed = true)
    private val members = mockk<MembersRepository>(relaxed = true)
    private val similarities = mockk<SimilaritiesRepository>(relaxed = true)
    private val session = mockk<SessionManager>(relaxed = true)

    private fun viewModel() = DetalleCompaneroViewModel(users, fichas, projects, members, similarities, session)

    private fun usuario(id: Int, rol: String, fichaId: Int? = null) = GeneralUser(
        id = id,
        name = if (rol == "instructor") "Instructor Demo" else "Aprendiz Demo",
        email = "usuario$id@sena.edu.co",
        role = rol,
        fichaId = fichaId,
    )

    private fun ficha(id: Int, instructorUsuarioId: Int) = Ficha(
        id = id,
        codigo = "ABC-$id",
        programa = "ADSO",
        instructorId = 99,
        instructorUsuarioId = instructorUsuarioId,
        instructorName = "Instructor Demo",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `usa la ficha del usuario consultado y no la del solicitante`() = runTest(dispatcher) {
        coEvery { users.perfil(9) } returns Result.success(usuario(9, "aprendiz"))
        coEvery { members.aprendizPorUsuario(9) } returns Result.success(
            ApprenticeProfile(id = 1, idUsuario = 9, idClassGroup = 5),
        )
        coEvery { fichas.obtener(5) } returns Result.success(ficha(5, instructorUsuarioId = 3))
        coEvery { projects.listar() } returns Result.success(emptyList())
        coEvery { similarities.listar() } returns Result.success(emptyList())
        every { session.currentUser } returns flowOf(usuario(1, "aprendiz", fichaId = 12))

        val vm = viewModel()
        vm.load(9)
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertTrue(estado is CompaneroUiState.Success)
        assertEquals(5, (estado as CompaneroUiState.Success).data.ficha?.id)
    }

    @Test
    fun `el perfil de instructor muestra la ficha compartida`() = runTest(dispatcher) {
        coEvery { users.perfil(7) } returns Result.success(usuario(7, "instructor"))
        coEvery { members.aprendizPorUsuario(7) } returns Result.success(null)
        coEvery { fichas.obtener(12) } returns Result.success(ficha(12, instructorUsuarioId = 7))
        coEvery { projects.listar() } returns Result.success(emptyList())
        coEvery { similarities.listar() } returns Result.success(emptyList())
        every { session.currentUser } returns flowOf(usuario(1, "aprendiz", fichaId = 12))

        val vm = viewModel()
        vm.load(7)
        advanceUntilIdle()

        val data = (vm.uiState.value as CompaneroUiState.Success).data
        assertEquals(12, data.ficha?.id)
        assertTrue(data.proyectos.isEmpty())
    }

    @Test
    fun `no muestra la ficha si el instructor no es el de la ficha`() = runTest(dispatcher) {
        coEvery { users.perfil(8) } returns Result.success(usuario(8, "instructor"))
        coEvery { members.aprendizPorUsuario(8) } returns Result.success(null)
        coEvery { fichas.obtener(12) } returns Result.success(ficha(12, instructorUsuarioId = 7))
        coEvery { projects.listar() } returns Result.success(emptyList())
        coEvery { similarities.listar() } returns Result.success(emptyList())
        every { session.currentUser } returns flowOf(usuario(1, "aprendiz", fichaId = 12))

        val vm = viewModel()
        vm.load(8)
        advanceUntilIdle()

        val data = (vm.uiState.value as CompaneroUiState.Success).data
        assertNull(data.ficha)
    }

    @Test
    fun `calcula el porcentaje maximo de similitud por propuesta`() = runTest(dispatcher) {
        val proyecto = Project(id = 21, title = "Proyecto visible", studentId = 9)
        coEvery { users.perfil(9) } returns Result.success(usuario(9, "aprendiz"))
        coEvery { members.aprendizPorUsuario(9) } returns Result.success(
            ApprenticeProfile(id = 1, idUsuario = 9, idClassGroup = 5),
        )
        coEvery { fichas.obtener(5) } returns Result.success(ficha(5, instructorUsuarioId = 3))
        coEvery { projects.listar() } returns Result.success(listOf(proyecto))
        coEvery { similarities.listar() } returns Result.success(
            listOf(
                Similarity(id = 1, projectId1 = 21, projectId2 = 22, similitud = 0.25),
                Similarity(id = 2, projectId1 = 23, projectId2 = 21, similitud = 0.42),
            ),
        )
        every { session.currentUser } returns flowOf(usuario(1, "aprendiz", fichaId = 12))

        val vm = viewModel()
        vm.load(9)
        advanceUntilIdle()

        val data = (vm.uiState.value as CompaneroUiState.Success).data
        assertEquals(42, data.porcentajesSimilitud[21])
    }
}

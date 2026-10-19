package com.example.proyectwin.ui.viewmodel

import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.model.AuditLog
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.domain.repository.AuditRepository
import com.example.proyectwin.domain.repository.UsersRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Bitácora admin: filtros del contrato real (`accion`, `entidad`,
 * `id_usuario`, `desde`, `hasta`), paginación en cliente con filtros
 * conservados, estados y descarte de respuestas desactualizadas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitacoraViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val audit = mockk<AuditRepository>(relaxed = true)
    private val users = mockk<UsersRepository>(relaxed = true)

    private fun viewModel() = BitacoraViewModel(audit, users)

    private fun registro(id: Int) = AuditLog(
        id = id,
        accion = "crear_usuario",
        createdAt = "2026-10-01T00:00:00.000000Z",
    )

    private fun registros(n: Int) = List(n) { registro(it + 1) }

    private fun datosDe(vm: BitacoraViewModel): BitacoraUiState.Success =
        vm.uiState.value as BitacoraUiState.Success

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { audit.listar(any(), any(), any(), any(), any()) } returns Result.success(emptyList())
        coEvery { users.listar() } returns Result.success(
            listOf(GeneralUser(id = 1, name = "Ana", email = "a@x", role = "admin")),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `carga inicial sin filtros y catalogo de responsables`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { audit.listar(null, null, null, null, null) }
        assertTrue(vm.uiState.value is BitacoraUiState.Success)
        assertEquals(1, vm.usuarios.value.size)
        assertTrue(!datosDe(vm).filtros.hayFiltros)
    }

    @Test
    fun `filtro por usuario usa el id real`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.load(idUsuario = 7)
        advanceUntilIdle()

        coVerify(exactly = 1) { audit.listar(null, null, 7, null, null) }
        assertEquals(7, datosDe(vm).filtros.idUsuario)
    }

    @Test
    fun `combinacion de filtros viaja completa`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.load("crear_usuario", "general_users", 7, "2026-10-01", "2026-10-31")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audit.listar("crear_usuario", "general_users", 7, "2026-10-01", "2026-10-31")
        }
        assertTrue(datosDe(vm).filtros.hayFiltros)
    }

    @Test
    fun `cambiar de pagina conserva filtros y no repite la consulta`() = runTest(dispatcher) {
        coEvery { audit.listar(any(), any(), any(), any(), any()) } returns Result.success(registros(20))
        val vm = viewModel()
        advanceUntilIdle()
        vm.load(idUsuario = 7)
        advanceUntilIdle()

        vm.irAPagina(2)

        val datos = datosDe(vm)
        assertEquals(2, datos.pagina)
        assertEquals(3, datos.totalPaginas)
        assertEquals((9..16).toList(), datos.visibles.map { it.id })
        assertEquals(7, datos.filtros.idUsuario)
        // init + load: paginar en cliente no genera consultas extra.
        coVerify(exactly = 2) { audit.listar(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cambiar un filtro vuelve a la primera pagina`() = runTest(dispatcher) {
        coEvery { audit.listar(any(), any(), any(), any(), any()) } returns Result.success(registros(20))
        val vm = viewModel()
        advanceUntilIdle()

        vm.irAPagina(3)
        assertEquals(3, datosDe(vm).pagina)

        vm.load(idUsuario = 3)
        advanceUntilIdle()
        assertEquals(1, datosDe(vm).pagina)
    }

    @Test
    fun `limpiar quita los filtros y recarga`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.load(entidad = "projects")
        advanceUntilIdle()
        assertTrue(datosDe(vm).filtros.hayFiltros)

        vm.limpiar()
        advanceUntilIdle()

        assertTrue(!datosDe(vm).filtros.hayFiltros)
        // init + limpiar: dos consultas sin filtros, ninguna con `entidad`.
        coVerify(exactly = 2) { audit.listar(null, null, null, null, null) }
    }

    @Test
    fun `lista vacia distingue con y sin filtros`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(datosDe(vm).registros.isEmpty())
        assertTrue(!datosDe(vm).filtros.hayFiltros)
        assertEquals(1, datosDe(vm).totalPaginas)

        vm.load(entidad = "projects")
        advanceUntilIdle()
        assertTrue(datosDe(vm).registros.isEmpty())
        assertTrue(datosDe(vm).filtros.hayFiltros)
    }

    @Test
    fun `error es recuperable manteniendo los filtros`() = runTest(dispatcher) {
        coEvery { audit.listar("crear_usuario", any(), any(), any(), any()) } returns
            Result.failure(ApiException.Http(500, "Fallo temporal"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.load(accion = "crear_usuario")
        advanceUntilIdle()
        assertTrue(vm.uiState.value is BitacoraUiState.Error)

        coEvery { audit.listar("crear_usuario", any(), any(), any(), any()) } returns
            Result.success(listOf(registro(1), registro(2)))
        vm.recargar()
        advanceUntilIdle()

        assertEquals(2, datosDe(vm).registros.size)
        assertEquals("crear_usuario", datosDe(vm).filtros.accion)
    }

    @Test
    fun `respuesta vieja no pisa un filtro mas reciente`() = runTest(dispatcher) {
        // La consulta inicial (sin filtros) tarda; la del filtro responde ya.
        coEvery { audit.listar(null, null, null, null, null) } coAnswers {
            delay(100)
            Result.success(registros(1))
        }
        coEvery { audit.listar(null, null, 7, null, null) } returns Result.success(registros(3))

        val vm = viewModel()
        runCurrent()
        vm.load(idUsuario = 7)
        advanceUntilIdle()

        assertEquals(3, datosDe(vm).registros.size)
        assertEquals(7, datosDe(vm).filtros.idUsuario)
    }

    @Test
    fun `pagina fuera de rango se corrige al limite`() = runTest(dispatcher) {
        coEvery { audit.listar(any(), any(), any(), any(), any()) } returns Result.success(registros(20))
        val vm = viewModel()
        advanceUntilIdle()

        vm.irAPagina(99)
        assertEquals(3, datosDe(vm).pagina)
        vm.irAPagina(0)
        assertEquals(1, datosDe(vm).pagina)
        vm.paginaSiguiente()
        assertEquals(2, datosDe(vm).pagina)
        vm.paginaAnterior()
        assertEquals(1, datosDe(vm).pagina)
    }
}

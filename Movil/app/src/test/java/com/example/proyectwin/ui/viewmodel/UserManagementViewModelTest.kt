package com.example.proyectwin.ui.viewmodel

import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.data.model.PaginaUsuarios
import com.example.proyectwin.domain.repository.CatalogsRepository
import com.example.proyectwin.domain.repository.FichasRepository
import com.example.proyectwin.domain.repository.UsersRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Gestión admin de usuarios: filtros del contrato real (`search`, `role`,
 * `estado`, `ficha_id`, `programa`), paginación del servidor, reinicio a la
 * primera página al filtrar, descarte de respuestas desactualizadas y
 * selección múltiple para el PDF de credenciales por lote.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserManagementViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val users = mockk<UsersRepository>(relaxed = true)
    private val fichasRepo = mockk<FichasRepository>(relaxed = true)
    private val catalogsRepo = mockk<CatalogsRepository>(relaxed = true)

    private fun viewModel() = UserManagementViewModel(users, fichasRepo, catalogsRepo)

    private fun usuario(id: Int) = GeneralUser(id = id, name = "Usuario $id", email = "u$id@x", role = "aprendiz")

    private fun pagina(items: Int = 8, current: Int = 1, last: Int = 3, total: Int = 20) =
        PaginaUsuarios(items = List(items) { usuario(it + 1) }, currentPage = current, lastPage = last, total = total)

    private fun datosDe(vm: UserManagementViewModel): PaginaUsuarios =
        (vm.uiState.value as UserManagementUiState.Success).pagina

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { fichasRepo.listar() } returns Result.success(emptyList())
        coEvery { catalogsRepo.listarProgramas() } returns Result.success(emptyList())
        coEvery { users.listarPaginado(any(), any(), any(), any(), any(), any(), any()) } returns Result.success(pagina())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `carga inicial consulta pagina 1 con 8 por pagina y sin filtros`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { users.listarPaginado(1, 8, null, null, null, null, null) }
        assertEquals(20, datosDe(vm).total)
    }

    @Test
    fun `cada filtro viaja con el nombre real del contrato`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.filtrarRol("aprendiz")
        advanceUntilIdle()
        coVerify(exactly = 1) { users.listarPaginado(1, 8, null, "aprendiz", null, null, null) }

        vm.filtrarEstado("activo")
        advanceUntilIdle()
        coVerify(exactly = 1) { users.listarPaginado(1, 8, null, "aprendiz", "activo", null, null) }

        vm.filtrarFicha("sin")
        advanceUntilIdle()
        coVerify(exactly = 1) { users.listarPaginado(1, 8, null, "aprendiz", "activo", "sin", null) }

        vm.filtrarPrograma("ADSO")
        advanceUntilIdle()
        coVerify(exactly = 1) { users.listarPaginado(1, 8, null, "aprendiz", "activo", "sin", "ADSO") }
    }

    @Test
    fun `varios filtros y busqueda se combinan en una sola consulta`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.buscar("ana")
        advanceUntilIdle()
        coVerify(exactly = 1) { users.listarPaginado(1, 8, "ana", null, null, null, null) }

        vm.filtrarRol("instructor")
        vm.filtrarEstado("suspendido")
        vm.filtrarFicha("3")
        vm.filtrarPrograma("ADSO")
        advanceUntilIdle()

        coVerify(exactly = 1) { users.listarPaginado(1, 8, "ana", "instructor", "suspendido", "3", "ADSO") }
    }

    @Test
    fun `cambiar de pagina conserva los filtros`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.filtrarRol("aprendiz")
        advanceUntilIdle()

        vm.paginaSiguiente()
        advanceUntilIdle()

        coVerify(exactly = 1) { users.listarPaginado(2, 8, null, "aprendiz", null, null, null) }
    }

    @Test
    fun `cambiar un filtro vuelve a la primera pagina`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.irAPagina(3)
        advanceUntilIdle()
        coVerify(exactly = 1) { users.listarPaginado(3, 8, null, null, null, null, null) }

        vm.filtrarEstado("activo")
        advanceUntilIdle()
        coVerify(exactly = 1) { users.listarPaginado(1, 8, null, null, "activo", null, null) }
    }

    @Test
    fun `buscar espera 300 ms y vuelve a la primera pagina`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.irAPagina(2)
        advanceUntilIdle()

        vm.buscar("mg")
        // El texto se refleja de inmediato, pero la consulta espera el retardo.
        assertEquals("mg", vm.filtros.value.search)
        advanceTimeBy(UserManagementViewModel.DEBOUNCE_BUSQUEDA_MS - 1)
        coVerify(exactly = 0) { users.listarPaginado(1, 8, "mg", null, null, null, null) }

        advanceTimeBy(2)
        advanceUntilIdle()
        coVerify(exactly = 1) { users.listarPaginado(1, 8, "mg", null, null, null, null) }
    }

    @Test
    fun `escribir rapido solo dispara la ultima busqueda`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.buscar("m")
        advanceTimeBy(100)
        vm.buscar("mg")
        advanceUntilIdle()

        coVerify(exactly = 1) { users.listarPaginado(1, 8, "mg", null, null, null, null) }
        coVerify(exactly = 0) { users.listarPaginado(1, 8, "m", null, null, null, null) }
    }

    @Test
    fun `resultado vacio queda como exito sin elementos`() = runTest(dispatcher) {
        coEvery { users.listarPaginado(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(pagina(items = 0, last = 1, total = 0))

        val vm = viewModel()
        advanceUntilIdle()

        val pagina = datosDe(vm)
        assertTrue(pagina.items.isEmpty())
        assertEquals(0, pagina.total)
    }

    @Test
    fun `error de carga es recuperable con recargar`() = runTest(dispatcher) {
        coEvery { users.listarPaginado(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(ApiException.Http(500, "Fallo temporal"))

        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is UserManagementUiState.Error)

        coEvery { users.listarPaginado(any(), any(), any(), any(), any(), any(), any()) } returns Result.success(pagina())
        vm.recargar()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is UserManagementUiState.Success)
        assertEquals(20, datosDe(vm).total)
    }

    @Test
    fun `una respuesta vieja no pisa el resultado de un filtro mas reciente`() = runTest(dispatcher) {
        // La primera consulta (sin filtros) tarda; la del filtro responde ya.
        coEvery { users.listarPaginado(1, 8, null, null, null, null, null) } coAnswers {
            kotlinx.coroutines.delay(100)
            Result.success(pagina(items = 1, last = 1, total = 111))
        }
        coEvery { users.listarPaginado(1, 8, null, "aprendiz", null, null, null) } returns
            Result.success(pagina(items = 2, last = 2, total = 22))

        val vm = viewModel()
        runCurrent()
        vm.filtrarRol("aprendiz")
        advanceUntilIdle()

        assertEquals(22, datosDe(vm).total)
        assertEquals(2, datosDe(vm).items.size)
    }

    @Test
    fun `eliminar recarga la pagina y un fallo se avisa una sola vez`() = runTest(dispatcher) {
        coEvery { users.eliminar(5) } returns Result.success(Unit)

        val vm = viewModel()
        advanceUntilIdle()
        vm.eliminarUsuario(5)
        advanceUntilIdle()

        coVerify(exactly = 1) { users.eliminar(5) }
        // init + recarga tras eliminar = dos consultas idénticas
        coVerify(exactly = 2) { users.listarPaginado(1, 8, null, null, null, null, null) }

        coEvery { users.eliminar(6) } returns Result.failure(ApiException.Http(403, "No se puede eliminar."))
        vm.eliminarUsuario(6)
        advanceUntilIdle()
        assertEquals("No se puede eliminar.", vm.errorAccion.value)

        vm.limpiarErrorAccion()
        assertNull(vm.errorAccion.value)
    }

    @Test
    fun `una pagina inexistente se corrige a la ultima disponible`() = runTest(dispatcher) {
        coEvery { users.listarPaginado(1, 8, null, null, null, null, null) } returns
            Result.success(pagina(items = 8, current = 1, last = 3, total = 20))
        // Página 3 ya no existe (otro admin borró filas): vacía y last_page=2.
        coEvery { users.listarPaginado(3, 8, null, null, null, null, null) } returns
            Result.success(PaginaUsuarios(emptyList(), currentPage = 3, lastPage = 2, total = 10))
        coEvery { users.listarPaginado(2, 8, null, null, null, null, null) } returns
            Result.success(pagina(items = 2, current = 2, last = 2, total = 10))

        val vm = viewModel()
        advanceUntilIdle()
        vm.irAPagina(3)
        advanceUntilIdle()

        val datos = datosDe(vm)
        assertEquals(2, datos.currentPage)
        assertEquals(2, datos.items.size)
        coVerify(exactly = 1) { users.listarPaginado(3, 8, null, null, null, null, null) }
        coVerify(exactly = 1) { users.listarPaginado(2, 8, null, null, null, null, null) }
    }

    // ------------------------------------------- Selección múltiple (PDF por lote)

    @Test
    fun `activar y desactivar el modo seleccion limpia los ids marcados`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.activarSeleccion()
        vm.toggleSeleccion(3)
        assertTrue(vm.seleccionActiva.value)
        assertEquals(setOf(3), vm.seleccionados.value)

        vm.desactivarSeleccion()
        assertFalse(vm.seleccionActiva.value)
        assertTrue(vm.seleccionados.value.isEmpty())
    }

    @Test
    fun `toggle alterna la marca del mismo usuario`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleSeleccion(1)
        vm.toggleSeleccion(2)
        assertEquals(setOf(1, 2), vm.seleccionados.value)

        vm.toggleSeleccion(1)
        assertEquals(setOf(2), vm.seleccionados.value)
    }

    @Test
    fun `todos en pagina marca y desmarca solo la pagina visible`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleTodosEnPagina(listOf(1, 2, 3))
        assertEquals(setOf(1, 2, 3), vm.seleccionados.value)

        vm.toggleSeleccion(9) // de otra página
        vm.toggleTodosEnPagina(listOf(1, 2, 3))
        assertEquals(setOf(9), vm.seleccionados.value)
    }

    @Test
    fun `la seleccion persiste al cambiar de pagina`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleSeleccion(1)
        vm.paginaSiguiente()
        advanceUntilIdle()
        vm.toggleSeleccion(9)

        assertEquals(setOf(1, 9), vm.seleccionados.value)
    }

    @Test
    fun `cambiar filtro, buscar o limpiar filtros vacia la seleccion`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.activarSeleccion()

        vm.toggleSeleccion(1)
        vm.filtrarRol("aprendiz")
        assertTrue(vm.seleccionados.value.isEmpty())
        assertTrue(vm.seleccionActiva.value) // el modo sigue activo

        advanceUntilIdle()
        vm.toggleSeleccion(2)
        vm.buscar("ana")
        assertTrue(vm.seleccionados.value.isEmpty())

        advanceUntilIdle()
        vm.toggleSeleccion(3)
        vm.limpiarFiltros()
        advanceUntilIdle()
        assertTrue(vm.seleccionados.value.isEmpty())
    }

    @Test
    fun `los ids para el pdf van ordenados y sin repetidos`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleSeleccion(9)
        vm.toggleSeleccion(2)
        vm.toggleSeleccion(5)
        vm.toggleSeleccion(2) // quita
        vm.toggleSeleccion(2) // vuelve a marcar

        assertEquals(listOf(2, 5, 9), vm.idsSeleccionados())
    }

    @Test
    fun `un fallo del pdf se avisa por el snackbar y conserva la seleccion`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleSeleccion(4)
        vm.reportarError("No se pudo descargar el PDF.")

        assertEquals("No se pudo descargar el PDF.", vm.errorAccion.value)
        assertEquals(setOf(4), vm.seleccionados.value)

        vm.limpiarErrorAccion()
        assertNull(vm.errorAccion.value)
    }

    @Test
    fun `eliminar un usuario lo saca de la seleccion`() = runTest(dispatcher) {
        coEvery { users.eliminar(5) } returns Result.success(Unit)

        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleSeleccion(5)
        vm.toggleSeleccion(7)

        vm.eliminarUsuario(5)
        advanceUntilIdle()

        assertEquals(setOf(7), vm.seleccionados.value)
    }

    // ------------------------------------------- Pull-to-refresh sin skeleton

    @Test
    fun `la carga inicial usa el estado de carga y no el indicador de refresco`() = runTest(dispatcher) {
        coEvery { users.listarPaginado(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(100)
            Result.success(pagina())
        }

        val vm = viewModel()
        runCurrent()

        assertTrue(vm.uiState.value is UserManagementUiState.Loading)
        assertFalse(vm.refrescando.value)

        advanceUntilIdle()
        assertTrue(vm.uiState.value is UserManagementUiState.Success)
        assertEquals(20, datosDe(vm).total)
    }

    @Test
    fun `recargar conserva la lista visible mientras responde`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(20, datosDe(vm).total)

        // La recarga tarda: la lista anterior debe seguir en pantalla.
        coEvery { users.listarPaginado(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(100)
            Result.success(pagina(items = 2, last = 1, total = 22))
        }
        vm.recargar()
        runCurrent()

        assertTrue(vm.refrescando.value)
        assertTrue(vm.uiState.value is UserManagementUiState.Success)
        assertEquals(20, datosDe(vm).total) // sigue el éxito previo, sin skeleton

        advanceUntilIdle()
        assertFalse(vm.refrescando.value)
        assertEquals(22, datosDe(vm).total)
    }
}

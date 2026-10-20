package com.example.proyectwin.ui.viewmodel

import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.data.model.ResultadoFichaEliminada
import com.example.proyectwin.domain.repository.FichasRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Acciones de fichas: eliminación/anulación y unión por código. */
@OptIn(ExperimentalCoroutinesApi::class)
class FichasViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<FichasRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `eliminar ficha con dependencias avisa que quedo anulada y refresca`() = runTest(dispatcher) {
        coEvery { repo.eliminar(7) } returns Result.success(ResultadoFichaEliminada(accion = "anulada"))
        coEvery { repo.listar() } returns Result.success(emptyList())

        val vm = FichasViewModel(repo)
        vm.eliminarFicha(7)
        advanceUntilIdle()

        assertEquals(
            "La ficha tenía aprendices o propuestas: quedó anulada.",
            vm.actionState.value.message,
        )
        assertFalse(vm.actionState.value.eliminando)
        coVerify { repo.listar() }
    }

    @Test
    fun `eliminar ficha sin dependencias confirma la eliminacion`() = runTest(dispatcher) {
        coEvery { repo.eliminar(3) } returns Result.success(ResultadoFichaEliminada(accion = "eliminada"))
        coEvery { repo.listar() } returns Result.success(emptyList())

        val vm = FichasViewModel(repo)
        vm.eliminarFicha(3)
        advanceUntilIdle()

        assertEquals("Ficha eliminada.", vm.actionState.value.message)
        assertFalse(vm.actionState.value.eliminando)
    }

    @Test
    fun `eliminar ficha rechazada conserva el mensaje del backend`() = runTest(dispatcher) {
        coEvery { repo.eliminar(9) } returns Result.failure(
            ApiException.Http(403, "No puedes eliminar una ficha que no está a tu cargo."),
        )

        val vm = FichasViewModel(repo)
        vm.eliminarFicha(9)
        advanceUntilIdle()

        assertEquals(
            "No puedes eliminar una ficha que no está a tu cargo.",
            vm.actionState.value.message,
        )
        assertFalse(vm.actionState.value.eliminando)
        coVerify(exactly = 0) { repo.listar() }
    }

    @Test
    fun `unirse usa el codigo digitado aunque la preview no devuelva codigo`() = runTest(dispatcher) {
        // Simula una preview cuyo DTO omite `codigo` (se mapea como "").
        coEvery { repo.fichaPorCodigo("abc-defg") } returns Result.success(
            Ficha(id = 42, codigo = "", programa = "ADSO"),
        )
        coEvery { repo.unirmeAFicha("abc-defg") } returns Result.success(
            Ficha(id = 42, codigo = "abc-defg", programa = "ADSO"),
        )

        val vm = FichasViewModel(repo)
        vm.validarCodigo("abc-defg")
        advanceUntilIdle()

        assertEquals(true, vm.actionState.value.codigoValido)
        assertEquals("abc-defg", vm.actionState.value.codigoValidado)

        vm.joinFicha("abc-defg")
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.unirmeAFicha("abc-defg") }
        coVerify(exactly = 0) { repo.unirmeAFicha("") }
        assertTrue(vm.actionState.value.joinSuccess)
    }

    @Test
    fun `unirse propaga el error del backend`() = runTest(dispatcher) {
        coEvery { repo.fichaPorCodigo("abc-defg") } returns Result.success(
            Ficha(id = 42, codigo = "", programa = "ADSO"),
        )
        coEvery { repo.unirmeAFicha("abc-defg") } returns Result.failure(
            ApiException.Http(422, "Esa ficha no acepta nuevos integrantes."),
        )

        val vm = FichasViewModel(repo)
        vm.validarCodigo("abc-defg")
        advanceUntilIdle()

        vm.joinFicha("abc-defg")
        advanceUntilIdle()

        assertEquals(
            "Esa ficha no acepta nuevos integrantes.",
            vm.actionState.value.message,
        )
        assertFalse(vm.actionState.value.joinSuccess)
        coVerify(exactly = 1) { repo.unirmeAFicha("abc-defg") }
    }

    @Test
    fun `unirse con codigo en blanco no llama al backend`() = runTest(dispatcher) {
        val vm = FichasViewModel(repo)

        vm.joinFicha("   ")
        advanceUntilIdle()

        assertEquals(
            "Valida el código de la ficha antes de unirte.",
            vm.actionState.value.message,
        )
        coVerify(exactly = 0) { repo.unirmeAFicha(any()) }
    }
}

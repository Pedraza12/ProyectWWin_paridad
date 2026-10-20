package com.example.proyectwin.ui.viewmodel

import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.domain.repository.AuthRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Perfil propio (instructor/admin): carga de la sesión, guardado de
 * nombre/apellido (éxito, error y estado `isSaving`), foto y cambio de correo
 * (éxito/error). El VM no valida campos en cliente ni maneja permisos: la
 * validación del correo la resuelve el backend (se documenta con prueba) y el
 * permiso de galería lo gestiona el selector del sistema en la pantalla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<AuthRepository>(relaxed = true)

    private fun usuario() = GeneralUser(
        id = 7,
        name = "Ana María Gómez",
        email = "ana@sena.edu.co",
        role = "instructor",
        username = "ana",
        nombre = "Ana María",
        apellido = "Gómez",
    )

    private fun viewModel(user: GeneralUser? = usuario()): ProfileViewModel {
        every { repo.currentUser } returns flowOf(user)
        return ProfileViewModel(repo)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init carga el usuario de la sesion`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertTrue(estado is ProfileUiState.Loaded)
        assertEquals(usuario(), (estado as ProfileUiState.Loaded).user)
    }

    @Test
    fun `sin sesion queda cargado con usuario nulo`() = runTest(dispatcher) {
        val vm = viewModel(user = null)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is ProfileUiState.Loaded)
        assertNull((vm.uiState.value as ProfileUiState.Loaded).user)
    }

    @Test
    fun `guardar nombre y apellido avisa exito y limpia el error`() = runTest(dispatcher) {
        coEvery { repo.updateProfile(any(), any()) } returns Result.failure(ApiException.Http(500, "Fallo temporal"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateProfileNombres("Ana María", "Gómez")
        advanceUntilIdle()
        assertEquals("Fallo temporal", vm.saveError.value)

        coEvery { repo.updateProfile(any(), any()) } returns Result.success(usuario())
        vm.updateProfileNombres("Ana María", "Gómez")
        advanceUntilIdle()

        coVerify(exactly = 2) { repo.updateProfile("Ana María", "Gómez") }
        assertTrue(vm.saveSuccess.value)
        assertNull(vm.saveError.value)
        assertFalse(vm.isSaving.value)
    }

    @Test
    fun `error al guardar expone el mensaje y no marca exito`() = runTest(dispatcher) {
        coEvery { repo.updateProfile(any(), any()) } returns
            Result.failure(ApiException.Http(422, "El nombre es obligatorio."))
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateProfileNombres("", "")
        advanceUntilIdle()

        assertEquals("El nombre es obligatorio.", vm.saveError.value)
        assertFalse(vm.saveSuccess.value)
        assertFalse(vm.isSaving.value)
    }

    @Test
    fun `error sin mensaje usa el texto por defecto`() = runTest(dispatcher) {
        coEvery { repo.updateProfile(any(), any()) } returns Result.failure(IllegalStateException())
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateProfileNombres("Ana", "Gómez")
        advanceUntilIdle()

        assertEquals("Error al actualizar el perfil", vm.saveError.value)
    }

    @Test
    fun `isSaving se activa mientras guarda y se apaga al terminar`() = runTest(dispatcher) {
        coEvery { repo.updateProfile(any(), any()) } coAnswers {
            delay(100)
            Result.success(usuario())
        }
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateProfileNombres("Ana", "Gómez")
        runCurrent()

        assertTrue(vm.isSaving.value)
        advanceUntilIdle()

        assertFalse(vm.isSaving.value)
        assertTrue(vm.saveSuccess.value)
    }

    @Test
    fun `updateProfile divide el nombre completo conservando apellidos compuestos`() = runTest(dispatcher) {
        coEvery { repo.updateProfile(any(), any()) } returns Result.success(usuario())
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateProfile("Ana María Gómez")
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.updateProfile("Ana", "María Gómez") }
    }

    @Test
    fun `guardar foto exitoso marca exito`() = runTest(dispatcher) {
        coEvery { repo.updateFoto("data:image/png;base64,AAA") } returns Result.success(usuario())
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateFoto("data:image/png;base64,AAA")
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.updateFoto("data:image/png;base64,AAA") }
        assertTrue(vm.saveSuccess.value)
        assertNull(vm.saveError.value)
        assertFalse(vm.isSaving.value)
    }

    @Test
    fun `error al guardar foto usa el texto por defecto`() = runTest(dispatcher) {
        coEvery { repo.updateFoto(null) } returns Result.failure(IllegalStateException())
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateFoto(null)
        advanceUntilIdle()

        assertEquals("Error al actualizar la foto", vm.saveError.value)
        assertFalse(vm.saveSuccess.value)
    }

    @Test
    fun `cambio de correo exitoso avisa exito y baja los indicadores`() = runTest(dispatcher) {
        coEvery { repo.changeEmail(any(), any()) } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.changeEmail("nueva@sena.edu.co", "claveActual1")
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.changeEmail("nueva@sena.edu.co", "claveActual1") }
        assertTrue(vm.saveSuccess.value)
        assertNull(vm.saveError.value)
        assertFalse(vm.isChangingEmail.value)
        assertFalse(vm.isSaving.value)
    }

    @Test
    fun `cambio de correo fallido expone el error del backend y baja los indicadores`() = runTest(dispatcher) {
        coEvery { repo.changeEmail(any(), any()) } returns
            Result.failure(ApiException.Http(422, "La contraseña actual no es correcta."))
        val vm = viewModel()
        advanceUntilIdle()

        vm.changeEmail("nueva@sena.edu.co", "mala")
        advanceUntilIdle()

        assertEquals("La contraseña actual no es correcta.", vm.saveError.value)
        assertFalse(vm.saveSuccess.value)
        assertFalse(vm.isChangingEmail.value)
        assertFalse(vm.isSaving.value)
    }

    @Test
    fun `correo mal formado se delega al backend sin validacion local`() = runTest(dispatcher) {
        // El VM no valida el correo en cliente: lo envía tal cual y expone el 422.
        coEvery { repo.changeEmail("sin-arroba", "") } returns
            Result.failure(ApiException.Http(422, "El correo no es válido."))
        val vm = viewModel()
        advanceUntilIdle()

        vm.changeEmail("sin-arroba", "")
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.changeEmail("sin-arroba", "") }
        assertEquals("El correo no es válido.", vm.saveError.value)
    }

    @Test
    fun `clearSaveSuccess y clearError rearman el estado de guardado`() = runTest(dispatcher) {
        coEvery { repo.updateProfile(any(), any()) } returns Result.success(usuario())
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateProfileNombres("Ana", "Gómez")
        advanceUntilIdle()
        assertTrue(vm.saveSuccess.value)

        vm.clearSaveSuccess()
        assertFalse(vm.saveSuccess.value)

        coEvery { repo.updateProfile(any(), any()) } returns Result.failure(ApiException.Http(500, "Fallo"))
        vm.updateProfileNombres("Ana", "Gómez")
        advanceUntilIdle()
        assertEquals("Fallo", vm.saveError.value)

        vm.clearError()
        assertNull(vm.saveError.value)
    }
}

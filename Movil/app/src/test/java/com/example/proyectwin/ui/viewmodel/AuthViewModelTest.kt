package com.example.proyectwin.ui.viewmodel

import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
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
import java.io.IOException

/**
 * Ciclo de sesión del ViewModel (Fase 2): arranque con 401/cuenta suspendida,
 * login, y cambio de contraseña (éxito y 422).
 */
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<AuthRepository>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)

    private fun usuario(mustChange: Boolean = false) = GeneralUser(
        id = 1,
        name = "Ana Gómez",
        email = "ana@sena.edu.co",
        role = "aprendiz",
        username = "ana",
        mustChangePassword = mustChange,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun arranque(user: GeneralUser?, refresh: Result<GeneralUser>?): AuthViewModel {
        every { repo.currentUser } returns flowOf(user)
        if (refresh != null) coEvery { repo.refreshSession() } returns refresh
        coEvery { repo.logout() } returns Result.success(Unit)
        return AuthViewModel(repo, sessionManager)
    }

    @Test
    fun arranque_con401_cierraLaSesion() = runTest(dispatcher) {
        val vm = arranque(
            usuario(),
            Result.failure(ApiException.Http(401, "Tu sesión expiró. Inicia sesión nuevamente.")),
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.logout() }
        assertEquals(AuthUiState.LoggedOut, vm.uiState.value)
    }

    @Test
    fun arranque_conCuentaSuspendida_cierraLaSesion() = runTest(dispatcher) {
        val vm = arranque(
            usuario(),
            Result.failure(
                ApiException.Http(
                    403,
                    "Cuenta suspendida. Contacta al administrador.",
                    cuentaSuspendida = true,
                ),
            ),
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.logout() }
        assertEquals(AuthUiState.LoggedOut, vm.uiState.value)
    }

    @Test
    fun arranque_conErrorDeRed_conservaLaSesion() = runTest(dispatcher) {
        val vm = arranque(
            usuario(),
            Result.failure(ApiException.SinConexion(IOException("sin red"))),
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.logout() }
        assertTrue(vm.uiState.value is AuthUiState.LoggedIn)
    }

    @Test
    fun arranque_sinSesion_noRefresca() = runTest(dispatcher) {
        val vm = arranque(null, null)
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.refreshSession() }
        assertEquals(AuthUiState.LoggedOut, vm.uiState.value)
    }

    @Test
    fun login_exitoso_dejaLaSesionActiva() = runTest(dispatcher) {
        val vm = arranque(null, null)
        coEvery { repo.login(any(), any(), any()) } returns Result.success(usuario())
        advanceUntilIdle()

        vm.login("ana", "clave123")
        advanceUntilIdle()

        assertEquals(AuthUiState.LoggedIn(usuario()), vm.uiState.value)
    }

    @Test
    fun login_fallido_exponeElError() = runTest(dispatcher) {
        val vm = arranque(null, null)
        coEvery { repo.login(any(), any(), any()) } returns Result.failure(
            ApiException.Http(422, "Credenciales incorrectas. Verifica tus datos."),
        )
        advanceUntilIdle()

        vm.login("ana", "mala")
        advanceUntilIdle()

        assertEquals(
            AuthUiState.Error("Credenciales incorrectas. Verifica tus datos."),
            vm.uiState.value,
        )
    }

    @Test
    fun cambioDeClaveCon422_exponeLosErroresDeCampo() = runTest(dispatcher) {
        val vm = arranque(usuario(mustChange = true), Result.success(usuario(mustChange = true)))
        coEvery { repo.changePassword(any(), any(), any()) } returns Result.failure(
            ApiException.Http(
                422,
                "Los datos no son válidos.",
                fieldErrors = mapOf("password" to "La contraseña debe tener al menos 6 caracteres."),
            ),
        )
        advanceUntilIdle()

        vm.changePassword("actual", "corta", "corta")
        advanceUntilIdle()

        val estado = vm.passwordChange.value as AuthActionState.Error
        assertEquals("La contraseña debe tener al menos 6 caracteres.", estado.fieldErrors["password"])
    }

    @Test
    fun cambioDeClaveExitoso_liberaLaMarca() = runTest(dispatcher) {
        val vm = arranque(usuario(mustChange = true), Result.success(usuario(mustChange = true)))
        coEvery { repo.changePassword(any(), any(), any()) } returns Result.success(Unit)
        advanceUntilIdle()

        vm.changePassword("temporal", "nueva123", "nueva123")
        advanceUntilIdle()

        assertEquals(AuthActionState.Success, vm.passwordChange.value)
        val estado = vm.uiState.value as AuthUiState.LoggedIn
        assertEquals(false, estado.user.mustChangePassword)
    }

    @Test
    fun marcaDeCambioObligatorio_persisteYSeRefleja() = runTest(dispatcher) {
        val vm = arranque(usuario(), Result.success(usuario()))
        advanceUntilIdle()

        vm.markMustChangePassword()
        advanceUntilIdle()

        coVerify { sessionManager.setMustChangePassword(true) }
        val estado = vm.uiState.value as AuthUiState.LoggedIn
        assertTrue(estado.user.mustChangePassword)
    }
}

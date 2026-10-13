package com.example.proyectwin.data.repository

import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.api.TokenProvider
import com.example.proyectwin.data.api.dto.ApprenticeDto
import com.example.proyectwin.data.api.dto.GeneralUserDto
import com.example.proyectwin.data.api.dto.LoginResponse
import com.example.proyectwin.data.api.dto.MessageResponse
import com.example.proyectwin.data.api.service.ApprenticesApi
import com.example.proyectwin.data.api.service.AuthApi
import com.example.proyectwin.data.api.service.UsersApi
import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.data.model.GeneralUser
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Contrato de autenticación (Fase 1 + Fase 2): orden del login, logout,
 * restauración de sesión, cuenta suspendida y cambio de contraseña.
 */
class AuthRepositoryImplTest {

    private val api = mockk<AuthApi>()
    private val usersApi = mockk<UsersApi>()
    private val apprenticesApi = mockk<ApprenticesApi>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val tokenProvider = mockk<TokenProvider>(relaxed = true)

    private val repo = AuthRepositoryImpl(api, usersApi, apprenticesApi, sessionManager, tokenProvider)

    private fun usuario(mustChange: Boolean = false) = GeneralUser(
        id = 5,
        name = "Ana Gómez",
        email = "ana@sena.edu.co",
        role = "aprendiz",
        username = "ana",
        mustChangePassword = mustChange,
    )

    private fun httpError(code: Int, body: String): HttpException = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
    )

    // ---------------------------------------------------------------- Login

    @Test
    fun login_guardaElTokenAntesDeEnriquecerLaCuenta() = runTest {
        coEvery { api.login(any()) } returns LoginResponse(
            user = GeneralUserDto(
                id = 5,
                nombre = "Ana",
                apellido = "Gómez",
                correo = "ana@sena.edu.co",
                rol = "aprendiz",
            ),
            token = "token-123",
            rol = "aprendiz",
        )
        coEvery { usersApi.obtener(5, "apprentice,instructor") } returns GeneralUserDto(
            id = 5,
            nombre = "Ana",
            apellido = "Gómez",
            correo = "ana@sena.edu.co",
            rol = "aprendiz",
            tipoDocumento = "CC",
            numeroDocumento = "1098",
        )
        coEvery { apprenticesApi.listar("classGroup") } returns listOf(
            ApprenticeDto(id = 1, idUsuario = 5, idClassGroup = 7),
        )

        val sesion = slot<GeneralUser>()
        coEvery { sessionManager.saveUser(capture(sesion)) } just Runs

        val resultado = repo.login("Ana", "secreta", recordarme = false)

        assertTrue(resultado.isSuccess)
        assertEquals(7, resultado.getOrThrow().fichaId)
        assertEquals("1098", resultado.getOrThrow().documentoIdentidad)
        assertEquals("token-123", sesion.captured.token)

        // Orden exacto: login -> token -> llamadas autenticadas -> sesión local.
        coVerifyOrder {
            api.login(any())
            tokenProvider.update("token-123")
            usersApi.obtener(5, "apprentice,instructor")
            sessionManager.saveUser(any())
        }
    }

    @Test
    fun login_fallido_noGuardaNada() = runTest {
        coEvery { api.login(any()) } throws IOException("sin red")

        val resultado = repo.login("Ana", "secreta")

        assertTrue(resultado.isFailure)
        assertTrue(resultado.exceptionOrNull() is ApiException.SinConexion)
        coVerify(exactly = 0) { sessionManager.saveUser(any()) }
        verify(exactly = 0) { tokenProvider.update(any()) }
    }

    @Test
    fun login_conCuentaSuspendida_marcaElError() = runTest {
        coEvery { api.login(any()) } throws httpError(
            403,
            """{"message":"Cuenta suspendida. Contacta al administrador."}""",
        )

        val resultado = repo.login("Ana", "secreta")

        val error = resultado.exceptionOrNull() as ApiException.Http
        assertEquals(403, error.status)
        assertTrue(error.cuentaSuspendida)
    }

    // ---------------------------------------------------------------- Logout

    @Test
    fun logout_revocaElTokenYLimpiaLaSesion() = runTest {
        coEvery { api.logout() } returns MessageResponse("Sesión cerrada.")

        val resultado = repo.logout()

        assertTrue(resultado.isSuccess)
        coVerify { api.logout() }
        verify { tokenProvider.update(null) }
        coVerify { sessionManager.clearSession() }
    }

    // ------------------------------------------------------- Restauración

    @Test
    fun refreshSession_ok_actualizaLaSesionConLaMarca() = runTest {
        everyCurrentUser(usuario())
        coEvery { api.me() } returns GeneralUserDto(
            id = 5,
            nombre = "Ana",
            apellido = "Gómez",
            correo = "ana@sena.edu.co",
            rol = "aprendiz",
            username = "ana",
            mustChangePassword = true,
        )
        coEvery { usersApi.obtener(5, "apprentice,instructor") } returns GeneralUserDto(
            id = 5,
            nombre = "Ana",
            apellido = "Gómez",
            correo = "ana@sena.edu.co",
            rol = "aprendiz",
            username = "ana",
            numeroDocumento = "1098",
        )
        coEvery { apprenticesApi.listar("classGroup") } returns emptyList()
        coEvery { sessionManager.getToken() } returns "token-9"

        val sesion = slot<GeneralUser>()
        coEvery { sessionManager.saveUser(capture(sesion)) } just Runs

        val resultado = repo.refreshSession()

        assertTrue(resultado.isSuccess)
        assertTrue(sesion.captured.mustChangePassword)
        assertEquals("1098", sesion.captured.documentoIdentidad)
        assertEquals("token-9", sesion.captured.token)
    }

    @Test
    fun refreshSession_401_propagaElStatus() = runTest {
        everyCurrentUser(usuario())
        coEvery { api.me() } throws httpError(401, """{"message":"Unauthenticated."}""")

        val resultado = repo.refreshSession()

        val error = resultado.exceptionOrNull() as ApiException.Http
        assertEquals(401, error.status)
    }

    @Test
    fun refreshSession_403deCuentaSuspendida_seDetecta() = runTest {
        everyCurrentUser(usuario())
        coEvery { api.me() } throws httpError(
            403,
            """{"message":"Cuenta suspendida. Contacta al administrador."}""",
        )

        val resultado = repo.refreshSession()

        val error = resultado.exceptionOrNull() as ApiException.Http
        assertEquals(403, error.status)
        assertTrue(error.cuentaSuspendida)
    }

    // ------------------------------------------------- Cambio de contraseña

    @Test
    fun changePassword_exitoso_limpiaLaMarca() = runTest {
        coEvery { api.changePassword(any()) } returns MessageResponse("Contraseña actualizada.")

        val resultado = repo.changePassword("actual", "nueva123", "nueva123")

        assertTrue(resultado.isSuccess)
        coVerify { sessionManager.setMustChangePassword(false) }
    }

    @Test
    fun changePassword_422_conservaLaMarca() = runTest {
        coEvery { api.changePassword(any()) } throws httpError(
            422,
            """{"message":"La contraseña actual no es correcta."}""",
        )

        val resultado = repo.changePassword("mala", "nueva123", "nueva123")

        val error = resultado.exceptionOrNull() as ApiException.Http
        assertEquals(422, error.status)
        coVerify(exactly = 0) { sessionManager.setMustChangePassword(any()) }
    }

    private fun everyCurrentUser(user: GeneralUser) {
        io.mockk.every { sessionManager.currentUser } returns flowOf(user)
    }
}

package com.example.proyectwin.data.api

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Cierre de sesión centralizado (fase 401): la capa de red emite los eventos
 * globales correctos y solo con las respuestas que lo justifican.
 */
class SafeApiCallTest {

    private fun httpError(code: Int, body: String): HttpException = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
    )

    @Test
    fun `un 401 emite el evento global de sesion expirada`() = runTest {
        var expiradas = 0
        // Colector unconfined: queda suscrito al instante y recibe el evento
        // en el mismo momento del emit (patrón estable para SharedFlow).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SessionEvents.expired.collect { expiradas++ }
        }

        val resultado = safeApiCall<Unit> {
            throw httpError(401, """{"message":"Unauthenticated."}""")
        }
        advanceUntilIdle()

        assertTrue(resultado.isFailure)
        assertEquals(401, (resultado.exceptionOrNull() as ApiException.Http).status)
        assertEquals(1, expiradas)
    }

    @Test
    fun `una cuenta suspendida 403 emite el evento de sesion expirada`() = runTest {
        var expiradas = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SessionEvents.expired.collect { expiradas++ }
        }

        val resultado = safeApiCall<Unit> {
            throw httpError(403, """{"message":"Cuenta suspendida. Contacta al administrador."}""")
        }
        advanceUntilIdle()

        val error = resultado.exceptionOrNull() as ApiException.Http
        assertTrue(error.cuentaSuspendida)
        assertEquals(1, expiradas)
    }

    @Test
    fun `un 403 de cambio obligatorio emite su evento y no el de expiracion`() = runTest {
        var expiradas = 0
        var cambiosObligatorios = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SessionEvents.expired.collect { expiradas++ }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SessionEvents.mustChangePassword.collect { cambiosObligatorios++ }
        }

        safeApiCall<Unit> {
            throw httpError(
                403,
                """{"message":"Debes cambiar tu contraseña.","must_change_password":true}""",
            )
        }
        advanceUntilIdle()

        assertEquals(0, expiradas)
        assertEquals(1, cambiosObligatorios)
    }

    @Test
    fun `un 403 de permisos normales no emite eventos de sesion`() = runTest {
        var eventos = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SessionEvents.expired.collect { eventos++ }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SessionEvents.mustChangePassword.collect { eventos++ }
        }

        val resultado = safeApiCall<Unit> {
            throw httpError(403, """{"message":"Solo puedes editar tus propias observaciones."}""")
        }
        advanceUntilIdle()

        val error = resultado.exceptionOrNull() as ApiException.Http
        assertEquals(0, eventos)
        assertEquals(403, error.status)
        assertEquals(false, error.cuentaSuspendida)
        assertEquals(false, error.mustChangePassword)
    }
}

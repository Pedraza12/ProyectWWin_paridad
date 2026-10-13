package com.example.proyectwin.data.repository

import com.example.proyectwin.data.api.TokenProvider
import com.example.proyectwin.data.api.dto.ChangeEmailRequest
import com.example.proyectwin.data.api.dto.ChangePasswordRequest
import com.example.proyectwin.data.api.dto.ForgotPasswordRequest
import com.example.proyectwin.data.api.dto.FotoUpdateRequest
import com.example.proyectwin.data.api.dto.GeneralUserDto
import com.example.proyectwin.data.api.dto.LoginRequest
import com.example.proyectwin.data.api.dto.ProfileUpdateRequest
import com.example.proyectwin.data.api.dto.ResetPasswordRequest
import com.example.proyectwin.data.api.service.ApprenticesApi
import com.example.proyectwin.data.api.service.AuthApi
import com.example.proyectwin.data.api.service.UsersApi
import com.example.proyectwin.data.api.safeApiCall
import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.data.mapper.toDomain
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.data.model.UserRole
import com.example.proyectwin.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val usersApi: UsersApi,
    private val apprenticesApi: ApprenticesApi,
    private val sessionManager: SessionManager,
    private val tokenProvider: TokenProvider,
) : AuthRepository {

    override val currentUser: Flow<GeneralUser?> = sessionManager.currentUser
    override val isLoggedIn: Flow<Boolean> = sessionManager.isLoggedIn

    override suspend fun login(username: String, password: String, recordarme: Boolean): Result<GeneralUser> {
        val credenciales = LoginRequest(
            username = username.trim().lowercase(),
            password = password,
            recordarme = recordarme,
        )
        val respuesta = safeApiCall { api.login(credenciales) }.getOrElse { return Result.failure(it) }

        // El token va primero: el enriquecimiento llama endpoints autenticados
        // (/general-users y /apprentices) y en un login reciente el interceptor
        // aún no tenía Bearer, por lo que la ficha/documento llegaban vacíos.
        tokenProvider.update(respuesta.token)

        // Enriquece con perfil/ficha; si falla, la sesión se arma con lo del login.
        val cuenta = enriquecerCuenta(respuesta.user.id) ?: respuesta.user.toDomain()
        val sesion = cuenta.copy(token = respuesta.token)
        sessionManager.saveUser(sesion)
        return Result.success(sesion)
    }

    override suspend fun logout(): Result<Unit> {
        runCatching { api.logout() } // best effort: aunque falle, se limpia local
        tokenProvider.update(null)
        return runCatching { sessionManager.clearSession() }
    }

    override suspend fun refreshSession(): Result<GeneralUser> {
        val actual = sessionManager.currentUser.first()
            ?: return Result.failure(IllegalStateException("No hay sesión activa."))

        val me = safeApiCall { api.me() }.getOrElse { return Result.failure(it) }

        // `/auth/me` no expone documento ni el username completo; el detalle de
        // la propia cuenta sí (show hace visibles los campos de credenciales).
        // Sin esto, el perfil quedaba con documento "No registrado" al reiniciar.
        val detalle = runCatching {
            usersApi.obtener(actual.id, INCLUDE_CUENTA).toDomain()
        }.getOrNull()

        var cuenta = (detalle ?: me.toDomain()).copy(
            token = sessionManager.getToken(),
            mustChangePassword = me.mustChangePassword,
            username = me.username.ifBlank { detalle?.username.orEmpty() },
        )

        if (cuenta.role == UserRole.APRENDIZ.value) {
            val fichaId = fichaDelAprendiz(cuenta.id)
            if (fichaId != null) cuenta = cuenta.copy(fichaId = fichaId)
            else if (actual.fichaId != null) sessionManager.clearFicha()
        }

        sessionManager.saveUser(cuenta)
        return Result.success(cuenta)
    }

    override suspend fun updateProfile(nombre: String, apellido: String): Result<GeneralUser> {
        val actual = sessionManager.currentUser.first()
            ?: return Result.failure(IllegalStateException("No hay sesión activa."))
        return safeApiCall {
            usersApi.actualizarPerfil(actual.id, ProfileUpdateRequest(nombre = nombre, apellido = apellido))
        }.mapCatching { dto ->
            val cuenta = dto.mergeCon(actual, sessionManager.getToken())
            sessionManager.saveUser(cuenta)
            cuenta
        }
    }

    override suspend fun updateFoto(fotoUrl: String?): Result<GeneralUser> {
        val actual = sessionManager.currentUser.first()
            ?: return Result.failure(IllegalStateException("No hay sesión activa."))
        return safeApiCall {
            usersApi.actualizarFoto(actual.id, FotoUpdateRequest(fotoUrl))
        }.mapCatching { dto ->
            val cuenta = dto.mergeCon(actual, sessionManager.getToken())
            sessionManager.saveUser(cuenta)
            sessionManager.updateFoto(fotoUrl)
            cuenta
        }
    }

    /**
     * `PUT /general-users` responde el usuario SIN los campos de credenciales
     * (van en `$hidden`), por lo que se conservan los de la sesión: de lo
     * contrario el documento/username desaparecerían del perfil tras guardar.
     */
    private fun GeneralUserDto.mergeCon(actual: GeneralUser, token: String?): GeneralUser {
        val base = toDomain()
        return base.copy(
            token = token,
            fichaId = actual.fichaId,
            username = base.username.ifBlank { actual.username },
            documentoIdentidad = base.documentoIdentidad ?: actual.documentoIdentidad,
            tipoDocumento = base.tipoDocumento ?: actual.tipoDocumento,
            credencialesEnviadasEn = base.credencialesEnviadasEn ?: actual.credencialesEnviadasEn,
            credencialesError = base.credencialesError ?: actual.credencialesError,
        )
    }

    override suspend fun changePassword(
        passwordActual: String,
        password: String,
        passwordConfirmation: String,
    ): Result<Unit> = safeApiCall {
        api.changePassword(
            ChangePasswordRequest(
                passwordActual = passwordActual,
                password = password,
                passwordConfirmation = passwordConfirmation,
            ),
        )
    }.map {
        // La contraseña definitiva reemplaza la temporal: el middleware deja de bloquear.
        sessionManager.setMustChangePassword(false)
    }

    override suspend fun changeEmail(correo: String, passwordActual: String): Result<Unit> =
        safeApiCall { api.changeEmail(ChangeEmailRequest(correo = correo, passwordActual = passwordActual)) }
            .mapCatching { respuesta ->
                val actual = sessionManager.currentUser.first()
                if (actual != null) {
                    sessionManager.saveUser(actual.copy(email = respuesta.correo.ifEmpty { correo }))
                }
            }

    override suspend fun forgotPassword(correo: String): Result<String> =
        safeApiCall { api.forgotPassword(ForgotPasswordRequest(correo = correo.trim().lowercase())) }
            .map { it.message.ifEmpty { it.resetUrl ?: "" } }

    override suspend fun resetPassword(
        token: String,
        correo: String,
        password: String,
        passwordConfirmation: String,
    ): Result<Unit> = safeApiCall {
        api.resetPassword(
            ResetPasswordRequest(
                token = token,
                correo = correo,
                password = password,
                passwordConfirmation = passwordConfirmation,
            ),
        )
    }.map {}

    // ---------------------------------------------------------------- Internos

    /** Cuenta completa (perfil + ficha del aprendiz). Silencioso si falla. */
    private suspend fun enriquecerCuenta(idUsuario: Int): GeneralUser? = runCatching {
        val base = usersApi.obtener(idUsuario, INCLUDE_CUENTA).toDomain()
        base.copy(fichaId = fichaDelAprendiz(idUsuario) ?: base.fichaId)
    }.getOrNull()

    /** Ficha actual del aprendiz (la relación `apprentice.classGroup` no es incluible). */
    private suspend fun fichaDelAprendiz(idUsuario: Int): Int? = runCatching {
        apprenticesApi.listar("classGroup")
            .firstOrNull { it.idUsuario == idUsuario }
            ?.idClassGroup
    }.getOrNull()

    companion object {
        private const val INCLUDE_CUENTA = "apprentice,instructor"
    }
}

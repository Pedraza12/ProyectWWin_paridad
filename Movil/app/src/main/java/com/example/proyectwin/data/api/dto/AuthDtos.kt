package com.example.proyectwin.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    /**
     * Solo aplica a la sesión por cookie del SPA (remember_token). En móvil el
     * acceso es por token Bearer y su vigencia la fija SANCTUM_EXPIRATION:
     * el campo se conserva por contrato, pero no cambia nada (se omite).
     */
    val recordarme: Boolean = false,
)

@Serializable
data class LoginResponse(
    val user: GeneralUserDto,
    val token: String? = null,
    val rol: String,
)

@Serializable
data class ForgotPasswordRequest(val correo: String)

@Serializable
data class ForgotPasswordResponse(
    val message: String = "",
    @SerialName("reset_url") val resetUrl: String? = null,
)

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val correo: String,
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("password_actual") val passwordActual: String,
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
)

@Serializable
data class ChangeEmailRequest(
    val correo: String,
    @SerialName("password_actual") val passwordActual: String,
)

@Serializable
data class ChangeEmailResponse(
    val message: String = "",
    val correo: String = "",
)

@Serializable
data class MessageResponse(val message: String = "")

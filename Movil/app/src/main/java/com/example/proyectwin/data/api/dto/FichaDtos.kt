package com.example.proyectwin.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ficha de formación (`class_groups`). */
@Serializable
data class ClassGroupDto(
    val id: Int = 0,
    val codigo: String = "",
    val numero: String? = null,
    val nombre: String = "",
    val estado: String = "activo",
    @SerialName("id_programa") val idPrograma: Int = 0,
    @SerialName("id_instructor") val idInstructor: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val program: TrainingProgramDto? = null,
    val instructor: InstructorDto? = null,
    val apprentices: List<ApprenticeDto>? = null,
    @SerialName("apprentices_count") val apprenticesCount: Int? = null,
)

@Serializable
data class ClassGroupCreateRequest(
    /** El backend exige `numero` y genera el `codigo` de unión por su cuenta. */
    val numero: String,
    val nombre: String,
    val estado: String,
    @SerialName("id_programa") val idPrograma: Int,
    @SerialName("id_instructor") val idInstructor: Int,
    /** Usuarios (`general_users.id`) a matricular; el backend sincroniza el roster. */
    val aprendices: List<Int>? = null,
)

/** PUT /class-groups: `numero` es obligatorio; `codigo` es inmutable y el backend lo descarta. */
@Serializable
data class ClassGroupUpdateRequest(
    val numero: String,
    val nombre: String,
    val estado: String,
    @SerialName("id_programa") val idPrograma: Int,
    @SerialName("id_instructor") val idInstructor: Int,
    /** Roster completo (usuarios); los ausentes quedan desvinculados. */
    val aprendices: List<Int>? = null,
)

/** `DELETE /class-groups/{id}`: `{accion: "anulada"|"eliminada", ficha}`. */
@Serializable
data class ClassGroupDeleteResponse(
    val accion: String = "",
    val ficha: ClassGroupDto? = null,
)

/**
 * Alta/asociación de aprendiz en una ficha. Con [usuarioId] asocia una cuenta
 * existente; sin él crea la cuenta (documento y correo obligatorios).
 */
@Serializable
data class AgregarAprendizRequest(
    @SerialName("usuario_id") val usuarioId: Int? = null,
    val nombre: String? = null,
    val apellido: String? = null,
    @SerialName("tipo_documento") val tipoDocumento: String? = null,
    @SerialName("numero_documento") val numeroDocumento: String? = null,
    val correo: String? = null,
)

@Serializable
data class AgregarAprendizResponse(
    val usuario: GeneralUserDto? = null,
    val aprendiz: ApprenticeDto? = null,
    val creada: Boolean = false,
    @SerialName("credenciales_enviadas") val credencialesEnviadas: Boolean = false,
    val credenciales: CredencialesDto? = null,
)

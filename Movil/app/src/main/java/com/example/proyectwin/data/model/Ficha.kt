package com.example.proyectwin.data.model

import kotlinx.serialization.Serializable

/** Resultado de eliminar una ficha: se anula si tenía dependencias. */
data class ResultadoFichaEliminada(
    val accion: String,
    val ficha: Ficha? = null,
) {
    val anulada: Boolean get() = accion == "anulada"
}

@Serializable
data class Ficha(
    val id: Int,
    val codigo: String,
    val programa: String,
    val estado: String = ClassGroupStatus.ACTIVO.value,
    val instructorId: Int? = null,
    /** `general_users.id` del instructor: necesario para su perfil público. */
    val instructorUsuarioId: Int? = null,
    val instructorName: String? = null,
    val createdAt: String? = null,
    val estudiantes: List<GeneralUser> = emptyList(),
    val nombre: String? = null,
    val numero: String? = null,
    val idPrograma: Int? = null
) {
    val classGroupStatus: ClassGroupStatus get() = ClassGroupStatus.fromValue(estado)
    val statusDisplay: String get() = when (classGroupStatus) {
        ClassGroupStatus.ACTIVO -> "Activo"
        ClassGroupStatus.FINALIZADO -> "Finalizado"
        ClassGroupStatus.ANULADA -> "Anulada"
    }

}

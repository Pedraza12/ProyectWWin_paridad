package com.example.proyectwin.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ------------------------------------------------------------ Catálogos

@Serializable
data class KnowledgeNetworkDto(
    val id: Int = 0,
    val nombre: String = "",
    val trainingPrograms: List<TrainingProgramDto>? = null,
)

@Serializable
data class KnowledgeNetworkRequest(val nombre: String)

@Serializable
data class TrainingProgramDto(
    val id: Int = 0,
    val nombre: String = "",
    val nivel: String? = null,
    @SerialName("knowledge_network_id") val knowledgeNetworkId: Int = 0,
    val knowledgeNetwork: KnowledgeNetworkDto? = null,
)

@Serializable
data class TrainingProgramRequest(
    val nombre: String,
    /** El backend lo exige (`required`); la UI no debe omitirlo ni enviarlo null. */
    val nivel: String,
    @SerialName("knowledge_network_id") val knowledgeNetworkId: Int,
)

// ------------------------------------------------------------ Motor de similitud

@Serializable
data class MotorConfigDto(
    val id: Int = 0,
    val umbral: Double = 0.30,
    val meses: Int = 12,
)

@Serializable
data class MotorConfigUpdateRequest(
    val umbral: Double,
    val meses: Int,
)

@Serializable
data class PublicResumenDto(
    val umbral: Double = 0.0,
    val meses: Int = 0,
    @SerialName("total_propuestas") val totalPropuestas: Int = 0,
    @SerialName("total_programas") val totalProgramas: Int = 0,
)

@Serializable
data class DemoSimilitudRequest(val texto: String)

@Serializable
data class DemoSimilitudResponse(
    val umbral: Double = 0.0,
    val meses: Int = 0,
    val total: Int = 0,
    val sobre: Int = 0,
    val coincidencias: List<CoincidenciaDemo> = emptyList(),
)

@Serializable
data class CoincidenciaDemo(
    val id: Int = 0,
    val titulo: String = "",
    val porcentaje: Double = 0.0,
)

// ------------------------------------------------------------ Bitácora

@Serializable
data class AuditLogDto(
    val id: Int = 0,
    @SerialName("id_usuario") val idUsuario: Int? = null,
    val accion: String = "",
    val entidad: String? = null,
    @SerialName("entidad_id") val entidadId: Int? = null,
    val detalle: JsonObject? = null,
    val ip: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val user: GeneralUserDto? = null,
)

package com.example.proyectwin.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BugReport(
    val id: Int,
    val titulo: String,
    val numeroFicha: String? = null,
    val motivo: String? = null,
    val descripcion: String,
    val tipo: String = BugReportType.OTRO.value,
    val estado: String = BugReportStatus.PENDIENTE.value,
    val respuesta: String? = null,
    val reporterId: Int? = null,
    val reporterName: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val fecha: String? = null
) {
    val bugType: BugReportType get() = BugReportType.fromValue(tipo)
    val bugStatus: BugReportStatus get() = BugReportStatus.fromValue(estado)
    val typeDisplay: String get() = bugType.typeDisplay
    val statusDisplay: String get() = when (bugStatus) {
        BugReportStatus.PENDIENTE -> "Pendiente"
        BugReportStatus.EN_REVISION -> "En Revisión"
        BugReportStatus.RESUELTO -> "Resuelto"
        BugReportStatus.CERRADO -> "Cerrado"
        BugReportStatus.RECHAZADO -> "Rechazado"
    }
}

val BugReportType.typeDisplay: String get() = when (this) {
    BugReportType.BUG_UI -> "Bug de UI"
    BugReportType.ERROR_DATOS -> "Error de datos"
    BugReportType.RENDIMIENTO -> "Rendimiento"
    BugReportType.SEGURIDAD -> "Seguridad"
    BugReportType.SISTEMA -> "Sistema"
    BugReportType.PROYECTO -> "Proyecto"
    BugReportType.DATOS -> "Datos"
    BugReportType.OTRO -> "Otro"
}

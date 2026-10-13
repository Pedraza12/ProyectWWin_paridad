package com.example.proyectwin.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EnumsTest {

    @Test
    fun projectStatus_fromValue_returnsCorrect() {
        assertEquals(ProjectStatus.BORRADOR, ProjectStatus.fromValue("borrador"))
        assertEquals(ProjectStatus.PENDIENTE, ProjectStatus.fromValue("pendiente"))
        assertEquals(ProjectStatus.APROBADO, ProjectStatus.fromValue("aprobado"))
        assertEquals(ProjectStatus.RECHAZADO, ProjectStatus.fromValue("rechazado"))
    }

    @Test
    fun projectStatus_fromValue_invalid_returnsDefault() {
        assertEquals(ProjectStatus.BORRADOR, ProjectStatus.fromValue("invalido"))
    }

    @Test
    fun userRole_fromValue_returnsCorrect() {
        assertEquals(UserRole.INSTRUCTOR, UserRole.fromValue("instructor"))
        assertEquals(UserRole.APRENDIZ, UserRole.fromValue("aprendiz"))
        assertEquals(UserRole.ADMINISTRADOR, UserRole.fromValue("admin"))
        assertEquals(UserRole.ADMINISTRADOR, UserRole.fromValue("administrador"))
    }

    @Test
    fun notificationType_fromValue_returnsCorrect() {
        assertEquals(NotificationType.SIMILITUD, NotificationType.fromValue("similitud"))
        assertEquals(NotificationType.OBSERVACION, NotificationType.fromValue("observacion"))
        assertEquals(NotificationType.REVISION, NotificationType.fromValue("revision"))
        assertEquals(NotificationType.MENSAJE, NotificationType.fromValue("mensaje"))
        assertEquals(NotificationType.SISTEMA, NotificationType.fromValue("sistema"))
        // Valores que el backend no usa (legado) o desconocidos: genérico real.
        assertEquals(NotificationType.SISTEMA, NotificationType.fromValue("info"))
        assertEquals(NotificationType.SISTEMA, NotificationType.fromValue("desconocido"))
    }

    @Test
    fun bugReportStatus_fromValue_returnsCorrect() {
        assertEquals(BugReportStatus.PENDIENTE, BugReportStatus.fromValue("pendiente"))
        assertEquals(BugReportStatus.EN_REVISION, BugReportStatus.fromValue("en_revision"))
        assertEquals(BugReportStatus.RESUELTO, BugReportStatus.fromValue("resuelto"))
        assertEquals(BugReportStatus.CERRADO, BugReportStatus.fromValue("cerrado"))
    }

    @Test
    fun bugReportType_fromValue_returnsCorrect() {
        assertEquals(BugReportType.BUG_UI, BugReportType.fromValue("bug_ui"))
        assertEquals(BugReportType.ERROR_DATOS, BugReportType.fromValue("error_datos"))
        assertEquals(BugReportType.RENDIMIENTO, BugReportType.fromValue("rendimiento"))
        assertEquals(BugReportType.SEGURIDAD, BugReportType.fromValue("seguridad"))
        assertEquals(BugReportType.OTRO, BugReportType.fromValue("otro"))
    }

    @Test
    fun classGroupStatus_fromValue_returnsCorrect() {
        assertEquals(ClassGroupStatus.ACTIVO, ClassGroupStatus.fromValue("activo"))
        assertEquals(ClassGroupStatus.FINALIZADO, ClassGroupStatus.fromValue("finalizado"))
        assertEquals(ClassGroupStatus.ANULADA, ClassGroupStatus.fromValue("anulada"))
    }
}

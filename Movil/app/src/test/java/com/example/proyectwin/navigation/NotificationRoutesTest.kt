package com.example.proyectwin.navigation

import com.example.proyectwin.data.model.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Resolución del `enlace` de una notificación a la ruta del rol (Fase 4). */
class NotificationRoutesTest {

    private val similitudes = "instructor/similitudes?proyectoId={proyectoId}"
    private val detalle = "instructor_detail/{id}"
    private val ficha = "instructor_ficha_detail/{fichaId}"
    private val reporte = "admin/bug/{bugId}"

    private fun resolver(
        tipo: NotificationType = NotificationType.SISTEMA,
        modulo: String? = null,
        id: Int? = null,
        similitudesRoute: String = similitudes,
        fichaRoute: String = ficha,
        reporteRoute: String = reporte,
    ) = NotificationRoutes.resolverDestino(
        tipo = tipo,
        enlaceModulo = modulo,
        enlaceId = id,
        similitudesRoute = similitudesRoute,
        detailRoute = detalle,
        fichaRoute = fichaRoute,
        reporteRoute = reporteRoute,
    )

    @Test
    fun similitudDeProyecto_conservaElFiltroDelProyecto() {
        assertEquals(
            "instructor/similitudes?proyectoId=12",
            resolver(NotificationType.SIMILITUD, "proyecto", 12),
        )
    }

    @Test
    fun proyectoNormal_vaAlDetalleDelRol() {
        assertEquals("instructor_detail/7", resolver(NotificationType.REVISION, "proyecto", 7))
    }

    @Test
    fun similitudSinRutaDeSimilitudes_caeAlDetalle() {
        assertEquals(
            "instructor_detail/7",
            resolver(NotificationType.SIMILITUD, "proyecto", 7, similitudesRoute = ""),
        )
    }

    @Test
    fun ficha_conYsinPlaceholder() {
        assertEquals("instructor_ficha_detail/3", resolver(NotificationType.MENSAJE, "ficha", 3))
        assertEquals("admin/fichas", resolver(NotificationType.MENSAJE, "ficha", 3, fichaRoute = "admin/fichas"))
    }

    @Test
    fun reporte_soloSiElRolTieneRuta() {
        assertEquals("admin/bug/9", resolver(NotificationType.SISTEMA, "reporte", 9))
        assertNull(resolver(NotificationType.SISTEMA, "reporte", 9, reporteRoute = ""))
    }

    @Test
    fun enlacesInvalidos_oAntiguos_noNavegan() {
        assertNull(resolver(NotificationType.SISTEMA, null, 1))
        assertNull(resolver(NotificationType.SISTEMA, "proyecto", null))
        assertNull(resolver(NotificationType.SISTEMA, "modulo_desconocido", 1))
    }
}

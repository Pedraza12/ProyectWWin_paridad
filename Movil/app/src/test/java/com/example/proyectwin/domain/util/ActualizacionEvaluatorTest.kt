package com.example.proyectwin.domain.util

import com.example.proyectwin.data.model.AppVersionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Política de actualización: comparación numérica y enlace seguro. */
class ActualizacionEvaluatorTest {

    private fun info(
        version: String,
        minima: String = "1.0.0",
        enforce: Boolean = false,
        url: String? = "https://descargas.example.com/proyectwin.apk",
    ) = AppVersionInfo(
        versionPublicada = version,
        versionMinima = minima,
        obligatoria = enforce,
        urlDescarga = url,
        notas = "Mejoras",
    )

    @Test
    fun `hay actualizacion cuando la publicada es mayor`() {
        val r = evaluarActualizacion(info("1.4.0"), "1.3.0")
        assertTrue(r.valida)
        assertTrue(r.hayActualizacion)
        assertFalse(r.obligatoria)
        assertEquals("https://descargas.example.com/proyectwin.apk", r.urlDescarga)
        assertFalse(r.sinUrlDescarga)
    }

    @Test
    fun `no hay aviso si las versiones son iguales o la instalada es mayor`() {
        assertFalse(evaluarActualizacion(info("1.3.0"), "1.3.0").hayActualizacion)
        assertFalse(evaluarActualizacion(info("1.2.9"), "1.3.0").hayActualizacion)
    }

    @Test
    fun `compara numericamente y no alfabeticamente`() {
        // 1.10.0 > 1.9.0 (una comparación alfabética daría lo contrario).
        assertTrue(evaluarActualizacion(info("1.10.0"), "1.9.0").hayActualizacion)
    }

    @Test
    fun `una version nueva sin URL https se informa como no accionable`() {
        val r = evaluarActualizacion(info("1.4.0", url = ""), "1.3.0")
        assertTrue(r.hayActualizacion)
        assertTrue(r.sinUrlDescarga)
        assertNull(r.urlDescarga)

        val http = evaluarActualizacion(info("1.4.0", url = "http://inseguro/x.apk"), "1.3.0")
        assertTrue(http.sinUrlDescarga)
    }

    @Test
    fun `solo es obligatoria si el servidor lo pide y la instalada es menor que la minima`() {
        val obligatoria = evaluarActualizacion(info("1.4.0", minima = "1.2.0", enforce = true), "1.1.0")
        assertTrue(obligatoria.obligatoria)

        val yaSuperaMinima = evaluarActualizacion(info("1.4.0", minima = "1.2.0", enforce = true), "1.3.0")
        assertFalse(yaSuperaMinima.obligatoria)

        val sinPolitica = evaluarActualizacion(info("1.4.0", minima = "1.2.0", enforce = false), "1.1.0")
        assertFalse(sinPolitica.obligatoria)
    }

    @Test
    fun `una version publicada invalida no genera estado de actualizacion`() {
        val r = evaluarActualizacion(info(""), "1.3.0")
        assertFalse(r.valida)
        assertFalse(r.hayActualizacion)
        assertFalse(r.obligatoria)
    }
}

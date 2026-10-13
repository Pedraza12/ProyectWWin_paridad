package com.example.proyectwin.ui.viewmodel

import com.example.proyectwin.data.model.Comment
import com.example.proyectwin.data.model.Project
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permisos de edición/eliminación de observaciones (Fase 5, M1): espejo de la
 * regla del backend (autor o admin; el autor además con ficha escribible).
 */
class ProjectDetailDataTest {

    private fun comentario(de: Int) = Comment(id = 1, texto = "Hola", proyectoId = 5, usuarioId = de)

    private fun data(
        esAdmin: Boolean = false,
        usuarioActualId: Int? = 10,
        esCreador: Boolean = false,
        fichaActiva: Boolean = true,
    ) = ProjectDetailData(
        project = Project(id = 5, title = "Propuesta", estado = "pendiente"),
        esAdmin = esAdmin,
        esCreador = esCreador,
        fichaActiva = fichaActiva,
        usuarioActualId = usuarioActualId,
    )

    @Test
    fun `el admin puede gestionar cualquier observacion`() {
        assertTrue(data(esAdmin = true).puedeGestionarComentario(comentario(de = 99)))
    }

    @Test
    fun `el autor con ficha activa puede gestionar la suya`() {
        assertTrue(data(esCreador = true).puedeGestionarComentario(comentario(de = 10)))
    }

    @Test
    fun `el autor fuera de la ficha no puede gestionar`() {
        assertFalse(
            data(esCreador = true, fichaActiva = false).puedeGestionarComentario(comentario(de = 10)),
        )
    }

    @Test
    fun `otro usuario no puede gestionar observaciones ajenas`() {
        assertFalse(data(esCreador = true).puedeGestionarComentario(comentario(de = 77)))
    }
}

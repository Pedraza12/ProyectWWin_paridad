package com.example.proyectwin.ui.util

import com.example.proyectwin.data.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Representación del hilo de observaciones con anidamiento (fase M10). */
class HiloComentariosTest {

    private fun comentario(id: Int, padre: Int? = null) = Comment(
        id = id,
        texto = "Observación $id",
        proyectoId = 1,
        usuarioId = 1,
        respuestaA = padre,
    )

    @Test
    fun `un hilo de un nivel mantiene raiz y respuestas en orden`() {
        val hilo = aplanarHiloComentarios(
            listOf(comentario(1), comentario(2, padre = 1), comentario(3, padre = 1)),
        )

        assertEquals(listOf(1 to 0, 2 to 1, 3 to 1), hilo.map { it.comentario.id to it.profundidad })
    }

    @Test
    fun `las respuestas anidadas a mas de un nivel se aplanan en profundidad`() {
        val hilo = aplanarHiloComentarios(
            listOf(
                comentario(1),
                comentario(2, padre = 1),
                comentario(3, padre = 2),
                comentario(4, padre = 3),
            ),
        )

        assertEquals(listOf(1 to 0, 2 to 1, 3 to 2, 4 to 3), hilo.map { it.comentario.id to it.profundidad })
    }

    @Test
    fun `un comentario cuyo padre no esta visible se trata como raiz`() {
        val hilo = aplanarHiloComentarios(listOf(comentario(5, padre = 99)))

        assertEquals(listOf(5 to 0), hilo.map { it.comentario.id to it.profundidad })
    }

    @Test
    fun `un ciclo no se repite y no cuelga`() {
        val hilo = aplanarHiloComentarios(
            listOf(comentario(1, padre = 2), comentario(2, padre = 1)),
        )

        assertEquals(2, hilo.size)
        assertEquals(listOf(1, 2), hilo.map { it.comentario.id }.sorted())
        assertTrue(hilo.all { it.profundidad >= 0 })
    }

    @Test
    fun `no se duplican comentarios con el mismo id`() {
        val hilo = aplanarHiloComentarios(listOf(comentario(1), comentario(1)))

        assertEquals(1, hilo.size)
    }

    @Test
    fun `los hermanos conservan el orden del listado`() {
        val hilo = aplanarHiloComentarios(
            listOf(comentario(1), comentario(3, padre = 1), comentario(2, padre = 1)),
        )

        assertEquals(listOf(1, 3, 2), hilo.map { it.comentario.id })
    }

    @Test
    fun `hilo vacio devuelve lista vacia`() {
        assertTrue(aplanarHiloComentarios(emptyList()).isEmpty())
    }

    @Test
    fun `profundidades mayores al tope visual se conservan en el modelo`() {
        // La sangría de la UI se acota a 5 niveles, pero el modelo debe
        // conservar la profundidad real de cadenas más largas.
        val cadena = (1..8).map { id -> comentario(id, padre = if (id == 1) null else id - 1) }

        val hilo = aplanarHiloComentarios(cadena)

        assertEquals(8, hilo.size)
        assertEquals(0, hilo.first().profundidad)
        assertEquals(7, hilo.last().profundidad)
    }
}

package com.example.proyectwin.ui.util

import com.example.proyectwin.data.model.Comment

/** Observación lista para pintar dentro del hilo, con su profundidad. */
data class ComentarioEnHilo(
    val comentario: Comment,
    val profundidad: Int,
)

/**
 * Aplana el hilo de observaciones en orden de lectura (raíz y luego sus
 * respuestas en profundidad). El backend admite responder a cualquier
 * observación del mismo proyecto, por lo que el árbol puede tener más de un
 * nivel:
 *
 * - un comentario cuyo padre no está en la lista visible se trata como raíz
 *   (no se pierde);
 * - un ciclo (dato corrupto) se corta: cada id se visita una sola vez;
 * - no se duplican comentarios;
 * - se conserva el orden del listado para los hermanos.
 *
 * La recorrida es iterativa con pila: no hay recursión que pueda desbordar
 * la pila con hilos muy profundos.
 */
fun aplanarHiloComentarios(comentarios: List<Comment>): List<ComentarioEnHilo> {
    val porId = comentarios.associateBy { it.id }
    val hijos = HashMap<Int, MutableList<Comment>>()
    val raices = mutableListOf<Comment>()

    comentarios.forEach { comentario ->
        val padre = comentario.respuestaA?.takeIf { porId.containsKey(it) }
        if (padre == null) raices += comentario
        else hijos.getOrPut(padre) { mutableListOf() }.add(comentario)
    }

    val resultado = ArrayList<ComentarioEnHilo>(comentarios.size)
    val visitados = HashSet<Int>(comentarios.size)

    fun recorrerDesde(inicio: Comment) {
        val pila = ArrayDeque<ComentarioEnHilo>()
        pila.addLast(ComentarioEnHilo(inicio, 0))
        while (pila.isNotEmpty()) {
            val actual = pila.removeLast()
            if (!visitados.add(actual.comentario.id)) continue // ciclo o duplicado
            resultado += actual
            // asReversed + pila (LIFO) mantiene el orden original del listado.
            hijos[actual.comentario.id].orEmpty().asReversed().forEach { hijo ->
                pila.addLast(ComentarioEnHilo(hijo, actual.profundidad + 1))
            }
        }
    }

    raices.forEach { recorrerDesde(it) }
    // Bloque cíclico sin raíz (no alcanzable desde el recorrido anterior):
    // se lista una vez como raíz para no perder las observaciones.
    comentarios.forEach { comentario ->
        if (!visitados.contains(comentario.id)) recorrerDesde(comentario)
    }

    return resultado
}

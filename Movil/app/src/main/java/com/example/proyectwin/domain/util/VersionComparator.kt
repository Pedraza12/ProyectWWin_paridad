package com.example.proyectwin.domain.util

/**
 * Comparación numérica de versiones tipo "MAJOR.MINOR.PATCH" (1.10.0 > 1.9.0,
 * 2.0.0 > 1.99.99). Los segmentos se comparan entero a entero; un sufijo no
 * numérico (p. ej. "0-beta") se ignora desde su primer carácter no dígito.
 */
object VersionComparator {

    /** true si el texto tiene al menos un dígito (versión interpretable). */
    fun esValida(version: String?): Boolean =
        version?.trim()?.any(Char::isDigit) == true

    /** -1 si [a] es anterior a [b], 0 si equivalen, 1 si [a] es posterior. */
    fun comparar(a: String?, b: String?): Int {
        val pa = segmentos(a)
        val pb = segmentos(b)
        val largo = maxOf(pa.size, pb.size)
        for (i in 0 until largo) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    private fun segmentos(version: String?): List<Int> =
        version?.trim().orEmpty()
            .split('.')
            .map { segmento -> segmento.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}

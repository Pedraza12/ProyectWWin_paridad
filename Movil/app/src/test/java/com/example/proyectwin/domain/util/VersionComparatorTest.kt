package com.example.proyectwin.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comparación numérica de versiones: nunca alfabética (1.10.0 > 1.9.0) y
 * tolerante a formatos cortos, sufijos y textos inválidos.
 */
class VersionComparatorTest {

    @Test
    fun `compara segmentos numericamente y no alfabeticamente`() {
        assertEquals(-1, VersionComparator.comparar("1.9.0", "1.10.0"))
        assertEquals(1, VersionComparator.comparar("1.10.0", "1.9.0"))
        assertEquals(1, VersionComparator.comparar("2.0.0", "1.99.99"))
        assertEquals(-1, VersionComparator.comparar("1.0.0", "1.0.1"))
    }

    @Test
    fun `versiones equivalentes dan cero`() {
        assertEquals(0, VersionComparator.comparar("1.0.0", "1.0.0"))
        assertEquals(0, VersionComparator.comparar("1.0", "1.0.0"))
        assertEquals(0, VersionComparator.comparar("1.0.0", "1.0"))
        assertEquals(0, VersionComparator.comparar(" 1.2.3 ", "1.2.3"))
    }

    @Test
    fun `ignora sufijos no numericos desde el primer caracter no digito`() {
        assertEquals(0, VersionComparator.comparar("1.1.0-beta", "1.1.0"))
        assertEquals(-1, VersionComparator.comparar("1.1.0-beta", "1.1.1"))
    }

    @Test
    fun `textos invalidos o nulos cuentan como cero`() {
        assertEquals(-1, VersionComparator.comparar(null, "1.0"))
        assertEquals(0, VersionComparator.comparar("", "0"))
        assertFalse(VersionComparator.esValida(null))
        assertFalse(VersionComparator.esValida(""))
        assertFalse(VersionComparator.esValida("abc"))
        assertFalse(VersionComparator.esValida("   "))
        assertTrue(VersionComparator.esValida("1.0"))
        assertTrue(VersionComparator.esValida("v2.1.0"))
    }
}

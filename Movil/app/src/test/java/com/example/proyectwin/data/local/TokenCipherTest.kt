package com.example.proyectwin.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Formato del token cifrado (Fase 6): distingue los blobs `v1` del token
 * legado en texto plano y decide cuándo migrar. El cifrado real (Keystore)
 * y el descifrado requieren un dispositivo/emulador Android.
 */
class TokenCipherTest {

    @Test
    fun `un blob v1 con iv y cifrado se reconoce como cifrado`() {
        assertTrue(TokenCipher.esBlobCifrado("v1:YWJjMTIz:ZGVmNDU2"))
    }

    @Test
    fun `un token legado en texto plano requiere migracion`() {
        assertTrue(TokenCipher.requiereMigracion("12|abcdef123456"))
    }

    @Test
    fun `un blob cifrado no requiere migracion`() {
        assertFalse(TokenCipher.requiereMigracion("v1:YWJj:ZGVm"))
    }

    @Test
    fun `sin token no hay nada que migrar`() {
        assertFalse(TokenCipher.requiereMigracion(null))
    }

    @Test
    fun `formatos invalidos no se confunden con un blob cifrado`() {
        assertFalse(TokenCipher.esBlobCifrado("v1:soloiv"))
        assertFalse(TokenCipher.esBlobCifrado("v1:a:b:c"))
        assertFalse(TokenCipher.esBlobCifrado("abc:def"))
        assertFalse(TokenCipher.esBlobCifrado(""))
    }
}

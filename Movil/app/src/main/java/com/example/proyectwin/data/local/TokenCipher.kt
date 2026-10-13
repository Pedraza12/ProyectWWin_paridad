package com.example.proyectwin.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifra el token de sesión con AES-256-GCM y una clave del Android Keystore
 * (la clave nunca sale del dispositivo ni se guarda junto al token).
 *
 * Formato del blob: `v1:<iv base64>:<cifrado base64>`.
 *
 * Los fallos del Keystore (clave invalidada, dato corrupto, etc.) devuelven
 * `null`: el llamador decide (no se persiste nada y la sesión se reconstruye
 * con un nuevo login, sin crashes).
 */
@Singleton
class TokenCipher @Inject constructor() {

    private fun clave(): SecretKey {
        val almacen = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (almacen.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generador.generateKey()
    }

    /** Devuelve el blob cifrado o null si el Keystore no está disponible. */
    fun cifrar(texto: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, clave())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val cifrado = Base64.encodeToString(cipher.doFinal(texto.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        "$MARCA$iv:$cifrado"
    }.getOrNull()

    /** Devuelve null ante clave invalidada, dato corrupto o fallo del Keystore. */
    fun descifrar(blob: String): String? = runCatching {
        val partes = blob.removePrefix(MARCA).split(':')
        require(partes.size == 2) { "blob inválido" }
        val iv = Base64.decode(partes[0], Base64.NO_WRAP)
        val cifrado = Base64.decode(partes[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(cifrado), Charsets.UTF_8)
    }.getOrNull()

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "proyectwin_token_sesion"
        private const val TRANSFORMACION = "AES/GCM/NoPadding"

        /** Marca de versión del formato cifrado (distingue del texto legado). */
        const val MARCA = "v1:"

        /** true si el valor guardado ya tiene el formato cifrado. */
        fun esBlobCifrado(valor: String): Boolean =
            valor.startsWith(MARCA) && valor.count { it == ':' } == 2

        /** true si hay un token legado en texto plano pendiente de migrar. */
        fun requiereMigracion(valor: String?): Boolean =
            valor != null && !esBlobCifrado(valor)
    }
}

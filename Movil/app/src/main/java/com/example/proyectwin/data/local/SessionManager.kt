package com.example.proyectwin.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.data.model.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "proyectwin_session")

/**
 * Sesión persistente (DataStore Preferences): usuario, token Bearer de Sanctum
 * (cifrado con [TokenCipher]) y datos locales de perfil. Fuente de verdad de
 * la sesión; la copia en memoria para la red la mantiene `TokenProvider`.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenCipher: TokenCipher,
) {

    companion object {
        private val KEY_USER_ID = intPreferencesKey("user_id")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_APELLIDO = stringPreferencesKey("user_apellido")
        private val KEY_USER_NOMBRE = stringPreferencesKey("user_nombre")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_USER_ROLE = stringPreferencesKey("user_role")
        private val KEY_USER_TOKEN = stringPreferencesKey("user_token")
        private val KEY_USER_FOTO = stringPreferencesKey("user_foto")
        private val KEY_USER_FICHA_ID = intPreferencesKey("user_ficha_id")
        private val KEY_USER_DOCUMENTO = stringPreferencesKey("user_documento")
        private val KEY_USER_USERNAME = stringPreferencesKey("user_username")
        private val KEY_USER_MUST_CHANGE_PASSWORD = booleanPreferencesKey("user_must_change_password")
    }

    val currentUser: Flow<GeneralUser?> = context.dataStore.data.map { prefs ->
        val id = prefs[KEY_USER_ID] ?: return@map null
        GeneralUser(
            id = id,
            name = prefs[KEY_USER_NAME] ?: "",
            email = prefs[KEY_USER_EMAIL] ?: "",
            role = prefs[KEY_USER_ROLE] ?: UserRole.APRENDIZ.value,
            token = descifrarToken(prefs[KEY_USER_TOKEN]),
            fotoPerfil = prefs[KEY_USER_FOTO],
            fichaId = prefs[KEY_USER_FICHA_ID],
            documentoIdentidad = prefs[KEY_USER_DOCUMENTO],
            nombre = prefs[KEY_USER_NOMBRE],
            apellido = prefs[KEY_USER_APELLIDO],
            username = prefs[KEY_USER_USERNAME] ?: "",
            mustChangePassword = prefs[KEY_USER_MUST_CHANGE_PASSWORD] ?: false,
        )
    }

    val isLoggedIn: Flow<Boolean> = currentUser.map { it != null }

    suspend fun saveUser(user: GeneralUser) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = user.id
            prefs[KEY_USER_NAME] = user.name
            prefs[KEY_USER_EMAIL] = user.email
            prefs[KEY_USER_ROLE] = user.role
            user.nombre?.let { prefs[KEY_USER_NOMBRE] = it }
            user.apellido?.let { prefs[KEY_USER_APELLIDO] = it }
            // El token solo se persiste cifrado; si el Keystore falla no se
            // guarda (la sesión vivirá en memoria hasta el próximo login).
            user.token?.let { token -> tokenCipher.cifrar(token)?.let { prefs[KEY_USER_TOKEN] = it } }
            if (user.fotoPerfil != null) prefs[KEY_USER_FOTO] = user.fotoPerfil
            if (user.fichaId != null) prefs[KEY_USER_FICHA_ID] = user.fichaId
            if (user.documentoIdentidad != null) prefs[KEY_USER_DOCUMENTO] = user.documentoIdentidad
            prefs[KEY_USER_USERNAME] = user.username
            prefs[KEY_USER_MUST_CHANGE_PASSWORD] = user.mustChangePassword
        }
    }

    /** Marca/desmarca el cambio obligatorio de contraseña (403 del middleware). */
    suspend fun setMustChangePassword(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_MUST_CHANGE_PASSWORD] = value
        }
    }

    suspend fun updateFoto(fotoBase64: String?) {
        context.dataStore.edit { prefs ->
            if (fotoBase64 != null) prefs[KEY_USER_FOTO] = fotoBase64
            else prefs.remove(KEY_USER_FOTO)
        }
    }

    suspend fun joinFicha(fichaId: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_FICHA_ID] = fichaId
        }
    }

    suspend fun clearFicha() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_USER_FICHA_ID)
        }
    }

    suspend fun getToken(): String? {
        return descifrarToken(context.dataStore.data.first()[KEY_USER_TOKEN])
    }

    /**
     * Migra el token legado (texto plano) al formato cifrado y descarta blobs
     * corruptos o con clave invalidada. Idempotente y seguro ante fallos: si
     * el Keystore no puede cifrar, el valor legado se conserva para reintentar.
     */
    suspend fun migrarTokenLegacy() {
        context.dataStore.edit { prefs ->
            val actual = prefs[KEY_USER_TOKEN] ?: return@edit
            when {
                TokenCipher.requiereMigracion(actual) -> {
                    val cifrado = tokenCipher.cifrar(actual)
                    if (cifrado != null) prefs[KEY_USER_TOKEN] = cifrado
                }
                tokenCipher.descifrar(actual) == null -> prefs.remove(KEY_USER_TOKEN)
            }
        }
    }

    /** Descifra el token guardado; el texto plano legado se usa mientras migra. */
    private fun descifrarToken(valor: String?): String? = when {
        valor == null -> null
        TokenCipher.esBlobCifrado(valor) -> tokenCipher.descifrar(valor)
        else -> valor
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}

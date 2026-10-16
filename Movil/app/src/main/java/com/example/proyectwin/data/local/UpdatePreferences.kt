package com.example.proyectwin.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateDataStore by preferencesDataStore(name = "proyectwin_updates")

/**
 * Estado local del aviso de actualización (DataStore propio, separado del de
 * sesión: cerrar sesión no reinicia la política de avisos). Solo guarda
 * marcas de tiempo y números de versión: nunca credenciales ni tokens.
 */
@Singleton
class UpdatePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private val KEY_ULTIMA_CONSULTA = longPreferencesKey("ultima_consulta_millis")
        private val KEY_VERSION_POSPUESTA = stringPreferencesKey("version_pospuesta")
        private val KEY_POSPUESTA_EN = longPreferencesKey("pospuesta_en_millis")
    }

    /** Última consulta exitosa al endpoint de versiones (epoch millis). */
    suspend fun ultimaConsulta(): Long? =
        context.updateDataStore.data.first()[KEY_ULTIMA_CONSULTA]

    suspend fun marcarConsulta(ts: Long) {
        context.updateDataStore.edit { it[KEY_ULTIMA_CONSULTA] = ts }
    }

    /** Versión pospuesta por el usuario junto al momento en que lo hizo. */
    suspend fun pospuesta(): Pair<String?, Long> {
        val prefs = context.updateDataStore.data.first()
        return prefs[KEY_VERSION_POSPUESTA] to (prefs[KEY_POSPUESTA_EN] ?: 0L)
    }

    suspend fun posponer(version: String, ts: Long) {
        context.updateDataStore.edit { prefs ->
            prefs[KEY_VERSION_POSPUESTA] = version
            prefs[KEY_POSPUESTA_EN] = ts
        }
    }
}

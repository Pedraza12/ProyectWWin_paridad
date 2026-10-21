package com.example.proyectwin.ui.viewmodel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.BuildConfig
import com.example.proyectwin.data.local.UpdatePreferences
import com.example.proyectwin.data.model.AppVersionInfo
import com.example.proyectwin.domain.repository.AppUpdatesRepository
import com.example.proyectwin.domain.util.VersionComparator
import com.example.proyectwin.domain.util.evaluarActualizacion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Estado completo del centro de actualizaciones (consulta + descarga + instalación). */
data class UpdateCenterUiState(
    val versionInstalada: String = BuildConfig.VERSION_NAME,
    val consultando: Boolean = true,
    val info: AppVersionInfo? = null,
    val hayActualizacion: Boolean = false,
    val obligatoria: Boolean = false,
    val error: String? = null,
    val errorDescarga: String? = null,
    val sinUrlDescarga: Boolean = false,
    val descargando: Boolean = false,
    val progresoDescarga: Int = 0,
    val archivoListo: Boolean = false,
    val versionDescargada: String? = null,
    val requierePermisoInstalacion: Boolean = false,
    val mensaje: String? = null,
)

/**
 * Consulta la versión publicada del APK, la compara numéricamente con la
 * instalada y, si el usuario lo pide, descarga el APK por HTTPS, verifica
 * paquete/firma/versión y abre el instalador oficial del sistema.
 *
 * No instala silenciosamente ni requiere root. La sesión no se toca.
 */
@HiltViewModel
class UpdateCenterViewModel @Inject constructor(
    private val repository: AppUpdatesRepository,
    private val prefs: UpdatePreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        private const val MAX_APK_BYTES = 200L * 1024 * 1024
    }

    private val _uiState = MutableStateFlow(UpdateCenterUiState())
    val uiState: StateFlow<UpdateCenterUiState> = _uiState.asStateFlow()

    private var archivoDescargado: File? = null

    init {
        verificar()
    }

    /** Comprobación manual o al entrar a la sección. */
    fun verificar() {
        if (_uiState.value.descargando) return
        _uiState.update {
            it.copy(
                consultando = true,
                error = null,
                errorDescarga = null,
                mensaje = null,
            )
        }
        viewModelScope.launch {
            repository.versionPublicada().fold(
                onSuccess = { info ->
                    val evaluacion = evaluarActualizacion(info, BuildConfig.VERSION_NAME)
                    if (!evaluacion.valida) {
                        _uiState.update {
                            it.copy(
                                consultando = false,
                                error = "El servidor no informó una versión válida de la aplicación.",
                            )
                        }
                        return@fold
                    }
                    prefs.marcarConsulta(System.currentTimeMillis())
                    _uiState.update {
                        it.copy(
                            consultando = false,
                            info = info,
                            hayActualizacion = evaluacion.hayActualizacion,
                            obligatoria = evaluacion.obligatoria,
                            sinUrlDescarga = evaluacion.sinUrlDescarga,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            consultando = false,
                            error = e.message ?: "No se pudo consultar la versión publicada. Revisa tu conexión.",
                        )
                    }
                },
            )
        }
    }

    /** "Más tarde" del centro: misma política de 24 h que el aviso global. */
    fun posponer() {
        val version = _uiState.value.info?.versionPublicada ?: return
        viewModelScope.launch {
            prefs.posponer(version, System.currentTimeMillis())
            _uiState.update { it.copy(mensaje = "Te recordaremos esta actualización más adelante.") }
        }
    }

    /** Descarga y valida el APK; al terminar abre el instalador del sistema. */
    fun descargarEInstalar() {
        val estado = _uiState.value
        if (estado.descargando) return
        if (!estado.hayActualizacion) {
            _uiState.update { it.copy(mensaje = "Ya tienes la última versión disponible.") }
            return
        }
        val info = estado.info ?: return
        val url = info.urlDescarga?.takeIf { it.startsWith("https://") }
        if (url == null) {
            // El estado `sinUrlDescarga` ya se muestra como aviso en la tarjeta.
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    descargando = true,
                    progresoDescarga = 0,
                    error = null,
                    errorDescarga = null,
                    mensaje = null,
                    archivoListo = false,
                    requierePermisoInstalacion = false,
                )
            }
            runCatching { descargarYValidar(url, info.versionPublicada) }
                .onSuccess { archivo ->
                    archivoDescargado = archivo
                    _uiState.update {
                        it.copy(
                            descargando = false,
                            progresoDescarga = 100,
                            archivoListo = true,
                            versionDescargada = info.versionPublicada,
                            errorDescarga = null,
                        )
                    }
                    instalar()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            descargando = false,
                            errorDescarga = e.message ?: "No se pudo descargar la actualización.",
                        )
                    }
                }
        }
    }

    /** Abre el instalador oficial si ya hay un APK validado. */
    fun instalar() {
        val archivo = archivoDescargado
        if (archivo == null || !archivo.exists()) {
            _uiState.update { it.copy(errorDescarga = "Primero descarga la actualización.") }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _uiState.update {
                it.copy(
                    requierePermisoInstalacion = true,
                    mensaje = "Permite instalar aplicaciones desconocidas para ProyecTwin y vuelve a tocar Instalar.",
                )
            }
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            archivo,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            _uiState.update {
                it.copy(
                    requierePermisoInstalacion = false,
                    mensaje = "Se abrió el instalador del sistema.",
                )
            }
        } catch (_: ActivityNotFoundException) {
            _uiState.update { it.copy(errorDescarga = "No se encontró el instalador de aplicaciones del sistema.") }
        }
    }

    /** Envía a los ajustes de "instalar apps desconocidas" para esta app. */
    fun abrirAjustesPermiso() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            _uiState.update { it.copy(errorDescarga = "No se pudo abrir la configuración de instalación.") }
        }
    }

    fun limpiarMensaje() {
        _uiState.update { it.copy(mensaje = null) }
    }

    // ------------------------------------------------------------ Internos

    private suspend fun descargarYValidar(url: String, versionEsperada: String): File =
        withContext(Dispatchers.IO) {
            val directorio = File(context.cacheDir, "updates").apply { mkdirs() }
            directorio.listFiles()?.forEach { it.delete() }
            val archivo = File(directorio, "ProyecTwin-$versionEsperada.apk")

            // Cliente propio SIN interceptores de autenticación: la URL del APK
            // es externa y nunca debe recibir el token de la sesión.
            val cliente = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            cliente.newCall(Request.Builder().url(url).get().build()).execute().use { respuesta ->
                if (!respuesta.isSuccessful) {
                    error("La descarga falló (HTTP ${respuesta.code}). Intenta de nuevo más tarde.")
                }
                val cuerpo = respuesta.body ?: error("El servidor devolvió una descarga vacía.")
                val longitud = cuerpo.contentLength()
                if (longitud > MAX_APK_BYTES) error("El archivo de actualización es demasiado grande.")

                cuerpo.byteStream().use { entrada ->
                    archivo.outputStream().use { salida ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val leidos = entrada.read(buffer)
                            if (leidos < 0) break
                            total += leidos
                            if (total > MAX_APK_BYTES) {
                                error("El archivo de actualización es demasiado grande.")
                            }
                            salida.write(buffer, 0, leidos)
                            if (longitud > 0) {
                                val progreso = ((total * 100) / longitud).toInt().coerceIn(0, 100)
                                _uiState.update { it.copy(progresoDescarga = progreso) }
                            }
                        }
                    }
                }
            }

            verificarApk(archivo, versionEsperada)
            archivo
        }

    /**
     * El APK debe ser de este paquete, declarar la versión anunciada y estar
     * firmado con el mismo certificado que la app instalada.
     */
    private fun verificarApk(archivo: File, versionEsperada: String) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val paquete = pm.getPackageArchiveInfo(archivo.absolutePath, flags)
            ?: error("El archivo descargado no es un APK válido.")
        if (paquete.packageName != context.packageName) {
            error("El APK descargado no corresponde a ProyecTwin.")
        }
        if (paquete.versionName.isNullOrBlank() ||
            VersionComparator.comparar(paquete.versionName, versionEsperada) != 0
        ) {
            error("El APK descargado no corresponde a la versión anunciada ($versionEsperada).")
        }

        val instalada = pm.getPackageInfo(context.packageName, flags)
        val firmaInstalada = huellasFirma(instalada)
        val firmaNueva = huellasFirma(paquete)
        if (firmaInstalada.isEmpty() || firmaNueva.isEmpty() ||
            firmaInstalada.toSet() != firmaNueva.toSet()
        ) {
            error("La firma del APK no coincide con la aplicación instalada.")
        }
    }

    private fun huellasFirma(info: PackageInfo): List<String> {
        val firmas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.toList().orEmpty()
        }
        return firmas.map { firma ->
            val digest = MessageDigest.getInstance("SHA-256").digest(firma.toByteArray())
            digest.joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}

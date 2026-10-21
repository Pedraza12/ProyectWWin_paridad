package com.example.proyectwin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.UpdateCenterUiState
import com.example.proyectwin.ui.viewmodel.UpdateCenterViewModel

/**
 * Centro de actualizaciones compartido por los tres roles:
 *  - muestra versión instalada y publicada;
 *  - consulta manual/pull;
 *  - descarga el APK por HTTPS, lo valida y abre el instalador del sistema;
 *  - conserva la sesión y los datos locales (no borra nada).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdatesScreen(
    onBack: () -> Unit,
    viewModel: UpdateCenterViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(estado.mensaje) {
        estado.mensaje?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.limpiarMensaje()
        }
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "Actualizaciones",
                onBack = onBack,
                showProfile = false,
                showNotifications = false,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = senaColors().background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SenaPageHeader(
                title = "Centro de Actualizaciones",
                subtitle = "Consulta la versión instalada, revisa las novedades y actualiza ProyecTwin.",
                icon = Icons.Default.SystemUpdate,
            )

            TarjetaEstado(estado = estado, onReintentar = viewModel::verificar)

            if (estado.hayActualizacion) {
                TarjetaActualizacion(
                    estado = estado,
                    onDescargar = viewModel::descargarEInstalar,
                    onInstalar = viewModel::instalar,
                    onPermiso = viewModel::abrirAjustesPermiso,
                    onMasTarde = viewModel::posponer,
                )
            } else if (!estado.consultando && estado.info != null && estado.error == null) {
                SenaAlertBanner(
                    title = "Estás al día",
                    message = "Tienes la última versión disponible de ProyecTwin (${estado.versionInstalada}).",
                    icon = Icons.Default.CheckCircle,
                    color = senaColors().success,
                )
            }

            SenaButton(
                text = "BUSCAR ACTUALIZACIONES",
                onClick = viewModel::verificar,
                isPrimary = false,
                isLoading = estado.consultando,
                enabled = !estado.descargando,
                icon = Icons.Default.Refresh,
                modifier = Modifier.fillMaxWidth(),
            )

            SenaSectionHeader(title = "Historial de versiones")
            TarjetaHistorial(estado = estado)

            SenaAlertBanner(
                title = "Sobre los avisos",
                message = "La comprobación se ejecuta al abrir la aplicación y al entrar a esta sección. " +
                    "No hay notificaciones push configuradas: no se avisa con la app cerrada.",
                icon = Icons.Default.NotificationsNone,
                color = senaColors().info,
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun TarjetaEstado(
    estado: UpdateCenterUiState,
    onReintentar: () -> Unit,
) {
    SenaCard(elevation = 1.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = senaColors().green.copy(alpha = 0.1f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = senaColors().green)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Versión instalada",
                        style = MaterialTheme.typography.labelSmall,
                        color = senaColors().textLight,
                        maxLines = 1,
                    )
                    Text(
                        estado.versionInstalada,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = senaColors().text,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = when {
                        estado.consultando -> senaColors().info.copy(alpha = 0.12f)
                        estado.error != null -> senaColors().danger.copy(alpha = 0.12f)
                        estado.hayActualizacion -> senaColors().warning.copy(alpha = 0.15f)
                        else -> senaColors().success.copy(alpha = 0.12f)
                    },
                ) {
                    Text(
                        text = when {
                            estado.consultando -> "Consultando…"
                            estado.error != null -> "Sin verificar"
                            estado.hayActualizacion -> "Disponible"
                            else -> "Al día"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = when {
                            estado.consultando -> senaColors().info
                            estado.error != null -> senaColors().danger
                            estado.hayActualizacion -> senaColors().warning
                            else -> senaColors().success
                        },
                    )
                }
            }

            HorizontalDivider(color = senaColors().borderSoft)

            Row {
                Text(
                    "Última versión publicada",
                    style = MaterialTheme.typography.bodySmall,
                    color = senaColors().textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    estado.info?.versionPublicada ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                )
            }

            if (estado.consultando) {
                BarraProgresoIndeterminada()
            }

            estado.error?.let { error ->
                SenaAlertBanner(
                    title = "No se pudo consultar",
                    message = error,
                    icon = Icons.Default.CloudOff,
                    color = senaColors().danger,
                    actionText = "Reintentar",
                    onAction = onReintentar,
                )
            }
        }
    }
}

@Composable
private fun TarjetaActualizacion(
    estado: UpdateCenterUiState,
    onDescargar: () -> Unit,
    onInstalar: () -> Unit,
    onPermiso: () -> Unit,
    onMasTarde: () -> Unit,
) {
    val info = estado.info ?: return

    SenaSectionHeader(title = "Nueva versión disponible")
    SenaCard(elevation = 2.dp, containerColor = senaColors().warning.copy(alpha = 0.06f)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = senaColors().warning)
                Spacer(Modifier.width(10.dp))
                Text(
                    "ProyecTwin ${info.versionPublicada}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = senaColors().text,
                    modifier = Modifier.weight(1f),
                )
                if (estado.obligatoria) {
                    SenaChip(text = "Obligatoria", color = senaColors().danger)
                }
            }

            Text(
                "Hay una actualización de ProyecTwin con mejoras y correcciones.",
                style = MaterialTheme.typography.bodySmall,
                color = senaColors().textSecondary,
            )

            info.notas?.let { notas ->
                Text(
                    notas,
                    style = MaterialTheme.typography.bodySmall,
                    color = senaColors().textSecondary,
                )
            }

            if (estado.sinUrlDescarga) {
                SenaAlertBanner(
                    title = "Descarga no publicada",
                    message = "El servidor anunció una versión nueva, pero todavía no publicó un enlace HTTPS de descarga. Intenta más tarde.",
                    icon = Icons.Default.LinkOff,
                    color = senaColors().warning,
                )
            }

            estado.errorDescarga?.let { error ->
                SenaAlertBanner(
                    title = "No se pudo completar la actualización",
                    message = error,
                    icon = Icons.Default.ErrorOutline,
                    color = senaColors().danger,
                )
                Text(
                    "La actualización publicada debe estar firmada con la misma clave que la aplicación instalada.",
                    style = MaterialTheme.typography.labelSmall,
                    color = senaColors().textMuted,
                )
            }

            if (estado.descargando) {
                Text(
                    "Descargando actualización… ${estado.progresoDescarga}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                )
                BarraProgreso(estado.progresoDescarga)
            }

            if (estado.archivoListo) {
                Text(
                    "APK descargado y verificado (paquete, versión y firma).",
                    style = MaterialTheme.typography.labelSmall,
                    color = senaColors().success,
                )
            }

            if (estado.requierePermisoInstalacion) {
                SenaAlertBanner(
                    title = "Permiso requerido",
                    message = "Android necesita autorizar la instalación de aplicaciones desconocidas para ProyecTwin.",
                    icon = Icons.Default.LockOpen,
                    color = senaColors().warning,
                    actionText = "Abrir ajustes",
                    onAction = onPermiso,
                )
            }

            if (!estado.sinUrlDescarga && !estado.descargando) {
                SenaButton(
                    text = if (estado.archivoListo) "INSTALAR ACTUALIZACIÓN" else "DESCARGAR E INSTALAR",
                    onClick = if (estado.archivoListo) onInstalar else onDescargar,
                    icon = if (estado.archivoListo) Icons.Default.InstallMobile else Icons.Default.Download,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!estado.obligatoria && !estado.descargando) {
                TextButton(onClick = onMasTarde, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Más tarde",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = senaColors().textSecondary,
                    )
                }
            }

            Text(
                "La instalación la realiza el instalador oficial de Android. " +
                    "Tu sesión y tus datos locales se conservan.",
                style = MaterialTheme.typography.labelSmall,
                color = senaColors().textMuted,
            )
        }
    }
}

@Composable
private fun TarjetaHistorial(estado: UpdateCenterUiState) {
    SenaCard(elevation = 1.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val info = estado.info
            if (info == null) {
                Text(
                    "Sin información de versiones por ahora.",
                    style = MaterialTheme.typography.bodySmall,
                    color = senaColors().textSecondary,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Versión ${info.versionPublicada}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = senaColors().text,
                        modifier = Modifier.weight(1f),
                    )
                    SenaChip(
                        text = if (estado.hayActualizacion) "Disponible" else "Instalada",
                        color = if (estado.hayActualizacion) senaColors().warning else senaColors().success,
                    )
                }
                info.notas?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().textSecondary,
                    )
                }
                info.versionMinima.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "Versión mínima admitida: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = senaColors().textLight,
                    )
                }
            }
            Text(
                "El servidor publica actualmente solo la versión más reciente; no se inventa un historial anterior.",
                style = MaterialTheme.typography.labelSmall,
                color = senaColors().textMuted,
            )
        }
    }
}

@Composable
private fun BarraProgreso(progreso: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(senaColors().borderSoft, CircleShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progreso.coerceIn(0, 100) / 100f)
                .fillMaxHeight()
                .background(senaColors().green, CircleShape),
        )
    }
}

@Composable
private fun BarraProgresoIndeterminada() {
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = senaColors().green,
        trackColor = senaColors().borderSoft,
    )
}

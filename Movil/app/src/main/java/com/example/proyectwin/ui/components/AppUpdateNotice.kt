package com.example.proyectwin.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.proyectwin.ui.theme.senaColors
import com.example.proyectwin.ui.viewmodel.AppUpdateUiState

/** Texto único del aviso (corto, claro y amable). */
private const val MENSAJE_ACTUALIZACION =
    "Hay una actualización de ProyecTwin con mejoras y ajustes. " +
        "Puedes instalarla ahora o continuar y hacerlo más tarde."

/**
 * Aviso de actualización de la app:
 *  - Opcional: banner discreto (no bloquea la navegación) con "Actualizar" y
 *    "Más tarde".
 *  - Obligatoria (política explícita del servidor): diálogo no posponible que
 *    solo se cierra después de abrir el enlace de descarga.
 * El aviso aparece una sola vez por detección; la política de reaparición vive
 * en el ViewModel (DataStore), no en cada pantalla.
 */
@Composable
fun AppUpdateHost(
    estado: AppUpdateUiState,
    onActualizar: () -> Unit,
    onMasTarde: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!estado.mostrar) return

    if (estado.obligatoria) {
        AppUpdateMandatoryDialog(estado = estado, onActualizar = onActualizar)
    } else {
        AppUpdateBanner(
            estado = estado,
            onActualizar = onActualizar,
            onMasTarde = onMasTarde,
            modifier = modifier,
        )
    }
}

@Composable
private fun AppUpdateBanner(
    estado: AppUpdateUiState,
    onActualizar: () -> Unit,
    onMasTarde: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 520.dp),
        shape = RoundedCornerShape(16.dp),
        color = senaColors().backgroundElevated,
        border = BorderStroke(1.dp, senaColors().border),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = senaColors().green,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Nueva versión disponible",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                MENSAJE_ACTUALIZACION,
                style = MaterialTheme.typography.bodySmall,
                color = senaColors().textSecondary,
            )
            estado.notas?.let { notas ->
                Spacer(Modifier.height(4.dp))
                Text(
                    notas,
                    style = MaterialTheme.typography.bodySmall,
                    color = senaColors().textMuted,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onMasTarde) {
                    Text(
                        "Más tarde",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = senaColors().textSecondary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                SenaButton(
                    text = "Actualizar",
                    onClick = onActualizar,
                    icon = Icons.Default.Download,
                    minWidth = 0.dp,
                )
            }
        }
    }
}

@Composable
private fun AppUpdateMandatoryDialog(
    estado: AppUpdateUiState,
    onActualizar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* Política obligatoria: el aviso no se pospone. */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        icon = {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = senaColors().green,
            )
        },
        title = {
            Text(
                "Actualización obligatoria",
                color = senaColors().text,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    "Tu versión de ProyecTwin ya no es compatible. Instala la " +
                        "versión ${estado.versionNueva.orEmpty()} para seguir usando la aplicación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = senaColors().textSecondary,
                )
                estado.notas?.let { notas ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        notas,
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().textMuted,
                    )
                }
            }
        },
        confirmButton = {
            SenaButton(
                text = "Actualizar",
                onClick = onActualizar,
                icon = Icons.Default.Download,
            )
        },
        containerColor = senaColors().backgroundElevated,
        iconContentColor = senaColors().green,
    )
}

/** Abre el enlace oficial de descarga en el navegador. Solo acepta https. */
fun abrirEnlaceSeguro(context: Context, url: String): Boolean {
    if (!url.startsWith("https://")) return false
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

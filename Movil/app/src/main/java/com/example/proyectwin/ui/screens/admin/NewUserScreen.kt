package com.example.proyectwin.ui.screens.admin

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.proyectwin.data.local.DescargasArchivos
import com.example.proyectwin.data.model.CredencialesUsuario
import com.example.proyectwin.ui.components.*
import com.example.proyectwin.ui.theme.*
import com.example.proyectwin.ui.viewmodel.AdminViewModel

/**
 * Alta de usuario (solo admin): documento obligatorio, rol aprendiz/instructor
 * y credenciales temporales generadas por el backend (se muestran una vez).
 */
@Composable
fun NewUserScreen(
    onBack: () -> Unit,
    adminViewModel: AdminViewModel = hiltViewModel(),
) {
    var nombre by remember { mutableStateOf("") }
    var apellido by remember { mutableStateOf("") }
    var tipoDocumento by remember { mutableStateOf("CC") }
    var numeroDocumento by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var rol by remember { mutableStateOf("aprendiz") }
    var errorLocal by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    var errorServidor by remember { mutableStateOf<String?>(null) }
    var credenciales by remember { mutableStateOf<CredencialesUsuario?>(null) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    if (credenciales != null) {
        val datos = credenciales!!
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.Key, contentDescription = null, tint = senaColors().green) },
            title = { Text("Credenciales generadas", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Guárdalas o compártelas: la contraseña temporal no se vuelve a mostrar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().textSecondary,
                    )
                    CredencialFila("Usuario", datos.username) { clipboard.setText(AnnotatedString(datos.username)) }
                    CredencialFila("Contraseña temporal", datos.passwordTemporal) { clipboard.setText(AnnotatedString(datos.passwordTemporal)) }
                    Text(
                        if (datos.enviadas) "El correo con las credenciales fue enviado."
                        else "El correo no pudo enviarse; usa el reenvío en el detalle del usuario.",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (datos.enviadas) senaColors().success else senaColors().warning,
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        adminViewModel.credencialesPdf(listOf(datos.usuario.id)) { resultado ->
                            val mensaje = resultado.fold(
                                onSuccess = { bytes ->
                                    DescargasArchivos.guardarPdf(context, bytes, "credenciales-${datos.username}.pdf")
                                },
                                onFailure = { it.message ?: "No se pudo descargar el PDF." },
                            )
                            Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Text("PDF", color = senaColors().green)
                    }
                    TextButton(onClick = { credenciales = null; onBack() }) {
                        Text("Listo", color = senaColors().green, fontWeight = FontWeight.Bold)
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            SenaTopBar(
                title = "Nuevo Usuario",
                onBack = onBack,
                showProfile = true,
                showNotifications = true,
            )
        },
        containerColor = senaColors().background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SenaPageHeader(
                title = "Crear Usuario",
                subtitle = "El sistema genera usuario y contraseña temporal automáticamente.",
                icon = Icons.Default.PersonAdd,
            )

            errorLocal?.let {
                SenaAlertBanner(title = "Revisa el formulario", message = it, icon = Icons.Default.Error, color = senaColors().danger)
            }
            errorServidor?.let {
                SenaAlertBanner(title = "Error del servidor", message = it, icon = Icons.Default.Error, color = senaColors().danger)
            }

            SenaCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SenaTextField(value = nombre, onValueChange = { nombre = it; errorLocal = null }, label = "Nombre *", placeholder = "Ej: Ana María")
                    SenaTextField(value = apellido, onValueChange = { apellido = it; errorLocal = null }, label = "Apellido *", placeholder = "Ej: Gómez Ruiz")

                    Text("Tipo de documento *", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("CC", "TI", "CE", "PPT").forEach { tipo ->
                            SenaChip(
                                text = tipo,
                                color = senaColors().green,
                                isSelected = tipoDocumento == tipo,
                                onClick = { tipoDocumento = tipo },
                            )
                        }
                    }

                    SenaTextField(
                        value = numeroDocumento,
                        onValueChange = { numeroDocumento = it; errorLocal = null },
                        label = "Número de documento *",
                        placeholder = "Ej: 1098765432",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    )
                    SenaTextField(
                        value = correo,
                        onValueChange = { correo = it; errorLocal = null },
                        label = "Correo institucional *",
                        placeholder = "usuario@sena.edu.co",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                    )

                    Text("Rol *", style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("aprendiz" to "Aprendiz", "instructor" to "Instructor").forEach { (valor, etiqueta) ->
                            SenaChip(
                                text = etiqueta,
                                color = senaColors().green,
                                isSelected = rol == valor,
                                onClick = { rol = valor },
                            )
                        }
                    }
                }
            }

            SenaButton(
                text = "CREAR USUARIO",
                onClick = {
                    errorLocal = when {
                        nombre.isBlank() || apellido.isBlank() -> "Nombre y apellido son obligatorios."
                        numeroDocumento.isBlank() -> "El número de documento es obligatorio."
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches() -> "Ingresa un correo válido."
                        else -> null
                    }
                    if (errorLocal == null && !enviando) {
                        enviando = true
                        adminViewModel.createUser(
                            nombre = nombre.trim(),
                            apellido = apellido.trim(),
                            tipoDocumento = tipoDocumento,
                            numeroDocumento = numeroDocumento.trim(),
                            correo = correo.trim().lowercase(),
                            rol = rol,
                        ) { resultado ->
                            enviando = false
                            resultado.fold(
                                onSuccess = { credenciales = it },
                                onFailure = { errorServidor = it.message ?: "No se pudo crear el usuario." },
                            )
                        }
                    }
                },
                isLoading = enviando,
                icon = Icons.Default.PersonAdd,
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun CredencialFila(
    etiqueta: String,
    valor: String,
    onCopiar: () -> Unit,
) {
    Surface(
        color = senaColors().inputBackground,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
                Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = senaColors().text)
            }
            IconButton(onClick = onCopiar) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = senaColors().green)
            }
        }
    }
}

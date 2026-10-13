package com.example.proyectwin.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.di.AppEntryPoint
import com.example.proyectwin.domain.repository.NotificationsRepository
import com.example.proyectwin.navigation.NavIntents
import com.example.proyectwin.ui.theme.*
import dagger.hilt.android.EntryPointAccessors

// --- PREMIUM COMPONENTS (EMERALD LUSH EDITION) ---

@Composable
fun SenaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = true,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color? = null
) {
    val primaryGradient = Brush.linearGradient(colors = listOf(senaColors().green, senaColors().darkGreen))
    
    Surface(
        modifier = modifier
            // Se adapta al contenido: no recorta el texto si el caller fija una
            // altura menor o si el texto ocupa más de una línea.
            .heightIn(min = 56.dp)
            .widthIn(min = 120.dp)
            .clip(CircleShape)
            .then(if (isPrimary && enabled && !isLoading) Modifier.background(primaryGradient) else Modifier)
            .clickable(enabled = enabled && !isLoading) { onClick() },
        color = when {
            !isPrimary -> Color.Transparent
            containerColor != null -> containerColor
            else -> if (enabled) Color.Transparent else senaColors().border
        },
        border = if (!isPrimary) androidx.compose.foundation.BorderStroke(2.dp, senaColors().green) else null,
        shape = CircleShape
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = if (isPrimary) Color.White else senaColors().green, strokeWidth = 2.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (isPrimary) Color.White else senaColors().green)
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = if (isPrimary) Color.White else senaColors().green
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun SenaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    val focusManager = LocalFocusManager.current
    // Los campos de varias líneas deben conservar la tecla Intro (nueva línea)
    // y el comportamiento normal de los espacios: con ImeAction.Next el teclado
    // mostraba "→|" y al pulsarlo (o al escribir) saltaba al siguiente campo,
    // impidiendo redactar párrafos y objetivos.
    val multilinea = !singleLine
    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                text = label, 
                style = MaterialTheme.typography.labelMedium, 
                color = senaColors().textSecondary, 
                modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            isError = isError,
            placeholder = { Text(placeholder, color = senaColors().textMuted) },
            leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, tint = if (isError) senaColors().danger else senaColors().green) } },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (multilinea) ImeAction.Default else imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { if (!multilinea) focusManager.moveFocus(FocusDirection.Down) },
                onDone = { focusManager.clearFocus() },
            ),
            supportingText = supportingText?.let { { Text(it, color = senaColors().danger) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isError) senaColors().danger else senaColors().green,
                unfocusedBorderColor = if (isError) senaColors().danger else senaColors().border,
                focusedContainerColor = senaColors().inputBackground,
                unfocusedContainerColor = senaColors().inputBackground,
                cursorColor = senaColors().green,
                errorBorderColor = senaColors().danger
            ),
            singleLine = singleLine,
            minLines = minLines,
        )
    }
}

@Composable
fun SenaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: Dp = 4.dp,
    containerColor: Color = senaColors().backgroundElevated,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier.padding(24.dp), 
            content = content
        )
    }
}

@Composable
fun SenaPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        if (icon != null) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = senaColors().green.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = senaColors().green, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
        
        Text(
            title, 
            style = MaterialTheme.typography.headlineMedium, 
            fontWeight = FontWeight.Black, 
            color = senaColors().text
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle, 
                style = MaterialTheme.typography.bodyMedium, 
                color = senaColors().textLight
            )
        }
    }
}

@Composable
fun SenaStatusBadge(status: String, modifier: Modifier = Modifier) {
    val color = when (status.lowercase()) {
        "borrador" -> senaColors().accent
        "pendiente", "en revisión", "en_revision" -> senaColors().warning
        "aprobado", "revisado", "activo", "vigente", "resuelto" -> senaColors().success
        "rechazado", "error", "inactivo", "anulada", "anulado", "suspendido", "histórica", "falló" -> senaColors().danger
        "finalizado", "cerrado" -> senaColors().info
        else -> senaColors().textMuted
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = CircleShape,
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = status.uppercase(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenaTopBar(
    title: String, 
    showProfile: Boolean = true, 
    showNotifications: Boolean = true,
    notificationBadgeCount: Int = -1,
    onBack: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    onNavigateToProfile: (() -> Unit)? = null,
    onNavigateToAlerts: (() -> Unit)? = null
) {
    val backgroundBrush = Brush.linearGradient(colors = listOf(senaColors().header, senaColors().darkGreen))
    val isDarkState = LocalThemeIsDark.current
    val effectiveBadgeCount = if (notificationBadgeCount == -1 && showNotifications) {
        val context = LocalContext.current
        val entryPoint = remember(context) {
            EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java)
        }
        val sessionManager = remember(entryPoint) { entryPoint.sessionManager() }
        val repo = remember(entryPoint) { entryPoint.notificationsRepository() }
        val user by sessionManager.currentUser.collectAsState(initial = null)
        var unreadCount by remember { mutableStateOf(0) }
        val version by com.example.proyectwin.ui.viewmodel.NotificationsVersion.version.collectAsState()
        LaunchedEffect(user?.id, version) {
            val id = user?.id ?: return@LaunchedEffect
            unreadCount = repo.listar(id).fold(
                onSuccess = { lista -> lista.count { !it.leido } },
                onFailure = { 0 },
            )
        }
        if (user == null) 0 else unreadCount
    } else {
        notificationBadgeCount.coerceAtLeast(0)
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.background(backgroundBrush)) {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        title, 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold, 
                        color = Color.White
                    ) 
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { isDarkState.value = !isDarkState.value }) {
                        Icon(
                            if (isDarkState.value) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Tema",
                            tint = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    if (showNotifications) {
                        if (effectiveBadgeCount > 0) {
                            SenaBadgedIcon(
                                icon = Icons.Default.Notifications,
                                badgeCount = effectiveBadgeCount,
                                contentDescription = "Notificaciones",
                                onClick = { onNavigateToAlerts?.invoke() ?: NavIntents.pedirNotificaciones() }
                            )
                        } else {
                            IconButton(onClick = { onNavigateToAlerts?.invoke() ?: NavIntents.pedirNotificaciones() }) { 
                                Icon(Icons.Default.Notifications, contentDescription = "Notificaciones", tint = Color.White.copy(alpha = 0.9f)) 
                            }
                        }
                    }
                    if (onLogout != null) {
                        IconButton(onClick = onLogout) { 
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Salir", tint = Color.White.copy(alpha = 0.9f)) 
                        }
                    }
                    if (showProfile) {
                        Surface(
                            modifier = Modifier.padding(end = 12.dp).size(36.dp), 
                            shape = CircleShape, 
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            IconButton(onClick = { onNavigateToProfile?.invoke() ?: NavIntents.pedirPerfil() }) { 
                                Icon(Icons.Default.Person, contentDescription = "Perfil", tint = Color.White, modifier = Modifier.size(18.dp)) 
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SenaMetricPill(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = senaColors().backgroundElevated,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, senaColors().borderSoft)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = senaColors().green.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = senaColors().green, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = senaColors().text)
                Text(label, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
            }
        }
    }
}

@Composable
fun SenaSettingsItem(
    icon: ImageVector,
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = senaColors().green.copy(alpha = 0.08f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = senaColors().green, modifier = Modifier.size(22.dp))
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = senaColors().text)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = senaColors().textLight)
            }
        }
        
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = senaColors().textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SenaEmptyState(
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = senaColors().green.copy(alpha = 0.05f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = senaColors().green.copy(alpha = 0.4f), 
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        if (title != null) {
            Text(
                title, 
                style = MaterialTheme.typography.titleLarge, 
                fontWeight = FontWeight.Black, 
                color = senaColors().text
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            message, 
            style = MaterialTheme.typography.bodyMedium, 
            color = senaColors().textSecondary, 
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp)
        )
        if (action != null) {
            Spacer(modifier = Modifier.height(32.dp))
            action()
        }
    }
}

@Composable
fun SenaAlertBanner(
    message: String,
    title: String,
    icon: ImageVector = Icons.Default.Warning,
    color: Color = senaColors().warning,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = senaColors().text)
                Text(message, style = MaterialTheme.typography.bodySmall, color = senaColors().textSecondary, lineHeight = 18.sp)
                if (actionText != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(actionText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color)
                    }
                }
            }
        }
    }
}

@Composable
fun SenaSectionHeader(title: String, modifier: Modifier = Modifier, actionText: String? = null, onActionClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), 
        horizontalArrangement = Arrangement.SpaceBetween, 
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title, 
            style = MaterialTheme.typography.titleLarge, 
            fontWeight = FontWeight.Black, 
            color = senaColors().text
        )
        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(actionText, color = senaColors().green, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = senaColors().green, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun SenaBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        // Con el teclado abierto, la barra de acciones sube por encima de él:
        // antes quedaba tapada y no se podía pulsar Guardar/Enviar/Borrador.
        modifier = modifier.fillMaxWidth().imePadding(),
        tonalElevation = 12.dp,
        shadowElevation = 24.dp,
        color = senaColors().backgroundElevated
    ) {
        Row(
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun SenaChip(text: String, color: Color, modifier: Modifier = Modifier, isSelected: Boolean = false, onClick: (() -> Unit)? = null) {
    Surface(
        color = if (isSelected) color else color.copy(alpha = 0.1f),
        shape = CircleShape,
        modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (isSelected) Color.White else color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun SenaActionCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SenaCard(modifier = modifier, onClick = onClick, elevation = 6.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.size(64.dp), 
                shape = RoundedCornerShape(20.dp), 
                color = senaColors().green.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = senaColors().green, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = senaColors().text, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = senaColors().textLight, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun TeamMemberRow(initials: String, name: String, role: String, isLider: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = if (isLider) senaColors().green else senaColors().borderSoft
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(initials, color = if (isLider) Color.White else senaColors().text, fontWeight = FontWeight.Black)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = senaColors().text)
            Text(role, style = MaterialTheme.typography.bodySmall, color = senaColors().textLight)
        }
    }
}

@Composable
fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = senaColors().green.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = senaColors().green, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = senaColors().textLight)
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = senaColors().text)
        }
    }
}

@Composable
fun ProfileInfoRow(icon: ImageVector, label: String, value: String) {
    DetailRow(icon = icon, label = label, value = value)
}

@Composable
fun SenaBottomNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    items: List<SenaBottomNavItem>
) {
    NavigationBar(
        containerColor = senaColors().backgroundElevated,
        tonalElevation = 8.dp,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        items.forEach { item ->
            // Los destinos pueden declarar query args opcionales (p. ej. filtros);
            // el tab compara solo la ruta base.
            val isSelected = currentRoute?.substringBefore('?') == item.route.substringBefore('?')
            NavigationBarItem(
                selected = isSelected,
                onClick = { if (!isSelected) onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = if (isSelected) senaColors().green else senaColors().textMuted
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                        color = if (isSelected) senaColors().green else senaColors().textMuted
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = senaColors().green.copy(alpha = 0.1f)
                )
            )
        }
    }
}

data class SenaBottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

// --- PADDING ROW (UTILITY, shared across all screens) ---
@Composable
fun PaddingRow(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.padding(horizontal = 20.dp), content = content)
}

// --- FOTO DE PERFIL (avatar clickable con preview, soporte base64) ---
@Composable
fun SenaAvatar(
    fotoBase64: String?,
    nombre: String,
    modifier: Modifier = Modifier.size(100.dp),
    onClick: (() -> Unit)? = null,
    showChangeIndicator: Boolean = false
) {
    val imagen = remember(fotoBase64) { decodificarFotoBase64(fotoBase64) }
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = CircleShape,
        color = senaColors().backgroundElevated,
        shadowElevation = 10.dp
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            // El tamaño de la inicial se deriva del lado real del avatar (los
            // callers usan 28–110 dp): un tamaño fijo recortaba las letras en
            // los círculos pequeños y las desbordaba en los grandes.
            val lado = if (maxWidth < maxHeight) maxWidth else maxHeight
            val iniciales = nombre.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
                .ifBlank { "?" }
            val tamanoLetra = (lado.value * if (iniciales.length > 1) 0.38f else 0.46f).sp
            if (imagen != null) {
                Image(
                    bitmap = imagen,
                    contentDescription = "Foto de $nombre",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(lado * 0.05f)
                        .fillMaxSize()
                        .background(senaColors().green, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = iniciales,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = tamanoLetra,
                        lineHeight = tamanoLetra,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            if (showChangeIndicator) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 4.dp)
                        // El indicador también escala para no tapar el círculo
                        // en los avatares pequeños.
                        .size(if (lado < 48.dp) 22.dp else 32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "Cambiar foto",
                        tint = Color.White,
                        modifier = Modifier.size(if (lado < 48.dp) 12.dp else 18.dp)
                    )
                }
            }
        }
    }
}

private fun decodificarFotoBase64(valor: String?): ImageBitmap? {
    if (valor.isNullOrBlank()) return null
    val limpio = valor.substringAfter("base64,").trim()
    return try {
        val bytes = Base64.decode(limpio, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

// --- CLIPBOARD / COPY BUTTON ---
@Composable
fun SenaCopyButton(
    textToCopy: String,
    label: String = "Copiar",
    onCopied: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var copied by remember { mutableStateOf(false) }

    SenaButton(
        text = if (copied) "¡Copiado!" else label,
        onClick = {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("label", textToCopy)
            clipboard.setPrimaryClip(clip)
            copied = true
            onCopied()
        },
        icon = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
        isPrimary = false,
        modifier = Modifier.height(44.dp)
    )
}

// --- NOTIFICATION BADGE (para campana en SenaTopBar) ---
@Composable
fun SenaBadgedIcon(
    icon: ImageVector,
    badgeCount: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    BadgedBox(
        badge = {
            if (badgeCount > 0) {
                Badge(
                    containerColor = senaColors().danger,
                    contentColor = Color.White
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
            }
        }
    ) {
        IconButton(onClick = onClick) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

// --- LOADING STATE (full-screen loader) ---
@Composable
fun SenaLoadingState(
    modifier: Modifier = Modifier,
    message: String = "Cargando..."
) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = senaColors().green,
                strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = senaColors().textSecondary
            )
        }
    }
}

// --- ERROR STATE ---
@Composable
fun SenaErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    SenaEmptyState(
        message = message,
        icon = Icons.Default.ErrorOutline,
        modifier = modifier,
        title = "Error",
        action = if (onRetry != null) {
            { SenaButton(text = "Reintentar", onClick = onRetry, isPrimary = false) }
        } else null
    )
}

// --- PULL-TO-REFRESH WRAPPER ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenaPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val pullRefreshState = androidx.compose.material3.pulltorefresh.PullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = modifier
    ) {
        content()
    }
}

// --- SKELETON LOADERS ---
@Composable
fun SenaSkeletonLine(
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    height: Dp = 16.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeletonLine")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "alpha"
    )
    Surface(
        modifier = modifier.width(width).height(height),
        color = senaColors().border.copy(alpha = alpha),
        shape = RoundedCornerShape(8.dp)
    ) {}
}

@Composable
fun SenaSkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeletonCircle")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "alpha"
    )
    Surface(
        modifier = modifier.size(size),
        color = senaColors().border.copy(alpha = alpha),
        shape = CircleShape
    ) {}
}

@Composable
fun SenaSkeletonCard(
    modifier: Modifier = Modifier,
    lines: Int = 3,
    hasAvatar: Boolean = false,
    hasImage: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = senaColors().backgroundElevated
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            if (hasAvatar) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SenaSkeletonCircle(size = 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        SenaSkeletonLine(width = 140.dp, height = 14.dp)
                        Spacer(Modifier.height(8.dp))
                        SenaSkeletonLine(width = 100.dp, height = 12.dp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (hasImage) {
                SenaSkeletonLine(width = 300.dp, height = 120.dp)
                Spacer(Modifier.height(16.dp))
            }
            repeat(lines) { i ->
                SenaSkeletonLine(width = if (i == lines - 1) 160.dp else 280.dp, height = 14.dp)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun SenaSkeletonTable(
    modifier: Modifier = Modifier,
    rows: Int = 5,
    cols: Int = 4
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(cols) { i ->
                SenaSkeletonLine(
                    modifier = Modifier.weight(1f),
                    width = 60.dp,
                    height = 20.dp
                )
            }
        }
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(cols) { i ->
                    SenaSkeletonLine(
                        modifier = Modifier.weight(1f),
                        width = if (i == 0) 120.dp else (60..100).random().dp,
                        height = 14.dp
                    )
                }
            }
        }
    }
}

@Composable
fun SenaSkeletonStats(
    modifier: Modifier = Modifier,
    count: Int = 4
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(count) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = senaColors().backgroundElevated
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SenaSkeletonCircle(size = 32.dp)
                    Spacer(Modifier.height(12.dp))
                    SenaSkeletonLine(width = 40.dp, height = 24.dp)
                    Spacer(Modifier.height(8.dp))
                    SenaSkeletonLine(width = 60.dp, height = 12.dp)
                }
            }
        }
    }
}

// --- METRIC CARD WITH TREND ---
@Composable
fun SenaMetricCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    trend: Float? = null,
    trendUp: Boolean = true,
    color: Color = senaColors().green
) {
    SenaCard(modifier = modifier, elevation = 4.dp, containerColor = senaColors().backgroundElevated) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = color.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (trend != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (trendUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (trendUp) senaColors().success else senaColors().danger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${if (trendUp) "+" else ""}${trend.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (trendUp) senaColors().success else senaColors().danger
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = senaColors().text
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = senaColors().textLight
            )
        }
    }
}

// --- COLLAPSIBLE FILTER BAR ---
@Composable
fun SenaFilterBar(
    modifier: Modifier = Modifier,
    title: String = "Filtros",
    expanded: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    var internalExpanded by remember { mutableStateOf(expanded) }
    val isExpanded = onToggle != null || internalExpanded

    SenaCard(modifier = modifier, elevation = 1.dp, containerColor = senaColors().backgroundElevated) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (onToggle != null) onToggle(!isExpanded)
                    else internalExpanded = !internalExpanded
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    tint = senaColors().green,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().textSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.weight(1f))
                actions()
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                    tint = senaColors().textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

// --- ICON EXTENSIONS (PREMIUM UI) ---
val Icons.Filled.Tasks: ImageVector get() = Icons.AutoMirrored.Filled.List
val Icons.Filled.ChalkboardTeacher: ImageVector get() = Icons.Default.School

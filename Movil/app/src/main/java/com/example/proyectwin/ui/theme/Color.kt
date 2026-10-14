package com.example.proyectwin.ui.theme

import androidx.compose.ui.graphics.Color

// --- PALETA COMPARTIDA CON LA WEB (igual semántica y valores) ---
// Fuente de verdad: frontend/src/tokens/index.css.
// Claro = tema claro de la web; Oscuro = tema oscuro (por defecto en la web).
// El tema claro es el predeterminado en Android; el oscuro replica la consola.

// Verde institucional (marca). Claro: g-700; Oscuro: primario de la consola.
val SenaGreen = Color(0xFF22B949)          // consola (oscuro)
val SenaGreenLight = Color(0xFF2A7C00)     // g-700 (claro)
val SenaGreenStrong = Color(0xFF31D160)    // hover/foco en oscuro
val SenaDarkGreen = Color(0xFF1F5E00)      // g-800
val SenaAccent = Color(0xFF1B89A8)         // petróleo t-500 (claro)
val SenaAccentDark = Color(0xFF3BA7C4)     // petróleo t-400 (oscuro)

data class SenaColorScheme(
    val background: Color,
    val backgroundElevated: Color,
    val surface: Color,
    val header: Color,
    val inputBackground: Color,
    val text: Color,
    val textSecondary: Color,
    val textLight: Color,
    val textMuted: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val border: Color,
    val borderSoft: Color,
    val glassEffect: Color,
    val green: Color,
    val darkGreen: Color,
    val accent: Color,
    val onPrimary: Color,
    val dangerHover: Color,
    val dangerActive: Color,
    val primary10: Color,
    val success10: Color,
    val warning10: Color,
    val danger10: Color,
    val header10: Color,
    val info10: Color,
    val filaZebra: Color,
)

fun lightSenaColors() = SenaColorScheme(
    background = Color(0xFFF7FAF3),          // fondo con tinte verde (web claro)
    backgroundElevated = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F5),             // s-100
    header = Color(0xFF1F5E00),              // g-800 (govbar clara)
    inputBackground = Color(0xFFF5F5F5),
    text = Color(0xFF171717),                // s-900
    textSecondary = Color(0xFF666666),
    textLight = Color(0xFF666666),
    textMuted = Color(0xFFA3A3A3),           // s-400
    success = SenaGreenLight,
    warning = Color(0xFFF59E0B),             // amber-500
    danger = Color(0xFFDC2626),              // red-600
    info = Color(0xFF0E7490),                // t-600
    border = Color(0xFFEAEAEA),              // s-200
    borderSoft = Color(0xFFF5F5F5),
    glassEffect = Color(0x1AFFFFFF),
    green = SenaGreenLight,
    darkGreen = SenaDarkGreen,
    accent = SenaAccent,
    onPrimary = Color(0xFFFFFFFF),
    dangerHover = Color(0xFFB91C1C),
    dangerActive = Color(0xFF991B1B),
    primary10 = Color(0x0D2A7C00),
    success10 = Color(0x1A2A7C00),
    warning10 = Color(0x1AF59E0B),
    danger10 = Color(0x1ADC2626),
    header10 = Color(0x1A1F5E00),
    info10 = Color(0x1A0E7490),
    filaZebra = Color(0x05000000),
)

fun darkSenaColors() = SenaColorScheme(
    background = Color(0xFF0A0A0A),          // consola
    backgroundElevated = Color(0xFF1E1E1E),
    surface = Color(0xFF2A2A2A),             // surface-alt
    header = Color(0xFF12261B),              // govbar oscura
    inputBackground = Color(0xFF111111),     // surface-sunken
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFD0D0D0),
    textLight = Color(0xFFA0A0A0),
    textMuted = Color(0xFF737373),           // s-500
    success = Color(0xFF4CE07A),
    warning = Color(0xFFFFD07A),
    danger = Color(0xFFFFA3A3),
    info = Color(0xFF5CD4F2),
    border = Color(0x24FFFFFF),              // blanco 14%
    borderSoft = Color(0x1AFFFFFF),          // blanco 10%
    glassEffect = Color(0x1A000000),
    green = SenaGreen,
    darkGreen = SenaDarkGreen,
    accent = SenaAccentDark,
    onPrimary = Color(0xFF000000),           // texto negro sobre verde brillante
    dangerHover = Color(0xFFFF6B6B),
    dangerActive = Color(0xFFEF4444),
    primary10 = Color(0x1A22B949),
    success10 = Color(0x1A4CE07A),
    warning10 = Color(0x1AFFD07A),
    danger10 = Color(0x1AFFA3A3),
    header10 = Color(0x1A12261B),
    info10 = Color(0x1A5CD4F2),
    filaZebra = Color(0x08FFFFFF),
)

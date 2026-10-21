package com.example.proyectwin.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proyectwin.ui.theme.senaColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Colores web del par A (pendiente) y B (referencia aprobada). */
private val ParA = Color(0xFF22B949)
private val ParB = Color(0xFF22C1E8)

/**
 * Indicador circular del UMBRAL configurado del motor (no el porcentaje del
 * par). Geometría equivalente a la web: radio 46, trazo 9, arranque -90°.
 */
@Composable
fun UmbralDial(
    umbralPct: Int?,
    modifier: Modifier = Modifier,
) {
    // Color del tema leído en contexto composable (Canvas no es @Composable).
    val colorPista = senaColors().border.copy(alpha = 0.55f)

    Box(modifier = modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val grosor = 9.dp.toPx()
            val inset = grosor / 2f
            val tam = size.minDimension - grosor
            val topLeft = Offset((size.width - tam) / 2f, (size.height - tam) / 2f)

            drawArc(
                color = colorPista,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(tam, tam),
                style = Stroke(width = grosor, cap = StrokeCap.Round),
            )
            if (umbralPct != null) {
                drawArc(
                    color = ParA,
                    startAngle = -90f,
                    sweepAngle = 360f * umbralPct.coerceIn(0, 100) / 100f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(tam, tam),
                    style = Stroke(width = grosor, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = umbralPct?.let { "$it%" } ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = senaColors().text,
            )
            Text(
                text = "UMBRAL",
                style = MaterialTheme.typography.labelSmall,
                color = senaColors().textLight,
                letterSpacing = 1.sp,
            )
        }
    }
}

/**
 * Tarjeta del umbral real + explicación. Aclara que es un valor global del
 * motor y no el porcentaje de la comparación.
 */
@Composable
fun TarjetaUmbral(
    umbralPct: Int?,
    cargando: Boolean,
    error: String? = null,
    modifier: Modifier = Modifier,
) {
    SenaCard(modifier = modifier, elevation = 1.dp) {
        Column {
            Text(
                "Umbral de similitud",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = senaColors().text,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UmbralDial(umbralPct = umbralPct)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    when {
                        cargando -> Text(
                            "Consultando el umbral configurado…",
                            style = MaterialTheme.typography.bodySmall,
                            color = senaColors().textSecondary,
                        )
                        error != null -> Text(
                            "No se pudo consultar el umbral. Se reintentará al abrir el detalle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = senaColors().textSecondary,
                        )
                        umbralPct != null -> Text(
                            "Coincidencias con $umbralPct% o más se consideran relevantes para revisión.",
                            style = MaterialTheme.typography.bodySmall,
                            color = senaColors().textSecondary,
                        )
                        else -> Text(
                            "Configuración aún no disponible.",
                            style = MaterialTheme.typography.bodySmall,
                            color = senaColors().textSecondary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Es el umbral global del motor, no el porcentaje de este par.",
                        style = MaterialTheme.typography.labelSmall,
                        color = senaColors().textMuted,
                    )
                }
            }
        }
    }
}

/**
 * Gráfica A/B de la comparación: B es la referencia aprobada (posición fija)
 * y A se acerca a B a medida que crece el porcentaje real. Al 100 % los puntos
 * coinciden matemáticamente y solo se separan las etiquetas.
 */
@Composable
fun PlanoComparacion(
    porcentaje: Int,
    modifier: Modifier = Modifier,
) {
    val pct = porcentaje.coerceIn(0, 100)
    // Colores del tema leídos fuera del Canvas (no composable).
    val colorEje = senaColors().border
    val colorTick = senaColors().textMuted.copy(alpha = 0.55f)
    val colorFondo = senaColors().backgroundElevated
    val colorTexto = senaColors().text
    val colorTextoLight = senaColors().textLight

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(560f / 210f),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = size.width / 560f
            val ejeY = 128f * s
            val xRef = 430f * s
            val xMin = 110f * s
            val xA = xRef - (100 - pct) / 100f * (xRef - xMin)

            val paint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.MONOSPACE
            }

            // Eje y ticks de 0 a 100 %.
            drawLine(
                color = colorEje,
                start = Offset(80f * s, ejeY),
                end = Offset(460f * s, ejeY),
                strokeWidth = 1.4f * s,
            )
            listOf(0, 25, 50, 75, 100).forEach { tick ->
                val x = xMin + (100 - tick) / 100f * (xRef - xMin)
                drawLine(
                    color = colorTick,
                    start = Offset(x, ejeY - 4f * s),
                    end = Offset(x, ejeY + 4f * s),
                    strokeWidth = 1f * s,
                )
            }

            // Distancia A–B y puntos.
            val efectoGuiones = PathEffect.dashPathEffect(floatArrayOf(5f * s, 5f * s), 0f)
            drawLine(
                color = ParA.copy(alpha = 0.65f),
                start = Offset(xA, ejeY),
                end = Offset(xRef, ejeY),
                strokeWidth = 1.5f * s,
                pathEffect = efectoGuiones,
            )
            drawCircle(color = colorFondo, radius = 11f * s, center = Offset(xA, ejeY))
            drawCircle(
                color = ParA,
                radius = 11f * s,
                center = Offset(xA, ejeY),
                style = Stroke(width = 3.5f * s),
            )
            drawCircle(color = colorFondo, radius = 11f * s, center = Offset(xRef, ejeY))
            drawCircle(
                color = ParB,
                radius = 11f * s,
                center = Offset(xRef, ejeY),
                style = Stroke(width = 3.5f * s),
            )

            // Etiquetas A/B (se separan solo si los puntos coinciden).
            val cerca = abs(xRef - xA) < 16f * s
            paint.textSize = 13f * s
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            if (cerca) {
                paint.color = ParA.toArgb()
                drawContext.canvas.nativeCanvas.drawText("A", xA - 42f * s, 84f * s, paint)
                paint.color = ParB.toArgb()
                drawContext.canvas.nativeCanvas.drawText("B", xRef + 42f * s, 84f * s, paint)
                drawLine(
                    color = ParA.copy(alpha = 0.5f),
                    start = Offset(xA, 116f * s),
                    end = Offset(xA - 42f * s, 88f * s),
                    strokeWidth = 1f * s,
                    pathEffect = efectoGuiones,
                )
                drawLine(
                    color = ParB.copy(alpha = 0.5f),
                    start = Offset(xRef, 116f * s),
                    end = Offset(xRef + 42f * s, 88f * s),
                    strokeWidth = 1f * s,
                    pathEffect = efectoGuiones,
                )
            } else {
                paint.color = ParA.toArgb()
                drawContext.canvas.nativeCanvas.drawText("A", xA, 102f * s, paint)
                paint.color = ParB.toArgb()
                drawContext.canvas.nativeCanvas.drawText("B", xRef, 102f * s, paint)
            }

            // Porcentaje real del par y subtítulo.
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            paint.textSize = 24f * s
            paint.color = colorTexto.toArgb()
            paint.textAlign = Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText("$pct%", 280f * s, 38f * s, paint)
            paint.typeface = Typeface.MONOSPACE
            paint.textSize = 9f * s
            paint.color = colorTextoLight.toArgb()
            drawContext.canvas.nativeCanvas.drawText("SIMILITUD GLOBAL", 280f * s, 57f * s, paint)

            // Etiquetas del eje (0 % a la izquierda, 100 % a la derecha).
            paint.textSize = 11f * s
            paint.color = colorTextoLight.toArgb()
            paint.textAlign = Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText("0%", xMin, 150f * s, paint)
            drawContext.canvas.nativeCanvas.drawText("100%", xRef, 150f * s, paint)
        }
    }
}

/**
 * Tarjeta completa de comparación A/B con el porcentaje real y la ayuda.
 */
@Composable
fun TarjetaComparacion(
    porcentaje: Int,
    modifier: Modifier = Modifier,
) {
    val pct = porcentaje.coerceIn(0, 100)
    SenaCard(modifier = modifier, elevation = 1.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Comparación A/B",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            PlanoComparacion(porcentaje = pct)
            Spacer(Modifier.height(6.dp))
            Text(
                "B = propuesta de referencia aprobada · A = propuesta pendiente. " +
                    "A mayor similitud, menor distancia entre los puntos.",
                style = MaterialTheme.typography.labelSmall,
                color = senaColors().textLight,
            )
        }
    }
}

/** Nota institucional: la similitud no es un veredicto de plagio. */
@Composable
fun NotaSimilitud(modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = senaColors().info.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, senaColors().info.copy(alpha = 0.20f)),
    ) {
        Text(
            "Medida de similitud calculada por el motor de ProyecTwin. " +
                "No es una prueba de plagio ni un veredicto definitivo.",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = senaColors().textSecondary,
            textAlign = TextAlign.Start,
        )
    }
}

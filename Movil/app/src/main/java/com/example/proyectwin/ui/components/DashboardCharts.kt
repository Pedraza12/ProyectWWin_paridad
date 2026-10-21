package com.example.proyectwin.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proyectwin.data.model.ActividadReciente
import com.example.proyectwin.data.model.DashboardMetrics
import com.example.proyectwin.data.model.PropuestasMetricas
import com.example.proyectwin.data.model.PuntoEvolucion
import com.example.proyectwin.data.model.UsuariosMetricas
import com.example.proyectwin.ui.theme.senaColors
import com.example.proyectwin.ui.util.formatearFechaHoraLocal
import com.example.proyectwin.ui.viewmodel.PeriodoMetricas
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Verde esmeralda del acento web (#10b981) para la serie temporal. */
private val VerdeGrafica = Color(0xFF10B981)

// ------------------------------------------------------------------ KPIs

/** Tarjetas superiores del panel admin, en una rejilla 2×2 legible. */
@Composable
fun MetricasKpiGrid(metrics: DashboardMetrics, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                titulo = "Usuarios",
                valor = metrics.usuarios.total,
                icon = Icons.Default.Groups,
                color = senaColors().info,
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                titulo = "Aprendices",
                valor = metrics.usuarios.aprendices,
                icon = Icons.Default.School,
                color = senaColors().success,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                titulo = "Instructores",
                valor = metrics.usuarios.instructores,
                icon = Icons.Default.ChalkboardTeacher,
                color = senaColors().warning,
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                titulo = "Fichas",
                valor = metrics.fichasTotal,
                icon = Icons.Default.Groups,
                color = senaColors().accent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KpiCard(
    titulo: String,
    valor: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    SenaCard(modifier = modifier, elevation = 1.dp) {
        Column {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = valor.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = senaColors().text,
            )
            Text(
                text = titulo,
                style = MaterialTheme.typography.labelSmall,
                color = senaColors().textLight,
            )
        }
    }
}

// ------------------------------------------------------------------ Dona

private data class SegmentoDona(val etiqueta: String, val valor: Int, val color: Color)

@Composable
fun DonaPropuestas(propuestas: PropuestasMetricas, modifier: Modifier = Modifier) {
    val segmentos = listOf(
        SegmentoDona("Borrador", propuestas.borrador, senaColors().textMuted),
        SegmentoDona("En revisión", propuestas.pendiente, senaColors().warning),
        SegmentoDona("Aprobadas", propuestas.aprobado, senaColors().success),
        SegmentoDona("Rechazadas", propuestas.rechazado, senaColors().danger),
    )
    val total = propuestas.total

    SenaCard(modifier = modifier, elevation = 1.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Propuestas por estado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                    modifier = Modifier.weight(1f),
                )
                SenaChip(text = "$total", color = senaColors().green)
            }
            Spacer(Modifier.height(16.dp))
            if (total <= 0) {
                Text(
                    "Sin datos para mostrar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = senaColors().textSecondary,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            val grosor = 22.dp.toPx()
                            val inset = grosor / 2f
                            var inicio = -90f
                            segmentos.forEach { segmento ->
                                if (segmento.valor > 0) {
                                    val barrido = 360f * segmento.valor / total
                                    drawArc(
                                        color = segmento.color,
                                        startAngle = inicio,
                                        sweepAngle = barrido,
                                        useCenter = false,
                                        topLeft = Offset(inset, inset),
                                        size = Size(size.width - grosor, size.height - grosor),
                                        style = Stroke(width = grosor, cap = StrokeCap.Butt),
                                    )
                                    inicio += barrido
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                total.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = senaColors().text,
                            )
                            Text(
                                "propuestas",
                                style = MaterialTheme.typography.labelSmall,
                                color = senaColors().textLight,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        segmentos.forEach { segmento ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(segmento.color, CircleShape),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    segmento.etiqueta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = senaColors().textSecondary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Text(
                                    segmento.valor.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = senaColors().text,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Barras

@Composable
fun CuentasPorRol(usuarios: UsuariosMetricas, modifier: Modifier = Modifier) {
    val filas = listOf(
        Triple("Aprendices", usuarios.aprendices, senaColors().green),
        Triple("Instructores", usuarios.instructores, senaColors().info),
        Triple("Administradores", usuarios.admins, senaColors().warning),
    )
    val maximo = max(1, filas.maxOf { it.second })

    SenaCard(modifier = modifier, elevation = 1.dp) {
        Column {
            Text(
                "Cuentas por rol",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = senaColors().text,
            )
            Spacer(Modifier.height(16.dp))
            if (usuarios.total <= 0) {
                Text(
                    "Sin datos para mostrar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = senaColors().textSecondary,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    filas.forEach { (etiqueta, valor, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                etiqueta,
                                style = MaterialTheme.typography.labelSmall,
                                color = senaColors().textSecondary,
                                modifier = Modifier.width(112.dp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp)
                                    .background(senaColors().borderSoft, CircleShape),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(valor.toFloat() / maximo)
                                        .fillMaxHeight()
                                        .background(color, CircleShape),
                                )
                            }
                            Text(
                                valor.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = senaColors().text,
                                modifier = Modifier.width(40.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Serie temporal

@Composable
fun EvolucionPropuestas(
    puntos: List<PuntoEvolucion>,
    periodo: PeriodoMetricas,
    actualizando: Boolean,
    onPeriodo: (PeriodoMetricas) -> Unit,
    modifier: Modifier = Modifier,
) {
    var seleccionado by remember(puntos) { mutableStateOf<Int?>(null) }
    // Los colores del tema se leen una vez en contexto composable y se usan
    // dentro de Canvas (que no puede invocar @Composable).
    val colorBorde = senaColors().border
    val colorTextoLight = senaColors().textLight
    val colorFondoElevado = senaColors().backgroundElevated

    SenaCard(modifier = modifier, elevation = 1.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Propuestas por mes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = senaColors().text,
                    )
                    Text(
                        "Serie real del periodo seleccionado",
                        style = MaterialTheme.typography.labelSmall,
                        color = senaColors().textLight,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PeriodoMetricas.TRES_MESES to "3 meses",
                    PeriodoMetricas.TREINTA_DIAS to "30 días",
                    PeriodoMetricas.SIETE_DIAS to "7 días",
                ).forEach { (valor, etiqueta) ->
                    SenaChip(
                        text = etiqueta,
                        color = senaColors().green,
                        isSelected = periodo == valor,
                        onClick = { onPeriodo(valor) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (actualizando) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = senaColors().green,
                    trackColor = senaColors().borderSoft,
                )
            }
            Spacer(Modifier.height(8.dp))

            val totalSerie = puntos.sumOf { it.total }
            if (puntos.isEmpty() || totalSerie <= 0) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Sin datos en el período.",
                        style = MaterialTheme.typography.bodySmall,
                        color = senaColors().textSecondary,
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val anchoGrafica = maxWidth
                    val alturaGrafica: Dp = 230.dp
                    val densidad = androidx.compose.ui.platform.LocalDensity.current
                    val padIzq = 34.dp
                    val padDer = 16.dp
                    val padSup = 22.dp
                    val padInf = 28.dp
                    val n = puntos.size
                    val maximo = max(1, puntos.maxOf { it.total })
                    val paso = if (n > 1) (anchoGrafica - padIzq - padDer) / (n - 1) else 0.dp

                    fun xDe(indice: Int): Dp = padIzq + paso * indice
                    fun yDe(valor: Int): Dp =
                        padSup + (alturaGrafica - padSup - padInf) * (1f - valor.toFloat() / maximo)

                    val puntoSeleccionado = seleccionado?.takeIf { it in puntos.indices }

                    Box(modifier = Modifier.fillMaxWidth().height(alturaGrafica)) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(puntos) {
                                    detectTapGestures { offset ->
                                        val anchoPx = anchoGrafica.toPx()
                                        val padIzqPx = padIzq.toPx()
                                        val padDerPx = padDer.toPx()
                                        val pasoPx = if (n > 1) {
                                            (anchoPx - padIzqPx - padDerPx) / (n - 1)
                                        } else {
                                            0f
                                        }
                                        val indice = if (pasoPx <= 0f) {
                                            0
                                        } else {
                                            ((offset.x - padIzqPx) / pasoPx).roundToInt().coerceIn(0, n - 1)
                                        }
                                        seleccionado = if (seleccionado == indice) null else indice
                                    }
                                },
                        ) {
                            val anchoPx = size.width
                            val altoPx = size.height
                            val padIzqPx = padIzq.toPx()
                            val padDerPx = padDer.toPx()
                            val padSupPx = padSup.toPx()
                            val padInfPx = padInf.toPx()
                            val pasoPx = if (n > 1) (anchoPx - padIzqPx - padDerPx) / (n - 1) else 0f
                            fun xPx(i: Int) = padIzqPx + pasoPx * i
                            fun yPx(v: Int) =
                                padSupPx + (altoPx - padSupPx - padInfPx) * (1f - v.toFloat() / maximo)

                            val paint = Paint().apply {
                                isAntiAlias = true
                                typeface = Typeface.MONOSPACE
                                textSize = 10.5.sp.toPx()
                            }

                            // Rejilla horizontal y etiquetas Y (0, mitad, máximo).
                            val ticks = listOf(0, (maximo / 2.0).roundToInt(), maximo)
                                .distinct()
                                .sorted()
                            ticks.forEach { valor ->
                                val y = yPx(valor)
                                drawLine(
                                    color = colorBorde.copy(alpha = 0.55f),
                                    start = Offset(padIzqPx, y),
                                    end = Offset(anchoPx - padDerPx, y),
                                    strokeWidth = 1f,
                                )
                                paint.color = colorTextoLight.toArgb()
                                paint.textAlign = Paint.Align.RIGHT
                                drawContext.canvas.nativeCanvas.drawText(
                                    valor.toString(),
                                    padIzqPx - 6.dp.toPx(),
                                    y + 4.dp.toPx(),
                                    paint,
                                )
                            }

                            // Curva suave (Catmull-Rom → Bézier) y área con degradado.
                            val puntosPx = puntos.mapIndexed { i, p -> Offset(xPx(i), yPx(p.total)) }
                            val curva = CaminoSuave(puntosPx)
                            val area = Path().apply {
                                addPath(curva)
                                lineTo(xPx(n - 1), altoPx - padInfPx)
                                lineTo(xPx(0), altoPx - padInfPx)
                                close()
                            }
                            drawPath(
                                path = area,
                                brush = Brush.verticalGradient(
                                    colors = listOf(VerdeGrafica.copy(alpha = 0.40f), Color.Transparent),
                                    startY = padSupPx,
                                    endY = altoPx - padInfPx,
                                ),
                            )
                            drawPath(
                                path = curva,
                                color = VerdeGrafica,
                                style = Stroke(
                                    width = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )

                            // Etiquetas X (muestreadas si hay muchos días).
                            paint.color = colorTextoLight.toArgb()
                            paint.textAlign = Paint.Align.CENTER
                            val salto = ceil(n / 7.0).toInt().coerceAtLeast(1)
                            puntos.forEachIndexed { i, p ->
                                if (i % salto == 0 || i == n - 1) {
                                    drawContext.canvas.nativeCanvas.drawText(
                                        p.label,
                                        xPx(i),
                                        altoPx - 8.dp.toPx(),
                                        paint,
                                    )
                                }
                            }

                            // Puntos y marca del seleccionado.
                            puntosPx.forEachIndexed { i, punto ->
                                if (seleccionado == i) {
                                    drawLine(
                                        color = VerdeGrafica.copy(alpha = 0.35f),
                                        start = Offset(punto.x, padSupPx),
                                        end = Offset(punto.x, altoPx - padInfPx),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                }
                                drawCircle(
                                    color = colorFondoElevado,
                                    radius = 5.dp.toPx(),
                                    center = punto,
                                )
                                drawCircle(
                                    color = VerdeGrafica,
                                    radius = if (seleccionado == i) 5.dp.toPx() else 3.5.dp.toPx(),
                                    center = punto,
                                )
                            }
                        }

                        if (puntoSeleccionado != null) {
                            val p = puntos[puntoSeleccionado]
                            val anchoTooltip = 190.dp
                            val xBruto = xDe(puntoSeleccionado)
                            val x = xBruto.coerceIn(8.dp, (anchoGrafica - anchoTooltip - 8.dp).coerceAtLeast(8.dp))
                            val yPunto = yDe(p.total)
                            val y = if (yPunto > 110.dp) yPunto - 96.dp else yPunto + 16.dp
                            Surface(
                                modifier = Modifier
                                    .width(anchoTooltip)
                                    .offset(x = x, y = y),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF18181B),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    Color.White.copy(alpha = 0.14f),
                                ),
                                shadowElevation = 8.dp,
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        p.fecha.ifBlank { p.label },
                                        color = Color(0xFFFAFAFA),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        if (p.total == 1) "1 propuesta" else "${p.total} propuestas",
                                        color = Color(0xFFFAFAFA),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    val visibles = p.propuestas.take(4)
                                    if (visibles.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        visibles.forEach { titulo ->
                                            Text(
                                                "· $titulo",
                                                color = Color(0xFFD4D4D8),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    val restantes = p.restantes + max(0, p.propuestas.size - 4)
                                    if (restantes > 0) {
                                        Text(
                                            "y $restantes más…",
                                            color = Color(0xFFA1A1AA),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Toca un punto de la gráfica para ver fecha, cantidad y propuestas.",
                    style = MaterialTheme.typography.labelSmall,
                    color = senaColors().textLight,
                )
            }
        }
    }
}

/** Convierte una polilínea en un camino con curvas Catmull-Rom → Bézier. */
private fun CaminoSuave(puntos: List<Offset>): Path {
    val path = Path()
    if (puntos.isEmpty()) return path
    path.moveTo(puntos.first().x, puntos.first().y)
    if (puntos.size == 1) return path

    for (i in 0 until puntos.size - 1) {
        val p0 = puntos.getOrElse(i - 1) { puntos[i] }
        val p1 = puntos[i]
        val p2 = puntos[i + 1]
        val p3 = puntos.getOrElse(i + 2) { p2 }

        val cp1 = Offset(
            p1.x + (p2.x - p0.x) / 6f,
            p1.y + (p2.y - p0.y) / 6f,
        )
        val cp2 = Offset(
            p2.x - (p3.x - p1.x) / 6f,
            p2.y - (p3.y - p1.y) / 6f,
        )
        path.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
    }
    return path
}

// ------------------------------------------------------------------ Actividad

@Composable
fun ActividadRecienteCard(
    actividad: List<ActividadReciente>,
    modifier: Modifier = Modifier,
    onProyectoClick: ((Int) -> Unit)? = null,
) {
    SenaCard(modifier = modifier, elevation = 1.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Actividad reciente",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = senaColors().text,
                    modifier = Modifier.weight(1f),
                )
                SenaChip(text = actividad.size.toString(), color = senaColors().info)
            }
            Spacer(Modifier.height(12.dp))
            if (actividad.isEmpty()) {
                Text(
                    "Sin actividad reciente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = senaColors().textSecondary,
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    actividad.forEachIndexed { indice, item ->
                        val color = colorActividad(item.titulo)
                        val clickable = onProyectoClick != null && item.proyectoId != null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (clickable) {
                                        Modifier.clickable { onProyectoClick?.invoke(item.proyectoId!!) }
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .background(color, CircleShape),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    textoAccion(item.titulo),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = senaColors().text,
                                )
                                val detalle = listOf(item.meta, item.usuario)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                if (detalle.isNotBlank()) {
                                    Text(
                                        detalle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = senaColors().textLight,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                formatearFechaHoraLocal(item.fecha),
                                style = MaterialTheme.typography.labelSmall,
                                color = senaColors().textMuted,
                            )
                        }
                        if (indice < actividad.lastIndex) {
                            HorizontalDivider(color = senaColors().borderSoft)
                        }
                    }
                }
            }
        }
    }
}

/** Formato legible del nombre técnico de la acción (no inventa contenido). */
private fun textoAccion(accion: String): String =
    accion.replace('_', ' ')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

@Composable
private fun colorActividad(accion: String): Color {
    val texto = accion.lowercase(Locale.getDefault())
    return when {
        texto.contains("rechaz") || texto.contains("elimin") || texto.contains("anul") -> senaColors().danger
        texto.contains("aprob") || texto.contains("cread") -> senaColors().success
        texto.contains("similitud") || texto.contains("revis") -> senaColors().warning
        else -> senaColors().info
    }
}

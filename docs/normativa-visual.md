# Normativa visual compartida — ProyecTwin (Web + Android)

> Documento breve y operativo. Fuente de verdad de la identidad: `frontend/src/tokens/index.css`.
> Android refleja los mismos valores semánticos en `ui/theme/Color.kt` (light/dark = tema claro/oscuro de la web).

## 1. Paleta (mismos significados en ambas plataformas)

| Rol | Tema claro (web / Android) | Tema oscuro (web / Android) |
|---|---|---|
| Primario (CTA, foco) | `#2a7c00` (g-700) | `#22b949` → hover/strong `#31d160` |
| Superficie | `#ffffff` | `#1e1e1e` (alt `#2a2a2a`) |
| Fondo | `#f7faf3` | `#0a0a0a` |
| Texto / 2.º / 3.º | `#171717` / `#666` / `#666` | `#fff` / `#d0d0d0` / `#a0a0a0` |
| Borde | `#eaeaea` | blanco 14% |
| Éxito | g-700 | `#4ce07a` |
| Advertencia | `#f59e0b` | `#ffd07a` |
| Peligro | `#dc2626` | `#ffa3a3` |
| Info (petróleo) | `#0e7490` | `#5cd4f2` |
| Cabecera institucional | degradado g-700→g-800 | `#12261b` |
| Similitud baja/media/alta | verde / ámbar / rojo (mismos tokens `--sim-*`) | ídem en versión oscura |

Regla: los estados nunca dependen solo del color (siempre acompañan icono o texto).

## 2. Espaciado

Escala única de 4 px: `2, 4, 6, 8, 12, 16, 20, 24, 32, 40, 48`.
- Web: `var(--sp-*)`. Android: múltiplos de 4 dp (preferir 8/12/16/20/24; evitar 10, 14, 18, 22).
- Contenido de tarjeta: 20–24. Separación entre secciones: 24. Gap entre botones de una fila: 8–12.

## 3. Radios

Web `--r-sm 6 / --r-md 8 / --r-lg 8 / --r-xl 12 / full`.
Android `Shapes`: small 6, medium 8, large 12, extraLarge 16; chips y pills = full.

## 4. Tipografía (mapeo web → Android)

| Uso | Web | Android |
|---|---|---|
| Título de página | 24 (display) | headlineMedium/24 |
| Título de sección | 20 | titleLarge/20 |
| Título de tarjeta | 16 (bold) | titleMedium/16 |
| Cuerpo | 14 | bodyMedium/14 |
| Secundario | 13–12 | bodySmall/13, labelMedium/12 |
| Micro-etiquetas | 12 min | labelSmall/12 |

Web: display `Sora`, cuerpo `Inter`, datos `JetBrains Mono` (clase `.mono`).
Android: fuente del sistema; los datos numéricos usan `FontFamily.Monospace` cuando aplique.
Nunca bajar de 12 sp/11 px en textos de interfaz.

## 5. Botones y acciones

- Jerarquía idéntica: **primario** (acción principal, sólido verde) → **secundario** (contorno) → **terciario/ghost** (texto) → **peligro** (rojo, siempre separado del grupo).
- Altura mínima: web sm 36 / md 40 / lg 44 px; Android `heightIn(min = 48.dp)` o 44 dp en barras densas. Presionado/deshabilitado/carga con los mismos estados.
- Filas de acciones: envuelven (web `flex-wrap`, Android `FlowRow`/weight/2 filas). Máximo 1 acción primaria por pantalla; secundarias agrupadas u ocultas tras menú.
- Acciones destructivas: variante danger + confirmación.

## 6. Formularios

- Orden: etiqueta → campo → ayuda/error. Obligatorio marcado con `*` y `required`.
- Error bajo el campo, con `--c-danger`/`senaColors().danger`; nunca solo color.
- Espaciado vertical 16; acciones Guardar (primario) + Cancelar (secundario) al final, alineadas; en móvil el CTA principal a ancho completo.
- Formularios largos con scroll; diálogos con scroll interno y dentro del viewport.

## 7. Contenedores, tablas y listas

- Tarjeta: fondo superficie, borde 1 px, radio 12, sombra `--sh-card`; padding 20–24.
- Web: tablas con `DataTable` (scroll horizontal + modo tarjeta ≤640 px); la columna de acciones queda sticky (desactivable).
- Android: listas como tarjetas (LazyColumn) con la misma información, jerarquía, acciones y términos que la tabla web.
- Textos largos: truncar con elipsis y `maxLines` (2 en títulos de tarjeta); correos/URLs con `overflow-wrap:anywhere`.

## 8. Estados

Cargando (skeleton/spinner centrado) · Error (mensaje + botón reintentar) · Vacío (título + invitación a actuar) · Éxito (alerta breve) — con el mismo texto y orden en ambas plataformas.

## 9. Accesibilidad

- Objetivo táctil: Android ≥48 dp; web ≥44 px (36 px mínimo en controles densos de tabla, con padding).
- Foco visible (`:focus-visible` / indicador M3), contraste AA, `contentDescription`/aria equivalentes, `prefers-reduced-motion` respetado, soporte de tamaño de fuente ampliado.

## 10. Adaptaciones móviles justificadas (no son divergencias)

- Navegación propia de Android (bottom bar/back), barras del sistema e insets.
- Tabla web → tarjetas en Android/móvil.
- Diálogos/selectores nativos de la plataforma.
- CTA principal a ancho completo cuando el ancho no permite fila de botones.
- Densidad de tablas reducida en Android manteniendo la misma información y acciones.

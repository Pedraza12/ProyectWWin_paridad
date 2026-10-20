import { useEffect, useId, useRef, useState } from 'react'
import s from './Charts.module.css'

// Gráficas del dashboard con el tema visual de la app (sin cyberpunk).

export function BarChart({ data = [], ariaLabel = 'Gráfica de barras' }) {
  const max = Math.max(1, ...data.map((d) => Number(d.value) || 0))
  if (!data.length) return <p className={s.empty}>Sin datos para mostrar.</p>

  const resumen = data.map((d) => `${d.label}: ${d.value}`).join(', ')

  return (
    <ul className={s.bars} role="img" aria-label={`${ariaLabel}. ${resumen}`}>
      {data.map((d) => (
        <li key={d.label} className={s.barRow}>
          <span className={s.barLabel}>{d.label}</span>
          <span className={s.barTrack}>
            <span
              className={s.barFill}
              style={{
                width: `${((Number(d.value) || 0) / max) * 100}%`,
                background: d.color || 'var(--c-primary)',
              }}
            />
          </span>
          <span className={`mono ${s.barValue}`}>{d.value}</span>
        </li>
      ))}
    </ul>
  )
}

export function DonutChart({
  data = [],
  size = 176,
  thickness = 22,
  centro = '',
  ariaLabel = 'Distribución',
}) {
  const total = data.reduce((acc, d) => acc + (Number(d.value) || 0), 0)
  if (!total) return <p className={s.empty}>Sin datos para mostrar.</p>

  const radio = (size - thickness) / 2
  const circunferencia = 2 * Math.PI * radio
  const cx = size / 2

  // Segmentos calculados de forma pura (sin mutar variables durante el render).
  const segmentos = data.map((d, i) => {
    const valor = Number(d.value) || 0
    const previo = data
      .slice(0, i)
      .reduce((acc, x) => acc + (Number(x.value) || 0), 0)
    return {
      ...d,
      valor,
      largo: (valor / total) * circunferencia,
      offset: -((previo / total) * circunferencia),
    }
  })

  const resumen = data.map((d) => `${d.label}: ${d.value}`).join(', ')

  return (
    <div className={s.donutWrap}>
      <div className={s.donutBox} style={{ width: size, height: size }}>
        <svg
          width={size}
          height={size}
          viewBox={`0 0 ${size} ${size}`}
          role="img"
          aria-label={`${ariaLabel}. ${resumen}`}
        >
          <circle
            className={s.donutTrack}
            cx={cx}
            cy={cx}
            r={radio}
            fill="none"
            strokeWidth={thickness}
          />
          {segmentos.map((segmento) => {
            if (!segmento.valor) return null
            return (
              <circle
                key={segmento.label}
                cx={cx}
                cy={cx}
                r={radio}
                fill="none"
                stroke={segmento.color || 'var(--c-primary)'}
                strokeWidth={thickness}
                strokeDasharray={`${segmento.largo} ${circunferencia - segmento.largo}`}
                strokeDashoffset={segmento.offset}
                transform={`rotate(-90 ${cx} ${cx})`}
              />
            )
          })}
        </svg>
        <div className={s.donutCenter} aria-hidden="true">
          <span className={s.donutValue}>{total}</span>
          <span className={s.donutLabel}>{centro}</span>
        </div>
      </div>
      <ul className={s.legend}>
        {data.map((d) => (
          <li key={d.label} className={s.legendItem}>
            <span
              className={s.legendDot}
              style={{ background: d.color || 'var(--c-primary)' }}
              aria-hidden="true"
            />
            <span className={s.legendText}>{d.label}</span>
            <span className={s.legendValue}>{d.value}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

// Curva suave (Catmull-Rom → Bézier) para la gráfica de área.
function curvaSuave(puntos) {
  if (puntos.length === 0) return ''
  if (puntos.length === 1) return `M ${puntos[0].x} ${puntos[0].y}`
  let d = `M ${puntos[0].x} ${puntos[0].y}`
  for (let i = 0; i < puntos.length - 1; i++) {
    const p0 = puntos[i - 1] || puntos[i]
    const p1 = puntos[i]
    const p2 = puntos[i + 1]
    const p3 = puntos[i + 2] || p2
    const cp1x = p1.x + (p2.x - p0.x) / 6
    const cp1y = p1.y + (p2.y - p0.y) / 6
    const cp2x = p2.x - (p3.x - p1.x) / 6
    const cp2y = p2.y - (p3.y - p1.y) / 6
    d += ` C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${p2.x} ${p2.y}`
  }
  return d
}

/**
 * Gráfica de área compacta e interactiva: curva suave, degradado, tooltip con
 * fecha, total y nombres de propuestas del periodo (datos reales del backend).
 */
export function LineChart({ data = [], ariaLabel = 'Evolución mensual' }) {
  const wrapRef = useRef(null)
  const [ancho, setAncho] = useState(640)
  const [activo, setActivo] = useState(null)
  const gid = useId().replace(/[^a-zA-Z0-9]/g, '')

  useEffect(() => {
    const el = wrapRef.current
    if (!el || typeof ResizeObserver === 'undefined') return undefined
    const ro = new ResizeObserver(([entrada]) => {
      const w = Math.round(entrada.contentRect.width)
      if (w > 0) setAncho(w)
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const valores = data.map((d) => Number(d.value) || 0)
  if (!data.length || valores.every((v) => v === 0)) {
    return <p className={s.empty}>Sin datos en el período.</p>
  }

  const W = Math.max(320, ancho)
  const H = 230
  const pad = { top: 22, right: 16, bottom: 28, left: 34 }
  const max = Math.max(1, ...valores)
  const paso = data.length > 1 ? (W - pad.left - pad.right) / (data.length - 1) : 0
  const x = (i) => pad.left + i * paso
  const y = (v) => pad.top + (1 - v / max) * (H - pad.top - pad.bottom)

  const puntos = valores.map((v, i) => ({ x: x(i), y: y(v) }))
  const linea = curvaSuave(puntos)
  const area = `${linea} L ${x(valores.length - 1)} ${H - pad.bottom} L ${pad.left} ${H - pad.bottom} Z`
  const ticks = [0, 0.5, 1]
    .map((f) => ({ valor: Math.round(max * f), y: y(max * f) }))
    .filter((t, i, arr) => arr.findIndex((u) => u.valor === t.valor) === i)

  // En 30/7 días no se muestran todas las etiquetas para evitar solapamientos.
  const salto = data.length > 12 ? Math.ceil(data.length / 8) : 1
  const resumen = data.map((d) => `${d.label}: ${d.value}`).join(', ')
  const puntoActivo = activo != null ? data[activo] : null

  const tooltipX = activo != null ? Math.min(Math.max(x(activo), 100), W - 100) : 0

  return (
    <div className={s.lineWrap} ref={wrapRef} role="img" aria-label={`${ariaLabel}. ${resumen}`}>
      <svg viewBox={`0 0 ${W} ${H}`} className={s.lineSvg} aria-hidden="true">
        <defs>
          <linearGradient id={`area-${gid}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#10b981" stopOpacity="0.4" />
            <stop offset="1" stopColor="#10b981" stopOpacity="0" />
          </linearGradient>
        </defs>

        {ticks.map((t) => (
          <g key={t.valor}>
            <line x1={pad.left} x2={W - pad.right} y1={t.y} y2={t.y} className={s.gridLine} />
            <text x={pad.left - 8} y={t.y + 4} textAnchor="end" className={s.axisText}>
              {t.valor}
            </text>
          </g>
        ))}

        <path d={area} fill={`url(#area-${gid})`} />
        <path d={linea} className={s.linePath} />

        {puntos.map((punto, i) => (
          <g key={data[i].clave || i}>
            <circle cx={punto.x} cy={punto.y} r="3.5" className={s.lineDot} />
            <circle
              cx={punto.x}
              cy={punto.y}
              r="12"
              className={s.lineHit}
              tabIndex={0}
              role="button"
              aria-label={`${data[i].fecha || data[i].label}: ${valores[i]} propuesta${valores[i] !== 1 ? 's' : ''}`}
              onMouseEnter={() => setActivo(i)}
              onMouseLeave={() => setActivo(null)}
              onFocus={() => setActivo(i)}
              onBlur={() => setActivo(null)}
              onPointerDown={() => setActivo(i)}
            />
          </g>
        ))}

        {data.map((d, i) => (
          (i % salto === 0 || i === data.length - 1) && (
            <text key={d.clave || d.label} x={x(i)} y={H - 8} textAnchor="middle" className={s.axisText}>
              {d.label}
            </text>
          )
        ))}
      </svg>

      {puntoActivo && (
        <div className={s.tooltip} style={{ left: `${tooltipX}px`, top: `${y(valores[activo])}px` }}>
          <p className={s.tipFecha}>{puntoActivo.fecha || puntoActivo.label}</p>
          <p className={s.tipTotal}>
            {valores[activo]} propuesta{valores[activo] !== 1 ? 's' : ''}
          </p>
          {Array.isArray(puntoActivo.propuestas) && puntoActivo.propuestas.length > 0 && (
            <ul className={s.tipLista}>
              {puntoActivo.propuestas.slice(0, 4).map((titulo, i) => (
                <li key={`${titulo}-${i}`}>{titulo}</li>
              ))}
            </ul>
          )}
          {puntoActivo.restantes > 0 && (
            <p className={s.tipMas}>y {puntoActivo.restantes} más…</p>
          )}
        </div>
      )}
    </div>
  )
}

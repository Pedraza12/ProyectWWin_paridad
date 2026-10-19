import s from './Charts.module.css'
import { CYBER_COLORS } from './palette'

// Gráficas SVG livianas (sin librerías externas), con estética de consola
// tecnológica: fondo oscuro, tipografía mono, neón sutil y datos reales.

export function BarChart({ data = [], ariaLabel = 'Gráfica de barras' }) {
  const max = Math.max(1, ...data.map((d) => Number(d.value) || 0))
  if (!data.length) return <p className={`${s.cyber} ${s.empty}`}>Sin datos para mostrar.</p>

  const resumen = data.map((d) => `${d.label}: ${d.value}`).join(', ')

  return (
    <ul className={`${s.cyber} ${s.bars}`} role="img" aria-label={`${ariaLabel}. ${resumen}`}>
      {data.map((d) => (
        <li key={d.label} className={s.barRow}>
          <span className={s.barLabel}>{d.label}</span>
          <span className={s.barTrack}>
            <span
              className={s.barFill}
              style={{
                width: `${((Number(d.value) || 0) / max) * 100}%`,
                background: d.color || CYBER_COLORS.cyan,
                boxShadow: `0 0 10px ${(d.color || CYBER_COLORS.cyan)}55`,
              }}
            />
          </span>
          <span className={s.barValue}>{d.value}</span>
        </li>
      ))}
    </ul>
  )
}

export function DonutChart({
  data = [],
  size = 158,
  thickness = 18,
  centro = '',
  ariaLabel = 'Distribución',
}) {
  const total = data.reduce((acc, d) => acc + (Number(d.value) || 0), 0)
  if (!total) return <p className={`${s.cyber} ${s.empty}`}>Sin datos para mostrar.</p>

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
    <div className={`${s.cyber} ${s.donutWrap}`}>
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
                stroke={segmento.color || CYBER_COLORS.cyan}
                strokeWidth={thickness}
                strokeDasharray={`${segmento.largo} ${circunferencia - segmento.largo}`}
                strokeDashoffset={segmento.offset}
                transform={`rotate(-90 ${cx} ${cx})`}
                style={{ filter: `drop-shadow(0 0 4px ${segmento.color || CYBER_COLORS.cyan}66)` }}
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
              style={{
                background: d.color || CYBER_COLORS.cyan,
                boxShadow: `0 0 6px ${d.color || CYBER_COLORS.cyan}`,
              }}
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

export function LineChart({ data = [], ariaLabel = 'Evolución mensual' }) {
  const valores = data.map((d) => Number(d.value) || 0)
  if (!data.length || valores.every((v) => v === 0)) {
    return <p className={`${s.cyber} ${s.empty}`}>Sin datos en el período.</p>
  }

  // Lienzo compacto (aspecto ~3:1) para que la gráfica no domine el panel.
  const W = 600
  const H = 190
  const pad = { top: 12, right: 10, bottom: 26, left: 30 }
  const max = Math.max(1, ...valores)
  const paso = data.length > 1 ? (W - pad.left - pad.right) / (data.length - 1) : 0
  const x = (i) => pad.left + i * paso
  const y = (v) => pad.top + (1 - v / max) * (H - pad.top - pad.bottom)
  const puntos = valores.map((v, i) => `${x(i)},${y(v)}`).join(' ')
  const area = `${pad.left},${H - pad.bottom} ${puntos} ${x(valores.length - 1)},${H - pad.bottom}`
  const ticks = [0, 0.5, 1].map((f) => ({ valor: Math.round(max * f), y: y(max * f) }))
  const resumen = data.map((d) => `${d.label}: ${d.value}`).join(', ')

  return (
    <div className={`${s.cyber} ${s.lineWrap}`} role="img" aria-label={`${ariaLabel}. ${resumen}`}>
      <svg viewBox={`0 0 ${W} ${H}`} className={s.lineSvg} aria-hidden="true">
        {ticks.map((t) => (
          <g key={t.valor}>
            <line x1={pad.left} x2={W - pad.right} y1={t.y} y2={t.y} className={s.gridLine} />
            <text x={pad.left - 6} y={t.y + 3} textAnchor="end" className={s.axisText}>
              {t.valor}
            </text>
          </g>
        ))}
        <polygon points={area} className={s.lineArea} />
        <polyline points={puntos} className={s.linePath} />
        {valores.map((v, i) => (
          <circle key={data[i].mes || i} cx={x(i)} cy={y(v)} r="3.5" className={s.lineDot}>
            <title>{`${data[i].label}: ${v}`}</title>
          </circle>
        ))}
        {data.map((d, i) => (
          <text key={d.mes || d.label} x={x(i)} y={H - 8} textAnchor="middle" className={s.axisText}>
            {d.label}
          </text>
        ))}
      </svg>
    </div>
  )
}

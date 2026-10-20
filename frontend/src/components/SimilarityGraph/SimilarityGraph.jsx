import { useId } from 'react'
import s from './SimilarityGraph.module.css'

/**
 * Gráfica compacta de la comparación: nodos A y B unidos por un enlace y el
 * porcentaje global real del motor en el centro. Se identifican solo con las
 * letras A/B (los títulos completos viven en las tarjetas y el detalle).
 */
export default function SimilarityGraph({ porcentaje = 0, etiqueta = 'Índice de similitud' }) {
  const pct = Math.max(0, Math.min(100, Math.round(Number(porcentaje) || 0)))
  const uid = useId().replace(/[^a-zA-Z0-9]/g, '')
  const gradId = `sg-${uid}`
  const R = 42
  const CIRC = 2 * Math.PI * R
  const pasos = 8

  return (
    <div
      className={s.wrap}
      role="img"
      aria-label={`${etiqueta}: ${pct}% entre las propuestas A y B`}
    >
      <svg viewBox="0 0 560 150" className={s.svg} aria-hidden="true">
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="1" y2="0">
            <stop offset="0" stopColor="#39ff88" />
            <stop offset=".5" stopColor="#22e0ff" />
            <stop offset="1" stopColor="#ff3fd8" />
          </linearGradient>
        </defs>

        {/* Marcas técnicas del enlace A—B */}
        {Array.from({ length: pasos + 1 }, (_, i) => {
          const x = 96 + ((464 - 96) / pasos) * i
          return <line key={i} x1={x} y1={72} x2={x} y2={88} className={s.tick} />
        })}

        <line
          x1="96"
          y1="80"
          x2="464"
          y2="80"
          className={s.track}
          stroke={`url(#${gradId})`}
        />

        {/* Nodo A (izquierda) */}
        <circle cx="60" cy="80" r="30" className={s.nodeA} />
        <text x="60" y="80" textAnchor="middle" dominantBaseline="central" className={s.letterA}>
          A
        </text>

        {/* Nodo B (derecha) */}
        <circle cx="500" cy="80" r="30" className={s.nodeB} />
        <text x="500" y="80" textAnchor="middle" dominantBaseline="central" className={s.letterB}>
          B
        </text>

        {/* Indicador central: único porcentaje de la comparación */}
        <circle cx="280" cy="80" r={R} className={s.gaugeTrack} />
        <circle
          cx="280"
          cy="80"
          r={R}
          className={s.gaugeArc}
          stroke={`url(#${gradId})`}
          strokeDasharray={CIRC}
          strokeDashoffset={CIRC * (1 - pct / 100)}
          transform="rotate(-90 280 80)"
        />
        <text x="280" y="74" textAnchor="middle" dominantBaseline="central" className={s.pct}>
          {pct}%
        </text>
        <text x="280" y="96" textAnchor="middle" className={s.pctLabel}>
          similitud
        </text>
      </svg>
    </div>
  )
}

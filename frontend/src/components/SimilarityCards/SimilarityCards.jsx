import s from './SimilarityCards.module.css'

/**
 * TARJETA A — Umbral real configurado del motor (`MotorConfig.umbral`).
 * No es el porcentaje de similitud del par: es el valor de referencia con el
 * que el sistema decide qué coincidencias son relevantes.
 */
export function ThresholdDial({ umbralPct = 30 }) {
  const pct = Math.max(0, Math.min(100, Math.round(Number(umbralPct) || 0)))
  const R = 46
  const CIRC = 2 * Math.PI * R

  return (
    <div className={s.dialWrap} role="img" aria-label={`Umbral de similitud configurado: ${pct}%`}>
      <svg viewBox="0 0 140 140" className={s.dialSvg} aria-hidden="true">
        <circle cx="70" cy="70" r={R} className={s.dialTrack} />
        <circle
          cx="70"
          cy="70"
          r={R}
          className={s.dialArc}
          strokeDasharray={CIRC}
          strokeDashoffset={CIRC * (1 - pct / 100)}
          transform="rotate(-90 70 70)"
        />
        <text x="70" y="64" textAnchor="middle" dominantBaseline="central" className={s.dialValue}>
          {pct}%
        </text>
        <text x="70" y="88" textAnchor="middle" className={s.dialLabel}>
          umbral
        </text>
      </svg>
      <p className={s.dialHelp}>
        Coincidencias con <strong>{pct}%</strong> o más se consideran relevantes para revisión.
      </p>
    </div>
  )
}

/**
 * TARJETA B — Plano A/B: B es la propuesta de referencia y A la pendiente.
 * La distancia entre ambos puntos es proporcional a (100 − similitud): al 100%
 * los puntos coinciden; al 0% quedan en extremos opuestos. El porcentaje es el
 * valor real del motor.
 */
export function SimilarityPlane({ porcentaje = 0 }) {
  const pct = Math.max(0, Math.min(100, Math.round(Number(porcentaje) || 0)))

  const W = 560
  const H = 210
  const ejeY = 128
  const xRef = 430 // B: referencia (100%)
  const xMin = 110 // A: distancia máxima (0%)
  const xA = xRef - ((100 - pct) / 100) * (xRef - xMin)

  // Si ambos puntos coinciden (similitud ≈100%), se separan SOLO las etiquetas
  // con guías; la posición matemática de los puntos no se altera.
  const superpuestos = Math.abs(xRef - xA) < 16
  const etiquetaA = superpuestos ? { x: xA - 42, y: ejeY - 44 } : { x: xA, y: ejeY - 26 }
  const etiquetaB = superpuestos ? { x: xRef + 42, y: ejeY - 44 } : { x: xRef, y: ejeY - 26 }

  return (
    <div
      className={s.planeWrap}
      role="img"
      aria-label={`Comparación A/B: ${pct}% de similitud. A es la propuesta pendiente y B la propuesta de referencia.`}
    >
      <svg viewBox={`0 0 ${W} ${H}`} className={s.planeSvg} aria-hidden="true">
        {/* Eje de referencia: 0% (máxima distancia) → 100% (coincidencia) */}
        <line x1={xMin - 30} y1={ejeY} x2={xRef + 30} y2={ejeY} className={s.eje} />
        {[0, 25, 50, 75, 100].map((t) => {
          const x = xMin + ((100 - t) / 100) * (xRef - xMin)
          return <line key={t} x1={x} y1={ejeY - 4} x2={x} y2={ejeY + 4} className={s.tick} />
        })}
        <text x={xMin} y={ejeY + 22} textAnchor="middle" className={s.axis}>0%</text>
        <text x={xRef} y={ejeY + 22} textAnchor="middle" className={s.axis}>100%</text>

        {/* Conexión A—B: su longitud representa la distancia real */}
        <line x1={xA} y1={ejeY} x2={xRef} y2={ejeY} className={s.distancia} />

        {superpuestos && (
          <>
            <line x1={xA} y1={ejeY - 12} x2={etiquetaA.x} y2={etiquetaA.y + 8} className={s.guia} />
            <line x1={xRef} y1={ejeY - 12} x2={etiquetaB.x} y2={etiquetaB.y + 8} className={s.guia} />
          </>
        )}

        <circle cx={xRef} cy={ejeY} r="11" className={s.puntoB} />
        <circle cx={xA} cy={ejeY} r="11" className={s.puntoA} />

        <text x={etiquetaA.x} y={etiquetaA.y} textAnchor="middle" className={s.etiquetaA}>A</text>
        <text x={etiquetaB.x} y={etiquetaB.y} textAnchor="middle" className={s.etiquetaB}>B</text>

        {/* Único porcentaje de la tarjeta (resultado real del par) */}
        <text x={W / 2} y="38" textAnchor="middle" className={s.pct}>{pct}%</text>
        <text x={W / 2} y="57" textAnchor="middle" className={s.pctLabel}>similitud global</text>
      </svg>
      <p className={s.planeHelp}>
        <strong>B</strong> = propuesta de referencia aprobada · <strong>A</strong> = propuesta pendiente.
        A mayor similitud, menor distancia entre los puntos.
      </p>
    </div>
  )
}

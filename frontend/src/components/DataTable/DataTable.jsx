import { Fragment } from 'react'
import s from './DataTable.module.css'

// Tabla densa de consola: header sticky + data-label para modo tarjetas en móvil.
// columns: [{ key, header, render?(row), align?: 'start'|'center'|'end' }]
// expandRender + expandido permiten una fila expandible (detalle adicional).
export default function DataTable({ columns = [], rows = [], keyOf, empty = 'Sin registros.', ariaLabel, caption, stickyLast = true, className = '', expandRender = null, expandido = null }) {
  if (rows.length === 0) {
    return <p className={`${s.empty} ${className}`}>{empty}</p>
  }
  return (
    <div className={`${s.scroll} ${className}`}>
      <table className={s.table} aria-label={ariaLabel}>
        {caption ? <caption className={s.caption}>{caption}</caption> : null}
        <thead>
          <tr>
            {columns.map((c, i) => (
              <th key={c.key} scope="col" className={`${s[`align-${c.align || 'start'}`]}${stickyLast && i === columns.length - 1 ? ` ${s.stickyCol}` : ''}`}>{c.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const clave = keyOf ? keyOf(row) : row.id
            const abierto = Boolean(expandRender) && expandido === clave
            return (
              <Fragment key={clave}>
                <tr>
                  {columns.map((c, i) => (
                    <td key={c.key} data-label={c.header} className={`${s[`align-${c.align || 'start'}`]}${stickyLast && i === columns.length - 1 ? ` ${s.stickyCol}` : ''}`}>
                      {c.render ? c.render(row) : row[c.key]}
                    </td>
                  ))}
                </tr>
                {abierto && (
                  <tr className={s.expandRow}>
                    <td colSpan={columns.length}>{expandRender(row)}</td>
                  </tr>
                )}
              </Fragment>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

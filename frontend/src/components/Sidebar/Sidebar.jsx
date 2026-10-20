import { Fragment } from 'react'
import { NavLink, matchPath, useLocation } from 'react-router-dom'
import s from './Sidebar.module.css'

function CloseIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
      <path d="M18 6 6 18M6 6l12 12" />
    </svg>
  )
}

// Navegación lateral: iconos + etiquetas, con grupos visuales opcionales.
export default function Sidebar({ isOpen = false, onClose, role = '', links = [] }) {
  const { pathname } = useLocation()
  const items = links
  const activoExtra = (link) =>
    (link.activeFor || []).some((p) => matchPath({ path: `${p}/*`, end: false }, pathname))
  return (
    <>
      {isOpen && <div className={s.overlay} onClick={onClose} aria-hidden="true" />}

      <aside className={`${s.sidebar} ${isOpen ? s.isOpen : ''}`} aria-label={`Navegación principal (${role})`}>
        <button type="button" className={s.closeBtn} onClick={onClose} aria-label="Cerrar menú">
          <CloseIcon />
        </button>

        <nav className={s.menu}>
          {items.map((link, index) => {
            const mostrarGrupo = Boolean(link.group) && (index === 0 || items[index - 1].group !== link.group)
            return (
              <Fragment key={link.to}>
                {mostrarGrupo && <span className={s.groupLabel}>{link.group}</span>}
                <NavLink
                  to={link.to}
                  end
                  viewTransition
                  onClick={onClose}
                  className={({ isActive }) =>
                    isActive || activoExtra(link) ? `${s.link} ${s.linkActive}` : s.link
                  }
                >
                  <span className={s.icon} aria-hidden="true">{link.icon}</span>
                  <span>{link.label}</span>
                </NavLink>
              </Fragment>
            )
          })}
        </nav>
      </aside>
    </>
  )
}

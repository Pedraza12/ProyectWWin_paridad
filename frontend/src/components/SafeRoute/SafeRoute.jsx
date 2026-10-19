import ErrorBoundary from '../ErrorBoundary/ErrorBoundary'
import Actions from '../Actions/Actions'
import { useLocation, useNavigate } from 'react-router-dom'
import { WarningCircle, ArrowCounterClockwise, Bug } from 'phosphor-react'
import { RUTA_POR_ROL, RUTA_SOPORTE_POR_ROL } from '../../constants/routes'
import s from './SafeRoute.module.css'

function derivarRol(pathname) {
  if (pathname.startsWith('/instructor')) return 'instructor'
  if (pathname.startsWith('/admin')) return 'admin'
  return 'aprendiz'
}

export default function SafeRoute({ children }) {
  const location = useLocation()
  const navigate = useNavigate()
  const role = derivarRol(location.pathname)
  const home = RUTA_POR_ROL[role] || '/login'

  return (
    <ErrorBoundary
      fallbackRender={() => (
        <div className={s.wrapper}>
          <div className={s.card}>
            <WarningCircle size={56} weight="light" className={s.icon} />
            <h2 className={s.title}>Algo salió mal</h2>
            <p className={s.text}>
              Ocurrió un error inesperado en esta sección. Tus datos están a salvo — puedes volver al inicio o
              avisarnos de lo ocurrido.
            </p>
            <Actions align="center" wrap className={s.actions}>
              <button type="button" className={s.primary} onClick={() => navigate(home)}>
                <ArrowCounterClockwise size={16} /> Volver al dashboard
              </button>
              <button type="button" className={s.secondary} onClick={() => navigate(RUTA_SOPORTE_POR_ROL[role] || home)}>
                <Bug size={16} /> Solicitar soporte
              </button>
            </Actions>
          </div>
        </div>
      )}
    >
      {children}
    </ErrorBoundary>
  )
}

import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../contexts/AuthContext'
import { useApi } from '../../lib/useApi'
import { usuarios } from '../../lib/recursos'
import { iniciales } from '../../utils/helpers'
import Tooltip from '../Tooltip/Tooltip'
import s from './TopNav.module.css'
import { ROL_LABEL } from '../../constants/badgeVariants'
import { RUTA_ALERTAS_POR_ROL } from '../../constants/routes'

const RUTA_CREAR = {
  aprendiz: '/aprendiz/propuestas?crear=1',
  instructor: '/instructor/fichas?crear=1',
  admin: '/admin/usuarios?crear=1',
}

const TITULO_CREAR = {
  aprendiz: 'Nueva propuesta',
  instructor: 'Crear ficha',
  admin: 'Crear usuario',
}


function BellIcon() {
  return (
    <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  )
}

function LogoutIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <path d="M16 17l5-5-5-5M21 12H9" />
    </svg>
  )
}

function PlusIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden="true">
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

function HamburgerIcon() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
      <path d="M3 6h18M3 12h18M3 18h18" />
    </svg>
  )
}

export default function TopNav({ titulo = '', usuario = null, notificaciones = 0, role = '', onToggleSidebar }) {
  const { logout } = useAuth()
  const navigate = useNavigate()

  // Foto de perfil real (data URL base64) desde la API.
  const { data: perfil } = useApi(
    () => (usuario?.id ? usuarios.obtener(usuario.id) : Promise.resolve(null)),
    [usuario?.id]
  )
  const fotoPerfilSesion = perfil?.foto_url || usuario?.foto_url || null
  const nombrePerfil = [perfil?.nombre, perfil?.apellido].filter(Boolean).join(' ').trim()
    || usuario?.nombre || ''
  const correoPerfil = perfil?.correo || usuario?.correo

  const cerrarSesion = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  const irANotificaciones = () => {
    const ruta = RUTA_ALERTAS_POR_ROL[role]
    if (ruta) navigate(ruta)
  }

  return (
    <header className={s.topnav}>
      <div className={s.bar}>
        <div className={s.left}>
          <Tooltip content="Abrir menú de navegación">
            <button type="button" className={s.hamburger} onClick={onToggleSidebar} aria-label="Abrir menú de navegación">
              <HamburgerIcon />
            </button>
          </Tooltip>
          <Link to="/" viewTransition className={s.logo} aria-label="Ir al inicio">
            <img src="/images/Logo-ProyecTwin.png" alt="ProyecTwin SENA" />
          </Link>
          <span className={s.title}>{titulo}</span>
        </div>

        <div className={s.right}>
          {RUTA_CREAR[role] && (
            <Tooltip content={TITULO_CREAR[role]}>
              <button
                type="button"
                className={s.ctaNueva}
                onClick={() => navigate(RUTA_CREAR[role])}
                aria-label={TITULO_CREAR[role]}
              >
                <PlusIcon />
              </button>
            </Tooltip>
          )}

          <span className={s.senaWrap}>
            <img className={s.senaLogo} src="/images/logo-sena-blanco.png" alt="SENA" />
          </span>

          <Tooltip content={notificaciones > 0 ? `Notificaciones (${notificaciones} sin leer)` : 'Notificaciones'}>
            <button
              type="button"
              className={`${s.notif} ${notificaciones > 0 ? s.notifUnread : ''}`}
              onClick={irANotificaciones}
              aria-label={`Notificaciones${notificaciones > 0 ? ` (${notificaciones} sin leer)` : ''}`}
            >
              <BellIcon />
              {notificaciones > 0 && <span className={s.dot} aria-hidden="true" />}
            </button>
          </Tooltip>

          <div className={s.user} title={correoPerfil}>
            <Link to={`/${role}/perfil`} viewTransition className={s.avatarLink} aria-label="Ir a mi perfil">
              <span className={s.avatar} aria-hidden="true">
              {fotoPerfilSesion ? (
                <img src={fotoPerfilSesion} alt="" />
              ) : (
                iniciales(nombrePerfil)
              )}
              </span>
            </Link>
            <span className={s.userInfo}>
              <span className={s.userName}>{nombrePerfil}</span>
              <span className={s.userRole}>{ROL_LABEL[role] || role}</span>
            </span>
          </div>

          <button type="button" className={s.logoutBtn} onClick={cerrarSesion} aria-label="Cerrar sesión">
            <LogoutIcon />
            <span className={s.logoutText}>Salir</span>
          </button>
        </div>
      </div>
    </header>
  )
}

import { useState } from 'react'
import { useAuth } from '../../contexts/AuthContext'
import { useApi } from '../../lib/useApi'
import { notificaciones as apiNotificaciones } from '../../lib/recursos'
import {
  House, FolderOpen, Bell, Bug, UserCircle,
  ClipboardText, BookOpen, BookBookmark, UsersThree, MagnifyingGlass, GraduationCap, GearSix,
  ClockCounterClockwise, TreeStructure
} from 'phosphor-react'
import { RUTA_POR_ROL, RUTA_ALERTAS_POR_ROL, RUTA_SOPORTE_POR_ROL } from '../../constants/routes'
import GovernmentBar from '../../components/GovernmentBar/GovernmentBar'
import TopNav from '../../components/TopNav/TopNav'
import Sidebar from '../../components/Sidebar/Sidebar'
import Footer from '../../components/Footer/Footer'
import s from './DashboardLayout.module.css'

const LINKS = {
  aprendiz: [
    { to: RUTA_POR_ROL.aprendiz, icon: <House size={20} weight="regular" />, label: 'Dashboard' },
    { to: '/aprendiz/propuestas', icon: <FolderOpen size={20} weight="regular" />, label: 'Propuestas', activeFor: ['/aprendiz/detalle-proyecto'] },
    { to: '/aprendiz/similitudes', icon: <MagnifyingGlass size={20} weight="regular" />, label: 'Similitudes', activeFor: ['/aprendiz/detalle-similitud', '/aprendiz/resultado-analisis'] },
    { to: '/aprendiz/ficha', icon: <GraduationCap size={20} weight="regular" />, label: 'Ficha', activeFor: ['/aprendiz/detalle-ficha'] },
    { to: RUTA_ALERTAS_POR_ROL.aprendiz, icon: <Bell size={20} weight="regular" />, label: 'Notificaciones' },
    { to: '/aprendiz/perfil', icon: <UserCircle size={20} weight="regular" />, label: 'Mi Perfil' },
    { to: RUTA_SOPORTE_POR_ROL.aprendiz, icon: <Bug size={20} weight="regular" />, label: 'Soporte' },
  ],
  instructor: [
    { to: RUTA_POR_ROL.instructor, icon: <House size={20} weight="regular" />, label: 'Dashboard' },
    { to: '/instructor/revision-propuestas', icon: <ClipboardText size={20} weight="regular" />, label: 'Revisión Propuestas', activeFor: ['/instructor/detalle-proyecto'] },
    { to: '/instructor/similitudes', icon: <MagnifyingGlass size={20} weight="regular" />, label: 'Similitudes', activeFor: ['/instructor/detalle-similitud'] },
    { to: '/instructor/fichas', icon: <BookOpen size={20} weight="regular" />, label: 'Fichas', activeFor: ['/instructor/detalle-ficha', '/instructor/directorio-ficha'] },
    { to: RUTA_ALERTAS_POR_ROL.instructor, icon: <Bell size={20} weight="regular" />, label: 'Notificaciones' },
    { to: '/instructor/perfil', icon: <UserCircle size={20} weight="regular" />, label: 'Mi Perfil' },
    { to: RUTA_SOPORTE_POR_ROL.instructor, icon: <Bug size={20} weight="regular" />, label: 'Soporte' },
  ],
  admin: [
    { to: RUTA_POR_ROL.admin, icon: <House size={20} weight="regular" />, label: 'Dashboard' },
    { to: '/admin/proyectos', icon: <FolderOpen size={20} weight="regular" />, label: 'Propuestas', activeFor: ['/admin/detalle-proyecto'] },
    { to: '/admin/similitudes', icon: <MagnifyingGlass size={20} weight="regular" />, label: 'Similitudes', activeFor: ['/admin/detalle-similitud'] },
    { to: RUTA_SOPORTE_POR_ROL.admin, icon: <Bug size={20} weight="regular" />, label: 'Soporte', activeFor: ['/admin/detalle-reporte'] },
    { to: RUTA_ALERTAS_POR_ROL.admin, icon: <Bell size={20} weight="regular" />, label: 'Notificaciones' },
    { to: '/admin/usuarios', icon: <UsersThree size={20} weight="regular" />, label: 'Usuarios', activeFor: ['/admin/detalle-usuario'] },
    { to: '/admin/fichas', icon: <BookBookmark size={20} weight="regular" />, label: 'Fichas', activeFor: ['/admin/detalle-ficha'] },
    { to: '/admin/bitacora', icon: <ClockCounterClockwise size={20} weight="regular" />, label: 'Bitácora' },
    { to: '/admin/redes-conocimiento', icon: <TreeStructure size={20} weight="regular" />, label: 'Redes y programas', activeFor: ['/admin/redes-conocimiento'], group: 'Configuración' },
    { to: '/admin/config-similitud', icon: <GearSix size={20} weight="regular" />, label: 'Motor de similitud', group: 'Configuración' },
    { to: '/admin/perfil', icon: <UserCircle size={20} weight="regular" />, label: 'Mi Perfil' },
  ],
}

export default function DashboardLayout({ role = 'aprendiz', titulo = '', children }) {
  const { user } = useAuth()
  const [sidebarOpen, setSidebarOpen] = useState(false)

  // Campana: notificaciones reales del usuario (fuente única: la API).
  const { data } = useApi(
    () => (user?.id ? apiNotificaciones.listar() : Promise.resolve([])),
    [user?.id],
    { inicial: [] }
  )
  const sinLeer = user
    ? (data || []).filter((n) => Number(n.id_usuario) === Number(user.id) && !n.leida).length
    : 0
  const links = LINKS[role] || LINKS.aprendiz

  return (
    <div className={s.layout}>
      <GovernmentBar />
      <TopNav
        titulo={titulo}
        usuario={user}
        role={role}
        notificaciones={sinLeer}
        onToggleSidebar={() => setSidebarOpen(o => !o)}
      />
      <Sidebar
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        role={role}
        links={links}
      />
      <div className={s.body}>
        <main className={s.main}>{children}</main>
        <Footer />
      </div>
    </div>
  )
}

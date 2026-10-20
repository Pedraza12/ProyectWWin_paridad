import { Link } from 'react-router-dom'
import { UsersThree, FolderOpen, MagnifyingGlass, Bug, Bell, Sparkle, SlidersHorizontal, Gauge, Database } from 'phosphor-react'
import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import DashboardHero from '../../../components/DashboardHero/DashboardHero'
import DashboardMetrics from '../../../components/DashboardMetrics/DashboardMetrics'
import DashboardGrid from '../../../components/DashboardGrid/DashboardGrid'
import StatChip from '../../../components/StatChip/StatChip'
import DataPanel from '../../../components/DataPanel/DataPanel'
import SectionHeader from '../../../components/SectionHeader/SectionHeader'
import QueueTile, { QueueGrid } from '../../../components/QueueTile/QueueTile'
import ActivityList from '../../../components/ActivityList/ActivityList'
import QuickActions from '../../../components/QuickActions/QuickActions'
import Badge from '../../../components/Badge/Badge'
import Button from '../../../components/Button/Button'
import EmptyState from '../../../components/EmptyState/EmptyState'
import ApiState from '../../../components/ApiState/ApiState'
import { useAuth } from '../../../contexts/AuthContext'
import { useApi } from '../../../lib/useApi'
import { usuarios, proyectos, similitudes, reportes, notificaciones, motor, metricas } from '../../../lib/recursos'
import { fechaDesdeApi } from '../../../utils/helpers'
import { RECIENTES } from '../../../constants/pagination'
import { RUTA_ALERTAS_POR_ROL, RUTA_SOPORTE_POR_ROL } from '../../../constants/routes'
import s from './DashboardAdmin.module.css'

// Etiquetas legibles del tipo de notificación (columnas reales de la API).
const TIPO_NOTIF = {
  similitud: 'Similitud',
  observacion: 'Observación',
  revision: 'Revisión',
  mensaje: 'Mensaje',
  sistema: 'Sistema',
}

export default function DashboardAdmin() {
  const { user } = useAuth()

  // Métricas agregadas en el backend (una sola carga para las gráficas).
  const { data: metricasData } = useApi(() => metricas.obtener(), [user?.id], { inicial: null })

  // Fuente única: la API. Se piden todos los recursos del tablero en paralelo.
  const { data, cargando, error, recargar } = useApi(
    async () => {
      const [listaUsuarios, listaProyectos, listaSimilitudes, listaReportes, listaNotificaciones, configMotor] =
        await Promise.all([
          usuarios.listar(),
          proyectos.listar(),
          similitudes.listar(),
          reportes.listar(),
          notificaciones.listar(),
          motor.obtener(),
        ])
      return { listaUsuarios, listaProyectos, listaSimilitudes, listaReportes, listaNotificaciones, configMotor }
    },
    [user?.id],
    { inicial: null }
  )

  const dato = data || {}
  const usuariosLista = dato.listaUsuarios || []
  const proyectosLista = dato.listaProyectos || []
  const similitudesLista = dato.listaSimilitudes || []
  const reportesLista = dato.listaReportes || []
  const motorConfig = dato.configMotor || { umbral: 0.30, meses: 12 }

  // Alertas del admin autenticado (la API devuelve todas; se filtra por usuario).
  const alertas = (dato.listaNotificaciones || [])
    .filter((n) => Number(n.id_usuario) === Number(user?.id))
    .sort((a, b) => Number(b.id) - Number(a.id))
    .slice(0, RECIENTES)

  const datos = {
    totalUsuarios: usuariosLista.length,
    suspendidos: usuariosLista.filter((u) => u.estado === false).length,
    totalProyectos: proyectosLista.length,
    pendientes: proyectosLista.filter((p) => p.estado === 'pendiente').length,
    totalSimilitudes: similitudesLista.length,
    totalReportes: reportesLista.length,
    reportesAbiertos: reportesLista.filter((r) => r.estado === 'pendiente' || r.estado === 'en_revision').length,
  }
  const saludo = user?.nombre?.split(' ')[0] || 'Admin'

  const quick = [{
    to: '/admin/config-similitud',
    icon: <SlidersHorizontal size={24} weight="regular" />,
    titulo: 'Motor de similitud',
    descripcion: 'Ajusta el umbral y recalibra la base',
  }]

  if (datos.reportesAbiertos > 0) {
    quick.unshift({
      to: RUTA_SOPORTE_POR_ROL.admin,
      icon: <Bug size={24} weight="regular" />,
      titulo: 'Atender reportes',
      descripcion: `${datos.reportesAbiertos} abiertos`,
    })
  }

  const colas = [
    { to: '/admin/proyectos', icon: <FolderOpen size={18} />, nombre: 'Propuestas pendientes', valor: datos.pendientes, total: datos.totalProyectos },
    { to: RUTA_SOPORTE_POR_ROL.admin, icon: <Bug size={18} />, nombre: 'Reportes abiertos', valor: datos.reportesAbiertos, total: datos.totalReportes },
    { to: '/admin/usuarios', icon: <UsersThree size={18} />, nombre: 'Cuentas suspendidas', valor: datos.suspendidos, total: datos.totalUsuarios },
  ]

  const itemsAlertas = alertas.map((n) => ({
    key: n.id,
    to: RUTA_ALERTAS_POR_ROL.admin,
    title: n.titulo,
    meta: `${TIPO_NOTIF[n.tipo] || 'Notificación'} · ${fechaDesdeApi(n.fecha)}`,
    side: !n.leida ? <Badge variant="primary">Nueva</Badge> : null,
  }))

  return (
    <DashboardLayout role="admin" titulo="Dashboard">
      <div className={s.page}>
        <DashboardHero
          kicker="STATUS · ADMIN"
          title={<>¡Hola, {saludo}!</>}
          text="Monitorea usuarios, propuestas, similitudes detectadas y reportes de fallas de toda la plataforma ProyecTwin."
        />

        <DashboardMetrics rol="admin" data={metricasData} />

        <ApiState cargando={cargando} error={error} onReintentar={recargar}>
          <DataPanel
            title="Salud del motor"
            subtitle={`Umbral ${Math.round(motorConfig.umbral * 100)}% · corpus de ${motorConfig.meses} meses`}
            glow
            action={
              <Button as="link" to="/admin/config-similitud" viewTransition size="sm" variant="secondary">
                <SlidersHorizontal size={14} /> Ajustar motor
              </Button>
            }
          >
            <div className={s.motorChips}>
              <StatChip icon={<Gauge size={14} />} label="Umbral" value={`${Math.round(motorConfig.umbral * 100)}%`} />
              <StatChip icon={<Database size={14} />} label="Corpus" value={`${motorConfig.meses}M`} />
              <StatChip icon={<MagnifyingGlass size={14} />} label="Pares" value={datos.totalSimilitudes} />
              <StatChip icon={<UsersThree size={14} />} label="Usuarios" value={datos.totalUsuarios} />
            </div>
          </DataPanel>

          <section aria-label="Colas por atender">
            <SectionHeader title="Colas por atender" hint="lo que espera acción" />
            <QueueGrid>
              {colas.map((c) => (
                <QueueTile key={c.to} to={c.to} icon={c.icon} value={c.valor} total={c.total} title={c.nombre} />
              ))}
            </QueueGrid>
          </section>

          <DashboardGrid
            left={
              <DataPanel title="Acciones rápidas" icon={<Sparkle size={18} />}>
                <QuickActions items={quick} variant="stack" />
              </DataPanel>
            }
            right={
              <DataPanel
                title="Notificaciones recientes"
                icon={<Bell size={18} />}
                action={<Link to={RUTA_ALERTAS_POR_ROL.admin} viewTransition className={s.panelLink}>Ver todas</Link>}
              >
                <ActivityList
                  items={itemsAlertas}
                  empty={<EmptyState title="Sin notificaciones" message="No tienes notificaciones recientes." />}
                />
              </DataPanel>
            }
          />
        </ApiState>
      </div>
    </DashboardLayout>
  )
}

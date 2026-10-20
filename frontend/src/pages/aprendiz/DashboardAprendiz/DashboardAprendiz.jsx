import { useMemo } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Bell, CaretRight, Clock, FolderOpen, MagnifyingGlass, PlusCircle, Sparkle, Tray } from 'phosphor-react'
import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import DashboardHero from '../../../components/DashboardHero/DashboardHero'
import DashboardMetrics from '../../../components/DashboardMetrics/DashboardMetrics'
import DashboardGrid from '../../../components/DashboardGrid/DashboardGrid'
import MetricCard from '../../../components/MetricCard/MetricCard'
import DataPanel from '../../../components/DataPanel/DataPanel'
import QuickActions from '../../../components/QuickActions/QuickActions'
import ActivityList from '../../../components/ActivityList/ActivityList'
import Badge from '../../../components/Badge/Badge'
import GradeBadge from '../../../components/GradeBadge/GradeBadge'
import EmptyState from '../../../components/EmptyState/EmptyState'
import ApiState from '../../../components/ApiState/ApiState'
import { useApi } from '../../../lib/useApi'
import { proyectos, similitudes as similitudesApi, notificaciones, INCLUDE_PROYECTOS } from '../../../lib/recursos'
import { PROJECT_ESTADO_VARIANT, PROJECT_ESTADO_LABEL as ESTADO_LABEL } from '../../../constants/badgeVariants'
import { useAuth } from '../../../contexts/AuthContext'
import { fechaDesdeApi, esPropietarioProyecto, infoSimilitud } from '../../../utils/helpers'
import { RECIENTES } from '../../../constants/pagination'
import { RUTA_ALERTAS_POR_ROL } from '../../../constants/routes'
import s from './DashboardAprendiz.module.css'

// Relaciones que la lista debe incluir para poder detectar al equipo (pivote).

// Etiquetas de estado de propuesta (el backend solo devuelve el código).

const fechaHoy = new Intl.DateTimeFormat('es-CO', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
}).format(new Date())

// Una propuesta es del aprendiz si la creó o si figura en su equipo.
const esMio = esPropietarioProyecto
// Máximo porcentaje y número de coincidencias de una propuesta.

export default function DashboardAprendiz() {
  const { user } = useAuth()
  const navigate = useNavigate()

  // Propuestas, similitudes y notificaciones: fuente única la API.
  const { data: todosProyectos, cargando, error, recargar } = useApi(
    () => proyectos.listar({ included: INCLUDE_PROYECTOS }),
    [],
    { inicial: [] }
  )
  const { data: todasSimilitudes } = useApi(() => similitudesApi.listar(), [], { inicial: [] })
  const { data: misNotificaciones } = useApi(
    // El backend ya acota las notificaciones al usuario autenticado: misma URL
    // que usa el layout, así se comparte una sola petición.
    () => notificaciones.listar(),
    [user.id],
    { inicial: [] }
  )

  const misProyectos = useMemo(
    () => todosProyectos.filter((p) => esMio(p, user.id)),
    [todosProyectos, user.id]
  )
  const idsPropios = useMemo(() => new Set(misProyectos.map((p) => Number(p.id))), [misProyectos])

  const similitudesPropias = useMemo(
    () => todasSimilitudes.filter(
      (x) => idsPropios.has(Number(x.id_proyecto_1)) || idsPropios.has(Number(x.id_proyecto_2))
    ).length,
    [todasSimilitudes, idsPropios]
  )

  const sinLeer = useMemo(
    () => misNotificaciones.filter((n) => Number(n.id_usuario) === Number(user.id) && !n.leida).length,
    [misNotificaciones, user.id]
  )

  const recientes = useMemo(() => [...misProyectos].slice(0, RECIENTES), [misProyectos])
  const saludo = (user.nombre || '').split(' ')[0]

  const acciones = [{
    to: '/aprendiz/propuestas?crear=1',
    icon: <PlusCircle size={24} weight="regular" />,
    titulo: 'Nueva Propuesta',
    descripcion: 'Crea tu propuesta y analízala al instante',
  }]

  const items = recientes.map((p) => {
    const info = infoSimilitud(todasSimilitudes, p.id)
    return {
      key: p.id,
      to: `/aprendiz/detalle-proyecto/${p.id}`,
      title: p.titulo,
      meta: `${fechaDesdeApi(p.created_at)} · ${ESTADO_LABEL[p.estado] || p.estado}`,
      side: (
        <>
          {info && (
            <span title={`${info.pct}% · ${info.count} coincidencia${info.count !== 1 ? 's' : ''}`}>
              <GradeBadge score={info.pct} size="sm" />
            </span>
          )}
          <Badge variant={PROJECT_ESTADO_VARIANT[p.estado] || 'neutral'}>{ESTADO_LABEL[p.estado] || p.estado}</Badge>
          <CaretRight size={16} />
        </>
      ),
    }
  })

  return (
    <DashboardLayout role="aprendiz" titulo="Dashboard">
      <div className={s.page}>
        <DashboardHero
          kicker="Panel de aprendiz"
          title={<>¡Hola, {saludo}!</>}
          text="Este es tu espacio para gestionar tus propuestas y mantener la originalidad de tu trabajo."
          date={fechaHoy}
        />

        <section className={s.stats} aria-label="Resumen de actividad">
          <MetricCard to="/aprendiz/propuestas" icon={<FolderOpen size={22} />} label="Propuestas registradas" value={misProyectos.length} variant="primary" />
          <MetricCard to="/aprendiz/similitudes" icon={<MagnifyingGlass size={22} />} label="Similitudes detectadas" value={similitudesPropias} variant="warning" />
          <MetricCard to={RUTA_ALERTAS_POR_ROL.aprendiz} icon={<Bell size={22} />} label="Notificaciones sin leer" value={sinLeer} variant="info" />
        </section>

        <DashboardMetrics rol="aprendiz" />

        <DashboardGrid
          left={
            <DataPanel title="Acciones rápidas" icon={<Sparkle size={18} />}>
              <QuickActions items={acciones} />
            </DataPanel>
          }
          right={
            <DataPanel
              title="Propuestas recientes"
              icon={<Clock size={18} />}
              action={<Link to="/aprendiz/propuestas" className={s.panelLink}>Ver todas</Link>}
            >
              <ApiState cargando={cargando} error={error} onReintentar={recargar}>
                <ActivityList
                  items={items}
                  empty={
                    <EmptyState
                      icon={<Tray />}
                      title="Aún no tienes propuestas"
                      message="Registra tu primera propuesta y envíala para analizarla."
                      actionLabel="Crear propuesta"
                      onAction={() => navigate('/aprendiz/propuestas?crear=1')}
                    />
                  }
                />
              </ApiState>
            </DataPanel>
          }
        />
      </div>
    </DashboardLayout>
  )
}

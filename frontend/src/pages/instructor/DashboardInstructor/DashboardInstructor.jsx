import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { Bell, CaretRight, ClipboardText, PlusCircle, BookOpen, MagnifyingGlass } from 'phosphor-react'
import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import DashboardHero from '../../../components/DashboardHero/DashboardHero'
import DashboardMetrics from '../../../components/DashboardMetrics/DashboardMetrics'
import StatChip from '../../../components/StatChip/StatChip'
import SectionHeader from '../../../components/SectionHeader/SectionHeader'
import ActivityList from '../../../components/ActivityList/ActivityList'
import QueueTile, { QueueGrid } from '../../../components/QueueTile/QueueTile'
import GradeBadge from '../../../components/GradeBadge/GradeBadge'
import Badge from '../../../components/Badge/Badge'
import Button from '../../../components/Button/Button'
import EmptyState from '../../../components/EmptyState/EmptyState'
import ApiState from '../../../components/ApiState/ApiState'
import { useAuth } from '../../../contexts/AuthContext'
import { useApi } from '../../../lib/useApi'
import { proyectos, similitudes as similitudesApi, notificaciones, fichas, instructores, INCLUDE_PROYECTOS, metricas } from '../../../lib/recursos'
import { PROJECT_ESTADO_VARIANT, PROJECT_ESTADO_LABEL as ESTADO_LABEL } from '../../../constants/badgeVariants'
import { fechaDesdeApi, nombreCompleto, infoSimilitud } from '../../../utils/helpers'
import { RECIENTES } from '../../../constants/pagination'
import { RUTA_ALERTAS_POR_ROL } from '../../../constants/routes'
import s from './DashboardInstructor.module.css'

// Relaciones que la lista de propuestas debe traer para mostrar creador y ficha.



// Máximo porcentaje y conteo de coincidencias de una propuesta.

export default function DashboardInstructor() {
  const { user } = useAuth()

  // Métricas agregadas en el backend (una sola carga para las gráficas).
  const { data: metricasData } = useApi(() => metricas.obtener(), [user?.id], { inicial: null })

  // Fuente única: la API. Catálogos e instructor propio para resolver el alcance.
  const { data: instructoresApi } = useApi(() => instructores.listar(), [], { inicial: [] })
  const { data: fichasApi } = useApi(() => fichas.listar(), [], { inicial: [] })
  const { data: todosProyectos, cargando, error, recargar } = useApi(
    () => proyectos.listar({ included: INCLUDE_PROYECTOS }),
    [],
    { inicial: [] }
  )
  const { data: todasSimilitudes } = useApi(() => similitudesApi.listar(), [], { inicial: [] })
  const { data: misNotificaciones } = useApi(
    // El backend ya acota las notificaciones al usuario autenticado: misma URL
    // que usa el layout, así se comparte una sola petición.
    () => (user?.id ? notificaciones.listar() : Promise.resolve([])),
    [user?.id],
    { inicial: [] }
  )

  // Fila de perfil del instructor (instructors) del usuario autenticado.
  const miFila = useMemo(
    () => instructoresApi.find((i) => Number(i.id_usuario) === Number(user?.id)) || null,
    [instructoresApi, user?.id]
  )

  // Fichas a su cargo: la relación viene en ficha.instructor.id (fila instructors).
  const misFichas = useMemo(
    () => fichasApi.filter((f) => Number(f.instructor?.id) === Number(miFila?.id)),
    [fichasApi, miFila?.id]
  )
  const misFichasIds = useMemo(() => new Set(misFichas.map((f) => Number(f.id))), [misFichas])

  // Regla de negocio: el instructor ve el proyecto si es su asignado o si la
  // propuesta pertenece a una de sus fichas.
  const verProyecto = useMemo(
    () => (p) =>
      Number(p.id_instructor_asignado) === Number(miFila?.id) ||
      misFichasIds.has(Number(p.id_class_group)),
    [miFila?.id, misFichasIds]
  )

  const misProyectos = useMemo(
    () => todosProyectos.filter(verProyecto),
    [todosProyectos, verProyecto]
  )
  const pendientes = useMemo(
    () => misProyectos.filter((p) => p.estado === 'pendiente'),
    [misProyectos]
  )

  const idsPropios = useMemo(() => new Set(misProyectos.map((p) => Number(p.id))), [misProyectos])
  const similitudesPropias = useMemo(
    () => todasSimilitudes.filter(
      (x) => idsPropios.has(Number(x.id_proyecto_1)) || idsPropios.has(Number(x.id_proyecto_2))
    ).length,
    [todasSimilitudes, idsPropios]
  )

  const sinLeer = useMemo(
    () => misNotificaciones.filter((n) => Number(n.id_usuario) === Number(user?.id) && !n.leida).length,
    [misNotificaciones, user?.id]
  )

  const recientes = useMemo(() => pendientes.slice(0, RECIENTES), [pendientes])
  const saludo = (user?.nombre || '').split(' ')[0] || 'Instructor'

  const itemsTurno = recientes.map((p, i) => {
    const info = infoSimilitud(todasSimilitudes, p.id)
    return {
      key: p.id,
      to: `/instructor/detalle-proyecto/${p.id}`,
      ordinal: i + 1,
      title: p.titulo,
      meta: `${nombreCompleto(p.creator)} · ${fechaDesdeApi(p.created_at)}`,
      side: (
        <>
          {info ? <GradeBadge score={info.pct} size="sm" /> : null}
          <Badge variant={PROJECT_ESTADO_VARIANT[p.estado] || 'neutral'}>{ESTADO_LABEL[p.estado] || p.estado}</Badge>
          <CaretRight size={16} />
        </>
      ),
    }
  })

  return (
    <DashboardLayout role="instructor" titulo="Dashboard">
      <div className={s.page}>
        <DashboardHero
          kicker="TURNO · INSTRUCTOR"
          title={<>¡Hola, {saludo}!</>}
          text={
            pendientes.length === 0
              ? 'Sin pendientes en tu turno. Bienvenido de nuevo a ProyecTwin.'
              : `Tienes ${pendientes.length} propuesta${pendientes.length !== 1 ? 's' : ''} esperando tu revisión.`
          }
          chips={
            <>
              <Link to="/instructor/revision-propuestas" viewTransition className={s.chipLink}>
                <StatChip icon={<ClipboardText size={14} />} label="Por revisar" value={pendientes.length} />
              </Link>
              <Link to="/instructor/fichas" viewTransition className={s.chipLink}>
                <StatChip icon={<BookOpen size={14} />} label="Fichas" value={misFichas.length} />
              </Link>
              <Link to="/instructor/similitudes" viewTransition className={s.chipLink}>
                <StatChip icon={<MagnifyingGlass size={14} />} label="Similitudes" value={similitudesPropias} />
              </Link>
              <Link to={RUTA_ALERTAS_POR_ROL.instructor} viewTransition className={s.chipLink}>
                <StatChip icon={<Bell size={14} />} label="Alertas" value={sinLeer} />
              </Link>
            </>
          }
          actions={
            <>
              {pendientes.length > 0 && (
                <Button as="link" to="/instructor/revision-propuestas" viewTransition>
                  <ClipboardText size={14} /> Revisar propuestas
                </Button>
              )}
              <Button as="link" to="/instructor/fichas?crear=1" viewTransition variant="secondary">
                <PlusCircle size={14} /> Crear ficha
              </Button>
            </>
          }
        />

        <DashboardMetrics rol="instructor" data={metricasData} />

        <ApiState cargando={cargando} error={error} onReintentar={recargar}>
          <section aria-label="Pendientes del turno">
            <SectionHeader title="Hoy en tu turno" count={recientes.length} hint="pendientes recientes" />
            <ActivityList
              variant="card"
              accent="warning"
              items={itemsTurno}
              empty={<EmptyState title="No hay propuestas pendientes" message="Cuando tus aprendices envíen nuevas propuestas aparecerán aquí para su revisión." />}
            />
          </section>

          <section aria-label="Tus cohortes">
            <SectionHeader title="Tus cohortes" count={misFichas.length} />
            <QueueGrid>
              {misFichas.map((f) => {
                const deFicha = misProyectos.filter((p) => Number(p.id_class_group) === Number(f.id))
                const pend = deFicha.filter((p) => p.estado === 'pendiente').length
                return (
                  <QueueTile
                    key={f.id}
                    to={`/instructor/detalle-ficha/${f.id}`}
                    eyebrow={f.codigo}
                    title={f.nombre}
                    meta={`${deFicha.length} propuesta${deFicha.length !== 1 ? 's' : ''} · ${pend} por revisar`}
                  />
                )
              })}
            </QueueGrid>
          </section>
        </ApiState>
      </div>
    </DashboardLayout>
  )
}

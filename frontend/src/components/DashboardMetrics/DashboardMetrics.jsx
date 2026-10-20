import { useState } from 'react'
import { Books, ChartLineUp, ChartPieSlice, ClipboardText, Clock, FolderOpen, Student, UsersThree, ChalkboardTeacher } from 'phosphor-react'
import SectionHeader from '../SectionHeader/SectionHeader'
import MetricCard from '../MetricCard/MetricCard'
import DataPanel from '../DataPanel/DataPanel'
import ActivityList from '../ActivityList/ActivityList'
import EmptyState from '../EmptyState/EmptyState'
import { BarChart, DonutChart, LineChart } from '../Charts/Charts'
import { useApi } from '../../lib/useApi'
import { metricas } from '../../lib/recursos'
import { fechaHoraDesdeApi } from '../../utils/helpers'
import s from './DashboardMetrics.module.css'

// Colores semánticos del sistema (mismos tokens que los badges de estado).
const COLOR_ESTADO = {
  borrador: 'var(--c-text-3)',
  pendiente: 'var(--c-warning)',
  aprobado: 'var(--c-success)',
  rechazado: 'var(--c-danger)',
}

// Rangos reales de la serie temporal (el backend agrupa por mes o por día).
const PERIODOS = [
  { valor: '3m', etiqueta: 'Últimos 3 meses' },
  { valor: '30d', etiqueta: 'Últimos 30 días' },
  { valor: '7d', etiqueta: 'Últimos 7 días' },
]

// Panel de métricas reales según el rol. Se alimenta del endpoint
// `/dashboard-metrics?periodo=…` (agregados calculados en el backend).
export default function DashboardMetrics({ rol }) {
  const [periodo, setPeriodo] = useState('3m')

  const { data } = useApi(
    () => metricas.obtener(periodo),
    [rol, periodo],
    { inicial: null }
  )

  // Si falla o aún carga, el resto del dashboard sigue funcionando.
  if (!data) return null

  const propuestas = data.propuestas || {}
  const estados = [
    { label: 'Borrador', value: propuestas.borrador || 0, color: COLOR_ESTADO.borrador },
    { label: 'En revisión', value: propuestas.pendiente || 0, color: COLOR_ESTADO.pendiente },
    { label: 'Aprobadas', value: propuestas.aprobado || 0, color: COLOR_ESTADO.aprobado },
    { label: 'Rechazadas', value: propuestas.rechazado || 0, color: COLOR_ESTADO.rechazado },
  ]

  const evolucion = (data.evolucion || []).map((item) => ({
    clave: item.clave,
    label: item.label,
    fecha: item.fecha,
    value: Number(item.total) || 0,
    propuestas: item.propuestas || [],
    restantes: item.restantes || 0,
  }))

  const actividad = (data.actividad || []).map((item) => {
    const enlace = item.proyecto_id
      ? (rol === 'instructor'
        ? `/instructor/detalle-proyecto/${item.proyecto_id}`
        : rol === 'aprendiz'
          ? `/aprendiz/detalle-proyecto/${item.proyecto_id}`
          : undefined)
      : (rol === 'admin' ? '/admin/bitacora' : undefined)

    return {
      key: item.id,
      title: item.titulo,
      meta: [
        item.meta,
        rol === 'aprendiz' ? null : item.usuario,
        item.fecha ? fechaHoraDesdeApi(item.fecha) : '',
      ].filter(Boolean).join(' · '),
      to: enlace,
    }
  })

  const tituloEvolucion =
    rol === 'instructor' ? 'Revisiones por mes' : 'Propuestas por mes'

  const selectorPeriodo = (
    <div className={s.periodos} role="group" aria-label="Rango de tiempo de la gráfica">
      {PERIODOS.map((opcion) => (
        <button
          key={opcion.valor}
          type="button"
          className={`${s.periodoBtn} ${periodo === opcion.valor ? s.periodoActivo : ''}`}
          onClick={() => setPeriodo(opcion.valor)}
          aria-pressed={periodo === opcion.valor}
        >
          {opcion.etiqueta}
        </button>
      ))}
    </div>
  )

  return (
    <section className={s.section} aria-label="Métricas del panel">
      <SectionHeader title="Métricas" hint="datos reales de la plataforma" />

      {rol === 'admin' && (
        <div className={s.kpis}>
          <MetricCard icon={<UsersThree size={22} />} label="Usuarios" value={data.usuarios?.total || 0} variant="primary" />
          <MetricCard icon={<Student size={22} />} label="Aprendices" value={data.usuarios?.aprendices || 0} variant="info" />
          <MetricCard icon={<ChalkboardTeacher size={22} />} label="Instructores" value={data.usuarios?.instructores || 0} variant="warning" />
          <MetricCard icon={<Books size={22} />} label="Fichas" value={data.fichas?.total || 0} variant="success" />
        </div>
      )}

      {rol === 'instructor' && (
        <div className={s.kpis}>
          <MetricCard icon={<Books size={22} />} label="Fichas a cargo" value={data.fichas?.total || 0} variant="primary" />
          <MetricCard icon={<Student size={22} />} label="Aprendices" value={data.aprendices?.total || 0} variant="info" />
          <MetricCard icon={<FolderOpen size={22} />} label="Propuestas" value={propuestas.total || 0} variant="success" />
          <MetricCard icon={<ClipboardText size={22} />} label="Por revisar" value={propuestas.pendiente || 0} variant="warning" />
        </div>
      )}

      <div className={`${s.charts} ${rol !== 'admin' ? s.single : ''}`}>
        <DataPanel title="Propuestas por estado" icon={<ChartPieSlice size={18} />}>
          <DonutChart
            data={estados}
            centro="propuestas"
            ariaLabel="Distribución de propuestas por estado"
          />
        </DataPanel>

        {rol === 'admin' && (
          <DataPanel title="Cuentas por rol" icon={<UsersThree size={18} />}>
            <BarChart
              ariaLabel="Usuarios por rol"
              data={[
                { label: 'Aprendices', value: data.usuarios?.aprendices || 0, color: 'var(--c-primary)' },
                { label: 'Instructores', value: data.usuarios?.instructores || 0, color: 'var(--c-info)' },
                { label: 'Administradores', value: data.usuarios?.admins || 0, color: 'var(--c-warning)' },
              ]}
            />
          </DataPanel>
        )}
      </div>

      {/* Grid 8/4: gráfica (izquierda) + actividad reciente (derecha). */}
      <div className={s.lower}>
        <DataPanel
          title={tituloEvolucion}
          icon={<ChartLineUp size={18} />}
          action={selectorPeriodo}
        >
          <LineChart data={evolucion} ariaLabel={`${tituloEvolucion} (${periodo})`} />
        </DataPanel>

        <DataPanel
          title={rol === 'aprendiz' ? 'Observaciones recientes' : 'Actividad reciente'}
          icon={<Clock size={18} />}
        >
          <div className={s.actividadScroll}>
            <ActivityList
              items={actividad}
              empty={
                <EmptyState
                  title="Sin actividad reciente"
                  message={rol === 'aprendiz'
                    ? 'Cuando tu instructor deje observaciones sobre tus propuestas aparecerán aquí.'
                    : 'Aún no hay movimientos registrados en este período.'}
                />
              }
            />
          </div>
        </DataPanel>
      </div>
    </section>
  )
}

import { Books, ChartLineUp, ChartPieSlice, ClipboardText, Clock, FolderOpen, Student, UsersThree, ChalkboardTeacher } from 'phosphor-react'
import SectionHeader from '../SectionHeader/SectionHeader'
import MetricCard from '../MetricCard/MetricCard'
import DataPanel from '../DataPanel/DataPanel'
import ActivityList from '../ActivityList/ActivityList'
import EmptyState from '../EmptyState/EmptyState'
import { BarChart, DonutChart, LineChart } from '../Charts/Charts'
import { CYBER_COLORS } from '../Charts/palette'
import { fechaDesdeApi } from '../../utils/helpers'
import s from './DashboardMetrics.module.css'

// Colores neón exclusivos de las gráficas (coherentes con el panel cyber).
const COLOR_ESTADO = {
  borrador: CYBER_COLORS.dim,
  pendiente: CYBER_COLORS.magenta,
  aprobado: CYBER_COLORS.green,
  rechazado: CYBER_COLORS.red,
}

// Panel de métricas reales según el rol. Se alimenta del endpoint
// `/dashboard-metrics` (agregados calculados en el backend).
export default function DashboardMetrics({ rol, data }) {
  if (!data) return null

  const propuestas = data.propuestas || {}
  const estados = [
    { label: 'Borrador', value: propuestas.borrador || 0, color: COLOR_ESTADO.borrador },
    { label: 'En revisión', value: propuestas.pendiente || 0, color: COLOR_ESTADO.pendiente },
    { label: 'Aprobadas', value: propuestas.aprobado || 0, color: COLOR_ESTADO.aprobado },
    { label: 'Rechazadas', value: propuestas.rechazado || 0, color: COLOR_ESTADO.rechazado },
  ]

  const evolucion = (data.evolucion || []).map((mes) => ({
    mes: mes.mes,
    label: mes.label,
    value: Number(mes.total) || 0,
  }))

  const actividad = (data.actividad || []).map((item) => ({
    key: item.id,
    title: item.titulo,
    meta: [item.usuario, item.fecha ? fechaDesdeApi(item.fecha) : ''].filter(Boolean).join(' · ') || item.meta,
  }))

  const tituloEvolucion =
    rol === 'instructor' ? 'Revisiones por mes' : 'Propuestas por mes'

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

      <div className={s.charts}>
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
                { label: 'Aprendices', value: data.usuarios?.aprendices || 0, color: CYBER_COLORS.green },
                { label: 'Instructores', value: data.usuarios?.instructores || 0, color: CYBER_COLORS.cyan },
                { label: 'Administradores', value: data.usuarios?.admins || 0, color: CYBER_COLORS.magenta },
              ]}
            />
          </DataPanel>
        )}

        <DataPanel title={tituloEvolucion} icon={<ChartLineUp size={18} />}>
          <LineChart data={evolucion} ariaLabel={tituloEvolucion} />
        </DataPanel>
      </div>

      <DataPanel
        title={rol === 'aprendiz' ? 'Observaciones recientes' : 'Actividad reciente'}
        icon={<Clock size={18} />}
      >
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
      </DataPanel>
    </section>
  )
}

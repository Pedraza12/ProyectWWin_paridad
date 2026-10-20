import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import AlertasBase from '../../../components/AlertasBase/AlertasBase'

export default function AlertasAprendiz() {
  return (
    <DashboardLayout role="aprendiz" titulo="Notificaciones">
      <AlertasBase
        titulo="Notificaciones"
        subtitle="Mantente al tanto de las novedades de tus proyectos"
        detallePath="/aprendiz"
        emptyActionLabel="Ir a mis propuestas"
        emptyActionTo="/aprendiz/propuestas"
      />
    </DashboardLayout>
  )
}

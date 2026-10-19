import { useState, useEffect } from 'react'
import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import PageHeader from '../../../components/PageHeader/PageHeader'
import ConsoleCard from '../../../components/ConsoleCard/ConsoleCard'
import StatChip from '../../../components/StatChip/StatChip'
import FormField from '../../../components/FormField/FormField'
import Alert from '../../../components/Alert/Alert'
import Button from '../../../components/Button/Button'
import { Input } from '../../../components/Input/Input'
import Actions from '../../../components/Actions/Actions'
import ApiState from '../../../components/ApiState/ApiState'
import { ChartBar, CheckCircle, SlidersHorizontal, ArrowClockwise, Gauge, Database, MagnifyingGlass, Warning } from 'phosphor-react'
import ConfirmModal from '../../../components/ConfirmModal/ConfirmModal'
import { useApi } from '../../../lib/useApi'
import { motor, similitudes } from '../../../lib/recursos'
import { useMotor } from '../../../contexts/MotorContext'
import s from './ConfigSimilitud.module.css'

export default function ConfigSimilitud() {
  // Fuente única: la API. Config vigente del motor + conteo de coincidencias.
  const { data, cargando, error, recargar } = useApi(
    async () => {
      const [configMotor, listaSimilitudes] = await Promise.all([motor.obtener(), similitudes.listar()])
      return { configMotor, listaSimilitudes }
    },
    [],
    { inicial: null }
  )

  // El umbral gobierna la clasificación (A/B/C) en toda la app: al cambiarlo,
  // se refresca el contexto para que los badges/niveles se recalculen.
  const { recargar: recargarMotor } = useMotor()

  const vigente = data?.configMotor || { umbral: 0.30, meses: 12 }
  const totalSimilitudes = (data?.listaSimilitudes || []).length

  const [umbral, setUmbral] = useState('')
  const [meses, setMeses] = useState('')
  const [errores, setErrores] = useState({})
  const [msg, setMsg] = useState(null)
  const [msgTipo, setMsgTipo] = useState('ok')
  const [confirmRecalcular, setConfirmRecalcular] = useState(false)
  const [guardando, setGuardando] = useState(false)

  // Sincroniza el formulario cuando llega la config de la API.
  useEffect(() => {
    if (data?.configMotor) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setUmbral(Math.round(data.configMotor.umbral * 100))
      setMeses(data.configMotor.meses)
    }
  }, [data?.configMotor])

  const guardar = async (e) => {
    e.preventDefault()
    const errs = {}
    const u = Number(umbral)
    const m = Math.round(Number(meses))
    if (!Number.isFinite(u) || u < 5 || u > 95) errs.umbral = 'Ingresa un porcentaje entre 5 y 95.'
    if (!Number.isFinite(m) || m < 1 || m > 60) errs.meses = 'Ingresa entre 1 y 60 meses.'
    setErrores(errs)
    if (Object.keys(errs).length > 0) return

    setGuardando(true)
    setMsg(null)
    try {
      await motor.actualizar(u / 100, m)
      await recargar()
      await recargarMotor()
      setMsgTipo('ok')
      setMsg(`Motor actualizado: umbral ${u}% y ventana de ${m} meses. Aplica a las próximas detecciones; usa Recalcular para la base existente.`)
    } catch (err2) {
      setMsgTipo('error')
      setMsg(err2?.data?.message || 'No fue posible guardar la configuración.')
    } finally {
      setGuardando(false)
    }
  }

  const ejecutarRecalcular = async () => {
    setConfirmRecalcular(false)
    try {
      const res = await similitudes.recalcular()
      await recargar()
      await recargarMotor()
      setMsgTipo('ok')
      setMsg(`Recalibración lista: ${res?.eliminadas ?? 0} coincidencia(s) fuera de regla eliminadas, ${res?.creadas ?? 0} nueva(s) detectada(s).`)
    } catch (err) {
      setMsgTipo('error')
      setMsg(err?.data?.message || 'No se pudo recalcular la base de coincidencias.')
    }
  }

  return (
    <DashboardLayout role="admin" titulo="Motor de Similitudes">
      <div className={s.page}>
        <PageHeader
          title="Motor de similitudes"
          subtitle={`Umbral vigente: ${Math.round(vigente.umbral * 100)}% · Ventana: ${vigente.meses} meses.`}
          icon={<SlidersHorizontal />}
          breadcrumb={[
            { label: 'Dashboard', to: '/admin/dashboard', icon: <ChartBar size={14} /> },
            { label: 'Motor de similitudes' },
          ]}
        />

        {msg && (
          <Alert variant={msgTipo === 'error' ? 'danger' : undefined}>
            {msgTipo === 'error' ? <Warning size={14} /> : <CheckCircle size={14} />} {msg}
          </Alert>
        )}

        <ApiState cargando={cargando} error={error} onReintentar={recargar}>
          <div className={s.readout} role="status" aria-label="Lectura vigente del motor">
            <StatChip icon={<Gauge size={14} />} label="Umbral" value={`${Math.round(vigente.umbral * 100)}%`} />
            <StatChip icon={<Database size={14} />} label="Ventana" value={`${vigente.meses}M`} />
            <StatChip icon={<MagnifyingGlass size={14} />} label="Coincidencias" value={totalSimilitudes} />
          </div>

          <div className={s.grid}>
            <ConsoleCard title="Parámetros de detección" subtitle="Aplica a las próximas detecciones" glow>
              <form className={s.form} onSubmit={guardar} noValidate>
                <FormField
                  label="Porcentaje límite de coincidencia"
                  required
                  error={errores.umbral}
                  help="Se alerta si una propuesta supera este porcentaje frente a otra del mismo programa."
                >
                  <Input
                    type="number"
                    min={5}
                    max={95}
                    value={umbral}
                    onChange={(e) => setUmbral(e.target.value)}
                  />
                </FormField>
                <FormField
                  label="Antigüedad máxima del corpus (meses)"
                  required
                  error={errores.meses}
                  help="Las propuestas más viejas siguen en el historial, pero dejan de generar coincidencias."
                >
                  <Input
                    type="number"
                    min={1}
                    max={60}
                    value={meses}
                    onChange={(e) => setMeses(e.target.value)}
                  />
                </FormField>
                <Actions form>
                  <Button type="submit" disabled={guardando}>
                    <CheckCircle size={14} /> {guardando ? 'Guardando…' : 'Guardar parámetros'}
                  </Button>
                  <Button type="button" variant="secondary" onClick={() => setConfirmRecalcular(true)}>
                    <ArrowClockwise size={14} /> Recalcular base existente
                  </Button>
                </Actions>
              </form>
            </ConsoleCard>

            <ConsoleCard title="Base de comparación">
              <p className={s.baseText}>
                El motor compara cada propuesta contra las <strong>propuestas aprobadas</strong> del
                mismo programa de los últimos <strong>{vigente.meses} meses</strong>, con{' '}
                <strong>{totalSimilitudes} coincidencia(s) válida(s)</strong> a la fecha.
                Así aprobar siempre sigue siendo posible aunque la base crezca.
              </p>
              <p className={`mono ${s.pipeline}`}>propuestas → TF-IDF + coseno → umbral {Math.round(vigente.umbral * 100)}% → coincidencias</p>
            </ConsoleCard>
          </div>
        </ApiState>
      </div>
      <ConfirmModal
        open={confirmRecalcular}
        titulo="Recalcular coincidencias"
        mensaje={`Se recalcularán las coincidencias con el umbral vigente de ${Math.round(vigente.umbral * 100)}% y ventana de ${vigente.meses} meses. Las que queden por debajo dejarán de considerarse vigentes (se conservan como evidencia) y se generarán las nuevas que superen el umbral. ¿Continuar?`}
        textoConfirmar="Sí, recalcular"
        responsabilidad
        onConfirmar={ejecutarRecalcular}
        onCancelar={() => setConfirmRecalcular(false)}
      />
    </DashboardLayout>
  )
}

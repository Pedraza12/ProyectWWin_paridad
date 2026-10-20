import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowRight, Bug, ChartBar, CheckCircle, FileText, MagnifyingGlass, User, Warning } from 'phosphor-react'
import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import PageHeader from '../../../components/PageHeader/PageHeader'
import DataPanel from '../../../components/DataPanel/DataPanel'
import Badge from '../../../components/Badge/Badge'
import StatusMark from '../../../components/StatusMark/StatusMark'
import {
  REPORTE_STATUS,
  REPORTE_ESTADO_LABEL as ESTADO_LABEL,
  REPORTE_TIPO_LABEL as TIPO_LABEL,
  REPORTE_PRIORIDAD_META as PRIORIDAD_META,
  REPORTE_PRIORIDAD_POR_TIPO as PRIORIDAD_POR_TIPO,
} from '../../../constants/estadoStatus'
import { RUTA_SOPORTE_POR_ROL } from '../../../constants/routes'
import Avatar from '../../../components/Avatar/Avatar'
import Button from '../../../components/Button/Button'
import { Select, Textarea } from '../../../components/Input/Input'
import Lightbox from '../../../components/Lightbox/Lightbox'
import EmptyState from '../../../components/EmptyState/EmptyState'
import ApiState from '../../../components/ApiState/ApiState'
import { useApi } from '../../../lib/useApi'
import { reportes } from '../../../lib/recursos'
import { fechaDesdeApi, nombreCompleto } from '../../../utils/helpers'
import s from './DetalleReporte.module.css'





const ESTADOS = ['pendiente', 'en_revision', 'resuelto', 'cerrado', 'rechazado']


export default function DetalleReporte() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [nuevoEstado, setNuevoEstado] = useState('pendiente')
  const [respuesta, setRespuesta] = useState('')
  const [guardado, setGuardado] = useState(false)
  const [accionMsg, setAccionMsg] = useState(null)
  const [enviandoEstado, setEnviandoEstado] = useState(false)
  const [fotoViendo, setFotoViendo] = useState(null)

  // Fuente única: la API (reporte + usuario reportante incluido).
  const { data: reporte, cargando, error, recargar } = useApi(
    () => reportes.obtener(id),
    [id],
    { inicial: null }
  )

  // Sincroniza el selector al navegar entre reportes sin remontar.
  useEffect(() => {
    if (reporte) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setNuevoEstado(reporte.estado || 'pendiente')
      setRespuesta(reporte.respuesta || '')
      setGuardado(false)
    }
  }, [reporte?.id]) // eslint-disable-line react-hooks/exhaustive-deps

  if (cargando || error || !reporte) {
    return (
      <DashboardLayout role="admin" titulo="Detalle de Reporte">
        <div className={s.page}>
          {error ? (
            <EmptyState
              icon={<MagnifyingGlass />}
              title="No se pudo cargar el reporte"
              message={error.message || 'Ocurrió un error al consultar la API.'}
              actionLabel="Reintentar"
              onAction={recargar}
            />
          ) : cargando ? (
            <ApiState cargando error={null} />
          ) : (
            <EmptyState
              icon={<MagnifyingGlass />}
              title="Solicitud no encontrada"
              message="La solicitud de soporte que buscas no existe."
              actionLabel="Volver a soporte"
              onAction={() => navigate(RUTA_SOPORTE_POR_ROL.admin)}
            />
          )}
        </div>
      </DashboardLayout>
    )
  }

  const reportante = reporte.generalUser || null
  const prioridadInfo = PRIORIDAD_POR_TIPO[reporte.tipo] || PRIORIDAD_META.media

  const guardarEstado = async (e) => {
    e.preventDefault()
    if (enviandoEstado) return
    setAccionMsg(null)
    setEnviandoEstado(true)
    try {
      // El backend registra la bitácora y avisa al solicitante cuando la
      // solicitud se cierra (resuelta/rechazada).
      await reportes.actualizar(reporte.id, {
        titulo: reporte.titulo,
        numero_ficha: reporte.numero_ficha,
        motivo: reporte.motivo,
        descripcion: reporte.descripcion,
        tipo: reporte.tipo,
        estado: nuevoEstado,
        respuesta: respuesta.trim() || null,
        fecha: reporte.fecha,
        id_usuario: reporte.id_usuario,
      })
      await recargar()
      setGuardado(true)
    } catch (err) {
      setAccionMsg(err?.data?.message || 'No se pudo actualizar el estado de la solicitud.')
    } finally {
      setEnviandoEstado(false)
    }
  }

  return (
    <DashboardLayout role="admin" titulo="Detalle de Soporte">
      <div className={s.page}>
        <PageHeader
          title={reporte.titulo}
          subtitle={`Solicitud #${reporte.id} · Recibida el ${fechaDesdeApi(reporte.fecha)}`}
          icon={<Bug />}
          breadcrumb={[
            { label: 'Dashboard', to: '/admin/dashboard', icon: <ChartBar size={14} /> },
            { label: 'Soporte', to: RUTA_SOPORTE_POR_ROL.admin, icon: <Bug size={14} /> },
            { label: `#${reporte.id}` },
          ]}
        />

        {guardado && (
          <p className={s.alertSuccess} role="status">
            <CheckCircle size={14} /> La solicitud se actualizó correctamente.
          </p>
        )}

        {accionMsg && (
          <p className={s.alertError} role="alert">
            <Warning size={14} /> {accionMsg}
          </p>
        )}

        <DataPanel
          title="Información de la solicitud"
          icon={<FileText />}
          action={
            <StatusMark status={REPORTE_STATUS[reporte.estado] || 'pending'} label={ESTADO_LABEL[reporte.estado] || reporte.estado} />
          }
        >
          <p className={s.description}>{reporte.descripcion}</p>

          <dl className={s.grid}>
            <div className={s.cell}>
              <dt>Tipo</dt>
              <dd>{TIPO_LABEL[reporte.tipo] || reporte.tipo}</dd>
            </div>
            <div className={s.cell}>
              <dt>Prioridad</dt>
              <dd>
                <Badge variant={prioridadInfo.variant}>{prioridadInfo.label}</Badge>
              </dd>
            </div>
            {reporte.numero_ficha && (
              <div className={s.cell}>
                <dt>Número de ficha</dt>
                <dd><code>{reporte.numero_ficha}</code></dd>
              </div>
            )}
            {reporte.motivo && (
              <div className={s.cell}>
                <dt>Motivo</dt>
                <dd>{reporte.motivo}</dd>
              </div>
            )}
            <div className={s.cell}>
              <dt>Fecha de la solicitud</dt>
              <dd>{fechaDesdeApi(reporte.fecha)}</dd>
            </div>
            <div className={s.cell}>
              <dt>Última actualización</dt>
              <dd>{fechaDesdeApi(reporte.updated_at || reporte.fecha)}</dd>
            </div>
          </dl>

          {reporte.respuesta && (
            <p className={s.description}>
              <strong>Respuesta enviada:</strong> {reporte.respuesta}
            </p>
          )}

          <form className={s.statusForm} onSubmit={guardarEstado}>
            <label className={s.statusField}>
              <span className={s.statusLabel}>Cambiar estado</span>
              <Select
                value={nuevoEstado}
                onChange={(e) => {
                  setNuevoEstado(e.target.value)
                  setGuardado(false)
                }}
              >
                {ESTADOS.map((est) => (
                  <option key={est} value={est}>
                    {ESTADO_LABEL[est]}
                  </option>
                ))}
              </Select>
            </label>
            <label className={s.statusField}>
              <span className={s.statusLabel}>Respuesta al solicitante</span>
              <Textarea
                rows={3}
                value={respuesta}
                onChange={(e) => {
                  setRespuesta(e.target.value)
                  setGuardado(false)
                }}
                maxLength={2000}
                placeholder="Explica cómo se resolvió (se envía al cerrar la solicitud)."
              />
            </label>
            <Button
              type="submit"
              disabled={enviandoEstado || (nuevoEstado === reporte.estado && respuesta === (reporte.respuesta || ''))}
            >
              <CheckCircle size={14} /> Guardar
            </Button>
          </form>
        </DataPanel>

        <DataPanel title="Información del reportante" icon={<User />}>
          {reportante ? (
            <div className={s.personCard}>
              {reportante.foto_url ? (
                <button type="button" className={s.avatarBtn} title="Ver foto" aria-label="Ver foto del reportante" onClick={() => setFotoViendo({ src: reportante.foto_url, alt: nombreCompleto(reportante) })}>
                  <Avatar name={nombreCompleto(reportante)} src={reportante.foto_url} size="md" />
                </button>
              ) : (
                <Avatar name={nombreCompleto(reportante)} size="md" />
              )}
              <div className={s.personInfo}>
                <span className={s.personName}>{nombreCompleto(reportante)}</span>
                <span className={s.personEmail}>{reportante.correo}</span>
              </div>
              <Button as="link" to={`/admin/detalle-usuario/${reportante.id}`} viewTransition variant="secondary">
                Ver usuario <ArrowRight size={14} />
              </Button>
            </div>
          ) : (
            <p className={s.muted}>
              Reportado por <strong>{`Usuario #${reporte.id_usuario}`}</strong> (usuario no registrado o
              eliminado).
            </p>
          )}
        </DataPanel>
      </div>
      {fotoViendo && <Lightbox src={fotoViendo.src} alt={fotoViendo.alt} caption={fotoViendo.alt} onClose={() => setFotoViendo(null)} />}
    </DashboardLayout>
  )
}

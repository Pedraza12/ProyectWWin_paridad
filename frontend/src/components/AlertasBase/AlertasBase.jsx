import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell, MagnifyingGlass, CheckCircle, ChatCircle, GearSix, Trash } from 'phosphor-react'
import PageHeader from '../PageHeader/PageHeader'
import Badge from '../Badge/Badge'
import Button from '../Button/Button'
import EmptyState from '../EmptyState/EmptyState'
import ApiState from '../ApiState/ApiState'
import Alert from '../Alert/Alert'
import ConfirmModal from '../ConfirmModal/ConfirmModal'
import { useAuth } from '../../contexts/AuthContext'
import { useApi } from '../../lib/useApi'
import { notificaciones as apiNotificaciones } from '../../lib/recursos'
import { fechaDesdeApi } from '../../utils/helpers'
import s from './AlertasBase.module.css'

const TIPO_CONFIG = {
  observacion: { icon: <ChatCircle size={18} />, label: 'Observación', variant: 'primary' },
  similitud: { icon: <MagnifyingGlass size={18} />, label: 'Similitud', variant: 'warning' },
  revision: { icon: <CheckCircle size={18} />, label: 'Revisión', variant: 'info' },
  mensaje: { icon: <ChatCircle size={18} />, label: 'Mensaje', variant: 'neutral' },
  sistema: { icon: <GearSix size={18} />, label: 'Sistema', variant: 'primary' },
}

// La API guarda el destino como 'proyecto:<id>', 'reporte:<id>' o 'ficha:<id>'.
function decodificarEnlace(enlace) {
  if (!enlace) return {}
  const [tipo, valor] = String(enlace).split(':')
  if (tipo === 'proyecto') return { projectId: Number(valor) }
  if (tipo === 'reporte') return { reporteId: Number(valor) }
  if (tipo === 'ficha') return { fichaId: Number(valor) }
  return {}
}

export default function AlertasBase({ titulo, subtitle, detallePath, emptyActionLabel, emptyActionTo, puedeEliminar = false }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [aEliminar, setAEliminar] = useState(null)
  const [errorEliminar, setErrorEliminar] = useState('')

  // Notificaciones del usuario autenticado (fuente única: la API).
  const { data, cargando, error, recargar } = useApi(
    () => apiNotificaciones.listar(),
    [user?.id],
    { inicial: [] }
  )

  const alertas = useMemo(() => {
    if (!user) return []
    return (data || [])
      .filter((n) => Number(n.id_usuario) === Number(user.id))
      .sort((a, b) => Number(b.id) - Number(a.id))
  }, [data, user])

  const sinLeer = useMemo(() => alertas.filter((n) => !n.leida).length, [alertas])

  const marcarTodas = async () => {
    const pendientes = alertas.filter((n) => !n.leida)
    await Promise.all(pendientes.map((n) => apiNotificaciones.marcarLeida(n, true).catch(() => null)))
    await recargar()
  }

  const handleClick = async (n) => {
    if (!n.leida) {
      try {
        await apiNotificaciones.marcarLeida(n, true)
        await recargar()
      } catch {
        // Si falla la marca, igualmente se navega al destino.
      }
    }
    const { projectId, reporteId, fichaId } = decodificarEnlace(n.enlace)
    if (projectId) navigate(`${detallePath}/detalle-proyecto/${projectId}`)
    // Solo el admin tiene detalle-reporte; en otros roles solo se marca como leída.
    else if (reporteId && detallePath === '/admin') navigate(`${detallePath}/detalle-reporte/${reporteId}`)
    // Movimientos de ficha: el aprendiz va a su ficha; el staff al detalle.
    else if (fichaId) {
      if (detallePath === '/aprendiz') navigate('/aprendiz/ficha')
      else navigate(`${detallePath}/detalle-ficha/${fichaId}`)
    }
  }

  async function confirmarEliminar() {
    if (!aEliminar) return
    setErrorEliminar('')
    try {
      await apiNotificaciones.eliminar(aEliminar.id)
      setAEliminar(null)
      await recargar()
    } catch (err) {
      setAEliminar(null)
      setErrorEliminar(err?.data?.message || 'No se pudo eliminar la notificación.')
    }
  }

  return (
    <div className={s.wrapper}>
      <PageHeader
        title={titulo}
        subtitle={subtitle}
        icon={<Bell size={20} />}
        actions={
          <Button type="button" variant="secondary" onClick={marcarTodas} disabled={sinLeer === 0}>
            <CheckCircle size={14} /> Marcar todas como leídas
          </Button>
        }
      />

      {errorEliminar && <Alert variant="danger">{errorEliminar}</Alert>}

      <ApiState cargando={cargando} error={error} onReintentar={recargar}>
        {sinLeer > 0 && (
          <p className={s.summary}>
            Tienes <strong className={`mono ${s.sinLeer}`}>{sinLeer}</strong> notificaci{sinLeer !== 1 ? 'ones' : 'ón'} sin leer.
          </p>
        )}

        {alertas.length === 0 ? (
          <EmptyState
            icon={<Bell size={40} />}
            title="Sin notificaciones por ahora"
            message="Cuando haya novedades sobre tus proyectos o similitudes, aparecerán aquí."
            actionLabel={emptyActionLabel}
            onAction={emptyActionTo ? () => navigate(emptyActionTo) : undefined}
          />
        ) : (
          <ul className={s.list}>
            {alertas.map((n) => {
              const info = TIPO_CONFIG[n.tipo] || TIPO_CONFIG.sistema
              const { projectId, reporteId, fichaId } = decodificarEnlace(n.enlace)
              const puedeAbrir = Boolean(projectId) || Boolean(reporteId && detallePath === '/admin') || Boolean(fichaId)
              return (
                <li key={n.id} className={s.row}>
                  <button
                    type="button"
                    className={`${s.item} ${puedeEliminar ? s.itemDeletable : ''} ${s[`tipo-${n.tipo}`] || ''} ${!n.leida ? s.unread : ''}`}
                    onClick={() => handleClick(n)}
                    title={puedeAbrir ? 'Abrir detalle' : 'Marcar como leída'}
                  >
                    <span className={s.rail} aria-hidden="true" />
                    <span className={s.icon} aria-hidden="true">{info.icon}</span>
                    <span className={s.body}>
                      <span className={s.top}>
                        <Badge variant={info.variant}>{info.label}</Badge>
                        {!n.leida && <span className={s.unreadDot}>Nueva</span>}
                        <time className={`mono ${s.date}`}>{fechaDesdeApi(n.fecha)}</time>
                      </span>
                      <span className={s.message}>{n.titulo}</span>
                    </span>
                  </button>
                  {puedeEliminar && (
                    <button
                      type="button"
                      className={s.deleteBtn}
                      onClick={() => setAEliminar(n)}
                      title="Eliminar notificación"
                    >
                      <Trash size={12} /> Eliminar
                    </button>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </ApiState>

      <ConfirmModal
        open={!!aEliminar}
        titulo="Eliminar notificación"
        mensaje="¿Seguro que deseas eliminar esta notificación? Esta acción no se puede deshacer."
        textoConfirmar="Sí, eliminar"
        responsabilidad
        onConfirmar={confirmarEliminar}
        onCancelar={() => setAEliminar(null)}
      />
    </div>
  )
}

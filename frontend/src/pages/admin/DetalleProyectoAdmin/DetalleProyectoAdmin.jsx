import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowRight, ChatCircle, CheckCircle, FileText, FolderOpen, GraduationCap, MagnifyingGlass, PencilSimple, Plus, Trash, X, Warning } from 'phosphor-react'
import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import PageHeader from '../../../components/PageHeader/PageHeader'
import DataPanel from '../../../components/DataPanel/DataPanel'
import Button from '../../../components/Button/Button'
import Alert from '../../../components/Alert/Alert'
import { Input, Select, Textarea } from '../../../components/Input/Input'
import FormField from '../../../components/FormField/FormField'
import { MAX_TITULO } from '../../../utils/validation'
import Avatar from '../../../components/Avatar/Avatar'
import Lightbox from '../../../components/Lightbox/Lightbox'
import EmptyState from '../../../components/EmptyState/EmptyState'
import ConfirmModal from '../../../components/ConfirmModal/ConfirmModal'
import ApiState from '../../../components/ApiState/ApiState'
import ObservacionHilo from '../../../components/ObservacionHilo/ObservacionHilo'
import HistorialProyecto from '../../../components/HistorialProyecto/HistorialProyecto'
import SimilitudesHistoricas from '../../../components/SimilitudesHistoricas/SimilitudesHistoricas'
import GradeBadge from '../../../components/GradeBadge/GradeBadge'
import { useAuth } from '../../../contexts/AuthContext'
import { useApi } from '../../../lib/useApi'
import { proyectos, similitudes, observaciones } from '../../../lib/recursos'
import { agruparObservaciones, fechaDesdeApi, nombreCompleto } from '../../../utils/helpers'
import s from '../../../components/DetalleProyectoBase/DetalleProyectoBase.module.css'
import InformacionProyecto from '../../../components/DetalleProyectoBase/InformacionProyecto'
import { PROJECT_ESTADO_LABEL as ESTADO_LABEL, ROL_LABEL as ROL_CHIP } from '../../../constants/badgeVariants'

const ESTADOS = ['pendiente', 'aprobado', 'rechazado']

// Rol legible para el chip del hilo de observaciones.


// Campos que acepta PUT /projects (varios son obligatorios).
function payloadProyecto(proyecto, extra = {}) {
  return {
    titulo: proyecto.titulo,
    resumen: proyecto.resumen,
    palabras_clave: proyecto.palabras_clave,
    area_aplicacion: proyecto.area_aplicacion,
    objetivo_general: proyecto.objetivo_general,
    objetivos_especificos: proyecto.objetivos_especificos,
    estado: proyecto.estado,
    id_creador: proyecto.id_creador,
    id_instructor_asignado: proyecto.id_instructor_asignado,
    id_class_group: proyecto.id_class_group,
    ...extra,
  }
}

export default function DetalleProyectoAdmin() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [textoObs, setTextoObs] = useState('')
  const [respondiendoA, setRespondiendoA] = useState(null)
  const [modalEliminar, setModalEliminar] = useState(false)
  const [fotoViendo, setFotoViendo] = useState(null)
  const [obsAEliminar, setObsAEliminar] = useState(null)
  const [accionMsg, setAccionMsg] = useState(null)

  // Fuente única: la API. Propuesta + similitudes + observaciones del hilo.
  const { data, cargando, error, recargar } = useApi(
    async () => {
      const [proyecto, listaSimilitudes, listaObservaciones] = await Promise.all([
        proyectos.obtener(id),
        similitudes.listar({ proyecto_id: id }),
        observaciones.listar('user', { id_proyecto: id }),
      ])
      return { proyecto, listaSimilitudes, listaObservaciones }
    },
    [id],
    { inicial: null }
  )

  const proyecto = data?.proyecto || null

  const [nuevoEstado, setNuevoEstado] = useState('pendiente')
  const [guardado, setGuardado] = useState(false)
  const [accionOcupada, setAccionOcupada] = useState(false)
  const [editando, setEditando] = useState(false)
  const [form, setForm] = useState({ titulo: '', resumen: '', palabras_clave: '' })
  const [errores, setErrores] = useState({})
  const [editMsg, setEditMsg] = useState(null)

  // Sincroniza los selectores al navegar entre proyectos sin remontar.
  useEffect(() => {
    if (proyecto) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setNuevoEstado(proyecto.estado)
      setGuardado(false)
      setEditando(false)
      setEditMsg(null)
    }
  }, [proyecto?.id]) // eslint-disable-line react-hooks/exhaustive-deps

  if (cargando || error || !proyecto) {
    return (
      <DashboardLayout role="admin" titulo="Detalle de Propuesta">
        <div className={s.page}>
          {error ? (
            <EmptyState
              icon={<MagnifyingGlass />}
              title="No se pudo cargar la propuesta"
              message={error.message || 'Ocurrió un error al consultar la API.'}
              actionLabel="Reintentar"
              onAction={recargar}
            />
          ) : cargando ? (
            <ApiState cargando error={null} />
          ) : (
            <EmptyState
              icon={<MagnifyingGlass />}
              title="Propuesta no encontrada"
              message="La propuesta que buscas no existe o fue eliminada."
              actionLabel="Volver a propuestas"
              onAction={() => navigate('/admin/proyectos')}
            />
          )}
        </div>
      </DashboardLayout>
    )
  }

  const estudiante = proyecto.creator || null
  const similitudesProyecto = (data.listaSimilitudes || [])
    .filter(
      (sim) => Number(sim.id_proyecto_1) === Number(proyecto.id) || Number(sim.id_proyecto_2) === Number(proyecto.id)
    )
    .sort((a, b) => Number(b.porcentaje) - Number(a.porcentaje))

  // Observaciones de la API -> shape que espera ObservacionHilo.
  const observacionesMapeadas = (data.listaObservaciones || []).map((o) => ({
    id: o.id,
    autor: `${nombreCompleto(o.user) || 'Usuario'} | ${ROL_CHIP[o.user?.rol] || 'Usuario'}`,
    fecha: fechaDesdeApi(o.created_at),
    texto: o.texto,
    respuestaA: o.respuesta_a,
  }))

  const guardarEstado = async (e) => {
    e.preventDefault()
    if (accionOcupada || nuevoEstado === proyecto.estado) return
    setAccionMsg(null)
    setAccionOcupada(true)
    try {
      // El backend registra historial y avisa al creador (F1/F3).
      await proyectos.actualizar(proyecto.id, payloadProyecto(proyecto, { estado: nuevoEstado }))
      await recargar()
      setGuardado(true)
      setTimeout(() => setGuardado(false), 3000)
    } catch (err) {
      setAccionMsg(err?.data?.message || 'No se pudo actualizar el estado de la propuesta.')
    } finally {
      setAccionOcupada(false)
    }
  }

  const confirmarEliminar = async () => {
    try {
      await proyectos.eliminar(proyecto.id)
      navigate('/admin/proyectos')
    } catch (err) {
      setModalEliminar(false)
      setAccionMsg(err?.data?.message || 'No se pudo eliminar la propuesta.')
    }
  }

  const agregarObservacion = async (e) => {
    e.preventDefault()
    if (accionOcupada) return
    const texto = textoObs.trim()
    if (!texto) return
    setAccionOcupada(true)
    try {
      await observaciones.crear({
        texto,
        id_proyecto: proyecto.id,
        id_usuario: Number(user?.id),
        respuesta_a: respondiendoA?.id || null,
      })
      await recargar()
      setTextoObs('')
      setRespondiendoA(null)
    } catch (err) {
      setAccionMsg(err?.data?.message || 'No se pudo publicar la observación.')
    } finally {
      setAccionOcupada(false)
    }
  }

  const confirmarEliminarObservacion = async () => {
    if (!obsAEliminar) return
    if (respondiendoA?.id === obsAEliminar.id) setRespondiendoA(null)
    try {
      await observaciones.eliminar(obsAEliminar.id)
      await recargar()
    } catch (err) {
      setAccionMsg(err?.data?.message || 'No se pudo eliminar la observación.')
    } finally {
      setObsAEliminar(null)
    }
  }

  const ficha = proyecto.classGroup || null

  const iniciarEdicion = () => {
    setForm({
      titulo: proyecto.titulo || '',
      resumen: proyecto.resumen || '',
      palabras_clave: proyecto.palabras_clave || '',
    })
    setErrores({})
    setEditMsg(null)
    setEditando(true)
  }

  const guardarEdicion = async (e) => {
    e.preventDefault()
    if (accionOcupada) return
    const errs = {}
    if (form.titulo.trim().length < 5) errs.titulo = 'El título debe tener al menos 5 caracteres.'
    if (form.resumen.trim().length < 20) errs.resumen = 'La descripción debe tener al menos 20 caracteres.'
    setErrores(errs)
    if (Object.keys(errs).length > 0) return
    const eraAprobada = proyecto.estado === 'aprobado'
    setAccionOcupada(true)
    try {
      await proyectos.actualizar(proyecto.id, payloadProyecto(proyecto, {
        titulo: form.titulo.trim(),
        resumen: form.resumen.trim(),
        palabras_clave: form.palabras_clave.trim(),
      }))
      await recargar()
      setEditando(false)
      setEditMsg(eraAprobada
        ? 'Contenido actualizado. La propuesta volvió a En revisión por intervención administrativa.'
        : 'Contenido actualizado correctamente.')
    } catch (err) {
      setErrores({ titulo: err?.data?.message || 'No se pudo actualizar el contenido.' })
    } finally {
      setAccionOcupada(false)
    }
  }

  return (
    <DashboardLayout role="admin" titulo="Detalle de Propuesta">
      <div className={s.page}>
        <PageHeader
          title={proyecto.titulo}
          subtitle={`Enviado el ${fechaDesdeApi(proyecto.created_at)} por ${nombreCompleto(estudiante) || '—'}`}
          icon={<FolderOpen />}
          breadcrumb={[
            { label: 'Dashboard', to: '/admin/dashboard' },
            { label: 'Proyectos', to: '/admin/proyectos' },
            { label: proyecto.titulo },
          ]}
        />

        {guardado && (
          <Alert><CheckCircle size={14} /> Estado actualizado correctamente.</Alert>
        )}

        {accionMsg && (
          <Alert variant="danger"><Warning size={14} /> {accionMsg}</Alert>
        )}

        <div className={s.dossier}>
          <div className={s.colPrincipal}>
            <DataPanel
              title="Información del proyecto"
              icon={<FileText />}
              action={
                <form onSubmit={guardarEstado} className={s.stateForm}>
                  <Select
                    value={nuevoEstado}
                    onChange={(e) => { setNuevoEstado(e.target.value); setGuardado(false) }}
                    aria-label="Cambiar estado"
                  >
                    {ESTADOS.map((est) => (
                      <option key={est} value={est}>
                        {ESTADO_LABEL[est] || est}
                      </option>
                    ))}
                  </Select>
                  <Button type="submit" disabled={accionOcupada || nuevoEstado === proyecto.estado}>
                    <CheckCircle size={14} /> Guardar
                  </Button>
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => (editando ? setEditando(false) : iniciarEdicion())}
                  >
                    <PencilSimple size={14} /> {editando ? 'Cancelar edición' : 'Editar'}
                  </Button>
                  <Button
                    type="button"
                    variant="dangerGhost"
                    onClick={() => setModalEliminar(true)}
                  >
                    <Trash size={14} /> Eliminar
                  </Button>
                </form>
              }
            >
              {editMsg && (
                <Alert><CheckCircle size={14} /> {editMsg}</Alert>
              )}
              {editando ? (
                <form className={s.obsForm} onSubmit={guardarEdicion} noValidate>
                  {proyecto.estado === 'aprobado' && (
                    <Alert variant="warning">
                      <Warning size={14} /> Intervención administrativa: al guardar contenido de una
                      propuesta aprobada, volverá a En revisión.
                    </Alert>
                  )}
                  <FormField label="Título" required error={errores.titulo}>
                    <Input
                      value={form.titulo}
                      onChange={(e) => setForm((f) => ({ ...f, titulo: e.target.value }))}
                      maxLength={MAX_TITULO}
                    />
                  </FormField>
                  <FormField label="Descripción" required error={errores.resumen}>
                    <Textarea
                      rows={4}
                      value={form.resumen}
                      onChange={(e) => setForm((f) => ({ ...f, resumen: e.target.value }))}
                    />
                  </FormField>
                  <FormField label="Palabras clave" help="Separadas por comas.">
                    <Input
                      value={form.palabras_clave}
                      onChange={(e) => setForm((f) => ({ ...f, palabras_clave: e.target.value }))}
                    />
                  </FormField>
                  <Button type="submit" disabled={accionOcupada}>
                    <CheckCircle size={14} /> Guardar contenido
                  </Button>
                </form>
              ) : (
                <InformacionProyecto proyecto={proyecto} ficha={ficha} fichaHref={ficha ? `/admin/detalle-ficha/${ficha.id}` : null} />
              )}
            </DataPanel>
          </div>

          <aside className={s.rail} aria-label="Aprendiz, similitudes y observaciones">
            <DataPanel title="Información del aprendiz" icon={<GraduationCap />}>
              {estudiante ? (
                <div className={s.personCard}>
                  {estudiante.foto_url ? (
                    <button type="button" className={s.avatarBtn} title="Ver foto" aria-label="Ver foto del aprendiz" onClick={() => setFotoViendo({ src: estudiante.foto_url, alt: nombreCompleto(estudiante) })}>
                      <Avatar name={nombreCompleto(estudiante)} src={estudiante.foto_url} size="md" />
                    </button>
                  ) : (
                    <Avatar name={nombreCompleto(estudiante)} size="md" />
                  )}
                  <div className={s.personInfo}>
                    <span className={s.personName}>{nombreCompleto(estudiante)}</span>
                    <span className={s.personEmail}>{estudiante.correo}</span>
                  </div>
                  <Button as="link" to={`/admin/detalle-usuario/${estudiante.id}`} variant="secondary">
                    Ver usuario <ArrowRight size={14} />
                  </Button>
                </div>
              ) : (
                <p className={s.muted}>No se encontró la información del aprendiz.</p>
              )}
            </DataPanel>

            <DataPanel title="Similitudes detectadas" icon={<MagnifyingGlass />}>
              {similitudesProyecto.length === 0 ? (
                <p className={s.muted}>No se han detectado similitudes para esta propuesta.</p>
              ) : (
                <ul className={s.simList}>
                  {similitudesProyecto.map((sim) => {
                    const pct = Math.round(Number(sim.porcentaje) || 0)
                    const otro = Number(sim.id_proyecto_1) === Number(proyecto.id) ? sim.project2 : sim.project1
                    return (
                      <li key={sim.id}>
                        <Link to={`/admin/detalle-similitud/${sim.id}`} viewTransition className={s.simRow}>
                          <span className={s.simPair}>
                            vs. {otro?.titulo || 'Propuesta no disponible'}
                          </span>
                          <span className={s.simRight}>
                            <GradeBadge score={pct} size="sm" />
                          </span>
                        </Link>
                      </li>
                    )
                  })}
                </ul>
              )}
              <SimilitudesHistoricas proyectoId={proyecto.id} detalleBase="/admin" />
            </DataPanel>

            <DataPanel title={`Observaciones (${observacionesMapeadas.length})`} icon={<ChatCircle />}>
              {respondiendoA && (
                <div className={s.respondiendoChip}>
                  Respondiendo a {String(respondiendoA.autor).split(' | ')[0]}
                  <button type="button" onClick={() => setRespondiendoA(null)} aria-label="Cancelar respuesta"><X size={12} /></button>
                </div>
              )}
              <ObservacionHilo
                grupos={agruparObservaciones(observacionesMapeadas)}
                permitirResponder
                onRespuesta={(o) => setRespondiendoA(o)}
                permitirEliminar
                onEliminar={(o) => setObsAEliminar(o)}
                className={s.hilosScroll}
              />

              <form className={s.obsForm} onSubmit={agregarObservacion}>
                <Textarea
                  rows={3}
                  value={textoObs}
                  onChange={(e) => setTextoObs(e.target.value)}
                  placeholder="Escribe una observación sobre esta propuesta…"
                  aria-label="Observación sobre esta propuesta"
                />
                <Button type="submit" disabled={!textoObs.trim() || accionOcupada}>
                  <Plus size={14} /> Agregar observación
                </Button>
              </form>
            </DataPanel>

            <HistorialProyecto projectId={proyecto.id} />
          </aside>
        </div>
      </div>

      <ConfirmModal
        open={modalEliminar}
        titulo="Eliminar propuesta"
        mensaje={`¿Seguro que deseas eliminar "${proyecto.titulo}"? Solo se elimina si no tiene similitudes compartidas; las observaciones se borrarán. Esta acción no se puede deshacer.`}
        textoConfirmar="Sí, eliminar"
        responsabilidad
        onConfirmar={confirmarEliminar}
        onCancelar={() => setModalEliminar(false)}
      />
      <ConfirmModal
        open={!!obsAEliminar}
        titulo="Eliminar observación"
        mensaje={
          obsAEliminar
            ? `¿Seguro que deseas eliminar la observación de "${String(obsAEliminar.autor).split(' | ')[0]}"? Esta acción no se puede deshacer.`
            : ''
        }
        textoConfirmar="Sí, eliminar"
        responsabilidad
        onConfirmar={confirmarEliminarObservacion}
        onCancelar={() => setObsAEliminar(null)}
      />
      {fotoViendo && <Lightbox src={fotoViendo.src} alt={fotoViendo.alt} caption={fotoViendo.alt} onClose={() => setFotoViendo(null)} />}
    </DashboardLayout>
  )
}

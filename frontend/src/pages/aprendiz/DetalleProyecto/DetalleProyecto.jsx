import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import PageHeader from
'../../../components/PageHeader/PageHeader'
import DataPanel from '../../../components/DataPanel/DataPanel'
import FormField from '../../../components/FormField/FormField'
import Actions from '../../../components/Actions/Actions'
import Button from '../../../components/Button/Button'
import { Input, Textarea } from '../../../components/Input/Input'
import EmptyState from '../../../components/EmptyState/EmptyState'
import Alert from '../../../components/Alert/Alert'
import ConfirmModal from '../../../components/ConfirmModal/ConfirmModal'
import ApiState from '../../../components/ApiState/ApiState'
import ObservacionHilo from
'../../../components/ObservacionHilo/ObservacionHilo'
import GradeBadge from '../../../components/GradeBadge/GradeBadge'
import HistorialProyecto from '../../../components/HistorialProyecto/HistorialProyecto'
import SimilitudesHistoricas from '../../../components/SimilitudesHistoricas/SimilitudesHistoricas'
import { useAuth } from '../../../contexts/AuthContext'
import { useApi } from '../../../lib/useApi'
import { proyectos, similitudes as similitudesApi, observaciones as observacionesApi, aprendices } from '../../../lib/recursos'
import { toFieldErrors } from '../../../lib/api'
import { agruparObservaciones, fechaDesdeApi, esPropietarioProyecto, nombreCompleto } from '../../../utils/helpers'
import { MAX_TITULO, MAX_DESCRIPCION, MAX_DESCRIPCION_CORTA } from '../../../utils/validation'
import s from '../../../components/DetalleProyectoBase/DetalleProyectoBase.module.css'
import n from '../../../components/FormularioBase/FormularioBase.module.css'
import InformacionProyecto from '../../../components/DetalleProyectoBase/InformacionProyecto'
import { ChatCircle, CheckCircle, FileText, FolderOpen, MagnifyingGlass, PaperPlaneRight, PencilSimple, Plus, Trash, UsersThree, Warning, X } from 'phosphor-react'
import { ROL_LABEL } from '../../../constants/badgeVariants'


function nombreUsuario(u) {
  return nombreCompleto(u, u?.correo || 'Usuario')
}

// Una propuesta es del aprendiz si la creó o si figura en su equipo.
const esMia = esPropietarioProyecto

// Campos del formulario que muestran su error bajo el campo; un 422 con claves
// fuera de esta lista (p. ej. relaciones del payload) no tiene dónde verse.
const CAMPOS_CON_ERROR = ['titulo', 'resumen', 'objetivo_general', 'objetivos_especificos', 'area_aplicacion']

export default function DetalleProyecto() {
  const { id } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()

  // Propuesta con sus relaciones, similitudes del par y comentarios del hilo.
  const { data: project, cargando, error, recargar } = useApi(
    () => proyectos.obtener(id),
    [id],
    { inicial: null }
  )
  const { data: todasSimilitudes, recargar: recargarSimilitudes } = useApi(() => similitudesApi.listar(), [], { inicial: [] })
  const { data: comentariosApi, cargando: cargandoObs, recargar: recargarObs } = useApi(
    () => observacionesApi.listar('user', { id_proyecto: id }),
    [id],
    { inicial: [] }
  )
  // Equipo de la propuesta (pivote) y compañeros de la ficha (para agregar).
  const { data: equipoApi, recargar: recargarEquipo } = useApi(
    () => proyectos.equipo(id),
    [id],
    { inicial: [] }
  )
  const { data: rosterApi } = useApi(() => aprendices.listar(), [], { inicial: [] })

  const [texto, setTexto] = useState('')
  const [respondiendoA, setRespondiendoA] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [obsError, setObsError] = useState('')
  const [modalEliminar, setModalEliminar] = useState(false)
  const [eliminando, setEliminando] = useState(false)
  const [eliminarError, setEliminarError] = useState('')
  const [equipoError, setEquipoError] = useState('')
  const [equipoOcupado, setEquipoOcupado] = useState(false)

  /* ---------- Edición de la propuesta (mientras no esté aprobada) ---------- */
  const [editando, setEditando] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [enviandoPropuesta, setEnviandoPropuesta] = useState(false)
  const [errores, setErrores] = useState({})
  const [aviso, setAviso] = useState('')
  const [avisoError, setAvisoError] = useState('')
  const [form, setForm] = useState({ titulo: '', resumen: '', palabras_clave: '', objetivo_general: '', objetivos_especificos: '', area_aplicacion: '' })

  // Comentarios de la API → shape que consume ObservacionHilo.
  const observaciones = useMemo(
    () => (comentariosApi || []).map((c) => ({
      id: c.id,
      respuestaA: c.respuesta_a,
      autor: `${nombreUsuario(c.user)} | ${ROL_LABEL[c.user?.rol] || 'Comentario'}`,
      texto: c.texto,
      fecha: fechaDesdeApi(c.created_at),
    })),
    [comentariosApi]
  )

  const similitudes = useMemo(
    () => (project
      ? (todasSimilitudes || []).filter(
          (x) => Number(x.id_proyecto_1) === Number(project.id) || Number(x.id_proyecto_2) === Number(project.id)
        )
      : []),
    [project, todasSimilitudes]
  )

  // Equipo real (con el aprendiz y su usuario incluidos) y compañeros
  // candidatos. Solo se puede agregar si el usuario pertenece a la ficha de la
  // propuesta; si salió de la ficha, el equipo se ve en solo lectura.
  const equipo = equipoApi || []
  const equipoIds = useMemo(
    () => new Set((equipoApi || []).map((e) => Number(e.id_aprendiz))),
    [equipoApi]
  )
  const miAprendiz = useMemo(
    () => (rosterApi || []).find((a) => Number(a.id_usuario) === Number(user.id)) || null,
    [rosterApi, user.id]
  )
  // Regla Classroom: la ficha debe estar activa y el usuario pertenecer a ella.
  const fichaActiva = project?.classGroup?.estado === 'activo'
  const perteneceAFicha = !!miAprendiz
    && Number(miAprendiz.id_class_group) === Number(project?.id_class_group)
  // El equipo lo gestiona el CREADOR, dentro de su ficha activa y solo
  // mientras la propuesta es editable (borrador/en revisión/rechazada).
  const equipoEditable = ['borrador', 'pendiente', 'rechazado'].includes(project?.estado)
  const gestionaEquipo = equipoEditable
    && Number(project?.id_creador) === Number(user.id)
    && fichaActiva && perteneceAFicha
  const disponibles = useMemo(
    () => (gestionaEquipo ? (rosterApi || []).filter((a) => !equipoIds.has(Number(a.id))) : []),
    [gestionaEquipo, rosterApi, equipoIds]
  )

  if (cargando) {
    return (
      <DashboardLayout role="aprendiz" titulo="Detalle del Proyecto">
        <div className={s.page}><ApiState cargando /></div>
      </DashboardLayout>
    )
  }

  if (error) {
    return (
      <DashboardLayout role="aprendiz" titulo="Detalle del Proyecto">
        <div className={s.page}><ApiState error={error} onReintentar={recargar} /></div>
      </DashboardLayout>
    )
  }

  if (!project) {
    return (
      <DashboardLayout role="aprendiz" titulo="Detalle del Proyecto">
        <div className={s.page}>
          <EmptyState
            icon={<MagnifyingGlass />}
            title="Proyecto no encontrado"
            message="El proyecto que buscas no existe o fue eliminado."
            actionLabel="Volver a mis propuestas"
            onAction={() => navigate('/aprendiz/propuestas')}
          />
        </div>
      </DashboardLayout>
    )
  }

  // Creador o integrante del equipo → la propuesta es suya (historial visible).
  const esPropio = esMia(project, user.id)
  // Solo el creador puede eliminar la propuesta (el equipo no la borra).
  const esCreador = Number(project.id_creador) === Number(user.id)
  const ficha = project.classGroup || null
  // Participar (comentar/editar/equipo) exige estar DENTRO de la ficha activa.
  const puedeEscribir = esPropio && fichaActiva && perteneceAFicha
  const soloLectura = esPropio && !puedeEscribir
  const motivoSoloLectura = ficha && ficha.estado !== 'activo'
    ? 'La ficha está finalizada: la propuesta queda en solo lectura.'
    : 'Solo lectura: ya no perteneces a esta ficha.'
  // Se puede editar en borrador, en revisión o rechazada; aprobada queda
  // bloqueada. El envío explícito sigue disponible solo en borrador/rechazada.
  const puedeEditar = puedeEscribir && ['borrador', 'pendiente', 'rechazado'].includes(project.estado)
  const puedeEnviar = puedeEscribir && ['borrador', 'rechazado'].includes(project.estado)

  // Envío explícito: el backend valida, detecta similitudes y avisa al instructor.
  async function enviarPropuesta() {
    setAviso('')
    setAvisoError('')
    setEnviandoPropuesta(true)
    try {
      await proyectos.enviar(project.id)
      navigate('/aprendiz/analizando-proyecto', { state: { projectId: project.id }, replace: true })
    } catch (err) {
      setAvisoError(err?.data?.message || 'No se pudo enviar la propuesta.')
      setEnviandoPropuesta(false)
    }
  }

  function iniciarEdicion() {
    setForm({
      titulo: project.titulo || '',
      resumen: project.resumen || '',
      palabras_clave: project.palabras_clave || '',
      objetivo_general: project.objetivo_general || '',
      objetivos_especificos: (Array.isArray(project.objetivos_especificos) ? project.objetivos_especificos : []).join('\n'),
      area_aplicacion: project.area_aplicacion || '',
    })
    setErrores({})
    setAviso('')
    setAvisoError('')
    setEditando(true)
  }

  function validar() {
    const errs = {}
    if (form.titulo.trim().length < 5) errs.titulo = 'El título debe tener al menos 5 caracteres.'
    if (form.resumen.trim().length < 20) errs.resumen = 'La descripción debe tener al menos 20 caracteres.'
    if (form.objetivo_general.trim().length < 15) errs.objetivo_general = 'El objetivo general debe tener al menos 15 caracteres.'
    const objetivos = form.objetivos_especificos.split('\n').map((l) => l.trim()).filter(Boolean)
    if (objetivos.length < 2) errs.objetivos_especificos = 'Escribe al menos 2 objetivos específicos (uno por línea).'
    else if (objetivos.some((o) => o.length < 8)) errs.objetivos_especificos = 'Cada objetivo específico debe tener al menos 8 caracteres.'
    if (!form.area_aplicacion.trim()) errs.area_aplicacion = 'Indica el área de aplicación.'
    return errs
  }

  async function guardarEdicion(e) {
    e.preventDefault()
    const errs = validar()
    setErrores(errs)
    if (Object.keys(errs).length > 0) return

    setGuardando(true)
    try {
      const objetivosValidos = form.objetivos_especificos.split('\n').map((l) => l.trim()).filter(Boolean)
      await proyectos.actualizar(project.id, {
        titulo: form.titulo.trim(),
        resumen: form.resumen.trim(),
        palabras_clave: form.palabras_clave.trim() || null,
        area_aplicacion: form.area_aplicacion.trim(),
        objetivo_general: form.objetivo_general.trim() || null,
        objetivos_especificos: objetivosValidos,
        id_creador: project.id_creador,
        id_instructor_asignado: project.id_instructor_asignado,
        id_class_group: project.id_class_group,
      })
      await recargar()
      await recargarSimilitudes()
      setEditando(false)
      setAviso(project.estado === 'rechazado'
        ? 'Propuesta actualizada. Revísala y envíala de nuevo cuando estés listo.'
        : project.estado === 'pendiente'
          ? 'Propuesta actualizada. Sigue en revisión; el instructor verá los cambios.'
          : 'Propuesta actualizada correctamente.')
    } catch (err) {
      // Errores 422 por campo (mismas claves que el formulario). Si el 422 solo
      // trae claves sin campo visible, se conserva el mensaje general bajo el
      // título; si trae alguna clave visible, se comporta como antes.
      const campos = toFieldErrors(err?.data)
      const hayCampoVisible = CAMPOS_CON_ERROR.some((k) => campos[k])
      setErrores(
        hayCampoVisible
          ? campos
          : { titulo: err?.data?.message || 'No se pudo actualizar la propuesta.' }
      )
    } finally {
      setGuardando(false)
    }
  }

  async function agregarObservacion(e) {
    e.preventDefault()
    if (enviando) return
    const t = texto.trim()
    if (!t) return
    setEnviando(true)
    setObsError('')
    try {
      await observacionesApi.crear({
        texto: t,
        id_proyecto: Number(project.id),
        id_usuario: Number(user.id),
        respuesta_a: respondiendoA?.id || null,
      })
      await recargarObs()
      setTexto('')
      setRespondiendoA(null)
    } catch (err) {
      // Se conserva el texto para que el usuario pueda reintentar.
      setObsError(err?.data?.message || 'No se pudo publicar la observación.')
    } finally {
      setEnviando(false)
    }
  }

  async function eliminarPropuesta() {
    setEliminando(true)
    setEliminarError('')
    try {
      await proyectos.eliminar(project.id)
      navigate('/aprendiz/propuestas', { replace: true })
    } catch (err) {
      setEliminarError(err?.data?.message || 'No se pudo eliminar la propuesta.')
      setModalEliminar(false)
    } finally {
      setEliminando(false)
    }
  }

  async function agregarMiembro(idAprendiz) {
    if (equipoOcupado) return
    setEquipoOcupado(true)
    setEquipoError('')
    try {
      await proyectos.agregarAlEquipo(Number(idAprendiz), Number(project.id))
    } catch (err) {
      setEquipoError(err?.data?.message || 'No se pudo agregar al integrante.')
    } finally {
      await recargarEquipo()
      setEquipoOcupado(false)
    }
  }

  async function quitarMiembro(pivotId) {
    if (equipoOcupado) return
    setEquipoOcupado(true)
    setEquipoError('')
    try {
      await proyectos.quitarDelEquipo(pivotId)
    } catch (err) {
      // 404 = la fila ya no existía (recarga/otra pestaña): no es un error real.
      if (err?.status !== 404) {
        setEquipoError(err?.data?.message || 'No se pudo quitar al integrante.')
      }
    } finally {
      await recargarEquipo()
      setEquipoOcupado(false)
    }
  }

  return (
    <DashboardLayout role="aprendiz" titulo="Detalle del Proyecto">
      <div className={s.page}>
        <PageHeader
          title={project.titulo}
          subtitle={`Enviado el ${fechaDesdeApi(project.created_at)} por ${nombreUsuario(project.creator)} · Ficha ${project.classGroup?.nombre || '—'}${project.classGroup?.program?.nombre ? ` (${project.classGroup.program.nombre})` : ''}`}
          icon={<FolderOpen />}
          breadcrumb={[
            { label: 'Dashboard', to: '/aprendiz/dashboard' },
            { label: 'Mis Propuestas', to: '/aprendiz/propuestas' },
            { label: project.titulo },
          ]}
          actions={
            !editando ? (
              <>
                {puedeEditar && (
                  <Button type="button" variant="secondary" onClick={iniciarEdicion}>
                    <PencilSimple size={14} /> Editar
                  </Button>
                )}
                {puedeEnviar && (
                  <Button type="button" disabled={enviandoPropuesta} onClick={enviarPropuesta}>
                    <PaperPlaneRight size={14} /> {enviandoPropuesta ? 'Enviando…' : 'Enviar propuesta'}
                  </Button>
                )}
                {esCreador && puedeEscribir && (
                  <Button type="button" variant="dangerGhost" onClick={() => setModalEliminar(true)}>
                    <Trash size={14} /> Eliminar
                  </Button>
                )}
              </>
            ) : undefined
          }
        />

        {aviso && (
          <p className={s.muted} role="status"><CheckCircle size={14} /> {aviso}</p>
        )}

        {avisoError && <Alert variant="danger">{avisoError}</Alert>}

        {eliminarError && <Alert variant="danger">{eliminarError}</Alert>}

        {soloLectura && <Alert variant="info">{motivoSoloLectura}</Alert>}

        <div className={esPropio ? s.dossier : s.dossierSolo}>
          <div className={s.colPrincipal}>
            <DataPanel title="Información del proyecto" icon={<FileText />}>
              {editando ? (
                <form className={n.form} onSubmit={guardarEdicion} noValidate>
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
                      maxLength={MAX_DESCRIPCION}
                    />
                  </FormField>
                  <FormField label="Objetivo general" required error={errores.objetivo_general}>
                    <Textarea
                      rows={3}
                      value={form.objetivo_general}
                      onChange={(e) => setForm((f) => ({ ...f, objetivo_general: e.target.value }))}
                      maxLength={MAX_DESCRIPCION_CORTA}
                    />
                  </FormField>
                  <FormField label="Objetivos específicos" required error={errores.objetivos_especificos} help="Uno por línea.">
                    <Textarea
                      rows={4}
                      value={form.objetivos_especificos}
                      onChange={(e) => setForm((f) => ({ ...f, objetivos_especificos: e.target.value }))}
                    />
                  </FormField>
                  <div className={n.grid2}>
                    <FormField label="Palabras clave" help="Opcional, separadas por comas.">
                      <Input
                        value={form.palabras_clave}
                        onChange={(e) => setForm((f) => ({ ...f, palabras_clave: e.target.value }))}
                        maxLength={200}
                      />
                    </FormField>
                    <FormField label="Área de aplicación" required error={errores.area_aplicacion}>
                      <Input
                        value={form.area_aplicacion}
                        onChange={(e) => setForm((f) => ({ ...f, area_aplicacion: e.target.value }))}
                        maxLength={120}
                      />
                    </FormField>
                  </div>
                  {project.estado === 'rechazado' && (
                    <p className={s.muted}><Warning size={14} /> Al guardar, la propuesta sigue rechazada. Cuando termines, usa «Enviar propuesta» para reenviarla a revisión (el sistema exige que el contenido haya cambiado).</p>
                  )}
                  {project.estado === 'pendiente' && (
                    <p className={s.muted}><Warning size={14} /> Al guardar, la propuesta sigue en revisión y sus coincidencias se vuelven a analizar.</p>
                  )}
                  <Actions form>
                    <Button type="submit" disabled={guardando}>
                      <CheckCircle size={14} /> {guardando ? 'Guardando…' : 'Guardar cambios'}
                    </Button>
                    <Button type="button" variant="secondary" onClick={() => setEditando(false)}>
                      Cancelar
                    </Button>
                  </Actions>
                </form>
              ) : (
                <InformacionProyecto proyecto={project} ficha={ficha} fichaHref={`/aprendiz/detalle-ficha/${project.id_class_group}`} />
              )}
            </DataPanel>
          </div>

          {esPropio && (
            <aside className={s.rail} aria-label="Similitudes y observaciones">
              <DataPanel title="Similitudes detectadas" icon={<MagnifyingGlass />}>
                {similitudes.length === 0 ? (
                  <p className={s.muted}>No se han detectado similitudes para esta propuesta.</p>
                ) : (
                  <ul className={s.simList}>
                    {similitudes.map((sim) => {
                      const pct = Math.round(Number(sim.porcentaje) || 0)
                      const otroTitulo = Number(sim.id_proyecto_1) === Number(project.id)
                        ? sim.project2?.titulo
                        : sim.project1?.titulo
                      return (
                        <li key={sim.id}>
                          <Link to={`/aprendiz/detalle-similitud/${sim.id}`} viewTransition className={s.simRow}>
                            <span className={s.simPair}>vs. {otroTitulo || 'Proyecto no disponible'}</span>
                            <span className={s.simRight}>
                              <GradeBadge score={pct} size="sm" />
                            </span>
                          </Link>
                        </li>
                      )
                    })}
                  </ul>
                )}
                <SimilitudesHistoricas key={project.updated_at} proyectoId={project.id} detalleBase="/aprendiz" />
              </DataPanel>

              <DataPanel title={`Observaciones (${observaciones.length})`} icon={<ChatCircle />}>
                {respondiendoA && (
                  <div className={s.respondiendoChip}>
                    Respondiendo a {String(respondiendoA.autor).split(' | ')[0]}
                    <button type="button" onClick={() => setRespondiendoA(null)} aria-label="Cancelar respuesta"><X size={12} /></button>
                  </div>
                )}
                {cargandoObs ? (
                  <ApiState cargando />
                ) : (
                  <ObservacionHilo
                    grupos={agruparObservaciones(observaciones)}
                    permitirResponder={puedeEscribir}
                    onRespuesta={(o) => setRespondiendoA(o)}
                    className={s.hilosScroll}
                  />
                )}

                {obsError && <Alert variant="danger">{obsError}</Alert>}

                {puedeEscribir ? (
                  <form className={s.obsForm} onSubmit={agregarObservacion}>
                    <Textarea
                      rows={3}
                      value={texto}
                      onChange={(e) => setTexto(e.target.value)}
                      aria-label="Escribe un comentario sobre la propuesta"
                      placeholder={respondiendoA ? 'Escribe tu respuesta al instructor…' : 'Escribe tu comentario sobre la propuesta...'}
                    />
                    <Button type="submit" disabled={!texto.trim() || enviando}>
                      <Plus size={14} /> Agregar observación
                    </Button>
                  </form>
                ) : (
                  <p className={s.muted}>Solo lectura: no puedes agregar observaciones.</p>
                )}
              </DataPanel>

              <HistorialProyecto projectId={project.id} />

              <DataPanel title="Equipo" icon={<UsersThree />}>
                {equipoError && <Alert variant="danger">{equipoError}</Alert>}

                <ul className={s.simList}>
                  {equipo.map((e) => {
                    const esCreadorFila = Number(e.apprentice?.id_usuario) === Number(project.id_creador)
                    return (
                      <li key={e.id} className={s.simRow}>
                        <span className={s.simPair}>
                          {nombreUsuario(e.apprentice?.generalUser)}{esCreadorFila ? ' (creador)' : ''}
                        </span>
                        {gestionaEquipo && !esCreadorFila && (
                          <Button
                            type="button"
                            size="sm"
                            variant="ghost"
                            disabled={equipoOcupado}
                            onClick={() => quitarMiembro(e.id)}
                          >
                            Quitar
                          </Button>
                        )}
                      </li>
                    )
                  })}
                </ul>

                {gestionaEquipo && disponibles.length > 0 && (
                  <>
                    <p className={s.muted}>Agregar compañero de la ficha:</p>
                    <ul className={s.simList}>
                      {disponibles.map((a) => (
                        <li key={a.id} className={s.simRow}>
                          <span className={s.simPair}>{nombreUsuario(a.generalUser)}</span>
                          <Button
                            type="button"
                            size="sm"
                            variant="secondary"
                            disabled={equipoOcupado}
                            onClick={() => agregarMiembro(a.id)}
                          >
                            <Plus size={12} /> Agregar
                          </Button>
                        </li>
                      ))}
                    </ul>
                  </>
                )}
              </DataPanel>
            </aside>
          )}
        </div>
      </div>

      <ConfirmModal
        open={modalEliminar}
        titulo="Eliminar propuesta"
        mensaje={`¿Eliminar "${project.titulo}"? Solo se elimina si está en borrador o rechazada y no tiene similitudes compartidas; las observaciones se borrarán. Esta acción no se puede deshacer.`}
        textoConfirmar={eliminando ? 'Eliminando…' : 'Sí, eliminar'}
        onCancelar={() => setModalEliminar(false)}
        onConfirmar={eliminarPropuesta}
      />
    </DashboardLayout>
  )
}

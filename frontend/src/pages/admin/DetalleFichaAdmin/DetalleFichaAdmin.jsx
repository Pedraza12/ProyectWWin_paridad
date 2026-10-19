import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import PageHeader from '../../../components/PageHeader/PageHeader'
import DataPanel from '../../../components/DataPanel/DataPanel'
import FormField from '../../../components/FormField/FormField'
import Badge from '../../../components/Badge/Badge'
import Alert from '../../../components/Alert/Alert'
import Button from '../../../components/Button/Button'
import { Input, Select } from '../../../components/Input/Input'
import Actions from '../../../components/Actions/Actions'
import Avatar from '../../../components/Avatar/Avatar'
import EmptyState from '../../../components/EmptyState/EmptyState'
import ApiState from '../../../components/ApiState/ApiState'
import ConfirmModal from '../../../components/ConfirmModal/ConfirmModal'
import InformacionFicha from '../../../components/DetalleFichaBase/InformacionFicha'
import { useApi } from '../../../lib/useApi'
import { fichas, programas, redes, instructores, proyectos, usuarios } from '../../../lib/recursos'
import { toFieldErrors } from '../../../lib/api'
import { MAX_NOMBRE, MAX_NUMERO_FICHA, esNumeroFichaValido } from '../../../utils/validation'
import { PROJECT_ESTADO_VARIANT, PROJECT_ESTADO_LABEL as PROYECTO_ESTADO_LABEL, FICHA_ESTADO_LABEL as ESTADO_LABEL } from '../../../constants/badgeVariants'
import { fechaDesdeApi, nombreCompleto } from '../../../utils/helpers'
import s from '../../../components/DetalleFichaBase/DetalleFichaBase.module.css'
import { Books, ChartBar, CheckCircle, FolderOpen, GraduationCap, IdentificationCard, MagnifyingGlass, PencilLine, Plus, Trash, Warning } from 'phosphor-react'

const ESTADOS_FICHA = ['activo', 'finalizado']


function formDesde(ficha) {
  if (!ficha) return null
  return {
    nombre: ficha.nombre || '',
    numero: ficha.numero || '',
    estado: ficha.estado || 'activo',
    red: ficha.program?.knowledge_network_id ? String(ficha.program.knowledge_network_id) : '',
    programa: ficha.id_programa ? String(ficha.id_programa) : '',
    instructorId: ficha.id_instructor ? String(ficha.id_instructor) : '',
  }
}

export default function DetalleFichaAdmin() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [editando, setEditando] = useState(false)
  const [form, setForm] = useState(null)
  const [errores, setErrores] = useState({})
  const [guardado, setGuardado] = useState(false)
  const [enviandoEdicion, setEnviandoEdicion] = useState(false)
  const [accionMsg, setAccionMsg] = useState(null)
  const [avisoMsg, setAvisoMsg] = useState('')
  const [modalEliminar, setModalEliminar] = useState(false)
  const [busquedaAprendiz, setBusquedaAprendiz] = useState('')
  const [filtroPropFicha, setFiltroPropFicha] = useState('todos')
  // Alta/asociación de aprendices desde la ficha.
  const [mostrarAlta, setMostrarAlta] = useState(false)
  const [nuevoAprendiz, setNuevoAprendiz] = useState({
    nombre: '', apellido: '', tipoDocumento: 'CC', numeroDocumento: '', correo: '',
  })
  const [altaError, setAltaError] = useState('')
  const [altaMsg, setAltaMsg] = useState('')
  const [altaCredenciales, setAltaCredenciales] = useState(null)
  const [ocupadoAlta, setOcupadoAlta] = useState(false)

  // Fuente única: la API. Ficha con relaciones + catálogos + propuestas + usuarios.
  const { data, cargando, error, recargar } = useApi(
    async () => {
      const [ficha, listaProgramas, listaRedes, listaInstructores, listaProyectos, listaUsuarios] = await Promise.all([
        fichas.obtener(id),
        programas.listar(),
        redes.listar(),
        instructores.listar('generalUser'),
        proyectos.listar(),
        usuarios.listar({ role: 'aprendiz' }),
      ])
      return { ficha, listaProgramas, listaRedes, listaInstructores, listaProyectos, listaUsuarios }
    },
    [id],
    { inicial: null }
  )

  const ficha = data?.ficha || null

  // Sincroniza el formulario al navegar entre fichas o recargar.
  useEffect(() => {
    if (ficha) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setForm(formDesde(ficha))
      setEditando(false)
      setGuardado(false)
    }
  }, [ficha?.id]) // eslint-disable-line react-hooks/exhaustive-deps

  if (cargando || error || !ficha) {
    return (
      <DashboardLayout role="admin" titulo="Detalle de Ficha">
        <div className={s.page}>
          {error ? (
            <EmptyState
              icon={<MagnifyingGlass />}
              title="No se pudo cargar la ficha"
              message={error.message || 'Ocurrió un error al consultar la API.'}
              actionLabel="Reintentar"
              onAction={recargar}
            />
          ) : cargando ? (
            <ApiState cargando error={null} />
          ) : (
            <EmptyState
              icon={<MagnifyingGlass />}
              title="Ficha no encontrada"
              message="La ficha que buscas no existe o fue eliminada."
              actionLabel="Volver a fichas"
              onAction={() => navigate('/admin/fichas')}
            />
          )}
        </div>
      </DashboardLayout>
    )
  }

  const listaProgramas = data.listaProgramas || []
  const listaRedes = data.listaRedes || []
  const listaInstructores = data.listaInstructores || []

  const estudiantes = ficha.apprentices || []
  const proyectosDeLaFicha = (data.listaProyectos || []).filter((p) => Number(p.id_class_group) === Number(ficha.id))

  const estudiantesFiltrados = estudiantes.filter((est) => {
    const q = busquedaAprendiz.trim().toLowerCase()
    if (!q) return true
    const g = est.generalUser || {}
    return nombreCompleto(g).toLowerCase().includes(q)
      || (g.correo || '').toLowerCase().includes(q)
      || (g.username || '').toLowerCase().includes(q)
      || String(g.numero_documento || '').includes(q)
  })

  // Usuarios con rol aprendiz que aún no están en la ficha (se pueden asociar).
  const idsEnFicha = new Set(estudiantes.map((e) => Number(e.generalUser?.id)))
  const disponibles = (data.listaUsuarios || []).filter((u) => !idsEnFicha.has(Number(u.id)))
  const qDisponibles = busquedaAprendiz.trim().toLowerCase()
  const disponiblesFiltrados = qDisponibles
    ? disponibles.filter((u) => {
        const texto = `${u.nombre || ''} ${u.apellido || ''} ${u.username || ''} ${u.numero_documento || ''} ${u.correo || ''}`.toLowerCase()
        return texto.includes(qDisponibles)
      })
    : []

  // Asocia un usuario existente (sin credenciales nuevas).
  const asociarExistente = async (usuario) => {
    setAltaError('')
    setAltaMsg('')
    setOcupadoAlta(true)
    try {
      await fichas.agregarAprendiz(ficha.id, { usuario_id: usuario.id })
      setAltaMsg(`${nombreCompleto(usuario)} se asoció a la ficha (sin nuevas credenciales).`)
      setBusquedaAprendiz('')
      setMostrarAlta(false)
      await recargar()
    } catch (err) {
      setAltaError(err?.data?.message || 'No se pudo asociar el aprendiz.')
    } finally {
      setOcupadoAlta(false)
    }
  }

  // Crea la cuenta del aprendiz, la asocia a la ficha y envía credenciales.
  const crearAprendiz = async (e) => {
    e.preventDefault()
    setAltaError('')
    setAltaMsg('')
    setAltaCredenciales(null)
    if (!nuevoAprendiz.nombre.trim() || !nuevoAprendiz.apellido.trim()
      || !nuevoAprendiz.numeroDocumento.trim() || !nuevoAprendiz.correo.trim()) {
      setAltaError('Completa nombre, apellido, documento y correo.')
      return
    }

    setOcupadoAlta(true)
    try {
      const resp = await fichas.agregarAprendiz(ficha.id, {
        nombre: nuevoAprendiz.nombre.trim(),
        apellido: nuevoAprendiz.apellido.trim(),
        tipo_documento: nuevoAprendiz.tipoDocumento,
        numero_documento: nuevoAprendiz.numeroDocumento.trim(),
        correo: nuevoAprendiz.correo.trim().toLowerCase(),
      })
      setAltaCredenciales({
        nombre: `${resp.usuario.nombre} ${resp.usuario.apellido}`.trim(),
        username: resp.credenciales.username,
        temporal: resp.credenciales.password_temporal,
        enviadas: resp.credenciales.enviadas,
        correo: resp.usuario.correo,
      })
      setNuevoAprendiz({ nombre: '', apellido: '', tipoDocumento: 'CC', numeroDocumento: '', correo: '' })
      setMostrarAlta(false)
      setBusquedaAprendiz('')
      await recargar()
    } catch (err) {
      const campos = toFieldErrors(err?.data)
      setAltaError(
        campos.numero_documento || campos.correo || campos.nombre || campos.apellido
        || err?.data?.message || 'No se pudo crear el aprendiz.'
      )
    } finally {
      setOcupadoAlta(false)
    }
  }
  const proyectosFiltradosFicha = proyectosDeLaFicha.filter(
    (p) => filtroPropFicha === 'todos' || p.estado === filtroPropFicha
  )
  const instructoresActivos = listaInstructores.filter((i) => i.generalUser?.estado !== false)

  const onChange = (e) => {
    const { name, value } = e.target
    setForm((f) => ({ ...f, [name]: value }))
    setErrores((err) => ({ ...err, [name]: undefined }))
    setGuardado(false)
  }

  function alCambiarRed(e) {
    const { value } = e.target
    setForm((f) => ({ ...f, red: value, programa: '' }))
    setErrores((err) => ({ ...err, red: undefined, programa: undefined }))
    setGuardado(false)
  }

  const programasDeRed = form?.red
    ? listaProgramas.filter((p) => Number(p.knowledge_network_id) === Number(form.red))
    : listaProgramas

  const validar = () => {
    const err = {}
    if (!form.nombre.trim()) err.nombre = 'El nombre es obligatorio.'
    if (!form.numero.trim()) err.numero = 'El número de ficha es obligatorio.'
    else if (!esNumeroFichaValido(form.numero)) err.numero = 'Solo dígitos (4 a 8 caracteres).'
    if (!form.programa) err.programa = 'Selecciona el programa de formación.'
    if (!form.instructorId) err.instructorId = 'Selecciona un instructor a cargo.'
    return err
  }

  const guardarEdicion = async (e) => {
    e.preventDefault()
    if (enviandoEdicion) return
    const err = validar()
    if (Object.keys(err).length) {
      setErrores(err)
      return
    }
    setEnviandoEdicion(true)
    try {
      await fichas.actualizar(ficha.id, {
        codigo: ficha.codigo,
        nombre: form.nombre.trim(),
        numero: form.numero.trim(),
        estado: form.estado,
        id_programa: Number(form.programa),
        id_instructor: Number(form.instructorId),
      })
      await recargar()
      setEditando(false)
      setGuardado(true)
    } catch (error2) {
      const campos = toFieldErrors(error2?.data)
      if (campos.id_programa) {
        setErrores({ programa: 'El programa no existe en el servidor.' })
        return
      }
      setErrores({ nombre: error2?.data?.message || 'No se pudo guardar la ficha en el servidor.' })
    } finally {
      setEnviandoEdicion(false)
    }
  }

  const cancelarEdicion = () => {
    setEditando(false)
    setErrores({})
    setForm(formDesde(ficha))
  }

  const confirmarEliminar = async () => {
    try {
      const resp = await fichas.eliminar(ficha.id)
      // Con historial la ficha se anula (el número queda libre); sin historial
      // se elimina de verdad.
      if (resp?.accion === 'anulada') {
        setModalEliminar(false)
        setAvisoMsg('La ficha tenía aprendices o propuestas: quedó anulada y su número volvió a estar disponible.')
        await recargar()
        return
      }
      navigate('/admin/fichas')
    } catch (err) {
      setModalEliminar(false)
      setAccionMsg(err?.data?.message || 'No se pudo eliminar la ficha.')
    }
  }


  return (
    <DashboardLayout role="admin" titulo="Detalle de Ficha">
      <div className={s.page}>
        <PageHeader
          title={ficha.nombre}
          subtitle={`Código ${ficha.codigo} · N° ${ficha.numero} · ${ficha.program?.nombre || 'Sin programa'}`}
          icon={<Books />}
          breadcrumb={[
            { label: 'Dashboard', to: '/admin/dashboard', icon: <ChartBar size={14} /> },
            { label: 'Fichas', to: '/admin/fichas', icon: <Books size={14} /> },
            { label: ficha.nombre },
          ]}
        />

        {guardado && (
          <Alert><CheckCircle size={14} /> Ficha actualizada correctamente.</Alert>
        )}

        {accionMsg && (
          <Alert variant="danger"><Warning size={14} /> {accionMsg}</Alert>
        )}

        {avisoMsg && (
          <Alert><CheckCircle size={14} /> {avisoMsg}</Alert>
        )}

        <DataPanel
          title="Información de la ficha"
          icon={<IdentificationCard />}
          action={
            <div className={s.headActions}>
              <Button
                type="button"
                variant="secondary"
                onClick={() => (editando ? cancelarEdicion() : setEditando(true))}
              >
                <PencilLine size={14} /> {editando ? 'Cancelar edición' : 'Editar'}
              </Button>
              <Button
                type="button"
                variant="danger"
                onClick={() => setModalEliminar(true)}
              >
                <Trash size={14} /> Eliminar ficha
              </Button>
            </div>
          }
        >
          {!editando || !form ? (
            <InformacionFicha
              ficha={ficha}
              estudiantesCount={estudiantes.length}
              proyectosCount={proyectosDeLaFicha.length}
              instructorHref={ficha.instructor?.generalUser?.id ? `/admin/detalle-usuario/${ficha.instructor.generalUser.id}` : null}
            />
          ) : (
            <form className={s.form} onSubmit={guardarEdicion} noValidate>
              <FormField label="Nombre de la ficha" required error={errores.nombre}>
                <Input
                  name="nombre"
                  value={form.nombre}
                  onChange={onChange}
                  maxLength={MAX_NOMBRE}
                />
              </FormField>
              <FormField label="Número de ficha" required error={errores.numero} help="Solo dígitos, sin espacios. Ej. 3142101">
                <Input
                  name="numero"
                  inputMode="numeric"
                  value={form.numero}
                  onChange={onChange}
                  maxLength={MAX_NUMERO_FICHA}
                />
              </FormField>
              <FormField label="Red de conocimiento" help="Solo para cambiar el programa.">
                <Select name="red" value={form.red} onChange={alCambiarRed}>
                  <option value="">Mantener programa actual…</option>
                  {listaRedes.map((r) => (
                    <option key={r.id} value={String(r.id)}>
                      {r.nombre}
                    </option>
                  ))}
                </Select>
              </FormField>
              <FormField label="Programa de formación" required error={errores.programa}>
                <Select name="programa" value={form.programa} onChange={onChange}>
                  <option value="">Selecciona un programa…</option>
                  {programasDeRed.map((p) => (
                    <option key={p.id} value={String(p.id)}>
                      {p.nombre}
                    </option>
                  ))}
                </Select>
              </FormField>
              <FormField label="Instructor a cargo" required error={errores.instructorId}>
                <Select name="instructorId" value={form.instructorId} onChange={onChange}>
                  <option value="">Selecciona un instructor…</option>
                  {instructoresActivos.map((i) => (
                    <option key={i.id} value={String(i.id)}>
                      {nombreCompleto(i.generalUser)}
                    </option>
                  ))}
                </Select>
              </FormField>
              <FormField label="Estado" help="Archivar cierra la ficha: conserva el historial y bloquea nuevas uniones.">
                <Select name="estado" value={form.estado} onChange={onChange}>
                  {ESTADOS_FICHA.map((est) => (
                    <option key={est} value={est}>
                      {ESTADO_LABEL[est] || est}
                    </option>
                  ))}
                </Select>
              </FormField>
              <Actions form>
                <Button type="submit" disabled={enviandoEdicion}>
                  <CheckCircle size={14} /> Guardar cambios
                </Button>
                <Button type="button" variant="secondary" onClick={cancelarEdicion}>
                  Cancelar
                </Button>
              </Actions>
            </form>
          )}
        </DataPanel>

        <DataPanel title={`Aprendices (${estudiantes.length})`} icon={<GraduationCap />}>
          <FormField
            label="Buscar o agregar aprendiz"
            help="Busca por nombre, usuario, documento o correo; si no existe, créalo aquí."
          >
            <Input
              value={busquedaAprendiz}
              onChange={(e) => setBusquedaAprendiz(e.target.value)}
              placeholder="Nombre, usuario, documento o correo…"
            />
          </FormField>

          {altaMsg && <Alert variant="success"><CheckCircle size={14} /> {altaMsg}</Alert>}
          {altaError && <Alert variant="danger">{altaError}</Alert>}
          {altaCredenciales && (
            <Alert variant={altaCredenciales.enviadas ? 'success' : 'warning'}>
              <CheckCircle size={14} /> Aprendiz <strong>{altaCredenciales.nombre}</strong> creado.
              {altaCredenciales.enviadas
                ? <> Credenciales enviadas a <strong>{altaCredenciales.correo}</strong>.</>
                : <> No se pudo enviar el correo; reenvíalas desde Usuarios.</>}
              <div className={s.altaCred}>
                Usuario: <code>{altaCredenciales.username}</code> · Contraseña temporal: <code>{altaCredenciales.temporal}</code>
              </div>
            </Alert>
          )}

          {qDisponibles !== '' && (
            disponiblesFiltrados.length > 0 ? (
              <ul className={s.studentList}>
                {disponiblesFiltrados.slice(0, 8).map((u) => (
                  <li key={u.id}>
                    <div className={s.studentRow}>
                      <Avatar name={nombreCompleto(u)} src={u.foto_url} size="md" />
                      <span className={s.studentInfo}>
                        <span className={s.studentName}>{nombreCompleto(u)}</span>
                        <span className={s.studentEmail}>{u.username} · {u.correo}</span>
                      </span>
                      <Button
                        type="button"
                        size="sm"
                        variant="secondary"
                        disabled={ocupadoAlta}
                        onClick={() => asociarExistente(u)}
                      >
                        <Plus size={12} /> Agregar
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <Button type="button" variant="secondary" size="sm" onClick={() => setMostrarAlta(true)}>
                <Plus size={12} /> Crear aprendiz «{busquedaAprendiz.trim()}»
              </Button>
            )
          )}

          {mostrarAlta && (
            <form className={s.altaForm} onSubmit={crearAprendiz} noValidate>
              <div className={s.altaGrid}>
                <FormField label="Nombres" required>
                  <Input
                    value={nuevoAprendiz.nombre}
                    onChange={(e) => setNuevoAprendiz((f) => ({ ...f, nombre: e.target.value }))}
                    placeholder="Ej. María José"
                  />
                </FormField>
                <FormField label="Apellidos" required>
                  <Input
                    value={nuevoAprendiz.apellido}
                    onChange={(e) => setNuevoAprendiz((f) => ({ ...f, apellido: e.target.value }))}
                    placeholder="Ej. González Ruiz"
                  />
                </FormField>
              </div>
              <div className={s.altaGrid}>
                <FormField label="Tipo de documento" required>
                  <Select
                    value={nuevoAprendiz.tipoDocumento}
                    onChange={(e) => setNuevoAprendiz((f) => ({ ...f, tipoDocumento: e.target.value }))}
                  >
                    {['CC', 'TI', 'CE', 'PPT'].map((t) => (
                      <option key={t} value={t}>{t}</option>
                    ))}
                  </Select>
                </FormField>
                <FormField label="Número de documento" required>
                  <Input
                    inputMode="numeric"
                    value={nuevoAprendiz.numeroDocumento}
                    onChange={(e) => setNuevoAprendiz((f) => ({ ...f, numeroDocumento: e.target.value }))}
                    placeholder="Ej. 1234567890"
                  />
                </FormField>
              </div>
              <FormField label="Correo personal" required help="Allí se envían las credenciales.">
                <Input
                  type="email"
                  value={nuevoAprendiz.correo}
                  onChange={(e) => setNuevoAprendiz((f) => ({ ...f, correo: e.target.value }))}
                  placeholder="Correo personal"
                />
              </FormField>
              <Actions>
                <Button type="submit" disabled={ocupadoAlta}>
                  <CheckCircle size={14} /> {ocupadoAlta ? 'Creando…' : 'Crear y asociar'}
                </Button>
                <Button type="button" variant="secondary" onClick={() => setMostrarAlta(false)}>
                  Cancelar
                </Button>
              </Actions>
            </form>
          )}

          {estudiantes.length === 0 && qDisponibles === '' && (
            <p className={s.muted}>Aún no hay aprendices en esta ficha.</p>
          )}
          {estudiantesFiltrados.length > 0 && (
            <ul className={s.studentList}>
              {estudiantesFiltrados.map((est) => {
                const perfil = est.generalUser || {}
                return (
                  <li key={est.id}>
                    <Link to={`/admin/detalle-usuario/${perfil.id}`} viewTransition className={s.studentRow}>
                      <Avatar name={nombreCompleto(perfil)} src={perfil.foto_url} size="md" />
                      <span className={s.studentInfo}>
                        <span className={s.studentName}>{nombreCompleto(perfil)}</span>
                        <span className={s.studentEmail}>{perfil.correo}</span>
                      </span>
                      <span className={s.arrow} aria-hidden="true">→</span>
                    </Link>
                  </li>
                )
              })}
            </ul>
          )}
        </DataPanel>

        <DataPanel title={`Propuestas (${proyectosDeLaFicha.length})`} icon={<FolderOpen />}>
          {proyectosDeLaFicha.length > 0 && (
            <FormField label="Filtrar por estado">
              <Select value={filtroPropFicha} onChange={(e) => setFiltroPropFicha(e.target.value)}>
                <option value="todos">Todas</option>
                {Object.entries(PROYECTO_ESTADO_LABEL).map(([valor, etiqueta]) => (
                  <option key={valor} value={valor}>{etiqueta}</option>
                ))}
              </Select>
            </FormField>
          )}
          {proyectosDeLaFicha.length === 0 ? (
            <p className={s.muted}>Esta ficha aún no tiene propuestas asociadas.</p>
          ) : proyectosFiltradosFicha.length === 0 ? (
            <p className={s.muted}>Ninguna propuesta coincide con el estado seleccionado.</p>
          ) : (
            <ul className={s.studentList}>
              {proyectosFiltradosFicha.map((p) => (
                <li key={p.id}>
                  <Link to={`/admin/detalle-proyecto/${p.id}`} viewTransition className={s.studentRow}>
                    <span className={s.studentInfo}>
                      <span className={s.studentName}>{p.titulo}</span>
                      <span className={s.studentEmail}>{nombreCompleto(p.creator) || 'Sin autor'} · {fechaDesdeApi(p.created_at)}</span>
                    </span>
                    <Badge variant={PROJECT_ESTADO_VARIANT[p.estado] || 'neutral'}>
                      {PROYECTO_ESTADO_LABEL[p.estado] || p.estado}
                    </Badge>
                    <span className={s.arrow} aria-hidden="true">→</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </DataPanel>
      </div>

      <ConfirmModal
        open={modalEliminar}
        titulo="Eliminar ficha"
        mensaje={`¿Seguro que deseas eliminar la ficha "${ficha.nombre}" (${ficha.numero || ficha.codigo})? Si tiene aprendices o propuestas quedará anulada y su número se liberará; si está vacía, se elimina.`}
        textoConfirmar="Sí, eliminar"
        textoCancelar="Cancelar"
        responsabilidad
        verificacion="ELIMINAR"
        onConfirmar={confirmarEliminar}
        onCancelar={() => setModalEliminar(false)}
      />
    </DashboardLayout>
  )
}

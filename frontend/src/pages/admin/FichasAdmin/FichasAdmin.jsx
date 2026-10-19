import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import PageHeader from '../../../components/PageHeader/PageHeader'
import FilterBar from '../../../components/FilterBar/FilterBar'
import DataPanel from '../../../components/DataPanel/DataPanel'
import FormField from '../../../components/FormField/FormField'
import Badge from '../../../components/Badge/Badge'
import Alert from '../../../components/Alert/Alert'
import Button from '../../../components/Button/Button'
import { Input, Select } from '../../../components/Input/Input'
import Actions from '../../../components/Actions/Actions'
import Pagination from '../../../components/Pagination/Pagination'
import EmptyState from '../../../components/EmptyState/EmptyState'
import DataTable from '../../../components/DataTable/DataTable'
import ApiState from '../../../components/ApiState/ApiState'
import ConfirmModal from '../../../components/ConfirmModal/ConfirmModal'
import { Books, ChartBar, CheckCircle, Eye, Lifebuoy, Plus, Trash, Warning, DownloadSimple, MagnifyingGlass } from 'phosphor-react'
import { useApi } from '../../../lib/useApi'
import { useDebounced } from '../../../lib/useDebounced'
import { fichas, programas, redes, instructores, proyectos, usuarios } from '../../../lib/recursos'
import { toFieldErrors } from '../../../lib/api'
import { norm, fechaDesdeApi, nombreCompleto } from '../../../utils/helpers'
import { FICHA_ESTADO_VARIANT as ESTADO_VARIANT, FICHA_ESTADO_LABEL as ESTADO_LABEL } from '../../../constants/badgeVariants'
import s from '../../../components/ListaBase/ListaBase.module.css'
import c from '../../../components/FormularioBase/FormularioBase.module.css'
import { MAX_NOMBRE, MAX_NUMERO_FICHA, esNumeroFichaValido } from '../../../utils/validation'
import { PAGINA_TABLA } from '../../../constants/pagination'

const ITEMS_POR_PAGINA = PAGINA_TABLA



export default function FichasAdmin() {
  const [searchParams] = useSearchParams()
  const [creando, setCreando] = useState(() => searchParams.get('crear') === '1')
  const [creadaMsg, setCreadaMsg] = useState(false)
  const [accionMsg, setAccionMsg] = useState(null)
  const [avisoMsg, setAvisoMsg] = useState('')
  const msgTimer = useRef(null)

  /* ---------- Lista ---------- */
  const [busqueda, setBusqueda] = useState('')
  const [campoBusqueda, setCampoBusqueda] = useState('todos')
  const busquedaDebounced = useDebounced(busqueda, 300)
  const [filtroEstado, setFiltroEstado] = useState('todos')
  const [filtroInstructor, setFiltroInstructor] = useState('todos')
  const [filtroPrograma, setFiltroPrograma] = useState('todos')
  const [filtroRed, setFiltroRed] = useState('todos')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [pagina, setPagina] = useState(1)
  const [aEliminar, setAEliminar] = useState(null)

  // Catálogos (una sola carga) y fichas con búsqueda server-side + debounce.
  const { data: catalogos, cargando: cargandoCatalogos, recargar: recargarCatalogos } = useApi(
    async () => {
      const [listaProgramas, listaRedes, listaInstructores, listaProyectos, listaAprendices] = await Promise.all([
        programas.listar(),
        redes.listar(),
        instructores.listar('generalUser'),
        proyectos.listar(),
        usuarios.listar({ role: 'aprendiz' }),
      ])
      return { listaProgramas, listaRedes, listaInstructores, listaProyectos, listaAprendices }
    },
    [],
    { inicial: null }
  )

  const { data: listaFichas, cargando, error, recargar: recargarFichas } = useApi(
    () => fichas.listar('program,instructor.generalUser,apprentices.generalUser', {
      buscar: busquedaDebounced,
      campo: campoBusqueda,
    }),
    [busquedaDebounced, campoBusqueda],
    { inicial: [] }
  )

  const recargar = async () => { await Promise.all([recargarFichas(), recargarCatalogos()]) }

  const listaProgramas = catalogos?.listaProgramas || []
  const listaRedes = catalogos?.listaRedes || []
  const listaInstructores = catalogos?.listaInstructores || []
  const listaProyectos = catalogos?.listaProyectos || []
  const listaAprendices = catalogos?.listaAprendices || []

  // Propuestas por ficha (id_class_group).
  const propuestasPorFicha = new Map()
  for (const p of listaProyectos) {
    const key = Number(p.id_class_group)
    propuestasPorFicha.set(key, (propuestasPorFicha.get(key) || 0) + 1)
  }

  const filtradas = listaFichas.filter((f) => {
    const coincideEstado = filtroEstado === 'todos' || f.estado === filtroEstado
    const coincideInstructor = filtroInstructor === 'todos' || String(f.id_instructor || '') === String(filtroInstructor)
    const coincidePrograma = filtroPrograma === 'todos' || String(f.id_programa || '') === String(filtroPrograma)
    const coincideRed = filtroRed === 'todos' || String(f.program?.knowledge_network_id || '') === String(filtroRed)
    const fecha = String(f.created_at || '').slice(0, 10)
    const coincideFecha = (!desde || fecha >= desde) && (!hasta || fecha <= hasta)
    return coincideEstado && coincideInstructor && coincidePrograma && coincideRed && coincideFecha
  })

  const paginadas = filtradas.slice(
    (pagina - 1) * ITEMS_POR_PAGINA,
    pagina * ITEMS_POR_PAGINA
  )

  const limpiarFiltros = () => {
    setBusqueda('')
    setCampoBusqueda('todos')
    setFiltroEstado('todos')
    setFiltroInstructor('todos')
    setFiltroPrograma('todos')
    setFiltroRed('todos')
    setDesde('')
    setHasta('')
    setPagina(1)
  }

  const hayFiltros = Boolean(
    busqueda || filtroEstado !== 'todos' || filtroInstructor !== 'todos'
    || filtroPrograma !== 'todos' || filtroRed !== 'todos' || desde || hasta
  )

  useEffect(() => () => { if (msgTimer.current) clearTimeout(msgTimer.current) }, [])

  // Reacciona si se navega a ?crear=1 ya estando en la lista
  useEffect(() => {
    if (searchParams.get('crear') === '1') {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setCreando(true)
    }
  }, [searchParams])

  function mostrarCreada() {
    setCreadaMsg(true)
    if (msgTimer.current) clearTimeout(msgTimer.current)
    msgTimer.current = setTimeout(() => setCreadaMsg(false), 3500)
  }

  function abrirCreacion() {
    setCreadaMsg(false)
    setCreando(true)
  }

  const [exportando, setExportando] = useState(false)

  // Exporta el PDF con las credenciales iniciales de la ficha (solo admin).
  const exportarCredenciales = async (id) => {
    if (exportando) return
    setAccionMsg(null)
    setExportando(true)
    try {
      await fichas.exportarCredenciales(id)
    } catch (err) {
      setAccionMsg(err?.data?.message || 'No se pudieron exportar las credenciales.')
    } finally {
      setExportando(false)
    }
  }

  const confirmarEliminar = async () => {    if (!aEliminar) return
    setAccionMsg(null)
    try {
      const resp = await fichas.eliminar(aEliminar.id)
      // Con historial la ficha se anula (el número queda libre); sin historial
      // se elimina de verdad.
      setAvisoMsg(
        resp?.accion === 'anulada'
          ? 'La ficha tenía aprendices o propuestas: quedó anulada y su número volvió a estar disponible.'
          : 'Ficha eliminada correctamente.'
      )
      setAEliminar(null)
      await recargar()
    } catch (err) {
      setAEliminar(null)
      setAccionMsg(err?.data?.message || 'No se pudo eliminar la ficha.')
    }
  }

  /* ---------- Creación ---------- */
  const [form, setForm] = useState({ red: '', programa: '', nombre: '', numero: '', instructorId: '' })
  const [errores, setErrores] = useState({})
  const [guardando, setGuardando] = useState(false)
  // Número que chocó con una ficha existente (habilita pedir soporte).
  const [numeroDuplicado, setNumeroDuplicado] = useState(null)
  // Aprendices seleccionados para la ficha nueva y su buscador.
  const [aprendicesSel, setAprendicesSel] = useState([])
  const [busquedaAprendiz, setBusquedaAprendiz] = useState('')

  const alternarAprendiz = (id) => {
    setAprendicesSel((sel) => (sel.includes(id) ? sel.filter((x) => x !== id) : [...sel, id]))
  }

  const aprendicesFiltrados = listaAprendices.filter((a) => {
    const q = norm(busquedaAprendiz.trim())
    if (!q) return true
    const nombre = `${a.nombre || ''} ${a.apellido || ''} ${a.username || ''} ${a.numero_documento || ''}`
    return norm(nombre).includes(q)
  })

  const onChange = (e) => {
    const { name, value } = e.target
    setForm((f) => ({ ...f, [name]: value }))
    setErrores((err) => ({ ...err, [name]: undefined }))
    if (name === 'numero') setNumeroDuplicado(null)
  }

  function alCambiarRed(e) {
    const { value } = e.target
    setForm((f) => ({ ...f, red: value, programa: '' }))
    setErrores((err) => ({ ...err, red: undefined, programa: undefined }))
  }

  const validar = () => {
    const err = {}
    if (!form.red) err.red = 'Selecciona la red de conocimiento.'
    if (!form.programa) err.programa = 'Selecciona el programa de formación.'
    if (!form.instructorId) err.instructorId = 'Asigna un instructor a cargo.'
    if (!form.nombre.trim()) err.nombre = 'El nombre de la ficha es obligatorio.'
    const numero = form.numero.trim()
    if (!numero) {
      err.numero = 'El número de ficha es obligatorio.'
    } else if (!esNumeroFichaValido(numero)) {
      err.numero = 'Solo dígitos (4 a 8 caracteres).'
    }
    return err
  }

  const programasDeRed = form.red
    ? listaProgramas.filter((p) => Number(p.knowledge_network_id) === Number(form.red))
    : []
  const instructoresActivos = listaInstructores.filter((i) => i.generalUser?.estado !== false)

  const onSubmit = async (e) => {
    e.preventDefault()
    const err = validar()
    if (Object.keys(err).length) {
      setErrores(err)
      return
    }
    const payload = {
      numero: form.numero.trim(),
      nombre: form.nombre.trim(),
      estado: 'activo',
      id_programa: Number(form.programa),
      id_instructor: form.instructorId === '' ? null : Number(form.instructorId),
      // Pertenencia de los aprendices seleccionados (relación aprendiz-ficha).
      aprendices: aprendicesSel,
    }
    setGuardando(true)
    setNumeroDuplicado(null)
    try {
      // El código de unión lo genera el servidor; el número es único.
      await fichas.crear(payload)
      await recargar()
      setForm({ red: '', programa: '', nombre: '', numero: '', instructorId: '' })
      setAprendicesSel([])
      setBusquedaAprendiz('')
      setErrores({})
      setCreando(false)
      mostrarCreada()
    } catch (error) {
      const campos = toFieldErrors(error?.data)
      if (campos.numero) {
        setErrores({ numero: campos.numero })
        setNumeroDuplicado(form.numero.trim())
      } else if (campos.id_programa) {
        setErrores({ programa: 'El programa no existe en el servidor.' })
      } else {
        setErrores({ numero: error?.data?.message || 'No se pudo crear la ficha en el servidor.' })
      }
    } finally {
      setGuardando(false)
    }
  }

  return (
    <DashboardLayout role="admin" titulo={creando ? 'Crear Ficha' : 'Fichas'}>
      <div className={s.page}>
        <PageHeader
          title={creando ? 'Crear Ficha' : 'Fichas de formación'}
          subtitle={
            creando
              ? 'Registra una ficha y asigna su instructor a cargo.'
              : 'Consulta todas las fichas de la plataforma y administra su información.'
          }
          icon={creando ? <Plus /> : <Books />}
          breadcrumb={
            creando
              ? [
                  { label: 'Dashboard', to: '/admin/dashboard', icon: <ChartBar size={14} /> },
                  { label: 'Fichas', icon: <Books size={14} />, onClick: () => setCreando(false) },
                  { label: 'Nueva ficha' },
                ]
              : [
                  { label: 'Dashboard', to: '/admin/dashboard' },
                  { label: 'Fichas' },
                ]
          }
          onBack={creando ? () => setCreando(false) : undefined}
          actions={
            !creando ? (
              <Button type="button" onClick={abrirCreacion}>
                <Plus size={14} /> Crear Ficha
              </Button>
            ) : undefined
          }
        />

        {creando ? (
          <DataPanel title="Datos de la ficha" icon={<Books />}>
            <form className={c.form} onSubmit={onSubmit} noValidate>
              <div className={c.grid2}>
                <FormField label="Red de conocimiento" required error={errores.red}>
                  <Select name="red" value={form.red} onChange={alCambiarRed}>
                    <option value="">Selecciona una red…</option>
                    {listaRedes.map((r) => (
                      <option key={r.id} value={String(r.id)}>
                        {r.nombre}
                      </option>
                    ))}
                  </Select>
                </FormField>

                <FormField label="Programa de formación" required error={errores.programa}>
                  <Select name="programa" value={form.programa} onChange={onChange} disabled={!form.red}>
                    <option value="">{form.red ? 'Selecciona un programa…' : 'Elige primero la red…'}</option>
                    {programasDeRed.map((p) => (
                      <option key={p.id} value={String(p.id)}>
                        {p.nombre}
                      </option>
                    ))}
                  </Select>
                </FormField>
              </div>

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

              <FormField
                label={`Aprendices de la ficha${aprendicesSel.length ? ` (${aprendicesSel.length})` : ''}`}
                help="Marca los aprendices que pertenecen a esta ficha. Se pueden agregar o retirar después."
              >
                <Input
                  value={busquedaAprendiz}
                  onChange={(e) => setBusquedaAprendiz(e.target.value)}
                  placeholder="Buscar por nombre, usuario o documento…"
                />
                <div className={c.listaAprendices}>
                  {aprendicesFiltrados.length === 0 ? (
                    <p className={c.hint}>No hay aprendices que coincidan. Créalos primero en Usuarios.</p>
                  ) : (
                    aprendicesFiltrados.slice(0, 60).map((a) => (
                      <label key={a.id} className={c.aprendizItem}>
                        <input
                          type="checkbox"
                          checked={aprendicesSel.includes(a.id)}
                          onChange={() => alternarAprendiz(a.id)}
                        />
                        <span>
                          {nombreCompleto(a)} · {a.username}
                          {a.numero_documento ? ` · ${a.numero_documento}` : ''}
                        </span>
                      </label>
                    ))
                  )}
                </div>
              </FormField>

              <FormField label="Nombre de la ficha" required error={errores.nombre}>
                <Input
                  name="nombre"
                  value={form.nombre}
                  onChange={onChange}
                  placeholder="Ej. Análisis y Desarrollo 2718"
                  maxLength={MAX_NOMBRE}
                />
              </FormField>

              <FormField label="Número de ficha" required error={errores.numero} help="Solo dígitos, sin espacios. Ej. 3142101">
                <Input
                  name="numero"
                  inputMode="numeric"
                  value={form.numero}
                  onChange={onChange}
                  placeholder="Ej. 3142101"
                  maxLength={MAX_NUMERO_FICHA}
                />
              </FormField>

              {numeroDuplicado && (
                <Alert variant="warning">
                  <Warning size={14} /> El número {numeroDuplicado} ya está registrado en otra ficha.
                  <Button
                    as="link"
                    to={`/admin/reportes-fallas?numero=${encodeURIComponent(numeroDuplicado)}`}
                    size="sm"
                    variant="secondary"
                  >
                    <Lifebuoy size={14} /> Revisar soporte
                  </Button>
                </Alert>
              )}

              <Actions form>
                <Button type="submit" disabled={guardando}>
                  <CheckCircle size={14} /> {guardando ? 'Creando…' : 'Crear ficha'}
                </Button>
                <Button type="button" variant="secondary" onClick={() => setCreando(false)}>
                  Cancelar
                </Button>
              </Actions>
            </form>
          </DataPanel>
        ) : (
          <ApiState cargando={cargando || cargandoCatalogos} error={error} onReintentar={recargar}>
            {creadaMsg && (
              <Alert>
                <CheckCircle size={14} /> Ficha creada correctamente.
              </Alert>
            )}

            {accionMsg && (
              <Alert variant="danger"><Warning size={14} /> {accionMsg}</Alert>
            )}

            {avisoMsg && (
              <Alert><CheckCircle size={14} /> {avisoMsg}</Alert>
            )}

            <FilterBar
              title="Buscar y filtrar"
              actions={hayFiltros && (
                <Button type="button" variant="secondary" size="sm" onClick={limpiarFiltros}>
                  Limpiar filtros
                </Button>
              )}
            >
              <label className={s.field}>
                <span className={s.label}>Buscar por</span>
                <Select
                  value={campoBusqueda}
                  onChange={(e) => {
                    setCampoBusqueda(e.target.value)
                    setPagina(1)
                  }}
                >
                  <option value="todos">Todos los campos</option>
                  <option value="codigo">Código</option>
                  <option value="nombre">Nombre</option>
                  <option value="numero">Número de ficha</option>
                </Select>
              </label>
              <label className={s.field}>
                <span className={s.label}>Buscar</span>
                <span className={s.searchBox}>
                  <MagnifyingGlass size={16} aria-hidden="true" />
                  <Input
                    className={s.searchInput}
                    value={busqueda}
                    onChange={(e) => {
                      setBusqueda(e.target.value)
                      setPagina(1)
                    }}
                    placeholder="Escribe código, nombre o número…"
                  />
                </span>
              </label>
              <label className={s.field}>
                <span className={s.label}>Estado</span>
                <Select
                  value={filtroEstado}
                  onChange={(e) => {
                    setFiltroEstado(e.target.value)
                    setPagina(1)
                  }}
                >
                  <option value="todos">Todos</option>
                  <option value="activo">{ESTADO_LABEL.activo}</option>
                  <option value="finalizado">{ESTADO_LABEL.finalizado}</option>
                  <option value="anulada">{ESTADO_LABEL.anulada}</option>
                </Select>
              </label>
              <label className={s.field}>
                <span className={s.label}>Instructor</span>
                <Select
                  value={filtroInstructor}
                  onChange={(e) => {
                    setFiltroInstructor(e.target.value)
                    setPagina(1)
                  }}
                >
                  <option value="todos">Todos</option>
                  {listaInstructores.map((i) => (
                    <option key={i.id} value={String(i.id)}>
                      {nombreCompleto(i.generalUser)}
                    </option>
                  ))}
                </Select>
              </label>
              <label className={s.field}>
                <span className={s.label}>Programa</span>
                <Select
                  value={filtroPrograma}
                  onChange={(e) => {
                    setFiltroPrograma(e.target.value)
                    setPagina(1)
                  }}
                >
                  <option value="todos">Todos</option>
                  {listaProgramas.map((p) => (
                    <option key={p.id} value={String(p.id)}>
                      {p.nombre}
                    </option>
                  ))}
                </Select>
              </label>
              <label className={s.field}>
                <span className={s.label}>Red</span>
                <Select
                  value={filtroRed}
                  onChange={(e) => {
                    setFiltroRed(e.target.value)
                    setPagina(1)
                  }}
                >
                  <option value="todos">Todas</option>
                  {listaRedes.map((r) => (
                    <option key={r.id} value={String(r.id)}>
                      {r.nombre}
                    </option>
                  ))}
                </Select>
              </label>
              <label className={s.field}>
                <span className={s.label}>Desde</span>
                <Input
                  type="date"
                  value={desde}
                  onChange={(e) => { setDesde(e.target.value); setPagina(1) }}
                />
              </label>
              <label className={s.field}>
                <span className={s.label}>Hasta</span>
                <Input
                  type="date"
                  value={hasta}
                  onChange={(e) => { setHasta(e.target.value); setPagina(1) }}
                />
              </label>
              <p className={s.info}>
                {filtradas.length} ficha{filtradas.length !== 1 ? 's' : ''}
              </p>
            </FilterBar>

            {paginadas.length === 0 ? (
              <EmptyState
                icon={<Books />}
                title="No hay fichas"
                message={
                  hayFiltros
                    ? 'Ninguna ficha coincide con los filtros aplicados.'
                    : 'Aún no se han creado fichas de formación. Crea la primera.'
                }
                actionLabel={hayFiltros ? 'Limpiar filtros' : 'Crear primera ficha'}
                onAction={hayFiltros ? limpiarFiltros : abrirCreacion}
              />
            ) : (
              <>
                <DataTable
                  ariaLabel="Fichas de formación"
                  columns={[
                    {
                      key: 'codigo',
                      header: 'Código',
                      render: (f) => <code className={s.codigo}>{f.codigo}</code>,
                    },
                    {
                      key: 'ficha',
                      header: 'Ficha',
                      render: (f) => (
                        <>
                          <span className={s.title}>{f.nombre}</span>
                          <br />
                          <span className={s.subText}>N° {f.numero || '—'} · {f.program?.nombre || 'Sin programa'}</span>
                        </>
                      ),
                    },
                    {
                      key: 'instructor',
                      header: 'Instructor',
                      render: (f) => nombreCompleto(f.instructor?.generalUser) || <span className={s.muted}>Sin asignar</span>,
                    },
                    {
                      key: 'aprendices',
                      header: 'Aprendices',
                      render: (f) => <span className={s.count}>{(f.apprentices || []).length}</span>,
                    },
                    {
                      key: 'propuestas',
                      header: 'Propuestas',
                      render: (f) => <span className={s.count}>{propuestasPorFicha.get(Number(f.id)) || 0}</span>,
                    },
                    {
                      key: 'estado',
                      header: 'Estado',
                      render: (f) => (
                        <Badge variant={ESTADO_VARIANT[f.estado] || 'neutral'}>
                          {ESTADO_LABEL[f.estado] || f.estado}
                        </Badge>
                      ),
                    },
                    { key: 'created_at', header: 'Creada', render: (f) => fechaDesdeApi(f.created_at) },
                    {
                      key: 'acciones',
                      header: 'Acciones',
                      align: 'end',
                      render: (f) => (
                        <div className={s.actions}>
                          <Button
                            type="button"
                            size="sm"
                            variant="ghost"
                            title="Exportar credenciales de la ficha"
                            disabled={exportando}
                            onClick={() => exportarCredenciales(f.id)}
                          >
                            <DownloadSimple size={14} /> Credenciales
                          </Button>
                          <Button
                            as="link"
                            to={`/admin/detalle-ficha/${f.id}`}
                            viewTransition
                            size="sm"
                            variant="secondary"
                          >
                            <Eye size={14} /> Ver
                          </Button>
                          <Button
                            type="button"
                            size="sm"
                            variant="danger"
                            onClick={() => setAEliminar(f)}
                          >
                            <Trash size={14} /> Eliminar
                          </Button>
                        </div>
                      ),
                    },
                  ]}
                  rows={paginadas}
                  keyOf={(f) => f.id}
                />

                <Pagination
                  totalItems={filtradas.length}
                  itemsPerPage={ITEMS_POR_PAGINA}
                  paginaActual={pagina}
                  setPaginaActual={setPagina}
                  itemName="fichas"
                  filteredCount={filtradas.length}
                />
              </>
            )}
          </ApiState>
        )}
      </div>

      <ConfirmModal
        open={!!aEliminar}
        titulo="Eliminar ficha"
        mensaje={
          aEliminar
            ? `¿Seguro que deseas quitar la ficha "${aEliminar.nombre}" (${aEliminar.numero || aEliminar.codigo})? Si tiene aprendices o propuestas quedará anulada y su número se liberará; si está vacía, se elimina.`
            : ''
        }
        textoConfirmar="Sí, eliminar"
        textoCancelar="Cancelar"
        responsabilidad
        verificacion="ELIMINAR"
        onConfirmar={confirmarEliminar}
        onCancelar={() => setAEliminar(null)}
      />
    </DashboardLayout>
  )
}

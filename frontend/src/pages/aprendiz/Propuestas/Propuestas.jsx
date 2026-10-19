import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import PageHeader from '../../../components/PageHeader/PageHeader'
import FilterBar from '../../../components/FilterBar/FilterBar'
import Pagination from '../../../components/Pagination/Pagination'
import EmptyState from '../../../components/EmptyState/EmptyState'
import Badge from '../../../components/Badge/Badge'
import GradeBadge from '../../../components/GradeBadge/GradeBadge'
import Button from '../../../components/Button/Button'
import Actions from '../../../components/Actions/Actions'
import Alert from '../../../components/Alert/Alert'
import ApiState from '../../../components/ApiState/ApiState'
import { Input, Select, Textarea } from '../../../components/Input/Input'
import FormField from '../../../components/FormField/FormField'
import { CalendarBlank, ChartBar, FolderOpen, GraduationCap, PaperPlaneRight, Plus, Tray } from 'phosphor-react'
import { useAuth } from '../../../contexts/AuthContext'
import { useApi } from '../../../lib/useApi'
import { toFieldErrors } from '../../../lib/api'
import { proyectos, aprendices, fichas, similitudes as similitudesApi, INCLUDE_PROYECTOS } from '../../../lib/recursos'
import { PROJECT_ESTADO_VARIANT, PROJECT_ESTADO_LABEL as ESTADO_LABEL } from '../../../constants/badgeVariants'
import { fechaDesdeApi, norm, esPropietarioProyecto, infoSimilitud } from '../../../utils/helpers'
import { PAGINA_TARJETAS } from '../../../constants/pagination'
import { MAX_TITULO, MAX_DESCRIPCION, MAX_DESCRIPCION_CORTA } from '../../../utils/validation'
// Estilos reutilizados de las páginas originales (lista + formulario)
import s from '../../../components/ListaBase/ListaBase.module.css'
import n from '../../../components/FormularioBase/FormularioBase.module.css'

const ITEMS_POR_PAGINA = PAGINA_TARJETAS

// Relaciones necesarias para detectar al equipo (pivote) en la lista.

const AREAS = [
  'Desarrollo Web',
  'Inteligencia Artificial',
  'Ciencia de Datos',
  'Ciberseguridad',
  'Otro',
]


// Errores 422 de la API (columnas del backend) → campos del formulario.
const CAMPOS_API = {
  titulo: 'title',
  resumen: 'description',
  objetivo_general: 'objetivoGeneral',
  objetivos_especificos: 'objetivosEspecificos',
  area_aplicacion: 'areaAplicacion',
  palabras_clave: 'keywords',
}

// Una propuesta es del aprendiz si la creó o si figura en su equipo.
const esMio = esPropietarioProyecto

function nombreAprendiz(a) {
  const g = a?.generalUser || {}
  return [g.nombre, g.apellido].filter(Boolean).join(' ').trim() || g.correo || `Aprendiz #${a?.id}`
}

export default function Propuestas() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [creando, setCreando] = useState(() => searchParams.get('crear') === '1')

  // Reacciona si se navega a ?crear=1 ya estando en la lista
  useEffect(() => {
    if (searchParams.get('crear') === '1') {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setCreando(true)
    }
  }, [searchParams])

  /* ---------- Lista ---------- */
  const [busqueda, setBusqueda] = useState('')
  const [filtro, setFiltro] = useState('todos')
  const [pagina, setPagina] = useState(1)
  // Borrador ya creado si el envío falló: evita duplicar la propuesta al reintentar.
  const [borradorId, setBorradorId] = useState(null)

  const { data: todosProyectos, cargando, error, recargar } = useApi(
    () => proyectos.listar({ included: INCLUDE_PROYECTOS }),
    [],
    { inicial: [] }
  )
  const { data: todasSimilitudes } = useApi(() => similitudesApi.listar(), [], { inicial: [] })
  const proyectosMios = useMemo(
    () => todosProyectos.filter((p) => esMio(p, user.id)),
    [todosProyectos, user.id]
  )

  const filtrados = useMemo(() => {
    let lista = filtro === 'todos' ? proyectosMios : proyectosMios.filter((p) => p.estado === filtro)
    const q = norm(busqueda.trim())
    if (q) {
      lista = lista.filter((p) =>
        norm(p.titulo).includes(q) ||
        norm(p.resumen).includes(q) ||
        norm(p.palabras_clave).includes(q)
      )
    }
    return lista
  }, [proyectosMios, filtro, busqueda])

  const inicio = (pagina - 1) * ITEMS_POR_PAGINA
  const visibles = filtrados.slice(inicio, inicio + ITEMS_POR_PAGINA)

  function cambiarFiltro(valor) {
    setFiltro(valor)
    setPagina(1)
  }

  /* ---------- Ficha y compañeros del aprendiz ---------- */
  const { data: aprendicesApi, cargando: cargandoPerfil, error: errorPerfil, recargar: recargarPerfil } =
    useApi(() => aprendices.listar(), [], { inicial: [] })
  const { data: fichasApi, cargando: cargandoFichas, error: errorFichas, recargar: recargarFichas } =
    useApi(() => fichas.listar(), [], { inicial: [] })

  const miAprendiz = useMemo(
    () => aprendicesApi.find((a) => Number(a.id_usuario) === Number(user.id)) || null,
    [aprendicesApi, user.id]
  )
  const miFicha = useMemo(
    () => (miAprendiz ? fichasApi.find((f) => Number(f.id) === Number(miAprendiz.id_class_group)) || null : null),
    [miAprendiz, fichasApi]
  )
  const instructorId = miFicha?.instructor?.id ?? null

  const companeros = useMemo(
    () => (miFicha
      ? aprendicesApi.filter(
          (a) => Number(a.id_class_group) === Number(miFicha.id) && Number(a.id_usuario) !== Number(user.id)
        )
      : []),
    [aprendicesApi, miFicha, user.id]
  )

  /* ---------- Creación ---------- */
  const [form, setForm] = useState({
    title: '',
    description: '',
    objetivoGeneral: '',
    objetivosEspecificos: '',
    areaAplicacion: '',
    keywords: '',
  })
  const [errors, setErrors] = useState({})
  const [errorGeneral, setErrorGeneral] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [seleccionados, setSeleccionados] = useState([])
  // Envío desde la lista (borrador/rechazada).
  const [enviandoId, setEnviandoId] = useState(null)
  const [avisoEnvio, setAvisoEnvio] = useState('')

  function alternarCompanero(id) {
    setSeleccionados((sel) => (sel.includes(id) ? sel.filter((x) => x !== id) : [...sel, id]))
  }

  const objetivosValidos = useMemo(
    () =>
      form.objetivosEspecificos
        .split('\n')
        .map((l) => l.trim())
        .filter(Boolean),
    [form.objetivosEspecificos]
  )

  function set(campo, valor) {
    setForm((f) => ({ ...f, [campo]: valor }))
    setErrors((e) => ({ ...e, [campo]: undefined }))
  }

  function validar() {
    const errs = {}
    if (form.title.trim().length < 5) errs.title = 'El nombre debe tener al menos 5 caracteres.'
    if (form.description.trim().length < 20) {
      errs.description = 'La descripción debe tener al menos 20 caracteres.'
    }
    if (form.objetivoGeneral.trim().length < 15) {
      errs.objetivoGeneral = 'El objetivo general debe tener al menos 15 caracteres.'
    }
    if (objetivosValidos.length < 2) {
      errs.objetivosEspecificos = 'Escribe al menos 2 objetivos específicos (uno por línea).'
    } else if (objetivosValidos.some((o) => o.length < 8)) {
      errs.objetivosEspecificos = 'Cada objetivo específico debe tener al menos 8 caracteres.'
    }
    if (!form.areaAplicacion) errs.areaAplicacion = 'Selecciona un área de aplicación.'
    return errs
  }

  function empezar() {
    // Crear una propuesta exige ficha: si no la tiene, se le guía a unirse.
    if (!miFicha) {
      navigate('/aprendiz/ficha')
      return
    }
    setBorradorId(null)
    setCreando(true)
  }

  function volverALista() {
    setBorradorId(null)
    setCreando(false)
  }

  // Guarda la propuesta como borrador (sin validar ni enviar al instructor).
  async function guardarBorrador() {
    if (!miFicha || !miAprendiz) {
      setErrorGeneral('Únete a una ficha para registrar propuestas.')
      return
    }
    setGuardando(true)
    setErrorGeneral('')
    try {
      await proyectos.crear({
        titulo: form.title.trim() || null,
        resumen: form.description.trim() || null,
        palabras_clave: form.keywords.trim() || null,
        area_aplicacion: form.areaAplicacion || null,
        objetivo_general: form.objetivoGeneral.trim() || null,
        objetivos_especificos: objetivosValidos,
        id_creador: Number(user.id),
        id_instructor_asignado: instructorId,
        id_class_group: Number(miFicha.id),
      })
      setCreando(false)
      await recargar()
    } catch (err) {
      setErrorGeneral(err?.data?.message || 'No se pudo guardar el borrador.')
    } finally {
      setGuardando(false)
    }
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (guardando) return
    const errs = validar()
    setErrors(errs)
    setErrorGeneral('')
    if (Object.keys(errs).length > 0) return
    if (!miFicha || !miAprendiz) {
      setErrorGeneral('Únete a una ficha para registrar propuestas.')
      return
    }

    let id = borradorId
    setGuardando(true)
    try {
      const equipoFallidos = []

      // 1) Solo la primera vez: crear el borrador y vincular el equipo. Si el
      // envío falla, el reintento reutiliza el MISMO borrador (sin duplicar).
      if (!id) {
        const nueva = await proyectos.crear({
          titulo: form.title.trim(),
          resumen: form.description.trim(),
          palabras_clave: form.keywords.trim() || null,
          area_aplicacion: form.areaAplicacion,
          objetivo_general: form.objetivoGeneral.trim(),
          objetivos_especificos: objetivosValidos,
          id_creador: Number(user.id),
          id_instructor_asignado: instructorId,
          id_class_group: Number(miFicha.id),
        })
        id = nueva.id
        setBorradorId(id)

        for (const idAprendiz of seleccionados) {
          try {
            await proyectos.agregarAlEquipo(Number(idAprendiz), Number(id))
          } catch {
            equipoFallidos.push(Number(idAprendiz))
          }
        }
      } else {
        // W-01: reintento tras un fallo. Antes de reenviar se persisten las
        // correcciones del formulario y el equipo, y se CONFIRMAN leyendo.
        await proyectos.actualizar(id, {
          titulo: form.title.trim(),
          resumen: form.description.trim(),
          palabras_clave: form.keywords.trim() || null,
          area_aplicacion: form.areaAplicacion,
          objetivo_general: form.objetivoGeneral.trim(),
          objetivos_especificos: objetivosValidos,
        })

        const fresco = await proyectos.obtener(id)
        const contenidoConfirmado =
          fresco?.titulo === form.title.trim() &&
          (fresco?.resumen || '') === form.description.trim() &&
          (fresco?.area_aplicacion || '') === form.areaAplicacion &&
          (fresco?.objetivo_general || '') === form.objetivoGeneral.trim()
        if (!contenidoConfirmado) {
          setErrorGeneral('No se pudo confirmar el guardado de los cambios. Vuelve a intentarlo.')
          return
        }

        for (const idAprendiz of seleccionados) {
          try {
            await proyectos.agregarAlEquipo(Number(idAprendiz), Number(id))
          } catch {
            equipoFallidos.push(Number(idAprendiz))
          }
        }
        if (equipoFallidos.length > 0) {
          setErrorGeneral('No se pudieron guardar todos los integrantes del equipo. Vuelve a intentarlo.')
          return
        }
      }

      // 2) Envío explícito: el backend valida, detecta similitudes y avisa al
      // instructor. La pantalla de análisis espera ese resultado.
      await proyectos.enviar(id)
      setBorradorId(null)
      navigate('/aprendiz/analizando-proyecto', {
        state: { projectId: id, equipoFallidos },
        replace: true,
      })
    } catch (err) {
      const campos = toFieldErrors(err?.data)
      const traducidos = {}
      for (const [clave, mensaje] of Object.entries(campos)) {
        traducidos[CAMPOS_API[clave] || clave] = mensaje
      }
      setErrors(traducidos)
      if (Object.keys(traducidos).length === 0) {
        setErrorGeneral(
          id
            ? 'La propuesta quedó guardada como borrador. Corrige lo indicado y vuelve a intentar el envío.'
            : (err?.data?.message || err?.message || 'No se pudo enviar la propuesta. Intenta de nuevo.')
        )
      }
    } finally {
      setGuardando(false)
    }
  }

  // Envía una propuesta existente (borrador o rechazada) desde la lista.
  async function enviarExistente(p) {
    setAvisoEnvio('')
    setEnviandoId(p.id)
    try {
      await proyectos.enviar(p.id)
      navigate('/aprendiz/analizando-proyecto', { state: { projectId: p.id }, replace: true })
    } catch (err) {
      setAvisoEnvio(err?.data?.message || 'No se pudo enviar la propuesta.')
      setEnviandoId(null)
    }
  }

  // Carga inicial del perfil académico (aprendiz ↔ ficha).
  if (cargandoPerfil || cargandoFichas) {
    return (
      <DashboardLayout role="aprendiz" titulo="Mis Propuestas">
        <div className={s.page}><ApiState cargando /></div>
      </DashboardLayout>
    )
  }

  if (errorPerfil || errorFichas) {
    return (
      <DashboardLayout role="aprendiz" titulo="Mis Propuestas">
        <div className={s.page}>
          <ApiState error={errorPerfil || errorFichas} onReintentar={() => { recargarPerfil(); recargarFichas() }} />
        </div>
      </DashboardLayout>
    )
  }

  // El estado "sin ficha" solo bloquea cuando NO hay historial (usuario nuevo).
  // Si tiene propuestas propias, se muestran igual (trazabilidad).
  if (!miAprendiz && proyectosMios.length === 0) {
    return (
      <DashboardLayout role="aprendiz" titulo="Mis Propuestas">
        <div className={s.page}>
          <EmptyState
            icon={<GraduationCap size={40} weight="light" />}
            title="Aún no perteneces a una ficha"
            message="Únete a tu ficha con el código que te comparta tu instructor para poder registrar tus propuestas."
            actionLabel="Unirme a una ficha"
            onAction={() => navigate('/aprendiz/ficha')}
          />
        </div>
      </DashboardLayout>
    )
  }

  // El formulario de creación solo se muestra con ficha; si no la hay, se ve
  // la lista (con aviso) y el botón lleva a unirse a una ficha.
  const enForm = creando && !!miFicha

  return (
    <DashboardLayout role="aprendiz" titulo={enForm ? 'Nueva Propuesta' : 'Mis Propuestas'}>
      <div className={s.page}>
        <PageHeader
          title={enForm ? 'Nueva Propuesta' : 'Mis Propuestas'}
          subtitle={
            enForm
              ? 'Registra tu idea: una solución de software para un problema concreto. No necesitas definir tecnologías ni entregables todavía.'
              : 'Administra y revisa el estado de tus propuestas académicas'
          }
          icon={enForm ? <Plus /> : <FolderOpen />}
          breadcrumb={
            enForm
              ? [
                  { label: 'Dashboard', to: '/aprendiz/dashboard', icon: <ChartBar size={14} /> },
                  { label: 'Mis Propuestas', icon: <FolderOpen size={14} />, onClick: volverALista },
                  { label: 'Nueva propuesta' },
                ]
              : []
          }
          onBack={enForm ? volverALista : undefined}
          actions={
            !enForm ? (
              <Button type="button" onClick={empezar}>
                <Plus size={14} /> Nueva propuesta
              </Button>
            ) : undefined
          }
        />

        {enForm ? (
          <form className={n.form} onSubmit={handleSubmit} noValidate>
                <FormField label="Nombre de la propuesta" error={errors.title} required>
                  <Input
                    type="text"
                    value={form.title}
                    onChange={(e) => set('title', e.target.value)}
                    placeholder="Ej: Sistema de monitoreo ambiental con IoT"
                    maxLength={MAX_TITULO}
                    autoFocus
                  />
                </FormField>

                <FormField
                  label="Descripción del problema y la solución"
                  error={errors.description}
                  help={`${form.description.length}/600 caracteres`}
                  required
                >
                  <Textarea
                    rows={5}
                    value={form.description}
                    onChange={(e) => set('description', e.target.value)}
                    placeholder="¿Qué problema quieres resolver? ¿Cómo lo resolvería tu software? ¿Quiénes se beneficiarían?"
                    maxLength={MAX_DESCRIPCION}
                  />
                </FormField>

                <FormField
                  label="Objetivo general"
                  error={errors.objetivoGeneral}
                  help="Qué quieres lograr con la solución, en una sola frase."
                  required
                >
                  <Textarea
                    rows={3}
                    value={form.objetivoGeneral}
                    onChange={(e) => set('objetivoGeneral', e.target.value)}
                    placeholder="Ej: Optimizar el riego de cultivos pequeños mediante monitoreo automatizado de humedad del suelo."
                    maxLength={MAX_DESCRIPCION_CORTA}
                    autoFocus
                  />
                </FormField>

                <FormField
                  label="Objetivos específicos"
                  error={errors.objetivosEspecificos}
                  help={`Un objetivo por línea (mínimo 2). Usa verbos como Implementar, Diseñar, Evaluar. Llevas ${objetivosValidos.length}.`}
                  required
                >
                  <Textarea
                    rows={5}
                    value={form.objetivosEspecificos}
                    onChange={(e) => set('objetivosEspecificos', e.target.value)}
                    placeholder={'Implementar sensores de humedad en el cultivo.\nDiseñar un panel web para visualizar los datos.\nEvaluar el ahorro de agua durante un mes.'}
                  />
                </FormField>

                <FormField
                  label={`Integrantes del equipo${seleccionados.length > 0 ? ` (${seleccionados.length})` : ''}`}
                  help="Opcional. Compañeros de tu ficha con los que desarrollarás la propuesta."
                >
                  {companeros.length === 0 ? (
                    <p className={n.hint}>Aún no hay compañeros en tu ficha para invitar.</p>
                  ) : (
                    <div className={n.chipList}>
                      {companeros.map((c) => {
                        const activo = seleccionados.includes(c.id)
                        return (
                          <button
                            key={c.id}
                            type="button"
                            className={`${n.chip} ${activo ? n.chipActive : ''}`}
                            onClick={() => alternarCompanero(c.id)}
                            aria-pressed={activo}
                          >
                            {nombreAprendiz(c)}
                          </button>
                        )
                      })}
                    </div>
                  )}
                </FormField>

                <FormField label="Área de aplicación" error={errors.areaAplicacion} required>
                  <Select
                    value={form.areaAplicacion}
                    onChange={(e) => set('areaAplicacion', e.target.value)}
                  >
                    <option value="">Selecciona un área...</option>
                    {AREAS.map((a) => (
                      <option key={a} value={a}>
                        {a}
                      </option>
                    ))}
                  </Select>
                </FormField>

                <p className={n.hint}>
                  Ficha de formación: {miFicha.nombre} · {miFicha.codigo}
                </p>

                <FormField
                  label="Palabras clave"
                  help="Opcional. Si aún no las tienes claras, puedes agregarlas después."
                >
                  <Input
                    type="text"
                    value={form.keywords}
                    onChange={(e) => set('keywords', e.target.value)}
                    placeholder="iot, sensores, agricultura"
                  />
                </FormField>

            {errorGeneral && <Alert variant="danger">{errorGeneral}</Alert>}

            <Actions className={n.actions}>
              <Button type="submit" disabled={guardando}>
                <PaperPlaneRight size={14} /> {guardando ? 'Enviando...' : 'Enviar propuesta y analizar'}
              </Button>
              <Button type="button" variant="secondary" disabled={guardando} onClick={guardarBorrador}>
                Guardar borrador
              </Button>
              <Button type="button" variant="ghost" onClick={volverALista}>
                Cancelar
              </Button>
            </Actions>
          </form>
        ) : (
          <>
            <FilterBar title="Filtros">
              <label className={s.field}>
                <span className={s.label}>Buscar</span>
                <Input
                  value={busqueda}
                  onChange={(e) => { setBusqueda(e.target.value); setPagina(1) }}
                  placeholder="Título, resumen o palabras clave…"
                />
              </label>
              <label className={s.field}>
                <span className={s.label}>Estado</span>
                <Select
                  value={filtro}
                  onChange={(e) => cambiarFiltro(e.target.value)}
                >
                  <option value="todos">Todos</option>
                  {Object.entries(ESTADO_LABEL).map(([valor, etiqueta]) => (
                    <option key={valor} value={valor}>{etiqueta}</option>
                  ))}
                </Select>
              </label>
            </FilterBar>

            <ApiState cargando={cargando} error={error} onReintentar={recargar}>
              {filtrados.length === 0 ? (
                <EmptyState
                  icon={<Tray />}
                  title={filtro === 'todos' && proyectosMios.length === 0 ? 'Aún no tienes propuestas' : 'Sin resultados'}
                  message={
                    filtro === 'todos' && proyectosMios.length === 0
                      ? 'Registra tu primera propuesta y envíala para analizarla en ProyecTwin.'
                      : 'No hay propuestas con el estado seleccionado. Prueba con otro filtro.'
                  }
                  actionLabel={
                    filtro === 'todos' && proyectosMios.length === 0 ? 'Crear propuesta' : undefined
                  }
                  actionIcon={<Plus size={14} />}
                  onAction={
                    filtro === 'todos' && proyectosMios.length === 0 ? empezar : undefined
                  }
                />
              ) : (
                <>
                  {!miFicha && (
                    <Alert variant="warning">
                      No perteneces a ninguna ficha. Únete con el código de tu instructor para
                      <strong> crear nuevas</strong>; tus propuestas anteriores se conservan.
                    </Alert>
                  )}
                  {miFicha && filtrados.some((p) => Number(p.id_class_group) !== Number(miFicha?.id)) && (
                    <Alert variant="info">
                      Algunas propuestas son de una <strong>ficha anterior</strong>: se conservan en la
                      ficha donde las creaste y no se mueven al cambiarte de ficha.
                    </Alert>
                  )}
                  {avisoEnvio && (
                    <Alert variant="warning">{avisoEnvio}</Alert>
                  )}
                  <div className={s.cardGrid}>
                    {visibles.map((p) => {
                      const info = infoSimilitud(todasSimilitudes, p.id)
                      // Solo se puede enviar si la propuesta vive en la ficha
                      // activa actual del aprendiz (el backend lo exige igual).
                      const editable = (p.estado === 'borrador' || p.estado === 'rechazado')
                        && Number(p.id_class_group) === Number(miFicha?.id)
                        && p.classGroup?.estado === 'activo'
                      return (
                        <article key={p.id} className={`${s.card} ${s.cardClickable}`}>
                          <header className={s.cardHeader}>
                            <Link to={`/aprendiz/detalle-proyecto/${p.id}`} viewTransition className={s.cardTitle}>
                              {p.titulo}
                            </Link>
                            <Badge variant={PROJECT_ESTADO_VARIANT[p.estado] || 'neutral'}>
                              {ESTADO_LABEL[p.estado] || p.estado}
                            </Badge>
                          </header>
                          <p className={s.cardDesc}>{p.resumen}</p>
                          <footer className={s.cardFooter}>
                            <span className={s.cardMeta}><CalendarBlank size={14} /> {fechaDesdeApi(p.created_at)}</span>
                            <span className={s.cardMeta}>
                              <GraduationCap size={14} /> {p.classGroup?.codigo || 'Sin ficha'}
                              {p.classGroup?.program?.nombre ? ` · ${p.classGroup.program.nombre}` : ''}
                              {Number(p.id_class_group) !== Number(miFicha?.id) ? ' · ficha anterior' : ''}
                            </span>
                            {info && (
                              <span title={`${info.pct}% · ${info.count} coincidencia${info.count !== 1 ? 's' : ''}`}>
                                <GradeBadge score={info.pct} size="sm" />
                              </span>
                            )}
                            {editable && (
                              <Button
                                type="button"
                                size="sm"
                                variant="secondary"
                                disabled={enviandoId === p.id}
                                onClick={() => enviarExistente(p)}
                              >
                                <PaperPlaneRight size={14} /> {enviandoId === p.id ? 'Enviando…' : 'Enviar'}
                              </Button>
                            )}
                          </footer>
                        </article>
                      )
                    })}
                  </div>
                  <Pagination
                    totalItems={filtrados.length}
                    filteredCount={filtrados.length}
                    itemsPerPage={ITEMS_POR_PAGINA}
                    paginaActual={pagina}
                    setPaginaActual={setPagina}
                    itemName="propuestas"
                  />
                </>
              )}
            </ApiState>
          </>
        )}
      </div>
    </DashboardLayout>
  )
}

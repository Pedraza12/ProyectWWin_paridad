import { useMemo } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowLeft, ArrowRight, Books, CalendarBlank, FileText, MagnifyingGlass, User } from 'phosphor-react'
import PageHeader from '../PageHeader/PageHeader'
import DataPanel from '../DataPanel/DataPanel'
import Badge from '../Badge/Badge'
import GradeBadge from '../GradeBadge/GradeBadge'
import Tag from '../Tag/Tag'
import EmptyState from '../EmptyState/EmptyState'
import SimilarityGraph from '../SimilarityGraph/SimilarityGraph'
import { useAuth } from '../../contexts/AuthContext'
import { useApi } from '../../lib/useApi'
import { similitudes, fichas } from '../../lib/recursos'
import { fechaDesdeApi, esPropietarioProyecto, nombreCompleto } from '../../utils/helpers'
import s from './DetalleSimilitudBase.module.css'
import { PROJECT_ESTADO_LABEL as ESTADO_PROYECTO_LABEL, PROJECT_ESTADO_VARIANT as ESTADO_PROYECTO_VARIANT } from '../../constants/badgeVariants'

const RUTA_DETALLE_POR_ROL = {
  aprendiz: { volver: '/aprendiz/propuestas', label: 'Mis Propuestas' },
  instructor: { volver: '/instructor/dashboard', label: 'Dashboard' },
  admin: { volver: '/admin/similitudes', label: 'Similitudes' },
}


// ¿La propuesta pertenece a las fichas o programas del instructor?
function estaEnAlcance(proyecto, misFichasIds, misProgramas) {
  if (!proyecto) return false
  if (misFichasIds.has(Number(proyecto.id_class_group))) return true
  const programa = Number(proyecto.classGroup?.id_programa)
  return Number.isFinite(programa) && misProgramas.has(programa)
}

export default function DetalleSimilitudBase({
  similitud,
  role = 'aprendiz',
  projectPath = '/detalle-proyecto',
  actions = null,
  children = null,
  backTo,
  backLabel,
}) {
  const navigate = useNavigate()
  const ruta = RUTA_DETALLE_POR_ROL[role] || RUTA_DETALLE_POR_ROL.aprendiz
  const { user } = useAuth()
  const esInstructor = role === 'instructor'

  // Proyectos del par: vienen incluidos en la similitud (project1/project2).
  const proyecto1 = similitud?.project1 || null
  const proyecto2 = similitud?.project2 || null

  // ¿La propuesta es del usuario (creador o integrante del equipo)?
  const esMia = (p) => esPropietarioProyecto(p, user?.id)

  // Lados del par que son del usuario. Sin esto, "A" era el id menor y la
  // propuesta propia podía salir como B.
  const misIds = useMemo(
    () => new Set([proyecto1, proyecto2].filter(esMia).map((p) => Number(p.id))),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- esMia depende de user?.id
    [proyecto1, proyecto2, user?.id]
  )

  // Orden mostrado: si una es mía, va como A (yo la subí); la otra es B.
  const [propuestaA, propuestaB] = useMemo(() => {
    if (!proyecto1 || !proyecto2) return [proyecto1, proyecto2]
    const p1Mia = misIds.has(Number(proyecto1.id))
    const p2Mia = misIds.has(Number(proyecto2.id))
    return p2Mia && !p1Mia ? [proyecto2, proyecto1] : [proyecto1, proyecto2]
  }, [proyecto1, proyecto2, misIds])

  // Fichas del instructor: se identifican por el generalUser anidado.
  const { data: listaFichas, cargando: cargandoFichas } = useApi(() => fichas.listar(), [], { inicial: [] })
  const misFichasIds = useMemo(
    () => new Set((listaFichas || [])
      .filter((f) => Number(f.instructor?.generalUser?.id) === Number(user?.id))
      .map((f) => Number(f.id))),
    [listaFichas, user?.id]
  )
  const misProgramas = useMemo(
    () => new Set((listaFichas || [])
      .filter((f) => Number(f.instructor?.generalUser?.id) === Number(user?.id))
      .map((f) => Number(f.id_programa))
      .filter(Number.isFinite)),
    [listaFichas, user?.id]
  )

  // "Otras coincidencias relacionadas": solo los pares que tocan el par actual
  // (`related_to`). El backend además acota por rol: el aprendiz solo recibe
  // pares de sus proyectos, así que no puede ver similitudes ajenas.
  const relatedTo = [proyecto1?.id, proyecto2?.id].filter(Boolean).join(',')
  const { data: todas } = useApi(
    () => similitudes.listar(relatedTo ? { related_to: relatedTo } : {}),
    [relatedTo],
    { inicial: [] }
  )
  const proyectosPorId = useMemo(() => {
    const map = new Map()
    for (const x of todas || []) {
      if (x.project1) map.set(Number(x.project1.id), x.project1)
      if (x.project2) map.set(Number(x.project2.id), x.project2)
    }
    if (proyecto1) map.set(Number(proyecto1.id), proyecto1)
    if (proyecto2) map.set(Number(proyecto2.id), proyecto2)
    return map
  }, [todas, proyecto1, proyecto2])

  const otras = useMemo(() => {
    if (!similitud) return []
    const idA = Number(propuestaA?.id)
    const idB = Number(propuestaB?.id)
    const esPropietario = misIds.size > 0
    const vistas = new Map()
    for (const x of todas || []) {
      if (Number(x.id) === Number(similitud.id)) continue
      const x1 = Number(x.id_proyecto_1)
      const x2 = Number(x.id_proyecto_2)
      // Del aprendiz: solo coincidencias de SUS propuestas del par (lado A).
      // Del staff: las de cualquiera de los dos lados del par abierto.
      const comparte = esPropietario
        ? (misIds.has(x1) || misIds.has(x2))
        : (x1 === idA || x2 === idA || x1 === idB || x2 === idB)
      if (!comparte) continue

      let origen = null
      let otroPid = null
      if (x1 === idA || x2 === idA) {
        origen = 'A'
        otroPid = x1 === idA ? x2 : x1
      } else if (x1 === idB || x2 === idB) {
        origen = 'B'
        otroPid = x1 === idB ? x2 : x1
      }
      if (!origen || vistas.has(x.id)) continue
      // Instructor: solo otras coincidencias de su ficha o programa.
      if (esInstructor && !cargandoFichas) {
        if (!estaEnAlcance(proyectosPorId.get(Number(otroPid)), misFichasIds, misProgramas)) continue
      }
      vistas.set(x.id, { ...x, origen, otroPid })
    }
    return [...vistas.values()].sort((a, b) => Number(b.porcentaje) - Number(a.porcentaje))
  }, [similitud, propuestaA, propuestaB, misIds, todas, esInstructor, cargandoFichas, proyectosPorId, misFichasIds, misProgramas])

  // Guard instructor: bloquear si el par no es de su programa/fichas (hooks antes de returns).
  const noAutorizado = useMemo(() => {
    if (!esInstructor || !similitud || cargandoFichas) return false
    if (!proyecto1 || !proyecto2) return false
    return !(estaEnAlcance(proyecto1, misFichasIds, misProgramas)
      || estaEnAlcance(proyecto2, misFichasIds, misProgramas))
  }, [esInstructor, similitud, cargandoFichas, proyecto1, proyecto2, misFichasIds, misProgramas])

  if (!similitud) {
    return (
      <EmptyState
        icon={<MagnifyingGlass />}
        title="Similitud no encontrada"
        message="El análisis de similitud que buscas no existe o fue eliminado."
        actionLabel={`Volver a ${ruta.label.toLowerCase()}`}
        onAction={() => navigate(ruta.volver)}
      />
    )
  }

  if (noAutorizado) {
    return (
      <EmptyState
        icon={<MagnifyingGlass />}
        title="Similitud no autorizada"
        message="No tienes acceso a esta similitud porque no pertenece a tu programa. Solo puedes ver similitudes de propuestas del mismo programa que tus fichas."
        actionLabel={`Volver a ${ruta.label.toLowerCase()}`}
        onAction={() => navigate(ruta.volver)}
      />
    )
  }

  const pct = Math.round(Number(similitud.porcentaje) || 0)

  // Ver proyecto: propio de la ficha o mismo programa exacto (ADSO solo con ADSO, etc.)
  function puedeVerProyecto(pid) {
    if (!esInstructor) return true
    return estaEnAlcance(proyectosPorId.get(Number(pid)), misFichasIds, misProgramas)
  }

  function autorDe(p) {
    const nombre = nombreCompleto(p?.creator)
    return nombre || p?.classGroup?.program?.nombre || '—'
  }

  const crumbPrevio =
    role === 'admin'
      ? [
          { label: 'Dashboard', to: '/admin/dashboard' },
          { label: 'Similitudes', to: '/admin/similitudes' },
        ]
      : [{ label: ruta.label, to: ruta.volver }]

  const subtitulo = propuestaA && propuestaB
    ? `Detectada el ${fechaDesdeApi(similitud.fecha)} · ${propuestaA.titulo} vs. ${propuestaB.titulo}`
    : `Detectada el ${fechaDesdeApi(similitud.fecha)}`

  // Campos reales disponibles en la propuesta (sin inventar tecnologías).
  const listaClaves = (valor) => {
    if (!valor) return []
    if (Array.isArray(valor)) return valor.filter(Boolean)
    return String(valor).split(',').map((x) => x.trim()).filter(Boolean)
  }
  const objetivosDe = (p) => (Array.isArray(p?.objetivos_especificos) ? p.objetivos_especificos.filter(Boolean) : [])
  const fichaDe = (p) => {
    if (!p?.classGroup) return ''
    // Sin `codigo`: es la credencial para unirse a una ficha y no debe exponerse.
    return p.classGroup.nombre || ''
  }

  const detalles = similitud.detalles || null
  const terminos = Array.isArray(detalles?.terminos) ? detalles.terminos : []
  const pasajes = Array.isArray(detalles?.pasajes) ? detalles.pasajes : []

  // Tarjeta de una propuesta con los campos reales del modelo.
  const tarjetaProyecto = (p, tag) => (
    <article className={s.card} key={tag} aria-label={`Proyecto ${tag}`}>
      <header className={s.cardHead}>
        <Tag variant={tag === 'A' ? 'a' : 'b'}>Proyecto {tag}</Tag>
        {p?.estado && (
          <Badge variant={ESTADO_PROYECTO_VARIANT[p.estado] || 'neutral'}>
            {ESTADO_PROYECTO_LABEL[p.estado] || p.estado}
          </Badge>
        )}
        {p && esMia(p) && <span className={s.tuya}>Tu propuesta</span>}
        {p && <span className={`mono ${s.cardId}`}>#{p.id}</span>}
      </header>
      {p ? (
        <>
          <h2 className={s.cardTitle}>{p.titulo}</h2>
          <p className={s.cardMeta}>
            <User size={14} /> {autorDe(p)}
            <span className={s.dot}>·</span>
            <CalendarBlank size={14} /> {fechaDesdeApi(p.created_at)}
            {fichaDe(p) && (
              <>
                <span className={s.dot}>·</span>
                <Books size={14} /> {fichaDe(p)}
              </>
            )}
          </p>

          {p.resumen && <p className={s.cardResumen}>{p.resumen}</p>}

          {puedeVerProyecto(p.id) ? (
            <Link to={`/${role}${projectPath}/${p.id}`} className={s.link}>
              Ver proyecto <ArrowRight size={14} />
            </Link>
          ) : (
            <span className={s.muted}>Propuesta de otra ficha</span>
          )}
        </>
      ) : (
        <p className={s.muted}>Este proyecto ya no está disponible.</p>
      )}
    </article>
  )

  return (
    <div className={s.wrapper}>
      <PageHeader
        title="Comparación de similitudes"
        subtitle={subtitulo}
        icon={<MagnifyingGlass />}
        breadcrumb={[...crumbPrevio, { label: `#${similitud.id}` }]}
      />

      <SimilarityGraph porcentaje={pct} etiqueta="Índice de similitud" />
      <p className={s.note}>
        Medida calculada por el motor de ProyecTwin. No es una prueba de plagio ni un veredicto definitivo.
      </p>

      <div className={s.cardsRow}>
        {tarjetaProyecto(propuestaA, 'A')}
        {tarjetaProyecto(propuestaB, 'B')}
      </div>

      <DataPanel title="Detalle de las propuestas" icon={<FileText />}>
        <div className={s.detailsGrid}>
          {[['A', propuestaA], ['B', propuestaB]].map(([tag, p]) => (
            <section key={tag} className={s.detalle} aria-label={`Detalles del proyecto ${tag}`}>
              <header className={s.detalleHead}>
                <Tag variant={tag === 'A' ? 'a' : 'b'}>Proyecto {tag}</Tag>
                <h3 className={s.detalleTitulo}>{p?.titulo || 'Proyecto no disponible'}</h3>
              </header>
              {p ? (
                <dl className={s.fields}>
                  {p.resumen && (
                    <div className={s.field}>
                      <dt>Resumen</dt>
                      <dd>{p.resumen}</dd>
                    </div>
                  )}
                  {p.objetivo_general && (
                    <div className={s.field}>
                      <dt>Objetivo general</dt>
                      <dd>{p.objetivo_general}</dd>
                    </div>
                  )}
                  {objetivosDe(p).length > 0 && (
                    <div className={s.field}>
                      <dt>Objetivos específicos</dt>
                      <dd>
                        <ul className={s.lista}>
                          {objetivosDe(p).map((objetivo, i) => (
                            <li key={`${tag}-obj-${i}`}>{objetivo}</li>
                          ))}
                        </ul>
                      </dd>
                    </div>
                  )}
                  {listaClaves(p.palabras_clave).length > 0 && (
                    <div className={s.field}>
                      <dt>Palabras clave</dt>
                      <dd className={s.tags}>
                        {listaClaves(p.palabras_clave).map((clave, i) => (
                          <span key={`${tag}-kw-${i}`} className={s.kw}>{clave}</span>
                        ))}
                      </dd>
                    </div>
                  )}
                  {p.area_aplicacion && (
                    <div className={s.field}>
                      <dt>Área de aplicación</dt>
                      <dd>{p.area_aplicacion}</dd>
                    </div>
                  )}
                  {fichaDe(p) && (
                    <div className={s.field}>
                      <dt>Ficha</dt>
                      <dd>{fichaDe(p)}</dd>
                    </div>
                  )}
                </dl>
              ) : (
                <p className={s.muted}>Este proyecto ya no está disponible.</p>
              )}
            </section>
          ))}
        </div>
      </DataPanel>

      {(detalles || terminos.length > 0 || pasajes.length > 0) && (
        <DataPanel title="Coincidencias detectadas por el motor" icon={<MagnifyingGlass />}>
          <p className={s.detailsNote}>
            Resultado del análisis de texto de ProyecTwin (palabras, caracteres, tema y cobertura).
            No representa porcentajes por campo ni una prueba de plagio.
          </p>
          <dl className={s.metrics}>
            {detalles?.palabras != null && (
              <div className={s.metric}><dt>Palabras</dt><dd className="mono">{detalles.palabras}%</dd></div>
            )}
            {detalles?.caracteres != null && (
              <div className={s.metric}><dt>Caracteres</dt><dd className="mono">{detalles.caracteres}%</dd></div>
            )}
            {detalles?.tema != null && (
              <div className={s.metric}><dt>Tema</dt><dd className="mono">{detalles.tema}%</dd></div>
            )}
            {detalles?.cobertura != null && (
              <div className={s.metric}><dt>Cobertura</dt><dd className="mono">{detalles.cobertura}%</dd></div>
            )}
          </dl>
          {terminos.length > 0 && (
            <div className={s.detailBlock}>
              <p className={s.detailTitle}>Términos compartidos ({terminos.length})</p>
              <div className={s.tags}>
                {terminos.map((termino, i) => (
                  <span key={`term-${i}`} className={s.kw}>{termino}</span>
                ))}
              </div>
            </div>
          )}
          {pasajes.length > 0 && (
            <div className={s.detailBlock}>
              <p className={s.detailTitle}>Fragmentos coincidentes ({pasajes.length})</p>
              <ul className={s.pasajes}>
                {pasajes.slice(0, 6).map((pasaje, i) => (
                  <li key={`pasaje-${i}`} className={s.pasaje}>
                    <p className={s.pasajeTexto}>{pasaje.a}</p>
                    <span className={s.pasajeSep} aria-hidden="true" />
                    <p className={s.pasajeTexto}>{pasaje.b}</p>
                    {pasaje.score != null && (
                      <span className={`mono ${s.pasajeScore}`}>{pasaje.score}%</span>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </DataPanel>
      )}

      {otras.length > 0 && (
        <DataPanel title={`Otras coincidencias relacionadas (${otras.length})`} icon={<MagnifyingGlass />}>
          <ul className={s.otrasList}>
            {otras.map((x) => {
              const pctX = Math.round(Number(x.porcentaje) || 0)
              const otro = proyectosPorId.get(Number(x.otroPid))
              return (
                <li key={x.id}>
                  <Link to={`/${role}/detalle-similitud/${x.id}`} className={s.otrasRow}>
                    <Tag variant={x.origen === 'A' ? 'a' : 'b'}>
                      Proyecto {x.origen}
                    </Tag>
                    <span className={s.otrasInfo}>
                      <span className={s.otrasTitle}>{otro?.titulo || 'Proyecto no disponible'}</span>
                      <span className={s.otrasMeta}>
                        <User size={12} /> {autorDe(otro)}
                      </span>
                    </span>
                    <GradeBadge score={pctX} size="sm" />
                    <ArrowRight size={14} className={s.otrasChevron} />
                  </Link>
                </li>
              )
            })}
          </ul>
        </DataPanel>
      )}

      {actions}
      {children}

      {backTo && (
        <div className={s.backRow}>
          <Link to={backTo} className={s.backLink}>
            <ArrowLeft size={14} /> {backLabel || 'Volver'}
          </Link>
        </div>
      )}
    </div>
  )
}

import { Link } from 'react-router-dom'
import StatusMark from '../StatusMark/StatusMark'
import { PROPUESTA_STATUS } from '../../constants/estadoStatus'
import Tag from '../Tag/Tag'
import s from './DetalleProyectoBase.module.css'
import { fechaDesdeApi, nombreCompleto } from '../../utils/helpers'
import { PROJECT_ESTADO_LABEL as ESTADO_LABEL } from '../../constants/badgeVariants'



/**
 * Contenido compartido "Información del proyecto" — usado por
 * DetalleProyecto (aprendiz), DetalleProyectoInstructor y DetalleProyectoAdmin.
 * Mantiene una sola fuente de verdad visual para coherencia entre roles.
 */
export default function InformacionProyecto({ proyecto, ficha, fichaHref }) {
  // Palabras clave: la API las guarda como texto separado por comas.
  const keywords = (Array.isArray(proyecto.palabras_clave)
    ? proyecto.palabras_clave
    : String(proyecto.palabras_clave || '').split(','))
    .map((k) => String(k).trim())
    .filter(Boolean)

  // Objetivos específicos: la API los expone como array (cast) o texto.
  const objetivosEsp = (Array.isArray(proyecto.objetivos_especificos)
    ? proyecto.objetivos_especificos
    : String(proyecto.objetivos_especificos || '').split('\n'))
    .map((o) => String(o).trim())
    .filter(Boolean)

  // Relaciones incluidas por la API (instructor, aprendiz, classGroup).
  const instructor = proyecto.instructor?.generalUser
    ? nombreCompleto(proyecto.instructor.generalUser)
    : null
  // Integrantes = creador + equipo (pivote), sin duplicados ni vacíos.
  // El pivote ya incluye al creador, pero se refuerza por si hay datos previos.
  const creador = nombreCompleto(proyecto.creator)
  const nombres = [
    creador,
    ...(proyecto.apprentices || []).map((a) => nombreCompleto(a.generalUser)),
  ].map((n) => String(n || '').trim()).filter(Boolean)
  const integrantes = [...new Set(nombres)]
  const integrantesTexto = integrantes.length ? integrantes.join(', ') : '—'

  // Ficha: preferimos la relación completa; si no, el classGroup del proyecto.
  const fichaInfo = ficha || proyecto.classGroup || null
  // Sin `codigo`: la ficha puede ser ajena o anterior y el código es la
  // credencial de unión; en el detalle de la propuesta basta el nombre.
  const fichaTexto = fichaInfo
    ? (fichaInfo.nombre || '—')
    : `#${proyecto.id_class_group || '—'}`

  return (
    <>
      <div className={s.badgeRow}>
        <StatusMark status={PROPUESTA_STATUS[proyecto.estado] || 'pending'} label={ESTADO_LABEL[proyecto.estado] || proyecto.estado} />
        {proyecto.area_aplicacion && <Tag variant="info">{proyecto.area_aplicacion}</Tag>}
      </div>

      <dl className={s.detailList}>
        <div className={s.detailRow}>
          <dt>Fecha de creación</dt>
          <dd>{fechaDesdeApi(proyecto.created_at)}</dd>
        </div>
        <div className={s.detailRow}>
          <dt>Instructor</dt>
          <dd>{instructor || '—'}</dd>
        </div>
        <div className={s.detailRow}>
          <dt>Ficha</dt>
          <dd>
            {fichaHref && fichaInfo ? (
              <Link to={fichaHref} className={s.link}>
                {fichaTexto}
              </Link>
            ) : (
              fichaTexto
            )}
          </dd>
        </div>
        <div className={s.detailRow}>
          <dt>Integrantes</dt>
          <dd>{integrantesTexto}</dd>
        </div>
      </dl>

      <h3 className={s.subTitle}>Descripción</h3>
      <p className={s.paragraph}>{proyecto.resumen}</p>

      {proyecto.objetivo_general || objetivosEsp.length > 0 ? (
        <>
          <h3 className={s.subTitle}>Objetivo general</h3>
          <p className={s.paragraph}>{proyecto.objetivo_general || 'Sin definir.'}</p>
          {objetivosEsp.length > 0 && (
            <>
              <h3 className={s.subTitle}>Objetivos específicos</h3>
              <ol className={s.objList}>
                {objetivosEsp.map((o) => (
                  <li key={o}>{o}</li>
                ))}
              </ol>
            </>
          )}
        </>
      ) : null}

      {keywords.length > 0 && (
        <>
          <h3 className={s.subTitle}>Palabras clave</h3>
          <div className={s.chips}>
            {keywords.map((k) => (
              <Tag key={k} variant="success">
                {k}
              </Tag>
            ))}
          </div>
        </>
      )}
    </>
  )
}

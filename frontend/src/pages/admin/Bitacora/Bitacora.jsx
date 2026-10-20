import { useState } from 'react'
import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import PageHeader from '../../../components/PageHeader/PageHeader'
import FilterBar from '../../../components/FilterBar/FilterBar'
import FormField from '../../../components/FormField/FormField'
import { Input, Select } from '../../../components/Input/Input'
import DataTable from '../../../components/DataTable/DataTable'
import Badge from '../../../components/Badge/Badge'
import ApiState from '../../../components/ApiState/ApiState'
import Pagination from '../../../components/Pagination/Pagination'
import Button from '../../../components/Button/Button'
import { useApi } from '../../../lib/useApi'
import { useDebounced } from '../../../lib/useDebounced'
import { auditoria, usuarios } from '../../../lib/recursos'
import { fechaHoraDesdeApi, nombreCompleto } from '../../../utils/helpers'
import { ROL_LABEL } from '../../../constants/badgeVariants'
import { ClockCounterClockwise, MagnifyingGlass } from 'phosphor-react'
import s from './Bitacora.module.css'

const ACCIONES = [
  ['', 'Todas'],
  ['crear_usuario', 'Crear usuario'],
  ['actualizar_usuario', 'Actualizar usuario'],
  ['eliminar_usuario', 'Eliminar usuario'],
  ['reenviar_credenciales', 'Reenviar credenciales'],
  ['restablecer_credenciales', 'Restablecer credenciales'],
  ['exportar_credenciales', 'Exportar credenciales'],
  ['cambiar_correo', 'Cambiar correo'],
  ['cambiar_clave', 'Cambiar contraseña'],
  ['solicitar_reset', 'Solicitar recuperación'],
  ['restablecer_clave', 'Restablecer contraseña'],
  ['crear_propuesta', 'Crear propuesta'],
  ['enviar_propuesta', 'Enviar propuesta'],
  ['revisar_propuesta', 'Revisar propuesta'],
  ['eliminar_propuesta', 'Eliminar propuesta'],
  ['crear_ficha', 'Crear ficha'],
  ['actualizar_ficha', 'Actualizar ficha'],
  ['finalizar_ficha', 'Finalizar ficha'],
  ['anular_ficha', 'Anular ficha'],
  ['reasignar_ficha', 'Reasignar ficha'],
  ['eliminar_ficha', 'Eliminar ficha'],
  ['agregar_aprendiz', 'Agregar aprendiz'],
  ['asignar_aprendices', 'Asignar aprendices'],
  ['crear_aprendiz', 'Crear aprendiz'],
  ['actualizar_aprendiz', 'Actualizar aprendiz'],
  ['eliminar_aprendiz', 'Eliminar aprendiz'],
  ['actualizar_instructor', 'Actualizar instructor'],
  ['eliminar_instructor', 'Eliminar instructor'],
  ['config_motor', 'Configurar motor'],
  ['recalibrar_motor', 'Recalibrar motor'],
  ['resolver_soporte', 'Resolver soporte'],
  ['eliminar_red', 'Eliminar red'],
  ['eliminar_programa', 'Eliminar programa'],
]

const ENTIDADES = [
  ['', 'Todas'],
  ['general_users', 'Usuarios'],
  ['projects', 'Propuestas'],
  ['class_groups', 'Fichas'],
  ['similarities', 'Similitudes'],
  ['motor_configs', 'Motor'],
  ['apprentices', 'Aprendices'],
  ['instructors', 'Instructores'],
  ['bug_reports', 'Soporte'],
  ['knowledge_networks', 'Redes'],
  ['training_programs', 'Programas'],
]

const ACCION_LABEL = Object.fromEntries(ACCIONES.filter(([v]) => v))
const TAMANOS = [10, 15, 25, 50]

function nombreUsuario(u) {
  // El backend oculta `username`: el responsable se identifica por nombre y
  // apellido. No se usa el correo como alternativa visible.
  return u ? nombreCompleto(u, 'Usuario sin nombre') : 'Sistema'
}

function valorTexto(valor) {
  if (valor === null || valor === undefined) return '—'
  if (Array.isArray(valor)) return valor.join(', ')
  if (typeof valor === 'object') return JSON.stringify(valor)
  return String(valor)
}

function detalleResumen(detalle) {
  if (!detalle || typeof detalle !== 'object') return '—'
  const texto = Object.entries(detalle)
    .map(([k, v]) => `${k}: ${valorTexto(v)}`)
    .join(' · ')
  return texto.length > 110 ? `${texto.slice(0, 110)}…` : texto || '—'
}

function DetalleLog({ log }) {
  const campos = [
    ['Registro', `#${log.id}`],
    ['Fecha y hora', fechaHoraDesdeApi(log.created_at)],
    ['Responsable (nombre y apellido)', nombreUsuario(log.user)],
    ['Rol', ROL_LABEL[log.user?.rol] || log.user?.rol || '—'],
    ['Acción', ACCION_LABEL[log.accion] || log.accion],
    ['Entidad', log.entidad ? `${log.entidad}${log.entidad_id ? ` #${log.entidad_id}` : ''}` : '—'],
    ['IP', log.ip || '—'],
  ]

  return (
    <div className={s.expand}>
      <dl className={s.expandGrid}>
        {campos.map(([etiqueta, valor]) => (
          <div key={etiqueta} className={s.expandItem}>
            <dt>{etiqueta}</dt>
            <dd>{valor}</dd>
          </div>
        ))}
      </dl>
      {log.detalle && typeof log.detalle === 'object' && (
        <div className={s.expandDetalle}>
          <p className={s.expandTitulo}>Detalle del evento</p>
          <dl className={s.detalleList}>
            {Object.entries(log.detalle).map(([clave, valor]) => (
              <div key={clave} className={s.detalleItem}>
                <dt>{clave}</dt>
                <dd>{valorTexto(valor)}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </div>
  )
}

export default function Bitacora() {
  const [buscar, setBuscar] = useState('')
  const [accion, setAccion] = useState('')
  const [entidad, setEntidad] = useState('')
  const [usuario, setUsuario] = useState('')
  const [rol, setRol] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [orden, setOrden] = useState('recientes')
  const [porPagina, setPorPagina] = useState(15)
  const [pagina, setPagina] = useState(1)
  const [expandido, setExpandido] = useState(null)

  const busqueda = useDebounced(buscar, 300)

  // Catálogo de responsables para el filtro (panel solo de admin): proyección
  // ligera para no descargar correo ni la foto de toda la plantilla.
  const { data: usuariosLista } = useApi(() => usuarios.listar({ resumen: 1 }), [], { inicial: [] })

  // Paginación y filtros en el servidor: nunca se descarga toda la bitácora.
  const { data, cargando, error, recargar } = useApi(
    () => auditoria.listarPaginado({
      buscar: busqueda,
      accion,
      entidad,
      id_usuario: usuario,
      rol,
      desde,
      hasta,
      orden,
      por_pagina: porPagina,
      page: pagina,
    }),
    [busqueda, accion, entidad, usuario, rol, desde, hasta, orden, porPagina, pagina],
    { inicial: null }
  )

  const filas = data?.data || []
  const total = data?.total || 0

  const limpiarFiltros = () => {
    setBuscar('')
    setAccion('')
    setEntidad('')
    setUsuario('')
    setRol('')
    setDesde('')
    setHasta('')
    setOrden('recientes')
    setPagina(1)
    setExpandido(null)
  }

  const hayFiltros = Boolean(buscar || accion || entidad || usuario || rol || desde || hasta)

  const columnas = [
    {
      key: 'created_at',
      header: 'Fecha y hora',
      render: (r) => <span className={s.fecha}>{fechaHoraDesdeApi(r.created_at)}</span>,
    },
    {
      key: 'usuario',
      header: 'Responsable',
      render: (r) => (
        <span className={s.userCell}>
          <span>{nombreUsuario(r.user)}</span>
          {r.user?.rol ? <Badge variant="neutral">{ROL_LABEL[r.user.rol] || r.user.rol}</Badge> : null}
        </span>
      ),
    },
    {
      key: 'accion',
      header: 'Acción',
      render: (r) => <Badge variant="neutral">{ACCION_LABEL[r.accion] || r.accion}</Badge>,
    },
    {
      key: 'entidad',
      header: 'Entidad',
      render: (r) => (r.entidad ? `${r.entidad}${r.entidad_id ? ` #${r.entidad_id}` : ''}` : '—'),
    },
    {
      key: 'detalle',
      header: 'Detalle',
      render: (r) => <span className={s.detalleCell}>{detalleResumen(r.detalle)}</span>,
    },
    {
      key: 'expandir',
      header: '',
      align: 'end',
      render: (r) => (
        <Button
          type="button"
          size="sm"
          variant="secondary"
          onClick={() => setExpandido(expandido === r.id ? null : r.id)}
          aria-expanded={expandido === r.id}
          aria-label={expandido === r.id ? `Ocultar detalle del registro ${r.id}` : `Ver detalle del registro ${r.id}`}
        >
          {expandido === r.id ? 'Ocultar' : 'Detalle'}
        </Button>
      ),
    },
  ]

  return (
    <DashboardLayout role="admin" titulo="Bitácora">
      <PageHeader
        title="Bitácora de acciones"
        subtitle="Registro inmutable de las acciones sensibles (quién, qué y cuándo)."
        icon={<ClockCounterClockwise />}
        breadcrumb={[
          { label: 'Dashboard', to: '/admin/dashboard' },
          { label: 'Bitácora' },
        ]}
      />

      <FilterBar
        title="Filtrar bitácora"
        actions={hayFiltros && (
          <Button type="button" variant="secondary" size="sm" onClick={limpiarFiltros}>
            Limpiar filtros
          </Button>
        )}
      >
        <FormField label="Buscar">
          <span className={s.searchBox}>
            <MagnifyingGlass size={16} aria-hidden="true" />
            <Input
              className={s.searchInput}
              value={buscar}
              onChange={(e) => { setBuscar(e.target.value); setPagina(1) }}
              placeholder="Acción, entidad, detalle o responsable…"
            />
          </span>
        </FormField>
        <FormField label="Responsable">
          <Select value={usuario} onChange={(e) => { setUsuario(e.target.value); setPagina(1) }}>
            <option value="">Todos</option>
            {(usuariosLista || []).map((u) => (
              <option key={u.id} value={String(u.id)}>{nombreUsuario(u)}</option>
            ))}
          </Select>
        </FormField>
        <FormField label="Rol">
          <Select value={rol} onChange={(e) => { setRol(e.target.value); setPagina(1) }}>
            <option value="">Todos</option>
            <option value="aprendiz">Aprendiz</option>
            <option value="instructor">Instructor</option>
            <option value="admin">Administrador</option>
          </Select>
        </FormField>
        <FormField label="Acción">
          <Select value={accion} onChange={(e) => { setAccion(e.target.value); setPagina(1) }}>
            {ACCIONES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </Select>
        </FormField>
        <FormField label="Entidad">
          <Select value={entidad} onChange={(e) => { setEntidad(e.target.value); setPagina(1) }}>
            {ENTIDADES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </Select>
        </FormField>
        <FormField label="Desde">
          <Input type="date" value={desde} onChange={(e) => { setDesde(e.target.value); setPagina(1) }} />
        </FormField>
        <FormField label="Hasta">
          <Input type="date" value={hasta} onChange={(e) => { setHasta(e.target.value); setPagina(1) }} />
        </FormField>
        <FormField label="Orden">
          <Select value={orden} onChange={(e) => { setOrden(e.target.value); setPagina(1) }}>
            <option value="recientes">Más recientes primero</option>
            <option value="antiguos">Más antiguos primero</option>
          </Select>
        </FormField>
        <FormField label="Por página">
          <Select value={porPagina} onChange={(e) => { setPorPagina(Number(e.target.value)); setPagina(1) }}>
            {TAMANOS.map((n) => <option key={n} value={n}>{n} registros</option>)}
          </Select>
        </FormField>
      </FilterBar>

      <p className={s.resultado} aria-live="polite">
        {cargando ? 'Consultando…' : `${total} registro${total !== 1 ? 's' : ''} encontrado${total !== 1 ? 's' : ''}`}
      </p>

      <ApiState cargando={cargando} error={error} onReintentar={recargar}>
        <DataTable
          ariaLabel="Bitácora de acciones"
          columns={columnas}
          rows={filas}
          keyOf={(r) => r.id}
          empty={hayFiltros ? 'Ningún registro coincide con los filtros aplicados.' : 'Sin acciones registradas.'}
          stickyLast={false}
          expandRender={(r) => <DetalleLog log={r} />}
          expandido={expandido}
        />
        <Pagination
          totalItems={total}
          itemsPerPage={porPagina}
          paginaActual={pagina}
          setPaginaActual={setPagina}
          itemName="acciones"
        />
      </ApiState>
    </DashboardLayout>
  )
}

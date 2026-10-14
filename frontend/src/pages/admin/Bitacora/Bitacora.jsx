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
import { auditoria, usuarios } from '../../../lib/recursos'
import { fechaDesdeApi, nombreCompleto } from '../../../utils/helpers'
import { PAGINA_TABLA } from '../../../constants/pagination'
import { ClockCounterClockwise } from 'phosphor-react'

const ITEMS_POR_PAGINA = PAGINA_TABLA

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

function detalleTexto(detalle) {
  if (!detalle || typeof detalle !== 'object') return '—'
  return Object.entries(detalle)
    .map(([k, v]) => `${k}: ${Array.isArray(v) ? v.join(', ') : v}`)
    .join(' · ')
}

function nombreUsuario(u) {
  return u ? nombreCompleto(u, u.correo || 'Usuario') : 'Sistema'
}

export default function Bitacora() {
  const [accion, setAccion] = useState('')
  const [entidad, setEntidad] = useState('')
  const [usuario, setUsuario] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [pagina, setPagina] = useState(1)

  // Catálogo de responsables para el filtro (todos los usuarios).
  const { data: usuariosLista } = useApi(() => usuarios.listar(), [], { inicial: [] })

  const { data, cargando, error, recargar } = useApi(
    () => auditoria.listar({ accion, entidad, desde, hasta, id_usuario: usuario }),
    [accion, entidad, usuario, desde, hasta],
    { inicial: [] }
  )

  const filas = data || []
  const paginadas = filas.slice((pagina - 1) * ITEMS_POR_PAGINA, pagina * ITEMS_POR_PAGINA)

  const limpiarFiltros = () => {
    setAccion('')
    setEntidad('')
    setUsuario('')
    setDesde('')
    setHasta('')
    setPagina(1)
  }

  const hayFiltros = accion || entidad || usuario || desde || hasta

  const columnas = [
    {
      key: 'created_at',
      header: 'Fecha',
      render: (r) => fechaDesdeApi(r.created_at),
    },
    {
      key: 'usuario',
      header: 'Responsable',
      render: (r) => nombreUsuario(r.user),
    },
    {
      key: 'accion',
      header: 'Acción',
      render: (r) => <Badge variant="neutral">{r.accion}</Badge>,
    },
    {
      key: 'entidad',
      header: 'Entidad',
      render: (r) => (r.entidad ? `${r.entidad}${r.entidad_id ? ` #${r.entidad_id}` : ''}` : '—'),
    },
    {
      key: 'detalle',
      header: 'Detalle',
      render: (r) => detalleTexto(r.detalle),
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
        <FormField label="Responsable">
          <Select value={usuario} onChange={(e) => { setUsuario(e.target.value); setPagina(1) }}>
            <option value="">Todos</option>
            {(usuariosLista || []).map((u) => (
              <option key={u.id} value={String(u.id)}>{nombreUsuario(u)}</option>
            ))}
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
      </FilterBar>

      <ApiState cargando={cargando} error={error} onReintentar={recargar}>
        <DataTable
          ariaLabel="Bitácora de acciones"
          columns={columnas}
          rows={paginadas}
          keyOf={(r) => r.id}
          empty="Sin acciones registradas para este filtro."
          stickyLast={false}
        />
        <Pagination
          totalItems={filas.length}
          itemsPerPage={ITEMS_POR_PAGINA}
          paginaActual={pagina}
          setPaginaActual={setPagina}
          itemName="acciones"
          filteredCount={filas.length}
        />
      </ApiState>
    </DashboardLayout>
  )
}

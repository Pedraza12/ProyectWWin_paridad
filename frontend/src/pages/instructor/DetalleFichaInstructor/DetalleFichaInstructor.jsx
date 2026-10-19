import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import PageHeader from '../../../components/PageHeader/PageHeader'
import DataPanel from '../../../components/DataPanel/DataPanel'
import Avatar from '../../../components/Avatar/Avatar'
import Badge from '../../../components/Badge/Badge'
import EmptyState from '../../../components/EmptyState/EmptyState'
import Alert from '../../../components/Alert/Alert'
import ApiState from '../../../components/ApiState/ApiState'
import { PROJECT_ESTADO_VARIANT, PROJECT_ESTADO_LABEL as ESTADO_LABEL, FICHA_ESTADO_LABEL } from '../../../constants/badgeVariants'
import ConfirmModal from '../../../components/ConfirmModal/ConfirmModal'
import FormField from '../../../components/FormField/FormField'
import Actions from '../../../components/Actions/Actions'
import Button from '../../../components/Button/Button'
import { Input, Select } from '../../../components/Input/Input'
import { MAX_NOMBRE, MAX_NUMERO_FICHA, esNumeroFichaValido } from '../../../utils/validation'
import InformacionFicha from '../../../components/DetalleFichaBase/InformacionFicha'
import { useAuth } from '../../../contexts/AuthContext'
import { useApi } from '../../../lib/useApi'
import { fichas, instructores, proyectos } from '../../../lib/recursos'
import { fechaDesdeApi, nombreCompleto } from '../../../utils/helpers'
import s from '../../../components/DetalleFichaBase/DetalleFichaBase.module.css'
import { ArrowRight, Books, CalendarBlank, ChartBar, CheckCircle, FolderOpen, GraduationCap, IdentificationCard, LockKey, MagnifyingGlass, PencilLine, Trash, Users, Warning } from 'phosphor-react'



export default function DetalleFichaInstructor() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [editando, setEditando] = useState(false)
  const [form, setForm] = useState({ nombre: '', numero: '', estado: 'activo' })
  const [errores, setErrores] = useState({})
  const [modalEliminar, setModalEliminar] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [accionMsg, setAccionMsg] = useState('')
  const [avisoMsg, setAvisoMsg] = useState('')

  // Ficha con aprendices incluidos, propuestas y catálogo de instructores.
  const { data: ficha, cargando, error, recargar } = useApi(
    () => fichas.obtener(id),
    [id],
    { inicial: null }
  )
  const { data: todosProyectos } = useApi(() => proyectos.listar(), [], { inicial: [] })
  const { data: instructoresApi } = useApi(() => instructores.listar(), [], { inicial: [] })

  const miFila = useMemo(
    () => instructoresApi.find((i) => Number(i.id_usuario) === Number(user?.id)) || null,
    [instructoresApi, user?.id]
  )

  const estudiantes = ficha?.apprentices || []
  const proyectosFicha = useMemo(
    () => (ficha ? (todosProyectos || []).filter((p) => Number(p.id_class_group) === Number(ficha.id)) : []),
    [todosProyectos, ficha]
  )

  // Autorización: solo el instructor a cargo de la ficha.
  const autorizado = !!ficha && Number(ficha.instructor?.id) === Number(miFila?.id)

  if (cargando) {
    return (
      <DashboardLayout role="instructor" titulo="Detalle de Ficha">
        <div className={s.page}><ApiState cargando /></div>
      </DashboardLayout>
    )
  }

  if (error) {
    return (
      <DashboardLayout role="instructor" titulo="Detalle de Ficha">
        <div className={s.page}><ApiState error={error} onReintentar={recargar} /></div>
      </DashboardLayout>
    )
  }

  if (!ficha) {
    return (
      <DashboardLayout role="instructor" titulo="Detalle de Ficha">
        <div className={s.page}>
          <EmptyState
            icon={<MagnifyingGlass />}
            title="Ficha no encontrada"
            message="La ficha que buscas no existe o fue eliminada."
            actionLabel="Volver a fichas"
            onAction={() => navigate('/instructor/fichas')}
          />
        </div>
      </DashboardLayout>
    )
  }

  if (!autorizado) {
    return (
      <DashboardLayout role="instructor" titulo="Detalle de Ficha">
        <div className={s.page}>
          <EmptyState
            icon={<LockKey size={40} weight="light" />}
            title="Esta ficha no está a tu cargo"
            message="Pertenece a otro instructor. Solo puedes gestionar las fichas que tú creaste."
            actionLabel="Volver al dashboard"
            onAction={() => navigate('/instructor/dashboard')}
          />
        </div>
      </DashboardLayout>
    )
  }

  const onChange = (e) => {
    const { name, value } = e.target
    setForm((f) => ({ ...f, [name]: value }))
    setErrores((err) => ({ ...err, [name]: undefined }))
  }

  function iniciarEdicion() {
    setForm({ nombre: ficha.nombre, numero: ficha.numero || '', estado: ficha.estado || 'activo' })
    setErrores({})
    setEditando(true)
  }

  const guardarEdicion = async (e) => {
    e.preventDefault()
    const numero = form.numero.trim()
    if (!form.nombre.trim()) {
      setErrores({ nombre: 'El nombre es obligatorio.' })
      return
    }
    if (!numero) {
      setErrores({ numero: 'El número de ficha es obligatorio.' })
      return
    }
    if (!esNumeroFichaValido(numero)) {
      setErrores({ numero: 'Solo dígitos (4 a 8 caracteres).' })
      return
    }
    setGuardando(true)
    try {
      // Solo escalares: el PUT no debe reenviar relaciones (aprendices) que el
      // backend espera como ids de usuario.
      await fichas.actualizar(ficha.id, {
        nombre: form.nombre.trim(),
        numero,
        estado: form.estado,
        id_programa: ficha.id_programa,
        id_instructor: ficha.id_instructor,
      })
      await recargar()
      setEditando(false)
    } catch (err) {
      setErrores({ numero: err?.data?.message || 'No se pudo actualizar la ficha. Intenta de nuevo.' })
    } finally {
      setGuardando(false)
    }
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
      navigate('/instructor/fichas')
    } catch (err) {
      setModalEliminar(false)
      setAccionMsg(err?.data?.message || 'No se pudo eliminar la ficha.')
    }
  }

  return (
    <DashboardLayout role="instructor" titulo="Detalle de Ficha">
      <div className={s.page}>
        <PageHeader
          title={ficha.nombre}
          subtitle={`Código ${ficha.codigo} · N° ${ficha.numero} · ${ficha.program?.nombre || 'Sin programa'}`}
          icon={<Books />}
          breadcrumb={[
            { label: 'Dashboard', to: '/instructor/dashboard', icon: <ChartBar size={14} /> },
            { label: 'Fichas', to: '/instructor/fichas', icon: <Books size={14} /> },
            { label: ficha.nombre },
          ]}
        />

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
                onClick={() => (editando ? setEditando(false) : iniciarEdicion())}
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
          {!editando ? (
            <InformacionFicha
              ficha={ficha}
              estudiantesCount={estudiantes.length}
              proyectosCount={proyectosFicha.length}
              showDirectorioLink
              directorioTo={`/instructor/directorio-ficha/${ficha.id}`}
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
              <FormField label="Estado">
                <Select
                  name="estado"
                  value={form.estado}
                  onChange={onChange}
                >
                  <option value="activo">{FICHA_ESTADO_LABEL.activo}</option>
                  <option value="finalizado">{FICHA_ESTADO_LABEL.finalizado}</option>
                </Select>
              </FormField>
              <Actions form>
                <Button type="submit" disabled={guardando}>
                  <CheckCircle size={14} /> {guardando ? 'Guardando…' : 'Guardar cambios'}
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => {
                    setEditando(false)
                    setErrores({})
                  }}
                >
                  Cancelar
                </Button>
              </Actions>
            </form>
          )}
        </DataPanel>

        <DataPanel title={`Aprendices (${estudiantes.length})`} icon={<GraduationCap />}>
          {estudiantes.length === 0 ? (
            <EmptyState
              icon={<Users />}
              title="Sin aprendices"
              message="Los aprendices que se unan con el código de la ficha aparecerán aquí."
            />
          ) : (
            <ul className={s.studentList}>
              {estudiantes.map((est) => {
                const g = est.generalUser || {}
                return (
                  <li key={est.id}>
                    <Link to={`/instructor/perfil-companero/${g.id}`} viewTransition className={s.studentRow}>
                      <Avatar name={nombreCompleto(g)} src={g.foto_url} size="md" />
                      <span className={s.studentInfo}>
                        <span className={s.studentName}>{nombreCompleto(g)}</span>
                        <span className={s.studentEmail}>{g.correo}</span>
                      </span>
                      <span className={s.arrow} aria-hidden="true"><ArrowRight size={22} /></span>
                    </Link>
                  </li>
                )
              })}
            </ul>
          )}
        </DataPanel>

        <DataPanel title={`Propuestas de la ficha (${proyectosFicha.length})`} icon={<FolderOpen />}>
          {proyectosFicha.length === 0 ? (
            <EmptyState
              icon={<FolderOpen />}
              title="Sin propuestas"
              message="Aún no hay propuestas registradas en esta ficha."
            />
          ) : (
            <ul className={s.studentList}>
              {proyectosFicha.map((p) => (
                <li key={p.id}>
                  <Link to={`/instructor/detalle-proyecto/${p.id}`} viewTransition className={s.studentRow}>
                    <span className={s.studentInfo}>
                      <span className={s.studentName}>{p.titulo}</span>
                      <span className={s.studentEmail}><CalendarBlank size={12} /> {fechaDesdeApi(p.created_at)}</span>
                    </span>
                    <Badge variant={PROJECT_ESTADO_VARIANT[p.estado] || 'neutral'}>
                      {ESTADO_LABEL[p.estado] || p.estado}
                    </Badge>
                    <span className={s.arrow} aria-hidden="true"><ArrowRight size={22} /></span>
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
        responsabilidad
        verificacion="ELIMINAR"
        onConfirmar={confirmarEliminar}
        onCancelar={() => setModalEliminar(false)}
      />
    </DashboardLayout>
  )
}

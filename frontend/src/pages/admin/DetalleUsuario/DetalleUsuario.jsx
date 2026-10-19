import { useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { FolderOpen, MagnifyingGlass, ChartBar, Users, PencilSimple, Prohibit, Key, Trash, CheckCircle, Warning, ArrowCounterClockwise, Camera } from 'phosphor-react'
import DashboardLayout from '../../../layouts/DashboardLayout/DashboardLayout'
import PerfilBase from '../../../components/PerfilBase/PerfilBase'
import Avatar from '../../../components/Avatar/Avatar'
import DataPanel from '../../../components/DataPanel/DataPanel'
import Badge from '../../../components/Badge/Badge'
import GradeBadge from '../../../components/GradeBadge/GradeBadge'
import Button from '../../../components/Button/Button'
import EmptyState from '../../../components/EmptyState/EmptyState'
import ConfirmModal from '../../../components/ConfirmModal/ConfirmModal'
import Alert from '../../../components/Alert/Alert'
import FormField from '../../../components/FormField/FormField'
import { Input, PasswordInput, Select } from '../../../components/Input/Input'
import Actions from '../../../components/Actions/Actions'
import ApiState from '../../../components/ApiState/ApiState'
import { useAuth } from '../../../contexts/AuthContext'
import { useApi } from '../../../lib/useApi'
import { usuarios, aprendices, fichas, proyectos, similitudes } from '../../../lib/recursos'
import { esEmailValido } from '../../../utils/validation'
import { procesarFoto } from '../../../utils/foto'
import { PROJECT_ESTADO_VARIANT, PROJECT_ESTADO_LABEL as PROYECTO_ESTADO_LABEL, ROL_LABEL } from '../../../constants/badgeVariants'
import { fechaDesdeApi, infoSimilitud, nombreCompleto, payloadCuenta } from '../../../utils/helpers'

import s from '../../../components/PersonaDetalleBase/PersonaDetalleBase.module.css'
import formStyles from '../../../components/FormularioBase/FormularioBase.module.css'


export default function DetalleUsuario() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user: sesion, sincronizarSesion } = useAuth()
  const [modalEliminar, setModalEliminar] = useState(false)
  const [modalRestablecer, setModalRestablecer] = useState(false)
  // Cambio del propio correo: exige la contraseña actual del admin.
  const [correoPendiente, setCorreoPendiente] = useState(null)
  const [correoPass, setCorreoPass] = useState('')
  const [correoError, setCorreoError] = useState('')
  const [guardandoCorreo, setGuardandoCorreo] = useState(false)

  // Edición
  const [editando, setEditando] = useState(false)
  const [form, setForm] = useState({
    name: '', apellido: '', tipoDocumento: 'CC', numeroDocumento: '',
    email: '', fichaId: '', role: 'aprendiz', fotoUrl: null,
  })
  const [errores, setErrores] = useState({})
  const [subiendoFoto, setSubiendoFoto] = useState(false)
  const [fotoError, setFotoError] = useState('')
  const fileRef = useRef(null)
  const [guardado, setGuardado] = useState(false)
  const [accionMsg, setAccionMsg] = useState(null)
  const [claveTemporal, setClaveTemporal] = useState(null)
  const [avisoCredenciales, setAvisoCredenciales] = useState('')
  const [accionOcupada, setAccionOcupada] = useState(false)
  const [filtroProp, setFiltroProp] = useState('todos')
  const [busquedaProp, setBusquedaProp] = useState('')

  // Fuente única: la API. Usuario + relaciones + propuestas/similitudes del aprendiz.
  const { data, cargando, error, recargar } = useApi(
    async () => {
      const [usuario, listaAprendices, listaFichas, listaProyectos, listaSimilitudes] = await Promise.all([
        usuarios.obtener(id),
        aprendices.listar('generalUser,classGroup.program'),
        fichas.listar('program,instructor', {}),
        proyectos.listar({ included: 'creator,classGroup.program,apprentices.generalUser' }),
        similitudes.listar(),
      ])
      return { usuario, listaAprendices, listaFichas, listaProyectos, listaSimilitudes }
    },
    [id],
    { inicial: null }
  )

  if (cargando || error || !data?.usuario) {
    return (
      <DashboardLayout role="admin" titulo="Detalle de Usuario">
        <div className={s.wrapper}>
          {error ? (
            <EmptyState
              icon={<MagnifyingGlass />}
              title="No se pudo cargar el usuario"
              message={error.message || 'Ocurrió un error al consultar la API.'}
              actionLabel="Reintentar"
              onAction={recargar}
            />
          ) : cargando ? (
            <ApiState cargando error={null} />
          ) : (
            <EmptyState
              icon={<MagnifyingGlass />}
              title="Usuario no encontrado"
              message="El usuario que buscas no existe o fue eliminado."
              actionLabel="Volver a usuarios"
              onAction={() => navigate('/admin/usuarios')}
            />
          )}
        </div>
      </DashboardLayout>
    )
  }

  const usuario = data.usuario
  const listaFichas = data.listaFichas || []
  const listaSimilitudes = data.listaSimilitudes || []
  const nombre = nombreCompleto(usuario)

  // Perfil de aprendiz (si aplica) y su ficha dentro del catálogo.
  const perfilAprendiz = (data.listaAprendices || []).find((a) => Number(a.id_usuario) === Number(usuario.id)) || null
  const ficha = perfilAprendiz?.classGroup
    || (perfilAprendiz?.id_class_group ? listaFichas.find((f) => Number(f.id) === Number(perfilAprendiz.id_class_group)) : null)
    || null

  // Propuestas del aprendiz: creador O integrante del equipo (misma regla que
  // usa el propio aprendiz en su dashboard, para que los conteos coincidan).
  const proyectosDelUsuario = usuario.rol === 'aprendiz'
    ? (data.listaProyectos || []).filter((p) =>
        Number(p.id_creador) === Number(usuario.id)
        || (p.apprentices || []).some((a) => Number(a.generalUser?.id) === Number(usuario.id))
      )
    : []

  const proyectosFiltrados = proyectosDelUsuario.filter((p) => {
    const q = busquedaProp.trim().toLowerCase()
    const coincideQ = !q || (p.titulo || '').toLowerCase().includes(q)
    const coincideEstado = filtroProp === 'todos' || p.estado === filtroProp
    return coincideQ && coincideEstado
  })

  // Máximo porcentaje y conteo de coincidencias de una propuesta.
  const similitudInfoDe = (projectId) => infoSimilitud(listaSimilitudes, projectId)

  const estado = usuario.estado === false ? 'suspendido' : 'activo'
  const esMiCuenta = Number(sesion?.id) === Number(usuario.id)

  // Estado del envío de credenciales temporales (solo mientras sean vigentes).
  const estadoCredenciales = usuario.must_change_password
    ? (usuario.credenciales_error
      ? 'Falló el envío'
      : (usuario.credenciales_enviadas_en ? 'Enviadas al correo' : 'Pendiente de envío'))
    : 'Contraseña ya establecida'

  const detalles =
    usuario.rol === 'aprendiz'
      ? [
          { label: 'Rol', value: ROL_LABEL[usuario.rol] || usuario.rol },
          { label: 'Estado', value: estado === 'suspendido' ? 'Suspendido' : 'Activo' },
          { label: 'Ficha', value: ficha ? `${ficha.nombre} (${ficha.codigo})` : 'Sin ficha asignada' },
          { label: 'Programa', value: perfilAprendiz?.program?.nombre || ficha?.program?.nombre || 'No asignado' },
          { label: 'Credenciales', value: estadoCredenciales },
        ]
      : [
          { label: 'Rol', value: ROL_LABEL[usuario.rol] || usuario.rol },
          { label: 'Estado', value: estado === 'suspendido' ? 'Suspendido' : 'Activo' },
          { label: 'Ficha', value: ficha ? `${ficha.nombre} (${ficha.codigo})` : 'Sin ficha asignada' },
          { label: 'Credenciales', value: estadoCredenciales },
        ]

  const confirmarEliminar = async () => {
    try {
      await usuarios.eliminar(usuario.id)
      navigate('/admin/usuarios')
    } catch (err) {
      setModalEliminar(false)
      setAccionMsg(err?.data?.message || 'No se pudo eliminar el usuario.')
    }
  }

  const iniciarEdicion = () => {
    setForm({
      name: usuario.nombre || '',
      apellido: usuario.apellido || '',
      tipoDocumento: usuario.tipo_documento || 'CC',
      numeroDocumento: usuario.numero_documento || '',
      email: usuario.correo || '',
      fichaId: perfilAprendiz?.id_class_group ? String(perfilAprendiz.id_class_group) : '',
      role: usuario.rol || 'aprendiz',
      fotoUrl: usuario.foto_url || null,
    })
    setFotoError('')
    setErrores({})
    setGuardado(false)
    setClaveTemporal(null)
    setAccionMsg(null)
    setEditando(true)
  }

  // Foto: se recorta/optimiza en el navegador y se guarda como data URL.
  const alElegirFoto = async (e) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setSubiendoFoto(true)
    setFotoError('')
    try {
      const dataUrl = await procesarFoto(file)
      setForm((f) => ({ ...f, fotoUrl: dataUrl }))
    } catch (err) {
      setFotoError(err.message || 'No fue posible procesar la imagen.')
    } finally {
      setSubiendoFoto(false)
    }
  }

  const quitarFoto = () => {
    setFotoError('')
    setForm((f) => ({ ...f, fotoUrl: null }))
  }

  const onChange = (e) => {
    const { name, value } = e.target
    setForm((f) => ({ ...f, [name]: value }))
    setErrores((err) => ({ ...err, [name]: undefined }))
  }

  const validar = () => {
    const err = {}
    if (!form.name.trim()) err.name = 'El nombre es obligatorio.'
    if (!form.apellido.trim()) err.apellido = 'El apellido es obligatorio.'
    if (!form.numeroDocumento.trim()) err.numeroDocumento = 'El número de documento es obligatorio.'
    if (esMiCuenta && form.role !== 'admin') {
      err.role = 'No puedes quitarte tu propio rol de administrador.'
    }
    if (!form.email.trim()) err.email = 'El correo es obligatorio.'
    else if (!esEmailValido(form.email.trim())) err.email = 'Ingresa un correo válido.'
    return err
  }

  const onSubmitEdicion = async (e) => {
    e.preventDefault()
    if (accionOcupada) return
    const err = validar()
    if (Object.keys(err).length) {
      setErrores(err)
      return
    }
    const nuevoEmail = form.email.trim().toLowerCase()
    // Cambiar el correo propio (usuario de acceso) exige la contraseña actual.
    if (esMiCuenta && nuevoEmail !== usuario.correo) {
      setCorreoPendiente(nuevoEmail)
      setCorreoPass('')
      setCorreoError('')
      return
    }
    await guardarCambios(nuevoEmail)
  }

  const confirmarCambioCorreo = async () => {
    if (!correoPass) {
      setCorreoError('Ingresa tu contraseña actual.')
      return
    }
    setGuardandoCorreo(true)
    setCorreoError('')
    try {
      await usuarios.cambiarCorreo(correoPendiente, correoPass)
      sincronizarSesion(null, correoPendiente)
      setCorreoPendiente(null)
      setCorreoPass('')
      await recargar()
      setEditando(false)
      setGuardado(true)
    } catch (err) {
      setCorreoError(err?.data?.message || 'No fue posible cambiar el correo.')
    } finally {
      setGuardandoCorreo(false)
    }
  }

  const guardarCambios = async (nuevoEmail) => {
    if (accionOcupada) return
    setAccionOcupada(true)
    try {
      // El correo propio nunca se cambia por aquí: solo con contraseña.
      const correoAEnviar = esMiCuenta ? usuario.correo : nuevoEmail
      const cambios = {
        nombre: form.name.trim(),
        apellido: form.apellido.trim(),
        correo: correoAEnviar,
        rol: form.role,
        tipo_documento: form.tipoDocumento,
        numero_documento: form.numeroDocumento.trim(),
      }
      // La foto solo viaja si cambió (evita reenviar el base64 sin motivo).
      if ((form.fotoUrl || null) !== (usuario.foto_url || null)) cambios.foto_url = form.fotoUrl
      await usuarios.actualizar(usuario.id, payloadCuenta(usuario, cambios))
      // 2) Ficha del aprendiz: solo si ya tiene perfil y cambió la selección.
      if (usuario.rol === 'aprendiz' && perfilAprendiz) {
        const actual = perfilAprendiz.id_class_group ? String(perfilAprendiz.id_class_group) : ''
        const destino = form.fichaId ? listaFichas.find((f) => Number(f.id) === Number(form.fichaId)) : null
        if (form.fichaId !== actual) {
          // '' = quitar ficha; con ficha destino se hereda su programa.
          await aprendices.actualizar(perfilAprendiz.id, {
            codigo: perfilAprendiz.codigo,
            id_class_group: destino ? Number(destino.id) : null,
            id_usuario: perfilAprendiz.id_usuario,
            // Sin ficha no hay programa (opción A: el programa se deriva).
            id_programa: destino ? destino.id_programa : null,
          })
        }
      }
      await recargar()
      setEditando(false)
      setGuardado(true)
    } catch (err2) {
      const campos = err2?.data?.errors || {}
      // El backend responde en inglés para `unique`; se traduce al mensaje de la UI.
      if (campos.correo || campos.email) {
        setErrores({ email: 'Ya existe un usuario con este correo.' })
      } else if (campos.numero_documento) {
        setErrores({ numeroDocumento: 'Ya existe un usuario con este número de documento.' })
      } else {
        setErrores({})
        setAccionMsg(err2?.data?.message || 'No se pudo actualizar el usuario.')
      }
    } finally {
      setAccionOcupada(false)
    }
  }

  const restablecerClave = async () => {
    if (accionOcupada) return
    setAccionOcupada(true)
    try {
      // La temporal la genera el servidor con el formato institucional único.
      const resp = await usuarios.restablecerCredenciales(usuario.id)
      setClaveTemporal(resp.credenciales.password_temporal)
      setGuardado(false)
      setAccionMsg(null)
      await recargar()
    } catch (err) {
      setAccionMsg(err?.data?.message || 'No se pudo restablecer la contraseña.')
    } finally {
      setAccionOcupada(false)
    }
  }

  const cambiarEstado = async (nuevo) => {
    if (accionOcupada) return
    setAccionMsg(null)
    setAccionOcupada(true)
    try {
      await usuarios.actualizar(usuario.id, payloadCuenta(usuario, { estado: nuevo }))
      await recargar()
    } catch (err) {
      setAccionMsg(err?.data?.message || 'No se pudo actualizar el estado.')
    } finally {
      setAccionOcupada(false)
    }
  }

  // Reenvía las credenciales temporales (solo mientras la contraseña no cambie).
  const reenviarCredenciales = async () => {
    if (accionOcupada) return
    setAccionMsg(null)
    setAvisoCredenciales('')
    setAccionOcupada(true)
    try {
      await usuarios.reenviarCredenciales(usuario.id)
      setAvisoCredenciales('Credenciales reenviadas al correo personal.')
      await recargar()
    } catch (err) {
      setAccionMsg(err?.data?.message || 'No se pudieron reenviar las credenciales.')
    } finally {
      setAccionOcupada(false)
    }
  }

  const fichasActivas = listaFichas.filter((f) => f.estado === 'activo')

  return (
    <DashboardLayout role="admin" titulo="Detalle de Usuario">
      <div className={s.wrapper}>
        <PerfilBase
          user={usuario}
          role={usuario.rol}
          soloLectura
          titulo={nombre}
          subtitulo={`Cuenta ${ROL_LABEL[usuario.rol] || usuario.rol}`}
          breadcrumb={[
            { label: 'Dashboard', to: '/admin/dashboard', icon: <ChartBar size={14} /> },
            { label: 'Usuarios', to: '/admin/usuarios', icon: <Users size={14} /> },
            { label: nombre },
          ]}
          detalles={detalles}
        />

        <div className={s.barraInspector}>
          <Actions align="start" wrap>
            <Button type="button" variant="secondary" onClick={iniciarEdicion}>
              <PencilSimple size={14} /> Editar
            </Button>
            {estado === 'suspendido' ? (
              <Button type="button" variant="secondary" disabled={accionOcupada} onClick={() => cambiarEstado(true)}>
                <CheckCircle size={14} /> Activar cuenta
              </Button>
            ) : (
              <Button
                type="button"
                variant="secondary"
                title={esMiCuenta ? 'No puedes suspender tu propia cuenta' : 'Suspender cuenta'}
                disabled={esMiCuenta || accionOcupada}
                onClick={() => cambiarEstado(false)}
              >
                <Prohibit size={14} /> Suspender
              </Button>
            )}
            {usuario.rol !== 'admin' && (
              <Button type="button" variant="secondary" disabled={accionOcupada} onClick={() => setModalRestablecer(true)}>
                <Key size={14} /> Restablecer contraseña
              </Button>
            )}
            {usuario.rol !== 'admin' && usuario.must_change_password && (
              <Button
                type="button"
                variant="secondary"
                title="Reenviar las credenciales temporales al correo personal"
                disabled={accionOcupada}
                onClick={reenviarCredenciales}
              >
                <ArrowCounterClockwise size={14} /> Reenviar credenciales
              </Button>
            )}
            <Button
              type="button"
              variant="dangerGhost"
              disabled={esMiCuenta}
              title={esMiCuenta ? 'No puedes eliminar tu propia cuenta' : undefined}
              onClick={() => setModalEliminar(true)}
            >
              <Trash size={14} /> Eliminar
            </Button>
          </Actions>
        </div>

        {claveTemporal && (
          <Alert>
            <Key size={14} /> Nueva contraseña temporal: <strong>{claveTemporal}</strong>. Compártela con el usuario por un canal seguro.
          </Alert>
        )}

        {avisoCredenciales && (
          <Alert><CheckCircle size={14} /> {avisoCredenciales}</Alert>
        )}

        {accionMsg && (
          <Alert variant="danger"><Warning size={14} /> {accionMsg}</Alert>
        )}

        {guardado && (
          <Alert><CheckCircle size={14} /> Usuario actualizado correctamente.</Alert>
        )}

        {editando && (
          <DataPanel title="Editar usuario" icon={<PencilSimple />}>
            <form className={formStyles.form} onSubmit={onSubmitEdicion} noValidate>
              <FormField
                label="Foto de perfil"
                error={fotoError}
                help="Se recorta y optimiza automáticamente (máx. 5 MB). Puedes retirar una foto inapropiada."
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
                  <Avatar
                    name={`${form.name} ${form.apellido}`.trim()}
                    src={form.fotoUrl || undefined}
                    size="md"
                  />
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => fileRef.current?.click()}
                    disabled={subiendoFoto}
                  >
                    <Camera size={14} /> {subiendoFoto ? 'Procesando…' : 'Cambiar foto'}
                  </Button>
                  {form.fotoUrl && (
                    <Button type="button" variant="secondary" onClick={quitarFoto} disabled={subiendoFoto}>
                      <Trash size={14} /> Quitar foto
                    </Button>
                  )}
                  <input
                    ref={fileRef}
                    type="file"
                    accept="image/*"
                    style={{ display: 'none' }}
                    onChange={alElegirFoto}
                  />
                </div>
              </FormField>
              <div className={formStyles.grid2}>
                <FormField label="Nombres" required error={errores.name}>
                  <Input name="name" value={form.name} onChange={onChange} placeholder="Nombres" />
                </FormField>
                <FormField label="Apellidos" required error={errores.apellido}>
                  <Input name="apellido" value={form.apellido} onChange={onChange} placeholder="Apellidos" />
                </FormField>
              </div>
              <div className={formStyles.grid2}>
                <FormField label="Tipo de documento" required>
                  <Select name="tipoDocumento" value={form.tipoDocumento} onChange={onChange}>
                    <option value="CC">Cédula de ciudadanía (CC)</option>
                    <option value="TI">Tarjeta de identidad (TI)</option>
                    <option value="CE">Cédula de extranjería (CE)</option>
                    <option value="PPT">Permiso por protección temporal (PPT)</option>
                  </Select>
                </FormField>
                <FormField label="Número de documento" required error={errores.numeroDocumento}>
                  <Input
                    name="numeroDocumento"
                    value={form.numeroDocumento}
                    onChange={onChange}
                    placeholder="Número de documento"
                    inputMode="numeric"
                  />
                </FormField>
              </div>
              <FormField label="Correo electrónico" required error={errores.email}>
                <Input
                  name="email"
                  type="email"
                  value={form.email}
                  onChange={onChange}
                  placeholder="Correo electrónico"
                />
              </FormField>
              <FormField
                label="Rol"
                required
                error={errores.role}
                help={esMiCuenta ? 'No puedes quitarte tu propio rol de administrador.' : 'Cambiar el rol ajusta los permisos de la cuenta.'}
              >
                <Select name="role" value={form.role} onChange={onChange} disabled={esMiCuenta}>
                  <option value="aprendiz">Aprendiz</option>
                  <option value="instructor">Instructor</option>
                  <option value="admin">Administrador</option>
                </Select>
              </FormField>
              {usuario.rol === 'aprendiz' && perfilAprendiz && (
                <FormField
                  label="Ficha"
                  help="Solo fichas activas. Las propuestas conservan su ficha original."
                >
                  <Select name="fichaId" value={form.fichaId} onChange={onChange}>
                    <option value="">Sin ficha</option>
                    {fichasActivas.map((f) => (
                      <option key={f.id} value={String(f.id)}>
                        {f.codigo} — {f.nombre}
                      </option>
                    ))}
                  </Select>
                </FormField>
              )}
              {/* Aviso de traslado: qué cambia al mover al aprendiz de ficha */}
              {usuario.rol === 'aprendiz' && perfilAprendiz && form.fichaId !== '' && (() => {
                const destino = listaFichas.find((f) => Number(f.id) === Number(form.fichaId))
                const actual = perfilAprendiz.id_class_group
                  ? listaFichas.find((f) => Number(f.id) === Number(perfilAprendiz.id_class_group))
                  : null
                if (!destino || (actual && Number(destino.id) === Number(actual.id))) return null
                const avisos = []
                if ((destino.program?.nombre || null) !== (actual?.program?.nombre || null)) {
                  avisos.push(`cambia de programa (${actual?.program?.nombre || '—'} → ${destino.program?.nombre || '—'})`)
                }
                if (avisos.length === 0) return null
                return <Alert>Este traslado {avisos.join(' · ')}.</Alert>
              })()}
              <Actions form>
                <Button type="submit" disabled={accionOcupada}><CheckCircle size={14} /> Guardar cambios</Button>
                <Button type="button" variant="secondary" onClick={() => setEditando(false)}>Cancelar</Button>
              </Actions>
            </form>
          </DataPanel>
        )}

        {usuario.rol === 'aprendiz' && (
          <DataPanel title={`Propuestas del aprendiz (${proyectosDelUsuario.length})`} icon={<FolderOpen />}>
            {proyectosDelUsuario.length > 0 && (
              <div className={formStyles.grid2} style={{ marginBottom: '1rem' }}>
                <FormField label="Buscar">
                  <Input
                    value={busquedaProp}
                    onChange={(e) => setBusquedaProp(e.target.value)}
                    placeholder="Título…"
                  />
                </FormField>
                <FormField label="Estado">
                  <Select value={filtroProp} onChange={(e) => setFiltroProp(e.target.value)}>
                    <option value="todos">Todos</option>
                    <option value="pendiente">{PROYECTO_ESTADO_LABEL.pendiente}</option>
                    <option value="aprobado">{PROYECTO_ESTADO_LABEL.aprobado}</option>
                    <option value="rechazado">{PROYECTO_ESTADO_LABEL.rechazado}</option>
                  </Select>
                </FormField>
              </div>
            )}
            {proyectosDelUsuario.length === 0 ? (
              <EmptyState
                icon={<FolderOpen />}
                title="Sin propuestas"
                message="Este aprendiz aún no ha registrado ninguna propuesta."
              />
            ) : proyectosFiltrados.length === 0 ? (
              <EmptyState
                icon={<FolderOpen />}
                title="Sin resultados"
                message="Ninguna propuesta coincide con el filtro aplicado."
              />
            ) : (
              <ul className={s.list}>
                {proyectosFiltrados.map((p) => {
                  const info = similitudInfoDe(p.id)
                  return (
                    <li key={p.id}>
                      <Link to={`/admin/detalle-proyecto/${p.id}`} className={s.row}>
                        <span className={s.rowInfo}>
                          <span className={s.rowTitle}>{p.titulo}</span>
                          <span className={s.rowMeta}>Enviado el {fechaDesdeApi(p.created_at)}</span>
                        </span>
                        {info && (
                          <span title={`${info.pct}% · ${info.count} coincidencia${info.count !== 1 ? 's' : ''}`}>
                            <GradeBadge score={info.pct} size="sm" />
                          </span>
                        )}
                        <Badge variant={PROJECT_ESTADO_VARIANT[p.estado] || 'neutral'}>
                          {PROYECTO_ESTADO_LABEL[p.estado] || p.estado}
                        </Badge>
                      </Link>
                    </li>
                  )
                })}
              </ul>
            )}
          </DataPanel>
        )}
      </div>

      <ConfirmModal
        open={!!modalEliminar}
        titulo="Eliminar usuario"
        mensaje={`¿Seguro que deseas eliminar a "${nombre}"? Se eliminarán su cuenta y todos sus registros asociados (propuestas, similitudes, observaciones, notificaciones y reportes). Esta acción no se puede deshacer.`}
        textoConfirmar="Sí, eliminar"
        textoCancelar="Cancelar"
        responsabilidad
        verificacion="ELIMINAR"
        onConfirmar={confirmarEliminar}
        onCancelar={() => setModalEliminar(false)}
      />

      <ConfirmModal
        open={modalRestablecer}
        titulo="Restablecer contraseña"
        mensaje={`Se generará una contraseña temporal para "${nombre}" y deberá cambiarla al ingresar. ¿Continuar?`}
        textoConfirmar="Sí, restablecer"
        textoCancelar="Cancelar"
        onConfirmar={async () => { await restablecerClave(); setModalRestablecer(false) }}
        onCancelar={() => setModalRestablecer(false)}
      />

      <ConfirmModal
        open={!!correoPendiente}
        titulo="Cambiar correo electrónico"
        mensaje={`Vas a cambiar tu correo de "${usuario.correo}" a "${correoPendiente}". Confirma tu identidad con la contraseña actual.`}
        textoConfirmar={guardandoCorreo ? 'Guardando…' : 'Cambiar correo'}
        textoCancelar="Cancelar"
        onConfirmar={confirmarCambioCorreo}
        onCancelar={() => setCorreoPendiente(null)}
      >
        <FormField label="Contraseña actual" required error={correoError}>
          <PasswordInput
            value={correoPass}
            onChange={(e) => { setCorreoPass(e.target.value); setCorreoError('') }}
            autoComplete="current-password"
          />
        </FormField>
      </ConfirmModal>
    </DashboardLayout>
  )
}

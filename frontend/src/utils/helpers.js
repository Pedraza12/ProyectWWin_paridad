export function iniciales(nombre) {
  return (nombre || '').split(' ').filter(Boolean).map((n) => n[0]).slice(0, 2).join('').toUpperCase()
}

// Normaliza para búsquedas: minúsculas + sin tildes (María === maria)
export function norm(texto) {
  return String(texto || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
}

export const MESES_CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']

export function formatearFecha(fecha) {
  if (!fecha) return ''
  const [d, m, a] = String(fecha).split('/')
  if (!d || !m || !a) return fecha
  return `${Number(d)} ${MESES_CORTOS[Number(m) - 1]} ${a}`
}

export function parseFecha(fecha) {
  const [d, m, a] = String(fecha || '').split('/').map(Number)
  if (!d || !m || !a) return null
  return new Date(a, m - 1, d)
}

// Formatea una fecha que viene de la API.
// - Si es "YYYY-MM-DD" (date-only) se interpreta como día LOCAL por componentes,
//   evitando el desfase de zona (new Date("2026-09-16") = medianoche UTC).
// - Si trae hora (ISO), se usa Date y se muestra en la zona del usuario.
export function fechaDesdeApi(valor) {
  if (!valor) return ''
  const texto = String(valor)
  const soloFecha = /^(\d{4})-(\d{2})-(\d{2})/.exec(texto)
  if (soloFecha) {
    const [, y, m, d] = soloFecha
    return formatearFecha(`${Number(d)}/${Number(m)}/${y}`)
  }
  const fecha = new Date(texto)
  if (Number.isNaN(fecha.getTime())) return texto
  return formatearFecha(`${fecha.getDate()}/${fecha.getMonth() + 1}/${fecha.getFullYear()}`)
}

// Fecha + hora uniforme (zona local) para bitácora y actividad reciente.
export function fechaHoraDesdeApi(valor) {
  if (!valor) return ''
  const fecha = new Date(valor)
  if (Number.isNaN(fecha.getTime())) return String(valor)
  return fecha.toLocaleString('es-CO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// Agrupa observaciones planas en hilos: [{ ...obsRaiz, respuestas: [...] }]
export function agruparObservaciones(lista) {
  const nodos = new Map(lista.map((o) => [o.id, { ...o, respuestas: [] }]))
  const raices = []
  lista.forEach((o) => {
    const nodo = nodos.get(o.id)
    const padre = o.respuestaA ? nodos.get(Number(o.respuestaA)) : undefined
    if (padre) padre.respuestas.push(nodo)
    else raices.push(nodo)
  })
  raices.forEach((r) => r.respuestas?.sort((a, b) => a.id - b.id))
  return raices
}

// Nombre visible de una persona (general_user o perfil con nombre/apellido).
export function nombreCompleto(usuario, alterno = '') {
  const nombre = [usuario?.nombre, usuario?.apellido].filter(Boolean).join(' ').trim()
  return nombre || alterno
}

// ¿La propuesta es del usuario? (creador o integrante del equipo).
export function esPropietarioProyecto(proyecto, userId) {
  if (!proyecto || !userId) return false
  if (Number(proyecto.id_creador) === Number(userId)) return true
  return (proyecto.apprentices || []).some(
    (a) => Number(a.generalUser?.id) === Number(userId) || Number(a.id_usuario) === Number(userId)
  )
}

// Máximo % y cantidad de pares de similitud de una propuesta.
export function infoSimilitud(lista, projectId) {
  const pares = (lista || []).filter(
    (x) => Number(x.id_proyecto_1) === Number(projectId) || Number(x.id_proyecto_2) === Number(projectId)
  )
  if (pares.length === 0) return null
  return {
    pct: Math.max(...pares.map((x) => Math.round(Number(x.porcentaje) || 0))),
    count: pares.length,
  }
}

// Campos escalares que acepta PUT /general-users (edición de cuentas).
export function payloadCuenta(cuenta, extra = {}) {
  return {
    nombre: cuenta.nombre,
    apellido: cuenta.apellido,
    correo: cuenta.correo,
    rol: cuenta.rol,
    estado: cuenta.estado,
    ...extra,
  }
}

// Payload mínimo del perfil propio: el correo, el rol, el estado y el
// documento tienen flujos o permisos separados. No acepta extras a propósito.
export function payloadPerfil(perfil = {}) {
  return {
    nombre: perfil.nombre,
    apellido: perfil.apellido,
  }
}



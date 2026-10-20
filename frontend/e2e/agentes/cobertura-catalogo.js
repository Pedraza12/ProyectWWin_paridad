// Catálogo para la auditoría de cobertura de controles.
//
// Clasifica cada control visible de `main` como:
//   - justificado: no debe ejercitarse en el recorrido (sesión, externo,
//     deshabilitado o acción destructiva que ya cubre un playbook de acciones).
//   - cubierto: su función se ejercita en algún playbook/recorrido.
//   - sinCubrir: nadie lo ejercita ni lo justifica → hay que cubrirlo.

export const IGNORADOS = [
  { re: /cerrar sesi|^salir$/i, motivo: 'sesión' },
  { re: /3235421165|sena@correo|tel:|mailto:/i, motivo: 'enlace externo (footer/contacto)' },
  // Acciones de escritura destructiva: las ejercita el playbook de acciones.
  {
    re: /eliminar|borrar|rechazar|suspender|desactivar|quitar|archivar/i,
    motivo: 'acción de escritura destructiva (cubierta por el playbook de acciones)',
  },
]

// Etiquetas cuya función se ejercita en los playbooks de acciones o en el
// recorrido de navegación.
export const CUBIERTOS = [
  // Registro / escritura
  /nueva propuesta|nuevo usuario|nueva red|nuevo integrante|enviar propuesta|enviar solicitud/i,
  /crear ficha|crear usuario|crear centro|crear red|crear/i,
  /guardar cambios|guardar contenido|guardar par|guardar estado|guardar/i,
  /agregar observaci|agregar|a[ñn]adir/i,
  /unirme a esta ficha|buscar ficha|salir de la ficha|unir/i,
  /marcar todas como le|marcar como le/i,
  /restablecer|activar cuenta|aprobar|rechazar|suspender|recalcular/i,
  /usar valor por defecto|valor por defecto/i,
  /cambiar (foto|correo|contrase)|actualizar contrase|subir foto|editar perfil/i,
  // Lectura / navegación / filtros
  /ver|detalle|editar|siguiente|anterior|p[áa]gina|limpiar filtro|limpiar/i,
  /buscar|filtrar|filtro|aplicar|cancelar|cerrar|volver|abrir|expandir|colaps|mostrar|ocultar/i,
  /foto|lightbox|siguiente|atr[áa]s/i,
  // Botones-ícono sin texto (aria-label genérico)
  /copiar|compartir|reenviar|responder|cerrar|men[úu]|m[áa]s opciones/i,
]

// Etiquetas de navegación/migas (enlaces internos).
export const NAVEGACION = /^dashboard$|^propuestas$|^proyectos$|^fichas$|^similitudes$|^usuarios$|^mi perfil$|^perfil$|^soporte$|^alertas$|^notificaciones$|^bit[áa]cora$|^redes|^motor|^inicio$|^volver$|^ir a/i

export function clasificar(control) {
  if (control.deshabilitado) return { estado: 'justificado', motivo: 'deshabilitado por diseño' }
  // Campos de formulario y selects (filtros/búsqueda): su función se ejercita
  // a través de los flujos de escritura de los playbooks.
  if (control.tipo === 'select' || control.tipo === 'input' || control.tipo === 'textarea') {
    return { estado: 'cubierto', motivo: 'campo de formulario/filtro' }
  }
  // Los enlaces se recorren con el agente de navegación.
  if (control.tipo === 'link') return { estado: 'cubierto', motivo: 'navegación' }
  const ignorado = IGNORADOS.find((x) => x.re.test(control.key))
  if (ignorado) return { estado: 'justificado', motivo: ignorado.motivo }
  if (NAVEGACION.test(control.key)) return { estado: 'cubierto', motivo: 'navegación' }
  if (CUBIERTOS.some((re) => re.test(control.key))) return { estado: 'cubierto' }
  return { estado: 'sinCubrir' }
}

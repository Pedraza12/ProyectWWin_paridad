// Rutas por rol. Fuente única para el destino post-login.

export const RUTA_POR_ROL = {
  aprendiz: '/aprendiz/dashboard',
  instructor: '/instructor/dashboard',
  admin: '/admin/dashboard',
}

// Alertas/notificaciones por rol. Fuente única para TopNav, Footer y DashboardLayout.
export const RUTA_ALERTAS_POR_ROL = {
  aprendiz: '/aprendiz/alertas',
  instructor: '/instructor/alertas',
  admin: '/admin/notificaciones',
}

// Reporte de fallas (soporte) por rol. El admin no tiene formulario propio: va al listado de reportes.
export const RUTA_SOPORTE_POR_ROL = {
  aprendiz: '/aprendiz/reportar-falla',
  instructor: '/instructor/reportar-falla',
  admin: '/admin/reportes-fallas',
}

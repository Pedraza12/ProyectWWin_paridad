import { describe, it, expect } from 'vitest'
import { RUTA_POR_ROL, RUTA_ALERTAS_POR_ROL, RUTA_SOPORTE_POR_ROL } from './routes'

describe('RUTA_POR_ROL', () => {
  it('fija el dashboard por rol', () => {
    expect(RUTA_POR_ROL).toEqual({
      aprendiz: '/aprendiz/dashboard',
      instructor: '/instructor/dashboard',
      admin: '/admin/dashboard',
    })
  })
})

describe('RUTA_ALERTAS_POR_ROL', () => {
  it('fija las alertas/notificaciones por rol', () => {
    expect(RUTA_ALERTAS_POR_ROL).toEqual({
      aprendiz: '/aprendiz/alertas',
      instructor: '/instructor/alertas',
      admin: '/admin/notificaciones',
    })
  })
})

describe('RUTA_SOPORTE_POR_ROL', () => {
  it('fija el reporte de fallas por rol', () => {
    expect(RUTA_SOPORTE_POR_ROL).toEqual({
      aprendiz: '/aprendiz/reportar-falla',
      instructor: '/instructor/reportar-falla',
      admin: '/admin/reportes-fallas',
    })
  })
})

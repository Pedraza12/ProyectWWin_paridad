import { describe, it, expect } from 'vitest'
import { iniciales, formatearFecha, parseFecha, agruparObservaciones, payloadCuenta, payloadPerfil } from './helpers'

describe('iniciales', () => {
  it('genera iniciales del primer apellido y nombre', () => {
    expect(iniciales('María González')).toBe('MG')
  })

  it('devuelve solo la inicial cuando hay un único nombre', () => {
    expect(iniciales('Carlos')).toBe('C')
  })

  it('ignora espacios extra y pasa a mayúsculas', () => {
    expect(iniciales('  juan   perez  ')).toBe('JP')
  })

  it('devuelve cadena vacía sin nombre', () => {
    expect(iniciales('')).toBe('')
    expect(iniciales(undefined)).toBe('')
  })
})

describe('formatearFecha', () => {
  it('formatea dd/mm/aaaa al formato d mes aaaa', () => {
    expect(formatearFecha('10/11/2026')).toBe('10 nov 2026')
  })

  it('devuelve la entrada sin cambios si no es una fecha válida', () => {
    expect(formatearFecha('')).toBe('')
    expect(formatearFecha(undefined)).toBe('')
  })
})

describe('parseFecha', () => {
  it('convierte dd/mm/aaaa en objeto Date', () => {
    const d = parseFecha('15/3/2025')
    expect(d).toBeInstanceOf(Date)
    expect(d.getFullYear()).toBe(2025)
    expect(d.getMonth()).toBe(2)
    expect(d.getDate()).toBe(15)
  })

  it('devuelve null cuando falta algún componente', () => {
    expect(parseFecha('1/2')).toBeNull()
    expect(parseFecha(undefined)).toBeNull()
  })
})

describe('agruparObservaciones', () => {
  it('agrupa respuestas bajo su observación raíz', () => {
    const lista = [
      { id: 1, texto: 'raiz', respuestaA: null },
      { id: 2, texto: 'respuesta', respuestaA: 1 },
    ]
    const resultado = agruparObservaciones(lista)
    expect(resultado).toHaveLength(1)
    expect(resultado[0].id).toBe(1)
    expect(resultado[0].respuestas.map((r) => r.id)).toEqual([2])
  })

  it('mantiene varias raíces y ordena sus respuestas', () => {
    const lista = [
      { id: 3, texto: 'b', respuestaA: 2 },
      { id: 2, texto: 'a', respuestaA: null },
      { id: 4, texto: 'c', respuestaA: 2 },
    ]
    const resultado = agruparObservaciones(lista)
    expect(resultado).toHaveLength(1)
    expect(resultado[0].respuestas.map((r) => r.id)).toEqual([3, 4])
  })
})

describe('payloadPerfil', () => {
  const perfil = {
    nombre: 'Ana',
    apellido: 'Pérez',
    correo: 'ana@test.local',
    rol: 'aprendiz',
    estado: true,
    tipo_documento: 'CC',
    numero_documento: '12345678',
    username: 'ana123',
    foto_url: 'data:image/jpeg;base64,abc',
  }

  it('devuelve exclusivamente nombre y apellido', () => {
    expect(payloadPerfil(perfil)).toEqual({
      nombre: 'Ana',
      apellido: 'Pérez',
    })
  })

  it('no propaga campos adicionales del perfil', () => {
    const resultado = payloadPerfil(perfil)

    expect(Object.keys(resultado)).toEqual(['nombre', 'apellido'])
    expect(resultado).not.toHaveProperty('correo')
    expect(resultado).not.toHaveProperty('rol')
    expect(resultado).not.toHaveProperty('estado')
    expect(resultado).not.toHaveProperty('tipo_documento')
    expect(resultado).not.toHaveProperty('numero_documento')
    expect(resultado).not.toHaveProperty('username')
    expect(resultado).not.toHaveProperty('foto_url')
  })
})

describe('payloadCuenta', () => {
  const cuenta = {
    nombre: 'Ana',
    apellido: 'Pérez',
    correo: 'ana@test.local',
    rol: 'aprendiz',
    estado: true,
  }

  it('conserva el contrato de edición administrativa', () => {
    expect(payloadCuenta(cuenta)).toEqual(cuenta)
    expect(payloadCuenta(cuenta, { estado: false, tipo_documento: 'CC' })).toEqual({
      ...cuenta,
      estado: false,
      tipo_documento: 'CC',
    })
  })
})

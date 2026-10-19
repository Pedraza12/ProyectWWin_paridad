import { execSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test, expect } from './helpers'

// Prueba permanente NO destructiva de la guarda de reseteo E2E.
//
// `php artisan e2e:reset` debe ABORTAR —sin tocar ninguna base de datos—
// cuando no se confirmó el entorno E2E autorizado. Estas invocaciones solo
// ejercitan las capas de guarda que terminan ANTES de cualquier operación
// sobre la BD (flags/entorno/nombre de base), por lo que la prueba es segura
// por construcción: cualquier camino que llegue a operar requiere que TODAS
// las guardas pasen, y aquí se fuerza que exactamente una falle.

const backend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'backend')

function ejecutarReset(env, args = ['--env=e2e']) {
  try {
    execSync(`php artisan e2e:reset ${args.join(' ')}`.trim(), {
      cwd: backend,
      stdio: 'pipe',
      timeout: 60000,
      env,
    })
    return { codigo: 0, salida: '' }
  } catch (err) {
    return {
      codigo: err.status ?? -1,
      salida: `${err.stdout || ''}${err.stderr || ''}`.toString(),
    }
  }
}

test('e2e:reset sin E2E_ALLOW_RESET aborta sin operar sobre ninguna base', () => {
  const env = { ...process.env }
  delete env.E2E_ALLOW_RESET

  const { codigo, salida } = ejecutarReset(env)

  expect(codigo).not.toBe(0)
  expect(salida).toMatch(/Falta autorización explícita/)
  expect(salida).toMatch(/ABORTADO: no se ejecutó ningún reset/)
})

test('e2e:reset sin --env=e2e aborta (no toca la configuración por defecto)', () => {
  // Se fuerza el escenario peligroso real: flag presente, pero entorno por
  // defecto (`local` → `.env` → BD de desarrollo). La guarda de entorno debe
  // cortar antes de cualquier operación.
  const env = { ...process.env, APP_ENV: 'local', E2E_ALLOW_RESET: '1' }

  const { codigo, salida } = ejecutarReset(env, [])

  expect(codigo).not.toBe(0)
  expect(salida).toMatch(/debe ser "e2e"/)
  expect(salida).toMatch(/ABORTADO: no se ejecutó ningún reset/)
})

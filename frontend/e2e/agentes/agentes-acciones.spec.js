// Agentes que EJECUTAN su rol (crear/editar/borrar) y verifican cada paso.
//
// Uso normal (recomendado):
//   npm run test:agentes:acciones   → un rol por invocación + reseed por rol
//
// Si se corre sin AGENTE_ROL, ejecuta los 4 roles (reseed entre cada uno).
import { execSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test, expect, login } from '../helpers'
import { crearEjecutor, escribirReporte } from './acciones/util'
import { PLAYBOOKS } from './acciones'

test.setTimeout(240000)

const ROL_ENV = globalThis.process?.env?.AGENTE_ROL
const ROLES = ROL_ENV ? [ROL_ENV] : ['aprendiz', 'instructor', 'admin']

const backend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..', 'backend')
function reseed() {
  // Reseed entre roles SOLO vía `php artisan e2e:reset --env=e2e`: el comando
  // aplica las guardas server-side (E2E_ALLOW_RESET, APP_ENV=e2e, BD exacta
  // `proyectwin_e2e`, loopback y lock) y ABORTA sin tocar nada si el entorno
  // no está autorizado. Nunca `migrate:fresh` directo: heredaría el `.env` por
  // defecto (BD de desarrollo) sin ninguna guarda.
  execSync('php artisan e2e:reset --env=e2e', { cwd: backend, stdio: 'pipe', timeout: 180000 })
}

// Sin AGENTE_ROL se corre todo en una sola invocación: se resiembra entre roles.
if (!ROL_ENV) {
  test.beforeEach(() => reseed())
}

for (const rol of ROLES) {
  test(`agente ${rol} ejecuta su rol (crear/editar/borrar)`, async ({ page }) => {
    const h = crearEjecutor(page, rol)
    await login(page, rol)
    await page.waitForTimeout(400)
    await PLAYBOOKS[rol](h)
    escribirReporte(rol, h)

    const detalle = h.problemas.map((p) => `- [${p.tipo}] ${p.url} :: ${p.detalle}`).join('\n')
    expect(
      h.problemas,
      `Agente ${rol}: ${h.problemas.length} problema(s):\n${detalle}`
    ).toEqual([])
  })
}

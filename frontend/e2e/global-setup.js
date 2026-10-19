// Reseed de la base E2E antes de cada corrida de Playwright (sprint final, Paso 1).
//
// SOLO resiembra `proyectwin_e2e`, a través de `php artisan e2e:reset --env=e2e`
// (guardas server-side: autorización E2E_ALLOW_RESET, APP_ENV=e2e, nombre de BD,
// host loopback y lock anti-concurrencia). Si la configuración no es segura,
// ABORTA: nunca se degrada a una advertencia ni se toca la base de desarrollo.
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { problemasEntornoE2E } from './guardas-e2e.mjs'

export default async function globalSetup() {
  const problemas = problemasEntornoE2E()

  if (problemas.length) {
    throw new Error(
      '[e2e] ABORTADO: entorno inseguro; no se tocó ninguna base de datos.\n' +
        problemas.map((p) => `  - ${p}`).join('\n')
    )
  }

  const backend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'backend')

  const r = spawnSync('php', ['artisan', 'e2e:reset', '--env=e2e'], {
    cwd: backend,
    stdio: 'pipe',
    shell: true,
    timeout: 180000,
    env: { ...process.env, E2E_ALLOW_RESET: '1' },
  })

  if (r.status !== 0) {
    const detalle = String(r.stderr || r.stdout || r.error?.message || '').slice(0, 600)
    throw new Error('[e2e] ABORTADO: el reseed E2E falló o fue rechazado por las guardas.\n' + detalle)
  }

  console.log('[e2e] base E2E resembrada (php artisan e2e:reset --env=e2e)')
}

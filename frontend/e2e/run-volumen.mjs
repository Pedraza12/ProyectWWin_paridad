// Runner de pruebas de VOLUMEN (sprint final, Paso 1: solo base E2E):
//   1) reseed base E2E + volumen  2) corre el spec (config sin globalSetup)
//   3) restaura el seed base E2E.
//
// El reseed SIEMPRE pasa por `php artisan e2e:reset --env=e2e`, que exige
// autorización (E2E_ALLOW_RESET=1), APP_ENV=e2e, base `proyectwin_e2e`, host
// loopback y lock anti-concurrencia. Cualquier otra configuración aborta sin
// tocar datos.
// Uso: npm run test:volumen
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { exigirEntornoE2E } from './guardas-e2e.mjs'

exigirEntornoE2E('volumen')

const here = path.dirname(fileURLToPath(import.meta.url))
const raiz = path.resolve(here, '..')
const backend = path.resolve(raiz, '..', 'backend')

function reseedE2E(...extra) {
  const args = ['artisan', 'e2e:reset', '--env=e2e', ...extra]
  const r = spawnSync('php', args, {
    cwd: backend,
    stdio: 'inherit',
    shell: true,
    timeout: 180000,
  })
  if (r.status !== 0) {
    console.error(`[volumen] ABORTADO: falló o fue rechazado ${args.join(' ')}`)
    process.exit(2)
  }
}

console.log('[volumen] reseteando base E2E + volumen…')
reseedE2E('--con-volumen')

console.log('[volumen] corriendo spec…')
const r = spawnSync(
  'npx',
  [
    'playwright',
    'test',
    'e2e/agentes/volumen.spec.js',
    '--config=playwright.volumen.config.js',
    '--reporter=line',
    '--workers=1',
  ],
  { cwd: raiz, stdio: 'inherit', shell: true }
)

console.log('[volumen] restaurando seed base E2E…')
reseedE2E()

process.exit(r.status === 0 ? 0 : 1)

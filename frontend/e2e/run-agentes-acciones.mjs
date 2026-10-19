// Corre los agentes de ACCIÓN uno por rol, sembrando la base antes de cada uno
// (vía globalSetup de Playwright) y restaurando el seed al terminar.
// Uso: npm run test:agentes:acciones
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { exigirEntornoE2E } from './guardas-e2e.mjs'

exigirEntornoE2E('agentes-acciones')

const here = path.dirname(fileURLToPath(import.meta.url))
const raiz = path.resolve(here, '..')
const backend = path.resolve(raiz, '..', 'backend')
const ROLES = ['aprendiz', 'instructor', 'admin']

function reseed(motivo) {
  // Reseed SOLO vía `php artisan e2e:reset --env=e2e` (guardas server-side:
  // autorización E2E_ALLOW_RESET, APP_ENV=e2e, nombre de BD y lock).
  console.log(`[agentes] reseed E2E (${motivo})…`)
  const r = spawnSync('php', ['artisan', 'e2e:reset', '--env=e2e'], {
    cwd: backend,
    stdio: 'inherit',
    shell: true,
    timeout: 180000,
  })
  if (r.status !== 0) {
    console.error('[agentes] ABORTADO: el reseed E2E falló o fue rechazado por las guardas.')
    process.exit(2)
  }
}

let ok = 0
let mal = 0
const fallidos = []
const t0 = Date.now()

for (const rol of ROLES) {
  console.log(`\n[agentes] rol ${rol}`)
  // Cada invocación dispara el globalSetup, que resiembra la base.
  const r = spawnSync(
    'npx',
    ['playwright', 'test', 'e2e/agentes/agentes-acciones.spec.js', '--reporter=line', '--workers=1'],
    { cwd: raiz, stdio: 'inherit', shell: true, env: { ...process.env, AGENTE_ROL: rol } }
  )
  if (r.status === 0) ok++
  else {
    mal++
    fallidos.push(rol)
  }
}

// Escenarios de simulación cruzada (varios roles encadenados).
const SIMULACIONES = [
  { nombre: 'simulación cruzada', spec: 'e2e/agentes/simulacion-cruzada.spec.js' },
  { nombre: 'simulación similitud', spec: 'e2e/agentes/simulacion-similitud.spec.js' },
  { nombre: 'simulación reporte', spec: 'e2e/agentes/simulacion-reporte.spec.js' },
]

for (const sim of SIMULACIONES) {
  console.log(`\n[agentes] ${sim.nombre}`)
  const r = spawnSync('npx', ['playwright', 'test', sim.spec, '--reporter=line', '--workers=1'], {
    cwd: raiz,
    stdio: 'inherit',
    shell: true,
  })
  if (r.status === 0) ok++
  else {
    mal++
    fallidos.push(sim.nombre)
  }
}

console.log('\n[agentes] restableciendo el seed E2E…')
reseed('restauración final')

const min = ((Date.now() - t0) / 60000).toFixed(1)
console.log(`[agentes] ${ok} OK, ${mal} con fallos en ${min} min`)
if (fallidos.length) console.log(`[agentes] fallidos: ${fallidos.join(', ')}`)
process.exit(mal ? 1 : 0)

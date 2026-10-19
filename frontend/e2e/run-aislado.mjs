// Corredor E2E con aislamiento por archivo: cada spec corre en su propia
// invocación de playwright, y el globalSetup resiembra la DB local antes de
// cada una (~3s). Así las lecturas fusionadas (API ∪ mock) parten siempre del
// seed conocido aunque la DB sea monotónica entre pruebas.
// Mismo orden alfabético que `playwright test`.
// Uso: npm run test:e2e:aislado [-- <filtros playwright>]
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { exigirEntornoE2E } from './guardas-e2e.mjs'

// Guarda de seguridad (sprint final, Paso 1): sin autorización explícita
// (E2E_ALLOW_RESET=1) y un backend E2E (PROYECTWIN_API ≠ :8000), el runner
// aborta antes de lanzar Playwright.
exigirEntornoE2E('aislado')

const here = path.dirname(fileURLToPath(import.meta.url))
// Uso: node e2e/run-aislado.mjs [-- <args playwright>] | [filtro...]
// Con filtros, solo corren los archivos que contengan algún filtro.
const argv = process.argv.slice(2)
const sep = argv.indexOf('--')
const filtros = (sep === -1 ? argv : argv.slice(0, sep)).filter((a) => !a.startsWith('-'))
const extraArgs = sep === -1 ? [] : argv.slice(sep + 1)

let archivos = fs.readdirSync(here).filter((f) => f.endsWith('.spec.js')).sort()
if (filtros.length) archivos = archivos.filter((f) => filtros.some((x) => f.includes(x)))
if (!archivos.length) {
  console.log('[aislado] ningún archivo coincide con el filtro')
  process.exit(0)
}
console.log(`[aislado] ${archivos.length} archivos, reseed por archivo (vía globalSetup)`)

let ok = 0
let mal = 0
const fallidos = []
const t0 = Date.now()

for (const archivo of archivos) {
  const r = spawnSync('npx', ['playwright', 'test', `e2e/${archivo}`, '--reporter=line', ...extraArgs], {
    cwd: path.resolve(here, '..'),
    stdio: 'inherit',
    shell: true,
  })
  if (r.status === 0) {
    ok++
  } else {
    mal++
    fallidos.push(archivo)
  }
}

const min = ((Date.now() - t0) / 60000).toFixed(1)
console.log(`[aislado] ${ok} archivos OK, ${mal} con fallos en ${min} min`)
if (fallidos.length) {
  console.log(`[aislado] fallidos: ${fallidos.join(', ')}`)
  process.exit(1)
}

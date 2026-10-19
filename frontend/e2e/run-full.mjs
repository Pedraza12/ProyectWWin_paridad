// Gate completo: corre todas las suites y NO se detiene en el primer fallo.
// Al final imprime el resumen y sale con código != 0 si algo falló.
// Uso: npm run test:full
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { exigirEntornoE2E } from './guardas-e2e.mjs'

exigirEntornoE2E('full')

const here = path.dirname(fileURLToPath(import.meta.url))
const raiz = path.resolve(here, '..')

const ETAPAS = [
  { nombre: 'E2E determinista + navegación', cmd: ['node', ['e2e/run-aislado.mjs']] },
  { nombre: 'Agentes de acción + simulaciones', cmd: ['node', ['e2e/run-agentes-acciones.mjs']] },
  { nombre: 'Cobertura de controles', cmd: ['npx', ['playwright', 'test', 'e2e/agentes/cobertura.spec.js', '--workers=4']] },
  { nombre: 'Negativos', cmd: ['npx', ['playwright', 'test', 'e2e/agentes/negativos.spec.js', '--workers=4']] },
  { nombre: 'Volumen de datos', cmd: ['node', ['e2e/run-volumen.mjs']] },
]

const resultados = []
const t0 = Date.now()

for (const etapa of ETAPAS) {
  console.log(`\n=========== ${etapa.nombre} ===========`)
  const [bin, args] = etapa.cmd
  const r = spawnSync(bin, args, { cwd: raiz, stdio: 'inherit', shell: true })
  resultados.push({ nombre: etapa.nombre, ok: r.status === 0 })
}

const min = ((Date.now() - t0) / 60000).toFixed(1)
console.log('\n=========== RESUMEN GATE COMPLETO ===========')
for (const r of resultados) console.log(`${r.ok ? 'OK  ' : 'FALLA'} · ${r.nombre}`)
const fallos = resultados.filter((r) => !r.ok)
console.log(`\n${resultados.length - fallos.length}/${resultados.length} etapas OK en ${min} min`)
if (fallos.length) {
  console.log(`Fallaron: ${fallos.map((f) => f.nombre).join(', ')}`)
  process.exit(1)
}

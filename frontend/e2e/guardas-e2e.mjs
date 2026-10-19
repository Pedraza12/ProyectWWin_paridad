// Guardas compartidas para TODAS las entradas E2E (sprint final, Paso 1).
// Una configuración insegura debe ABORTAR la ejecución, nunca degradarse a una
// advertencia. La verificación server-side definitiva vive en el comando
// `php artisan e2e:reset --env=e2e` (backend/app/Console/Commands/E2eReset.php).
export const API_DESARROLLO = 'http://127.0.0.1:8000'

export function problemasEntornoE2E(env = process.env) {
  const problemas = []

  if (env.E2E_ALLOW_RESET !== '1') {
    problemas.push('Falta la autorización explícita del reseed: define E2E_ALLOW_RESET=1')
  }

  const api = (env.PROYECTWIN_API || '').replace(/\/+$/, '')
  if (!api) {
    problemas.push('Falta PROYECTWIN_API (debe apuntar al backend E2E, p. ej. http://127.0.0.1:8001)')
  } else if (api === API_DESARROLLO) {
    problemas.push(
      `PROYECTWIN_API apunta al backend de DESARROLLO (${API_DESARROLLO}); usa el backend E2E de :8001 (ver docs/entorno-pruebas-seguro.md)`
    )
  }

  return problemas
}

export function exigirEntornoE2E(nombre) {
  const problemas = problemasEntornoE2E()

  if (problemas.length === 0) return

  console.error(`[${nombre}] ABORTADO: entorno E2E inseguro; no se ejecutó ninguna operación.`)
  for (const problema of problemas) console.error(`  - ${problema}`)
  process.exit(2)
}

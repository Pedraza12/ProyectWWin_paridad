// ---------------------------------------------------------------------------
// Capturas reales de la interfaz de ProyecTwin para el Manual Técnico.
//
// Requisitos (documentados en el capítulo 8 del manual):
//   1) Backend Laravel en http://127.0.0.1:8000 (php artisan serve)
//   2) Frontend Vite en  http://localhost:5173 (npm run dev)
//   3) Base de datos seedeada (migrate:fresh --seed) con datos demo
//   4) node_modules del frontend instalado (aquí se reutiliza su Playwright)
//
// Uso:  node capturas.mjs
// Salida: PNGs en ../capturas/
// ---------------------------------------------------------------------------
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const FRONTEND = 'D:/ProyecTwin-MVP/frontend'
const { chromium } = require(path.join(FRONTEND, 'node_modules', 'playwright'))

const AQUI = path.dirname(fileURLToPath(import.meta.url))
const OUT = path.resolve(AQUI, '..', 'capturas')
const BASE = 'http://localhost:5173'

const CUENTAS = {
  aprendiz: { usuario: 'mgonzalez', clave: '123456' },
  instructor: { usuario: 'cruiz', clave: '123456' },
  admin: { usuario: 'a', clave: 'admin123' },
}

let capturadas = 0
const errores = []

async function esperarApp(page, ms = 900) {
  await page.waitForLoadState('networkidle').catch(() => {})
  await page.waitForTimeout(ms)
}

async function shot(page, nombre) {
  try {
    await page.screenshot({ path: path.join(OUT, nombre), fullPage: false })
    capturadas++
    console.log('OK  ', nombre)
  } catch (err) {
    errores.push(`${nombre}: ${err.message}`)
    console.error('FALLO', nombre, err.message)
  }
}

async function login(page, rol) {
  const { usuario, clave } = CUENTAS[rol]
  await page.goto(`${BASE}/login`)
  await page.fill('input[placeholder="Usuario"]', usuario)
  await page.fill('input[placeholder="••••••••"]', clave)
  await page.click('button[type="submit"]')
  await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 15000 })
  await esperarApp(page)
}

async function primerId(page, rutaApi, extractor = null) {
  return page.evaluate(
    async ([ruta, modo]) => {
      try {
        const r = await fetch(ruta, { credentials: 'include', headers: { Accept: 'application/json' } })
        if (!r.ok) return null
        const j = await r.json()
        const arr = Array.isArray(j) ? j : (j.data ?? [])
        const x = arr[0]
        if (!x) return null
        if (modo === 'similarity') return x.id
        return x.id
      } catch {
        return null
      }
    },
    [rutaApi, extractor],
  )
}

const contexto = async (browser, opts = {}) =>
  browser.newContext({
    viewport: { width: 1400, height: 880 },
    deviceScaleFactor: 1.25,
    locale: 'es-CO',
    reducedMotion: 'reduce',
    ...opts,
  })

const browser = await chromium.launch()

// ------------------------------------------------------------------ Público
{
  const ctx = await contexto(browser)
  const page = await ctx.newPage()
  await page.goto(`${BASE}/`)
  await esperarApp(page, 2600) // la portada tiene animación de texto
  await shot(page, '01_home_publica.png')

  await page.goto(`${BASE}/login`)
  await esperarApp(page)
  await shot(page, '02_login.png')
  await ctx.close()
}

// ------------------------------------------------------------------ Aprendiz
{
  const ctx = await contexto(browser)
  const page = await ctx.newPage()
  await login(page, 'aprendiz')

  await shot(page, '03_aprendiz_dashboard.png')

  await page.goto(`${BASE}/aprendiz/propuestas`)
  await esperarApp(page)
  await shot(page, '04_aprendiz_propuestas.png')

  const idProyecto = await primerId(page, '/v1/projects')
  if (idProyecto) {
    await page.goto(`${BASE}/aprendiz/detalle-proyecto/${idProyecto}`)
    await esperarApp(page, 1400)
    await shot(page, '05_aprendiz_detalle_proyecto.png')
  }

  await page.goto(`${BASE}/aprendiz/ficha`)
  await esperarApp(page)
  await shot(page, '06_aprendiz_mi_ficha.png')

  await page.goto(`${BASE}/aprendiz/similitudes`)
  await esperarApp(page)
  await shot(page, '07_aprendiz_similitudes.png')

  const idSim = await primerId(page, '/v1/similarities')
  if (idSim) {
    await page.goto(`${BASE}/aprendiz/detalle-similitud/${idSim}`)
    await esperarApp(page, 1400)
    await shot(page, '08_aprendiz_detalle_similitud.png')
  }

  await page.goto(`${BASE}/aprendiz/alertas`)
  await esperarApp(page)
  await shot(page, '09_aprendiz_alertas.png')

  // Modo oscuro persistente (cookie theme) sobre el dashboard
  await ctx.addCookies([{ name: 'theme', value: 'dark', url: BASE }])
  await page.goto(`${BASE}/aprendiz/dashboard`)
  await esperarApp(page)
  await shot(page, '10_modo_oscuro_dashboard.png')
  await ctx.close()
}

// ------------------------------------------------------------------ Instructor
{
  const ctx = await contexto(browser)
  const page = await ctx.newPage()
  await login(page, 'instructor')

  await shot(page, '11_instructor_dashboard.png')

  await page.goto(`${BASE}/instructor/revision-propuestas`)
  await esperarApp(page)
  await shot(page, '12_instructor_revision.png')

  const idProyecto = await primerId(page, '/v1/projects?estado=pendiente')
  if (idProyecto) {
    await page.goto(`${BASE}/instructor/detalle-proyecto/${idProyecto}`)
    await esperarApp(page, 1400)
    await shot(page, '13_instructor_detalle_proyecto.png')
  }

  await page.goto(`${BASE}/instructor/similitudes`)
  await esperarApp(page)
  await shot(page, '14_instructor_similitudes.png')

  await page.goto(`${BASE}/instructor/fichas`)
  await esperarApp(page)
  await shot(page, '15_instructor_fichas.png')
  await ctx.close()
}

// ------------------------------------------------------------------ Admin
{
  const ctx = await contexto(browser)
  const page = await ctx.newPage()
  await login(page, 'admin')

  await shot(page, '16_admin_dashboard.png')

  await page.goto(`${BASE}/admin/usuarios`)
  await esperarApp(page)
  await shot(page, '17_admin_usuarios.png')

  const idUsuario = await primerId(page, '/v1/general-users')
  if (idUsuario) {
    await page.goto(`${BASE}/admin/detalle-usuario/${idUsuario}`)
    await esperarApp(page, 1400)
    await shot(page, '18_admin_detalle_usuario.png')
  }

  await page.goto(`${BASE}/admin/bitacora`)
  await esperarApp(page)
  await shot(page, '19_admin_bitacora.png')

  await page.goto(`${BASE}/admin/config-similitud`)
  await esperarApp(page)
  await shot(page, '20_admin_motor.png')

  await page.goto(`${BASE}/admin/similitudes`)
  await esperarApp(page)
  await shot(page, '21_admin_similitudes.png')

  await page.goto(`${BASE}/admin/reportes-fallas`)
  await esperarApp(page)
  await shot(page, '22_admin_reportes.png')
  await ctx.close()
}

// ------------------------------------------------------------------ Móvil
{
  const ctx = await contexto(browser, { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
  const page = await ctx.newPage()
  await page.goto(`${BASE}/`)
  await esperarApp(page, 2400)
  await shot(page, '23_movil_home.png')

  await login(page, 'aprendiz')
  await page.goto(`${BASE}/aprendiz/propuestas`)
  await esperarApp(page)
  await shot(page, '24_movil_propuestas.png')
  await ctx.close()
}

await browser.close()

console.log(`\nCapturas generadas: ${capturadas}`)
if (errores.length) {
  console.log('Errores:')
  errores.forEach((e) => console.log(' -', e))
}
process.exit(errores.length ? 1 : 0)

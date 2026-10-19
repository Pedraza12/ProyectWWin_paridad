import { test, expect, login } from './helpers'

// W-01: reintento de envío. Tras un fallo, el reintento debe persistir las
// correcciones del formulario y el equipo ANTES de reenviar, confirmarlas y
// no generar proyectos duplicados. Cubre también el fallo al guardar y el
// envío desde la tarjeta.

async function abrirNuevaPropuesta(page) {
  await page.getByRole('main').getByRole('button', { name: /Nueva propuesta/i }).click()
}

async function llenar(page, titulo) {
  await page.getByPlaceholder(/Sistema de monitoreo ambiental/i).fill(titulo)
  await page.locator('textarea').nth(0).fill('Herramienta web para controlar inventarios con alertas de stock y reportes de trazabilidad.')
  await page.locator('textarea').nth(1).fill('Controlar los inventarios del almacén con alertas por stock mínimo.')
  await page.locator('textarea').nth(2).fill('Registrar entradas y salidas de productos.\nGenerar reportes de trazabilidad por lote.')
  await page.locator('select').nth(0).selectOption({ index: 1 })
}

test('W-01 · fallo → corrección → reintento persiste título y equipo', async ({ page }) => {
  const sello = String(Date.now())
  const titulo = `Reintento E2E ${sello}`
  const corregido = `Reintento E2E CORREGIDO ${sello}`

  let llamadas = 0
  await page.route('**/v1/projects/*/enviar', async (route) => {
    llamadas += 1
    if (llamadas === 1) {
      return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'Fallo simulado' }) })
    }
    return route.continue()
  })

  await login(page, 'aprendiz')
  await page.goto('/aprendiz/propuestas')
  await abrirNuevaPropuesta(page)
  await llenar(page, titulo)

  const chips = page.locator('button[aria-pressed]')
  const nombre1 = ((await chips.nth(0).textContent()) || '').trim()
  const nombre2 = ((await chips.nth(1).textContent()) || '').trim()
  await chips.nth(0).click()

  await page.getByRole('button', { name: /Enviar propuesta y analizar/i }).click()
  await expect(page.getByText(/quedó guardada como borrador/i)).toBeVisible({ timeout: 15000 })

  await page.getByPlaceholder(/Sistema de monitoreo ambiental/i).fill(corregido)
  await chips.nth(1).click()

  await page.getByRole('button', { name: /Enviar propuesta y analizar/i }).click()
  await page.waitForURL('**/aprendiz/resultado-analisis**', { timeout: 25000 })

  // Persistencia real: título corregido visible, sin rastro del original.
  await page.goto('/aprendiz/propuestas')
  await expect(page.getByText(corregido).first()).toBeVisible({ timeout: 15000 })
  await expect(page.getByText(titulo, { exact: true })).toHaveCount(0)

  // El equipo completo (integrante agregado tras el fallo) quedó persistido.
  await page.getByText(corregido).first().click()
  await page.waitForURL('**/aprendiz/detalle-proyecto/**')
  await expect(page.getByText(nombre1).first()).toBeVisible()
  await expect(page.getByText(nombre2).first()).toBeVisible()
})

test('W-01 · si el guardado previo al reintento falla, no se envía y sigue en borrador', async ({ page }) => {
  const sello = String(Date.now())
  const titulo = `Reintento E2E guardado ${sello}`
  const corregido = `Reintento E2E guardado CORREGIDO ${sello}`

  let enviar = 0
  let puts = 0
  // Playwright evalúa las rutas en orden inverso al registro: registrar el PUT
  // primero y el enviar después deja al intercept de enviar con prioridad.
  await page.route('**/v1/projects/*', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue()
    puts += 1
    return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'Fallo guardando' }) })
  })
  await page.route('**/v1/projects/*/enviar', async (route) => {
    enviar += 1
    if (enviar === 1) {
      return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'Fallo simulado' }) })
    }
    return route.continue()
  })

  await login(page, 'aprendiz')
  await page.goto('/aprendiz/propuestas')
  await abrirNuevaPropuesta(page)
  await llenar(page, titulo)
  await page.locator('button[aria-pressed]').nth(0).click()

  await page.getByRole('button', { name: /Enviar propuesta y analizar/i }).click()
  await expect(page.getByText(/quedó guardada como borrador/i)).toBeVisible({ timeout: 15000 })

  await page.getByPlaceholder(/Sistema de monitoreo ambiental/i).fill(corregido)
  await page.getByRole('button', { name: /Enviar propuesta y analizar/i }).click()

  await expect(page.getByText(/No se pudo confirmar el guardado|quedó guardada como borrador/i)).toBeVisible({ timeout: 15000 })
  expect(puts).toBe(1)
  expect(enviar).toBe(1) // el reintento NO llegó a llamar a /enviar

  await page.getByRole('button', { name: /Cancelar/i }).click()
  await page.reload()
  const tarjeta = page.locator('article').filter({ hasText: titulo }).first()
  await expect(tarjeta.getByText('Borrador')).toBeVisible()
})

test('W-01 · reintentos repetidos no duplican la propuesta', async ({ page }) => {
  const sello = String(Date.now())
  const titulo = `Reintento E2E repetido ${sello}`

  let llamadas = 0
  await page.route('**/v1/projects/*/enviar', async (route) => {
    llamadas += 1
    if (llamadas <= 2) {
      return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'Fallo simulado' }) })
    }
    return route.continue()
  })

  await login(page, 'aprendiz')
  await page.goto('/aprendiz/propuestas')
  await abrirNuevaPropuesta(page)
  await llenar(page, titulo)
  await page.locator('button[aria-pressed]').nth(0).click()

  await page.getByRole('button', { name: /Enviar propuesta y analizar/i }).click()
  await expect(page.getByText(/quedó guardada como borrador/i)).toBeVisible({ timeout: 15000 })

  await page.getByRole('button', { name: /Enviar propuesta y analizar/i }).click()
  await expect(page.getByText(/quedó guardada como borrador/i)).toBeVisible({ timeout: 15000 })

  await page.getByRole('button', { name: /Enviar propuesta y analizar/i }).click()
  await page.waitForURL('**/aprendiz/resultado-analisis**', { timeout: 25000 })

  await page.goto('/aprendiz/propuestas')
  await expect(page.locator('article').filter({ hasText: titulo })).toHaveCount(1)
})

test('W-01 · envío desde la tarjeta tras el fallo (sin duplicar)', async ({ page }) => {
  const sello = String(Date.now())
  const titulo = `Reintento E2E tarjeta ${sello}`

  let llamadas = 0
  await page.route('**/v1/projects/*/enviar', async (route) => {
    llamadas += 1
    if (llamadas === 1) {
      return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'Fallo simulado' }) })
    }
    return route.continue()
  })

  await login(page, 'aprendiz')
  await page.goto('/aprendiz/propuestas')
  await abrirNuevaPropuesta(page)
  await llenar(page, titulo)
  await page.locator('button[aria-pressed]').nth(0).click()

  await page.getByRole('button', { name: /Enviar propuesta y analizar/i }).click()
  await expect(page.getByText(/quedó guardada como borrador/i)).toBeVisible({ timeout: 15000 })

  await page.getByRole('button', { name: /Cancelar/i }).click()
  await page.reload()

  const tarjeta = page.locator('article').filter({ hasText: titulo }).first()
  await expect(tarjeta).toBeVisible({ timeout: 15000 })
  await tarjeta.getByRole('button', { name: /Enviar/i }).click()
  await page.waitForURL('**/aprendiz/resultado-analisis**', { timeout: 25000 })

  await page.goto('/aprendiz/propuestas')
  const tarjetaFinal = page.locator('article').filter({ hasText: titulo })
  await expect(tarjetaFinal).toHaveCount(1)
  await expect(tarjetaFinal.getByText('En revisión')).toBeVisible()
})

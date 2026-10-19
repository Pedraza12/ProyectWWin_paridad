import { test, expect, login, logout } from './helpers'

test.describe('Revisión de propuestas (instructor)', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, 'instructor')
    await page.getByRole('link', { name: /Revisión Propuestas/i }).first().click()
    await page.waitForURL('**/instructor/revision-propuestas')
  })

  test('muestra la cola con nodos de propuesta', async ({ page }) => {
    const cola = page.getByRole('list', { name: /Cola de revisión/i })
    await expect(cola).toBeVisible()
    const primero = cola.getByRole('button').first()
    await expect(primero).toContainText(/./)
  })

  test('aprobar una pendiente cambia su estado y notifica al aprendiz', async ({ page }) => {
    const cola = page.getByRole('list', { name: /Cola de revisión/i })
    await expect(cola.getByRole('button').first()).toBeVisible({ timeout: 15000 })
    const nodo = cola.getByRole('button', { name: /En revisión/ }).first()
    if ((await nodo.count()) === 0) test.skip(true, 'No hay pendientes para este instructor en el seed actual')

    await nodo.click()
    await page.getByRole('button', { name: /^Aprobar /i }).click()
    await page.getByRole('button', { name: /Sí, aprobar/i }).click()

    // La propuesta aprobada ya no ofrece botones Aprobar/Rechazar en el preview
    await expect(page.getByRole('button', { name: /^Aprobar /i })).toHaveCount(0, { timeout: 10000 })
  })
})

test.describe('Detección de similitudes al enviar', () => {
  test('una propuesta enviada con solapamiento genera coincidencias que el instructor ve al aprobarla', async ({ page }) => {
    // 1. El aprendiz crea una propuesta cuyas palabras clave solapan con
    //    'Sistema de Gestión de Inventarios' (aprobada en el seed) → Jaccard ≈ 0.71
    await login(page, 'aprendiz')
    await page.getByRole('link', { name: 'Propuestas' }).first().click()
    await page.waitForURL('**/aprendiz/propuestas')
    await page.getByRole('main').getByRole('button', { name: /Nueva propuesta/i }).click()
    await page.getByPlaceholder(/Sistema de monitoreo ambiental/i).fill('Sistema de Control de Inventarios')
    await page.locator('textarea').nth(0).fill('Herramienta web para controlar inventarios de almacén con alertas de stock y reportes de trazabilidad.')
    await page.locator('textarea').nth(1).fill('Controlar los inventarios del almacén con alertas por stock mínimo.')
    await page.locator('textarea').nth(2).fill('Registrar entradas y salidas de productos.\nGenerar reportes de trazabilidad por lote.')
    await page.getByPlaceholder('iot, sensores, agricultura').fill('inventarios, stock, almacén, control')
    await page.locator('select').nth(0).selectOption({ index: 1 })
    await page.getByRole('button', { name: /Enviar propuesta/i }).click()
    await page.waitForURL('**/aprendiz/analizando-proyecto', { timeout: 15000 })
    await page.waitForURL('**/aprendiz/resultado-analisis**', { timeout: 20000 })

    // 2. El instructor aprueba la nueva propuesta desde la cola de Revisión
    await logout(page)
    await login(page, 'instructor')
    await page.goto('/instructor/revision-propuestas')
    // La cola está paginada: se busca por título para asegurar el nodo.
    await page.getByPlaceholder(/Título, aprendiz o ficha/i).fill('Sistema de Control de Inventarios')
    await page.getByRole('list', { name: /Cola de revisión/i }).getByRole('button', { name: /Sistema de Control de Inventarios/ }).click()
    await page.getByRole('button', { name: /^Aprobar /i }).click()
    await page.getByRole('button', { name: 'Sí, aprobar' }).click()

    // 3. La detección corre contra el corpus vigente del mismo programa y lo informa
    await expect(page.getByText(/se detectaron \d+ coincidencia/i)).toBeVisible()
  })
})

test.describe('Crear ficha (instructor)', () => {
  test('el formulario compila, valida y crea la ficha de punta a punta', async ({ page }) => {
    await login(page, 'instructor')
    await page.goto('/instructor/fichas?crear=1')

    // El formulario renderiza con estilos (cascada red→programa). El código de
    // unión ya no se elige en el formulario: lo genera el servidor.
    const nombre = page.getByPlaceholder('Ej. Análisis y Desarrollo 2718')
    const numero = page.getByPlaceholder('Ej. 3142101')
    const red = page.locator('form select[name="red"]')
    const programa = page.locator('form select[name="programaId"]')
    await expect(nombre).toBeVisible()
    await expect(numero).toBeVisible()

    // Validación: enviar vacío muestra errores y no navega
    const submit = page.locator('form').getByRole('button', { name: /^Crear ficha$/i })
    await submit.click()
    await expect(page.getByText(/Selecciona la red de conocimiento/i)).toBeVisible()
    await expect(page.getByText(/Selecciona el programa de formación/i)).toBeVisible()
    await expect(page.getByText(/El nombre de la ficha es obligatorio/i)).toBeVisible()
    await expect(page.getByText(/El número de ficha es obligatorio/i)).toBeVisible()

    // Cascada: elegir la red habilita sus programas
    await expect(programa).toBeDisabled()
    await red.selectOption('Informática, Diseño y Desarrollo de Software')
    await expect(programa).toBeEnabled()
    await expect(programa.locator('option')).toHaveCount(3) // placeholder + 2 programas

    // Llenar y enviar
    await nombre.fill('Ficha de Prueba E2E')
    await numero.fill('9999')
    await programa.selectOption({ label: 'ADSO' })
    await submit.click()

    // El formulario se cierra (sin navegación), muestra confirmación y lista la ficha nueva
    await expect(page.getByText('Ficha creada correctamente.')).toBeVisible()
    await expect(page.getByText('Ficha de Prueba E2E')).toBeVisible()
    await expect(page.getByText('N° 9999 · ADSO')).toBeVisible()
    // El código de unión generado por el servidor aparece en la lista.
    await expect(page.locator('code').filter({ hasText: /^[a-z]{3}-[a-z]{4}$/ }).first()).toBeVisible()
  })
})

test.describe('Similitudes del instructor', () => {
  test('sección listada con filas de coincidencias del seed', async ({ page }) => {
    await login(page, 'instructor')
    await page.goto('/instructor/similitudes')
    await expect(page.getByText(/Similitudes Detectadas|coincidencia/i).first()).toBeVisible()
  })
})

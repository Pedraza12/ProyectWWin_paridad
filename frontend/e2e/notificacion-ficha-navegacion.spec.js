import { test, expect, login, CUENTAS } from './helpers'

// BUG-020 (auditoría): las notificaciones navegables no deben comportarse como
// un simple "Marcar como leída". El ajuste `fichaId` de AlertasBase hizo que
// `ficha:<id>` sea abrible: el control muestra title="Abrir detalle" y navega
// (el staff al detalle de la ficha; el aprendiz a su propia ficha).
// El control negativo (sin enlace) sigue anunciándose como lectura y no navega.

test('instructor: la notificación ficha del seed abre el detalle de la ficha', async ({ page }) => {
  await login(page, 'instructor')
  await page.goto('/instructor/alertas')

  // El seed crea "ficha:1" para el instructor de la ficha 1 (Carlos Ruiz) al
  // unirse Andrés Cifuentes (NotificacionesService::fichaMovimientoInstructor).
  // Se nombra al aprendiz para no confundirla con uniones posteriores.
  const notificacion = page.getByRole('button', { name: /Andrés Cifuentes se unió a la ficha/ })
  await expect(notificacion).toBeVisible()

  // Caso que motivó el ajuste: si es navegable, NO se anuncia solo como lectura.
  await expect(notificacion).toHaveAttribute('title', 'Abrir detalle')

  await notificacion.click()
  await page.waitForURL('**/instructor/detalle-ficha/1')
  await expect(page.getByRole('heading', { name: 'Analisis y Desarrollo 2568' })).toBeVisible()
})

test('aprendiz: una notificación ficha creada por API lleva a /aprendiz/ficha', async ({ page, request }) => {
  // El seed no trae notificaciones ficha para el aprendiz: se crea una como
  // admin (patrón de API de los agentes, con rutas relativas al proxy de Vite).
  const acceso = await request.post('/v1/auth/login', {
    data: { username: CUENTAS.admin.username, password: CUENTAS.admin.password },
  })
  expect(acceso.ok()).toBeTruthy()
  const { token } = await acceso.json()

  const hoy = new Date()
  const fecha = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`
  const titulo = `E2E ficha navegable ${Date.now()}`

  const creada = await request.post('/v1/notifications', {
    headers: { Authorization: `Bearer ${token}` },
    data: { titulo, tipo: 'sistema', enlace: 'ficha:1', fecha, id_usuario: 1 },
  })
  expect(creada.ok()).toBeTruthy()

  await login(page, 'aprendiz')
  await page.goto('/aprendiz/alertas')

  const notificacion = page.getByRole('button', { name: new RegExp(titulo) })
  await expect(notificacion).toBeVisible()
  await expect(notificacion).toHaveAttribute('title', 'Abrir detalle')

  await notificacion.click()
  // El aprendiz no tiene detalle de ficha: va a su propia ficha (rama fichaId).
  await page.waitForURL('**/aprendiz/ficha')
  await expect(page.getByRole('heading', { name: 'Analisis y Desarrollo 2568' })).toBeVisible()
})

test('aprendiz: una notificación sin enlace solo se marca como leída', async ({ page, request }) => {
  // Control negativo de BUG-020: sin enlace no hay destino que decodificar,
  // así que `puedeAbrir` es false (AlertasBase.jsx) y el clic no debe navegar.
  const acceso = await request.post('/v1/auth/login', {
    data: { username: CUENTAS.admin.username, password: CUENTAS.admin.password },
  })
  expect(acceso.ok()).toBeTruthy()
  const { token } = await acceso.json()

  const hoy = new Date()
  const fecha = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`
  const titulo = `E2E sin enlace ${Date.now()}`

  // Sin `enlace`: la API lo admite nulo (validación nullable del controlador).
  const creada = await request.post('/v1/notifications', {
    headers: { Authorization: `Bearer ${token}` },
    data: { titulo, tipo: 'sistema', fecha, id_usuario: 1 },
  })
  expect(creada.ok()).toBeTruthy()

  await login(page, 'aprendiz')
  await page.goto('/aprendiz/alertas')

  const notificacion = page.getByRole('button', { name: new RegExp(titulo) })
  await expect(notificacion).toBeVisible()
  // Texto exacto de AlertasBase: navegable ⇒ "Abrir detalle"; si no, lectura.
  await expect(notificacion).toHaveAttribute('title', 'Marcar como leída')
  await expect(notificacion.getByText('Nueva')).toBeVisible()

  // Se espera el PUT de la marca para no depender de tiempos.
  const marcado = page.waitForResponse(
    (r) => r.request().method() === 'PUT' && /\/v1\/notifications\/\d+$/.test(new URL(r.url()).pathname) && r.ok()
  )
  await notificacion.click()
  await marcado

  // Sin navegación: la URL sigue en alertas y la notificación queda leída.
  await expect(page).toHaveURL(/\/aprendiz\/alertas$/)
  await expect(notificacion.getByText('Nueva')).toHaveCount(0)
  await expect(notificacion).toBeVisible()
})

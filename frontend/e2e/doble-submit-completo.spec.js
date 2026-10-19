import { test, expect, login } from './helpers'

// BUG-022 (auditoría): el doble envío. Se cubren dos formularios que aún no
// tenían spec de doble submit: el guardado del perfil del aprendiz y la edición
// de usuario del admin. Mismo patrón que doble-submit-admin.spec.js:
// interceptar la escritura, retrasarla y contar emisiones; el segundo clic
// (durante la ventana de guardado) no debe disparar otra petición.

test('perfil del aprendiz: doble clic en Guardar no envía dos PUT', async ({ page }) => {
  await login(page, 'aprendiz')

  let puts = 0
  // La actualización es un no-op (mismos nombre/apellido del seed); se retrasa
  // la respuesta para sostener la ventana de "guardando" durante el 2º clic.
  await page.route('**/v1/general-users/1', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue()
    puts++
    await new Promise((r) => setTimeout(r, 800))
    return route.continue()
  })

  await page.goto('/aprendiz/perfil')
  await page.getByRole('button', { name: /Editar perfil/i }).click()

  // Único submit visible: el formulario de edición (el panel Seguridad no
  // monta su formulario hasta abrir "Cambiar contraseña").
  const guardar = page.locator('form button[type="submit"]')
  await guardar.click()
  // Durante la petición el botón queda deshabilitado (y rotulado "Guardando...").
  await expect(guardar).toBeDisabled()
  // Segundo intento dentro de la ventana: no debe emitir otra petición.
  await guardar.click({ force: true, timeout: 1000 }).catch(() => {})

  await expect(page.getByText('Tus datos se actualizaron correctamente.')).toBeVisible({ timeout: 15000 })
  expect(puts).toBe(1)
})

test('edición de usuario en admin: doble clic en Guardar cambios no envía dos PUT', async ({ page }) => {
  await login(page, 'admin')

  let puts = 0
  // La respuesta se simula: el objetivo es contar emisiones sin alterar la
  // cuenta sembrada (el reseed por archivo ya aísla cada spec).
  await page.route('**/v1/general-users/13', async (route) => {
    if (route.request().method() !== 'PUT') return route.continue()
    puts++
    await new Promise((r) => setTimeout(r, 800))
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ id: 13, correo: 'luis.garcia.e2e@correo.com' }),
    })
  })

  // Usuario 13 = Luis Fernando García (instructor sin fichas a cargo).
  await page.goto('/admin/detalle-usuario/13')
  await page.getByRole('button', { name: /^Editar$/i }).click()

  // El formulario solo emite PUT si cambia correo o rol: se cambia el correo.
  await page.locator('input[name="email"]').fill('luis.garcia.e2e@correo.com')

  const guardar = page.getByRole('button', { name: /Guardar cambios/i })
  await guardar.click()
  await expect(guardar).toBeDisabled()
  await guardar.click({ force: true, timeout: 1000 }).catch(() => {})

  await expect(page.getByText('Usuario actualizado correctamente.')).toBeVisible({ timeout: 15000 })
  expect(puts).toBe(1)
})

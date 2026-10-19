import { test, expect, login, confirmarBorrado } from './helpers'

// D-2b × BUG-007: la evidencia de similitud es COMPARTIDA (una fila conecta dos
// propuestas), así que ninguna cascada puede destruirla. La red sembrada
// "Informática, Diseño y Desarrollo de Software" tiene a ADSO con propuestas
// emparejadas por el motor: el backend responde 409 con {"message"} y el
// frontend muestra error.data.message sin borrar nada.

const RED = 'Informática, Diseño y Desarrollo de Software'

test('admin: eliminar la red con evidencia responde 409 y no borra nada', async ({ page }) => {
  await login(page, 'admin')
  await page.goto('/admin/redes-conocimiento')

  const fila = page.locator('tr', { hasText: RED })
  await expect(fila).toBeVisible()
  // Evidencia real del seed: ADSO (programa de la red) tiene pares de similitud.
  await expect(fila).toContainText('ADSO')

  await fila.getByRole('button', { name: /Eliminar/i }).click()
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await expect(page.getByText('Eliminar red')).toBeVisible()

  const [respuesta] = await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/v1/knowledge-networks/1') && r.request().method() === 'DELETE'
    ),
    confirmarBorrado(page, 'ELIMINAR'),
  ])
  expect(respuesta.status()).toBe(409)

  // (a) El aviso de error muestra el mensaje del backend.
  const aviso = page.getByRole('alert').filter({ hasText: /similitudes compartidas/i })
  await expect(aviso).toBeVisible()
  await expect(aviso).toContainText(RED)

  // (b) Nada se borró: la red sigue en la lista, incluso tras recargar.
  await expect(page.locator('tr', { hasText: RED })).toBeVisible()
  await page.reload()
  await expect(page.locator('tr', { hasText: RED })).toBeVisible()
})

import { test, expect, login } from './helpers'

test.describe('Hover de DataTable', () => {
  test('la fila y la celda sticky comparten el fondo en escritorio', async ({ page }) => {
    await login(page, 'admin')
    await page.goto('/admin/usuarios')

    const fila = page.locator('tbody tr').first()
    await fila.hover()

    const fondoFila = await fila.evaluate((el) => getComputedStyle(el).backgroundColor)
    const fondoSticky = await fila.locator('td').last().evaluate((el) => getComputedStyle(el).backgroundColor)
    expect(fondoFila).not.toBe('rgba(0, 0, 0, 0)')
    expect(fondoSticky).toBe(fondoFila)
  })

  test('en móvil la celda sticky no conserva un fondo opaco', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await login(page, 'admin')
    await page.goto('/admin/usuarios')

    const sticky = page.locator('tbody tr').first().locator('td').last()
    const fondo = await sticky.evaluate((el) => getComputedStyle(el).backgroundColor)
    expect(fondo).toBe('rgba(0, 0, 0, 0)')
  })
})

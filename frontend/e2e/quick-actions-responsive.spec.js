import { test, expect, login } from './helpers'

const ANCHOS = [375, 768, 1024, 1025]

test.describe('Acciones rápidas responsive', () => {
  test('admin mantiene las acciones visibles sin desbordamiento', async ({ page }) => {
    await login(page, 'admin')

    for (const width of ANCHOS) {
      await page.setViewportSize({ width, height: 900 })
      await page.goto('/admin/dashboard')
      await expect(page.getByText('Acciones rápidas')).toBeVisible()
      const accion = page.locator('main').getByRole('link', { name: /Motor de similitud/i })
      await expect(accion).toBeVisible()
      const desborde = await page.evaluate(
        () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
      )
      expect(desborde, `desborde a ${width}px`).toBe(false)
    }
  })

  test('aprendiz mantiene Nueva Propuesta visible sin desbordamiento', async ({ page }) => {
    await login(page, 'aprendiz')

    for (const width of ANCHOS) {
      await page.setViewportSize({ width, height: 900 })
      await page.goto('/aprendiz/dashboard')
      await expect(page.getByText('Acciones rápidas')).toBeVisible()
      await expect(page.locator('main').getByRole('link', { name: /Nueva Propuesta/i })).toBeVisible()
      const desborde = await page.evaluate(
        () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
      )
      expect(desborde, `desborde a ${width}px`).toBe(false)
    }
  })
})

import { test, expect, login } from './helpers'

test.describe('Footer informativo', () => {
  test('no duplica la navegación y conserva contacto y copyright', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await login(page, 'admin')
    await page.goto('/admin/dashboard')

    const footer = page.locator('footer')
    await expect(footer.locator('nav')).toHaveCount(0)
    await expect(footer).toContainText('Todos los derechos reservados')
    await expect(footer.locator('a[href^="tel:"]')).toBeVisible()
    await expect(footer.locator('a[href^="mailto:"]')).toBeVisible()

    const desborde = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    )
    expect(desborde).toBe(false)
  })
})

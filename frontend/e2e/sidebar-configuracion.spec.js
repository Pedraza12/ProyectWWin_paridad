import { test, expect, login } from './helpers'

test.describe('Sidebar: Configuración', () => {
  test('agrupa Redes y programas y Motor de similitud sin duplicar enlaces', async ({ page }) => {
    await login(page, 'admin')
    await page.goto('/admin/dashboard')

    const sidebar = page.locator('aside')
    await expect(sidebar.getByText('Configuración')).toBeVisible()
    await expect(sidebar.getByRole('link', { name: 'Redes y programas' })).toHaveCount(1)
    await expect(sidebar.getByRole('link', { name: 'Motor de similitud' })).toHaveCount(1)
    await expect(sidebar.getByRole('link', { name: 'Bitácora' })).toBeVisible()
    await expect(sidebar.locator('p')).toHaveCount(0)
  })
})

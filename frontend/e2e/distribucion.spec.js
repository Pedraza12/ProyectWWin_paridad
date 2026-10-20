import { test, expect, login, logout } from './helpers'

test.describe('Navegación lateral (sidebar)', () => {
  test('admin ve sus enlaces sin títulos de sección', async ({ page }) => {
    await login(page, 'admin')
    await page.goto('/admin/dashboard')
    const sidebar = page.locator('aside')
    for (const nombre of ['Dashboard', 'Propuestas', 'Similitudes', 'Soporte', 'Notificaciones', 'Usuarios', 'Fichas', 'Bitácora', 'Redes y programas', 'Motor de similitud', 'Mi Perfil']) {
      await expect(sidebar.getByRole('link', { name: nombre })).toBeVisible()
    }
    await expect(sidebar.getByText('Configuración')).toBeVisible()
    // Sin párrafos separadores (el grupo usa un span visual)
    await expect(sidebar.locator('p')).toHaveCount(0)
  })

  test('instructor y aprendiz ven sus enlaces', async ({ page }) => {
    await login(page, 'instructor')
    await page.goto('/instructor/dashboard')
    let sidebar = page.locator('aside')
    for (const nombre of ['Dashboard', 'Revisión Propuestas', 'Similitudes', 'Fichas', 'Notificaciones', 'Mi Perfil', 'Soporte']) {
      await expect(sidebar.getByRole('link', { name: nombre })).toBeVisible()
    }
    await expect(sidebar.locator('p')).toHaveCount(0)

    await logout(page)
    await page.goto('/login')
    await page.getByPlaceholder('Usuario').fill('mgonzalez')
    await page.locator('input[type="password"]').fill('123456')
    await page.getByRole('button', { name: /Iniciar Sesión/i }).click()
    await page.waitForURL('**/aprendiz/dashboard')
    await page.goto('/aprendiz/dashboard')
    sidebar = page.locator('aside')
    for (const nombre of ['Dashboard', 'Propuestas', 'Similitudes', 'Ficha', 'Notificaciones', 'Mi Perfil', 'Soporte']) {
      await expect(sidebar.getByRole('link', { name: nombre })).toBeVisible()
    }
  })

  test('el enlace queda activo en las subrutas de detalle', async ({ page }) => {
    await login(page, 'admin')
    await page.goto('/admin/detalle-proyecto/4')
    const activo = page.locator('aside a[href="/admin/proyectos"]')
    await expect(activo).toHaveClass(/linkActive/)
    await expect(activo).toContainText('Propuestas')
  })
})

test.describe('Footer informativo y migas', () => {
  test('el footer no duplica la navegación y conserva el contacto', async ({ page }) => {
    await login(page, 'admin')
    await page.goto('/admin/dashboard')
    const footer = page.locator('footer')
    await expect(footer.locator('nav')).toHaveCount(0)
    await expect(footer).toContainText('Todos los derechos reservados')
    await expect(footer.locator('a[href^="tel:"]')).toBeVisible()
    await expect(footer.locator('a[href^="mailto:"]')).toBeVisible()
  })

  test('listas admin con miga Dashboard › Sección', async ({ page }) => {
    await login(page, 'admin')
    await page.goto('/admin/proyectos')
    await expect(page.getByText('Dashboard').first()).toBeVisible()
    await page.goto('/admin/similitudes')
    await expect(page.getByText('Dashboard').first()).toBeVisible()
    await page.goto('/admin/reportes-fallas')
    await expect(page.getByText('Dashboard').first()).toBeVisible()
  })
})

import { test, expect } from './helpers'

// La descarga del APK es pública: no requiere sesión ni rol.
test.describe('Home pública: descarga del APK', () => {
  test('expone y descarga el APK oficial sin autenticación', async ({ page, request }) => {
    await page.goto('/')

    const enlace = page.getByRole('link', { name: /Descargar APK para Android/i })
    await expect(enlace).toBeVisible({ timeout: 15000 })
    await expect(enlace).toHaveAttribute('href', '/apk/ProyecTWin.apk')
    await expect(enlace).toHaveAttribute('download', 'ProyecTWin.apk')

    // El archivo existe y se sirve con el MIME de Android.
    const head = await request.head('/apk/ProyecTWin.apk')
    expect(head.status()).toBe(200)
    expect(head.headers()['content-type']).toContain('android.package-archive')

    // El enlace inicia una descarga real, sin pasar por /login.
    const [descarga] = await Promise.all([
      page.waitForEvent('download'),
      enlace.click(),
    ])
    expect(descarga.suggestedFilename()).toBe('ProyecTWin.apk')
  })
})

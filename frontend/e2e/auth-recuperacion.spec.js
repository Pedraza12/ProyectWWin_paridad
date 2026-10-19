import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test, expect, crearUsuarioApi } from './helpers'

// Recuperación de contraseña por correo. En el entorno E2E el backend usa
// MAIL_MAILER=log: el mensaje completo (con el enlace) queda en
// backend/storage/logs/laravel.log y el spec lo recupera de ahí — buzón de
// pruebas determinista — sin exponer enlaces en la API fuera de `local`.

// El registro público no existe; para este flujo se crea la cuenta desde el
// admin y se recupera la contraseña con el correo personal registrado.
async function crearAprendiz(request) {
  return crearUsuarioApi(request, { nombre: 'Reset E2E', apellido: 'Prueba' })
}

// Buzón de pruebas: ruta del log del backend (mailer `log` de `.env.e2e`).
const LOG_CORREO_E2E = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  '..',
  'backend',
  'storage',
  'logs',
  'laravel.log',
)

// Extrae del log la ÚLTIMA URL de restablecimiento emitida para `correo`.
// Formato estable (`ResetPasswordNotification::resetUrl` con http_build_query;
// en el HTML el separador aparece escapado como `&amp;`).
function enlaceEnLog(correo) {
  let contenido
  try {
    contenido = fs.readFileSync(LOG_CORREO_E2E, 'utf8')
  } catch {
    return null
  }
  const patron = /restablecer-contrasena\?token=([A-Za-z0-9]{16,})&(?:amp;)?correo=([^\s"'<>]+)/g
  let ultimo = null
  for (const m of contenido.matchAll(patron)) {
    let correoDelEnlace = m[2]
    try {
      correoDelEnlace = decodeURIComponent(m[2])
    } catch {
      // Valor crudo si el log trae una codificación inesperada.
    }
    if (correoDelEnlace.toLowerCase() === String(correo).toLowerCase()) {
      ultimo = `/restablecer-contrasena?token=${m[1]}&correo=${encodeURIComponent(correo)}`
    }
  }
  return ultimo
}

async function esperarEnlaceDeRecuperacion(correo, timeout = 15000) {
  const fin = Date.now() + timeout
  while (Date.now() < fin) {
    const enlace = enlaceEnLog(correo)
    if (enlace) return enlace
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(
    `No se encontró en el correo de pruebas (laravel.log) el enlace de restablecimiento para ${correo}.`,
  )
}

test.describe('Recuperar contraseña', () => {
  test('valida el correo y muestra el aviso genérico', async ({ page }) => {
    await page.goto('/recuperar-contrasena')

    await page.getByPlaceholder(/Correo electr/i).fill('no-es-correo')
    await page.getByRole('button', { name: /Enviar enlace/i }).click()
    await expect(page.getByText(/correo electrónico válido/i)).toBeVisible()

    await page.getByPlaceholder(/Correo electr/i).fill('maria.gonzalez@soy.sena.edu.co')
    await page.getByRole('button', { name: /Enviar enlace/i }).click()
    await expect(page.getByRole('heading', { name: /Revisa tu correo/i })).toBeVisible()
  })
})

test.describe('Restablecer contraseña', () => {
  test('flujo completo: enlace, nueva clave y login', async ({ page, request }) => {
    const { username, password: claveAnterior, correo } = await crearAprendiz(request)

    // Solicita el enlace; el correo de pruebas llega al log del backend.
    await page.goto('/recuperar-contrasena')
    await page.getByPlaceholder(/Correo electr/i).fill(correo)
    await page.getByRole('button', { name: /Enviar enlace/i }).click()
    await expect(page.getByRole('heading', { name: /Revisa tu correo/i })).toBeVisible()

    const enlace = await esperarEnlaceDeRecuperacion(correo)

    // Se navega en relativo para quedarse en el mismo origen (preview/E2E).
    await page.goto(enlace)
    const campos = page.locator('input[autocomplete="new-password"]')
    await campos.nth(0).fill('nuevaClave123')
    await campos.nth(1).fill('nuevaClave123')
    await page.getByRole('button', { name: /Restablecer contraseña/i }).click()

    await expect(page.getByRole('heading', { name: /Contraseña restablecida/i })).toBeVisible()

    // La nueva contraseña queda activa.
    await page.getByRole('link', { name: /Ir al login/i }).click()
    await page.waitForURL('**/login')
    await page.getByPlaceholder('Usuario').fill(username)
    await page.locator('input[type="password"]').fill('nuevaClave123')
    await page.getByRole('button', { name: /Iniciar Sesi/i }).click()
    await page.waitForURL('**/aprendiz/dashboard', { timeout: 15000 })

    // La contraseña anterior ya no es válida (contrato de credenciales).
    const rechazo = await request.post('/v1/auth/login', {
      data: { username, password: claveAnterior },
    })
    expect(rechazo.status()).toBe(422)
  })

  test('un enlace inválido avisa y ofrece pedir otro', async ({ page }) => {
    await page.goto('/restablecer-contrasena')
    await expect(page.getByRole('heading', { name: /Enlace inválido/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /Solicitar un nuevo enlace/i })).toBeVisible()
  })
})

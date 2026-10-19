// Config para las pruebas de VOLUMEN: igual que la principal pero SIN
// globalSetup (el reseed base borraría los datos de volumen que siembra
// e2e/run-volumen.mjs).
import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 60000,
  expect: { timeout: 20000 },
  fullyParallel: false,
  workers: 1,
  retries: 1,
  use: {
    baseURL: 'http://localhost:4173',
    locale: 'es-CO',
    viewport: { width: 1280, height: 800 },
    actionTimeout: 20000,
  },
  webServer: {
    command: 'npm run preview -- --strictPort',
    url: 'http://localhost:4173',
    // Nunca reutilizar un preview ya abierto: podría apuntar a otro backend
    // (proxy PROYECTWIN_API) y ejecutar la suite de volumen contra datos ajenos.
    reuseExistingServer: false,
    timeout: 60000,
  },
})

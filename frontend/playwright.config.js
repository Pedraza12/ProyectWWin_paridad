import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.js',
  timeout: 60000,
  // La app consulta la API real; el servidor de desarrollo (PHP) puede tardar
  // bajo carga, así que se da margen a acciones y aserciones.
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
    // (proxy PROYECTWIN_API) y ejecutar la suite E2E contra datos ajenos.
    reuseExistingServer: false,
    timeout: 60000,
  },
})

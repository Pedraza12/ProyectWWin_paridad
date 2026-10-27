# Informe de calidad — paridad Android/Web (27-oct-2026)

**Repositorio:** `https://github.com/Pedraza12/ProyectWWin_paridad.git`
**Rama:** `master`
**HEAD al iniciar:** `8811021` (`chore(apk): renombrar APK a ProyecTwin.apk y mantener compatibilidad`)
**Working tree inicial:** limpio (`git status --short --branch` sin cambios; sin worktrees adicionales).

## 1. Estado inicial verificado

- Remoto único `origin` → `Pedraza12/ProyectWWin_paridad`.
- `master` sincronizado con `origin/master`; no hay ramas de paridad adicionales.
- El APK versionado en `backend/public/apk/ProyecTwin.apk` se verificó con
  `aapt`/`apksigner`: `versionCode 4`, `versionName 1.3.0`, `sdk 26`,
  `targetSdk 36`, **sin** `application-debuggable` y firmado con el certificado
  del owner (`CN=Oscar`), no con la clave debug.
- El aviso de `docs/deploy-railway.md` que describía un APK debug 1.2.0 estaba
  desactualizado; se corrigió la documentación.

## 2. Archivos inspeccionados (muestra representativa)

- Navegación y roles: `Movil/.../navigation/AppNavigation.kt`, `MainActivity.kt`,
  `frontend/src/App.jsx`, `frontend/src/layouts/DashboardLayout/DashboardLayout.jsx`.
- API y DTO: `Movil/.../data/api/service/*.kt`, `.../data/api/dto/*.kt`,
  `backend/routes/api-v1.php`.
- Pantallas móviles: dashboards, fichas, similitudes, perfiles, usuarios,
  catálogos, bitácora, reportes, notificaciones y actualizaciones.
- Web de referencia: Home, MiFicha, DetalleFicha, DetalleInstructor,
  DetalleCompanero, DirectorioFichaInstructor, MiPerfil, Dashboards.
- Documentación: `AUDITORIA-INTEGRAL.md`, `HISTORIAL-FIXES.md`,
  `docs/inventario-pendientes.md`, `docs/release-android.md`,
  `docs/deploy-railway.md`, `docs/entorno-pruebas-seguro.md`.

## 3. Problemas encontrados y correcciones

| # | Problema real | Corrección |
|---|---|---|
| 1 | El Home web no tenía botón de descarga del APK | `ApkDownloadButton` en el hero del Home, enlace estático a `/apk/ProyecTWin.apk`, con `download` y sin autenticación |
| 2 | Android no tenía “Mi Instructor” | Ruta `aprendiz/instructor/{userId}` + enlace desde la tarjeta del instructor en Mi Ficha |
| 3 | El perfil de compañero mostraba la ficha del **solicitante**, no la del usuario consultado | `DetalleCompaneroViewModel` resuelve la ficha del aprendiz consultado; para instructor usa la ficha compartida |
| 4 | El perfil de compañero no permitía abrir propuestas ni mostraba similitud | Tarjetas clicables + badge de similitud máxima vigente (mismo criterio web) |
| 5 | El instructor no podía abrir el perfil de un aprendiz desde el roster | Tarjeta clicable + ruta `instructor/companero/{userId}` |
| 6 | Mi Ficha no permitía buscar compañeros | Campo de búsqueda por nombre/correo con estado “sin resultados” |
| 7 | Lint fallaba por `BoxWithConstraints` sin uso | Se reemplazó por `Box` en `SimilarityCharts.kt` |
| 8 | Aviso documental obsoleto del APK debug | `docs/deploy-railway.md` actualizado a 1.3.0 firmado (verificado) |
| 9 | Los dashboards Android de aprendiz e instructor no mostraban el bloque de métricas del web | `DashboardMetricsSection` reutilizable (KPIs de instructor, dona, evolución por periodo y actividad) integrado en ambos dashboards; datos reales de `GET /dashboard-metrics` |
| 10 | **Defecto de la web (no corregido):** el título “Propuestas por mes” se mantiene con periodos de 7/30 días | Se replica en Android para no divergir; se documenta para decisión del owner (la web es la referencia y no se modificó) |

No se modificaron dashboards, rutas ni reglas de negocio de la web fuera del
botón del Home.

## 4. Pruebas ejecutadas

### Frontend

| Comprobación | Resultado |
|---|---|
| `npm run test:unit` | **84 pruebas, 24 archivos, 0 fallos** (incluye `ApkDownloadButton.test.jsx` y `Home.test.jsx`) |
| `npm run lint` | **0 errores** |
| `npm run build` | **OK** (Vite) |
| APK público (servidor PHP local) | `HEAD /apk/ProyecTWin.apk` → **200**, `application/vnd.android.package-archive`, 14.866.767 bytes |

### Android

| Comprobación | Resultado |
|---|---|
| `gradlew testDebugUnitTest` | **192 pruebas, 29 suites, 0 fallos** (4 nuevas de `DetalleCompaneroViewModelTest`) |
| `gradlew lintDebug` | **0 errores**, 45 warnings, 2 hints |
| `gradlew assembleDebug` | **BUILD SUCCESSFUL** (`app/build/outputs/apk/debug/app-debug.apk`) |
| `gradlew assembleDebugAndroidTest` | **BUILD SUCCESSFUL** (APK de pruebas compilado; no ejecutado) |

### E2E web (Playwright)

| Comprobación | Resultado |
|---|---|
| `home-apk.spec.js` (nuevo) | **1/1 OK**: botón visible, `HEAD` 200 con MIME Android y descarga real de `ProyecTWin.apk` |
| `home-demo.spec.js` | **2/2 OK**: Home pública sin regresiones |
| Gate E2E completo (`npm run test:e2e`) | **49/58 archivos OK**; 9 archivos con fallos preexistentes ajenos al cambio del Home (detalle abajo) |

Entorno E2E: backend `--env=e2e` en `:8001`, base aislada `proyectwin_e2e`
(reseed por archivo con `E2E_ALLOW_RESET=1`). El gate tardó ~39 min y no tocó
`proyectwin_mvp`.

### Backend

| Comprobación | Resultado |
|---|---|
| `php artisan test` | **395 pruebas, 2408 aserciones, 0 fallos** (~47 s) |

Entorno backend: MySQL local aislado `proyectwin_test`, `APP_ENV=testing`,
`backend/.env.testing` local (no versionado). No se tocó la base de producción.

### Fallos E2E preexistentes (no introducidos por este cambio)

El gate completo ejecutó 13 pruebas fallidas en 9 archivos. Todos los fallos
están fuera del Home y de los archivos modificados; se reproducen al ejecutar
cada spec por separado:

| Spec | Fallo observado |
|---|---|
| `accesibilidad.spec.js` | 2 violaciones críticas/serias en dashboards de aprendiz y admin |
| `accesibilidad-oscuro.spec.js` | Las mismas 2 violaciones en modo oscuro |
| `admin-fichas.spec.js` | No encuentra el enlace de la ficha `xkp-mqwr` en el detalle de la propuesta 4 |
| `cambiar-ficha-trazabilidad.spec.js` | No encuentra `xkp-mqwr` en las propuestas del aprendiz |
| `fechas-formato.spec.js` | La bitácora no muestra filas de fecha en el seed actual |
| `hover-datatable.spec.js` | El color de la celda sticky no coincide con el de la fila (`rgb(246,246,246)` vs `rgba(245,245,245,0.62)`) |
| `intrusion.spec.js` | No aparece el texto “Propuesta A” en el detalle de similitud |
| `similitudes-vistas.spec.js` | 3 fallos de datos/rutas (`Similitud #3`, histórica 5↔15, vista sin señales) |
| `traslados-extendido.spec.js` | No encuentra `xkp-mqwr` tras el traslado |

Estos fallos ya existían en el estado del repositorio y deben tratarse por
separado; no se desactivaron reglas ni pruebas para ocultarlos.

## 5. Errores preexistentes / limitaciones

- `docs/inventario-pendientes.md` está fechado el 19-oct-2026 y no refleja los
  commits del 21-oct (dashboard admin, similitudes A/B, centro de actualizaciones
  y APK 1.3.0). Se conserva como histórico; la matriz vigente es
  `docs/paridad-android-web.md`.
- `Movil/.idea/misc.xml` referencia JBR 17; la compilación se ejecutó con JDK 21
  y funcionó.
- Warnings de lint/deprecaciones ya existentes (iconos `Logout`/`Send`,
  `LocalClipboardManager`, anotaciones Hilt). No bloquean.
- **Hallazgo web:** el título “Propuestas por mes” no cambia al seleccionar
  7/30 días. Se replicó el mismo texto en Android para conservar paridad; no se
  corrigió la web porque la única modificación autorizada era el botón del APK.
- No hay integración Quick Store en este repositorio; no se amplió el alcance.

## 6. Pruebas bloqueadas

| Prueba | Causa | Cómo desbloquear |
|---|---|---|
| Instalación y smoke en dispositivo | No hay emulador/dispositivo disponible en el entorno (`adb devices` vacío, sin AVDs ni system images) | Abrir Android Studio, crear/usar AVD API 26+ e instalar `app-debug.apk` |
| Instrumentadas (`connectedDebugAndroidTest`) | Requiere dispositivo/emulador; la tarea falla con `No connected devices!` | `gradlew connectedDebugAndroidTest` con AVD libre |
| Flujos autenticados de los 3 roles en dispositivo | Falta dispositivo y cuentas de prueba | Ver guía de validación manual |
| Fallos E2E preexistentes | 13 pruebas en 9 specs ajenos al Home; requieren revisión de seed/datos y contraste | Corregirlos en un lote separado, sin desactivar reglas |

## 7. Guía de validación manual

### Home público (sin sesión)

1. Abrir `/` en una ventana privada.
2. Verificar el botón **“Descargar APK para Android”** junto a “Iniciar sesión”.
3. Pulsarlo: debe descargar `ProyecTWin.apk` desde `/apk/ProyecTWin.apk` sin pedir login.
4. Verificar que ningún dashboard muestra ese botón.

### Android — Aprendiz

1. Instalar `app-debug.apk` (API 26+).
2. Iniciar sesión con una cuenta aprendiz.
3. Dashboard → **Mi Ficha**: buscar un compañero por nombre/correo.
4. Tocar la tarjeta del instructor → debe abrir **Mi Instructor** con su perfil y la ficha compartida.
5. Volver, tocar un compañero → perfil con propuestas y porcentaje de similitud; tocar una propuesta → detalle.
6. Abrir Similitudes → detalle A/B → observaciones.
7. Editar perfil y cambiar foto; volver a entrar y confirmar persistencia.
8. Cerrar sesión.

### Android — Instructor

1. Iniciar sesión como instructor.
2. Fichas → detalle → tocar un aprendiz del roster → perfil público; abrir una propuesta visible.
3. Revisión de propuestas → aprobar/rechazar con observación.
4. Verificar que no aparecen acciones de admin (catálogos, motor, bitácora).

### Android — Admin

1. Iniciar sesión como admin.
2. Dashboard: métricas, dona, evolución por periodo y actividad con datos reales.
3. Usuarios: paginar, filtrar, detalle, credenciales PDF.
4. Fichas → detalle con roster y propuestas.
5. Catálogos y motor de similitud: editar y verificar persistencia.
6. Bitácora y reportes: abrir detalle y cerrar un reporte.

### Actualizaciones

1. Con una versión anterior instalada, abrir la app y esperar el aviso.
2. Perfil → Actualizaciones → descargar; verificar que se valida versión/firma antes de abrir el instalador.

## 8. Estado final de Git

- Cambios **sin commit** (no se hizo commit/push por instrucción explícita).
- Archivos nuevos: `frontend/src/components/ApkDownload/` (componente + test),
  `frontend/src/pages/public/Home/Home.test.jsx`,
  `frontend/e2e/home-apk.spec.js`,
  `Movil/.../DashboardMetricsSection.kt`,
  `Movil/.../DetalleCompaneroViewModelTest.kt`, `docs/paridad-android-web.md`,
  este informe.
- Archivos modificados: Home, `vite.config.js`, navegación Android, modelo/mapper
  de ficha, FichaDetail, DetalleCompanero (VM/UI), DetalleFichaInstructor y
  `SimilarityCharts.kt` (lint).
- `backend/.phpunit.result.cache` se revirtió tras las pruebas para no versionar ruido.
- `Movil/local.properties`, `backend/.env.testing` y `backend/.env.e2e` son
  locales e ignorados por Git.

## 9. Riesgos / pendientes

1. El botón del APK usa un enlace estático; el componente comprueba el `HEAD` y
   avisa si recibe error o el fallback HTML. La descarga real se validó en E2E
   local; falta verificarla en el dominio de producción tras el despliegue.
2. No se probó en dispositivo; los flujos de perfil, ficha y similitudes están
   compilados y cubiertos por unit tests, no validados integralmente.
3. La pantalla de “Resultado del análisis” agregada sigue siendo una diferencia
   funcional (parcial) frente a la web.
4. El detalle móvil de una ficha ajena por `id` sigue ausente; se documenta en la
   matriz.
5. Quick Store no existe en el repositorio; cualquier trabajo futuro requiere
   confirmación de alcance.
6. El gate E2E completo conserva 13 fallos preexistentes en 9 specs ajenos al
   Home; están listados en la sección 4 y no se ocultaron.

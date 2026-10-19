# Entorno de pruebas seguro — ProyecTWIN MVP (sprint final, Paso 1)

> Objetivo del paso (B0 de la auditoría de fase 2): que PHPUnit y Playwright/E2E
> **no puedan borrar ni modificar** la base de datos de desarrollo (`proyectwin_mvp`).
> Las suites ahora se ejecutan **solo** contra bases dedicadas y **abortan** ante
> cualquier configuración insegura (nunca se degradan a una advertencia).

## 1. Bases de datos y entornos

| Entorno | Config que la define | Base de datos | Uso |
|---|---|---|---|
| Desarrollo | `backend/.env` | `proyectwin_mvp` | trabajo diario; las suites ya NO lo tocan |
| PHPUnit | `backend/phpunit.xml` + `backend/.env.testing` (local, **no versionado**) | `proyectwin_test` | `php artisan test` |
| E2E (Playwright) | `backend/.env.e2e` (local, **no versionado**) | `proyectwin_e2e` | suites E2E + backend servido en `:8001` |

Creación de las bases dedicadas (ya ejecutada el 18-oct-2026; este SQL se conserva para recrearlas y **requiere autorización explícita**):

```sql
CREATE DATABASE proyectwin_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE proyectwin_e2e  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

`backend/.env.testing` y `backend/.env.e2e` se generaron copiando `.env` con dos overrides
(`APP_ENV` y `DB_DATABASE`) y están **ignorados por git** (`.gitignore` ampliado en este paso).

## 2. PHPUnit

Cadena real de resolución (verificada con `php artisan config:show`, no supuesta):

1. `phpunit.xml` fija por `<env>`: `APP_ENV=testing`, `DB_CONNECTION=mysql`, `DB_HOST=127.0.0.1`, `DB_DATABASE=proyectwin_test`.
2. Laravel carga `backend/.env.testing` (existe) porque `APP_ENV=testing`; de ahí salen `APP_KEY` y credenciales locales.
3. `tests/CreatesApplication.php` aplica la **guarda dura**: aborta si `APP_ENV !== testing`, si la BD no es exactamente `proyectwin_test` o si el host no es loopback. La excepción se lanza durante el bootstrap, **antes de cualquier setUp o escritura**.

Notas:
- No debe existir `bootstrap/cache/config.php` (config cacheada ignora `.env`). Si aparece: `php artisan config:clear`.
- La suite sigue usando `DatabaseTransactions`; con la BD correcta el rollback aplica como siempre.

Ejecución:

```
cd backend
php artisan test
```

## 3. E2E (Playwright)

Guardas en capas — **todas** deben pasar:

| Capa | Qué exige | Dónde |
|---|---|---|
| 1. Runner (JS) | `E2E_ALLOW_RESET=1` y `PROYECTWIN_API` explícito y **distinto** del backend de desarrollo (`:8000`) | `frontend/e2e/guardas-e2e.mjs` (invocada por todas las entradas `test*`) |
| 2. globalSetup | Lo anterior + aborta con excepción si falta (ya no “avisa y continúa”) | `frontend/e2e/global-setup.js` |
| 3. Comando Artisan | `E2E_ALLOW_RESET=1`, existencia de `.env.e2e`, `APP_ENV=e2e`, BD exactamente `proyectwin_e2e`, host loopback y **lock** anti-reseeds concurrentes (`storage/framework/e2e-reset.lock`) | `backend/app/Console/Commands/E2eReset.php` (`php artisan e2e:reset --env=e2e`) |
| 4. Preview sin reutilización | El preview de Vite ya no se reutiliza (`reuseExistingServer: false`) para que el proxy `PROYECTWIN_API` corresponda siempre a esta corrida | `playwright.config.js`, `playwright.volumen.config.js` |

Entradas protegidas: `npm test` · `test:e2e` · `test:e2e:aislado` · `test:agentes` ·
`test:agentes:acciones` · `test:agentes:cobertura` · `test:agentes:negativos` ·
`test:volumen` · `test:full`.

Preparación (con autorización previa):

```
# 1) bases dedicadas creadas (ver SQL de la sección 1)
# 2) backend E2E en :8001 — APP_ENV se propaga al proceso hijo de `serve`
cd backend
$env:APP_ENV='e2e'
php artisan serve --env=e2e --port=8001
# 3) frontend: proxy al backend E2E + autorización del reseed
cd frontend
$env:PROYECTWIN_API='http://127.0.0.1:8001'
$env:E2E_ALLOW_RESET='1'
npm run test:e2e        # o: test:full / test:volumen / test:agentes:*
```

Advertencias:
- Nunca exportar `PROYECTWIN_API` hacia `:8000` para E2E (la capa 1 lo rechaza).
- Cerrar cualquier `npm run preview` previo (ya no se reutiliza, pero el puerto debe estar libre).
- Los workers paralelos (`--workers=4`) comparten la BD E2E **después** de un único reset; el lock del comando impide resets simultáneos entre procesos.

## 4. Cómo comprobar que la BD de desarrollo no se toca

1. Antes/después de una corrida autorizada: conteos y `CHECKSUM TABLE` de tablas clave de `proyectwin_mvp` deben ser idénticos.
2. Los logs del backend E2E deben mostrar conexión a `proyectwin_e2e`.
3. Prueba de humo de guardas: sin `E2E_ALLOW_RESET` o apuntando a `:8000`, la ejecución debe **fallar**, no advertir.

## 5. Estado de la implementación

- **Paso 1 (histórico):** quedaron implementadas las guardas PHPUnit + E2E, el comando `e2e:reset`, los entornos locales, los runners actualizados y este documento; la creación de las bases y la ejecución de las suites quedaron pendientes de autorización.
- **Ejecutado y verificado (18-oct-2026):**
  - `proyectwin_test` y `proyectwin_e2e` creadas y migradas.
  - `php artisan test` con la guarda activa: **314/314 pruebas, 1797 aserciones** (certificación histórica de ese punto — Lote 1; `proyectwin_test` limpia al terminar).
  - **Resultado posterior (misma fecha, Lote 6): 324/324 pruebas, 1883 aserciones**, tras añadir las pruebas de D-01 (borrado de usuarios con evidencia).
  - Gate E2E integral: **5/5 etapas, 347 pruebas** (53 archivos aislados + agentes/cobertura/negativos/volumen), con backend E2E en `:8001` y reseed contra `proyectwin_e2e`.
  - Los 4 specs E2E nuevos del Lote 2 (`doble-submit-completo`, `fechas-formato`, `notificacion-ficha-navegacion`, `red-evidencia-409`) se ejecutaron y aprobaron en el gate.
  - Guardas probadas en negativo: sin `E2E_ALLOW_RESET` o apuntando a `:8000` la corrida falla; PHPUnit aborta fuera de `testing` + `proyectwin_test`.
- **Requiere autorización explícita:** volver a ejecutar el gate E2E integral (un único run completo) y cualquier `e2e:reset`/migración sobre las bases dedicadas. Nota: los refuerzos del Lote 3 a 2 specs E2E de frontend (`fechas-formato` y `notificacion-ficha-navegacion`) fueron ejecutados y aprobados el 18-oct con autorización focal (5 tests autorizados + 4 adicionales aprobados por coincidencia de filtro).
- **Limitación conocida (histórico, Paso 1):** la dependencia de datos del seed de demo (p. ej. `SimilarityScopeTest`) quedó absorbida por la suite actual: no hay seeder en `phpunit.xml` ni llamadas a `seed()` en `tests/`, y `SimilarityScopeTest` limpia las similitudes preexistentes; si reaparece, se definirá un seeder de test determinista.

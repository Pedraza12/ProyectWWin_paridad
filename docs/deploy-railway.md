# Despliegue en Railway — ProyecTwin MVP

Guía del despliegue del MVP completo (**SPA React + API Laravel**) en un solo
servicio de Railway, con MySQL como base de datos.

## Arquitectura

```
Internet ── HTTPS ──► Railway (proxy TLS)
                        │
                        ▼
              ┌───────────────────────┐      ┌──────────────┐
              │  servicio "backend"   │      │    MySQL     │
              │  nginx + php-fpm      │─────►│  (plugin)    │
              │  ├─ /v1/**  → Laravel │      └──────────────┘
              │  ├─ /sanctum/**       │
              │  ├─ /up     → health  │
              │  └─ resto   → SPA     │
              └───────────────────────┘
```

**Un solo servicio** porque el SPA usa cookie de sesión httpOnly y CSRF
(`/sanctum/csrf-cookie`), con `/v1` relativo. Al compartir dominio y origen no
hay CORS ni cookies cross-site: exactamente el mismo esquema que en local con
el proxy de Vite.

## Archivos del despliegue

| Archivo | Para qué |
|---|---|
| `Dockerfile` | Compila el SPA (Vite), instala Composer y arma nginx + php-fpm |
| `docker/nginx.conf` | Rutas: API → PHP, resto → SPA con fallback `index.html`; escucha en `$PORT` |
| `docker/entrypoint.sh` | Espera MySQL, migra, siembra (opcional), cachea y arranca |
| `railway.json` | Builder Dockerfile + healthcheck `/up` |
| `.dockerignore` / `.railwayignore` | Suben solo lo necesario (sin `Movil/`, `node_modules`, `vendor`) |

## Variables de entorno (servicio web)

| Variable | Valor |
|---|---|
| `APP_NAME` | `ProyecTwin-MVP` |
| `APP_ENV` | `production` |
| `APP_DEBUG` | `false` |
| `APP_KEY` | salida de `php artisan key:generate --show` |
| `APP_URL` | `https://<dominio>.up.railway.app` |
| `APP_TIMEZONE` | `America/Bogota` |
| `LOG_CHANNEL` | `stderr` |
| `API_RATE_LIMIT` | `300` (evita throttling en la demo con varias sesiones) |
| `DB_CONNECTION` | `mysql` |
| `DB_HOST` | `${{MySQL.MYSQLHOST}}` |
| `DB_PORT` | `${{MySQL.MYSQLPORT}}` |
| `DB_DATABASE` | `${{MySQL.MYSQLDATABASE}}` |
| `DB_USERNAME` | `${{MySQL.MYSQLUSER}}` |
| `DB_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
| `SESSION_DRIVER` | `database` (las sesiones sobreviven a los redeploys) |
| `SESSION_SECURE_COOKIE` | `true` |
| `CACHE_DRIVER` | `file` |
| `QUEUE_CONNECTION` | `sync` |
| `BROADCAST_DRIVER` | `log` |
| `FILESYSTEM_DISK` | `local` |
| `SANCTUM_STATEFUL_DOMAINS` | `<dominio>.up.railway.app` (sin `https://`) |
| `SANCTUM_EXPIRATION` | `1440` |
| `FRONTEND_URL` | `https://<dominio>.up.railway.app` |
| `MAIL_MAILER` | `log` (los correos quedan en el log; la demo no envía emails) |
| `RUN_SEED` | `true` **solo el primer deploy**; luego ponlo en `false` |

> Las referencias `${{MySQL.*}}` las resuelve Railway; el servicio de la base de
> datos debe llamarse `MySQL` (o ajusta el nombre en las referencias).
> En PowerShell van entre comillas simples para que `$` no se interprete.

## Pasos (PowerShell)

```powershell
# 1. CLI de Railway
npm i -g @railway/cli

# 2. Autenticación. Con token de cuenta:
railway login --browserless     # pega el token de railway.com/account/tokens
# (o abre el navegador simplemente con: railway login)

# 3. Crear el proyecto y vincularlo
railway init -n proyecttwin

# 4. Base de datos MySQL (o desde el dashboard: New → Database → MySQL)
railway add --database mysql

# 5. Definir variables del servicio (o desde el dashboard → Variables)
#    Ejemplo con algunas:
railway variables --set 'APP_ENV=production' --set 'APP_DEBUG=false' `
                  --set 'DB_CONNECTION=mysql' `
                  --set 'DB_HOST=${{MySQL.MYSQLHOST}}' `
                  --set 'DB_PORT=${{MySQL.MYSQLPORT}}' `
                  --set 'DB_DATABASE=${{MySQL.MYSQLDATABASE}}' `
                  --set 'DB_USERNAME=${{MySQL.MYSQLUSER}}' `
                  --set 'DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}' `
                  --set 'RUN_SEED=true'

# 6. Desplegar (Railway construye el Dockerfile en la nube)
railway up

# 7. Dominio público HTTPS
railway domain
```

Tras generar el dominio, define `APP_URL`, `FRONTEND_URL` y
`SANCTUM_STATEFUL_DOMAINS` con ese valor; Railway redespliega automáticamente.

## Verificación

- `https://<dominio>.up.railway.app/up` → `{"status":"ok"}`.
- La raíz `/` carga el SPA; recargar cualquier ruta interna (p. ej.
  `/iniciar-sesion`) no da 404.
- Login con las credenciales demo (sembradas por el seeder):

| Rol | Usuario | Contraseña |
|---|---|---|
| Aprendiz | `mgonzalez` | `123456` |
| Instructor | `cruiz` | `123456` |
| Admin | `a` | `admin123` |

- Logs en vivo: `railway logs -d`.

## Después del primer deploy

1. Pon `RUN_SEED=false` (el entrypoint ya se protege sembrando solo si la base
   está vacía, pero así evitas el chequeo extra).
2. Re-deploys: `railway up` de nuevo. Las sesiones viven en la tabla
   `sessions` (`SESSION_DRIVER=database`), así que sobreviven a los redeploys.
   Si el navegador conservara un token CSRF viejo, el SPA lo renueva y
   reintenta solo ante un 419.

## Solución de problemas

| Síntoma | Causa probable |
|---|---|
| Healthcheck falla, migraciones en loop | `DB_*` mal referenciadas o MySQL aún creándose; revisa `railway logs -d` |
| `No application encryption key` | Falta `APP_KEY` |
| Login responde 419/CSRF | `SANCTUM_STATEFUL_DOMAINS` sin el dominio real (sin `https://`). El SPA renueva el token y reintenta una vez ante 419; si persiste, revisa esa variable |
| Las rutas internas dan 404 al recargar | nginx no sirvió `docker/nginx.conf`; verifica que `docker/` se haya subido |
| Error 500 al exportar PDF | Faltó alguna extensión PHP en el Dockerfile (`dom`, `mbstring`) |

## Notas

- **App Android (`Movil/`)**: no se despliega en Railway. Antes de compilar el
  APK define en `Movil/local.properties` la línea
  `proyectwin.apiBaseUrl=https://<dominio>/v1/` y reconstruye (Android Studio:
  Build → Build APK, o `gradlew.bat assembleDebug`).
- **Correo**: `MAIL_MAILER=log`. Para correos reales (recuperación de
  contraseña), configura un proveedor SMTP en las variables.
- **Costos**: el servicio y MySQL consumen del crédito del plan de Railway.

## Publicar una versión nueva del APK (aviso de actualización in-app)

La app consulta `GET /v1/app-version/android` (público) y compara versiones
numéricamente. Para publicar una versión:

1. Sube `versionCode` y `versionName` en `Movil/app/build.gradle.kts`.
2. Compila el APK: `cd Movil` → `gradlew.bat assembleDebug` (o Android Studio
   → Build → Build APK).
3. Reemplaza `backend/public/apk/proyecttwin.apk` por el APK nuevo.
4. Actualiza las variables del servicio en Railway (dashboard → Variables, o
   `railway variable set`):
   - `ANDROID_APP_VERSION=1.2.0` (versión nueva que se anuncia)
   - `ANDROID_APP_MIN_VERSION=1.0.0` (súbela solo si quieres aviso obligatorio)
   - `ANDROID_APP_ENFORCE_MINIMUM=false` (`true` = obligatoria, no posponible)
   - `ANDROID_APP_DOWNLOAD_URL=https://<dominio>/apk/proyecttwin.apk`
   - `ANDROID_APP_RELEASE_NOTES=Mejoras y ajustes`
5. `railway up` (o redeploy desde el dashboard). La app avisa como máximo una
   vez cada 6 h por instalación; "Más tarde" silencia esa versión 24 h.

**Probar en el dispositivo**: instala el APK actual (1.1.0) → sube
`ANDROID_APP_VERSION` a `1.2.0` → abre la app con sesión iniciada: aparece el
aviso (Actualizar abre el APK del servidor; Más tarde lo oculta).

**Probar el modo obligatorio**: `ANDROID_APP_ENFORCE_MINIMUM=true` con
`ANDROID_APP_MIN_VERSION` por encima de la versión instalada.

## Correo real con Brevo

El backend envía los correos (credenciales, restablecer contraseña) por la
**API HTTP de Brevo** (`MAIL_MAILER=brevo`), sin SMTP. Variables del servicio:

- `MAIL_MAILER=brevo`
- `BREVO_API_KEY=<clave API v3>` (app.brevo.com → SMTP y API → Claves API)
- `MAIL_FROM_ADDRESS=<remitente verificado en Brevo>`
- `MAIL_FROM_NAME=ProyecTwin`

Requisitos en la cuenta de Brevo:

1. **Remitente verificado** (Senders, Domains & Dedicated IPs → Senders).
2. **Seguridad por IP**: si está activa, hay que autorizar la IP de salida
   (Railway usa IPs dinámicas → se recomienda desactivar la restricción en
   `app.brevo.com/security/authorised_ips`).

Diagnóstico: si un envío falla, el detalle queda en `railway logs`
(p. ej. `401 unrecognised IP address` o `400 remitente no verificado`).

## Notas de despliegue (redeploys)

- **Builder del servicio**: el monorepo solo compila con el Dockerfile de la
  raíz. En el dashboard → servicio → *Settings → Build* deja
  **Builder: Dockerfile** y **Dockerfile Path: Dockerfile**. Así los
  redespliegues automáticos (cambio de variables en el dashboard) usan la
  imagen correcta en lugar del builder por defecto (Railpack no detecta este
  monorepo y el deploy falla).
- **Variables por CLI**: usa `--skip-deploys` y luego `railway up` para
  desplegar con los valores nuevos (así el build usa el Dockerfile).
- Un deploy fallido **no tumba** el servicio: la versión anterior sigue Online
  hasta que el nuevo deploy pase el healthcheck.

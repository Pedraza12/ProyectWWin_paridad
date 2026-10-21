# Despliegue en Railway — ProyecTwin MVP

Guía del despliegue del MVP completo (**SPA React + API Laravel**) en un solo
servicio de Railway, con MySQL como base de datos.

> **Aviso sobre el APK Android (19-oct-2026):** el APK servido actualmente en
> `/apk/ProyecTWin.apk` es un **build de pruebas (debug)** —`versionName` 1.2.0,
> `versionCode` 3, marcado `application-debuggable` y firmado con la clave
> debug—, **no** una distribución oficial. Para publicar una versión firmada
> sigue [`release-android.md`](release-android.md); `assembleDebug` no es un
> artefacto publicable.

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

- **App Android (`Movil/`)**: no se despliega en Railway. El APK servido en
  `/apk/ProyecTWin.apk` es un build de pruebas (debug) y **no** una distribución
  oficial. Para el APK de distribución define en `Movil/local.properties`
  `proyectwin.apiBaseUrl.release=https://<dominio>/v1/` y sigue el plan de firma,
  validación y publicación de [`release-android.md`](release-android.md)
  (`assembleRelease`, nunca `assembleDebug`).
- **Correo**: `MAIL_MAILER=log`. Para correos reales (recuperación de
  contraseña), configura un proveedor SMTP en las variables.
- **Costos**: el servicio y MySQL consumen del crédito del plan de Railway.

## Publicar una versión nueva del APK (solo release firmado)

> Este procedimiento solo aplica a un APK **release firmado**. El APK de pruebas
> (debug) no se publica. La firma, la validación con `aapt`/`apksigner`, el
> SHA-256 y la transición desde instalaciones debug están en
> [`release-android.md`](release-android.md).

La app consulta `GET /v1/app-version/android` (público) y compara versiones
numéricamente. Para publicar una versión:

1. Sube `versionCode` (debe ser **mayor que 3**, el actual) y `versionName` en
   `Movil/app/build.gradle.kts`.
2. Compila el APK release firmado: `cd Movil` → `.\gradlew.bat :app:assembleRelease`
   (requiere la firma configurada; ver [`release-android.md`](release-android.md)).
   Android Studio: **Build → Generate Signed App Bundle / APK → APK**.
3. Valida el APK antes de publicarlo (`aapt` sin `application-debuggable`,
   `apksigner` con v2/v3 y el certificado esperado, SHA-256). Si falla, **no lo
   publiques**.
4. Reemplaza `backend/public/apk/ProyecTWin.apk` por el APK validado.
5. Actualiza las variables del servicio en Railway (dashboard → Variables, o
   `railway variables --set ...`):
   - `ANDROID_APP_VERSION=<versionName>` (p. ej. `1.3.0`; debe coincidir con el APK)
   - `ANDROID_APP_MIN_VERSION=1.0.0` (súbela solo si quieres aviso obligatorio)
   - `ANDROID_APP_ENFORCE_MINIMUM=false` (`true` = obligatoria, no posponible)
   - `ANDROID_APP_DOWNLOAD_URL=https://<dominio>/apk/ProyecTWin.apk` (HTTPS)
   - `ANDROID_APP_RELEASE_NOTES=...`
6. `railway up` (o redeploy desde el dashboard). La app avisa como máximo una
   vez cada 6 h por instalación; "Más tarde" silencia esa versión 24 h.

**Probar en el dispositivo**: instala el APK de pruebas actual (1.2.0/versionCode
3) → sube `ANDROID_APP_VERSION` a `1.3.0` → abre la app con sesión iniciada:
aparece el aviso (Actualizar abre el APK del servidor; Más tarde lo oculta). Si
mantienes el `applicationId`, la instalación del release firmado **fallará sobre
el debug** por firma distinta: desinstala el debug (pierdes la sesión local) o
instala antes un release firmado anterior (p. ej., un 1.2.0 firmado con la misma
clave) para probar una actualización real.

**Probar el modo obligatorio**: `ANDROID_APP_ENFORCE_MINIMUM=true` con
`ANDROID_APP_MIN_VERSION` por encima de la versión instalada. Hazlo con un
release firmado ya instalado; si el dispositivo tiene el debug, el aviso será
obligatorio pero la instalación seguirá fallando por firma distinta.

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

## Fotos de perfil con Cloudinary (opcional)

Variables del servicio (si faltan, la web guarda la foto localmente como antes):

- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`
- `CLOUDINARY_FOLDER` (por defecto `proyecttwin/perfiles`)

Con las credenciales configuradas, `POST /v1/media/foto` (autenticado, multipart
`foto`, máx. 5 MB) sube la imagen al servidor y devuelve `foto_url` + `public_id`;
la web guarda esa URL en `users.foto_url`. Las fotos históricas (data URL base64)
siguen funcionando sin migración. El secreto nunca se expone al frontend ni al APK.

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

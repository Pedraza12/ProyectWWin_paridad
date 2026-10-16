# -*- coding: utf-8 -*-
"""Capítulo 5 — Instalación y configuración. Capítulo 6 — Registro técnico del desarrollo."""

FIG = lambda P, n: f"{P['fig']}\\{n}"


def escribir(M, P):
    # =====================================================================
    # CAPÍTULO 5
    # =====================================================================
    M.h1("CAPÍTULO 5. INSTALACIÓN Y CONFIGURACIÓN", salto=True)
    M.p("Este capítulo describe el procedimiento completo para poner ProyecTwin en funcionamiento desde un "
        "entorno limpio. **Todos los comandos de las secciones 5.5 a 5.11 fueron ejecutados y verificados** por "
        "la auditoría del 16/10/2026 sobre Windows con XAMPP; los comandos de contenedores y despliegue "
        "(5.12–5.13) provienen de los archivos del repositorio y se ajustan a su contenido, pero **el empaquetado "
        "Docker no pudo ejecutarse en el equipo de auditoría** (Docker no está instalado), lo que se señala donde "
        "corresponde.")

    M.h2("5.1. Requisitos previos")
    M.tabla(
        "Requisitos previos del entorno de desarrollo",
        ["Componente", "Requisito", "Verificado con"],
        [
            ("PHP", "8.1 o superior con extensiones: `pdo_mysql`, `mbstring`, `openssl`, `dom`, `tokenizer`, `xml`, `curl`, `fileinfo`, `bcmath`", "PHP 8.2.12"),
            ("Composer", "2.x", "2.10.2"),
            ("MySQL o MariaDB", "MySQL 5.7+ / MariaDB equivalente, en ejecución y accesible por TCP", "MariaDB 10.4.32 (puerto 3306)"),
            ("Node.js + npm", "20.19+/22.12+ (requisito de Vite 8); recomendado LTS reciente", "Node 26.3.1 + npm 11.16.0"),
            ("Git", "Para clonar el repositorio", "Repositorio clonado"),
            ("Navegador moderno", "Chrome/Edge/Firefox actuales", "Chromium (Playwright)"),
            ("(Opcional) Android Studio + SDK 36", "Solo para compilar la app `Movil/`", "SDK presente; compilación no ejecutada"),
            ("(Opcional) Docker", "Solo para construir el contenedor de producción", "No disponible en la auditoría"),
        ],
        fuente="Manifiestos del proyecto y entorno de la auditoría.",
        anchos=[3.4, 8.6, 4.0],
        tamano=8.5,
    )

    M.h2("5.2. Instalación de herramientas")
    M.p("**Windows (entorno verificado):** instalar XAMPP (o cualquier distribución con PHP 8.1+ y MySQL/MariaDB), "
        "Composer y Node.js LTS. En la auditoría se usó el PHP de XAMPP (`C:\\xampp\\php`) con las extensiones "
        "requeridas ya habilitadas. Verificar con:")
    M.codigo(
        "php -v\ncomposer --version\nnode -v\nnpm -v",
        archivo="Comandos de verificación (ejecutados).")
    M.p("**Linux/macOS:** usar el gestor de paquetes de la distribución (`apt`, `brew`, etc.) para PHP, Composer y "
        "Node; instalar MySQL/MariaDB por separado. Los comandos del resto del capítulo son idénticos salvo las "
        "diferencias de rutas señaladas.")

    M.h2("5.3. Obtención del código fuente")
    M.codigo(
        "git clone https://github.com/YDanB-312/ProyecTwin-MVP.git\ncd ProyecTwin-MVP",
        archivo="Repositorio remoto declarado en git (`origin`). La rama de trabajo es `master`.")

    M.h2("5.4. Estructura inicial del proyecto")
    M.p("Tras clonar, el repositorio presenta esta estructura de primer nivel (el árbol detallado está en el "
        "Anexo A):")
    M.codigo(
        "ProyecTwin-MVP/\n"
        "├── backend/          # API Laravel (composer.json, app/, routes/api-v1.php, database/, tests/)\n"
        "├── frontend/         # SPA React + Vite (package.json, src/, e2e/, public/)\n"
        "├── Movil/            # App Android Kotlin + Compose (gradle/, app/src/main/)\n"
        "├── docker/           # nginx.conf y entrypoint.sh del contenedor de producción\n"
        "├── docs/             # documentación interna (arquitectura, despliegue, guion, normativa visual)\n"
        "├── Dockerfile        # empaquetado multi-etapa del servicio web\n"
        "├── railway.json      # configuración de despliegue (healthcheck /up)\n"
        "└── AUDITORIA-INTEGRAL.md / HISTORIAL-FIXES.md   # informes internos",
        archivo="Árbol real del repositorio (16/10/2026).")

    M.h2("5.5. Instalación de dependencias")
    M.p("**Backend.** Desde `backend/`:")
    M.codigo("composer install", archivo="Ejecutado en la auditoría: descargó y descubrió paquetes sin errores.")
    M.p("**Frontend.** Desde `frontend/`:")
    M.codigo("npm install", archivo="Ejecutado en la auditoría: 227 paquetes instalados en ~32 s.")
    M.p("Resultado esperado: `backend/vendor/autoload.php` y `frontend/node_modules/` existen. Ambos directorios "
        "están excluidos del control de versiones (`.gitignore`).")

    M.h2("5.6. Variables de entorno")
    M.p("El backend usa `backend/.env` (no versionado). Crear a partir de la plantilla y generar la clave:")
    M.codigo(
        "cd backend\n"
        "copy .env.example .env        # Windows (o: cp .env.example .env)\n"
        "php artisan key:generate",
        archivo="Ejecutado en la auditoría (clave generada correctamente).")
    M.p("Variables relevantes (solo nombres y valores de plantilla; **no se publican secretos**): "
        "`APP_NAME=ProyecTwin-MVP`, `APP_URL`, `DB_CONNECTION=mysql`, `DB_HOST=127.0.0.1`, `DB_PORT=3306`, "
        "`DB_DATABASE=proyectwin_mvp`, `DB_USERNAME`, `DB_PASSWORD`, `FRONTEND_URL=http://localhost:5173`, "
        "`SANCTUM_STATEFUL_DOMAINS` (orígenes del SPA), `SANCTUM_EXPIRATION=1440`, `MAIL_MAILER=log` (en "
        "desarrollo el correo se escribe en `storage/logs/laravel.log`), `API_RATE_LIMIT`, `APP_TIMEZONE=America/Bogota`. "
        "El archivo de ejemplo incluye comentarios con las recomendaciones para producción (SMTP verificado, "
        "cookies seguras).")

    M.h2("5.7. Configuración de servicios")
    M.p("**Base de datos.** Crear la base con codificación completa (comando `mysql` de XAMPP o del servidor "
        "instalado):")
    M.codigo(
        'mysql -u root -e "CREATE DATABASE IF NOT EXISTS proyectwin_mvp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"',
        archivo="Ejecutado en la auditoría (se creó además una base separada para no alterar datos existentes).")
    M.p("Verificar que el servicio de MySQL/MariaDB esté en ejecución y que `DB_USERNAME`/`DB_PASSWORD` del `.env` "
        "tengan permisos sobre la base. En XAMPP el servicio se inició desde el panel de control; el puerto "
        "verificado fue 3306 en `127.0.0.1`.")
    M.p("**Correo (opcional).** En desarrollo no requiere configuración (`MAIL_MAILER=log`). Para envíos reales "
        "(credenciales, recuperación), configurar `MAIL_MAILER=smtp` con un proveedor y remitente verificados; "
        "sin SMTP la cuenta se crea igualmente y el error de envío queda registrado en la cuenta "
        "(`credenciales_error`).")

    M.h2("5.8. Configuración de la base de datos")
    M.bullets([
        "Ajustar `DB_DATABASE` (por defecto `proyectwin_mvp`) y credenciales en `backend/.env`.",
        "**Advertencia operativa:** las pruebas de backend usan la conexión del `.env` (no hay base de pruebas separada; `phpunit.xml` trae la configuración SQLite comentada). Ejecutar las pruebas sobre una base de desarrollo, nunca sobre producción.",
        "El esquema se reconstruye íntegramente con migraciones (§ 5.9); no se requieren scripts SQL manuales.",
    ])

    M.h2("5.9. Inicialización y carga de datos")
    M.p("Desde `backend/`:")
    M.codigo(
        "php artisan migrate:fresh --seed",
        archivo="Ejecutado en la auditoría: 18 migraciones + documento base; sembró 15 usuarios, 16 propuestas, 10 similitudes y 30 notificaciones.")
    M.p("El seeder usa el flujo real de la aplicación (incluido el motor de similitud), por lo que los datos "
        "quedan coherentes con las reglas de negocio. Para datos de volumen (paginación y filtros):")
    M.codigo("php artisan db:seed --class=VolumenSeeder", archivo="Documentado en el README del backend (opcional).")

    M.h2("5.10. Ejecución en desarrollo")
    M.p("**Terminal 1 — API** (desde `backend/`):")
    M.codigo("php artisan serve --host=127.0.0.1 --port=8000", archivo="Ejecutado y verificado en la auditoría.")
    M.p("**Terminal 2 — SPA** (desde `frontend/`):")
    M.codigo("npm run dev", archivo="Ejecutado y verificado (Vite en el puerto 5173 con proxy /v1 → :8000).")
    M.p("Abrir `http://localhost:5173`. El frontend **no tiene capa de datos simulada**: todas las pantallas "
        "consumen la API real. El proxy de Vite hacia `127.0.0.1:8000` (no `localhost:8000`) está comentado en "
        "`vite.config.js` para evitar que Windows resuelva `localhost` a IPv6 mientras `artisan serve` escucha "
        "en IPv4.")

    M.h2("5.11. Verificación del funcionamiento")
    M.p("Comprobaciones ejecutadas durante la auditoría y su resultado:")
    M.tabla(
        "Lista de verificación posterior a la instalación",
        ["Comprobación", "Procedimiento", "Resultado obtenido (16/10/2026)"],
        [
            ("API en línea", "`Invoke-WebRequest http://127.0.0.1:8000/up`", "200 — `{\"status\":\"ok\"}`"),
            ("SPA en línea", "Abrir `http://localhost:5173`", "200 — portal público cargado"),
            ("Autenticación", "Iniciar sesión con las cuentas de demostración del seeder", "Redirección al panel por rol (aprendiz/instructor/admin)"),
            ("Ciclo de datos", "Listado de propuestas y similitudes con datos del seed", "16 propuestas y 10 pares de similitud visibles según alcance"),
            ("Datos demo", "Credenciales del seeder: `mgonzalez`/`123456`, `cruiz`/`123456`, `a`/`admin123`", "Son credenciales **de demostración**; no reutilizar en entornos reales"),
            ("Suite backend", "`php artisan test` (desde `backend/`)", "244 pruebas superadas, 1.156 aserciones (35,9 s)"),
            ("Unitarias frontend", "`npm run test:unit` (desde `frontend/`)", "17 archivos, 60 pruebas superadas (12,9 s)"),
            ("Lint frontend", "`npm run lint`", "Sin hallazgos"),
        ],
        fuente="Ejecución real de la auditoría (base de datos separada `proyectwin_mvp_manual`).",
        anchos=[3.2, 6.4, 6.4],
        tamano=8.5,
    )

    M.h2("5.12. Compilación y construcción de producción")
    M.p("**SPA:** desde `frontend/`, `npm run build` genera `dist/` (estáticos optimizados) usando "
        "`VITE_API_URL=/v1` (ruta relativa) para que la API se consuma del mismo origen. El resultado se sirve "
        "con `npm run preview` (puerto 4173) para validación local.")
    M.p("**Backend:** en el contenedor se ejecuta "
        "`composer install --no-dev --no-scripts --no-interaction --prefer-dist --optimize-autoloader`, y al "
        "arrancar: `php artisan package:discover`, `php artisan config:cache`, migraciones con reintentos y, "
        "opcionalmente, `db:seed` (si `RUN_SEED=true` y la base está vacía). Estos comandos provienen de "
        "`Dockerfile` y `docker/entrypoint.sh`; **no se ejecutó el empaquetado en esta auditoría** (Docker no "
        "está disponible en el equipo), por lo que se clasifican como configuración verificada por lectura, no "
        "por ejecución.")

    M.h2("5.13. Despliegue")
    num = M.figura(FIG(P, "fig06_despliegue.png"),
                   "Topología de despliegue prevista (servicio único en Railway + MySQL gestionado)",
                   "Elaboración propia a partir de `Dockerfile`, `docker/` y `railway.json`. No ejecutado en la auditoría.", ancho_cm=16.2)
    M.p(f"La Figura {num} resume la topología prevista: un único servicio (nginx + PHP-FPM + SPA compilada) con "
        "healthcheck `GET /up`, más una base MySQL gestionada. La guía interna `docs/deploy-railway.md` describe "
        "el flujo completo (no re-ejecutado aquí). Resumen de pasos y variables (se publican solo los nombres):")
    M.numerada([
        "Crear el proyecto en Railway e instalar/enlazar la CLI (`railway login`, `railway init`).",
        "Agregar el plugin MySQL (`railway add --database mysql`).",
        "Definir variables: `APP_KEY` (obligatoria; el contenedor falla con error explícito si falta), `APP_URL`, `APP_ENV=production`, `APP_DEBUG=false`, credenciales `DB_*` del plugin, `FRONTEND_URL`, `SANCTUM_STATEFUL_DOMAINS` con el dominio real, `SESSION_DRIVER`, `API_RATE_LIMIT`, `MAIL_*` y `RUN_SEED=true` solo en el primer despliegue.",
        "Desplegar (`railway up`). El contenedor espera la base hasta 24 reintentos, migra y sirve.",
        "Verificar: `GET /up` → `{\"status\":\"ok\"}`; la raíz carga la SPA; las rutas internas no devuelven 404 (fallback SPA).",
        "Después del primer despliegue, cambiar `RUN_SEED=false` para no resembrar en cada arranque.",
    ])
    M.p("Limitaciones declaradas de este apartado: el despliegue no fue ejecutado por la auditoría; la guía "
        "interna advierte además que con `SESSION_DRIVER=file` cada redespliegue cierra las sesiones (mitigación "
        "en curso: tabla `sessions`, § 4.5.18) y que el correo real requiere configurar SMTP externo.")

    M.h2("5.14. Solución de errores frecuentes")
    M.tabla(
        "Errores frecuentes, causa y solución",
        ["Síntoma", "Causa probable", "Solución"],
        [
            ("`php` no se reconoce como comando", "PHP fuera del PATH", "Agregar la carpeta de PHP (p. ej. `C:\\xampp\\php`) al PATH o usar su ruta completa."),
            ("`composer install` falla por extensiones", "Faltan extensiones requeridas", "Habilitar `pdo_mysql`, `mbstring`, `openssl`, `dom`, `fileinfo`, `curl`, `bcmath`, `zip` en `php.ini` y reintentar."),
            ("Error de conexión a MySQL al migrar", "Servicio detenido o credenciales/`DB_DATABASE` incorrectos", "Iniciar MySQL/MariaDB; revisar `DB_*` en `backend/.env`; crear la base (§ 5.7)."),
            ("419 (CSRF) al usar la SPA", "Cookie `XSRF-TOKEN` vencida tras reinicio del backend", "Recargar la página; el cliente renueva la cookie automáticamente en la siguiente escritura (`api.js`)."),
            ("401 constante desde el navegador", "Sesión expirada o `SANCTUM_STATEFUL_DOMAINS` sin el origen del SPA", "Reiniciar sesión; revisar `SANCTUM_STATEFUL_DOMAINS` y `FRONTEND_URL`."),
            ("CORS bloqueado con API en otro origen", "Vite proxy sin usar o API y SPA en orígenes distintos sin configurar", "Usar el proxy de Vite (§ 5.10) o configurar `config/cors.php` con el origen correcto."),
            ("403 «Debe cambiar la contraseña»", "Cuenta con `must_change_password = 1` (primer ingreso)", "Completar el cambio de contraseña obligatorio o restablecer credenciales desde administración."),
            ("403 «Cuenta suspendida»", "Usuario con `estado = 0`", "Reactivar la cuenta desde el panel de administración."),
            ("422 al enviar una propuesta", "Campos mínimos incompletos o envío sin cambios (huella idéntica)", "Completar título/descripción/área/objetivos; si no hubo cambios reales, editar antes de reenviar."),
            ("La lista de propuestas no muestra datos", "Aprendiz sin ficha activa", "Unirse a una ficha con código válido; las propuestas existen pero la edición requiere ficha activa."),
            ("Correo de credenciales no llega", "`MAIL_MAILER=log` (desarrollo) o SMTP mal configurado", "Revisar `storage/logs/laravel.log`; configurar SMTP; reenviar desde administración (el error queda en `credenciales_error`)."),
            ("`npm run dev` no conecta con la API", "Backend detenido o puerto 8000 ocupado", "Verificar `php artisan serve` en `127.0.0.1:8000`; el proxy apunta a ese host/puerto."),
            ("Playwright no encuentra navegadores", "Binarios no instalados", "Ejecutar `npx playwright install` dentro de `frontend/`."),
            ("PDF de credenciales falla en el servidor", "Faltan extensiones `dom`/`mbstring` en PHP", "Habilitarlas en el entorno de despliegue (el `Dockerfile` ya las instala)."),
        ],
        fuente="Casos documentados por el proyecto y observados/prevenidos durante la auditoría.",
        anchos=[4.2, 5.6, 6.2],
        tamano=8.5,
    )

    # =====================================================================
    # CAPÍTULO 6
    # =====================================================================
    M.h1("CAPÍTULO 6. REGISTRO TÉCNICO DEL DESARROLLO Y PROCEDIMIENTOS", salto=True)

    M.h2("6.1. Organización del código fuente")
    M.p("El repositorio es un **monorepo** con tres aplicaciones y artefactos de despliegue. Cada aplicación "
        "mantiene su propio manifiesto de dependencias y su propio conjunto de pruebas:")
    M.bullets([
        "`backend/`: aplicación Laravel estándar (MVC + servicios), API bajo `/v1`.",
        "`frontend/`: aplicación Vite/React con páginas por rol, componentes compartidos y pruebas unitarias y E2E.",
        "`Movil/`: proyecto Gradle de una sola aplicación Android con arquitectura MVVM por capas (`data`, `domain`, `ui`).",
        "`docker/` y raíz: empaquetado de producción y configuración de despliegue.",
        "`docs/`: documentación interna del proyecto (útil pero parcialmente histórica; ver § 1.9).",
    ])

    M.h2("6.2. Árbol de directorios relevantes")
    M.codigo(
        "backend/\n"
        "├── app/\n"
        "│   ├── Console/Commands/        # 3 comandos artisan (perfiles:docentes, similitud:*)\n"
        "│   ├── Http/Controllers/Api/    # 15 controladores de la API\n"
        "│   ├── Http/Middleware/         # CuentaActiva, RolMiddleware, CambioContrasenaObligatorio + base\n"
        "│   ├── Models/                  # 16 modelos Eloquent (scopes de alcance)\n"
        "│   ├── Notifications/           # CredencialesUsuario, ResetPasswordNotification\n"
        "│   ├── Services/                # AltaUsuario, CredencialesCorreo, NotificacionesService, SimilitudService\n"
        "│   ├── Similarity/              # Lsa, Pasajes, Recomputador (motor)\n"
        "│   └── Support/                 # Auditoria, BorradoCascada, Credenciales, CredencialesPdf, HistorialProyecto, Included\n"
        "├── config/                      # configuración Laravel (database, sanctum, cors, auth…)\n"
        "├── database/\n"
        "│   ├── migrations/              # 18 migraciones (esquema consolidado)\n"
        "│   └── seeders/                 # DatabaseSeeder (escenario demo) y VolumenSeeder\n"
        "├── resources/views/pdf/         # plantilla Blade del PDF de credenciales\n"
        "├── routes/                      # api-v1.php (84 rutas), web.php (/ y /up), console.php\n"
        "└── tests/                       # 33 clases: 31 Feature + 2 Unit (244 métodos)\n\n"
        "frontend/src/\n"
        "├── pages/{public,aprendiz,instructor,admin}/   # 47 páginas (carpetas index.jsx + implementación)\n"
        "├── components/                  # 54 componentes .jsx (incluye guards y *Base compartidos)\n"
        "├── layouts/                     # DashboardLayout, AuthLayout, LandingLayout\n"
        "├── contexts/                    # AuthContext, ThemeContext, MotorContext\n"
        "├── constants/                   # rutas por rol, paginación, estados, contacto\n"
        "├── lib/                         # api.js (cliente HTTP), recursos.js (contratos), useApi.js\n"
        "├── tokens/index.css             # sistema de diseño (tema claro/oscuro)\n"
        "└── utils/, hooks/               # helpers, validaciones, focus-trap\n"
        "frontend/e2e/                    # 54 especificaciones E2E + runners + agentes de navegación\n\n"
        "Movil/app/src/main/java/com/example/proyectwin/\n"
        "├── data/{api,model,mapper,local,repository}/    # red (Retrofit), modelos, sesión cifrada, repositorios\n"
        "├── domain/{repository,usecase}/                 # contratos y casos de uso\n"
        "├── di/                          # módulos Hilt (red, repositorios)\n"
        "├── navigation/                  # AppNavigation (4 grafos por rol)\n"
        "└── ui/{screens,viewmodel,components,theme}/     # ~40 pantallas Compose y 22 ViewModels",
        archivo="Árbol real del repositorio (resumido; el íntegro está en el Anexo A).", ref="Árbol de código")

    M.h2("6.3. Archivos principales y sus responsabilidades")
    M.tabla(
        "Archivos clave del sistema",
        ["Archivo", "Responsabilidad"],
        [
            ("`backend/routes/api-v1.php`", "Definición de las 84 rutas de la API con sus grupos y middlewares."),
            ("`backend/app/Providers/RouteServiceProvider.php`", "Registro del prefijo `/v1` y del rate limiter `api`."),
            ("`backend/app/Http/Kernel.php`", "Grupos de middleware (web/api) y alias (`cuenta.activa`, `rol`)."),
            ("`backend/app/Services/SimilitudService.php`", "Motor de puntuación (4 señales ponderadas, normalización, pasajes)."),
            ("`backend/app/Similarity/Recomputador.php`", "Orquestación: detectar, archivar, recalcular y vigencia de pares."),
            ("`backend/app/Models/Project.php`", "Reglas de escritura (`puedeEscribir`) y scopes de visibilidad."),
            ("`backend/app/Support/Included.php`", "Inclusión controlada de relaciones por lista blanca."),
            ("`backend/app/Support/Auditoria.php`", "Escritura silenciosa de la bitácora."),
            ("`frontend/src/App.jsx`", "Enrutado completo con carga diferida y guards por rol."),
            ("`frontend/src/lib/api.js`", "Cliente HTTP: cookie, CSRF, caché 15 s, 401 global."),
            ("`frontend/src/lib/recursos.js`", "Contratos tipados por recurso de la API."),
            ("`frontend/src/layouts/DashboardLayout/DashboardLayout.jsx`", "Shell de la aplicación privada (sidebar, topnav, campana)."),
            ("`Movil/.../data/api/AuthInterceptor.kt`", "Inyección del token Bearer en cada petición."),
            ("`Movil/.../data/local/SessionManager.kt` + `TokenCipher.kt`", "Persistencia de sesión con token cifrado (Keystore)."),
            ("`Dockerfile`, `docker/entrypoint.sh`, `docker/nginx.conf`", "Empaquetado y arranque de producción."),
        ],
        fuente="Auditoría del repositorio.",
        anchos=[6.6, 9.4],
        tamano=8.5,
    )

    M.h2("6.4. Punto de entrada de la aplicación")
    M.bullets([
        "**API (web y móvil):** `backend/public/index.php` → `bootstrap/app.php` → kernel HTTP → "
        "`RouteServiceProvider` (prefijo `/v1`). En producción nginx enruta `/v1` y `/sanctum` a este archivo.",
        "**SPA:** `frontend/index.html` (script anti-parpadeo de tema + metadatos) → `frontend/src/main.jsx` "
        "(proveedores: tema, motor, router, error boundary, sesión) → `App.jsx` (rutas).",
        "**Android:** `MainActivity.kt` (NavHost y eventos de sesión) con la clase de aplicación "
        "`ProyecTwinApp` (Hilt y migración de token heredado).",
    ])

    M.h2("6.5. Rutas y navegación")
    M.p("**Backend (84 rutas):** 10 públicas (login, recuperación, catálogos, configuración del motor y demo), "
        "4 de sesión (`logout`, `me`, correo, contraseña) y 70 autenticadas; 22 de ellas exigen rol "
        "administrativo o administrativo/instructor. El catálogo íntegro con método, ruta, acción y middleware "
        "está en el Anexo B.")
    M.p("**Frontend (55 declaraciones de ruta):** 5 públicas (incluida la 404), 7 redirecciones heredadas y 43 "
        "rutas protegidas por rol. Cada página carga de forma diferida (`lazy`) bajo un guard doble: "
        "`ProtectedRoute` (sesión + rol + cambio de contraseña obligatorio) y `SafeRoute` (barrera de error con "
        "salidas de recuperación). Resumen por rol:")
    M.tabla(
        "Rutas del frontend por área",
        ["Área", "Rutas protegidas", "Ejemplos representativos"],
        [
            ("Públicas", "—", "`/` (portal), `/login`, `/recuperar-contrasena`, `/restablecer-contrasena`, 404"),
            ("Aprendiz", "14", "`/aprendiz/dashboard`, `/aprendiz/propuestas`, `/aprendiz/ficha`, `/aprendiz/similitudes`, detalles de proyecto/similitud/ficha"),
            ("Instructor", "11", "`/instructor/revision-propuestas`, `/instructor/fichas`, `/instructor/similitudes`, perfiles"),
            ("Administrador", "16", "`/admin/usuarios`, `/admin/fichas`, `/admin/proyectos`, `/admin/similitudes`, `/admin/bitacora`, `/admin/config-similitud`, `/admin/reportes-fallas`"),
            ("Compartidas", "2", "`/aprendiz/perfil-companero/:id` y `/instructor/perfil-companero/:id` (componente único)"),
        ],
        fuente="`frontend/src/App.jsx` (auditoría).",
        anchos=[2.8, 3.0, 10.2],
        tamano=8.5,
    )

    M.h2("6.6. Componentes de interfaz")
    M.p("La interfaz usa un sistema de diseño propio en dos temas (claro/oscuro) definido en "
        "`frontend/src/tokens/index.css`, con tipografías Inter/Sora y JetBrains Mono, y una regla explícita: el "
        "estado nunca depende solo del color (`docs/normativa-visual.md`). Los componentes más reutilizados:")
    M.tabla(
        "Componentes de interfaz destacados",
        ["Componente", "Función"],
        [
            ("`DataTable`", "Tabla densa con encabezado fijo y modo tarjeta en pantallas pequeñas."),
            ("`Sidebar` / `TopNav` / `GovernmentBar`", "Navegación por rol, campana de notificaciones, identidad institucional y conmutador de tema."),
            ("`AlertasBase` / `PerfilBase` / `ReportarFallaBase` / `DetalleSimilitudBase`", "Implementaciones únicas compartidas por las tres fachadas de rol (patrón `*Base`)."),
            ("`ScoreDial` + `GradeBadge` + `grade.js`", "Visualización del porcentaje de similitud y su grado S/A/B/C según el umbral vigente del motor (contexto `MotorContext`)."),
            ("`ObservacionHilo`", "Render de hilos de observaciones con autor y respuestas anidadas."),
            ("`HistorialProyecto`", "Línea de tiempo del `project_histories`."),
            ("`ProtectedRoute` / `SafeRoute` / `ErrorBoundary`", "Guardas de acceso y tolerancia a fallos de render."),
            ("`FormField`, `Input`, `Button`, `Badge`, `Modal`, `Lightbox`", "Bloques base de formularios e interacción, con accesibilidad (`aria`, foco visible, focus-trap)."),
        ],
        fuente="Auditoría del frontend.",
        anchos=[6.2, 9.8],
        tamano=8.5,
    )

    M.h2("6.7. Lógica de negocio")
    M.p("La lógica de negocio está distribuida en tres lugares claramente identificables:")
    M.numerada([
        "**Modelos** (reglas de dominio reutilizables): `Project::puedeEscribir` (regla «Classroom») y los scopes de alcance (`paraUsuario`, `paraDetalle`, `visibleDetalle`, `contraparteAprobada`, `vigentes`, `historicos`).",
        "**Controladores** (transiciones y validaciones): envío con huella y motor, revisión con observación, gestión de fichas con salvaguardas, credenciales con PDF.",
        "**Servicios y soporte** (operaciones transversales): motor (`Recomputador`, `SimilitudService`), notificaciones (`NotificacionesService`), alta institucional (`AltaUsuario`), auditoría (`Auditoria`), historial (`HistorialProyecto`) y borrado en cascada controlado (`BorradoCascada`).",
    ])
    M.p("Fragmento representativo de la regla de participación (**«Classroom»**), que gobierna cuándo un usuario "
        "puede editar, comentar o gestionar equipo:")
    M.codigo(
        "// backend/app/Models/Project.php (líneas 201-224)\n"
        "public function puedeEscribir($user): bool\n"
        "{\n"
        "    if (!$user) return false;\n"
        "    if ($user->rol === 'admin') return true;\n\n"
        "    if ($user->rol === 'instructor') {\n"
        "        $instructor = Instructor::where('id_usuario', $user->id)->first();\n"
        "        if (!$instructor) return false;\n"
        "        return (int) $this->id_instructor_asignado === (int) $instructor->id\n"
        "            || (int) optional($this->classGroup)->id_instructor === (int) $instructor->id;\n"
        "    }\n\n"
        "    $ficha = $this->classGroup;\n"
        "    if (!$ficha || $ficha->estado !== 'activo') return false;\n\n"
        "    $aprendiz = Apprentice::where('id_usuario', $user->id)->first();\n"
        "    if (!$aprendiz || (int) $aprendiz->id_class_group !== (int) $ficha->id) return false;\n\n"
        "    if ((int) $this->id_creador === (int) $user->id) return true;\n\n"
        "    return ApprenticeProject::where('id_proyecto', $this->id)\n"
        "        ->where('id_aprendiz', $aprendiz->id)\n"
        "        ->exists();\n"
        "}",
        archivo="backend/app/Models/Project.php", ref="Fragmento A")

    M.h2("6.8. Controladores, servicios, módulos y clases")
    M.tabla(
        "Controladores de la API y su responsabilidad (una línea)",
        ["Controlador", "Responsabilidad"],
        [
            ("`AuthController`", "Login (usuario o correo), logout, sesión actual, cambio de correo/contraseña y recuperación."),
            ("`GeneralUserController`", "Cuentas: CRUD administrativo, perfil público con alcance, credenciales (PDF, reenvío, restablecimiento)."),
            ("`InstructorController` / `ApprenticeController`", "Perfiles de instructor y aprendiz con alcance por ficha; unirse/salir de ficha."),
            ("`ClassGroupController`", "Fichas: crear con código servidor, roster, estados, credenciales de la ficha y borrado con salvaguardas."),
            ("`ProjectController`", "Propuestas: CRUD por rol, envío con validación/huella/motor, revisión, historial."),
            ("`SimilarityController`", "Listado y detalle con alcance, detección manual, recálculo global y demo pública."),
            ("`MotorConfigController`", "Lectura pública y actualización administrativa del umbral/ventana; resumen público."),
            ("`KnowledgeNetworkController` / `TrainingProgramController`", "Catálogos institucionales (lectura pública; escritura admin)."),
            ("`NotificationController`", "Avisos internos con reglas anti-suplantación y marcado de lectura."),
            ("`BugReportController`", "Soporte: reporte del usuario, bandeja administrativa y cierre con respuesta."),
            ("`ApprenticeProjectController`", "Equipo de la propuesta (pivote) con invariantes de ficha y estado."),
            ("`CommentController`", "Observaciones y respuestas con regla de participación."),
            ("`AuditLogController`", "Consulta de la bitácora (solo administrador; sin escritura por API)."),
        ],
        fuente="`backend/app/Http/Controllers/Api`.",
        anchos=[6.2, 9.8],
        tamano=8.5,
    )

    M.h2("6.9. Acceso y transformación de datos")
    M.bullets([
        "**Eloquent en todos los accesos:** no hay SQL crudo de negocio; los scopes de los modelos encapsulan las reglas de visibilidad y los controladores componen consultas con filtros y `?included=`.",
        "**Inclusión por lista blanca:** cada modelo declara `$allowIncluded`; `Support/Included.php` valida la ruta de relación solicitada y aplica `with()`. Las relaciones naviegan en camelCase en el JSON (`$snakeAttributes = false`).",
        "**Ocultamiento por defecto:** los campos sensibles de `GeneralUser` están en `$hidden`; solo los endpoints administrativos los revelan explícitamente (`makeVisible`) cuando la operación lo exige.",
        "**Casts:** `objetivos_especificos`, `detalles` y `detalle` se castean a arreglo; `vigente` a booleano; fechas normalizadas por mutadores (`setFechaAttribute`, `setFechaIngresoAttribute`).",
        "**Serialización de dominio:** `Similarity` ordena el par antes de guardar; `Project` expone `puedeEscribir` implícitamente vía permisos calculados en la respuesta de detalle.",
    ])

    M.h2("6.10. Procesos principales")
    M.p("**1) Envío de propuesta y detección de similitud** (flujo completo verificado en código y pruebas). El "
        "fragmento siguiente muestra el mecanismo anti-duplicados que exige cambios reales entre envíos:")
    M.codigo(
        "// backend/app/Http/Controllers/Api/ProjectController.php (líneas 504-514)\n"
        "// Huella del contenido que define la propuesta (bloquea reenvíos sin cambios).\n"
        "private function huellaContenido(Project $project): string\n"
        "{\n"
        "    return sha1(json_encode([\n"
        "        $project->titulo,\n"
        "        $project->resumen,\n"
        "        $project->palabras_clave,\n"
        "        $project->area_aplicacion,\n"
        "        $project->objetivo_general,\n"
        "        $project->objetivos_especificos,\n"
        "    ]));\n"
        "}",
        archivo="backend/app/Http/Controllers/Api/ProjectController.php", ref="Fragmento B")
    M.p("**2) Motor de similitud.** El puntaje combina cuatro señales ponderadas; si el corpus es demasiado "
        "pequeño para el análisis latente, el peso del tema se reparte entre las demás (comentario del código):")
    M.codigo(
        "// backend/app/Services/SimilitudService.php (líneas 23-44)\n"
        "// Pesos de las señales (suman 1). Si no hay LSA (corpus pequeño), el peso del\n"
        "// tema se reparte entre las demás.\n"
        "private const PESOS = [\n"
        "    'palabras' => 0.40,\n"
        "    'caracteres' => 0.20,\n"
        "    'tema' => 0.25,\n"
        "    'cobertura' => 0.15,\n"
        "];\n\n"
        "// Parámetros de BM25.\n"
        "private const BM25_K1 = 1.4;\n"
        "private const BM25_B = 0.75;\n\n"
        "// Campos de la propuesta y su peso (los tokens se repiten según el peso).\n"
        "private const PESO_CAMPOS = [\n"
        "    ['titulo', 3],\n"
        "    ['palabras_clave', 3],\n"
        "    ['objetivo_general', 2],\n"
        "    ['objetivos_especificos', 2],\n"
        "    ['area_aplicacion', 2],\n"
        "    ['resumen', 1],\n"
        "];",
        archivo="backend/app/Services/SimilitudService.php", ref="Fragmento C")
    num = M.figura(FIG(P, "fig04_motor.png"),
                   "Flujo del motor de similitud (señales, umbral y resultados vigentes/históricos)",
                   "Elaboración propia a partir de `SimilitudService`, `Recomputador` y `motor_configs`.", ancho_cm=16.0)
    M.p(f"La Figura {num} sintetiza el proceso interno del motor documentado en § 6.7–6.8.")
    M.p("**3) Autenticación y sesión.** La SPA obtiene la cookie CSRF antes de la primera escritura y mantiene "
        "la sesión en cookie httpOnly; el cliente renueva la cookie si el servidor se reinicia (evitando 419 "
        "persistentes):")
    M.codigo(
        "// frontend/src/lib/api.js (líneas 31-48)\n"
        "let csrfListo = false;\n\n"
        "// Pide (o renueva) la cookie CSRF. El Set-Cookie de esta respuesta reemplaza la\n"
        "// XSRF-TOKEN anterior, así que también recupera el caso en que el servidor se\n"
        "// reinicia (p. ej. un despliegue): la sesión muere pero el navegador conserva\n"
        "// el token viejo y toda escritura respondería 419 hasta limpiar cookies.\n"
        "async function refrescarCsrf() {\n"
        "  csrfListo = false;\n"
        "  try {\n"
        "    await fetch(URL_CSRF, {\n"
        "      credentials: \"include\",\n"
        "      headers: { Accept: \"application/json\" },\n"
        "    });\n"
        "    csrfListo = true;\n"
        "  } catch {\n"
        "    /* se reintentará en la próxima escritura */\n"
        "  }\n"
        "}",
        archivo="frontend/src/lib/api.js", ref="Fragmento D")
    M.p("**4) Calibración del corpus (administración).** `POST /v1/similarities/recalculate` purga pares "
        "obsoletos y recrea los que el umbral vigente detecte; el comando `php artisan similitud:recalcular` "
        "hace lo mismo desde consola y `similitud:calibrar` sugiere umbrales por programa con tabla de señales.")

    M.h2("6.11. Operaciones CRUD implementadas")
    M.tabla(
        "Cobertura CRUD por recurso (según rutas y controladores)",
        ["Recurso", "Crear", "Leer", "Actualizar", "Eliminar"],
        [
            ("Usuarios (`general-users`)", "Admin", "Admin / propio / perfil público", "Propio o admin (campos con lista blanca)", "Admin (con guardas)"),
            ("Redes y programas", "Admin", "Público", "Admin", "Admin (con cascada controlada)"),
            ("Instructores / aprendices", "Admin / instructor (aprendiz)", "Según alcance", "Según rol y alcance", "Admin"),
            ("Fichas (`class-groups`)", "Admin / instructor", "Según rol (la ficha propia)", "Gestor de la ficha", "Anular o eliminar según impacto"),
            ("Propuestas (`projects`)", "Aprendiz con ficha activa / admin", "Según alcance", "Según estado y rol (con historial)", "Creador o admin"),
            ("Equipo (`apprentice-projects`)", "Con permiso de escritura", "Según visibilidad", "(No aplica: se agrega/quita)", "Quitar (no al creador)"),
            ("Similitudes", "Motor / admin", "Según alcance", "Admin", "Admin"),
            ("Notificaciones", "Sistema / permitido por alcance", "Propias (admin: todas)", "Marcar leída (dueño)", "Admin"),
            ("Observaciones (`comments`)", "Con permiso de escritura", "Según alcance", "Autor o admin", "Autor o admin"),
            ("Reportes (`bug-reports`)", "Cualquier autenticado (el suyo)", "Propios / admin", "Admin (estado y respuesta)", "Admin"),
        ],
        fuente="`boot()`, `routes/api-v1.php` y controladores (auditoría).",
        anchos=[4.0, 2.8, 4.0, 3.4, 2.8],
        tamano=8.5,
    )

    M.h2("6.12. Endpoints de la API")
    M.p("La API expone **84 endpoints** bajo `/v1`. La tabla resume la distribución por recurso; el catálogo "
        "detallado (método, ruta, acción y middleware) está en el Anexo B.")
    M.tabla(
        "Distribución de endpoints por recurso",
        ["Recurso (prefijo)", "Nº", "Público", "Autenticado", "Restricción de rol"],
        [
            ("`/auth` (login, recuperación, sesión)", "7", "3", "4", "—"),
            ("`/general-users` (cuentas y credenciales)", "11", "—", "11", "Escritura total y credenciales: admin"),
            ("`/knowledge-networks` y `/training-programs`", "10", "4 (lectura)", "6", "Escritura: admin"),
            ("`/instructors` y `/apprentices`", "12", "—", "12", "Alta/edición según rol; me/ficha solo aprendiz"),
            ("`/class-groups` (fichas)", "7", "—", "7", "Creación por admin/instructor; credenciales admin"),
            ("`/projects` (propuestas)", "7", "—", "7", "Todos según alcance"),
            ("`/similarities`", "7", "1 (demo)", "6", "Recálculo y CRUD manual: admin"),
            ("`/notifications`", "5", "—", "5", "Según alcance del aviso"),
            ("`/bug-reports` (soporte)", "5", "—", "5", "Responder: admin"),
            ("`/apprentice-projects` (equipo)", "4", "—", "4", "Permiso de escritura sobre la propuesta"),
            ("`/comments` (observaciones)", "5", "—", "5", "Permiso de escritura sobre la propuesta"),
            ("`/config-similitud` y `/public/*`", "4", "3", "1", "Actualización: admin"),
        ],
        fuente="`backend/routes/api-v1.php` (auditoría). Los totales por fila incluyen duplicados de verbos por recurso.",
        anchos=[5.6, 1.2, 1.8, 2.4, 5.0],
        tamano=8.5,
    )

    M.h2("6.13. Autenticación y manejo de sesiones")
    M.p("El sistema ofrece **dos mecanismos sobre la misma API**: (a) cookie de sesión httpOnly con CSRF para "
        "la SPA servida en el mismo origen, y (b) token Bearer Sanctum para la app Android y las pruebas. En "
        "ambos casos, la cadena de middleware aplica `auth:sanctum` → `cuenta.activa` → "
        "`CambioContrasenaObligatorio` → `rol:*` (cuando la ruta lo exige). La sesión de la SPA se apoya en el "
        "grupo `EnsureFrontendRequestsAreStateful` de Sanctum; el móvil guarda su token cifrado en el "
        "dispositivo (AES-256-GCM con Android Keystore) y lo adjunta en cada petición mediante "
        "`AuthInterceptor`. El detalle de seguridad está en el Capítulo 7.")

    M.h2("6.14. Gestión de errores")
    M.tabla(
        "Errores del backend y tratamiento en la interfaz",
        ["Código", "Significado en este sistema", "Ejemplo", "Tratamiento en el frontend"],
        [
            ("401", "Sin sesión o token inválido", "Cookie expirada", "Evento `auth:expirada` → limpieza de sesión y redirección a login."),
            ("403", "Sin permiso, cuenta suspendida o cambio de contraseña pendiente", "Instructor sobre ficha ajena", "Mensaje de solo lectura o redirección a `/cambio-obligatorio`."),
            ("404", "Recurso no encontrado", "Código de ficha inexistente", "Estado vacío con mensaje del backend."),
            ("409", "Conflicto de invariantes", "Quitar al creador del equipo; último admin", "Alerta con el mensaje del servidor."),
            ("422", "Validación de negocio o de campos", "Reenvío sin cambios; campos mínimos", "Errores por campo (`toFieldErrors`) o alerta general."),
            ("419", "CSRF expirado (SPA)", "Reinicio del backend", "Renovación automática de la cookie y reintento en la próxima escritura."),
            ("502", "Fallo de servicio externo", "Envío de correo de credenciales", "Aviso con opción de reenvío; el error queda en la cuenta."),
        ],
        fuente="Código de controladores, `api.js` y pruebas del proyecto.",
        anchos=[1.4, 5.0, 4.0, 5.6],
        tamano=8.5,
    )
    M.p("En la interfaz, cualquier fallo de render dentro de una página protegida lo captura `SafeRoute` "
        "(`ErrorBoundary`) con opciones de volver al panel o reportar una falla; las páginas no se quedan en "
        "pantalla blanca. La auditoría interna encontró y documentó casos históricos de errores silenciados en "
        "acciones destructivas (corregidos en el cierre de auditoría; ver § 7.12).")

    M.h2("6.15. Comandos frecuentes")
    M.tabla(
        "Comandos de operación y desarrollo",
        ["Contexto", "Comando", "Para qué sirve"],
        [
            ("Backend", "`php artisan serve`", "Servidor de desarrollo en el puerto 8000."),
            ("Backend", "`php artisan migrate:fresh --seed`", "Reconstruir esquema y datos demo (destructivo)."),
            ("Backend", "`php artisan test`", "Suite completa de pruebas (MySQL del `.env`)."),
            ("Backend", "`php artisan similitud:recalcular`", "Recalibrar el corpus con el umbral vigente."),
            ("Backend", "`php artisan similitud:calibrar`", "Sugerir umbral por programa (con etiquetas opcionales)."),
            ("Backend", "`php artisan perfiles:docentes`", "Crear perfiles de instructor faltantes."),
            ("Backend", "`php artisan db:seed --class=VolumenSeeder`", "Cargar datos de volumen para probar listados."),
            ("Frontend", "`npm run dev`", "Servidor de desarrollo con proxy a la API."),
            ("Frontend", "`npm run build` / `npm run preview`", "Build de producción y servidor de vista previa (4173)."),
            ("Frontend", "`npm run test:unit`", "Pruebas unitarias (Vitest)."),
            ("Frontend", "`npm run lint`", "Análisis estático (ESLint)."),
            ("Frontend", "`npm test` / `npm run test:e2e`", "E2E de Playwright (reseedea la base antes de cada archivo)."),
            ("Frontend", "`npm run test:full`", "Gate completo E2E (aislado + agentes + cobertura + negativos + volumen)."),
            ("Móvil", "`gradlew assembleDebug` (desde `Movil/`)", "Compilar APK de depuración (no ejecutado en la auditoría)."),
        ],
        fuente="README de backend y frontend; verificado lo ejecutado en la auditoría.",
        anchos=[2.2, 6.0, 7.8],
        tamano=8.5,
    )

    M.h2("6.16. Convenciones de nombres")
    M.tabla(
        "Convenciones observadas en el código",
        ["Ámbito", "Convención", "Ejemplo"],
        [
            ("Tablas y columnas (BD)", "snake_case en español", "`class_groups.numero`, `id_instructor_asignado`"),
            ("Modelos Eloquent", "PascalCase singular", "`ClassGroup`, `ProjectHistory`"),
            ("Controladores", "PascalCase + sufijo Controller", "`SimilarityController`"),
            ("Rutas de la API", "kebab-case en plural", "`/v1/class-groups/{id}/aprendices`"),
            ("Scopes de modelos", "camelCase con prefijo `scope`", "`scopeParaUsuario`, `scopeVigentes`"),
            ("Páginas del frontend", "Carpeta PascalCase con `index.jsx` de reexportación", "`pages/aprendiz/Propuestas/Propuestas.jsx`"),
            ("Componentes React", "PascalCase por carpeta", "`components/DataTable/DataTable.jsx`"),
            ("Hooks y utilidades", "`use*` / camelCase", "`useApi`, `formatearFecha`"),
            ("Pivotes y pivote N:M", "snake_case singular compuesto", "`apprentice_projects`"),
            ("Commits (Git)", "prefijos tipo Convencional (`feat`, `fix`, `refactor`, `docs`, `style`)", "`fix: completar cierre definitivo del MVP web`"),
        ],
        fuente="Revisión de código y `git log`.",
        anchos=[3.4, 6.2, 6.4],
        tamano=8.5,
    )

    M.h2("6.17. Control de versiones")
    M.bullets([
        "**Herramienta:** Git sobre GitHub (`origin`: `https://github.com/YDanB-312/ProyecTwin-MVP.git`), rama principal `master` (única).",
        "**Historial:** 46 commits; la actividad se concentra entre septiembre y octubre de 2026. Últimos hitos registrados: consolidación de migraciones (`eeff159`), normalización de controladores (`54e184f`), móvil F1–F6 (`09abd95`), documentación de auditoría (`b6dab04`) y normalización responsiva (`3b15f3f`, 13/10/2026).",
        "**Autores:** dos identidades de commit («Oscar» y «YDanB-312»). No hay ramas de características ni etiquetas (tags) de versión.",
        "**Estado del árbol al cierre de la auditoría:** existían cambios sin confirmar (archivos de despliegue nuevos —`Dockerfile`, `docker/`, `railway.json`—, ajustes en `TrustProxies`, `routes/web.php` y `frontend/src/lib/api.js`, y la migración `sessions`). Se documentan como trabajo en curso; **el manual describe el árbol de trabajo completo**, señalando donde algo aún no está confirmado en Git.",
        "**Recomendación:** antes de la entrega, confirmar (commit) el trabajo en curso, etiquetar la versión entregada y mantener la rama `master` desplegable en todo momento.",
    ])

    M.h2("6.18. Procedimiento recomendado para modificar y mantener el código")
    M.p("**Flujo general sugerido** (alineado con las herramientas que el proyecto ya usa):")
    M.numerada([
        "Crear una rama descriptiva (`feat/…`, `fix/…`) desde `master`.",
        "Implementar el cambio respetando las convenciones (§ 6.16) y las reglas de dominio existentes.",
        "Backend: ejecutar `php artisan test` (base de desarrollo); Frontend: `npm run test:unit`, `npm run lint` y, cuando aplique, los E2E (`npm test`).",
        "Si el cambio altera el esquema: nueva migración (nunca editar migraciones ya aplicadas en producción) y verificación con `migrate:fresh --seed` en local.",
        "Si toca el motor: actualizar pruebas de `SimilitudServiceTest` y recalibrar (`similitud:recalibrar`) en un entorno con datos representativos.",
        "Ejecutar `npm run build` antes de desplegar; confirmar que el contenedor arranca y `/up` responde.",
        "Confirmar el cambio con mensaje convencional y actualizar la documentación afectada (incluido este manual cuando corresponda).",
    ])
    M.p("**Recetas puntuales verificadas contra la estructura real:**")
    M.bullets([
        "**Agregar un endpoint:** crear ruta en `backend/routes/api-v1.php` dentro del grupo adecuado (autenticado y rol), implementar el método en el controlador con validación y scopes de alcance, y añadir la prueba Feature correspondiente.",
        "**Agregar una página web:** crear la carpeta en `frontend/src/pages/<rol>/…` con `Nombre.jsx` e `index.jsx`, declarar la ruta con `ProtectedRoute allowedRoles=[…]` + `SafeRoute` en `App.jsx`, y añadir el enlace al `Sidebar` y `Footer` según el rol.",
        "**Cambiar el umbral del motor:** hacerlo desde `/admin/config-similitud` (queda auditado) y ejecutar el recálculo; no editar filas de `motor_configs` directamente en producción.",
        "**Cambiar reglas de visibilidad:** modificar el scope del modelo afectado (p. ej. `scopeParaUsuario`) y actualizar las pruebas de alcance (`VisibilityTest`, `SimilarityScopeTest`) para conservar la cobertura.",
    ])

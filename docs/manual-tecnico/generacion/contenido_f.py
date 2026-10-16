# -*- coding: utf-8 -*-
"""Referencias bibliográficas (APA 7) y anexos."""

FIG = lambda P, n: f"{P['fig']}\\{n}"


def _endpoints(M):
    """Anexo B: catálogo completo de los 84 endpoints."""
    publicas = [
        ("POST", "/v1/auth/login", "AuthController@login", "—"),
        ("POST", "/v1/auth/forgot-password", "AuthController@forgotPassword", "—"),
        ("POST", "/v1/auth/reset-password", "AuthController@resetPassword", "—"),
        ("GET", "/v1/knowledge-networks", "KnowledgeNetworkController@index", "—"),
        ("GET", "/v1/knowledge-networks/{id}", "KnowledgeNetworkController@show", "—"),
        ("GET", "/v1/training-programs", "TrainingProgramController@index", "—"),
        ("GET", "/v1/training-programs/{id}", "TrainingProgramController@show", "—"),
        ("GET", "/v1/config-similitud", "MotorConfigController@show", "—"),
        ("GET", "/v1/public/resumen", "MotorConfigController@resumen", "—"),
        ("POST", "/v1/public/demo-similitud", "SimilarityController@demo", "CSRF exento"),
    ]
    sesion = [
        ("POST", "/v1/auth/logout", "AuthController@logout", "auth:sanctum + cuenta.activa"),
        ("GET", "/v1/auth/me", "AuthController@me", "auth:sanctum + cuenta.activa"),
        ("PUT", "/v1/auth/email", "AuthController@changeEmail", "auth:sanctum + cuenta.activa"),
        ("PUT", "/v1/auth/password", "AuthController@changePassword", "auth:sanctum + cuenta.activa"),
    ]
    auth_admin = "AUTH + rol:admin"
    auth_ai = "AUTH + rol:admin,instructor"
    auth_ap = "AUTH + rol:aprendiz"
    autenticadas = [
        ("PUT", "/v1/config-similitud", "MotorConfigController@update", auth_admin),
        ("GET", "/v1/audit-logs", "AuditLogController@index", auth_admin),
        ("GET", "/v1/general-users", "GeneralUserController@index", auth_admin),
        ("POST", "/v1/general-users", "GeneralUserController@store", auth_admin),
        ("POST", "/v1/general-users/credenciales", "GeneralUserController@credenciales", auth_admin),
        ("POST", "/v1/general-users/{id}/credenciales/reenviar", "GeneralUserController@reenviarCredenciales", auth_admin),
        ("POST", "/v1/general-users/{id}/credenciales/restablecer", "GeneralUserController@restablecerCredenciales", auth_admin),
        ("GET", "/v1/general-users/{id}/perfil", "GeneralUserController@perfil", "AUTH"),
        ("GET", "/v1/general-users/{id}", "GeneralUserController@show", "AUTH"),
        ("PUT", "/v1/general-users/{id}", "GeneralUserController@update", "AUTH"),
        ("DELETE", "/v1/general-users/{id}", "GeneralUserController@destroy", auth_admin),
        ("POST", "/v1/knowledge-networks", "KnowledgeNetworkController@store", auth_admin),
        ("PUT", "/v1/knowledge-networks/{id}", "KnowledgeNetworkController@update", auth_admin),
        ("DELETE", "/v1/knowledge-networks/{id}", "KnowledgeNetworkController@destroy", auth_admin),
        ("POST", "/v1/training-programs", "TrainingProgramController@store", auth_admin),
        ("PUT", "/v1/training-programs/{id}", "TrainingProgramController@update", auth_admin),
        ("DELETE", "/v1/training-programs/{id}", "TrainingProgramController@destroy", auth_admin),
        ("GET", "/v1/instructors", "InstructorController@index", "AUTH"),
        ("GET", "/v1/instructors/{id}", "InstructorController@show", "AUTH"),
        ("PUT", "/v1/instructors/{id}", "InstructorController@update", "AUTH + rol:admin,instructor"),
        ("POST", "/v1/instructors", "InstructorController@store", auth_admin),
        ("DELETE", "/v1/instructors/{id}", "InstructorController@destroy", auth_admin),
        ("GET", "/v1/apprentices", "ApprenticeController@index", "AUTH"),
        ("GET", "/v1/apprentices/me/ficha/codigo/{codigo}", "ApprenticeController@fichaPorCodigo", auth_ap),
        ("POST", "/v1/apprentices/me/ficha", "ApprenticeController@unirmeAFicha", auth_ap),
        ("DELETE", "/v1/apprentices/me/ficha", "ApprenticeController@salirDeFicha", auth_ap),
        ("GET", "/v1/apprentices/{id}", "ApprenticeController@show", "AUTH"),
        ("POST", "/v1/apprentices", "ApprenticeController@store", auth_ai),
        ("PUT", "/v1/apprentices/{id}", "ApprenticeController@update", auth_ai),
        ("DELETE", "/v1/apprentices/{id}", "ApprenticeController@destroy", auth_admin),
        ("GET", "/v1/class-groups", "ClassGroupController@index", "AUTH"),
        ("GET", "/v1/class-groups/{id}/credenciales", "ClassGroupController@credenciales", auth_admin),
        ("GET", "/v1/class-groups/{id}", "ClassGroupController@show", "AUTH"),
        ("POST", "/v1/class-groups", "ClassGroupController@store", auth_ai),
        ("POST", "/v1/class-groups/{id}/aprendices", "ClassGroupController@agregarAprendiz", auth_ai),
        ("PUT", "/v1/class-groups/{id}", "ClassGroupController@update", auth_ai),
        ("DELETE", "/v1/class-groups/{id}", "ClassGroupController@destroy", auth_ai),
        ("GET", "/v1/projects", "ProjectController@index", "AUTH"),
        ("POST", "/v1/projects", "ProjectController@store", "AUTH"),
        ("POST", "/v1/projects/{id}/enviar", "ProjectController@enviar", "AUTH"),
        ("GET", "/v1/projects/{id}/historial", "ProjectController@historial", "AUTH"),
        ("GET", "/v1/projects/{id}", "ProjectController@show", "AUTH"),
        ("PUT", "/v1/projects/{id}", "ProjectController@update", "AUTH"),
        ("DELETE", "/v1/projects/{id}", "ProjectController@destroy", "AUTH"),
        ("POST", "/v1/similarities/detect", "SimilarityController@detect", "AUTH"),
        ("GET", "/v1/similarities", "SimilarityController@index", "AUTH"),
        ("GET", "/v1/similarities/{id}", "SimilarityController@show", "AUTH"),
        ("POST", "/v1/similarities/recalculate", "SimilarityController@recalculate", auth_admin),
        ("POST", "/v1/similarities", "SimilarityController@store", auth_admin),
        ("PUT", "/v1/similarities/{id}", "SimilarityController@update", auth_admin),
        ("DELETE", "/v1/similarities/{id}", "SimilarityController@destroy", auth_admin),
        ("GET", "/v1/notifications", "NotificationController@index", "AUTH"),
        ("POST", "/v1/notifications", "NotificationController@store", "AUTH"),
        ("GET", "/v1/notifications/{id}", "NotificationController@show", "AUTH"),
        ("PUT", "/v1/notifications/{id}", "NotificationController@update", "AUTH"),
        ("DELETE", "/v1/notifications/{id}", "NotificationController@destroy", "AUTH"),
        ("GET", "/v1/bug-reports", "BugReportController@index", "AUTH"),
        ("POST", "/v1/bug-reports", "BugReportController@store", "AUTH"),
        ("GET", "/v1/bug-reports/{id}", "BugReportController@show", "AUTH"),
        ("PUT", "/v1/bug-reports/{id}", "BugReportController@update", "AUTH"),
        ("DELETE", "/v1/bug-reports/{id}", "BugReportController@destroy", "AUTH"),
        ("GET", "/v1/apprentice-projects", "ApprenticeProjectController@index", "AUTH"),
        ("POST", "/v1/apprentice-projects", "ApprenticeProjectController@store", "AUTH"),
        ("GET", "/v1/apprentice-projects/{id}", "ApprenticeProjectController@show", "AUTH"),
        ("DELETE", "/v1/apprentice-projects/{id}", "ApprenticeProjectController@destroy", "AUTH"),
        ("GET", "/v1/comments", "CommentController@index", "AUTH"),
        ("POST", "/v1/comments", "CommentController@store", "AUTH"),
        ("GET", "/v1/comments/{id}", "CommentController@show", "AUTH"),
        ("PUT", "/v1/comments/{id}", "CommentController@update", "AUTH"),
        ("DELETE", "/v1/comments/{id}", "CommentController@destroy", "AUTH"),
    ]
    filas = [(*e, "Pública") for e in publicas] + [(*e, "Sesión") for e in sesion] + [(*e, "Autenticada") for e in autenticadas]
    M.tabla(
        "Catálogo completo de endpoints de la API (84 rutas)",
        ["Método", "Ruta", "Acción", "Middleware (además del grupo api)", "Ámbito"],
        filas,
        fuente="`backend/routes/api-v1.php` (auditoría 16/10/2026). AUTH = auth:sanctum + cuenta.activa + CambioContrasenaObligatorio.",
        anchos=[1.4, 4.5, 4.2, 3.7, 2.2],
        tamano=7.5,
    )


def escribir(M, P):
    # =====================================================================
    # REFERENCIAS
    # =====================================================================
    M.h1("REFERENCIAS BIBLIOGRÁFICAS", salto=True)
    M.p("Fuentes consultadas durante la auditoría y la elaboración del manual (normas APA, séptima edición). "
        "Solo se listan fuentes efectivamente consultadas entre el 5 y el 16 de octubre de 2026; las normas se "
        "verificaron directamente en el catálogo oficial de ISO el 16/10/2026.")

    M.h3("Normas técnicas (ISO)")
    refs_iso = [
        "International Organization for Standardization. (2022). *ISO/IEC 27001:2022. Information security, cybersecurity and privacy protection — Information security management systems — Requirements* (Ed. 3; Amd 1:2024). https://www.iso.org/standard/27001",
        "International Organization for Standardization. (2026). *ISO 14001:2026. Environmental management systems — Requirements with guidance for use* (Ed. 4). https://www.iso.org/standard/14001",
        "International Organization for Standardization. (2023). *ISO/IEC 25010:2023. Systems and software engineering — Systems and software Quality Requirements and Evaluation (SQuaRE) — Product quality model* (Ed. 2). https://www.iso.org/standard/78176.html",
        "International Organization for Standardization. (2024). *ISO/IEC 25010:2011 (retirada el 04/03/2024; reemplazada por ISO/IEC 25002:2024, ISO/IEC 25010:2023 e ISO/IEC 25019:2023)*. https://www.iso.org/standard/35733.html",
        "International Organization for Standardization. (2012). *ISO/IEC 9126-1:2001. Software engineering — Product quality — Part 1: Quality model (retirada el 13/09/2012; revisada por ISO/IEC 25010)*. https://www.iso.org/standard/22749.html",
        "International Organization for Standardization. (2025). *ISO 37001:2025. Anti-bribery management systems — Requirements with guidance for use* (Ed. 2). https://www.iso.org/standard/37001",
        "International Organization for Standardization. (2026). *ISO 9001:2026. Quality management systems — Requirements*. https://www.iso.org/standard/9001",
        "International Organization for Standardization. (2026). *ISO 9001:2015 (retirada el 16/09/2026; reemplazada por ISO 9001:2026)*. https://www.iso.org/standard/62085.html",
        "International Organization for Standardization. (2015). *ISO/IEC 33002:2015. Information technology — Process assessment — Requirements for performing process assessment*. https://www.iso.org/standard/54176.html",
        "International Organization for Standardization. (2026). *ISO/IEC 27000:2026. Information security, cybersecurity and privacy protection — Information security management systems — Overview* (Ed. 6). https://www.iso.org/standard/27000",
        "International Organization for Standardization. (2026). *ISO/TC 176/SC 2 — Quality systems: catálogo de normas publicadas y retiradas* (consulta del estado de ISO 10005). https://www.iso.org/committee/53896.html",
    ]
    M.bullets(refs_iso)

    M.h3("Documentación oficial de las tecnologías")
    refs_tec = [
        "Laravel. (2026). *Laravel 10.x Documentation*. https://laravel.com/docs/10.x",
        "Meta Open Source. (2026). *React — Quick Start (documentación oficial)*. https://react.dev/learn",
        "VoidZero Inc. y colaboradores de Vite. (2026). *Vite — Getting Started (documentación de la versión 8)*. https://vite.dev/guide/",
        "The PHP Group. (2026). *Supported Versions (ciclo de vida de las ramas de PHP)*. https://www.php.net/supported-versions.php",
        "Microsoft. (2026). *Playwright — Installation (documentación oficial)*. https://playwright.dev/docs/intro",
    ]
    M.bullets(refs_tec)

    M.h3("Documentos internos del proyecto (fuentes primarias)")
    refs_int = [
        "ProyecTwin. (2026). *README del backend* [backend/README.md]. https://github.com/YDanB-312/ProyecTwin-MVP",
        "ProyecTwin. (2026). *README del frontend* [frontend/README.md]. https://github.com/YDanB-312/ProyecTwin-MVP",
        "ProyecTwin. (2026). *Arquitectura y recorrido de una petición* [docs/arquitectura.md]. https://github.com/YDanB-312/ProyecTwin-MVP",
        "ProyecTwin. (2026). *Guía de despliegue en Railway* [docs/deploy-railway.md]. https://github.com/YDanB-312/ProyecTwin-MVP",
        "ProyecTwin. (2026). *Normativa visual Web + Android* [docs/normativa-visual.md]. https://github.com/YDanB-312/ProyecTwin-MVP",
        "ProyecTwin. (2026). *Informe de auditoría integral del MVP* [AUDITORIA-INTEGRAL.md, 05/10/2026]. https://github.com/YDanB-312/ProyecTwin-MVP",
        "ProyecTwin. (2026). *Historial de correcciones* [HISTORIAL-FIXES.md]. https://github.com/YDanB-312/ProyecTwin-MVP",
        "ProyecTwin. (2026). *Guion de presentación* [docs/guion-presentacion.md]. https://github.com/YDanB-312/ProyecTwin-MVP",
    ]
    M.bullets(refs_int)
    M.p("Nota: las fechas entre corchetes corresponden a la fecha del documento interno cuando consta; el "
        "contenido se verificó contra el árbol de trabajo del 16/10/2026 y las divergencias se señalan en el "
        "Anexo F.")

    # =====================================================================
    # ANEXOS
    # =====================================================================
    M.h1("ANEXO A. ÁRBOL DE ARCHIVOS RELEVANTES", salto=True)
    M.p("Árbol resumido del repositorio (se excluyen dependencias instaladas, artefactos de build y archivos "
        "temporales). El estado corresponde al árbol de trabajo del 16/10/2026.")
    M.codigo(
        "ProyecTwin-MVP/\n"
        "├── backend/\n"
        "│   ├── app/\n"
        "│   │   ├── Console/Commands/  (BackfillInstructorProfiles, SimilitudCalibrar, SimilitudRecalcular)\n"
        "│   │   ├── Http/\n"
        "│   │   │   ├── Controllers/Controller.php\n"
        "│   │   │   ├── Controllers/Api/  (15 controladores: Auth, GeneralUser, Instructor, Apprentice,\n"
        "│   │   │   │                    ClassGroup, Project, Similarity, MotorConfig, KnowledgeNetwork,\n"
        "│   │   │   │                    TrainingProgram, Notification, BugReport, ApprenticeProject,\n"
        "│   │   │   │                    Comment, AuditLog)\n"
        "│   │   │   ├── Middleware/  (Authenticate, CambioContrasenaObligatorio, CuentaActiva,\n"
        "│   │   │   │                RolMiddleware, EncryptCookies, RedirectIfAuthenticated, TrimStrings,\n"
        "│   │   │   │                TrustHosts, TrustProxies, ValidateSignature, VerifyCsrfToken)\n"
        "│   │   │   └── Kernel.php\n"
        "│   │   ├── Models/  (Admin, Apprentice, ApprenticeProject, AuditLog, BugReport, ClassGroup,\n"
        "│   │   │             Comment, GeneralUser, Instructor, KnowledgeNetwork, MotorConfig,\n"
        "│   │   │             Notification, Project, ProjectHistory, Similarity, TrainingProgram)\n"
        "│   │   ├── Notifications/  (CredencialesUsuario, ResetPasswordNotification)\n"
        "│   │   ├── Providers/  (AppServiceProvider, AuthServiceProvider, EventServiceProvider,\n"
        "│   │   │                RouteServiceProvider)\n"
        "│   │   ├── Services/  (AltaUsuario, CredencialesCorreo, NotificacionesService, SimilitudService)\n"
        "│   │   ├── Similarity/  (Lsa, Pasajes, Recomputador)\n"
        "│   │   └── Support/  (Auditoria, BorradoCascada, Credenciales, CredencialesPdf,\n"
        "│   │                 HistorialProyecto, Included)\n"
        "│   ├── config/  (app, auth, cache, cors, database, filesystems, hashing, logging, mail, queue,\n"
        "│   │             sanctum, services, session, view)\n"
        "│   ├── database/\n"
        "│   │   ├── migrations/  (18 archivos, 2026_04_09 … 2026_10_16)\n"
        "│   │   └── seeders/  (DatabaseSeeder, VolumenSeeder)\n"
        "│   ├── public/index.php\n"
        "│   ├── resources/views/pdf/credenciales.blade.php\n"
        "│   ├── routes/  (api-v1.php, console.php, web.php)\n"
        "│   ├── tests/  (Feature/ 31 archivos, Unit/ 2 archivos)\n"
        "│   ├── composer.json / composer.lock / .env.example / phpunit.xml\n"
        "│   └── README.md / README-ALCANCE.md\n"
        "├── frontend/\n"
        "│   ├── e2e/  (54 especificaciones *.spec.js, runners y agentes)\n"
        "│   ├── public/  (images/Logo-ProyecTwin.png, images/logo-sena-blanco.png, manifest.webmanifest)\n"
        "│   ├── src/\n"
        "│   │   ├── App.jsx / main.jsx / index.css\n"
        "│   │   ├── components/  (54 componentes + guards, con *Base compartidos)\n"
        "│   │   ├── constants/ contexts/ hooks/ layouts/ lib/ pages/ tokens/ utils/\n"
        "│   │   └── test/setup.js\n"
        "│   ├── docs/ALINEACION-FRONTEND-BACKEND.md / CHANGELOG.md\n"
        "│   ├── index.html / package.json / package-lock.json\n"
        "│   ├── vite.config.js / vitest.config.js / playwright.config.js / playwright.volumen.config.js\n"
        "│   └── README.md / .env.example\n"
        "├── Movil/\n"
        "│   ├── app/src/main/java/com/example/proyectwin/\n"
        "│   │   ├── MainActivity.kt / ProyecTwinApp.kt\n"
        "│   │   ├── data/api/  (interceptores, 14 interfaces API, DTOs)  data/model/  data/mapper/\n"
        "│   │   ├── data/local/  (SessionManager, TokenCipher, ImagenUtils, DescargasArchivos)\n"
        "│   │   ├── data/repository/ (12)  domain/repository/ (12)  domain/usecase/ (2)\n"
        "│   │   ├── di/  (NetworkModule, RepositoryModule)\n"
        "│   │   ├── navigation/  (AppNavigation, NavIntents, NotificationRoutes)\n"
        "│   │   └── ui/  (screens ~40, viewmodel 22, components, theme, util)\n"
        "│   ├── app/src/main/res/  (drawable logos, themes, xml de red y backup)\n"
        "│   ├── app/build.gradle.kts / build.gradle.kts / settings.gradle.kts\n"
        "│   ├── gradle/libs.versions.toml + wrapper\n"
        "│   └── app/src/main/AndroidManifest.xml\n"
        "├── docker/  (nginx.conf, entrypoint.sh)\n"
        "├── docs/  (arquitectura.md, arquitectura.html, deploy-railway.md, guion-presentacion.md,\n"
        "│          normativa-visual.md, diagrama*.drawio/excalidraw, arquitectura-figma.svg)\n"
        "├── docs/manual-tecnico/  (este manual, sus figuras, capturas y scripts de generación)\n"
        "├── Dockerfile / railway.json / .dockerignore / .railwayignore\n"
        "├── AUDITORIA-INTEGRAL.md / HISTORIAL-FIXES.md\n"
        "└── .git/",
        archivo="Árbol del repositorio (16/10/2026).", ref="Anexo A")

    M.h1("ANEXO B. CATÁLOGO COMPLETO DE ENDPOINTS", salto=True)
    _endpoints(M)

    M.h1("ANEXO C. MATRIZ DE TRAZABILIDAD AMPLIADA", salto=True)
    M.tabla(
        "Trazabilidad requisito → evidencia en código → prueba → estado",
        ["ID", "Evidencia principal (archivo)", "Prueba / verificación", "Estado"],
        [
            ("RF-01", "`backend/app/Http/Controllers/Api/AuthController.php`; middleware `CambioContrasenaObligatorio`", "`CredencialesTest`; login real de tres roles (auditoría)", "Verificado"),
            ("RF-02", "`AuthController::forgotPassword/resetPassword`; `config/auth.php` (broker)", "`PasswordRecoveryTest`", "Verificado"),
            ("RF-03", "`GeneralUserController`; `Support/BorradoCascada.php`", "`DeleteGuardsTest`, `GeneralUserRolTest`", "Verificado"),
            ("RF-04", "`Services/AltaUsuario.php`; `Support/CredencialesPdf.php`; vista Blade del PDF", "`CredencialesTest`, `CredencialesCorreoTest`", "Verificado"),
            ("RF-05", "`ClassGroupController`; migración `class_groups`", "`FichaNumeroUnicoTest`, `FichaAprendizTest`", "Verificado"),
            ("RF-06", "`ApprenticeController::unirmeAFicha/salirDeFicha`", "`ApprenticeFichaTest`", "Verificado"),
            ("RF-07", "`ProjectController`; `Project::puedeEscribir`; `HistorialProyecto`", "`FlujoPropuestaTest`, `LogicaNegocioTest`, `InteraccionPropuestaTest`", "Verificado"),
            ("RF-08", "`ApprenticeProjectController`; pivote `apprentice_projects`", "`EquipoTest`", "Verificado"),
            ("RF-09", "`SimilarityController@detect`; `Similarity/Recomputador.php`; `Services/SimilitudService.php`", "`SimilitudServiceTest`; 10 pares generados por el seed", "Verificado"),
            ("RF-10", "`Recomputador::archivar/recalcular`; comando `similitud:recalcular`", "`SimilitudesVigentesTest`", "Verificado"),
            ("RF-11", "`NotificationController`; `Services/NotificacionesService.php`", "`AuthorizationTest` (casos de notificación)", "Verificado"),
            ("RF-12", "`Support/Auditoria.php`; `AuditLogController`; `project_histories`", "`AuditLogTest`, `CierreAuditoriaTest`", "Verificado"),
            ("RF-13", "`BugReportController`; notificaciones de soporte", "`SoporteTest`", "Verificado"),
            ("RF-14", "`KnowledgeNetworkController`, `TrainingProgramController`", "`ReglasNegocioFase2Test`", "Verificado"),
            ("RF-15", "`MotorConfigController@resumen`; `SimilarityController@demo`", "`home-demo.spec.js` (E2E, existente)", "Verificado en ejecución (demo pública navegada)"),
            ("RF-16", "`Movil/app/src/main/java/...` (22 ViewModels, 12 repositorios)", "Sin suite propia; análisis estático", "Parcialmente verificado"),
            ("RF-17", "`MotorConfigController@update`; `motor_configs`", "`ValidacionesTest` (rangos); captura 20", "Verificado"),
            ("RF-18", "`GeneralUserController@perfil`", "`VisibilityTest`, `AccesoContraparteTest`", "Verificado"),
            ("RNF-01", "Middlewares + scopes; `Project::scopeParaUsuario`", "`PermisosTest`, `AuthorizationTest`", "Verificado"),
            ("RNF-02", "`GeneralUser::$hidden`; `makeVisible` administrativo", "`CierreAuditoriaTest` (BUG-001)", "Verificado por código y prueba"),
            ("RNF-03", "`audit_logs` + `project_histories`", "`AuditLogTest`", "Verificado"),
            ("RNF-04", "`DB::transaction` + `lockForUpdate`", "`AtomicidadTest`", "Verificado"),
            ("RNF-05", "Tokens de diseño; especificaciones axe", "`accesibilidad*.spec.js` (existentes, no re-ejecutadas)", "Parcialmente verificado"),
            ("RNF-06", "`DataTable` modo tarjeta; layout móvil", "`responsive.spec.js`; capturas móviles reales", "Parcialmente verificado"),
            ("RNF-07", "`lib/api.js` (TTL 15 s, deduplicación); `?paginado=1`", "Revisión de código", "Verificado por código"),
            ("RNF-08", "`Dockerfile`; `docker/entrypoint.sh`", "Lectura de configuración (build no ejecutado)", "Verificado por configuración"),
            ("RNF-09", "Suites de prueba y ESLint", "§ 7.10 (244 + 60 + lint 0)", "Verificado"),
            ("RNF-10", "Revocación de tokens en cambio de correo; auditorías", "`ChangeEmailTest`", "Verificado"),
            ("RNF-11", "`ThrottleRequests:api`; `CuentaActiva`", "`NormalizacionSeguridadTest`", "Verificado"),
            ("RNF-12", "Scopes de visibilidad y privacidad de pasajes", "`VisibilityTest`, `SimilarityScopeTest`, `privacidad.spec.js`", "Verificado"),
        ],
        fuente="Auditoría (archivos y pruebas reales).",
        anchos=[1.4, 6.6, 6.2, 1.8],
        tamano=8,
    )

    M.h1("ANEXO D. COMANDOS DE REFERENCIA", salto=True)
    M.tabla(
        "Comandos verificados o derivados de la configuración del repositorio",
        ["Ámbito", "Comando", "Origen / verificación"],
        [
            ("Instalación backend", "`composer install`", "Verificado (auditoría)"),
            ("Entorno backend", "`copy .env.example .env` + `php artisan key:generate`", "Verificado"),
            ("Base de datos", "`CREATE DATABASE proyectwin_mvp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`", "Verificado"),
            ("Esquema y datos", "`php artisan migrate:fresh --seed`", "Verificado (18 migraciones + seed)"),
            ("Datos de volumen", "`php artisan db:seed --class=VolumenSeeder`", "README del backend"),
            ("Servidor de desarrollo", "`php artisan serve --host=127.0.0.1 --port=8000`", "Verificado"),
            ("Pruebas backend", "`php artisan test`", "Verificado (244/244)"),
            ("Motor", "`php artisan similitud:recalcular` · `similitud:calibrar`", "Comandos del repositorio"),
            ("Perfiles", "`php artisan perfiles:docentes`", "Comando del repositorio"),
            ("Dependencias web", "`npm install`", "Verificado (227 paquetes)"),
            ("Desarrollo web", "`npm run dev`", "Verificado (5173 + proxy)"),
            ("Build web", "`npm run build` · `npm run preview`", "Scripts del repositorio"),
            ("Pruebas unitarias", "`npm run test:unit`", "Verificado (60/60)"),
            ("Lint", "`npm run lint`", "Verificado (0 hallazgos)"),
            ("E2E", "`npm test` (Playwright; reseedea la base)", "Configuración del repositorio (no re-ejecutada completa)"),
            ("E2E completo", "`npm run test:full`", "Runner `e2e/run-full.mjs`"),
            ("Móvil", "`gradlew assembleDebug` (desde `Movil/`)", "Gradle del repositorio (no ejecutado)"),
            ("Contenedor", "`docker build -t proyectwin .` / `docker run -p 8080:8080 --env-file …`", "Dockerfile + entrypoint (no ejecutado; Docker ausente)"),
            ("Healthcheck", "`GET /up` → `{\"status\":\"ok\"}`", "Verificado en local y definido en `railway.json`"),
            ("Respaldo BD", "`mysqldump … > respaldo.sql`", "Procedimiento recomendado (§ 4.11, no implementado en el repo)"),
        ],
        fuente="Verificaciones de la auditoría y archivos del repositorio.",
        anchos=[3.0, 6.6, 6.0],
        tamano=8.5,
    )

    M.h1("ANEXO E. EVIDENCIAS DE VERIFICACIÓN DE LA AUDITORÍA", salto=True)
    M.p("Resultados reales obtenidos el 16/10/2026 (detalle en § 7.10). Se listan además las 24 capturas de "
        "interfaz generadas, disponibles en la carpeta de documentación del proyecto:")
    M.tabla(
        "Resumen de evidencias ejecutadas",
        ["Evidencia", "Detalle"],
        [
            ("Suite backend", "244 pruebas superadas · 1.156 aserciones · 35,93 s · 0 fallidas"),
            ("Unitarias frontend", "17 archivos · 60 pruebas superadas · 12,85 s"),
            ("Lint frontend", "ESLint sin hallazgos"),
            ("Arranque completo", "API 200 en `/up`; SPA operativa en `localhost:5173`; base sembrada (15 usuarios, 16 propuestas, 10 similitudes)"),
            ("Integridad de datos", "10/10 consultas sin incidencias (§ 4.12)"),
            ("Dependencias", "`composer audit`: 42 avisos / 11 paquetes; `npm audit`: 3 altas (dev)"),
            ("Capturas de interfaz", "24 PNG (portal, 3 roles, modo oscuro y vista móvil) — nombres `01_…24_` en `docs/manual-tecnico/capturas/`"),
            ("Figuras técnicas", "8 diagramas propios en `docs/manual-tecnico/figuras/` (arquitectura, secuencia, ER, motor, estados, despliegue, autenticación, casos de uso)"),
            ("Scripts de generación", "`docs/manual-tecnico/generacion/` (capturas Playwright, figuras matplotlib y generador DOCX)"),
        ],
        fuente="Auditoría (16/10/2026).",
        anchos=[4.2, 11.8],
        tamano=8.5,
    )

    M.h1("ANEXO F. REGISTRO DE LIMITACIONES, DISCREPANCIAS Y DATOS NO CONFIRMADOS", salto=True)
    M.tabla(
        "Registro de limitaciones y discrepancias",
        ["ID", "Tema", "Situación encontrada", "Tratamiento en este manual"],
        [
            ("L-01", "Fuente funcional ajena", "El PDF adjunto («M.U- PopayanAllTour -SdeI.pdf») documenta el proyecto PopayanAllTour, sin relación con este repositorio; su código fuente no existe en el entorno.", "Se excluyó como fuente funcional; la discrepancia queda registrada aquí y en los preliminares."),
            ("L-02", "Datos institucionales", "Ficha de formación, centro, nómina completa del equipo y roles individuales no constan en el repositorio.", "La portada y la ficha técnica los declaran como no confirmados; no se inventaron."),
            ("L-03", "Conteos de los README", "El README del backend cita «218 tests» y el del frontend «225 passed / 1 skipped» y «33 tests en 7 archivos»; la verificación real dio 244 pruebas backend, 54 especificaciones E2E y 60 pruebas unitarias en 17 archivos.", "Prevalece la verificación del 16/10/2026; los conteos de los README se señalan como desactualizados."),
            ("L-04", "«0 vulnerabilidades» del README", "El README del frontend afirma `npm audit` → 0 vulnerabilidades; la auditoría detectó 3 altas (dev).", "Prevalece la ejecución reciente (§ 7.8); recomendación de actualización."),
            ("L-05", "Suite E2E no re-ejecutada completa", "Existen 54 especificaciones E2E; su ejecución completa (con reseeds) no se realizó en esta auditoría.", "Documentada como existente; estado «pendiente de re-ejecución» (Anexo E, § 7.10)."),
            ("L-06", "Docker y despliegue no ejecutados", "El equipo de auditoría no tiene Docker instalado; Railway no se desplegó.", "Configuración verificada por lectura; comandos derivados de los archivos, señalados como no ejecutados."),
            ("L-07", "App Android no compilada", "No se generó APK ni capturas del dispositivo.", "Análisis estático; estado «no verificado visualmente»."),
            ("L-08", "documentación histórica", "`README-ALCANCE.md`, `ALINEACION-FRONTEND-BACKEND.md`, `CHANGELOG.md` y `HISTORIAL-FIXES.md` describen etapas anteriores (era mock) y llevan nota histórica parcial.", "Usados solo como contexto; las afirmaciones vigentes se tomaron del código. El CHANGELOG no tiene entradas posteriores a la integración real."),
            ("L-09", "Trabajo en curso sin confirmar", "Artefactos de despliegue, ajustes en `TrustProxies`/`routes/web.php`/`api.js` y la migración `sessions` no estaban confirmados en Git al cierre.", "Documentados como estado del árbol de trabajo; no se alteró ningún archivo del repositorio."),
            ("L-10", "Estado de la auditoría previa vs. actualidad", "`AUDITORIA-INTEGRAL.md` (05/10/2026) concluyó «NO LISTO» con 2 hallazgos críticos; los commits posteriores y las pruebas de regresión indican corrección.", "Se documentó la resolución verificable (pruebas de regresión y código actual: § 7.11); no se re-ejecutó la auditoría previa completa."),
            ("L-11", "ISO 10005", "La edición de 2018 no pudo confirmarse directamente en el catálogo durante la consulta (las ediciones 1995 y 2005 figuran retiradas).", "Se declara la incertidumbre y se recomienda verificar la edición vigente antes de citarla contractualmente."),
            ("L-12", "Integridad de negocio bajo concurrencia", "No se ejecutaron pruebas de concurrencia reales (envíos simultáneos).", "Documentada como limitación (§ 4.12); las mitigaciones de código (`lockForUpdate`) se describen como tales, no como probadas."),
            ("L-13", "Métricas de calidad del motor", "No hay resultados versionados de precisión/recall del motor sobre un conjunto etiquetado.", "Se describe el mecanismo de calibración existente sin afirmar métricas no medidas."),
            ("L-14", "Entorno de la base de datos de verificación", "Para proteger los datos del equipo se usó una base separada (`proyectwin_mvp_manual`); el `.env` de trabajo fue creado por la auditoría (archivo local excluido de Git).", "Registrado aquí; la base original no fue modificada y no se comprometió ningún secreto."),
        ],
        fuente="Auditoría (16/10/2026).",
        anchos=[1.2, 3.2, 7.2, 4.4],
        tamano=8,
    )
    M.p("**Declaración final de alcance:** este manual describe el sistema a partir de su repositorio y de su "
        "ejecución local verificada. Las afirmaciones se limitan a la evidencia recogida; toda afirmación "
        "clasificada como «parcialmente verificada», «inferida» o «no verificada» debe tratarse como pendiente "
        "hasta que el equipo del proyecto la confirme o complete.")

# -*- coding: utf-8 -*-
"""Capítulo 7 — Seguridad y calidad. Capítulo 8 — Interfaces y funcionalidades implementadas."""

FIG = lambda P, n: f"{P['fig']}\\{n}"
CAP = lambda P, n: f"{P['cap']}\\{n}"


def escribir(M, P):
    # =====================================================================
    # CAPÍTULO 7
    # =====================================================================
    M.h1("CAPÍTULO 7. SEGURIDAD Y CALIDAD", salto=True)
    M.p("Este capítulo describe la seguridad implementada, las pruebas existentes y las ejecutadas durante la "
        "auditoría, los riesgos identificados y el plan de mejora. **Advertencia metodológica:** una revisión "
        "estática y funcional como esta **no equivale a una auditoría formal de certificación** ni a una prueba de "
        "penetración; las conclusiones se limitan a lo observado y ejecutado.")

    M.h2("7.1. Arquitectura de seguridad")
    num = M.figura(FIG(P, "fig07_autenticacion.png"),
                   "Cadena de autenticación y autorización (SPA y móvil) con los eslabones de defensa",
                   "Elaboración propia a partir de `routes/api-v1.php`, `Kernel.php` y los middleware propios.", ancho_cm=16.0)
    M.p(f"La seguridad se organiza en capas defensivas (Figura {num}): (1) transporte — cookie httpOnly y "
        "mismo origen en web, HTTPS extremo a extremo en producción; (2) autenticación — Sanctum con dos modos; "
        "(3) estado de cuenta — el middleware `cuenta.activa` invalida tokens de cuentas suspendidas; (4) "
        "contraseña temporal — `CambioContrasenaObligatorio` bloquea toda la API hasta el primer cambio; (5) "
        "autorización — middleware `rol` más *scoping* por ficha en modelos y controladores; (6) superficie de "
        "datos — campos sensibles ocultos por defecto e inclusión por lista blanca; (7) tasa — `ThrottleRequests` "
        "global de la API; y (8) trazabilidad — bitácora inmutable de acciones sensibles.")

    M.h2("7.2. Autenticación")
    M.bullets([
        "**Acceso por `username`** (no por correo), con `recordarme` opcional; el correo se reserva para la recuperación de contraseña (`getEmailForPasswordReset`).",
        "**Contraseñas con hash bcrypt** (Laravel); la contraseña temporal se cifra con `Crypt` (clave `APP_KEY`) y **solo se descifra al exportar el PDF de credenciales**, mientras `must_change_password` esté activo.",
        "**SPA:** cookie de sesión httpOnly + CSRF (`/sanctum/csrf-cookie` + `X-XSRF-TOKEN`); el JavaScript nunca almacena tokens.",
        "**Móvil:** token Sanctum emitido en el login, persistido cifrado con AES-256-GCM (clave en Android Keystore) y reintroducido por interceptor en cada petición.",
        "**Recuperación de contraseña:** broker nativo con token de 60 minutos y *throttling* de 60 segundos; la respuesta es genérica exista o no el correo. En entorno local el enlace puede devolverse a la interfaz (facilitador de desarrollo); en producción debe viajar solo por correo.",
        "**Cambio de correo o contraseña** exige la contraseña actual; al cambiar el correo se revocan los demás tokens del usuario; la contraseña nueva no puede repetir la anterior.",
    ])

    M.h2("7.3. Autorización y roles")
    M.p("El modelo de autorización combina **roles** (aprendiz, instructor, admin) con **alcance por ficha** y "
        "**reglas de estado**. Los puntos de control verificados son:")
    M.bullets([
        "Middleware `rol` en 22 rutas (administración de usuarios, catálogos, motor, bitácora, credenciales, recálculo).",
        "Scopes de visibilidad en modelos: `Project::paraUsuario` (listado), `paraDetalle` (detalle con lectura ampliada del instructor en su programa), `Similarity::paraUsuario`/`visibleDetalle` (pares propios o autorizados) y `Comment::paraUsuario`.",
        "Regla «Classroom» (`Project::puedeEscribir`, § 6.7): participación solo dentro de la ficha activa; fuera de ella, solo lectura.",
        "Guardas de invariantes en controladores: no auto-degradarse, no dejar el sistema sin administradores activos (409), instructor con fichas no cambia de rol (409), no quitar al creador de su equipo (409), traslado de aprendices entre fichas bloqueado para instructores (422; solo admin).",
        "Privacidad entre pares: un aprendiz no ve propuestas, comentarios ni pasajes de compañeros; las contrapartes de similitud de otros alcances se abren en modo lectura sin datos personales.",
    ])
    M.p("Estas reglas cuentan con pruebas específicas (`PermisosTest`, `AuthorizationTest`, `VisibilityTest`, "
        "`SimilarityScopeTest`, `SimilarityPerspectiveTest`, `AccesoContraparteTest`, `InteraccionPropuestaTest`, "
        "`CierreAuditoriaTest`), todas superadas en la ejecución de esta auditoría (§ 7.10).")

    M.h2("7.4. Validación de entradas")
    M.bullets([
        "Validación declarativa (`$request->validate`) en cada controlador: tipos, longitudes, rangos (umbral 0,05–0,95; meses 1–60; campos mínimos de propuesta), unicidad (correo, documento, número de ficha) y pertenencia (códigos de ficha, ids relacionados).",
        "Asignación masiva controlada: los modelos y controladores usan listas blancas; los campos internos (`huella_envio`, `vigente`, `estado` en reportes, notificaciones) no son aceptados desde el cliente (probado en `NormalizacionSeguridadTest` y `CierreAuditoriaTest`).",
        "Inclusión de relaciones con lista blanca por modelo (`?included=`), ignorando rutas no autorizadas de forma silenciosa y segura.",
        "Salida saneada por `$hidden`: documentos, `username`, `must_change_password` y metadatos de credenciales no se serializan salvo en endpoints administrativos que los revelan explícitamente.",
        "Bloqueo de reenvíos vacíos mediante huella de contenido (`sha1`) y transacciones con `lockForUpdate` en envío y en el motor.",
    ])

    M.h2("7.5. Gestión de errores")
    M.p("El backend responde con códigos homogéneos (401/403/404/409/422/419/502, § 6.14) y mensajes en "
        "español orientados al usuario. En seguridad, el diseño «falla cerrado»: sin sesión → 401; sin permiso → "
        "403; cuenta suspendida → 403 aunque el token siga vigente. La interfaz evita pantallas blancas con "
        "`SafeRoute`/`ErrorBoundary` y expone recuperación (volver al panel o reportar la falla). El registro de "
        "auditoría y el de historial están diseñados para **no romper la acción del usuario** ante fallos de "
        "escritura secundaria (fail-open controlado que la auditoría interna marcó como riesgo menor de "
        "observabilidad).")

    M.h2("7.6. Protección de datos")
    M.bullets([
        "**Minimización:** las vistas de terceros (perfiles y contrapartes de similitud) muestran solo nombre, rol, foto y datos de contacto institucional.",
        "**Cifrado en reposo selectivo:** contraseñas (hash) y contraseñas temporales (cifrado simétrico con `APP_KEY`); el resto de los datos personales no se cifra a nivel de columna (decisión del MVP).",
        "**Cifrado en tránsito:** HTTPS (proveedor) con `SESSION_SECURE_COOKIE` recomendado en producción y `TrustProxies` configurado para terminar TLS en el proxy.",
        "**Credenciales institucionales:** los PDF con contraseña temporal deben manejarse como material sensible; el sistema audita su exportación, pero no controla su distribución posterior (riesgo R-08, § 7.13).",
        "**Datos de demostración:** el seeder crea personas ficticias (documentos `10000000XX`, correos `@sena.edu.co`); no corresponden a personas reales.",
    ])

    M.h2("7.7. Gestión de secretos")
    M.bullets([
        "`backend/.env` está excluido del control de versiones; solo se versiona `.env.example` sin valores sensibles.",
        "En producción, el arranque exige `APP_KEY` (el entrypoint falla con error explícito si falta) y las credenciales de base de datos llegan por variables del proveedor (Railway), no por archivos.",
        "`APP_DEBUG=false` y logs a `stderr` en el contenedor; no se expone `phpinfo` ni rutas de depuración.",
        "En esta auditoría **no se localizaron secretos reales** (claves, tokens o contraseñas de producción) en el repositorio; las únicas credenciales presentes son las de demostración del seeder, documentadas como tales.",
        "Riesgo residual: los PDF de credenciales y los correos son canales externos al control del sistema; se recomienda política institucional de manejo.",
    ])

    M.h2("7.8. Dependencias y riesgos de seguridad")
    M.p("La auditoría ejecutó los verificadores de vulnerabilidades de ambos ecosistemas el 16/10/2026. Los "
        "resultados **difieren de las afirmaciones históricas de los README** (el frontend declaraba «0 "
        "vulnerabilidades»), porque se publicaron avisos nuevos con posterioridad; prevalece la verificación "
        "reciente:")
    M.tabla(
        "Resultado de los verificadores de dependencias (16/10/2026)",
        ["Herramienta", "Resultado", "Detalle principal", "Acción recomendada"],
        [
            ("`composer audit` (backend)", "42 avisos en 11 paquetes", "`league/commonmark` (12, incl. alta), `guzzlehttp/guzzle` (9, incl. alta CVE-2026-69246), `laravel/framework` (4, incl. alta), `symfony/mime` (2, alta), `symfony/http-foundation`, `symfony/mailer`, `symfony/routing`, `guzzlehttp/psr7`, `symfony/yaml`, `symfony/polyfill-intl-idn`, `league/flysystem`", "Actualizar dependencias con `composer update` controlado (probando la suite) o priorizar los parches de las severidades altas."),
            ("`npm audit` (frontend)", "3 vulnerabilidades altas", "`brace-expansion`, `source-map-js` y `undici`; todas en la cadena de herramientas de desarrollo/build; `npm` reporta corrección disponible (`npm audit fix`)", "Ejecutar `npm audit fix` y revalidar build/lint/E2E."),
        ],
        fuente="Salida real de `composer audit` y `npm audit` durante la auditoría (no se modificaron dependencias).",
        anchos=[2.8, 2.6, 7.6, 3.0],
        tamano=8.5,
    )
    M.p("**Contexto de explotabilidad:** `guzzle` se usa como transporte de correo y `commonmark` para "
        "renderizado de Markdown en correos; si el sistema no procesa contenido de usuarios a través de esas "
        "vías, la explotación remota directa es improbable, pero el riesgo de cadena de suministro permanece y "
        "debe planificarse (riesgo R-02, § 7.13). No existe automatización de dependencias (Dependabot) ni CI "
        "que detecte avisos de forma continua.")

    M.h2("7.9. Pruebas existentes")
    M.tabla(
        "Inventario de pruebas del repositorio",
        ["Suite", "Herramienta", "Composición", "Qué cubre"],
        [
            ("Backend", "PHPUnit 10", "33 clases (31 Feature + 2 Unit), 244 métodos, 1.156 aserciones", "Autenticación, autorización por rol y alcance, credenciales, fichas, propuestas (ciclo completo), motor y vigencia, comentarios, notificaciones, soporte, auditoría, atomicidad y regresiones de cierre de auditoría (BUG-001…)."),
            ("Frontend unitarias", "Vitest + Testing Library", "17 archivos, 60 pruebas", "Utilidades, validaciones, cliente HTTP, componentes base (formularios, tablas, badges, dials, paginación) y focus-trap."),
            ("Frontend E2E", "Playwright (+ axe)", "54 especificaciones (47 raíz + 7 agentes)", "Flujos por rol, privacidad, permisos, ciclo de propuesta, similitudes, credenciales, responsividad (375/768), accesibilidad WCAG A/AA (claro y oscuro), navegación autónoma por agentes y casos negativos."),
            ("Móvil", "—", "Sin pruebas propias en el repositorio", "La app Android no incluye suite automatizada (deuda técnica declarada)."),
        ],
        fuente="Inventario directo del repositorio (16/10/2026).",
        anchos=[2.6, 3.0, 4.4, 6.0],
        tamano=8.5,
    )

    M.h2("7.10. Pruebas ejecutadas durante la auditoría")
    M.p("Todas las ejecuciones siguientes se realizaron el 16/10/2026 sobre una **base de datos separada** "
        "(`proyectwin_mvp_manual`) para no alterar los datos del equipo, con el árbol de trabajo tal como se "
        "encontró:")
    M.tabla(
        "Verificaciones ejecutadas y resultados reales",
        ["#", "Verificación", "Comando / método", "Resultado"],
        [
            ("1", "Instalación backend", "`composer install`", "Correcta (paquetes descubiertos; 81 sugerencias de financiación, sin errores)"),
            ("2", "Instalación frontend", "`npm install`", "227 paquetes en ~32 s"),
            ("3", "Clave de aplicación", "`php artisan key:generate`", "Correcta"),
            ("4", "Esquema y datos", "`php artisan migrate:fresh --seed`", "18 migraciones DONE + seed (15 usuarios, 16 propuestas, 10 similitudes, 30 notificaciones)"),
            ("5", "Arranque API", "`php artisan serve` + `GET /up`", "200 — `{\"status\":\"ok\"}`"),
            ("6", "Arranque SPA", "`npm run dev` + carga del portal", "200 — interfaz operativa"),
            ("7", "Suite backend", "`php artisan test`", "**244 superadas / 0 fallidas** (1.156 aserciones, 35,93 s)"),
            ("8", "Unitarias frontend", "`npm run test:unit`", "**60 superadas** en 17 archivos (12,85 s)"),
            ("9", "Análisis estático", "`npm run lint`", "0 hallazgos"),
            ("10", "Integridad de datos", "10 consultas SQL de verificación (§ 4.12)", "0 incidencias en los 10 chequeos"),
            ("11", "Capturas de interfaz", "Script Playwright propio (24 pantallas)", "24 capturas reales (Capítulo 8)"),
            ("12", "Auditoría de dependencias", "`composer audit` / `npm audit`", "42 avisos / 3 altas (dev) — § 7.8"),
            ("13", "Suite E2E completa", "`npm run test:e2e`", "**No ejecutada** en esta auditoría (ver limitación siguiente)"),
            ("14", "Compilación Android", "`gradlew assembleDebug`", "**No ejecutada** (Docker y emulador no disponibles)"),
        ],
        fuente="Ejecución real de la auditoría (salidas registradas).",
        anchos=[0.8, 3.2, 4.6, 7.4],
        tamano=8.5,
    )
    M.p("**Limitación de la verificación E2E:** la suite completa re-siembra la base antes de cada archivo y "
        "puede tardar decenas de minutos con reseeds; en esta auditoría se priorizó una verificación "
        "representativa (capturas navegadas por rol con el sistema real) y la ejecución íntegra de la suite de "
        "backend y de unitarias. La suite E2E queda documentada como existente y pendiente de re-ejecución "
        "completa por el equipo (Anexo F).")

    M.h2("7.11. Resultados y evidencias")
    M.bullets([
        "Las capturas del Capítulo 8 demuestran que los flujos principales funcionan de extremo a extremo con la API real y datos sembrados (sin maquetas).",
        "La suite de backend cubre las regresiones de la auditoría interna previa: `CierreAuditoriaTest` referencia explícitamente BUG-001…027 y NEW-001…004, incluidos los dos hallazgos críticos de aquel informe (fuga de datos por `included` y traslados de aprendices entre fichas).",
        "Verificación independiente de los dos hallazgos críticos en el código actual: (1) `GeneralUser::$hidden` oculta documento, `username` y metadatos de credenciales; (2) el traslado de aprendices entre fichas por instructores responde 422 («Solo un administrador puede trasladarlo») en los tres caminos (`store`, `agregarAprendiz`, `update`). Ambos se clasifican como **corregidos y con prueba de regresión** al cierre de esta auditoría.",
        "Los conteos se obtuvieron por lectura directa y ejecución; donde difieren de los README (E2E y tests por archivo), prevalece esta verificación.",
    ])
    M.p("**Conclusión de seguridad honesta:** el sistema aplica prácticas sólidas de autenticación, autorización "
        "y trazabilidad para su alcance de MVP; **no se afirma que sea «seguro»** en términos absolutos. Existen "
        "riesgos abiertos de dependencias, ausencia de CI, ausencia de pruebas de penetración y decisiones de "
        "infraestructura pendientes (sesiones, copias de seguridad) que se detallan a continuación.")

    M.h2("7.12. Limitaciones identificadas")
    M.bullets([
        "No se ejecutaron pruebas de penetración ni escaneo dinámico de vulnerabilidades.",
        "No se verificó el comportamiento tras un balanceador/proxy real (el rastro de IP en auditoría depende de `TrustProxies`).",
        "La accesibilidad (WCAG) se apoya en las especificaciones axe existentes; no se re-ejecutaron completas en esta auditoría.",
        "No hay pruebas de carga: los límites de rendimiento y los efectos del tamaño de la base (fotos en texto) no están medidos.",
        "La app móvil no fue compilada ni ejecutada en esta auditoría (análisis estático únicamente).",
        "El motor de similitud no fue evaluado con métricas de precisión/recall sobre un conjunto etiquetado real (existe la herramienta `similitud:calibrar` para ello, sin resultados versionados).",
    ])

    M.h2("7.13. Riesgos técnicos y medidas de mitigación")
    M.tabla(
        "Registro de riesgos (observados y potenciales)",
        ["ID", "Riesgo", "Causa", "Impacto", "Prob.", "Nivel", "Evidencia", "Mitigación"],
        [
            ("R-01", "Dependencias con avisos de seguridad vigentes (backend y dev-tools)", "Versiones fijadas por el lockfile frente a avisos publicados en 2026", "Medio", "Alta", "Alto", "`composer audit` (42), `npm audit` (3 altas)", "Planificar `composer update`/`npm audit fix` con revalidación de la suite; activar Dependabot al crear CI."),
            ("R-02", "Fin de soporte de Laravel 10 y PHP 8.1/8.2", "El framework y la rama PHP declaradas salieron del ciclo de soporte (PHP 8.2 termina el 31/12/2026)", "Alto", "Alta", "Alto", "`composer.json` (^8.1, laravel ^10); php.net; laravel.com (rama actual 13.x)", "Migrar a Laravel 11/12 y PHP 8.3+ (la imagen Docker ya usa 8.3); planificar ventana de migración."),
            ("R-03", "Sesiones de archivo se pierden en cada redespliegue", "`SESSION_DRIVER=file` con contenedor efímero", "Medio", "Alta", "Medio", "`.env.example`; migración `sessions` en curso", "Adoptar `SESSION_DRIVER=database` (migración ya creada en el árbol de trabajo) o Redis gestionado."),
            ("R-04", "Ausencia de integración continua", "No hay flujos CI en el repositorio", "Medio", "Alta", "Medio", "Sin `.github/workflows`; verificaciones manuales", "Crear pipeline (lint + tests backend + unit + build) en cada push."),
            ("R-05", "Sin estrategia de copias de seguridad implementada", "No hay scripts ni retención documentada", "Alto", "Media", "Alto", "§ 4.11 (procedimiento solo recomendado)", "Volcados programados + prueba de restauración trimestral; verificar respaldos del plugin MySQL."),
            ("R-06", "Errores de correo no visibles para el usuario final", "Sin SMTP, el aviso de credenciales falla y solo queda `credenciales_error`", "Bajo", "Media", "Bajo", "`CredencialesCorreo` (diseño fail-safe)", "Configurar SMTP en producción y monitorear el campo; mostrar el error al admin que reenvía."),
            ("R-07", "Crecimiento de la base por fotos en base64", "`foto_url` almacena data URLs", "Medio", "Media", "Medio", "`general_users.foto_url`; `utils/foto.js` (256 px)", "Mover fotos a almacenamiento de objetos con URL firmada si el volumen crece."),
            ("R-08", "Distribución insegura de PDF de credenciales", "El PDF contiene contraseña temporal en claro", "Alto", "Baja", "Medio", "`CredencialesPdf`", "Política institucional de entrega; acortar vigencia de temporales; auditar exportaciones (ya se audita)."),
            ("R-09", "Sin límite específico de intentos de login", "El throttle es global por API, no por credencial", "Medio", "Media", "Medio", "`Kernel.php` (grupo api)", "Añadir rate limiter dedicado al `POST /auth/login` y alertas de auditoría."),
            ("R-10", "Borrado de ficha elimina filas de aprendices (CASCADE)", "FK `apprentices.id_class_group` ON DELETE CASCADE", "Medio", "Baja", "Bajo", "Migración; hallazgo histórico BUG-007", "Cambiar a `SET NULL` o nullificar antes de borrar (mejora planificada)."),
            ("R-11", "Doble detección concurrente del mismo par", "Índice no único en `similarities` + buscar-y-crear", "Bajo", "Baja", "Bajo", "`Recomputador` (lock sobre `motor_configs` serializa)", "Índice único parcial por par vigente si el motor escala."),
            ("R-12", "Documentación interna desactualizada", "README/CHANGELOG describen etapas previas", "Bajo", "Alta", "Medio", "§ 1.9; Anexo F", "Actualizar README con los conteos reales y enlazar a este manual."),
            ("R-13", "APK sin configuración de firma en Gradle", "No hay `signingConfig` declarado", "Medio", "Media", "Medio", "`Movil/app/build.gradle.kts`", "Definir firma por variables/keystore fuera del repositorio antes de distribuir."),
            ("R-14", "Sin observabilidad centralizada", "Logs locales; sin métricas ni alertas", "Medio", "Media", "Medio", "`LOG_CHANNEL=stack`; contenedor a stderr", "Servicio de recolección de logs y alertas de errores; healthcheck ya existe."),
            ("R-15", "Trabajo en curso sin confirmar en Git", "Artefactos de despliegue y migración `sessions` sin commit al cierre", "Bajo", "Alta", "Medio", "`git status` del 16/10/2026", "Confirmar y etiquetar antes de la entrega; revisar `routes/web.php` y `TrustProxies` modificados."),
        ],
        fuente="Auditoría (observados) y análisis (potenciales). «Nivel» = combinación cualitativa de impacto y probabilidad.",
        anchos=[1.0, 3.6, 3.2, 1.4, 1.1, 1.3, 2.8, 3.6],
        tamano=7.5,
    )

    M.h2("7.14. Plan de mejora")
    M.p("Orden sugerido por relación impacto/esfuerzo, coherente con los riesgos anteriores:")
    M.numerada([
        "**Confirmar y etiquetar** el trabajo en curso (despliegue, `sessions`) y actualizar README con conteos reales (R-12, R-15).",
        "**Actualizar dependencias** con severidad alta y revalidar las suites (R-01).",
        "**Crear CI mínimo** (lint + pruebas backend + unitarias + build) y activar Dependabot (R-04).",
        "**Adoptar `SESSION_DRIVER=database`** y configurar SMTP real (R-03, R-06).",
        "**Implantar copias de seguridad** programadas con prueba de restauración (R-05).",
        "**Rate limiting específico de login** y alertas de auditoría sobre fallos repetidos (R-09).",
        "**Plan de migración de versión** (Laravel 11/12 + PHP 8.3+), con ventana y pruebas de regresión (R-02).",
        "**Endurecer borrados** (aprendices sin ficha al borrar ficha) y revisar la unicidad del par vigente (R-10, R-11).",
        "**Observabilidad**: recolección de logs y alertas de errores (R-14).",
        "**Firma de APK** y suite de pruebas para la app móvil (R-13).",
        "**Re-ejecutar la suite E2E completa** y agregar pruebas de concurrencia (R-11, limitación § 7.10).",
    ])

    M.h2("7.15. Control de calidad y normas ISO (análisis de pertinencia)")
    M.p("Las ediciones vigentes y las denominaciones se verificaron **directamente en el catálogo oficial de ISO "
        "el 16 de octubre de 2026** (fuente: iso.org, referencias completas en la bibliografía). Este apartado es "
        "un **análisis de pertinencia con criterios de evaluación propuestos**, no una auditoría ni una "
        "declaración de cumplimiento: se distingue, para cada norma, la práctica observada de la evidencia de "
        "cumplimiento.")
    M.tabla(
        "Normas de referencia: estado verificado y relación con el proyecto",
        ["Norma", "Edición vigente verificada (16/10/2026)", "Propósito general", "Relación con ProyecTwin"],
        [
            ("ISO/IEC 27001", "ISO/IEC 27001:2022 (+ Amd 1:2024)", "Requisitos de un sistema de gestión de seguridad de la información (SGSI).", "La seguridad implementada (roles, cifrado selectivo, auditoría, validación) se puede leer como prácticas alineadas a controles; el proyecto **no tiene un SGSI**."),
            ("ISO 14001", "ISO 14001:2026 (retiró la edición 2015)", "Requisitos de un sistema de gestión ambiental.", "Sin relación funcional directa; pertinente solo como criterio organizacional/institucional si el SENA lo exige al proyecto (p. ej. consumo energético de la infraestructura)."),
            ("ISO/IEC 25010", "ISO/IEC 25010:2023 (modelo de calidad del producto; línea SQuaRE con 25019:2023 y 25002:2024)", "Modelo de características de calidad de producto de software.", "Aplicable como **marco de evaluación**: idoneidad, seguridad, fiabilidad, mantenibilidad, usabilidad, compatibilidad, rendimiento y portabilidad se pueden calificar con la evidencia disponible."),
            ("ISO/IEC 9126", "Retirada (ISO/IEC 9126-1:2001, revisada por la línea 25010)", "Modelo histórico de calidad del producto (2001).", "Referencia **histórica**: su sucesión por ISO/IEC 25010:2023 debe citarse correctamente; no debe usarse como norma vigente."),
            ("ISO 37001", "ISO 37001:2025 (retiró la edición 2016)", "Sistemas de gestión antisoborno.", "No aplica al alcance técnico del proyecto; se menciona por completitud institucional."),
            ("ISO 10005", "Ediciones 1995 y 2005 retiradas; la edición de 2018 no pudo confirmarse en la ficha del catálogo durante esta consulta", "Directrices para planes de calidad.", "Útil si el equipo formaliza un plan de calidad del proyecto (versión, alcance, criterios de aceptación); verificar la edición vigente antes de citarla."),
            ("ISO 9001", "ISO 9001:2026 (publicada el 07/10/2026; retiró la 9001:2015 el 16/09/2026)", "Requisitos de un sistema de gestión de la calidad.", "Marco de referencia para documentación, control de cambios y mejora continua; el proyecto **no está certificado**."),
            ("Familia ISO/IEC 33000", "ISO/IEC 33002:2015 publicada y confirmada (revisión 2026-01-08); predecesora ISO/IEC 15504-2:2003 retirada", "Evaluación de procesos de software (conceptos, requisitos y modelos de capacidad).", "Permitiría evaluar la madurez de procesos (desarrollo, pruebas, configuración); requeriría una evaluación formal con evidencia histórica del equipo."),
            ("Familia ISO/IEC 27000", "ISO/IEC 27000:2026 (edición 6; retiró la de 2018)", "Visión general, conceptos y vocabulario de la familia de seguridad de la información.", "Vocabulario de referencia para el apartado de seguridad; su uso no implica conformidad con ISO/IEC 27001."),
        ],
        fuente="Catálogo oficial de ISO (iso.org), consulta directa 16/10/2026; referencias completas en la bibliografía.",
        anchos=[1.8, 4.6, 3.8, 5.8],
        tamano=8.5,
    )
    M.p("**Lectura crítica por norma (qué se podría evaluar, qué hay y qué falta):**")
    M.bullets([
        "**ISO/IEC 27001:2022** — *Criterios evaluables:* control de acceso (roles, mínimo privilegio), criptografía (hash/cifrado selectivo), gestión de vulnerabilidades (audit de dependencias), registro de eventos (bitácora), gestión de incidentes (reportes de soporte). *Evidencias disponibles:* middlewares y scopes, `$hidden`, auditoría inmutable, salidas de `composer/npm audit`. *No comprobado:* SGSI formal, análisis de riesgos documentado, revisión por la dirección, continuidad de negocio. *Recomendación:* mantener este manual como evidencia técnica primaria si la institución decide formalizar un SGSI.",
        "**ISO/IEC 25010:2023** — *Criterios evaluables con la evidencia de este manual:* idoneidad funcional (trazabilidad RF, capítulos 3 y 8), fiabilidad (atomicidad, 244 pruebas), seguridad (capítulo 7), usabilidad (WCAG, especificaciones axe), compatibilidad (web/móvil/escritorio), mantenibilidad (convenciones, lint, documentación), portabilidad (Docker). *No comprobado:* medición cuantitativa de eficiencia de desempeño y fiabilidad bajo carga. *Recomendación:* usar la tabla de requisitos (§ 3.1–3.2) como base de una futura evaluación por características.",
        "**ISO 9001:2026** — *Criterios evaluables:* control de la información documentada (este manual, README, guías internas), control de cambios (historial de fixes, Git), mejora continua (plan § 7.14). *No comprobado:* sistema de gestión de la calidad formal, auditorías internas de calidad. *Nota:* la edición 2026 es reciente (07/10/2026); su transición afecta a organizaciones certificadas, no a este proyecto.",
        "**Familia ISO/IEC 33000** — *Criterios evaluables:* proceso de desarrollo (evidencia en Git y estructura), proceso de pruebas (suites y ejecución de § 7.10), proceso de gestión de configuración (migraciones consolidadas, control de versiones). *No comprobado:* evaluación de capacidad por niveles con evaluadores competentes. *Recomendación:* considerar un autodiagnóstico ligero si se busca madurez de procesos.",
        "**Familia ISO/IEC 27000 y ISO 37001 / ISO 14001 / ISO 10005** — se documentan como **referencias institucionales**; no se identificó evidencia interna específica más allá del vocabulario de seguridad (27000) y la oportunidad de formalizar un plan de calidad (10005) con la estructura de este manual (objetivo, alcance, criterios de aceptación, control de versiones).",
    ])
    M.p("**Declaración explícita:** ninguna sección de este capítulo permite afirmar que ProyecTwin «cumple» una "
        "norma ISO en sentido certificable. Lo documentado son **prácticas observadas**, **evidencia disponible** "
        "y **aspectos no evaluados**, con recomendaciones para una eventual evaluación formal.")

    # =====================================================================
    # CAPÍTULO 8
    # =====================================================================
    M.h1("CAPÍTULO 8. INTERFACES Y FUNCIONALIDADES IMPLEMENTADAS", salto=True)

    M.h2("8.1. Metodología de verificación visual")
    M.p("Todas las capturas de este capítulo se tomaron el **16 de octubre de 2026** sobre el **sistema en "
        "ejecución** (API en `127.0.0.1:8000` y SPA en `localhost:5173`), con la base de datos sembrada por el "
        "`DatabaseSeeder` del proyecto. Las capturas se generaron con Chromium (Playwright) a 1400×880 y "
        "factor de escala 1,25 (escritorio) y 390×844 (móvil), con movimiento reducido activado. Los datos que "
        "aparecen son **sintéticos** (personas y documentos de demostración del seeder, § 7.6); no se exponen "
        "credenciales ni datos reales. La app Android **no** aporta capturas porque no se compiló en esta "
        "auditoría (limitación declarada). Las capturas del manual de usuario ajeno (PopayanAllTour) **no** se "
        "usaron como evidencia de este sistema.")
    M.p("Cada módulo se documenta con: objetivo, ubicación en el código, componentes, datos de entrada/salida, "
        "validaciones, errores y estado de implementación. El estado «verificado» significa que la pantalla fue "
        "ejecutada y capturada en esta auditoría; la lógica asociada está además cubierta por las suites cuando "
        "se indica.")

    M.h2("8.2. Módulo público (portal, acceso y recuperación)")
    M.p("**Objetivo:** presentar el sistema sin sesión, ofrecer la demo pública del motor y gestionar el acceso. "
        "**Ubicación:** `frontend/src/pages/public/{Home,Login,RecuperarContrasena,RestablecerContrasena,PaginaNoEncontrada}`; "
        "API pública en `AuthController`, `MotorConfigController@resumen` y `SimilarityController@demo`. "
        "**Componentes:** `LandingLayout`, `LandingHeader`, `GovernmentBar`, `TextType`, `ConsoleCard`. "
        "**Datos:** la demo compara un texto (3–300 caracteres) contra el corpus aprobado y devuelve máximo 3 "
        "coincidencias con título y porcentaje (nunca autores). **Errores:** validación 422 del texto; el portal "
        "no requiere sesión.")
    M.figura(CAP(P, "01_home_publica.png"),
             "Portal público de ProyecTwin con el resumen del motor y la demo de similitud",
             "Captura real del sistema en ejecución (16/10/2026).", ancho_cm=16.2)
    M.figura(CAP(P, "02_login.png"),
             "Pantalla de inicio de sesión por nombre de usuario",
             "Captura real del sistema en ejecución (16/10/2026).", ancho_cm=16.2)

    M.h2("8.3. Módulo del aprendiz")
    M.p("**Objetivo:** gestionar el ciclo completo de las propuestas propias (crear, enviar, corregir, seguir) "
        "dentro de una ficha activa. **Páginas:** 14 rutas en `frontend/src/pages/aprendiz/`. **Reglas clave:** "
        "solo se participa dentro de la ficha activa (regla «Classroom»); el aprobado queda bloqueado; solo se "
        "reenvía con cambios reales; las similitudes muestran únicamente pares propios. **Cobertura por pruebas:** "
        "`Propuestas`/`FlujoPropuestaTest`, `Ficha`/`ApprenticeFichaTest`, `Equipo`/`EquipoTest` y las "
        "especificaciones E2E homónimas.")
    M.figura(CAP(P, "03_aprendiz_dashboard.png"),
             "Panel del aprendiz: métricas personales, acciones rápidas y propuestas recientes con su grado de similitud",
             "Captura real (usuario de demostración «María González»).", ancho_cm=16.2)
    M.figura(CAP(P, "04_aprendiz_propuestas.png"),
             "Listado de propuestas del aprendiz con filtros por estado y grado de similitud",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)
    M.figura(CAP(P, "05_aprendiz_detalle_proyecto.png"),
             "Detalle de una propuesta: datos, equipo, historial y acciones según estado",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)
    M.figura(CAP(P, "06_aprendiz_mi_ficha.png"),
             "«Mi ficha»: unión por código, código propio y compañeros",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)
    M.figura(CAP(P, "07_aprendiz_similitudes.png"),
             "Mis similitudes: pares detectados con porcentaje y grado (el histórico se conserva)",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)
    M.figura(CAP(P, "08_aprendiz_detalle_similitud.png"),
             "Detalle de similitud: comparación del par, señales del motor y pasajes coincidentes",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)
    M.figura(CAP(P, "09_aprendiz_alertas.png"),
             "Bandeja de alertas del aprendiz con estados de lectura",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)

    M.h2("8.4. Módulo del instructor")
    M.p("**Objetivo:** revisar las propuestas de sus fichas (aprobar/rechazar con observación), gestionar las "
        "fichas y su roster, y supervisar las similitudes de su alcance. **Páginas:** 11 rutas en "
        "`frontend/src/pages/instructor/`. **Reglas clave:** no edita aprobadas; el rechazo exige observación "
        "(guardada como comentario); la aprobación no re-ejecuta el motor (decisión verificada); puede crear "
        "fichas y dar de alta aprendices con credenciales. **Cobertura:** `instructor.spec.js`, "
        "`revision-rechazo.spec.js`, `ficha-edicion.spec.js` y las pruebas `PermisosTest`/`FichaAprendizTest`.")
    M.figura(CAP(P, "11_instructor_dashboard.png"),
             "Panel del instructor: cola de revisión, fichas a cargo y actividad",
             "Captura real (usuario de demostración «Carlos Ruiz»).", ancho_cm=16.2)
    M.figura(CAP(P, "12_instructor_revision.png"),
             "Cola de revisión de propuestas con filtros por estado y ficha",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)
    M.figura(CAP(P, "13_instructor_detalle_proyecto.png"),
             "Detalle de propuesta para revisión: contenido completo, equipo y observaciones",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)
    M.figura(CAP(P, "14_instructor_similitudes.png"),
             "Similitudes del alcance del instructor agrupadas por ficha",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)
    M.figura(CAP(P, "15_instructor_fichas.png"),
             "Gestión de fichas del instructor (creación, edición y roster)",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)

    M.h2("8.5. Módulo del administrador")
    M.p("**Objetivo:** operar la plataforma: usuarios y credenciales, catálogos, fichas, propuestas, similitudes, "
        "motor, soporte y bitácora. **Páginas:** 16 rutas en `frontend/src/pages/admin/`. **Poderes singularizados:** "
        "único rol que exporta credenciales en PDF, configura el motor, recalcula el corpus, interviene propuestas "
        "aprobadas y consulta la bitácora. **Cobertura:** `admin*.spec.js` (6 especificaciones) y pruebas "
        "`DeleteGuardsTest`, `GeneralUserRolTest`, `CredencialesTest`.")
    M.figura(CAP(P, "16_admin_dashboard.png"),
             "Panel administrativo: métricas globales y colas de gestión",
             "Captura real (usuario de demostración «Administrador»).", ancho_cm=16.2)
    M.figura(CAP(P, "17_admin_usuarios.png"),
             "Gestión de usuarios: búsqueda, filtros, estado y credenciales",
             "Captura real; los datos mostrados son ficticios del seeder.", ancho_cm=16.2)
    M.figura(CAP(P, "18_admin_detalle_usuario.png"),
             "Detalle de usuario: perfil, poderes administrativos y actividad",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)
    M.figura(CAP(P, "19_admin_bitacora.png"),
             "Bitácora de auditoría inmutable con filtros y paginación",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)
    M.figura(CAP(P, "20_admin_motor.png"),
             "Configuración del motor de similitud (umbral y ventana del corpus)",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)
    M.figura(CAP(P, "21_admin_similitudes.png"),
             "Supervisión global de similitudes con recálculo del corpus",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)
    M.figura(CAP(P, "22_admin_reportes.png"),
             "Bandeja de soporte: reportes de falla y su ciclo de atención",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)

    M.h2("8.6. Modo oscuro y experiencia móvil")
    M.p("La interfaz incluye un modo oscuro persistente (cookie `theme`) y diseño adaptable. Las capturas "
        "siguientes evidencian ambos comportamientos; las especificaciones `accesibilidad-oscuro.spec.js` y "
        "`responsive.spec.js` cubren estas variantes en la suite E2E (no re-ejecutadas completas en esta "
        "auditoría).")
    M.figura(CAP(P, "10_modo_oscuro_dashboard.png"),
             "Panel del aprendiz en modo oscuro (persistencia por cookie y contraste verificado por axe en la suite)",
             "Captura real del sistema en ejecución.", ancho_cm=16.2)
    M.figura(CAP(P, "23_movil_home.png"),
             "Portal público en vista móvil (390×844)",
             "Captura real del sistema en ejecución.", ancho_cm=9.6)
    M.figura(CAP(P, "24_movil_propuestas.png"),
             "Listado de propuestas del aprendiz en vista móvil (tarjetas en lugar de tabla)",
             "Captura real del sistema en ejecución.", ancho_cm=9.6)

    M.h2("8.7. Aplicación Android (análisis estático)")
    M.p("La app `Movil/` replica los flujos de los tres roles con arquitectura MVVM (22 ViewModels, 12 "
        "repositorios, 14 interfaces de API) y navegación por grafos de rol. Usa los mismos endpoints `/v1` con "
        "token Bearer cifrado, tema Material con la identidad institucional (`ui/theme`) y pantallas compartidas "
        "(alertas, soporte, perfil, fichas). **No se capturaron pantallas ni se compiló el APK en esta "
        "auditoría** (requiere Android Studio/emulador); su estado se clasifica como *no verificado visualmente*, "
        "con análisis de código fuente y configuración. Detalle funcional: Anexo A y capítulo 6.")

    M.h2("8.8. Resumen de estado por módulo")
    M.tabla(
        "Estado de implementación y verificación por módulo",
        ["Módulo", "Estado", "Evidencia principal"],
        [
            ("Portal público y demo del motor", "Verificado", "Capturas 01–02; ejecución del portal; endpoints públicos"),
            ("Autenticación, recuperación y cambio obligatorio", "Verificado", "Login por tres roles ejecutado; `CredencialesTest`, `PasswordRecoveryTest`"),
            ("Aprendiz: propuestas, equipo y ficha", "Verificado", "Capturas 03–06; `FlujoPropuestaTest`, `EquipoTest`, `ApprenticeFichaTest`"),
            ("Aprendiz: similitudes y alertas", "Verificado", "Capturas 07–09; `SimilarityScopeTest`, `SimilarityPerspectiveTest`"),
            ("Instructor: revisión, fichas y similitudes", "Verificado", "Capturas 11–15; `PermisosTest`, `FichaAprendizTest`"),
            ("Administrador: usuarios, motor, bitácora y soporte", "Verificado", "Capturas 16–22; `DeleteGuardsTest`, `AuditLogTest`, `SoporteTest`"),
            ("Modo oscuro y experiencia móvil web", "Verificado (capturas) / suite E2E pendiente de re-ejecución", "Capturas 10, 23–24; especificaciones existentes"),
            ("Motor de similitud (backend)", "Verificado", "10 pares generados por el seed; `SimilitudServiceTest`; comando `similitud:calcular`"),
            ("Credenciales PDF y correo", "Verificado (generación) / parcial (correo real no probado sin SMTP)", "`CredencialesCorreoTest` (con correo simulado)"),
            ("App Android", "No verificado visualmente en esta auditoría", "Análisis de código y configuración"),
            ("Despliegue Docker/Railway", "No ejecutado en esta auditoría", "Configuración verificada por lectura (`Dockerfile`, `railway.json`)"),
        ],
        fuente="Auditoría (16/10/2026).",
        anchos=[5.0, 4.6, 6.4],
        tamano=8.5,
    )

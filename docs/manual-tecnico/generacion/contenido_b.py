# -*- coding: utf-8 -*-
"""Capítulo 1 — Presentación del proyecto. Capítulo 2 — Aspectos técnicos."""
from docx.shared import Pt

FIG = lambda P, n: f"{P['fig']}\\{n}"


def escribir(M, P):
    # =====================================================================
    # CAPÍTULO 1
    # =====================================================================
    M.h1("CAPÍTULO 1. PRESENTACIÓN DEL PROYECTO")

    M.h2("1.1. Introducción")
    M.p("ProyecTwin es una plataforma desarrollada en el marco de la formación del Servicio Nacional de "
        "Aprendizaje (SENA) cuyo propósito es apoyar la **gestión de las propuestas de proyecto formativo** de "
        "los aprendices y el análisis de **similitud entre propuestas**. El sistema permite registrar propuestas, "
        "someterlas a revisión por parte de los instructores, compararlas contra un corpus de propuestas aprobadas "
        "mediante un motor de similitud implementado íntegramente en el backend, y conservar la trazabilidad de "
        "todo el ciclo mediante una bitácora de auditoría y un historial funcional.")
    M.p("Este manual técnico documenta el estado real del sistema a partir de una auditoría del repositorio "
        "`ProyecTwin-MVP` realizada el 16 de octubre de 2026. La auditoría incluyó la lectura del código fuente y "
        "de los archivos de configuración, la ejecución del entorno completo en un equipo local (backend, frontend "
        "y base de datos), la ejecución de las suites de pruebas y la captura de pantallas reales de la interfaz. "
        "Cada afirmación relevante se vincula con su evidencia en los anexos.")
    M.p("El documento está dirigido a desarrolladores, instructores técnicos y evaluadores académicos. Está "
        "organizado en ocho capítulos que cubren desde la presentación funcional hasta el detalle de instalación, "
        "operación, mantenimiento, seguridad y calidad.")

    M.h2("1.2. Presentación general de ProyecTwin")
    M.p("ProyecTwin materializa el ciclo completo de una propuesta de proyecto formativo:")
    M.bullets([
        "**El aprendiz** crea y edita su propuesta (título, resumen, palabras clave, área de aplicación, objetivo general y objetivos específicos), gestiona el equipo de trabajo cuando la propuesta es grupal, y la envía a revisión. Al enviarla, el motor de similitud la compara contra el corpus aprobado del mismo programa de formación.",
        "**El instructor** revisa las propuestas de sus fichas, aprueba o rechaza con observaciones, gestiona sus fichas de formación (roster, credenciales, estados) y consulta las similitudes detectadas en su alcance.",
        "**El administrador** gestiona usuarios y credenciales institucionales, los catálogos de redes de conocimiento y programas, las fichas, las propuestas, las similitudes, la configuración del motor de similitud, la bitácora de auditoría y los reportes de soporte.",
    ])
    M.p("El sistema se compone de tres artefactos de software que conviven en un mismo repositorio: una **API "
        "REST** (Laravel 10 sobre PHP), una **aplicación web** tipo SPA (React 19 sobre Vite) y una **aplicación "
        "móvil Android** (Kotlin con Jetpack Compose). La web y la API se despliegan juntas en un único contenedor "
        "Docker; la app móvil se distribuye por separado y consume la misma API mediante token de acceso.")
    M.p("Un rasgo distintivo del proyecto es que el **motor de similitud es 100 % local**: no depende de servicios "
        "externos de inteligencia artificial ni de librerías de procesamiento de lenguaje natural; combina cuatro "
        "señales (coincidencia de palabras con BM25, n-gramas de caracteres, tema latente con LSA y cobertura de "
        "términos) en una puntuación de 0 a 100 con umbral y ventana temporal configurables.")

    M.h2("1.3. Resumen ejecutivo")
    M.bullets([
        "**Arquitectura verificada:** monorepo con API Laravel 10 (`/v1`, 84 rutas), SPA React 19 (52 rutas navegables de 55 declaradas), app Android (Kotlin 2.2, Compose) y base de datos MySQL/MariaDB (20 tablas físicas).",
        "**Seguridad por diseño:** autenticación Sanctum con cookie httpOnly para la SPA y token Bearer para móvil; autorización por roles (`aprendiz`, `instructor`, `admin`) más *scoping* por ficha; validación en cada controlador; contraseñas con hash; bitácora de auditoría inmutable y campos sensibles ocultos por defecto.",
        "**Motor de similitud:** cuatro señales ponderadas, umbral configurable (0,05–0,95) y ventana temporal (1–60 meses) desde el panel de administración; pares vigentes e históricos; coincidencia textual por pasajes.",
        "**Calidad verificada en esta auditoría:** 244 pruebas de backend superadas (1.156 aserciones), 60 pruebas unitarias de frontend superadas, ESLint sin hallazgos, arranque completo del sistema y 24 capturas reales de interfaz; 54 especificaciones E2E existen en el repositorio aunque no se re-ejecutaron completas (ver § 7.10).",
        "**Estado de dependencias:** `composer audit` reporta 42 avisos de seguridad en 11 paquetes (varios de severidad alta) y `npm audit` reporta 3 vulnerabilidades altas en la cadena de herramientas de desarrollo. Existen actualizaciones disponibles; se recomienda planificarlas (ver § 7.8).",
        "**Despliegue:** Dockerfile multi-etapa con nginx + PHP-FPM y publicación en Railway con healthcheck `/up`; migraciones automáticas con reintentos y seed opcional en el primer despliegue.",
        "**Riesgos principales:** Laravel 10 y PHP 8.2 alcanzaron el final de su vida útil o están cerca de hacerlo; ausencia de integración continua (CI); sesiones de archivo que se pierden en cada redespliegue (su migración a base de datos está en curso, sin confirmar en Git); y estrategia de copias de seguridad no implementada en el repositorio.",
    ])

    M.h2("1.4. Necesidad que atiende la aplicación")
    M.p("En los procesos de formación con proyectos, la revisión de propuestas plantea tres necesidades "
        "recurrentes: (1) **originalidad**: detectar coincidencias significativas entre propuestas para orientar "
        "acompañamiento sin emitir juicios automáticos; (2) **trazabilidad**: saber quién hizo qué y cuándo, tanto "
        "sobre la evolución funcional de cada propuesta como sobre las acciones administrativas; y (3) "
        "**centralización**: que aprendices, instructores y administración trabajen sobre una misma fuente de "
        "verdad en lugar de hojas de cálculo o documentos dispersos.")
    M.p("ProyecTwin atiende esas necesidades con reglas explícitas: las propuestas viven en estados auditables "
        "(borrador → pendiente → aprobado/rechazado), la detección de similitud se ejecuta en el momento del envío "
        "contra un corpus acotado por programa y ventana temporal, y cada acción relevante queda registrada en una "
        "bitácora inmutable. Este manual **no atribuye resultados medidos** (como reducciones de tiempo o mejoras "
        "cuantificadas) porque el repositorio no contiene estudios de impacto ni métricas de uso verificables.")

    M.h2("1.5. Solución tecnológica propuesta")
    M.p("La solución implementada es una arquitectura **cliente–servidor en tres clientes** sobre una API común:")
    M.bullets([
        "**API REST Laravel** en `/v1`, con autenticación doble (cookie de sesión para la SPA, token para móvil y pruebas), middleware propio (`cuenta.activa`, `rol`, cambio de contraseña obligatorio), controladores con validación y autorización, y 16 modelos Eloquent.",
        "**SPA React 19 + Vite 8** con enrutado por rol, contextos de sesión/tema/motor, cliente HTTP propio con CSRF, caché de lectura de 15 s y manejo centralizado de sesión expirada.",
        "**App Android Kotlin + Jetpack Compose** con arquitectura MVVM, Retrofit + kotlinx-serialization, token cifrado en el dispositivo (AES-256-GCM con Android Keystore) y paridad funcional con la web.",
        "**Base de datos MySQL/MariaDB** con 18 tablas de dominio, restricciones de integridad y migraciones versionadas.",
        "**Empaquetado único** (nginx + PHP-FPM + estáticos de la SPA) para el despliegue web, con la app móvil fuera del alcance del contenedor.",
    ])
    M.p("El detalle de cada componente, su versión y su evidencia se desarrolla en el Capítulo 2.")

    M.h2("1.6. Objetivo general del manual técnico")
    M.p("Documentar de forma completa, verificable y reproducible la arquitectura, la base de datos, la instalación "
        "y configuración, los procedimientos de desarrollo, las medidas de seguridad y las interfaces implementadas "
        "en ProyecTwin, de modo que un desarrollador ajeno al equipo pueda comprender el sistema, ponerlo en "
        "funcionamiento y mantenerlo sin depender del conocimiento de sus autores originales.")

    M.h2("1.7. Objetivos específicos")
    M.numerada([
        "Describir la arquitectura real del sistema y sus componentes con base en el código fuente auditado.",
        "Documentar el modelo de datos completo: tablas, columnas, tipos, claves, relaciones, índices y restricciones.",
        "Establecer un procedimiento de instalación y ejecución reproducible en un entorno limpio, con comandos verificados y resultados esperados.",
        "Explicar la organización del código, los flujos de negocio principales y las convenciones de desarrollo.",
        "Analizar la seguridad implementada, las pruebas existentes y las ejecutadas durante la auditoría, con sus limitaciones explícitas.",
        "Evaluar la pertinencia de normas ISO de referencia frente al proyecto, distinguiendo práctica observada de evidencia de cumplimiento.",
        "Documentar las interfaces reales de la aplicación con evidencia visual capturada del sistema en ejecución.",
        "Registrar limitaciones, riesgos y discrepancias entre fuentes para orientar el plan de mejora.",
    ])

    M.h2("1.8. Alcance del manual")
    M.p("**Cubre:** el backend API (Laravel), el frontend web (React), la aplicación móvil Android, la base de "
        "datos, el motor de similitud, la configuración de despliegue (Docker/Railway), el entorno de pruebas y los "
        "procedimientos de operación y mantenimiento.")
    M.p("**No cubre:** la infraestructura física de la institución, la administración del dominio institucional, la "
        "capacitación pedagógica de los usuarios finales (existe material en el repositorio: `docs/guion-presentacion.md`), "
        "la auditoría contable o legal del proyecto, ni una evaluación de rendimiento bajo carga (no se ejecutaron "
        "pruebas de estrés).")

    M.h2("1.9. Limitaciones conocidas")
    M.p("Las siguientes limitaciones se identificaron durante la auditoría y se mantienen visibles de forma "
        "deliberada (el detalle y su tratamiento están en el Anexo F):")
    M.bullets([
        "**Datos institucionales incompletos:** el repositorio no contiene la ficha de formación, la nómina completa del equipo, cargos ni el centro de formación; la portada y la ficha documental lo declaran como dato faltante en lugar de inventarlo.",
        "**Discrepancia de fuentes:** el PDF de referencia entregado pertenece a otro proyecto (PopayanAllTour) y no fue utilizable como fuente funcional; ver la nota de alcance en los preliminares.",
        "**Pruebas E2E no re-ejecutadas completas:** la suite E2E (54 especificaciones) existe y está documentada, pero en esta auditoría solo se ejecutaron capturas controladas del sistema; no se corrió la suite completa (justificación y método en § 7.10).",
        "**App móvil sin compilación verificada en esta auditoría:** el SDK de Android está presente, pero no se generó el APK ni se capturaron pantallas del dispositivo; su análisis es estático (código y configuración).",
        "**Entorno verificado ≠ producción:** todas las verificaciones se ejecutaron en un equipo Windows con PHP 8.2.12 y MariaDB 10.4.32; el contenedor de producción usa PHP 8.3 (imagen oficial) y la base de datos gestionada de Railway.",
        "**Documentación histórica desactualizada:** varios archivos del repositorio (`README-ALCANCE.md`, `ALINEACION-FRONTEND-BACKEND.md`, `CHANGELOG.md`, conteos del `README.md` del frontend) describen etapas anteriores del proyecto; este manual señala las discrepancias donde afectan afirmaciones vigentes.",
        "**Trabajo en curso sin confirmar:** al cierre de la auditoría existían cambios sin confirmar en Git (incluida una migración de sesiones en base de datos y archivos de despliegue); se documentaron como estado del árbol de trabajo, no como versión liberada.",
        "**Sin integración continua:** no existen flujos de CI en el repositorio; las verificaciones dependen de ejecución manual.",
    ])

    M.h2("1.10. Público objetivo")
    M.tabla(
        "Público objetivo y uso esperado del manual",
        ["Perfil", "Uso esperado"],
        [
            ("Desarrollador nuevo en el equipo", "Comprender arquitectura, estructura del código y flujos; poner el sistema en marcha; modificar y extender con seguridad."),
            ("Desarrollador de mantenimiento", "Consultar diccionario de datos, contratos de API, convenciones y procedimientos de cambios."),
            ("Instructor técnico / evaluador académico SENA", "Verificar la correspondencia entre lo implementado y lo documentado; evaluar calidad, seguridad y trazabilidad."),
            ("Administrador de sistemas", "Desplegar, configurar variables, migrar la base de datos y operar el servicio (capítulo 5)."),
            ("Auditor de calidad / seguridad", "Punto de partida para revisiones formales: hallazgos, riesgos, pruebas y limitaciones (capítulo 7)."),
        ],
        fuente="Elaboración propia.",
        anchos=[5.2, 10.8],
    )

    M.h2("1.11. Convenciones de lectura y nomenclatura documental")
    M.bullets([
        "**Código fuente en línea:** `formato monoespaciado` para rutas, archivos, comandos, tablas y campos.",
        "**Rutas de archivo:** relativas a la raíz del repositorio (`backend/…`, `frontend/…`, `Movil/…`) salvo indicación contraria.",
        "**Clasificación de afirmaciones:** *verificado* (comprobado en código, configuración o ejecución durante la auditoría); *parcialmente verificado* (evidencia indirecta o incompleta); *inferido* (deducido del código sin prueba ejecutada); *no verificado* (declarado por documentos del proyecto pero no comprobado en esta auditoría).",
        "**Referencias normativas:** las ediciones de las normas se verificaron en el catálogo oficial de ISO el 16/10/2026 (§ 7, control de calidad).",
        "**Figuras y tablas:** numeradas de forma consecutiva y generadas con campos automáticos de Word; las listas de figuras y tablas al inicio del documento se diligencian al actualizar los campos.",
        "**Prioridad de fuentes:** código y configuración > documentos técnicos internos vigentes > documentación oficial de las tecnologías > material histórico (marcado como tal).",
        "**Cifras:** cuando un documento del proyecto afirma un conteo y la auditoría obtuvo otro, prevalecen los conteos obtenidos por lectura directa y ejecución, señalando la diferencia.",
    ])

    # =====================================================================
    # CAPÍTULO 2
    # =====================================================================
    M.h1("CAPÍTULO 2. ASPECTOS TÉCNICOS", salto=True)

    M.h2("2.1. Descripción general de la solución")
    M.p("ProyecTwin es una solución cliente–servidor. En el repositorio conviven tres aplicaciones y los archivos "
        "de despliegue:")
    M.tabla(
        "Artefactos del repositorio",
        ["Ruta", "Qué contiene", "Estado"],
        [
            ("`backend/`", "API REST Laravel 10 (PHP): rutas `/v1`, controladores, modelos, migraciones, seeders, motor de similitud, pruebas PHPUnit.", "Completo y en ejecución"),
            ("`frontend/`", "SPA React 19 + Vite 8: 47 páginas, 54 componentes, clientes de API, suite E2E (54 especificaciones) y unitarias.", "Completo y en ejecución"),
            ("`Movil/`", "Aplicación Android Kotlin + Jetpack Compose (MVVM), Retrofit, DataStore, ~40 pantallas.", "Completo; compilación no verificada en esta auditoría"),
            ("`docker/` + `Dockerfile` + `railway.json`", "Empaquetado de producción (nginx + PHP-FPM) y despliegue en Railway.", "Trabajo reciente, aún sin confirmar en Git al cierre de la auditoría"),
            ("`docs/`", "Diagramas y documentos internos (arquitectura, despliegue, guion de presentación, normativa visual).", "Parcialmente desactualizado (ver § 1.9)"),
        ],
        fuente="Inventario del repositorio (16/10/2026).",
        anchos=[4.0, 9.6, 2.6],
    )
    M.p("No existe un número de versión semántico único del sistema: la app Android declara `versionName 1.0` "
        "(`Movil/app/build.gradle.kts`), mientras que la web y la API no exponen versión en el código (el "
        "`package.json` del frontend es `0.0.0`). El nombre canónico del producto en la interfaz y los metadatos es "
        "**ProyecTwin** (los identificadores técnicos usan variantes `proyectwin`/`proyecttwin`, p. ej. la base de "
        "datos `proyectwin_mvp`).")

    M.h2("2.2. Arquitectura real del sistema")
    num = M.figura(FIG(P, "fig01_arquitectura.png"),
                   "Arquitectura por capas de ProyecTwin (clientes, servidor de aplicación y datos)",
                   "Elaboración propia a partir del código y la configuración del repositorio.", ancho_cm=16.2)
    M.p(f"La Figura {num} resume la arquitectura verificada. Los clientes (SPA y app Android) consumen la misma "
        "API REST bajo `/v1` mediante HTTPS y JSON. En producción, un único contenedor ejecuta nginx (sirve los "
        "estáticos compilados de la SPA y enruta `/v1` y `/sanctum` a PHP-FPM) y Laravel resuelve las peticiones "
        "atravesando middleware de seguridad antes de llegar a los controladores; estos usan modelos Eloquent y "
        "servicios de dominio (credenciales, notificaciones, auditoría, motor de similitud) sobre MySQL. La app "
        "Android no forma parte del contenedor: se distribuye aparte y apunta a la misma API.")
    M.p("En desarrollo, Vite sirve la SPA en el puerto 5173 y **proxya** `/v1` y `/sanctum` hacia "
        "`http://127.0.0.1:8000` (`frontend/vite.config.js`), de modo que navegador y API comparten origen y "
        "la cookie de sesión viaja sin fricción CORS. Esta decisión (mismo origen en desarrollo y producción) "
        "explica por qué el cliente HTTP del frontend usa rutas relativas y cookies httpOnly en lugar de tokens "
        "en almacenamiento web.")

    M.h2("2.3. Componentes principales")
    M.tabla(
        "Componentes principales y su responsabilidad",
        ["Componente", "Tecnología", "Responsabilidad", "Ubicación"],
        [
            ("API REST", "Laravel 10", "84 rutas bajo `/v1`; autenticación, autorización por rol y alcance, reglas de negocio, motor de similitud, auditoría.", "`backend/routes/api-v1.php`"),
            ("Autenticación", "Laravel Sanctum 3", "Sesión por cookie httpOnly para la SPA; tokens Bearer para móvil y pruebas.", "`backend/app/Http/Controllers/Api/AuthController.php`"),
            ("Motor de similitud", "PHP puro (sin IA externa)", "Puntuación 0–100 con 4 señales, umbral y ventana configurables; pares vigentes/históricos y pasajes.", "`backend/app/Services/SimilitudService.php`, `backend/app/Similarity/`"),
            ("Servicio de notificaciones", "PHP/Laravel", "Punto único de creación de avisos internos (similitud, revisión, observación, mensaje, sistema).", "`backend/app/Services/NotificacionesService.php`"),
            ("Auditoría e historial", "PHP/Laravel", "Bitácora inmutable de acciones administrativas y línea de tiempo funcional de cada propuesta.", "`backend/app/Support/Auditoria.php`, `HistorialProyecto.php`"),
            ("Credenciales institucionales", "PHP + Dompdf", "Alta con usuario/contraseña temporal, envío por correo y exportación de PDF.", "`backend/app/Services/AltaUsuario.php`, `backend/app/Support/CredencialesPdf.php`"),
            ("SPA web", "React 19 + React Router 7", "43 rutas protegidas por rol, guardas de sesión y error, temas claro/oscuro, PWA básica.", "`frontend/src/App.jsx`, `frontend/src/pages/`"),
            ("Cliente HTTP web", "JavaScript (fetch)", "CSRF, cookie de sesión, caché de lectura 15 s, deduplicación y manejo de 401.", "`frontend/src/lib/api.js`, `frontend/src/lib/recursos.js`"),
            ("App móvil", "Kotlin + Compose (MVVM)", "Paridad funcional con la web para los tres roles; token cifrado en el dispositivo.", "`Movil/app/src/main/java/com/example/proyectwin/`"),
            ("Contenedor de despliegue", "Docker (nginx + php-fpm 8.3)", "Empaqueta SPA + API en un servicio con healthcheck y migraciones al arranque.", "`Dockerfile`, `docker/nginx.conf`, `docker/entrypoint.sh`"),
        ],
        fuente="Elaboración propia con base en el código auditado.",
        anchos=[3.4, 3.0, 6.4, 3.4],
        tamano=8.5,
    )

    M.h2("2.4. Lenguajes de programación")
    M.tabla(
        "Lenguajes utilizados (con evidencia)",
        ["Lenguaje", "Uso en el sistema", "Evidencia"],
        [
            ("PHP (≥ 8.1)", "Backend completo: API, motor de similitud, comandos y pruebas.", "`backend/composer.json` (`\"php\": \"^8.1\"`); ejecución verificada con 8.2.12"),
            ("JavaScript (ES2022+)", "SPA React, clientes de API, pruebas unitarias y E2E.", "`frontend/package.json`; archivos `.js`/`.jsx`"),
            ("Kotlin 2.2", "Aplicación Android completa (Compose, ViewModels, repositorios).", "`Movil/gradle/libs.versions.toml`"),
            ("SQL (MySQL)", "Esquema relacional, consultas de scopes, integridad y seeders.", "Migraciones y modelos Eloquent"),
            ("HTML5 / CSS3", "Interfaz web: tokens de diseño, CSS Modules, PWA.", "`frontend/src/tokens/index.css`, módulos CSS"),
            ("JSON", "Contratos de la API, columnas JSON (`detalles`, `objetivos_especificos`) y configuración.", "Respuestas de API y migraciones"),
        ],
        fuente="Revisión del repositorio (16/10/2026).",
        anchos=[2.8, 7.6, 5.6],
    )

    M.h2("2.5. Frameworks y bibliotecas")
    M.p("La Tabla siguiente consolida las dependencias principales por capa, con la **versión declarada** en los "
        "archivos de manifiesto y la **versión detectada** en el entorno de verificación (instalada por el gestor "
        "de paquetes). Las restricciones declaradas y las versiones resueltas pueden diferir porque los manifiestos "
        "usan rangos (`^`).")
    M.tabla(
        "Frameworks y bibliotecas principales",
        ["Capa", "Paquete", "Declarada", "Detectada", "Propósito"],
        [
            ("Backend", "laravel/framework", "^10.0", "10.x", "Framework base de la API."),
            ("Backend", "laravel/sanctum", "^3.2", "3.x", "Autenticación SPA (cookie) y tokens móviles."),
            ("Backend", "barryvdh/laravel-dompdf", "*", "2.x", "Generación del PDF de credenciales."),
            ("Backend", "guzzlehttp/guzzle", "^7.2", "7.10.0", "Cliente HTTP interno (correo/notificaciones)."),
            ("Backend (dev)", "phpunit/phpunit", "^10.0", "10.x", "Suite de pruebas del backend (244 casos)."),
            ("Backend (dev)", "fakerphp/faker, mockery, collision, pint, sail", "^1.9 / ^1.4 / ^7 / ^1 / ^1.18", "—", "Datos falsos, dobles de prueba, salida de pruebas, estilo de código y contenedores de desarrollo."),
            ("Frontend", "react, react-dom", "^19.2.5", "19.2.6", "Biblioteca de interfaz."),
            ("Frontend", "react-router-dom", "^7.15.1", "7.18.3", "Enrutado de la SPA con guardas por rol."),
            ("Frontend", "phosphor-react", "^1.4.1", "1.4.x", "Iconografía."),
            ("Frontend (dev)", "vite", "^8.0.10", "8.2.2", "Servidor de desarrollo y empaquetado."),
            ("Frontend (dev)", "@playwright/test + @axe-core/playwright", "^1.62.1 / ^4.13.0", "1.62.1", "Pruebas E2E y auditoría de accesibilidad."),
            ("Frontend (dev)", "vitest + @testing-library/*", "^4.1.11 / ^16.3 / ^7.0 / ^14.6", "4.1.11", "Pruebas unitarias de componentes y utilidades."),
            ("Frontend (dev)", "eslint + plugins", "^10.2.1", "10.x", "Análisis estático (0 hallazgos en esta auditoría)."),
            ("Móvil", "Kotlin + Jetpack Compose", "2.2.10 / BOM 2026.02.01", "—", "UI declarativa y lógica de la app Android."),
            ("Móvil", "Hilt", "2.60.1", "—", "Inyección de dependencias."),
            ("Móvil", "Retrofit + OkHttp + kotlinx-serialization", "2.9.0 / 4.12.0 / 1.6.3", "—", "Consumo de la API y serialización JSON."),
            ("Móvil", "DataStore Preferences", "1.1.4", "—", "Persistencia local de sesión."),
        ],
        fuente="`backend/composer.json`, `frontend/package.json`, `Movil/gradle/libs.versions.toml`; versiones detectadas con `composer show`, `npm ls` (16/10/2026).",
        anchos=[2.2, 4.4, 3.0, 1.8, 4.6],
        tamano=8.5,
    )

    M.h2("2.6. Herramientas de desarrollo")
    M.tabla(
        "Herramientas de desarrollo y operación",
        ["Herramienta", "Versión en la auditoría", "Uso"],
        [
            ("Composer", "2.10.2", "Gestión de dependencias PHP; instalación verificada del backend."),
            ("npm / Node.js", "11.16.0 / 26.3.1", "Dependencias y scripts del frontend; instalación verificada."),
            ("Vite", "8.2.2", "Servidor de desarrollo (proxy a la API), build de producción y preview."),
            ("PHP + extensiones", "8.2.12 (XAMPP)", "Ejecución local del backend; extensiones clave presentes: pdo_mysql, mbstring, dom, openssl, tokenizer, xml, fileinfo, curl, bcmath, zip."),
            ("MariaDB (XAMPP)", "10.4.32", "Base de datos local compatible con MySQL."),
            ("Git", "Repositorio con 46 commits", "Control de versiones (rama master; remoto GitHub)."),
            ("Gradle", "9.4.1 (wrapper)", "Construcción de la app Android (no ejecutada en esta auditoría)."),
            ("PHPUnit / Vitest / Playwright", "10.x / 4.1.11 / 1.62.1", "Pruebas de backend, unitarias y E2E."),
            ("ESLint", "10.x", "Análisis estático del frontend (flat config)."),
            ("Microsoft Word", "Instalado en el equipo", "Actualización de campos y validación del presente documento."),
        ],
        fuente="Verificación directa del entorno (16/10/2026).",
        anchos=[3.6, 4.4, 8.0],
    )

    M.h2("2.7. Motor de base de datos y persistencia")
    M.p("La persistencia principal es **MySQL**, con compatibilidad efectiva con **MariaDB** (la verificación se "
        "hizo sobre MariaDB 10.4.32; el driver es `pdo_mysql`). No se detectó ningún otro motor de datos: los "
        "archivos de configuración declaran `DB_CONNECTION=mysql` y el esquema usa 18 tablas de dominio creadas por "
        "migraciones Laravel con codificación `utf8mb4` y colación `utf8mb4_unicode_ci`. Algunos datos viven en "
        "columnas `JSON` (detalles del motor, objetivos específicos, bitácora); no se usan bases NoSQL ni "
        "almacenamiento en la nube.")
    M.p("Persistencias secundarias verificadas: archivos locales para caché y **sesiones** (`SESSION_DRIVER=file` "
        "según `.env.example`; existe una migración reciente, aún sin confirmar en Git, que crea la tabla "
        "`sessions` para migrar este comportamiento a base de datos), logs en `storage/logs` y generación de PDF "
        "en memoria con Dompdf (sin almacenamiento permanente de los PDF). Las fotografías de perfil se guardan "
        "como cadenas en `general_users.foto_url` (data URL procesada en el cliente a 256 px), decisión aceptable "
        "para el MVP pero con impacto en el tamaño de la base de datos (riesgo R-09, § 7.13).")

    M.h2("2.8. API e integraciones")
    M.p("El sistema expone su propia **API REST** (no consume API de terceros para su función principal):")
    M.bullets([
        "Prefijo único `/v1` registrado en `backend/app/Providers/RouteServiceProvider.php`; **84 rutas** en `backend/routes/api-v1.php` (10 públicas, 4 de sesión, 70 autenticadas, de las cuales 22 restringen a `rol:admin` o `rol:admin,instructor`).",
        "Formato JSON con errores homogéneos `{message: …}` y códigos 401/403/404/409/422/502; paginación opt-in (`?paginado=1`); inclusión controlada de relaciones con `?included=` validada por lista blanca en cada modelo.",
        "Integraciones salientes: **correo** para credenciales y recuperación de contraseña (MAIL_MAILER configurable; en desarrollo escribe al log) y **Webhook no**: no hay integraciones de pago, mapas ni IA externa.",
        "El catálogo completo de endpoints está en el Anexo B y su capítulo de referencia es el 6.12.",
    ])

    M.h2("2.9. Servicios externos")
    M.tabla(
        "Dependencias de servicios externos",
        ["Servicio", "¿Obligatorio?", "Uso y observaciones"],
        [
            ("Servidor SMTP (correo)", "Opcional", "Solo para envío real de credenciales y recuperación; en desarrollo/log y en producción sin SMTP la cuenta se crea y el error queda registrado (`credenciales_error`)."),
            ("Railway (hosting)", "Opcional", "Plataforma de despliegue prevista en `railway.json`; el software puede ejecutarse en cualquier host con Docker."),
            ("Google Fonts", "Sí en la web pública", "`index.html` carga Inter, JetBrains Mono y Sora desde `fonts.googleapis.com`; sin conexión, el navegador usa fuentes de reserva."),
            ("Android Keystore", "Sí en la app", "Protege el token de sesión en el dispositivo (servicio local del sistema operativo, no red)."),
            ("APIs de terceros / IA", "No", "El motor de similitud es local; no se detectaron claves ni llamadas a servicios de IA."),
        ],
        fuente="Revisión de configuración y código (16/10/2026).",
        anchos=[3.4, 2.6, 10.0],
    )

    M.h2("2.10. Entorno de ejecución")
    M.tabla(
        "Entornos de ejecución soportados por el proyecto",
        ["Entorno", "Composición", "Evidencia"],
        [
            ("Desarrollo (verificado en esta auditoría)", "Windows + XAMPP (PHP 8.2.12, MariaDB 10.4.32) + `php artisan serve` (:8000) + Vite dev (:5173, proxy a la API).", "Ejecución y capturas del 16/10/2026"),
            ("Producción web (previsto)", "Contenedor Docker: `php:8.3-fpm-alpine` + nginx + tini; SPA compilada servida por nginx; migraciones al arranque.", "`Dockerfile`, `docker/entrypoint.sh`"),
            ("Móvil", "Android 8.0 (API 26) o superior; compilación con SDK 36 y Gradle 9.4.1.", "`Movil/app/build.gradle.kts`"),
            ("Navegadores web", "Navegadores modernos de escritorio y móviles (la build de Vite 8 apunta a navegadores «Baseline widely available»).", "documentación oficial de Vite 8 y `vite.config.js`"),
        ],
        fuente="Auditoría del repositorio y ejecución local.",
        anchos=[4.2, 8.4, 3.4],
        tamano=8.5,
    )

    M.h2("2.11. Requisitos mínimos y recomendados de hardware")
    M.p("El repositorio no declara requisitos de hardware. Los siguientes valores son **recomendaciones de "
        "estimación técnica** elaboradas a partir de la carga observada durante la auditoría (instalación completa, "
        "servidor de desarrollo, base de datos y suite de pruebas), no requisitos oficiales del proyecto.")
    M.tabla(
        "Requisitos de hardware (estimación técnica)",
        ["Perfil", "Mínimo", "Recomendado"],
        [
            ("Equipo de desarrollo (web)", "4 GB de RAM, 4 GB de disco libre, CPU de 2 núcleos", "8–16 GB de RAM, SSD, CPU de 4 núcleos (la instalación de dependencias y las suites de prueba se benefician del disco SSD)"),
            ("Equipo de desarrollo (móvil)", "Además de lo anterior: 8 GB de RAM y 10 GB libres para SDK/emulador", "16 GB de RAM"),
            ("Producción web (contenedor)", "512 MB de RAM, 1 vCPU (MVP)", "1 GB de RAM y 1–2 vCPU con base de datos gestionada aparte"),
        ],
        fuente="Estimación propia durante la auditoría (no consta en el repositorio).",
        anchos=[4.0, 5.4, 6.6],
    )

    M.h2("2.12. Requisitos de software y versiones compatibles")
    M.tabla(
        "Matriz de software: declarado, verificado y compatibilidad",
        ["Software", "Declarado / exigido", "Verificado en auditoría", "Observaciones de compatibilidad"],
        [
            ("PHP", "`^8.1` (composer.json)", "8.2.12 (local); imagen de producción 8.3", "PHP 8.1 ya no recibe soporte; la rama 8.2 termina su soporte de seguridad el 31/12/2026 y la 8.3 el 31/12/2027 (fuente: php.net, consulta 16/10/2026)."),
            ("Composer", "2.x", "2.10.2", "Instalación reproducible con `composer install`."),
            ("MySQL / MariaDB", "MySQL 5.7+ (README)", "MariaDB 10.4.32", "Esquema compatible; `strict` activo y `utf8mb4_unicode_ci`."),
            ("Node.js", "No declarado explícitamente; Vite 8 exige ≥ 20.19/22.12", "26.3.1", "La imagen Docker compila con Node 22."),
            ("npm", "—", "11.16.0", "Lockfile `package-lock.json` presente."),
            ("Navegadores", "Modernos", "Chromium (capturas)", "PWA instalable opcional (`manifest.webmanifest`)."),
            ("Android", "minSdk 26 / target 36", "SDK presente; compilación no ejecutada", "Requiere JDK 17 para el daemon de Gradle (descargado por toolchain)."),
            ("Word", "Para editar el manual", "Disponible", "Los índices del documento son campos automáticos."),
        ],
        fuente="Manifiestos del proyecto y verificación local (16/10/2026); ciclo de vida de PHP según php.net.",
        anchos=[2.8, 4.0, 3.6, 5.6],
        tamano=8.5,
    )

    M.h2("2.13. Dependencias principales y su función")
    M.p("Además de la tabla de frameworks (§ 2.5), estas son las dependencias cuyo comportamiento es determinante "
        "para operar el sistema:")
    M.tabla(
        "Dependencias relevantes para operación y mantenimiento",
        ["Dependencia", "Capa", "Función en el sistema", "Nota de operación"],
        [
            ("laravel/sanctum", "Backend", "Sesiones SPA + tokens móviles.", "La expiración del token se controla con `SANCTUM_EXPIRATION` (1440 minutos por defecto en `.env.example`)."),
            ("barryvdh/laravel-dompdf", "Backend", "PDF de credenciales institucionales.", "Requiere extensiones PHP `dom`, `mbstring` y `fileinfo`."),
            ("guzzlehttp/guzzle", "Backend", "Transporte HTTP para correo.", "Aparece en `composer audit` con avisos vigentes (ver § 7.8)."),
            ("react-router-dom", "Frontend", "Enrutado con guardas por rol.", "Centraliza redirecciones por rol en `src/constants/routes.js`."),
            ("vite", "Frontend", "Dev server con proxy y build de producción.", "El proxy `/v1`→`127.0.0.1:8000` evita CORS y preflight en desarrollo."),
            ("@playwright/test", "Frontend", "E2E y capturas automatizadas.", "Descarga navegadores propios (`npx playwright install`)."),
            ("vitest", "Frontend", "Pruebas unitarias de componentes.", "Entorno jsdom; se ejecuta con `npm run test:unit`."),
            ("Hilt / Retrofit / DataStore", "Móvil", "DI, red y sesión persistente.", "El token se cifra con AES-256-GCM y la clave vive en Android Keystore."),
        ],
        fuente="Auditoría del repositorio.",
        anchos=[3.2, 1.8, 6.2, 4.8],
        tamano=8.5,
    )

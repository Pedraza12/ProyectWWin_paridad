# -*- coding: utf-8 -*-
"""Capítulo 3 — Diseño y modelado. Capítulo 4 — Base de datos."""

FIG = lambda P, n: f"{P['fig']}\\{n}"


def escribir(M, P):
    # =====================================================================
    # CAPÍTULO 3
    # =====================================================================
    M.h1("CAPÍTULO 3. DISEÑO Y MODELADO", salto=True)
    M.p("Este capítulo documenta los requisitos y modelos del sistema tal como están implementados. La "
        "clasificación usada es: **documentado** (declarado en documentos vigentes del proyecto), **inferido** "
        "(reconstruido por la auditoría a partir del código) y **verificado** (comprobado en ejecución durante "
        "esta auditoría). El proyecto no conserva un documento formal de requisitos aprobados; por transparencia, "
        "la matriz completa de requisitos se presenta como reconstrucción evidenciada desde el código, sus pruebas "
        "y su ejecución.")

    M.h2("3.1. Requisitos funcionales")
    M.tabla(
        "Requisitos funcionales (reconstruidos y verificados)",
        ["ID", "Requisito", "Prioridad", "Criterio de aceptación (evidencia)", "Estado"],
        [
            ("RF-01", "Autenticar usuarios por nombre de usuario y contraseña, con sesión persistente y cierre de sesión.", "Alta", "Login/logout funcionan por rol; `CambioContrasenaObligatorio` bloquea el resto de la API en primer ingreso (`CredencialesTest`).", "Verificado"),
            ("RF-02", "Recuperar contraseña por correo con token temporal (60 min) y cambio de contraseña autenticado.", "Alta", "Flujo completo con broker de Laravel; respuesta genérica ante correo inexistente (`PasswordRecoveryTest`).", "Verificado"),
            ("RF-03", "Gestionar cuentas y perfiles (nombre, apellido, documento, correo, foto, rol, estado) con alta administrativa y credenciales temporales.", "Alta", "Alta, suspensión, cambio de rol, eliminación con guardas (`AuthorizationTest`, `DeleteGuardsTest`).", "Verificado"),
            ("RF-04", "Exportar las credenciales institucionales en PDF (por usuario, selección o ficha) y reenviarlas por correo.", "Media", "PDF generado con Dompdf; reenvío solo con contraseña temporal vigente (`CredencialesCorreoTest`).", "Verificado"),
            ("RF-05", "Gestionar fichas de formación: crear con código de unión generado, asignar instructor y programa, cambiar de estado (activo/finalizado/anulada), editar roster y eliminar con salvaguardas.", "Alta", "Código único servidor-generado; anulación libera número conservando historial (`FichaNumeroUnicoTest`, `FichaAprendizTest`).", "Verificado"),
            ("RF-06", "Permitir al aprendiz unirse o salir de una ficha por código, con notificaciones al instructor.", "Media", "Unión/salida con validaciones 404/422 y avisos (`ApprenticeFichaTest`).", "Verificado"),
            ("RF-07", "Registrar propuestas de proyecto con ciclo borrador → enviado → aprobado/rechazado, edición según estado y bloqueo tras la aprobación.", "Alta", "Transiciones con historial; reenvío exige cambios reales por huella sha1 (`FlujoPropuestaTest`, `LogicaNegocioTest`).", "Verificado"),
            ("RF-08", "Gestionar el equipo de una propuesta (agregar/quitar integrantes con reglas de ficha y estado).", "Media", "Pivote `apprentice_projects` con invariantes; no se puede quitar al creador (409) (`EquipoTest`).", "Verificado"),
            ("RF-09", "Detectar similitudes entre la propuesta enviada y el corpus aprobado del mismo programa, con porcentaje, señales y pasajes.", "Alta", "`SimilarityController@detect` y `Recomputador::detectar`; umbral y ventana desde configuración (`SimilitudServiceTest`).", "Verificado"),
            ("RF-10", "Conservar evidencia histórica de similitudes (pares vigentes vs. archivados) y recalibrar el corpus globalmente.", "Media", "Archivado al rechazar/reenviar; recálculo admin (`SimilitudesVigentesTest`, comando `similitud:recalcular`).", "Verificado"),
            ("RF-11", "Generar notificaciones internas por eventos (revisión, similitud, observación, ficha, soporte) y gestionar su lectura.", "Media", "`NotificacionesService` como punto único; reglas de spoofing cerradas (`AuthorizationTest`).", "Verificado"),
            ("RF-12", "Registrar bitácora de auditoría inmutable y línea de tiempo funcional por propuesta.", "Media", "`audit_logs` solo lectura vía API; `project_histories` visible en detalle (`AuditLogTest`).", "Verificado"),
            ("RF-13", "Ofrecer soporte interno: reportar fallas, clasificarlas y resolverlas con respuesta del administrador.", "Media", "Flujo completo reporte → revisión → cierre con aviso (`SoporteTest`).", "Verificado"),
            ("RF-14", "Administrar catálogos institucionales: redes de conocimiento y programas de formación.", "Media", "CRUD con borrado protegido por dependencias (`ReglasNegocioFase2Test`).", "Verificado"),
            ("RF-15", "Publicar un portal público con resumen del sistema y demo del motor de similitud sin sesión.", "Baja", "`/public/resumen` y `/public/demo-similitud` (máx. 300 caracteres; solo títulos y porcentajes) (`home-demo` E2E).", "Verificado"),
            ("RF-16", "Disponer de una aplicación Android con paridad funcional para los tres roles.", "Media", "Código y configuración completos; compilación no verificada en esta auditoría.", "Parcialmente verificado"),
            ("RF-17", "Configurar el motor de similitud (umbral 0,05–0,95 y ventana 1–60 meses) desde el panel administrativo.", "Alta", "`PUT /v1/config-similitud` con auditoría `config_motor` (`ValidacionesTest`).", "Verificado"),
            ("RF-18", "Consultar perfiles de otros usuarios con alcance limitado (compañeros de ficha, instructor, aprendices propios).", "Baja", "Endpoint `perfil` con datos mínimos; sin PII (`NormalizacionSeguridadTest`).", "Verificado"),
        ],
        fuente="Reconstrucción de la auditoría a partir del código, los tests y la ejecución (16/10/2026). Requisitos inferidos, no un documento original de requisitos.",
        anchos=[1.3, 4.6, 1.6, 6.7, 1.8],
        tamano=8.5,
    )

    M.h2("3.2. Requisitos no funcionales")
    M.tabla(
        "Requisitos no funcionales (reconstruidos y verificados)",
        ["ID", "Requisito", "Evidencia", "Estado"],
        [
            ("RNF-01", "Seguridad de acceso: autenticación obligatoria en toda operación privada y autorización por rol y alcance.", "Middleware `auth:sanctum` + `cuenta.activa` + `rol`; scopes por ficha; 15 pruebas de `AuthorizationTest`.", "Verificado"),
            ("RNF-02", "Protección de datos personales: documentos, usernames y metadatos de credenciales no se exponen fuera de las vistas administrativas.", "`GeneralUser::$hidden` + `CierreAuditoriaTest` (regresión BUG-001) + verificación de código del 16/10/2026.", "Verificado"),
            ("RNF-03", "Trazabilidad: toda acción administrativa relevante y toda transición de propuesta quedan registradas con actor, fecha y detalle.", "`audit_logs` (inmutable) y `project_histories`; `AuditLogTest` y `HistorialProyecto`.", "Verificado"),
            ("RNF-04", "Integridad transaccional en operaciones compuestas (altas con perfiles, envíos con motor, borrados en cascada).", "`DB::transaction` + `lockForUpdate`; `AtomicidadTest`.", "Verificado"),
            ("RNF-05", "Accesibilidad web: cumplimiento de WCAG A/AA en las rutas del sistema (claro y oscuro).", "Especificaciones `accesibilidad*.spec.js` (axe) e infraestructura de navegación; no re-ejecutadas en esta auditoría.", "Parcialmente verificado"),
            ("RNF-06", "Adaptabilidad responsiva en móvil (375 px) y tableta (768 px) sin desbordes.", "Especificación `responsive.spec.js`; capturas móviles reales tomadas en la auditoría.", "Parcialmente verificado"),
            ("RNF-07", "Rendimiento percibido: lecturas con caché corta (15 s), deduplicación de peticiones y paginación opcional.", "`frontend/src/lib/api.js`; `?paginado=1` en listados densos.", "Verificado (por código)"),
            ("RNF-08", "Compatibilidad de despliegue: empaquetado reproducible y sin dependencia de servicios externos para funcionar.", "`Dockerfile` multi-etapa; solo SMTP/Google Fonts son externos y opcionales.", "Verificado (por configuración)"),
            ("RNF-09", "Mantenibilidad: convenciones de código, análisis estático y suites de prueba automatizadas.", "244 pruebas backend superadas; 60 unitarias superadas; ESLint 0 hallazgos.", "Verificado"),
            ("RNF-10", "Auditabilidad de seguridad: los cambios de credenciales, correo y configuración quedan auditados y los tokens se revocan cuando corresponde.", "`ChangeEmailTest`; auditorías `cambiar_clave`, `cambiar_correo`, `config_motor`.", "Verificado"),
            ("RNF-11", "Protección frente a abuso básico: límite de tasa en la API y bloqueo de cuentas suspendidas.", "`ThrottleRequests:api` (límite configurable); middleware `cuenta.activa`; `NormalizacionSeguridadTest`.", "Verificado"),
            ("RNF-12", "Privacidad del contenido entre pares: un aprendiz no ve propuestas, comentarios ni pasajes ajenos.", "`VisibilityTest`, `SimilarityScopeTest`, `privacidad` E2E; verificación de código del 16/10/2026.", "Verificado"),
        ],
        fuente="Reconstrucción de la auditoría a partir del código, los tests y la ejecución.",
        anchos=[1.3, 5.4, 7.5, 1.8],
        tamano=8.5,
    )

    M.h2("3.3. Matriz de trazabilidad de requisitos")
    M.p("La matriz siguiente vincula cada requisito con su implementación principal y sus pruebas específicas. La "
        "versión ampliada (con archivos y líneas) está en el Anexo C.")
    M.tabla(
        "Trazabilidad requisito → implementación → prueba",
        ["Requisito", "Implementación principal", "Prueba representativa"],
        [
            ("RF-01, RF-02", "`AuthController`, `PersonalAccessToken`, broker `password_reset_tokens`", "`CredencialesTest`, `PasswordRecoveryTest`"),
            ("RF-03, RF-04", "`GeneralUserController`, `AltaUsuario`, `CredencialesPdf`", "`DeleteGuardsTest`, `CredencialesCorreoTest`"),
            ("RF-05, RF-06", "`ClassGroupController`, `ApprenticeController`", "`FichaNumeroUnicoTest`, `ApprenticeFichaTest`"),
            ("RF-07, RF-08", "`ProjectController`, `ApprenticeProjectController`, `Project::puedeEscribir`", "`FlujoPropuestaTest`, `EquipoTest`"),
            ("RF-09, RF-10, RF-17", "`SimilarityController`, `Recomputador`, `SimilitudService`, `MotorConfigController`", "`SimilitudServiceTest`, `SimilitudesVigentesTest`, `SimilarityPerspectiveTest`"),
            ("RF-11", "`NotificationController`, `NotificacionesService`", "`AuthorizationTest` (notificaciones)"),
            ("RF-12", "`AuditLogController`, `Auditoria`, `HistorialProyecto`", "`AuditLogTest`, `CierreAuditoriaTest`"),
            ("RF-13", "`BugReportController`, `SoporteTest`", "`SoporteTest`"),
            ("RF-14", "`KnowledgeNetworkController`, `TrainingProgramController`", "`ReglasNegocioFase2Test`"),
            ("RF-15", "`MotorConfigController@resumen`, `SimilarityController@demo`", "`home-demo.spec.js` (E2E)"),
            ("RF-16", "Código `Movil/` (22 ViewModels, 12 repositorios)", "Sin pruebas automatizadas propias en el repositorio"),
            ("RF-18", "`GeneralUserController@perfil`", "`VisibilityTest`, `AccesoContraparteTest`"),
            ("RNF-01, RNF-02, RNF-12", "Middlewares, scopes y `$hidden` de `GeneralUser`", "`AuthorizationTest`, `CierreAuditoriaTest`"),
            ("RNF-03, RNF-04, RNF-10", "Transacciones, `Auditoria`, `lockForUpdate`", "`AtomicidadTest`, `ChangeEmailTest`"),
            ("RNF-05, RNF-06", "Tokens de diseño, `DataTable`, layout responsive", "`accesibilidad*.spec.js`, `responsive.spec.js` (no re-ejecutadas)"),
            ("RNF-07…RNF-11", "`api.js`, `ThrottleRequests`, Dockerfile, suites de prueba", "Verificación de ejecución § 7.10"),
        ],
        fuente="Auditoría del repositorio.",
        anchos=[3.4, 7.0, 5.6],
        tamano=8.5,
    )

    M.h2("3.4. Casos de uso relevantes")
    num = M.figura(FIG(P, "fig08_casos_uso.png"),
                   "Casos de uso por actor (reconstruidos del código y la interfaz real)",
                   "Elaboración propia a partir de las rutas, los guardas y las interfaces verificadas.", ancho_cm=16.2)
    M.p(f"La Figura {num} agrupa los casos de uso por actor. A continuación se detallan los tres flujos más "
        "representativos; el resto se describe en la matriz de interfaces del Capítulo 8.")
    M.tabla(
        "Detalle de casos de uso principales",
        ["Caso", "Actor", "Flujo principal", "Reglas y errores"],
        [
            ("CU-03 Enviar propuesta", "Aprendiz (creador o integrante)", "1) Edita el borrador · 2) Pulsar Enviar · 3) El backend valida campos mínimos · 4) Calcula huella y compara con la anterior · 5) Ejecuta el motor contra el corpus · 6) Notifica a instructor y coincidencias · 7) Registra historial y auditoría.", "403 sin ficha activa o fuera de la ficha; 422 por campos faltantes, reenvío sin cambios o transición inválida."),
            ("CU-04 Revisar propuesta", "Instructor de la ficha / asignado", "1) Abre la cola de revisión · 2) Lee el detalle con historial y observaciones · 3) Aprueba o rechaza con observación · 4) El sistema notifica al aprendiz y registra historial y auditoría.", "403 sobre propuestas ajenas; 422 al editar aprobadas; la observación de rechazo queda como comentario."),
            ("CU-05 Consultar similitudes", "Aprendiz / Instructor / Admin", "1) Abre Similitudes · 2) El listado aplica scoping (propias / de sus fichas / global) · 3) El detalle muestra el par A/B, señales y pasajes · 4) El histórico conserva pares archivados.", "Aprendiz solo ve pares de sus propuestas; contrapartes de otros alcances se muestran en solo lectura."),
        ],
        fuente="Auditoría del código (`ProjectController`, `SimilarityController`) y pruebas de la suite backend.",
        anchos=[3.0, 2.6, 6.6, 3.8],
        tamano=8.5,
    )

    M.h2("3.5. Actores y permisos")
    M.tabla(
        "Matriz de permisos por actor (síntesis del middleware y los scopes)",
        ["Recurso / operación", "Visitante", "Aprendiz", "Instructor", "Administrador"],
        [
            ("Portal y demo pública del motor", "Sí", "Sí", "Sí", "Sí"),
            ("Iniciar sesión / recuperar contraseña", "Sí", "Sí", "Sí", "Sí"),
            ("Ver y editar el propio perfil", "—", "Sí", "Sí", "Sí"),
            ("Propuestas: ver", "—", "Solo propias y de su ficha", "Sus fichas + mismo programa (lectura)", "Todas"),
            ("Propuestas: crear/editar/enviar", "—", "Propias, según estado y ficha activa", "No crea; aprueba/rechaza en revisión", "Intervención excepcional"),
            ("Equipo de propuesta", "—", "Según regla de participación", "—", "Todas"),
            ("Fichas: unirse/salir", "—", "Por código", "—", "—"),
            ("Fichas: gestionar roster y estado", "—", "—", "Sus fichas", "Todas"),
            ("Similitudes: ver", "—", "Solo de sus propuestas", "De sus fichas", "Todas (vigentes e históricas)"),
            ("Similitudes: recalcular/editar", "—", "—", "—", "Sí"),
            ("Motor: configurar umbral/ventana", "—", "—", "—", "Sí"),
            ("Usuarios y credenciales", "—", "—", "Alta de aprendices en sus fichas", "CRUD completo + PDF"),
            ("Catálogos (redes/programas)", "Lectura", "Lectura", "Lectura", "CRUD completo"),
            ("Notificaciones / alertas", "—", "Propias", "Propias + avisos a sus aprendices", "Propias + supervisión"),
            ("Auditoría (bitácora)", "—", "—", "—", "Lectura total"),
            ("Reportes de soporte", "—", "Crea y consulta los suyos", "Crea y consulta los suyos", "Responde y cierra"),
        ],
        fuente="`routes/api-v1.php`, middlewares y scopes de modelos (auditoría).",
        anchos=[4.4, 1.6, 3.4, 3.4, 3.2],
        tamano=8.5,
    )

    M.h2("3.6. Diagramas UML pertinentes")
    M.p("Los diagramas se generaron a partir del comportamiento real del código y se referencian donde se "
        "explican con detalle:")
    M.bullets([
        f"**Secuencia de una petición de lectura** (Figura del capítulo 3.8): recorrido completo desde la pantalla hasta MySQL y regreso, con los archivos intervinientes.",
        f"**Estados de una propuesta** (sección 3.7 y Capítulo 6): máquina de estados `borrador → pendiente → aprobado/rechazado` con reenvío e intervención administrativa.",
        f"**Modelo entidad-relación** (sección 3.9 y Capítulo 4): esquema relacional implementado.",
        f"**Casos de uso** (Figura anterior): actores y operaciones.",
        f"**Despliegue** (Capítulo 5): topología del contenedor y servicios.",
    ])
    num = M.figura(FIG(P, "fig02_flujo_peticion.png"),
                   "Diagrama de secuencia: «el aprendiz abre Mis Propuestas» (GET /v1/projects)",
                   "Elaboración propia a partir de `Propuestas.jsx`, `useApi.js`, `api.js`, `routes/api-v1.php` y `ProjectController`.", ancho_cm=16.2)
    M.p(f"La Figura {num} refleja el flujo verificado con líneas de código identificables: la página llama al "
        "hook `useApi` (`frontend/src/pages/aprendiz/Propuestas/Propuestas.jsx`), este usa los contratos de "
        "`frontend/src/lib/recursos.js`, y el cliente `frontend/src/lib/api.js` añade credenciales/CSRF. En el "
        "backend, la ruta vive en `backend/routes/api-v1.php` y el scoping se aplica en "
        "`Project::scopeParaUsuario` (`backend/app/Models/Project.php`).")

    M.h2("3.7. Modelo de componentes y estados")
    M.p("**Componentes:** el sistema se descompone en los artefactos descritos en § 2.1–2.3. En el backend se "
        "distinguen: controladores (15), servicios de dominio (4), soporte transversal (6 clases en "
        "`app/Support`), motor de similitud (4 clases en `app/Similarity` y `app/Services`), middleware propio (3) "
        "y comandos artisan (3). En el frontend: páginas (47), componentes reutilizables (54 archivos `.jsx`, con "
        "implementaciones compartidas `*Base` por familia funcional), contextos (sesión, tema y motor), y una "
        "capa de datos única (`lib/api.js`). La correspondencia pantalla→componente está en el Capítulo 8.")
    num = M.figura(FIG(P, "fig05_estados_propuesta.png"),
                   "Máquina de estados de una propuesta de proyecto",
                   "Elaboración propia a partir de las transiciones implementadas y probadas en `ProjectController`.", ancho_cm=16.2)
    M.p(f"La Figura {num} formaliza el ciclo de vida de la propuesta. Las transiciones están impuestas por el "
        "controlador y por `Project::puedeEscribir`, y cada cambio relevante se refleja en `project_histories` y "
        "en la bitácora cuando corresponde. El envío valida campos mínimos, computa la huella `sha1` del contenido "
        "y ejecuta el motor; el reenvío exige que la huella cambie (anti-reenvío vacío) y archiva las detecciones "
        "anteriores.")

    M.h2("3.8. Diagramas de secuencia de procesos importantes")
    M.p("**Proceso «enviar propuesta» (RF-07 + RF-09), pasos verificados en `ProjectController::enviar`:**")
    M.numerada([
        "Verificación de sesión, rol y alcance (`puedeEscribir`) y de estado (`borrador` o `rechazado`).",
        "Validación de campos mínimos (`camposFaltantes`: título ≥ 5, descripción ≥ 20, área ≥ 3, objetivo ≥ 15, al menos 2 objetivos específicos ≥ 8 caracteres).",
        "Bloqueo de concurrencia con `lockForUpdate` sobre la propuesta.",
        "Cálculo de la huella `sha1` del contenido y comparación con `huella_envio`; si no cambió, se responde 422.",
        "Transacción: `archivar()` de los pares previos si la propuesta venía rechazada, cambio a `pendiente` y `detectar()` de similitudes contra el corpus.",
        "Notificación al instructor y a los creadores de propuestas coincidentes; registro de historial (`enviada`/`reenviada`) y auditoría `enviar_propuesta`.",
    ])
    M.p("**Proceso «aprobar/rechazar» (`ProjectController::update`):** el instructor de la ficha o el asignado "
        "aplica la transición `pendiente → aprobado|rechazado` (con observación obligatoria en rechazo, guardada "
        "como comentario). La aprobación no re-ejecuta el motor (decisión de diseño verificada en el código); el "
        "rechazo archiva los pares vigentes y notifica al equipo. El administrador puede intervenir una propuesta "
        "aprobada, lo que la devuelve a `pendiente`, re-detecta similitudes y queda auditado como "
        "`devuelta_revision`.")
    M.p("**Proceso «unirme a ficha» (`ApprenticeController::unirmeAFicha`):** validación del código (404 si no "
        "existe; 422 si la ficha no está activa o ya es la propia), transacción que crea/actualiza la fila de "
        "aprendiz con derivación del programa de la ficha, y notificaciones al instructor nuevo y al anterior si "
        "hubo cambio. La salida limpia `id_class_group` e `id_programa` y notifica igualmente.")

    M.h2("3.9. Modelo entidad-relación")
    num = M.figura(FIG(P, "fig03_modelo_er.png"),
                   "Modelo entidad-relación (núcleo del esquema implementado)",
                   "Elaboración propia a partir de las migraciones reales de `backend/database/migrations`.", ancho_cm=16.4)
    M.p(f"La Figura {num} presenta el núcleo del esquema. El sistema es relacional puro: 18 tablas de dominio con "
        "claves foráneas declaradas y restricciones de integridad (el diccionario completo, con tipos y reglas "
        "ON DELETE, está en el Capítulo 4). Las relaciones estructurales más importantes son: general_users 1:1 "
        "con apprentices/instructors/admins; apprentices N:1 con class_groups y training_programs; class_groups "
        "N:1 con training_programs e instructors; training_programs N:1 con knowledge_networks; projects N:1 con "
        "creador, instructor asignado y ficha, y N:M con apprentices a través de apprentice_projects; "
        "similarities como relación N:M reflexiva entre projects (con par normalizado `id_proyecto_1 < "
        "id_proyecto_2`); comments autorreferenciada para hilos; y las tablas de trazabilidad (notifications, "
        "bug_reports, audit_logs, project_histories) colgando de usuarios y propuestas.")

    M.h2("3.10. Modelo de datos alternativo")
    M.p("No aplica: la persistencia es exclusivamente relacional. Se usan columnas `JSON` para estructuras "
        "polimórficas simples (`objetivos_especificos` de las propuestas, `detalles` de similitudes y bitácora, "
        "`detalle` del historial), lo que constituye un uso híbrido dentro del mismo motor y no un cambio de "
        "modelo de datos. No hay base documental, ni caché distribuida, ni almacenamiento externo de datos.")

    M.h2("3.11. Decisiones de diseño identificadas")
    M.tabla(
        "Decisiones de diseño con evidencia y compromiso (trade-off)",
        ["Decisión", "Justificación observada", "Compromiso / riesgo asociado"],
        [
            ("Sesión por cookie httpOnly con SPA en mismo origen", "Elimina el almacenamiento de tokens en el navegador y el preflight CORS (documentado en `vite.config.js` y `docs/deploy-railway.md`).", "Requiere proxy en desarrollo y despliegue conjunto web+API; la app móvil usa token aparte."),
            ("Token Bearer para móvil con cifrado en dispositivo", "Sanctum permite ambos modos sobre la misma API.", "Dos rutas de autenticación que mantener; expiración por variable de entorno."),
            ("Motor de similitud local en PHP puro", "Sin dependencias de IA de terceros, reproducible y sin costos (comentario en `SimilitudService`).", "Calidad de detección acotada al corpus; sin embeddings semánticos modernos."),
            ("Umbral y ventana en tabla de fila única `motor_configs`", "Ajuste operativo sin tocar código, auditado.", "Requiere recalcular el corpus tras cambios; pares antiguos se reanalizan."),
            ("Huella `sha1` anti-reenvío", "Evita duplicar propuestas y envíos vacíos (BUG-004 de la auditoría previa).", "Cambios triviales de formato cuentan como cambios."),
            ("Separar auditoría de seguridad (`audit_logs`) del historial funcional (`project_histories`)", "Distinta audiencia y distinta inmutabilidad (comentado en los soportes).", "Dos mecanismos de escritura que mantener."),
            ("Regla «Classroom» de solo lectura fuera de la ficha", "Protege el trabajo una vez termina la ficha; probado en `InteraccionPropuestaTest`.", "Usuarios sin ficha conservan propuestas pero no pueden editarlas."),
            ("Inclusión de relaciones por lista blanca (`?included=`)", "Control explícito de lo que se expone y carga; evita N+1 arbitrarios.", "Sin selección de columnas por relación (mitigado ocultando campos sensibles)."),
            ("Migraciones consolidadas antes de la entrega", "Esquema reproducible desde cero (commit `eeff159`).", "Bases creadas antes de la consolidación podrían requerir `migrate:fresh`."),
            ("Componentes compartidos `*Base` en el frontend", "Una sola implementación para las tres fachadas de rol (comentado en el README del frontend).", "Acoplamiento entre rol y presentación a gestionar con cuidado."),
        ],
        fuente="Código, comentarios del repositorio y auditoría previa (`AUDITORIA-INTEGRAL.md`).",
        anchos=[4.2, 6.8, 5.0],
        tamano=8.5,
    )

    # =====================================================================
    # CAPÍTULO 4
    # =====================================================================
    M.h1("CAPÍTULO 4. BASE DE DATOS", salto=True)

    M.h2("4.1. Motor y versión")
    M.p("El sistema usa **MySQL** como motor relacional (driver `pdo_mysql`). La verificación de esta auditoría "
        "se realizó sobre **MariaDB 10.4.32** (compatible), y la configuración de conexión es la estándar de "
        "Laravel: `utf8mb4` / `utf8mb4_unicode_ci`, modo estricto activado (`strict => true`) y motor InnoDB "
        "(por defecto de MySQL, ya que la configuración no lo fuerza). El entorno de producción previsto "
        "(Railway) suministra la base como servicio gestionado de la misma familia. **No existen** bases NoSQL, "
        "réplicas ni *sharding*.")

    M.h2("4.2. Configuración de conexión")
    M.p("La configuración efectiva se define por variables de entorno (`backend/.env`, no versionado) y "
        "`backend/config/database.php`. Las variables (solo nombres; los valores reales no se publican en este "
        "documento) son: `DB_CONNECTION` (`mysql`), `DB_HOST`, `DB_PORT` (3306), `DB_DATABASE` "
        "(`proyectwin_mvp` en la plantilla), `DB_USERNAME` y `DB_PASSWORD`. En el despliegue, el proveedor inyecta "
        "estas variables y el contenedor reintenta la migración hasta 24 veces al arrancar para tolerar el "
        "levantamiento de la base (`docker/entrypoint.sh`).")
    M.p("Nota de auditoría: para verificar el sistema sin tocar la base de datos del equipo, la auditoría creó una "
        "base separada `proyectwin_mvp_manual` y ejecutó sobre ella migraciones, seeders y pruebas. La base "
        "original `proyectwin_mvp` no fue modificada.")

    M.h2("4.3. Estructura de la base de datos")
    M.p("El esquema está compuesto por **20 tablas físicas**: 18 tablas de dominio (creadas por las migraciones "
        "del repositorio), la tabla `migrations` (control de Laravel) y `personal_access_tokens` (creada por "
        "Sanctum desde el paquete). La migración más reciente (`sessions`) se encuentra en el árbol de trabajo "
        "sin confirmar en Git al cierre de la auditoría y se documenta como tal. Composición por dominio:")
    M.tabla(
        "Tablas de la base de datos y su propósito",
        ["#", "Tabla", "Propósito", "Origen"],
        [
            ("1", "`general_users`", "Cuenta única de acceso (aprendiz, instructor o admin) con credenciales y datos personales.", "Migración 2026_04_09_023859"),
            ("2", "`knowledge_networks`", "Catálogo de redes de conocimiento.", "Migración 2026_04_09_024300"),
            ("3", "`training_programs`", "Catálogo de programas de formación (pertenece a una red).", "Migración 2026_04_09_024326"),
            ("4", "`instructors`", "Perfil de instructor (1:1 con `general_users`).", "Migración 2026_04_09_024350"),
            ("5", "`class_groups`", "Fichas de formación con código de unión y estados activo/finalizado/anulada.", "Migración 2026_04_09_024400"),
            ("6", "`apprentices`", "Perfil de aprendiz (1:1 con `general_users`), con ficha y programa.", "Migración 2026_04_09_024516"),
            ("7", "`admins`", "Perfil de administrador (1:1 con `general_users`).", "Migración 2026_04_09_030055"),
            ("8", "`projects`", "Propuestas de proyecto formativo con estados y huella de contenido.", "Migración 2026_04_09_030220"),
            ("9", "`similarities`", "Pares de propuestas con similitud detectada (vigentes e históricos).", "Migración 2026_04_09_030704"),
            ("10", "`notifications`", "Avisos internos por usuario (dominio propio, no la tabla estándar de Laravel).", "Migración 2026_04_09_031912"),
            ("11", "`bug_reports`", "Reportes de falla y solicitudes de soporte.", "Migración 2026_04_09_032118"),
            ("12", "`apprentice_projects`", "Pivote N:M del equipo de cada propuesta.", "Migración 2026_04_09_032359"),
            ("13", "`comments`", "Observaciones y respuestas anidadas sobre propuestas.", "Migración 2026_04_09_032402"),
            ("14", "`motor_configs`", "Parámetros del motor (umbral y ventana); fila única.", "Migración 2026_04_10_000002"),
            ("15", "`audit_logs`", "Bitácora inmutable de acciones sensibles.", "Migración 2026_04_11_000002"),
            ("16", "`password_reset_tokens`", "Tokens del flujo de recuperación de contraseña.", "Migración 2026_04_11_000003"),
            ("17", "`project_histories`", "Línea de tiempo funcional de cada propuesta.", "Migración 2026_10_06_000002"),
            ("18", "`sessions`", "Sesiones en base de datos (mejora de despliegue; migración en curso al cierre de la auditoría).", "Migración 2026_10_16_000001 (sin confirmar en Git)"),
            ("19", "`personal_access_tokens`", "Tokens Sanctum (móvil y pruebas).", "Paquete laravel/sanctum 3.x"),
            ("20", "`migrations`", "Control interno de migraciones ejecutadas.", "Framework Laravel"),
        ],
        fuente="`backend/database/migrations` y verificación en MariaDB (16/10/2026).",
        anchos=[0.8, 3.6, 8.4, 3.2],
        tamano=8.5,
    )

    M.h2("4.4. Tablas, colecciones o estructuras persistentes")
    M.p("Además de las tablas, el sistema persiste: sesiones y caché en archivos (en producción se prevé "
        "`sessions` en base de datos), logs en `storage/logs/laravel.log` y fotografías de perfil como cadenas "
        "en `general_users.foto_url`. No hay colecciones documentales ni almacenamiento de objetos externo.")

    M.h2("4.5. Diccionario de datos")
    M.p("Diccionario completo de las 18 tablas de dominio, en su **estado final consolidado** (las migraciones "
        "eliminadas durante la refactorización quedaron fusionadas en los archivos de creación). Convenciones: "
        "`UQ` = restricción única; `FK` = clave foránea; los tipos se expresan en su forma DDL de MySQL. Ninguna "
        "tabla declara comentarios DDL (los comentarios citados provienen del código fuente).")

    def dic(nombre, proposito, filas, extra=""):
        M.h3(f"4.5.{dic.n}. `{nombre}`")
        dic.n += 1
        M.p(proposito + (" " + extra if extra else ""), espacio_despues=4)
        M.tabla(
            f"Diccionario de datos — {nombre}",
            ["Columna", "Tipo", "Nulo", "Predet.", "Notas"],
            filas,
            fuente="Migración correspondiente en `backend/database/migrations`.",
            anchos=[3.4, 3.4, 1.2, 1.8, 5.8],
            tamano=8,
        )
    dic.n = 1

    dic("general_users",
        "Cuenta base del sistema (los perfiles viven en `apprentices`, `instructors` o `admins`). Implementa la autenticación (`Authenticatable`) y la recuperación de contraseña por correo.",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("nombre", "VARCHAR(255)", "No", "—", ""),
            ("apellido", "VARCHAR(255)", "No", "—", ""),
            ("tipo_documento", "VARCHAR(20)", "Sí", "NULL", "p. ej. CC en los datos demo"),
            ("numero_documento", "VARCHAR(40)", "Sí", "NULL", "UQ; oculto en JSON por `$hidden`"),
            ("correo", "VARCHAR(255)", "No", "—", "UQ; usado para recuperación"),
            ("username", "VARCHAR(255)", "Sí", "NULL", "UQ; credencial de acceso (login por usuario)"),
            ("password", "VARCHAR(255)", "No", "—", "Hash bcrypt"),
            ("must_change_password", "TINYINT(1)", "No", "0", "Primer ingreso con contraseña temporal"),
            ("password_temporal", "TEXT", "Sí", "NULL", "Cifrada con `Crypt`; solo para exportar el PDF"),
            ("credenciales_enviadas_en", "TIMESTAMP", "Sí", "NULL", "Bitácora de envío de credenciales"),
            ("credenciales_error", "VARCHAR(500)", "Sí", "NULL", "Último error de envío (si hubo)"),
            ("foto_url", "TEXT", "Sí", "NULL", "Data URL o URL; procesada a 256 px en el cliente"),
            ("rol", "ENUM('aprendiz','instructor','admin')", "No", "—", "Rol único del sistema"),
            ("estado", "TINYINT(1)", "No", "1", "1 activo / 0 suspendido"),
            ("remember_token", "VARCHAR(100)", "Sí", "NULL", "Recordar sesión"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", "Auditoría de fila"),
        ])

    dic("knowledge_networks",
        "Catálogo institucional de redes de conocimiento; agrupa programas.",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("nombre", "VARCHAR(255)", "No", "—", "UQ"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", ""),
        ])

    dic("training_programs",
        "Programas de formación (ADSO, Multimedia, etc.) vinculados a una red.",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("nombre", "VARCHAR(255)", "No", "—", ""),
            ("nivel", "VARCHAR(255)", "No", "—", "p. ej. «Tecnólogo»"),
            ("knowledge_network_id", "BIGINT UNSIGNED", "No", "—", "FK → knowledge_networks.id (RESTRICT)"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", ""),
        ],
        extra="La columna histórica `num_trimestres` fue retirada y no debe reintroducirse (comentario en la migración).")

    dic("instructors",
        "Perfil 1:1 del rol instructor.",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("fecha_ingreso", "DATE", "No", "—", "Normalizada a Y-m-d por el modelo"),
            ("id_usuario", "BIGINT UNSIGNED", "No", "—", "UQ; FK → general_users.id (CASCADE)"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", ""),
        ])

    dic("class_groups",
        "Fichas de formación. El código de unión es una credencial: lo genera el servidor.",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("codigo", "VARCHAR(255)", "No", "—", "UQ; formato xxx-xxxx"),
            ("numero", "VARCHAR(255)", "Sí", "NULL", "UQ; al anular se libera (NULL) conservando historial"),
            ("nombre", "VARCHAR(255)", "No", "—", ""),
            ("estado", "ENUM('activo','finalizado','anulada')", "No", "'activo'", "Gobierna la participación de aprendices"),
            ("id_programa", "BIGINT UNSIGNED", "No", "—", "FK → training_programs.id (RESTRICT)"),
            ("id_instructor", "BIGINT UNSIGNED", "No", "—", "FK → instructors.id (RESTRICT)"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", ""),
        ])

    dic("apprentices",
        "Perfil 1:1 del rol aprendiz; puede existir sin ficha.",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("codigo", "VARCHAR(255)", "No", "—", "UQ; formato AP-###"),
            ("id_class_group", "BIGINT UNSIGNED", "Sí", "NULL", "FK → class_groups.id (CASCADE al borrar ficha)"),
            ("id_usuario", "BIGINT UNSIGNED", "No", "—", "UQ; FK → general_users.id (CASCADE)"),
            ("id_programa", "BIGINT UNSIGNED", "Sí", "NULL", "FK → training_programs.id (RESTRICT); sin ficha puede ser NULL"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", ""),
        ],
        extra="Hallazgo histórico: el borrado de una ficha elimina la fila del aprendiz (CASCADE) y con ella sus vínculos de equipo; la auditoría previa lo documentó y el flujo de «salir de ficha» mitiga el caso normal anulando la referencia.")

    dic("admins",
        "Perfil 1:1 del rol administrador.",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("id_usuario", "BIGINT UNSIGNED", "No", "—", "UQ; FK → general_users.id (CASCADE)"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", ""),
        ])

    dic("projects",
        "Propuestas de proyecto formativo; nacen como borrador y admiten campos incompletos hasta el envío.",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("titulo", "VARCHAR(255)", "Sí", "NULL", "Obligatorio para enviar (validación de negocio)"),
            ("resumen", "TEXT", "Sí", "NULL", ""),
            ("palabras_clave", "TEXT", "Sí", "NULL", ""),
            ("area_aplicacion", "VARCHAR(255)", "Sí", "NULL", ""),
            ("objetivo_general", "TEXT", "Sí", "NULL", ""),
            ("objetivos_especificos", "JSON", "Sí", "NULL", "Cast a arreglo en el modelo"),
            ("estado", "ENUM('borrador','pendiente','aprobado','rechazado')", "No", "'pendiente'", "INDEX"),
            ("huella_envio", "VARCHAR(40)", "Sí", "NULL", "sha1 del contenido enviado"),
            ("id_creador", "BIGINT UNSIGNED", "No", "—", "FK → general_users.id (CASCADE)"),
            ("id_instructor_asignado", "BIGINT UNSIGNED", "Sí", "NULL", "FK → instructors.id (SET NULL)"),
            ("id_class_group", "BIGINT UNSIGNED", "Sí", "NULL", "FK → class_groups.id (RESTRICT: no se borra ficha con propuestas)"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", ""),
        ])

    dic("similarities",
        "Pares de propuestas con similitud detectada; guarda siempre el id menor primero.",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("porcentaje", "DOUBLE", "No", "—", "0–100"),
            ("detalles", "JSON", "Sí", "NULL", "Señales, pesos, términos y pasajes"),
            ("fecha", "DATE", "Sí", "NULL", ""),
            ("id_proyecto_1", "BIGINT UNSIGNED", "No", "—", "FK → projects.id (CASCADE)"),
            ("id_proyecto_2", "BIGINT UNSIGNED", "No", "—", "FK → projects.id (CASCADE)"),
            ("vigente", "TINYINT(1)", "No", "1", "1 = contenido actual; 0 = evidencia histórica. INDEX"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", ""),
        ],
        extra="Índice normal (no único) sobre `(id_proyecto_1, id_proyecto_2)`: el mismo par puede reaparecer como versiones vigente/histórica; la serialización del motor se logra con bloqueo sobre `motor_configs`.")

    dic("notifications",
        "Avisos internos por usuario. Es una tabla del dominio (no la tabla nativa de notificaciones de Laravel).",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("titulo", "VARCHAR(255)", "No", "—", ""),
            ("descripcion", "TEXT", "Sí", "NULL", ""),
            ("tipo", "ENUM('similitud','observacion','revision','mensaje','sistema')", "No", "—", "Validado también en el controlador"),
            ("enlace", "VARCHAR(255)", "Sí", "NULL", "Formato `proyecto:{id}`, `ficha:{id}`, `reporte:{id}`"),
            ("leida", "TINYINT(1)", "No", "0", ""),
            ("fecha", "DATE", "No", "—", ""),
            ("id_usuario", "BIGINT UNSIGNED", "No", "—", "FK → general_users.id (CASCADE)"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", ""),
        ])

    dic("bug_reports",
        "Reportes de falla del usuario; también funciona como bandeja de soporte por conflictos de ficha.",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("titulo", "VARCHAR(255)", "Sí", "NULL", "Autogenerado si no llega («Conflicto con la ficha N»)"),
            ("numero_ficha", "VARCHAR(30)", "Sí", "NULL", "Ficha involucrada (soporte)"),
            ("motivo", "VARCHAR(120)", "Sí", "NULL", "Motivo corto del soporte"),
            ("descripcion", "TEXT", "No", "—", ""),
            ("tipo", "ENUM('sistema','proyecto','datos','bug_ui','error_datos','rendimiento','seguridad','otro')", "No", "—", ""),
            ("estado", "ENUM('pendiente','en_revision','resuelto','cerrado','rechazado')", "No", "'pendiente'", "El alta siempre inicia en `pendiente`"),
            ("respuesta", "TEXT", "Sí", "NULL", "Respuesta con la que el admin cierra"),
            ("fecha", "DATE", "No", "—", ""),
            ("id_usuario", "BIGINT UNSIGNED", "No", "—", "FK → general_users.id (CASCADE)"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", ""),
        ])

    dic("apprentice_projects",
        "Pivote del equipo de trabajo: qué aprendices participan en qué propuesta.",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("id_aprendiz", "BIGINT UNSIGNED", "No", "—", "FK → apprentices.id (CASCADE)"),
            ("id_proyecto", "BIGINT UNSIGNED", "No", "—", "FK → projects.id (CASCADE)"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", ""),
        ],
        extra="UQ compuesta `(id_aprendiz, id_proyecto)`: una participación única por propuesta. El creador no puede retirarse de su propio equipo (regla 409).")

    dic("comments",
        "Observaciones sobre propuestas, con respuestas anidadas.",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("texto", "TEXT", "No", "—", ""),
            ("id_proyecto", "BIGINT UNSIGNED", "No", "—", "FK → projects.id (CASCADE)"),
            ("id_usuario", "BIGINT UNSIGNED", "No", "—", "FK → general_users.id (CASCADE); firmado por el servidor"),
            ("respuesta_a", "BIGINT UNSIGNED", "Sí", "NULL", "FK autorreferente → comments.id (CASCADE: borra el hilo completo)"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", ""),
        ])

    dic("motor_configs",
        "Parámetros del motor de similitud. Tabla de fila única (id = 1).",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK (convención: solo existe id 1)"),
            ("umbral", "DOUBLE", "No", "0.2", "Porcentaje mínimo; el seeder lo fija en 0.30. Rango validado 0,05–0,95"),
            ("meses", "INT", "No", "12", "Ventana del corpus. Rango validado 1–60"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", ""),
        ])

    dic("audit_logs",
        "Bitácora de acciones sensibles: inmutable (solo inserción y consulta por API).",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("id_usuario", "BIGINT UNSIGNED", "Sí", "NULL", "FK → general_users.id (SET NULL): si se borra el actor, la fila se conserva"),
            ("accion", "VARCHAR(255)", "No", "—", "INDEX; p. ej. `crear_usuario`, `enviar_propuesta`, `config_motor`"),
            ("entidad", "VARCHAR(255)", "Sí", "NULL", "Composición de INDEX con `entidad_id`"),
            ("entidad_id", "BIGINT UNSIGNED", "Sí", "NULL", "Referencia lógica polimórfica (sin FK)"),
            ("detalle", "JSON", "Sí", "NULL", "Cast a arreglo"),
            ("ip", "VARCHAR(255)", "Sí", "NULL", "IP del actor (tras proxy, según `TrustProxies`)"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", "INDEX en `created_at` para consultas por fecha"),
        ])

    dic("password_reset_tokens",
        "Tokens del flujo de recuperación de contraseña (broker de Laravel).",
        [
            ("email", "VARCHAR(255)", "No", "—", "PK; almacena el valor de `general_users.correo`"),
            ("token", "VARCHAR(255)", "No", "—", "Hash del token enviado por correo"),
            ("created_at", "TIMESTAMP", "Sí", "NULL", "Vigencia: 60 minutos; throttling 60 s"),
        ])

    dic("project_histories",
        "Evolución funcional y visible de cada propuesta (separada de la bitácora de seguridad).",
        [
            ("id", "BIGINT UNSIGNED AUTO_INCREMENT", "No", "—", "PK"),
            ("id_proyecto", "BIGINT UNSIGNED", "No", "—", "FK → projects.id (CASCADE)"),
            ("id_usuario", "BIGINT UNSIGNED", "Sí", "NULL", "FK → general_users.id (SET NULL)"),
            ("accion", "VARCHAR(255)", "No", "—", "creada, enviada, reenviada, actualizada, aprobada, rechazada, devuelta_revision"),
            ("detalle", "JSON", "Sí", "NULL", "Cast a arreglo"),
            ("created_at / updated_at", "TIMESTAMP", "Sí", "NULL", "INDEX compuesto `(id_proyecto, created_at)`"),
        ])

    dic("sessions",
        "Almacenamiento de sesiones en base de datos (mejora de despliegue para no perder sesiones al redesplegar).",
        [
            ("id", "VARCHAR(255)", "No", "—", "PK"),
            ("user_id", "BIGINT UNSIGNED", "Sí", "NULL", "INDEX (no FK declarada)"),
            ("ip_address", "VARCHAR(45)", "Sí", "NULL", ""),
            ("user_agent", "TEXT", "Sí", "NULL", ""),
            ("payload", "LONGTEXT", "No", "—", "Contenido serializado de la sesión"),
            ("last_activity", "INT", "No", "—", "INDEX"),
        ],
        extra="Al cierre de la auditoría esta migración estaba en el árbol de trabajo sin confirmar en Git; su adopción requiere además `SESSION_DRIVER=database` en el entorno.")

    M.h3("4.5.19. `personal_access_tokens` (gestionada por Sanctum)")
    M.p("Tabla estándar del paquete `laravel/sanctum` (no versionada en `database/migrations`; el paquete la "
        "registra automáticamente). Almacena los tokens Bearer: `id` (PK), `tokenable_type`/`tokenable_id` "
        "(morfología hacia `general_users`), `name`, `token` (UQ, hash sha256), `abilities`, `last_used_at`, "
        "`expires_at` y timestamps. La expiración se controla con `SANCTUM_EXPIRATION` (1440 minutos en la "
        "plantilla).")

    M.h2("4.6. Tipos de datos")
    M.tabla(
        "Tipos de datos empleados (convención Laravel 10 → MySQL)",
        ["Declaración en la migración", "Tipo MySQL resultante", "Uso en el esquema"],
        [
            ("`id()` / `bigIncrements`", "BIGINT UNSIGNED AUTO_INCREMENT", "Claves primarias de todas las tablas"),
            ("`foreignId()`", "BIGINT UNSIGNED", "Claves foráneas"),
            ("`string('x')`", "VARCHAR(255)", "Nombres, títulos, códigos, enums textuales de bajo volumen"),
            ("`text()` / `longText`", "TEXT / LONGTEXT", "Descripciones, objetivos, respuestas, payload de sesión"),
            ("`json()`", "JSON", "objetivos_especificos, detalles, detalle (histórico/auditoría)"),
            ("`enum(...)`", "ENUM", "rol, estados de ficha/propuesta/reporte, tipos de notificación"),
            ("`boolean()`", "TINYINT(1)", "estado, leida, vigente, must_change_password"),
            ("`float()`", "DOUBLE", "porcentaje y umbral (nota: Laravel mapea `float` a DOUBLE en MySQL)"),
            ("`integer()`", "INT", "meses del motor, last_activity"),
            ("`date()`", "DATE", "fecha de ingreso, fecha de similitud/aviso/reporte"),
            ("`timestamps()`", "TIMESTAMP NULL", "created_at / updated_at"),
            ("`timestamp()` / `rememberToken()`", "TIMESTAMP / VARCHAR(100)", "Marcas de credenciales y «recordarme»"),
        ],
        fuente="Migraciones reales (auditoría).",
        anchos=[4.2, 4.2, 7.6],
        tamano=8.5,
    )

    M.h2("4.7. Claves primarias y foráneas")
    M.tabla(
        "Claves foráneas declaradas (estado final)",
        ["Tabla", "Columna", "Referencia", "ON DELETE"],
        [
            ("training_programs", "knowledge_network_id", "knowledge_networks.id", "RESTRICT (implícito)"),
            ("instructors", "id_usuario", "general_users.id", "CASCADE"),
            ("class_groups", "id_programa", "training_programs.id", "RESTRICT (implícito)"),
            ("class_groups", "id_instructor", "instructors.id", "RESTRICT (implícito)"),
            ("apprentices", "id_class_group", "class_groups.id", "CASCADE"),
            ("apprentices", "id_usuario", "general_users.id", "CASCADE"),
            ("apprentices", "id_programa", "training_programs.id", "RESTRICT"),
            ("admins", "id_usuario", "general_users.id", "CASCADE"),
            ("projects", "id_creador", "general_users.id", "CASCADE"),
            ("projects", "id_instructor_asignado", "instructors.id", "SET NULL"),
            ("projects", "id_class_group", "class_groups.id", "RESTRICT"),
            ("similarities", "id_proyecto_1 / id_proyecto_2", "projects.id", "CASCADE en ambas"),
            ("notifications", "id_usuario", "general_users.id", "CASCADE"),
            ("bug_reports", "id_usuario", "general_users.id", "CASCADE"),
            ("apprentice_projects", "id_aprendiz", "apprentices.id", "CASCADE"),
            ("apprentice_projects", "id_proyecto", "projects.id", "CASCADE"),
            ("comments", "id_proyecto", "projects.id", "CASCADE"),
            ("comments", "id_usuario", "general_users.id", "CASCADE"),
            ("comments", "respuesta_a", "comments.id", "CASCADE (borra el hilo)"),
            ("audit_logs", "id_usuario", "general_users.id", "SET NULL"),
            ("project_histories", "id_proyecto", "projects.id", "CASCADE"),
            ("project_histories", "id_usuario", "general_users.id", "SET NULL"),
        ],
        fuente="Migraciones (auditoría). ON UPDATE no se declara en ninguna FK (comportamiento por defecto).",
        anchos=[3.6, 3.8, 4.0, 4.6],
        tamano=8.5,
    )
    M.p("Las claves primarias son `BIGINT UNSIGNED AUTO_INCREMENT` en todas las tablas salvo "
        "`password_reset_tokens` (PK `email`) y `sessions` (PK `id` de tipo VARCHAR).")

    M.h2("4.8. Relaciones y restricciones")
    M.bullets([
        "**Un perfil por usuario** por rol: `instructors.id_usuario`, `apprentices.id_usuario` y `admins.id_usuario` son únicos.",
        "**Par de similitud normalizado:** el modelo ordena `id_proyecto_1 < id_proyecto_2` antes de guardar (invariante probado en `IntegridadDatosTest`).",
        "**Participación única de equipo:** UQ compuesta en `apprentice_projects`.",
        "**Número de ficha reutilizable:** `class_groups.numero` es único pero admite `NULL`; la anulación libera el número conservando el historial (`FichaNumeroUnicoTest`).",
        "**Códigos únicos generados por el servidor:** `class_groups.codigo` (xxx-xxxx), `apprentices.codigo` (AP-###), `general_users.username` (patrón seguro) y `numero_documento`.",
        "**Borrados con salvaguardas:** la ficha con propuestas se anula en lugar de eliminarse; el programa con aprendices no se borra (RESTRICT); el usuario no puede autoeliminarse ni dejar el sistema sin administradores activos (409).",
        "**Retención de evidencia:** las similitudes archivadas nunca se borran al rechazar o reenviar; la bitácora no expone operaciones de escritura vía API.",
    ])

    M.h2("4.9. Índices")
    M.tabla(
        "Índices declarados explícitamente",
        ["Tabla", "Índice", "Objetivo"],
        [
            ("projects", "`estado`", "Filtros de bandeja por estado (añadido en la consolidación)."),
            ("similarities", "`(id_proyecto_1, id_proyecto_2)` (normal)", "Búsqueda de pares por propuesta; reemplazó al UNIQUE al introducir el histórico."),
            ("similarities", "`vigente`", "Separar pares actuales de evidencia histórica."),
            ("audit_logs", "`accion`; `(entidad, entidad_id)`; `created_at`", "Consulta por acción, por entidad afectada y por rango de fechas."),
            ("project_histories", "`(id_proyecto, created_at)`", "Línea de tiempo por propuesta."),
            ("personal_access_tokens", "`(tokenable_type, tokenable_id)`; `token` UQ", "Búsqueda y revocación de tokens Sanctum."),
        ],
        fuente="Migraciones y paquete Sanctum (auditoría).",
        anchos=[3.2, 6.2, 6.6],
        tamano=8.5,
    )

    M.h2("4.10. Migraciones y scripts de inicialización")
    M.p("Existen 18 migraciones (listadas en § 4.3 con su tabla). No hay migraciones tipo ALTER vigentes: las "
        "modificaciones históricas fueron consolidadas dentro de las creaciones (commit `eeff159`), de modo que "
        "`migrate:fresh` reproduce el esquema final exacto. La inicialización de datos se realiza con dos seeders:")
    M.bullets([
        "**`DatabaseSeeder`** (invocado por defecto): escenario completo de demostración — 15 usuarios (9 aprendices, 4 instructores, 2 admins), 4 fichas, 3 programas, 2 redes, 16 propuestas en todos los estados, equipos, similitudes (incluye un par histórico deliberado), notificaciones, historiales, 6 reportes de soporte, comentarios y auditorías. Ejecuta además el motor real (`Recomputador::recalcular`) para dejar datos coherentes.",
        "**`VolumenSeeder`** (manual): añade 5 instructores, 5 fichas, 40 aprendices y 80 propuestas para probar listados y paginación con volumen. El README lo recomienda únicamente para pruebas de carga visual.",
    ])
    M.codigo(
        "# Preparación completa del esquema y datos demo (verificado en la auditoría)\n"
        "php artisan migrate:fresh --seed        # 18 migraciones + escenario demo\n"
        "php artisan db:seed --class=VolumenSeeder   # (opcional) datos de volumen\n"
        "php artisan similitud:recalcular        # recalibra el corpus con el umbral vigente\n"
        "php artisan similitud:calibrar          # sugiere umbral por programa (opcional)\n"
        "php artisan perfiles:docentes           # backfill de perfiles de instructor faltantes",
        archivo="Comandos verificados contra `backend/README.md` y ejecución real del 16/10/2026.")

    M.h2("4.11. Copia de seguridad y restauración")
    M.p("**No se encontró ningún procedimiento de copia de seguridad implementado en el repositorio** (ni scripts "
        "de respaldo, ni configuración de retención). Se documentan a continuación **procedimientos recomendados** "
        "—no implementados— para el entorno MySQL/MariaDB y para el servicio gestionado de Railway:")
    M.codigo(
        "# Respaldo lógico recomendado (entorno local o servidor con acceso CLI)\n"
        "mysqldump -u USUARIO -p --single-transaction --routines --triggers \\\n"
        "  proyectwin_mvp > respaldo_proyectwin_AAAA-MM-DD.sql\n\n"
        "# Restauración\n"
        "mysql -u USUARIO -p proyectwin_mvp < respaldo_proyectwin_AAAA-MM-DD.sql",
        archivo="Procedimiento recomendado (no existe en el repositorio).")
    M.bullets([
        "En Railway, el plugin MySQL gestiona copias propias del proveedor; deben verificarse en el panel y complementarse con un volcado lógico programado si el dato es crítico.",
        "Frecuencia sugerida: diaria en periodos de uso activo; retención mínima de 30 días; prueba de restauración trimestral.",
        "Antes de aplicar cambios estructurales (migraciones nuevas), ejecutar un respaldo manual y verificar `php artisan migrate --pretend` cuando sea viable.",
    ])

    M.h2("4.12. Integridad y validación de datos")
    M.p("La integridad se protege en tres niveles: (1) **restricciones de base de datos** (claves, únicos, "
        "restricciones de borrado y modo estricto); (2) **validaciones de la aplicación** (reglas `validate()` en "
        "cada controlador, listas blancas de campos, invariantes de dominio como el orden del par o la "
        "participación única); y (3) **transacciones y bloqueos** en las operaciones compuestas.")
    M.p("Como evidencia, se ejecutaron sobre la base recién sembrada (`proyectwin_mvp_manual`, 16/10/2026, modo "
        "solo lectura) diez consultas de integridad; todas devolvieron cero incidencias:")
    M.tabla(
        "Verificación de integridad ejecutada durante la auditoría",
        ["Chequeo", "Resultado"],
        [
            ("Pivotes de equipo con proyecto o aprendiz inexistente", "0"),
            ("Comentarios y notificaciones con usuario inexistente", "0"),
            ("Propuestas con ficha inexistente", "0"),
            ("Pares de similitud vigentes duplicados", "0"),
            ("Porcentajes fuera del rango 0–100", "0"),
            ("Usuarios sin `username` y aprendices sin `codigo`", "0"),
            ("Pares con orden invertido (`id_proyecto_1 >= id_proyecto_2`)", "0"),
            ("Distribución de propuestas resultante del seed", "1 borrador · 10 pendientes · 3 aprobadas · 2 rechazadas"),
            ("Pares de similitud resultantes del motor", "7 vigentes · 3 históricos"),
        ],
        fuente="Consultas ejecutadas por la auditoría sobre `proyectwin_mvp_manual` (16/10/2026).",
        anchos=[10.4, 5.6],
        tamano=8.5,
    )
    M.p("Limitación: la auditoría no ejecutó pruebas de concurrencia (dos envíos simultáneos sobre la misma "
        "propuesta o ficha). El código mitiga la concurrencia con `lockForUpdate`, pero la garantía no fue "
        "comprobada empíricamente en esta revisión.")

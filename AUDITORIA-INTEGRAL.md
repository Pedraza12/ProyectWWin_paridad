# INFORME DE AUDITORÍA INTEGRAL — ProyecTWIN MVP

> ## ⚠️ Nota de vigencia (18-oct-2026)
>
> **Documento histórico:** este informe es un snapshot del 5-oct-2026 y su cuerpo se conserva sin cambios (no se reescribe la historia). La mayoría de los hallazgos fue corregida y verificada después: ver `HISTORIAL-FIXES.md`, los commits del repo y las suites.
>
> **Estado actual resumido (18-oct-2026):** backend **314/314 pruebas (1797 aserciones)**; gate E2E integral **5/5 etapas · 347/347 pruebas** (los 4 specs nuevos del Lote 2, ejecutados y aprobados); frontend unit **63 tests en 18 archivos**, lint **0 errores**; Android **139 tests unitarios en 23 archivos, 0 fallos** + `assembleDebug` OK. La guarda de evidencia D-2b × BUG-007 está implementada: `DELETE /projects/{id}` y las cascadas de instructor/programa/red responden 409 con pares vigentes o históricos.
>
> Los conteos y afirmaciones del cuerpo (p. ej. «218 tests backend», «46 archivos E2E») corresponden a la fecha del snapshot y están desactualizados.

**Fecha:** 5 de octubre de 2026
**Alcance:** `MVP/backend` (Laravel 10), `MVP/frontend` (React), BD MySQL `proyectwin_mvp`, tests, seeders y documentación.
**Método:** revisión estática de código + consultas SQL de solo lectura + ejecución de ESLint + verificación cruzada de hallazgos. **No se modificó ningún archivo ni dato; no se ejecutaron migraciones/seeders; no se hicieron commits.**

---

## 1. RESUMEN EJECUTIVO

El sistema está funcionalmente maduro: 218 tests backend y 46 archivos E2E pasan, los flujos F1–F8 están implementados con scoping por rol en la mayoría de recursos, y el motor de similitudes es robusto ante casos límite. Sin embargo, la auditoría encontró **2 problemas críticos** y varios altos:

1. **Fuga de PII vía `included`** (🔴): cualquier usuario autenticado recibe `numero_documento`, `username`, `must_change_password` y `credenciales_error` de terceros al pedir relaciones `generalUser` (p. ej. un aprendiz cargando su propia ficha obtiene los datos completos de todos sus compañeros). `Included` valida rutas de relación, pero no campos.
2. **Traslado no autorizado de aprendices entre fichas** (🔴): un instructor puede mover a cualquier aprendiz de otra ficha a la suya (vía `agregarAprendiz`, `store` o `update` de ficha) sin validar la ficha de origen ni notificar.

Adicionalmente: lectura de pasajes de propuestas ajenas vía `similarities?proyecto_id=`, duplicación de propuestas si falla el envío inicial, similitudes obsoletas tras intervención admin, filtros de estado muertos en el panel de similitudes admin, y pérdida de filas/equipos de aprendices al borrar una ficha. La documentación de backend está desactualizada (README Laravel genérico, `.env.example` con `DB_DATABASE=laravel`).

**Veredicto:** 🔴 **NO LISTO** hasta cerrar los dos críticos (el resto puede planificarse).

---

## 2. INVENTARIO REVISADO

**Backend:** `routes/api-v1.php` (162 líneas, 60+ endpoints), 16 controladores API, 20 modelos, 23 migraciones, 2 seeders, 9 servicios/support (`Credenciales`, `AltaUsuario`, `CredencialesCorreo`, `CredencialesPdf`, `BorradoCascada`, `Included`, `HistorialProyecto`, `Auditoria`, `NotificacionesService`), motor (`Recomputador`, `SimilitudService`, `Lsa`, `Pasajes`), 3 middleware (`CuentaActiva`, `CambioContrasenaObligatorio`, `RolMiddleware`), 2 comandos artisan, 2 notificaciones mail, 30 archivos Feature.

**Frontend:** 40+ páginas, ~50 componentes, 6 contextos (`Auth`, `Theme`, `Motor`, etc.), `lib/api.js` (cliente con caché/CSRF/401), `lib/recursos.js` (contratos API), guards (`ProtectedRoute`, `SafeRoute`), 53 specs E2E, 16 archivos unit.

**BD (19 tablas):** `general_users`, `instructors`, `apprentices`, `admins`, `class_groups`, `training_programs`, `knowledge_networks`, `projects`, `similarities`, `apprentice_projects`, `comments`, `notifications`, `bug_reports`, `motor_configs`, `audit_logs`, `project_histories`, `password_reset_tokens`, `personal_access_tokens`, `failed_jobs` (+ `migrations`). Consultas de integridad ejecutadas: 0 huérfanos en pivotes/comentarios/historial/auditoría/notificaciones, 0 pares duplicados vigentes, 0 porcentajes fuera de rango, 0 usuarios sin username, 0 `must_change_password` sin temporal.

**Documentación:** `README.md` backend (genérico), `README-ALCANCE.md`, `frontend/README.md`, `frontend/docs/ALINEACION-FRONTEND-BACKEND.md`, `CHANGELOG.md`, `HISTORIAL-FIXES.md`, `docs/arquitectura.md`, `docs/guion-presentacion.md`, `.env.example`.

---

## 3. HALLAZGOS CRÍTICOS

### BUG-001 🔴 — Fuga de PII y metadatos de cuenta vía `included`

- **Área:** Seguridad / API
- **Rol afectado:** Todos los autenticados (especialmente Aprendiz)
- **Archivos:** `backend/app/Support/Included.php:14-31`; `backend/app/Models/GeneralUser.php:23-38,70`; `backend/app/Models/ClassGroup.php:19`; `backend/app/Models/Project.php:27-31`; `backend/app/Models/Similarity.php:44-49`; `frontend/src/lib/recursos.js:77,97-102`
- **Problema:** `Included` valida que la ruta de relación esté en la allowlist, pero serializa el modelo completo. `GeneralUser::$hidden` solo oculta `password`, `remember_token`, `password_temporal`; expone `tipo_documento`, `numero_documento`, `username`, `must_change_password`, `credenciales_error`, `estado`.
- **Comportamiento actual:** `GET /v1/class-groups/1?included=apprentices.generalUser` (permitido a un aprendiz de la ficha, ruta `api-v1.php:107`) devuelve todos los aprendices con su usuario completo, incluidos documentos y username. Igual con `GET /v1/projects?included=apprentices.generalUser` y `GET /v1/similarities?included=project1.creator,project2.apprentices.generalUser`.
- **Comportamiento esperado:** solo datos de contacto (nombre, apellido, correo, foto, rol), como hace el endpoint `perfil` (`GeneralUserController.php:152-164`).
- **Cómo reproducir:** login como `mgonzalez` → `GET /v1/class-groups/1?included=apprentices.generalUser` con cookie/CSRF → inspeccionar `apprentices[].generalUser.numero_documento`/`username`.
- **Impacto:** exfiltración de cédulas, usernames y estado de credenciales de terceros; violación de privacidad institucional.
- **Causa probable:** allowlist por relación sin selección de columnas ni Resource.
- **Solución propuesta:** ocultar campos sensibles en `GeneralUser` (`$hidden` ampliado) y/o permitir includes de `generalUser` solo con columnas seguras (select) o un Resource; ajustar `perfil` como único contrato público.
- **Prueba recomendada:** Feature: aprendiz pide su ficha con include y se asserta que la respuesta NO contiene `numero_documento`, `username`, `must_change_password`, `credenciales_error`.

### BUG-002 🔴 — Traslado no autorizado de aprendices entre fichas (instructor)

- **Área:** Seguridad / Lógica de negocio
- **Rol afectado:** Instructor, Aprendiz (víctima)
- **Archivos:** `backend/app/Http/Controllers/Api/ClassGroupController.php:116-153, 392-432` (`asociarAprendiz`), `:57-73` (store), `:228-263` (update)
- **Problema:** `asociarAprendiz()` hace `firstOrCreate(['id_usuario' => …])` + `update(['id_class_group' => …])` sin verificar la ficha de origen del aprendiz. `puedeGestionar()` solo valida la ficha destino.
- **Comportamiento actual:** un instructor con su propia ficha envía `POST /v1/class-groups/{suFicha}/aprendices {usuario_id: X}` con X de otra ficha/carrera y lo "roba" silenciosamente (la fila `apprentices` es única por usuario). También vía `PUT /v1/class-groups/{suFicha} {aprendices:[…]}` y al crear ficha (`store`).
- **Comportamiento esperado:** solo se pueden asociar aprendices sin ficha o ya pertenecientes a fichas propias; mover desde otra ficha exige admin/consentimiento.
- **Cómo reproducir:** instructor `cruiz` (ficha 1) → `POST /v1/class-groups/1/aprendices {usuario_id: 6}` (Laura Gómez, ficha 2) → 201 y Laura queda en ficha 1 sin aviso.
- **Impacto:** alteración de roster ajeno, pérdida de pertenencia de ficha del aprendiz, conflicto de autoridad entre instructores.
- **Causa probable:** falta de validación del `id_class_group` actual en `asociarAprendiz`.
- **Solución propuesta:** en `asociarAprendiz`/`sincronizarAprendices`, permitir solo `sin ficha` o `misma ficha` para instructores (admin exento), y notificar al instructor anterior.
- **Prueba recomendada:** Feature: instructor B intenta agregar aprendiz de ficha A → 422/403 y la fila no cambia; admin sí puede.

---

## 4. HALLAZGOS ALTOS

### BUG-003 🟠 — Lectura de similitudes y pasajes de propuestas ajenas vía `?proyecto_id=`

- **Área:** Privacidad / API | **Rol:** Aprendiz
- **Archivos:** `backend/app/Http/Controllers/Api/SimilarityController.php:24-43`; `backend/app/Models/Similarity.php:101-112`; `backend/app/Models/Project.php:139-146`
- **Problema:** la vista por propuesta solo exige `Project::paraDetalle` (para aprendiz = toda su ficha) + `contraparteAprobada`, no que el par sea "suyo".
- **Actual:** `GET /v1/similarities?proyecto_id=<propuesta de un compañero>&included=project1.apprentices.generalUser` devuelve el par con `detalles.pasajes` (frases textuales) y relaciones.
- **Esperado:** misma regla que el listado (`paraUsuario`): el aprendiz solo ve pares donde es creador o integrante.
- **Reproducir:** `mgonzalez` → `GET /v1/similarities?proyecto_id=3` (proyecto de Laura, su ficha) → 200 con detalles.
- **Impacto:** lectura de contenido/coincidencias de propuestas ajenas, incluidas contrapartes de otras fichas.
- **Solución:** aplicar la condición "mía" también en la rama `proyecto_id` para aprendiz.
- **Prueba:** Feature: aprendiz no obtiene pares de una propuesta de su ficha que no es suya.

### BUG-004 🟠 — Duplicación de propuestas si falla el envío inicial

- **Área:** UX / Integridad | **Rol:** Aprendiz
- **Archivos:** `frontend/src/pages/aprendiz/Propuestas/Propuestas.jsx:231-288`
- **Problema:** `handleSubmit` hace 3 llamadas no atómicas (crear → equipo → enviar) sin guarda `if (guardando) return`. Si `enviar` falla (422/red), el borrador ya existe; al reintentar se crea otro.
- **Actual:** propuestas duplicadas y equipos duplicados; el usuario no entiende por qué su lista crece.
- **Esperado:** si falla el envío, navegar al detalle del borrador creado (o reusar `nueva.id`) y reintentar sobre él.
- **Reproducir:** crear propuesta con objetivo específico inválido para el backend (p. ej. enviar con datos que el PUT acepta pero `/enviar` rechaza) y reintentar.
- **Impacto:** contaminación de datos y del corpus del motor.
- **Solución:** conservar el id creado, reintentar solo `enviar` y deshabilitar/ignorar reenvíos mientras `guardando`.
- **Prueba:** E2E: forzar fallo de envío y verificar que no se crea una segunda propuesta.

### BUG-005 🟠 — Intervención admin sobre aprobada deja similitudes obsoletas vigentes

- **Área:** Motor / Coherencia | **Rol:** Admin
- **Archivos:** `backend/app/Http/Controllers/Api/ProjectController.php:302-304, 310-358` (archiva solo `if estado === 'rechazado'`, ~:356)
- **Problema:** al editar contenido de una aprobada, pasa a `pendiente` y se audita/historia, pero no se archivan ni re-detectan los pares del contenido anterior.
- **Actual:** pares del contenido viejo siguen `vigente=true`; el creador no recibe aviso de devolución.
- **Esperado:** archivar + re-detectar (mismo tratamiento que la edición aprendiz en pendiente, `:372-376`).
- **Reproducir:** admin edita el título de la propuesta 5 → `similarities` de 5 conserva vigentes calculados sobre el texto anterior.
- **Impacto:** coincidencias obsoletas visibles como actuales.
- **Solución:** replicar `archivar()/detectar()` para la intervención admin.
- **Prueba:** Feature: intervención admin archiva el par previo y crea el nuevo.

### BUG-006 🟠 — Filtros de estado en Similitudes (admin/instructor) siempre vacíos

- **Área:** Frontend/Backend | **Rol:** Admin, Instructor
- **Archivos:** `backend/app/Models/Similarity.php:125-136` (admin exige ambas aprobadas); `frontend/src/pages/admin/SimilitudesAdmin/SimilitudesAdmin.jsx:89-117`; `SimilitudesInstructor.jsx:95,249-252`
- **Problema:** la UI ofrece filtros `pendiente/rechazado` y muestra estados A/B, pero el backend nunca entrega pares con una propuesta no aprobada.
- **Actual:** filtrar por "pendiente" o "rechazado" da 0 resultados siempre; incidencias invisibles.
- **Esperado:** filtros acordes al alcance real o endpoint admin sin la restricción.
- **Impacto:** funcionalidad de supervisión engañosa.
- **Solución:** o quitar los filtros/estados imposibles, o habilitar un listado admin específico.
- **Prueba:** E2E: el filtro "pendiente" no debe existir si no puede devolver resultados.

### BUG-007 🟠 — Borrar ficha/instructor/programa destruye filas de aprendices y sus equipos

- **Área:** BD / Pérdida de datos | **Rol:** Admin
- **Archivos:** `backend/app/Support/BorradoCascada.php:24-45`; migración `2026_04_09_024516_create_apprentices_table.php:14` (`id_class_group` cascade); `2026_04_09_032359` (`apprentice_projects` cascade)
- **Problema:** `BorradoCascada::ficha` borra propuestas y la ficha; la FK cascade elimina las filas `apprentices` y, en cadena, sus `apprentice_projects` de OTRAS propuestas. El aviso dice "Quedaste sin ficha", pero la fila desaparece.
- **Actual:** al borrar una ficha (o instructor/programa/red con cascada) se pierde la pertenencia a equipos de propuestas ajenas; al re-unirse se crea fila nueva.
- **Esperado:** conservar la fila con `id_class_group=null` (como hace `sincronizarAprendices`) o no prometer continuidad.
- **Impacto:** pérdida silenciosa de datos de equipo/historial.
- **Solución:** nullificar antes del delete (o cambiar FK a `nullOnDelete`).
- **Prueba:** Feature: borrar ficha con aprendiz en equipo de otra ficha conserva el pivote.

---

## 5. HALLAZGOS MEDIOS

### BUG-008 🟡 — `BugReportController::store` acepta `estado` del cliente
`BugReportController.php:35,54`. Un usuario crea su ticket ya `resuelto/cerrado` (el aviso a admins se envía igual, pero no hay transición de cierre). Esperado: `store` siempre `pendiente`. Prueba: POST con `estado=resuelto` → 201 con estado pendiente.

### BUG-009 🟡 — Spoofing de notificaciones
`NotificationController.php:30-51`. Instructor/admin puede crear avisos con `tipo`/`enlace` libres para cualquier usuario; un aprendiz puede autocrear `tipo=sistema`. Esperado: validar patrón de enlace y alcance del destinatario. Prueba: instructor no puede notificar fuera de sus fichas.

### BUG-010 🟡 — Instructor puede añadir/mover aprendices a fichas finalizadas
`ClassGroupController.php:222-263` (update sin check de estado) y `ApprenticeController.php:126-144` (solo valida pertenencia). `agregarAprendiz` sí valida (`:121`). Esperado: bloquear roster en `finalizado`. Prueba: `PUT` ficha finalizada con `aprendices` → 422.

### BUG-011 🟡 — Alta de aprendiz desde ficha no atómica
`ClassGroupController.php:155-184`: `AltaUsuario::crear` (transacción propia) y `asociarAprendiz` fuera; si la asociación falla queda usuario aprendiz sin fila. Esperado: una sola transacción. Prueba: forzar colisión de `codigo` y verificar rollback total.

### BUG-012 🟡 — Sin unicidad parcial para pares vigentes
`2026_10_07_000001` (unique → index). `Recomputador::detectar/recalcular` hacen buscar-y-crear sin lock; dos envíos concurrentes pueden duplicar `vigente=true`. Esperado: índice único parcial o lock. Prueba: concurrencia/`lockForUpdate`.

### BUG-013 🟡 — `CommentController::update` sin `puedeEscribir`
`CommentController.php:70-91` vs `:36`. El autor edita observaciones en ficha finalizada o tras salir; `store` sí bloquea. Esperado: mismo guard. Prueba: editar comentario en ficha finalizada → 403.

### BUG-014 🟡 — Instructor edita aprendices sin ficha
`ApprenticeController.php:126-137`: si `id_class_group` es null se salta el alcance; puede cambiar `codigo` de cualquier fila sin ficha. Esperado: instructor solo sobre fichas propias o sin-ficha propios. Prueba: instructor B edita aprendiz sin ficha → 403.

### BUG-015 🟡 — `/general-users/{id}/perfil` expone correo y rol a cualquier autenticado
`GeneralUserController.php:152-164` + `api-v1.php:66`. Enumeración de PII. Esperado: limitar a relaciones académicas o decisión explícita. (Requiere decisión).

### BUG-016 🟡 — Huérfanos al borrar usuario
`BorradoCascada.php:70-83`: no limpia notificaciones `reporte:*` de admins, ni `password_reset_tokens` (sin FK), ni `personal_access_tokens` (morph sin FK). Esperado: limpiar por correo/tokens. Prueba: borrar usuario con reporte → 0 notificaciones `reporte:` huérfanas.

### BUG-017 🟡 — PDF de credenciales: fallo de descifrado se etiqueta "Contraseña ya establecida"
`CredencialesPdf.php:18-42`. Si `APP_KEY` rotó, el admin entrega un PDF sin temporal creyendo que ya cambió. Esperado: mensaje de error explícito. Prueba: simular `Crypt` inválido.

### BUG-018 🟡 — Configuración de sesión/tokens para despliegue
`.env.example:2-4,14,63-64`; `config/sanctum.php` (`expiration=null`); `config/session.php` (`same_site` fijo `lax`, `secure` sin default). `forgotPassword` devuelve `reset_url` con token en `local` (`AuthController.php:168-174`). Riesgos al desplegar (cookie no viaja cross-site, token sin caducidad, reset filtrable si `APP_ENV=local`). Esperado: endurecer para producción. (Fuera de alcance de demo local, pero documentar).

### BUG-019 🟡 — Fechas ISO crudas en varias vistas
`helpers.js:15-20` (`formatearFecha` espera `dd/mm/yyyy`); usado con timestamps en `DetalleSimilitud.jsx:56`, `DashboardAprendiz.jsx:91`, `DetalleFicha.jsx:139`, `DetalleCompanero.jsx:139`, `DetalleProyectoInstructor.jsx:103`, `DetalleFichaInstructor.jsx:305`, `Bitacora.jsx:116`, `ResultadoAnalisis.jsx:241`. Esperado: `fechaDesdeApi` (`helpers.js:32`). Prueba: E2E de formato de fecha.

### BUG-020 🟡 — Notificaciones `ficha:` no navegan
`AlertasBase.jsx:25-31,60-73`; backend genera `ficha:<id>` (`ApprenticeController.php` aviso de movimiento y `fichaMovimientoInstructor`). Al hacer clic solo se marca leída. Esperado: navegar a la ficha. Prueba: clic en alerta de movimiento → URL de ficha.

### BUG-021 🟡 — Errores silenciados en acciones destructivas
Catches vacíos: `SimilitudesAdmin.jsx:63-65`, `ReportesFallas.jsx:63-65`, `AlertasBase.jsx:81-83`, `DetalleProyectoInstructor.jsx:160-163`. El usuario cree que la acción funcionó. Esperado: feedback de error.

### BUG-022 🟡 — Doble submit sin guarda en varios formularios
`Login.jsx:28-50`, `CambioObligatorio.jsx:24-48`, `RestablecerContrasena.jsx:23-45`, `PerfilBase.jsx:242-262`, `DetalleSimilitudAdmin.jsx:76-95`, `DetalleReporte.jsx:91-113`, `Usuarios.jsx:175-183,271-280`. Enter/doble clic dispara 2 requests. Esperado: `if (enviando) return`.

### BUG-023 🟡 — Borrado de red no atómico en frontend
`RedesConocimiento.jsx:342-356`: elimina programas asociados y luego la red en llamadas separadas; un fallo intermedio deja catálogo inconsistente. Esperado: cascada en backend o abortar. Prueba: forzar 409 en la red tras borrar programas.

### BUG-024 🟡 — `VolumenSeeder` incoherente con reglas actuales
`VolumenSeeder.php:25-32,54-61` (sin `username`/documentos/temporales), `:71-83` (estados directos, sin historial/huella/motor), `:91-99` (sin notificar), `:44` (`numero` fijo 9000-9004 → colisiona al segundo run). Herramienta opt-in; tarea futura.

### BUG-025 🟡 — Cobertura de tests con vacíos en reglas críticas
Sin test backend de IDOR en `notifications/{id}` y `bug-reports/{id}`; "aprendiz no edita aprobada" solo E2E; login suspendido solo E2E; doble submit sin pruebas. Archivos: `NotificationController.php:54-86`, `BugReportController.php:65-73,115-125`, `ProjectController.php:246-250`.

### BUG-026 🟡 — Motor: ventana temporal asimétrica y pasajes unidireccionales
`Recomputador.php:132-161` (corpus filtra por ventana, `parVigente` acepta si cualquiera está en ventana); `SimilitudService.php:420-423` (pasajes solo A→B). Pares/notificaciones no reproducibles y explicación incompleta.

### BUG-027 🟡 — API admin de similitudes sin validación de rango/self-pair
`SimilarityController.php:56-68,85-97`: `porcentaje` sin `min:0|max:100`; permite `id_proyecto_1 === id_proyecto_2`.

### BUG-028 🟡 — Admin puede crear/aprobar sin detección
`ProjectController.php:84` (store admin con estado aprobado) y transición a aprobado: el motor no corre; el proyecto queda sin pares hasta un recálculo.

---

## 6. HALLAZGOS BAJOS

- **BUG-029 🔵** `routes/api.php` inerte (solo `/user`, no se carga); `RouteServiceProvider` carga `api-v1.php`. `api.php:1-8`.
- **BUG-030 🔵** Defaults obsoletos: `projects.estado` default `pendiente` (`2026_04_09_030220:22`) y `motor_configs.umbral` default `0.2` (`2026_04_10_000002:15`); la app siempre pasa valores actuales.
- **BUG-031 🔵** Migración `2026_10_05_000001` depende de `App\Support\Credenciales` y del modelo; riesgo de mantenimiento.
- **BUG-032 🔵** Rollback frágil: `2026_10_07_000001::down()` falla con pares duplicados sembrados; `2026_10_04_000002::down()` con fichas `anulada`.
- **BUG-033 🔵** Código muerto: re-exports `Lightbox/index.jsx`, `ObservacionHilo/index.jsx`, `QuickActions/index.jsx`, `PageFallback/index.jsx`; APIs sin uso `recursos.js:168-172,73,79,203`; CSS huérfano `ListaBase.module.css:36-73,135-162,181-240`.
- **BUG-034 🔵** Constantes de rutas por rol duplicadas en 6 archivos (`constants/routes.js`, `DetalleSimilitudBase.jsx:18-22`, `SafeRoute.jsx:9-13`, `TopNav.jsx:10-20`, `Footer.jsx`, `DashboardLayout.jsx`).
- **BUG-035 🔵** `include` ignorado en silencio: si se pide una ruta anidada no listada (p. ej. `classGroup.program` en aprendices), `Included` la descarta sin aviso (`Included.php:23`).
- **BUG-036 🔵** `HistorialProyecto`/`Auditoria` tragan `Throwable` sin log (`HistorialProyecto.php:25-27`, `Auditoria.php:28-30`).
- **BUG-037 🔵** IP de auditoría no confiable tras proxy (`TrustProxies` sin proxies) y `127.0.0.1` en CLI.
- **BUG-038 🔵** `instructor.spec.js:32` describe "detección al aprobar" cuando el motor detecta al enviar; `ConfigSimilitud.jsx:166` contiene "vǭlida(s)" (caracteres corruptos).
- **BUG-039 🔵** Drift de BD tras E2E (no es bug de código): `apprentices.id_class_group` queda mutado por los tests; no hay reset final (`run-aisolado.mjs`).

---

## 7. PROBLEMAS DE UX/UI (consolidado)

- Fechas crudas (BUG-019), errores silenciados (BUG-021), doble submit (BUG-022), notificaciones sin navegación (BUG-020), filtros muertos (BUG-006), error de PUT en `DetalleProyecto.jsx:247-249` siempre bajo "Título", `ApiState.jsx:32` muestra "No query results for model […]" / "Failed to fetch", `ProtectedRoute` redirige a login sin 403 (parpadeo), mensajes de borrado de ficha prometen cascada cuando se anula (`DetalleFichaAdmin.jsx:584`, `DetalleFichaInstructor.jsx:322`), `Alert` siempre `role="status"` (accesibilidad), responsive con breakpoints inconsistentes (`DetalleFichaBase.module.css:296-299` 5 columnas en tablet; `Home.module.css:47-60` 3 columnas hasta 960px).

## 8. PROBLEMAS DE LÓGICA DE NEGOCIO

- Robo de aprendices (BUG-002), roster en fichas finalizadas (BUG-010), edición admin sin re-detección (BUG-005), estados imposibles de filtrar (BUG-006), ticket auto-cerrado (BUG-008), `Comment.update` sin regla Classroom (BUG-013), admin aprueba sin motor (BUG-028), ventana asimétrica del motor (BUG-026).

## 9. PROBLEMAS DE SEGURIDAD

- PII por `included` (BUG-001), transferencia cross-ficha (BUG-002), pasajes ajenos (BUG-003), spoofing de notificaciones (BUG-009), perfil enumerable (BUG-015), sesión/tokens en despliegue (BUG-018), sin throttle específico de login (solo `throttle:api` 60/min), `reset_url` con token en `local`.

## 10. PROBLEMAS DE API

- `store` de similitudes sin rango/self-pair (BUG-027), `store` de bug-reports con estado (BUG-008), `update` de comentarios inconsistente (BUG-013), `show` de similitudes por `proyecto_id` con alcance laxo (BUG-003), notificaciones spoofables (BUG-009).

## 11. PROBLEMAS DE BASE DE DATOS

- Cascada destructiva de `apprentices` al borrar ficha (BUG-007), huérfanos potenciales de tokens/notificaciones (BUG-016), sin unicidad parcial de vigentes (BUG-012), 3 pivotes cross-ficha intrínsecos del seeder (ver matriz), defaults obsoletos y rollback frágil (BUG-030/032). Integridad general: 0 huérfanos reales hoy.

## 12. PROBLEMAS DE INTEGRACIÓN FRONTEND/BACKEND

- Filtros de similitudes vs scope (BUG-006), `ficha:` sin decodificar (BUG-020), `include` anidado ignorado (BUG-035), `ApiState` con mensajes crudos, `apiDescargar` no dispara evento 401 (`api.js:180-220`), `useApi` invalida caché global al recargar (`useApi.js:49-59`), sin AbortController (`useApi.js:32-46`).

## 13. PROBLEMAS DE TESTS/COBERTURA

- IDOR notificaciones/bug-reports sin test (BUG-025), aprobada-no-editable y login suspendido solo E2E, doble submit sin cobertura, trazabilidad de traslado de ficha solo E2E, moderación admin de comentarios solo E2E, conteos en README desactualizados (225/33 vs 218 backend/46 E2E).

## 14. PROBLEMAS DE DOCUMENTACIÓN/CONFIGURACIÓN

- `backend/README.md` genérico de Laravel; `.env.example` con `APP_NAME=Laravel`, `DB_DATABASE=laravel`; `README-ALCANCE.md` y `frontend/docs/ALINEACION-FRONTEND-BACKEND.md` describen mock/Bearer/`/api/v1` obsoletos (tienen nota histórica); `HISTORIAL-FIXES.md`/`CHANGELOG.md` referencian archivos eliminados; no hay README raíz; `docs/arquitectura.md` dice Bearer; conteos de tests hardcodeados.

---

## 15. MATRIZ DE HALLAZGOS

| ID | Severidad | Área | Problema | Rol | Bloqueante |
|---|---|---|---|---|---|
| BUG-001 | 🔴 | Seguridad/API | PII (documento/username) vía `included` | Todos | Sí |
| BUG-002 | 🔴 | Seguridad | Traslado de aprendices entre fichas | Instructor | Sí |
| BUG-003 | 🟠 | Privacidad | Pasajes de propuestas ajenas por `proyecto_id` | Aprendiz | No |
| BUG-004 | 🟠 | Integridad | Propuestas duplicadas si falla el envío | Aprendiz | No |
| BUG-005 | 🟠 | Motor | Intervención admin sin archivar/re-detectar | Admin | No |
| BUG-006 | 🟠 | Integración | Filtros de estado muertos en similitudes | Admin/Instructor | No |
| BUG-007 | 🟠 | BD | Borrar ficha destruye filas/equipos de aprendices | Admin | No |
| BUG-008 | 🟡 | API | Bug-report nace con estado del cliente | Todos | No |
| BUG-009 | 🟡 | Seguridad | Spoofing de notificaciones | Instructor | No |
| BUG-010 | 🟡 | Lógica | Roster en fichas finalizadas | Instructor | No |
| BUG-011 | 🟡 | Atomicidad | Alta de aprendiz no atómica | Admin/Instructor | No |
| BUG-012 | 🟡 | BD | Duplicados vigentes por concurrencia | Sistema | No |
| BUG-013 | 🟡 | Lógica | `Comment.update` sin `puedeEscribir` | Aprendiz | No |
| BUG-014 | 🟡 | Autorización | Instructor edita aprendiz sin ficha | Instructor | No |
| BUG-015 | 🟡 | Privacidad | `perfil` enumerable con correo/rol | Todos | No |
| BUG-016 | 🟡 | BD | Huérfanos de tokens/notificaciones al borrar usuario | Admin | No |
| BUG-017 | 🟡 | UX/PDF | Credenciales "ya establecida" si falla descifrado | Admin | No |
| BUG-018 | 🟡 | Config | Sesión/tokens sin hardening de despliegue | Todos | No |
| BUG-019 | 🟡 | UX | Fechas ISO crudas en 8 vistas | Todos | No |
| BUG-020 | 🟡 | UX | Notificaciones `ficha:` no navegan | Aprendiz/Instructor | No |
| BUG-021 | 🟡 | UX | Errores silenciados en acciones destructivas | Staff | No |
| BUG-022 | 🟡 | UX | Doble submit en formularios | Todos | No |
| BUG-023 | 🟡 | Integridad | Borrado de red no atómico | Admin | No |
| BUG-024 | 🟡 | Seeder | `VolumenSeeder` incoherente | Dev | No |
| BUG-025 | 🟡 | Tests | Vacíos de cobertura en IDOR/estados | QA | No |
| BUG-026 | 🟡 | Motor | Ventana asimétrica y pasajes unidireccionales | Sistema | No |
| BUG-027 | 🟡 | API | Similitud manual sin rango/self-pair | Admin | No |
| BUG-028 | 🟡 | Lógica | Admin aprueba sin detección | Admin | No |
| BUG-029..039 | 🔵 | Varios | Rutas inertes, defaults, código/CSS muerto, docs, drift E2E | Varios | No |

---

## 16. MATRIZ DE COBERTURA

| Área | Revisado | Problemas | Estado |
|---|---|---|---|
| Backend | Sí | 20 | 🟠 |
| Frontend | Sí (estático + lint) | 18 | 🟡 |
| API | Sí | 8 | 🟠 |
| Seguridad | Sí | 7 | 🔴 |
| BD | Sí (SQL solo lectura) | 5 | 🟡 |
| Rol Aprendiz | Sí | 8 | 🟠 |
| Rol Instructor | Sí | 6 | 🟠 |
| Rol Admin | Sí | 8 | 🟡 |
| UX/UI | Sí (estático) | 10 | 🟡 |
| Responsive | Parcial (CSS) | 3 | 🟡 |
| Similitudes | Sí | 5 | 🟠 |
| Equipos | Sí | 4 | 🟠 |
| Notificaciones | Sí | 4 | 🟡 |
| Historial | Sí | 2 | 🟢 |
| Tests | Sí (inventario + vacíos) | 5 | 🟡 |
| Seeder | Sí | 3 | 🟡 |
| Documentación | Sí | 6 | 🟡 |

---

## 17. ASPECTOS CORRECTAMENTE IMPLEMENTADOS

- Mapa de rutas con middleware `auth:sanctum` + `cuenta.activa` + `rol:...` + `CambioContrasenaObligatorio`; `RolMiddleware` falla cerrado; sin bypass detectado.
- `Similarity` store/update/destroy y `recalculate` son admin-only; `audit-logs` solo lectura admin; `config-similitud` lectura pública intencional y update admin validado.
- Flujos de contraseña: hash, temporal encriptada, `must_change_password`, limpieza al cambiar/restablecer, revocación de sesiones en reset y cambio de correo, respuesta genérica en forgot.
- Motor robusto: evita el par consigo mismo, clampea porcentajes [0,100], sin división por cero, corpus vacío seguro, LSA acotado, pares obsoletos archivados (no borrados) y normalización `id_proyecto_1 < id_proyecto_2`.
- Scoping correcto en `projects`, `comments`, `notifications`, `bug-reports` (lectura), `apprentices` (index/show), `class-groups` (index/show), `instructors`; `Included` con allowlist estricta de rutas.
- `BorradoCascada` respeta orden de FKs; guardas de último admin, auto-borrado y instructor con fichas.
- Frontend: cliente HTTP centralizado con CSRF/caché/dedupe/timeout, guards + ErrorBoundaries, ConfirmModal reforzado, estados loading/empty/error mayoritarios, sin mock.
- Seeder: 15 usuarios coherentes, 16 proyectos con todos los estados, similitudes vigentes/históricas, historial/auditoría/notificaciones correctos; integridad SQL sin huérfanos.
- Calidad: backend 218/218 (1028 assertions), E2E 46/46, lint/build OK.

---

## 18. FUERA DE ALCANCE

- App móvil (`Movil/`, Kotlin) no auditada.
- Hardening de despliegue (HTTPS, SMTP real, proxies, WAF) más allá de BUG-018.
- Rediseño responsive completo y accesibilidad WCAG formal.
- Optimización de rendimiento a escala (solo se reporta BUG-026 y fetches globales).
- Consolidación de migraciones y refactors arquitectónicos.
- `VolumenSeeder` (herramienta opt-in) — tarea futura.

---

## 19. ESTADO FINAL DE PREPARACIÓN DEL MVP

**🔴 NO LISTO** — existen 2 problemas críticos (fuga de PII y transferencia no autorizada de aprendices) que deben corregirse antes de cerrar el MVP. Los flujos centrales funcionan y la base es sólida, pero ambos son explotables por usuarios autenticados con roles normales y afectan seguridad/privacidad. Tras corregirlos, con los altos pendientes se pasaría a 🟠 CASI LISTO; los medios/bajos no bloquean la demo.

---

## 20. ORDEN RECOMENDADO DE CORRECCIÓN

1. **BUG-001** (PII `included`) — ocultar campos sensibles / Resource; ajustar includes del frontend.
2. **BUG-002** (robo cross-ficha) — validar ficha de origen en `asociarAprendiz/sincronizarAprendices` + notificación.
3. **BUG-003** (pasajes ajenos) — regla "propia" en la vista por `proyecto_id`.
4. **BUG-005** (admin sin re-detección) + **BUG-007** (cascada de aprendices).
5. **BUG-004** (duplicados al fallar envío) y **BUG-006** (filtros muertos).
6. Medios de API/lógica: BUG-008..BUG-014.
7. UX y coherencia: BUG-015..BUG-023.
8. Tests de regresión para cada corrección (IDOR notificaciones/reportes, aprobada-no-editable API, doble submit).
9. Seeder/docs/config: BUG-024, BUG-027 (docs), `.env.example`.
10. Bajos y limpieza: BUG-029..039.

---

*Fin del informe. No se implementaron correcciones.*

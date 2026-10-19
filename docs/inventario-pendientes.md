# Inventario maestro de pendientes — ProyecTWIN MVP

**Actualizado:** 18-oct-2026 (Lotes 3-6) · **Fuentes:** gate E2E aprobado (5/5 etapas, 347 pruebas, exit 0), certificación backend (324/324 tras el Lote 6), matrices del Lote 3 (backend y Android), investigaciones y validaciones de los Lotes 4-6 y auditorías independientes.
**Refs:** `C-xx`/`P-xx`/`D-xx` (backend) y `M-x`/`O-x`/`D1` (Android) son **referencias de trabajo del Lote 3**, no IDs de auditoría. Los `BUG-xxx` provienen de `AUDITORIA-INTEGRAL.md` (snapshot histórico).
**Regla:** ningún ítem se implementa sin autorización; las decisiones del propietario no se asumen.

**Cambios del Lote 4 (18-oct):** C-01, C-04 y C-08 **cubiertos con pruebas** (focales verdes); FE-2 y FE-8 **corregidos**; M4 **implementado** (gradle 139/139 + assembleDebug); M7 **plan presentado**; M8 y FE-7/O5 **parciales**; D-01 **investigado**.
**Cambios del Lote 5 (cierre de entrega, 18-oct):** D-01 con **recomendación lista** (opción a: guarda 409, comparativa completa); Android con **estado de entrega y procedimientos APK + verificación manual M4** documentados (`docs/ejecutar-android.md §10-11`); FE-1 con **plan de ejecución y autorización solicitada** (requiere reset de `proyectwin_e2e` vía procedimiento oficial; NO es el gate integral); clasificación de pendientes: **nada nuevo imprescindible** más allá de M1/D-01/FE-1/M4.
**Cambios del Lote 6 (18-oct):** **D-01 implementado y verificado** (guarda 409 en el borrado de usuarios: instructor + propuestas creadas, mutex O1, admin exento; 6 pruebas nuevas; suite backend **324/324, 1883 aserciones**); **plan de versionado Android revisable** creado (`docs/plan-versionado-android.md`: 24 archivos a versionar + exclusión de 4 `.idea` máquina-específico preservando 9 compartibles; diff exacto — aplicado después, ver M7); FE-1 quedaba pendiente de autorización de reset en ese momento (ejecutado después — ver fila FE-1).
**Corrección documental (Lote 11, 19-oct):** FE-1 **ejecutado y aprobado** (5 tests autorizados + 4 adicionales); M7 **aplicado** (Lote 8); M1 **preparado en el índice** (103 entradas, pendiente de commit).

## 1. Bloqueantes para un MVP funcional y seguro

- **Funcionales/seguridad: 0 abiertos** (salvo hardening de despliegue, C-07/P-05 en la sección 2). El MVP web está certificado (backend 314/314; gate E2E 347/347) y la guarda de evidencia D-2b × BUG-007 cubre instructor/programa/red.
- **M1 · Entrega móvil sin versionar (bloqueante de entrega, no funcional):** el sprint móvil (15 `M` + 8 `??` en `Movil/`, incluidos `UserManagementViewModel.kt`, `PaginationDtos.kt`, `PaginaUsuarios.kt` y 5 tests) no está en el commit; un clon de HEAD no reproduce la app actual y un commit parcial no compila (`UserManagementScreen.kt:22-23`, `UsersRepositoryImpl.kt:13`, `UsersApi.kt:6`). 📋 **Preparado en el índice (Lote 9), pendiente de commit**: las 103 entradas staged incluyen los 24 archivos Android (15 `M` + 8 `??` + `docs/ejecutar-android.md`; ver `docs/plan-versionado-android.md`). El commit requiere autorización expresa.

## 2. Importantes, no bloqueantes

| Ref | Estado + evidencia | Impacto | Dependencias / archivos | Pruebas necesarias |
|---|---|---|---|---|
| C-01 | ✅ **Cubierto (Lote 4)** — `RecalcularMotorTest` 3/3 (34 aserciones): 403, contrato `{eliminadas,creadas}`, archivado+recreación, auditoría | — | `SimilarityController.php:169-174`, `Recomputador.php:124-131` | — |
| C-04 | ✅ **Cubierto (Lote 4)** — `MotorConcurrenciaTest` 1/1 (8 aserciones; 4 ejecuciones verdes): 2 `detectar` simultáneos → 1 fila vigente | — | `Recomputador`, infra `mysql_aux`/proceso hijo | — |
| C-07 | BUG-037 parcial: `TrustProxies` confía `*` (`TrustProxies.php:19`); IP en `Auditoria:26`; sin test de IP/proxy | Rastro de IP no garantizado; `X-Forwarded-For` spoofeable si se expone directo | `TrustProxies.php`, `Auditoria.php`; despliegue | Feature con `X-Forwarded-For` simulado |
| P-05 | BUG-018 + `same_site`: `session.php:199` fijo `lax`, `secure`/`expiration` por env sin default (`sanctum.php:49`); guía `deploy-railway.md:56,62` | Desplegar sin flags deja cookies/tokens débiles; SPA cross-site no viaja | `config/session.php`, `config/sanctum.php`, `.env.example` | Checklist de despliegue o test de flags |
| M2 | PDF por lote sin UI móvil: soporte existe (`UsersRepositoryImpl.kt:123-124`) y solo se usa 1 id (`NewUserScreen.kt:75`) | Paridad admin incompleta | `UserManagementScreen.kt:244-260` | Test de selección + manual en emulador |
| M3 | Detalle de ficha admin sin ruta móvil (`AppNavigation.kt:534-538` solo lista) | Faltan detalle + propuestas por ficha | `AdminFichasScreen.kt:143-171` | Contraste funcional por rol |
| M4 | 🟨 **Implementado (Lote 4) + procedimiento manual documentado (Lote 5, `docs/ejecutar-android.md §11`)**: confirmación tipada "ELIMINAR" (con `trim()`, los espacios extremos se ignoran) en ambos diálogos; gradle 139/139 + assembleDebug OK. **Pendiente ejecución manual en emulador** (4 pasos con casillas) | — | `AdminFichasScreen.kt`, `UserManagementScreen.kt` | **Verificación manual M4** |
| M5 | Contrato por texto exacto "Cuenta suspendida" (`ApiResult.kt:104` ↔ `CuentaActiva.php:16`) | Si cambia el copy, la sesión suspendida no se cierra | Backend (contrato); `ApiResult.kt` | Prueba de contrato o flag `code` |
| M6 | `androidTest` vacío (solo plantilla `ExampleInstrumentedTest.kt`) | Sin verificación de UI en dispositivo | deps ya configuradas | Smoke tests en emulador |
| M7 | ✅ **Aplicado (Lote 8)** — los 4 `.idea` máquina-específico retirados del índice (`git rm --cached`; 4 `D` staged) y reglas en `Movil/.gitignore` (líneas 10-13); copias locales conservadas; `misc.xml` (jbr-17) sigue versionado | — | `.gitignore`, `.idea/*` | — |
| M8 | 🟨 **Doc alineada (Lote 4)** — `jbr-17` (coincide con `misc.xml:4` y daemon) + wrapper 9.6.0 documentado; **versionado pendiente** (D-07/M1) | Clon sin guía hasta versionar | `docs/ejecutar-android.md` | Commit (D-07) |
| FE-1 | ✅ **Ejecutado y aprobado (18-oct, autorización focal)**: `fechas-formato` 2/2 (test 1 con día exacto) y `notificacion-ficha-navegacion` 3/3 (control negativo) — **5 tests autorizados aprobados** — más `notificaciones-tema` 4/4 (el filtro "notificacion" capturó el archivo extra); runner aislado oficial con reseed protegido por archivo; `proyectwin_e2e` en conteos de seed; sin reset final (restricción) | — | `frontend/e2e/*` | — |
| FE-2 | ✅ **Corregido (Lote 4)** — copys de borrado alineados al 409 real en `DetalleProyectoAdmin` y `DetalleProyecto` (solo el `mensaje`); lint+unit verdes; ningún spec depende de los textos | — | 2 archivos JSX | — |

## 3. Mejoras opcionales

| Ref | Estado + evidencia | Impacto | Archivos | Pruebas |
|---|---|---|---|---|
| C-02 | `POST /public/demo-similitud` sin test backend (E2E feliz `home-demo.spec.js:22-26`) | Endpoint público sin red | `SimilarityController:181-219` | Feature anónimo 422/200 sin PII |
| C-03 | `similitud:calibrar` y `similitud:recalcular` sin test directo | Herramientas dev sin red | `SimilitudCalibrar.php`, `SimilitudRecalcular.php` | Unit de comandos |
| C-05 | BUG-032: migraciones consolidadas, `down()` = dropIfExists (18/18); sin test de rollback | Bajo con esquema consolidado | `database/migrations/*` | Test `migrate:rollback` en CI |
| C-06 | `e2e:reset`: guardas E2E no destructivas (`guardas-reset.spec.js:34-56`); sin test backend de guardas de BD/host/lock | Guardas críticas sin red en backend | `E2eReset.php:40-51,82-96` | Feature de abortos sin tocar BD |
| C-08 | ✅ **Cubierto (Lote 4)** — caso admin 200 añadido a `IdorRecursosTest` (espejo de notificaciones) | — | `IdorRecursosTest.php` | — |
| C-09 | Sin test backend: `GET /public/resumen`, `POST /auth/logout`, `PUT/DELETE /similarities/{id}` | Endpoints menores sin red | `MotorConfigController`, `AuthController`, `SimilarityController` | Features básicos |
| P-01 | BUG-030: defaults muertos (`projects.estado` `pendiente`, `umbral` `0.2`) vs app (`borrador`, `0.30`) | Camino nuevo que omita valores insertaría datos incorrectos | migraciones citadas | Alinear o test explícito |
| P-02 | BUG-036: `Auditoria:28-30` e `HistorialProyecto:25-27` tragan `Throwable` sin log | Pérdida silenciosa de trazabilidad | 2 archivos | Test de log si se decide |
| P-03 | Mensaje 422 de `enviar` impreciso cuando la propuesta ya no existe (`ProjectController:176-179`; fijado en `AlineacionBloqueosTest:323-346`) | Diagnóstico engañoso | `ProjectController` | Ajustar mensaje + test de carrera |
| P-04 | BUG-035: `scopeIncluded` descarta includes no autorizados en silencio (`Project:55-66`, `GeneralUser:98-112`) | Typos no detectables | modelos con el patrón | Log opcional |
| O1 | `MotorApi` demo/resumen sin uso en móvil (código muerto) | Limpieza / posible exclusión | `MotorApi.kt:25-29`, `MotorRepositoryImpl` | Decidir borrado o pantalla |
| O2 | `NotificationsApi.crear` declarado sin uso (la web tampoco permite crear) | Endpoint muerto en cliente | `NotificationsApi.kt:26-27` | Limpieza |
| O3 | Paginación desigual en móvil (solo usuarios servidor; bitácora cliente; resto sin paginar) | Listas largas menos manejables | `UserManagementScreen`, `BitacoraViewModel`, resto | Paridad progresiva |
| O4 | Deprecaciones `hiltViewModel` (41 imports; hilt-navigation 1.3.0) | Ruido de compilación | `libs.versions.toml:17` + imports | Migrar imports |
| O5 | 🟨 **Parcial (Lote 4)** — nota de vigencia en `HISTORIAL-FIXES` (solo +2 líneas); manual-técnico/generadores sin cambios (Bearer correcto ahí); `frontend/CHANGELOG` pendiente de decisión | Onboarding | docs citados | Decisión sobre CHANGELOG |
| FE-3 | BUG-039: `run-aisolado.mjs` sin restauración final (solo `test:full` termina con seed restaurado) | Drift si se corre `npm test` aislado | `run-aisolado.mjs` | Reset final opcional |
| FE-4 | Literales de alertas/soporte aún duplicados en deep-links (`Dashboard*`, `DetalleReporte`, `Fichas*`) | Duplicación residual (no funcional) | ~6 archivos | Migrar a mapas de `routes.js` |
| FE-5 | Mapa local `RUTA_POR_ROL` homónimo en `DetalleSimilitudBase:18` (semántica distinta) | Confusión de nombres | 1 archivo | Renombrar |
| FE-6 | Fallback de `DetalleProyecto:251-256` si el 422 solo trae claves no visibles | Borde improbable sin mensaje | 1 archivo | Mejorar fallback |
| FE-7 | 🟨 **Parcial (Lote 4)** — `arquitectura.md` (8 anclas), `guion-presentacion.md` y `arquitectura.html` corregidos; `arquitectura-figma.svg`, `diagrama-*.drawio` y `diagrama.excalidraw` **pendientes de regenerar por su autor** (solo reportados) | Doc menor | docs citados | Regeneración |
| FE-8 | ✅ **Precisado (Lote 4)** — `backend/README` distingue ruta directa (anula) de cascada (borra tras el 409) | — | `backend/README.md` | — |
| O-INFO | Doble-submit same-tick: guardas robustas (React 19: eventos discretos, flush en microtarea + `disabled`); único hueco teórico = despachos sintéticos en el mismo task (no alcanzable por input real) | Informativo, sin acción | — | — |
| O-INFO | Test 2 de fechas depende de TZ no-UTC (válido en esta máquina UTC-05:00); `retries:1` podría enmascarar flakiness futura (hoy 0) | Informativo | — | — |

## 4. Requieren decisión del propietario

| Ref | Decisión | Alternativas / consecuencias | Evidencia |
|---|---|---|---|
| D-01 | Borrado de usuarios con propuestas | ✅ **Implementado y verificado (Lote 6)** — guarda 409 en `BorradoCascada::usuario` (rama instructor + propuestas creadas, mutex O1, **admin exento**); mensaje fijo `'usuario'`; 6 pruebas nuevas (`BorradoUsuarioEvidenciaTest`) + suite **324/324 (1883 aserciones)**. **Residual documentado**: "callejón 409" del aprendiz creador (sin salida inventada); auditoría del rechazo y limpieza de `sessions` huérfanas = no indispensables (decisión pendiente) | `BorradoCascada:136-169,181-210,220`; `BorradoUsuarioEvidenciaTest.php` |
| D-02 | Singleton `MotorConfig` | (a) constraint/seed único + test; (b) documentar convención (hoy: 6 `firstOrCreate` sin unicidad) | `2026_04_10_000002:13-18`; `MotorConfigController:13` |
| D-03 | BUG-026: pasajes unidireccionales A→B (la ventana ya está ratificada por escrito) | (a) ratificar y documentar; (b) simetrizar + test | `SimilitudService:420-423`; `README:80-83` |
| D-04 | `demo()` compara contra aprobadas de todos los programas | (a) alinear con el motor (filtrar por programa); (b) ratificar como demo | `SimilarityController:198-202` vs `Recomputador:139-151` |
| D-05 | `VolumenSeeder` opt-in | (a) ratificar alcance opt-in actual; (b) ampliar coherencia (historial/motor) | `VolumenSeeder.php:85`; `E2eReset:58-60` |
| D1 | Paridad móvil D-6/D-11/D-15 | Sin contenido en el repo (grep = 0): transcribir o autorizar alcance móvil actual | `docs/alcance-y-exclusiones.md` (no las menciona) |
| D-06 | Refs de conversación NF-01..NF-08, R1–R3, S-1 | Sin detalle completo en el repositorio (única referencia: `NF-04` en `DeleteGuardsTest.php:189`; `RNF-*` en el manual técnico, por confirmar si comparten espacio de nombres): aportar contenido o confirmar descarte | Búsqueda en contexto |
| D-07 | Política de versionado (commits) | Hoy: sin commits por política (conteo vivo en `git status`); M1/M7/M8 quedan bloqueados hasta una autorización de versionado | `git status` |
| D-08 | `ProtectedRoute`: 403 vs redirección a login | (a) página 403; (b) mantener redirección | `ProtectedRoute.jsx:15` |
| D-09 | BUG-006: supervisión de pares con propuestas no aprobadas | (a) listado admin alternativo; (b) mantener UI actual (solo "aprobado") | `SimilitudesAdmin:197-199` |
| D-10 | Perfiles de aprendices al borrar programas/redes | (a) conservar la fila con `id_class_group`/`id_programa` en null (consistente con la anulación de fichas); (b) ratificar la eliminación actual. Hoy: se elimina la fila | `docs/alcance-y-exclusiones.md:27-28`; `BorradoCascada::programaInterno:91` |

---
*Los detalles completos (impacto, dependencias y pruebas por ítem) provienen de las matrices del Lote 3 (Agentes 3 y 4), las investigaciones del Lote 4 (D-01) y las auditorías del gate; este documento es el índice consolidado y trazable. Incorpora las correcciones de la revisión independiente (R3 del Lote 3) y los estados verificados del Lote 4.*

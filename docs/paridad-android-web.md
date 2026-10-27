# Matriz de paridad Web ↔ Android — ProyecTwin

**Fecha:** 27-oct-2026
**Repositorio:** `https://github.com/Pedraza12/ProyectWWin_paridad.git`
**Rama:** `master` · **HEAD auditado:** `8811021`
**Referencia funcional:** frontend React (`frontend/src`), backend Laravel (`backend/routes/api-v1.php`).
**Cliente móvil:** Kotlin + Jetpack Compose (`Movil/app/src/main/java`).

## Cómo leer esta matriz

| Estado Android | Significado |
|---|---|
| **Completo** | Pantalla conectada a la API real, con permisos, estados de carga/error/vacío y flujo equivalente al web. |
| **Parcial** | Existe el flujo principal, pero falta una operación, filtro o dato del web. |
| **Ausente** | No existe pantalla/flujo equivalente. |
| **N/A** | Función exclusiva de una plataforma por su naturaleza (p. ej. Home público). |

**Resultado** distingue explícitamente: `Aprobado (unit)` = cubierto por pruebas
automáticas y compilación; `Pendiente dispositivo` = compila pero no se ejecutó en
un dispositivo/emulador; `Bloqueado` = falta entorno o autorización.

## Matriz funcional

| Módulo | Rol | Funcionalidad web | Estado Android | Endpoint | Diferencia / acción | Prueba | Resultado |
|---|---|---|---|---|---|---|---|
| Autenticación | Todos | Login, logout, sesión persistente, token cifrado | Completo | `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` | Sin diferencias funcionales detectadas | `AuthViewModelTest`, `AuthRepositoryImplTest`, `TokenCipherTest` | Aprobado (unit) |
| Recuperación | Público | Olvidé contraseña, código, restablecer | Completo | `POST /auth/forgot-password`, `POST /auth/reset-password` | Flujo nativo equivalente | Compilación + revisión de código | Pendiente dispositivo |
| Cambio obligatorio | Todos | Bloqueo hasta cambiar clave temporal | Completo | `PUT /auth/password` + `must_change_password` | Misma regla que web | `AuthViewModelTest` | Aprobado (unit) |
| Navegación por rol | Todos | Rutas y menús separados | Completo | — | Grafos independientes; el backend sigue validando permisos | `AppNavigationRoutesTest` | Aprobado (unit) |
| Dashboard aprendiz | Aprendiz | Métricas, propuestas recientes, acciones | Completo | `GET /dashboard-metrics`, `GET /projects` | Bloque de métricas reales añadido (dona, evolución, observaciones) | `DashboardMetricsViewModelTest` | Aprobado (unit) |
| Dashboard instructor | Instructor | Métricas, fichas y propuestas | Completo | `GET /dashboard-metrics`, `GET /projects`, `GET /class-groups` | KPIs (fichas, aprendices, propuestas, por revisar), dona, evolución y actividad añadidos | `DashboardMetricsViewModelTest` | Aprobado (unit) |
| Dashboard admin | Admin | KPIs, dona, cuentas, evolución, actividad | Completo | `GET /dashboard-metrics`, `GET /config-similitud` | Gráficas con periodo y etiquetas reales | `DashboardMetricsViewModelTest` | Aprobado (unit) |
| Propuestas | Aprendiz | Listar, filtrar, crear, editar borrador, enviar, eliminar | Parcial | `GET/POST/PUT/DELETE /projects`, `POST /projects/{id}/enviar` | Búsqueda móvil solo por título (web busca también resumen/palabras) | `ProjectsViewModel`, `NewProjectScreen` + compilación | Parcial (búsqueda) |
| Resultado del análisis | Aprendiz | Veredicto agregado, ranking, recomendaciones | Parcial | `GET /similarities`, `GET /projects/{id}` | Android muestra las similitudes dentro del detalle, sin pantalla de veredicto/recomendaciones | Revisión de código | Pendiente |
| Similitudes | Aprendiz | Lista, detalle A/B, umbral, observaciones propias | Completo | `GET /similarities`, `GET /similarities/{id}`, `GET /comments` | Misma autorización de contraparte aprobada | `SimilarityDetailViewModelTest` | Aprobado (unit) |
| Similitudes | Instructor | Pares de sus fichas, detalle A/B, observaciones | Completo | `GET /similarities`, `GET /comments` | Sin diferencias detectadas | `SimilitudesInstructorViewModel` + compilación | Pendiente dispositivo |
| Similitudes | Admin | Todas, filtros, detalle, recalcular, eliminar | Completo | `GET/DELETE /similarities`, `POST /similarities/recalculate` | Sin diferencias detectadas | `AdminSimilarityDetailViewModelTest` | Aprobado (unit) |
| Mi Ficha | Aprendiz | Datos, instructor enlazado, búsqueda de integrantes, salir | Parcial | `GET /class-groups/{id}`, `DELETE /apprentices/me/ficha` | Se añadió enlace al perfil del instructor y búsqueda de compañeros; falta el conteo/listado de propuestas de la ficha | Compilación + revisión | Pendiente dispositivo |
| Detalle de ficha | Aprendiz | Abrir cualquier ficha autorizada por id | Parcial | `GET /class-groups/{id}` | Android solo abre la ficha propia; no hay ruta genérica por id | Revisión de código | Pendiente |
| Mi Instructor | Aprendiz | Perfil del instructor y ficha compartida | Completo | `GET /general-users/{id}/perfil`, `GET /class-groups/{id}` | Se añadió ruta y vista reutilizando el perfil público | `DetalleCompaneroViewModelTest` | Aprobado (unit) |
| Perfil de compañero | Aprendiz / Instructor | Datos, ficha, propuestas visibles y similitud | Completo | `GET /general-users/{id}/perfil`, `GET /projects`, `GET /similarities` | Se corrigió la ficha mostrada (era la del solicitante) y se habilitó abrir propuestas | `DetalleCompaneroViewModelTest` | Aprobado (unit) |
| Fichas | Instructor | Listar, crear, editar, roster, eliminar/anular | Completo | `GET/POST/PUT/DELETE /class-groups` | Sin diferencias detectadas | `FichasViewModelTest` | Aprobado (unit) |
| Directorio de ficha | Instructor | Buscar/ordenar integrantes, paginar, abrir perfil | Parcial | `GET /class-groups/{id}` | Android muestra rosters y abre perfiles; falta búsqueda/orden por ficha | Revisión de código | Pendiente |
| Revisión de propuestas | Instructor | Aprobar/rechazar con observación y similitud | Completo | `PUT /projects/{id}`, `GET /similarities` | Misma regla de estado | `RevisionViewModel` + compilación | Pendiente dispositivo |
| Usuarios | Admin | Listar/paginar/filtrar, crear, editar, suspender, eliminar, credenciales PDF | Completo | `GET/POST/PUT/DELETE /general-users`, credenciales | Sin diferencias detectadas | `UserManagementViewModelTest` | Aprobado (unit) |
| Detalle de usuario | Admin | Datos, documento, rol, foto, credenciales | Completo | `GET /general-users/{id}`, `PUT /general-users/{id}` | Sin diferencias detectadas | Compilación + revisión | Pendiente dispositivo |
| Propuestas (admin) | Admin | Todas, filtros, detalle, edición, borrado | Completo | `GET /projects`, `PUT/DELETE /projects/{id}` | Sin diferencias detectadas | `AdminViewModelTest` | Aprobado (unit) |
| Fichas (admin) | Admin | Listar, crear, detalle con roster/propuestas, credenciales | Completo | `GET/POST /class-groups`, `GET /projects?ficha_id` | Sin diferencias detectadas | `AdminFichaDetailViewModelTest` | Aprobado (unit) |
| Catálogos | Admin | CRUD redes de conocimiento y programas | Completo | `GET/POST/PUT/DELETE /knowledge-networks`, `/training-programs` | Sin diferencias detectadas | `CatalogosViewModel` + compilación | Pendiente dispositivo |
| Motor de similitud | Admin | Umbral, meses, recalcular | Completo | `GET/PUT /config-similitud`, `POST /similarities/recalculate` | Sin diferencias detectadas | `AdminViewModelTest` | Aprobado (unit) |
| Bitácora | Admin | Auditoría paginada y filtros | Completo | `GET /audit-logs` | Paginación cliente en móvil (el backend ya pagina) | `BitacoraViewModelTest` | Aprobado (unit) |
| Reportes de fallas | Todos / Admin | Crear, listar, detalle, cerrar | Completo | `GET/POST/PUT /bug-reports` | Sin diferencias detectadas | `BugReportsViewModel` + compilación | Pendiente dispositivo |
| Notificaciones | Todos | Listar, filtrar, marcar leída/todas, abrir destino | Completo | `GET/PUT /notifications` | Sin diferencias detectadas | `NotificationsViewModel` + compilación | Pendiente dispositivo |
| Perfil y foto | Todos | Ver/editar nombre, foto, correo, clave | Completo | `PUT /general-users/{id}`, `PUT /auth/email`, `PUT /auth/password` | Foto recortada a data URL, mismo contrato que web | `ProfileViewModelTest`, `FotoPerfilValidacionTest` (backend) | Aprobado (unit) |
| Descarga APK (Home) | Público | Botón visible sin sesión; aviso si el archivo falta | Completo | `GET /apk/ProyecTWin.apk` (estático) | Se añadió `ApkDownloadButton` al Home; si el `HEAD` devuelve 404 se muestra aviso y se bloquea el enlace | `ApkDownloadButton.test.jsx` + `Home.test.jsx` | Aprobado (unit) |
| E2E Home + APK | Público | Descarga real sin login | Completo | `HEAD`/`GET /apk/ProyecTWin.apk` | Validado en navegador Chromium con backend E2E | `home-apk.spec.js` (1/1), `home-demo.spec.js` (2/2) | Aprobado (E2E) |
| Actualizaciones Android | Todos | Aviso, centro de actualizaciones, descarga y validación de firma | Completo | `GET /app-version/android` | Conserva descarga HTTPS, validación de paquete/versión/firma | `AppUpdateViewModelTest`, `ActualizacionEvaluatorTest` | Aprobado (unit) |
| Quick Store | — | Distribución alternativa | No verificable | — | No se encontró integración en el repositorio; no se amplió el alcance | Búsqueda en repo | Bloqueado |
| Pruebas instrumentadas | — | — | Presente | — | Smoke tests compilados (`assembleDebugAndroidTest` OK); no ejecutados por falta de dispositivo/emulador | Compilación androidTest | Bloqueado |
| Pruebas E2E Android | — | — | Pendiente | — | Sin emulador/dispositivo disponible en este entorno | — | Bloqueado |

## Cambios de esta iteración

1. **Home web:** botón público `Descargar APK para Android` enlazado a
   `/apk/ProyecTwin.apk` (archivo existente, `versionCode 4` / `1.3.0`, firmado,
   no debug). No se modificó ningún dashboard.
2. **Android — Mi Instructor:** nueva ruta `aprendiz/instructor/{userId}` y
   enlace desde la tarjeta del instructor en Mi Ficha.
3. **Android — Perfil de compañero/instructor:** la ficha ahora se resuelve para
   el usuario consultado (antes se mostraba la del solicitante); se añadió el
   porcentaje máximo de similitud y la apertura de sus propuestas.
4. **Android — Directorio de ficha:** los integrantes abren su perfil público.
5. **Android — Mi Ficha:** búsqueda de compañeros por nombre/correo.
6. **Lint:** se corrigió `BoxWithConstraints` sin uso en `SimilarityCharts.kt`.
7. **Paneles Android:** bloque de métricas reales compartido para aprendiz e
   instructor (KPIs, dona, evolución por periodo y actividad), equivalente al
   `DashboardMetrics` web.
8. **Pruebas E2E:** se añadió `home-apk.spec.js` y se ejecutó contra backend E2E
   aislado; el Home y la descarga real del APK quedaron aprobados.

## Pendientes reales (no bloqueantes de esta entrega)

- Pantalla agregada de “Resultado del análisis” con veredicto y recomendaciones.
- Ruta móvil de detalle de una ficha ajena autorizada (por `fichaId`).
- Búsqueda/orden por ficha en el directorio del instructor.
- Búsqueda de propuestas por resumen/palabras clave en Android.
- Pruebas instrumentadas/UI y corrida en dispositivo de los tres roles.
- Integración Quick Store: no existe en este repositorio.

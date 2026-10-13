# Historial de Fixes - ProyecTwin

**Fecha:** Agosto 2026
**Ruta:** D:\ADSO YDMV\Proyecto\ProyecTwin-master\MVP

## Resumen

| Plataforma | Fixes | Estado |
|------------|-------|--------|
| Web (React + Vite) | 74 | npm run build OK |
| Movil (Kotlin/Compose) | 56 | gradlew assembleDebug BUILD SUCCESSFUL |
| **Total** | **130** | **Completado** |

---

## Web - 74 Fixes

- Menores (1-17), Moderados (18-40), Mayores (41-66), Criticos (67-74)
- Archivos: frontend/src/data/mockData.js, AuthContext.jsx, components/, pages/

---

## Movil - Fixes 1-34 (Paridad Inicial)

| # | Fix | Archivo(s) |
|---|-----|------------|
| 1 | Instructor dinamico desde ficha (no hardcoded) | ProyectoViewModel.kt |
| 2 | guardarBorrador() edita existente sin duplicar | ProyectoViewModel.kt |
| 3 | Borrador -> PENDIENTE al enviar propuesta | ProyectoViewModel.kt |
| 4 | accionSeleccionada!! -> null-safe | ProyectoViewModel.kt |
| 5 | cambiarPassword() llama updateUserPassword() | PerfilViewModel.kt |
| 6 | APRENDIZ/INSTRUCTOR guardan telefono | PerfilViewModel.kt |
| 7 | setUserFicha() sincroniza programa desde ficha | MockDataProvider.kt |
| 8 | createFicha() deriva idPrograma del nombre | MockDataProvider.kt |
| 9 | updateProject() title/description nullable con fallback | MockDataProvider.kt |
| 10 | Ficha campo instructorId: Int? + seed data | Ficha.kt |
| 11 | cargarDetalleCompanero() carga ficha correcta | FichaViewModel.kt |
| 12 | Breadcrumbs "Inicio" root en 8 pantallas | Multiples screens |
| 13 | BugReport icon -> FolderOpen en pestaña Proyectos | RoleTabs.kt |
| 14 | "Maria Gonzalez" -> "Maria Gonzalez" (acentos) | MockDataProvider.kt |
| 15 | Proyectos pendientes filtrados por rol | DashboardViewModel.kt |
| 16 | Radio buttons para tipo proyecto | NuevoProyectoScreen.kt |
| 17 | Password policy unificada 8+ chars | AuthViewModel, PerfilViewModel, AdminViewModel |
| 18 | Credenciales de test bajo BuildConfig.DEBUG | LoginScreen.kt |
| 19 | Secciones de similitud derivadas del porcentaje | DetalleSimilitudScreen.kt |
| 20 | Contactar Aprendiz disabled + proximamente | DetalleSimilitudInstructorScreen.kt |
| 21 | cambiarPasswordAdmin regex 8-char | PerfilViewModel.kt |
| 22 | Admin fechaIngreso default -> fecha actual | AdminViewModel.kt |
| 23 | Pagination overflow -> horizontalScroll | Pagination.kt |
| 24 | Dead code similitudEnRevision eliminado | FlowStore.kt |
| 25 | Ternario redundante simplificado | Display.kt |
| 26 | Footer telefono formateado | HomeScreen.kt |
| 27 | "Produccion Multimedia" -> "Produccion Multimedia" (acento) | MockDataProvider.kt |
| 28 | SenaGovernmentBar agregado a RoleScaffold | ProyecTwinNavHost.kt |
| 29 | Ghost button con feedback visual sutil | Buttons.kt |
| 30 | Meses calculados dinamicamente desde fechaIngreso | MiPerfilScreen.kt |
| 31 | Rating instructor "4.8" -> "N/A" (sin datos reales) | PerfilInstructorScreen.kt |
| 32 | BugReportDto id_proyecto -> projectId mapeado | Dto.kt |
| 33 | Notification modelo + titulo mapeado en DTO | Notification.kt + Dto.kt |
| 34 | cambiarPassword (non-admin) regex 8-char policy | PerfilViewModel.kt |

---

## Movil - Fixes 35-56 (Cierre de Gaps Web-Movil)

| # | Gap | Archivo(s) |
|---|-----|------------|
| 35 | SimilitudesAdmin filtro nivel (Critico/Moderado/Bajo) | SimilitudesAdminScreen.kt |
| 36 | SimilitudesAdmin busqueda por nombre de proyecto | SimilitudesAdminScreen.kt |
| 37 | Nivel badge en SimilarityCard (Crítico/Moderado/Bajo) | Cards.kt |
| 38 | Eliminar observaciones en DetalleProyectoInstructor | DetalleProyectoInstructorScreen.kt |
| 39 | Eliminar observaciones en DetalleProyectoAdmin | DetalleProyectoAdminScreen.kt |
| 40 | eliminarObservacion() en ProyectoViewModel | ProyectoViewModel.kt |
| 41 | Contactar Aprendiz funcional (toggle on/off) | DetalleSimilitudInstructorScreen.kt |
| 42 | Badge "Creador/Lider" vs "Integrante" en proyecto | DetalleProyectoScreen.kt |
| 43 | RegisterScreen placeholder actualizado 8+ chars | RegisterScreen.kt |
| 44 | Register estado=0 -> estado=1 (usuarios activos) | AuthViewModel.kt |
| 45 | "Seleccionar todos" miembros de equipo | NuevoProyectoScreen.kt |
| 46 | Filtro por programa en GestionarFichas | GestionarFichasScreen.kt |
| 47 | Programa filter chips (ADSO/Multimedia/Infraestructura) | GestionarFichasScreen.kt |
| 48 | RevisionPropuestas: keywords -> technologies | RevisionPropuestasScreen.kt |
| 49 | RevisionPropuestas: alerta de similitud inline | RevisionPropuestasScreen.kt |
| 50 | cargarRevisionPropuestas filtra solo instructor | ProyectoViewModel.kt |
| 51 | Selector dropdown entre similitudes (Instructor) | DetalleSimilitudInstructorScreen.kt |
| 52 | DetalleFichaInstructor inline miembros + badge activo | DetalleFichaInstructorScreen.kt |
| 53-56 | Gaps 13,14,20,23-28: Ya existian o son diferencias de plataforma | N/A |

---

## Archivos Clave Modificados

### Data Layer
- MockDataProvider.kt (seed data, CRUD, filtros)
- FlowStore.kt (dead code removed)
- SessionStore.kt
- PhotoStore.kt

### Models
- Project.kt, Ficha.kt, Notification.kt, BugReport.kt, Dto.kt

### ViewModels
- ProyectoViewModel.kt, AuthViewModel.kt, PerfilViewModel.kt
- AdminViewModel.kt, DashboardViewModel.kt, FichaViewModel.kt

### Screens - Admin
- SimilitudesAdminScreen.kt, DetalleProyectoAdminScreen.kt

### Screens - Instructor
- DetalleProyectoInstructorScreen.kt, DetalleSimilitudInstructorScreen.kt
- RevisionPropuestasScreen.kt, GestionarFichasScreen.kt
- DetalleFichaInstructorScreen.kt, PerfilInstructorScreen.kt

### Screens - Aprendiz
- DetalleProyectoScreen.kt, DetalleSimilitudScreen.kt
- NuevoProyectoScreen.kt, MiPerfilScreen.kt

### Screens - Invitado
- LoginScreen.kt, RegisterScreen.kt, HomeScreen.kt

### Components
- Cards.kt, Buttons.kt, Display.kt, Pagination.kt

### Navigation
- ProyecTwinNavHost.kt, RoleTabs.kt

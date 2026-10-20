# Plan de versionado Android (Movil) y limpieza de `.idea`

> **Estado: APLICADO (18-oct-2026, Lote 8)** — pasos 1-2 ejecutados: reglas añadidas a `Movil/.gitignore` (líneas 10-13) y 4 `git rm --cached` (4 `D` staged; copias locales conservadas). El paso 3 quedó **ejecutado** (commit `d327b10`, 19-oct-2026, publicado en `master`). Repo raíz: `MVP/` (rama `master`).
> Fecha: 2026-10-18. Alcance: exclusivamente el conjunto de cambios Android pendientes en `git status` (`Movil/` + `docs/ejecutar-android.md`) y la reclasificación de `Movil/.idea`.
> Los cambios pendientes de `backend/`, `frontend/` y demás `docs/` quedan **fuera** de este plan.

Objetivo doble:

1. Versionar **todo lo necesario** para que un clon compile y pueda seguir `docs/ejecutar-android.md`.
2. Excluir del versionado **solo** los 4 archivos `.idea` específicos de máquina, preservando la configuración compartible (en particular `misc.xml` con `jbr-17`).

---

## 1. Lista exacta de archivos Android que deben versionarse

Según `git status --porcelain=v1 -uall` (ejecutado en `MVP/`):

### 1.1 Modificados (`M`) en `Movil/` — **15 archivos**

| # | Ruta | Nota |
|---|---|---|
| 1 | `Movil/.idea/deploymentTargetSelector.xml` | **Se excluye** (§2); entra al commit como borrado (§3) |
| 2 | `Movil/.idea/misc.xml` | **Se preserva**: diff = declaración XML + `temurin-17` → `jbr-17` (§2) |
| 3 | `Movil/app/src/main/java/com/example/proyectwin/data/api/service/UsersApi.kt` | paginación `PaginaGeneralUsersDto` |
| 4 | `Movil/app/src/main/java/com/example/proyectwin/data/mapper/DtoMappers.kt` | mapeos nuevos |
| 5 | `Movil/app/src/main/java/com/example/proyectwin/data/repository/UsersRepositoryImpl.kt` | usa `PaginaUsuarios` |
| 6 | `Movil/app/src/main/java/com/example/proyectwin/di/NetworkModule.kt` | |
| 7 | `Movil/app/src/main/java/com/example/proyectwin/domain/repository/UsersRepository.kt` | |
| 8 | `Movil/app/src/main/java/com/example/proyectwin/ui/screens/admin/AdminBitacoraScreen.kt` | |
| 9 | `Movil/app/src/main/java/com/example/proyectwin/ui/screens/admin/AdminFichasScreen.kt` | |
| 10 | `Movil/app/src/main/java/com/example/proyectwin/ui/screens/admin/UserManagementScreen.kt` | usa `UserManagementViewModel` |
| 11 | `Movil/app/src/main/java/com/example/proyectwin/ui/screens/aprendiz/SimilarityDetailScreen.kt` | |
| 12 | `Movil/app/src/main/java/com/example/proyectwin/ui/viewmodel/BitacoraViewModel.kt` | |
| 13 | `Movil/app/src/main/java/com/example/proyectwin/ui/viewmodel/SimilarityDetailViewModel.kt` | |
| 14 | `Movil/gradle/libs.versions.toml` | `agp` 9.2.1 → 9.4.1 |
| 15 | `Movil/gradle/wrapper/gradle-wrapper.properties` | wrapper 9.4.1 → **9.6.0** (sha actualizado) |

### 1.2 No versionados (`??`) en `Movil/` — **8 archivos**

| # | Ruta |
|---|---|
| 16 | `Movil/app/src/main/java/com/example/proyectwin/data/api/dto/PaginationDtos.kt` |
| 17 | `Movil/app/src/main/java/com/example/proyectwin/data/model/PaginaUsuarios.kt` |
| 18 | `Movil/app/src/main/java/com/example/proyectwin/ui/viewmodel/UserManagementViewModel.kt` |
| 19 | `Movil/app/src/test/java/com/example/proyectwin/data/api/dto/PaginaGeneralUsersDtoTest.kt` |
| 20 | `Movil/app/src/test/java/com/example/proyectwin/data/mapper/AuditLogMapperTest.kt` |
| 21 | `Movil/app/src/test/java/com/example/proyectwin/ui/viewmodel/BitacoraViewModelTest.kt` |
| 22 | `Movil/app/src/test/java/com/example/proyectwin/ui/viewmodel/SimilarityDetailViewModelTest.kt` |
| 23 | `Movil/app/src/test/java/com/example/proyectwin/ui/viewmodel/UserManagementViewModelTest.kt` |

### 1.3 Documento fuera de `Movil/` — **1 archivo**

| # | Ruta |
|---|---|
| 24 | `docs/ejecutar-android.md` |

**Total en el punto 1: 15 `M` + 8 `??` + 1 doc = 24 archivos.** Tras la exclusión de `deploymentTargetSelector.xml` (§2), el commit conjunto contiene **23** de ellos.

### 1.4 Por qué deben ir juntos (un commit parcial no compila)

Los archivos modificados dependen de los nuevos (`??`); HEAD no contiene ninguno de los 9 `??`:

- `Movil/.../ui/screens/admin/UserManagementScreen.kt:22-23` importa `UserManagementUiState` (línea 22) y `UserManagementViewModel` (línea 23) → requiere `UserManagementViewModel.kt` (`??`). La versión en HEAD de la pantalla **no** tiene esos imports (verificado con `git show HEAD:...`).
- `Movil/.../data/repository/UsersRepositoryImpl.kt:13` importa `com.example.proyectwin.data.model.PaginaUsuarios` → requiere `PaginaUsuarios.kt` (`??`). HEAD no lo referencia.
- `Movil/.../data/api/service/UsersApi.kt:6` importa `PaginaGeneralUsersDto` → requiere `PaginationDtos.kt` (`??`, que define el DTO). HEAD no lo referencia.
- Cadena adicional: `UserManagementViewModel.kt:6` importa `PaginaUsuarios`, y `UserManagementViewModelTest.kt:5` también; ninguno de los tres está en HEAD.

Además, `Movil/gradle/wrapper/gradle-wrapper.properties` (M) y `libs.versions.toml` (M) deben ir en el mismo commit para que `docs/ejecutar-android.md` sea cierto en un clon (§2 exige wrapper **9.6.0**; el SHA del wrapper y `agp` 9.4.1 solo están en el working tree).

### 1.5 Verificación de existencia y ausencia en HEAD

- `git show HEAD:Movil/.../PaginationDtos.kt`, `.../PaginaUsuarios.kt`, `.../UserManagementViewModel.kt` y `git show HEAD:docs/ejecutar-android.md` → `fatal: ... exists on disk, but not in 'HEAD'`.
- Comprobación programática (`Test-Path` + `git cat-file -e HEAD:<ruta>`) sobre los 9 archivos `??`: **existen en disco = True, en HEAD = False** en los 9 casos. Los 15 `M` existen en ambos (HEAD tiene versiones anteriores).
- Los 8 `??` de `Movil/` son los únicos no versionados del directorio (`git status -uall`), por lo que no hay fuentes ocultas necesarias.

---

## 2. Clasificación de `Movil/.idea` (13 archivos versionados)

`git ls-files Movil/.idea` → **13 archivos**. Propuesta: **excluir 4, preservar 9**.

| Archivo | Clasificación | Motivo |
|---|---|---|
| `.gitignore` | **Compartible** | Convención del proyecto (`/shelf/`, `/workspace.xml`); sin datos de máquina |
| `.name` | **Compartible** | Nombre del proyecto: `ProyecTwin` |
| `AndroidProjectSystem.xml` | **Compartible** | `providerId` Gradle, idéntico para todos |
| `compiler.xml` | **Compartible** | `bytecodeTargetLevel target="17"` |
| `gradle.xml` | **Compartible** | Importación Gradle (módulos `$PROJECT_DIR$`/`app`, `testRunner`); sin rutas absolutas de máquina |
| `inspectionProfiles/Project_Default.xml` | **Compartible** | Perfil "Project Default" de inspecciones |
| `misc.xml` | **Compartible (preservar)** | `project-jdk-name="jbr-17"`, `languageLevel="JDK_17"`, `ProjectType=Android`: estándar documentado |
| `runConfigurations.xml` | **Compartible** | Productores de run configs ignorados (preferencia del proyecto, no del usuario) |
| `vcs.xml` | **Compartible** | Mapeo Git `$PROJECT_DIR$/..` |
| `deploymentTargetSelector.xml` | **Máquina → excluir** | El diff `M` actual añade `DropdownSelection timestamp="2026-10-17T06:16:14.424622500Z"` con `DeviceId pluginId="Wireless" identifier="serviceName=adb-31208843-2cd0e6"`: el dispositivo adb de esta máquina; nadie más tiene ese serial |
| `deviceManager.xml` | **Máquina → excluir** | Orden de columnas del Device Manager (`Name`/`ASCENDING`): preferencia local |
| `studiobot.xml` | **Máquina → excluir** | `shareContext=OptedIn`: consentimiento del usuario para compartir contexto con StudioBot |
| `planningMode.xml` | **Máquina → excluir** | 11 entradas `approvalStates` con IDs/fechas de sesión (`20260527-...` a `20260708-...`): historial local, crece con cada sesión |

### Nota sobre `misc.xml` (preservar)

- Diff actual (M): añade `<?xml version="1.0" encoding="UTF-8"?>` y cambia `project-jdk-name="temurin-17"` → `"jbr-17"`.
- `docs/ejecutar-android.md` lo referencia como estándar: §2 paso 3 ("elegir el JDK embebido (`jbr-17`). El proyecto ya viene configurado para usarlo", línea 21) y §9 ("Ya corregido en `.idea/misc.xml`", línea 86; también línea 85). Por eso se **versiona tal cual** en el commit conjunto.
- (El §3 del documento trata del emulador/Device Manager, no de `jbr-17`; la referencia a `jbr-17` está en §1–§2 y §9.)

---

## 3. Diff exacto (aplicado el 18-oct en el Lote 8; se conserva como registro)

### 3.1 Hunk para `Movil/.gitignore`

Archivo actual (15 líneas, `git show HEAD:Movil/.gitignore` + working tree coinciden salvo la adición propuesta). Añadir las 4 líneas dentro del bloque `.idea` (tras `/.idea/assetWizardSettings.xml`):

```diff
diff --git a/Movil/.gitignore b/Movil/.gitignore
--- a/Movil/.gitignore
+++ b/Movil/.gitignore
@@ -7,6 +7,10 @@
 /.idea/workspace.xml
 /.idea/navEditor.xml
 /.idea/assetWizardSettings.xml
+.idea/deploymentTargetSelector.xml
+.idea/deviceManager.xml
+.idea/studiobot.xml
+.idea/planningMode.xml
 .DS_Store
 /build
 /captures
```

> Nota de estilo: las entradas `.idea` existentes usan prefijo `/` (`/.idea/workspace.xml`). Ambas formas son equivalentes aquí (el `.gitignore` está en la raíz de `Movil/`); se proponen las 4 líneas sin `/` tal como se solicitaron. Si se prefiere coherencia con el bloque, usar `/.idea/deploymentTargetSelector.xml`, etc. — el efecto es idéntico.

### 3.2 Comandos de exclusión (desde la raíz `MVP/`)

```powershell
git rm --cached Movil/.idea/deploymentTargetSelector.xml
git rm --cached Movil/.idea/deviceManager.xml
git rm --cached Movil/.idea/studiobot.xml
git rm --cached Movil/.idea/planningMode.xml
```

- `--cached` **no toca el working tree**: las 4 copias locales siguen en disco.
- No hace falta `-f`: el índice coincide con HEAD (los cambios están sin stage). Verificado en un repo de prueba: `git rm --cached` funciona aunque el archivo esté modificado en el working tree.
- Los 3 archivos sin cambios (`deviceManager.xml`, `studiobot.xml`, `planningMode.xml`) quedan como `D` en el índice; `deploymentTargetSelector.xml` (M) pasa también a `D` en el índice.

### 3.3 Efecto esperado en el commit futuro

`git status` del commit conjunto (28 cambios):

- **4 borrados (D)**: los 4 `.idea` de §3.2.
- **1 modificación (M)**: `Movil/.gitignore` (hunk de §3.1).
- **Resto del punto 1 (23 archivos)**:
  - 14 `M`: `Movil/.idea/misc.xml` + 11 fuentes `app/src/main/java/...` + `Movil/gradle/libs.versions.toml` + `Movil/gradle/wrapper/gradle-wrapper.properties`.
  - 8 `A` (antes `??`): los 3 fuentes nuevos + los 5 tests.
  - 1 `A`: `docs/ejecutar-android.md`.

En el clon resultante: los 4 `.idea` excluidos no existen; las 9 restantes de `.idea` sí; la app compila y `docs/ejecutar-android.md` describe el estado real (wrapper 9.6.0, `jbr-17`).

---

## 4. Consecuencias

### 4.1 De aplicar

- `git status` deja de listar los 4 archivos: se elimina el ruido de `.idea` en cada sesión de Android Studio (selección de dispositivo, orden de columnas, opt-in de StudioBot, sesiones de planning).
- Se pierde **solo configuración local** del repo: cada desarrollador conserva sus copias en disco (ignoradas) y AS las regenera cuando hacen falta.
- Se conserva toda la configuración compartible, incluido `misc.xml` con `jbr-17` (y su diff header+JDK pasa a HEAD), de modo que un clon abre con el JDK documentado.
- El commit conjunto deja HEAD compilable: los `M` de pantallas/repos/api y los `??` que necesitan entran juntos.

### 4.2 De no aplicar

- Churn continuo: `deploymentTargetSelector.xml` ya está `M` con el dispositivo `adb-31208843-2cd0e6` y su timestamp; `planningMode.xml` crecerá con cada sesión; `studiobot.xml`/`deviceManager.xml` cambian con preferencias locales.
- El commit futuro incluiría **2 `M` de `.idea`**: `deploymentTargetSelector.xml` (dato de máquina) y `misc.xml` (fix JDK compartido), mezclando ambos tipos sin criterio.
- Cada clon arrancaría con preferencias ajenas (p. ej. selección de un dispositivo inexistente).

---

## 5. Pasos de ejecución (pasos 1-3 aplicados; el commit se ejecutó el 19-oct-2026)

Orden prescrito, todo desde la raíz `MVP/`:

1. **Editar `Movil/.gitignore`** aplicando el hunk de §3.1 (4 líneas).
2. **Excluir del índice** los 4 `.idea` con los 4 `git rm --cached` de §3.2.
3. **Commit conjunto** con el punto 1:
   ```powershell
   git add Movil/.gitignore Movil/.idea/misc.xml Movil/app/src Movil/gradle docs/ejecutar-android.md
   git commit -m "feat(android): paginacion usuarios/bitacora, docs de ejecucion y limpieza de .idea local"
   ```
   (El `git add` cubre los 23 archivos restantes del punto 1; los 4 excluidos ya están ignorados y no se re-añaden.)

### Verificación del resultado

```powershell
git status --porcelain=v1 -- Movil/.idea   # debe salir vacío
git ls-files Movil/.idea                    # 9 archivos (ya no aparecen los 4)
git check-ignore -v Movil/.idea/deploymentTargetSelector.xml Movil/.idea/deviceManager.xml Movil/.idea/studiobot.xml Movil/.idea/planningMode.xml
```

Prueba de clon:

```powershell
git clone <ruta-del-repo> <carpeta-temporal>\proyectwin-clone
cd <carpeta-temporal>\proyectwin-clone\Movil
.\gradlew.bat :app:testDebugUnitTest
```

- Requisitos del clon: SDK Android 36.1 y JDK según `docs/ejecutar-android.md` (§1–§2); el wrapper descarga Gradle 9.6.0 y el toolchain 17.
- Extra opcional: `.\gradlew.bat :app:assembleDebug` (§10 del documento de ejecución).

---

## 6. Nota sobre FE-1

FE-1 (los 5 tests E2E focales del frontend) fue autorizado y ejecutado el 18-oct-2026 (5 tests aprobados, más 4 adicionales aprobados por coincidencia de filtro) y **no forma parte de este plan**: aquí solo se versiona el conjunto Android y se limpian los `.idea` de máquina.

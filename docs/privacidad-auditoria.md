# Privacidad de la bitácora de auditoría

**Lote 16 §5** · Revisión estática del 19-oct-2026.

**Alcance:** `Auditoria::registrar()` y los `detalle` que escriben controladores y
servicios. Este documento no cambia retención, no borra ni reescribe históricos y
no modifica el esquema. Los ejemplos usan valores ficticios.

## 1. Escritura y exposición

- `Auditoria::registrar()` inserta actor (`id_usuario`), `accion`, `entidad`,
  `entidad_id`, `detalle` (JSON) e `ip` (`backend/app/Support/Auditoria.php:20-27`).
- `detalle` es JSON libre y no tiene redacción central
  (`backend/database/migrations/2026_04_11_000002_create_audit_logs_table.php:19`).
  La IP se guarda en claro (`Auditoria.php:26`; migración `:20`).
- La bitácora completa solo la lee un admin por `GET /v1/audit-logs`
  (`backend/routes/api-v1.php:66`). El tablero del admin también lee las últimas
  acciones, pero solo devuelve acción, entidad, nombre del actor y fecha
  (`DashboardMetricsController.php:189-207`).
- Desde el Lote 15, la relación `user` de la bitácora se proyecta a
  `id, nombre, apellido, rol` (`AuditLogController.php:11-14,33-36`): no se
  devuelven correo, username ni foto. La prueba lo fija en
  `AuditLogPiiTest.php:76-87,102-104`. El `detalle` se devuelve sin cambios.
- No se guardan contraseñas, tokens, `password_temporal` ni `foto_url`/base64 en
  `detalle`. `cambiar_clave` no lleva detalle (`AuthController.php:148`). La foto
  solo se valida como data URL de imagen (`GeneralUserController.php:38-56`).
- Se revisaron las 36 llamadas a `Auditoria::registrar` de `backend/app`; 9
  acciones escriben PII directa en `detalle` (tabla 2.1).

## 2. Mapa de PII en `detalle`

### 2.1 Acciones con PII directa

| Acción | Archivo:línea | Campos en `detalle` | Necesidad / trazabilidad |
|---|---|---|---|
| `crear_usuario` | `backend/app/Services/AltaUsuario.php:44-49` | `correo`, `username`, `documento` (`tipo_documento` + `numero_documento`), `rol` | `entidad_id` ya identifica la cuenta y `rol` es necesario. Correo, documento y username pueden enmascararse; si se requiere correlación, usar HMAC. |
| `cambiar_correo` | `backend/app/Http/Controllers/Api/AuthController.php:111` | `correo` nuevo | Basta saber que cambió y cuándo; el valor completo puede enmascararse. Hoy no se guarda el correo anterior. |
| `solicitar_reset` | `backend/app/Http/Controllers/Api/AuthController.php:168` | `correo` | Ruta pública y `entidad_id` nulo: se registra cualquier correo escrito, exista o no. Enmascarar y usar HMAC; evitar correos de terceros en claro. |
| `restablecer_clave` | `backend/app/Http/Controllers/Api/AuthController.php:222` | `correo` | Mismo caso: no hay `entidad_id`; enmascarar/HMAC. |
| `reenviar_credenciales` | `backend/app/Http/Controllers/Api/GeneralUserController.php:155-158` | `correo`, `enviado` | Conservar `enviado`; el correo puede enmascararse porque la entidad ya está identificada. |
| `restablecer_credenciales` | `backend/app/Http/Controllers/Api/GeneralUserController.php:186-188` | `correo` | Enmascarar; `entidad_id` conserva la traza de la cuenta. |
| `actualizar_usuario` | `backend/app/Http/Controllers/Api/GeneralUserController.php:337-345` | `correo`, `nombre`, `documento`, `rol`, `estado` | Conservar `rol` y `estado`; enmascarar correo/documento; el nombre puede reducirse a iniciales o a `nombre_cambiado: true`. Solo guarda valores nuevos, no anteriores. |
| `eliminar_usuario` | `backend/app/Http/Controllers/Api/GeneralUserController.php:386-390` | `correo`, `rol`, `impacto` | Conservar `rol` e `impacto`; enmascarar el correo. Si la cuenta se borra, un HMAC permite correlacionar sin conservar el valor legible. |
| `resolver_soporte` | `backend/app/Http/Controllers/Api/BugReportController.php:105-110` | `respuesta` (texto libre), `numero_ficha` | La respuesta completa ya está en `bug_reports.respuesta`; la bitácora solo necesita constancia del cierre. Guardar `respondido`/longitud/hash; si se conserva el texto, redactar PII. |

### 2.2 Identificadores pseudónimos y texto libre

| Acción | Archivo:línea | Campos | Evaluación |
|---|---|---|---|
| `crear_aprendiz`, `actualizar_aprendiz`, `eliminar_aprendiz` | `ApprenticeController.php:83-87,170-173,182-185` | `codigo`, `id_usuario`, `id_class_group` | Identificadores institucionales/pseudónimos; conservar para la traza académica. |
| `agregar_aprendiz` | `ClassGroupController.php:194-197,224-228` | `usuario`, `creada`, `credenciales_enviadas` | Sin PII directa. La rama de cuenta nueva emite además `crear_usuario`. |
| `crear_instructor`, `actualizar_instructor`, `eliminar_instructor` | `InstructorController.php:52-55,97-100,112-114` | `id_usuario`, `fecha_ingreso` | Sin PII directa; conservar. |
| Acciones de ficha (`crear_ficha`, `finalizar_ficha`, `anular_ficha`, `reasignar_ficha`, `actualizar_ficha`, `eliminar_ficha`, `asignar_aprendices`) | `ClassGroupController.php:111-115,356-389,413-417,427-431,499-501` | `codigo`, `numero`, `nombre`, ids de instructor, conteos | Datos institucionales; conservar. |
| `crear_propuesta`, `enviar_propuesta`, `revisar_propuesta`, `eliminar_propuesta` | `ProjectController.php:109-112,195-198,379-384,520-523` | `titulo` (texto libre), estado, huella | El título es el objeto auditado, pero puede contener PII incidental; acceso restringido y política de no incluir datos personales. |
| `exportar_credenciales` | `GeneralUserController.php:409-412`; `ClassGroupController.php:257-260` | Solo conteos | Ejemplo de minimización: no registra a quién se exportó. |
| `eliminar_red`, `eliminar_programa`, `config_motor`, `recalibrar_motor` | `KnowledgeNetworkController.php:50-53`; `TrainingProgramController.php:56-59`; `MotorConfigController.php:42-45`; `Recomputador.php:124-131` | Nombres, conteos, umbral/meses | Sin PII directa; conservar. |

### 2.3 Campos de la fila fuera de `detalle`

- `id_usuario` y `entidad_id`: identificadores pseudónimos necesarios para la
  traza. Si se borra el actor, la FK queda en `NULL`
  (`...create_audit_logs_table.php:15`).
- `ip`: dato de seguridad y potencial PII; se guarda completa en
  `Auditoria.php:26`.
- Proyección del actor: `nombre` y `apellido` siguen siendo PII, aunque ya no se
  expongan correo, username ni foto. Se mantienen para pintar la UI de la
  bitácora.
- `foto_url` no se registra en ningún `detalle`. Un cambio solo de foto deja
  `actualizar_usuario` sin datos de detalle (`GeneralUserController.php:337-345`).
  Es correcto para privacidad (evita transportar base64 a la bitácora), pero no
  hay traza del cambio de foto; si se necesita, registrar un indicador o hash,
  nunca la imagen.

## 3. Necesidad frente a trazabilidad

| Campo | ¿Es necesario en claro? | Enmascarado propuesto | Qué se conserva |
|---|---|---|---|
| `correo` | No para “quién/cuándo”; solo para saber “qué correo”. En `solicitar_reset`/`restablecer_clave` no hay otra clave. | `a***@dominio.test` + HMAC si se necesita correlación | Acción, actor, entidad, fecha y correlación por HMAC. |
| `documento` | No. El número completo es dato de alta sensibilidad. | `CC ****1234` (tipo + últimos 4) + HMAC | Tipo, últimos 4 y posibilidad de correlacionar. |
| `nombre` | No para la traza; el sujeto ya está por `entidad_id`. | Iniciales o `nombre_cambiado: true` | Que el nombre cambió y sobre quién. |
| `username` | Parcialmente: identifica la credencial, pero la cuenta ya tiene id. | `u***` o HMAC; omitir si el id basta | Cuenta afectada y correlación. |
| `respuesta` | No en la bitácora: el texto completo vive en `bug_reports`. | `respondido: true`, longitud o hash | Que el soporte se cerró y su tamaño. |
| `rol`, `estado`, `impacto`, conteos, ids y códigos | Sí. Son la traza funcional. | Sin cambios | Decisión administrativa y alcance. |
| `titulo` de propuesta / nombres de ficha o red | No es PII por diseño, pero es texto libre. | Sin cambio automático | Objeto auditado; revisar caso a caso. |
| `ip` | Sí por seguridad, no indefinidamente. | Completa por un periodo corto; luego truncar o eliminar | Origen de la acción durante la ventana de investigación. |

## 4. Recomendaciones de enmascarado

1. **Redactar al escribir, no al mostrar.** La bitácora se exporta y se busca;
   enmascarar solo en la respuesta deja el dato en la base y en cualquier
   consulta directa.
2. **Redacción semántica y centralizada.** Un helper en `Auditoria` o en un
   `Support/Pii` debe tratar claves conocidas (`correo`, `documento`, `nombre`,
   `username`, `respuesta`); no conviene redactar todo el JSON a ciegas porque se
   perderían ids y estados.
3. **Patrones sugeridos:** correo `a***@dominio.test`; documento
   `CC ****1234`; nombre en iniciales; `username` como `u***`; `respuesta` como
   booleano/longitud. Para correlación usar HMAC-SHA256 con la clave de la
   aplicación, no un hash simple (un correo o documento es reversible por
   diccionario).
4. **Conservar** `accion`, `entidad`, `entidad_id`, `id_usuario`, `rol`,
   `estado`, conteos, ids de ficha y códigos de aprendiz: son necesarios para
   reconstruir la operación.
5. **No reescribir históricos por defecto.** Aplicar el enmascarado a escrituras
   nuevas. Una limpieza retroactiva requiere respaldo, revisión de la evidencia
   y decisión del propietario; puede afectar la trazabilidad de auditorías
   anteriores.
6. **Impacto en búsqueda.** `AuditLogController` busca con `LIKE` sobre
   `detalle` y sobre el correo del actor (`:47-60`). Si se enmascara el correo,
   la búsqueda por correo completo dejará de encontrar la fila; si se necesita,
   agregar un campo HMAC indexado o buscar por `entidad_id`.
7. **Pruebas.** `AuditLogPiiTest` fija la proyección del actor y conserva un
   `detalle` sintético (`:81-82`); no exige PII en `detalle`. Cualquier cambio de
   forma del detalle debe acompañarse de pruebas, sin ejecutarlas en este lote.

## 5. Decisiones que requieren al propietario

1. **Retención** de `audit_logs` y de la IP, incluyendo plazos legales o
   evidentes. No se modificó nada en este lote.
2. **Nivel de enmascarado:** si se acepta correo/documento parciales o si se
   requiere el valor completo para soporte o investigación; y si se adopta HMAC
   para correlación.
3. **Históricos:** dejarlos como están, anonimizarlos en una migración única o
   purgarlos después de la retención. Cualquier opción requiere respaldo y
   revisión legal.
4. **Usuarios eliminados:** si el correo enmascarado + HMAC basta para identificar
   la cuenta borrada o si se conserva el valor completo bajo acceso restringido.
5. **Texto libre:** política para `respuesta` de soporte, `titulo` de propuestas y
   nombres de ficha; y si se elimina la duplicación de `respuesta` en la bitácora.
6. **Actor:** mantener `nombre`/`apellido` en la proyección o reducirlos a
   iniciales; la proyección actual del Lote 15 sigue siendo PII parcial.
7. **Acceso:** hoy la bitácora es solo para admin. Valorar un rol de seguridad o
   controles adicionales para exportaciones.
8. **Marco legal aplicable:** validar la política con la oficina jurídica o de
   protección de datos correspondiente.

## 6. Exposición claramente innecesaria detectada (no aplicada)

- `resolver_soporte` duplica la `respuesta` completa en `detalle`
  (`BugReportController.php:105-110`) aunque ya está en `bug_reports.respuesta`.
  Corrección propuesta: registrar solo `respondido`, longitud o hash; no se
  aplicó porque el archivo está fuera del alcance de este lote.
- `crear_usuario` y `actualizar_usuario` guardan correo, documento, nombre o
  username completos aunque `entidad_id` ya identifica la cuenta
  (`AltaUsuario.php:44-49`; `GeneralUserController.php:337-345`). Es una
  exposición innecesaria para la traza, pero el enmascarado requiere decisión de
  política; queda documentado, no aplicado.
- `actualizar_usuario` usaba `array_filter` sin callback
  (`GeneralUserController.php:337-345`): cuando un admin desactivaba una cuenta
  (`estado = false`), la clave `estado` se descartaba del `detalle`.
  **Corregido en el Lote 16**: se filtra solo `null` y se añadió
  `AuditoriaEstadoUsuarioTest` (desactivar una cuenta queda registrado como
  `estado: false`).
- No se encontró `foto_url` ni base64 en ningún `detalle`; no hay nada que
  eliminar por ese lado.

## 7. Cambios realizados en este lote

- Documento nuevo: `docs/privacidad-auditoria.md`.
- Comentario de `foto_url` actualizado en
  `backend/database/migrations/2026_04_09_023859_create_general_users_table.php`:
  solo data URL de imagen (`png/jpg/jpeg/webp/gif`), máximo 65.000 caracteres,
  sin URLs externas. No se cambió el esquema.
- Sin cambios de retención, borrado de históricos, controladores, frontend ni
  Android.

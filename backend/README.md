# ProyecTwin MVP — Backend

API REST del MVP ProyecTwin (SENA): propuestas de proyecto, detección de
similitudes, revisión por instructores, historial, auditoría, notificaciones,
fichas, equipos y credenciales institucionales.

- **Stack:** Laravel 10 · PHP 8.1+ · MySQL · Sanctum (cookie httpOnly para el SPA, Bearer para móvil/tests).
- **Frontend:** `../frontend` (React + Vite).
- **Prefijo de API:** `/v1` (`routes/api-v1.php`).

## Requisitos

- PHP 8.1+ con extensiones habituales de Laravel.
- Composer.
- MySQL 5.7+/MariaDB con una base `proyectwin_mvp`.

## Instalación

```bash
composer install
cp .env.example .env
php artisan key:generate
```

Ajusta `.env` si hace falta (`DB_DATABASE=proyectwin_mvp`, `FRONTEND_URL`,
`SANCTUM_STATEFUL_DOMAINS`).

```bash
php artisan migrate:fresh --seed
php artisan serve
```

La API queda en `http://127.0.0.1:8000/v1`.

## Datos de demostración (Seeder)

`DatabaseSeeder` crea el escenario completo (usuarios, fichas, programas,
proyectos en todos los estados, similitudes vigentes e históricas, historial,
auditoría y notificaciones). Credenciales demo:

| Rol | Username | Contraseña |
|---|---|---|
| Aprendiz | `mgonzalez` | `123456` |
| Instructor | `cruiz` | `123456` |
| Admin | `a` | `admin123` |

Otros usuarios demo (aprendices/instructores/admin) también usan `123456`.
Los usuarios creados por el flujo real reciben contraseña temporal y
`must_change_password=true`.

## Pruebas

```bash
php artisan test          # Feature/Unit contra proyectwin_test (phpunit.xml + guarda en tests/CreatesApplication.php; NO usa el .env de desarrollo)
```

E2E (Playwright) se ejecuta desde `../frontend`:

```bash
cd ../frontend && npm run test:e2e
```

## Reglas relevantes

- **Estados de propuesta:** `borrador → pendiente → aprobado|rechazado`. El
  aprendiz edita en borrador, en revisión y rechazado; aprobado queda bloqueado
  (el admin conserva la intervención excepcional).
- **Motor de similitudes:** corre al **enviar/reenviar** y al editar en revisión
  (archiva la detección anterior y re-analiza el contenido nuevo); **aprobar no
  ejecuta el motor**. Tras una intervención admin que devuelve a revisión se
  archiva y se re-detecta igual. Los pares históricos se conservan
  (`vigente=false`) y nunca se borran al rechazar/reenviar.
- **Guarda de evidencia (D-2b × BUG-007):** `DELETE /projects/{id}` responde
  **409** si el proyecto tiene **cualquier** par de similitud (vigente o
  histórico): la evidencia es compartida con la contraparte. Las cascadas de
  instructor/programa/red también responden 409 si alguna propuesta de su
  subárbol tiene pares (mensajes fijos por entidad en `BorradoCascada`); en el
  borrado directo (`DELETE /class-groups/{id}`), la ficha con aprendices o
  propuestas no se borra, se **anula** (conserva todo y libera el número) y
  solo se elimina físicamente si está vacía. En cambio, las cascadas sí las
  eliminan físicamente (con sus propuestas) cuando no hay pares.
- **Ventana temporal (intencional):** el corpus compara contra aprobadas dentro
  de la ventana de `meses`; un par vigente se conserva si cualquiera de los dos
  proyectos está en ventana. Los antiguos se reanalizan al recalibrar desde
  Configuración del motor.
- **Credenciales:** login por `username`; contraseña temporal cifrada solo para
  exportar el PDF; el cambio obligatorio bloquea el resto de la API.
- **Autorización:** `rol:admin|instructor|aprendiz` + alcance por ficha
  (`puedeEscribir`), scoping en controladores y `Similarity::visibleDetalle`.
- **Bitácora vs historial:** `audit_logs` (administración/seguridad, inmutable)
  y `project_histories` (evolución funcional de la propuesta).

## Comandos útiles

```bash
php artisan similitud:recalcular     # recalibra pares con el umbral vigente
php artisan similitud:calibrar       # sugiere umbral por programa
php artisan perfiles:docentes        # crea perfiles de instructor faltantes
```

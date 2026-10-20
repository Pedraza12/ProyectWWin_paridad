<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\Api\GeneralUserController;
use App\Http\Controllers\Api\KnowledgeNetworkController;
use App\Http\Controllers\Api\TrainingProgramController;
use App\Http\Controllers\Api\InstructorController;
use App\Http\Controllers\Api\ClassGroupController;
use App\Http\Controllers\Api\ApprenticeController;
use App\Http\Controllers\Api\ProjectController;
use App\Http\Controllers\Api\SimilarityController;
use App\Http\Controllers\Api\NotificationController;
use App\Http\Controllers\Api\BugReportController;
use App\Http\Controllers\Api\ApprenticeProjectController;
use App\Http\Controllers\Api\AuthController;
use App\Http\Controllers\Api\CommentController;
use App\Http\Controllers\Api\MotorConfigController;
use App\Http\Controllers\Api\AuditLogController;
use App\Http\Controllers\Api\AppVersionController;
use App\Http\Controllers\Api\DashboardMetricsController;
use App\Http\Controllers\Api\MediaController;

// ------------------------------------------------------------------ Públicas
// Login y recuperación de contraseña por correo no exigen token. La creación
// de usuarios es exclusiva del administrador (grupo autenticado, abajo).
Route::post('auth/login', [AuthController::class, 'login']);
Route::post('auth/forgot-password', [AuthController::class, 'forgotPassword']);
Route::post('auth/reset-password', [AuthController::class, 'resetPassword']);

// Lectura pública (landing/demo): catálogo institucional y config del motor.
// Solo GET; la escritura sigue restringida a admin dentro del grupo autenticado.
Route::get('knowledge-networks', [KnowledgeNetworkController::class, 'index']);
Route::get('knowledge-networks/{knowledge_network}', [KnowledgeNetworkController::class, 'show']);
Route::get('training-programs', [TrainingProgramController::class, 'index']);
Route::get('training-programs/{training_program}', [TrainingProgramController::class, 'show']);
Route::get('config-similitud', [MotorConfigController::class, 'show']);
Route::get('public/resumen', [MotorConfigController::class, 'resumen']);

// Demo pública del motor: compara un texto libre contra el corpus vigente y
// devuelve solo títulos y porcentajes (nunca autores ni resúmenes).
Route::post('public/demo-similitud', [SimilarityController::class, 'demo']);

// Versión publicada de la app Android (detección de actualizaciones en móvil).
// Pública: la app la consulta sin token; se configura en app_version.php.
Route::get('app-version/android', [AppVersionController::class, 'android']);

// ------------------------------------------------------------------ Sesión
// Cuenta suspendida no puede ni siquiera usar su token vigente.
Route::middleware(['auth:sanctum', 'cuenta.activa'])->group(function () {
    Route::post('auth/logout', [AuthController::class, 'logout']);
    Route::get('auth/me', [AuthController::class, 'me']);
    Route::put('auth/email', [AuthController::class, 'changeEmail']);
    Route::put('auth/password', [AuthController::class, 'changePassword']);
});

// ------------------------------------------------------------------ Autenticadas
Route::middleware(['auth:sanctum', 'cuenta.activa', \App\Http\Middleware\CambioContrasenaObligatorio::class])->group(function () {

    // Métricas del panel: cada rol recibe solo su ámbito (calculadas en el
    // servidor; el cliente nunca elige el rol).
    Route::get('dashboard-metrics', [DashboardMetricsController::class, 'show']);

    // Subida de foto de perfil a Cloudinary (credenciales solo del servidor).
    Route::post('media/foto', [MediaController::class, 'uploadFoto']);

    // Configuración del motor de similitudes (la lectura es pública, arriba).
    Route::middleware('rol:admin')->put('config-similitud', [MotorConfigController::class, 'update']);

    // Bitácora de acciones sensibles (inmutable: no hay escritura).
    Route::middleware('rol:admin')->get('audit-logs', [AuditLogController::class, 'index']);

    // Usuarios: el listado completo y el alta son solo para admin; el detalle es
    // del propio usuario o de un admin; el perfil público lo usan las vistas.
    Route::middleware('rol:admin')->get('general-users', [GeneralUserController::class, 'index']);
    Route::middleware('rol:admin')->post('general-users', [GeneralUserController::class, 'store']);
    // Credenciales iniciales en PDF: selección de usuarios o toda una ficha.
    Route::middleware('rol:admin')->post('general-users/credenciales', [GeneralUserController::class, 'credenciales']);
    Route::middleware('rol:admin')->post('general-users/{general_user}/credenciales/reenviar', [GeneralUserController::class, 'reenviarCredenciales']);
    Route::middleware('rol:admin')->post('general-users/{general_user}/credenciales/restablecer', [GeneralUserController::class, 'restablecerCredenciales']);
    Route::get('general-users/{general_user}/perfil', [GeneralUserController::class, 'perfil']);
    Route::get('general-users/{general_user}', [GeneralUserController::class, 'show']);
    Route::put('general-users/{general_user}', [GeneralUserController::class, 'update']);
    Route::middleware('rol:admin')->delete('general-users/{general_user}', [GeneralUserController::class, 'destroy']);

    // Catálogos institucionales: la lectura es pública (arriba); escritura admin.
    Route::middleware('rol:admin')->group(function () {
        Route::post('knowledge-networks', [KnowledgeNetworkController::class, 'store']);
        Route::put('knowledge-networks/{knowledge_network}', [KnowledgeNetworkController::class, 'update']);
        Route::delete('knowledge-networks/{knowledge_network}', [KnowledgeNetworkController::class, 'destroy']);
        Route::post('training-programs', [TrainingProgramController::class, 'store']);
        Route::put('training-programs/{training_program}', [TrainingProgramController::class, 'update']);
        Route::delete('training-programs/{training_program}', [TrainingProgramController::class, 'destroy']);
    });

    // Instructores / Aprendices / Admins (altas y ediciones según rol)
    Route::get('instructors', [InstructorController::class, 'index']);
    Route::get('instructors/{instructor}', [InstructorController::class, 'show']);
    Route::get('apprentices', [ApprenticeController::class, 'index']);
    // Mi ficha: el aprendiz sale de su ficha y se une a otra con el código del
    // instructor. Va antes de `apprentices/{apprentice}` para no colisionar.
    Route::middleware('rol:aprendiz')->group(function () {
        Route::get('apprentices/me/ficha/codigo/{codigo}', [ApprenticeController::class, 'fichaPorCodigo']);
        Route::post('apprentices/me/ficha', [ApprenticeController::class, 'unirmeAFicha']);
        Route::delete('apprentices/me/ficha', [ApprenticeController::class, 'salirDeFicha']);
    });
    Route::get('apprentices/{apprentice}', [ApprenticeController::class, 'show']);
    Route::middleware('rol:admin,instructor')->group(function () {
        Route::put('instructors/{instructor}', [InstructorController::class, 'update']);
        Route::post('apprentices', [ApprenticeController::class, 'store']);
        Route::put('apprentices/{apprentice}', [ApprenticeController::class, 'update']);
    });
    Route::middleware('rol:admin')->group(function () {
        Route::post('instructors', [InstructorController::class, 'store']);
        Route::delete('instructors/{instructor}', [InstructorController::class, 'destroy']);
        Route::delete('apprentices/{apprentice}', [ApprenticeController::class, 'destroy']);
    });

    // Fichas: lectura autenticada; escritura admin/instructor
    Route::get('class-groups', [ClassGroupController::class, 'index']);
    Route::middleware('rol:admin')->get('class-groups/{class_group}/credenciales', [ClassGroupController::class, 'credenciales']);
    Route::get('class-groups/{class_group}', [ClassGroupController::class, 'show']);
    Route::middleware('rol:admin,instructor')->group(function () {
        Route::post('class-groups', [ClassGroupController::class, 'store']);
        Route::post('class-groups/{class_group}/aprendices', [ClassGroupController::class, 'agregarAprendiz']);
        Route::put('class-groups/{class_group}', [ClassGroupController::class, 'update']);
        Route::delete('class-groups/{class_group}', [ClassGroupController::class, 'destroy']);
    });

    // Propuestas
    Route::get('projects', [ProjectController::class, 'index']);
    Route::post('projects', [ProjectController::class, 'store']);
    Route::post('projects/{project}/enviar', [ProjectController::class, 'enviar']);
    Route::get('projects/{project}/historial', [ProjectController::class, 'historial']);
    Route::get('projects/{project}', [ProjectController::class, 'show']);
    Route::put('projects/{project}', [ProjectController::class, 'update']);
    Route::delete('projects/{project}', [ProjectController::class, 'destroy']);

    // Similitudes: el motor se ejecuta del lado servidor
    Route::post('similarities/detect', [SimilarityController::class, 'detect']);
    Route::get('similarities', [SimilarityController::class, 'index']);
    Route::get('similarities/{similarity}', [SimilarityController::class, 'show']);
    // Escribir pares a mano es exclusivo del admin (el motor los genera).
    Route::middleware('rol:admin')->group(function () {
        Route::post('similarities/recalculate', [SimilarityController::class, 'recalculate']);
        Route::post('similarities', [SimilarityController::class, 'store']);
        Route::put('similarities/{similarity}', [SimilarityController::class, 'update']);
        Route::delete('similarities/{similarity}', [SimilarityController::class, 'destroy']);
    });

    // Notificaciones
    Route::get('notifications', [NotificationController::class, 'index']);
    Route::post('notifications', [NotificationController::class, 'store']);
    Route::get('notifications/{notification}', [NotificationController::class, 'show']);
    Route::put('notifications/{notification}', [NotificationController::class, 'update']);
    Route::delete('notifications/{notification}', [NotificationController::class, 'destroy']);

    // Reportes de falla
    Route::get('bug-reports', [BugReportController::class, 'index']);
    Route::post('bug-reports', [BugReportController::class, 'store']);
    Route::get('bug-reports/{bug_report}', [BugReportController::class, 'show']);
    Route::put('bug-reports/{bug_report}', [BugReportController::class, 'update']);
    Route::delete('bug-reports/{bug_report}', [BugReportController::class, 'destroy']);

    // Equipo de la propuesta (pivote)
    Route::get('apprentice-projects', [ApprenticeProjectController::class, 'index']);
    Route::post('apprentice-projects', [ApprenticeProjectController::class, 'store']);
    Route::get('apprentice-projects/{apprentice_project}', [ApprenticeProjectController::class, 'show']);
    Route::delete('apprentice-projects/{apprentice_project}', [ApprenticeProjectController::class, 'destroy']);

    // Observaciones (hilos de comentarios)
    Route::get('comments', [CommentController::class, 'index']);
    Route::post('comments', [CommentController::class, 'store']);
    Route::get('comments/{comment}', [CommentController::class, 'show']);
    Route::put('comments/{comment}', [CommentController::class, 'update']);
    Route::delete('comments/{comment}', [CommentController::class, 'destroy']);
});

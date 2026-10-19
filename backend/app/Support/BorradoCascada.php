<?php

namespace App\Support;

use App\Models\Apprentice;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\MotorConfig;
use App\Models\Notification;
use App\Models\Project;
use App\Models\Similarity;
use App\Models\TrainingProgram;
use App\Services\NotificacionesService;
use Illuminate\Http\Exceptions\HttpResponseException;
use Illuminate\Support\Facades\DB;

// Borrado en cascada para el administrador (sin restricciones): elimina la
// entidad y todo lo dependiente dentro de una transacción, respetando el orden
// que exigen las claves foráneas. La responsabilidad del impacto la asume el
// admin en la confirmación del cliente.
//
// D-2b × BUG-007: ninguna cascada puede destruir evidencia de similitud
// compartida (una fila conecta dos propuestas). Antes de mutar nada se verifica
// —con el mutex global del motor tomado primero (orden O1)— que ninguna
// propuesta del subárbol esté referenciada por pares vigentes o históricos;
// si lo está, se aborta con 409 y no se modifica nada.
//
// Limitación documentada (fuera de este lote): la creación concurrente de una
// ficha dentro del programa/red no está serializada por el mutex del motor; si
// ocurriera entre la lectura y el borrado, el FK restrict aborta la transacción
// (error sin pérdida de datos).
class BorradoCascada
{
    // Ficha: borra sus propuestas (arrastran similitudes, observaciones y equipo)
    // y luego la ficha. Los aprendices se conservan sin ficha (la FK cascade los
    // borraría y perderían sus equipos en otras propuestas).
    public static function ficha(ClassGroup $ficha): void
    {
        DB::transaction(function () use ($ficha) {
            self::assertSinEvidencia([$ficha->id], self::mensajeEvidencia('ficha', $ficha->nombre));
            self::fichaInterna($ficha);
        });
    }

    // (interno: la guarda de evidencia ya se evaluó en el punto de entrada)
    private static function fichaInterna(ClassGroup $ficha): void
    {
        $aprendices = Apprentice::where('id_class_group', $ficha->id)->get();
        $idUsuarios = $aprendices->pluck('id_usuario');
        $titulo = 'Tu ficha "' . $ficha->nombre . '" fue eliminada. Quedaste sin ficha.';

        // Las propuestas de la ficha se eliminan: sus notificaciones con
        // enlace al proyecto quedarían huérfanas.
        $proyectos = Project::where('id_class_group', $ficha->id)->pluck('id');
        Notification::whereIn('enlace', $proyectos->map(fn ($id) => 'proyecto:' . $id))->delete();

        Project::where('id_class_group', $ficha->id)->get()->each->delete();
        Notification::where('enlace', 'ficha:' . $ficha->id)->delete();

        // Desvincular antes de borrar la ficha (conserva fila y pivotes).
        foreach ($aprendices as $aprendiz) {
            $aprendiz->update(['id_class_group' => null, 'id_programa' => null]);
        }

        $ficha->delete();

        foreach ($idUsuarios as $idUsuario) {
            app(NotificacionesService::class)->crear($idUsuario, $titulo, 'sistema');
        }
    }

    // Programa: borra sus fichas (con su cascada) y los aprendices del programa,
    // y luego el programa. Los aprendices se capturan antes porque ficha() los
    // desvincula del programa.
    public static function programa(TrainingProgram $programa): void
    {
        DB::transaction(function () use ($programa) {
            $fichas = ClassGroup::where('id_programa', $programa->id)->pluck('id');
            self::assertSinEvidencia($fichas, self::mensajeEvidencia('programa', $programa->nombre));
            self::programaInterno($programa);
        });
    }

    // (interno: la guarda de evidencia ya se evaluó en el punto de entrada)
    private static function programaInterno(TrainingProgram $programa): void
    {
        $aprendices = Apprentice::where('id_programa', $programa->id)->pluck('id');
        ClassGroup::where('id_programa', $programa->id)->get()->each(fn ($f) => self::fichaInterna($f));
        Apprentice::whereIn('id', $aprendices)->delete();
        $programa->delete();
    }

    // Red de conocimiento: borra sus programas (con su cascada) y luego la red.
    public static function red(KnowledgeNetwork $red): void
    {
        DB::transaction(function () use ($red) {
            $fichas = ClassGroup::whereIn(
                'id_programa',
                TrainingProgram::where('knowledge_network_id', $red->id)->pluck('id')
            )->pluck('id');
            self::assertSinEvidencia($fichas, self::mensajeEvidencia('red', $red->nombre));
            self::redInterna($red);
        });
    }

    // (interno: la guarda de evidencia ya se evaluó en el punto de entrada)
    private static function redInterna(KnowledgeNetwork $red): void
    {
        TrainingProgram::where('knowledge_network_id', $red->id)->get()
            ->each(fn ($p) => self::programaInterno($p));
        $red->delete();
    }

    // Instructor: borra sus fichas (con su cascada); el perfil lo elimina el
    // controlador tras esta llamada, dentro de la misma transacción.
    public static function instructor(Instructor $instructor): void
    {
        DB::transaction(function () use ($instructor) {
            $fichas = ClassGroup::where('id_instructor', $instructor->id)->pluck('id');
            self::assertSinEvidencia($fichas, self::mensajeEvidencia('instructor'));
            ClassGroup::where('id_instructor', $instructor->id)->get()
                ->each(fn ($f) => self::fichaInterna($f));
        });
    }

    // Usuario: si es instructor con fichas, se borran esas fichas (con su
    // cascada); luego el borrado del usuario arrastra propuestas, notificaciones,
    // comentarios, reportes y filas de perfil (FK cascade).
    // D-01 × BUG-007: antes de mutar nada se verifica la evidencia compartida
    // de su subárbol (propuestas de sus fichas como instructor + propuestas
    // propias) con el mutex del motor ya tomado (orden O1). Los usuarios con
    // rol admin quedan exentos y conservan el borrado sin restricciones
    // (exención documentada: su impacto lo asume el admin que confirma).
    public static function usuario(GeneralUser $usuario): void
    {
        DB::transaction(function () use ($usuario) {
            $instructor = Instructor::where('id_usuario', $usuario->id)->first();

            if ($usuario->rol !== 'admin') {
                $fichas = $instructor
                    ? ClassGroup::where('id_instructor', $instructor->id)->pluck('id')
                    : collect();
                self::assertSinEvidencia($fichas, self::mensajeEvidencia('usuario'), $usuario->id);
            }

            if ($instructor) {
                ClassGroup::where('id_instructor', $instructor->id)->get()
                    ->each(fn ($f) => self::fichaInterna($f));
            }
            // Sus propias propuestas se eliminan en cascada: limpia los avisos
            // que apuntaban a ellas.
            $proyectos = Project::where('id_creador', $usuario->id)->pluck('id');
            Notification::whereIn('enlace', $proyectos->map(fn ($id) => 'proyecto:' . $id))->delete();

            // Reportes del usuario: la FK los borra; limpia los avisos a admins.
            $reportes = \App\Models\BugReport::where('id_usuario', $usuario->id)->pluck('id');
            Notification::whereIn('enlace', $reportes->map(fn ($id) => 'reporte:' . $id))->delete();

            // Tokens de sesión y de recuperación (no tienen FK al usuario).
            $usuario->tokens()->delete();
            DB::table('password_reset_tokens')
                ->where('email', strtolower((string) $usuario->correo))
                ->delete();

            $usuario->delete();
        });
    }

    // ------------------------------------------------------------ guarda D-2b

    // D-2b extendida a cascadas: con el mutex global del motor ya tomado
    // (canon Recomputador / ProjectController::destroy, orden O1), comprueba
    // mediante lecturas de bloqueo —que ven lo último confirmado, no la
    // instantánea REPEATABLE READ— si alguna propuesta del subárbol está
    // referenciada por un par de similitud (vigente o histórico, interno o con
    // contraparte externa). Si lo está, aborta con 409 antes de mutar nada.
    // `$idCreador` (rama creador de D-01) fusiona las propuestas propias del
    // usuario en el mismo subárbol.
    private static function assertSinEvidencia(iterable $fichaIds, string $mensaje, ?int $idCreador = null): void
    {
        // Mutex del motor SIEMPRE primero: serializa contra todo creador de
        // pares (el motor, el alta manual y los flujos de propuestas lo toman
        // antes de escribir) y garantiza la fila del motor en el arranque.
        $config = MotorConfig::firstOrCreate([], ['umbral' => 0.30, 'meses' => 12]);
        MotorConfig::whereKey($config->id)->lockForUpdate()->first();

        // Propuestas del subárbol (lectura de bloqueo: filas actuales).
        $proyectos = Project::whereIn('id_class_group', $fichaIds)->lockForUpdate()->pluck('id');
        if ($idCreador !== null) {
            $proyectos = $proyectos->merge(
                Project::where('id_creador', $idCreador)->lockForUpdate()->pluck('id')
            );
        }
        if ($proyectos->isEmpty()) {
            return;
        }

        $pares = Similarity::whereIn('id_proyecto_1', $proyectos)
            ->orWhereIn('id_proyecto_2', $proyectos)
            ->lockForUpdate()
            ->count();

        if ($pares > 0) {
            throw new HttpResponseException(
                response()->json(['message' => $mensaje], 409)
            );
        }
    }

    // Mensajes 409 fijos por tipo de borrado (formato {"message"} como D-2b).
    private static function mensajeEvidencia(string $tipo, ?string $nombre = null): string
    {
        return match ($tipo) {
            'ficha' => "No puedes eliminar la ficha «{$nombre}»: sus propuestas tienen similitudes compartidas (vigentes o históricas). La evidencia pertenece también a las demás propuestas.",
            'programa' => "No puedes eliminar el programa «{$nombre}»: las propuestas de sus fichas tienen similitudes compartidas (vigentes o históricas). La evidencia pertenece también a las demás propuestas.",
            'red' => "No puedes eliminar la red «{$nombre}»: las propuestas de sus programas tienen similitudes compartidas (vigentes o históricas). La evidencia pertenece también a las demás propuestas.",
            'instructor' => 'No puedes eliminar al instructor: las propuestas de sus fichas tienen similitudes compartidas (vigentes o históricas). La evidencia pertenece también a las demás propuestas.',
            'usuario' => 'No puedes eliminar al usuario: sus propuestas o las de sus fichas tienen similitudes compartidas (vigentes o históricas). La evidencia pertenece también a las demás propuestas.',
            default => 'No puedes eliminar este elemento: sus propuestas tienen similitudes compartidas (vigentes o históricas). La evidencia pertenece también a las demás propuestas.',
        };
    }
}

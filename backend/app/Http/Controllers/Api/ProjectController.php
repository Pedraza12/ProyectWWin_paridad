<?php

namespace App\Http\Controllers\Api;

use App\Models\Apprentice;
use App\Models\ApprenticeProject;
use App\Models\ClassGroup;
use App\Models\Comment;
use App\Models\GeneralUser;
use App\Models\Notification;
use App\Models\Project;
use App\Models\ProjectHistory;
use App\Models\Similarity;
use App\Services\NotificacionesService;
use App\Similarity\Recomputador;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class ProjectController extends Controller
{
    public function index(Request $request)
    {
        // Params inválidos (p. ej. arrays) se rechazan con 422, nunca 500.
        $request->validate([
            'search' => 'nullable|string',
            'estado' => 'nullable|string',
            'ficha_id' => 'nullable|string',
            'programa' => 'nullable|string',
        ]);

        return Project::included()
            ->paraUsuario($request->user())
            ->search($request->query('search'))
            ->byEstado($request->query('estado'))
            ->byFicha($request->query('ficha_id'))
            ->byPrograma($request->query('programa'))
            ->orderByDesc('id')
            ->get();
    }

    public function store(Request $request)
    {
        $request->validate([
            'titulo' => 'nullable|max:255',
            'resumen' => 'nullable',
            'palabras_clave' => 'nullable|max:255',
            'area_aplicacion' => 'nullable|max:255',
            'objetivo_general' => 'nullable',
            'objetivos_especificos' => 'nullable|array',
            'estado' => 'nullable|in:borrador,pendiente,aprobado,rechazado',
            'id_creador' => 'nullable|exists:general_users,id',
            'id_instructor_asignado' => 'nullable|exists:instructors,id',
            'id_class_group' => 'nullable|exists:class_groups,id',
        ]);

        $usuario = $request->user();
        $rol = optional($usuario)->rol;
        // Whitelist: la huella de envío la calcula el servidor al enviar:
        // nunca llega del cliente.
        $datos = $request->only([
            'titulo', 'resumen', 'palabras_clave', 'area_aplicacion',
            'objetivo_general', 'objetivos_especificos', 'estado',
            'id_creador', 'id_instructor_asignado', 'id_class_group',
        ]);

        if ($rol === 'aprendiz') {
            // La propuesta vive en la ficha ACTUAL del aprendiz (activa) y con
            // el instructor de esa ficha: no se confía en el request.
            $aprendiz = Apprentice::where('id_usuario', $usuario->id)->first();
            if (!$aprendiz || !$aprendiz->id_class_group) {
                return response()->json(['message' => 'Únete a una ficha para registrar propuestas.'], 422);
            }
            $ficha = ClassGroup::find($aprendiz->id_class_group);
            if (!$ficha || $ficha->estado !== 'activo') {
                return response()->json(['message' => 'Tu ficha no está activa para recibir propuestas.'], 422);
            }
            $datos['id_class_group'] = $ficha->id;
            $datos['id_instructor_asignado'] = $ficha->id_instructor;
            $datos['id_creador'] = $usuario->id;
            // Toda propuesta nace como borrador: el envío es explícito.
            $datos['estado'] = 'borrador';
        } elseif ($rol === 'admin') {
            // Un admin puede registrarla a nombre de otro, pero el autor debe
            // ser un aprendiz (integridad de dominio).
            if (!empty($datos['id_creador'])) {
                $creador = GeneralUser::find($datos['id_creador']);
                if (!$creador || $creador->rol !== 'aprendiz') {
                    return response()->json(['message' => 'La propuesta debe pertenecer a un aprendiz.'], 422);
                }
            } else {
                $datos['id_creador'] = $usuario->id;
            }
            // Toda propuesta nace como borrador (también las del admin): el
            // envío explícito es el que ejecuta el motor de similitud (D-3).
            $datos['estado'] = 'borrador';
        } else {
            // Solo aprendices (o un admin a nombre de uno) pueden ser autores:
            // una propuesta sin autor aprendiz rompe el dominio del motor.
            return response()->json(['message' => 'Solo un aprendiz puede registrar propuestas.'], 403);
        }

        // Alta atómica: propuesta + equipo + auditoría (todo o nada).
        $item = DB::transaction(function () use ($datos) {
            $item = Project::create($datos);
            // El creador forma parte del equipo: se registra en el pivote para que
            // los conteos y el listado de integrantes sean consistentes.
            $this->asegurarCreadorEnEquipo($item);
            \App\Support\Auditoria::registrar('crear_propuesta', 'projects', $item->id, [
                'titulo' => $item->titulo,
                'estado' => $item->estado,
            ]);
            \App\Support\HistorialProyecto::registrar($item, 'creada', [
                'titulo' => $item->titulo,
            ]);
            return $item;
        });

        return response()->json($item, 201);
    }

    // Envío explícito del borrador (o reenvío tras un rechazo): valida la
    // propuesta completa, ejecuta el motor de similitud en el servidor y avisa
    // al instructor. El frontend ya no dispara el análisis.
    //
    // Regla del motor (intencional): corre al enviar/reenviar y al editar en
    // revisión; NO al crear ni al aprobar. Tras una intervención admin que
    // devuelve a revisión, el contenido se archiva y se re-detecta.
    public function enviar(Request $request, Project $project)
    {
        $usuario = $request->user();
        $rol = optional($usuario)->rol;

        $permitido = $rol === 'admin'
            || ($rol === 'aprendiz'
                && $this->esPropiaDelAprendiz($project, $usuario)
                && $project->puedeEscribir($usuario));
        if (!$permitido) {
            return response()->json(['message' => 'No puedes enviar esta propuesta.'], 403);
        }

        if (!in_array($project->estado, ['borrador', 'rechazado'], true)) {
            $mensaje = $project->estado === 'aprobado'
                ? 'La propuesta ya está aprobada.'
                : 'La propuesta ya fue enviada y está en revisión.';
            return response()->json(['message' => $mensaje], 422);
        }

        // Validación estricta del contenido al enviar (el borrador es libre).
        $faltantes = $this->camposFaltantes($project);
        if ($faltantes) {
            return response()->json([
                'message' => 'Completa la propuesta antes de enviarla: ' . implode(', ', $faltantes) . '.',
                'campos' => $faltantes,
            ], 422);
        }

        // Reenvío sin cambios reales: se compara con el último envío.
        $huella = $this->huellaContenido($project);
        $eraRechazada = $project->estado === 'rechazado';
        if ($eraRechazada && $project->huella_envio && $project->huella_envio === $huella) {
            return response()->json([
                'message' => 'No hay cambios respecto a la versión anterior. Modifica la propuesta antes de reenviarla.',
            ], 422);
        }

        $item = DB::transaction(function () use ($project, $huella, $eraRechazada) {
            // Orden global de bloqueos (D-2b): el mutex del motor se adquiere
            // SIEMPRE antes de tocar la propuesta (evita el AB-BA con destroy).
            $config = \App\Models\MotorConfig::firstOrCreate([], ['umbral' => 0.30, 'meses' => 12]);
            \App\Models\MotorConfig::whereKey($config->id)->lockForUpdate()->first();

            // Lock de la fila: dos envíos concurrentes no pueden pasar la
            // transición; el segundo espera y recibe 422 al re-validar.
            $bloqueado = Project::whereKey($project->id)->lockForUpdate()->first();
            if (!$bloqueado || !in_array($bloqueado->estado, ['borrador', 'rechazado'], true)) {
                throw new \Illuminate\Http\Exceptions\HttpResponseException(
                    response()->json(['message' => 'La propuesta ya fue enviada y está en revisión.'], 422)
                );
            }

            $project->update(['estado' => 'pendiente', 'huella_envio' => $huella]);

            // Reenvío: la detección de la versión rechazada queda como evidencia
            // histórica y el motor analiza el contenido nuevo.
            if ($eraRechazada) {
                app(Recomputador::class)->archivar($project);
            }

            // El motor corre en el servidor (guarda pares y notifica al creador).
            $creadas = app(Recomputador::class)->detectar($project);

            \App\Support\Auditoria::registrar('enviar_propuesta', 'projects', $project->id, [
                'titulo' => $project->titulo,
                'huella' => $huella,
            ]);

            \App\Support\HistorialProyecto::registrar($project, $eraRechazada ? 'reenviada' : 'enviada', [
                'huella' => $huella,
            ]);

            // Avisa al instructor: reenvío (nueva revisión) o envío inicial.
            if ($eraRechazada) {
                app(NotificacionesService::class)->propuestaReenviada($project);
            } else {
                app(NotificacionesService::class)->revisionPropuesta($project);
            }
            // Si el motor encontró coincidencias, el instructor las revisa.
            if ($creadas > 0) {
                app(NotificacionesService::class)->similitudesEncontradas($project, $creadas);
            }

            return $project->fresh();
        });

        return $item;
    }

    public function show(Request $request, $id)
    {
        $user = $request->user();

        // Vista: propia/de la ficha o contraparte de una similitud autorizada
        // (solo lectura). La gestión sigue siendo del propietario/equipo/admin.
        $visible = Project::where('id', $id)->visiblePara($user)->exists();

        if (!$visible) {
            return response()->json(['message' => 'No tienes acceso a esta propuesta.'], 403);
        }

        return Project::included()->findOrFail($id);
    }

    public function update(Request $request, Project $project)
    {
        $request->validate([
            'titulo' => 'nullable|max:255',
            'resumen' => 'nullable',
            'palabras_clave' => 'nullable|max:255',
            'area_aplicacion' => 'nullable|max:255',
            'objetivo_general' => 'nullable',
            'objetivos_especificos' => 'nullable|array',
            'estado' => 'nullable|in:borrador,pendiente,aprobado,rechazado',
            // Observación del instructor al rechazar (opcional).
            'observacion' => 'nullable|string|max:1000',
            'id_creador' => 'nullable|exists:general_users,id',
            'id_instructor_asignado' => 'nullable|exists:instructors,id',
            'id_class_group' => 'nullable|exists:class_groups,id',
        ]);

        $usuario = $request->user();
        $rol = optional($usuario)->rol;

        // Autorización por rol: aprendiz solo las suyas; instructor solo las de
        // su ficha o asignadas; admin cualquiera.
        if ($rol === 'aprendiz') {
            if (!$this->esPropiaDelAprendiz($project, $usuario)) {
                return response()->json(['message' => 'No puedes editar una propuesta que no es tuya.'], 403);
            }
            // Solo lectura si ya no perteneces a la ficha (o está finalizada).
            if (!$project->puedeEscribir($usuario)) {
                return response()->json([
                    'message' => 'Solo lectura: ya no perteneces a la ficha de esta propuesta.',
                ], 403);
            }
            // El aprendiz edita en borrador, en revisión o rechazada; el envío
            // (y el cambio de estado) es una acción explícita.
            if (!in_array($project->estado, ['borrador', 'pendiente', 'rechazado'], true)) {
                return response()->json([
                    'message' => 'Solo puedes editar una propuesta en borrador, en revisión o rechazada.',
                ], 422);
            }
        } elseif ($rol === 'instructor') {
            if (!$this->esInstructorDeLaPropuesta($project, $usuario)) {
                return response()->json(['message' => 'No puedes editar una propuesta fuera de tus fichas.'], 403);
            }
            // Un proyecto aprobado es estado final del flujo normal: el
            // instructor no lo edita (el admin conserva su intervención).
            if ($project->estado === 'aprobado') {
                return response()->json([
                    'message' => 'Un proyecto aprobado no se edita en el flujo normal.',
                ], 422);
            }
            // Transiciones válidas: solo desde "pendiente" hacia aprobado/rechazado.
            $nuevoEstado = $request->input('estado');
            if ($nuevoEstado && $nuevoEstado !== $project->estado
                && !($project->estado === 'pendiente' && in_array($nuevoEstado, ['aprobado', 'rechazado'], true))) {
                return response()->json(['message' => 'Transición no permitida desde el estado actual.'], 422);
            }
        } elseif ($rol !== 'admin') {
            return response()->json(['message' => 'Sin permiso para editar propuestas.'], 403);
        }

        // Un borrador no salta el flujo (D-3): el envío explícito es el que
        // ejecuta el motor de similitud. El admin conserva las transiciones de
        // revisión existentes (pendiente→aprobado/rechazado, intervención).
        if ($rol === 'admin' && $project->estado === 'borrador'
            && $request->filled('estado') && $request->input('estado') !== 'borrador') {
            return response()->json([
                'message' => 'Un borrador debe enviarse con "Enviar propuesta" para analizarse antes de cambiar de estado.',
            ], 422);
        }

        // D-1: coherencia estado↔análisis del motor. Estas retrotransiciones
        // administrativas dejarían la propuesta sin detección vigente (o con
        // pares vigentes colgando en un borrador); el camino válido es el
        // reenvío canónico, que archiva, re-analiza y sella la huella.
        if ($rol === 'admin' && $request->filled('estado') && $request->input('estado') !== $project->estado) {
            $bloqueos = [
                'rechazado>aprobado' => 'Una propuesta rechazada no puede aprobarse directamente: debe reenviarse y analizarse primero.',
                'rechazado>pendiente' => 'Una propuesta rechazada no puede volver a revisión sin reenviarse y analizarse.',
                'pendiente>borrador' => 'Una propuesta enviada no puede volver a borrador; permanece en revisión o se rechaza.',
            ];
            $clave = $project->estado . '>' . $request->input('estado');
            if (isset($bloqueos[$clave])) {
                return response()->json(['message' => $bloqueos[$clave]], 422);
            }
        }

        // Whitelist: `observacion` no es columna (alimenta el rechazo) y la
        // huella de envío la fija el servidor en `enviar`: no se edita a mano.
        $datos = $request->only([
            'titulo', 'resumen', 'palabras_clave', 'area_aplicacion',
            'objetivo_general', 'objetivos_especificos', 'estado',
            'id_creador', 'id_instructor_asignado', 'id_class_group',
        ]);

        // La propuesta no cambia de ficha ni de creador por esta vía
        // (trazabilidad histórica); se conservan los valores actuales.
        unset($datos['id_class_group'], $datos['id_creador']);
        $datos['id_creador'] = $project->id_creador;
        // El instructor asignado solo lo reasigna un admin (evita que el aprendiz
        // o el equipo "secuestren" la asignación).
        if ($rol !== 'admin') {
            unset($datos['id_instructor_asignado']);
        }
        // El aprendiz no cambia estados por esta vía (usa "Enviar propuesta").
        if ($rol === 'aprendiz') {
            unset($datos['estado']);
        }

        // ¿Cambió el contenido descriptivo? Sirve para devolver a revisión una
        // aprobada editada por el staff y para registrarlo en el historial.
        $camposContenido = ['titulo', 'resumen', 'palabras_clave', 'area_aplicacion', 'objetivo_general', 'objetivos_especificos'];
        $contenidoCambio = false;
        foreach ($camposContenido as $c) {
            if (!$request->has($c)) continue;
            if (json_encode($request->input($c)) !== json_encode($project->{$c})) {
                $contenidoCambio = true;
                break;
            }
        }
        if ($rol !== 'aprendiz' && $project->estado === 'aprobado' && $contenidoCambio) {
            $datos['estado'] = 'pendiente';
        }

        $estadoAnterior = $project->estado;
        $observacion = trim((string) $request->input('observacion', ''));

        // Actualización atómica: propuesta + comentario + historial + avisos.
        DB::transaction(function () use ($project, $datos, $rol, $estadoAnterior, $observacion, $contenidoCambio, $usuario) {
            // Orden global de bloqueos (D-2b): el mutex del motor se adquiere
            // SIEMPRE antes de tocar la propuesta (evita el AB-BA con destroy).
            $config = \App\Models\MotorConfig::firstOrCreate([], ['umbral' => 0.30, 'meses' => 12]);
            \App\Models\MotorConfig::whereKey($config->id)->lockForUpdate()->first();

            $project->update($datos);

            // Aprobar/rechazar queda en la bitácora (decisión con efecto académico).
            if ($rol !== 'aprendiz' && $estadoAnterior !== $project->estado) {
                // Intervención excepcional del admin: una aprobada editada
                // vuelve a revisión y se marca como intervención administrativa.
                $intervencionAdmin = $rol === 'admin'
                    && $estadoAnterior === 'aprobado'
                    && $project->estado === 'pendiente';

                \App\Support\Auditoria::registrar('revisar_propuesta', 'projects', $project->id, array_filter([
                    'de' => $estadoAnterior,
                    'a' => $project->estado,
                    'titulo' => $project->titulo,
                    'intervencion_admin' => $intervencionAdmin ?: null,
                ]));

                // Observación opcional del instructor al rechazar: se guarda como
                // comentario del proyecto.
                if ($project->estado === 'rechazado' && $observacion !== '') {
                    Comment::create([
                        'texto' => $observacion,
                        'id_proyecto' => $project->id,
                        'id_usuario' => $usuario->id,
                        'respuesta_a' => null,
                    ]);
                }

                // Historial de la decisión (con la observación si la hubo).
                $accionHistorial = $project->estado === 'aprobado'
                    ? 'aprobada'
                    : ($project->estado === 'rechazado' ? 'rechazada' : null);
                if ($accionHistorial) {
                    \App\Support\HistorialProyecto::registrar($project, $accionHistorial, array_filter([
                        'contenido_cambiado' => $contenidoCambio ?: null,
                        'observacion' => $project->estado === 'rechazado' ? ($observacion ?: null) : null,
                    ]));
                } elseif ($intervencionAdmin) {
                    \App\Support\HistorialProyecto::registrar($project, 'devuelta_revision', [
                        'intervencion_admin' => true,
                    ]);
                }

                // La versión rechazada conserva sus coincidencias como evidencia
                // histórica (dejan de estar vigentes).
                if ($project->estado === 'rechazado') {
                    app(Recomputador::class)->archivar($project);
                }

                // Intervención admin: la detección del contenido aprobado queda
                // como evidencia histórica y el motor analiza el contenido nuevo.
                if ($intervencionAdmin) {
                    $motor = app(Recomputador::class);
                    $motor->archivar($project);
                    $motor->detectar($project, false);
                }

                // Avisa al creador de la decisión (el rechazo incluye la observación).
                if ($project->estado === 'aprobado') {
                    app(NotificacionesService::class)->propuestaAprobada($project);
                } elseif ($project->estado === 'rechazado') {
                    app(NotificacionesService::class)->propuestaRechazada($project, $observacion ?: null);
                }
            } elseif ($contenidoCambio) {
                \App\Support\HistorialProyecto::registrar($project, 'actualizada');

                // Edición en revisión (cualquier rol editor: aprendiz,
                // instructor o admin): la detección anterior corresponde al
                // contenido enviado (se conserva como histórica) y el motor
                // analiza el contenido nuevo.
                if ($project->estado === 'pendiente') {
                    $motor = app(Recomputador::class);
                    $motor->archivar($project);
                    $motor->detectar($project, false);
                }
            }
        });

        return $project;
    }

    // Historial de la propuesta (participantes y staff con alcance).
    public function historial(Request $request, $id)
    {
        $user = $request->user();
        if (!Project::where('id', $id)->paraDetalle($user)->exists()) {
            return response()->json(['message' => 'No tienes acceso al historial de esta propuesta.'], 403);
        }

        return ProjectHistory::included()
            ->where('id_proyecto', $id)
            ->orderByDesc('id')
            ->get();
    }

    public function destroy(Request $request, Project $project)
    {
        $usuario = $request->user();
        $rol = optional($usuario)->rol;

        // Solo el CREADOR aprendiz (dentro de su ficha activa) o un admin.
        $permitido = $rol === 'admin'
            || ($rol === 'aprendiz'
                && (int) $project->id_creador === (int) $usuario->id
                && $project->puedeEscribir($usuario));
        if (!$permitido) {
            return response()->json(['message' => 'No puedes eliminar esta propuesta.'], 403);
        }

        // El creador conserva borrar borradores y rechazadas; una propuesta ya
        // enviada (en revisión o aprobada) protege su evidencia y la de la
        // contraparte de las similitudes compartidas (D-2).
        if ($rol === 'aprendiz' && in_array($project->estado, ['pendiente', 'aprobado'], true)) {
            return response()->json([
                'message' => 'No puedes eliminar una propuesta en revisión o aprobada.',
            ], 409);
        }

        // D-2b: la evidencia de similitud es compartida (una fila conecta dos
        // propuestas). Si existe cualquier par —vigente o archivado—, el
        // borrado físico destruiría evidencia de la contraparte: se rechaza.
        $mensajeEvidencia = 'No puedes eliminar una propuesta con similitudes compartidas: la evidencia pertenece también a las demás propuestas.';
        $tienePares = fn (): bool => Similarity::where('id_proyecto_1', $project->id)
            ->orWhere('id_proyecto_2', $project->id)
            ->exists();
        if ($tienePares()) {
            return response()->json(['message' => $mensajeEvidencia], 409);
        }

        return DB::transaction(function () use ($project, $mensajeEvidencia) {
            // Re-verificación serializada con el motor (mismo mutex que
            // Recomputador::detectar) y como LECTURA DE BLOQUEO (actual, no la
            // instantánea de REPEATABLE READ): ve cualquier par confirmado antes
            // de adquirir el mutex y sus locks de rango cubren la ventana.
            // Inicialización canónica (igual que Recomputador): garantiza la fila
            // del motor también en el arranque y toma SIEMPRE el mutex.
            $config = \App\Models\MotorConfig::firstOrCreate([], ['umbral' => 0.30, 'meses' => 12]);
            \App\Models\MotorConfig::whereKey($config->id)->lockForUpdate()->first();
            $paresBloqueados = Similarity::where('id_proyecto_1', $project->id)
                ->orWhere('id_proyecto_2', $project->id)
                ->lockForUpdate()
                ->count();
            if ($paresBloqueados > 0) {
                throw new \Illuminate\Http\Exceptions\HttpResponseException(
                    response()->json(['message' => $mensajeEvidencia], 409)
                );
            }

            $project->delete();
            // Evita notificaciones con enlace a una propuesta ya inexistente.
            Notification::where('enlace', 'proyecto:' . $project->id)->delete();
            \App\Support\Auditoria::registrar('eliminar_propuesta', 'projects', $project->id, [
                'titulo' => $project->titulo,
                'estado' => $project->estado,
            ]);

            return $project;
        });
    }

    // ---------------------------------------------------------------- Internos

    // ¿La propuesta es del aprendiz (creador o integrante del equipo)?
    private function esPropiaDelAprendiz(Project $project, $usuario): bool
    {
        if ((int) $project->id_creador === (int) $usuario->id) return true;

        $filaAprendiz = $usuario->apprentice()->first();
        if (!$filaAprendiz) return false;

        return ApprenticeProject::where('id_proyecto', $project->id)
            ->where('id_aprendiz', $filaAprendiz->id)
            ->exists();
    }

    // ¿El instructor está asignado a la propuesta o es el de su ficha?
    private function esInstructorDeLaPropuesta(Project $project, $usuario): bool
    {
        $instructor = $usuario->instructor()->first();
        if (!$instructor) return false;

        if ((int) $project->id_instructor_asignado === (int) $instructor->id) return true;

        return (int) optional($project->classGroup)->id_instructor === (int) $instructor->id;
    }

    // Inserta la fila del creador en el pivote si aún no está.
    private function asegurarCreadorEnEquipo(Project $project): void
    {
        $filaAprendiz = Apprentice::where('id_usuario', $project->id_creador)->first();
        if (!$filaAprendiz) return;

        ApprenticeProject::firstOrCreate([
            'id_proyecto' => $project->id,
            'id_aprendiz' => $filaAprendiz->id,
        ]);
    }

    // Campos obligatorios para enviar la propuesta (coherente con el formulario).
    private function camposFaltantes(Project $project): array
    {
        $faltantes = [];
        if (mb_strlen(trim((string) $project->titulo)) < 5) $faltantes[] = 'título';
        if (mb_strlen(trim((string) $project->resumen)) < 20) $faltantes[] = 'descripción';
        if (mb_strlen(trim((string) $project->area_aplicacion)) < 3) $faltantes[] = 'área de aplicación';
        if (mb_strlen(trim((string) $project->objetivo_general)) < 15) $faltantes[] = 'objetivo general';

        $objetivos = collect($project->objetivos_especificos ?? [])
            ->filter(fn ($o) => mb_strlen(trim((string) $o)) >= 8);
        if ($objetivos->count() < 2) $faltantes[] = 'al menos 2 objetivos específicos';

        return $faltantes;
    }

    // Huella del contenido que define la propuesta (bloquea reenvíos sin cambios).
    private function huellaContenido(Project $project): string
    {
        return sha1(json_encode([
            $project->titulo,
            $project->resumen,
            $project->palabras_clave,
            $project->area_aplicacion,
            $project->objetivo_general,
            $project->objetivos_especificos,
        ]));
    }

}

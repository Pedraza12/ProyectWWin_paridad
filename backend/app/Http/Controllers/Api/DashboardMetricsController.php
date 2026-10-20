<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Apprentice;
use App\Models\AuditLog;
use App\Models\ClassGroup;
use App\Models\Comment;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\Project;
use App\Models\ProjectHistory;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;

/**
 * Métricas reales de los paneles, calculadas en el backend y acotadas al rol
 * autenticado (el cliente no elige el rol).
 *
 * `periodo` (3m|30d|7d) cambia realmente el rango de la serie temporal:
 *  - 3m: últimos 3 meses (buckets mensuales).
 *  - 30d: últimos 30 días (buckets diarios).
 *  - 7d: últimos 7 días (buckets diarios).
 * Cada bucket incluye los títulos de las propuestas del periodo (máx. 6) para
 * el tooltip del dashboard; el resto se informa en `restantes`.
 */
class DashboardMetricsController extends Controller
{
    private const PERIODOS = ['3m', '30d', '7d'];
    private const MAX_TITULOS = 6;
    private const MAX_ACTIVIDAD = 12;

    public function show(Request $request)
    {
        $user = $request->user();
        $periodo = $request->query('periodo', '3m');
        if (!in_array($periodo, self::PERIODOS, true)) {
            $periodo = '3m';
        }

        return match ($user->rol) {
            'admin' => $this->admin($periodo),
            'instructor' => $this->instructor($user, $periodo),
            default => $this->aprendiz($user, $periodo),
        };
    }

    private function admin(string $periodo): array
    {
        $usuarios = GeneralUser::selectRaw('rol, count(*) as total')
            ->groupBy('rol')
            ->pluck('total', 'rol');

        return [
            'rol' => 'admin',
            'periodo' => $periodo,
            'usuarios' => [
                'total' => (int) $usuarios->sum(),
                'aprendices' => (int) ($usuarios['aprendiz'] ?? 0),
                'instructores' => (int) ($usuarios['instructor'] ?? 0),
                'admins' => (int) ($usuarios['admin'] ?? 0),
            ],
            'fichas' => ['total' => ClassGroup::count()],
            'aprendices' => ['total' => Apprentice::count()],
            'propuestas' => $this->porEstado(Project::query()),
            'evolucion' => $this->evolucionProyectos(Project::query(), $periodo),
            'actividad' => $this->actividadAuditoria(),
        ];
    }

    private function instructor(GeneralUser $user, string $periodo): array
    {
        $instructorId = Instructor::where('id_usuario', $user->id)->value('id');

        $fichasIds = ClassGroup::query()
            ->where('id_instructor', $instructorId ?: 0)
            ->pluck('id');

        $proyectos = Project::query()->where(function ($q) use ($instructorId, $fichasIds) {
            $q->where('id_instructor_asignado', $instructorId ?: 0);
            if ($fichasIds->isNotEmpty()) {
                $q->orWhereIn('id_class_group', $fichasIds);
            }
        });

        $idsProyectos = (clone $proyectos)->pluck('id');

        return [
            'rol' => 'instructor',
            'periodo' => $periodo,
            'fichas' => ['total' => $fichasIds->count()],
            'aprendices' => [
                'total' => $fichasIds->isEmpty()
                    ? 0
                    : Apprentice::whereIn('id_class_group', $fichasIds)->count(),
            ],
            'propuestas' => $this->porEstado($proyectos),
            'evolucion' => $this->evolucionRevisiones($idsProyectos, $periodo),
            'actividad' => $this->actividadProyectos($idsProyectos),
        ];
    }

    private function aprendiz(GeneralUser $user, string $periodo): array
    {
        $proyectos = Project::query()->deAutor($user);
        $ids = (clone $proyectos)->pluck('id');

        $observaciones = $ids->isEmpty()
            ? []
            : Comment::whereIn('id_proyecto', $ids)
                ->with('user:id,nombre,apellido,rol')
                ->orderByDesc('id')
                ->limit(self::MAX_ACTIVIDAD)
                ->get()
                ->map(function (Comment $comentario) {
                    $texto = trim((string) $comentario->texto);
                    $autor = trim((string) optional($comentario->user)->nombre . ' ' . (string) optional($comentario->user)->apellido);

                    return [
                        'id' => $comentario->id,
                        'titulo' => mb_strlen($texto) > 120 ? mb_substr($texto, 0, 120) . '…' : $texto,
                        'meta' => $autor !== '' ? 'Observación de ' . $autor : 'Observación',
                        'usuario' => $autor,
                        'proyecto_id' => $comentario->id_proyecto,
                        'fecha' => optional($comentario->created_at)->toIso8601String(),
                    ];
                })
                ->values()
                ->all();

        return [
            'rol' => 'aprendiz',
            'periodo' => $periodo,
            'propuestas' => $this->porEstado($proyectos),
            'evolucion' => $this->evolucionProyectos($proyectos, $periodo),
            'actividad' => $observaciones,
        ];
    }

    /** Conteo por estado (una sola consulta agregada). */
    private function porEstado($query): array
    {
        $conteo = (clone $query)
            ->selectRaw('estado, count(*) as total')
            ->groupBy('estado')
            ->pluck('total', 'estado');

        return [
            'total' => (int) $conteo->sum(),
            'borrador' => (int) ($conteo['borrador'] ?? 0),
            'pendiente' => (int) ($conteo['pendiente'] ?? 0),
            'aprobado' => (int) ($conteo['aprobado'] ?? 0),
            'rechazado' => (int) ($conteo['rechazado'] ?? 0),
        ];
    }

    /** Rango real del periodo (inicio/fin) y claves de agrupación. */
    private function rango(string $periodo): array
    {
        return match ($periodo) {
            '7d' => [now()->subDays(6)->startOfDay(), 'Y-m-d', 7, 'dia'],
            '30d' => [now()->subDays(29)->startOfDay(), 'Y-m-d', 30, 'dia'],
            default => [now()->subMonths(2)->startOfMonth(), 'Y-m', 3, 'mes'],
        };
    }

    /** Serie de propuestas creadas en el periodo (con títulos para el tooltip). */
    private function evolucionProyectos($query, string $periodo): array
    {
        [$inicio, $formato, $cantidad, $tipo] = $this->rango($periodo);

        $proyectos = (clone $query)
            ->where('created_at', '>=', $inicio)
            ->get(['id', 'titulo', 'created_at']);

        $grupos = [];
        foreach ($proyectos as $proyecto) {
            $fecha = Carbon::parse($proyecto->created_at);
            $clave = $fecha->format($formato);
            if (!isset($grupos[$clave])) {
                $grupos[$clave] = ['total' => 0, 'titulos' => []];
            }
            $grupos[$clave]['total']++;
            if (count($grupos[$clave]['titulos']) < self::MAX_TITULOS) {
                $grupos[$clave]['titulos'][] = $proyecto->titulo;
            }
        }

        return $this->construirSerie($periodo, $tipo, $cantidad, $grupos);
    }

    /** Revisiones del instructor en el periodo (historiales "revisar_propuesta"). */
    private function evolucionRevisiones($idsProyectos, string $periodo): array
    {
        [$inicio, $formato, $cantidad, $tipo] = $this->rango($periodo);

        if ($idsProyectos->isEmpty()) {
            return $this->construirSerie($periodo, $tipo, $cantidad, []);
        }

        $historiales = ProjectHistory::with('project:id,titulo')
            ->whereIn('id_proyecto', $idsProyectos)
            ->where('accion', 'revisar_propuesta')
            ->where('created_at', '>=', $inicio)
            ->get(['id', 'id_proyecto', 'created_at']);

        $grupos = [];
        foreach ($historiales as $historial) {
            $clave = Carbon::parse($historial->created_at)->format($formato);
            if (!isset($grupos[$clave])) {
                $grupos[$clave] = ['total' => 0, 'titulos' => []];
            }
            $grupos[$clave]['total']++;
            $titulo = optional($historial->project)->titulo;
            if ($titulo && !in_array($titulo, $grupos[$clave]['titulos'], true) && count($grupos[$clave]['titulos']) < self::MAX_TITULOS) {
                $grupos[$clave]['titulos'][] = $titulo;
            }
        }

        return $this->construirSerie($periodo, $tipo, $cantidad, $grupos);
    }

    /** Genera los buckets completos (incluye periodos sin registros). */
    private function construirSerie(string $periodo, string $tipo, int $cantidad, array $grupos): array
    {
        $serie = [];
        for ($i = $cantidad - 1; $i >= 0; $i--) {
            $fecha = $tipo === 'mes' ? now()->subMonths($i) : now()->subDays($i);
            $clave = $fecha->format($tipo === 'mes' ? 'Y-m' : 'Y-m-d');
            $datos = $grupos[$clave] ?? ['total' => 0, 'titulos' => []];

            $serie[] = [
                'clave' => $clave,
                'label' => $tipo === 'mes'
                    ? $fecha->locale('es')->isoFormat('MMM')
                    : $fecha->format('d/m'),
                'fecha' => $tipo === 'mes'
                    ? $fecha->locale('es')->isoFormat('MMMM [de] YYYY')
                    : $fecha->locale('es')->isoFormat('ddd D [de] MMMM'),
                'total' => $datos['total'],
                'propuestas' => $datos['titulos'],
                'restantes' => max(0, $datos['total'] - count($datos['titulos'])),
            ];
        }

        return $serie;
    }

    private function actividadAuditoria(): array
    {
        return AuditLog::with(['user:id,nombre,apellido,rol'])
            ->orderByDesc('id')
            ->limit(self::MAX_ACTIVIDAD)
            ->get()
            ->map(function (AuditLog $log) {
                $usuario = trim((string) optional($log->user)->nombre . ' ' . (string) optional($log->user)->apellido);

                return [
                    'id' => $log->id,
                    'titulo' => (string) $log->accion,
                    'meta' => trim(($log->entidad ?? '') . ($log->entidad_id ? ' #' . $log->entidad_id : '')) ?: 'Sistema',
                    'usuario' => $usuario,
                    'proyecto_id' => null,
                    'fecha' => optional($log->created_at)->toIso8601String(),
                ];
            })
            ->values()
            ->all();
    }

    private function actividadProyectos($idsProyectos): array
    {
        if ($idsProyectos->isEmpty()) {
            return [];
        }

        return ProjectHistory::whereIn('id_proyecto', $idsProyectos)
            ->with(['user:id,nombre,apellido,rol', 'project:id,titulo'])
            ->orderByDesc('id')
            ->limit(self::MAX_ACTIVIDAD)
            ->get()
            ->map(function (ProjectHistory $historial) {
                $usuario = trim((string) optional($historial->user)->nombre . ' ' . (string) optional($historial->user)->apellido);

                return [
                    'id' => $historial->id,
                    'titulo' => (string) $historial->accion,
                    'meta' => optional($historial->project)->titulo ?: 'Propuesta',
                    'usuario' => $usuario,
                    'proyecto_id' => $historial->id_proyecto,
                    'fecha' => optional($historial->created_at)->toIso8601String(),
                ];
            })
            ->values()
            ->all();
    }
}

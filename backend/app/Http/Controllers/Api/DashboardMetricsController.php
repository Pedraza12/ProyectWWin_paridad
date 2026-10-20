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
 * autenticado (el cliente no elige el rol). Devuelve una sola carga para
 * todas las gráficas del tablero.
 */
class DashboardMetricsController extends Controller
{
    private const MESES = 6;

    public function show(Request $request)
    {
        $user = $request->user();

        return match ($user->rol) {
            'admin' => $this->admin(),
            'instructor' => $this->instructor($user),
            default => $this->aprendiz($user),
        };
    }

    private function admin(): array
    {
        $usuarios = GeneralUser::selectRaw('rol, count(*) as total')
            ->groupBy('rol')
            ->pluck('total', 'rol');

        return [
            'rol' => 'admin',
            'usuarios' => [
                'total' => (int) $usuarios->sum(),
                'aprendices' => (int) ($usuarios['aprendiz'] ?? 0),
                'instructores' => (int) ($usuarios['instructor'] ?? 0),
                'admins' => (int) ($usuarios['admin'] ?? 0),
            ],
            'fichas' => ['total' => ClassGroup::count()],
            'aprendices' => ['total' => Apprentice::count()],
            'propuestas' => $this->porEstado(Project::query()),
            'evolucion' => $this->evolucion(Project::query()),
            'actividad' => $this->actividadAuditoria(),
        ];
    }

    private function instructor(GeneralUser $user): array
    {
        $instructorId = Instructor::where('id_usuario', $user->id)->value('id');

        $fichasIds = ClassGroup::query()
            ->when(true, fn ($q) => $q->where('id_instructor', $instructorId ?: 0))
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
            'fichas' => ['total' => $fichasIds->count()],
            'aprendices' => [
                'total' => $fichasIds->isEmpty()
                    ? 0
                    : Apprentice::whereIn('id_class_group', $fichasIds)->count(),
            ],
            'propuestas' => $this->porEstado($proyectos),
            'evolucion' => $this->evolucionRevisiones($idsProyectos),
            'actividad' => $this->actividadProyectos($idsProyectos),
        ];
    }

    private function aprendiz(GeneralUser $user): array
    {
        $proyectos = Project::query()->deAutor($user);
        $ids = (clone $proyectos)->pluck('id');

        $observaciones = $ids->isEmpty()
            ? []
            : Comment::whereIn('id_proyecto', $ids)
                ->with('user')
                ->orderByDesc('id')
                ->limit(6)
                ->get()
                ->map(function (Comment $comentario) {
                    $texto = trim((string) $comentario->texto);
                    $autor = trim((string) optional($comentario->user)->nombre . ' ' . (string) optional($comentario->user)->apellido);

                    return [
                        'id' => $comentario->id,
                        'titulo' => mb_strlen($texto) > 120 ? mb_substr($texto, 0, 120) . '…' : $texto,
                        'meta' => $autor !== '' ? 'Observación de ' . $autor : 'Observación',
                        'fecha' => optional($comentario->created_at)->toIso8601String(),
                    ];
                })
                ->values()
                ->all();

        return [
            'rol' => 'aprendiz',
            'propuestas' => $this->porEstado($proyectos),
            'evolucion' => $this->evolucion($proyectos),
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

    /** Serie mensual de los últimos 6 meses (incluye meses sin registros). */
    private function evolucion($query, string $columna = 'created_at'): array
    {
        $desde = Carbon::now()->subMonths(self::MESES - 1)->startOfMonth();
        $fechas = (clone $query)->where($columna, '>=', $desde)->pluck($columna);

        return $this->serieMensual($fechas);
    }

    private function evolucionRevisiones($idsProyectos): array
    {
        if ($idsProyectos->isEmpty()) {
            return $this->serieMensual(collect());
        }

        return $this->serieMensual(
            ProjectHistory::whereIn('id_proyecto', $idsProyectos)
                ->where('accion', 'revisar_propuesta')
                ->where('created_at', '>=', Carbon::now()->subMonths(self::MESES - 1)->startOfMonth())
                ->pluck('created_at')
        );
    }

    private function serieMensual($fechas): array
    {
        $porMes = [];
        foreach ($fechas as $fecha) {
            if (! $fecha) {
                continue;
            }
            $clave = Carbon::parse($fecha)->format('Y-m');
            $porMes[$clave] = ($porMes[$clave] ?? 0) + 1;
        }

        $serie = [];
        for ($i = self::MESES - 1; $i >= 0; $i--) {
            $mes = Carbon::now()->subMonths($i);
            $clave = $mes->format('Y-m');
            $serie[] = [
                'mes' => $clave,
                'label' => $mes->locale('es')->isoFormat('MMM'),
                'total' => $porMes[$clave] ?? 0,
            ];
        }

        return $serie;
    }

    private function actividadAuditoria(): array
    {
        return AuditLog::with('user')
            ->orderByDesc('id')
            ->limit(6)
            ->get()
            ->map(function (AuditLog $log) {
                $usuario = trim((string) optional($log->user)->nombre . ' ' . (string) optional($log->user)->apellido);

                return [
                    'id' => $log->id,
                    'titulo' => (string) $log->accion,
                    'meta' => trim(($log->entidad ?? '') . ($log->entidad_id ? ' #' . $log->entidad_id : '')) ?: 'Sistema',
                    'usuario' => $usuario,
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
            ->with(['user', 'project'])
            ->orderByDesc('id')
            ->limit(6)
            ->get()
            ->map(function (ProjectHistory $historial) {
                $usuario = trim((string) optional($historial->user)->nombre . ' ' . (string) optional($historial->user)->apellido);

                return [
                    'id' => $historial->id,
                    'titulo' => (string) $historial->accion,
                    'meta' => optional($historial->project)->titulo ?: 'Propuesta',
                    'usuario' => $usuario,
                    'fecha' => optional($historial->created_at)->toIso8601String(),
                ];
            })
            ->values()
            ->all();
    }
}

<?php

namespace App\Support;

use App\Models\Project;
use App\Models\ProjectHistory;

// Registro de la evolución de una propuesta. Nunca rompe la acción principal:
// si falla el insert del historial, se ignora silenciosamente (igual que la
// auditoría).
class HistorialProyecto
{
    public static function registrar(Project $project, string $accion, array $detalle = [], ?int $idUsuario = null): void
    {
        try {
            $request = request();
            $actor = $idUsuario ?? optional($request->user() ?? auth('sanctum')->user())->id;

            ProjectHistory::create([
                'id_proyecto' => $project->id,
                'id_usuario' => $actor,
                'accion' => $accion,
                'detalle' => $detalle ?: null,
            ]);
        } catch (\Throwable $e) {
            // Silencio deliberado para la acción principal (P-02): el historial
            // nunca la rompe, pero la traza queda registrada para diagnóstico.
            report($e);
        }
    }
}

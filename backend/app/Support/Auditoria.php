<?php

namespace App\Support;

use App\Models\AuditLog;

// Registro de acciones sensibles. Nunca rompe la acción principal: si falla el
// insert de la bitácora, se ignora silenciosamente.
class Auditoria
{
    public static function registrar(string $accion, ?string $entidad = null, $entidadId = null, array $detalle = []): void
    {
        try {
            $request = request();

            // En rutas públicas (registro) el guard por defecto no resuelve el
            // token; se consulta explícitamente el de Sanctum para saber quién fue.
            $actor = $request->user() ?? auth('sanctum')->user();

            AuditLog::create([
                'id_usuario' => optional($actor)->id,
                'accion' => $accion,
                'entidad' => $entidad,
                'entidad_id' => $entidadId,
                'detalle' => $detalle ?: null,
                'ip' => $request->ip(),
            ]);
        } catch (\Throwable $e) {
            // Silencio deliberado para la acción principal (P-02): la bitácora
            // nunca la rompe, pero la traza queda registrada para diagnóstico.
            report($e);
        }
    }
}

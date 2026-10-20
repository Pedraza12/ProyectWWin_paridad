<?php

namespace App\Http\Controllers\Api;

use App\Models\AuditLog;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class AuditLogController extends Controller
{
    // Datos mínimos del responsable para la UI de la bitácora. Se excluyen
    // correo, username y foto (base64): la respuesta no debe transportar PII
    // innecesaria aunque el log incluya la relación `user`.
    private const CAMPOS_USUARIO = ['id', 'nombre', 'apellido', 'rol'];

    // Bitácora inmutable (solo admin): filtros, búsqueda, orden y paginación
    // real en el servidor para no enviar listas interminables al cliente.
    public function index(Request $request)
    {
        $request->validate([
            'buscar' => 'nullable|string|max:120',
            'accion' => 'nullable|string|max:60',
            'entidad' => 'nullable|string|max:60',
            'id_usuario' => 'nullable|integer',
            'rol' => 'nullable|in:aprendiz,instructor,admin',
            'desde' => 'nullable|date',
            'hasta' => 'nullable|date',
            'orden' => 'nullable|in:recientes,antiguos',
            'paginado' => 'nullable|boolean',
            'por_pagina' => 'nullable|integer|min:5|max:100',
        ]);

        $query = AuditLog::included()
            // Después de `included()` para que `?included=user` no reemplace la
            // proyección: la relación siempre sale con los campos mínimos.
            ->with(['user' => fn ($q) => $q->select(self::CAMPOS_USUARIO)])
            ->when($request->query('accion'), fn ($q, $a) => $q->where('accion', $a))
            ->when($request->query('entidad'), fn ($q, $e) => $q->where('entidad', $e))
            ->when($request->query('id_usuario'), fn ($q, $u) => $q->where('id_usuario', $u))
            ->when($request->query('rol'), fn ($q, $rol) => $q->whereHas(
                'user',
                fn ($qq) => $qq->where('rol', $rol)
            ))
            ->when($request->query('desde'), fn ($q, $d) => $q->whereDate('created_at', '>=', $d))
            ->when($request->query('hasta'), fn ($q, $h) => $q->whereDate('created_at', '<=', $h));

        // Búsqueda libre: acción, entidad, detalle y datos del responsable.
        $buscar = trim((string) $request->query('buscar'));
        if ($buscar !== '') {
            $t = '%' . $buscar . '%';
            $query->where(function ($q) use ($t) {
                $q->where('accion', 'like', $t)
                    ->orWhere('entidad', 'like', $t)
                    ->orWhere('detalle', 'like', $t)
                    ->orWhereHas('user', function ($qq) use ($t) {
                        $qq->where('nombre', 'like', $t)
                            ->orWhere('apellido', 'like', $t)
                            ->orWhere('username', 'like', $t)
                            ->orWhere('correo', 'like', $t);
                    });
            });
        }

        $query->orderBy('id', $request->query('orden') === 'antiguos' ? 'asc' : 'desc');

        if ($request->boolean('paginado')) {
            $porPagina = (int) $request->query('por_pagina', 15);
            return $query->paginate($porPagina);
        }

        // Compatibilidad con clientes que esperan el arreglo completo.
        return $query->limit(500)->get();
    }
}

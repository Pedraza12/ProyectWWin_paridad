<?php

namespace App\Http\Controllers\Api;

use App\Models\BugReport;
use App\Models\Notification;
use App\Services\NotificacionesService;
use App\Support\Auditoria;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class BugReportController extends Controller
{
    private const TIPOS = 'sistema,proyecto,datos,bug_ui,error_datos,rendimiento,seguridad,otro';

    public function index(Request $request)
    {
        $user = $request->user();

        // El admin ve todos; cada usuario solo sus propios reportes.
        return BugReport::included()
            ->when(optional($user)->rol !== 'admin', fn ($q) => $q->where('id_usuario', $user->id))
            ->orderByDesc('id')
            ->get();
    }

    public function store(Request $request)
    {
        $request->validate([
            'titulo' => 'nullable|max:255',
            'numero_ficha' => 'nullable|max:30',
            'motivo' => 'nullable|max:120',
            'descripcion' => 'required',
            'tipo' => 'required|in:' . self::TIPOS,
            'fecha' => 'required|date',
            'id_usuario' => 'nullable|exists:general_users,id',
        ]);

        // Soporte por conflicto de ficha: si no llega título, se genera con el
        // número involucrado para que la bandeja del admin lo identifique.
        $titulo = $request->titulo;
        if (!$titulo && $request->numero_ficha) {
            $titulo = 'Conflicto con la ficha ' . $request->numero_ficha;
        }

        // La solicitud siempre se firma con el usuario del token.
        $item = BugReport::create([
            'titulo' => $titulo,
            'numero_ficha' => $request->numero_ficha,
            'motivo' => $request->motivo,
            'descripcion' => $request->descripcion,
            'tipo' => $request->tipo,
            // El estado inicial siempre lo controla el backend.
            'estado' => 'pendiente',
            'fecha' => $request->fecha,
            'id_usuario' => $request->user()->id,
        ]);

        // Avisa a cada administrador activo que entró una solicitud nueva.
        $this->notificarAdmins($item);

        return response()->json($item, 201);
    }

    public function show(Request $request, $id)
    {
        $item = BugReport::included()->findOrFail($id);
        $user = $request->user();
        if ((int) $item->id_usuario !== (int) $user->id && $user->rol !== 'admin') {
            return response()->json(['message' => 'No tienes acceso a este reporte.'], 403);
        }
        return $item;
    }

    public function update(Request $request, BugReport $bug_report)
    {
        if ($request->user()->rol !== 'admin') {
            return response()->json(['message' => 'Solo un administrador puede actualizar reportes.'], 403);
        }

        $request->validate([
            'titulo' => 'nullable|max:255',
            'numero_ficha' => 'nullable|max:30',
            'motivo' => 'nullable|max:120',
            'descripcion' => 'required',
            'tipo' => 'required|in:' . self::TIPOS,
            'estado' => 'nullable|in:pendiente,en_revision,resuelto,cerrado,rechazado',
            'respuesta' => 'nullable|string|max:2000',
            'fecha' => 'required|date',
            'id_usuario' => 'required|exists:general_users,id',
        ]);

        $estadoAnterior = $bug_report->estado;
        $bug_report->update($request->only([
            'titulo', 'numero_ficha', 'motivo', 'descripcion', 'tipo', 'estado',
            'respuesta', 'fecha', 'id_usuario',
        ]));

        // Cierre de la solicitud (resuelta/rechazada): bitácora + aviso al
        // solicitante. Solo cuenta la primera transición hacia el cierre.
        $seCierra = in_array($bug_report->estado, ['resuelto', 'rechazado'], true)
            && !in_array($estadoAnterior, ['resuelto', 'rechazado'], true);

        if ($seCierra) {
            Auditoria::registrar('resolver_soporte', 'bug_reports', $bug_report->id, [
                'de' => $estadoAnterior,
                'a' => $bug_report->estado,
                'respuesta' => $bug_report->respuesta,
                'numero_ficha' => $bug_report->numero_ficha,
            ]);

            app(NotificacionesService::class)->soporteResuelto($bug_report);
        }

        return $bug_report;
    }

    public function destroy(Request $request, BugReport $bug_report)
    {
        if ($request->user()->rol !== 'admin') {
            return response()->json(['message' => 'Solo un administrador puede eliminar reportes.'], 403);
        }

        $bug_report->delete();
        // Evita notificaciones con enlace a un reporte ya inexistente.
        Notification::where('enlace', 'reporte:' . $bug_report->id)->delete();
        return $bug_report;
    }

    // Crea una notificación por cada admin activo (el panel filtra por usuario).
    private function notificarAdmins(BugReport $reporte): void
    {
        app(NotificacionesService::class)->reporteFalla($reporte);
    }
}

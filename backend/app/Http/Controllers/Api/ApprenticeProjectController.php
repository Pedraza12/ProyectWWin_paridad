<?php

namespace App\Http\Controllers\Api;

use App\Models\Apprentice;
use App\Models\ApprenticeProject;
use App\Models\Instructor;
use App\Models\Project;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class ApprenticeProjectController extends Controller
{
    // Alcance por rol: admin todo; instructor las de sus fichas/asignadas;
    // aprendiz las de sus propias propuestas (creador o equipo).
    public function index(Request $request)
    {
        $user = $request->user();

        if (optional($user)->rol === 'admin') {
            return ApprenticeProject::included()->get();
        }

        if ($user && $user->rol === 'instructor') {
            $instructorId = Instructor::where('id_usuario', $user->id)->value('id');
            if (!$instructorId) return collect();
            $ids = Project::where('id_instructor_asignado', $instructorId)
                ->orWhereHas('classGroup', fn ($q) => $q->where('id_instructor', $instructorId))
                ->pluck('id');
            return ApprenticeProject::included()->whereIn('id_proyecto', $ids)->get();
        }

        $ids = Project::where('id_creador', optional($user)->id)
            ->orWhereHas('apprentices', fn ($q) => $q->where('id_usuario', optional($user)->id))
            ->pluck('id');
        return ApprenticeProject::included()->whereIn('id_proyecto', $ids)->get();
    }

    public function store(Request $request)
    {
        $request->validate([
            'id_aprendiz' => 'required|exists:apprentices,id',
            'id_proyecto' => 'required|exists:projects,id',
        ]);

        $proyecto = Project::findOrFail($request->id_proyecto);

        if (!$this->puedeGestionarEquipo($request, (int) $proyecto->id)
            || !$proyecto->puedeEscribir($request->user())) {
            return response()->json(['message' => 'No puedes gestionar el equipo de esta propuesta.'], 403);
        }

        // El equipo se gestiona mientras la propuesta es editable (borrador,
        // en revisión o rechazada); aprobada queda bloqueada.
        if ($request->user()->rol === 'aprendiz' && !in_array($proyecto->estado, ['borrador', 'pendiente', 'rechazado'], true)) {
            return response()->json([
                'message' => 'Solo puedes gestionar el equipo mientras la propuesta es editable.',
            ], 422);
        }

        // Invariante: el integrante debe pertenecer a la ficha de la propuesta.
        $aprendiz = Apprentice::findOrFail($request->id_aprendiz);
        if ($proyecto->id_class_group
            && (int) $aprendiz->id_class_group !== (int) $proyecto->id_class_group) {
            return response()->json([
                'message' => 'El aprendiz no pertenece a la ficha de la propuesta.',
            ], 422);
        }

        $item = ApprenticeProject::firstOrCreate([
            'id_aprendiz' => $aprendiz->id,
            'id_proyecto' => $proyecto->id,
        ]);

        return response()->json($item, $item->wasRecentlyCreated ? 201 : 200);
    }

    public function show(Request $request, $id)
    {
        $item = ApprenticeProject::included()->findOrFail($id);
        // La fila solo es visible si la propuesta está dentro del alcance.
        if (!Project::where('id', $item->id_proyecto)->paraDetalle($request->user())->exists()) {
            return response()->json(['message' => 'No tienes acceso a este equipo.'], 403);
        }
        return $item;
    }

    // Retiro idempotente: borrar dos veces no falla (evita el 404 crudo del
    // binding) y nunca permite sacar al creador de su propia propuesta.
    public function destroy(Request $request, $id)
    {
        $item = ApprenticeProject::find($id);
        if (!$item) {
            return response()->json(['message' => 'El integrante ya no está en el equipo.']);
        }

        $proyecto = Project::find($item->id_proyecto);
        if (!$this->puedeGestionarEquipo($request, (int) $item->id_proyecto)
            || !$proyecto
            || !$proyecto->puedeEscribir($request->user())) {
            return response()->json(['message' => 'No puedes gestionar el equipo de esta propuesta.'], 403);
        }

        if ($request->user()->rol === 'aprendiz'
            && !in_array($proyecto->estado, ['borrador', 'pendiente', 'rechazado'], true)) {
            return response()->json([
                'message' => 'Solo puedes gestionar el equipo mientras la propuesta es editable.',
            ], 422);
        }

        $aprendiz = Apprentice::find($item->id_aprendiz);
        if ($proyecto && $aprendiz && (int) $aprendiz->id_usuario === (int) $proyecto->id_creador) {
            return response()->json([
                'message' => 'El creador no puede salir del equipo de su propia propuesta.',
            ], 409);
        }

        $item->delete();
        return response()->json(['message' => 'Integrante retirado del equipo.']);
    }

    // Admin cualquiera; el creador aprendiz el suyo; el instructor su ficha.
    private function puedeGestionarEquipo(Request $request, int $idProyecto): bool
    {
        $user = $request->user();
        if (!$user) return false;
        if ($user->rol === 'admin') return true;

        $project = Project::find($idProyecto);
        if (!$project) return false;

        if ($user->rol === 'aprendiz') {
            return (int) $project->id_creador === (int) $user->id;
        }

        if ($user->rol === 'instructor') {
            $instructor = $user->instructor()->first();
            if (!$instructor) return false;
            return (int) $project->id_instructor_asignado === (int) $instructor->id
                || (int) optional($project->classGroup)->id_instructor === (int) $instructor->id;
        }

        return false;
    }
}

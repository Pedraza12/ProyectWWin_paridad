<?php

namespace App\Http\Controllers\Api;

use App\Models\Apprentice;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class InstructorController extends Controller
{
    public function index(Request $request)
    {
        $user = $request->user();

        // Admin ve todos; instructor su propia fila; aprendiz los de su ficha.
        if (optional($user)->rol === 'admin') {
            return Instructor::included()->get();
        }
        if ($user && $user->rol === 'instructor') {
            // Sin auto-creación: la verificación se resuelve en el registro.
            return Instructor::included()->where('id_usuario', $user->id)->get();
        }

        $fichaIds = Apprentice::where('id_usuario', optional($user)->id)->pluck('id_class_group')->filter();
        $instructorIds = ClassGroup::whereIn('id', $fichaIds)->pluck('id_instructor')->filter();

        return Instructor::included()->whereIn('id', $instructorIds)->get();
    }

    public function store(Request $request)
    {
        $request->validate([
            'fecha_ingreso' => 'required|date',
            'id_usuario' => 'required|exists:general_users,id',
        ]);

        $usuario = GeneralUser::findOrFail($request->id_usuario);
        if ($usuario->rol !== 'instructor') {
            return response()->json(['message' => 'El usuario no tiene rol de instructor.'], 422);
        }

        $item = Instructor::firstOrCreate(
            ['id_usuario' => $request->id_usuario],
            ['fecha_ingreso' => $request->fecha_ingreso]
        );

        // Alta real del perfil (no un no-op del firstOrCreate): queda en la bitácora.
        if ($item->wasRecentlyCreated) {
            \App\Support\Auditoria::registrar('crear_instructor', 'instructors', $item->id, [
                'id_usuario' => $item->id_usuario,
                'fecha_ingreso' => $item->fecha_ingreso,
            ]);
        }

        return response()->json($item, 201);
    }

    public function show(Request $request, $id)
    {
        $item = Instructor::included()->findOrFail($id);
        $user = $request->user();

        // Mismo alcance que el listado: admin todos; instructor su propia fila;
        // aprendiz los instructores de su ficha.
        if (optional($user)->rol === 'admin') {
            return $item;
        }
        if ($user && $user->rol === 'instructor' && (int) $item->id_usuario === (int) $user->id) {
            return $item;
        }
        if ($user && $user->rol === 'aprendiz') {
            $fichaIds = Apprentice::where('id_usuario', $user->id)->pluck('id_class_group')->filter();
            if (ClassGroup::whereIn('id', $fichaIds)->where('id_instructor', $item->id)->exists()) {
                return $item;
            }
        }

        return response()->json(['message' => 'No tienes acceso a este instructor.'], 403);
    }

    public function update(Request $request, Instructor $instructor)
    {
        // Solo el propio instructor (o un admin) edita su perfil.
        $user = $request->user();
        if (optional($user)->rol !== 'admin' && (int) $instructor->id_usuario !== (int) $user->id) {
            return response()->json(['message' => 'No puedes modificar el perfil de otro instructor.'], 403);
        }

        // El dueño del perfil no cambia: solo la fecha de ingreso.
        $request->validate(['fecha_ingreso' => 'required|date']);

        $instructor->update(['fecha_ingreso' => $request->fecha_ingreso]);

        \App\Support\Auditoria::registrar('actualizar_instructor', 'instructors', $instructor->id, [
            'id_usuario' => $instructor->id_usuario,
            'fecha_ingreso' => $instructor->fecha_ingreso,
        ]);

        return $instructor;
    }

    public function destroy(Instructor $instructor)
    {
        // Admin sin restricciones: borra sus fichas (con su cascada) y luego el
        // perfil de instructor. La guarda de evidencia compartida (D-2b ×
        // BUG-007) responde 409 si alguna propuesta de sus fichas tiene pares.
        \Illuminate\Support\Facades\DB::transaction(function () use ($instructor) {
            \App\Support\BorradoCascada::instructor($instructor);
            \App\Support\Auditoria::registrar('eliminar_instructor', 'instructors', $instructor->id, [
                'id_usuario' => $instructor->id_usuario,
            ]);
            $instructor->delete();
        });
        return $instructor;
    }
}

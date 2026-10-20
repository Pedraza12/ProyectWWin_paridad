<?php

namespace App\Http\Controllers\Api;

use App\Models\Apprentice;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;

class InstructorController extends Controller
{
    public function index(Request $request)
    {
        $user = $request->user();

        // Admin ve todos; instructor su propia fila; aprendiz los de su ficha.
        if (optional($user)->rol === 'admin') {
            $resultado = Instructor::included()->get();
        } elseif ($user && $user->rol === 'instructor') {
            // Sin auto-creación: la verificación se resuelve en el registro.
            $resultado = Instructor::included()->where('id_usuario', $user->id)->get();
        } else {
            $fichaIds = Apprentice::where('id_usuario', optional($user)->id)->pluck('id_class_group')->filter();
            $instructorIds = ClassGroup::whereIn('id', $fichaIds)->pluck('id_instructor')->filter();

            $resultado = Instructor::included()->whereIn('id', $instructorIds)->get();
        }

        // El alcance de arriba decide qué instructores se ven; aquí se limita
        // la relación `classGroups` para que `included` no exponga fichas (y
        // códigos) fuera del alcance del rol.
        return $this->limitarClassGroups($resultado, $user);
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
            return $this->limitarClassGroups($item, $user);
        }
        if ($user && $user->rol === 'aprendiz') {
            $fichaIds = Apprentice::where('id_usuario', $user->id)->pluck('id_class_group')->filter();
            if (ClassGroup::whereIn('id', $fichaIds)->where('id_instructor', $item->id)->exists()) {
                return $this->limitarClassGroups($item, $user);
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

    // ---------------------------------------------------------------- Internos

    // Limita la relación `classGroups` cuando el cliente la pidió con
    // `included`. No cambia qué instructores son visibles (eso lo resuelve el
    // alcance de index/show), solo las fichas anidadas: admin todas, instructor
    // las que instrueye y aprendiz su ficha actual. Así cambiar `included` no
    // permite recuperar fichas ni códigos fuera del alcance del rol.
    private function limitarClassGroups($resultado, $usuario)
    {
        if (optional($usuario)->rol === 'admin') {
            return $resultado;
        }

        $permitidas = $this->fichasPermitidas($usuario);
        $items = $resultado instanceof Collection ? $resultado : collect([$resultado]);

        foreach ($items as $item) {
            if (!$item->relationLoaded('classGroups')) continue;

            $item->setRelation(
                'classGroups',
                $item->classGroups
                    ->filter(fn ($ficha) => in_array((int) $ficha->id, $permitidas, true))
                    ->values()
            );
        }

        return $resultado;
    }

    // Fichas que el rol puede recibir anidadas en un instructor.
    private function fichasPermitidas($usuario): array
    {
        if (!$usuario) return [];

        if ($usuario->rol === 'instructor') {
            $instructorId = Instructor::where('id_usuario', $usuario->id)->value('id');
            if (!$instructorId) return [];

            return ClassGroup::where('id_instructor', $instructorId)
                ->pluck('id')
                ->map(fn ($id) => (int) $id)
                ->all();
        }

        if ($usuario->rol === 'aprendiz') {
            return Apprentice::where('id_usuario', $usuario->id)
                ->pluck('id_class_group')
                ->filter()
                ->map(fn ($id) => (int) $id)
                ->unique()
                ->values()
                ->all();
        }

        return [];
    }
}

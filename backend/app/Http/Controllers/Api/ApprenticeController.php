<?php

namespace App\Http\Controllers\Api;

use App\Models\Apprentice;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Services\NotificacionesService;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;

class ApprenticeController extends Controller
{
    public function index(Request $request)
    {
        $user = $request->user();

        // Solo cuentan como integrantes quienes tienen rol aprendiz (una cuenta
        // que cambió de rol conserva su fila histórica pero sale del roster).
        $soloAprendices = fn ($q) => $q->whereHas('generalUser', fn ($u) => $u->where('rol', 'aprendiz'));

        // Admin ve todos; instructor los de sus fichas; aprendiz los de la suya.
        if (optional($user)->rol === 'admin') {
            return Apprentice::included()->where($soloAprendices)->get();
        }

        $fichaIds = collect();
        if ($user && $user->rol === 'instructor') {
            $instructorId = $user->instructor()->value('id');
            $fichaIds = $instructorId
                ? ClassGroup::where('id_instructor', $instructorId)->pluck('id')
                : collect();
        } elseif ($user) {
            $fichaIds = $user->apprentice()->pluck('id_class_group')->filter();
        }

        return Apprentice::included()->whereIn('id_class_group', $fichaIds)->where($soloAprendices)->get();
    }

    public function store(Request $request)
    {
        $request->validate([
            'codigo' => 'required|max:255|unique:apprentices,codigo',
            'id_class_group' => 'nullable|exists:class_groups,id',
            'id_usuario' => ['required', 'exists:general_users,id', Rule::unique('apprentices', 'id_usuario')],
            // Sin ficha no hay programa (se deriva de la ficha cuando la hay).
            'id_programa' => 'nullable|exists:training_programs,id',
        ]);

        $usuario = GeneralUser::findOrFail($request->id_usuario);
        if ($usuario->rol !== 'aprendiz') {
            return response()->json(['message' => 'El usuario no tiene rol de aprendiz.'], 422);
        }

        // Un instructor solo inscribe en sus propias fichas.
        $user = $request->user();
        if (optional($user)->rol === 'instructor') {
            if (!$request->filled('id_class_group') || !$this->puedeGestionarFicha($user, (int) $request->id_class_group)) {
                return response()->json(['message' => 'Solo puedes inscribir aprendices en tus propias fichas.'], 403);
            }
        }

        // Una ficha finalizada/anulada no admite nuevos integrantes (admin exento).
        if ($request->filled('id_class_group')) {
            $fichaDestino = ClassGroup::findOrFail($request->id_class_group);
            if ($fichaDestino->estado !== 'activo' && optional($user)->rol !== 'admin') {
                return response()->json(['message' => 'La ficha no está activa: no admite nuevos integrantes.'], 422);
            }
        }

        $data = $request->only(['codigo', 'id_class_group', 'id_usuario', 'id_programa']);
        // Invariante: el programa del aprendiz es el de su ficha (o null sin ficha).
        if ($request->filled('id_class_group')) {
            $data['id_programa'] = ClassGroup::findOrFail($request->id_class_group)->id_programa;
        } else {
            $data['id_programa'] = null;
        }

        $item = Apprentice::create($data);

        \App\Support\Auditoria::registrar('crear_aprendiz', 'apprentices', $item->id, [
            'codigo' => $item->codigo,
            'id_usuario' => $item->id_usuario,
            'id_class_group' => $item->id_class_group,
        ]);

        return $item;
    }

    public function show(Request $request, $id)
    {
        $item = Apprentice::included()->findOrFail($id);
        $user = $request->user();

        // Mismo alcance que el listado: admin todos; instructor los de sus
        // fichas; aprendiz el propio o el de un compañero de su ficha.
        if (optional($user)->rol === 'admin') {
            return $item;
        }
        if ($user && $user->rol === 'instructor') {
            if ($item->id_class_group && $this->puedeGestionarFicha($user, (int) $item->id_class_group)) {
                return $item;
            }
        }
        if ($user && $user->rol === 'aprendiz') {
            $propio = $user->apprentice()->first();
            if ($propio && ((int) $propio->id === (int) $item->id
                || ($propio->id_class_group && (int) $propio->id_class_group === (int) $item->id_class_group))) {
                return $item;
            }
        }

        return response()->json(['message' => 'No tienes acceso a este aprendiz.'], 403);
    }

    public function update(Request $request, Apprentice $apprentice)
    {
        $request->validate([
            'codigo' => 'required|max:255|unique:apprentices,codigo,' . $apprentice->id,
            'id_class_group' => 'nullable|exists:class_groups,id',
            'id_usuario' => ['required', 'exists:general_users,id', Rule::unique('apprentices', 'id_usuario')->ignore($apprentice->id)],
            // Sin ficha no hay programa (se deriva de la ficha cuando la hay).
            'id_programa' => 'nullable|exists:training_programs,id',
        ]);

        $usuario = GeneralUser::findOrFail($request->id_usuario);
        if ($usuario->rol !== 'aprendiz') {
            return response()->json(['message' => 'El usuario no tiene rol de aprendiz.'], 422);
        }

        // Un instructor solo gestiona aprendices de sus fichas y no reasigna
        // la cuenta dueña de la fila (evita suplantación entre fichas).
        $user = $request->user();
        if (optional($user)->rol !== 'admin') {
            $fichaActual = $apprentice->id_class_group;
            // Un aprendiz sin ficha no pertenece a ninguna ficha del instructor.
            if (!$fichaActual || !$this->puedeGestionarFicha($user, (int) $fichaActual)) {
                return response()->json(['message' => 'No puedes modificar aprendices fuera de tus fichas.'], 403);
            }
            if ($request->filled('id_class_group') && (int) $request->id_class_group !== (int) $fichaActual) {
                $destino = ClassGroup::findOrFail($request->id_class_group);
                if ($destino->estado !== 'activo' || !$this->puedeGestionarFicha($user, (int) $destino->id)) {
                    return response()->json(['message' => 'Solo puedes mover aprendices a tus fichas activas.'], 403);
                }
            }
            if ((int) $request->id_usuario !== (int) $apprentice->id_usuario) {
                return response()->json(['message' => 'No puedes reasignar la cuenta del aprendiz.'], 403);
            }
        }

        $data = $request->only(['codigo', 'id_class_group', 'id_usuario', 'id_programa']);
        // Invariante: el programa del aprendiz es el de su ficha (o null sin ficha).
        if ($request->filled('id_class_group')) {
            $data['id_programa'] = ClassGroup::findOrFail($request->id_class_group)->id_programa;
        } elseif ($request->has('id_class_group')) {
            $data['id_programa'] = null;
        }

        $fichaAnterior = $apprentice->id_class_group;
        $apprentice->update($data);
        $apprentice->refresh();

        // Si el staff lo sacó o lo movió de ficha, se avisa al aprendiz.
        if ($fichaAnterior && (int) $apprentice->id_class_group !== (int) $fichaAnterior) {
            $this->notificarAprendizDeFicha($apprentice, (int) $fichaAnterior);
        }

        \App\Support\Auditoria::registrar('actualizar_aprendiz', 'apprentices', $apprentice->id, [
            'de' => $fichaAnterior,
            'a' => $apprentice->id_class_group,
        ]);

        return $apprentice;
    }

    public function destroy(Apprentice $apprentice)
    {
        // Admin sin restricciones: borra el perfil (la FK arrastra sus pivotes de
        // equipo). Las propuestas del usuario se conservan (son de la cuenta).
        \App\Support\Auditoria::registrar('eliminar_aprendiz', 'apprentices', $apprentice->id, [
            'codigo' => $apprentice->codigo,
            'id_usuario' => $apprentice->id_usuario,
        ]);
        $apprentice->delete();
        return $apprentice;
    }

    // ------------------------------------------------ Mi ficha (aprendiz)

    // Previsualiza una ficha por código antes de unirse. Devuelve el estado
    // para que la interfaz explique si no acepta nuevos integrantes.
    public function fichaPorCodigo(string $codigo)
    {
        $ficha = $this->buscarPorCodigo($codigo);
        if (!$ficha) {
            return response()->json(['message' => 'No encontramos una ficha con ese código.'], 404);
        }
        return $ficha->loadCount('apprentices');
    }

    // Une al aprendiz autenticado a la ficha del código recibido. Antes puede
    // salir de su ficha actual (la interfaz lo hace en dos pasos); si aún así
    // llega con ficha, el cambio también notifica al instructor anterior.
    public function unirmeAFicha(Request $request)
    {
        $request->validate(['codigo' => 'required|string|max:255']);
        $user = $request->user();

        $ficha = $this->buscarPorCodigo($request->codigo);
        if (!$ficha) {
            return response()->json(['message' => 'No encontramos una ficha con ese código.'], 404);
        }
        if ($ficha->estado !== 'activo') {
            return response()->json(['message' => 'Esa ficha no acepta nuevos integrantes.'], 422);
        }

        $aprendiz = $this->miAprendiz($request);
        if ($aprendiz && (int) $aprendiz->id_class_group === (int) $ficha->id) {
            return response()->json(['message' => 'Ya perteneces a esta ficha.'], 422);
        }

        $fichaAnterior = ($aprendiz && $aprendiz->id_class_group)
            ? ClassGroup::with('instructor')->find($aprendiz->id_class_group)
            : null;

        // Unión atómica: cambio de ficha + notificaciones (todo o nada).
        $aprendiz = DB::transaction(function () use ($aprendiz, $ficha, $fichaAnterior, $user) {
            if ($aprendiz) {
                $aprendiz->update([
                    'id_class_group' => $ficha->id,
                    'id_programa' => $ficha->id_programa,
                ]);
            } else {
                // Un aprendiz recién registrado no tiene fila todavía: se crea aquí.
                $aprendiz = Apprentice::create([
                    'codigo' => Apprentice::codigoDisponible(),
                    'id_usuario' => $user->id,
                    'id_class_group' => $ficha->id,
                    'id_programa' => $ficha->id_programa,
                ]);
            }

            $this->notificarInstructor(
                $ficha,
                'El aprendiz ' . $this->nombreDe($user) . ' se unió a la ficha "' . $ficha->nombre . '".',
                'ficha:' . $ficha->id
            );
            if ($fichaAnterior) {
                $this->notificarInstructor(
                    $fichaAnterior,
                    'El aprendiz ' . $this->nombreDe($user) . ' salió de la ficha "' . $fichaAnterior->nombre . '".',
                    'ficha:' . $fichaAnterior->id
                );
            }

            return $aprendiz;
        });

        return $aprendiz->fresh()->load('classGroup.program', 'classGroup.instructor.generalUser');
    }

    // Deja al aprendiz autenticado sin ficha. La fila y su código se conservan.
    public function salirDeFicha(Request $request)
    {
        $aprendiz = $this->miAprendiz($request);
        if (!$aprendiz || !$aprendiz->id_class_group) {
            return response()->json(['message' => 'No perteneces a ninguna ficha.'], 422);
        }

        $ficha = ClassGroup::with('instructor')->find($aprendiz->id_class_group);
        $nombre = $this->nombreDe($request->user());

        // Salida atómica: cambio + notificación (todo o nada).
        $aprendiz = DB::transaction(function () use ($aprendiz, $ficha, $nombre) {
            // Opción A: sin ficha tampoco hay programa (el programa se deriva).
            $aprendiz->update(['id_class_group' => null, 'id_programa' => null]);

            if ($ficha) {
                $this->notificarInstructor(
                    $ficha,
                    'El aprendiz ' . $nombre . ' salió de la ficha "' . $ficha->nombre . '".',
                    'ficha:' . $ficha->id
                );
            }

            return $aprendiz;
        });

        return $aprendiz->fresh();
    }

    // ---------------------------------------------------------------- Internos

    private function miAprendiz(Request $request): ?Apprentice
    {
        return $request->user()->apprentice()->first();
    }

    // Admin cualquiera; instructor solo sus fichas (mismo criterio que fichas).
    private function puedeGestionarFicha($user, int $fichaId): bool
    {
        if (optional($user)->rol === 'admin') return true;
        if (!$user || $user->rol !== 'instructor') return false;

        $instructorId = $user->instructor()->value('id');
        return $instructorId && ClassGroup::where('id', $fichaId)
            ->where('id_instructor', $instructorId)
            ->exists();
    }

    // Busca por código sin importar mayúsculas/espacios.
    private function buscarPorCodigo(?string $codigo): ?ClassGroup
    {
        $codigo = strtolower(trim((string) $codigo));
        if ($codigo === '') return null;

        return ClassGroup::with('program', 'instructor.generalUser')
            ->whereRaw('LOWER(codigo) = ?', [$codigo])
            ->first();
    }

    private function nombreDe($user): string
    {
        return trim(($user->nombre ?? '') . ' ' . ($user->apellido ?? '')) ?: ($user->correo ?? 'Un aprendiz');
    }

    // Avisa al aprendiz de que lo sacaron o movieron de ficha (lo hizo el staff).
    private function notificarAprendizDeFicha(Apprentice $aprendiz, int $fichaAnteriorId): void
    {
        $nombreAnterior = optional(ClassGroup::find($fichaAnteriorId))->nombre ?? 'su ficha';
        $nombreNueva = optional(ClassGroup::find($aprendiz->id_class_group))->nombre;

        $titulo = $nombreNueva
            ? 'Te movieron de la ficha "' . $nombreAnterior . '" a "' . $nombreNueva . '".'
            : 'Te sacaron de la ficha "' . $nombreAnterior . '".';

        app(NotificacionesService::class)->crear($aprendiz->id_usuario, $titulo, 'sistema');
    }

    // Avisa al instructor de la ficha (si tiene uno) de un movimiento.
    private function notificarInstructor(?ClassGroup $ficha, string $titulo, string $enlace): void
    {
        app(NotificacionesService::class)->fichaMovimientoInstructor($ficha, $titulo, $enlace);
    }
}

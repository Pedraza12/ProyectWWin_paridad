<?php

namespace App\Http\Controllers\Api;

use App\Models\Apprentice;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\Project;
use App\Support\CredencialesPdf;
use App\Http\Controllers\Controller;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class ClassGroupController extends Controller
{
    // Alcance por rol: admin todas; instructor SOLO las que creó/gestiona;
    // aprendiz SOLO su ficha actual. Evita exponer los códigos de todas las
    // fichas (el código es la credencial para unirse).
    public function index(Request $request)
    {
        $request->validate([
            'buscar' => 'nullable|string|max:100',
            'campo' => 'nullable|in:codigo,nombre,numero,todos',
        ]);

        $user = $request->user();
        $query = ClassGroup::included();

        if (optional($user)->rol === 'admin') {
            // Todas.
        } elseif ($user && $user->rol === 'instructor') {
            $instructorId = $user->instructor()->value('id');
            $query->where('id_instructor', $instructorId ?: 0);
        } else {
            $fichaId = Apprentice::where('id_usuario', optional($user)->id)->value('id_class_group');
            $query->where('id', $fichaId ?: 0);
        }

        // Búsqueda server-side por código, nombre o número de ficha (así el
        // cliente no filtra sobre una lista ya cargada). Insensible a
        // mayúsculas según la colación de la base.
        $buscar = trim((string) $request->query('buscar'));
        if ($buscar !== '') {
            $campo = $request->query('campo', 'todos');
            $t = '%' . $buscar . '%';
            $query->where(function (Builder $q) use ($campo, $t) {
                if ($campo === 'codigo') {
                    $q->where('codigo', 'like', $t);
                } elseif ($campo === 'nombre') {
                    $q->where('nombre', 'like', $t);
                } elseif ($campo === 'numero') {
                    $q->where('numero', 'like', $t);
                } else {
                    $q->where('codigo', 'like', $t)
                        ->orWhere('nombre', 'like', $t)
                        ->orWhere('numero', 'like', $t);
                }
            });
        }

        return $query->orderByDesc('id')->get();
    }

    public function store(Request $request)
    {
        $user = $request->user();

        // Solo un instructor con perfil crea fichas (el admin puede cualquiera).
        $mio = null;
        if (optional($user)->rol === 'instructor') {
            $mio = $this->miFilaInstructor($request);
            if (!$mio) {
                return response()->json(['message' => 'Tu cuenta no tiene perfil de instructor.'], 422);
            }
        }

        $request->validate([
            'numero' => 'required|max:255|unique:class_groups,numero',
            'nombre' => 'required|max:255',
            'estado' => 'required|in:activo,finalizado',
            'id_programa' => 'required|exists:training_programs,id',
            'id_instructor' => 'required|exists:instructors,id',
            'aprendices' => 'nullable|array',
            'aprendices.*' => 'integer|exists:general_users,id',
        ]);

        // Whitelist: `aprendices` se sincroniza aparte (relación aprendiz-ficha).
        $datos = $request->only(['numero', 'nombre', 'estado', 'id_programa', 'id_instructor']);
        // El código de unión lo genera el servidor: el cliente no lo elige.
        $datos['codigo'] = ClassGroup::codigoDisponible();
        // Un instructor solo crea fichas a su nombre (el admin asigna a quien sea).
        if ($mio) {
            $datos['id_instructor'] = $mio->id;
        }

        // Un instructor no puede llevarse aprendices de fichas ajenas.
        $fuera = $this->aprendizDeOtraFicha($request, $request->input('aprendices', []));
        if ($fuera) {
            return response()->json([
                'message' => 'El aprendiz ' . $fuera->nombre . ' ' . $fuera->apellido
                    . ' pertenece a otra ficha. Solo un administrador puede trasladarlo.',
            ], 422);
        }

        // Alta atómica: ficha + aprendices seleccionados + auditoría.
        $item = DB::transaction(function () use ($datos, $request) {
            $item = ClassGroup::create($datos);
            $this->sincronizarAprendices($item, $request->input('aprendices', []));
            \App\Support\Auditoria::registrar('crear_ficha', 'class_groups', $item->id, [
                'codigo' => $item->codigo,
                'numero' => $item->numero,
                'id_instructor' => $item->id_instructor,
            ]);
            return $item;
        });

        return response()->json($item, 201);
    }

    public function show(Request $request, $id)
    {
        $ficha = ClassGroup::included()->findOrFail($id);
        $user = $request->user();

        // Mismo alcance que el listado: admin todas; instructor las suyas;
        // aprendiz solo su ficha actual (el código es credencial de unión).
        if (optional($user)->rol === 'admin') {
            return $ficha;
        }
        if ($user && $user->rol === 'instructor') {
            $instructor = $this->miFilaInstructor($request);
            if ($instructor && (int) $ficha->id_instructor === (int) $instructor->id) {
                return $ficha;
            }
        }
        if ($user && $user->rol === 'aprendiz') {
            $esSuFicha = Apprentice::where('id_usuario', $user->id)
                ->where('id_class_group', $ficha->id)
                ->exists();
            if ($esSuFicha) {
                return $ficha;
            }
        }

        return response()->json(['message' => 'No tienes acceso a esta ficha.'], 403);
    }

    // Alta/asociación atómica de un aprendiz a la ficha: si el usuario ya
    // existe solo se asocia (sin credenciales nuevas); si no, se crea la cuenta
    // con username + temporal, se asocia y se envían las credenciales.
    public function agregarAprendiz(Request $request, ClassGroup $class_group)
    {
        if (!$this->puedeGestionar($request, $class_group)) {
            return response()->json(['message' => 'No puedes agregar aprendices a esta ficha.'], 403);
        }
        if ($class_group->estado !== 'activo') {
            return response()->json(['message' => 'La ficha no está activa.'], 422);
        }

        $request->validate([
            'usuario_id' => 'nullable|integer|exists:general_users,id',
            'nombre' => 'required_without:usuario_id|nullable|string|max:255',
            'apellido' => 'required_without:usuario_id|nullable|string|max:255',
            'tipo_documento' => 'required_without:usuario_id|nullable|in:CC,TI,CE,PPT',
            'numero_documento' => 'required_without:usuario_id|nullable|string|max:40|unique:general_users,numero_documento',
            'correo' => 'required_without:usuario_id|nullable|email|unique:general_users,correo',
        ]);

        // Usuario existente: solo se asocia a la ficha (sin nuevas credenciales).
        if ($request->filled('usuario_id')) {
            $usuario = GeneralUser::findOrFail($request->usuario_id);
            if ($usuario->rol !== 'aprendiz') {
                return response()->json(['message' => 'Solo se pueden asociar usuarios con rol aprendiz.'], 422);
            }

            // No se traslada a un aprendiz de una ficha ajena (admin exento).
            $aprendizExistente = Apprentice::where('id_usuario', $usuario->id)->first();
            if ($aprendizExistente && $aprendizExistente->id_class_group
                && (int) $aprendizExistente->id_class_group !== (int) $class_group->id
                && optional($request->user())->rol !== 'admin') {
                $origen = ClassGroup::find($aprendizExistente->id_class_group);
                $miInstructor = $request->user()->instructor()->first();
                $esSuya = $origen && $miInstructor && (int) $origen->id_instructor === (int) $miInstructor->id;
                if (!$esSuya) {
                    return response()->json([
                        'message' => 'El aprendiz pertenece a otra ficha. Solo un administrador puede trasladarlo.',
                    ], 422);
                }
            }

            $aprendiz = $this->asociarAprendiz($class_group, $usuario);
            \App\Support\Auditoria::registrar('agregar_aprendiz', 'class_groups', $class_group->id, [
                'usuario' => $usuario->id,
                'creada' => false,
            ]);

            return response()->json([
                'usuario' => $usuario,
                'aprendiz' => $aprendiz,
                'creada' => false,
                'credenciales_enviadas' => false,
            ]);
        }

        // Usuario nuevo: cuenta + credenciales + asociación atómicas (todo o
        // nada). El correo se envía después de confirmar la transacción.
        [$alta, $aprendiz] = DB::transaction(function () use ($request, $class_group) {
            $alta = app(\App\Services\AltaUsuario::class)->crear([
                'nombre' => $request->nombre,
                'apellido' => $request->apellido,
                'tipo_documento' => $request->tipo_documento,
                'numero_documento' => $request->numero_documento,
                'correo' => $request->correo,
                'rol' => 'aprendiz',
            ], false);

            return [$alta, $this->asociarAprendiz($class_group, $alta['usuario'])];
        });

        $enviadas = app(\App\Services\CredencialesCorreo::class)->enviar($alta['usuario'], $alta['temporal']);

        \App\Support\Auditoria::registrar('agregar_aprendiz', 'class_groups', $class_group->id, [
            'usuario' => $alta['usuario']->id,
            'creada' => true,
            'credenciales_enviadas' => $enviadas,
        ]);

        return response()->json([
            'usuario' => $alta['usuario']->fresh(),
            'aprendiz' => $aprendiz,
            'creada' => true,
            'credenciales_enviadas' => $enviadas,
            'credenciales' => [
                'username' => $alta['usuario']->username,
                'password_temporal' => $alta['temporal'],
                'enviadas' => $enviadas,
            ],
        ], 201);
    }

    // Exporta en PDF las credenciales iniciales de la ficha (aprendices +
    // instructor responsable). Solo admin; queda en la bitácora.
    public function credenciales(ClassGroup $class_group)
    {
        $ids = Apprentice::where('id_class_group', $class_group->id)->pluck('id_usuario')->all();
        if ($class_group->instructor) {
            $ids[] = $class_group->instructor->id_usuario;
        }

        $usuarios = GeneralUser::with('apprentice.classGroup.program', 'instructor.classGroups.program')
            ->whereIn('id', $ids)
            ->orderBy('nombre')
            ->get();

        \App\Support\Auditoria::registrar('exportar_credenciales', 'class_groups', $class_group->id, [
            'usuarios' => $usuarios->count(),
            'temporales' => $usuarios->where('must_change_password', true)->count(),
        ]);

        return CredencialesPdf::generar($usuarios, [
            'ficha' => $class_group->numero,
            'programa' => optional($class_group->program)->nombre,
        ]);
    }

    public function update(Request $request, ClassGroup $class_group)
    {
        if (!$this->puedeGestionar($request, $class_group)) {
            return response()->json(['message' => 'No puedes editar una ficha que no está a tu cargo.'], 403);
        }

        // Una ficha anulada solo la interviene un administrador.
        if ($class_group->estado === 'anulada' && optional($request->user())->rol !== 'admin') {
            return response()->json(['message' => 'Una ficha anulada solo la puede modificar un administrador.'], 403);
        }

        $request->validate([
            'numero' => 'required|max:255|unique:class_groups,numero,' . $class_group->id,
            'nombre' => 'required|max:255',
            'estado' => 'required|in:activo,finalizado,anulada',
            'id_programa' => 'required|exists:training_programs,id',
            'id_instructor' => 'required|exists:instructors,id',
            'aprendices' => 'nullable|array',
            'aprendices.*' => 'integer|exists:general_users,id',
        ]);

        // Whitelist: el código de unión es inmutable y `aprendices` se
        // sincroniza aparte (relación aprendiz-ficha).
        $datos = $request->only(['numero', 'nombre', 'estado', 'id_programa', 'id_instructor']);
        // Un instructor no puede reasignar su propia ficha a otro (eso es del admin).
        if ($request->user()->rol === 'instructor') {
            $datos['id_instructor'] = $class_group->id_instructor;
        }

        // Ficha finalizada: el roster queda en solo lectura (admin exento).
        if ($class_group->estado === 'finalizado' && $request->has('aprendices')
            && optional($request->user())->rol !== 'admin') {
            return response()->json([
                'message' => 'La ficha está finalizada: su roster no se puede modificar.',
            ], 422);
        }

        // Un instructor no puede llevarse aprendices de fichas ajenas.
        $fuera = $this->aprendizDeOtraFicha($request, $request->input('aprendices', []));
        if ($fuera) {
            return response()->json([
                'message' => 'El aprendiz ' . $fuera->nombre . ' ' . $fuera->apellido
                    . ' pertenece a otra ficha. Solo un administrador puede trasladarlo.',
            ], 422);
        }

        $programaAnterior = $class_group->id_programa;
        $estadoAnterior = $class_group->estado;
        $numeroAnterior = $class_group->numero;
        $nombreAnterior = $class_group->nombre;
        $instructorAnterior = $class_group->id_instructor;

        // Reasignación de responsable: acción administrativa que debe quedar
        // trazada con instructor anterior y nuevo.
        $cambiaInstructor = array_key_exists('id_instructor', $datos)
            && (int) $datos['id_instructor'] !== (int) $instructorAnterior;

        // Anular libera el número: la ficha conserva su historial, el número no.
        $seAnula = $datos['estado'] === 'anulada' && $estadoAnterior !== 'anulada';
        if ($seAnula) {
            $datos['numero'] = null;
        }

        // Cambio de ficha atómico: ficha + aprendices + recálculo (todo o nada).
        DB::transaction(function () use ($class_group, $datos, $request, $programaAnterior, $estadoAnterior, $numeroAnterior, $nombreAnterior, $instructorAnterior, $cambiaInstructor, $seAnula) {
            // O1 (D-2b × BUG-007): el mutex del motor se toma al inicio de la
            // transacción —antes de bloquear ficha/aprendices—; el recálculo por
            // cambio de programa lo reutiliza. Evita el ciclo de bloqueos con las
            // cascadas de BorradoCascada (que también empiezan por el mutex).
            $config = \App\Models\MotorConfig::firstOrCreate([], ['umbral' => 0.30, 'meses' => 12]);
            \App\Models\MotorConfig::whereKey($config->id)->lockForUpdate()->first();

            $class_group->update($datos);

            // Aprendices seleccionados en el formulario (si vino el campo).
            if ($request->has('aprendices')) {
                $this->sincronizarAprendices($class_group, $request->input('aprendices', []));
            }

            // Si cambió el programa de la ficha, se sincroniza el programa de sus
            // aprendices y se recalcula el corpus (los pares exigen mismo programa).
            if ((int) $programaAnterior !== (int) $class_group->id_programa) {
                \App\Models\Apprentice::where('id_class_group', $class_group->id)
                    ->update(['id_programa' => $class_group->id_programa]);
                app(\App\Similarity\Recomputador::class)->recalcular(true, true);
            }

            if ($estadoAnterior !== 'finalizado' && $class_group->estado === 'finalizado') {
                \App\Support\Auditoria::registrar('finalizar_ficha', 'class_groups', $class_group->id, [
                    'codigo' => $class_group->codigo,
                ]);
            }

            if ($seAnula) {
                \App\Support\Auditoria::registrar('anular_ficha', 'class_groups', $class_group->id, [
                    'codigo' => $class_group->codigo,
                    'numero_anterior' => $numeroAnterior,
                ]);
            }

            if ($cambiaInstructor) {
                \App\Support\Auditoria::registrar('reasignar_ficha', 'class_groups', $class_group->id, [
                    'codigo' => $class_group->codigo,
                    'de' => $instructorAnterior,
                    'a' => $class_group->id_instructor,
                ]);
            }

            // Modificaciones administrativas de la ficha (antes → después).
            $cambios = array_filter([
                'nombre' => $class_group->nombre !== $nombreAnterior
                    ? ['de' => $nombreAnterior, 'a' => $class_group->nombre] : null,
                'numero' => !$seAnula && $numeroAnterior !== $class_group->numero
                    ? ['de' => $numeroAnterior, 'a' => $class_group->numero] : null,
                'programa' => (int) $programaAnterior !== (int) $class_group->id_programa
                    ? ['de' => $programaAnterior, 'a' => $class_group->id_programa] : null,
            ]);
            if ($cambios) {
                \App\Support\Auditoria::registrar('actualizar_ficha', 'class_groups', $class_group->id, [
                    'codigo' => $class_group->codigo,
                    'cambios' => $cambios,
                ]);
            }
        });

        return $class_group;
    }

    public function destroy(Request $request, ClassGroup $class_group)
    {
        if (!$this->puedeGestionar($request, $class_group)) {
            return response()->json(['message' => 'No puedes eliminar una ficha que no está a tu cargo.'], 403);
        }

        $impacto = [
            'aprendices' => $class_group->apprentices()->count(),
            'propuestas' => Project::where('id_class_group', $class_group->id)->count(),
        ];

        // Con información asociada no se borra físicamente: se anula y se libera
        // el número para que otra ficha pueda usarlo (el historial se conserva).
        if ($impacto['aprendices'] > 0 || $impacto['propuestas'] > 0) {
            $numeroAnterior = $class_group->numero;
            $class_group->update(['estado' => 'anulada', 'numero' => null]);

            \App\Support\Auditoria::registrar('anular_ficha', 'class_groups', $class_group->id, [
                'codigo' => $class_group->codigo,
                'nombre' => $class_group->nombre,
                'numero_anterior' => $numeroAnterior,
                'impacto' => $impacto,
            ]);

            return response()->json(['accion' => 'anulada', 'ficha' => $class_group->fresh()]);
        }

        // Sin información: se borra de verdad y el número queda disponible.
        $numeroAnterior = $class_group->numero;
        \App\Support\BorradoCascada::ficha($class_group);

        \App\Support\Auditoria::registrar('eliminar_ficha', 'class_groups', $class_group->id, [
            'codigo' => $class_group->codigo,
            'nombre' => $class_group->nombre,
            'numero' => $numeroAnterior,
        ]);

        return response()->json(['accion' => 'eliminada', 'ficha' => $class_group]);
    }

    // Admin cualquiera; instructor solo sus fichas.
    private function puedeGestionar(Request $request, ClassGroup $ficha): bool
    {
        $user = $request->user();
        if (!$user) return false;
        if ($user->rol === 'admin') return true;
        if ($user->rol !== 'instructor') return false;

        $instructor = $this->miFilaInstructor($request);
        return $instructor && (int) $ficha->id_instructor === (int) $instructor->id;
    }

    // Perfil de instructor del usuario autenticado (null si no existe). No se
    // auto-crea: la verificación se resuelve en el registro, no al usar la API.
    private function miFilaInstructor(Request $request): ?Instructor
    {
        return $request->user()->instructor()->first();
    }

    // Aprendiz seleccionado que pertenece a una ficha ajena (bloquea a
    // instructores; el admin puede trasladar). Ignora ids inválidos como el
    // flujo actual (solo se asocian usuarios con rol aprendiz).
    private function aprendizDeOtraFicha(Request $request, array $ids): ?GeneralUser
    {
        $user = $request->user();
        if (!$user || $user->rol === 'admin') return null;
        $miInstructor = $user->rol === 'instructor' ? $user->instructor()->first() : null;

        foreach (collect($ids)->map(fn ($id) => (int) $id)->filter()->unique() as $idUsuario) {
            $usuario = GeneralUser::find($idUsuario);
            if (!$usuario || $usuario->rol !== 'aprendiz') continue;

            $aprendiz = Apprentice::where('id_usuario', $usuario->id)->first();
            if (!$aprendiz || !$aprendiz->id_class_group) continue;

            $origen = ClassGroup::find($aprendiz->id_class_group);
            $esSuya = $origen && $miInstructor && (int) $origen->id_instructor === (int) $miInstructor->id;
            if (!$esSuya) return $usuario;
        }

        return null;
    }

    // Sincroniza la pertenencia de los aprendices a la ficha: los seleccionados
    // quedan en la ficha (se crea su fila si no existía) y los no seleccionados
    // salen conservando su historial (fila con ficha/programa en null).
    private function sincronizarAprendices(ClassGroup $ficha, array $ids): void
    {
        $seleccionados = collect($ids)->map(fn ($id) => (int) $id)->filter()->unique()->values();

        // Retira a quienes ya no están en la selección.
        Apprentice::where('id_class_group', $ficha->id)
            ->when($seleccionados->isNotEmpty(), fn ($q) => $q->whereNotIn('id_usuario', $seleccionados))
            ->update(['id_class_group' => null, 'id_programa' => null]);

        // Agrega/actualiza a los seleccionados (solo usuarios con rol aprendiz).
        foreach ($seleccionados as $idUsuario) {
            $usuario = GeneralUser::find($idUsuario);
            if (!$usuario || $usuario->rol !== 'aprendiz') continue;
            $this->asociarAprendiz($ficha, $usuario);
        }

        if ($seleccionados->isNotEmpty()) {
            \App\Support\Auditoria::registrar('asignar_aprendices', 'class_groups', $ficha->id, [
                'aprendices' => $seleccionados->count(),
            ]);
        }
    }

    // Asocia (o reasocia) a un aprendiz con la ficha: crea su fila si no existía.
    private function asociarAprendiz(ClassGroup $ficha, GeneralUser $usuario): Apprentice
    {
        $aprendiz = Apprentice::firstOrCreate(
            ['id_usuario' => $usuario->id],
            [
                'codigo' => Apprentice::codigoDisponible(),
                'id_class_group' => $ficha->id,
                'id_programa' => $ficha->id_programa,
            ]
        );
        $aprendiz->update([
            'id_class_group' => $ficha->id,
            'id_programa' => $ficha->id_programa,
        ]);

        return $aprendiz;
    }
}

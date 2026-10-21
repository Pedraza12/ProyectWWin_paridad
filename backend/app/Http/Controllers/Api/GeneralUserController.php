<?php

namespace App\Http\Controllers\Api;

use App\Models\GeneralUser;
use App\Models\Admin;
use App\Models\ApprenticeProject;
use App\Models\ClassGroup;
use App\Models\Instructor;
use App\Models\Project;
use App\Support\Auditoria;
use App\Support\BorradoCascada;
use App\Support\Credenciales;
use App\Support\CredencialesPdf;
use App\Services\AltaUsuario;
use App\Services\CredencialesCorreo;
use App\Services\NotificacionesService;
use App\Support\Pii;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;

class GeneralUserController extends Controller
{
    // Campos institucionales/credenciales que solo revela la administración
    // (el modelo los oculta por defecto para el resto de la API).
    private const CAMPOS_CREDENCIALES = [
        'tipo_documento', 'numero_documento', 'username',
        'must_change_password', 'credenciales_enviadas_en', 'credenciales_error',
    ];

    // Proyección opt-in para selectores que solo necesitan identificar al
    // usuario (p. ej. el filtro de la bitácora). Evita enviar correo y la foto
    // en base64 de toda la plantilla. Los contratos actuales no cambian: solo
    // se activa con `resumen=1`.
    private const CAMPOS_RESUMEN = ['id', 'nombre', 'apellido', 'rol'];

    // La foto de perfil viaja como data URL de imagen generada por los
    // clientes (web y Android recortan a 256×256 JPEG). Se rechazan URLs
    // externas, MIME que no sea de imagen y payloads fuera de la columna TEXT
    // (65.535 bytes). El tope de 65.000 caracteres es seguro para la columna
    // (ASCII = 1 byte), cubre el peor caso local con ruido del JPEG 256×256
    // q0.82 (≈60.600 caracteres) y deja que Android mantenga su autocapa de
    // 60.000. Así ningún flujo actual queda rechazado por el límite.
    private const MAX_FOTO_URL = 65000;

    private const REGEX_FOTO_URL = '/\Adata:image\/(?:png|jpe?g|webp|gif);base64,[A-Za-z0-9+\/]+={0,2}\z/';

    // Reglas en arreglo (no en cadena) porque el regex contiene `|` y el
    // parser de reglas de Laravel dividiría la expresión por ese carácter.
    private const REGLAS_FOTO_URL = [
        'nullable',
        'string',
        'max:' . self::MAX_FOTO_URL,
        'regex:' . self::REGEX_FOTO_URL,
    ];

    public function index(Request $request)
    {
        // Params inválidos (p. ej. arrays) se rechazan con 422, nunca 500.
        $request->validate([
            'search' => 'nullable|string',
            'role' => 'nullable|string',
            'estado' => 'nullable|string',
            'ficha_id' => 'nullable|string',
            'programa' => 'nullable|string',
            'resumen' => 'nullable|boolean',
        ]);

        $query = GeneralUser::included()
            ->search($request->query('search'))
            ->byRol($request->query('role'))
            ->byEstado($request->query('estado'))
            ->byFicha($request->query('ficha_id'))
            ->byPrograma($request->query('programa'));

        // Proyección ligera opt-in (compatible: sin el parámetro todo sigue igual).
        $resumen = $request->boolean('resumen');

        // Paginación opt-in para el panel de administración (miles de filas).
        if ($request->boolean('paginado')) {
            $porPagina = max(1, min((int) $request->query('por_pagina', 10), 100));
            $query->orderBy('id');
            if ($resumen) {
                $query->select(self::CAMPOS_RESUMEN);
            }
            $pagina = $query->paginate($porPagina);
            if (!$resumen) {
                $pagina->getCollection()->each->makeVisible(self::CAMPOS_CREDENCIALES);
            }
            return $pagina;
        }

        $query->orderBy('id');
        if ($resumen) {
            return $query->get(self::CAMPOS_RESUMEN);
        }

        return $query->get()->each->makeVisible(self::CAMPOS_CREDENCIALES);
    }

    // Alta exclusiva del administrador (no hay registro público): las
    // credenciales se generan solas y el usuario cambia la temporal al entrar.
    public function store(Request $request)
    {
        $request->validate([
            'nombre' => 'required|max:255',
            'apellido' => 'required|max:255',
            'tipo_documento' => 'required|in:CC,TI,CE,PPT',
            'numero_documento' => 'required|max:40|unique:general_users,numero_documento',
            'correo' => 'required|email|unique:general_users,correo',
            'foto_url' => self::REGLAS_FOTO_URL,
            'rol' => 'required|in:aprendiz,instructor',
            'estado' => 'nullable|boolean',
        ]);

        // Alta compartida (cuenta + perfil + auditoría + correo de credenciales).
        // Whitelist: los campos internos de credenciales no se aceptan del cliente.
        $alta = app(AltaUsuario::class)->crear($request->only([
            'nombre', 'apellido', 'tipo_documento', 'numero_documento',
            'correo', 'foto_url', 'rol', 'estado',
        ]));

        return response()->json([
            'usuario' => $alta['usuario']->makeVisible(self::CAMPOS_CREDENCIALES),
            'credenciales' => [
                'username' => $alta['usuario']->username,
                'password_temporal' => $alta['temporal'],
                'enviadas' => $alta['enviadas'],
            ],
        ], 201);
    }

    // Reenvía las credenciales temporales (solo admin y solo mientras la
    // contraseña siga siendo temporal; después nunca se revela la actual).
    public function reenviarCredenciales(Request $request, GeneralUser $general_user)
    {
        if ($general_user->rol === 'admin') {
            return response()->json(['message' => 'Los administradores no usan credenciales temporales.'], 422);
        }
        if (!$general_user->must_change_password || !$general_user->password_temporal) {
            return response()->json([
                'message' => 'La contraseña ya fue cambiada: no se pueden reenviar credenciales.',
            ], 422);
        }

        try {
            $temporal = Crypt::decryptString($general_user->password_temporal);
        } catch (\Throwable $e) {
            return response()->json(['message' => 'No se pudo recuperar la contraseña temporal.'], 422);
        }

        $ok = app(CredencialesCorreo::class)->enviar($general_user, $temporal);

        Auditoria::registrar('reenviar_credenciales', 'general_users', $general_user->id, [
            'correo' => $general_user->correo,
            'enviado' => $ok,
        ]);

        if (!$ok) {
            return response()->json(['message' => 'No se pudo enviar el correo. Intenta de nuevo.'], 502);
        }

        return $general_user->fresh()->makeVisible(self::CAMPOS_CREDENCIALES);
    }

    // Restablecimiento del admin: la temporal la genera el servidor (misma
    // fuente y reglas que el alta) y obliga al cambio en el primer ingreso.
    public function restablecerCredenciales(Request $request, GeneralUser $general_user)
    {
        if ($general_user->rol === 'admin') {
            return response()->json(['message' => 'Los administradores no usan credenciales temporales.'], 422);
        }

        $temporal = Credenciales::passwordTemporal();

        $general_user->update([
            'password' => Hash::make($temporal),
            'must_change_password' => true,
            'password_temporal' => Crypt::encryptString($temporal),
            // La temporal nueva aún no se envía: se limpia el estado del correo.
            'credenciales_enviadas_en' => null,
            'credenciales_error' => null,
        ]);

        Auditoria::registrar('restablecer_credenciales', 'general_users', $general_user->id, [
            'correo' => $general_user->correo,
        ]);

        return response()->json([
            'usuario' => $general_user->fresh()->makeVisible(self::CAMPOS_CREDENCIALES),
            'credenciales' => [
                'username' => $general_user->username,
                'password_temporal' => $temporal,
                'enviadas' => false,
            ],
        ]);
    }

    public function show(Request $request, $id)
    {
        $user = $request->user();

        // El detalle completo es del propio usuario o de un admin.
        if ((int) $user->id !== (int) $id && $user->rol !== 'admin') {
            return response()->json(['message' => 'Solo puedes ver tu propia cuenta.'], 403);
        }

        return GeneralUser::included()->findOrFail($id)->makeVisible(self::CAMPOS_CREDENCIALES);
    }

    // Perfil público (vistas entre usuarios: compañero, instructor). Expone solo
    // datos de contacto básicos; nunca estado ni información de gestión. El
    // alcance es por relación académica (misma ficha / instructor de la ficha /
    // aprendiz de las fichas propias); el admin ve cualquiera.
    public function perfil(Request $request, $id)
    {
        $yo = $request->user();
        $user = GeneralUser::findOrFail($id);

        if (!$this->puedeVerPerfil($yo, $user)) {
            return response()->json(['message' => 'No tienes acceso a este perfil.'], 403);
        }

        return response()->json([
            'id' => $user->id,
            'nombre' => $user->nombre,
            'apellido' => $user->apellido,
            'correo' => $user->correo,
            'foto_url' => $user->foto_url,
            'rol' => $user->rol,
        ]);
    }

    public function update(Request $request, GeneralUser $general_user)
    {
        // El documento solo lo corrige un administrador. Se rechaza antes de
        // validar para que una petición manual reciba siempre 403 y no pueda
        // usar el error de unicidad como sondeo.
        $intentaDocumento = $request->has('tipo_documento') || $request->has('numero_documento');
        if ($intentaDocumento && optional($request->user())->rol !== 'admin') {
            return response()->json([
                'message' => 'Solo un administrador puede modificar el documento de identidad.',
            ], 403);
        }

        // Normaliza el correo antes de validar: la unicidad y la comparación
        // con el valor actual deben usar siempre la misma representación.
        if ($request->has('correo') && is_string($request->input('correo'))) {
            $request->merge(['correo' => strtolower(trim($request->input('correo')))]);
        }

        // Actualización parcial: los campos solo se validan si vienen. Así se
        // puede guardar SOLO la foto (foto_url) sin exigir el resto.
        $request->validate([
            'nombre' => 'sometimes|required|max:255',
            'apellido' => 'sometimes|required|max:255',
            'correo' => 'sometimes|required|email|unique:general_users,correo,' . $general_user->id,
            'foto_url' => self::REGLAS_FOTO_URL,
            'rol' => 'sometimes|required|in:aprendiz,instructor,admin',
            'estado' => 'sometimes|boolean',
            // Documento de identidad: el admin puede corregirlo (p. ej. un
            // número mal digitado en el alta). El propio usuario no lo edita.
            'tipo_documento' => 'sometimes|required|in:CC,TI,CE,PPT',
            'numero_documento' => 'sometimes|required|max:40|unique:general_users,numero_documento,' . $general_user->id,
        ]);

        // Autorización: la propia cuenta, o cualquier cuenta si eres admin.
        $yo = $request->user();
        $esPropiaCuenta = $yo && (int) $yo->id === (int) $general_user->id;
        $esAdmin = optional($yo)->rol === 'admin';
        if (!$esPropiaCuenta && !$esAdmin) {
            return response()->json(['message' => 'No puedes modificar los datos de otro usuario.'], 403);
        }

        // Cambios de rol o estado exigen admin; nadie se auto-eleva.
        $cambiaRol = $request->has('rol') && $request->rol !== $general_user->rol;
        $cambiaEstado = $request->has('estado') && (bool) $request->estado !== (bool) $general_user->estado;
        if (($cambiaRol || $cambiaEstado) && !$esAdmin) {
            return response()->json(['message' => 'Solo un administrador puede cambiar rol o estado.'], 403);
        }
        // El propio correo (identificador de acceso) solo se cambia con la
        // contraseña actual: PUT /auth/email. Un admin sí puede corregir el
        // correo de OTRO usuario desde su detalle.
        $correoAnterior = strtolower(trim((string) $general_user->correo));
        $cambiaCorreo = $request->has('correo') && $request->input('correo') !== $correoAnterior;
        if ($cambiaCorreo && $esPropiaCuenta) {
            return response()->json([
                'message' => 'Usa "Cambiar correo" e ingresa tu contraseña actual para modificar tu correo.',
            ], 422);
        }
        // Nadie puede quitarse a sí mismo el rol de admin.
        if ($cambiaRol && $yo && (int) $yo->id === (int) $general_user->id && $general_user->rol === 'admin') {
            return response()->json(['message' => 'No puedes quitarte tu propio rol de administrador.'], 422);
        }
        // Tampoco se puede suspender/degradar al último administrador activo.
        $dejaDeSerAdminActivo = $general_user->rol === 'admin' && $general_user->estado
            && (($cambiaRol && $request->rol !== 'admin') || ($cambiaEstado && !$request->boolean('estado')));
        if ($dejaDeSerAdminActivo) {
            $activos = GeneralUser::where('rol', 'admin')->where('estado', true)->count();
            if ($activos <= 1) {
                return response()->json([
                    'message' => 'No puedes dejar el sistema sin administradores activos.',
                ], 409);
            }
        }
        // Un admin no cambia su propio estado (ni se activa ni se desactiva
        // solo): la regla vive en el backend, sin depender del cliente (D-4).
        if ($cambiaEstado && $esPropiaCuenta && $esAdmin) {
            return response()->json(['message' => 'No puedes cambiar tu propio estado.'], 422);
        }

        // Un instructor con fichas a cargo no puede dejar de ser instructor: las
        // fichas quedarían sin responsable. Se exige reasignarlas primero.
        if ($cambiaRol && $general_user->rol === 'instructor' && $request->rol !== 'instructor') {
            $instructor = $general_user->instructor;
            $fichas = $instructor ? ClassGroup::where('id_instructor', $instructor->id)->count() : 0;
            if ($fichas > 0) {
                return response()->json([
                    'message' => "No se puede cambiar el rol: tiene {$fichas} ficha(s) a cargo. Reasígnalas primero.",
                ], 409);
            }
        }

        // Whitelist: solo campos editables por esta vía. El admin también puede
        // corregir el documento de identidad; los campos internos del flujo de
        // credenciales (username, must_change_password, password_temporal,
        // credenciales_*) nunca se aceptan desde el request.
        $data = $esAdmin
            ? $request->only([
                'nombre', 'apellido', 'correo', 'foto_url', 'rol', 'estado',
                'tipo_documento', 'numero_documento',
            ])
            : $request->only(['nombre', 'apellido', 'correo', 'foto_url']);

        // Las contraseñas no se cambian por esta vía: la propia exige la actual
        // (PUT /auth/password) y la temporal la genera el servidor.
        if ($request->filled('password')) {
            return response()->json([
                'message' => $esAdmin
                    ? 'Usa "Restablecer contraseña" para generar una temporal.'
                    : 'Usa "Cambiar contraseña" e ingresa tu contraseña actual para modificarla.',
            ], 422);
        }

        $cambiaNombre = $request->filled('nombre') && $request->nombre !== $general_user->nombre;
        $cambiaDocumento = ($request->filled('tipo_documento') && $request->tipo_documento !== $general_user->tipo_documento)
            || ($request->filled('numero_documento') && $request->numero_documento !== $general_user->numero_documento);
        $documentoAnterior = Pii::documento($general_user->tipo_documento, $general_user->numero_documento);

        // Actualización atómica: cuenta + perfiles + auditoría (todo o nada).
        DB::transaction(function () use ($general_user, $data, $cambiaRol, $cambiaEstado, $cambiaCorreo, $cambiaNombre, $cambiaDocumento, $documentoAnterior) {
            $general_user->update($data);
            $documentoNuevo = $cambiaDocumento
                ? Pii::documento($general_user->tipo_documento, $general_user->numero_documento)
                : null;

            Auditoria::registrar('actualizar_usuario', 'general_users', $general_user->id, array_filter([
                'rol' => $cambiaRol ? $general_user->rol : null,
                'estado' => $cambiaEstado ? (bool) $general_user->estado : null,
                'correo' => $cambiaCorreo ? $general_user->correo : null,
                'nombre' => $cambiaNombre ? $general_user->nombre : null,
                // La bitácora no guarda el documento completo. Se conserva el
                // valor anterior y el nuevo enmascarados para trazabilidad.
                'documento' => $documentoNuevo,
                'documento_anterior' => $cambiaDocumento ? $documentoAnterior : null,
                'documento_nuevo' => $documentoNuevo,
            // Solo se descartan los nulos: `estado=false` es un cambio real y
            // debe quedar registrado (el array_filter por defecto lo perdería).
            ], fn ($v) => $v !== null));

            // Mantiene coherentes los perfiles (admin/instructor/aprendiz) al cambio de rol.
            if ($cambiaRol) {
                $this->sincronizarPerfiles($general_user);
                $this->limpiarPerfilesObsoletos($general_user);
            }
        });

        // Aviso al usuario afectado solo si el correo cambió realmente y el
        // cambio lo hizo un administrador sobre otra cuenta.
        if ($cambiaCorreo && $esAdmin && !$esPropiaCuenta) {
            app(NotificacionesService::class)->crear(
                $general_user->id,
                'Tu correo electrónico fue actualizado por un administrador.',
                'sistema'
            );
        }

        return $general_user;
    }

    public function destroy(Request $request, GeneralUser $general_user)
    {
        $yo = $request->user();

        // Autoprotección: nadie borra su propia cuenta.
        if ($yo && (int) $yo->id === (int) $general_user->id) {
            return response()->json(['message' => 'No puedes eliminar tu propia cuenta.'], 422);
        }

        // No se puede dejar el sistema sin administradores activos.
        if ($general_user->rol === 'admin' && $general_user->estado) {
            $activos = GeneralUser::where('rol', 'admin')->where('estado', true)->count();
            if ($activos <= 1) {
                return response()->json([
                    'message' => 'No puedes eliminar al último administrador activo.',
                ], 409);
            }
        }

        // Admin sin restricciones: se borra el usuario y todo lo dependiente en
        // cascada (el impacto se advierte en la confirmación del cliente).
        $instructor = $general_user->instructor;
        $impacto = [
            'propuestas' => Project::where('id_creador', $general_user->id)->count(),
            'fichas_a_cargo' => $instructor ? ClassGroup::where('id_instructor', $instructor->id)->count() : 0,
        ];

        BorradoCascada::usuario($general_user);

        Auditoria::registrar('eliminar_usuario', 'general_users', $general_user->id, [
            'correo' => $general_user->correo,
            'rol' => $general_user->rol,
            'impacto' => $impacto,
        ]);

        return $general_user;
    }

    // Exporta en PDF las credenciales iniciales de los usuarios seleccionados
    // (independiente de una ficha). Solo admin; queda en la bitácora.
    public function credenciales(Request $request)
    {
        $request->validate([
            'ids' => 'required|array|min:1',
            'ids.*' => 'integer|exists:general_users,id',
        ]);

        $usuarios = GeneralUser::with('apprentice.classGroup.program', 'instructor.classGroups.program')
            ->whereIn('id', $request->ids)
            ->orderBy('nombre')
            ->get();

        Auditoria::registrar('exportar_credenciales', 'general_users', null, [
            'usuarios' => $usuarios->count(),
            'temporales' => $usuarios->where('must_change_password', true)->count(),
        ]);

        return CredencialesPdf::generar($usuarios);
    }

    // ---------------------------------------------------------------- Perfiles

    // Alcance del perfil público según la relación académica.
    private function puedeVerPerfil(GeneralUser $yo, GeneralUser $objetivo): bool
    {
        if ($yo->rol === 'admin' || (int) $yo->id === (int) $objetivo->id) return true;

        $miAprendiz = $yo->apprentice()->first();
        $suAprendiz = $objetivo->apprentice;
        $miInstructor = $yo->instructor()->first();
        $suInstructor = $objetivo->instructor;

        // Compañeros de la misma ficha.
        if ($miAprendiz && $suAprendiz
            && $miAprendiz->id_class_group && $suAprendiz->id_class_group
            && (int) $miAprendiz->id_class_group === (int) $suAprendiz->id_class_group) {
            return true;
        }

        // Aprendiz → instructor responsable de su ficha.
        if ($miAprendiz && $miAprendiz->id_class_group && $suInstructor) {
            $ficha = ClassGroup::find($miAprendiz->id_class_group);
            if ($ficha && (int) $ficha->id_instructor === (int) $suInstructor->id) return true;
        }

        // Instructor → aprendiz de sus fichas.
        if ($miInstructor && $suAprendiz && $suAprendiz->id_class_group) {
            return ClassGroup::where('id', $suAprendiz->id_class_group)
                ->where('id_instructor', $miInstructor->id)
                ->exists();
        }

        return false;
    }

    // Crea el perfil que corresponde al rol. El de aprendiz no se crea aquí
    // porque `apprentices.id_programa` es obligatorio: nace al unirse a una ficha.
    private function sincronizarPerfiles(GeneralUser $usuario): void
    {
        if ($usuario->rol === 'instructor') {
            Instructor::firstOrCreate(
                ['id_usuario' => $usuario->id],
                ['fecha_ingreso' => now()->toDateString()]
            );
        }
        if ($usuario->rol === 'admin') {
            Admin::firstOrCreate(['id_usuario' => $usuario->id]);
        }
    }

    // Elimina los perfiles que ya no corresponden al rol, salvo que tengan
    // historial asociado (fichas a cargo, propuestas o equipo).
    private function limpiarPerfilesObsoletos(GeneralUser $usuario): void
    {
        if ($usuario->rol !== 'admin') {
            Admin::where('id_usuario', $usuario->id)->delete();
        }

        if ($usuario->rol !== 'instructor') {
            $instructor = $usuario->instructor;
            if ($instructor && !ClassGroup::where('id_instructor', $instructor->id)->exists()) {
                $instructor->delete();
            }
        }

        if ($usuario->rol !== 'aprendiz') {
            $aprendiz = $usuario->apprentice;
            if ($aprendiz) {
                // Deja de ser aprendiz: sale del roster de su ficha (la fila se
                // conserva para el historial de sus propuestas). Sin ficha no hay
                // programa vigente.
                $aprendiz->update(['id_class_group' => null, 'id_programa' => null]);
                $enEquipo = ApprenticeProject::where('id_aprendiz', $aprendiz->id)->exists();
                $esAutor = Project::where('id_creador', $usuario->id)->exists();
                if (!$enEquipo && !$esAutor) {
                    $aprendiz->delete();
                }
            }
        }
    }
}

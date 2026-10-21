<?php

namespace App\Services;

use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Support\Auditoria;
use App\Support\Credenciales;
use App\Support\Pii;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;

// Alta de una cuenta institucional: genera username + contraseña temporal,
// crea el perfil que corresponda, audita y (opcionalmente) envía las
// credenciales al correo personal. Punto único usado por el panel de usuarios
// y por el alta de aprendices desde una ficha.
class AltaUsuario
{
    public function crear(array $datos, bool $enviarCredenciales = true): array
    {
        $username = Credenciales::usernameSeguro($datos['nombre'], $datos['apellido']);
        $temporal = Credenciales::passwordTemporal();

        $data = $datos;
        $data['correo'] = strtolower(trim($datos['correo']));
        $data['username'] = $username;
        $data['password'] = Hash::make($temporal);
        $data['must_change_password'] = true;
        $data['password_temporal'] = Crypt::encryptString($temporal);
        $data['estado'] = $datos['estado'] ?? true;

        $usuario = DB::transaction(function () use ($data) {
            $usuario = GeneralUser::create($data);

            // El perfil de instructor nace con la cuenta; el de aprendiz nace al
            // asociarse a una ficha (id_programa es obligatorio).
            if ($usuario->rol === 'instructor') {
                Instructor::firstOrCreate(
                    ['id_usuario' => $usuario->id],
                    ['fecha_ingreso' => now()->toDateString()]
                );
            }

            Auditoria::registrar('crear_usuario', 'general_users', $usuario->id, [
                'correo' => $usuario->correo,
                'username' => $usuario->username,
                'documento' => Pii::documento($usuario->tipo_documento, $usuario->numero_documento),
                'rol' => $usuario->rol,
            ]);

            return $usuario;
        });

        $enviadas = $enviarCredenciales
            ? app(CredencialesCorreo::class)->enviar($usuario, $temporal)
            : false;

        return [
            'usuario' => $usuario->fresh(),
            'temporal' => $temporal,
            'enviadas' => $enviadas,
        ];
    }
}

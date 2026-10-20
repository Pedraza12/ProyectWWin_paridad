<?php

namespace Tests\Feature;

use App\Models\GeneralUser;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// M5 (contrato): el login de una cuenta suspendida responde 403 con el mensaje
// exacto 'Cuenta suspendida. Contacta al administrador.' (mismo texto que la
// guarda de tokens vigentes, CuentaActiva) y sin emitir token. Con credenciales
// incorrectas la respuesta sigue siendo 422: no se revela la suspensión.
class LoginCuentaSuspendidaTest extends TestCase
{
    use DatabaseTransactions;

    private function suspendido(): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => 'Suspendido',
            'apellido' => 'Contrato',
            'correo' => 'suspendido.' . uniqid() . '@login.local',
            'username' => 'suspendido_' . uniqid(),
            'password' => Hash::make('123456'),
            'rol' => 'aprendiz',
            'estado' => false,
        ]);
    }

    public function test_login_de_cuenta_suspendida_responde_403_con_mensaje_exacto(): void
    {
        $user = $this->suspendido();

        // Acceso oficial por username.
        $res = $this->postJson('/v1/auth/login', [
            'username' => $user->username,
            'password' => '123456',
        ])->assertStatus(403)
            ->assertJsonPath('message', 'Cuenta suspendida. Contacta al administrador.');

        // Respuesta de contrato: solo el mensaje, sin token ni datos de sesión.
        $res->assertJsonCount(1);
        $this->assertNull($res->json('token'));
        $this->assertSame(0, $user->tokens()->count());

        // Compatibilidad por correo: mismo 403 y mismo mensaje.
        $this->postJson('/v1/auth/login', [
            'correo' => $user->correo,
            'password' => '123456',
        ])->assertStatus(403)
            ->assertJsonPath('message', 'Cuenta suspendida. Contacta al administrador.');
    }

    public function test_credenciales_incorrectas_de_cuenta_suspendida_siguen_dando_422(): void
    {
        $user = $this->suspendido();

        $this->postJson('/v1/auth/login', [
            'username' => $user->username,
            'password' => 'clave-incorrecta',
        ])->assertStatus(422)
            ->assertJsonPath('message', 'Credenciales incorrectas. Verifica tus datos.');
    }
}

<?php

namespace Tests\Feature;

use App\Models\GeneralUser;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// Regresión: desactivar una cuenta es un cambio real y debe quedar en el
// detalle de auditoría como `estado: false` (el array_filter por defecto
// descartaba el booleano false y la desactivación no quedaba registrada).
class AuditoriaEstadoUsuarioTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Auditoria',
            'correo' => $rol . '.' . uniqid() . '@auditoria.local',
            'username' => 'aud_' . uniqid(),
            'password' => Hash::make('123456'),
            'rol' => $rol,
            'estado' => true,
        ]);
    }

    private function como(GeneralUser $user): string
    {
        $token = $this->postJson('/v1/auth/login', [
            'username' => $user->username,
            'password' => '123456',
        ])->assertOk()->json('token');
        $this->app['auth']->forgetGuards();

        return $token;
    }

    public function test_desactivar_una_cuenta_registra_estado_false_en_la_auditoria(): void
    {
        $admin = $this->usuario('admin');
        $objetivo = $this->usuario('aprendiz');
        $token = $this->como($admin);

        $this->withToken($token)
            ->putJson('/v1/general-users/' . $objetivo->id, ['estado' => false])
            ->assertOk();

        $this->assertFalse((bool) $objetivo->fresh()->estado);

        $registros = $this->withToken($token)
            ->getJson('/v1/audit-logs?' . http_build_query([
                'paginado' => 1,
                'por_pagina' => 100,
                'accion' => 'actualizar_usuario',
                'id_usuario' => $admin->id,
            ]))
            ->assertOk()
            ->json('data');

        $registro = collect($registros)->first(
            fn ($r) => (string) ($r['entidad'] ?? '') === 'general_users'
                && (int) ($r['entidad_id'] ?? 0) === (int) $objetivo->id
        );

        $this->assertNotNull($registro, 'Debe existir el registro de auditoría del cambio.');
        $this->assertArrayHasKey('estado', $registro['detalle']);
        $this->assertFalse($registro['detalle']['estado']);
    }
}

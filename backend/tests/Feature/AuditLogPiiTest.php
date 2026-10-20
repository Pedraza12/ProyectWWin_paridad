<?php

namespace Tests\Feature;

use App\Models\AuditLog;
use App\Models\GeneralUser;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// La bitácora es solo de admin y no debe transportar PII innecesaria del
// responsable (correo, username, foto en base64). El listado de usuarios
// conserva su contrato salvo cuando el cliente pide la proyección `resumen=1`.
class AuditLogPiiTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol, array $extra = []): GeneralUser
    {
        return GeneralUser::create(array_merge([
            'nombre' => ucfirst($rol) . 'Pii',
            'apellido' => 'Auditoria',
            'correo' => $rol . '.' . uniqid() . '@test.local',
            'password' => Hash::make('123456'),
            'rol' => $rol,
            'estado' => true,
        ], $extra));
    }

    private function token(GeneralUser $user): string
    {
        return $this->postJson('/v1/auth/login', [
            'correo' => $user->correo,
            'password' => '123456',
        ])->assertOk()->json('token');
    }

    private function como(GeneralUser $user)
    {
        $token = $this->token($user);
        $this->app['auth']->forgetGuards();
        return $this->withToken($token);
    }

    private function fotoBase64(): string
    {
        return 'data:image/jpeg;base64,' . base64_encode('foto-privada-' . uniqid());
    }

    private function registrarLog(GeneralUser $actor): AuditLog
    {
        return AuditLog::create([
            'id_usuario' => $actor->id,
            'accion' => 'crear_usuario',
            'entidad' => 'general_users',
            'entidad_id' => 1,
            'detalle' => ['resultado' => 'ok'],
            'ip' => '127.0.0.1',
        ]);
    }

    public function test_la_bitacora_paginada_proyecta_solo_datos_minimos_del_responsable(): void
    {
        $foto = $this->fotoBase64();
        $admin = $this->usuario('admin', ['foto_url' => $foto]);
        $log = $this->registrarLog($admin);

        $respuesta = $this->como($admin)
            ->getJson('/v1/audit-logs?paginado=1&id_usuario=' . $admin->id)
            ->assertOk();

        $fila = collect($respuesta->json('data'))->firstWhere('id', $log->id);
        $this->assertNotNull($fila);

        // Proyección explícita: lo justo para pintar al responsable.
        $this->assertSame(['id', 'nombre', 'apellido', 'rol'], array_keys($fila['user']));
        $this->assertSame($admin->id, $fila['user']['id']);
        $this->assertSame($admin->nombre, $fila['user']['nombre']);
        $this->assertSame($admin->apellido, $fila['user']['apellido']);
        $this->assertSame('admin', $fila['user']['rol']);
        // El detalle de la acción se conserva (la proyección solo afecta a `user`).
        $this->assertSame(['resultado' => 'ok'], $fila['detalle']);

        $cuerpo = $respuesta->getContent();
        $this->assertStringNotContainsString($admin->correo, $cuerpo);
        $this->assertStringNotContainsString('data:image', $cuerpo);
        $this->assertStringNotContainsString(substr($foto, strlen('data:image/jpeg;base64,')), $cuerpo);
    }

    public function test_la_proyeccion_se_mantiene_aunque_el_cliente_pida_included_user(): void
    {
        $admin = $this->usuario('admin', ['foto_url' => $this->fotoBase64()]);
        $log = $this->registrarLog($admin);

        // El arreglo completo (sin paginar) también debe respetar la proyección.
        $respuesta = $this->como($admin)
            ->getJson('/v1/audit-logs?id_usuario=' . $admin->id . '&included=user')
            ->assertOk();

        $fila = collect($respuesta->json())->firstWhere('id', $log->id);
        $this->assertNotNull($fila);
        $this->assertSame(['id', 'nombre', 'apellido', 'rol'], array_keys($fila['user']));
        $this->assertStringNotContainsString($admin->correo, $respuesta->getContent());
        $this->assertStringNotContainsString('data:image', $respuesta->getContent());
    }

    public function test_la_bitacora_sigue_siendo_solo_para_admin(): void
    {
        $aprendiz = $this->usuario('aprendiz');
        $admin = $this->usuario('admin');

        $this->como($aprendiz)->getJson('/v1/audit-logs?paginado=1')->assertStatus(403);
        $this->como($admin)->getJson('/v1/audit-logs?paginado=1')->assertOk();
    }

    public function test_el_listado_resumen_no_expone_correo_foto_ni_credenciales(): void
    {
        $admin = $this->usuario('admin');
        $objetivo = $this->usuario('aprendiz', [
            'nombre' => 'ResumenUnico' . uniqid(),
            'foto_url' => $this->fotoBase64(),
        ]);

        // Camino sin paginar.
        $respuesta = $this->como($admin)
            ->getJson('/v1/general-users?resumen=1&search=' . urlencode($objetivo->nombre))
            ->assertOk();

        $fila = collect($respuesta->json())->firstWhere('id', $objetivo->id);
        $this->assertNotNull($fila);
        $this->assertSame(['id', 'nombre', 'apellido', 'rol'], array_keys($fila));
        $this->assertStringNotContainsString($objetivo->correo, $respuesta->getContent());
        $this->assertStringNotContainsString('data:image', $respuesta->getContent());

        // Camino paginado: misma proyección.
        $paginada = $this->como($admin)
            ->getJson('/v1/general-users?resumen=1&paginado=1&por_pagina=100&search=' . urlencode($objetivo->nombre))
            ->assertOk();

        $filaPaginada = collect($paginada->json('data'))->firstWhere('id', $objetivo->id);
        $this->assertNotNull($filaPaginada);
        $this->assertSame(['id', 'nombre', 'apellido', 'rol'], array_keys($filaPaginada));
        $this->assertStringNotContainsString($objetivo->correo, $paginada->getContent());
        $this->assertStringNotContainsString('data:image', $paginada->getContent());
    }

    public function test_el_listado_sin_resumen_conserva_el_contrato_actual(): void
    {
        $admin = $this->usuario('admin');
        $objetivo = $this->usuario('aprendiz', [
            'nombre' => 'ContratoUnico' . uniqid(),
            'foto_url' => $this->fotoBase64(),
        ]);

        $respuesta = $this->como($admin)
            ->getJson('/v1/general-users?search=' . urlencode($objetivo->nombre))
            ->assertOk();

        $fila = collect($respuesta->json())->firstWhere('id', $objetivo->id);
        $this->assertNotNull($fila);
        // El panel de administración sigue recibiendo correo, foto y username.
        $this->assertSame($objetivo->correo, $fila['correo']);
        $this->assertSame($objetivo->foto_url, $fila['foto_url']);
        $this->assertArrayHasKey('username', $fila);
    }
}

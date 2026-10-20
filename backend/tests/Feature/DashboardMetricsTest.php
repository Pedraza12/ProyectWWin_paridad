<?php

namespace Tests\Feature;

use App\Models\GeneralUser;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// Contrato y permisos del endpoint de métricas (integración del trabajo del
// compañero): exige autenticación, el rol sale del token (no del cliente) y la
// respuesta no expone PII (correo, usuario de acceso ni documento).
class DashboardMetricsTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol, array $extra = []): GeneralUser
    {
        return GeneralUser::create(array_merge([
            'nombre' => 'Metricas ' . ucfirst($rol),
            'apellido' => 'Prueba',
            'correo' => $rol . '.' . uniqid() . '@metricas.local',
            'username' => 'metricas_' . uniqid(),
            'password' => Hash::make('123456'),
            'rol' => $rol,
            'estado' => true,
        ], $extra));
    }

    private function como(GeneralUser $user): string
    {
        return $this->postJson('/v1/auth/login', [
            'username' => $user->username,
            'password' => '123456',
        ])->assertOk()->json('token');
    }

    public function test_requiere_autenticacion(): void
    {
        $this->getJson('/v1/dashboard-metrics')->assertStatus(401);
    }

    public function test_el_rol_sale_del_token_y_no_del_cliente(): void
    {
        foreach (['admin', 'instructor', 'aprendiz'] as $rol) {
            $user = $this->usuario($rol);
            $token = $this->como($user);
            $this->app['auth']->forgetGuards();

            // El cliente intenta forzar el rol admin; debe prevalecer el del token.
            $res = $this->withToken($token)
                ->getJson('/v1/dashboard-metrics?rol=admin')
                ->assertOk();

            $this->assertSame($rol, $res->json('rol'));

            if ($rol === 'aprendiz') {
                // El bloque global del admin no debe aparecer en el ámbito del aprendiz.
                $this->assertArrayNotHasKey('usuarios', $res->json());
                $this->assertArrayNotHasKey('fichas', $res->json());
            }

            $this->app['auth']->forgetGuards();
        }
    }

    public function test_la_respuesta_no_expone_pii(): void
    {
        $numero = '1099' . random_int(100000, 999999);
        $admin = $this->usuario('admin', ['numero_documento' => $numero]);
        $token = $this->como($admin);
        $this->app['auth']->forgetGuards();

        $res = $this->withToken($token)->getJson('/v1/dashboard-metrics')->assertOk();

        $this->assertArrayHasKey('usuarios', $res->json());

        $cuerpo = $res->getContent();
        $this->assertStringNotContainsString($admin->correo, $cuerpo);
        $this->assertStringNotContainsString((string) $admin->username, $cuerpo);
        $this->assertStringNotContainsString($numero, $cuerpo);
    }
}

<?php

namespace Tests\Feature;

use App\Models\GeneralUser;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// Reglas del perfil: nadie cambia su propio rol; el correo solo se edita con
// los campos obligatorios y el rol nunca viaja si el cliente no lo envía.
class GeneralUserRolTest extends TestCase
{
    use DatabaseTransactions;

    private function crearUsuario(string $rol, bool $estado = true): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'E2E',
            'correo' => $rol . '.' . uniqid() . '@test.local',
            'password' => Hash::make('123456'),
            'rol' => $rol,
            'estado' => $estado,
        ]);
    }

    private function token(GeneralUser $user): string
    {
        return $this->postJson('/v1/auth/login', [
            'correo' => $user->correo,
            'password' => '123456',
        ])->assertOk()->json('token');
    }

    public function test_un_aprendiz_no_puede_cambiar_su_rol(): void
    {
        $user = $this->crearUsuario('aprendiz');

        $this->withToken($this->token($user))
            ->putJson('/v1/general-users/' . $user->id, [
                'nombre' => $user->nombre,
                'apellido' => $user->apellido,
                'correo' => $user->correo,
                'rol' => 'admin',
            ])
            ->assertStatus(403);

        $this->assertSame('aprendiz', $user->fresh()->rol);
    }

    public function test_un_instructor_tampoco_puede_ascenderse_a_admin(): void
    {
        $user = $this->crearUsuario('instructor');

        $this->withToken($this->token($user))
            ->putJson('/v1/general-users/' . $user->id, [
                'nombre' => $user->nombre,
                'apellido' => $user->apellido,
                'correo' => $user->correo,
                'rol' => 'admin',
            ])
            ->assertStatus(403);

        $this->assertSame('instructor', $user->fresh()->rol);
    }

    public function test_el_perfil_se_actualiza_sin_enviar_rol_y_este_no_cambia(): void
    {
        $user = $this->crearUsuario('aprendiz');

        $this->withToken($this->token($user))
            ->putJson('/v1/general-users/' . $user->id, [
                'nombre' => 'Nombre Nuevo',
                'apellido' => 'Apellido Nuevo',
                'correo' => $user->correo,
            ])
            ->assertOk();

        $fresco = $user->fresh();
        $this->assertSame('Nombre Nuevo', $fresco->nombre);
        $this->assertSame('aprendiz', $fresco->rol);
        $this->assertSame($user->correo, $fresco->correo);
    }

    public function test_nadie_cambia_su_propio_correo_por_el_update_generico(): void
    {
        $user = $this->crearUsuario('aprendiz');

        $this->withToken($this->token($user))
            ->putJson('/v1/general-users/' . $user->id, [
                'nombre' => $user->nombre,
                'apellido' => $user->apellido,
                'correo' => 'otro.' . uniqid() . '@test.local',
            ])
            ->assertStatus(422);

        $this->assertSame($user->correo, $user->fresh()->correo);
    }

    public function test_un_admin_si_puede_cambiar_el_rol_de_otro(): void
    {
        $admin = $this->crearUsuario('admin');
        $objetivo = $this->crearUsuario('aprendiz');

        $this->withToken($this->token($admin))
            ->putJson('/v1/general-users/' . $objetivo->id, [
                'nombre' => $objetivo->nombre,
                'apellido' => $objetivo->apellido,
                'correo' => $objetivo->correo,
                'rol' => 'instructor',
            ])
            ->assertOk();

        $this->assertSame('instructor', $objetivo->fresh()->rol);
    }

    public function test_un_admin_no_puede_quitarse_su_propio_rol(): void
    {
        $admin = $this->crearUsuario('admin');

        $this->withToken($this->token($admin))
            ->putJson('/v1/general-users/' . $admin->id, [
                'nombre' => $admin->nombre,
                'apellido' => $admin->apellido,
                'correo' => $admin->correo,
                'rol' => 'aprendiz',
            ])
            ->assertStatus(422);

        $this->assertSame('admin', $admin->fresh()->rol);
    }

    public function test_un_aprendiz_no_puede_suspender_a_otro_usuario(): void
    {
        $aprendiz = $this->crearUsuario('aprendiz');
        $objetivo = $this->crearUsuario('aprendiz');

        $this->withToken($this->token($aprendiz))
            ->putJson('/v1/general-users/' . $objetivo->id, [
                'nombre' => $objetivo->nombre,
                'apellido' => $objetivo->apellido,
                'correo' => $objetivo->correo,
                'estado' => false,
            ])
            ->assertStatus(403);

        $this->assertTrue($objetivo->fresh()->estado);
    }

    // ------------------------------------------------ D-4: auto-estado admin

    public function test_un_admin_no_cambia_su_propio_estado(): void
    {
        $admin = $this->crearUsuario('admin');
        $otroAdmin = $this->crearUsuario('admin'); // garantiza ≥2 admins activos
        $token = $this->token($admin);

        $this->withToken($token)
            ->putJson('/v1/general-users/' . $admin->id, [
                'nombre' => $admin->nombre,
                'apellido' => $admin->apellido,
                'correo' => $admin->correo,
                'estado' => false,
            ])
            ->assertStatus(422);

        $this->assertTrue($admin->fresh()->estado);

        // La propia sesión sigue válida: nada se tocó.
        $this->app['auth']->forgetGuards();
        $this->withToken($token)->getJson('/v1/auth/me')->assertOk();
    }

    public function test_otro_admin_si_gestiona_el_estado_y_las_demas_sesiones_no_se_afectan(): void
    {
        $adminA = $this->crearUsuario('admin');
        $adminB = $this->crearUsuario('admin');
        $tokenA = $this->token($adminA);
        $tokenB = $this->token($adminB);

        // B suspende a A (permitido: la regla solo prohíbe la autogestión).
        $this->withToken($tokenB)
            ->putJson('/v1/general-users/' . $adminA->id, [
                'nombre' => $adminA->nombre,
                'apellido' => $adminA->apellido,
                'correo' => $adminA->correo,
                'estado' => false,
            ])
            ->assertOk();
        $this->assertFalse($adminA->fresh()->estado);

        // El token de A queda bloqueado por el middleware de cuenta activa…
        $this->app['auth']->forgetGuards();
        $this->withToken($tokenA)->getJson('/v1/auth/me')->assertStatus(403);

        // …y la sesión de B (quien gestionó) permanece intacta.
        $this->app['auth']->forgetGuards();
        $this->withToken($tokenB)->getJson('/v1/auth/me')->assertOk();

        // B puede reactivarlo; la protección del último admin no se altera.
        $this->withToken($tokenB)
            ->putJson('/v1/general-users/' . $adminA->id, [
                'nombre' => $adminA->nombre,
                'apellido' => $adminA->apellido,
                'correo' => $adminA->correo,
                'estado' => true,
            ])
            ->assertOk();
        $this->assertTrue($adminA->fresh()->estado);
    }
}

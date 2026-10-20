<?php

namespace Tests\Feature;

use App\Models\GeneralUser;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// La foto de perfil solo admite data URLs de imagen dentro del tamaño de la
// columna TEXT. Se rechazan URLs externas, MIME no visual y payloads enormes.
class FotoPerfilValidacionTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol, array $extra = []): GeneralUser
    {
        return GeneralUser::create(array_merge([
            'nombre' => ucfirst($rol),
            'apellido' => 'Foto',
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

    // JPEG pequeño real en base64: formato exacto que generan web y Android.
    private function fotoValida(): string
    {
        return 'data:image/jpeg;base64,' . base64_encode(str_repeat('foto-perfil-', 8));
    }

    public function test_acepta_data_url_de_imagen_en_el_alta(): void
    {
        $admin = $this->usuario('admin');
        $foto = $this->fotoValida();

        $this->como($admin)->postJson('/v1/general-users', [
            'nombre' => 'Alta',
            'apellido' => 'ConFoto',
            'tipo_documento' => 'CC',
            'numero_documento' => (string) random_int(1000000, 9999999),
            'correo' => 'alta.foto.' . uniqid() . '@test.local',
            'rol' => 'aprendiz',
            'foto_url' => $foto,
        ])->assertCreated()->assertJsonPath('usuario.foto_url', $foto);
    }

    public function test_acepta_data_url_de_imagen_al_editar(): void
    {
        $user = $this->usuario('aprendiz');
        $foto = $this->fotoValida();

        $this->como($user)
            ->putJson('/v1/general-users/' . $user->id, ['foto_url' => $foto])
            ->assertOk()
            ->assertJsonPath('foto_url', $foto);

        $this->assertSame($foto, $user->fresh()->foto_url);
    }

    public function test_permite_quitar_la_foto_con_null(): void
    {
        $user = $this->usuario('aprendiz', ['foto_url' => $this->fotoValida()]);

        $this->como($user)
            ->putJson('/v1/general-users/' . $user->id, ['foto_url' => null])
            ->assertOk()
            ->assertJsonPath('foto_url', null);

        $this->assertNull($user->fresh()->foto_url);
    }

    public function test_rechaza_una_url_externa_en_el_alta(): void
    {
        $admin = $this->usuario('admin');
        $correo = 'alta.invalida.' . uniqid() . '@test.local';

        $this->como($admin)->postJson('/v1/general-users', [
            'nombre' => 'Alta',
            'apellido' => 'SinFotoValida',
            'tipo_documento' => 'CC',
            'numero_documento' => (string) random_int(1000000, 9999999),
            'correo' => $correo,
            'rol' => 'aprendiz',
            'foto_url' => 'https://ejemplo.test/foto.jpg',
        ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('foto_url');

        $this->assertDatabaseMissing('general_users', ['correo' => $correo]);
    }

    public function test_rechaza_una_url_externa(): void
    {
        $user = $this->usuario('aprendiz');

        $this->como($user)
            ->putJson('/v1/general-users/' . $user->id, [
                'foto_url' => 'https://ejemplo.test/foto.jpg',
            ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('foto_url');

        $this->assertNull($user->fresh()->foto_url);
    }

    public function test_rechaza_un_data_url_que_no_es_de_imagen(): void
    {
        $user = $this->usuario('aprendiz');

        $this->como($user)
            ->putJson('/v1/general-users/' . $user->id, [
                'foto_url' => 'data:text/html;base64,' . base64_encode('<script>alert(1)</script>'),
            ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('foto_url');
    }

    public function test_rechaza_svg_por_ser_imagen_scriptable(): void
    {
        $user = $this->usuario('aprendiz');

        $this->como($user)
            ->putJson('/v1/general-users/' . $user->id, [
                'foto_url' => 'data:image/svg+xml;base64,' . base64_encode('<svg onload="alert(1)"></svg>'),
            ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('foto_url');
    }

    public function test_rechaza_una_foto_que_supera_el_limite_de_la_columna(): void
    {
        $user = $this->usuario('aprendiz');

        // 65.001 caracteres de payload + prefijo supera el máximo permitido
        // (la columna TEXT admite 65.535 bytes; el tope deja 535 de margen).
        $foto = 'data:image/jpeg;base64,' . str_repeat('A', 65001);

        $this->como($user)
            ->putJson('/v1/general-users/' . $user->id, ['foto_url' => $foto])
            ->assertStatus(422)
            ->assertJsonValidationErrors('foto_url');

        $this->assertNull($user->fresh()->foto_url);
    }
}

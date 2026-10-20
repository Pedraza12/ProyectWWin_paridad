<?php

namespace Tests\Feature;

use App\Models\GeneralUser;
use App\Models\KnowledgeNetwork;
use App\Models\MotorConfig;
use App\Models\Project;
use App\Models\Similarity;
use App\Models\TrainingProgram;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// C-09: contratos básicos de la API — resumen público (sin token), logout que
// revoca el token vigente y escritura de similitudes reservada al admin
// (PUT/DELETE responden 403 a aprendiz e instructor y no alteran nada).
class PublicosYSesionTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Publico',
            'correo' => $rol . '.' . uniqid() . '@publicos.local',
            'password' => Hash::make('123456'),
            'rol' => $rol,
            'estado' => true,
        ]);
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
        // El guard cachea el usuario entre peticiones del mismo test; se
        // reinicia para que el token de cada rol aplique de verdad.
        $this->app['auth']->forgetGuards();

        return $this->withToken($token);
    }

    private function propuesta(GeneralUser $creador, string $estado): Project
    {
        return Project::create([
            'titulo' => 'Propuesta ' . $estado . ' ' . uniqid(),
            'resumen' => 'Resumen de la propuesta de prueba.',
            'area_aplicacion' => 'Tecnología',
            'estado' => $estado,
            'id_creador' => $creador->id,
        ]);
    }

    public function test_resumen_publico_sin_token_devuelve_solo_agregados(): void
    {
        $creador = $this->usuario('aprendiz');
        $this->propuesta($creador, 'aprobado');
        $this->propuesta($creador, 'pendiente');
        $this->propuesta($creador, 'rechazado'); // no debe contarse
        $red = KnowledgeNetwork::create(['nombre' => 'Red ' . uniqid()]);
        TrainingProgram::create([
            'nombre' => 'Programa ' . uniqid(),
            'nivel' => 'Tecnologo',
            'knowledge_network_id' => $red->id,
        ]);

        $res = $this->getJson('/v1/public/resumen')->assertOk();

        // Contrato exacto: cuatro agregados, ninguno personal.
        $res->assertJsonCount(4);
        $res->assertJsonStructure(['umbral', 'meses', 'total_propuestas', 'total_programas']);

        $config = MotorConfig::firstOrCreate([], ['umbral' => 0.30, 'meses' => 12]);
        $this->assertEqualsWithDelta((float) $config->umbral, (float) $res->json('umbral'), 0.0001);
        $this->assertSame((int) $config->meses, (int) $res->json('meses'));

        // Las rechazadas no cuentan y el total de programas es el real.
        $this->assertSame(
            Project::where('estado', '!=', 'rechazado')->count(),
            $res->json('total_propuestas')
        );
        $this->assertGreaterThanOrEqual(2, $res->json('total_propuestas'));
        $this->assertSame(TrainingProgram::count(), $res->json('total_programas'));

        // Sin PII: ni correo ni nombre del creador.
        $cuerpo = $res->getContent();
        $this->assertStringNotContainsString($creador->correo, $cuerpo);
        $this->assertStringNotContainsString($creador->nombre, $cuerpo);
    }

    public function test_logout_revoca_el_token_actual(): void
    {
        $user = $this->usuario('aprendiz');
        $token = $this->token($user);
        $this->app['auth']->forgetGuards();

        $this->withToken($token)->postJson('/v1/auth/logout')
            ->assertOk()
            ->assertJsonPath('message', 'Sesión cerrada.');

        // El token revocado ya no autentica.
        $this->app['auth']->forgetGuards();
        $this->withToken($token)->getJson('/v1/auth/me')->assertStatus(401);
    }

    public function test_logout_sin_token_es_rechazado(): void
    {
        $this->postJson('/v1/auth/logout')->assertStatus(401);
    }

    public function test_solo_admin_actualiza_o_borra_similitudes(): void
    {
        $aprendiz = $this->usuario('aprendiz');
        $instructor = $this->usuario('instructor');
        $admin = $this->usuario('admin');

        $pA = $this->propuesta($aprendiz, 'aprobado');
        $pB = $this->propuesta($instructor, 'aprobado');

        $similitud = Similarity::create([
            'id_proyecto_1' => min((int) $pA->id, (int) $pB->id),
            'id_proyecto_2' => max((int) $pA->id, (int) $pB->id),
            'porcentaje' => 55,
            'fecha' => now()->toDateString(),
        ]);

        $payload = [
            'porcentaje' => 66,
            'id_proyecto_1' => $similitud->id_proyecto_1,
            'id_proyecto_2' => $similitud->id_proyecto_2,
        ];

        // Aprendiz e instructor: 403 en ambas operaciones.
        $this->como($aprendiz)
            ->putJson('/v1/similarities/' . $similitud->id, $payload)
            ->assertStatus(403)
            ->assertJsonPath('message', 'Sin permiso para esta acción.');

        $this->como($instructor)
            ->deleteJson('/v1/similarities/' . $similitud->id)
            ->assertStatus(403)
            ->assertJsonPath('message', 'Sin permiso para esta acción.');

        // El rechazo no altera ni borra la fila.
        $this->assertSame(55, (int) $similitud->fresh()->porcentaje);
        $this->assertDatabaseHas('similarities', ['id' => $similitud->id]);

        // El admin sí actualiza y borra.
        $this->como($admin)
            ->putJson('/v1/similarities/' . $similitud->id, $payload)
            ->assertOk();
        $this->assertSame(66, (int) $similitud->fresh()->porcentaje);

        $this->como($admin)
            ->deleteJson('/v1/similarities/' . $similitud->id)
            ->assertOk();
        $this->assertDatabaseMissing('similarities', ['id' => $similitud->id]);
    }
}

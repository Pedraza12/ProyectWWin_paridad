<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\TrainingProgram;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// BUG-011: el alta de un aprendiz nuevo desde una ficha es atómica (cuenta +
// credenciales + asociación). Si la asociación falla, la cuenta recién creada
// se revierte con la transacción: no quedan usuarios huérfanos sin aprendiz.
class AtomicidadAltaAprendizTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Alta',
            'correo' => $rol . '.' . uniqid() . '@alta.local',
            'password' => Hash::make('123456'),
            'rol' => $rol,
            'estado' => true,
        ]);
    }

    private function como(GeneralUser $user)
    {
        $token = $this->postJson('/v1/auth/login', [
            'correo' => $user->correo,
            'password' => '123456',
        ])->assertOk()->json('token');
        $this->app['auth']->forgetGuards();

        return $this->withToken($token);
    }

    private function ficha(): ClassGroup
    {
        $red = KnowledgeNetwork::create(['nombre' => 'Red ' . uniqid()]);
        $programa = TrainingProgram::create([
            'nombre' => 'Programa ' . uniqid(),
            'nivel' => 'Tecnologo',
            'knowledge_network_id' => $red->id,
        ]);
        $instructor = Instructor::create([
            'fecha_ingreso' => '2024-01-01',
            'id_usuario' => $this->usuario('instructor')->id,
        ]);

        return ClassGroup::create([
            'codigo' => 'aa-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha alta atómica',
            'estado' => 'activo',
            'id_programa' => $programa->id,
            'id_instructor' => $instructor->id,
        ]);
    }

    private function datosAlta(string $correo): array
    {
        return [
            'nombre' => 'Nuevo',
            'apellido' => 'Aprendiz',
            'tipo_documento' => 'CC',
            'numero_documento' => (string) random_int(10000000, 99999999),
            'correo' => $correo,
        ];
    }

    public function test_alta_exitosa_crea_cuenta_y_aprendiz(): void
    {
        $admin = $this->usuario('admin');
        $ficha = $this->ficha();
        $correo = 'nuevo.alta.' . uniqid() . '@alta.local';

        $respuesta = $this->como($admin)
            ->postJson('/v1/class-groups/' . $ficha->id . '/aprendices', $this->datosAlta($correo))
            ->assertCreated();

        $this->assertTrue($respuesta->json('creada'));
        $this->assertDatabaseHas('general_users', ['correo' => $correo, 'rol' => 'aprendiz']);
        $this->assertDatabaseHas('apprentices', [
            'id_usuario' => $respuesta->json('usuario.id'),
            'id_class_group' => $ficha->id,
        ]);
    }

    public function test_fallo_en_la_asociacion_deshace_la_cuenta_nueva(): void
    {
        $admin = $this->usuario('admin');
        $ficha = $this->ficha();
        $correo = 'fallo.alta.' . uniqid() . '@alta.local';

        // Fallo determinista en el INSERT del aprendiz: la cuenta ya se creó
        // dentro de la misma transacción y debe revertirse con ella.
        DB::beforeExecuting(function ($query) {
            if (stripos($query, 'insert into `apprentices`') !== false) {
                throw new \RuntimeException('Fallo inducido en la asociación');
            }
        });

        $this->como($admin)
            ->postJson('/v1/class-groups/' . $ficha->id . '/aprendices', $this->datosAlta($correo))
            ->assertStatus(500);

        $this->assertDatabaseMissing('general_users', ['correo' => $correo]);
        $this->assertSame(0, Apprentice::where('id_class_group', $ficha->id)->count());
    }
}

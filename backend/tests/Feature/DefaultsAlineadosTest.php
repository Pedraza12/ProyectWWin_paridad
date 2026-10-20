<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\MotorConfig;
use App\Models\TrainingProgram;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// P-01 (defaults muertos): la migración fuente de `projects.estado` declaraba
// default `pendiente` (la app SIEMPRE crea `borrador`) y la de
// `motor_configs.umbral` declaraba default `0.2` (la app SIEMPRE crea 0.30).
// Los defaults quedaron alineados en las migraciones fuente. Como
// `proyectwin_test` ya está migrada, la introspección de defaults de la BD no
// es viable sin re-migrar (la restricción del sprint lo prohíbe): estas
// pruebas cubren el comportamiento real de los caminos que crean cada fila.
class DefaultsAlineadosTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Defaults',
            'correo' => $rol . '.' . uniqid() . '@defaults.local',
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

    private function aprendizEnFicha(): GeneralUser
    {
        $red = KnowledgeNetwork::create(['nombre' => 'Red ' . uniqid()]);
        $programa = TrainingProgram::create([
            'nombre' => 'Programa ' . uniqid(),
            'nivel' => 'Tecnologo',
            'knowledge_network_id' => $red->id,
        ]);
        $instructorUser = $this->usuario('instructor');
        $instructor = Instructor::create([
            'fecha_ingreso' => now()->toDateString(),
            'id_usuario' => $instructorUser->id,
        ]);
        $ficha = ClassGroup::create([
            'codigo' => 'de-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha defaults',
            'estado' => 'activo',
            'id_programa' => $programa->id,
            'id_instructor' => $instructor->id,
        ]);

        $user = $this->usuario('aprendiz');
        Apprentice::create([
            'codigo' => 'AP-' . uniqid(),
            'id_usuario' => $user->id,
            'id_class_group' => $ficha->id,
            'id_programa' => $ficha->id_programa,
        ]);

        return $user;
    }

    // El camino real de alta (ProjectController::store) no envía `estado` en
    // el request y no depende del default del esquema ya migrado: nace
    // borrador y así queda persistido.
    public function test_una_propuesta_creada_sin_estado_nace_en_borrador(): void
    {
        $aprendiz = $this->aprendizEnFicha();

        $creada = $this->como($aprendiz)->postJson('/v1/projects', [
            'titulo' => 'Propuesta sin estado explícito',
            'resumen' => 'Propuesta creada sin el campo estado para verificar el valor real de alta.',
            'area_aplicacion' => 'Desarrollo Web',
            'objetivo_general' => 'Verificar que una propuesta sin estado explícito nace en borrador.',
            'objetivos_especificos' => [
                'Crear la propuesta sin el campo estado.',
                'Comprobar el estado persistido en la base de datos.',
            ],
            'id_creador' => $aprendiz->id,
        ])->assertCreated();

        $this->assertSame('borrador', $creada->json('estado'));
        $this->assertDatabaseHas('projects', [
            'id' => $creada->json('id'),
            'estado' => 'borrador',
        ]);
    }

    // El camino real que inicializa la configuración del motor
    // (MotorConfig::firstOrCreate) crea umbral 0.30 aunque no exista fila: no
    // depende del viejo default 0.2 del esquema ya migrado.
    public function test_la_app_crea_la_config_del_motor_con_umbral_030(): void
    {
        // Sin fila previa (el trait restaura el estado con su rollback).
        MotorConfig::query()->delete();

        // La primera creación responde 201 (Eloquent: wasRecentlyCreated); la
        // prueba no fija ese matiz, solo el valor creado por la app.
        $this->getJson('/v1/config-similitud')
            ->assertSuccessful()
            ->assertJsonPath('umbral', 0.30)
            ->assertJsonPath('meses', 12);

        $this->assertDatabaseHas('motor_configs', [
            'umbral' => 0.30,
            'meses' => 12,
        ]);
    }
}

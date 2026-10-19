<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\TrainingProgram;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// BUG-002: guardas de ficha en store/update: un instructor no puede llevarse
// aprendices de fichas ajenas (ni al crear una ficha ni al editar la suya);
// el aprendiz sin ficha sí se agrega y solo el admin traslada entre fichas.
class CrossFichaStoreUpdateTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Cross',
            'correo' => $rol . '.' . uniqid() . '@cross.local',
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

    private function instructor(GeneralUser $user): Instructor
    {
        return Instructor::create(['fecha_ingreso' => '2024-01-01', 'id_usuario' => $user->id]);
    }

    private function programa(): TrainingProgram
    {
        $red = KnowledgeNetwork::create(['nombre' => 'Red ' . uniqid()]);

        return TrainingProgram::create([
            'nombre' => 'Programa ' . uniqid(),
            'nivel' => 'Tecnologo',
            'knowledge_network_id' => $red->id,
        ]);
    }

    private function fichaDe(Instructor $instructor, TrainingProgram $programa, string $nombre): ClassGroup
    {
        return ClassGroup::create([
            'codigo' => 'cf-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => $nombre,
            'estado' => 'activo',
            'id_programa' => $programa->id,
            'id_instructor' => $instructor->id,
        ]);
    }

    private function aprendizEn(ClassGroup $ficha): GeneralUser
    {
        $user = $this->usuario('aprendiz');
        Apprentice::create([
            'codigo' => 'AP-' . uniqid(),
            'id_usuario' => $user->id,
            'id_class_group' => $ficha->id,
            'id_programa' => $ficha->id_programa,
        ]);

        return $user;
    }

    // Mensaje real de ClassGroupController::store/update al bloquear el traslado.
    private function mensajeFuera(GeneralUser $aprendiz): string
    {
        return 'El aprendiz ' . $aprendiz->nombre . ' ' . $aprendiz->apellido
            . ' pertenece a otra ficha. Solo un administrador puede trasladarlo.';
    }

    public function test_instructor_no_crea_ficha_con_aprendiz_ajeno(): void
    {
        $programa = $this->programa();
        $fichaA = $this->fichaDe($this->instructor($this->usuario('instructor')), $programa, 'Ficha A');
        $x = $this->aprendizEn($fichaA);

        $instructorB = $this->instructor($this->usuario('instructor'));
        $numero = (string) random_int(100000, 999999);

        $this->como($instructorB->generalUser)
            ->postJson('/v1/class-groups', [
                'numero' => $numero,
                'nombre' => 'Ficha creada por B',
                'estado' => 'activo',
                'id_programa' => $programa->id,
                'id_instructor' => $instructorB->id,
                'aprendices' => [$x->id],
            ])
            ->assertStatus(422)
            ->assertJsonPath('message', $this->mensajeFuera($x));

        // La guarda corta antes de la transacción: no se crea la ficha y X sigue en A.
        $this->assertDatabaseMissing('class_groups', ['numero' => $numero]);
        $this->assertDatabaseHas('apprentices', ['id_usuario' => $x->id, 'id_class_group' => $fichaA->id]);
    }

    public function test_instructor_no_mueve_aprendiz_ajeno_al_editar_su_ficha(): void
    {
        $programa = $this->programa();
        $fichaA = $this->fichaDe($this->instructor($this->usuario('instructor')), $programa, 'Ficha A');
        $x = $this->aprendizEn($fichaA);

        $userB = $this->usuario('instructor');
        $instructorB = $this->instructor($userB);
        $fichaB = $this->fichaDe($instructorB, $programa, 'Ficha B');

        $this->como($userB)
            ->putJson('/v1/class-groups/' . $fichaB->id, [
                'numero' => $fichaB->numero,
                'nombre' => 'Ficha B editada',
                'estado' => 'activo',
                'id_programa' => $programa->id,
                'id_instructor' => $instructorB->id,
                'aprendices' => [$x->id],
            ])
            ->assertStatus(422)
            ->assertJsonPath('message', $this->mensajeFuera($x));

        // Sin efecto parcial: X sigue en A y la ficha de B conserva su nombre.
        $this->assertDatabaseHas('apprentices', ['id_usuario' => $x->id, 'id_class_group' => $fichaA->id]);
        $this->assertSame('Ficha B', $fichaB->fresh()->nombre);
    }

    public function test_instructor_si_agrega_aprendiz_sin_ficha(): void
    {
        $programa = $this->programa();
        $userB = $this->usuario('instructor');
        $instructorB = $this->instructor($userB);
        $fichaB = $this->fichaDe($instructorB, $programa, 'Ficha B');

        // Aprendiz sin ficha (fila con id_class_group nulo): no hay traslado ajeno.
        $y = $this->usuario('aprendiz');
        Apprentice::create([
            'codigo' => 'AP-' . uniqid(),
            'id_usuario' => $y->id,
            'id_class_group' => null,
            'id_programa' => null,
        ]);

        $this->como($userB)
            ->putJson('/v1/class-groups/' . $fichaB->id, [
                'numero' => $fichaB->numero,
                'nombre' => 'Ficha B',
                'estado' => 'activo',
                'id_programa' => $programa->id,
                'id_instructor' => $instructorB->id,
                'aprendices' => [$y->id],
            ])
            ->assertOk();

        $this->assertDatabaseHas('apprentices', [
            'id_usuario' => $y->id,
            'id_class_group' => $fichaB->id,
            'id_programa' => $programa->id,
        ]);
    }

    public function test_admin_si_puede_trasladar(): void
    {
        $programa = $this->programa();
        $fichaA = $this->fichaDe($this->instructor($this->usuario('instructor')), $programa, 'Ficha A');
        $x = $this->aprendizEn($fichaA);

        $instructorDestino = $this->instructor($this->usuario('instructor'));
        $fichaDestino = $this->fichaDe($instructorDestino, $programa, 'Ficha destino');

        $this->como($this->usuario('admin'))
            ->putJson('/v1/class-groups/' . $fichaDestino->id, [
                'numero' => $fichaDestino->numero,
                'nombre' => 'Ficha destino',
                'estado' => 'activo',
                'id_programa' => $programa->id,
                'id_instructor' => $instructorDestino->id,
                'aprendices' => [$x->id],
            ])
            ->assertOk();

        $this->assertDatabaseHas('apprentices', [
            'id_usuario' => $x->id,
            'id_class_group' => $fichaDestino->id,
        ]);
    }
}

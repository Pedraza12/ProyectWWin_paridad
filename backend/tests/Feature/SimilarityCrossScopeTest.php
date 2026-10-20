<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\Project;
use App\Models\Similarity;
use App\Models\TrainingProgram;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// El detalle de similitud no puede entregar contenido de una propuesta que el
// rol no puede abrir. El motor solo genera pares del mismo programa; un par
// manual que cruza programas (creado por un admin) no debe quedar visible para
// el instructor de una de las fichas.
class SimilarityCrossScopeTest extends TestCase
{
    use DatabaseTransactions;

    private const INCLUDE = 'project1.classGroup.program,project2.classGroup.program,project1.creator,project2.creator';

    private GeneralUser $instructor;
    private GeneralUser $admin;
    private ClassGroup $fichaA;
    private ClassGroup $fichaA2;
    private ClassGroup $fichaB;
    private Project $proyectoA;
    private Project $proyectoA2;
    private Project $proyectoB;
    private Similarity $parCruzado;
    private Similarity $parMismoPrograma;

    protected function setUp(): void
    {
        parent::setUp();

        Similarity::query()->delete();

        $programaA = $this->programa();
        $programaB = $this->programa();

        $this->instructor = $this->usuario('instructor');
        $instA = Instructor::create([
            'fecha_ingreso' => '2024-01-01',
            'id_usuario' => $this->instructor->id,
        ]);
        $otroInstructor = $this->usuario('instructor');
        $instB = Instructor::create([
            'fecha_ingreso' => '2024-01-01',
            'id_usuario' => $otroInstructor->id,
        ]);

        // Dos fichas del programa A (una del instructor y otra del mismo
        // programa) y una ficha del programa B (fuera de su alcance).
        $this->fichaA = $this->ficha($programaA, $instA->id, 'crz-aaaa');
        $this->fichaA2 = $this->ficha($programaA, $instA->id, 'crz-bbbb');
        $this->fichaB = $this->ficha($programaB, $instB->id, 'crz-cccc');

        $aprendizA = $this->usuario('aprendiz');
        $aprendizA2 = $this->usuario('aprendiz');
        $aprendizB = $this->usuario('aprendiz');
        $this->admin = $this->usuario('admin');

        $this->aprendiz($aprendizA, $this->fichaA, $programaA);
        $this->aprendiz($aprendizA2, $this->fichaA2, $programaA);
        $this->aprendiz($aprendizB, $this->fichaB, $programaB);

        $this->proyectoA = $this->proyecto('Propuesta A', $aprendizA, $this->fichaA);
        $this->proyectoA2 = $this->proyecto('Propuesta A2', $aprendizA2, $this->fichaA2);
        $this->proyectoB = $this->proyecto('Propuesta B', $aprendizB, $this->fichaB);

        $this->parCruzado = $this->similaridad($this->proyectoA, $this->proyectoB);
        $this->parMismoPrograma = $this->similaridad($this->proyectoA, $this->proyectoA2);
    }

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Cross',
            'correo' => $rol . '.' . uniqid() . '@test.local',
            'password' => Hash::make('123456'),
            'rol' => $rol,
            'estado' => true,
        ]);
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

    private function ficha(TrainingProgram $programa, int $instructorId, string $codigo): ClassGroup
    {
        return ClassGroup::create([
            'codigo' => $codigo . '-' . substr(uniqid(), -4),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha ' . $codigo,
            'estado' => 'activo',
            'id_programa' => $programa->id,
            'id_instructor' => $instructorId,
        ]);
    }

    private function aprendiz(GeneralUser $user, ClassGroup $ficha, TrainingProgram $programa): void
    {
        Apprentice::create([
            'codigo' => 'AP-' . uniqid(),
            'id_usuario' => $user->id,
            'id_class_group' => $ficha->id,
            'id_programa' => $programa->id,
        ]);
    }

    private function proyecto(string $titulo, GeneralUser $creador, ClassGroup $ficha): Project
    {
        return Project::create([
            'titulo' => $titulo,
            'resumen' => 'Resumen de ' . $titulo,
            'area_aplicacion' => 'Tecnología',
            'estado' => 'aprobado',
            'id_creador' => $creador->id,
            'id_instructor_asignado' => $ficha->id_instructor,
            'id_class_group' => $ficha->id,
        ]);
    }

    private function similaridad(Project $a, Project $b): Similarity
    {
        [$p1, $p2] = $a->id < $b->id ? [$a->id, $b->id] : [$b->id, $a->id];

        return Similarity::create([
            'id_proyecto_1' => $p1,
            'id_proyecto_2' => $p2,
            'porcentaje' => 65,
            'fecha' => now()->toDateString(),
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
        $this->app['auth']->forgetGuards();
        return $this->withToken($token);
    }

    private function urlSimilitud(int $id): string
    {
        return '/v1/similarities/' . $id . '?' . http_build_query(['included' => self::INCLUDE]);
    }

    private function idsListado(array $query = []): array
    {
        return collect(
            $this->como($this->instructor)
                ->getJson('/v1/similarities?' . http_build_query(array_merge(
                    ['included' => self::INCLUDE],
                    $query
                )))
                ->assertOk()
                ->json()
        )->pluck('id')->all();
    }

    public function test_el_instructor_no_abre_un_par_que_cruza_programas(): void
    {
        // Evidencia de la regla vigente: la contraparte de otro programa no se
        // puede abrir por el endpoint de propuestas.
        $this->como($this->instructor)
            ->getJson('/v1/projects/' . $this->proyectoB->id)
            ->assertStatus(403);

        $respuesta = $this->como($this->instructor)
            ->getJson($this->urlSimilitud($this->parCruzado->id));

        $respuesta->assertStatus(403);
        $this->assertArrayNotHasKey('project1', $respuesta->json());
        $this->assertArrayNotHasKey('project2', $respuesta->json());
    }

    public function test_el_par_cruzado_no_aparece_en_el_listado_del_instructor(): void
    {
        $ids = $this->idsListado();

        $this->assertContains($this->parMismoPrograma->id, $ids);
        $this->assertNotContains($this->parCruzado->id, $ids);
    }

    public function test_la_vista_por_propuesta_oculta_el_par_cruzado(): void
    {
        $ids = $this->idsListado(['proyecto_id' => $this->proyectoA->id]);

        $this->assertContains($this->parMismoPrograma->id, $ids);
        $this->assertNotContains($this->parCruzado->id, $ids);
    }

    public function test_el_instructor_tampoco_abre_la_evidencia_historica_de_un_par_cruzado(): void
    {
        $this->parCruzado->update(['vigente' => false]);

        $this->como($this->instructor)
            ->getJson($this->urlSimilitud($this->parCruzado->id))
            ->assertStatus(403);

        $ids = $this->idsListado([
            'proyecto_id' => $this->proyectoA->id,
            'historial' => 1,
        ]);

        $this->assertNotContains($this->parCruzado->id, $ids);
    }

    public function test_el_instructor_si_abre_el_par_de_su_mismo_programa(): void
    {
        $this->como($this->instructor)
            ->getJson($this->urlSimilitud($this->parMismoPrograma->id))
            ->assertOk()
            ->assertJsonPath('id', $this->parMismoPrograma->id);
    }

    public function test_el_admin_si_abre_el_par_cruzado(): void
    {
        // La restricción es del alcance del instructor, no un bloqueo global:
        // el admin conserva la vista completa.
        $this->como($this->admin)
            ->getJson($this->urlSimilitud($this->parCruzado->id))
            ->assertOk()
            ->assertJsonPath('id', $this->parCruzado->id);
    }
}

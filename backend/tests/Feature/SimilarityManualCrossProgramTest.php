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

// Un admin puede crear pares manuales entre propuestas de programas distintos
// (`POST /v1/similarities`), pero el par no es una autorización para revelar la
// contraparte: el motor solo compara dentro del mismo programa y el aprendiz
// no puede abrir contenido de otro programa. El par debe quedar visible solo
// para el admin; los pares del mismo programa (aunque crucen fichas) siguen
// siendo visibles para el aprendiz dueño.
class SimilarityManualCrossProgramTest extends TestCase
{
    use DatabaseTransactions;

    private const INCLUDE = 'project1.classGroup.program,project2.classGroup.program,project1.creator,project2.creator';

    private GeneralUser $aprendizA;
    private GeneralUser $aprendizA2;
    private GeneralUser $aprendizB;
    private GeneralUser $admin;
    private ClassGroup $fichaA;
    private ClassGroup $fichaA2;
    private ClassGroup $fichaB;
    private Project $proyectoA;
    private Project $proyectoA2;
    private Project $proyectoB;
    private Similarity $parMismoPrograma;
    private Similarity $parCrossProgram;

    protected function setUp(): void
    {
        parent::setUp();

        // La base local puede traer pares del seed; el alcance debe ser
        // determinista (la transacción los restaura al terminar).
        Similarity::query()->delete();

        $programaA = $this->programa();
        $programaB = $this->programa();

        $instructorA = $this->usuario('instructor');
        $instA = Instructor::create([
            'fecha_ingreso' => '2024-01-01',
            'id_usuario' => $instructorA->id,
        ]);
        $instructorB = $this->usuario('instructor');
        $instB = Instructor::create([
            'fecha_ingreso' => '2024-01-01',
            'id_usuario' => $instructorB->id,
        ]);

        $this->fichaA = $this->ficha($programaA, $instA->id, 'mcp-a');
        $this->fichaA2 = $this->ficha($programaA, $instA->id, 'mcp-a2');
        $this->fichaB = $this->ficha($programaB, $instB->id, 'mcp-b');

        $this->aprendizA = $this->usuario('aprendiz');
        $this->aprendizA2 = $this->usuario('aprendiz');
        $this->aprendizB = $this->usuario('aprendiz');
        $this->admin = $this->usuario('admin');

        $this->aprendiz($this->aprendizA, $this->fichaA, $programaA);
        $this->aprendiz($this->aprendizA2, $this->fichaA2, $programaA);
        $this->aprendiz($this->aprendizB, $this->fichaB, $programaB);

        $this->proyectoA = $this->proyecto('Own proposal A', $this->aprendizA, $this->fichaA);
        $this->proyectoA2 = $this->proyecto('Peer proposal A2', $this->aprendizA2, $this->fichaA2);
        $this->proyectoB = $this->proyecto('Cross program B', $this->aprendizB, $this->fichaB);

        // Par del mismo programa (fichas distintas) y par manual cross-program.
        $this->parMismoPrograma = $this->similaridad($this->proyectoA, $this->proyectoA2);
        $this->parCrossProgram = $this->similaridad($this->proyectoA, $this->proyectoB);
    }

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Manual',
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
            'codigo' => $codigo . '-' . uniqid(),
            'numero' => (string) random_int(1000000, 9999999),
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
            'porcentaje' => 80,
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

    private function idsListado(GeneralUser $user, array $query = []): array
    {
        return collect(
            $this->como($user)
                ->getJson('/v1/similarities?' . http_build_query(array_merge(
                    ['included' => self::INCLUDE],
                    $query
                )))
                ->assertOk()
                ->json()
        )->pluck('id')->all();
    }

    public function test_el_aprendiz_dueno_no_ve_ni_abre_el_par_manual_cross_program(): void
    {
        $listado = $this->como($this->aprendizA)
            ->getJson('/v1/similarities?' . http_build_query(['included' => self::INCLUDE]))
            ->assertOk();
        $ids = collect($listado->json())->pluck('id')->all();

        // El par del mismo programa sí es visible; el manual cross-program no.
        $this->assertContains($this->parMismoPrograma->id, $ids);
        $this->assertNotContains($this->parCrossProgram->id, $ids);
        $this->assertStringNotContainsString($this->proyectoB->titulo, $listado->getContent());

        // Detalle directo del par: 403 y sin contenido de la contraparte.
        $detalle = $this->como($this->aprendizA)
            ->getJson('/v1/similarities/' . $this->parCrossProgram->id);
        $detalle->assertStatus(403);
        $this->assertStringNotContainsString($this->proyectoB->titulo, $detalle->getContent());
        $this->assertStringNotContainsString($this->proyectoB->resumen, $detalle->getContent());

        // Vista por propuesta propia: el par cross-program tampoco aparece.
        $porPropia = $this->como($this->aprendizA)
            ->getJson('/v1/similarities?' . http_build_query([
                'proyecto_id' => $this->proyectoA->id,
                'included' => self::INCLUDE,
            ]))
            ->assertOk();
        $idsPropia = collect($porPropia->json())->pluck('id')->all();
        $this->assertContains($this->parMismoPrograma->id, $idsPropia);
        $this->assertNotContains($this->parCrossProgram->id, $idsPropia);
        $this->assertStringNotContainsString($this->proyectoB->titulo, $porPropia->getContent());

        // Tampoco puede pedir los pares desde la contraparte ni abrirla por id.
        $this->como($this->aprendizA)
            ->getJson('/v1/similarities?proyecto_id=' . $this->proyectoB->id)
            ->assertStatus(403);

        $contraparte = $this->como($this->aprendizA)
            ->getJson('/v1/projects/' . $this->proyectoB->id . '?included=creator,apprentices.generalUser');
        $contraparte->assertStatus(403);
        $this->assertStringNotContainsString($this->proyectoB->titulo, $contraparte->getContent());
        $this->assertStringNotContainsString($this->proyectoB->resumen, $contraparte->getContent());
    }

    public function test_el_included_de_similitudes_en_una_propia_no_expone_el_par_cross_program(): void
    {
        $incluir = 'similaritiesAsOrigin,similaritiesAsDestination';

        $propia = $this->como($this->aprendizA)
            ->getJson('/v1/projects/' . $this->proyectoA->id . '?' . http_build_query(['included' => $incluir]))
            ->assertOk()
            ->json();

        $ids = collect($propia['similaritiesAsOrigin'] ?? $propia['similarities_as_origin'] ?? [])
            ->merge($propia['similaritiesAsDestination'] ?? $propia['similarities_as_destination'] ?? [])
            ->pluck('id')
            ->all();

        // El par del mismo programa sigue visible; el manual cross-program no.
        $this->assertContains($this->parMismoPrograma->id, $ids);
        $this->assertNotContains($this->parCrossProgram->id, $ids);

        // El admin conserva la vista completa.
        $admin = $this->como($this->admin)
            ->getJson('/v1/projects/' . $this->proyectoA->id . '?' . http_build_query(['included' => $incluir]))
            ->assertOk()
            ->json();

        $idsAdmin = collect($admin['similaritiesAsOrigin'] ?? $admin['similarities_as_origin'] ?? [])
            ->merge($admin['similaritiesAsDestination'] ?? $admin['similarities_as_destination'] ?? [])
            ->pluck('id')
            ->all();

        $this->assertContains($this->parCrossProgram->id, $idsAdmin);
    }

    public function test_la_contraparte_cross_program_tampoco_puede_abrir_el_par_ni_la_otra_propuesta(): void
    {
        $this->assertNotContains(
            $this->parCrossProgram->id,
            $this->idsListado($this->aprendizB)
        );

        $this->como($this->aprendizB)
            ->getJson('/v1/similarities/' . $this->parCrossProgram->id)
            ->assertStatus(403);

        $this->como($this->aprendizB)
            ->getJson('/v1/projects/' . $this->proyectoA->id)
            ->assertStatus(403);
    }

    public function test_el_aprendiz_conserva_el_acceso_a_la_contraparte_del_mismo_programa(): void
    {
        // La contraparte está en otra ficha del mismo programa: la relación de
        // similitud sigue habilitando la lectura (regla de AccesoContraparteTest).
        $this->como($this->aprendizA)
            ->getJson('/v1/similarities/' . $this->parMismoPrograma->id)
            ->assertOk()
            ->assertJsonPath('id', $this->parMismoPrograma->id);

        $this->como($this->aprendizA)
            ->getJson('/v1/projects/' . $this->proyectoA2->id)
            ->assertOk()
            ->assertJsonPath('id', $this->proyectoA2->id);
    }

    public function test_la_evidencia_historica_de_un_par_cross_program_tampoco_se_expone(): void
    {
        $this->parCrossProgram->update(['vigente' => false]);
        $this->parMismoPrograma->update(['vigente' => false]);

        $historial = $this->como($this->aprendizA)
            ->getJson('/v1/similarities?' . http_build_query([
                'proyecto_id' => $this->proyectoA->id,
                'historial' => 1,
                'included' => self::INCLUDE,
            ]))
            ->assertOk();
        $ids = collect($historial->json())->pluck('id')->all();

        // La evidencia histórica del par legítimo se conserva; la del par
        // cross-program no se expone.
        $this->assertContains($this->parMismoPrograma->id, $ids);
        $this->assertNotContains($this->parCrossProgram->id, $ids);
        $this->assertStringNotContainsString($this->proyectoB->titulo, $historial->getContent());

        $this->como($this->aprendizA)
            ->getJson('/v1/similarities/' . $this->parCrossProgram->id)
            ->assertStatus(403);

        $this->como($this->aprendizA)
            ->getJson('/v1/projects/' . $this->proyectoB->id)
            ->assertStatus(403);
    }

    public function test_el_admin_conserva_la_vista_del_par_manual_cross_program(): void
    {
        // La restricción es del alcance del aprendiz: el admin mantiene la
        // supervisión completa del par que él mismo puede crear.
        $this->assertContains(
            $this->parCrossProgram->id,
            $this->idsListado($this->admin)
        );

        $this->como($this->admin)
            ->getJson('/v1/similarities/' . $this->parCrossProgram->id)
            ->assertOk()
            ->assertJsonPath('id', $this->parCrossProgram->id);

        $this->como($this->admin)
            ->getJson('/v1/projects/' . $this->proyectoB->id)
            ->assertOk()
            ->assertJsonPath('id', $this->proyectoB->id);
    }
}

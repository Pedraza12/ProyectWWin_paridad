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

// El código de la ficha es la credencial de unión: las respuestas de
// similitudes no lo exponen a los roles no administradores, ni siquiera dentro
// del classGroup anidado de project1/project2. Los endpoints de fichas
// autorizados conservan el código para su dueño.
class SimilarityClassGroupCodigoTest extends TestCase
{
    use DatabaseTransactions;

    private const INCLUDE = 'project1.classGroup.program,project2.classGroup.program,project1.creator,project2.creator';

    private GeneralUser $aprendizA;
    private GeneralUser $aprendizB;
    private GeneralUser $instructor;
    private GeneralUser $admin;
    private ClassGroup $fichaA;
    private ClassGroup $fichaB;
    private Project $proyectoA;
    private Project $proyectoB;
    private Similarity $par;

    protected function setUp(): void
    {
        parent::setUp();

        // La base local trae similitudes del seed: el alcance debe ser
        // determinista (la transacción las restaura al terminar).
        Similarity::query()->delete();

        $programa = $this->programa();

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

        $this->fichaA = $this->ficha($programa, $instA->id, 'cod-' . substr(uniqid(), -8));
        $this->fichaB = $this->ficha($programa, $instB->id, 'cod-' . substr(uniqid(), -8));

        $this->aprendizA = $this->usuario('aprendiz');
        $this->aprendizB = $this->usuario('aprendiz');
        $this->admin = $this->usuario('admin');

        $this->aprendiz($this->aprendizA, $this->fichaA, $programa);
        $this->aprendiz($this->aprendizB, $this->fichaB, $programa);

        $this->proyectoA = $this->proyecto('Propuesta A', $this->aprendizA, $this->fichaA);
        $this->proyectoB = $this->proyecto('Propuesta B', $this->aprendizB, $this->fichaB);
        $this->par = $this->similaridad($this->proyectoA, $this->proyectoB);
    }

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Codigo',
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
            'codigo' => $codigo,
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
            'porcentaje' => 55,
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
        // El guard cachea el usuario entre peticiones del mismo test.
        $this->app['auth']->forgetGuards();
        return $this->withToken($token);
    }

    private function urlSimilitud(int $id): string
    {
        return '/v1/similarities/' . $id . '?' . http_build_query(['included' => self::INCLUDE]);
    }

    // Recorre el JSON y exige que ningún nodo classGroup incluya `codigo`.
    private function assertSinCodigoDeFicha(array $datos): void
    {
        foreach ($datos as $clave => $valor) {
            if ($clave === 'classGroup' && is_array($valor)) {
                $this->assertArrayNotHasKey(
                    'codigo',
                    $valor,
                    'El código de la ficha no debe exponerse en respuestas de similitudes.'
                );
            }
            if (is_array($valor)) {
                $this->assertSinCodigoDeFicha($valor);
            }
        }
    }

    public function test_el_aprendiz_no_recibe_el_codigo_en_el_listado_ni_en_el_detalle(): void
    {
        $listado = $this->como($this->aprendizA)
            ->getJson('/v1/similarities?' . http_build_query(['included' => self::INCLUDE]))
            ->assertOk()
            ->json();

        $item = collect($listado)->firstWhere('id', $this->par->id);
        $this->assertNotNull($item);
        // El resto de campos que usa la UI se conserva.
        $this->assertNotEmpty($item['project1']['classGroup']['nombre']);
        $this->assertSinCodigoDeFicha($item);

        $detalle = $this->como($this->aprendizA)
            ->getJson($this->urlSimilitud($this->par->id))
            ->assertOk()
            ->json();

        $this->assertNotEmpty($detalle['project1']['classGroup']['nombre']);
        $this->assertSinCodigoDeFicha($detalle);
    }

    public function test_la_vista_por_propuesta_tambien_oculta_el_codigo(): void
    {
        $respuesta = $this->como($this->aprendizA)
            ->getJson('/v1/similarities?' . http_build_query([
                'proyecto_id' => $this->proyectoA->id,
                'included' => self::INCLUDE,
            ]))
            ->assertOk()
            ->json();

        $item = collect($respuesta)->firstWhere('id', $this->par->id);
        $this->assertNotNull($item);
        $this->assertSinCodigoDeFicha($item);
    }

    public function test_el_instructor_no_recibe_el_codigo_y_su_ficha_lo_conserva(): void
    {
        $detalle = $this->como($this->instructor)
            ->getJson($this->urlSimilitud($this->par->id))
            ->assertOk()
            ->json();

        $this->assertNotEmpty($detalle['project1']['classGroup']['nombre']);
        $this->assertSinCodigoDeFicha($detalle);

        // Flujo legítimo: el dueño de la ficha sigue recibiendo su código.
        $this->como($this->instructor)
            ->getJson('/v1/class-groups/' . $this->fichaA->id)
            ->assertOk()
            ->assertJsonPath('codigo', $this->fichaA->codigo);

        // Y no puede leer el código de una ficha ajena.
        $this->como($this->instructor)
            ->getJson('/v1/class-groups/' . $this->fichaB->id)
            ->assertStatus(403);
    }

    public function test_el_aprendiz_conserva_el_codigo_de_su_ficha_en_el_endpoint_de_fichas(): void
    {
        $this->como($this->aprendizA)
            ->getJson('/v1/class-groups/' . $this->fichaA->id)
            ->assertOk()
            ->assertJsonPath('codigo', $this->fichaA->codigo);

        $this->como($this->aprendizA)
            ->getJson('/v1/class-groups/' . $this->fichaB->id)
            ->assertStatus(403);
    }

    public function test_el_admin_si_recibe_el_codigo_de_las_fichas(): void
    {
        $this->como($this->admin)
            ->getJson($this->urlSimilitud($this->par->id))
            ->assertOk()
            ->assertJsonPath('project1.classGroup.codigo', $this->fichaA->codigo)
            ->assertJsonPath('project2.classGroup.codigo', $this->fichaB->codigo);
    }
}

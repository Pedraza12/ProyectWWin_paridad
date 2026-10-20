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

// El código de la ficha es la credencial de unión: en las respuestas de
// propuestas solo se entrega al admin, al instructor de esa ficha y al aprendiz
// de su ficha actual. El `included` anidado no debe abrir otra ruta de acceso.
class ProjectClassGroupCodigoTest extends TestCase
{
    use DatabaseTransactions;

    private GeneralUser $aprendiz;
    private GeneralUser $contraparte;
    private GeneralUser $instructor;
    private GeneralUser $otroInstructor;
    private GeneralUser $admin;
    private Instructor $inst;
    private Instructor $instOtro;
    private ClassGroup $fichaActual;
    private ClassGroup $fichaAnterior;
    private ClassGroup $fichaContraparte;
    private Project $proyectoActual;
    private Project $proyectoAnterior;
    private Project $proyectoContraparte;
    private Project $proyectoAsignadoFuera;

    protected function setUp(): void
    {
        parent::setUp();

        // La base local trae similitudes del seed: el alcance debe ser
        // determinista (la transacción las restaura al terminar).
        Similarity::query()->delete();

        $programa = $this->programa();

        $this->aprendiz = $this->usuario('aprendiz');
        $this->contraparte = $this->usuario('aprendiz');
        $this->instructor = $this->usuario('instructor');
        $this->otroInstructor = $this->usuario('instructor');
        $this->admin = $this->usuario('admin');

        $this->inst = Instructor::create([
            'fecha_ingreso' => '2024-01-01',
            'id_usuario' => $this->instructor->id,
        ]);
        $this->instOtro = Instructor::create([
            'fecha_ingreso' => '2024-01-01',
            'id_usuario' => $this->otroInstructor->id,
        ]);

        $this->fichaActual = $this->ficha($programa, $this->inst->id, 'prj-act');
        $this->fichaAnterior = $this->ficha($programa, $this->inst->id, 'prj-ant');
        $this->fichaContraparte = $this->ficha($programa, $this->instOtro->id, 'prj-con');

        // El aprendiz estuvo en la ficha anterior y hoy pertenece a la actual:
        // su código anterior ya no debe viajar en las respuestas de propuestas.
        $this->aprendizEn($this->aprendiz, $this->fichaAnterior, $programa);
        $this->proyectoAnterior = $this->proyecto(
            'Propuesta de la ficha anterior',
            $this->aprendiz,
            $this->fichaAnterior,
            $this->inst->id
        );
        Apprentice::where('id_usuario', $this->aprendiz->id)
            ->update(['id_class_group' => $this->fichaActual->id]);

        $this->proyectoActual = $this->proyecto(
            'Propuesta de la ficha actual',
            $this->aprendiz,
            $this->fichaActual,
            $this->inst->id
        );

        $this->aprendizEn($this->contraparte, $this->fichaContraparte, $programa);
        $this->proyectoContraparte = $this->proyecto(
            'Propuesta de la contraparte',
            $this->contraparte,
            $this->fichaContraparte,
            $this->instOtro->id
        );
        // Asignada a otro instructor pero nacida en una ficha que no instrueye:
        // aparece en su listado, pero sin el código de esa ficha.
        $this->proyectoAsignadoFuera = $this->proyecto(
            'Propuesta asignada fuera de sus fichas',
            $this->contraparte,
            $this->fichaContraparte,
            $this->inst->id
        );

        $this->similaridad($this->proyectoActual, $this->proyectoContraparte);
    }

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Ficha',
            'correo' => $rol . '.' . uniqid() . '@codigo.local',
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

    private function ficha(TrainingProgram $programa, int $instructorId, string $prefijo): ClassGroup
    {
        return ClassGroup::create([
            'codigo' => $prefijo . '-' . uniqid(),
            'numero' => 'N-' . uniqid(),
            'nombre' => 'Ficha ' . $prefijo,
            'estado' => 'activo',
            'id_programa' => $programa->id,
            'id_instructor' => $instructorId,
        ]);
    }

    private function aprendizEn(GeneralUser $usuario, ClassGroup $ficha, TrainingProgram $programa): Apprentice
    {
        return Apprentice::create([
            'codigo' => 'AP-' . uniqid(),
            'id_usuario' => $usuario->id,
            'id_class_group' => $ficha->id,
            'id_programa' => $programa->id,
        ]);
    }

    private function proyecto(
        string $titulo,
        GeneralUser $creador,
        ClassGroup $ficha,
        int $instructorAsignado
    ): Project {
        return Project::create([
            'titulo' => $titulo,
            'resumen' => 'Resumen de ' . $titulo,
            'area_aplicacion' => 'Tecnología',
            'estado' => 'aprobado',
            'id_creador' => $creador->id,
            'id_instructor_asignado' => $instructorAsignado,
            'id_class_group' => $ficha->id,
        ]);
    }

    private function similaridad(Project $a, Project $b): Similarity
    {
        [$p1, $p2] = $a->id < $b->id ? [$a->id, $b->id] : [$b->id, $a->id];

        return Similarity::create([
            'id_proyecto_1' => $p1,
            'id_proyecto_2' => $p2,
            'porcentaje' => 70,
            'fecha' => now()->toDateString(),
        ]);
    }

    private function como(GeneralUser $usuario)
    {
        $token = $this->postJson('/v1/auth/login', [
            'correo' => $usuario->correo,
            'password' => '123456',
        ])->assertOk()->json('token');

        // El guard cachea el usuario entre peticiones del mismo test.
        $this->app['auth']->forgetGuards();

        return $this->withToken($token);
    }

    private function urlProyecto(int $id, string $included): string
    {
        return '/v1/projects/' . $id . '?' . http_build_query(['included' => $included]);
    }

    public function test_el_aprendiz_conserva_el_codigo_de_su_ficha_actual_y_redacta_la_anterior(): void
    {
        $listado = $this->como($this->aprendiz)
            ->getJson('/v1/projects?' . http_build_query(['included' => 'classGroup.program']))
            ->assertOk()
            ->json();

        $actual = collect($listado)->firstWhere('id', $this->proyectoActual->id);
        $this->assertNotNull($actual);
        $this->assertSame($this->fichaActual->codigo, $actual['classGroup']['codigo']);

        $anterior = collect($listado)->firstWhere('id', $this->proyectoAnterior->id);
        $this->assertNotNull($anterior);
        $this->assertArrayNotHasKey('codigo', $anterior['classGroup']);
        // El resto de datos de la ficha que usa la UI se conserva.
        $this->assertSame($this->fichaAnterior->nombre, $anterior['classGroup']['nombre']);

        $this->como($this->aprendiz)
            ->getJson($this->urlProyecto($this->proyectoActual->id, 'classGroup.program'))
            ->assertOk()
            ->assertJsonPath('classGroup.codigo', $this->fichaActual->codigo);

        $this->como($this->aprendiz)
            ->getJson($this->urlProyecto($this->proyectoAnterior->id, 'classGroup.program'))
            ->assertOk()
            ->assertJsonPath('classGroup.id', $this->fichaAnterior->id)
            ->assertJsonMissingPath('classGroup.codigo');
    }

    public function test_el_aprendiz_redacta_el_codigo_de_la_contraparte_de_una_similitud(): void
    {
        $respuesta = $this->como($this->aprendiz)
            ->getJson($this->urlProyecto(
                $this->proyectoContraparte->id,
                'classGroup.program,apprentices.generalUser'
            ))
            ->assertOk();

        $respuesta
            ->assertJsonPath('classGroup.id', $this->fichaContraparte->id)
            ->assertJsonPath('classGroup.program.id', $this->fichaContraparte->id_programa)
            ->assertJsonMissingPath('classGroup.codigo');

        // La ficha propia sigue entregando su código con el mismo include.
        $this->como($this->aprendiz)
            ->getJson($this->urlProyecto(
                $this->proyectoActual->id,
                'classGroup.program,apprentices.generalUser'
            ))
            ->assertOk()
            ->assertJsonPath('classGroup.codigo', $this->fichaActual->codigo);
    }

    public function test_el_instructor_conserva_el_codigo_de_sus_fichas_y_redacta_el_de_otra(): void
    {
        $listado = $this->como($this->instructor)
            ->getJson('/v1/projects?' . http_build_query(['included' => 'classGroup.program']))
            ->assertOk()
            ->json();

        $propio = collect($listado)->firstWhere('id', $this->proyectoActual->id);
        $this->assertNotNull($propio);
        $this->assertSame($this->fichaActual->codigo, $propio['classGroup']['codigo']);

        // Asignada a él, pero la ficha pertenece a otro instructor.
        $asignadoFuera = collect($listado)->firstWhere('id', $this->proyectoAsignadoFuera->id);
        $this->assertNotNull($asignadoFuera);
        $this->assertArrayNotHasKey('codigo', $asignadoFuera['classGroup']);

        $this->como($this->instructor)
            ->getJson($this->urlProyecto($this->proyectoActual->id, 'classGroup.program'))
            ->assertOk()
            ->assertJsonPath('classGroup.codigo', $this->fichaActual->codigo);

        // Ficha anterior del mismo instructor: sigue siendo suya.
        $this->como($this->instructor)
            ->getJson($this->urlProyecto($this->proyectoAnterior->id, 'classGroup.program'))
            ->assertOk()
            ->assertJsonPath('classGroup.codigo', $this->fichaAnterior->codigo);

        // Mismo programa, ficha de otro instructor: lectura permitida sin código.
        $this->como($this->instructor)
            ->getJson($this->urlProyecto($this->proyectoContraparte->id, 'classGroup.program'))
            ->assertOk()
            ->assertJsonPath('classGroup.id', $this->fichaContraparte->id)
            ->assertJsonMissingPath('classGroup.codigo');
    }

    public function test_el_admin_conserva_los_codigos_en_listado_y_detalle(): void
    {
        $listado = $this->como($this->admin)
            ->getJson('/v1/projects?' . http_build_query(['included' => 'classGroup.program']))
            ->assertOk()
            ->json();

        $esperados = [
            $this->proyectoActual->id => $this->fichaActual->codigo,
            $this->proyectoAnterior->id => $this->fichaAnterior->codigo,
            $this->proyectoContraparte->id => $this->fichaContraparte->codigo,
            $this->proyectoAsignadoFuera->id => $this->fichaContraparte->codigo,
        ];

        foreach ($esperados as $idProyecto => $codigo) {
            $item = collect($listado)->firstWhere('id', $idProyecto);
            $this->assertNotNull($item, 'El admin debe listar la propuesta ' . $idProyecto);
            $this->assertSame($codigo, $item['classGroup']['codigo']);
        }

        $this->como($this->admin)
            ->getJson($this->urlProyecto($this->proyectoContraparte->id, 'classGroup.program'))
            ->assertOk()
            ->assertJsonPath('classGroup.codigo', $this->fichaContraparte->codigo);
    }

    public function test_el_included_anidado_no_recupera_el_codigo_redactado(): void
    {
        $variantes = [
            'classGroup',
            'classGroup.program',
            'classGroup.program,apprentices.generalUser,creator',
        ];

        foreach ($variantes as $included) {
            $this->como($this->aprendiz)
                ->getJson($this->urlProyecto($this->proyectoContraparte->id, $included))
                ->assertOk()
                ->assertJsonPath('classGroup.id', $this->fichaContraparte->id)
                ->assertJsonMissingPath('classGroup.codigo');

            // La redacción depende de la ficha, no del include: la propia
            // conserva el código incluso con la variante más anidada.
            $this->como($this->aprendiz)
                ->getJson($this->urlProyecto($this->proyectoActual->id, $included))
                ->assertOk()
                ->assertJsonPath('classGroup.codigo', $this->fichaActual->codigo);
        }
    }

    public function test_los_flujos_legitimos_de_ficha_conservan_el_codigo(): void
    {
        // El endpoint autorizado de fichas sigue entregando el código propio.
        $this->como($this->aprendiz)
            ->getJson('/v1/class-groups/' . $this->fichaActual->id)
            ->assertOk()
            ->assertJsonPath('codigo', $this->fichaActual->codigo);

        // La ficha anterior ya no es su ficha actual: sigue bloqueada.
        $this->como($this->aprendiz)
            ->getJson('/v1/class-groups/' . $this->fichaAnterior->id)
            ->assertStatus(403);

        // Previsualización por código (flujo de unión) intacta.
        $this->como($this->aprendiz)
            ->getJson('/v1/apprentices/me/ficha/codigo/' . urlencode($this->fichaActual->codigo))
            ->assertOk()
            ->assertJsonPath('codigo', $this->fichaActual->codigo);

        // El instructor conserva el código de las fichas que instrueye.
        $this->como($this->instructor)
            ->getJson('/v1/class-groups/' . $this->fichaActual->id)
            ->assertOk()
            ->assertJsonPath('codigo', $this->fichaActual->codigo);
    }
}

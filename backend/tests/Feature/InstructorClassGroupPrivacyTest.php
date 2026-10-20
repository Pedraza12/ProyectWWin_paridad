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

// `included=classGroups` en instructores no puede ampliar el alcance: el
// aprendiz solo ve su ficha, el instructor las que instrueye y el admin todas.
// El código de cada ficha sigue viajando únicamente en esas fichas autorizadas.
class InstructorClassGroupPrivacyTest extends TestCase
{
    use DatabaseTransactions;

    private GeneralUser $instructor1;
    private GeneralUser $instructor2;
    private GeneralUser $aprendiz;
    private GeneralUser $admin;
    private Instructor $inst1;
    private Instructor $inst2;
    private ClassGroup $fichaA1;
    private ClassGroup $fichaA2;
    private ClassGroup $fichaB1;

    protected function setUp(): void
    {
        parent::setUp();

        $programa = $this->programa();

        $this->instructor1 = $this->usuario('instructor');
        $this->instructor2 = $this->usuario('instructor');
        $this->aprendiz = $this->usuario('aprendiz');
        $this->admin = $this->usuario('admin');

        $this->inst1 = Instructor::create([
            'fecha_ingreso' => '2024-01-01',
            'id_usuario' => $this->instructor1->id,
        ]);
        $this->inst2 = Instructor::create([
            'fecha_ingreso' => '2024-01-01',
            'id_usuario' => $this->instructor2->id,
        ]);

        // El instructor 1 tiene dos fichas; solo una es la ficha del aprendiz.
        $this->fichaA1 = $this->ficha($programa, $this->inst1->id, 'ins-a1');
        $this->fichaA2 = $this->ficha($programa, $this->inst1->id, 'ins-a2');
        $this->fichaB1 = $this->ficha($programa, $this->inst2->id, 'ins-b1');

        Apprentice::create([
            'codigo' => 'AP-' . uniqid(),
            'id_usuario' => $this->aprendiz->id,
            'id_class_group' => $this->fichaA1->id,
            'id_programa' => $programa->id,
        ]);
    }

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Instructor',
            'correo' => $rol . '.' . uniqid() . '@fichas.local',
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

    private function instructorPorUsuario(array $lista, int $idUsuario): ?array
    {
        return collect($lista)->first(
            fn ($item) => (int) ($item['id_usuario'] ?? 0) === $idUsuario
        );
    }

    private function idsDeClassGroups(array $instructor): array
    {
        return collect($instructor['classGroups'] ?? [])
            ->pluck('id')
            ->map(fn ($id) => (int) $id)
            ->sort()
            ->values()
            ->all();
    }

    private function idsClassGroupsAnidados(array $datos): array
    {
        $ids = [];

        foreach ($datos as $clave => $valor) {
            if (!is_array($valor)) continue;

            if ($clave === 'classGroups') {
                foreach ($valor as $ficha) {
                    if (is_array($ficha) && isset($ficha['id'])) {
                        $ids[] = (int) $ficha['id'];
                    }
                }
            }

            $ids = array_merge($ids, $this->idsClassGroupsAnidados($valor));
        }

        return $ids;
    }

    private function codigoDe(array $instructor, ClassGroup $ficha): ?string
    {
        $item = collect($instructor['classGroups'] ?? [])
            ->first(fn ($f) => (int) ($f['id'] ?? 0) === (int) $ficha->id);

        return $item['codigo'] ?? null;
    }

    public function test_el_aprendiz_solo_recibe_su_ficha_en_class_groups_anidados(): void
    {
        $listado = $this->como($this->aprendiz)
            ->getJson('/v1/instructors?' . http_build_query(['included' => 'classGroups']))
            ->assertOk()
            ->json();

        $instructor = $this->instructorPorUsuario($listado, $this->instructor1->id);
        $this->assertNotNull($instructor);
        $this->assertSame([$this->fichaA1->id], $this->idsDeClassGroups($instructor));
        $this->assertSame($this->fichaA1->codigo, $this->codigoDe($instructor, $this->fichaA1));

        // La otra ficha del mismo instructor no viaja por ninguna ruta.
        $this->assertNotContains($this->fichaA2->id, $this->idsClassGroupsAnidados($listado));
        $this->assertNotContains($this->fichaB1->id, $this->idsClassGroupsAnidados($listado));

        $detalle = $this->como($this->aprendiz)
            ->getJson('/v1/instructors/' . $this->inst1->id . '?' . http_build_query(['included' => 'classGroups']))
            ->assertOk()
            ->json();

        $this->assertSame([$this->fichaA1->id], $this->idsDeClassGroups($detalle));
        $this->assertSame($this->fichaA1->codigo, $this->codigoDe($detalle, $this->fichaA1));
        $this->assertNotContains($this->fichaA2->id, $this->idsClassGroupsAnidados($detalle));
    }

    public function test_el_instructor_recibe_solo_sus_fichas_en_class_groups_anidados(): void
    {
        $listado = $this->como($this->instructor1)
            ->getJson('/v1/instructors?' . http_build_query(['included' => 'classGroups']))
            ->assertOk()
            ->json();

        $instructor = $this->instructorPorUsuario($listado, $this->instructor1->id);
        $this->assertNotNull($instructor);
        $this->assertSame(
            [$this->fichaA1->id, $this->fichaA2->id],
            $this->idsDeClassGroups($instructor)
        );
        $this->assertSame($this->fichaA1->codigo, $this->codigoDe($instructor, $this->fichaA1));
        $this->assertSame($this->fichaA2->codigo, $this->codigoDe($instructor, $this->fichaA2));
        $this->assertNotContains($this->fichaB1->id, $this->idsClassGroupsAnidados($listado));

        $detalle = $this->como($this->instructor1)
            ->getJson('/v1/instructors/' . $this->inst1->id . '?' . http_build_query(['included' => 'classGroups']))
            ->assertOk()
            ->json();
        $this->assertSame(
            [$this->fichaA1->id, $this->fichaA2->id],
            $this->idsDeClassGroups($detalle)
        );

        // El perfil de otro instructor sigue fuera de alcance, con o sin include.
        $this->como($this->instructor1)
            ->getJson('/v1/instructors/' . $this->inst2->id . '?' . http_build_query(['included' => 'classGroups']))
            ->assertStatus(403);
    }

    public function test_el_admin_recibe_todas_las_fichas_en_class_groups_anidados(): void
    {
        $listado = $this->como($this->admin)
            ->getJson('/v1/instructors?' . http_build_query(['included' => 'classGroups']))
            ->assertOk()
            ->json();

        $primero = $this->instructorPorUsuario($listado, $this->instructor1->id);
        $this->assertNotNull($primero);
        $this->assertSame(
            [$this->fichaA1->id, $this->fichaA2->id],
            $this->idsDeClassGroups($primero)
        );
        $this->assertSame($this->fichaA1->codigo, $this->codigoDe($primero, $this->fichaA1));
        $this->assertSame($this->fichaA2->codigo, $this->codigoDe($primero, $this->fichaA2));

        $segundo = $this->instructorPorUsuario($listado, $this->instructor2->id);
        $this->assertNotNull($segundo);
        $this->assertSame([$this->fichaB1->id], $this->idsDeClassGroups($segundo));
        $this->assertSame($this->fichaB1->codigo, $this->codigoDe($segundo, $this->fichaB1));

        // Pantalla legítima de selección/directorio: mismo shape de siempre.
        $directorio = $this->como($this->admin)
            ->getJson('/v1/instructors?' . http_build_query(['included' => 'generalUser']))
            ->assertOk()
            ->json();
        $item = $this->instructorPorUsuario($directorio, $this->instructor1->id);
        $this->assertNotNull($item);
        $this->assertSame($this->instructor1->id, $item['generalUser']['id']);
        $this->assertArrayNotHasKey('classGroups', $item);
    }

    public function test_cambiar_included_no_elude_la_autorizacion_de_fichas(): void
    {
        foreach (['classGroups', 'generalUser,classGroups', 'classGroups,projects'] as $included) {
            $respuesta = $this->como($this->aprendiz)
                ->getJson('/v1/instructors?' . http_build_query(['included' => $included]))
                ->assertOk()
                ->json();

            $this->assertNotContains(
                $this->fichaA2->id,
                $this->idsClassGroupsAnidados($respuesta),
                'El aprendiz no debe recuperar fichas ajenas con included=' . $included
            );
            $this->assertNotContains($this->fichaB1->id, $this->idsClassGroupsAnidados($respuesta));
        }

        // El detalle de un instructor ajeno sigue bloqueado aunque se pida
        // `classGroups`: el include no sustituye la autorización.
        $this->como($this->aprendiz)
            ->getJson('/v1/instructors/' . $this->inst2->id . '?' . http_build_query(['included' => 'classGroups']))
            ->assertStatus(403);

        $this->como($this->aprendiz)
            ->getJson('/v1/instructors/' . $this->inst1->id . '?' . http_build_query(['included' => 'classGroups']))
            ->assertOk()
            ->assertJsonPath('id', $this->inst1->id);
    }

    public function test_el_directorio_conserva_su_shape_sin_class_groups(): void
    {
        // `instructores.listar()` del frontend pide solo generalUser.
        $listado = $this->como($this->aprendiz)
            ->getJson('/v1/instructors?' . http_build_query(['included' => 'generalUser']))
            ->assertOk()
            ->json();

        $instructor = $this->instructorPorUsuario($listado, $this->instructor1->id);
        $this->assertNotNull($instructor);
        $this->assertSame($this->instructor1->id, $instructor['generalUser']['id']);
        $this->assertArrayNotHasKey('classGroups', $instructor);

        // Sin `included`, el listado tampoco agrega la relación.
        $sinInclude = $this->como($this->aprendiz)
            ->getJson('/v1/instructors')
            ->assertOk()
            ->json();
        $instructor = $this->instructorPorUsuario($sinInclude, $this->instructor1->id);
        $this->assertNotNull($instructor);
        $this->assertArrayNotHasKey('classGroups', $instructor);
    }
}

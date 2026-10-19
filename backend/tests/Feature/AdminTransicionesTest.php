<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\AuditLog;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\Notification;
use App\Models\Project;
use App\Models\ProjectHistory;
use App\Models\Similarity;
use App\Models\TrainingProgram;
use App\Similarity\Recomputador;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// D-1: coherencia entre el estado de la propuesta y su análisis de similitud.
// Las retrotransiciones administrativas `rechazado→aprobado`,
// `rechazado→pendiente` y `pendiente→borrador` responden 422 y producen efecto
// cero (estado, pares, motor, huella, historial, auditoría y notificaciones).
// El camino válido es el reenvío canónico (archiva, re-analiza y permite
// aprobar después).
class AdminTransicionesTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Trans',
            'correo' => $rol . '.' . uniqid() . '@admin.local',
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

    private function escenario(): array
    {
        $red = KnowledgeNetwork::create(['nombre' => 'Red ' . uniqid()]);
        $programa = TrainingProgram::create([
            'nombre' => 'Programa ' . uniqid(),
            'nivel' => 'Tecnologo',
            'knowledge_network_id' => $red->id,
        ]);
        $instructorUser = $this->usuario('instructor');
        $instructor = Instructor::create(['fecha_ingreso' => '2024-01-01', 'id_usuario' => $instructorUser->id]);
        $ficha = ClassGroup::create([
            'codigo' => 'tr-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha transiciones',
            'estado' => 'activo',
            'id_programa' => $programa->id,
            'id_instructor' => $instructor->id,
        ]);
        $aprendizEn = function () use ($ficha): GeneralUser {
            $user = $this->usuario('aprendiz');
            Apprentice::create([
                'codigo' => 'AP-' . uniqid(),
                'id_usuario' => $user->id,
                'id_class_group' => $ficha->id,
                'id_programa' => $ficha->id_programa,
            ]);
            return $user;
        };

        return [$instructorUser, $aprendizEn(), $aprendizEn()];
    }

    private function contenido(string $titulo): array
    {
        return [
            'titulo' => $titulo,
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes de trazabilidad.',
            'area_aplicacion' => 'Desarrollo Web',
            'objetivo_general' => 'Controlar los inventarios del almacén con alertas por stock mínimo.',
            'objetivos_especificos' => [
                'Registrar entradas y salidas de productos.',
                'Generar reportes de trazabilidad por lote.',
            ],
        ];
    }

    private function crearYEnviar(GeneralUser $aprendiz, string $titulo): int
    {
        $creada = $this->como($aprendiz)
            ->postJson('/v1/projects', $this->contenido($titulo) + ['id_creador' => $aprendiz->id])
            ->assertCreated();
        $id = (int) $creada->json('id');
        $this->como($aprendiz)->postJson('/v1/projects/' . $id . '/enviar')->assertOk();

        return $id;
    }

    private function parDe(int $id): Similarity
    {
        return Similarity::where(function ($q) use ($id) {
            $q->where('id_proyecto_1', $id)->orWhere('id_proyecto_2', $id);
        })->firstOrFail();
    }

    private function snapshot(int $id): array
    {
        return [
            'historial' => ProjectHistory::where('id_proyecto', $id)->count(),
            'auditoria' => AuditLog::where('entidad', 'projects')->where('entidad_id', $id)->count(),
            'notificaciones' => Notification::count(),
        ];
    }

    public function test_b1_admin_no_aprueba_directamente_una_rechazada_con_efecto_cero(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();

        $corpus = $this->crearYEnviar($ap1, 'Inventarios corpus');
        $this->como($inst)->putJson('/v1/projects/' . $corpus, ['estado' => 'aprobado'])->assertOk();

        $rechazada = $this->crearYEnviar($ap2, 'Inventarios rechazada');
        $par = $this->parDe($rechazada);
        $this->assertTrue((bool) $par->vigente);
        $this->como($inst)->putJson('/v1/projects/' . $rechazada, ['estado' => 'rechazado'])->assertOk();
        $this->assertFalse((bool) $par->fresh()->vigente);

        // El motor no debe invocarse en la transición bloqueada.
        $this->mock(Recomputador::class, function ($m) {
            $m->shouldNotReceive('archivar');
            $m->shouldNotReceive('detectar');
        });

        $simsAntes = Similarity::count();
        $antes = $this->snapshot($rechazada);

        $this->como($this->usuario('admin'))
            ->putJson('/v1/projects/' . $rechazada, ['estado' => 'aprobado'])
            ->assertStatus(422)
            ->assertJsonPath('message', 'Una propuesta rechazada no puede aprobarse directamente: debe reenviarse y analizarse primero.');

        $this->assertSame('rechazado', Project::find($rechazada)->estado);
        $this->assertFalse((bool) $par->fresh()->vigente);
        $this->assertSame($simsAntes, Similarity::count());
        $this->assertSame($antes, $this->snapshot($rechazada));
    }

    public function test_b2_admin_no_devuelve_una_rechazada_a_revision_con_efecto_cero(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();

        $corpus = $this->crearYEnviar($ap1, 'Inventarios corpus');
        $this->como($inst)->putJson('/v1/projects/' . $corpus, ['estado' => 'aprobado'])->assertOk();

        $rechazada = $this->crearYEnviar($ap2, 'Inventarios rechazada');
        $par = $this->parDe($rechazada);
        $this->como($inst)->putJson('/v1/projects/' . $rechazada, ['estado' => 'rechazado'])->assertOk();
        $this->assertFalse((bool) $par->fresh()->vigente);

        $this->mock(Recomputador::class, function ($m) {
            $m->shouldNotReceive('archivar');
            $m->shouldNotReceive('detectar');
        });

        $simsAntes = Similarity::count();
        $antes = $this->snapshot($rechazada);

        $this->como($this->usuario('admin'))
            ->putJson('/v1/projects/' . $rechazada, ['estado' => 'pendiente'])
            ->assertStatus(422)
            ->assertJsonPath('message', 'Una propuesta rechazada no puede volver a revisión sin reenviarse y analizarse.');

        $this->assertSame('rechazado', Project::find($rechazada)->estado);
        $this->assertFalse((bool) $par->fresh()->vigente);
        $this->assertSame($simsAntes, Similarity::count());
        $this->assertSame($antes, $this->snapshot($rechazada));
    }

    public function test_b3_admin_no_devuelve_una_pendiente_a_borrador_con_efecto_cero(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();

        $corpus = $this->crearYEnviar($ap1, 'Inventarios corpus');
        $this->como($inst)->putJson('/v1/projects/' . $corpus, ['estado' => 'aprobado'])->assertOk();

        $pendiente = $this->crearYEnviar($ap2, 'Inventarios pendiente');
        $par = $this->parDe($pendiente);
        $this->assertTrue((bool) $par->vigente);

        $this->mock(Recomputador::class, function ($m) {
            $m->shouldNotReceive('archivar');
            $m->shouldNotReceive('detectar');
        });

        $simsAntes = Similarity::count();
        $antes = $this->snapshot($pendiente);

        $this->como($this->usuario('admin'))
            ->putJson('/v1/projects/' . $pendiente, ['estado' => 'borrador'])
            ->assertStatus(422)
            ->assertJsonPath('message', 'Una propuesta enviada no puede volver a borrador; permanece en revisión o se rechaza.');

        $this->assertSame('pendiente', Project::find($pendiente)->estado);
        $this->assertTrue((bool) $par->fresh()->vigente);
        $this->assertSame($simsAntes, Similarity::count());
        $this->assertSame($antes, $this->snapshot($pendiente));
    }

    public function test_el_reenvio_canonico_conserva_historial_y_permite_aprobar(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();

        $corpus = $this->crearYEnviar($ap1, 'Inventarios corpus');
        $this->como($inst)->putJson('/v1/projects/' . $corpus, ['estado' => 'aprobado'])->assertOk();

        $id = $this->crearYEnviar($ap2, 'Inventarios ciclo');
        $original = $this->parDe($id);
        $this->assertTrue((bool) $original->vigente);
        $this->como($inst)->putJson('/v1/projects/' . $id, ['estado' => 'rechazado'])->assertOk();
        $this->assertFalse((bool) $original->fresh()->vigente);

        // El aprendiz corrige y reenvía por el canal canónico.
        $this->como($ap2)
            ->putJson('/v1/projects/' . $id, ['titulo' => 'Inventarios ciclo con diferencial'])
            ->assertOk();
        $this->como($ap2)->postJson('/v1/projects/' . $id . '/enviar')->assertOk();

        $this->assertSame('pendiente', Project::find($id)->estado);
        $this->assertFalse((bool) $original->fresh()->vigente);

        $nueva = Similarity::where(function ($q) use ($id) {
            $q->where('id_proyecto_1', $id)->orWhere('id_proyecto_2', $id);
        })->where('vigente', true)->first();
        $this->assertNotNull($nueva);
        $this->assertNotSame($original->id, $nueva->id);

        // Y la aprobación administrativa ya es posible desde `pendiente`.
        $this->como($this->usuario('admin'))
            ->putJson('/v1/projects/' . $id, ['estado' => 'aprobado'])
            ->assertOk();
        $this->assertSame('aprobado', Project::find($id)->estado);
    }

    public function test_instructor_sigue_sin_poder_aprobar_una_rechazada(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();

        $corpus = $this->crearYEnviar($ap1, 'Inventarios corpus');
        $this->como($inst)->putJson('/v1/projects/' . $corpus, ['estado' => 'aprobado'])->assertOk();

        $rechazada = $this->crearYEnviar($ap2, 'Inventarios rechazada');
        $this->como($inst)->putJson('/v1/projects/' . $rechazada, ['estado' => 'rechazado'])->assertOk();

        // Regla existente del instructor (sin cambios por D-1, sin ampliación).
        $this->como($inst)
            ->putJson('/v1/projects/' . $rechazada, ['estado' => 'aprobado'])
            ->assertStatus(422)
            ->assertJsonPath('message', 'Transición no permitida desde el estado actual.');

        $this->assertSame('rechazado', Project::find($rechazada)->estado);
    }
}

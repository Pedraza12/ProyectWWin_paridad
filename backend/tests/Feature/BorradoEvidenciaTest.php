<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\AuditLog;
use App\Models\ClassGroup;
use App\Models\Comment;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\MotorConfig;
use App\Models\Notification;
use App\Models\Project;
use App\Models\ProjectHistory;
use App\Models\Similarity;
use App\Models\TrainingProgram;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// D-2b: la evidencia de similitud es compartida (una fila conecta dos
// propuestas). `DELETE /projects/{id}` responde 409 —sin efecto alguno—
// cuando la propuesta tiene cualquier par (vigente o archivado), para todos
// los actores autorizados. Sin pares, el borrado actual se conserva.
class BorradoEvidenciaTest extends TestCase
{
    use DatabaseTransactions;

    private const MSG = 'No puedes eliminar una propuesta con similitudes compartidas: la evidencia pertenece también a las demás propuestas.';

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Borrado',
            'correo' => $rol . '.' . uniqid() . '@borrado.local',
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
            'codigo' => 'be-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha borrado',
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

        return [$instructorUser, $aprendizEn(), $aprendizEn(), $ficha];
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

    private function parejaConCorpus(GeneralUser $instructorUser, GeneralUser $aprendiz, string $titulo): int
    {
        $corpus = $this->crearYEnviar($aprendiz, 'Inventarios corpus ' . uniqid());
        $this->como($instructorUser)->putJson('/v1/projects/' . $corpus, ['estado' => 'aprobado'])->assertOk();

        return $this->crearYEnviar($aprendiz, $titulo);
    }

    private function snapshot(int $id): array
    {
        return [
            'propuesta' => Project::whereKey($id)->exists(),
            'pares' => Similarity::where(function ($q) use ($id) {
                $q->where('id_proyecto_1', $id)->orWhere('id_proyecto_2', $id);
            })->count(),
            'comentarios' => Comment::where('id_proyecto', $id)->count(),
            'historial' => ProjectHistory::where('id_proyecto', $id)->count(),
            'notificaciones' => Notification::count(),
            'auditoria' => AuditLog::where('entidad', 'projects')->where('entidad_id', $id)->count(),
        ];
    }

    public function test_aprendiz_no_borra_una_rechazada_con_par_historico(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();
        $id = $this->parejaConCorpus($inst, $ap2, 'Inventarios rechazada');
        $par = $this->parDe($id);
        $this->como($inst)->putJson('/v1/projects/' . $id, ['estado' => 'rechazado'])->assertOk();
        $this->assertFalse((bool) $par->fresh()->vigente);

        $antes = $this->snapshot($id);

        $this->como($ap2)
            ->deleteJson('/v1/projects/' . $id)
            ->assertStatus(409)
            ->assertJsonPath('message', self::MSG);

        $this->assertSame($antes, $this->snapshot($id));
        $this->assertSame('rechazado', Project::find($id)->estado);
        $this->assertFalse((bool) $par->fresh()->vigente);
    }

    public function test_aprendiz_borra_una_rechazada_sin_pares(): void
    {
        [$inst, $ap1, $ap2, $ficha] = $this->escenario();

        $proyecto = Project::create([
            'titulo' => 'Rechazada sin evidencia',
            'resumen' => 'Resumen de una propuesta rechazada sin pares de similitud.',
            'area_aplicacion' => 'Tecnología',
            'estado' => 'rechazado',
            'id_creador' => $ap2->id,
            'id_class_group' => $ficha->id,
        ]);

        $this->como($ap2)->deleteJson('/v1/projects/' . $proyecto->id)->assertOk();
        $this->assertDatabaseMissing('projects', ['id' => $proyecto->id]);
    }

    public function test_admin_no_borra_una_pendiente_con_par_vigente(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();
        $id = $this->parejaConCorpus($inst, $ap2, 'Inventarios pendiente');
        $par = $this->parDe($id);
        $this->assertTrue((bool) $par->vigente);

        $antes = $this->snapshot($id);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/projects/' . $id)
            ->assertStatus(409)
            ->assertJsonPath('message', self::MSG);

        $this->assertSame($antes, $this->snapshot($id));
        $this->assertSame('pendiente', Project::find($id)->estado);
        $this->assertTrue((bool) $par->fresh()->vigente);
    }

    public function test_admin_no_borra_una_aprobada_con_par(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();
        $id = $this->parejaConCorpus($inst, $ap2, 'Inventarios aprobada');
        $this->como($inst)->putJson('/v1/projects/' . $id, ['estado' => 'aprobado'])->assertOk();
        $par = $this->parDe($id);

        $antes = $this->snapshot($id);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/projects/' . $id)
            ->assertStatus(409)
            ->assertJsonPath('message', self::MSG);

        $this->assertSame($antes, $this->snapshot($id));
        $this->assertSame('aprobado', Project::find($id)->estado);
        $this->assertTrue((bool) $par->fresh()->vigente);
    }

    public function test_admin_no_borra_una_rechazada_con_solo_historicos(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();
        $id = $this->parejaConCorpus($inst, $ap2, 'Inventarios histórica');
        $par = $this->parDe($id);
        $this->como($inst)->putJson('/v1/projects/' . $id, ['estado' => 'rechazado'])->assertOk();
        $this->assertFalse((bool) $par->fresh()->vigente);

        $antes = $this->snapshot($id);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/projects/' . $id)
            ->assertStatus(409)
            ->assertJsonPath('message', self::MSG);

        $this->assertSame($antes, $this->snapshot($id));
        $this->assertSame('rechazado', Project::find($id)->estado);
        $this->assertFalse((bool) $par->fresh()->vigente);
    }

    public function test_admin_borra_una_propuesta_sin_pares(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();

        $creada = $this->como($ap2)
            ->postJson('/v1/projects', $this->contenido('Borrador sin evidencia') + ['id_creador' => $ap2->id])
            ->assertCreated();
        $id = (int) $creada->json('id');

        $this->como($this->usuario('admin'))->deleteJson('/v1/projects/' . $id)->assertOk();
        $this->assertDatabaseMissing('projects', ['id' => $id]);
    }

    public function test_no_se_borra_con_vigentes_e_historicos_simultaneos(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();
        $id = $this->parejaConCorpus($inst, $ap2, 'Inventarios ciclo');

        // Rechazo (archiva) y reenvío con cambio (nueva detección vigente).
        $this->como($inst)->putJson('/v1/projects/' . $id, ['estado' => 'rechazado'])->assertOk();
        $this->como($ap2)
            ->putJson('/v1/projects/' . $id, ['titulo' => 'Inventarios ciclo con diferencial'])
            ->assertOk();
        $this->como($ap2)->postJson('/v1/projects/' . $id . '/enviar')->assertOk();

        $this->assertGreaterThanOrEqual(1, Similarity::where(function ($q) use ($id) {
            $q->where('id_proyecto_1', $id)->orWhere('id_proyecto_2', $id);
        })->where('vigente', true)->count());
        $this->assertGreaterThanOrEqual(1, Similarity::where(function ($q) use ($id) {
            $q->where('id_proyecto_1', $id)->orWhere('id_proyecto_2', $id);
        })->where('vigente', false)->count());

        $antes = $this->snapshot($id);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/projects/' . $id)
            ->assertStatus(409)
            ->assertJsonPath('message', self::MSG);

        $this->assertSame($antes, $this->snapshot($id));
        $this->assertSame('pendiente', Project::find($id)->estado);
    }

    public function test_ambos_extremos_del_par_bloquean_el_borrado(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();
        $b = $this->parejaConCorpus($inst, $ap2, 'Inventarios extremo B');
        $par = $this->parDe($b);
        $a = (int) $par->id_proyecto_1 === $b ? (int) $par->id_proyecto_2 : (int) $par->id_proyecto_1;

        $admin = $this->usuario('admin');
        $this->como($admin)->deleteJson('/v1/projects/' . $a)->assertStatus(409);
        $this->como($admin)->deleteJson('/v1/projects/' . $b)->assertStatus(409);

        $this->assertDatabaseHas('similarities', ['id' => $par->id]);
        $this->assertDatabaseHas('projects', ['id' => $a]);
        $this->assertDatabaseHas('projects', ['id' => $b]);
    }

    public function test_guardas_p1_y_roles_sin_regresiones(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();
        $id = $this->parejaConCorpus($inst, $ap2, 'Inventarios guardas');

        // P1: el creador no borra una propuesta en revisión (mensaje propio).
        $this->como($ap2)
            ->deleteJson('/v1/projects/' . $id)
            ->assertStatus(409)
            ->assertJsonPath('message', 'No puedes eliminar una propuesta en revisión o aprobada.');

        // El instructor sigue sin poder borrar.
        $this->como($inst)
            ->deleteJson('/v1/projects/' . $id)
            ->assertStatus(403)
            ->assertJsonPath('message', 'No puedes eliminar esta propuesta.');

        // Un aprendiz ajeno tampoco.
        $this->como($ap1)
            ->deleteJson('/v1/projects/' . $id)
            ->assertStatus(403);

        $this->assertDatabaseHas('projects', ['id' => $id]);
    }

    public function test_borrado_permitido_inicializa_y_bloquea_el_motor_sin_fila_previa(): void
    {
        [$inst, $ap1, $ap2, $ficha] = $this->escenario();
        $this->assertSame(0, MotorConfig::count(), 'premisa: motor sin fila previa');

        // Propuesta sin pares (creación directa: no pasa por el motor).
        $proyecto = Project::create([
            'titulo' => 'Rechazada génesis',
            'resumen' => 'Resumen de una propuesta rechazada sin pares de similitud.',
            'area_aplicacion' => 'Tecnología',
            'estado' => 'rechazado',
            'id_creador' => $ap2->id,
            'id_class_group' => $ficha->id,
        ]);

        DB::connection()->flushQueryLog();
        DB::connection()->enableQueryLog();
        $this->como($this->usuario('admin'))->deleteJson('/v1/projects/' . $proyecto->id)->assertOk();
        $mutex = collect(DB::connection()->getQueryLog())
            ->pluck('query')
            ->contains(fn ($q) => str_contains($q, 'motor_configs') && str_contains($q, 'for update'));
        DB::connection()->disableQueryLog();

        $this->assertTrue($mutex, 'destroy debe inicializar y bloquear el motor.');
        $this->assertDatabaseMissing('projects', ['id' => $proyecto->id]);
        $this->assertSame(1, MotorConfig::count(), 'la fila del motor queda inicializada con la operación.');
    }

    public function test_con_pares_y_sin_fila_previa_responde_409_sin_persistir_config(): void
    {
        [$inst, $ap1, $ap2, $ficha] = $this->escenario();
        $this->assertSame(0, MotorConfig::count(), 'premisa: motor sin fila previa');

        $y = Project::create([
            'titulo' => 'Inventarios génesis corpus',
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
            'area_aplicacion' => 'Desarrollo Web',
            'estado' => 'aprobado',
            'id_creador' => $ap1->id,
            'id_class_group' => $ficha->id,
        ]);
        $x = Project::create([
            'titulo' => 'Inventarios génesis objetivo',
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
            'area_aplicacion' => 'Desarrollo Web',
            'estado' => 'pendiente',
            'id_creador' => $ap2->id,
            'id_class_group' => $ficha->id,
        ]);
        $par = Similarity::create([
            'id_proyecto_1' => min((int) $y->id, (int) $x->id),
            'id_proyecto_2' => max((int) $y->id, (int) $x->id),
            'porcentaje' => 90,
            'fecha' => now()->toDateString(),
        ]);
        $antes = $this->snapshot($x->id);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/projects/' . $x->id)
            ->assertStatus(409)
            ->assertJsonPath('message', self::MSG);

        $this->assertSame($antes, $this->snapshot($x->id));
        $this->assertTrue((bool) $par->fresh()->vigente);
        $this->assertSame(0, MotorConfig::count(), 'el 409 revierte la inicialización del motor.');
    }
}

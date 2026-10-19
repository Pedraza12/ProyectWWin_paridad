<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\AuditLog;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\Project;
use App\Models\ProjectHistory;
use App\Models\Similarity;
use App\Models\TrainingProgram;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// Reglas administrativas de propuestas (D-3): las propuestas creadas por un
// admin nacen en borrador, siguen el flujo normal de envío/análisis y ninguna
// ruta administrativa salta el motor para llegar a aprobado.
class AdminPropuestasTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Admin',
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
            'codigo' => 'ap-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha admin',
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
        $admin = $this->usuario('admin');

        return [$instructorUser, $aprendizEn(), $aprendizEn(), $ficha, $admin];
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
        $id = $creada->json('id');
        $this->como($aprendiz)->postJson('/v1/projects/' . $id . '/enviar')->assertOk();

        return $id;
    }

    private function paresDe(int $id): int
    {
        return Similarity::where(function ($q) use ($id) {
            $q->where('id_proyecto_1', $id)->orWhere('id_proyecto_2', $id);
        })->count();
    }

    private function auditoriaEnvio(int $id): int
    {
        return AuditLog::where('entidad', 'projects')->where('entidad_id', $id)
            ->where('accion', 'enviar_propuesta')->count();
    }

    public function test_control_positivo_el_envio_normal_invoca_el_motor(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();

        $corpus = $this->crearYEnviar($ap1, 'Inventarios Admin Corpus');
        $this->como($inst)->putJson('/v1/projects/' . $corpus, ['estado' => 'aprobado'])->assertOk();

        $control = $this->crearYEnviar($ap2, 'Inventarios Admin Control');

        $this->assertGreaterThanOrEqual(1, $this->paresDe($control), 'El motor crea pares en el flujo normal');
        $this->assertNotNull(Project::find($control)->huella_envio, 'La huella se fija al enviar');
        $this->assertContains('enviada', ProjectHistory::where('id_proyecto', $control)->pluck('accion')->all());
        $this->assertSame(1, $this->auditoriaEnvio($control), 'Auditoría de envío presente');
    }

    public function test_creacion_admin_con_estado_aprobado_nace_en_borrador_y_sigue_el_flujo(): void
    {
        [$inst, $ap1, $ap2, $ficha, $admin] = $this->escenario();

        $corpus = $this->crearYEnviar($ap1, 'Inventarios Admin Corpus');
        $this->como($inst)->putJson('/v1/projects/' . $corpus, ['estado' => 'aprobado'])->assertOk();

        $creada = $this->como($admin)->postJson('/v1/projects', $this->contenido('Inventarios Admin A') + [
            'estado' => 'aprobado', 'id_creador' => $ap2->id, 'id_class_group' => $ficha->id,
        ])->assertCreated();
        $id = (int) $creada->json('id');

        $proyecto = Project::find($id);
        $this->assertSame('borrador', $proyecto->estado, 'D-3: nace en borrador aunque el cliente pida aprobado');
        $this->assertNull($proyecto->huella_envio);
        $this->assertSame(0, $this->paresDe($id), 'Sin pares antes de enviar');
        $this->assertSame(0, $this->auditoriaEnvio($id));

        // Flujo normal: el admin envía y el motor analiza.
        $this->como($admin)->postJson('/v1/projects/' . $id . '/enviar')->assertOk();

        $this->assertSame('pendiente', Project::find($id)->estado);
        $this->assertGreaterThanOrEqual(1, $this->paresDe($id), 'El motor analiza al enviar');
        $this->assertNotNull(Project::find($id)->huella_envio);
        $this->assertContains('enviada', ProjectHistory::where('id_proyecto', $id)->pluck('accion')->all());
        $this->assertSame(1, $this->auditoriaEnvio($id));
    }

    public function test_creacion_admin_con_estado_pendiente_nace_en_borrador_y_no_se_aprueba_sin_envio(): void
    {
        [$inst, $ap1, $ap2, $ficha, $admin] = $this->escenario();

        $creada = $this->como($admin)->postJson('/v1/projects', $this->contenido('Inventarios Admin B') + [
            'estado' => 'pendiente', 'id_creador' => $ap2->id, 'id_class_group' => $ficha->id,
        ])->assertCreated();
        $id = (int) $creada->json('id');
        $this->assertSame('borrador', Project::find($id)->estado, 'D-3: nace en borrador aunque el cliente pida pendiente');

        // El instructor no puede aprobar un borrador (transición inválida).
        $this->como($inst)->putJson('/v1/projects/' . $id, ['estado' => 'aprobado'])->assertStatus(422);

        // Flujo normal: enviar (motor) → instructor aprueba.
        $this->como($admin)->postJson('/v1/projects/' . $id . '/enviar')->assertOk();
        $this->assertSame('pendiente', Project::find($id)->estado);
        $this->como($inst)->putJson('/v1/projects/' . $id, ['estado' => 'aprobado'])->assertOk();
        $this->assertSame('aprobado', Project::find($id)->estado);
        $this->assertSame(1, $this->auditoriaEnvio($id));
    }

    public function test_admin_no_salta_el_borrador_por_update(): void
    {
        [$inst, $ap1, $ap2, $ficha, $admin] = $this->escenario();

        $creada = $this->como($admin)->postJson('/v1/projects', $this->contenido('Inventarios Admin C') + [
            'id_creador' => $ap2->id, 'id_class_group' => $ficha->id,
        ])->assertCreated();
        $id = (int) $creada->json('id');

        foreach (['aprobado', 'pendiente', 'rechazado'] as $estadoProhibido) {
            $this->como($admin)->putJson('/v1/projects/' . $id, ['estado' => $estadoProhibido])
                ->assertStatus(422);
        }

        $proyecto = Project::find($id);
        $this->assertSame('borrador', $proyecto->estado, 'El borrador solo sale por el flujo de envío');
        $this->assertNull($proyecto->huella_envio);
        $this->assertSame(0, $this->paresDe($id));
        $this->assertSame(0, $this->auditoriaEnvio($id));
    }
}

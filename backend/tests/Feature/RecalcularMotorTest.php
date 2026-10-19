<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\AuditLog;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\MotorConfig;
use App\Models\Project;
use App\Models\Similarity;
use App\Models\TrainingProgram;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// C-01 (recalibración del motor): POST /v1/similarities/recalculate es
// admin-only, responde EXACTAMENTE {eliminadas, creadas} y archiva los pares
// que ya no cumplen el umbral vigente (vigente=false, sin borrarlos) para
// recrear la versión del contenido actual. La acción queda auditada como
// `recalibrar_motor` sobre la entidad `similarities`.
class RecalcularMotorTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Recalculo',
            'correo' => $rol . '.' . uniqid() . '@recalculo.local',
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

    private function programa(): TrainingProgram
    {
        $red = KnowledgeNetwork::create(['nombre' => 'Red ' . uniqid()]);

        return TrainingProgram::create([
            'nombre' => 'Programa ' . uniqid(),
            'nivel' => 'Tecnologo',
            'knowledge_network_id' => $red->id,
        ]);
    }

    private function ficha(TrainingProgram $programa, GeneralUser $instructorUser): ClassGroup
    {
        $instructor = Instructor::create([
            'fecha_ingreso' => '2024-01-01',
            'id_usuario' => $instructorUser->id,
        ]);

        return ClassGroup::create([
            'codigo' => 'rc-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha recálculo',
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

    // Texto plantilla "Inventarios": score alto comprobado (CierreAuditoriaTest).
    private function propuestaCompleta(GeneralUser $creador, ClassGroup $ficha, string $estado): Project
    {
        return Project::create([
            'titulo' => 'Inventarios del almacén',
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes de trazabilidad.',
            'area_aplicacion' => 'Tecnología',
            'objetivo_general' => 'Controlar los inventarios del almacén con alertas por stock mínimo.',
            'objetivos_especificos' => [
                'Registrar entradas y salidas de productos.',
                'Generar reportes de trazabilidad por lote.',
            ],
            'estado' => $estado,
            'id_creador' => $creador->id,
            'id_class_group' => $ficha->id,
        ]);
    }

    public function test_aprendiz_no_puede_recalcular_el_motor(): void
    {
        $aprendiz = $this->usuario('aprendiz');
        $antes = [
            'auditoria' => AuditLog::count(),
            'similitudes' => Similarity::count(),
            'motor' => MotorConfig::count(),
        ];

        $this->como($aprendiz)
            ->postJson('/v1/similarities/recalculate')
            ->assertStatus(403)
            ->assertJsonPath('message', 'Sin permiso para esta acción.');

        // La guarda del rol corta ANTES del controlador: ni auditoría ni BD
        // (la recalibración habría inicializado la fila del motor).
        $this->assertSame($antes['auditoria'], AuditLog::count());
        $this->assertSame($antes['similitudes'], Similarity::count());
        $this->assertSame($antes['motor'], MotorConfig::count());
    }

    public function test_admin_recalibra_y_la_respuesta_expone_solo_eliminadas_y_creadas(): void
    {
        $admin = $this->usuario('admin');

        $resp = $this->como($admin)
            ->postJson('/v1/similarities/recalculate')
            ->assertOk()
            ->assertJsonStructure(['eliminadas', 'creadas']);

        // Exactamente dos claves: el `total` interno NO se filtra a la API.
        $resp->assertJsonCount(2);
        $this->assertSame(0, $resp->json('eliminadas'));
        $this->assertSame(0, $resp->json('creadas'));
    }

    public function test_recalcular_archiva_el_par_invalido_y_crea_la_version_vigente(): void
    {
        $programa = $this->programa();
        $instructorUser = $this->usuario('instructor');
        $ficha = $this->ficha($programa, $instructorUser);
        $a = $this->aprendizEn($ficha);
        $b = $this->aprendizEn($ficha);

        $pA = $this->propuestaCompleta($a, $ficha, 'aprobado');
        $pB = $this->propuestaCompleta($b, $ficha, 'aprobado');

        // Par pre-existente por debajo del umbral vigente (20% < 30%): la
        // recalibración debe archivarlo (NO borrarlo) y recrear el par con el
        // contenido actual (score alto del texto plantilla).
        $p1 = min((int) $pA->id, (int) $pB->id);
        $p2 = max((int) $pA->id, (int) $pB->id);
        $viejo = Similarity::create([
            'id_proyecto_1' => $p1,
            'id_proyecto_2' => $p2,
            'porcentaje' => 20,
            'fecha' => now()->toDateString(),
        ]);
        $this->assertTrue((bool) $viejo->fresh()->vigente, 'premisa: el par previo nace vigente');

        $admin = $this->usuario('admin');
        $resp = $this->como($admin)
            ->postJson('/v1/similarities/recalculate')
            ->assertOk()
            ->assertJsonStructure(['eliminadas', 'creadas']);

        $this->assertSame(1, $resp->json('eliminadas'));
        $this->assertSame(1, $resp->json('creadas'));

        // Evidencia histórica conservada + versión vigente nueva (dos filas).
        $this->assertFalse((bool) $viejo->fresh()->vigente);
        $filas = Similarity::where('id_proyecto_1', $p1)->where('id_proyecto_2', $p2)->get();
        $this->assertCount(2, $filas, 'el par archivado y el recreado conviven');
        $nueva = $filas->firstWhere('vigente', true);
        $this->assertNotNull($nueva);
        $this->assertNotSame((int) $viejo->id, (int) $nueva->id);
        $this->assertGreaterThanOrEqual(30, (int) $nueva->porcentaje);

        // Auditoría de la recalibración (actor = admin autenticado).
        $log = AuditLog::where('accion', 'recalibrar_motor')->latest('id')->first();
        $this->assertNotNull($log);
        $this->assertSame('similarities', $log->entidad);
        $this->assertNull($log->entidad_id);
        $this->assertSame((int) $admin->id, (int) $log->id_usuario);
        $this->assertSame(1, $log->detalle['eliminadas']);
        $this->assertSame(1, $log->detalle['creadas']);
        $this->assertEqualsWithDelta(0.30, $log->detalle['umbral'], 0.0001);
        $this->assertSame(12, $log->detalle['meses']);
    }
}

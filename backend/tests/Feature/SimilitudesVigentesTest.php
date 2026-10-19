<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\AuditLog;
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

// Coherencia F8: las similitudes de una versión rechazada se conservan como
// evidencia (vigente=false) y el reenvío genera la detección del contenido
// nuevo; el proyecto aprobado es estado final del flujo normal y la
// intervención excepcional del admin queda auditada y en el historial.
class SimilitudesVigentesTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Sim',
            'correo' => $rol . '.' . uniqid() . '@correo.com',
            'password' => Hash::make('123456'),
            'rol' => $rol,
            'estado' => true,
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
            'fecha_ingreso' => now()->toDateString(),
            'id_usuario' => $instructorUser->id,
        ]);

        return ClassGroup::create([
            'codigo' => 'sv-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha similitudes',
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

    private function propuesta(GeneralUser $aprendiz, string $titulo): array
    {
        return [
            'titulo' => $titulo,
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
            'area_aplicacion' => 'Desarrollo Web',
            'objetivo_general' => 'Controlar los inventarios del almacén con alertas por stock mínimo.',
            'objetivos_especificos' => [
                'Registrar entradas y salidas de productos.',
                'Generar reportes de trazabilidad por lote.',
            ],
            'id_creador' => $aprendiz->id,
        ];
    }

    private function crearYEnviar(GeneralUser $aprendiz, string $titulo): int
    {
        $creada = $this->como($aprendiz)
            ->postJson('/v1/projects', $this->propuesta($aprendiz, $titulo))
            ->assertCreated();
        $id = $creada->json('id');
        $this->como($aprendiz)->postJson('/v1/projects/' . $id . '/enviar')->assertOk();
        return $id;
    }

    private function parDe(int $id): Similarity
    {
        return Similarity::where(function ($q) use ($id) {
            $q->where('id_proyecto_1', $id)->orWhere('id_proyecto_2', $id);
        })->firstOrFail();
    }

    private function escenario(): array
    {
        $programa = $this->programa();
        $instructorUser = $this->usuario('instructor');
        $ficha = $this->ficha($programa, $instructorUser);
        return [$instructorUser, $this->aprendizEn($ficha), $this->aprendizEn($ficha)];
    }

    public function test_rechazar_conserva_la_similitud_como_historica(): void
    {
        [$instructorUser, $maria, $ana] = $this->escenario();

        $idMaria = $this->crearYEnviar($maria, 'Inventarios María');
        $this->como($instructorUser)->putJson('/v1/projects/' . $idMaria, ['estado' => 'aprobado'])->assertOk();

        $idAna = $this->crearYEnviar($ana, 'Inventarios Ana');
        $par = $this->parDe($idAna);
        $this->assertTrue((bool) $par->vigente);

        // Rechazo: el par deja de estar vigente, pero no se borra.
        $this->como($instructorUser)->putJson('/v1/projects/' . $idAna, ['estado' => 'rechazado'])->assertOk();

        $par->refresh();
        $this->assertFalse((bool) $par->vigente);
        $this->assertDatabaseHas('similarities', ['id' => $par->id]);

        // El aprendiz la consulta como evidencia de la versión anterior.
        $historial = $this->como($ana)
            ->getJson('/v1/similarities?proyecto_id=' . $idAna . '&historial=1')
            ->assertOk()->json();
        $this->assertContains($par->id, collect($historial)->pluck('id')->all());

        // Y no aparece entre las vigentes.
        $vigentes = $this->como($ana)
            ->getJson('/v1/similarities?proyecto_id=' . $idAna)
            ->assertOk()->json();
        $this->assertNotContains($par->id, collect($vigentes)->pluck('id')->all());
    }

    public function test_el_dueno_de_la_aprobada_abre_la_evidencia_historica(): void
    {
        [$instructorUser, $maria, $ana] = $this->escenario();

        $idMaria = $this->crearYEnviar($maria, 'Inventarios María');
        $this->como($instructorUser)->putJson('/v1/projects/' . $idMaria, ['estado' => 'aprobado'])->assertOk();

        $idAna = $this->crearYEnviar($ana, 'Inventarios Ana');
        $par = $this->parDe($idAna);

        // La contraparte se rechaza: el par queda como evidencia histórica.
        $this->como($instructorUser)->putJson('/v1/projects/' . $idAna, ['estado' => 'rechazado'])->assertOk();
        $this->assertFalse((bool) $par->fresh()->vigente);

        // El dueño del proyecto aprobado puede abrir el detalle histórico.
        $this->como($maria)->getJson('/v1/similarities/' . $par->id)->assertOk();
    }

    public function test_reenviar_genera_nueva_deteccion_y_conserva_la_anterior(): void
    {
        [$instructorUser, $maria, $ana] = $this->escenario();

        $idMaria = $this->crearYEnviar($maria, 'Inventarios María');
        $this->como($instructorUser)->putJson('/v1/projects/' . $idMaria, ['estado' => 'aprobado'])->assertOk();
        $idAna = $this->crearYEnviar($ana, 'Inventarios Ana');
        $this->como($instructorUser)->putJson('/v1/projects/' . $idAna, ['estado' => 'rechazado'])->assertOk();

        $historica = $this->parDe($idAna);

        // Ana corrige el contenido y reenvía.
        $this->como($ana)->putJson('/v1/projects/' . $idAna, [
            'titulo' => 'Inventarios Ana con diferencial',
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
        ])->assertOk();
        $this->como($ana)->postJson('/v1/projects/' . $idAna . '/enviar')->assertOk();

        // La anterior sigue existiendo como histórica y aparece una nueva vigente.
        $this->assertFalse((bool) $historica->fresh()->vigente);
        $nueva = Similarity::where(function ($q) use ($idAna) {
            $q->where('id_proyecto_1', $idAna)->orWhere('id_proyecto_2', $idAna);
        })->where('vigente', true)->first();
        $this->assertNotNull($nueva);
        $this->assertNotSame($historica->id, $nueva->id);
    }

    public function test_editar_una_pendiente_archiva_la_deteccion_anterior_y_detecta_la_nueva(): void
    {
        [$instructorUser, $maria, $ana] = $this->escenario();

        $idMaria = $this->crearYEnviar($maria, 'Inventarios María');
        $this->como($instructorUser)->putJson('/v1/projects/' . $idMaria, ['estado' => 'aprobado'])->assertOk();

        $idAna = $this->crearYEnviar($ana, 'Inventarios Ana');
        $par = $this->parDe($idAna);
        $this->assertTrue((bool) $par->vigente);

        // Ana edita su propuesta EN REVISIÓN con contenido aún parecido.
        $this->como($ana)->putJson('/v1/projects/' . $idAna, [
            'titulo' => 'Inventarios Ana con diferencial',
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
        ])->assertOk();

        // La detección anterior queda como evidencia histórica (no se borra).
        $this->assertFalse((bool) $par->fresh()->vigente);

        // Y aparece una nueva detección vigente del contenido editado.
        $nueva = Similarity::where(function ($q) use ($idAna) {
            $q->where('id_proyecto_1', $idAna)->orWhere('id_proyecto_2', $idAna);
        })->where('vigente', true)->first();
        $this->assertNotNull($nueva);
        $this->assertNotSame($par->id, $nueva->id);
    }

    public function test_editar_una_pendiente_como_instructor_archiva_y_redetecta(): void
    {
        [$instructorUser, $maria, $ana] = $this->escenario();

        $idMaria = $this->crearYEnviar($maria, 'Inventarios María');
        $this->como($instructorUser)->putJson('/v1/projects/' . $idMaria, ['estado' => 'aprobado'])->assertOk();

        $idAna = $this->crearYEnviar($ana, 'Inventarios Ana');
        $par = $this->parDe($idAna);
        $this->assertTrue((bool) $par->vigente);

        // El instructor edita el CONTENIDO de la pendiente (sin cambiar estado).
        $this->como($instructorUser)->putJson('/v1/projects/' . $idAna, [
            'titulo' => 'Inventarios Ana con diferencial',
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
        ])->assertOk();

        // La detección anterior queda como evidencia histórica.
        $this->assertFalse((bool) $par->fresh()->vigente);

        // Y aparece una nueva detección vigente del contenido editado.
        $nueva = Similarity::where(function ($q) use ($idAna) {
            $q->where('id_proyecto_1', $idAna)->orWhere('id_proyecto_2', $idAna);
        })->where('vigente', true)->first();
        $this->assertNotNull($nueva);
        $this->assertNotSame($par->id, $nueva->id);
    }

    public function test_editar_una_pendiente_como_admin_archiva_y_redetecta(): void
    {
        [$instructorUser, $maria, $ana] = $this->escenario();

        $idMaria = $this->crearYEnviar($maria, 'Inventarios María');
        $this->como($instructorUser)->putJson('/v1/projects/' . $idMaria, ['estado' => 'aprobado'])->assertOk();

        $idAna = $this->crearYEnviar($ana, 'Inventarios Ana');
        $par = $this->parDe($idAna);
        $this->assertTrue((bool) $par->vigente);

        // El admin edita el CONTENIDO de la pendiente (sin cambiar estado).
        $admin = $this->usuario('admin');
        $this->como($admin)->putJson('/v1/projects/' . $idAna, [
            'titulo' => 'Inventarios Ana con diferencial',
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
        ])->assertOk();

        // La detección anterior queda como evidencia histórica.
        $this->assertFalse((bool) $par->fresh()->vigente);

        // Y aparece una nueva detección vigente del contenido editado.
        $nueva = Similarity::where(function ($q) use ($idAna) {
            $q->where('id_proyecto_1', $idAna)->orWhere('id_proyecto_2', $idAna);
        })->where('vigente', true)->first();
        $this->assertNotNull($nueva);
        $this->assertNotSame($par->id, $nueva->id);
    }

    public function test_el_instructor_no_edita_un_proyecto_aprobado(): void
    {
        [$instructorUser, $maria] = $this->escenario();
        $id = $this->crearYEnviar($maria, 'Inventarios María');
        $this->como($instructorUser)->putJson('/v1/projects/' . $id, ['estado' => 'aprobado'])->assertOk();

        $this->como($instructorUser)
            ->putJson('/v1/projects/' . $id, ['titulo' => 'Intento de edición'])
            ->assertStatus(422);

        $this->assertSame('aprobado', Project::find($id)->estado);
        $this->assertSame('Inventarios María', Project::find($id)->titulo);
    }

    public function test_la_intervencion_admin_queda_auditada_y_en_el_historial(): void
    {
        [$instructorUser, $maria] = $this->escenario();
        $id = $this->crearYEnviar($maria, 'Inventarios María');
        $this->como($instructorUser)->putJson('/v1/projects/' . $id, ['estado' => 'aprobado'])->assertOk();

        $admin = $this->usuario('admin');
        $this->como($admin)
            ->putJson('/v1/projects/' . $id, ['titulo' => 'Inventarios María (intervención)'])
            ->assertOk();

        $this->assertSame('pendiente', Project::find($id)->estado);
        $this->assertDatabaseHas('project_histories', [
            'id_proyecto' => $id,
            'accion' => 'devuelta_revision',
        ]);
        $log = AuditLog::where('accion', 'revisar_propuesta')
            ->where('entidad_id', $id)->latest('id')->first();
        $this->assertNotNull($log);
        $this->assertTrue((bool) ($log->detalle['intervencion_admin'] ?? false));
    }
}

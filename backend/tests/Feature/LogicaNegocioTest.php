<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\Project;
use App\Models\TrainingProgram;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// Invariantes de lógica de negocio: ficha/programa del aprendiz, creación y
// edición de propuestas, cambio de rol y borrado de perfiles.
class LogicaNegocioTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Log',
            'correo' => $rol . '.' . uniqid() . '@test.local',
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

    private function ficha(TrainingProgram $programa, string $estado = 'activo'): ClassGroup
    {
        $instructor = Instructor::create([
            'fecha_ingreso' => '2024-01-01',
            'id_usuario' => $this->usuario('instructor')->id,
        ]);

        return ClassGroup::create([
            'codigo' => 'log-' . uniqid(),
            'nombre' => 'Ficha lógica',
            'estado' => $estado,
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

    private function payloadPropuesta(string $titulo = 'Propuesta lógica'): array
    {
        return [
            'titulo' => $titulo,
            'resumen' => 'Resumen suficientemente largo para la propuesta de prueba.',
            'area_aplicacion' => 'Tecnología',
        ];
    }

    // ---------------------------------------------------------------- Crear

    public function test_crear_propuesta_sin_ficha_falla(): void
    {
        $aprendiz = $this->usuario('aprendiz'); // sin fila de aprendiz

        $this->como($aprendiz)
            ->postJson('/v1/projects', $this->payloadPropuesta())
            ->assertStatus(422);
    }

    public function test_crear_propuesta_en_ficha_finalizada_falla(): void
    {
        $aprendiz = $this->aprendizEn($this->ficha($this->programa(), 'finalizado'));

        $this->como($aprendiz)
            ->postJson('/v1/projects', $this->payloadPropuesta())
            ->assertStatus(422);
    }

    public function test_crear_propuesta_fuerza_la_ficha_del_aprendiz(): void
    {
        $programa = $this->programa();
        $fichaPropia = $this->ficha($programa);
        $otraFicha = $this->ficha($programa);
        $aprendiz = $this->aprendizEn($fichaPropia);

        // Intenta colarla en otra ficha: el backend la fuerza a la suya.
        $res = $this->como($aprendiz)
            ->postJson('/v1/projects', array_merge($this->payloadPropuesta(), [
                'id_class_group' => $otraFicha->id,
            ]))
            ->assertStatus(201);

        $this->assertSame($fichaPropia->id, (int) $res->json('id_class_group'));
        $this->assertSame($fichaPropia->id_instructor, (int) $res->json('id_instructor_asignado'));
    }

    public function test_admin_no_crea_propuesta_a_nombre_de_un_no_aprendiz(): void
    {
        $admin = $this->usuario('admin');
        $instructor = $this->usuario('instructor');

        $this->como($admin)
            ->postJson('/v1/projects', array_merge($this->payloadPropuesta(), [
                'id_creador' => $instructor->id,
            ]))
            ->assertStatus(422);
    }

    // ---------------------------------------------------------------- Editar

    public function test_editar_propuesta_no_cambia_ficha_ni_creador(): void
    {
        $programa = $this->programa();
        $ficha = $this->ficha($programa);
        $otra = $this->ficha($programa);
        $aprendiz = $this->aprendizEn($ficha);

        $project = Project::create([
            'titulo' => 'Original',
            'resumen' => 'Resumen original de la propuesta de prueba.',
            'area_aplicacion' => 'Tecnología',
            'estado' => 'borrador',
            'id_creador' => $aprendiz->id,
            'id_class_group' => $ficha->id,
        ]);

        $this->como($aprendiz)
            ->putJson('/v1/projects/' . $project->id, [
                'titulo' => 'Editada',
                'resumen' => 'Resumen editado de la propuesta de prueba.',
                'area_aplicacion' => 'Tecnología',
                'id_creador' => $aprendiz->id,
                'id_class_group' => $otra->id, // intento de mover
            ])
            ->assertOk();

        $this->assertSame($ficha->id, (int) $project->fresh()->id_class_group);
        $this->assertSame($aprendiz->id, (int) $project->fresh()->id_creador);
    }

    public function test_editar_contenido_de_aprobada_por_admin_la_devuelve_a_pendiente(): void
    {
        $programa = $this->programa();
        $ficha = $this->ficha($programa);
        $aprendiz = $this->aprendizEn($ficha);
        $admin = $this->usuario('admin');

        $project = Project::create([
            'titulo' => 'Aprobada',
            'resumen' => 'Resumen de la propuesta aprobada de prueba.',
            'area_aplicacion' => 'Tecnología',
            'estado' => 'aprobado',
            'id_creador' => $aprendiz->id,
            'id_class_group' => $ficha->id,
        ]);

        $this->como($admin)
            ->putJson('/v1/projects/' . $project->id, [
                'titulo' => 'Aprobada (corregida)',
                'resumen' => 'Resumen de la propuesta aprobada de prueba.',
                'area_aplicacion' => 'Tecnología',
                'id_creador' => $aprendiz->id,
                'estado' => 'aprobado',
            ])
            ->assertOk();

        $this->assertSame('pendiente', $project->fresh()->estado);
    }

    public function test_admin_no_aprueba_directamente_una_rechazada(): void
    {
        $programa = $this->programa();
        $ficha = $this->ficha($programa);
        $aprendiz = $this->aprendizEn($ficha);
        $admin = $this->usuario('admin');

        $project = Project::create([
            'titulo' => 'Rechazada',
            'resumen' => 'Resumen de la propuesta rechazada de prueba.',
            'area_aplicacion' => 'Tecnología',
            'estado' => 'rechazado',
            'id_creador' => $aprendiz->id,
            'id_class_group' => $ficha->id,
        ]);

        // D-1: la aprobación directa desde `rechazado` rompería la coherencia
        // con el análisis (quedaría sin detección vigente); el camino válido
        // es el reenvío del aprendiz, que re-analiza el contenido.
        $this->como($admin)
            ->putJson('/v1/projects/' . $project->id, [
                'titulo' => 'Rechazada',
                'resumen' => 'Resumen de la propuesta rechazada de prueba.',
                'area_aplicacion' => 'Tecnología',
                'id_creador' => $aprendiz->id,
                'estado' => 'aprobado',
            ])
            ->assertStatus(422)
            ->assertJsonPath('message', 'Una propuesta rechazada no puede aprobarse directamente: debe reenviarse y analizarse primero.');

        $this->assertSame('rechazado', $project->fresh()->estado);
    }

    // ---------------------------------------------------------------- Rol / perfil

    public function test_borrar_perfil_de_aprendiz_con_historial_borra_el_perfil(): void
    {
        $programa = $this->programa();
        $ficha = $this->ficha($programa);
        $aprendiz = $this->aprendizEn($ficha);
        $admin = $this->usuario('admin');

        Project::create([
            'titulo' => 'Con historial',
            'resumen' => 'Resumen de la propuesta con historial.',
            'area_aplicacion' => 'Tecnología',
            'estado' => 'pendiente',
            'id_creador' => $aprendiz->id,
            'id_class_group' => $ficha->id,
        ]);

        $fila = Apprentice::where('id_usuario', $aprendiz->id)->firstOrFail();

        // Admin sin restricciones: borra el perfil (las propuestas quedan).
        $this->como($admin)
            ->deleteJson('/v1/apprentices/' . $fila->id)
            ->assertOk();
        $this->assertDatabaseMissing('apprentices', ['id' => $fila->id]);
    }

    public function test_cambiar_rol_de_aprendiz_lo_desvincula_de_la_ficha(): void
    {
        $programa = $this->programa();
        $ficha = $this->ficha($programa);
        $aprendiz = $this->aprendizEn($ficha);
        $admin = $this->usuario('admin');

        $this->como($admin)
            ->putJson('/v1/general-users/' . $aprendiz->id, [
                'nombre' => $aprendiz->nombre,
                'apellido' => $aprendiz->apellido,
                'correo' => $aprendiz->correo,
                'rol' => 'instructor',
            ])
            ->assertOk();

        $fila = Apprentice::where('id_usuario', $aprendiz->id)->first();
        $this->assertNull(optional($fila)->id_class_group);
    }

    // ---------------------------------------------------------------- Ficha / programa

    public function test_unirse_a_ficha_actualiza_el_programa_del_aprendiz(): void
    {
        $ficha = $this->ficha($this->programa());
        $aprendiz = $this->usuario('aprendiz');
        Apprentice::create([
            'codigo' => 'AP-' . uniqid(),
            'id_usuario' => $aprendiz->id,
            'id_class_group' => null,
            'id_programa' => $this->programa()->id,
        ]);

        $this->como($aprendiz)
            ->postJson('/v1/apprentices/me/ficha', ['codigo' => $ficha->codigo])
            ->assertOk();

        $this->assertSame($ficha->id_programa, (int) Apprentice::where('id_usuario', $aprendiz->id)->value('id_programa'));
    }

    public function test_cambiar_programa_de_ficha_sincroniza_a_los_aprendices(): void
    {
        $programaA = $this->programa();
        $programaB = $this->programa();
        $ficha = $this->ficha($programaA);
        $aprendiz = $this->aprendizEn($ficha);
        $admin = $this->usuario('admin');

        $this->como($admin)
            ->putJson('/v1/class-groups/' . $ficha->id, [
                'codigo' => $ficha->codigo,
                'numero' => '9802',
                'nombre' => $ficha->nombre,
                'estado' => 'activo',
                'id_programa' => $programaB->id,
                'id_instructor' => $ficha->id_instructor,
            ])
            ->assertOk();

        $this->assertSame($programaB->id, (int) Apprentice::where('id_usuario', $aprendiz->id)->value('id_programa'));
    }
}

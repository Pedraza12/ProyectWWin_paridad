<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\Notification;
use App\Models\Project;
use App\Models\Similarity;
use App\Models\TrainingProgram;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// Admin sin restricciones: puede borrar entidades con dependientes (cascada
// transaccional). Solo se conservan dos salvaguardas de sistema: nadie borra su
// propia cuenta y no se puede dejar el sistema sin administradores activos.
class DeleteGuardsTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol, bool $estado = true): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Guarda',
            'correo' => $rol . '.' . uniqid() . '@test.local',
            'password' => Hash::make('123456'),
            'rol' => $rol,
            'estado' => $estado,
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

    private function ficha(?TrainingProgram $programa = null): ClassGroup
    {
        $user = $this->usuario('instructor');
        $instructor = Instructor::create(['fecha_ingreso' => '2024-01-01', 'id_usuario' => $user->id]);

        return ClassGroup::create([
            'codigo' => 'gd-' . uniqid(),
            'nombre' => 'Ficha guarda',
            'estado' => 'activo',
            'id_programa' => ($programa ?: $this->programa())->id,
            'id_instructor' => $instructor->id,
        ]);
    }

    private function proyecto(GeneralUser $creador, ?ClassGroup $ficha = null): Project
    {
        return Project::create([
            'titulo' => 'Propuesta guarda',
            'resumen' => 'Resumen de la propuesta de prueba.',
            'area_aplicacion' => 'Tecnología',
            'estado' => 'pendiente',
            'id_creador' => $creador->id,
            'id_class_group' => $ficha?->id,
        ]);
    }

    public function test_admin_borra_una_ficha_con_datos_la_anula_sin_perder_historial(): void
    {
        $admin = $this->usuario('admin');
        $ficha = $this->ficha();
        $aprendizUser = $this->usuario('aprendiz');
        $aprendiz = Apprentice::create([
            'codigo' => 'AP-' . uniqid(),
            'id_usuario' => $aprendizUser->id,
            'id_class_group' => $ficha->id,
            'id_programa' => $ficha->id_programa,
        ]);
        $proyecto = $this->proyecto($aprendizUser, $ficha);

        $this->como($admin)->deleteJson('/v1/class-groups/' . $ficha->id)->assertOk();

        // La ficha con historial no se borra: se anula y se conserva todo.
        $this->assertDatabaseHas('class_groups', ['id' => $ficha->id, 'estado' => 'anulada']);
        $this->assertDatabaseHas('apprentices', ['id' => $aprendiz->id]);
        $this->assertDatabaseHas('projects', ['id' => $proyecto->id]);
    }

    public function test_admin_borra_un_usuario_con_propuestas_en_cascada(): void
    {
        $admin = $this->usuario('admin');
        $autor = $this->usuario('aprendiz');
        $proyecto = $this->proyecto($autor);

        $this->como($admin)->deleteJson('/v1/general-users/' . $autor->id)->assertOk();

        $this->assertDatabaseMissing('general_users', ['id' => $autor->id]);
        $this->assertDatabaseMissing('projects', ['id' => $proyecto->id]);
    }

    public function test_admin_borra_un_instructor_con_fichas_en_cascada(): void
    {
        $admin = $this->usuario('admin');
        $instructorUser = $this->usuario('instructor');
        $instructor = Instructor::create(['fecha_ingreso' => '2024-01-01', 'id_usuario' => $instructorUser->id]);
        $ficha = $this->ficha();
        $ficha->update(['id_instructor' => $instructor->id]);

        $this->como($admin)->deleteJson('/v1/general-users/' . $instructorUser->id)->assertOk();

        $this->assertDatabaseMissing('general_users', ['id' => $instructorUser->id]);
        $this->assertDatabaseMissing('class_groups', ['id' => $ficha->id]);
    }

    public function test_admin_borra_un_usuario_sin_historial(): void
    {
        $admin = $this->usuario('admin');
        $nuevo = $this->usuario('aprendiz');

        $this->como($admin)->deleteJson('/v1/general-users/' . $nuevo->id)->assertOk();
        $this->assertDatabaseMissing('general_users', ['id' => $nuevo->id]);
    }

    public function test_nadie_borra_su_propia_cuenta(): void
    {
        $admin = $this->usuario('admin');

        $this->como($admin)->deleteJson('/v1/general-users/' . $admin->id)->assertStatus(422);
        $this->assertDatabaseHas('general_users', ['id' => $admin->id]);
    }

    public function test_borrar_un_usuario_limpia_las_notificaciones_de_sus_propuestas(): void
    {
        $admin = $this->usuario('admin');
        $autor = $this->usuario('aprendiz');
        $proyecto = $this->proyecto($autor);

        Notification::create([
            'titulo' => 'Similitud detectada',
            'tipo' => 'similitud',
            'enlace' => 'proyecto:' . $proyecto->id,
            'leida' => false,
            'fecha' => now()->toDateString(),
            'id_usuario' => $admin->id,
        ]);

        $this->como($admin)->deleteJson('/v1/general-users/' . $autor->id)->assertOk();

        // Sin proyecto, la notificación con enlace no queda huérfana.
        $this->assertDatabaseMissing('notifications', ['enlace' => 'proyecto:' . $proyecto->id]);
    }

    public function test_no_se_puede_suspender_al_ultimo_admin_activo(): void
    {
        // Deja un solo administrador activo en el escenario (se revierte al terminar).
        GeneralUser::where('rol', 'admin')->update(['estado' => false]);
        $admin = $this->usuario('admin', true);

        $this->como($admin)->putJson('/v1/general-users/' . $admin->id, [
            'nombre' => $admin->nombre,
            'apellido' => $admin->apellido,
            'correo' => $admin->correo,
            'estado' => false,
        ])->assertStatus(409);

        $this->assertTrue($admin->fresh()->estado);
    }

    // ---------------------------------------------------------------- NF-04
    // El creador conserva borrar borradores y rechazadas; una propuesta ya
    // enviada (pendiente/aprobada) protege su evidencia compartida (D-2).

    private function aprendizConFicha(ClassGroup $ficha): GeneralUser
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

    public function test_el_creador_no_borra_una_propuesta_en_revision(): void
    {
        $ficha = $this->ficha();
        $creador = $this->aprendizConFicha($ficha);
        $proyecto = $this->proyecto($creador, $ficha); // pendiente

        $this->como($creador)
            ->deleteJson('/v1/projects/' . $proyecto->id)
            ->assertStatus(409);

        $this->assertDatabaseHas('projects', ['id' => $proyecto->id, 'estado' => 'pendiente']);
    }

    public function test_el_creador_no_borra_una_aprobada_y_la_evidencia_compartida_sobrevive(): void
    {
        $ficha = $this->ficha();
        $creador = $this->aprendizConFicha($ficha);
        $contraparte = $this->usuario('aprendiz');

        $aprobada = $this->proyecto($creador, $ficha);
        $aprobada->update(['estado' => 'aprobado']);
        $otro = $this->proyecto($contraparte, $ficha);

        $par = Similarity::create([
            'id_proyecto_1' => min($aprobada->id, $otro->id),
            'id_proyecto_2' => max($aprobada->id, $otro->id),
            'porcentaje' => 80,
            'fecha' => now()->toDateString(),
        ]);

        $this->como($creador)
            ->deleteJson('/v1/projects/' . $aprobada->id)
            ->assertStatus(409);

        $this->assertDatabaseHas('projects', ['id' => $aprobada->id, 'estado' => 'aprobado']);
        $this->assertDatabaseHas('projects', ['id' => $otro->id]);
        $this->assertDatabaseHas('similarities', ['id' => $par->id]);
    }

    public function test_el_creador_si_borra_un_borrador(): void
    {
        $ficha = $this->ficha();
        $creador = $this->aprendizConFicha($ficha);
        $proyecto = $this->proyecto($creador, $ficha);
        $proyecto->update(['estado' => 'borrador']);

        $this->como($creador)->deleteJson('/v1/projects/' . $proyecto->id)->assertOk();
        $this->assertDatabaseMissing('projects', ['id' => $proyecto->id]);
    }

    public function test_el_creador_si_borra_una_rechazada(): void
    {
        $ficha = $this->ficha();
        $creador = $this->aprendizConFicha($ficha);
        $proyecto = $this->proyecto($creador, $ficha);
        $proyecto->update(['estado' => 'rechazado']);

        $this->como($creador)->deleteJson('/v1/projects/' . $proyecto->id)->assertOk();
        $this->assertDatabaseMissing('projects', ['id' => $proyecto->id]);
    }

    public function test_un_instructor_no_borra_propuestas(): void
    {
        $instructorUser = $this->usuario('instructor');
        $instructor = Instructor::create(['fecha_ingreso' => '2024-01-01', 'id_usuario' => $instructorUser->id]);
        $ficha = $this->ficha();
        $ficha->update(['id_instructor' => $instructor->id]);
        $creador = $this->aprendizConFicha($ficha);
        $proyecto = $this->proyecto($creador, $ficha);
        $proyecto->update(['estado' => 'borrador']);

        $this->como($instructorUser)
            ->deleteJson('/v1/projects/' . $proyecto->id)
            ->assertStatus(403);

        $this->assertDatabaseHas('projects', ['id' => $proyecto->id]);
    }
}

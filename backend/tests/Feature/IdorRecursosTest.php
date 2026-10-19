<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\BugReport;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\Notification;
use App\Models\Project;
use App\Models\TrainingProgram;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// BUG-025 (verificación de autorización): los recursos individuales solo son
// legibles por su dueño o un admin (notificación y reporte de falla), y una
// propuesta aprobada no se edita por el aprendiz creador.
class IdorRecursosTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Idor',
            'correo' => $rol . '.' . uniqid() . '@idor.local',
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

    // Ficha activa con el aprendiz dentro: requisito para que `puedeEscribir`
    // lo deje llegar a la guarda de estado que se quiere verificar.
    private function fichaConAprendiz(GeneralUser $aprendiz): ClassGroup
    {
        $red = KnowledgeNetwork::create(['nombre' => 'Red ' . uniqid()]);
        $programa = TrainingProgram::create([
            'nombre' => 'Programa ' . uniqid(),
            'nivel' => 'Tecnologo',
            'knowledge_network_id' => $red->id,
        ]);
        $instructor = Instructor::create([
            'fecha_ingreso' => '2024-01-01',
            'id_usuario' => $this->usuario('instructor')->id,
        ]);
        $ficha = ClassGroup::create([
            'codigo' => 'id-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha idor',
            'estado' => 'activo',
            'id_programa' => $programa->id,
            'id_instructor' => $instructor->id,
        ]);
        Apprentice::create([
            'codigo' => 'AP-' . uniqid(),
            'id_usuario' => $aprendiz->id,
            'id_class_group' => $ficha->id,
            'id_programa' => $programa->id,
        ]);

        return $ficha;
    }

    public function test_aprendiz_no_lee_la_notificacion_de_otro(): void
    {
        $a = $this->usuario('aprendiz');
        $b = $this->usuario('aprendiz');
        $notificacion = Notification::create([
            'titulo' => 'Aviso',
            'tipo' => 'mensaje',
            'fecha' => now()->toDateString(),
            'id_usuario' => $a->id,
        ]);

        // Un aprendiz ajeno no puede leer la bandeja de otro.
        $this->como($b)
            ->getJson('/v1/notifications/' . $notificacion->id)
            ->assertStatus(403)
            ->assertJsonPath('message', 'No tienes acceso a esta notificación.');

        // El dueño sí.
        $this->como($a)
            ->getJson('/v1/notifications/' . $notificacion->id)
            ->assertOk()
            ->assertJsonPath('titulo', 'Aviso');

        // Un admin también (consulta de soporte).
        $this->como($this->usuario('admin'))
            ->getJson('/v1/notifications/' . $notificacion->id)
            ->assertOk();
    }

    public function test_aprendiz_no_lee_el_reporte_de_otro(): void
    {
        $a = $this->usuario('aprendiz');
        $b = $this->usuario('aprendiz');
        $reporte = BugReport::create([
            'titulo' => 'Falla al guardar',
            'descripcion' => 'La propuesta no guarda el objetivo general.',
            'tipo' => 'bug_ui',
            'estado' => 'pendiente',
            'fecha' => now()->toDateString(),
            'id_usuario' => $a->id,
        ]);

        // Un aprendiz ajeno no puede leer el reporte de otro.
        $this->como($b)
            ->getJson('/v1/bug-reports/' . $reporte->id)
            ->assertStatus(403)
            ->assertJsonPath('message', 'No tienes acceso a este reporte.');

        // El dueño sí.
        $this->como($a)
            ->getJson('/v1/bug-reports/' . $reporte->id)
            ->assertOk()
            ->assertJsonPath('id', $reporte->id);

        // Un admin también (consulta de soporte).
        $this->como($this->usuario('admin'))
            ->getJson('/v1/bug-reports/' . $reporte->id)
            ->assertOk()
            ->assertJsonPath('id', $reporte->id);
    }

    public function test_aprendiz_no_edita_una_propuesta_aprobada(): void
    {
        $aprendiz = $this->usuario('aprendiz');
        $ficha = $this->fichaConAprendiz($aprendiz);
        $propuesta = Project::create([
            'titulo' => 'Propuesta aprobada',
            'resumen' => 'Resumen de una propuesta ya aprobada por el instructor.',
            'area_aplicacion' => 'Desarrollo Web',
            'estado' => 'aprobado',
            'id_creador' => $aprendiz->id,
            'id_class_group' => $ficha->id,
        ]);

        // El dueño (aprendiz de la ficha activa) llega a la guarda de estado.
        $this->como($aprendiz)
            ->putJson('/v1/projects/' . $propuesta->id, ['titulo' => 'Otro título'])
            ->assertStatus(422)
            ->assertJsonPath('message', 'Solo puedes editar una propuesta en borrador, en revisión o rechazada.');

        $this->assertSame('Propuesta aprobada', $propuesta->fresh()->titulo);
    }
}

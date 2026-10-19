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

// D-01 × BUG-007: el borrado de usuarios (kernel BorradoCascada::usuario)
// verifica la evidencia compartida de su subárbol —propuestas de sus fichas
// como instructor y propuestas propias— antes de mutar nada. Con cualquier par
// (vigente o histórico, interno o cross) responde 409 y conserva todo; los
// usuarios con rol admin quedan exentos y conservan el borrado sin
// restricciones (exención documentada).
class BorradoUsuarioEvidenciaTest extends TestCase
{
    use DatabaseTransactions;

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

    private function programa(): TrainingProgram
    {
        $red = KnowledgeNetwork::create(['nombre' => 'Red ' . uniqid()]);

        return TrainingProgram::create([
            'nombre' => 'Programa ' . uniqid(),
            'nivel' => 'Tecnologo',
            'knowledge_network_id' => $red->id,
        ]);
    }

    private function instructorDe(): array
    {
        $user = $this->usuario('instructor');
        $fila = Instructor::create(['fecha_ingreso' => '2024-01-01', 'id_usuario' => $user->id]);

        return [$user, $fila];
    }

    private function ficha(TrainingProgram $programa, Instructor $instructor, string $nombre = null): ClassGroup
    {
        return ClassGroup::create([
            'codigo' => 'bu-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => $nombre ?? 'Ficha ' . uniqid(),
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

    private function proyecto(GeneralUser $creador, ClassGroup $ficha, string $estado = 'pendiente'): Project
    {
        return Project::create([
            'titulo' => 'Propuesta ' . uniqid(),
            'resumen' => 'Resumen de prueba del borrado de usuarios con evidencia.',
            'area_aplicacion' => 'Desarrollo Web',
            'estado' => $estado,
            'id_creador' => $creador->id,
            'id_class_group' => $ficha->id,
        ]);
    }

    private function par(Project $a, Project $b, bool $vigente = true): Similarity
    {
        return Similarity::create([
            'porcentaje' => 80,
            'fecha' => now()->toDateString(),
            'id_proyecto_1' => $a->id,
            'id_proyecto_2' => $b->id,
            'vigente' => $vigente,
        ]);
    }

    private function msgUsuario(): string
    {
        return 'No puedes eliminar al usuario: sus propuestas o las de sus fichas tienen similitudes compartidas (vigentes o históricas). La evidencia pertenece también a las demás propuestas.';
    }

    // ------------------------------------------------------------ bloqueos

    public function test_instructor_con_par_vigente_responde_409_y_conserva_todo(): void
    {
        $programa = $this->programa();
        [$userA, $instrA] = $this->instructorDe();
        [$userB, $instrB] = $this->instructorDe();
        $fichaA = $this->ficha($programa, $instrA, 'Ficha A');
        $fichaB = $this->ficha($programa, $instrB, 'Ficha B');
        $pA = $this->proyecto($this->aprendizEn($fichaA), $fichaA, 'aprobado');
        $pB = $this->proyecto($this->aprendizEn($fichaB), $fichaB, 'aprobado');
        $par = $this->par($pA, $pB, true);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/general-users/' . $userA->id)
            ->assertStatus(409)
            ->assertJsonPath('message', $this->msgUsuario());

        $this->assertDatabaseHas('general_users', ['id' => $userA->id]);
        $this->assertDatabaseHas('instructors', ['id' => $instrA->id]);
        $this->assertDatabaseHas('class_groups', ['id' => $fichaA->id]);
        $this->assertDatabaseHas('projects', ['id' => $pA->id]);
        $this->assertDatabaseHas('projects', ['id' => $pB->id]);
        $this->assertDatabaseHas('similarities', ['id' => $par->id]);
    }

    public function test_instructor_con_par_historico_tambien_bloquea(): void
    {
        $programa = $this->programa();
        [$userA, $instrA] = $this->instructorDe();
        [$userB, $instrB] = $this->instructorDe();
        $fichaA = $this->ficha($programa, $instrA);
        $fichaB = $this->ficha($programa, $instrB);
        $pA = $this->proyecto($this->aprendizEn($fichaA), $fichaA, 'rechazado');
        $pB = $this->proyecto($this->aprendizEn($fichaB), $fichaB, 'aprobado');
        $par = $this->par($pA, $pB, false);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/general-users/' . $userA->id)
            ->assertStatus(409)
            ->assertJsonPath('message', $this->msgUsuario());

        $this->assertFalse((bool) $par->fresh()->vigente);
        $this->assertDatabaseHas('general_users', ['id' => $userA->id]);
        $this->assertDatabaseHas('instructors', ['id' => $instrA->id]);
        $this->assertDatabaseHas('class_groups', ['id' => $fichaA->id]);
        $this->assertDatabaseHas('projects', ['id' => $pA->id]);
        $this->assertDatabaseHas('similarities', ['id' => $par->id]);
    }

    public function test_aprendiz_creador_con_par_cross_responde_409_y_conserva_la_contraparte(): void
    {
        $programa = $this->programa();
        [$userInstrA, $instrA] = $this->instructorDe();
        [$userInstrB, $instrB] = $this->instructorDe();
        $fichaA = $this->ficha($programa, $instrA);
        $fichaB = $this->ficha($programa, $instrB);
        $creador = $this->aprendizEn($fichaA);
        $contraparte = $this->aprendizEn($fichaB);
        $pA = $this->proyecto($creador, $fichaA, 'aprobado');
        $pB = $this->proyecto($contraparte, $fichaB, 'aprobado');
        $par = $this->par($pA, $pB, true);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/general-users/' . $creador->id)
            ->assertStatus(409)
            ->assertJsonPath('message', $this->msgUsuario());

        $this->assertDatabaseHas('general_users', ['id' => $creador->id]);
        $this->assertDatabaseHas('projects', ['id' => $pA->id]);
        $this->assertDatabaseHas('similarities', ['id' => $par->id]);
        // La contraparte (otra ficha) queda intacta.
        $this->assertDatabaseHas('general_users', ['id' => $contraparte->id]);
        $this->assertDatabaseHas('projects', ['id' => $pB->id]);
    }

    // ------------------------------------------------------------ permitidos

    public function test_aprendiz_creador_sin_pares_se_borra_en_cascada(): void
    {
        $programa = $this->programa();
        [$userInstr, $instr] = $this->instructorDe();
        $ficha = $this->ficha($programa, $instr);
        $creador = $this->aprendizEn($ficha);
        $proyecto = $this->proyecto($creador, $ficha);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/general-users/' . $creador->id)
            ->assertOk();

        $this->assertDatabaseMissing('general_users', ['id' => $creador->id]);
        $this->assertDatabaseMissing('projects', ['id' => $proyecto->id]);
        // La ficha y su instructor no forman parte del subárbol del aprendiz.
        $this->assertDatabaseHas('class_groups', ['id' => $ficha->id]);
        $this->assertDatabaseHas('instructors', ['id' => $instr->id]);
    }

    public function test_aprendiz_sin_propuestas_se_borra(): void
    {
        $admin = $this->usuario('admin');
        $aprendiz = $this->usuario('aprendiz');

        $this->como($admin)
            ->deleteJson('/v1/general-users/' . $aprendiz->id)
            ->assertOk();

        $this->assertDatabaseMissing('general_users', ['id' => $aprendiz->id]);
    }

    public function test_admin_objetivo_con_par_se_borra_por_exencion(): void
    {
        $solicitante = $this->usuario('admin');
        $objetivo = $this->usuario('admin');
        $programa = $this->programa();
        [$userInstr, $instr] = $this->instructorDe();
        $ficha = $this->ficha($programa, $instr);
        $contraparte = $this->aprendizEn($ficha);

        $pObjetivo = $this->proyecto($objetivo, $ficha, 'aprobado');
        $pContraparte = $this->proyecto($contraparte, $ficha, 'aprobado');
        $par = $this->par($pObjetivo, $pContraparte, true);

        $this->como($solicitante)
            ->deleteJson('/v1/general-users/' . $objetivo->id)
            ->assertOk();

        // Exención admin: la cascada destruye la propuesta y con ella el par.
        $this->assertDatabaseMissing('general_users', ['id' => $objetivo->id]);
        $this->assertDatabaseMissing('projects', ['id' => $pObjetivo->id]);
        $this->assertDatabaseMissing('similarities', ['id' => $par->id]);
        // La contraparte (aprendiz de la ficha) no se toca.
        $this->assertDatabaseHas('projects', ['id' => $pContraparte->id]);
    }
}

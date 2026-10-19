<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\ApprenticeProject;
use App\Models\ClassGroup;
use App\Models\Comment;
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

// D-2b × BUG-007: ninguna cascada de catálogo (instructor, programa, red)
// puede destruir evidencia de similitud compartida. Con cualquier par —
// vigente o histórico, interno al subárbol o con contraparte externa— el
// borrado responde 409 sin mutar nada; sin pares, el comportamiento actual
// se conserva (incluida la anulación directa de fichas con datos).
class CascadaEvidenciaTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Cascada',
            'correo' => $rol . '.' . uniqid() . '@cascada.local',
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

    private function red(string $nombre = null): KnowledgeNetwork
    {
        return KnowledgeNetwork::create(['nombre' => $nombre ?? 'Red ' . uniqid()]);
    }

    private function programa(KnowledgeNetwork $red, string $nombre = null): TrainingProgram
    {
        return TrainingProgram::create([
            'nombre' => $nombre ?? 'Programa ' . uniqid(),
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
            'codigo' => 'ce-' . uniqid(),
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
            'titulo' => 'Inventarios ' . uniqid(),
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
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

    private function msgFicha(string $nombre): string
    {
        return "No puedes eliminar la ficha «{$nombre}»: sus propuestas tienen similitudes compartidas (vigentes o históricas). La evidencia pertenece también a las demás propuestas.";
    }

    private function msgPrograma(string $nombre): string
    {
        return "No puedes eliminar el programa «{$nombre}»: las propuestas de sus fichas tienen similitudes compartidas (vigentes o históricas). La evidencia pertenece también a las demás propuestas.";
    }

    private function msgRed(string $nombre): string
    {
        return "No puedes eliminar la red «{$nombre}»: las propuestas de sus programas tienen similitudes compartidas (vigentes o históricas). La evidencia pertenece también a las demás propuestas.";
    }

    private function msgInstructor(): string
    {
        return 'No puedes eliminar al instructor: las propuestas de sus fichas tienen similitudes compartidas (vigentes o históricas). La evidencia pertenece también a las demás propuestas.';
    }

    // ------------------------------------------------------------ bloqueos

    public function test_instructor_con_par_vigente_responde_409_y_conserva_todo(): void
    {
        $red = $this->red();
        $programa = $this->programa($red);
        [$userA, $instrA] = $this->instructorDe();
        [$userB, $instrB] = $this->instructorDe();
        $fichaA = $this->ficha($programa, $instrA, 'Ficha A');
        $fichaB = $this->ficha($programa, $instrB, 'Ficha B');
        $apA = $this->aprendizEn($fichaA);
        $apB = $this->aprendizEn($fichaB);
        $pA = $this->proyecto($apA, $fichaA, 'aprobado');
        $pB = $this->proyecto($apB, $fichaB, 'aprobado');
        $par = $this->par($pA, $pB, true);

        // Evidencia adicional que tampoco debe tocarse: comentario, historial y
        // un pivote de equipo del aprendiz hacia una propuesta externa.
        $comentario = Comment::create(['texto' => 'Observación del instructor.', 'id_proyecto' => $pA->id, 'id_usuario' => $userB->id]);
        $historial = ProjectHistory::create(['id_proyecto' => $pA->id, 'id_usuario' => $userB->id, 'accion' => 'creacion', 'detalle' => ['nota' => 'x']]);
        $filaA = Apprentice::where('id_usuario', $apA->id)->first();
        $pivote = ApprenticeProject::create(['id_aprendiz' => $filaA->id, 'id_proyecto' => $pB->id]);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/instructors/' . $instrA->id)
            ->assertStatus(409)
            ->assertJsonPath('message', $this->msgInstructor());

        $this->assertDatabaseHas('instructors', ['id' => $instrA->id]);
        $this->assertDatabaseHas('class_groups', ['id' => $fichaA->id]);
        $this->assertDatabaseHas('projects', ['id' => $pA->id]);
        $this->assertDatabaseHas('projects', ['id' => $pB->id]);
        $this->assertDatabaseHas('similarities', ['id' => $par->id]);
        $this->assertDatabaseHas('comments', ['id' => $comentario->id]);
        $this->assertDatabaseHas('project_histories', ['id' => $historial->id]);
        $this->assertDatabaseHas('apprentice_projects', ['id' => $pivote->id]);
        $this->assertDatabaseHas('apprentices', ['id_usuario' => $apA->id, 'id_class_group' => $fichaA->id]);
        $this->assertDatabaseHas('apprentices', ['id_usuario' => $apB->id, 'id_class_group' => $fichaB->id]);
    }

    public function test_instructor_con_par_historico_tambien_bloquea(): void
    {
        $red = $this->red();
        $programa = $this->programa($red);
        [$userA, $instrA] = $this->instructorDe();
        [$userB, $instrB] = $this->instructorDe();
        $fichaA = $this->ficha($programa, $instrA);
        $fichaB = $this->ficha($programa, $instrB);
        $pA = $this->proyecto($this->aprendizEn($fichaA), $fichaA, 'rechazado');
        $pB = $this->proyecto($this->aprendizEn($fichaB), $fichaB, 'aprobado');
        $par = $this->par($pA, $pB, false);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/instructors/' . $instrA->id)
            ->assertStatus(409)
            ->assertJsonPath('message', $this->msgInstructor());

        $this->assertFalse((bool) $par->fresh()->vigente);
        $this->assertDatabaseHas('instructors', ['id' => $instrA->id]);
        $this->assertDatabaseHas('class_groups', ['id' => $fichaA->id]);
        $this->assertDatabaseHas('projects', ['id' => $pA->id]);
        $this->assertDatabaseHas('similarities', ['id' => $par->id]);
    }

    public function test_ficha_con_par_bloquea_el_kernel_con_409(): void
    {
        $red = $this->red();
        $programa = $this->programa($red);
        [$userA, $instrA] = $this->instructorDe();
        [$userB, $instrB] = $this->instructorDe();
        $fichaA = $this->ficha($programa, $instrA, 'Ficha A');
        $fichaB = $this->ficha($programa, $instrB, 'Ficha B');
        $pA = $this->proyecto($this->aprendizEn($fichaA), $fichaA, 'aprobado');
        $pB = $this->proyecto($this->aprendizEn($fichaB), $fichaB, 'aprobado');
        $par = $this->par($pA, $pB, true);

        try {
            \App\Support\BorradoCascada::ficha($fichaA);
            $this->fail('La guarda debía abortar con 409.');
        } catch (\Illuminate\Http\Exceptions\HttpResponseException $e) {
            $this->assertSame(409, $e->getResponse()->getStatusCode());
            $this->assertSame($this->msgFicha('Ficha A'), $e->getResponse()->getData(true)['message'] ?? null);
        }

        $this->assertDatabaseHas('class_groups', ['id' => $fichaA->id]);
        $this->assertDatabaseHas('projects', ['id' => $pA->id]);
        $this->assertDatabaseHas('similarities', ['id' => $par->id]);
    }

    public function test_programa_con_par_interno_responde_409(): void
    {
        $red = $this->red();
        $programa = $this->programa($red, 'Programa con evidencia');
        [$userA, $instrA] = $this->instructorDe();
        $fichaA = $this->ficha($programa, $instrA);
        $fichaB = $this->ficha($programa, $instrA);
        $pA = $this->proyecto($this->aprendizEn($fichaA), $fichaA, 'aprobado');
        $pB = $this->proyecto($this->aprendizEn($fichaB), $fichaB, 'aprobado');
        $par = $this->par($pA, $pB, true);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/training-programs/' . $programa->id)
            ->assertStatus(409)
            ->assertJsonPath('message', $this->msgPrograma('Programa con evidencia'));

        $this->assertDatabaseHas('training_programs', ['id' => $programa->id]);
        $this->assertDatabaseHas('class_groups', ['id' => $fichaA->id]);
        $this->assertDatabaseHas('class_groups', ['id' => $fichaB->id]);
        $this->assertDatabaseHas('projects', ['id' => $pA->id]);
        $this->assertDatabaseHas('projects', ['id' => $pB->id]);
        $this->assertDatabaseHas('similarities', ['id' => $par->id]);
    }

    public function test_programa_con_par_cross_responde_409_y_conserva_la_contraparte(): void
    {
        $red = $this->red();
        $programaP = $this->programa($red, 'Programa P');
        $programaQ = $this->programa($red, 'Programa Q');
        [$userA, $instrA] = $this->instructorDe();
        [$userB, $instrB] = $this->instructorDe();
        $fichaA = $this->ficha($programaP, $instrA);
        $fichaB = $this->ficha($programaQ, $instrB);
        $pA = $this->proyecto($this->aprendizEn($fichaA), $fichaA, 'aprobado');
        $pB = $this->proyecto($this->aprendizEn($fichaB), $fichaB, 'aprobado');
        $par = $this->par($pA, $pB, true);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/training-programs/' . $programaP->id)
            ->assertStatus(409)
            ->assertJsonPath('message', $this->msgPrograma('Programa P'));

        $this->assertDatabaseHas('training_programs', ['id' => $programaP->id]);
        $this->assertDatabaseHas('training_programs', ['id' => $programaQ->id]);
        $this->assertDatabaseHas('class_groups', ['id' => $fichaB->id]);
        $this->assertDatabaseHas('projects', ['id' => $pA->id]);
        $this->assertDatabaseHas('projects', ['id' => $pB->id]);
        $this->assertDatabaseHas('similarities', ['id' => $par->id]);
    }

    public function test_red_con_par_responde_409_y_conserva_todo(): void
    {
        $red1 = $this->red('Red con evidencia');
        $red2 = $this->red('Red externa');
        $programa1 = $this->programa($red1);
        $programa2 = $this->programa($red2);
        [$userA, $instrA] = $this->instructorDe();
        [$userB, $instrB] = $this->instructorDe();
        $fichaA = $this->ficha($programa1, $instrA);
        $fichaB = $this->ficha($programa2, $instrB);
        $pA = $this->proyecto($this->aprendizEn($fichaA), $fichaA, 'aprobado');
        $pB = $this->proyecto($this->aprendizEn($fichaB), $fichaB, 'aprobado');
        $par = $this->par($pA, $pB, true);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/knowledge-networks/' . $red1->id)
            ->assertStatus(409)
            ->assertJsonPath('message', $this->msgRed('Red con evidencia'));

        $this->assertDatabaseHas('knowledge_networks', ['id' => $red1->id]);
        $this->assertDatabaseHas('knowledge_networks', ['id' => $red2->id]);
        $this->assertDatabaseHas('training_programs', ['id' => $programa1->id]);
        $this->assertDatabaseHas('training_programs', ['id' => $programa2->id]);
        $this->assertDatabaseHas('projects', ['id' => $pA->id]);
        $this->assertDatabaseHas('projects', ['id' => $pB->id]);
        $this->assertDatabaseHas('similarities', ['id' => $par->id]);
    }

    // ------------------------------------------------------------ permitidos

    public function test_instructor_sin_pares_se_borra_y_conserva_los_aprendices(): void
    {
        $red = $this->red();
        $programa = $this->programa($red);
        [$user, $instr] = $this->instructorDe();
        $ficha = $this->ficha($programa, $instr);
        $ap = $this->aprendizEn($ficha);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/instructors/' . $instr->id)
            ->assertOk();

        $this->assertDatabaseMissing('instructors', ['id' => $instr->id]);
        $this->assertDatabaseMissing('class_groups', ['id' => $ficha->id]);
        $this->assertDatabaseHas('apprentices', ['id_usuario' => $ap->id, 'id_class_group' => null]);
    }

    public function test_programa_sin_pares_se_borra_con_la_politica_actual(): void
    {
        $red = $this->red();
        $programa = $this->programa($red);
        [$user, $instr] = $this->instructorDe();
        $ficha = $this->ficha($programa, $instr);
        $ap = $this->aprendizEn($ficha);
        $proyecto = $this->proyecto($ap, $ficha, 'borrador');

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/training-programs/' . $programa->id)
            ->assertOk();

        $this->assertDatabaseMissing('training_programs', ['id' => $programa->id]);
        $this->assertDatabaseMissing('class_groups', ['id' => $ficha->id]);
        $this->assertDatabaseMissing('projects', ['id' => $proyecto->id]);
        // Política vigente, sin cambios en este lote: los aprendices del
        // programa se eliminan (la cuenta queda sin perfil).
        $this->assertDatabaseMissing('apprentices', ['id_usuario' => $ap->id]);
    }

    public function test_red_sin_pares_se_borra(): void
    {
        $red = $this->red('Red libre');
        $programa = $this->programa($red);
        [$user, $instr] = $this->instructorDe();
        $ficha = $this->ficha($programa, $instr);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/knowledge-networks/' . $red->id)
            ->assertOk();

        $this->assertDatabaseMissing('knowledge_networks', ['id' => $red->id]);
        $this->assertDatabaseMissing('training_programs', ['id' => $programa->id]);
        $this->assertDatabaseMissing('class_groups', ['id' => $ficha->id]);
    }

    public function test_ficha_con_propuestas_sin_pares_se_anula_sin_borrar_nada(): void
    {
        $red = $this->red();
        $programa = $this->programa($red);
        [$user, $instr] = $this->instructorDe();
        $ficha = $this->ficha($programa, $instr);
        $ap = $this->aprendizEn($ficha);
        $proyecto = $this->proyecto($ap, $ficha, 'borrador');

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/class-groups/' . $ficha->id)
            ->assertOk()
            ->assertJsonPath('accion', 'anulada');

        $this->assertDatabaseHas('class_groups', ['id' => $ficha->id, 'estado' => 'anulada', 'numero' => null]);
        $this->assertDatabaseHas('projects', ['id' => $proyecto->id]);
        $this->assertDatabaseHas('apprentices', ['id_usuario' => $ap->id, 'id_class_group' => $ficha->id]);
    }

    public function test_ficha_vacia_se_elimina(): void
    {
        $red = $this->red();
        $programa = $this->programa($red);
        [$user, $instr] = $this->instructorDe();
        $ficha = $this->ficha($programa, $instr);

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/class-groups/' . $ficha->id)
            ->assertOk()
            ->assertJsonPath('accion', 'eliminada');

        $this->assertDatabaseMissing('class_groups', ['id' => $ficha->id]);
    }
}

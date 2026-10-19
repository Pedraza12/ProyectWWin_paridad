<?php

namespace Tests\Feature;

use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\Project;
use App\Models\Similarity;
use App\Models\TrainingProgram;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// D-2b × BUG-007 (concurrencia): la guarda de las cascadas debe ver —mediante
// lecturas de bloqueo— un par confirmado por otra conexión aunque la
// instantánea REPEATABLE READ del proceso principal sea anterior; además, el
// mutex del motor se adquiere primero (orden O1: motor → propuestas → pares).
class CascadaConcurrenciaTest extends TestCase
{
    use DatabaseTransactions;

    private const MSG = 'No puedes eliminar al instructor: las propuestas de sus fichas tienen similitudes compartidas (vigentes o históricas). La evidencia pertenece también a las demás propuestas.';

    private function conexionAuxiliar()
    {
        $base = config('database.connections.mysql');

        // Guarda dura: esta prueba ejecuta SQL directo y COMMITs reales; jamás
        // debe correr fuera de la base de pruebas autorizada.
        $this->assertSame('proyectwin_test', $base['database'], 'La prueba concurrente exige proyectwin_test.');

        config(['database.connections.mysql_aux' => $base]);
        DB::purge('mysql_aux');

        $aux = DB::connection('mysql_aux');
        // Fallo rápido y determinista si algo introduce contención inesperada.
        $aux->statement('SET SESSION innodb_lock_wait_timeout = 5');

        return $aux;
    }

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Concurrencia',
            'correo' => $rol . '.' . uniqid() . '@concurrencia.local',
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

    public function test_un_par_confirmado_por_otra_conexion_bloquea_el_borrado_del_instructor(): void
    {
        $aux = $this->conexionAuxiliar();
        $sufijo = uniqid();

        // Escenario comprometido por la conexión auxiliar (SQL directo: el hook
        // `creating` de GeneralUser consultaría por la conexión principal y
        // fijaría su instantánea antes de estos commits).
        $aux->insert(
            'INSERT INTO general_users (nombre, apellido, correo, username, password, rol, estado, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 1, NOW(), NOW())',
            ['Docente', 'Concurrente', 'docente.' . $sufijo . '@concurrencia.local', 'docente.' . $sufijo, Hash::make('123456'), 'instructor']
        );
        $instructorUserId = (int) $aux->getPdo()->lastInsertId();

        $aux->insert(
            'INSERT INTO general_users (nombre, apellido, correo, username, password, rol, estado, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 1, NOW(), NOW())',
            ['Creador', 'Concurrente', 'creador.' . $sufijo . '@concurrencia.local', 'creador.' . $sufijo, Hash::make('123456'), 'aprendiz']
        );
        $creadorId = (int) $aux->getPdo()->lastInsertId();

        $aux->insert(
            'INSERT INTO knowledge_networks (nombre, created_at, updated_at) VALUES (?, NOW(), NOW())',
            ['Red concurrente ' . $sufijo]
        );
        $redId = (int) $aux->getPdo()->lastInsertId();

        $aux->insert(
            'INSERT INTO training_programs (nombre, nivel, knowledge_network_id, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())',
            ['Programa concurrente', 'Tecnologo', $redId]
        );
        $programaId = (int) $aux->getPdo()->lastInsertId();

        $aux->insert(
            'INSERT INTO instructors (fecha_ingreso, id_usuario, created_at, updated_at) VALUES (?, ?, NOW(), NOW())',
            ['2024-01-01', $instructorUserId]
        );
        $instructorId = (int) $aux->getPdo()->lastInsertId();

        $aux->insert(
            'INSERT INTO class_groups (codigo, numero, nombre, estado, id_programa, id_instructor, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())',
            ['cc-' . $sufijo, (string) random_int(100000, 999999), 'Ficha concurrente', 'activo', $programaId, $instructorId]
        );
        $fichaId = (int) $aux->getPdo()->lastInsertId();

        $objetivo = new Project([
            'titulo' => 'Inventarios objetivo concurrente',
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
            'area_aplicacion' => 'Desarrollo Web',
            'estado' => 'aprobado',
            'id_creador' => $creadorId,
            'id_class_group' => $fichaId,
        ]);
        $objetivo->setConnection('mysql_aux');
        $objetivo->save();

        $contraparte = new Project([
            'titulo' => 'Inventarios contraparte concurrente',
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
            'area_aplicacion' => 'Desarrollo Web',
            'estado' => 'aprobado',
            'id_creador' => $creadorId,
        ]);
        $contraparte->setConnection('mysql_aux');
        $contraparte->save();

        // Limpieza diferida: InnoDB NO libera los locks al hacer rollback a un
        // savepoint, así que las filas confirmadas por la auxiliar se eliminan
        // después del rollback del trait (los callbacks corren en orden de
        // registro: primero el rollback de la transacción principal, luego esto).
        $parId = null;
        $filaConfig = null;
        $configCreado = false;
        $this->beforeApplicationDestroyed(function () use (&$parId, &$filaConfig, &$configCreado, $instructorUserId, $creadorId, $redId, $programaId, $instructorId, $fichaId, $objetivo, $contraparte) {
            $aux = DB::connection('mysql_aux');
            if ($parId) {
                $aux->delete('DELETE FROM similarities WHERE id = ?', [$parId]);
            }
            $aux->delete('DELETE FROM projects WHERE id IN (?, ?)', [$objetivo->id, $contraparte->id]);
            $aux->delete('DELETE FROM class_groups WHERE id = ?', [$fichaId]);
            $aux->delete('DELETE FROM instructors WHERE id = ?', [$instructorId]);
            $aux->delete('DELETE FROM training_programs WHERE id = ?', [$programaId]);
            $aux->delete('DELETE FROM knowledge_networks WHERE id = ?', [$redId]);
            if ($configCreado && $filaConfig) {
                $aux->delete('DELETE FROM motor_configs WHERE id = ?', [$filaConfig->id]);
            }
            $aux->delete('DELETE FROM general_users WHERE id IN (?, ?)', [$instructorUserId, $creadorId]);
        });

        // Premisa: la principal ve el escenario comprometido y aún sin pares;
        // su instantánea queda antes del commit ajeno y la lectura de bloqueo
        // deberá ver el par.
        $this->assertTrue(Project::whereKey($objetivo->id)->exists());
        $this->assertFalse(Similarity::where('id_proyecto_1', $objetivo->id)->orWhere('id_proyecto_2', $objetivo->id)->exists());

        // T2 concurrente (conexión auxiliar): mutex del motor + par + COMMIT.
        $aux->beginTransaction();
        try {
            $filaConfig = $aux->selectOne('SELECT id FROM motor_configs ORDER BY id LIMIT 1');
            if (!$filaConfig) {
                $aux->insert('INSERT INTO motor_configs (umbral, meses, created_at, updated_at) VALUES (0.30, 12, NOW(), NOW())');
                $configCreado = true;
                $filaConfig = $aux->selectOne('SELECT id FROM motor_configs ORDER BY id LIMIT 1');
            }
            $aux->selectOne('SELECT id FROM motor_configs WHERE id = ? FOR UPDATE', [$filaConfig->id]);

            $p1 = min((int) $objetivo->id, (int) $contraparte->id);
            $p2 = max((int) $objetivo->id, (int) $contraparte->id);
            $aux->insert(
                'INSERT INTO similarities (id_proyecto_1, id_proyecto_2, porcentaje, fecha, vigente, created_at, updated_at) VALUES (?, ?, 100, CURDATE(), 1, NOW(), NOW())',
                [$p1, $p2]
            );
            $parId = (int) $aux->getPdo()->lastInsertId();
            $aux->commit();
        } catch (\Throwable $e) {
            $aux->rollBack();
            throw $e;
        }

        // La instantánea consistente sigue siendo anterior al commit ajeno.
        $this->assertFalse(Similarity::where('id_proyecto_1', $objetivo->id)->orWhere('id_proyecto_2', $objetivo->id)->exists());

        // El borrado de la cascada debe ver el par YA CONFIRMADO y rechazar.
        DB::connection()->flushQueryLog();
        DB::connection()->enableQueryLog();

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/instructors/' . $instructorId)
            ->assertStatus(409)
            ->assertJsonPath('message', self::MSG);

        $consultas = collect(DB::connection()->getQueryLog())->pluck('query');
        $lecturaBloqueo = $consultas->contains(fn ($q) => str_contains($q, 'similarities') && str_contains($q, 'for update'));
        $mutex = $consultas->contains(fn ($q) => str_contains($q, 'motor_configs') && str_contains($q, 'for update'));
        DB::connection()->disableQueryLog();
        $this->assertTrue($mutex, 'La guarda debe adquirir el mutex del motor (for update).');
        $this->assertTrue($lecturaBloqueo, 'La comprobación de pares debe ser una lectura de bloqueo (for update).');

        // Persistencia real: instructor, ficha, propuestas y par siguen.
        $this->assertSame(1, (int) $aux->selectOne('SELECT COUNT(*) AS c FROM instructors WHERE id = ?', [$instructorId])->c);
        $this->assertSame(1, (int) $aux->selectOne('SELECT COUNT(*) AS c FROM class_groups WHERE id = ?', [$fichaId])->c);
        $this->assertSame(2, (int) $aux->selectOne('SELECT COUNT(*) AS c FROM projects WHERE id IN (?, ?)', [$objetivo->id, $contraparte->id])->c);
        $this->assertSame(1, (int) $aux->selectOne('SELECT COUNT(*) AS c FROM similarities WHERE id = ?', [$parId])->c);

        // (La limpieza de las filas confirmadas por la auxiliar está registrada
        // como callback posterior al rollback del trait.)
    }

    public function test_el_mutex_del_motor_se_adquiere_antes_que_propuestas_y_pares(): void
    {
        $red = KnowledgeNetwork::create(['nombre' => 'Red orden ' . uniqid()]);
        $programa = TrainingProgram::create([
            'nombre' => 'Programa orden ' . uniqid(),
            'nivel' => 'Tecnologo',
            'knowledge_network_id' => $red->id,
        ]);
        $instructorUser = $this->usuario('instructor');
        $instructor = Instructor::create(['fecha_ingreso' => '2024-01-01', 'id_usuario' => $instructorUser->id]);
        $ficha = ClassGroup::create([
            'codigo' => 'or-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha orden',
            'estado' => 'activo',
            'id_programa' => $programa->id,
            'id_instructor' => $instructor->id,
        ]);
        $aprendizUser = $this->usuario('aprendiz');
        \App\Models\Apprentice::create([
            'codigo' => 'AP-' . uniqid(),
            'id_usuario' => $aprendizUser->id,
            'id_class_group' => $ficha->id,
            'id_programa' => $ficha->id_programa,
        ]);
        Project::create([
            'titulo' => 'Inventarios orden ' . uniqid(),
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
            'area_aplicacion' => 'Desarrollo Web',
            'estado' => 'borrador',
            'id_creador' => $aprendizUser->id,
            'id_class_group' => $ficha->id,
        ]);

        DB::connection()->flushQueryLog();
        DB::connection()->enableQueryLog();

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/instructors/' . $instructor->id)
            ->assertOk();

        $log = collect(DB::connection()->getQueryLog())->pluck('query')->values();
        DB::connection()->disableQueryLog();

        $motor = $log->search(fn ($q) => str_contains($q, 'motor_configs') && str_contains($q, 'for update'));
        $propuestas = $log->search(fn ($q) => str_contains($q, 'projects') && str_contains($q, 'for update'));
        $pares = $log->search(fn ($q) => str_contains($q, 'similarities') && str_contains($q, 'for update'));

        $this->assertNotFalse($motor, 'La guarda debe bloquear el motor (for update).');
        $this->assertNotFalse($propuestas, 'La guarda debe bloquear las propuestas del subárbol.');
        $this->assertNotFalse($pares, 'La guarda debe comprobar los pares con lectura de bloqueo.');
        $this->assertTrue($motor < $propuestas, 'Orden O1: el motor se bloquea antes que las propuestas.');
        $this->assertTrue($propuestas < $pares, 'Orden O1: las propuestas antes que los pares.');

        $this->assertDatabaseMissing('instructors', ['id' => $instructor->id]);
    }

    public function test_la_edicion_de_ficha_con_cambio_de_programa_toma_el_mutex_antes_de_ficha_y_aprendices(): void
    {
        $red = KnowledgeNetwork::create(['nombre' => 'Red edicion ' . uniqid()]);
        $programaA = TrainingProgram::create(['nombre' => 'Programa A ' . uniqid(), 'nivel' => 'Tecnologo', 'knowledge_network_id' => $red->id]);
        $programaB = TrainingProgram::create(['nombre' => 'Programa B ' . uniqid(), 'nivel' => 'Tecnologo', 'knowledge_network_id' => $red->id]);
        $instructorUser = $this->usuario('instructor');
        $instructor = Instructor::create(['fecha_ingreso' => '2024-01-01', 'id_usuario' => $instructorUser->id]);
        $ficha = ClassGroup::create([
            'codigo' => 'ed-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha edicion',
            'estado' => 'activo',
            'id_programa' => $programaA->id,
            'id_instructor' => $instructor->id,
        ]);
        $aprendizUser = $this->usuario('aprendiz');
        \App\Models\Apprentice::create([
            'codigo' => 'AP-' . uniqid(),
            'id_usuario' => $aprendizUser->id,
            'id_class_group' => $ficha->id,
            'id_programa' => $ficha->id_programa,
        ]);

        DB::connection()->flushQueryLog();
        DB::connection()->enableQueryLog();

        $this->como($this->usuario('admin'))
            ->putJson('/v1/class-groups/' . $ficha->id, [
                'numero' => $ficha->numero,
                'nombre' => $ficha->nombre,
                'estado' => 'activo',
                'id_programa' => $programaB->id,
                'id_instructor' => $instructor->id,
            ])
            ->assertOk();

        $log = collect(DB::connection()->getQueryLog())->pluck('query')->values();
        DB::connection()->disableQueryLog();

        $motor = $log->search(fn ($q) => str_contains($q, 'motor_configs') && str_contains($q, 'for update'));
        $primeraEscritura = $log->search(fn ($q) => str_contains($q, 'update `class_groups`') || str_contains($q, 'update `apprentices`'));

        $this->assertNotFalse($motor, 'La edición con cambio de programa debe tomar el mutex del motor.');
        $this->assertNotFalse($primeraEscritura, 'La edición debe escribir ficha/aprendices.');
        $this->assertTrue($motor < $primeraEscritura, 'Orden O1: el mutex del motor antes de ficha/aprendices.');
    }
}

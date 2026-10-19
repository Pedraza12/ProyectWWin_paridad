<?php

namespace Tests\Feature;

use App\Models\Apprentice;
use App\Models\ClassGroup;
use App\Models\GeneralUser;
use App\Models\Instructor;
use App\Models\KnowledgeNetwork;
use App\Models\MotorConfig;
use App\Models\Project;
use App\Models\Similarity;
use App\Models\TrainingProgram;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// D-2b (concurrencia): la re-verificación dentro de destroy debe ser una
// LECTURA DE BLOQUEO (actual): un par confirmado por otra conexión —aunque la
// instantánea REPEATABLE READ de la transacción sea anterior— debe verse y el
// borrado responder 409 conservando propuesta y par. La creación manual
// comparte el mutex del motor.
class BorradoConcurrenciaTest extends TestCase
{
    use DatabaseTransactions;

    private const MSG = 'No puedes eliminar una propuesta con similitudes compartidas: la evidencia pertenece también a las demás propuestas.';

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
            'codigo' => 'co-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha concurrencia',
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

    private function proyecto(GeneralUser $creador, ClassGroup $ficha, string $estado, string $titulo): Project
    {
        return Project::create([
            'titulo' => $titulo,
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
            'area_aplicacion' => 'Desarrollo Web',
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

    public function test_un_par_confirmado_por_otra_conexion_se_ve_y_bloquea_el_borrado(): void
    {
        $aux = $this->conexionAuxiliar();

        // Escenario comprometido por la conexión auxiliar: la principal no debe
        // retener locks sobre las filas referenciadas por el par (FK del INSERT).
        // El creador se inserta por SQL directo: el hook `creating` de
        // GeneralUser genera `username` con una consulta por la conexión por
        // defecto (principal) que fijaría su instantánea antes de estos commits.
        $sufijo = uniqid();
        $aux->insert(
            'INSERT INTO general_users (nombre, apellido, correo, username, password, rol, estado, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 1, NOW(), NOW())',
            ['Creador', 'Concurrente', 'creador.' . $sufijo . '@concurrencia.local', 'creador.' . $sufijo, Hash::make('123456'), 'aprendiz']
        );
        $creadorId = (int) $aux->getPdo()->lastInsertId();

        $y = new Project([
            'titulo' => 'Inventarios corpus concurrente',
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
            'area_aplicacion' => 'Desarrollo Web',
            'objetivo_general' => 'Controlar los inventarios del almacén con alertas por stock mínimo.',
            'estado' => 'aprobado',
            'id_creador' => $creadorId,
        ]);
        $y->setConnection('mysql_aux');
        $y->save();

        $x = new Project([
            'titulo' => 'Inventarios objetivo concurrente',
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes.',
            'area_aplicacion' => 'Desarrollo Web',
            'objetivo_general' => 'Controlar los inventarios del almacén con alertas por stock mínimo.',
            'estado' => 'pendiente',
            'id_creador' => $creadorId,
        ]);
        $x->setConnection('mysql_aux');
        $x->save();

        // Limpieza diferida: InnoDB NO libera los locks al hacer rollback a un
        // savepoint, así que las filas confirmadas por la auxiliar se eliminan
        // después del rollback del trait (los callbacks corren en orden de
        // registro: primero el rollback de la transacción principal, luego esto).
        $parId = null;
        $filaConfig = null;
        $configCreado = false;
        $this->beforeApplicationDestroyed(function () use (&$parId, &$filaConfig, &$configCreado, $creadorId) {
            $aux = DB::connection('mysql_aux');
            if ($parId) {
                $aux->delete('DELETE FROM similarities WHERE id = ?', [$parId]);
            }
            $aux->delete('DELETE FROM projects WHERE id_creador = ?', [$creadorId]);
            if ($configCreado && $filaConfig) {
                $aux->delete('DELETE FROM motor_configs WHERE id = ?', [$filaConfig->id]);
            }
            $aux->delete('DELETE FROM general_users WHERE id = ?', [$creadorId]);
        });

        // Premisa (REPEATABLE READ sin overrides — verificado): la principal ve
        // el escenario comprometido y aún sin pares; su instantánea quedará
        // antes del commit ajeno y la lectura de bloqueo deberá ver el par.
        $this->assertTrue($aux->table('projects')->where('id', $x->id)->exists());
        $this->assertTrue(Project::whereKey($x->id)->exists());
        $this->assertFalse(Similarity::where('id_proyecto_1', $x->id)->orWhere('id_proyecto_2', $x->id)->exists());

        // T2 concurrente (conexión auxiliar): mutex del motor + par (Y,X) + COMMIT.
        $aux->beginTransaction();
        try {
            $filaConfig = $aux->selectOne('SELECT id FROM motor_configs ORDER BY id LIMIT 1');
            if (!$filaConfig) {
                $aux->insert('INSERT INTO motor_configs (umbral, meses, created_at, updated_at) VALUES (0.30, 12, NOW(), NOW())');
                $configCreado = true;
                $filaConfig = $aux->selectOne('SELECT id FROM motor_configs ORDER BY id LIMIT 1');
            }
            $aux->selectOne('SELECT id FROM motor_configs WHERE id = ? FOR UPDATE', [$filaConfig->id]);

            $p1 = min((int) $y->id, (int) $x->id);
            $p2 = max((int) $y->id, (int) $x->id);
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
        $this->assertFalse(Similarity::where('id_proyecto_1', $x->id)->orWhere('id_proyecto_2', $x->id)->exists());

        // El borrado debe ver el par YA CONFIRMADO (lectura de bloqueo) y rechazar.
        DB::connection()->flushQueryLog();
        DB::connection()->enableQueryLog();

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/projects/' . $x->id)
            ->assertStatus(409)
            ->assertJsonPath('message', self::MSG);

        $lecturaBloqueo = collect(DB::connection()->getQueryLog())
            ->pluck('query')
            ->contains(fn ($q) => str_contains($q, 'similarities') && str_contains($q, 'for update'));
        DB::connection()->disableQueryLog();
        $this->assertTrue($lecturaBloqueo, 'La re-verificación debe ser una lectura de bloqueo (for update).');

        // Persistencia real: X sigue (y su estado), el par confirmado sigue.
        $this->assertDatabaseHas('projects', ['id' => $x->id, 'estado' => 'pendiente']);
        $this->assertSame(1, (int) $aux->selectOne('SELECT COUNT(*) AS c FROM similarities WHERE id = ?', [$parId])->c);
        $this->assertSame(1, (int) $aux->selectOne('SELECT COUNT(*) AS c FROM projects WHERE id = ?', [$x->id])->c);

        // (La limpieza de las filas confirmadas por la auxiliar está registrada
        // como callback posterior al rollback del trait.)
    }

    public function test_el_store_manual_adquiere_el_mutex_del_motor(): void
    {
        [$inst, $ap1, $ap2, $ficha] = $this->escenario();

        $a = $this->proyecto($ap1, $ficha, 'pendiente', 'Inventarios manual A');
        $b = $this->proyecto($ap2, $ficha, 'aprobado', 'Inventarios manual B');
        MotorConfig::firstOrCreate([], ['umbral' => 0.30, 'meses' => 12]);

        DB::connection()->flushQueryLog();
        DB::connection()->enableQueryLog();

        $this->como($this->usuario('admin'))->postJson('/v1/similarities', [
            'porcentaje' => 80,
            'id_proyecto_1' => $a->id,
            'id_proyecto_2' => $b->id,
        ])->assertCreated();

        $mutex = collect(DB::connection()->getQueryLog())
            ->pluck('query')
            ->contains(fn ($q) => str_contains($q, 'motor_configs') && str_contains($q, 'for update'));
        DB::connection()->disableQueryLog();

        $this->assertTrue($mutex, 'El store manual debe adquirir el mutex del motor (for update).');

        $this->assertDatabaseHas('similarities', [
            'id_proyecto_1' => min((int) $a->id, (int) $b->id),
            'id_proyecto_2' => max((int) $a->id, (int) $b->id),
        ]);
    }

    public function test_el_store_manual_inicializa_y_bloquea_el_motor_sin_fila_previa(): void
    {
        [$inst, $ap1, $ap2, $ficha] = $this->escenario();
        $this->assertSame(0, MotorConfig::count(), 'premisa: motor sin fila previa');

        $a = $this->proyecto($ap1, $ficha, 'pendiente', 'Inventarios génesis A');
        $b = $this->proyecto($ap2, $ficha, 'aprobado', 'Inventarios génesis B');

        DB::connection()->flushQueryLog();
        DB::connection()->enableQueryLog();
        $this->como($this->usuario('admin'))->postJson('/v1/similarities', [
            'porcentaje' => 70,
            'id_proyecto_1' => $a->id,
            'id_proyecto_2' => $b->id,
        ])->assertCreated();
        $mutex = collect(DB::connection()->getQueryLog())
            ->pluck('query')
            ->contains(fn ($q) => str_contains($q, 'motor_configs') && str_contains($q, 'for update'));
        DB::connection()->disableQueryLog();

        $this->assertTrue($mutex, 'store debe inicializar y bloquear el motor.');
        $this->assertDatabaseHas('similarities', [
            'id_proyecto_1' => min((int) $a->id, (int) $b->id),
            'id_proyecto_2' => max((int) $a->id, (int) $b->id),
            'porcentaje' => 70,
        ]);
        $this->assertSame(1, MotorConfig::count(), 'la fila del motor queda inicializada con la operación.');
    }
}

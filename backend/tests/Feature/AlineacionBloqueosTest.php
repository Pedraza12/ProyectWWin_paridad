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
use Illuminate\Http\Exceptions\HttpResponseException;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Symfony\Component\Process\Process;
use Tests\TestCase;

// D-2b (alineación O1): el mutex de MotorConfig se adquiere SIEMPRE al inicio
// de las transacciones que tocan propuestas o similitudes (enviar, update,
// destroy, store, detectar, recalcular) => orden global MC → projects/
// similarities, sin ciclos AB-BA entre destroy y enviar/update.
class AlineacionBloqueosTest extends TestCase
{
    use DatabaseTransactions;

    private const MSG = 'No puedes eliminar una propuesta con similitudes compartidas: la evidencia pertenece también a las demás propuestas.';

    /** @var string[] */
    private array $archivosTemporales = [];

    protected function setUp(): void
    {
        parent::setUp();

        $this->beforeApplicationDestroyed(function () {
            foreach ($this->archivosTemporales as $archivo) {
                if (is_file($archivo)) {
                    @unlink($archivo);
                }
            }
        });
    }

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Bloqueos',
            'correo' => $rol . '.' . uniqid() . '@bloqueos.local',
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
            'codigo' => 'al-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha bloqueos',
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
        $id = (int) $creada->json('id');
        $this->como($aprendiz)->postJson('/v1/projects/' . $id . '/enviar')->assertOk();

        return $id;
    }

    private function conexionAuxiliar()
    {
        $base = config('database.connections.mysql');
        $this->assertSame('proyectwin_test', $base['database'], 'La prueba concurrente exige proyectwin_test.');
        config(['database.connections.mysql_aux' => $base]);
        DB::purge('mysql_aux');

        $aux = DB::connection('mysql_aux');
        $aux->statement('SET SESSION innodb_lock_wait_timeout = 5');

        return $aux;
    }

    // Escenario comprometido por la auxiliar (visible para la principal y para
    // el proceso hijo) + limpieza diferida posterior al rollback del trait.
    private function escenarioAux(string $estado, int $n = 1): array
    {
        $aux = $this->conexionAuxiliar();
        $sufijo = uniqid();
        $aux->insert(
            'INSERT INTO general_users (nombre, apellido, correo, username, password, rol, estado, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 1, NOW(), NOW())',
            ['Creador', 'Bloqueos', 'creador.' . $sufijo . '@bloqueos.local', 'creador.' . $sufijo, Hash::make('123456'), 'aprendiz']
        );
        $creadorId = (int) $aux->getPdo()->lastInsertId();

        $ids = [];
        for ($i = 0; $i < $n; $i++) {
            $p = new Project($this->contenido('Inventarios bloqueos ' . $i . ' ' . $sufijo) + [
                'estado' => $estado,
                'id_creador' => $creadorId,
            ]);
            $p->setConnection('mysql_aux');
            $p->save();
            $ids[] = (int) $p->id;
        }

        $filaConfig = $aux->selectOne('SELECT id FROM motor_configs ORDER BY id LIMIT 1');
        $configCreado = false;
        if (!$filaConfig) {
            $aux->insert('INSERT INTO motor_configs (umbral, meses, created_at, updated_at) VALUES (0.30, 12, NOW(), NOW())');
            $configCreado = true;
            $filaConfig = $aux->selectOne('SELECT id FROM motor_configs ORDER BY id LIMIT 1');
        }

        $this->beforeApplicationDestroyed(function () use ($aux, $creadorId, $configCreado, $filaConfig) {
            $aux->delete('DELETE FROM projects WHERE id_creador = ?', [$creadorId]);
            if ($configCreado && $filaConfig) {
                $aux->delete('DELETE FROM motor_configs WHERE id = ?', [$filaConfig->id]);
            }
            $aux->delete('DELETE FROM general_users WHERE id = ?', [$creadorId]);
        });

        return [$aux, $creadorId, $ids];
    }

    private function marcar(string $sufijo): string
    {
        $ruta = tempnam(sys_get_temp_dir(), 'ali_' . $sufijo . '_');
        @unlink($ruta);
        $this->archivosTemporales[] = $ruta;

        return $ruta;
    }

    private function lanzarHijo(array $lineas): Process
    {
        $script = tempnam(sys_get_temp_dir(), 'ali_hijo_') . '.php';
        $this->archivosTemporales[] = $script;

        file_put_contents($script, "<?php\n"
            . 'require ' . var_export(base_path('vendor/autoload.php'), true) . ";\n"
            . '$app = require ' . var_export(base_path('bootstrap/app.php'), true) . ";\n"
            . '$app->make(Illuminate\\Contracts\\Console\\Kernel::class)->bootstrap();' . "\n"
            . implode("\n", $lineas) . "\n");

        $proceso = new Process([PHP_BINARY, $script], base_path(), ['APP_ENV' => 'testing']);
        $proceso->setTimeout(30);
        $proceso->start();

        return $proceso;
    }

    private function esperarMarcador(string $ruta, int $timeoutMs = 8000): void
    {
        $fin = microtime(true) + $timeoutMs / 1000;
        while (microtime(true) < $fin) {
            if (file_exists($ruta)) return;
            usleep(50000);
        }
        $this->fail('El proceso hijo no alcanzó el marcador esperado: ' . $ruta);
    }

    // ---------------------------------------------------------------- Orden O1

    public function test_enviar_adquiere_el_mutex_antes_de_la_propuesta(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();

        $creada = $this->como($ap2)
            ->postJson('/v1/projects', $this->contenido('Inventarios O1 orden') + ['id_creador' => $ap2->id])
            ->assertCreated();
        $id = (int) $creada->json('id');

        DB::connection()->flushQueryLog();
        DB::connection()->enableQueryLog();
        $this->como($ap2)->postJson('/v1/projects/' . $id . '/enviar')->assertOk();
        $consultas = collect(DB::connection()->getQueryLog())->pluck('query');
        DB::connection()->disableQueryLog();

        $idxMc = $consultas->search(fn ($q) => str_contains($q, 'motor_configs') && str_contains($q, 'for update'));
        $idxProyecto = $consultas->search(fn ($q) => str_contains($q, 'projects') && str_contains($q, 'for update'));

        $this->assertNotFalse($idxMc, 'enviar debe bloquear el mutex del motor.');
        $this->assertNotFalse($idxProyecto, 'enviar debe bloquear la propuesta.');
        $this->assertLessThan($idxProyecto, $idxMc, 'El mutex debe adquirirse ANTES que la propuesta.');
    }

    public function test_update_adquiere_el_mutex_antes_de_la_propuesta(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();
        $id = $this->crearYEnviar($ap2, 'Inventarios O1 update');

        DB::connection()->flushQueryLog();
        DB::connection()->enableQueryLog();
        $this->como($ap2)
            ->putJson('/v1/projects/' . $id, ['titulo' => 'Inventarios O1 update editada'])
            ->assertOk();
        $consultas = collect(DB::connection()->getQueryLog())->pluck('query');
        DB::connection()->disableQueryLog();

        $idxMc = $consultas->search(fn ($q) => str_contains($q, 'motor_configs') && str_contains($q, 'for update'));
        $idxUpdate = $consultas->search(fn ($q) => str_starts_with($q, 'update `projects`'));

        $this->assertNotFalse($idxMc, 'update debe bloquear el mutex del motor.');
        $this->assertNotFalse($idxUpdate, 'update debe modificar la propuesta.');
        $this->assertLessThan($idxUpdate, $idxMc, 'El mutex debe adquirirse ANTES de tocar la propuesta.');
    }

    // ------------------------------------------------- Carreras con proceso real

    public function test_sin_interbloqueo_entre_enviar_y_el_mutex_ajeno(): void
    {
        [$aux, $creadorId, $ids] = $this->escenarioAux('borrador');
        $x = $ids[0];
        $admin = $this->usuario('admin');
        $this->como($admin); // fija la instantánea (ve el escenario comprometido)

        $mLock = $this->marcar('lock');
        $mX = $this->marcar('x');
        $proceso = $this->lanzarHijo([
            '\Illuminate\Support\Facades\DB::beginTransaction();',
            '\Illuminate\Support\Facades\DB::table(\'motor_configs\')->orderBy(\'id\')->lockForUpdate()->first();',
            'file_put_contents(' . var_export($mLock, true) . ", '1');",
            'usleep(1200000);',
            '\Illuminate\Support\Facades\DB::table(\'projects\')->where(\'id\', ' . $x . ')->lockForUpdate()->first();',
            'file_put_contents(' . var_export($mX, true) . ", '1');",
            '\Illuminate\Support\Facades\DB::rollBack();',
        ]);

        $this->esperarMarcador($mLock);
        $this->como($admin)->postJson('/v1/projects/' . $x . '/enviar')->assertOk();

        $proceso->wait();
        $this->assertTrue($proceso->isSuccessful(), 'El hijo no debe recibir 1213: ' . $proceso->getErrorOutput());
        $this->assertFileExists($mX, 'El hijo debió adquirir la fila X mientras el envío esperaba el mutex.');
        $this->assertSame('pendiente', Project::find($x)->estado);
    }

    public function test_sin_interbloqueo_entre_update_y_el_mutex_ajeno(): void
    {
        [$aux, $creadorId, $ids] = $this->escenarioAux('pendiente');
        $x = $ids[0];
        $admin = $this->usuario('admin');
        $this->como($admin);

        $mLock = $this->marcar('lock');
        $mX = $this->marcar('x');
        $proceso = $this->lanzarHijo([
            '\Illuminate\Support\Facades\DB::beginTransaction();',
            '\Illuminate\Support\Facades\DB::table(\'motor_configs\')->orderBy(\'id\')->lockForUpdate()->first();',
            'file_put_contents(' . var_export($mLock, true) . ", '1');",
            'usleep(1200000);',
            '\Illuminate\Support\Facades\DB::table(\'projects\')->where(\'id\', ' . $x . ')->lockForUpdate()->first();',
            'file_put_contents(' . var_export($mX, true) . ", '1');",
            '\Illuminate\Support\Facades\DB::rollBack();',
        ]);

        $this->esperarMarcador($mLock);
        $this->como($admin)
            ->putJson('/v1/projects/' . $x, ['titulo' => 'Inventarios bloqueos editada ' . uniqid()])
            ->assertOk();

        $proceso->wait();
        $this->assertTrue($proceso->isSuccessful(), 'El hijo no debe recibir 1213: ' . $proceso->getErrorOutput());
        $this->assertFileExists($mX, 'El hijo debió adquirir la fila X mientras la edición esperaba el mutex.');
    }

    public function test_el_borrado_gana_la_carrera_sin_500(): void
    {
        [$aux, $creadorId, $ids] = $this->escenarioAux('borrador');
        $x = $ids[0];
        $admin = $this->usuario('admin');
        $this->como($admin);

        $mLock = $this->marcar('lock');
        $mFin = $this->marcar('fin');
        $proceso = $this->lanzarHijo([
            '\Illuminate\Support\Facades\DB::beginTransaction();',
            '\Illuminate\Support\Facades\DB::table(\'motor_configs\')->orderBy(\'id\')->lockForUpdate()->first();',
            'file_put_contents(' . var_export($mLock, true) . ", '1');",
            'usleep(1200000);',
            '\Illuminate\Support\Facades\DB::table(\'projects\')->where(\'id\', ' . $x . ')->delete();',
            '\Illuminate\Support\Facades\DB::commit();',
            'file_put_contents(' . var_export($mFin, true) . ", '1');",
        ]);

        $this->esperarMarcador($mLock);
        $this->como($admin)
            ->postJson('/v1/projects/' . $x . '/enviar')
            ->assertStatus(422)
            ->assertJsonPath('message', 'La propuesta ya no existe o fue modificada; actualiza la lista e inténtalo de nuevo.');

        $proceso->wait();
        $this->assertTrue($proceso->isSuccessful(), 'El hijo no debe recibir 1213: ' . $proceso->getErrorOutput());
        $this->assertSame(0, (int) $aux->selectOne('SELECT COUNT(*) AS c FROM projects WHERE id = ?', [$x])->c);
    }

    // Variante determinista de la carrera anterior: el modelo cargado por el
    // binding se elimina antes de que `enviar` adquiera el lock (el borrado
    // ganó la ventana). Debe responder 422 con el mensaje de la carrera,
    // nunca 500 ni el mensaje de un envío ya en revisión.
    public function test_enviar_una_propuesta_eliminada_entre_la_carga_y_el_envio_responde_422(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();

        $creada = $this->como($ap2)
            ->postJson('/v1/projects', $this->contenido('Inventarios eliminados antes del envío') + ['id_creador' => $ap2->id])
            ->assertCreated();
        $project = Project::findOrFail((int) $creada->json('id'));

        // La fila desaparece entre la carga (binding) y el envío (lock).
        $project->delete();

        $request = Request::create('/v1/projects/' . $project->id . '/enviar', 'POST');
        $request->setUserResolver(fn () => $ap2);

        try {
            app(\App\Http\Controllers\Api\ProjectController::class)->enviar($request, $project);
            $this->fail('El envío de una propuesta eliminada debía abortar con 422.');
        } catch (HttpResponseException $e) {
            $this->assertSame(422, $e->getResponse()->getStatusCode());
            $this->assertSame(
                'La propuesta ya no existe o fue modificada; actualiza la lista e inténtalo de nuevo.',
                $e->getResponse()->getData(true)['message'] ?? null
            );
        }

        $this->assertDatabaseMissing('projects', ['id' => $project->id]);
    }

    public function test_el_reenvio_gana_y_el_borrado_responde_409_con_evidencia(): void
    {
        [$inst, $ap1, $ap2] = $this->escenario();

        $corpus = $this->crearYEnviar($ap1, 'Inventarios O1 corpus');
        $this->como($inst)->putJson('/v1/projects/' . $corpus, ['estado' => 'aprobado'])->assertOk();

        $id = $this->crearYEnviar($ap2, 'Inventarios O1 ciclo');
        $this->como($inst)->putJson('/v1/projects/' . $id, ['estado' => 'rechazado'])->assertOk();
        $this->como($ap2)
            ->putJson('/v1/projects/' . $id, ['titulo' => 'Inventarios O1 ciclo con diferencial'])
            ->assertOk();
        // El reenvío "gana": queda pendiente con detección nueva e histórica.
        $this->como($ap2)->postJson('/v1/projects/' . $id . '/enviar')->assertOk();
        $this->assertSame('pendiente', Project::find($id)->estado);

        $paresAntes = Similarity::where(function ($q) use ($id) {
            $q->where('id_proyecto_1', $id)->orWhere('id_proyecto_2', $id);
        })->count();

        $this->como($this->usuario('admin'))
            ->deleteJson('/v1/projects/' . $id)
            ->assertStatus(409)
            ->assertJsonPath('message', self::MSG);

        $this->assertSame($paresAntes, Similarity::where(function ($q) use ($id) {
            $q->where('id_proyecto_1', $id)->orWhere('id_proyecto_2', $id);
        })->count());
        $this->assertDatabaseHas('projects', ['id' => $id, 'estado' => 'pendiente']);
    }
}

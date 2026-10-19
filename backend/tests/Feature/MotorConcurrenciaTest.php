<?php

namespace Tests\Feature;

use App\Models\Project;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Symfony\Component\Process\Process;
use Tests\TestCase;

// C-04 (concurrencia real del motor): dos detecciones simultáneas sobre la
// misma propuesta deben dejar UN solo par vigente. El mutex de MotorConfig
// serializa: el segundo proceso, al entrar, refresca el par que el primero
// dejó comprometido (su snapshot se establece DESPUÉS del commit ajeno) en
// vez de duplicarlo. Nada corre por la conexión principal del trait: su
// instantánea (REPEATABLE READ) haría ver el par como inexistente.
class MotorConcurrenciaTest extends TestCase
{
    use DatabaseTransactions;

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

    // Fixture comprometida por la auxiliar (visible para los procesos hijos):
    // Y aprobada + X pendiente, misma ficha/programa, texto idéntico, fila del
    // motor y limpieza diferida posterior al rollback del trait (InnoDB NO
    // libera locks al volver de un savepoint: borrar antes bloquearía).
    private function escenarioAux(): array
    {
        $aux = $this->conexionAuxiliar();
        $sufijo = uniqid();

        // Los usuarios van por SQL directo: el hook `creating` de GeneralUser
        // consultaría por la conexión principal y fijaría su instantánea.
        $aux->insert(
            'INSERT INTO general_users (nombre, apellido, correo, username, password, rol, estado, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 1, NOW(), NOW())',
            ['Instructor', 'Concurrencia', 'instructor.' . $sufijo . '@concurrencia.local', 'instructor.' . $sufijo, Hash::make('123456'), 'instructor']
        );
        $instructorUserId = (int) $aux->getPdo()->lastInsertId();

        $aux->insert(
            'INSERT INTO general_users (nombre, apellido, correo, username, password, rol, estado, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 1, NOW(), NOW())',
            ['Creador', 'Concurrencia', 'creador.' . $sufijo . '@concurrencia.local', 'creador.' . $sufijo, Hash::make('123456'), 'aprendiz']
        );
        $creadorId = (int) $aux->getPdo()->lastInsertId();

        $aux->insert('INSERT INTO knowledge_networks (nombre, created_at, updated_at) VALUES (?, NOW(), NOW())', ['Red ' . $sufijo]);
        $redId = (int) $aux->getPdo()->lastInsertId();

        $aux->insert(
            'INSERT INTO training_programs (nombre, nivel, knowledge_network_id, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())',
            ['Programa ' . $sufijo, 'Tecnologo', $redId]
        );
        $programaId = (int) $aux->getPdo()->lastInsertId();

        $aux->insert(
            'INSERT INTO instructors (fecha_ingreso, id_usuario, created_at, updated_at) VALUES (?, ?, NOW(), NOW())',
            ['2024-01-01', $instructorUserId]
        );
        $instructorId = (int) $aux->getPdo()->lastInsertId();

        $aux->insert(
            'INSERT INTO class_groups (codigo, numero, nombre, estado, id_programa, id_instructor, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())',
            ['co-' . $sufijo, (string) random_int(100000, 999999), 'Ficha concurrencia', 'activo', $programaId, $instructorId]
        );
        $fichaId = (int) $aux->getPdo()->lastInsertId();

        // Texto idéntico en ambas propuestas: score alto comprobado
        // (CierreAuditoriaTest: detectar crea el par).
        $contenido = [
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes de trazabilidad.',
            'area_aplicacion' => 'Tecnología',
            'objetivo_general' => 'Controlar los inventarios del almacén con alertas por stock mínimo.',
            'objetivos_especificos' => [
                'Registrar entradas y salidas de productos.',
                'Generar reportes de trazabilidad por lote.',
            ],
            'id_creador' => $creadorId,
            'id_class_group' => $fichaId,
        ];

        $y = new Project($contenido + ['titulo' => 'Inventarios del almacén', 'estado' => 'aprobado']);
        $y->setConnection('mysql_aux');
        $y->save();

        $x = new Project($contenido + ['titulo' => 'Inventarios del almacén', 'estado' => 'pendiente']);
        $x->setConnection('mysql_aux');
        $x->save();

        $filaConfig = $aux->selectOne('SELECT id FROM motor_configs ORDER BY id LIMIT 1');
        $configCreado = false;
        if (!$filaConfig) {
            $aux->insert('INSERT INTO motor_configs (umbral, meses, created_at, updated_at) VALUES (0.30, 12, NOW(), NOW())');
            $configCreado = true;
            $filaConfig = $aux->selectOne('SELECT id FROM motor_configs ORDER BY id LIMIT 1');
        }

        $xId = (int) $x->id;
        $yId = (int) $y->id;
        $this->beforeApplicationDestroyed(function () use ($aux, $xId, $yId, $creadorId, $instructorUserId, $fichaId, $instructorId, $programaId, $redId, $filaConfig, $configCreado) {
            $aux->delete('DELETE FROM similarities WHERE id_proyecto_1 IN (?, ?) OR id_proyecto_2 IN (?, ?)', [$xId, $yId, $xId, $yId]);
            $aux->delete('DELETE FROM projects WHERE id_creador = ?', [$creadorId]);
            $aux->delete('DELETE FROM class_groups WHERE id = ?', [$fichaId]);
            $aux->delete('DELETE FROM instructors WHERE id = ?', [$instructorId]);
            $aux->delete('DELETE FROM training_programs WHERE id = ?', [$programaId]);
            $aux->delete('DELETE FROM knowledge_networks WHERE id = ?', [$redId]);
            if ($configCreado && $filaConfig) {
                $aux->delete('DELETE FROM motor_configs WHERE id = ?', [$filaConfig->id]);
            }
            $aux->delete('DELETE FROM general_users WHERE id IN (?, ?)', [$creadorId, $instructorUserId]);
        });

        return [$aux, $x, $y];
    }

    private function marcar(string $sufijo): string
    {
        $ruta = tempnam(sys_get_temp_dir(), 'mot_' . $sufijo . '_');
        @unlink($ruta);
        $this->archivosTemporales[] = $ruta;

        return $ruta;
    }

    private function lanzarHijo(array $lineas): Process
    {
        $script = tempnam(sys_get_temp_dir(), 'mot_hijo_') . '.php';
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

    private function esperarMarcador(string $ruta, int $timeoutMs = 10000): void
    {
        $fin = microtime(true) + $timeoutMs / 1000;
        while (microtime(true) < $fin) {
            if (file_exists($ruta)) return;
            usleep(50000);
        }
        $this->fail('El proceso hijo no alcanzó el marcador esperado: ' . $ruta);
    }

    public function test_dos_detecciones_simultaneas_no_duplican_el_par(): void
    {
        [$aux, $x, $y] = $this->escenarioAux();

        // Premisa: el par (X, Y) aún no existe.
        $p1 = min((int) $x->id, (int) $y->id);
        $p2 = max((int) $x->id, (int) $y->id);
        $this->assertSame(
            0,
            (int) $aux->selectOne('SELECT COUNT(*) AS c FROM similarities WHERE id_proyecto_1 = ? AND id_proyecto_2 = ?', [$p1, $p2])->c
        );

        $mLock = $this->marcar('lock');
        $resA = $this->marcar('a');
        $resB = $this->marcar('b');

        // Hijo A: adquiere el mutex del motor, avisa con el marcador y ejecuta
        // la detección REAL (~1.2s después) mientras sigue en su transacción.
        $hijoA = $this->lanzarHijo([
            '\Illuminate\Support\Facades\DB::statement(\'SET SESSION innodb_lock_wait_timeout = 5\');',
            '\Illuminate\Support\Facades\DB::beginTransaction();',
            '\Illuminate\Support\Facades\DB::table(\'motor_configs\')->orderBy(\'id\')->lockForUpdate()->first();',
            'file_put_contents(' . var_export($mLock, true) . ", '1');",
            'usleep(1200000);',
            '$n = app(\App\Similarity\Recomputador::class)->detectar(\App\Models\Project::find(' . (int) $x->id . '), false);',
            '\Illuminate\Support\Facades\DB::commit();',
            'file_put_contents(' . var_export($resA, true) . ', (string) $n);',
        ]);

        $this->esperarMarcador($mLock);

        // Hijo B: lanzado DESPUÉS del marcador. Su detección espera el mutex de
        // A; al entrar (snapshot posterior al commit ajeno) ve el par y lo
        // refresca: debe reportar 0 creaciones.
        $hijoB = $this->lanzarHijo([
            '\Illuminate\Support\Facades\DB::statement(\'SET SESSION innodb_lock_wait_timeout = 5\');',
            '$n = app(\App\Similarity\Recomputador::class)->detectar(\App\Models\Project::find(' . (int) $x->id . '), false);',
            'file_put_contents(' . var_export($resB, true) . ', (string) $n);',
        ]);

        $hijoA->wait();
        $hijoB->wait();

        $this->assertTrue($hijoA->isSuccessful(), 'El hijo A no debe recibir 1213/1205: ' . $hijoA->getErrorOutput());
        $this->assertTrue($hijoB->isSuccessful(), 'El hijo B no debe recibir 1213/1205: ' . $hijoB->getErrorOutput());

        $this->assertSame('1', file_get_contents($resA), 'A crea el par inicial.');
        $this->assertSame('0', file_get_contents($resB), 'B refresca el par existente (no lo duplica).');

        // Estado final real (por la auxiliar): UN solo par, y vigente.
        $filas = $aux->select('SELECT id, vigente FROM similarities WHERE id_proyecto_1 = ? AND id_proyecto_2 = ?', [$p1, $p2]);
        $this->assertCount(1, $filas, 'Dos detecciones simultáneas deben dejar UN solo par.');
        $this->assertSame(1, (int) $filas[0]->vigente);
    }
}

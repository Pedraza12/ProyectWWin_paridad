<?php

namespace Tests\Unit;

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

// C-03: comportamiento básico de los comandos del motor contra la BD de test:
// `similitud:calibrar` (pares + distribución + umbral sugerido con etiquetas) y
// `similitud:recalcular` (crea el par vigente y respeta --sin-notificar).
class ComandosSimilitudTest extends TestCase
{
    use DatabaseTransactions;

    private function usuario(string $rol): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol),
            'apellido' => 'Comando',
            'correo' => $rol . '.' . uniqid() . '@comandos.local',
            'password' => Hash::make('123456'),
            'rol' => $rol,
            'estado' => true,
        ]);
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
            'fecha_ingreso' => '2024-01-01',
            'id_usuario' => $instructorUser->id,
        ]);

        return ClassGroup::create([
            'codigo' => 'cmd-' . uniqid(),
            'numero' => (string) random_int(100000, 999999),
            'nombre' => 'Ficha comandos',
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

    // Texto plantilla "Inventarios": score alto comprobado (CierreAuditoriaTest).
    private function propuestaCompleta(GeneralUser $creador, ClassGroup $ficha): Project
    {
        return Project::create([
            'titulo' => 'Inventarios del almacén',
            'resumen' => 'Herramienta web para controlar inventarios de almacén con alertas de stock y reportes de trazabilidad.',
            'area_aplicacion' => 'Tecnología',
            'objetivo_general' => 'Controlar los inventarios del almacén con alertas por stock mínimo.',
            'objetivos_especificos' => [
                'Registrar entradas y salidas de productos.',
                'Generar reportes de trazabilidad por lote.',
            ],
            'estado' => 'aprobado',
            'id_creador' => $creador->id,
            'id_class_group' => $ficha->id,
        ]);
    }

    // Dos propuestas aprobadas del mismo programa (par con score alto).
    private function parAprobado(): array
    {
        $programa = $this->programa();
        $instructorUser = $this->usuario('instructor');
        $ficha = $this->ficha($programa, $instructorUser);
        $a = $this->aprendizEn($ficha);
        $b = $this->aprendizEn($ficha);

        $pA = $this->propuestaCompleta($a, $ficha);
        $pB = $this->propuestaCompleta($b, $ficha);

        return [$a, $b, $pA, $pB];
    }

    public function test_calibrar_sin_propuestas_avisa_y_termina(): void
    {
        $this->artisan('similitud:calibrar')
            ->expectsOutputToContain('No hay propuestas para analizar.')
            ->assertExitCode(0);
    }

    public function test_calibrar_muestra_el_par_y_la_distribucion_sin_persistir(): void
    {
        [, , $pA, $pB] = $this->parAprobado();
        $clave = min((int) $pA->id, (int) $pB->id) . '-' . max((int) $pA->id, (int) $pB->id);

        $this->artisan('similitud:calibrar')
            ->expectsOutputToContain($clave)
            ->expectsOutputToContain('Distribución')
            ->expectsOutputToContain('Sin etiquetas: usa --labels=archivo.json para métricas y umbral sugerido.')
            ->assertExitCode(0);

        // Calibrar es solo lectura: no crea similitudes.
        $this->assertSame(0, Similarity::count());
    }

    public function test_calibrar_con_etiquetas_sugiere_el_mejor_umbral(): void
    {
        [, , $pA, $pB] = $this->parAprobado();
        $clave = min((int) $pA->id, (int) $pB->id) . '-' . max((int) $pA->id, (int) $pB->id);

        $ruta = sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'pw-labels-' . uniqid() . '.json';
        file_put_contents($ruta, json_encode([$clave => 1]));

        try {
            $this->artisan('similitud:calibrar', ['--labels' => $ruta])
                ->expectsOutputToContain('Mejor umbral por F1:')
                ->assertExitCode(0);
        } finally {
            @unlink($ruta);
        }
    }

    public function test_recalcular_sin_notificar_crea_el_par_vigente_sin_notificaciones(): void
    {
        [, , $pA, $pB] = $this->parAprobado();
        $p1 = min((int) $pA->id, (int) $pB->id);
        $p2 = max((int) $pA->id, (int) $pB->id);

        $this->artisan('similitud:recalcular', ['--sin-notificar' => true])
            ->expectsOutputToContain('Eliminadas: 0 · creadas: 1 · total: 1')
            ->assertExitCode(0);

        $par = Similarity::where('id_proyecto_1', $p1)->where('id_proyecto_2', $p2)->first();
        $this->assertNotNull($par, 'el comando debe crear el par de propuestas similares');
        $this->assertTrue((bool) $par->vigente);
        $this->assertGreaterThanOrEqual(30, (int) $par->porcentaje);
        $this->assertSame(0, Notification::count(), '--sin-notificar no debe generar avisos');
    }

    public function test_recalcular_por_defecto_notifica_al_creador(): void
    {
        [$a, $b, $pA, $pB] = $this->parAprobado();
        $p1 = min((int) $pA->id, (int) $pB->id);
        $p2 = max((int) $pA->id, (int) $pB->id);

        $this->artisan('similitud:recalcular')->assertExitCode(0);

        $this->assertDatabaseHas('similarities', [
            'id_proyecto_1' => $p1,
            'id_proyecto_2' => $p2,
            'vigente' => 1,
        ]);

        // El aviso va al creador de la propuesta que origina el par (el orden de
        // procesamiento no está garantizado, así que se acepta cualquiera de los
        // dos creadores del par).
        $aviso = Notification::where('tipo', 'similitud')
            ->whereIn('id_usuario', [$a->id, $b->id])
            ->first();
        $this->assertNotNull($aviso, 'sin --sin-notificar el creador debe recibir el aviso de similitud');
    }
}

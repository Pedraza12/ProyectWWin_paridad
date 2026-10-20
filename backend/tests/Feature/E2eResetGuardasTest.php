<?php

namespace Tests\Feature;

use Illuminate\Console\Command;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Tests\TestCase;

// C-06: guardas de seguridad de `e2e:reset`. Solo se ejercitan las ramas de
// ABORTO (sin autorización, BD incorrecta, host incorrecto y lock presente):
// ninguna llega al reseed destructivo (migrate:fresh). La rama del lock usa una
// conexión centinela SQLite —con el nombre "proyectwin_e2e" para pasar la
// guarda— de modo que ni siquiera un fallo inesperado del flock podría tocar
// una base MySQL real.
class E2eResetGuardasTest extends TestCase
{
    use DatabaseTransactions;

    private const MENSAJE_ABORTO = 'ABORTADO: no se ejecutó ningún reset y no se tocó ninguna base de datos.';

    // Ejecuta el callback con E2E_ALLOW_RESET=1 y APP_ENV=e2e (restaura después).
    private function comoE2eAutorizado(callable $callback): void
    {
        $envPrevio = $_ENV['E2E_ALLOW_RESET'] ?? null;
        $serverPrevio = $_SERVER['E2E_ALLOW_RESET'] ?? null;
        $putenvPrevio = getenv('E2E_ALLOW_RESET');
        $appEnvPrevio = $this->app['env'];

        $_ENV['E2E_ALLOW_RESET'] = '1';
        $_SERVER['E2E_ALLOW_RESET'] = '1';
        putenv('E2E_ALLOW_RESET=1');
        $this->app['env'] = 'e2e';

        try {
            $callback();
        } finally {
            $this->app['env'] = $appEnvPrevio;
            if ($envPrevio === null) {
                unset($_ENV['E2E_ALLOW_RESET']);
            } else {
                $_ENV['E2E_ALLOW_RESET'] = $envPrevio;
            }
            if ($serverPrevio === null) {
                unset($_SERVER['E2E_ALLOW_RESET']);
            } else {
                $_SERVER['E2E_ALLOW_RESET'] = $serverPrevio;
            }
            $putenvPrevio === false ? putenv('E2E_ALLOW_RESET') : putenv('E2E_ALLOW_RESET=' . $putenvPrevio);
        }
    }

    // Ejecuta el callback SIN autorización E2E (aunque el shell la exporte).
    private function sinAutorizacionE2e(callable $callback): void
    {
        $envPrevio = $_ENV['E2E_ALLOW_RESET'] ?? null;
        $serverPrevio = $_SERVER['E2E_ALLOW_RESET'] ?? null;
        $putenvPrevio = getenv('E2E_ALLOW_RESET');

        unset($_ENV['E2E_ALLOW_RESET'], $_SERVER['E2E_ALLOW_RESET']);
        putenv('E2E_ALLOW_RESET');

        try {
            $callback();
        } finally {
            if ($envPrevio !== null) $_ENV['E2E_ALLOW_RESET'] = $envPrevio;
            if ($serverPrevio !== null) $_SERVER['E2E_ALLOW_RESET'] = $serverPrevio;
            if ($putenvPrevio !== false) putenv('E2E_ALLOW_RESET=' . $putenvPrevio);
        }
    }

    // Garantiza backend/.env.e2e durante el callback (lo crea solo si falta).
    private function conEnvE2e(callable $callback): void
    {
        $ruta = base_path('.env.e2e');
        $creado = false;

        if (!file_exists($ruta)) {
            file_put_contents($ruta, "APP_ENV=e2e\n");
            $creado = true;
        }

        try {
            $callback();
        } finally {
            if ($creado) @unlink($ruta);
        }
    }

    public function test_sin_autorizacion_explicita_aborta(): void
    {
        $this->sinAutorizacionE2e(function () {
            $this->artisan('e2e:reset')
                ->expectsOutputToContain('Falta autorización explícita: define E2E_ALLOW_RESET=1.')
                ->expectsOutputToContain(self::MENSAJE_ABORTO)
                ->assertExitCode(Command::FAILURE);
        });
    }

    public function test_base_de_datos_incorrecta_aborta(): void
    {
        $conexion = (string) config('database.default');
        $bdResuelta = (string) config("database.connections.{$conexion}.database");

        $this->conEnvE2e(function () use ($bdResuelta) {
            $this->comoE2eAutorizado(function () use ($bdResuelta) {
                $this->artisan('e2e:reset')
                    ->expectsOutputToContain('Base de datos resuelta="' . $bdResuelta . '"; solo se permite "proyectwin_e2e".')
                    ->expectsOutputToContain(self::MENSAJE_ABORTO)
                    ->assertExitCode(Command::FAILURE);
            });
        });
    }

    public function test_host_incorrecto_aborta(): void
    {
        $conexion = (string) config('database.default');
        $bdPrevia = config("database.connections.{$conexion}.database");
        $hostPrevio = config("database.connections.{$conexion}.host");

        // La BD resuelta cumple; solo el host viola la guarda.
        config([
            "database.connections.{$conexion}.database" => 'proyectwin_e2e',
            "database.connections.{$conexion}.host" => '10.255.255.1',
        ]);

        try {
            $this->conEnvE2e(function () {
                $this->comoE2eAutorizado(function () {
                    $this->artisan('e2e:reset')
                        ->expectsOutputToContain('Host de BD="10.255.255.1"; solo se permite loopback.')
                        ->expectsOutputToContain(self::MENSAJE_ABORTO)
                        ->assertExitCode(Command::FAILURE);
                });
            });
        } finally {
            config([
                "database.connections.{$conexion}.database" => $bdPrevia,
                "database.connections.{$conexion}.host" => $hostPrevio,
            ]);
        }
    }

    public function test_lock_presente_aborta_sin_ejecutar_el_reseed(): void
    {
        $rutaLock = storage_path('framework/e2e-reset.lock');
        $bloqueo = fopen($rutaLock, 'c');
        $this->assertIsResource($bloqueo, 'premisa: se pudo abrir el archivo de lock');
        $this->assertTrue(flock($bloqueo, LOCK_EX | LOCK_NB), 'premisa: el test retiene el lock exclusivo');

        $conexionPrevia = (string) config('database.default');

        // Centinela: pasa las guardas de nombre/host pero jamás podría reseedear
        // MySQL (si el flock fallara, migrate:fresh moriría en SQLite).
        config([
            'database.default' => 'e2e_guard_test',
            'database.connections.e2e_guard_test' => [
                'driver' => 'sqlite',
                'database' => 'proyectwin_e2e',
                'host' => '127.0.0.1',
                'prefix' => '',
                'foreign_key_constraints' => true,
            ],
        ]);

        try {
            $this->conEnvE2e(function () {
                $this->comoE2eAutorizado(function () {
                    $this->artisan('e2e:reset')
                        ->expectsOutputToContain('ABORTADO: ya hay un reseed E2E en curso')
                        ->assertExitCode(Command::FAILURE);
                });
            });
        } finally {
            config(['database.default' => $conexionPrevia]);
            flock($bloqueo, LOCK_UN);
            fclose($bloqueo);
        }
    }
}

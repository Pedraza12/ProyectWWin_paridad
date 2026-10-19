<?php

namespace App\Console\Commands;

use Illuminate\Console\Command;

/**
 * Reseed de la base de datos E2E (sprint final, Paso 1).
 *
 * SOLO opera sobre `proyectwin_e2e` y exige:
 *   - E2E_ALLOW_RESET=1 (autorización explícita),
 *   - APP_ENV=e2e (invocar como `php artisan e2e:reset --env=e2e`),
 *   - backend/.env.e2e presente,
 *   - base de datos resuelta = proyectwin_e2e en loopback,
 *   - lock exclusivo (evita reseeds concurrentes de workers paralelos).
 *
 * Si algo de esto falta, la ejecución ABORTA sin tocar ninguna base de datos.
 */
class E2eReset extends Command
{
    protected $signature = 'e2e:reset {--con-volumen : siembra también VolumenSeeder}';

    protected $description = 'Reinicia SOLO la base E2E (proyectwin_e2e) con guardas estrictas de seguridad.';

    private const BD_PERMITIDA = '/^proyectwin_e2e$/';

    public function handle(): int
    {
        $problemas = $this->problemasDeSeguridad();

        if ($problemas !== []) {
            foreach ($problemas as $problema) {
                $this->error($problema);
            }
            $this->error('ABORTADO: no se ejecutó ningún reset y no se tocó ninguna base de datos.');

            return self::FAILURE;
        }

        $rutaBloqueo = storage_path('framework/e2e-reset.lock');
        $bloqueo = fopen($rutaBloqueo, 'c');

        if ($bloqueo === false || ! flock($bloqueo, LOCK_EX | LOCK_NB)) {
            $this->error("ABORTADO: ya hay un reseed E2E en curso (lock: {$rutaBloqueo}).");

            if (is_resource($bloqueo)) {
                fclose($bloqueo);
            }

            return self::FAILURE;
        }

        $this->info('Reseed E2E autorizado: APP_ENV=e2e · BD='.$this->baseDeDatos().' · host='.$this->host());

        try {
            $this->call('migrate:fresh', ['--seed' => true, '--force' => true]);

            if ($this->option('con-volumen')) {
                $this->call('db:seed', ['--class' => 'VolumenSeeder', '--force' => true]);
            }
        } finally {
            flock($bloqueo, LOCK_UN);
            fclose($bloqueo);
        }

        $this->info('Reseed E2E completado sobre '.$this->baseDeDatos().'.');

        return self::SUCCESS;
    }

    /**
     * @return string[]
     */
    private function problemasDeSeguridad(): array
    {
        $problemas = [];

        if (env('E2E_ALLOW_RESET') !== '1') {
            $problemas[] = 'Falta autorización explícita: define E2E_ALLOW_RESET=1.';
        }

        if (! file_exists(base_path('.env.e2e'))) {
            $problemas[] = 'No existe backend/.env.e2e (configuración E2E obligatoria).';
        }

        if ($this->laravel->environment() !== 'e2e') {
            $problemas[] = 'APP_ENV="'.$this->laravel->environment().'"; debe ser "e2e" (usa --env=e2e).';
        }

        if (! preg_match(self::BD_PERMITIDA, $this->baseDeDatos())) {
            $problemas[] = 'Base de datos resuelta="'.$this->baseDeDatos().'"; solo se permite "proyectwin_e2e".';
        }

        if (! in_array($this->host(), ['127.0.0.1', 'localhost', '::1'], true)) {
            $problemas[] = 'Host de BD="'.$this->host().'"; solo se permite loopback.';
        }

        return $problemas;
    }

    private function baseDeDatos(): string
    {
        $conexion = (string) config('database.default');

        return (string) config("database.connections.{$conexion}.database");
    }

    private function host(): string
    {
        $conexion = (string) config('database.default');

        return (string) config("database.connections.{$conexion}.host");
    }
}

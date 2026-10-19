<?php

namespace Tests;

use Illuminate\Contracts\Console\Kernel;
use Illuminate\Foundation\Application;

trait CreatesApplication
{
    /**
     * Creates the application.
     */
    public function createApplication(): Application
    {
        $app = require __DIR__.'/../bootstrap/app.php';

        $app->make(Kernel::class)->bootstrap();

        $this->afirmarEntornoDePruebasSeguro($app);

        return $app;
    }

    /**
     * Guarda dura de aislamiento (sprint final, Paso 1).
     *
     * Las pruebas SOLO pueden ejecutarse contra APP_ENV=testing y la base de
     * datos `proyectwin_test` en loopback. Cualquier otra combinación aborta
     * durante el bootstrap, antes de cualquier setUp o escritura.
     */
    private function afirmarEntornoDePruebasSeguro(Application $app): void
    {
        $entorno = $app->environment();
        $conexion = (string) config('database.default');
        $baseDatos = (string) config("database.connections.{$conexion}.database");
        $host = (string) config("database.connections.{$conexion}.host");

        $hostSeguro = in_array($host, ['127.0.0.1', 'localhost', '::1'], true);

        if ($entorno !== 'testing' || $baseDatos !== 'proyectwin_test' || ! $hostSeguro) {
            throw new \RuntimeException(sprintf(
                'Entorno de pruebas inseguro: APP_ENV="%s", BD="%s", host="%s". '.
                'PHPUnit debe resolver a testing + proyectwin_test en loopback '.
                '(ver backend/phpunit.xml, backend/.env.testing y docs/entorno-pruebas-seguro.md).',
                $entorno,
                $baseDatos,
                $host,
            ));
        }
    }
}

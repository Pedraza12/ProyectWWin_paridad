<?php

namespace App\Providers;

use App\Mail\BrevoTransport;
use Illuminate\Support\Facades\Mail;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register any application services.
     */
    public function register(): void
    {
        //
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        // Transporte de correo por la API HTTP de Brevo (MAIL_MAILER=brevo).
        // Un solo punto de integración: todos los correos de la app salen por
        // Brevo sin cambiar los flujos de notificaciones existentes.
        Mail::extend('brevo', function (array $config) {
            return new BrevoTransport((string) ($config['key'] ?? ''));
        });
    }
}

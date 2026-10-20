<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Cuentas base del sistema. `rol` y `estado` quedan en español por
        // convención del dominio (valores visibles al usuario).
        Schema::create('general_users', function (Blueprint $table) {
            $table->id();
            $table->string('nombre');
            $table->string('apellido');
            // Credenciales institucionales: el admin crea las cuentas y el
            // documento es único por persona.
            $table->string('tipo_documento', 20)->nullable();
            $table->string('numero_documento', 40)->nullable()->unique();
            $table->string('correo')->unique();
            // Usuario de acceso (login); el correo queda para recuperación.
            $table->string('username')->nullable()->unique();
            $table->string('password');
            // Primer ingreso: la contraseña temporal debe cambiarse.
            $table->boolean('must_change_password')->default(false);
            // Contraseña temporal encriptada (Crypt): solo para exportar
            // credenciales; se limpia cuando el usuario define la definitiva.
            $table->text('password_temporal')->nullable();
            // Estado del envío de credenciales al correo personal (permite
            // reintentar sin perder la cuenta si el correo falla).
            $table->timestamp('credenciales_enviadas_en')->nullable();
            $table->string('credenciales_error', 500)->nullable();
            // text: solo data URL de imagen (png/jpg/jpeg/webp/gif) generada
            // por los clientes; máximo 65.000 caracteres. No admite URLs externas.
            $table->text('foto_url')->nullable();
            $table->enum('rol', ['aprendiz', 'instructor', 'admin']);
            $table->boolean('estado')->default(true);
            // Necesario para el flujo de restablecer contraseña (broker de Laravel).
            $table->rememberToken();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('general_users');
    }
};

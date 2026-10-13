<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Reporte de falla enviado por un usuario. También funciona como
        // bandeja de soporte por conflictos de ficha: número involucrado,
        // motivo y la respuesta con la que el admin cierra la solicitud. La
        // prioridad mostrada en la UI se deriva del tipo; no se persiste.
        Schema::create('bug_reports', function (Blueprint $table) {
            $table->id();
            $table->string('titulo')->nullable();
            $table->string('numero_ficha', 30)->nullable();
            $table->string('motivo', 120)->nullable();
            $table->text('descripcion');
            $table->enum('tipo', ['sistema', 'proyecto', 'datos', 'bug_ui', 'error_datos', 'rendimiento', 'seguridad', 'otro']);
            $table->enum('estado', ['pendiente', 'en_revision', 'resuelto', 'cerrado', 'rechazado'])->default('pendiente');
            $table->text('respuesta')->nullable();
            $table->date('fecha');

            $table->foreignId('id_usuario')->constrained('general_users')->onDelete('cascade');

            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('bug_reports');
    }
};

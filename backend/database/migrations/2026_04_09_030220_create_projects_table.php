<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Propuesta de proyecto. Nace en borrador (admite campos incompletos
        // hasta el envío) y guarda la huella del último envío para bloquear
        // reenvíos sin cambios reales. Solo se persiguen los datos que la UI
        // captura y muestra: objetivo general + objetivos específicos.
        Schema::create('projects', function (Blueprint $table) {
            $table->id();
            $table->string('titulo')->nullable();
            $table->text('resumen')->nullable();
            $table->text('palabras_clave')->nullable();
            $table->string('area_aplicacion')->nullable();
            $table->text('objetivo_general')->nullable();
            $table->json('objetivos_especificos')->nullable();
            $table->enum('estado', ['borrador', 'pendiente', 'aprobado', 'rechazado'])->default('pendiente')->index();
            $table->string('huella_envio', 40)->nullable();

            $table->foreignId('id_creador')->constrained('general_users')->onDelete('cascade');
            $table->foreignId('id_instructor_asignado')->nullable()->constrained('instructors')->onDelete('set null');
            // La propuesta pertenece históricamente a la ficha donde nació:
            // no se puede borrar la ficha si tiene propuestas (restrict).
            $table->foreignId('id_class_group')->nullable()->constrained('class_groups')->restrictOnDelete();

            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('projects');
    }
};

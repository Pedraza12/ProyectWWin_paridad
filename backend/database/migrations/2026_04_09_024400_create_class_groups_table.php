<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Ficha de formación (grupo). `estado`: 'activo' acepta uniones y
        // propuestas; 'finalizado' cierra la ficha (solo lectura, conserva
        // historial y bloquea nuevas uniones/entregas); 'anulada' conserva el
        // historial sin aceptar uniones.
        Schema::create('class_groups', function (Blueprint $table) {
            $table->id();
            $table->string('codigo')->unique();
            // El número de ficha es único; al anularse queda NULL y se libera.
            $table->string('numero')->nullable()->unique();
            $table->string('nombre');
            $table->enum('estado', ['activo', 'finalizado', 'anulada'])->default('activo');
            $table->foreignId('id_programa')->constrained('training_programs');
            $table->foreignId('id_instructor')->constrained('instructors');
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('class_groups');
    }
};

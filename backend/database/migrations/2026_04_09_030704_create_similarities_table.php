<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Par de propuestas con similitud detectada por el motor. `detalles`
        // guarda la explicación (términos compartidos) para la UI.
        Schema::create('similarities', function (Blueprint $table) {
            $table->id();
            $table->float('porcentaje');
            $table->json('detalles')->nullable();
            $table->date('fecha')->nullable();

            $table->foreignId('id_proyecto_1')->constrained('projects')->onDelete('cascade');
            $table->foreignId('id_proyecto_2')->constrained('projects')->onDelete('cascade');

            // Índice normal (no unique): se conservan varias versiones del
            // mismo par (contenido actual vs evidencia histórica) y sigue
            // cubriendo la FK de id_proyecto_1.
            $table->index(['id_proyecto_1', 'id_proyecto_2']);
            $table->timestamps();
            // Vigente = par del contenido actual; false = evidencia histórica.
            $table->boolean('vigente')->default(true)->index();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('similarities');
    }
};

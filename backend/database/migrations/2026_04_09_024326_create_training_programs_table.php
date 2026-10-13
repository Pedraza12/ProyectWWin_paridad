<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Catálogo de programas de formación. La red se referencia por FK
        // (knowledge_networks); no se denormaliza el nombre.
        // Nota: num_trimestres no forma parte del esquema vigente de ProyecTwin
        // (se retiró en el cierre de auditoría); no reintroducir la columna.
        Schema::create('training_programs', function (Blueprint $table) {
            $table->id();
            $table->string('nombre');
            $table->string('nivel');
            $table->foreignId('knowledge_network_id')->constrained('knowledge_networks');
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('training_programs');
    }
};

<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

// Parámetros del motor de similitudes (umbral + ventana del corpus).
// Tabla de fila única (id 1): el admin los ajusta desde ConfigSimilitud.
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('motor_configs', function (Blueprint $table) {
            $table->id();
            $table->float('umbral')->default(0.30);
            $table->integer('meses')->default(12);
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('motor_configs');
    }
};

<?php

namespace Tests\Feature;

use App\Models\GeneralUser;
use App\Models\Project;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// C-02: demo pública del motor (POST /v1/public/demo-similitud). Es anónima,
// valida el texto y devuelve SOLO id/título/porcentaje de las coincidencias:
// nunca autores, correos ni resúmenes (sin PII).
class DemoSimilitudPublicaTest extends TestCase
{
    use DatabaseTransactions;

    // Propuesta aprobada cuyo creador y resumen llevan marcadores únicos: si
    // alguno apareciera en la respuesta, la fuga de PII sería detectable.
    private function propuestaAprobada(): Project
    {
        $creador = GeneralUser::create([
            'nombre' => 'PII Reservado',
            'apellido' => 'No Visible',
            'correo' => 'pii.secreto@demo.local',
            'password' => Hash::make('123456'),
            'rol' => 'aprendiz',
            'estado' => true,
        ]);

        return Project::create([
            'titulo' => 'Inventarios del almacén',
            'resumen' => 'ZZ987654ZZ texto interno que jamás debe salir en la demo.',
            'palabras_clave' => 'inventarios, stock, almacén, alertas',
            'area_aplicacion' => 'Tecnología',
            'estado' => 'aprobado',
            'id_creador' => $creador->id,
        ]);
    }

    public function test_anonimo_prueba_el_motor_y_recibe_solo_titulo_y_porcentaje(): void
    {
        $proyecto = $this->propuestaAprobada();

        // Sin token: la demo es pública.
        $res = $this->postJson('/v1/public/demo-similitud', [
            'texto' => 'Inventarios del almacén con alertas de stock',
        ])->assertOk();

        $res->assertJsonStructure(['umbral', 'meses', 'total', 'sobre', 'coincidencias']);

        $coincidencias = $res->json('coincidencias');
        $this->assertNotEmpty($coincidencias);
        $this->assertContains($proyecto->id, array_column($coincidencias, 'id'));

        // Cada coincidencia expone EXACTAMENTE id, título y porcentaje.
        $this->assertSame(['id', 'titulo', 'porcentaje'], array_keys($coincidencias[0]));
        $this->assertArrayNotHasKey('resumen', $coincidencias[0]);
        $this->assertArrayNotHasKey('creator', $coincidencias[0]);
        $this->assertArrayNotHasKey('id_creador', $coincidencias[0]);

        // El cuerpo completo no filtra datos personales ni contenido interno.
        $cuerpo = $res->getContent();
        $this->assertStringNotContainsString('pii.secreto@demo.local', $cuerpo);
        $this->assertStringNotContainsString('PII Reservado', $cuerpo);
        $this->assertStringNotContainsString('No Visible', $cuerpo);
        $this->assertStringNotContainsString('ZZ987654ZZ', $cuerpo);
    }

    public function test_demo_rechaza_texto_faltante_o_invalido(): void
    {
        // Sin texto.
        $this->postJson('/v1/public/demo-similitud', [])
            ->assertStatus(422)
            ->assertJsonValidationErrors('texto');

        // Menos de 3 caracteres.
        $this->postJson('/v1/public/demo-similitud', ['texto' => 'ab'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('texto');

        // Más de 300 caracteres.
        $this->postJson('/v1/public/demo-similitud', ['texto' => str_repeat('a', 301)])
            ->assertStatus(422)
            ->assertJsonValidationErrors('texto');

        // Tipo inválido (array): 422, nunca 500.
        $this->postJson('/v1/public/demo-similitud', ['texto' => ['no', 'es', 'string']])
            ->assertStatus(422)
            ->assertJsonValidationErrors('texto');
    }
}

<?php

namespace Tests\Feature;

use Tests\TestCase;

class AppVersionTest extends TestCase
{
    // El endpoint es público y refleja la configuración vigente (sin BD ni
    // autenticación): es la fuente de la detección de actualizaciones.
    public function test_expone_la_version_configurada(): void
    {
        config([
            'app_version.android.version' => '1.10.0',
            'app_version.android.min_version' => '1.0.0',
            'app_version.android.enforce_minimum' => false,
            'app_version.android.download_url' => 'https://example.test/apk/proyecttwin.apk',
            'app_version.android.release_notes' => 'Mejoras de estabilidad',
        ]);

        $this->getJson('/v1/app-version/android')
            ->assertOk()
            ->assertJson([
                'latest_version' => '1.10.0',
                'minimum_supported_version' => '1.0.0',
                'enforce_minimum' => false,
                'download_url' => 'https://example.test/apk/proyecttwin.apk',
                'release_notes' => 'Mejoras de estabilidad',
            ]);
    }

    // Sin configuración explícita responde el contrato completo con los
    // valores por defecto (nunca claves ausentes).
    public function test_valores_por_defecto_con_contrato_completo(): void
    {
        $this->getJson('/v1/app-version/android')
            ->assertOk()
            ->assertJsonStructure([
                'latest_version',
                'minimum_supported_version',
                'enforce_minimum',
                'download_url',
                'release_notes',
            ]);
    }

    // La obligatoriedad solo se activa con configuración explícita.
    public function test_obligatoriedad_explicita(): void
    {
        config([
            'app_version.android.enforce_minimum' => true,
            'app_version.android.version' => '2.0.0',
            'app_version.android.min_version' => '1.5.0',
        ]);

        $this->getJson('/v1/app-version/android')
            ->assertOk()
            ->assertJson([
                'latest_version' => '2.0.0',
                'minimum_supported_version' => '1.5.0',
                'enforce_minimum' => true,
            ]);
    }
}

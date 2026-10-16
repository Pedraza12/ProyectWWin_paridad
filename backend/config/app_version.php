<?php

// Versión publicada de la app Android y política de actualización. Todo se
// define por variables de entorno (ver .env.example): publicar una versión
// nueva = subir el APK y actualizar estas variables, sin tocar código.
return [

    'android' => [
        // Última versión publicada. Se compara numéricamente en el cliente
        // (1.10.0 > 1.9.0), nunca de forma alfabética.
        'version' => env('ANDROID_APP_VERSION', '1.0.0'),

        // Versión mínima soportada: con `enforce_minimum` activo, los clientes
        // por debajo reciben un aviso obligatorio (no posponible). La política
        // está desactivada por defecto.
        'min_version' => env('ANDROID_APP_MIN_VERSION', '1.0.0'),
        'enforce_minimum' => filter_var(env('ANDROID_APP_ENFORCE_MINIMUM', false), FILTER_VALIDATE_BOOLEAN),

        // Enlace oficial de descarga del APK (https). Si está vacío, la app no
        // anuncia la actualización (no hay nada accionable que ofrecer).
        'download_url' => env('ANDROID_APP_DOWNLOAD_URL', ''),

        // Nota breve opcional que acompaña el aviso.
        'release_notes' => env('ANDROID_APP_RELEASE_NOTES', ''),
    ],

];

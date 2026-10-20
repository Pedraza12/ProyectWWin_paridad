<?php

// Cloudinary (fotos de perfil). Las credenciales viven solo en el servidor:
// nunca se exponen al frontend ni al APK. Si faltan, la app mantiene el guardado
// local (data URL) como antes.
return [
    'cloud_name' => env('CLOUDINARY_CLOUD_NAME'),
    'api_key' => env('CLOUDINARY_API_KEY'),
    'api_secret' => env('CLOUDINARY_API_SECRET'),
    'folder' => env('CLOUDINARY_FOLDER', 'proyecttwin/perfiles'),
];

<?php

namespace App\Services;

use GuzzleHttp\Client;
use Illuminate\Http\UploadedFile;
use RuntimeException;

/**
 * Subida firmada de imágenes a Cloudinary desde el backend.
 *
 * La firma (SHA1 de los parámetros ordenados + secreto) se calcula aquí: el
 * secreto nunca viaja al navegador ni al APK. Si las credenciales no están
 * configuradas, el controlador devuelve 422 y el cliente conserva el guardado
 * local (data URL) que ya existía.
 */
class CloudinaryService
{
    public function configurado(): bool
    {
        return filled(config('cloudinary.cloud_name'))
            && filled(config('cloudinary.api_key'))
            && filled(config('cloudinary.api_secret'));
    }

    /** @return array{foto_url: string, public_id: ?string} */
    public function subirImagen(UploadedFile $archivo): array
    {
        if (!$this->configurado()) {
            throw new RuntimeException('Cloudinary no está configurado.');
        }

        $cloud = (string) config('cloudinary.cloud_name');
        $apiKey = (string) config('cloudinary.api_key');
        $secret = (string) config('cloudinary.api_secret');
        $folder = (string) config('cloudinary.folder');
        $timestamp = time();

        // Parámetros firmados en orden alfabético (solo folder y timestamp).
        $firma = sha1("folder={$folder}&timestamp={$timestamp}{$secret}");

        $client = new Client(['timeout' => 20, 'connect_timeout' => 10, 'http_errors' => false]);
        $respuesta = $client->post("https://api.cloudinary.com/v1_1/{$cloud}/image/upload", [
            'multipart' => [
                [
                    'name' => 'file',
                    'contents' => fopen($archivo->getRealPath(), 'rb'),
                    'filename' => $archivo->getClientOriginalName() ?: 'foto.jpg',
                ],
                ['name' => 'api_key', 'contents' => $apiKey],
                ['name' => 'timestamp', 'contents' => (string) $timestamp],
                ['name' => 'folder', 'contents' => $folder],
                ['name' => 'signature', 'contents' => $firma],
            ],
        ]);

        $cuerpo = (string) $respuesta->getBody();
        if ($respuesta->getStatusCode() >= 300) {
            throw new RuntimeException("Cloudinary rechazó la imagen ({$respuesta->getStatusCode()}): {$cuerpo}");
        }

        $datos = json_decode($cuerpo, true);
        if (!is_array($datos) || empty($datos['secure_url'])) {
            throw new RuntimeException('Cloudinary no devolvió una URL válida.');
        }

        return [
            'foto_url' => (string) $datos['secure_url'],
            'public_id' => isset($datos['public_id']) ? (string) $datos['public_id'] : null,
        ];
    }
}

<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Services\CloudinaryService;
use Illuminate\Http\Request;

class MediaController extends Controller
{
    // Subida de la foto de perfil a Cloudinary. Cualquier usuario autenticado
    // puede subir SU imagen; asignarla a una cuenta sigue pasando por
    // PUT /general-users/{id} (que ya valida permisos y propiedad).
    public function uploadFoto(Request $request, CloudinaryService $cloudinary)
    {
        $request->validate([
            'foto' => 'required|image|mimes:jpeg,png,webp|max:5120',
        ]);

        if (!$cloudinary->configurado()) {
            return response()->json([
                'message' => 'Cloudinary no está configurado en el servidor.',
            ], 422);
        }

        try {
            $datos = $cloudinary->subirImagen($request->file('foto'));
        } catch (\Throwable $e) {
            return response()->json([
                'message' => 'No se pudo subir la imagen. Intenta de nuevo.',
            ], 502);
        }

        return response()->json($datos);
    }
}

<?php

namespace App\Support;

// Enmascarado de datos de identidad para la bitácora. Se aplica al escribir:
// los registros históricos no se reescriben.
class Pii
{
    // Devuelve el tipo y los últimos cuatro caracteres del documento.
    // Ejemplo: CC 12345678 -> "CC ****5678".
    public static function documento(?string $tipo, ?string $numero): ?string
    {
        $tipo = strtoupper(trim((string) $tipo));
        $numero = preg_replace('/[^0-9A-Za-z]/', '', (string) $numero);

        if ($tipo === '' && $numero === '') {
            return null;
        }

        // Con cuatro caracteres o menos no se muestra ningún dígito: de lo
        // contrario el "último cuatro" equivaldría al documento completo.
        $sufijo = strlen($numero) > 4 ? substr($numero, -4) : '****';

        return trim($tipo . ' ****' . $sufijo);
    }
}

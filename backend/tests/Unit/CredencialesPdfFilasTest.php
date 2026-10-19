<?php

namespace Tests\Unit;

use App\Models\GeneralUser;
use App\Support\CredencialesPdf;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Crypt;
use Tests\TestCase;

// BUG-017: el PDF de credenciales muestra el mensaje correcto para el temporal
// pendiente: si el cifrado guardado no se puede descifrar, se indica que debe
// restablecerse; si ya definió su contraseña, se indica que ya está establecida.
class CredencialesPdfFilasTest extends TestCase
{
    use DatabaseTransactions;

    // Modelo sin guardar: las filas del PDF se resuelven sin tocar la BD.
    private function usuario(): GeneralUser
    {
        return new GeneralUser([
            'nombre' => 'Ana',
            'apellido' => 'Gómez',
            'username' => 'AnaGo_12345',
            'correo' => 'ana.credenciales@correo.local',
            'password' => 'hash-de-prueba',
            'rol' => 'aprendiz',
            'estado' => true,
        ]);
    }

    public function test_temporal_ilegible_muestra_mensaje_de_restablecer(): void
    {
        $usuario = $this->usuario();
        $usuario->must_change_password = true;
        $usuario->password_temporal = 'no-es-un-cifrado';

        $fila = CredencialesPdf::filas(collect([$usuario]))->first();

        $this->assertSame('No se pudo recuperar; restablecer', $fila['password']);
    }

    public function test_sin_temporal_pendiente_muestra_ya_establecida(): void
    {
        $usuario = $this->usuario();
        $usuario->must_change_password = false;

        $fila = CredencialesPdf::filas(collect([$usuario]))->first();

        $this->assertSame('Contraseña ya establecida', $fila['password']);
    }

    public function test_temporal_valido_se_descifra_en_la_fila(): void
    {
        $usuario = $this->usuario();
        $usuario->must_change_password = true;
        $usuario->password_temporal = Crypt::encryptString('Temporal123');

        $fila = CredencialesPdf::filas(collect([$usuario]))->first();

        $this->assertSame('Temporal123', $fila['password']);
    }
}

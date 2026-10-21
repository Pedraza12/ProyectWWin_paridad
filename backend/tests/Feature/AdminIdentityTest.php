<?php

namespace Tests\Feature;

use App\Models\AuditLog;
use App\Models\GeneralUser;
use App\Models\Notification;
use App\Models\Project;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

// Identidad administrativa: normalización de correo, aviso al usuario,
// auditoría enmascarada del documento y rechazo de cambios no autorizados.
class AdminIdentityTest extends TestCase
{
    use DatabaseTransactions;

    private GeneralUser $admin;
    private GeneralUser $target;
    private GeneralUser $otro;
    private Project $proyecto;

    protected function setUp(): void
    {
        parent::setUp();

        $this->admin = $this->usuario('admin');
        $this->target = $this->usuario('aprendiz', 'TI', (string) random_int(10000000, 99999999));
        $this->otro = $this->usuario('aprendiz');
        $this->proyecto = Project::create([
            'titulo' => 'Propuesta identidad ' . uniqid(),
            'resumen' => 'Propuesta para comprobar relaciones.',
            'area_aplicacion' => 'Tecnología',
            'estado' => 'aprobado',
            'id_creador' => $this->target->id,
        ]);
    }

    private function usuario(string $rol, string $tipoDocumento = 'CC', ?string $numeroDocumento = null): GeneralUser
    {
        return GeneralUser::create([
            'nombre' => ucfirst($rol) . ' Identidad',
            'apellido' => 'Prueba',
            'tipo_documento' => $tipoDocumento,
            'numero_documento' => $numeroDocumento ?? (string) random_int(10000000, 99999999),
            'correo' => $rol . '.' . uniqid() . '@identidad.local',
            'username' => 'identidad_' . uniqid(),
            'password' => Hash::make('123456'),
            'rol' => $rol,
            'estado' => true,
        ]);
    }

    private function como(GeneralUser $user)
    {
        $token = $this->postJson('/v1/auth/login', [
            'username' => $user->username,
            'password' => '123456',
        ])->assertOk()->json('token');

        $this->app['auth']->forgetGuards();

        return $this->withToken($token);
    }

    public function test_admin_normaliza_correo_personal_actualiza_documento_audita_y_notifica(): void
    {
        $anteriorNumero = $this->target->numero_documento;
        $anterior = $this->target->tipo_documento . ' ' . $anteriorNumero;
        $nuevoNumero = (string) random_int(10000000, 99999999);
        $nuevoCorreo = 'persona.nueva.' . uniqid() . '@gmail.com';

        $this->como($this->admin)->putJson('/v1/general-users/' . $this->target->id, [
            'correo' => '  ' . strtoupper($nuevoCorreo) . '  ',
            'tipo_documento' => 'CC',
            'numero_documento' => $nuevoNumero,
        ])->assertOk();

        $this->target->refresh();
        $this->assertSame($nuevoCorreo, $this->target->correo);
        $this->assertSame('CC', $this->target->tipo_documento);
        $this->assertSame($nuevoNumero, $this->target->numero_documento);
        $this->assertSame($this->target->id, GeneralUser::where('correo', $nuevoCorreo)->value('id'));
        $this->assertSame($this->target->id, $this->proyecto->fresh()->id_creador);

        $auditoria = AuditLog::where('accion', 'actualizar_usuario')
            ->where('entidad_id', $this->target->id)
            ->latest('id')
            ->firstOrFail();
        $detalle = $auditoria->detalle;

        $this->assertSame(substr($anterior, -4), substr($detalle['documento_anterior'], -4));
        $this->assertSame(substr($nuevoNumero, -4), substr($detalle['documento_nuevo'], -4));
        $this->assertStringStartsWith('TI ****', $detalle['documento_anterior']);
        $this->assertStringStartsWith('CC ****', $detalle['documento_nuevo']);
        $this->assertStringNotContainsString($anteriorNumero, json_encode($detalle));
        $this->assertStringNotContainsString($nuevoNumero, json_encode($detalle));

        $notificaciones = Notification::where('id_usuario', $this->target->id)->get();
        $this->assertCount(1, $notificaciones);
        $this->assertSame('sistema', $notificaciones->first()->tipo);
        $this->assertStringNotContainsString($nuevoCorreo, $notificaciones->first()->titulo);
    }

    public function test_correo_duplicado_se_rechaza_sin_cambiar_usuario_ni_notificar(): void
    {
        $correoOriginal = $this->target->correo;

        $this->como($this->admin)->putJson('/v1/general-users/' . $this->target->id, [
            'correo' => $this->otro->correo,
        ])->assertStatus(422);

        $this->target->refresh();
        $this->assertSame($correoOriginal, $this->target->correo);
        $this->assertSame(0, Notification::where('id_usuario', $this->target->id)->count());
    }

    public function test_correo_normalizado_igual_no_notifica(): void
    {
        $correoOriginal = $this->target->correo;

        $this->como($this->admin)->putJson('/v1/general-users/' . $this->target->id, [
            'correo' => '  ' . strtoupper($correoOriginal) . '  ',
        ])->assertOk();

        $this->target->refresh();
        $this->assertSame($correoOriginal, $this->target->correo);
        $this->assertSame(0, Notification::where('id_usuario', $this->target->id)->count());

        $auditoria = AuditLog::where('accion', 'actualizar_usuario')
            ->where('entidad_id', $this->target->id)
            ->latest('id')
            ->first();
        $this->assertArrayNotHasKey('correo', $auditoria?->detalle ?? []);
    }

    public function test_alta_no_guarda_documento_completo_en_auditoria(): void
    {
        $numero = (string) random_int(10000000, 99999999);

        $respuesta = $this->como($this->admin)->postJson('/v1/general-users', [
            'nombre' => 'Nuevo',
            'apellido' => 'Personal',
            'tipo_documento' => 'CC',
            'numero_documento' => $numero,
            'correo' => 'personal.' . uniqid() . '@gmail.com',
            'rol' => 'aprendiz',
        ])->assertCreated();

        $id = $respuesta->json('usuario.id');
        $auditoria = AuditLog::where('accion', 'crear_usuario')
            ->where('entidad_id', $id)
            ->firstOrFail();

        $this->assertSame('CC ****' . substr($numero, -4), $auditoria->detalle['documento']);
        $this->assertStringNotContainsString($numero, json_encode($auditoria->detalle));
    }

    public function test_no_admin_no_puede_cambiar_documento_por_peticion_manual(): void
    {
        $tipoOriginal = $this->target->tipo_documento;
        $numeroOriginal = $this->target->numero_documento;

        $this->como($this->target)->putJson('/v1/general-users/' . $this->target->id, [
            'tipo_documento' => 'CC',
            'numero_documento' => (string) random_int(10000000, 99999999),
        ])->assertStatus(403)->assertJson([
            'message' => 'Solo un administrador puede modificar el documento de identidad.',
        ]);

        $this->target->refresh();
        $this->assertSame($tipoOriginal, $this->target->tipo_documento);
        $this->assertSame($numeroOriginal, $this->target->numero_documento);
        $this->assertSame(0, Notification::where('id_usuario', $this->target->id)->count());
    }

    public function test_actualizar_otros_campos_no_notifica_ni_audita_correo_o_documento(): void
    {
        $correoOriginal = $this->target->correo;
        $tipoOriginal = $this->target->tipo_documento;
        $numeroOriginal = $this->target->numero_documento;

        $this->como($this->admin)->putJson('/v1/general-users/' . $this->target->id, [
            'nombre' => 'Nombre Actualizado',
        ])->assertOk();

        $this->target->refresh();
        $this->assertSame('Nombre Actualizado', $this->target->nombre);
        $this->assertSame($correoOriginal, $this->target->correo);
        $this->assertSame($tipoOriginal, $this->target->tipo_documento);
        $this->assertSame($numeroOriginal, $this->target->numero_documento);
        $this->assertSame(0, Notification::where('id_usuario', $this->target->id)->count());

        $auditoria = AuditLog::where('accion', 'actualizar_usuario')
            ->where('entidad_id', $this->target->id)
            ->latest('id')
            ->firstOrFail();
        $this->assertArrayNotHasKey('correo', $auditoria->detalle);
        $this->assertArrayNotHasKey('documento', $auditoria->detalle);
        $this->assertArrayNotHasKey('documento_anterior', $auditoria->detalle);
        $this->assertArrayNotHasKey('documento_nuevo', $auditoria->detalle);
    }
}

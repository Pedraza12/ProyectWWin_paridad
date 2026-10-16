<?php

namespace Tests\Unit;

use App\Mail\BrevoTransport;
use GuzzleHttp\Client;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Middleware;
use GuzzleHttp\Psr7\Response;
use PHPUnit\Framework\TestCase;
use RuntimeException;
use Symfony\Component\Mime\Address;
use Symfony\Component\Mime\Email;

/**
 * Contrato del transporte Brevo: payload correcto hacia POST /v3/smtp/email
 * (remitente, destinatarios, copia, cuerpos y adjuntos) y errores claros.
 * Usa un cliente HTTP simulado: no hay llamadas de red.
 */
class BrevoTransportTest extends TestCase
{
    /** @var array<int, array{request: \Psr\Http\Message\RequestInterface}> */
    private array $enviadas = [];

    private function transporte(array $respuestas): BrevoTransport
    {
        $stack = HandlerStack::create(new MockHandler($respuestas));
        $stack->push(Middleware::history($this->enviadas));

        return new BrevoTransport(
            'clave-de-prueba',
            'https://api.brevo.com/v3/smtp/email',
            new Client(['handler' => $stack]),
        );
    }

    private function correo(): Email
    {
        return (new Email())
            ->from(new Address('no-responder@proyecttwin.test', 'ProyecTwin'))
            ->to(new Address('aprendiz@ejemplo.test', 'Ana Gómez'))
            ->subject('Restablece tu contraseña de ProyecTwin')
            ->text('Hola, abre el enlace para restablecer tu contraseña.')
            ->html('<p>Hola, abre el enlace para restablecer tu contraseña.</p>');
    }

    public function test_envia_el_payload_esperado_a_brevo(): void
    {
        $transport = $this->transporte([
            new Response(201, [], json_encode(['messageId' => '<abc@brevo>'])),
        ]);

        $sent = $transport->send($this->correo());

        $this->assertCount(1, $this->enviadas);
        $peticion = $this->enviadas[0]['request'];

        $this->assertSame('POST', $peticion->getMethod());
        $this->assertSame('https://api.brevo.com/v3/smtp/email', (string) $peticion->getUri());
        $this->assertSame('clave-de-prueba', $peticion->getHeaderLine('api-key'));

        $payload = json_decode((string) $peticion->getBody(), true);

        $this->assertSame('no-responder@proyecttwin.test', $payload['sender']['email']);
        $this->assertSame('ProyecTwin', $payload['sender']['name']);
        $this->assertSame('aprendiz@ejemplo.test', $payload['to'][0]['email']);
        $this->assertSame('Restablece tu contraseña de ProyecTwin', $payload['subject']);
        $this->assertStringContainsString('restablecer tu contraseña', $payload['textContent']);
        $this->assertStringContainsString('<p>Hola', $payload['htmlContent']);

        $this->assertNotNull($sent);
        $this->assertSame('<abc@brevo>', $sent->getMessageId());
    }

    public function test_incluye_copia_y_adjuntos_en_base64(): void
    {
        $transport = $this->transporte([
            new Response(201, [], json_encode(['messageId' => '<def@brevo>'])),
        ]);

        $correo = $this->correo()
            ->cc(new Address('instructor@ejemplo.test'))
            ->attach('contenido-del-pdf', 'credenciales.pdf', 'application/pdf');

        $transport->send($correo);

        $payload = json_decode((string) $this->enviadas[0]['request']->getBody(), true);

        $this->assertSame('instructor@ejemplo.test', $payload['cc'][0]['email']);
        $this->assertSame('credenciales.pdf', $payload['attachment'][0]['name']);
        $this->assertSame(
            base64_encode('contenido-del-pdf'),
            $payload['attachment'][0]['content'],
        );
    }

    public function test_error_de_brevo_lanza_excepcion_con_el_detalle(): void
    {
        $transport = $this->transporte([
            new Response(401, [], '{"message":"unrecognised IP address"}'),
        ]);

        $this->expectException(RuntimeException::class);
        $this->expectExceptionMessage('Brevo rechazó el envío (401)');

        $transport->send($this->correo());
    }
}

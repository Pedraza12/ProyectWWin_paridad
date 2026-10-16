<?php

namespace App\Mail;

use GuzzleHttp\Client;
use GuzzleHttp\ClientInterface;
use RuntimeException;
use Symfony\Component\Mailer\Envelope;
use Symfony\Component\Mailer\SentMessage;
use Symfony\Component\Mailer\Transport\TransportInterface;
use Symfony\Component\Mime\Address;
use Symfony\Component\Mime\Email;
use Symfony\Component\Mime\Message;
use Symfony\Component\Mime\MessageConverter;
use Symfony\Component\Mime\RawMessage;

/**
 * Transporte de correo por la API HTTP de Brevo (v3), sin SMTP.
 *
 * Laravel lo usa cuando `MAIL_MAILER=brevo` (ver config/mail.php y
 * AppServiceProvider). Cubre todos los correos de la app —credenciales,
 * restablecer contraseña, avisos— sin tocar los flujos existentes.
 *
 * Requisitos en la cuenta de Brevo: remitente verificado y, si la seguridad
 * por IP está activa, la IP de salida autorizada (o la restricción desactiva).
 */
class BrevoTransport implements TransportInterface
{
    public function __construct(
        private readonly string $apiKey,
        private readonly string $endpoint = 'https://api.brevo.com/v3/smtp/email',
        private readonly ?ClientInterface $client = null,
    ) {
    }

    public function send(RawMessage $message, Envelope $envelope = null): ?SentMessage
    {
        // Symfony entrega el mensaje ya convertido (los correos de Laravel
        // siempre son Email: HTML/texto con cabeceras) o, en un caso extremo,
        // un Message sin formato de correo, que se convierte aquí.
        $email = match (true) {
            $message instanceof Email => $message,
            $message instanceof Message => MessageConverter::toEmail($message),
            default => throw new RuntimeException(
                'El transporte Brevo solo admite mensajes Email (no cadenas MIME crudas).'
            ),
        };

        $envelope ??= Envelope::create($message);

        $client = $this->client ?? new Client([
            'timeout' => 20,
            'connect_timeout' => 10,
            'http_errors' => false,
        ]);

        $response = $client->request('POST', $this->endpoint, [
            'headers' => [
                'api-key' => $this->apiKey,
                'accept' => 'application/json',
                'content-type' => 'application/json',
            ],
            'json' => $this->payload($email, $envelope),
            // El status se evalúa abajo para poder registrar el detalle que
            // responde Brevo en lugar de una excepción genérica de Guzzle.
            'http_errors' => false,
        ]);

        $cuerpo = (string) $response->getBody();

        if ($response->getStatusCode() >= 300) {
            // El cuerpo de Brevo explica el motivo (remitente no verificado,
            // IP no autorizada, cuota agotada...). Queda en el log de Laravel
            // para diagnosticar; nunca se registra la clave.
            throw new RuntimeException(
                "Brevo rechazó el envío ({$response->getStatusCode()}): {$cuerpo}"
            );
        }

        $sent = new SentMessage($message, $envelope);
        $id = json_decode($cuerpo, true)['messageId'] ?? null;

        if (is_string($id) && $id !== '') {
            $sent->setMessageId($id);
        }

        return $sent;
    }

    public function __toString(): string
    {
        return 'brevo';
    }

    /** Construye el payload de POST /v3/smtp/email a partir del mensaje. */
    private function payload(Email $email, Envelope $envelope): array
    {
        $payload = [
            'sender' => $this->direccion($email->getFrom()[0] ?? $envelope->getSender()),
            'to' => $this->direcciones($email->getTo() ?: $envelope->getRecipients()),
            'subject' => (string) $email->getSubject(),
        ];

        if ($email->getCc()) {
            $payload['cc'] = $this->direcciones($email->getCc());
        }
        if ($email->getBcc()) {
            $payload['bcc'] = $this->direcciones($email->getBcc());
        }
        if ($email->getReplyTo()) {
            $payload['replyTo'] = $this->direccion($email->getReplyTo()[0]);
        }

        $html = $this->contenido($email->getHtmlBody());
        $texto = $this->contenido($email->getTextBody());

        if ($html !== null) {
            $payload['htmlContent'] = $html;
        }
        if ($texto !== null) {
            $payload['textContent'] = $texto;
        }
        // Brevo exige al menos un cuerpo: los correos de Laravel siempre traen
        // texto o HTML, pero se protege el caso límite.
        if (! isset($payload['htmlContent']) && ! isset($payload['textContent'])) {
            $payload['textContent'] = '';
        }

        $adjuntos = [];
        foreach ($email->getAttachments() as $parte) {
            $adjuntos[] = array_filter([
                'name' => $parte->getFilename() ?: 'adjunto',
                'content' => base64_encode($this->contenido($parte->getBody()) ?? ''),
            ]);
        }
        if ($adjuntos) {
            $payload['attachment'] = $adjuntos;
        }

        return $payload;
    }

    private function direccion(Address $address): array
    {
        return array_filter([
            'email' => $address->getAddress(),
            'name' => $address->getName() !== '' ? $address->getName() : null,
        ]);
    }

    /** @param Address[] $addresses */
    private function direcciones(array $addresses): array
    {
        return array_map(fn (Address $a) => $this->direccion($a), $addresses);
    }

    /** El cuerpo puede venir como string o como recurso (Symfony Mime). */
    private function contenido(mixed $cuerpo): ?string
    {
        if (is_resource($cuerpo)) {
            $cuerpo = stream_get_contents($cuerpo);
        }

        if (! is_string($cuerpo) || $cuerpo === '') {
            return null;
        }

        return $cuerpo;
    }
}

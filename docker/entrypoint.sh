#!/bin/sh
# =============================================================================
# Arranque del contenedor de ProyecTwin:
#   1. genera la config de nginx con el puerto que inyecta Railway ($PORT)
#   2. descubre paquetes y cachea la configuración de Laravel
#   3. espera la base de datos y ejecuta migraciones (con reintentos)
#   4. siembra datos demo la primera vez (RUN_SEED=true y base vacía)
#   5. levanta php-fpm (segundo plano) y nginx (primer plano)
# =============================================================================
set -e

PORT="${PORT:-8080}"
export PORT

if [ -z "${APP_KEY}" ]; then
    echo "[entrypoint] ERROR: falta la variable APP_KEY (php artisan key:generate --show)" >&2
    exit 1
fi

if [ -z "${APP_URL}" ]; then
    echo "[entrypoint] ADVERTENCIA: APP_URL vacío; define https://<dominio> para cookies y enlaces correctos" >&2
fi

echo "[entrypoint] nginx escuchará en el puerto ${PORT}"
envsubst '${PORT}' < /opt/docker/nginx.conf > /etc/nginx/http.d/default.conf

cd /app

echo "[entrypoint] descubriendo paquetes de Laravel…"
php artisan package:discover --ansi

echo "[entrypoint] cacheando configuración…"
php artisan config:cache

echo "[entrypoint] ejecutando migraciones…"
intentos=0
until php artisan migrate --force; do
    intentos=$((intentos + 1))
    if [ "$intentos" -ge 24 ]; then
        echo "[entrypoint] ERROR: la base de datos no respondió tras 2 minutos" >&2
        exit 1
    fi
    echo "[entrypoint] base de datos no disponible; reintento ${intentos}/24 en 5 s…"
    sleep 5
done

if [ "${RUN_SEED}" = "true" ]; then
    echo "[entrypoint] RUN_SEED=true: el seeder corre solo si la base está vacía"
    estado=$(php -r '
        require "/app/vendor/autoload.php";
        $app = require "/app/bootstrap/app.php";
        $app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();
        echo App\Models\GeneralUser::query()->exists() ? "con-datos" : "vacia";
    ' 2>/dev/null || echo "desconocido")

    if [ "$estado" = "vacia" ]; then
        echo "[entrypoint] base vacía: ejecutando seeder…"
        php artisan db:seed --force
    else
        echo "[entrypoint] la base ya tiene datos (${estado}); se omite el seeder"
    fi
fi

echo "[entrypoint] arrancando php-fpm y nginx…"
php-fpm -D
exec nginx -g "daemon off;"

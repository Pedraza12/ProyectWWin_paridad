# =============================================================================
# ProyecTwin MVP — imagen única: SPA (React) + API (Laravel) en el mismo origen.
#
# Railway construye esta imagen en la nube a partir de `railway up` (no hace
# falta Docker local). Etapas:
#   1) frontend  → compila el SPA con Vite (dist/)
#   2) vendor    → instala dependencias PHP de producción (composer --no-dev)
#   3) runtime   → php-fpm + nginx sirviendo el SPA desde backend/public/
#
# El contenedor escucha en $PORT (lo inyecta Railway):
#   - /v1/** y /sanctum/**  → Laravel (API + cookie CSRF de Sanctum)
#   - /up                   → healthcheck (ver railway.json)
#   - el resto              → SPA con fallback a index.html (React Router)
# =============================================================================

# ------------------------------------------------------- 1) SPA (frontend) ---
FROM node:22-alpine AS frontend

WORKDIR /build

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY frontend/ ./
# La API se consume con ruta relativa: mismo origen, sin CORS.
ENV VITE_API_URL=/v1
RUN npm run build

# ------------------------------------------- 2) dependencias PHP (Composer) ---
FROM composer:2 AS vendor

WORKDIR /app

COPY backend/composer.json backend/composer.lock ./
RUN composer install \
        --no-dev \
        --no-scripts \
        --no-interaction \
        --prefer-dist \
        --optimize-autoloader

# ------------------------------------------------- 3) runtime (fpm + nginx) ---
FROM php:8.3-fpm-alpine AS runtime

# nginx (servidor), gettext (envsubst para $PORT), tzdata (America/Bogota) y
# tini (reapa procesos y reenvía señales).
RUN apk add --no-cache nginx gettext tzdata tini

# Extensiones PHP que Laravel/MySQL/dompdf necesitan. El instalador omite las
# que ya vienen compiladas en la imagen oficial (mbstring, bcmath, opcache).
COPY --from=mlocati/php-extension-installer:latest /usr/bin/install-php-extensions /usr/local/bin/
RUN install-php-extensions pdo_mysql mbstring opcache bcmath

# php-fpm escucha solo en localhost (nginx lo alcanza por 127.0.0.1:9000) y
# conserva las variables de entorno del contenedor.
RUN printf '[www]\nlisten = 127.0.0.1:9000\nclear_env = no\n' \
        > /usr/local/etc/php-fpm.d/zz-local.conf

WORKDIR /app

# Aplicación Laravel + dependencias + SPA compilado dentro de public/
COPY backend/ ./
COPY --from=vendor /app/vendor ./vendor
COPY --from=frontend /build/dist ./public

# Directorios de escritura para php-fpm (www-data) y limpieza de cachés locales.
RUN rm -f bootstrap/cache/*.php \
    && mkdir -p \
        storage/framework/cache/data \
        storage/framework/sessions \
        storage/framework/views \
        storage/logs \
        bootstrap/cache \
    && chown -R www-data:www-data storage bootstrap/cache

# Plantilla de nginx y script de arranque (migra, cachea y levanta servicios).
COPY docker/ /opt/docker/
RUN chmod +x /opt/docker/entrypoint.sh

ENV APP_ENV=production \
    APP_DEBUG=false \
    LOG_CHANNEL=stderr \
    PORT=8080

EXPOSE 8080

ENTRYPOINT ["/sbin/tini", "--", "/opt/docker/entrypoint.sh"]

# -*- coding: utf-8 -*-
"""
Genera los diagramas técnicos del Manual Técnico de ProyecTwin (PNG, 200 dpi).
Se basan en la auditoría del código real (octubre de 2026).

Uso:  python figuras.py
Salida: ../figuras/*.png
"""
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Circle
from matplotlib.lines import Line2D

AQUI = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(AQUI, "..", "figuras"))
os.makedirs(OUT, exist_ok=True)

# Paleta sobria alineada con la identidad del proyecto (verde SENA + neutros)
VERDE = "#2a7c00"
VERDE_CLARO = "#dff0d5"
VERDE_SUAVE = "#f3f9ef"
TEAL = "#0e7490"
TEAL_CLARO = "#e2f2f6"
GRIS = "#444f5a"
GRIS_CLARO = "#eef0f2"
AMARILLO = "#8a6d00"
AMARILLO_CLARO = "#fdf3d7"
ROJO = "#a32626"
ROJO_CLARO = "#fbe6e6"
BLANCO = "#ffffff"

plt.rcParams.update({
    "font.family": ["Arial", "DejaVu Sans"],
    "font.size": 9,
    "axes.edgecolor": "#c8ced4",
    "figure.facecolor": "white",
})


def caja(ax, x, y, w, h, texto, fc=BLANCO, ec=GRIS, fs=9, bold=False, tc="#1c2530", radius=0.02):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle=f"round,pad=0,rounding_size={radius}",
                                linewidth=1.1, edgecolor=ec, facecolor=fc, zorder=2))
    ax.text(x + w / 2, y + h / 2, texto, ha="center", va="center", fontsize=fs,
            fontweight="bold" if bold else "normal", color=tc, zorder=3, linespacing=1.35)


def flecha(ax, p1, p2, color=GRIS, estilo="-|>", lw=1.3, ls="-", rad=0.0, z=2):
    ax.add_patch(FancyArrowPatch(p1, p2, arrowstyle=estilo, mutation_scale=11,
                                 color=color, linewidth=lw, linestyle=ls,
                                 connectionstyle=f"arc3,rad={rad}", zorder=z))


def lienzo(w=11.2, h=5.6):
    fig, ax = plt.subplots(figsize=(w, h))
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1)
    ax.axis("off")
    return fig, ax


def guardar(fig, nombre):
    ruta = os.path.join(OUT, nombre)
    fig.savefig(ruta, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print("OK", nombre)


# ---------------------------------------------------------------- Fig 1: Arquitectura
def fig_arquitectura():
    fig, ax = lienzo(11.2, 6.2)

    # Bandas horizontales
    for (y, h, color, etiqueta) in [
        (0.830, 0.140, VERDE_SUAVE, "CLIENTES"),
        (0.540, 0.240, GRIS_CLARO, "SERVIDOR DE APLICACIÓN (contenedor único en producción)"),
        (0.050, 0.140, TEAL_CLARO, "CAPA DE DATOS"),
    ]:
        ax.add_patch(FancyBboxPatch((0.015, y), 0.97, h, boxstyle="round,pad=0,rounding_size=0.015",
                                    linewidth=1.0, edgecolor="#b9c2c9", facecolor=color, zorder=1))
        ax.text(0.030, y + h - 0.018, etiqueta, fontsize=8, color=GRIS, fontweight="bold", va="top")

    # Clientes
    caja(ax, 0.06, 0.855, 0.38, 0.085, "SPA web — React 19 + Vite 8\n(52 rutas, sesión por cookie httpOnly)", fc=BLANCO, ec=VERDE, bold=False)
    caja(ax, 0.56, 0.855, 0.38, 0.085, "App móvil — Android (Kotlin + Compose)\ntoken Sanctum cifrado (Keystore AES-256-GCM)", fc=BLANCO, ec=VERDE)

    # Servidor: nginx + laravel
    caja(ax, 0.045, 0.575, 0.20, 0.135, "nginx (puerto $PORT)\nSirve la SPA compilada\ny enruta /v1 y /sanctum", fc=BLANCO, ec=TEAL, fs=8)
    caja(ax, 0.275, 0.575, 0.155, 0.135, "php-fpm 8.3\nFastCGI\n127.0.0.1:9000", fc=BLANCO, ec=TEAL, fs=8)
    caja(ax, 0.460, 0.640, 0.24, 0.083, "Middleware (API /v1)\nSanctum · Throttle · cuenta.activa · rol", fc=VERDE_CLARO, ec=VERDE, fs=8)
    caja(ax, 0.460, 0.545, 0.24, 0.078, "15 controladores REST + 20 modelos Eloquent\n(validación, autorización y scoping por rol)", fc=VERDE_CLARO, ec=VERDE, fs=8)
    caja(ax, 0.730, 0.640, 0.235, 0.083, "Servicios y soporte\nAltaUsuario · Notificaciones · Auditoría", fc=TEAL_CLARO, ec=TEAL, fs=8)
    caja(ax, 0.730, 0.545, 0.235, 0.078, "Motor de similitud (PHP puro)\nBM25 + n-gramas + LSA + pasajes", fc=TEAL_CLARO, ec=TEAL, fs=8)

    # Datos
    caja(ax, 0.16, 0.070, 0.30, 0.095, "MySQL / MariaDB\nBD proyectwin_mvp (18 tablas de dominio)", fc=BLANCO, ec=TEAL)
    caja(ax, 0.56, 0.070, 0.28, 0.095, "Archivos locales\nsesiones · caché · logs · PDF de credenciales", fc=BLANCO, ec=TEAL, fs=8)

    # Flujos
    flecha(ax, (0.25, 0.855), (0.195, 0.712), color=GRIS)
    flecha(ax, (0.70, 0.855), (0.615, 0.712), color=GRIS)
    ax.text(0.405, 0.800, "HTTPS · JSON · /v1  (cookie de sesión | Bearer token)", fontsize=8, color=GRIS, ha="center")
    flecha(ax, (0.245, 0.6425), (0.275, 0.6425), color=TEAL)
    flecha(ax, (0.430, 0.680), (0.460, 0.680), color=TEAL)
    flecha(ax, (0.580, 0.583), (0.730, 0.583), color=TEAL, estilo="-|>", ls="--")
    # Flujo Eloquent: baja del bloque Eloquent, cruza por el espacio libre y entra a la BD
    flecha(ax, (0.847, 0.545), (0.847, 0.480), color=TEAL)
    flecha(ax, (0.847, 0.480), (0.31, 0.480), color=TEAL)
    flecha(ax, (0.31, 0.480), (0.31, 0.165), color=TEAL)
    ax.text(0.578, 0.452, "Eloquent (PDO · consultas preparadas · transacciones)", fontsize=8, color=TEAL, ha="center")

    guardar(fig, "fig01_arquitectura.png")


# ---------------------------------------------------------------- Fig 2: Flujo de una petición
def fig_flujo_peticion():
    fig, ax = lienzo(11.2, 6.2)

    actores = [
        ("Aprendiz\n(navegador)", VERDE_SUAVE, VERDE),
        ("Panel React\nPropuestas.jsx", BLANCO, GRIS),
        ("useApi +\nrecursos.js", BLANCO, GRIS),
        ("api.js\n(CSRF + cookie)", BLANCO, GRIS),
        ("Proxy Vite\n/v1 → :8000", GRIS_CLARO, GRIS),
        ("Laravel /v1\nruta + middleware", VERDE_CLARO, VERDE),
        ("ProjectController\n(+ scoping)", VERDE_CLARO, VERDE),
        ("MySQL\nproyectwin_mvp", TEAL_CLARO, TEAL),
    ]
    n = len(actores)
    x0, dx = 0.075, 0.118
    for i, (texto, fc, ec) in enumerate(actores):
        x = x0 + i * dx
        caja(ax, x - 0.052, 0.86, 0.104, 0.11, texto, fc=fc, ec=ec, fs=6.8)
        ax.add_line(Line2D([x, x], [0.80, 0.07], color="#c3cbd2", lw=0.9, ls=(0, (3, 4))))

    pasos = [
        (0, 1, 0, "1. Abre «Mis Propuestas»"),
        (1, 2, 1, "2. solicita datos"),
        (2, 3, 2, "3. GET /v1/projects"),
        (3, 4, 3, "4. reenvía (cookie)"),
        (4, 5, 4, "5. autentica y autoriza"),
        (5, 6, 5, "6. delega en el controlador"),
        (6, 7, 6, "7. SELECT con scoping"),
        (7, 6, -1, "8. filas"),
        (6, 5, -1, "9. JSON (modelos)"),
        (5, 4, -1, "10. 200 OK"),
        (4, 3, -1, "11. responde"),
        (3, 2, -1, "12. datos"),
        (2, 1, -1, "13. estado + render"),
    ]
    y = 0.76
    for a, b, _, etiqueta in pasos:
        xa = x0 + a * dx
        xb = x0 + b * dx
        color = VERDE if xa < xb else TEAL
        flecha(ax, (xa, y), (xb, y), color=color, rad=0.32 if abs(a - b) > 1 else 0.0)
        ax.text((xa + xb) / 2, y + 0.028, etiqueta, ha="center", fontsize=7.6, color="#333c46")
        y -= 0.052
    guardar(fig, "fig02_flujo_peticion.png")


# ---------------------------------------------------------------- Fig 3: Modelo relacional (núcleo)
def fig_modelo_er():
    fig, ax = lienzo(11.4, 6.8)

    def tabla(x, top, w, alto, titulo, filas, fc=BLANCO, ec=VERDE, fs_filas=7.0):
        ax.add_patch(FancyBboxPatch((x, top - alto), w, alto, boxstyle="round,pad=0,rounding_size=0.012",
                                    linewidth=1.2, edgecolor=ec, facecolor=fc, zorder=2))
        ax.add_patch(FancyBboxPatch((x, top - 0.042), w, 0.042, boxstyle="round,pad=0,rounding_size=0.012",
                                    linewidth=1.2, edgecolor=ec, facecolor=ec, zorder=3))
        ax.text(x + w / 2, top - 0.021, titulo, ha="center", va="center", fontsize=8.4,
                color="white", fontweight="bold", zorder=4)
        ax.text(x + 0.012, top - 0.058, filas, ha="left", va="top", fontsize=fs_filas,
                color="#26313c", zorder=4, linespacing=1.5)

    # Columna 1
    tabla(0.030, 0.920, 0.165, 0.290, "general_users",
          "id (PK)\nnombre · apellido\ncorreo (UQ)\nusername (UQ)\npassword (hash)\nrol ENUM\nestado · foto_url")
    tabla(0.030, 0.585, 0.165, 0.300, "apprentices",
          "id (PK)\ncodigo (UQ)\nid_usuario FK (UQ)\nid_class_group FK (NULL)\nid_programa FK (NULL)")
    tabla(0.030, 0.245, 0.165, 0.135, "instructors", "id (PK)\nid_usuario FK (UQ)\nfecha_ingreso", fs_filas=7.0)

    # Columna 2
    tabla(0.245, 0.920, 0.160, 0.285, "class_groups",
          "id (PK)\ncodigo (UQ)\nnumero (UQ, NULL)\nnombre\nestado ENUM\nid_programa FK\nid_instructor FK")
    tabla(0.245, 0.585, 0.160, 0.190, "training_programs",
          "id (PK)\nnombre · nivel\nknowledge_network_id FK")
    tabla(0.245, 0.350, 0.160, 0.140, "knowledge_networks", "id (PK)\nnombre (UQ)")

    # Columna 3
    tabla(0.455, 0.920, 0.200, 0.440, "projects",
          "id (PK)\ntitulo · resumen\npalabras_clave\narea_aplicacion\nobjetivo_general\nobjetivos_especificos JSON\nestado ENUM (INDEX)\nhuella_envio\nid_creador FK\nid_instructor_asignado FK (NULL)\nid_class_group FK (NULL)")
    tabla(0.455, 0.430, 0.200, 0.250, "similarities",
          "id (PK)\nporcentaje DOUBLE\ndetalles JSON\nfecha · vigente BOOL (INDEX)\nid_proyecto_1 FK · id_proyecto_2 FK\nINDEX (id_proyecto_1, id_proyecto_2)")

    # Columna 4
    tabla(0.705, 0.920, 0.185, 0.165, "apprentice_projects",
          "id (PK)\nid_aprendiz FK\nid_proyecto FK\nUQ (id_aprendiz, id_proyecto)")
    tabla(0.705, 0.700, 0.185, 0.190, "comments",
          "id (PK)\ntexto\nrespuesta_a FK→comments\nid_proyecto FK · id_usuario FK")
    tabla(0.705, 0.455, 0.185, 0.190, "notifications",
          "id (PK)\ntitulo · descripcion\ntipo ENUM · enlace\nleida · fecha\nid_usuario FK")
    tabla(0.705, 0.245, 0.185, 0.150, "project_histories",
          "id (PK)\naccion · detalle JSON\nid_proyecto FK · id_usuario FK")

    # Relaciones (solo las dibujables en los espacios libres; el detalle de FK va en la franja inferior)
    flecha(ax, (0.1125, 0.585), (0.1125, 0.605), color=TEAL, lw=1.1)
    ax.text(0.120, 0.590, "1:1", fontsize=6.6, color=TEAL)

    flecha(ax, (0.198, 0.435), (0.245, 0.435), color=TEAL, lw=1.1)
    ax.text(0.2215, 0.443, "1:N", fontsize=6.6, color=TEAL, ha="center")

    flecha(ax, (0.198, 0.320), (0.245, 0.320), color=TEAL, lw=1.1)
    ax.text(0.2215, 0.328, "N:1", fontsize=6.6, color=TEAL, ha="center")

    flecha(ax, (0.325, 0.350), (0.325, 0.395), color=TEAL, lw=1.1)
    ax.text(0.331, 0.358, "N:1", fontsize=6.6, color=TEAL)

    flecha(ax, (0.405, 0.770), (0.455, 0.770), color=TEAL, lw=1.1)
    ax.text(0.430, 0.778, "1:N", fontsize=6.6, color=TEAL, ha="center")

    flecha(ax, (0.655, 0.840), (0.705, 0.840), color=TEAL, lw=1.1)
    ax.text(0.680, 0.848, "N:M", fontsize=6.6, color=TEAL, ha="center")

    flecha(ax, (0.655, 0.610), (0.705, 0.610), color=TEAL, lw=1.1)
    ax.text(0.680, 0.618, "1:N", fontsize=6.6, color=TEAL, ha="center")

    # Franja inferior con las tablas restantes y FK no dibujadas
    ax.add_patch(FancyBboxPatch((0.030, 0.018), 0.940, 0.062, boxstyle="round,pad=0,rounding_size=0.012",
                                linewidth=1.0, edgecolor="#b9c2c9", facecolor="#f7f9fa", zorder=2))
    ax.text(0.042, 0.066,
            "Otras tablas: admins (perfil 1:1 con general_users) · audit_logs · bug_reports · motor_configs · password_reset_tokens · personal_access_tokens (Sanctum) · sessions · migrations.",
            fontsize=6.8, color="#333c46", va="top")
    ax.text(0.042, 0.044,
            "Otras FK: similarities.id_proyecto_1/2 → projects.id · instructors.id → projects.id_instructor_asignado · comments/notifications/project_histories/audit_logs.id_usuario → general_users.id (CASCADE o SET NULL según la tabla).",
            fontsize=6.8, color="#333c46", va="top")
    ax.text(0.042, 0.024,
            "Esquema simplificado; el diccionario completo (tipos, nulabilidad, índices y ON DELETE) está en el capítulo 4.",
            fontsize=6.8, color=GRIS, va="top")

    guardar(fig, "fig03_modelo_er.png")


# ---------------------------------------------------------------- Fig 4: Motor de similitud
def fig_motor():
    fig, ax = lienzo(11.2, 5.2)

    caja(ax, 0.02, 0.40, 0.155, 0.32,
         "Propuesta enviada\ntítulo · palabras clave\nobjetivo general y\nespecíficos · área\nresumen", fc=BLANCO, ec=GRIS, fs=8)

    caja(ax, 0.205, 0.45, 0.14, 0.22,
         "Normalización\nminúsculas, sin tildes,\nstemming ligero,\nstopwords", fc=GRIS_CLARO, ec=GRIS, fs=7.6)

    senales = [
        ("Palabras (BM25 + bigramas + sinónimos)\npeso 0,40", VERDE_CLARO, VERDE),
        ("N-gramas de caracteres TF-IDF (3–5)\npeso 0,20", VERDE_CLARO, VERDE),
        ("Tema latente (LSA en PHP)\npeso 0,25 · se reparte si no aplica", VERDE_CLARO, VERDE),
        ("Cobertura de términos del corpus\npeso 0,15", VERDE_CLARO, VERDE),
    ]
    ys = [0.72, 0.565, 0.41, 0.255]
    for (texto, fc, ec), y in zip(senales, ys):
        caja(ax, 0.385, y, 0.30, 0.115, texto, fc=fc, ec=ec, fs=7.4)
        flecha(ax, (0.345, 0.56), (0.385, y + 0.0575), color=GRIS, rad=0.12, lw=1.0)

    caja(ax, 0.725, 0.475, 0.125, 0.20,
         "Score 0–1\n(porcentaje)\n+ pasajes\ncoincidentes", fc=TEAL_CLARO, ec=TEAL, fs=7.8, bold=True)
    for y in ys:
        flecha(ax, (0.685, y + 0.0575), (0.725, 0.575), color=GRIS, rad=-0.10, lw=1.0)

    caja(ax, 0.885, 0.55, 0.105, 0.14,
         "Umbral\n(motor_configs)\n0,05 – 0,95", fc=AMARILLO_CLARO, ec=AMARILLO, fs=7.4)
    flecha(ax, (0.85, 0.575), (0.885, 0.62), color=GRIS, lw=1.0)

    caja(ax, 0.80, 0.16, 0.19, 0.13,
         "Pares vigentes\n(similarities.vigente = 1)\n+ notificación", fc=VERDE_SUAVE, ec=VERDE, fs=7.6)
    caja(ax, 0.56, 0.16, 0.20, 0.13,
         "Histórico\n(vigente = 0)\nse conserva como\nevidencia", fc=BLANCO, ec=GRIS, fs=7.6)
    flecha(ax, (0.8875, 0.55), (0.895, 0.29), color=VERDE, lw=1.2)
    flecha(ax, (0.80, 0.225), (0.76, 0.225), color=GRIS, lw=1.0, estilo="-|>", ls="--")
    ax.text(0.775, 0.245, "archivado", fontsize=7, color=GRIS, ha="center")

    ax.text(0.02, 0.16,
            "Corpus de comparación: propuestas aprobadas del mismo programa dentro de la ventana temporal (meses).\n"
            "El motor corre al enviar, reenviar y editar en revisión; no al crear ni al aprobar (recalibrable con artisan).",
            fontsize=7.6, color=GRIS, va="top")
    guardar(fig, "fig04_motor.png")


# ---------------------------------------------------------------- Fig 5: Estados de la propuesta
def fig_estados():
    fig, ax = lienzo(11.2, 4.3)

    def nodo(x, y, texto, fc=BLANCO, ec=GRIS, w=0.15, h=0.14, fs=8.6):
        caja(ax, x, y, w, h, texto, fc=fc, ec=ec, fs=fs, bold=True)
        return (x, y, w, h)

    b = nodo(0.06, 0.62, "borrador", fc=GRIS_CLARO)
    p = nodo(0.36, 0.62, "pendiente", fc=AMARILLO_CLARO, ec=AMARILLO)
    a = nodo(0.72, 0.80, "aprobado", fc=VERDE_CLARO, ec=VERDE)
    r = nodo(0.72, 0.44, "rechazado", fc=ROJO_CLARO, ec=ROJO)

    flecha(ax, (0.21, 0.69), (0.36, 0.69), color=VERDE)
    ax.text(0.285, 0.715, "enviar\n(valida y ejecuta el motor)", fontsize=7.4, ha="center", color=GRIS)

    flecha(ax, (0.51, 0.735), (0.72, 0.855), color=VERDE)
    ax.text(0.615, 0.830, "aprobar", fontsize=7.4, color=GRIS, ha="center")
    flecha(ax, (0.51, 0.640), (0.72, 0.505), color=ROJO)
    ax.text(0.645, 0.535, "rechazar (observación)", fontsize=7.4, color=GRIS, ha="center")

    # Reenvío: rechazado → pendiente (curva inferior)
    flecha(ax, (0.77, 0.44), (0.435, 0.615), color=ROJO, rad=-0.18)
    ax.text(0.50, 0.395, "reenviar con cambios\n(archiva el par anterior\ny re-detecta)", fontsize=7.2, ha="right", color=GRIS)

    # Intervención admin: aprobado → pendiente (codo por la parte superior)
    ax.add_line(Line2D([0.725, 0.435], [0.905, 0.905], color=AMARILLO, lw=1.3))
    flecha(ax, (0.435, 0.905), (0.435, 0.765), color=AMARILLO)
    ax.text(0.5785, 0.921, "intervención admin (vuelve a revisión)", fontsize=7.2, ha="center", color=AMARILLO)

    ax.add_patch(FancyBboxPatch((0.06, 0.17), 0.86, 0.20, boxstyle="round,pad=0,rounding_size=0.015",
                                linewidth=1.0, edgecolor="#b9c2c9", facecolor="#f7f9fa"))
    ax.text(0.075, 0.325,
            "Reglas verificadas en el código (ProjectController + Project::puedeEscribir):",
            fontsize=8, fontweight="bold", color=GRIS)
    ax.text(0.075, 0.285,
            "• El aprendiz edita en borrador, en revisión y rechazado; el aprobado queda bloqueado (el admin conserva intervención excepcional).\n"
            "• Fuera de la ficha activa (o con ficha finalizada/anulada) la propuesta queda en solo lectura.\n"
            "• Solo se puede enviar con campos mínimos completos y sin cambios exactos respecto al último envío (huella sha1).",
            fontsize=7.4, color="#333c46", va="top", linespacing=1.6)
    guardar(fig, "fig05_estados_propuesta.png")


# ---------------------------------------------------------------- Fig 6: Despliegue
def fig_despliegue():
    fig, ax = lienzo(11.2, 5.4)

    # Producción (Railway)
    ax.add_patch(FancyBboxPatch((0.03, 0.08), 0.60, 0.84, boxstyle="round,pad=0,rounding_size=0.02",
                                linewidth=1.2, edgecolor=VERDE, facecolor=VERDE_SUAVE))
    ax.text(0.33, 0.885, "RAILWAY — un solo servicio (Dockerfile multi-etapa)", ha="center",
            fontsize=8.6, fontweight="bold", color=VERDE)

    caja(ax, 0.06, 0.66, 0.24, 0.16, "Etapa 1 · node:22-alpine\nnpm ci + npm run build\n(VITE_API_URL=/v1)", fc=BLANCO, ec=GRIS, fs=7.4)
    caja(ax, 0.33, 0.66, 0.27, 0.16, "Etapa 2 · composer:2\ncomposer install --no-dev\n--optimize-autoloader", fc=BLANCO, ec=GRIS, fs=7.4)
    caja(ax, 0.06, 0.42, 0.54, 0.17,
         "Etapa 3 · php:8.3-fpm-alpine + nginx + tini\n"
         "dist/ → public/ · extensiones pdo_mysql, mbstring, opcache, bcmath\n"
         "entrypoint: config:cache → migrate --force (24 reintentos) → seed opcional (RUN_SEED) → php-fpm + nginx",
         fc=BLANCO, ec=TEAL, fs=7.3)
    caja(ax, 0.06, 0.20, 0.54, 0.14,
         "Healthcheck GET /up → {\"status\":\"ok\"} · reinicio ON_FAILURE (máx. 10)\n"
         "Puerto expuesto 8080 (override con $PORT) · logs a stdout/stderr",
         fc=GRIS_CLARO, ec=GRIS, fs=7.3)
    caja(ax, 0.06, 0.09, 0.54, 0.075, "Variables: APP_KEY (obligatoria), APP_URL, DB_*, RUN_SEED, SANCTUM_STATEFUL_DOMAINS…",
         fc=AMARILLO_CLARO, ec=AMARILLO, fs=7.2)

    # MySQL plugin
    caja(ax, 0.70, 0.52, 0.27, 0.20, "Plugin MySQL (Railway)\nBD gestionada · conexión por\nvariables del proveedor\n(${{MySQL.*}})", fc=TEAL_CLARO, ec=TEAL, fs=7.6)
    flecha(ax, (0.60, 0.50), (0.70, 0.55), color=TEAL)

    # Clientes
    caja(ax, 0.70, 0.80, 0.27, 0.115, "Navegadores (SPA)\nhttps://<dominio>/", fc=BLANCO, ec=VERDE, fs=7.6)
    caja(ax, 0.70, 0.20, 0.27, 0.19, "App Android (Movil/)\nNo se despliega en Railway:\nse compila por separado (APK)\ny apunta a la API (local.properties)", fc=BLANCO, ec=GRIS, fs=7.4)
    flecha(ax, (0.835, 0.80), (0.60, 0.74), color=GRIS)
    ax.text(0.71, 0.77, "HTTPS 443", fontsize=7, color=GRIS)

    ax.text(0.5, 0.025,
            "Referencias: Dockerfile, docker/nginx.conf, docker/entrypoint.sh, railway.json (raíz del repositorio).",
            ha="center", fontsize=7.6, color=GRIS)
    guardar(fig, "fig06_despliegue.png")


# ---------------------------------------------------------------- Fig 7: Autenticación
def fig_auth():
    fig, ax = lienzo(11.2, 5.6)

    caja(ax, 0.03, 0.70, 0.26, 0.22,
         "SPA web (mismo origen)\n1) POST /sanctum/csrf-cookie\n2) POST /v1/auth/login\n→ cookie de sesión httpOnly\n+ cabecera X-XSRF-TOKEN en escrituras",
         fc=VERDE_SUAVE, ec=VERDE, fs=7.6)
    caja(ax, 0.03, 0.36, 0.26, 0.22,
         "App Android / pruebas\nPOST /v1/auth/login\n→ token Bearer «proyectwin»\ncifrado en DataStore\n(AES-256-GCM, Keystore)",
         fc=TEAL_CLARO, ec=TEAL, fs=7.6)
    caja(ax, 0.03, 0.10, 0.26, 0.16,
         "Recuperación de contraseña\nPOST /v1/auth/forgot-password\n→ enlace con token (60 min)\nPOST /v1/auth/reset-password",
         fc=BLANCO, ec=GRIS, fs=7.4)

    cadena = [
        ("auth:sanctum", "valida cookie de sesión o token Bearer; sin sesión → 401"),
        ("cuenta.activa", "cuenta suspendida (estado=0) → 403 aunque el token siga vigente"),
        ("CambioContrasenaObligatorio", "primer ingreso → 403 con must_change_password y solo permite el cambio"),
        ("rol:admin | instructor | aprendiz", "403 si el rol no está autorizado en la ruta"),
    ]
    y = 0.74
    for nombre, desc in cadena:
        caja(ax, 0.36, y, 0.27, 0.095, nombre, fc=VERDE_CLARO, ec=VERDE, fs=7.8, bold=True)
        ax.text(0.645, y + 0.047, desc, fontsize=7.4, va="center", color="#333c46")
        y -= 0.125

    caja(ax, 0.36, 0.26, 0.27, 0.10, "Controlador\nscoping por ficha y reglas de negocio", fc=BLANCO, ec=GRIS, fs=7.6)
    flecha(ax, (0.29, 0.81), (0.36, 0.7875), color=GRIS)
    flecha(ax, (0.29, 0.47), (0.36, 0.55), color=GRIS)
    flecha(ax, (0.29, 0.18), (0.36, 0.28), color=GRIS, estilo="-|>", ls="--")
    flecha(ax, (0.495, 0.26), (0.495, 0.235), color=GRIS, estilo="-|>")
    ax.text(0.50, 0.245, "403/401 en cualquier eslabón", fontsize=7.2, color=ROJO, ha="left")

    ax.text(0.5, 0.045,
            "ThrottleRequests (API_RATE_LIMIT; 60 por defecto, 600 en local/E2E) protege toda la API. "
            "Los intentos fallidos de login no tienen limitador específico por usuario (hallazgo documentado en el capítulo 7).",
            ha="center", fontsize=7.4, color=GRIS)
    guardar(fig, "fig07_autenticacion.png")


# ---------------------------------------------------------------- Fig 8: Casos de uso
def fig_casos_uso():
    fig, ax = lienzo(11.4, 6.4)

    columnas = [
        ("Visitante\n(público)", "#5b6770", [
            "Consultar el portal y el resumen del motor",
            "Probar la demo pública de similitud",
            "Iniciar sesión",
            "Recuperar / restablecer contraseña",
        ]),
        ("Aprendiz", VERDE, [
            "Gestionar mis propuestas (crear, editar, enviar, reenviar)",
            "Gestionar el equipo de la propuesta",
            "Unirme a una ficha / salir de ella",
            "Consultar mis similitudes y su detalle",
            "Ver alertas y observaciones",
            "Actualizar mi perfil y mi foto",
            "Reportar una falla (soporte)",
        ]),
        ("Instructor", TEAL, [
            "Revisar propuestas (aprobar / rechazar con observación)",
            "Gestionar mis fichas y su roster",
            "Consultar similitudes de mis fichas",
            "Ver perfiles de mis aprendices (solo lectura)",
        ]),
        ("Administrador", AMARILLO, [
            "Gestionar usuarios y credenciales (PDF, reenvío)",
            "Gestionar redes de conocimiento y programas",
            "Configurar el motor y recalcular el corpus",
            "Supervisar propuestas, fichas y similitudes",
            "Consultar la bitácora de auditoría",
            "Atender reportes de falla",
        ]),
        ("Sistema\n(eventos automáticos)", "#6b4fa1", [
            "Ejecutar el motor al enviar / reenviar",
            "Registrar auditoría e historial",
            "Generar notificaciones internas",
            "Bloquear con cambio de contraseña obligatorio",
        ]),
    ]

    import textwrap
    n = len(columnas)
    x0, ancho, gap = 0.025, 0.176, 0.019
    for i, (actor, color, casos) in enumerate(columnas):
        x = x0 + i * (ancho + gap)
        # Actor
        ax.add_patch(FancyBboxPatch((x, 0.925), ancho, 0.06,
                                    boxstyle="round,pad=0,rounding_size=0.02",
                                    linewidth=1.4, edgecolor=color, facecolor="white", zorder=2))
        ax.text(x + ancho / 2, 0.955, actor, ha="center", va="center", fontsize=8.2,
                fontweight="bold", color=color, zorder=3, linespacing=1.2)
        # Conector
        ax.add_line(Line2D([x + ancho / 2, x + ancho / 2], [0.925, 0.91], color=color, lw=1.1))
        # Casos de uso (texto ajustado a ~34 caracteres por línea)
        y = 0.895
        ax.add_line(Line2D([x + ancho / 2, x + ancho / 2], [y, 0.10], color="#d5dbe0", lw=0.9, ls=(0, (2, 3))))
        for caso in casos:
            texto = textwrap.fill(caso, width=34)
            lineas = texto.count("\n") + 1
            alto = 0.030 + 0.021 * lineas
            ax.add_patch(FancyBboxPatch((x + 0.004, y - alto), ancho - 0.008, alto,
                                        boxstyle="round,pad=0,rounding_size=0.028",
                                        linewidth=1.0, edgecolor=color, facecolor="white", zorder=2))
            ax.text(x + ancho / 2, y - alto / 2, texto, ha="center", va="center", fontsize=6.6,
                    color="#26313c", zorder=3, linespacing=1.25)
            ax.add_line(Line2D([x + ancho / 2, x + ancho / 2], [y - alto, y - alto - 0.012],
                               color="#d5dbe0", lw=0.9, ls=(0, (2, 3))))
            y -= alto + 0.012

    ax.text(0.5, 0.05,
            "Los actores heredan los casos públicos; el alcance fino de cada operación (por ficha y por estado) se documenta en los apartados 3.4, 3.5 y 7.3.",
            ha="center", fontsize=7.4, color=GRIS)
    guardar(fig, "fig08_casos_uso.png")


if __name__ == "__main__":
    fig_arquitectura()
    fig_flujo_peticion()
    fig_modelo_er()
    fig_motor()
    fig_estados()
    fig_despliegue()
    fig_auth()
    fig_casos_uso()
    print("Diagramas generados en:", OUT)

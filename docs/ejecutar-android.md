# Ejecutar la app Android de ProyecTwin (guía breve)

> Procedimiento verificado en Windows con Android Studio. El objetivo es:
> **iniciar el backend habitual (puerto 8000) y pulsar Run ▶** (emulador o dispositivo).

## 1. Requisitos del entorno

| Componente | Requisito |
|---|---|
| Sistema | Windows 10/11 |
| Backend | Laravel en marcha en `http://127.0.0.1:8000` — el mismo backend de desarrollo de la web (p. ej. `php artisan serve`) |
| Android Studio | 2025.3.4 o superior con su JBR incluido |
| SDK Android | Platform **36.1** (compileSdk 36.1, minSdk 26) instalado desde el SDK Manager |
| JDK | El **JBR embebido de Android Studio** (para el editor). Gradle usa su toolchain 17 automática (`gradle-daemon-jvm.properties`) |
| Emulador | Cualquier AVD API 26+ (validado con *Medium Phone API 36.1*) |

## 2. Abrir el proyecto

1. Android Studio → **File → Open** → seleccionar la carpeta `MVP/Movil`.
2. Esperar a la sincronización inicial de Gradle (wrapper **9.6.0**; la primera vez descarga dependencias y el JDK 17 del daemon).
3. Si en **Project Structure → SDK** aparece el SDK en rojo, elegir el JDK embebido (`jbr-17`). El proyecto ya viene configurado para usarlo.

> El proyecto **no** necesita que el frontend React esté en marcha. El backend es el servicio compartido.

## 3. Crear o seleccionar el emulador

Device Manager → crear/usar un AVD (API 26+). Botón ▶ en la barra de dispositivos.

## 4. Iniciar el backend en el puerto 8000

Es el backend de desarrollo que ya se usa para la web (no hace falta ningún servidor adicional):

```powershell
cd MVP\backend
php artisan serve
```

Verificación rápida: `http://127.0.0.1:8000/v1/config-similitud` debe responder JSON.

## 5. Dirección que usa Android

- El `debug` apunta por defecto a **`http://10.0.2.2:8000/v1/`** (definido una sola vez en `app/build.gradle.kts` → `BuildConfig.API_BASE_URL`; todos los servicios la leen desde `NetworkModule`).
- En el emulador, `10.0.2.2` es **el equipo anfitrión**. `localhost`/`127.0.0.1` dentro del emulador es el propio teléfono: no usarlos.
- El tráfico HTTP sin cifrar solo está permitido en **debug** y solo para `10.0.2.2`, `localhost` y `127.0.0.1` (`app/src/debug/res/xml/network_security_config.xml`). En **release** solo se permite HTTPS (`app/src/main/res/xml/network_security_config.xml`).
- Para apuntar a otra dirección sin tocar código: `local.properties` (`proyectwin.apiBaseUrl[.debug|.release]`) o `-Pproyectwin.apiBaseUrl…` en Gradle.

## 6. Ejecutar con Run ▶

1. Backend iniciado (paso 4).
2. Emulador seleccionado en la barra de dispositivos.
3. Pulsar **Run ▶**.
4. Android Studio compila, instala y abre la app. Iniciar sesión con las cuentas demo.

No hacen falta `-P…`, comandos de instalación ni editar archivos.

## 7. Dispositivo físico (opcional)

El teléfono debe alcanzar el equipo donde corre el backend. Dos variantes:

**A. Con cable (recomendada, sin tocar red):**
1. Conectar el teléfono por USB con depuración activada.
2. `adb reverse tcp:8000 tcp:8000` (el teléfono podrá usar `127.0.0.1:8000`).
3. Añadir en `Movil/local.properties` (archivo local, no versionado):
   `proyectwin.apiBaseUrl.debug=http://127.0.0.1:8000/v1/`

**B. Por Wi-Fi (IP LAN):**
1. Obtener la IP del PC (`ipconfig`) y añadirla en `local.properties`:
   `proyectwin.apiBaseUrl.debug=http://192.168.X.X:8000/v1/`
2. Añadir esa misma IP como `<domain>` en `app/src/debug/res/xml/network_security_config.xml` (solo debug).

## 8. Desarrollo vs producción

| | Debug (desarrollo) | Release (distribución) |
|---|---|---|
| URL | `10.0.2.2:8000` o la definida en `local.properties`/`-P` | `proyectwin.apiBaseUrl.release` (HTTPS real) |
| HTTP sin cifrar | Solo dominios locales (network_security_config de debug) | Prohibido (solo HTTPS) |
| Logging HTTP | Básico (método/URL/estado; cabeceras sensibles redactadas) | Desactivado |

## 9. Errores comunes

| Síntoma | Causa | Solución |
|---|---|---|
| “El servidor devolvió una respuesta que no se pudo interpretar” | Backend apagado o la dirección no está sirviendo la API | Comprobar que `http://127.0.0.1:8000/v1/config-similitud` responde e iniciar/reiniciar el backend habitual |
| Pantalla de error con botón *Reintentar* | Backend caído durante el uso | Levantar el backend y pulsar *Reintentar* (la sesión se conserva) |
| SDK del proyecto en rojo en Android Studio | JDK del proyecto no encontrado | Project Structure → SDK → elegir `jbr-17` (embebido) |
| `Error while resolving … FirSimpleFunctionImpl` en el analizador | SDK de proyecto inválido (histórico: `temurin-17` inexistente) | Ya corregido en `.idea/misc.xml`; si reaparece, revisar el SDK del paso anterior y *File → Invalidate Caches → Restart* |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` / “not enough space” al instalar (desde AS o adb) | El AVD tiene el almacenamiento casi lleno | Liberar espacio en el emulador: `adb shell pm trim-caches 8G`, borrar `adb shell rm -rf /data/local/tmp/*`, desinstalar apps de prueba; o crear un AVD con más disco (Wipe Data no amplía) |

## 10. APK debug: cómo generarlo y localizarlo

Desde `MVP\Movil`:

```powershell
.\gradlew.bat :app:assembleDebug
```

- Ruta exacta del APK: `app/build/outputs/apk/debug/app-debug.apk` (absoluta: `MVP\Movil\app\build\outputs\apk\debug\app-debug.apk`).
- Instalarlo en el emulador o dispositivo conectado: `adb install -r <ruta>` (con `-r` reinstala conservando datos; paquete `com.example.proyectwin`).

> Es un **build de desarrollo (debug)**: apunta a `10.0.2.2:8000`, permite HTTP sin cifrar solo para dominios locales y activa logging básico (sección 5). No es un artefacto de distribución ni está firmado para release.

## 11. Verificación manual M4 (borrado seguro en la app)

Objetivo: comprobar en el emulador la confirmación tipada `ELIMINAR` en los dos diálogos de borrado (ficha admin y usuario admin). Con backend y app en marcha (secciones 4–6), iniciar sesión como admin y marcar cada paso:

- [ ] **1. Diálogo abierto y botón deshabilitado.** Abrir eliminar en una ficha (pantalla admin de fichas) y en un usuario (pantalla admin de usuarios). Resultado esperado: aparece el diálogo con el campo “Escribe ELIMINAR para confirmar”; con el campo vacío, el botón **Eliminar** está deshabilitado.
- [ ] **2. Texto distinto sigue bloqueado.** Escribir algo distinto, p. ej. `eliminar` (minúsculas) o `ELIMINARX`. Resultado esperado: el botón sigue deshabilitado (comparación exacta en mayúsculas; los espacios al inicio/fin se ignoran).
- [ ] **3. `ELIMINAR` habilita y borra.** Escribir exactamente `ELIMINAR`. Resultado esperado: el botón se habilita; al pulsarlo se cierra el diálogo y se ejecuta el borrado (el usuario desaparece de la lista; la ficha se elimina o queda anulada si tiene aprendices/propuestas).
- [ ] **4. Cancelar no borra.** Con el campo relleno, pulsar **Cancelar** (o tocar fuera del diálogo). Resultado esperado: no se borra nada; al reabrir el diálogo el campo vuelve vacío y el botón está de nuevo deshabilitado.

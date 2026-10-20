# preparar-fisico.ps1
# Prepara un dispositivo Android físico (por USB) para usar la app ProyecTwin
# contra el backend local del PC en el puerto 8000.
#
# Pasos que ejecuta:
#   1. Localiza adb (SDK del usuario o PATH).
#   2. Comprueba que hay un dispositivo conectado y autorizado.
#   3. Crea el túnel inverso: adb reverse tcp:8000 tcp:8000
#      (el teléfono podrá usar http://127.0.0.1:8000).
#   4. Comprueba con una petición de SOLO LECTURA que el backend responde en
#      http://127.0.0.1:8000/v1/config-similitud.
#   5. Recuerda la línea de local.properties si falta (solo la lee, no escribe).
#
# Este script NO escribe archivos ni reinicia servicios.
# Códigos de salida: 0 = listo (puede haber avisos); 1 = requisito sin cumplir.
#
# Uso:  powershell -ExecutionPolicy Bypass -File preparar-fisico.ps1

$ErrorActionPreference = "Stop"

$puerto = 8000
$urlCheck = "http://127.0.0.1:$puerto/v1/config-similitud"

# --- 1. Localizar adb --------------------------------------------------------
$adb = $null
$adbSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (Test-Path $adbSdk) {
    $adb = $adbSdk
} else {
    $comando = Get-Command adb -ErrorAction SilentlyContinue
    if ($comando) { $adb = $comando.Source }
}
if (-not $adb) {
    Write-Host "ERROR: no se encontró adb." -ForegroundColor Red
    Write-Host "  - Ruta esperada: $adbSdk"
    Write-Host "  - Alternativa: instalar platform-tools y añadirlo al PATH."
    exit 1
}
Write-Host "adb: $adb"

# --- 2. ¿Hay dispositivo conectado y autorizado? -----------------------------
$salida = @(& $adb devices)
$lineas = @($salida | Select-Object -Skip 1 | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne "" })
$dispositivos = @($lineas | Where-Object { $_ -match "\tdevice$" })
if ($dispositivos.Count -eq 0) {
    Write-Host "ERROR: no hay ningún dispositivo conectado y autorizado." -ForegroundColor Red
    Write-Host "  1. Conecta el teléfono por USB."
    Write-Host "  2. Activa Opciones de desarrollador -> Depuración USB y acepta el aviso en pantalla."
    Write-Host "  3. Comprueba con: adb devices"
    if ($lineas | Where-Object { $_ -match "\tunauthorized$" }) {
        Write-Host "  Aviso: hay un dispositivo 'unauthorized': acepta el diálogo de depuración USB en el teléfono." -ForegroundColor Yellow
    }
    exit 1
}
Write-Host "Dispositivo(s) detectado(s): $($dispositivos.Count)"

# --- 3. Túnel inverso --------------------------------------------------------
& $adb reverse "tcp:$puerto" "tcp:$puerto"
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: 'adb reverse' falló (código $LASTEXITCODE)." -ForegroundColor Red
    exit 1
}
Write-Host "OK: adb reverse tcp:$puerto tcp:$puerto activo."
Write-Host "    (Se pierde al desconectar el cable o reiniciar el teléfono: re-ejecuta este script.)"

# --- 4. Comprobación del backend (petición de solo lectura) ------------------
try {
    $respuesta = Invoke-WebRequest -Uri $urlCheck -Method Get -UseBasicParsing -TimeoutSec 5
    Write-Host "OK: el backend responde en $urlCheck (HTTP $($respuesta.StatusCode))."
} catch {
    Write-Host "AVISO: el backend no respondió en $urlCheck." -ForegroundColor Yellow
    Write-Host "  Arranca el backend en el PC:  cd MVP\backend  ->  php artisan serve"
}

# --- 5. Recordatorio de local.properties (solo lectura) ----------------------
$rutaLocal = Join-Path (Split-Path -Parent $PSScriptRoot) "local.properties"
$linea = "proyectwin.apiBaseUrl.debug=http://127.0.0.1:$puerto/v1/"
$tieneLinea = $false
if (Test-Path $rutaLocal) {
    $contenido = Get-Content $rutaLocal -Raw
    if ($contenido -match [regex]::Escape($linea)) { $tieneLinea = $true }
}
if ($tieneLinea) {
    Write-Host "OK: local.properties ya define la URL para USB ($linea)."
} else {
    Write-Host "AVISO: añade en Movil\local.properties esta línea (archivo local, no versionado):" -ForegroundColor Yellow
    Write-Host "  $linea"
    Write-Host "  Plantilla con todos los escenarios: Movil\local.properties.example"
}

Write-Host ""
Write-Host "Listo. Abre la app desde Android Studio con Run (build debug)."

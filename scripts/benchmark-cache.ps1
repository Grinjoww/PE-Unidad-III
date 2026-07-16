<#
Benchmark reproducible del listado paginado de mascotas: 10 mediciones sin
cache (cache miss controlado, limpiando Redis antes de cada peticion) y 10
mediciones con cache (cache hit real, sin tocar Redis entre peticiones).

Requiere el stack levantado y saludable:
  docker compose up --build -d postgres redis backend

Uso:
  .\scripts\benchmark-cache.ps1
  .\scripts\benchmark-cache.ps1 -BaseUrl "http://localhost:8080" -Repeticiones 10 -Calentamientos 5

No incluye el arranque del backend ni el login dentro de las mediciones.
Cada peticion medida se cronometra con Stopwatch envolviendo unicamente la
llamada HTTP (sin impresion, login ni limpieza de Redis dentro del cronometro).
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Email = "admin@biopet.ec",
    [string]$Password = "Admin123*",
    [string]$RedisContainer = "biopet-redis",
    [string]$PostgresContainer = "biopet-postgres",
    [string]$BackendContainer = "biopet-backend",
    [string]$ClavePatron = "mascotas:listado:*",
    [string]$RutaConsulta = "/api/mascotas?page=0&size=50&sort=nombre,asc",
    [int]$Repeticiones = 10,
    [int]$Calentamientos = 5
)

$raiz = Split-Path -Parent $PSScriptRoot
$carpetaResultados = Join-Path $raiz "scripts\resultados"
New-Item -ItemType Directory -Force -Path $carpetaResultados | Out-Null
$rutaCsv = Join-Path $carpetaResultados "benchmark-cache.csv"
$rutaResumen = Join-Path $carpetaResultados "benchmark-resumen.json"

$uriCompleta = "$BaseUrl$RutaConsulta"

function Get-ClavesRedis {
    $salida = docker exec $RedisContainer redis-cli --scan --pattern $ClavePatron 2>&1
    return @($salida | Where-Object { $_ -and $_.Trim() -ne "" })
}

function Clear-CacheKeys {
    # @(...) fuerza semantica de array: Get-ClavesRedis puede devolver un
    # string simple (no un array de 1 elemento) cuando solo hay una clave,
    # por como PowerShell "desenrolla" el resultado de una funcion.
    $claves = @(Get-ClavesRedis)
    foreach ($clave in $claves) {
        docker exec $RedisContainer redis-cli DEL "$clave" | Out-Null
    }
    return $claves.Count
}

function Invoke-TimedGet {
    param($Uri, $Session)
    $cronometro = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $respuesta = Invoke-WebRequest -Uri $Uri -Method Get -WebSession $Session -UseBasicParsing
        $cronometro.Stop()
        return [pscustomobject]@{ TiempoMs = $cronometro.Elapsed.TotalMilliseconds; StatusCode = [int]$respuesta.StatusCode }
    } catch {
        $cronometro.Stop()
        $status = $null
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ TiempoMs = $cronometro.Elapsed.TotalMilliseconds; StatusCode = $status }
    }
}

function Format-TiempoMs {
    param([double]$Valor)
    # Fuerza punto decimal (InvariantCulture) sin importar la configuracion
    # regional del sistema: el CSV usa coma como delimitador de columnas,
    # y un numero con coma decimal ahi seria ambiguo/incorrecto al releerlo.
    return [math]::Round($Valor, 3).ToString("F3", [System.Globalization.CultureInfo]::InvariantCulture)
}

function Get-Percentil95 {
    param([double[]]$Valores)
    $ordenados = $Valores | Sort-Object
    $n = $ordenados.Count
    if ($n -eq 1) { return [math]::Round($ordenados[0], 3) }
    # Percentile.Inclusive (interpolacion lineal), equivalente a Excel
    # PERCENTILE.INC / numpy 'linear': rank = p * (n-1), interpolar entre
    # los dos valores ordenados mas cercanos.
    $rank = 0.95 * ($n - 1)
    $inferior = [int][math]::Floor($rank)
    $superior = [int][math]::Ceiling($rank)
    if ($inferior -eq $superior) {
        return [math]::Round($ordenados[$inferior], 3)
    }
    $fraccion = $rank - $inferior
    $valor = $ordenados[$inferior] + $fraccion * ($ordenados[$superior] - $ordenados[$inferior])
    return [math]::Round($valor, 3)
}

function Test-EntornoListo {
    Write-Host "0) Verificando Docker y servicios..."
    $version = docker version --format "{{.Server.Version}}" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "   ERROR: Docker no responde: $version"
        return $false
    }
    Write-Host "   Docker OK (server $version)"

    foreach ($nombre in @($PostgresContainer, $RedisContainer, $BackendContainer)) {
        $estado = docker inspect --format "{{.State.Health.Status}}" $nombre 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "   ERROR: contenedor '$nombre' no encontrado. Ejecuta: docker compose up --build -d postgres redis backend"
            return $false
        }
        if ($estado -ne "healthy") {
            Write-Host "   ERROR: contenedor '$nombre' no esta healthy (estado actual: $estado)."
            return $false
        }
        Write-Host "   $nombre -> healthy"
    }

    $ping = docker exec $RedisContainer redis-cli ping 2>&1
    if ($ping -ne "PONG") {
        Write-Host "   ERROR: Redis no respondio PONG (respuesta: $ping)"
        return $false
    }
    Write-Host "   Redis PING -> PONG"
    return $true
}

if (-not (Test-EntornoListo)) {
    exit 1
}

Write-Host ""
Write-Host "1) Login ($Email), una sola vez, cookie conservada en WebRequestSession..."
$loginBody = @{ email = $Email; password = $Password } | ConvertTo-Json
try {
    $loginResponse = Invoke-WebRequest -Uri "$BaseUrl/api/auth/login" -Method Post `
        -Body $loginBody -ContentType "application/json" -SessionVariable session -UseBasicParsing
    Write-Host "   Login -> HTTP $([int]$loginResponse.StatusCode)"
} catch {
    Write-Host "   ERROR: login fallo. $_"
    exit 1
}

Write-Host ""
Write-Host "2) Calentamiento ($Calentamientos peticiones, no forman parte de las mediciones)..."
for ($i = 1; $i -le $Calentamientos; $i++) {
    Clear-CacheKeys | Out-Null
    $resultado = Invoke-TimedGet -Uri $uriCompleta -Session $session
    Write-Host ("   Calentamiento {0}/{1} -> HTTP {2} en {3:N1} ms" -f $i, $Calentamientos, $resultado.StatusCode, $resultado.TiempoMs)
    if ($resultado.StatusCode -ne 200) {
        Write-Host "   ERROR: el calentamiento no devolvio HTTP 200. Abortando."
        exit 1
    }
}
Clear-CacheKeys | Out-Null

$mediciones = New-Object System.Collections.Generic.List[object]

Write-Host ""
Write-Host "3) ESCENARIO A - sin cache ($Repeticiones cache misses controlados: se limpia Redis antes de cada peticion)..."
for ($i = 1; $i -le $Repeticiones; $i++) {
    Clear-CacheKeys | Out-Null
    $resultado = Invoke-TimedGet -Uri $uriCompleta -Session $session
    $fechaHora = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss.fffK")
    Write-Host ("   [sin_cache {0}/{1}] HTTP {2} en {3:N1} ms" -f $i, $Repeticiones, $resultado.StatusCode, $resultado.TiempoMs)
    if ($resultado.StatusCode -ne 200) {
        Write-Host "   ERROR: la medicion sin_cache #$i no devolvio HTTP 200 (obtuvo $($resultado.StatusCode)). Abortando sin guardar resultados."
        exit 1
    }
    $mediciones.Add([pscustomobject]@{
        escenario   = "sin_cache"
        repeticion  = $i
        tiempo_ms   = Format-TiempoMs -Valor $resultado.TiempoMs
        http_status = $resultado.StatusCode
        fecha_hora  = $fechaHora
    })
}

Write-Host ""
Write-Host "4) ESCENARIO B - con cache..."
Clear-CacheKeys | Out-Null
Write-Host "   Peticion de cebado (no cuenta en las mediciones)..."
$cebado = Invoke-TimedGet -Uri $uriCompleta -Session $session
Write-Host ("   Cebado -> HTTP {0} en {1:N1} ms" -f $cebado.StatusCode, $cebado.TiempoMs)
if ($cebado.StatusCode -ne 200) {
    Write-Host "   ERROR: la peticion de cebado no devolvio HTTP 200. Abortando."
    exit 1
}

$clavesTrasCebado = @(Get-ClavesRedis)
if ($clavesTrasCebado.Count -eq 0) {
    Write-Host "   ERROR: no se creo ninguna clave con el patron '$ClavePatron' tras el cebado. Abortando."
    exit 1
}
$claveObservada = $clavesTrasCebado[0]
$ttlObservado = docker exec $RedisContainer redis-cli TTL "$claveObservada" 2>&1
Write-Host "   Clave confirmada en Redis: $claveObservada (TTL=$ttlObservado s)"
if ([int]$ttlObservado -le 0) {
    Write-Host "   ERROR: el TTL de la clave no es positivo ($ttlObservado). Abortando."
    exit 1
}

Write-Host "   Ejecutando $Repeticiones peticiones identicas (deben ser cache hits; no se toca Redis entre ellas)..."
for ($i = 1; $i -le $Repeticiones; $i++) {
    $resultado = Invoke-TimedGet -Uri $uriCompleta -Session $session
    $fechaHora = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss.fffK")
    Write-Host ("   [con_cache {0}/{1}] HTTP {2} en {3:N1} ms" -f $i, $Repeticiones, $resultado.StatusCode, $resultado.TiempoMs)
    if ($resultado.StatusCode -ne 200) {
        Write-Host "   ERROR: la medicion con_cache #$i no devolvio HTTP 200 (obtuvo $($resultado.StatusCode)). Abortando sin guardar resultados."
        exit 1
    }
    $mediciones.Add([pscustomobject]@{
        escenario   = "con_cache"
        repeticion  = $i
        tiempo_ms   = Format-TiempoMs -Valor $resultado.TiempoMs
        http_status = $resultado.StatusCode
        fecha_hora  = $fechaHora
    })
}

$clavesFinales = @(Get-ClavesRedis)
Write-Host ""
Write-Host "5) Claves de Redis conservadas durante el escenario con_cache: $($clavesFinales.Count) (esperado >= 1)"

$mediciones | Export-Csv -Path $rutaCsv -NoTypeInformation -Encoding utf8
Write-Host ""
Write-Host "CSV guardado en: $rutaCsv ($($mediciones.Count) filas)"

$tiemposSinCache = $mediciones | Where-Object { $_.escenario -eq "sin_cache" } | ForEach-Object { [double]$_.tiempo_ms }
$tiemposConCache = $mediciones | Where-Object { $_.escenario -eq "con_cache" } | ForEach-Object { [double]$_.tiempo_ms }

$promedioSinCache = [math]::Round((($tiemposSinCache | Measure-Object -Average).Average), 3)
$promedioConCache = [math]::Round((($tiemposConCache | Measure-Object -Average).Average), 3)
$p95SinCache = Get-Percentil95 -Valores $tiemposSinCache
$p95ConCache = Get-Percentil95 -Valores $tiemposConCache
$speedup = if ($promedioConCache -gt 0) { [math]::Round($promedioSinCache / $promedioConCache, 3) } else { $null }

$resumen = [ordered]@{
    fecha                     = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss.fffK")
    endpoint                  = $RutaConsulta
    parametros                = [ordered]@{ page = 0; size = 50; sort = "nombre,asc" }
    entorno = [ordered]@{
        base_url            = $BaseUrl
        contenedor_postgres = $PostgresContainer
        contenedor_redis    = $RedisContainer
        contenedor_backend  = $BackendContainer
        cache_nombre        = "mascotas-listado"
        clave_observada     = $claveObservada
        ttl_observado_seg   = [int]$ttlObservado
    }
    metodologia = [ordered]@{
        calentamientos     = $Calentamientos
        repeticiones       = $Repeticiones
        sin_cache          = "Se limpian (SCAN + DEL) todas las claves '$ClavePatron' en Redis inmediatamente antes de cada una de las $Repeticiones peticiones, garantizando un cache miss controlado que ejecuta la consulta real contra PostgreSQL en cada repeticion."
        con_cache          = "Se limpia el cache, se ejecuta una peticion de cebado (no contada) para crear la entrada, se confirma su existencia y TTL positivo con redis-cli, y luego se ejecutan las $Repeticiones peticiones sin tocar Redis entre ellas, garantizando cache hits reales."
        metodo_p95         = "percentile.inclusive (interpolacion lineal): rank = 0.95 * (n-1) sobre los valores ordenados, interpolando entre los dos mas cercanos. Mismo metodo en ambos escenarios."
        medicion            = "System.Diagnostics.Stopwatch envolviendo unicamente la llamada Invoke-WebRequest; login, limpieza de Redis e impresion en consola quedan fuera del cronometro."
    }
    resultados = [ordered]@{
        mediciones_totales = $mediciones.Count
        sin_cache = [ordered]@{
            n         = $tiemposSinCache.Count
            promedio_ms = $promedioSinCache
            p95_ms      = $p95SinCache
        }
        con_cache = [ordered]@{
            n         = $tiemposConCache.Count
            promedio_ms = $promedioConCache
            p95_ms      = $p95ConCache
        }
        speedup = $speedup
    }
}

$resumen | ConvertTo-Json -Depth 6 | Out-File -FilePath $rutaResumen -Encoding utf8
Write-Host "Resumen guardado en: $rutaResumen"

Write-Host ""
Write-Host "=================== RESULTADOS ==================="
Write-Host ("Sin cache  -> promedio {0} ms | P95 {1} ms | n={2}" -f $promedioSinCache, $p95SinCache, $tiemposSinCache.Count)
Write-Host ("Con cache  -> promedio {0} ms | P95 {1} ms | n={2}" -f $promedioConCache, $p95ConCache, $tiemposConCache.Count)
Write-Host ("Speedup S = promedio_sin_cache / promedio_con_cache = {0}x" -f $speedup)
Write-Host "===================================================="

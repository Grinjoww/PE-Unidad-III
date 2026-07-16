<#
Evidencia tecnica reproducible del cache-aside de Redis para el listado de
mascotas. Requiere el stack levantado con Redis en un contenedor Docker:

  docker compose up --build -d postgres redis backend

Uso:
  .\scripts\verificar-cache.ps1
  .\scripts\verificar-cache.ps1 -BaseUrl "http://localhost:8080" -RedisContainer "biopet-redis"

No realiza las 10 mediciones formales del benchmark; solo confirma cache
hit/miss y la invalidacion tras escribir.
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Email = "admin@biopet.ec",
    [string]$Password = "Admin123*",
    [string]$CookieName = "BIOPET_ACCESS_TOKEN",
    [string]$RedisContainer = "biopet-redis",
    [string]$ClavePatron = "mascotas:listado:*"
)

$ErrorActionPreference = "Stop"

function Get-StatusCode($ex) {
    if ($ex.Exception.Response) {
        return [int]$ex.Exception.Response.StatusCode
    }
    return $null
}

function Get-ClavesRedis {
    $salida = docker exec $RedisContainer redis-cli --scan --pattern $ClavePatron 2>&1
    return $salida | Where-Object { $_ -and $_.Trim() -ne "" }
}

Write-Host "1) Limpiando claves de cache existentes ($ClavePatron) en '$RedisContainer'..."
$clavesPrevias = Get-ClavesRedis
foreach ($clave in $clavesPrevias) {
    docker exec $RedisContainer redis-cli DEL "$clave" | Out-Null
}
Write-Host "   Claves eliminadas antes de empezar: $($clavesPrevias.Count)"

Write-Host "2) Login ($Email)..."
$loginBody = @{ email = $Email; password = $Password } | ConvertTo-Json
try {
    $loginResponse = Invoke-WebRequest -Uri "$BaseUrl/api/auth/login" -Method Post `
        -Body $loginBody -ContentType "application/json" -SessionVariable session
    Write-Host "   Login -> HTTP $([int]$loginResponse.StatusCode)"
} catch {
    Write-Host "   Login -> HTTP $(Get-StatusCode $_) (fallo inesperado)"
    exit 1
}

Write-Host "3) Primera consulta a /api/mascotas (deberia consultar PostgreSQL)..."
$cronometro1 = [System.Diagnostics.Stopwatch]::StartNew()
$primera = Invoke-WebRequest -Uri "$BaseUrl/api/mascotas?page=0&size=10&sort=id,asc" -Method Get -WebSession $session
$cronometro1.Stop()
Write-Host "   HTTP $([int]$primera.StatusCode) en $($cronometro1.ElapsedMilliseconds) ms"

Write-Host "4) Segunda consulta identica a /api/mascotas (deberia venir de Redis)..."
$cronometro2 = [System.Diagnostics.Stopwatch]::StartNew()
$segunda = Invoke-WebRequest -Uri "$BaseUrl/api/mascotas?page=0&size=10&sort=id,asc" -Method Get -WebSession $session
$cronometro2.Stop()
Write-Host "   HTTP $([int]$segunda.StatusCode) en $($cronometro2.ElapsedMilliseconds) ms"

Write-Host "5) Claves creadas en Redis tras las consultas:"
$clavesCreadas = Get-ClavesRedis
if ($clavesCreadas.Count -eq 0) {
    Write-Host "   ERROR: no se creo ninguna clave con el patron '$ClavePatron'"
} else {
    foreach ($clave in $clavesCreadas) {
        $ttl = docker exec $RedisContainer redis-cli TTL "$clave" 2>&1
        Write-Host "   $clave (TTL=$ttl s)"
    }
}

Write-Host "6) Creando una mascota para invalidar el cache..."
$duenioId = 1
$crearBody = @{
    duenioId        = $duenioId
    nombre          = "Cache QA"
    especie         = "Perro"
    raza            = "Poodle"
    fechaNacimiento = "2022-01-01"
} | ConvertTo-Json
try {
    $crear = Invoke-WebRequest -Uri "$BaseUrl/api/mascotas" -Method Post `
        -Body $crearBody -ContentType "application/json" -WebSession $session
    Write-Host "   Crear mascota -> HTTP $([int]$crear.StatusCode)"
} catch {
    Write-Host "   Crear mascota -> HTTP $(Get-StatusCode $_) (revisa que duenioId=$duenioId exista)"
}

Write-Host "7) Verificando que las claves fueron eliminadas por la invalidacion..."
$clavesTrasEscritura = Get-ClavesRedis
if ($clavesTrasEscritura.Count -eq 0) {
    Write-Host "   OK: no quedan claves con el patron '$ClavePatron'"
} else {
    Write-Host "   ERROR: aun quedan $($clavesTrasEscritura.Count) claves: $($clavesTrasEscritura -join ', ')"
}

Write-Host ""
Write-Host "Resumen:"
Write-Host "  Primera consulta (miss esperado) .. $($cronometro1.ElapsedMilliseconds) ms"
Write-Host "  Segunda consulta (hit esperado) ... $($cronometro2.ElapsedMilliseconds) ms"
Write-Host "  Claves creadas ..................... $($clavesCreadas.Count)"
Write-Host "  Claves tras crear mascota .......... $($clavesTrasEscritura.Count) (esperado 0)"

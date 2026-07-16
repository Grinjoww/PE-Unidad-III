<#
Evidencia tecnica reproducible del flujo de logout con cookie HttpOnly.
Requiere el backend levantado (docker compose up -d postgres redis backend,
o .\mvnw.cmd spring-boot:run) accesible en -BaseUrl.

Uso:
  .\scripts\verificar-logout.ps1
  .\scripts\verificar-logout.ps1 -BaseUrl "http://localhost:8080" -Email "admin@biopet.ec" -Password "Admin123*"
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Email = "admin@biopet.ec",
    [string]$Password = "Admin123*",
    [string]$CookieName = "BIOPET_ACCESS_TOKEN"
)

$ErrorActionPreference = "Stop"

function Get-StatusCode($ex) {
    if ($ex.Exception.Response) {
        return [int]$ex.Exception.Response.StatusCode
    }
    return $null
}

Write-Host "1) Login ($Email)..."
$loginBody = @{ email = $Email; password = $Password } | ConvertTo-Json
$loginStatus = $null
try {
    $loginResponse = Invoke-WebRequest -Uri "$BaseUrl/api/auth/login" -Method Post `
        -Body $loginBody -ContentType "application/json" -SessionVariable session
    $loginStatus = [int]$loginResponse.StatusCode
    Write-Host "   Login -> HTTP $loginStatus"
} catch {
    $loginStatus = Get-StatusCode $_
    Write-Host "   Login -> HTTP $loginStatus (fallo inesperado)"
    exit 1
}

$sessionCookie = $session.Cookies.GetCookies("$BaseUrl") | Where-Object { $_.Name -eq $CookieName }
if (-not $sessionCookie) {
    Write-Host "   ERROR: no se recibio la cookie '$CookieName'"
    exit 1
}
$oldTokenValue = $sessionCookie.Value
Write-Host "   Cookie '$CookieName' recibida (HttpOnly=$($sessionCookie.HttpOnly))"

Write-Host "2) Peticion a /api/auth/me con cookie valida..."
$meStatus = $null
try {
    $meResponse = Invoke-WebRequest -Uri "$BaseUrl/api/auth/me" -Method Get -WebSession $session
    $meStatus = [int]$meResponse.StatusCode
} catch {
    $meStatus = Get-StatusCode $_
}
Write-Host "   /api/auth/me -> HTTP $meStatus"

Write-Host "3) Logout..."
$logoutStatus = $null
try {
    $logoutResponse = Invoke-WebRequest -Uri "$BaseUrl/api/auth/logout" -Method Post -WebSession $session
    $logoutStatus = [int]$logoutResponse.StatusCode
} catch {
    $logoutStatus = Get-StatusCode $_
}
Write-Host "   Logout -> HTTP $logoutStatus"

Write-Host "4) Reintentando /api/auth/me con la cookie ANTERIOR al logout..."
$oldCookie = New-Object System.Net.Cookie($CookieName, $oldTokenValue, "/", ([uri]$BaseUrl).Host)
$replaySession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$replaySession.Cookies.Add($oldCookie)
$replayStatus = $null
try {
    $retryResponse = Invoke-WebRequest -Uri "$BaseUrl/api/auth/me" -Method Get -WebSession $replaySession
    $replayStatus = [int]$retryResponse.StatusCode
} catch {
    $replayStatus = Get-StatusCode $_
}
Write-Host "   /api/auth/me con token anterior -> HTTP $replayStatus"

Write-Host ""
Write-Host "Resumen (codigos HTTP esperados entre parentesis):"
Write-Host "  Login .......................... $loginStatus (200)"
Write-Host "  Protegido con cookie valida .... $meStatus (200)"
Write-Host "  Logout .......................... $logoutStatus (200 o 204)"
Write-Host "  Protegido con cookie anterior ... $replayStatus (401)"

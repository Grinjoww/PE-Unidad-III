<#
Genera el reporte de cobertura JaCoCo del backend (mvnw clean verify) y
muestra el porcentaje real de lineas/ramas cubiertas, leido del CSV que
JaCoCo genera (no un valor escrito a mano).

Uso:
  .\scripts\generar-cobertura.ps1
#>

$raiz = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $raiz "backend"

Set-Location $backend
& .\mvnw.cmd clean verify
$mavenExitCode = $LASTEXITCODE

$reporteHtml = Join-Path $backend "target\site\jacoco\index.html"
$reporteXml = Join-Path $backend "target\site\jacoco\jacoco.xml"
$reporteCsv = Join-Path $backend "target\site\jacoco\jacoco.csv"

if ($mavenExitCode -ne 0) {
    Write-Host ""
    Write-Host "Maven fallo (exit code $mavenExitCode). Revisa la salida anterior (prueba fallida o cobertura por debajo del minimo)."
    if (Test-Path $reporteHtml) {
        Write-Host "El reporte generado hasta el punto de falla sigue disponible en: $reporteHtml"
    }
    exit $mavenExitCode
}

if (-not (Test-Path $reporteHtml)) {
    Write-Host "ERROR: Maven termino en exito pero no se genero $reporteHtml"
    exit 1
}

if (-not (Test-Path $reporteCsv)) {
    Write-Host "ERROR: Maven termino en exito pero no se genero $reporteCsv"
    exit 1
}

$filas = Import-Csv $reporteCsv
$lineasCubiertas = ($filas | Measure-Object -Property LINE_COVERED -Sum).Sum
$lineasFaltantes = ($filas | Measure-Object -Property LINE_MISSED -Sum).Sum
$totalLineas = $lineasCubiertas + $lineasFaltantes
$porcentajeLineas = if ($totalLineas -gt 0) { [math]::Round(($lineasCubiertas / $totalLineas) * 100, 2) } else { 0 }

$ramasCubiertas = ($filas | Measure-Object -Property BRANCH_COVERED -Sum).Sum
$ramasFaltantes = ($filas | Measure-Object -Property BRANCH_MISSED -Sum).Sum
$totalRamas = $ramasCubiertas + $ramasFaltantes
$porcentajeRamas = if ($totalRamas -gt 0) { [math]::Round(($ramasCubiertas / $totalRamas) * 100, 2) } else { 0 }

Write-Host ""
Write-Host "Cobertura real obtenida (backend):"
Write-Host "  Lineas: $lineasCubiertas / $totalLineas ($porcentajeLineas%)"
Write-Host "  Ramas:  $ramasCubiertas / $totalRamas ($porcentajeRamas%)"
Write-Host ""
Write-Host "Reportes generados:"
Write-Host "  HTML: $reporteHtml"
Write-Host "  XML:  $reporteXml"
Write-Host "  CSV:  $reporteCsv"

exit 0

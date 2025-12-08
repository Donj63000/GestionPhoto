$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $projectRoot

[xml]$pom = Get-Content (Join-Path $projectRoot "pom.xml")
$javafxVersion = $pom.project.properties.'javafx.version'
$slf4jVersion = $pom.project.properties.'slf4j.version'
$logbackVersion = $pom.project.properties.'logback.version'

$m2 = Join-Path $env:USERPROFILE ".m2\repository"
$platform = "win"
$javafxModules = @("javafx-base", "javafx-graphics", "javafx-controls", "javafx-fxml")

$modulePathJars = @()
$classPathJars = @("target\classes")
foreach ($module in $javafxModules) {
  $modulePathJars += Join-Path $m2 "org\openjfx\$module\$javafxVersion\$module-$javafxVersion-$platform.jar"
  $classPathJars += Join-Path $m2 "org\openjfx\$module\$javafxVersion\$module-$javafxVersion.jar"
}

$classPathJars += @(
  Join-Path $m2 "org\slf4j\slf4j-api\$slf4jVersion\slf4j-api-$slf4jVersion.jar",
  Join-Path $m2 "ch\qos\logback\logback-classic\$logbackVersion\logback-classic-$logbackVersion.jar",
  Join-Path $m2 "ch\qos\logback\logback-core\$logbackVersion\logback-core-$logbackVersion.jar"
)

if (-not (Test-Path "target\classes")) {
  Write-Host "Le dossier target/classes est absent. Lance d'abord 'mvn -Pwindows -DskipTests compile' ou construis depuis l'IDE." -ForegroundColor Yellow
}

$missing = @($modulePathJars + $classPathJars | Where-Object { -not (Test-Path $_) })
if ($missing.Count -gt 0) {
  Write-Host "Dependances manquantes :" -ForegroundColor Yellow
  $missing | ForEach-Object { Write-Host " - $_" }
  Write-Host "`nExecute 'mvn -Pwindows dependency:resolve' pour telecharger les dependances runtime JavaFX, puis relance ce script." -ForegroundColor Yellow
  exit 1
}

$modulePath = ($modulePathJars -join ';')
$classPath = ($classPathJars -join ';')

$javaExe =
  if ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME "bin\java.exe"
  } else {
    "java.exe"
  }

$versionOutput = & $javaExe -version 2>&1
if ($LASTEXITCODE -ne 0) {
  Write-Error "Impossible de demarrer $javaExe -version"
}

if ($versionOutput -match '"(\d+)[\.\d_]*"') {
  $major = [int]$matches[1]
  if ($major -lt 21) {
    Write-Host "Version Java detectee: $major. JavaFX 21 requiert Java 21 ou plus recent." -ForegroundColor Red
    Write-Host "Mets a jour JAVA_HOME ou ton PATH pour pointer vers un JDK 21+, puis relance." -ForegroundColor Yellow
    exit 1
  }
}

$vmArgs = @(
  "--module-path",
  $modulePath,
  "--add-modules",
  "javafx.controls,javafx.fxml",
  "-cp",
  $classPath,
  "org.example.app.PhotoGestionApp"
) + $args

& $javaExe @vmArgs

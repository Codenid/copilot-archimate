param(
    [string]$ArchiHome = 'C:\Dev\Apps\Archi'
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$javaHome = Split-Path -Parent (Split-Path -Parent (Get-Command javac).Source)
$javac = Join-Path $javaHome 'bin\javac.exe'
$jar = Join-Path $javaHome 'bin\jar.exe'
$pluginDir = Join-Path $ArchiHome 'plugins'

if (-not (Test-Path -LiteralPath $pluginDir)) {
    throw "No se encontro Archi en: $ArchiHome"
}

$editorBundle = Get-ChildItem -LiteralPath $pluginDir -Directory |
    Where-Object { $_.Name -like 'com.archimatetool.editor_5.7.0.*' } |
    Select-Object -First 1
$modelBundle = Get-ChildItem -LiteralPath $pluginDir -Directory |
    Where-Object { $_.Name -like 'com.archimatetool.model_5.7.0.*' } |
    Select-Object -First 1
if (-not $editorBundle -or -not $modelBundle) {
    throw 'El proyecto requiere Archi 5.7.0.'
}

$editorJar = Join-Path $editorBundle.FullName 'com.archimatetool.editor.jar'
$modelJar = Join-Path $modelBundle.FullName 'com.archimatetool.model.jar'
$classes = Join-Path $projectRoot 'build\classes'
$bundleJar = Join-Path $projectRoot 'build\com.archimatetool.asistentediagramacion_1.0.0.jar'
$javaFiles = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'java') -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName }

if (Test-Path -LiteralPath $classes) {
    Remove-Item -LiteralPath $classes -Recurse -Force
}
New-Item -ItemType Directory -Path $classes -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $bundleJar) -Force | Out-Null

$classpath = "$editorJar;$modelJar;$pluginDir\*"
& $javac -encoding UTF-8 --release 21 -classpath $classpath -d $classes $javaFiles
if ($LASTEXITCODE -ne 0) {
    throw "La compilacion fallo (codigo $LASTEXITCODE)."
}

& $jar --create --file $bundleJar --manifest (Join-Path $projectRoot 'META-INF\MANIFEST.MF') `
    -C $classes . -C $projectRoot plugin.xml -C $projectRoot schema
if ($LASTEXITCODE -ne 0) {
    throw "No se pudo empaquetar la extension (codigo $LASTEXITCODE)."
}

Remove-Item -LiteralPath $classes -Recurse -Force

$dropins = Join-Path $env:APPDATA 'Archi\dropins'
New-Item -ItemType Directory -Path $dropins -Force | Out-Null
Copy-Item -LiteralPath $bundleJar -Destination $dropins -Force

Write-Host "Extension compilada e instalada en: $dropins"
Write-Host 'Reinicia Archi para cargarla; luego ejecuta iniciar.ajs desde jArchi.'

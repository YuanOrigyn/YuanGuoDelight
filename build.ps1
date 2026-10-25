# Yuan来是果 / YuanGuoDelight build script.
#
# Gradle cannot start on this machine (native-platform.dll), so the plugin is compiled
# with javac directly and packed with the jar tool that ships with the JDK.
# Sources target Java 21 bytecode (--release 21) so the plugin runs on any modern server.
#
# Usage: powershell -ExecutionPolicy Bypass -File build.ps1

$ErrorActionPreference = "Stop"

$D       = Split-Path -Parent $MyInvocation.MyCommand.Path
$JDK     = "C:\Program Files\Java\jdk-25.0.3"
$JAVAC   = "$JDK\bin\javac.exe"
$JAR     = "$JDK\bin\jar.exe"

$VERSION = "1.0.0"
# Output directory: $env:YGD_BUILD wins, otherwise build/ when it is writable,
# otherwise build-<timestamp>/ next to the project (e.g. when the sandbox blocks build/).
function Test-WritableDirectory([string]$Path) {
    try {
        [IO.Directory]::CreateDirectory($Path) | Out-Null
        $probe = Join-Path $Path (".write-probe-" + [Guid]::NewGuid().ToString("N"))
        [IO.File]::WriteAllText($probe, "x")
        [IO.File]::Delete($probe)
        return $true
    } catch {
        return $false
    }
}

if ($env:YGD_BUILD) {
    $BUILD = $env:YGD_BUILD
} elseif (Test-WritableDirectory "$D\build") {
    $BUILD = "$D\build"
} else {
    $BUILD = Join-Path $D "build-local"
}
Write-Host "Build directory: $BUILD"
$CLASSES = "$BUILD\classes"
$STAGE   = "$BUILD\staging"
$LOG     = "$BUILD\compile.log"

# Every dependency is resolved from libs/, so the build works completely offline.
# Paper 1.21.11 (the API the plugin targets) plus CraftEngine 26.8.2,
# PapersDelight and their compile-time transitive libraries.
$CP = @(
    "$D\libs\paper-api-1.21.11.jar",
    "$D\libs\craft-engine-paper-plugin-26.8.2.jar",
    "$D\libs\craft-engine-core-26.8.2.jar",
    "$D\libs\craft-engine-bukkit-26.8.2.jar",
    "$D\libs\craft-engine-bukkit-proxy-26.8.2.jar",
    "$D\libs\RealisticSeasons-11.12.0.jar",
    "$D\libs\PapersDelight-1.2.0.jar",
    "$D\libs\guava-33.4.0-jre.jar",
    "$D\libs\adventure-api-4.26.1.jar",
    "$D\libs\adventure-key-4.26.1.jar",
    "$D\libs\adventure-text-serializer-commons-4.26.1.jar",
    "$D\libs\adventure-text-serializer-legacy-4.26.1.jar",
    "$D\libs\examination-api-1.3.0.jar",
    "$D\libs\annotations-26.0.2.jar"
) -join ';'

foreach ($dependency in ($CP -split ';')) {
    if (-not (Test-Path -LiteralPath $dependency)) {
        throw "Missing dependency: $dependency"
    }
}

[IO.Directory]::CreateDirectory($BUILD) | Out-Null
if (Test-Path $CLASSES) { [IO.Directory]::Delete($CLASSES, $true) }
if (Test-Path $STAGE)   { [IO.Directory]::Delete($STAGE, $true) }
[IO.Directory]::CreateDirectory($CLASSES) | Out-Null
[IO.Directory]::CreateDirectory($STAGE) | Out-Null

$utf8 = New-Object Text.UTF8Encoding($false)

# 1) Compile
$sources = (Get-ChildItem "$D\src\main\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName }) -join "`r`n"
$sourcesFile = "$BUILD\sources.txt"
[IO.File]::WriteAllText($sourcesFile, $sources, $utf8)

# javac on Windows prints a harmless AccessDeniedException while closing the classpath jars,
# so the exit code is the only signal that is trusted here.
cmd /c "`"$JAVAC`" -encoding UTF-8 --release 21 -nowarn -cp `"$CP`" -d `"$CLASSES`" @`"$sourcesFile`" > `"$LOG`" 2>&1"
if ($LASTEXITCODE -ne 0) {
    Get-Content $LOG | ForEach-Object { Write-Host $_ }
    throw "Compilation failed"
}
Write-Host "Compiled: $((Get-ChildItem $CLASSES -Recurse -Filter *.class).Count) classes"

# 2) Assemble
Copy-Item "$CLASSES\*" -Destination $STAGE -Recurse -Force
$meta = [IO.File]::ReadAllText("$D\src\main\resources\paper-plugin.yml", [Text.Encoding]::UTF8).Replace('${version}', $VERSION)
[IO.File]::WriteAllText("$STAGE\paper-plugin.yml", $meta, $utf8)
Copy-Item "$D\src\main\resources\config.yml" "$STAGE\config.yml" -Force
# effects.yml is generated from the mod data, so it is copied in rather than hand written.
Copy-Item "$D\src\main\resources\effects.yml" "$STAGE\effects.yml" -Force

# 3) Bundle the content pack.
# The cooking pot / cutting board recipes belong to PapersDelight's CraftEngine config
# section, so they live in the same pack instead of a separate data directory.
& $JAR --create --no-manifest --file "$STAGE\pack.zip" -C "$D\pack" . | Out-Null

# 4) Pack the plugin jar
$out = "$D\YuanGuoDelight-$VERSION.jar"
if (Test-Path $out) { [IO.File]::Delete($out) }
& $JAR --create --file $out -C $STAGE . | Out-Null
Write-Host ("Packed: {0} ({1:N0} bytes)" -f $out, (Get-Item $out).Length)

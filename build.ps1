# Yuan来是果 / YuanGuoDelight build script.
#
# Gradle cannot start on every machine, so the plugin can also be compiled with
# javac directly and packed with the jar tool that ships with the JDK.
# Sources target Java 21 bytecode (--release 21) so the plugin runs on any modern server.
#
# Usage: powershell -ExecutionPolicy Bypass -File build.ps1
#
# Dependencies are read from libs/. When a jar is missing it is downloaded from its
# public Maven repository once and cached in libs/ for later offline builds.
# Set YGD_LIBS to use another cache directory.

$ErrorActionPreference = "Stop"

$D       = Split-Path -Parent $MyInvocation.MyCommand.Path
$VERSION = "1.0.0"

# ---------------------------------------------------------------------------
# Locate a JDK (21 or newer). JAVA_HOME wins, then common install locations,
# then javac on PATH.
# ---------------------------------------------------------------------------
function Get-JdkMajorVersion([string]$JdkPath) {
    $release = Join-Path $JdkPath "release"
    if (-not (Test-Path -LiteralPath $release)) { return $null }
    $match = Select-String -LiteralPath $release -Pattern '^JAVA_VERSION="([0-9]+)' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($match) { return [int]$match.Matches[0].Groups[1].Value }
    return $null
}

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }

    foreach ($root in @("C:\Program Files\Java", "C:\Program Files\Eclipse Adoptium", "C:\Program Files\Microsoft")) {
        if (Test-Path -LiteralPath $root) {
            Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending |
                ForEach-Object { $candidates += $_.FullName }
        }
    }

    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($javac) { $candidates += Split-Path -Parent (Split-Path -Parent $javac.Source) }

    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        if (-not (Test-Path -LiteralPath (Join-Path $candidate "bin\javac.exe"))) { continue }
        if (-not (Test-Path -LiteralPath (Join-Path $candidate "bin\jar.exe"))) { continue }
        $major = Get-JdkMajorVersion $candidate
        # Unknown version: accept only when it came from JAVA_HOME or PATH.
        if ($null -eq $major -or $major -ge 21) { return $candidate }
    }
    return $null
}

$JDK = Find-Jdk
if (-not $JDK) {
    throw "No JDK found. Install JDK 21+ and set JAVA_HOME, or add javac to PATH."
}
$JAVAC = Join-Path $JDK "bin\javac.exe"
$JAR   = Join-Path $JDK "bin\jar.exe"
Write-Host "Using JDK: $JDK"

# ---------------------------------------------------------------------------
# Dependency resolution
# ---------------------------------------------------------------------------
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
} catch {
    # PowerShell 7 already negotiates TLS correctly.
}

$LIBS = if ($env:YGD_LIBS) { $env:YGD_LIBS } else { Join-Path $D "libs" }
[IO.Directory]::CreateDirectory($LIBS) | Out-Null

$PAPER_METADATA = "https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/maven-metadata.xml"
$PAPER_FALLBACK = "https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/paper-api-1.21.11-R0.1-20260511.115010-91.jar"

function Resolve-PaperApiUrl {
    try {
        $metadata = Invoke-WebRequest -Uri $PAPER_METADATA -UseBasicParsing -TimeoutSec 30
        [xml]$xml = $metadata.Content
        $entry = $xml.metadata.versioning.snapshotVersions.snapshotVersion |
            Where-Object { -not $_.classifier -and $_.extension -eq "jar" } |
            Select-Object -First 1
        if ($entry -and $entry.value) {
            return "https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/paper-api-$($entry.value).jar"
        }
    } catch {
        Write-Warning "Could not read Paper snapshot metadata: $($_.Exception.Message)"
    }
    return $PAPER_FALLBACK
}

function Invoke-Download([string]$Url, [string]$Destination) {
    $temporary = "$Destination.download"
    $lastError = $null
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
            Write-Host "Downloading $(Split-Path -Leaf $Destination) ..."
            Invoke-WebRequest -Uri $Url -OutFile $temporary -UseBasicParsing -TimeoutSec 180
            if (-not (Test-Path -LiteralPath $temporary) -or (Get-Item -LiteralPath $temporary).Length -le 0) {
                throw "downloaded file is empty"
            }
            Move-Item -LiteralPath $temporary -Destination $Destination -Force
            return
        } catch {
            $lastError = $_
            if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
            if ($attempt -lt 3) { Start-Sleep -Seconds (2 * $attempt) }
        }
    }
    throw "Failed to download $Url`n$($lastError.Exception.Message)"
}

# The exact compile-time classpath. These are the same coordinates used by
# build.gradle.kts; keep the two lists in sync.
$DEPENDENCIES = @(
    @{ File = "paper-api-1.21.11.jar";                    PaperSnapshot = $true },
    @{ File = "craft-engine-core-26.8.2.jar";             Url = "https://repo.momirealms.net/releases/net/momirealms/craft-engine-core/26.8.2/craft-engine-core-26.8.2.jar" },
    @{ File = "craft-engine-bukkit-26.8.2.jar";           Url = "https://repo.momirealms.net/releases/net/momirealms/craft-engine-bukkit/26.8.2/craft-engine-bukkit-26.8.2.jar" },
    @{ File = "craft-engine-bukkit-proxy-26.8.2.jar";     Url = "https://repo.momirealms.net/releases/net/momirealms/craft-engine-bukkit-proxy/26.8.2/craft-engine-bukkit-proxy-26.8.2.jar" },
    @{ File = "papersdelight-api-4.0.0.jar";              Url = "https://mvn.hezhongkj.top/releases/dev/tako/papersdelight-api/4.0.0/papersdelight-api-4.0.0.jar" },
    @{ File = "guava-33.4.0-jre.jar";                     Url = "https://repo1.maven.org/maven2/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar" },
    @{ File = "adventure-api-4.26.1.jar";                 Url = "https://repo1.maven.org/maven2/net/kyori/adventure-api/4.26.1/adventure-api-4.26.1.jar" },
    @{ File = "adventure-key-4.26.1.jar";                 Url = "https://repo1.maven.org/maven2/net/kyori/adventure-key/4.26.1/adventure-key-4.26.1.jar" },
    @{ File = "adventure-text-serializer-commons-4.26.1.jar"; Url = "https://repo1.maven.org/maven2/net/kyori/adventure-text-serializer-commons/4.26.1/adventure-text-serializer-commons-4.26.1.jar" },
    @{ File = "adventure-text-serializer-legacy-4.26.1.jar";  Url = "https://repo1.maven.org/maven2/net/kyori/adventure-text-serializer-legacy/4.26.1/adventure-text-serializer-legacy-4.26.1.jar" },
    @{ File = "examination-api-1.3.0.jar";                Url = "https://repo1.maven.org/maven2/net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar" },
    @{ File = "annotations-26.0.2.jar";                   Url = "https://repo1.maven.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar" }
)

$classpath = @()
foreach ($dependency in $DEPENDENCIES) {
    $path = Join-Path $LIBS $dependency.File
    if (-not (Test-Path -LiteralPath $path)) {
        $url = if ($dependency.PaperSnapshot) { Resolve-PaperApiUrl } else { $dependency.Url }
        Invoke-Download -Url $url -Destination $path
    }
    if ((Get-Item -LiteralPath $path).Length -le 0) {
        throw "Dependency is empty: $path"
    }
    $classpath += $path
}
$CP = $classpath -join ';'

# Output directory: $env:YGD_BUILD wins, otherwise build/ when it is writable,
# otherwise build-local/ next to the project (e.g. when the sandbox blocks build/).
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

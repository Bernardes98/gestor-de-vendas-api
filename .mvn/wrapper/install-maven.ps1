param(
    [Parameter(Mandatory = $true)]
    [string]$MavenVersion,

    [Parameter(Mandatory = $true)]
    [string]$CacheDir
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression.FileSystem

$mavenHome = Join-Path $CacheDir "apache-maven-$MavenVersion"
$mavenCommand = Join-Path $mavenHome 'bin\mvn.cmd'
$archive = Join-Path $CacheDir "apache-maven-$MavenVersion-bin.zip"
$tempArchive = "$archive.part"
$url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MavenVersion/apache-maven-$MavenVersion-bin.zip"

function Test-ValidZip {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return $false
    }

    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
        try {
            return $zip.Entries.Count -gt 0
        }
        finally {
            $zip.Dispose()
        }
    }
    catch {
        return $false
    }
}

if (Test-Path -LiteralPath $mavenCommand) {
    exit 0
}

New-Item -ItemType Directory -Force -Path $CacheDir | Out-Null

if ((Test-Path -LiteralPath $archive) -and -not (Test-ValidZip -Path $archive)) {
    Write-Host "Cache do Maven corrompido detectado. Removendo $archive"
    Remove-Item -LiteralPath $archive -Force
}

if (-not (Test-Path -LiteralPath $archive)) {
    Remove-Item -LiteralPath $tempArchive -Force -ErrorAction SilentlyContinue
    Write-Host "Baixando Maven $MavenVersion..."

    try {
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $tempArchive

        if (-not (Test-ValidZip -Path $tempArchive)) {
            throw "O arquivo baixado nao e um ZIP Maven valido: $url"
        }

        Move-Item -LiteralPath $tempArchive -Destination $archive -Force
    }
    catch {
        Remove-Item -LiteralPath $tempArchive -Force -ErrorAction SilentlyContinue
        throw
    }
}

if (Test-Path -LiteralPath $mavenHome) {
    Remove-Item -LiteralPath $mavenHome -Recurse -Force
}

Write-Host "Extraindo Maven $MavenVersion..."
Expand-Archive -LiteralPath $archive -DestinationPath $CacheDir -Force

if (-not (Test-Path -LiteralPath $mavenCommand)) {
    throw "A instalacao do Maven terminou sem criar $mavenCommand"
}

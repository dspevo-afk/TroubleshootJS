[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateSet('OBF', 'PRETTY', 'DETAILED')]
    [string]$Style = 'OBF',
    [ValidateSet('Compile', 'Dev')]
    [string]$Target = 'Compile',
    [ValidateRange(1, 65535)]
    [int]$Port = 8888
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$gwtVersion = '2.7.0'
$toolRoot = Join-Path $repositoryRoot ".tools\gwt-$gwtVersion"
$workDirectoryName = if ($Target -eq 'Dev') { 'dev-work' } else { 'compile-work' }
$cacheDirectoryName = if ($Target -eq 'Dev') { 'dev-cache' } else { 'compile-cache' }
$workDirectory = Join-Path $toolRoot $workDirectoryName
$persistentCacheRoot = Join-Path $toolRoot $cacheDirectoryName
$moduleName = 'com.lushprojects.circuitjs1.circuitjs1'

if ($JavaHome) {
    $java = Join-Path $JavaHome 'bin\java.exe'
} else {
    $javaCommand = Get-Command 'java.exe' -ErrorAction SilentlyContinue
    $java = if ($javaCommand) { $javaCommand.Source } else { $null }
}

if (-not $java -or -not (Test-Path $java -PathType Leaf)) {
    throw 'Java was not found. Install a JDK 8 distribution and set JAVA_HOME, or pass -JavaHome.'
}

$versionStartInfo = New-Object System.Diagnostics.ProcessStartInfo
$versionStartInfo.FileName = $java
$versionStartInfo.Arguments = '-version'
$versionStartInfo.UseShellExecute = $false
$versionStartInfo.RedirectStandardOutput = $true
$versionStartInfo.RedirectStandardError = $true
$versionProcess = [System.Diagnostics.Process]::Start($versionStartInfo)
$javaVersion = $versionProcess.StandardOutput.ReadToEnd() + $versionProcess.StandardError.ReadToEnd()
$versionProcess.WaitForExit()

if ($versionProcess.ExitCode -ne 0 -or $javaVersion -notmatch 'version "1\.8\.') {
    throw "GWT $gwtVersion requires JDK 8. Selected Java reported: $($javaVersion.Trim())"
}

$artifacts = @(
    [pscustomobject]@{ File = 'gwt-dev-2.7.0.jar'; Url = 'https://repo.maven.apache.org/maven2/com/google/gwt/gwt-dev/2.7.0/gwt-dev-2.7.0.jar'; Sha256 = 'C7321E367FC24E5C7AD97B74D1CF2980C45FBD7CBE1FD412F8A8BA128F685135' },
    [pscustomobject]@{ File = 'gwt-user-2.7.0.jar'; Url = 'https://repo.maven.apache.org/maven2/com/google/gwt/gwt-user/2.7.0/gwt-user-2.7.0.jar'; Sha256 = 'D3721BCDD7C6855A524801212DD74548967D8FB65CBC212A20890732FF2C6948' },
    [pscustomobject]@{ File = 'validation-api-1.0.0.GA.jar'; Url = 'https://repo.maven.apache.org/maven2/javax/validation/validation-api/1.0.0.GA/validation-api-1.0.0.GA.jar'; Sha256 = 'E459F313EBC6DB2483F8CEAAD39AF07086361B474FA92E40F442E8DE5D9895DC' },
    [pscustomobject]@{ File = 'validation-api-1.0.0.GA-sources.jar'; Url = 'https://repo.maven.apache.org/maven2/javax/validation/validation-api/1.0.0.GA/validation-api-1.0.0.GA-sources.jar'; Sha256 = 'A394D52A9B7FE2BB14F0718D2B3C8308FFE8F37E911956012398D55C9F9F9B54' },
    [pscustomobject]@{ File = 'asm-5.0.3.jar'; Url = 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm/5.0.3/asm-5.0.3.jar'; Sha256 = '71C4F78E437B8FDCD9CC0DFD2ABEA8C089EB677005A6A5CFF320206CC52B46CC' },
    [pscustomobject]@{ File = 'asm-analysis-5.0.3.jar'; Url = 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm-analysis/5.0.3/asm-analysis-5.0.3.jar'; Sha256 = 'E8FA2A63462C96557DCD36C25525E1264B77366FF851CF0B94EB7592B290849D' },
    [pscustomobject]@{ File = 'asm-commons-5.0.3.jar'; Url = 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm-commons/5.0.3/asm-commons-5.0.3.jar'; Sha256 = '18C1E092230233C9D29E46F21943D769BDB48130CC279E4B0E663F423948C2DA' },
    [pscustomobject]@{ File = 'asm-tree-5.0.3.jar'; Url = 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm-tree/5.0.3/asm-tree-5.0.3.jar'; Sha256 = '347A7A9400F9964E87C91D3980E48EEBDC8D024BC3B36F7F22189C662853A51C' },
    [pscustomobject]@{ File = 'asm-util-5.0.3.jar'; Url = 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm-util/5.0.3/asm-util-5.0.3.jar'; Sha256 = '2768EDBFA2681B5077F08151DE586A6D66B916703CDA3AB297E58B41AE8F2362' }
)

New-Item -ItemType Directory -Force -Path $toolRoot, $workDirectory, $persistentCacheRoot | Out-Null
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

foreach ($artifact in $artifacts) {
    $destination = Join-Path $toolRoot $artifact.File
    if ((Test-Path $destination -PathType Leaf) -and
        (Get-FileHash -Algorithm SHA256 -Path $destination).Hash -ne $artifact.Sha256) {
        Write-Host "Refreshing $($artifact.File) after a checksum mismatch..."
        Remove-Item -Force -Path $destination
    }
    if (-not (Test-Path $destination -PathType Leaf)) {
        Write-Host "Downloading $($artifact.File)..."
        $temporaryDestination = "$destination.download"
        Invoke-WebRequest -UseBasicParsing -Uri $artifact.Url -OutFile $temporaryDestination
        Move-Item -Force -Path $temporaryDestination -Destination $destination
    }
    if ((Get-FileHash -Algorithm SHA256 -Path $destination).Hash -ne $artifact.Sha256) {
        Remove-Item -Force -Path $destination
        throw "Checksum verification failed for $($artifact.File)."
    }
}

$classpathEntries = @((Join-Path $repositoryRoot 'src'))
$classpathEntries += $artifacts | ForEach-Object { Join-Path $toolRoot $_.File }
$classpath = [string]::Join([IO.Path]::PathSeparator, $classpathEntries)

if ($Target -eq 'Dev') {
    $javaArguments = @(
        '-Xmx1g',
        "-Dgwt.persistentunitcachedir=$persistentCacheRoot",
        '-cp', $classpath,
        'com.google.gwt.dev.DevMode',
        '-war', (Join-Path $repositoryRoot 'war'),
        '-workDir', $workDirectory,
        '-bindAddress', '127.0.0.1',
        '-port', $Port,
        '-startupUrl', 'circuitjs.html',
        '-logLevel', 'INFO',
        $moduleName
    )
    Write-Host "Starting CircuitJS at http://127.0.0.1:$Port/circuitjs.html"
} else {
    $javaArguments = @(
        '-Xmx1g',
        "-Dgwt.persistentunitcachedir=$persistentCacheRoot",
        '-cp', $classpath,
        'com.google.gwt.dev.Compiler',
        '-war', (Join-Path $repositoryRoot 'war'),
        '-workDir', $workDirectory,
        '-style', $Style,
        '-logLevel', 'INFO',
        $moduleName
    )
}

Push-Location $repositoryRoot
try {
    & $java $javaArguments
    if ($LASTEXITCODE -ne 0) {
        throw "GWT $Target failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
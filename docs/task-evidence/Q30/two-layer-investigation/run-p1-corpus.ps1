[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$GwtHome,
    [ValidateSet('two-layer', 'p1-policy', 'geometry')][string]$Mode = 'two-layer',
    [string]$Seed = 'all',
    [ValidateRange(1, 12)][int]$CandidateCount = 6,
    [string]$OutputPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..\..')).Path
$jdk = (Resolve-Path -LiteralPath $JavaHome).Path
$gwt = (Resolve-Path -LiteralPath $GwtHome).Path
$scratch = Join-Path ([IO.Path]::GetTempPath()) (
    'tsj-q30-p1-corpus-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $scratch 'classes'
$emptySource = Join-Path $scratch 'empty-sourcepath'
New-Item -ItemType Directory -Path $scratch, $classes, $emptySource | Out-Null

# Compile a snapshot so a concurrent source edit cannot change a running corpus.
Copy-Item -LiteralPath (Join-Path $repo 'src') -Destination (Join-Path $scratch 'src') -Recurse
$pilotDir = Join-Path $scratch 'docs\task-evidence\Q30'
New-Item -ItemType Directory -Path $pilotDir | Out-Null
Copy-Item -LiteralPath (Join-Path $repo 'docs\task-evidence\Q30\Q30PhysicalPilot.java') `
    -Destination (Join-Path $pilotDir 'Q30PhysicalPilot.java')

$stub = Join-Path $scratch 'PhysicalSpecificationDeveloperVerifier.java'
[IO.File]::WriteAllText($stub,
    'package com.lushprojects.circuitjs1.client; final class PhysicalSpecificationDeveloperVerifier { static void verify(CirSim sim) { } }')
$production = @(Get-ChildItem (Join-Path $scratch 'src') -Recurse -Filter '*.java' |
    Where-Object { $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' } |
    Select-Object -ExpandProperty FullName)
$paths = @($production) + @($stub, (Join-Path $pilotDir 'Q30PhysicalPilot.java'))
$sourceList = Join-Path $scratch 'sources.txt'
[IO.File]::WriteAllLines($sourceList,
    @($paths | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }))
$jars = @(Get-ChildItem $gwt -Filter '*.jar' -File | Select-Object -ExpandProperty FullName)
if ($jars.Count -lt 2) { throw 'GWT JAR bundle not found' }
$classpath = $classes + [IO.Path]::PathSeparator + ($jars -join [IO.Path]::PathSeparator)
$javac = Join-Path $jdk 'bin\javac.exe'
$java = Join-Path $jdk 'bin\java.exe'
& $javac -version *> (Join-Path $scratch 'java-version.txt')
if ($LASTEXITCODE -ne 0 -or (Get-Content (Join-Path $scratch 'java-version.txt') -Raw) -notmatch 'javac 1\.8\.') {
    throw 'Q30-P1 corpus requires JDK 8'
}
& $javac -source 7 -target 7 -encoding UTF-8 -classpath $classpath `
    -sourcepath $emptySource -d $classes ('@' + $sourceList) `
    *> (Join-Path $scratch 'javac.txt')
if ($LASTEXITCODE -ne 0) { throw "Q30-P1 corpus compilation failed: $scratch" }
$raw = if ($OutputPath) { $OutputPath } else { Join-Path $scratch 'routing.txt' }
if ($Mode -eq 'two-layer') {
    & $java -Xmx6g -cp $classpath com.lushprojects.circuitjs1.client.Q30PhysicalPilot `
        --two-layer $Seed $CandidateCount *> $raw
} elseif ($Mode -eq 'p1-policy') {
    & $java -Xmx6g -cp $classpath com.lushprojects.circuitjs1.client.Q30PhysicalPilot `
        --p1-policy $Seed *> $raw
} else {
    if ($Seed -eq 'all') { throw 'Geometry export requires one signed-long seed' }
    & $java -Xmx6g -cp $classpath com.lushprojects.circuitjs1.client.Q30PhysicalPilot `
        --geometry $Seed *> $raw
}
if ($LASTEXITCODE -ne 0) { throw "Q30-P1 corpus failed: $raw" }
Write-Output "PASS: Q30-P1 structural $Mode corpus; raw: $raw; compiler: $scratch\javac.txt"

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$SourceRoot,
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$GwtHome,
    [string]$Seed = 'all',
    [ValidateRange(1, 6)][int]$CandidateCount = 6
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $SourceRoot).Path
$jdk = (Resolve-Path -LiteralPath $JavaHome).Path
$gwt = (Resolve-Path -LiteralPath $GwtHome).Path
$scratch = Join-Path ([IO.Path]::GetTempPath()) (
    'tsj-q30-two-layer-repro-' + [Guid]::NewGuid().ToString('N'))
$pilotDir = Join-Path $scratch 'docs\task-evidence\Q30'
$classes = Join-Path $scratch 'classes'
$emptySource = Join-Path $scratch 'empty-sourcepath'
New-Item -ItemType Directory -Path $scratch, $pilotDir, $classes, $emptySource | Out-Null
Copy-Item -LiteralPath (Join-Path $source 'src') -Destination (Join-Path $scratch 'src') -Recurse
Copy-Item -LiteralPath (Join-Path $source 'docs\task-evidence\Q30\Q30PhysicalPilot.java') `
    -Destination (Join-Path $pilotDir 'Q30PhysicalPilot.java')
$patch = Join-Path $PSScriptRoot 'two-layer-investigation.UNAPPLIED.patch'
& git -C $scratch apply --check $patch
if ($LASTEXITCODE -ne 0) { throw 'Prototype patch does not apply to the copied source' }
& git -C $scratch apply $patch
if ($LASTEXITCODE -ne 0) { throw 'Prototype patch failed in copied source' }

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
& $javac -source 7 -target 7 -encoding UTF-8 -classpath $classpath `
    -sourcepath $emptySource -d $classes ('@' + $sourceList) `
    *> (Join-Path $scratch 'javac.txt')
if ($LASTEXITCODE -ne 0) { throw "Q30 pilot compile failed: $scratch" }
& $java -Xmx6g -cp $classpath com.lushprojects.circuitjs1.client.Q30PhysicalPilot `
    --two-layer $Seed $CandidateCount *> (Join-Path $scratch 'routing.txt')
if ($LASTEXITCODE -ne 0) { throw "Q30 pilot failed: $scratch" }
Write-Output "PASS: Q30 two-layer structural comparison; output: $scratch"

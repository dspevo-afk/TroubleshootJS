[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$GwtHome
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$scratch = Join-Path ([IO.Path]::GetTempPath()) (
    'tsj-q30-physical-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $scratch 'classes'
$emptySource = Join-Path $scratch 'empty-sourcepath'
New-Item -ItemType Directory -Path $classes, $emptySource | Out-Null

$stub = Join-Path $scratch 'PhysicalSpecificationDeveloperVerifier.java'
[IO.File]::WriteAllText($stub,
    'package com.lushprojects.circuitjs1.client; final class PhysicalSpecificationDeveloperVerifier { static void verify(CirSim sim) { } }')
$pilot = Join-Path $PSScriptRoot 'Q30PhysicalPilot.java'
$production = @(Get-ChildItem (Join-Path $repo 'src') -Recurse -Filter '*.java' |
    Where-Object { $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' } |
    Select-Object -ExpandProperty FullName)
$paths = @($production) + @($stub, $pilot)
$sourceList = Join-Path $scratch 'sources.txt'
[IO.File]::WriteAllLines($sourceList,
    @($paths | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }))
$jars = @(Get-ChildItem $GwtHome -Filter '*.jar' -File |
    Select-Object -ExpandProperty FullName)
if ($jars.Count -lt 2) { throw 'GWT JAR dependency bundle not found' }
$classpath = $classes + [IO.Path]::PathSeparator +
    ($jars -join [IO.Path]::PathSeparator)
$javac = Join-Path $JavaHome 'bin\javac.exe'
$java = Join-Path $JavaHome 'bin\java.exe'
& $javac -source 7 -target 7 -encoding UTF-8 -classpath $classpath `
    -sourcepath $emptySource -d $classes ('@' + $sourceList)
if ($LASTEXITCODE -ne 0) { throw 'Q30 physical pilot compilation failed' }
& $java -cp $classpath com.lushprojects.circuitjs1.client.Q30PhysicalPilot `
    --root-plan | Tee-Object -FilePath (Join-Path $scratch 'routing.txt')
if ($LASTEXITCODE -ne 0) { throw 'Q30 physical pilot failed to run' }
Write-Host ('Q30 physical pilot scratch: ' + $scratch)

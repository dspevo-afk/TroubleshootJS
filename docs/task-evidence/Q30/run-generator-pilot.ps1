[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$GwtHome,
    [ValidateSet('Generator', 'WholeBoard')][string]$Pilot = 'Generator',
    [string]$WholeBoardMode = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$client = Join-Path $repo 'src\com\lushprojects\circuitjs1\client'
$scratch = Join-Path ([IO.Path]::GetTempPath()) (
    'tsj-q30-generator-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null
$stub = Join-Path $scratch 'PhysicalSpecificationDeveloperVerifier.java'
[IO.File]::WriteAllText($stub,
    'package com.lushprojects.circuitjs1.client; final class PhysicalSpecificationDeveloperVerifier { static void verify(CirSim sim) { throw new UnsupportedOperationException(); } }')

# Native CircuitJS logs via JSNI, which cannot execute on the JVM. Replace
# only that logging method in a scratch copy; keep every solver model unchanged.
$cirText = [IO.File]::ReadAllText((Join-Path $client 'CirSim.java'))
$pattern = 'public static native void console\(String text\)\s*/\*-\{\s*console\.log\(text\);\s*\}-\*/;'
$patched = [regex]::Replace($cirText, $pattern,
    'public static void console(String text) { System.err.println(text); }')
if ($patched -eq $cirText) { throw 'CirSim JSNI logging pattern changed' }
$shim = Join-Path $scratch 'CirSim.java'
[IO.File]::WriteAllText($shim, $patched)

$pilotClass = if ($Pilot -eq 'Generator') {
    'Rb30GeneratorPilot'
} else {
    'Q30WholeBoardElectricalPilot'
}
$pilotSource = Join-Path $PSScriptRoot ($pilotClass + '.java')
$sources = @(Get-ChildItem $client -Filter '*.java' -File |
    Where-Object { $_.Name -ne 'CirSim.java' -and
        $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' } |
    Select-Object -ExpandProperty FullName)
$paths = @($sources) + @($stub, $shim, $pilotSource)
$sourceList = Join-Path $scratch 'sources.txt'
[IO.File]::WriteAllLines($sourceList,
    @($paths | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }))
$jars = @(Get-ChildItem $GwtHome -Filter '*.jar' -File |
    Select-Object -ExpandProperty FullName)
if ($jars.Count -lt 2) { throw 'GWT JAR dependency bundle not found' }
$classpath = $scratch + [IO.Path]::PathSeparator +
    ($jars -join [IO.Path]::PathSeparator)
$javac = Join-Path $JavaHome 'bin\javac.exe'
$java = Join-Path $JavaHome 'bin\java.exe'
& $javac -source 7 -target 7 -encoding UTF-8 -classpath $classpath `
    -d $scratch ('@' + $sourceList) *> (Join-Path $scratch 'javac.txt')
if ($LASTEXITCODE -ne 0) { throw 'Q30 generator pilot compilation failed' }
if ($Pilot -eq 'WholeBoard' -and $WholeBoardMode -ne '') {
    $pilotArgs = @($WholeBoardMode.Split(' ') | Where-Object { $_ -ne '' })
    & $java -ea -cp $classpath `
        ('com.lushprojects.circuitjs1.client.' + $pilotClass) `
        @pilotArgs *> (Join-Path $scratch 'pilot-pass.txt')
} else {
    & $java -ea -cp $classpath `
        ('com.lushprojects.circuitjs1.client.' + $pilotClass) `
        *> (Join-Path $scratch 'pilot-pass.txt')
}
if ($LASTEXITCODE -ne 0) {
    Get-Content (Join-Path $scratch 'pilot-pass.txt') | Select-Object -Last 30
    Write-Host ('Q30 ' + $Pilot + ' pilot scratch: ' + $scratch)
    throw ('Q30 ' + $Pilot + ' pilot failed')
}
if ($Pilot -eq 'Generator') {
    & $java -ea -cp $classpath `
        com.lushprojects.circuitjs1.client.Rb30GeneratorPilot nmos `
        *> (Join-Path $scratch 'pilot-variants.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Q30 generator NMOS pilot failed' }
    Get-Content (Join-Path $scratch 'pilot-pass.txt') |
        Select-String '^PASS RB30'
    Get-Content (Join-Path $scratch 'pilot-variants.txt') |
        Select-String '^RB30 NMOS seed='
} else {
    Get-Content (Join-Path $scratch 'pilot-pass.txt') |
        Select-String '^Q30_WHOLE_BOARD|^PASS: Q30 whole-board'
}
Write-Host ('Q30 ' + $Pilot + ' pilot scratch: ' + $scratch)

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$SourceRoot,
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$GwtHome,
    [string]$Seed = 'all',
    [ValidateRange(1, 6)][int]$CandidateCount = 6,
    [string]$OutputDir = '',
    [string]$SyntheticRaw = '',
    [string]$ExporterPatch = '',
    [string]$PlannerSource = '',
    [string]$PlannerLabel = 'unspecified',
    [string]$MeasurementLabel = 'unclassified',
    [string]$MeasurementStatus = 'unclassified',
    [string]$Python = 'python',
    [int]$ExpectedQ30Parts = 33
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$source = (Resolve-Path -LiteralPath $SourceRoot).Path
$jdk = (Resolve-Path -LiteralPath $JavaHome).Path
$gwt = (Resolve-Path -LiteralPath $GwtHome).Path
$q30Source = Join-Path $source 'docs\task-evidence\Q30\Q30PhysicalPilot.java'
$synthetic = if ($SyntheticRaw) {
    (Resolve-Path -LiteralPath $SyntheticRaw).Path
} else {
    (Resolve-Path -LiteralPath (Join-Path $source 'docs\task-evidence\Q30\two-layer-investigation\scaling\thirtythree-raw.txt')).Path
}
$evidenceDir = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$floorplanPatch = if ($ExporterPatch) {
    (Resolve-Path -LiteralPath $ExporterPatch).Path
} else {
    (Resolve-Path -LiteralPath (Join-Path $source 'docs\task-evidence\Q30\q30-floorplan-v3-experiments.patch')).Path
}
$argumentPatch = (Resolve-Path -LiteralPath (Join-Path $evidenceDir 'inspect-args.patch')).Path
$measureScript = (Resolve-Path -LiteralPath (Join-Path $evidenceDir 'measure_floorplans.py')).Path
$plannerSourcePath = if ($PlannerSource) {
    (Resolve-Path -LiteralPath $PlannerSource).Path
} else {
    Join-Path $source 'src\com\lushprojects\circuitjs1\client\PcbPlacementPlanner.java'
}
$plannerSourceMode = if ($PlannerSource) { 'explicit-snapshot' } else { 'source-tree' }
$plannerInput = [IO.Path]::GetFileName($plannerSourcePath)

if (-not (Test-Path -LiteralPath $q30Source -PathType Leaf)) {
    throw "Q30PhysicalPilot.java not found: $q30Source"
}
if (-not (Test-Path -LiteralPath $synthetic -PathType Leaf)) {
    throw "Synthetic raw receipt not found: $synthetic"
}
if (-not (Test-Path -LiteralPath $floorplanPatch -PathType Leaf)) {
    throw "Floorplan exporter patch not found: $floorplanPatch"
}
if (-not (Test-Path -LiteralPath $plannerSourcePath -PathType Leaf)) {
    throw "Planner source snapshot not found: $plannerSourcePath"
}

if ($OutputDir) {
    $output = [IO.Path]::GetFullPath($OutputDir)
} else {
    $output = Join-Path $evidenceDir 'run-output'
}
New-Item -ItemType Directory -Path $output -Force | Out-Null

$seeds = @()
if ($Seed -eq 'all') {
    $seeds = @(0L, 1L, 3L, 17L, 42L, -1L, 11L, 23L, 37L, 59L, 83L, -23L)
} else {
    foreach ($token in $Seed.Split(',')) {
        if ($token.Trim().Length -eq 0) { throw 'Seed list contains an empty item' }
        $seeds += [Int64]::Parse($token.Trim(), [Globalization.CultureInfo]::InvariantCulture)
    }
}

$scratch = Join-Path ([IO.Path]::GetTempPath()) (
    'tsj-q30-floorplan-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $scratch 'classes'
$emptySource = Join-Path $scratch 'empty-sourcepath'
$pilotDir = Join-Path $scratch 'docs\task-evidence\Q30'
$completed = $false

function Remove-TaskScratch {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return }
    $tempRoot = (Resolve-Path -LiteralPath ([IO.Path]::GetTempPath())).Path.TrimEnd('\')
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    $leaf = Split-Path -Leaf $resolved
    $insideTemp = $resolved.StartsWith($tempRoot + '\', [StringComparison]::OrdinalIgnoreCase)
    $ownedName = $leaf.StartsWith('tsj-q30-floorplan-', [StringComparison]::OrdinalIgnoreCase)
    if (-not ($insideTemp -and $ownedName)) {
        Write-Warning "Refusing to remove unverified scratch path: $resolved"
        return
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

try {
    New-Item -ItemType Directory -Path $classes, $emptySource, $pilotDir -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $source 'src') -Destination (Join-Path $scratch 'src') -Recurse
    if ($PlannerSource) {
        $plannerTarget = Join-Path $scratch 'src\com\lushprojects\circuitjs1\client\PcbPlacementPlanner.java'
        Copy-Item -LiteralPath $plannerSourcePath -Destination $plannerTarget -Force
    }
    Copy-Item -LiteralPath $q30Source -Destination (Join-Path $pilotDir 'Q30PhysicalPilot.java')
    & git -C $scratch apply --check $floorplanPatch
    if ($LASTEXITCODE -ne 0) { throw 'The floorplan exporter patch does not apply to this Q30 source' }
    & git -C $scratch apply $floorplanPatch
    if ($LASTEXITCODE -ne 0) { throw 'The floorplan exporter patch failed to apply' }
    & git -C $scratch apply --check $argumentPatch
    if ($LASTEXITCODE -ne 0) { throw 'The inspect argument patch does not apply after the exporter patch' }
    & git -C $scratch apply $argumentPatch
    if ($LASTEXITCODE -ne 0) { throw 'The inspect argument patch failed to apply' }

    $stub = Join-Path $scratch 'PhysicalSpecificationDeveloperVerifier.java'
    [IO.File]::WriteAllText($stub,
        'package com.lushprojects.circuitjs1.client; final class PhysicalSpecificationDeveloperVerifier { static void verify(CirSim sim) { } }')
    $production = @(Get-ChildItem (Join-Path $scratch 'src') -Recurse -Filter '*.java' |
        Where-Object { $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' } |
        Select-Object -ExpandProperty FullName)
    $pilot = Join-Path $pilotDir 'Q30PhysicalPilot.java'
    $sourceList = Join-Path $scratch 'sources.txt'
    [IO.File]::WriteAllLines($sourceList,
        @(@($production) + @($stub, $pilot) |
            ForEach-Object { '"' + $_.Replace('\', '/') + '"' }))
    $jars = @(Get-ChildItem $gwt -Filter '*.jar' -File |
        Select-Object -ExpandProperty FullName)
    if ($jars.Count -lt 2) { throw 'GWT JAR dependency bundle not found' }
    $classpath = $classes + [IO.Path]::PathSeparator +
        ($jars -join [IO.Path]::PathSeparator)
    $javac = Join-Path $jdk 'bin\javac.exe'
    $java = Join-Path $jdk 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javac -PathType Leaf) -or
            -not (Test-Path -LiteralPath $java -PathType Leaf)) {
        throw "JDK executables not found below $jdk"
    }
    & $javac -source 7 -target 7 -encoding UTF-8 -classpath $classpath `
        -sourcepath $emptySource -d $classes ('@' + $sourceList) `
        *> (Join-Path $output 'javac.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Q30 floorplan inspect compilation failed' }

    $raw = Join-Path $output 'q30-current-inspect.raw.txt'
    [IO.File]::WriteAllText($raw, '')
    foreach ($currentSeed in $seeds) {
        foreach ($candidate in 0..($CandidateCount - 1)) {
            $lines = & $java -Xmx4g -cp $classpath `
                com.lushprojects.circuitjs1.client.Q30PhysicalPilot `
                --root-inspect $currentSeed $candidate 2>&1
            Add-Content -LiteralPath $raw -Value $lines
            if ($LASTEXITCODE -ne 0) {
                throw "Q30 inspect failed for seed=$currentSeed candidate=$candidate"
            }
        }
    }

    $q30Hash = (Get-FileHash -LiteralPath $q30Source -Algorithm SHA256).Hash.ToLowerInvariant()
    $plannerHash = (Get-FileHash -LiteralPath $plannerSourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
    $q30PlanSource = Join-Path $source 'src\com\lushprojects\circuitjs1\client\Rb30Plan.java'
    $q30PlanHash = if (Test-Path -LiteralPath $q30PlanSource -PathType Leaf) {
        (Get-FileHash -LiteralPath $q30PlanSource -Algorithm SHA256).Hash.ToLowerInvariant()
    } else { 'not-present-in-source-root' }
    $patchHash = (Get-FileHash -LiteralPath $floorplanPatch -Algorithm SHA256).Hash.ToLowerInvariant()
    $syntheticHash = (Get-FileHash -LiteralPath (Join-Path $source 'docs\task-evidence\Q30\two-layer-investigation\scaling\Q30TwoLayerScaling.java') -Algorithm SHA256).Hash.ToLowerInvariant()
    $pythonArgs = @(
        $measureScript, '--q30-inspect', $raw, '--synthetic-raw', $synthetic,
        '--output-dir', $output, '--q30-source-sha256', $q30Hash,
        '--q30-plan-sha256', $q30PlanHash,
        '--q30-planner-sha256', $plannerHash, '--q30-planner-input', $plannerInput,
        '--q30-planner-mode', $plannerSourceMode, '--planner-label', $PlannerLabel,
        '--measurement-label', $MeasurementLabel, '--measurement-status', $MeasurementStatus,
        '--q30-patch-sha256', $patchHash, '--synthetic-source-sha256', $syntheticHash)
    if ($ExpectedQ30Parts -gt 0) {
        $pythonArgs += @('--expected-q30-parts', [string]$ExpectedQ30Parts)
    }
    & $Python @pythonArgs
    if ($LASTEXITCODE -ne 0) { throw 'Floorplan metric extraction failed' }
    $completed = $true
    Write-Output ('PASS: Q30 floorplan inspect and comparison; output: ' + $output)
} finally {
    if ($completed) {
        Remove-TaskScratch $scratch
    } elseif (Test-Path -LiteralPath $scratch) {
        Write-Warning ('Scratch retained for failure diagnosis: ' + $scratch)
    }
}

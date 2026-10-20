[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('contracts','matrix','build')]
    [string]$Phase,
    [Parameter(Mandatory=$true)]
    [string]$JavaHome,
    [Parameter(Mandatory=$true)]
    [string]$PythonExe,
    [switch]$VerifyCleanup
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$packetRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$r2Root = (Resolve-Path -LiteralPath (Join-Path $packetRoot '..')).Path
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $packetRoot '..\..\..\..\..')).Path
$manifestPath = Join-Path $packetRoot 'suites.json'
$assessmentPath = Join-Path $r2Root 'routes-assessment.json'
$inputsPath = Join-Path $r2Root 'inputs.py'
$verifyScript = Join-Path $repoRoot 'scripts\verify-current-contracts.ps1'
$buildScript = Join-Path $repoRoot 'scripts\build.ps1'

function Write-Utf8NoBom([string]$Path, [string]$Text) {
    [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($false))
}

function Write-Json([string]$Path, [object]$Value, [int]$Depth = 12) {
    Write-Utf8NoBom $Path (($Value | ConvertTo-Json -Depth $Depth) + "`n")
}

function Read-Json([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw ('Missing JSON artifact: ' + $Path)
    }
    return (Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json)
}

function Assert-AssessmentPrerequisite {
    if (-not (Test-Path -LiteralPath $assessmentPath -PathType Leaf)) {
        throw 'Missing root-owned routes-assessment.json; final validation is blocked'
    }
    $assessment = Read-Json $assessmentPath
    foreach ($field in @('assessmentStatus','integrityStatus','nativeStatus','fitnessStatus')) {
        if (-not ($assessment.PSObject.Properties.Name -contains $field) -or
                [string]$assessment.$field -cne 'PASS') {
            throw ('routes-assessment.json prerequisite is not PASS: ' + $field)
        }
    }
    if (-not ($assessment.PSObject.Properties.Name -contains 'phase') -or
            [string]$assessment.phase -cne 'routes') {
        throw 'routes-assessment.json is not a routes assessment'
    }
}

function Assert-Manifest {
    $manifest = Read-Json $manifestPath
    if ([int]$manifest.schema -ne 1) { throw 'final validation suite schema changed' }
    $contracts = @($manifest.contracts)
    if ($contracts.Count -ne 15) { throw 'final validation requires exactly fifteen contract suites' }
    $names = @($contracts | ForEach-Object { [string]$_.name })
    $expectedNames = @(
        'P03PlacementContractTest','PcbCompactionContractTest',
        'MediumBoardFloorplanningContractTest','A10DependencyContractTest',
        'U04SessionContractTest','U06SessionSaveContractTest',
        'RelBSessionIdentityContractTest','PlayerSessionFingerprintContractTest',
        'ControlledIndicatorAssemblyContractTest','Rb30PhysicalMetadataContractTest',
        'QuickPlayGateContractTest','QuickPlayContractTest',
        'PcbCompactnessContractTest','PcbLayerRoutingResumptionContractTest',
        'MediumBoardNormalAdmissionContractTest')
    if ([string]::Join('|',$names) -cne [string]::Join('|',$expectedNames)) {
        throw 'final validation contract suite order changed'
    }
    $matrix = $manifest.matrix
    $small = @($matrix.smallFamilies)
    $expectedSmall = @(
        'LED_INDICATOR','DIODE_PROTECTED_INDICATOR','PARALLEL_DUAL_INDICATOR',
        'RC_DELAY','NPN_LOW_SIDE_SWITCH','NMOS_LOW_SIDE_SWITCH','RELAY_OUTPUT',
        'SENSOR_CONTROL','RB15_CONTROL','COMPOSED_CONTROLLED_INDICATOR')
    if ([string]::Join('|',$small) -cne [string]::Join('|',$expectedSmall) -or
        [string]$matrix.name -cne 'QuickPlayPhysicalMatrixContractTest' -or
        [int]$matrix.smallBudgetMillis -ne 180000 -or
        [string]$matrix.mediumFamily -cne 'RB30_CONTROL' -or
        [int]$matrix.mediumBudgetMillis -ne 600000 -or
        [int]$matrix.accepted -ne 220 -or [int]$matrix.exactReplays -ne 220) {
        throw 'final validation physical-matrix contract changed'
    }
    if ([string]$manifest.build.style -cne 'OBF' -or
        [string]$manifest.build.target -cne 'Compile' -or
        [int]$manifest.build.processTimeoutSeconds -ne 900 -or
        [int]$manifest.build.requiredPermutations -ne 5) {
        throw 'final validation build contract changed'
    }
    return $manifest
}

function Assert-CandidateSeal {
    if (-not (Test-Path -LiteralPath (Join-Path $r2Root 'candidate-inputs.json') -PathType Leaf)) {
        throw 'Missing frozen candidate-inputs.json'
    }
    if (-not (Test-Path -LiteralPath $inputsPath -PathType Leaf)) {
        throw 'Missing parent r2 inputs.py'
    }
}

function Get-PhasePath([string]$Suffix) {
    return (Join-Path $packetRoot ($Phase + $Suffix))
}

function Get-PhaseArtifactNames {
    $names = @(
        ($Phase + '-input-audit-before.txt'),
        ($Phase + '-input-audit-after.txt'),
        ($Phase + '-process-launch.json'),
        ($Phase + '-log.txt'),
        ($Phase + '-timing.json'),
        ($Phase + '-process-cleanup.json'),
        ($Phase + '-log-sanitization.json'))
    if ($Phase -in @('contracts','matrix')) {
        $names += ($Phase + '-receipt.txt')
    }
    return $names
}

function Assert-PhaseArtifactsAbsent {
    foreach ($name in (Get-PhaseArtifactNames)) {
        if (Test-Path -LiteralPath (Join-Path $packetRoot $name)) {
            throw ('Refusing to overwrite final validation artifact: ' + $name)
        }
    }
}

function Assert-PhasePrerequisite([string]$RequiredPhase) {
    $timingPath = Join-Path $packetRoot ($RequiredPhase + '-timing.json')
    $cleanupPath = Join-Path $packetRoot ($RequiredPhase + '-process-cleanup.json')
    if (-not (Test-Path -LiteralPath $timingPath -PathType Leaf) -or
            -not (Test-Path -LiteralPath $cleanupPath -PathType Leaf)) {
        throw ('Final validation phase prerequisite is missing: ' + $RequiredPhase)
    }
    $timing = Read-Json $timingPath
    $cleanup = Read-Json $cleanupPath
    if ([string]$timing.status -cne 'PASS' -or [int]$timing.exitCode -ne 0 -or
            [string]$timing.phase -cne $RequiredPhase) {
        throw ('Final validation phase prerequisite failed: ' + $RequiredPhase)
    }
    if ([string]$cleanup.status -cne 'PASS' -or
            [bool]$cleanup.terminationAttempted) {
        throw ('Final validation cleanup prerequisite failed: ' + $RequiredPhase)
    }
}

function Invoke-CandidateAudit([string]$Kind) {
    $path = Join-Path $packetRoot ($Phase + '-input-audit-' + $Kind + '.txt')
    if (Test-Path -LiteralPath $path) { throw ('Refusing to overwrite input audit: ' + $path) }
    $lines = @()
    $exitCode = 2
    try {
        $lines = @(& $PythonExe -X utf8 -B $inputsPath audit-candidate 2>&1)
        $exitCode = [int]$LASTEXITCODE
    } catch {
        $lines += $_.Exception.ToString()
        $exitCode = 2
    }
    $text = ($lines | ForEach-Object { [string]$_ }) -join [Environment]::NewLine
    Write-Utf8NoBom $path ($text + "`n")
    if ($exitCode -ne 0 -or $text -notmatch '(?m)^PASS audit-candidate exact sources=1481 deltas=38 gates=14\r?$') {
        throw ('Candidate input audit failed (' + $Kind + '), exit ' + $exitCode)
    }
}

function Get-ProcessBirth([int]$ProcessId, [string]$Role) {
    $process = Get-Process -Id $ProcessId -ErrorAction Stop
    $startUtc = $process.StartTime.ToUniversalTime()
    [ordered]@{
        role = $Role
        pid = [int]$process.Id
        startTicks = [string]$startUtc.Ticks
        startUtc = $startUtc.ToString('o')
        executable = [string]$process.ProcessName
    }
}

function Capture-Launch {
    $current = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $PID)
    if ($null -eq $current -or [int]$current.ParentProcessId -le 0) {
        throw 'Unable to capture final validation parent process'
    }
    $parentId = [int]$current.ParentProcessId
    [ordered]@{
        schema = 1
        phase = $Phase
        scope = [string]$manifest.scope
        drivers = @(
            (Get-ProcessBirth $parentId 'parent'),
            (Get-ProcessBirth ([int]$PID) 'current')
        )
    }
}

function Invoke-ProcessCleanup {
    $launchPath = Join-Path $packetRoot ($Phase + '-process-launch.json')
    $cleanupPath = Join-Path $packetRoot ($Phase + '-process-cleanup.json')
    foreach ($path in @($launchPath,(Join-Path $packetRoot ($Phase + '-timing.json')),
                        (Join-Path $packetRoot ($Phase + '-log.txt')))) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw ('Cannot verify cleanup before phase artifact exists: ' + $path)
        }
    }
    if (Test-Path -LiteralPath $cleanupPath) { throw 'Refusing to overwrite process cleanup artifact' }
    $launch = Read-Json $launchPath
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $rows = @($launch.drivers | ForEach-Object {
        $current = $null
        $queryError = $null
        try {
            $current = Get-Process -Id ([int]$_.pid) -ErrorAction Stop
        } catch {
            if ($_.Exception.Message -notmatch 'cannot find a process|no process') {
                $queryError = $_.Exception.Message
            }
        }
        $observed = if ($null -ne $queryError) { 'UNKNOWN' }
            elseif ($null -eq $current) { 'ABSENT' } else { 'PRESENT' }
        $birth = $false
        $exe = $false
        $probeError = $queryError
        if ($null -ne $current) {
            try {
                $birth = ([string]$current.StartTime.ToUniversalTime().Ticks -ceq [string]$_.startTicks)
                $exe = ([string]$current.ProcessName -ceq [string]$_.executable)
            } catch {
                $probeError = $_.Exception.Message
            }
        }
        $same = $observed -eq 'PRESENT' -and $null -eq $probeError -and $birth -and $exe
        $outcome = if ($observed -eq 'ABSENT') { 'ABSENT' }
            elseif ($null -ne $probeError) { 'UNKNOWN' }
            elseif ($same) { 'STILL_RUNNING' }
            else { 'DIFFERENT_PROCESS' }
        [ordered]@{
            role = [string]$_.role
            pid = [int]$_.pid
            startTicks = [string]$_.startTicks
            executable = [string]$_.executable
            observed = $observed
            birthRevalidated = [bool]$birth
            executableRevalidated = [bool]$exe
            sameCreationStillRunning = [bool]$same
            outcome = $outcome
            probeError = $probeError
        }
    })
    $watch.Stop()
    $failed = @($rows | Where-Object { $_.outcome -in @('STILL_RUNNING','UNKNOWN') }).Count -gt 0
    $result = [ordered]@{
        schema = 1
        phase = $Phase
        status = if ($failed) { 'FAIL' } else { 'PASS' }
        drivers = $rows
        verificationElapsedSeconds = $watch.Elapsed.TotalSeconds
        terminationAttempted = $false
        scope = 'Recorded parent/current births only; no uncertain process is terminated'
    }
    Write-Json $cleanupPath $result 8
    if ($failed) { throw 'Final validation driver creation remains running or could not be birth-revalidated' }
    Write-Output ('PASS recorded final validation ' + $Phase + ' driver births; no termination attempted')
}

function Get-PathVariants([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return @() }
    $trimmed = $Value -replace '[\\/]+$',''
    $values = @($Value,$trimmed,$trimmed.Replace('\','/'),$trimmed.Replace('/','\'))
    return @($values | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique |
        Sort-Object Length -Descending)
}

function Sanitize-TextArtifact([string]$Path, [string]$Token) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return 0 }
    $text = [IO.File]::ReadAllText($Path)
    $count = 0
    foreach ($prefix in (Get-PathVariants $repoRoot)) {
        $escaped = [regex]::Escape($prefix)
        $count += [regex]::Matches($text,$escaped,[Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
        $text = [regex]::Replace($text,$escaped,$Token,[Text.RegularExpressions.RegexOptions]::IgnoreCase)
    }
    foreach ($prefix in (Get-PathVariants $JavaHome)) {
        $escaped = [regex]::Escape($prefix)
        $count += [regex]::Matches($text,$escaped,[Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
        $text = [regex]::Replace($text,$escaped,'[JDK8]',[Text.RegularExpressions.RegexOptions]::IgnoreCase)
    }
    Write-Utf8NoBom $Path $text
    return $count
}

function Sanitize-PhaseOutputs {
    $paths = @((Join-Path $packetRoot ($Phase + '-log.txt')))
    if ($Phase -in @('contracts','matrix')) {
        $paths += (Join-Path $packetRoot ($Phase + '-receipt.txt'))
    }
    $rows = @()
    foreach ($path in $paths) {
        $rows += [ordered]@{file = [IO.Path]::GetFileName($path);replacements = [int](Sanitize-TextArtifact $path '[WORKTREE]')}
    }
    Write-Json (Join-Path $packetRoot ($Phase + '-log-sanitization.json')) ([ordered]@{
        schema = 1
        phase = $Phase
        files = $rows
        allowedPrefixes = @('[WORKTREE]','[JDK8]')
        policy = 'Only the selected worktree and selected JDK prefixes are replaced; operation output is otherwise preserved'
    }) 8
}

function Invoke-Phase {
    Assert-PhaseArtifactsAbsent
    $manifest = Assert-Manifest
    Assert-CandidateSeal
    Assert-AssessmentPrerequisite
    if ($Phase -eq 'matrix') { Assert-PhasePrerequisite 'contracts' }
    if ($Phase -eq 'build') {
        Assert-PhasePrerequisite 'contracts'
        Assert-PhasePrerequisite 'matrix'
    }
    Invoke-CandidateAudit 'before'
    $launchPath = Join-Path $packetRoot ($Phase + '-process-launch.json')
    Write-Json $launchPath (Capture-Launch) 8
    $logPath = Join-Path $packetRoot ($Phase + '-log.txt')
    $receiptPath = Join-Path $packetRoot ($Phase + '-receipt.txt')
    $operationException = $null
    $postAuditException = $null
    $sanitizationException = $null
    $operationExit = 2
    $operationStatus = 'FAIL'
    $entered = $false
    $watch = [Diagnostics.Stopwatch]::StartNew()
    try {
        try {
            Push-Location $repoRoot
            $entered = $true
            if ($Phase -eq 'contracts') {
                $suiteNames = @($manifest.contracts | ForEach-Object { [string]$_.name })
                & $verifyScript -JavaHome $JavaHome -PythonExe $PythonExe -Suite $suiteNames -ReceiptOutputPath $receiptPath *> $logPath
            } elseif ($Phase -eq 'matrix') {
                & $verifyScript -JavaHome $JavaHome -PythonExe $PythonExe -Suite @([string]$manifest.matrix.name) -ReceiptOutputPath $receiptPath *> $logPath
            } else {
                & $buildScript -JavaHome $JavaHome -Style ([string]$manifest.build.style) -Target ([string]$manifest.build.target) -ProcessTimeoutSeconds ([int]$manifest.build.processTimeoutSeconds) *> $logPath
            }
            $operationExit = [int]$LASTEXITCODE
            if ($operationExit -eq 0) { $operationStatus = 'PASS' }
            else { $operationException = [InvalidOperationException]::new('Final validation phase exited with code ' + $operationExit) }
        } catch {
            $operationException = $_.Exception
            $operationExit = 2
        } finally {
            if ($entered) { Pop-Location; $entered = $false }
        }
    } finally {
        $watch.Stop()
        if (-not (Test-Path -LiteralPath $logPath -PathType Leaf)) {
            Write-Utf8NoBom $logPath ('Final validation runner exception: ' +
                $(if ($null -ne $operationException) { $operationException.ToString() } else { 'no operation log' }) + "`n")
        }
        try { Invoke-CandidateAudit 'after' } catch { $postAuditException = $_.Exception }
        try { Sanitize-PhaseOutputs } catch { $sanitizationException = $_.Exception }
    }
    if ($null -eq $operationException -and $null -eq $postAuditException -and $null -eq $sanitizationException) {
        $operationStatus = 'PASS'
        $operationExit = 0
    } else {
        $operationStatus = 'FAIL'
        if ($operationExit -eq 0) { $operationExit = 2 }
    }
    $timing = [ordered]@{
        schema = 1
        status = $operationStatus
        exitCode = $operationExit
        elapsedSeconds = $watch.Elapsed.TotalSeconds
        phase = $Phase
        scope = [string]$manifest.scope
        operation = if ($Phase -eq 'build') { 'scripts/build.ps1' } else { 'scripts/verify-current-contracts.ps1' }
        suiteCount = if ($Phase -eq 'contracts') { 15 } elseif ($Phase -eq 'matrix') { 1 } else { 0 }
        terminalCleanupIncluded = $true
        cleanupDuration = 'NOT_SEPARATELY_MEASURED'
    }
    Write-Json (Join-Path $packetRoot ($Phase + '-timing.json')) $timing 8
    if ($null -ne $operationException) { [Console]::Error.WriteLine($operationException.ToString()) }
    if ($null -ne $postAuditException) { [Console]::Error.WriteLine('Post-phase candidate audit failed: ' + $postAuditException.ToString()) }
    if ($null -ne $sanitizationException) { [Console]::Error.WriteLine('Phase output sanitization failed: ' + $sanitizationException.ToString()) }
    exit $operationExit
}

Assert-Manifest | Out-Null
Assert-CandidateSeal
Assert-AssessmentPrerequisite
if ($VerifyCleanup) {
    Invoke-ProcessCleanup
    exit 0
}
Invoke-Phase

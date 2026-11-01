[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$EvidenceRoot,
    [Parameter(Mandatory = $false)]
    [string]$ExpectedModuleSha256 = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:AssertionCount = 0
$script:ModuleInfo = $null
$script:TaskRoot = $null
$script:ChildProcess = $null
$script:ChildProcessId = 0
$script:ChildStartTicks = ''
$script:RunNonce = [Guid]::NewGuid().ToString('N')
$script:ChildTtlSeconds = 20
$script:StopSignalSent = $false
$script:CimBoundaryResults = New-Object Collections.ArrayList
$script:EmptyFastPathResult = $null
$script:CleanupStatus = 'NOT_RUN'
$script:CleanupFailureCode = ''
$script:PrimaryFailureCode = ''
$script:ParentProcessId = [int]$PID
$script:ParentStartTicks = ''
$script:ParentBaselineRows = @()
$script:ParentExecutableSha256 = ''
$script:Proofs = [ordered]@{
    sourcePinned = $false
    wrongExpectedModulePinRejectedBeforeChild = $false
    invalidParentRejectedAsInfrastructure = $false
    parentBaselineCapturedBeforeModuleImport = $false
    parentBaselineStableBeforeLaunch = $false
    childReadinessAndIdentity = $false
    cimAndNativeParentSetsMatch = $false
    processIdFieldsAreUInt32 = $false
    rawObservationParity = $false
    utcMicrosecondProjection = $false
    exactNativeStartIdentity = $false
    retainedNineTickPredicate = $false
    liveNoLateRejectedActualParentChildSet = $false
    childExitedZero = $false
    currentCimPidAbsent = $false
    outerParentCimBaselineRestoredAfterExit = $false
    outerParentNativeBaselineRestoredAfterExit = $false
    ownedChildCimParentEmptyAfterExit = $false
    ownedChildNativeParentEmptyAfterExit = $false
    noLateProofAcceptedAfterOwnedChildExit = $false
    sourceStableAtEnd = $false
    syntheticCimFailuresRejectedWithoutOutput = $false
    emptyNativeFastPathSkippedCim = $false
    unmockedParityAfterCimOverrides = $false
}

$capturedModuleSha256 = [string]::Empty
$clock = [Diagnostics.Stopwatch]::StartNew()
$utf8 = [Text.UTF8Encoding]::new($false)

function Assert-Contract([bool]$Condition, [string]$FailureCode) {
    if (-not $Condition) {
        throw [InvalidOperationException]::new($FailureCode)
    }
    $script:AssertionCount++
}

function Write-NewTaskFile([string]$Name, [byte[]]$Bytes) {
    if ([String]::IsNullOrWhiteSpace($Name) -or
            [IO.Path]::GetFileName($Name) -cne $Name -or
            $Name.Contains('/') -or $Name.Contains('\')) {
        throw [InvalidOperationException]::new('TASK_FILE_NAME_INVALID')
    }
    $path = Join-Path $script:TaskRoot $Name
    $rootWithSeparator = $script:TaskRoot.TrimEnd('\') + '\'
    $full = [IO.Path]::GetFullPath($path)
    if (-not $full.StartsWith($rootWithSeparator, [StringComparison]::OrdinalIgnoreCase)) {
        throw [InvalidOperationException]::new('TASK_FILE_ESCAPES_OWNED_ROOT')
    }
    $stream = [IO.File]::Open($full, [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $stream.Write($Bytes, 0, $Bytes.Length)
        $stream.Flush()
    } finally {
        $stream.Dispose()
    }
    return $full
}

function Write-NewTaskText([string]$Name, [string]$Text) {
    return Write-NewTaskFile $Name $utf8.GetBytes($Text)
}

function Write-NewTaskJson([string]$Name, $Value) {
    $json = ConvertTo-Json -InputObject $Value -Depth 12
    [void](Write-NewTaskText $Name $json)
}

function Get-SafeFailureCode($ErrorRecord) {
    if ($null -eq $ErrorRecord) { return 'UNKNOWN_FAILURE' }
    if ($ErrorRecord.PSObject.Properties['Exception'] -and $null -ne $ErrorRecord.Exception) {
        $exception = $ErrorRecord.Exception
        if ($exception.Data -and $exception.Data.Contains('VerifierFailureKind') -and
                [string]$exception.Data['VerifierFailureKind'] -ceq 'infrastructure') {
            return 'VERIFIER_INFRASTRUCTURE'
        }
        $message = [string]$exception.Message
        if ($message -ceq 'EXPLICIT_MODULE_SOURCE_PIN_MISMATCH') { return 'EXPLICIT_MODULE_SOURCE_PIN_MISMATCH' }
        if ($message.StartsWith('VERIFIER_INFRASTRUCTURE:', [StringComparison]::OrdinalIgnoreCase)) {
            return 'VERIFIER_INFRASTRUCTURE'
        }
        return $exception.GetType().Name
    }
    return $ErrorRecord.GetType().Name
}

function Test-ModuleInfrastructureError($ErrorRecord) {
    return [bool](& $script:ModuleInfo {
        param($candidateError)
        Test-VerifierInfrastructureError $candidateError
    } $ErrorRecord)
}

function Get-DirectCimChildren([int]$ParentId) {
    return @(Get-CimInstance -ClassName Win32_Process `
        -Filter ('ParentProcessId = {0}' -f $ParentId) -ErrorAction Stop)
}

function Get-DirectCimProcess([int]$ProcessId) {
    return @(Get-CimInstance -ClassName Win32_Process `
        -Filter ('ProcessId = {0}' -f $ProcessId) -ErrorAction Stop)
}

function Get-NativeParentRecords([int]$ParentId) {
    return @(& $script:ModuleInfo {
        param($requestedParentId)
        Get-VerifierBoundReceiptNativeProcessRecordsByParent $requestedParentId
    } $ParentId)
}

function Invoke-NoLateDescendantProof([int]$ParentId, $DescendantRecords = @()) {
    $retained = @($DescendantRecords)
    & $script:ModuleInfo {
        param($requestedParentId, $requestedDescendants)
        Assert-VerifierBoundReceiptNaturalShutdownNoLateDescendants `
            ([pscustomobject]@{ ProcessId = [int]$requestedParentId }) $requestedDescendants
    } $ParentId $retained
}

function Write-ChildStopSignal {
    $stopPath = Join-Path $script:TaskRoot 'child-stop.txt'
    $text = 'stop:' + $script:RunNonce
    [void](Write-NewTaskText 'child-stop.txt' $text)
    $script:StopSignalSent = $true
}

function Get-FileSha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-ExpectedModulePin([string]$ActualSha256, [string]$ExpectedSha256) {
    $actual = [string]$ActualSha256
    if (-not [String]::IsNullOrWhiteSpace($ExpectedSha256) -and
            $actual -cne $ExpectedSha256.ToLowerInvariant()) {
        throw [InvalidOperationException]::new('EXPLICIT_MODULE_SOURCE_PIN_MISMATCH')
    }
    return $actual
}

function ConvertTo-ParentIdentityFact($Record) {
    $path = if ($Record.PSObject.Properties['ExecutablePath']) { [string]$Record.ExecutablePath } else { $null }
    $command = if ($Record.PSObject.Properties['CommandLine']) { [string]$Record.CommandLine } else { $null }
    $creationTicks = $null
    if ($Record.PSObject.Properties['CreationDate'] -and $Record.CreationDate -is [DateTime]) {
        $creationTicks = [string]$Record.CreationDate.ToUniversalTime().Ticks
    }
    $pathFact = Get-TextDigestMetadata $path
    $commandFact = Get-TextDigestMetadata $command
    return [ordered]@{
        processId = [long]$Record.ProcessId
        parentProcessId = [long]$Record.ParentProcessId
        name = [string]$Record.Name
        creationUtcTicks = $creationTicks
        executableLeaf = if ([String]::IsNullOrWhiteSpace($path)) { $null } else { [IO.Path]::GetFileName($path) }
        executablePathLength = $pathFact.characterLength
        executablePathSha256 = $pathFact.sha256
        commandLineLength = $commandFact.characterLength
        commandLineSha256 = $commandFact.sha256
    }
}

function Test-AllowedPrelaunchParentBaseline($Rows, [int]$ParentId) {
    $records = @($Rows)
    if ($records.Count -eq 0) { return $true }
    if ($records.Count -ne 1) { return $false }
    $row = $records[0]
    if ($row.ProcessId.GetType() -ne [uint32] -or
            $row.ParentProcessId.GetType() -ne [uint32] -or
            [int]$row.ProcessId -le 0 -or [int]$row.ParentProcessId -ne $ParentId -or
            [string]$row.Name -ine 'conhost.exe' -or
            [String]::IsNullOrWhiteSpace([string]$row.ExecutablePath)) {
        return $false
    }
    try {
        $expectedPath = [IO.Path]::GetFullPath((Join-Path $env:WINDIR 'System32\conhost.exe'))
        $actualPath = [IO.Path]::GetFullPath([string]$row.ExecutablePath)
        if (-not [String]::Equals($actualPath, $expectedPath, [StringComparison]::OrdinalIgnoreCase) -or
                -not (Test-Path -LiteralPath $expectedPath -PathType Leaf)) { return $false }
        $file = Get-Item -LiteralPath $expectedPath -Force -ErrorAction Stop
        if (([int]$file.Attributes -band [int][IO.FileAttributes]::ReparsePoint) -ne 0 -or
                (Resolve-Path -LiteralPath $expectedPath).Path -ine $expectedPath) { return $false }
    } catch { return $false }
    return $true
}

function Test-DirectCimIdentityUnchanged($Expected, $Current) {
    foreach ($field in @('ProcessId','ParentProcessId','Name','ExecutablePath','CommandLine')) {
        if (-not $Expected.PSObject.Properties[$field] -or -not $Current.PSObject.Properties[$field]) { return $false }
        $left = $Expected.PSObject.Properties[$field].Value
        $right = $Current.PSObject.Properties[$field].Value
        if (($null -eq $left) -ne ($null -eq $right)) { return $false }
        if ($null -ne $left -and ($left.GetType() -ne $right.GetType() -or -not [object]::Equals($left,$right))) { return $false }
    }
    if (-not $Expected.PSObject.Properties['CreationDate'] -or -not $Current.PSObject.Properties['CreationDate'] -or
            $Expected.CreationDate -isnot [DateTime] -or $Current.CreationDate -isnot [DateTime]) { return $false }
    return ($Expected.CreationDate.Kind -eq $Current.CreationDate.Kind -and
        $Expected.CreationDate.ToUniversalTime().Ticks -eq $Current.CreationDate.ToUniversalTime().Ticks)
}

function Test-ParentSetMatchesBaselineAndOwnedChild($BaselineRows, $DirectRows, $NativeRows,
        [int]$ParentId, [int]$OwnedChildId) {
    $baseline = @($BaselineRows)
    $direct = @($DirectRows)
    $native = @($NativeRows)
    $expectedIds = New-Object Collections.ArrayList
    foreach ($row in $baseline) { [void]$expectedIds.Add([int]$row.ProcessId) }
    if ($OwnedChildId -gt 0) { [void]$expectedIds.Add($OwnedChildId) }
    $expectedArray = @($expectedIds.ToArray() | Sort-Object)
    $uniqueExpected = @($expectedArray | Sort-Object -Unique)
    if ($expectedArray.Count -ne $uniqueExpected.Count) { return $false }
    $directIds = @($direct | ForEach-Object { [int]$_.ProcessId } | Sort-Object)
    $nativeIds = @($native | ForEach-Object { [int]$_.ProcessId } | Sort-Object)
    $expectedText = [String]::Join(',', [string[]]@($expectedArray | ForEach-Object { [string]$_ }))
    $directText = [String]::Join(',', [string[]]@($directIds | ForEach-Object { [string]$_ }))
    $nativeText = [String]::Join(',', [string[]]@($nativeIds | ForEach-Object { [string]$_ }))
    if ($direct.Count -ne $expectedArray.Count -or $native.Count -ne $expectedArray.Count -or
            $directText -cne $expectedText -or $nativeText -cne $expectedText) { return $false }
    foreach ($baselineRow in $baseline) {
        $baselineProcessId = [int]$baselineRow.ProcessId
        $directMatch = @($direct | Where-Object { [int]$_.ProcessId -eq $baselineProcessId })
        $nativeMatch = @($native | Where-Object { [int]$_.ProcessId -eq $baselineProcessId })
        if ($directMatch.Count -ne 1 -or $nativeMatch.Count -ne 1 -or
                -not (Test-DirectCimIdentityUnchanged $baselineRow $directMatch[0])) { return $false }
        $nativeEquivalent = [bool](& $script:ModuleInfo {
            param($expected, $current)
            Test-VerifierBoundReceiptObservationRawEquivalent $expected $current
        } $baselineRow $nativeMatch[0])
        if (-not $nativeEquivalent) { return $false }
    }
    if ($OwnedChildId -gt 0) {
        $directMatch = @($direct | Where-Object { [int]$_.ProcessId -eq $OwnedChildId })
        $nativeMatch = @($native | Where-Object { [int]$_.ProcessId -eq $OwnedChildId })
        if ($directMatch.Count -ne 1 -or $nativeMatch.Count -ne 1 -or
                [int]$directMatch[0].ParentProcessId -ne $ParentId -or
                [int]$nativeMatch[0].ParentProcessId -ne $ParentId) { return $false }
        $nativeEquivalent = [bool](& $script:ModuleInfo {
            param($expected, $current)
            Test-VerifierBoundReceiptObservationRawEquivalent $expected $current
        } $directMatch[0] $nativeMatch[0])
        if (-not $nativeEquivalent) { return $false }
    }
    return $true
}

function Get-TextDigestMetadata($Value) {
    if ($null -eq $Value) {
        return [ordered]@{ isNull = $true; characterLength = 0; utf8ByteLength = 0; sha256 = $null }
    }
    $text = [string]$Value
    $bytes = $utf8.GetBytes($text)
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha.ComputeHash($bytes)
        $hex = ([BitConverter]::ToString($digest)).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
    return [ordered]@{
        isNull = $false
        characterLength = $text.Length
        utf8ByteLength = $bytes.Length
        sha256 = $hex
    }
}

function Test-ExactNoMatchCimError($ErrorRecord) {
    if ($null -eq $ErrorRecord -or -not $ErrorRecord.PSObject.Properties['Exception']) { return $false }
    return ([string]$ErrorRecord.Exception.Message -like 'No matching MSFT_NetTCPConnection objects found by CIM query*')
}

function Get-CimBoundaryHelperScriptBlock {
    return (& $script:ModuleInfo {
        (Get-Command -Name 'Get-VerifierBoundReceiptCimProcessRecordsByParent' `
            -CommandType Function -ErrorAction Stop).ScriptBlock
    })
}

function Set-CimBoundaryHelperScriptBlock([scriptblock]$Replacement) {
    & $script:ModuleInfo {
        param($body)
        Set-Item -Path 'Function:\Get-VerifierBoundReceiptCimProcessRecordsByParent' `
            -Value $body -Force | Out-Null
    } $Replacement
}

function New-CimBoundaryOverride([string]$Mode, $Rows, $Counter) {
    $counterRef = $Counter
    if ($Mode -ceq 'THROW') {
        return ({
            param($ParentId)
            $counterRef.count++
            $synthetic = [InvalidOperationException]::new('SYNTHETIC_CIM_PROVIDER_FAILURE')
            $synthetic.Data['VerifierFailureKind'] = 'infrastructure'
            throw $synthetic
        }).GetNewClosure()
    }
    if ($Mode -ceq 'EMPTY') {
        return ({
            param($ParentId)
            $counterRef.count++
            return
        }).GetNewClosure()
    }
    if ($Mode -cne 'ROWS') {
        throw [InvalidOperationException]::new('SYNTHETIC_CIM_OVERRIDE_MODE_INVALID')
    }
    $replyRows = $Rows
    return ({
        param($ParentId)
        $counterRef.count++
        return @($replyRows)
    }).GetNewClosure()
}

function Invoke-NativeParentCapture([int]$ParentId) {
    $sink = New-Object Collections.ArrayList
    $caught = $null
    try {
        & $script:ModuleInfo {
            param($requestedParentId, $recordSink)
            Get-VerifierBoundReceiptNativeProcessRecordsByParent $requestedParentId |
                ForEach-Object { [void]$recordSink.Add($_) }
        } $ParentId $sink
    } catch {
        $caught = $_
    }
    $outputTypes = @($sink | ForEach-Object { $_.GetType().FullName })
    $infrastructure = $false
    $safeFailureCode = $null
    $diagnosticMatched = $false
    if ($null -ne $caught) {
        $infrastructure = Test-ModuleInfrastructureError $caught
        $safeFailureCode = Get-SafeFailureCode $caught
        if ($caught.PSObject.Properties['Exception'] -and
                -not [String]::IsNullOrEmpty($script:ExpectedCimDiagnosticFragment)) {
            $diagnosticMatched = ([string]$caught.Exception.Message).IndexOf(
                $script:ExpectedCimDiagnosticFragment, [StringComparison]::Ordinal) -ge 0
        }
    }
    return [pscustomobject][ordered]@{
        outputCount = $sink.Count
        outputTypeNames = $outputTypes
        caught = ($null -ne $caught)
        infrastructure = $infrastructure
        safeFailureCode = $safeFailureCode
        expectedDiagnosticMatched = $diagnosticMatched
    }
}

function New-DirectCimBoundaryClone($Source, [switch]$IncludeCreationDate) {
    $row = [ordered]@{
        ProcessId = $Source.ProcessId
        ParentProcessId = $Source.ParentProcessId
        Name = $Source.Name
        ExecutablePath = $Source.ExecutablePath
        CommandLine = $Source.CommandLine
    }
    if ($IncludeCreationDate) { $row['CreationDate'] = $Source.CreationDate }
    return [pscustomobject]$row
}

function Invoke-SyntheticCimBoundaryCase([string]$CaseId, [string]$Mode,
        $Rows, [string]$ExpectedDiagnosticId, [string]$ExpectedDiagnosticFragment,
        [int]$ParentId) {
    $counter = [pscustomobject]@{ count = 0 }
    $script:ExpectedCimDiagnosticFragment = $ExpectedDiagnosticFragment
    $original = Get-CimBoundaryHelperScriptBlock
    $override = New-CimBoundaryOverride $Mode $Rows $counter
    $capture = $null
    $restored = $false
    try {
        Set-CimBoundaryHelperScriptBlock $override
        $capture = Invoke-NativeParentCapture $ParentId
    } finally {
        Set-CimBoundaryHelperScriptBlock $original
        $restoredText = (Get-CimBoundaryHelperScriptBlock).ToString()
        $restored = ($restoredText -ceq $original.ToString())
    }
    $passed = ($counter.count -eq 1 -and $null -ne $capture -and
        $capture.caught -and $capture.infrastructure -and
        $capture.outputCount -eq 0 -and $capture.expectedDiagnosticMatched -and $restored)
    return [pscustomobject][ordered]@{
        caseId = $CaseId
        synthetic = $true
        phase = 'nonempty-native-parent-cim-correlation'
        expectedDiagnosticId = $ExpectedDiagnosticId
        helperCallCount = [int]$counter.count
        caught = [bool]($null -ne $capture -and $capture.caught)
        infrastructure = [bool]($null -ne $capture -and $capture.infrastructure)
        safeFailureCode = if ($null -ne $capture) { $capture.safeFailureCode } else { $null }
        expectedDiagnosticMatched = [bool]($null -ne $capture -and $capture.expectedDiagnosticMatched)
        streamedRecordCount = if ($null -ne $capture) { [int]$capture.outputCount } else { -1 }
        streamedRecordTypeNames = if ($null -ne $capture) { @($capture.outputTypeNames) } else { @() }
        helperRestored = $restored
        status = if ($passed) { 'PASS_EXPECTED_INFRA_REJECTION_ZERO_OUTPUT' } else { 'FAIL' }
    }
}

function Invoke-EmptyCimFastPathProbe([int]$ParentId) {
    $counter = [pscustomobject]@{ count = 0 }
    $script:ExpectedCimDiagnosticFragment = 'SYNTHETIC_EMPTY_PARENT_FAST_PATH_SHOULD_NOT_QUERY_CIM'
    $original = Get-CimBoundaryHelperScriptBlock
    $override = New-CimBoundaryOverride 'THROW' @() $counter
    $capture = $null
    $restored = $false
    try {
        Set-CimBoundaryHelperScriptBlock $override
        $capture = Invoke-NativeParentCapture $ParentId
    } finally {
        Set-CimBoundaryHelperScriptBlock $original
        $restored = ((Get-CimBoundaryHelperScriptBlock).ToString() -ceq $original.ToString())
    }
    $passed = ($counter.count -eq 0 -and $null -ne $capture -and
        -not $capture.caught -and $capture.outputCount -eq 0 -and $restored)
    return [pscustomobject][ordered]@{
        synthetic = $true
        phase = 'fresh-empty-native-parent-fast-path-after-child-exit'
        helperCallCount = [int]$counter.count
        caught = [bool]($null -ne $capture -and $capture.caught)
        infrastructure = [bool]($null -ne $capture -and $capture.infrastructure)
        streamedRecordCount = if ($null -ne $capture) { [int]$capture.outputCount } else { -1 }
        helperRestored = $restored
        status = if ($passed) { 'PASS_CIM_HELPER_NOT_CALLED' } else { 'FAIL' }
    }
}
$modulePath = $null
$moduleShaAtStart = ''
$moduleShaAtEnd = ''
$taskRootCreated = $false
$childScriptText = @'
param(
    [Parameter(Mandatory = $true)][string]$ReadyFileName,
    [Parameter(Mandatory = $true)][string]$StopFileName,
    [Parameter(Mandatory = $true)][int]$ExpectedParentProcessId,
    [Parameter(Mandatory = $true)][string]$RunNonce,
    [Parameter(Mandatory = $true)][int]$TtlSeconds
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($PSScriptRoot)
$readyPath = [IO.Path]::GetFullPath((Join-Path $root $ReadyFileName))
$stopPath = [IO.Path]::GetFullPath((Join-Path $root $StopFileName))
if (-not $readyPath.StartsWith($root.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase) -or
        -not $stopPath.StartsWith($root.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    exit 21
}
$process = [Diagnostics.Process]::GetCurrentProcess()
try {
    $payload = [ordered]@{
        processId = [int]$PID
        parentProcessId = [int]$ExpectedParentProcessId
        startTicks = [string]$process.StartTime.ToUniversalTime().Ticks
        executableName = 'powershell.exe'
        runNonce = $RunNonce
    }
    $json = ConvertTo-Json -InputObject $payload -Compress -Depth 4
    [IO.File]::WriteAllText($readyPath, $json, [Text.UTF8Encoding]::new($false))
} finally {
    $process.Dispose()
}
$deadline = [DateTime]::UtcNow.AddSeconds($TtlSeconds)
while ([DateTime]::UtcNow -lt $deadline) {
    if (Test-Path -LiteralPath $stopPath -PathType Leaf) {
        $stopText = [IO.File]::ReadAllText($stopPath, [Text.Encoding]::UTF8)
        if ($stopText -ceq ('stop:' + $RunNonce)) { exit 0 }
        exit 22
    }
    Start-Sleep -Milliseconds 50
}
exit 23
'@

try {
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
    $root = [IO.Path]::GetFullPath($EvidenceRoot).TrimEnd('\')
    $tempPrefix = $tempRoot + '\'
    if (-not $root.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetFileName($root) -cne 'maintained-parent-canary2') {
        throw [InvalidOperationException]::new('EVIDENCE_ROOT_OUTSIDE_ASSIGNED_TEMP_LEAF')
    }
    $tempItem = Get-Item -LiteralPath $tempRoot -Force -ErrorAction Stop
    if (-not $tempItem.PSIsContainer -or
            ([int]$tempItem.Attributes -band [int][IO.FileAttributes]::ReparsePoint) -ne 0 -or
            (Resolve-Path -LiteralPath $tempRoot).Path -cne $tempRoot) {
        throw [InvalidOperationException]::new('TEMP_ROOT_IDENTITY_NOT_PROVEN')
    }
    $parentPath = Split-Path -Parent $root
    if (-not $parentPath.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw [InvalidOperationException]::new('EVIDENCE_PARENT_OUTSIDE_TEMP_ROOT')
    }
    $relativeParent = $parentPath.Substring($tempPrefix.Length)
    $cursor = $tempRoot
    foreach ($segment in @($relativeParent.Split([char[]]@('\'), [StringSplitOptions]::RemoveEmptyEntries))) {
        $cursor = Join-Path $cursor $segment
        $segmentItem = Get-Item -LiteralPath $cursor -Force -ErrorAction Stop
        if (-not $segmentItem.PSIsContainer -or
                ([int]$segmentItem.Attributes -band [int][IO.FileAttributes]::ReparsePoint) -ne 0 -or
                (Resolve-Path -LiteralPath $cursor).Path -cne $cursor) {
            throw [InvalidOperationException]::new('EVIDENCE_PARENT_REPARSE_OR_IDENTITY_FAILURE')
        }
    }
    $repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
    $modulePath = Join-Path $repoRoot 'scripts\VerifierIsolation.psm1'
    if (-not (Test-Path -LiteralPath $modulePath -PathType Leaf)) {
        throw [InvalidOperationException]::new('MODULE_SOURCE_MISSING_BEFORE_EVIDENCE_OWNER_CHECK')
    }
    $moduleShaAtStart = Get-FileSha256 $modulePath
    $wrongExpectedPin = if ($moduleShaAtStart[0] -ceq '0') { '1' + $moduleShaAtStart.Substring(1) } else { '0' + $moduleShaAtStart.Substring(1) }
    $wrongPinRejected = $false
    try {
        [void](Assert-ExpectedModulePin $moduleShaAtStart $wrongExpectedPin)
    } catch {
        $wrongPinRejected = ([string]$_.Exception.Message -ceq 'EXPLICIT_MODULE_SOURCE_PIN_MISMATCH')
    }
    Assert-Contract $wrongPinRejected 'WRONG_EXPECTED_MODULE_PIN_NOT_REJECTED'
    $script:Proofs.wrongExpectedModulePinRejectedBeforeChild = $true
    $capturedModuleSha256 = Assert-ExpectedModulePin $moduleShaAtStart $ExpectedModuleSha256
    if (Test-Path -LiteralPath $root) {
        $rootItem = Get-Item -LiteralPath $root -Force -ErrorAction Stop
        if (-not $rootItem.PSIsContainer -or
                ([int]$rootItem.Attributes -band [int][IO.FileAttributes]::ReparsePoint) -ne 0 -or
                (Resolve-Path -LiteralPath $root).Path -cne $root) {
            throw [InvalidOperationException]::new('EXISTING_EVIDENCE_ROOT_IDENTITY_FAILURE')
        }
    } else {
        $null = New-Item -ItemType Directory -Path $root -ErrorAction Stop
    }
    $script:TaskRoot = $root
    $ownerMarkerPath = Join-Path $root '.owned-root.json'
    if (Test-Path -LiteralPath $ownerMarkerPath) {
        $ownerItem = Get-Item -LiteralPath $ownerMarkerPath -Force -ErrorAction Stop
        if ($ownerItem.PSIsContainer -or
                ([int]$ownerItem.Attributes -band [int][IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw [InvalidOperationException]::new('EVIDENCE_OWNER_MARKER_NOT_REGULAR_FILE')
        }
        $ownerMarker = Get-Content -LiteralPath $ownerMarkerPath -Raw | ConvertFrom-Json
        if ([string]$ownerMarker.schema -cne 'browser-bound-receipt-cim-errors-evidence-root-v2' -or
                [string]$ownerMarker.moduleSha256 -cne $capturedModuleSha256) {
            throw [InvalidOperationException]::new('EVIDENCE_OWNER_MARKER_MISMATCH')
        }
    } else {
        $entries = @(Get-ChildItem -LiteralPath $root -Force)
        if ($entries.Count -gt 0) {
            $priorResultPath = Join-Path $root 'result.json'
            if (-not (Test-Path -LiteralPath $priorResultPath -PathType Leaf)) {
                throw [InvalidOperationException]::new('UNOWNED_CONTENT_IN_EVIDENCE_ROOT')
            }
            $priorResultItem = Get-Item -LiteralPath $priorResultPath -Force
            if (([int]$priorResultItem.Attributes -band [int][IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw [InvalidOperationException]::new('PRIOR_RESULT_IS_REPARSE_POINT')
            }
            $priorResult = Get-Content -LiteralPath $priorResultPath -Raw | ConvertFrom-Json
            if ([string]$priorResult.schema -cne 'browser-bound-receipt-cim-errors-contract-v2' -or
                    [string]$priorResult.moduleSha256 -cne $capturedModuleSha256 -or
                    -not [bool]$priorResult.scratchRootIdentityVerified -or
                    $null -eq $priorResult.scratchFiles) {
                throw [InvalidOperationException]::new('PRIOR_EVIDENCE_NOT_OWNED_BY_THIS_CONTRACT')
            }
            $expectedNames = @(@($priorResult.scratchFiles | ForEach-Object { [string]$_.name }) + 'result.json' | Sort-Object)
            $actualNames = @($entries | ForEach-Object { [string]$_.Name } | Sort-Object)
            $nameDifferences = @(Compare-Object -ReferenceObject $expectedNames `
                -DifferenceObject $actualNames -CaseSensitive)
            if ($expectedNames.Count -ne $actualNames.Count -or
                    $nameDifferences.Count -ne 0) {
                throw [InvalidOperationException]::new('PRIOR_EVIDENCE_FILE_SET_MISMATCH')
            }
            foreach ($row in @($priorResult.scratchFiles)) {
                $priorPath = Join-Path $root ([string]$row.name)
                $priorItem = Get-Item -LiteralPath $priorPath -Force -ErrorAction Stop
                if ($priorItem.PSIsContainer -or
                        ([int]$priorItem.Attributes -band [int][IO.FileAttributes]::ReparsePoint) -ne 0 -or
                        [long]$priorItem.Length -ne [long]$row.bytes -or
                        (Get-FileSha256 $priorPath) -cne [string]$row.sha256) {
                    throw [InvalidOperationException]::new('PRIOR_EVIDENCE_FILE_HASH_OR_TYPE_MISMATCH')
                }
            }
        }
        Write-NewTaskJson '.owned-root.json' ([ordered]@{
            schema = 'browser-bound-receipt-cim-errors-evidence-root-v2'
            moduleSha256 = $capturedModuleSha256
            taskLeaf = 'maintained-parent-canary2'
        })
    }
    $taskAttemptName = 'attempt-' + $script:RunNonce
    $attemptPath = Join-Path $root $taskAttemptName
    if (Test-Path -LiteralPath $attemptPath) {
        throw [InvalidOperationException]::new('TASK_ATTEMPT_ALREADY_EXISTS')
    }
    $attemptItem = New-Item -ItemType Directory -Path $attemptPath -ErrorAction Stop
    $taskRootCreated = $true
    $script:TaskRoot = (Resolve-Path -LiteralPath $attemptPath).Path
    if ($script:TaskRoot -cne $attemptPath -or
            ([int]$attemptItem.Attributes -band [int][IO.FileAttributes]::ReparsePoint) -ne 0 -or
            @(Get-ChildItem -LiteralPath $script:TaskRoot -Force).Count -ne 0) {
        throw [InvalidOperationException]::new('CREATED_ATTEMPT_ROOT_IDENTITY_FAILURE')
    }

    $repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
    $modulePath = Join-Path $repoRoot 'scripts\VerifierIsolation.psm1'
    $moduleShaAtStart = Get-FileSha256 $modulePath
    Assert-Contract ($moduleShaAtStart -ceq $capturedModuleSha256) 'MODULE_HASH_MISMATCH'
    $script:Proofs.sourcePinned = $true

    $parentProcess = Get-Process -Id $script:ParentProcessId -ErrorAction Stop
    try { $script:ParentStartTicks = [string]$parentProcess.StartTime.ToUniversalTime().Ticks }
    finally { $parentProcess.Dispose() }
    $parentCimBeforeModule = @(Get-DirectCimProcess $script:ParentProcessId)
    Assert-Contract ($parentCimBeforeModule.Count -eq 1 -and
        [int]$parentCimBeforeModule[0].ProcessId -eq $script:ParentProcessId -and
        [string]$parentCimBeforeModule[0].Name -ieq 'powershell.exe') 'RUNNER_PROCESS_IDENTITY_NOT_PROVEN_BEFORE_MODULE_IMPORT'
    $expectedParentExePath = [IO.Path]::GetFullPath((Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'))
    $actualParentExePath = [IO.Path]::GetFullPath([string]$parentCimBeforeModule[0].ExecutablePath)
    Assert-Contract ([String]::Equals($actualParentExePath,$expectedParentExePath,[StringComparison]::OrdinalIgnoreCase) -and
        (Test-Path -LiteralPath $expectedParentExePath -PathType Leaf)) 'RUNNER_EXECUTABLE_IDENTITY_NOT_PROVEN_BEFORE_MODULE_IMPORT'
    $script:ParentExecutableSha256 = Get-FileSha256 $expectedParentExePath
    $script:ParentBaselineRows = @(Get-DirectCimChildren $script:ParentProcessId)
    $baselineAllowed = Test-AllowedPrelaunchParentBaseline $script:ParentBaselineRows $script:ParentProcessId
    Assert-Contract $baselineAllowed 'UNEXPECTED_PRELAUNCH_PARENT_CHILD_BASELINE'
    $script:Proofs.parentBaselineCapturedBeforeModuleImport = $true
    Write-NewTaskJson 'parent-baseline-before-module.json' ([ordered]@{
        schema = 'browser-bound-receipt-parent-baseline-v2'
        runnerProcessId = $script:ParentProcessId
        runnerStartTicks = $script:ParentStartTicks
        runnerExecutableName = [IO.Path]::GetFileName($actualParentExePath)
        runnerExecutableSha256 = $script:ParentExecutableSha256
        baselineCount = $script:ParentBaselineRows.Count
        baselineHasOnlyKnownConsoleHost = ($script:ParentBaselineRows.Count -eq 1)
        baselineProcesses = @($script:ParentBaselineRows | ForEach-Object { ConvertTo-ParentIdentityFact $_ })
    })

    $script:ModuleInfo = Import-Module -Name $modulePath -Force -PassThru -DisableNameChecking
    [void](& $script:ModuleInfo { Initialize-VerifierBoundReceiptNativeParentCensus })

    $invalidParentRejected = $false
    $invalidParentInfrastructure = $false
    try {
        [void]@(Get-NativeParentRecords 0)
    } catch {
        $invalidParentInfrastructure = Test-ModuleInfrastructureError $_
        $invalidParentRejected = $invalidParentInfrastructure
    }
    Assert-Contract $invalidParentRejected 'INVALID_PARENT_ZERO_NOT_REJECTED_AS_INFRASTRUCTURE'
    $script:Proofs.invalidParentRejectedAsInfrastructure = $true

    $baselineChildrenCim = @(Get-DirectCimChildren $script:ParentProcessId)
    $baselineChildrenNative = @(Get-NativeParentRecords $script:ParentProcessId)
    $baselineStableBeforeLaunch = Test-ParentSetMatchesBaselineAndOwnedChild `
        $script:ParentBaselineRows $baselineChildrenCim $baselineChildrenNative $script:ParentProcessId 0
    Assert-Contract $baselineStableBeforeLaunch 'PARENT_BASELINE_CHANGED_BEFORE_OWNED_CHILD_LAUNCH'
    $script:Proofs.parentBaselineStableBeforeLaunch = $true
    Write-NewTaskJson 'parent-baseline-before-child-launch.json' ([ordered]@{
        schema = 'browser-bound-receipt-parent-baseline-v2'
        directCimCount = $baselineChildrenCim.Count
        nativeCount = $baselineChildrenNative.Count
        baselineCount = $script:ParentBaselineRows.Count
        baselineStable = $baselineStableBeforeLaunch
        baselineProcesses = @($script:ParentBaselineRows | ForEach-Object { ConvertTo-ParentIdentityFact $_ })
    })

    $parentProcess = Get-Process -Id $script:ParentProcessId -ErrorAction Stop
    try { $parentStartTicksNow = [string]$parentProcess.StartTime.ToUniversalTime().Ticks }
    finally { $parentProcess.Dispose() }
    Assert-Contract ($parentStartTicksNow -ceq $script:ParentStartTicks) 'RUNNER_PROCESS_START_IDENTITY_CHANGED_BEFORE_CHILD_LAUNCH'
    $parentStartTicks = $script:ParentStartTicks
    $parentCim = @(Get-DirectCimProcess $script:ParentProcessId)
    Assert-Contract ($parentCim.Count -eq 1) 'CURRENT_POWERSHELL_EXACT_CIM_IDENTITY_MISSING'
    Assert-Contract ([string]$parentCim[0].Name -ieq 'powershell.exe' -and
        [IO.Path]::GetFullPath([string]$parentCim[0].ExecutablePath) -ieq $expectedParentExePath) 'TEST_HOST_EXECUTABLE_NOT_WINDOWS_POWERSHELL'

    $childScriptPath = Join-Path $script:TaskRoot 'child.ps1'
    [void](Write-NewTaskText 'child.ps1' $childScriptText)
    $childScriptSha256 = Get-FileSha256 $childScriptPath
    $childReadyPath = Join-Path $script:TaskRoot 'child-ready.json'
    $childStopPath = Join-Path $script:TaskRoot 'child-stop.txt'
    $powershellExe = Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'
    if (-not (Test-Path -LiteralPath $powershellExe -PathType Leaf) -or
            $childScriptPath.Contains('"') -or $script:TaskRoot.Contains('"')) {
        throw [InvalidOperationException]::new('CHILD_LAUNCH_ARGUMENTS_NOT_SAFE')
    }
    $powershellExeSha256 = Get-FileSha256 $powershellExe
    if ((Test-Path -LiteralPath $childReadyPath) -or (Test-Path -LiteralPath $childStopPath)) {
        throw [InvalidOperationException]::new('CHILD_SIGNAL_PATH_ALREADY_EXISTS')
    }
    $arguments = '-NoProfile -ExecutionPolicy Bypass -File "{0}" "child-ready.json" "child-stop.txt" {1} "{2}" {3}' -f `
        $childScriptPath, $script:ParentProcessId, $script:RunNonce, $script:ChildTtlSeconds
    $script:ChildProcess = Start-Process -FilePath $powershellExe -ArgumentList $arguments `
        -WorkingDirectory $script:TaskRoot -PassThru -WindowStyle Hidden
    $script:ChildProcess.Refresh()
    $script:ChildProcessId = [int]$script:ChildProcess.Id
    $script:ChildStartTicks = [string]$script:ChildProcess.StartTime.ToUniversalTime().Ticks
    $launchRecord = [ordered]@{
        childProcessId = $script:ChildProcessId
        childStartTicks = $script:ChildStartTicks
        childExecutableName = 'powershell.exe'
        childExecutableSha256 = $powershellExeSha256
        childScript = 'child.ps1'
        childScriptSha256 = $childScriptSha256
        readinessFile = 'child-ready.json'
        stopFile = 'child-stop.txt'
        childTtlSeconds = $script:ChildTtlSeconds
        parentProcessId = $script:ParentProcessId
        parentProcessStartTicks = $parentStartTicks
        parentExecutableName = 'powershell.exe'
        runNonce = $script:RunNonce
        moduleSha256 = $moduleShaAtStart
    }
    Write-NewTaskJson 'launch.json' $launchRecord

    $readyDeadline = [DateTime]::UtcNow.AddSeconds(6)
    while (-not (Test-Path -LiteralPath $childReadyPath -PathType Leaf) -and
            [DateTime]::UtcNow -lt $readyDeadline) {
        if ($script:ChildProcess.HasExited) { break }
        Start-Sleep -Milliseconds 50
    }
    Assert-Contract (Test-Path -LiteralPath $childReadyPath -PathType Leaf) 'CHILD_READINESS_FILE_NOT_CREATED'
    $ready = Get-Content -LiteralPath $childReadyPath -Raw | ConvertFrom-Json
    Assert-Contract ([int]$ready.processId -eq $script:ChildProcessId -and
        [int]$ready.parentProcessId -eq $script:ParentProcessId -and
        [string]$ready.startTicks -ceq $script:ChildStartTicks -and
        [string]$ready.executableName -ceq 'powershell.exe' -and
        [string]$ready.runNonce -ceq $script:RunNonce) 'CHILD_READINESS_IDENTITY_MISMATCH'

    $childCim = @(Get-DirectCimProcess $script:ChildProcessId)
    Assert-Contract ($childCim.Count -eq 1) 'EXACT_CHILD_CIM_PROCESS_NOT_FOUND'
    $childExecutablePathMatches = ([string]$childCim[0].Name -ieq 'powershell.exe' -and
        [IO.Path]::GetFileName([string]$childCim[0].ExecutablePath) -ieq 'powershell.exe' -and
        [IO.Path]::GetFullPath([string]$childCim[0].ExecutablePath).Equals(
            [IO.Path]::GetFullPath($powershellExe), [StringComparison]::OrdinalIgnoreCase))
    Assert-Contract $childExecutablePathMatches `
        'CHILD_EXECUTABLE_IDENTITY_MISMATCH'
    $childStartFromCimHandle = [string]$script:ChildProcess.StartTime.ToUniversalTime().Ticks
    Assert-Contract ($childStartFromCimHandle -ceq $script:ChildStartTicks) 'RETAINED_PROCESS_START_TICKS_CHANGED'

    $cimRecords = @(Get-DirectCimChildren $script:ParentProcessId)
    $nativeRecords = @(Get-NativeParentRecords $script:ParentProcessId)
    $activeParentSetMatches = Test-ParentSetMatchesBaselineAndOwnedChild `
        $script:ParentBaselineRows $cimRecords $nativeRecords $script:ParentProcessId $script:ChildProcessId
    $childCimRows = @($cimRecords | Where-Object { [int]$_.ProcessId -eq $script:ChildProcessId })
    $childNativeRows = @($nativeRecords | Where-Object { [int]$_.ProcessId -eq $script:ChildProcessId })
    Assert-Contract ($activeParentSetMatches -and $childCimRows.Count -eq 1 -and $childNativeRows.Count -eq 1) `
        'CIM_NATIVE_PARENT_SET_IDENTITY_MISMATCH'
    $childCimRecord = $childCimRows[0]
    $childNativeRecord = $childNativeRows[0]
    $script:Proofs.cimAndNativeParentSetsMatch = $true
    $script:Proofs.childReadinessAndIdentity = $true

    $cimPidType = $childCimRecord.ProcessId.GetType().FullName
    $cimParentType = $childCimRecord.ParentProcessId.GetType().FullName
    $nativePidType = $childNativeRecord.ProcessId.GetType().FullName
    $nativeParentType = $childNativeRecord.ParentProcessId.GetType().FullName
    Assert-Contract ($cimPidType -ceq 'System.UInt32' -and $cimParentType -ceq 'System.UInt32' -and
        $nativePidType -ceq 'System.UInt32' -and $nativeParentType -ceq 'System.UInt32') `
        'PARENT_PROVIDER_PID_FIELDS_NOT_UINT32'
    $script:Proofs.processIdFieldsAreUInt32 = $true

    $nativeRecord = $childNativeRecord
    $nativeCreationTicks = [long]$nativeRecord.NativeCreationTicks
    $creationRemainder = [long]$nativeRecord.NativeCreationRemainderTicks
    $providerCimTicks = [long]$nativeRecord.CreationDate.Ticks
    $directCimUtcTicks = [long]$childCimRecord.CreationDate.ToUniversalTime().Ticks
    $rawFieldDiagnostics = [ordered]@{}
    foreach ($fieldName in @('ProcessId','ParentProcessId','Name','ExecutablePath','CommandLine')) {
        $directValue = $childCimRecord.PSObject.Properties[$fieldName].Value
        $nativeValue = $nativeRecord.PSObject.Properties[$fieldName].Value
        $fieldRow = [ordered]@{
            equal = [object]::Equals($directValue, $nativeValue)
            directType = if ($null -eq $directValue) { $null } else { $directValue.GetType().FullName }
            nativeType = if ($null -eq $nativeValue) { $null } else { $nativeValue.GetType().FullName }
        }
        if ($fieldName -in @('Name','ExecutablePath','CommandLine')) {
            $fieldRow['directText'] = Get-TextDigestMetadata $directValue
            $fieldRow['nativeText'] = Get-TextDigestMetadata $nativeValue
        }
        if ($fieldName -ceq 'ExecutablePath') {
            $caseOnlyDifferenceCount = 0
            $otherDifferenceCount = 0
            if ($null -ne $directValue -and $null -ne $nativeValue -and
                    ([string]$directValue).Length -eq ([string]$nativeValue).Length) {
                for ($index = 0; $index -lt ([string]$directValue).Length; $index++) {
                    $directChar = ([string]$directValue)[$index]
                    $nativeChar = ([string]$nativeValue)[$index]
                    if ($directChar -cne $nativeChar) {
                        if ([Char]::ToUpperInvariant($directChar) -ceq [Char]::ToUpperInvariant($nativeChar)) {
                            $caseOnlyDifferenceCount++
                        } else {
                            $otherDifferenceCount++
                        }
                    }
                }
            }
            $fieldRow['ordinalIgnoreCaseEqual'] = ($null -ne $directValue -and
                [String]::Equals([string]$directValue, [string]$nativeValue,
                    [StringComparison]::OrdinalIgnoreCase))
            $fieldRow['caseOnlyDifferenceCount'] = $caseOnlyDifferenceCount
            $fieldRow['otherDifferenceCount'] = $otherDifferenceCount
            $fieldRow['directMatchesLaunchedExecutable'] = [String]::Equals(
                [string]$directValue, $powershellExe, [StringComparison]::OrdinalIgnoreCase)
            $fieldRow['nativeMatchesLaunchedExecutable'] = [String]::Equals(
                [string]$nativeValue, $powershellExe, [StringComparison]::OrdinalIgnoreCase)
        }
        $rawFieldDiagnostics[$fieldName] = $fieldRow
    }
    $creationTicksEqual = ($directCimUtcTicks -eq $providerCimTicks)
    $utcProjection = ($nativeRecord.CreationDate.GetType() -eq [DateTime] -and
        $nativeRecord.CreationDate.Kind -eq [DateTimeKind]::Utc -and
        $creationRemainder -ge 0 -and $creationRemainder -le 9 -and
        ($providerCimTicks % 10L) -eq 0 -and
        $providerCimTicks -eq ($nativeCreationTicks - $creationRemainder) -and
        $creationTicksEqual)
    $rawEquivalent = [bool](& $script:ModuleInfo {
        param($expected, $current)
        Test-VerifierBoundReceiptObservationRawEquivalent $expected $current
    } $childCimRecord $childNativeRecord)
    $script:AssertionCount++
    if ($rawEquivalent) {
        $script:Proofs.rawObservationParity = $true
    } else {
        if ($script:PrimaryFailureCode -eq '') {
            $script:PrimaryFailureCode = 'DIRECT_CIM_AND_NATIVE_RAW_OBSERVATIONS_DIVERGED'
        }
    }
    $script:AssertionCount++
    if ($utcProjection) {
        $script:Proofs.utcMicrosecondProjection = $true
    } else {
        if ($script:PrimaryFailureCode -eq '') {
            $script:PrimaryFailureCode = 'UTC_MICROSECOND_CREATION_PROJECTION_MISMATCH'
        }
    }
    Assert-Contract ($nativeCreationTicks -ceq [long]$script:ChildStartTicks) `
        'NATIVE_CREATION_TICKS_NOT_EXACT_RETAINED_START'
    $script:Proofs.exactNativeStartIdentity = $true
    $retainedNineTickPredicate = $false
    try {
        [void](& $script:ModuleInfo {
            param($record, $retainedTicks)
            Assert-VerifierBoundReceiptObservationMatchesRetainedProcess $record $retainedTicks
        } $nativeRecord ([long]$script:ChildStartTicks))
        $retainedNineTickPredicate = $true
    } catch {
        if ($script:PrimaryFailureCode -eq '') {
            $script:PrimaryFailureCode = Get-SafeFailureCode $_
        }
    }
    $script:AssertionCount++
    if ($retainedNineTickPredicate) {
        $script:Proofs.retainedNineTickPredicate = $true
    }
    Write-NewTaskJson 'observations.json' ([ordered]@{
        directCimCount = $cimRecords.Count
        nativeParentRecordCount = $nativeRecords.Count
        ambientBaselineCount = $script:ParentBaselineRows.Count
        ownedChildCount = 1
        directCimProcessIdType = $cimPidType
        directCimParentProcessIdType = $cimParentType
        childExecutablePathMatchesLaunchPath = $childExecutablePathMatches
        childExecutableSha256 = $powershellExeSha256
        nativeProcessIdType = $nativePidType
        nativeParentProcessIdType = $nativeParentType
        rawObservationParity = $rawEquivalent
        rawFieldDiagnostics = $rawFieldDiagnostics
        directCreationDateKind = [string]$childCimRecord.CreationDate.Kind
        normalizedDirectCimCreationTicks = [string]$directCimUtcTicks
        nativeCimCreationTicks = [string]$providerCimTicks
        directAndNativeCimCreationTicksEqual = $creationTicksEqual
        nativeCreationTicks = [string]$nativeCreationTicks
        nativeCreationRemainderTicks = [string]$creationRemainder
        nativeCreationKind = [string]$nativeRecord.CreationDate.Kind
        retainedProcessStartTicks = $script:ChildStartTicks
        exactNativeStartIdentity = ($nativeCreationTicks -ceq [long]$script:ChildStartTicks)
        utcMicrosecondProjection = $utcProjection
        retainedNineTickPredicate = $retainedNineTickPredicate
    })
    $script:ExpectedCimDiagnosticFragment = ''
    $actualCimSource = $childCimRecord
    $validSyntheticRow = New-DirectCimBoundaryClone $actualCimSource -IncludeCreationDate
    $baselineSyntheticRows = @($script:ParentBaselineRows | ForEach-Object {
        New-DirectCimBoundaryClone $_ -IncludeCreationDate
    })
    $emptyRows = @()
    $duplicateRows = @($validSyntheticRow, $validSyntheticRow)
    $differentPidRow = New-DirectCimBoundaryClone $actualCimSource -IncludeCreationDate
    $differentPidRow.ProcessId = [uint32]([int]$actualCimSource.ProcessId + 1)
    $wrongParentRow = New-DirectCimBoundaryClone $actualCimSource -IncludeCreationDate
    $wrongParentRow.ParentProcessId = [uint32]([int]$actualCimSource.ParentProcessId + 1)
    $wrongNameRow = New-DirectCimBoundaryClone $actualCimSource -IncludeCreationDate
    $wrongNameRow.Name = 'synthetic-invalid-process-name.exe'
    $wrongCommandRow = New-DirectCimBoundaryClone $actualCimSource -IncludeCreationDate
    $wrongCommandRow.CommandLine = ([string]$actualCimSource.CommandLine + ' --synthetic-boundary-mutation')
    $wrongTimeRow = New-DirectCimBoundaryClone $actualCimSource -IncludeCreationDate
    $wrongTimeRow.CreationDate = ([DateTime]$actualCimSource.CreationDate).AddTicks(10)
    $missingTimeRow = New-DirectCimBoundaryClone $actualCimSource
    $longPidRow = New-DirectCimBoundaryClone $actualCimSource -IncludeCreationDate
    $longPidRow.ProcessId = [long]$actualCimSource.ProcessId
    $duplicateDiagnosticId = if ($script:ParentBaselineRows.Count -eq 1) {
        'NATIVE_CIM_PID_SET_MISMATCH'
    } else { 'NATIVE_CIM_SET_COUNT_MISMATCH' }
    $duplicateDiagnosticFragment = if ($script:ParentBaselineRows.Count -eq 1) {
        'direct CIM child set carried a duplicate or mismatched identity.'
    } else { 'native and direct CIM child sets disagreed.' }
    $fullDifferentPidRows = @($baselineSyntheticRows) + @($differentPidRow)
    $fullWrongParentRows = @($baselineSyntheticRows) + @($wrongParentRow)
    $fullWrongNameRows = @($baselineSyntheticRows) + @($wrongNameRow)
    $fullWrongCommandRows = @($baselineSyntheticRows) + @($wrongCommandRow)
    $fullWrongTimeRows = @($baselineSyntheticRows) + @($wrongTimeRow)
    $fullMissingTimeRows = @($baselineSyntheticRows) + @($missingTimeRow)
    $fullLongPidRows = @($baselineSyntheticRows) + @($longPidRow)

    $syntheticCases = New-Object Collections.ArrayList
    [void]$syntheticCases.Add([pscustomobject]@{ id='CIM_THROWN_PROVIDER_ERROR'; mode='THROW'; rows=@(); diagnosticId='CIM_PROVIDER_ERROR'; fragment='SYNTHETIC_CIM_PROVIDER_FAILURE' })
    [void]$syntheticCases.Add([pscustomobject]@{ id='CIM_EMPTY_CHILD_SET'; mode='EMPTY'; rows=@(); diagnosticId='NATIVE_CIM_SET_COUNT_MISMATCH'; fragment='native and direct CIM child sets disagreed.' })
    [void]$syntheticCases.Add([pscustomobject]@{ id='CIM_DUPLICATE_CHILD_ROW'; mode='ROWS'; rows=$duplicateRows; diagnosticId=$duplicateDiagnosticId; fragment=$duplicateDiagnosticFragment })
    [void]$syntheticCases.Add([pscustomobject]@{ id='CIM_DIFFERENT_PID_SET'; mode='ROWS'; rows=$fullDifferentPidRows; diagnosticId='NATIVE_CIM_PID_SET_MISMATCH'; fragment='direct CIM child set carried a duplicate or mismatched identity.' })
    [void]$syntheticCases.Add([pscustomobject]@{ id='CIM_WRONG_PARENT_PID'; mode='ROWS'; rows=$fullWrongParentRows; diagnosticId='NATIVE_CIM_PARENT_MISMATCH'; fragment='direct CIM child set carried a duplicate or mismatched identity.' })
    [void]$syntheticCases.Add([pscustomobject]@{ id='CIM_WRONG_NAME'; mode='ROWS'; rows=$fullWrongNameRows; diagnosticId='NATIVE_CIM_NAME_MISMATCH'; fragment='identity disagreed at Name.' })
    [void]$syntheticCases.Add([pscustomobject]@{ id='CIM_WRONG_COMMAND_LINE'; mode='ROWS'; rows=$fullWrongCommandRows; diagnosticId='NATIVE_CIM_COMMAND_LINE_MISMATCH'; fragment='identity disagreed at CommandLine.' })
    [void]$syntheticCases.Add([pscustomobject]@{ id='CIM_CREATION_TICKS_PLUS_10'; mode='ROWS'; rows=$fullWrongTimeRows; diagnosticId='NATIVE_CIM_CREATION_TIME_MISMATCH'; fragment='creation times disagreed.' })
    [void]$syntheticCases.Add([pscustomobject]@{ id='CIM_MISSING_CREATION_DATE'; mode='ROWS'; rows=$fullMissingTimeRows; diagnosticId='CIM_REQUIRED_CREATION_DATE_MISSING'; fragment='omitted ''CreationDate'' before protected-helper observation.' })
    [void]$syntheticCases.Add([pscustomobject]@{ id='CIM_PID_INT64_NOT_UINT32'; mode='ROWS'; rows=$fullLongPidRows; diagnosticId='NATIVE_CIM_PROCESS_ID_TYPE_MISMATCH'; fragment='identity disagreed at ProcessId.' })

    $originalCimHelper = Get-CimBoundaryHelperScriptBlock
    $allSyntheticCasesPassed = ($syntheticCases.Count -eq 10)
    foreach ($case in $syntheticCases) {
        try {
            $caseRow = Invoke-SyntheticCimBoundaryCase ([string]$case.id) ([string]$case.mode) `
                $case.rows ([string]$case.diagnosticId) ([string]$case.fragment) $script:ParentProcessId
        } catch {
            $caseRow = [pscustomobject][ordered]@{
                caseId = [string]$case.id; synthetic = $true
                phase = 'nonempty-native-parent-cim-correlation'
                expectedDiagnosticId = [string]$case.diagnosticId
                helperCallCount = -1; caught = $false; infrastructure = $false
                safeFailureCode = Get-SafeFailureCode $_
                expectedDiagnosticMatched = $false; streamedRecordCount = -1
                streamedRecordTypeNames = @(); helperRestored = $false; status = 'FAIL'
            }
        }
        [void]$script:CimBoundaryResults.Add($caseRow)
        $script:AssertionCount++
        if ([string]$caseRow.status -cne 'PASS_EXPECTED_INFRA_REJECTION_ZERO_OUTPUT') {
            $allSyntheticCasesPassed = $false
        }
    }
    $script:Proofs.syntheticCimFailuresRejectedWithoutOutput = ($allSyntheticCasesPassed -and
        $script:CimBoundaryResults.Count -eq 10)
    if (-not $script:Proofs.syntheticCimFailuresRejectedWithoutOutput -and
            $script:PrimaryFailureCode -eq '') {
        $script:PrimaryFailureCode = 'SYNTHETIC_CIM_BOUNDARY_NOT_FAIL_CLOSED'
    }
    Write-NewTaskJson 'cim-boundary-cases.json' ([ordered]@{
        protocol = 'browser-bound-receipt-cim-errors-synthetic-v2'
        allCasesSynthetic = $true
        caseCount = $script:CimBoundaryResults.Count
        allRejectedWithoutPartialOutput = $script:Proofs.syntheticCimFailuresRejectedWithoutOutput
        cases = @($script:CimBoundaryResults.ToArray())
    })

    $restoredHelperText = (Get-CimBoundaryHelperScriptBlock).ToString()
    $moduleHelperRestoredBeforeFreshProbe = ($restoredHelperText -ceq $originalCimHelper.ToString())
    $freshUnmockedCim = @(Get-DirectCimChildren $script:ParentProcessId)
    $freshUnmockedNative = @(Get-NativeParentRecords $script:ParentProcessId)
    $freshChildCim = @($freshUnmockedCim | Where-Object { [int]$_.ProcessId -eq $script:ChildProcessId })
    $freshChildNative = @($freshUnmockedNative | Where-Object { [int]$_.ProcessId -eq $script:ChildProcessId })
    $freshFullSetParity = Test-ParentSetMatchesBaselineAndOwnedChild `
        $script:ParentBaselineRows $freshUnmockedCim $freshUnmockedNative $script:ParentProcessId $script:ChildProcessId
    $freshChildParity = $false
    if ($freshChildCim.Count -eq 1 -and $freshChildNative.Count -eq 1) {
        $freshChildParity = [bool](& $script:ModuleInfo {
            param($expected, $current)
            Test-VerifierBoundReceiptObservationRawEquivalent $expected $current
        } $freshChildCim[0] $freshChildNative[0])
    }
    $freshParity = ($freshFullSetParity -and $freshChildParity)
    $script:Proofs.unmockedParityAfterCimOverrides = ($moduleHelperRestoredBeforeFreshProbe -and $freshParity)
    $script:AssertionCount++
    if (-not $script:Proofs.unmockedParityAfterCimOverrides -and
            $script:PrimaryFailureCode -eq '') {
        $script:PrimaryFailureCode = 'UNMOCKED_CIM_PARITY_NOT_RESTORED_AFTER_SYNTHETIC_OVERRIDES'
    }
    Write-NewTaskJson 'post-injection-parity.json' ([ordered]@{
        synthetic = $false
        directCimRecordCount = $freshUnmockedCim.Count
        nativeCompositeRecordCount = $freshUnmockedNative.Count
        ambientBaselineCount = $script:ParentBaselineRows.Count
        ownedChildCount = $freshChildCim.Count
        fullParentSetParity = $freshFullSetParity
        helperScriptRestored = $moduleHelperRestoredBeforeFreshProbe
        rawObservationParity = $freshParity
        status = if ($script:Proofs.unmockedParityAfterCimOverrides) { 'PASS_UNMOCKED_PARITY_RESTORED' } else { 'FAIL' }
    })
    $liveRejected = $false
    $liveRejectInfrastructure = $false
    $liveRejectObservedPid = $null
    $liveRejectObservedRole = 'NOT_OBSERVED'
    $activeExpectedPids = @(@($script:ParentBaselineRows | ForEach-Object { [int]$_.ProcessId }) + $script:ChildProcessId)
    try {
        Invoke-NoLateDescendantProof $script:ParentProcessId @()
    } catch {
        $caught = $_
        $liveRejectInfrastructure = Test-ModuleInfrastructureError $caught
        $caughtMessage = if ($caught.PSObject.Properties['Exception']) {
            [string]$caught.Exception.Message
        } else { '' }
        $pidMatch = [regex]::Match($caughtMessage, 'helper PID (?<pid>[0-9]+) remained after Browser\.close')
        if ($pidMatch.Success) {
            $liveRejectObservedPid = [int]$pidMatch.Groups['pid'].Value
            if ($liveRejectObservedPid -eq $script:ChildProcessId) {
                $liveRejectObservedRole = 'OWNED_CHILD'
            } elseif (@($script:ParentBaselineRows | Where-Object { [int]$_.ProcessId -eq $liveRejectObservedPid }).Count -eq 1) {
                $liveRejectObservedRole = 'AMBIENT_CONSOLE_HOST'
            } else {
                $liveRejectObservedRole = 'UNEXPECTED_CHILD'
            }
        }
        $liveRejected = ($liveRejectInfrastructure -and $null -ne $liveRejectObservedPid -and
            $activeExpectedPids -contains [int]$liveRejectObservedPid)
    }
    Assert-Contract ($liveRejected -and $liveRejectInfrastructure -and
        $null -ne $liveRejectObservedPid -and $activeExpectedPids -contains [int]$liveRejectObservedPid) `
        'LIVE_PARENT_CHILD_SET_NOT_REJECTED_BY_NO_LATE_PROOF'
    $script:Proofs.liveNoLateRejectedActualParentChildSet = $true
    $stillLiveCim = @(Get-DirectCimChildren $script:ParentProcessId)
    $stillLiveNative = @(Get-NativeParentRecords $script:ParentProcessId)
    $stillLiveMatches = Test-ParentSetMatchesBaselineAndOwnedChild `
        $script:ParentBaselineRows $stillLiveCim $stillLiveNative $script:ParentProcessId $script:ChildProcessId
    Assert-Contract $stillLiveMatches 'LIVE_PARENT_SET_CHANGED_AFTER_NO_LATE_REJECTION'
    Write-NewTaskJson 'live-rejection.json' ([ordered]@{
        schema = 'browser-bound-receipt-live-rejection-v2'
        status = 'EXPECTED_REJECTION'
        rejectionClass = 'INFRASTRUCTURE'
        observedRejectedProcessId = $liveRejectObservedPid
        observedRejectedProcessRole = $liveRejectObservedRole
        rejectedPidBelongsToFreshFullParentCensus = $liveRejected
        baselineProcessIds = @($script:ParentBaselineRows | ForEach-Object { [int]$_.ProcessId })
        ownedChildProcessId = $script:ChildProcessId
        parentProcessId = $script:ParentProcessId
        directCimCountAfterRejection = $stillLiveCim.Count
        nativeCountAfterRejection = $stillLiveNative.Count
        fullParentSetStillMatches = $stillLiveMatches
    })
} catch {
    if ($script:PrimaryFailureCode -eq '') {
        $script:PrimaryFailureCode = Get-SafeFailureCode $_
    }
} finally {
    if ($null -eq $script:ChildProcess) {
        $script:CleanupStatus = 'PASS_NO_CHILD_STARTED'
    } else {
        try {
            $script:ChildProcess.Refresh()
            if (-not $script:ChildProcess.HasExited) {
                $sameIdentity = ([int]$script:ChildProcess.Id -eq $script:ChildProcessId -and
                    [string]$script:ChildProcess.StartTime.ToUniversalTime().Ticks -ceq $script:ChildStartTicks)
                if (-not $sameIdentity) {
                    throw [InvalidOperationException]::new('REFUSING_TO_SIGNAL_CHILD_IDENTITY_NOT_RETAINED')
                }
                Write-ChildStopSignal
                $exited = $script:ChildProcess.WaitForExit(8000)
                if (-not $exited) { $exited = $script:ChildProcess.WaitForExit(17000) }
                if (-not $exited) {
                    throw [TimeoutException]::new('CHILD_DID_NOT_EXIT_WITHIN_GRACEFUL_TTL_WINDOW')
                }
            }
            $script:ChildProcess.Refresh()
            if (-not $script:ChildProcess.HasExited) {
                throw [InvalidOperationException]::new('CHILD_EXIT_STATE_NOT_PROVEN')
            }
            $childExitCode = [int]$script:ChildProcess.ExitCode
            $pidAbsent = $false
            $absenceDeadline = [DateTime]::UtcNow.AddSeconds(3)
            while ([DateTime]::UtcNow -lt $absenceDeadline) {
                $byPid = @(Get-DirectCimProcess $script:ChildProcessId)
                if ($byPid.Count -eq 0) { $pidAbsent = $true; break }
                Start-Sleep -Milliseconds 50
            }
            $script:Proofs.childExitedZero = ($childExitCode -eq 0)
            $script:Proofs.currentCimPidAbsent = $pidAbsent
            if ($childExitCode -eq 0 -and $pidAbsent) {
                $script:CleanupStatus = 'PASS'
                $script:AssertionCount++
                $script:AssertionCount++
            } else {
                throw [InvalidOperationException]::new('CHILD_EXIT_OR_CURRENT_CIM_ABSENCE_NOT_PROVEN')
            }
            Write-NewTaskJson 'child-exit.json' ([ordered]@{
                childProcessId = $script:ChildProcessId
                childExitCode = $childExitCode
                gracefulStopSignalSent = $script:StopSignalSent
                currentCimExactPidAbsent = $pidAbsent
                cleanupStatus = $script:CleanupStatus
            })
        } catch {
            $script:CleanupStatus = 'FAIL_NOT_PROVEN'
            $script:CleanupFailureCode = Get-SafeFailureCode $_
        } finally {
            try { $script:ChildProcess.Dispose() } catch {}
        }
    }
}

if ($script:CleanupStatus -ceq 'PASS') {
    try {
        $ownedChildProcessAfterExit = @(Get-DirectCimProcess $script:ChildProcessId)
        Assert-Contract ($ownedChildProcessAfterExit.Count -eq 0) 'OWNED_CHILD_PID_REAPPEARED_AFTER_RETAINED_EXIT'
        $script:Proofs.currentCimPidAbsent = $true

        $afterChildCim = @(Get-DirectCimChildren $script:ChildProcessId)
        $afterChildNative = @(Get-NativeParentRecords $script:ChildProcessId)
        Assert-Contract ($afterChildCim.Count -eq 0) 'OWNED_CHILD_CIM_DESCENDANTS_NOT_EMPTY_AFTER_EXIT'
        $script:Proofs.ownedChildCimParentEmptyAfterExit = $true
        Assert-Contract ($afterChildNative.Count -eq 0) 'OWNED_CHILD_NATIVE_DESCENDANTS_NOT_EMPTY_AFTER_EXIT'
        $script:Proofs.ownedChildNativeParentEmptyAfterExit = $true

        $afterParentCim = @(Get-DirectCimChildren $script:ParentProcessId)
        $afterParentNative = @(Get-NativeParentRecords $script:ParentProcessId)
        $parentBaselineRestored = Test-ParentSetMatchesBaselineAndOwnedChild `
            $script:ParentBaselineRows $afterParentCim $afterParentNative $script:ParentProcessId 0
        Assert-Contract $parentBaselineRestored 'OUTER_PARENT_SET_DID_NOT_RETURN_TO_RETAINED_BASELINE'
        $script:Proofs.outerParentCimBaselineRestoredAfterExit = ($afterParentCim.Count -eq $script:ParentBaselineRows.Count)
        $script:Proofs.outerParentNativeBaselineRestoredAfterExit = ($afterParentNative.Count -eq $script:ParentBaselineRows.Count)

        $script:EmptyFastPathResult = Invoke-EmptyCimFastPathProbe $script:ChildProcessId
        $script:Proofs.emptyNativeFastPathSkippedCim = ([string]$script:EmptyFastPathResult.status -ceq 'PASS_CIM_HELPER_NOT_CALLED')
        $script:AssertionCount++
        if (-not $script:Proofs.emptyNativeFastPathSkippedCim -and
                $script:PrimaryFailureCode -eq '') {
            $script:PrimaryFailureCode = 'EMPTY_NATIVE_OWNED_CHILD_FAST_PATH_CALLED_CIM'
        }
        Write-NewTaskJson 'empty-fast-path.json' $script:EmptyFastPathResult
        Invoke-NoLateDescendantProof $script:ChildProcessId @()
        $script:Proofs.noLateProofAcceptedAfterOwnedChildExit = $true
        $script:AssertionCount++
        Write-NewTaskJson 'empty-after-exit.json' ([ordered]@{
            schema = 'browser-bound-receipt-owned-child-empty-after-exit-v2'
            ownedChildProcessId = $script:ChildProcessId
            childExitCode = 0
            exactOwnedChildCimPidAbsent = $true
            ownedChildDirectCimDescendantCount = $afterChildCim.Count
            ownedChildNativeDescendantCount = $afterChildNative.Count
            noLateDescendantProof = 'PASS_EMPTY_OWNED_CHILD_TREE'
            outerParentBaselineCount = $script:ParentBaselineRows.Count
            outerParentDirectCimCount = $afterParentCim.Count
            outerParentNativeCount = $afterParentNative.Count
            outerParentBaselineRestored = $parentBaselineRestored
            baselineProcesses = @($script:ParentBaselineRows | ForEach-Object { ConvertTo-ParentIdentityFact $_ })
        })
    } catch {
        if ($script:PrimaryFailureCode -eq '') {
            $script:PrimaryFailureCode = Get-SafeFailureCode $_
        }
    }
}

try {
    if ($null -ne $modulePath -and (Test-Path -LiteralPath $modulePath -PathType Leaf)) {
        $moduleShaAtEnd = Get-FileSha256 $modulePath
        $script:Proofs.sourceStableAtEnd = ($moduleShaAtEnd -ceq $moduleShaAtStart -and
            $moduleShaAtEnd -ceq $capturedModuleSha256)
    }
} catch {
    $script:Proofs.sourceStableAtEnd = $false
}
$allProofs = @($script:Proofs.Values | Where-Object { -not [bool]$_ }).Count -eq 0
$status = if ($script:PrimaryFailureCode -eq '' -and $script:CleanupStatus -ceq 'PASS' -and $allProofs) {
    'PASS'
} else { 'FAIL' }
$clock.Stop()
$result = [ordered]@{
    schema = 'browser-bound-receipt-cim-errors-contract-v2'
    status = $status
    assertions = $script:AssertionCount
    moduleSha256 = $capturedModuleSha256
    moduleSha256After = $moduleShaAtEnd
    runnerProcessId = $script:ParentProcessId
    runnerStartTicks = $script:ParentStartTicks
    runnerExecutableSha256 = $script:ParentExecutableSha256
    parentBaselineCount = $script:ParentBaselineRows.Count
    parentBaselineProcesses = @($script:ParentBaselineRows | ForEach-Object { ConvertTo-ParentIdentityFact $_ })
    sourceStable = [bool]$script:Proofs.sourceStableAtEnd
    cleanupStatus = $script:CleanupStatus
    primaryFailureCode = $script:PrimaryFailureCode
    cleanupFailureCode = $script:CleanupFailureCode
    proofs = $script:Proofs
    syntheticCimBoundaryCases = @($script:CimBoundaryResults.ToArray())
    emptyNativeFastPath = $script:EmptyFastPathResult
    childProcessId = if ($script:ChildProcessId -gt 0) { $script:ChildProcessId } else { $null }
    childStartTicks = if ($script:ChildStartTicks -ne '') { $script:ChildStartTicks } else { $null }
    stopSignalSent = $script:StopSignalSent
    elapsedMs = [Math]::Round($clock.Elapsed.TotalMilliseconds, 3)
    scratchDisposition = 'RETAINED_TASK_OWNED_EVIDENCE_LEAF'
    scope = 'Actual native parent enumeration plus ten explicitly synthetic CIM response failures, zero partial output, restored actual CIM parity, empty native fast path without CIM, live-child no-late rejection, graceful exit, and exact post-exit absence; no browser qualification.'
}
if ($taskRootCreated -and $null -ne $script:TaskRoot) {
    try {
        $knownFiles = @('child.ps1','child-ready.json','child-stop.txt','launch.json',
            'observations.json','cim-boundary-cases.json','post-injection-parity.json',
            'live-rejection.json','child-exit.json','empty-fast-path.json','empty-after-exit.json')
        $fileRows = New-Object System.Collections.Generic.List[object]
        foreach ($name in $knownFiles) {
            $path = Join-Path $script:TaskRoot $name
            if (Test-Path -LiteralPath $path -PathType Leaf) {
                $item = Get-Item -LiteralPath $path -Force
                $fileRows.Add([pscustomobject][ordered]@{
                    name = $name
                    bytes = [long]$item.Length
                    sha256 = Get-FileSha256 $path
                    isReparsePoint = (([int]$item.Attributes -band [int][IO.FileAttributes]::ReparsePoint) -ne 0)
                })
            }
        }
        $result['scratchFiles'] = @($fileRows.ToArray())
        $result['scratchRootIdentityVerified'] = ((Resolve-Path -LiteralPath $script:TaskRoot).Path -ceq $script:TaskRoot)
    } catch {
        $result['scratchRootIdentityVerified'] = $false
    }
    try { Write-NewTaskJson 'result.json' $result } catch { $status = 'FAIL'; $result['status'] = $status }
}

$stdoutResult = [ordered]@{
    status = $status
    assertions = $script:AssertionCount
    cleanupStatus = $script:CleanupStatus
    sourceStable = [bool]$script:Proofs.sourceStableAtEnd
    primaryFailureCode = $script:PrimaryFailureCode
    cleanupFailureCode = $script:CleanupFailureCode
}
if ($status -ceq 'PASS') {
    Write-Output ('PASS: browser bound-receipt CIM boundary contracts assertions={0}' -f $script:AssertionCount)
} else {
    Write-Output ('FAIL: browser bound-receipt CIM boundary contracts ' +
        ($stdoutResult | ConvertTo-Json -Compress -Depth 6))
    exit 2
}

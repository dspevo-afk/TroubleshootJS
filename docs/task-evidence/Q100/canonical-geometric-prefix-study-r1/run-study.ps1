param(
    [Parameter(Mandatory = $true)][string]$RepositoryRoot,
    [Parameter(Mandatory = $true)][string]$InputPinsPath,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{64}$')][string]$InputPinsSha256,
    [Parameter(Mandatory = $true)][string]$OutputRoot,
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$PythonExe,
    [Parameter(Mandatory = $true)][ValidateSet('CANARY', 'SUITE')][string]$Mode
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$hostClock = [Diagnostics.Stopwatch]::StartNew()
$utf8 = [Text.UTF8Encoding]::new($false)
$moduleSha256 = 'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c'
$selectedPowerShellSha256 = '60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f'
$selectedPythonSha256 = 'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec'
$selectedJavaSha256 = 'e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce'
$selectedJavacSha256 = '61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64'
$selectedP6PinsSha256 = 'b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771'
$selectedP6Parent = '831d69a41d9504bfb845caef6942e9ef58c9d6d2'
$selectedCanaryPinsSha256 = '11b3fa459e9bf6950c7ee3d3e804a3b37638bb23cc6c322aaa8ef94549462a5a'
$selectedCanaryQualificationSha256 = '755f921b068f325c9e81315b88f38c4bc8b7196dd61d65f8c8ecf61e26e43276'
$sourceParent = '0891474cadc4b9e00d7911e81c124721a11efb87'
$sourceRowsRequired = 1084
$productionFilesRequired = 850
$canaryCompilerSourcesRequired = 1081
$compilerSourcesRequired = 1083
$modeRoles = @('router', 'support', 'writer', 'adapter', 'blocker', 'queries',
    'stageInspector', 'netRouterOverlay', 'rulesOverlay', 'reasonCanary')
$bridgeNames = @('CirSim.java', 'ChipElm.java', 'PhysicalSpecificationDeveloperVerifier.java')
$excludedBaseSources = @(
    'src/com/lushprojects/circuitjs1/client/CirSim.java',
    'src/com/lushprojects/circuitjs1/client/ChipElm.java',
    'src/com/lushprojects/circuitjs1/client/PhysicalSpecificationDeveloperVerifier.java',
    'src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java',
    'src/com/lushprojects/circuitjs1/client/PcbNetRouter.java',
    'src/com/lushprojects/circuitjs1/client/PcbTwoLayerRules.java'
)
$modeOrder = @('P6_CANARY', 'P8_LITERAL_CANARY', 'P8_BAD_MODE', 'P8_BAD_UUID', 'P8_BAD_INPUT_SHA', 'P8_NONEMPTY_DATA')
if ($Mode -ceq 'SUITE') { $modeOrder += @('NULL', 'OBSERVE', 'CALLBACK_THROW', 'REASON_THROW') }
$baseOutputFiles = @(
    'host-launch-intent.json', 'scratch-intent.json',
    'source-preflight-process.json', 'source-preflight.stdout', 'source-preflight.stderr',
    'compiler-launch-intent.json', 'compiler-process.json', 'compiler.stdout', 'compiler.stderr', 'compiler-sources.txt',
    'bridge-copies.json',
    'source-prenative-process.json', 'source-prenative.stdout', 'source-prenative.stderr',
    'p6-canary-launch-intent.json', 'p6-canary-process.json', 'p6-canary.stdout', 'p6-canary.stderr',
    'p8-canary-launch-intent.json', 'p8-canary-process.json', 'p8-canary.stdout', 'p8-canary.stderr',
    'p8-bad-mode-launch-intent.json', 'p8-bad-mode-process.json', 'p8-bad-mode.stdout', 'p8-bad-mode.stderr',
    'p8-bad-uuid-launch-intent.json', 'p8-bad-uuid-process.json', 'p8-bad-uuid.stdout', 'p8-bad-uuid.stderr',
    'p8-bad-input-sha-launch-intent.json', 'p8-bad-input-sha-process.json', 'p8-bad-input-sha.stdout', 'p8-bad-input-sha.stderr',
    'p8-nonempty-data-launch-intent.json', 'p8-nonempty-data-process.json', 'p8-nonempty-data.stdout', 'p8-nonempty-data.stderr',
    'source-post-process.json', 'source-post.stdout', 'source-post.stderr',
    'suite-index.json', 'suite-footer.json'
)
$suiteOutputFiles = @(
    'runs/NULL/launch-intent.json', 'runs/NULL/process.json', 'runs/NULL.stdout', 'runs/NULL.stderr',
    'runs/NULL/data/op-1.jsonl', 'runs/NULL/data/fixed-prefix.jsonl', 'runs/NULL/data/prefix-blockers.json',
    'runs/NULL/data/prefix-stages.json', 'runs/NULL/data/canonical-stage-index.json',
    'runs/NULL/data/prefix-geometric-reasons.jsonl', 'runs/NULL/data/canonical-geometric-prefix-index.json',
    'runs/OBSERVE/launch-intent.json', 'runs/OBSERVE/process.json', 'runs/OBSERVE.stdout', 'runs/OBSERVE.stderr',
    'runs/OBSERVE/data/op-1.jsonl', 'runs/OBSERVE/data/fixed-prefix.jsonl', 'runs/OBSERVE/data/prefix-blockers.json',
    'runs/OBSERVE/data/prefix-stages.json', 'runs/OBSERVE/data/canonical-stage-index.json',
    'runs/OBSERVE/data/prefix-geometric-reasons.jsonl', 'runs/OBSERVE/data/canonical-geometric-prefix-index.json',
    'runs/CALLBACK_THROW/launch-intent.json', 'runs/CALLBACK_THROW/process.json', 'runs/CALLBACK_THROW.stdout', 'runs/CALLBACK_THROW.stderr',
    'runs/CALLBACK_THROW/data/op-1.jsonl',
    'runs/REASON_THROW/launch-intent.json', 'runs/REASON_THROW/process.json', 'runs/REASON_THROW.stdout', 'runs/REASON_THROW.stderr',
    'runs/REASON_THROW/data/op-1.jsonl', 'runs/REASON_THROW/data/prefix-blockers.json',
    'runs/REASON_THROW/data/prefix-stages.json', 'runs/REASON_THROW/data/prefix-geometric-reasons.jsonl'
)
$outputFiles = @($baseOutputFiles)
$allowedDirectories = @(
    'controls', 'controls/P8_BAD_MODE', 'controls/P8_BAD_MODE/data',
    'controls/P8_BAD_UUID', 'controls/P8_BAD_UUID/data',
    'controls/P8_BAD_INPUT_SHA', 'controls/P8_BAD_INPUT_SHA/data',
    'controls/P8_NONEMPTY_DATA', 'controls/P8_NONEMPTY_DATA/data'
)
if ($Mode -ceq 'SUITE') {
    $outputFiles += $suiteOutputFiles
    $allowedDirectories += @('runs', 'runs/NULL', 'runs/NULL/data', 'runs/OBSERVE', 'runs/OBSERVE/data',
        'runs/CALLBACK_THROW', 'runs/CALLBACK_THROW/data', 'runs/REASON_THROW', 'runs/REASON_THROW/data')
}
$outputFiles += @('controls/P8_NONEMPTY_DATA/data/sentinel.txt')
$allSuccessFiles = @($outputFiles + @('host-result.json'))
$phase = 'SETUP'
$outputCreated = $false
$scratchCreated = $false
$scratchRemoved = $false
$compilerInvoked = $false
$nativeInvoked = $false
$scratch = $null
$scratchTicks = $null
$outputCanonical = $null
$outputCreationTicks = $null
$owner = $null
$pins = $null
$p6Pins = $null
$canaryPins = $null
$inventoryRows = $null
$inventoryBytes = $null
$sourceInventorySha256 = $null
$wireSha256 = $null
$canaryRoot = $null
$bridgeManifestPath = $null
$bridgeRoot = $null
$modulePath = $null
$auditorPath = $null
$java = $null
$javac = $null
$classpath = $null
$compilerArgs = $null
$argsFile = $null
$bridgeCopies = @()
$processRecords = [ordered]@{}
$sourceChecks = [ordered]@{}
$nativeCanaries = [ordered]@{}
$operations = @()
$inputControls = @()
$failureCode = $null
$failureMessage = $null
$cleanupFailure = $null
$failedProcessCall = $null
$failedProcessReturn = $null
$returnedChildEvidence = $null
$registeredProcessCaptures = @()
$failureBundleResult = $null
$failureEvidenceWriteError = $null
$hostFailureSummaryWriteError = $null
[long]$writtenOutputBytes = 0
$nativePhaseStartOffsetMs = $null
$cleanupStartOffsetMs = $null
$childWallSumMs = [double]0
$workCreditsMs = [double]0
$p6CanaryWallMs = [double]0
$p8CanaryWallMs = [double]0
$p8ControlWalls = [ordered]@{}
$operationWalls = [ordered]@{}
$operationWorkCredits = [ordered]@{}
$hostPath = $null
$hostSha = $null
$hostStartTicks = $null
$suiteId = $null
function Require([bool]$Condition, [string]$Code) {
    if (-not $Condition) { throw $Code }
}

function Get-Hash([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Test-OrdinalPathEqual([string]$Left, [string]$Right) {
    return [string]::Equals($Left, $Right, [StringComparison]::OrdinalIgnoreCase)
}

function Test-PathUnder([string]$Child, [string]$Root) {
    $canonicalChild = [IO.Path]::GetFullPath($Child).TrimEnd('\', '/')
    $canonicalRoot = [IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    if (Test-OrdinalPathEqual $canonicalChild $canonicalRoot) { return $true }
    $prefix = $canonicalRoot + [IO.Path]::DirectorySeparatorChar
    return $canonicalChild.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)
}

function Assert-LocalNoReparseAncestors([string]$Path) {
    $current = [IO.Path]::GetFullPath($Path)
    while (-not [string]::IsNullOrWhiteSpace($current)) {
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            Require (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'REPARSE_PATH'
        }
        $parent = [IO.Path]::GetDirectoryName($current)
        if ([string]::IsNullOrWhiteSpace($parent) -or (Test-OrdinalPathEqual $parent $current)) { break }
        $current = $parent
    }
}

function Require-CanonicalExistingFile([string]$Path, [string]$Code) {
    Require ([IO.Path]::IsPathRooted($Path)) $Code
    $full = [IO.Path]::GetFullPath($Path)
    Require (Test-OrdinalPathEqual $Path $full) $Code
    Require (Test-Path -LiteralPath $full -PathType Leaf) $Code
    Assert-VerifierNoReparseAncestors $full
    return $full
}

function Require-CanonicalExistingDirectory([string]$Path, [string]$Code) {
    Require ([IO.Path]::IsPathRooted($Path)) $Code
    $full = [IO.Path]::GetFullPath($Path)
    Require (Test-OrdinalPathEqual $Path $full) $Code
    Require (Test-Path -LiteralPath $full -PathType Container) $Code
    Assert-VerifierNoReparseAncestors $full
    return $full
}

function Require-ExactKeys($Value, [string[]]$Expected, [string]$Code) {
    Require ($null -ne $Value) $Code
    $actual = @($Value.PSObject.Properties | ForEach-Object { [string]$_.Name })
    Require ($actual.Count -eq $Expected.Count) $Code
    foreach ($key in $Expected) {
        $matches = @($actual | Where-Object { [string]::Equals($_, $key, [StringComparison]::Ordinal) })
        Require ($matches.Count -eq 1) $Code
    }
}

function Test-ExactInteger($Value) {
    if ($null -eq $Value -or $Value -is [bool]) { return $false }
    return ($Value -is [byte] -or $Value -is [int16] -or $Value -is [int32] -or
        $Value -is [int64] -or $Value -is [uint16] -or $Value -is [uint32] -or $Value -is [uint64])
}

function Require-ExactInteger($Value, [long]$Minimum, [long]$Maximum, [string]$Code) {
    Require (Test-ExactInteger $Value) $Code
    Require ([decimal]$Value -ge [decimal]$Minimum -and [decimal]$Value -le [decimal]$Maximum) $Code
}

function Write-NewBytes([string]$Path, [byte[]]$Bytes) {
    Require ([long]$script:writtenOutputBytes + [long]$Bytes.Length -le 41943040) 'AGGREGATE_OUTPUT_LIMIT'
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try {
        if ($Bytes.Length -gt 0) { $stream.Write($Bytes, 0, $Bytes.Length) }
        $stream.Flush()
    }
    finally { $stream.Dispose() }
    $script:writtenOutputBytes = [long]$script:writtenOutputBytes + [long]$Bytes.Length
}

function Write-NewText([string]$Path, [string]$Text) {
    Write-NewBytes $Path $utf8.GetBytes($Text)
}

function Write-NewJson([string]$Path, $Value) {
    $json = ConvertTo-Json -InputObject $Value -Depth 40 -Compress
    $bytes = $utf8.GetBytes($json + "`n")
    Require ($bytes.Length -le 65536) 'STRUCTURED_OUTPUT_LIMIT'
    Write-NewBytes $Path $bytes
}

function Test-ByteEquality([byte[]]$Left, [byte[]]$Right) {
    if ($Left.Length -ne $Right.Length) { return $false }
    for ($i = 0; $i -lt $Left.Length; $i++) {
        if ($Left[$i] -ne $Right[$i]) { return $false }
    }
    return $true
}

function Test-VerifierLogsRemoved($ProcessResult) {
    foreach ($name in @('ProcessRoot', 'StdoutLog', 'StderrLog')) {
        $property = $ProcessResult.PSObject.Properties[$name]
        Require ($null -ne $property -and $property.Value -is [string] -and
            -not [string]::IsNullOrWhiteSpace([string]$property.Value)) 'VERIFIER_LOG_RECEIPT'
        Require (-not (Test-Path -LiteralPath ([string]$property.Value))) 'VERIFIER_LOG_ROOT_REMAINS'
    }
}

function Save-ProcessEvidence([string]$Stem, $ProcessResult) {
    Require ($ProcessResult.TerminationProven -eq $true) 'PROCESS_TERMINATION_UNPROVEN'
    Require-ExactInteger $ProcessResult.ProcessId 1 ([int]::MaxValue) 'PROCESS_ID_INVALID'
    Require-ExactInteger $ProcessResult.ProcessStartTicks 1 ([long]::MaxValue) 'PROCESS_START_INVALID'
    Test-VerifierLogsRemoved $ProcessResult
    $receiptName = $Stem + '-process.json'
    $stdoutName = $Stem + '.stdout'
    $stderrName = $Stem + '.stderr'
    Write-NewJson (Join-Path $outputCanonical $receiptName) $ProcessResult
    $stdoutBytes = $utf8.GetBytes([string]$ProcessResult.Stdout)
    $stderrBytes = $utf8.GetBytes([string]$ProcessResult.Stderr)
    Write-NewBytes (Join-Path $outputCanonical $stdoutName) $stdoutBytes
    Write-NewBytes (Join-Path $outputCanonical $stderrName) $stderrBytes
    $diskStdout = [IO.File]::ReadAllBytes((Join-Path $outputCanonical $stdoutName))
    $diskStderr = [IO.File]::ReadAllBytes((Join-Path $outputCanonical $stderrName))
    Require (Test-ByteEquality $stdoutBytes $diskStdout) 'STDOUT_COPY_MISMATCH'
    Require (Test-ByteEquality $stderrBytes $diskStderr) 'STDERR_COPY_MISMATCH'
    return [ordered]@{ receipt = $receiptName; stdout = $stdoutName; stderr = $stderrName }
}

function Invoke-BoundedChild([string]$FilePath, [string[]]$Arguments, [int]$TimeoutMilliseconds, [string]$CallPhase) {
    $started = $hostClock.Elapsed.TotalMilliseconds
    $script:returnedChildEvidence = $null
    try {
        $processResult = Invoke-VerifierBoundedProcess -FilePath $FilePath -Arguments $Arguments -TimeoutMilliseconds $TimeoutMilliseconds
    }
    catch {
        $finished = $hostClock.Elapsed.TotalMilliseconds
        $exception = $_.Exception
        $exceptionData = @()
        if ($null -ne $exception.Data) {
            foreach ($key in $exception.Data.Keys) {
                $exceptionData += @([ordered]@{ key = [string]$key; value = [string]$exception.Data[$key] })
            }
        }
        $script:failedProcessCall = [ordered]@{
            status = 'CALL_FAILED_WITHOUT_RETURNED_PROCESS_RECEIPT'
            phase = $CallPhase
            executable = $FilePath
            arguments = @($Arguments)
            limitMs = [int]$TimeoutMilliseconds
            wallMs = [double]($finished - $started)
            exceptionType = [string]$exception.GetType().FullName
            exceptionText = [string]$exception.Message
            exceptionData = $exceptionData
            process = $null
            terminationProven = $false
        }
        throw 'CALL_FAILED_WITHOUT_RETURNED_PROCESS_RECEIPT'
    }
    $finished = $hostClock.Elapsed.TotalMilliseconds
    $wall = [double]($finished - $started)
    $script:returnedChildEvidence = [ordered]@{
        phase = $CallPhase
        wallMs = $wall
        process = $processResult
    }
    $script:registeredProcessCaptures += [pscustomobject]@{
        ordinal = [int]$script:registeredProcessCaptures.Count
        phase = [string]$CallPhase
        wallMs = [double]$wall
        process = $processResult
    }
    $capturedBytes = $utf8.GetByteCount([string]$processResult.Stdout) + $utf8.GetByteCount([string]$processResult.Stderr)
    Require ($capturedBytes -le 8388608) 'PROCESS_CAPTURE_LIMIT'
    return [pscustomobject]@{ process = $processResult; wallMs = $wall }
}

function Require-InvocationReceipt($ProcessResult, [string]$FilePath, [string[]]$Arguments, [string]$Code) {
    Require ([string]::Equals([string]$ProcessResult.FilePath, $FilePath, [StringComparison]::Ordinal)) $Code
    $actualArguments = @($ProcessResult.Arguments)
    Require ($actualArguments.Count -eq $Arguments.Count) $Code
    for ($i = 0; $i -lt $Arguments.Count; $i++) {
        Require ([string]::Equals([string]$actualArguments[$i], [string]$Arguments[$i], [StringComparison]::Ordinal)) $Code
    }
}

function Parse-OneJsonMarker([string]$Text, [string]$Prefix, [string]$Code, [switch]$AllowPreamble) {
    $pattern = '(?m)^' + [regex]::Escape($Prefix) + ' (?<json>\{[^\r\n]*\})\r?$'
    $matches = [regex]::Matches($Text, $pattern)
    Require ($matches.Count -eq 1) $Code
    $match = $matches[0]
    $tail = $Text.Substring($match.Index + $match.Length)
    Require ($tail -eq '' -or $tail -eq "`n" -or $tail -eq "`r`n") $Code
    if (-not $AllowPreamble) {
        Require ($match.Index -eq 0) $Code
    }
    try { return (ConvertFrom-Json -InputObject $match.Groups['json'].Value -ErrorAction Stop) }
    catch { throw $Code }
}

function Parse-AuditorPass([string]$Text) {
    $prefix = 'PASS_Q100_GEOMETRIC_PREFIX_INPUTS_ONLY'
    $value = Parse-OneJsonMarker $Text $prefix 'INPUT_AUDIT_MARKER'
    Require-ExactKeys $value @('status', 'sourceRows', 'productionFiles', 'extraFiles', 'pinsSha256', 'sourceInventorySha256') 'INPUT_AUDIT_SCHEMA'
    Require ([string]::Equals([string]$value.status, $prefix, [StringComparison]::Ordinal)) 'INPUT_AUDIT_STATUS'
    Require-ExactInteger $value.sourceRows 1084 1084 'SOURCE_CENSUS'
    Require-ExactInteger $value.productionFiles 850 850 'SOURCE_CENSUS'
    Require-ExactInteger $value.extraFiles 0 ([int]::MaxValue) 'SOURCE_CENSUS'
    Require ([string]$value.pinsSha256 -ceq $InputPinsSha256) 'INPUT_PINS_HASH'
    Require ([string]$value.sourceInventorySha256 -ceq $sourceInventorySha256) 'SOURCE_INVENTORY_HASH'
    return [ordered]@{
        status = [string]$value.status
        sourceRows = [long]$value.sourceRows
        productionFiles = [long]$value.productionFiles
        extraFiles = [long]$value.extraFiles
        pinsSha256 = [string]$value.pinsSha256
        sourceInventorySha256 = [string]$value.sourceInventorySha256
    }
}

function Get-AuditorFailureCode([string]$Text) {
    $match = [regex]::Match($Text, '\AP8_INPUT_AUDIT_FAIL (?<code>[A-Z0-9_]+)(?:\r?\n)?\z')
    if (-not $match.Success) { return 'INPUT_AUDIT_FAILED' }
    return $match.Groups['code'].Value
}

function Invoke-Auditor([string]$Stage) {
    $recordKey = switch -CaseSensitive ($Stage) {
        'preflight' { 'preflight' }
        'prenative' { 'preNative' }
        'post' { 'postSource' }
        default { throw 'AUDIT_STAGE' }
    }
    $auditorPath = [string]$pins.roles.input.auditor
    $auditorArgs = @('-I', '-B', $auditorPath, $InputPinsPath, $InputPinsSha256)
    $call = Invoke-BoundedChild -FilePath $PythonExe -Arguments $auditorArgs -TimeoutMilliseconds 60000 -CallPhase ('AUDIT_' + $Stage.ToUpperInvariant())
    $proc = $call.process
    $wall = [double]$call.wallMs
    try {
        Require-InvocationReceipt $proc $PythonExe $auditorArgs 'AUDIT_PROCESS_IDENTITY'
        Test-VerifierLogsRemoved $proc
        Require ($wall -ge 0 -and $wall -le 60000) ($Stage.ToUpperInvariant() + '_WALL_LIMIT')
        if ($outputCreated) {
            $record = Save-ProcessEvidenceAt ('source-' + $(if ($Stage -eq 'prenative') { 'prenative' } elseif ($Stage -eq 'post') { 'post' } else { 'preflight' }) + '-process.json') ('source-' + $(if ($Stage -eq 'prenative') { 'prenative' } elseif ($Stage -eq 'post') { 'post' } else { 'preflight' }) + '.stdout') ('source-' + $(if ($Stage -eq 'prenative') { 'prenative' } elseif ($Stage -eq 'post') { 'post' } else { 'preflight' }) + '.stderr') $proc
            $processRecords[$recordKey] = [ordered]@{ receipt = $record.receipt; stdout = $record.stdout; stderr = $record.stderr; wallMs = $wall }
        }
        if ($proc.ExitCode -ne 0 -or $proc.TerminationProven -ne $true) { throw (Get-AuditorFailureCode ([string]$proc.Stderr)) }
        Require ([string]::IsNullOrEmpty([string]$proc.Stderr)) ($Stage.ToUpperInvariant() + '_AUDIT_PROCESS')
        $summary = Parse-AuditorPass ([string]$proc.Stdout)
        $script:returnedChildEvidence = $null
        return [pscustomobject]@{ process = $proc; wallMs = $wall; summary = $summary }
    }
    catch {
        $script:failedProcessReturn = [ordered]@{ phase = ('AUDIT_' + $Stage.ToUpperInvariant()); wallMs = $wall; process = $proc }
        throw
    }
}

function Require-PinnedFileFromRows($Rows, [string]$Path, [string]$Code) {
    $matches = @($Rows | Where-Object { Test-OrdinalPathEqual ([string]$_.path) $Path })
    Require ($matches.Count -eq 1) $Code
    $row = $matches[0]
    Require-ExactKeys $row @('path', 'bytes', 'sha256') $Code
    Require-ExactInteger $row.bytes 0 ([long]::MaxValue) $Code
    Require ([string]$row.sha256 -cmatch '^[0-9a-f]{64}$') $Code
    $file = Require-CanonicalExistingFile $Path $Code
    $item = Get-Item -LiteralPath $file
    Require ($item.Length -eq [long]$row.bytes -and (Get-Hash $file) -ceq [string]$row.sha256) $Code
    return $row
}
function Require-PinnedFile([string]$Path, [string]$Code) {
    return Require-PinnedFileFromRows $pins.files $Path $Code
}
function Require-PinnedSourceFile([string]$RelativePath, [string]$Code) {
    $rows = @($canaryPins.sourceRows | Where-Object { [string]::Equals([string]$_.path, $RelativePath, [StringComparison]::Ordinal) })
    Require ($rows.Count -eq 1) $Code
    $row = $rows[0]
    Require-ExactKeys $row @('path', 'bytes', 'role', 'sha256') $Code
    Require ([string]$row.role -ceq 'BASE_SOURCE') $Code
    Require-ExactInteger $row.bytes 1 ([long]::MaxValue) $Code
    Require ([string]$row.sha256 -cmatch '^[0-9a-f]{64}$') $Code
    $file = Require-CanonicalExistingFile ([IO.Path]::GetFullPath((Join-Path $RepositoryRoot $RelativePath))) $Code
    $item = Get-Item -LiteralPath $file
    Require ($item.Length -eq [long]$row.bytes -and (Get-Hash $file) -ceq [string]$row.sha256) $Code
    return $file
}

function Verify-AllManifestExtraPins {
    $hostFull = [IO.Path]::GetFullPath($PSCommandPath)
    $previous = $null
    foreach ($row in $pins.files) {
        $path = [string]$row.path
        Require ([IO.Path]::IsPathRooted($path) -and (Test-OrdinalPathEqual $path ([IO.Path]::GetFullPath($path)))) 'EXTRA_INPUT_HASH'
        if ($null -ne $previous) {
            $previousOrderKey = ([string]$previous).ToLowerInvariant()
            $pathOrderKey = $path.ToLowerInvariant()
            Require ([StringComparer]::Ordinal.Compare($previousOrderKey, $pathOrderKey) -lt 0) 'INPUT_FILE_ORDER'
        }
        $previous = $path
        Require-ExactKeys $row @('path', 'bytes', 'sha256') 'INPUT_FILE_SCHEMA'
        Require-ExactInteger $row.bytes 0 ([long]::MaxValue) 'INPUT_FILE_SCHEMA'
        Require ([string]$row.sha256 -cmatch '^[0-9a-f]{64}$') 'INPUT_FILE_SCHEMA'
        if (Test-OrdinalPathEqual $path $hostFull) { continue }
        $item = Get-Item -LiteralPath (Require-CanonicalExistingFile $path 'EXTRA_INPUT_HASH')
        Require ($item.Length -eq [long]$row.bytes -and (Get-Hash $path) -ceq [string]$row.sha256) 'EXTRA_INPUT_HASH'
    }
}

function Validate-InputManifest($Manifest) {
    Require-ExactKeys $Manifest @('schema', 'repositoryRoot', 'parent', 'canaryPrerequisite', 'p6Context', 'sourceInventory', 'roles', 'files', 'toolPaths') 'INPUT_SCHEMA'
    Require ([string]$Manifest.schema -ceq 'tsj-q100-geometric-prefix-study-inputs-v1') 'INPUT_SCHEMA'
    Require ([string]$Manifest.parent -ceq $sourceParent) 'INPUT_CONTEXT'
    Require (Test-OrdinalPathEqual ([string]$Manifest.repositoryRoot) $RepositoryRoot) 'INPUT_CONTEXT'

    Require-ExactKeys $Manifest.canaryPrerequisite @('root', 'pinsPath', 'pinsSha256', 'qualificationPath', 'qualificationSha256') 'CANARY_CONTEXT'
    Require-ExactKeys $Manifest.p6Context @('pinsPath', 'pinsSha256') 'P6_CONTEXT'
    Require-ExactKeys $Manifest.sourceInventory @('path', 'sha256') 'SOURCE_INVENTORY_PIN'
    Require-ExactKeys $Manifest.roles @('native', 'process', 'input') 'ROLE_KEYS'
    Require-ExactKeys $Manifest.toolPaths @('pythonExe', 'javaHome', 'powershellExe') 'TOOL_PATHS'
    Require ((Test-OrdinalPathEqual ([string]$Manifest.toolPaths.pythonExe) $PythonExe) -and
        (Test-OrdinalPathEqual ([string]$Manifest.toolPaths.javaHome) $JavaHome) -and
        (Test-OrdinalPathEqual ([string]$Manifest.toolPaths.powershellExe) ([string]$owner.Path))) 'TOOL_CONTEXT'
    Require-ExactKeys $Manifest.roles.native @('study', 'inspector') 'ROLE_KEYS'
    Require-ExactKeys $Manifest.roles.process @('helper', 'reader', 'host') 'ROLE_KEYS'
    Require-ExactKeys $Manifest.roles.input @('maker', 'auditor') 'ROLE_KEYS'
    $nativeStudyPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'source/CanonicalGeometricPrefixStudy.java'))
    $nativeInspectorPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'source/CanonicalGeometricReasonInspector.java'))
    $expectedHelper = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'scripts/VerifierIsolation.psm1'))
    $expectedHost = [IO.Path]::GetFullPath($PSCommandPath)
    $expectedReader = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'read_geometric_prefix.py'))
    $expectedMaker = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'make_study_inputs.py'))
    $expectedAuditor = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'audit_study_inputs.py'))
    Require ((Test-OrdinalPathEqual ([string]$Manifest.roles.native.study) $nativeStudyPath) -and
        (Test-OrdinalPathEqual ([string]$Manifest.roles.native.inspector) $nativeInspectorPath) -and
        (Test-OrdinalPathEqual ([string]$Manifest.roles.process.helper) $expectedHelper) -and
        (Test-OrdinalPathEqual ([string]$Manifest.roles.process.reader) $expectedReader) -and
        (Test-OrdinalPathEqual ([string]$Manifest.roles.process.host) $expectedHost) -and
        (Test-OrdinalPathEqual ([string]$Manifest.roles.input.maker) $expectedMaker) -and
        (Test-OrdinalPathEqual ([string]$Manifest.roles.input.auditor) $expectedAuditor)) 'ROLE_MAPPING'
    $script:auditorPath = $expectedAuditor
    Require ([IO.Path]::IsPathRooted([string]$Manifest.sourceInventory.path) -and
        (Test-OrdinalPathEqual ([string]$Manifest.sourceInventory.path) ([IO.Path]::GetFullPath([string]$Manifest.sourceInventory.path)))) 'SOURCE_INVENTORY_PIN'
    Require ([IO.Path]::GetFileName([string]$Manifest.sourceInventory.path) -ceq 'native-source-inventory.jsonl') 'SOURCE_INVENTORY_PIN'
    Require ([string]$Manifest.sourceInventory.sha256 -cmatch '^[0-9a-f]{64}$') 'SOURCE_INVENTORY_PIN'
    $script:sourceInventorySha256 = [string]$Manifest.sourceInventory.sha256
    Require ([string]$Manifest.p6Context.pinsSha256 -ceq $selectedP6PinsSha256 -and
        [string]$Manifest.canaryPrerequisite.pinsSha256 -ceq $selectedCanaryPinsSha256 -and
        [string]$Manifest.canaryPrerequisite.qualificationSha256 -ceq $selectedCanaryQualificationSha256) 'INPUT_CONTEXT'
    $inputPathsToCheck = @(
        [pscustomobject]@{ path = [string]$Manifest.canaryPrerequisite.root; code = 'CANARY_ROOT' },
        [pscustomobject]@{ path = [string]$Manifest.canaryPrerequisite.pinsPath; code = 'CANARY_PINS_PATH' },
        [pscustomobject]@{ path = [string]$Manifest.canaryPrerequisite.qualificationPath; code = 'CANARY_QUALIFICATION_PATH' },
        [pscustomobject]@{ path = [string]$Manifest.p6Context.pinsPath; code = 'P6_PINS_PATH' },
        [pscustomobject]@{ path = [string]$Manifest.sourceInventory.path; code = 'SOURCE_INVENTORY_PATH' }
    )
    foreach ($entry in $inputPathsToCheck) {
        Require ([IO.Path]::IsPathRooted($entry.path) -and (Test-OrdinalPathEqual $entry.path ([IO.Path]::GetFullPath($entry.path)))) $entry.code
    }

    Require ($Manifest.files -is [array] -and $Manifest.files.Count -gt 0 -and $Manifest.files.Count -le 256) 'INPUT_FILE_CENSUS'
    $filePaths = @()
    foreach ($row in $Manifest.files) {
        Require-ExactKeys $row @('path', 'bytes', 'sha256') 'INPUT_FILE_SCHEMA'
        Require ([string]$row.path -and [IO.Path]::IsPathRooted([string]$row.path)) 'INPUT_FILE_SCHEMA'
        Require-ExactInteger $row.bytes 0 ([long]::MaxValue) 'INPUT_FILE_SCHEMA'
        Require ([string]$row.sha256 -cmatch '^[0-9a-f]{64}$') 'INPUT_FILE_SCHEMA'
        Require (Test-OrdinalPathEqual ([string]$row.path) ([IO.Path]::GetFullPath([string]$row.path))) 'INPUT_FILE_PATH'
        $filePaths += @([string]$row.path)
    }
    Require (@($filePaths | Select-Object -Unique).Count -eq $filePaths.Count) 'INPUT_FILE_DUPLICATE'
    $selfRows = @($Manifest.files | Where-Object { Test-OrdinalPathEqual ([string]$_.path) $expectedHost })
    Require ($selfRows.Count -eq 1) 'SELF_PIN'
    $selfInfo = Get-Item -LiteralPath $expectedHost
    Require ([long]$selfRows[0].bytes -eq $selfInfo.Length -and [string]$selfRows[0].sha256 -ceq (Get-Hash $expectedHost)) 'SELF_PIN'
    Require ([string]$Manifest.toolPaths.pythonExe -ceq $PythonExe -and [string]$Manifest.toolPaths.javaHome -ceq $JavaHome) 'TOOL_CONTEXT'
    $ownerExecutable = Require-CanonicalExistingFile ([string]$owner.Path) 'SELECTED_PS51_REQUIRED'
    Require ((Get-Hash $ownerExecutable) -ceq $selectedPowerShellSha256 -and
        (Test-OrdinalPathEqual $ownerExecutable ([string]$Manifest.toolPaths.powershellExe))) 'SELECTED_PS51_REQUIRED'
    Require ((Get-Hash $PythonExe) -ceq $selectedPythonSha256) 'PYTHON_PIN'
    Require ((Get-Hash (Join-Path $JavaHome 'bin/java.exe')) -ceq $selectedJavaSha256) 'JAVA_PIN'
    Require ((Get-Hash (Join-Path $JavaHome 'bin/javac.exe')) -ceq $selectedJavacSha256) 'JAVAC_PIN'
    Verify-AllManifestExtraPins
}

function Load-AuthenticatedContexts {
    $script:canaryRoot = Require-CanonicalExistingDirectory ([string]$pins.canaryPrerequisite.root) 'CANARY_ROOT'
    $canaryPinsPath = Require-CanonicalExistingFile ([string]$pins.canaryPrerequisite.pinsPath) 'CANARY_PINS_PATH'
    Require ((Get-Hash $canaryPinsPath) -ceq $selectedCanaryPinsSha256) 'CANARY_PINS_HASH'
    $qualificationPath = Require-CanonicalExistingFile ([string]$pins.canaryPrerequisite.qualificationPath) 'CANARY_QUALIFICATION_PATH'
    Require ((Get-Hash $qualificationPath) -ceq $selectedCanaryQualificationSha256) 'CANARY_QUALIFICATION_HASH'
    try { $script:canaryPins = Get-Content -LiteralPath $canaryPinsPath -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop }
    catch { throw 'CANARY_PINS_SCHEMA' }
    Require ([string]$canaryPins.schema -ceq 'tsj-q100-geometric-canary-inputs-v1' -and
        [string]$canaryPins.parent -ceq $sourceParent -and
        [string]$canaryPins.repositoryRoot -ceq $RepositoryRoot) 'CANARY_CONTEXT'
    $p6PinsPath = Require-CanonicalExistingFile ([string]$pins.p6Context.pinsPath) 'P6_PINS_PATH'
    Require ((Get-Hash $p6PinsPath) -ceq $selectedP6PinsSha256 -and
        (Test-OrdinalPathEqual $p6PinsPath ([string]$canaryPins.p6PinsPath))) 'P6_PINS_HASH'
    try { $script:p6Pins = Get-Content -LiteralPath $p6PinsPath -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop }
    catch { throw 'P6_PINS_SCHEMA' }
    Require ([string]$p6Pins.parent -ceq $selectedP6Parent -and
        [string]$p6Pins.schema -ceq 'tsj-canonical-prefix-stage-inputs-v1') 'P6_CONTEXT'
    Require ($p6Pins.sourceRows -is [array] -and $p6Pins.sourceRows.Count -eq $sourceRowsRequired -and
        $canaryPins.sourceRows -is [array] -and $canaryPins.sourceRows.Count -eq $sourceRowsRequired) 'SOURCE_CENSUS'
    $p6RepositoryRoot = Require-CanonicalExistingDirectory ([string]$p6Pins.repositoryRoot) 'P6_CONTEXT'
    Require (-not (Test-OrdinalPathEqual $p6RepositoryRoot $RepositoryRoot) -and
        -not (Test-PathUnder $p6RepositoryRoot $RepositoryRoot) -and -not (Test-PathUnder $RepositoryRoot $p6RepositoryRoot)) 'P6_CONTEXT'
    Require ([string]$p6Pins.bridgeManifest -ceq [string]$canaryPins.bridgeManifest -and
        [string]$p6Pins.bridgeRoot -ceq [string]$canaryPins.bridgeRoot) 'BRIDGE_CONTEXT'
    $script:bridgeManifestPath = Require-CanonicalExistingFile ([string]$p6Pins.bridgeManifest) 'BRIDGE_CONTEXT'
    $script:bridgeRoot = Require-CanonicalExistingDirectory ([string]$p6Pins.bridgeRoot) 'BRIDGE_CONTEXT'
    Require-PinnedFileFromRows $p6Pins.files $bridgeManifestPath 'BRIDGE_CONTEXT' | Out-Null
    $wireRows = @($p6Pins.sourceRows | Where-Object { [string]$_.role -ceq 'WIRE' })
    Require ($wireRows.Count -eq 1) 'WIRE_CONTEXT'
    $wireRow = $wireRows[0]
    Require-ExactKeys $wireRow @('path', 'bytes', 'role', 'sha256') 'WIRE_CONTEXT'
    Require ([string]$wireRow.path -ceq 'docs/task-evidence/Q100/outf-graph-r1/WIRE.md' -and
        [long]$wireRow.bytes -eq 33064 -and [string]$wireRow.role -ceq 'WIRE' -and
        [string]$wireRow.sha256 -ceq '0bd76a4fe4261a8abe5c92b2b0b5f50000077e9541e7ca7e84e9af3ed1661cfb') 'WIRE_CONTEXT'
    $wirePath = [IO.Path]::GetFullPath((Join-Path $p6RepositoryRoot ([string]$wireRow.path)))
    Require (Test-PathUnder $wirePath $p6RepositoryRoot) 'WIRE_CONTEXT'
    $wirePath = Require-CanonicalExistingFile $wirePath 'WIRE_CONTEXT'
    $wireInfo = Get-Item -LiteralPath $wirePath
    Require ($wireInfo.Length -eq [long]$wireRow.bytes -and (Get-Hash $wirePath) -ceq [string]$wireRow.sha256) 'WIRE_CONTEXT'
    $script:wireSha256 = [string]$wireRow.sha256
    $oldWriterPath = [string]$p6Pins.modeFiles.writer
    $maintainedWriterPath = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java'))
    Require (Test-PathUnder $oldWriterPath $p6RepositoryRoot) 'P6_WRITER_PATH'
    $oldWriterPin = @($p6Pins.files | Where-Object { Test-OrdinalPathEqual ([string]$_.path) $oldWriterPath })
    Require ($oldWriterPin.Count -eq 1) 'P6_WRITER_PIN'
    $oldWriterInfo = Get-Item -LiteralPath (Require-CanonicalExistingFile $oldWriterPath 'P6_WRITER_PATH')
    Require ($oldWriterInfo.Length -eq [long]$oldWriterPin[0].bytes -and (Get-Hash $oldWriterPath) -ceq [string]$oldWriterPin[0].sha256) 'P6_WRITER_HASH'
    Require (Test-OrdinalPathEqual ([string]$canaryPins.modeFiles.writer) $maintainedWriterPath) 'ROLE_MAPPING'
    $newWriterRow = @($canaryPins.sourceRows | Where-Object { [string]$_.path -ceq 'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java' })
    Require ($newWriterRow.Count -eq 1 -and [string]$newWriterRow[0].role -ceq 'WRITER' -and
        [long]$newWriterRow[0].bytes -eq [long]$oldWriterPin[0].bytes -and
        [string]$newWriterRow[0].sha256 -ceq [string]$oldWriterPin[0].sha256) 'WRITER_SOURCE'
    $newWriterInfo = Get-Item -LiteralPath (Require-CanonicalExistingFile $maintainedWriterPath 'WRITER_SOURCE')
    Require ($newWriterInfo.Length -eq [long]$newWriterRow[0].bytes -and
        (Get-Hash $maintainedWriterPath) -ceq [string]$newWriterRow[0].sha256) 'WRITER_SOURCE'
    $inventoryPath = Require-CanonicalExistingFile ([string]$pins.sourceInventory.path) 'SOURCE_INVENTORY_PATH'
    $inventoryInfo = Get-Item -LiteralPath $inventoryPath
    Require ($inventoryInfo.Length -le 1048576 -and (Get-Hash $inventoryPath) -ceq $sourceInventorySha256) 'SOURCE_INVENTORY_HASH'
    $raw = [IO.File]::ReadAllBytes($inventoryPath)
    Require ($raw.Length -le 1048576 -and (Get-Hash $inventoryPath) -ceq $sourceInventorySha256) 'SOURCE_INVENTORY_HASH'
    $strictUtf8 = [Text.UTF8Encoding]::new($false, $true)
    try { $text = $strictUtf8.GetString($raw) } catch { throw 'SOURCE_INVENTORY_ENCODING' }
    Require ($text.Length -gt 0 -and $text.EndsWith("`n") -and -not $text.Contains("`r") -and
        -not ($raw.Length -ge 3 -and $raw[0] -eq 239 -and $raw[1] -eq 187 -and $raw[2] -eq 191)) 'SOURCE_INVENTORY_FORMAT'
    $lines = $text.Split([char]10)
    Require ($lines.Count -eq 1087 -and $lines[$lines.Count - 1] -ceq '') 'SOURCE_INVENTORY_CENSUS'
    try { $header = ConvertFrom-Json -InputObject $lines[0] -ErrorAction Stop } catch { throw 'SOURCE_INVENTORY_HEADER' }
    Require-ExactKeys $header @('kind', 'schema', 'sourceParent', 'rowCount', 'productionCount') 'SOURCE_INVENTORY_HEADER'
    Require ([string]$header.kind -ceq 'header' -and [string]$header.schema -ceq 'tsj-q100-geometric-prefix-source-v1' -and
        [string]$header.sourceParent -ceq $sourceParent) 'SOURCE_INVENTORY_HEADER'
    Require-ExactInteger $header.rowCount 1084 1084 'SOURCE_INVENTORY_CENSUS'
    Require-ExactInteger $header.productionCount 850 850 'SOURCE_INVENTORY_CENSUS'
    $rows = @()
    $priorPath = $null
    for ($i = 1; $i -le 1084; $i++) {
        try { $record = ConvertFrom-Json -InputObject $lines[$i] -ErrorAction Stop } catch { throw 'SOURCE_INVENTORY_ROW' }
        Require-ExactKeys $record @('kind', 'path', 'bytes', 'sha256', 'role') 'SOURCE_INVENTORY_ROW'
        Require ([string]$record.kind -ceq 'source' -and [string]$record.path -cmatch '^[A-Za-z0-9_./-]+$' -and
            [string]$record.sha256 -cmatch '^[0-9a-f]{64}$') 'SOURCE_INVENTORY_ROW'
        Require-ExactInteger $record.bytes 1 ([long]::MaxValue) 'SOURCE_INVENTORY_ROW'
        if ($null -ne $priorPath) { Require ([StringComparer]::Ordinal.Compare([string]$priorPath, [string]$record.path) -lt 0) 'SOURCE_INVENTORY_ORDER' }
        $priorPath = [string]$record.path
        $sourceMatches = @($canaryPins.sourceRows | Where-Object { [string]::Equals([string]$_.path, [string]$record.path, [StringComparison]::Ordinal) })
        Require ($sourceMatches.Count -eq 1) 'SOURCE_INVENTORY_ROW_BINDING'
        $sourceRow = $sourceMatches[0]
        Require ([string]$sourceRow.role -ceq [string]$record.role -and [long]$sourceRow.bytes -eq [long]$record.bytes -and
            [string]$sourceRow.sha256 -ceq [string]$record.sha256) 'SOURCE_INVENTORY_ROW_BINDING'
        $rows += @([pscustomobject]@{ path = [string]$record.path; role = [string]$record.role; bytes = [long]$record.bytes; sha256 = [string]$record.sha256 })
    }
    try { $footer = ConvertFrom-Json -InputObject $lines[1085] -ErrorAction Stop } catch { throw 'SOURCE_INVENTORY_FOOTER' }
    Require-ExactKeys $footer @('kind', 'rowCount') 'SOURCE_INVENTORY_FOOTER'
    Require ([string]$footer.kind -ceq 'footer') 'SOURCE_INVENTORY_FOOTER'
    Require-ExactInteger $footer.rowCount 1084 1084 'SOURCE_INVENTORY_FOOTER'
    $script:inventoryRows = $rows
    $script:inventoryBytes = $raw
}

function Parse-P8Preflight([string]$Text, [string]$ExpectedCode, [int]$ExpectedIndex) {
    Require (-not $Text.Contains("`r") -and $Text.EndsWith("`n") -and
        $Text.IndexOf("`n", [StringComparison]::Ordinal) -eq $Text.Length - 1) 'P8_PREFLIGHT_FRAME'
    $value = Parse-OneJsonMarker $Text 'P8_GEOMETRIC_PREFIX_PREFLIGHT_REJECT' 'P8_PREFLIGHT_MARKER'
    Require-ExactKeys $value @('schema', 'phase', 'code', 'argIndex') 'P8_PREFLIGHT_SCHEMA'
    Require ([string]$value.schema -ceq 'tsj-canonical-geometric-prefix-preflight-v1' -and
        [string]$value.phase -ceq 'PREFLIGHT' -and [string]$value.code -ceq $ExpectedCode) 'P8_PREFLIGHT_CODE'
    Require-ExactInteger $value.argIndex $ExpectedIndex $ExpectedIndex 'P8_PREFLIGHT_ARG_INDEX'
    return $value
}

function Parse-MarkerWithPossibleStack([string]$Text, [string]$Prefix, [string]$Code) {
    $pattern = '(?m)^' + [regex]::Escape($Prefix) + ' (?<json>\{[^\r\n]*\})\r?$'
    $matches = [regex]::Matches($Text, $pattern)
    Require ($matches.Count -eq 1) $Code
    try { return (ConvertFrom-Json -InputObject $matches[0].Groups['json'].Value -ErrorAction Stop) }
    catch { throw $Code }
}

function Get-JsonLines([string]$Path, [string]$Code) {
    $bytes = [IO.File]::ReadAllBytes($Path)
    Require ($bytes.Length -le 8388608 -and $bytes.Length -gt 0) $Code
    Require (-not ($bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191)) $Code
    $decoder = [Text.UTF8Encoding]::new($false, $true)
    try { $text = $decoder.GetString($bytes) } catch { throw $Code }
    Require ($text.EndsWith("`n") -and -not $text.Contains("`r")) $Code
    $lines = $text.Split([char]10)
    Require ($lines[$lines.Count - 1] -ceq '') $Code
    $result = @()
    for ($i = 0; $i -lt ($lines.Count - 1); $i++) {
        Require ($lines[$i].Length -gt 0 -and $decoder.GetByteCount($lines[$i] + "`n") -le 65536) $Code
        try { $result += @(ConvertFrom-Json -InputObject $lines[$i] -ErrorAction Stop) } catch { throw $Code }
    }
    return ,$result
}

function Get-GroupRecordCount([string]$Path) {
    $rows = Get-JsonLines $Path 'OPERATION_JSONL'
    $count = 0
    foreach ($row in $rows) { if ([string]$row.record -ceq 'GROUP') { $count++ } }
    Require ($count -ge 0) 'OPERATION_GROUPS'
    return [long]$count
}

function Get-P8FooterUnits([string]$Path, [string]$ModeValue, [string]$RunId) {
    $rows = Get-JsonLines $Path 'REASON_SIDECAR'
    $expectedRows = if ($ModeValue -ceq 'REASON_THROW') { 3 } else { 16 }
    Require ($rows.Count -eq $expectedRows) 'REASON_SIDECAR'
    $footer = $rows[$rows.Count - 1]
    Require-ExactKeys $footer @('kind', 'status', 'complete', 'runId', 'inputSha256', 'sourceSha256', 'mode',
        'parentPrefixSha256', 'parentGraphSha256', 'selectedQueryCount', 'queryCount', 'returnedQueryCount',
        'fineEventCount', 'coarseEventCount', 'priorObservationUnits', 'p8ObservationUnits', 'totalObservationUnits',
        'copyEntries', 'comparisonEntries', 'copyUnits', 'comparisonUnits', 'queryUnits', 'metadataUnits', 'setupUnits',
        'exportUnits', 'copyNanos', 'comparisonNanos', 'queryNanos', 'metadataNanos', 'setupNanos', 'exportNanos',
        'workNanos', 'maximumUnitNanos', 'groupCount', 'outsideQueryWork', 'cleanupKind', 'cleanupComplete',
        'sourcePreserved', 'observersDetached', 'referencesDropped', 'ownersReleased', 'contextRestored') 'REASON_FOOTER_SCHEMA'
    Require ([string]$footer.kind -ceq 'footer' -and [string]$footer.mode -ceq $ModeValue -and
        [string]$footer.runId -ceq $RunId -and [string]$footer.inputSha256 -ceq $InputPinsSha256 -and
        [string]$footer.sourceSha256 -ceq $sourceInventorySha256 -and
        [string]$footer.parentPrefixSha256 -cmatch '^[0-9a-f]{64}$' -and
        [string]$footer.parentGraphSha256 -cmatch '^[0-9a-f]{64}$') 'REASON_FOOTER_BINDING'
    $interrupted = $ModeValue -ceq 'REASON_THROW'
    $expectedStatus = if ($interrupted) { 'EXPECTED_REASON_THROW_ONLY' } elseif ($ModeValue -ceq 'NULL') { 'COMPLETE_NULL_CONTROL_ONLY' } else { 'COMPLETE_REASON_OBSERVATIONS_ONLY' }
    $expectedSelected = if ($interrupted) { 1 } else { 14 }
    $expectedReturned = if ($interrupted) { 0 } else { 14 }
    $expectedFine = if ($interrupted) { 1 } elseif ($ModeValue -ceq 'NULL') { 0 } else { 14 }
    $expectedCoarse = if ($interrupted) { 0 } else { 14 }
    Require ([string]$footer.status -ceq $expectedStatus -and $footer.complete -is [bool] -and
        $footer.complete -eq (-not $interrupted) -and [string]$footer.cleanupKind -ceq 'FULL_NATIVE_WALL_PLUS_POST_SOURCE') 'REASON_FOOTER_STATUS'
    foreach ($flag in @('cleanupComplete', 'sourcePreserved', 'observersDetached', 'referencesDropped', 'ownersReleased', 'contextRestored')) {
        Require ($footer.$flag -is [bool] -and $footer.$flag -eq $true) 'REASON_FOOTER_CLEANUP'
    }
    foreach ($field in @('selectedQueryCount', 'queryCount', 'returnedQueryCount', 'fineEventCount', 'coarseEventCount',
        'priorObservationUnits', 'p8ObservationUnits', 'totalObservationUnits', 'copyEntries', 'comparisonEntries',
        'copyUnits', 'comparisonUnits', 'queryUnits', 'metadataUnits', 'setupUnits', 'exportUnits', 'copyNanos',
        'comparisonNanos', 'queryNanos', 'metadataNanos', 'setupNanos', 'exportNanos', 'workNanos', 'maximumUnitNanos', 'groupCount')) {
        Require-ExactInteger $footer.$field 0 ([long]::MaxValue) 'REASON_FOOTER_COUNTER'
    }
    Require ($footer.selectedQueryCount -eq $expectedSelected -and $footer.queryCount -eq $expectedSelected -and
        $footer.returnedQueryCount -eq $expectedReturned -and $footer.fineEventCount -eq $expectedFine -and
        $footer.coarseEventCount -eq $expectedCoarse -and $footer.queryUnits -eq $expectedSelected) 'REASON_FOOTER_COUNTS'
    Require-ExactKeys $footer.outsideQueryWork @('copyEntries', 'comparisonEntries', 'copyUnits', 'comparisonUnits',
        'queryUnits', 'metadataUnits', 'setupUnits', 'exportUnits', 'copyNanos', 'comparisonNanos', 'queryNanos',
        'metadataNanos', 'setupNanos', 'exportNanos', 'maximumUnitNanos', 'groupCount') 'REASON_OUTSIDE_WORK_SCHEMA'
    foreach ($field in @('copyEntries', 'comparisonEntries', 'copyUnits', 'comparisonUnits', 'queryUnits', 'metadataUnits',
        'setupUnits', 'exportUnits', 'copyNanos', 'comparisonNanos', 'queryNanos', 'metadataNanos', 'setupNanos',
        'exportNanos', 'maximumUnitNanos', 'groupCount')) {
        Require-ExactInteger $footer.outsideQueryWork.$field 0 ([long]::MaxValue) 'REASON_OUTSIDE_WORK_COUNTER'
    }
    Require ($footer.outsideQueryWork.queryUnits -eq 0) 'REASON_OUTSIDE_WORK_QUERY_UNITS'
    $units = [decimal]$footer.copyUnits + [decimal]$footer.comparisonUnits + [decimal]$footer.queryUnits +
        [decimal]$footer.metadataUnits + [decimal]$footer.setupUnits + [decimal]$footer.exportUnits
    $nanos = [decimal]$footer.copyNanos + [decimal]$footer.comparisonNanos + [decimal]$footer.queryNanos +
        [decimal]$footer.metadataNanos + [decimal]$footer.setupNanos + [decimal]$footer.exportNanos
    Require ($units -eq [decimal]$footer.p8ObservationUnits -and
        [decimal]$footer.priorObservationUnits + $units -eq [decimal]$footer.totalObservationUnits -and
        $footer.totalObservationUnits -le 32768 -and $nanos -eq [decimal]$footer.workNanos -and
        $footer.workNanos -le 90000000000 -and [decimal]$footer.groupCount -eq $units -and $footer.maximumUnitNanos -le 5000000000) 'REASON_FOOTER_ARITHMETIC'
    return [long]$footer.totalObservationUnits
}

function Require-ExactDataFiles([string]$Directory, [string[]]$ExpectedNames, [string]$Code) {
    $actual = @([IO.Directory]::GetFiles($Directory, '*', [IO.SearchOption]::TopDirectoryOnly) | ForEach-Object { [IO.Path]::GetFileName($_) })
    $actualDirs = [IO.Directory]::GetDirectories($Directory, '*', [IO.SearchOption]::TopDirectoryOnly)
    Require ($actualDirs.Count -eq 0 -and $actual.Count -eq $ExpectedNames.Count) $Code
    foreach ($name in $ExpectedNames) { Require (@($actual | Where-Object { [string]::Equals($_, $name, [StringComparison]::Ordinal) }).Count -eq 1) $Code }
}

function Invoke-NativeChild([string]$FilePath, [string[]]$Arguments, [string]$CallPhase) {
    $call = Invoke-BoundedChild -FilePath $FilePath -Arguments $Arguments -TimeoutMilliseconds 480000 -CallPhase $CallPhase
    Require ($call.wallMs -ge 0 -and $call.wallMs -le 480000) ($CallPhase + '_WALL_LIMIT')
    $script:childWallSumMs = [double]$script:childWallSumMs + [double]$call.wallMs
    return $call
}

function Save-ProcessEvidenceAt([string]$ReceiptName, [string]$StdoutName, [string]$StderrName, $ProcessResult) {
    Require ($ProcessResult.TerminationProven -eq $true) 'PROCESS_TERMINATION_UNPROVEN'
    Require-ExactInteger $ProcessResult.ProcessId 1 ([int]::MaxValue) 'PROCESS_ID_INVALID'
    Require-ExactInteger $ProcessResult.ProcessStartTicks 1 ([long]::MaxValue) 'PROCESS_START_INVALID'
    Test-VerifierLogsRemoved $ProcessResult
    $receiptPath = Join-Path $outputCanonical ($ReceiptName.Replace('/', [IO.Path]::DirectorySeparatorChar))
    $stdoutPath = Join-Path $outputCanonical ($StdoutName.Replace('/', [IO.Path]::DirectorySeparatorChar))
    $stderrPath = Join-Path $outputCanonical ($StderrName.Replace('/', [IO.Path]::DirectorySeparatorChar))
    Write-NewJson $receiptPath $ProcessResult
    $stdoutBytes = $utf8.GetBytes([string]$ProcessResult.Stdout)
    $stderrBytes = $utf8.GetBytes([string]$ProcessResult.Stderr)
    Require ([long]$stdoutBytes.Length + [long]$stderrBytes.Length -le 8388608) 'PROCESS_CAPTURE_LIMIT'
    Write-NewBytes $stdoutPath $stdoutBytes
    Write-NewBytes $stderrPath $stderrBytes
    Require (Test-ByteEquality $stdoutBytes ([IO.File]::ReadAllBytes($stdoutPath))) 'STDOUT_COPY_MISMATCH'
    Require (Test-ByteEquality $stderrBytes ([IO.File]::ReadAllBytes($stderrPath))) 'STDERR_COPY_MISMATCH'
    return [ordered]@{ receipt = $ReceiptName; stdout = $StdoutName; stderr = $StderrName }
}

function Get-FailureEvidenceErrorText($Exception) {
    $typeName = 'System.Exception'
    $message = 'No error message was supplied.'
    if ($null -ne $Exception) {
        $typeName = [string]$Exception.GetType().FullName
        $message = [string]$Exception.Message
        if ([string]::IsNullOrWhiteSpace($message)) { $message = 'No error message was supplied.' }
    }
    if ($message.Length -gt 1024) { $message = $message.Substring(0, 1024) + '...[truncated]' }
    return ($typeName + ': ' + $message)
}

function ConvertTo-FailureJsonBytes($Value, [string]$Code) {
    $json = ConvertTo-Json -InputObject $Value -Depth 40 -Compress
    $bytes = $utf8.GetBytes($json + "`n")
    Require ($bytes.Length -le 65536) $Code
    return ,$bytes
}

function Write-FailureBundleFile([string]$BundleRoot, [string]$RelativePath, [byte[]]$Bytes,
    [long]$CurrentTotalBytes, [switch]$Structured) {
    Require ($null -ne $Bytes) 'FAILURE_EVIDENCE_BYTES'
    Require (-not [IO.Path]::IsPathRooted($RelativePath) -and $RelativePath -cmatch '^[A-Za-z0-9._/-]+$' -and
        $RelativePath -notmatch '(^|/)\.\.?(/|$)') 'FAILURE_EVIDENCE_PATH'
    $length = [long]$Bytes.LongLength
    Require ($length -le 134217728) 'FAILURE_EVIDENCE_FILE_LIMIT'
    Require ($CurrentTotalBytes -ge 0 -and ([decimal]$CurrentTotalBytes + [decimal]$length) -le 268435456) 'FAILURE_EVIDENCE_TOTAL_LIMIT'
    if ($Structured) { Require ($length -le 65536) 'FAILURE_EVIDENCE_STRUCTURED_LIMIT' }
    $canonicalRoot = [IO.Path]::GetFullPath($BundleRoot)
    Require (Test-OrdinalPathEqual $canonicalRoot $BundleRoot) 'FAILURE_EVIDENCE_ROOT'
    $platformRelative = $RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar)
    $targetPath = [IO.Path]::GetFullPath((Join-Path $canonicalRoot $platformRelative))
    Require ((Test-PathUnder $targetPath $canonicalRoot) -and -not (Test-OrdinalPathEqual $targetPath $canonicalRoot)) 'FAILURE_EVIDENCE_PATH'
    Assert-LocalNoReparseAncestors $canonicalRoot
    $parentPath = [IO.Path]::GetDirectoryName($targetPath)
    Require (Test-PathUnder $parentPath $canonicalRoot) 'FAILURE_EVIDENCE_PATH'
    [void][IO.Directory]::CreateDirectory($parentPath)
    Assert-LocalNoReparseAncestors $parentPath
    Require (($targetPath -ceq [IO.Path]::GetFullPath($targetPath)) -and
        (Test-OrdinalPathEqual $targetPath ([IO.Path]::GetFullPath($targetPath)))) 'FAILURE_EVIDENCE_PATH'
    $stream = $null
    try {
        $stream = [IO.File]::Open($targetPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
        if ($Bytes.Length -gt 0) { $stream.Write($Bytes, 0, $Bytes.Length) }
        $stream.Flush()
    }
    finally {
        if ($null -ne $stream) { $stream.Dispose() }
    }
    Assert-LocalNoReparseAncestors $targetPath
    $item = Get-Item -LiteralPath $targetPath -Force
    Require (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0 -and $item.Length -eq $length) 'FAILURE_EVIDENCE_FILE_IDENTITY'
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { $expectedSha256 = [BitConverter]::ToString($hasher.ComputeHash($Bytes)).Replace('-', '').ToLowerInvariant() }
    finally { $hasher.Dispose() }
    $actualSha256 = Get-Hash $targetPath
    Require ($actualSha256 -ceq $expectedSha256) 'FAILURE_EVIDENCE_FILE_HASH'
    return [ordered]@{ path = $RelativePath; bytes = $length; sha256 = $actualSha256 }
}

function Save-FailureProcessBundle {
    $errors = New-Object 'System.Collections.Generic.List[string]'
    $captureRows = New-Object 'System.Collections.Generic.List[object]'
    $bundleRoot = $null
    $creationTicks = $null
    $ownerRef = $null
    $manifestSha256 = $null
    $manifestPath = 'manifest.json'
    [long]$totalBytes = 0
    $complete = $false
    $tempRoot = $null

    try {
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        Require (Test-Path -LiteralPath $tempRoot -PathType Container) 'FAILURE_EVIDENCE_TEMP_ROOT'
        Require (Test-OrdinalPathEqual $tempRoot ([IO.Path]::GetFullPath($tempRoot))) 'FAILURE_EVIDENCE_TEMP_ROOT'
        Assert-LocalNoReparseAncestors $tempRoot
        $protectedRoots = @()
        if (-not [string]::IsNullOrWhiteSpace([string]$outputCanonical)) { $protectedRoots += @([string]$outputCanonical) }
        if (-not [string]::IsNullOrWhiteSpace([string]$RepositoryRoot)) { $protectedRoots += @([string]$RepositoryRoot) }
        if (-not [string]::IsNullOrWhiteSpace([string]$InputPinsPath)) {
            $protectedRoots += @([string]$InputPinsPath, [IO.Path]::GetDirectoryName([string]$InputPinsPath))
        }
        if (-not [string]::IsNullOrWhiteSpace([string]$PSScriptRoot)) { $protectedRoots += @([string]$PSScriptRoot) }
        for ($attempt = 0; $attempt -lt 8 -and $null -eq $bundleRoot; $attempt++) {
            $candidateRoot = [IO.Path]::GetFullPath((Join-Path $tempRoot ('tsj-p8-prefix-failure-r1-' + [Guid]::NewGuid().ToString('N').ToLowerInvariant())))
            Require (([IO.Path]::GetFileName($candidateRoot)) -cmatch '^tsj-p8-prefix-failure-r1-[0-9a-f]{32}$') 'FAILURE_EVIDENCE_ROOT'
            Require (Test-PathUnder $candidateRoot $tempRoot) 'FAILURE_EVIDENCE_TEMP_ROOT'
            Require (-not (Test-OrdinalPathEqual $candidateRoot $tempRoot)) 'FAILURE_EVIDENCE_TEMP_ROOT'
            foreach ($protectedRoot in $protectedRoots) {
                $canonicalProtectedRoot = [IO.Path]::GetFullPath([string]$protectedRoot)
                Require (-not (Test-PathUnder $candidateRoot $canonicalProtectedRoot) -and
                    -not (Test-PathUnder $canonicalProtectedRoot $candidateRoot)) 'FAILURE_EVIDENCE_ROOT_OVERLAP'
            }
            if (Test-Path -LiteralPath $candidateRoot) { continue }
            [void][IO.Directory]::CreateDirectory($candidateRoot)
            $bundleRoot = [IO.Path]::GetFullPath($candidateRoot)
        }
        Require ($null -ne $bundleRoot) 'FAILURE_EVIDENCE_ROOT_CREATE'
        Assert-LocalNoReparseAncestors $bundleRoot
        $rootItem = Get-Item -LiteralPath $bundleRoot -Force
        Require ((Test-OrdinalPathEqual $bundleRoot ([IO.Path]::GetFullPath($rootItem.FullName))) -and
            (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0)) 'FAILURE_EVIDENCE_ROOT_IDENTITY'
        $creationTicks = [string]$rootItem.CreationTimeUtc.Ticks
        Require ($creationTicks -match '^[0-9]+$' -and [long]$creationTicks -gt 0) 'FAILURE_EVIDENCE_ROOT_IDENTITY'
    }
    catch {
        $errors.Add(('bundle root: ' + (Get-FailureEvidenceErrorText $_.Exception)))
    }

    if ($null -ne $bundleRoot) {
        try {
            $bundleOwner = Get-Process -Id $PID -ErrorAction Stop
            Require ($bundleOwner.Id -eq $PID -and $null -ne $bundleOwner.Path -and
                (Test-Path -LiteralPath $bundleOwner.Path -PathType Leaf)) 'FAILURE_EVIDENCE_OWNER'
            $hostExecutable = [IO.Path]::GetFullPath([string]$bundleOwner.Path)
            Require (Test-OrdinalPathEqual $hostExecutable ([string]$bundleOwner.Path)) 'FAILURE_EVIDENCE_OWNER'
            $hostStartTicksValue = [string]$bundleOwner.StartTime.ToUniversalTime().Ticks
            Require ($hostStartTicksValue -match '^[0-9]+$' -and [long]$hostStartTicksValue -gt 0) 'FAILURE_EVIDENCE_OWNER'
            $hostScriptPath = [IO.Path]::GetFullPath([string]$PSCommandPath)
            Require ((Test-OrdinalPathEqual $hostScriptPath ([string]$PSCommandPath)) -and
                (Test-Path -LiteralPath $hostScriptPath -PathType Leaf)) 'FAILURE_EVIDENCE_OWNER'
            Assert-LocalNoReparseAncestors $hostExecutable
            Assert-LocalNoReparseAncestors $hostScriptPath
            Require ([string]$InputPinsSha256 -cmatch '^[0-9a-f]{64}$') 'FAILURE_EVIDENCE_OWNER'
            $ownerRecord = [ordered]@{
                schema = 'tsj-q100-prefix-failed-process-owner-v1'
                hostPid = [int]$PID
                hostStartTicks = $hostStartTicksValue
                hostExecutable = $hostExecutable
                hostScriptSha256 = Get-Hash $hostScriptPath
                inputSha256 = [string]$InputPinsSha256
                bundleRoot = $bundleRoot
                creationTicks = $creationTicks
            }
            $ownerBytes = ConvertTo-FailureJsonBytes $ownerRecord 'FAILURE_EVIDENCE_OWNER_SIZE'
            $ownerRef = Write-FailureBundleFile $bundleRoot 'owner.json' $ownerBytes $totalBytes -Structured
            $totalBytes += [long]$ownerRef.bytes
        }
        catch {
            $errors.Add(('owner.json: ' + (Get-FailureEvidenceErrorText $_.Exception)))
            $ownerRef = $null
        }

        for ($index = 0; $index -lt $registeredProcessCaptures.Count; $index++) {
            $registered = $registeredProcessCaptures[$index]
            $ordinal = [int]$registered.ordinal
            $capturePhase = [string]$registered.phase
            $processResult = $registered.process
            $receiptRef = $null
            $stdoutRef = $null
            $stderrRef = $null
            $captureErrors = New-Object 'System.Collections.Generic.List[string]'
            $captureDirectory = Join-Path $bundleRoot ('calls/' + $ordinal.ToString('D4', [Globalization.CultureInfo]::InvariantCulture))
            try {
                Require ((Test-PathUnder ([IO.Path]::GetFullPath($captureDirectory)) $bundleRoot) -and
                    -not (Test-OrdinalPathEqual ([IO.Path]::GetFullPath($captureDirectory)) $bundleRoot)) 'FAILURE_EVIDENCE_PATH'
                [void][IO.Directory]::CreateDirectory($captureDirectory)
                Assert-LocalNoReparseAncestors $captureDirectory
                $captureDirectoryItem = Get-Item -LiteralPath $captureDirectory -Force
                Require (($captureDirectoryItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'FAILURE_EVIDENCE_PATH'
            }
            catch {
                $captureErrors.Add(('capture directory: ' + (Get-FailureEvidenceErrorText $_.Exception)))
            }
            if ($null -eq $processResult) {
                $captureErrors.Add('receipt.json: returned process receipt is missing.')
            }
            else {
                try {
                    $receiptJson = ConvertTo-Json -InputObject $processResult -Depth 40 -Compress
                    $receiptBytes = $utf8.GetBytes($receiptJson + "`n")
                    $receiptRef = Write-FailureBundleFile $bundleRoot ('calls/' + $ordinal.ToString('D4', [Globalization.CultureInfo]::InvariantCulture) + '/receipt.json') $receiptBytes $totalBytes
                    $totalBytes += [long]$receiptRef.bytes
                }
                catch {
                    $captureErrors.Add(('receipt.json: ' + (Get-FailureEvidenceErrorText $_.Exception)))
                }
                foreach ($streamName in @('stdout', 'stderr')) {
                    $propertyName = if ($streamName -eq 'stdout') { 'Stdout' } else { 'Stderr' }
                    $streamProperty = $processResult.PSObject.Properties[$propertyName]
                    if ($null -eq $streamProperty -or $streamProperty.Value -isnot [string]) {
                        $captureErrors.Add(($streamName + '.utf8: returned stream string is missing.'))
                        continue
                    }
                    try {
                        $streamBytes = $utf8.GetBytes([string]$streamProperty.Value)
                        $streamPath = 'calls/' + $ordinal.ToString('D4', [Globalization.CultureInfo]::InvariantCulture) + '/' + $streamName + '.utf8'
                        $streamRef = Write-FailureBundleFile $bundleRoot $streamPath $streamBytes $totalBytes
                        $totalBytes += [long]$streamRef.bytes
                        if ($streamName -eq 'stdout') { $stdoutRef = $streamRef } else { $stderrRef = $streamRef }
                    }
                    catch {
                        $captureErrors.Add(($streamName + '.utf8: ' + (Get-FailureEvidenceErrorText $_.Exception)))
                    }
                }
            }
            $captureSaved = ($null -ne $receiptRef -and $null -ne $stdoutRef -and $null -ne $stderrRef -and $captureErrors.Count -eq 0)
            $captureError = if ($captureErrors.Count -eq 0) { $null } else { [string]::Join('; ', $captureErrors.ToArray()) }
            if (-not $captureSaved) {
                $errors.Add(('calls/' + $ordinal.ToString('D4', [Globalization.CultureInfo]::InvariantCulture) + ': ' + [string]$captureError))
            }
            $captureRows.Add([ordered]@{
                ordinal = $ordinal
                phase = $capturePhase
                receipt = $receiptRef
                stdout = $stdoutRef
                stderr = $stderrRef
                status = if ($captureSaved) { 'SAVED' } else { 'WRITE_FAILED' }
                error = $captureError
            })
        }

        try {
            Assert-LocalNoReparseAncestors $bundleRoot
            $identityCheck = Get-Item -LiteralPath $bundleRoot -Force
            Require ((Test-OrdinalPathEqual $bundleRoot ([IO.Path]::GetFullPath($identityCheck.FullName))) -and
                (($identityCheck.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) -and
                ([string]$identityCheck.CreationTimeUtc.Ticks -ceq $creationTicks)) 'FAILURE_EVIDENCE_ROOT_IDENTITY'
        }
        catch {
            $errors.Add(('bundle identity: ' + (Get-FailureEvidenceErrorText $_.Exception)))
        }
    }

    $complete = ($null -ne $bundleRoot -and $null -ne $ownerRef -and $errors.Count -eq 0 -and
        $captureRows.Count -eq $registeredProcessCaptures.Count)
    $manifestSha256 = $null
    if ($null -ne $bundleRoot) {
        $manifestError = if ($errors.Count -eq 0) { $null } else {
            $includedErrors = @($errors | Select-Object -First 12)
            $joinedErrors = [string]::Join('; ', $includedErrors)
            if ($errors.Count -gt 12) { $joinedErrors += '; additional preservation errors are recorded on capture rows.' }
            if ($joinedErrors.Length -gt 8192) { $joinedErrors = $joinedErrors.Substring(0, 8192) + '...[truncated]' }
            $joinedErrors
        }
        $manifest = [ordered]@{
            schema = 'tsj-q100-prefix-failed-process-bundle-v1'
            status = if ($complete) { 'PRESERVED_FAILED_PROCESS_EVIDENCE_ONLY' } else { 'FAILED_TO_PRESERVE_ALL_PROCESS_EVIDENCE' }
            complete = [bool]$complete
            owner = $ownerRef
            captures = [object[]]$captureRows.ToArray()
            totalBytes = [long]$totalBytes
            error = $manifestError
        }
        try {
            $manifestBytes = ConvertTo-FailureJsonBytes $manifest 'FAILURE_EVIDENCE_MANIFEST_SIZE'
            $manifestRef = Write-FailureBundleFile $bundleRoot $manifestPath $manifestBytes $totalBytes -Structured
            $manifestSha256 = [string]$manifestRef.sha256
        }
        catch {
            $errors.Add(('manifest.json: ' + (Get-FailureEvidenceErrorText $_.Exception)))
        }
    }
    if ($null -eq $manifestSha256) { $complete = $false }
    $writeError = $null
    if ($errors.Count -gt 0) {
        $writeError = [string]::Join('; ', @($errors | Select-Object -First 12))
        if ($errors.Count -gt 12) { $writeError += '; additional preservation errors are recorded on capture rows.' }
        if ($writeError.Length -gt 8192) { $writeError = $writeError.Substring(0, 8192) + '...[truncated]' }
    }
    $script:failureEvidenceWriteError = $writeError
    $script:failureBundleResult = [ordered]@{
        root = $bundleRoot
        manifestPath = if ($null -ne $bundleRoot) { $manifestPath } else { $null }
        manifestSha256 = $manifestSha256
        complete = [bool]$complete
        writeError = $writeError
    }
}

function Get-ProcessRecord([string]$Receipt, [string]$Stdout, [string]$Stderr, [double]$WallMs) {
    Require ($WallMs -ge 0 -and -not [double]::IsNaN($WallMs) -and -not [double]::IsInfinity($WallMs)) 'PROCESS_WALL'
    return [ordered]@{ receipt = $Receipt; stdout = $Stdout; stderr = $Stderr; wallMs = [double]$WallMs }
}
function Parse-P6Canary([string]$Stdout) {
    $summary = Parse-OneJsonMarker $Stdout 'CANONICAL_PREFIX_STAGE_CANARY_COMPLETE' 'P6_CANARY_MARKER' -AllowPreamble
    Require-ExactKeys $summary @('schema', 'status', 'assertions', 'blockerAssertions', 'stageAssertions',
        'elapsedNanos', 'groups', 'maximumGroupNanos', 'layoutQualification') 'P6_CANARY_SCHEMA'
    Require ([string]$summary.schema -ceq 'tsj-canonical-prefix-stage-canary-v1' -and
        [string]$summary.status -ceq 'PASS') 'P6_CANARY_STATUS'
    Require-ExactInteger $summary.assertions 1 ([long]::MaxValue) 'P6_CANARY_ASSERTIONS'
    Require-ExactInteger $summary.blockerAssertions 1 ([long]::MaxValue) 'P6_CANARY_ASSERTIONS'
    Require-ExactInteger $summary.stageAssertions 1 ([long]::MaxValue) 'P6_CANARY_ASSERTIONS'
    Require-ExactInteger $summary.elapsedNanos 0 90000000000 'P6_CANARY_WORK'
    Require-ExactInteger $summary.groups 1 640 'P6_CANARY_GROUPS'
    Require-ExactInteger $summary.maximumGroupNanos 0 5000000000 'P6_CANARY_GROUP'
    Require ($summary.layoutQualification -is [bool] -and $summary.layoutQualification -eq $false) 'P6_CANARY_SCOPE'
    return $summary
}

function Parse-P8Canary([string]$Stdout) {
    Require (-not $Stdout.Contains("`r") -and $Stdout.EndsWith("`n") -and
        $Stdout.IndexOf("`n", [StringComparison]::Ordinal) -eq $Stdout.Length - 1) 'P8_CANARY_LINE_ENDING'
    $summary = Parse-OneJsonMarker $Stdout 'P8_GEOMETRIC_REASON_CANARY_COMPLETE' 'P8_CANARY_MARKER'
    Require-ExactKeys $summary @('schema', 'status', 'assertions', 'caseCount', 'workNanos', 'groups',
        'maximumGroupNanos', 'totalObservationUnits', 'cases', 'layoutQualification') 'P8_CANARY_SCHEMA'
    Require ([string]$summary.schema -ceq 'tsj-q100-geometric-reason-canary-v1' -and
        [string]$summary.status -ceq 'PASS') 'P8_CANARY_STATUS'
    Require-ExactInteger $summary.assertions 1 ([long]::MaxValue) 'P8_CANARY_ASSERTIONS'
    Require-ExactInteger $summary.caseCount 1 ([int]::MaxValue) 'P8_CANARY_CASES'
    Require-ExactInteger $summary.workNanos 0 90000000000 'P8_CANARY_WORK'
    Require-ExactInteger $summary.groups 0 640 'P8_CANARY_GROUPS'
    Require-ExactInteger $summary.maximumGroupNanos 0 5000000000 'P8_CANARY_GROUP'
    Require-ExactInteger $summary.totalObservationUnits 0 32768 'P8_CANARY_UNITS'
    Require ($summary.layoutQualification -is [bool] -and $summary.layoutQualification -eq $false) 'P8_CANARY_SCOPE'
    Require ($summary.cases -is [array] -and $summary.caseCount -eq $summary.cases.Count) 'P8_CANARY_CASES'
    $ids = @{}
    $coveredSites = @{}
    $allowedScopes = @('SYNTHETIC_ROUTER_LITERAL', 'INVALID_RULES_INPUT', 'RULES_BOUNDARY_LITERAL', 'OBSERVER_LIFECYCLE')
    $allowedSites = @('START_ESCAPE_DIRECTION', 'END_ESCAPE_DIRECTION', 'DOMAIN_UNKNOWN_NET',
        'DOMAIN_CORRIDOR_OVERLAP', 'DOMAIN_MEMBERSHIP_SIDE', 'EMPTY_FACE_RESERVATION',
        'ADJACENT_RESERVATION', 'PAD_ESCAPE_INTERSECTION', 'PROFILED_PAD_TOUCH', 'COMPONENT_COURTYARD')
    foreach ($case in $summary.cases) {
        Require-ExactKeys $case @('id', 'status', 'scope', 'expectedSite', 'actualSites', 'fineEvents',
            'coarseEvents', 'sourcePreserved', 'observersDetached', 'referencesDropped') 'P8_CANARY_CASE_SCHEMA'
        Require ($case.id -is [string] -and -not [string]::IsNullOrWhiteSpace([string]$case.id)) 'P8_CANARY_CASE_ID'
        Require (-not $ids.ContainsKey([string]$case.id)) 'P8_CANARY_CASE_ID'
        $ids.Add([string]$case.id, $true)
        Require ([string]$case.status -ceq 'PASS') 'P8_CANARY_CASE_STATUS'
        Require ($case.scope -is [string] -and $case.scope -cin $allowedScopes) 'P8_CANARY_CASE_SCOPE'
        Require ($null -eq $case.expectedSite -or ($case.expectedSite -is [string] -and $case.expectedSite -cin $allowedSites)) 'P8_CANARY_SITE'
        Require ($case.actualSites -is [array]) 'P8_CANARY_SITE'
        foreach ($site in $case.actualSites) {
            Require ($site -is [string] -and $site -cin $allowedSites) 'P8_CANARY_SITE'
            $coveredSites[[string]$site] = $true
        }
        Require-ExactInteger $case.fineEvents 0 ([int]::MaxValue) 'P8_CANARY_EVENTS'
        Require-ExactInteger $case.coarseEvents 0 ([int]::MaxValue) 'P8_CANARY_EVENTS'
        Require ($case.sourcePreserved -is [bool] -and $case.sourcePreserved -eq $true) 'P8_CANARY_CLEANUP'
        Require ($case.observersDetached -is [bool] -and $case.observersDetached -eq $true) 'P8_CANARY_CLEANUP'
        Require ($case.referencesDropped -is [bool] -and $case.referencesDropped -eq $true) 'P8_CANARY_CLEANUP'
    }
    Require ($summary.caseCount -eq 31) 'P8_CANARY_CASE_LEDGER'
    foreach ($site in $allowedSites) { Require ($coveredSites.ContainsKey($site)) 'P8_CANARY_SITE_COVERAGE' }
    return [pscustomobject]@{
        schema = [string]$summary.schema
        status = [string]$summary.status
        assertions = [long]$summary.assertions
        caseCount = [long]$summary.caseCount
        workNanos = [long]$summary.workNanos
        groups = [long]$summary.groups
        maximumGroupNanos = [long]$summary.maximumGroupNanos
        totalObservationUnits = [long]$summary.totalObservationUnits
        layoutQualification = $false
    }
}

function Verify-BridgeCopies {
    foreach ($copy in $bridgeCopies) {
        Require (Test-Path -LiteralPath $copy.path -PathType Leaf) 'COPIED_BRIDGE_MISSING'
        $item = Get-Item -LiteralPath $copy.path
        Require ($item.Length -eq [long]$copy.bytes -and (Get-Hash ([string]$copy.path)) -ceq [string]$copy.sha256) 'COPIED_BRIDGE_HASH'
        Assert-VerifierNoReparseAncestors ([string]$copy.path)
    }
}

function Get-OutputPath([string]$RelativePath) {
    return Join-Path $outputCanonical ($RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar))
}

function Ensure-OutputDirectories {
    foreach ($relative in $allowedDirectories) {
        $path = Get-OutputPath $relative
        if (-not (Test-Path -LiteralPath $path)) { [void][IO.Directory]::CreateDirectory($path) }
        Assert-VerifierNoReparseAncestors $path
    }
}

function Get-OrdinalSortedStrings([string[]]$Values) {
    $copy = [string[]]@($Values)
    [Array]::Sort($copy, [StringComparer]::Ordinal)
    return ,$copy
}

function Get-TreePaths {
    $fileNames = New-Object 'System.Collections.Generic.List[string]'
    $directoryNames = New-Object 'System.Collections.Generic.List[string]'
    $pending = New-Object 'System.Collections.Generic.Stack[string]'
    $pending.Push($outputCanonical)
    while ($pending.Count -gt 0) {
        $current = $pending.Pop()
        foreach ($directory in [IO.Directory]::GetDirectories($current, '*', [IO.SearchOption]::TopDirectoryOnly)) {
            $item = Get-Item -LiteralPath $directory -Force
            Require (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'OUTPUT_REPARSE'
            $relative = $directory.Substring($outputCanonical.Length).TrimStart('\', '/').Replace('\', '/')
            [void]$directoryNames.Add($relative)
            $pending.Push($directory)
        }
        foreach ($file in [IO.Directory]::GetFiles($current, '*', [IO.SearchOption]::TopDirectoryOnly)) {
            $item = Get-Item -LiteralPath $file -Force
            Require (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'OUTPUT_REPARSE'
            $relative = $file.Substring($outputCanonical.Length).TrimStart('\', '/').Replace('\', '/')
            [void]$fileNames.Add($relative)
        }
    }
    return [pscustomobject]@{ files = $fileNames.ToArray(); directories = $directoryNames.ToArray() }
}

function Get-OutputClosure {
    $tree = Get-TreePaths
    $actualFiles = Get-OrdinalSortedStrings $tree.files
    $expectedFiles = Get-OrdinalSortedStrings $outputFiles
    $actualDirectories = Get-OrdinalSortedStrings $tree.directories
    $expectedDirectories = Get-OrdinalSortedStrings $allowedDirectories
    Require ($actualFiles.Count -eq $expectedFiles.Count -and $actualDirectories.Count -eq $expectedDirectories.Count) 'OUTPUT_CENSUS'
    for ($i = 0; $i -lt $expectedFiles.Count; $i++) {
        Require ([string]::Equals($actualFiles[$i], $expectedFiles[$i], [StringComparison]::Ordinal)) 'OUTPUT_CENSUS'
    }
    for ($i = 0; $i -lt $expectedDirectories.Count; $i++) {
        Require ([string]::Equals($actualDirectories[$i], $expectedDirectories[$i], [StringComparison]::Ordinal)) 'OUTPUT_CENSUS'
    }
    $rows = @()
    [long]$total = 0
    foreach ($name in $expectedFiles) {
        $path = Get-OutputPath $name
        Assert-VerifierNoReparseAncestors $path
        $info = Get-Item -LiteralPath $path -Force
        if ($name -match '\.json$') { Require ($info.Length -le 65536) 'STRUCTURED_OUTPUT_LIMIT' }
        if ($name -eq 'compiler-sources.txt') { Require ($info.Length -le 1048576) 'COMPILER_LIST_LIMIT' }
        if ($name -like '*.jsonl') { [void](Get-JsonLines $path 'JSONL_OUTPUT_INVALID') }
        $rows += @([ordered]@{ name = $name; bytes = [long]$info.Length; sha256 = Get-Hash $path })
        $total += [long]$info.Length
    }
    Require ($total -eq [long]$script:writtenOutputBytes) 'OUTPUT_CENSUS'
    Require ($total -le 41943040) 'SUITE_OUTPUT_LIMIT'
    return [ordered]@{ files = $rows; directories = @($expectedDirectories); aggregateBytes = $total; hostResultExcluded = $true }
}

function Get-ArtifactRoleAndOperation([string]$Name) {
    if ($Name -ceq 'controls/P8_NONEMPTY_DATA/data/sentinel.txt') { return [pscustomobject]@{ role = 'CONTROL_REPORT'; operation = 'P8_NONEMPTY_DATA' } }
    if ($Name -match '^runs/(NULL|OBSERVE|CALLBACK_THROW|REASON_THROW)\.(stdout|stderr)$') {
        $operation = $Matches[1]
        if ($Matches[2] -ceq 'stdout') { return [pscustomobject]@{ role = 'STDOUT'; operation = $operation } }
        return [pscustomobject]@{ role = 'STDERR'; operation = $operation }
    }
    if ($Name.StartsWith('runs/', [StringComparison]::Ordinal)) {
        $parts = $Name.Split('/')
        Require ($parts.Count -ge 3 -and $parts[0] -ceq 'runs') 'OUTPUT_ROLE'
        $operation = $parts[1]
        Require ($operation -cin @('NULL', 'OBSERVE', 'CALLBACK_THROW', 'REASON_THROW')) 'OUTPUT_ROLE'
        if ($parts[2] -ceq 'launch-intent.json') { return [pscustomobject]@{ role = 'HOST_INTENT'; operation = $operation } }
        if ($parts[2] -ceq 'process.json') { return [pscustomobject]@{ role = 'PROCESS_RECEIPT'; operation = $operation } }
        if ($parts[2] -ceq 'data') {
            if ($parts.Count -eq 4 -and ($parts[3] -ceq 'canonical-stage-index.json' -or $parts[3] -ceq 'canonical-geometric-prefix-index.json')) {
                return [pscustomobject]@{ role = 'MODE_INDEX'; operation = $operation }
            }
            if ($parts.Count -eq 4) { return [pscustomobject]@{ role = 'NATIVE_DATA'; operation = $operation } }
        }
        throw 'OUTPUT_ROLE'
    }
    if ($Name.StartsWith('source-', [StringComparison]::Ordinal)) {
        if ($Name.EndsWith('-process.json', [StringComparison]::Ordinal)) { return [pscustomobject]@{ role = 'PROCESS_RECEIPT'; operation = 'HOST' } }
        if ($Name.EndsWith('.stdout', [StringComparison]::Ordinal) -or $Name.EndsWith('.stderr', [StringComparison]::Ordinal)) { return [pscustomobject]@{ role = 'SOURCE_AUDIT'; operation = 'HOST' } }
    }
    $p6p8CanaryArtifacts = @(
        'p6-canary-launch-intent.json', 'p6-canary-process.json', 'p6-canary.stdout', 'p6-canary.stderr',
        'p8-canary-launch-intent.json', 'p8-canary-process.json', 'p8-canary.stdout', 'p8-canary.stderr'
    )
    if ($Name -cin $p6p8CanaryArtifacts) {
        $operation = if ($Name.StartsWith('p6-', [StringComparison]::Ordinal)) { 'P6_CANARY' } else { 'P8_LITERAL_CANARY' }
        if ($Name.EndsWith('-launch-intent.json', [StringComparison]::Ordinal)) { return [pscustomobject]@{ role = 'HOST_INTENT'; operation = $operation } }
        if ($Name.EndsWith('-process.json', [StringComparison]::Ordinal)) { return [pscustomobject]@{ role = 'PROCESS_RECEIPT'; operation = $operation } }
        if ($Name.EndsWith('.stdout', [StringComparison]::Ordinal)) { return [pscustomobject]@{ role = 'CANARY_REPORT'; operation = $operation } }
        if ($Name.EndsWith('.stderr', [StringComparison]::Ordinal)) { return [pscustomobject]@{ role = 'STDERR'; operation = $operation } }
    }
    $negativeControlArtifacts = @(
        'p8-bad-mode-launch-intent.json', 'p8-bad-mode-process.json', 'p8-bad-mode.stdout', 'p8-bad-mode.stderr',
        'p8-bad-uuid-launch-intent.json', 'p8-bad-uuid-process.json', 'p8-bad-uuid.stdout', 'p8-bad-uuid.stderr',
        'p8-bad-input-sha-launch-intent.json', 'p8-bad-input-sha-process.json', 'p8-bad-input-sha.stdout', 'p8-bad-input-sha.stderr',
        'p8-nonempty-data-launch-intent.json', 'p8-nonempty-data-process.json', 'p8-nonempty-data.stdout', 'p8-nonempty-data.stderr'
    )
    if ($Name -cin $negativeControlArtifacts) {
        $negativeStem = $Name -replace '(-launch-intent\.json|-process\.json|\.stdout|\.stderr)$', ''
        $operation = switch -CaseSensitive ($negativeStem) {
            'p8-bad-mode' { 'P8_BAD_MODE' }
            'p8-bad-uuid' { 'P8_BAD_UUID' }
            'p8-bad-input-sha' { 'P8_BAD_INPUT_SHA' }
            'p8-nonempty-data' { 'P8_NONEMPTY_DATA' }
            default { throw 'OUTPUT_ROLE' }
        }
        if ($Name.EndsWith('-launch-intent.json', [StringComparison]::Ordinal)) { return [pscustomobject]@{ role = 'HOST_INTENT'; operation = $operation } }
        if ($Name.EndsWith('-process.json', [StringComparison]::Ordinal)) { return [pscustomobject]@{ role = 'PROCESS_RECEIPT'; operation = $operation } }
        if ($Name.EndsWith('.stdout', [StringComparison]::Ordinal)) { return [pscustomobject]@{ role = 'STDOUT'; operation = $operation } }
        if ($Name.EndsWith('.stderr', [StringComparison]::Ordinal)) { return [pscustomobject]@{ role = 'STDERR'; operation = $operation } }
    }
    if ($Name -ceq 'host-launch-intent.json' -or $Name -ceq 'scratch-intent.json' -or $Name -ceq 'compiler-launch-intent.json') { return [pscustomobject]@{ role = 'HOST_INTENT'; operation = 'HOST' } }
    if ($Name -ceq 'compiler-process.json') { return [pscustomobject]@{ role = 'PROCESS_RECEIPT'; operation = 'HOST' } }
    if ($Name -ceq 'compiler.stdout') { return [pscustomobject]@{ role = 'STDOUT'; operation = 'HOST' } }
    if ($Name -ceq 'compiler.stderr') { return [pscustomobject]@{ role = 'STDERR'; operation = 'HOST' } }
    if ($Name -ceq 'compiler-sources.txt') { return [pscustomobject]@{ role = 'COMPILER_INPUT'; operation = 'HOST' } }
    if ($Name -ceq 'bridge-copies.json') { return [pscustomobject]@{ role = 'BRIDGE_RECEIPT'; operation = 'HOST' } }
    throw 'OUTPUT_ROLE'
}
function Get-OutputCounterWithNativeFiles([long]$CurrentOutputBytes, [string]$ModeValue, [string[]]$RelativePaths) {
    if ($ModeValue -ceq 'CANARY') { return [long]$CurrentOutputBytes }
    Require ($ModeValue -ceq 'SUITE') 'OUTPUT_CENSUS'
    [long]$nativeOutputBytes = 0
    $nativeOutputPaths = New-Object 'System.Collections.Generic.List[string]'
    foreach ($name in $RelativePaths) {
        $artifact = Get-ArtifactRoleAndOperation $name
        if ([string]$artifact.role -ceq 'NATIVE_DATA' -or [string]$artifact.role -ceq 'MODE_INDEX') {
            Require (-not $nativeOutputPaths.Contains([string]$name)) 'OUTPUT_CENSUS'
            $path = Require-CanonicalExistingFile (Get-OutputPath $name) 'OUTPUT_CENSUS'
            Require ((Test-PathUnder $path $outputCanonical) -and -not (Test-OrdinalPathEqual $path $outputCanonical)) 'OUTPUT_CENSUS'
            $info = Get-Item -LiteralPath $path -Force
            Require (($info.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0 -and -not $info.PSIsContainer) 'OUTPUT_REPARSE'
            $nextBytes = [decimal]$CurrentOutputBytes + [decimal]$nativeOutputBytes + [decimal]$info.Length
            Require ($nextBytes -le 41943040) 'AGGREGATE_OUTPUT_LIMIT'
            $nativeOutputBytes = [long]$nativeOutputBytes + [long]$info.Length
            [void]$nativeOutputPaths.Add([string]$name)
        }
    }
    Require ($nativeOutputPaths.Count -eq 19) 'OUTPUT_CENSUS'
    return [long]$CurrentOutputBytes + [long]$nativeOutputBytes
}
function New-SuiteIndexAndFooter {
    $tree = Get-TreePaths
    $expectedBeforeIndex = @($outputFiles | Where-Object { $_ -cne 'suite-index.json' -and $_ -cne 'suite-footer.json' })
    $actualBeforeIndex = Get-OrdinalSortedStrings $tree.files
    $expectedSorted = Get-OrdinalSortedStrings $expectedBeforeIndex
    Require ($actualBeforeIndex.Count -eq $expectedSorted.Count) 'OUTPUT_CENSUS'
    for ($i = 0; $i -lt $expectedSorted.Count; $i++) { Require ([string]::Equals($actualBeforeIndex[$i], $expectedSorted[$i], [StringComparison]::Ordinal)) 'OUTPUT_CENSUS' }
    $directories = Get-OrdinalSortedStrings $tree.directories
    $expectedDirs = Get-OrdinalSortedStrings $allowedDirectories
    Require ($directories.Count -eq $expectedDirs.Count) 'OUTPUT_CENSUS'
    for ($i = 0; $i -lt $expectedDirs.Count; $i++) { Require ([string]::Equals($directories[$i], $expectedDirs[$i], [StringComparison]::Ordinal)) 'OUTPUT_CENSUS' }
    $artifacts = @()
    [long]$aggregate = 0
    foreach ($name in $expectedSorted) {
        $path = Get-OutputPath $name
        $info = Get-Item -LiteralPath $path -Force
        $identity = Get-ArtifactRoleAndOperation $name
        $artifacts += @([ordered]@{ path = $name; bytes = [long]$info.Length; sha256 = Get-Hash $path; role = [string]$identity.role; operation = [string]$identity.operation })
        $aggregate += [long]$info.Length
    }
    $index = [ordered]@{
        schema = 'tsj-q100-geometric-prefix-suite-index-v1'
        status = 'HOST_INDEX_COMPLETE'
        suiteId = $suiteId
        mode = $Mode
        inputSha256 = $InputPinsSha256
        sourceSha256 = $sourceInventorySha256
        wireSha256 = $wireSha256
        modeOrder = @($modeOrder)
        artifactCount = [long]$artifacts.Count
        aggregateBytes = $aggregate
        artifacts = $artifacts
        directories = @($directories)
        layoutQualification = $false
    }
    $indexPath = Get-OutputPath 'suite-index.json'
    Write-NewJson $indexPath $index
    $indexInfo = Get-Item -LiteralPath $indexPath
    Require ($indexInfo.Length -le 65536) 'INDEX_SIZE'
    $footer = [ordered]@{
        schema = 'tsj-q100-geometric-prefix-suite-footer-v1'
        status = 'HOST_OUTPUT_CLOSED'
        suiteId = $suiteId
        inputSha256 = $InputPinsSha256
        sourceSha256 = $sourceInventorySha256
        wireSha256 = $wireSha256
        modeOrder = @($modeOrder)
        artifactCount = [long]$artifacts.Count
        indexBytes = [long]$indexInfo.Length
        indexSha256 = Get-Hash $indexPath
        aggregateBytes = $aggregate
        directoryCount = [long]$directories.Count
        layoutQualification = $false
    }
    Write-NewJson (Get-OutputPath 'suite-footer.json') $footer
}

function Get-OutputClosure {
    $tree = Get-TreePaths
    $actualFiles = Get-OrdinalSortedStrings $tree.files
    $expectedFiles = Get-OrdinalSortedStrings $outputFiles
    $actualDirectories = Get-OrdinalSortedStrings $tree.directories
    $expectedDirectories = Get-OrdinalSortedStrings $allowedDirectories
    Require ($actualFiles.Count -eq $expectedFiles.Count -and $actualDirectories.Count -eq $expectedDirectories.Count) 'OUTPUT_CENSUS'
    for ($i = 0; $i -lt $expectedFiles.Count; $i++) { Require ([string]::Equals($actualFiles[$i], $expectedFiles[$i], [StringComparison]::Ordinal)) 'OUTPUT_CENSUS' }
    for ($i = 0; $i -lt $expectedDirectories.Count; $i++) { Require ([string]::Equals($actualDirectories[$i], $expectedDirectories[$i], [StringComparison]::Ordinal)) 'OUTPUT_CENSUS' }
    $rows = @()
    [long]$total = 0
    foreach ($name in $expectedFiles) {
        $path = Get-OutputPath $name
        Assert-VerifierNoReparseAncestors $path
        $info = Get-Item -LiteralPath $path -Force
        if ($name -match '\.json$') { Require ($info.Length -le 65536) 'STRUCTURED_OUTPUT_LIMIT' }
        if ($name -eq 'compiler-sources.txt') { Require ($info.Length -le 1048576) 'COMPILER_LIST_LIMIT' }
        if ($name -like '*.jsonl') { [void](Get-JsonLines $path 'JSONL_OUTPUT_INVALID') }
        $rows += @([ordered]@{ name = $name; bytes = [long]$info.Length; sha256 = Get-Hash $path })
        $total += [long]$info.Length
    }
    Require ($total -eq [long]$script:writtenOutputBytes) 'OUTPUT_CENSUS'
    Require ($total -le 41943040) 'SUITE_OUTPUT_LIMIT'
    return [ordered]@{ files = $rows; directories = @($expectedDirectories); aggregateBytes = $total; hostResultExcluded = $true }
}

function Verify-OutputAfterResult($Closure) {
    $tree = Get-TreePaths
    $actualFiles = Get-OrdinalSortedStrings $tree.files
    $expectedFiles = Get-OrdinalSortedStrings $allSuccessFiles
    $actualDirectories = Get-OrdinalSortedStrings $tree.directories
    $expectedDirectories = Get-OrdinalSortedStrings $allowedDirectories
    Require ($actualFiles.Count -eq $expectedFiles.Count -and $actualDirectories.Count -eq $expectedDirectories.Count) 'OUTPUT_CENSUS'
    for ($i = 0; $i -lt $expectedFiles.Count; $i++) { Require ([string]::Equals($actualFiles[$i], $expectedFiles[$i], [StringComparison]::Ordinal)) 'OUTPUT_CENSUS' }
    for ($i = 0; $i -lt $expectedDirectories.Count; $i++) { Require ([string]::Equals($actualDirectories[$i], $expectedDirectories[$i], [StringComparison]::Ordinal)) 'OUTPUT_CENSUS' }
    foreach ($row in $Closure.files) {
        $path = Get-OutputPath ([string]$row.name)
        $info = Get-Item -LiteralPath $path -Force
        Require ($info.Length -eq [long]$row.bytes -and (Get-Hash $path) -ceq [string]$row.sha256) 'OUTPUT_CHANGED_AFTER_CLOSURE'
    }
    $hostResultPath = Get-OutputPath 'host-result.json'
    $hostResultInfo = Get-Item -LiteralPath $hostResultPath -Force
    Require ($hostResultInfo.Length -le 65536) 'HOST_RESULT_LIMIT'
    [long]$allBytes = [long]$Closure.aggregateBytes + [long]$hostResultInfo.Length
    Require ($allBytes -eq [long]$script:writtenOutputBytes -and $allBytes -le 41943040) 'OUTPUT_CENSUS'
    $modeBytes = @{}
    foreach ($op in @('NULL', 'OBSERVE', 'CALLBACK_THROW', 'REASON_THROW')) { $modeBytes[$op] = [long]0 }
    foreach ($row in $Closure.files) {
        $rowName = [string]$row.name
        foreach ($op in $modeBytes.Keys) {
            if ($rowName.StartsWith(('runs/' + $op + '/'), [StringComparison]::Ordinal) -or
                $rowName.StartsWith(('runs/' + $op + '.'), [StringComparison]::Ordinal)) { $modeBytes[$op] += [long]$row.bytes; break }
        }
    }
    foreach ($op in @('NULL', 'OBSERVE', 'CALLBACK_THROW', 'REASON_THROW')) {
        if ($Mode -ceq 'SUITE') { Require ($modeBytes[$op] -le 8388608) 'OPERATION_OUTPUT_LIMIT' }
    }
    $hostBytes = $allBytes
    foreach ($op in @('NULL', 'OBSERVE', 'CALLBACK_THROW', 'REASON_THROW')) { $hostBytes -= [long]$modeBytes[$op] }
    Require ($hostBytes -le 8388608) 'HOST_EVIDENCE_LIMIT'
}
function Remove-OwnedScratch {
    if (-not $scratchCreated -or $scratchRemoved) { return }
    Require (Test-Path -LiteralPath $scratch -PathType Container) 'SCRATCH_MISSING'
    Assert-VerifierNoReparseAncestors $scratch
    $current = Get-Item -LiteralPath $scratch
    Require ([long]$current.CreationTimeUtc.Ticks -eq [long]$scratchTicks) 'SCRATCH_IDENTITY_CHANGED'
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    [void](Remove-VerifierOwnedTree $tempRoot $scratch)
    Require (-not (Test-Path -LiteralPath $scratch)) 'SCRATCH_CLEANUP_UNPROVEN'
    $script:scratchRemoved = $true
}

function Get-ControlSnapshot([string]$Directory) {
    $files = @([IO.Directory]::GetFiles($Directory, '*', [IO.SearchOption]::TopDirectoryOnly))
    $directories = [IO.Directory]::GetDirectories($Directory, '*', [IO.SearchOption]::TopDirectoryOnly)
    Require ($directories.Count -eq 0) 'CONTROL_DIRECTORY_CHANGED'
    $rows = @()
    foreach ($file in $files) {
        $info = Get-Item -LiteralPath $file -Force
        Require (($info.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'CONTROL_DIRECTORY_CHANGED'
        $rows += @([ordered]@{ name = [IO.Path]::GetFileName($file); bytes = [long]$info.Length; sha256 = Get-Hash $file })
    }
    $rows = @($rows | Sort-Object -Property name -CaseSensitive)
    return (ConvertTo-Json -InputObject $rows -Depth 8 -Compress)
}

function Verify-ControlSnapshot([string]$Directory, [string]$ExpectedSnapshot) {
    $current = Get-ControlSnapshot $Directory
    Require ([string]::Equals($current, $ExpectedSnapshot, [StringComparison]::Ordinal)) 'CONTROL_DIRECTORY_CHANGED'
}

function Get-NativeDataBytes([string]$Directory) {
    [long]$total = 0
    foreach ($file in [IO.Directory]::GetFiles($Directory, '*', [IO.SearchOption]::TopDirectoryOnly)) { $total += [long](Get-Item -LiteralPath $file).Length }
    return $total
}

function Get-OperationArtifactBytes([string]$ModeValue) {
    [long]$total = 0
    $prefix = 'runs/' + $ModeValue + '/'
    foreach ($name in $allSuccessFiles) {
        if ($name.StartsWith($prefix, [StringComparison]::Ordinal) -or $name.StartsWith(('runs/' + $ModeValue + '.'), [StringComparison]::Ordinal)) {
            $path = Get-OutputPath $name
            if (Test-Path -LiteralPath $path -PathType Leaf) { $total += [long](Get-Item -LiteralPath $path).Length }
        }
    }
    Require ($total -le 8388608) 'OPERATION_OUTPUT_LIMIT'
    return $total
}

function Invoke-P8NegativeControl([string]$Name, [string]$ProcessKey, [string]$Stem, [string]$ExpectedCode, [int]$ExpectedIndex,
    [string]$ModeValue, [string]$RunIdValue, [string]$InputShaValue, [string]$WireShaValue, [string]$SourceShaValue,
    [string]$DataRelative) {
    $dataPath = Get-OutputPath $DataRelative
    $before = Get-ControlSnapshot $dataPath
    $arguments = @('-ea', '-cp', $classpath, $studyMain, $ModeValue, $RunIdValue, $InputShaValue, $WireShaValue, $SourceShaValue, $dataPath)
    $launchName = $Stem + '-launch-intent.json'
    Write-NewJson (Get-OutputPath $launchName) ([ordered]@{ status = 'INTENT_NOT_START_PROOF'; executable = $java; sha256 = $selectedJavaSha256; arguments = $arguments; limitMs = 480000 })
    $call = Invoke-NativeChild $java $arguments ('P8_' + $Name)
    $proc = $call.process
    try {
        Require-InvocationReceipt $proc $java $arguments ($Name + '_PROCESS_IDENTITY')
        Test-VerifierLogsRemoved $proc
        $receipt = Save-ProcessEvidenceAt ($Stem + '-process.json') ($Stem + '.stdout') ($Stem + '.stderr') $proc
        $script:processRecords[$ProcessKey] = Get-ProcessRecord $receipt.receipt $receipt.stdout $receipt.stderr ([double]$call.wallMs)
        Require ($proc.ExitCode -ne 0 -and $proc.TerminationProven -eq $true -and [string]::IsNullOrEmpty([string]$proc.Stdout)) ($Name + '_EXPECTED_REJECTION')
        $marker = Parse-P8Preflight ([string]$proc.Stderr) $ExpectedCode $ExpectedIndex
        Verify-ControlSnapshot $dataPath $before
        $script:returnedChildEvidence = $null
        $result = [ordered]@{ name = $Name; expectedFailureCode = $ExpectedCode; observedFailureCode = [string]$marker.code;
            exitCode = [int]$proc.ExitCode; terminationProven = $true; dataDirectoryUnchanged = $true }
        $script:inputControls += @($result)
        return $result
    }
    catch {
        $script:failedProcessReturn = [ordered]@{ phase = ('P8_' + $Name); wallMs = [double]$call.wallMs; process = $proc }
        throw
    }
}

function Get-NativeJsonFile([string]$Directory, [string]$Name, [string]$Code) {
    $path = Join-Path $Directory $Name
    Require (Test-Path -LiteralPath $path -PathType Leaf) $Code
    $info = Get-Item -LiteralPath $path
    Require ($info.Length -le 65536) $Code
    try { return (Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop) }
    catch { throw $Code }
}

function Validate-P8CompleteOperation([string]$ModeValue, [string]$RunIdValue, [string]$DataPath, $Process, [double]$WallMs, [string]$ProcessKey, [string]$DirectoryRelative) {
    Require ($Process.ExitCode -eq 0 -and $Process.TerminationProven -eq $true -and [string]::IsNullOrEmpty([string]$Process.Stderr)) ($ModeValue + '_PROCESS')
    $stdout = [string]$Process.Stdout
    Require (-not $stdout.Contains("`r") -and $stdout.EndsWith("`n") -and $stdout.IndexOf("`n", [StringComparison]::Ordinal) -eq $stdout.Length - 1) ($ModeValue + '_MARKER_LINE')
    $marker = Parse-OneJsonMarker $stdout 'CANONICAL_GEOMETRIC_PREFIX_COMPLETE' ($ModeValue + '_MARKER')
    Require-ExactKeys $marker @('schema', 'mode', 'runId', 'inputSha256', 'sourceSha256', 'wireSha256', 'index', 'aggregateBytes', 'workNanos') ($ModeValue + '_MARKER_SCHEMA')
    Require ([string]$marker.schema -ceq 'tsj-canonical-geometric-prefix-complete-v1' -and
        [string]$marker.mode -ceq $ModeValue -and [string]$marker.runId -ceq $RunIdValue -and
        [string]$marker.inputSha256 -ceq $InputPinsSha256 -and [string]$marker.sourceSha256 -ceq $sourceInventorySha256 -and
        [string]$marker.wireSha256 -ceq $wireSha256) ($ModeValue + '_MARKER_BINDING')
    Require-ExactKeys $marker.index @('path', 'bytes', 'sha256') ($ModeValue + '_INDEX_REFERENCE')
    Require ([string]$marker.index.path -ceq 'canonical-geometric-prefix-index.json' -and
        [string]$marker.index.sha256 -cmatch '^[0-9a-f]{64}$') ($ModeValue + '_INDEX_REFERENCE')
    Require-ExactInteger $marker.index.bytes 1 65536 ($ModeValue + '_INDEX_REFERENCE')
    Require-ExactInteger $marker.aggregateBytes 1 8388608 ($ModeValue + '_AGGREGATE_BYTES')
    Require-ExactInteger $marker.workNanos 0 90000000000 ($ModeValue + '_WORK_LIMIT')
    Require (([double]$marker.workNanos / 1000000.0) -le $WallMs) ($ModeValue + '_WORK_WALL')
    $expectedNames = @('op-1.jsonl', 'fixed-prefix.jsonl', 'prefix-blockers.json', 'prefix-stages.json',
        'canonical-stage-index.json', 'prefix-geometric-reasons.jsonl', 'canonical-geometric-prefix-index.json')
    Require-ExactDataFiles $DataPath $expectedNames ($ModeValue + '_DATA_CENSUS')
    $indexPath = Join-Path $DataPath 'canonical-geometric-prefix-index.json'
    $indexInfo = Get-Item -LiteralPath $indexPath
    Require ($indexInfo.Length -eq [long]$marker.index.bytes -and (Get-Hash $indexPath) -ceq [string]$marker.index.sha256) ($ModeValue + '_INDEX_HASH')
    $index = Get-NativeJsonFile $DataPath 'canonical-geometric-prefix-index.json' ($ModeValue + '_INDEX_JSON')
    Require ([string]$index.schema -ceq 'tsj-canonical-geometric-prefix-index-v1' -and
        [string]$index.mode -ceq $ModeValue -and [string]$index.runId -ceq $RunIdValue -and
        [string]$index.inputSha256 -ceq $InputPinsSha256 -and [string]$index.sourceSha256 -ceq $sourceInventorySha256 -and
        [string]$index.wireSha256 -ceq $wireSha256) ($ModeValue + '_INDEX_BINDING')
    $sidecarRows = Get-JsonLines (Join-Path $DataPath 'prefix-geometric-reasons.jsonl') ($ModeValue + '_SIDECAR')
    Require ($sidecarRows.Count -eq 16) ($ModeValue + '_SIDECAR_ROWS')
    $footer = $sidecarRows[$sidecarRows.Count - 1]
    Require ([string]$footer.kind -ceq 'footer' -and [string]$footer.mode -ceq $ModeValue -and
        [string]$footer.runId -ceq $RunIdValue -and [string]$footer.inputSha256 -ceq $InputPinsSha256 -and
        [string]$footer.sourceSha256 -ceq $sourceInventorySha256) ($ModeValue + '_FOOTER_BINDING')
    Require-ExactInteger $footer.totalObservationUnits 0 32768 ($ModeValue + '_UNIT_LIMIT')
    Require ([string]$footer.cleanupKind -ceq 'FULL_NATIVE_WALL_PLUS_POST_SOURCE' -and
        $footer.complete -is [bool] -and $footer.complete -eq $true -and
        $footer.cleanupComplete -is [bool] -and $footer.cleanupComplete -eq $true) ($ModeValue + '_FOOTER_STATUS')
    $footerUnits = Get-P8FooterUnits (Join-Path $DataPath 'prefix-geometric-reasons.jsonl') $ModeValue $RunIdValue
    Require ($footerUnits -eq [long]$footer.totalObservationUnits) ($ModeValue + '_FOOTER_UNITS')
    [long]$dataBytes = Get-NativeDataBytes $DataPath
    Require ($dataBytes -eq [long]$marker.aggregateBytes -and $dataBytes -le 8388608) ($ModeValue + '_AGGREGATE_BYTES')
    $groups = Get-GroupRecordCount (Join-Path $DataPath 'op-1.jsonl')
    Require ($groups -ge 0 -and $groups -le 640) ($ModeValue + '_GROUP_LIMIT')
    $operationBytes = Get-OperationArtifactBytes $ModeValue
    Require ($operationBytes -le 8388608) ($ModeValue + '_OPERATION_BYTES')
    $script:operationWalls[$ModeValue] = [double]$WallMs
    $script:operationWorkCredits[$ModeValue] = [double]$marker.workNanos / 1000000.0
    $script:returnedChildEvidence = $null
    return [ordered]@{ mode = $ModeValue; runId = $RunIdValue; disposition = 'COMPLETE'; processKey = $ProcessKey;
        dataDirectory = $DirectoryRelative; workNanos = [long]$marker.workNanos; groups = [long]$groups;
        observationUnits = [long]$footerUnits }
}

function Invoke-P8Complete([string]$ModeValue, [string]$ProcessKey, [string]$RunIdValue) {
    $dataRelative = 'runs/' + $ModeValue + '/data'
    $dataPath = Get-OutputPath $dataRelative
    Require (([IO.Directory]::GetFiles($dataPath, '*', [IO.SearchOption]::TopDirectoryOnly)).Count -eq 0) ($ModeValue + '_DATA_NOT_EMPTY')
    $arguments = @('-ea', '-cp', $classpath, $studyMain, $ModeValue, $RunIdValue, $InputPinsSha256, $wireSha256, $sourceInventorySha256, $dataPath)
    Write-NewJson (Get-OutputPath ('runs/' + $ModeValue + '/launch-intent.json')) ([ordered]@{ status = 'INTENT_NOT_START_PROOF'; executable = $java; sha256 = $selectedJavaSha256; arguments = $arguments; limitMs = 480000 })
    $call = Invoke-NativeChild $java $arguments ('BASE_' + $ModeValue)
    $proc = $call.process
    try {
        Require-InvocationReceipt $proc $java $arguments ($ModeValue + '_PROCESS_IDENTITY')
        Test-VerifierLogsRemoved $proc
        $receipt = Save-ProcessEvidenceAt ('runs/' + $ModeValue + '/process.json') ('runs/' + $ModeValue + '.stdout') ('runs/' + $ModeValue + '.stderr') $proc
        $script:processRecords[$ProcessKey] = Get-ProcessRecord $receipt.receipt $receipt.stdout $receipt.stderr ([double]$call.wallMs)
        $operation = Validate-P8CompleteOperation $ModeValue $RunIdValue $dataPath $proc ([double]$call.wallMs) $ProcessKey $dataRelative
        $script:operations += @($operation)
        return $operation
    }
    catch {
        $script:failedProcessReturn = [ordered]@{ phase = ('BASE_' + $ModeValue); wallMs = [double]$call.wallMs; process = $proc }
        throw
    }
}

function Invoke-P6CallbackThrow([string]$RunIdValue) {
    $modeValue = 'CALLBACK_THROW'
    $dataRelative = 'runs/CALLBACK_THROW/data'
    $dataPath = Get-OutputPath $dataRelative
    $p6Main = 'com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy'
    $arguments = @('-ea', '-cp', $classpath, $p6Main, $modeValue, $RunIdValue, $InputPinsSha256, $wireSha256, $dataPath)
    Write-NewJson (Get-OutputPath 'runs/CALLBACK_THROW/launch-intent.json') ([ordered]@{ status = 'INTENT_NOT_START_PROOF'; executable = $java; sha256 = $selectedJavaSha256; arguments = $arguments; limitMs = 480000 })
    $call = Invoke-NativeChild $java $arguments 'BASE_CALLBACK_THROW'
    $proc = $call.process
    try {
        Require-InvocationReceipt $proc $java $arguments 'CALLBACK_THROW_PROCESS_IDENTITY'
        Test-VerifierLogsRemoved $proc
        $receipt = Save-ProcessEvidenceAt 'runs/CALLBACK_THROW/process.json' 'runs/CALLBACK_THROW.stdout' 'runs/CALLBACK_THROW.stderr' $proc
        $script:processRecords.callbackThrow = Get-ProcessRecord $receipt.receipt $receipt.stdout $receipt.stderr ([double]$call.wallMs)
        Require ($proc.ExitCode -ne 0 -and $proc.TerminationProven -eq $true -and [string]::IsNullOrEmpty([string]$proc.Stdout)) 'CALLBACK_THROW_EXIT'
        $trailer = Parse-MarkerWithPossibleStack ([string]$proc.Stderr) 'CANONICAL_PREFIX_STAGE_EXPECTED_CALLBACK_THROW' 'CALLBACK_THROW_MARKER'
        Require-ExactKeys $trailer @('schema', 'mode', 'runId', 'sourceManifestSha256', 'wireSha256', 'scope', 'layoutQualification',
            'exceptionType', 'exceptionMessage', 'partialReceipt', 'stages', 'inclusiveWorkNanos', 'cleanupAccounting') 'CALLBACK_THROW_SCHEMA'
        Require ([string]$trailer.schema -ceq 'tsj-canonical-prefix-stage-exception-v1' -and [string]$trailer.mode -ceq $modeValue -and
            [string]$trailer.runId -ceq $RunIdValue -and [string]$trailer.sourceManifestSha256 -ceq $InputPinsSha256 -and
            [string]$trailer.wireSha256 -ceq $wireSha256 -and [string]$trailer.scope -ceq 'CANONICAL_BASE_REQ0_ZERO_DELTA_STAGES_ONLY' -and
            $trailer.layoutQualification -is [bool] -and $trailer.layoutQualification -eq $false -and
            [string]$trailer.exceptionType -ceq 'java.lang.IllegalStateException' -and
            [string]$trailer.exceptionMessage -ceq 'CANONICAL_PREFIX_STAGE_CALLBACK_THROW' -and
            [string]$trailer.cleanupAccounting -ceq 'FULL_NATIVE_WALL_PLUS_POST_SOURCE') 'CALLBACK_THROW_IDENTITY'
        Require-ExactKeys $trailer.partialReceipt @('path', 'bytes', 'sha256') 'CALLBACK_THROW_PARTIAL_RECEIPT'
        Require ([string]$trailer.partialReceipt.path -ceq 'op-1.jsonl' -and [string]$trailer.partialReceipt.sha256 -cmatch '^[0-9a-f]{64}$') 'CALLBACK_THROW_PARTIAL_RECEIPT'
        Require-ExactInteger $trailer.partialReceipt.bytes 1 8388608 'CALLBACK_THROW_PARTIAL_RECEIPT'
        $stageSummary = $trailer.stages
        Require ([string]$stageSummary.status -ceq 'EXPECTED_CALLBACK_THROW_ONLY' -and [string]$stageSummary.mode -ceq $modeValue -and
            [string]$stageSummary.runId -ceq $RunIdValue -and [string]$stageSummary.sourceManifestSha256 -ceq $InputPinsSha256 -and
            [string]$stageSummary.wireSha256 -ceq $wireSha256) 'CALLBACK_THROW_STAGE_CONTEXT'
        Require ($stageSummary.lifecycle.ownersReleased -is [bool] -and $stageSummary.lifecycle.ownersReleased -eq $true -and
            $stageSummary.lifecycle.contextRestored -is [bool] -and $stageSummary.lifecycle.contextRestored -eq $true -and
            $stageSummary.lifecycle.sourceRefsDropped -is [bool] -and $stageSummary.lifecycle.sourceRefsDropped -eq $true -and
            $stageSummary.lifecycle.observersDetached -is [bool] -and $stageSummary.lifecycle.observersDetached -eq $true) 'CALLBACK_THROW_CLEANUP'
        Require ($stageSummary.queries -is [array] -and $stageSummary.queries.Count -eq 1 -and
            [string]$stageSummary.queries[0].outcome -ceq 'EXPECTED_CALLBACK_THROW') 'CALLBACK_THROW_QUERY'
        Require-ExactInteger $trailer.inclusiveWorkNanos 0 90000000000 'CALLBACK_THROW_WORK'
        Require (([double]$trailer.inclusiveWorkNanos / 1000000.0) -le [double]$call.wallMs) 'CALLBACK_THROW_WORK_WALL'
        Require-ExactDataFiles $dataPath @('op-1.jsonl') 'CALLBACK_THROW_DATA_CENSUS'
        $partialPath = Join-Path $dataPath 'op-1.jsonl'
        $partialInfo = Get-Item -LiteralPath $partialPath
        Require ($partialInfo.Length -eq [long]$trailer.partialReceipt.bytes -and (Get-Hash $partialPath) -ceq [string]$trailer.partialReceipt.sha256) 'CALLBACK_THROW_PARTIAL_RECEIPT'
        $groups = Get-GroupRecordCount $partialPath
        Require ($groups -le 640) 'CALLBACK_THROW_GROUP_LIMIT'
        $units = [long]$stageSummary.totals.totalObservationUnits
        Require-ExactInteger $units 0 32768 'CALLBACK_THROW_UNITS'
        $operationBytes = Get-OperationArtifactBytes $modeValue
        Require ($operationBytes -le 8388608) 'CALLBACK_THROW_OPERATION_BYTES'
        $script:returnedChildEvidence = $null
        $script:operations += @([ordered]@{ mode = $modeValue; runId = $RunIdValue; disposition = 'EXPECTED_THROW'; processKey = 'callbackThrow';
            dataDirectory = $dataRelative; workNanos = $null; groups = [long]$groups; observationUnits = $units })
    }
    catch {
        $script:failedProcessReturn = [ordered]@{ phase = 'BASE_CALLBACK_THROW'; wallMs = [double]$call.wallMs; process = $proc }
        throw
    }
}

function Verify-NativeDataReference($Reference, [string]$ExpectedPath, [string]$Directory, [string]$Code) {
    Require-ExactKeys $Reference @('path', 'bytes', 'sha256') $Code
    Require ([string]$Reference.path -ceq $ExpectedPath -and [string]$Reference.sha256 -cmatch '^[0-9a-f]{64}$') $Code
    Require-ExactInteger $Reference.bytes 1 8388608 $Code
    $filePath = Join-Path $Directory $ExpectedPath
    $fileInfo = Get-Item -LiteralPath $filePath
    Require ($fileInfo.Length -eq [long]$Reference.bytes -and (Get-Hash $filePath) -ceq [string]$Reference.sha256) $Code
}
function Invoke-P8ReasonThrow([string]$RunIdValue) {
    $modeValue = 'REASON_THROW'
    $dataRelative = 'runs/REASON_THROW/data'
    $dataPath = Get-OutputPath $dataRelative
    $arguments = @('-ea', '-cp', $classpath, $studyMain, $modeValue, $RunIdValue, $InputPinsSha256, $wireSha256, $sourceInventorySha256, $dataPath)
    Write-NewJson (Get-OutputPath 'runs/REASON_THROW/launch-intent.json') ([ordered]@{ status = 'INTENT_NOT_START_PROOF'; executable = $java; sha256 = $selectedJavaSha256; arguments = $arguments; limitMs = 480000 })
    $call = Invoke-NativeChild $java $arguments 'BASE_REASON_THROW'
    $proc = $call.process
    try {
        Require-InvocationReceipt $proc $java $arguments 'REASON_THROW_PROCESS_IDENTITY'
        Test-VerifierLogsRemoved $proc
        $receipt = Save-ProcessEvidenceAt 'runs/REASON_THROW/process.json' 'runs/REASON_THROW.stdout' 'runs/REASON_THROW.stderr' $proc
        $script:processRecords.reasonThrow = Get-ProcessRecord $receipt.receipt $receipt.stdout $receipt.stderr ([double]$call.wallMs)
        Require ($proc.ExitCode -ne 0 -and $proc.TerminationProven -eq $true -and [string]::IsNullOrEmpty([string]$proc.Stdout)) 'REASON_THROW_EXIT'
        $marker = Parse-MarkerWithPossibleStack ([string]$proc.Stderr) 'CANONICAL_GEOMETRIC_PREFIX_EXPECTED_REASON_THROW' 'REASON_THROW_MARKER'
        $markerKeys = @('schema', 'runId', 'mode', 'inputSha256', 'sourceSha256', 'wireSha256', 'queryOrdinal', 'parentOrdinal', 'sentinel',
            'exceptionClass', 'exceptionMessage', 'suppressedCount', 'cleanupKind', 'workNanos', 'cleanupComplete', 'sourcePreserved',
            'observersDetached', 'referencesDropped', 'ownersReleased', 'contextRestored', 'partialSidecar', 'partialOperationReceipt',
            'parentBlockers', 'parentStages')
        Require-ExactKeys $marker $markerKeys 'REASON_THROW_SCHEMA'
        Require ([string]$marker.schema -ceq 'tsj-canonical-geometric-prefix-exception-v1' -and [string]$marker.runId -ceq $RunIdValue -and
            [string]$marker.mode -ceq $modeValue -and [string]$marker.inputSha256 -ceq $InputPinsSha256 -and
            [string]$marker.sourceSha256 -ceq $sourceInventorySha256 -and [string]$marker.wireSha256 -ceq $wireSha256 -and
            [string]$marker.sentinel -ceq 'FIRST_GEOMETRIC_REASON_CALLBACK' -and
            [string]$marker.exceptionClass -ceq 'java.lang.IllegalStateException' -and
            [string]$marker.exceptionMessage -ceq 'FIRST_GEOMETRIC_REASON_CALLBACK' -and
            [string]$marker.cleanupKind -ceq 'FULL_NATIVE_WALL_PLUS_POST_SOURCE') 'REASON_THROW_IDENTITY'
        Require-ExactInteger $marker.queryOrdinal 0 0 'REASON_THROW_QUERY'
        Require-ExactInteger $marker.parentOrdinal 0 0 'REASON_THROW_QUERY'
        Require-ExactInteger $marker.suppressedCount 0 0 'REASON_THROW_SUPPRESSED'
        Require-ExactInteger $marker.workNanos 0 90000000000 'REASON_THROW_WORK'
        Require (([double]$marker.workNanos / 1000000.0) -le [double]$call.wallMs) 'REASON_THROW_WORK_WALL'
        foreach ($flag in @('cleanupComplete', 'sourcePreserved', 'observersDetached', 'referencesDropped', 'ownersReleased', 'contextRestored')) {
            Require ($marker.$flag -is [bool] -and $marker.$flag -eq $true) 'REASON_THROW_CLEANUP'
        }
        Require-ExactDataFiles $dataPath @('op-1.jsonl', 'prefix-blockers.json', 'prefix-stages.json', 'prefix-geometric-reasons.jsonl') 'REASON_THROW_DATA_CENSUS'
        Verify-NativeDataReference $marker.partialSidecar 'prefix-geometric-reasons.jsonl' $dataPath 'REASON_THROW_REFERENCE'
        Verify-NativeDataReference $marker.partialOperationReceipt 'op-1.jsonl' $dataPath 'REASON_THROW_REFERENCE'
        Verify-NativeDataReference $marker.parentBlockers 'prefix-blockers.json' $dataPath 'REASON_THROW_REFERENCE'
        Verify-NativeDataReference $marker.parentStages 'prefix-stages.json' $dataPath 'REASON_THROW_REFERENCE'
        $units = Get-P8FooterUnits (Join-Path $dataPath 'prefix-geometric-reasons.jsonl') $modeValue $RunIdValue
        $groups = Get-GroupRecordCount (Join-Path $dataPath 'op-1.jsonl')
        Require ($groups -le 640) 'REASON_THROW_GROUP_LIMIT'
        $operationBytes = Get-OperationArtifactBytes $modeValue
        Require ($operationBytes -le 8388608) 'REASON_THROW_OPERATION_BYTES'
        $script:returnedChildEvidence = $null
        $script:operations += @([ordered]@{ mode = $modeValue; runId = $RunIdValue; disposition = 'EXPECTED_THROW'; processKey = 'reasonThrow';
            dataDirectory = $dataRelative; workNanos = [long]$marker.workNanos; groups = [long]$groups; observationUnits = [long]$units })
    }
    catch {
        $script:failedProcessReturn = [ordered]@{ phase = 'BASE_REASON_THROW'; wallMs = [double]$call.wallMs; process = $proc }
        throw
    }
}
try {
    $phase = 'HOST_CONTEXT'
    Require ($PSVersionTable.PSVersion.Major -eq 5 -and $PSVersionTable.PSVersion.Minor -eq 1) 'SELECTED_PS51_REQUIRED'
    $owner = Get-Process -Id $PID
    Require ($null -ne $owner.Path -and (Test-Path -LiteralPath $owner.Path -PathType Leaf)) 'SELECTED_PS51_REQUIRED'
    Require ((Get-Hash ([string]$owner.Path)) -ceq $selectedPowerShellSha256) 'SELECTED_PS51_REQUIRED'
    Require ([IO.Path]::IsPathRooted($RepositoryRoot) -and [IO.Path]::IsPathRooted($InputPinsPath) -and
        [IO.Path]::IsPathRooted($OutputRoot) -and [IO.Path]::IsPathRooted($JavaHome) -and [IO.Path]::IsPathRooted($PythonExe)) 'ABSOLUTE_PATH_REQUIRED'
    $repositoryArgument = $RepositoryRoot
    $pinsArgument = $InputPinsPath
    $javaHomeArgument = $JavaHome
    $pythonArgument = $PythonExe
    $repositoryFull = [IO.Path]::GetFullPath($repositoryArgument)
    $pinsFull = [IO.Path]::GetFullPath($pinsArgument)
    $javaHomeFull = [IO.Path]::GetFullPath($javaHomeArgument)
    $pythonFull = [IO.Path]::GetFullPath($pythonArgument)
    Require ((Test-OrdinalPathEqual $repositoryArgument $repositoryFull) -and
        (Test-OrdinalPathEqual $pinsArgument $pinsFull) -and
        (Test-OrdinalPathEqual $javaHomeArgument $javaHomeFull) -and
        (Test-OrdinalPathEqual $pythonArgument $pythonFull)) 'NONCANONICAL_PATH'
    $RepositoryRoot = $repositoryFull
    $InputPinsPath = $pinsFull
    $JavaHome = $javaHomeFull
    $PythonExe = $pythonFull
    Require (Test-Path -LiteralPath $RepositoryRoot -PathType Container) 'REPOSITORY_ROOT'
    Require (Test-Path -LiteralPath $InputPinsPath -PathType Leaf) 'INPUT_PINS_PATH'
    Require (Test-Path -LiteralPath $JavaHome -PathType Container) 'JAVA_HOME'
    Require (Test-Path -LiteralPath $PythonExe -PathType Leaf) 'PYTHON_PATH'
    Require ($InputPinsSha256 -cmatch '^[0-9a-f]{64}$' -and (Get-Hash $InputPinsPath) -ceq $InputPinsSha256) 'INPUT_PINS_HASH'
    foreach ($path in @($RepositoryRoot, $InputPinsPath, $JavaHome, $PythonExe, $PSCommandPath, [string]$owner.Path)) {
        Assert-LocalNoReparseAncestors $path
    }

    $outputCanonical = [IO.Path]::GetFullPath($OutputRoot)
    Require (Test-OrdinalPathEqual $OutputRoot $outputCanonical) 'OUTPUT_PATH'
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    Require (Test-PathUnder $outputCanonical $tempRoot) 'EXTERNAL_OUTPUT_REQUIRED'
    Require (-not (Test-PathUnder $outputCanonical $RepositoryRoot) -and -not (Test-PathUnder $RepositoryRoot $outputCanonical) -and
        -not (Test-PathUnder $outputCanonical $PSScriptRoot) -and -not (Test-PathUnder $PSScriptRoot $outputCanonical)) 'EXTERNAL_OUTPUT_REQUIRED'
    $outputParent = [IO.Path]::GetDirectoryName($outputCanonical)
    Require (Test-Path -LiteralPath $outputParent -PathType Container) 'OUTPUT_PARENT_MISSING'
    Assert-LocalNoReparseAncestors $outputParent
    Require (-not (Test-Path -LiteralPath $outputCanonical)) 'OUTPUT_EXISTS'

    $modulePath = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'scripts/VerifierIsolation.psm1'))
    Require (Test-Path -LiteralPath $modulePath -PathType Leaf) 'ISOLATION_MODULE_PATH'
    Assert-LocalNoReparseAncestors $modulePath
    Require ((Get-Hash $modulePath) -ceq $moduleSha256) 'ISOLATION_PIN'
    Import-Module $modulePath -Force -DisableNameChecking
    foreach ($path in @($RepositoryRoot, $InputPinsPath, $JavaHome, $PythonExe, $PSCommandPath, $modulePath)) { Assert-VerifierNoReparseAncestors $path }
    Assert-VerifierNoReparseAncestors $outputParent

    $phase = 'INPUT_PINS'
    $pinsInfo = Get-Item -LiteralPath $InputPinsPath
    Require ($pinsInfo.Length -le 4194304) 'INPUT_PINS_SIZE'
    try { $pins = Get-Content -LiteralPath $InputPinsPath -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop }
    catch { throw 'INPUT_SCHEMA' }
    Validate-InputManifest $pins
    $hostRows = @($pins.files | Where-Object { Test-OrdinalPathEqual ([string]$_.path) ([IO.Path]::GetFullPath($PSCommandPath)) })
    Require ($hostRows.Count -eq 1 -and [long]$hostRows[0].bytes -eq (Get-Item -LiteralPath $PSCommandPath).Length -and
        [string]$hostRows[0].sha256 -ceq (Get-Hash $PSCommandPath)) 'SELF_PIN'
    Require ((Test-OrdinalPathEqual $PythonExe ([string]$pins.toolPaths.pythonExe)) -and
        (Test-OrdinalPathEqual $JavaHome ([string]$pins.toolPaths.javaHome)) -and
        (Test-OrdinalPathEqual ([string]$owner.Path) ([string]$pins.toolPaths.powershellExe))) 'TOOL_CONTEXT'
    $java = Require-CanonicalExistingFile (Join-Path $JavaHome 'bin/java.exe') 'JAVA_PATH'
    $javac = Require-CanonicalExistingFile (Join-Path $JavaHome 'bin/javac.exe') 'JAVAC_PATH'
    Require ((Get-Hash $java) -ceq $selectedJavaSha256 -and (Get-Hash $javac) -ceq $selectedJavacSha256) 'JAVA_PIN'
    Verify-AllManifestExtraPins
    Load-AuthenticatedContexts

    $protectedRoots = @($RepositoryRoot, $PSScriptRoot, $canaryRoot, [string]$p6Pins.repositoryRoot,
        [string]$p6Pins.historicalRoot, $bridgeRoot, [IO.Path]::GetDirectoryName($InputPinsPath),
        [IO.Path]::GetDirectoryName([string]$pins.sourceInventory.path), [IO.Path]::GetDirectoryName([string]$pins.canaryPrerequisite.pinsPath),
        [IO.Path]::GetDirectoryName([string]$pins.canaryPrerequisite.qualificationPath), [IO.Path]::GetDirectoryName([string]$pins.p6Context.pinsPath))
    foreach ($row in $pins.files) { $protectedRoots += @([IO.Path]::GetDirectoryName([string]$row.path)) }
    foreach ($rootValue in @($protectedRoots | Select-Object -Unique)) {
        if ([string]::IsNullOrWhiteSpace([string]$rootValue)) { continue }
        $rootPath = [IO.Path]::GetFullPath([string]$rootValue)
        Require (-not (Test-PathUnder $outputCanonical $rootPath) -and -not (Test-PathUnder $rootPath $outputCanonical)) 'EXTERNAL_OUTPUT_REQUIRED'
    }
    Require (-not (Test-Path -LiteralPath $outputCanonical)) 'OUTPUT_EXISTS'

    $phase = 'SOURCE_PREFLIGHT'
    # Load-AuthenticatedContexts already validated and retained the pinned source inventory.
    $moduleSource = Require-PinnedSourceFile 'scripts/VerifierIsolation.psm1' 'SOURCE_HELPER_PIN'
    Require (Test-OrdinalPathEqual $moduleSource $modulePath) 'SOURCE_HELPER_PIN'
    $preflight = Invoke-Auditor 'preflight'
    $sourceChecks.preflight = $preflight.summary
    Require ($preflight.process.TerminationProven -eq $true) 'PREFLIGHT_TERMINATION'

    # Input, source, role, tool and prerequisite checks all passed before creating outputs.
    $suiteId = [Guid]::NewGuid().ToString('D').ToLowerInvariant()
    Require ($suiteId -cmatch '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') 'SUITE_ID'
    try { [void](New-Item -ItemType Directory -Path $outputCanonical -ErrorAction Stop) }
    catch { throw 'OUTPUT_CREATE' }
    $outputCreated = $true
    Assert-VerifierNoReparseAncestors $outputCanonical
    $outputItem = Get-Item -LiteralPath $outputCanonical -Force
    $outputCreationTicks = [string]$outputItem.CreationTimeUtc.Ticks
    Require ($outputCreationTicks -match '^[0-9]+$' -and [long]$outputCreationTicks -gt 0) 'OUTPUT_IDENTITY'
    $hostPath = [IO.Path]::GetFullPath($PSCommandPath)
    $hostSha = Get-Hash $hostPath
    $hostStartTicks = [string]$owner.StartTime.ToUniversalTime().Ticks
    Require ($hostStartTicks -match '^[0-9]+$' -and [long]$hostStartTicks -gt 0) 'HOST_IDENTITY'
    Write-NewJson (Get-OutputPath 'host-launch-intent.json') ([ordered]@{
        schema = 'tsj-q100-geometric-prefix-host-launch-v1'
        status = 'LAUNCHED_NOT_ACCEPTED'
        mode = $Mode
        pid = [int]$PID
        startTicks = $hostStartTicks
        executable = [string]$owner.Path
        executableSha256 = $selectedPowerShellSha256
        script = $hostPath
        scriptSha256 = $hostSha
        inputPinsSha256 = $InputPinsSha256
        outputRoot = $outputCanonical
        outputCreationTicks = $outputCreationTicks
        compileLimitMs = 60000
        auditLimitMs = 60000
        nativeLimitMs = 480000
        consumerLimitMs = 180000
        cleanupLimitMs = 120000
        outerLimitMs = 1800000
    })
    Ensure-OutputDirectories
    $sentinelBytes = $utf8.GetBytes("P8_PREEXISTING_DATA_DO_NOT_CHANGE`n")
    Write-NewBytes (Get-OutputPath 'controls/P8_NONEMPTY_DATA/data/sentinel.txt') $sentinelBytes
    $preflightEvidence = Save-ProcessEvidenceAt 'source-preflight-process.json' 'source-preflight.stdout' 'source-preflight.stderr' $preflight.process
    $processRecords.preflight = Get-ProcessRecord $preflightEvidence.receipt $preflightEvidence.stdout $preflightEvidence.stderr ([double]$preflight.wallMs)

    $phase = 'SCRATCH_CREATE'
    $scratch = [IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetTempPath()) ('tsj-q100-geometric-prefix-study-r1-' + [Guid]::NewGuid().ToString('N'))))
    Assert-VerifierNoReparseAncestors ([IO.Path]::GetDirectoryName($scratch))
    Require (-not (Test-Path -LiteralPath $scratch)) 'SCRATCH_EXISTS'
    Write-NewJson (Get-OutputPath 'scratch-intent.json') ([ordered]@{
        status = 'INTENT_NOT_CREATION_PROOF'
        scratch = $scratch
        hostPid = [int]$PID
        hostStartTicks = $hostStartTicks
        executable = [string]$owner.Path
        runId = $suiteId
    })
    [void][IO.Directory]::CreateDirectory($scratch)
    $scratchCreated = $true
    $scratchTicks = [long](Get-Item -LiteralPath $scratch -Force).CreationTimeUtc.Ticks
    Require ($scratchTicks -gt 0) 'SCRATCH_IDENTITY'
    Assert-VerifierNoReparseAncestors $scratch

    $classes = Join-Path $scratch 'classes'
    $emptySourcePath = Join-Path $scratch 'empty-sourcepath'
    [void][IO.Directory]::CreateDirectory($classes)
    [void][IO.Directory]::CreateDirectory($emptySourcePath)
    $gwtUser = Require-PinnedSourceFile '.tools/gwt-2.7.0/gwt-user-2.7.0.jar' 'SOURCE_HASH'
    $gwtDev = Require-PinnedSourceFile '.tools/gwt-2.7.0/gwt-dev-2.7.0.jar' 'SOURCE_HASH'
    $paths = New-Object 'System.Collections.Generic.List[string]'
    foreach ($row in $p6Pins.sourceRows) {
        if ([string]$row.role -ceq 'BASE_SOURCE' -and [string]$row.path -match '\.java$') {
            $relative = ([string]$row.path).Replace('\', '/')
            if ($relative -cnotin $excludedBaseSources) {
                $sourcePath = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot ([string]$row.path)))
                Require-CanonicalExistingFile $sourcePath 'BASE_SOURCE_PATH' | Out-Null
                [void]$paths.Add($sourcePath)
            }
        }
    }
    foreach ($role in $modeRoles) {
        $rolePath = Require-CanonicalExistingFile ([string]$canaryPins.modeFiles.$role) 'MODE_ROLE'
        if ($role -ceq 'writer') {
            $writerRelativePath = 'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java'
            $writerRows = @($canaryPins.sourceRows | Where-Object {
                [string]::Equals([string]$_.path, $writerRelativePath, [StringComparison]::Ordinal)
            })
            Require ($writerRows.Count -eq 1) 'MODE_ROLE_PIN'
            $writerRow = $writerRows[0]
            Require-ExactKeys $writerRow @('path', 'bytes', 'role', 'sha256') 'MODE_ROLE_PIN'
            Require ([string]$writerRow.role -ceq 'WRITER' -and
                (Test-OrdinalPathEqual $rolePath ([IO.Path]::GetFullPath((Join-Path $RepositoryRoot $writerRelativePath)))) ) 'MODE_ROLE_PIN'
            Require-ExactInteger $writerRow.bytes 1 ([long]::MaxValue) 'MODE_ROLE_PIN'
            Require ([string]$writerRow.sha256 -cmatch '^[0-9a-f]{64}$') 'MODE_ROLE_PIN'
            $writerInfo = Get-Item -LiteralPath $rolePath
            Require ($writerInfo.Length -eq [long]$writerRow.bytes -and
                (Get-Hash $rolePath) -ceq [string]$writerRow.sha256) 'MODE_ROLE_PIN'
        }
        else {
            Require-PinnedFileFromRows $canaryPins.files $rolePath 'MODE_ROLE_PIN' | Out-Null
        }
        [void]$paths.Add($rolePath)
    }
    $bridgeRaw = [IO.File]::ReadAllBytes($bridgeManifestPath)
    Require ($bridgeRaw.Length -le 65536) 'BRIDGE_MANIFEST_SIZE'
    $bridgeDecoder = [Text.UTF8Encoding]::new($false, $true)
    try { $bridgeText = $bridgeDecoder.GetString($bridgeRaw) } catch { throw 'BRIDGE_MANIFEST_ENCODING' }
    try { $bridgeRows = ConvertFrom-Json -InputObject $bridgeText -ErrorAction Stop } catch { throw 'BRIDGE_MANIFEST_SCHEMA' }
    Require ($bridgeRows -is [array] -and $bridgeRows.Count -eq 3) 'BRIDGE_CENSUS'
    $seenBridges = @{}
    foreach ($bridge in $bridgeRows) {
        Require-ExactKeys $bridge @('path', 'bytes', 'sha256') 'BRIDGE_MANIFEST_SCHEMA'
        Require ([string]$bridge.path -cin $bridgeNames -and -not $seenBridges.ContainsKey([string]$bridge.path)) 'BRIDGE_CENSUS'
        $seenBridges.Add([string]$bridge.path, $true)
        Require-ExactInteger $bridge.bytes 1 ([long]::MaxValue) 'BRIDGE_MANIFEST_SCHEMA'
        Require ([string]$bridge.sha256 -cmatch '^[0-9a-f]{64}$') 'BRIDGE_MANIFEST_SCHEMA'
        $from = Join-Path $bridgeRoot ('bridge-' + [string]$bridge.path)
        Require-PinnedFileFromRows $p6Pins.files $from 'BRIDGE_SOURCE_PIN' | Out-Null
        $sourceInfo = Get-Item -LiteralPath $from
        Require ($sourceInfo.Length -eq [long]$bridge.bytes -and (Get-Hash $from) -ceq [string]$bridge.sha256) 'BRIDGE_SOURCE_HASH'
        $to = Join-Path $scratch ([string]$bridge.path)
        [IO.File]::Copy($from, $to, $false)
        $copyInfo = Get-Item -LiteralPath $to
        Require ($copyInfo.Length -eq [long]$bridge.bytes -and (Get-Hash $to) -ceq [string]$bridge.sha256) 'COPIED_BRIDGE_HASH'
        $bridgeCopies += @([ordered]@{ path = $to; bytes = [long]$bridge.bytes; sha256 = [string]$bridge.sha256 })
        [void]$paths.Add([IO.Path]::GetFullPath($to))
    }
    foreach ($bridgeName in $bridgeNames) { Require ($seenBridges.ContainsKey($bridgeName)) 'BRIDGE_CENSUS' }
    Require ($paths.Count -eq $canaryCompilerSourcesRequired) 'CANARY_COMPILE_CENSUS'
    $nativeStudyPath = Require-PinnedFile ([string]$pins.roles.native.study) 'NATIVE_STUDY_PIN'
    $nativeInspectorPath = Require-PinnedFile ([string]$pins.roles.native.inspector) 'NATIVE_INSPECTOR_PIN'
    $nativeStudyPath = [string]$pins.roles.native.study
    $nativeInspectorPath = [string]$pins.roles.native.inspector
    foreach ($nativeSource in @($nativeStudyPath, $nativeInspectorPath)) {
        $nativeFull = Require-CanonicalExistingFile $nativeSource 'NATIVE_SOURCE_PATH'
        [void]$paths.Add($nativeFull)
    }
    Require ($paths.Count -eq $compilerSourcesRequired) 'COMPILE_CENSUS'
    $seenSources = @{}
    foreach ($sourcePath in $paths) {
        Require ([IO.Path]::IsPathRooted($sourcePath) -and (Test-OrdinalPathEqual $sourcePath ([IO.Path]::GetFullPath($sourcePath)))) 'COMPILER_PATH'
        Require (-not $seenSources.ContainsKey($sourcePath)) 'COMPILER_DUPLICATE'
        $seenSources.Add($sourcePath, $true)
    }
    $argsFile = Join-Path $scratch 'sources.txt'
    $sourceLines = @($paths | ForEach-Object { '"' + ([string]$_).Replace('\', '/') + '"' })
    [IO.File]::WriteAllLines($argsFile, [string[]]$sourceLines, $utf8)
    $compilerListPath = Get-OutputPath 'compiler-sources.txt'
    $compilerListBytes = [long](Get-Item -LiteralPath $argsFile).Length
    Require ($compilerListBytes -le 1048576 -and $writtenOutputBytes + $compilerListBytes -le 41943040) 'COMPILER_LIST_LIMIT'
    [IO.File]::Copy($argsFile, $compilerListPath, $false)
    $writtenOutputBytes += $compilerListBytes
    Require ((Get-Hash $argsFile) -ceq (Get-Hash $compilerListPath)) 'COMPILER_LIST_COPY'
    Write-NewJson (Get-OutputPath 'bridge-copies.json') $bridgeCopies
    $classpath = [string]::Join(';', @($classes, $gwtUser, $gwtDev))
    $compilerArgs = @('-source', '7', '-target', '7', '-encoding', 'UTF-8', '-classpath', $classpath,
        '-sourcepath', $emptySourcePath, '-d', $classes, ('@' + $argsFile))
    Write-NewJson (Get-OutputPath 'compiler-launch-intent.json') ([ordered]@{
        status = 'INTENT_NOT_START_PROOF'
        executable = $javac
        sha256 = $selectedJavacSha256
        arguments = $compilerArgs
        sourceListPath = $argsFile
        sourceListSha256 = Get-Hash $argsFile
        compilerSourceCount = [long]$paths.Count
        limitMs = 60000
    })

    $phase = 'COMPILE'
    $compilerInvoked = $true
    $compilerCall = Invoke-BoundedChild -FilePath $javac -Arguments $compilerArgs -TimeoutMilliseconds 60000 -CallPhase 'COMPILE'
    $compilerProcess = $compilerCall.process
    $compileWall = [double]$compilerCall.wallMs
    Require-InvocationReceipt $compilerProcess $javac $compilerArgs 'COMPILE_PROCESS_IDENTITY'
    Test-VerifierLogsRemoved $compilerProcess
    Require ($compileWall -ge 0 -and $compileWall -le 60000) 'COMPILE_WALL_LIMIT'
    $compilerEvidence = Save-ProcessEvidenceAt 'compiler-process.json' 'compiler.stdout' 'compiler.stderr' $compilerProcess
    $processRecords.compiler = Get-ProcessRecord $compilerEvidence.receipt $compilerEvidence.stdout $compilerEvidence.stderr $compileWall
    Require ($compilerProcess.ExitCode -eq 0 -and $compilerProcess.TerminationProven -eq $true) 'COMPILE_FAILED'
    $returnedChildEvidence = $null
    Require ((Get-Hash $argsFile) -ceq (Get-Hash $compilerListPath)) 'COMPILER_LIST_CHANGED'
    Verify-BridgeCopies
    $sourceChecks.preflight = $preflight.summary
    $phase = 'SOURCE_PRE_NATIVE'
    $preNative = Invoke-Auditor 'prenative'
    $sourceChecks.preNative = $preNative.summary
    Verify-BridgeCopies

    $classesCp = $classpath
    $p6Main = 'com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy'
    $p8Main = 'com.lushprojects.circuitjs1.client.CanonicalGeometricReasonCanary'
    $studyMain = 'com.lushprojects.circuitjs1.client.CanonicalGeometricPrefixStudy'
    $p6CanaryArgs = @('-ea', '-cp', $classesCp, $p6Main, '--canary')
    Write-NewJson (Get-OutputPath 'p6-canary-launch-intent.json') ([ordered]@{ status = 'INTENT_NOT_START_PROOF'; executable = $java; sha256 = $selectedJavaSha256; arguments = $p6CanaryArgs; limitMs = 480000 })
    $p8CanaryArgs = @('-ea', '-cp', $classesCp, $p8Main)
    Write-NewJson (Get-OutputPath 'p8-canary-launch-intent.json') ([ordered]@{ status = 'INTENT_NOT_START_PROOF'; executable = $java; sha256 = $selectedJavaSha256; arguments = $p8CanaryArgs; limitMs = 480000 })
    $phase = 'P6_CANARY'
    $nativeInvoked = $true
    $nativePhaseStartOffsetMs = $hostClock.Elapsed.TotalMilliseconds
    $p6Call = Invoke-NativeChild $java $p6CanaryArgs 'P6_CANARY'
    $p6Process = $p6Call.process
    Require-InvocationReceipt $p6Process $java $p6CanaryArgs 'P6_CANARY_PROCESS_IDENTITY'
    Test-VerifierLogsRemoved $p6Process
    $p6CanaryWallMs = [double]$p6Call.wallMs
    $p6Evidence = Save-ProcessEvidenceAt 'p6-canary-process.json' 'p6-canary.stdout' 'p6-canary.stderr' $p6Process
    $processRecords.p6Canary = Get-ProcessRecord $p6Evidence.receipt $p6Evidence.stdout $p6Evidence.stderr $p6CanaryWallMs
    Require ($p6Process.ExitCode -eq 0 -and $p6Process.TerminationProven -eq $true -and [string]::IsNullOrEmpty([string]$p6Process.Stderr)) 'P6_CANARY_PROCESS'
    $p6Summary = Parse-P6Canary ([string]$p6Process.Stdout)
    $returnedChildEvidence = $null
    $nativeCanaries.p6 = [ordered]@{ schema = [string]$p6Summary.schema; status = [string]$p6Summary.status;
        assertions = [long]$p6Summary.assertions; blockerAssertions = [long]$p6Summary.blockerAssertions;
        stageAssertions = [long]$p6Summary.stageAssertions; elapsedNanos = [long]$p6Summary.elapsedNanos;
        groups = [long]$p6Summary.groups; maximumGroupNanos = [long]$p6Summary.maximumGroupNanos; layoutQualification = $false }
    Verify-BridgeCopies

    $phase = 'P8_LITERAL_CANARY'
    $p8Call = Invoke-NativeChild $java $p8CanaryArgs 'P8_LITERAL_CANARY'
    $p8Process = $p8Call.process
    Require-InvocationReceipt $p8Process $java $p8CanaryArgs 'P8_CANARY_PROCESS_IDENTITY'
    Test-VerifierLogsRemoved $p8Process
    $p8CanaryWallMs = [double]$p8Call.wallMs
    $p8Evidence = Save-ProcessEvidenceAt 'p8-canary-process.json' 'p8-canary.stdout' 'p8-canary.stderr' $p8Process
    $processRecords.p8Canary = Get-ProcessRecord $p8Evidence.receipt $p8Evidence.stdout $p8Evidence.stderr $p8CanaryWallMs
    Require ($p8Process.ExitCode -eq 0 -and $p8Process.TerminationProven -eq $true -and [string]::IsNullOrEmpty([string]$p8Process.Stderr)) 'P8_CANARY_PROCESS'
    $p8Summary = Parse-P8Canary ([string]$p8Process.Stdout)
    $returnedChildEvidence = $null
    $nativeCanaries.p8 = [ordered]@{ schema = [string]$p8Summary.schema; status = [string]$p8Summary.status;
        assertions = [long]$p8Summary.assertions; caseCount = [long]$p8Summary.caseCount; workNanos = [long]$p8Summary.workNanos;
        groups = [long]$p8Summary.groups; maximumGroupNanos = [long]$p8Summary.maximumGroupNanos;
        totalObservationUnits = [long]$p8Summary.totalObservationUnits; layoutQualification = $false }
    Verify-BridgeCopies

    $controlRunId = '00000000-0000-4000-8000-000000000008'
    $p8BadMode = Invoke-P8NegativeControl 'P8_BAD_MODE' 'badMode' 'p8-bad-mode' 'INVALID_MODE' 0 'NOT_A_MODE' $controlRunId $InputPinsSha256 $wireSha256 $sourceInventorySha256 'controls/P8_BAD_MODE/data'
    $p8BadUuid = Invoke-P8NegativeControl 'P8_BAD_UUID' 'badUuid' 'p8-bad-uuid' 'INVALID_RUN_UUID' 1 'NULL' 'NOT_A_UUID' $InputPinsSha256 $wireSha256 $sourceInventorySha256 'controls/P8_BAD_UUID/data'
    $p8BadInput = Invoke-P8NegativeControl 'P8_BAD_INPUT_SHA' 'badInputSha' 'p8-bad-input-sha' 'INVALID_INPUT_SHA256' 2 'NULL' $controlRunId 'not-a-sha' $wireSha256 $sourceInventorySha256 'controls/P8_BAD_INPUT_SHA/data'
    $p8Nonempty = Invoke-P8NegativeControl 'P8_NONEMPTY_DATA' 'nonemptyData' 'p8-nonempty-data' 'OUTPUT_DIRECTORY_NOT_EMPTY' 5 'NULL' $controlRunId $InputPinsSha256 $wireSha256 $sourceInventorySha256 'controls/P8_NONEMPTY_DATA/data'
    $nativeCanaries.inputControls = @($p8BadMode, $p8BadUuid, $p8BadInput, $p8Nonempty)
    Verify-BridgeCopies

    if ($Mode -ceq 'SUITE') {
        $runNull = [Guid]::NewGuid().ToString('D').ToLowerInvariant()
        $runObserve = [Guid]::NewGuid().ToString('D').ToLowerInvariant()
        $runCallback = [Guid]::NewGuid().ToString('D').ToLowerInvariant()
        $runReason = [Guid]::NewGuid().ToString('D').ToLowerInvariant()
        $phase = 'BASE_NULL'
        $nullOperation = Invoke-P8Complete 'NULL' 'nullMode' $runNull
        $phase = 'BASE_OBSERVE'
        $observeOperation = Invoke-P8Complete 'OBSERVE' 'observeMode' $runObserve
        $phase = 'BASE_CALLBACK_THROW'
        Invoke-P6CallbackThrow $runCallback
        $phase = 'BASE_REASON_THROW'
        Invoke-P8ReasonThrow $runReason
        Require ($operations.Count -eq 4) 'OPERATION_CENSUS'
        Verify-BridgeCopies
    }

    $phase = 'POST_SOURCE'
    $cleanupStartOffsetMs = $hostClock.Elapsed.TotalMilliseconds
    $postSource = Invoke-Auditor 'post'
    $sourceChecks.postSource = $postSource.summary
    Require ((Get-Hash $argsFile) -ceq (Get-Hash $compilerListPath)) 'COMPILER_LIST_CHANGED'
    Verify-BridgeCopies
    Require ((Get-Item -LiteralPath $scratch -Force).CreationTimeUtc.Ticks -eq [long]$scratchTicks) 'SCRATCH_IDENTITY_CHANGED'
    Remove-OwnedScratch
    Require ($scratchRemoved) 'SCRATCH_CLEANUP_UNPROVEN'

    $phase = 'TIMING_CLOSURE'
    $nativePhaseElapsedMs = [double]$cleanupStartOffsetMs - [double]$nativePhaseStartOffsetMs
    $nativePhaseOverheadMs = $nativePhaseElapsedMs - [double]$childWallSumMs
    if ($Mode -ceq 'SUITE') { $workCreditsMs = [double]$operationWorkCredits['NULL'] + [double]$operationWorkCredits['OBSERVE'] }
    $nativePhaseChargeMs = $nativePhaseElapsedMs - [double]$workCreditsMs
    $childResidualsMs = [double]$childWallSumMs - [double]$workCreditsMs
    Require ($nativePhaseStartOffsetMs -ge 0 -and $cleanupStartOffsetMs -ge $nativePhaseStartOffsetMs -and
        $nativePhaseElapsedMs -ge $childWallSumMs -and $nativePhaseOverheadMs -ge 0 -and
        $workCreditsMs -ge 0 -and $nativePhaseChargeMs -ge $childResidualsMs -and $nativePhaseChargeMs -ge 0) 'NATIVE_PHASE_ARITHMETIC'
    Require ($hostClock.Elapsed.TotalMilliseconds -le 1800000) 'HOST_OUTER_LIMIT'

    $phase = 'OUTPUT_INDEX'
    $script:writtenOutputBytes = Get-OutputCounterWithNativeFiles ([long]$script:writtenOutputBytes) ([string]$Mode) ([string[]]$suiteOutputFiles)
    New-SuiteIndexAndFooter
    $closure = Get-OutputClosure
    $hostElapsedBeforeFinalWriteMs = [double]$hostClock.Elapsed.TotalMilliseconds
    $compilerInfo = Get-Item -LiteralPath $compilerListPath
    $hostObject = [ordered]@{
        pid = [int]$PID
        startTicks = $hostStartTicks
        executable = [string]$owner.Path
        executableSha256 = $selectedPowerShellSha256
        script = $hostPath
        scriptSha256 = $hostSha
    }
    $hostResult = [ordered]@{
        schema = 'tsj-q100-geometric-prefix-host-v1'
        status = if ($Mode -ceq 'CANARY') { 'HOST_CANARY_COMPLETE_READER_PENDING' } else { 'HOST_COMPLETE_READER_PENDING' }
        mode = $Mode
        suiteId = $suiteId
        inputSha256 = $InputPinsSha256
        sourceSha256 = $sourceInventorySha256
        wireSha256 = $wireSha256
        modeOrder = @($modeOrder)
        host = $hostObject
        processes = $processRecords
        sourceChecks = $sourceChecks
        compiler = [ordered]@{ sourceCount = [long]$paths.Count; sourceList = 'compiler-sources.txt';
            sourceListBytes = [long]$compilerInfo.Length; sourceListSha256 = Get-Hash $compilerListPath }
        bridgeCopies = [ordered]@{ receipt = 'bridge-copies.json'; count = [long]$bridgeCopies.Count;
            verifiedAfterCompile = $true; verifiedBeforeNative = $true; verifiedAfterNative = $true }
        nativeCanaries = $nativeCanaries
        operations = @($operations)
        timing = [ordered]@{ nativePhaseStartOffsetMs = [double]$nativePhaseStartOffsetMs;
            cleanupStartOffsetMs = [double]$cleanupStartOffsetMs; nativePhaseElapsedMs = [double]$nativePhaseElapsedMs;
            childWallSumMs = [double]$childWallSumMs; workCreditsMs = [double]$workCreditsMs;
            nativePhaseChargeMs = [double]$nativePhaseChargeMs; nativePhaseOverheadMs = [double]$nativePhaseOverheadMs;
            hostElapsedBeforeFinalWriteMs = [double]$hostElapsedBeforeFinalWriteMs }
        cleanup = [ordered]@{ scratchPath = $scratch; scratchCreationTicks = [string]$scratchTicks; scratchRemoved = [bool]$scratchRemoved;
            boundMs = 120000; formula = 'nativePhaseChargeMs+(parentFullHostElapsedMs-cleanupStartOffsetMs)' }
        outputClosure = $closure
        limits = [ordered]@{ compileMs = 60000; nativeMs = 480000; auditMs = 60000; readerMs = 180000; cleanupMs = 120000;
            outerMs = 1800000; workNanos = 90000000000; groups = 640; groupNanos = 5000000000; observationUnits = 32768;
            operationBytes = 8388608; hostEvidenceBytes = 8388608; suiteBytes = 41943040; lineBytes = 65536;
            compilerListBytes = 1048576; inventoryBytes = 1048576 }
        layoutQualification = $false
    }
    $hostResultPath = Get-OutputPath 'host-result.json'
    Write-NewJson $hostResultPath $hostResult
    Verify-OutputAfterResult $closure
    Require ($hostClock.Elapsed.TotalMilliseconds -le 1800000) 'HOST_OUTER_LIMIT'
    $indexPath = Get-OutputPath 'suite-index.json'
    $footerPath = Get-OutputPath 'suite-footer.json'
    $success = [ordered]@{
        schema = 'tsj-q100-geometric-prefix-host-complete-v1'
        status = [string]$hostResult.status
        mode = $Mode
        suiteId = $suiteId
        inputSha256 = $InputPinsSha256
        sourceSha256 = $sourceInventorySha256
        wireSha256 = $wireSha256
        hostResult = [ordered]@{ path = 'host-result.json'; bytes = [long](Get-Item -LiteralPath $hostResultPath).Length; sha256 = Get-Hash $hostResultPath }
        suiteIndex = [ordered]@{ path = 'suite-index.json'; bytes = [long](Get-Item -LiteralPath $indexPath).Length; sha256 = Get-Hash $indexPath }
        suiteFooter = [ordered]@{ path = 'suite-footer.json'; bytes = [long](Get-Item -LiteralPath $footerPath).Length; sha256 = Get-Hash $footerPath }
        layoutQualification = $false
    }
    $successJson = ConvertTo-Json -InputObject $success -Depth 20 -Compress
    $successLine = 'P8_PREFIX_HOST_COMPLETE ' + $successJson
    Require ($utf8.GetByteCount($successLine + "`r`n") -le 65536) 'HOST_MARKER_SIZE'
    [Console]::Out.Write($successLine + "`r`n")
    [Console]::Out.Flush()
}
catch {
    $firstFailure = $_
    $failureMessage = [string]$firstFailure.Exception.Message
    if ([string]::IsNullOrWhiteSpace($failureMessage)) { $failureMessage = 'HOST_FAILURE' }
    $failureCode = $failureMessage
    if ($null -eq $failedProcessReturn -and $null -ne $returnedChildEvidence) { $failedProcessReturn = $returnedChildEvidence }
    if ($registeredProcessCaptures.Count -gt 0) {
        try { Save-FailureProcessBundle }
        catch {
            $failureEvidenceWriteError = Get-FailureEvidenceErrorText $_.Exception
            $failureBundleResult = [ordered]@{ root = $null; manifestPath = $null; manifestSha256 = $null; complete = $false; writeError = $failureEvidenceWriteError }
        }
    }
    else {
        $failureBundleResult = $null
        $failureEvidenceWriteError = $null
    }
    $terminationUnproven = ($null -ne $failedProcessCall)
    if ($null -ne $failedProcessReturn) {
        $returnedProcess = $failedProcessReturn.process
        $terminationProperty = if ($null -eq $returnedProcess) { $null } else { $returnedProcess.PSObject.Properties['TerminationProven'] }
        if ($null -eq $terminationProperty -or $terminationProperty.Value -isnot [bool] -or $terminationProperty.Value -ne $true) { $terminationUnproven = $true }
    }
    if ($scratchCreated -and -not $scratchRemoved) {
        if (-not (Test-Path -LiteralPath $scratch)) { $cleanupFailure = 'SCRATCH_CLEANUP_UNPROVEN' }
        elseif ($terminationUnproven) { $cleanupFailure = 'PROCESS_TERMINATION_UNPROVEN' }
        else {
            try { Remove-OwnedScratch }
            catch { $cleanupFailure = [string]$_.Exception.Message }
        }
    }
    if ($outputCreated) {
        try {
            $failedProcessReturnSummary = $null
            if ($null -ne $failedProcessReturn) {
                $captureOrdinal = $null
                if ($null -ne $failedProcessReturn.process) {
                    for ($captureIndex = 0; $captureIndex -lt $registeredProcessCaptures.Count; $captureIndex++) {
                        if ([object]::ReferenceEquals($registeredProcessCaptures[$captureIndex].process, $failedProcessReturn.process)) {
                            $captureOrdinal = [int]$registeredProcessCaptures[$captureIndex].ordinal
                            break
                        }
                    }
                }
                $failedProcessReturnSummary = [ordered]@{
                    phase = [string]$failedProcessReturn.phase
                    wallMs = [double]$failedProcessReturn.wallMs
                    captureOrdinal = $captureOrdinal
                }
            }
            $failureRecord = [ordered]@{ schema = 'tsj-q100-geometric-prefix-host-failure-v1'; status = 'FAIL'; phase = $phase;
                errorCode = $failureCode; message = $failureMessage; mode = $Mode; suiteId = $suiteId;
                inputSha256 = $InputPinsSha256; sourceSha256 = $sourceInventorySha256; wireSha256 = $wireSha256;
                cleanupError = if ($null -eq $cleanupFailure) { $null } else { [string]$cleanupFailure };
                scratchPath = $scratch; scratchCreationTicks = if ($null -eq $scratchTicks) { $null } else { [string]$scratchTicks };
                scratchRemoved = [bool]$scratchRemoved; failedProcessCall = $failedProcessCall;
                failedProcessReturn = $failedProcessReturnSummary; failureBundle = $failureBundleResult;
                hostElapsedMs = [double]$hostClock.Elapsed.TotalMilliseconds }
            $failurePath = Get-OutputPath 'host-failure.json'
            if (Test-Path -LiteralPath $failurePath) { throw 'HOST_FAILURE_SUMMARY_PATH_EXISTS' }
            Write-NewJson $failurePath $failureRecord
        }
        catch { $hostFailureSummaryWriteError = Get-FailureEvidenceErrorText $_.Exception }
    }
    try {
        $reject = [ordered]@{ code = [string]$failureCode; phase = [string]$phase; outputCreated = [bool]$outputCreated;
            scratchCreated = [bool]$scratchCreated; compilerInvoked = [bool]$compilerInvoked; nativeInvoked = [bool]$nativeInvoked;
            cleanupError = if ($null -eq $cleanupFailure) { $null } else { [string]$cleanupFailure } }
        $rejectJson = ConvertTo-Json -InputObject $reject -Depth 8 -Compress
        [Console]::Error.Write('P8_PREFIX_HOST_REJECT ' + $rejectJson + "`r`n")
        [Console]::Error.Flush()
    }
    catch {
        [Console]::Error.Write('P8_PREFIX_HOST_REJECT {"code":"HOST_REJECTION_FRAME_FAILURE","phase":"REJECTION_FRAME","outputCreated":false,"scratchCreated":false,"compilerInvoked":false,"nativeInvoked":false,"cleanupError":null}' + "`r`n")
        [Console]::Error.Flush()
    }
    if ($null -ne $failureEvidenceWriteError -or $null -ne $hostFailureSummaryWriteError) {
        $evidenceDiagnostic = [ordered]@{
            code = 'P8_PREFIX_FAILURE_EVIDENCE'
            failureBundle = $failureBundleResult
            failureEvidenceWriteError = $failureEvidenceWriteError
            hostFailureSummaryWriteError = $hostFailureSummaryWriteError
        }
        try {
            $evidenceJson = ConvertTo-Json -InputObject $evidenceDiagnostic -Depth 8 -Compress
            Require ($utf8.GetByteCount('P8_PREFIX_FAILURE_EVIDENCE ' + $evidenceJson + "`r`n") -le 65536) 'FAILURE_EVIDENCE_DIAGNOSTIC_SIZE'
            [Console]::Error.Write('P8_PREFIX_FAILURE_EVIDENCE ' + $evidenceJson + "`r`n")
            [Console]::Error.Flush()
        }
        catch {
            $diagnosticError = Get-FailureEvidenceErrorText $_.Exception
            $fallbackEvidence = [ordered]@{
                code = 'P8_PREFIX_FAILURE_EVIDENCE'
                failureBundle = $failureBundleResult
                failureEvidenceWriteError = $failureEvidenceWriteError
                hostFailureSummaryWriteError = $hostFailureSummaryWriteError
                diagnosticWriteError = $diagnosticError
            }
            $fallbackJson = ConvertTo-Json -InputObject $fallbackEvidence -Depth 8 -Compress
            [Console]::Error.Write('P8_PREFIX_FAILURE_EVIDENCE ' + $fallbackJson + "`r`n")
            [Console]::Error.Flush()
        }
    }
    exit 1
}

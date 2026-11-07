param(
    [Parameter(Mandatory = $true)][string]$RepositoryRoot,
    [Parameter(Mandatory = $true)][string]$InputPinsPath,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{64}$')][string]$InputPinsSha256,
    [Parameter(Mandatory = $true)][string]$OutputRoot,
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$PythonExe
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
$sourceRowsRequired = 1084
$productionFilesRequired = 850
$canaryCompilerSourcesRequired = 1081
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
$outputFiles = @(
    'host-launch.json',
    'scratch-intent.json',
    'source-preflight-process.json', 'source-preflight.stdout', 'source-preflight.stderr',
    'compiler-launch-intent.json', 'compiler-process.json', 'compiler.stdout', 'compiler.stderr',
    'compiler-sources.txt',
    'bridge-copies.json',
    'source-prenative-process.json', 'source-prenative.stdout', 'source-prenative.stderr',
    'p6-canary-launch-intent.json', 'p6-canary-process.json', 'p6-canary.stdout', 'p6-canary.stderr',
    'p8-canary-launch-intent.json', 'p8-canary-process.json', 'p8-canary.stdout', 'p8-canary.stderr',
    'source-post-process.json', 'source-post.stdout', 'source-post.stderr'
)
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
$owner = $null
$pins = $null
$p6Pins = $null
$modulePath = $null
$java = $null
$javac = $null
$classpath = $null
$compilerArgs = $null
$argsFile = $null
$bridgeCopies = @()
$processRecords = [ordered]@{}
$sourceChecks = [ordered]@{}
$nativeCanaries = [ordered]@{}
$failureCode = $null
$failureMessage = $null
$cleanupFailure = $null
$failedProcessCall = $null
$failedProcessReturn = $null
$returnedChildEvidence = $null
$writtenOutputBytes = [long]0

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
    Require ([long]$script:writtenOutputBytes + [long]$Bytes.Length -le 8388608) 'AGGREGATE_OUTPUT_LIMIT'
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
    $prefix = 'PASS_Q100_GEOMETRIC_CANARY_INPUTS_ONLY'
    $value = Parse-OneJsonMarker $Text $prefix 'INPUT_AUDIT_MARKER'
    Require-ExactKeys $value @('status', 'sourceRows', 'productionFiles', 'extraFiles', 'pinsSha256') 'INPUT_AUDIT_SCHEMA'
    Require ([string]::Equals([string]$value.status, $prefix, [StringComparison]::Ordinal)) 'INPUT_AUDIT_STATUS'
    Require-ExactInteger $value.sourceRows 1084 1084 'SOURCE_CENSUS'
    Require-ExactInteger $value.productionFiles 850 850 'SOURCE_CENSUS'
    Require-ExactInteger $value.extraFiles 0 ([int]::MaxValue) 'SOURCE_CENSUS'
    Require ([string]::Equals([string]$value.pinsSha256, $InputPinsSha256, [StringComparison]::Ordinal)) 'INPUT_PINS_HASH'
    return [ordered]@{
        status = [string]$value.status
        sourceRows = [long]$value.sourceRows
        productionFiles = [long]$value.productionFiles
        extraFiles = [long]$value.extraFiles
        pinsSha256 = [string]$value.pinsSha256
    }
}

function Get-AuditorFailureCode([string]$Text) {
    $match = [regex]::Match($Text, '\AP8_INPUT_AUDIT_FAIL (?<code>[A-Z0-9_]+)(?:\r?\n)?\z')
    if (-not $match.Success) { return 'INPUT_AUDIT_FAILED' }
    $code = $match.Groups['code'].Value
    if ($code -eq 'EXTRA_HASH') { return 'EXTRA_INPUT_HASH' }
    return $code
}

function Invoke-Auditor([string]$Stage) {
    $recordKey = switch -CaseSensitive ($Stage) {
        'preflight' { 'preflight' }
        'prenative' { 'preNative' }
        'post' { 'postSource' }
        default { throw 'AUDIT_STAGE' }
    }
    $args = @('-I', '-B', [string]$pins.auditorPath, $InputPinsPath, $InputPinsSha256)
    $call = Invoke-BoundedChild -FilePath $PythonExe -Arguments $args -TimeoutMilliseconds 60000 -CallPhase ('AUDIT_' + $Stage.ToUpperInvariant())
    $proc = $call.process
    $wall = [double]$call.wallMs
    try {
        Require-InvocationReceipt $proc $PythonExe $args 'AUDIT_PROCESS_IDENTITY'
        Test-VerifierLogsRemoved $proc
        Require ($wall -ge 0 -and $wall -le 60000) ($Stage.ToUpperInvariant() + '_WALL_LIMIT')
        if ($outputCreated) {
            $record = Save-ProcessEvidence ('source-' + $Stage) $proc
            $processRecords[$recordKey] = [ordered]@{ receipt = $record.receipt; stdout = $record.stdout; stderr = $record.stderr; wallMs = $wall }
        }
        if ($proc.ExitCode -ne 0 -or $proc.TerminationProven -ne $true -or
            -not [string]::IsNullOrEmpty([string]$proc.Stdout) -and $proc.Stdout.StartsWith('P8_INPUT_AUDIT_FAIL')) {
            throw (Get-AuditorFailureCode ([string]$proc.Stderr))
        }
        Require ($proc.ExitCode -eq 0 -and $proc.TerminationProven -eq $true -and
            [string]::IsNullOrEmpty([string]$proc.Stderr)) ($Stage.ToUpperInvariant() + '_AUDIT_PROCESS')
        $summary = Parse-AuditorPass ([string]$proc.Stdout)
        $script:returnedChildEvidence = $null
        return [pscustomobject]@{ process = $proc; wallMs = $wall; summary = $summary }
    }
    catch {
        $script:failedProcessReturn = [ordered]@{
            phase = ('AUDIT_' + $Stage.ToUpperInvariant())
            wallMs = $wall
            process = $proc
        }
        throw
    }
}

function Require-PinnedFile([string]$Path, [string]$Code) {
    $matches = @($pins.files | Where-Object { Test-OrdinalPathEqual ([string]$_.path) $Path })
    Require ($matches.Count -eq 1) $Code
    $row = $matches[0]
    Require-ExactInteger $row.bytes 0 ([long]::MaxValue) $Code
    Require ([string]$row.sha256 -cmatch '^[0-9a-f]{64}$') $Code
    $item = Get-Item -LiteralPath $Path
    Require ($item.Length -eq [long]$row.bytes -and (Get-Hash $Path) -ceq [string]$row.sha256) $Code
}

function Require-PinnedSourceFile([string]$RelativePath, [string]$Code) {
    Require ($pins.sourceRows -is [array] -and $p6Pins.sourceRows -is [array]) $Code
    $currentRows = @($pins.sourceRows | Where-Object {
        [string]::Equals([string]$_.path, $RelativePath, [StringComparison]::Ordinal)
    })
    $originalRows = @($p6Pins.sourceRows | Where-Object {
        [string]::Equals([string]$_.path, $RelativePath, [StringComparison]::Ordinal)
    })
    Require ($currentRows.Count -eq 1 -and $originalRows.Count -eq 1) $Code
    Require-ExactKeys $currentRows[0] @('path', 'bytes', 'role', 'sha256') $Code
    Require-ExactKeys $originalRows[0] @('path', 'bytes', 'role', 'sha256') $Code
    Require ([string]$currentRows[0].role -ceq 'BASE_SOURCE' -and
        [string]$originalRows[0].role -ceq 'BASE_SOURCE') $Code
    Require-ExactInteger $currentRows[0].bytes 0 ([long]::MaxValue) $Code
    Require-ExactInteger $originalRows[0].bytes 0 ([long]::MaxValue) $Code
    Require ([string]$currentRows[0].sha256 -cmatch '^[0-9a-f]{64}$' -and
        [string]$originalRows[0].sha256 -cmatch '^[0-9a-f]{64}$') $Code
    Require ([long]$currentRows[0].bytes -eq [long]$originalRows[0].bytes -and
        [string]$currentRows[0].sha256 -ceq [string]$originalRows[0].sha256) $Code
    $path = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot $RelativePath))
    $path = Require-CanonicalExistingFile $path $Code
    $info = Get-Item -LiteralPath $path
    Require ($info.Length -eq [long]$currentRows[0].bytes -and
        (Get-Hash $path) -ceq [string]$currentRows[0].sha256) $Code
    return $path
}

function Verify-AllManifestExtraPins {
    $hostPath = [IO.Path]::GetFullPath($PSCommandPath)
    foreach ($row in $pins.files) {
        $path = [string]$row.path
        Require ([IO.Path]::IsPathRooted($path) -and
            (Test-OrdinalPathEqual $path ([IO.Path]::GetFullPath($path)))) 'EXTRA_INPUT_HASH'
        if (Test-OrdinalPathEqual $path $hostPath) { continue }
        Require (Test-Path -LiteralPath $path -PathType Leaf) 'EXTRA_INPUT_HASH'
        Assert-LocalNoReparseAncestors $path
        $item = Get-Item -LiteralPath $path -Force
        Require ($item.Length -eq [long]$row.bytes -and (Get-Hash $path) -ceq [string]$row.sha256) 'EXTRA_INPUT_HASH'
    }
}

function Write-ChildFailureDiagnostic([string]$Prefix, $Record, [string]$LimitCode) {
    $json = ConvertTo-Json -InputObject $Record -Depth 40 -Compress
    $line = $Prefix + ' ' + $json
    $bytes = $utf8.GetBytes($line + "`n")
    if ($bytes.Length -gt 65536) {
        $script:failureCode = $LimitCode
        return
    }
    [Console]::Error.Write($line + "`n")
    [Console]::Error.Flush()
}

function Validate-InputManifest($Manifest) {
    $keys = @('schema', 'repositoryRoot', 'parent', 'p6PinsPath', 'p6PinsSha256', 'sourceRows',
        'files', 'modeFiles', 'bridgeManifest', 'bridgeRoot', 'javaHome', 'pythonExe', 'hostPath',
        'auditorPath', 'planPath', 'apiPath', 'hostContractPath')
    Require-ExactKeys $Manifest $keys 'INPUT_SCHEMA'
    Require ([string]$Manifest.schema -ceq 'tsj-q100-geometric-canary-inputs-v1') 'INPUT_SCHEMA'
    Require ([string]$Manifest.parent -ceq '0891474cadc4b9e00d7911e81c124721a11efb87') 'INPUT_CONTEXT'
    Require ($Manifest.sourceRows -is [array] -and
        $Manifest.sourceRows.Count -eq $sourceRowsRequired) 'SOURCE_CENSUS'
    Require ([string]$Manifest.repositoryRoot -ceq $RepositoryRoot) 'INPUT_CONTEXT'
    Require ([string]$Manifest.javaHome -ceq $JavaHome) 'TOOL_CONTEXT'
    Require ([string]$Manifest.pythonExe -ceq $PythonExe) 'TOOL_CONTEXT'
    Require ([string]$Manifest.hostPath -ceq [IO.Path]::GetFullPath($PSCommandPath)) 'SELF_PIN'
    Require ([string]$Manifest.planPath -ceq [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'PLAN_R1.md'))) 'PLAN_PATH'
    Require ([string]$Manifest.apiPath -ceq [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'API_SCHEMA_R1.md'))) 'API_PATH'
    Require ([string]$Manifest.hostContractPath -ceq [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'CANARY_HOST_R1.md'))) 'HOST_CONTRACT_PATH'
    Require ([string]$Manifest.auditorPath -ceq [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'audit_canary_inputs.py'))) 'AUDITOR_PATH'
    Require ([string]$Manifest.p6PinsSha256 -ceq $selectedP6PinsSha256) 'P6_PINS_HASH'
    Require ([string]$Manifest.p6PinsSha256 -cmatch '^[0-9a-f]{64}$') 'P6_PINS_HASH'
    Require-ExactKeys $Manifest.modeFiles $modeRoles 'MODE_ROLE'
    Require ($Manifest.files -is [array] -and $Manifest.files.Count -gt 0) 'INPUT_SCHEMA'
    foreach ($row in $Manifest.files) {
        Require-ExactKeys $row @('path', 'bytes', 'sha256') 'INPUT_SCHEMA'
        Require ([string]$row.path -and [string]$row.sha256 -cmatch '^[0-9a-f]{64}$') 'INPUT_SCHEMA'
        Require-ExactInteger $row.bytes 0 ([long]::MaxValue) 'INPUT_SCHEMA'
    }
    foreach ($role in $modeRoles) {
        $rolePath = [string]$Manifest.modeFiles.$role
        Require ([IO.Path]::IsPathRooted($rolePath) -and
            (Test-OrdinalPathEqual $rolePath ([IO.Path]::GetFullPath($rolePath)))) 'MODE_ROLE'
    }
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

function Get-OutputClosure {
    $actualFiles = [IO.Directory]::GetFiles($outputCanonical, '*', [IO.SearchOption]::TopDirectoryOnly)
    $actualDirectories = [IO.Directory]::GetDirectories($outputCanonical, '*', [IO.SearchOption]::TopDirectoryOnly)
    Require ($actualFiles.Count -eq $outputFiles.Count -and $actualDirectories.Count -eq 0) 'OUTPUT_CENSUS'
    $actualNames = @($actualFiles | ForEach-Object { [IO.Path]::GetFileName($_) })
    Require ($actualNames.Count -eq $outputFiles.Count) 'OUTPUT_CENSUS'
    foreach ($name in $outputFiles) {
        Require (@($actualNames | Where-Object { [string]::Equals($_, $name, [StringComparison]::Ordinal) }).Count -eq 1) 'OUTPUT_CENSUS'
    }
    $rows = @()
    [long]$total = 0
    foreach ($name in $outputFiles) {
        $path = Join-Path $outputCanonical $name
        Assert-VerifierNoReparseAncestors $path
        $info = Get-Item -LiteralPath $path
        $sha = Get-Hash $path
        $rows += @([ordered]@{ name = $name; bytes = [long]$info.Length; sha256 = $sha })
        $total += [long]$info.Length
        if ($name -like '*.json') { Require ($info.Length -le 65536) 'STRUCTURED_OUTPUT_LIMIT' }
        if ($name -eq 'compiler-sources.txt') { Require ($info.Length -le 1048576) 'COMPILER_LIST_LIMIT' }
    }
    Require ($total -le 8388608) 'AGGREGATE_OUTPUT_LIMIT'
    Require ($total -eq [long]$script:writtenOutputBytes) 'OUTPUT_CENSUS'
    return [ordered]@{ files = $rows; aggregateBytes = $total; hostResultExcluded = $true }
}

function Verify-OutputAfterResult($Closure) {
    $actualFiles = [IO.Directory]::GetFiles($outputCanonical, '*', [IO.SearchOption]::TopDirectoryOnly)
    $actualDirectories = [IO.Directory]::GetDirectories($outputCanonical, '*', [IO.SearchOption]::TopDirectoryOnly)
    Require ($actualFiles.Count -eq $allSuccessFiles.Count -and $actualDirectories.Count -eq 0) 'OUTPUT_CENSUS'
    foreach ($row in $Closure.files) {
        $path = Join-Path $outputCanonical ([string]$row.name)
        $info = Get-Item -LiteralPath $path
        Require ($info.Length -eq [long]$row.bytes -and (Get-Hash $path) -ceq [string]$row.sha256) 'OUTPUT_CHANGED_AFTER_CLOSURE'
    }
    $hostResultPath = Join-Path $outputCanonical 'host-result.json'
    $hostResultInfo = Get-Item -LiteralPath $hostResultPath
    Require ($hostResultInfo.Length -le 65536) 'HOST_RESULT_LIMIT'
    Require ([long]$Closure.aggregateBytes + [long]$hostResultInfo.Length -le 8388608) 'AGGREGATE_OUTPUT_LIMIT'
    Require ([long]$Closure.aggregateBytes + [long]$hostResultInfo.Length -eq [long]$script:writtenOutputBytes) 'OUTPUT_CENSUS'
}

function Remove-OwnedScratch {
    if (-not $scratchCreated -or $scratchRemoved) { return }
    Require (Test-Path -LiteralPath $scratch -PathType Container) 'SCRATCH_MISSING'
    Assert-VerifierNoReparseAncestors $scratch
    $current = Get-Item -LiteralPath $scratch
    Require ([long]$current.CreationTimeUtc.Ticks -eq [long]$scratchTicks) 'SCRATCH_IDENTITY_CHANGED'
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    Remove-VerifierOwnedTree $tempRoot $scratch
    Require (-not (Test-Path -LiteralPath $scratch)) 'SCRATCH_CLEANUP_UNPROVEN'
    $script:scratchRemoved = $true
}

try {
    $phase = 'HOST_CONTEXT'
    Require ($PSVersionTable.PSVersion.Major -eq 5 -and $PSVersionTable.PSVersion.Minor -eq 1) 'SELECTED_PS51_REQUIRED'
    $owner = Get-Process -Id $PID
    Require (Test-Path -LiteralPath $owner.Path -PathType Leaf) 'SELECTED_PS51_REQUIRED'
    Require ((Get-Hash ([string]$owner.Path)) -ceq $selectedPowerShellSha256) 'SELECTED_PS51_REQUIRED'
    Require ([IO.Path]::IsPathRooted($RepositoryRoot) -and [IO.Path]::IsPathRooted($InputPinsPath) -and
        [IO.Path]::IsPathRooted($OutputRoot) -and [IO.Path]::IsPathRooted($JavaHome) -and [IO.Path]::IsPathRooted($PythonExe)) 'ABSOLUTE_PATH_REQUIRED'
    $RepositoryRootFull = [IO.Path]::GetFullPath($RepositoryRoot)
    $InputPinsFull = [IO.Path]::GetFullPath($InputPinsPath)
    $JavaHomeFull = [IO.Path]::GetFullPath($JavaHome)
    $PythonExeFull = [IO.Path]::GetFullPath($PythonExe)
    Require ((Test-OrdinalPathEqual $RepositoryRoot $RepositoryRootFull) -and
        (Test-OrdinalPathEqual $InputPinsPath $InputPinsFull) -and
        (Test-OrdinalPathEqual $JavaHome $JavaHomeFull) -and
        (Test-OrdinalPathEqual $PythonExe $PythonExeFull)) 'NONCANONICAL_PATH'
    Require (Test-Path -LiteralPath $RepositoryRootFull -PathType Container) 'REPOSITORY_ROOT'
    Require (Test-Path -LiteralPath $InputPinsFull -PathType Leaf) 'INPUT_PINS_PATH'
    Require (Test-Path -LiteralPath $JavaHomeFull -PathType Container) 'JAVA_HOME'
    Require (Test-Path -LiteralPath $PythonExeFull -PathType Leaf) 'PYTHON_PATH'
    $RepositoryRoot = $RepositoryRootFull
    $InputPinsPath = $InputPinsFull
    $JavaHome = $JavaHomeFull
    $PythonExe = $PythonExeFull
    Require ([string]::IsNullOrWhiteSpace($InputPinsSha256) -eq $false -and
        $InputPinsSha256 -cmatch '^[0-9a-f]{64}$') 'INPUT_PINS_HASH'
    foreach ($path in @($RepositoryRoot, $InputPinsPath, $JavaHome, $PythonExe, $PSCommandPath)) {
        Assert-LocalNoReparseAncestors $path
    }
    Require ((Get-Hash $InputPinsPath) -ceq $InputPinsSha256) 'INPUT_PINS_HASH'

    $outputCanonical = [IO.Path]::GetFullPath($OutputRoot)
    Require (Test-OrdinalPathEqual $OutputRoot $outputCanonical) 'OUTPUT_PATH'
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    Require ($outputCanonical.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) 'EXTERNAL_OUTPUT_REQUIRED'
    Require (-not (Test-PathUnder $outputCanonical $RepositoryRoot)) 'EXTERNAL_OUTPUT_REQUIRED'
    Require (-not (Test-PathUnder $outputCanonical $PSScriptRoot)) 'EXTERNAL_OUTPUT_REQUIRED'
    $outputParent = [IO.Path]::GetDirectoryName($outputCanonical)
    Require (Test-Path -LiteralPath $outputParent -PathType Container) 'OUTPUT_PARENT_MISSING'
    Assert-LocalNoReparseAncestors $outputParent
    Require (-not (Test-Path -LiteralPath $outputCanonical)) 'OUTPUT_EXISTS'

    $modulePath = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'scripts/VerifierIsolation.psm1'))
    Require (Test-Path -LiteralPath $modulePath -PathType Leaf) 'ISOLATION_MODULE_PATH'
    Assert-LocalNoReparseAncestors $modulePath
    Require ((Get-Hash $modulePath) -ceq $moduleSha256) 'ISOLATION_PIN'
    Import-Module $modulePath -Force -DisableNameChecking
    foreach ($path in @($RepositoryRoot, $InputPinsPath, $JavaHome, $PythonExe, $PSCommandPath, $modulePath)) {
        Assert-VerifierNoReparseAncestors $path
    }
    Assert-VerifierNoReparseAncestors $outputParent

    $phase = 'INPUT_PINS'
    try { $pins = Get-Content -LiteralPath $InputPinsPath -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop }
    catch { throw 'INPUT_SCHEMA' }
    Validate-InputManifest $pins
    $hostRows = @($pins.files | Where-Object { Test-OrdinalPathEqual ([string]$_.path) ([IO.Path]::GetFullPath($PSCommandPath)) })
    Require ($hostRows.Count -eq 1 -and [long]$hostRows[0].bytes -eq (Get-Item -LiteralPath $PSCommandPath).Length -and
        [string]$hostRows[0].sha256 -ceq (Get-Hash $PSCommandPath)) 'SELF_PIN'
    Verify-AllManifestExtraPins
    Require ((Get-Hash $PythonExe) -ceq $selectedPythonSha256) 'PYTHON_PIN'
    Require (Test-OrdinalPathEqual $PythonExe ([IO.Path]::GetFullPath([string]$pins.pythonExe))) 'TOOL_CONTEXT'
    Require (Test-OrdinalPathEqual $JavaHome ([IO.Path]::GetFullPath([string]$pins.javaHome))) 'TOOL_CONTEXT'
    Require ((Get-Hash (Join-Path $JavaHome 'bin/java.exe')) -ceq $selectedJavaSha256) 'JAVA_PIN'
    Require ((Get-Hash (Join-Path $JavaHome 'bin/javac.exe')) -ceq $selectedJavacSha256) 'JAVAC_PIN'
    $java = Require-CanonicalExistingFile (Join-Path $JavaHome 'bin/java.exe') 'JAVA_PATH'
    $javac = Require-CanonicalExistingFile (Join-Path $JavaHome 'bin/javac.exe') 'JAVAC_PATH'

    $phase = 'P6_PARENT_BINDING'
    $p6PinsPath = Require-CanonicalExistingFile ([string]$pins.p6PinsPath) 'P6_PINS_PATH'
    Require ((Get-Hash $p6PinsPath) -ceq [string]$pins.p6PinsSha256 -and
        [string]$pins.p6PinsSha256 -ceq $selectedP6PinsSha256) 'P6_PINS_HASH'
    try { $p6Pins = Get-Content -LiteralPath $p6PinsPath -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop }
    catch { throw 'P6_PINS_SCHEMA' }
    Require ([string]$p6Pins.schema -ceq 'tsj-canonical-prefix-stage-inputs-v1') 'P6_PINS_SCHEMA'
    Require ([string]$p6Pins.parent -ceq $selectedP6Parent) 'P6_CONTEXT'
    $p6RepositoryRoot = Require-CanonicalExistingDirectory ([string]$p6Pins.repositoryRoot) 'P6_CONTEXT'
    Require (-not (Test-PathUnder $p6RepositoryRoot $RepositoryRoot) -and
        -not (Test-PathUnder $RepositoryRoot $p6RepositoryRoot)) 'P6_CONTEXT'
    Require ($p6Pins.sourceRows -is [array] -and
        $p6Pins.sourceRows.Count -eq $sourceRowsRequired) 'SOURCE_CENSUS'
    Require ($p6Pins.files -is [array]) 'P6_PINS_SCHEMA'
    Require ([string]$pins.bridgeManifest -ceq [string]$p6Pins.bridgeManifest -and
        [string]$pins.bridgeRoot -ceq [string]$p6Pins.bridgeRoot) 'BRIDGE_CONTEXT'
    $protectedInputRoots = @(
        [IO.Path]::GetDirectoryName($p6PinsPath),
        $p6RepositoryRoot,
        [IO.Path]::GetFullPath([string]$pins.bridgeRoot)
    )
    if ($p6Pins.PSObject.Properties['historicalRoot'] -and [string]$p6Pins.historicalRoot) {
        $protectedInputRoots += @([IO.Path]::GetFullPath([string]$p6Pins.historicalRoot))
    }
    foreach ($inputRow in $pins.files) {
        $inputDirectory = [IO.Path]::GetDirectoryName([string]$inputRow.path)
        if ($inputDirectory -and (Test-OrdinalPathEqual $inputDirectory $PSScriptRoot)) { continue }
        if ($inputDirectory -and (Test-PathUnder $inputDirectory $PSScriptRoot)) { continue }
        if ($inputDirectory) { $protectedInputRoots += @([IO.Path]::GetFullPath($inputDirectory)) }
    }
    foreach ($protectedRoot in @($protectedInputRoots | Select-Object -Unique)) {
        Require (-not (Test-PathUnder $outputCanonical ([string]$protectedRoot))) 'EXTERNAL_OUTPUT_REQUIRED'
    }
    foreach ($role in @('router', 'support', 'adapter', 'blocker', 'queries', 'stageInspector')) {
        Require (Test-OrdinalPathEqual ([string]$pins.modeFiles.$role) ([string]$p6Pins.modeFiles.$role)) 'ROLE_MAPPING'
    }
    $writerRelativePath = 'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java'
    $maintainedWriterPath = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot $writerRelativePath))
    Require (Test-OrdinalPathEqual ([string]$pins.modeFiles.writer) $maintainedWriterPath) 'ROLE_MAPPING'
    $oldWriterPath = Require-CanonicalExistingFile ([string]$p6Pins.modeFiles.writer) 'P6_WRITER_PATH'
    Require (Test-PathUnder $oldWriterPath $p6RepositoryRoot) 'P6_WRITER_PATH'
    $oldWriterRows = @($p6Pins.files | Where-Object {
        [string]::Equals([string]$_.path, $oldWriterPath, [StringComparison]::OrdinalIgnoreCase)
    })
    Require ($oldWriterRows.Count -eq 1) 'P6_WRITER_PIN'
    Require-ExactInteger $oldWriterRows[0].bytes 0 ([long]::MaxValue) 'P6_WRITER_PIN'
    Require ([string]$oldWriterRows[0].sha256 -cmatch '^[0-9a-f]{64}$') 'P6_WRITER_PIN'
    $oldWriterInfo = Get-Item -LiteralPath $oldWriterPath
    Require ($oldWriterInfo.Length -eq [long]$oldWriterRows[0].bytes -and
        (Get-Hash $oldWriterPath) -ceq [string]$oldWriterRows[0].sha256) 'P6_WRITER_HASH'
    $oldWriterSourceRows = @($p6Pins.sourceRows | Where-Object {
        [string]::Equals([string]$_.path, $writerRelativePath, [StringComparison]::Ordinal)
    })
    Require ($oldWriterSourceRows.Count -eq 1 -and
        [string]$oldWriterSourceRows[0].role -ceq 'WRITER' -and
        [long]$oldWriterSourceRows[0].bytes -eq [long]$oldWriterRows[0].bytes -and
        [string]$oldWriterSourceRows[0].sha256 -ceq [string]$oldWriterRows[0].sha256) 'P6_WRITER_SOURCE'
    $maintainedWriter = Require-CanonicalExistingFile $maintainedWriterPath 'WRITER_SOURCE'
    Require (-not (Test-OrdinalPathEqual $oldWriterPath $maintainedWriter)) 'P6_CONTEXT'
    $maintainedWriterRows = @($pins.sourceRows | Where-Object {
        [string]::Equals([string]$_.path, $writerRelativePath, [StringComparison]::Ordinal)
    })
    Require ($maintainedWriterRows.Count -eq 1 -and
        [string]$maintainedWriterRows[0].role -ceq 'WRITER') 'WRITER_SOURCE'
    Require-ExactInteger $maintainedWriterRows[0].bytes 0 ([long]::MaxValue) 'WRITER_SOURCE'
    Require ([string]$maintainedWriterRows[0].sha256 -ceq [string]$oldWriterRows[0].sha256 -and
        [long]$maintainedWriterRows[0].bytes -eq [long]$oldWriterRows[0].bytes) 'WRITER_SOURCE'
    $maintainedWriterInfo = Get-Item -LiteralPath $maintainedWriter
    Require ($maintainedWriterInfo.Length -eq [long]$oldWriterRows[0].bytes -and
        (Get-Hash $maintainedWriter) -ceq [string]$oldWriterRows[0].sha256) 'WRITER_SOURCE'

    $gwtUser = Require-PinnedSourceFile '.tools/gwt-2.7.0/gwt-user-2.7.0.jar' 'SOURCE_HASH'
    $gwtDev = Require-PinnedSourceFile '.tools/gwt-2.7.0/gwt-dev-2.7.0.jar' 'SOURCE_HASH'
    foreach ($role in $modeRoles) {
        $rolePath = Require-CanonicalExistingFile ([string]$pins.modeFiles.$role) 'MODE_ROLE'
        if ($role -cne 'writer') { Require-PinnedFile $rolePath 'EXTRA_INPUT_HASH' }
    }
    $bridgeManifestPath = Require-CanonicalExistingFile ([string]$pins.bridgeManifest) 'BRIDGE_CONTEXT'
    $bridgeRoot = Require-CanonicalExistingDirectory ([string]$pins.bridgeRoot) 'BRIDGE_CONTEXT'
    Require-PinnedFile $bridgeManifestPath 'BRIDGE_CONTEXT'

    $phase = 'SOURCE_PREFLIGHT'
    $preflight = Invoke-Auditor 'preflight'
    $sourceChecks.preflight = $preflight.summary
    Require ($preflight.process.TerminationProven -eq $true) 'PREFLIGHT_TERMINATION'

    # Strict auditing and every pin check precede creation or mutation of OutputRoot.
    Require (-not (Test-Path -LiteralPath $outputCanonical)) 'OUTPUT_EXISTS'
    [void][IO.Directory]::CreateDirectory($outputCanonical)
    $outputCreated = $true
    Assert-VerifierNoReparseAncestors $outputCanonical
    $outputItem = Get-Item -LiteralPath $outputCanonical
    $outputCreationTicks = [string]$outputItem.CreationTimeUtc.Ticks
    $hostPath = [IO.Path]::GetFullPath($PSCommandPath)
    $hostSha = Get-Hash $hostPath
    $hostStartTicks = [string]$owner.StartTime.ToUniversalTime().Ticks
    Write-NewJson (Join-Path $outputCanonical 'host-launch.json') ([ordered]@{
        schema = 'tsj-q100-geometric-canary-host-launch-v1'
        status = 'LAUNCHED_NOT_ACCEPTED'
        mode = 'CANARY'
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
    $preflightEvidence = Save-ProcessEvidence 'source-preflight' $preflight.process
    $processRecords.preflight = [ordered]@{ receipt = $preflightEvidence.receipt; stdout = $preflightEvidence.stdout; stderr = $preflightEvidence.stderr; wallMs = $preflight.wallMs }

    $phase = 'SCRATCH_CREATE'
    $scratch = Join-Path ([IO.Path]::GetTempPath()) ('tsj-q100-geometric-canary-r1-' + [Guid]::NewGuid().ToString('N'))
    $scratch = [IO.Path]::GetFullPath($scratch)
    Assert-VerifierNoReparseAncestors ([IO.Path]::GetDirectoryName($scratch))
    Require (-not (Test-Path -LiteralPath $scratch)) 'SCRATCH_EXISTS'
    Write-NewJson (Join-Path $outputCanonical 'scratch-intent.json') ([ordered]@{
        status = 'INTENT_NOT_CREATION_PROOF'
        scratch = $scratch
        hostPid = [int]$PID
        hostStartTicks = $hostStartTicks
        executable = [string]$owner.Path
        runId = [Guid]::NewGuid().ToString('D')
    })
    [void][IO.Directory]::CreateDirectory($scratch)
    $scratchCreated = $true
    $scratchTicks = [long](Get-Item -LiteralPath $scratch).CreationTimeUtc.Ticks
    Assert-VerifierNoReparseAncestors $scratch

    $classes = Join-Path $scratch 'classes'
    $emptySourcePath = Join-Path $scratch 'empty-sourcepath'
    [void][IO.Directory]::CreateDirectory($classes)
    [void][IO.Directory]::CreateDirectory($emptySourcePath)
    $p6Rows = @($p6Pins.sourceRows)
    Require ($p6Rows.Count -eq $sourceRowsRequired) 'SOURCE_CENSUS'
    $paths = New-Object 'System.Collections.Generic.List[string]'
    foreach ($row in $p6Rows) {
        if ([string]$row.role -ceq 'BASE_SOURCE' -and [string]$row.path -match '\.java$') {
            $relative = ([string]$row.path).Replace('\', '/')
            if ($relative -cnotin $excludedBaseSources) {
                $sourcePath = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot ([string]$row.path)))
                Require-CanonicalExistingFile $sourcePath 'BASE_SOURCE_PATH' | Out-Null
                [void]$paths.Add($sourcePath)
            }
        }
    }
    foreach ($role in $modeRoles) { [void]$paths.Add([string]$pins.modeFiles.$role) }
    Require ([IO.File]::Exists($bridgeManifestPath)) 'BRIDGE_CONTEXT'
    try { $bridgeRows = Get-Content -LiteralPath $bridgeManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop }
    catch { throw 'BRIDGE_MANIFEST_SCHEMA' }
    Require ($bridgeRows -is [array] -and $bridgeRows.Count -eq 3) 'BRIDGE_CENSUS'
    $seenBridges = @{}
    foreach ($bridge in $bridgeRows) {
        Require ([string]$bridge.path -cin $bridgeNames) 'BRIDGE_NAME'
        Require (-not $seenBridges.ContainsKey([string]$bridge.path)) 'BRIDGE_CENSUS'
        $seenBridges.Add([string]$bridge.path, $true)
        Require-ExactInteger $bridge.bytes 0 ([long]::MaxValue) 'BRIDGE_MANIFEST_SCHEMA'
        Require ([string]$bridge.sha256 -cmatch '^[0-9a-f]{64}$') 'BRIDGE_MANIFEST_SCHEMA'
        $from = Require-CanonicalExistingFile (Join-Path $bridgeRoot ('bridge-' + [string]$bridge.path)) 'BRIDGE_SOURCE_PATH'
        $sourceInfo = Get-Item -LiteralPath $from
        Require ($sourceInfo.Length -eq [long]$bridge.bytes -and (Get-Hash $from) -ceq [string]$bridge.sha256) 'BRIDGE_SOURCE_HASH'
        $to = Join-Path $scratch ([string]$bridge.path)
        [IO.File]::Copy($from, $to, $false)
        $copyInfo = Get-Item -LiteralPath $to
        Require ($copyInfo.Length -eq [long]$bridge.bytes -and (Get-Hash $to) -ceq [string]$bridge.sha256) 'COPIED_BRIDGE_HASH'
        $bridgeCopies += @([ordered]@{ path = $to; bytes = [long]$bridge.bytes; sha256 = [string]$bridge.sha256 })
        [void]$paths.Add([IO.Path]::GetFullPath($to))
    }
    foreach ($name in $bridgeNames) { Require ($seenBridges.ContainsKey($name)) 'BRIDGE_CENSUS' }
    Require ($paths.Count -eq $canaryCompilerSourcesRequired) 'COMPILE_CENSUS'
    $seenSources = @{}
    foreach ($path in $paths) {
        Require ([IO.Path]::IsPathRooted($path) -and
            (Test-OrdinalPathEqual $path ([IO.Path]::GetFullPath($path)))) 'COMPILER_PATH'
        Require (-not $seenSources.ContainsKey($path)) 'COMPILER_DUPLICATE'
        $seenSources.Add($path, $true)
    }
    $argsFile = Join-Path $scratch 'sources.txt'
    $sourceLines = @($paths | ForEach-Object { '"' + ([string]$_).Replace('\', '/') + '"' })
    [IO.File]::WriteAllLines($argsFile, [string[]]$sourceLines, $utf8)
    $compilerListCopy = Join-Path $outputCanonical 'compiler-sources.txt'
    $compilerListBytes = [long](Get-Item -LiteralPath $argsFile).Length
    Require ($writtenOutputBytes + $compilerListBytes -le 8388608) 'AGGREGATE_OUTPUT_LIMIT'
    [IO.File]::Copy($argsFile, $compilerListCopy, $false)
    $writtenOutputBytes += $compilerListBytes
    Require ((Get-Hash $argsFile) -ceq (Get-Hash $compilerListCopy)) 'COMPILER_LIST_COPY'
    Write-NewJson (Join-Path $outputCanonical 'bridge-copies.json') $bridgeCopies
    $classpath = [string]::Join(';', @($classes, $gwtUser, $gwtDev))
    $compilerArgs = @('-source', '7', '-target', '7', '-encoding', 'UTF-8', '-classpath', $classpath,
        '-sourcepath', $emptySourcePath, '-d', $classes, ('@' + $argsFile))
    Write-NewJson (Join-Path $outputCanonical 'compiler-launch-intent.json') ([ordered]@{
        status = 'INTENT_NOT_START_PROOF'
        executable = $javac
        sha256 = $selectedJavacSha256
        arguments = $compilerArgs
        sourceListPath = $argsFile
        sourceListSha256 = Get-Hash $argsFile
        compilerSourceCount = $paths.Count
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
    $compilerEvidence = Save-ProcessEvidence 'compiler' $compilerProcess
    $processRecords.compiler = [ordered]@{ receipt = $compilerEvidence.receipt; stdout = $compilerEvidence.stdout; stderr = $compilerEvidence.stderr; wallMs = $compileWall }
    Require ($compilerProcess.ExitCode -eq 0 -and $compilerProcess.TerminationProven -eq $true) 'COMPILE_FAILED'
    $returnedChildEvidence = $null
    Require ((Get-Hash $argsFile) -ceq (Get-Hash (Join-Path $outputCanonical 'compiler-sources.txt'))) 'COMPILER_LIST_CHANGED'
    Verify-BridgeCopies

    $phase = 'SOURCE_PRE_NATIVE'
    $preNative = Invoke-Auditor 'prenative'
    $sourceChecks.preNative = $preNative.summary
    Verify-BridgeCopies

    $classesCp = $classpath
    $p6Main = 'com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy'
    $p8Main = 'com.lushprojects.circuitjs1.client.CanonicalGeometricReasonCanary'
    $p6Args = @('-ea', '-cp', $classesCp, $p6Main, '--canary')
    Write-NewJson (Join-Path $outputCanonical 'p6-canary-launch-intent.json') ([ordered]@{
        status = 'INTENT_NOT_START_PROOF'
        executable = $java
        sha256 = $selectedJavaSha256
        arguments = $p6Args
        limitMs = 480000
    })

    $phase = 'P6_CANARY'
    $nativeInvoked = $true
    $nativePhaseStartOffsetMs = $hostClock.Elapsed.TotalMilliseconds
    $p6Call = Invoke-BoundedChild -FilePath $java -Arguments $p6Args -TimeoutMilliseconds 480000 -CallPhase 'P6_CANARY'
    $p6Process = $p6Call.process
    Require-InvocationReceipt $p6Process $java $p6Args 'P6_CANARY_PROCESS_IDENTITY'
    Test-VerifierLogsRemoved $p6Process
    $p6CanaryWallMs = [double]$p6Call.wallMs
    Require ($p6CanaryWallMs -ge 0 -and $p6CanaryWallMs -le 480000) 'P6_CANARY_WALL_LIMIT'
    $p6Evidence = Save-ProcessEvidence 'p6-canary' $p6Process
    $processRecords.p6Canary = [ordered]@{ receipt = $p6Evidence.receipt; stdout = $p6Evidence.stdout; stderr = $p6Evidence.stderr; wallMs = $p6CanaryWallMs }
    Require ($p6Process.ExitCode -eq 0 -and $p6Process.TerminationProven -eq $true -and
        [string]::IsNullOrEmpty([string]$p6Process.Stderr)) 'P6_CANARY_PROCESS'
    $p6Summary = Parse-P6Canary ([string]$p6Process.Stdout)
    $returnedChildEvidence = $null
    $nativeCanaries.p6 = [ordered]@{
        schema = [string]$p6Summary.schema
        status = [string]$p6Summary.status
        assertions = [long]$p6Summary.assertions
        blockerAssertions = [long]$p6Summary.blockerAssertions
        stageAssertions = [long]$p6Summary.stageAssertions
        elapsedNanos = [long]$p6Summary.elapsedNanos
        groups = [long]$p6Summary.groups
        maximumGroupNanos = [long]$p6Summary.maximumGroupNanos
        layoutQualification = $false
    }
    Verify-BridgeCopies

    $p8Args = @('-ea', '-cp', $classesCp, $p8Main)
    Write-NewJson (Join-Path $outputCanonical 'p8-canary-launch-intent.json') ([ordered]@{
        status = 'INTENT_NOT_START_PROOF'
        executable = $java
        sha256 = $selectedJavaSha256
        arguments = $p8Args
        limitMs = 480000
    })
    $phase = 'P8_CANARY'
    $p8Call = Invoke-BoundedChild -FilePath $java -Arguments $p8Args -TimeoutMilliseconds 480000 -CallPhase 'P8_CANARY'
    $p8Process = $p8Call.process
    Require-InvocationReceipt $p8Process $java $p8Args 'P8_CANARY_PROCESS_IDENTITY'
    Test-VerifierLogsRemoved $p8Process
    $p8CanaryWallMs = [double]$p8Call.wallMs
    Require ($p8CanaryWallMs -ge 0 -and $p8CanaryWallMs -le 480000) 'P8_CANARY_WALL_LIMIT'
    $p8Evidence = Save-ProcessEvidence 'p8-canary' $p8Process
    $processRecords.p8Canary = [ordered]@{ receipt = $p8Evidence.receipt; stdout = $p8Evidence.stdout; stderr = $p8Evidence.stderr; wallMs = $p8CanaryWallMs }
    Require ($p8Process.ExitCode -eq 0 -and $p8Process.TerminationProven -eq $true -and
        [string]::IsNullOrEmpty([string]$p8Process.Stderr)) 'P8_CANARY_PROCESS'
    $p8Summary = Parse-P8Canary ([string]$p8Process.Stdout)
    $returnedChildEvidence = $null
    $nativeCanaries.p8 = [ordered]@{
        schema = [string]$p8Summary.schema
        status = [string]$p8Summary.status
        assertions = [long]$p8Summary.assertions
        caseCount = [long]$p8Summary.caseCount
        workNanos = [long]$p8Summary.workNanos
        groups = [long]$p8Summary.groups
        maximumGroupNanos = [long]$p8Summary.maximumGroupNanos
        totalObservationUnits = [long]$p8Summary.totalObservationUnits
        layoutQualification = $false
    }
    Verify-BridgeCopies

    $phase = 'POST_SOURCE'
    $cleanupStartOffsetMs = $hostClock.Elapsed.TotalMilliseconds
    $postSource = Invoke-Auditor 'post'
    $sourceChecks.postSource = $postSource.summary
    Require ((Get-Hash $argsFile) -ceq (Get-Hash (Join-Path $outputCanonical 'compiler-sources.txt'))) 'COMPILER_LIST_CHANGED'
    Verify-BridgeCopies
    $nativePhaseChargeMs = $cleanupStartOffsetMs - $nativePhaseStartOffsetMs
    $nativePhaseOverheadMs = $nativePhaseChargeMs - ($p6CanaryWallMs + $p8CanaryWallMs)
    Require ($nativePhaseStartOffsetMs -ge 0 -and $cleanupStartOffsetMs -ge $nativePhaseStartOffsetMs -and
        $nativePhaseChargeMs -ge 0 -and $nativePhaseOverheadMs -ge 0) 'NATIVE_PHASE_ARITHMETIC'
    Require ((Get-Item -LiteralPath $scratch).CreationTimeUtc.Ticks -eq $scratchTicks) 'SCRATCH_IDENTITY_CHANGED'
    Remove-OwnedScratch
    Require ($scratchRemoved) 'SCRATCH_CLEANUP_UNPROVEN'
    Require ($hostClock.Elapsed.TotalMilliseconds -le 1800000) 'HOST_OUTER_LIMIT'

    $phase = 'OUTPUT_CLOSURE'
    $closure = Get-OutputClosure
    $compilerListPath = Join-Path $outputCanonical 'compiler-sources.txt'
    $compilerInfo = Get-Item -LiteralPath $compilerListPath
    $hostResult = [ordered]@{
        schema = 'tsj-q100-geometric-canary-host-v1'
        status = 'HOST_CANARY_COMPLETE_READER_PENDING'
        mode = 'CANARY'
        inputPinsSha256 = $InputPinsSha256
        host = [ordered]@{
            pid = [int]$PID
            startTicks = $hostStartTicks
            executable = [string]$owner.Path
            executableSha256 = $selectedPowerShellSha256
            script = $hostPath
            scriptSha256 = $hostSha
        }
        processes = $processRecords
        sourceChecks = $sourceChecks
        compiler = [ordered]@{
            sourceCount = [long]$paths.Count
            sourceList = 'compiler-sources.txt'
            sourceListBytes = [long]$compilerInfo.Length
            sourceListSha256 = Get-Hash $compilerListPath
        }
        bridgeCopies = [ordered]@{
            receipt = 'bridge-copies.json'
            count = [long]$bridgeCopies.Count
            verifiedAfterCompile = $true
            verifiedBeforeNative = $true
            verifiedAfterNative = $true
        }
        nativeCanaries = $nativeCanaries
        timing = [ordered]@{
            nativePhaseStartOffsetMs = [double]$nativePhaseStartOffsetMs
            cleanupStartOffsetMs = [double]$cleanupStartOffsetMs
            nativePhaseChargeMs = [double]$nativePhaseChargeMs
            p6CanaryWallMs = [double]$p6CanaryWallMs
            p8CanaryWallMs = [double]$p8CanaryWallMs
            nativePhaseOverheadMs = [double]$nativePhaseOverheadMs
        }
        cleanup = [ordered]@{
            scratchPath = $scratch
            scratchCreationTicks = [string]$scratchTicks
            scratchRemoved = [bool]$scratchRemoved
            boundMs = 120000
            formula = 'nativePhaseChargeMs+(parentFullHostElapsedMs-cleanupStartOffsetMs)'
        }
        outputClosure = $closure
        limits = [ordered]@{
            compileMs = 60000
            nativeMs = 480000
            auditMs = 60000
            consumerMs = 180000
            cleanupMs = 120000
            outerMs = 1800000
            workNanos = 90000000000
            groups = 640
            groupNanos = 5000000000
            observationUnits = 32768
            aggregateBytes = 8388608
            lineBytes = 65536
        }
    }
    $hostResultPath = Join-Path $outputCanonical 'host-result.json'
    Write-NewJson $hostResultPath $hostResult
    Verify-OutputAfterResult $closure
    Write-Output 'P8_GEOMETRIC_REASON_CANARY_HOST_COMPLETE'
}
catch {
    $firstFailure = $_
    $failureMessage = [string]$firstFailure.Exception.Message
    $failureCode = $failureMessage
    if ($null -eq $failedProcessReturn -and $null -ne $returnedChildEvidence) {
        $failedProcessReturn = $returnedChildEvidence
    }
    $terminationUnproven = ($null -ne $failedProcessCall)
    if ($null -ne $failedProcessReturn) {
        $returnedProcess = $failedProcessReturn.process
        $terminationProperty = if ($null -eq $returnedProcess) { $null } else { $returnedProcess.PSObject.Properties['TerminationProven'] }
        if ($null -eq $terminationProperty -or $terminationProperty.Value -isnot [bool] -or
            $terminationProperty.Value -ne $true) {
            $terminationUnproven = $true
        }
    }
    if ($scratchCreated -and -not $scratchRemoved) {
        if (-not (Test-Path -LiteralPath $scratch)) {
            $cleanupFailure = 'SCRATCH_CLEANUP_UNPROVEN'
        }
        elseif ($terminationUnproven) {
            $cleanupFailure = 'PROCESS_TERMINATION_UNPROVEN'
        }
        else {
            try { Remove-OwnedScratch }
            catch { $cleanupFailure = [string]$_.Exception.Message }
        }
    }
    if ($outputCreated) {
        try {
            $failureRecord = [ordered]@{
                schema = 'tsj-q100-geometric-canary-host-failure-v1'
                status = 'FAIL'
                phase = $phase
                errorCode = $failureCode
                message = $failureMessage
                mode = 'CANARY'
                inputPinsSha256 = $InputPinsSha256
                cleanupError = $cleanupFailure
                scratchPath = $scratch
                scratchCreationTicks = if ($null -eq $scratchTicks) { $null } else { [string]$scratchTicks }
                scratchRemoved = [bool]$scratchRemoved
                failedProcessCall = $failedProcessCall
                failedProcessReturn = $failedProcessReturn
                hostElapsedMs = [double]$hostClock.Elapsed.TotalMilliseconds
            }
            $failurePath = Join-Path $outputCanonical 'host-failure.json'
            if (-not (Test-Path -LiteralPath $failurePath)) { Write-NewJson $failurePath $failureRecord }
        }
        catch {
            $failureCode = 'HOST_FAILURE_EVIDENCE_UNAVAILABLE'
        }
    }
    elseif ($null -ne $failedProcessCall) {
        try { Write-ChildFailureDiagnostic 'P8_CANARY_HOST_CALL_FAILURE' $failedProcessCall 'HOST_CALL_FAILURE_EVIDENCE_LIMIT' }
        catch { $failureCode = 'HOST_CALL_FAILURE_EVIDENCE_UNAVAILABLE' }
    }
    elseif ($null -ne $failedProcessReturn) {
        try { Write-ChildFailureDiagnostic 'P8_CANARY_HOST_PROCESS_FAILURE' $failedProcessReturn 'HOST_PROCESS_FAILURE_EVIDENCE_LIMIT' }
        catch { $failureCode = 'HOST_PROCESS_FAILURE_EVIDENCE_UNAVAILABLE' }
    }
    try {
        $reject = [ordered]@{
            code = [string]$failureCode
            phase = [string]$phase
            outputCreated = [bool]$outputCreated
            scratchCreated = [bool]$scratchCreated
            compilerInvoked = [bool]$compilerInvoked
            nativeInvoked = [bool]$nativeInvoked
            cleanupError = if ($null -eq $cleanupFailure) { $null } else { [string]$cleanupFailure }
        }
        $rejectJson = ConvertTo-Json -InputObject $reject -Depth 8 -Compress
        [Console]::Error.Write('P8_CANARY_HOST_REJECT ' + $rejectJson + "`n")
        [Console]::Error.Flush()
    }
    catch {
        $cleanupJson = if ($null -eq $cleanupFailure) { 'null' } else { ConvertTo-Json -InputObject ([string]$cleanupFailure) -Compress }
        $fallback = '{"code":"HOST_REJECTION_FRAME_FAILURE","phase":"REJECTION_FRAME","outputCreated":' +
            $outputCreated.ToString().ToLowerInvariant() + ',"scratchCreated":' +
            $scratchCreated.ToString().ToLowerInvariant() + ',"compilerInvoked":' +
            $compilerInvoked.ToString().ToLowerInvariant() + ',"nativeInvoked":' +
            $nativeInvoked.ToString().ToLowerInvariant() + ',"cleanupError":' + $cleanupJson + '}'
        [Console]::Error.Write('P8_CANARY_HOST_REJECT ' + $fallback + "`n")
        [Console]::Error.Flush()
    }
    exit 1
}

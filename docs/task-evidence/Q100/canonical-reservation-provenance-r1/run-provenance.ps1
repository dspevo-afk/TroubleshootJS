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
$strictUtf8 = [Text.UTF8Encoding]::new($false, $true)
$selectedPowerShellSha256 = '60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f'
$selectedPythonSha256 = 'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec'
$selectedJavaSha256 = 'e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce'
$selectedJavacSha256 = '61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64'
$selectedModuleSha256 = 'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c'
$sourceParent = '4623f1e5680503527a4423c2ca31fb09ede3cbb8'
$p8SourceParent = '0891474cadc4b9e00d7911e81c124721a11efb87'
$p8PinsSha256 = 'b3bca1391fcb8e229e6f34dddddfc52ae77447a02a064d9e7eb8a30e7bc8ba7a'
$selectedP6PinsSha256 = 'b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771'
$selectedCanaryPinsSha256 = '11b3fa459e9bf6950c7ee3d3e804a3b37638bb23cc6c322aaa8ef94549462a5a'
$selectedCanaryQualificationSha256 = '755f921b068f325c9e81315b88f38c4bc8b7196dd61d65f8c8ecf61e26e43276'
$modeRoles = @('router', 'support', 'writer', 'adapter', 'blocker', 'queries', 'stageInspector', 'netRouterOverlay', 'rulesOverlay', 'reasonCanary')
$bridgeNames = @('CirSim.java', 'ChipElm.java', 'PhysicalSpecificationDeveloperVerifier.java')
$excludedBaseSources = @(
    'src/com/lushprojects/circuitjs1/client/CirSim.java',
    'src/com/lushprojects/circuitjs1/client/ChipElm.java',
    'src/com/lushprojects/circuitjs1/client/PhysicalSpecificationDeveloperVerifier.java',
    'src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java',
    'src/com/lushprojects/circuitjs1/client/PcbNetRouter.java',
    'src/com/lushprojects/circuitjs1/client/PcbTwoLayerRules.java'
)
$modeOrder = @('P6_CANARY', 'P8_CANARY', 'P9_CANARY', 'P9_BAD_MODE', 'P9_BAD_UUID', 'P9_BAD_INPUT_SHA', 'P9_NONEMPTY_DATA', 'P9_NONEMPTY_P9')
if ($Mode -ceq 'SUITE') { $modeOrder += @('NULL', 'OBSERVE', 'TAP_THROW', 'CALLBACK_THROW', 'REASON_THROW') }

$baseLeaves = @(
    'host-launch-intent.json', 'scratch-intent.json', 'compiler-launch-intent.json', 'compiler-sources.txt',
    'compiler-process.json', 'compiler.stdout', 'compiler.stderr', 'bridge-copies.json',
    'host-result.json', 'suite-index.json', 'suite-footer.json',
    'source-preflight-process.json', 'source-preflight.stdout', 'source-preflight.stderr',
    'source-prenative-process.json', 'source-prenative.stdout', 'source-prenative.stderr',
    'source-post-process.json', 'source-post.stdout', 'source-post.stderr'
)
$nativeStems = @('p6-canary', 'p8-canary', 'p9-canary', 'p9-bad-mode', 'p9-bad-uuid', 'p9-bad-input-sha', 'p9-nonempty-data', 'p9-nonempty-p9')
foreach ($stem in $nativeStems) { $baseLeaves += @("$stem-launch-intent.json", "$stem-process.json", "$stem.stdout", "$stem.stderr") }
$baseLeaves += @('controls/P9_NONEMPTY_DATA/data/sentinel.txt', 'controls/P9_NONEMPTY_P9/p9/sentinel.txt')
$suiteLeaves = @()
if ($Mode -ceq 'SUITE') {
    foreach ($op in @('NULL', 'OBSERVE', 'TAP_THROW', 'CALLBACK_THROW', 'REASON_THROW')) {
        $suiteLeaves += @("runs/$op/launch-intent.json", "runs/$op/process.json", "runs/$op.stdout", "runs/$op.stderr")
    }
    foreach ($op in @('NULL', 'OBSERVE', 'TAP_THROW')) {
        foreach ($leaf in @('op-1.jsonl','fixed-prefix.jsonl','prefix-blockers.json','prefix-stages.json','canonical-stage-index.json','prefix-geometric-reasons.jsonl','canonical-geometric-prefix-index.json')) { $suiteLeaves += "runs/$op/data/$leaf" }
        $suiteLeaves += @("runs/$op/p9/reservation-operands.jsonl", "runs/$op/p9/reservation-index.json")
    }
    $suiteLeaves += 'runs/CALLBACK_THROW/data/op-1.jsonl'
    $suiteLeaves += @('runs/REASON_THROW/data/op-1.jsonl','runs/REASON_THROW/data/prefix-blockers.json','runs/REASON_THROW/data/prefix-stages.json','runs/REASON_THROW/data/prefix-geometric-reasons.jsonl')
}
$outputLeaves = @($baseLeaves + $suiteLeaves)
$successLeaves = @($outputLeaves)
$extraEmptyDirectories = @(
    'controls/P9_BAD_MODE/data', 'controls/P9_BAD_MODE/p9', 'controls/P9_BAD_UUID/data', 'controls/P9_BAD_UUID/p9',
    'controls/P9_BAD_INPUT_SHA/data', 'controls/P9_BAD_INPUT_SHA/p9', 'controls/P9_NONEMPTY_DATA/p9', 'controls/P9_NONEMPTY_P9/data'
)
if ($Mode -ceq 'SUITE') { $extraEmptyDirectories += @('runs') }
$allowedDirectories = @()
foreach ($leaf in $outputLeaves) {
    $parent = [IO.Path]::GetDirectoryName($leaf.Replace('/', [IO.Path]::DirectorySeparatorChar))
    while (-not [string]::IsNullOrEmpty($parent) -and -not $allowedDirectories.Contains($parent.Replace('\','/'))) {
        $allowedDirectories += $parent.Replace('\','/')
        $parent = [IO.Path]::GetDirectoryName($parent)
    }
}
foreach ($dir in $extraEmptyDirectories) {
    $parent = $dir
    while (-not [string]::IsNullOrEmpty($parent) -and -not $allowedDirectories.Contains($parent)) {
        $allowedDirectories += $parent
        $parent = [IO.Path]::GetDirectoryName($parent)
        if ($null -ne $parent) { $parent = $parent.Replace('\','/') }
    }
}

$phase = 'HOST_PREFLIGHT'
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
$hostPath = $null
$hostSha = $null
$hostStartTicks = $null
$suiteId = $null
$pins = $null
$p8Pins = $null
$p6Pins = $null
$canaryPins = $null
$sourceInventorySha256 = $null
$wireSha256 = $null
$p9Root = $null
$modulePath = $null
$auditorPath = $null
$java = $null
$javac = $null
$gwtUser = $null
$gwtDev = $null
$argsFile = $null
$classes = $null
$classpath = $null
$compilerArgs = $null
$sourcePaths = @()
$bridgeCopies = @()
$processRecords = [ordered]@{}
$sourceChecks = [ordered]@{}
$canaries = [ordered]@{ p6 = $null; p8 = $null; p9 = $null; preflight = @() }
$operations = @()
$nativePhaseStartOffsetMs = $null
$cleanupStartOffsetMs = $null
$childWallSumMs = [double]0
$nativePhaseChildWallSumMs = [double]0
$workCreditsMs = [double]0
$operationWorkCredits = [ordered]@{}
$failedProcessCall = $null
$failedProcessErrorRecord = $null
$activeLaunchIntent = $null
$failedProcessReturn = $null
$returnedChildEvidence = $null
$registeredProcessCaptures = @()
$failureBundleResult = $null
$failureEvidenceWriteError = $null
$hostFailureSummaryWriteError = $null
$cleanupFailure = $null
[long]$writtenOutputBytes = 0
[long]$operationOutputBytes = 0
$operationOutputCounters = @{NULL=[long]0;OBSERVE=[long]0;TAP_THROW=[long]0;CALLBACK_THROW=[long]0;REASON_THROW=[long]0}
[long]$hostOutputBytes = 0

function Require([bool]$Condition, [string]$Code) { if (-not $Condition) { throw $Code } }
function Get-Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Test-PathEqual([string]$A, [string]$B) { return [string]::Equals($A, $B, [StringComparison]::OrdinalIgnoreCase) }
function Test-PathUnder([string]$Child, [string]$Root) {
    $c = [IO.Path]::GetFullPath($Child).TrimEnd('\','/')
    $r = [IO.Path]::GetFullPath($Root).TrimEnd('\','/')
    return (Test-PathEqual $c $r) -or $c.StartsWith(($r + [IO.Path]::DirectorySeparatorChar), [StringComparison]::OrdinalIgnoreCase)
}
function Assert-NoReparse([string]$Path) {
    $current = [IO.Path]::GetFullPath($Path)
    while (-not [string]::IsNullOrEmpty($current)) {
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            Require (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'REPARSE_PATH'
        }
        $parent = [IO.Path]::GetDirectoryName($current)
        if ([string]::IsNullOrWhiteSpace($parent) -or (Test-PathEqual $parent $current)) { break }
        $current = $parent
    }
}
function Require-ExistingFile([string]$Path, [string]$Code) {
    Require ([IO.Path]::IsPathRooted($Path)) $Code
    $full = [IO.Path]::GetFullPath($Path)
    Require (Test-PathEqual $full $Path) $Code
    Require (Test-Path -LiteralPath $full -PathType Leaf) $Code
    Assert-NoReparse $full
    return $full
}
function Require-ExistingDirectory([string]$Path, [string]$Code) {
    Require ([IO.Path]::IsPathRooted($Path)) $Code
    $full = [IO.Path]::GetFullPath($Path)
    Require (Test-PathEqual $full $Path) $Code
    Require (Test-Path -LiteralPath $full -PathType Container) $Code
    Assert-NoReparse $full
    return $full
}
function Require-ExactKeys($Value, [string[]]$Expected, [string]$Code) {
    Require ($null -ne $Value) $Code
    $actual = @($Value.PSObject.Properties | ForEach-Object { [string]$_.Name })
    Require ($actual.Count -eq $Expected.Count) $Code
    foreach ($key in $Expected) { Require (@($actual | Where-Object { [string]::Equals($_,$key,[StringComparison]::Ordinal) }).Count -eq 1) $Code }
}
function Test-ExactInteger($Value) {
    if ($null -eq $Value -or $Value -is [bool]) { return $false }
    return ($Value -is [byte] -or $Value -is [int16] -or $Value -is [int32] -or $Value -is [int64] -or $Value -is [uint16] -or $Value -is [uint32] -or $Value -is [uint64])
}
function Require-Integer($Value, [long]$Min, [long]$Max, [string]$Code) {
    Require (Test-ExactInteger $Value) $Code
    Require ([decimal]$Value -ge [decimal]$Min -and [decimal]$Value -le [decimal]$Max) $Code
}
function Assert-UniqueJsonKeys([string]$Text, [string]$Code) {
    $stack = New-Object 'System.Collections.Generic.List[System.Collections.Generic.HashSet[string]]'
    $inString = $false; $escaped = $false; $start = -1
    for ($i=0; $i -lt $Text.Length; $i++) {
        $ch = $Text[$i]
        if ($inString) {
            if ($escaped) { $escaped = $false; continue }
            if ($ch -ceq '\') { $escaped = $true; continue }
            if ($ch -ceq '"') {
                $inString = $false
                $j = $i + 1
                while ($j -lt $Text.Length -and [char]::IsWhiteSpace($Text[$j])) { $j++ }
                if ($j -lt $Text.Length -and $Text[$j] -ceq ':' -and $stack.Count -gt 0) {
                    $raw = $Text.Substring($start, $i - $start + 1)
                    Require (-not $raw.Contains('\')) $Code
                    Require ($stack[$stack.Count-1].Add($raw)) $Code
                }
            }
            continue
        }
        if ($ch -ceq '"') { $inString = $true; $start = $i; continue }
        if ($ch -ceq '{') { $stack.Add((New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal))); continue }
        if ($ch -ceq '}') { Require ($stack.Count -gt 0) $Code; $stack.RemoveAt($stack.Count-1); continue }
    }
    Require (-not $inString -and $stack.Count -eq 0) $Code
}
function Write-NewBytes([string]$Path, [byte[]]$Bytes, [string]$RelativeName) {
    Require ([long]$script:writtenOutputBytes + [long]$Bytes.Length -le 41943040) 'SUITE_OUTPUT_LIMIT'
    if ($script:Mode -ceq 'SUITE' -and $RelativeName -match '^runs/(NULL|OBSERVE|TAP_THROW|CALLBACK_THROW|REASON_THROW)(/|\.)') {
        $op=$Matches[1]; $next=[decimal]$script:operationOutputCounters[$op]+[decimal]$Bytes.Length
        Require ($next -le 8388608) 'OPERATION_OUTPUT_LIMIT'
        $script:operationOutputCounters[$op]=[long]$next
    } else {
        $next = [decimal]$script:hostOutputBytes + [decimal]$Bytes.Length
        Require ($next -le 8388608) 'HOST_EVIDENCE_LIMIT'
        $script:hostOutputBytes = [long]$next
    }
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { if ($Bytes.Length -gt 0) { $stream.Write($Bytes,0,$Bytes.Length) }; $stream.Flush() } finally { $stream.Dispose() }
    $script:writtenOutputBytes = [long]$script:writtenOutputBytes + [long]$Bytes.Length
}
function Get-OutputPath([string]$Relative) {
    Require ($Relative -cmatch '^[A-Za-z0-9._/-]+$' -and -not $Relative.StartsWith('/') -and -not $Relative.Contains('..')) 'OUTPUT_PATH'
    return [IO.Path]::GetFullPath((Join-Path $script:outputCanonical $Relative.Replace('/',[IO.Path]::DirectorySeparatorChar)))
}
function Write-NewText([string]$Relative, [string]$Text) { Write-NewBytes (Get-OutputPath $Relative) $script:utf8.GetBytes($Text) $Relative }
function Write-NewJson([string]$Relative, $Value) {
    $json = ConvertTo-Json -InputObject $Value -Depth 40 -Compress
    $bytes = $script:utf8.GetBytes($json + "`n")
    Require ($bytes.Length -le 65536) 'STRUCTURED_OUTPUT_LIMIT'
    Write-NewBytes (Get-OutputPath $Relative) $bytes $Relative
}
function Test-ByteEquality([byte[]]$A, [byte[]]$B) {
    if ($A.Length -ne $B.Length) { return $false }
    for ($i=0; $i -lt $A.Length; $i++) { if ($A[$i] -ne $B[$i]) { return $false } }
    return $true
}
function Test-VerifierLogsRemoved($ProcessResult) {
    foreach ($name in @('ProcessRoot','StdoutLog','StderrLog')) {
        $property = $ProcessResult.PSObject.Properties[$name]
        Require ($null -ne $property -and $property.Value -is [string] -and -not [string]::IsNullOrWhiteSpace([string]$property.Value)) 'PROCESS_LOG_RECEIPT'
        Require (-not (Test-Path -LiteralPath ([string]$property.Value))) 'PROCESS_LOG_REMAINS'
    }
}
function Validate-Receipt($ProcessResult, [string]$FilePath, [string[]]$Arguments) {
    Require-ExactKeys $ProcessResult @('FilePath','Arguments','ProcessId','ProcessStartTicks','ExitCode','Stdout','Stderr','StdoutLog','StderrLog','ProcessRoot','TerminationProven') 'PROCESS_RECEIPT_SCHEMA'
    Require ([string]::Equals([string]$ProcessResult.FilePath,$FilePath,[StringComparison]::Ordinal)) 'PROCESS_IDENTITY'
    Require ($ProcessResult.Arguments -is [array] -and $ProcessResult.Arguments.Count -eq $Arguments.Count) 'PROCESS_ARGUMENTS'
    for ($i=0; $i -lt $Arguments.Count; $i++) { Require ([string]::Equals([string]$ProcessResult.Arguments[$i],[string]$Arguments[$i],[StringComparison]::Ordinal)) 'PROCESS_ARGUMENTS' }
    Require-Integer $ProcessResult.ProcessId 1 ([int]::MaxValue) 'PROCESS_PID'
    Require-Integer $ProcessResult.ProcessStartTicks 1 ([long]::MaxValue) 'PROCESS_START_TICKS'
    Require-Integer $ProcessResult.ExitCode ([int]::MinValue) ([int]::MaxValue) 'PROCESS_EXIT_CODE'
    Require ($ProcessResult.Stdout -is [string] -and $ProcessResult.Stderr -is [string] -and $ProcessResult.TerminationProven -is [bool] -and $ProcessResult.TerminationProven) 'PROCESS_TERMINATION'
    Test-VerifierLogsRemoved $ProcessResult
}
function Invoke-BoundedChild([string]$FilePath, [string[]]$Arguments, [int]$LimitMs, [string]$CallPhase) {
    $started = $script:hostClock.Elapsed.TotalMilliseconds
    $script:returnedChildEvidence = $null
    try { $raw = Invoke-VerifierBoundedProcess -FilePath $FilePath -Arguments $Arguments -TimeoutMilliseconds $LimitMs }
    catch {
        $errorRecord = $_; $exception = $errorRecord.Exception; $script:failedProcessErrorRecord = $errorRecord; $data = @()
        if ($null -ne $exception.Data) { foreach ($key in $exception.Data.Keys) { $data += @([ordered]@{key=$key;value=$exception.Data[$key]}) } }
        $script:failedProcessCall = [ordered]@{schema='tsj-q100-reservation-failed-call-v1';status='CALL_FAILED_WITHOUT_RETURNED_PROCESS_RECEIPT';phase=$CallPhase;
            attemptedExecutable=$FilePath;attemptedArguments=@($Arguments);limitMs=$LimitMs;elapsedMs=[double]($script:hostClock.Elapsed.TotalMilliseconds-$started);
            exception=[ordered]@{type=[string]$exception.GetType().FullName;message=[string]$exception.Message;toString=[string]$exception.ToString()};exceptionData=$data;
            terminationProven=$false;process=$null}
        throw 'CALL_FAILED_WITHOUT_RETURNED_PROCESS_RECEIPT'
    }
    $wall = [double]($script:hostClock.Elapsed.TotalMilliseconds-$started)
    $script:returnedChildEvidence = [ordered]@{ phase=$CallPhase; wallMs=$wall; process=$raw }
    $script:registeredProcessCaptures += [pscustomobject]@{ ordinal=[int]$script:registeredProcessCaptures.Count; phase=$CallPhase; wallMs=$wall; process=$raw }
    $stdoutBytes = $script:utf8.GetByteCount([string]$raw.Stdout)
    $stderrBytes = $script:utf8.GetByteCount([string]$raw.Stderr)
    Require (([decimal]$stdoutBytes+[decimal]$stderrBytes) -le 8388608) 'PROCESS_CAPTURE_LIMIT'
    $script:childWallSumMs += $wall
    $script:activeLaunchIntent = $null
    return [pscustomobject]@{ process=$raw; wallMs=$wall }
}
function Save-Process([string]$ReceiptName, [string]$StdoutName, [string]$StderrName, $ProcessResult, [string]$ExpectedExecutable, [string[]]$ExpectedArgs) {
    Write-NewJson $ReceiptName $ProcessResult
    $stdoutBytes = $script:utf8.GetBytes([string]$ProcessResult.Stdout)
    $stderrBytes = $script:utf8.GetBytes([string]$ProcessResult.Stderr)
    Write-NewBytes (Get-OutputPath $StdoutName) $stdoutBytes $StdoutName
    Write-NewBytes (Get-OutputPath $StderrName) $stderrBytes $StderrName
    Require (Test-ByteEquality $stdoutBytes ([IO.File]::ReadAllBytes((Get-OutputPath $StdoutName)))) 'STDOUT_COPY_MISMATCH'
    Require (Test-ByteEquality $stderrBytes ([IO.File]::ReadAllBytes((Get-OutputPath $StderrName)))) 'STDERR_COPY_MISMATCH'
    Validate-Receipt $ProcessResult $ExpectedExecutable $ExpectedArgs
    return [ordered]@{receipt=$ReceiptName;stdout=$StdoutName;stderr=$StderrName}
}
function Add-ProcessRecord([string]$Role, $Evidence, [double]$WallMs) {
    Require (-not $script:processRecords.Contains($Role)) 'PROCESS_ROLE_DUPLICATE'
    $script:processRecords.Add($Role,[ordered]@{receipt=$Evidence.receipt;stdout=$Evidence.stdout;stderr=$Evidence.stderr;wallMs=$WallMs})
}
function Parse-OneMarker([string]$Text, [string]$Prefix, [string]$Code, [switch]$AllowPreamble) {
    $pattern = '(?m)^' + [regex]::Escape($Prefix) + ' (?<json>\{[^\r\n]*\})\r?$'
    $matches = [regex]::Matches($Text,$pattern)
    Require ($matches.Count -eq 1) $Code
    $m = $matches[0]; $tail = $Text.Substring($m.Index+$m.Length); $lineEnding=$null
    if ($tail.StartsWith("`r`n",[StringComparison]::Ordinal)) { $lineEnding="`r`n" }
    elseif ($tail.StartsWith("`n",[StringComparison]::Ordinal)) { $lineEnding="`n" }
    Require ($null -ne $lineEnding) $Code
    $afterLine=$tail.Substring($lineEnding.Length)
    Require (($AllowPreamble -or $m.Index -eq 0) -and $afterLine -ceq '') $Code
    Require ($script:utf8.GetByteCount($m.Value+$lineEnding) -le 65536) $Code
    try { $v = ConvertFrom-Json -InputObject $m.Groups['json'].Value -ErrorAction Stop } catch { throw $Code }
    return $v
}
function Parse-AuditorSuccess([string]$Text) {
    $prefix = 'PASS_Q100_RESERVATION_INPUTS_ONLY'
    $v = Parse-OneMarker $Text $prefix 'INPUT_AUDIT_MARKER'
    Require-ExactKeys $v @('status','pinsSha256','sourceInventorySha256','existingFiles','directFiles','derivedFiles','totalFiles','productionFiles','topFiles') 'INPUT_AUDIT_SCHEMA'
    Require ([string]$v.status -ceq $prefix -and [string]$v.pinsSha256 -ceq $script:InputPinsSha256 -and [string]$v.sourceInventorySha256 -ceq $script:sourceInventorySha256) 'INPUT_AUDIT_BINDING'
    Require-Integer $v.existingFiles 3437 3437 'INPUT_AUDIT_COUNTS'; Require-Integer $v.directFiles 17 17 'INPUT_AUDIT_COUNTS'
    Require-Integer $v.derivedFiles 2 2 'INPUT_AUDIT_COUNTS'; Require-Integer $v.totalFiles 3456 3456 'INPUT_AUDIT_COUNTS'
    Require-Integer $v.productionFiles 850 850 'INPUT_AUDIT_COUNTS'; Require-Integer $v.topFiles 27 27 'INPUT_AUDIT_COUNTS'
    return [ordered]@{status=[string]$v.status;pinsSha256=[string]$v.pinsSha256;sourceInventorySha256=[string]$v.sourceInventorySha256;
        existingFiles=[long]$v.existingFiles;directFiles=[long]$v.directFiles;derivedFiles=[long]$v.derivedFiles;totalFiles=[long]$v.totalFiles;productionFiles=[long]$v.productionFiles;topFiles=[long]$v.topFiles}
}
function Invoke-Auditor([string]$Stage, [string]$Role, [string]$Stem, [switch]$MemoryOnly) {
    $args = @('-I','-B',$script:auditorPath,$script:InputPinsPath,$script:InputPinsSha256)
    $script:phase = 'SOURCE_' + $Stage.ToUpperInvariant()
    $intent = [ordered]@{schema='tsj-q100-reservation-provenance-launch-v1';role=$Role;owner=[ordered]@{pid=[int]$PID;startTicks=[long]$script:hostStartTicks;
        executable=[string]$script:owner.Path;executableSha256=$script:selectedPowerShellSha256};executable=$script:pythonPath;executableSha256=$script:selectedPythonSha256;
        arguments=@($args);limitMs=60000;inputSha256=$script:InputPinsSha256;sourceSha256=$script:sourceInventorySha256;outputRoot=$script:OutputRoot}
    $script:activeLaunchIntent = $intent
    $call = Invoke-BoundedChild $script:pythonPath $args 60000 $script:phase
    $p = $call.process
    if ($MemoryOnly) {
        Validate-Receipt $p $script:pythonPath $args
        Require ($p.ExitCode -eq 0 -and [string]::IsNullOrEmpty([string]$p.Stderr)) 'INPUT_AUDIT_PROCESS'
        $summary = Parse-AuditorSuccess ([string]$p.Stdout)
        $script:returnedChildEvidence = $null
        return [pscustomobject]@{summary=$summary;call=$call;intent=$intent;role=$Role;stem=$Stem;args=$args}
    }
    $evidence = Save-Process ($Stem+'-process.json') ($Stem+'.stdout') ($Stem+'.stderr') $p $script:pythonPath $args
    Add-ProcessRecord $Role $evidence $call.wallMs
    Require ($p.ExitCode -eq 0 -and [string]::IsNullOrEmpty([string]$p.Stderr)) 'INPUT_AUDIT_PROCESS'
    $summary = Parse-AuditorSuccess ([string]$p.Stdout)
    $script:returnedChildEvidence = $null
    return [pscustomobject]@{summary=$summary;wallMs=[double]$call.wallMs}
}
function Save-PreflightEvidence($Preflight) {
    $p = $Preflight.call.process
    $evidence = Save-Process 'source-preflight-process.json' 'source-preflight.stdout' 'source-preflight.stderr' $p $script:pythonPath $Preflight.args
    Add-ProcessRecord 'preflight' $evidence ([double]$Preflight.call.wallMs)
    $script:sourceChecks.preflight = $Preflight.summary
}
function Write-LaunchIntent([string]$Relative, [string]$Role, [string]$Executable, [string]$ExecutableSha, [string[]]$Arguments, [int]$LimitMs) {
    $intent = [ordered]@{schema='tsj-q100-reservation-provenance-launch-v1';role=$Role;
        owner=[ordered]@{pid=[int]$PID;startTicks=[long]$script:hostStartTicks;executable=[string]$script:owner.Path;executableSha256=$script:selectedPowerShellSha256};
        executable=$Executable;executableSha256=$ExecutableSha;arguments=@($Arguments);limitMs=$LimitMs;
        inputSha256=$script:InputPinsSha256;sourceSha256=$script:sourceInventorySha256;outputRoot=$script:outputCanonical}
    Write-NewJson $Relative $intent
    $script:activeLaunchIntent = $intent
    return $intent
}
function Write-HostLaunchIntent {
    $args = @('-NoLogo','-NoProfile','-NonInteractive','-File',$script:hostPath,'-RepositoryRoot',$script:RepositoryRoot,'-InputPinsPath',$script:InputPinsPath,
        '-InputPinsSha256',$script:InputPinsSha256,'-OutputRoot',$script:OutputRoot,'-JavaHome',$script:JavaHome,'-PythonExe',$script:PythonExe,'-Mode',$script:Mode)
    $v = [ordered]@{schema='tsj-q100-reservation-provenance-launch-v1';role='host';owner=[ordered]@{pid=[int]$PID;startTicks=[long]$script:hostStartTicks;
        executable=[string]$script:owner.Path;executableSha256=$script:selectedPowerShellSha256};executable=$script:owner.Path;executableSha256=$script:selectedPowerShellSha256;
        arguments=$args;limitMs=1800000;inputSha256=$script:InputPinsSha256;sourceSha256=$script:sourceInventorySha256;outputRoot=$script:outputCanonical;
        mode=$script:Mode;script=$script:hostPath;scriptSha256=$script:hostSha;outputCreationTicks=[long]$script:outputCreationTicks;
        sourceParent=$script:sourceParent;p8PrerequisiteSha256=$script:p8PinsSha256}
    Write-NewJson 'host-launch-intent.json' $v
}
function Read-StrictJsonFile([string]$Path, [long]$MaximumBytes, [string]$Code) {
    $bytes = [IO.File]::ReadAllBytes($Path)
    Require ($bytes.Length -le $MaximumBytes -and $bytes.Length -gt 0) $Code
    Require (-not ($bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191)) $Code
    foreach ($b in $bytes) { Require ($b -lt 128) $Code }
    $text = $script:strictUtf8.GetString($bytes)
    Require ($text.EndsWith("`n") -and -not $text.Contains("`r") -and -not $text.Substring(0,$text.Length-1).EndsWith("`n")) $Code
    Assert-UniqueJsonKeys $text $Code
    try { $value = ConvertFrom-Json -InputObject $text.Substring(0,$text.Length-1) -ErrorAction Stop } catch { throw $Code }
    return $value
}
function Require-PinnedRow($Rows, [string]$Path, [string]$Code) {
    $matchingRows = @($Rows | Where-Object { [string]::Equals([string]$_.path,$Path,[StringComparison]::OrdinalIgnoreCase) })
    Require ($matchingRows.Count -eq 1) $Code
    Require-ExactKeys $matchingRows[0] @('path','bytes','sha256') $Code
    Require-Integer $matchingRows[0].bytes 0 ([long]::MaxValue) $Code
    Require ([string]$matchingRows[0].sha256 -cmatch '^[0-9a-f]{64}$') $Code
    $file = Require-ExistingFile $Path $Code
    $info = Get-Item -LiteralPath $file -Force
    Require ($info.Length -eq [long]$matchingRows[0].bytes -and (Get-Hash $file) -ceq [string]$matchingRows[0].sha256) $Code
    return $matchingRows[0]
}
function Load-HostInputs {
    $script:p9Root = Require-ExistingDirectory $PSScriptRoot 'P9_ROOT'
    $script:hostPath = Require-ExistingFile $PSCommandPath 'P9_INPUT_SELF_PIN'
    $script:hostSha = Get-Hash $script:hostPath
    $actualProcess = [Diagnostics.Process]::GetCurrentProcess()
    $script:owner = [pscustomobject]@{ Path=$actualProcess.MainModule.FileName; StartTimeUtc=$actualProcess.StartTime.ToUniversalTime() }
    $script:hostStartTicks = [long]$script:owner.StartTimeUtc.Ticks
    $ownerExe = Require-ExistingFile ([string]$script:owner.Path) 'P9_SELECTED_POWERSHELL'
    Require ((Get-Hash $ownerExe) -ceq $script:selectedPowerShellSha256) 'P9_SELECTED_POWERSHELL'
    $script:InputPinsPath = Require-ExistingFile $InputPinsPath 'P9_INPUT_PINS_PATH'
    Require ((Get-Hash $script:InputPinsPath) -ceq $InputPinsSha256) 'P9_INPUT_PINS_HASH'
    $script:pins = Read-StrictJsonFile $script:InputPinsPath 4194304 'P9_INPUT_PINS_SCHEMA'
    Require-ExactKeys $script:pins @('schema','sourceParent','repositoryRoot','p8Prerequisite','publishedP8','sourceInventory','roles','toolPaths','files') 'P9_INPUT_PINS_SCHEMA'
    Require ([string]$script:pins.schema -ceq 'tsj-q100-reservation-provenance-inputs-v1' -and [string]$script:pins.sourceParent -ceq $script:sourceParent) 'P9_INPUT_PINS_SCHEMA'
    $script:RepositoryRoot = Require-ExistingDirectory $RepositoryRoot 'P9_REPOSITORY_ROOT'
    Require ([string]$script:pins.repositoryRoot -ceq $script:RepositoryRoot) 'P9_REPOSITORY_ROOT'
    Require-ExactKeys $script:pins.p8Prerequisite @('pinsPath','pinsSha256','sourceInventorySha256') 'P9_P8_PREREQUISITE'
    Require ([string]$script:pins.p8Prerequisite.pinsSha256 -ceq $script:p8PinsSha256 -and [string]$script:pins.p8Prerequisite.sourceInventorySha256 -ceq 'dba31be32c8d0d838fa8a1859c2a37a62a619b517515714ef519e59ce4a02de8') 'P9_P8_PREREQUISITE'
    Require-ExactKeys $script:pins.sourceInventory @('path','sha256') 'P9_INVENTORY_PIN'
    Require ([string]$script:pins.sourceInventory.sha256 -cmatch '^[0-9a-f]{64}$' -and [IO.Path]::GetFileName([string]$script:pins.sourceInventory.path) -ceq 'native-source-inventory.jsonl') 'P9_INVENTORY_PIN'
    $script:sourceInventorySha256 = [string]$script:pins.sourceInventory.sha256
    Require-ExactKeys $script:pins.roles @('native','process','input') 'P9_ROLE_SCHEMA'
    Require-ExactKeys $script:pins.roles.native @('study','canary','routerOverlay') 'P9_ROLE_SCHEMA'
    Require-ExactKeys $script:pins.roles.process @('helper','host','controls','reader') 'P9_ROLE_SCHEMA'
    Require-ExactKeys $script:pins.roles.input @('builder','maker','auditor','test') 'P9_ROLE_SCHEMA'
    Require-ExactKeys $script:pins.toolPaths @('pythonExe','javaHome','powershellExe') 'P9_TOOL_SCHEMA'
    Require ($script:pins.files -is [array] -and $script:pins.files.Count -eq 27) 'P9_INPUT_FILE_CENSUS'
    Require-ExactKeys $script:pins.files[0] @('path','bytes','sha256') 'P9_INPUT_FILE_SCHEMA'
    Require ((Test-PathEqual ([string]$script:pins.roles.process.host) $script:hostPath) -and
        (Test-PathEqual ([string]$script:pins.roles.process.controls) ([IO.Path]::GetFullPath((Join-Path $script:p9Root 'run-controls.ps1')))) -and
        (Test-PathEqual ([string]$script:pins.roles.process.reader) ([IO.Path]::GetFullPath((Join-Path $script:p9Root 'read_provenance.py')))) -and
        (Test-PathEqual ([string]$script:pins.roles.input.builder) ([IO.Path]::GetFullPath((Join-Path $script:p9Root 'build_overlay.py')))) -and
        (Test-PathEqual ([string]$script:pins.roles.input.maker) ([IO.Path]::GetFullPath((Join-Path $script:p9Root 'make_inputs.py')))) -and
        (Test-PathEqual ([string]$script:pins.roles.input.auditor) ([IO.Path]::GetFullPath((Join-Path $script:p9Root 'audit_inputs.py')))) -and
        (Test-PathEqual ([string]$script:pins.roles.input.test) ([IO.Path]::GetFullPath((Join-Path $script:p9Root 'test_provenance.py')))) -and
        (Test-PathEqual ([string]$script:pins.roles.native.study) ([IO.Path]::GetFullPath((Join-Path $script:p9Root 'source/CanonicalReservationProvenanceStudy.java')))) -and
        (Test-PathEqual ([string]$script:pins.roles.native.canary) ([IO.Path]::GetFullPath((Join-Path $script:p9Root 'source/CanonicalReservationProvenanceCanary.java')))) -and
        (Test-PathEqual ([string]$script:pins.roles.native.routerOverlay) ([IO.Path]::GetFullPath((Join-Path $script:p9Root 'overlays-r1/PcbNetRouter.java'))))) 'P9_ROLE_MAPPING'
    $selfRow = Require-PinnedRow $script:pins.files $script:hostPath 'P9_INPUT_SELF_PIN'
    Require ((Get-Hash $script:hostPath) -ceq [string]$selfRow.sha256) 'P9_INPUT_SELF_PIN'
    foreach ($fileRow in $script:pins.files) {
        Require-ExactKeys $fileRow @('path','bytes','sha256') 'P9_INPUT_FILE_HASH'
        Require-Integer $fileRow.bytes 1 104857600 'P9_INPUT_FILE_HASH'
        Require ($fileRow.sha256 -is [string] -and $fileRow.sha256 -cmatch '^[0-9a-f]{64}$') 'P9_INPUT_FILE_HASH'
        $filePath = Require-ExistingFile ([string]$fileRow.path) 'P9_INPUT_FILE_HASH'
        Require ((Get-Item -LiteralPath $filePath).Length -eq [long]$fileRow.bytes -and
            (Get-Hash $filePath) -ceq [string]$fileRow.sha256) 'P9_INPUT_FILE_HASH'
    }
    $script:PythonExe = Require-ExistingFile $PythonExe 'P9_TOOL_PATH'
    $script:pythonPath = $script:PythonExe
    Require ([string]$script:pins.toolPaths.pythonExe -ceq $script:PythonExe -and (Get-Hash $script:PythonExe) -ceq $script:selectedPythonSha256) 'P9_TOOL_IDENTITY'
    $script:JavaHome = Require-ExistingDirectory $JavaHome 'P9_TOOL_PATH'
    Require ([string]$script:pins.toolPaths.javaHome -ceq $script:JavaHome) 'P9_TOOL_IDENTITY'
    Require ([string]$script:pins.toolPaths.powershellExe -ceq $ownerExe) 'P9_TOOL_IDENTITY'
    $script:java = Require-ExistingFile (Join-Path $script:JavaHome 'bin/java.exe') 'P9_JAVA_IDENTITY'
    $script:javac = Require-ExistingFile (Join-Path $script:JavaHome 'bin/javac.exe') 'P9_JAVAC_IDENTITY'
    Require ((Get-Hash $script:java) -ceq $script:selectedJavaSha256 -and (Get-Hash $script:javac) -ceq $script:selectedJavacSha256) 'P9_JAVA_IDENTITY'
    $script:auditorPath = Require-PinnedRow $script:pins.files ([string]$script:pins.roles.input.auditor) 'P9_INPUT_AUDITOR_PIN' | ForEach-Object { [string]$_.path }
    $expectedAuditor = [IO.Path]::GetFullPath((Join-Path $script:p9Root 'audit_inputs.py'))
    Require ([string]$script:auditorPath -ceq $expectedAuditor) 'P9_INPUT_AUDITOR_PIN'
    $helperPath = [string]$script:pins.roles.process.helper
    Require ((Get-Hash (Require-ExistingFile $helperPath 'P9_PROCESS_HELPER_PIN')) -ceq $script:selectedModuleSha256) 'P9_PROCESS_HELPER_PIN'
    $script:modulePath = $helperPath
    $script:p8PinsPath = Require-ExistingFile ([string]$script:pins.p8Prerequisite.pinsPath) 'P9_P8_PINS_PATH'
    Require ((Get-Hash $script:p8PinsPath) -ceq $script:p8PinsSha256) 'P9_P8_PINS_HASH'
    $script:p8Pins = Read-StrictJsonFile $script:p8PinsPath 4194304 'P9_P8_PINS_SCHEMA'
    Require ([string]$script:p8Pins.schema -ceq 'tsj-q100-geometric-prefix-study-inputs-v1' -and [string]$script:p8Pins.parent -ceq $script:p8SourceParent) 'P9_P8_PINS_SCHEMA'
    $script:p6PinsPath = Require-ExistingFile ([string]$script:p8Pins.p6Context.pinsPath) 'P9_P6_PINS_PATH'
    Require ((Get-Hash $script:p6PinsPath) -ceq $script:selectedP6PinsSha256) 'P9_P6_PINS_HASH'
    $script:p6Pins = Read-StrictJsonFile $script:p6PinsPath 4194304 'P9_P6_PINS_SCHEMA'
    $script:canaryPinsPath = Require-ExistingFile ([string]$script:p8Pins.canaryPrerequisite.pinsPath) 'P9_CANARY_PINS_PATH'
    Require ((Get-Hash $script:canaryPinsPath) -ceq $script:selectedCanaryPinsSha256) 'P9_CANARY_PINS_HASH'
    $script:canaryPins = Read-StrictJsonFile $script:canaryPinsPath 4194304 'P9_CANARY_PINS_SCHEMA'
    Require ($script:p6Pins.sourceRows -is [array] -and $script:p6Pins.sourceRows.Count -eq 1084 -and $script:canaryPins.sourceRows -is [array] -and $script:canaryPins.sourceRows.Count -eq 1084) 'P9_SOURCE_ROWS'
    $wireRows = @($script:p6Pins.sourceRows | Where-Object { [string]$_.role -ceq 'WIRE' })
    Require ($wireRows.Count -eq 1) 'P9_WIRE_SOURCE'
    $wire = $wireRows[0]
    Require ([string]$wire.path -ceq 'docs/task-evidence/Q100/outf-graph-r1/WIRE.md' -and [long]$wire.bytes -eq 33064 -and [string]$wire.sha256 -ceq '0bd76a4fe4261a8abe5c92b2b0b5f50000077e9541e7ca7e84e9af3ed1661cfb') 'P9_WIRE_SOURCE'
    $wirePath = Require-ExistingFile ([IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot ([string]$wire.path)))) 'P9_WIRE_SOURCE'
    Require ((Get-Item -LiteralPath $wirePath).Length -eq [long]$wire.bytes -and (Get-Hash $wirePath) -ceq [string]$wire.sha256) 'P9_WIRE_SOURCE'
    $script:wireSha256 = [string]$wire.sha256
    $script:bridgeManifestPath = Require-ExistingFile ([string]$script:p6Pins.bridgeManifest) 'P9_BRIDGE_MANIFEST'
    $script:bridgeRoot = Require-ExistingDirectory ([string]$script:p6Pins.bridgeRoot) 'P9_BRIDGE_ROOT'
    Require-PinnedRow $script:p6Pins.files $script:bridgeManifestPath 'P9_BRIDGE_MANIFEST_PIN' | Out-Null
    $script:sourceInventoryPath = Require-ExistingFile ([string]$script:pins.sourceInventory.path) 'P9_INVENTORY_PATH'
    Require ((Get-Hash $script:sourceInventoryPath) -ceq $script:sourceInventorySha256) 'P9_INVENTORY_HASH'
    $module = Get-Module | Where-Object { $_.Name -ceq 'VerifierIsolation' }
    if ($null -eq $module) { Import-Module -Name $script:modulePath -Force -DisableNameChecking -ErrorAction Stop }
    Require ($null -ne (Get-Command Invoke-VerifierBoundedProcess -ErrorAction SilentlyContinue)) 'P9_PROCESS_HELPER_API'
}
function Require-SourceRow($Rows, [string]$RelativePath, [string]$Role, [string]$Code) {
    $matchingRows = @($Rows | Where-Object { [string]::Equals([string]$_.path,$RelativePath,[StringComparison]::Ordinal) })
    Require ($matchingRows.Count -eq 1) $Code
    Require-ExactKeys $matchingRows[0] @('path','bytes','role','sha256') $Code
    Require ([string]$matchingRows[0].role -ceq $Role) $Code
    Require-Integer $matchingRows[0].bytes 1 ([long]::MaxValue) $Code
    Require ([string]$matchingRows[0].sha256 -cmatch '^[0-9a-f]{64}$') $Code
    return $matchingRows[0]
}
function Verify-PinnedSourceRow($Row, [string]$Path, [string]$Code) {
    $full = Require-ExistingFile $Path $Code
    $info = Get-Item -LiteralPath $full -Force
    Require ($info.Length -eq [long]$Row.bytes -and (Get-Hash $full) -ceq [string]$Row.sha256) $Code
    return $full
}
function Verify-BridgeCopies {
    foreach ($copy in $script:bridgeCopies) {
        $dest = [string]$copy.path
        $source = Join-Path $script:bridgeRoot ('bridge-' + [IO.Path]::GetFileName($dest))
        $sourceRow = Require-PinnedRow $script:p6Pins.files $source 'P9_BRIDGE_SOURCE'
        Require ((Get-Item -LiteralPath $source).Length -eq [long]$copy.bytes -and (Get-Hash $source) -ceq [string]$copy.sha256) 'P9_BRIDGE_SOURCE'
        Require ((Get-Item -LiteralPath $dest).Length -eq [long]$copy.bytes -and (Get-Hash $dest) -ceq [string]$copy.sha256) 'P9_BRIDGE_COPY'
        [void]$sourceRow
    }
}
function Load-P9Inventory {
    $bytes = [IO.File]::ReadAllBytes($script:sourceInventoryPath)
    Require ($bytes.Length -le 1048576 -and $bytes.Length -gt 0) 'P9_INVENTORY_SIZE'
    foreach ($b in $bytes) { Require ($b -lt 128) 'P9_INVENTORY_ENCODING' }
    Require (-not ($bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191)) 'P9_INVENTORY_ENCODING'
    $text = $script:strictUtf8.GetString($bytes)
    Require ($text.EndsWith("`n") -and -not $text.Contains("`r")) 'P9_INVENTORY_FORMAT'
    $lines = $text.Split([char]10)
    Require ($lines.Count -eq 3459 -and $lines[$lines.Count-1] -ceq '') 'P9_INVENTORY_CENSUS'
    foreach ($line in $lines) { if ($line.Length -gt 65536) { throw 'P9_INVENTORY_LINE_LIMIT' } }
    $script:inventoryRows = @()
    for ($i=0; $i -lt 3458; $i++) {
        Assert-UniqueJsonKeys $lines[$i] 'P9_INVENTORY_JSON'
        try { $row = ConvertFrom-Json -InputObject $lines[$i] -ErrorAction Stop } catch { throw 'P9_INVENTORY_JSON' }
        if ($i -eq 0) {
            Require-ExactKeys $row @('kind','schema','sourceParent','roots','rowCounts','byteCounts') 'P9_INVENTORY_HEADER'
            Require ([string]$row.kind -ceq 'header' -and [string]$row.schema -ceq 'tsj-q100-reservation-provenance-source-v1' -and [string]$row.sourceParent -ceq $script:sourceParent) 'P9_INVENTORY_HEADER'
            Require-ExactKeys $row.rowCounts @('existing','direct','derived','total') 'P9_INVENTORY_COUNTS'
            Require-ExactKeys $row.byteCounts @('existing','direct','derived','total') 'P9_INVENTORY_COUNTS'
            Require-Integer $row.rowCounts.existing 3437 3437 'P9_INVENTORY_COUNTS'; Require-Integer $row.rowCounts.direct 17 17 'P9_INVENTORY_COUNTS'
            Require-Integer $row.rowCounts.derived 2 2 'P9_INVENTORY_COUNTS'; Require-Integer $row.rowCounts.total 3456 3456 'P9_INVENTORY_COUNTS'
            Require-Integer $row.byteCounts.existing 283692817 283692817 'P9_INVENTORY_COUNTS'
            $script:inventoryHeader = $row
            continue
        }
        if ($i -eq 3457) {
            Require-ExactKeys $row @('kind','rowCounts','byteCounts') 'P9_INVENTORY_FOOTER'
            Require ([string]$row.kind -ceq 'footer') 'P9_INVENTORY_FOOTER'
            Require-ExactKeys $row.rowCounts @('existing','direct','derived','total') 'P9_INVENTORY_FOOTER'
            Require-ExactKeys $row.byteCounts @('existing','direct','derived','total') 'P9_INVENTORY_FOOTER'
            Require ((ConvertTo-Json -InputObject $row.rowCounts -Compress) -ceq (ConvertTo-Json -InputObject $script:inventoryHeader.rowCounts -Compress) -and
                (ConvertTo-Json -InputObject $row.byteCounts -Compress) -ceq (ConvertTo-Json -InputObject $script:inventoryHeader.byteCounts -Compress)) 'P9_INVENTORY_FOOTER'
            continue
        }
        Require-ExactKeys $row @('kind','path','bytes','sha256','role','origin') 'P9_INVENTORY_ROW'
        Require ([string]$row.kind -cin @('source','dependency','direct','derived') -and [string]$row.path -cmatch '^[A-Za-z0-9_./-]+$' -and
            -not ([string]$row.path).StartsWith('/') -and -not ([string]$row.path).Contains('..') -and [string]$row.sha256 -cmatch '^[0-9a-f]{64}$') 'P9_INVENTORY_ROW'
        Require-Integer $row.bytes 1 ([long]::MaxValue) 'P9_INVENTORY_ROW'
        $script:inventoryRows += @($row)
    }
    Require ($script:inventoryRows.Count -eq 3456) 'P9_INVENTORY_CENSUS'
}
function New-CompilerSources {
    $paths = New-Object 'System.Collections.Generic.List[string]'
    foreach ($row in $script:p6Pins.sourceRows) {
        if ([string]$row.role -ceq 'BASE_SOURCE' -and [string]$row.path -cmatch '\.java$') {
            $relative = [string]$row.path
            if ($relative -cnotin $script:excludedBaseSources) {
                $path = [IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot $relative))
                Require (Test-PathUnder $path $script:RepositoryRoot) 'P9_BASE_SOURCE_PATH'
                Assert-NoReparse $path
                Require (Test-Path -LiteralPath $path -PathType Leaf) 'P9_BASE_SOURCE_PATH'
                [void]$paths.Add($path)
            }
        }
    }
    foreach ($role in $script:modeRoles) {
        if ($role -ceq 'netRouterOverlay') {
            $path = [string]$script:pins.roles.native.routerOverlay
            $relative = 'overlays-r1/PcbNetRouter.java'
            $inventoryMatches = @($script:inventoryRows | Where-Object {
                $_.origin -ceq 'P9' -and $_.kind -ceq 'derived' -and $_.role -ceq 'ROUTER_OVERLAY' -and
                [string]::Equals([string]$_.path,$relative,[StringComparison]::Ordinal)
            })
            Require ($inventoryMatches.Count -eq 1 -and [string]$inventoryMatches[0].role -ceq 'ROUTER_OVERLAY') 'P9_ROUTER_OVERLAY_ROW'
            Require ([string]::Equals([string]$script:inventoryHeader.roots.P9,$script:p9Root,[StringComparison]::Ordinal) -and
                [string]::Equals([IO.Path]::GetFullPath((Join-Path $script:p9Root $relative)),$path,[StringComparison]::Ordinal)) 'P9_ROUTER_OVERLAY_BINDING'
            [void](Verify-PinnedSourceRow $inventoryMatches[0] $path 'P9_ROUTER_OVERLAY_HASH')
        } else {
            $path = [string]$script:canaryPins.modeFiles.$role
            if ($role -ceq 'writer') {
                $relative = 'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java'
                $row = Require-SourceRow $script:canaryPins.sourceRows $relative 'WRITER' 'P9_WRITER_SOURCE'
                Require (Test-PathEqual $path ([IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot $relative)))) 'P9_WRITER_SOURCE'
                [void](Verify-PinnedSourceRow $row $path 'P9_WRITER_SOURCE')
            } else {
                $row = Require-PinnedRow $script:canaryPins.files $path 'P9_MODE_ROLE_PIN'
            }
        }
        [void]$paths.Add((Require-ExistingFile $path 'P9_MODE_ROLE_PATH'))
    }
    $bridgePin = Require-PinnedRow $script:p6Pins.files $script:bridgeManifestPath 'P9_BRIDGE_MANIFEST_PIN'
    $bridgeRaw = [IO.File]::ReadAllBytes($script:bridgeManifestPath)
    Require ($bridgeRaw.Length -gt 0 -and $bridgeRaw.Length -le 65536 -and
        $bridgeRaw.Length -eq [long]$bridgePin.bytes) 'P9_BRIDGE_MANIFEST_SIZE'
    $bridgeHasher = [Security.Cryptography.SHA256]::Create()
    try { $bridgeReadSha = ([BitConverter]::ToString($bridgeHasher.ComputeHash($bridgeRaw))).Replace('-','').ToLowerInvariant() }
    finally { $bridgeHasher.Dispose() }
    Require ($bridgeReadSha -ceq [string]$bridgePin.sha256) 'P9_BRIDGE_MANIFEST_PIN'
    $bridgeText = $script:strictUtf8.GetString($bridgeRaw)
    # This authenticated ancestor manifest retains its original CRLF/LF JSON whitespace.
    Assert-UniqueJsonKeys $bridgeText 'P9_BRIDGE_MANIFEST_SCHEMA'
    try { $bridgeRows = ConvertFrom-Json -InputObject $bridgeText.TrimEnd("`n") -ErrorAction Stop } catch { throw 'P9_BRIDGE_MANIFEST_SCHEMA' }
    Require ($bridgeRows -is [array] -and $bridgeRows.Count -eq 3) 'P9_BRIDGE_CENSUS'
    $seen = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
    foreach ($row in $bridgeRows) {
        Require-ExactKeys $row @('path','bytes','sha256') 'P9_BRIDGE_ROW'
        Require ([string]$row.path -cin $script:bridgeNames -and $seen.Add([string]$row.path)) 'P9_BRIDGE_ROW'
        Require-Integer $row.bytes 1 ([long]::MaxValue) 'P9_BRIDGE_ROW'
        Require ([string]$row.sha256 -cmatch '^[0-9a-f]{64}$') 'P9_BRIDGE_ROW'
        $from = Require-ExistingFile (Join-Path $script:bridgeRoot ('bridge-'+[string]$row.path)) 'P9_BRIDGE_SOURCE'
        $pin = Require-PinnedRow $script:p6Pins.files $from 'P9_BRIDGE_SOURCE_PIN'
        Require ([long]$pin.bytes -eq [long]$row.bytes -and [string]$pin.sha256 -ceq [string]$row.sha256) 'P9_BRIDGE_SOURCE_HASH'
        $to = Join-Path $script:scratch ([string]$row.path)
        [IO.File]::Copy($from,$to,$false)
        Require ((Get-Item -LiteralPath $to).Length -eq [long]$row.bytes -and (Get-Hash $to) -ceq [string]$row.sha256) 'P9_BRIDGE_COPY_HASH'
        $script:bridgeCopies += @([ordered]@{path=$to;bytes=[long]$row.bytes;sha256=[string]$row.sha256})
        [void]$paths.Add([IO.Path]::GetFullPath($to))
    }
    foreach ($name in $script:bridgeNames) { Require ($seen.Contains($name)) 'P9_BRIDGE_CENSUS' }
    $p8Study = Require-PinnedRow $script:p8Pins.files ([string]$script:p8Pins.roles.native.study) 'P9_P8_STUDY_PIN'
    $p8Inspector = Require-PinnedRow $script:p8Pins.files ([string]$script:p8Pins.roles.native.inspector) 'P9_P8_INSPECTOR_PIN'
    [void]$paths.Add([IO.Path]::GetFullPath([string]$p8Study.path)); [void]$paths.Add([IO.Path]::GetFullPath([string]$p8Inspector.path))
    foreach ($role in @('study','canary')) {
        $path = Require-ExistingFile ([string]$script:pins.roles.native.$role) 'P9_NATIVE_SOURCE_PATH'
        if ($role -ceq 'study') { [void](Require-PinnedRow $script:pins.files $path 'P9_NATIVE_STUDY_PIN') }
        else { [void](Require-PinnedRow $script:pins.files $path 'P9_NATIVE_CANARY_PIN') }
        [void]$paths.Add($path)
    }
    Require ($paths.Count -eq 1085) 'P9_COMPILER_SOURCE_CENSUS'
    $seenPaths = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
    foreach ($path in $paths) {
            Require ([IO.Path]::IsPathRooted($path) -and (Test-PathEqual $path ([IO.Path]::GetFullPath($path)))) 'P9_COMPILER_SOURCE_PATH'
        Require ($seenPaths.Add([IO.Path]::GetFullPath($path))) 'P9_COMPILER_SOURCE_DUPLICATE'
    }
    $array = [string[]]$paths.ToArray()
    [Array]::Sort($array,[StringComparer]::Ordinal)
    return ,$array
}
function Validate-OutputDestination {
    Require ([IO.Path]::IsPathRooted($OutputRoot)) 'EXTERNAL_OUTPUT_REQUIRED'
    $script:OutputRoot = [IO.Path]::GetFullPath($OutputRoot)
    Require (Test-PathEqual $script:OutputRoot $OutputRoot) 'EXTERNAL_OUTPUT_REQUIRED'
    Require (-not (Test-Path -LiteralPath $script:OutputRoot)) 'OUTPUT_EXISTS'
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    Require (Test-PathUnder $script:OutputRoot $tempRoot) 'EXTERNAL_OUTPUT_REQUIRED'
    foreach ($root in @($script:RepositoryRoot,$script:p9Root,[IO.Path]::GetDirectoryName($script:p8PinsPath),
        [IO.Path]::GetDirectoryName($script:p6PinsPath),[IO.Path]::GetDirectoryName($script:canaryPinsPath),$script:bridgeRoot)) {
        Require (-not (Test-PathUnder $script:OutputRoot ([string]$root)) -and -not (Test-PathUnder ([string]$root) $script:OutputRoot)) 'EXTERNAL_OUTPUT_REQUIRED'
    }
    $parent = [IO.Path]::GetDirectoryName($script:OutputRoot)
    [void](Require-ExistingDirectory $parent 'EXTERNAL_OUTPUT_REQUIRED')
    Assert-NoReparse $parent
}
function Create-OutputRoot {
    [void][IO.Directory]::CreateDirectory($script:OutputRoot)
    $script:outputCreated = $true
    $script:outputCanonical = Require-ExistingDirectory $script:OutputRoot 'OUTPUT_ROOT'
    $script:outputCreationTicks = [long](Get-Item -LiteralPath $script:outputCanonical -Force).CreationTimeUtc.Ticks
    Require ($script:outputCreationTicks -gt 0) 'OUTPUT_ROOT_IDENTITY'
    Write-HostLaunchIntent
    $dirs = [string[]]$script:allowedDirectories
    [Array]::Sort($dirs,[StringComparer]::Ordinal)
    foreach ($relative in $dirs) {
        $path = Get-OutputPath $relative
        [void][IO.Directory]::CreateDirectory($path)
        Assert-NoReparse $path
    }
}
function New-Scratch {
    $script:scratch = [IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetTempPath()) ('tsj-q100-reservation-provenance-r1-' + [Guid]::NewGuid().ToString('N'))))
    Assert-NoReparse ([IO.Path]::GetDirectoryName($script:scratch))
    Require (-not (Test-Path -LiteralPath $script:scratch)) 'SCRATCH_EXISTS'
    Require (-not (Test-PathUnder $script:scratch $script:OutputRoot) -and -not (Test-PathUnder $script:OutputRoot $script:scratch)) 'SCRATCH_OUTPUT_ALIAS'
    foreach ($root in @($script:RepositoryRoot,$script:p9Root,[IO.Path]::GetDirectoryName($script:InputPinsPath),
        [IO.Path]::GetDirectoryName($script:p8PinsPath),[IO.Path]::GetDirectoryName($script:p6PinsPath),
        [IO.Path]::GetDirectoryName($script:canaryPinsPath),$script:bridgeRoot)) {
        Require (-not (Test-PathUnder $script:scratch ([string]$root)) -and -not (Test-PathUnder ([string]$root) $script:scratch)) 'SCRATCH_INPUT_ALIAS'
    }
    Write-NewJson 'scratch-intent.json' ([ordered]@{schema='tsj-q100-reservation-provenance-scratch-v1';status='INTENT_NOT_CREATION_PROOF';path=$script:scratch;
        owner=[ordered]@{pid=[int]$PID;startTicks=[long]$script:hostStartTicks;executable=[string]$script:owner.Path;executableSha256=$script:selectedPowerShellSha256};suiteId=$script:suiteId})
    [void][IO.Directory]::CreateDirectory($script:scratch)
    $script:scratchCreated = $true
    $script:scratchTicks = [long](Get-Item -LiteralPath $script:scratch -Force).CreationTimeUtc.Ticks
    Require ($script:scratchTicks -gt 0) 'SCRATCH_IDENTITY'
    Assert-NoReparse $script:scratch
    $script:classes = Join-Path $script:scratch 'classes'
    $emptySourcePath = Join-Path $script:scratch 'empty-sourcepath'
    [void][IO.Directory]::CreateDirectory($script:classes); [void][IO.Directory]::CreateDirectory($emptySourcePath)
    $script:emptySourcePath = $emptySourcePath
}
function Write-CompilerIntent {
    $sourceBytes = [long](Get-Item -LiteralPath $script:argsFile).Length
    $script:compilerSourceListBytes = $sourceBytes
    $script:compilerSourceListSha256 = Get-Hash $script:argsFile
    $intent = [ordered]@{schema='tsj-q100-reservation-provenance-launch-v1';role='compiler';
        owner=[ordered]@{pid=[int]$PID;startTicks=[long]$script:hostStartTicks;executable=[string]$script:owner.Path;executableSha256=$script:selectedPowerShellSha256};
        executable=$script:javac;executableSha256=$script:selectedJavacSha256;arguments=@($script:compilerArgs);limitMs=60000;
        inputSha256=$script:InputPinsSha256;sourceSha256=$script:sourceInventorySha256;outputRoot=$script:outputCanonical;
        sourceCount=1085;sourceListBytes=$sourceBytes;sourceListSha256=$script:compilerSourceListSha256}
    Write-NewJson 'compiler-launch-intent.json' $intent
    $script:activeLaunchIntent = $intent
}
function Run-Compiler {
    $script:phase = 'COMPILE'
    $script:sourcePaths = New-CompilerSources
    $argLines = @($script:sourcePaths | ForEach-Object { '"' + ([string]$_).Replace('\','/') + '"' })
    $script:argsFile = Join-Path $script:scratch 'compiler-sources.txt'
    [IO.File]::WriteAllLines($script:argsFile,[string[]]$argLines,$script:utf8)
    $bytes = [long](Get-Item -LiteralPath $script:argsFile).Length
    Require ($bytes -le 1048576) 'COMPILER_LIST_LIMIT'
    $relative = 'compiler-sources.txt'
    $copy = [IO.File]::ReadAllBytes($script:argsFile)
    Write-NewBytes (Get-OutputPath $relative) $copy $relative
    Require (Test-ByteEquality $copy ([IO.File]::ReadAllBytes((Get-OutputPath $relative)))) 'COMPILER_LIST_COPY'
    $userRow = Require-SourceRow $script:canaryPins.sourceRows '.tools/gwt-2.7.0/gwt-user-2.7.0.jar' 'BASE_SOURCE' 'P9_GWT_PIN'
    $devRow = Require-SourceRow $script:canaryPins.sourceRows '.tools/gwt-2.7.0/gwt-dev-2.7.0.jar' 'BASE_SOURCE' 'P9_GWT_PIN'
    $script:gwtUser = Verify-PinnedSourceRow $userRow ([IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot ([string]$userRow.path)))) 'P9_GWT_PIN'
    $script:gwtDev = Verify-PinnedSourceRow $devRow ([IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot ([string]$devRow.path)))) 'P9_GWT_PIN'
    $script:classpath = [string]::Join(';',@($script:classes,$script:gwtUser,$script:gwtDev))
    $script:compilerArgs = @('-source','7','-target','7','-encoding','UTF-8','-classpath',$script:classpath,'-sourcepath',$script:emptySourcePath,'-d',$script:classes,('@'+$script:argsFile))
    Write-NewJson 'bridge-copies.json' $script:bridgeCopies
    Write-CompilerIntent
    $script:compilerInvoked = $true
    $call = Invoke-BoundedChild $script:javac $script:compilerArgs 60000 'COMPILE'
    $p = $call.process
    $evidence = Save-Process 'compiler-process.json' 'compiler.stdout' 'compiler.stderr' $p $script:javac $script:compilerArgs
    Add-ProcessRecord 'compiler' $evidence $call.wallMs
    Require ($p.ExitCode -eq 0 -and $call.wallMs -le 60000) 'COMPILE_FAILED'
    Require ((Get-Hash $script:argsFile) -ceq $script:compilerSourceListSha256 -and (Get-Hash (Get-OutputPath 'compiler-sources.txt')) -ceq $script:compilerSourceListSha256) 'COMPILER_LIST_CHANGED'
    Verify-BridgeCopies
}
function Remove-OwnedScratch {
    if (-not $script:scratchCreated -or $script:scratchRemoved) { return }
    Require (Test-Path -LiteralPath $script:scratch -PathType Container) 'SCRATCH_MISSING'
    Assert-NoReparse $script:scratch
    Require ([long](Get-Item -LiteralPath $script:scratch -Force).CreationTimeUtc.Ticks -eq [long]$script:scratchTicks) 'SCRATCH_IDENTITY_CHANGED'
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    [void](Remove-VerifierOwnedTree $tempRoot $script:scratch)
    Require (-not (Test-Path -LiteralPath $script:scratch)) 'SCRATCH_CLEANUP_UNPROVEN'
    $script:scratchRemoved = $true
}
function Get-OrdinalSortedStrings([string[]]$Values) {
    $copy = [string[]]@($Values)
    [Array]::Sort($copy,[StringComparer]::Ordinal)
    return ,$copy
}
function Get-TreePaths {
    $files = New-Object 'System.Collections.Generic.List[string]'
    $dirs = New-Object 'System.Collections.Generic.List[string]'
    $pending = New-Object 'System.Collections.Generic.Stack[string]'
    $pending.Push($script:outputCanonical)
    while ($pending.Count -gt 0) {
        $current = $pending.Pop()
        foreach ($entry in [IO.Directory]::GetFileSystemEntries($current)) {
            $item = Get-Item -LiteralPath $entry -Force
            Require (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'OUTPUT_REPARSE'
            $relative = $entry.Substring($script:outputCanonical.Length).TrimStart('\','/').Replace('\','/')
            if ($item.PSIsContainer) { [void]$dirs.Add($relative); $pending.Push($entry) }
            else { [void]$files.Add($relative) }
        }
    }
    return [pscustomobject]@{files=$files.ToArray();directories=$dirs.ToArray()}
}
function Assert-TreeEquals([string[]]$ExpectedFiles, [string[]]$ExpectedDirectories, [string]$Code) {
    $tree = Get-TreePaths
    $actualFiles = Get-OrdinalSortedStrings $tree.files; $expectedFilesSorted = Get-OrdinalSortedStrings $ExpectedFiles
    $actualDirs = Get-OrdinalSortedStrings $tree.directories; $expectedDirsSorted = Get-OrdinalSortedStrings $ExpectedDirectories
    Require ($actualFiles.Count -eq $expectedFilesSorted.Count -and $actualDirs.Count -eq $expectedDirsSorted.Count) $Code
    for ($i=0;$i -lt $actualFiles.Count;$i++) { Require ([string]::Equals($actualFiles[$i],$expectedFilesSorted[$i],[StringComparison]::Ordinal)) $Code }
    for ($i=0;$i -lt $actualDirs.Count;$i++) { Require ([string]::Equals($actualDirs[$i],$expectedDirsSorted[$i],[StringComparison]::Ordinal)) $Code }
    return $tree
}
function Get-JsonLines([string]$Path, [string]$Code) {
    $bytes = [IO.File]::ReadAllBytes($Path)
    Require ($bytes.Length -le 8388608 -and $bytes.Length -gt 0) $Code
    Require (-not ($bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191)) $Code
    try { $text = $script:strictUtf8.GetString($bytes) } catch { throw $Code }
    Require ($text.EndsWith("`n") -and -not $text.Contains("`r")) $Code
    $lines = $text.Split([char]10)
    Require ($lines[$lines.Count-1] -ceq '') $Code
    $rows = @()
    for ($i=0;$i -lt $lines.Count-1;$i++) {
        Require ($lines[$i].Length -gt 0 -and $script:strictUtf8.GetByteCount($lines[$i]+"`n") -le 65536) $Code
        Assert-UniqueJsonKeys $lines[$i] $Code
        try { $rows += @(ConvertFrom-Json -InputObject $lines[$i] -ErrorAction Stop) } catch { throw $Code }
    }
    return ,$rows
}
function Get-GroupRecordCount([string]$Path) {
    $rows = Get-JsonLines $Path 'P9_OPERATION_JSONL'
    $count = 0
    foreach ($row in $rows) { if ([string]$row.record -ceq 'GROUP') { $count++ } }
    Require ($count -le 640) 'P9_GROUP_LIMIT'
    return [long]$count
}
function Require-ExactDataFiles([string]$Directory, [string[]]$Expected, [string]$Code) {
    $actualFiles = @([IO.Directory]::GetFiles($Directory,'*',[IO.SearchOption]::TopDirectoryOnly) | ForEach-Object { [IO.Path]::GetFileName($_) })
    $actualDirs = [IO.Directory]::GetDirectories($Directory,'*',[IO.SearchOption]::TopDirectoryOnly)
    Require ($actualDirs.Count -eq 0 -and $actualFiles.Count -eq $Expected.Count) $Code
    foreach ($name in $Expected) { Require (@($actualFiles | Where-Object { [string]::Equals($_,$name,[StringComparison]::Ordinal) }).Count -eq 1) $Code }
}
function Get-ArtifactRoleAndOperation([string]$Name) {
    if ($Name -ceq 'controls/P9_NONEMPTY_DATA/data/sentinel.txt') { return [pscustomobject]@{role='CONTROL_PAYLOAD';operation='HOST'} }
    if ($Name -ceq 'controls/P9_NONEMPTY_P9/p9/sentinel.txt') { return [pscustomobject]@{role='CONTROL_PAYLOAD';operation='HOST'} }
    if ($Name -match '^runs/(NULL|OBSERVE|TAP_THROW|CALLBACK_THROW|REASON_THROW)\.(stdout|stderr)$') {
        $op=$Matches[1]; if ($Matches[2] -ceq 'stdout') { return [pscustomobject]@{role='STDOUT';operation=$op} }; return [pscustomobject]@{role='STDERR';operation=$op}
    }
    if ($Name.StartsWith('runs/',[StringComparison]::Ordinal)) {
        $parts=$Name.Split('/'); Require ($parts.Count -ge 3 -and $parts[0] -ceq 'runs') 'OUTPUT_ROLE'
        $op=$parts[1]; Require ($op -cin @('NULL','OBSERVE','TAP_THROW','CALLBACK_THROW','REASON_THROW')) 'OUTPUT_ROLE'
        if ($parts.Count -eq 3 -and $parts[2] -ceq 'launch-intent.json') { return [pscustomobject]@{role='HOST_INTENT';operation=$op} }
        if ($parts.Count -eq 3 -and $parts[2] -ceq 'process.json') { return [pscustomobject]@{role='PROCESS_RECEIPT';operation=$op} }
        if ($parts.Count -eq 4 -and $parts[2] -ceq 'data') {
            if ($parts[3] -cin @('canonical-stage-index.json','canonical-geometric-prefix-index.json','reservation-index.json')) { return [pscustomobject]@{role='NATIVE_INDEX';operation=$op} }
            if ($parts[3] -cin @('op-1.jsonl','fixed-prefix.jsonl','prefix-blockers.json','prefix-stages.json','prefix-geometric-reasons.jsonl','reservation-operands.jsonl')) { return [pscustomobject]@{role='NATIVE_PAYLOAD';operation=$op} }
        }
        if ($parts.Count -eq 4 -and $parts[2] -ceq 'p9' -and $parts[3] -ceq 'reservation-operands.jsonl') { return [pscustomobject]@{role='NATIVE_PAYLOAD';operation=$op} }
        if ($parts.Count -eq 4 -and $parts[2] -ceq 'p9' -and $parts[3] -ceq 'reservation-index.json') { return [pscustomobject]@{role='NATIVE_INDEX';operation=$op} }
        throw 'OUTPUT_ROLE'
    }
    if ($Name.StartsWith('source-',[StringComparison]::Ordinal)) {
        if ($Name.EndsWith('-process.json',[StringComparison]::Ordinal)) { return [pscustomobject]@{role='PROCESS_RECEIPT';operation='HOST'} }
        if ($Name.EndsWith('.stdout',[StringComparison]::Ordinal)) { return [pscustomobject]@{role='STDOUT';operation='HOST'} }
        if ($Name.EndsWith('.stderr',[StringComparison]::Ordinal)) { return [pscustomobject]@{role='STDERR';operation='HOST'} }
    }
    foreach ($stem in @('p6-canary','p8-canary','p9-canary','p9-bad-mode','p9-bad-uuid','p9-bad-input-sha','p9-nonempty-data','p9-nonempty-p9')) {
        if ($Name -ceq ($stem+'-launch-intent.json')) { return [pscustomobject]@{role='HOST_INTENT';operation='HOST'} }
        if ($Name -ceq ($stem+'-process.json')) { return [pscustomobject]@{role='PROCESS_RECEIPT';operation='HOST'} }
        if ($Name -ceq ($stem+'.stdout')) { return [pscustomobject]@{role='STDOUT';operation='HOST'} }
        if ($Name -ceq ($stem+'.stderr')) { return [pscustomobject]@{role='STDERR';operation='HOST'} }
    }
    if ($Name -in @('host-launch-intent.json','scratch-intent.json','compiler-launch-intent.json')) { return [pscustomobject]@{role='HOST_INTENT';operation='HOST'} }
    if ($Name -ceq 'compiler-process.json') { return [pscustomobject]@{role='PROCESS_RECEIPT';operation='HOST'} }
    if ($Name -ceq 'compiler.stdout') { return [pscustomobject]@{role='STDOUT';operation='HOST'} }
    if ($Name -ceq 'compiler.stderr') { return [pscustomobject]@{role='STDERR';operation='HOST'} }
    if ($Name -ceq 'compiler-sources.txt') { return [pscustomobject]@{role='COMPILER_INPUT';operation='HOST'} }
    if ($Name -ceq 'bridge-copies.json') { return [pscustomobject]@{role='BRIDGE_COPIES';operation='HOST'} }
    throw 'OUTPUT_ROLE'
}
function Get-OperationBytes([string]$Operation) {
    [long]$total=0
    foreach ($name in $script:outputLeaves) {
        if ($name -cin @('host-result.json','suite-index.json','suite-footer.json')) { continue }
        $identity=Get-ArtifactRoleAndOperation $name
        if ([string]$identity.operation -ceq $Operation) {
            $path=Get-OutputPath $name
            if (Test-Path -LiteralPath $path -PathType Leaf) { $total += [long](Get-Item -LiteralPath $path -Force).Length }
        }
    }
    Require ($total -le 8388608) 'OPERATION_OUTPUT_LIMIT'
    return $total
}
function Get-OutputCounterWithNativeFiles {
    if ($script:Mode -ceq 'CANARY') { return }
    [long]$nativeBytes=0; $nativeCount=0
    foreach ($name in $script:outputLeaves) {
        if ($name -cin @('host-result.json','suite-index.json','suite-footer.json')) { continue }
        $identity=Get-ArtifactRoleAndOperation $name
        if ([string]$identity.role -cin @('NATIVE_PAYLOAD','NATIVE_INDEX')) {
            $path=Require-ExistingFile (Get-OutputPath $name) 'OUTPUT_CENSUS'
            Require (Test-PathUnder $path $script:outputCanonical) 'OUTPUT_CENSUS'
            $info=Get-Item -LiteralPath $path -Force
            Require (($info.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0 -and -not $info.PSIsContainer) 'OUTPUT_REPARSE'
            $nativeBytes += [long]$info.Length; $nativeCount++
        }
    }
    Require ($nativeCount -eq 32) 'NATIVE_OUTPUT_CENSUS'
    Require ([decimal]$script:writtenOutputBytes+[decimal]$nativeBytes -le 41943040) 'SUITE_OUTPUT_LIMIT'
    $script:writtenOutputBytes = [long]$script:writtenOutputBytes + $nativeBytes
}
function Invoke-NativeChild([string[]]$Arguments, [string]$CallPhase) {
    $call = Invoke-BoundedChild $script:java $Arguments 480000 $CallPhase
    Require ($call.wallMs -ge 0 -and $call.wallMs -le 480000) ($CallPhase+'_WALL_LIMIT')
    $script:nativePhaseChildWallSumMs += [double]$call.wallMs
    return $call
}
function Save-NativeRole([string]$ProcessRole, [string]$Stem, $Call, [string[]]$Arguments) {
    $receipt = Save-Process ($Stem+'-process.json') ($Stem+'.stdout') ($Stem+'.stderr') $Call.process $script:java $Arguments
    Add-ProcessRecord $ProcessRole $receipt ([double]$Call.wallMs)
    return $Call.process
}
function Parse-P6Canary([string]$Stdout) {
    $m=Parse-OneMarker $Stdout 'CANONICAL_PREFIX_STAGE_CANARY_COMPLETE' 'P6_CANARY_MARKER' -AllowPreamble
    Require-ExactKeys $m @('schema','status','assertions','blockerAssertions','stageAssertions','elapsedNanos','groups','maximumGroupNanos','layoutQualification') 'P6_CANARY_SCHEMA'
    Require ([string]$m.schema -ceq 'tsj-canonical-prefix-stage-canary-v1' -and [string]$m.status -ceq 'PASS') 'P6_CANARY_STATUS'
    Require-Integer $m.assertions 1 ([long]::MaxValue) 'P6_CANARY_ASSERTIONS'; Require-Integer $m.blockerAssertions 1 ([long]::MaxValue) 'P6_CANARY_ASSERTIONS'
    Require-Integer $m.stageAssertions 1 ([long]::MaxValue) 'P6_CANARY_ASSERTIONS'; Require-Integer $m.elapsedNanos 0 90000000000 'P6_CANARY_WORK'
    Require-Integer $m.groups 1 640 'P6_CANARY_GROUPS'; Require-Integer $m.maximumGroupNanos 0 5000000000 'P6_CANARY_GROUP'
    Require ($m.layoutQualification -is [bool] -and -not $m.layoutQualification) 'P6_CANARY_SCOPE'
    return $m
}
function Parse-P8Canary([string]$Stdout) {
    Require (-not $Stdout.Contains("`r") -and $Stdout.EndsWith("`n") -and $Stdout.IndexOf("`n",[StringComparison]::Ordinal) -eq $Stdout.Length-1) 'P8_CANARY_LINE_ENDING'
    $m=Parse-OneMarker $Stdout 'P8_GEOMETRIC_REASON_CANARY_COMPLETE' 'P8_CANARY_MARKER'
    Require-ExactKeys $m @('schema','status','assertions','caseCount','workNanos','groups','maximumGroupNanos','totalObservationUnits','cases','layoutQualification') 'P8_CANARY_SCHEMA'
    Require ([string]$m.schema -ceq 'tsj-q100-geometric-reason-canary-v1') 'P8_CANARY_SCHEMA'
    Require ([string]$m.status -ceq 'PASS') 'P8_CANARY_STATUS'
    Require-Integer $m.assertions 1 ([long]::MaxValue) 'P8_CANARY_ASSERTIONS'; Require-Integer $m.caseCount 1 ([int]::MaxValue) 'P8_CANARY_CASES'
    Require-Integer $m.workNanos 0 90000000000 'P8_CANARY_WORK'; Require-Integer $m.groups 0 640 'P8_CANARY_GROUPS'
    Require-Integer $m.maximumGroupNanos 0 5000000000 'P8_CANARY_GROUPS'; Require-Integer $m.totalObservationUnits 0 32768 'P8_CANARY_UNITS'
    Require ($m.cases -is [array] -and $m.cases.Count -eq [int]$m.caseCount -and $m.layoutQualification -is [bool] -and -not $m.layoutQualification) 'P8_CANARY_CASES'
    return $m
}
function Parse-P9Canary([string]$Stdout) {
    Require (-not $Stdout.Contains("`r") -and $Stdout.EndsWith("`n") -and $Stdout.IndexOf("`n",[StringComparison]::Ordinal) -eq $Stdout.Length-1) 'P9_CANARY_LINE_ENDING'
    $m=Parse-OneMarker $Stdout 'P9_RESERVATION_CANARY_COMPLETE' 'P9_CANARY_MARKER'
    Require-ExactKeys $m @('schema','status','passed','cases','layoutQualification') 'P9_CANARY_SCHEMA'
    $expected=@('NULL_UNARMED','FORWARD_ONCE','TARGET_ONE_SHOT','DETACH_DROP','CONTAIN_SENTINEL','UNEXPECTED_PROPAGATES','UNIT_LIMIT','INCLUSIVE_OUTPUT_LIMIT')
    Require ([string]$m.schema -ceq 'tsj-q100-reservation-canary-v1' -and [string]$m.status -ceq 'PASS_LITERAL_NATIVE_ADAPTER_ONLY' -and
        $m.layoutQualification -is [bool] -and -not $m.layoutQualification) 'P9_CANARY_STATUS'
    Require-Integer $m.passed 8 8 'P9_CANARY_COUNT'
    Require ($m.cases -is [array] -and $m.cases.Count -eq 8) 'P9_CANARY_CASES'
    for ($i=0;$i -lt 8;$i++) { Require ([string]$m.cases[$i] -ceq $expected[$i]) 'P9_CANARY_CASES' }
    return $m
}
function Get-ControlSnapshot([string]$Directory) {
    $dir=Require-ExistingDirectory $Directory 'P9_CONTROL_DIRECTORY'
    $rows=@(); $dirs=[IO.Directory]::GetDirectories($dir,'*',[IO.SearchOption]::TopDirectoryOnly)
    foreach ($file in [IO.Directory]::GetFiles($dir,'*',[IO.SearchOption]::TopDirectoryOnly)) {
        $item=Get-Item -LiteralPath $file -Force; Require (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'P9_CONTROL_DIRECTORY'
        $rows += @([ordered]@{name=[IO.Path]::GetFileName($file);bytes=[long]$item.Length;sha256=Get-Hash $file})
    }
    return [pscustomobject]@{directory=$dir;directories=@($dirs);files=@($rows)}
}
function Verify-ControlSnapshot($Before) {
    $after=Get-ControlSnapshot ([string]$Before.directory)
    Require ($after.directories.Count -eq $Before.directories.Count -and $after.files.Count -eq $Before.files.Count) 'P9_CONTROL_CHANGED'
    for ($i=0;$i -lt $Before.files.Count;$i++) {
        $left=$Before.files[$i]; $right=$after.files[$i]
        Require ([string]$left.name -ceq [string]$right.name -and [long]$left.bytes -eq [long]$right.bytes -and [string]$left.sha256 -ceq [string]$right.sha256) 'P9_CONTROL_CHANGED'
    }
}
function Run-NativeCanariesAndControls {
    $p6Main='com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy'
    $p8Main='com.lushprojects.circuitjs1.client.CanonicalGeometricReasonCanary'
    $p9CanaryMain='com.lushprojects.circuitjs1.client.CanonicalReservationProvenanceCanary'
    $p9Main='com.lushprojects.circuitjs1.client.CanonicalReservationProvenanceStudy'
    $p6Args=@('-ea','-cp',$script:classpath,$p6Main,'--canary')
    $p8Args=@('-ea','-cp',$script:classpath,$p8Main)
    $p9Args=@('-ea','-cp',$script:classpath,$p9CanaryMain)
    $script:nativePhaseStartOffsetMs=[double]$script:hostClock.Elapsed.TotalMilliseconds
    $script:nativeInvoked=$true
    $script:phase='P6_CANARY'; Write-LaunchIntent 'p6-canary-launch-intent.json' 'P6_CANARY' $script:java $script:selectedJavaSha256 $p6Args 480000 | Out-Null
    $p6=Invoke-NativeChild $p6Args 'P6_CANARY'; $p=$p6.process
    $null=Save-NativeRole 'p6Canary' 'p6-canary' $p6 $p6Args
    Require ($p.ExitCode -eq 0 -and [string]::IsNullOrEmpty([string]$p.Stderr)) 'P6_CANARY_PROCESS'
    $script:canaries.p6=Parse-P6Canary ([string]$p.Stdout); $script:returnedChildEvidence=$null; Verify-BridgeCopies

    $script:phase='P8_CANARY'; Write-LaunchIntent 'p8-canary-launch-intent.json' 'P8_CANARY' $script:java $script:selectedJavaSha256 $p8Args 480000 | Out-Null
    $p8=Invoke-NativeChild $p8Args 'P8_CANARY'; $p=$p8.process
    $null=Save-NativeRole 'p8Canary' 'p8-canary' $p8 $p8Args
    Require ($p.ExitCode -eq 0 -and [string]::IsNullOrEmpty([string]$p.Stderr)) 'P8_CANARY_PROCESS'
    $script:canaries.p8=Parse-P8Canary ([string]$p.Stdout); $script:returnedChildEvidence=$null; Verify-BridgeCopies

    $script:phase='P9_CANARY'; Write-LaunchIntent 'p9-canary-launch-intent.json' 'P9_CANARY' $script:java $script:selectedJavaSha256 $p9Args 480000 | Out-Null
    $p9=Invoke-NativeChild $p9Args 'P9_CANARY'; $p=$p9.process
    $null=Save-NativeRole 'p9Canary' 'p9-canary' $p9 $p9Args
    Require ($p.ExitCode -eq 0 -and [string]::IsNullOrEmpty([string]$p.Stderr)) 'P9_CANARY_PROCESS'
    $script:canaries.p9=Parse-P9Canary ([string]$p.Stdout); $script:returnedChildEvidence=$null; Verify-BridgeCopies

    $controlRun='00000000-0000-4000-8000-000000000009'
    $cases=@(
        [pscustomobject]@{name='P9_BAD_MODE';role='badMode';stem='p9-bad-mode';expected='INVALID_MODE';arg=0;value='INVALID';dir='controls/P9_BAD_MODE'},
        [pscustomobject]@{name='P9_BAD_UUID';role='badUuid';stem='p9-bad-uuid';expected='INVALID_RUN_UUID';arg=1;value='not-a-v4-uuid';dir='controls/P9_BAD_UUID'},
        [pscustomobject]@{name='P9_BAD_INPUT_SHA';role='badInputSha';stem='p9-bad-input-sha';expected='INVALID_INPUT_SHA256';arg=2;value='not-a-sha256';dir='controls/P9_BAD_INPUT_SHA'},
        [pscustomobject]@{name='P9_NONEMPTY_DATA';role='nonemptyData';stem='p9-nonempty-data';expected='OUTPUT_DIRECTORY_NOT_EMPTY';arg=5;value=$null;dir='controls/P9_NONEMPTY_DATA'},
        [pscustomobject]@{name='P9_NONEMPTY_P9';role='nonemptyP9';stem='p9-nonempty-p9';expected='OUTPUT_DIRECTORY_NOT_EMPTY';arg=6;value=$null;dir='controls/P9_NONEMPTY_P9'}
    )
    foreach ($case in $cases) {
        $data=Get-OutputPath ($case.dir+'/data'); $p9dir=Get-OutputPath ($case.dir+'/p9')
        $dataSentinel=Get-OutputPath 'controls/P9_NONEMPTY_DATA/data/sentinel.txt'
        $p9Sentinel=Get-OutputPath 'controls/P9_NONEMPTY_P9/p9/sentinel.txt'
        if ($case.name -ceq 'P9_NONEMPTY_DATA') { Write-NewBytes $dataSentinel $script:utf8.GetBytes("P9_PREEXISTING_DATA_DO_NOT_CHANGE`n") 'controls/P9_NONEMPTY_DATA/data/sentinel.txt' }
        if ($case.name -ceq 'P9_NONEMPTY_P9') { Write-NewBytes $p9Sentinel $script:utf8.GetBytes("P9_PREEXISTING_DATA_DO_NOT_CHANGE`n") 'controls/P9_NONEMPTY_P9/p9/sentinel.txt' }
        $beforeData=Get-ControlSnapshot $data; $beforeP9=Get-ControlSnapshot $p9dir
        $app=@('NULL',$controlRun,$script:InputPinsSha256,$script:wireSha256,$script:sourceInventorySha256,$data,$p9dir)
        if ($null -ne $case.value) { $app[[int]$case.arg]=[string]$case.value }
        $args=@('-ea','-cp',$script:classpath,$p9Main)+$app
        $script:phase=$case.name; Write-LaunchIntent ($case.stem+'-launch-intent.json') $case.name $script:java $script:selectedJavaSha256 $args 480000 | Out-Null
        $call=Invoke-NativeChild $args $case.name; $p=$call.process
        $null=Save-NativeRole ([string]$case.role) ([string]$case.stem) $call $args
        Require ($p.ExitCode -eq 1 -and [string]::IsNullOrEmpty([string]$p.Stdout)) ($case.name+'_EXPECTED_FAILURE')
        $marker=Parse-OneMarker ([string]$p.Stderr) 'P9_RESERVATION_PROVENANCE_PREFLIGHT_REJECT' ($case.name+'_MARKER')
        Require-ExactKeys $marker @('schema','phase','code','argIndex') ($case.name+'_SCHEMA')
        Require ([string]$marker.schema -ceq 'tsj-q100-reservation-provenance-preflight-v1' -and [string]$marker.phase -ceq 'PREFLIGHT' -and
            [string]$marker.code -ceq [string]$case.expected) ($case.name+'_CODE')
        Require-Integer $marker.argIndex ([int]$case.arg) ([int]$case.arg) ($case.name+'_ARG')
        Verify-ControlSnapshot $beforeData; Verify-ControlSnapshot $beforeP9
        $script:canaries.preflight += @([ordered]@{name=[string]$case.name;expectedFailureCode=[string]$case.expected;observedFailureCode=[string]$marker.code;
            argIndex=[int]$marker.argIndex;exitCode=1;terminationProven=$true;dataDirectoryUnchanged=$true;p9DirectoryUnchanged=$true})
        $script:returnedChildEvidence=$null; Verify-BridgeCopies
    }
    Require ($script:canaries.preflight.Count -eq 5) 'P9_PREFLIGHT_CENSUS'
}
function Read-NativeJsonFile([string]$Path, [string]$Code) {
    $bytes=[IO.File]::ReadAllBytes($Path)
    Require ($bytes.Length -le 65536 -and $bytes.Length -gt 0) $Code
    foreach ($b in $bytes) { Require ($b -lt 128) $Code }
    Require (-not ($bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191)) $Code
    $text=$script:strictUtf8.GetString($bytes)
    Require ($text.EndsWith("`n") -and -not $text.Contains("`r") -and -not $text.Substring(0,$text.Length-1).EndsWith("`n")) $Code
    Assert-UniqueJsonKeys $text $Code
    try { return (ConvertFrom-Json -InputObject $text.Substring(0,$text.Length-1) -ErrorAction Stop) } catch { throw $Code }
}
function Verify-FileReference($Reference, [string]$ExpectedPath, [string]$FilePath, [string]$Code) {
    Require-ExactKeys $Reference @('path','bytes','sha256') $Code
    Require ([string]$Reference.path -ceq $ExpectedPath -and [string]$Reference.sha256 -cmatch '^[0-9a-f]{64}$') $Code
    Require-Integer $Reference.bytes 1 8388608 $Code
    $full=Require-ExistingFile $FilePath $Code; $info=Get-Item -LiteralPath $full -Force
    Require ($info.Length -eq [long]$Reference.bytes -and (Get-Hash $full) -ceq [string]$Reference.sha256) $Code
}
function Get-DirectFileBytes([string]$Directory) {
    [long]$sum=0
    foreach ($path in [IO.Directory]::GetFiles($Directory,'*',[IO.SearchOption]::TopDirectoryOnly)) { $item=Get-Item -LiteralPath $path -Force; Require (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'NATIVE_REPARSE'; $sum += [long]$item.Length }
    return $sum
}
function Verify-P8Normal([string]$ModeValue, [string]$RunIdValue, [string]$DataPath, $Process, [double]$WallMs) {
    Require ($Process.ExitCode -eq 0 -and [string]::IsNullOrEmpty([string]$Process.Stderr)) ($ModeValue+'_P8_PROCESS')
    $stdout=[string]$Process.Stdout
    Require (-not $stdout.Contains("`r") -and $stdout.EndsWith("`n") -and $stdout.IndexOf("`n",[StringComparison]::Ordinal) -eq $stdout.Length-1) ($ModeValue+'_P8_MARKER_LINE')
    $marker=Parse-OneMarker $stdout 'CANONICAL_GEOMETRIC_PREFIX_COMPLETE' ($ModeValue+'_P8_MARKER')
    Require-ExactKeys $marker @('schema','mode','runId','inputSha256','sourceSha256','wireSha256','index','aggregateBytes','workNanos') ($ModeValue+'_P8_MARKER_SCHEMA')
    Require ([string]$marker.schema -ceq 'tsj-canonical-geometric-prefix-complete-v1' -and [string]$marker.mode -ceq $ModeValue -and
        [string]$marker.runId -ceq $RunIdValue -and [string]$marker.inputSha256 -ceq $script:InputPinsSha256 -and
        [string]$marker.sourceSha256 -ceq $script:sourceInventorySha256 -and [string]$marker.wireSha256 -ceq $script:wireSha256) ($ModeValue+'_P8_BINDING')
    Require-ExactKeys $marker.index @('path','bytes','sha256') ($ModeValue+'_P8_INDEX_REF')
    Require ([string]$marker.index.path -ceq 'canonical-geometric-prefix-index.json' -and [string]$marker.index.sha256 -cmatch '^[0-9a-f]{64}$') ($ModeValue+'_P8_INDEX_REF')
    Require-Integer $marker.index.bytes 1 65536 ($ModeValue+'_P8_INDEX_REF'); Require-Integer $marker.aggregateBytes 1 8388608 ($ModeValue+'_P8_AGGREGATE')
    Require-Integer $marker.workNanos 0 90000000000 ($ModeValue+'_P8_WORK'); Require (([double]$marker.workNanos/1000000.0) -le $WallMs) ($ModeValue+'_P8_WORK_WALL')
    $names=@('op-1.jsonl','fixed-prefix.jsonl','prefix-blockers.json','prefix-stages.json','canonical-stage-index.json','prefix-geometric-reasons.jsonl','canonical-geometric-prefix-index.json')
    Require-ExactDataFiles $DataPath $names ($ModeValue+'_P8_DATA_CENSUS')
    $indexPath=Join-Path $DataPath 'canonical-geometric-prefix-index.json'
    Verify-FileReference $marker.index 'canonical-geometric-prefix-index.json' $indexPath ($ModeValue+'_P8_INDEX_HASH')
    $index=Read-NativeJsonFile $indexPath ($ModeValue+'_P8_INDEX_JSON')
    Require ([string]$index.schema -ceq 'tsj-canonical-geometric-prefix-index-v1' -and [string]$index.mode -ceq $ModeValue -and
        [string]$index.runId -ceq $RunIdValue -and [string]$index.inputSha256 -ceq $script:InputPinsSha256 -and
        [string]$index.sourceSha256 -ceq $script:sourceInventorySha256 -and [string]$index.wireSha256 -ceq $script:wireSha256) ($ModeValue+'_P8_INDEX_BINDING')
    $sidecar=Get-JsonLines (Join-Path $DataPath 'prefix-geometric-reasons.jsonl') ($ModeValue+'_P8_SIDECAR')
    Require ($sidecar.Count -eq 16) ($ModeValue+'_P8_SIDECAR_ROWS')
    $footer=$sidecar[$sidecar.Count-1]
    Require ([string]$footer.kind -ceq 'footer' -and [string]$footer.mode -ceq $ModeValue -and [string]$footer.runId -ceq $RunIdValue -and
        [string]$footer.inputSha256 -ceq $script:InputPinsSha256 -and [string]$footer.sourceSha256 -ceq $script:sourceInventorySha256) ($ModeValue+'_P8_FOOTER')
    Require-Integer $footer.totalObservationUnits 0 32768 ($ModeValue+'_P8_UNITS')
    Require ($footer.cleanupComplete -is [bool] -and $footer.cleanupComplete -and $footer.complete -is [bool] -and $footer.complete) ($ModeValue+'_P8_CLEANUP')
    $dataBytes=Get-DirectFileBytes $DataPath
    Require ($dataBytes -eq [long]$marker.aggregateBytes -and $dataBytes -le 8388608) ($ModeValue+'_P8_DATA_BYTES')
    $groups=Get-GroupRecordCount (Join-Path $DataPath 'op-1.jsonl')
    return [pscustomobject]@{marker=$marker;index=$index;footer=$footer;units=[long]$footer.totalObservationUnits;groups=[long]$groups;dataBytes=[long]$dataBytes}
}
function Verify-P9Index([string]$WrapperMode, [string]$P8Mode, [string]$RunIdValue, [string]$DataPath, [string]$P9Path, $Process, $P8, [double]$WallMs) {
    $indexPath=Join-Path $P9Path 'reservation-index.json'; $sidecarPath=Join-Path $P9Path 'reservation-operands.jsonl'
    $index=Read-NativeJsonFile $indexPath ($WrapperMode+'_P9_INDEX_JSON')
    Require-ExactKeys $index @('schema','wrapperMode','p8Mode','runId','inputSha256','sourceSha256','p8Index','sidecar','capture','work','bytes','cleanup','status','layoutQualification') ($WrapperMode+'_P9_INDEX_SCHEMA')
    Require ([string]$index.schema -ceq 'tsj-q100-reservation-provenance-index-v1' -and [string]$index.wrapperMode -ceq $WrapperMode -and
        [string]$index.p8Mode -ceq $P8Mode -and [string]$index.runId -ceq $RunIdValue -and [string]$index.inputSha256 -ceq $script:InputPinsSha256 -and
        [string]$index.sourceSha256 -ceq $script:sourceInventorySha256 -and [string]$index.status -ceq 'PROVISIONAL_NATIVE_RESULT' -and
        $index.layoutQualification -is [bool] -and -not $index.layoutQualification) ($WrapperMode+'_P9_INDEX_BINDING')
    Verify-FileReference $index.p8Index 'canonical-geometric-prefix-index.json' (Join-Path $DataPath 'canonical-geometric-prefix-index.json') ($WrapperMode+'_P9_P8_INDEX_REF')
    Verify-FileReference $index.sidecar 'reservation-operands.jsonl' $sidecarPath ($WrapperMode+'_P9_SIDECAR_REF')
    $captureKeys=@('state','registrationArmed','fineAttachments','decoratorsDetached','openDecorators','targetAttachmentOrdinal','targetParentOrdinal','targetEvents','padRowsBuilt','padRowsRetained','sentinelObserved','forwardedFineEvents','targetForwardedEvents')
    Require-ExactKeys $index.capture $captureKeys ($WrapperMode+'_P9_CAPTURE')
    $workKeys=@('delegateStartOffsetNanos','delegateReturnOffsetNanos','p8ReportedWorkNanos','delegateCleanupNanos','p9DelegateCleanupNanos','outsideWorkSegments','p9CleanupNanos','creditCutoffOffsetNanos','creditedWorkNanos','p8Units','p9CaptureUnits','p9ReleaseUnits','p9MetadataUnits','p9SerializationUnits','p9ByteUnits','p9IndexUnits','p9ExportUnits','p9Units','totalUnits','actualReceiptGroups','nativeWrapperGroups','p9Groups','totalGroups','maximumP9GroupNanosBeforeIndex','measuredWorkAtIndexCutoffNanos','finalGuardFormula')
    Require-ExactKeys $index.work $workKeys ($WrapperMode+'_P9_WORK')
    $cleanupKeys=@('p8AfterOwnersClosed','p8OwnersReleased','p8ContextRestored','p8SourcePreserved','p8ObserversDetached','p8ReferencesDropped','p9RegistrationCleared','p9DecoratorDetachCount','p9OpenDecorators','p9DecoratorReferencesDropped','p9DtoReferencesDropped')
    Require-ExactKeys $index.cleanup $cleanupKeys ($WrapperMode+'_P9_CLEANUP')
    foreach ($field in @('p8AfterOwnersClosed','p8OwnersReleased','p8ContextRestored','p8SourcePreserved','p8ObserversDetached','p8ReferencesDropped','p9RegistrationCleared','p9DecoratorReferencesDropped','p9DtoReferencesDropped')) { Require ($index.cleanup.$field -is [bool] -and $index.cleanup.$field) ($WrapperMode+'_P9_CLEANUP') }
    $expectedDetach=if ($WrapperMode -ceq 'NULL') { 0 } else { 14 }
    Require-Integer $index.cleanup.p9DecoratorDetachCount $expectedDetach $expectedDetach ($WrapperMode+'_P9_CLEANUP')
    Require-Integer $index.cleanup.p9OpenDecorators 0 0 ($WrapperMode+'_P9_CLEANUP')
    $captureState=switch -CaseSensitive ($WrapperMode) { 'NULL' {'NONE'}; 'OBSERVE' {'COMPLETE'}; 'TAP_THROW' {'DISCARDED_AFTER_SENTINEL'}; default {throw 'P9_CAPTURE_STATE'} }
    Require ([string]$index.capture.state -ceq $captureState) ($WrapperMode+'_P9_CAPTURE')
    $expectedCapture=switch -CaseSensitive ($WrapperMode) {
        'NULL' { @('NONE',$false,0,0,0,$null,$null,0,0,0,$false,0,0) }
        'OBSERVE' { @('COMPLETE',$true,14,14,0,10,11,1,246,246,$false,14,1) }
        'TAP_THROW' { @('DISCARDED_AFTER_SENTINEL',$true,14,14,0,10,11,1,246,0,$true,14,1) }
    }
    $captureValues=@([string]$index.capture.state,[bool]$index.capture.registrationArmed,[long]$index.capture.fineAttachments,[long]$index.capture.decoratorsDetached,
        [long]$index.capture.openDecorators,$index.capture.targetAttachmentOrdinal,$index.capture.targetParentOrdinal,[long]$index.capture.targetEvents,
        [long]$index.capture.padRowsBuilt,[long]$index.capture.padRowsRetained,[bool]$index.capture.sentinelObserved,[long]$index.capture.forwardedFineEvents,[long]$index.capture.targetForwardedEvents)
    for($i=0;$i -lt $expectedCapture.Count;$i++) {
        if ($null -eq $expectedCapture[$i]) { Require ($null -eq $captureValues[$i]) ($WrapperMode+'_P9_CAPTURE') }
        elseif ($expectedCapture[$i] -is [bool]) { Require ($captureValues[$i] -is [bool] -and [bool]$captureValues[$i] -eq [bool]$expectedCapture[$i]) ($WrapperMode+'_P9_CAPTURE') }
        elseif ($expectedCapture[$i] -is [string]) { Require ([string]$captureValues[$i] -ceq [string]$expectedCapture[$i]) ($WrapperMode+'_P9_CAPTURE') }
        else { Require ([long]$captureValues[$i] -eq [long]$expectedCapture[$i]) ($WrapperMode+'_P9_CAPTURE') }
    }
    Require-Integer $index.work.totalUnits 0 32768 ($WrapperMode+'_P9_UNITS')
    Require-Integer $index.work.totalGroups 0 640 ($WrapperMode+'_P9_GROUPS')
    Require-Integer $index.work.actualReceiptGroups 0 640 ($WrapperMode+'_P9_GROUPS')
    Require-Integer $index.work.nativeWrapperGroups 3 3 ($WrapperMode+'_P9_GROUPS')
    Require-Integer $index.work.p9Groups 0 640 ($WrapperMode+'_P9_GROUPS')
    Require-Integer $index.work.delegateCleanupNanos 0 90000000000 ($WrapperMode+'_P9_WORK')
    Require-Integer $index.work.measuredWorkAtIndexCutoffNanos 0 90000000000 ($WrapperMode+'_P9_WORK')
    Require ([string]$index.work.finalGuardFormula -ceq 'P9_ELAPSED_MINUS_P6_ARM_CLEANUP') 'P9_FINAL_GUARD_FORMULA'
    Require ([long]$index.work.p8Units -eq [long]$P8.units -and [long]$index.work.p8ReportedWorkNanos -eq [long]$P8.marker.workNanos) ($WrapperMode+'_P9_P8_BINDING')
    Require ([long]$index.work.p9ExportUnits -eq ([long]$index.work.p9SerializationUnits+[long]$index.work.p9ByteUnits+[long]$index.work.p9IndexUnits) -and
        [long]$index.work.p9Units -eq ([long]$index.work.p9CaptureUnits+[long]$index.work.p9ReleaseUnits+[long]$index.work.p9MetadataUnits+[long]$index.work.p9ExportUnits) -and
        [long]$index.work.totalUnits -eq ([long]$index.work.p8Units+[long]$index.work.p9Units) -and
        [long]$index.work.measuredWorkAtIndexCutoffNanos -eq ([long]$index.work.creditCutoffOffsetNanos-[long]$index.work.delegateCleanupNanos)) ($WrapperMode+'_P9_WORK_ARITHMETIC')
    Require ([long]$index.work.actualReceiptGroups -eq [long]$P8.groups -and [long]$index.work.totalGroups -eq ([long]$P8.groups+[long]$index.work.nativeWrapperGroups+[long]$index.work.p9Groups)) ($WrapperMode+'_P9_GROUP_BINDING')
    if ($WrapperMode -ceq 'TAP_THROW') { Require-Integer $index.work.creditedWorkNanos 0 0 'TAP_THROW_WORK_CREDIT' }
    else { Require-Integer $index.work.creditedWorkNanos 0 90000000000 ($WrapperMode+'_P9_WORK_CREDIT') }
    $sideRows=Get-JsonLines $sidecarPath ($WrapperMode+'_P9_SIDECAR')
    $expectedSideRows=if ($WrapperMode -ceq 'OBSERVE') { 248 } else { 2 }
    Require ($sideRows.Count -eq $expectedSideRows) ($WrapperMode+'_P9_SIDECAR_ROWS')
    $sideInfo=Get-Item -LiteralPath $sidecarPath -Force
    $dataInfo=Get-DirectFileBytes $DataPath
    $p8StdoutBytes=$script:utf8.GetByteCount([string]$Process.Stdout); $p8StderrBytes=$script:utf8.GetByteCount([string]$Process.Stderr)
    Require-ExactKeys $index.bytes @('p8DataBytes','p8StdoutBytes','p8StderrBytes','p9SidecarBytes','preIndexAggregateBytes') ($WrapperMode+'_P9_BYTES')
    Require ([long]$index.bytes.p8DataBytes -eq $dataInfo -and [long]$index.bytes.p8StdoutBytes -eq $p8StdoutBytes -and
        [long]$index.bytes.p8StderrBytes -eq $p8StderrBytes -and [long]$index.bytes.p9SidecarBytes -eq [long]$sideInfo.Length -and
        [long]$index.bytes.preIndexAggregateBytes -eq ([long]$dataInfo+[long]$p8StdoutBytes+[long]$p8StderrBytes+[long]$sideInfo.Length)) ($WrapperMode+'_P9_BYTES')
    Require ([long]$index.bytes.preIndexAggregateBytes -le 8388608 -and (Get-Item -LiteralPath $indexPath).Length -le 65536) ($WrapperMode+'_P9_BYTE_LIMIT')
    $p9IndexRef=[ordered]@{path=('runs/'+$WrapperMode+'/p9/reservation-index.json');bytes=[long](Get-Item -LiteralPath $indexPath).Length;sha256=Get-Hash $indexPath}
    return [pscustomobject]@{index=$index;indexRef=$p9IndexRef;groups=[long]$P8.groups;units=[long]$P8.units;workNanos=[long]$P8.marker.workNanos;
        creditedWorkNanos=[long]$index.work.creditedWorkNanos}
}
function Invoke-P9NormalMode([string]$WrapperMode, [string]$ProcessKey, [string]$RunIdValue) {
    $p8Mode=if ($WrapperMode -ceq 'NULL') { 'NULL' } else { 'OBSERVE' }
    $dataRelative='runs/'+$WrapperMode+'/data'; $p9Relative='runs/'+$WrapperMode+'/p9'
    $dataPath=Get-OutputPath $dataRelative; $p9Path=Get-OutputPath $p9Relative
    Require (([IO.Directory]::GetFileSystemEntries($dataPath).Count -eq 0) -and ([IO.Directory]::GetFileSystemEntries($p9Path).Count -eq 0)) ($WrapperMode+'_OUTPUT_NOT_EMPTY')
    $main='com.lushprojects.circuitjs1.client.CanonicalReservationProvenanceStudy'
    $app=@($WrapperMode,$RunIdValue,$script:InputPinsSha256,$script:wireSha256,$script:sourceInventorySha256,$dataPath,$p9Path)
    $args=@('-ea','-cp',$script:classpath,$main)+$app
    $script:phase=$WrapperMode
    Write-LaunchIntent ('runs/'+$WrapperMode+'/launch-intent.json') $WrapperMode $script:java $script:selectedJavaSha256 $args 480000 | Out-Null
    $call=Invoke-NativeChild $args $WrapperMode; $p=$call.process
    $evidence=Save-Process ('runs/'+$WrapperMode+'/process.json') ('runs/'+$WrapperMode+'.stdout') ('runs/'+$WrapperMode+'.stderr') $p $script:java $args
    Add-ProcessRecord $ProcessKey $evidence $call.wallMs
    $p8=Verify-P8Normal $p8Mode $RunIdValue $dataPath $p ([double]$call.wallMs)
    $p9=Verify-P9Index $WrapperMode $p8Mode $RunIdValue $dataPath $p9Path $p $p8 ([double]$call.wallMs)
    $disposition=switch -CaseSensitive ($WrapperMode) { 'NULL' {'COMPLETE_NULL_CONTROL_ONLY'}; 'OBSERVE' {'COMPLETE_RESERVATION_OBSERVATION_ONLY'}; 'TAP_THROW' {'EXPECTED_CONTAINED_TAP_THROW_ONLY'} }
    $script:operations += @([ordered]@{wrapperMode=$WrapperMode;p8Mode=$p8Mode;runId=$RunIdValue;disposition=$disposition;processKey=$ProcessKey;
        p8DataDirectory=$dataRelative;p9Directory=$p9Relative;workNanos=[long]$p8.marker.workNanos;groups=[long]$p8.groups;observationUnits=[long]$p8.units;p9Index=$p9.indexRef})
    if ($WrapperMode -cin @('NULL','OBSERVE')) { $script:workCreditsMs += [double]$p9.creditedWorkNanos/1000000.0 }
    Require ((Get-OperationBytes $WrapperMode) -le 8388608) 'OPERATION_OUTPUT_LIMIT'
    $script:returnedChildEvidence=$null; Verify-BridgeCopies
}
function Parse-ThrowableMarker([string]$Text, [string]$Prefix, [string]$Code) {
    $pattern='(?m)^'+[regex]::Escape($Prefix)+' (?<json>\{[^\r\n]*\})\r?$'
    $matches=[regex]::Matches($Text,$pattern)
    Require ($matches.Count -eq 1) $Code
    $m=$matches[0]; $tail=$Text.Substring($m.Index+$m.Length); $lineEnding=$null
    if ($tail.StartsWith("`r`n",[StringComparison]::Ordinal)) { $lineEnding="`r`n" }
    elseif ($tail.StartsWith("`n",[StringComparison]::Ordinal)) { $lineEnding="`n" }
    Require ($null -ne $lineEnding) $Code
    Require ($script:utf8.GetByteCount($m.Value+$lineEnding) -le 65536) $Code
    Assert-UniqueJsonKeys $m.Groups['json'].Value $Code
    try { return (ConvertFrom-Json -InputObject $m.Groups['json'].Value -ErrorAction Stop) } catch { throw $Code }
}
function Verify-PartialReference($Reference, [string]$ExpectedName, [string]$DataPath, [string]$Code) {
    Require-ExactKeys $Reference @('path','bytes','sha256') $Code
    Require ([string]$Reference.path -ceq $ExpectedName -and [string]$Reference.sha256 -cmatch '^[0-9a-f]{64}$') $Code
    Require-Integer $Reference.bytes 1 8388608 $Code
    $path=Require-ExistingFile (Join-Path $DataPath $ExpectedName) $Code; $item=Get-Item -LiteralPath $path -Force
    Require ($item.Length -eq [long]$Reference.bytes -and (Get-Hash $path) -ceq [string]$Reference.sha256) $Code
}
function Invoke-P6CallbackThrow([string]$RunIdValue) {
    $mode='CALLBACK_THROW'; $relative='runs/CALLBACK_THROW/data'; $data=Get-OutputPath $relative
    $main='com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy'
    $app=@($mode,$RunIdValue,$script:InputPinsSha256,$script:wireSha256,$data)
    $args=@('-ea','-cp',$script:classpath,$main)+$app
    $script:phase='CALLBACK_THROW'; Write-LaunchIntent 'runs/CALLBACK_THROW/launch-intent.json' $mode $script:java $script:selectedJavaSha256 $args 480000 | Out-Null
    $call=Invoke-NativeChild $args 'CALLBACK_THROW'; $p=$call.process
    $evidence=Save-Process 'runs/CALLBACK_THROW/process.json' 'runs/CALLBACK_THROW.stdout' 'runs/CALLBACK_THROW.stderr' $p $script:java $args
    Add-ProcessRecord 'callbackThrow' $evidence $call.wallMs
    Require ($p.ExitCode -eq 1 -and [string]::IsNullOrEmpty([string]$p.Stdout)) 'CALLBACK_THROW_EXIT'
    $m=Parse-ThrowableMarker ([string]$p.Stderr) 'CANONICAL_PREFIX_STAGE_EXPECTED_CALLBACK_THROW' 'CALLBACK_THROW_MARKER'
    Require-ExactKeys $m @('schema','mode','runId','sourceManifestSha256','wireSha256','scope','layoutQualification','exceptionType','exceptionMessage','partialReceipt','stages','inclusiveWorkNanos','cleanupAccounting') 'CALLBACK_THROW_SCHEMA'
    Require ([string]$m.schema -ceq 'tsj-canonical-prefix-stage-exception-v1' -and [string]$m.mode -ceq $mode -and [string]$m.runId -ceq $RunIdValue -and
        [string]$m.sourceManifestSha256 -ceq $script:InputPinsSha256 -and [string]$m.wireSha256 -ceq $script:wireSha256 -and
        [string]$m.scope -ceq 'CANONICAL_BASE_REQ0_ZERO_DELTA_STAGES_ONLY' -and $m.layoutQualification -is [bool] -and -not $m.layoutQualification -and
        [string]$m.exceptionType -ceq 'java.lang.IllegalStateException' -and [string]$m.exceptionMessage -ceq 'CANONICAL_PREFIX_STAGE_CALLBACK_THROW' -and
        [string]$m.cleanupAccounting -ceq 'FULL_NATIVE_WALL_PLUS_POST_SOURCE') 'CALLBACK_THROW_IDENTITY'
    Verify-PartialReference $m.partialReceipt 'op-1.jsonl' $data 'CALLBACK_THROW_RECEIPT'
    Require ([string]$m.stages.status -ceq 'EXPECTED_CALLBACK_THROW_ONLY' -and [string]$m.stages.mode -ceq $mode -and
        [string]$m.stages.runId -ceq $RunIdValue -and [string]$m.stages.sourceManifestSha256 -ceq $script:InputPinsSha256 -and [string]$m.stages.wireSha256 -ceq $script:wireSha256) 'CALLBACK_THROW_STAGES'
    foreach ($flag in @('ownersReleased','contextRestored','sourceRefsDropped','observersDetached')) { Require ($m.stages.lifecycle.$flag -is [bool] -and $m.stages.lifecycle.$flag) 'CALLBACK_THROW_CLEANUP' }
    Require ($m.stages.queries -is [array] -and $m.stages.queries.Count -eq 1 -and [string]$m.stages.queries[0].outcome -ceq 'EXPECTED_CALLBACK_THROW') 'CALLBACK_THROW_QUERY'
    Require-Integer $m.inclusiveWorkNanos 0 90000000000 'CALLBACK_THROW_WORK'
    Require (([double]$m.inclusiveWorkNanos/1000000.0) -le [double]$call.wallMs) 'CALLBACK_THROW_WORK_WALL'
    Require-ExactDataFiles $data @('op-1.jsonl') 'CALLBACK_THROW_DATA_CENSUS'
    $groups=Get-GroupRecordCount (Join-Path $data 'op-1.jsonl')
    $units=[long]$m.stages.totals.totalObservationUnits
    Require-Integer $m.stages.totals.priorObservationUnits 0 0 'CALLBACK_THROW_PRIOR_UNITS'
    Require-Integer $units 0 32768 'CALLBACK_THROW_UNITS'
    $script:operations += @([ordered]@{wrapperMode=$mode;p8Mode=$mode;runId=$RunIdValue;disposition='EXPECTED_CALLBACK_THROW_ONLY';processKey='callbackThrow';
        p8DataDirectory=$relative;p9Directory=$null;workNanos=$null;groups=$groups;observationUnits=$units;p9Index=$null})
    Require ((Get-OperationBytes $mode) -le 8388608) 'OPERATION_OUTPUT_LIMIT'
    $script:returnedChildEvidence=$null; Verify-BridgeCopies
}
function Invoke-P8ReasonThrow([string]$RunIdValue) {
    $mode='REASON_THROW'; $relative='runs/REASON_THROW/data'; $data=Get-OutputPath $relative
    $main='com.lushprojects.circuitjs1.client.CanonicalGeometricPrefixStudy'
    $app=@($mode,$RunIdValue,$script:InputPinsSha256,$script:wireSha256,$script:sourceInventorySha256,$data)
    $args=@('-ea','-cp',$script:classpath,$main)+$app
    $script:phase='REASON_THROW'; Write-LaunchIntent 'runs/REASON_THROW/launch-intent.json' $mode $script:java $script:selectedJavaSha256 $args 480000 | Out-Null
    $call=Invoke-NativeChild $args 'REASON_THROW'; $p=$call.process
    $evidence=Save-Process 'runs/REASON_THROW/process.json' 'runs/REASON_THROW.stdout' 'runs/REASON_THROW.stderr' $p $script:java $args
    Add-ProcessRecord 'reasonThrow' $evidence $call.wallMs
    Require ($p.ExitCode -eq 1 -and [string]::IsNullOrEmpty([string]$p.Stdout)) 'REASON_THROW_EXIT'
    $m=Parse-ThrowableMarker ([string]$p.Stderr) 'CANONICAL_GEOMETRIC_PREFIX_EXPECTED_REASON_THROW' 'REASON_THROW_MARKER'
    Require-ExactKeys $m @('schema','runId','mode','inputSha256','sourceSha256','wireSha256','queryOrdinal','parentOrdinal','sentinel','exceptionClass','exceptionMessage',
        'suppressedCount','cleanupKind','workNanos','cleanupComplete','sourcePreserved','observersDetached','referencesDropped','ownersReleased','contextRestored',
        'partialSidecar','partialOperationReceipt','parentBlockers','parentStages') 'REASON_THROW_SCHEMA'
    Require ([string]$m.schema -ceq 'tsj-canonical-geometric-prefix-exception-v1' -and [string]$m.runId -ceq $RunIdValue -and [string]$m.mode -ceq $mode -and
        [string]$m.inputSha256 -ceq $script:InputPinsSha256 -and [string]$m.sourceSha256 -ceq $script:sourceInventorySha256 -and [string]$m.wireSha256 -ceq $script:wireSha256 -and
        [string]$m.sentinel -ceq 'FIRST_GEOMETRIC_REASON_CALLBACK' -and [string]$m.exceptionClass -ceq 'java.lang.IllegalStateException' -and
        [string]$m.exceptionMessage -ceq 'FIRST_GEOMETRIC_REASON_CALLBACK' -and [string]$m.cleanupKind -ceq 'FULL_NATIVE_WALL_PLUS_POST_SOURCE') 'REASON_THROW_IDENTITY'
    Require-Integer $m.queryOrdinal 0 0 'REASON_THROW_QUERY'; Require-Integer $m.parentOrdinal 0 0 'REASON_THROW_QUERY'; Require-Integer $m.suppressedCount 0 0 'REASON_THROW_SUPPRESSED'
    Require-Integer $m.workNanos 0 90000000000 'REASON_THROW_WORK'; Require (([double]$m.workNanos/1000000.0) -le [double]$call.wallMs) 'REASON_THROW_WORK_WALL'
    foreach ($flag in @('cleanupComplete','sourcePreserved','observersDetached','referencesDropped','ownersReleased','contextRestored')) { Require ($m.$flag -is [bool] -and $m.$flag) 'REASON_THROW_CLEANUP' }
    Require-ExactDataFiles $data @('op-1.jsonl','prefix-blockers.json','prefix-stages.json','prefix-geometric-reasons.jsonl') 'REASON_THROW_DATA_CENSUS'
    Verify-PartialReference $m.partialSidecar 'prefix-geometric-reasons.jsonl' $data 'REASON_THROW_REFERENCE'
    Verify-PartialReference $m.partialOperationReceipt 'op-1.jsonl' $data 'REASON_THROW_REFERENCE'
    Verify-PartialReference $m.parentBlockers 'prefix-blockers.json' $data 'REASON_THROW_REFERENCE'
    Verify-PartialReference $m.parentStages 'prefix-stages.json' $data 'REASON_THROW_REFERENCE'
    $side=Get-JsonLines (Join-Path $data 'prefix-geometric-reasons.jsonl') 'REASON_THROW_SIDECAR'
    Require ($side.Count -eq 3 -and [string]$side[$side.Count-1].kind -ceq 'footer') 'REASON_THROW_SIDECAR'
    Require ([string]$side[$side.Count-1].mode -ceq $mode -and [string]$side[$side.Count-1].runId -ceq $RunIdValue -and
        [string]$side[$side.Count-1].inputSha256 -ceq $script:InputPinsSha256 -and [string]$side[$side.Count-1].sourceSha256 -ceq $script:sourceInventorySha256) 'REASON_THROW_SIDECAR'
    Require-Integer $side[$side.Count-1].totalObservationUnits 0 32768 'REASON_THROW_UNITS'
    $groups=Get-GroupRecordCount (Join-Path $data 'op-1.jsonl')
    $script:operations += @([ordered]@{wrapperMode=$mode;p8Mode=$mode;runId=$RunIdValue;disposition='EXPECTED_REASON_THROW_ONLY';processKey='reasonThrow';
        p8DataDirectory=$relative;p9Directory=$null;workNanos=[long]$m.workNanos;groups=$groups;observationUnits=[long]$side[$side.Count-1].totalObservationUnits;p9Index=$null})
    Require ((Get-OperationBytes $mode) -le 8388608) 'OPERATION_OUTPUT_LIMIT'
    $script:returnedChildEvidence=$null; Verify-BridgeCopies
}
function Run-SuiteOperations {
    $ids=@{}; foreach ($mode in @('NULL','OBSERVE','TAP_THROW','CALLBACK_THROW','REASON_THROW')) { $ids[$mode]=[Guid]::NewGuid().ToString('D').ToLowerInvariant() }
    foreach ($id in $ids.Values) { Require ($id -cmatch '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$') 'RUN_UUID' }
    Invoke-P9NormalMode 'NULL' 'nullMode' $ids['NULL']
    Invoke-P9NormalMode 'OBSERVE' 'observeMode' $ids['OBSERVE']
    Invoke-P9NormalMode 'TAP_THROW' 'tapThrow' $ids['TAP_THROW']
    Invoke-P6CallbackThrow $ids['CALLBACK_THROW']
    Invoke-P8ReasonThrow $ids['REASON_THROW']
    Require ($script:operations.Count -eq 5) 'OPERATION_CENSUS'
}
function Get-OutputClosure {
    $expected=@($script:outputLeaves | Where-Object { $_ -cne 'host-result.json' })
    $tree=Assert-TreeEquals $expected $script:allowedDirectories 'OUTPUT_CENSUS'
    $names=Get-OrdinalSortedStrings $expected; $dirs=Get-OrdinalSortedStrings $script:allowedDirectories
    $rows=@(); [long]$aggregate=0
    foreach ($name in $names) {
        $path=Get-OutputPath $name; Assert-NoReparse $path; $item=Get-Item -LiteralPath $path -Force
        if ($name.EndsWith('.json',[StringComparison]::Ordinal)) { Require ($item.Length -le 65536) 'STRUCTURED_OUTPUT_LIMIT' }
        if ($name -ceq 'compiler-sources.txt') { Require ($item.Length -le 1048576) 'COMPILER_LIST_LIMIT' }
        if ($name.EndsWith('.jsonl',[StringComparison]::Ordinal)) { [void](Get-JsonLines $path 'JSONL_OUTPUT_INVALID') }
        $rows += @([ordered]@{name=$name;bytes=[long]$item.Length;sha256=Get-Hash $path})
        $aggregate += [long]$item.Length
    }
    Require ($aggregate -eq [long]$script:writtenOutputBytes -and $aggregate -le 41943040) 'OUTPUT_BYTE_CLOSURE'
    return [ordered]@{files=$rows;directories=$dirs;aggregateBytes=$aggregate;hostResultExcluded=$true}
}
function New-SuiteIndexAndFooter {
    $before=@($script:outputLeaves | Where-Object { $_ -cne 'suite-index.json' -and $_ -cne 'suite-footer.json' -and $_ -cne 'host-result.json' })
    $tree=Assert-TreeEquals $before $script:allowedDirectories 'OUTPUT_CENSUS'
    $names=Get-OrdinalSortedStrings $before; $dirs=Get-OrdinalSortedStrings $script:allowedDirectories
    $artifacts=@(); [long]$aggregate=0
    foreach ($name in $names) {
        $path=Get-OutputPath $name; $item=Get-Item -LiteralPath $path -Force; $identity=Get-ArtifactRoleAndOperation $name
        $artifacts += @([ordered]@{path=$name;role=[string]$identity.role;operation=[string]$identity.operation;bytes=[long]$item.Length;sha256=Get-Hash $path})
        $aggregate += [long]$item.Length
    }
    Require ($aggregate -eq [long]$script:writtenOutputBytes -and $aggregate -le 41943040) 'OUTPUT_BYTE_CLOSURE'
    $index=[ordered]@{schema='tsj-q100-reservation-provenance-suite-index-v1';status='HOST_INDEX_COMPLETE';mode=$script:Mode;suiteId=$script:suiteId;
        inputSha256=$script:InputPinsSha256;sourceSha256=$script:sourceInventorySha256;wireSha256=$script:wireSha256;modeOrder=@($script:modeOrder);
        artifactCount=[long]$artifacts.Count;aggregateBytes=$aggregate;artifacts=$artifacts;directories=$dirs;layoutQualification=$false}
    Write-NewJson 'suite-index.json' $index
    $indexPath=Get-OutputPath 'suite-index.json'; $indexInfo=Get-Item -LiteralPath $indexPath -Force
    Require ($indexInfo.Length -le 65536) 'SUITE_INDEX_LIMIT'
    $footer=[ordered]@{schema='tsj-q100-reservation-provenance-suite-footer-v1';status='HOST_OUTPUT_CLOSED';mode=$script:Mode;suiteId=$script:suiteId;
        inputSha256=$script:InputPinsSha256;sourceSha256=$script:sourceInventorySha256;wireSha256=$script:wireSha256;modeOrder=@($script:modeOrder);
        suiteIndex=[ordered]@{path='suite-index.json';bytes=[long]$indexInfo.Length;sha256=Get-Hash $indexPath};artifactCount=[long]$artifacts.Count;
        aggregateBytes=$aggregate;directoryCount=[long]$dirs.Count;layoutQualification=$false}
    Write-NewJson 'suite-footer.json' $footer
}
function Verify-OutputAfterResult($Closure) {
    [void](Assert-TreeEquals $script:outputLeaves $script:allowedDirectories 'OUTPUT_CENSUS')
    foreach ($row in $Closure.files) {
        $path=Get-OutputPath ([string]$row.name); $item=Get-Item -LiteralPath $path -Force
        Require ($item.Length -eq [long]$row.bytes -and (Get-Hash $path) -ceq [string]$row.sha256) 'OUTPUT_CHANGED_AFTER_CLOSURE'
    }
    $hostResultPath=Get-OutputPath 'host-result.json'; $hostItem=Get-Item -LiteralPath $hostResultPath -Force
    Require ($hostItem.Length -le 65536) 'HOST_RESULT_LIMIT'
    [long]$total=[long]$Closure.aggregateBytes+[long]$hostItem.Length
    Require ($total -eq [long]$script:writtenOutputBytes -and $total -le 41943040) 'OUTPUT_BYTE_CLOSURE'
    $perOperation=@{NULL=[long]0;OBSERVE=[long]0;TAP_THROW=[long]0;CALLBACK_THROW=[long]0;REASON_THROW=[long]0}
    foreach ($row in $Closure.files) {
        if ([string]$row.name -cin @('suite-index.json','suite-footer.json')) { continue }
        $identity=Get-ArtifactRoleAndOperation ([string]$row.name)
        if ($perOperation.ContainsKey([string]$identity.operation)) { $perOperation[[string]$identity.operation]+=[long]$row.bytes }
    }
    [long]$operationTotal=0
    foreach ($op in @('NULL','OBSERVE','TAP_THROW','CALLBACK_THROW','REASON_THROW')) {
        if ($script:Mode -ceq 'SUITE') { Require ($perOperation[$op] -le 8388608) 'OPERATION_OUTPUT_LIMIT'; $operationTotal += [long]$perOperation[$op] }
    }
    Require (($total-$operationTotal) -le 8388608) 'HOST_EVIDENCE_LIMIT'
    Require ($hostItem.Length -le 65536) 'STRUCTURED_OUTPUT_LIMIT'
}
function Get-FailureErrorText($ErrorRecord) {
    $value='EVIDENCE_WRITE_FAILED'
    if ($null -ne $ErrorRecord) {
        if ($ErrorRecord -is [System.Management.Automation.ErrorRecord]) { $value=[string]$ErrorRecord.Exception.Message }
        elseif ($ErrorRecord -is [Exception]) { $value=[string]$ErrorRecord.Message }
        else { $value=[string]$ErrorRecord }
    }
    if ([string]::IsNullOrWhiteSpace($value)) { $value='EVIDENCE_WRITE_FAILED' }
    if ($value.Length -gt 8192) { $value=$value.Substring(0,8192) }
    return $value
}
function Write-FailureBundleBytes([string]$Path, [string]$RelativePath, [byte[]]$Bytes, [ref]$BundleTotal) {
    Require ($Bytes.Length -le 134217728) 'FAILURE_FILE_LIMIT'
    Require ([decimal]$BundleTotal.Value+[decimal]$Bytes.Length -le 268435456) 'FAILURE_BUNDLE_LIMIT'
    $stream=[IO.File]::Open($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try { if ($Bytes.Length -gt 0) { $stream.Write($Bytes,0,$Bytes.Length) }; $stream.Flush() } finally { $stream.Dispose() }
    $read=[IO.File]::ReadAllBytes($Path)
    Require (Test-ByteEquality $Bytes $read) 'FAILURE_READBACK_MISMATCH'
    $BundleTotal.Value=[long]$BundleTotal.Value+[long]$Bytes.Length
    return [ordered]@{path=$RelativePath;bytes=[long]$Bytes.Length;sha256=Get-Hash $Path}
}
function Save-FailureProcessBundle {
    if ($script:registeredProcessCaptures.Count -eq 0 -and $null -eq $script:failedProcessCall) { $script:failureBundleResult=$null; return }
    $root=$null; $manifestPath=$null; $manifestSha=$null; $writeError=$null; $manifestError=$null; $complete=$true
    try {
        $root=[IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetTempPath()) ('tsj-p9-reservation-failure-r1-'+[Guid]::NewGuid().ToString('N'))))
        Require ($root -match 'tsj-p9-reservation-failure-r1-[0-9a-f]{32}$' -and -not (Test-Path -LiteralPath $root)) 'FAILURE_ROOT_IDENTITY'
        Assert-NoReparse ([IO.Path]::GetDirectoryName($root))
        foreach ($forbidden in @($script:RepositoryRoot,$script:p9Root,$script:OutputRoot,[IO.Path]::GetDirectoryName($script:InputPinsPath),
            [IO.Path]::GetDirectoryName($script:p8PinsPath),[IO.Path]::GetDirectoryName($script:p6PinsPath),[IO.Path]::GetDirectoryName($script:canaryPinsPath),
            $script:bridgeRoot,$script:scratch)) {
            if (-not [string]::IsNullOrWhiteSpace([string]$forbidden)) { Require (-not (Test-PathUnder $root ([string]$forbidden)) -and -not (Test-PathUnder ([string]$forbidden) $root)) 'FAILURE_ROOT_OVERLAP' }
        }
        [void][IO.Directory]::CreateDirectory($root); Assert-NoReparse $root
        Require ($PID -gt 0 -and [long]$script:hostStartTicks -gt 0) 'FAILURE_OWNER_IDENTITY'
        $creationTicks=[long](Get-Item -LiteralPath $root -Force).CreationTimeUtc.Ticks
        Require ($creationTicks -gt 0) 'FAILURE_OWNER_IDENTITY'
        $ownerInfo=[ordered]@{schema='tsj-q100-reservation-failed-process-owner-v1';hostPid=[int]$PID;hostStartTicks=[string]$script:hostStartTicks;
            hostExecutable=[string]$script:owner.Path;hostScriptSha256=[string]$script:hostSha;inputSha256=[string]$script:InputPinsSha256;
            bundleRoot=$root;creationTicks=$creationTicks.ToString([Globalization.CultureInfo]::InvariantCulture)}
        $ownerBytes=$script:utf8.GetBytes((ConvertTo-Json -InputObject $ownerInfo -Depth 8 -Compress)+"`n")
        Require ($ownerBytes.Length -le 65536) 'FAILURE_OWNER_LIMIT'
        $bundleTotal=[long]0; $ownerRef=Write-FailureBundleBytes (Join-Path $root 'owner.json') 'owner.json' $ownerBytes ([ref]$bundleTotal)
    } catch {
        $writeError=Get-FailureErrorText $_; $manifestError=$writeError; $complete=$false
        if ($null -eq $root -or -not (Test-Path -LiteralPath $root -PathType Container)) {
            $script:failureEvidenceWriteError=$writeError
            $script:failureBundleResult=[ordered]@{root=$null;manifestPath=$null;manifestSha256=$null;complete=$false;writeError=$writeError}
            return
        }
        $ownerRef=$null; $bundleTotal=[long]0
    }
    $rows=@()
    foreach ($capture in $script:registeredProcessCaptures) {
        $ordinal=[int]$capture.ordinal; $captureRoot=Join-Path $root ('calls/'+$ordinal.ToString('D4'))
        $row=[ordered]@{ordinal=$ordinal;phase=[string]$capture.phase;receipt=$null;stdout=$null;stderr=$null;status='SAVED';error=$null}
        try {
            [void][IO.Directory]::CreateDirectory($captureRoot)
            $raw=$capture.process
            $receiptBytes=$script:utf8.GetBytes((ConvertTo-Json -InputObject $raw -Depth 40 -Compress)+"`n")
            $receiptRelative='calls/'+$ordinal.ToString('D4')+'/receipt.json'
            $stdoutRelative='calls/'+$ordinal.ToString('D4')+'/stdout.utf8'
            $stderrRelative='calls/'+$ordinal.ToString('D4')+'/stderr.utf8'
            $row.receipt=Write-FailureBundleBytes (Join-Path $captureRoot 'receipt.json') $receiptRelative $receiptBytes ([ref]$bundleTotal)
            $stdoutBytes=$script:utf8.GetBytes([string]$raw.Stdout); $stderrBytes=$script:utf8.GetBytes([string]$raw.Stderr)
            $row.stdout=Write-FailureBundleBytes (Join-Path $captureRoot 'stdout.utf8') $stdoutRelative $stdoutBytes ([ref]$bundleTotal)
            $row.stderr=Write-FailureBundleBytes (Join-Path $captureRoot 'stderr.utf8') $stderrRelative $stderrBytes ([ref]$bundleTotal)
        } catch {
            $row.status='WRITE_FAILED'; $row.error=Get-FailureErrorText $_; $complete=$false
            if ($null -eq $writeError) { $writeError=[string]$row.error }; if ($null -eq $manifestError) { $manifestError=[string]$row.error }
        }
        $rows += @($row)
    }
    $failedCallRef=$null
    if ($null -ne $script:failedProcessCall) {
        try {
            $callBytes=$script:utf8.GetBytes((ConvertTo-Json -InputObject $script:failedProcessCall -Depth 24 -Compress)+"`n")
            $failedCallRef=Write-FailureBundleBytes (Join-Path $root 'failed-call.json') 'failed-call.json' $callBytes ([ref]$bundleTotal)
            $manifestError='Unreturned invocation has no process receipt or termination proof; failed-call.json sha256='+$failedCallRef.sha256
            $complete=$false
        } catch { $writeError=Get-FailureErrorText $_; $manifestError=$writeError; $complete=$false }
    }
    if ($null -eq $ownerRef) { $complete=$false }
    $manifest=[ordered]@{schema='tsj-q100-reservation-failed-process-bundle-v1';status=$(if($complete){'PRESERVED_FAILED_PROCESS_EVIDENCE_ONLY'}else{'FAILED_TO_PRESERVE_ALL_PROCESS_EVIDENCE'});
        complete=[bool]$complete;owner=$ownerRef;captures=$rows;totalBytes=[long]$bundleTotal;error=$manifestError}
    try {
        $manifestPath=Join-Path $root 'manifest.json'
        $manifestBytes=$script:utf8.GetBytes((ConvertTo-Json -InputObject $manifest -Depth 20 -Compress)+"`n")
        Require ($manifestBytes.Length -le 65536) 'FAILURE_MANIFEST_LIMIT'
        $stream=[IO.File]::Open($manifestPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
        try { $stream.Write($manifestBytes,0,$manifestBytes.Length); $stream.Flush() } finally { $stream.Dispose() }
        Require (Test-ByteEquality $manifestBytes ([IO.File]::ReadAllBytes($manifestPath))) 'FAILURE_MANIFEST_READBACK'
        $manifestSha=Get-Hash $manifestPath
    } catch { $writeError=Get-FailureErrorText $_; $script:failureEvidenceWriteError=$writeError; $manifestPath=$null; $manifestSha=$null; $complete=$false }
    $script:failureEvidenceWriteError=$writeError
    $script:failureBundleResult=[ordered]@{root=$root;manifestPath=$manifestPath;manifestSha256=$manifestSha;complete=[bool]$complete;writeError=$writeError}
}
function Write-FailureSummary {
    if (-not $script:outputCreated) { return }
    $failureEvidence=$null
    if ($null -ne $script:failureBundleResult) { $failureEvidence=[ordered]@{root=$script:failureBundleResult.root;manifestPath=$script:failureBundleResult.manifestPath;
        manifestSha256=$script:failureBundleResult.manifestSha256;complete=[bool]$script:failureBundleResult.complete;writeError=$script:failureBundleResult.writeError} }
    else { $failureEvidence=[ordered]@{root=$null;manifestPath=$null;manifestSha256=$null;complete=$false;writeError=$null} }
    $record=[ordered]@{schema='tsj-q100-reservation-host-failure-v1';status='FAIL';phase=[string]$script:phase;code=[string]$script:failureCode;
        cleanupError=$(if($null -eq $script:cleanupFailure){$null}else{[string]$script:cleanupFailure});failureEvidence=$failureEvidence}
    try { Write-NewJson 'host-failure.json' $record }
    catch { $script:hostFailureSummaryWriteError=Get-FailureErrorText $_ }
}
function Write-RejectionFrame {
    $record=[ordered]@{code=[string]$script:failureCode;phase=[string]$script:phase;outputCreated=[bool]$script:outputCreated;
        scratchCreated=[bool]$script:scratchCreated;compilerInvoked=[bool]$script:compilerInvoked;nativeInvoked=[bool]$script:nativeInvoked;
        cleanupError=$(if($null -eq $script:cleanupFailure){$null}else{[string]$script:cleanupFailure})}
    $json=ConvertTo-Json -InputObject $record -Depth 8 -Compress
    $line='P9_RESERVATION_HOST_REJECT '+$json+"`r`n"
    Require ($script:utf8.GetByteCount($line) -le 65536) 'HOST_REJECTION_FRAME_LIMIT'
    [Console]::Error.Write($line); [Console]::Error.Flush()
}
function Write-FailureEvidenceDiagnostic {
    if ($null -eq $script:failureEvidenceWriteError -and $null -eq $script:hostFailureSummaryWriteError -and
        ($null -eq $script:failureBundleResult -or [bool]$script:failureBundleResult.complete)) { return }
    $v=[ordered]@{code='P9_RESERVATION_FAILURE_EVIDENCE';failureEvidence=$script:failureBundleResult;
        failureEvidenceWriteError=$script:failureEvidenceWriteError;hostFailureSummaryWriteError=$script:hostFailureSummaryWriteError}
    $json=ConvertTo-Json -InputObject $v -Depth 12 -Compress
    $line='P9_RESERVATION_FAILURE_EVIDENCE '+$json+"`r`n"
    if ($script:utf8.GetByteCount($line) -le 65536) { [Console]::Error.Write($line); [Console]::Error.Flush() }
}
try {
    $phase='HOST_INPUTS'
    Load-HostInputs
    Validate-OutputDestination
    $suite=[Guid]::NewGuid().ToString('D').ToLowerInvariant()
    Require ($suite -cmatch '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$') 'SUITE_ID'
    $script:suiteId=$suite

    # The first authenticated audit is intentionally before output-root creation.
    $preflight=Invoke-Auditor 'preflight' 'preflight' 'source-preflight' -MemoryOnly
    Create-OutputRoot
    Save-PreflightEvidence $preflight
    Load-P9Inventory
    New-Scratch
    Run-Compiler

    $phase='SOURCE_PRENATIVE'
    $preNative=Invoke-Auditor 'prenative' 'preNative' 'source-prenative'
    $sourceChecks.preNative=$preNative.summary
    Verify-BridgeCopies

    Run-NativeCanariesAndControls
    if ($Mode -ceq 'SUITE') { Run-SuiteOperations; Verify-BridgeCopies }
    else { Require ($operations.Count -eq 0) 'CANARY_OPERATION_CENSUS' }

    $phase='SOURCE_POST'
    $cleanupStartOffsetMs=[double]$hostClock.Elapsed.TotalMilliseconds
    $post=Invoke-Auditor 'post' 'postSource' 'source-post'
    $sourceChecks.postSource=$post.summary
    Require ((Get-Hash $argsFile) -ceq $compilerSourceListSha256 -and (Get-Hash (Get-OutputPath 'compiler-sources.txt')) -ceq $compilerSourceListSha256) 'COMPILER_LIST_CHANGED'
    Verify-BridgeCopies
    Require ([long](Get-Item -LiteralPath $scratch -Force).CreationTimeUtc.Ticks -eq [long]$scratchTicks) 'SCRATCH_IDENTITY_CHANGED'
    Remove-OwnedScratch
    Require ($scratchRemoved) 'SCRATCH_CLEANUP_UNPROVEN'

    $phase='TIMING_CLOSURE'
    $nativePhaseElapsedMs=[double]$cleanupStartOffsetMs-[double]$nativePhaseStartOffsetMs
    $childWallSum=[double]$nativePhaseChildWallSumMs
    $workCredits=[double]$workCreditsMs
    $nativePhaseOverheadMs=$nativePhaseElapsedMs-$childWallSum
    $nativePhaseChargeMs=$nativePhaseElapsedMs-$workCredits
    Require ($nativePhaseStartOffsetMs -ge 0 -and $cleanupStartOffsetMs -ge $nativePhaseStartOffsetMs -and
        $nativePhaseElapsedMs -ge $childWallSum -and $nativePhaseOverheadMs -ge 0 -and $workCredits -ge 0 -and
        $workCredits -le $childWallSum -and $nativePhaseChargeMs -ge 0) 'NATIVE_PHASE_ARITHMETIC'
    Require ($nativePhaseChargeMs+([double]$hostClock.Elapsed.TotalMilliseconds-[double]$cleanupStartOffsetMs) -le 120000) 'CLEANUP_BOUND'
    Require ($hostClock.Elapsed.TotalMilliseconds -le 1800000) 'HOST_OUTER_LIMIT'

    Get-OutputCounterWithNativeFiles
    $phase='OUTPUT_INDEX'
    New-SuiteIndexAndFooter
    $closure=Get-OutputClosure
    $hostElapsedBeforeFinalWriteMs=[double]$hostClock.Elapsed.TotalMilliseconds
    $compilerInfo=Get-Item -LiteralPath (Get-OutputPath 'compiler-sources.txt') -Force
    $hostObject=[ordered]@{pid=[int]$PID;startTicks=[long]$hostStartTicks;executable=[string]$owner.Path;executableSha256=$selectedPowerShellSha256;script=$hostPath;scriptSha256=$hostSha}
    $hostResult=[ordered]@{
        schema='tsj-q100-reservation-provenance-host-v1'
        status=$(if($Mode -ceq 'CANARY'){'HOST_CANARY_COMPLETE_READER_PENDING'}else{'HOST_COMPLETE_READER_PENDING'})
        mode=$Mode;suiteId=$suiteId;inputSha256=$InputPinsSha256;sourceSha256=$sourceInventorySha256;sourceParent=$sourceParent;
        p8PrerequisiteSha256=$p8PinsSha256;wireSha256=$wireSha256;modeOrder=@($modeOrder);host=$hostObject;processes=$processRecords;sourceChecks=$sourceChecks
        compiler=[ordered]@{sourceCount=1085;sourceList='compiler-sources.txt';sourceListBytes=[long]$compilerInfo.Length;sourceListSha256=Get-Hash (Get-OutputPath 'compiler-sources.txt')}
        bridgeCopies=[ordered]@{receipt='bridge-copies.json';count=3;verifiedAfterCompile=$true;verifiedBeforeNative=$true;verifiedAfterNative=$true}
        canaries=$canaries;operations=@($operations)
        timing=[ordered]@{nativePhaseStartOffsetMs=[double]$nativePhaseStartOffsetMs;cleanupStartOffsetMs=[double]$cleanupStartOffsetMs;
            nativePhaseElapsedMs=[double]$nativePhaseElapsedMs;childWallSumMs=[double]$childWallSum;workCreditsMs=[double]$workCredits;
            nativePhaseChargeMs=[double]$nativePhaseChargeMs;nativePhaseOverheadMs=[double]$nativePhaseOverheadMs;hostElapsedBeforeFinalWriteMs=[double]$hostElapsedBeforeFinalWriteMs}
        cleanup=[ordered]@{scratchPath=$scratch;scratchCreationTicks=[string]$scratchTicks;scratchRemoved=[bool]$scratchRemoved;boundMs=120000;
            formula='nativePhaseChargeMs+(parentFullHostElapsedMs-cleanupStartOffsetMs)'}
        limits=[ordered]@{compileMs=60000;auditMs=60000;nativeMs=480000;readerMs=180000;outerMs=1800000;cleanupMs=120000;workNanos=90000000000;
            groups=640;groupNanos=5000000000;observationUnits=32768;cloneEntries=1048576;chunkEntries=4096;expansions=1000000;holes=48;flips=4;
            operationBytes=8388608;hostEvidenceBytes=8388608;suiteBytes=41943040;lineBytes=65536;compilerListBytes=1048576;inventoryBytes=1048576}
        outputClosure=$closure;layoutQualification=$false
    }
    $phase='HOST_RESULT'
    Write-NewJson 'host-result.json' $hostResult
    Verify-OutputAfterResult $closure
    Require ($hostClock.Elapsed.TotalMilliseconds -le 1800000) 'HOST_OUTER_LIMIT'
    $hostResultPath=Get-OutputPath 'host-result.json'; $indexPath=Get-OutputPath 'suite-index.json'; $footerPath=Get-OutputPath 'suite-footer.json'
    $success=[ordered]@{schema='tsj-q100-reservation-provenance-host-complete-v1';status=[string]$hostResult.status;mode=$Mode;suiteId=$suiteId;
        inputSha256=$InputPinsSha256;sourceSha256=$sourceInventorySha256;wireSha256=$wireSha256;
        hostResult=[ordered]@{path='host-result.json';bytes=[long](Get-Item -LiteralPath $hostResultPath).Length;sha256=Get-Hash $hostResultPath};
        suiteIndex=[ordered]@{path='suite-index.json';bytes=[long](Get-Item -LiteralPath $indexPath).Length;sha256=Get-Hash $indexPath};
        suiteFooter=[ordered]@{path='suite-footer.json';bytes=[long](Get-Item -LiteralPath $footerPath).Length;sha256=Get-Hash $footerPath};layoutQualification=$false}
    $line='P9_RESERVATION_HOST_COMPLETE '+(ConvertTo-Json -InputObject $success -Depth 16 -Compress)+"`r`n"
    Require ($utf8.GetByteCount($line) -le 65536) 'HOST_MARKER_LIMIT'
    [Console]::Out.Write($line); [Console]::Out.Flush()
}
catch {
    $firstFailure=if ($null -ne $failedProcessErrorRecord) { $failedProcessErrorRecord } else { $_ }
    $failureMessage=Get-FailureErrorText $firstFailure
    if ([string]::IsNullOrWhiteSpace($failureMessage)) { $failureMessage='HOST_FAILURE' }
    $failureCode=if ($null -ne $failedProcessCall) { 'CALL_FAILED_WITHOUT_RETURNED_PROCESS_RECEIPT' } else { $failureMessage }
    if ($null -eq $failedProcessReturn -and $null -ne $returnedChildEvidence) { $failedProcessReturn=$returnedChildEvidence }
    if ($registeredProcessCaptures.Count -gt 0 -or $null -ne $failedProcessCall) {
        try { Save-FailureProcessBundle }
        catch {
            $failureEvidenceWriteError=Get-FailureErrorText $_
            $failureBundleResult=[ordered]@{root=$null;manifestPath=$null;manifestSha256=$null;complete=$false;writeError=$failureEvidenceWriteError}
        }
    } else { $failureBundleResult=$null }
    $terminationUnproven=($null -ne $failedProcessCall)
    foreach ($capture in $registeredProcessCaptures) {
        $property=$null
        if ($null -ne $capture.process) { $property=$capture.process.PSObject.Properties['TerminationProven'] }
        if ($null -eq $property -or $property.Value -isnot [bool] -or -not $property.Value) { $terminationUnproven=$true }
    }
    if ($scratchCreated -and -not $scratchRemoved) {
        if (-not (Test-Path -LiteralPath $scratch)) { $cleanupFailure='SCRATCH_CLEANUP_UNPROVEN' }
        elseif ($terminationUnproven) { $cleanupFailure='PROCESS_TERMINATION_UNPROVEN' }
        else { try { Remove-OwnedScratch } catch { $cleanupFailure=Get-FailureErrorText $_ } }
    }
    if ($outputCreated) { Write-FailureSummary }
    try { Write-RejectionFrame } catch {
        $fallback=[ordered]@{code=[string]$failureCode;phase=[string]$phase;outputCreated=[bool]$outputCreated;scratchCreated=[bool]$scratchCreated;
            compilerInvoked=[bool]$compilerInvoked;nativeInvoked=[bool]$nativeInvoked;cleanupError=$(if($null -eq $cleanupFailure){$null}else{[string]$cleanupFailure})}
        $fallbackLine='P9_RESERVATION_HOST_REJECT '+(ConvertTo-Json -InputObject $fallback -Depth 8 -Compress)+"`r`n"
        if ($utf8.GetByteCount($fallbackLine) -le 65536) { [Console]::Error.Write($fallbackLine); [Console]::Error.Flush() }
    }
    Write-FailureEvidenceDiagnostic
    exit 1
}

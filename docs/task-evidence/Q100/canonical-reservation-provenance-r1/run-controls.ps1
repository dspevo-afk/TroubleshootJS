param(
    [Parameter(Mandatory=$true)][string]$RepositoryRoot,
    [Parameter(Mandatory=$true)][string]$PythonExe,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$InputPinsPath,
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{64}$')][string]$InputPinsSha256,
    [Parameter(Mandatory=$true)][string]$OutputRoot,
    [Parameter(Mandatory=$true)][ValidateSet('CANARY','SUITE')][string]$Mode
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:FullWatch = [Diagnostics.Stopwatch]::StartNew()
$script:Utf8NoBom = [Text.UTF8Encoding]::new($false)
$script:AllProcesses = New-Object System.Collections.ArrayList
$script:RetentionErrors = New-Object System.Collections.ArrayList
$script:InputBefore = $null
$script:InputPins = $null
$script:InputPinsBytes = $null
$script:InputInventoryBytes = $null
$script:P9Root = $null
$script:QRoot = $null
$script:EvidenceRoot = $null
$script:OutputBase = $null
$script:SourceSha256 = $null
$script:FailureCode = 'UNEXPECTED_FAILURE'
$script:FailurePhase = 'STARTUP'
$script:HostLimits = [ordered]@{
    compileMs = 60000; auditMs = 60000; nativeMs = 480000; readerMs = 180000
    outerMs = 1800000; cleanupMs = 120000; workNanos = 90000000000
    groups = 640; groupNanos = 5000000000; observationUnits = 32768
    cloneEntries = 1048576; chunkEntries = 4096; expansions = 1000000
    holes = 48; flips = 4; operationBytes = 8388608
    hostEvidenceBytes = 8388608; suiteBytes = 41943040; lineBytes = 65536
    compilerListBytes = 1048576; inventoryBytes = 1048576
}

$script:ExpectedProcessKeys = @(
    'FilePath','Arguments','ProcessId','ProcessStartTicks','ExitCode','Stdout','Stderr',
    'StdoutLog','StderrLog','ProcessRoot','TerminationProven'
)
$script:ExpectedReaderKeys = @(
    'schema','status','layoutQualification','scope','p9InputSha256','p9SourceSha256',
    'p8WireSha256','runs','target','slotComparison','contributorNetCount',
    'contributorPadCount','contributorSetSha256','p8Parity','sourceClosure','processes',
    'outputClosure','limits'
)
$script:ExpectedHostCompleteKeys = @(
    'schema','status','mode','suiteId','inputSha256','sourceSha256','wireSha256',
    'hostResult','suiteIndex','suiteFooter','layoutQualification'
)
$script:SourceControlCases = @(
    [pscustomobject]@{ Id='SRC01_PINS_DIGEST'; Code='P9_INPUT_PINS_HASH' },
    [pscustomobject]@{ Id='SRC02_SELF'; Code='P9_INPUT_SELF_PIN' },
    [pscustomobject]@{ Id='SRC03_LEAF_HASH'; Code='P9_INPUT_FILE_HASH' },
    [pscustomobject]@{ Id='SRC04_TOOL'; Code='P9_INPUT_TOOL_IDENTITY' },
    [pscustomobject]@{ Id='SRC05_ROOT_ALIAS'; Code='P9_INPUT_ROOT_BINDING' },
    [pscustomobject]@{ Id='SRC06_EXTRA_TOP'; Code='P9_INPUT_TOP_CENSUS' },
    [pscustomobject]@{ Id='SRC07_EXTRA_DERIVED'; Code='P9_INPUT_DERIVED_CENSUS' },
    [pscustomobject]@{ Id='SRC08_EDIT_SET'; Code='P9_OVERLAY_EDIT_SET' },
    [pscustomobject]@{ Id='SRC09_PRESERVED'; Code='P9_OVERLAY_PRESERVATION' },
    [pscustomobject]@{ Id='SRC10_COMPILE_SET'; Code='P9_INPUT_COMPILE_SET' },
    [pscustomobject]@{ Id='SRC11_TRANSITIVE'; Code='P9_INPUT_DEPENDENCY_CLOSURE' },
    [pscustomobject]@{ Id='SRC12_TOTALS'; Code='P9_INPUT_INVENTORY_ARITHMETIC' }
)
$script:ReaderControlCases = @(
    [pscustomobject]@{ Id='READER01_DIGEST'; Code='P9_INPUT_PINS_HASH' },
    [pscustomobject]@{ Id='READER02_ELAPSED_TYPE'; Code='PARENT_ELAPSED_TYPE_INVALID' },
    [pscustomobject]@{ Id='READER03_EXIT_TWO'; Code='PARENT_PROCESS_EXIT_INVALID' },
    [pscustomobject]@{ Id='READER04_TERMINATION'; Code='PARENT_PROCESS_TERMINATION_INVALID' },
    [pscustomobject]@{ Id='READER05_TIMING_ORDER'; Code='PARENT_TIMING_ORDER_INVALID' }
)
$script:HostControlCases = @(
    [pscustomobject]@{ Id='HOST01_DIGEST'; Code='P9_INPUT_PINS_HASH' },
    [pscustomobject]@{ Id='HOST02_SELF'; Code='P9_INPUT_SELF_PIN' },
    [pscustomobject]@{ Id='HOST03_EXTRA_PIN'; Code='P9_INPUT_FILE_HASH' },
    [pscustomobject]@{ Id='HOST04_EXISTING_OUTPUT'; Code='OUTPUT_EXISTS' },
    [pscustomobject]@{ Id='HOST05_SOURCE_OUTPUT'; Code='EXTERNAL_OUTPUT_REQUIRED' }
)

function Fail([string]$Code) {
    throw [InvalidOperationException]::new('P9C:' + $Code)
}

function Need([bool]$Condition, [string]$Code) {
    if (-not $Condition) { Fail $Code }
}

function Get-Sha256Bytes([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-','').ToLowerInvariant()
    } finally { $sha.Dispose() }
}

function Get-Sha256File([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Test-BytesEqual([byte[]]$Left, [byte[]]$Right) {
    if ($null -eq $Left -or $null -eq $Right -or $Left.Length -ne $Right.Length) { return $false }
    for ($i = 0; $i -lt $Left.Length; $i++) {
        if ($Left[$i] -ne $Right[$i]) { return $false }
    }
    return $true
}

function Get-ASCIIBytes([string]$Text, [string]$Code) {
    try {
        $ascii = [Text.Encoding]::GetEncoding(20127,
            [Text.EncoderExceptionFallback]::new(), [Text.DecoderExceptionFallback]::new())
        return ,($ascii.GetBytes($Text))
    } catch { Fail $Code }
}

function ConvertTo-CanonicalJsonText($Value, [bool]$PreservePropertyOrder = $false) {
    if ($null -eq $Value) { return 'null' }
    if ($Value -is [string]) {
        $sb = New-Object Text.StringBuilder
        [void]$sb.Append('"')
        foreach ($ch in $Value.ToCharArray()) {
            $n = [int][char]$ch
            if ($n -gt 127) { Fail 'NON_ASCII_JSON' }
            if ($n -eq 34) { [void]$sb.Append('\"') }
            elseif ($n -eq 92) { [void]$sb.Append('\\') }
            elseif ($n -eq 8) { [void]$sb.Append('\b') }
            elseif ($n -eq 9) { [void]$sb.Append('\t') }
            elseif ($n -eq 10) { [void]$sb.Append('\n') }
            elseif ($n -eq 12) { [void]$sb.Append('\f') }
            elseif ($n -eq 13) { [void]$sb.Append('\r') }
            elseif ($n -lt 32) { [void]$sb.Append(('\u{0:x4}' -f $n)) }
            else { [void]$sb.Append($ch) }
        }
        [void]$sb.Append('"')
        return $sb.ToString()
    }
    if ($Value -is [bool]) {
        if ($Value) { return 'true' }
        return 'false'
    }
    if ($Value -is [System.Collections.IDictionary]) {
        $keys = [string[]]@($Value.Keys | ForEach-Object { [string]$_ })
        if (-not $PreservePropertyOrder) { [Array]::Sort($keys,[StringComparer]::Ordinal) }
        $parts = New-Object System.Collections.Generic.List[string]
        foreach ($key in $keys) {
            $parts.Add((ConvertTo-CanonicalJsonText $key) + ':' +
                (ConvertTo-CanonicalJsonText $Value[$key] $PreservePropertyOrder))
        }
        return '{' + [string]::Join(',', $parts.ToArray()) + '}'
    }
    if ($Value -is [System.Management.Automation.PSCustomObject]) {
        $keys = [string[]]@($Value.PSObject.Properties | ForEach-Object { $_.Name })
        if (-not $PreservePropertyOrder) { [Array]::Sort($keys,[StringComparer]::Ordinal) }
        $parts = New-Object System.Collections.Generic.List[string]
        foreach ($key in $keys) {
            $parts.Add((ConvertTo-CanonicalJsonText $key) + ':' +
                (ConvertTo-CanonicalJsonText $Value.PSObject.Properties[$key].Value $PreservePropertyOrder))
        }
        return '{' + [string]::Join(',', $parts.ToArray()) + '}'
    }
    if ($Value -is [System.Array] -or
        ($Value -is [System.Collections.IList] -and -not ($Value -is [string]))) {
        $parts = New-Object System.Collections.Generic.List[string]
        foreach ($item in $Value) { $parts.Add((ConvertTo-CanonicalJsonText $item $PreservePropertyOrder)) }
        return '[' + [string]::Join(',', $parts.ToArray()) + ']'
    }
    if ($Value -is [byte] -or $Value -is [int16] -or $Value -is [int32] -or
        $Value -is [int64] -or $Value -is [uint16] -or $Value -is [uint32] -or
        $Value -is [uint64] -or $Value -is [decimal]) {
        return ([Convert]::ToString($Value,[Globalization.CultureInfo]::InvariantCulture))
    }
    if ($Value -is [double] -or $Value -is [single]) {
        if ([double]::IsNaN([double]$Value) -or [double]::IsInfinity([double]$Value)) {
            Fail 'NONFINITE_JSON_NUMBER'
        }
        $number = ([double]$Value).ToString('R',[Globalization.CultureInfo]::InvariantCulture)
        if ($number.IndexOf('.') -lt 0 -and $number.IndexOf('e') -lt 0 -and $number.IndexOf('E') -lt 0) {
            $number += '.0'
        }
        return $number
    }
    Fail 'UNSUPPORTED_JSON_VALUE'
}

function Get-CanonicalJsonBytes($Value, [bool]$FinalLf = $true) {
    $text = ConvertTo-CanonicalJsonText $Value
    if ($FinalLf) { $text += "`n" }
    return ,(Get-ASCIIBytes $text 'NON_ASCII_JSON')
}

function Assert-ExactKeys($Object, [string[]]$Expected, [string]$Code) {
    Need ($null -ne $Object -and $Object -is [System.Management.Automation.PSCustomObject]) $Code
    $actual = @($Object.PSObject.Properties | ForEach-Object { $_.Name })
    Need ($actual.Count -eq $Expected.Count) $Code
    $set = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
    foreach ($key in $actual) { [void]$set.Add([string]$key) }
    foreach ($key in $Expected) { Need $set.Contains($key) $Code }
}

function Assert-ExactOrderedKeys($Object, [string[]]$Expected, [string]$Code) {
    Need ($null -ne $Object -and $Object -is [System.Management.Automation.PSCustomObject]) $Code
    $actual = @($Object.PSObject.Properties | ForEach-Object { $_.Name })
    Need ($actual.Count -eq $Expected.Count) $Code
    for ($i = 0; $i -lt $Expected.Count; $i++) { Need ($actual[$i] -ceq $Expected[$i]) $Code }
}

function Test-PathWithin([string]$Root, [string]$Candidate) {
    $r = [IO.Path]::GetFullPath($Root).TrimEnd('\')
    $c = [IO.Path]::GetFullPath($Candidate)
    return $c.StartsWith($r + '\',[StringComparison]::OrdinalIgnoreCase)
}

function Test-PathOverlap([string]$Left, [string]$Right) {
    $l = [IO.Path]::GetFullPath($Left).TrimEnd('\')
    $r = [IO.Path]::GetFullPath($Right).TrimEnd('\')
    return ($l.Equals($r,[StringComparison]::OrdinalIgnoreCase) -or
        (Test-PathWithin $l $r) -or (Test-PathWithin $r $l))
}

function Get-RelativeOutputPath([string]$Path) {
    $full = [IO.Path]::GetFullPath($Path)
    Need (Test-PathWithin $script:OutputBase $full) 'OUTPUT_EVIDENCE_ESCAPE'
    return $full.Substring($script:OutputBase.Length + 1).Replace('\','/')
}

function Assert-CanonicalExisting([string]$Path, [bool]$Directory, [string]$Code) {
    Need ([IO.Path]::IsPathRooted($Path)) $Code
    $full = [IO.Path]::GetFullPath($Path)
    Need ($full -ceq $Path) $Code
    if ($Directory) { Need (Test-Path -LiteralPath $full -PathType Container) $Code }
    else { Need (Test-Path -LiteralPath $full -PathType Leaf) $Code }
    Assert-VerifierNoReparseAncestors $full
    return $full
}

function Read-StableFile([string]$Path, [long]$ExpectedBytes = -1) {
    Assert-VerifierNoReparseAncestors $Path
    $before = Get-Item -LiteralPath $Path -Force
    Need (-not ($before.Attributes -band [IO.FileAttributes]::ReparsePoint)) 'INPUT_REPARSE'
    Need ($before -is [IO.FileInfo]) 'INPUT_NOT_FILE'
    if ($ExpectedBytes -ge 0) { Need ([long]$before.Length -eq $ExpectedBytes) 'INPUT_SIZE_CHANGED' }
    $bytes = [IO.File]::ReadAllBytes($Path)
    $after = Get-Item -LiteralPath $Path -Force
    Need ([long]$before.Length -eq [long]$after.Length -and
          $before.LastWriteTimeUtc.Ticks -eq $after.LastWriteTimeUtc.Ticks -and
          $before.CreationTimeUtc.Ticks -eq $after.CreationTimeUtc.Ticks -and
          $before.Attributes -eq $after.Attributes) 'INPUT_CHANGED_DURING_READ'
    Need ($bytes.Length -eq [long]$after.Length) 'INPUT_CHANGED_DURING_READ'
    return ,$bytes
}

function Write-CreateNew([string]$Path, [byte[]]$Bytes) {
    Need (-not (Test-Path -LiteralPath $Path)) 'EVIDENCE_COLLISION'
    Assert-VerifierNoReparseAncestors (Split-Path -Parent $Path)
    $stream = [IO.File]::Open($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try { $stream.Write($Bytes,0,$Bytes.Length); $stream.Flush($true) }
    finally { $stream.Dispose() }
}

function Write-JsonCreateNew([string]$Path, $Object) {
    Write-CreateNew $Path (Get-CanonicalJsonBytes $Object)
}

function Write-OrderedJsonCreateNew([string]$Path, $Object) {
    $text = ConvertTo-CanonicalJsonText $Object $true
    $bytes = Get-ASCIIBytes ($text + "`n") 'ORDERED_JSON_ASCII'
    Need ($bytes.Length -le 65536) 'ORDERED_JSON_CAP'
    Write-CreateNew $Path $bytes
}

function Write-Utf8JsonCreateNew([string]$Path, $Object) {
    $raw = ConvertTo-Json -InputObject $Object -Depth 100 -Compress -ErrorAction Stop -WarningAction Stop
    $bytes = $script:Utf8NoBom.GetBytes($raw + "`n")
    Write-CreateNew $Path $bytes
}

function Get-ReturnedObjectProperty($Object, [string]$Name) {
    if ($null -eq $Object) { return [pscustomobject]@{present=$false;value=$null} }
    if ($Object -is [System.Collections.IDictionary]) {
        if ($Object.Contains($Name)) { return [pscustomobject]@{present=$true;value=$Object[$Name]} }
        return [pscustomobject]@{present=$false;value=$null}
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return [pscustomobject]@{present=$false;value=$null} }
    return [pscustomobject]@{present=$true;value=$property.Value}
}

function Get-ExceptionDataSnapshot($Exception) {
    $rows = New-Object System.Collections.ArrayList
    if ($null -ne $Exception) {
        try {
            foreach ($key in $Exception.Data.Keys) {
                [void]$rows.Add([ordered]@{key=[string]$key;value=$Exception.Data[$key]})
            }
        } catch {
            [void]$rows.Add([ordered]@{captureError=[string]$_.ToString()})
        }
    }
    return ,$rows.ToArray()
}

function Get-ErrorRecordSnapshot($ErrorRecord) {
    if ($null -eq $ErrorRecord) { return $null }
    $exception = $ErrorRecord.Exception
    return [ordered]@{
        type=$ErrorRecord.GetType().FullName
        fullyQualifiedErrorId=[string]$ErrorRecord.FullyQualifiedErrorId
        categoryInfo=[string]$ErrorRecord.CategoryInfo
        errorRecordText=[string]$ErrorRecord.ToString()
        exceptionType=$(if ($null -ne $exception) { $exception.GetType().FullName } else { $null })
        exceptionMessage=$(if ($null -ne $exception) { [string]$exception.Message } else { $null })
        scriptStackTrace=[string]$ErrorRecord.ScriptStackTrace
        exceptionData=(Get-ExceptionDataSnapshot $exception)
    }
}

function Record-RetentionError([string]$Name, [string]$Artifact, [string]$Directory,
                               $ActualErrorRecord, $RetentionErrorRecord,
                               $ReturnedProcess, [bool]$ReturnedValueObserved,
                               $AttemptedCall) {
    $entry = [ordered]@{
        schema='tsj-q100-reservation-retention-error-v1'; status='FAIL_PROCESS_EVIDENCE_RETENTION'
        name=$Name; phase=$script:FailurePhase; artifact=$Artifact
        actualErrorRecord=(Get-ErrorRecordSnapshot $ActualErrorRecord)
        exceptionData=(Get-ExceptionDataSnapshot $(if ($null -ne $ActualErrorRecord) { $ActualErrorRecord.Exception } else { $null }))
        retentionErrorRecord=(Get-ErrorRecordSnapshot $RetentionErrorRecord)
        retentionExceptionData=(Get-ExceptionDataSnapshot $(if ($null -ne $RetentionErrorRecord) { $RetentionErrorRecord.Exception } else { $null }))
        returnedValueObserved=$ReturnedValueObserved
        returnedProcessType=$(if ($ReturnedValueObserved -and $null -ne $ReturnedProcess) { $ReturnedProcess.GetType().FullName } else { $null })
        returnedProcess=$ReturnedProcess; returnedProcessOmittedDueToCaptureFailure=$false
        attemptedCall=$AttemptedCall
        retentionErrorPath=$null
    }
    [void]$script:RetentionErrors.Add($entry)
    $candidate = $null
    if ($Directory -and (Test-Path -LiteralPath $Directory -PathType Container)) {
        $candidate = Join-Path $Directory ('retention-error-' + [Guid]::NewGuid().ToString('N') + '.json')
    } elseif ($script:EvidenceRoot -and (Test-Path -LiteralPath $script:EvidenceRoot -PathType Container)) {
        $fallbackDirectory = Join-Path $script:EvidenceRoot 'retention-errors'
        try {
            if (-not (Test-Path -LiteralPath $fallbackDirectory)) { [void][IO.Directory]::CreateDirectory($fallbackDirectory) }
            Assert-VerifierNoReparseAncestors $fallbackDirectory
            $candidate = Join-Path $fallbackDirectory ($Name + '-' + [Guid]::NewGuid().ToString('N') + '.json')
        } catch {
            $entry.retentionErrorPath = $null
            $entry.retentionWriteError = Get-ErrorRecordSnapshot $_
            throw [InvalidOperationException]::new('P9C:PROCESS_RETENTION_FAILED')
        }
    }
    if ($null -eq $candidate) {
        $entry.retentionWriteError = [ordered]@{message='No owned evidence directory was available'}
        throw [InvalidOperationException]::new('P9C:PROCESS_RETENTION_FAILED')
    }
    $entry.retentionErrorPath = $candidate
    try {
        Write-Utf8JsonCreateNew $candidate $entry
    } catch {
        $entry.retentionWriteError = Get-ErrorRecordSnapshot $_
        if ($script:EvidenceRoot -and (Test-Path -LiteralPath $script:EvidenceRoot -PathType Container)) {
            try {
                $fallbackDirectory = Join-Path $script:EvidenceRoot 'retention-errors'
                if (-not (Test-Path -LiteralPath $fallbackDirectory)) { [void][IO.Directory]::CreateDirectory($fallbackDirectory) }
                Assert-VerifierNoReparseAncestors $fallbackDirectory
                $candidate = Join-Path $fallbackDirectory ($Name + '-' + [Guid]::NewGuid().ToString('N') + '.json')
                $entry.retentionErrorPath = $candidate
                $entry.returnedProcess = $null
                $entry.returnedProcessOmittedDueToCaptureFailure = $ReturnedValueObserved
                Write-Utf8JsonCreateNew $candidate $entry
            } catch {
                $entry.fallbackRetentionWriteError = Get-ErrorRecordSnapshot $_
                throw [InvalidOperationException]::new('P9C:PROCESS_RETENTION_FAILED')
            }
        } else {
            throw [InvalidOperationException]::new('P9C:PROCESS_RETENTION_FAILED')
        }
    }
    return $candidate
}

function Write-ProcessEvidence([string]$Name, $Process, [double]$WallMs, [string]$Directory) {
    $receiptPath = Join-Path $Directory 'process.json'
    try {
        if ($null -eq $Process) { $rawJson = 'null' }
        else { $rawJson = ConvertTo-Json -InputObject $Process -Depth 100 -Compress -ErrorAction Stop -WarningAction Stop }
        $receiptBytes = $script:Utf8NoBom.GetBytes($rawJson + "`n")
        $ref = [ordered]@{
            path = Get-RelativeOutputPath $receiptPath
            bytes = $receiptBytes.Length
            sha256 = Get-Sha256Bytes $receiptBytes
        }
        Write-CreateNew $receiptPath $receiptBytes
    } catch {
        $captureError = $_
        $attempt = [ordered]@{ name=$Name; wallMs=$WallMs; receiptPath=$receiptPath }
        [void](Record-RetentionError -Name $Name -Artifact 'process.json' -Directory $Directory `
            -ActualErrorRecord $captureError -RetentionErrorRecord $null -ReturnedProcess $Process `
            -ReturnedValueObserved $true -AttemptedCall $attempt)
        Fail 'PROCESS_RECEIPT_RETENTION_FAILED'
    }
    $record = [pscustomobject]@{ name=$Name; receipt=$Process; wallMs=$WallMs; ref=$ref;
        stdoutCaptured=$false; stderrCaptured=$false }
    [void]$script:AllProcesses.Add($record)

    $stdout = Get-ReturnedObjectProperty $Process 'Stdout'
    $stderr = Get-ReturnedObjectProperty $Process 'Stderr'
    try {
        if ($stdout.present -and $stdout.value -is [string]) {
            Write-CreateNew (Join-Path $Directory 'stdout.utf8') ($script:Utf8NoBom.GetBytes($stdout.value))
            $record.stdoutCaptured = $true
        }
        if ($stderr.present -and $stderr.value -is [string]) {
            Write-CreateNew (Join-Path $Directory 'stderr.utf8') ($script:Utf8NoBom.GetBytes($stderr.value))
            $record.stderrCaptured = $true
        }
    } catch {
        $captureError = $_
        $artifact = if (-not $record.stdoutCaptured) { 'stdout.utf8' } else { 'stderr.utf8' }
        $attempt = [ordered]@{ name=$Name; wallMs=$WallMs; receiptPath=$receiptPath;
            stdoutCaptured=$record.stdoutCaptured; stderrCaptured=$record.stderrCaptured }
        [void](Record-RetentionError -Name $Name -Artifact $artifact -Directory $Directory `
            -ActualErrorRecord $captureError -RetentionErrorRecord $null -ReturnedProcess $Process `
            -ReturnedValueObserved $true -AttemptedCall $attempt)
        Fail 'PROCESS_STREAM_RETENTION_FAILED'
    }

    Assert-ExactOrderedKeys $Process $script:ExpectedProcessKeys 'PROCESS_RECEIPT_KEYS'
    Need ($Process.FilePath -is [string] -and $Process.Arguments -is [array] -and
          $Process.ProcessId -is [int] -and $Process.ProcessStartTicks -is [long] -and
          $Process.ExitCode -is [int] -and $Process.Stdout -is [string] -and
          $Process.Stderr -is [string] -and $Process.StdoutLog -is [string] -and
          $Process.StderrLog -is [string] -and $Process.ProcessRoot -is [string] -and
          $Process.TerminationProven -is [bool]) 'PROCESS_RECEIPT_TYPES'
    Need ($record.stdoutCaptured -and $record.stderrCaptured) 'PROCESS_STREAM_CAPTURE'
    Need ($Process.ProcessId -gt 0 -and $Process.ProcessStartTicks -gt 0 -and
          $Process.TerminationProven) 'PROCESS_TERMINATION'
    Need (-not (Test-Path -LiteralPath $Process.ProcessRoot) -and
          -not (Test-Path -LiteralPath $Process.StdoutLog) -and
          -not (Test-Path -LiteralPath $Process.StderrLog)) 'PROCESS_ROOT_REMAINS'
    Need (-not [double]::IsNaN($WallMs) -and -not [double]::IsInfinity($WallMs) -and
          $WallMs -gt 0) 'PROCESS_WALL_INVALID'
    return $record
}

function Invoke-Tracked([string]$Name, [string]$Executable, [string[]]$Arguments,
                        [int]$LimitMs, [string]$Directory) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $process = $null
    try {
        $process = Invoke-VerifierBoundedProcess $Executable @($Arguments) $LimitMs
    } catch {
        $invokeError = $_
        $watch.Stop()
        $exceptionData = Get-ExceptionDataSnapshot $invokeError.Exception
        $attempt = [ordered]@{
            schema='tsj-q100-reservation-process-attempt-v1'; name=$Name
            executable=$Executable; arguments=@($Arguments); limitMs=$LimitMs
            wallMs=$watch.Elapsed.TotalMilliseconds; process=$null
            terminationProven=$false; error=$invokeError.Exception.Message; exceptionData=$exceptionData
        }
        try {
            Need (Test-Path -LiteralPath $Directory -PathType Container) 'PROCESS_ATTEMPT_DIRECTORY'
            Write-JsonCreateNew (Join-Path $Directory 'attempt.json') $attempt
        } catch {
            $retentionError = $_
            [void](Record-RetentionError -Name $Name -Artifact 'attempt.json' -Directory $Directory `
                -ActualErrorRecord $invokeError -RetentionErrorRecord $retentionError -ReturnedProcess $null `
                -ReturnedValueObserved $false -AttemptedCall $attempt)
            Fail 'PROCESS_ATTEMPT_RETENTION_FAILED'
        }
        Fail 'PROCESS_INVOCATION_FAILED'
    }
    $watch.Stop()
    $record = Write-ProcessEvidence $Name $process $watch.Elapsed.TotalMilliseconds $Directory
    return $record
}

function New-ChildSummary([string]$Name, [int]$ExpectedExit, $Record,
                          [string]$ExpectedCode = $null, [string]$ActualCode = $null) {
    return [ordered]@{
        name=$Name; expectedExit=$ExpectedExit; actualExit=$Record.receipt.ExitCode
        expectedCode=$ExpectedCode; actualCode=$ActualCode; process=$Record.ref
        wallMs=$Record.wallMs; terminationProven=[bool]$Record.receipt.TerminationProven
    }
}

function Split-OneFrame([string]$Text, [string]$Prefix, [bool]$AllowCrLf,
                        [string]$Code, [int]$MaxBytes) {
    Need ($Text -is [string] -and $Text.Length -gt 0 -and $Text.EndsWith("`n") -and
          $Text.LastIndexOf("`n") -eq ($Text.Length - 1)) $Code
    Need ((Get-ASCIIBytes $Text $Code).Length -le $MaxBytes) $Code
    $line = $Text.Substring(0,$Text.Length - 1)
    if ($AllowCrLf -and $line.EndsWith("`r")) { $line = $line.Substring(0,$line.Length - 1) }
    Need (-not $line.Contains("`r") -and -not $line.Contains("`n") -and
          $line.StartsWith($Prefix,[StringComparison]::Ordinal)) $Code
    Need ((Get-ASCIIBytes $line $Code).Length -le $MaxBytes) $Code
    return $line.Substring($Prefix.Length)
}

function Parse-OneJsonFrame([string]$Text, [string]$Prefix, [bool]$AllowCrLf,
                            [string]$Code, [int]$MaxBytes) {
    $payload = Split-OneFrame $Text $Prefix $AllowCrLf $Code $MaxBytes
    try { return ($payload | ConvertFrom-Json -ErrorAction Stop) }
    catch { Fail $Code }
}

function Get-SingleRejectCode([string]$Stderr, [string]$Prefix, [string]$Code) {
    Need ($Stderr -is [string] -and $Stderr.EndsWith("`n") -and
          $Stderr.LastIndexOf("`n") -eq ($Stderr.Length - 1) -and
          ([Text.Encoding]::ASCII.GetBytes($Stderr)).Length -le 65536) $Code
    $line = $Stderr.Substring(0,$Stderr.Length - 1)
    Need ($line.StartsWith($Prefix,[StringComparison]::Ordinal) -and
          -not $line.Contains("`r") -and -not $line.Contains("`n")) $Code
    return $line.Substring($Prefix.Length)
}

function Get-PinRow([object[]]$Rows, [string]$Path, [string]$Code) {
    $matches = @($Rows | Where-Object { $_.path -ceq $Path })
    Need ($matches.Count -eq 1) $Code
    return $matches[0]
}

function Sort-PinFileRows([object[]]$Rows) {
    $items = New-Object System.Collections.ArrayList
    foreach ($row in $Rows) { [void]$items.Add($row) }
    for ($i=1; $i -lt $items.Count; $i++) {
        $value = $items[$i]
        $j = $i - 1
        while ($j -ge 0) {
            $left = ([string]$items[$j].path).ToLowerInvariant()
            $right = ([string]$value.path).ToLowerInvariant()
            $cmp = [StringComparer]::Ordinal.Compare($left,$right)
            if ($cmp -eq 0) { $cmp = [StringComparer]::Ordinal.Compare([string]$items[$j].path,[string]$value.path) }
            if ($cmp -le 0) { break }
            $items[$j+1] = $items[$j]
            $j--
        }
        $items[$j+1] = $value
    }
    return ,$items.ToArray()
}

function Get-InventoryModel([byte[]]$Bytes, [string]$Code) {
    Need ($Bytes.Length -gt 0 -and $Bytes.Length -le 1048576 -and
          $Bytes[0] -ne 0xef -and $Bytes[0] -ne 0xff -and $Bytes[0] -ne 0xfe) $Code
    Need ($Bytes[$Bytes.Length-1] -eq 10) $Code
    try {
        $ascii = [Text.Encoding]::GetEncoding(20127,
            [Text.EncoderExceptionFallback]::new(), [Text.DecoderExceptionFallback]::new())
        $text = $ascii.GetString($Bytes)
    } catch { Fail $Code }
    Need (-not $text.Contains("`r")) $Code
    $lines = $text.Split([char]10)
    Need ($lines.Length -ge 3 -and $lines[$lines.Length-1] -ceq '') $Code
    $objects = New-Object System.Collections.ArrayList
    for ($i=0; $i -lt ($lines.Length-1); $i++) {
        Need ($lines[$i].Length -gt 0 -and
              ([Text.Encoding]::ASCII.GetByteCount($lines[$i]) + 1 -le 65536)) $Code
        try { [void]$objects.Add(($lines[$i] | ConvertFrom-Json -ErrorAction Stop)) }
        catch { Fail $Code }
    }
    Need ($objects.Count -ge 2 -and $objects[0].kind -ceq 'header' -and
          $objects[$objects.Count-1].kind -ceq 'footer') $Code
    $rows = New-Object System.Collections.ArrayList
    for ($i=1; $i -lt ($objects.Count-1); $i++) { [void]$rows.Add($objects[$i]) }
    return [pscustomobject]@{ header=$objects[0]; rows=$rows; footer=$objects[$objects.Count-1] }
}

function New-InventoryBytes($Model, [string]$Code) {
    $counts = [ordered]@{ existing=[long]0; direct=[long]0; derived=[long]0; total=[long]0 }
    $bytes = [ordered]@{ existing=[long]0; direct=[long]0; derived=[long]0; total=[long]0 }
    foreach ($row in $Model.rows) {
        Need ($row.bytes -is [long] -or $row.bytes -is [int]) $Code
        Need ([long]$row.bytes -ge 0) $Code
        if ($row.kind -ceq 'direct') { $counts.direct++; $bytes.direct += [long]$row.bytes }
        elseif ($row.kind -ceq 'derived') { $counts.derived++; $bytes.derived += [long]$row.bytes }
        else { $counts.existing++; $bytes.existing += [long]$row.bytes }
    }
    $counts.total = $counts.existing + $counts.direct + $counts.derived
    $bytes.total = $bytes.existing + $bytes.direct + $bytes.derived
    $header = [ordered]@{
        kind='header'; schema='tsj-q100-reservation-provenance-source-v1'
        sourceParent='4623f1e5680503527a4423c2ca31fb09ede3cbb8'
        roots=$Model.header.roots; rowCounts=$counts; byteCounts=$bytes
    }
    $footer = [ordered]@{ kind='footer'; rowCounts=$counts; byteCounts=$bytes }
    $sb = New-Object Text.StringBuilder
    [void]$sb.Append((ConvertTo-CanonicalJsonText $header)).Append("`n")
    foreach ($row in $Model.rows) { [void]$sb.Append((ConvertTo-CanonicalJsonText $row)).Append("`n") }
    [void]$sb.Append((ConvertTo-CanonicalJsonText $footer)).Append("`n")
    $result = Get-ASCIIBytes $sb.ToString() $Code
    Need ($result.Length -le 1048576) $Code
    return ,$result
}

function Set-PinInventoryReference($Pins, [string]$InventoryPath, [byte[]]$InventoryBytes) {
    $sha = Get-Sha256Bytes $InventoryBytes
    $oldPath = [string]$Pins.sourceInventory.path
    $row = Get-PinRow @($Pins.files) $oldPath 'PINS_INVENTORY_ROW'
    $Pins.sourceInventory.path = $InventoryPath
    $Pins.sourceInventory.sha256 = $sha
    $row.path = $InventoryPath
    $row.bytes = [long]$InventoryBytes.Length
    $row.sha256 = $sha
    $Pins.files = Sort-PinFileRows @($Pins.files)
}

function Write-SyntheticPins([string]$Path, $Pins) {
    $bytes = Get-CanonicalJsonBytes $Pins
    Need ($bytes.Length -le 4194304) 'SYNTHETIC_PINS_CAP'
    Write-CreateNew $Path $bytes
    return (Get-Sha256Bytes $bytes)
}

function Get-InventoryRowForAbsolutePath($Model, [string]$AbsolutePath, [string]$Code) {
    $full = [IO.Path]::GetFullPath($AbsolutePath)
    $matches = New-Object System.Collections.ArrayList
    foreach ($rootProp in $Model.header.roots.PSObject.Properties) {
        $rootName = $rootProp.Name
        $rootPath = [string]$rootProp.Value
        if (-not (Test-PathWithin $rootPath $full)) { continue }
        $rel = $full.Substring([IO.Path]::GetFullPath($rootPath).TrimEnd('\').Length + 1).Replace('\','/')
        foreach ($row in $Model.rows) {
            if ($row.origin -ceq $rootName -and $row.path -ceq $rel) { [void]$matches.Add($row) }
        }
    }
    Need ($matches.Count -eq 1) $Code
    return $matches[0]
}

function Set-InventoryMetadata($Model, [string]$AbsolutePath, [long]$Bytes,
                               [string]$Sha256, [string]$Code) {
    $row = Get-InventoryRowForAbsolutePath $Model $AbsolutePath $Code
    Need ($row.sha256 -is [string] -and ($row.bytes -is [long] -or $row.bytes -is [int])) $Code
    $row.bytes = $Bytes
    $row.sha256 = $Sha256
}

function Set-TopFileMetadata($Pins, [string]$Path, [long]$Bytes,
                             [string]$Sha256, [string]$Code) {
    $row = Get-PinRow @($Pins.files) $Path $Code
    $row.bytes = $Bytes
    $row.sha256 = $Sha256
}

function Remap-P9AbsolutePath([string]$Path, [string]$OldRoot, [string]$NewRoot) {
    $oldFull = [IO.Path]::GetFullPath($OldRoot).TrimEnd('\')
    $candidate = [IO.Path]::GetFullPath($Path)
    if ($candidate.Equals($oldFull,[StringComparison]::OrdinalIgnoreCase)) { return $NewRoot }
    if (Test-PathWithin $oldFull $candidate) {
        $relative = $candidate.Substring($oldFull.Length + 1)
        return [IO.Path]::GetFullPath((Join-Path $NewRoot $relative))
    }
    return $Path
}

function Clone-P9Root($Pins, $Model, [string]$CloneParent, [string]$Code) {
    $original = [IO.Path]::GetFullPath((Split-Path -Parent ([string]$Pins.roles.input.auditor)))
    Need ($original -ceq [IO.Path]::GetFullPath([string]$Model.header.roots.P9)) $Code
    $newRoot = Join-Path $CloneParent 'canonical-reservation-provenance-r1'
    Need (-not (Test-Path -LiteralPath $newRoot)) $Code
    [void][IO.Directory]::CreateDirectory($newRoot)
    Assert-VerifierNoReparseAncestors $newRoot
    $rows = @($Model.rows | Where-Object { $_.origin -ceq 'P9' -and
        ($_.kind -ceq 'direct' -or $_.kind -ceq 'derived') })
    Need ($rows.Count -eq 19) $Code
    foreach ($row in $rows) {
        Need ([string]$row.path -cmatch '^[A-Za-z0-9._/-]+$' -and -not ([string]$row.path).StartsWith('/')) $Code
        $relative = ([string]$row.path).Replace('/','\')
        Need (@($relative.Split('\') | Where-Object { $_ -ceq '..' -or $_ -ceq '.' }).Count -eq 0) $Code
        $source = Join-Path $original $relative
        $target = Join-Path $newRoot $relative
        Assert-VerifierNoReparseAncestors $source
        $bytes = Read-StableFile $source ([long]$row.bytes)
        Need ((Get-Sha256Bytes $bytes) -ceq [string]$row.sha256) $Code
        $parent = Split-Path -Parent $target
        [void][IO.Directory]::CreateDirectory($parent)
        Assert-VerifierNoReparseAncestors $parent
        Write-CreateNew $target $bytes
    }
    foreach ($group in @($Pins.roles.native,$Pins.roles.process,$Pins.roles.input)) {
        foreach ($property in @($group.PSObject.Properties)) {
            if ($property.Value -is [string]) {
                $property.Value = Remap-P9AbsolutePath ([string]$property.Value) $original $newRoot
            }
        }
    }
    foreach ($file in @($Pins.files)) {
        $file.path = Remap-P9AbsolutePath ([string]$file.path) $original $newRoot
    }
    $Pins.files = Sort-PinFileRows @($Pins.files)
    $Model.header.roots.P9 = [IO.Path]::GetFullPath($newRoot)
    return [IO.Path]::GetFullPath($newRoot)
}

function Set-SourceControlMutation([string]$Id, $Pins, $Model, [string]$P9Root,
                                   [bool]$WasCloned, [string]$CaseDir,
                                   [string]$Code) {
    switch -CaseSensitive ($Id) {
        'SRC02_SELF' {
            $target = [string]$Pins.roles.input.auditor
            Set-TopFileMetadata $Pins $target ((Get-PinRow @($Pins.files) $target $Code).bytes) ('0' * 64) $Code
            Set-InventoryMetadata $Model $target ((Get-InventoryRowForAbsolutePath $Model $target $Code).bytes) ('0' * 64) $Code
        }
        'SRC03_LEAF_HASH' {
            $target = Join-Path $P9Root 'PLAN_R1.md'
            Set-TopFileMetadata $Pins $target ((Get-PinRow @($Pins.files) $target $Code).bytes) ('0' * 64) $Code
            Set-InventoryMetadata $Model $target ((Get-InventoryRowForAbsolutePath $Model $target $Code).bytes) ('0' * 64) $Code
        }
        'SRC04_TOOL' {
            $target = [string]$Pins.toolPaths.pythonExe
            $row = Get-PinRow @($Pins.files) $target $Code
            Set-TopFileMetadata $Pins $target ([long]$row.bytes) ('0' * 64) $Code
            $inventoryRow = Get-InventoryRowForAbsolutePath $Model $target $Code
            Set-InventoryMetadata $Model $target ([long]$inventoryRow.bytes) ('0' * 64) $Code
        }
        'SRC05_ROOT_ALIAS' {
            $Model.header.roots.P9 = ([IO.Path]::GetFullPath($Model.header.roots.P9) + '\.')
        }
        'SRC06_EXTRA_TOP' {
            $derived = @($Model.rows | Where-Object { $_.origin -ceq 'P9' -and
                $_.kind -ceq 'derived' -and $_.path -ceq 'overlays-r1/overlay-manifest.json' })
            Need ($derived.Count -eq 1) $Code
            $extra = [pscustomobject]@{
                path=(Join-Path $P9Root 'overlays-r1\overlay-manifest.json')
                bytes=[long]$derived[0].bytes; sha256=[string]$derived[0].sha256
            }
            $Pins.files = Sort-PinFileRows (@($Pins.files) + @($extra))
        }
        'SRC07_EXTRA_DERIVED' {
            $path = Join-Path $P9Root 'overlays-r1\overlay-manifest.json'
            $raw = Read-StableFile $path
            $proof = ConvertFrom-Json -InputObject ([Text.Encoding]::ASCII.GetString($raw).TrimEnd("`n")) -ErrorAction Stop
            Need ($proof.outputs.Count -eq 1) $Code
            $proof.outputs = @($proof.outputs) + @($proof.outputs[0])
            $changed = Get-CanonicalJsonBytes $proof
            [IO.File]::WriteAllBytes($path,$changed)
            Set-InventoryMetadata $Model $path $changed.Length (Get-Sha256Bytes $changed) $Code
        }
        'SRC08_EDIT_SET' {
            $path = Join-Path $P9Root 'overlays-r1\overlay-manifest.json'
            $raw = Read-StableFile $path
            $proof = ConvertFrom-Json -InputObject ([Text.Encoding]::ASCII.GetString($raw).TrimEnd("`n")) -ErrorAction Stop
            Need ($proof.edits.Count -eq 3) $Code
            $proof.edits[0].id = 'NOT_AN_ALLOWED_EDIT'
            $changed = Get-CanonicalJsonBytes $proof
            [IO.File]::WriteAllBytes($path,$changed)
            Set-InventoryMetadata $Model $path $changed.Length (Get-Sha256Bytes $changed) $Code
        }
        'SRC09_PRESERVED' {
            $path = Join-Path $P9Root 'overlays-r1\overlay-manifest.json'
            $raw = Read-StableFile $path
            $proof = ConvertFrom-Json -InputObject ([Text.Encoding]::ASCII.GetString($raw).TrimEnd("`n")) -ErrorAction Stop
            Need ($proof.preserved.Count -gt 0) $Code
            $proof.preserved[0].sha256 = '0' * 64
            $changed = Get-CanonicalJsonBytes $proof
            [IO.File]::WriteAllBytes($path,$changed)
            Set-InventoryMetadata $Model $path $changed.Length (Get-Sha256Bytes $changed) $Code
        }
        'SRC10_COMPILE_SET' {
            $path = Join-Path $P9Root 'source\CanonicalReservationProvenanceCanary.java'
            $raw = Read-StableFile $path
            $text = [Text.Encoding]::UTF8.GetString($raw)
            $pattern = '(?m)^public\s+final\s+class\s+CanonicalReservationProvenanceCanary\b'
            $matches = [regex]::Matches($text,$pattern)
            Need ($matches.Count -eq 1) $Code
            $changedText = [regex]::Replace($text,$pattern,
                'public final class CanonicalReservationProvenanceStudy',1)
            $changed = $script:Utf8NoBom.GetBytes($changedText)
            [IO.File]::WriteAllBytes($path,$changed)
            Set-TopFileMetadata $Pins $path $changed.Length (Get-Sha256Bytes $changed) $Code
            Set-InventoryMetadata $Model $path $changed.Length (Get-Sha256Bytes $changed) $Code
        }
        'SRC11_TRANSITIVE' {
            $found = $false
            for ($i=0; $i -lt $Model.rows.Count; $i++) {
                if ($Model.rows[$i].origin -ceq 'E' -and $Model.rows[$i].kind -ceq 'source') {
                    $Model.rows.RemoveAt($i); $found=$true; break
                }
            }
            Need $found $Code
        }
        'SRC12_TOTALS' { }
        default { Fail 'SOURCE_CONTROL_ID' }
    }
}

function New-SourceFixture([string]$Id, [string]$CaseDir, [string]$CloneParent) {
    $needClone = $Id -in @('SRC07_EXTRA_DERIVED','SRC08_EDIT_SET','SRC09_PRESERVED','SRC10_COMPILE_SET')
    $pins = ConvertFrom-Json -InputObject ([Text.Encoding]::ASCII.GetString($script:InputPinsBytes).TrimEnd("`n")) -ErrorAction Stop
    $inventoryPath = [string]$pins.sourceInventory.path
    $inventoryRaw = Read-StableFile $inventoryPath ([long](Get-PinRow @($pins.files) $inventoryPath 'PINS_INVENTORY_ROW').bytes)
    Need ((Get-Sha256Bytes $inventoryRaw) -ceq [string]$pins.sourceInventory.sha256) 'SOURCE_FIXTURE_BASE_INVENTORY'
    $model = Get-InventoryModel $inventoryRaw 'SOURCE_FIXTURE_BASE_INVENTORY'
    $canonicalBase = New-InventoryBytes $model 'SOURCE_FIXTURE_BASE_INVENTORY'
    Need (Test-BytesEqual $canonicalBase $inventoryRaw) 'SOURCE_FIXTURE_BASE_INVENTORY_CANONICAL'
    $p9Root = [IO.Path]::GetFullPath((Split-Path -Parent ([string]$pins.roles.input.auditor)))
    if ($needClone) {
        Need ([IO.Path]::GetFullPath($CaseDir) -ne [IO.Path]::GetFullPath($CloneParent)) 'SOURCE_CLONE_PARENT'
        Need (-not (Test-PathOverlap $CaseDir $CloneParent)) 'SOURCE_CLONE_PARENT'
        [void][IO.Directory]::CreateDirectory($CloneParent)
        Assert-VerifierNoReparseAncestors $CloneParent
        $p9Root = Clone-P9Root $pins $model $CloneParent 'SOURCE_CLONE'
    }
    Set-SourceControlMutation $Id $pins $model $p9Root $needClone $CaseDir 'SOURCE_MUTATION'
    $newInventoryPath = Join-Path $CaseDir 'native-source-inventory.jsonl'
    $inventoryBytes = New-InventoryBytes $model 'SOURCE_INVENTORY_BUILD'
    if ($Id -ceq 'SRC12_TOTALS') {
        $tampered = Get-InventoryModel $inventoryBytes 'SOURCE_TOTALS_FIXTURE'
        $tampered.header.byteCounts.existing = [long]$tampered.header.byteCounts.existing + 1
        $inventoryBytes = Get-ASCIIBytes ((ConvertTo-CanonicalJsonText $tampered.header) + "`n" +
            [string]::Join("`n",@($tampered.rows | ForEach-Object { ConvertTo-CanonicalJsonText $_ })) + "`n" +
            (ConvertTo-CanonicalJsonText $tampered.footer) + "`n") 'SOURCE_TOTALS_FIXTURE'
    }
    Write-CreateNew $newInventoryPath $inventoryBytes
    Set-PinInventoryReference $pins $newInventoryPath $inventoryBytes
    $pins.files = Sort-PinFileRows @($pins.files)
    $pinsPath = Join-Path $CaseDir 'input-provenance-pins.json'
    $pinsSha = Write-SyntheticPins $pinsPath $pins
    if ($needClone) {
        $expectedAuditor = [string]$script:InputPins.roles.input.auditor
        $actualAuditor = [string]$pins.roles.input.auditor
        Need ((Get-Sha256File $expectedAuditor) -ceq (Get-Sha256File $actualAuditor)) 'SOURCE_CLONE_AUDITOR_BYTES'
        Need ((Get-Item -LiteralPath $expectedAuditor).Length -eq (Get-Item -LiteralPath $actualAuditor).Length) 'SOURCE_CLONE_AUDITOR_BYTES'
    }
    return [pscustomobject]@{ pins=$pins; pinsPath=$pinsPath; pinsSha=$pinsSha;
        inventoryPath=$newInventoryPath; p9Root=$p9Root; auditor=[string]$pins.roles.input.auditor }
}

function Invoke-AuditorControl([string]$Id, [string]$ExpectedCode,
                              [string]$PinsPath, [string]$PinsSha,
                              [string]$AuditorPath, [string]$EvidenceDir) {
    $args = @('-I','-B',$AuditorPath,$PinsPath,$PinsSha)
    $record = Invoke-Tracked $Id $script:Python @($args) 60000 $EvidenceDir
    Need ($record.receipt.ExitCode -eq 1 -and $record.receipt.Stdout -ceq '') 'SOURCE_CONTROL_EXIT'
    $actual = Get-SingleRejectCode $record.receipt.Stderr 'P9_RESERVATION_INPUT_REJECT ' 'SOURCE_CONTROL_FRAME'
    Need ($actual -ceq $ExpectedCode) 'SOURCE_CONTROL_REASON'
    return (New-ChildSummary $Id 1 $record $ExpectedCode $actual)
}

function Run-SourceControls([string]$SourceControlsDir) {
    $rows = New-Object System.Collections.ArrayList
    $cloneParentRoot = Join-Path $script:EvidenceRoot 'source-fixtures'
    [void](New-DirectoryOwned $cloneParentRoot)
    foreach ($case in $script:SourceControlCases) {
        $script:FailurePhase = 'SOURCE_' + $case.Id
        $caseDir = Join-Path $SourceControlsDir $case.Id
        [void][IO.Directory]::CreateDirectory($caseDir)
        Assert-VerifierNoReparseAncestors $caseDir
        if ($case.Id -ceq 'SRC01_PINS_DIGEST') {
            $row = Invoke-AuditorControl $case.Id $case.Code $script:InputPinsPath ('0' * 64) $script:Auditor $caseDir
        } else {
            $cloneParent = Join-Path $cloneParentRoot $case.Id
            $fixture = New-SourceFixture $case.Id $caseDir $cloneParent
            $row = Invoke-AuditorControl $case.Id $case.Code $fixture.pinsPath $fixture.pinsSha $fixture.auditor $caseDir
        }
        [void]$rows.Add($row)
    }
    Need ($rows.Count -eq 12) 'SOURCE_CONTROL_CENSUS'
    return ,$rows.ToArray()
}

function Get-ActualPowerShellImage() {
    if (-not ('P9NativeImage' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class P9NativeImage {
  [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
  public static extern uint GetModuleFileName(IntPtr module, StringBuilder path, uint size);
}
'@ -ErrorAction Stop | Out-Null
    }
    $buffer = New-Object Text.StringBuilder 32768
    $length = [P9NativeImage]::GetModuleFileName([IntPtr]::Zero,$buffer,[uint32]$buffer.Capacity)
    Need ($length -gt 0 -and $length -lt $buffer.Capacity) 'POWERSHELL_IMAGE_QUERY'
    return [IO.Path]::GetFullPath($buffer.ToString())
}

function Assert-AsciiJsonFile([string]$Path, [int]$MaxBytes, [string]$Code) {
    $bytes = Read-StableFile $Path
    Need ($bytes.Length -le $MaxBytes -and $bytes.Length -gt 1 -and $bytes[$bytes.Length-1] -eq 10) $Code
    foreach ($b in $bytes) { Need ($b -le 127 -and $b -ne 13) $Code }
    Need ($bytes[0] -ne 0xef -and $bytes[0] -ne 0xff -and $bytes[0] -ne 0xfe) $Code
    $text = [Text.Encoding]::ASCII.GetString($bytes)
    $payload = $text.Substring(0,$text.Length-1)
    try { $value = $payload | ConvertFrom-Json -ErrorAction Stop }
    catch { Fail $Code }
    Need (Test-BytesEqual (Get-CanonicalJsonBytes $value) $bytes) $Code
    return $value
}

function Assert-PinFileRows($Pins, [string]$Code) {
    Need ($Pins.files -is [array] -and $Pins.files.Count -eq 27) $Code
    $paths = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
    $folded = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
    $expectedOrder = Sort-PinFileRows @($Pins.files)
    for ($i=0; $i -lt $Pins.files.Count; $i++) {
        $row = $Pins.files[$i]
        Assert-ExactKeys $row @('path','bytes','sha256') $Code
        Need ($row.path -is [string] -and ($row.bytes -is [long] -or $row.bytes -is [int])) $Code
        Need ([long]$row.bytes -ge 0 -and $row.sha256 -cmatch '^[0-9a-f]{64}$') $Code
        Need ($paths.Add([string]$row.path) -and $folded.Add(([string]$row.path).ToLowerInvariant())) $Code
        Need ($row.path -ceq $expectedOrder[$i].path) $Code
    }
}

function Get-PinnedInputsFingerprint($Pins) {
    $entries = New-Object System.Collections.ArrayList
    $pinsBytes = Read-StableFile $script:InputPinsPath
    Need (Test-BytesEqual $pinsBytes $script:InputPinsBytes) 'PINNED_PINS_CHANGED'
    Need ((Get-Sha256Bytes $pinsBytes) -ceq $InputPinsSha256) 'PINNED_PINS_CHANGED'
    [void]$entries.Add([pscustomobject]@{path=$script:InputPinsPath;bytes=[long]$pinsBytes.Length;
        sha256=(Get-Sha256Bytes $pinsBytes)})
    foreach ($row in $Pins.files) {
        $path = [string]$row.path
        Assert-VerifierNoReparseAncestors $path
        $before = Get-Item -LiteralPath $path -Force
        Need (-not ($before.Attributes -band [IO.FileAttributes]::ReparsePoint) -and
              [long]$before.Length -eq [long]$row.bytes) 'PINNED_INPUT_SHAPE'
        $sha = Get-Sha256File $path
        $after = Get-Item -LiteralPath $path -Force
        Need ([long]$before.Length -eq [long]$after.Length -and
              $before.LastWriteTimeUtc.Ticks -eq $after.LastWriteTimeUtc.Ticks -and
              $before.CreationTimeUtc.Ticks -eq $after.CreationTimeUtc.Ticks -and
              $before.Attributes -eq $after.Attributes -and $sha -ceq [string]$row.sha256) 'PINNED_INPUT_CHANGED'
        [void]$entries.Add([pscustomobject]@{path=$path;bytes=[long]$after.Length;sha256=$sha})
    }
    $inventoryPath = [string]$Pins.sourceInventory.path
    $inventory = Read-StableFile $inventoryPath
    Need ((Get-Sha256Bytes $inventory) -ceq [string]$Pins.sourceInventory.sha256) 'PINNED_INVENTORY_CHANGED'
    [void]$entries.Add([pscustomobject]@{path=$inventoryPath;bytes=[long]$inventory.Length;
        sha256=(Get-Sha256Bytes $inventory)})
    return (ConvertTo-CanonicalJsonText @($entries.ToArray()))
}

function Validate-InitialInputs() {
    $script:FailurePhase = 'INPUT_PREFLIGHT'
    Need ([IO.Path]::IsPathRooted($RepositoryRoot)) 'REPOSITORY_ROOT'
    $preRepo = [IO.Path]::GetFullPath($RepositoryRoot)
    Need ($preRepo -ceq $RepositoryRoot) 'REPOSITORY_ROOT'
    $module = Join-Path $preRepo 'scripts\VerifierIsolation.psm1'
    Need (Test-Path -LiteralPath $module -PathType Leaf) 'VERIFIER_MODULE_MISSING'
    Need ((Get-Sha256File $module) -ceq 'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c') 'VERIFIER_MODULE_PIN'
    Import-Module $module -Force -DisableNameChecking -ErrorAction Stop
    $repo = Assert-CanonicalExisting $RepositoryRoot $true 'REPOSITORY_ROOT'
    $python = Assert-CanonicalExisting $PythonExe $false 'PYTHON_PATH'
    $javaHome = Assert-CanonicalExisting $JavaHome $true 'JAVA_HOME'
    $pinsPath = Assert-CanonicalExisting $InputPinsPath $false 'INPUT_PINS_PATH'
    Need ((Get-Sha256File $pinsPath) -ceq $InputPinsSha256) 'P9_INPUT_PINS_HASH'
    $script:InputPinsBytes = Read-StableFile $pinsPath
    Need ($script:InputPinsBytes.Length -le 4194304) 'INPUT_PINS_CAP'
    $pinsText = [Text.Encoding]::ASCII.GetString($script:InputPinsBytes)
    Need ($script:InputPinsBytes.Length -gt 1 -and $script:InputPinsBytes[$script:InputPinsBytes.Length-1] -eq 10 -and
          $pinsText.EndsWith("`n") -and $pinsText.IndexOf("`n") -eq ($pinsText.Length-1) -and
          -not $pinsText.Contains("`r") -and $script:InputPinsBytes[0] -ne 0xef) 'INPUT_PINS_FRAMING'
    try { $pins = $pinsText.Substring(0,$pinsText.Length-1) | ConvertFrom-Json -ErrorAction Stop }
    catch { Fail 'INPUT_PINS_JSON' }
    Need (Test-BytesEqual (Get-CanonicalJsonBytes $pins) $script:InputPinsBytes) 'INPUT_PINS_CANONICAL'
    Assert-ExactKeys $pins @('schema','sourceParent','repositoryRoot','p8Prerequisite','publishedP8',
        'sourceInventory','roles','toolPaths','files') 'INPUT_PINS_KEYS'
    Need ($pins.schema -ceq 'tsj-q100-reservation-provenance-inputs-v1' -and
          $pins.sourceParent -ceq '4623f1e5680503527a4423c2ca31fb09ede3cbb8' -and
          $pins.repositoryRoot -ceq $repo) 'INPUT_PINS_IDENTITY'
    Assert-ExactKeys $pins.p8Prerequisite @('pinsPath','pinsSha256','sourceInventorySha256') 'P8_PREREQUISITE_KEYS'
    Assert-ExactKeys $pins.publishedP8 @('qualificationPath','qualificationSha256','sourceCopiesPath',
        'sourceCopiesSha256','observationsPath','observationsSha256','semanticControlsPath','semanticControlsSha256') 'PUBLISHED_P8_KEYS'
    Assert-ExactKeys $pins.sourceInventory @('path','sha256') 'SOURCE_INVENTORY_KEYS'
    Assert-ExactKeys $pins.roles @('native','process','input') 'INPUT_ROLE_GROUPS'
    Assert-ExactKeys $pins.roles.native @('study','canary','routerOverlay') 'INPUT_NATIVE_ROLES'
    Assert-ExactKeys $pins.roles.process @('helper','host','controls','reader') 'INPUT_PROCESS_ROLES'
    Assert-ExactKeys $pins.roles.input @('builder','maker','auditor','test') 'INPUT_TOOL_ROLES'
    Assert-ExactKeys $pins.toolPaths @('pythonExe','javaHome','powershellExe') 'INPUT_TOOL_PATHS'
    Need ($pins.toolPaths.pythonExe -ceq $python -and $pins.toolPaths.javaHome -ceq $javaHome) 'INPUT_TOOL_ARGUMENTS'
    Assert-PinFileRows $pins 'INPUT_TOP_FILES'
    $script:InputPins = $pins
    $script:InputPinsPath = $pinsPath
    $script:Repository = $repo
    $script:Python = $python
    $script:JavaHomeCanonical = $javaHome
    $script:SourceSha256 = [string]$pins.sourceInventory.sha256
    $script:P9Root = [IO.Path]::GetFullPath((Split-Path -Parent ([string]$pins.roles.input.auditor)))
    Need ((Split-Path -Leaf $script:P9Root) -ceq 'canonical-reservation-provenance-r1') 'P9_ROOT_BASENAME'
    $p8PinsPath = Assert-CanonicalExisting ([string]$pins.p8Prerequisite.pinsPath) $false 'P8_PINS_PATH'
    Need ((Get-Sha256File $p8PinsPath) -ceq 'b3bca1391fcb8e229e6f34dddddfc52ae77447a02a064d9e7eb8a30e7bc8ba7a' -and
          [string]$pins.p8Prerequisite.pinsSha256 -ceq 'b3bca1391fcb8e229e6f34dddddfc52ae77447a02a064d9e7eb8a30e7bc8ba7a') 'P8_PINS_BINDING'
    $source33Root = Split-Path -Parent $p8PinsPath
    Need ((Split-Path -Leaf $p8PinsPath) -ceq 'input-study-pins.json' -and
          (Split-Path -Leaf $source33Root) -ceq 'geometric-prefix-source-gates-r33-80fb23ced3575719833471100b325a1d') 'P8_PINS_BINDING'
    $script:QRoot = Assert-CanonicalExisting (Split-Path -Parent $source33Root) $true 'Q_ROOT'
    Need ((Split-Path -Leaf $script:QRoot) -ceq 'tsj-q100-fixed-prefix-capture-840a8ef24dbb448f830d2786a95a1591' -and
          -not (Test-PathOverlap $script:QRoot $script:P9Root)) 'Q_ROOT_BINDING'
    foreach ($path in @($pins.roles.process.host,$pins.roles.process.controls,
          $pins.roles.process.reader,$pins.roles.input.auditor,$pins.roles.input.test,
          $pins.roles.native.study,$pins.roles.native.canary,$pins.roles.native.routerOverlay)) {
        Assert-VerifierNoReparseAncestors ([string]$path)
        Need (Test-Path -LiteralPath ([string]$path) -PathType Leaf) 'PINNED_ROLE_MISSING'
    }
    $selfRow = Get-PinRow @($pins.files) ([IO.Path]::GetFullPath($PSCommandPath)) 'CONTROLLER_SELF_ROW'
    Need ([string]$selfRow.sha256 -ceq (Get-Sha256File $PSCommandPath) -and
          [long]$selfRow.bytes -eq (Get-Item -LiteralPath $PSCommandPath).Length) 'CONTROLLER_SELF_PIN'
    foreach ($path in @($pins.roles.process.host,$pins.roles.process.reader,
          $pins.roles.process.controls,$pins.roles.input.auditor,$pins.roles.input.test,
          $pins.roles.input.builder,$pins.roles.input.maker,
          $pins.roles.native.study,$pins.roles.native.canary)) {
        $row = Get-PinRow @($pins.files) ([IO.Path]::GetFullPath([string]$path)) 'ROLE_PIN_ROW'
        Need ([long]$row.bytes -eq (Get-Item -LiteralPath $path).Length -and
              [string]$row.sha256 -ceq (Get-Sha256File $path)) 'ROLE_PIN_HASH'
    }
    $script:Auditor = [string]$pins.roles.input.auditor
    $script:HostScript = [string]$pins.roles.process.host
    $script:ReaderScript = [string]$pins.roles.process.reader
    $script:TestScript = [string]$pins.roles.input.test
    $script:ControlsScript = [string]$pins.roles.process.controls
    Need ($script:ControlsScript -ceq [IO.Path]::GetFullPath($PSCommandPath)) 'CONTROLLER_ROLE_PATH'
    Need ((Get-Sha256File $python) -ceq 'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec') 'PYTHON_PIN'
    $selfImage = Get-ActualPowerShellImage
    Need ($selfImage -ceq [string]$pins.toolPaths.powershellExe -and
          (Get-Sha256File $selfImage) -ceq '60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f') 'POWERSHELL_PIN'
    $script:InputBefore = Get-PinnedInputsFingerprint $pins
    $inventoryBytes = Read-StableFile ([string]$pins.sourceInventory.path)
    Need ((Get-Sha256Bytes $inventoryBytes) -ceq $script:SourceSha256) 'SOURCE_INVENTORY_HASH'
    $model = Get-InventoryModel $inventoryBytes 'SOURCE_INVENTORY_FORMAT'
    Need (Test-BytesEqual (New-InventoryBytes $model 'SOURCE_INVENTORY_FORMAT') $inventoryBytes) 'SOURCE_INVENTORY_CANONICAL'
    Need ($model.rows.Count -eq 3456 -and $model.header.rowCounts.total -eq 3456 -and
          $model.header.rowCounts.existing -eq 3437 -and $model.header.rowCounts.direct -eq 17 -and
          $model.header.rowCounts.derived -eq 2 -and $model.header.byteCounts.existing -eq 283692817) 'SOURCE_INVENTORY_COUNTS'
    Need ([string]$model.header.roots.P9 -ceq $script:P9Root -and
          [string]$model.header.roots.SOURCE33 -ceq $source33Root) 'SOURCE_INVENTORY_ROOT_BINDING'
    $routerRows = @($model.rows | Where-Object {
        $_.origin -ceq 'P9' -and $_.kind -ceq 'derived' -and $_.role -ceq 'ROUTER_OVERLAY' -and
        $_.path -ceq 'overlays-r1/PcbNetRouter.java'
    })
    Need ($routerRows.Count -eq 1) 'ROUTER_INVENTORY_ROW'
    $routerPath = [IO.Path]::GetFullPath((Join-Path $script:P9Root 'overlays-r1/PcbNetRouter.java'))
    Need ($routerPath -ceq [string]$pins.roles.native.routerOverlay -and
          ($routerRows[0].bytes -is [int] -or $routerRows[0].bytes -is [long]) -and
          [long]$routerRows[0].bytes -eq (Get-Item -LiteralPath $routerPath).Length -and
          $routerRows[0].sha256 -is [string] -and $routerRows[0].sha256 -cmatch '^[0-9a-f]{64}$' -and
          $routerRows[0].sha256 -ceq (Get-Sha256File $routerPath)) 'ROUTER_INVENTORY_BINDING'
    $script:SourceRoots = @($model.header.roots.PSObject.Properties | ForEach-Object { [string]$_.Value })
}

function New-DirectoryOwned([string]$Path, [bool]$AllowExisting = $false) {
    if (Test-Path -LiteralPath $Path) {
        Need ($AllowExisting -and (Test-Path -LiteralPath $Path -PathType Container)) 'OUTPUT_EXISTS'
    } else {
        [void][IO.Directory]::CreateDirectory($Path)
    }
    Assert-VerifierNoReparseAncestors $Path
    $item = Get-Item -LiteralPath $Path -Force
    Need (($item.Attributes -band [IO.FileAttributes]::Directory) -ne 0 -and
          -not ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) 'OUTPUT_NOT_DIRECTORY'
    return $item
}

function Prepare-OutputRoot() {
    $script:FailurePhase = 'OUTPUT_PREFLIGHT'
    $requested = $OutputRoot
    Need ([IO.Path]::IsPathRooted($requested)) 'OUTPUT_PATH'
    $script:OutputBase = [IO.Path]::GetFullPath($requested)
    Need ($script:OutputBase -ceq $requested) 'OUTPUT_PATH_CANONICAL'
    $parent = Split-Path -Parent $script:OutputBase
    [void](Assert-CanonicalExisting $parent $true 'OUTPUT_PARENT')
    $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
    Need (Test-PathWithin $temp $script:OutputBase) 'OUTPUT_NOT_OS_TEMP'
    Need (-not (Test-PathOverlap $script:Repository $script:OutputBase)) 'OUTPUT_SOURCE_OVERLAP'
    foreach ($root in $script:SourceRoots) { Need (-not (Test-PathOverlap $root $script:OutputBase)) 'OUTPUT_SOURCE_OVERLAP' }
    Need (-not (Test-PathWithin $script:QRoot $script:OutputBase)) 'OUTPUT_SOURCE_OVERLAP'
    Need (-not (Test-PathOverlap ([IO.Path]::GetDirectoryName([string]$script:InputPins.p8Prerequisite.pinsPath)) $script:OutputBase)) 'OUTPUT_INPUT_OVERLAP'
    Need (-not (Test-PathOverlap ([IO.Path]::GetDirectoryName([string]$script:InputPins.sourceInventory.path)) $script:OutputBase)) 'OUTPUT_INPUT_OVERLAP'
    Need (-not (Test-Path -LiteralPath $script:OutputBase)) 'OUTPUT_EXISTS'
    $baseInfo = New-DirectoryOwned $script:OutputBase
    $script:EvidenceRoot = Join-Path $script:OutputBase 'controller-evidence'
    [void](New-DirectoryOwned $script:EvidenceRoot)
    $owner = [ordered]@{
        schema='tsj-q100-reservation-controller-owner-v1'; pid=[int]$PID
        startTicks=[string]([Diagnostics.Process]::GetCurrentProcess().StartTime.ToUniversalTime().Ticks)
        executable=(Get-ActualPowerShellImage); executableSha256=(Get-Sha256File (Get-ActualPowerShellImage))
        script=[IO.Path]::GetFullPath($PSCommandPath); scriptSha256=(Get-Sha256File $PSCommandPath)
        inputSha256=$InputPinsSha256; outputRoot=$script:OutputBase
        creationTicks=[string]$baseInfo.CreationTimeUtc.Ticks
    }
    Write-JsonCreateNew (Join-Path $script:EvidenceRoot 'controller-owner.json') $owner
    [void](New-DirectoryOwned (Join-Path $script:OutputBase 'precase-canary'))
}

function Get-NextChildLimit([int]$ContractCapMs, [int]$ReserveMs) {
    $remaining = 1800000.0 - $script:FullWatch.Elapsed.TotalMilliseconds
    Need ($remaining -gt ($ReserveMs + 1)) 'OUTER_TIME_RESERVE'
    $available = [int][Math]::Floor($remaining - $ReserveMs)
    return [Math]::Min($ContractCapMs,$available)
}

function Get-HostArgs([string]$PinsPath, [string]$PinsSha, [string]$HostOutput,
                      [string]$ModeValue) {
    return ,@('-NoLogo','-NoProfile','-NonInteractive','-File',$script:HostScript,
        '-RepositoryRoot',$script:Repository,'-InputPinsPath',$PinsPath,
        '-InputPinsSha256',$PinsSha,'-OutputRoot',$HostOutput,
        '-JavaHome',$script:JavaHomeCanonical,'-PythonExe',$script:Python,'-Mode',$ModeValue)
}

function Assert-HostProcess($Record, [string]$PinsPath, [string]$PinsSha,
                            [string]$HostOutput, [string]$ModeValue) {
    $expected = Get-HostArgs $PinsPath $PinsSha $HostOutput $ModeValue
    Need ($Record.receipt.FilePath -ceq [string]$script:InputPins.toolPaths.powershellExe -and
          $Record.receipt.Arguments.Count -eq $expected.Count) 'HOST_PROCESS_COMMAND'
    for ($i=0; $i -lt $expected.Count; $i++) { Need ($Record.receipt.Arguments[$i] -ceq $expected[$i]) 'HOST_PROCESS_COMMAND' }
}

function New-HostPinsFixture([string]$Id, [string]$CaseDir) {
    $pins = ConvertFrom-Json -InputObject ([Text.Encoding]::ASCII.GetString($script:InputPinsBytes).TrimEnd("`n")) -ErrorAction Stop
    if ($Id -ceq 'HOST02_SELF') {
        $target = [string]$pins.roles.process.host
        $row = Get-PinRow @($pins.files) $target 'HOST_FIXTURE_PIN_ROW'
        $row.sha256 = '0' * 64
    } elseif ($Id -ceq 'HOST03_EXTRA_PIN') {
        $matches = @($pins.files | Where-Object { (Split-Path -Leaf ([string]$_.path)) -ceq 'PLAN_R1.md' })
        Need ($matches.Count -eq 1) 'HOST_FIXTURE_PLAN_ROW'
        $matches[0].sha256 = '0' * 64
    } else { Fail 'HOST_FIXTURE_ID' }
    $path = Join-Path $CaseDir 'input-provenance-pins.json'
    $sha = Write-SyntheticPins $path $pins
    return [pscustomobject]@{ path=$path; sha=$sha }
}

function Test-DirectoryEmpty([string]$Path) {
    Need (Test-Path -LiteralPath $Path -PathType Container) 'CONTROL_TARGET_MISSING'
    Assert-VerifierNoReparseTree $Path
    $items = @(Get-ChildItem -LiteralPath $Path -Force)
    return ($items.Count -eq 0)
}

function Run-HostControls([string]$ControlMode, [string]$ModeEvidence) {
    $script:FailurePhase = $ControlMode + '_HOST_CONTROLS'
    $result = New-Object System.Collections.ArrayList
    $hostControlsRoot = Join-Path $script:EvidenceRoot 'host-controls'
    [void](New-DirectoryOwned $hostControlsRoot $true)
    foreach ($case in $script:HostControlCases) {
        $caseDir = Join-Path $hostControlsRoot ($ControlMode + '\' + $case.Id)
        [void][IO.Directory]::CreateDirectory($caseDir)
        Assert-VerifierNoReparseAncestors $caseDir
        $pinsPath = $script:InputPinsPath
        $pinsSha = $InputPinsSha256
        if ($case.Id -ceq 'HOST01_DIGEST') { $pinsSha = '0' * 64 }
        if ($case.Id -ceq 'HOST02_SELF' -or $case.Id -ceq 'HOST03_EXTRA_PIN') {
            $fixture = New-HostPinsFixture $case.Id $caseDir
            $pinsPath = $fixture.path; $pinsSha = $fixture.sha
        }
        $target = Join-Path $caseDir 'output'
        $targetCreated = $false
        if ($case.Id -ceq 'HOST04_EXISTING_OUTPUT') {
            [void](New-DirectoryOwned $target)
            $targetCreated = $true
            Need (Test-DirectoryEmpty $target) 'HOST04_INITIAL_NOT_EMPTY'
        } elseif ($case.Id -ceq 'HOST05_SOURCE_OUTPUT') {
            $target = Join-Path $script:Repository ('p9-control-output-' + [Guid]::NewGuid().ToString('N'))
            Need (-not (Test-Path -LiteralPath $target)) 'HOST05_TARGET_EXISTS'
        } else {
            Need (-not (Test-Path -LiteralPath $target)) 'HOST_NEGATIVE_TARGET_EXISTS'
        }
        $args = Get-HostArgs $pinsPath $pinsSha $target $ControlMode
        $limit = Get-NextChildLimit 60000 300000
        $record = Invoke-Tracked ($ControlMode + '_' + $case.Id) $script:InputPins.toolPaths.powershellExe $args $limit $caseDir
        Assert-HostProcess $record $pinsPath $pinsSha $target $ControlMode
        Need ($record.receipt.ExitCode -eq 1 -and $record.receipt.Stdout -ceq '') 'HOST_CONTROL_EXIT'
        $payload = Parse-OneJsonFrame $record.receipt.Stderr 'P9_RESERVATION_HOST_REJECT ' $true 'HOST_CONTROL_FRAME' 65536
        Assert-ExactKeys $payload @('code','phase','outputCreated','scratchCreated',
            'compilerInvoked','nativeInvoked','cleanupError') 'HOST_CONTROL_FIELDS'
        Need ($payload.code -ceq $case.Code -and $payload.phase -is [string] -and
              $payload.phase.Length -gt 0 -and $payload.outputCreated -is [bool] -and
              $payload.scratchCreated -is [bool] -and $payload.compilerInvoked -is [bool] -and
              $payload.nativeInvoked -is [bool] -and $null -eq $payload.cleanupError -and
              -not $payload.outputCreated -and -not $payload.scratchCreated -and
              -not $payload.compilerInvoked -and -not $payload.nativeInvoked) 'HOST_CONTROL_SEMANTICS'
        if ($case.Id -ceq 'HOST04_EXISTING_OUTPUT') { Need (Test-DirectoryEmpty $target) 'HOST04_TARGET_CHANGED' }
        else { Need (-not (Test-Path -LiteralPath $target)) 'HOST_NEGATIVE_TARGET_CREATED' }
        [void]$result.Add((New-ChildSummary ($ControlMode + '_' + $case.Id) 1 $record $case.Code ([string]$payload.code)))
    }
    return ,$result.ToArray()
}

function Assert-HostCompleteOutput($Record, [string]$ModeValue, [string]$HostOutput) {
    Need ($Record.receipt.ExitCode -eq 0 -and $Record.receipt.Stderr -ceq '') 'HOST_POSITIVE_PROCESS'
    $payload = Parse-OneJsonFrame $Record.receipt.Stdout 'P9_RESERVATION_HOST_COMPLETE ' $true 'HOST_POSITIVE_FRAME' 65536
    Assert-ExactKeys $payload $script:ExpectedHostCompleteKeys 'HOST_POSITIVE_FIELDS'
    $expectedStatus = if ($ModeValue -ceq 'CANARY') { 'HOST_CANARY_COMPLETE_READER_PENDING' } else { 'HOST_COMPLETE_READER_PENDING' }
    Need ($payload.schema -ceq 'tsj-q100-reservation-provenance-host-complete-v1' -and
          $payload.status -ceq $expectedStatus -and $payload.mode -ceq $ModeValue -and
          $payload.inputSha256 -ceq $InputPinsSha256 -and
          $payload.sourceSha256 -ceq $script:SourceSha256 -and
          $payload.wireSha256 -is [string] -and $payload.wireSha256 -cmatch '^[0-9a-f]{64}$' -and
          $payload.suiteId -cmatch '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' -and
          $payload.layoutQualification -is [bool] -and -not $payload.layoutQualification) 'HOST_POSITIVE_IDENTITY'
    foreach ($pair in @(@('hostResult','host-result.json'),@('suiteIndex','suite-index.json'),@('suiteFooter','suite-footer.json'))) {
        $ref = $payload.($pair[0]); $name = $pair[1]
        Assert-ExactKeys $ref @('path','bytes','sha256') 'HOST_COMPLETE_REF_FIELDS'
        Need ($ref.path -ceq $name -and ($ref.bytes -is [long] -or $ref.bytes -is [int])) 'HOST_COMPLETE_REF'
        $path = Join-Path $HostOutput $name
        Need (Test-Path -LiteralPath $path -PathType Leaf) 'HOST_COMPLETE_REF_MISSING'
        $item = Get-Item -LiteralPath $path
        Need ([long]$item.Length -eq [long]$ref.bytes -and
              (Get-Sha256File $path) -ceq [string]$ref.sha256) 'HOST_COMPLETE_REF_HASH'
    }
    return $payload
}

function Save-ParentReceipt([string]$Path, $Process, [double]$ElapsedMs) {
    Need (-not [double]::IsNaN($ElapsedMs) -and -not [double]::IsInfinity($ElapsedMs) -and
          $ElapsedMs -gt 0 -and $ElapsedMs -le 1800000) 'PARENT_ELAPSED_INVALID'
    $parent = [ordered]@{
        schema='tsj-q100-reservation-provenance-parent-v1'; elapsedMs=$ElapsedMs; process=$Process
    }
    Write-OrderedJsonCreateNew $Path $parent
    return $parent
}

function Run-HostPositive([string]$WrapperMode, [string]$HostOutput,
                          [string]$EvidenceDir, [string]$ParentPath) {
    $script:FailurePhase = $WrapperMode + '_HOST_POSITIVE'
    Need (-not (Test-Path -LiteralPath $HostOutput)) 'HOST_OUTPUT_ALREADY_EXISTS'
    $args = Get-HostArgs $script:InputPinsPath $InputPinsSha256 $HostOutput $WrapperMode
    $limit = Get-NextChildLimit 1800000 250000
    $record = Invoke-Tracked ($WrapperMode + '_HOST') $script:InputPins.toolPaths.powershellExe $args $limit $EvidenceDir
    Assert-HostProcess $record $script:InputPinsPath $InputPinsSha256 $HostOutput $WrapperMode
    $complete = Assert-HostCompleteOutput $record $WrapperMode $HostOutput
    $parent = Save-ParentReceipt $ParentPath $record.receipt $record.wallMs
    return [pscustomobject]@{ record=$record; complete=$complete; parent=$parent; parentPath=$ParentPath;
        summary=(New-ChildSummary ($WrapperMode + '_HOST') 0 $record $null $null) }
}

function Assert-ReaderResult($ReaderResult, [string]$ModeValue) {
    Assert-ExactKeys $ReaderResult $script:ExpectedReaderKeys 'READER_RESULT_FIELDS'
    $status = if ($ModeValue -ceq 'CANARY') { 'PASS_RESERVATION_HOST_CANARY_ONLY' }
              else { 'PASS_RESERVATION_PROVENANCE_DIAGNOSTIC_ONLY' }
    Need ($ReaderResult.schema -ceq 'tsj-q100-reservation-provenance-reader-result-v1' -and
          $ReaderResult.status -ceq $status -and $ReaderResult.layoutQualification -is [bool] -and
          -not $ReaderResult.layoutQualification -and
          $ReaderResult.p9InputSha256 -ceq $InputPinsSha256 -and
          $ReaderResult.p9SourceSha256 -ceq $script:SourceSha256 -and
          $ReaderResult.p8WireSha256 -is [string] -and
          $ReaderResult.p8WireSha256 -cmatch '^[0-9a-f]{64}$') 'READER_RESULT_IDENTITY'
    Assert-ExactKeys $ReaderResult.sourceClosure @('status','pinsSha256','sourceInventorySha256',
        'existingFiles','directFiles','derivedFiles','totalFiles','productionFiles','topFiles','rechecked') 'READER_SOURCE_CLOSURE_FIELDS'
    Need ($ReaderResult.sourceClosure.status -ceq 'PASS_Q100_RESERVATION_INPUTS_ONLY' -and
          $ReaderResult.sourceClosure.pinsSha256 -ceq $InputPinsSha256 -and
          $ReaderResult.sourceClosure.sourceInventorySha256 -ceq $script:SourceSha256 -and
          $ReaderResult.sourceClosure.existingFiles -eq 3437 -and
          $ReaderResult.sourceClosure.directFiles -eq 17 -and
          $ReaderResult.sourceClosure.derivedFiles -eq 2 -and
          $ReaderResult.sourceClosure.totalFiles -eq 3456 -and
          $ReaderResult.sourceClosure.productionFiles -eq 850 -and
          $ReaderResult.sourceClosure.topFiles -eq 27 -and
          $ReaderResult.sourceClosure.rechecked -is [bool] -and
          $ReaderResult.sourceClosure.rechecked) 'READER_SOURCE_CLOSURE'
    Assert-ExactKeys $ReaderResult.processes @('hostReturned','hostTerminationProven','childProcessCount',
        'allChildrenTerminated','allProcessRootsAbsent','readerExecutableSha256') 'READER_PROCESS_FIELDS'
    $childCount = if ($ModeValue -ceq 'CANARY') { 12 } else { 17 }
    Need ($ReaderResult.processes.hostReturned -and $ReaderResult.processes.hostTerminationProven -and
          $ReaderResult.processes.childProcessCount -eq $childCount -and
          $ReaderResult.processes.allChildrenTerminated -and $ReaderResult.processes.allProcessRootsAbsent -and
          $ReaderResult.processes.readerExecutableSha256 -ceq 'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec') 'READER_PROCESS_PROOF'
    if ($ModeValue -ceq 'CANARY') {
        Need ($ReaderResult.scope -ceq 'HOST_CANARY_ONLY' -and $ReaderResult.runs.Count -eq 0 -and
              $null -eq $ReaderResult.target -and $null -eq $ReaderResult.slotComparison -and
              $ReaderResult.contributorNetCount -eq 0 -and $ReaderResult.contributorPadCount -eq 0 -and
              $null -eq $ReaderResult.contributorSetSha256 -and $null -eq $ReaderResult.p8Parity) 'READER_CANARY_SCOPE'
    } else {
        Need ($ReaderResult.scope -ceq 'ONE_AUTHORED_BASE_PARENT11_SLOT_ONLY' -and
              $ReaderResult.runs.Count -eq 5 -and $ReaderResult.target -is [pscustomobject] -and
              $ReaderResult.slotComparison -is [pscustomobject] -and
              $ReaderResult.p8Parity -is [pscustomobject]) 'READER_SUITE_SCOPE'
    }
    return $ReaderResult
}

function Run-ReaderPositive([string]$ModeValue, [string]$HostOutput,
                            [string]$ParentPath, [string]$EvidenceDir) {
    $script:FailurePhase = $ModeValue + '_READER_POSITIVE'
    $outputBefore = Get-OutputFingerprint $HostOutput
    $parentBefore = Get-Sha256File $ParentPath
    $args = @('-I','-B',$script:ReaderScript,$script:InputPinsPath,$InputPinsSha256,$HostOutput,$ParentPath)
    $limit = Get-NextChildLimit 180000 90000
    $record = Invoke-Tracked ($ModeValue + '_READER') $script:Python @($args) $limit $EvidenceDir
    Need ($outputBefore -ceq (Get-OutputFingerprint $HostOutput)) 'READER_POSITIVE_CHANGED_OUTPUT'
    Need ($parentBefore -ceq (Get-Sha256File $ParentPath)) 'READER_POSITIVE_CHANGED_PARENT'
    Need ($record.receipt.ExitCode -eq 0 -and $record.receipt.Stderr -ceq '') 'READER_POSITIVE_PROCESS'
    Need (([Text.Encoding]::UTF8.GetByteCount($record.receipt.Stdout)) -le 65536) 'READER_POSITIVE_SIZE'
    Need ($record.receipt.Stdout.EndsWith("`n") -and
          $record.receipt.Stdout.LastIndexOf("`n") -eq ($record.receipt.Stdout.Length-1) -and
          -not $record.receipt.Stdout.Contains("`r")) 'READER_POSITIVE_FRAMING'
    try { $value = $record.receipt.Stdout.Substring(0,$record.receipt.Stdout.Length-1) | ConvertFrom-Json -ErrorAction Stop }
    catch { Fail 'READER_POSITIVE_JSON' }
    Need (Test-BytesEqual (Get-CanonicalJsonBytes $value) ($script:Utf8NoBom.GetBytes($record.receipt.Stdout))) 'READER_POSITIVE_CANONICAL'
    $value = Assert-ReaderResult $value $ModeValue
    return [pscustomobject]@{ record=$record; result=$value;
        summary=(New-ChildSummary ($ModeValue + '_READER') 0 $record $null $null) }
}

function Clone-ParentReceipt($Parent, [string]$Path, [string]$Mutation) {
    $copy = ConvertFrom-Json -InputObject (ConvertTo-CanonicalJsonText $Parent $true) -ErrorAction Stop
    switch -CaseSensitive ($Mutation) {
        'ELAPSED_BOOL' { $copy.elapsedMs = $true }
        'EXIT_TWO' { $copy.process.ExitCode = 2 }
        'TERMINATION_FALSE' { $copy.process.TerminationProven = $false }
        'TIMING_ONE' { $copy.elapsedMs = [double]1.0 }
        default { Fail 'PARENT_MUTATION_ID' }
    }
    Write-OrderedJsonCreateNew $Path $copy
    return $copy
}

function Get-OutputFingerprint([string]$HostOutput) {
    Assert-VerifierNoReparseTree $HostOutput
    $root = [IO.Path]::GetFullPath($HostOutput).TrimEnd('\')
    $rows = New-Object System.Collections.ArrayList
    foreach ($item in @(Get-ChildItem -LiteralPath $root -Force -Recurse)) {
        Need (-not ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) 'OUTPUT_REPARSE'
        $relative = $item.FullName.Substring($root.Length+1).Replace('\','/')
        if ($item -is [IO.DirectoryInfo]) {
            [void]$rows.Add([pscustomobject]@{kind='directory';path=$relative;bytes=[long]0;sha256=$null})
            continue
        }
        Need ($item -is [IO.FileInfo]) 'OUTPUT_UNKNOWN_ENTRY'
        $before = $item.Length; $write = $item.LastWriteTimeUtc.Ticks; $creation=$item.CreationTimeUtc.Ticks
        $sha = Get-Sha256File $item.FullName
        $after = Get-Item -LiteralPath $item.FullName -Force
        Need ($before -eq $after.Length -and $write -eq $after.LastWriteTimeUtc.Ticks -and
              $creation -eq $after.CreationTimeUtc.Ticks) 'OUTPUT_CHANGED_DURING_FINGERPRINT'
        [void]$rows.Add([pscustomobject]@{kind='file';path=$relative;bytes=[long]$after.Length;sha256=$sha})
    }
    $array = New-Object System.Collections.ArrayList
    foreach ($row in $rows) { [void]$array.Add($row) }
    for ($i=1; $i -lt $array.Count; $i++) {
        $value = $array[$i]
        $j = $i - 1
        while ($j -ge 0) {
            $cmp = [StringComparer]::Ordinal.Compare([string]$array[$j].path,[string]$value.path)
            if ($cmp -eq 0) { $cmp = [StringComparer]::Ordinal.Compare([string]$array[$j].kind,[string]$value.kind) }
            if ($cmp -le 0) { break }
            $array[$j+1] = $array[$j]
            $j--
        }
        $array[$j+1] = $value
    }
    return (ConvertTo-CanonicalJsonText $array)
}

function Run-ReaderControls([string]$ModeValue, [string]$HostOutput,
                            $Parent, [string]$ParentPath, [string]$EvidenceDir) {
    $script:FailurePhase = $ModeValue + '_READER_CONTROLS'
    $before = Get-OutputFingerprint $HostOutput
    $originalParentSha = Get-Sha256File $ParentPath
    $result = New-Object System.Collections.ArrayList
    foreach ($case in $script:ReaderControlCases) {
        $caseDir = Join-Path $EvidenceDir $case.Id
        [void][IO.Directory]::CreateDirectory($caseDir)
        Assert-VerifierNoReparseAncestors $caseDir
        $mutatedParentPath = $ParentPath
        $pinsSha = $InputPinsSha256
        if ($case.Id -ceq 'READER01_DIGEST') { $pinsSha = '0' * 64 }
        else {
            $mutatedParentPath = Join-Path $caseDir 'parent-receipt.json'
            $mutation = switch ($case.Id) {
                'READER02_ELAPSED_TYPE' { 'ELAPSED_BOOL' }
                'READER03_EXIT_TWO' { 'EXIT_TWO' }
                'READER04_TERMINATION' { 'TERMINATION_FALSE' }
                'READER05_TIMING_ORDER' { 'TIMING_ONE' }
                default { Fail 'READER_CONTROL_ID' }
            }
            [void](Clone-ParentReceipt $Parent $mutatedParentPath $mutation)
        }
        if ($case.Id -ceq 'READER05_TIMING_ORDER') {
            Need ($Parent.elapsedMs -gt 1) 'READER_TIMING_FIXTURE_PRECONDITION'
        }
        $mutatedParentSha = Get-Sha256File $mutatedParentPath
        $args = @('-I','-B',$script:ReaderScript,$script:InputPinsPath,$pinsSha,$HostOutput,$mutatedParentPath)
        $limit = Get-NextChildLimit 30000 120000
        $record = Invoke-Tracked ($ModeValue + '_' + $case.Id) $script:Python @($args) $limit $caseDir
        Need ($mutatedParentSha -ceq (Get-Sha256File $mutatedParentPath)) 'READER_CONTROL_CHANGED_PARENT'
        Need ($record.receipt.ExitCode -eq 2 -and $record.receipt.Stdout -ceq '') 'READER_CONTROL_EXIT'
        Need ($record.receipt.Stderr -ceq ('P9_RESERVATION_READER_REJECT ' + $case.Code + "`n")) 'READER_CONTROL_REASON'
        [void]$result.Add((New-ChildSummary ($ModeValue + '_' + $case.Id) 2 $record $case.Code $case.Code))
    }
    $after = Get-OutputFingerprint $HostOutput
    Need ($before -ceq $after) 'READER_CONTROLS_CHANGED_OUTPUT'
    Need ($originalParentSha -ceq (Get-Sha256File $ParentPath)) 'READER_CONTROLS_CHANGED_PARENT'
    return ,$result.ToArray()
}

function Run-LiteralControl([string]$EvidenceDir) {
    $script:FailurePhase = 'LITERAL_CONTROL'
    $args = @('-I','-B',$script:TestScript,'--literal')
    $limit = Get-NextChildLimit 180000 60000
    $record = Invoke-Tracked 'P9_LITERAL_ORACLE' $script:Python @($args) $limit $EvidenceDir
    $expected = '{"schema":"tsj-q100-reservation-literal-result-v1","status":"PASS_LITERAL_ORACLE_ONLY","positiveCount":12,"negativeCount":16,"passedPositiveCount":12,"passedNegativeCount":16}' + "`n"
    Need ($record.receipt.ExitCode -eq 0 -and $record.receipt.Stderr -ceq '' -and
          $record.receipt.Stdout -ceq $expected) 'LITERAL_CONTROL_RESULT'
    return (New-ChildSummary 'P9_LITERAL_ORACLE' 0 $record $null $null)
}

function Run-SemanticControls([string]$HostOutput, [string]$ParentPath,
                              [string]$EvidenceDir) {
    $script:FailurePhase = 'SUITE_SEMANTIC_CONTROLS'
    $before = Get-OutputFingerprint $HostOutput
    $parentBefore = Get-Sha256File $ParentPath
    $args = @('-I','-B',$script:TestScript,$script:InputPinsPath,$InputPinsSha256,$HostOutput,$ParentPath)
    $limit = Get-NextChildLimit 180000 60000
    $record = Invoke-Tracked 'P9_SUITE_SEMANTIC_CONTROLS' $script:Python @($args) $limit $EvidenceDir
    Need ($record.receipt.ExitCode -eq 0 -and $record.receipt.Stderr -ceq '') 'SEMANTIC_CONTROL_PROCESS'
    Need ($record.receipt.Stdout.EndsWith("`n") -and
          $record.receipt.Stdout.LastIndexOf("`n") -eq ($record.receipt.Stdout.Length-1) -and
          -not $record.receipt.Stdout.Contains("`r") -and
          [Text.Encoding]::UTF8.GetByteCount($record.receipt.Stdout) -le 65536) 'SEMANTIC_CONTROL_FRAMING'
    try { $value = $record.receipt.Stdout.Substring(0,$record.receipt.Stdout.Length-1) | ConvertFrom-Json -ErrorAction Stop }
    catch { Fail 'SEMANTIC_CONTROL_JSON' }
    Assert-ExactKeys $value @('schema','status','syntheticFixtures','passed','positiveControl',
        'cases','genuineOutputsUnchanged','layoutQualification') 'SEMANTIC_CONTROL_FIELDS'
    Need ($value.schema -ceq 'tsj-q100-reservation-semantic-controls-v1' -and
          $value.status -ceq 'PASS_SYNTHETIC_RESERVATION_CONTROLS_ONLY' -and
          $value.syntheticFixtures -is [bool] -and $value.syntheticFixtures -and
          $value.passed -eq 16 -and $value.positiveControl -is [bool] -and $value.positiveControl -and
          $value.genuineOutputsUnchanged -is [bool] -and $value.genuineOutputsUnchanged -and
          $value.layoutQualification -is [bool] -and -not $value.layoutQualification -and
          $value.cases.Count -eq 16) 'SEMANTIC_CONTROL_RESULT'
    $expectedIds = @(
        'P9N01_CRLF_OR_MISSING_FINAL_LF','P9N02_DUPLICATE_JSON_KEY','P9N03_EXTRA_OR_MISSING_HEADER_KEY',
        'P9N04_PAD_REORDERED_OR_DUPLICATED','P9N05_PAD_MISSING_OR_EXTRA','P9N06_BOOL_OR_FLOAT_AS_INTEGER',
        'P9N07_UNKNOWN_ROLE_OR_ENUM','P9N08_ROLE_WIDTH_DISAGREEMENT','P9N09_BAD_GRID_DERIVATION',
        'P9N10_WRONG_PINNED_OUTLET_ORIGIN_OR_VALUE','P9N11_INCONSISTENT_COURTYARD_FOR_COMPONENT',
        'P9N12_PREPARED_CORRIDOR_DIFFERS_FROM_DERIVATION','P9N13_WRONG_TARGET_OR_QUERY_IDENTITY',
        'P9N14_CAPTURED_SLOT_DIFFERS_FROM_ORACLE','P9N15_TAP_SENTINEL_NOT_CAUGHT_ONCE_OR_DTO_NOT_DISCARDED',
        'P9N16_UNEXPECTED_SIDECAR_FILE_OR_INDEX_REFERENCE'
    )
    $expectedCodes = @(
        'P9_OPERAND_FRAMING','P9_OPERAND_JSON','P9_HEADER_FIELDS','P9_PAD_ORDER',
        'P9_PAD_COUNT','P9_INTEGER_TYPE','P9_PAD_ROLE','P9_PAD_WIDTH',
        'P9_GRID_DERIVATION','P9_OUTLET_SOURCE_BINDING','P9_OWNER_COURTYARD',
        'P9_CORRIDOR_PARITY','P9_QUERY_IDENTITY','P9_SLOT_MISMATCH',
        'P9_TAP_THROW_PROOF','P9_OUTPUT_CENSUS'
    )
    Need (Test-BytesEqual (Get-CanonicalJsonBytes $value) ($script:Utf8NoBom.GetBytes($record.receipt.Stdout))) 'SEMANTIC_CONTROL_CANONICAL'
    for ($i=0; $i -lt $expectedIds.Count; $i++) {
        $case = $value.cases[$i]
        Assert-ExactKeys $case @('id','expectedCode','actualCode','status') 'SEMANTIC_CASE_FIELDS'
        Need ($case.id -ceq $expectedIds[$i] -and $case.expectedCode -ceq $expectedCodes[$i] -and
              $case.expectedCode -ceq $case.actualCode -and
              $case.status -ceq 'PASS_EXPECTED_REJECTION') 'SEMANTIC_CASE_ORDER'
    }
    $after = Get-OutputFingerprint $HostOutput
    Need ($before -ceq $after) 'SEMANTIC_CONTROL_CHANGED_OUTPUT'
    Need ($parentBefore -ceq (Get-Sha256File $ParentPath)) 'SEMANTIC_CONTROL_CHANGED_PARENT'
    return (New-ChildSummary 'P9_SUITE_SEMANTIC_CONTROLS' 0 $record $null $null)
}

function Assert-ControlRootSize() {
    Assert-VerifierNoReparseTree $script:OutputBase
    [long]$total = 0
    foreach ($item in @(Get-ChildItem -LiteralPath $script:OutputBase -Force -Recurse)) {
        Need (-not ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) 'OUTPUT_ROOT_REPARSE'
        if ($item -is [IO.FileInfo]) { $total += [long]$item.Length }
    }
    Need ($total -le 41943040) 'CONTROLLER_EVIDENCE_CAP'
    return $total
}

function Add-Rows([System.Collections.ArrayList]$Target, [object[]]$Rows) {
    foreach ($row in $Rows) { [void]$Target.Add($row) }
}

function Run-Controller() {
    Validate-InitialInputs
    Prepare-OutputRoot

    $sourceDir = Join-Path $script:EvidenceRoot 'source-controls'
    [void](New-DirectoryOwned $sourceDir)
    $sourceControls = Run-SourceControls $sourceDir

    $literalDir = Join-Path $script:EvidenceRoot 'literal'
    [void](New-DirectoryOwned $literalDir)
    $literal = Run-LiteralControl $literalDir

    $hostControls = New-Object System.Collections.ArrayList
    $readerControls = New-Object System.Collections.ArrayList
    $hosts = New-Object System.Collections.ArrayList
    $consumers = New-Object System.Collections.ArrayList
    $readerResults = New-Object System.Collections.ArrayList
    $hostControlsCanary = Run-HostControls 'CANARY' 'CANARY'
    Add-Rows $hostControls $hostControlsCanary

    $canaryParentDir = Join-Path $script:OutputBase 'precase-canary'
    $canaryOutput = Join-Path $canaryParentDir 'host'
    $canaryHostEvidence = Join-Path $canaryParentDir 'host-process-evidence'
    [void](New-DirectoryOwned $canaryHostEvidence)
    $canaryParentPath = Join-Path $canaryParentDir 'parent-receipt.json'
    $canaryHost = Run-HostPositive 'CANARY' $canaryOutput $canaryHostEvidence $canaryParentPath
    [void]$hosts.Add($canaryHost.summary)

    $canaryReaderEvidence = Join-Path $canaryParentDir 'reader-evidence'
    [void](New-DirectoryOwned $canaryReaderEvidence)
    $canaryReader = Run-ReaderPositive 'CANARY' $canaryOutput $canaryParentPath $canaryReaderEvidence
    [void]$consumers.Add($canaryReader.summary)
    [void]$readerResults.Add($canaryReader.result)

    $canaryReaderControls = Join-Path $canaryParentDir 'reader-controls'
    [void](New-DirectoryOwned $canaryReaderControls)
    $canaryReaderControlRows = Run-ReaderControls 'CANARY' $canaryOutput $canaryHost.parent $canaryParentPath $canaryReaderControls
    Add-Rows $readerControls $canaryReaderControlRows
    $canaryFingerprint = Get-OutputFingerprint $canaryOutput

    $semantic = $null
    $suiteFingerprint = $null
    if ($Mode -ceq 'SUITE') {
        $hostControlsSuite = Run-HostControls 'SUITE' 'SUITE'
        Add-Rows $hostControls $hostControlsSuite

        $suiteParentDir = Join-Path $script:OutputBase 'suite'
        [void](New-DirectoryOwned $suiteParentDir)
        $suiteOutput = Join-Path $suiteParentDir 'host'
        $suiteHostEvidence = Join-Path $suiteParentDir 'host-process-evidence'
        [void](New-DirectoryOwned $suiteHostEvidence)
        $suiteParentPath = Join-Path $suiteParentDir 'parent-receipt.json'
        $suiteHost = Run-HostPositive 'SUITE' $suiteOutput $suiteHostEvidence $suiteParentPath
        [void]$hosts.Add($suiteHost.summary)

        $suiteReaderEvidence = Join-Path $suiteParentDir 'reader-evidence'
        [void](New-DirectoryOwned $suiteReaderEvidence)
        $suiteReader = Run-ReaderPositive 'SUITE' $suiteOutput $suiteParentPath $suiteReaderEvidence
        [void]$consumers.Add($suiteReader.summary)
        [void]$readerResults.Add($suiteReader.result)

        $suiteReaderControls = Join-Path $suiteParentDir 'reader-controls'
        [void](New-DirectoryOwned $suiteReaderControls)
        $suiteReaderControlRows = Run-ReaderControls 'SUITE' $suiteOutput $suiteHost.parent $suiteParentPath $suiteReaderControls
        Add-Rows $readerControls $suiteReaderControlRows

        $suiteSemanticEvidence = Join-Path $suiteParentDir 'semantic-controls'
        [void](New-DirectoryOwned $suiteSemanticEvidence)
        $semantic = Run-SemanticControls $suiteOutput $suiteParentPath $suiteSemanticEvidence
        $suiteFingerprint = Get-OutputFingerprint $suiteOutput
        Need ($canaryFingerprint -ceq (Get-OutputFingerprint $canaryOutput)) 'CANARY_OUTPUT_CHANGED_AFTER_SUITE'
    }

    $script:FailurePhase = 'FINAL_CLOSURE'
    $inputAfter = Get-PinnedInputsFingerprint $script:InputPins
    Need ($script:InputBefore -ceq $inputAfter) 'INPUTS_CHANGED_DURING_CONTROLLER'
    Need ($readerResults.Count -eq $hosts.Count -and $readerResults.Count -eq $(if ($Mode -ceq 'CANARY') {1} else {2})) 'POSITIVE_READER_CENSUS'
    foreach ($reader in $readerResults) {
        Need ($reader.sourceClosure.rechecked -is [bool] -and $reader.sourceClosure.rechecked) 'SOURCE_CLOSURE_NOT_RECHECKED'
    }
    Need ($canaryFingerprint -ceq (Get-OutputFingerprint $canaryOutput)) 'CANARY_OUTPUT_CHANGED'
    if ($Mode -ceq 'SUITE') { Need ($suiteFingerprint -ceq (Get-OutputFingerprint $suiteOutput)) 'SUITE_OUTPUT_CHANGED' }
    Need ($script:AllProcesses.Count -eq $(if ($Mode -ceq 'CANARY') {25} else {38})) 'CONTROLLER_PROCESS_CENSUS'
    foreach ($item in $script:AllProcesses) {
        Need ($item.receipt.TerminationProven -and
              -not (Test-Path -LiteralPath $item.receipt.ProcessRoot) -and
              -not (Test-Path -LiteralPath $item.receipt.StdoutLog) -and
              -not (Test-Path -LiteralPath $item.receipt.StderrLog)) 'PROCESS_CLEANUP_FINAL'
    }
    $evidenceBytes = Assert-ControlRootSize
    Need ($evidenceBytes -le 41943040) 'CONTROLLER_EVIDENCE_CAP'

    $status = if ($Mode -ceq 'CANARY') { 'PASS_HOST_CANARY_CONTROLS_ONLY' }
              else { 'PASS_RESERVATION_SUITE_CONTROLS_ONLY' }
    $summary = [ordered]@{
        schema='tsj-q100-reservation-controls-v1'; status=$status; mode=$Mode
        inputSha256=$InputPinsSha256; sourceSha256=$script:SourceSha256
        sourceControls=@($sourceControls); hostControls=@($hostControls.ToArray())
        literal=$literal; host=@($hosts.ToArray()); consumer=@($consumers.ToArray())
        readerControls=@($readerControls.ToArray()); semantic=$semantic
        inputUnchanged=$true; outputUnchanged=$true
        allReturnedProcessesTerminated=$true; limits=$script:HostLimits
        layoutQualification=$false
    }
    $summaryBytes = Get-CanonicalJsonBytes $summary
    Write-CreateNew (Join-Path $script:EvidenceRoot 'controller-result.json') $summaryBytes
    $evidenceBytes = Assert-ControlRootSize
    Need ($evidenceBytes -le 41943040) 'CONTROLLER_EVIDENCE_CAP'
    $script:FailurePhase = 'FINAL_OUTPUT'
    [Console]::Out.Write('P9_RESERVATION_CONTROLS_COMPLETE ' +
        [Text.Encoding]::ASCII.GetString($summaryBytes))
    [Console]::Out.Flush()
    Need ($script:FullWatch.Elapsed.TotalMilliseconds -le 1800000) 'OUTER_FINAL_WALL'
    $script:FailurePhase = 'COMPLETE'
    $script:FullWatch.Stop()
}

try {
    Run-Controller
    exit 0
} catch {
    $message = [string]$_.Exception.Message
    if ($message.StartsWith('P9C:',[StringComparison]::Ordinal)) {
        $script:FailureCode = $message.Substring(4)
    } else {
        $script:FailureCode = 'UNEXPECTED_FAILURE'
    }
    if ($script:OutputBase -and (Test-Path -LiteralPath $script:OutputBase -PathType Container)) {
        try {
            $failurePath = Join-Path $script:EvidenceRoot 'controller-failure.json'
            if (-not (Test-Path -LiteralPath $failurePath)) {
                $failure = [ordered]@{
                    schema='tsj-q100-reservation-controller-failure-v1'; status='FAIL'
                    phase=$script:FailurePhase; code=$script:FailureCode
                    elapsedMs=$script:FullWatch.Elapsed.TotalMilliseconds
                    returnedProcesses=$script:AllProcesses.Count; evidenceRoot=$script:EvidenceRoot
                    returnedProcessRefs=@($script:AllProcesses | ForEach-Object {
                        [ordered]@{name=$_.name;receipt=$_.ref;wallMs=$_.wallMs;
                            stdoutCaptured=$_.stdoutCaptured;stderrCaptured=$_.stderrCaptured}
                    })
                    retentionErrors=@($script:RetentionErrors.ToArray())
                }
                Write-Utf8JsonCreateNew $failurePath $failure
            }
        } catch { }
    }
    [Console]::Error.WriteLine('P9_RESERVATION_CONTROLS_REJECT ' + $script:FailurePhase + '_' + $script:FailureCode)
    exit 2
}



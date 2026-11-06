[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('CANARY','ANALYZE')][string]$Mode,
    [Parameter(Mandatory=$true)][string]$SpecPath,
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{64}$')][string]$SpecSha256,
    [Parameter(Mandatory=$true)][string]$OutputRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:failureCode = $null
$script:phase = 'PREFLIGHT'
$script:outputCreated = $false
$script:outputRootFull = $null
$script:outputCreationTicks = 0L
$script:spec = $null
$script:specValue = $null
$script:specPins = $null
$script:sourceInventory = $null
$script:p6InputPins = $null
$script:sourceBefore = [pscustomobject]@{ status = 'NOT_RUN'; sourceRows = 0; sourceFiles = 0; extraFiles = 0; elapsedMs = 0.0 }
$script:sourceAfter = [pscustomobject]@{ status = 'NOT_RUN'; sourceRows = 0; sourceFiles = 0; extraFiles = 0; elapsedMs = 0.0 }
$script:parentReceipt = $null
$script:workerReceipt = $null
$script:parentRelation = $null
$script:workerRelation = $null
$script:parentWallMs = 0.0
$script:workerWallMs = 0.0
$script:parentWallNanos = 0L
$script:workerWallNanos = 0L
$script:indexElapsedNanos = 0L
$script:residualUpperNanos = 0L
$script:finalGroups = 0L
$script:maximumGroupNanos = 0L
$script:outerWatch = [Diagnostics.Stopwatch]::StartNew()
$script:hostIdentity = $null
$script:modulePath = $null
$script:pythonPath = $null
$script:parentRunRoot = $null
$script:parentReaderPath = $null
$script:parentPinsPath = $null
$script:parentPinsSha256 = $null
$script:outputDataRoot = $null
$script:temporaryOutputRetained = $false

$script:utf8 = [Text.UTF8Encoding]::new($false)
$script:strictUtf8 = [Text.UTF8Encoding]::new($false, $true)
$script:chunkBytes = 32768
$script:maximumInputBytes = 8388608L
$script:sourceBytesLimit = 67108864L
$script:verifierIsolationSha256 = 'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c'
$script:p6QualificationSha256 = 'd7f39f1a77e302e9e883b4aedbdeb9c416a93cf4cfa85029710b04da5b25d692'
$script:p6InputPinsSha256 = 'b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771'
$script:p6ReaderSha256 = '1d58c06ecb3581ad7d8c9eef1dfcd653d8ba1e5b797b2dc7cda76b2ed621a96e'
$script:resultLimitBytes = 65536L
$script:artifactLimitBytes = 8388608L

function Fail([string]$Code) {
    if ($Code -notmatch '^[A-Z0-9_]{1,96}$') { $Code = 'HOST_STATIC_FAILURE' }
    if ($null -eq $script:failureCode) { $script:failureCode = $Code }
    throw $Code
}

function Test-PathWithin([string]$Root, [string]$Candidate) {
    $rootFull = [IO.Path]::GetFullPath($Root).TrimEnd('\')
    $candidateFull = [IO.Path]::GetFullPath($Candidate).TrimEnd('\')
    if ($candidateFull.Equals($rootFull, [StringComparison]::OrdinalIgnoreCase)) { return $true }
    $prefix = $rootFull + '\'
    return $candidateFull.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)
}

function Get-CanonicalPath([string]$Path, [string]$Code) {
    if ([String]::IsNullOrWhiteSpace($Path) -or -not [IO.Path]::IsPathRooted($Path)) { Fail $Code }
    try { $full = [IO.Path]::GetFullPath($Path) } catch { Fail $Code }
    if (-not $full.Equals($Path, [StringComparison]::Ordinal)) { Fail $Code }
    return $full
}

function Assert-NoReparseAncestors([string]$Path) {
    if (Get-Command Assert-VerifierNoReparseAncestors -ErrorAction SilentlyContinue) {
        Assert-VerifierNoReparseAncestors $Path
        return
    }
    try { $cursor = [IO.Path]::GetFullPath($Path) } catch { Fail 'PATH_CANONICALIZE' }
    while (-not (Test-Path -LiteralPath $cursor)) {
        $parent = Split-Path -Parent $cursor
        if ([String]::IsNullOrWhiteSpace($parent) -or
            $parent.Equals($cursor, [StringComparison]::OrdinalIgnoreCase)) { Fail 'PATH_ANCESTOR_MISSING' }
        $cursor = $parent
    }
    while ($true) {
        try { $item = Get-Item -LiteralPath $cursor -Force -ErrorAction Stop } catch { Fail 'PATH_ANCESTOR_METADATA' }
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Fail 'PATH_REPARSE' }
        $parent = Split-Path -Parent $cursor
        if ([String]::IsNullOrWhiteSpace($parent) -or
            $parent.Equals($cursor, [StringComparison]::OrdinalIgnoreCase)) { break }
        $cursor = $parent
    }
}

function Get-PathMetadata([string]$Path, [string]$Code) {
    try { $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop } catch { Fail $Code }
    if ($item -isnot [IO.FileInfo] -or
        ($item.Attributes -band [IO.FileAttributes]::Directory) -ne 0 -or
        ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Fail $Code }
    return [pscustomobject]@{
        length = [long]$item.Length
        attributes = [int]$item.Attributes
        creationTicks = [long]$item.CreationTimeUtc.Ticks
        writeTicks = [long]$item.LastWriteTimeUtc.Ticks
    }
}

function Get-StableDigest([string]$Path, [long]$MaximumBytes, [string]$Code) {
    Assert-NoReparseAncestors $Path
    $before = Get-PathMetadata $Path $Code
    if ($before.length -lt 0 -or $before.length -gt $MaximumBytes) { Fail ($Code + '_SIZE') }
    $stream = $null
    $sha = $null
    try {
        $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
        if ([long]$stream.Length -ne $before.length) { Fail ($Code + '_OPEN_LENGTH') }
        $sha = [Security.Cryptography.SHA256]::Create()
        $inputBuffer = New-Object byte[] $script:chunkBytes
        $outputBuffer = New-Object byte[] $script:chunkBytes
        $total = 0L
        while (($read = $stream.Read($inputBuffer, 0, $inputBuffer.Length)) -gt 0) {
            $total += [long]$read
            if ($total -gt $MaximumBytes) { Fail ($Code + '_READ_SIZE') }
            [void]$sha.TransformBlock($inputBuffer, 0, $read, $outputBuffer, 0)
        }
        [void]$sha.TransformFinalBlock([byte[]]@(), 0, 0)
        $digest = ([BitConverter]::ToString($sha.Hash)).Replace('-', '').ToLowerInvariant()
    } catch {
        if ($script:failureCode) { throw }
        Fail $Code
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
        if ($null -ne $sha) { $sha.Dispose() }
    }
    Assert-NoReparseAncestors $Path
    $after = Get-PathMetadata $Path $Code
    if ($total -ne $before.length -or $after.length -ne $before.length -or
        $after.attributes -ne $before.attributes -or
        $after.creationTicks -ne $before.creationTicks -or
        $after.writeTicks -ne $before.writeTicks) { Fail ($Code + '_METADATA_CHANGED') }
    return [pscustomobject]@{ bytes = [long]$total; sha256 = $digest; metadata = $after }
}

function Read-StableBytes([string]$Path, [long]$MaximumBytes, [string]$Code) {
    Assert-NoReparseAncestors $Path
    $before = Get-PathMetadata $Path $Code
    if ($before.length -lt 0 -or $before.length -gt $MaximumBytes) { Fail ($Code + '_SIZE') }
    $stream = $null
    $memory = $null
    try {
        $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
        if ([long]$stream.Length -ne $before.length) { Fail ($Code + '_OPEN_LENGTH') }
        $memory = New-Object IO.MemoryStream
        $buffer = New-Object byte[] $script:chunkBytes
        $total = 0L
        while (($read = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            $total += [long]$read
            if ($total -gt $MaximumBytes) { Fail ($Code + '_READ_SIZE') }
            $memory.Write($buffer, 0, $read)
        }
        if ($total -ne $before.length) { Fail ($Code + '_READ_LENGTH') }
        $bytes = $memory.ToArray()
    } catch {
        if ($script:failureCode) { throw }
        Fail $Code
    } finally {
        if ($null -ne $memory) { $memory.Dispose() }
        if ($null -ne $stream) { $stream.Dispose() }
    }
    Assert-NoReparseAncestors $Path
    $after = Get-PathMetadata $Path $Code
    if ($after.length -ne $before.length -or $after.attributes -ne $before.attributes -or
        $after.creationTicks -ne $before.creationTicks -or
        $after.writeTicks -ne $before.writeTicks) { Fail ($Code + '_METADATA_CHANGED') }
    return ,$bytes
}

function Get-BytesSha256([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Assert-NoDuplicateJsonKeys([string]$Text, [string]$Code) {
    $frames = New-Object 'System.Collections.Generic.List[object]'
    for ($i = 0; $i -lt $Text.Length; $i++) {
        $ch = $Text[$i]
        if ($ch -eq '"') {
            $start = $i
            $i++
            $closed = $false
            while ($i -lt $Text.Length) {
                if ($Text[$i] -eq '\') {
                    $i++
                    if ($i -ge $Text.Length) { Fail ($Code + '_JSON_STRING') }
                } elseif ($Text[$i] -eq '"') {
                    $closed = $true
                    break
                }
                $i++
            }
            if (-not $closed) { Fail ($Code + '_JSON_STRING') }
            $next = $i + 1
            while ($next -lt $Text.Length -and [char]::IsWhiteSpace($Text[$next])) { $next++ }
            if ($next -lt $Text.Length -and $Text[$next] -eq ':' -and $frames.Count -gt 0) {
                $frame = $frames[$frames.Count - 1]
                if ($frame.kind -ceq 'object') {
                    $tokenLength = $i - $start + 1
                    if ($tokenLength -gt 65536) { Fail ($Code + '_KEY_SIZE') }
                    try { $key = ConvertFrom-Json -InputObject $Text.Substring($start, $tokenLength) -ErrorAction Stop }
                    catch { Fail ($Code + '_KEY_ENCODING') }
                    if ($key -isnot [string] -or $frame.keys.Contains($key)) { Fail ($Code + '_DUPLICATE_KEY') }
                    [void]$frame.keys.Add($key)
                }
            }
            continue
        }
        if (($ch -eq 'N' -and $i + 3 -le $Text.Length -and $Text.Substring($i, 3) -ceq 'NaN') -or
            ($ch -eq 'I' -and $i + 8 -le $Text.Length -and $Text.Substring($i, 8) -ceq 'Infinity') -or
            ($ch -eq '-' -and $i + 9 -le $Text.Length -and $Text.Substring($i, 9) -ceq '-Infinity')) {
            Fail ($Code + '_NONFINITE')
        }
        if ($ch -eq '{') {
            $keySet = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
            [void]$frames.Add([pscustomobject]@{ kind = 'object'; keys = $keySet })
        } elseif ($ch -eq '[') {
            [void]$frames.Add([pscustomobject]@{ kind = 'array'; keys = $null })
        } elseif (($ch -eq '}' -or $ch -eq ']') -and $frames.Count -gt 0) {
            $frames.RemoveAt($frames.Count - 1)
        }
    }
}

function Read-StrictJsonBytes([byte[]]$Raw, [string]$Code, [long]$MaximumBytes) {
    if ($Raw.Length -lt 3 -or $Raw.Length -gt $MaximumBytes -or
        $Raw[$Raw.Length - 1] -ne 10 -or $Raw[0] -eq 239 -or
        $Raw[1] -eq 187 -or $Raw[2] -eq 191 -or
        [Array]::IndexOf($Raw, [byte]13) -ge 0 -or [Array]::IndexOf($Raw, [byte]0) -ge 0) {
        Fail ($Code + '_ENCODING')
    }
    $newlineCount = 0
    foreach ($byte in $Raw) { if ($byte -eq 10) { $newlineCount++ } }
    if ($newlineCount -ne 1) { Fail ($Code + '_EOF') }
    try { $text = $script:strictUtf8.GetString($Raw, 0, $Raw.Length - 1) }
    catch { Fail ($Code + '_UTF8') }
    Assert-NoDuplicateJsonKeys $text $Code
    try { $value = ConvertFrom-Json -InputObject $text -ErrorAction Stop }
    catch { Fail ($Code + '_JSON') }
    return [pscustomobject]@{ value = $value; text = $text }
}

function Read-StrictJsonFile([string]$Path, [long]$MaximumBytes, [string]$Code) {
    $raw = Read-StableBytes $Path $MaximumBytes $Code
    $parsed = Read-StrictJsonBytes $raw $Code $MaximumBytes
    return [pscustomobject]@{ value = $parsed.value; text = $parsed.text; raw = $raw; sha256 = Get-BytesSha256 $raw }
}

function Read-ValidatedJsonFile([string]$Path, [long]$MaximumBytes, [string]$Code) {
    $raw = Read-StableBytes $Path $MaximumBytes $Code
    if ($raw.Length -lt 3 -or $raw[$raw.Length - 1] -ne 10 -or
        $raw[0] -eq 239 -or $raw[1] -eq 187 -or $raw[2] -eq 191 -or
        [Array]::IndexOf($raw, [byte]0) -ge 0) { Fail ($Code + '_ENCODING') }
    try { $text = $script:strictUtf8.GetString($raw, 0, $raw.Length - 1) }
    catch { Fail ($Code + '_UTF8') }
    Assert-NoDuplicateJsonKeys $text $Code
    try { $value = ConvertFrom-Json -InputObject $text -ErrorAction Stop }
    catch { Fail ($Code + '_JSON') }
    return [pscustomobject]@{ value = $value; text = $text; raw = $raw; sha256 = Get-BytesSha256 $raw }
}

function Assert-ExactKeys($Value, [string[]]$Expected, [string]$Code) {
    if ($null -eq $Value -or $Value -isnot [pscustomobject]) { Fail ($Code + '_OBJECT') }
    $actual = @($Value.PSObject.Properties | ForEach-Object { [string]$_.Name })
    if ($actual.Count -ne $Expected.Count) { Fail ($Code + '_KEYS') }
    $set = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
    foreach ($name in $actual) { [void]$set.Add($name) }
    foreach ($name in $Expected) { if (-not $set.Contains($name)) { Fail ($Code + '_KEYS') } }
}

function Assert-JsonInteger($Value, [long]$Minimum, [long]$Maximum, [string]$Code) {
    if ($Value -is [bool] -or -not ($Value -is [int] -or $Value -is [long])) { Fail $Code }
    $number = [long]$Value
    if ($number -lt $Minimum -or $number -gt $Maximum) { Fail $Code }
    return $number
}

function Assert-Sha256([string]$Value, [string]$Code) {
    if ($Value -cnotmatch '^[0-9a-f]{64}$') { Fail $Code }
}

function Assert-DirectoryPath([string]$Path, [string]$Code) {
    $full = Get-CanonicalPath $Path $Code
    Assert-NoReparseAncestors $full
    try { $item = Get-Item -LiteralPath $full -Force -ErrorAction Stop } catch { Fail $Code }
    if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Fail $Code }
    return $full
}

function Assert-ExactLimits($Limits) {
    $names = @('workNanos','groupNanos','groups','batchEntries','cloneEntries','observationUnits',
        'artifactBytes','resultBytes','lineBytes','readChunkBytes','specBytes','parentMs','workerMs','cleanupMs','outerMs')
    Assert-ExactKeys $Limits $names 'SPEC_LIMITS'
    $expected = [ordered]@{
        workNanos = 90000000000L; groupNanos = 5000000000L; groups = 640L
        batchEntries = 4096L; cloneEntries = 1048576L; observationUnits = 32768L
        artifactBytes = 8388608L; resultBytes = 65536L; lineBytes = 65536L
        readChunkBytes = 32768L; specBytes = 4194304L; parentMs = 180000L
        workerMs = 180000L; cleanupMs = 120000L; outerMs = 1800000L
    }
    foreach ($entry in $expected.GetEnumerator()) {
        $actual = Assert-JsonInteger $Limits.($entry.Key) 0 1000000000000L 'SPEC_LIMIT_VALUE'
        if ($actual -ne [long]$entry.Value) { Fail 'SPEC_LIMIT_VALUE' }
    }
}

function Get-SpecAndPins([string]$Path, [string]$ExpectedSha) {
    $specFull = Get-CanonicalPath $Path 'SPEC_PATH'
    $raw = Read-StableBytes $specFull 4194304L 'SPEC_READ'
    $sha = Get-BytesSha256 $raw
    if ($sha -cne $ExpectedSha) { Fail 'SPEC_HASH' }
    $parsed = Read-StrictJsonBytes $raw 'SPEC' 4194304L
    $specValue = $parsed.value
    $specNames = @('schema','parent','repositoryRoot','sourceManifest','sourceManifestSha256',
        'parentReader','parentReaderSha256','parentPins','parentPinsSha256','parentRun',
        'parentQualification','parentQualificationSha256','transport','transportSha256',
        'blockers','blockersSha256','stages','stagesSha256','cutInput','cutInputSha256',
        'plan','planSha256','protocol','protocolSha256','schemaDocument','schemaDocumentSha256',
        'addendum','addendumSha256','analysisModule','analysisModuleSha256','worker','workerSha256',
        'tests','testsSha256','graphOracle','graphOracleSha256','viaOracle','viaOracleSha256',
        'captureReader','captureReaderSha256','selectedPython','selectedPythonSha256',
        'selectedPowerShell','selectedPowerShellSha256','files','limits')
    Assert-ExactKeys $specValue $specNames 'SPEC'
    if ($specValue.schema -cne 'tsj-canonical-cut-sensitivity-input-v1' -or
        $specValue.parent -cne '23f94f8f4ea8025fe24475fabcb194840e472fd8') { Fail 'SPEC_IDENTITY' }
    Assert-ExactLimits $specValue.limits
    $pathFields = @('sourceManifest','parentReader','parentPins',
        'parentQualification','transport','blockers','stages','cutInput','plan','protocol',
        'schemaDocument','addendum','analysisModule','worker','tests','graphOracle','viaOracle',
        'captureReader','selectedPython','selectedPowerShell')
    foreach ($field in $pathFields) {
        $specValue.$field = Get-CanonicalPath ([string]$specValue.$field) 'SPEC_FILE_PATH'
        $hashField = $field + 'Sha256'
        Assert-Sha256 ([string]$specValue.$hashField) 'SPEC_FILE_SHA'
    }
    $specValue.parentRun = Assert-DirectoryPath $specValue.parentRun 'PARENT_RUN_PATH'
    $specValue.repositoryRoot = Assert-DirectoryPath $specValue.repositoryRoot 'REPOSITORY_ROOT'
    foreach ($field in @('sourceManifestSha256','parentReaderSha256','parentPinsSha256',
        'parentQualificationSha256','transportSha256','blockersSha256','stagesSha256',
        'cutInputSha256','planSha256','protocolSha256','schemaDocumentSha256','addendumSha256',
        'analysisModuleSha256','workerSha256','testsSha256','graphOracleSha256','viaOracleSha256',
        'captureReaderSha256','selectedPythonSha256','selectedPowerShellSha256')) {
        Assert-Sha256 ([string]$specValue.$field) 'SPEC_FILE_SHA'
    }
    if ($specValue.parentQualificationSha256 -cne $script:p6QualificationSha256 -or
        $specValue.parentPinsSha256 -cne $script:p6InputPinsSha256 -or
        $specValue.parentReaderSha256 -cne $script:p6ReaderSha256) { Fail 'P6_SOURCE_BINDING' }
    if ((Split-Path -Leaf $specValue.parentRun) -cne 'observe-r6') { Fail 'P6_PARENT_RUN_IDENTITY' }

    if ($specValue.files -isnot [System.Array] -or $specValue.files.Count -lt 1) { Fail 'SPEC_FILE_CENSUS' }
    $pins = New-Object 'System.Collections.Generic.Dictionary[string,object]' ([StringComparer]::OrdinalIgnoreCase)
    foreach ($row in $specValue.files) {
        Assert-ExactKeys $row @('path','bytes','sha256') 'SPEC_FILE_ROW'
        $row.path = Get-CanonicalPath ([string]$row.path) 'SPEC_FILE_PATH'
        $row.bytes = Assert-JsonInteger $row.bytes 0 $script:sourceBytesLimit 'SPEC_FILE_BYTES'
        Assert-Sha256 ([string]$row.sha256) 'SPEC_FILE_SHA'
        if ($pins.ContainsKey($row.path)) { Fail 'SPEC_FILE_DUPLICATE' }
        $pins.Add($row.path, $row)
    }
    $requiredPins = @(
        @($specValue.sourceManifest, $specValue.sourceManifestSha256),
        @($specValue.parentReader, $specValue.parentReaderSha256),
        @($specValue.parentPins, $specValue.parentPinsSha256),
        @($specValue.parentQualification, $specValue.parentQualificationSha256),
        @($specValue.transport, $specValue.transportSha256),
        @($specValue.blockers, $specValue.blockersSha256),
        @($specValue.stages, $specValue.stagesSha256),
        @($specValue.cutInput, $specValue.cutInputSha256),
        @($specValue.plan, $specValue.planSha256),
        @($specValue.protocol, $specValue.protocolSha256),
        @($specValue.schemaDocument, $specValue.schemaDocumentSha256),
        @($specValue.addendum, $specValue.addendumSha256),
        @($specValue.analysisModule, $specValue.analysisModuleSha256),
        @($specValue.worker, $specValue.workerSha256),
        @($specValue.tests, $specValue.testsSha256),
        @($specValue.graphOracle, $specValue.graphOracleSha256),
        @($specValue.viaOracle, $specValue.viaOracleSha256),
        @($specValue.captureReader, $specValue.captureReaderSha256),
        @($specValue.selectedPython, $specValue.selectedPythonSha256),
        @($specValue.selectedPowerShell, $specValue.selectedPowerShellSha256)
    )
    foreach ($pin in $requiredPins) {
        if (-not $pins.ContainsKey([string]$pin[0]) -or
            $pins[[string]$pin[0]].sha256 -cne [string]$pin[1]) { Fail 'SPEC_FILE_PIN_MISSING' }
    }
    $p6PinsFile = Read-ValidatedJsonFile $specValue.parentPins 4194304L 'P6_INPUT_PINS'
    if ($p6PinsFile.sha256 -cne $script:p6InputPinsSha256) { Fail 'P6_INPUT_PINS_HASH' }
    $p6Pins = $p6PinsFile.value
    if ($p6Pins.repositoryRoot -isnot [string] -or $p6Pins.sourceRows -isnot [System.Array] -or
        $p6Pins.sourceRows.Count -ne 1084) { Fail 'P6_SOURCE_INVENTORY' }
    $p6Pins.repositoryRoot = Assert-DirectoryPath ([string]$p6Pins.repositoryRoot) 'P6_REPOSITORY_ROOT'
    foreach ($p6Row in $p6Pins.sourceRows) {
        Assert-ExactKeys $p6Row @('role','path','bytes','sha256') 'P6_SOURCE_ROW'
    }
    $script:p6InputPins = $p6Pins
    $specDir = Split-Path -Parent $specFull
    $hostFull = Get-CanonicalPath $PSCommandPath 'HOST_SCRIPT_PATH'
    if (-not $hostFull.Equals((Join-Path $specDir 'run-cut-sensitivity.ps1'), [StringComparison]::OrdinalIgnoreCase) -or
        -not $pins.ContainsKey($hostFull)) { Fail 'HOST_SCRIPT_BINDING' }
    if (-not ([string]$specValue.worker).Equals((Join-Path $specDir (Split-Path -Leaf $specValue.worker)), [StringComparison]::OrdinalIgnoreCase)) {
        Fail 'WORKER_SOURCE_BINDING'
    }
    foreach ($leafName in @('SOURCE_CENSUS_R3.md','read_cut_sensitivity.py')) {
        $matching = @($specValue.files | Where-Object { (Split-Path -Leaf ([string]$_.path)) -ceq $leafName })
        if ($matching.Count -ne 1 -or
            -not ([IO.Path]::GetDirectoryName([string]$matching[0].path)).Equals($specDir, [StringComparison]::OrdinalIgnoreCase)) {
            Fail 'R3_SOURCE_CENSUS_BINDING'
        }
    }
    $module = Join-Path $specValue.repositoryRoot 'scripts\VerifierIsolation.psm1'
    if (-not $pins.ContainsKey($module) -or $pins[$module].sha256 -cne $script:verifierIsolationSha256) {
        Fail 'VERIFIER_ISOLATION_PIN'
    }
    $script:modulePath = $module
    $script:pythonPath = $specValue.selectedPython
    $script:parentRunRoot = $specValue.parentRun
    $script:parentReaderPath = $specValue.parentReader
    $script:parentPinsPath = $specValue.parentPins
    $script:parentPinsSha256 = $specValue.parentPinsSha256
    $script:spec = [pscustomobject]@{ path = $specFull; sha256 = $sha; raw = $raw }
    $script:specValue = $specValue
    $script:specPins = $pins
    return $specValue
}

function Assert-ModuleToolAndHost() {
    $moduleDigest = Get-StableDigest $script:modulePath 4194304L 'VERIFIER_ISOLATION_READ'
    if ($moduleDigest.sha256 -cne $script:verifierIsolationSha256) { Fail 'VERIFIER_ISOLATION_HASH' }
    $pythonDigest = Get-StableDigest $script:pythonPath $script:sourceBytesLimit 'SELECTED_PYTHON_READ'
    if ($pythonDigest.sha256 -cne $script:specValue.selectedPythonSha256) { Fail 'SELECTED_PYTHON_HASH' }
    $psPath = Get-CanonicalPath $script:specValue.selectedPowerShell 'SELECTED_POWERSHELL_PATH'
    $psDigest = Get-StableDigest $psPath $script:sourceBytesLimit 'SELECTED_POWERSHELL_READ'
    if ($psDigest.sha256 -cne $script:specValue.selectedPowerShellSha256) { Fail 'SELECTED_POWERSHELL_HASH' }
    if ($PSVersionTable.PSVersion.Major -ne 5) { Fail 'HOST_POWERSHELL_VERSION' }
    try {
        $current = Get-Process -Id $PID -ErrorAction Stop
        $exe = Get-CanonicalPath $current.MainModule.FileName 'HOST_EXECUTABLE'
        $startTicks = [long]$current.StartTime.ToUniversalTime().Ticks
    } catch { Fail 'HOST_PROCESS_IDENTITY' }
    if (-not $exe.Equals($psPath, [StringComparison]::OrdinalIgnoreCase) -or $startTicks -le 0) {
        Fail 'HOST_PROCESS_BINDING'
    }
    $hostScript = Get-CanonicalPath $PSCommandPath 'HOST_SCRIPT_PATH'
    $hostDigest = Get-StableDigest $hostScript 1048576L 'HOST_SCRIPT_READ'
    if (-not $script:specPins.ContainsKey($hostScript) -or
        $hostDigest.sha256 -cne $script:specPins[$hostScript].sha256) { Fail 'HOST_SCRIPT_HASH' }
    $script:hostIdentity = [pscustomobject]@{
        pid = [int]$PID; startTicks = $startTicks; executable = $exe
        script = $hostScript; scriptSha256 = $hostDigest.sha256
    }
    try { Import-Module $script:modulePath -Force -DisableNameChecking -ErrorAction Stop }
    catch { Fail 'VERIFIER_ISOLATION_IMPORT' }
    if (-not (Get-Command Invoke-VerifierBoundedProcess -ErrorAction SilentlyContinue) -or
        -not (Get-Command Assert-VerifierNoReparseAncestors -ErrorAction SilentlyContinue)) {
        Fail 'VERIFIER_ISOLATION_API'
    }
}

function Get-SourceInventory() {
    $inventoryFile = Read-StrictJsonFile $script:specValue.sourceManifest 4194304L 'SOURCE_INVENTORY'
    if ($inventoryFile.sha256 -cne $script:specValue.sourceManifestSha256) { Fail 'SOURCE_INVENTORY_HASH' }
    $inventory = $inventoryFile.value
    Assert-ExactKeys $inventory @('schema','parent','repositoryRoot','parentSourceRoot','files') 'SOURCE_INVENTORY'
    if ($inventory.schema -cne 'tsj-canonical-cut-source-inventory-v1' -or
        $inventory.parent -cne '23f94f8f4ea8025fe24475fabcb194840e472fd8') { Fail 'SOURCE_INVENTORY_IDENTITY' }
    $repo = Assert-DirectoryPath $inventory.repositoryRoot 'SOURCE_D_ROOT'
    $parent = Assert-DirectoryPath $inventory.parentSourceRoot 'SOURCE_C_ROOT'
    if (-not $repo.Equals($script:specValue.repositoryRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $repo.Equals($parent, [StringComparison]::OrdinalIgnoreCase)) { Fail 'SOURCE_ROOT_BINDING' }
    if ($inventory.files -isnot [System.Array] -or $inventory.files.Count -ne 1084) { Fail 'SOURCE_ROW_CENSUS' }
    $seen = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
    foreach ($row in $inventory.files) {
        Assert-ExactKeys $row @('role','path','bytes','sha256') 'SOURCE_ROW'
        if ($row.role -isnot [string] -or [String]::IsNullOrWhiteSpace($row.role)) { Fail 'SOURCE_ROW_ROLE' }
        if ($row.path -isnot [string] -or [String]::IsNullOrWhiteSpace($row.path) -or
            $row.path.Contains('\') -or $row.path.StartsWith('/') -or $row.path.Contains(':')) { Fail 'SOURCE_ROW_PATH' }
        $segments = $row.path.Split('/')
        if (@($segments | Where-Object { [String]::IsNullOrEmpty($_) -or $_ -ceq '.' -or $_ -ceq '..' }).Count -gt 0) {
            Fail 'SOURCE_ROW_PATH'
        }
        if (-not $seen.Add([string]$row.path)) { Fail 'SOURCE_ROW_DUPLICATE' }
        $row.bytes = Assert-JsonInteger $row.bytes 0 $script:sourceBytesLimit 'SOURCE_ROW_BYTES'
        Assert-Sha256 ([string]$row.sha256) 'SOURCE_ROW_SHA'
    }
    if (-not $parent.Equals($script:p6InputPins.repositoryRoot, [StringComparison]::OrdinalIgnoreCase)) {
        Fail 'SOURCE_PARENT_ROOT_P6_BINDING'
    }
    if ($script:p6InputPins.sourceRows.Count -ne $inventory.files.Count) { Fail 'SOURCE_P6_ROW_CENSUS' }
    for ($rowIndex = 0; $rowIndex -lt $inventory.files.Count; $rowIndex++) {
        $actualRow = $inventory.files[$rowIndex]
        $pinnedRow = $script:p6InputPins.sourceRows[$rowIndex]
        $actualBytes = Assert-JsonInteger $actualRow.bytes 0 $script:sourceBytesLimit 'SOURCE_ROW_BYTES'
        $pinnedBytes = Assert-JsonInteger $pinnedRow.bytes 0 $script:sourceBytesLimit 'P6_SOURCE_ROW_BYTES'
        if ($actualRow.role -cne $pinnedRow.role -or $actualRow.path -cne $pinnedRow.path -or
            $actualBytes -ne $pinnedBytes -or $actualRow.sha256 -cne $pinnedRow.sha256) {
            Fail 'SOURCE_P6_ROW_BINDING'
        }
    }
    $script:sourceInventory = [pscustomobject]@{ value = $inventory; rawSha256 = $inventoryFile.sha256 }
    return $script:sourceInventory
}

function Get-RelativeSourcePath([string]$Root, [string]$Relative, [string]$Code) {
    $nativeRelative = $Relative.Replace('/', '\')
    $full = [IO.Path]::GetFullPath((Join-Path $Root $nativeRelative))
    if (-not (Test-PathWithin $Root $full) -or $full.Equals($Root, [StringComparison]::OrdinalIgnoreCase)) { Fail $Code }
    return $full
}

function Invoke-SourceAudit([string]$Which) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $inventory = $script:sourceInventory.value
    $roots = @([string]$inventory.repositoryRoot, [string]$inventory.parentSourceRoot)
    foreach ($root in $roots) { [void](Assert-DirectoryPath $root 'SOURCE_ROOT') }
    foreach ($row in $inventory.files) {
        foreach ($root in $roots) {
            $path = Get-RelativeSourcePath $root ([string]$row.path) 'SOURCE_ROW_PATH'
            $actual = Get-StableDigest $path $script:sourceBytesLimit 'SOURCE_FILE_READ'
            if ($actual.bytes -ne [long]$row.bytes -or $actual.sha256 -cne [string]$row.sha256) {
                Fail 'SOURCE_FILE_HASH'
            }
        }
    }
    $extraFiles = 0
    foreach ($row in $script:specValue.files) {
        $path = [string]$row.path
        $actual = Get-StableDigest $path $script:sourceBytesLimit 'PINNED_FILE_READ'
        if ($actual.bytes -ne [long]$row.bytes -or $actual.sha256 -cne [string]$row.sha256) {
            Fail 'PINNED_FILE_HASH'
        }
        if (-not (Test-PathWithin $roots[0] $path) -and -not (Test-PathWithin $roots[1] $path)) { $extraFiles++ }
    }
    $specDigest = Get-StableDigest $script:spec.path 4194304L 'SPEC_POST_READ'
    if ($specDigest.sha256 -cne $script:spec.sha256) { Fail 'SPEC_CHANGED' }
    $watch.Stop()
    return [pscustomobject]@{
        status = 'PASS'; sourceRows = 1084; sourceFiles = 2168
        extraFiles = [int]$extraFiles; elapsedMs = [Math]::Round($watch.Elapsed.TotalMilliseconds, 3)
    }
}

function Get-OutputSourceRoots() {
    $roots = New-Object 'System.Collections.Generic.List[string]'
    foreach ($path in @($script:specValue.repositoryRoot, $script:sourceInventory.value.parentSourceRoot,
        $script:specValue.parentRun, (Split-Path -Parent $script:specValue.parentReader),
        (Split-Path -Parent $script:spec.path), $PSScriptRoot)) {
        if (-not [String]::IsNullOrWhiteSpace([string]$path)) {
            $full = [IO.Path]::GetFullPath([string]$path)
            if (-not @($roots | Where-Object { $_.Equals($full, [StringComparison]::OrdinalIgnoreCase) }).Count) {
                [void]$roots.Add($full)
            }
        }
    }
    foreach ($row in $script:specValue.files) {
        $parent = Split-Path -Parent ([string]$row.path)
        if (-not @($roots | Where-Object { $_.Equals($parent, [StringComparison]::OrdinalIgnoreCase) }).Count) {
            [void]$roots.Add($parent)
        }
    }
    return $roots.ToArray()
}

function Assert-OutputRoot([string]$Requested, [bool]$Create) {
    $full = Get-CanonicalPath $Requested 'OUTPUT_ROOT_PATH'
    $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if (-not (Test-PathWithin $temp $full) -or $full.TrimEnd('\').Equals($temp.TrimEnd('\'), [StringComparison]::OrdinalIgnoreCase)) {
        Fail 'OUTPUT_ROOT_NOT_TEMP'
    }
    foreach ($root in (Get-OutputSourceRoots)) {
        if (Test-PathWithin $root $full) { Fail 'OUTPUT_ROOT_INSIDE_SOURCE' }
    }
    Assert-NoReparseAncestors $full
    if (Test-Path -LiteralPath $full) { Fail 'OUTPUT_ROOT_EXISTS' }
    if ($Create) {
        try { [void][IO.Directory]::CreateDirectory($full) } catch { Fail 'OUTPUT_ROOT_CREATE' }
        Assert-NoReparseAncestors $full
        $item = Get-Item -LiteralPath $full -Force -ErrorAction Stop
        if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
            @(Get-ChildItem -LiteralPath $full -Force).Count -ne 0) { Fail 'OUTPUT_ROOT_CREATE_IDENTITY' }
        $script:outputCreationTicks = [long]$item.CreationTimeUtc.Ticks
        $script:outputRootFull = $full
        $script:outputCreated = $true
        $script:temporaryOutputRetained = $true
        return $full
    }
    return $full
}

function Assert-OutputIdentity() {
    if (-not $script:outputCreated) { Fail 'OUTPUT_ROOT_NOT_OWNED' }
    Assert-NoReparseAncestors $script:outputRootFull
    $item = Get-Item -LiteralPath $script:outputRootFull -Force -ErrorAction Stop
    if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
        [long]$item.CreationTimeUtc.Ticks -ne $script:outputCreationTicks) { Fail 'OUTPUT_ROOT_IDENTITY_CHANGED' }
}

function Write-CreateNewBytes([string]$Path, [byte[]]$Bytes, [string]$Code) {
    Assert-NoReparseAncestors $Path
    $stream = $null
    try {
        $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
        $stream.Write($Bytes, 0, $Bytes.Length)
        $stream.Flush($true)
    } catch {
        if ($script:failureCode) { throw }
        Fail $Code
    } finally { if ($null -ne $stream) { $stream.Dispose() } }
    $actual = Read-StableBytes $Path ([long]$Bytes.Length) $Code
    if ((Get-BytesSha256 $actual) -cne (Get-BytesSha256 $Bytes)) { Fail ($Code + '_VERIFY') }
}

function ConvertTo-JsonBytes($Value, [long]$MaximumBytes, [string]$Code) {
    try { $json = ConvertTo-Json -InputObject $Value -Depth 40 -Compress -ErrorAction Stop }
    catch { Fail ($Code + '_SERIALIZE') }
    $raw = $script:utf8.GetBytes($json + "`n")
    if ($raw.Length -gt $MaximumBytes) { Fail ($Code + '_SIZE') }
    return ,$raw
}

function Write-CreateNewJson([string]$Path, $Value, [long]$MaximumBytes, [string]$Code) {
    $raw = ConvertTo-JsonBytes $Value $MaximumBytes $Code
    Write-CreateNewBytes $Path $raw $Code
    return $raw
}

function Test-ProcessShape($Receipt, [string]$Executable, [string[]]$Arguments, [string]$Code) {
    Assert-ExactKeys $Receipt @('FilePath','Arguments','ProcessId','ProcessStartTicks','ExitCode',
        'Stdout','Stderr','StdoutLog','StderrLog','ProcessRoot','TerminationProven') $Code
    if (-not ([string]$Receipt.FilePath).Equals($Executable, [StringComparison]::OrdinalIgnoreCase) -or
        $Receipt.Arguments -isnot [System.Array] -or $Receipt.Arguments.Count -ne $Arguments.Count) { Fail ($Code + '_INVOCATION') }
    for ($i = 0; $i -lt $Arguments.Count; $i++) {
        if ([string]$Receipt.Arguments[$i] -cne [string]$Arguments[$i]) { Fail ($Code + '_ARGUMENTS') }
    }
    [void](Assert-JsonInteger $Receipt.ProcessId 1 ([int]::MaxValue) ($Code + '_PID'))
    [void](Assert-JsonInteger $Receipt.ProcessStartTicks 1 3155378975999999999L ($Code + '_START_TICKS'))
    [void](Assert-JsonInteger $Receipt.ExitCode ([int]::MinValue) ([int]::MaxValue) ($Code + '_EXIT'))
    if ($Receipt.TerminationProven -isnot [bool]) { Fail ($Code + '_TERMINATION_TYPE') }
    foreach ($field in @('Stdout','Stderr','StdoutLog','StderrLog','ProcessRoot')) {
        if ($Receipt.$field -isnot [string]) { Fail ($Code + '_STRING_FIELDS') }
    }
}

function Save-ProcessReceipt([string]$Name, $Receipt, [double]$WallMs, [string[]]$Arguments) {
    Test-ProcessShape $Receipt $script:pythonPath $Arguments ($Name.ToUpperInvariant() + '_PROCESS')
    $receiptPath = Join-Path $script:outputRootFull ($Name + '-process.json')
    $raw = Write-CreateNewJson $receiptPath $Receipt $script:artifactLimitBytes ($Name.ToUpperInvariant() + '_PROCESS_SAVE')
    $stdoutBytes = $script:utf8.GetBytes([string]$Receipt.Stdout)
    $stderrBytes = $script:utf8.GetBytes([string]$Receipt.Stderr)
    if (($stdoutBytes.Length + $stderrBytes.Length) -gt $script:artifactLimitBytes) { Fail ($Name.ToUpperInvariant() + '_STREAMS_SIZE') }
    Write-CreateNewBytes (Join-Path $script:outputRootFull ($Name + '.stdout')) $stdoutBytes ($Name.ToUpperInvariant() + '_STDOUT_SAVE')
    Write-CreateNewBytes (Join-Path $script:outputRootFull ($Name + '.stderr')) $stderrBytes ($Name.ToUpperInvariant() + '_STDERR_SAVE')
    return [pscustomobject]@{
        relation = [pscustomobject]@{ receipt = ($Name + '-process.json'); sha256 = (Get-BytesSha256 $raw); wallMs = [Math]::Round($WallMs, 3) }
        receipt = $Receipt
    }
}

function Assert-OneJsonLine([string]$Text, [string]$Code, [long]$MaximumBytes) {
    if ([String]::IsNullOrEmpty($Text)) { Fail ($Code + '_EMPTY') }
    $raw = $script:utf8.GetBytes($Text)
    if ($raw.Length -gt $MaximumBytes -or -not $Text.EndsWith("`n", [StringComparison]::Ordinal) -or
        $Text.Contains("`r") -or $Text.IndexOf("`n") -ne ($Text.Length - 1)) { Fail ($Code + '_LINE') }
    $parsed = Read-StrictJsonBytes $raw $Code $MaximumBytes
    if ($parsed.value -isnot [pscustomobject]) { Fail ($Code + '_OBJECT') }
    return $parsed.value
}

function Assert-ParentReaderResult($Receipt) {
    if ($Receipt.ExitCode -ne 0 -or $Receipt.TerminationProven -ne $true -or
        -not [String]::IsNullOrEmpty([string]$Receipt.Stderr)) { Fail 'PARENT_PROCESS_REJECTED' }
    # The unchanged selected P6 reader prints through Windows text stdout.
    # Preserve its CRLF receipt/copy bytes; normalize only the local parse view.
    $text = [string]$Receipt.Stdout
    if (-not $text.EndsWith("`r`n", [StringComparison]::Ordinal) -or
        $text.IndexOf("`r") -ne $text.Length - 2 -or
        $text.IndexOf("`n") -ne $text.Length - 1 -or
        $script:utf8.GetByteCount($text) -gt 65536) { Fail 'PARENT_STDOUT_LINE' }
    $localParse = $text.Substring(0, $text.Length - 2) + "`n"
    $result = Assert-OneJsonLine $localParse 'PARENT_STDOUT' 65536L
    if ($result.status -cne 'PASS_STAGE_OBSERVATIONS_ONLY') { Fail 'PARENT_STATUS' }
    return $result
}

function Assert-WorkerResult($Receipt, [string]$ExpectedMode) {
    if ($Receipt.ExitCode -ne 0 -or $Receipt.TerminationProven -ne $true -or
        -not [String]::IsNullOrEmpty([string]$Receipt.Stderr)) { Fail 'WORKER_PROCESS_REJECTED' }
    $stdout = [string]$Receipt.Stdout
    $marker = 'CANONICAL_CUT_SENSITIVITY_COMPLETE '
    if ($script:utf8.GetByteCount($stdout) -gt 65536) { Fail 'WORKER_INDEX_SIZE' }
    if (-not $stdout.StartsWith($marker, [StringComparison]::Ordinal) -or
        -not $stdout.EndsWith("`n", [StringComparison]::Ordinal) -or
        $stdout.Contains("`r") -or $stdout.IndexOf("`n") -ne ($stdout.Length - 1)) { Fail 'WORKER_INDEX_MARKER' }
    $indexText = $stdout.Substring($marker.Length)
    $index = Assert-OneJsonLine $indexText 'WORKER_INDEX' 65536L
    Assert-ExactKeys $index @('schema','status','mode','specSha256','result','aggregateBytes','elapsedNanos','work') 'WORKER_INDEX'
    $expectedStatus = if ($ExpectedMode -ceq 'CANARY') { 'PASS_LITERAL_HOST_CANARY_ONLY' } else { 'PASS_CONDITIONAL_CUT_SENSITIVITY_ONLY' }
    if ($index.schema -cne 'tsj-canonical-cut-sensitivity-worker-v1' -or
        $index.status -cne $expectedStatus -or $index.mode -cne $ExpectedMode -or
        $index.specSha256 -cne $script:spec.sha256) { Fail 'WORKER_INDEX_IDENTITY' }
    Assert-ExactKeys $index.result @('path','bytes','sha256') 'WORKER_RESULT_REF'
    if ($index.result.path -cne 'sensitivity-result.json') { Fail 'WORKER_RESULT_PATH' }
    $resultBytes = Assert-JsonInteger $index.result.bytes 2 $script:resultLimitBytes 'WORKER_RESULT_BYTES'
    $aggregate = Assert-JsonInteger $index.aggregateBytes 0 $script:artifactLimitBytes 'WORKER_AGGREGATE'
    if ($aggregate -ne $resultBytes) { Fail 'WORKER_AGGREGATE_BINDING' }
    Assert-Sha256 ([string]$index.result.sha256) 'WORKER_RESULT_SHA'
    $elapsed = Assert-JsonInteger $index.elapsedNanos 1 90000000000L 'WORKER_INDEX_ELAPSED'
    Assert-ExactKeys $index.work @('groups','elapsedNanos','maximumGroupNanos','copyEntries','copyUnits',
        'bfsPops','bfsUnits','opaqueCalls','totalObservationUnits','actualNewP07Steps',
        'actualNewP07Advances','actualNewP07Expansions') 'WORKER_FINAL_WORK'
    $groups = Assert-JsonInteger $index.work.groups 1 639 'WORKER_GROUPS'
    $budgetElapsed = Assert-JsonInteger $index.work.elapsedNanos 1 90000000000L 'WORKER_BUDGET_ELAPSED'
    $maxGroup = Assert-JsonInteger $index.work.maximumGroupNanos 1 5000000000L 'WORKER_MAX_GROUP'
    foreach ($field in @('copyEntries','copyUnits','bfsPops','bfsUnits','opaqueCalls','totalObservationUnits',
        'actualNewP07Steps','actualNewP07Advances','actualNewP07Expansions')) {
        [void](Assert-JsonInteger $index.work.$field 0 8388608L ('WORKER_' + $field.ToUpperInvariant()))
    }
    $copyEntries = Assert-JsonInteger $index.work.copyEntries 0 2621440L 'WORKER_COPY_ENTRIES'
    $copyUnits = Assert-JsonInteger $index.work.copyUnits 0 32768L 'WORKER_COPY_UNITS'
    $bfsPops = Assert-JsonInteger $index.work.bfsPops 0 1181616L 'WORKER_BFS_POPS'
    $bfsUnits = Assert-JsonInteger $index.work.bfsUnits 0 32768L 'WORKER_BFS_UNITS'
    $opaqueCalls = Assert-JsonInteger $index.work.opaqueCalls 0 640L 'WORKER_OPAQUE_CALLS'
    if ($budgetElapsed -ne $elapsed -or $index.work.totalObservationUnits -ne $groups -or
        $copyUnits -gt $groups -or $bfsUnits -gt $groups -or $opaqueCalls -gt $groups -or
        $script:workerWallNanos -gt 0 -and $elapsed -gt $script:workerWallNanos -or
        $index.work.actualNewP07Steps -ne 0 -or $index.work.actualNewP07Advances -ne 0 -or
        $index.work.actualNewP07Expansions -ne 0) { Fail 'WORKER_FINAL_WORK_BINDING' }
    $dataItem = Get-Item -LiteralPath $script:outputDataRoot -Force -ErrorAction Stop
    if (-not $dataItem.PSIsContainer -or ($dataItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Fail 'WORKER_DATA_ROOT' }
    $items = @(Get-ChildItem -LiteralPath $script:outputDataRoot -Force)
    if ($items.Count -ne 1 -or $items[0].Name -cne 'sensitivity-result.json' -or
        $items[0].PSIsContainer -or ($items[0].Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Fail 'WORKER_OUTPUT_CENSUS' }
    $resultPath = Join-Path $script:outputDataRoot 'sensitivity-result.json'
    $raw = Read-StableBytes $resultPath $script:resultLimitBytes 'WORKER_RESULT_READ'
    if ($raw.Length -ne $resultBytes -or (Get-BytesSha256 $raw) -cne [string]$index.result.sha256) { Fail 'WORKER_RESULT_BINDING' }
    $resultParsed = Read-StrictJsonBytes $raw 'WORKER_RESULT' $script:resultLimitBytes
    $result = $resultParsed.value
    Assert-ExactKeys $result @('schema','status','mode','specSha256','scope','parent','source',
        'graph','cut','cases','work','acceptance','limitations') 'WORKER_RESULT'
    if ($result.schema -cne 'tsj-canonical-cut-sensitivity-result-v1' -or
        $result.status -cne $expectedStatus -or $result.mode -cne $ExpectedMode -or
        $result.specSha256 -cne $script:spec.sha256 -or
        $result.scope -cne 'CANONICAL_BASE_DIRECTED_CUT_SENSITIVITY_ONLY') { Fail 'WORKER_RESULT_IDENTITY' }
    if ($ExpectedMode -ceq 'CANARY') {
        if ($null -ne $result.parent -or $null -ne $result.graph -or $null -ne $result.cut -or
            @($result.cases).Count -ne 0) { Fail 'CANARY_RESULT_BOUNDARY' }
    } else {
        if ($result.parent -isnot [pscustomobject] -or $result.parent.status -cne 'PASS_STAGE_OBSERVATIONS_ONLY' -or
            $result.parent.qualificationSha256 -cne $script:specValue.parentQualificationSha256 -or
            $result.parent.inputPinsSha256 -cne $script:specValue.parentPinsSha256 -or
            $result.parent.readerSha256 -cne $script:specValue.parentReaderSha256 -or
            $result.parent.receiptSha256 -cne $script:parentRelation.sha256 -or
            @($result.cases).Count -ne 4) { Fail 'ANALYZE_RESULT_PARENT' }
    }
    Assert-ExactKeys $result.acceptance @('route','layout','electrical','q100','productionFeasibility','newGeneration','newP07Search') 'RESULT_ACCEPTANCE'
    if ($result.acceptance.route -isnot [bool] -or $result.acceptance.layout -isnot [bool] -or
        $result.acceptance.electrical -isnot [bool] -or $result.acceptance.q100 -isnot [bool] -or
        $result.acceptance.newGeneration -isnot [bool] -or $result.acceptance.newP07Search -isnot [bool] -or
        $result.acceptance.route -ne $false -or $result.acceptance.layout -ne $false -or
        $result.acceptance.electrical -ne $false -or $result.acceptance.q100 -ne $false -or
        $result.acceptance.productionFeasibility -cne 'UNKNOWN' -or
        $result.acceptance.newGeneration -ne $false -or $result.acceptance.newP07Search -ne $false) { Fail 'RESULT_ACCEPTANCE_VALUES' }
    $expectedLimitations = @('CONDITIONAL_MASK_OPENINGS_ONLY','NOT_WHOLE_NET_REMOVAL',
        'NOT_UNIQUE_TRACE_OWNERSHIP','NOT_PHYSICAL_ROUTE','NO_PRODUCTION_ADOPTION',
        'NO_NEW_ELECTRICAL_OR_POPULATION_QUALIFICATION')
    if ($result.limitations -isnot [System.Array] -or $result.limitations.Count -ne $expectedLimitations.Count) {
        Fail 'RESULT_LIMITATIONS'
    }
    for ($i = 0; $i -lt $expectedLimitations.Count; $i++) {
        if ([string]$result.limitations[$i] -cne $expectedLimitations[$i]) { Fail 'RESULT_LIMITATIONS' }
    }
    $script:indexElapsedNanos = $elapsed
    $script:workerIndexWork = $index.work
    return [pscustomobject]@{ index = $index; result = $result; maxGroupNanos = $maxGroup; groups = $groups }
}

function Assert-OutputCensus([bool]$IncludeHostResult) {
    Assert-OutputIdentity
    $expected = if ($Mode -ceq 'ANALYZE') {
        @('parent-process.json','parent.stdout','parent.stderr','worker-process.json','worker.stdout','worker.stderr','data')
    } else {
        @('worker-process.json','worker.stdout','worker.stderr','data')
    }
    if ($IncludeHostResult) { $expected += 'host-receipt.json' }
    $items = @(Get-ChildItem -LiteralPath $script:outputRootFull -Force)
    $names = @($items | ForEach-Object { [string]$_.Name })
    if ($names.Count -ne $expected.Count) { Fail 'OUTPUT_ROOT_CENSUS' }
    foreach ($name in $expected) { if ($names -cnotcontains $name) { Fail 'OUTPUT_ROOT_CENSUS' } }
    foreach ($item in $items) {
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Fail 'OUTPUT_ROOT_REPARSE' }
        if ($item.Name -ceq 'data') {
            if (-not $item.PSIsContainer) { Fail 'OUTPUT_DATA_TYPE' }
        } elseif ($item.PSIsContainer) { Fail 'OUTPUT_FILE_TYPE' }
    }
    $totalBytes = 0L
    foreach ($item in $items) { if (-not $item.PSIsContainer) { $totalBytes += [long]$item.Length } }
    $dataItems = @(Get-ChildItem -LiteralPath $script:outputDataRoot -Force)
    if ($dataItems.Count -ne 1 -or $dataItems[0].Name -cne 'sensitivity-result.json' -or
        $dataItems[0].PSIsContainer -or ($dataItems[0].Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        Fail 'OUTPUT_DATA_CENSUS'
    }
    $totalBytes += [long]$dataItems[0].Length
    if ($totalBytes -gt $script:artifactLimitBytes) { Fail 'OUTPUT_AGGREGATE_LIMIT' }
    return $totalBytes
}

function Get-HostReceipt([string]$Status, [double]$ConservativeUpperMs, [bool]$AllTerminated) {
    $cleanupStatus = if ($Status -ceq 'HOST_COMPLETE_READER_PENDING' -and
        $ConservativeUpperMs -le 120000.0 -and $AllTerminated -and $script:temporaryOutputRetained) { 'PASS' } else { 'FAIL' }
    return [pscustomobject][ordered]@{
        schema = 'tsj-canonical-cut-sensitivity-host-v1'
        status = $Status
        mode = $Mode
        specSha256 = $SpecSha256
        host = $script:hostIdentity
        sourceBefore = $script:sourceBefore
        sourceAfter = $script:sourceAfter
        parent = if ($Mode -ceq 'CANARY') { $null } else { $script:parentRelation }
        worker = $script:workerRelation
        work = [pscustomobject][ordered]@{
            indexElapsedNanos = [long]$script:indexElapsedNanos
            residualUpperNanos = [long]$script:residualUpperNanos
            finalGroups = [long]$script:finalGroups
            maximumGroupNanos = [long]$script:maximumGroupNanos
            fullWorkerWallNanos = [long]$script:workerWallNanos
        }
        cleanup = [pscustomobject][ordered]@{
            status = $cleanupStatus
            accounting = 'FULL_PARENT_WORKER_POST_SOURCE'
            conservativeUpperMs = [Math]::Round($ConservativeUpperMs, 3)
            boundMs = 120000
            allProcessesTerminated = $AllTerminated
            temporaryOutputRetainedAsEvidence = $script:temporaryOutputRetained
        }
        outerWallMs = [Math]::Round($script:outerWatch.Elapsed.TotalMilliseconds, 3)
    }
}

$script:conservativeUpperMs = 0.0
$script:allProcessesTerminated = $false
$script:workerIndexWork = $null
$script:workerMaxGroupNanos = 0L
$script:workerGroups = 0L

try {
    $script:phase = 'SPEC'
    $script:specValue = Get-SpecAndPins $SpecPath $SpecSha256
    $script:sourceInventory = Get-SourceInventory
    $script:phase = 'TOOLS'
    Assert-ModuleToolAndHost
    $script:phase = 'OUTPUT_ROOT'
    $script:outputRootFull = Assert-OutputRoot $OutputRoot $true
    $script:outputDataRoot = Join-Path $script:outputRootFull 'data'

    $script:phase = 'SOURCE_BEFORE'
    $script:sourceBefore = Invoke-SourceAudit 'BEFORE'

    if ($Mode -ceq 'ANALYZE') {
        $script:phase = 'PARENT'
        $parentArguments = @('-B',$script:parentReaderPath,$script:parentRunRoot,
            $script:parentPinsPath,$script:parentPinsSha256,'OBSERVE')
        $watch = [Diagnostics.Stopwatch]::StartNew()
        $parentProcess = Invoke-VerifierBoundedProcess $script:pythonPath $parentArguments 180000
        $watch.Stop()
        $script:parentWallMs = [Math]::Round($watch.Elapsed.TotalMilliseconds,3)
        $script:parentWallNanos = [long][Math]::Ceiling($watch.Elapsed.TotalMilliseconds * 1000000.0)
        $savedParent = Save-ProcessReceipt 'parent' $parentProcess $script:parentWallMs $parentArguments
        $script:parentReceipt = $savedParent.receipt
        $script:parentRelation = $savedParent.relation
        [void](Assert-ParentReaderResult $script:parentReceipt)
        if ($script:parentWallMs -gt 180000.0) { Fail 'PARENT_WALL_LIMIT' }
    }

    $script:phase = 'DATA_ROOT'
    Assert-OutputIdentity
    if (Test-Path -LiteralPath $script:outputDataRoot) { Fail 'OUTPUT_DATA_EXISTS' }
    [void][IO.Directory]::CreateDirectory($script:outputDataRoot)
    Assert-NoReparseAncestors $script:outputDataRoot
    if (@(Get-ChildItem -LiteralPath $script:outputDataRoot -Force).Count -ne 0) { Fail 'OUTPUT_DATA_NOT_EMPTY' }

    $script:phase = 'WORKER'
    $parentArgPath = if ($Mode -ceq 'ANALYZE') { Join-Path $script:outputRootFull 'parent-process.json' } else { '-' }
    $parentArgSha = if ($Mode -ceq 'ANALYZE') { $script:parentRelation.sha256 } else { '-' }
    $workerArguments = @('-B',[string]$script:specValue.worker,$Mode,$script:spec.path,
        $SpecSha256,$script:outputDataRoot,$parentArgPath,$parentArgSha)
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $workerProcess = Invoke-VerifierBoundedProcess $script:pythonPath $workerArguments 180000
    $watch.Stop()
    $script:workerWallMs = [Math]::Round($watch.Elapsed.TotalMilliseconds,3)
    $script:workerWallNanos = [long][Math]::Ceiling($watch.Elapsed.TotalMilliseconds * 1000000.0)
    $savedWorker = Save-ProcessReceipt 'worker' $workerProcess $script:workerWallMs $workerArguments
    $script:workerReceipt = $savedWorker.receipt
    $script:workerRelation = $savedWorker.relation
    if ($script:workerWallNanos -gt 90000000000L) { Fail 'WORKER_WALL_LIMIT' }
    $workerValidated = Assert-WorkerResult $script:workerReceipt $Mode
    $script:workerMaxGroupNanos = [long]$workerValidated.maxGroupNanos
    $script:workerGroups = [long]$workerValidated.groups
    $script:residualUpperNanos = [Math]::Max(0L, $script:workerWallNanos - $script:indexElapsedNanos)
    $script:finalGroups = $script:workerGroups + 1L
    $script:maximumGroupNanos = [Math]::Max($script:workerMaxGroupNanos, $script:residualUpperNanos)
    if ($script:finalGroups -gt 640L -or $script:maximumGroupNanos -gt 5000000000L) { Fail 'WORKER_FINAL_BUDGET' }

    $script:phase = 'SOURCE_AFTER'
    $postWatch = [Diagnostics.Stopwatch]::StartNew()
    $script:sourceAfter = Invoke-SourceAudit 'AFTER'
    $postWatch.Stop()
    $postSourceMs = [Math]::Round($postWatch.Elapsed.TotalMilliseconds,3)
    $script:phase = 'OUTPUT_FINALIZE'
    $outputWatch = [Diagnostics.Stopwatch]::StartNew()
    [void](Assert-OutputCensus $false)
    $outputWatch.Stop()
    $outputFinalizeMs = [Math]::Round($outputWatch.Elapsed.TotalMilliseconds,3)
    $script:conservativeUpperMs = $script:parentWallMs + $script:workerWallMs + $postSourceMs + $outputFinalizeMs
    $script:allProcessesTerminated = ($script:workerReceipt.TerminationProven -eq $true -and
        ($Mode -ceq 'CANARY' -or $script:parentReceipt.TerminationProven -eq $true))
    if ($script:conservativeUpperMs -gt 120000.0 -or -not $script:allProcessesTerminated) { Fail 'CLEANUP_BOUND_OR_TERMINATION' }
    if ($script:outerWatch.Elapsed.TotalMilliseconds -gt 1800000.0) { Fail 'OUTER_WALL_LIMIT' }
} catch {
    if ($null -eq $script:failureCode) {
        $message = [string]$_.Exception.Message
        if ($message -match '^[A-Z0-9_]{1,96}$') { $script:failureCode = $message }
        else { $script:failureCode = 'HOST_' + $script:phase + '_FAILURE' }
    }
}

if ($script:outputCreated) {
    try {
        $status = if ($null -eq $script:failureCode) { 'HOST_COMPLETE_READER_PENDING' } else { 'FAIL_' + $script:failureCode }
        if ($Mode -ceq 'CANARY') { $script:parentRelation = $null }
        $allTerminated = $script:allProcessesTerminated
        if ($null -eq $script:failureCode) {
            [void](Assert-OutputCensus $false)
            $script:conservativeUpperMs = [Math]::Max($script:conservativeUpperMs, $script:parentWallMs + $script:workerWallMs)
            if ($script:conservativeUpperMs -gt 120000.0) { $script:failureCode = 'CLEANUP_BOUND' }
            $status = if ($null -eq $script:failureCode) { 'HOST_COMPLETE_READER_PENDING' } else { 'FAIL_' + $script:failureCode }
        }
        $receipt = Get-HostReceipt $status $script:conservativeUpperMs $allTerminated
        $receiptRaw = Write-CreateNewJson (Join-Path $script:outputRootFull 'host-receipt.json') $receipt 1048576L 'HOST_RECEIPT'
        [void](Assert-OutputCensus $true)
        if ($receipt.cleanup.status -cne 'PASS' -and $null -eq $script:failureCode) { $script:failureCode = 'HOST_CLEANUP_RECEIPT' }
    } catch {
        if ($null -eq $script:failureCode) {
            $message = [string]$_.Exception.Message
            if ($message -match '^[A-Z0-9_]{1,96}$') { $script:failureCode = $message }
            else { $script:failureCode = 'HOST_RECEIPT_FAILURE' }
        }
    }
}

$script:outerWatch.Stop()
if ($null -ne $script:failureCode) {
    [Console]::Error.WriteLine('P7_HOST_FAILURE ' + $script:failureCode)
    exit 1
}
[Console]::Out.WriteLine('P7_HOST_COMPLETE')
exit 0

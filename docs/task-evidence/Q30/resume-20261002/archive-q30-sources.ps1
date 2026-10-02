[CmdletBinding()]
param(
    [string]$TaskRoot = (Join-Path ([IO.Path]::GetTempPath()) 'q30-finish-0fa2512465'),
    [string]$Repo,
    [string]$ArchiveRoot,
    [string]$ProfileRoot,
    [string[]]$CandidateNames = @(
        'q30-scale-acceptance-prepared',
        'q30-scale-acceptance-r2',
        'q30-scale-acceptance-r2b',
        'q30-scale-acceptance-r3',
        'q30-scale-acceptance-r4',
        'q30-scale-acceptance-r4b',
        'q30-scale-acceptance-r5',
        'q30-scale-acceptance-r6',
        'q30-scale-acceptance-r7',
        'q30-scale-acceptance-r8',
        'q30-scale-acceptance-r9'
    ),
    [switch]$SkipProfile,
    [switch]$IncludeSourceTrees
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ArchiveRoot)) {
    $ArchiveRoot = Join-Path $TaskRoot 'source-attempt-archive'
}
if ([string]::IsNullOrWhiteSpace($ProfileRoot)) {
    $ProfileRoot = Join-Path $TaskRoot 'expanded-profile-001'
}

$expectedBaselinePatchRelative = 'tests/qualification/q30-scale-acceptance/baseline.patch'
$expectedBaselinePatchSha256 = 'f3d40b868c21c3579cb22fd567f6d112b8db24cd5199f9edec6542201c9e8080'
$expectedBaselinePaths = @(
    'scripts/verify-current-contracts.ps1',
    'src/com/lushprojects/circuitjs1/circuitjs1.gwt.xml',
    'src/com/lushprojects/circuitjs1/client/GenerationCoordinator.java',
    'src/com/lushprojects/circuitjs1/client/GenerationJob.java'
)

function Normalize-RelativePath {
    param([Parameter(Mandatory)][string]$Path)
    $normalized = $Path.Replace('\', '/').TrimStart('/')
    if ([IO.Path]::IsPathRooted($normalized) -or
        $normalized -match '(^|/)\.\.(?:/|$)' -or
        $normalized -match '(^|/)\.git(?:/|$)') {
        throw "Unsafe non-relative source path: $Path"
    }
    return $normalized
}

function Join-LogicalPath {
    param(
        [Parameter(Mandatory)][string]$Root,
        [Parameter(Mandatory)][string]$RelativePath
    )
    return Join-Path $Root ($RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar))
}

function Get-Sha256File {
    param([Parameter(Mandatory)][string]$Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-Sha256Bytes {
    param([Parameter(Mandatory)][byte[]]$Bytes)
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
    }
}

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Text
    )
    $parent = Split-Path -Parent $Path
    if ($parent) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($false))
}

function Write-JsonFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)]$Value
    )
    $json = $Value | ConvertTo-Json -Depth 100
    Write-Utf8NoBom -Path $Path -Text ($json + [Environment]::NewLine)
}

function Read-JsonFile {
    param([Parameter(Mandatory)][string]$Path)
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function Invoke-NativeCapture {
    param(
        [Parameter(Mandatory)][string]$FileName,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$WorkingDirectory
    )
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $FileName
    $start.WorkingDirectory = $WorkingDirectory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    $start.StandardErrorEncoding = [Text.UTF8Encoding]::new($false)
    foreach ($argument in $Arguments) {
        [void]$start.ArgumentList.Add([string]$argument)
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    try {
        if (-not $process.Start()) {
            throw "Could not start $FileName"
        }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Stdout = $stdout
            Stderr = $stderr
        }
    }
    finally {
        $process.Dispose()
    }
}

function Invoke-NativeChecked {
    param(
        [Parameter(Mandatory)][string]$FileName,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$WorkingDirectory,
        [int[]]$AllowedExitCodes = @(0)
    )
    $result = Invoke-NativeCapture -FileName $FileName -Arguments $Arguments -WorkingDirectory $WorkingDirectory
    if ($result.ExitCode -notin $AllowedExitCodes) {
        $message = "${FileName} exited $($result.ExitCode)."
        if ($result.Stderr) { $message += " $($result.Stderr.Trim())" }
        throw $message
    }
    return $result
}

function Write-GzipBytes {
    param(
        [Parameter(Mandatory)][byte[]]$Bytes,
        [Parameter(Mandatory)][string]$Path
    )
    $parent = Split-Path -Parent $Path
    if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    $file = [IO.FileStream]::new($Path, [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $gzip = [IO.Compression.GZipStream]::new($file, [IO.Compression.CompressionLevel]::Optimal, $false)
        try {
            $gzip.Write($Bytes, 0, $Bytes.Length)
        }
        finally {
            $gzip.Dispose()
        }
    }
    finally {
        $file.Dispose()
    }
}

function Test-ByteArrayEqual {
    param(
        [Parameter(Mandatory)][byte[]]$Left,
        [Parameter(Mandatory)][byte[]]$Right
    )
    if ($Left.Length -ne $Right.Length) { return $false }
    for ($index = 0; $index -lt $Left.Length; $index++) {
        if ($Left[$index] -ne $Right[$index]) { return $false }
    }
    return $true
}

function Read-GzipBytes {
    param([Parameter(Mandatory)][string]$Path)
    $file = [IO.FileStream]::new($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
    try {
        $gzip = [IO.Compression.GZipStream]::new($file, [IO.Compression.CompressionMode]::Decompress, $false)
        try {
            $memory = [IO.MemoryStream]::new()
            try {
                $gzip.CopyTo($memory)
                return $memory.ToArray()
            }
            finally {
                $memory.Dispose()
            }
        }
        finally {
            $gzip.Dispose()
        }
    }
    finally {
        $file.Dispose()
    }
}

function Copy-ManifestFiles {
    param(
        [Parameter(Mandatory)]$SourceEntries,
        [Parameter(Mandatory)][string]$SourceRoot,
        [Parameter(Mandatory)][string]$DestinationRoot,
        [Parameter(Mandatory)][string]$MissingLabel,
        [switch]$AllowMissing
    )
    foreach ($entry in @($SourceEntries)) {
        $relative = Normalize-RelativePath -Path ([string]$entry.path)
        $source = Join-LogicalPath -Root $SourceRoot -RelativePath $relative
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            if ($AllowMissing) { continue }
            throw "Missing $MissingLabel source file: $relative"
        }
        $destination = Join-LogicalPath -Root $DestinationRoot -RelativePath $relative
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination -Force
    }
}

function Get-FileRecord {
    param(
        [Parameter(Mandatory)][string]$Root,
        [Parameter(Mandatory)][string]$RelativePath
    )
    $path = Join-LogicalPath -Root $Root -RelativePath $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return [pscustomobject]@{ exists = $false; size = $null; sha256 = $null }
    }
    $item = Get-Item -LiteralPath $path
    return [pscustomobject]@{
        exists = $true
        size = [int64]$item.Length
        sha256 = Get-Sha256File -Path $path
    }
}

function New-TarGzDirectory {
    param(
        [Parameter(Mandatory)][string]$SourceDirectory,
        [Parameter(Mandatory)][string]$ArchivePath,
        [Parameter(Mandatory)][string]$WorkingDirectory
    )
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ArchivePath) | Out-Null
    $result = Invoke-NativeChecked -FileName 'tar.exe' -Arguments @('-czf', $ArchivePath, '-C', $SourceDirectory, '.') -WorkingDirectory $WorkingDirectory
    return [pscustomobject]@{
        path = $ArchivePath
        bytes = [int64](Get-Item -LiteralPath $ArchivePath).Length
        sha256 = Get-Sha256File -Path $ArchivePath
        stderr = $result.Stderr.Trim()
    }
}

function Expand-TarGzDirectory {
    param(
        [Parameter(Mandatory)][string]$ArchivePath,
        [Parameter(Mandatory)][string]$DestinationDirectory,
        [Parameter(Mandatory)][string]$WorkingDirectory
    )
    New-Item -ItemType Directory -Force -Path $DestinationDirectory | Out-Null
    [void](Invoke-NativeChecked -FileName 'tar.exe' -Arguments @('-xzf', $ArchivePath, '-C', $DestinationDirectory) -WorkingDirectory $WorkingDirectory)
}

function Get-ArchiveRelativePath {
    param(
        [Parameter(Mandatory)][string]$Root,
        [Parameter(Mandatory)][string]$Path
    )
    return $Path.Substring($Root.Length + 1).Replace('\', '/')
}

function Get-OverlayIdentity {
    param([Parameter(Mandatory)]$OverlayFiles)
    $lines = @($OverlayFiles) |
        ForEach-Object {
            $path = Normalize-RelativePath -Path ([string]$_.path)
            "{0}|{1}|{2}" -f $path, ([string]$_.sha256).ToLowerInvariant(), [int64]$_.size
        } |
        Sort-Object
    $canonical = (($lines -join "`n") + "`n")
    return [pscustomobject]@{
        sha256 = Get-Sha256Bytes -Bytes ([Text.UTF8Encoding]::new($false).GetBytes($canonical))
        fileCount = $lines.Count
        canonical = $canonical
    }
}

function Get-PatchPaths {
    param([Parameter(Mandatory)][string]$PatchPath)
    $paths = [System.Collections.Generic.List[string]]::new()
    foreach ($line in ([IO.File]::ReadAllText($PatchPath) -split "`r?`n")) {
        if ($line -match '^diff --git a/(.+) b/(.+)$') {
            if ($Matches[1] -ne $Matches[2]) {
                throw "Baseline patch has asymmetric paths: $($Matches[1]) / $($Matches[2])"
            }
            $paths.Add((Normalize-RelativePath -Path $Matches[1]))
        }
    }
    return @($paths | Sort-Object -Unique)
}

function Invoke-ExactForwardBaselineCheck {
    param([string]$SourceRoot, [string]$BaselinePatchPath, $SnapshotEntries,
        $DeclaredMismatches, [string[]]$ExpectedPaths, [string]$WorkingDirectory,
        [string]$CandidateName, [int]$ReverseCheckExitCode)
    $patchPaths = @(Get-PatchPaths -PatchPath $BaselinePatchPath)
    $pathSetMatch = @(Compare-Object -ReferenceObject @($ExpectedPaths | Sort-Object) -DifferenceObject @($patchPaths | Sort-Object)).Count -eq 0
    $mismatchPaths = @($DeclaredMismatches | ForEach-Object { [string]$_.path } | Sort-Object)
    $mismatchSetMatch = @(Compare-Object -ReferenceObject @($ExpectedPaths | Sort-Object) -DifferenceObject $mismatchPaths).Count -eq 0
    $originals = Join-Path $WorkingDirectory ($CandidateName + '-declared-originals')
    $stage = Join-Path $WorkingDirectory ($CandidateName + '-baseline-forward')
    New-Item -ItemType Directory -Path $originals, $stage | Out-Null
    $records = [System.Collections.Generic.List[object]]::new()
    foreach ($path in $ExpectedPaths) {
        $entry = @($SnapshotEntries | Where-Object { [string]$_.path -ceq $path })
        if ($entry.Count -ne 1) { throw 'Missing or duplicate declared baseline input' }
        $actual = Get-FileRecord -Root $Repo -RelativePath $path
        if (-not $actual.exists -or $actual.size -ne $entry[0].size -or
            $actual.sha256 -cne ([string]$entry[0].sha256).ToLowerInvariant()) {
            throw "Exact declared original unavailable: $path"
        }
        foreach ($destinationRoot in @($originals, $stage)) {
            $destination = Join-LogicalPath -Root $destinationRoot -RelativePath $path
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
            Copy-Item -LiteralPath (Join-LogicalPath -Root $Repo -RelativePath $path) -Destination $destination
        }
        $records.Add([ordered]@{ path=$path; size=$actual.size; sha256=$actual.sha256 })
    }
    # This reproduces prepare.py's Git checkout conversion in a scratch copy.
    # Raw originals are first bound to every declared size/hash, without any
    # normalization. The frozen patch and effective output must both match.
    $arguments = @('-C', $stage, '-c', 'core.autocrlf=true', 'apply')
    $check = Invoke-NativeCapture -FileName 'git.exe' -Arguments ($arguments + @('--check', $BaselinePatchPath)) -WorkingDirectory $WorkingDirectory
    if ($check.ExitCode -ne 0) { throw ('Exact baseline forward check failed: ' + $check.Stderr) }
    $apply = Invoke-NativeCapture -FileName 'git.exe' -Arguments ($arguments + @($BaselinePatchPath)) -WorkingDirectory $WorkingDirectory
    if ($apply.ExitCode -ne 0) { throw ('Exact baseline forward apply failed: ' + $apply.Stderr) }
    $reproduced = [System.Collections.Generic.List[object]]::new()
    foreach ($path in $ExpectedPaths) {
        $actual = Get-FileRecord -Root $stage -RelativePath $path
        $expected = Get-FileRecord -Root $SourceRoot -RelativePath $path
        $match = $actual.exists -and $expected.exists -and
            $actual.size -eq $expected.size -and $actual.sha256 -ceq $expected.sha256
        if (-not $match) { throw "Frozen patch did not reproduce exact effective bytes: $path" }
        $reproduced.Add([ordered]@{path=$path; size=$actual.size; sha256=$actual.sha256; match=$match})
    }
    if (-not $pathSetMatch -or -not $mismatchSetMatch) { throw 'Baseline path/mismatch set changed' }
    $archivePath = Join-Path $ArchiveRoot ($CandidateName + '/declared-baseline-originals.tar.gz')
    $archive = New-TarGzDirectory -SourceDirectory $originals -ArchivePath $archivePath -WorkingDirectory $WorkingDirectory
    $verifiedRoot = Join-Path $WorkingDirectory ($CandidateName + '-originals-roundtrip')
    Expand-TarGzDirectory -ArchivePath $archivePath -DestinationDirectory $verifiedRoot -WorkingDirectory $WorkingDirectory
    foreach ($record in $records) {
        $actual = Get-FileRecord -Root $verifiedRoot -RelativePath $record.path
        if (-not $actual.exists -or $actual.size -ne $record.size -or $actual.sha256 -cne $record.sha256) {
            throw 'Declared originals archive roundtrip failed'
        }
    }
    return [ordered]@{
        status='PASS'; classification='EXPECTED_BASELINE_PATCH'; method='EXACT_FORWARD_RAW_BYTE_BINDING'
        patchPathSetMatch=$pathSetMatch; declaredMismatchSetMatch=$mismatchSetMatch
        reverseCheckExitCode=$ReverseCheckExitCode; reverseApplyExitCode=$null
        forwardCheckExitCode=$check.ExitCode; forwardApplyExitCode=$apply.ExitCode
        declaredOriginalHashes=@($records); reproducedEffectiveHashes=@($reproduced)
        originalsArchive=[ordered]@{path=($CandidateName + '/declared-baseline-originals.tar.gz'); sha256=$archive.sha256; bytes=$archive.bytes; roundTrip='PASS'}
        affectedPaths=$patchPaths
        reason='Exact raw originals match every snapshot hash; the pinned patch reproduces every effective app byte; originals tar extraction matches all raw hashes. Reverse failure is retained.'
    }
}

function Invoke-ReverseBaselineCheck {
    param(
        [Parameter(Mandatory)][string]$SourceRoot,
        [Parameter(Mandatory)][string]$BaselinePatchPath,
        [Parameter(Mandatory)]$SnapshotEntries,
        [Parameter(Mandatory)]$DeclaredMismatches,
        [Parameter(Mandatory)][string[]]$ExpectedPaths,
        [Parameter(Mandatory)][string]$WorkingDirectory,
        [Parameter(Mandatory)][string]$CandidateName
    )
    $stage = Join-Path $WorkingDirectory ("{0}-baseline-reverse" -f $CandidateName)
    New-Item -ItemType Directory -Force -Path $stage | Out-Null
    Copy-ManifestFiles -SourceEntries $SnapshotEntries -SourceRoot $SourceRoot -DestinationRoot $stage -MissingLabel "$CandidateName effective source"

    $patchPaths = @(Get-PatchPaths -PatchPath $BaselinePatchPath)
    $sortedPatch = @($patchPaths | Sort-Object)
    $sortedExpected = @($ExpectedPaths | Sort-Object)
    $patchPathSetMatch = @((Compare-Object -ReferenceObject $sortedExpected -DifferenceObject $sortedPatch)).Count -eq 0
    $mismatchPaths = @($DeclaredMismatches | ForEach-Object { [string]$_.path } | Sort-Object)
    $declaredMismatchSetMatch = @((Compare-Object -ReferenceObject $sortedExpected -DifferenceObject $mismatchPaths)).Count -eq 0

    $check = Invoke-NativeCapture -FileName 'git.exe' -Arguments @('-C', $stage, '-c', 'core.autocrlf=false', '-c', 'core.eol=lf', 'apply', '--reverse', '--check', '--whitespace=nowarn', $BaselinePatchPath) -WorkingDirectory $WorkingDirectory
    if ($check.ExitCode -ne 0) {
        return Invoke-ExactForwardBaselineCheck -SourceRoot $SourceRoot `
            -BaselinePatchPath $BaselinePatchPath -SnapshotEntries $SnapshotEntries `
            -DeclaredMismatches $DeclaredMismatches -ExpectedPaths $ExpectedPaths `
            -WorkingDirectory $WorkingDirectory -CandidateName $CandidateName `
            -ReverseCheckExitCode $check.ExitCode
    }
    $apply = Invoke-NativeCapture -FileName 'git.exe' -Arguments @('-C', $stage, '-c', 'core.autocrlf=false', '-c', 'core.eol=lf', 'apply', '--reverse', '--whitespace=nowarn', $BaselinePatchPath) -WorkingDirectory $WorkingDirectory
    if ($apply.ExitCode -ne 0) {
        return [ordered]@{
            status = 'NOT_VERIFIED'
            classification = 'UNCLASSIFIED_SNAPSHOT_MISMATCH'
            patchPathSetMatch = $patchPathSetMatch
            declaredMismatchSetMatch = $declaredMismatchSetMatch
            reverseCheckExitCode = $check.ExitCode
            reverseApplyExitCode = $apply.ExitCode
            restoredDeclaredHashes = @()
            affectedPaths = $patchPaths
            reason = 'git reverse-apply failed on a copy'
        }
    }

    $restored = [System.Collections.Generic.List[object]]::new()
    foreach ($path in $ExpectedPaths) {
        $entry = @($SnapshotEntries | Where-Object { [string]$_.path -eq $path }) | Select-Object -First 1
        $actual = Get-FileRecord -Root $stage -RelativePath $path
        $match = $null -ne $entry -and $actual.exists -and
            [int64]$actual.size -eq [int64]$entry.size -and
            $actual.sha256 -eq ([string]$entry.sha256).ToLowerInvariant()
        $restored.Add([ordered]@{
            path = $path
            declaredSize = if ($null -ne $entry) { [int64]$entry.size } else { $null }
            declaredSha256 = if ($null -ne $entry) { ([string]$entry.sha256).ToLowerInvariant() } else { $null }
            restoredSize = $actual.size
            restoredSha256 = $actual.sha256
            match = $match
        })
    }
    $hashesRestored = @($restored | Where-Object { -not $_.match }).Count -eq 0
    $verified = $patchPathSetMatch -and $declaredMismatchSetMatch -and $hashesRestored
    return [ordered]@{
        status = if ($verified) { 'PASS' } else { 'FAIL' }
        classification = if ($verified) { 'EXPECTED_BASELINE_PATCH' } else { 'UNCLASSIFIED_SNAPSHOT_MISMATCH' }
        patchPathSetMatch = $patchPathSetMatch
        declaredMismatchSetMatch = $declaredMismatchSetMatch
        reverseCheckExitCode = $check.ExitCode
        reverseApplyExitCode = $apply.ExitCode
        restoredDeclaredHashes = @($restored)
        affectedPaths = $patchPaths
        reason = if ($verified) { 'reverse patch on copy restored every declared hash' } else { 'reverse patch ran, but expected path/hash binding did not verify' }
    }
}

function Invoke-DeltaReconstructionCheck {
    param(
        [Parameter(Mandatory)][string]$HeadRoot,
        [Parameter(Mandatory)][string]$CandidateRoot,
        [Parameter(Mandatory)]$DeltaChanges,
        [Parameter(Mandatory)][byte[]]$PatchBytes,
        [Parameter(Mandatory)][string]$WorkingDirectory,
        [Parameter(Mandatory)][string]$CandidateName
    )
    if (@($DeltaChanges).Count -eq 0) {
        return [ordered]@{
            status = 'PASS'
            method = 'no changed source paths'
            checkedFileCount = 0
            checkExitCode = 0
            applyExitCode = 0
            hashMismatches = @()
        }
    }
    $stage = Join-Path $WorkingDirectory ("{0}-delta-reconstruction" -f $CandidateName)
    $patchPath = Join-Path $WorkingDirectory ("{0}-source-delta.patch" -f $CandidateName)
    New-Item -ItemType Directory -Force -Path $stage | Out-Null
    [IO.File]::WriteAllBytes($patchPath, $PatchBytes)
    foreach ($change in @($DeltaChanges)) {
        $relative = Normalize-RelativePath -Path ([string]$change.path)
        $head = Join-LogicalPath -Root $HeadRoot -RelativePath $relative
        if (Test-Path -LiteralPath $head -PathType Leaf) {
            $destination = Join-LogicalPath -Root $stage -RelativePath $relative
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
            Copy-Item -LiteralPath $head -Destination $destination -Force
        }
    }
    $check = Invoke-NativeCapture -FileName 'git.exe' -Arguments @('-C', $stage, '-c', 'core.autocrlf=false', '-c', 'core.eol=lf', 'apply', '--check', '--whitespace=nowarn', $patchPath) -WorkingDirectory $WorkingDirectory
    if ($check.ExitCode -ne 0) {
        return [ordered]@{
            status = 'FAIL'
            method = 'git apply on changed HEAD paths'
            checkedFileCount = @($DeltaChanges).Count
            checkExitCode = $check.ExitCode
            applyExitCode = $null
            hashMismatches = @()
        }
    }
    $apply = Invoke-NativeCapture -FileName 'git.exe' -Arguments @('-C', $stage, '-c', 'core.autocrlf=false', '-c', 'core.eol=lf', 'apply', '--whitespace=nowarn', $patchPath) -WorkingDirectory $WorkingDirectory
    if ($apply.ExitCode -ne 0) {
        return [ordered]@{
            status = 'FAIL'
            method = 'git apply on changed HEAD paths'
            checkedFileCount = @($DeltaChanges).Count
            checkExitCode = $check.ExitCode
            applyExitCode = $apply.ExitCode
            hashMismatches = @()
        }
    }
    $mismatches = [System.Collections.Generic.List[object]]::new()
    foreach ($change in @($DeltaChanges)) {
        $relative = Normalize-RelativePath -Path ([string]$change.path)
        $actual = Get-FileRecord -Root $stage -RelativePath $relative
        $expected = Get-FileRecord -Root $CandidateRoot -RelativePath $relative
        if ($actual.exists -ne $expected.exists -or $actual.size -ne $expected.size -or $actual.sha256 -ne $expected.sha256) {
            $mismatches.Add([ordered]@{
                path = $relative
                reconstructed = $actual
                candidate = $expected
            })
        }
    }
    return [ordered]@{
        status = if (@($mismatches).Count -eq 0) { 'PASS' } else { 'FAIL' }
        method = 'git apply on changed HEAD paths'
        checkedFileCount = @($DeltaChanges).Count
        checkExitCode = $check.ExitCode
        applyExitCode = $apply.ExitCode
        hashMismatches = @($mismatches)
    }
}

function Get-SafeJsonText {
    param([Parameter(Mandatory)]$Value)
    $json = $Value | ConvertTo-Json -Depth 100
    if ($json -match '(?i)([A-Z]:\\Users\\|[A-Z]:/Users/|/Users/|AppData[\\/](?:Local|Roaming)|q30-finish-[^"\r\n]*[\\/])') {
        throw 'Publishable JSON contains a private or absolute path.'
    }
    return $json
}

function Assert-NoPrivateTextPath {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Label
    )
    $text = [Text.UTF8Encoding]::new($false, $false).GetString([IO.File]::ReadAllBytes($Path))
    if ($text -match '(?i)([A-Z]:\\Users\\|[A-Z]:/Users/|/Users/|AppData[\\/](?:Local|Roaming)|q30-finish-[^"\r\n]*[\\/])') {
        throw "$Label contains a private or absolute path"
    }
}

function Get-ProfileRunSummary {
    param(
        [Parameter(Mandatory)]$Result,
        [Parameter(Mandatory)][string]$RunName
    )
    $case = @($Result.cases)[0]
    $cpu = $case.cpuProfile
    $attribution = $null
    $attributionPath = Join-Path $env:PROFILE_ROOT ("runs/{0}/cpu-profile/attribution.json" -f $RunName)
    if (Test-Path -LiteralPath $attributionPath -PathType Leaf) {
        $attribution = Read-JsonFile -Path $attributionPath
    }
    $topMethods = @()
    if ($null -ne $attribution -and $null -ne $attribution.topMappedMethods) {
        $topMethods = @($attribution.topMappedMethods) | Select-Object -First 20 | ForEach-Object {
            [ordered]@{
                strongName = [string]$_.strongName
                functionName = [string]$_.functionName
                label = [string]$_.label
                sourceUri = [string]$_.sourceUri
                sourceLine = [string]$_.sourceLine
                mapped = [bool]$_.mapped
                sampleCount = [int64]$_.sampleCount
                sampledMicroseconds = [int64]$_.sampledMicroseconds
                mappingBasis = [string]$_.mappingBasis
            }
        }
    }
    return [ordered]@{
        run = $RunName
        caseName = [string]$case.case.name
        applicationOutcome = [string]$case.applicationOutcome
        cpuProfileOutcome = [string]$case.cpuProfileOutcome
        operationSeconds = [double]$case.operationSeconds
        profileStatus = [string]$cpu.status
        samplingIntervalMicroseconds = [int64]$cpu.samplingIntervalMicroseconds
        profileScope = [string]$cpu.scope
        stopTargetState = [string]$cpu.stopTargetState
        stopObservedState = [string]$cpu.stopObservedState
        stopReason = [string]$cpu.stopReason
        profileMayIncludeWorkUntilStopAck = [bool]$cpu.profileMayIncludeWorkUntilStopAck
        sampleCount = if ($null -ne $attribution) { [int64]$attribution.sampleCount } else { $null }
        mappedGwtSampleCount = if ($null -ne $attribution) { [int64]$attribution.mappedGwtSampleCount } else { $null }
        profileNodeCount = if ($null -ne $attribution) { [int64]$attribution.profileNodeCount } else { $null }
        topMappedMethods = @($topMethods)
    }
}

function Copy-ProfileFile {
    param(
        [Parameter(Mandatory)][string]$ProfileRootPath,
        [Parameter(Mandatory)][string]$RelativePath,
        [Parameter(Mandatory)][string]$StageRoot,
        [Parameter(Mandatory)][AllowEmptyCollection()][System.Collections.Generic.List[object]]$Records
    )
    $relative = Normalize-RelativePath -Path $RelativePath
    $source = Join-LogicalPath -Root $ProfileRootPath -RelativePath $relative
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Missing selected profile artifact: $relative"
    }
    if ($relative -notmatch '(?i)\.gz$') {
        $text = [Text.UTF8Encoding]::new($false, $false).GetString([IO.File]::ReadAllBytes($source))
        if ($text -match '(?i)([A-Z]:\\Users\\|[A-Z]:/Users/|/Users/|AppData[\\/](?:Local|Roaming)|q30-finish-[^"\r\n]*[\\/])') {
            throw "Selected profile artifact contains a private or absolute path: $relative"
        }
    }
    $destination = Join-LogicalPath -Root $StageRoot -RelativePath $relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
    Copy-Item -LiteralPath $source -Destination $destination -Force
    $Records.Add([pscustomobject]@{
        sourcePath = $relative
        archivePath = $relative
        bytes = [int64](Get-Item -LiteralPath $source).Length
        sha256 = Get-Sha256File -Path $source
    })
}

function New-ProfileArchive {
    param(
        [Parameter(Mandatory)][string]$ProfileRootPath,
        [Parameter(Mandatory)][string]$StageRoot,
        [Parameter(Mandatory)][string]$OutputDirectory,
        [Parameter(Mandatory)][string[]]$CandidateNames,
        [Parameter(Mandatory)][string]$WorkingDirectory
    )
    $coldResultPath = Join-LogicalPath -Root $ProfileRootPath -RelativePath 'runs/cold-10014-profiled/result.json'
    $cancelResultPath = Join-LogicalPath -Root $ProfileRootPath -RelativePath 'runs/positive-cancel/result.json'
    if (-not (Test-Path -LiteralPath $coldResultPath -PathType Leaf)) {
        return [ordered]@{ status = 'NOT_FOUND'; reason = 'cold profile result is absent' }
    }
    if (-not (Test-Path -LiteralPath $cancelResultPath -PathType Leaf)) {
        return [ordered]@{ status = 'NOT_FOUND'; reason = 'positive cancel profile result is absent' }
    }

    $coldResult = Read-JsonFile -Path $coldResultPath
    $cancelResult = Read-JsonFile -Path $cancelResultPath
    $coldCase = @($coldResult.cases)[0]
    $compiledResource = @($coldCase.compiledResources)[0]
    $permutation = [string]$compiledResource.permutation
    $compiledHash = ([string]$compiledResource.sha256).ToLowerInvariant()
    $compiledRelative = Normalize-RelativePath -Path ("war/circuitjs1/{0}.cache.js" -f $permutation)
    $nocacheExpected = ([string](@($coldCase.cpuProfile.scriptProvenance.sampledScripts)[0].scriptSourceSha256)).ToLowerInvariant()
    $nocacheRelative = 'war/circuitjs1/circuitjs1.nocache.js'
    $symbol = @($coldCase.cpuProfile.artifacts.symbolMaps)[0]
    $symbolRelative = Normalize-RelativePath -Path ([string]$symbol.repoPath)
    $symbolHash = ([string]$symbol.sha256).ToLowerInvariant()
    $symbolCompressedRelative = Normalize-RelativePath -Path ("runs/cold-10014-profiled/{0}" -f (Normalize-RelativePath -Path ([string]$symbol.compressedArtifact)))

    $payloadCandidate = $null
    $payloadRecords = [System.Collections.Generic.List[object]]::new()
    foreach ($candidateName in $CandidateNames) {
        $candidateApp = Join-Path (Join-Path $TaskRoot $candidateName) 'app'
        $compiledPath = Join-LogicalPath -Root $candidateApp -RelativePath $compiledRelative
        if (-not (Test-Path -LiteralPath $compiledPath -PathType Leaf)) { continue }
        if ((Get-Sha256File -Path $compiledPath) -ne $compiledHash) { continue }
        $nocachePath = Join-LogicalPath -Root $candidateApp -RelativePath $nocacheRelative
        if (-not (Test-Path -LiteralPath $nocachePath -PathType Leaf)) { continue }
        if ((Get-Sha256File -Path $nocachePath) -ne $nocacheExpected) { continue }
        $symbolPath = Join-LogicalPath -Root $candidateApp -RelativePath $symbolRelative
        if (-not (Test-Path -LiteralPath $symbolPath -PathType Leaf)) { continue }
        if ((Get-Sha256File -Path $symbolPath) -ne $symbolHash) { continue }
        $payloadCandidate = [pscustomobject]@{
            name = $candidateName
            appRoot = $candidateApp
            compiledPath = $compiledPath
            nocachePath = $nocachePath
            symbolPath = $symbolPath
        }
        break
    }

    New-Item -ItemType Directory -Force -Path $StageRoot | Out-Null
    $records = [System.Collections.Generic.List[object]]::new()
    $selected = @(
        'runs/cold-10014-profiled/cpu-profile/v8-cpu-profile.json',
        'runs/cold-10014-profiled/cpu-profile/attribution.json',
        'runs/cold-10014-profiled/cpu-profile/focused-callchains.json',
        'runs/cold-10014-profiled/cpu-profile/script-provenance.json',
        'runs/cold-10014-profiled/cpu-profile/self-inclusive-hotspots.json',
        'runs/cold-10014-profiled/cpu-profile/self-inclusive-hotspots.txt',
        'runs/cold-10014-profiled/cases/001-cold-10014-profiled.report.json',
        'runs/positive-cancel/cpu-profile/v8-cpu-profile.json',
        'runs/positive-cancel/cpu-profile/attribution.json',
        'runs/positive-cancel/cpu-profile/script-provenance.json',
        'runs/positive-cancel/cases/001-positive-cancel-10387.report.json'
    )
    foreach ($relative in $selected) {
        $source = Join-LogicalPath -Root $ProfileRootPath -RelativePath $relative
        if (Test-Path -LiteralPath $source -PathType Leaf) {
            Copy-ProfileFile -ProfileRootPath $ProfileRootPath -RelativePath $relative -StageRoot $StageRoot -Records $records
        }
    }

    $cpuprofiles = Get-ChildItem -LiteralPath $ProfileRootPath -Recurse -File -Force -Filter '*.cpuprofile' |
        Where-Object { $_.FullName -notmatch '(?i)[\\/]profile[\\/]' }
    $cpuprofileHashes = @{}
    foreach ($file in $cpuprofiles) {
        $relative = Get-ArchiveRelativePath -Root $ProfileRootPath -Path $file.FullName
        $hash = Get-Sha256File -Path $file.FullName
        if ($cpuprofileHashes.ContainsKey($hash)) { continue }
        $cpuprofileHashes[$hash] = $relative
        Copy-ProfileFile -ProfileRootPath $ProfileRootPath -RelativePath $relative -StageRoot $StageRoot -Records $records
    }

    $compiledRecord = $null
    $nocacheRecord = $null
    $symbolRecord = $null
    $symbolCompressedRecord = $null
    if ($null -ne $payloadCandidate) {
        $compiledDestination = Join-LogicalPath -Root $StageRoot -RelativePath ("compiled/{0}.cache.js" -f $permutation)
        $nocacheDestination = Join-LogicalPath -Root $StageRoot -RelativePath 'compiled/circuitjs1.nocache.js'
        $symbolDestination = Join-LogicalPath -Root $StageRoot -RelativePath ("compiled/{0}.symbolMap" -f $permutation)
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $compiledDestination) | Out-Null
        Copy-Item -LiteralPath $payloadCandidate.compiledPath -Destination $compiledDestination -Force
        Copy-Item -LiteralPath $payloadCandidate.nocachePath -Destination $nocacheDestination -Force
        Copy-Item -LiteralPath $payloadCandidate.symbolPath -Destination $symbolDestination -Force
        $compiledRecord = [ordered]@{
            sourcePath = $compiledRelative
            archivePath = "compiled/{0}.cache.js" -f $permutation
            bytes = [int64](Get-Item -LiteralPath $payloadCandidate.compiledPath).Length
            sha256 = Get-Sha256File -Path $payloadCandidate.compiledPath
        }
        $nocacheRecord = [ordered]@{
            sourcePath = $nocacheRelative
            archivePath = 'compiled/circuitjs1.nocache.js'
            bytes = [int64](Get-Item -LiteralPath $payloadCandidate.nocachePath).Length
            sha256 = Get-Sha256File -Path $payloadCandidate.nocachePath
        }
        $symbolRecord = [ordered]@{
            sourcePath = $symbolRelative
            archivePath = "compiled/{0}.symbolMap" -f $permutation
            bytes = [int64](Get-Item -LiteralPath $payloadCandidate.symbolPath).Length
            sha256 = Get-Sha256File -Path $payloadCandidate.symbolPath
        }
        $records.Add([pscustomobject]$compiledRecord)
        $records.Add([pscustomobject]$nocacheRecord)
        $records.Add([pscustomobject]$symbolRecord)

        $compressedSource = Join-LogicalPath -Root $ProfileRootPath -RelativePath $symbolCompressedRelative
        if (Test-Path -LiteralPath $compressedSource -PathType Leaf) {
            $compressedDestination = Join-LogicalPath -Root $StageRoot -RelativePath ("compiled/{0}.symbolMap.gz" -f $permutation)
            Copy-Item -LiteralPath $compressedSource -Destination $compressedDestination -Force
            $decompressed = Read-GzipBytes -Path $compressedSource
            $symbolCompressedRecord = [ordered]@{
                sourcePath = $symbolCompressedRelative
                archivePath = "compiled/{0}.symbolMap.gz" -f $permutation
                bytes = [int64](Get-Item -LiteralPath $compressedSource).Length
                sha256 = Get-Sha256File -Path $compressedSource
                decompressedBytes = $decompressed.Length
                decompressedSha256 = Get-Sha256Bytes -Bytes $decompressed
            }
            $records.Add([pscustomobject]$symbolCompressedRecord)
        }
    }

    $browserProfileFiles = Get-ChildItem -LiteralPath $ProfileRootPath -Recurse -File -Force |
        Where-Object { $_.FullName -match '(?i)[\\/]profile[\\/]' }
    $profileSummary = [ordered]@{
        schema = 1
        status = if ($null -ne $payloadCandidate) { 'ARCHIVED_PROFILE_SUPPORT' } else { 'PARTIAL_PROFILE_SUPPORT' }
        profileRootLabel = 'expanded-profile-001'
        browserProfileDirectories = [ordered]@{
            archived = $false
            excludedReason = 'browser profile state is large and includes machine/account-local data'
            fileCount = @($browserProfileFiles).Count
            bytes = [int64](($browserProfileFiles | Measure-Object -Property Length -Sum).Sum)
        }
        cpuProfileFormat = 'v8-cpu-profile.json'
        cpuprofileFilesFound = @($cpuprofiles | ForEach-Object { Get-ArchiveRelativePath -Root $ProfileRootPath -Path $_.FullName })
        sourceCandidate = if ($null -ne $payloadCandidate) { $payloadCandidate.name } else { $null }
        compiledBundle = [ordered]@{
            permutation = $permutation
            expectedCacheSha256 = $compiledHash
            expectedCacheBytes = [int64]$compiledResource.length
            payload = $compiledRecord
            exactCandidateMatch = ($null -ne $payloadCandidate)
        }
        nocacheScript = [ordered]@{
            expectedSha256 = $nocacheExpected
            expectedUtf8Bytes = [int64](@($coldCase.cpuProfile.scriptProvenance.sampledScripts)[0].scriptSourceUtf8Bytes)
            payload = $nocacheRecord
        }
        symbolMap = [ordered]@{
            strongName = [string]$symbol.strongName
            expectedSha256 = $symbolHash
            expectedCompressedBytes = [int64]$symbol.compressedBytes
            repoPath = $symbolRelative
            compressedPayload = $symbolCompressedRecord
            rawPayload = $symbolRecord
            decompressedHashMatchesExpected = if ($null -ne $symbolCompressedRecord) { $symbolCompressedRecord.decompressedSha256 -eq $symbolHash } else { $false }
        }
        runner = [ordered]@{
            baseName = [string](Read-JsonFile -Path (Join-LogicalPath -Root $ProfileRootPath -RelativePath 'runner-identity.json')).base.name
            baseSha256 = [string](Read-JsonFile -Path (Join-LogicalPath -Root $ProfileRootPath -RelativePath 'runner-identity.json')).base.sha256
            profiledName = [string](Read-JsonFile -Path (Join-LogicalPath -Root $ProfileRootPath -RelativePath 'runner-identity.json')).profiledRunner.name
            profiledSha256 = [string](Read-JsonFile -Path (Join-LogicalPath -Root $ProfileRootPath -RelativePath 'runner-identity.json')).profiledRunner.sha256
            profiledBytes = [int64](Read-JsonFile -Path (Join-LogicalPath -Root $ProfileRootPath -RelativePath 'runner-identity.json')).profiledRunner.bytes
            runnerPatchSha256 = [string](Read-JsonFile -Path (Join-LogicalPath -Root $ProfileRootPath -RelativePath 'runner-identity.json')).runnerPatch.sha256
            historicalProfilerPatchSha256 = [string](Read-JsonFile -Path (Join-LogicalPath -Root $ProfileRootPath -RelativePath 'runner-identity.json')).historicalProfilerPatch.sha256
        }
        runs = @(
            (Get-ProfileRunSummary -Result $coldResult -RunName 'cold-10014-profiled'),
            (Get-ProfileRunSummary -Result $cancelResult -RunName 'positive-cancel')
        )
        files = @($records)
    }
    $profileJson = Get-SafeJsonText -Value $profileSummary
    Write-Utf8NoBom -Path (Join-Path $OutputDirectory 'profile-summary.json') -Text ($profileJson + [Environment]::NewLine)
    Write-Utf8NoBom -Path (Join-LogicalPath -Root $StageRoot -RelativePath 'profile-summary.json') -Text ($profileJson + [Environment]::NewLine)
    $archivePath = Join-Path $OutputDirectory 'profile-selected.tar.gz'
    $archive = New-TarGzDirectory -SourceDirectory $StageRoot -ArchivePath $archivePath -WorkingDirectory $WorkingDirectory

    $verifyRoot = Join-Path $WorkingDirectory 'profile-verify'
    Expand-TarGzDirectory -ArchivePath $archivePath -DestinationDirectory $verifyRoot -WorkingDirectory $WorkingDirectory
    foreach ($record in @($records)) {
        $verified = Join-LogicalPath -Root $verifyRoot -RelativePath ([string]$record.archivePath)
        if (-not (Test-Path -LiteralPath $verified -PathType Leaf)) { throw "Profile archive omitted $($record.archivePath)" }
        if ((Get-Sha256File -Path $verified) -ne ([string]$record.sha256).ToLowerInvariant()) {
            throw "Profile archive hash mismatch for $($record.archivePath)"
        }
    }
    $summaryVerified = Join-LogicalPath -Root $verifyRoot -RelativePath 'profile-summary.json'
    if ((Get-Sha256File -Path $summaryVerified) -ne (Get-Sha256File -Path (Join-Path $OutputDirectory 'profile-summary.json'))) {
        throw 'Profile summary archive hash mismatch'
    }
    return [ordered]@{
        status = $profileSummary.status
        summaryPath = 'profile-summary.json'
        archivePath = 'profile-selected.tar.gz'
        archiveBytes = $archive.bytes
        archiveSha256 = $archive.sha256
        sourceCandidate = $profileSummary.sourceCandidate
        exactCompiledPayload = [bool]$profileSummary.compiledBundle.exactCandidateMatch
        cpuprofileFilesFound = @($profileSummary.cpuprofileFilesFound)
        selectedFileCount = @($records).Count
    }
}

$git = Get-Command git.exe -ErrorAction SilentlyContinue
$tar = Get-Command tar.exe -ErrorAction SilentlyContinue
if ($null -eq $git) { throw 'git.exe is required' }
if ($null -eq $tar) { throw 'tar.exe is required' }
if (-not (Test-Path -LiteralPath $TaskRoot -PathType Container)) { throw "Task root not found: $TaskRoot" }
if (-not (Test-Path -LiteralPath $Repo -PathType Container)) { throw "Repository not found: $Repo" }
New-Item -ItemType Directory -Force -Path $ArchiveRoot | Out-Null

$scratch = Join-Path ([IO.Path]::GetTempPath()) ('q30-source-archive-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $scratch | Out-Null
$summaryRecords = [System.Collections.Generic.List[object]]::new()
$baseHead = $null
$headStage = Join-Path $scratch 'head'

try {
    $candidateProbe = Join-Path (Join-Path $TaskRoot $CandidateNames[0]) 'prepare-receipt.json'
    if (-not (Test-Path -LiteralPath $candidateProbe -PathType Leaf)) { throw "Candidate receipt not found: $CandidateNames[0]" }
    $baseHead = [string](Read-JsonFile -Path $candidateProbe).baseHead
    if ($baseHead -notmatch '^[0-9a-fA-F]{40}$') { throw "Invalid baseHead in receipt: $baseHead" }
    $repoHeadResult = Invoke-NativeChecked -FileName 'git.exe' -Arguments @('-C', $Repo, 'rev-parse', 'HEAD') -WorkingDirectory $Repo
    $repoHead = $repoHeadResult.Stdout.Trim()
    # A committed checkpoint may advance HEAD while immutable prepared attempts
    # remain bound to their original base. Reconstruct from that exact object;
    # accept only a verified ancestor of the current checkpoint.
    [void](Invoke-NativeChecked -FileName 'git.exe' -Arguments @('-C', $Repo,
        'merge-base', '--is-ancestor', $baseHead, $repoHead) -WorkingDirectory $Repo)
    $baselinePatchPath = Join-LogicalPath -Root $Repo -RelativePath $expectedBaselinePatchRelative
    if (-not (Test-Path -LiteralPath $baselinePatchPath -PathType Leaf)) {
        throw "Expected baseline patch is missing: $expectedBaselinePatchRelative"
    }
    $baselinePatchSha256 = Get-Sha256File -Path $baselinePatchPath
    if ($baselinePatchSha256 -ne $expectedBaselinePatchSha256) {
        throw "Baseline patch hash $baselinePatchSha256 does not match the pinned expected hash $expectedBaselinePatchSha256"
    }
    $baselinePatchPaths = @(Get-PatchPaths -PatchPath $baselinePatchPath)
    if (@((Compare-Object -ReferenceObject (@($expectedBaselinePaths | Sort-Object)) -DifferenceObject (@($baselinePatchPaths | Sort-Object)))).Count -ne 0) {
        throw 'Pinned baseline patch paths do not match the four expected source paths'
    }
    New-Item -ItemType Directory -Force -Path $headStage | Out-Null
    $headZip = Join-Path $scratch 'head.zip'
    [void](Invoke-NativeChecked -FileName 'git.exe' -Arguments @('-C', $Repo, '-c', 'core.autocrlf=false', '-c', 'core.eol=lf', 'archive', '--format=zip', "--output=$headZip", $baseHead) -WorkingDirectory $Repo)
    Expand-Archive -LiteralPath $headZip -DestinationPath $headStage -Force

    foreach ($candidateName in $CandidateNames) {
        $candidateRoot = Join-Path $TaskRoot $candidateName
        $appRoot = Join-Path $candidateRoot 'app'
        $receiptPath = Join-Path $candidateRoot 'prepare-receipt.json'
        $snapshotPath = Join-Path $candidateRoot 'source-snapshot.json'
        if (-not (Test-Path -LiteralPath $receiptPath -PathType Leaf)) { throw "Receipt not found: $candidateName" }
        if (-not (Test-Path -LiteralPath $snapshotPath -PathType Leaf)) { throw "Source snapshot not found: $candidateName" }
        if (-not (Test-Path -LiteralPath $appRoot -PathType Container)) { throw "Candidate app not found: $candidateName" }

        $receipt = Read-JsonFile -Path $receiptPath
        $snapshot = Read-JsonFile -Path $snapshotPath
        if ([string]$snapshot.baseHead -ne $baseHead) { throw "$candidateName source snapshot baseHead mismatch" }
        if (([string]$receipt.baselinePatchSha256).ToLowerInvariant() -ne $baselinePatchSha256) {
            throw "$candidateName receipt baseline patch hash does not match the pinned repository patch"
        }
        $entries = @($snapshot.files)
        $candidateOutput = Join-Path $ArchiveRoot $candidateName
        New-Item -ItemType Directory -Force -Path $candidateOutput | Out-Null
        Copy-Item -LiteralPath $receiptPath -Destination (Join-Path $candidateOutput 'prepare-receipt.json') -Force
        Copy-Item -LiteralPath $snapshotPath -Destination (Join-Path $candidateOutput 'source-snapshot.json') -Force

        $sourceStage = Join-Path $scratch ("{0}-source" -f $candidateName)
        $baselineStage = Join-Path $scratch ("{0}-baseline" -f $candidateName)
        $candidateDeltaStage = Join-Path $scratch ("{0}-candidate" -f $candidateName)
        $overlayStage = Join-Path $scratch ("{0}-overlay" -f $candidateName)
        New-Item -ItemType Directory -Force -Path $sourceStage | Out-Null
        New-Item -ItemType Directory -Force -Path $baselineStage | Out-Null
        New-Item -ItemType Directory -Force -Path $candidateDeltaStage | Out-Null
        New-Item -ItemType Directory -Force -Path $overlayStage | Out-Null
        Copy-ManifestFiles -SourceEntries $entries -SourceRoot $appRoot -DestinationRoot $sourceStage -MissingLabel "$candidateName app"
        Copy-ManifestFiles -SourceEntries $entries -SourceRoot $appRoot -DestinationRoot $candidateDeltaStage -MissingLabel "$candidateName app"
        Copy-ManifestFiles -SourceEntries @($receipt.overlayFiles) -SourceRoot $appRoot -DestinationRoot $overlayStage -MissingLabel "$candidateName overlay"
        foreach ($overlayFile in @($receipt.overlayFiles)) {
            $overlayRelative = Normalize-RelativePath -Path ([string]$overlayFile.path)
            Assert-NoPrivateTextPath -Path (Join-LogicalPath -Root $appRoot -RelativePath $overlayRelative) -Label "$candidateName overlay $overlayRelative"
        }
        Copy-ManifestFiles -SourceEntries $entries -SourceRoot $headStage -DestinationRoot $baselineStage -MissingLabel "$baseHead HEAD" -AllowMissing

        $declaredMismatches = [System.Collections.Generic.List[object]]::new()
        $deltaChanges = [System.Collections.Generic.List[object]]::new()
        $sourceBytes = [int64]0
        foreach ($entry in $entries) {
            $relative = Normalize-RelativePath -Path ([string]$entry.path)
            $candidateFile = Get-FileRecord -Root $appRoot -RelativePath $relative
            $headFile = Get-FileRecord -Root $headStage -RelativePath $relative
            $sourceBytes += if ($candidateFile.exists) { [int64]$candidateFile.size } else { [int64]0 }
            $declaredMatch = $candidateFile.exists -and
                ([int64]$candidateFile.size -eq [int64]$entry.size) -and
                ($candidateFile.sha256 -eq ([string]$entry.sha256).ToLowerInvariant())
            if (-not $declaredMatch) {
                $declaredMismatches.Add([ordered]@{
                    path = $relative
                    declaredSize = [int64]$entry.size
                    declaredSha256 = ([string]$entry.sha256).ToLowerInvariant()
                    actualExists = [bool]$candidateFile.exists
                    actualSize = $candidateFile.size
                    actualSha256 = $candidateFile.sha256
                })
            }
            $sameAsHead = $candidateFile.exists -and $headFile.exists -and
                ([int64]$candidateFile.size -eq [int64]$headFile.size) -and
                ($candidateFile.sha256 -eq $headFile.sha256)
            if (-not $sameAsHead) {
                $kind = if (-not $headFile.exists -and $candidateFile.exists) { 'added' }
                    elseif ($headFile.exists -and -not $candidateFile.exists) { 'deleted' }
                    elseif ($headFile.exists -and $candidateFile.exists) { 'modified' }
                    else { 'missing' }
                $deltaChanges.Add([ordered]@{
                    path = $relative
                    kind = $kind
                    headSize = $headFile.size
                    headSha256 = $headFile.sha256
                    candidateSize = $candidateFile.size
                    candidateSha256 = $candidateFile.sha256
                })
            }
        }

        $overlayIdentity = Get-OverlayIdentity -OverlayFiles @($receipt.overlayFiles)
        $overlayAudit = @($receipt.overlayFiles) | ForEach-Object {
            $relative = Normalize-RelativePath -Path ([string]$_.path)
            $actual = Get-FileRecord -Root $appRoot -RelativePath $relative
            [ordered]@{
                path = $relative
                declaredSize = [int64]$_.size
                declaredSha256 = ([string]$_.sha256).ToLowerInvariant()
                actualSize = $actual.size
                actualSha256 = $actual.sha256
                match = $actual.exists -and [int64]$actual.size -eq [int64]$_.size -and $actual.sha256 -eq ([string]$_.sha256).ToLowerInvariant()
            }
        }
        $baselineReverse = Invoke-ReverseBaselineCheck -SourceRoot $sourceStage -BaselinePatchPath $baselinePatchPath -SnapshotEntries $entries -DeclaredMismatches @($declaredMismatches) -ExpectedPaths $expectedBaselinePaths -WorkingDirectory $scratch -CandidateName $candidateName

        $overlayArchivePath = Join-Path $candidateOutput 'overlay.tar.gz'
        $overlayArchive = New-TarGzDirectory -SourceDirectory $overlayStage -ArchivePath $overlayArchivePath -WorkingDirectory $scratch
        $overlayVerifyRoot = Join-Path $scratch ("{0}-overlay-verify" -f $candidateName)
        Expand-TarGzDirectory -ArchivePath $overlayArchivePath -DestinationDirectory $overlayVerifyRoot -WorkingDirectory $scratch
        $overlayArchiveMismatches = [System.Collections.Generic.List[object]]::new()
        foreach ($overlayFile in @($receipt.overlayFiles)) {
            $relative = Normalize-RelativePath -Path ([string]$overlayFile.path)
            $actual = Get-FileRecord -Root $appRoot -RelativePath $relative
            $verified = Get-FileRecord -Root $overlayVerifyRoot -RelativePath $relative
            if (-not $actual.exists -or -not $verified.exists -or $actual.size -ne $verified.size -or $actual.sha256 -ne $verified.sha256) {
                $overlayArchiveMismatches.Add([ordered]@{
                    path = $relative
                    sourceSize = $actual.size
                    sourceSha256 = $actual.sha256
                    archivedSize = $verified.size
                    archivedSha256 = $verified.sha256
                })
            }
        }
        if (@($overlayArchiveMismatches).Count -ne 0) { throw "Overlay archive verification failed for $candidateName" }

        $patchResult = Invoke-NativeCapture -FileName 'git.exe' -Arguments @('-C', $scratch, '-c', 'core.autocrlf=false', '-c', 'core.safecrlf=false', 'diff', '--no-index', '--binary', '--full-index', '--no-textconv', '--src-prefix=a/', '--dst-prefix=b/', (Split-Path -Leaf $baselineStage), (Split-Path -Leaf $candidateDeltaStage)) -WorkingDirectory $scratch
        if ($patchResult.ExitCode -notin @(0, 1)) {
            throw "Could not create source delta for ${candidateName}: $($patchResult.Stderr.Trim())"
        }
        $patchText = $patchResult.Stdout.Replace(('a/{0}/' -f (Split-Path -Leaf $baselineStage)), 'a/').Replace(('b/{0}/' -f (Split-Path -Leaf $candidateDeltaStage)), 'b/')
        $patchBytes = [Text.UTF8Encoding]::new($false).GetBytes($patchText)
        $reconstruction = Invoke-DeltaReconstructionCheck -HeadRoot $headStage -CandidateRoot $candidateDeltaStage -DeltaChanges @($deltaChanges) -PatchBytes $patchBytes -WorkingDirectory $scratch -CandidateName $candidateName
        $patchPath = Join-Path $candidateOutput 'source-delta.patch.gz'
        Write-GzipBytes -Bytes $patchBytes -Path $patchPath
        $roundTripPatch = Read-GzipBytes -Path $patchPath
        if (-not (Test-ByteArrayEqual -Left $roundTripPatch -Right $patchBytes)) {
            throw "Gzip round-trip changed source delta for $candidateName"
        }
        $patchHeaderPaths = @($patchText -split "`n" |
            Where-Object { $_.StartsWith('diff --git ') } |
            ForEach-Object {
                $parts = $_ -split ' '
                if ($parts.Count -ge 4) { $parts[2].Substring(2) }
            })
        $missingPatchPaths = @($deltaChanges | Where-Object { $patchHeaderPaths -notcontains ([string]$_.path) })
        if (@($missingPatchPaths).Count -ne 0) {
            throw "Source delta omitted $(@($missingPatchPaths).Count) changed file headers for $candidateName"
        }
        $deltaObject = [ordered]@{
            schema = 1
            relativeTo = $baseHead
            sourcePaths = 'source-snapshot.json files only'
            command = 'git diff --no-index --binary --full-index baseline candidate'
            changedFileCount = @($deltaChanges).Count
            changedFiles = @($deltaChanges)
            patch = [ordered]@{
                archivePath = 'source-delta.patch.gz'
                uncompressedBytes = $patchBytes.Length
                uncompressedSha256 = Get-Sha256Bytes -Bytes $patchBytes
                compressedBytes = [int64](Get-Item -LiteralPath $patchPath).Length
                compressedSha256 = Get-Sha256File -Path $patchPath
                roundTrip = if (Test-ByteArrayEqual -Left $roundTripPatch -Right $patchBytes) { 'PASS' } else { 'FAIL' }
                changedPathHeaderGaps = @($missingPatchPaths | ForEach-Object { $_.path })
            }
            reconstruction = $reconstruction
        }
        Write-JsonFile -Path (Join-Path $candidateOutput 'source-delta.json') -Value $deltaObject

        $sourceArchive = $null
        $archiveMismatches = @()
        if ($IncludeSourceTrees) {
            $sourceArchivePath = Join-Path $candidateOutput 'source-tree.tar.gz'
            $sourceArchive = New-TarGzDirectory -SourceDirectory $sourceStage -ArchivePath $sourceArchivePath -WorkingDirectory $scratch
            $sourceVerifyRoot = Join-Path $scratch ("{0}-source-verify" -f $candidateName)
            Expand-TarGzDirectory -ArchivePath $sourceArchivePath -DestinationDirectory $sourceVerifyRoot -WorkingDirectory $scratch
            $archiveMismatchesList = [System.Collections.Generic.List[object]]::new()
            foreach ($entry in $entries) {
                $relative = Normalize-RelativePath -Path ([string]$entry.path)
                $actual = Get-FileRecord -Root $appRoot -RelativePath $relative
                $verified = Get-FileRecord -Root $sourceVerifyRoot -RelativePath $relative
                if (-not $actual.exists -or -not $verified.exists -or $actual.size -ne $verified.size -or $actual.sha256 -ne $verified.sha256) {
                    $archiveMismatchesList.Add([ordered]@{
                        path = $relative
                        sourceSize = $actual.size
                        sourceSha256 = $actual.sha256
                        archivedSize = $verified.size
                        archivedSha256 = $verified.sha256
                    })
                }
            }
            if (@($archiveMismatchesList).Count -ne 0) { throw "Source archive verification failed for $candidateName" }
            $archiveMismatches = @($archiveMismatchesList)
        }

        $snapshotSha = Get-Sha256File -Path $snapshotPath
        $receiptSha = Get-Sha256File -Path $receiptPath
        $receiptSnapshotMatch = $snapshotSha -eq ([string]$receipt.sourceSnapshotSha256).ToLowerInvariant()
        $candidateArchiveValid = $receiptSnapshotMatch -and
            $baselineReverse.status -eq 'PASS' -and
            $baselineReverse.classification -eq 'EXPECTED_BASELINE_PATCH' -and
            $reconstruction.status -eq 'PASS' -and
            $deltaObject.patch.roundTrip -eq 'PASS' -and
            @($missingPatchPaths).Count -eq 0 -and
            @($overlayAudit | Where-Object { -not $_.match }).Count -eq 0 -and
            @($overlayArchiveMismatches).Count -eq 0
        $auditObject = [ordered]@{
            schema = 1
            candidate = $candidateName
            status = if ($candidateArchiveValid) { 'ARCHIVED_SOURCE_ONLY' } else { 'ARCHIVED_SOURCE_INVALID' }
            sourceIdentity = [string]$snapshot.sourceIdentity
            baseHead = $baseHead
        observedRepositoryHead = $repoHead
            sourceFileCount = $entries.Count
            sourceBytes = $sourceBytes
            sourceSnapshotSha256 = $snapshotSha
            receiptSha256 = $receiptSha
            receiptSourceSnapshotSha256 = ([string]$receipt.sourceSnapshotSha256).ToLowerInvariant()
            sourceSnapshotReceiptHashMatch = $receiptSnapshotMatch
            declaredSnapshotAppByteMatch = [ordered]@{
                status = if (@($declaredMismatches).Count -eq 0) { 'PASS' } else { 'FAIL' }
                mismatchCount = @($declaredMismatches).Count
                mismatches = @($declaredMismatches)
                classification = if ($baselineReverse.status -eq 'PASS') { 'EXPECTED_BASELINE_PATCH' } else { 'UNCLASSIFIED_SNAPSHOT_MISMATCH' }
                baselinePatchVerification = $baselineReverse
                boundary = 'source-snapshot.json is a manifest; source-delta.patch.gz reconstructs effective snapshot paths from base HEAD; overlay.tar.gz carries receipt.overlayFiles'
            }
            overlay = [ordered]@{
                identitySha256 = $overlayIdentity.sha256
                fileCount = $overlayIdentity.fileCount
                declaredAndActual = @($overlayAudit)
                declaredBytesMatch = if (@($overlayAudit | Where-Object { -not $_.match }).Count -eq 0) { 'PASS' } else { 'FAIL' }
                archive = [ordered]@{
                    archivePath = 'overlay.tar.gz'
                    bytes = $overlayArchive.bytes
                    sha256 = $overlayArchive.sha256
                    extractedBytesMatchSource = if (@($overlayArchiveMismatches).Count -eq 0) { 'PASS' } else { 'FAIL' }
                }
            }
            sourceArchive = if ($null -ne $sourceArchive) {
                [ordered]@{
                    status = 'INCLUDED'
                    archivePath = 'source-tree.tar.gz'
                    bytes = $sourceArchive.bytes
                    sha256 = $sourceArchive.sha256
                    extractedFileCount = $entries.Count
                    extractedBytesMatchSource = if (@($archiveMismatches).Count -eq 0) { 'PASS' } else { 'FAIL' }
                }
            }
            else {
                [ordered]@{
                    status = 'OMITTED_COMPACT'
                    reason = 'source-delta.patch.gz plus source-delta.json reconstruct the effective source paths from base HEAD'
                }
            }
            sourceDelta = [ordered]@{
                manifestPath = 'source-delta.json'
                patchPath = 'source-delta.patch.gz'
                changedFileCount = @($deltaChanges).Count
                patchRoundTrip = $deltaObject.patch.roundTrip
                reconstruction = $reconstruction
            }
            inputIdentity = [ordered]@{
                manifestSha256 = ([string]$receipt.manifestSha256).ToLowerInvariant()
                acceptancePlanSha256 = ([string]$receipt.acceptancePlanSha256).ToLowerInvariant()
                baselinePatchSha256 = ([string]$receipt.baselinePatchSha256).ToLowerInvariant()
                baselinePatchPath = $expectedBaselinePatchRelative
                baselinePatchRepositoryHashVerified = $baselinePatchSha256 -eq $expectedBaselinePatchSha256
                wrapperIdentity = ([string]$receipt.wrapperIdentity).ToLowerInvariant()
                hostRunnerSha256 = ([string]$receipt.hostRunnerSha256).ToLowerInvariant()
            }
        }
        Write-JsonFile -Path (Join-Path $candidateOutput 'source-file-audit.json') -Value $auditObject
        $summaryRecords.Add([ordered]@{
            candidate = $candidateName
            status = if ($candidateArchiveValid) { 'ARCHIVED_SOURCE_ONLY' } else { 'ARCHIVED_SOURCE_INVALID' }
            sourceIdentity = [string]$snapshot.sourceIdentity
            sourceSnapshotSha256 = $snapshotSha
            receiptSha256 = $receiptSha
            sourceFileCount = $entries.Count
            sourceBytes = $sourceBytes
            declaredSnapshotAppByteMatch = $auditObject.declaredSnapshotAppByteMatch.status
            declaredSnapshotMismatchCount = @($declaredMismatches).Count
            snapshotMismatchClassification = [string]$auditObject.declaredSnapshotAppByteMatch.classification
            baselinePatchVerification = $baselineReverse.status
            sourceSnapshotReceiptHashMatch = $receiptSnapshotMatch
            effectiveSourceArchive = if ($null -ne $sourceArchive) {
                [ordered]@{
                    status = 'INCLUDED'
                    path = "$candidateName/source-tree.tar.gz"
                    bytes = $sourceArchive.bytes
                    sha256 = $sourceArchive.sha256
                    extractedBytesMatchSource = 'PASS'
                }
            }
            else {
                [ordered]@{
                    status = 'OMITTED_COMPACT'
                    reconstructionInputs = @(
                        "${candidateName}/source-delta.patch.gz",
                        "${candidateName}/source-delta.json",
                        "${candidateName}/prepare-receipt.json",
                        "${candidateName}/source-snapshot.json",
                        "${candidateName}/overlay.tar.gz",
                        'base HEAD identified by archive-manifest.json.baseHead'
                    )
                }
            }
            sourceDelta = [ordered]@{
                manifestPath = "$candidateName/source-delta.json"
                patchPath = "$candidateName/source-delta.patch.gz"
                changedFileCount = @($deltaChanges).Count
                patchRoundTrip = $deltaObject.patch.roundTrip
                reconstruction = $reconstruction.status
            }
            overlayArchive = [ordered]@{
                path = "$candidateName/overlay.tar.gz"
                bytes = $overlayArchive.bytes
                sha256 = $overlayArchive.sha256
                extractedBytesMatchSource = 'PASS'
            }
            overlayDeclaredBytesMatch = $auditObject.overlay.declaredBytesMatch
            archiveValid = $candidateArchiveValid
            overlayIdentitySha256 = $overlayIdentity.sha256
            manifestSha256 = ([string]$receipt.manifestSha256).ToLowerInvariant()
            acceptancePlanSha256 = ([string]$receipt.acceptancePlanSha256).ToLowerInvariant()
            baselinePatchSha256 = ([string]$receipt.baselinePatchSha256).ToLowerInvariant()
            wrapperIdentity = ([string]$receipt.wrapperIdentity).ToLowerInvariant()
            hostRunnerSha256 = ([string]$receipt.hostRunnerSha256).ToLowerInvariant()
        })
    }

    $profileSummaryRecord = $null
    if (-not $SkipProfile) {
        $profileStage = Join-Path $scratch 'profile-stage'
        $profileOutput = Join-Path $ArchiveRoot 'profile'
        New-Item -ItemType Directory -Force -Path $profileOutput | Out-Null
        $env:PROFILE_ROOT = $ProfileRoot
        $profileSummaryRecord = New-ProfileArchive -ProfileRootPath $ProfileRoot -StageRoot $profileStage -OutputDirectory $profileOutput -CandidateNames $CandidateNames -WorkingDirectory $scratch
        Remove-Item Env:PROFILE_ROOT -ErrorAction SilentlyContinue
    }

    $invalidCandidates = @($summaryRecords | Where-Object {
        -not $_.archiveValid -or
        $_.snapshotMismatchClassification -eq 'UNCLASSIFIED_SNAPSHOT_MISMATCH' -or
        $_.baselinePatchVerification -ne 'PASS' -or
        $_.sourceDelta.reconstruction -ne 'PASS' -or
        $_.sourceDelta.patchRoundTrip -ne 'PASS' -or
        -not $_.sourceSnapshotReceiptHashMatch -or
        $_.overlayDeclaredBytesMatch -ne 'PASS' -or
        $_.overlayArchive.extractedBytesMatchSource -ne 'PASS'
    })
    $archiveRunValid = @($invalidCandidates).Count -eq 0

    $manifest = [ordered]@{
        schema = 1
        kind = 'Q30_SOURCE_ATTEMPT_ARCHIVE'
        generatedUtc = (Get-Date).ToUniversalTime().ToString('o')
        taskRootLabel = [IO.Path]::GetFileName($TaskRoot.TrimEnd('\', '/'))
        repoLabel = 'repository'
        baseHead = $baseHead
        observedRepositoryHead = $repoHead
        status = if ($archiveRunValid) { 'SOURCE_ARCHIVE_ONLY' } else { 'SOURCE_ARCHIVE_INVALID' }
        archiveRunStatus = if ($archiveRunValid) { 'PASS' } else { 'FAIL' }
        acceptanceStatus = 'NOT_ASSESSED'
        baselinePatch = [ordered]@{
            repositoryRelativePath = $expectedBaselinePatchRelative
            sha256 = $baselinePatchSha256
            pinnedSha256 = $expectedBaselinePatchSha256
            repositoryHashVerified = $baselinePatchSha256 -eq $expectedBaselinePatchSha256
            expectedPaths = @($expectedBaselinePaths)
        }
        compactMode = -not $IncludeSourceTrees
        exclusions = @(
            'jars and .tools content',
            'generated build outputs except the exact profile-linked compiled payload recorded under profile/',
            'browser profile directories and account-local state',
            'absolute/private paths',
            'full source-tree.tar.gz copies unless -IncludeSourceTrees is supplied'
        )
        candidates = @($summaryRecords)
        invalidCandidates = @($invalidCandidates | ForEach-Object { $_.candidate })
        profile = $profileSummaryRecord
        reconstructionBoundary = 'For each candidate, start from baseHead, apply source-delta.patch.gz at the repository root, and validate every source-delta.json changedFiles size and SHA-256. Then unpack overlay.tar.gz at the repository root and validate receipt.overlayFiles. The four declared snapshot/app mismatches are classified as EXPECTED_BASELINE_PATCH only when raw reverse restoration matches, or exact archived originals match all snapshot hashes and forward application of the pinned patch reproduces all effective app bytes. source-tree.tar.gz is optional audit evidence; no acceptance result is inferred.'
    }
    $manifestJson = Get-SafeJsonText -Value $manifest
    Write-Utf8NoBom -Path (Join-Path $ArchiveRoot 'archive-manifest.json') -Text ($manifestJson + [Environment]::NewLine)
    $manifestHash = Get-Sha256File -Path (Join-Path $ArchiveRoot 'archive-manifest.json')
    Write-Utf8NoBom -Path (Join-Path $ArchiveRoot 'archive-manifest.sha256') -Text ("{0}  archive-manifest.json{1}" -f $manifestHash, [Environment]::NewLine)
    if (-not $archiveRunValid) {
        Write-Output ("ARCHIVE FAIL {0} candidates={1} invalidCandidates={2} manifestSha256={3}" -f $ArchiveRoot, @($summaryRecords).Count, (@($invalidCandidates | ForEach-Object { $_.candidate }) -join ','), $manifestHash)
        throw 'Archive validation rejected: at least one candidate has an unclassified snapshot mismatch, failed baseline binding, failed delta reconstruction, or failed overlay/hash validation.'
    }
    Write-Output ("ARCHIVE PASS {0} candidates={1} manifestSha256={2}" -f $ArchiveRoot, @($summaryRecords).Count, $manifestHash)
}
finally {
    if (Test-Path -LiteralPath $scratch -PathType Container) {
        Write-Output ('ARCHIVE_SCRATCH_RETAINED ' + [IO.Path]::GetFileName($scratch))
    }
}

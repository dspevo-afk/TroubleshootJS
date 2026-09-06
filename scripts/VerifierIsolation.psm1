Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:VerifierHeldPortClaims = @{}
$script:VerifierHeldPortMutexes = @{}
$script:VerifierKernelTransportOwnerKind = 'kernel-transport'
$script:VerifierKernelTransportOwnerProof = 'run-owned-preview-http-sys-v1'
$script:VerifierKernelTransportOwnerEvidence = 'pid-4-system-http-sys'
$script:VerifierUserProcessOwnerKind = 'user-process'
$script:VerifierUserProcessOwnerProof = 'diagnostics-process-start-v1'
$script:VerifierUserProcessOwnerEvidence = 'system-diagnostics-process-starttime'
# This private reference makes the HTTP.sys authorization proof opaque to
# callers.  A protocol-shaped object with copied fields is not a proof.
$script:VerifierRunOwnedPreviewHttpSysProofMarker = [object]::new()
# Fixed-point browser cleanup uses the same reference-bound capability pattern.
# A copied protocol-shaped object must never authorize the natural-exit lane.
$script:VerifierBrowserDrainScopeMarker = [object]::new()
$script:VerifierBrowserDrainAttestationMarker = [object]::new()
$script:VerifierBrowserDrainScopes = New-Object Collections.ArrayList
$script:VerifierBrowserCloseAttempts = @{}

function Get-VerifierErrorMessage($ErrorRecord) {
    if ($null -eq $ErrorRecord) { return '' }
    if ($ErrorRecord.PSObject.Properties['Exception'] -and
            $null -ne $ErrorRecord.Exception) {
        return [string]$ErrorRecord.Exception.Message
    }
    return [string]$ErrorRecord
}

function Throw-VerifierInfrastructure([string]$Message) {
    $exception = [System.InvalidOperationException]::new(
        ('VERIFIER_INFRASTRUCTURE: ' + $Message))
    $exception.Data['VerifierFailureKind'] = 'infrastructure'
    throw $exception
}

function Throw-VerifierInfrastructureMissingProcess([string]$Message,
        [int]$ProcessId) {
    if ($ProcessId -le 0) {
        Throw-VerifierInfrastructure $Message
    }
    $exception = [System.InvalidOperationException]::new(
        ('VERIFIER_INFRASTRUCTURE: ' + $Message))
    $exception.Data['VerifierFailureKind'] = 'infrastructure'
    $exception.Data['VerifierMissingProcessId'] = [int]$ProcessId
    throw $exception
}

function Get-VerifierMissingProcessId($ErrorRecord) {
    $exception = if ($ErrorRecord -and $ErrorRecord.PSObject.Properties['Exception'] -and
            $null -ne $ErrorRecord.Exception) { $ErrorRecord.Exception } else { $ErrorRecord }
    if ($null -eq $exception -or $null -eq $exception.Data -or
            -not $exception.Data.Contains('VerifierMissingProcessId')) {
        return 0
    }
    $value = $exception.Data['VerifierMissingProcessId']
    if (-not (Test-VerifierStrictIntegralValue $value 1 ([int]::MaxValue))) {
        return 0
    }
    return [int]$value
}

function Test-VerifierInfrastructureError($ErrorRecord) {
    $exception = if ($ErrorRecord -and $ErrorRecord.PSObject.Properties['Exception'] -and
            $null -ne $ErrorRecord.Exception) { $ErrorRecord.Exception } else { $ErrorRecord }
    # An explicit verifier exit-2 result is infrastructure even when the
    # producer did not also attach the typed failure-kind marker. This keeps a
    # route/child result from being downgraded by a generic application catch.
    if ($exception -and $exception.Data -and
            $exception.Data.Contains('VerifierExitCode')) {
        $explicitExit = 0
        if ([int]::TryParse([string]$exception.Data['VerifierExitCode'],
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture,
                [ref]$explicitExit) -and $explicitExit -eq 2) {
            return $true
        }
    }
    if ($exception -and $exception.Data -and
            $exception.Data.Contains('VerifierFailureKind') -and
            [string]$exception.Data['VerifierFailureKind'] -eq 'infrastructure') {
        return $true
    }
    return (Get-VerifierErrorMessage $ErrorRecord).IndexOf(
        'VERIFIER_INFRASTRUCTURE:', [StringComparison]::OrdinalIgnoreCase) -ge 0
}

function Merge-VerifierFailureExitCode([int]$CurrentExitCode, [bool]$Infrastructure) {
    if ($Infrastructure -or $CurrentExitCode -eq 2) { return 2 }
    if ($CurrentExitCode -eq 1) { return 1 }
    return 1
}

function Get-VerifierFullPath($Path) {
    if ($null -eq $Path -or $Path.GetType() -ne [string]) {
        Throw-VerifierInfrastructure 'A required path was not an exact string.'
    }
    if ([String]::IsNullOrWhiteSpace($Path)) {
        Throw-VerifierInfrastructure 'A required path was empty.'
    }
    try {
        $absolute = [IO.Path]::GetFullPath($Path)
        $canonical = Get-VerifierCanonicalWindowsPath $absolute
        if ([String]::IsNullOrWhiteSpace($canonical)) {
            Throw-VerifierInfrastructure "Could not canonicalize required path: $Path"
        }
        return $canonical
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not normalize required path '$Path': " +
            (Get-VerifierErrorMessage $_))
    }
}

function Resolve-VerifierBrowserPath([string]$RequestedPath = '') {
    # Browser resolution is shared by the verifier and the live ownership
    # canary.  Do not assume that the host PowerShell bitness selects the
    # browser's installation directory: Edge may be installed in either
    # Program Files tree, or be available through PATH.
    $requested = [string]$RequestedPath
    $explicitPath = $false
    $executableName = 'msedge.exe'
    if (-not [String]::IsNullOrWhiteSpace($requested)) {
        $explicitPath = [IO.Path]::IsPathRooted($requested) -or
            $requested.IndexOf([char]92) -ge 0 -or
            $requested.IndexOf('/') -ge 0
        try {
            $requestedName = [IO.Path]::GetFileName($requested)
            if (-not [String]::IsNullOrWhiteSpace($requestedName)) {
                $executableName = $requestedName
            }
        } catch {
            Throw-VerifierInfrastructure "Could not derive the browser executable name from '$requested': $(Get-VerifierErrorMessage $_)"
        }
    }
    $candidates = New-Object Collections.ArrayList
    $seenCandidates = @{}
    $addCandidate = {
        param([string]$Candidate)
        if ([String]::IsNullOrWhiteSpace($Candidate)) { return }
        $key = $Candidate.ToLowerInvariant()
        if (-not $seenCandidates.ContainsKey($key)) {
            $seenCandidates[$key] = $true
            [void]$candidates.Add($Candidate)
        }
    }
    if (-not [String]::IsNullOrWhiteSpace($requested)) {
        if ($explicitPath) {
            & $addCandidate $requested
        } else {
            $command = Get-Command $requested -ErrorAction SilentlyContinue
            if ($null -ne $command) {
                $commandPath = if ($command.PSObject.Properties['Source']) {
                    [string]$command.Source
                } else { [string]$command.Path }
                & $addCandidate $commandPath
            }
        }
    } else {
        $command = Get-Command $executableName -ErrorAction SilentlyContinue
        if ($null -ne $command) {
            $commandPath = if ($command.PSObject.Properties['Source']) {
                [string]$command.Source
            } else { [string]$command.Path }
            & $addCandidate $commandPath
        }
    }
    if (-not $explicitPath) {
        $programRoots = @(
            $env:ProgramW6432,
            $env:ProgramFiles,
            ${env:ProgramFiles(x86)}
        )
        foreach ($programRoot in $programRoots) {
            if (-not [String]::IsNullOrWhiteSpace([string]$programRoot)) {
                & $addCandidate (Join-Path ([string]$programRoot) ('Microsoft\Edge\Application\' + $executableName))
            }
        }
        if (-not [String]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
            & $addCandidate (Join-Path $env:LOCALAPPDATA ('Microsoft\Edge\Application\' + $executableName))
        }
    }
    foreach ($candidate in @($candidates)) {
        try {
            if (-not (Test-Path -LiteralPath $candidate -PathType Leaf -ErrorAction Stop)) {
                continue
            }
            $resolved = Get-VerifierFullPath $candidate
            if (Test-Path -LiteralPath $resolved -PathType Leaf -ErrorAction Stop) {
                return $resolved
            }
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            # A stale PATH/program-files entry is not an ownership proof. Try
            # the next supported installation location and fail typed if none
            # remains.
        }
    }
    $description = if ($explicitPath) { $requested } else { $executableName }
    Throw-VerifierInfrastructure "Could not resolve the configured browser executable '$description' from PATH, Program Files, or Program Files (x86)."
}

function Get-VerifierRetainedProcessStartTimeValue($Process) {
    # Keep the only raw Process.StartTime read behind one retained-handle
    # boundary.  Callers decide whether the retained process must still be
    # live; this accessor never re-queries by PID and can therefore also read
    # the creation time from a launch handle after a naturally short-lived
    # child has exited.
    return $Process.StartTime
}

function Get-VerifierProcessStartTicks {
    param(
        [Parameter(Position = 0)]
        $Process,
        # This is an accessor-boundary test seam only. Production callers omit
        # it, so the default path below reads the retained Process.StartTime.
        # The Process object is still required to be a live real Process.
        [Parameter(Position = 1)]
        [scriptblock]$StartTimeAccessor = $null
    )

    if ($null -eq $Process) {
        Throw-VerifierInfrastructure 'Could not read process start identity from a null process object.'
    }
    if (-not ($Process -is [System.Diagnostics.Process])) {
        Throw-VerifierInfrastructure 'Could not read process start identity from a non-System.Diagnostics.Process object.'
    }

    $processId = 0
    try {
        $processId = [int]$Process.Id
    } catch {
        Throw-VerifierInfrastructure ('Could not read process PID while establishing start identity: ' +
            (Get-VerifierErrorMessage $_))
    }
    if ($processId -le 0) {
        Throw-VerifierInfrastructure 'Could not read process start identity because the retained process PID was not positive.'
    }

    $maxAttempts = 5
    $maxMilliseconds = 250
    $retryDelayMilliseconds = 25
    $stopwatchFrequency = [double][Diagnostics.Stopwatch]::Frequency
    if ($stopwatchFrequency -le 0) {
        Throw-VerifierInfrastructure 'Could not establish a monotonic clock for bounded process identity capture.'
    }
    $startedTimestamp = [Diagnostics.Stopwatch]::GetTimestamp()
    $lastFailure = ''

    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        # Refresh and liveness checks operate on this exact retained object.
        # No PID lookup is permitted inside the retry loop: a replacement
        # process must never become the identity of the original handle.
        try {
            [void]$Process.Refresh()
        } catch {
            Throw-VerifierInfrastructure ("Could not refresh retained process PID $processId before start-identity capture: " +
                (Get-VerifierErrorMessage $_))
        }
        try {
            if ([bool]$Process.HasExited) {
                Throw-VerifierInfrastructure "Retained process PID $processId exited before its start identity was established."
            }
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not confirm retained process PID $processId was alive before start-identity capture: " +
                (Get-VerifierErrorMessage $_))
        }

        $lastFailure = ''
        $identityReadSucceeded = $false
        $retryableUnavailable = $false

        # Keep raw accessor acquisition separate from validation. Only a
        # null/unavailable result is retryable; a thrown accessor error is an
        # immediate typed infrastructure failure because its cause is not
        # safely classifiable at this boundary.
        try {
            $startTimeValues = if ($null -ne $StartTimeAccessor) {
                @(& $StartTimeAccessor $Process)
            } else {
                @(Get-VerifierRetainedProcessStartTimeValue $Process)
            }
        } catch {
            Throw-VerifierInfrastructure ("Could not read retained process PID $processId StartTime: " +
                (Get-VerifierErrorMessage $_))
        }

        $startTimeValueCount = @($startTimeValues).Count
        if ($startTimeValueCount -eq 0) {
            # PowerShell represents a scriptblock that emitted only $null as
            # no pipeline output. Treat that representation as unavailable.
            $retryableUnavailable = $true
            $lastFailure = 'the retained process returned no StartTime value'
        } elseif ($startTimeValueCount -ne 1) {
            Throw-VerifierInfrastructure "The retained process returned $startTimeValueCount StartTime values instead of exactly one."
        } else {
            $startTime = @($startTimeValues)[0]
            if ($null -eq $startTime) {
                # An explicit one-element null array is the other supported
                # PowerShell representation of an unavailable accessor value.
                $retryableUnavailable = $true
                $lastFailure = 'the retained process returned a null StartTime value'
            } elseif (-not ($startTime -is [DateTime])) {
                Throw-VerifierInfrastructure 'The retained process returned a malformed non-DateTime StartTime value.'
            } else {
                try {
                    $utcStartTime = $startTime.ToUniversalTime()
                } catch {
                    Throw-VerifierInfrastructure ("Could not convert retained process PID $processId StartTime to UTC: " +
                        (Get-VerifierErrorMessage $_))
                }
                if ($null -eq $utcStartTime -or -not ($utcStartTime -is [DateTime])) {
                    Throw-VerifierInfrastructure 'The retained process returned a malformed UTC StartTime value.'
                }
                try {
                    $startTicks = [long]$utcStartTime.Ticks
                } catch {
                    Throw-VerifierInfrastructure ("Could not convert retained process PID $processId UTC StartTime to ticks: " +
                        (Get-VerifierErrorMessage $_))
                }
                if ($startTicks -le 0) {
                    Throw-VerifierInfrastructure 'The retained process returned a non-positive StartTime identity.'
                }
                $identityReadSucceeded = $true
            }
        }

        if ($identityReadSucceeded) {
            try {
                [void]$Process.Refresh()
                if ([bool]$Process.HasExited) {
                    Throw-VerifierInfrastructure "Retained process PID $processId exited before its start identity could be returned."
                }
            } catch {
                if (Test-VerifierInfrastructureError $_) { throw }
                Throw-VerifierInfrastructure ("Could not confirm retained process PID $processId remained alive after start-identity capture: " +
                    (Get-VerifierErrorMessage $_))
            }
            return [long]$startTicks
        }

        if (-not $retryableUnavailable) {
            Throw-VerifierInfrastructure "Retained process PID $processId did not provide a retryable unavailable StartTime result."
        }
        if ($attempt -ge $maxAttempts) { break }
        $elapsedMilliseconds = (([double]([Diagnostics.Stopwatch]::GetTimestamp() -
            $startedTimestamp)) * 1000.0) / $stopwatchFrequency
        if ($elapsedMilliseconds -ge $maxMilliseconds) { break }

        # A failed accessor read is retryable only after the same object has
        # again proved alive. Refreshing this object cannot authorize a PID
        # replacement and keeps transient StartTime availability bounded.
        try {
            [void]$Process.Refresh()
            if ([bool]$Process.HasExited) {
                Throw-VerifierInfrastructure "Retained process PID $processId exited while its start identity was unavailable."
            }
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not confirm retained process PID $processId remained alive for a bounded identity retry: " +
                (Get-VerifierErrorMessage $_))
        }
        $remainingMilliseconds = [Math]::Max(1, [int][Math]::Floor(
            $maxMilliseconds - $elapsedMilliseconds))
        Start-Sleep -Milliseconds ([Math]::Min($retryDelayMilliseconds, $remainingMilliseconds))
    }

    $detail = if ([String]::IsNullOrWhiteSpace($lastFailure)) {
        'the retained process identity was unavailable'
    } else { $lastFailure }
    Throw-VerifierInfrastructure ("Could not establish a positive start identity for retained process PID $processId " +
        "within $maxAttempts attempts/$maxMilliseconds ms: $detail")
}

function Get-VerifierRepositoryIdentity([string]$WorktreeRoot) {
    $canonicalRoot = Get-VerifierCanonicalWindowsPath $WorktreeRoot
    if ([String]::IsNullOrWhiteSpace($canonicalRoot)) {
        Throw-VerifierInfrastructure 'Could not canonicalize worktree root for repository identity.'
    }
    # Repository identity is a physical namespace identity.  A lexical path
    # through a junction could otherwise make two worktrees share one server,
    # profile, or evidence namespace.
    $physicalRoot = Get-VerifierPhysicalExistingPath $canonicalRoot
    if ([String]::IsNullOrWhiteSpace($physicalRoot)) {
        Throw-VerifierInfrastructure 'Could not prove the physical repository root for repository identity.'
    }
    $canonicalRoot = $physicalRoot
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($canonicalRoot.ToLowerInvariant())
        return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').Substring(0, 20).ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Get-VerifierUtcText() {
    return [DateTime]::UtcNow.ToString('o', [Globalization.CultureInfo]::InvariantCulture)
}

function Get-VerifierCurrentProcessStartTicks() {
    try {
        return Get-VerifierProcessStartTicks (Get-Process -Id $PID -ErrorAction Stop)
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not read verifier process start identity: " +
            (Get-VerifierErrorMessage $_))
    }
}

function ConvertFrom-VerifierWindowsCommandLine([string]$CommandLine) {
    if ([String]::IsNullOrWhiteSpace($CommandLine)) { return @() }
    $tokens = New-Object Collections.ArrayList
    $builder = New-Object Text.StringBuilder
    $inQuotes = $false
    $tokenStarted = $false
    $index = 0
    while ($index -lt $CommandLine.Length) {
        $character = $CommandLine[$index]
        if ($character -eq [char]92) {
            $slashCount = 0
            while ($index -lt $CommandLine.Length -and
                    $CommandLine[$index] -eq [char]92) {
                $slashCount++
                $index++
            }
            if ($index -lt $CommandLine.Length -and
                    $CommandLine[$index] -eq [char]34) {
                for ($slashIndex = 0; $slashIndex -lt [Math]::Floor($slashCount / 2); $slashIndex++) {
                    [void]$builder.Append([char]92)
                }
                if (($slashCount % 2) -eq 1) {
                    [void]$builder.Append([char]34)
                    $tokenStarted = $true
                    $index++
                } else {
                    $inQuotes = -not $inQuotes
                    $tokenStarted = $true
                    $index++
                }
            } else {
                for ($slashIndex = 0; $slashIndex -lt $slashCount; $slashIndex++) {
                    [void]$builder.Append([char]92)
                }
                $tokenStarted = $true
            }
            continue
        }
        if ($character -eq [char]34) {
            $inQuotes = -not $inQuotes
            $tokenStarted = $true
            $index++
            continue
        }
        if (-not $inQuotes -and [char]::IsWhiteSpace($character)) {
            if ($tokenStarted) {
                [void]$tokens.Add($builder.ToString())
                [void]$builder.Clear()
                $tokenStarted = $false
            }
            $index++
            continue
        }
        [void]$builder.Append($character)
        $tokenStarted = $true
        $index++
    }
    if ($tokenStarted) { [void]$tokens.Add($builder.ToString()) }
    return @($tokens)
}

function Get-VerifierCanonicalWindowsPath([string]$Path) {
    if ([String]::IsNullOrWhiteSpace($Path)) { return '' }
    try {
        $candidate = ([string]$Path).Replace('/', '\')
        # Ownership identity must never depend on an unknown process current
        # directory. Callers that accept relative developer inputs first pass
        # them through Get-VerifierFullPath; command-line identity values must
        # already be absolute.
        if ($candidate -notmatch '^(?:[A-Za-z]:\\|\\\\)') { return '' }
        $full = [IO.Path]::GetFullPath($candidate)
        $root = [IO.Path]::GetPathRoot($full)
        if ([String]::IsNullOrWhiteSpace($full) -or
                [String]::IsNullOrWhiteSpace($root)) { return '' }
        if ($full.Length -gt $root.Length) {
            return $full.TrimEnd([char]92)
        }
        return $root
    } catch {
        return ''
    }
}

function Test-VerifierCanonicalWindowsPathValue([string]$Actual, [string]$Expected) {
    $actualCanonical = Get-VerifierCanonicalWindowsPath $Actual
    $expectedCanonical = Get-VerifierCanonicalWindowsPath $Expected
    if ([String]::IsNullOrWhiteSpace($actualCanonical) -or
            [String]::IsNullOrWhiteSpace($expectedCanonical)) {
        return $false
    }
    if ($actualCanonical.Equals($expectedCanonical, [StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }
    # Lexical normalization is sufficient for not-yet-created paths and for
    # command-line forms such as separator/case/dot-segment variants.  When
    # two existing paths have different lexical spellings, use the same
    # physical identity routine used by ownership checks.  This closes the
    # junction/symlink alias gap without turning an unavailable physical proof
    # into an ownership pass.
    try {
        $actualPhysical = Get-VerifierCanonicalPhysicalPath $actualCanonical
        $expectedPhysical = Get-VerifierCanonicalPhysicalPath $expectedCanonical
        return -not [String]::IsNullOrWhiteSpace($actualPhysical) -and
            -not [String]::IsNullOrWhiteSpace($expectedPhysical) -and
            $actualPhysical.Equals($expectedPhysical, [StringComparison]::OrdinalIgnoreCase)
    } catch {
        return $false
    }
}

function Get-VerifierNativeFinalPath([string]$ExistingPath) {
    $canonical = Get-VerifierCanonicalWindowsPath $ExistingPath
    if ([String]::IsNullOrWhiteSpace($canonical)) { return '' }
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
        return $canonical
    }
    try {
        $nativeType = ([System.Management.Automation.PSTypeName]'VerifierNativePathApi').Type
        if ($null -eq $nativeType) {
            Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;
public static class VerifierNativePathApi {
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern IntPtr CreateFile(
        string name, uint access, uint share, IntPtr security, uint creation,
        uint flags, IntPtr template);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern uint GetFinalPathNameByHandle(
        IntPtr handle, StringBuilder path, uint length, uint flags);
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool CloseHandle(IntPtr handle);
}
'@ -ErrorAction Stop
            $nativeType = ([System.Management.Automation.PSTypeName]'VerifierNativePathApi').Type
        }
        $invalidHandle = [IntPtr]::new(-1)
        $handle = $nativeType::CreateFile($canonical, 0x00000080, 0x00000007,
            [IntPtr]::Zero, 3, 0x02000000, [IntPtr]::Zero)
        if ($handle -eq $invalidHandle) {
            Throw-VerifierInfrastructure "Could not open '$canonical' to prove its physical path."
        }
        try {
            $builder = New-Object Text.StringBuilder 32768
            $length = $nativeType::GetFinalPathNameByHandle($handle, $builder,
                [uint32]$builder.Capacity, 0)
            if ($length -le 0 -or $length -ge $builder.Capacity) {
                Throw-VerifierInfrastructure "Could not read the final physical path for '$canonical'."
            }
            $final = $builder.ToString()
            if ($final.StartsWith('\\?\UNC\', [StringComparison]::OrdinalIgnoreCase)) {
                $final = '\\' + $final.Substring(8)
            } elseif ($final.StartsWith('\\?\', [StringComparison]::OrdinalIgnoreCase)) {
                $final = $final.Substring(4)
            }
            $normalized = Get-VerifierCanonicalWindowsPath $final
            if ([String]::IsNullOrWhiteSpace($normalized)) {
                Throw-VerifierInfrastructure "The final physical path for '$canonical' was invalid."
            }
            return $normalized
        } finally {
            [void]$nativeType::CloseHandle($handle)
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure "Could not prove the physical path for '$canonical': $(Get-VerifierErrorMessage $_)"
    }
}

function Get-VerifierNearestExistingPath([string]$Path) {
    $canonical = Get-VerifierCanonicalWindowsPath $Path
    if ([String]::IsNullOrWhiteSpace($canonical)) { return '' }
    $candidate = $canonical
    while ($true) {
        try {
            if (Test-Path -LiteralPath $candidate -ErrorAction Stop) { return $candidate }
        } catch {
            Throw-VerifierInfrastructure "Could not inspect path '$candidate' while proving its physical namespace: $(Get-VerifierErrorMessage $_)"
        }
        $parent = Split-Path -Parent $candidate
        if ([String]::IsNullOrWhiteSpace($parent) -or
                $parent.Equals($candidate, [StringComparison]::OrdinalIgnoreCase)) {
            return ''
        }
        $candidate = Get-VerifierCanonicalWindowsPath $parent
    }
}

function Assert-VerifierNoReparseAncestors([string]$Path) {
    $canonical = Get-VerifierCanonicalWindowsPath $Path
    if ([String]::IsNullOrWhiteSpace($canonical)) {
        Throw-VerifierInfrastructure 'Cannot inspect a blank path for reparse safety.'
    }
    $existing = Get-VerifierNearestExistingPath $canonical
    if ([String]::IsNullOrWhiteSpace($existing)) {
        Throw-VerifierInfrastructure "Could not find an existing ancestor for '$canonical'."
    }
    $cursor = $existing
    while ($true) {
        try {
            $item = Get-Item -LiteralPath $cursor -Force -ErrorAction Stop
        } catch {
            Throw-VerifierInfrastructure "Could not inspect path component '$cursor' for reparse safety: $(Get-VerifierErrorMessage $_)"
        }
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            Throw-VerifierInfrastructure "Refusing a path that traverses reparse point '$cursor'."
        }
        $parent = Split-Path -Parent $cursor
        if ([String]::IsNullOrWhiteSpace($parent) -or
                $parent.Equals($cursor, [StringComparison]::OrdinalIgnoreCase)) { break }
        $cursor = Get-VerifierCanonicalWindowsPath $parent
    }
}

function Get-VerifierPhysicalExistingPath([string]$Path) {
    $canonical = Get-VerifierCanonicalWindowsPath $Path
    if ([String]::IsNullOrWhiteSpace($canonical)) { return '' }
    $existing = Get-VerifierNearestExistingPath $canonical
    if ([String]::IsNullOrWhiteSpace($existing)) { return '' }
    Assert-VerifierNoReparseAncestors $existing
    return Get-VerifierNativeFinalPath $existing
}

function Get-VerifierCanonicalPhysicalPath([string]$Path) {
    # Return one physical identity for both existing and not-yet-created
    # verifier paths.  For a missing path, the already-existing ancestor is
    # resolved first and the remaining lexical suffix is appended only after
    # every ancestor has passed the reparse check.  Callers use this routine at
    # the same ownership boundary for writes, reads, serving, and deletion.
    $canonical = Get-VerifierFullPath $Path
    if ([String]::IsNullOrWhiteSpace($canonical)) {
        Throw-VerifierInfrastructure 'Cannot resolve a blank path physically.'
    }
    Assert-VerifierNoReparseAncestors $canonical
    $exists = $false
    try {
        $exists = [bool](Test-Path -LiteralPath $canonical -ErrorAction Stop)
    } catch {
        Throw-VerifierInfrastructure "Could not inspect '$canonical' while resolving its physical path: $(Get-VerifierErrorMessage $_)"
    }
    if ($exists) {
        $physical = Get-VerifierNativeFinalPath $canonical
        if ([String]::IsNullOrWhiteSpace($physical)) {
            Throw-VerifierInfrastructure "Could not resolve the physical path for '$canonical'."
        }
        return $physical
    }
    $nearest = Get-VerifierNearestExistingPath $canonical
    if ([String]::IsNullOrWhiteSpace($nearest)) {
        Throw-VerifierInfrastructure "Could not resolve an existing physical ancestor for '$canonical'."
    }
    $nearestPhysical = Get-VerifierPhysicalExistingPath $nearest
    if ([String]::IsNullOrWhiteSpace($nearestPhysical)) {
        Throw-VerifierInfrastructure "Could not resolve the physical ancestor for '$canonical'."
    }
    $remaining = $canonical.Substring($nearest.Length).TrimStart([char]92)
    if ([String]::IsNullOrWhiteSpace($remaining)) { return $nearestPhysical }
    $physicalCandidate = Get-VerifierCanonicalWindowsPath (Join-Path $nearestPhysical $remaining)
    if ([String]::IsNullOrWhiteSpace($physicalCandidate)) {
        Throw-VerifierInfrastructure "Could not resolve the physical destination for '$canonical'."
    }
    return $physicalCandidate
}

function Test-VerifierPhysicalChildPath([string]$Root, [string]$Candidate) {
    $rootFull = Get-VerifierCanonicalWindowsPath (Get-VerifierFullPath $Root)
    $candidateFull = Get-VerifierCanonicalWindowsPath (Get-VerifierFullPath $Candidate)
    if ([String]::IsNullOrWhiteSpace($rootFull) -or
            [String]::IsNullOrWhiteSpace($candidateFull)) { return $false }
    if ($candidateFull.Equals($rootFull, [StringComparison]::OrdinalIgnoreCase)) {
        return $false
    }
    $lexicalRoot = $rootFull.TrimEnd([char]92) + [char]92
    if (-not $candidateFull.StartsWith($lexicalRoot, [StringComparison]::OrdinalIgnoreCase)) {
        return $false
    }
    $rootPhysical = Get-VerifierCanonicalPhysicalPath $rootFull
    $candidatePhysical = Get-VerifierCanonicalPhysicalPath $candidateFull
    if ([String]::IsNullOrWhiteSpace($rootPhysical) -or
            [String]::IsNullOrWhiteSpace($candidatePhysical)) {
        Throw-VerifierInfrastructure 'Could not prove the physical path for an owned namespace.'
    }
    if ($candidatePhysical.Equals($rootPhysical, [StringComparison]::OrdinalIgnoreCase)) {
        return $false
    }
    $physicalRoot = $rootPhysical.TrimEnd([char]92) + [char]92
    return $candidatePhysical.StartsWith($physicalRoot, [StringComparison]::OrdinalIgnoreCase)
}

function Assert-VerifierPhysicalOwnedPath([string]$Root, [string]$Candidate,
        [switch]$AllowRoot, [switch]$ValidateTree) {
    # This is the common ownership boundary for verifier paths. It validates
    # the lexical form, the physical final path, and all existing ancestors
    # before a caller reads, writes, serves, or deletes the path. Namespaces
    # that are recursively owned can request a complete descendant walk.
    $rootCanonical = Get-VerifierFullPath $Root
    $candidateCanonical = Get-VerifierFullPath $Candidate
    Assert-VerifierNoReparseAncestors $rootCanonical
    Assert-VerifierNoReparseAncestors $candidateCanonical
    $rootPhysical = Get-VerifierCanonicalPhysicalPath $rootCanonical
    $candidatePhysical = Get-VerifierCanonicalPhysicalPath $candidateCanonical
    if ([String]::IsNullOrWhiteSpace($rootPhysical) -or
            [String]::IsNullOrWhiteSpace($candidatePhysical)) {
        Throw-VerifierInfrastructure 'Could not prove the physical identity of an owned path.'
    }
    if ($AllowRoot) {
        if (-not $rootPhysical.Equals($candidatePhysical,
                [StringComparison]::OrdinalIgnoreCase)) {
            Throw-VerifierInfrastructure "Path '$candidateCanonical' was not the owned namespace root '$rootCanonical'."
        }
    } else {
        $lexicalRoot = $rootCanonical.TrimEnd([char]92) + [char]92
        $physicalChild = $rootPhysical.TrimEnd([char]92) + [char]92
        if ($candidateCanonical.Equals($rootCanonical,
                [StringComparison]::OrdinalIgnoreCase) -or
                -not $candidateCanonical.StartsWith($lexicalRoot,
                    [StringComparison]::OrdinalIgnoreCase) -or
                $candidatePhysical.Equals($rootPhysical,
                    [StringComparison]::OrdinalIgnoreCase) -or
                -not $candidatePhysical.StartsWith($physicalChild,
                    [StringComparison]::OrdinalIgnoreCase)) {
            Throw-VerifierInfrastructure "Path '$candidateCanonical' escaped the physical namespace '$rootCanonical'."
        }
    }
    if ($ValidateTree -and (Test-Path -LiteralPath $candidateCanonical -ErrorAction Stop)) {
        Assert-VerifierNoReparseTree $candidateCanonical
    }
    return $candidateCanonical
}

function Assert-VerifierNoReparseTree([string]$Path) {
    $canonical = Get-VerifierCanonicalWindowsPath $Path
    if ([String]::IsNullOrWhiteSpace($canonical)) {
        Throw-VerifierInfrastructure 'Cannot inspect a blank tree for reparse safety.'
    }
    if (-not (Test-Path -LiteralPath $canonical -ErrorAction Stop)) { return }
    Assert-VerifierNoReparseAncestors $canonical
    $physicalRoot = Get-VerifierPhysicalExistingPath $canonical
    foreach ($item in @(Get-ChildItem -LiteralPath $canonical -Force -Recurse -ErrorAction Stop)) {
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            Throw-VerifierInfrastructure "Refusing recursive access to reparse point '$($item.FullName)'."
        }
        $physicalItem = Get-VerifierPhysicalExistingPath $item.FullName
        # Windows PowerShell includes a file passed as -LiteralPath in the
        # recursive enumeration. The tree root was already validated above;
        # only descendants must be tested for physical escape.
        if ([String]::IsNullOrWhiteSpace($physicalItem)) {
            Throw-VerifierInfrastructure "Physical tree path '$($item.FullName)' could not be proven."
        }
        if ($physicalItem.Equals($physicalRoot, [StringComparison]::OrdinalIgnoreCase)) {
            continue
        }
        if (-not $physicalItem.StartsWith($physicalRoot.TrimEnd([char]92) + [char]92,
                    [StringComparison]::OrdinalIgnoreCase)) {
            Throw-VerifierInfrastructure "Physical tree path '$($item.FullName)' escaped its owned namespace."
        }
    }
}

function Remove-VerifierOwnedTree($Root, $Target) {
    if ($null -eq $Root -or $Root.GetType() -ne [string] -or
            [String]::IsNullOrWhiteSpace($Root) -or
            $null -eq $Target -or $Target.GetType() -ne [string] -or
            [String]::IsNullOrWhiteSpace($Target)) {
        Throw-VerifierInfrastructure 'Owned-tree deletion requires exact non-empty root and target paths.'
    }
    $rootCanonical = Get-VerifierFullPath $Root
    $targetCanonical = Get-VerifierFullPath $Target
    Assert-VerifierNoReparseAncestors $rootCanonical
    if (-not (Test-VerifierPhysicalChildPath $rootCanonical $targetCanonical)) {
        Throw-VerifierInfrastructure "Refusing to delete a path outside the owned namespace: $Target"
    }
    if (-not (Test-Path -LiteralPath $targetCanonical -ErrorAction Stop)) { return }
    Assert-VerifierNoReparseTree $targetCanonical
    # Recheck the physical namespace after the tree walk and immediately
    # before deletion. A replaced junction/reparse point must fail closed.
    Assert-VerifierNoReparseAncestors $rootCanonical
    if (-not (Test-VerifierPhysicalChildPath $rootCanonical $targetCanonical)) {
        Throw-VerifierInfrastructure "Physical deletion path was not owned: $targetCanonical"
    }
    try {
        Remove-Item -LiteralPath $targetCanonical -Recurse -Force -ErrorAction Stop
    } catch {
        Throw-VerifierInfrastructure "Could not remove owned path '$targetCanonical': $(Get-VerifierErrorMessage $_)"
    }
    if (Test-Path -LiteralPath $targetCanonical -ErrorAction Stop) {
        Throw-VerifierInfrastructure "Owned path remained after deletion: $targetCanonical"
    }
}

function Test-VerifierCommandLineValue([string]$Actual, [string]$Expected) {
    $actualNormalized = ([string]$Actual).Replace('/', '\')
    $expectedNormalized = ([string]$Expected).Replace('/', '\')
    return $actualNormalized.Equals($expectedNormalized, [StringComparison]::OrdinalIgnoreCase)
}

function Test-VerifierPathValuedSwitch([string]$Switch) {
    # Keep this list explicit.  --tsj-verifier-worktree is an opaque
    # repository identity token in the current browser command line, not a
    # path; treating every switch as a path would reject that identity.
    return @('-File', '-WorktreeRoot', '-WebRoot', '-PreviewScript', '-Script',
        '-VerifierWorktreePath', '-VerifierWebRoot', '--user-data-dir',
        '--user-data-dir-path', '--tsj-verifier-worktree-path') -contains $Switch
}

function Test-VerifierCommandLineEquivalent($Actual, $Expected) {
    if ([String]::IsNullOrWhiteSpace([string]$Actual) -or
            [String]::IsNullOrWhiteSpace([string]$Expected)) { return $false }
    try {
        $actualTokens = @(ConvertFrom-VerifierWindowsCommandLine ([string]$Actual))
        $expectedTokens = @(ConvertFrom-VerifierWindowsCommandLine ([string]$Expected))
    } catch { return $false }
    if ($actualTokens.Count -ne $expectedTokens.Count -or $actualTokens.Count -eq 0) {
        return $false
    }
    for ($index = 0; $index -lt $actualTokens.Count; $index++) {
        $actualToken = [string]$actualTokens[$index]
        $expectedToken = [string]$expectedTokens[$index]
        $isPathValue = $false
        if ($index -gt 0 -and
                (Test-VerifierPathValuedSwitch ([string]$expectedTokens[$index - 1]))) {
            $isPathValue = $true
        }
        $expectedEquals = $expectedToken.IndexOf('=')
        $actualEquals = $actualToken.IndexOf('=')
        if ($expectedEquals -gt 0 -and $actualEquals -gt 0 -and
                $expectedToken.Substring(0, $expectedEquals).Equals(
                    $actualToken.Substring(0, $actualEquals),
                    [StringComparison]::OrdinalIgnoreCase) -and
                (Test-VerifierPathValuedSwitch $expectedToken.Substring(0, $expectedEquals))) {
            $isPathValue = $true
            $expectedToken = $expectedToken.Substring($expectedEquals + 1)
            $actualToken = $actualToken.Substring($actualEquals + 1)
        }
        if ($isPathValue) {
            if (-not (Test-VerifierCanonicalWindowsPathValue $actualToken $expectedToken)) {
                return $false
            }
            continue
        }
        if ($index -eq 0) {
            $actualPath = Get-VerifierCanonicalWindowsPath $actualToken
            $expectedPath = Get-VerifierCanonicalWindowsPath $expectedToken
            if (-not [String]::IsNullOrWhiteSpace($actualPath) -and
                    -not [String]::IsNullOrWhiteSpace($expectedPath)) {
                if (-not $actualPath.Equals($expectedPath, [StringComparison]::OrdinalIgnoreCase)) {
                    return $false
                }
                continue
            }
        }
        if (-not $actualToken.Equals($expectedToken, [StringComparison]::OrdinalIgnoreCase)) {
            return $false
        }
    }
    return $true
}

function Test-VerifierCommandLineSwitch($CommandLine, [string]$Switch, [string]$Value) {
    if ([String]::IsNullOrWhiteSpace([string]$CommandLine) -or
            [String]::IsNullOrWhiteSpace($Switch)) { return $false }
    $tokens = @(ConvertFrom-VerifierWindowsCommandLine ([string]$CommandLine))
    $expectedCanonicalPath = Get-VerifierCanonicalWindowsPath $Value
    $pathValueExpected = (Test-VerifierPathValuedSwitch $Switch) -or
        -not [String]::IsNullOrWhiteSpace($expectedCanonicalPath)
    for ($index = 0; $index -lt $tokens.Count; $index++) {
        $token = [string]$tokens[$index]
        $valueMatches = {
            param([string]$Actual)
            if ($pathValueExpected) {
                return Test-VerifierCanonicalWindowsPathValue $Actual $expectedCanonicalPath
            }
            return Test-VerifierCommandLineValue $Actual $Value
        }
        if ($token.Equals($Switch, [StringComparison]::OrdinalIgnoreCase) -and
                $index + 1 -lt $tokens.Count -and
                (& $valueMatches ([string]$tokens[$index + 1]))) {
            return $true
        }
        $equalsIndex = $token.IndexOf('=')
        if ($equalsIndex -gt 0 -and
                $token.Substring(0, $equalsIndex).Equals($Switch,
                    [StringComparison]::OrdinalIgnoreCase) -and
                (& $valueMatches $token.Substring($equalsIndex + 1))) {
            return $true
        }
    }
    return $false
}

function Test-VerifierCommandLinePath($CommandLine, [string]$Path) {
    if ([String]::IsNullOrWhiteSpace([string]$CommandLine) -or
            [String]::IsNullOrWhiteSpace($Path)) { return $false }
    $canonicalPath = Get-VerifierCanonicalWindowsPath $Path
    if ([String]::IsNullOrWhiteSpace($canonicalPath)) { return $false }
    $tokens = @(ConvertFrom-VerifierWindowsCommandLine ([string]$CommandLine))
    for ($index = 0; $index -lt $tokens.Count; $index++) {
        if ((Test-VerifierCanonicalWindowsPathValue ([string]$tokens[$index]) $canonicalPath) -and
                $index -gt 0 -and
                ([string]$tokens[$index - 1]).Equals('-File',
                    [StringComparison]::OrdinalIgnoreCase)) {
            return $true
        }
        $fileEqualsPrefix = '-File='
        $token = [string]$tokens[$index]
        if ($token.StartsWith($fileEqualsPrefix,
                [StringComparison]::OrdinalIgnoreCase)) {
            if (Test-VerifierCanonicalWindowsPathValue $token.Substring(
                    $fileEqualsPrefix.Length) $canonicalPath) {
                return $true
            }
        }
    }
    return $false
}

function Test-VerifierCommandLineCanonicalPathToken($CommandLine, [string]$Path) {
    # Scripting hosts such as cscript.exe receive their script as a positional
    # argument rather than PowerShell's -File switch.  Keep this a token-level
    # check: it accepts only one complete canonical path token and never uses a
    # substring/prefix match. Callers must still prove the host executable,
    # ancestry, and all other ownership fields independently.
    if ([String]::IsNullOrWhiteSpace([string]$CommandLine) -or
            [String]::IsNullOrWhiteSpace($Path)) { return $false }
    $canonicalPath = Get-VerifierCanonicalWindowsPath $Path
    if ([String]::IsNullOrWhiteSpace($canonicalPath)) { return $false }
    foreach ($token in @(ConvertFrom-VerifierWindowsCommandLine ([string]$CommandLine))) {
        if (Test-VerifierCanonicalWindowsPathValue ([string]$token) $canonicalPath) {
            return $true
        }
    }
    return $false
}

function ConvertTo-VerifierWindowsArgument($Value) {
    # Start-Process on Windows PowerShell 5.1 ultimately receives one Windows
    # command line. Always quote with CommandLineToArgvW-compatible backslash
    # handling so a worktree/profile/script path containing spaces is one
    # argument, including when it ends in a backslash.
    if (-not (Test-VerifierStrictStringValue $Value)) {
        Throw-VerifierInfrastructure 'A Windows process argument was not an exact string.'
    }
    if ($Value.Length -eq 0) { return '""' }
    $builder = New-Object Text.StringBuilder
    [void]$builder.Append([char]34)
    $backslashes = 0
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq [char]92) {
            $backslashes++
            continue
        }
        if ($character -eq [char]34) {
            for ($i = 0; $i -lt (2 * $backslashes + 1); $i++) {
                [void]$builder.Append([char]92)
            }
            [void]$builder.Append([char]34)
            $backslashes = 0
            continue
        }
        for ($i = 0; $i -lt $backslashes; $i++) {
            [void]$builder.Append([char]92)
        }
        [void]$builder.Append($character)
        $backslashes = 0
    }
    for ($i = 0; $i -lt (2 * $backslashes); $i++) {
        [void]$builder.Append([char]92)
    }
    [void]$builder.Append([char]34)
    return $builder.ToString()
}

function Assert-VerifierArgumentArray($Arguments,
        [string]$Label = 'verifier process arguments') {
    if ($null -eq $Arguments -or -not $Arguments.GetType().IsArray -or
            $Arguments.GetType().GetArrayRank() -ne 1) {
        Throw-VerifierInfrastructure "$Label must be one exact argument array before process start."
    }
    foreach ($argument in $Arguments) {
        if (-not (Test-VerifierStrictStringValue $argument)) {
            Throw-VerifierInfrastructure "$Label contained a non-string argument before process start."
        }
    }
}

function Get-VerifierLaunchedProcessStartTicks($Process) {
    # This launch-only boundary is deliberately narrower than
    # Get-VerifierProcessStartTicks.  The shared identity helper requires a
    # live retained process before and after the read, which is correct for
    # ownership/adoption callers.  A bounded launcher may instead observe a
    # child that exits between Process.Start() and the first identity read.
    # The retained Process handle still carries the exact creation time; read
    # only that handle and require a positive DateTime identity.  No PID lookup
    # or current-process adoption is permitted here.
    if ($null -eq $Process -or $Process.GetType() -ne [Diagnostics.Process]) {
        Throw-VerifierInfrastructure 'Could not read launched process start identity from a non-System.Diagnostics.Process object.'
    }
    $processId = 0
    try {
        $processId = [int]$Process.Id
    } catch {
        Throw-VerifierInfrastructure ('Could not read launched process PID while establishing start identity: ' +
            (Get-VerifierErrorMessage $_))
    }
    if ($processId -le 0) {
        Throw-VerifierInfrastructure 'Could not read launched process start identity because the retained launch PID was not positive.'
    }
    $startTimeValues = @()
    try {
        $startTimeValues = @(Get-VerifierRetainedProcessStartTimeValue $Process)
    } catch {
        Throw-VerifierInfrastructure ("Could not read retained launched process PID $processId StartTime: " +
            (Get-VerifierErrorMessage $_))
    }
    if ($startTimeValues.Count -ne 1 -or $null -eq $startTimeValues[0]) {
        Throw-VerifierInfrastructure "Retained launched process PID $processId did not provide exactly one non-null StartTime value."
    }
    $startTime = $startTimeValues[0]
    if (-not ($startTime -is [DateTime])) {
        Throw-VerifierInfrastructure 'The retained launched process returned a malformed non-DateTime StartTime value.'
    }
    try {
        $utcStartTime = $startTime.ToUniversalTime()
        $startTicks = [long]$utcStartTime.Ticks
    } catch {
        Throw-VerifierInfrastructure ("Could not convert retained launched process PID $processId StartTime to ticks: " +
            (Get-VerifierErrorMessage $_))
    }
    if ($startTicks -le 0) {
        Throw-VerifierInfrastructure 'The retained launched process returned a non-positive StartTime identity.'
    }
    return $startTicks
}

function Assert-VerifierProcessInvocationBoundary($FilePath, $Arguments,
        [string]$Label = 'verifier process') {
    if (-not (Test-VerifierStrictStringValue $FilePath) -or
            [String]::IsNullOrWhiteSpace($FilePath)) {
        Throw-VerifierInfrastructure "$Label path must be one exact non-empty string before process start."
    }
    Assert-VerifierArgumentArray $Arguments ($Label + ' arguments')
    return [pscustomobject]@{ FilePath = $FilePath; Arguments = $Arguments }
}

function ConvertTo-VerifierArgumentString($Arguments) {
    Assert-VerifierArgumentArray $Arguments
    $quotedArguments = New-Object 'System.Collections.Generic.List[string]'
    foreach ($argument in $Arguments) {
        [void]$quotedArguments.Add((ConvertTo-VerifierWindowsArgument $argument))
    }
    return [string]::Join(' ', $quotedArguments.ToArray())
}

function Start-VerifierProcess($FilePath, $Arguments,
        $RedirectStandardOutput = '', $RedirectStandardError = '') {
    $invocation = Assert-VerifierProcessInvocationBoundary $FilePath $Arguments
    if ($null -ne $RedirectStandardOutput -and
            -not (Test-VerifierStrictStringValue $RedirectStandardOutput)) {
        Throw-VerifierInfrastructure 'Verifier standard-output redirect was not an exact string.'
    }
    if ($null -ne $RedirectStandardError -and
            -not (Test-VerifierStrictStringValue $RedirectStandardError)) {
        Throw-VerifierInfrastructure 'Verifier standard-error redirect was not an exact string.'
    }
    $redirectStandardOutput = if ($null -eq $RedirectStandardOutput) { '' } else {
        $RedirectStandardOutput
    }
    $redirectStandardError = if ($null -eq $RedirectStandardError) { '' } else {
        $RedirectStandardError
    }
    try {
        # Windows PowerShell's Start-Process file-redirection path builds a
        # case-insensitive environment dictionary. A supported caller can
        # inherit both PATH and Path from cmd.exe, which makes that cmdlet
        # reject an otherwise valid launch before the child exists. Keep the
        # no-redirect path unchanged, but use the same direct ProcessStartInfo
        # stream boundary as Invoke-VerifierBoundedProcess when logs are
        # requested. The asynchronous .NET readers preserve the exact
        # Process handle and do not run PowerShell callbacks on thread-pool
        # threads.
        $hasRedirect = (-not [String]::IsNullOrWhiteSpace($redirectStandardOutput) -or
            -not [String]::IsNullOrWhiteSpace($redirectStandardError))
        if ($hasRedirect) {
            $startInfo = New-Object Diagnostics.ProcessStartInfo
            $startInfo.FileName = $invocation.FilePath
            $startInfo.Arguments = ConvertTo-VerifierArgumentString $invocation.Arguments
            $startInfo.UseShellExecute = $false
            $startInfo.CreateNoWindow = $true
            $stdoutTask = $null
            $stderrTask = $null
            if (-not [String]::IsNullOrWhiteSpace($redirectStandardOutput)) {
                # Preserve Start-Process's create/truncate behavior before
                # starting the child, while retaining the caller's exact path.
                [IO.File]::WriteAllText($redirectStandardOutput, '',
                    [Text.UTF8Encoding]::new($false))
                $startInfo.RedirectStandardOutput = $true
            }
            if (-not [String]::IsNullOrWhiteSpace($redirectStandardError)) {
                [IO.File]::WriteAllText($redirectStandardError, '',
                    [Text.UTF8Encoding]::new($false))
                $startInfo.RedirectStandardError = $true
            }
            $process = New-Object Diagnostics.Process
            $process.StartInfo = $startInfo
            if (-not $process.Start()) {
                Throw-VerifierInfrastructure "Could not start verifier process '$FilePath'."
            }
            if ($startInfo.RedirectStandardOutput) {
                $stdoutTask = $process.StandardOutput.ReadToEndAsync()
            }
            if ($startInfo.RedirectStandardError) {
                $stderrTask = $process.StandardError.ReadToEndAsync()
            }
            Add-Member -InputObject $process -MemberType NoteProperty `
                -Name VerifierRedirectedOutputTask -Value $stdoutTask
            Add-Member -InputObject $process -MemberType NoteProperty `
                -Name VerifierRedirectedErrorTask -Value $stderrTask
            Add-Member -InputObject $process -MemberType NoteProperty `
                -Name VerifierRedirectedOutputPath -Value $redirectStandardOutput
            Add-Member -InputObject $process -MemberType NoteProperty `
                -Name VerifierRedirectedErrorPath -Value $redirectStandardError
            Add-Member -InputObject $process -MemberType NoteProperty `
                -Name VerifierRedirectedOutputCaptureComplete -Value $false
            return $process
        }
        $startParameters = @{
            FilePath = $invocation.FilePath
            ArgumentList = ConvertTo-VerifierArgumentString $invocation.Arguments
            PassThru = $true
            WindowStyle = 'Hidden'
            ErrorAction = 'Stop'
        }
        if (-not [String]::IsNullOrWhiteSpace($redirectStandardOutput)) {
            $startParameters.RedirectStandardOutput = $redirectStandardOutput
        }
        if (-not [String]::IsNullOrWhiteSpace($redirectStandardError)) {
            $startParameters.RedirectStandardError = $redirectStandardError
        }
        return (Start-Process @startParameters)
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not start verifier process '$FilePath': " +
            (Get-VerifierErrorMessage $_))
    }
}

function Stop-VerifierBoundedProcessExactly($Process, $ExpectedStartTicks,
        $WaitMilliseconds = 5000) {
    # Keep all bounded-process scalars raw until the exact-type boundary.  A
    # coercive parameter declaration or method call must never turn a string,
    # Boolean, fraction, collection, or null into a usable timeout.
    if (-not (Test-VerifierStrictIntegralValue $ExpectedStartTicks 1 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $WaitMilliseconds 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'A bounded verifier process requires exact positive start identity and wait-bound scalars.'
    }
    if ($null -eq $Process -or $Process.GetType() -ne [Diagnostics.Process] -or
            -not (Test-VerifierStrictIntegralValue $Process.Id 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'A bounded verifier process had no valid process identity.'
    }
    try {
        [void](Stop-VerifierVerifiedProcessExactly $Process $ExpectedStartTicks $WaitMilliseconds)
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not prove exact bounded-process termination for PID " +
            [string]$Process.Id + ': ' + (Get-VerifierErrorMessage $_))
    }
}

function Invoke-VerifierBoundedProcess($FilePath, $Arguments,
        $TimeoutMilliseconds = 5000) {
    $invocation = Assert-VerifierProcessInvocationBoundary $FilePath $Arguments
    # This is the raw caller boundary.  Validate the timeout before creating
    # its log directory or starting the child; PowerShell must not coerce a
    # numeric string, Boolean, fraction, array, object, or null into an int.
    if (-not (Test-VerifierStrictIntegralValue $TimeoutMilliseconds 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'A bounded process timeout must be one exact positive integral scalar.'
    }
    $TimeoutMilliseconds = [int]$TimeoutMilliseconds
    $processRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\verify-process-' + [Guid]::NewGuid().ToString('N'))
    $stdoutLog = Join-Path $processRoot 'stdout.log'
    $stderrLog = Join-Path $processRoot 'stderr.log'
    $process = $null
    $startTicks = 0L
    $terminationProven = $false
    $stdoutTask = $null
    $stderrTask = $null
    try {
        New-Item -ItemType Directory -Path $processRoot -Force -ErrorAction Stop | Out-Null
        # Start-Process with redirected files does not reliably expose ExitCode
        # on Windows PowerShell 5.1. Use ProcessStartInfo pipes directly so the
        # bounded runner has a real process handle, numeric exit, and captured
        # stdout/stderr without relying on a shell-global exit variable.
        $startInfo = New-Object Diagnostics.ProcessStartInfo
        $startInfo.FileName = $invocation.FilePath
        $startInfo.Arguments = ConvertTo-VerifierArgumentString $invocation.Arguments
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $process = New-Object Diagnostics.Process
        $process.StartInfo = $startInfo
        if (-not $process.Start()) {
            Throw-VerifierInfrastructure "Could not start bounded process '$FilePath'."
        }
        $processId = [int]$process.Id
        # Capture the creation identity from this exact launch handle before
        # starting asynchronous readers.  A very short-lived child may exit in
        # the gap, but the retained handle still exposes its positive exact
        # StartTime; no current-PID lookup or identity inference is allowed.
        $startTicks = Get-VerifierLaunchedProcessStartTicks $process
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()

        if (-not $process.WaitForExit($TimeoutMilliseconds)) {
            Stop-VerifierBoundedProcessExactly $process $startTicks
            $terminationProven = $true
            Throw-VerifierInfrastructure "Bounded process '$FilePath' exceeded ${TimeoutMilliseconds}ms; logs retained at '$processRoot'."
        }
        $process.Refresh()
        if (-not [bool]$process.HasExited) {
            Stop-VerifierBoundedProcessExactly $process $startTicks
            $terminationProven = $true
            Throw-VerifierInfrastructure "Bounded process '$FilePath' had an unproven exit after WaitForExit."
        }
        # Give both redirected streams a bounded drain interval before reading
        # them, preventing deadlock while retaining complete diagnostics.
        if (-not $stdoutTask.Wait(5000) -or -not $stderrTask.Wait(5000)) {
            Throw-VerifierInfrastructure "Bounded process '$FilePath' output streams did not close within the bound."
        }
        $process.Refresh()
        if (-not [bool]$process.HasExited) {
            Throw-VerifierInfrastructure "Bounded process '$FilePath' became non-terminal during output drain."
        }
        $stdout = [string]$stdoutTask.Result
        $stderr = [string]$stderrTask.Result
        [IO.File]::WriteAllText($stdoutLog, $stdout, [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($stderrLog, $stderr, [Text.UTF8Encoding]::new($false))
        $rawExitCode = $process.ExitCode
        if (-not (Test-VerifierStrictIntegralValue $rawExitCode `
                ([int]::MinValue) ([int]::MaxValue))) {
            Throw-VerifierInfrastructure "Bounded process '$FilePath' did not expose an exact integral exit code."
        }
        $exitCode = [int]$rawExitCode
        $terminationProven = $true
        $result = [pscustomobject]@{
            FilePath = $FilePath
            Arguments = @($Arguments)
            ProcessId = $processId
            ProcessStartTicks = $startTicks
            ExitCode = $exitCode
            Stdout = $stdout
            Stderr = $stderr
            StdoutLog = $stdoutLog
            StderrLog = $stderrLog
            ProcessRoot = $processRoot
            TerminationProven = $terminationProven
        }
        try {
            $process.Dispose()
            $process = $null
        } catch {
            Throw-VerifierInfrastructure ("Bounded process '$FilePath' handle disposal was not proven: " +
                (Get-VerifierErrorMessage $_))
        }
        try {
            $tempNamespace = Get-VerifierFullPath ([IO.Path]::GetTempPath())
            Assert-VerifierNoReparseAncestors $processRoot
            if (-not (Test-VerifierPhysicalChildPath $tempNamespace $processRoot)) {
                Throw-VerifierInfrastructure 'Bounded-process log root changed physical ownership before deletion.'
            }
            Remove-VerifierOwnedTree $tempNamespace $processRoot
        } catch {
            Throw-VerifierInfrastructure ("Bounded process completed but its owned log root could not be removed: " +
                (Get-VerifierErrorMessage $_) + ". Evidence retained at '$processRoot'.")
        }
        return $result
    } catch {
        $failureRecord = $_
        $failureMessage = Get-VerifierErrorMessage $failureRecord
        $cleanupFailure = $null
        if ($null -ne $process) {
            try {
                $process.Refresh()
                if (-not [bool]$process.HasExited) {
                    if ($startTicks -le 0) {
                        Throw-VerifierInfrastructure "Bounded process '$FilePath' remained alive without a proven start identity."
                    }
                    Stop-VerifierBoundedProcessExactly $process $startTicks
                }
                if (-not $process.WaitForExit(5000)) {
                    Throw-VerifierInfrastructure "Bounded process '$FilePath' did not complete its final WaitForExit."
                }
                $process.Refresh()
                if (-not [bool]$process.HasExited) {
                    Throw-VerifierInfrastructure "Bounded process '$FilePath' remained alive during failure cleanup."
                }
                $currentAfterFailure = Get-VerifierCurrentProcessRecordById $process.Id
                if ($null -ne $currentAfterFailure) {
                    Throw-VerifierInfrastructure "Bounded process '$FilePath' still had a current process identity during failure cleanup."
                }
                if ($stdoutTask -and $stderrTask) {
                    if (-not $stdoutTask.Wait(5000) -or -not $stderrTask.Wait(5000)) {
                        Throw-VerifierInfrastructure "Bounded process '$FilePath' output streams did not close during failure cleanup."
                    }
                    try {
                        [IO.File]::WriteAllText($stdoutLog, [string]$stdoutTask.Result,
                            [Text.UTF8Encoding]::new($false))
                        [IO.File]::WriteAllText($stderrLog, [string]$stderrTask.Result,
                            [Text.UTF8Encoding]::new($false))
                    } catch {
                        Throw-VerifierInfrastructure ("Could not retain bounded-process output for '$FilePath': " +
                            (Get-VerifierErrorMessage $_))
                    }
                }
            } catch {
                $cleanupFailure = Get-VerifierErrorMessage $_
            }
            try {
                $process.Dispose()
                $process = $null
            } catch {
                $disposeFailure = Get-VerifierErrorMessage $_
                if ($cleanupFailure) { $cleanupFailure += '; ' + $disposeFailure }
                else { $cleanupFailure = $disposeFailure }
            }
        }
        if ($cleanupFailure) {
            Throw-VerifierInfrastructure ($failureMessage +
                '; bounded-process cleanup was not proven: ' + $cleanupFailure +
                ". Bounded-process evidence: '$processRoot'.")
        }
        if (Test-VerifierInfrastructureError $failureRecord) {
            if ($stdoutTask -and $stdoutTask.IsCompleted -and
                    $stderrTask -and $stderrTask.IsCompleted) {
                try {
                    [IO.File]::WriteAllText($stdoutLog, [string]$stdoutTask.Result,
                        [Text.UTF8Encoding]::new($false))
                    [IO.File]::WriteAllText($stderrLog, [string]$stderrTask.Result,
                        [Text.UTF8Encoding]::new($false))
                } catch { }
            }
            Throw-VerifierInfrastructure ($failureMessage + " Bounded-process evidence: '$processRoot'.")
        }
        Throw-VerifierInfrastructure ("Bounded process '$FilePath' failed: " +
            $failureMessage + ". Evidence: '$processRoot'.")
    }
}

function Get-VerifierPortMutexName($Context, $Port) {
    if (-not (Test-VerifierStrictIntegralValue $Port 1 65535)) {
        Throw-VerifierInfrastructure 'Port mutex derivation requires an exact integral port in the valid TCP range.'
    }
    # The mutex is intentionally independent of repository/worktree identity.
    # Worktree/run metadata belongs in the claim record; the OS-level claim must
    # serialize the same loopback port across every worktree and process.
    return 'Global\TroubleshootJS.Verifier.Port.' + [string]$Port
}

function Test-VerifierLoopbackAddress([string]$Address) {
    $normalized = ([string]$Address).Trim().Trim('[', ']')
    return $normalized -in @('127.0.0.1', '0.0.0.0', '::1', '::')
}

function Test-VerifierStrictStringValue($Value) {
    return ($null -ne $Value -and $Value.GetType() -eq [string])
}

function Assert-VerifierRawPathValue($Value, [string]$Label = 'path',
        [switch]$AllowEmpty) {
    if (-not (Test-VerifierStrictStringValue $Value) -or
            (-not $AllowEmpty -and [String]::IsNullOrWhiteSpace($Value))) {
        $emptyDetail = if ($AllowEmpty) { '' } else { ' and non-empty' }
        Throw-VerifierInfrastructure "$Label must be an exact$emptyDetail path string before path access."
    }
    return $Value
}

function Get-VerifierFileSha256($Path) {
    if (-not (Test-VerifierStrictStringValue $Path) -or
            [String]::IsNullOrWhiteSpace($Path)) {
        Throw-VerifierInfrastructure 'File hashing requires one exact non-empty path string.'
    }
    $hasher = [Security.Cryptography.SHA256]::Create()
    $stream = $null
    try {
        # Direct ProcessStartInfo launches can inherit a PowerShell 7
        # PSModulePath into Windows PowerShell 5.1. Its bundled
        # Microsoft.PowerShell.Utility module may shadow the native module and
        # omit Get-FileHash. Hash the selected file bytes directly so every
        # verifier provenance path remains cryptographic and independent of the
        # caller's PATH/PSModulePath environment.
        $stream = [IO.File]::OpenRead([string]$Path)
        return [BitConverter]::ToString(
            $hasher.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
        $hasher.Dispose()
    }
}

function Get-VerifierExecutionTreeProvenance($RepositoryRoot) {
    # This digest is deliberately computed by the verifier-side module rather
    # than accepted from the preview.  It binds the source, verifier scripts,
    # and compiled web tree that are actually selected for a route.  A clean
    # wrapper repository state is not sufficient when a disposable execution
    # root is supplied.
    [void](Assert-VerifierRawPathValue $RepositoryRoot 'execution repository root')
    $root = Get-VerifierFullPath $RepositoryRoot
    $categoryDefinitions = @(
        [pscustomobject]@{ Name = 'src'; Path = (Join-Path $root 'src') }
        [pscustomobject]@{ Name = 'scripts'; Path = (Join-Path $root 'scripts') }
        [pscustomobject]@{ Name = 'war'; Path = (Join-Path $root 'war') }
    )
    $allRecords = New-Object Collections.Generic.List[string]
    $categoryResults = [ordered]@{}
    foreach ($category in $categoryDefinitions) {
        $categoryRoot = Get-VerifierFullPath $category.Path
        if (-not (Test-Path -LiteralPath $categoryRoot -PathType Container -ErrorAction Stop)) {
            Throw-VerifierInfrastructure "Execution provenance root '$($category.Name)' was missing."
        }
        [void](Assert-VerifierPhysicalOwnedPath $root $categoryRoot -ValidateTree)
        $records = New-Object Collections.Generic.List[string]
        $filePaths = New-Object Collections.Generic.List[string]
        foreach ($file in @(Get-ChildItem -LiteralPath $categoryRoot -Recurse -File -ErrorAction Stop)) {
            [void]$filePaths.Add([string]$file.FullName)
        }
        # PowerShell's default comparer is culture-sensitive and can produce
        # different provenance bytes between Windows PowerShell and pwsh.  The
        # repository uses case-insensitive Windows paths, so use an explicit
        # ordinal comparer and reject a case-colliding record rather than leave
        # equal keys to an unspecified sort order.
        $filePaths.Sort([StringComparer]::OrdinalIgnoreCase)
        $previousPath = $null
        foreach ($filePath in $filePaths) {
            if ($null -ne $previousPath -and
                    [StringComparer]::OrdinalIgnoreCase.Equals($previousPath, $filePath)) {
                Throw-VerifierInfrastructure "Execution provenance contained duplicate or case-colliding file path '$filePath'."
            }
            $previousPath = $filePath
            $relativePath = $filePath.Substring($categoryRoot.Length).TrimStart('\', '/')
            $fileHash = Get-VerifierFileSha256 $filePath
            [void]$records.Add($relativePath.Replace('\', '/') + '=' + $fileHash)
        }
        $categoryHasher = [Security.Cryptography.SHA256]::Create()
        try {
            $categoryBytes = [Text.UTF8Encoding]::new($false).GetBytes(($records -join "`n"))
            $categoryDigest = [BitConverter]::ToString(
                $categoryHasher.ComputeHash($categoryBytes)).Replace('-', '').ToLowerInvariant()
        } finally {
            $categoryHasher.Dispose()
        }
        $categoryResults[$category.Name] = [pscustomobject]@{
            Root = $categoryRoot
            Digest = $categoryDigest
            FileCount = $records.Count
        }
        foreach ($record in $records) {
            [void]$allRecords.Add($category.Name + '/' + $record)
        }
    }
    $hasher = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($allRecords -join "`n"))
        $digest = [BitConverter]::ToString($hasher.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant()
    } finally {
        $hasher.Dispose()
    }
    return [pscustomobject]@{
        Protocol = 'troubleshootjs-execution-provenance-v1'
        RepositoryRoot = $root
        SourceRoot = $categoryResults['src'].Root
        ScriptRoot = $categoryResults['scripts'].Root
        WebRoot = $categoryResults['war'].Root
        SourceDigest = $categoryResults['src'].Digest
        ScriptDigest = $categoryResults['scripts'].Digest
        WebDigest = $categoryResults['war'].Digest
        Digest = $digest
        FileCount = $allRecords.Count
    }
}

function Complete-VerifierProcessOutputCapture($Process, $WaitMilliseconds = 5000) {
    # Redirected Start-VerifierProcess launches retain their async .NET tasks
    # on the exact Process object. Callers invoke this only after the child is
    # terminal, before reading diagnostics or releasing the owned state.
    if ($null -eq $Process -or $Process.GetType() -ne [Diagnostics.Process]) {
        Throw-VerifierInfrastructure 'Verifier output capture requires the exact Process object returned at launch.'
    }
    if (-not (Test-VerifierStrictIntegralValue $WaitMilliseconds 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'Verifier output capture requires one exact positive wait bound.'
    }
    if (-not $Process.PSObject.Properties['VerifierRedirectedOutputCaptureComplete']) {
        return
    }
    if ([bool]$Process.VerifierRedirectedOutputCaptureComplete) {
        return
    }
    try {
        $Process.Refresh()
        if (-not [bool]$Process.HasExited) {
            Throw-VerifierInfrastructure 'Verifier output capture was requested before the child reached a terminal state.'
        }
        $outputTask = $Process.VerifierRedirectedOutputTask
        $errorTask = $Process.VerifierRedirectedErrorTask
        if ($null -ne $outputTask -and $outputTask -isnot [Threading.Tasks.Task]) {
            Throw-VerifierInfrastructure 'Verifier standard-output capture carried a malformed task.'
        }
        if ($null -ne $errorTask -and $errorTask -isnot [Threading.Tasks.Task]) {
            Throw-VerifierInfrastructure 'Verifier standard-error capture carried a malformed task.'
        }
        if ($null -ne $outputTask -and -not $outputTask.Wait([int]$WaitMilliseconds)) {
            Throw-VerifierInfrastructure 'Verifier standard-output capture did not close within the bound.'
        }
        if ($null -ne $errorTask -and -not $errorTask.Wait([int]$WaitMilliseconds)) {
            Throw-VerifierInfrastructure 'Verifier standard-error capture did not close within the bound.'
        }
        $output = if ($null -eq $outputTask) { '' } else {
            [string]$outputTask.GetAwaiter().GetResult()
        }
        $error = if ($null -eq $errorTask) { '' } else {
            [string]$errorTask.GetAwaiter().GetResult()
        }
        $outputPath = [string]$Process.VerifierRedirectedOutputPath
        $errorPath = [string]$Process.VerifierRedirectedErrorPath
        if (-not [String]::IsNullOrWhiteSpace($outputPath)) {
            [IO.File]::WriteAllText($outputPath, $output, [Text.UTF8Encoding]::new($false))
        }
        if (-not [String]::IsNullOrWhiteSpace($errorPath)) {
            [IO.File]::WriteAllText($errorPath, $error, [Text.UTF8Encoding]::new($false))
        }
        $Process.VerifierRedirectedOutputCaptureComplete = $true
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not complete verifier output capture: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function Test-VerifierBrowserSessionStatus($Value) {
    return ((Test-VerifierStrictStringValue $Value) -and
        ($Value -cin @('leased', 'started', 'attached', 'startup-failed',
            'cleanup-failed', 'cleaned')))
}

function Test-VerifierBrowserSessionCleanupResult($Value) {
    return ((Test-VerifierStrictStringValue $Value) -and
        ($Value -cin @('pending', 'complete', 'infrastructure-failure')))
}

function Test-VerifierServerState($Value) {
    return ((Test-VerifierStrictStringValue $Value) -and
        ($Value -cin @('not-started', 'lease-acquired', 'started-unidentified',
            'starting', 'run-owned-verified', 'startup-failed',
            'caller-verified', 'cleaned', 'cleanup-failed')))
}

function Test-VerifierServerCleanupResult($Value) {
    return ((Test-VerifierStrictStringValue $Value) -and
        ($Value -cin @('not-applicable', 'not-owned', 'pending', 'complete',
            'infrastructure-failure')))
}

function Test-VerifierStrictBooleanValue($Value) {
    return ($null -ne $Value -and $Value.GetType() -eq [bool])
}

function Test-VerifierRawNetTcpListenState($Value) {
    if ($null -eq $Value) { return $false }
    $type = $Value.GetType()
    if ($type -eq [string]) {
        return $Value -cmatch '^(?i:Listen|Listening)$'
    }
    # Get-NetTCPConnection returns the CDXML-generated State enum on supported
    # Windows hosts.  Permit that exact scalar OS type, but do not coerce an
    # arbitrary number/object into a state string.
    if (-not $type.IsEnum -or
            $type.FullName -cne 'Microsoft.PowerShell.Cmdletization.GeneratedTypes.NetTCPConnection.State') {
        return $false
    }
    try {
        return ([string]$Value) -ceq 'Listen'
    } catch { return $false }
}

function Get-VerifierRequiredBooleanValue($Object, [string]$Name,
        [string]$Label) {
    if ($null -eq $Object -or [String]::IsNullOrWhiteSpace($Name) -or
            $null -eq $Object.PSObject.Properties[$Name] -or
            -not (Test-VerifierStrictBooleanValue $Object.PSObject.Properties[$Name].Value)) {
        Throw-VerifierInfrastructure "$Label omitted or malformed its required Boolean '$Name'."
    }
    return $Object.PSObject.Properties[$Name].Value
}

function Test-VerifierStrictIntegralValue($Value, [long]$Minimum = [long]::MinValue,
        [long]$Maximum = [long]::MaxValue) {
    if ($null -eq $Value) { return $false }
    $type = $Value.GetType()
    if ($type -notin @([byte], [sbyte], [int16], [uint16], [int32], [uint32],
            [int64], [uint64])) {
        return $false
    }
    try {
        $number = [long]$Value
        return ($number -ge $Minimum -and $number -le $Maximum)
    } catch { return $false }
}

function ConvertTo-VerifierStrictTimestampText($Value, [bool]$AllowEmpty = $false,
        [string]$Label = 'timestamp', [switch]$AllowJsonDateTime) {
    if ($null -eq $Value) {
        Throw-VerifierInfrastructure "$Label was missing or null."
    }
    # PowerShell 7's integrated JSON reader materializes ISO-8601 values as
    # DateTime.  Normalize only this known timestamp field type; all other
    # durable scalars remain exact strings and are never broadly stringified.
    if ($AllowJsonDateTime -and $Value.GetType() -eq [DateTime]) {
        if ($Value.Kind -ne [DateTimeKind]::Utc) {
            Throw-VerifierInfrastructure "$Label carried a non-UTC DateTime value."
        }
        $Value = $Value.ToString('o', [Globalization.CultureInfo]::InvariantCulture)
    } elseif (-not (Test-VerifierStrictStringValue $Value)) {
        Throw-VerifierInfrastructure "$Label was not an exact timestamp string."
    }
    if ($AllowEmpty -and $Value -ceq '') { return '' }
    if ($Value -cnotmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{7}Z$') {
        Throw-VerifierInfrastructure "$Label carried a malformed strict UTC timestamp."
    }
    $parsed = [DateTime]::MinValue
    if (-not [DateTime]::TryParseExact($Value, 'o',
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind, [ref]$parsed) -or
            $parsed.Kind -ne [DateTimeKind]::Utc) {
        Throw-VerifierInfrastructure "$Label carried an invalid strict UTC timestamp."
    }
    return [string]$Value
}

function ConvertFrom-VerifierDurableJson($Json) {
    if (-not (Test-VerifierStrictStringValue $Json)) {
        Throw-VerifierInfrastructure 'Durable JSON input was not an exact string.'
    }
    $convertCommand = Get-Command ConvertFrom-Json -CommandType Cmdlet -ErrorAction Stop
    if ($convertCommand.Parameters.ContainsKey('DateKind')) {
        return $Json | ConvertFrom-Json -DateKind String
    }
    return $Json | ConvertFrom-Json
}

function Assert-VerifierTimeoutSeconds($Value, [string]$Label = 'timeout seconds') {
    if (-not (Test-VerifierStrictIntegralValue $Value 10L 300L)) {
        Throw-VerifierInfrastructure "$Label must be an exact integral value from 10 through 300 before mutation."
    }
    return [int]$Value
}

function Test-VerifierSupportedPortLeaseKind($Kind) {
    return ((Test-VerifierStrictStringValue $Kind) -and
        ($Kind -ceq 'cdp' -or $Kind -ceq 'preview'))
}

function Assert-VerifierLeaseTransition($Lease,
        [string]$Label = 'verifier lease transition') {
    if ($null -eq $Lease -or $Lease -is [array]) {
        Throw-VerifierInfrastructure "$Label was null or an array before lifecycle validation."
    }
    $stateValues = New-Object Collections.Generic.List[string]
    foreach ($propertyName in @('Status', 'ClaimState', 'ReleaseState',
            'ReleaseJournalState')) {
        $property = $Lease.PSObject.Properties[$propertyName]
        if ($null -eq $property -or
                -not (Test-VerifierStrictStringValue $property.Value)) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its exact lifecycle field '$propertyName'."
        }
        [void]$stateValues.Add([string]$property.Value)
    }
    $transition = [string]::Join('|', $stateValues.ToArray())
    $allowedTransitions = @(
        'leased|held|active|active'
        'bound|bound|active|active'
        'releasing|releasing|releasing|releasing'
        'releasing|os-released|os-released|os-released'
        'releasing|os-released|claim-delete-failed|claim-delete-failed'
        'released|delete-pending|complete|pre-delete'
        'released|delete-pending|complete|post-delete-pending'
        'released|released|complete|complete'
    )
    if ($allowedTransitions -cnotcontains $transition) {
        Throw-VerifierInfrastructure "$Label carried an impossible cross-field lifecycle transition '$transition'."
    }
    return $Lease
}

function Assert-VerifierRawProcessRecord($Record,
        [string]$Label = 'process record', [switch]$RequirePositiveIdentity,
        [switch]$RequireCommandLine) {
    if ($null -eq $Record -or $Record -is [array]) {
        Throw-VerifierInfrastructure "$Label was null or an array before process identity validation."
    }
    foreach ($propertyName in @('ProcessId', 'ParentProcessId')) {
        if ($null -eq $Record.PSObject.Properties[$propertyName]) {
            Throw-VerifierInfrastructure "$Label omitted '$propertyName' before process identity validation."
        }
    }
    $pidProperty = $Record.PSObject.Properties['ProcessId']
    $parentProperty = $Record.PSObject.Properties['ParentProcessId']
    $commandProperty = $Record.PSObject.Properties['CommandLine']
    $pidMinimum = if ($RequirePositiveIdentity) { 1L } else { 0L }
    $parentMinimum = if ($RequirePositiveIdentity) { 1L } else { 0L }
    if (-not (Test-VerifierStrictIntegralValue $pidProperty.Value $pidMinimum ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $parentProperty.Value $parentMinimum ([int]::MaxValue))) {
        Throw-VerifierInfrastructure "$Label carried malformed raw PID or parent PID identity."
    }
    if ($RequireCommandLine -and $null -eq $Record.PSObject.Properties['CommandLine']) {
        Throw-VerifierInfrastructure "$Label omitted command-line identity."
    }
    if ($null -ne $commandProperty -and $null -ne $commandProperty.Value -and
            -not (Test-VerifierStrictStringValue $commandProperty.Value)) {
        Throw-VerifierInfrastructure "$Label carried malformed raw command-line identity."
    }
    if ($RequireCommandLine -and
            [String]::IsNullOrWhiteSpace($commandProperty.Value)) {
        $emptyIdentityMessage = "$Label carried an empty raw command-line identity."
        try {
            # Diagnostics describe captured observations only. A prior PID
            # binding is not exit proof or permission to accept empty identity.
            $rawProcessId = [int]$pidProperty.Value
            $rawParentId = [int]$parentProperty.Value
            $rawPriorBindings = 0
            $rawPriorParentBindings = 0
            foreach ($rawScope in $script:VerifierBrowserDrainScopes) {
                foreach ($rawBinding in $rawScope.Item3) {
                    if ([long]$rawBinding.Item4.Item1 -eq [long]$rawProcessId) {
                        $rawPriorBindings++
                        if ([long]$rawBinding.Item4.Item3 -eq [long]$rawParentId) {
                            $rawPriorParentBindings++
                        }
                    }
                }
            }
            $rawCallers = (@(Get-PSCallStack | Select-Object -First 12 `
                -ExpandProperty FunctionName) -join '>')
            $emptyIdentityMessage += (' [raw-identity pid={0}; parent={1}; ' +
                'activeDrains={2}; priorBindings={3}; priorParentBindings={4}; callers={5}]') -f `
                $rawProcessId, $rawParentId, $script:VerifierBrowserDrainScopes.Count,
                $rawPriorBindings, $rawPriorParentBindings, $rawCallers
        } catch {
            # Missing diagnostic state must preserve the original failure.
            $emptyIdentityMessage = "$Label carried an empty raw command-line identity."
        }
        Throw-VerifierInfrastructure $emptyIdentityMessage
    }
    foreach ($propertyName in @('ProcessStartTicks', 'ParentProcessStartTicks')) {
        $property = $Record.PSObject.Properties[$propertyName]
        if ($null -ne $property -and
                -not (Test-VerifierStrictIntegralValue $property.Value 0 ([long]::MaxValue))) {
            Throw-VerifierInfrastructure "$Label carried malformed raw '$propertyName' identity."
        }
    }
    foreach ($propertyName in @('Name', 'ExecutablePath')) {
        $property = $Record.PSObject.Properties[$propertyName]
        if ($null -ne $property -and $null -ne $property.Value -and
                -not (Test-VerifierStrictStringValue $property.Value)) {
            Throw-VerifierInfrastructure "$Label carried malformed '$propertyName' identity."
        }
    }
    $depthProperty = $Record.PSObject.Properties['VerifierDepth']
    if ($null -ne $depthProperty -and
            -not (Test-VerifierStrictIntegralValue $depthProperty.Value 0 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure "$Label carried malformed ancestry depth."
    }
    return $Record
}

function Assert-VerifierRawProcessId($Record,
        [string]$Label = 'process owner record') {
    if ($null -eq $Record -or $Record -is [array] -or
            $null -eq $Record.PSObject.Properties['ProcessId'] -or
            -not (Test-VerifierStrictIntegralValue $Record.PSObject.Properties['ProcessId'].Value `
                1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure "$Label omitted or carried a malformed positive raw PID."
    }
    return $Record
}

function Assert-VerifierRawProcessIdentityRecord($Record,
        [string]$Label = 'process identity',
        [string]$ParentPropertyName = 'ParentProcessId',
        [string]$StartPropertyName = 'ProcessStartTicks',
        [string]$ParentStartPropertyName = 'ParentProcessStartTicks',
        [string]$CommandPropertyName = 'CommandLine',
        [switch]$RequireParentStart) {
    if ($null -eq $Record -or $Record -is [array]) {
        Throw-VerifierInfrastructure "$Label was null or an array before process identity validation."
    }
    foreach ($propertyName in @('ProcessId', $StartPropertyName,
            $ParentPropertyName, $CommandPropertyName)) {
        if ([String]::IsNullOrWhiteSpace($propertyName) -or
                $null -eq $Record.PSObject.Properties[$propertyName]) {
            Throw-VerifierInfrastructure "$Label omitted '$propertyName' before process identity validation."
        }
    }
    if (-not (Test-VerifierStrictIntegralValue $Record.PSObject.Properties['ProcessId'].Value `
                1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $Record.PSObject.Properties[$StartPropertyName].Value `
                1 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $Record.PSObject.Properties[$ParentPropertyName].Value `
                1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictStringValue $Record.PSObject.Properties[$CommandPropertyName].Value) -or
            [String]::IsNullOrWhiteSpace($Record.PSObject.Properties[$CommandPropertyName].Value)) {
        Throw-VerifierInfrastructure "$Label carried malformed positive PID, parent, start, or command-line identity."
    }
    $parentStartProperty = $Record.PSObject.Properties[$ParentStartPropertyName]
    if ($null -eq $parentStartProperty) {
        if ($RequireParentStart) {
            Throw-VerifierInfrastructure "$Label omitted its required parent start identity."
        }
    } elseif (-not (Test-VerifierStrictIntegralValue $parentStartProperty.Value 0 ([long]::MaxValue)) -or
            ($RequireParentStart -and [long]$parentStartProperty.Value -le 0)) {
        Throw-VerifierInfrastructure "$Label carried malformed parent start identity."
    }
    return $Record
}

function Assert-VerifierDurableProcessIdentityTuple($Record,
        [string]$ProcessIdPropertyName = 'ProcessId',
        [string]$ProcessStartPropertyName = 'ProcessStartTicks',
        [string]$ParentProcessIdPropertyName = 'ParentProcessId',
        [string]$ParentProcessStartPropertyName = 'ParentProcessStartTicks',
        [string]$CommandLinePropertyName = 'CommandLine',
        [string]$Label = 'durable process identity') {
    if ($null -eq $Record -or $Record -is [array]) {
        Throw-VerifierInfrastructure "$Label was null or an array before durable identity validation."
    }
    foreach ($propertyName in @($ProcessIdPropertyName, $ProcessStartPropertyName,
            $ParentProcessIdPropertyName, $ParentProcessStartPropertyName,
            $CommandLinePropertyName)) {
        if ([String]::IsNullOrWhiteSpace($propertyName) -or
                $null -eq $Record.PSObject.Properties[$propertyName]) {
            Throw-VerifierInfrastructure "$Label omitted '$propertyName' before durable identity validation."
        }
    }
    $processId = $Record.PSObject.Properties[$ProcessIdPropertyName].Value
    $processStart = $Record.PSObject.Properties[$ProcessStartPropertyName].Value
    $parentProcessId = $Record.PSObject.Properties[$ParentProcessIdPropertyName].Value
    $parentProcessStart = $Record.PSObject.Properties[$ParentProcessStartPropertyName].Value
    $commandLine = $Record.PSObject.Properties[$CommandLinePropertyName].Value
    if (-not (Test-VerifierStrictIntegralValue $processId 0 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $processStart 0 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $parentProcessId 0 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $parentProcessStart 0 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictStringValue $commandLine)) {
        Throw-VerifierInfrastructure "$Label carried malformed raw PID/start/parent/command identity fields."
    }
    if ([long]$processId -eq 0) {
        if ([long]$processStart -ne 0 -or [long]$parentProcessId -ne 0 -or
                [long]$parentProcessStart -ne 0 -or $commandLine -cne '') {
            Throw-VerifierInfrastructure "$Label carried a mixed identity instead of the exact explicit absent tuple."
        }
        $identityState = 'absent'
    } else {
        if ([long]$processStart -le 0 -or [long]$parentProcessId -le 0 -or
                [long]$parentProcessStart -le 0 -or
                [String]::IsNullOrWhiteSpace($commandLine)) {
            Throw-VerifierInfrastructure "$Label carried an incomplete positive PID/start/parent/command identity tuple."
        }
        $identityState = 'positive'
    }
    return [pscustomobject]@{
        State = $identityState
        ProcessId = [int]$processId
        ProcessStartTicks = [long]$processStart
        ParentProcessId = [int]$parentProcessId
        ParentProcessStartTicks = [long]$parentProcessStart
        CommandLine = [string]$commandLine
    }
}

function Assert-VerifierLiveClaimMutex($Lease, $Context = $null,
        [string]$Label = 'verifier lease') {
    if ($null -eq $Lease) {
        Throw-VerifierInfrastructure "$Label was null before live claim-mutex validation."
    }
    if ($null -ne $Context) {
        [void](Assert-VerifierContextPreflight $Context ($Label + ' context') `
            -RequiredProperties @('RunId'))
    }
    $claimMutexProperty = $Lease.PSObject.Properties['ClaimMutex']
    if ($null -eq $claimMutexProperty) {
        Throw-VerifierInfrastructure "$Label omitted its live ClaimMutex field."
    }

    foreach ($propertyName in @('Status', 'ClaimState', 'ReleaseState',
            'ReleaseJournalState', 'MutexReleased')) {
        if ($null -eq $Lease.PSObject.Properties[$propertyName]) {
            Throw-VerifierInfrastructure "$Label omitted '$propertyName' before live ClaimMutex association validation."
        }
    }
    if (-not (Test-VerifierStrictStringValue $Lease.Status) -or
            -not (Test-VerifierStrictStringValue $Lease.ClaimState) -or
            -not (Test-VerifierStrictStringValue $Lease.ReleaseState) -or
            -not (Test-VerifierStrictStringValue $Lease.ReleaseJournalState) -or
            -not (Test-VerifierStrictBooleanValue $Lease.MutexReleased)) {
        Throw-VerifierInfrastructure "$Label carried malformed lifecycle state before live ClaimMutex association validation."
    }

    foreach ($propertyName in @('ClaimName', 'Port')) {
        if ($null -eq $Lease.PSObject.Properties[$propertyName]) {
            Throw-VerifierInfrastructure "$Label omitted '$propertyName' before live ClaimMutex association validation."
        }
    }
    if (-not (Test-VerifierStrictStringValue $Lease.ClaimName) -or
            [String]::IsNullOrWhiteSpace($Lease.ClaimName) -or
            -not (Test-VerifierStrictIntegralValue $Lease.Port 1 65535)) {
        Throw-VerifierInfrastructure "$Label carried malformed claim identity before live ClaimMutex association validation."
    }
    $claimName = $Lease.ClaimName
    if ($claimName -cne (Get-VerifierPortMutexName $null ([int]$Lease.Port))) {
        Throw-VerifierInfrastructure "$Label ClaimMutex was not associated with the canonical leased-port mutex name."
    }

    $claimMutex = $claimMutexProperty.Value
    if ($null -eq $claimMutex) {
        # A null handle is the only valid post-disposal representation.  It is
        # accepted only after the exact durable terminal/release state has been
        # established; an active lease may never silently lose its mutex.
        if (-not $Lease.MutexReleased -or
                $Lease.Status -notin @('releasing', 'released') -or
                $Lease.ClaimState -notin @('os-released', 'delete-pending', 'released') -or
                $Lease.ReleaseState -notin @('os-released', 'complete', 'claim-delete-failed') -or
                $Lease.ReleaseJournalState -notin @('os-released', 'pre-delete',
                    'post-delete-pending', 'claim-delete-failed', 'complete')) {
            Throw-VerifierInfrastructure "$Label carried a null ClaimMutex outside an exact released lifecycle state."
        }
        return
    }

    if ($claimMutex.GetType() -ne [Threading.Mutex]) {
        Throw-VerifierInfrastructure "$Label carried a malformed live ClaimMutex handle."
    }
    if (-not $script:VerifierHeldPortMutexes.ContainsKey($claimName) -or
            $null -eq $script:VerifierHeldPortMutexes[$claimName] -or
            $script:VerifierHeldPortMutexes[$claimName].GetType() -ne [Threading.Mutex] -or
            -not [object]::ReferenceEquals($script:VerifierHeldPortMutexes[$claimName], $claimMutex)) {
        Throw-VerifierInfrastructure "$Label ClaimMutex was not the exact mutex instance acquired for this claim."
    }
    if ($null -ne $Context) {
        if ($null -eq $Context.PSObject.Properties['RunId'] -or
                -not (Test-VerifierStrictStringValue $Context.RunId) -or
                ($script:VerifierHeldPortClaims.ContainsKey($claimName) -and
                    [string]$script:VerifierHeldPortClaims[$claimName] -cne [string]$Context.RunId) -or
                ((-not $script:VerifierHeldPortClaims.ContainsKey($claimName)) -and
                    (-not $Lease.MutexReleased -or
                     $Lease.Status -notin @('releasing', 'released') -or
                     $Lease.ClaimState -notin @('os-released', 'delete-pending', 'released') -or
                     $Lease.ReleaseState -notin @('os-released', 'complete', 'claim-delete-failed')))) {
            Throw-VerifierInfrastructure "$Label ClaimMutex was not associated with the exact verifier run."
        }
    }
}

function Test-VerifierListenerOwnerTuple($OwnerKind, $OwnerProof,
        $OwnerEvidence, $ProcessId, $ProcessStartTicks, [bool]$AllowNone = $false) {
    if (-not (Test-VerifierStrictStringValue $OwnerKind) -or
            -not (Test-VerifierStrictStringValue $OwnerProof) -or
            -not (Test-VerifierStrictStringValue $OwnerEvidence) -or
            -not (Test-VerifierStrictIntegralValue $ProcessId 0 ([int]::MaxValue))) {
        return $false
    }
    $pid = [long]$ProcessId
    if ($OwnerKind -ceq 'none') {
        return ($AllowNone -and $OwnerProof.Length -eq 0 -and
            $OwnerEvidence.Length -eq 0 -and $pid -eq 0 -and
            (Test-VerifierStrictIntegralValue $ProcessStartTicks 0 0))
    }
    if ($OwnerKind -ceq $script:VerifierUserProcessOwnerKind) {
        return ($pid -gt 0 -and $pid -ne 4 -and
            $OwnerProof -ceq $script:VerifierUserProcessOwnerProof -and
            $OwnerEvidence -ceq $script:VerifierUserProcessOwnerEvidence -and
            (Test-VerifierStrictIntegralValue $ProcessStartTicks 1))
    }
    if ($OwnerKind -ceq $script:VerifierKernelTransportOwnerKind) {
        return ($pid -eq 4 -and $null -eq $ProcessStartTicks -and
            $OwnerProof -ceq $script:VerifierKernelTransportOwnerProof -and
            $OwnerEvidence -ceq $script:VerifierKernelTransportOwnerEvidence)
    }
    return $false
}

function Assert-VerifierDurableListenerOwnerTuple($Lease,
        [string]$Label = 'verifier lease') {
    if ($null -eq $Lease) {
        Throw-VerifierInfrastructure "$Label was null before durable serialization."
    }
    foreach ($propertyName in @('ListenerProcessId', 'ListenerProcessStartTicks',
            'ListenerOwnerKind', 'ListenerOwnerProof', 'ListenerOwnerEvidence')) {
        if ($null -eq $Lease.PSObject.Properties[$propertyName]) {
            Throw-VerifierInfrastructure "$Label omitted required durable listener owner field '$propertyName'."
        }
    }
    $listenerProcessId = $Lease.PSObject.Properties['ListenerProcessId'].Value
    $listenerProcessStartTicks = $Lease.PSObject.Properties['ListenerProcessStartTicks'].Value
    $listenerOwnerKind = $Lease.PSObject.Properties['ListenerOwnerKind'].Value
    $listenerOwnerProof = $Lease.PSObject.Properties['ListenerOwnerProof'].Value
    $listenerOwnerEvidence = $Lease.PSObject.Properties['ListenerOwnerEvidence'].Value
    if (-not (Test-VerifierListenerOwnerTuple $listenerOwnerKind $listenerOwnerProof `
            $listenerOwnerEvidence $listenerProcessId $listenerProcessStartTicks $true)) {
        Throw-VerifierInfrastructure "$Label did not contain an exact canonical durable listener owner tuple."
    }
}

function Assert-VerifierContextPreflight($Context,
        [string]$Label = 'verifier context',
        [string[]]$RequiredProperties = @(), [switch]$RequireDurable) {
    # This is the side-effect-free context boundary.  It deliberately reads
    # only exact in-memory fields: no path canonicalization, OS query,
    # filesystem query, mutation, persistence, or cleanup may occur before it
    # succeeds.  Consumers that need the complete durable graph call their
    # corresponding schema validator after this preflight.
    if ($null -eq $Context -or $Context -is [array] -or
            $Context -isnot [pscustomobject]) {
        Throw-VerifierInfrastructure "$Label was not an exact verifier context object."
    }
    $requiredPropertiesForUse = @('WorktreeRoot') + @($RequiredProperties)
    foreach ($propertyName in @($requiredPropertiesForUse | Select-Object -Unique)) {
        $property = $Context.PSObject.Properties[$propertyName]
        if ($null -eq $property -or
                -not (Test-VerifierStrictStringValue $property.Value)) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its exact string '$propertyName'."
        }
    }
    foreach ($requiredString in @($requiredPropertiesForUse | Select-Object -Unique)) {
        if ([String]::IsNullOrWhiteSpace(
                $Context.PSObject.Properties[$requiredString].Value)) {
            Throw-VerifierInfrastructure "$Label omitted a non-empty run identity string '$requiredString'."
        }
    }

    # Optional fields are still schema-bound when present, so a partial
    # preview-authorization context cannot smuggle a malformed durable field
    # past this boundary. Full manifest consumers opt into the complete set.
    foreach ($propertyName in @('Protocol', 'RunRoot', 'RunNamespaceRoot',
            'EvidenceDirectory', 'EvidenceNamespaceRoot', 'ManifestPath',
            'CreatedUtc', 'BaseUrl', 'CleanupState', 'CleanupCompletedUtc')) {
        $property = $Context.PSObject.Properties[$propertyName]
        if ($null -ne $property -and
                -not (Test-VerifierStrictStringValue $property.Value)) {
            Throw-VerifierInfrastructure "$Label carried a malformed exact string '$propertyName'."
        }
    }
    $createdUtcProperty = $Context.PSObject.Properties['CreatedUtc']
    if ($null -ne $createdUtcProperty) {
        [void](ConvertTo-VerifierStrictTimestampText $createdUtcProperty.Value $false `
            ($Label + ' CreatedUtc'))
    }
    $cleanupCompletedUtcProperty = $Context.PSObject.Properties['CleanupCompletedUtc']
    if ($null -ne $cleanupCompletedUtcProperty) {
        [void](ConvertTo-VerifierStrictTimestampText $cleanupCompletedUtcProperty.Value $true `
            ($Label + ' CleanupCompletedUtc'))
    }
    $cleanupStateProperty = $Context.PSObject.Properties['CleanupState']
    if ($null -ne $cleanupStateProperty) {
        if (-not (Test-VerifierStrictStringValue $cleanupStateProperty.Value) -or
                $cleanupStateProperty.Value -cnotin @('pending', 'complete',
                    'infrastructure-failure')) {
            Throw-VerifierInfrastructure "$Label carried an unknown exact cleanup state."
        }
        if ($null -ne $cleanupCompletedUtcProperty -and
                $cleanupStateProperty.Value -ceq 'pending' -and
                $cleanupCompletedUtcProperty.Value -cne '') {
            Throw-VerifierInfrastructure "$Label carried a completion timestamp for a pending cleanup state."
        }
        if ($cleanupStateProperty.Value -ceq 'complete') {
            if ($null -eq $cleanupCompletedUtcProperty) {
                Throw-VerifierInfrastructure "$Label marked cleanup complete without a completion timestamp."
            }
            [void](ConvertTo-VerifierStrictTimestampText $cleanupCompletedUtcProperty.Value $false `
                ($Label + ' CleanupCompletedUtc'))
        }
    }
    foreach ($collectionName in @('LeaseRecords', 'BrowserSessions',
            'Artifacts', 'CleanupErrors')) {
        $property = $Context.PSObject.Properties[$collectionName]
        if ($null -ne $property -and ($null -eq $property.Value -or
                $property.Value -is [string] -or
                -not ($property.Value -is [System.Collections.IEnumerable]))) {
            Throw-VerifierInfrastructure "$Label carried a malformed collection '$collectionName'."
        }
    }
    if ($RequireDurable) {
        $protocolProperty = $Context.PSObject.Properties['Protocol']
        if ($null -eq $protocolProperty -or
                -not (Test-VerifierStrictStringValue $protocolProperty.Value) -or
                $protocolProperty.Value -cne 'troubleshootjs-verifier-run-v1') {
            Throw-VerifierInfrastructure "$Label carried an unknown durable protocol."
        }
        foreach ($requiredString in @('RunRoot', 'RunNamespaceRoot',
                'EvidenceDirectory', 'EvidenceNamespaceRoot', 'ManifestPath',
                'CreatedUtc', 'CleanupState')) {
            $property = $Context.PSObject.Properties[$requiredString]
            if ($null -eq $property -or
                -not (Test-VerifierStrictStringValue $property.Value) -or
                [String]::IsNullOrWhiteSpace($property.Value)) {
                Throw-VerifierInfrastructure "$Label omitted or malformed its durable string '$requiredString'."
            }
        }
        if ($null -eq $Context.PSObject.Properties['CleanupCompletedUtc'] -or
                -not (Test-VerifierStrictStringValue $Context.CleanupCompletedUtc)) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its durable completion timestamp."
        }
        foreach ($collectionName in @('LeaseRecords', 'BrowserSessions',
                'Artifacts', 'CleanupErrors')) {
            $property = $Context.PSObject.Properties[$collectionName]
            if ($null -eq $property -or $null -eq $property.Value -or
                    $property.Value -is [string] -or
                    -not ($property.Value -is [System.Collections.IEnumerable])) {
                Throw-VerifierInfrastructure "$Label omitted or malformed collection '$collectionName'."
            }
        }
        if ($null -eq $Context.PSObject.Properties['Server']) {
            Throw-VerifierInfrastructure "$Label omitted its explicit Server owner state."
        }
    }
    return $Context
}

function Assert-VerifierDurableLeaseRecord($Lease, $Context,
        [string]$Label = 'verifier lease record', [switch]$Serialized) {
    if ($null -eq $Lease) {
        Throw-VerifierInfrastructure "$Label was null before durable validation."
    }
    if ($null -ne $Context) {
        [void](Assert-VerifierContextPreflight $Context ($Label + ' context') `
            -RequiredProperties @('RunId', 'RepositoryIdentity'))
    }
    $timestampProperties = @('BindValidatedUtc', 'ReleasedUtc', 'ListenerInspectionUtc')
    foreach ($propertyName in @(
            'LeaseId', 'Kind', 'Path', 'RunId', 'RepositoryIdentity',
            'WorktreeRoot', 'Status', 'ClaimName', 'ClaimState',
            'ProfilePath', 'BrowserPath', 'BindValidatedUtc', 'ReleasedUtc',
            'ReleaseState', 'ReleaseJournalState', 'ReleaseBlockReason',
            'ListenerInspectionUtc')) {
        $property = $Lease.PSObject.Properties[$propertyName]
        if ($null -eq $property) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its exact string '$propertyName'."
        }
        if ($propertyName -in $timestampProperties) {
            [void](ConvertTo-VerifierStrictTimestampText $property.Value $true `
                ($Label + " $propertyName"))
        } elseif (-not (Test-VerifierStrictStringValue $property.Value)) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its exact string '$propertyName'."
        }
    }
    if (-not (Test-VerifierSupportedPortLeaseKind $Lease.Kind)) {
        Throw-VerifierInfrastructure "$Label carried an unsupported finite lease kind."
    }
    foreach ($numericProperty in @(
            [pscustomobject]@{ Name = 'Port'; Minimum = 1L; Maximum = 65535L }
            [pscustomobject]@{ Name = 'ClaimOwnerPid'; Minimum = 1L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'ClaimOwnerStartTicks'; Minimum = 1L; Maximum = [long]::MaxValue }
            [pscustomobject]@{ Name = 'BoundProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'BoundProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
            [pscustomobject]@{ Name = 'ListenerProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
        )) {
        if ($null -eq $Lease.PSObject.Properties[$numericProperty.Name] -or
                -not (Test-VerifierStrictIntegralValue $Lease.PSObject.Properties[$numericProperty.Name].Value `
                    $numericProperty.Minimum $numericProperty.Maximum)) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its exact integral '$($numericProperty.Name)'."
        }
    }
    foreach ($booleanProperty in @(
            'Registered', 'ReleaseBlocked', 'MutexReleased',
            'ListenerInspectionSuccess', 'ListenerInspectionKnown',
            'ProcessProofRequired', 'ProcessTerminationProven', 'ProcessAbsent')) {
        [void](Get-VerifierRequiredBooleanValue $Lease $booleanProperty $Label)
    }
    # The live handle is part of the complete lease preflight even though it is
    # deliberately omitted from durable JSON.  Run this before any context
    # association/path work so arbitrary strings or mutexes cannot reach a
    # listener, process, or cleanup consumer.  A serialized snapshot uses a
    # canonical null handle after the raw JSON fields have been validated.
    if ($Serialized) {
        $claimMutexProperty = $Lease.PSObject.Properties['ClaimMutex']
        if ($null -eq $claimMutexProperty -or $null -ne $claimMutexProperty.Value) {
            Throw-VerifierInfrastructure "$Label serialized lease did not carry the exact absent ClaimMutex representation."
        }
    } else {
        Assert-VerifierLiveClaimMutex $Lease $Context $Label
    }
    foreach ($nullableBooleanProperty in @('ListenerHasListeners', 'ListenerAbsent')) {
        $property = $Lease.PSObject.Properties[$nullableBooleanProperty]
        if ($null -eq $property -or ($null -ne $property.Value -and
                -not (Test-VerifierStrictBooleanValue $property.Value))) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its nullable Boolean '$nullableBooleanProperty'."
        }
    }
    $listenerStartProperty = $Lease.PSObject.Properties['ListenerProcessStartTicks']
    if ($null -eq $listenerStartProperty -or ($null -ne $listenerStartProperty.Value -and
            -not (Test-VerifierStrictIntegralValue $listenerStartProperty.Value 0))) {
        Throw-VerifierInfrastructure "$Label omitted or malformed its listener start identity."
    }
    # Validate the complete finite owner tuple before any branch reads its
    # fields.  Missing owner fields must fail through the typed infrastructure
    # boundary rather than through PowerShell property access.
    Assert-VerifierDurableListenerOwnerTuple $Lease $Label
    $hasListeners = $Lease.PSObject.Properties['ListenerHasListeners'].Value
    $listenerAbsent = $Lease.PSObject.Properties['ListenerAbsent'].Value
    if (($null -eq $hasListeners) -xor ($null -eq $listenerAbsent) -or
            ($null -ne $hasListeners -and $listenerAbsent -eq $hasListeners)) {
        Throw-VerifierInfrastructure "$Label carried inconsistent listener presence flags."
    }
    $inspectionSuccess = $Lease.ListenerInspectionSuccess
    $inspectionKnown = $Lease.ListenerInspectionKnown
    if ($inspectionSuccess -ne $inspectionKnown) {
        Throw-VerifierInfrastructure "$Label carried mismatched listener inspection success/known flags."
    }
    if ($null -eq $hasListeners) {
        # A newly acquired lease has not been inspected yet. It is an explicit
        # unbound/none state, never inferred absence and never a retained
        # positive tuple.
        if ($inspectionSuccess -or $inspectionKnown -or
                $Lease.Status -cne 'leased' -or $Lease.ClaimState -cne 'held' -or
                $Lease.ListenerOwnerKind -cne 'none' -or
                $Lease.ListenerProcessId -ne 0 -or
                ($null -ne $Lease.ListenerProcessStartTicks -and
                    $Lease.ListenerProcessStartTicks -ne 0)) {
            Throw-VerifierInfrastructure "$Label used an uninspected listener state that was not the exact new-lease none tuple."
        }
    } else {
        if (-not $inspectionSuccess -or -not $inspectionKnown) {
            Throw-VerifierInfrastructure "$Label carried listener presence flags without a positively known inspection."
        }
        if ($hasListeners -and $Lease.ListenerOwnerKind -ceq 'none') {
            Throw-VerifierInfrastructure "$Label claimed a positive listener observation with the none owner tuple."
        }
    }
    if ($Lease.Status -cnotin @('leased', 'bound', 'releasing', 'released') -or
            $Lease.ClaimState -cnotin @('held', 'bound', 'releasing', 'os-released',
                'delete-pending', 'released') -or
            $Lease.ReleaseState -cnotin @('active', 'releasing', 'os-released',
                'complete', 'claim-delete-failed') -or
            $Lease.ReleaseJournalState -cnotin @('active', 'releasing', 'os-released',
                'pre-delete', 'post-delete-pending', 'claim-delete-failed', 'complete')) {
        Throw-VerifierInfrastructure "$Label carried an unknown durable lifecycle state."
    }
    [void](Assert-VerifierLeaseTransition $Lease ($Label + ' lifecycle'))
    if ($null -ne $Context) {
        if ($null -eq $Context.PSObject.Properties['RunId'] -or
                -not (Test-VerifierStrictStringValue $Context.RunId) -or
                $Lease.RunId -cne $Context.RunId -or
                $null -eq $Context.PSObject.Properties['RepositoryIdentity'] -or
                -not (Test-VerifierStrictStringValue $Context.RepositoryIdentity) -or
                $Lease.RepositoryIdentity -cne $Context.RepositoryIdentity -or
                $null -eq $Context.PSObject.Properties['WorktreeRoot'] -or
                -not (Test-VerifierCanonicalWindowsPathValue $Lease.WorktreeRoot `
                    $Context.WorktreeRoot)) {
            Throw-VerifierInfrastructure "$Label did not match the exact verifier run identity."
        }
    }
}

function Assert-VerifierDurableListenerOwnerTuples($Context) {
    # Preserve the historical entry point while routing it through the single
    # complete durable-context validator used by manifest projection/writes.
    Assert-VerifierDurableManifestContext $Context
}

function Assert-VerifierDurableLeaseTerminalFields($Lease, $Context = $null,
        [string]$Label = 'verifier terminal lease') {
    # This is the one terminal-state predicate for both live cleanup and
    # serialized integrated-child readers.  It deliberately performs only
    # exact in-memory validation; claim absence is proved by the caller after
    # this boundary succeeds.
    Assert-VerifierDurableLeaseRecord $Lease $Context $Label
    foreach ($propertyName in @('LeaseId', 'Kind', 'Path', 'ClaimName')) {
        $property = $Lease.PSObject.Properties[$propertyName]
        if ($null -eq $property -or
                -not (Test-VerifierStrictStringValue $property.Value) -or
                [String]::IsNullOrWhiteSpace($property.Value)) {
            Throw-VerifierInfrastructure "$Label omitted its non-empty exact claim association '$propertyName'."
        }
    }
    if (-not (Test-VerifierSupportedPortLeaseKind $Lease.Kind) -or
            $Lease.ClaimName -cne (Get-VerifierPortMutexName $null ([int]$Lease.Port))) {
        Throw-VerifierInfrastructure "$Label did not carry a finite lease kind and canonical claim association."
    }
    foreach ($propertyName in @('Registered', 'ReleaseBlocked', 'MutexReleased',
            'ListenerInspectionSuccess', 'ListenerInspectionKnown',
            'ProcessProofRequired', 'ProcessTerminationProven', 'ProcessAbsent')) {
        [void](Get-VerifierRequiredBooleanValue $Lease $propertyName $Label)
    }
    if (-not $Lease.Registered -or $Lease.ReleaseBlocked -or
            -not $Lease.MutexReleased -or
            $Lease.Status -cne 'released' -or
            $Lease.ClaimState -cne 'released' -or
            $Lease.ReleaseState -cne 'complete' -or
            $Lease.ReleaseJournalState -cne 'complete' -or
            -not $Lease.ListenerInspectionSuccess -or
            -not $Lease.ListenerInspectionKnown -or
            $null -eq $Lease.ListenerHasListeners -or
            -not (Test-VerifierStrictBooleanValue $Lease.ListenerHasListeners) -or
            $Lease.ListenerHasListeners -or
            $null -eq $Lease.ListenerAbsent -or
            -not (Test-VerifierStrictBooleanValue $Lease.ListenerAbsent) -or
            -not $Lease.ListenerAbsent -or
            $Lease.ReleaseBlockReason -cne '') {
        Throw-VerifierInfrastructure "$Label did not carry the exact released lifecycle and listener-absence proof."
    }
    $claimMutexProperty = $Lease.PSObject.Properties['ClaimMutex']
    if ($null -eq $claimMutexProperty -or $null -ne $claimMutexProperty.Value) {
        Throw-VerifierInfrastructure "$Label retained a live or missing ClaimMutex after release."
    }
    $requiresProcessProof = $Lease.ProcessProofRequired -or
        [int]$Lease.BoundProcessId -gt 0 -or
        [int]$Lease.ListenerProcessId -gt 0
    if ($Lease.ProcessProofRequired -and
            ([int]$Lease.BoundProcessId -le 0 -or
             [int]$Lease.ListenerProcessId -le 0)) {
        Throw-VerifierInfrastructure "$Label required process proof without both exact positive process identities."
    }
    if (($Lease.BoundProcessId -gt 0 -or $Lease.ListenerProcessId -gt 0) -and
            -not $Lease.ProcessProofRequired) {
        Throw-VerifierInfrastructure "$Label carried a positive process/listener identity without ProcessProofRequired."
    }
    if ($requiresProcessProof) {
        if (-not $Lease.ProcessTerminationProven -or -not $Lease.ProcessAbsent) {
            Throw-VerifierInfrastructure "$Label did not retain positive process termination and absence proof."
        }
    } elseif ($Lease.ProcessTerminationProven -or $Lease.ProcessAbsent) {
        Throw-VerifierInfrastructure "$Label carried process proof for an explicitly absent process identity."
    }
    return $Lease
}

function Set-VerifierLeaseProcessProofFromCleanup($Lease,
        $ProcessTerminationProven) {
    # Root/session cleanup proof and lease-level process proof are distinct.
    # Validate every field before mutation so an invalid cleanup result cannot
    # manufacture a terminal lease proof or erase a required bound identity.
    if ($null -eq $Lease) {
        Throw-VerifierInfrastructure 'Lease process proof propagation received a null lease.'
    }
    foreach ($propertyName in @('ProcessProofRequired',
            'ProcessTerminationProven', 'ProcessAbsent')) {
        $property = $Lease.PSObject.Properties[$propertyName]
        if ($null -eq $property -or
                -not (Test-VerifierStrictBooleanValue $property.Value)) {
            Throw-VerifierInfrastructure "Lease process proof propagation received a missing or malformed Boolean '$propertyName'."
        }
    }
    if (-not (Test-VerifierStrictBooleanValue $ProcessTerminationProven)) {
        Throw-VerifierInfrastructure 'Lease process proof propagation received a missing or malformed cleanup proof.'
    }
    if ($Lease.ProcessProofRequired) {
        $Lease.ProcessTerminationProven = [bool]$ProcessTerminationProven
        $Lease.ProcessAbsent = [bool]$ProcessTerminationProven
    } else {
        $Lease.ProcessTerminationProven = $false
        $Lease.ProcessAbsent = $false
    }
    return $Lease
}

function Assert-VerifierDurableLeaseTerminal($Lease, $Context = $null,
        [string]$Label = 'verifier terminal lease') {
    [void](Assert-VerifierDurableLeaseTerminalFields $Lease $Context $Label)
    $claimPath = [string]$Lease.Path
    try {
        if ($null -ne $Context -and $Context.PSObject.Properties['PortLeaseRoot']) {
            [void](Assert-VerifierPhysicalOwnedPath $Context.PortLeaseRoot $claimPath)
        } else {
            Assert-VerifierNoReparseAncestors $claimPath
        }
        if (Test-Path -LiteralPath $claimPath -PathType Leaf -ErrorAction Stop) {
            Throw-VerifierInfrastructure "$Label retained its exact claim path '$claimPath'."
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure "$Label claim absence was not positively proven: $(Get-VerifierErrorMessage $_)"
    }
    return $Lease
}

function Test-VerifierDurableLeaseTerminal($Lease, $Context = $null,
        [string]$Label = 'verifier terminal lease') {
    try {
        [void](Assert-VerifierDurableLeaseTerminal $Lease $Context $Label)
        return $true
    } catch {
        $message = Get-VerifierErrorMessage $_
        if ($message -match 'claim absence was not positively proven|retained its exact claim path|Could not inspect|Could not find an existing ancestor|physical namespace|reparse') {
            throw
        }
        return $false
    }
}

function Assert-VerifierSerializedObject($Object, [string]$Label) {
    if ($null -eq $Object -or $Object -is [array] -or
            $Object -isnot [pscustomobject]) {
        Throw-VerifierInfrastructure "$Label was not an exact serialized verifier object."
    }
}

function Assert-VerifierSerializedString($Object, [string]$Name,
        [string]$Label, [bool]$AllowEmpty = $false) {
    $property = if ($null -eq $Object) { $null } else {
        $Object.PSObject.Properties[$Name]
    }
    if ($null -eq $property -or
            -not (Test-VerifierStrictStringValue $property.Value) -or
            (-not $AllowEmpty -and [String]::IsNullOrWhiteSpace($property.Value))) {
        Throw-VerifierInfrastructure "$Label omitted or malformed serialized string '$Name'."
    }
    return $property.Value
}

function Assert-VerifierSerializedIntegral($Object, [string]$Name,
        [long]$Minimum, [long]$Maximum, [string]$Label) {
    $property = if ($null -eq $Object) { $null } else {
        $Object.PSObject.Properties[$Name]
    }
    if ($null -eq $property -or
            -not (Test-VerifierStrictIntegralValue $property.Value $Minimum $Maximum)) {
        Throw-VerifierInfrastructure "$Label omitted or malformed serialized integral '$Name'."
    }
    return $property.Value
}

function Assert-VerifierSerializedBoolean($Object, [string]$Name,
        [string]$Label, [switch]$AllowNull) {
    $property = if ($null -eq $Object) { $null } else {
        $Object.PSObject.Properties[$Name]
    }
    if ($null -eq $property) {
        Throw-VerifierInfrastructure "$Label omitted serialized Boolean '$Name'."
    }
    if ($null -eq $property.Value) {
        if ($AllowNull) { return $null }
        Throw-VerifierInfrastructure "$Label carried null serialized Boolean '$Name'."
    }
    if (-not (Test-VerifierStrictBooleanValue $property.Value)) {
        Throw-VerifierInfrastructure "$Label carried malformed serialized Boolean '$Name'."
    }
    return $property.Value
}

function ConvertFrom-VerifierSerializedLeaseRecord($Lease,
        [string]$Label = 'serialized verifier lease') {
    Assert-VerifierSerializedObject $Lease $Label
    $timestampNames = @('bindValidatedUtc', 'releasedUtc', 'listenerInspectionUtc')
    foreach ($name in @('leaseId', 'kind', 'path', 'runId',
            'repositoryIdentity', 'worktreeRoot', 'claimName', 'status',
            'claimState', 'releaseState', 'releaseJournalState', 'profile',
            'browserPath', 'releaseBlockReason')) {
        $allowEmpty = $name -in @('profile', 'browserPath', 'releaseBlockReason')
        [void](Assert-VerifierSerializedString $Lease $name $Label -AllowEmpty $allowEmpty)
    }
    foreach ($name in $timestampNames) {
        $property = $Lease.PSObject.Properties[$name]
        if ($null -eq $property) {
            Throw-VerifierInfrastructure "$Label omitted serialized timestamp '$name'."
        }
        [void](ConvertTo-VerifierStrictTimestampText $property.Value $true `
            ($Label + ' ' + $name) -AllowJsonDateTime)
    }
    if (-not (Test-VerifierSupportedPortLeaseKind $Lease.PSObject.Properties['kind'].Value)) {
        Throw-VerifierInfrastructure "$Label carried an unsupported finite lease kind."
    }
    foreach ($number in @(
            [pscustomobject]@{ Name = 'port'; Minimum = 1L; Maximum = 65535L }
            [pscustomobject]@{ Name = 'claimOwnerPid'; Minimum = 1L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'claimOwnerStartTicks'; Minimum = 1L; Maximum = [long]::MaxValue }
            [pscustomobject]@{ Name = 'boundProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'boundProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
            [pscustomobject]@{ Name = 'listenerProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
        )) {
        [void](Assert-VerifierSerializedIntegral $Lease $number.Name $number.Minimum $number.Maximum $Label)
    }
    $listenerStart = $Lease.PSObject.Properties['listenerProcessStartTicks']
    if ($null -eq $listenerStart -or ($null -ne $listenerStart.Value -and
            -not (Test-VerifierStrictIntegralValue $listenerStart.Value 0 ([long]::MaxValue)))) {
        Throw-VerifierInfrastructure "$Label omitted or malformed serialized listenerProcessStartTicks."
    }
    foreach ($name in @('registered', 'releaseBlocked', 'mutexReleased',
            'listenerInspectionSuccess', 'listenerInspectionKnown',
            'processProofRequired', 'processTerminationProven', 'processAbsent')) {
        [void](Assert-VerifierSerializedBoolean $Lease $name $Label)
    }
    foreach ($name in @('listenerHasListeners', 'listenerAbsent')) {
        [void](Assert-VerifierSerializedBoolean $Lease $name $Label -AllowNull)
    }
    foreach ($name in @('listenerOwnerKind', 'listenerOwnerProof', 'listenerOwnerEvidence')) {
        [void](Assert-VerifierSerializedString $Lease $name $Label -AllowEmpty $true)
    }
    if (-not (Test-VerifierListenerOwnerTuple $Lease.PSObject.Properties['listenerOwnerKind'].Value $Lease.PSObject.Properties['listenerOwnerProof'].Value $Lease.PSObject.Properties['listenerOwnerEvidence'].Value $Lease.PSObject.Properties['listenerProcessId'].Value $Lease.PSObject.Properties['listenerProcessStartTicks'].Value $true)) {
        Throw-VerifierInfrastructure "$Label carried a noncanonical serialized listener owner tuple."
    }
    $claimProperty = $Lease.PSObject.Properties['claim']
    if ($null -eq $claimProperty -or $null -eq $claimProperty.Value) {
        Throw-VerifierInfrastructure "$Label omitted its exact serialized claim descriptor."
    }
    $claim = $claimProperty.Value
    Assert-VerifierSerializedObject $claim ($Label + ' claim')
    foreach ($name in @('protocol', 'runId', 'repositoryIdentity',
            'worktreeRoot', 'leaseId', 'path', 'kind', 'mutexName')) {
        [void](Assert-VerifierSerializedString $claim $name ($Label + ' claim'))
    }
    foreach ($number in @(
            [pscustomobject]@{ Name = 'port'; Minimum = 1L; Maximum = 65535L }
            [pscustomobject]@{ Name = 'ownerPid'; Minimum = 1L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'ownerStartTicks'; Minimum = 1L; Maximum = [long]::MaxValue }
        )) {
        [void](Assert-VerifierSerializedIntegral $claim $number.Name $number.Minimum $number.Maximum ($Label + ' claim'))
    }

    # Every raw scalar above is exact before any typed projection or
    # association comparison is performed.
    $leasePort = $Lease.PSObject.Properties['port'].Value
    $claimPort = $claim.PSObject.Properties['port'].Value
    foreach ($pair in @(
            [pscustomobject]@{ Lease = 'leaseId'; Claim = 'leaseId' }
            [pscustomobject]@{ Lease = 'runId'; Claim = 'runId' }
            [pscustomobject]@{ Lease = 'repositoryIdentity'; Claim = 'repositoryIdentity' }
            [pscustomobject]@{ Lease = 'worktreeRoot'; Claim = 'worktreeRoot' }
            [pscustomobject]@{ Lease = 'path'; Claim = 'path' }
            [pscustomobject]@{ Lease = 'kind'; Claim = 'kind' }
            [pscustomobject]@{ Lease = 'claimName'; Claim = 'mutexName' }
        )) {
        if ($Lease.PSObject.Properties[$pair.Lease].Value -cne
                $claim.PSObject.Properties[$pair.Claim].Value) {
            Throw-VerifierInfrastructure "$Label and its claim descriptor disagreed on '$($pair.Lease)'."
        }
    }
    if ($claim.protocol -cne 'troubleshootjs-verifier-port-claim-v1' -or
            -not (Test-VerifierSupportedPortLeaseKind $Lease.kind) -or
            [int]$claimPort -ne [int]$leasePort -or
            $claim.mutexName -cne (Get-VerifierPortMutexName $null ([int]$leasePort)) -or
            [long]$claim.ownerPid -ne [long]$Lease.claimOwnerPid -or
            [long]$claim.ownerStartTicks -ne [long]$Lease.claimOwnerStartTicks) {
        Throw-VerifierInfrastructure "$Label carried a mismatched or unsupported serialized claim association."
    }
    $canonical = [pscustomobject]@{
        LeaseId = [string]$Lease.leaseId; Kind = [string]$Lease.kind
        Port = [int]$Lease.port; Path = [string]$Lease.path
        RunId = [string]$Lease.runId
        RepositoryIdentity = [string]$Lease.repositoryIdentity
        WorktreeRoot = [string]$Lease.worktreeRoot
        Status = [string]$Lease.status; Registered = [bool]$Lease.registered
        ClaimName = [string]$Lease.claimName; ClaimState = [string]$Lease.claimState
        ClaimOwnerPid = [int]$Lease.claimOwnerPid
        ClaimOwnerStartTicks = [long]$Lease.claimOwnerStartTicks
        BoundProcessId = [int]$Lease.boundProcessId
        BoundProcessStartTicks = [long]$Lease.boundProcessStartTicks
        ListenerProcessId = [int]$Lease.listenerProcessId
        ListenerProcessStartTicks = $Lease.listenerProcessStartTicks
        ListenerOwnerKind = [string]$Lease.listenerOwnerKind
        ListenerOwnerProof = [string]$Lease.listenerOwnerProof
        ListenerOwnerEvidence = [string]$Lease.listenerOwnerEvidence
        BindValidatedUtc = ConvertTo-VerifierStrictTimestampText $Lease.bindValidatedUtc $true `
            ($Label + ' bindValidatedUtc') -AllowJsonDateTime
        ReleasedUtc = ConvertTo-VerifierStrictTimestampText $Lease.releasedUtc $true `
            ($Label + ' releasedUtc') -AllowJsonDateTime
        ReleaseState = [string]$Lease.releaseState
        ReleaseJournalState = [string]$Lease.releaseJournalState
        ReleaseBlocked = [bool]$Lease.releaseBlocked
        ReleaseBlockReason = [string]$Lease.releaseBlockReason
        MutexReleased = [bool]$Lease.mutexReleased
        ListenerInspectionSuccess = [bool]$Lease.listenerInspectionSuccess
        ListenerInspectionKnown = [bool]$Lease.listenerInspectionKnown
        ListenerHasListeners = $Lease.listenerHasListeners
        ListenerAbsent = $Lease.listenerAbsent
        ListenerInspectionUtc = ConvertTo-VerifierStrictTimestampText $Lease.listenerInspectionUtc $true `
            ($Label + ' listenerInspectionUtc') -AllowJsonDateTime
        ProcessProofRequired = [bool]$Lease.processProofRequired
        ProcessTerminationProven = [bool]$Lease.processTerminationProven
        ProcessAbsent = [bool]$Lease.processAbsent
        ProfilePath = [string]$Lease.profile
        BrowserPath = [string]$Lease.browserPath
        ClaimMutex = $null
    }
    [void](Assert-VerifierDurableLeaseRecord $canonical $null $Label -Serialized)
    return $canonical
}

function Assert-VerifierSerializedLeaseRecord($Lease,
        [string]$Label = 'serialized verifier lease') {
    [void](ConvertFrom-VerifierSerializedLeaseRecord $Lease $Label)
}

function Assert-VerifierSerializedBrowserSessionRecord($Session,
        [string]$Label = 'serialized verifier browser session') {
    Assert-VerifierSerializedObject $Session $Label
    foreach ($name in @('owner', 'runId', 'repositoryIdentity',
            'worktreeRoot', 'routeId', 'routeName', 'profile', 'cdpLeasePath',
            'browserPath', 'processCommandLine', 'targetId', 'expectedUrl',
            'expectedRunMarker', 'expectedRouteMarker', 'status',
            'cleanupResult', 'error')) {
        $allowEmpty = $name -in @('processCommandLine', 'targetId', 'expectedUrl', 'error')
        [void](Assert-VerifierSerializedString $Session $name $Label -AllowEmpty $allowEmpty)
    }
    if (-not (Test-VerifierBrowserSessionStatus $Session.status) -or
            -not (Test-VerifierBrowserSessionCleanupResult $Session.cleanupResult)) {
        Throw-VerifierInfrastructure "$Label carried an unknown finite lifecycle status or cleanup result."
    }
    if (-not (Test-VerifierStrictStringValue $Session.routeName) -or
            [String]::IsNullOrWhiteSpace($Session.routeName)) {
        Throw-VerifierInfrastructure "$Label omitted a non-empty exact route name."
    }
    foreach ($number in @(
            [pscustomobject]@{ Name = 'cdpPort'; Minimum = 1L; Maximum = 65535L }
            [pscustomobject]@{ Name = 'processId'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'processStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
            [pscustomobject]@{ Name = 'processParentProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'processParentProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
        )) {
        [void](Assert-VerifierSerializedIntegral $Session $number.Name $number.Minimum $number.Maximum $Label)
    }
    foreach ($name in @('profileProcessScanCompleted', 'profileInspectionFailed')) {
        [void](Assert-VerifierSerializedBoolean $Session $name $Label)
    }
    $canonicalIdentity = [pscustomobject]@{
        ProcessId = $Session.processId
        ProcessStartTicks = $Session.processStartTicks
        ProcessParentProcessId = $Session.processParentProcessId
        ProcessParentProcessStartTicks = $Session.processParentProcessStartTicks
        ProcessCommandLine = $Session.processCommandLine
    }
    [void](Assert-VerifierDurableProcessIdentityTuple $canonicalIdentity `
        -ProcessIdPropertyName 'ProcessId' `
        -ProcessStartPropertyName 'ProcessStartTicks' `
        -ParentProcessIdPropertyName 'ProcessParentProcessId' `
        -ParentProcessStartPropertyName 'ProcessParentProcessStartTicks' `
        -CommandLinePropertyName 'ProcessCommandLine' `
        -Label ($Label + ' process identity'))
    if ($Session.owner -cne 'run' -or
            [String]::IsNullOrWhiteSpace($Session.runId) -or
            [String]::IsNullOrWhiteSpace($Session.repositoryIdentity) -or
            [String]::IsNullOrWhiteSpace($Session.worktreeRoot) -or
            [String]::IsNullOrWhiteSpace($Session.profile) -or
            [String]::IsNullOrWhiteSpace($Session.cdpLeasePath) -or
            [String]::IsNullOrWhiteSpace($Session.browserPath)) {
        Throw-VerifierInfrastructure "$Label did not carry the exact run-owned browser identity strings."
    }
    if ($Session.cleanupResult -ceq 'complete' -and
            ($Session.status -cne 'cleaned' -or
             -not $Session.profileProcessScanCompleted -or
             $Session.profileInspectionFailed)) {
        Throw-VerifierInfrastructure "$Label marked completion without retained profile cleanup proof."
    }
}

function Assert-VerifierSerializedServerRecord($Server,
        [string]$Label = 'serialized verifier server') {
    Assert-VerifierSerializedObject $Server $Label
    foreach ($name in @('owner', 'baseUrl', 'repositoryIdentity',
            'worktreeRoot', 'repositoryRoot', 'webRoot', 'identityProtocol',
            'processCommandLine', 'script', 'runId', 'nonce', 'leaseId',
            'leaseKind', 'leaseClaimName', 'leaseClaimState',
            'leaseReleaseState', 'leaseReleaseJournalState', 'leasePath',
            'state', 'stdoutLog', 'stderrLog', 'cleanupResult',
            'leaseListenerOwnerKind', 'leaseListenerOwnerProof',
            'leaseListenerOwnerEvidence', 'error')) {
        $allowEmpty = $name -in @('baseUrl', 'processCommandLine', 'script', 'runId',
            'nonce', 'leaseId', 'leaseKind', 'leaseClaimName',
            'leaseClaimState', 'leaseReleaseState',
            'leaseReleaseJournalState', 'leasePath', 'stdoutLog',
            'stderrLog', 'identityProtocol', 'leaseListenerOwnerKind',
            'leaseListenerOwnerProof', 'leaseListenerOwnerEvidence', 'error')
        [void](Assert-VerifierSerializedString $Server $name $Label -AllowEmpty $allowEmpty)
    }
    if (-not (Test-VerifierServerState $Server.state) -or
            -not (Test-VerifierServerCleanupResult $Server.cleanupResult)) {
        Throw-VerifierInfrastructure "$Label carried an unknown finite lifecycle state or cleanup result."
    }
    foreach ($number in @(
            [pscustomobject]@{ Name = 'port'; Minimum = 0L; Maximum = 65535L }
            [pscustomobject]@{ Name = 'processId'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'processStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
            [pscustomobject]@{ Name = 'processParentProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'processParentProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
            [pscustomobject]@{ Name = 'leaseOwnerPid'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'leaseOwnerStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
        )) {
        [void](Assert-VerifierSerializedIntegral $Server $number.Name $number.Minimum $number.Maximum $Label)
    }
    foreach ($name in @('identityVerified', 'callerOwned',
            'processIdentityKnown', 'ownershipUncertain',
            'processTerminationProven', 'processAbsent',
            'listenerInspectionProven')) {
        [void](Assert-VerifierSerializedBoolean $Server $name $Label)
    }
    foreach ($name in @('listenerAbsent', 'leaseListenerAbsent',
            'leaseProcessProofRequired')) {
        [void](Assert-VerifierSerializedBoolean $Server $name $Label -AllowNull)
    }
    if ($Server.owner -cnotin @('none', 'caller', 'run')) {
        Throw-VerifierInfrastructure "$Label carried an unknown serialized owner kind '$($Server.owner)'."
    }
    if ($Server.owner -ceq 'none' -and
            -not [String]::IsNullOrWhiteSpace($Server.identityProtocol)) {
        Throw-VerifierInfrastructure "$Label ownerless server carried an identity protocol."
    }
    if ($Server.owner -cin @('caller', 'run') -and
            $Server.identityProtocol -cne 'troubleshootjs-preview-identity-v1') {
        Throw-VerifierInfrastructure "$Label owned server carried a noncanonical identity protocol."
    }
    if ($Server.owner -eq 'run') {
        if ($Server.leaseListenerOwnerKind -ceq 'user-process') {
            if ($Server.leaseListenerOwnerProof -cne 'diagnostics-process-start-v1' -or
                    $Server.leaseListenerOwnerEvidence -cne 'system-diagnostics-process-starttime') {
                Throw-VerifierInfrastructure "$Label run-owned server carried a noncanonical user-process listener proof."
            }
        } elseif ($Server.leaseListenerOwnerKind -ceq 'kernel-transport') {
            if ($Server.leaseListenerOwnerProof -cne 'run-owned-preview-http-sys-v1' -or
                    $Server.leaseListenerOwnerEvidence -cne 'pid-4-system-http-sys') {
                Throw-VerifierInfrastructure "$Label run-owned server carried a noncanonical kernel-transport listener proof."
            }
        } else {
            Throw-VerifierInfrastructure "$Label run-owned server carried an unknown listener owner kind."
        }
    } elseif (-not (Test-VerifierListenerOwnerTuple `
            $Server.leaseListenerOwnerKind $Server.leaseListenerOwnerProof `
            $Server.leaseListenerOwnerEvidence 0 0L $true)) {
        Throw-VerifierInfrastructure "$Label ownerless/caller-owned server carried a noncanonical listener owner tuple."
    }
    $canonicalIdentity = [pscustomobject]@{
        ProcessId = $Server.processId
        ProcessStartTicks = $Server.processStartTicks
        ProcessParentProcessId = $Server.processParentProcessId
        ProcessParentProcessStartTicks = $Server.processParentProcessStartTicks
        ProcessCommandLine = $Server.processCommandLine
    }
    [void](Assert-VerifierDurableProcessIdentityTuple $canonicalIdentity `
        -ProcessIdPropertyName 'ProcessId' `
        -ProcessStartPropertyName 'ProcessStartTicks' `
        -ParentProcessIdPropertyName 'ProcessParentProcessId' `
        -ParentProcessStartPropertyName 'ProcessParentProcessStartTicks' `
        -CommandLinePropertyName 'ProcessCommandLine' `
        -Label ($Label + ' process identity'))
    if ($Server.owner -cne 'run' -and -not (Test-VerifierListenerOwnerTuple `
            $Server.leaseListenerOwnerKind $Server.leaseListenerOwnerProof `
            $Server.leaseListenerOwnerEvidence 0 0L $true)) {
        Throw-VerifierInfrastructure "$Label carried a noncanonical ownerless listener tuple."
    }
    if ($Server.owner -ceq 'run' -and $null -eq $Server.leaseProcessProofRequired) {
        Throw-VerifierInfrastructure "$Label omitted its run-owned lease process-proof Boolean."
    }
}

function Test-VerifierSerializedListenerOwnerSchema($Lease,
        $Server = $null) {
    try {
        $canonicalLease = $null
        if ($null -ne $Lease) {
            $canonicalLease = ConvertFrom-VerifierSerializedLeaseRecord $Lease `
                'serialized listener lease'
        }
        if ($null -ne $Server) {
            Assert-VerifierSerializedServerRecord $Server 'serialized listener server'
            if ($null -eq $canonicalLease -and $Server.owner -ceq 'run') {
                return $false
            }
            if ($null -ne $canonicalLease -and
                    ($Server.leaseListenerOwnerKind -cne $canonicalLease.ListenerOwnerKind -or
                     $Server.leaseListenerOwnerProof -cne $canonicalLease.ListenerOwnerProof -or
                     $Server.leaseListenerOwnerEvidence -cne $canonicalLease.ListenerOwnerEvidence)) {
                return $false
            }
        }
        return ($null -eq $canonicalLease -or
            (Test-VerifierListenerOwnerTuple $canonicalLease.ListenerOwnerKind `
                $canonicalLease.ListenerOwnerProof $canonicalLease.ListenerOwnerEvidence `
                $canonicalLease.ListenerProcessId $canonicalLease.ListenerProcessStartTicks $true))
    } catch { return $false }
}

function Test-VerifierSerializedLeaseTerminal($Lease, $ClaimPath,
        $Server = $null, [string]$ExpectedRunId = '',
        [string]$ExpectedRepositoryIdentity = '',
        [string]$ExpectedWorktreeRoot = '') {
    $canonicalLease = $null
    try {
        $canonicalLease = ConvertFrom-VerifierSerializedLeaseRecord $Lease `
            'serialized terminal lease'
        [void](Assert-VerifierDurableLeaseTerminalFields $canonicalLease $null `
            'serialized terminal lease')
        if (-not (Test-VerifierStrictStringValue $ClaimPath) -or
                [String]::IsNullOrWhiteSpace($ClaimPath)) {
            return $false
        }
        $expectedRun = if ([String]::IsNullOrWhiteSpace($ExpectedRunId)) {
            [string]$Lease.runId
        } else { $ExpectedRunId }
        $expectedRepository = if ([String]::IsNullOrWhiteSpace($ExpectedRepositoryIdentity)) {
            [string]$Lease.repositoryIdentity
        } else { $ExpectedRepositoryIdentity }
        $expectedWorktree = if ([String]::IsNullOrWhiteSpace($ExpectedWorktreeRoot)) {
            [string]$Lease.worktreeRoot
        } else { $ExpectedWorktreeRoot }
        if (-not (Test-VerifierStrictStringValue $expectedRun) -or
                -not (Test-VerifierStrictStringValue $expectedRepository) -or
                -not (Test-VerifierStrictStringValue $expectedWorktree) -or
                [String]::IsNullOrWhiteSpace($expectedRun) -or
                [String]::IsNullOrWhiteSpace($expectedRepository) -or
                [String]::IsNullOrWhiteSpace($expectedWorktree) -or
                $Lease.runId -cne $expectedRun -or
                $Lease.repositoryIdentity -cne $expectedRepository -or
                -not (Test-VerifierCanonicalWindowsPathValue $Lease.worktreeRoot $expectedWorktree) -or
                -not (Test-VerifierCanonicalWindowsPathValue $ClaimPath $Lease.path)) {
            return $false
        }
        if ($null -ne $Server) {
            Assert-VerifierSerializedServerRecord $Server 'serialized terminal server'
            if ($Server.owner -cne 'run' -or
                    $Server.port -ne $Lease.port -or
                    $Server.baseUrl -cne ('http://127.0.0.1:' + [string]$Server.port) -or
                    $Server.leaseKind -cne 'preview' -or
                    $Server.leaseListenerOwnerKind -cne $Lease.listenerOwnerKind -or
                    $Server.leaseListenerOwnerProof -cne $Lease.listenerOwnerProof -or
                    $Server.leaseListenerOwnerEvidence -cne $Lease.listenerOwnerEvidence -or
                    $Server.identityProtocol -cne 'troubleshootjs-preview-identity-v1' -or
                    $Server.runId -cne $expectedRun -or
                    $Server.repositoryIdentity -cne $expectedRepository -or
                    -not (Test-VerifierCanonicalWindowsPathValue $Server.worktreeRoot $expectedWorktree) -or
                    -not (Test-VerifierCanonicalWindowsPathValue $Server.repositoryRoot $expectedWorktree) -or
                    -not (Test-VerifierCanonicalWindowsPathValue $Server.webRoot (Join-Path $expectedWorktree 'war')) -or
                    -not (Test-VerifierCanonicalWindowsPathValue $Server.script (Join-Path $expectedWorktree 'scripts\preview.ps1')) -or
                    [String]::IsNullOrWhiteSpace($Server.nonce) -or
                    $Server.processId -ne $Lease.boundProcessId -or
                    $Server.processStartTicks -ne $Lease.boundProcessStartTicks -or
                    [String]::IsNullOrWhiteSpace($Server.processCommandLine) -or
                    -not (Test-VerifierCommandLinePath $Server.processCommandLine $Server.script) -or
                    -not (Test-VerifierCommandLineSwitch $Server.processCommandLine '-Port' ([string]$Server.port)) -or
                    -not (Test-VerifierCommandLineSwitch $Server.processCommandLine '-VerifierRunId' $expectedRun) -or
                    -not (Test-VerifierCommandLineSwitch $Server.processCommandLine '-VerifierNonce' $Server.nonce) -or
                     $Server.state -cne 'cleaned' -or $Server.cleanupResult -cne 'complete' -or
                    -not $Server.identityVerified -or $Server.callerOwned -or
                    -not $Server.processIdentityKnown -or $Server.ownershipUncertain -or
                    -not $Server.processTerminationProven -or -not $Server.processAbsent -or
                    -not $Server.listenerInspectionProven -or $Server.listenerAbsent -ne $true -or
                    $Server.leaseId -cne $Lease.leaseId -or
                    $Server.leaseClaimName -cne $Lease.claimName -or
                     $Server.leaseClaimState -cne $Lease.claimState -or
                     $Server.leaseReleaseState -cne $Lease.releaseState -or
                     $Server.leaseReleaseJournalState -cne $Lease.releaseJournalState -or
                    -not (Test-VerifierCanonicalWindowsPathValue $Server.leasePath $Lease.path) -or
                    $Server.leaseOwnerPid -ne $Lease.claimOwnerPid -or
                    $Server.leaseOwnerStartTicks -ne $Lease.claimOwnerStartTicks) {
                return $false
            }
        }
    } catch {
        return $false
    }
    try {
        Assert-VerifierNoReparseAncestors $ClaimPath
        if (Test-Path -LiteralPath $ClaimPath -PathType Leaf -ErrorAction Stop) {
            return $false
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not prove serialized terminal lease claim absence: ' +
            (Get-VerifierErrorMessage $_))
    }
    return $true
}

function Assert-VerifierDurableServerLease($Context,
        [string]$Label = 'verifier server state', [switch]$AllowMissing) {
    [void](Assert-VerifierContextPreflight $Context $Label `
        -RequiredProperties @('RunId', 'RepositoryIdentity'))
    if ($null -eq $Context) {
        if ($AllowMissing) { return }
        Throw-VerifierInfrastructure "$Label omitted its context."
    }
    $serverProperty = $Context.PSObject.Properties['Server']
    if ($null -eq $serverProperty -or $null -eq $serverProperty.Value) {
        if ($AllowMissing) { return }
        Throw-VerifierInfrastructure "$Label omitted its run-owned Server record."
    }
    $server = $serverProperty.Value
    # A run-owned server carries the live lease used by every preview/listener
    # operation.  Validate that association before reading server paths or
    # process identity fields; durable serialization below repeats the same
    # complete check through Assert-VerifierDurableLeaseRecord.
    $earlyServerLeaseProperty = $server.PSObject.Properties['Lease']
    if ($null -ne $earlyServerLeaseProperty -and
            $null -ne $earlyServerLeaseProperty.Value) {
        Assert-VerifierLiveClaimMutex $earlyServerLeaseProperty.Value $Context `
            ($Label + ' live lease')
    }
    foreach ($propertyName in @(
            'Owner', 'BaseUrl', 'ProcessCommandLine', 'Script', 'State',
            'RepositoryRoot', 'WebRoot', 'IdentityProtocol', 'StdoutLog',
            'StderrLog', 'CleanupResult', 'Error', 'RunId', 'Nonce')) {
        if ($null -eq $server.PSObject.Properties[$propertyName] -or
                -not (Test-VerifierStrictStringValue $server.PSObject.Properties[$propertyName].Value)) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its exact string '$propertyName'."
        }
    }
    if (-not (Test-VerifierServerState $server.State) -or
            -not (Test-VerifierServerCleanupResult $server.CleanupResult)) {
        Throw-VerifierInfrastructure "$Label carried an unknown finite lifecycle state or cleanup result."
    }
    foreach ($numericProperty in @(
            [pscustomobject]@{ Name = 'Port'; Minimum = 0L; Maximum = 65535L }
            [pscustomobject]@{ Name = 'ProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'ProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
            [pscustomobject]@{ Name = 'ProcessParentProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'ProcessParentProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
        )) {
        if ($null -eq $server.PSObject.Properties[$numericProperty.Name] -or
                -not (Test-VerifierStrictIntegralValue $server.PSObject.Properties[$numericProperty.Name].Value `
                    $numericProperty.Minimum $numericProperty.Maximum)) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its exact integral '$($numericProperty.Name)'."
        }
    }
    foreach ($booleanProperty in @(
            'IdentityVerified', 'CallerOwned', 'ProcessIdentityKnown',
            'OwnershipUncertain', 'ProcessTerminationProven', 'ProcessAbsent',
            'ListenerInspectionProven')) {
        [void](Get-VerifierRequiredBooleanValue $server $booleanProperty $Label)
    }
    $listenerAbsentProperty = $server.PSObject.Properties['ListenerAbsent']
    if ($null -eq $listenerAbsentProperty -or ($null -ne $listenerAbsentProperty.Value -and
            -not (Test-VerifierStrictBooleanValue $listenerAbsentProperty.Value))) {
        Throw-VerifierInfrastructure "$Label omitted or malformed its nullable Boolean 'ListenerAbsent'."
    }
    if (-not (Test-VerifierCanonicalWindowsPathValue $server.RepositoryRoot `
            $Context.WorktreeRoot) -or
            -not (Test-VerifierCanonicalWindowsPathValue $server.WebRoot `
                (Join-Path $Context.WorktreeRoot 'war'))) {
        Throw-VerifierInfrastructure "$Label carried a foreign repository or web-root path."
    }
    $callerOwned = Get-VerifierRequiredBooleanValue $server 'CallerOwned' $Label
    $hasLeaseProperty = $null -ne $server.PSObject.Properties['Lease']
    $lease = if ($hasLeaseProperty) { $server.Lease } else { $null }
    if ($server.Owner -ceq 'none') {
        if ($callerOwned -or ($hasLeaseProperty -and $null -ne $lease) -or
                $server.BaseUrl -cne '' -or $server.Port -ne 0 -or
                $server.ProcessId -ne 0 -or $server.ProcessStartTicks -ne 0 -or
                $server.ProcessParentProcessId -ne 0 -or
                $server.ProcessParentProcessStartTicks -ne 0 -or
                $server.ProcessCommandLine -cne '' -or $server.Script -cne '' -or
                $server.IdentityProtocol -cne '' -or $server.IdentityVerified -or
                -not [String]::IsNullOrWhiteSpace($server.RunId) -or
                -not [String]::IsNullOrWhiteSpace($server.Nonce) -or
                -not [String]::IsNullOrWhiteSpace($server.StdoutLog) -or
                -not [String]::IsNullOrWhiteSpace($server.StderrLog) -or
                $server.State -cne 'not-started' -or
                $server.CleanupResult -cne 'not-applicable' -or
                $server.ProcessIdentityKnown -or $server.OwnershipUncertain -or
                $server.ProcessTerminationProven -or $server.ProcessAbsent -or
                $server.ListenerInspectionProven -or $null -ne $listenerAbsentProperty.Value) {
            Throw-VerifierInfrastructure "$Label was not a validated ownerless server state."
        }
        return
    }
    if ($server.Owner -ceq 'caller') {
        $expectedCallerBaseUrl = 'http://127.0.0.1:' + [string]$server.Port
        $expectedCallerScript = Get-VerifierFullPath (Join-Path $Context.WorktreeRoot 'scripts\preview.ps1')
        $expectedCallerWebRoot = Get-VerifierFullPath (Join-Path $Context.WorktreeRoot 'war')
        if (-not $callerOwned -or ($hasLeaseProperty -and $null -ne $lease) -or
                $server.Port -lt 1 -or $server.BaseUrl -cne $expectedCallerBaseUrl -or
                $server.IdentityProtocol -cne 'troubleshootjs-preview-identity-v1' -or
                -not $server.IdentityVerified -or
                -not (Test-VerifierCanonicalWindowsPathValue $server.Script $expectedCallerScript) -or
                -not (Test-VerifierCanonicalWindowsPathValue $server.WebRoot $expectedCallerWebRoot) -or
                $server.ProcessId -ne 0 -or $server.ProcessStartTicks -ne 0 -or
                $server.ProcessParentProcessId -ne 0 -or
                $server.ProcessParentProcessStartTicks -ne 0 -or
                $server.ProcessCommandLine -cne '' -or
                -not [String]::IsNullOrWhiteSpace($server.RunId) -or
                -not [String]::IsNullOrWhiteSpace($server.Nonce) -or
                -not [String]::IsNullOrWhiteSpace($server.StdoutLog) -or
                -not [String]::IsNullOrWhiteSpace($server.StderrLog) -or
                $server.State -cne 'caller-verified' -or
                $server.CleanupResult -cne 'not-owned' -or
                $server.ProcessIdentityKnown -or $server.OwnershipUncertain -or
                $server.ProcessTerminationProven -or -not $server.ProcessAbsent -or
                $server.ListenerInspectionProven -or $null -eq $listenerAbsentProperty.Value -or
                $listenerAbsentProperty.Value) {
            Throw-VerifierInfrastructure "$Label was not a validated caller-owned server state."
        }
        return
    }
    if ($server.Owner -ceq 'run') {
        if ($callerOwned -or -not $hasLeaseProperty -or $null -eq $lease) {
            Throw-VerifierInfrastructure "$Label omitted its required run-owned Server.Lease."
        }
        if (-not (Test-VerifierStrictStringValue $Context.RunId) -or
                -not (Test-VerifierStrictStringValue $Context.PreviewNonce) -or
                $server.Port -lt 1 -or
                $server.BaseUrl -cne ('http://127.0.0.1:' + [string]$server.Port) -or
                $server.RunId -cne $Context.RunId -or
                $server.Nonce -cne $Context.PreviewNonce -or
                -not (Test-VerifierCanonicalWindowsPathValue $server.Script `
                    (Join-Path $Context.WorktreeRoot 'scripts\preview.ps1')) -or
                [String]::IsNullOrWhiteSpace($server.StdoutLog) -or
                [String]::IsNullOrWhiteSpace($server.StderrLog)) {
            Throw-VerifierInfrastructure "$Label was not a complete run-owned server state."
        }
        if ($server.ProcessId -eq 0 -and ($server.ProcessStartTicks -ne 0 -or
                $server.ProcessParentProcessId -ne 0 -or
                $server.ProcessParentProcessStartTicks -ne 0 -or
                -not [String]::IsNullOrWhiteSpace($server.ProcessCommandLine))) {
            Throw-VerifierInfrastructure "$Label carried a process identity without a positive server PID."
        }
        if ($server.ProcessIdentityKnown -and ($server.ProcessId -le 0 -or
                $server.ProcessStartTicks -le 0 -or
                $server.ProcessParentProcessId -le 0 -or
                $server.ProcessParentProcessStartTicks -le 0 -or
                [String]::IsNullOrWhiteSpace($server.ProcessCommandLine))) {
            Throw-VerifierInfrastructure "$Label claimed a complete process identity without all exact fields."
        }
        Assert-VerifierDurableLeaseRecord $lease $Context ($Label + ' lease')
        if ([long]$lease.Port -ne [long]$server.Port -or
                -not [object]::ReferenceEquals($Context.Server.Lease, $lease)) {
            Throw-VerifierInfrastructure "$Label server and lease records did not identify the same exact port lease."
        }
        return
    }
    Throw-VerifierInfrastructure "$Label used an unknown exact Owner value '$($server.Owner)'."
}

function Assert-VerifierDurableBrowserSession($Session, $Context,
        [string]$Label = 'verifier browser session') {
    if ($null -eq $Session) {
        Throw-VerifierInfrastructure "$Label was null before durable serialization."
    }
    if ($null -eq $Context) {
        Throw-VerifierInfrastructure "$Label omitted its verifier context."
    }
    [void](Assert-VerifierContextPreflight $Context ($Label + ' context') `
        -RequiredProperties @('RunId', 'RepositoryIdentity'))
    foreach ($propertyName in @('RunId', 'RepositoryIdentity', 'WorktreeRoot',
            'RouteId', 'RouteName', 'BrowserPath', 'Profile', 'ProcessCommandLine',
            'TargetId', 'ExpectedUrl', 'Status', 'CleanupResult', 'Error')) {
        $property = $Session.PSObject.Properties[$propertyName]
        if ($null -eq $property -or
                -not (Test-VerifierStrictStringValue $property.Value)) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its exact string '$propertyName'."
        }
    }
    if (-not (Test-VerifierBrowserSessionStatus $Session.Status) -or
            -not (Test-VerifierBrowserSessionCleanupResult $Session.CleanupResult)) {
        Throw-VerifierInfrastructure "$Label carried an unknown finite lifecycle status or cleanup result."
    }
    foreach ($requiredString in @('RunId', 'RepositoryIdentity', 'WorktreeRoot',
            'RouteId', 'RouteName', 'BrowserPath', 'Profile')) {
        if ([String]::IsNullOrWhiteSpace($Session.PSObject.Properties[$requiredString].Value)) {
            Throw-VerifierInfrastructure "$Label omitted a non-empty run-owned identity string '$requiredString'."
        }
    }
    foreach ($numericProperty in @(
            [pscustomobject]@{ Name = 'CdpPort'; Minimum = 1L; Maximum = 65535L }
        )) {
        $property = $Session.PSObject.Properties[$numericProperty.Name]
        if ($null -eq $property -or
                -not (Test-VerifierStrictIntegralValue $property.Value `
                    $numericProperty.Minimum $numericProperty.Maximum)) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its exact integral '$($numericProperty.Name)'."
        }
    }
    [void](Assert-VerifierDurableProcessIdentityTuple $Session `
        -ProcessIdPropertyName 'ProcessId' `
        -ProcessStartPropertyName 'ProcessStartTicks' `
        -ParentProcessIdPropertyName 'ProcessParentProcessId' `
        -ParentProcessStartPropertyName 'ProcessParentProcessStartTicks' `
        -CommandLinePropertyName 'ProcessCommandLine' `
        -Label ($Label + ' process identity'))
    foreach ($booleanProperty in @('ProfileInspectionFailed',
            'ProfileProcessScanCompleted')) {
        [void](Get-VerifierRequiredBooleanValue $Session $booleanProperty $Label)
    }
    if ($null -ne $Context -and
            ($Session.RunId -cne $Context.RunId -or
             $Session.RepositoryIdentity -cne $Context.RepositoryIdentity -or
             -not (Test-VerifierCanonicalWindowsPathValue $Session.WorktreeRoot `
                 $Context.WorktreeRoot))) {
        Throw-VerifierInfrastructure "$Label carried foreign run identity."
    }
    $leaseProperty = $Session.PSObject.Properties['Lease']
    if ($null -eq $leaseProperty -or $null -eq $leaseProperty.Value) {
        Throw-VerifierInfrastructure "$Label omitted its exact durable lease record."
    }
    Assert-VerifierDurableLeaseRecord $leaseProperty.Value $Context `
        ($Label + ' lease')
    if ($null -ne $Context -and $null -ne $Context.PSObject.Properties['LeaseRecords'] -and
            -not (@($Context.LeaseRecords) -contains $leaseProperty.Value)) {
        Throw-VerifierInfrastructure "$Label did not reference a lease in the exact run lease ledger."
    }
    if ($Session.CleanupResult -ceq 'complete') {
        if ($Session.Status -cne 'cleaned' -or
                -not $Session.ProfileProcessScanCompleted -or
                $Session.ProfileInspectionFailed) {
            Throw-VerifierInfrastructure "$Label marked completion without retained profile cleanup proof."
        }
        [void](Assert-VerifierDurableLeaseTerminal $leaseProperty.Value $Context `
            ($Label + ' completion lease'))
        try {
            Assert-VerifierNoReparseAncestors ([string]$Session.Profile)
            if (Test-Path -LiteralPath ([string]$Session.Profile) -ErrorAction Stop) {
                Throw-VerifierInfrastructure "$Label retained its browser profile after completion."
            }
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure "$Label profile deletion was not positively proven: $(Get-VerifierErrorMessage $_)"
        }
    }
}

function Assert-VerifierDurableManifestContext($Context) {
    [void](Assert-VerifierContextPreflight $Context 'verifier manifest context' -RequireDurable)
    $serverProperty = $Context.PSObject.Properties['Server']
    Assert-VerifierDurableServerLease $Context -AllowMissing
    foreach ($lease in @($Context.LeaseRecords)) {
        Assert-VerifierDurableLeaseRecord $lease $Context 'verifier lease record'
    }
    foreach ($session in @($Context.BrowserSessions)) {
        Assert-VerifierDurableBrowserSession $session $Context
    }
    foreach ($artifact in @($Context.Artifacts)) {
        if (-not (Test-VerifierStrictStringValue $artifact)) {
            Throw-VerifierInfrastructure 'Verifier manifest context carried a malformed evidence artifact entry.'
        }
    }
    foreach ($errorItem in @($Context.CleanupErrors)) {
        if (-not (Test-VerifierStrictStringValue $errorItem)) {
            Throw-VerifierInfrastructure 'Verifier manifest context carried a malformed cleanup error entry.'
        }
    }
}

function Test-VerifierListenerInspectionSchema($Inspection,
        $PreviewContext = $null, $PreviewOwner = $null,
        $AuthorizationProof = $null, [switch]$StructuralOnly) {
    if ($null -ne $PreviewContext) {
        try { [void](Assert-VerifierContextPreflight $PreviewContext 'listener inspection schema context') }
        catch { return $false }
    }
    if ($null -eq $Inspection) { return $false }
    foreach ($propertyName in @('Success', 'Known', 'HasListeners', 'Listeners',
            'ListenerOwnerKind', 'ListenerOwnerProof', 'ListenerOwnerEvidence',
            'Source', 'Error')) {
        if (-not $Inspection.PSObject.Properties[$propertyName]) { return $false }
    }
    foreach ($propertyName in @('Success', 'Known')) {
        if (-not (Test-VerifierStrictBooleanValue $Inspection.$propertyName) -or
                -not $Inspection.$propertyName) { return $false }
    }
    if (-not (Test-VerifierStrictBooleanValue $Inspection.HasListeners)) { return $false }
    if ($null -eq $Inspection.Listeners -or -not ($Inspection.Listeners -is [array]) -or
            -not (Test-VerifierStrictStringValue $Inspection.Source) -or
            $Inspection.Source -cnotin @('netstat', 'Get-NetTCPConnection') -or
            -not (Test-VerifierStrictStringValue $Inspection.Error) -or
            $Inspection.Error -cne '' -or
            -not (Test-VerifierStrictStringValue $Inspection.ListenerOwnerKind) -or
            -not (Test-VerifierStrictStringValue $Inspection.ListenerOwnerProof) -or
            -not (Test-VerifierStrictStringValue $Inspection.ListenerOwnerEvidence)) {
        return $false
    }
    $listeners = @($Inspection.Listeners)
    if ([bool]$Inspection.HasListeners -ne ($listeners.Count -gt 0)) { return $false }
    if (-not $Inspection.HasListeners) {
        return (Test-VerifierListenerOwnerTuple $Inspection.ListenerOwnerKind `
            $Inspection.ListenerOwnerProof $Inspection.ListenerOwnerEvidence `
            0 0L $true)
    }

    $endpoints = @{}
    $identities = @{}
    foreach ($listener in $listeners) {
        if (-not (Test-VerifierListenerRecordSchema $listener $PreviewContext `
                $PreviewOwner $AuthorizationProof -StructuralOnly:$StructuralOnly)) {
            return $false
        }
        $endpoint = ([string]$listener.LocalAddress).ToLowerInvariant() + '|' +
            [string]$listener.Port
        $identity = [string]$listener.ProcessId + '|' +
            $(if ($null -eq $listener.ProcessStartTicks) { '<null>' } else {
                [string]$listener.ProcessStartTicks })
        if ($endpoints.ContainsKey($endpoint) -or $identities.ContainsKey($identity)) {
            return $false
        }
        $endpoints[$endpoint] = $true
        $identities[$identity] = $true
        if ($listener.ListenerOwnerKind -cne $Inspection.ListenerOwnerKind -or
                $listener.ListenerOwnerProof -cne $Inspection.ListenerOwnerProof -or
                $listener.ListenerOwnerEvidence -cne $Inspection.ListenerOwnerEvidence) {
            return $false
        }
    }
    $firstListener = $listeners[0]
    return (Test-VerifierListenerOwnerTuple $Inspection.ListenerOwnerKind `
        $Inspection.ListenerOwnerProof $Inspection.ListenerOwnerEvidence `
        $firstListener.ProcessId $firstListener.ProcessStartTicks)
}

function Test-VerifierRunOwnedPreviewHttpSysAuthorization($Context, $OwnerRecord,
        $Port) {
    if ($null -eq $Context -or $null -eq $OwnerRecord) { return $false }
    try { [void](Assert-VerifierContextPreflight $Context 'preview authorization context' `
            -RequiredProperties @('RunId', 'RepositoryIdentity', 'PreviewNonce')) }
    catch { return $false }
    if (-not (Test-VerifierStrictIntegralValue $Port 1 65535)) { return $false }
    if (-not $Context.PSObject.Properties['Server'] -or
            -not [object]::ReferenceEquals($Context.Server, $OwnerRecord)) {
        return $false
    }
    # This is the kernel transport trust boundary.  Validate the complete
    # server/lease association before inspecting the retained process object
    # or accepting any PID 4 listener evidence.  The durable validator owns
    # all raw scalar bounds and lifecycle/identity relationships.
    try {
        Assert-VerifierDurableServerLease $Context 'run-owned preview HTTP.sys authorization'
    } catch { return $false }
    foreach ($propertyName in @('WorktreeRoot', 'RepositoryIdentity',
            'RunId', 'PreviewNonce')) {
        if (-not $Context.PSObject.Properties[$propertyName] -or
                -not (Test-VerifierStrictStringValue $Context.$propertyName) -or
                [String]::IsNullOrWhiteSpace($Context.$propertyName)) {
            return $false
        }
    }
    foreach ($propertyName in @('Owner', 'BaseUrl', 'Port', 'ProcessId',
            'ProcessStartTicks', 'ProcessParentProcessId',
            'ProcessParentProcessStartTicks', 'ProcessCommandLine', 'Script',
            'RepositoryRoot', 'WebRoot', 'IdentityProtocol',
            'IdentityVerified', 'CallerOwned', 'RunId', 'Nonce', 'State',
            'Lease', 'Process', 'ProcessIdentityKnown', 'OwnershipUncertain')) {
        if (-not $OwnerRecord.PSObject.Properties[$propertyName]) { return $false }
    }
    foreach ($propertyName in @('Owner', 'State', 'IdentityProtocol', 'RunId',
            'Nonce', 'ProcessCommandLine', 'Script', 'RepositoryRoot', 'WebRoot')) {
        if (-not (Test-VerifierStrictStringValue $OwnerRecord.$propertyName)) {
            return $false
        }
    }
    foreach ($propertyName in @('IdentityVerified', 'CallerOwned',
            'ProcessIdentityKnown', 'OwnershipUncertain')) {
        if (-not (Test-VerifierStrictBooleanValue $OwnerRecord.$propertyName)) {
            return $false
        }
    }
    foreach ($numericProperty in @(
            [pscustomobject]@{ Name = 'Port'; Minimum = 1L; Maximum = 65535L }
            [pscustomobject]@{ Name = 'ProcessId'; Minimum = 1L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'ProcessStartTicks'; Minimum = 1L; Maximum = [long]::MaxValue }
            [pscustomobject]@{ Name = 'ProcessParentProcessId'; Minimum = 1L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'ProcessParentProcessStartTicks'; Minimum = 1L; Maximum = [long]::MaxValue }
        )) {
        if (-not (Test-VerifierStrictIntegralValue $OwnerRecord.$($numericProperty.Name) `
                $numericProperty.Minimum $numericProperty.Maximum)) {
            return $false
        }
    }
    if ($OwnerRecord.Owner -cne 'run' -or
            $OwnerRecord.State -cne 'run-owned-verified' -or
            $OwnerRecord.IdentityProtocol -cne 'troubleshootjs-preview-identity-v1' -or
            -not $OwnerRecord.IdentityVerified -or $OwnerRecord.CallerOwned -or
            -not $OwnerRecord.ProcessIdentityKnown -or $OwnerRecord.OwnershipUncertain -or
            [long]$OwnerRecord.Port -ne $Port -or [long]$OwnerRecord.ProcessId -le 0 -or
            [long]$OwnerRecord.ProcessId -eq 4 -or
            [long]$OwnerRecord.ProcessStartTicks -le 0 -or
            [long]$OwnerRecord.ProcessParentProcessId -le 0 -or
            [long]$OwnerRecord.ProcessParentProcessStartTicks -le 0 -or
            [String]::IsNullOrWhiteSpace($OwnerRecord.ProcessCommandLine) -or
            [String]::IsNullOrWhiteSpace($OwnerRecord.Script) -or
            [String]::IsNullOrWhiteSpace($OwnerRecord.RunId) -or
            [String]::IsNullOrWhiteSpace($OwnerRecord.Nonce) -or
            -not ($OwnerRecord.Process -is [System.Diagnostics.Process])) {
        return $false
    }
    # The preview server is already the validated Context.Server above.  A
    # CDP/browser owner is not valid for this handshake, but if a caller adds
    # that shape it must still cross the same complete durable session gate.
    if ($OwnerRecord.PSObject.Properties['CdpPort']) {
        try {
            Assert-VerifierDurableBrowserSession $OwnerRecord $Context `
                'run-owned preview HTTP.sys browser owner'
        } catch { return $false }
    }
    try {
        if ([bool]$OwnerRecord.Process.HasExited -or
                [int]$OwnerRecord.Process.Id -ne [int]$OwnerRecord.ProcessId -or
                (Get-VerifierProcessStartTicks $OwnerRecord.Process) -ne
                    [long]$OwnerRecord.ProcessStartTicks) {
            return $false
        }
    } catch { return $false }
    $lease = $OwnerRecord.Lease
    if ($null -eq $lease -or
            -not $lease.PSObject.Properties['Kind'] -or
            -not $lease.PSObject.Properties['Port'] -or
            -not $lease.PSObject.Properties['Status'] -or
            -not $lease.PSObject.Properties['ClaimState'] -or
            -not $lease.PSObject.Properties['LeaseId'] -or
            -not $lease.PSObject.Properties['ClaimName'] -or
            -not $lease.PSObject.Properties['Path'] -or
            -not (Test-VerifierStrictStringValue $lease.Kind) -or
            -not (Test-VerifierStrictIntegralValue $lease.Port 1 65535) -or
            -not (Test-VerifierStrictStringValue $lease.Status) -or
            -not (Test-VerifierStrictStringValue $lease.ClaimState) -or
            -not (Test-VerifierStrictStringValue $lease.LeaseId) -or
            -not (Test-VerifierStrictStringValue $lease.ClaimName) -or
            -not (Test-VerifierStrictStringValue $lease.Path) -or
            [String]::IsNullOrWhiteSpace($lease.LeaseId) -or
            [String]::IsNullOrWhiteSpace($lease.ClaimName) -or
            [String]::IsNullOrWhiteSpace($lease.Path) -or
            $lease.Kind -cne 'preview' -or [long]$lease.Port -ne $Port -or
            $lease.Status -ceq 'released' -or
            $lease.ClaimState -cin @('released', 'os-released')) {
        return $false
    }
    if (-not $OwnerRecord.PSObject.Properties['Lease'] -or
            -not [object]::ReferenceEquals($OwnerRecord.Lease, $lease) -or
            $lease.ClaimName -cne (Get-VerifierPortMutexName $Context ([int]$Port))) {
        return $false
    }
    foreach ($boundProperty in @('BoundProcessId', 'BoundProcessStartTicks')) {
        if (-not $lease.PSObject.Properties[$boundProperty] -or
                -not (Test-VerifierStrictIntegralValue $lease.$boundProperty 0 ([long]::MaxValue))) {
            return $false
        }
    }
    $boundPid = [int]$lease.BoundProcessId
    $boundStart = [long]$lease.BoundProcessStartTicks
    if (($boundPid -eq 0) -xor ($boundStart -eq 0)) {
        return $false
    }
    if ($boundPid -gt 0 -and
            ($boundPid -ne [int]$OwnerRecord.ProcessId -or
             $boundStart -ne [long]$OwnerRecord.ProcessStartTicks)) {
        return $false
    }
    foreach ($propertyName in @('RunId', 'RepositoryIdentity', 'WorktreeRoot')) {
        if (-not $lease.PSObject.Properties[$propertyName] -or
                -not (Test-VerifierStrictStringValue $lease.$propertyName)) {
            return $false
        }
    }
    if ($lease.RunId -cne $Context.RunId -or
            $lease.RepositoryIdentity -cne $Context.RepositoryIdentity -or
            $lease.WorktreeRoot -cne $Context.WorktreeRoot -or
            $OwnerRecord.RunId -cne $Context.RunId -or
            $OwnerRecord.Nonce -cne $Context.PreviewNonce -or
            -not (Test-VerifierCanonicalWindowsPathValue `
                $OwnerRecord.RepositoryRoot $Context.WorktreeRoot)) {
        return $false
    }
    if ($Context.PSObject.Properties['PortLeaseRoot'] -and
            (Test-VerifierStrictStringValue $Context.PortLeaseRoot) -and
            -not [String]::IsNullOrWhiteSpace($Context.PortLeaseRoot)) {
        try {
            $expectedClaimPath = Get-VerifierFullPath (Join-Path $Context.PortLeaseRoot `
                ($Port.ToString() + '-' + $lease.LeaseId + '.lease'))
            if (-not (Test-VerifierCanonicalWindowsPathValue $lease.Path $expectedClaimPath)) {
                return $false
            }
        } catch { return $false }
    }
    try {
        $expectedScript = Get-VerifierFullPath (Join-Path $Context.WorktreeRoot 'scripts\preview.ps1')
        $expectedWebRoot = Get-VerifierFullPath (Join-Path $Context.WorktreeRoot 'war')
        if (-not (Test-VerifierStrictStringValue $OwnerRecord.BaseUrl) -or
                -not (Test-VerifierCanonicalWindowsPathValue $OwnerRecord.Script $expectedScript) -or
                -not (Test-VerifierCanonicalWindowsPathValue $OwnerRecord.WebRoot $expectedWebRoot) -or
                $OwnerRecord.BaseUrl -cne ('http://127.0.0.1:' + [string]$Port) -or
                -not (Test-VerifierCommandLinePath $OwnerRecord.ProcessCommandLine $OwnerRecord.Script) -or
                -not (Test-VerifierCommandLineSwitch $OwnerRecord.ProcessCommandLine '-Port' ([string]$Port)) -or
                -not (Test-VerifierCommandLineSwitch $OwnerRecord.ProcessCommandLine '-VerifierRunId' $OwnerRecord.RunId) -or
                -not (Test-VerifierCommandLineSwitch $OwnerRecord.ProcessCommandLine '-VerifierNonce' $OwnerRecord.Nonce)) {
            return $false
        }
    } catch {
        return $false
    }
    return $true
}

function New-VerifierRunOwnedPreviewHttpSysProofFieldSnapshot($Object,
        [string[]]$PropertyNames) {
    if ($null -eq $Object) { return $null }
    $snapshot = [ordered]@{}
    foreach ($propertyName in $PropertyNames) {
        $property = $Object.PSObject.Properties[$propertyName]
        $snapshot[$propertyName + 'Present'] = ($null -ne $property)
        if ($null -ne $property) {
            $snapshot[$propertyName] = $property.Value
        }
    }
    return [pscustomobject]$snapshot
}

function Test-VerifierRunOwnedPreviewHttpSysProofFieldSnapshot($Object,
        $Snapshot, [string[]]$PropertyNames) {
    if ($null -eq $Object -or $null -eq $Snapshot) { return $false }
    foreach ($propertyName in $PropertyNames) {
        $expectedPresence = $Snapshot.PSObject.Properties[
            ($propertyName + 'Present')]
        if ($null -eq $expectedPresence -or
                -not (Test-VerifierStrictBooleanValue $expectedPresence.Value)) {
            return $false
        }
        $currentProperty = $Object.PSObject.Properties[$propertyName]
        if (($null -ne $currentProperty) -ne [bool]$expectedPresence.Value) {
            return $false
        }
        if ($null -eq $currentProperty) { continue }
        $expectedProperty = $Snapshot.PSObject.Properties[$propertyName]
        if ($null -eq $expectedProperty) { return $false }
        $expected = $expectedProperty.Value
        $current = $currentProperty.Value
        if ($expected -is [string]) {
            if (-not (Test-VerifierStrictStringValue $current) -or
                    $current -cne $expected) { return $false }
        } elseif ($expected -is [bool]) {
            if (-not (Test-VerifierStrictBooleanValue $current) -or
                    [bool]$current -ne [bool]$expected) { return $false }
        } elseif ($expected -is [int] -or $expected -is [long]) {
            if (-not (Test-VerifierStrictIntegralValue $current 0 ([long]::MaxValue)) -or
                    [long]$current -ne [long]$expected) { return $false }
        } elseif ($propertyName -ceq 'ClaimMutex') {
            if (-not [object]::ReferenceEquals($current, $expected)) {
                return $false
            }
        } elseif ($null -eq $expected) {
            if ($null -ne $current) { return $false }
        } elseif ($current -ne $expected) {
            return $false
        }
    }
    return $true
}

function New-VerifierRunOwnedPreviewHttpSysProofIdentity($Context,
        $OwnerRecord) {
    if ($null -eq $Context -or $null -eq $OwnerRecord -or
            $null -eq $OwnerRecord.PSObject.Properties['Lease']) {
        return $null
    }
    # These are the context fields that participate in the semantic run
    # authorization.  In addition to the run/path tuple, retain lifecycle
    # state so a proof cannot survive cleanup or a changed manifest phase.
    $contextProperties = @('Protocol', 'WorktreeRoot', 'RepositoryIdentity',
        'RunId', 'PreviewNonce', 'RunRoot', 'RunNamespaceRoot',
        'EvidenceDirectory', 'EvidenceNamespaceRoot', 'ManifestPath',
        'PortLeaseRoot', 'CreatedUtc', 'BaseUrl', 'CleanupState',
        'CleanupCompletedUtc', 'ManifestWritePhase')
    $ownerProperties = @('Owner', 'BaseUrl', 'Port', 'ProcessId',
        'ProcessStartTicks', 'ProcessParentProcessId',
        'ProcessParentProcessStartTicks', 'ProcessCommandLine', 'Script',
        'RepositoryRoot', 'WebRoot', 'IdentityProtocol', 'IdentityVerified',
        'CallerOwned', 'RunId', 'Nonce', 'State', 'ProcessIdentityKnown',
        'OwnershipUncertain', 'ProcessTerminationProven', 'ProcessAbsent',
        'ListenerInspectionProven', 'ListenerAbsent', 'StdoutLog',
        'StderrLog', 'CleanupResult', 'Error', 'CdpPort', 'Profile',
        'BrowserPath')
    $leaseProperties = @('Kind', 'Port', 'Status', 'ClaimState', 'LeaseId',
        'ClaimName', 'Path', 'BoundProcessId', 'BoundProcessStartTicks',
        'RunId', 'RepositoryIdentity', 'WorktreeRoot', 'ClaimOwnerPid',
        'ClaimOwnerStartTicks', 'ClaimMutex', 'Registered', 'ProfilePath',
        'BrowserPath', 'BindValidatedUtc', 'ReleasedUtc', 'ReleaseState',
        'ReleaseJournalState', 'ReleaseBlocked', 'ReleaseBlockReason',
        'MutexReleased', 'ListenerInspectionSuccess',
        'ListenerInspectionKnown', 'ListenerHasListeners', 'ListenerAbsent',
        'ListenerProcessId', 'ListenerProcessStartTicks',
        'ListenerOwnerKind', 'ListenerOwnerProof', 'ListenerOwnerEvidence',
        'ListenerInspectionUtc', 'ProcessProofRequired',
        'ProcessTerminationProven', 'ProcessAbsent')
    $contextSnapshot = New-VerifierRunOwnedPreviewHttpSysProofFieldSnapshot `
        $Context $contextProperties
    $ownerSnapshot = New-VerifierRunOwnedPreviewHttpSysProofFieldSnapshot `
        $OwnerRecord $ownerProperties
    $leaseSnapshot = New-VerifierRunOwnedPreviewHttpSysProofFieldSnapshot `
        $OwnerRecord.Lease $leaseProperties
    if ($null -eq $contextSnapshot -or $null -eq $ownerSnapshot -or
            $null -eq $leaseSnapshot) { return $null }
    return [pscustomobject]@{
        Context = $contextSnapshot
        Owner = $ownerSnapshot
        Lease = $leaseSnapshot
        ContextServer = $Context.Server
        OwnerLease = $OwnerRecord.Lease
        OwnerProcess = $OwnerRecord.Process
    }
}

function New-VerifierRunOwnedPreviewHttpSysAuthorizationProof($Context,
        $OwnerRecord, $Port) {
    # The semantic handshake is intentionally performed exactly once at the
    # OS-listener boundary.  Later schema checks consume this opaque
    # reference, rather than repeating the expensive retained-server proof.
    if (-not (Test-VerifierRunOwnedPreviewHttpSysAuthorization $Context `
            $OwnerRecord $Port)) {
        return $null
    }
    $identity = New-VerifierRunOwnedPreviewHttpSysProofIdentity $Context `
        $OwnerRecord
    if ($null -eq $identity) { return $null }
    return [pscustomobject]@{
        Protocol = 'run-owned-preview-http-sys-proof-v1'
        Context = $Context
        Owner = $OwnerRecord
        Port = [int]$Port
        Authorized = $true
        Marker = $script:VerifierRunOwnedPreviewHttpSysProofMarker
        Identity = $identity
    }
}

function Test-VerifierRunOwnedPreviewHttpSysAuthorizationProof($Proof,
        $Context, $OwnerRecord, $Port) {
    if ($null -eq $Proof -or $null -eq $Context -or $null -eq $OwnerRecord -or
            -not (Test-VerifierStrictIntegralValue $Port 1 65535) -or
            $null -eq $Context.PSObject.Properties['Server'] -or
            $null -eq $OwnerRecord.PSObject.Properties['Lease'] -or
            $null -eq $OwnerRecord.PSObject.Properties['Process']) {
        return $false
    }
    if (-not ($Proof.PSObject.Properties['Protocol'] -and
        $Proof.Protocol -ceq 'run-owned-preview-http-sys-proof-v1' -and
        $Proof.PSObject.Properties['Authorized'] -and
        (Test-VerifierStrictBooleanValue $Proof.Authorized) -and
        [bool]$Proof.Authorized -and
        $Proof.PSObject.Properties['Context'] -and
        [object]::ReferenceEquals($Proof.Context, $Context) -and
        $Proof.PSObject.Properties['Owner'] -and
        [object]::ReferenceEquals($Proof.Owner, $OwnerRecord) -and
        $Proof.PSObject.Properties['Port'] -and
        (Test-VerifierStrictIntegralValue $Proof.Port 1 65535) -and
        [long]$Proof.Port -eq [long]$Port -and
        $Proof.PSObject.Properties['Marker'] -and
        [object]::ReferenceEquals($Proof.Marker,
            $script:VerifierRunOwnedPreviewHttpSysProofMarker) -and
        $Proof.PSObject.Properties['Identity'])) {
        return $false
    }
    $identity = $Proof.Identity
    if ($null -eq $identity -or
            -not [object]::ReferenceEquals($Context.Server, $OwnerRecord) -or
            -not [object]::ReferenceEquals($identity.ContextServer,
                $OwnerRecord) -or
            -not [object]::ReferenceEquals($identity.OwnerLease,
                $OwnerRecord.Lease) -or
            -not [object]::ReferenceEquals($identity.OwnerProcess,
                $OwnerRecord.Process) -or
            -not (Test-VerifierRunOwnedPreviewHttpSysProofFieldSnapshot `
                $Context $identity.Context @('Protocol', 'WorktreeRoot',
                    'RepositoryIdentity', 'RunId', 'PreviewNonce', 'RunRoot',
                    'RunNamespaceRoot', 'EvidenceDirectory',
                    'EvidenceNamespaceRoot', 'ManifestPath', 'PortLeaseRoot',
                    'CreatedUtc', 'BaseUrl', 'CleanupState',
                    'CleanupCompletedUtc', 'ManifestWritePhase')) -or
            -not (Test-VerifierRunOwnedPreviewHttpSysProofFieldSnapshot `
                $OwnerRecord $identity.Owner @('Owner', 'BaseUrl', 'Port',
                    'ProcessId', 'ProcessStartTicks',
                    'ProcessParentProcessId',
                    'ProcessParentProcessStartTicks', 'ProcessCommandLine',
                    'Script', 'RepositoryRoot', 'WebRoot',
                    'IdentityProtocol', 'IdentityVerified', 'CallerOwned',
                    'RunId', 'Nonce', 'State', 'ProcessIdentityKnown',
                    'OwnershipUncertain', 'ProcessTerminationProven',
                    'ProcessAbsent', 'ListenerInspectionProven',
                    'ListenerAbsent', 'StdoutLog', 'StderrLog',
                    'CleanupResult', 'Error', 'CdpPort', 'Profile',
                    'BrowserPath')) -or
            -not (Test-VerifierRunOwnedPreviewHttpSysProofFieldSnapshot `
                $OwnerRecord.Lease $identity.Lease @('Kind', 'Port', 'Status',
                    'ClaimState', 'LeaseId', 'ClaimName', 'Path',
                    'BoundProcessId', 'BoundProcessStartTicks', 'RunId',
                    'RepositoryIdentity', 'WorktreeRoot', 'ClaimOwnerPid',
                    'ClaimOwnerStartTicks', 'ClaimMutex', 'Registered',
                    'ProfilePath', 'BrowserPath', 'BindValidatedUtc',
                    'ReleasedUtc', 'ReleaseState', 'ReleaseJournalState',
                    'ReleaseBlocked', 'ReleaseBlockReason', 'MutexReleased',
                    'ListenerInspectionSuccess', 'ListenerInspectionKnown',
                    'ListenerHasListeners', 'ListenerAbsent',
                    'ListenerProcessId', 'ListenerProcessStartTicks',
                    'ListenerOwnerKind', 'ListenerOwnerProof',
                    'ListenerOwnerEvidence', 'ListenerInspectionUtc',
                    'ProcessProofRequired', 'ProcessTerminationProven',
                    'ProcessAbsent'))) {
        return $false
    }
    # The immutable owner tuple above prevents mutable-record reuse.  Re-read
    # the retained Process and the current process record as a separate
    # liveness/start-identity proof; this is deliberately not the semantic
    # HTTP.sys authorization handshake and therefore does not repeat it.
    try {
        $ownerProcess = $OwnerRecord.Process
        if ($null -eq $ownerProcess -or
                $ownerProcess.GetType() -ne [Diagnostics.Process]) {
            return $false
        }
        [void]$ownerProcess.Refresh()
        if ([bool]$ownerProcess.HasExited -or
                [int]$ownerProcess.Id -ne [int]$OwnerRecord.ProcessId -or
                (Get-VerifierProcessStartTicks $ownerProcess) -ne
                    [long]$OwnerRecord.ProcessStartTicks) {
            return $false
        }
        $currentRecord = Get-VerifierCurrentProcessRecordById `
            ([int]$OwnerRecord.ProcessId)
        if ($null -eq $currentRecord -or
                [int]$currentRecord.ProcessId -ne [int]$OwnerRecord.ProcessId -or
                [long]$currentRecord.ProcessStartTicks -ne
                    [long]$OwnerRecord.ProcessStartTicks) {
            return $false
        }
    } catch {
        return $false
    }
    return $true
}

function Test-VerifierListenerRecordSchema($Listener, $PreviewContext = $null,
        $PreviewOwner = $null, $AuthorizationProof = $null,
        [switch]$StructuralOnly) {
    if ($null -ne $PreviewContext) {
        try { [void](Assert-VerifierContextPreflight $PreviewContext 'listener record context') }
        catch { return $false }
    }
    if ($null -eq $Listener) { return $false }
    foreach ($propertyName in @('LocalAddress', 'Port', 'ProcessId',
            'ProcessStartTicks', 'Source', 'ListenerOwnerKind',
            'ListenerOwnerProof', 'ListenerOwnerEvidence')) {
        if (-not $Listener.PSObject.Properties[$propertyName]) { return $false }
    }
    if (-not (Test-VerifierStrictStringValue $Listener.LocalAddress) -or
            -not (Test-VerifierStrictStringValue $Listener.Source) -or
            -not (Test-VerifierStrictStringValue $Listener.ListenerOwnerKind) -or
            -not (Test-VerifierStrictStringValue $Listener.ListenerOwnerProof) -or
            -not (Test-VerifierStrictStringValue $Listener.ListenerOwnerEvidence) -or
            -not (Test-VerifierStrictIntegralValue $Listener.Port 1 65535) -or
            -not (Test-VerifierStrictIntegralValue $Listener.ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierLoopbackAddress $Listener.LocalAddress) -or
            $Listener.Source -cnotin @('netstat', 'Get-NetTCPConnection')) {
        return $false
    }
    if (-not (Test-VerifierListenerOwnerTuple $Listener.ListenerOwnerKind `
            $Listener.ListenerOwnerProof $Listener.ListenerOwnerEvidence `
            $Listener.ProcessId $Listener.ProcessStartTicks)) {
        return $false
    }
    if ($Listener.ListenerOwnerKind -ceq $script:VerifierKernelTransportOwnerKind) {
        if ($StructuralOnly) { return $true }
        if ($null -ne $AuthorizationProof) {
            return (Test-VerifierRunOwnedPreviewHttpSysAuthorizationProof `
                $AuthorizationProof $PreviewContext $PreviewOwner ([int]$Listener.Port))
        }
        if ($null -eq $PreviewContext -or $null -eq $PreviewOwner) {
            return $false
        }
        # A positive kernel listener is only consumable after the initial
        # listener boundary has carried its opaque semantic proof.  Do not
        # silently reauthorize at a later schema consumer.
        return $false
    }
    return $true
}

function Test-VerifierKernelTransportListenerRecord($Listener,
        $PreviewContext = $null, $PreviewOwner = $null,
        $AuthorizationProof = $null, [switch]$StructuralOnly) {
    if (-not (Test-VerifierListenerRecordSchema $Listener $PreviewContext `
            $PreviewOwner $AuthorizationProof -StructuralOnly:$StructuralOnly)) {
        return $false
    }
    return ($Listener.ListenerOwnerKind -ceq
        $script:VerifierKernelTransportOwnerKind)
}

function Test-VerifierRunOwnedPreviewHttpSysListener($Context, $OwnerRecord,
        $Listener, $AuthorizationProof = $null) {
    if (-not (Test-VerifierKernelTransportListenerRecord $Listener $Context `
            $OwnerRecord $AuthorizationProof -StructuralOnly)) {
        return $false
    }
    if ($null -ne $AuthorizationProof) {
        return (Test-VerifierRunOwnedPreviewHttpSysAuthorizationProof `
            $AuthorizationProof $Context $OwnerRecord ([int]$Listener.Port))
    }
    # The semantic handshake belongs only to the initial OS-listener
    # boundary. Every later listener consumer must carry its opaque proof;
    # absence is an authorization failure, never an invitation to reauthorize.
    return $false
}

function New-VerifierKernelTransportListenerRecord($LocalAddress, $Port, $Source,
        $PreviewContext = $null,
        $PreviewOwner = $null) {
    if (-not (Test-VerifierStrictStringValue $LocalAddress) -or
            -not (Test-VerifierStrictIntegralValue $Port 1 65535) -or
            -not (Test-VerifierStrictStringValue $Source) -or
            $Source -cnotin @('netstat', 'Get-NetTCPConnection') -or
            -not (Test-VerifierLoopbackAddress $LocalAddress)) {
        Throw-VerifierInfrastructure 'The kernel transport listener record did not come from a validated loopback listener query.'
    }
    # PID 4 is the Windows System process that owns HTTP.sys listeners on this
    # path. Read only the OS process name here; never read StartTime or create
    # a process identity for this non-process transport owner.
    $systemProcesses = @(Get-Process -Id 4 -ErrorAction SilentlyContinue)
    if ($systemProcesses.Count -ne 1 -or $null -eq $systemProcesses[0]) {
        Throw-VerifierInfrastructure "Loopback port $Port reported PID 4 but the OS System process could not be validated."
    }
    $systemProcess = $systemProcesses[0]
    $systemNameProperty = $systemProcess.PSObject.Properties['ProcessName']
    if ($null -eq $systemNameProperty) {
        $systemNameProperty = $systemProcess.PSObject.Properties['Name']
    }
    if ($null -eq $systemNameProperty -or
            -not (Test-VerifierStrictStringValue $systemNameProperty.Value)) {
        Throw-VerifierInfrastructure "Loopback port $Port reported PID 4 without an exact OS System process name."
    }
    $systemName = $systemNameProperty.Value
    if (-not $systemName.Equals('System', [StringComparison]::OrdinalIgnoreCase)) {
        Throw-VerifierInfrastructure "Loopback port $Port reported PID 4 without OS System/HTTP.sys ownership evidence."
    }
    $record = [pscustomobject]@{
        LocalAddress = $LocalAddress
        Port = $Port
        ProcessId = 4
        ProcessStartTicks = $null
        ListenerOwnerKind = $script:VerifierKernelTransportOwnerKind
        ListenerOwnerProof = $script:VerifierKernelTransportOwnerProof
        ListenerOwnerEvidence = $script:VerifierKernelTransportOwnerEvidence
        Source = $Source
    }
    if (-not (Test-VerifierKernelTransportListenerRecord $record `
            $PreviewContext $PreviewOwner -StructuralOnly)) {
        Throw-VerifierInfrastructure "Loopback port $Port produced an invalid kernel transport listener record."
    }
    return $record
}

function Get-VerifierListenerProcessRecord($ProcessId, $LocalAddress, $Port, $Source,
        $PreviewContext = $null,
        $PreviewOwner = $null) {
    if ($null -ne $PreviewContext) {
        [void](Assert-VerifierContextPreflight $PreviewContext 'listener process-record context')
    }
    if (-not (Test-VerifierStrictIntegralValue $ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictStringValue $LocalAddress) -or
            -not (Test-VerifierStrictIntegralValue $Port 1 65535) -or
            -not (Test-VerifierStrictStringValue $Source) -or
            $Source -cnotin @('netstat', 'Get-NetTCPConnection') -or
            -not (Test-VerifierLoopbackAddress $LocalAddress)) {
        Throw-VerifierInfrastructure "Loopback listener query returned an invalid PID for port $Port."
    }
    if ($ProcessId -eq 4) {
        return (New-VerifierKernelTransportListenerRecord $LocalAddress $Port $Source `
            $PreviewContext $PreviewOwner)
    }
    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        Throw-VerifierInfrastructure "Loopback port $Port is listening under a process that no longer exists; ownership is not provable."
    }
    $startTicks = Get-VerifierProcessStartTicks $process
    $record = [pscustomobject]@{
        LocalAddress = $LocalAddress
        Port = $Port
        ProcessId = $ProcessId
        ProcessStartTicks = $startTicks
        ListenerOwnerKind = $script:VerifierUserProcessOwnerKind
        ListenerOwnerProof = $script:VerifierUserProcessOwnerProof
        ListenerOwnerEvidence = $script:VerifierUserProcessOwnerEvidence
        Source = $Source
    }
    if (-not (Test-VerifierListenerRecordSchema $record)) {
        Throw-VerifierInfrastructure "Loopback port $Port produced an invalid user-process listener record."
    }
    return $record
}

function New-VerifierListenerInspection($Success, $Known, $Listeners, $Source,
        $ErrorMessage = '',
        $PreviewContext = $null, $PreviewOwner = $null,
        $AuthorizationProof = $null, [switch]$StructuralOnly) {
    if ($null -ne $PreviewContext) {
        [void](Assert-VerifierContextPreflight $PreviewContext 'listener inspection context')
    }
    if (-not (Test-VerifierStrictBooleanValue $Success) -or -not $Success -or
            -not (Test-VerifierStrictBooleanValue $Known) -or -not $Known -or
            $null -eq $Listeners -or -not ($Listeners -is [array]) -or
            -not (Test-VerifierStrictStringValue $Source) -or
            -not (Test-VerifierStrictStringValue $ErrorMessage) -or
            $ErrorMessage -cne '') {
        Throw-VerifierInfrastructure 'Listener inspection used a malformed success, source, error, or listener collection field.'
    }
    # `$null` is not an observation. Only a caller that explicitly supplied an
    # empty array may prove listener absence.
    $normalized = @($Listeners)
    foreach ($listener in $normalized) {
        if ($null -eq $listener -or
                -not (Test-VerifierListenerRecordSchema $listener $PreviewContext `
                    $PreviewOwner $AuthorizationProof -StructuralOnly:$StructuralOnly)) {
            Throw-VerifierInfrastructure "Listener inspection '$Source' returned a malformed or unauthorized listener owner record."
        }
    }
    $ownerKind = if ($normalized.Count -eq 0) { 'none' } else {
        $normalized[0].ListenerOwnerKind
    }
    $ownerProof = if ($normalized.Count -eq 0) { '' } else {
        $normalized[0].ListenerOwnerProof
    }
    $ownerEvidenceValue = if ($normalized.Count -eq 0) { '' } else {
        $normalized[0].ListenerOwnerEvidence
    }
    $inspection = [pscustomobject]@{
        Success = $Success
        Known = $Known
        HasListeners = ($normalized.Count -gt 0)
        Listeners = $normalized
        ListenerOwnerKind = $ownerKind
        ListenerOwnerProof = $ownerProof
        ListenerOwnerEvidence = $ownerEvidenceValue
        Source = $Source
        Error = $ErrorMessage
    }
    if (-not (Test-VerifierListenerInspectionSchema $inspection $PreviewContext `
            $PreviewOwner $AuthorizationProof -StructuralOnly:$StructuralOnly)) {
        Throw-VerifierInfrastructure "Listener inspection '$Source' returned a malformed, inconsistent, duplicate, or unauthorized result."
    }
    return $inspection
}

function Parse-VerifierNetstatListenerOutput($Port, [object[]]$Output,
        $ExitCode = 0, $PreviewContext = $null,
        $PreviewOwner = $null) {
    if ($null -ne $PreviewContext) {
        [void](Assert-VerifierContextPreflight $PreviewContext 'netstat listener context')
    }
    if (-not (Test-VerifierStrictIntegralValue $Port 1 65535)) {
        Throw-VerifierInfrastructure "Cannot inspect invalid loopback port $Port."
    }
    # Keep the process exit result raw at this boundary.  PowerShell parameter
    # binding must not turn a numeric string, fraction, Boolean, array, object,
    # or null into an apparently valid integral exit code.
    if (-not (Test-VerifierStrictIntegralValue $ExitCode 0 0)) {
        Throw-VerifierInfrastructure "netstat.exe returned a malformed non-integral exit code while inspecting loopback port $Port."
    }
    if ($ExitCode -ne 0) {
        Throw-VerifierInfrastructure "netstat.exe returned exit code $ExitCode while inspecting loopback port $Port."
    }
    $lines = @($Output)
    if ($lines.Count -eq 0) {
        Throw-VerifierInfrastructure "netstat.exe returned no output while inspecting loopback port $Port."
    }
    $records = New-Object Collections.ArrayList
    $sawDataLine = $false
    $sawTableHeader = $false
    foreach ($rawLine in $lines) {
        if ($null -eq $rawLine -or $rawLine.GetType() -ne [string]) {
            Throw-VerifierInfrastructure "netstat.exe returned a non-string output record while inspecting loopback port $Port."
        }
        $line = $rawLine.Trim()
        if ([String]::IsNullOrWhiteSpace($line)) { continue }
        if ($line -match '^(?i:Active Connections)$') {
            continue
        }
        if ($line -match '^(?i:Proto\s+Local Address\s+Foreign Address\s+State(?:\s+PID)?)$') {
            $sawTableHeader = $true
            continue
        }
        if ($line -match '(?i)error|access denied|not recognized|invalid|failed|cannot') {
            Throw-VerifierInfrastructure "netstat.exe returned error text while inspecting loopback port ${Port}: $line"
        }
        if ($line -notmatch '^(?i:TCP)\s+') {
            Throw-VerifierInfrastructure "netstat.exe returned an unrecognized line while inspecting loopback port ${Port}: $line"
        }
        $match = [regex]::Match($line,
            '^\s*TCP\s+(?<local>\S+):(?<localPort>\d+)\s+(?<foreign>\S+):(?<foreignPort>\d+)\s+(?<state>\S+)\s+(?<pid>\d+)\s*$')
        if (-not $match.Success) {
            Throw-VerifierInfrastructure "netstat.exe returned a malformed TCP record while inspecting loopback port ${Port}: $line"
        }
        $localPortText = $match.Groups['localPort'].Value
        $foreignPortText = $match.Groups['foreignPort'].Value
        $pidText = $match.Groups['pid'].Value
        $localPort = 0
        $foreignPort = 0
        $listenerPid = 0
        if (-not [int]::TryParse($localPortText,
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$localPort) -or
            -not [int]::TryParse($foreignPortText,
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$foreignPort) -or
            -not [int]::TryParse($pidText,
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$listenerPid) -or
            $localPort -lt 1 -or $localPort -gt 65535 -or
            $foreignPort -lt 0 -or $foreignPort -gt 65535 -or
            $listenerPid -lt 0 -or $listenerPid -gt [int]::MaxValue) {
            Throw-VerifierInfrastructure "netstat.exe returned an out-of-range integral TCP field while inspecting loopback port ${Port}: $line"
        }
        $sawDataLine = $true
        if ($localPort -ne $Port -or
                $match.Groups['state'].Value -notmatch '^(?i:LISTENING)$') { continue }
        $address = $match.Groups['local'].Value
        if (-not (Test-VerifierLoopbackAddress $address)) { continue }
        if ($listenerPid -le 0) {
            Throw-VerifierInfrastructure "netstat.exe returned a listening TCP record without a positive owning PID while inspecting loopback port ${Port}: $line"
        }
        [void]$records.Add((Get-VerifierListenerProcessRecord `
            $listenerPid $address $Port 'netstat' `
            $PreviewContext $PreviewOwner))
    }
    if (-not $sawDataLine -and -not $sawTableHeader) {
        Throw-VerifierInfrastructure "netstat.exe returned no complete TCP records while inspecting loopback port $Port."
    }
    return (New-VerifierListenerInspection $true $true @($records) 'netstat' '' `
        $PreviewContext $PreviewOwner $null -StructuralOnly)
}

function Get-VerifierLoopbackListenerRecords($Port,
        $PreviewContext = $null, $PreviewOwner = $null,
        [switch]$PreferNetstat) {
    if ($null -ne $PreviewContext) {
        [void](Assert-VerifierContextPreflight $PreviewContext 'loopback listener context')
    }
    if (-not (Test-VerifierStrictIntegralValue $Port 1 65535)) {
        Throw-VerifierInfrastructure "Cannot inspect invalid loopback port $Port."
    }
    $netConnectionError = $null
    $netConnectionSucceeded = $false
    $records = New-Object Collections.ArrayList
    # The normal route remains Get-NetTCPConnection-first. Strict 500ms
    # ownership proofs may opt into the narrower netstat process route when
    # the provider's startup/query latency would consume the entire budget.
    if (-not $PreferNetstat) {
        $netConnectionCommand = Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue
    }
    if (-not $PreferNetstat -and $null -ne $netConnectionCommand) {
        try {
            $connections = @(& $netConnectionCommand -LocalPort $Port -State Listen -ErrorAction Stop)
            $netConnectionSucceeded = $true
        } catch {
            $netConnectionError = Get-VerifierErrorMessage $_
        }
    }
    if ($netConnectionSucceeded) {
        if ($connections.Count -eq 0) {
            # Get-NetTCPConnection is an authoritative successful OS query on
            # hosts where it is available.  Its exact empty result is explicit
            # listener absence; do not require a second, less authoritative
            # query to turn that absence into proof.
            return (New-VerifierListenerInspection $true $true @() `
                'Get-NetTCPConnection' '' $PreviewContext $PreviewOwner $null -StructuralOnly)
        }
        foreach ($connection in $connections) {
            if ($null -eq $connection) {
                Throw-VerifierInfrastructure "Get-NetTCPConnection returned an incomplete listener record for port $Port."
            }
            $localAddressProperty = $connection.PSObject.Properties['LocalAddress']
            $localPortProperty = $connection.PSObject.Properties['LocalPort']
            $owningProcessProperty = $connection.PSObject.Properties['OwningProcess']
            $stateProperty = $connection.PSObject.Properties['State']
            if ($null -eq $localAddressProperty -or
                    $null -eq $localPortProperty -or
                    $null -eq $owningProcessProperty -or
                    $null -eq $stateProperty) {
                Throw-VerifierInfrastructure "Get-NetTCPConnection returned an incomplete listener record for port $Port."
            }
            # Keep the OS query boundary untyped.  Validate the raw scalar
            # values before comparing/casting them or looking up the owning
            # process; an implicit cast would turn a fractional or string port
            # into an apparently valid listener identity.
            $rawLocalAddress = $localAddressProperty.Value
            $rawLocalPort = $localPortProperty.Value
            $rawOwningProcess = $owningProcessProperty.Value
            $rawState = $stateProperty.Value
            if (-not (Test-VerifierStrictStringValue $rawLocalAddress) -or
                    -not (Test-VerifierStrictIntegralValue $rawLocalPort 1 65535) -or
                    -not (Test-VerifierStrictIntegralValue $rawOwningProcess 1 ([int]::MaxValue)) -or
                    -not (Test-VerifierRawNetTcpListenState $rawState)) {
                Throw-VerifierInfrastructure "Get-NetTCPConnection returned malformed raw listener scalar fields for port $Port."
            }
            if ([long]$rawLocalPort -ne [long]$Port -or
                    ([string]$rawState) -cnotmatch '^(?i:Listen|Listening)$') {
                Throw-VerifierInfrastructure "Get-NetTCPConnection returned an inconsistent listener record for port $Port."
            }
            $address = $rawLocalAddress.Trim()
            $parsedAddress = $null
            if (-not [Net.IPAddress]::TryParse($address, [ref]$parsedAddress)) {
                Throw-VerifierInfrastructure "Get-NetTCPConnection returned a malformed LocalAddress for port $Port."
            }
            if (-not (Test-VerifierLoopbackAddress $address)) { continue }
            [void]$records.Add((Get-VerifierListenerProcessRecord $rawOwningProcess `
                $address $Port 'Get-NetTCPConnection' $PreviewContext $PreviewOwner))
        }
        return (New-VerifierListenerInspection $true $true @($records) 'Get-NetTCPConnection' '' `
            $PreviewContext $PreviewOwner $null -StructuralOnly)
    }

    # Get-NetTCPConnection is access-controlled on some Windows hosts. The
    # netstat PID table is a narrower fallback, but malformed, empty, or
    # error-text output is unknown rather than proof that the port is free.
    $netstatCommand = Get-Command netstat.exe -ErrorAction SilentlyContinue
    if ($null -eq $netstatCommand) {
        $detail = if ($netConnectionError) { " ($netConnectionError)" } else { '' }
        Throw-VerifierInfrastructure "Could not inspect loopback listeners for port ${Port}: netstat.exe is unavailable$detail."
    }
    try {
        $netstatResult = Invoke-VerifierBoundedProcess $netstatCommand.Source `
            @('-ano', '-p', 'tcp') 5000
    } catch {
        Throw-VerifierInfrastructure "Could not inspect loopback listeners for port ${Port}: $(Get-VerifierErrorMessage $_)"
    }
    $netstatOutputList = New-Object Collections.ArrayList
    foreach ($line in ([string]$netstatResult.Stdout -split "`r?`n")) {
        [void]$netstatOutputList.Add($line)
    }
    foreach ($line in ([string]$netstatResult.Stderr -split "`r?`n")) {
        [void]$netstatOutputList.Add($line)
    }
    try {
        return (Parse-VerifierNetstatListenerOutput $Port @($netstatOutputList) $netstatResult.ExitCode `
            $PreviewContext $PreviewOwner)
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        $detail = if ($netConnectionError) { " Previous query: $netConnectionError." } else { '' }
        Throw-VerifierInfrastructure ("Could not inspect loopback listeners for port ${Port}: " +
            (Get-VerifierErrorMessage $_) + $detail)
    }
}

function Test-VerifierChildPath([string]$Root, [string]$Candidate) {
    return Test-VerifierPhysicalChildPath $Root $Candidate
}

function Get-VerifierManifestBooleanValue($Object, [string]$Name,
        [switch]$AllowNull) {
    if ($null -eq $Object -or [String]::IsNullOrWhiteSpace($Name) -or
            $null -eq $Object.PSObject.Properties[$Name]) {
        Throw-VerifierInfrastructure "Verifier manifest source omitted required Boolean field '$Name'."
    }
    $value = $Object.PSObject.Properties[$Name].Value
    if ($null -eq $value) {
        if ($AllowNull) { return $null }
        Throw-VerifierInfrastructure "Verifier manifest source field '$Name' was null instead of an exact Boolean."
    }
    if ($value.GetType() -ne [bool]) {
        Throw-VerifierInfrastructure "Verifier manifest source field '$Name' was not an exact Boolean."
    }
    return $value
}

function Get-VerifierManifestView($Context) {
    # Manifest fields are durable ownership evidence.  Validate the complete
    # tuple before projecting any lease into JSON; missing legacy fields must
    # never become serialized nulls.
    Assert-VerifierDurableManifestContext $Context
    $leases = @($Context.LeaseRecords | ForEach-Object {
        [ordered]@{
            leaseId = $_.LeaseId
            kind = $_.Kind
            port = $_.Port
            path = $_.Path
            runId = $Context.RunId
            repositoryIdentity = $Context.RepositoryIdentity
            worktreeRoot = $Context.WorktreeRoot
            status = $_.Status
            claimName = $_.ClaimName
            claimState = $_.ClaimState
            claimOwnerPid = $_.ClaimOwnerPid
            claimOwnerStartTicks = $_.ClaimOwnerStartTicks
             profile = $_.ProfilePath
             browserPath = $_.BrowserPath
            claim = [ordered]@{
                protocol = 'troubleshootjs-verifier-port-claim-v1'
                runId = $Context.RunId
                repositoryIdentity = $Context.RepositoryIdentity
                worktreeRoot = $Context.WorktreeRoot
                leaseId = $_.LeaseId
                path = $_.Path
                kind = $_.Kind
                port = $_.Port
                mutexName = $_.ClaimName
                ownerPid = $_.ClaimOwnerPid
                ownerStartTicks = $_.ClaimOwnerStartTicks
            }
            registered = Get-VerifierManifestBooleanValue $_ 'Registered'
            boundProcessId = $_.BoundProcessId
            boundProcessStartTicks = $_.BoundProcessStartTicks
            listenerProcessId = $_.ListenerProcessId
            listenerProcessStartTicks = $_.ListenerProcessStartTicks
            listenerOwnerKind = $_.ListenerOwnerKind
            listenerOwnerProof = $_.ListenerOwnerProof
            listenerOwnerEvidence = $_.ListenerOwnerEvidence
            bindValidatedUtc = $_.BindValidatedUtc
             releasedUtc = $_.ReleasedUtc
             releaseState = $_.ReleaseState
             releaseJournalState = $_.ReleaseJournalState
             releaseBlocked = Get-VerifierManifestBooleanValue $_ 'ReleaseBlocked'
              releaseBlockReason = $_.ReleaseBlockReason
             mutexReleased = Get-VerifierManifestBooleanValue $_ 'MutexReleased'
             listenerInspectionSuccess = Get-VerifierManifestBooleanValue $_ 'ListenerInspectionSuccess'
             listenerInspectionKnown = Get-VerifierManifestBooleanValue $_ 'ListenerInspectionKnown'
             listenerHasListeners = Get-VerifierManifestBooleanValue $_ 'ListenerHasListeners' -AllowNull
             listenerAbsent = Get-VerifierManifestBooleanValue $_ 'ListenerAbsent' -AllowNull
              listenerInspectionUtc = $_.ListenerInspectionUtc
             processTerminationProven = Get-VerifierManifestBooleanValue $_ 'ProcessTerminationProven'
             processAbsent = Get-VerifierManifestBooleanValue $_ 'ProcessAbsent'
             processProofRequired = Get-VerifierManifestBooleanValue $_ 'ProcessProofRequired'
         }
    })
    $sessions = @($Context.BrowserSessions | ForEach-Object {
        [ordered]@{
            owner = 'run'
            runId = $_.RunId
            repositoryIdentity = $_.RepositoryIdentity
            worktreeRoot = $Context.WorktreeRoot
            routeId = $_.RouteId
            routeName = $_.RouteName
             cdpPort = $_.CdpPort
              browserPath = $_.BrowserPath
             cdpLeasePath = $_.Lease.Path
             profile = $_.Profile
             processId = $_.ProcessId
             processStartTicks = $_.ProcessStartTicks
              processParentProcessId = $_.ProcessParentProcessId
              processParentProcessStartTicks = $_.ProcessParentProcessStartTicks
             processCommandLine = $_.ProcessCommandLine
            targetId = $_.TargetId
            expectedUrl = $_.ExpectedUrl
            expectedRunMarker = 'tsjVerifierRun=' + $_.RunId
            expectedRouteMarker = 'tsjVerifierRoute=' + $_.RouteId
            status = $_.Status
            cleanupResult = $_.CleanupResult
            profileProcessScanCompleted = Get-VerifierManifestBooleanValue $_ 'ProfileProcessScanCompleted'
            profileInspectionFailed = Get-VerifierManifestBooleanValue $_ 'ProfileInspectionFailed'
            error = $_.Error
        }
    })
    $server = if ($null -eq $Context.Server) { $null } else {
        $serverLease = if ($Context.Server.PSObject.Properties['Lease']) {
            $Context.Server.Lease
        } else { $null }
        [ordered]@{
            owner = $Context.Server.Owner
            baseUrl = $Context.Server.BaseUrl
            repositoryIdentity = $Context.RepositoryIdentity
            worktreeRoot = $Context.WorktreeRoot
            port = $Context.Server.Port
             processId = $Context.Server.ProcessId
             processStartTicks = $Context.Server.ProcessStartTicks
              repositoryRoot = $Context.Server.RepositoryRoot
              webRoot = $Context.Server.WebRoot
              identityProtocol = $Context.Server.IdentityProtocol
             identityVerified = Get-VerifierManifestBooleanValue $Context.Server 'IdentityVerified'
             callerOwned = Get-VerifierManifestBooleanValue $Context.Server 'CallerOwned'
               processParentProcessId = $Context.Server.ProcessParentProcessId
               processParentProcessStartTicks = $Context.Server.ProcessParentProcessStartTicks
             processCommandLine = $Context.Server.ProcessCommandLine
             script = $Context.Server.Script
             runId = $Context.Server.RunId
             nonce = $Context.Server.Nonce
              leaseId = if ($null -ne $serverLease) { $serverLease.LeaseId } else { '' }
              leaseKind = if ($null -ne $serverLease) { $serverLease.Kind } else { '' }
              leaseClaimName = if ($null -ne $serverLease) { $serverLease.ClaimName } else { '' }
              leaseClaimState = if ($null -ne $serverLease) { $serverLease.ClaimState } else { '' }
              leaseReleaseState = if ($null -ne $serverLease) { $serverLease.ReleaseState } else { '' }
              leaseReleaseJournalState = if ($null -ne $serverLease) { $serverLease.ReleaseJournalState } else { '' }
              leaseOwnerPid = if ($null -ne $serverLease) { $serverLease.ClaimOwnerPid } else { 0 }
              leaseOwnerStartTicks = if ($null -ne $serverLease) { $serverLease.ClaimOwnerStartTicks } else { 0 }
              leasePath = if ($null -ne $serverLease) { $serverLease.Path } else { '' }
              leaseListenerAbsent = if ($null -ne $serverLease) {
                  Get-VerifierManifestBooleanValue $serverLease 'ListenerAbsent' -AllowNull
              } else { $null }
              leaseProcessProofRequired = if ($null -ne $serverLease) {
                  Get-VerifierManifestBooleanValue $serverLease 'ProcessProofRequired'
              } else { $null }
              leaseListenerOwnerKind = if ($null -ne $serverLease) {
                  $serverLease.ListenerOwnerKind
              } else { 'none' }
              leaseListenerOwnerProof = if ($null -ne $serverLease) {
                  $serverLease.ListenerOwnerProof
              } else { '' }
              leaseListenerOwnerEvidence = if ($null -ne $serverLease) {
                  $serverLease.ListenerOwnerEvidence
              } else { '' }
            state = $Context.Server.State
            stdoutLog = $Context.Server.StdoutLog
            stderrLog = $Context.Server.StderrLog
             cleanupResult = $Context.Server.CleanupResult
             error = $Context.Server.Error
             processIdentityKnown = Get-VerifierManifestBooleanValue $Context.Server 'ProcessIdentityKnown'
             ownershipUncertain = Get-VerifierManifestBooleanValue $Context.Server 'OwnershipUncertain'
             processTerminationProven = Get-VerifierManifestBooleanValue $Context.Server 'ProcessTerminationProven'
             processAbsent = Get-VerifierManifestBooleanValue $Context.Server 'ProcessAbsent'
             listenerInspectionProven = Get-VerifierManifestBooleanValue $Context.Server 'ListenerInspectionProven'
             listenerAbsent = Get-VerifierManifestBooleanValue $Context.Server 'ListenerAbsent' -AllowNull
         }
    }
    return [ordered]@{
        protocol = 'troubleshootjs-verifier-run-v1'
        runId = $Context.RunId
        repositoryIdentity = $Context.RepositoryIdentity
        worktreeRoot = $Context.WorktreeRoot
            runRoot = $Context.RunRoot
        runNamespaceRoot = $Context.RunNamespaceRoot
        evidenceDirectory = $Context.EvidenceDirectory
        evidenceNamespaceRoot = $Context.EvidenceNamespaceRoot
        manifestPath = $Context.ManifestPath
        createdUtc = $Context.CreatedUtc
        baseUrl = $Context.BaseUrl
        server = $server
        previewNonce = $Context.PreviewNonce
        leases = $leases
        browserSessions = $sessions
        artifacts = @($Context.Artifacts)
        cleanup = [ordered]@{
            state = $Context.CleanupState
            completedUtc = $Context.CleanupCompletedUtc
            errors = @($Context.CleanupErrors)
        }
    }
}

function Assert-VerifierContextPhysicalResources($Context) {
    [void](Assert-VerifierContextPreflight $Context 'verifier physical-resource context' -RequireDurable)
    # A live browser profile is mutable and may contain open Chromium files
    # whose final physical path cannot be opened while Edge is running. Keep
    # the canonical/ancestor ownership checks for manifest writes, but defer
    # recursive profile/run-root enumeration until browser cleanup has drained
    # the exact owned process tree.
    $mutableBrowserProfile = $false
    foreach ($session in @($Context.BrowserSessions)) {
        if ($null -eq $session -or
                -not $session.PSObject.Properties['Profile'] -or
                [String]::IsNullOrWhiteSpace([string]$session.Profile)) {
            continue
        }
        $cleanupResultProperty = $session.PSObject.Properties['CleanupResult']
        if ($null -eq $cleanupResultProperty -or
                [string]$cleanupResultProperty.Value -cne 'complete') {
            $mutableBrowserProfile = $true
            break
        }
    }
    if (-not $mutableBrowserProfile) {
        foreach ($lease in @($Context.LeaseRecords)) {
            if ($null -eq $lease -or
                    -not $lease.PSObject.Properties['ProfilePath'] -or
                    [String]::IsNullOrWhiteSpace([string]$lease.ProfilePath)) {
                continue
            }
            $releaseStateProperty = $lease.PSObject.Properties['ReleaseState']
            if ($null -eq $releaseStateProperty -or
                    [string]$releaseStateProperty.Value -notin @('os-released',
                        'complete', 'claim-delete-failed')) {
                $mutableBrowserProfile = $true
                break
            }
        }
    }
    $validateProfileTrees = -not $mutableBrowserProfile
    [void](Assert-VerifierPhysicalOwnedPath $Context.WorktreeRoot $Context.WorktreeRoot `
        -AllowRoot)
    if ($mutableBrowserProfile) {
        [void](Assert-VerifierPhysicalOwnedPath $Context.RunNamespaceRoot $Context.RunRoot)
    } else {
        [void](Assert-VerifierPhysicalOwnedPath $Context.RunNamespaceRoot $Context.RunRoot `
            -ValidateTree)
    }
    [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $Context.ManifestPath)
    [void](Assert-VerifierPhysicalOwnedPath $Context.EvidenceNamespaceRoot $Context.EvidenceDirectory `
        -ValidateTree)
    [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $Context.PortLeaseRoot `
        -ValidateTree)
    if (-not $mutableBrowserProfile -and
            (Test-Path -LiteralPath $Context.RunRoot -PathType Container -ErrorAction Stop)) {
        Assert-VerifierNoReparseTree $Context.RunRoot
    }
    foreach ($lease in @($Context.LeaseRecords)) {
        if ($null -eq $lease) { continue }
        if (-not [String]::IsNullOrWhiteSpace([string]$lease.Path) -and
                -not (Assert-VerifierPhysicalOwnedPath $Context.PortLeaseRoot $lease.Path)) {
            Throw-VerifierInfrastructure "Verifier lease path escaped its physical claim namespace: $($lease.Path)"
        }
        if ($lease.PSObject.Properties['ProfilePath'] -and
                -not [String]::IsNullOrWhiteSpace([string]$lease.ProfilePath)) {
            if ($validateProfileTrees) {
                if (-not (Assert-VerifierPhysicalOwnedPath $Context.RunRoot $lease.ProfilePath `
                        -ValidateTree)) {
                    Throw-VerifierInfrastructure "Verifier lease profile escaped its physical run namespace: $($lease.ProfilePath)"
                }
            } else {
                if (-not (Assert-VerifierPhysicalOwnedPath $Context.RunRoot $lease.ProfilePath)) {
                    Throw-VerifierInfrastructure "Verifier lease profile escaped its physical run namespace: $($lease.ProfilePath)"
                }
            }
        }
    }
    foreach ($session in @($Context.BrowserSessions)) {
        if ($null -ne $session -and
                -not [String]::IsNullOrWhiteSpace([string]$session.Profile)) {
            if ($validateProfileTrees) {
                if (-not (Assert-VerifierPhysicalOwnedPath $Context.RunRoot $session.Profile `
                        -ValidateTree)) {
                    Throw-VerifierInfrastructure "Verifier browser profile escaped its physical run namespace: $($session.Profile)"
                }
            } else {
                if (-not (Assert-VerifierPhysicalOwnedPath $Context.RunRoot $session.Profile)) {
                    Throw-VerifierInfrastructure "Verifier browser profile escaped its physical run namespace: $($session.Profile)"
                }
            }
        }
    }
    if ($null -ne $Context.Server) {
        $serverRepositoryRoot = [string]$Context.Server.RepositoryRoot
        if (-not [String]::IsNullOrWhiteSpace($serverRepositoryRoot)) {
            if (-not (Test-VerifierCanonicalWindowsPathValue $serverRepositoryRoot $Context.WorktreeRoot) -or
                    -not (Assert-VerifierPhysicalOwnedPath $Context.WorktreeRoot $serverRepositoryRoot `
                        -AllowRoot)) {
                Throw-VerifierInfrastructure "Verifier preview repository root did not match the physical worktree: $serverRepositoryRoot"
            }
        }
        foreach ($serverPath in @(
            [string]$Context.Server.Script,
            [string]$Context.Server.WebRoot)) {
            if (-not [String]::IsNullOrWhiteSpace($serverPath)) {
                # The compiled `war` tree can contain a very large generated
                # artifact set.  A full recursive walk on every manifest
                # write would make ordinary single-run invocation appear to
                # hang.  Physical final-path/ancestor checks are still
                # mandatory here; preview.ps1 rechecks the exact web file and
                # its ancestors immediately before each serve.  Full tree
                # validation remains reserved for run-owned namespaces.
                $validateWebTree = $false
                if ($validateWebTree) {
                    [void](Assert-VerifierPhysicalOwnedPath $Context.WorktreeRoot $serverPath -ValidateTree)
                } else {
                    [void](Assert-VerifierPhysicalOwnedPath $Context.WorktreeRoot $serverPath)
                }
            }
        }
        $serverLogRoot = Get-VerifierCanonicalWindowsPath (Join-Path $Context.RunRoot 'server')
        foreach ($serverLog in @([string]$Context.Server.StdoutLog,
                [string]$Context.Server.StderrLog)) {
            if (-not [String]::IsNullOrWhiteSpace($serverLog) -and
                    -not (Assert-VerifierPhysicalOwnedPath $serverLogRoot $serverLog)) {
                Throw-VerifierInfrastructure "Verifier preview log escaped its physical server namespace: $serverLog"
            }
        }
    }
    foreach ($artifact in @($Context.Artifacts)) {
        if (-not [String]::IsNullOrWhiteSpace([string]$artifact) -and
                -not (Assert-VerifierPhysicalOwnedPath $Context.EvidenceDirectory $artifact)) {
            Throw-VerifierInfrastructure "Verifier evidence artifact escaped its physical evidence namespace: $artifact"
        }
    }
}

function Write-VerifierManifest($Context) {
    [void](Assert-VerifierContextPreflight $Context 'verifier manifest context')
    if ($null -eq $Context -or [String]::IsNullOrWhiteSpace([string]$Context.RunRoot)) {
        Throw-VerifierInfrastructure 'Cannot persist a verifier manifest without a run root.'
    }
    # Reject malformed owner evidence before deriving a temporary path or
    # entering the write/rename transaction.  The view repeats this guard for
    # callers that consume it directly.
    Assert-VerifierDurableManifestContext $Context
    # Revalidate the physical namespace immediately before every manifest
    # write.  This catches a junction/reparse replacement between setup and a
    # later lifecycle operation instead of following it with Move-Item.
    Assert-VerifierNoReparseAncestors $Context.RunRoot
    if (-not (Test-VerifierPhysicalChildPath $Context.RunRoot $Context.ManifestPath)) {
        Throw-VerifierInfrastructure 'Verifier manifest path was outside the physical run namespace.'
    }
    $evidenceNamespaceRoot = if ($Context.PSObject.Properties['EvidenceNamespaceRoot'] -and
            -not [String]::IsNullOrWhiteSpace([string]$Context.EvidenceNamespaceRoot)) {
        [string]$Context.EvidenceNamespaceRoot
    } else { [string]$Context.RunRoot }
    Assert-VerifierNoReparseAncestors $evidenceNamespaceRoot
    if (-not (Test-VerifierPhysicalChildPath $evidenceNamespaceRoot $Context.EvidenceDirectory)) {
        Throw-VerifierInfrastructure 'Verifier evidence namespace was outside its physical evidence parent.'
    }
    if (-not (Test-VerifierPhysicalChildPath $Context.RunRoot $Context.PortLeaseRoot)) {
        Throw-VerifierInfrastructure 'Verifier claim namespace was outside the physical run namespace.'
    }
    Assert-VerifierContextPhysicalResources $Context
    $manifest = Get-VerifierManifestView $Context
    $temporaryPath = $Context.ManifestPath + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
    try {
        if ($Context.PSObject.Properties['TestHooks'] -and
                $Context.TestHooks.PSObject.Properties['FailNextManifestWrite'] -and
                [bool]$Context.TestHooks.FailNextManifestWrite) {
            $Context.TestHooks.FailNextManifestWrite = $false
            Throw-VerifierInfrastructure 'Injected manifest-write failure.'
        }
        if ($Context.PSObject.Properties['TestHooks'] -and
                $Context.TestHooks.PSObject.Properties['FailNextFinalManifestWrite'] -and
                [bool]$Context.TestHooks.FailNextFinalManifestWrite -and
                $Context.PSObject.Properties['ManifestWritePhase'] -and
                [string]$Context.ManifestWritePhase -eq 'lease-final') {
            $Context.TestHooks.FailNextFinalManifestWrite = $false
            Throw-VerifierInfrastructure 'Injected final lease-manifest failure.'
        }
        if ($Context.PSObject.Properties['TestHooks'] -and
                $Context.TestHooks.PSObject.Properties['FailNextPostDeleteFinalManifestWrite'] -and
                [bool]$Context.TestHooks.FailNextPostDeleteFinalManifestWrite -and
                $Context.PSObject.Properties['ManifestWritePhase'] -and
                [string]$Context.ManifestWritePhase -eq 'lease-post-delete') {
            $Context.TestHooks.FailNextPostDeleteFinalManifestWrite = $false
            Throw-VerifierInfrastructure 'Injected post-delete final lease-manifest failure.'
        }
        if ($Context.PSObject.Properties['TestHooks'] -and
                $Context.TestHooks.PSObject.Properties['FailNextPostDeleteJournalWrite'] -and
                [bool]$Context.TestHooks.FailNextPostDeleteJournalWrite -and
                $Context.PSObject.Properties['ManifestWritePhase'] -and
                [string]$Context.ManifestWritePhase -eq 'lease-post-delete-journal') {
            $Context.TestHooks.FailNextPostDeleteJournalWrite = $false
            Throw-VerifierInfrastructure 'Injected post-delete journal lease-manifest failure.'
        }
        $json = $manifest | ConvertTo-Json -Depth 12
        if (-not (Test-VerifierPhysicalChildPath $Context.RunRoot $temporaryPath) -or
                -not (Test-VerifierPhysicalChildPath $Context.RunRoot $Context.ManifestPath)) {
            Throw-VerifierInfrastructure 'Verifier manifest destination changed physical ownership before write.'
        }
        Assert-VerifierContextPhysicalResources $Context
        [IO.File]::WriteAllText($temporaryPath, $json,
            [Text.UTF8Encoding]::new($false))
        if (-not (Test-VerifierPhysicalChildPath $Context.RunRoot $temporaryPath) -or
                -not (Test-VerifierPhysicalChildPath $Context.RunRoot $Context.ManifestPath)) {
            Throw-VerifierInfrastructure 'Verifier manifest destination changed physical ownership before commit.'
        }
        Assert-VerifierContextPhysicalResources $Context
        Move-Item -LiteralPath $temporaryPath -Destination $Context.ManifestPath -Force
    } catch {
        try {
            if (Test-VerifierPhysicalChildPath $Context.RunRoot $temporaryPath) {
                Remove-VerifierOwnedTree $Context.RunRoot $temporaryPath
            }
        } catch { }
        Throw-VerifierInfrastructure ("Could not write run manifest: " +
            (Get-VerifierErrorMessage $_))
    }
}

function Write-VerifierSetupFailureRecord([string]$RunRoot, [string]$ManifestPath,
        [string]$RunId, [string]$Message) {
    if ([String]::IsNullOrWhiteSpace($RunRoot)) { return }
    try {
        if (-not (Test-Path -LiteralPath $RunRoot -PathType Container)) { return }
        [void](Assert-VerifierPhysicalOwnedPath $RunRoot $RunRoot -AllowRoot)
        $setupFailureRecord = [ordered]@{
            protocol = 'troubleshootjs-verifier-setup-failure-v1'
            runId = $RunId
            runRoot = $RunRoot
            manifestPath = $ManifestPath
            failedUtc = Get-VerifierUtcText
            error = $Message
        }
        $path = Join-Path $RunRoot 'setup-failure.json'
        [void](Assert-VerifierPhysicalOwnedPath $RunRoot $path)
        [IO.File]::WriteAllText($path, ($setupFailureRecord | ConvertTo-Json -Depth 8),
            [Text.UTF8Encoding]::new($false))
    } catch { }
}

function New-VerifierRunContext($WorktreeRoot, $EvidenceParent = '',
        $RunNamespaceRoot = '') {
    [void](Assert-VerifierRawPathValue $WorktreeRoot 'worktree root')
    [void](Assert-VerifierRawPathValue $EvidenceParent 'evidence parent' -AllowEmpty)
    [void](Assert-VerifierRawPathValue $RunNamespaceRoot 'run namespace root' -AllowEmpty)
    $root = $null
    $runId = [Guid]::NewGuid().ToString('N')
    $repositoryIdentity = ''
    $runRoot = $null
    $manifestPath = $null
    try {
        $root = Get-VerifierFullPath $WorktreeRoot
        if (-not (Test-Path -LiteralPath $root -PathType Container)) {
            Throw-VerifierInfrastructure "Worktree root does not exist: $root"
        }
        $repositoryIdentity = Get-VerifierRepositoryIdentity $root
        $tempRoot = Get-VerifierFullPath ([IO.Path]::GetTempPath())
        Assert-VerifierNoReparseAncestors $tempRoot
        $verifyRoot = Get-VerifierFullPath (Join-Path $tempRoot 'TroubleshootJS\verify')
        $verifyRootParent = Get-VerifierNearestExistingPath $verifyRoot
        if ([String]::IsNullOrWhiteSpace($verifyRootParent)) {
            Throw-VerifierInfrastructure 'Could not prove the verifier temp namespace before setup.'
        }
        Assert-VerifierNoReparseAncestors $verifyRootParent
        $repositoryTempRoot = if ([String]::IsNullOrWhiteSpace($RunNamespaceRoot)) {
            Join-Path $verifyRoot $repositoryIdentity
        } else {
            $requestedNamespace = Get-VerifierFullPath $RunNamespaceRoot
            if (-not (Test-VerifierChildPath $verifyRoot $requestedNamespace)) {
                Throw-VerifierInfrastructure 'Requested run namespace escaped the verifier temp root.'
            }
            $requestedNamespace
        }
        $runRoot = Join-Path $repositoryTempRoot $runId
        $manifestPath = Join-Path $runRoot 'manifest.json'
        # Validate the exact physical namespace before creating anything below
        # it. This prevents an existing junction/reparse replacement from
        # redirecting a new run into another worktree or temp namespace.
        [void](Assert-VerifierPhysicalOwnedPath $verifyRoot $repositoryTempRoot)
        New-Item -ItemType Directory -Path $repositoryTempRoot -Force -ErrorAction Stop | Out-Null
        [void](Assert-VerifierPhysicalOwnedPath $verifyRoot $repositoryTempRoot)
        if (Test-Path -LiteralPath $runRoot) {
            Throw-VerifierInfrastructure "Unique verifier run root already exists: $runRoot"
        }
        [void](Assert-VerifierPhysicalOwnedPath $repositoryTempRoot $runRoot)
        New-Item -ItemType Directory -Path $runRoot -ErrorAction Stop | Out-Null
        [void](Assert-VerifierPhysicalOwnedPath $repositoryTempRoot $runRoot -ValidateTree)

        if ([String]::IsNullOrWhiteSpace($EvidenceParent)) {
            $evidenceBase = Join-Path $runRoot 'evidence'
        } else {
            $evidenceParentFull = Get-VerifierFullPath $EvidenceParent
            Assert-VerifierNoReparseAncestors $evidenceParentFull
            [void](Assert-VerifierPhysicalOwnedPath $evidenceParentFull $evidenceParentFull -AllowRoot)
            New-Item -ItemType Directory -Path $evidenceParentFull -Force -ErrorAction Stop | Out-Null
            [void](Assert-VerifierPhysicalOwnedPath $evidenceParentFull $evidenceParentFull -AllowRoot)
            $evidenceBase = Join-Path $evidenceParentFull ('run-' + $runId)
        }
        [void](Assert-VerifierPhysicalOwnedPath (Split-Path -Parent $evidenceBase) $evidenceBase)
        New-Item -ItemType Directory -Path $evidenceBase -ErrorAction Stop | Out-Null
        [void](Assert-VerifierPhysicalOwnedPath (Split-Path -Parent $evidenceBase) $evidenceBase -ValidateTree)
        # Claims/evidence are run-scoped. The global named mutex, rather than a
        # shared repository directory, provides cross-worktree serialization;
        # this prevents stale files from another run from becoming an implicit
        # ownership source.
        $portLeaseRoot = Join-Path $runRoot 'port-leases'
        [void](Assert-VerifierPhysicalOwnedPath $runRoot $portLeaseRoot)
        New-Item -ItemType Directory -Path $portLeaseRoot -ErrorAction Stop | Out-Null
        [void](Assert-VerifierPhysicalOwnedPath $runRoot $portLeaseRoot -ValidateTree)

        $context = [pscustomobject]@{
            Protocol = 'troubleshootjs-verifier-run-v1'
            RunId = $runId
            PreviewNonce = [Guid]::NewGuid().ToString('N')
            RepositoryIdentity = $repositoryIdentity
            WorktreeRoot = $root
            RunRoot = $runRoot
            RunNamespaceRoot = $repositoryTempRoot
            EvidenceDirectory = Get-VerifierFullPath $evidenceBase
            EvidenceNamespaceRoot = if ([String]::IsNullOrWhiteSpace($EvidenceParent)) {
                $runRoot
            } else { Get-VerifierFullPath $evidenceParentFull }
            ManifestPath = $manifestPath
            PortLeaseRoot = $portLeaseRoot
            CreatedUtc = Get-VerifierUtcText
            BaseUrl = ''
             Server = [pscustomobject]@{
                 Owner = 'none'; BaseUrl = ''; Port = 0; ProcessId = 0
                  ProcessStartTicks = 0; ProcessParentProcessId = 0
                  ProcessParentProcessStartTicks = 0
                 ProcessCommandLine = ''; Script = ''; State = 'not-started'
                 RepositoryRoot = $root
                 WebRoot = Get-VerifierFullPath (Join-Path $root 'war')
                 IdentityProtocol = ''; IdentityVerified = $false; CallerOwned = $false
                  StdoutLog = ''; StderrLog = ''; CleanupResult = 'not-applicable'
                 Error = ''; Nonce = ''; RunId = ''; Lease = $null; Process = $null
                 ProcessIdentityKnown = $false; OwnershipUncertain = $false
                 ProcessTerminationProven = $false; ProcessAbsent = $false
                 ListenerInspectionProven = $false; ListenerAbsent = $null
             }
            LeaseRecords = New-Object Collections.ArrayList
            BrowserSessions = New-Object Collections.ArrayList
            Artifacts = New-Object Collections.ArrayList
            CleanupState = 'pending'
            CleanupCompletedUtc = ''
            CleanupErrors = New-Object Collections.ArrayList
            # Test hooks are inert in normal invocation.  Gate B uses these
            # one-shot hooks to prove claim/manifest acquisition rolls back.
            TestHooks = [pscustomobject]@{
                 FailNextManifestWrite = $false
                 FailNextClaimWrite = $false
                 FailNextLeaseRelease = $false
                 FailNextLeaseMutexDispose = $false
                  FailNextFinalManifestWrite = $false
                  FailNextPostDeleteFinalManifestWrite = $false
                  FailNextPostDeleteJournalWrite = $false
                  FailNextPostDeleteBeforeFinalState = $false
                  FailNextBrowserLeaseManifestWrite = $false
                  FailNextPreviewIdentityCapture = $false
                 BrowserDrainAfterInitialGraphSignalPath = ''
                 BrowserDrainAfterInitialGraphReadyPath = ''
                 BrowserDrainAfterInitialGraphSignalWritten = $false
             }
             ManifestWritePhase = ''
        }
        Write-VerifierManifest $context
        return $context
    } catch {
        $message = Get-VerifierErrorMessage $_
        Write-VerifierSetupFailureRecord $runRoot $manifestPath $runId $message
        $setupRecordPath = if ($runRoot -and
                (Test-Path -LiteralPath (Join-Path $runRoot 'setup-failure.json') -PathType Leaf)) {
            Join-Path $runRoot 'setup-failure.json'
        } else { '' }
        $recordDetail = if ($setupRecordPath) {
            " Setup record retained at '$setupRecordPath'."
        } else { '' }
        if (Test-VerifierInfrastructureError $_) {
            Throw-VerifierInfrastructure ($message + $recordDetail)
        }
        Throw-VerifierInfrastructure ('Could not create verifier run context: ' + $message + $recordDetail)
    }
}

function Write-VerifierLeaseRollbackRecord($Context, [string]$LeaseId,
        [string]$ClaimPath, [string]$Reason, $CleanupErrors) {
    try {
        if ($null -ne $Context) {
            [void](Assert-VerifierContextPreflight $Context 'lease rollback context')
        }
        $base = if ($Context -and (Test-Path -LiteralPath $Context.EvidenceDirectory -PathType Container)) {
            $Context.EvidenceDirectory
        } elseif ($Context -and (Test-Path -LiteralPath $Context.RunRoot -PathType Container)) {
            $Context.RunRoot
        } else { $null }
        if ([String]::IsNullOrWhiteSpace($base)) { return }
        $baseRoot = if ($Context -and
                $Context.PSObject.Properties['EvidenceNamespaceRoot'] -and
                -not [String]::IsNullOrWhiteSpace([string]$Context.EvidenceNamespaceRoot) -and
                (Test-VerifierCanonicalWindowsPathValue ([string]$Context.EvidenceNamespaceRoot) $base)) {
            [string]$Context.EvidenceNamespaceRoot
        } elseif ($Context -and
                (Test-VerifierCanonicalWindowsPathValue ([string]$Context.RunRoot) $base)) {
            [string]$Context.RunRoot
        } else { $base }
        if (Test-VerifierCanonicalWindowsPathValue $baseRoot $base) {
            [void](Assert-VerifierPhysicalOwnedPath $baseRoot $base -AllowRoot)
        } else {
            [void](Assert-VerifierPhysicalOwnedPath $baseRoot $base)
        }
        $path = Join-Path $base ('lease-rollback-' + $LeaseId + '.json')
        [void](Assert-VerifierPhysicalOwnedPath $base $path)
        $leaseRollbackRecord = [ordered]@{
            protocol = 'troubleshootjs-verifier-lease-rollback-v1'
            runId = if ($Context) { $Context.RunId } else { '' }
            leaseId = $LeaseId
            claimPath = $ClaimPath
            failedUtc = Get-VerifierUtcText
            reason = $Reason
            cleanupErrors = @($CleanupErrors)
        }
        [void](Assert-VerifierPhysicalOwnedPath $base $path)
        [IO.File]::WriteAllText($path, ($leaseRollbackRecord | ConvertTo-Json -Depth 8),
            [Text.UTF8Encoding]::new($false))
    } catch { }
}

function New-VerifierPortLease($Context, $Kind, $RequestedPort = 0,
        $BrowserPath = '') {
    if (-not (Test-VerifierStrictStringValue $Kind) -or
            [String]::IsNullOrWhiteSpace($Kind) -or
            -not (Test-VerifierStrictIntegralValue $RequestedPort 0 65535) -or
            -not (Test-VerifierStrictStringValue $BrowserPath)) {
        Throw-VerifierInfrastructure 'A port lease request carried a malformed kind, requested port, or BrowserPath before allocation.'
    }
    if (-not (Test-VerifierSupportedPortLeaseKind $Kind)) {
        Throw-VerifierInfrastructure "Unsupported port lease kind '$Kind' before allocation."
    }
    [void](Assert-VerifierContextPreflight $Context 'port lease context' `
        -RequiredProperties @('RunId', 'RepositoryIdentity', 'PortLeaseRoot'))
    if ($Kind -eq 'cdp') {
        if ([String]::IsNullOrWhiteSpace($BrowserPath)) {
            $BrowserPath = Resolve-VerifierBrowserPath ''
        } else {
            $BrowserPath = Get-VerifierFullPath $BrowserPath
        }
        if ([String]::IsNullOrWhiteSpace($BrowserPath) -or
                -not (Test-Path -LiteralPath $BrowserPath -PathType Leaf)) {
            Throw-VerifierInfrastructure 'A cdp lease requires a resolved, existing BrowserPath executable identity.'
        }
    }
    $ordinaryPorts = @(8888, 8898, 8899, 9876)
    $maxAttempts = if ($RequestedPort -gt 0) { 1 } else { 64 }
    for ($attempt = 0; $attempt -lt $maxAttempts; $attempt++) {
        $listener = $null
        $mutex = $null
        $mutexHeld = $false
        $leaseRegistered = $false
        $leaseRecord = $null
        $claimName = ''
        $claimPath = ''
        $rollbackLeaseId = 'unknown'
        $claimFileOwned = $false
        $mapRegistered = $false
        $mutexMapRegistered = $false
        $retry = $false
        $rollbackErrors = New-Object Collections.ArrayList
        $rollbackReason = ''
        try {
            $listener = if ($RequestedPort -gt 0) {
                [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $RequestedPort)
            } else {
                [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
            }
            $listener.Start()
            $port = [int]$listener.LocalEndpoint.Port
            if ($ordinaryPorts -contains $port) {
                if ($RequestedPort -gt 0) {
                    Throw-VerifierInfrastructure "Requested port $port is reserved from verifier allocation."
                }
                $retry = $true
                continue
            }

            $claimName = Get-VerifierPortMutexName $Context $port
            if ($script:VerifierHeldPortClaims.ContainsKey($claimName)) {
                if ($RequestedPort -gt 0) {
                    Throw-VerifierInfrastructure "Port $port is already claimed by another verifier run in this process."
                }
                $retry = $true
                continue
            }
            $createdNew = $false
            $mutex = [Threading.Mutex]::new($false, $claimName, [ref]$createdNew)
            try {
                if (-not $mutex.WaitOne(0)) {
                    if ($RequestedPort -gt 0) {
                        Throw-VerifierInfrastructure "Port $port is already claimed by another verifier run."
                    }
                    $retry = $true
                    continue
                }
                $mutexHeld = $true
            } catch [Threading.AbandonedMutexException] {
                # An abandoned mutex is safe to take, but old claim evidence is
                # never adopted and remains available for diagnosis.
                $mutexHeld = $true
            }

            $leaseId = [Guid]::NewGuid().ToString('N')
            $rollbackLeaseId = $leaseId
            $claimPath = Join-Path $Context.PortLeaseRoot ("$port-$leaseId.lease")
            $claimOwnerStartTicks = Get-VerifierCurrentProcessStartTicks
            $claimData = [ordered]@{
                protocol = 'troubleshootjs-verifier-port-claim-v1'
                runId = $Context.RunId
                repositoryIdentity = $Context.RepositoryIdentity
                worktreeRoot = $Context.WorktreeRoot
                kind = $Kind
                leaseId = $leaseId
                path = $claimPath
                port = $port
                mutexName = $claimName
                ownerPid = [int]$PID
                ownerStartTicks = $claimOwnerStartTicks
            }
            $stream = $null
            try {
                Assert-VerifierNoReparseAncestors $Context.PortLeaseRoot
                if (-not (Test-VerifierPhysicalChildPath $Context.PortLeaseRoot $claimPath)) {
                    Throw-VerifierInfrastructure 'Port claim path was outside the physical lease namespace before creation.'
                }
                $stream = [IO.File]::Open($claimPath, [IO.FileMode]::CreateNew,
                    [IO.FileAccess]::Write, [IO.FileShare]::None)
                # From this point forward this exact path was created by this
                # attempt and is safe to remove during rollback, even if the
                # following write is interrupted halfway through.
                $claimFileOwned = $true
                $bytes = [Text.Encoding]::UTF8.GetBytes(
                    (($claimData | ConvertTo-Json -Compress -Depth 8)))
                if (-not (Test-VerifierPhysicalChildPath $Context.PortLeaseRoot $claimPath)) {
                    Throw-VerifierInfrastructure 'Port claim path changed physical ownership before write.'
                }
                if ($Context.PSObject.Properties['TestHooks'] -and
                        $Context.TestHooks.PSObject.Properties['FailNextClaimWrite'] -and
                        [bool]$Context.TestHooks.FailNextClaimWrite) {
                    $Context.TestHooks.FailNextClaimWrite = $false
                    $partialCount = [Math]::Max(1, [Math]::Min($bytes.Length,
                        [int][Math]::Ceiling($bytes.Length / 2.0)))
                    $stream.Write($bytes, 0, $partialCount)
                    $stream.Flush($true)
                    Throw-VerifierInfrastructure 'Injected claim-write failure after partial claim data.'
                }
                $stream.Write($bytes, 0, $bytes.Length)
                $stream.Flush($true)
            } catch [IO.IOException] {
                $retry = $true
            } finally {
                if ($null -ne $stream) { $stream.Dispose() }
            }
            if ($retry) { continue }

            # The listener was held while the global mutex and exact claim
            # record were acquired. The mutex is retained through the external
            # preview/Edge bind and until exact cleanup releases it.
            $listener.Stop()
            $listener = $null
            $leaseRecord = [pscustomobject]@{
                LeaseId = $leaseId; Kind = $Kind; Port = $port; Path = $claimPath
                RunId = $Context.RunId; RepositoryIdentity = $Context.RepositoryIdentity
                WorktreeRoot = $Context.WorktreeRoot
                Status = 'leased'; Registered = $false; ClaimName = $claimName
                ClaimState = 'held'; ClaimOwnerPid = [int]$PID
                ClaimOwnerStartTicks = $claimOwnerStartTicks
                BoundProcessId = 0; BoundProcessStartTicks = 0
             ListenerProcessId = 0; ListenerProcessStartTicks = 0
                 ListenerOwnerKind = 'none'; ListenerOwnerProof = ''
                 ListenerOwnerEvidence = ''
                  BindValidatedUtc = ''; ReleasedUtc = ''; ClaimMutex = $mutex
                  AuthorizationProof = $null
                 ProfilePath = ''; OwnerType = 'none'; ProfileInspectionFailed = $false
                  BrowserPath = [string]$BrowserPath; ReleaseState = 'active'; ReleaseBlocked = $false
                 ReleaseBlockReason = ''; MutexReleased = $false
                  ListenerInspectionSuccess = $false; ListenerInspectionKnown = $false
                  ListenerHasListeners = $null; ListenerAbsent = $null
                  ListenerInspectionUtc = ''
                  ProcessProofRequired = $false; ReleaseJournalState = 'active'
                  ProcessTerminationProven = $false; ProcessAbsent = $false
             }
            [void]$Context.LeaseRecords.Add($leaseRecord)
            $script:VerifierHeldPortClaims[$claimName] = $Context.RunId
            $script:VerifierHeldPortMutexes[$claimName] = $mutex
            $mutexMapRegistered = $true
            $mapRegistered = $true
            try {
                Write-VerifierManifest $Context
            } catch {
                $rollbackReason = Get-VerifierErrorMessage $_
                throw
            }
            # Registration is not complete until the manifest includes the
            # lease. A manifest failure therefore enters the rollback path.
            $leaseRecord.Registered = $true
            $leaseRegistered = $true
            return $leaseRecord
        } catch {
            $rollbackReason = if ($rollbackReason) { $rollbackReason } else {
                Get-VerifierErrorMessage $_
            }
            if (Test-VerifierInfrastructureError $_) { throw }
            if (-not $retry) {
                Throw-VerifierInfrastructure "Could not claim an isolated $Kind port: $rollbackReason"
            }
        } finally {
            if ($null -ne $listener) {
                try { $listener.Stop() } catch { [void]$rollbackErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if (-not $leaseRegistered) {
                if ($null -ne $leaseRecord) {
                    try { [void]$Context.LeaseRecords.Remove($leaseRecord) } catch {
                        [void]$rollbackErrors.Add('Could not remove the unregistered lease record: ' +
                            (Get-VerifierErrorMessage $_))
                    }
                }
                if ($mapRegistered -and $script:VerifierHeldPortClaims.ContainsKey($claimName) -and
                        [string]$script:VerifierHeldPortClaims[$claimName] -eq [string]$Context.RunId) {
                    [void]$script:VerifierHeldPortClaims.Remove($claimName)
                }
                if ($mutexMapRegistered -and
                        $script:VerifierHeldPortMutexes.ContainsKey($claimName) -and
                        [object]::ReferenceEquals($script:VerifierHeldPortMutexes[$claimName], $mutex)) {
                    [void]$script:VerifierHeldPortMutexes.Remove($claimName)
                }
                if ($claimFileOwned -and $claimPath) {
                    try {
                        if (Test-Path -LiteralPath $claimPath -PathType Leaf) {
                            Remove-VerifierOwnedTree $Context.PortLeaseRoot $claimPath
                        }
                    } catch {
                        [void]$rollbackErrors.Add('Could not remove the exact failed claim file: ' +
                            (Get-VerifierErrorMessage $_))
                    }
                }
                if ($null -ne $mutex) {
                    if ($mutexHeld) {
                        try { $mutex.ReleaseMutex() } catch {
                            [void]$rollbackErrors.Add('Could not release the failed claim mutex: ' +
                                (Get-VerifierErrorMessage $_))
                        }
                    }
                    try { $mutex.Dispose() } catch {
                        [void]$rollbackErrors.Add('Could not dispose the failed claim mutex: ' +
                            (Get-VerifierErrorMessage $_))
                    }
                }
                if ($rollbackErrors.Count -gt 0) {
                    Write-VerifierLeaseRollbackRecord $Context `
                        $rollbackLeaseId $claimPath $rollbackReason $rollbackErrors
                    Throw-VerifierInfrastructure ('Port claim rollback was not fully provable: ' +
                        ($rollbackErrors -join '; '))
                }
            }
        }
    }
    if ($RequestedPort -gt 0) {
        Throw-VerifierInfrastructure "Could not claim requested isolated port $RequestedPort for $Kind."
    }
    Throw-VerifierInfrastructure "Could not allocate an isolated $Kind port after bounded attempts."
}

function Test-VerifierPreviewProcessIdentity($PreviewRecord, $Snapshot) {
    if ($null -eq $PreviewRecord -or $null -eq $Snapshot) { return $false }
    foreach ($propertyName in @('ProcessId', 'ProcessStartTicks', 'Script',
            'Port', 'RunId', 'Nonce')) {
        if ($null -eq $PreviewRecord.PSObject.Properties[$propertyName]) {
            return $false
        }
    }
    if (-not (Test-VerifierStrictIntegralValue $PreviewRecord.ProcessId `
            1 ([int]::MaxValue)) -or
            [int]$PreviewRecord.ProcessId -eq 4 -or
            -not (Test-VerifierStrictIntegralValue $PreviewRecord.ProcessStartTicks 1) -or
            -not (Test-VerifierStrictIntegralValue $PreviewRecord.Port 1 65535) -or
            -not (Test-VerifierStrictStringValue $PreviewRecord.Script) -or
            [String]::IsNullOrWhiteSpace($PreviewRecord.Script) -or
            -not (Test-VerifierStrictStringValue $PreviewRecord.RunId) -or
            [String]::IsNullOrWhiteSpace($PreviewRecord.RunId) -or
            -not (Test-VerifierStrictStringValue $PreviewRecord.Nonce) -or
            [String]::IsNullOrWhiteSpace($PreviewRecord.Nonce)) {
        return $false
    }

    $previewPid = [int]$PreviewRecord.ProcessId
    $previewStart = [long]$PreviewRecord.ProcessStartTicks
    $matches = New-Object Collections.ArrayList
    foreach ($candidate in @($Snapshot)) {
        if ($null -eq $candidate -or
                $null -eq $candidate.PSObject.Properties['ProcessId'] -or
                -not (Test-VerifierStrictIntegralValue $candidate.ProcessId `
                    0 ([int]::MaxValue))) {
            return $false
        }
        $candidatePid = [int]$candidate.ProcessId
        # Complete OS snapshots legitimately include System Idle (PID 0) and
        # System (PID 4). Neither may be the selected user-process preview
        # owner, but unrelated records must not invalidate the full snapshot.
        if ($candidatePid -eq 0 -or $candidatePid -eq 4) { continue }
        $candidateStartProperty = $candidate.PSObject.Properties['ProcessStartTicks']
        if ($candidatePid -ne $previewPid) {
            if ($null -ne $candidateStartProperty -and
                    ($null -eq $candidateStartProperty.Value -or
                     -not (Test-VerifierStrictIntegralValue $candidateStartProperty.Value 1))) {
                return $false
            }
            continue
        }
        # WMI and the native fallback expose the retained process's PID and
        # command line, but neither snapshot shape carries ProcessStartTicks.
        # The retained real Process object below remains the authoritative
        # start-identity check. If a snapshot source does expose a start
        # property, preserve the stronger exact equality requirement; a
        # matching PID without a command line is still PID-only evidence.
        if (($null -ne $candidateStartProperty -and
                (-not (Test-VerifierStrictIntegralValue $candidateStartProperty.Value 1) -or
                 [long]$candidateStartProperty.Value -ne $previewStart)) -or
                $null -eq $candidate.PSObject.Properties['CommandLine'] -or
                -not (Test-VerifierStrictStringValue $candidate.CommandLine) -or
                [String]::IsNullOrWhiteSpace($candidate.CommandLine)) {
            return $false
        }
        [void]$matches.Add($candidate)
    }
    if ($matches.Count -ne 1) { return $false }

    # PID and command-line markers are only supporting evidence. Re-read the
    # retained real process identity so stale/fabricated WMI records cannot
    # authorize the preview owner.
    $process = Get-VerifierProcessById $previewPid
    if ($null -eq $process) { return $false }
    try {
        if ([bool]$process.HasExited -or
                (Get-VerifierProcessStartTicks $process) -ne $previewStart) {
            return $false
        }
    } catch { return $false }
    $command = $matches[0].CommandLine
    return (Test-VerifierCommandLinePath $command $PreviewRecord.Script) -and
        (Test-VerifierCommandLineSwitch $command '-Port' ([string]$PreviewRecord.Port)) -and
        (Test-VerifierCommandLineSwitch $command '-VerifierRunId' $PreviewRecord.RunId) -and
        (Test-VerifierCommandLineSwitch $command '-VerifierNonce' $PreviewRecord.Nonce)
}

function Test-VerifierListenerBelongsToOwner($OwnerRecord, $Listener,
        $ProcessId, $ProcessStartTicks, $Snapshot) {
    if (-not (Test-VerifierListenerRecordSchema $Listener)) {
        return $false
    }
    if (-not (Test-VerifierStrictIntegralValue $ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $ProcessStartTicks 1)) {
        return $false
    }
    # Kernel transport ownership is never a process identity and must be
    # handled only by the explicit run-owned preview proof at the caller.
    if ($Listener.ListenerOwnerKind -ne $script:VerifierUserProcessOwnerKind -or
            $null -eq $OwnerRecord) {
        return $false
    }
    $listenerStartProperty = $Listener.PSObject.Properties['ProcessStartTicks']
    if ($null -eq $listenerStartProperty.Value) { return $false }
    if (-not (Test-VerifierStrictIntegralValue $listenerStartProperty.Value 1)) { return $false }
    $listenerStart = [long]$listenerStartProperty.Value

    # A direct owner record is an explicit, testable identity proof. It is
    # intentionally stricter than PID/start equality: the retained object must
    # be a real live Process whose identity is re-read at this boundary.
    if ($OwnerRecord.PSObject.Properties['DirectProcessOwner']) {
        if (-not (Test-VerifierStrictBooleanValue $OwnerRecord.DirectProcessOwner) -or
                -not $OwnerRecord.DirectProcessOwner -or
                -not $OwnerRecord.PSObject.Properties['Process'] -or
                -not ($OwnerRecord.Process -is [System.Diagnostics.Process]) -or
                -not $OwnerRecord.PSObject.Properties['ProcessId'] -or
                -not $OwnerRecord.PSObject.Properties['ProcessStartTicks'] -or
                -not $OwnerRecord.PSObject.Properties['IdentityProof'] -or
                -not (Test-VerifierStrictStringValue $OwnerRecord.IdentityProof) -or
                $OwnerRecord.IdentityProof -cne 'retained-process-object-v1' -or
                -not (Test-VerifierStrictIntegralValue $OwnerRecord.ProcessId 1 ([int]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $OwnerRecord.ProcessStartTicks 1)) {
            return $false
        }
        try {
            if ([bool]$OwnerRecord.Process.HasExited -or
                    [int]$OwnerRecord.Process.Id -ne [int]$OwnerRecord.ProcessId -or
                    (Get-VerifierProcessStartTicks $OwnerRecord.Process) -ne
                        [long]$OwnerRecord.ProcessStartTicks) {
                return $false
            }
        } catch { return $false }
        return ([int]$Listener.ProcessId -eq [int]$ProcessId -and
            $listenerStart -eq [long]$ProcessStartTicks -and
            [int]$OwnerRecord.ProcessId -eq [int]$ProcessId -and
            [long]$OwnerRecord.ProcessStartTicks -eq [long]$ProcessStartTicks)
    }
    if ([int]$Listener.ProcessId -eq $ProcessId -and
            $listenerStart -eq $ProcessStartTicks) {
        if ($OwnerRecord.PSObject.Properties['Profile']) {
            return (Test-VerifierProcessIdentity $OwnerRecord $Snapshot)
        }
        if ($OwnerRecord.PSObject.Properties['Script']) {
            return (Test-VerifierPreviewProcessIdentity $OwnerRecord $Snapshot)
        }
        return $false
    }
    if (-not $OwnerRecord.PSObject.Properties['ProcessId'] -or
            -not (Test-VerifierStrictIntegralValue $OwnerRecord.ProcessId 1 ([int]::MaxValue))) {
        return $false
    }
    $rootIsOwned = if ($OwnerRecord.PSObject.Properties['Profile']) {
        Test-VerifierProcessIdentity $OwnerRecord $Snapshot
    } elseif ($OwnerRecord.PSObject.Properties['Script']) {
        Test-VerifierPreviewProcessIdentity $OwnerRecord $Snapshot
    } else { $false }
    if (-not $rootIsOwned) { return $false }
    $descendants = @(Get-VerifierDescendantProcessRecords $OwnerRecord $Snapshot)
    $candidate = @($descendants | Where-Object {
        [int]$_.ProcessId -eq [int]$Listener.ProcessId -and
            [long]$_.ProcessStartTicks -eq $listenerStart
    } | Select-Object -First 1)
    if ($candidate.Count -eq 0) { return $false }
    $command = [string]$candidate[0].CommandLine
    if ($OwnerRecord.PSObject.Properties['Profile']) {
        return (Test-VerifierCommandLineSwitch $command '--user-data-dir' $OwnerRecord.Profile) -and
            (Test-VerifierCommandLineSwitch $command '--tsj-verifier-run' $OwnerRecord.RunId) -and
            (Test-VerifierCommandLineSwitch $command '--tsj-verifier-worktree' $OwnerRecord.RepositoryIdentity) -and
            (Test-VerifierCommandLineSwitch $command '--remote-debugging-port' ([string]$OwnerRecord.CdpPort))
    }
    if ($OwnerRecord.PSObject.Properties['Script']) {
        return (Test-VerifierCommandLinePath $command ([string]$OwnerRecord.Script)) -and
            (Test-VerifierCommandLineSwitch $command '-Port' ([string]$OwnerRecord.Port)) -and
            (Test-VerifierCommandLineSwitch $command '-VerifierRunId' ([string]$OwnerRecord.RunId)) -and
            (Test-VerifierCommandLineSwitch $command '-VerifierNonce' ([string]$OwnerRecord.Nonce))
    }
    return $false
}

function Test-VerifierPreviewOwnerPortFields($OwnerRecord) {
    if ($null -eq $OwnerRecord) { return $true }
    $portValues = New-Object Collections.Generic.List[long]
    foreach ($propertyName in @('CdpPort', 'Port')) {
        $property = $OwnerRecord.PSObject.Properties[$propertyName]
        if ($null -eq $property) { continue }
        if (-not (Test-VerifierStrictIntegralValue $property.Value 1 65535)) {
            return $false
        }
        [void]$portValues.Add([long]$property.Value)
    }
    if ($portValues.Count -gt 1 -and $portValues[0] -ne $portValues[1]) {
        return $false
    }
    return $true
}

function Test-VerifierSameRootBrowserListenerEligibility($Inspection,
        $Listeners, $OwnerRecord, $ProcessId, $ProcessStartTicks,
        $PreviewListenerOwner) {
    if ($null -eq $Inspection -or $null -eq $OwnerRecord -or
            $null -ne $PreviewListenerOwner -or $null -eq $Listeners) {
        return $false
    }
    foreach ($propertyName in @('ListenerOwnerKind', 'Listeners')) {
        if (-not $Inspection.PSObject.Properties[$propertyName]) {
            return $false
        }
    }
    $ownerProfileProperty = $OwnerRecord.PSObject.Properties['Profile']
    $ownerBrowserPathProperty = $OwnerRecord.PSObject.Properties['BrowserPath']
    $ownerProcessIdProperty = $OwnerRecord.PSObject.Properties['ProcessId']
    $ownerProcessStartProperty = $OwnerRecord.PSObject.Properties['ProcessStartTicks']
    $ownerParentProcessIdProperty = $OwnerRecord.PSObject.Properties['ProcessParentProcessId']
    $ownerParentProcessStartProperty = $OwnerRecord.PSObject.Properties['ProcessParentProcessStartTicks']
    $ownerCommandLineProperty = $OwnerRecord.PSObject.Properties['ProcessCommandLine']
    $ownerRunIdProperty = $OwnerRecord.PSObject.Properties['RunId']
    $ownerRepositoryProperty = $OwnerRecord.PSObject.Properties['RepositoryIdentity']
    $ownerPortProperty = $OwnerRecord.PSObject.Properties['CdpPort']
    $sameRootBrowserShape = ($null -ne $ownerProfileProperty -and
        (Test-VerifierStrictStringValue $ownerProfileProperty.Value) -and
        -not [String]::IsNullOrWhiteSpace([string]$ownerProfileProperty.Value) -and
        $null -ne $ownerBrowserPathProperty -and
        (Test-VerifierStrictStringValue $ownerBrowserPathProperty.Value) -and
        -not [String]::IsNullOrWhiteSpace([string]$ownerBrowserPathProperty.Value) -and
        $null -ne $ownerProcessIdProperty -and
        (Test-VerifierStrictIntegralValue $ownerProcessIdProperty.Value 1 ([int]::MaxValue)) -and
        [int]$ownerProcessIdProperty.Value -eq [int]$ProcessId -and
        $null -ne $ownerProcessStartProperty -and
        (Test-VerifierStrictIntegralValue $ownerProcessStartProperty.Value 1) -and
        [long]$ownerProcessStartProperty.Value -eq [long]$ProcessStartTicks -and
        $null -ne $ownerParentProcessIdProperty -and
        (Test-VerifierStrictIntegralValue $ownerParentProcessIdProperty.Value 1 ([int]::MaxValue)) -and
        $null -ne $ownerParentProcessStartProperty -and
        (Test-VerifierStrictIntegralValue $ownerParentProcessStartProperty.Value 1) -and
        $null -ne $ownerCommandLineProperty -and
        (Test-VerifierStrictStringValue $ownerCommandLineProperty.Value) -and
        -not [String]::IsNullOrWhiteSpace([string]$ownerCommandLineProperty.Value) -and
        $null -ne $ownerRunIdProperty -and
        (Test-VerifierStrictStringValue $ownerRunIdProperty.Value) -and
        -not [String]::IsNullOrWhiteSpace([string]$ownerRunIdProperty.Value) -and
        $null -ne $ownerRepositoryProperty -and
        (Test-VerifierStrictStringValue $ownerRepositoryProperty.Value) -and
        -not [String]::IsNullOrWhiteSpace([string]$ownerRepositoryProperty.Value) -and
        $null -ne $ownerPortProperty -and
        (Test-VerifierStrictIntegralValue $ownerPortProperty.Value 1 65535) -and
        $null -eq $OwnerRecord.PSObject.Properties['Script'] -and
        $null -eq $OwnerRecord.PSObject.Properties['DirectProcessOwner'] -and
        $null -eq $OwnerRecord.PSObject.Properties['IdentityProof'] -and
        $null -eq $OwnerRecord.PSObject.Properties['Process'] -and
        [string]$Inspection.ListenerOwnerKind -ceq $script:VerifierUserProcessOwnerKind -and
        @($Listeners).Count -gt 0)
    if (-not $sameRootBrowserShape) { return $false }
    foreach ($listener in @($Listeners)) {
        if ([string]$listener.ListenerOwnerKind -cne
                $script:VerifierUserProcessOwnerKind -or
                -not (Test-VerifierStrictIntegralValue $listener.ProcessId `
                    1 ([int]::MaxValue)) -or
                [int]$listener.ProcessId -ne [int]$ProcessId -or
                -not (Test-VerifierStrictIntegralValue $listener.ProcessStartTicks 1) -or
                [long]$listener.ProcessStartTicks -ne [long]$ProcessStartTicks) {
            return $false
        }
    }
    return $true
}

function Test-VerifierLiveListenerInspectionAuthorization($Inspection,
        $PreviewContext, $PreviewOwner, $AuthorizedProcessId,
        $AuthorizedProcessStartTicks, $AuthorizationProof = $null) {
    $retryBudgetMilliseconds = 500
    # The listener authorization deadline covers the complete proof, including
    # the initial schema/capability boundary and every independent process or
    # kernel authorization query. It is one monotonic budget for the call;
    # retries may not start, reset, or extend it.
    $retryBudget = [Diagnostics.Stopwatch]::StartNew()
    $retryBudgetTicks = [long][Math]::Ceiling(
        ([double][Diagnostics.Stopwatch]::Frequency * $retryBudgetMilliseconds) /
        1000.0)
    # Validate preview-owner port fields before the inspection schema can
    # delegate a kernel record into its preview authorization path.  This keeps
    # every caller-level CdpPort/Port cast behind the same raw scalar gate.
    if ($null -eq $Inspection -or $retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
        return $false
    }
    $ownerPortFieldsValid = Test-VerifierPreviewOwnerPortFields $PreviewOwner
    if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
        return $false
    }
    if (-not $ownerPortFieldsValid) {
        return $false
    }
    if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
        return $false
    }
    $inspectionSchemaValid = Test-VerifierListenerInspectionSchema $Inspection `
        $PreviewContext $PreviewOwner $null -StructuralOnly
    if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
        return $false
    }
    if (-not $inspectionSchemaValid) {
        return $false
    }
    if (-not $Inspection.HasListeners) {
        return $true
    }
    if ($null -eq $PreviewContext -or $null -eq $PreviewOwner) {
        return $false
    }
    $listeners = @($Inspection.Listeners)
    if ($Inspection.ListenerOwnerKind -eq $script:VerifierKernelTransportOwnerKind) {
        # PID 4 is transport ownership, not a process identity.  Its positive
        # write is authorized only by the exact run-owned preview handshake
        # carried from the initial listener boundary.  This downstream
        # consumer must never spend a second semantic authorization.
        if ($null -eq $AuthorizationProof) { return $false }
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
        $kernelAuthorizationValid = Test-VerifierRunOwnedPreviewHttpSysAuthorizationProof `
            $AuthorizationProof $PreviewContext $PreviewOwner `
            ([int]$listeners[0].Port)
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
        return [bool]$kernelAuthorizationValid
    }
    if ($Inspection.ListenerOwnerKind -ne $script:VerifierUserProcessOwnerKind -or
            -not (Test-VerifierStrictIntegralValue $AuthorizedProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $AuthorizedProcessStartTicks 1)) {
        return $false
    }

    # Preserve the existing retained-process proof for non-browser/direct
    # owners.  This path has no browser profile or command-marker snapshot;
    # Test-VerifierListenerBelongsToOwner re-reads the actual Process object
    # and exact PID/start tuple for each listener.
    if ($PreviewOwner.PSObject.Properties['DirectProcessOwner']) {
        foreach ($listener in $listeners) {
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                return $false
            }
            $directListenerAuthorization = Test-VerifierListenerBelongsToOwner `
                $PreviewOwner $listener $AuthorizedProcessId $AuthorizedProcessStartTicks $null
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                return $false
            }
            if (-not $directListenerAuthorization) {
                return $false
            }
        }
        return $true
    }

    if (-not ($PreviewOwner.PSObject.Properties['Profile'] -or
            $PreviewOwner.PSObject.Properties['Script'])) {
        return $false
    }
    $ownerProcessIdProperty = $PreviewOwner.PSObject.Properties['ProcessId']
    $ownerStartProperty = $PreviewOwner.PSObject.Properties['ProcessStartTicks']
    if ($null -eq $ownerProcessIdProperty -or $null -eq $ownerStartProperty -or
            -not (Test-VerifierStrictIntegralValue $ownerProcessIdProperty.Value `
                1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $ownerStartProperty.Value 1)) {
        return $false
    }
    $ownerBrowserPath = if ($PreviewOwner.PSObject.Properties['BrowserPath']) {
        [string]$PreviewOwner.BrowserPath
    } else { '' }
    $ownerProfile = if ($PreviewOwner.PSObject.Properties['Profile']) {
        [string]$PreviewOwner.Profile
    } else { '' }
    $ownerRunId = if ($PreviewOwner.PSObject.Properties['RunId']) {
        [string]$PreviewOwner.RunId
    } else { '' }
    $ownerRepository = if ($PreviewOwner.PSObject.Properties['RepositoryIdentity']) {
        [string]$PreviewOwner.RepositoryIdentity
    } else { '' }
    $ownerPort = if ($PreviewOwner.PSObject.Properties['CdpPort']) {
        [int]$PreviewOwner.CdpPort
    } elseif ($PreviewOwner.PSObject.Properties['Port']) {
        [int]$PreviewOwner.Port
    } else { 0 }
    $ownerScript = if ($PreviewOwner.PSObject.Properties['Script']) {
        [string]$PreviewOwner.Script
    } else { '' }
    $ownerNonce = if ($PreviewOwner.PSObject.Properties['Nonce']) {
        [string]$PreviewOwner.Nonce
    } else { '' }
    if (-not (Test-VerifierStrictIntegralValue $ownerPort 1 65535)) {
        return $false
    }
    if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
        return $false
    }

    $expectedListenerKeys = @{}
    foreach ($listener in $listeners) {
        $key = [string]$listener.Port + '|' + [string]$listener.ProcessId + '|' +
            [string]$listener.ProcessStartTicks
        $expectedListenerKeys[$key] = $true
    }
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
        $attemptInspection = if ($attempt -eq 1) {
            $Inspection
        } else {
            try {
                Get-VerifierLoopbackListenerRecords $ownerPort $PreviewContext `
                    $PreviewOwner -PreferNetstat
            } catch { return $false }
        }
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
        $attemptInspectionSchemaValid = Test-VerifierListenerInspectionSchema $attemptInspection `
            $PreviewContext $PreviewOwner $null -StructuralOnly
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
        if (-not $attemptInspectionSchemaValid -or
                -not $attemptInspection.HasListeners) {
            return $false
        }
        $attemptListeners = @($attemptInspection.Listeners)
        if ($attempt -gt 1) {
            if ($attemptInspection.ListenerOwnerKind -cne $Inspection.ListenerOwnerKind -or
                    $attemptInspection.ListenerOwnerProof -cne $Inspection.ListenerOwnerProof -or
                    $attemptInspection.ListenerOwnerEvidence -cne $Inspection.ListenerOwnerEvidence -or
                    $attemptListeners.Count -ne $listeners.Count) {
                return $false
            }
            foreach ($listener in $attemptListeners) {
                $key = [string]$listener.Port + '|' + [string]$listener.ProcessId + '|' +
                    [string]$listener.ProcessStartTicks
                if (-not $expectedListenerKeys.ContainsKey($key)) { return $false }
            }
        }

        # A genuine same-root browser listener can use the narrow PID-scoped
        # proof.  Test-VerifierListenerBelongsToOwner receives no snapshot so
        # it independently re-reads the live Process object and its exact
        # PID/start/parent/command/executable/marker identity on this call.
        # Every other owner/listener shape continues through the complete
        # browser census and descendant refresh path below.
        $sameRootBrowserListenerSet = Test-VerifierSameRootBrowserListenerEligibility `
            $attemptInspection $attemptListeners $PreviewOwner `
            $AuthorizedProcessId $AuthorizedProcessStartTicks $null
        if ($sameRootBrowserListenerSet) {
            foreach ($listener in $attemptListeners) {
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                    return $false
                }
                $belongs = $false
                try {
                    $belongs = Test-VerifierListenerBelongsToOwner $PreviewOwner `
                        $listener $AuthorizedProcessId $AuthorizedProcessStartTicks $null
                } catch {
                    return $false
                }
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                    return $false
                }
                if (-not $belongs) {
                    return $false
                }
            }
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                return $false
            }
            return $true
        }

        # Re-read the retained root and every listener identity on each full
        # attempt. A listener snapshot is never trusted after the process
        # boundary has moved, even when the PID is unchanged.
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
        try {
            $rootCurrent = Get-VerifierCurrentProcessRecordById `
                ([int]$ownerProcessIdProperty.Value)
        } catch { return $false }
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
        if ($null -eq $rootCurrent -or
                [long]$rootCurrent.ProcessStartTicks -ne [long]$ownerStartProperty.Value) {
            return $false
        }
        foreach ($listener in $attemptListeners) {
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                return $false
            }
            try {
                $listenerCurrent = Get-VerifierCurrentProcessRecordById `
                    ([int]$listener.ProcessId)
            } catch { return $false }
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                return $false
            }
            if ($null -eq $listenerCurrent -or
                    [long]$listenerCurrent.ProcessStartTicks -ne
                        [long]$listener.ProcessStartTicks) {
                return $false
            }
        }
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }

        $snapshot = $null
        $ownershipError = $null
        try {
            $snapshot = @(Get-VerifierBrowserOwnershipSnapshot `
                $ownerBrowserPath $ownerProfile $ownerRunId $ownerRepository `
                $ownerPort $ownerScript $ownerNonce)
        } catch {
            $ownershipError = $_
        }
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
        $missingProcessId = Get-VerifierMissingProcessId $ownershipError
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
        if ($null -ne $ownershipError -and $missingProcessId -le 0) {
            return $false
        }
        if ($null -eq $ownershipError) {
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                return $false
            }
            $rootIsOwned = if ($PreviewOwner.PSObject.Properties['Profile']) {
                Test-VerifierProcessIdentity $PreviewOwner $snapshot $ownerBrowserPath
            } else {
                Test-VerifierPreviewProcessIdentity $PreviewOwner $snapshot
            }
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                return $false
            }
            if (-not $rootIsOwned) { return $false }
            $failedListener = $null
            foreach ($listener in $attemptListeners) {
                $belongs = $false
                $listenerError = $null
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                    return $false
                }
                try {
                    $belongs = Test-VerifierListenerBelongsToOwner $PreviewOwner $listener `
                        $AuthorizedProcessId $AuthorizedProcessStartTicks $snapshot
                } catch {
                    $listenerError = $_
                }
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                    return $false
                }
                if (-not $belongs) {
                    if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                        return $false
                    }
                    $missingProcessId = Get-VerifierMissingProcessId $listenerError
                    if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                        return $false
                    }
                    if ($missingProcessId -le 0) { return $false }
                    $failedListener = $listener
                    break
                }
            }
            if ($null -eq $failedListener) {
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                    return $false
                }
                return $true
            }
            if (@($attemptListeners | Where-Object {
                    [int]$_.ProcessId -eq [int]$missingProcessId }).Count -gt 0 -or
                    [int]$missingProcessId -eq [int]$ownerProcessIdProperty.Value) {
                return $false
            }
        }
        if ($attempt -ge 3) { return $false }
        # A retry is allowed only when a second, independent process view
        # positively proves the exact missing candidate PID is gone. Any
        # present, changed, inaccessible, malformed, or over-deadline view
        # fails closed.
        $secondViewError = $null
        $secondView = $null
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
        try {
            $secondView = Get-VerifierCurrentProcessRecordById $missingProcessId
        } catch { $secondViewError = $_ }
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
        if ($null -ne $secondViewError) { return $false }
        if ($null -ne $secondView) {
            return $false
        }
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
        $remainingTicks = $retryBudgetTicks - $retryBudget.ElapsedTicks
        if ($remainingTicks -le 0) { return $false }
        $remainingMilliseconds = ([double]$remainingTicks * 1000.0) /
            [double][Diagnostics.Stopwatch]::Frequency
        $sleepMilliseconds = [int][Math]::Floor(
            [Math]::Min(50.0, $remainingMilliseconds - 1.0))
        if ($sleepMilliseconds -lt 1) { return $false }
        Start-Sleep -Milliseconds $sleepMilliseconds
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            return $false
        }
    }
    return $false
}

function Set-VerifierLeaseListenerInspection($Lease, $Inspection,
        $PreviewContext = $null, $PreviewOwner = $null,
        $AuthorizedProcessId = $null, $AuthorizedProcessStartTicks = $null,
        $AuthorizationProof = $null) {
    if ($null -eq $Lease -or $null -eq $Inspection) {
        Throw-VerifierInfrastructure 'Cannot persist a missing listener lease inspection.'
    }
    if ($null -ne $PreviewContext) {
        [void](Assert-VerifierContextPreflight $PreviewContext 'listener inspection mutation context')
    }
    # This setter is itself a durable-state boundary.  Validate the complete
    # lease and, when supplied, the complete server association before reading
    # listener state or assigning any durable listener fields.  A later
    # Write-VerifierManifest check cannot undo an earlier in-memory mutation.
    Assert-VerifierDurableLeaseRecord $Lease $PreviewContext `
        'listener inspection lease'
    if ($null -ne $PreviewContext) {
        Assert-VerifierDurableServerLease $PreviewContext `
            'listener inspection server' -AllowMissing
    }
    if (-not (Test-VerifierPreviewOwnerPortFields $PreviewOwner)) {
        Throw-VerifierInfrastructure 'Listener inspection owner carried a malformed or mismatched raw port.'
    }
    if (-not (Test-VerifierListenerInspectionSchema $Inspection $PreviewContext `
            $PreviewOwner $AuthorizationProof)) {
        Throw-VerifierInfrastructure 'Cannot persist a malformed, inconsistent, duplicate, or unauthorized listener inspection.'
    }
    foreach ($listener in @($Inspection.Listeners)) {
        if (-not (Test-VerifierListenerRecordSchema $listener $PreviewContext `
                $PreviewOwner $AuthorizationProof)) {
            Throw-VerifierInfrastructure 'Cannot persist a listener inspection containing an unauthorized listener record.'
        }
    }
    $leasePortProperty = $Lease.PSObject.Properties['Port']
    if ($null -eq $leasePortProperty -or
            -not (Test-VerifierStrictIntegralValue $leasePortProperty.Value 1 65535)) {
        Throw-VerifierInfrastructure 'Lease listener inspection carried a missing or malformed lease port.'
    }
    $leasePort = $leasePortProperty.Value
    foreach ($listener in @($Inspection.Listeners)) {
        $listenerPortProperty = $listener.PSObject.Properties['Port']
        if ($null -eq $listenerPortProperty -or
                -not (Test-VerifierStrictIntegralValue $listenerPortProperty.Value 1 65535) -or
                [long]$listenerPortProperty.Value -ne [long]$leasePort) {
            Throw-VerifierInfrastructure 'Listener inspection port did not match the exact leased port.'
        }
    }
    foreach ($propertyName in @('ListenerProcessId', 'ListenerProcessStartTicks',
            'ListenerOwnerKind', 'ListenerOwnerProof', 'ListenerOwnerEvidence',
            'ListenerInspectionSuccess', 'ListenerInspectionKnown',
            'ListenerHasListeners', 'ListenerAbsent')) {
        if (-not $Lease.PSObject.Properties[$propertyName]) {
            Throw-VerifierInfrastructure "Lease omitted required listener state field '$propertyName'."
        }
    }
    if (-not (Test-VerifierStrictIntegralValue $Lease.ListenerProcessId 0 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictStringValue $Lease.ListenerOwnerKind) -or
            -not (Test-VerifierStrictStringValue $Lease.ListenerOwnerProof) -or
            -not (Test-VerifierStrictStringValue $Lease.ListenerOwnerEvidence) -or
            -not (Test-VerifierStrictBooleanValue $Lease.ListenerInspectionSuccess) -or
            -not (Test-VerifierStrictBooleanValue $Lease.ListenerInspectionKnown)) {
        Throw-VerifierInfrastructure 'Lease listener state contained malformed typed fields.'
    }
    $leaseStart = $Lease.ListenerProcessStartTicks
    if ($null -ne $leaseStart -and
            -not (Test-VerifierStrictIntegralValue $leaseStart 0)) {
        Throw-VerifierInfrastructure 'Lease listener start identity was malformed.'
    }
    if (-not (Test-VerifierListenerOwnerTuple $Lease.ListenerOwnerKind `
            $Lease.ListenerOwnerProof $Lease.ListenerOwnerEvidence `
            $Lease.ListenerProcessId $leaseStart $true)) {
        Throw-VerifierInfrastructure 'Lease listener owner state was not an exact canonical tuple.'
    }
    foreach ($propertyName in @('ListenerHasListeners', 'ListenerAbsent')) {
        $value = $Lease.PSObject.Properties[$propertyName].Value
        if ($null -ne $value -and -not (Test-VerifierStrictBooleanValue $value)) {
            Throw-VerifierInfrastructure "Lease field '$propertyName' was not Boolean or null."
        }
    }
    if (($null -eq $Lease.ListenerHasListeners) -xor ($null -eq $Lease.ListenerAbsent) -or
            ($null -ne $Lease.ListenerHasListeners -and
             $Lease.ListenerAbsent -eq $Lease.ListenerHasListeners)) {
        Throw-VerifierInfrastructure 'Lease listener presence flags were inconsistent.'
    }
    if ($Inspection.HasListeners -and
            -not (Test-VerifierLiveListenerInspectionAuthorization $Inspection `
                $PreviewContext $PreviewOwner $AuthorizedProcessId `
                $AuthorizedProcessStartTicks $AuthorizationProof)) {
        Throw-VerifierInfrastructure 'Positive listener inspection lacked canonical live owner authorization.'
    }

    $hasListeners = $Inspection.HasListeners
    $ownerKind = $Inspection.ListenerOwnerKind
    $ownerProof = $Inspection.ListenerOwnerProof
    $ownerEvidence = $Inspection.ListenerOwnerEvidence
    # All validation is complete before any field is assigned. An absence
    # observation changes only proof-of-inspection flags and never erases a
    # retained positive owner tuple.
    $Lease.ListenerInspectionSuccess = $Inspection.Success
    $Lease.ListenerInspectionKnown = $Inspection.Known
    $Lease.ListenerHasListeners = $hasListeners
    $Lease.ListenerAbsent = -not $hasListeners
    if ($hasListeners) {
        $first = @($Inspection.Listeners)[0]
        $Lease.ListenerProcessId = $first.ProcessId
        $Lease.ListenerProcessStartTicks = $first.ProcessStartTicks
        $Lease.ListenerOwnerKind = $ownerKind
        $Lease.ListenerOwnerProof = $ownerProof
        $Lease.ListenerOwnerEvidence = $ownerEvidence
    }
    if ($Lease.PSObject.Properties['ListenerInspectionUtc']) {
        $Lease.ListenerInspectionUtc = Get-VerifierUtcText
    }
}

function Get-VerifierPortLeaseBoundOwnershipProof($Context, $Lease,
        $ProcessId, $ProcessStartTicks, $OwnerRecord, $PreviewListenerOwner) {
    # A complete browser ownership snapshot can become stale between its
    # descendant walk and listener authorization. Only a typed missing-PID
    # observation may enter the bounded refresh lane, after an independent
    # second process view proves that exact PID is absent.
    $retryBudgetMilliseconds = 500
    # The ownership deadline covers the complete proof, including the initial
    # listener query, schema/capability validation, semantic kernel proof, and
    # every independent process refresh. It is one monotonic budget for this
    # call; retries may not start, reset, or extend it.
    $retryBudget = [Diagnostics.Stopwatch]::StartNew()
    $retryBudgetTicks = [long][Math]::Ceiling(
        ([double][Diagnostics.Stopwatch]::Frequency * $retryBudgetMilliseconds) /
        1000.0)
    $proofStageStopwatch = [Diagnostics.Stopwatch]::StartNew()
    $proofStage = 'listener-query'
    $expectedListenerKeys = $null
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
        }
        $inspection = try {
            if ($null -ne $PreviewListenerOwner) {
                Get-VerifierLoopbackListenerRecords ([int]$Lease.Port) $Context $PreviewListenerOwner `
                    -PreferNetstat
            } else {
                Get-VerifierLoopbackListenerRecords ([int]$Lease.Port) -PreferNetstat
            }
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not inspect the owned process listener for port " +
                "$($Lease.Port): $(Get-VerifierErrorMessage $_) " +
                "[stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
        }
        $proofStage = 'structural-listener-validation'
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
        }
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
        }
        $inspectionSchemaValid = Test-VerifierListenerInspectionSchema $inspection `
            $Context $PreviewListenerOwner $null -StructuralOnly
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
        }
        if (-not $inspectionSchemaValid -or
                -not $inspection.Success -or -not $inspection.Known) {
            Throw-VerifierInfrastructure ("Could not positively determine listener state for owned port " +
                "$($Lease.Port). [stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
        }
        $listeners = @($inspection.Listeners)
        if (-not $inspection.HasListeners) {
            Throw-VerifierInfrastructure ("Owned port $($Lease.Port) has no exact loopback listener; bind was not proven. " +
                "[stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
        }
        foreach ($listener in $listeners) {
            if (-not (Test-VerifierStrictIntegralValue $listener.Port 1 65535) -or
                    [long]$listener.Port -ne [long]$Lease.Port) {
                Throw-VerifierInfrastructure "Owned port $($Lease.Port) returned a listener on a different port."
            }
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
            }
            $listenerSchemaValid = Test-VerifierListenerRecordSchema $listener $Context `
                $PreviewListenerOwner $null -StructuralOnly
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
            }
            if (-not $listenerSchemaValid) {
                Throw-VerifierInfrastructure ("Loopback port $($Lease.Port) returned a malformed or unauthorized listener owner record. " +
                    "[stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
            }
        }
        if ($null -eq $expectedListenerKeys) {
            $expectedListenerKeys = @{}
            foreach ($listener in $listeners) {
                $key = [string]$listener.Port + '|' + [string]$listener.ProcessId + '|' +
                    [string]$listener.ProcessStartTicks
                if ($expectedListenerKeys.ContainsKey($key)) {
                    Throw-VerifierInfrastructure "Loopback port $($Lease.Port) returned duplicate listener identity records."
                }
                $expectedListenerKeys[$key] = $true
            }
        } else {
            if ($inspection.ListenerOwnerKind -cne $script:VerifierUserProcessOwnerKind -and
                    $inspection.ListenerOwnerKind -cne $script:VerifierKernelTransportOwnerKind) {
                Throw-VerifierInfrastructure "Loopback port $($Lease.Port) returned an unsupported listener owner kind '$($inspection.ListenerOwnerKind)'."
            }
            if ($listeners.Count -ne $expectedListenerKeys.Count) {
                Throw-VerifierInfrastructure "Loopback port $($Lease.Port) changed its listener identity set during ownership refresh."
            }
            foreach ($listener in $listeners) {
                $key = [string]$listener.Port + '|' + [string]$listener.ProcessId + '|' +
                    [string]$listener.ProcessStartTicks
                if (-not $expectedListenerKeys.ContainsKey($key)) {
                    Throw-VerifierInfrastructure "Loopback port $($Lease.Port) changed listener PID/start identity during ownership refresh."
                }
            }
        }

        # An HTTP.sys preview listener is owned by the exact run-verified
        # preview server handshake, not by a browser process tree. When the
        # complete listener set is kernel-owned, prove each tuple directly
        # under the same strict ownership deadline. Any user process in the
        # set keeps the complete browser snapshot path below.
        $kernelAuthorizationProof = $null
        $kernelListeners = @($listeners | Where-Object {
            $_.ListenerOwnerKind -ceq $script:VerifierKernelTransportOwnerKind
        })
        if ($kernelListeners.Count -gt 0) {
            if ($null -eq $PreviewListenerOwner -or $null -eq $OwnerRecord) {
                Throw-VerifierInfrastructure ("Loopback port $($Lease.Port) reported a kernel transport listener " +
                    'without a carried preview owner for semantic authorization.')
            }
            # The initial structural listener query gets exactly one semantic
            # authorization token.  Both all-kernel and mixed listener paths
            # carry this same token through every downstream consumer.
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
            }
            $kernelAuthorizationProof = New-VerifierRunOwnedPreviewHttpSysAuthorizationProof `
                $Context $OwnerRecord ([int]$Lease.Port)
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
            }
            if ($null -eq $kernelAuthorizationProof) {
                Throw-VerifierInfrastructure ("Loopback port $($Lease.Port) reported a kernel transport listener " +
                    'without the exact run-owned preview identity handshake.')
            }
        }
        $allKernelPreviewListeners = ($null -ne $PreviewListenerOwner -and
            $listeners.Count -gt 0 -and
            @($listeners | Where-Object {
                $_.ListenerOwnerKind -cne $script:VerifierKernelTransportOwnerKind
            }).Count -eq 0)
        if ($allKernelPreviewListeners) {
            # OS listener structure is complete at this point.  Perform one
            # exact semantic run-owned HTTP.sys handshake, then carry its
            # reference through the in-memory schema/setter checks.
            $proofStage = 'kernel-semantic-authorization'
            foreach ($listener in $listeners) {
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                    Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
                }
                $kernelListenerProof = Test-VerifierRunOwnedPreviewHttpSysListener $Context `
                    $OwnerRecord $listener $kernelAuthorizationProof
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                    Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
                }
                if (-not $kernelListenerProof) {
                    Throw-VerifierInfrastructure ("Loopback port $($Lease.Port) returned a kernel listener " +
                        'that did not match the exact semantic authorization proof.')
                }
            }
            return [pscustomobject]@{
                Inspection = $inspection; Listeners = @($listeners); Snapshot = @()
                AuthorizationProof = $kernelAuthorizationProof
                ProofStage = $proofStage
                ProofElapsedMilliseconds = [long]$proofStageStopwatch.ElapsedMilliseconds
            }
        }

        # Browser ownership requires a fresh root and listener liveness proof
        # on every attempt.  The direct retained-process canary has its own
        # exact Process-object proof and intentionally follows the old path.
        if ($null -eq $PreviewListenerOwner -and $null -ne $OwnerRecord -and
                ($OwnerRecord.PSObject.Properties['Profile'] -or
                 $OwnerRecord.PSObject.Properties['Script'])) {
            $proofStage = 'browser-live-identity'
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
            }
            $currentRoot = Get-VerifierCurrentProcessRecordById ([int]$ProcessId)
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
            }
            if ($null -eq $currentRoot -or
                    [long]$currentRoot.ProcessStartTicks -ne [long]$ProcessStartTicks) {
                Throw-VerifierInfrastructure ("Owned browser PID $ProcessId changed or disappeared during listener ownership refresh. " +
                    "[stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
            }
            foreach ($listener in $listeners) {
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure ("Port lease ownership proof exceeded its bounded monotonic deadline. " +
                "[stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
                }
                $currentListener = Get-VerifierCurrentProcessRecordById ([int]$listener.ProcessId)
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
                }
                if ($null -eq $currentListener -or
                        [long]$currentListener.ProcessStartTicks -ne
                            [long]$listener.ProcessStartTicks) {
                    Throw-VerifierInfrastructure "Listener PID $($listener.ProcessId) changed or disappeared during listener ownership refresh."
                }
            }
        }

        # A genuine browser root whose complete listener set is exactly that
        # same PID/start tuple does not need a broad ancestry census. The
        # retained root/listener refreshes above remain mandatory; the later
        # Test-VerifierProcessIdentity call receives Snapshot=$null and makes
        # its own PID-scoped command, executable, parent, and marker proof.
        # Preview/script, HTTP.sys, direct-process, mixed, malformed, and
        # descendant ownership retain the complete snapshot path.
        $sameRootBrowserListenerSet = Test-VerifierSameRootBrowserListenerEligibility `
            $inspection $listeners $OwnerRecord $ProcessId $ProcessStartTicks `
            $PreviewListenerOwner

        $ownerBrowserPath = if ($null -ne $OwnerRecord -and
                $OwnerRecord.PSObject.Properties['BrowserPath']) {
            [string]$OwnerRecord.BrowserPath
        } else { '' }
        $ownerProfile = if ($null -ne $OwnerRecord -and
                $OwnerRecord.PSObject.Properties['Profile']) {
            [string]$OwnerRecord.Profile
        } else { '' }
        $ownerRunId = if ($null -ne $OwnerRecord -and
                $OwnerRecord.PSObject.Properties['RunId']) {
            [string]$OwnerRecord.RunId
        } else { '' }
        $ownerRepository = if ($null -ne $OwnerRecord -and
                $OwnerRecord.PSObject.Properties['RepositoryIdentity']) {
            [string]$OwnerRecord.RepositoryIdentity
        } else { '' }
        $ownerPort = if ($null -ne $OwnerRecord -and
                $OwnerRecord.PSObject.Properties['CdpPort']) {
            [int]$OwnerRecord.CdpPort
        } elseif ($null -ne $OwnerRecord -and
                $OwnerRecord.PSObject.Properties['Port']) {
            [int]$OwnerRecord.Port
        } else { 0 }
        $ownerScript = if ($null -ne $OwnerRecord -and
                $OwnerRecord.PSObject.Properties['Script']) {
            [string]$OwnerRecord.Script
        } else { '' }
        $ownerNonce = if ($null -ne $OwnerRecord -and
                $OwnerRecord.PSObject.Properties['Nonce']) {
            [string]$OwnerRecord.Nonce
        } else { '' }
        $snapshot = $null
        $ownershipError = $null
        if ($sameRootBrowserListenerSet) {
            $proofStage = 'browser-root-listener-fast-identity'
        } elseif ($null -ne $OwnerRecord -and
                ($OwnerRecord.PSObject.Properties['Profile'] -or
                 $OwnerRecord.PSObject.Properties['Script'])) {
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
            }
            $proofStage = 'browser-ownership-snapshot'
            try {
                $snapshot = @(Get-VerifierBrowserOwnershipSnapshot `
                    $ownerBrowserPath $ownerProfile $ownerRunId $ownerRepository `
                    $ownerPort $ownerScript $ownerNonce)
            } catch {
                $ownershipError = $_
            }
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
            }
        }
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
        }
        $missingProcessId = Get-VerifierMissingProcessId $ownershipError
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
        }
        if ($null -ne $ownershipError -and $missingProcessId -le 0) {
            if (Test-VerifierInfrastructureError $ownershipError) { throw $ownershipError }
            Throw-VerifierInfrastructure ("Could not inspect the owned process for port $($Lease.Port): " +
                "$(Get-VerifierErrorMessage $ownershipError) [stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
        }

        $failedListener = $null
        if ($null -eq $ownershipError) {
            foreach ($listener in $listeners) {
                $listenerKind = $listener.ListenerOwnerKind
                if ($listenerKind -eq $script:VerifierKernelTransportOwnerKind) {
                    if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
                    }
                    $kernelListenerProof = Test-VerifierRunOwnedPreviewHttpSysListener `
                        $Context $OwnerRecord $listener $kernelAuthorizationProof
                    if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
                    }
                    if (-not $kernelListenerProof) {
                        Throw-VerifierInfrastructure ("Loopback port $($Lease.Port) reported a kernel transport listener " +
                            'without the exact run-owned preview identity handshake.')
                    }
                    continue
                }
                if ($listenerKind -ne $script:VerifierUserProcessOwnerKind) {
                    Throw-VerifierInfrastructure "Loopback port $($Lease.Port) returned an unsupported listener owner kind '$listenerKind'."
                }
                $listenerStartProperty = $listener.PSObject.Properties['ProcessStartTicks']
                if ($null -eq $listenerStartProperty -or
                        -not (Test-VerifierStrictIntegralValue $listenerStartProperty.Value 1)) {
                    Throw-VerifierInfrastructure "Loopback port $($Lease.Port) returned a user-process listener without a positive start identity."
                }
                $listenerError = $null
                $belongs = $false
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure ("Port lease ownership proof exceeded its bounded monotonic deadline. " +
                "[stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
                }
                try {
                    $belongs = Test-VerifierListenerBelongsToOwner $OwnerRecord $listener `
                        $ProcessId $ProcessStartTicks $snapshot
                } catch {
                    $listenerError = $_
                }
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure ("Port lease ownership proof exceeded its bounded monotonic deadline. " +
                "[stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
                }
                if (-not $belongs) {
                    if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                        Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
                    }
                    $missingProcessId = Get-VerifierMissingProcessId $listenerError
                    if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                        Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
                    }
                    if ($missingProcessId -le 0) {
                        Throw-VerifierInfrastructure ("Loopback port $($Lease.Port) is listening under foreign " +
                            "PID $($listener.ProcessId)/start $($listener.ProcessStartTicks), not the recorded owner. " +
                            "[stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
                    }
                    $failedListener = $listener
                    break
                }
            }
        }
        if ($null -eq $failedListener -and $missingProcessId -le 0) {
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
            }
            return [pscustomobject]@{
                Inspection = $inspection; Listeners = @($listeners); Snapshot = @($snapshot)
                AuthorizationProof = $kernelAuthorizationProof
                ProofStage = $proofStage
                ProofElapsedMilliseconds = [long]$proofStageStopwatch.ElapsedMilliseconds
            }
        }

        # Root or listener PIDs are never descendants eligible for refresh.
        if (($null -ne $OwnerRecord -and $OwnerRecord.PSObject.Properties['ProcessId'] -and
                [int]$missingProcessId -eq [int]$OwnerRecord.ProcessId) -or
                @($listeners | Where-Object {
                    [int]$_.ProcessId -eq [int]$missingProcessId }).Count -gt 0) {
            Throw-VerifierInfrastructure "Listener ownership refresh observed disappearance of a root/listener PID $missingProcessId."
        }
        if ($attempt -ge 3) {
            Throw-VerifierInfrastructure ("Descendant PID $missingProcessId did not obtain a fresh complete ownership snapshot within the bounded retry budget. " +
                "[stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
        }
        # A second independent process view must prove exact absence before a
        # stale descendant snapshot can be discarded. Presence, replacement,
        # inaccessible, malformed, or over-deadline results fail closed.
        $proofStage = 'missing-descendant-absence-proof'
        $secondView = $null
        $secondViewError = $null
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
        }
        try { $secondView = Get-VerifierCurrentProcessRecordById $missingProcessId } catch {
            $secondViewError = $_
        }
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
        }
        if ($null -ne $secondViewError) {
            if (Test-VerifierInfrastructureError $secondViewError) { throw $secondViewError }
            Throw-VerifierInfrastructure "Could not independently inspect missing descendant PID ${missingProcessId}: $(Get-VerifierErrorMessage $secondViewError)"
        }
        if ($null -ne $secondView) {
            Throw-VerifierInfrastructure ("Descendant PID $missingProcessId remained present or changed during snapshot refresh. " +
                "[stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
        }
        $proofStage = 'missing-descendant-refresh'
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
        }
        $remainingTicks = $retryBudgetTicks - $retryBudget.ElapsedTicks
        if ($remainingTicks -le 0) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
        }
        $remainingMilliseconds = ([double]$remainingTicks * 1000.0) /
            [double][Diagnostics.Stopwatch]::Frequency
        $sleepMilliseconds = [int][Math]::Floor(
            [Math]::Min(50.0, $remainingMilliseconds - 1.0))
        if ($sleepMilliseconds -lt 1) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
        }
        Start-Sleep -Milliseconds $sleepMilliseconds
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Port lease ownership proof exceeded its bounded monotonic deadline.'
        }
    }
    Throw-VerifierInfrastructure ("Port lease ownership proof did not complete. " +
        "[stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
}

function Confirm-VerifierPortLeaseBound($Context, $Lease, $ProcessId,
        $ProcessStartTicks, $OwnerRecord = $null) {
    [void](Assert-VerifierContextPreflight $Context 'port lease bind context' `
        -RequiredProperties @('RunId', 'RepositoryIdentity', 'PortLeaseRoot'))
    # Complete all raw context/server/lease/owner fields before any listener
    # query, process lookup, or durable mutation.  These validators are the
    # single trust boundary for bind ownership and lifecycle state.
    Assert-VerifierDurableServerLease $Context 'port lease bind server'
    Assert-VerifierDurableLeaseRecord $Lease $Context 'port lease bind'
    if (-not (Test-VerifierPreviewOwnerPortFields $OwnerRecord)) {
        Throw-VerifierInfrastructure 'Port lease bind owner carried a malformed or mismatched raw port.'
    }
    if ($null -ne $OwnerRecord -and
            $OwnerRecord.PSObject.Properties['CdpPort']) {
        Assert-VerifierDurableBrowserSession $OwnerRecord $Context `
            'port lease bind browser owner'
    }
    if (-not (Test-VerifierStrictIntegralValue $ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $ProcessStartTicks 1)) {
        Throw-VerifierInfrastructure 'Port lease bind process identity used malformed numeric fields.'
    }
    $claimMutexProperty = $Lease.PSObject.Properties['ClaimMutex']
    if ($null -eq $claimMutexProperty -or
            $null -eq $claimMutexProperty.Value -or
            $claimMutexProperty.Value.GetType() -ne [Threading.Mutex]) {
        Throw-VerifierInfrastructure 'Port lease bind omitted its exact live ClaimMutex handle.'
    }
    if ($Lease.Status -cne 'leased' -or $Lease.ClaimState -cne 'held' -or
            $Lease.ReleaseState -cne 'active' -or
            $Lease.ReleaseJournalState -cne 'active' -or
            -not $Lease.Registered -or $Lease.ReleaseBlocked -or
            $Lease.MutexReleased -or
            $Lease.ClaimName -cne (Get-VerifierPortMutexName $Context ([int]$Lease.Port)) -or
            -not $script:VerifierHeldPortClaims.ContainsKey([string]$Lease.ClaimName) -or
            [string]$script:VerifierHeldPortClaims[[string]$Lease.ClaimName] -cne
                [string]$Context.RunId) {
        Throw-VerifierInfrastructure 'Port lease bind did not carry the exact held claim lifecycle or mutex identity.'
    }
    if ([long]$Lease.ClaimOwnerPid -ne [int]$PID -or
            [long]$Lease.ClaimOwnerStartTicks -ne (Get-VerifierCurrentProcessStartTicks)) {
        Throw-VerifierInfrastructure "Verifier port claim $($Lease.Port) is not held by this run process."
    }
    $previewListenerOwner = $null
    if ($null -ne $OwnerRecord -and $OwnerRecord.PSObject.Properties['Script']) {
        # This is only the structural context marker.  Semantic HTTP.sys
        # authorization is deliberately deferred until after the fresh OS
        # listener query inside Get-VerifierPortLeaseBoundOwnershipProof.
            $previewListenerOwner = $OwnerRecord
    }
    $ownershipProof = Get-VerifierPortLeaseBoundOwnershipProof $Context $Lease `
        $ProcessId $ProcessStartTicks $OwnerRecord $previewListenerOwner
    $inspection = $ownershipProof.Inspection
    $listeners = @($ownershipProof.Listeners)
    $authorizationProof = $ownershipProof.AuthorizationProof
    # Retain the opaque kernel capability on the live lease so release and
    # later listener consumers can validate it without a second semantic auth.
    if ($null -ne $authorizationProof) {
        if (-not $Lease.PSObject.Properties['AuthorizationProof']) {
            Add-Member -InputObject $Lease -MemberType NoteProperty `
                -Name AuthorizationProof -Value $authorizationProof
        } else {
            $Lease.AuthorizationProof = $authorizationProof
        }
    }
    foreach ($listener in $listeners) {
        if (-not (Test-VerifierListenerRecordSchema $listener $Context `
                $previewListenerOwner $ownershipProof.AuthorizationProof)) {
            Throw-VerifierInfrastructure "Loopback port $($Lease.Port) returned a malformed or unauthorized listener owner record."
        }
    }
    # Ownership and the complete inspection were proven before this durable
    # mutation. The setter repeats the schema check as defense in depth.
    Set-VerifierLeaseListenerInspection $Lease $inspection $Context $OwnerRecord `
        $ProcessId $ProcessStartTicks $authorizationProof
    $Lease.BoundProcessId = $ProcessId
    $Lease.BoundProcessStartTicks = $ProcessStartTicks
    $Lease.ListenerProcessId = $listeners[0].ProcessId
    $Lease.ListenerProcessStartTicks = if ($listeners[0].ListenerOwnerKind -eq
        $script:VerifierKernelTransportOwnerKind) { $null } else {
        $listeners[0].ProcessStartTicks
    }
    $Lease.BindValidatedUtc = Get-VerifierUtcText
    $Lease.ClaimState = 'bound'
    $Lease.Status = 'bound'
    if ($Lease.PSObject.Properties['ProcessProofRequired']) {
        $Lease.ProcessProofRequired = $true
    }
    Write-VerifierManifest $Context
}

function Release-VerifierPortLease($Context, $Lease) {
    [void](Assert-VerifierContextPreflight $Context 'port lease release context' `
        -RequiredProperties @('RunId', 'RepositoryIdentity', 'PortLeaseRoot'))
    if ($null -eq $Lease) {
        Throw-VerifierInfrastructure 'Port lease release omitted its cleanup authority lease.'
    }
    if ($null -ne $Context) {
        # Validate the complete server association before inspecting claim
        # state or changing any in-memory lifecycle field.  A malformed server
        # record must not be able to reach a release write/delete path.
        $serverProperty = $Context.PSObject.Properties['Server']
        $server = if ($null -eq $serverProperty) { $null } else { $serverProperty.Value }
        $runOwnedServer = ($null -ne $server -and
            $server.PSObject.Properties['Owner'] -and
            (Test-VerifierStrictStringValue $server.Owner) -and
            $server.Owner -ceq 'run')
        $previewLease = ($Lease.PSObject.Properties['Kind'] -and
            (Test-VerifierStrictStringValue $Lease.Kind) -and
            $Lease.Kind -ceq 'preview')
        if ($runOwnedServer -or $previewLease -or $null -ne $serverProperty) {
            Assert-VerifierDurableServerLease $Context 'port lease release server'
        } else {
            # Explicit ownerless/caller-owned cdp recovery can carry no Server
            # object.  Missing server state is never accepted for preview or a
            # context that claims a run-owned server.
            Assert-VerifierDurableServerLease $Context 'port lease release server' -AllowMissing
        }
    }
    Assert-VerifierDurableLeaseRecord $Lease $Context 'port lease release'
    # Every successful release path, including durable terminal recovery,
    # must begin from the exact canonical listener owner tuple.  Do not let a
    # terminal state or a missing claim bypass owner-proof validation.
    Assert-VerifierDurableListenerOwnerTuple $Lease 'port lease release'
    foreach ($booleanProperty in @('ReleaseBlocked', 'MutexReleased',
            'ProcessProofRequired', 'ProcessTerminationProven', 'ProcessAbsent',
            'ListenerInspectionSuccess', 'ListenerInspectionKnown')) {
        [void](Get-VerifierRequiredBooleanValue $Lease $booleanProperty 'port lease lifecycle')
    }
    foreach ($nullableBooleanProperty in @('ListenerHasListeners', 'ListenerAbsent')) {
        $nullableProperty = $Lease.PSObject.Properties[$nullableBooleanProperty]
        if ($null -eq $nullableProperty -or
                ($null -ne $nullableProperty.Value -and
                 -not (Test-VerifierStrictBooleanValue $nullableProperty.Value))) {
            Throw-VerifierInfrastructure "Port lease lifecycle carried a missing or malformed Boolean '$nullableBooleanProperty'."
        }
    }
    # This is the release boundary for every lifecycle, including recovery of
    # a durable tombstone.  It must precede the terminal fast path so a blocked,
    # pathless, or ambiguously-associated record cannot silently return.
    foreach ($identityProperty in @('LeaseId', 'Kind', 'Path', 'ClaimName')) {
        $identity = $Lease.PSObject.Properties[$identityProperty]
        if ($null -eq $identity -or
                -not (Test-VerifierStrictStringValue $identity.Value) -or
                [String]::IsNullOrWhiteSpace($identity.Value)) {
            Throw-VerifierInfrastructure "Port lease release omitted its non-empty exact claim association '$identityProperty'."
        }
    }
    if ($Lease.ClaimName -cne (Get-VerifierPortMutexName $null ([int]$Lease.Port))) {
        Throw-VerifierInfrastructure 'Port lease release carried a noncanonical claim mutex association.'
    }
    if ($Lease.ReleaseBlocked) {
        Throw-VerifierInfrastructure "Port $($Lease.Port) release is blocked because ownership remains uncertain: $($Lease.ReleaseBlockReason)"
    }
    try {
        [void](Assert-VerifierPhysicalOwnedPath $Context.PortLeaseRoot $Lease.Path)
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure "Could not prove port $($Lease.Port) claim ownership before release: $(Get-VerifierErrorMessage $_)"
    }
    $terminalLifecycle = ($Lease.Status -ceq 'released' -and
        $Lease.ClaimState -ceq 'released' -and
        $Lease.ReleaseState -ceq 'complete' -and
        $Lease.ReleaseJournalState -ceq 'complete')
    if ($terminalLifecycle) {
        if (-not (Test-VerifierSupportedPortLeaseKind $Lease.Kind)) {
            Throw-VerifierInfrastructure 'Port lease terminal release carried an unsupported lease kind.'
        }
        [void](Assert-VerifierDurableLeaseTerminalFields $Lease $Context `
            'port lease terminal release')
        try {
            if (-not (Test-Path -LiteralPath $Lease.Path -PathType Leaf -ErrorAction Stop)) {
                [void](Assert-VerifierDurableLeaseTerminal $Lease $Context `
                    'port lease terminal release')
                if ($script:VerifierHeldPortClaims.ContainsKey([string]$Lease.ClaimName) -and
                        [string]$script:VerifierHeldPortClaims[[string]$Lease.ClaimName] -eq [string]$Context.RunId) {
                    [void]$script:VerifierHeldPortClaims.Remove([string]$Lease.ClaimName)
                }
                return
            }
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure "Could not prove port $($Lease.Port) terminal claim state: $(Get-VerifierErrorMessage $_)"
        }
    }
    foreach ($stateProperty in @('Status', 'ClaimState', 'ReleaseState',
            'ReleaseJournalState')) {
        if ($null -eq $Lease.PSObject.Properties[$stateProperty] -or
                -not (Test-VerifierStrictStringValue $Lease.PSObject.Properties[$stateProperty].Value)) {
            Throw-VerifierInfrastructure "Port lease lifecycle omitted or malformed its state '$stateProperty'."
        }
    }
    foreach ($identityProperty in @('Kind', 'LeaseId', 'ClaimName', 'RunId',
            'RepositoryIdentity', 'WorktreeRoot', 'Path')) {
        if ($null -eq $Lease.PSObject.Properties[$identityProperty] -or
                -not (Test-VerifierStrictStringValue $Lease.PSObject.Properties[$identityProperty].Value)) {
            Throw-VerifierInfrastructure "Port lease lifecycle omitted or malformed its identity '$identityProperty'."
        }
    }
    foreach ($numericProperty in @(
            [pscustomobject]@{ Name = 'Port'; Minimum = 1L; Maximum = 65535L }
            [pscustomobject]@{ Name = 'ClaimOwnerPid'; Minimum = 1L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'ClaimOwnerStartTicks'; Minimum = 1L; Maximum = [long]::MaxValue }
            [pscustomobject]@{ Name = 'BoundProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'BoundProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
            [pscustomobject]@{ Name = 'ListenerProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
        )) {
        if (-not (Test-VerifierStrictIntegralValue $Lease.PSObject.Properties[$numericProperty.Name].Value `
                $numericProperty.Minimum $numericProperty.Maximum)) {
            Throw-VerifierInfrastructure "Port lease lifecycle omitted or malformed its integral '$($numericProperty.Name)'."
        }
    }
    $listenerStartProperty = $Lease.PSObject.Properties['ListenerProcessStartTicks']
    if ($null -eq $listenerStartProperty -or
            ($null -ne $listenerStartProperty.Value -and
             -not (Test-VerifierStrictIntegralValue $listenerStartProperty.Value 0))) {
        Throw-VerifierInfrastructure 'Port lease lifecycle omitted or malformed its listener start identity.'
    }
    # ClaimMutex is a live ownership handle, not durable JSON. Validate its
    # exact runtime type before consulting the claim path or changing any
    # lifecycle field. A malformed replacement (for example a string) must
    # leave both memory and the manifest untouched.
    $claimMutexProperty = $Lease.PSObject.Properties['ClaimMutex']
    if ($null -eq $claimMutexProperty) {
        if (-not $Lease.MutexReleased) {
            Throw-VerifierInfrastructure 'Port lease lifecycle omitted its live claim mutex before OS release.'
        }
    } elseif ($null -ne $claimMutexProperty.Value -and
            $claimMutexProperty.Value.GetType() -ne [Threading.Mutex]) {
        Throw-VerifierInfrastructure 'Port lease lifecycle carried a malformed live claim mutex handle.'
    } elseif ($null -eq $claimMutexProperty.Value -and -not $Lease.MutexReleased) {
        Throw-VerifierInfrastructure 'Port lease lifecycle carried no live claim mutex before OS release.'
    }
    # Validate the complete run-owned server/lease association before any
    # release state or journal field is changed.  Write-VerifierManifest has
    # the same guard, but reaching it after an in-memory mutation would leave a
    # caller-visible lease in a state that was never durably written.
    if ($null -ne $Context -and $Context.PSObject.Properties['Server'] -and
            $null -ne $Context.Server) {
        Assert-VerifierDurableServerLease $Context 'port lease release server'
        if ($Context.Server.Owner -ceq 'run' -and
                $Lease.Kind -ceq 'preview' -and
                -not [object]::ReferenceEquals($Context.Server.Lease, $Lease)) {
            Throw-VerifierInfrastructure 'Port lease release target did not match the run-owned Server.Lease.'
        }
    }
    $claimRemains = $false
    if ($Lease.PSObject.Properties['Path'] -and
            -not [String]::IsNullOrWhiteSpace([string]$Lease.Path)) {
        try {
            $claimRemains = Test-Path -LiteralPath $Lease.Path -PathType Leaf -ErrorAction Stop
        } catch {
            $claimRemains = $true
        }
    }
    $liveHandleRemains = ($null -ne $claimMutexProperty -and
        $null -ne $claimMutexProperty.Value)
    $releaseState = $Lease.ReleaseState
    # Once the manifest durably records that this exact lease's OS mutex was
    # released, a later run may legitimately have rebound the same port. A
    # recovery retry may therefore skip only the *current listener absence*
    # check after exact claim validation; it must never skip claim identity or
    # profile ownership checks and must never remove a different claim.
    $durableOsRelease = (-not $liveHandleRemains -and
        $Lease.MutexReleased -and
        $releaseState -in @('os-released', 'complete', 'claim-delete-failed'))

    try {
        if (-not (Test-VerifierPhysicalChildPath $Context.PortLeaseRoot $Lease.Path)) {
            Throw-VerifierInfrastructure "Refusing to release a port lease outside this run's lease directory."
        }
        if (-not (Test-Path -LiteralPath $Lease.Path -PathType Leaf)) {
            if ($durableOsRelease) {
                # The durable OS-release marker proves that this exact run
                # passed its ownership checks before the claim disappeared.
                # Do not inspect the current port here: a newer exact run may
                # already own and listen on the same port. Never recreate or
                # broadly search for the old claim.
                $Lease.Status = 'released'
                $Lease.ClaimState = 'released'
                $Lease.ReleaseState = 'complete'
                $Lease.MutexReleased = $true
                if ($Lease.PSObject.Properties['ReleaseJournalState']) {
                    $Lease.ReleaseJournalState = 'complete'
                }
                $Context.ManifestWritePhase = 'lease-post-delete'
                try { Write-VerifierManifest $Context } finally {
                    $Context.ManifestWritePhase = ''
                }
                if ($script:VerifierHeldPortClaims.ContainsKey([string]$Lease.ClaimName) -and
                        [string]$script:VerifierHeldPortClaims[[string]$Lease.ClaimName] -eq [string]$Context.RunId) {
                    [void]$script:VerifierHeldPortClaims.Remove([string]$Lease.ClaimName)
                }
                return
            }
            Throw-VerifierInfrastructure "Port claim record is missing for owned port $($Lease.Port)."
        }
        Assert-VerifierNoReparseAncestors $Context.PortLeaseRoot
        if (-not (Test-VerifierPhysicalChildPath $Context.PortLeaseRoot $Lease.Path)) {
            Throw-VerifierInfrastructure 'Port claim physical ownership changed before identity read.'
        }
        $claim = Get-Content -LiteralPath $Lease.Path -Raw | ConvertFrom-Json
        foreach ($claimIdentityProperty in @('protocol', 'runId',
                'repositoryIdentity', 'worktreeRoot', 'kind', 'leaseId',
                'path', 'mutexName')) {
            if ($null -eq $claim.PSObject.Properties[$claimIdentityProperty] -or
                    -not (Test-VerifierStrictStringValue $claim.PSObject.Properties[$claimIdentityProperty].Value)) {
                Throw-VerifierInfrastructure "Port claim '$($Lease.Path)' omitted or malformed its identity '$claimIdentityProperty'."
            }
        }
        foreach ($claimNumericProperty in @(
                [pscustomobject]@{ Name = 'port'; Minimum = 1L; Maximum = 65535L }
                [pscustomobject]@{ Name = 'ownerPid'; Minimum = 1L; Maximum = [int]::MaxValue }
                [pscustomobject]@{ Name = 'ownerStartTicks'; Minimum = 1L; Maximum = [long]::MaxValue }
            )) {
            $claimProperty = $claim.PSObject.Properties[$claimNumericProperty.Name]
            if ($null -eq $claimProperty -or
                    -not (Test-VerifierStrictIntegralValue $claimProperty.Value `
                        $claimNumericProperty.Minimum $claimNumericProperty.Maximum)) {
                Throw-VerifierInfrastructure "Port claim '$($Lease.Path)' omitted or malformed its integral '$($claimNumericProperty.Name)'."
            }
        }
        if ($claim.protocol -ne 'troubleshootjs-verifier-port-claim-v1' -or
                $claim.runId -ne $Context.RunId -or
                $claim.repositoryIdentity -ne $Context.RepositoryIdentity -or
                -not (Test-VerifierCanonicalWindowsPathValue $claim.worktreeRoot `
                    $Context.WorktreeRoot) -or
                $claim.kind -ne $Lease.Kind -or
                $claim.leaseId -ne $Lease.LeaseId -or
                -not (Test-VerifierCanonicalWindowsPathValue $claim.path `
                    $Lease.Path) -or
                [long]$claim.port -ne [long]$Lease.Port -or
                $claim.mutexName -ne $Lease.ClaimName -or
                [long]$claim.ownerPid -ne [long]$Lease.ClaimOwnerPid -or
                [long]$claim.ownerStartTicks -ne [long]$Lease.ClaimOwnerStartTicks) {
            Throw-VerifierInfrastructure "Port claim '$($Lease.Path)' is owned by another run or claim identity."
        }
        # Before the durable release marker, the current process must still
        # own the mutex/claim.  Once the marker is persisted, recovery may
        # finish an exact claim after the original process has gone away.
        if ($releaseState -notin @('complete', 'os-released', 'claim-delete-failed') -and
                ([int]$Lease.ClaimOwnerPid -ne [int]$PID -or
                 [long]$Lease.ClaimOwnerStartTicks -ne (Get-VerifierCurrentProcessStartTicks))) {
            Throw-VerifierInfrastructure "Refusing to release port $($Lease.Port): claim process identity changed."
        }
        $browserPath = if ($Lease.PSObject.Properties['BrowserPath']) { [string]$Lease.BrowserPath } else { '' }
        $leaseRunId = if ($Lease.PSObject.Properties['RunId']) { [string]$Lease.RunId } else { [string]$Context.RunId }
        $leaseRepository = if ($Lease.PSObject.Properties['RepositoryIdentity']) {
            [string]$Lease.RepositoryIdentity
        } else { [string]$Context.RepositoryIdentity }
        if ($Lease.PSObject.Properties['ProfilePath'] -and
                -not [String]::IsNullOrWhiteSpace([string]$Lease.ProfilePath)) {
            try { Assert-VerifierBrowserProfileIsQuiescent ([string]$Lease.ProfilePath) `
                    $browserPath $leaseRunId $leaseRepository ([int]$Lease.Port) } catch {
                $Lease.ProfileInspectionFailed = $true
                throw
            }
        }
        if (-not $durableOsRelease) {
            $releasePreviewOwner = $null
            if ($null -ne $Context -and $Context.PSObject.Properties['Server'] -and
                    $null -ne $Context.Server -and
                    $Context.Server.PSObject.Properties['Lease'] -and
                    [object]::ReferenceEquals($Context.Server.Lease, $Lease) -and
                    $Context.Server.PSObject.Properties['Script']) {
                # Keep release listener inspection structural until after the
                # fresh OS query; Set-VerifierLeaseListenerInspection is the
                # semantic authorization boundary when a kernel listener is
                # actually present.
                $releasePreviewOwner = $Context.Server
            }
            $inspection = if ($null -ne $releasePreviewOwner) {
                Get-VerifierLoopbackListenerRecords ([int]$Lease.Port) $Context $releasePreviewOwner
            } else {
                Get-VerifierLoopbackListenerRecords ([int]$Lease.Port)
            }
            $releaseAuthorizationProof = $null
            $kernelListenerCount = @($inspection.Listeners | Where-Object {
                $_.ListenerOwnerKind -ceq $script:VerifierKernelTransportOwnerKind
            }).Count
            if ($kernelListenerCount -gt 0) {
                $proofProperty = $Lease.PSObject.Properties['AuthorizationProof']
                if ($null -eq $releasePreviewOwner -or
                        $null -eq $proofProperty -or
                        $null -eq $proofProperty.Value) {
                    Throw-VerifierInfrastructure ("Port $($Lease.Port) retained a positive kernel listener " +
                        'without the carried semantic authorization proof.')
                }
                $releaseAuthorizationProof = $proofProperty.Value
            }
            Set-VerifierLeaseListenerInspection $Lease $inspection $Context `
                $releasePreviewOwner $null $null $releaseAuthorizationProof
            if (-not $inspection.Success -or -not $inspection.Known) {
                Throw-VerifierInfrastructure "Could not positively prove that leased port $($Lease.Port) is no longer listening."
            }
            if ($inspection.HasListeners) {
                Throw-VerifierInfrastructure "Refusing to release leased port $($Lease.Port) while an exact loopback listener remains."
            }
        }
        if ($Context.TestHooks.PSObject.Properties['FailNextLeaseRelease'] -and
                [bool]$Context.TestHooks.FailNextLeaseRelease) {
            $Context.TestHooks.FailNextLeaseRelease = $false
            Throw-VerifierInfrastructure 'Injected port-lease release failure before OS ownership release.'
        }

        if ($releaseState -eq 'active' -or $releaseState -eq 'releasing') {
            if (($null -eq $claimMutexProperty -or
                    $null -eq $claimMutexProperty.Value) -and -not $Lease.MutexReleased) {
                Throw-VerifierInfrastructure "Port $($Lease.Port) has no live claim handle; refusing unsafe release."
            }
            # The releasing tombstone is durable before any OS ownership change.
            $Lease.Status = 'releasing'
            $Lease.ClaimState = 'releasing'
            $Lease.ReleaseState = 'releasing'
            if ($Lease.PSObject.Properties['ReleaseJournalState']) {
                $Lease.ReleaseJournalState = 'releasing'
            }
            $Context.ManifestWritePhase = 'lease-releasing'
            Write-VerifierManifest $Context
            $Context.ManifestWritePhase = ''

            if (-not $Lease.MutexReleased) {
                try { $Lease.ClaimMutex.ReleaseMutex() } catch {
                    Throw-VerifierInfrastructure "Could not release the owned port $($Lease.Port) claim: $(Get-VerifierErrorMessage $_)"
                }
                $Lease.MutexReleased = $true
                $Lease.ReleaseState = 'os-released'
                $Lease.ClaimState = 'os-released'
                if ($Lease.PSObject.Properties['ReleaseJournalState']) {
                    $Lease.ReleaseJournalState = 'os-released'
                }
                $Context.ManifestWritePhase = 'lease-releasing'
                Write-VerifierManifest $Context
                $Context.ManifestWritePhase = ''
                # The OS mutex is no longer owned once the durable
                # os-released marker is persisted. Remove only this exact
                # in-process bookkeeping entry so a newer context in the same
                # process can legitimately compete for the port while the old
                # claim tombstone remains available for exact recovery.
                if ($script:VerifierHeldPortClaims.ContainsKey([string]$Lease.ClaimName) -and
                        [string]$script:VerifierHeldPortClaims[[string]$Lease.ClaimName] -eq [string]$Context.RunId) {
                    [void]$script:VerifierHeldPortClaims.Remove([string]$Lease.ClaimName)
                }
            }
            if ($null -ne $Lease.ClaimMutex) {
                if ($Context.TestHooks.PSObject.Properties['FailNextLeaseMutexDispose'] -and
                        [bool]$Context.TestHooks.FailNextLeaseMutexDispose) {
                    $Context.TestHooks.FailNextLeaseMutexDispose = $false
                    Throw-VerifierInfrastructure 'Injected port-lease mutex-dispose failure.'
                }
                try { $Lease.ClaimMutex.Dispose() } catch {
                    Throw-VerifierInfrastructure "Could not dispose the owned port $($Lease.Port) claim handle: $(Get-VerifierErrorMessage $_)"
                }
                $disposedClaimMutex = $Lease.ClaimMutex
                $Lease.ClaimMutex = $null
                if ($script:VerifierHeldPortMutexes.ContainsKey([string]$Lease.ClaimName) -and
                        [object]::ReferenceEquals($script:VerifierHeldPortMutexes[[string]$Lease.ClaimName],
                            $disposedClaimMutex)) {
                    [void]$script:VerifierHeldPortMutexes.Remove([string]$Lease.ClaimName)
                }
            }
            $releaseState = $Lease.ReleaseState
        } elseif ($releaseState -notin @('os-released', 'claim-delete-failed')) {
            if ($releaseState -ne 'complete') {
                Throw-VerifierInfrastructure "Port $($Lease.Port) has an unrecognized durable release state '$releaseState'."
            }
        }

        if ($null -ne $Lease.ClaimMutex -and $Lease.MutexReleased) {
            if ($Context.TestHooks.PSObject.Properties['FailNextLeaseMutexDispose'] -and
                    [bool]$Context.TestHooks.FailNextLeaseMutexDispose) {
                $Context.TestHooks.FailNextLeaseMutexDispose = $false
                Throw-VerifierInfrastructure 'Injected port-lease mutex-dispose failure.'
            }
            try { $Lease.ClaimMutex.Dispose() } catch {
                Throw-VerifierInfrastructure "Could not dispose the owned port $($Lease.Port) claim handle: $(Get-VerifierErrorMessage $_)"
            }
            $disposedClaimMutex = $Lease.ClaimMutex
            $Lease.ClaimMutex = $null
            if ($script:VerifierHeldPortMutexes.ContainsKey([string]$Lease.ClaimName) -and
                    [object]::ReferenceEquals($script:VerifierHeldPortMutexes[[string]$Lease.ClaimName],
                        $disposedClaimMutex)) {
                [void]$script:VerifierHeldPortMutexes.Remove([string]$Lease.ClaimName)
            }
        }

        # Persist a durable pre-delete tombstone.  `complete` means that the
        # mutex is released and all listener/profile/claim identity checks
        # passed, while ClaimState=delete-pending means the exact claim still
        # has to be removed.  A crash in the following window is therefore
        # recoverable without contradictory ownership state.
        $oldStatus = $Lease.Status
        $oldClaimState = $Lease.ClaimState
        $oldReleaseState = $Lease.ReleaseState
        $oldReleaseJournalState = $Lease.ReleaseJournalState
        $oldReleasedUtc = [string]$Lease.ReleasedUtc
        $Lease.Status = 'released'
        $Lease.ClaimState = 'delete-pending'
        $Lease.ReleaseState = 'complete'
        if ($Lease.PSObject.Properties['ReleaseJournalState']) {
            $Lease.ReleaseJournalState = 'pre-delete'
        }
        if ([String]::IsNullOrWhiteSpace([string]$Lease.ReleasedUtc)) {
            $Lease.ReleasedUtc = Get-VerifierUtcText
        }
        try {
            $Context.ManifestWritePhase = 'lease-final'
            Write-VerifierManifest $Context
        } catch {
            $Lease.Status = $oldStatus
            $Lease.ClaimState = $oldClaimState
            $Lease.ReleaseState = $oldReleaseState
            $Lease.ReleaseJournalState = $oldReleaseJournalState
            $Lease.ReleasedUtc = $oldReleasedUtc
            throw
        } finally {
            $Context.ManifestWritePhase = ''
        }

        # The exact claim remains as a durable tombstone until this point. Only
        # the exact claim path validated above may be removed.
        try {
            Remove-VerifierOwnedTree $Context.PortLeaseRoot $Lease.Path
        } catch {
            $Lease.Status = 'releasing'
            $Lease.ClaimState = 'os-released'
            $Lease.ReleaseState = 'claim-delete-failed'
            if ($Lease.PSObject.Properties['ReleaseJournalState']) {
                $Lease.ReleaseJournalState = 'claim-delete-failed'
            }
            try { Write-VerifierManifest $Context } catch { }
            Throw-VerifierInfrastructure "Could not remove the released port $($Lease.Port) claim tombstone: $(Get-VerifierErrorMessage $_)"
        }

        # The claim has now been removed, but the post-delete journal state is
        # persisted before any simulated interruption or terminal in-memory
        # transition.  A crash in this window therefore leaves a durable,
        # idempotent tombstone that recovery can complete without requiring
        # the old port to remain unused.
        $Lease.Status = 'released'
        $Lease.ClaimState = 'delete-pending'
        $Lease.ReleaseState = 'complete'
        $Lease.MutexReleased = $true
        if ($Lease.PSObject.Properties['ReleaseJournalState']) {
            $Lease.ReleaseJournalState = 'post-delete-pending'
        }
        try {
            $Context.ManifestWritePhase = 'lease-post-delete-journal'
            Write-VerifierManifest $Context
        } catch {
            # The pre-delete journal is the last durable state if this write
            # fails.  Keep the in-memory lease at that exact state; the claim
            # is physically gone, but a retry can finish the tombstone without
            # claiming that the post-delete journal was durably committed.
            $Lease.Status = 'released'
            $Lease.ClaimState = 'delete-pending'
            $Lease.ReleaseState = 'complete'
            $Lease.MutexReleased = $true
            if ($Lease.PSObject.Properties['ReleaseJournalState']) {
                $Lease.ReleaseJournalState = 'pre-delete'
            }
            throw
        } finally {
            $Context.ManifestWritePhase = ''
        }

        # Test-only interruption point for the crash window.  The durable
        # post-delete marker remains, so Complete-VerifierRun can finish this
        # lease on the next attempt without treating the missing claim as a
        # foreign resource or requiring current listener absence.
        if ($Context.TestHooks.PSObject.Properties['FailNextPostDeleteBeforeFinalState'] -and
                [bool]$Context.TestHooks.FailNextPostDeleteBeforeFinalState) {
            $Context.TestHooks.FailNextPostDeleteBeforeFinalState = $false
            Throw-VerifierInfrastructure 'Injected post-delete/pre-final-state interruption.'
        }

        # Persist the terminal state after deletion. If this write fails, keep
        # the in-memory record at the durable post-delete-pending state so a
        # same-process retry cannot falsely report cleanup complete and the
        # already-persisted journal remains consistent with the missing claim.
        $Lease.Status = 'released'
        $Lease.ClaimState = 'released'
        $Lease.ReleaseState = 'complete'
        $Lease.MutexReleased = $true
        if ($Lease.PSObject.Properties['ReleaseJournalState']) {
            $Lease.ReleaseJournalState = 'complete'
        }
        try {
            $Context.ManifestWritePhase = 'lease-post-delete'
            Write-VerifierManifest $Context
        } catch {
            # The post-delete journal is the last durable truth.  If the
            # terminal write fails, retain exactly that state in memory so a
            # retry cannot report a terminal lease that was never durably
            # committed.
            $Lease.Status = 'released'
            $Lease.ClaimState = 'delete-pending'
            $Lease.ReleaseState = 'complete'
            $Lease.MutexReleased = $true
            if ($Lease.PSObject.Properties['ReleaseJournalState']) {
                $Lease.ReleaseJournalState = 'post-delete-pending'
            }
            throw
        } finally {
            $Context.ManifestWritePhase = ''
        }
        if ($script:VerifierHeldPortClaims.ContainsKey([string]$Lease.ClaimName) -and
                [string]$script:VerifierHeldPortClaims[[string]$Lease.ClaimName] -eq [string]$Context.RunId) {
            [void]$script:VerifierHeldPortClaims.Remove([string]$Lease.ClaimName)
        }
    } catch {
        $Context.ManifestWritePhase = ''
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not release port lease $($Lease.Port): " +
            (Get-VerifierErrorMessage $_))
    }
}

function New-VerifierBrowserLease($Context, $RouteName, $BrowserPath = '') {
    if (-not (Test-VerifierStrictStringValue $RouteName) -or
            [String]::IsNullOrWhiteSpace($RouteName)) {
        Throw-VerifierInfrastructure 'A browser lease route name must be one exact non-empty string before allocation.'
    }
    if ($null -ne $BrowserPath -and -not (Test-VerifierStrictStringValue $BrowserPath)) {
        Throw-VerifierInfrastructure 'A browser lease BrowserPath must be an exact string before allocation.'
    }
    [void](Assert-VerifierContextPreflight $Context 'browser lease context' -RequireDurable)
    if ([String]::IsNullOrWhiteSpace($BrowserPath)) {
        $BrowserPath = Resolve-VerifierBrowserPath ''
    } else {
        $BrowserPath = Get-VerifierFullPath $BrowserPath
    }
    # Pass the resolved executable into the lease before its first durable
    # manifest write.  A browser lease must never have a transient/pathless
    # executable identity in the ownership ledger.
    $lease = New-VerifierPortLease $Context 'cdp' 0 $BrowserPath
    $routeId = [Guid]::NewGuid().ToString('N')
    $routeRoot = Join-Path (Join-Path $Context.RunRoot 'browser') $routeId
    $profile = Join-Path $routeRoot 'profile'
    $browserLeaseSessionRecord = $null
    $sessionAdded = $false
    try {
        [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $routeRoot)
        New-Item -ItemType Directory -Path $profile -ErrorAction Stop | Out-Null
        [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $routeRoot -ValidateTree)
        [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $profile -ValidateTree)
    } catch {
        $profileFailure = $_
        $rollbackErrors = New-Object Collections.ArrayList
        try {
            if (Test-Path -LiteralPath $profile -ErrorAction Stop) {
                Remove-VerifierBrowserProfile $Context ([pscustomobject]@{ Profile = $profile })
            }
            if (Test-Path -LiteralPath $routeRoot -ErrorAction Stop) {
                Remove-VerifierOwnedTree $Context.RunRoot $routeRoot
            }
        } catch {
            [void]$rollbackErrors.Add('browser profile rollback: ' + (Get-VerifierErrorMessage $_))
        }
        try { Release-VerifierPortLease $Context $lease } catch {
            [void]$rollbackErrors.Add('browser lease rollback: ' + (Get-VerifierErrorMessage $_))
        }
        if ($rollbackErrors.Count -gt 0) {
            Throw-VerifierInfrastructure ('Could not create isolated browser profile and exact rollback was not proven: ' +
                (Get-VerifierErrorMessage $profileFailure) + '; ' + ($rollbackErrors -join '; '))
        }
        Throw-VerifierInfrastructure "Could not create isolated browser profile: $(Get-VerifierErrorMessage $profileFailure)"
    }
    try {
        $lease.ProfilePath = Get-VerifierFullPath $profile
        $lease.OwnerType = 'browser'
        $lease.BrowserPath = [string]$BrowserPath
        # Construct and validate the complete live session schema before adding
        # it to the durable context. In particular, WorktreeRoot is part of the
        # run identity and must never be omitted from the first session write.
        $browserLeaseSessionRecord = [pscustomobject]@{
            RunId = $Context.RunId; RepositoryIdentity = $Context.RepositoryIdentity
            WorktreeRoot = $Context.WorktreeRoot
            RouteId = $routeId; RouteName = $RouteName; CdpPort = $lease.Port
            BrowserPath = [string]$lease.BrowserPath
            Lease = $lease; Profile = Get-VerifierFullPath $profile
            ProcessId = 0; ProcessStartTicks = 0; ProcessParentProcessId = 0
            ProcessParentProcessStartTicks = 0
            ProcessCommandLine = ''; TargetId = ''; ExpectedUrl = ''
            Status = 'leased'; CleanupResult = 'pending'; Error = ''
            ProfileInspectionFailed = $false; ProfileProcessScanCompleted = $false
            Runtime = [pscustomobject]@{ Browser = $null; Socket = $null }
        }
        Assert-VerifierDurableBrowserSession $browserLeaseSessionRecord $Context `
            'browser lease session'
        [void]$Context.BrowserSessions.Add($browserLeaseSessionRecord)
        $sessionAdded = $true
        if ($Context.PSObject.Properties['TestHooks'] -and
                $Context.TestHooks.PSObject.Properties['FailNextBrowserLeaseManifestWrite'] -and
                [bool]$Context.TestHooks.FailNextBrowserLeaseManifestWrite) {
            $Context.TestHooks.FailNextBrowserLeaseManifestWrite = $false
            Throw-VerifierInfrastructure 'Injected browser-lease manifest-write failure.'
        }
        Write-VerifierManifest $Context
        return $browserLeaseSessionRecord
    } catch {
        $sessionFailure = $_
        $rollbackErrors = New-Object Collections.ArrayList
        if ($sessionAdded) {
            try {
                [void]$Context.BrowserSessions.Remove($browserLeaseSessionRecord)
            } catch {
                [void]$rollbackErrors.Add('browser session rollback: ' + (Get-VerifierErrorMessage $_))
            }
            if (@($Context.BrowserSessions | Where-Object {
                    [object]::ReferenceEquals($_, $browserLeaseSessionRecord)
                }).Count -ne 0) {
                [void]$rollbackErrors.Add('browser session remained in the live session ledger')
            }
        }
        try {
            if (Test-Path -LiteralPath $profile -ErrorAction Stop) {
                Remove-VerifierBrowserProfile $Context ([pscustomobject]@{ Profile = $profile })
            }
            if (Test-Path -LiteralPath $routeRoot -ErrorAction Stop) {
                Remove-VerifierOwnedTree $Context.RunRoot $routeRoot
            }
        } catch {
            [void]$rollbackErrors.Add('browser profile rollback: ' + (Get-VerifierErrorMessage $_))
        }
        try { Release-VerifierPortLease $Context $lease } catch {
            [void]$rollbackErrors.Add('browser lease rollback: ' + (Get-VerifierErrorMessage $_))
        }
        if ($rollbackErrors.Count -gt 0) {
            Throw-VerifierInfrastructure ('Browser lease construction failed and exact rollback was not proven: ' +
                (Get-VerifierErrorMessage $sessionFailure) + '; ' + ($rollbackErrors -join '; '))
        }
        if (Test-VerifierInfrastructureError $sessionFailure) { throw $sessionFailure }
        Throw-VerifierInfrastructure ('Browser lease construction failed: ' +
            (Get-VerifierErrorMessage $sessionFailure))
    }
}

function Select-VerifierRelevantProcessRecords($Snapshot, $BrowserPath = '',
        $Profile = '', $RunId = '', $RepositoryIdentity = '',
        $Port = 0, $Script = '', $Nonce = '') {
    foreach ($inputString in @(
            [pscustomobject]@{ Name = 'BrowserPath'; Value = $BrowserPath }
            [pscustomobject]@{ Name = 'Profile'; Value = $Profile }
            [pscustomobject]@{ Name = 'RunId'; Value = $RunId }
            [pscustomobject]@{ Name = 'RepositoryIdentity'; Value = $RepositoryIdentity }
            [pscustomobject]@{ Name = 'Script'; Value = $Script }
            [pscustomobject]@{ Name = 'Nonce'; Value = $Nonce }
        )) {
        if (-not (Test-VerifierStrictStringValue $inputString.Value)) {
            Throw-VerifierInfrastructure "Process ownership query carried a malformed $($inputString.Name) before OS records were inspected."
        }
    }
    if (-not (Test-VerifierStrictIntegralValue $Port 0 65535)) {
        Throw-VerifierInfrastructure 'Process ownership query carried a malformed port before OS records were inspected.'
    }
    $executableName = ''
    if (-not [String]::IsNullOrWhiteSpace($BrowserPath)) {
        try { $executableName = [IO.Path]::GetFileName($BrowserPath) } catch {
            Throw-VerifierInfrastructure "Could not derive the configured browser executable name: $(Get-VerifierErrorMessage $_)"
        }
    }
    $selected = New-Object Collections.ArrayList
    $seen = @{}
    foreach ($item in @($Snapshot)) {
        # PID 0/System Idle and records with no usable PID are irrelevant only
        # after their raw fields have been checked.  Never let PowerShell turn
        # malformed WMI values into a selectable identity.
        if ($null -eq $item) { continue }
        $processIdProperty = $item.PSObject.Properties['ProcessId']
        $nameProperty = $item.PSObject.Properties['Name']
        $commandProperty = $item.PSObject.Properties['CommandLine']
        if ($null -ne $nameProperty -and $null -ne $nameProperty.Value -and
                -not (Test-VerifierStrictStringValue $nameProperty.Value)) {
            Throw-VerifierInfrastructure 'A process ownership record carried a malformed Name field.'
        }
        if ($null -ne $commandProperty -and $null -ne $commandProperty.Value -and
                -not (Test-VerifierStrictStringValue $commandProperty.Value)) {
            Throw-VerifierInfrastructure 'A process ownership record carried a malformed CommandLine field.'
        }
        $name = if ($null -ne $nameProperty -and $null -ne $nameProperty.Value) {
            $nameProperty.Value
        } else { '' }
        $command = if ($null -ne $commandProperty -and $null -ne $commandProperty.Value) {
            $commandProperty.Value
        } else { '' }
        if ($null -eq $processIdProperty) {
            # A marker-bearing record without a PID cannot be safely owned;
            # an unrelated record remains ignorable.
            $processId = 0
        } elseif (-not (Test-VerifierStrictIntegralValue $processIdProperty.Value `
                0 ([int]::MaxValue))) {
            Throw-VerifierInfrastructure 'A process ownership record carried a malformed ProcessId field.'
        } else {
            $processId = [int]$processIdProperty.Value
        }
        $nameMatch = (-not [String]::IsNullOrWhiteSpace($executableName) -and
            $name -ieq $executableName)
        $profileMatch = (-not [String]::IsNullOrWhiteSpace($Profile) -and
            -not [String]::IsNullOrWhiteSpace($command) -and
            (Test-VerifierCommandLineSwitch $command '--user-data-dir' $Profile))
        $runMatch = (-not [String]::IsNullOrWhiteSpace($RunId) -and
            -not [String]::IsNullOrWhiteSpace($command) -and
            (Test-VerifierCommandLineSwitch $command '--tsj-verifier-run' $RunId))
        $portMatch = ($Port -gt 0 -and -not [String]::IsNullOrWhiteSpace($command) -and
            (Test-VerifierCommandLineSwitch $command '--remote-debugging-port' ([string]$Port)))
        $scriptMatch = (-not [String]::IsNullOrWhiteSpace($Script) -and
            -not [String]::IsNullOrWhiteSpace($command) -and
            (Test-VerifierCommandLinePath $command $Script))
        $nonceMatch = (-not [String]::IsNullOrWhiteSpace($Nonce) -and
            -not [String]::IsNullOrWhiteSpace($command) -and
            (Test-VerifierCommandLineSwitch $command '-VerifierNonce' $Nonce))
        $identityMatch = ($profileMatch -and $runMatch) -or
            ($runMatch -and $portMatch) -or ($scriptMatch -and $portMatch) -or
            ($scriptMatch -and $nonceMatch)
        if ($null -ne $nameProperty -and $null -eq $nameProperty.Value -and
                ($profileMatch -or $runMatch -or $scriptMatch -or $nonceMatch)) {
            Throw-VerifierInfrastructure 'A relevant browser candidate carried an explicit null Name field.'
        }
        if (-not ($nameMatch -or $profileMatch -or $identityMatch)) { continue }
        if ($processId -le 0) {
            Throw-VerifierInfrastructure 'A relevant browser candidate omitted a positive ProcessId.'
        }
        if (-not $commandProperty -or
                [String]::IsNullOrWhiteSpace($command)) {
            Throw-VerifierInfrastructure "A relevant browser candidate PID $processId had an inaccessible command line."
        }
        $parentProperty = $item.PSObject.Properties['ParentProcessId']
        if ($null -eq $parentProperty -or $null -eq $parentProperty.Value) {
            Throw-VerifierInfrastructure "A relevant browser candidate PID $processId had an inaccessible parent PID."
        }
        if (-not (Test-VerifierStrictIntegralValue $parentProperty.Value `
                0 ([int]::MaxValue))) {
            Throw-VerifierInfrastructure "A relevant browser candidate PID $processId had an invalid parent PID."
        }
        if ($seen.ContainsKey($processId)) {
            Throw-VerifierInfrastructure "The relevant browser snapshot contained duplicate PID $processId."
        }
        $seen[$processId] = $true
        [void]$selected.Add($item)
    }
    return @($selected)
}

function Assert-VerifierProcessSnapshotComplete($Snapshot, [string]$Purpose) {
    $seen = @{}
    foreach ($item in @($Snapshot)) {
        if ($null -eq $item -or
                -not $item.PSObject.Properties['ProcessId'] -or
                -not $item.PSObject.Properties['ParentProcessId'] -or
                -not $item.PSObject.Properties['CommandLine']) {
            Throw-VerifierInfrastructure "The $Purpose process snapshot omitted required identity data."
        }
        if (-not (Test-VerifierStrictIntegralValue $item.ProcessId `
                1 ([int]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $item.ParentProcessId `
                    0 ([int]::MaxValue)) -or
                -not (Test-VerifierStrictStringValue $item.CommandLine) -or
                [String]::IsNullOrWhiteSpace($item.CommandLine)) {
            Throw-VerifierInfrastructure "The $Purpose process snapshot contained an invalid PID record."
        }
        $processId = [int]$item.ProcessId
        if ($seen.ContainsKey($processId)) {
            Throw-VerifierInfrastructure "The $Purpose process snapshot contained duplicate PID $processId."
        }
        $seen[$processId] = $true
        $process = Get-VerifierProcessById $processId
        if ($null -eq $process) {
            # Inspect only captured data after selecting the same failure.
            # These counts describe existing scopes; they grant no ownership.
            $priorBindingCount = 0
            foreach ($scopeRegistration in @($script:VerifierBrowserDrainScopes)) {
                foreach ($scopeBinding in @($scopeRegistration.Item3)) {
                    if ([int]$scopeBinding.Item4.Item1 -eq $processId) { $priorBindingCount++ }
                }
            }
            $processType = [regex]::Match($item.CommandLine,
                '(?:^|[\s"])--type=([A-Za-z0-9_.:-]+)').Groups[1].Value
            $utilitySubtype = [regex]::Match($item.CommandLine,
                '(?:^|[\s"])--utility-sub-type=([A-Za-z0-9_.:-]+)').Groups[1].Value
            $callerNames = @((Get-PSCallStack) | Select-Object -ExpandProperty FunctionName) -join '>'
            Throw-VerifierInfrastructureMissingProcess `
                ("The $Purpose process snapshot contained PID $processId that could not be inspected. " +
                    "[activeDrains=$($script:VerifierBrowserDrainScopes.Count); priorBindings=$priorBindingCount; " +
                    "parent=$($item.ParentProcessId); processType=$processType; utilitySubtype=$utilitySubtype; " +
                    "callers=$callerNames]") $processId
        }
        try {
            if ((Get-VerifierProcessStartTicks $process) -le 0) {
                Throw-VerifierInfrastructure "The $Purpose process snapshot contained unknown start identity for PID $processId."
            }
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure "Could not inspect start identity for PID $processId in the $Purpose process snapshot: $(Get-VerifierErrorMessage $_)"
        }
    }
}

function Get-VerifierPreviewAdoptionCandidateRecords($Snapshot,
        $PreviewScript, $Port, $ExpectedProcessName = 'powershell.exe') {
    [void](Assert-VerifierRawPathValue $PreviewScript 'preview adoption script')
    if (-not (Test-VerifierStrictIntegralValue $Port 1 65535)) {
        Throw-VerifierInfrastructure 'Preview adoption requires an exact integral port before process selection.'
    }
    if (-not (Test-VerifierStrictStringValue $ExpectedProcessName) -or
            [String]::IsNullOrWhiteSpace($ExpectedProcessName)) {
        Throw-VerifierInfrastructure 'Preview adoption requires an exact executable name before process selection.'
    }
    $canonicalPreviewScript = Get-VerifierCanonicalWindowsPath ([string]$PreviewScript)
    if ([String]::IsNullOrWhiteSpace($canonicalPreviewScript)) {
        Throw-VerifierInfrastructure 'Preview adoption script canonicalization returned no path.'
    }
    $matches = New-Object Collections.ArrayList
    $seen = @{}
    foreach ($candidate in @($Snapshot)) {
        if ($null -eq $candidate) { continue }
        $processIdProperty = $candidate.PSObject.Properties['ProcessId']
        $parentProperty = $candidate.PSObject.Properties['ParentProcessId']
        $nameProperty = $candidate.PSObject.Properties['Name']
        $commandProperty = $candidate.PSObject.Properties['CommandLine']
        if ($null -ne $processIdProperty -and $null -ne $processIdProperty.Value -and
                -not (Test-VerifierStrictIntegralValue $processIdProperty.Value `
                    0 ([int]::MaxValue))) {
            Throw-VerifierInfrastructure 'A preview adoption record carried a malformed raw ProcessId.'
        }
        if ($null -ne $parentProperty -and $null -ne $parentProperty.Value -and
                -not (Test-VerifierStrictIntegralValue $parentProperty.Value `
                    0 ([int]::MaxValue))) {
            Throw-VerifierInfrastructure 'A preview adoption record carried a malformed raw ParentProcessId.'
        }
        if ($null -ne $nameProperty -and $null -ne $nameProperty.Value -and
                -not (Test-VerifierStrictStringValue $nameProperty.Value)) {
            Throw-VerifierInfrastructure 'A preview adoption record carried a malformed raw Name.'
        }
        if ($null -ne $commandProperty -and $null -ne $commandProperty.Value -and
                -not (Test-VerifierStrictStringValue $commandProperty.Value)) {
            Throw-VerifierInfrastructure 'A preview adoption record carried a malformed raw CommandLine.'
        }
        $name = if ($null -ne $nameProperty -and $null -ne $nameProperty.Value) {
            [string]$nameProperty.Value
        } else { '' }
        $command = if ($null -ne $commandProperty -and $null -ne $commandProperty.Value) {
            [string]$commandProperty.Value
        } else { '' }
        $nameMatch = $name.Equals([string]$ExpectedProcessName,
            [StringComparison]::OrdinalIgnoreCase)
        $scriptPortMatch = (-not [String]::IsNullOrWhiteSpace($command) -and
            (Test-VerifierCommandLinePath $command $canonicalPreviewScript) -and
            (Test-VerifierCommandLineSwitch $command '-Port' ([string]$Port)))
        if (-not $nameMatch -and -not $scriptPortMatch) { continue }
        if ($scriptPortMatch -and -not $nameMatch) {
            Throw-VerifierInfrastructure ('A preview adoption candidate carried the owned script/port markers ' +
                "but an unexpected executable name '$name'.")
        }
        # A same-name PowerShell process is not relevant until its raw command
        # line carries both exact preview markers. A marked record is relevant
        # even if its Name field is missing or foreign and must fail closed.
        if (-not $scriptPortMatch) { continue }
        [void](Assert-VerifierRawProcessRecord $candidate `
            'preview adoption candidate' -RequirePositiveIdentity -RequireCommandLine)
        $processId = [int]$processIdProperty.Value
        if ($seen.ContainsKey($processId)) {
            Throw-VerifierInfrastructure "Preview adoption contained duplicate candidate PID $processId."
        }
        $seen[$processId] = $true
        [void]$matches.Add($candidate)
    }
    return @($matches)
}

function Test-VerifierProcessInspectionFallbackError($ErrorRecord) {
    $exception = if ($ErrorRecord -and $ErrorRecord.PSObject.Properties['Exception'] -and
            $null -ne $ErrorRecord.Exception) { $ErrorRecord.Exception } else { $ErrorRecord }
    while ($null -ne $exception) {
        if ($exception -is [UnauthorizedAccessException] -or
                $exception -is [Security.SecurityException]) {
            return $true
        }
        $hresultProperty = $exception.PSObject.Properties['HResult']
        if ($null -ne $hresultProperty -and $null -ne $hresultProperty.Value) {
            try {
                $hresultText = ([int]$hresultProperty.Value).ToString('X8',
                    [Globalization.CultureInfo]::InvariantCulture)
                if ($hresultText -in @('80041003', '8004100E', '80041010',
                        '800706BA', '800706BE', '80070005')) {
                    return $true
                }
            } catch { }
        }
        $exception = if ($exception.PSObject.Properties['InnerException']) {
            $exception.InnerException
        } else { $null }
    }
    $message = Get-VerifierErrorMessage $ErrorRecord
    # This is deliberately limited to provider/permission availability. A
    # malformed WMI record reaches the existing raw-field validator and must
    # never be silently replaced by a second, potentially different view.
    return $message -match '(?i)(access is denied|access denied|permission.*denied|unauthorized|wmi.*(unavailable|not available)|cim.*(unavailable|not available)|rpc server.*(unavailable|not available)|invalid namespace|invalid class|provider.*(not loaded|failed|unavailable)|not supported on this platform|cannot be contacted|does not exist)'
}

function Initialize-VerifierNativeProcessInspection() {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
        Throw-VerifierInfrastructure 'Native Windows process inspection is unavailable on this platform.'
    }
    $typeName = 'TroubleshootJsVerifier.NativeProcessInspection'
    $loadedType = $typeName -as [type]
    if ($null -ne $loadedType) { return $loadedType }
    $source = @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;

namespace TroubleshootJsVerifier {
    public sealed class NativeProcessRecord {
        public int ProcessId { get; set; }
        public int ParentProcessId { get; set; }
        public string Name { get; set; }
        public string CommandLine { get; set; }
        public string ExecutablePath { get; set; }
    }

    public static class NativeProcessInspection {
        private const uint TH32CS_SNAPPROCESS = 0x00000002;
        private const uint PROCESS_QUERY_INFORMATION = 0x00000400;
        private const uint PROCESS_QUERY_LIMITED_INFORMATION = 0x00001000;
        private const int ProcessBasicInformation = 0;
        private const int ProcessCommandLineInformation = 60;
        private const int STATUS_INFO_LENGTH_MISMATCH = unchecked((int)0xC0000004);
        private const int STATUS_BUFFER_TOO_SMALL = unchecked((int)0xC0000023);

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct PROCESSENTRY32 {
            public uint dwSize;
            public uint cntUsage;
            public uint th32ProcessID;
            public IntPtr th32DefaultHeapID;
            public uint th32ModuleID;
            public uint cntThreads;
            public uint th32ParentProcessID;
            public int pcPriClassBase;
            public uint dwFlags;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 260)]
            public string szExeFile;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct PROCESS_BASIC_INFORMATION {
            public IntPtr Reserved1;
            public IntPtr PebBaseAddress;
            public IntPtr Reserved2_0;
            public IntPtr Reserved2_1;
            public IntPtr UniqueProcessId;
            public IntPtr InheritedFromUniqueProcessId;
        }

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr CreateToolhelp32Snapshot(uint flags, uint processId);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool Process32FirstW(IntPtr snapshot, ref PROCESSENTRY32 entry);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool Process32NextW(IntPtr snapshot, ref PROCESSENTRY32 entry);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool CloseHandle(IntPtr handle);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr OpenProcess(uint access, bool inheritHandle, uint processId);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool QueryFullProcessImageNameW(IntPtr processHandle,
            uint flags, StringBuilder imagePath, ref uint size);

        [DllImport("ntdll.dll")]
        private static extern int NtQueryInformationProcess(IntPtr processHandle,
            int informationClass, IntPtr information, int informationLength,
            out int returnLength);

        private static bool IsInvalidHandle(IntPtr handle) {
            return handle == IntPtr.Zero || handle == new IntPtr(-1);
        }

        private static IntPtr OpenQueryHandle(int processId) {
            IntPtr handle = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false,
                unchecked((uint)processId));
            if (IsInvalidHandle(handle)) {
                handle = OpenProcess(PROCESS_QUERY_INFORMATION, false,
                    unchecked((uint)processId));
            }
            return handle;
        }

        private static string TryQueryImagePath(int processId) {
            IntPtr handle = OpenQueryHandle(processId);
            if (IsInvalidHandle(handle)) { return null; }
            try {
                StringBuilder path = new StringBuilder(32768);
                uint length = (uint)path.Capacity;
                if (!QueryFullProcessImageNameW(handle, 0, path, ref length) || length == 0) {
                    return null;
                }
                return path.ToString(0, checked((int)length));
            } catch {
                return null;
            } finally {
                CloseHandle(handle);
            }
        }

        private static string TryQueryCommandLine(int processId) {
            IntPtr handle = OpenQueryHandle(processId);
            if (IsInvalidHandle(handle)) { return null; }
            try {
                int returnLength;
                int status = NtQueryInformationProcess(handle,
                    ProcessCommandLineInformation, IntPtr.Zero, 0, out returnLength);
                int bufferSize = returnLength > 0 ? returnLength + 2 : 4096;
                for (int attempt = 0; attempt < 4; attempt++) {
                    if (bufferSize < 64) { bufferSize = 64; }
                    if (bufferSize > 1048576) { return null; }
                    IntPtr buffer = Marshal.AllocHGlobal(bufferSize);
                    try {
                        status = NtQueryInformationProcess(handle,
                            ProcessCommandLineInformation, buffer, bufferSize,
                            out returnLength);
                        if (status == STATUS_INFO_LENGTH_MISMATCH ||
                                status == STATUS_BUFFER_TOO_SMALL) {
                            int requested = returnLength > bufferSize ?
                                returnLength + 2 : bufferSize * 2;
                            bufferSize = requested > bufferSize ? requested : bufferSize + 1024;
                            continue;
                        }
                        if (status != 0) { return null; }
                        ushort length = unchecked((ushort)Marshal.ReadInt16(buffer, 0));
                        ushort maximumLength = unchecked((ushort)Marshal.ReadInt16(buffer, 2));
                        int pointerOffset = IntPtr.Size == 8 ? 8 : 4;
                        IntPtr text = Marshal.ReadIntPtr(buffer, pointerOffset);
                        if (length == 0) { return string.Empty; }
                        if (text == IntPtr.Zero || (length % 2) != 0 ||
                                length > maximumLength || length > 65534) {
                            return null;
                        }
                        long bufferStart = buffer.ToInt64();
                        long bufferEnd = bufferStart + bufferSize;
                        long textStart = text.ToInt64();
                        long textEnd = textStart + length;
                        if (textStart < bufferStart || textEnd < textStart ||
                                textEnd > bufferEnd) {
                            return null;
                        }
                        return Marshal.PtrToStringUni(text, length / 2);
                    } catch {
                        return null;
                    } finally {
                        Marshal.FreeHGlobal(buffer);
                    }
                }
                return null;
            } catch {
                return null;
            } finally {
                CloseHandle(handle);
            }
        }

        private static int TryQueryNtParentProcessId(int processId) {
            IntPtr handle = OpenQueryHandle(processId);
            if (IsInvalidHandle(handle)) { return 0; }
            IntPtr buffer = IntPtr.Zero;
            try {
                int length = Marshal.SizeOf(typeof(PROCESS_BASIC_INFORMATION));
                buffer = Marshal.AllocHGlobal(length);
                int returnLength;
                int status = NtQueryInformationProcess(handle,
                    ProcessBasicInformation, buffer, length, out returnLength);
                if (status != 0) { return 0; }
                PROCESS_BASIC_INFORMATION information =
                    (PROCESS_BASIC_INFORMATION)Marshal.PtrToStructure(buffer,
                        typeof(PROCESS_BASIC_INFORMATION));
                long parent = information.InheritedFromUniqueProcessId.ToInt64();
                if (parent <= 0 || parent > Int32.MaxValue) { return 0; }
                return (int)parent;
            } catch {
                return 0;
            } finally {
                if (buffer != IntPtr.Zero) { Marshal.FreeHGlobal(buffer); }
                CloseHandle(handle);
            }
        }

        public static NativeProcessRecord[] GetSnapshot() {
            IntPtr snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
            if (IsInvalidHandle(snapshot)) {
                throw new Win32Exception(Marshal.GetLastWin32Error(),
                    "CreateToolhelp32Snapshot failed.");
            }
            try {
                List<NativeProcessRecord> records = new List<NativeProcessRecord>();
                PROCESSENTRY32 entry = new PROCESSENTRY32();
                entry.dwSize = (uint)Marshal.SizeOf(typeof(PROCESSENTRY32));
                if (!Process32FirstW(snapshot, ref entry)) {
                    throw new Win32Exception(Marshal.GetLastWin32Error(),
                        "Process32FirstW failed.");
                }
                do {
                    if (entry.th32ProcessID <= Int32.MaxValue) {
                        int processId = (int)entry.th32ProcessID;
                        int snapshotParent = entry.th32ParentProcessID <= Int32.MaxValue ?
                            (int)entry.th32ParentProcessID : 0;
                        int ntParent = processId > 0 ?
                            TryQueryNtParentProcessId(processId) : 0;
                        int parent = snapshotParent;
                        if (ntParent > 0 && snapshotParent > 0 && ntParent != snapshotParent) {
                            // A disagreement is retained as an invalid parent
                            // field. The PowerShell raw validator will prevent
                            // this candidate from authorizing any operation.
                            parent = 0;
                        }
                        records.Add(new NativeProcessRecord {
                            ProcessId = processId,
                            ParentProcessId = parent,
                            Name = entry.szExeFile ?? string.Empty,
                            CommandLine = processId > 0 ? TryQueryCommandLine(processId) : null,
                            ExecutablePath = processId > 0 ? TryQueryImagePath(processId) : null
                        });
                    }
                    entry = new PROCESSENTRY32();
                    entry.dwSize = (uint)Marshal.SizeOf(typeof(PROCESSENTRY32));
                } while (Process32NextW(snapshot, ref entry));
                return records.ToArray();
            } finally {
                CloseHandle(snapshot);
            }
        }
    }
}
'@
    try {
        Add-Type -TypeDefinition $source -Language CSharp -ErrorAction Stop | Out-Null
    } catch {
        Throw-VerifierInfrastructure ('Could not load the native Windows process-inspection helper: ' +
            (Get-VerifierErrorMessage $_))
    }
    $loadedType = $typeName -as [type]
    if ($null -eq $loadedType) {
        Throw-VerifierInfrastructure 'The native Windows process-inspection helper did not load a usable type.'
    }
    return $loadedType
}

function Get-VerifierNativeProcessSnapshot() {
    $inspectionType = Initialize-VerifierNativeProcessInspection
    try {
        $rawRecords = @($inspectionType::GetSnapshot())
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Native Windows process snapshot failed: ' +
            (Get-VerifierErrorMessage $_))
    }
    if ($rawRecords.Count -eq 0) {
        Throw-VerifierInfrastructure 'Native Windows process snapshot returned no records.'
    }
    $result = New-Object Collections.ArrayList
    $seen = @{}
    foreach ($rawRecord in $rawRecords) {
        $record = [pscustomobject]@{
            ProcessId = $rawRecord.ProcessId
            ParentProcessId = $rawRecord.ParentProcessId
            CommandLine = $rawRecord.CommandLine
            Name = $rawRecord.Name
            ExecutablePath = $rawRecord.ExecutablePath
        }
        [void](Assert-VerifierRawProcessRecord $record 'native process snapshot record')
        $processId = [int]$record.ProcessId
        if ($seen.ContainsKey($processId)) {
            Throw-VerifierInfrastructure "Native Windows process snapshot contained duplicate PID $processId."
        }
        $seen[$processId] = $true
        [void]$result.Add($record)
    }
    return @($result)
}

function Get-VerifierProcessSnapshotWithFallback([string]$Purpose = 'process') {
    try {
        return @(Get-CimInstance Win32_Process -ErrorAction Stop)
    } catch {
        if (-not (Test-VerifierProcessInspectionFallbackError $_)) {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not query the $Purpose WMI process snapshot: " +
                (Get-VerifierErrorMessage $_))
        }
        return @(Get-VerifierNativeProcessSnapshot)
    }
}

function Get-VerifierProcessRecordsByIdWithFallback($ProcessId,
        [string]$Purpose = 'current process') {
    if (-not (Test-VerifierStrictIntegralValue $ProcessId 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'A process-ID query requires an exact positive PID.'
    }
    $ProcessId = [int]$ProcessId
    try {
        return @(Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction Stop)
    } catch {
        if (-not (Test-VerifierProcessInspectionFallbackError $_)) {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not query $Purpose PID ${ProcessId}: " +
                (Get-VerifierErrorMessage $_))
        }
        return @(Get-VerifierNativeProcessSnapshot | Where-Object {
            $property = $_.PSObject.Properties['ProcessId']
            $null -ne $property -and
                (Test-VerifierStrictIntegralValue $property.Value 0 ([int]::MaxValue)) -and
                [int]$property.Value -eq $ProcessId
        })
    }
}

function Get-VerifierProcessRecordsByParentWithFallback($ParentProcessId,
        [string]$Purpose = 'descendant') {
    if (-not (Test-VerifierStrictIntegralValue $ParentProcessId 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'A parent-process query requires an exact positive PID.'
    }
    $ParentProcessId = [int]$ParentProcessId
    try {
        return @(Get-CimInstance Win32_Process `
            -Filter "ParentProcessId = $ParentProcessId" -ErrorAction Stop)
    } catch {
        if (-not (Test-VerifierProcessInspectionFallbackError $_)) {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not query $Purpose children of PID " +
                [string]$ParentProcessId + ': ' + (Get-VerifierErrorMessage $_))
        }
        return @(Get-VerifierNativeProcessSnapshot | Where-Object {
            $property = $_.PSObject.Properties['ParentProcessId']
            $null -ne $property -and
                (Test-VerifierStrictIntegralValue $property.Value 0 ([int]::MaxValue)) -and
                [int]$property.Value -eq $ParentProcessId
        })
    }
}

function Get-VerifierBrowserProcessSnapshot([string]$BrowserPath = '', [string]$Profile = '',
        [string]$RunId = '', [string]$RepositoryIdentity = '', $Port = 0,
        [string]$Script = '', [string]$Nonce = '') {
    try {
        if (-not (Test-VerifierStrictIntegralValue $Port 0 65535)) {
            Throw-VerifierInfrastructure 'Relevant browser ownership query requires an exact integral port before OS inspection.'
        }
        $snapshot = @(Get-VerifierProcessSnapshotWithFallback 'relevant browser ownership')
        $relevant = @(Select-VerifierRelevantProcessRecords $snapshot $BrowserPath $Profile `
            $RunId $RepositoryIdentity $Port $Script $Nonce)
        Assert-VerifierProcessSnapshotComplete $relevant 'relevant browser ownership'
        return $relevant
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not query relevant browser process ownership: " +
            (Get-VerifierErrorMessage $_))
    }
}

function Get-VerifierBrowserOwnershipSnapshot([string]$BrowserPath = '', [string]$Profile = '',
        [string]$RunId = '', [string]$RepositoryIdentity = '', $Port = 0,
        [string]$Script = '', [string]$Nonce = '') {
    try {
        if (-not (Test-VerifierStrictIntegralValue $Port 0 65535)) {
            Throw-VerifierInfrastructure 'Complete browser ownership query requires an exact integral port before OS inspection.'
        }
        # The relevant snapshot remains the admission/identity check for
        # unrelated OS records. Once an owned root is being cleaned, however,
        # PPID traversal must use the complete WMI candidate set so a helper
        # with a different executable name cannot disappear from the graph.
        $snapshot = @(Get-VerifierProcessSnapshotWithFallback 'complete browser ownership')
        $relevant = @(Select-VerifierRelevantProcessRecords $snapshot $BrowserPath $Profile `
            $RunId $RepositoryIdentity $Port $Script $Nonce)
        Assert-VerifierProcessSnapshotComplete $relevant 'relevant browser ownership'
        return $snapshot
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not query complete browser ownership candidates: " +
            (Get-VerifierErrorMessage $_))
    }
}

function Get-VerifierProcessById($ProcessId) {
    if (-not (Test-VerifierStrictIntegralValue $ProcessId 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'Process lookup requires an exact positive PID.'
    }
    $processes = @(Get-Process -Id ([int]$ProcessId) -ErrorAction SilentlyContinue)
    if ($processes.Count -gt 1) {
        Throw-VerifierInfrastructure "Process lookup for PID $ProcessId was ambiguous."
    }
    if ($processes.Count -eq 0 -or $null -eq $processes[0]) { return $null }
    if ($processes[0].GetType() -ne [Diagnostics.Process]) {
        Throw-VerifierInfrastructure "Process lookup for PID $ProcessId returned a malformed process object."
    }
    return $processes[0]
}

function Get-VerifierCurrentProcessRecordById($ProcessId) {
    if (-not (Test-VerifierStrictIntegralValue $ProcessId 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'Current process inspection requires a positive PID.'
    }
    $ProcessId = [int]$ProcessId
    $processes = @()
    try {
        $processes = @(Get-Process -Id $ProcessId -ErrorAction Stop)
    } catch {
        $category = if ($_.PSObject.Properties['CategoryInfo'] -and
                $_.CategoryInfo.PSObject.Properties['Category']) {
            [string]$_.CategoryInfo.Category
        } else { '' }
        if ($category -ne 'ObjectNotFound') {
            Throw-VerifierInfrastructure "Could not query current process PID ${ProcessId}: $(Get-VerifierErrorMessage $_)"
        }
        # Do not accept a Get-Process miss by itself. Query Win32_Process too;
        # only two positive empty views prove absence, while an inconsistent
        # view is an infrastructure failure rather than a cleanup pass.
        try {
            $wmiAfterMiss = @(Get-VerifierProcessRecordsByIdWithFallback $ProcessId `
                'absent-process confirmation')
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure "Could not confirm absent Win32_Process PID ${ProcessId}: $(Get-VerifierErrorMessage $_)"
        }
        if (@($wmiAfterMiss).Count -eq 0) {
            return $null
        }
        Throw-VerifierInfrastructure "Get-Process reported PID ${ProcessId} absent but Win32_Process still reported an identity."
    }
    if (@($processes).Count -ne 1 -or $null -eq $processes[0]) {
        Throw-VerifierInfrastructure "Current process PID $ProcessId was missing or ambiguous."
    }
    $process = $processes[0]
    if ($process.GetType() -ne [Diagnostics.Process]) {
        Throw-VerifierInfrastructure "Current process PID $ProcessId returned a malformed process object."
    }
    try { $process.Refresh() } catch {
        Throw-VerifierInfrastructure "Could not refresh current process PID ${ProcessId}: $(Get-VerifierErrorMessage $_)"
    }
    if ([bool]$process.HasExited) {
        # Get-Process can briefly return an exited Process object after the
        # kernel has already removed the PID from Win32_Process. Treat that as
        # absence only after the independent WMI view also proves the PID is
        # gone; a still-present WMI record remains an infrastructure
        # inconsistency and is never accepted as cleanup.
        try {
            $wmiAfterExit = @(Get-VerifierProcessRecordsByIdWithFallback $ProcessId `
                'exited-process confirmation')
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure "Could not confirm exited process PID ${ProcessId} absence: $(Get-VerifierErrorMessage $_)"
        }
        if (@($wmiAfterExit).Count -eq 0) { return $null }
        Throw-VerifierInfrastructure "Process PID $ProcessId reported exited but remained present in Win32_Process."
    }
    $startTicks = Get-VerifierProcessStartTicks $process
    if ($startTicks -le 0) {
        Throw-VerifierInfrastructure "Current process PID $ProcessId has an unknown start identity."
    }
    $records = @()
    try {
        $records = @(Get-VerifierProcessRecordsByIdWithFallback $ProcessId `
            'current process identity')
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure "Could not query current Win32_Process identity for PID ${ProcessId}: $(Get-VerifierErrorMessage $_)"
    }
    if (@($records).Count -ne 1 -or $null -eq $records[0]) {
        Throw-VerifierInfrastructure "Current Win32_Process identity for PID $ProcessId was missing or ambiguous."
    }
    $currentWmiRecord = $records[0]
    foreach ($property in @('ProcessId', 'ParentProcessId', 'CommandLine')) {
        if (-not $currentWmiRecord.PSObject.Properties[$property]) {
            Throw-VerifierInfrastructure "Current process PID $ProcessId omitted $property."
        }
    }
    if (-not (Test-VerifierStrictIntegralValue $currentWmiRecord.ProcessId `
            1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $currentWmiRecord.ParentProcessId `
                1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictStringValue $currentWmiRecord.CommandLine) -or
            [String]::IsNullOrWhiteSpace($currentWmiRecord.CommandLine)) {
        Throw-VerifierInfrastructure "Current process PID $ProcessId had incomplete command, parent, or PID identity."
    }
    if ([int]$currentWmiRecord.ProcessId -ne $ProcessId) {
        Throw-VerifierInfrastructure "Current process PID $ProcessId had a mismatched WMI PID identity."
    }
    $parentId = [int]$currentWmiRecord.ParentProcessId
    foreach ($optionalProperty in @('Name', 'ExecutablePath')) {
        $optional = $currentWmiRecord.PSObject.Properties[$optionalProperty]
        if ($null -ne $optional -and $null -ne $optional.Value -and
                -not (Test-VerifierStrictStringValue $optional.Value)) {
            Throw-VerifierInfrastructure "Current process PID $ProcessId had a malformed $optionalProperty field."
        }
    }
    return [pscustomobject]@{
        Process = $process
        ProcessId = [int]$currentWmiRecord.ProcessId
        ParentProcessId = $parentId
        ProcessStartTicks = [long]$startTicks
        CommandLine = $currentWmiRecord.CommandLine
        Name = if ($currentWmiRecord.PSObject.Properties['Name'] -and
            $null -ne $currentWmiRecord.Name) { $currentWmiRecord.Name } else { '' }
        ExecutablePath = if ($currentWmiRecord.PSObject.Properties['ExecutablePath']) {
            if ($null -ne $currentWmiRecord.ExecutablePath) { $currentWmiRecord.ExecutablePath } else { '' }
        } else { '' }
    }
}

function Get-VerifierCurrentParentStartTicks($ParentProcessId) {
    if (-not (Test-VerifierStrictIntegralValue $ParentProcessId 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'Parent-process start proof requires a positive parent PID.'
    }
    $ParentProcessId = [int]$ParentProcessId
    $parentProcess = Get-VerifierProcessById $ParentProcessId
    if ($null -eq $parentProcess) {
        Throw-VerifierInfrastructure "Parent process PID $ParentProcessId disappeared during ownership proof."
    }
    try { $parentProcess.Refresh() } catch {
        Throw-VerifierInfrastructure "Could not refresh parent process PID ${ParentProcessId}: $(Get-VerifierErrorMessage $_)"
    }
    if ([bool]$parentProcess.HasExited) {
        Throw-VerifierInfrastructure "Parent process PID $ParentProcessId has exited during ownership proof."
    }
    $parentRecords = @()
    try {
        $parentRecords = @(Get-VerifierProcessRecordsByIdWithFallback $ParentProcessId `
            'parent process identity')
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure "Could not query parent process PID ${ParentProcessId}: $(Get-VerifierErrorMessage $_)"
    }
    if ($parentRecords.Count -ne 1 -or
            -not $parentRecords[0].PSObject.Properties['ProcessId'] -or
            -not (Test-VerifierStrictIntegralValue $parentRecords[0].ProcessId `
                1 ([int]::MaxValue)) -or
            [int]$parentRecords[0].ProcessId -ne $ParentProcessId) {
        Throw-VerifierInfrastructure "Parent process PID $ParentProcessId was missing or ambiguous during ownership proof."
    }
    $startTicks = Get-VerifierProcessStartTicks $parentProcess
    if ($startTicks -le 0) {
        Throw-VerifierInfrastructure "Parent process PID $ParentProcessId has an unknown start identity."
    }
    return [long]$startTicks
}

function Test-VerifierCurrentProcessCarriesOwnership($Current,
        [string]$ExpectedCommandLine = '', [string]$Script = '', $Port = 0,
        [string]$RunId = '', [string]$Nonce = '', [string]$Profile = '') {
    if (-not (Test-VerifierStrictIntegralValue $Port 0 65535)) {
        return $false
    }
    if ($null -eq $Current -or
            [String]::IsNullOrWhiteSpace([string]$Current.CommandLine)) {
        return $false
    }
    $commandLine = [string]$Current.CommandLine
    if (-not [String]::IsNullOrWhiteSpace($ExpectedCommandLine) -and
            (Test-VerifierCommandLineEquivalent $commandLine $ExpectedCommandLine)) {
        return $true
    }
    if (-not [String]::IsNullOrWhiteSpace($Script) -and $Port -gt 0 -and
            [String]::IsNullOrWhiteSpace($RunId) -and
            [String]::IsNullOrWhiteSpace($Nonce) -and
            [String]::IsNullOrWhiteSpace($Profile) -and
            (Test-VerifierCommandLinePath $commandLine $Script) -and
            ((Test-VerifierCommandLineSwitch $commandLine '-Port' ([string]$Port)) -or
             (Test-VerifierCommandLineSwitch $commandLine '--remote-debugging-port' ([string]$Port)))) {
        return $true
    }
    if (-not [String]::IsNullOrWhiteSpace($RunId) -and
            ((Test-VerifierCommandLineSwitch $commandLine '-VerifierRunId' $RunId) -or
             (Test-VerifierCommandLineSwitch $commandLine '--tsj-verifier-run' $RunId))) {
        return $true
    }
    if (-not [String]::IsNullOrWhiteSpace($Nonce) -and
            (Test-VerifierCommandLineSwitch $commandLine '-VerifierNonce' $Nonce)) {
        return $true
    }
    if (-not [String]::IsNullOrWhiteSpace($Profile) -and
            (Test-VerifierCommandLineSwitch $commandLine '--user-data-dir' $Profile)) {
        return $true
    }
    return $false
}

function Confirm-VerifierRecordedProcessAbsent($Recorded, [string]$Role = 'owned process',
        [string]$ExpectedCommandLine = '', $Port = 0, [string]$Script = '',
        [string]$RunId = '', [string]$Nonce = '', [string]$Profile = '') {
    # Null is the explicit no-record absence state.  Any non-null record must
    # carry exact raw PID and start-identity scalars before it can enter the
    # absence/process lookup path; PowerShell casts here would turn malformed
    # cleanup evidence into a forged zero/absence.
    if ($null -eq $Recorded) {
        return [pscustomobject]@{ QueryProven = $true; Absent = $true; Replaced = $false; Current = $null }
    }
    if (-not (Test-VerifierStrictIntegralValue $Port 0 65535)) {
        Throw-VerifierInfrastructure "$Role carried a malformed exact port before process absence inspection."
    }
    $processIdProperty = $Recorded.PSObject.Properties['ProcessId']
    $processStartProperty = $Recorded.PSObject.Properties['ProcessStartTicks']
    if ($null -eq $processIdProperty -or $null -eq $processStartProperty -or
            -not (Test-VerifierStrictIntegralValue $processIdProperty.Value 0 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $processStartProperty.Value 0 ([long]::MaxValue))) {
        Throw-VerifierInfrastructure "$Role omitted or malformed its exact raw PID/start identity."
    }
    $recordedProcessId = [long]$processIdProperty.Value
    $recordedProcessStartTicks = [long]$processStartProperty.Value
    foreach ($optionalProperty in @(
            [pscustomobject]@{ Name = 'ParentProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'ParentProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
        )) {
        $property = $Recorded.PSObject.Properties[$optionalProperty.Name]
        if ($null -ne $property -and
                -not (Test-VerifierStrictIntegralValue $property.Value `
                    $optionalProperty.Minimum $optionalProperty.Maximum)) {
            Throw-VerifierInfrastructure "$Role carried a malformed optional process identity '$($optionalProperty.Name)'."
        }
    }
    $commandLineProperty = $Recorded.PSObject.Properties['CommandLine']
    if ($null -ne $commandLineProperty -and
            -not (Test-VerifierStrictStringValue $commandLineProperty.Value)) {
        Throw-VerifierInfrastructure "$Role carried a malformed optional process command line."
    }
    if ($recordedProcessId -eq 0) {
        if ($recordedProcessStartTicks -ne 0 -or
                ($commandLineProperty -and
                 -not [String]::IsNullOrWhiteSpace([string]$commandLineProperty.Value)) -or
                ($Recorded.PSObject.Properties['ParentProcessId'] -and
                 [long]$Recorded.ParentProcessId -ne 0) -or
                ($Recorded.PSObject.Properties['ParentProcessStartTicks'] -and
                 [long]$Recorded.ParentProcessStartTicks -ne 0)) {
            Throw-VerifierInfrastructure "$Role recorded explicit absence with mismatched process identity fields."
        }
        return [pscustomobject]@{ QueryProven = $true; Absent = $true; Replaced = $false; Current = $null }
    }
    if ($recordedProcessStartTicks -le 0) {
        Throw-VerifierInfrastructure "$Role recorded a PID without a positive start identity."
    }
    $current = Get-VerifierCurrentProcessRecordById ([int]$recordedProcessId)
    if ($null -eq $current) {
        return [pscustomobject]@{ QueryProven = $true; Absent = $true; Replaced = $false; Current = $null }
    }
    if ($Recorded.PSObject.Properties['ParentProcessStartTicks'] -and
            [long]$Recorded.ParentProcessStartTicks -gt 0) {
        $current | Add-Member -NotePropertyName ParentProcessStartTicks `
            -NotePropertyValue (Get-VerifierCurrentParentStartTicks ([int]$current.ParentProcessId)) -Force
    }
    if ([long]$current.ProcessStartTicks -ne $recordedProcessStartTicks) {
        if (Test-VerifierCurrentProcessCarriesOwnership $current $ExpectedCommandLine $Script `
                $Port $RunId $Nonce $Profile) {
            Throw-VerifierInfrastructure "$Role PID $($Recorded.ProcessId) was reused by a process carrying the old ownership identity."
        }
        return [pscustomobject]@{ QueryProven = $true; Absent = $true; Replaced = $true; Current = $current }
    }
    if ($Recorded.PSObject.Properties['ParentProcessId'] -and
            [int]$Recorded.ParentProcessId -gt 0 -and
            [int]$current.ParentProcessId -ne [int]$Recorded.ParentProcessId) {
        Throw-VerifierInfrastructure "$Role PID $($Recorded.ProcessId) retained its start identity but changed parent identity."
    }
    if ($Recorded.PSObject.Properties['CommandLine'] -and
            -not [String]::IsNullOrWhiteSpace([string]$Recorded.CommandLine) -and
            -not (Test-VerifierCommandLineEquivalent ([string]$current.CommandLine) `
                ([string]$Recorded.CommandLine))) {
        Throw-VerifierInfrastructure "$Role PID $($Recorded.ProcessId) retained its start identity but changed command-line identity."
    }
    if (Test-VerifierCurrentProcessCarriesOwnership $current $ExpectedCommandLine $Script `
            $Port $RunId $Nonce $Profile) {
        return [pscustomobject]@{ QueryProven = $true; Absent = $false; Replaced = $false; Current = $current }
    }
    Throw-VerifierInfrastructure "$Role PID $($Recorded.ProcessId) was present but its ownership markers were incomplete."
}

function Confirm-VerifierReleasedListener($Port, $ExpectedProcessId = 0,
        $ExpectedStartTicks = 0, $ExpectedCommandLine = '',
        $ExpectedParentProcessId = 0, $Script = '', $RunId = '',
        $Nonce = '', $Profile = '', $ExpectedParentProcessStartTicks = 0) {
    if (-not (Test-VerifierStrictIntegralValue $Port 1 65535) -or
            -not (Test-VerifierStrictIntegralValue $ExpectedProcessId 0 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $ExpectedStartTicks 0) -or
            -not (Test-VerifierStrictIntegralValue $ExpectedParentProcessId 0 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $ExpectedParentProcessStartTicks 0) -or
            -not (Test-VerifierStrictStringValue $ExpectedCommandLine) -or
            -not (Test-VerifierStrictStringValue $Script) -or
            -not (Test-VerifierStrictStringValue $RunId) -or
            -not (Test-VerifierStrictStringValue $Nonce) -or
            -not (Test-VerifierStrictStringValue $Profile)) {
        Throw-VerifierInfrastructure 'Released listener proof requires a valid port.'
    }
    if (($ExpectedProcessId -gt 0 -and $ExpectedStartTicks -le 0) -or
            ($ExpectedProcessId -eq 0 -and $ExpectedStartTicks -ne 0)) {
        Throw-VerifierInfrastructure "Released listener proof for port $Port omitted the expected process start identity."
    }
    $inspection = Get-VerifierLoopbackListenerRecords $Port
    # Release proof is a strict consumer too. In particular, a remaining
    # kernel/PID4 record cannot be interpreted as a process or killed; without
    # the preview context it is simply an infrastructure failure.
    if (-not (Test-VerifierListenerInspectionSchema $inspection) -or
            -not $inspection.Success -or -not $inspection.Known) {
        Throw-VerifierInfrastructure "Loopback listener inspection for released port $Port was not positively proven."
    }
    $replacementObserved = $false
    foreach ($listener in @($inspection.Listeners)) {
        if ($null -eq $listener) {
            Throw-VerifierInfrastructure "Loopback listener inspection for port $Port returned a null listener record."
        }
        if (-not (Test-VerifierListenerRecordSchema $listener) -or
                $listener.ListenerOwnerKind -ne $script:VerifierUserProcessOwnerKind) {
            Throw-VerifierInfrastructure "Loopback listener inspection for port $Port returned a malformed or unauthorized listener owner record."
        }
        if ([long]$listener.Port -ne [long]$Port -or
                -not (Test-VerifierStrictIntegralValue $listener.ProcessId 1 ([int]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $listener.ProcessStartTicks 1)) {
            Throw-VerifierInfrastructure "Loopback listener inspection for port $Port returned an incomplete listener identity."
        }
        $current = Get-VerifierCurrentProcessRecordById ([int]$listener.ProcessId)
        if ($null -eq $current -or
                -not (Test-VerifierStrictIntegralValue $current.ProcessStartTicks 1) -or
                [long]$current.ProcessStartTicks -ne [long]$listener.ProcessStartTicks) {
            Throw-VerifierInfrastructure "Loopback listener process identity for port $Port changed during independent proof."
        }
        if ($ExpectedProcessId -le 0) {
            Throw-VerifierInfrastructure "Port $Port has a listener but the completed ledger recorded no bound owner identity."
        }
        if ([int]$current.ProcessId -eq [int]$ExpectedProcessId -and
                [long]$current.ProcessStartTicks -eq $ExpectedStartTicks) {
            if ($ExpectedParentProcessId -gt 0 -and
                    [int]$current.ParentProcessId -ne $ExpectedParentProcessId) {
                Throw-VerifierInfrastructure "The recorded owner for released port $Port retained its PID/start identity but changed parent identity."
            }
            if ($ExpectedParentProcessStartTicks -gt 0) {
                $currentParentStart = Get-VerifierCurrentParentStartTicks ([int]$current.ParentProcessId)
                if ([long]$currentParentStart -ne $ExpectedParentProcessStartTicks) {
                    Throw-VerifierInfrastructure "The recorded owner for released port $Port retained its PID/start identity but changed parent start identity."
                }
            }
            if (-not [String]::IsNullOrWhiteSpace($ExpectedCommandLine) -and
                    -not (Test-VerifierCommandLineEquivalent ([string]$current.CommandLine) $ExpectedCommandLine)) {
                Throw-VerifierInfrastructure "The recorded owner for released port $Port retained its PID/start identity but changed command-line identity."
            }
            Throw-VerifierInfrastructure "The recorded owner for released port $Port is still listening."
        }
        if ([int]$current.ProcessId -eq [int]$ExpectedProcessId) {
            $replacementObserved = $true
        }
        if (Test-VerifierCurrentProcessCarriesOwnership $current $ExpectedCommandLine $Script `
                $Port $RunId $Nonce $Profile) {
            Throw-VerifierInfrastructure "A current listener on port $Port still carries the released run's ownership identity."
        }
    }
    return [pscustomobject]@{
        QueryProven = $true
        OldOwnerAbsent = $true
        PortReused = $inspection.HasListeners
        PidReused = $replacementObserved
        Inspection = $inspection
    }
}

function Get-VerifierProfileReferenceRecords($Snapshot, [string]$Profile) {
    if ([String]::IsNullOrWhiteSpace($Profile)) {
        Throw-VerifierInfrastructure 'Cannot inspect browser profile references without a profile path.'
    }
    $references = New-Object Collections.ArrayList
    foreach ($profileProcessRecord in @($Snapshot)) {
        if ($null -eq $profileProcessRecord -or
                -not $profileProcessRecord.PSObject.Properties['CommandLine'] -or
                [String]::IsNullOrWhiteSpace([string]$profileProcessRecord.CommandLine)) {
            Throw-VerifierInfrastructure "A browser ownership process record had no readable command line while inspecting profile '$Profile'."
        }
        if (Test-VerifierCommandLineSwitch ([string]$profileProcessRecord.CommandLine) '--user-data-dir' $Profile) {
            [void]$references.Add($profileProcessRecord)
        }
    }
    return @($references)
}

function Assert-VerifierProfileSnapshotQuiescent($Snapshot, [string]$Profile) {
    $references = @(Get-VerifierProfileReferenceRecords $Snapshot $Profile)
    if ($references.Count -ne 0) {
        $pids = ($references | ForEach-Object { [string]$_.ProcessId }) -join ', '
        Throw-VerifierInfrastructure "A browser process still references profile '$Profile' (PID(s): $pids); deletion and lease release are refused."
    }
}

function Assert-VerifierBrowserProfileIsQuiescent([string]$Profile,
        [string]$BrowserPath = '', [string]$RunId = '',
        [string]$RepositoryIdentity = '', $Port = 0) {
    if ([String]::IsNullOrWhiteSpace($Profile)) {
        Throw-VerifierInfrastructure 'Cannot prove ownership of an empty browser profile path.'
    }
    if (-not (Test-VerifierStrictIntegralValue $Port 0 65535)) {
        Throw-VerifierInfrastructure 'Browser profile ownership query requires an exact integral port before OS inspection.'
    }
    $snapshot = @(Get-VerifierBrowserProcessSnapshot $BrowserPath $Profile $RunId `
        $RepositoryIdentity $Port)
    Assert-VerifierProfileSnapshotQuiescent $snapshot $Profile
}

function Test-VerifierPortInUse($Port) {
    if (-not (Test-VerifierStrictIntegralValue $Port 1 65535)) {
        Throw-VerifierInfrastructure 'Port-in-use inspection requires an exact integral port in the valid TCP range.'
    }
    $inspection = Get-VerifierLoopbackListenerRecords $Port
    if (-not $inspection.Success -or -not $inspection.Known) {
        Throw-VerifierInfrastructure "Could not positively determine whether loopback port $Port is in use."
    }
    return [bool]$inspection.HasListeners
}

function Test-VerifierConfiguredExecutableIdentity($ExpectedBrowserPath,
        $CurrentProcessRecord, [switch]$RequireExecutablePath) {
    if ($null -eq $CurrentProcessRecord -or
            [String]::IsNullOrWhiteSpace([string]$ExpectedBrowserPath)) {
        return $false
    }
    $expectedPath = Get-VerifierCanonicalWindowsPath ([string]$ExpectedBrowserPath)
    if ([String]::IsNullOrWhiteSpace($expectedPath)) { return $false }
    $expectedName = [IO.Path]::GetFileName($expectedPath)
    if ([String]::IsNullOrWhiteSpace($expectedName) -or
            -not $CurrentProcessRecord.PSObject.Properties['Name'] -or
            [String]::IsNullOrWhiteSpace([string]$CurrentProcessRecord.Name) -or
            -not ([string]$CurrentProcessRecord.Name).Equals($expectedName,
                [StringComparison]::OrdinalIgnoreCase)) {
        return $false
    }
    $hasExecutablePath = $CurrentProcessRecord.PSObject.Properties['ExecutablePath'] -and
        -not [String]::IsNullOrWhiteSpace([string]$CurrentProcessRecord.ExecutablePath)
    if ($RequireExecutablePath -and -not $hasExecutablePath) {
        return $false
    }
    if ($hasExecutablePath -and
            -not (Test-VerifierCanonicalWindowsPathValue `
                ([string]$CurrentProcessRecord.ExecutablePath) $expectedPath)) {
        return $false
    }
    return $true
}

function Test-VerifierSystemIdleProcessRecord($Candidate) {
    if ($null -eq $Candidate -or $Candidate -is [array]) { return $false }
    foreach ($requiredProperty in @('ProcessId', 'ParentProcessId', 'Name',
            'CommandLine', 'ExecutablePath')) {
        if (-not $Candidate.PSObject.Properties[$requiredProperty]) { return $false }
    }
    if (-not (Test-VerifierStrictIntegralValue $Candidate.ProcessId 0 0) -or
            -not (Test-VerifierStrictIntegralValue $Candidate.ParentProcessId 0 0) -or
            -not (Test-VerifierStrictStringValue $Candidate.Name) -or
            $Candidate.Name -cne 'System Idle Process') {
        return $false
    }
    $commandProperty = $Candidate.PSObject.Properties['CommandLine']
    if ($null -ne $commandProperty.Value -and
            (-not (Test-VerifierStrictStringValue $commandProperty.Value) -or
             $commandProperty.Value -cne '')) {
        return $false
    }
    $executableProperty = $Candidate.PSObject.Properties['ExecutablePath']
    if ($null -ne $executableProperty -and $null -ne $executableProperty.Value -and
            (-not (Test-VerifierStrictStringValue $executableProperty.Value) -or
             $executableProperty.Value -cne '')) {
        return $false
    }
    foreach ($optionalPropertyName in @('ProcessStartTicks', 'ParentProcessStartTicks')) {
        $optionalProperty = $Candidate.PSObject.Properties[$optionalPropertyName]
        if ($null -ne $optionalProperty -and $null -ne $optionalProperty.Value -and
                -not (Test-VerifierStrictIntegralValue $optionalProperty.Value 0 0)) {
            return $false
        }
    }
    return $true
}

function Get-VerifierSignedExecutableMetadata([string]$Path) {
    # File metadata and Authenticode are part of the descendant executable
    # capability proof.  An unavailable field, provider, or certificate is a
    # failed capability proof, never a reason to fall back to a name/PPID
    # decision.
    if (-not (Test-VerifierStrictStringValue $Path) -or
            [String]::IsNullOrWhiteSpace($Path)) {
        return $null
    }
    try {
        $versionInfo = [Diagnostics.FileVersionInfo]::GetVersionInfo($Path)
        if ($null -eq $versionInfo -or
                -not (Test-VerifierStrictStringValue $versionInfo.FileVersion) -or
                [String]::IsNullOrWhiteSpace($versionInfo.FileVersion) -or
                -not (Test-VerifierStrictStringValue $versionInfo.OriginalFilename) -or
                [String]::IsNullOrWhiteSpace($versionInfo.OriginalFilename)) {
            return $null
        }
        $signatureCommand = Get-Command Get-AuthenticodeSignature `
            -CommandType Cmdlet -ErrorAction Stop
        $signatures = @(& $signatureCommand -LiteralPath $Path `
            -ErrorAction Stop)
        if ($signatures.Count -ne 1) { return $null }
        $signature = $signatures[0]
        if ($null -eq $signature -or
                -not $signature.PSObject.Properties['Status'] -or
                [string]$signature.Status -cne 'Valid' -or
                -not $signature.PSObject.Properties['SignerCertificate'] -or
                $null -eq $signature.SignerCertificate -or
                $signature.SignerCertificate.GetType() -ne
                    [Security.Cryptography.X509Certificates.X509Certificate2]) {
            return $null
        }
        $certificate = $signature.SignerCertificate
        $thumbprint = [string]$certificate.Thumbprint
        $subject = [string]$certificate.Subject
        $simpleName = [string]$certificate.GetNameInfo(
            [Security.Cryptography.X509Certificates.X509NameType]::SimpleName,
            $false)
        if ([String]::IsNullOrWhiteSpace($thumbprint) -or
                [String]::IsNullOrWhiteSpace($subject) -or
                $simpleName -cne 'Microsoft Corporation' -or
                $subject -notmatch '(?i)(^|,\s*)CN=Microsoft Corporation(,|$)' -or
                $subject -notmatch '(?i)(^|,\s*)O=Microsoft Corporation(,|$)') {
            return $null
        }
        return [pscustomobject]@{
            FileVersion = [string]$versionInfo.FileVersion
            OriginalFilename = [string]$versionInfo.OriginalFilename
            SignerThumbprint = $thumbprint
        }
    } catch {
        return $null
    }
}

function Test-VerifierEdgeCompanionExecutableIdentity($RootOwnerRecord,
        $CandidateChildRecord, [string]$ExpectedBrowserPath) {
    if ($null -eq $RootOwnerRecord -or $null -eq $CandidateChildRecord -or
            [String]::IsNullOrWhiteSpace($ExpectedBrowserPath) -or
            -not $CandidateChildRecord.PSObject.Properties['Name'] -or
            -not $CandidateChildRecord.PSObject.Properties['ExecutablePath'] -or
            -not $CandidateChildRecord.PSObject.Properties['CommandLine']) {
        return $false
    }
    $candidateName = [string]$CandidateChildRecord.Name
    $candidatePath = [string]$CandidateChildRecord.ExecutablePath
    $candidateCommandLine = [string]$CandidateChildRecord.CommandLine
    if ([String]::IsNullOrWhiteSpace($candidateName) -or
            [String]::IsNullOrWhiteSpace($candidatePath) -or
            [String]::IsNullOrWhiteSpace($candidateCommandLine) -or
            -not $candidateName.Equals('identity_helper.exe',
                [StringComparison]::OrdinalIgnoreCase)) {
        return $false
    }
    $profileProperty = $RootOwnerRecord.PSObject.Properties['Profile']
    if ($null -eq $profileProperty -or
            -not (Test-VerifierStrictStringValue $profileProperty.Value) -or
            [String]::IsNullOrWhiteSpace([string]$profileProperty.Value)) {
        return $false
    }
    try {
        $rootPath = Get-VerifierCanonicalWindowsPath $ExpectedBrowserPath
        $candidateCanonicalPath = Get-VerifierCanonicalWindowsPath $candidatePath
        if ([String]::IsNullOrWhiteSpace($rootPath) -or
                [String]::IsNullOrWhiteSpace($candidateCanonicalPath) -or
                -not ([IO.Path]::GetFileName($rootPath)).Equals('msedge.exe',
                    [StringComparison]::OrdinalIgnoreCase)) {
            return $false
        }
        $rootPhysicalPath = Get-VerifierCanonicalPhysicalPath $rootPath
        $candidatePhysicalPath = Get-VerifierCanonicalPhysicalPath `
            $candidateCanonicalPath
        if ([String]::IsNullOrWhiteSpace($rootPhysicalPath) -or
                [String]::IsNullOrWhiteSpace($candidatePhysicalPath)) {
            return $false
        }
        $rootMetadata = Get-VerifierSignedExecutableMetadata $rootPhysicalPath
        if ($null -eq $rootMetadata -or
                $rootMetadata.OriginalFilename -cne 'msedge.exe' -or
                $rootMetadata.FileVersion -cnotmatch '^\d+\.\d+\.\d+\.\d+$') {
            return $false
        }
        $rootDirectory = Split-Path -Parent $rootPhysicalPath
        $expectedHelperPath = Get-VerifierCanonicalWindowsPath (Join-Path `
            (Join-Path $rootDirectory $rootMetadata.FileVersion) `
            'identity_helper.exe')
        if ([String]::IsNullOrWhiteSpace($expectedHelperPath)) { return $false }
        $expectedHelperPhysicalPath = Get-VerifierCanonicalPhysicalPath `
            $expectedHelperPath
        if (-not $candidateCanonicalPath.Equals($expectedHelperPath,
                [StringComparison]::OrdinalIgnoreCase) -or
                -not $candidatePhysicalPath.Equals($expectedHelperPhysicalPath,
                    [StringComparison]::OrdinalIgnoreCase)) {
            return $false
        }
        $helperMetadata = Get-VerifierSignedExecutableMetadata `
            $candidatePhysicalPath
        if ($null -eq $helperMetadata -or
                $helperMetadata.FileVersion -cne $rootMetadata.FileVersion -or
                $helperMetadata.OriginalFilename -cne 'identity_helper.exe' -or
                -not $helperMetadata.SignerThumbprint.Equals(
                    $rootMetadata.SignerThumbprint,
                    [StringComparison]::OrdinalIgnoreCase)) {
            return $false
        }
        $profilePresence = Get-VerifierCommandLineSwitchPresence `
            $candidateCommandLine '--user-data-dir' ([string]$profileProperty.Value)
        if (-not $profilePresence.Present -or -not $profilePresence.Matches) {
            return $false
        }
        foreach ($switch in @(
                [pscustomobject]@{ Name = '--type'; Value = 'utility' }
                [pscustomobject]@{ Name = '--utility-sub-type';
                    Value = 'winrt_app_id.mojom.WinrtAppIdService' }
                [pscustomobject]@{ Name = '--service-sandbox-type';
                    Value = 'windows_package_identity' }
            )) {
            $presence = Get-VerifierCommandLineSwitchPresence `
                $candidateCommandLine $switch.Name $switch.Value
            if (-not $presence.Present -or -not $presence.Matches) {
                return $false
            }
        }
        return $true
    } catch {
        return $false
    }
}

function Test-VerifierProcessIdentity($ProcessRecord, $Snapshot = $null,
        $ExpectedBrowserPath = '') {
    if ($null -eq $ProcessRecord -or
            -not (Test-VerifierStrictStringValue $ExpectedBrowserPath) -or
            -not $ProcessRecord.PSObject.Properties['ProcessId'] -or
            -not $ProcessRecord.PSObject.Properties['ProcessStartTicks'] -or
            -not (Test-VerifierStrictIntegralValue $ProcessRecord.ProcessId `
                1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $ProcessRecord.ProcessStartTicks 1)) {
        return $false
    }
    foreach ($identityProperty in @('Profile', 'RunId', 'RepositoryIdentity')) {
        if (-not $ProcessRecord.PSObject.Properties[$identityProperty] -or
                -not (Test-VerifierStrictStringValue $ProcessRecord.PSObject.Properties[$identityProperty].Value) -or
                [String]::IsNullOrWhiteSpace($ProcessRecord.PSObject.Properties[$identityProperty].Value)) {
            return $false
        }
    }
    if (-not $ProcessRecord.PSObject.Properties['CdpPort'] -or
            -not (Test-VerifierStrictIntegralValue $ProcessRecord.CdpPort 1 65535)) {
        return $false
    }
    foreach ($optionalProperty in @('ProcessParentProcessId',
            'ProcessParentProcessStartTicks')) {
        $property = $ProcessRecord.PSObject.Properties[$optionalProperty]
        if ($null -ne $property -and $null -ne $property.Value -and
                -not (Test-VerifierStrictIntegralValue $property.Value 0 ([long]::MaxValue))) {
            return $false
        }
    }
    $browserPathProperty = $ProcessRecord.PSObject.Properties['BrowserPath']
    if ($null -ne $browserPathProperty -and $null -ne $browserPathProperty.Value -and
            -not (Test-VerifierStrictStringValue $browserPathProperty.Value)) {
        return $false
    }
    $process = Get-VerifierProcessById ([int]$ProcessRecord.ProcessId)
    if ($null -eq $process) { return $false }
    try {
        if ((Get-VerifierProcessStartTicks $process) -ne [long]$ProcessRecord.ProcessStartTicks) {
            return $false
        }
    } catch { return $false }
    if ($null -eq $Snapshot) {
        try {
            $Snapshot = @(Get-VerifierProcessRecordsByIdWithFallback `
                $ProcessRecord.ProcessId 'process identity')
        } catch { return $false }
    }
    $commands = @()
    foreach ($candidate in @($Snapshot)) {
        if ($null -eq $candidate -or -not $candidate.PSObject.Properties['ProcessId']) {
            return $false
        }
        if (Test-VerifierStrictIntegralValue $candidate.ProcessId 0 0) {
            if (-not (Test-VerifierSystemIdleProcessRecord $candidate)) { return $false }
            continue
        }
        if (-not (Test-VerifierStrictIntegralValue $candidate.ProcessId `
                1 ([int]::MaxValue))) { return $false }
        if ([int]$candidate.ProcessId -eq [int]$ProcessRecord.ProcessId) {
            $commands += $candidate
        }
    }
    if ($commands.Count -ne 1) { return $false }
    $command = $commands[0]
    if ($null -eq $command -or
            -not $command.PSObject.Properties['CommandLine'] -or
            -not (Test-VerifierStrictStringValue $command.CommandLine) -or
            [String]::IsNullOrWhiteSpace($command.CommandLine)) { return $false }
    if (-not $command.PSObject.Properties['ParentProcessId'] -or
            -not (Test-VerifierStrictIntegralValue $command.ParentProcessId `
                1 ([int]::MaxValue))) { return $false }
    $line = $command.CommandLine
    if ($ProcessRecord.PSObject.Properties['ProcessParentProcessId'] -and
            [long]$ProcessRecord.ProcessParentProcessId -gt 0 -and
            (-not $command.PSObject.Properties['ParentProcessId'] -or
             [int]$command.ParentProcessId -ne [int]$ProcessRecord.ProcessParentProcessId)) {
        return $false
    }
    if ($ProcessRecord.PSObject.Properties['ProcessParentProcessStartTicks'] -and
            $null -ne $ProcessRecord.ProcessParentProcessStartTicks -and
            [long]$ProcessRecord.ProcessParentProcessStartTicks -gt 0) {
        if (-not $command.PSObject.Properties['ParentProcessId'] -or
                [int]$command.ParentProcessId -le 0) {
            return $false
        }
        try {
            $currentParentStart = Get-VerifierCurrentParentStartTicks `
                ([int]$command.ParentProcessId)
        } catch {
            return $false
        }
        if ([long]$currentParentStart -ne
                [long]$ProcessRecord.ProcessParentProcessStartTicks) {
            return $false
        }
    }
    $configuredBrowserPath = if (-not [String]::IsNullOrWhiteSpace($ExpectedBrowserPath)) {
        $ExpectedBrowserPath
    } elseif ($null -ne $browserPathProperty) {
        $browserPathProperty.Value
    } else { '' }
    # A browser root is never identifiable from run/profile/port markers alone.
    # An absent configured executable path is an unproven ownership state and
    # must fail closed before any root or descendant can be stopped.
    if ([String]::IsNullOrWhiteSpace($configuredBrowserPath) -or
            -not (Test-VerifierConfiguredExecutableIdentity $configuredBrowserPath $command `
                -RequireExecutablePath)) {
        return $false
    }
    return (Test-VerifierCommandLineSwitch $line '--user-data-dir' $ProcessRecord.Profile) -and
        (Test-VerifierCommandLineSwitch $line '--tsj-verifier-run' $ProcessRecord.RunId) -and
        (Test-VerifierCommandLineSwitch $line '--tsj-verifier-worktree' $ProcessRecord.RepositoryIdentity) -and
        (Test-VerifierCommandLineSwitch $line '--remote-debugging-port' ([string]$ProcessRecord.CdpPort))
}

function Test-VerifierCurrentProcessRecordMatches($Recorded, $Current) {
    if ($null -eq $Recorded -or $null -eq $Current) { return $false }
    foreach ($property in @('ProcessId', 'ParentProcessId', 'ProcessStartTicks', 'CommandLine')) {
        if (-not $Recorded.PSObject.Properties[$property] -or
                -not $Current.PSObject.Properties[$property]) { return $false }
    }
    foreach ($record in @($Recorded, $Current)) {
        if (-not (Test-VerifierStrictIntegralValue $record.ProcessId `
                1 ([int]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $record.ParentProcessId `
                    1 ([int]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $record.ProcessStartTicks 1) -or
                -not (Test-VerifierStrictStringValue $record.CommandLine) -or
                [String]::IsNullOrWhiteSpace($record.CommandLine)) {
            return $false
        }
        foreach ($optionalProperty in @('ParentProcessStartTicks')) {
            $property = $record.PSObject.Properties[$optionalProperty]
            if ($null -ne $property -and $null -ne $property.Value -and
                    -not (Test-VerifierStrictIntegralValue $property.Value 0)) {
                return $false
            }
        }
        foreach ($optionalProperty in @('Name', 'ExecutablePath')) {
            $property = $record.PSObject.Properties[$optionalProperty]
            if ($null -ne $property -and $null -ne $property.Value -and
                    -not (Test-VerifierStrictStringValue $property.Value)) {
                return $false
            }
        }
    }
    if ($Recorded.PSObject.Properties['Name'] -and
            -not [String]::IsNullOrWhiteSpace([string]$Recorded.Name)) {
        if (-not $Current.PSObject.Properties['Name'] -or
                [String]::IsNullOrWhiteSpace([string]$Current.Name) -or
                -not ([string]$Recorded.Name).Equals([string]$Current.Name,
                    [StringComparison]::OrdinalIgnoreCase)) {
            return $false
        }
    }
    if ($Recorded.PSObject.Properties['ExecutablePath'] -and
            -not [String]::IsNullOrWhiteSpace([string]$Recorded.ExecutablePath)) {
        if (-not $Current.PSObject.Properties['ExecutablePath'] -or
                [String]::IsNullOrWhiteSpace([string]$Current.ExecutablePath) -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string]$Current.ExecutablePath) `
                    ([string]$Recorded.ExecutablePath))) {
            return $false
        }
    }
    if ([int]$Current.ProcessId -ne [int]$Recorded.ProcessId -or
            [int]$Current.ParentProcessId -ne [int]$Recorded.ParentProcessId -or
            [long]$Current.ProcessStartTicks -ne [long]$Recorded.ProcessStartTicks -or
            [String]::IsNullOrWhiteSpace($Recorded.CommandLine) -or
            [String]::IsNullOrWhiteSpace($Current.CommandLine)) {
        return $false
    }
    if ($Recorded.PSObject.Properties['ParentProcessStartTicks'] -and
            [long]$Recorded.ParentProcessStartTicks -gt 0) {
        if (-not $Current.PSObject.Properties['ParentProcessStartTicks'] -or
                [long]$Current.ParentProcessStartTicks -le 0 -or
                [long]$Current.ParentProcessStartTicks -ne
                    [long]$Recorded.ParentProcessStartTicks) {
            return $false
        }
    }
    return (Test-VerifierCommandLineEquivalent ([string]$Current.CommandLine) ([string]$Recorded.CommandLine))
}

function Get-VerifierCurrentOwnedProcessOnce($OwnerRecord, $Recorded, [switch]$Root) {
    [void](Assert-VerifierRawProcessId $OwnerRecord 'owned process owner')
    if ($Root) {
        [void](Assert-VerifierRawProcessIdentityRecord $OwnerRecord `
            'owned process root owner' `
            -ParentPropertyName 'ProcessParentProcessId' `
            -ParentStartPropertyName 'ProcessParentProcessStartTicks' `
            -CommandPropertyName 'ProcessCommandLine' -RequireParentStart)
    }
    [void](Assert-VerifierRawProcessIdentityRecord $Recorded `
        'owned process record' -RequireParentStart
    )
    $pidValue = [int]$Recorded.ProcessId
    $process = Get-VerifierProcessById $pidValue
    if ($null -eq $process) {
        Throw-VerifierInfrastructureMissingProcess `
            "Owned process PID $pidValue disappeared before termination proof." $pidValue
    }
    $currentStart = Get-VerifierProcessStartTicks $process
    $records = @()
    try {
        $records = @(Get-VerifierProcessRecordsByIdWithFallback $pidValue `
            'owned process identity')
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure "Could not re-query current process identity for PID ${pidValue}: $(Get-VerifierErrorMessage $_)"
    }
    if ($records.Count -ne 1) {
        Throw-VerifierInfrastructure "Current process identity for PID $pidValue was missing or ambiguous before termination."
    }
        $currentWmi = $records[0]
    [void](Assert-VerifierRawProcessRecord $currentWmi `
        "current process record for owned PID $pidValue" `
        -RequirePositiveIdentity -RequireCommandLine)
    $currentParentStartTicks = 0L
    if ($Recorded.PSObject.Properties['ParentProcessStartTicks'] -and
            [long]$Recorded.ParentProcessStartTicks -gt 0) {
        $currentParentStartTicks = Get-VerifierCurrentParentStartTicks `
            ([int]$currentWmi.ParentProcessId)
    }
    $current = [pscustomobject]@{
        ProcessId = [int]$currentWmi.ProcessId
        ParentProcessId = [int]$currentWmi.ParentProcessId
        ProcessStartTicks = [long]$currentStart
        CommandLine = [string]$currentWmi.CommandLine
        ParentProcessStartTicks = $currentParentStartTicks
        Name = if ($currentWmi.PSObject.Properties['Name']) { [string]$currentWmi.Name } else { '' }
        ExecutablePath = if ($currentWmi.PSObject.Properties['ExecutablePath']) {
            [string]$currentWmi.ExecutablePath
        } else { '' }
    }
    if (-not (Test-VerifierCurrentProcessRecordMatches $Recorded $current)) {
        Throw-VerifierInfrastructure "PID $pidValue changed command-line, parent, or start identity before termination."
    }
    if ($Root) {
        $expectedBrowserPath = if ($OwnerRecord.PSObject.Properties['BrowserPath']) {
            [string]$OwnerRecord.BrowserPath
        } else { '' }
        if ([String]::IsNullOrWhiteSpace($expectedBrowserPath)) {
            Throw-VerifierInfrastructure "Root browser PID $pidValue omitted the resolved BrowserPath executable identity."
        }
        if (-not (Test-VerifierProcessIdentity $OwnerRecord @($current) $expectedBrowserPath)) {
            Throw-VerifierInfrastructure "Root browser PID $pidValue no longer carries this run's exact ownership identity."
        }
        if (-not (Test-VerifierConfiguredExecutableIdentity $expectedBrowserPath $current `
                -RequireExecutablePath)) {
            Throw-VerifierInfrastructure "Root browser PID $pidValue no longer matches the configured browser executable identity."
        }
        if (-not $OwnerRecord.PSObject.Properties['ProcessParentProcessId'] -or
                [int]$OwnerRecord.ProcessParentProcessId -le 0 -or
                [int]$current.ParentProcessId -ne [int]$OwnerRecord.ProcessParentProcessId) {
            Throw-VerifierInfrastructure "Root browser PID $pidValue changed parent identity before termination."
        }
        if ($OwnerRecord.PSObject.Properties['ProcessParentProcessStartTicks'] -and
                [long]$OwnerRecord.ProcessParentProcessStartTicks -gt 0 -and
                [long]$current.ParentProcessStartTicks -ne
                    [long]$OwnerRecord.ProcessParentProcessStartTicks) {
            Throw-VerifierInfrastructure "Root browser PID $pidValue changed parent start identity before termination."
        }
    } elseif (-not (Test-VerifierDescendantOwnership $OwnerRecord $current `
            ([int]$Recorded.ParentProcessId))) {
        Throw-VerifierInfrastructure "Descendant PID $pidValue no longer carries this run's exact ownership identity."
    }
    # Re-query the Process object after the WMI comparison. The discovery
    # object is diagnostic only; termination receives this current instance.
    $verifiedProcess = Get-VerifierProcessById $pidValue
    if ($null -eq $verifiedProcess) {
        Throw-VerifierInfrastructureMissingProcess `
            "Owned process PID $pidValue disappeared after current identity validation." $pidValue
    }
    try { $verifiedProcess.Refresh() } catch {
        Throw-VerifierInfrastructure "Could not refresh owned process PID $pidValue immediately before termination: $(Get-VerifierErrorMessage $_)"
    }
    if ([bool]$verifiedProcess.HasExited) {
        Throw-VerifierInfrastructureMissingProcess `
            "Owned process PID $pidValue exited after current identity validation." $pidValue
    }
    if ((Get-VerifierProcessStartTicks $verifiedProcess) -ne [long]$current.ProcessStartTicks) {
        Throw-VerifierInfrastructure "Owned process PID $pidValue changed start identity after current identity validation."
    }
    return [pscustomobject]@{ Process = $verifiedProcess; Record = $current }
}

function Get-VerifierCurrentOwnedDescendantWithRetry($OwnerRecord, $Recorded) {
    # A Chromium child can briefly expose a complete PID/parent/command record
    # while its executable path is still unavailable. Retry only that narrow
    # observation against the same retained PID/start/parent identity. The
    # configured path, process name, command markers, and final current
    # Process object remain mandatory; no PID-only or PPID-only result can
    # leave this boundary.
    [void](Assert-VerifierRawProcessId $OwnerRecord 'owned process owner')
    [void](Assert-VerifierRawProcessIdentityRecord $Recorded `
        'owned browser descendant record' -RequireParentStart)
    if (-not $OwnerRecord.PSObject.Properties['Profile'] -or
            [String]::IsNullOrWhiteSpace([string]$OwnerRecord.Profile) -or
            -not $OwnerRecord.PSObject.Properties['BrowserPath'] -or
            [String]::IsNullOrWhiteSpace([string]$OwnerRecord.BrowserPath)) {
        Throw-VerifierInfrastructure 'Owned browser descendant retry omitted its exact profile or executable path.'
    }
    $expectedBrowserPath = [string]$OwnerRecord.BrowserPath
    $recordNameProperty = $Recorded.PSObject.Properties['Name']
    if ($null -eq $recordNameProperty -or
            [String]::IsNullOrWhiteSpace([string]$recordNameProperty.Value)) {
        Throw-VerifierInfrastructure "Browser descendant PID $($Recorded.ProcessId) omitted its process name."
    }
    $recordPathProperty = $Recorded.PSObject.Properties['ExecutablePath']
    if ($null -ne $recordPathProperty -and
            -not [String]::IsNullOrWhiteSpace([string]$recordPathProperty.Value) -and
            -not (Test-VerifierDescendantExecutableIdentity $OwnerRecord $Recorded)) {
        Throw-VerifierInfrastructure "Browser descendant PID $($Recorded.ProcessId) carried a mismatched configured executable identity."
    }
    $retryBudgetMilliseconds = 500
    $retryBudget = [Diagnostics.Stopwatch]::StartNew()
    $retryBudgetTicks = [long][Math]::Ceiling(
        ([double][Diagnostics.Stopwatch]::Frequency * $retryBudgetMilliseconds) /
        1000.0)
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            break
        }
        $current = $null
        try {
            $current = Get-VerifierCurrentProcessRecordById ([int]$Recorded.ProcessId)
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not re-query browser descendant PID " +
                [string]$Recorded.ProcessId + ': ' + (Get-VerifierErrorMessage $_))
        }
        if ($null -eq $current) {
            Throw-VerifierInfrastructureMissingProcess `
                "Browser descendant PID $($Recorded.ProcessId) disappeared during identity retry." `
                ([int]$Recorded.ProcessId)
        }
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            Throw-VerifierInfrastructure 'Browser descendant identity retry exceeded its monotonic startup budget.'
        }
        [void](Assert-VerifierRawProcessRecord $current `
            "current browser descendant PID $($Recorded.ProcessId)" `
            -RequirePositiveIdentity -RequireCommandLine)
        if ($Recorded.PSObject.Properties['ParentProcessStartTicks'] -and
                [long]$Recorded.ParentProcessStartTicks -gt 0) {
            $current | Add-Member -NotePropertyName ParentProcessStartTicks `
                -NotePropertyValue (Get-VerifierCurrentParentStartTicks `
                    ([int]$current.ParentProcessId)) -Force
        }
        if (-not (Test-VerifierCurrentProcessRecordMatches $Recorded $current)) {
            Throw-VerifierInfrastructure "Browser descendant PID $($Recorded.ProcessId) changed its retained PID/start/parent/command identity."
        }
        $currentNameProperty = $current.PSObject.Properties['Name']
        if ($null -eq $currentNameProperty -or
                [String]::IsNullOrWhiteSpace([string]$currentNameProperty.Value)) {
            Throw-VerifierInfrastructure "Browser descendant PID $($Recorded.ProcessId) omitted its current process name."
        }
        $currentPathProperty = $current.PSObject.Properties['ExecutablePath']
        $currentPath = if ($null -eq $currentPathProperty) { '' } else {
            [string]$currentPathProperty.Value
        }
        if ([String]::IsNullOrWhiteSpace($currentPath)) {
            # This is the only observation eligible for a bounded retry. A
            # complete process identity can precede the native image-path
            # publication for a short interval, but no other failure may be
            # converted into another attempt.
        } elseif (-not (Test-VerifierDescendantExecutableIdentity $OwnerRecord $current)) {
            Throw-VerifierInfrastructure "Browser descendant PID $($Recorded.ProcessId) omitted or mismatched the configured executable identity."
        } else {
            try {
                $verified = Get-VerifierCurrentOwnedProcessOnce $OwnerRecord $Recorded
            } catch {
                if (Test-VerifierInfrastructureError $_) {
                    # Get-VerifierCurrentOwnedProcessOnce has already passed
                    # the explicit executable-path observation above. Its
                    # infrastructure failures include identity changes,
                    # disappearance, and termination-boundary failures;
                    # none is a retryable empty-path observation.
                    throw
                } else {
                    Throw-VerifierInfrastructure ("Could not prove browser descendant PID " +
                        [string]$Recorded.ProcessId + ': ' + (Get-VerifierErrorMessage $_))
                }
            }
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                Throw-VerifierInfrastructure 'Browser descendant identity retry exceeded its monotonic startup budget.'
            }
            if ($null -eq $verified -or
                    $null -eq $verified.PSObject.Properties['Record'] -or
                    -not (Test-VerifierDescendantExecutableIdentity $OwnerRecord $verified.Record)) {
                Throw-VerifierInfrastructure "Browser descendant PID $($Recorded.ProcessId) did not retain a complete executable identity after retry."
            }
            [void](Assert-VerifierRawProcessRecord $verified.Record `
                "verified browser descendant PID $($Recorded.ProcessId)" `
                -RequirePositiveIdentity -RequireCommandLine)
            if (-not (Test-VerifierCurrentProcessRecordMatches $Recorded $verified.Record)) {
                Throw-VerifierInfrastructure "Browser descendant PID $($Recorded.ProcessId) did not retain its recorded identity after retry."
            }
            $verifiedProcessProperty = $verified.PSObject.Properties['Process']
            if ($null -eq $verifiedProcessProperty -or
                    $null -eq $verifiedProcessProperty.Value -or
                    $verifiedProcessProperty.Value.GetType() -ne [Diagnostics.Process]) {
                Throw-VerifierInfrastructure "Browser descendant PID $($Recorded.ProcessId) did not return a live System.Diagnostics.Process proof after retry."
            }
            $verifiedProcess = $verifiedProcessProperty.Value
            $verifiedProcessId = 0
            $verifiedProcessStartTicks = 0L
            $verifiedProcessLive = $false
            try {
                $verifiedProcess.Refresh()
                $verifiedProcessId = [int]$verifiedProcess.Id
                $verifiedProcessLive = -not [bool]$verifiedProcess.HasExited
                $verifiedProcessStartTicks = Get-VerifierProcessStartTicks $verifiedProcess
            } catch {
                Throw-VerifierInfrastructure ("Could not refresh verified browser descendant PID " +
                    [string]$Recorded.ProcessId + ': ' + (Get-VerifierErrorMessage $_))
            }
            if (-not $verifiedProcessLive -or
                    $verifiedProcessId -ne [int]$Recorded.ProcessId -or
                    -not (Test-VerifierStrictIntegralValue $verifiedProcessStartTicks 1 ([long]::MaxValue)) -or
                    [long]$verifiedProcessStartTicks -ne [long]$Recorded.ProcessStartTicks) {
                Throw-VerifierInfrastructure "Browser descendant PID $($Recorded.ProcessId) did not retain a live Process object with matching start identity after retry."
            }
            return $verified
        }
        $remainingTicks = $retryBudgetTicks - $retryBudget.ElapsedTicks
        if ($attempt -ge 3 -or $remainingTicks -le 0) {
            break
        }
        $remainingMilliseconds = ([double]$remainingTicks * 1000.0) /
            [double][Diagnostics.Stopwatch]::Frequency
        $sleepMilliseconds = [int][Math]::Floor(
            [Math]::Min(50.0, $remainingMilliseconds - 1.0))
        if ($sleepMilliseconds -lt 1) { break }
        Start-Sleep -Milliseconds $sleepMilliseconds
    }
    Throw-VerifierInfrastructure "Browser descendant PID $($Recorded.ProcessId) never exposed a complete configured executable identity within the bounded retry budget."
}

function Get-VerifierCurrentOwnedProcess($OwnerRecord, $Recorded, [switch]$Root) {
    if (-not $Root -and $null -ne $OwnerRecord -and
            $OwnerRecord.PSObject.Properties['Profile']) {
        return Get-VerifierCurrentOwnedDescendantWithRetry $OwnerRecord $Recorded
    }
    return Get-VerifierCurrentOwnedProcessOnce $OwnerRecord $Recorded -Root:$Root
}

function Stop-VerifierVerifiedProcessExactly($Process, $ExpectedStartTicks,
        $WaitMilliseconds = 5000, $ExpectedRecord = $null) {
    if (-not (Test-VerifierStrictIntegralValue $ExpectedStartTicks 1 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $WaitMilliseconds 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'Exact process termination requires exact positive start identity and wait-bound scalars.'
    }
    if ($null -eq $Process -or
            $Process.GetType() -ne [Diagnostics.Process] -or
            -not (Test-VerifierStrictIntegralValue $Process.Id 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'Exact process termination requires a valid retained process identity.'
    }
    if ($null -ne $ExpectedRecord) {
        [void](Assert-VerifierRawProcessIdentityRecord $ExpectedRecord `
            'exact process termination record')
    }
    $pidValue = [int]$Process.Id
    if ($pidValue -eq 4) {
        Throw-VerifierInfrastructure 'Refusing to terminate PID 4, which is reserved for the System/HTTP.sys kernel transport owner.'
    }
    try {
        $Process.Refresh()
        if ([bool]$Process.HasExited) {
            $absentAfterNaturalExit = Get-VerifierCurrentProcessRecordById $pidValue
            if ($null -ne $absentAfterNaturalExit) {
                Throw-VerifierInfrastructure "Verified process PID $pidValue reported exited but remained present."
            }
            return [pscustomobject]@{
                ProcessId = $pidValue
                ProcessStartTicks = $ExpectedStartTicks
                TerminationProven = $true
                NaturalExit = $true
            }
        }

        # Get the current Process object again immediately before Stop-Process.
        # The caller has already performed the exact Win32_Process command,
        # parent, marker, and start-identity comparison; this last refresh
        # prevents a stale discovery object from becoming the termination
        # target after a PID replacement.
        $currentProcess = Get-VerifierProcessById $pidValue
        if ($null -eq $currentProcess) {
            $absentAfterLookup = Get-VerifierCurrentProcessRecordById $pidValue
            if ($null -ne $absentAfterLookup) {
                Throw-VerifierInfrastructure "Process PID $pidValue disappeared from Get-Process but still has a current identity."
            }
            return [pscustomobject]@{
                ProcessId = $pidValue
                ProcessStartTicks = $ExpectedStartTicks
                TerminationProven = $true
                NaturalExit = $true
            }
        }
        $currentProcess.Refresh()
        if ([bool]$currentProcess.HasExited -or
                (Get-VerifierProcessStartTicks $currentProcess) -ne $ExpectedStartTicks) {
            Throw-VerifierInfrastructure "Process PID $pidValue changed or exited before exact termination."
        }
        if ($null -ne $ExpectedRecord) {
            $currentWmiRecords = @(Get-VerifierProcessRecordsByIdWithFallback $pidValue `
                'termination-boundary process identity')
            if ($currentWmiRecords.Count -ne 1) {
                Throw-VerifierInfrastructure "Process PID $pidValue current identity was incomplete at the termination boundary."
            }
            [void](Assert-VerifierRawProcessRecord $currentWmiRecords[0] `
                "current termination record for PID $pidValue" `
                -RequirePositiveIdentity -RequireCommandLine)
            $currentWmiRecord = [pscustomobject]@{
                ProcessId = [int]$currentWmiRecords[0].ProcessId
                ParentProcessId = [int]$currentWmiRecords[0].ParentProcessId
                ProcessStartTicks = [long](Get-VerifierProcessStartTicks $currentProcess)
                CommandLine = [string]$currentWmiRecords[0].CommandLine
                Name = if ($currentWmiRecords[0].PSObject.Properties['Name']) {
                    [string]$currentWmiRecords[0].Name
                } else { '' }
                ExecutablePath = if ($currentWmiRecords[0].PSObject.Properties['ExecutablePath']) {
                    [string]$currentWmiRecords[0].ExecutablePath
                } else { '' }
            }
            if ($ExpectedRecord.PSObject.Properties['ParentProcessStartTicks'] -and
                    [long]$ExpectedRecord.ParentProcessStartTicks -gt 0) {
                $currentWmiRecord | Add-Member -NotePropertyName ParentProcessStartTicks `
                    -NotePropertyValue (Get-VerifierCurrentParentStartTicks `
                        ([int]$currentWmiRecords[0].ParentProcessId))
            }
            if (-not (Test-VerifierCurrentProcessRecordMatches $ExpectedRecord $currentWmiRecord)) {
                Throw-VerifierInfrastructure "Process PID $pidValue current WMI identity changed at the termination boundary."
            }
        }
        Stop-Process -InputObject $currentProcess -Force -ErrorAction Stop
        if (-not $currentProcess.WaitForExit($WaitMilliseconds)) {
            Throw-VerifierInfrastructure "Process PID $pidValue did not terminate within the exact cleanup bound."
        }
        $currentProcess.Refresh()
        if (-not [bool]$currentProcess.HasExited) {
            Throw-VerifierInfrastructure "Process PID $pidValue remained alive after exact termination."
        }
        $absent = Get-VerifierCurrentProcessRecordById $pidValue
        if ($null -ne $absent) {
            Throw-VerifierInfrastructure "Process PID $pidValue remained present after exact termination."
        }
        return [pscustomobject]@{
            ProcessId = $pidValue
            ProcessStartTicks = $ExpectedStartTicks
            TerminationProven = $true
            NaturalExit = $false
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not prove exact termination of process PID " +
            [string]$pidValue + ': ' + (Get-VerifierErrorMessage $_))
    }
}

function Get-VerifierCurrentProcessIdentity($ProcessId,
        $ExpectedStartTicks = 0, $ExpectedParentProcessId = 0,
        $ExpectedCommandLine = '', $Script = '', $Port = 0,
        $RunId = '', $Nonce = '', $ExpectedParentProcessStartTicks = 0,
        $ExpectedBrowserPath = '') {
    if (-not (Test-VerifierStrictIntegralValue $ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $ExpectedStartTicks 0 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $ExpectedParentProcessId 0 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $Port 0 65535) -or
            -not (Test-VerifierStrictIntegralValue $ExpectedParentProcessStartTicks 0 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictStringValue $ExpectedCommandLine) -or
            -not (Test-VerifierStrictStringValue $Script) -or
            -not (Test-VerifierStrictStringValue $RunId) -or
            -not (Test-VerifierStrictStringValue $Nonce) -or
            -not (Test-VerifierStrictStringValue $ExpectedBrowserPath)) {
        Throw-VerifierInfrastructure 'Current process identity carried malformed raw PID, parent, port, or identity fields.'
    }
    $ProcessId = [int]$ProcessId
    $ExpectedStartTicks = [long]$ExpectedStartTicks
    $ExpectedParentProcessId = [int]$ExpectedParentProcessId
    $Port = [int]$Port
    $ExpectedParentProcessStartTicks = [long]$ExpectedParentProcessStartTicks
    if ($ProcessId -le 0) {
        Throw-VerifierInfrastructure 'A current process identity requires a positive PID.'
    }
    $process = Get-VerifierProcessById $ProcessId
    if ($null -eq $process) {
        Throw-VerifierInfrastructure "Process PID $ProcessId disappeared before current-identity proof."
    }
    try { $process.Refresh() } catch {
        Throw-VerifierInfrastructure "Could not refresh process PID $ProcessId before current-identity proof: $(Get-VerifierErrorMessage $_)"
    }
    if ([bool]$process.HasExited) {
        Throw-VerifierInfrastructure "Process PID $ProcessId has already exited before current-identity proof."
    }
    $currentStartTicks = Get-VerifierProcessStartTicks $process
    if ($ExpectedStartTicks -gt 0 -and $currentStartTicks -ne $ExpectedStartTicks) {
        Throw-VerifierInfrastructure "Process PID $ProcessId start identity changed before current-identity proof."
    }
    $records = @()
    try {
        $records = @(Get-VerifierProcessRecordsByIdWithFallback $ProcessId `
            'current process identity')
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure "Could not re-query current process PID ${ProcessId}: $(Get-VerifierErrorMessage $_)"
    }
    if ($records.Count -ne 1) {
        Throw-VerifierInfrastructure "Current process PID $ProcessId was missing or ambiguous during identity proof."
    }
    $wmi = $records[0]
    foreach ($property in @('ProcessId', 'ParentProcessId', 'CommandLine')) {
        if (-not $wmi.PSObject.Properties[$property]) {
            Throw-VerifierInfrastructure "Current process PID $ProcessId identity omitted $property."
        }
    }
    if (-not (Test-VerifierStrictIntegralValue $wmi.ProcessId `
            1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $wmi.ParentProcessId `
                1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictStringValue $wmi.CommandLine) -or
            [String]::IsNullOrWhiteSpace($wmi.CommandLine) -or
            [int]$wmi.ProcessId -ne $ProcessId) {
        Throw-VerifierInfrastructure "Current process PID $ProcessId identity was incomplete."
    }
    foreach ($optionalProperty in @('Name', 'ExecutablePath')) {
        $optional = $wmi.PSObject.Properties[$optionalProperty]
        if ($null -ne $optional -and $null -ne $optional.Value -and
                -not (Test-VerifierStrictStringValue $optional.Value)) {
            Throw-VerifierInfrastructure "Current process PID $ProcessId identity had a malformed $optionalProperty field."
        }
    }
    if ($ExpectedParentProcessId -gt 0 -and
            [int]$wmi.ParentProcessId -ne $ExpectedParentProcessId) {
        Throw-VerifierInfrastructure "Process PID $ProcessId parent identity changed before termination."
    }
    $currentParentRecord = Get-VerifierCurrentProcessRecordById ([int]$wmi.ParentProcessId)
    if ($null -eq $currentParentRecord -or
            [long]$currentParentRecord.ProcessStartTicks -le 0) {
        Throw-VerifierInfrastructure "Process PID $ProcessId parent identity could not be positively identified."
    }
    $parentStartTicks = [long]$currentParentRecord.ProcessStartTicks
    if ($ExpectedParentProcessStartTicks -gt 0 -and
            $parentStartTicks -ne $ExpectedParentProcessStartTicks) {
        Throw-VerifierInfrastructure "Process PID $ProcessId parent start identity changed before termination."
    }
    $commandLine = [string]$wmi.CommandLine
    if (-not [String]::IsNullOrWhiteSpace($ExpectedCommandLine) -and
            -not (Test-VerifierCommandLineEquivalent $commandLine $ExpectedCommandLine)) {
        Throw-VerifierInfrastructure "Process PID $ProcessId command-line identity changed before termination."
    }
    if (-not [String]::IsNullOrWhiteSpace($Script) -and
            -not (Test-VerifierCommandLinePath $commandLine $Script)) {
        Throw-VerifierInfrastructure "Process PID $ProcessId no longer references the owned script."
    }
    if ($Port -gt 0 -and
            -not (Test-VerifierCommandLineSwitch $commandLine '-Port' ([string]$Port)) -and
            -not (Test-VerifierCommandLineSwitch $commandLine '--remote-debugging-port' ([string]$Port))) {
        Throw-VerifierInfrastructure "Process PID $ProcessId no longer carries the owned port identity."
    }
    if (-not [String]::IsNullOrWhiteSpace($RunId) -and
            -not (Test-VerifierCommandLineSwitch $commandLine '-VerifierRunId' $RunId) -and
            -not (Test-VerifierCommandLineSwitch $commandLine '--tsj-verifier-run' $RunId)) {
        Throw-VerifierInfrastructure "Process PID $ProcessId no longer carries the owned run identity."
    }
    if (-not [String]::IsNullOrWhiteSpace($Nonce) -and
            -not (Test-VerifierCommandLineSwitch $commandLine '-VerifierNonce' $Nonce)) {
        Throw-VerifierInfrastructure "Process PID $ProcessId no longer carries the owned preview nonce."
    }
    # Re-query after the Win32_Process checks so the returned object is the
    # current verified process instance immediately before any stop operation.
    $verifiedProcess = Get-VerifierProcessById $ProcessId
    if ($null -eq $verifiedProcess) {
        Throw-VerifierInfrastructure "Process PID $ProcessId disappeared after current identity validation."
    }
    try { $verifiedProcess.Refresh() } catch {
        Throw-VerifierInfrastructure "Could not refresh process PID $ProcessId immediately before termination: $(Get-VerifierErrorMessage $_)"
    }
    if ([bool]$verifiedProcess.HasExited -or
            (Get-VerifierProcessStartTicks $verifiedProcess) -ne [long]$currentStartTicks) {
        Throw-VerifierInfrastructure "Process PID $ProcessId changed or exited after current identity validation."
    }
    $current = [pscustomobject]@{
        ProcessId = [int]$wmi.ProcessId
        ParentProcessId = [int]$wmi.ParentProcessId
        ProcessStartTicks = [long]$currentStartTicks
        CommandLine = $commandLine
        ParentProcessStartTicks = $parentStartTicks
        Name = if ($wmi.PSObject.Properties['Name'] -and $null -ne $wmi.Name) { $wmi.Name } else { '' }
        ExecutablePath = if ($wmi.PSObject.Properties['ExecutablePath']) {
            if ($null -ne $wmi.ExecutablePath) { $wmi.ExecutablePath } else { '' }
        } else { '' }
    }
    if (-not [String]::IsNullOrWhiteSpace($ExpectedBrowserPath) -and
            -not (Test-VerifierConfiguredExecutableIdentity $ExpectedBrowserPath $current `
                -RequireExecutablePath)) {
        Throw-VerifierInfrastructure "Process PID $ProcessId does not match the configured browser executable identity."
    }
    return [pscustomobject]@{ Process = $verifiedProcess; Record = $current }
}

function Get-VerifierCurrentProcessIdentityWithRetry($ProcessId,
        $ExpectedStartTicks = 0, $ExpectedParentProcessId = 0,
        $ExpectedCommandLine = '', $Script = '', $Port = 0,
        $RunId = '', $Nonce = '', $ExpectedParentProcessStartTicks = 0,
        $ExpectedBrowserPath = '') {
    # Windows process inspection can briefly publish a record before its image
    # path is available. Retry the complete identity proof against the same
    # retained PID/start identity; never accept a partial record or substitute
    # a name-only/path-free result.
    $lastError = $null
    $retryBudgetMilliseconds = 500
    $retryBudget = [Diagnostics.Stopwatch]::StartNew()
    $retryBudgetTicks = [long][Math]::Ceiling(
        ([double][Diagnostics.Stopwatch]::Frequency * $retryBudgetMilliseconds) / 1000.0)
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
            break
        }
        try {
            $identity = Get-VerifierCurrentProcessIdentity $ProcessId `
                $ExpectedStartTicks $ExpectedParentProcessId $ExpectedCommandLine `
                $Script $Port $RunId $Nonce $ExpectedParentProcessStartTicks `
                $ExpectedBrowserPath
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                Throw-VerifierInfrastructure 'Current process identity retry exceeded its monotonic startup budget.'
            }
            if ($null -eq $identity -or
                    $null -eq $identity.PSObject.Properties['Process'] -or
                    $identity.Process.GetType() -ne [Diagnostics.Process] -or
                    $null -eq $identity.PSObject.Properties['Record']) {
                Throw-VerifierInfrastructure 'Current process identity retry returned an incomplete process record.'
            }
            $record = $identity.Record
            [void](Assert-VerifierRawProcessIdentityRecord $record `
                'current process identity retry record' -RequireParentStart)
            foreach ($propertyName in @('Name', 'ExecutablePath')) {
                if (-not $record.PSObject.Properties[$propertyName] -or
                        -not (Test-VerifierStrictStringValue $record.PSObject.Properties[$propertyName].Value) -or
                        [String]::IsNullOrWhiteSpace([string]$record.PSObject.Properties[$propertyName].Value)) {
                    Throw-VerifierInfrastructure "Current process identity retry record omitted a complete '$propertyName'."
                }
            }
            if ([int]$record.ProcessId -ne [int]$ProcessId -or
                    ($ExpectedStartTicks -gt 0 -and
                        [long]$record.ProcessStartTicks -ne [long]$ExpectedStartTicks) -or
                    (-not [String]::IsNullOrWhiteSpace($ExpectedBrowserPath) -and
                        -not (Test-VerifierConfiguredExecutableIdentity $ExpectedBrowserPath $record `
                            -RequireExecutablePath))) {
                Throw-VerifierInfrastructure 'Current process identity retry changed its retained PID/start/executable identity.'
            }
            if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                Throw-VerifierInfrastructure 'Current process identity retry exceeded its monotonic startup budget.'
            }
            return $identity
        } catch {
            $lastError = $_
            $remainingTicks = $retryBudgetTicks - $retryBudget.ElapsedTicks
            if ($attempt -ge 3 -or $remainingTicks -le 0) {
                break
            }
            # Leave a one-millisecond monotonic margin so a sleep is never
            # scheduled for the entire remaining budget. The next loop's
            # tick gate still rejects any proof that returns at/after the
            # hard bound, even if the OS wakes the process late.
            $remainingMilliseconds = ([double]$remainingTicks * 1000.0) /
                [double][Diagnostics.Stopwatch]::Frequency
            $sleepMilliseconds = [int][Math]::Floor(
                [Math]::Min(50.0, $remainingMilliseconds - 1.0))
            if ($sleepMilliseconds -lt 1) { break }
            Start-Sleep -Milliseconds $sleepMilliseconds
        }
    }
    if ($null -ne $lastError) { throw $lastError }
    Throw-VerifierInfrastructure 'Current process identity retry produced no proof.'
}

function Get-VerifierCommandLineSwitchPresence($CommandLine, [string]$Switch,
        [string]$ExpectedValue) {
    $present = $false
    $allMatch = $true
    if ([String]::IsNullOrWhiteSpace([string]$CommandLine) -or
            [String]::IsNullOrWhiteSpace($Switch)) {
        return [pscustomobject]@{ Present = $false; Matches = $false }
    }
    $tokens = @(ConvertFrom-VerifierWindowsCommandLine ([string]$CommandLine))
    $expectedCanonical = Get-VerifierCanonicalWindowsPath $ExpectedValue
    $pathValueExpected = (Test-VerifierPathValuedSwitch $Switch) -or
        -not [String]::IsNullOrWhiteSpace($expectedCanonical)
    $valueMatches = {
        param([string]$ActualValue)
        if ($pathValueExpected) {
            return Test-VerifierCanonicalWindowsPathValue $ActualValue $ExpectedValue
        }
        return Test-VerifierCommandLineValue $ActualValue $ExpectedValue
    }
    for ($index = 0; $index -lt $tokens.Count; $index++) {
        $token = [string]$tokens[$index]
        if ($token.Equals($Switch, [StringComparison]::OrdinalIgnoreCase)) {
            $present = $true
            if ($index + 1 -ge $tokens.Count -or
                    -not (& $valueMatches ([string]$tokens[$index + 1]))) {
                $allMatch = $false
            }
            continue
        }
        $prefix = $Switch + '='
        if ($token.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
            $present = $true
            if (-not (& $valueMatches $token.Substring($prefix.Length))) {
                $allMatch = $false
            }
        }
    }
    return [pscustomobject]@{ Present = $present; Matches = ($present -and $allMatch) }
}

function Test-VerifierDescendantExecutableIdentity($RootOwnerRecord,
        $CandidateChildRecord) {
    if ($null -eq $RootOwnerRecord -or $null -eq $CandidateChildRecord -or
            -not $CandidateChildRecord.PSObject.Properties['Name'] -or
            [String]::IsNullOrWhiteSpace([string]$CandidateChildRecord.Name)) {
        return $false
    }
    $expectedPath = if ($RootOwnerRecord.PSObject.Properties['BrowserPath']) {
        [string]$RootOwnerRecord.BrowserPath
    } else { '' }
    # Every run-owned browser descendant is a termination candidate only when
    # WMI exposed a nonblank executable path and that path is the exact
    # configured browser binary. Markerless Chromium/Edge helpers may omit the
    # verifier switches, but they may not omit executable identity. A missing
    # BrowserPath on a browser owner is itself an unproven ownership state;
    # never fall back to a name-only or PPID-only decision.
    if ($RootOwnerRecord.PSObject.Properties['Profile'] -and
            -not [String]::IsNullOrWhiteSpace([string]$RootOwnerRecord.Profile)) {
        if ([String]::IsNullOrWhiteSpace($expectedPath)) { return $false }
        if (Test-VerifierConfiguredExecutableIdentity $expectedPath `
                $CandidateChildRecord -RequireExecutablePath) {
            return $true
        }
        return (Test-VerifierEdgeCompanionExecutableIdentity $RootOwnerRecord `
            $CandidateChildRecord $expectedPath)
    }
    return $false
}

function Test-VerifierDescendantOwnership($RootOwnerRecord, $CandidateChildRecord,
        [int]$ExpectedParentProcessId = 0) {
    # PowerShell variable names are case-insensitive. Keep the root owner and
    # candidate child under distinct names for the entire function; a loop
    # local can therefore never overwrite the owner and authorize a child
    # against its own identity.
    $rootOwner = $RootOwnerRecord
    $candidateChild = $CandidateChildRecord
    if ($null -eq $rootOwner -or $null -eq $candidateChild) {
        return $false
    }
    [void](Assert-VerifierRawProcessRecord $candidateChild `
        'descendant ownership candidate' -RequirePositiveIdentity -RequireCommandLine)
    $candidateStartProperty = $candidateChild.PSObject.Properties['ProcessStartTicks']
    if ($null -eq $candidateStartProperty -or
            -not (Test-VerifierStrictIntegralValue $candidateStartProperty.Value 0 ([long]::MaxValue))) {
        Throw-VerifierInfrastructure 'Descendant ownership candidate omitted or carried a malformed raw start identity.'
    }
    if ([long]$candidateStartProperty.Value -le 0) {
        # Zero is an explicit stale/unavailable identity, not an ownership
        # proof. It is validated above and must remain a non-owned candidate.
        return $false
    }
    $expectedParent = if ($ExpectedParentProcessId -gt 0) {
        $ExpectedParentProcessId
    } else { [int]$rootOwner.ProcessId }
    if ([int]$candidateChild.ParentProcessId -ne $expectedParent) {
        return $false
    }
    if ($rootOwner.PSObject.Properties['Profile'] -and
            -not [String]::IsNullOrWhiteSpace([string]$rootOwner.Profile)) {
        if (-not (Test-VerifierDescendantExecutableIdentity $rootOwner $candidateChild)) {
            return $false
        }
        # Real Chromium/Edge renderer, utility, and GPU helpers do not carry
        # every root-only verifier switch. A markerless child is admissible
        # only after the complete ancestry, current PID/start/parent proof,
        # and exact configured executable identity have succeeded. If a child
        # does carry one of these markers, every present marker must match;
        # wrong or prefix-like markers can never authorize cleanup.
        $markerChecks = @(
            @{ Switch = '--user-data-dir'; Value = [string]$rootOwner.Profile },
            @{ Switch = '--tsj-verifier-run'; Value = [string]$rootOwner.RunId },
            @{ Switch = '--tsj-verifier-worktree'; Value = [string]$rootOwner.RepositoryIdentity },
            @{ Switch = '--remote-debugging-port'; Value = [string]$rootOwner.CdpPort }
        )
        foreach ($markerCheck in $markerChecks) {
            $presence = Get-VerifierCommandLineSwitchPresence $candidateChild.CommandLine `
                ([string]$markerCheck.Switch) ([string]$markerCheck.Value)
            if ($presence.Present -and -not $presence.Matches) {
                return $false
            }
        }
        return $true
    }
    # A run-owned preview is a PowerShell process rather than a browser. Its
    # descendants must carry the exact script/port/run/nonce identity before
    # they can be considered owned or stopped during timeout cleanup.
    if ($rootOwner.PSObject.Properties['Script'] -and
            -not [String]::IsNullOrWhiteSpace([string]$rootOwner.Script)) {
        return (Test-VerifierCommandLinePath $candidateChild.CommandLine $rootOwner.Script) -and
            (Test-VerifierCommandLineSwitch $candidateChild.CommandLine '-Port' ([string]$rootOwner.Port)) -and
            (Test-VerifierCommandLineSwitch $candidateChild.CommandLine '-VerifierRunId' $rootOwner.RunId) -and
            (Test-VerifierCommandLineSwitch $candidateChild.CommandLine '-VerifierNonce' $rootOwner.Nonce)
    }
    return $false
}

function Get-VerifierDescendantCandidateRecords($RootOwnerRecord, $Snapshot,
        [bool]$QueryOwnedParents = $false) {
    if ($null -eq $RootOwnerRecord -or $null -eq $Snapshot) {
        Throw-VerifierInfrastructure 'Cannot inspect browser descendants without a complete process snapshot.'
    }
    [void](Assert-VerifierRawProcessId $RootOwnerRecord 'browser descendant cleanup root')
    $byParent = @{}
    foreach ($item in @($Snapshot)) {
        [void](Assert-VerifierRawProcessRecord $item 'browser descendant snapshot record')
        $parent = 0
        $parent = [int]$item.ParentProcessId
        if ($parent -le 0) { continue }
        if (-not $byParent.ContainsKey($parent)) {
            $byParent[$parent] = New-Object Collections.ArrayList
        }
        # Keep a raw-typed record in the parent bucket. If that bucket is
        # reachable from an owned PID, command-line completeness is checked
        # before the candidate can enter the ownership proof.
        [void]$byParent[$parent].Add($item)
    }
    $queue = New-Object Collections.Generic.Queue[int]
    # Keep the root owner named distinctly. PowerShell variable names are
    # case-insensitive; a child local named `$record` would otherwise replace
    # the owner parameter and make a child validate against itself.
    $queue.Enqueue([int]$RootOwnerRecord.ProcessId)
    $seen = @{}
    $depthByProcessId = @{}
    $depthByProcessId[[int]$RootOwnerRecord.ProcessId] = 0
    $result = New-Object Collections.ArrayList
    while ($queue.Count -gt 0) {
        $parent = $queue.Dequeue()
        if (-not $depthByProcessId.ContainsKey($parent)) {
            Throw-VerifierInfrastructure "Browser descendant graph lost ancestry depth for owned PID $parent."
        }
        $parentDepth = [int]$depthByProcessId[$parent]
        $children = New-Object Collections.ArrayList
        $childIds = @{}
        if ($byParent.ContainsKey($parent)) {
            foreach ($child in $byParent[$parent]) {
                [void](Assert-VerifierRawProcessRecord $child `
                    "browser descendant record under owned PID $parent" `
                    -RequirePositiveIdentity -RequireCommandLine)
                $childId = [int]$child.ProcessId
                if ([int]$child.ParentProcessId -ne $parent) {
                    Throw-VerifierInfrastructure "Browser descendant record under owned PID $parent had an invalid PID/parent identity."
                }
                if ($childIds.ContainsKey($childId)) {
                    Throw-VerifierInfrastructure "Browser descendant graph contained duplicate PID $childId under owned PID $parent."
                }
                $childIds[$childId] = $true
                [void]$children.Add($child)
            }
        }
        if ($QueryOwnedParents) {
            $queriedChildren = @()
            try {
                $queriedChildren = @(Get-VerifierProcessRecordsByParentWithFallback $parent `
                    'exact browser descendant')
            } catch {
                if (Test-VerifierInfrastructureError $_) { throw }
                Throw-VerifierInfrastructure ("Could not query descendants of owned PID " +
                    [string]$parent + ': ' + (Get-VerifierErrorMessage $_))
            }
            foreach ($child in $queriedChildren) {
                [void](Assert-VerifierRawProcessRecord $child `
                    "exact browser descendant query under owned PID $parent" `
                    -RequirePositiveIdentity -RequireCommandLine)
                $childId = [int]$child.ProcessId
                if ([int]$child.ParentProcessId -ne $parent) {
                    Throw-VerifierInfrastructure "Exact browser descendant query under owned PID $parent returned an invalid PID/parent identity."
                }
                if ($childIds.ContainsKey($childId)) {
                    # The same current process may appear in both the broad
                    # snapshot and the exact parent query; it is the same
                    # candidate, not an ambiguous second identity.
                    continue
                }
                $childIds[$childId] = $true
                [void]$children.Add($child)
            }
        }
        foreach ($child in $children) {
                [void](Assert-VerifierRawProcessRecord $child `
                    "browser descendant candidate under owned PID $parent" `
                    -RequirePositiveIdentity -RequireCommandLine)
                $id = [int]$child.ProcessId
                if ($seen.ContainsKey($id)) {
                    # A PID appearing under a second parent is not a harmless
                    # duplicate: it is evidence of a stale/reparented snapshot
                    # or PID reuse.  Never let the first observation authorize
                    # a later stop of the wrong process instance.
                    if ([int]$seen[$id] -ne $parent) {
                        Throw-VerifierInfrastructure "Browser descendant PID $id appeared under conflicting parents $($seen[$id]) and $parent."
                    }
                    continue
                }
                $seen[$id] = $parent
                if ($id -le 0 -or
                        -not $child.PSObject.Properties['ParentProcessId'] -or
                        [int]$child.ParentProcessId -ne $parent -or
                        -not $child.PSObject.Properties['CommandLine'] -or
                        [String]::IsNullOrWhiteSpace([string]$child.CommandLine)) {
                    Throw-VerifierInfrastructure "Browser descendant process record for PID $id was incomplete."
                }
                $childDepth = $parentDepth + 1
                try {
                    if ($child.PSObject.Properties['VerifierDepth']) {
                        $child.VerifierDepth = $childDepth
                    } else {
                        $child | Add-Member -MemberType NoteProperty `
                            -Name VerifierDepth -Value $childDepth -Force -ErrorAction Stop
                    }
                } catch {
                    Throw-VerifierInfrastructure "Could not retain ancestry depth for browser descendant PID ${id}: $(Get-VerifierErrorMessage $_)"
                }
                $depthByProcessId[$id] = $childDepth
                [void]$result.Add($child)
                $queue.Enqueue($id)
        }
    }
    return @($result)
}

function New-VerifierBrowserDrainScope() {
    # The scope is an in-memory capability created only by the fixed-point
    # browser cleanup owner. Its reference and private marker are both checked
    # before any attestation can enter the natural-exit lane.
    $scope = [pscustomobject]@{
        Protocol = 'browser-drain-scope-v1'
        Marker = $script:VerifierBrowserDrainScopeMarker
        Disposed = $false
    }
    # Keep the original scope, retained handles, and immutable attestation
    # bindings in module-private storage. Copying the public marker or fields
    # cannot mint a new scope or replace a registered Process instance.
    $registration = [Tuple]::Create($scope,
        (New-Object Collections.ArrayList), (New-Object Collections.ArrayList))
    [void]$script:VerifierBrowserDrainScopes.Add($registration)
    return $scope
}

function Get-VerifierBrowserDrainScopeRegistration($DrainScope) {
    foreach ($registration in @($script:VerifierBrowserDrainScopes)) {
        if ([object]::ReferenceEquals($registration.Item1, $DrainScope)) {
            return $registration
        }
    }
    Throw-VerifierInfrastructure 'Browser drain scope was copied, foreign, or already disposed.'
}

function Assert-VerifierBrowserDrainScope($DrainScope, [string]$Label = 'browser drain scope') {
    [void](Get-VerifierBrowserDrainScopeRegistration $DrainScope)
    if ($null -eq $DrainScope -or $DrainScope -is [array] -or
            -not $DrainScope.PSObject.Properties['Protocol'] -or
            [string]$DrainScope.Protocol -cne 'browser-drain-scope-v1' -or
            -not $DrainScope.PSObject.Properties['Marker'] -or
            -not [object]::ReferenceEquals($DrainScope.Marker,
                $script:VerifierBrowserDrainScopeMarker) -or
            -not $DrainScope.PSObject.Properties['Disposed'] -or
            -not (Test-VerifierStrictBooleanValue $DrainScope.Disposed) -or
            [bool]$DrainScope.Disposed) {
        Throw-VerifierInfrastructure "$Label was missing, copied, foreign, malformed, or already disposed."
    }
}

function Add-VerifierBrowserDrainHandle($DrainScope, $Process,
        [string]$Label = 'browser drain process handle') {
    Assert-VerifierBrowserDrainScope $DrainScope $Label
    if ($null -eq $Process -or $Process.GetType() -ne [Diagnostics.Process]) {
        Throw-VerifierInfrastructure "$Label was not an exact System.Diagnostics.Process object."
    }
    $registration = Get-VerifierBrowserDrainScopeRegistration $DrainScope
    foreach ($retained in @($registration.Item2)) {
        if ([object]::ReferenceEquals($retained, $Process)) { return }
    }
    [void]$registration.Item2.Add($Process)
}

function New-VerifierBrowserDrainAttestation($DrainScope, $Record, $Process) {
    Assert-VerifierBrowserDrainScope $DrainScope
    if ($null -eq $Record -or $Record -is [array]) {
        Throw-VerifierInfrastructure 'Browser drain attestation omitted its exact descendant record.'
    }
    foreach ($propertyName in @('ProcessId', 'ProcessStartTicks',
            'ParentProcessId', 'ParentProcessStartTicks', 'Name',
            'ExecutablePath', 'CommandLine')) {
        if (-not $Record.PSObject.Properties[$propertyName]) {
            Throw-VerifierInfrastructure "Browser drain attestation record omitted $propertyName."
        }
    }
    if (-not (Test-VerifierStrictIntegralValue $Record.ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $Record.ProcessStartTicks 1 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $Record.ParentProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $Record.ParentProcessStartTicks 1 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictStringValue $Record.Name) -or
            [String]::IsNullOrWhiteSpace([string]$Record.Name) -or
            -not (Test-VerifierStrictStringValue $Record.ExecutablePath) -or
            [String]::IsNullOrWhiteSpace([string]$Record.ExecutablePath) -or
            -not (Test-VerifierStrictStringValue $Record.CommandLine) -or
            [String]::IsNullOrWhiteSpace([string]$Record.CommandLine)) {
        Throw-VerifierInfrastructure 'Browser drain attestation record carried incomplete or malformed immutable identity scalars.'
    }
    if ($null -eq $Process -or $Process.GetType() -ne [Diagnostics.Process] -or
            -not $Record.PSObject.Properties['Process'] -or
            -not [object]::ReferenceEquals($Record.Process, $Process)) {
        Throw-VerifierInfrastructure 'Browser drain attestation omitted its exact retained Process handle.'
    }

    # Force acquisition of the native process handle while the child is still
    # live, then re-read PID/start identity from this same Process instance.
    # No PID lookup is allowed to manufacture this proof.
    try {
        [void]$Process.Refresh()
        if ([bool]$Process.HasExited) {
            Throw-VerifierInfrastructure "Browser drain child PID $($Record.ProcessId) exited before its retained handle was acquired."
        }
        $nativeHandle = $Process.Handle
        if ($nativeHandle -eq [IntPtr]::Zero -or
                [long]$nativeHandle -eq -1L) {
            Throw-VerifierInfrastructure "Browser drain child PID $($Record.ProcessId) did not expose a valid retained native handle."
        }
        # Register immediately after acquisition so a subsequent refresh or
        # start-identity failure is still covered by fixed-point disposal.
        [void](Add-VerifierBrowserDrainHandle $DrainScope $Process)
        [void]$Process.Refresh()
        if ([bool]$Process.HasExited) {
            Throw-VerifierInfrastructure "Browser drain child PID $($Record.ProcessId) exited while its retained handle was being established."
        }
        $handlePid = [int]$Process.Id
        $handleStart = [long](Get-VerifierProcessStartTicks $Process)
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not acquire the retained browser drain handle: ' +
            (Get-VerifierErrorMessage $_))
    }
    if ($handlePid -ne [int]$Record.ProcessId -or
            $handleStart -ne [long]$Record.ProcessStartTicks) {
        Throw-VerifierInfrastructure "Browser drain child PID $($Record.ProcessId) changed PID/start identity after retained-handle acquisition."
    }

    # System.Tuple is intentionally used for the scalar snapshot: its ItemN
    # properties are immutable, while the original record and Process object
    # remain reference-bound capabilities checked below.
    $identity = [Tuple]::Create(
        ([int]$Record.ProcessId),
        ([long]$Record.ProcessStartTicks),
        ([int]$Record.ParentProcessId),
        ([long]$Record.ParentProcessStartTicks),
        ([string]$Record.Name),
        ([string]$Record.ExecutablePath),
        ([string]$Record.CommandLine))
    $attestation = [pscustomobject]@{
        Protocol = 'browser-drain-attestation-v1'
        Marker = $script:VerifierBrowserDrainAttestationMarker
        Scope = $DrainScope
        Record = $Record
        Process = $Process
        ProcessId = [int]$Record.ProcessId
        ProcessStartTicks = [long]$Record.ProcessStartTicks
        ParentProcessId = [int]$Record.ParentProcessId
        ParentProcessStartTicks = [long]$Record.ParentProcessStartTicks
        Name = [string]$Record.Name
        ExecutablePath = [string]$Record.ExecutablePath
        CommandLine = [string]$Record.CommandLine
        Identity = $identity
    }
    $registration = Get-VerifierBrowserDrainScopeRegistration $DrainScope
    [void]$registration.Item3.Add([Tuple]::Create($attestation, $Record,
        $Process, $identity, ([IntPtr]$nativeHandle)))
    return $attestation
}

function Get-VerifierBrowserDrainAttestation($DrainScope, $Record) {
    Assert-VerifierBrowserDrainScope $DrainScope
    if ($null -eq $Record -or $Record -is [array] -or
            -not $Record.PSObject.Properties['VerifierDrainAttestation']) {
        Throw-VerifierInfrastructure 'Browser drain natural-exit proof omitted the exact attested descendant record.'
    }
    $attestation = $Record.VerifierDrainAttestation
    if ($null -eq $attestation -or
            -not $attestation.PSObject.Properties['Protocol'] -or
            [string]$attestation.Protocol -cne 'browser-drain-attestation-v1' -or
            -not $attestation.PSObject.Properties['Marker'] -or
            -not [object]::ReferenceEquals($attestation.Marker,
                $script:VerifierBrowserDrainAttestationMarker) -or
            -not $attestation.PSObject.Properties['Scope'] -or
            -not [object]::ReferenceEquals($attestation.Scope, $DrainScope) -or
            -not $attestation.PSObject.Properties['Record'] -or
            -not [object]::ReferenceEquals($attestation.Record, $Record) -or
            -not $attestation.PSObject.Properties['Process'] -or
            $null -eq $attestation.Process -or
            $attestation.Process.GetType() -ne [Diagnostics.Process] -or
            -not $attestation.PSObject.Properties['Identity']) {
        Throw-VerifierInfrastructure 'Browser drain natural-exit proof carried a copied, foreign, or malformed attestation.'
    }
    $registration = Get-VerifierBrowserDrainScopeRegistration $DrainScope
    $binding = $null
    foreach ($candidate in @($registration.Item3)) {
        if ([object]::ReferenceEquals($candidate.Item1, $attestation)) {
            $binding = $candidate
            break
        }
    }
    if ($null -eq $binding) {
        Throw-VerifierInfrastructure 'Browser drain natural-exit proof carried an unregistered attestation.'
    }
    if (-not [object]::ReferenceEquals($binding.Item2, $Record) -or
            -not [object]::ReferenceEquals($binding.Item3, $attestation.Process) -or
            -not [object]::ReferenceEquals($binding.Item4, $attestation.Identity) -or
            -not $Record.PSObject.Properties['Process'] -or
            -not [object]::ReferenceEquals($binding.Item3, $Record.Process)) {
        Throw-VerifierInfrastructure 'Browser drain attestation replaced its original record, Process, or immutable identity tuple.'
    }
    $registeredHandle = $false
    foreach ($retained in @($registration.Item2)) {
        if ([object]::ReferenceEquals($retained, $binding.Item3)) {
            $registeredHandle = $true
            break
        }
    }
    try {
        if (-not $registeredHandle -or
                $attestation.Process.Handle -ne $binding.Item5) {
            Throw-VerifierInfrastructure 'Browser drain attestation lost or replaced its pinned native process handle.'
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Browser drain pinned native handle was unreadable: ' +
            (Get-VerifierErrorMessage $_))
    }
    $identity = $binding.Item4
    $actual = @($attestation.ProcessId, $attestation.ProcessStartTicks,
        $attestation.ParentProcessId, $attestation.ParentProcessStartTicks,
        [string]$attestation.Name, [string]$attestation.ExecutablePath,
        [string]$attestation.CommandLine)
    $tuple = @($identity.Item1, $identity.Item2, $identity.Item3,
        $identity.Item4, [string]$identity.Item5, [string]$identity.Item6,
        [string]$identity.Item7)
    if ($actual.Count -ne 7 -or $tuple.Count -ne 7) {
        Throw-VerifierInfrastructure 'Browser drain attestation carried an incomplete immutable scalar identity.'
    }
    if (-not (Test-VerifierStrictIntegralValue $attestation.ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $attestation.ProcessStartTicks 1 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $attestation.ParentProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $attestation.ParentProcessStartTicks 1 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictStringValue $attestation.Name) -or
            -not (Test-VerifierStrictStringValue $attestation.ExecutablePath) -or
            -not (Test-VerifierStrictStringValue $attestation.CommandLine) -or
            [int]$attestation.ProcessId -ne [int]$identity.Item1 -or
            [long]$attestation.ProcessStartTicks -ne [long]$identity.Item2 -or
            [int]$attestation.ParentProcessId -ne [int]$identity.Item3 -or
            [long]$attestation.ParentProcessStartTicks -ne [long]$identity.Item4 -or
            [string]$attestation.Name -cne [string]$identity.Item5 -or
            [string]$attestation.ExecutablePath -cne [string]$identity.Item6 -or
            [string]$attestation.CommandLine -cne [string]$identity.Item7) {
        Throw-VerifierInfrastructure 'Browser drain attestation immutable scalar identity was mutated.'
    }
    foreach ($propertyName in @('ProcessId', 'ProcessStartTicks',
            'ParentProcessId', 'ParentProcessStartTicks', 'Name',
            'ExecutablePath', 'CommandLine')) {
        if (-not $Record.PSObject.Properties[$propertyName]) {
            Throw-VerifierInfrastructure "Attested browser descendant omitted $propertyName after discovery."
        }
    }
    $recordActual = @($Record.ProcessId, $Record.ProcessStartTicks,
        $Record.ParentProcessId, $Record.ParentProcessStartTicks,
        [string]$Record.Name, [string]$Record.ExecutablePath,
        [string]$Record.CommandLine)
    if (-not (Test-VerifierStrictIntegralValue $Record.ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $Record.ProcessStartTicks 1 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $Record.ParentProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $Record.ParentProcessStartTicks 1 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictStringValue $Record.Name) -or
            -not (Test-VerifierStrictStringValue $Record.ExecutablePath) -or
            -not (Test-VerifierStrictStringValue $Record.CommandLine) -or
            [int]$Record.ProcessId -ne [int]$identity.Item1 -or
            [long]$Record.ProcessStartTicks -ne [long]$identity.Item2 -or
            [int]$Record.ParentProcessId -ne [int]$identity.Item3 -or
            [long]$Record.ParentProcessStartTicks -ne [long]$identity.Item4 -or
            [string]$Record.Name -cne [string]$identity.Item5 -or
            [string]$Record.ExecutablePath -cne [string]$identity.Item6 -or
            [string]$Record.CommandLine -cne [string]$identity.Item7) {
        Throw-VerifierInfrastructure 'Attested browser descendant record changed its immutable identity after discovery.'
    }
    return $attestation
}

function Get-VerifierBrowserDrainNaturalExitResult($DrainScope, $Record) {
    # One monotonic whole-call budget covers capability validation, retained
    # handle identity, both current views, and the gap between observations.
    $proofBudget = [Diagnostics.Stopwatch]::StartNew()
    $proofBudgetTicks = [long][Math]::Ceiling(
        ([double][Diagnostics.Stopwatch]::Frequency * 500) / 1000.0)
    if ($proofBudget.ElapsedTicks -ge $proofBudgetTicks) {
        Throw-VerifierInfrastructure 'Browser drain natural-exit proof exceeded its bounded interval before attestation validation.'
    }
    $attestation = Get-VerifierBrowserDrainAttestation $DrainScope $Record
    if ($proofBudget.ElapsedTicks -ge $proofBudgetTicks) {
        Throw-VerifierInfrastructure 'Browser drain natural-exit attestation validation exceeded its bounded interval.'
    }
    $identity = $attestation.Identity
    $process = $attestation.Process
    $handlePid = 0
    $hasExited = $false
    try {
        [void]$process.Refresh()
        $handlePid = [int]$process.Id
        if ($handlePid -ne [int]$identity.Item1) {
            Throw-VerifierInfrastructure "Browser drain retained handle PID $handlePid no longer matches attested PID $($identity.Item1)."
        }
        $hasExited = [bool]$process.HasExited
        # This exact native handle was pinned while live. Read its creation
        # time even after exit; a matching PID alone is never sufficient.
        $startValues = @(Get-VerifierRetainedProcessStartTimeValue $process)
        if ($startValues.Count -ne 1 -or $null -eq $startValues[0] -or
                $startValues[0].GetType() -ne [DateTime] -or
                $startValues[0].ToUniversalTime().Ticks -ne [long]$identity.Item2) {
            Throw-VerifierInfrastructure "Browser drain retained handle PID $handlePid changed or lost its attested start identity."
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not inspect the attested browser drain handle: ' +
            (Get-VerifierErrorMessage $_))
    }
    if ($proofBudget.ElapsedTicks -ge $proofBudgetTicks) {
        Throw-VerifierInfrastructure 'Browser drain retained-handle identity proof exceeded its bounded interval.'
    }
    if (-not $hasExited) { return $null }

    # A natural exit is accepted only after two bounded, independent current
    # PID/WMI absence observations. A present/reused PID or either unreadable
    # view remains infrastructure failure and never reaches Stop-Process.
    for ($observation = 1; $observation -le 2; $observation++) {
        if ($proofBudget.ElapsedTicks -ge $proofBudgetTicks) {
            Throw-VerifierInfrastructure 'Browser drain natural-exit proof expired before a current absence observation.'
        }
        $current = $null
        try {
            $current = Get-VerifierCurrentProcessRecordById $handlePid
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not confirm natural exit for browser drain PID ${handlePid}: " +
                (Get-VerifierErrorMessage $_))
        }
        if ($proofBudget.ElapsedTicks -ge $proofBudgetTicks) {
            Throw-VerifierInfrastructure 'Browser drain natural-exit current absence observation exceeded its bounded interval.'
        }
        if ($null -ne $current) {
            Throw-VerifierInfrastructure "Browser drain PID $handlePid had a current/reused process identity after its attested handle exited."
        }
        if ($observation -lt 2) {
            Start-Sleep -Milliseconds 25
            if ($proofBudget.ElapsedTicks -ge $proofBudgetTicks) {
                Throw-VerifierInfrastructure 'Browser drain natural-exit proof expired between current absence observations.'
            }
        }
    }
    return [pscustomobject]@{
        ProcessId = [int]$identity.Item1
        ProcessStartTicks = [long]$identity.Item2
        TerminationProven = $true
        NaturalExit = $true
        Attestation = $attestation
    }
}

function Get-VerifierPreviouslyAttestedBrowserDescendantExit($DrainScope, $CandidateRecord) {
    # Candidate enumeration is not ownership proof. A stale later observation
    # may refer only to the original fully verified record in this live drain
    # scope; it must never mint a handle or adopt an initially missing child.
    $proofBudget = [Diagnostics.Stopwatch]::StartNew()
    $proofBudgetTicks = [long][Math]::Ceiling(
        ([double][Diagnostics.Stopwatch]::Frequency * 500) / 1000.0)
    if ($null -eq $DrainScope) { return $null }
    Assert-VerifierBrowserDrainScope $DrainScope 'repeated browser descendant observation'
    [void](Assert-VerifierRawProcessRecord $CandidateRecord `
        'repeated browser descendant candidate' -RequirePositiveIdentity -RequireCommandLine)
    $registration = Get-VerifierBrowserDrainScopeRegistration $DrainScope
    $retainedRecord = $null
    foreach ($binding in @($registration.Item3)) {
        if ($proofBudget.ElapsedTicks -ge $proofBudgetTicks) {
            Throw-VerifierInfrastructure 'Repeated browser descendant exit proof exceeded its bounded interval during retained-identity lookup.'
        }
        if ([int]$binding.Item4.Item1 -ne [int]$CandidateRecord.ProcessId) { continue }
        $priorRecord = $binding.Item2
        # Validate the private binding before reading public record fields.
        # Copied, mutated, substituted, or cross-scope capabilities stay fatal.
        [void](Get-VerifierBrowserDrainAttestation $DrainScope $priorRecord)
        # WMI uses UInt32 PIDs and the native fallback uses Int32. Compare
        # only after raw integral validation, without boxed-type inequality.
        foreach ($field in @('ProcessId', 'ParentProcessId')) {
            if ([long]$CandidateRecord.$field -ne [long]$priorRecord.$field) {
                Throw-VerifierInfrastructure "Repeated browser descendant PID $($CandidateRecord.ProcessId) changed its observed $field before natural-exit proof."
            }
        }
        foreach ($field in @('Name', 'ExecutablePath', 'CommandLine')) {
            if (-not $CandidateRecord.PSObject.Properties[$field] -or
                    -not [object]::Equals($CandidateRecord.$field, $priorRecord.$field)) {
                Throw-VerifierInfrastructure "Repeated browser descendant PID $($CandidateRecord.ProcessId) changed its observed $field before natural-exit proof."
            }
        }
        foreach ($field in @('ProcessStartTicks', 'ParentProcessStartTicks')) {
            if ($CandidateRecord.PSObject.Properties[$field] -and
                    (-not (Test-VerifierStrictIntegralValue $CandidateRecord.$field 1 ([long]::MaxValue)) -or
                        [long]$CandidateRecord.$field -ne [long]$priorRecord.$field)) {
                Throw-VerifierInfrastructure "Repeated browser descendant PID $($CandidateRecord.ProcessId) changed its observed $field before natural-exit proof."
            }
        }
        if ($null -ne $retainedRecord) {
            foreach ($field in @('ProcessId', 'ProcessStartTicks', 'ParentProcessId',
                    'ParentProcessStartTicks', 'Name', 'ExecutablePath', 'CommandLine')) {
                if (-not [object]::Equals($retainedRecord.$field, $priorRecord.$field)) {
                    Throw-VerifierInfrastructure "Repeated browser descendant PID $($CandidateRecord.ProcessId) had conflicting retained $field identities."
                }
            }
        } else {
            $retainedRecord = $priorRecord
        }
    }
    if ($proofBudget.ElapsedTicks -ge $proofBudgetTicks) {
        Throw-VerifierInfrastructure 'Repeated browser descendant exit proof exceeded its bounded interval before current absence proof.'
    }
    if ($null -eq $retainedRecord) { return $null }
    $naturalExit = Get-VerifierBrowserDrainNaturalExitResult $DrainScope $retainedRecord
    # Include lookup, candidate comparison, attestation validation and both
    # independent current absence observations in one whole-call 500 ms budget.
    if ($proofBudget.ElapsedTicks -ge $proofBudgetTicks) {
        Throw-VerifierInfrastructure 'Repeated browser descendant exit proof exceeded its bounded interval.'
    }
    if ($null -eq $naturalExit) { return $null }
    return $retainedRecord
}

function Get-VerifierDescendantProcessRecords($RootOwnerRecord, $Snapshot,
        $DrainScope = $null) {
    # The full snapshot is supplemented with an exact WMI child query for each
    # owned PID. This candidate expansion keeps a differently named helper in
    # the graph even if a broad snapshot was filtered or incomplete.
    # Preserve the root owner under a name that cannot collide with a child
    # local in PowerShell's case-insensitive variable scope.
    $rootOwner = $RootOwnerRecord
    if ($null -ne $DrainScope) {
        Assert-VerifierBrowserDrainScope $DrainScope
    }
    [void](Assert-VerifierRawProcessIdentityRecord $rootOwner `
        'browser descendant cleanup root' `
        -ParentPropertyName 'ProcessParentProcessId' `
        -ParentStartPropertyName 'ProcessParentProcessStartTicks' `
        -CommandPropertyName 'ProcessCommandLine' -RequireParentStart)
    $ownerPid = [int]$rootOwner.ProcessId
    $ownerStart = [long]$rootOwner.ProcessStartTicks
    $ownerParent = if ($rootOwner.PSObject.Properties['ProcessParentProcessId']) {
        [int]$rootOwner.ProcessParentProcessId
    } elseif ($rootOwner.PSObject.Properties['ParentProcessId']) {
        [int]$rootOwner.ParentProcessId
    } else { 0 }
    $ownerParentStart = if ($rootOwner.PSObject.Properties['ProcessParentProcessStartTicks']) {
        [long]$rootOwner.ProcessParentProcessStartTicks
    } elseif ($rootOwner.PSObject.Properties['ParentProcessStartTicks']) {
        [long]$rootOwner.ParentProcessStartTicks
    } else { 0L }
    $ownerCommand = if ($rootOwner.PSObject.Properties['ProcessCommandLine']) {
        [string]$rootOwner.ProcessCommandLine
    } elseif ($rootOwner.PSObject.Properties['CommandLine']) {
        [string]$rootOwner.CommandLine
    } else { '' }
    if ($ownerPid -le 0 -or $ownerStart -le 0 -or $ownerParent -le 0 -or
            $ownerParentStart -le 0 -or [String]::IsNullOrWhiteSpace($ownerCommand)) {
        Throw-VerifierInfrastructure 'Browser descendant cleanup root omitted a complete current parent/start/command identity.'
    }
    # Revalidate the root before enumerating descendants.  A child cannot be
    # authorized by a stale PPID graph when the root itself has been reparented,
    # replaced, or had its command-line ownership markers changed.
    $ownerRootRecord = [pscustomobject]@{
        ProcessId = $ownerPid
        ProcessStartTicks = $ownerStart
        CommandLine = $ownerCommand
        ParentProcessId = $ownerParent
        ParentProcessStartTicks = $ownerParentStart
        VerifierDepth = 0
    }
    if ($rootOwner.PSObject.Properties['Profile']) {
        $verifiedRoot = Get-VerifierCurrentOwnedProcess $rootOwner $ownerRootRecord -Root
        if ($null -ne $DrainScope) {
            [void](Add-VerifierBrowserDrainHandle $DrainScope $verifiedRoot.Process `
                'fixed-point browser root process handle')
        }
    } elseif ($rootOwner.PSObject.Properties['Script']) {
        [void](Get-VerifierCurrentProcessIdentity $ownerPid $ownerStart $ownerParent `
            $ownerCommand ([string]$rootOwner.Script) ([int]$rootOwner.Port) `
            ([string]$rootOwner.RunId) ([string]$rootOwner.Nonce) $ownerParentStart)
    } else {
        Throw-VerifierInfrastructure 'Browser descendant cleanup root had no verifiable browser or preview ownership identity.'
    }
    $candidates = @(Get-VerifierDescendantCandidateRecords $rootOwner $Snapshot $true)
    $result = New-Object Collections.ArrayList
    $verifiedStartByPid = @{}
    $verifiedRecordByPid = @{}
    $verifiedStartByPid[$ownerPid] = $ownerStart
    foreach ($child in $candidates) {
        [void](Assert-VerifierRawProcessRecord $child `
            'browser descendant candidate' -RequirePositiveIdentity -RequireCommandLine)
        $id = [int]$child.ProcessId
        $parent = [int]$child.ParentProcessId
        if (-not $verifiedStartByPid.ContainsKey($parent)) {
            Throw-VerifierInfrastructure "Browser descendant PID $id had no verified current parent ancestry."
        }
        # PPID traversal is only a candidate relationship. Immediately before
        # admitting a child, re-query its current parent and compare the
        # parent start identity and exact ownership markers. This prevents a
        # stale PPID or PID-reused ancestor from authorizing termination.
        $currentParent = Get-VerifierCurrentProcessRecordById $parent
        if ($null -eq $currentParent -or
                [long]$currentParent.ProcessStartTicks -ne [long]$verifiedStartByPid[$parent]) {
            Throw-VerifierInfrastructure "Browser descendant PID $id parent PID $parent was replaced, missing, or had an unproven start identity."
        }
        if ($parent -ne $ownerPid) {
                    if (-not $verifiedRecordByPid.ContainsKey($parent) -or
                    -not (Test-VerifierDescendantOwnership $rootOwner $currentParent `
                        ([int]$verifiedRecordByPid[$parent].ParentProcessId))) {
                Throw-VerifierInfrastructure "Browser descendant PID $id had a foreign or reparented owned ancestor PID $parent."
            }
        }
        $childIdentityRecord = [pscustomobject]@{
            ProcessId = $id
            ProcessStartTicks = 0L
            CommandLine = [string]$child.CommandLine
            ParentProcessId = $parent
            ParentProcessStartTicks = [long]$currentParent.ProcessStartTicks
            Name = if ($child.PSObject.Properties['Name']) { [string]$child.Name } else { '' }
            ExecutablePath = if ($child.PSObject.Properties['ExecutablePath']) {
                [string]$child.ExecutablePath
            } else { '' }
            VerifierDepth = if ($child.PSObject.Properties['VerifierDepth']) {
                [int]$child.VerifierDepth
            } else { 1 }
        }
        # Candidate discovery must carry a positive process name. A native
        # snapshot may transiently omit only ExecutablePath; the bounded
        # current-child proof below retries that one incomplete observation.
        # A present path is checked immediately so a stable wrong executable
        # can never enter the retry lane.
        if ($rootOwner.PSObject.Properties['Profile']) {
            if (-not $childIdentityRecord.PSObject.Properties['Name'] -or
                    [String]::IsNullOrWhiteSpace([string]$childIdentityRecord.Name)) {
                Throw-VerifierInfrastructure "Browser descendant PID $id omitted its process name."
            }
            if ($childIdentityRecord.PSObject.Properties['ExecutablePath'] -and
                    -not [String]::IsNullOrWhiteSpace([string]$childIdentityRecord.ExecutablePath) -and
                    -not (Test-VerifierDescendantExecutableIdentity $rootOwner `
                        $childIdentityRecord)) {
                Throw-VerifierInfrastructure "Browser descendant PID $id omitted or mismatched the configured executable identity."
            }
        } elseif (-not (Test-VerifierDescendantExecutableIdentity $rootOwner `
                $childIdentityRecord)) {
            Throw-VerifierInfrastructure "Browser descendant PID $id omitted or mismatched the configured executable identity."
        }
        $childProcess = Get-VerifierProcessById $id
        $childExited = $false
        if ($null -ne $childProcess) {
            if ($childProcess.GetType() -ne [Diagnostics.Process] -or
                    [int]$childProcess.Id -ne $id) {
                Throw-VerifierInfrastructure "Browser descendant PID $id returned a mismatched current Process object."
            }
            try {
                [void]$childProcess.Refresh()
                $childExited = [bool]$childProcess.HasExited
            } catch {
                Throw-VerifierInfrastructure "Could not inspect browser descendant PID $id liveness during ownership cleanup: $(Get-VerifierErrorMessage $_)"
            }
        }
        if ($null -eq $childProcess -or $childExited) {
            # Windows may return a Process object whose child has already
            # exited. It carries no new live start-identity proof; use only
            # an original attestation, with the same independent absences.
            $retainedExit = Get-VerifierPreviouslyAttestedBrowserDescendantExit `
                $DrainScope $child
            if ($null -ne $retainedExit) {
                $verifiedStartByPid[$id] = [long]$retainedExit.ProcessStartTicks
                $verifiedRecordByPid[$id] = $retainedExit
                [void]$result.Add($retainedExit)
                continue
            }
            # Failure diagnostics read only the already captured candidate and
            # private scope bindings. Do not query a new process/handle here or
            # turn an initially unproven disappearance into an accepted exit.
            $priorBindingCount = 0
            foreach ($scopeRegistration in @($script:VerifierBrowserDrainScopes)) {
                if (-not [object]::ReferenceEquals($scopeRegistration.Item1, $DrainScope)) {
                    continue
                }
                foreach ($scopeBinding in @($scopeRegistration.Item3)) {
                    if ([int]$scopeBinding.Item4.Item1 -eq $id) { $priorBindingCount++ }
                }
            }
            $processType = [regex]::Match($childIdentityRecord.CommandLine,
                '(?:^|[\s"])--type=([A-Za-z0-9_.:-]+)').Groups[1].Value
            $utilitySubtype = [regex]::Match($childIdentityRecord.CommandLine,
                '(?:^|[\s"])--utility-sub-type=([A-Za-z0-9_.:-]+)').Groups[1].Value
            Throw-VerifierInfrastructureMissingProcess `
                ("Could not inspect browser descendant PID $id during ownership cleanup. " +
                    "[scopePresent=$($null -ne $DrainScope); priorBindings=$priorBindingCount; " +
                    "currentProcessPresent=$($null -ne $childProcess); currentProcessExited=$childExited; " +
                    "processType=$processType; utilitySubtype=$utilitySubtype]") $id
        }
        $childIdentityRecord.ProcessStartTicks = Get-VerifierProcessStartTicks $childProcess
        if ([long]$childIdentityRecord.ProcessStartTicks -le 0) {
            Throw-VerifierInfrastructure "Browser descendant PID $id has unknown start identity."
        }
        # `$rootOwner` is the root owner and must remain untouched. PowerShell
        # variable names are case-insensitive, so a child local can never
        # replace it and make the child validate against itself.
        $verified = $null
        try {
            $verified = if ($rootOwner.PSObject.Properties['Profile']) {
                Get-VerifierCurrentOwnedDescendantWithRetry $rootOwner $childIdentityRecord
            } else {
                Get-VerifierCurrentOwnedProcessOnce $rootOwner $childIdentityRecord
            }
        } catch {
            # An already attested child can also exit during the subsequent
            # fresh live proof. Only a typed disappearance for this exact PID
            # may consult the retained handle; changed identity, inaccessible
            # data, and expired proof budgets remain unconditional failures.
            if ((Get-VerifierMissingProcessId $_) -eq $id) {
                $retainedExit = Get-VerifierPreviouslyAttestedBrowserDescendantExit `
                    $DrainScope $childIdentityRecord
                if ($null -ne $retainedExit) {
                    $verifiedStartByPid[$id] = [long]$retainedExit.ProcessStartTicks
                    $verifiedRecordByPid[$id] = $retainedExit
                    [void]$result.Add($retainedExit)
                    continue
                }
            }
            throw
        }
        $verified.Record | Add-Member -NotePropertyName ParentProcessStartTicks `
            -NotePropertyValue ([long]$currentParent.ProcessStartTicks) -Force
        $verified.Record | Add-Member -NotePropertyName VerifierDepth `
            -NotePropertyValue ([int]$childIdentityRecord.VerifierDepth) -Force
        $verified.Record | Add-Member -NotePropertyName Process -NotePropertyValue $verified.Process
        if ($null -ne $DrainScope) {
            # The attestation is attached only after the complete live
            # descendant proof above has passed. Ordinary listener/inspection
            # callers omit the optional scope and retain their prior behavior.
            $drainAttestation = New-VerifierBrowserDrainAttestation $DrainScope `
                $verified.Record $verified.Process
            $verified.Record | Add-Member -NotePropertyName VerifierDrainAttestation `
                -NotePropertyValue $drainAttestation -Force
        }
        $verifiedStartByPid[$id] = [long]$verified.Record.ProcessStartTicks
        $verifiedRecordByPid[$id] = $verified.Record
        [void]$result.Add($verified.Record)
    }
    return @($result)
}

function Remove-VerifierBrowserProfile($Context, $ProfileRecord) {
    [void](Assert-VerifierContextPreflight $Context 'browser profile cleanup context' `
        -RequiredProperties @('RunId', 'RepositoryIdentity', 'RunRoot'))
    [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $ProfileRecord.Profile -ValidateTree)
    if (-not (Test-Path -LiteralPath $ProfileRecord.Profile)) { return }
    try {
        $profileItem = Get-Item -LiteralPath $ProfileRecord.Profile -Force -ErrorAction Stop
        if ($profileItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            Throw-VerifierInfrastructure "Refusing to remove a reparse-point browser profile."
        }
        # The tree is rechecked immediately before recursive deletion.  This
        # prevents a junction/symlink inserted after the lexical ownership
        # check from redirecting deletion outside the run root.
        Remove-VerifierOwnedTree $Context.RunRoot $ProfileRecord.Profile
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure "Could not remove owned browser profile '$($ProfileRecord.Profile)': $(Get-VerifierErrorMessage $_)"
    }
    if (Test-Path -LiteralPath $ProfileRecord.Profile) {
        Throw-VerifierInfrastructure "Owned browser profile remained after cleanup: $($ProfileRecord.Profile)"
    }
}

function Connect-VerifierCdpSocket([Uri]$TargetUri, [DateTime]$Deadline) {
    if ($null -eq $TargetUri) {
        Throw-VerifierInfrastructure 'Cannot attach to a missing CDP target URI.'
    }
    if ($Deadline -eq [DateTime]::MinValue) {
        Throw-VerifierInfrastructure 'CDP attach requires a route deadline.'
    }
    $remainingMilliseconds = [int][Math]::Ceiling(
        ($Deadline - [DateTime]::UtcNow).TotalMilliseconds)
    if ($remainingMilliseconds -lt 1) {
        Throw-VerifierInfrastructure 'CDP attach deadline expired before the WebSocket handshake began.'
    }
    $socket = New-Object Net.WebSockets.ClientWebSocket
    $timeout = New-Object Threading.CancellationTokenSource
    $connectTask = $null
    $connected = $false
    $cleanupErrors = New-Object Collections.ArrayList
    $failure = $null
    try {
        $timeout.CancelAfter($remainingMilliseconds)
        [void]($connectTask = $socket.ConnectAsync($TargetUri, $timeout.Token))
        if (-not $connectTask.Wait($remainingMilliseconds)) {
            Throw-VerifierInfrastructure "CDP WebSocket handshake exceeded the route deadline of $remainingMilliseconds milliseconds."
        }
        [void]$connectTask.GetAwaiter().GetResult()
        if ($socket.State -ne [Net.WebSockets.WebSocketState]::Open) {
            Throw-VerifierInfrastructure ('CDP WebSocket handshake completed without an open socket; state=' +
                [string]$socket.State)
        }
        $connected = $true
    } catch {
        $failure = $_
    } finally {
        if (-not $connected) {
            try { $timeout.Cancel() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            if ($null -ne $connectTask -and -not $connectTask.IsCompleted) {
                try { [void]$connectTask.Wait(250) } catch { }
            }
            try { $socket.Abort() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            try { $socket.Dispose() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
        }
        try { $timeout.Dispose() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
    }
    if ($null -ne $failure) {
        $failureMessage = Get-VerifierErrorMessage $failure
        if ($cleanupErrors.Count -gt 0) {
            Throw-VerifierInfrastructure ('CDP attach failed and exact socket cleanup was not proven: ' +
                $failureMessage + '; cleanup: ' + ($cleanupErrors -join '; '))
        }
        if (Test-VerifierInfrastructureError $failure) { throw $failure }
        Throw-VerifierInfrastructure ('Could not attach to the owned browser target: ' + $failureMessage)
    }
    return $socket
}

function Get-VerifierBrowserRootRecordFromSnapshot($OwnerRoot, $Snapshot) {
    if ($null -eq $OwnerRoot -or $null -eq $Snapshot) {
        Throw-VerifierInfrastructure 'Browser cleanup cannot validate a missing root ownership snapshot.'
    }
    [void](Assert-VerifierRawProcessIdentityRecord $OwnerRoot `
        'browser cleanup root owner' `
        -ParentPropertyName 'ProcessParentProcessId' `
        -ParentStartPropertyName 'ProcessParentProcessStartTicks' `
        -CommandPropertyName 'ProcessCommandLine' -RequireParentStart)
    $rootPid = [int]$OwnerRoot.ProcessId
    if ($rootPid -le 0) {
        Throw-VerifierInfrastructure 'Browser cleanup cannot validate a root without a positive PID.'
    }
    $rootCandidates = New-Object Collections.ArrayList
    foreach ($candidate in @($Snapshot)) {
        [void](Assert-VerifierRawProcessRecord $candidate 'browser cleanup snapshot record')
        if ([int]$candidate.ProcessId -eq $rootPid) {
            [void]$rootCandidates.Add($candidate)
        }
    }
    if ($rootCandidates.Count -eq 0) {
        # A complete snapshot followed by a second-view absence still cannot
        # prove that a markerless child did not outlive the root.  Keep the
        # ownership ledger blocked until a root-anchored graph proof exists.
        $currentRoot = Get-VerifierCurrentProcessRecordById $rootPid
        if ($null -ne $currentRoot) {
            Throw-VerifierInfrastructure "Recorded browser root PID $rootPid was absent from the complete snapshot but remains in the current process view; cleanup is retained."
        }
        Throw-VerifierInfrastructure "Recorded browser root PID $rootPid disappeared before complete descendant ownership cleanup; profile and port ownership are retained."
    }
    if ($rootCandidates.Count -ne 1) {
        Throw-VerifierInfrastructure "Browser root PID $rootPid was ambiguous in the complete process snapshot."
    }
    $rootCandidate = $rootCandidates[0]
    [void](Assert-VerifierRawProcessRecord $rootCandidate `
        "browser cleanup root snapshot record for PID $rootPid" `
        -RequirePositiveIdentity -RequireCommandLine)
    if (-not (Test-VerifierProcessIdentity $OwnerRoot $Snapshot)) {
        $currentMismatchedRoot = Get-VerifierCurrentProcessRecordById $rootPid
        if ($null -ne $currentMismatchedRoot) {
            Throw-VerifierInfrastructure "Refusing to stop browser PID ${rootPid}: recorded root identity no longer matches."
        }
        Throw-VerifierInfrastructure "Browser root PID $rootPid disappeared before complete descendant ownership cleanup could be revalidated; profile and port ownership are retained."
    }
    if (-not $OwnerRoot.PSObject.Properties['ProcessParentProcessId'] -or
            [int]$OwnerRoot.ProcessParentProcessId -le 0 -or
            -not $rootCandidate.PSObject.Properties['ParentProcessId'] -or
            [int]$rootCandidate.ParentProcessId -ne [int]$OwnerRoot.ProcessParentProcessId) {
        Throw-VerifierInfrastructure "Recorded browser root PID $rootPid did not retain its exact launch parent identity."
    }
    if (-not $OwnerRoot.PSObject.Properties['ProcessParentProcessStartTicks'] -or
            [long]$OwnerRoot.ProcessParentProcessStartTicks -le 0) {
        Throw-VerifierInfrastructure "Recorded browser root PID $rootPid did not retain its exact launch parent start identity."
    }
    if (-not $rootCandidate.PSObject.Properties['CommandLine'] -or
            [String]::IsNullOrWhiteSpace([string]$rootCandidate.CommandLine)) {
        Throw-VerifierInfrastructure "Browser root PID $rootPid had no readable command line in the complete snapshot."
    }
    return [pscustomobject]@{
        ProcessId = $rootPid
        ProcessStartTicks = [long]$OwnerRoot.ProcessStartTicks
        CommandLine = [string]$rootCandidate.CommandLine
        ParentProcessId = [int]$rootCandidate.ParentProcessId
        ParentProcessStartTicks = [long]$OwnerRoot.ProcessParentProcessStartTicks
        Name = if ($rootCandidate.PSObject.Properties['Name']) {
            [string]$rootCandidate.Name
        } else { '' }
        ExecutablePath = if ($rootCandidate.PSObject.Properties['ExecutablePath']) {
            [string]$rootCandidate.ExecutablePath
        } else { '' }
        VerifierDepth = 0
    }
}

function Assert-VerifierNoResidualBrowserDescendants($OwnerRoot, $RootRecord,
        $KnownDescendantRecords, $Snapshot) {
    if ($null -eq $OwnerRoot -or $null -eq $RootRecord -or $null -eq $Snapshot) {
        Throw-VerifierInfrastructure 'Post-stop browser ownership proof omitted the root, known graph, or complete snapshot.'
    }
    [void](Assert-VerifierRawProcessIdentityRecord $OwnerRoot `
        'post-stop browser owner root' `
        -ParentPropertyName 'ProcessParentProcessId' `
        -ParentStartPropertyName 'ProcessParentProcessStartTicks' `
        -CommandPropertyName 'ProcessCommandLine' -RequireParentStart)
    [void](Assert-VerifierRawProcessIdentityRecord $RootRecord `
        'post-stop browser root record' -RequireParentStart)
    $rootPid = [int]$RootRecord.ProcessId
    if ($rootPid -le 0) {
        Throw-VerifierInfrastructure 'Post-stop browser ownership proof omitted a positive root PID.'
    }
    $ownedPidSet = @{}
    $ownedPidSet[$rootPid] = $true
    # Every discovered descendant must independently prove current absence.
    # A changed/reused PID is not equivalent to absence and therefore blocks
    # release even if the replacement no longer carries verifier markers.
    foreach ($knownDescendant in @($KnownDescendantRecords)) {
        [void](Assert-VerifierRawProcessIdentityRecord $knownDescendant `
            'retained browser descendant record' -RequireParentStart)
        $knownPid = [int]$knownDescendant.ProcessId
        if ($knownPid -le 0 -or [long]$knownDescendant.ProcessStartTicks -le 0) {
            Throw-VerifierInfrastructure 'The retained browser descendant graph contained an invalid process identity.'
        }
        $ownedPidSet[$knownPid] = $true
        $currentKnown = Get-VerifierCurrentProcessRecordById $knownPid
        if ($null -ne $currentKnown) {
            if (-not (Test-VerifierCurrentProcessRecordMatches $knownDescendant $currentKnown)) {
                Throw-VerifierInfrastructure "Browser descendant PID $knownPid was replaced or changed before post-stop absence proof."
            }
            Throw-VerifierInfrastructure "Browser descendant PID $knownPid remained alive after the browser root stopped."
        }
    }

    $currentRoot = Get-VerifierCurrentProcessRecordById $rootPid
    if ($null -ne $currentRoot) {
        if (-not (Test-VerifierCurrentProcessRecordMatches $RootRecord $currentRoot)) {
            Throw-VerifierInfrastructure "Browser root PID $rootPid was replaced or remained present after exact termination."
        }
        Throw-VerifierInfrastructure "Browser root PID $rootPid remained alive after exact termination."
    }

    # The complete post-stop snapshot is diagnostic, while exact child queries
    # below are authoritative for every PID that could have been owned. Any
    # record under a known parent is a late/unknown descendant, regardless of
    # executable name or marker content.
    foreach ($candidate in @($Snapshot)) {
        [void](Assert-VerifierRawProcessRecord $candidate 'post-stop browser snapshot record')
        $candidateParent = [int]$candidate.ParentProcessId
        if (-not $ownedPidSet.ContainsKey($candidateParent)) { continue }
        [void](Assert-VerifierRawProcessRecord $candidate `
            "residual browser descendant under owned PID $candidateParent" `
            -RequirePositiveIdentity -RequireCommandLine)
        Throw-VerifierInfrastructure "A late or unknown browser descendant PID $($candidate.ProcessId) remained under owned PID $candidateParent after root termination."
    }

    # Re-query each previously owned parent after root termination. This closes
    # the window where a same-executable markerless helper appears after the
    # last broad snapshot but before the root is stopped.
    foreach ($ownedParentPid in @($ownedPidSet.Keys)) {
        $lateChildren = @()
        try {
            $lateChildren = @(Get-VerifierProcessRecordsByParentWithFallback $ownedParentPid `
                'exact post-stop browser descendant')
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not re-query exact children of owned PID " +
                [string]$ownedParentPid + ': ' + (Get-VerifierErrorMessage $_))
        }
        foreach ($lateChild in $lateChildren) {
            [void](Assert-VerifierRawProcessRecord $lateChild `
                "exact post-stop child under owned PID $ownedParentPid" `
                -RequirePositiveIdentity -RequireCommandLine)
            $lateChildPid = [int]$lateChild.ProcessId
            if ([int]$lateChild.ParentProcessId -ne [int]$ownedParentPid) {
                Throw-VerifierInfrastructure "Exact post-stop child inspection under owned PID $ownedParentPid returned an invalid parent identity."
            }
            Throw-VerifierInfrastructure "A late or unknown browser descendant PID $lateChildPid was found by exact post-stop parent inspection."
        }
    }
}

function Stop-VerifierBrowserProcessTreeToFixedPointCore($Context, $OwnerRoot,
        [int]$DrainMilliseconds, $DrainScope) {
    Assert-VerifierBrowserDrainScope $DrainScope 'fixed-point browser drain scope'
    [void](Assert-VerifierContextPreflight $Context 'browser process-tree cleanup context' `
        -RequiredProperties @('RunId', 'RepositoryIdentity', 'RunRoot'))
    if ($null -eq $Context -or $null -eq $OwnerRoot -or
            $DrainMilliseconds -lt 1) {
        Throw-VerifierInfrastructure 'Browser cleanup requires a bounded root-anchored process drain.'
    }
    [void](Assert-VerifierRawProcessIdentityRecord $OwnerRoot `
        'browser drain root owner' `
        -ParentPropertyName 'ProcessParentProcessId' `
        -ParentStartPropertyName 'ProcessParentProcessStartTicks' `
        -CommandPropertyName 'ProcessCommandLine' -RequireParentStart)
    $drainDeadline = [DateTime]::UtcNow.AddMilliseconds($DrainMilliseconds)
    $knownByPid = @{}
    $knownDescendants = New-Object Collections.ArrayList
    $stableEmptySnapshots = 0
    $drainIteration = 0
    $rootRecord = $null
    while ($true) {
        if ([DateTime]::UtcNow -ge $drainDeadline) {
            Throw-VerifierInfrastructure 'Browser descendant drain did not reach a bounded fixed point while the root remained alive.'
        }
        $drainIteration++
        $snapshot = @(Get-VerifierBrowserOwnershipSnapshot `
            $OwnerRoot.BrowserPath $OwnerRoot.Profile $OwnerRoot.RunId `
            $OwnerRoot.RepositoryIdentity $OwnerRoot.CdpPort)
        $rootRecord = Get-VerifierBrowserRootRecordFromSnapshot $OwnerRoot $snapshot
        [void](Get-VerifierCurrentOwnedProcess $OwnerRoot $rootRecord -Root)
        $children = @(Get-VerifierDescendantProcessRecords $OwnerRoot $snapshot $DrainScope)

        foreach ($child in $children) {
            [void](Assert-VerifierRawProcessIdentityRecord $child `
                'browser drain descendant record' -RequireParentStart)
            $childPid = [int]$child.ProcessId
            if ($childPid -le 0 -or [long]$child.ProcessStartTicks -le 0) {
                Throw-VerifierInfrastructure 'Browser descendant drain discovered an invalid child identity.'
            }
            if ($knownByPid.ContainsKey($childPid)) {
                $oldChild = $knownByPid[$childPid]
                if ([long]$oldChild.ProcessStartTicks -ne [long]$child.ProcessStartTicks -or
                        -not (Test-VerifierCurrentProcessRecordMatches $oldChild $child)) {
                    Throw-VerifierInfrastructure "Browser descendant PID $childPid changed identity during the bounded drain."
                }
            } else {
                $knownByPid[$childPid] = $child
                [void]$knownDescendants.Add($child)
            }
        }

        if ($children.Count -gt 0) {
            $stableEmptySnapshots = 0
            foreach ($childToStop in @($children | Sort-Object `
                    @{Expression={ [int]$_.VerifierDepth }; Descending=$true},
                    @{Expression={ [int]$_.ProcessId }; Descending=$true})) {
                $childPid = [int]$childToStop.ProcessId
                # First consult only the exact, scope-bound retained handle for
                # the child that was fully proved during discovery. If that
                # handle has naturally exited, prove two independent current
                # PID/WMI absences and skip Stop-Process entirely.
                $knownChild = $knownByPid[$childPid]
                $naturalExit = Get-VerifierBrowserDrainNaturalExitResult `
                    $DrainScope $knownChild
                if ($null -ne $naturalExit) { continue }

                # The discovery object is never used as the termination target.
                # Re-query the exact current object and all identity fields
                # immediately before stopping it.
                $verifiedChild = $null
                try {
                    $verifiedChild = Get-VerifierCurrentOwnedProcess $OwnerRoot $childToStop
                } catch {
                    # Only the typed missing-process race for this exact PID
                    # may consult the same attestation a second time. Any
                    # identity, ownership, or inspection error stays fatal.
                    $missingPid = Get-VerifierMissingProcessId $_
                    if ($missingPid -eq $childPid) {
                        $naturalExit = Get-VerifierBrowserDrainNaturalExitResult `
                            $DrainScope $knownChild
                        if ($null -ne $naturalExit) { continue }
                    }
                    throw
                }
                [void](Add-VerifierBrowserDrainHandle $DrainScope $verifiedChild.Process `
                    'fixed-point current child process handle')
                [void](Stop-VerifierVerifiedProcessExactly $verifiedChild.Process `
                    ([long]$verifiedChild.Record.ProcessStartTicks) 5000 $verifiedChild.Record)
            }
            continue
        }

        # Test-only synchronization used by the WMI-backed late-helper
        # canary. It is inert unless a canary explicitly supplies both paths;
        # the signal is written only after a complete empty graph has been
        # captured, so the next iteration must discover the late child.
        if ($drainIteration -eq 1 -and
                $Context.PSObject.Properties['TestHooks'] -and
                $Context.TestHooks.PSObject.Properties['BrowserDrainAfterInitialGraphSignalPath'] -and
                -not [String]::IsNullOrWhiteSpace([string]$Context.TestHooks.BrowserDrainAfterInitialGraphSignalPath)) {
            $signalPath = Get-VerifierFullPath `
                ([string]$Context.TestHooks.BrowserDrainAfterInitialGraphSignalPath)
            Assert-VerifierNoReparseAncestors $Context.RunRoot
            [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $signalPath)
            [IO.File]::WriteAllText($signalPath, 'initial-empty-browser-graph',
                [Text.UTF8Encoding]::new($false))
            if (-not (Test-Path -LiteralPath $signalPath -PathType Leaf)) {
                Throw-VerifierInfrastructure 'Browser drain synchronization signal was not persisted.'
            }
            $Context.TestHooks.BrowserDrainAfterInitialGraphSignalWritten = $true
            if ($Context.TestHooks.PSObject.Properties['BrowserDrainAfterInitialGraphReadyPath'] -and
                    -not [String]::IsNullOrWhiteSpace([string]$Context.TestHooks.BrowserDrainAfterInitialGraphReadyPath)) {
                $readyPath = Get-VerifierFullPath `
                    ([string]$Context.TestHooks.BrowserDrainAfterInitialGraphReadyPath)
                [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $readyPath)
                $readyDeadline = [DateTime]::UtcNow.AddSeconds(5)
                while (-not (Test-Path -LiteralPath $readyPath -PathType Leaf)) {
                    if ([DateTime]::UtcNow -ge $readyDeadline) {
                        Throw-VerifierInfrastructure 'Late-helper canary did not report its post-snapshot launch within the bound.'
                    }
                    Start-Sleep -Milliseconds 50
                }
            }
        }

        # Require several independent empty observations while the root is
        # still alive. The final root revalidation and exact post-stop graph
        # check below handle a child arriving in the stop boundary itself.
        $stableEmptySnapshots++
        if ($stableEmptySnapshots -lt 3) {
            Start-Sleep -Milliseconds 100
            continue
        }
        $rootToStop = Get-VerifierCurrentOwnedProcess $OwnerRoot $rootRecord -Root
        [void](Add-VerifierBrowserDrainHandle $DrainScope $rootToStop.Process `
            'fixed-point browser root termination handle')
        [void](Stop-VerifierVerifiedProcessExactly $rootToStop.Process `
            ([long]$rootToStop.Record.ProcessStartTicks) 5000 $rootToStop.Record)
        $postStopSnapshot = @(Get-VerifierBrowserOwnershipSnapshot `
            $OwnerRoot.BrowserPath $OwnerRoot.Profile $OwnerRoot.RunId `
            $OwnerRoot.RepositoryIdentity $OwnerRoot.CdpPort)
        Assert-VerifierNoResidualBrowserDescendants $OwnerRoot $rootRecord `
            $knownDescendants $postStopSnapshot
        return [pscustomobject]@{
            RootRecord = $rootRecord
            KnownDescendants = @($knownDescendants)
            ProcessTerminationProven = $true
            DrainIterations = $drainIteration
        }
    }
}

function Dispose-VerifierBrowserDrainScope($DrainScope) {
    $registration = Get-VerifierBrowserDrainScopeRegistration $DrainScope
    $disposeErrors = New-Object Collections.ArrayList
    try {
        Assert-VerifierBrowserDrainScope $DrainScope 'browser drain scope disposal'
    } catch {
        [void]$disposeErrors.Add((Get-VerifierErrorMessage $_))
    }
    foreach ($Process in @($registration.Item2)) {
        try {
            $Process.Dispose()
        } catch {
            [void]$disposeErrors.Add((Get-VerifierErrorMessage $_))
        }
    }
    try { $DrainScope.Disposed = $true } catch {
        [void]$disposeErrors.Add((Get-VerifierErrorMessage $_))
    }
    for ($index = $script:VerifierBrowserDrainScopes.Count - 1; $index -ge 0; $index--) {
        if ([object]::ReferenceEquals($script:VerifierBrowserDrainScopes[$index], $registration)) {
            $script:VerifierBrowserDrainScopes.RemoveAt($index)
            break
        }
    }
    return @($disposeErrors)
}

function Stop-VerifierBrowserProcessTreeToFixedPoint($Context, $OwnerRoot,
        [int]$DrainMilliseconds = 15000) {
    # The optional scope is deliberately created here and nowhere in the
    # listener/inspection callers. Every newly retained child handle is
    # disposed on both success and failure before this boundary returns.
    $drainScope = New-VerifierBrowserDrainScope
    $result = $null
    $failure = $null
    try {
        $result = Stop-VerifierBrowserProcessTreeToFixedPointCore $Context `
            $OwnerRoot $DrainMilliseconds $drainScope
    } catch {
        $failure = $_
    }
    $disposeErrors = @()
    try {
        $disposeErrors = @(Dispose-VerifierBrowserDrainScope $drainScope)
    } catch {
        $disposeErrors = @((Get-VerifierErrorMessage $_))
    }
    if ($disposeErrors.Count -gt 0) {
        $detail = 'browser drain retained-handle disposal was not proven: ' +
            ($disposeErrors -join '; ')
        if ($null -ne $failure) {
            Throw-VerifierInfrastructure ((Get-VerifierErrorMessage $failure) + '; ' + $detail)
        }
        Throw-VerifierInfrastructure $detail
    }
    if ($null -ne $failure) { throw $failure }
    return $result
}

function Get-VerifierBrowserCloseAttemptKey($SessionRecord) {
    # A copied session can change route/run labels. The attempt belongs to
    # the immutable operating-system process instance, so labels cannot
    # authorize retry or force-stop after an uncertain command.
    return ([string]$SessionRecord.ProcessId + '/' + [string]$SessionRecord.ProcessStartTicks)
}

function Assert-VerifierBrowserCloseNotAttempted($SessionRecord) {
    $attemptKey = Get-VerifierBrowserCloseAttemptKey $SessionRecord
    if ($script:VerifierBrowserCloseAttempts.ContainsKey($attemptKey) -or
            ($SessionRecord.Runtime.PSObject.Properties['GracefulCloseAttempted'] -and
                (-not (Test-VerifierStrictBooleanValue $SessionRecord.Runtime.GracefulCloseAttempted) -or
                    $SessionRecord.Runtime.GracefulCloseAttempted))) {
        Throw-VerifierInfrastructure 'An earlier browser-close attempt was not proven; exact-stop fallback is prohibited.'
    }
}

function Assert-VerifierBrowserShutdownOwner($SessionRecord, $OwnerRoot) {
    if ($null -eq $OwnerRoot -or $OwnerRoot -is [array]) {
        Throw-VerifierInfrastructure 'Natural browser shutdown omitted the exact session owner.'
    }
    foreach ($field in @('ProcessId', 'ProcessStartTicks', 'ProcessParentProcessId',
            'ProcessParentProcessStartTicks', 'CdpPort')) {
        $maximum = if ($field -like '*Ticks') { [long]::MaxValue } else { [int]::MaxValue }
        if (-not $OwnerRoot.PSObject.Properties[$field] -or
                -not (Test-VerifierStrictIntegralValue $OwnerRoot.$field 1 $maximum) -or
                $OwnerRoot.$field -ne $SessionRecord.$field) {
            Throw-VerifierInfrastructure "Natural browser shutdown owner mismatched session $field."
        }
    }
    foreach ($field in @('ProcessCommandLine', 'BrowserPath', 'Profile', 'RunId',
            'RepositoryIdentity')) {
        if (-not $OwnerRoot.PSObject.Properties[$field] -or
                -not (Test-VerifierStrictStringValue $OwnerRoot.$field) -or
                [String]::IsNullOrWhiteSpace($OwnerRoot.$field) -or
                -not [String]::Equals($OwnerRoot.$field, $SessionRecord.$field,
                    [StringComparison]::Ordinal)) {
            Throw-VerifierInfrastructure "Natural browser shutdown owner mismatched session $field."
        }
    }
}

function Assert-VerifierBrowserShutdownDeadline($Budget, [long]$BudgetTicks) {
    if ($Budget.ElapsedTicks -ge $BudgetTicks) {
        Throw-VerifierInfrastructure 'Natural browser shutdown exceeded its complete monotonic cleanup deadline.'
    }
}

function Get-VerifierBrowserShutdownEndpoint($VersionDocument, $Port) {
    if (-not (Test-VerifierStrictIntegralValue $Port 1 65535) -or
            $null -eq $VersionDocument -or $VersionDocument -is [array] -or
            -not $VersionDocument.PSObject.Properties['webSocketDebuggerUrl'] -or
            -not (Test-VerifierStrictStringValue $VersionDocument.webSocketDebuggerUrl)) {
        Throw-VerifierInfrastructure 'Browser shutdown omitted its exact browser endpoint or leased port.'
    }
    # /json/list supplies PAGE targets. Browser.close uses the browser endpoint
    # from /json/version; never accept a page target, DNS name, alternate port,
    # credentials, query, fragment, or encoded/ambiguous path here.
    $endpoint = [string]$VersionDocument.webSocketDebuggerUrl
    $prefix = 'ws://127.0.0.1:' + [string]$Port + '/devtools/browser/'
    if (-not $endpoint.StartsWith($prefix, [StringComparison]::Ordinal)) {
        Throw-VerifierInfrastructure 'Browser shutdown endpoint did not belong to the exact loopback browser transport.'
    }
    $identifier = $endpoint.Substring($prefix.Length)
    $parsedIdentifier = [Guid]::Empty
    if (-not [Guid]::TryParseExact($identifier, 'D', [ref]$parsedIdentifier) -or
            $parsedIdentifier -eq [Guid]::Empty -or $identifier.Length -ne 36) {
        Throw-VerifierInfrastructure 'Browser shutdown endpoint did not carry one exact browser target identifier.'
    }
    return $endpoint
}

function Send-VerifierBrowserCloseAndReadAcknowledgement($Socket, $Budget,
        [long]$BudgetTicks) {
    Assert-VerifierBrowserShutdownDeadline $Budget $BudgetTicks
    if ($null -eq $Socket -or $Socket.GetType() -ne [Net.WebSockets.ClientWebSocket] -or
            $Socket.State -ne [Net.WebSockets.WebSocketState]::Open) {
        Throw-VerifierInfrastructure 'Browser shutdown requires its newly connected, open browser WebSocket.'
    }
    # This socket is private to cleanup and has no preceding CDP requests.
    $requestBytes = [Text.Encoding]::UTF8.GetBytes('{"id":1,"method":"Browser.close"}')
    $remainingMs = [int][Math]::Floor(
        (($BudgetTicks - $Budget.ElapsedTicks) * 1000.0) / [Diagnostics.Stopwatch]::Frequency)
    if ($remainingMs -le 0) {
        Throw-VerifierInfrastructure 'Browser shutdown transport had no remaining cleanup budget.'
    }
    $cancellation = New-Object Threading.CancellationTokenSource ([Math]::Min(2000, $remainingMs))
    try {
        [void]$Socket.SendAsync((New-Object ArraySegment[byte] -ArgumentList (,$requestBytes)),
            [Net.WebSockets.WebSocketMessageType]::Text, $true,
            $cancellation.Token).GetAwaiter().GetResult()
        Assert-VerifierBrowserShutdownDeadline $Budget $BudgetTicks
        while ($true) {
            Assert-VerifierBrowserShutdownDeadline $Budget $BudgetTicks
            $stream = New-Object IO.MemoryStream
            try {
                do {
                    $buffer = New-Object byte[] 65536
                    $received = $Socket.ReceiveAsync(
                        (New-Object ArraySegment[byte] -ArgumentList (,$buffer)),
                        $cancellation.Token).GetAwaiter().GetResult()
                    Assert-VerifierBrowserShutdownDeadline $Budget $BudgetTicks
                    if ($received.MessageType -ne [Net.WebSockets.WebSocketMessageType]::Text -or
                            $received.Count -lt 0 -or $stream.Length + $received.Count -gt 1048576) {
                        Throw-VerifierInfrastructure 'Browser shutdown acknowledgement was closed, non-text, or oversized.'
                    }
                    $stream.Write($buffer, 0, $received.Count)
                } while (-not $received.EndOfMessage)
                $json = ([Text.UTF8Encoding]::new($false, $true)).GetString($stream.ToArray())
                $response = ConvertFrom-Json -InputObject $json -ErrorAction Stop
            } finally {
                $stream.Dispose()
            }
            Assert-VerifierBrowserShutdownDeadline $Budget $BudgetTicks
            if ($null -eq $response -or $response -is [array]) {
                Throw-VerifierInfrastructure 'Browser shutdown acknowledgement was not an exact CDP object.'
            }
            if (-not $response.PSObject.Properties['id']) {
                if (-not $response.PSObject.Properties['method'] -or
                        -not (Test-VerifierStrictStringValue $response.method) -or
                        [String]::IsNullOrWhiteSpace($response.method)) {
                    Throw-VerifierInfrastructure 'Browser shutdown received an unrecognized CDP message.'
                }
                continue
            }
            # ConvertFrom-Json can collapse duplicate keys. The close reply
            # has one deliberately tiny wire schema, so also match that raw
            # schema (either field order) before trusting the parsed object.
            $space = '[ \t\r\n]*'
            $idField = '"id"' + $space + ':' + $space + '1'
            $resultField = '"result"' + $space + ':' + $space + '\{' + $space + '\}'
            $replyPattern = '\A' + $space + '\{' + $space + '(?:' +
                $idField + $space + ',' + $space + $resultField + '|' +
                $resultField + $space + ',' + $space + $idField + ')' +
                $space + '\}' + $space + '\z'
            if (-not [regex]::IsMatch($json, $replyPattern) -or
                    -not (Test-VerifierStrictIntegralValue $response.id 1 1) -or
                    $response.PSObject.Properties['error'] -or
                    -not $response.PSObject.Properties['result'] -or
                    $null -eq $response.result -or
                    $response.result.GetType() -ne [Management.Automation.PSCustomObject] -or
                    @($response.result.PSObject.Properties).Count -ne 0 -or
                    @($response.PSObject.Properties).Count -ne 2) {
                Throw-VerifierInfrastructure 'Browser shutdown did not acknowledge the exact successful close command.'
            }
            Assert-VerifierBrowserShutdownDeadline $Budget $BudgetTicks
            return
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Browser shutdown transport or acknowledgement failed: ' +
            (Get-VerifierErrorMessage $_))
    } finally {
        $cancellation.Dispose()
    }
}

function Close-VerifierBrowserSessionNaturally($Context, $SessionRecord, $OwnerRoot) {
    # This is an additional positive natural-shutdown lane, not permission to
    # adopt a missing root or terminate a child after its ancestry has gone.
    # The legacy descendant-first exact-stop path remains a separate mode.
    $budget = [Diagnostics.Stopwatch]::StartNew()
    $budgetTicks = [long][Math]::Ceiling(
        ([double][Diagnostics.Stopwatch]::Frequency * 15000) / 1000.0)
    $scope = New-VerifierBrowserDrainScope
    $browserSocket = $null
    $result = $null
    $failure = $null
    $disposeErrors = New-Object Collections.ArrayList
    try {
        Assert-VerifierDurableBrowserSession $SessionRecord $Context 'natural browser shutdown session'
        Assert-VerifierBrowserShutdownOwner $SessionRecord $OwnerRoot
        $attemptKey = Get-VerifierBrowserCloseAttemptKey $SessionRecord
        Assert-VerifierBrowserCloseNotAttempted $SessionRecord
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        [void](Get-VerifierPortLeaseBoundOwnershipProof $Context $SessionRecord.Lease `
            $SessionRecord.ProcessId $SessionRecord.ProcessStartTicks $SessionRecord $null)
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        $version = Invoke-RestMethod -Uri (
            'http://127.0.0.1:' + [string]$SessionRecord.CdpPort + '/json/version') -TimeoutSec 2
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        $endpoint = Get-VerifierBrowserShutdownEndpoint $version $SessionRecord.CdpPort
        # Establish the browser-level connection while the current listener
        # still belongs to this run. The socket never leaves this function.
        [void](Get-VerifierPortLeaseBoundOwnershipProof $Context $SessionRecord.Lease `
            $SessionRecord.ProcessId $SessionRecord.ProcessStartTicks $SessionRecord $null)
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        $remainingMs = [int][Math]::Floor(
            (($budgetTicks - $budget.ElapsedTicks) * 1000.0) / [Diagnostics.Stopwatch]::Frequency)
        if ($remainingMs -le 0) {
            Throw-VerifierInfrastructure 'Browser shutdown exhausted its budget before connecting.'
        }
        $browserSocket = Connect-VerifierCdpSocket $endpoint (
            [DateTime]::UtcNow.AddMilliseconds([Math]::Min(2000, $remainingMs)))
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        $snapshot = @(Get-VerifierBrowserOwnershipSnapshot $OwnerRoot.BrowserPath `
            $OwnerRoot.Profile $OwnerRoot.RunId $OwnerRoot.RepositoryIdentity $OwnerRoot.CdpPort)
        $rootRecord = Get-VerifierBrowserRootRecordFromSnapshot $OwnerRoot $snapshot
        $verifiedRoot = Get-VerifierCurrentOwnedProcess $OwnerRoot $rootRecord -Root
        $rootRecord = $verifiedRoot.Record
        $rootRecord | Add-Member -NotePropertyName Process -NotePropertyValue $verifiedRoot.Process -Force
        $rootAttestation = New-VerifierBrowserDrainAttestation $scope $rootRecord $verifiedRoot.Process
        $rootRecord | Add-Member -NotePropertyName VerifierDrainAttestation `
            -NotePropertyValue $rootAttestation -Force
        $children = New-Object Collections.ArrayList
        $childByPid = @{}
        $observedShutdownGraph = $false
        # Require a second complete graph observation with no new identities
        # before sending. Retain each original handle even if that child exits
        # between observations. A reused PID or changed immutable identity is
        # never adopted into the earlier proof.
        do {
            $newIdentities = 0
            foreach ($child in @(Get-VerifierDescendantProcessRecords $OwnerRoot $snapshot $scope)) {
                $childPid = [int]$child.ProcessId
                if ($childByPid.ContainsKey($childPid)) {
                    $prior = $childByPid[$childPid]
                    foreach ($field in @('ProcessId', 'ProcessStartTicks', 'ParentProcessId',
                            'ParentProcessStartTicks', 'Name', 'ExecutablePath', 'CommandLine')) {
                        if (-not [object]::Equals($prior.$field, $child.$field)) {
                            Throw-VerifierInfrastructure "Browser child PID $childPid changed $field between complete shutdown observations."
                        }
                    }
                } else {
                    $childByPid[$childPid] = $child
                    [void]$children.Add($child)
                    $newIdentities++
                }
            }
            Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
            if (-not $observedShutdownGraph) {
                $observedShutdownGraph = $true
                $newIdentities++
            }
            if ($newIdentities -gt 0) {
                $snapshot = @(Get-VerifierBrowserOwnershipSnapshot $OwnerRoot.BrowserPath `
                    $OwnerRoot.Profile $OwnerRoot.RunId $OwnerRoot.RepositoryIdentity $OwnerRoot.CdpPort)
                [void](Get-VerifierBrowserRootRecordFromSnapshot $OwnerRoot $snapshot)
                Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
            }
        } while ($newIdentities -gt 0)
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        # Revalidate the root/listener immediately before the browser command.
        # A child already attested above may exit naturally; a missing child
        # before that full attestation has already failed closed.
        [void](Get-VerifierPortLeaseBoundOwnershipProof $Context $SessionRecord.Lease `
            $SessionRecord.ProcessId $SessionRecord.ProcessStartTicks $SessionRecord $null)
        $currentRoot = Get-VerifierCurrentOwnedProcess $OwnerRoot $rootRecord -Root
        [void](Add-VerifierBrowserDrainHandle $scope $currentRoot.Process)
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        # Record the attempt BEFORE sending: even a partial send or a dropped
        # acknowledgement can never select the force-stop path on retry.
        $script:VerifierBrowserCloseAttempts[$attemptKey] = $true
        $SessionRecord.Runtime | Add-Member -NotePropertyName GracefulCloseAttempted `
            -NotePropertyValue $true -Force
        Send-VerifierBrowserCloseAndReadAcknowledgement $browserSocket $budget $budgetTicks
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        $unproven = New-Object Collections.ArrayList
        [void]$unproven.Add($rootRecord)
        foreach ($child in $children) { [void]$unproven.Add($child) }
        while ($unproven.Count -gt 0) {
            for ($index = $unproven.Count - 1; $index -ge 0; $index--) {
                Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
                $natural = Get-VerifierBrowserDrainNaturalExitResult $scope $unproven[$index]
                Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
                if ($null -ne $natural) { $unproven.RemoveAt($index) }
            }
            if ($unproven.Count -gt 0) {
                Start-Sleep -Milliseconds 25
                Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
            }
        }
        $postSnapshot = @(Get-VerifierBrowserOwnershipSnapshot $OwnerRoot.BrowserPath `
            $OwnerRoot.Profile $OwnerRoot.RunId $OwnerRoot.RepositoryIdentity $OwnerRoot.CdpPort)
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        Assert-VerifierNoResidualBrowserDescendants $OwnerRoot $rootRecord $children $postSnapshot
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        $result = [pscustomobject]@{
            ProcessTerminationProven = $true
            NaturalShutdownProven = $true
            AttestedDescendantCount = $children.Count
        }
    } catch {
        $failure = $_
    } finally {
        if ($null -ne $browserSocket) {
            try { $browserSocket.Abort() } catch { [void]$disposeErrors.Add((Get-VerifierErrorMessage $_)) }
            try { $browserSocket.Dispose() } catch { [void]$disposeErrors.Add((Get-VerifierErrorMessage $_)) }
        }
        try {
            foreach ($errorText in @(Dispose-VerifierBrowserDrainScope $scope)) {
                [void]$disposeErrors.Add($errorText)
            }
        } catch { [void]$disposeErrors.Add((Get-VerifierErrorMessage $_)) }
    }
    if ($disposeErrors.Count -gt 0) {
        Throw-VerifierInfrastructure ('Natural browser shutdown resource disposal was unproven: ' +
            ($disposeErrors -join '; ') + '; ' + (Get-VerifierErrorMessage $failure))
    }
    if ($null -ne $failure) {
        if (Test-VerifierInfrastructureError $failure) { throw $failure }
        Throw-VerifierInfrastructure ('Natural browser shutdown failed: ' + (Get-VerifierErrorMessage $failure))
    }
    Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
    return $result
}

function Complete-VerifierBrowserSession($Context, $SessionRecord) {
    # Durable validation is the cleanup entry boundary.  This must run before
    # even reading CleanupResult: a malformed terminal-looking record is not a
    # no-op and cannot authorize skipping process, profile, or lease proof.
    Assert-VerifierDurableManifestContext $Context
    Assert-VerifierDurableBrowserSession $SessionRecord $Context `
        'browser cleanup session'
    $cleanupErrors = New-Object Collections.ArrayList
    $processTerminationProven = $false
    # Establish the exact filesystem/session owner before any process
    # snapshot, descendant walk, or termination attempt.  This is deliberately
    # initialized on every cleanup entry path: a partially-created session may
    # still own a profile/claim, and an unset/colliding owner variable must
    # never turn a matching PID into an authorized cleanup target.
    $sessionRoot = $null
    $ownerRoot = $null
    try {
        if (-not $SessionRecord.PSObject.Properties['CleanupResult']) {
            Throw-VerifierInfrastructure 'Browser cleanup session omitted its cleanup state.'
        }
        if ([string]$SessionRecord.CleanupResult -eq 'complete') { return }
        if (-not $SessionRecord.PSObject.Properties['ProcessId']) {
            Throw-VerifierInfrastructure 'Browser cleanup session omitted its process identity.'
        }
        if ([int]$SessionRecord.ProcessId -le 0) {
            # A browser session without a positive root identity has no
            # authoritative ancestry anchor.  Absence of that root is not a
            # termination proof: a markerless or differently named helper may
            # still reference the profile.  Keep the claim/profile/evidence
            # until a real root has been revalidated and its complete graph
            # has been checked.
            Throw-VerifierInfrastructure 'Browser cleanup cannot prove a missing recorded root; profile and port ownership are retained.'
        }
        $processTerminationProven = $false
        if ($null -eq $Context -or [String]::IsNullOrWhiteSpace([string]$Context.RunRoot)) {
            Throw-VerifierInfrastructure 'Browser cleanup requires an exact verifier run root.'
        }
        $sessionRoot = Get-VerifierFullPath ([string]$Context.RunRoot)
        Assert-VerifierNoReparseAncestors $sessionRoot
        if (-not $SessionRecord.PSObject.Properties['Profile'] -or
                [String]::IsNullOrWhiteSpace([string]$SessionRecord.Profile)) {
            Throw-VerifierInfrastructure 'Browser cleanup requires an exact owned profile path.'
        }
        $sessionProfile = Get-VerifierFullPath ([string]$SessionRecord.Profile)
        # The profile may still contain open Edge files here. Validate the
        # owned namespace and physical root before any process query, but defer
        # recursive enumeration until the exact browser tree is drained.
        [void](Assert-VerifierPhysicalOwnedPath $sessionRoot $sessionProfile)
        $sessionBrowserPath = if ($SessionRecord.PSObject.Properties['BrowserPath']) {
            [string]$SessionRecord.BrowserPath
        } else { '' }
        if ([String]::IsNullOrWhiteSpace($sessionBrowserPath)) {
            Throw-VerifierInfrastructure 'Browser cleanup requires a resolved BrowserPath executable identity.'
        }
        $sessionBrowserPath = Get-VerifierFullPath $sessionBrowserPath
        if (-not (Test-Path -LiteralPath $sessionBrowserPath -PathType Leaf -ErrorAction Stop)) {
            Throw-VerifierInfrastructure "Browser cleanup could not find its resolved BrowserPath executable: $sessionBrowserPath"
        }
        # Keep the durable session record aligned with the canonical owner
        # values used by the cleanup proof and any retained failure manifest.
        $SessionRecord.Profile = $sessionProfile
        $SessionRecord.BrowserPath = $sessionBrowserPath
        # This is the one owner record passed through the entire root and
        # descendant proof.  Keep it separate from loop locals because
        # PowerShell variable names are case-insensitive.
        $ownerRoot = [pscustomobject]@{
            ProcessId = [int]$SessionRecord.ProcessId
            ProcessStartTicks = [long]$SessionRecord.ProcessStartTicks
            ProcessParentProcessId = if ($SessionRecord.PSObject.Properties['ProcessParentProcessId']) {
                [int]$SessionRecord.ProcessParentProcessId
            } else { 0 }
            ProcessParentProcessStartTicks = if ($SessionRecord.PSObject.Properties['ProcessParentProcessStartTicks']) {
                [long]$SessionRecord.ProcessParentProcessStartTicks
            } else { 0L }
            ProcessCommandLine = if ($SessionRecord.PSObject.Properties['ProcessCommandLine']) {
                [string]$SessionRecord.ProcessCommandLine
            } else { '' }
            BrowserPath = $sessionBrowserPath
            Profile = $sessionProfile
            RunId = [string]$SessionRecord.RunId
            RepositoryIdentity = [string]$SessionRecord.RepositoryIdentity
            CdpPort = [int]$SessionRecord.CdpPort
        }
        # The page socket is no longer used by the route. Natural shutdown
        # establishes its own privately owned browser-level connection.
        $socket = $SessionRecord.Runtime.Socket
        if ($null -ne $socket) {
            try { $socket.Abort() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            try { $socket.Dispose() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            $SessionRecord.Runtime.Socket = $null
        }
        if ($cleanupErrors.Count -gt 0) {
            Throw-VerifierInfrastructure ('Could not close the owned browser session cleanly: ' +
                ($cleanupErrors -join '; '))
        }
        Assert-VerifierBrowserCloseNotAttempted $SessionRecord

        # This scan is mandatory even when the recorded PID is zero, stale, or
        # already gone. Deleting a profile without a successful full process
        # inspection would make a foreign/stale browser process adoptable.
        $snapshot = $null
        try {
            $snapshot = @(Get-VerifierBrowserOwnershipSnapshot `
                $ownerRoot.BrowserPath $ownerRoot.Profile $ownerRoot.RunId `
                $ownerRoot.RepositoryIdentity $ownerRoot.CdpPort)
            $SessionRecord.ProfileProcessScanCompleted = $true
        } catch {
            $SessionRecord.ProfileInspectionFailed = $true
            $SessionRecord.Lease.ProfileInspectionFailed = $true
            throw
        }

        if ([int]$SessionRecord.ProcessId -gt 0) {
            # Keep the verified root alive while repeatedly draining a complete
            # current graph. A one-time descendant list is not a termination
            # proof: Chromium/Edge helpers may appear after any snapshot.
            $drainResult = if ($SessionRecord.Status -ceq 'attached') {
                Close-VerifierBrowserSessionNaturally $Context $SessionRecord $ownerRoot
            } else {
                Stop-VerifierBrowserProcessTreeToFixedPoint $Context $ownerRoot 15000
            }
            if ($null -eq $drainResult -or
                    $null -eq $drainResult.PSObject.Properties['ProcessTerminationProven'] -or
                    -not (Test-VerifierStrictBooleanValue $drainResult.ProcessTerminationProven)) {
                Throw-VerifierInfrastructure 'Browser cleanup received a missing or malformed process-termination proof.'
            }
            $processTerminationProven = $drainResult.ProcessTerminationProven
        }

        try {
            Assert-VerifierBrowserProfileIsQuiescent $ownerRoot.Profile `
                $sessionBrowserPath `
                $ownerRoot.RunId $ownerRoot.RepositoryIdentity $ownerRoot.CdpPort
            $SessionRecord.ProfileProcessScanCompleted = $true
        } catch {
            $SessionRecord.ProfileInspectionFailed = $true
            $SessionRecord.Lease.ProfileInspectionFailed = $true
            throw
        }
        # Repeat the strict recursive ownership walk only after all verified
        # browser descendants are quiescent. This keeps inaccessible open Edge
        # metadata files from masking the process-drain proof while still
        # requiring the final deletion boundary to reject escaped/reparse data.
        [void](Assert-VerifierPhysicalOwnedPath $sessionRoot $sessionProfile -ValidateTree)
        # Release performs its own fail-closed profile/process and listener
        # inspection while the profile still exists. Only after the claim is
        # safely released may the owned profile be deleted; an inspection
        # failure therefore always preserves the profile and evidence.
        [void](Set-VerifierLeaseProcessProofFromCleanup $SessionRecord.Lease `
            $processTerminationProven)
        Release-VerifierPortLease $Context $SessionRecord.Lease
        Remove-VerifierBrowserProfile $Context ([pscustomobject]@{
            Profile = $ownerRoot.Profile
        })
        $SessionRecord.Status = 'cleaned'
        $SessionRecord.CleanupResult = 'complete'
        $SessionRecord.Error = ''
        $SessionRecord.Runtime.Browser = $null
        Write-VerifierManifest $Context
    } catch {
        $SessionRecord.Status = 'cleanup-failed'
        $SessionRecord.CleanupResult = 'infrastructure-failure'
        $SessionRecord.Error = Get-VerifierErrorMessage $_
        # A failed browser-root proof must also block the independent final
        # lease-drain pass in Complete-VerifierRun.  Without this durable
        # blocker, a later filtered profile scan could release the claim after
        # this function correctly refused to clean an orphaned helper.
        if ($SessionRecord.PSObject.Properties['Lease'] -and
                $null -ne $SessionRecord.Lease) {
            if (-not $SessionRecord.Lease.PSObject.Properties['ReleaseBlocked']) {
                Add-Member -InputObject $SessionRecord.Lease -MemberType NoteProperty `
                    -Name ReleaseBlocked -Value $true
            } else {
                $SessionRecord.Lease.ReleaseBlocked = $true
            }
            if (-not $SessionRecord.Lease.PSObject.Properties['ReleaseBlockReason']) {
                Add-Member -InputObject $SessionRecord.Lease -MemberType NoteProperty `
                    -Name ReleaseBlockReason -Value 'browser root/descendant cleanup proof failed'
            } elseif ([String]::IsNullOrWhiteSpace([string]$SessionRecord.Lease.ReleaseBlockReason)) {
                $SessionRecord.Lease.ReleaseBlockReason = 'browser root/descendant cleanup proof failed'
            }
            if ($SessionRecord.Lease.PSObject.Properties['ProcessTerminationProven']) {
                $SessionRecord.Lease.ProcessTerminationProven = $false
            }
            if ($SessionRecord.Lease.PSObject.Properties['ProcessAbsent']) {
                $SessionRecord.Lease.ProcessAbsent = $false
            }
        }
        try { Write-VerifierManifest $Context } catch { }
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Browser cleanup failed: " + (Get-VerifierErrorMessage $_))
    }
}

function New-VerifierBrowserSession($Context, $RouteName, $Url,
        $BrowserPath, $TimeoutSeconds) {
    if (-not (Test-VerifierStrictStringValue $RouteName) -or
            [String]::IsNullOrWhiteSpace($RouteName)) {
        Throw-VerifierInfrastructure 'A browser session route name must be one exact non-empty string before allocation.'
    }
    if (-not (Test-VerifierStrictStringValue $Url)) {
        Throw-VerifierInfrastructure 'A browser session URL must be an exact string before allocation.'
    }
    if ($null -ne $BrowserPath -and -not (Test-VerifierStrictStringValue $BrowserPath)) {
        Throw-VerifierInfrastructure 'A browser session BrowserPath must be an exact string before allocation.'
    }
    $TimeoutSeconds = Assert-VerifierTimeoutSeconds $TimeoutSeconds 'browser session timeout'
    [void](Assert-VerifierContextPreflight $Context 'browser session context' -RequireDurable)
    if ([String]::IsNullOrWhiteSpace($BrowserPath)) {
        $BrowserPath = Resolve-VerifierBrowserPath ''
    } else {
        $BrowserPath = Get-VerifierFullPath $BrowserPath
    }
    $browserSessionRecord = New-VerifierBrowserLease $Context $RouteName $BrowserPath
    $browserSessionRecord.ExpectedUrl = $Url
    $browserSessionRecord.RunId = $Context.RunId
    try {
        if (-not (Test-Path -LiteralPath $BrowserPath -PathType Leaf)) {
            Throw-VerifierInfrastructure "Browser executable was not found: $BrowserPath"
        }
        $arguments = @('--headless=new', '--disable-gpu', '--no-first-run', '--disable-sync',
            '--window-size=1440,1000', "--user-data-dir=$($browserSessionRecord.Profile)",
            "--remote-debugging-port=$($browserSessionRecord.CdpPort)",
            "--tsj-verifier-run=$($Context.RunId)",
            "--tsj-verifier-route=$($browserSessionRecord.RouteId)",
            "--tsj-verifier-worktree=$($Context.RepositoryIdentity)", 'about:blank')
        $browser = Start-VerifierProcess $BrowserPath $arguments
        $browserSessionRecord.Runtime.Browser = $browser
        # Keep the durable session at its exact absent tuple until the live
        # process has passed every identity check.  A failed WMI/parent/command
        # read must not leave ProcessId/StartTicks partially published for the
        # catch block's manifest write; the retained runtime handle remains
        # diagnostic evidence, while cleanup fails closed without a root proof.
        $browserProcessId = [int]$browser.Id
        $browserProcessStartTicks = Get-VerifierProcessStartTicks $browser
        $browserIdentity = Get-VerifierCurrentProcessIdentityWithRetry $browserProcessId `
            $browserProcessStartTicks 0 '' '' $browserSessionRecord.CdpPort $Context.RunId '' `
            0 $BrowserPath
        $browserProcessParentProcessId = [int]$browserIdentity.Record.ParentProcessId
        $browserProcessParentProcessStartTicks = [long]$browserIdentity.Record.ParentProcessStartTicks
        $browserProcessCommandLine = [string]$browserIdentity.Record.CommandLine
        $browserSessionRecord.ProcessId = $browserProcessId
        $browserSessionRecord.ProcessStartTicks = $browserProcessStartTicks
        $browserSessionRecord.ProcessParentProcessId = $browserProcessParentProcessId
        $browserSessionRecord.ProcessParentProcessStartTicks = $browserProcessParentProcessStartTicks
        $browserSessionRecord.ProcessCommandLine = $browserProcessCommandLine
        $browserSessionRecord.Status = 'started'
        Write-VerifierManifest $Context
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        $target = $null
        do {
            if ($browser.HasExited) {
                Throw-VerifierInfrastructure "Browser exited before its CDP target became available."
            }
            Start-Sleep -Milliseconds 100
            try {
                $targets = Invoke-RestMethod "http://127.0.0.1:$($browserSessionRecord.CdpPort)/json/list" -TimeoutSec 2
                $target = @($targets | Where-Object { $_.type -eq 'page' } | Select-Object -First 1)
            } catch { $target = $null }
        } while ($null -eq $target -and [DateTime]::UtcNow -lt $deadline)
        if ($null -eq $target) {
            Throw-VerifierInfrastructure "Browser target did not become available on owned CDP port $($browserSessionRecord.CdpPort)."
        }
        $targetUri = [Uri][string]$target.webSocketDebuggerUrl
        if ($targetUri.Host -notin @('127.0.0.1', 'localhost') -or
                [int]$targetUri.Port -ne [int]$browserSessionRecord.CdpPort) {
            Throw-VerifierInfrastructure "CDP target was not on the owned loopback port."
        }
        # Prove the exact loopback listener and owner before attaching a CDP
        # socket or marking the browser lease bound.
        Confirm-VerifierPortLeaseBound $Context $browserSessionRecord.Lease $browserSessionRecord.ProcessId `
            $browserSessionRecord.ProcessStartTicks $browserSessionRecord
        $socket = Connect-VerifierCdpSocket $targetUri $deadline
        $browserSessionRecord.Runtime.Socket = $socket
        $browserSessionRecord.TargetId = [string]$target.id
        $browserSessionRecord.Status = 'attached'
        Write-VerifierManifest $Context
        return [pscustomobject]@{
            Browser = $browser; Socket = $socket; Profile = $browserSessionRecord.Profile
            CdpPort = $browserSessionRecord.CdpPort; RouteId = $browserSessionRecord.RouteId
            Deadline = $deadline; Record = $browserSessionRecord
        }
    } catch {
        $startupError = $_
        $browserSessionRecord.Status = 'startup-failed'
        $browserSessionRecord.CleanupResult = 'pending'
        $browserSessionRecord.Error = Get-VerifierErrorMessage $startupError
        $manifestError = $null
        try { Write-VerifierManifest $Context } catch {
            $manifestError = $_
        }
        $cleanupError = $null
        if ($null -eq $manifestError) {
            try { Complete-VerifierBrowserSession $Context $browserSessionRecord } catch {
                $cleanupError = $_
            }
        }
        if ($null -ne $manifestError -or $null -ne $cleanupError) {
            $failureDetails = New-Object Collections.ArrayList
            $startupMessage = Get-VerifierErrorMessage $startupError
            [void]$failureDetails.Add('Browser session startup failed: ' + $startupMessage)
            $manifestMessageAdded = $false
            if ($null -ne $manifestError) {
                [void]$failureDetails.Add('startup failure evidence could not be written: ' +
                    (Get-VerifierErrorMessage $manifestError))
                $manifestMessageAdded = $true
            }
            if ($null -ne $cleanupError) {
                $cleanupMessage = Get-VerifierErrorMessage $cleanupError
                # Complete-VerifierBrowserSession records its own cleanup
                # failure. Restore the originating startup reason in the
                # durable record before the final diagnostic write so a
                # cleanup/schema error can never mask the first typed cause.
                $browserSessionRecord.Error = 'startup: ' + $startupMessage +
                    '; cleanup: ' + $cleanupMessage
                try { Write-VerifierManifest $Context } catch {
                    if ($null -eq $manifestError) { $manifestError = $_ }
                }
                [void]$failureDetails.Add('exact cleanup was not proven; resource evidence was retained: ' +
                    $cleanupMessage)
            }
            if ($null -ne $manifestError -and -not $manifestMessageAdded) {
                [void]$failureDetails.Add('startup failure evidence could not be written: ' +
                    (Get-VerifierErrorMessage $manifestError))
            }
            Throw-VerifierInfrastructure ($failureDetails -join '; ')
        }
        if (Test-VerifierInfrastructureError $startupError) { throw $startupError }
        Throw-VerifierInfrastructure ("Browser session startup failed: " + (Get-VerifierErrorMessage $startupError))
    }
}

function Register-VerifierEvidenceArtifact($Context, $Path) {
    [void](Assert-VerifierRawPathValue $Path 'evidence artifact')
    [void](Assert-VerifierContextPreflight $Context 'evidence artifact context' -RequireDurable)
    Assert-VerifierNoReparseAncestors $Context.EvidenceDirectory
    $canonicalPath = Get-VerifierFullPath $Path
    if (-not (Test-VerifierPhysicalChildPath $Context.EvidenceDirectory $canonicalPath)) {
        Throw-VerifierInfrastructure "Refusing to register evidence outside this run's evidence directory."
    }
    Assert-VerifierNoReparseAncestors $canonicalPath
    if (-not ($Context.Artifacts -contains $canonicalPath)) {
        [void]$Context.Artifacts.Add($canonicalPath)
        Write-VerifierManifest $Context
    }
}

function Assert-VerifierPreviewIdentitySchema($Identity) {
    if ($null -eq $Identity -or $Identity -is [array] -or
            $Identity -isnot [pscustomobject]) {
        Throw-VerifierInfrastructure 'Preview identity response was not an exact JSON object.'
    }
    $allowedProperties = @('protocol', 'repositoryRoot', 'previewScript',
        'webRoot', 'verifierRunId', 'verifierNonce', 'sourceRoot', 'scriptRoot',
        'sourceDigest', 'scriptDigest', 'webDigest', 'executionDigest',
        'executionFileCount', 'previewPort', 'processId', 'processStartTicks')
    foreach ($property in @($Identity.PSObject.Properties)) {
        if ($allowedProperties -cnotcontains [string]$property.Name) {
            Throw-VerifierInfrastructure "Preview identity contained an unknown field '$($property.Name)'."
        }
    }
    foreach ($propertyName in @('protocol', 'repositoryRoot', 'previewScript',
            'webRoot', 'verifierRunId', 'verifierNonce', 'sourceRoot', 'scriptRoot',
            'sourceDigest', 'scriptDigest', 'webDigest', 'executionDigest')) {
        if ($null -eq $Identity.PSObject.Properties[$propertyName] -or
                -not (Test-VerifierStrictStringValue $Identity.PSObject.Properties[$propertyName].Value)) {
            Throw-VerifierInfrastructure "Preview identity omitted or malformed its exact string '$propertyName'."
        }
    }
    foreach ($propertyName in @('previewPort', 'processId', 'processStartTicks',
            'executionFileCount')) {
        if ($null -eq $Identity.PSObject.Properties[$propertyName] -or
                -not (Test-VerifierStrictIntegralValue $Identity.PSObject.Properties[$propertyName].Value `
                    0 ([long]::MaxValue))) {
            Throw-VerifierInfrastructure "Preview identity omitted or malformed its exact integral '$propertyName'."
        }
    }
    $previewPort = $Identity.PSObject.Properties['previewPort'].Value
    if (-not (Test-VerifierStrictIntegralValue $previewPort 1 65535)) {
        Throw-VerifierInfrastructure 'Preview identity carried an invalid exact preview port.'
    }
    foreach ($digestProperty in @('sourceDigest', 'scriptDigest', 'webDigest',
            'executionDigest')) {
        if ([string]$Identity.PSObject.Properties[$digestProperty].Value -notmatch '^[0-9a-f]{64}$') {
            Throw-VerifierInfrastructure "Preview identity carried a malformed '$digestProperty'."
        }
    }
    if ([long]$Identity.executionFileCount -le 0) {
        Throw-VerifierInfrastructure 'Preview identity carried a non-positive execution file count.'
    }
    if ([long]$Identity.processId -gt 0 -and [long]$Identity.processStartTicks -le 0) {
        Throw-VerifierInfrastructure 'Preview identity carried a process without a positive start identity.'
    }
}

function Set-VerifierCallerOwnedPreview($Context, [string]$BaseUrl) {
    [void](Assert-VerifierContextPreflight $Context 'caller-owned preview context' -RequireDurable)
    try {
         $uri = [Uri]$BaseUrl
        if ($uri.Scheme -ne 'http' -or $uri.Host -notin @('127.0.0.1', 'localhost')) {
            Throw-VerifierInfrastructure 'Caller-owned preview must be an HTTP loopback URL.'
        }
         if ($uri.Port -lt 1 -or $uri.Port -gt 65535) {
             Throw-VerifierInfrastructure 'Caller-owned preview URL must include a valid TCP port.'
         }
        if ($uri.AbsolutePath -notin @('', '/') -or $uri.Query -or $uri.Fragment) {
            Throw-VerifierInfrastructure 'Caller-owned preview URL must identify only the preview root.'
        }
         $identityRequestRoot = $BaseUrl.TrimEnd('/')
         # The integrated contract records one canonical loopback root even if
         # the caller used the equivalent localhost spelling. The request may
         # use the caller's supplied loopback spelling; ownership identity is
         # still checked against the exact repository/script/web-root response.
         $normalized = 'http://127.0.0.1:' + [string]$uri.Port
         Assert-VerifierNoReparseAncestors $Context.WorktreeRoot
         $identityResponse = Invoke-WebRequest -UseBasicParsing -Uri ($identityRequestRoot + '/__tsj/verify-identity') -TimeoutSec 5
         $identity = $identityResponse.Content | ConvertFrom-Json
         Assert-VerifierPreviewIdentitySchema $identity
         $expectedScript = Get-VerifierFullPath (Join-Path $Context.WorktreeRoot 'scripts\preview.ps1')
         $expectedWebRoot = Get-VerifierFullPath (Join-Path $Context.WorktreeRoot 'war')
        [void](Assert-VerifierPhysicalOwnedPath $Context.WorktreeRoot $expectedScript)
        [void](Assert-VerifierPhysicalOwnedPath $Context.WorktreeRoot $expectedWebRoot -ValidateTree)
        if ($identity.protocol -cne 'troubleshootjs-preview-identity-v1' -or
                -not (Test-VerifierCanonicalWindowsPathValue $identity.repositoryRoot $Context.WorktreeRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue $identity.previewScript $expectedScript) -or
                -not (Test-VerifierCanonicalWindowsPathValue $identity.webRoot $expectedWebRoot) -or
                $identity.previewPort -ne $uri.Port) {
            Throw-VerifierInfrastructure "Caller-owned preview identity does not match this worktree."
        }
        $runOwnedHandshake = ($Context.Server.Owner -eq 'run' -and
            $Context.Server.BaseUrl -eq $normalized)
        $identityRunId = $identity.verifierRunId
        $identityNonce = $identity.verifierNonce
        if ($runOwnedHandshake) {
            if ($identityRunId -cne $Context.RunId -or
                    $identityNonce -cne $Context.PreviewNonce -or
                    $identity.processId -ne $Context.Server.ProcessId -or
                    $identity.processStartTicks -ne $Context.Server.ProcessStartTicks -or
                    $identity.previewPort -ne $Context.Server.Port) {
                Throw-VerifierInfrastructure "Run-owned preview identity did not match its recorded run, process, nonce, or port."
            }
            $recordedProcess = Get-VerifierProcessById $Context.Server.ProcessId
            if ($null -eq $recordedProcess -or
                    (Get-VerifierProcessStartTicks $recordedProcess) -ne $Context.Server.ProcessStartTicks) {
                Throw-VerifierInfrastructure "Run-owned preview identity process PID $($Context.Server.ProcessId) was not the recorded process instance."
            }
            $snapshot = @(Get-VerifierProcessRecordsByIdWithFallback `
                $Context.Server.ProcessId 'run-owned preview identity')
            if ($snapshot.Count -ne 1 -or
                    -not $snapshot[0].PSObject.Properties['CommandLine'] -or
                    [String]::IsNullOrWhiteSpace([string]$snapshot[0].CommandLine)) {
                Throw-VerifierInfrastructure "Run-owned preview PID $($Context.Server.ProcessId) did not provide a complete command-line identity."
            }
            $command = $snapshot[0].CommandLine
            if (-not (Test-VerifierCommandLinePath $command $Context.Server.Script) -or
                    -not (Test-VerifierCommandLineSwitch $command '-Port' ([string]$Context.Server.Port)) -or
                    -not (Test-VerifierCommandLineSwitch $command '-VerifierRunId' $Context.RunId) -or
                    -not (Test-VerifierCommandLineSwitch $command '-VerifierNonce' $Context.PreviewNonce)) {
                Throw-VerifierInfrastructure "Run-owned preview process command identity did not match its recorded script, port, run, or nonce."
            }
        } elseif (-not [String]::IsNullOrWhiteSpace($identityRunId) -or
                -not [String]::IsNullOrWhiteSpace($identityNonce)) {
            Throw-VerifierInfrastructure 'A caller-owned preview cannot adopt a run-owned server identity.'
        }
         $Context.BaseUrl = $normalized
         if ($runOwnedHandshake) {
             $Context.Server.RepositoryRoot = $Context.WorktreeRoot
             $Context.Server.WebRoot = $expectedWebRoot
             $Context.Server.IdentityProtocol = [string]$identity.protocol
             $Context.Server.IdentityVerified = $true
             $Context.Server.CallerOwned = $false
             $Context.Server.State = 'run-owned-verified'
         } else {
             $Context.Server = [pscustomobject]@{
                 Owner = 'caller'; BaseUrl = $normalized; Port = [int]$uri.Port
                 ProcessId = 0; ProcessStartTicks = 0; Script = $expectedScript
                 RepositoryRoot = $Context.WorktreeRoot; WebRoot = $expectedWebRoot
                 IdentityProtocol = [string]$identity.protocol; IdentityVerified = $true
                 CallerOwned = $true
                 RunId = ''; Nonce = ''; Lease = $null; Process = $null
                ProcessParentProcessId = 0; ProcessParentProcessStartTicks = 0
                ProcessCommandLine = ''
                ProcessIdentityKnown = $false; OwnershipUncertain = $false
                State = 'caller-verified'; StdoutLog = ''; StderrLog = ''
                CleanupResult = 'not-owned'; Error = ''
                ProcessTerminationProven = $false; ProcessAbsent = $true
                ListenerInspectionProven = $false; ListenerAbsent = $false
            }
        }
        Write-VerifierManifest $Context
        return $normalized
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Caller-owned preview identity check failed: " +
            (Get-VerifierErrorMessage $_))
    }
}

function Start-VerifierOwnedPreview($Context, $PreviewScript, $TimeoutSeconds,
        $ForceTcpListener = $false, $RequestedPort = 0) {
    [void](Assert-VerifierRawPathValue $PreviewScript 'owned preview script')
    $canonicalPreviewScript = Get-VerifierFullPath $PreviewScript
    $TimeoutSeconds = Assert-VerifierTimeoutSeconds $TimeoutSeconds 'owned preview timeout'
    if (-not (Test-VerifierStrictBooleanValue $ForceTcpListener)) {
        Throw-VerifierInfrastructure 'Owned preview TCP-listener test mode was not an exact Boolean.'
    }
    if (-not (Test-VerifierStrictIntegralValue $RequestedPort 0 65535)) {
        Throw-VerifierInfrastructure 'Owned preview requested port was not an exact integral value.'
    }
    [void](Assert-VerifierContextPreflight $Context 'owned preview context' -RequireDurable)
    $lease = New-VerifierPortLease $Context 'preview' $RequestedPort
    $serverRoot = Join-Path $Context.RunRoot 'server'
    $stdoutLog = Join-Path $serverRoot 'stdout.log'
    $stderrLog = Join-Path $serverRoot 'stderr.log'
    # Record ownership as soon as the port lease exists, so even a failed
    # process launch is cleaned by Complete-VerifierRun.
    $Context.Server = [pscustomobject]@{
        Owner = 'run'; BaseUrl = "http://127.0.0.1:$($lease.Port)"
        Port = $lease.Port; ProcessId = 0; ProcessStartTicks = 0
        ProcessParentProcessId = 0; ProcessCommandLine = ''
        ProcessParentProcessStartTicks = 0
        Script = $canonicalPreviewScript; State = 'lease-acquired'
        RepositoryRoot = $Context.WorktreeRoot
        WebRoot = Get-VerifierFullPath (Join-Path $Context.WorktreeRoot 'war')
        IdentityProtocol = ''; IdentityVerified = $false; CallerOwned = $false
        RunId = $Context.RunId; Nonce = $Context.PreviewNonce
        StdoutLog = $stdoutLog; StderrLog = $stderrLog
        CleanupResult = 'pending'; Error = ''; Lease = $lease
        Process = $null; ProcessIdentityKnown = $false; OwnershipUncertain = $false
        ProcessTerminationProven = $false; ProcessAbsent = $false
        ListenerInspectionProven = $false; ListenerAbsent = $false
    }
    try {
        Assert-VerifierNoReparseAncestors $Context.WorktreeRoot
        Assert-VerifierNoReparseAncestors $canonicalPreviewScript
        Assert-VerifierNoReparseAncestors (Get-VerifierFullPath (Join-Path $Context.WorktreeRoot 'war'))
        Write-VerifierManifest $Context
        $Context.Server.Script = $canonicalPreviewScript
        [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $serverRoot)
        New-Item -ItemType Directory -Path $serverRoot -Force | Out-Null
        [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $serverRoot -ValidateTree)
        [void](Assert-VerifierPhysicalOwnedPath $serverRoot $stdoutLog)
        [void](Assert-VerifierPhysicalOwnedPath $serverRoot $stderrLog)
        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $previewArguments = @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $Context.Server.Script,
            '-Port', [string]$lease.Port, '-VerifierRunId', $Context.RunId,
            '-VerifierNonce', $Context.PreviewNonce)
        if ($ForceTcpListener) { $previewArguments += '-ForceTcpListener' }
        $process = Start-VerifierProcess $powershell $previewArguments $stdoutLog $stderrLog
        # Capture the live handle/PID before any fallible StartTime or WMI
        # identity call. A delayed-bind or identity failure must retain this
        # exact process record and its claim for safe cleanup diagnosis.
        $Context.Server.Process = $process
        $Context.Server.ProcessId = [int]$process.Id
        $Context.Server.State = 'started-unidentified'
        $Context.Server.OwnershipUncertain = $true
        Write-VerifierManifest $Context
        if ($Context.TestHooks.PSObject.Properties['FailNextPreviewIdentityCapture'] -and
                [bool]$Context.TestHooks.FailNextPreviewIdentityCapture) {
            $Context.TestHooks.FailNextPreviewIdentityCapture = $false
            Throw-VerifierInfrastructure 'Injected owned-preview process-identity capture failure.'
        }
        $startTicks = Get-VerifierProcessStartTicks $process
        $currentIdentity = Get-VerifierCurrentProcessIdentity $process.Id $startTicks 0 '' `
            $Context.Server.Script $lease.Port $Context.RunId $Context.PreviewNonce
        $Context.Server.ProcessStartTicks = $startTicks
        $Context.Server.ProcessParentProcessId = [int]$currentIdentity.Record.ParentProcessId
        $Context.Server.ProcessParentProcessStartTicks = [long]$currentIdentity.Record.ParentProcessStartTicks
        $Context.Server.ProcessCommandLine = [string]$currentIdentity.Record.CommandLine
        $Context.Server.ProcessIdentityKnown = $true
        $Context.Server.OwnershipUncertain = $false
        $Context.Server.State = 'starting'
        Write-VerifierManifest $Context
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        do {
            if ($process.HasExited) { break }
            Start-Sleep -Milliseconds 150
            try {
                $response = Invoke-WebRequest -UseBasicParsing -Uri ($Context.Server.BaseUrl + '/__tsj/verify-identity') -TimeoutSec 2
                if ($response.StatusCode -eq 200) { break }
            } catch { }
        } while ([DateTime]::UtcNow -lt $deadline)
        if ($process.HasExited) {
            Complete-VerifierProcessOutputCapture $process 5000
            $details = if (Test-Path -LiteralPath $stderrLog) { (Get-Content -LiteralPath $stderrLog -Raw).Trim() } else { '' }
            Throw-VerifierInfrastructure "Owned preview exited during startup. $details"
        }
        if ([DateTime]::UtcNow -ge $deadline) {
            Throw-VerifierInfrastructure "Owned preview did not answer its identity handshake on port $($lease.Port)."
        }
        [void](Set-VerifierCallerOwnedPreview $Context $Context.Server.BaseUrl)
        $Context.Server.Owner = 'run'
        $Context.Server.State = 'run-owned-verified'
        Confirm-VerifierPortLeaseBound $Context $Context.Server.Lease `
            $Context.Server.ProcessId $Context.Server.ProcessStartTicks $Context.Server
        Write-VerifierManifest $Context
        return $Context.Server.BaseUrl
    } catch {
        $Context.Server.State = 'startup-failed'
        $Context.Server.Error = Get-VerifierErrorMessage $_
        $failureProcessIdKnown = ($Context.Server.PSObject.Properties['ProcessId'] -and
            (Test-VerifierStrictIntegralValue $Context.Server.PSObject.Properties['ProcessId'].Value `
                0 ([int]::MaxValue)))
        $failureIdentityKnown = ($Context.Server.PSObject.Properties['ProcessIdentityKnown'] -and
            (Test-VerifierStrictBooleanValue $Context.Server.PSObject.Properties['ProcessIdentityKnown'].Value) -and
            $Context.Server.ProcessIdentityKnown)
        if (-not $failureProcessIdKnown -or -not $failureIdentityKnown) {
            $Context.Server.OwnershipUncertain = $true
            $Context.Server.Lease.ReleaseBlocked = $true
            $Context.Server.Lease.ReleaseBlockReason = 'owned preview process identity/startup was not proven'
        }
        Write-VerifierManifest $Context
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Owned preview startup failed: " + (Get-VerifierErrorMessage $_))
    }
}

function Test-VerifierPreviewCleanupReadiness($Server,
        $ProcessTerminationProven, $ListenerInspectionProven,
        $ListenerAbsent) {
    if ($null -eq $Server) { return $false }
    foreach ($proofValue in @($ProcessTerminationProven, $ListenerInspectionProven,
            $ListenerAbsent)) {
        if (-not (Test-VerifierStrictBooleanValue $proofValue)) { return $false }
    }
    if ($null -eq $Server.PSObject.Properties['ProcessIdentityKnown'] -or
            -not (Test-VerifierStrictBooleanValue $Server.ProcessIdentityKnown) -or
            $null -eq $Server.PSObject.Properties['ProcessId'] -or
            -not (Test-VerifierStrictIntegralValue $Server.ProcessId 0 ([int]::MaxValue)) -or
            $null -eq $Server.PSObject.Properties['ProcessStartTicks'] -or
            -not (Test-VerifierStrictIntegralValue $Server.ProcessStartTicks 0)) {
        return $false
    }
    $identityKnown = ($Server.PSObject.Properties['ProcessIdentityKnown'] -and
        $Server.ProcessIdentityKnown -and
        [long]$Server.ProcessStartTicks -gt 0)
    if ([int]$Server.ProcessId -gt 0 -and
        (-not $identityKnown) -and -not $ProcessTerminationProven) {
        return $false
    }
    return $ProcessTerminationProven -and $ListenerInspectionProven -and $ListenerAbsent
}

function Complete-VerifierPreview($Context) {
    # Cleanup must reject malformed durable context, server, lease, lifecycle,
    # and ClaimMutex state before process fields are cast or any side effect is
    # attempted. Keep this preflight outside the mutation catch block so a
    # malformed record cannot be rewritten as cleanup-failed evidence.
    Assert-VerifierDurableManifestContext $Context
    Assert-VerifierDurableServerLease $Context 'owned preview cleanup server' -AllowMissing
    if ($null -eq $Context.Server) { return }
    if ($Context.Server.Owner -cne 'run' -or
            $Context.Server.CleanupResult -ceq 'complete') { return }
    try {
        if ($null -eq $Context.Server.PSObject.Properties['ProcessIdentityKnown'] -or
                -not (Test-VerifierStrictBooleanValue $Context.Server.ProcessIdentityKnown) -or
                $null -eq $Context.Server.PSObject.Properties['ProcessId'] -or
                -not (Test-VerifierStrictIntegralValue $Context.Server.ProcessId 0 ([int]::MaxValue)) -or
                $null -eq $Context.Server.PSObject.Properties['ProcessStartTicks'] -or
                -not (Test-VerifierStrictIntegralValue $Context.Server.ProcessStartTicks 0)) {
            Throw-VerifierInfrastructure 'Owned preview cleanup encountered missing or malformed process identity proof fields.'
        }
        $processId = [int]$Context.Server.ProcessId
        $processTerminationProven = ($processId -le 0)
        if ($processId -gt 0) {
            $identityKnown = ($Context.Server.PSObject.Properties['ProcessIdentityKnown'] -and
                $Context.Server.ProcessIdentityKnown -and
                [long]$Context.Server.ProcessStartTicks -gt 0)
            if (-not $identityKnown) {
                # A process that was started but never received a trustworthy
                # start identity may bind later. Do not stop by PID or release
                # its claim unless its captured handle proves it already exited
                # and the exact PID is absent now.
                $uncertainProcess = $Context.Server.Process
                if ($null -eq $uncertainProcess) {
                    $uncertainProcess = Get-VerifierProcessById $processId
                }
                if ($null -eq $uncertainProcess) {
                    Throw-VerifierInfrastructure "Owned preview PID $processId has uncertain survival/identity; its claim is retained."
                }
                try { $uncertainProcess.Refresh() } catch {
                    Throw-VerifierInfrastructure "Could not refresh owned preview PID $processId while identity was uncertain: $(Get-VerifierErrorMessage $_)"
                }
                $uncertainHasExited = $false
                try { $uncertainHasExited = [bool]$uncertainProcess.WaitForExit(0) } catch { }
                try { $uncertainProcess.Refresh() } catch {
                    Throw-VerifierInfrastructure "Could not refresh owned preview PID $processId after the identity-uncertain stop: $(Get-VerifierErrorMessage $_)"
                }
                if (-not $uncertainHasExited) { $uncertainHasExited = [bool]$uncertainProcess.HasExited }
                if (-not $uncertainHasExited) {
                    try { $uncertainHasExited = [bool]$uncertainProcess.WaitForExit(1000) } catch { }
                    try { $uncertainProcess.Refresh() } catch { }
                    if (-not $uncertainHasExited) { $uncertainHasExited = [bool]$uncertainProcess.HasExited }
                }
                $uncertainCurrentProcess = Get-VerifierProcessById $processId
                $uncertainCurrentRecord = Get-VerifierCurrentProcessRecordById $processId
                if (-not $uncertainHasExited -or $null -ne $uncertainCurrentProcess -or
                        $null -ne $uncertainCurrentRecord) {
                    $Context.Server.OwnershipUncertain = $true
                    $Context.Server.Lease.ReleaseBlocked = $true
                    $Context.Server.Lease.ReleaseBlockReason = 'preview process survival/start identity was not proven'
                    Throw-VerifierInfrastructure ("Owned preview PID $processId has uncertain survival/identity; its claim is retained. " +
                        "hasExited=$uncertainHasExited currentProcessPresent=$([bool]($null -ne $uncertainCurrentProcess)) " +
                        "currentWmiPresent=$([bool]($null -ne $uncertainCurrentRecord))")
                }
                $processTerminationProven = $true
            } else {
                # The process object captured at launch is only a diagnostic
                # handle. Immediately before termination, re-query the PID,
                # current Win32_Process record, parent, start identity, and
                # every run-owned command-line marker. Stop only that newly
                # verified Process object; a PID replacement is retained as
                # infrastructure evidence and can never be killed by PID.
                $currentProcess = Get-VerifierProcessById $processId
                if ($null -eq $currentProcess) {
                    $capturedProcess = $Context.Server.Process
                    if ($null -eq $capturedProcess) {
                        Throw-VerifierInfrastructure "Owned preview PID $processId disappeared without a retained process handle."
                    }
                    try { $capturedProcess.Refresh() } catch {
                        Throw-VerifierInfrastructure "Could not prove owned preview PID $processId exited: $(Get-VerifierErrorMessage $_)"
                    }
                    if (-not [bool]$capturedProcess.HasExited) {
                        Throw-VerifierInfrastructure "Owned preview PID $processId disappeared without a proven terminal state."
                    }
                    $capturedCurrentRecord = Get-VerifierCurrentProcessRecordById $processId
                    if ($null -ne $capturedCurrentRecord) {
                        Throw-VerifierInfrastructure "Owned preview PID $processId disappeared from Get-Process but remained in Win32_Process."
                    }
                    $processTerminationProven = $true
                } else {
                    $expectedParent = if ($Context.Server.PSObject.Properties['ProcessParentProcessId']) {
                        [int]$Context.Server.ProcessParentProcessId
                    } else { 0 }
                    $expectedCommandLine = if ($Context.Server.PSObject.Properties['ProcessCommandLine']) {
                        [string]$Context.Server.ProcessCommandLine
                    } else { '' }
                    $expectedParentStart = if ($Context.Server.PSObject.Properties['ProcessParentProcessStartTicks']) {
                        [long]$Context.Server.ProcessParentProcessStartTicks
                    } else { 0L }
                    $verified = Get-VerifierCurrentProcessIdentity $processId `
                        ([long]$Context.Server.ProcessStartTicks) $expectedParent `
                        $expectedCommandLine `
                        ([string]$Context.Server.Script) ([int]$Context.Server.Port) `
                        ([string]$Context.Server.RunId) ([string]$Context.Server.Nonce) `
                        $expectedParentStart
                    $currentProcess = $verified.Process
                    [void](Stop-VerifierVerifiedProcessExactly $currentProcess `
                        ([long]$Context.Server.ProcessStartTicks) 5000 $verified.Record)
                    $processTerminationProven = $true
                }
            }
        }
        if ($null -ne $Context.Server.Process) {
            Complete-VerifierProcessOutputCapture $Context.Server.Process 5000
        }
        $previewInspection = Get-VerifierLoopbackListenerRecords `
            ([int]$Context.Server.Port) $Context $Context.Server
        if (-not $previewInspection.Success -or -not $previewInspection.Known) {
            Throw-VerifierInfrastructure "Could not positively inspect owned preview port $($Context.Server.Port) during cleanup."
        }
        if ($previewInspection.HasListeners) {
            Throw-VerifierInfrastructure "Preview cleanup completed but its leased port $($Context.Server.Port) is still in use; ownership is not proven."
        }
        foreach ($proofProperty in @('ProcessTerminationProven', 'ProcessAbsent',
                'ListenerInspectionProven', 'ListenerAbsent')) {
            if (-not $Context.Server.PSObject.Properties[$proofProperty]) {
                Add-Member -InputObject $Context.Server -MemberType NoteProperty `
                    -Name $proofProperty -Value $false
            }
        }
        $Context.Server.ProcessTerminationProven = $processTerminationProven
        $Context.Server.ProcessAbsent = ($processId -le 0 -or
            $null -eq (Get-VerifierProcessById $processId))
        $Context.Server.ListenerInspectionProven = ($previewInspection.Success -and
            $previewInspection.Known)
        $Context.Server.ListenerAbsent = (-not $previewInspection.HasListeners)
        if (-not (Test-VerifierPreviewCleanupReadiness $Context.Server $processTerminationProven `
                ($previewInspection.Success -and $previewInspection.Known) `
                (-not $previewInspection.HasListeners))) {
            Throw-VerifierInfrastructure 'Owned preview cleanup did not prove process termination and listener absence.'
        }
        # An earlier identity-capture failure remains blocking until this exact
        # cleanup proof succeeds. Do not clear it before both process and port
        # ownership are positively resolved.
        $Context.Server.OwnershipUncertain = $false
        $Context.Server.Lease.ReleaseBlocked = $false
        $Context.Server.Lease.ReleaseBlockReason = ''
        $Context.Server.State = 'cleaned'
        $Context.Server.CleanupResult = 'complete'
        # A preview can terminate after startup identity capture fails, before
        # its port lease was ever bound to a positive process identity.  The
        # server owns that handle/termination proof; the shared propagation
        # boundary keeps an unbound lease at its explicit false/false tuple.
        [void](Set-VerifierLeaseProcessProofFromCleanup $Context.Server.Lease `
            $processTerminationProven)
        Release-VerifierPortLease $Context $Context.Server.Lease
        Write-VerifierManifest $Context
    } catch {
        $Context.Server.State = 'cleanup-failed'
        $Context.Server.CleanupResult = 'infrastructure-failure'
        $Context.Server.Error = Get-VerifierErrorMessage $_
        $failureProcessIdKnown = ($Context.Server.PSObject.Properties['ProcessId'] -and
            (Test-VerifierStrictIntegralValue $Context.Server.ProcessId 0 ([int]::MaxValue)))
        $failureIdentityKnown = ($Context.Server.PSObject.Properties['ProcessIdentityKnown'] -and
            (Test-VerifierStrictBooleanValue $Context.Server.ProcessIdentityKnown) -and
            $Context.Server.ProcessIdentityKnown)
        if (-not $failureProcessIdKnown -or -not $failureIdentityKnown) {
            $Context.Server.OwnershipUncertain = $true
            $Context.Server.Lease.ReleaseBlocked = $true
            $Context.Server.Lease.ReleaseBlockReason = 'preview cleanup could not prove process survival/identity'
        }
        Write-VerifierManifest $Context
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Preview cleanup failed: " + (Get-VerifierErrorMessage $_))
    }
}

function Test-VerifierLeaseNeedsCleanup($Lease) {
    if ($null -eq $Lease) {
        Throw-VerifierInfrastructure 'Cleanup authority was missing its lease record.'
    }
    try { [void](Assert-VerifierLeaseTransition $Lease 'lease cleanup transition') } catch {
        # An impossible or malformed lifecycle record must stay on the cleanup
        # path so the owning caller retains evidence instead of skipping it as
        # terminal. Complete-VerifierRun performs the full throwing preflight.
        return $true
    }
    foreach ($stateProperty in @('Status', 'ReleaseState',
            'ReleaseJournalState', 'ClaimState')) {
        if ($null -eq $Lease.PSObject.Properties[$stateProperty] -or
                -not (Test-VerifierStrictStringValue $Lease.PSObject.Properties[$stateProperty].Value)) {
            # Missing/malformed lifecycle state is not terminal evidence. Keep
            # the record on the cleanup path, where Release-VerifierPortLease
            # will fail closed instead of allowing cleanup to be skipped.
            return $true
        }
    }
    foreach ($booleanProperty in @('ReleaseBlocked', 'MutexReleased')) {
        $property = $Lease.PSObject.Properties[$booleanProperty]
        if ($null -eq $property -or
                -not (Test-VerifierStrictBooleanValue $property.Value)) {
            # An untrusted lifecycle record remains on the cleanup path.  The
            # release boundary will retain evidence and fail closed instead of
            # allowing an invalid record to be mistaken for terminal absence.
            return $true
        }
    }
    if ($Lease.ReleaseBlocked) { return $true }
    if ($Lease.Status -ne 'released') { return $true }
    if ($Lease.ReleaseState -ne 'complete') { return $true }
    if ($Lease.ReleaseJournalState -ne 'complete') { return $true }
    # The durable pre-delete tombstone is intentionally incomplete until the
    # claim has been removed and the final released state has been persisted.
    # This also makes recovery after a process interruption idempotent.
    if ($Lease.ClaimState -ne 'released') { return $true }
    if ($Lease.PSObject.Properties['ClaimMutex'] -and $null -ne $Lease.ClaimMutex) {
        return $true
    }
    # A persisted released tombstone can outlive the process that
    # wrote it. Only the shared terminal predicate may prove that all release,
    # listener/process, ClaimMutex, association, and claim-absence evidence is
    # complete; otherwise keep it on the cleanup path.
    try {
        return -not (Test-VerifierDurableLeaseTerminal $Lease $null `
            'lease cleanup terminal proof')
    } catch {
        return $true
    }
}

function Complete-VerifierRun($Context) {
    [void](Assert-VerifierContextPreflight $Context 'verifier cleanup context')
    # Completion is the last cleanup authority.  Validate the complete live
    # context, including every lease's exact ClaimMutex association, before
    # inspecting paths, lifecycle fields, processes, listeners, or mutating
    # any cleanup state.
    Assert-VerifierDurableManifestContext $Context
    # CleanupErrors is the evidence for this cleanup pass, not an append-only
    # history of earlier failed retries.  Replace it only after the complete
    # durable preflight succeeds; every entry written below then describes the
    # current pass, and a successful retry serializes an exact empty array.
    $Context.CleanupErrors = New-Object Collections.ArrayList
    $pendingLeases = @($Context.LeaseRecords | Where-Object {
        Test-VerifierLeaseNeedsCleanup $_
    })
    $pendingSessions = @($Context.BrowserSessions | Where-Object {
        $_.CleanupResult -ne 'complete'
    })
    $pendingPreview = ($Context.Server -and $Context.Server.Owner -eq 'run' -and
        $Context.Server.CleanupResult -ne 'complete')
    $errors = New-Object Collections.ArrayList
    try {
        # A changed physical namespace invalidates every later cleanup proof.
        # Stop the cleanup pass before it can follow a replaced junction and
        # retain the run manifest/evidence for diagnosis.
        Assert-VerifierContextPhysicalResources $Context
    } catch {
        [void]$errors.Add('Verifier cleanup physical-namespace proof failed: ' +
            (Get-VerifierErrorMessage $_))
    }
    if ($errors.Count -gt 0) {
        $Context.CleanupState = 'infrastructure-failure'
        $Context.CleanupCompletedUtc = Get-VerifierUtcText
        foreach ($message in $errors) { [void]$Context.CleanupErrors.Add($message) }
        try { Write-VerifierManifest $Context } catch {
            [void]$errors.Add((Get-VerifierErrorMessage $_))
        }
        return [pscustomobject]@{ Success = $false; Errors = @($errors) }
    }
    foreach ($sessionRecord in @($Context.BrowserSessions)) {
        try { Complete-VerifierBrowserSession $Context $sessionRecord } catch {
            [void]$errors.Add((Get-VerifierErrorMessage $_))
        }
    }
    try { Complete-VerifierPreview $Context } catch {
        [void]$errors.Add((Get-VerifierErrorMessage $_))
    }
    # Browser/preview cleanup can fail part-way through setup. Walk the lease
    # ledger independently so every still-owned record gets one exact, safe
    # release attempt; Release-VerifierPortLease refuses active listeners,
    # foreign profiles, missing handles, and changed ownership.
    # Drain the ledger more than once: browser/preview cleanup can unblock a
    # lease only after its first release attempt, while every pass remains
    # bounded and exact. Never broad-search or release by port alone.
    for ($releasePass = 0; $releasePass -lt 3; $releasePass++) {
        $leasesToRelease = @($Context.LeaseRecords | Where-Object {
            Test-VerifierLeaseNeedsCleanup $_
        })
        if ($leasesToRelease.Count -eq 0) { break }
        foreach ($lease in $leasesToRelease) {
            try {
                Release-VerifierPortLease $Context $lease
            } catch {
                [void]$errors.Add((Get-VerifierErrorMessage $_))
            }
        }
    }

    # A successful cleanup result requires a final ledger/resource proof, not
    # merely a successful call to a cleanup helper. Any uncertain claim handle,
    # exact claim file, profile, server, or listener is infrastructure failure
    # and keeps the run root/manifest/evidence available for diagnosis.
    foreach ($lease in @($Context.LeaseRecords)) {
        $claimAbsenceKnown = $false
        $claimAbsent = $false
        if ($null -ne $lease.ClaimMutex) {
            [void]$errors.Add("Port $($lease.Port) retained a live claim handle after cleanup.")
        }
        if (-not [String]::IsNullOrWhiteSpace([string]$lease.Path)) {
            try {
                Assert-VerifierNoReparseAncestors $Context.PortLeaseRoot
                if (-not (Test-VerifierPhysicalChildPath $Context.PortLeaseRoot $lease.Path)) {
                    [void]$errors.Add("Port $($lease.Port) claim ownership could not be proven after cleanup.")
                } elseif (Test-Path -LiteralPath $lease.Path -PathType Leaf -ErrorAction Stop) {
                    [void]$errors.Add("Port $($lease.Port) retained its exact claim file after cleanup.")
                } else {
                    $claimAbsent = $true
                    $claimAbsenceKnown = $true
                }
            } catch {
                [void]$errors.Add("Port $($lease.Port) claim absence could not be proven: $(Get-VerifierErrorMessage $_)")
            }
        }
        if ($lease.PSObject.Properties['ProfilePath'] -and
                -not [String]::IsNullOrWhiteSpace([string]$lease.ProfilePath)) {
            try {
                if (-not (Test-VerifierPhysicalChildPath $Context.RunRoot $lease.ProfilePath)) {
                    [void]$errors.Add("Port $($lease.Port) profile ownership could not be proven after cleanup.")
                } elseif (Test-Path -LiteralPath $lease.ProfilePath -ErrorAction Stop) {
                    [void]$errors.Add("Port $($lease.Port) retained its owned profile after cleanup.")
                }
            } catch {
                [void]$errors.Add("Port $($lease.Port) profile absence could not be proven: $(Get-VerifierErrorMessage $_)")
            }
        }
        $mutexReleasedValid = ($lease.PSObject.Properties['MutexReleased'] -and
            (Test-VerifierStrictBooleanValue $lease.MutexReleased))
        if (-not $mutexReleasedValid) {
            [void]$errors.Add("Port $($lease.Port) carried a missing or malformed MutexReleased lifecycle proof.")
        }
        $releasedLifecycleProven = if ($mutexReleasedValid -and $lease.MutexReleased -and
                $lease.PSObject.Properties['ReleaseState'] -and
                (Test-VerifierStrictStringValue $lease.ReleaseState) -and
                $lease.ReleaseState -in @('os-released', 'complete', 'claim-delete-failed') -and
                $lease.PSObject.Properties['ReleaseJournalState'] -and
                (Test-VerifierStrictStringValue $lease.ReleaseJournalState) -and
                $lease.ReleaseJournalState -eq 'complete' -and
                $lease.PSObject.Properties['ClaimState'] -and
                (Test-VerifierStrictStringValue $lease.ClaimState) -and
                $lease.ClaimState -eq 'released' -and
                $claimAbsenceKnown -and $claimAbsent) {
            $true
        } else { $false }
        try {
            # Always take a current listener observation after the final claim
            # release. A prior claim/tombstone proof is not a substitute for
            # observing the port now: a newer owner may have appeared during
            # cleanup. If this lease has a recorded owner identity, the shared
            # released-listener validator proves that identity is absent while
            # permitting a genuinely different replacement. A lease that was
            # never bound has no old listener identity to confuse with the
            # replacement; its exact current user-process observation is the
            # bounded replacement identity.
            $finalPreviewOwner = $null
            $finalAuthorizationProofProperty = $lease.PSObject.Properties[
                'AuthorizationProof']
            if ($null -ne $Context.Server -and
                    $Context.Server.PSObject.Properties['Lease'] -and
                    [object]::ReferenceEquals($Context.Server.Lease, $lease) -and
                    $null -ne $finalAuthorizationProofProperty -and
                    $null -ne $finalAuthorizationProofProperty.Value -and
                    (Test-VerifierRunOwnedPreviewHttpSysAuthorizationProof `
                        $finalAuthorizationProofProperty.Value $Context `
                        $Context.Server ([int]$lease.Port))) {
                $finalPreviewOwner = $Context.Server
            }
            $finalInspection = if ($null -ne $finalPreviewOwner) {
                Get-VerifierLoopbackListenerRecords ([int]$lease.Port) $Context $finalPreviewOwner
            } else {
                Get-VerifierLoopbackListenerRecords ([int]$lease.Port)
            }
            if (-not $finalInspection.Success -or -not $finalInspection.Known) {
                [void]$errors.Add("Port $($lease.Port) listener inspection was not positively proven after cleanup.")
            } elseif ($finalInspection.HasListeners) {
                $boundProcessId = [int]$lease.BoundProcessId
                $boundProcessStartTicks = [long]$lease.BoundProcessStartTicks
                $listenerProcessId = [int]$lease.ListenerProcessId
                $listenerProcessStartTicks = [long]$lease.ListenerProcessStartTicks
                $expectedProcessId = if ($boundProcessId -gt 0) { $boundProcessId } else { $listenerProcessId }
                $expectedProcessStartTicks = if ($boundProcessId -gt 0) {
                    $boundProcessStartTicks
                } else { $listenerProcessStartTicks }
                if ($expectedProcessId -gt 0) {
                    $expectedParentProcessId = 0
                    $expectedParentProcessStartTicks = 0L
                    $expectedCommandLine = ''
                    $expectedScript = ''
                    $expectedNonce = ''
                    $expectedProfile = ''
                    if ($null -ne $Context.Server -and
                            $Context.Server.PSObject.Properties['Lease'] -and
                            [object]::ReferenceEquals($Context.Server.Lease, $lease)) {
                        $expectedParentProcessId = [int]$Context.Server.ProcessParentProcessId
                        $expectedParentProcessStartTicks = [long]$Context.Server.ProcessParentProcessStartTicks
                        $expectedCommandLine = [string]$Context.Server.ProcessCommandLine
                        $expectedScript = [string]$Context.Server.Script
                        $expectedNonce = [string]$Context.Server.Nonce
                        $expectedProfile = if ($Context.Server.PSObject.Properties['Profile']) {
                            [string]$Context.Server.Profile
                        } else { '' }
                    }
                    $releasedListener = Confirm-VerifierReleasedListener ([int]$lease.Port) `
                        $expectedProcessId $expectedProcessStartTicks $expectedCommandLine `
                        $expectedParentProcessId $expectedScript ([string]$lease.RunId) `
                        $expectedNonce $expectedProfile $expectedParentProcessStartTicks
                    if (-not (Test-VerifierStrictBooleanValue $releasedListener.QueryProven) -or
                            -not $releasedListener.QueryProven -or
                            -not (Test-VerifierStrictBooleanValue $releasedListener.OldOwnerAbsent) -or
                            -not $releasedListener.OldOwnerAbsent) {
                        [void]$errors.Add("Port $($lease.Port) replacement listener did not prove the released owner absent.")
                    }
                } elseif (-not $releasedLifecycleProven) {
                    [void]$errors.Add("Port $($lease.Port) had a listener before its exact release lifecycle was proven.")
                }
                # When no owner was ever bound to this lease, the fully
                # validated current user-process listener is a replacement
                # identity, not an old owner. Do not kill, adopt, or mutate it.
            }
        } catch {
            [void]$errors.Add("Port $($lease.Port) listener inspection failed after cleanup: $(Get-VerifierErrorMessage $_)")
        }
    }
    if ($Context.Server -and $Context.Server.Owner -eq 'run' -and
            $Context.Server.CleanupResult -ne 'complete') {
        [void]$errors.Add('Run-owned preview/server cleanup remained incomplete after the final drain.')
    }
    $Context.CleanupState = if ($errors.Count -eq 0) { 'complete' } else { 'infrastructure-failure' }
    $Context.CleanupCompletedUtc = Get-VerifierUtcText
    foreach ($message in $errors) { [void]$Context.CleanupErrors.Add($message) }
    try { Write-VerifierManifest $Context } catch {
        [void]$errors.Add((Get-VerifierErrorMessage $_))
        $Context.CleanupState = 'infrastructure-failure'
    }
    return [pscustomobject]@{ Success = $errors.Count -eq 0; Errors = @($errors) }
}

function Resolve-VerifierChildExitCode([bool]$InvocationSucceeded, $LastExitCode) {
    # ExitCode is an observed Process.ExitCode value. Keep this boundary raw
    # and exact: numeric strings, fractions, booleans, and arbitrary objects
    # are not process exit evidence and must remain infrastructure failures.
    if (-not (Test-VerifierStrictIntegralValue $LastExitCode 0 ([int]::MaxValue))) {
        return 2
    }
    $exitCode = [int]$LastExitCode
    if ($exitCode -eq 2) { return 2 }
    if ($exitCode -eq 1) { return 1 }
    if ($exitCode -eq 0 -and $InvocationSucceeded) { return 0 }
    return 2
}

function Test-VerifierChildContract([int]$ExpectedExit, [int]$ActualExit,
        [bool]$PrintedFail, [bool]$PositiveRoute) {
    if ($PositiveRoute -and $ExpectedExit -eq 0 -and $ActualExit -eq 0 -and $PrintedFail) {
        return [pscustomobject]@{ Pass = $false; ExitCode = 1; Reason = 'positive child printed FAIL while exiting 0' }
    }
    $mappedExit = 2
    if ($ActualExit -eq $ExpectedExit) {
        $mappedExit = 0
    } elseif ($ExpectedExit -eq 2 -or $ActualExit -eq 2) {
        # Infrastructure severity is monotonic. A child expected to report
        # infrastructure failure must never be reclassified as application
        # failure merely because its observed exit was 0 or 1.
        $mappedExit = 2
    } elseif ($ActualExit -eq 1 -or $ActualExit -eq 0) {
        $mappedExit = 1
    }
    return [pscustomobject]@{
        Pass = ($ActualExit -eq $ExpectedExit)
        ExitCode = $mappedExit
        Reason = if ($ActualExit -eq $ExpectedExit) { '' } else { "expected $ExpectedExit, got $ActualExit" }
    }
}

Export-ModuleMember -Function @(
    'Get-VerifierErrorMessage', 'Throw-VerifierInfrastructure',
    'Test-VerifierInfrastructureError', 'Merge-VerifierFailureExitCode',
    'Get-VerifierProcessStartTicks',
    'Get-VerifierCurrentProcessStartTicks',
    'Get-VerifierCanonicalWindowsPath', 'Get-VerifierFullPath',
    'Resolve-VerifierBrowserPath',
    'Get-VerifierPhysicalExistingPath', 'Get-VerifierCanonicalPhysicalPath',
    'Test-VerifierPhysicalChildPath',
    'Assert-VerifierPhysicalOwnedPath',
    'Assert-VerifierNoReparseAncestors', 'Assert-VerifierNoReparseTree',
    'Remove-VerifierOwnedTree',
    'Get-VerifierRepositoryIdentity', 'Get-VerifierUtcText',
    'Get-VerifierPortMutexName',
    'Test-VerifierStrictStringValue', 'Test-VerifierStrictBooleanValue',
    'Test-VerifierStrictIntegralValue',
    'Get-VerifierFileSha256', 'Get-VerifierExecutionTreeProvenance',
    'ConvertTo-VerifierStrictTimestampText', 'ConvertFrom-VerifierDurableJson',
    'Assert-VerifierTimeoutSeconds',
    'Test-VerifierSupportedPortLeaseKind',
    'Assert-VerifierLeaseTransition',
    'Assert-VerifierDurableProcessIdentityTuple',
    'Test-VerifierListenerOwnerTuple',
    'Assert-VerifierDurableListenerOwnerTuple',
    'Assert-VerifierDurableLeaseRecord',
    'Assert-VerifierDurableLeaseTerminalFields',
    'Assert-VerifierDurableLeaseTerminal',
    'Test-VerifierDurableLeaseTerminal',
    'Assert-VerifierDurableServerLease',
    'Assert-VerifierDurableBrowserSession',
    'Assert-VerifierSerializedLeaseRecord',
    'Assert-VerifierSerializedBrowserSessionRecord',
    'Assert-VerifierSerializedServerRecord',
    'Test-VerifierSerializedListenerOwnerSchema',
    'Test-VerifierSerializedLeaseTerminal',
    'Assert-VerifierDurableManifestContext',
    'ConvertTo-VerifierWindowsArgument', 'ConvertTo-VerifierArgumentString',
    'Assert-VerifierArgumentArray', 'Assert-VerifierProcessInvocationBoundary',
    'Start-VerifierProcess', 'Complete-VerifierProcessOutputCapture',
    'Invoke-VerifierBoundedProcess',
    'New-VerifierRunContext', 'New-VerifierPortLease',
    'Release-VerifierPortLease', 'Confirm-VerifierPortLeaseBound',
    'Test-VerifierChildPath',
    'Test-VerifierCommandLineSwitch', 'Test-VerifierCommandLinePath',
    'Test-VerifierCommandLineCanonicalPathToken',
    'Get-VerifierCommandLineSwitchPresence',
    'Test-VerifierCommandLineEquivalent',
    'New-VerifierBrowserLease', 'New-VerifierBrowserSession',
    'Complete-VerifierBrowserSession', 'Connect-VerifierCdpSocket',
    'Register-VerifierEvidenceArtifact',
    'Set-VerifierCallerOwnedPreview', 'Start-VerifierOwnedPreview',
    'Test-VerifierPreviewCleanupReadiness',
    'Test-VerifierLeaseNeedsCleanup',
    'Complete-VerifierRun', 'Resolve-VerifierChildExitCode',
    'Test-VerifierChildContract', 'Test-VerifierProcessIdentity',
    'Test-VerifierPreviewProcessIdentity',
    'Test-VerifierConfiguredExecutableIdentity',
    'Select-VerifierRelevantProcessRecords', 'Get-VerifierBrowserProcessSnapshot',
    'Get-VerifierBrowserOwnershipSnapshot',
    'Get-VerifierNativeProcessSnapshot',
    'Test-VerifierProcessInspectionFallbackError',
    'Get-VerifierProcessSnapshotWithFallback',
    'Get-VerifierProcessRecordsByIdWithFallback',
    'Get-VerifierProcessRecordsByParentWithFallback',
    'Get-VerifierPreviewAdoptionCandidateRecords',
    'Get-VerifierDescendantCandidateRecords', 'Test-VerifierDescendantOwnership',
    'Test-VerifierDescendantExecutableIdentity',
    'Get-VerifierDescendantProcessRecords',
    'Stop-VerifierVerifiedProcessExactly',
    'Test-VerifierCurrentProcessRecordMatches',
    'Get-VerifierCurrentProcessIdentity',
    'Get-VerifierCurrentProcessRecordById',
    'Confirm-VerifierRecordedProcessAbsent',
    'Confirm-VerifierReleasedListener',
    'Get-VerifierLoopbackListenerRecords', 'Parse-VerifierNetstatListenerOutput',
    'Test-VerifierRunOwnedPreviewHttpSysAuthorization',
    'Test-VerifierKernelTransportListenerRecord',
    'Test-VerifierRunOwnedPreviewHttpSysListener',
    'Write-VerifierManifest', 'Test-VerifierCanonicalWindowsPathValue',
    'Get-VerifierProfileReferenceRecords', 'Assert-VerifierProfileSnapshotQuiescent',
    'Assert-VerifierBrowserProfileIsQuiescent'
)

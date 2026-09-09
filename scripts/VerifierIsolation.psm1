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
$script:VerifierBrowserDrainPendingNaturalExitMarker = [object]::new()
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
            ${env:ProgramFiles(x86)},
            # A 32-bit host can omit ProgramW6432/ProgramFiles(x86) even
            # when Edge is installed there.  The special-folder APIs expose
            # both canonical install roots independently of the launching
            # shell's environment block.
            [Environment]::GetFolderPath([Environment+SpecialFolder]::ProgramFiles),
            [Environment]::GetFolderPath([Environment+SpecialFolder]::ProgramFilesX86)
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
        foreach ($file in @(Get-ChildItem -LiteralPath $categoryRoot -Recurse -File -ErrorAction Stop |
                Where-Object {
                    $_.Extension -ine '.pyc' -and
                    $_.FullName -notmatch '(\\|/)__pycache__(\\|/)'
                })) {
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
    $receiptProperty = $Session.PSObject.Properties['recoveryReceipt']
    if ($null -ne $receiptProperty -and $null -ne $receiptProperty.Value) {
        $receipt = $receiptProperty.Value
        Assert-VerifierSerializedObject $receipt ($Label + ' recovery receipt')
        foreach ($name in @('protocol', 'authorityToken', 'issuedUtc', 'state',
                'boundUtc', 'closedUtc', 'closeAttemptedUtc', 'runId',
                'repositoryIdentity', 'worktreeRoot', 'routeId', 'routeName',
                'browserPath', 'profile', 'leaseId', 'rootProcessCommandLine')) {
            $allowEmpty = $name -in @('boundUtc', 'closedUtc', 'closeAttemptedUtc',
                'rootProcessCommandLine')
            [void](Assert-VerifierSerializedString $receipt $name `
                ($Label + ' recovery receipt') -AllowEmpty:$allowEmpty)
        }
        foreach ($number in @(
                [pscustomobject]@{ Name = 'cdpPort'; Minimum = 1L; Maximum = 65535L }
                [pscustomobject]@{ Name = 'rootProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
                [pscustomobject]@{ Name = 'rootProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
                [pscustomobject]@{ Name = 'rootParentProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
                [pscustomobject]@{ Name = 'rootParentProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
                [pscustomobject]@{ Name = 'listenerProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
                [pscustomobject]@{ Name = 'listenerProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
            )) {
            [void](Assert-VerifierSerializedIntegral $receipt $number.Name $number.Minimum `
                $number.Maximum ($Label + ' recovery receipt'))
        }
        [void](Assert-VerifierSerializedBoolean $receipt 'closeAttempted' `
            ($Label + ' recovery receipt'))
        foreach ($timestamp in @(
                [pscustomobject]@{ Name = 'issuedUtc'; Required = $true },
                [pscustomobject]@{ Name = 'boundUtc'; Required = ($receipt.state -eq 'bound' -or
                    ($receipt.state -eq 'closed' -and -not [String]::IsNullOrWhiteSpace($receipt.boundUtc))) },
                [pscustomobject]@{ Name = 'closedUtc'; Required = ($receipt.state -eq 'closed') },
                [pscustomobject]@{ Name = 'closeAttemptedUtc'; Required = [bool]$receipt.closeAttempted }
            )) {
            $timestampValue = [string]$receipt.$($timestamp.Name)
            if ($timestamp.Required -and [String]::IsNullOrWhiteSpace($timestampValue)) {
                Throw-VerifierInfrastructure "$Label recovery receipt omitted required timestamp '$($timestamp.Name)'."
            }
            if (-not [String]::IsNullOrWhiteSpace($timestampValue)) {
                [void](ConvertTo-VerifierStrictTimestampText $timestampValue $false `
                    ($Label + ' recovery receipt ' + $timestamp.Name))
            }
        }
        if ($receipt.protocol -cne 'troubleshootjs-verifier-browser-recovery-v1' -or
                -not (Test-VerifierBrowserRecoveryAuthorityToken $receipt.authorityToken) -or
                $receipt.state -cnotin @('issued', 'bound', 'closed') -or
                $receipt.runId -cne $Session.runId -or
                $receipt.repositoryIdentity -cne $Session.repositoryIdentity -or
                -not (Test-VerifierCanonicalWindowsPathValue $receipt.worktreeRoot $Session.worktreeRoot) -or
                $receipt.routeId -cne $Session.routeId -or
                $receipt.routeName -cne $Session.routeName -or
                -not (Test-VerifierCanonicalWindowsPathValue $receipt.browserPath $Session.browserPath) -or
                -not (Test-VerifierCanonicalWindowsPathValue $receipt.profile $Session.profile) -or
                $receipt.leaseId -eq '' -or [int]$receipt.cdpPort -ne [int]$Session.cdpPort) {
            Throw-VerifierInfrastructure "$Label recovery receipt did not match the serialized browser session identity."
        }
        $hasSerializedRootTuple = ([int]$receipt.rootProcessId -gt 0 -or
            [long]$receipt.rootProcessStartTicks -gt 0 -or
            [int]$receipt.rootParentProcessId -gt 0 -or
            [long]$receipt.rootParentProcessStartTicks -gt 0 -or
            -not [String]::IsNullOrWhiteSpace($receipt.rootProcessCommandLine))
        $hasSerializedListenerTuple = ([int]$receipt.listenerProcessId -gt 0 -or
            [long]$receipt.listenerProcessStartTicks -gt 0)
        $serializedBoundState = ($receipt.state -eq 'bound' -or
            ($receipt.state -eq 'closed' -and
             -not [String]::IsNullOrWhiteSpace($receipt.boundUtc)))
        if (($receipt.state -eq 'issued' -and
                ($hasSerializedRootTuple -or $hasSerializedListenerTuple -or
                 -not [String]::IsNullOrWhiteSpace($receipt.boundUtc) -or
                 -not [String]::IsNullOrWhiteSpace($receipt.closedUtc) -or
                 [bool]$receipt.closeAttempted)) -or
                ($receipt.state -eq 'bound' -and
                 -not [String]::IsNullOrWhiteSpace($receipt.closedUtc)) -or
                ($serializedBoundState -and
                 (-not $hasSerializedRootTuple -or -not $hasSerializedListenerTuple)) -or
                ($receipt.state -eq 'closed' -and $serializedBoundState -and
                 -not [bool]$receipt.closeAttempted) -or
                ($receipt.state -eq 'closed' -and -not $serializedBoundState -and
                 ($hasSerializedRootTuple -or $hasSerializedListenerTuple -or
                  [bool]$receipt.closeAttempted))) {
            Throw-VerifierInfrastructure "$Label recovery receipt carried contradictory bound/closed lifecycle evidence."
        }
    if ($Session.cleanupResult -eq 'complete' -and $receipt.state -ne 'closed') {
        Throw-VerifierInfrastructure "$Label serialized a completed session without a closed recovery receipt."
    }
    $containmentLaunchProperty = $Session.PSObject.Properties['containmentLaunch']
    if ($null -eq $containmentLaunchProperty -or $null -eq $containmentLaunchProperty.Value) {
        # Older completed/bound evidence predates the launch ledger.  Preserve
        # that narrow read-only compatibility, but never let an issued session
        # without a launch record become eligible for recovery.
        if ($null -ne $receipt -and $receipt.state -in @('bound', 'closed') -and
                [int]$Session.processId -gt 0) {
            return
        }
        Throw-VerifierInfrastructure "$Label omitted its durable containment-launch record."
    }
    $containmentLaunch = $containmentLaunchProperty.Value
    Assert-VerifierSerializedObject $containmentLaunch ($Label + ' containment launch')
    foreach ($name in @('protocol', 'jobName', 'state', 'launchedUtc')) {
        $allowEmpty = $name -eq 'launchedUtc'
        [void](Assert-VerifierSerializedString $containmentLaunch $name `
            ($Label + ' containment launch') -AllowEmpty:$allowEmpty)
    }
    [void](Assert-VerifierSerializedIntegral $containmentLaunch 'launchProcessId' 0 `
        ([int]::MaxValue) ($Label + ' containment launch'))
    if ($containmentLaunch.protocol -cne
            'troubleshootjs-verifier-browser-containment-launch-v1' -or
            [String]::IsNullOrWhiteSpace([string]$containmentLaunch.jobName) -or
            $containmentLaunch.state -cnotin @('unlaunched', 'launch-pending', 'launched')) {
        Throw-VerifierInfrastructure "$Label containment launch carried an unknown protocol, job name, or lifecycle state."
    }
    if ($containmentLaunch.state -in @('unlaunched', 'launch-pending')) {
        if ([int]$containmentLaunch.launchProcessId -ne 0 -or
                -not [String]::IsNullOrWhiteSpace([string]$containmentLaunch.launchedUtc)) {
            Throw-VerifierInfrastructure "$Label unlaunched/pending containment record carried a process identity or timestamp."
        }
        if ($containmentLaunch.state -eq 'launch-pending' -and
                ($receipt.state -ne 'issued' -or [int]$Session.processId -ne 0 -or
                 [long]$Session.processStartTicks -ne 0 -or
                 [int]$Session.processParentProcessId -ne 0 -or
                 [long]$Session.processParentProcessStartTicks -ne 0 -or
                 -not [String]::IsNullOrWhiteSpace([string]$Session.processCommandLine))) {
            Throw-VerifierInfrastructure "$Label pending containment record did not retain one exact issued, rootless recovery state."
        }
    } else {
        if ([int]$containmentLaunch.launchProcessId -le 0 -or
                [String]::IsNullOrWhiteSpace([string]$containmentLaunch.launchedUtc)) {
            Throw-VerifierInfrastructure "$Label launched containment record omitted its exact PID or timestamp."
        }
        [void](ConvertTo-VerifierStrictTimestampText $containmentLaunch.launchedUtc $false `
            ($Label + ' containment launch launchedUtc') -AllowJsonDateTime)
        if ([int]$Session.processId -gt 0 -and
                [int]$containmentLaunch.launchProcessId -ne [int]$Session.processId) {
            Throw-VerifierInfrastructure "$Label containment launch PID disagreed with its complete browser root identity."
        }
    }
    if ($null -ne $receipt -and $receipt.state -in @('bound', 'closed') -and
            ($containmentLaunch.state -ne 'launched' -or
             [int]$Session.processId -le 0 -or
             [int]$containmentLaunch.launchProcessId -ne [int]$Session.processId)) {
        Throw-VerifierInfrastructure "$Label bound/closed receipt did not retain its exact launched containment root."
    }
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

function New-VerifierRetainedBrowserRecoveryContext([string]$ManifestPath) {
    # This is intentionally a narrow deserializer, not a general retained-run
    # importer. It accepts exactly one failed/bound CDP browser session with
    # no preview owner, then reconstructs only the live objects required to
    # send Browser.close and finish the exact existing lease transaction.
    if (-not (Test-VerifierStrictStringValue $ManifestPath) -or
            [String]::IsNullOrWhiteSpace($ManifestPath)) {
        Throw-VerifierInfrastructure 'Retained browser recovery requires one exact non-empty manifest path.'
    }
    $requestedManifestPath = Get-VerifierFullPath $ManifestPath
    Assert-VerifierNoReparseAncestors $requestedManifestPath
    if (-not (Test-Path -LiteralPath $requestedManifestPath -PathType Leaf -ErrorAction Stop)) {
        Throw-VerifierInfrastructure "Retained browser recovery manifest was not an existing file: $requestedManifestPath"
    }
    try {
        $manifestText = Get-Content -LiteralPath $requestedManifestPath -Raw -ErrorAction Stop
        $manifest = ConvertFrom-VerifierDurableJson $manifestText
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not read retained browser recovery manifest: ' +
            (Get-VerifierErrorMessage $_))
    }
    Assert-VerifierSerializedObject $manifest 'retained browser recovery manifest'
    foreach ($name in @('protocol', 'runId', 'repositoryIdentity', 'worktreeRoot',
            'runRoot', 'runNamespaceRoot', 'evidenceDirectory',
            'evidenceNamespaceRoot', 'manifestPath', 'createdUtc', 'baseUrl',
            'previewNonce')) {
        $allowEmpty = $name -eq 'baseUrl'
        [void](Assert-VerifierSerializedString $manifest $name `
            'retained browser recovery manifest' -AllowEmpty:$allowEmpty)
    }
    [void](ConvertTo-VerifierStrictTimestampText $manifest.createdUtc $false `
        'retained browser recovery manifest createdUtc' -AllowJsonDateTime)
    if ($manifest.protocol -cne 'troubleshootjs-verifier-run-v1' -or
            $manifest.baseUrl -cne '') {
        Throw-VerifierInfrastructure 'Retained browser recovery manifest carried an unsupported protocol or active preview URL.'
    }
    foreach ($collectionName in @('leases', 'browserSessions', 'artifacts')) {
        $property = $manifest.PSObject.Properties[$collectionName]
        if ($null -eq $property -or $null -eq $property.Value -or
                $property.Value -is [string] -or
                -not ($property.Value -is [Collections.IEnumerable])) {
            Throw-VerifierInfrastructure "Retained browser recovery manifest omitted its exact '$collectionName' collection."
        }
    }
    $cleanupProperty = $manifest.PSObject.Properties['cleanup']
    if ($null -eq $cleanupProperty -or $null -eq $cleanupProperty.Value) {
        Throw-VerifierInfrastructure 'Retained browser recovery manifest omitted its cleanup record.'
    }
    $cleanup = $cleanupProperty.Value
    Assert-VerifierSerializedObject $cleanup 'retained browser recovery cleanup record'
    [void](Assert-VerifierSerializedString $cleanup 'state' `
        'retained browser recovery cleanup record')
    [void](Assert-VerifierSerializedString $cleanup 'completedUtc' `
        'retained browser recovery cleanup record' -AllowEmpty:$true)
    $cleanupErrorsProperty = $cleanup.PSObject.Properties['errors']
    if ($null -eq $cleanupErrorsProperty -or $null -eq $cleanupErrorsProperty.Value -or
            $cleanupErrorsProperty.Value -is [string] -or
            -not ($cleanupErrorsProperty.Value -is [Collections.IEnumerable])) {
        Throw-VerifierInfrastructure 'Retained browser recovery cleanup record omitted its exact errors collection.'
    }
    if ($cleanup.state -cnotin @('pending', 'infrastructure-failure') -or
            ($cleanup.state -ceq 'pending' -and
             -not [String]::IsNullOrWhiteSpace([string]$cleanup.completedUtc))) {
        Throw-VerifierInfrastructure 'Retained browser recovery accepts only a pending or failed, nonterminal cleanup record.'
    }
    if (-not [String]::IsNullOrWhiteSpace([string]$cleanup.completedUtc)) {
        [void](ConvertTo-VerifierStrictTimestampText $cleanup.completedUtc $false `
            'retained browser recovery cleanup completedUtc' -AllowJsonDateTime)
    }
    foreach ($errorText in @($cleanup.errors)) {
        if (-not (Test-VerifierStrictStringValue $errorText)) {
            Throw-VerifierInfrastructure 'Retained browser recovery cleanup errors contained a malformed value.'
        }
    }
    $leases = @($manifest.leases)
    $sessions = @($manifest.browserSessions)
    if ($leases.Count -ne 1 -or $sessions.Count -ne 1) {
        Throw-VerifierInfrastructure 'Retained browser recovery requires exactly one retained CDP lease and browser session.'
    }
    $serializedLease = $leases[0]
    $serializedSession = $sessions[0]
    Assert-VerifierSerializedLeaseRecord $serializedLease 'retained browser recovery lease'
    Assert-VerifierSerializedBrowserSessionRecord $serializedSession `
        'retained browser recovery session'
    $serverProperty = $manifest.PSObject.Properties['server']
    if ($null -eq $serverProperty -or $null -eq $serverProperty.Value) {
        Throw-VerifierInfrastructure 'Retained browser recovery manifest omitted its ownerless server record.'
    }
    Assert-VerifierSerializedServerRecord $serverProperty.Value `
        'retained browser recovery server'
    if ($serverProperty.Value.owner -cne 'none') {
        Throw-VerifierInfrastructure 'Retained browser recovery refuses a manifest that owns a preview/server resource.'
    }
    $lease = ConvertFrom-VerifierSerializedLeaseRecord $serializedLease `
        'retained browser recovery lease'
    $receiptProperty = $serializedSession.PSObject.Properties['recoveryReceipt']
    if ($null -eq $receiptProperty -or $null -eq $receiptProperty.Value -or
            $receiptProperty.Value -is [array]) {
        Throw-VerifierInfrastructure 'Retained browser recovery requires one durable recovery receipt.'
    }
    $serializedReceipt = $receiptProperty.Value
    $recoveryMode = ''
    $boundRecovery = ($lease.Kind -ceq 'cdp' -and $lease.Status -ceq 'bound' -and
        $lease.ClaimState -ceq 'bound' -and $lease.ReleaseState -ceq 'active' -and
        $lease.ReleaseJournalState -ceq 'active' -and $lease.Registered -and
        $lease.ProcessProofRequired -and
        $serializedSession.cdpLeasePath -ceq $lease.Path -and
        [int]$serializedSession.cdpPort -eq [int]$lease.Port -and
        [int]$serializedSession.processId -eq [int]$lease.BoundProcessId -and
        [long]$serializedSession.processStartTicks -eq
            [long]$lease.BoundProcessStartTicks -and
        [int]$lease.ListenerProcessId -eq [int]$lease.BoundProcessId -and
        [long]$lease.ListenerProcessStartTicks -eq
            [long]$lease.BoundProcessStartTicks -and
        $serializedReceipt.state -ceq 'bound' -and
        -not [bool]$serializedReceipt.closeAttempted -and
        $serializedSession.status -cin @('cleanup-failed', 'attached') -and
        $serializedSession.cleanupResult -cin @('infrastructure-failure', 'pending'))
    $issuedContainedLaunch = ($null -ne $serializedSession.PSObject.Properties['containmentLaunch'] -and
        $null -ne $serializedSession.containmentLaunch -and
        (($serializedSession.containmentLaunch.state -ceq 'launched' -and
          [int]$serializedSession.containmentLaunch.launchProcessId -gt 0) -or
         ($serializedSession.containmentLaunch.state -ceq 'launch-pending' -and
          [int]$serializedSession.containmentLaunch.launchProcessId -eq 0 -and
          [String]::IsNullOrWhiteSpace([string]$serializedSession.containmentLaunch.launchedUtc))))
    $issuedContainedRecovery = ($lease.Kind -ceq 'cdp' -and $lease.Status -ceq 'leased' -and
        $lease.ClaimState -ceq 'held' -and $lease.ReleaseState -ceq 'active' -and
        $lease.ReleaseJournalState -ceq 'active' -and $lease.Registered -and
        -not $lease.ProcessProofRequired -and
        [int]$lease.BoundProcessId -eq 0 -and
        [long]$lease.BoundProcessStartTicks -eq 0 -and
        [int]$lease.ListenerProcessId -eq 0 -and
        [long]$lease.ListenerProcessStartTicks -eq 0 -and
        $serializedSession.cdpLeasePath -ceq $lease.Path -and
        [int]$serializedSession.cdpPort -eq [int]$lease.Port -and
        [int]$serializedSession.processId -eq 0 -and
        [long]$serializedSession.processStartTicks -eq 0 -and
        [int]$serializedSession.processParentProcessId -eq 0 -and
        [long]$serializedSession.processParentProcessStartTicks -eq 0 -and
        [String]::IsNullOrWhiteSpace([string]$serializedSession.processCommandLine) -and
        $serializedReceipt.state -ceq 'issued' -and
        -not [bool]$serializedReceipt.closeAttempted -and
        $serializedSession.status -cin @('leased', 'startup-failed', 'cleanup-failed') -and
        $serializedSession.cleanupResult -cin @('pending', 'infrastructure-failure') -and
        $issuedContainedLaunch)
    if ($boundRecovery) {
        $recoveryMode = 'bound'
    } elseif ($issuedContainedRecovery) {
        $recoveryMode = 'issued-contained'
    } else {
        Throw-VerifierInfrastructure 'Retained browser recovery lease/session association was neither one exact bound tuple nor one issued contained-launch tuple.'
    }

    $worktreeRoot = Get-VerifierFullPath $manifest.worktreeRoot
    $runRoot = Get-VerifierFullPath $manifest.runRoot
    $runNamespaceRoot = Get-VerifierFullPath $manifest.runNamespaceRoot
    $evidenceDirectory = Get-VerifierFullPath $manifest.evidenceDirectory
    $evidenceNamespaceRoot = Get-VerifierFullPath $manifest.evidenceNamespaceRoot
    $expectedManifestPath = Get-VerifierFullPath (Join-Path $runRoot 'manifest.json')
    $expectedPortLeaseRoot = Get-VerifierFullPath (Join-Path $runRoot 'port-leases')
    $expectedNamespaceRoot = Get-VerifierFullPath (Join-Path `
        (Join-Path (Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS') 'verify') `
        ([string]$manifest.repositoryIdentity))
    if (-not (Test-VerifierCanonicalWindowsPathValue $requestedManifestPath $expectedManifestPath) -or
            -not (Test-VerifierCanonicalWindowsPathValue $manifest.manifestPath $expectedManifestPath) -or
            -not (Test-VerifierCanonicalWindowsPathValue $runNamespaceRoot $expectedNamespaceRoot) -or
            -not (Test-VerifierCanonicalWindowsPathValue $lease.WorktreeRoot $worktreeRoot) -or
            -not (Test-VerifierCanonicalWindowsPathValue $serializedSession.worktreeRoot $worktreeRoot) -or
            $manifest.repositoryIdentity -cne (Get-VerifierRepositoryIdentity $worktreeRoot)) {
        Throw-VerifierInfrastructure 'Retained browser recovery manifest did not match its exact physical worktree/run namespace identity.'
    }
    Assert-VerifierNoReparseAncestors $runRoot
    Assert-VerifierNoReparseAncestors $evidenceNamespaceRoot
    [void](Assert-VerifierPhysicalOwnedPath $runNamespaceRoot $runRoot)
    [void](Assert-VerifierPhysicalOwnedPath $runRoot $expectedManifestPath)
    [void](Assert-VerifierPhysicalOwnedPath $runRoot $expectedPortLeaseRoot)
    [void](Assert-VerifierPhysicalOwnedPath $expectedPortLeaseRoot $lease.Path)
    [void](Assert-VerifierPhysicalOwnedPath $runRoot $serializedSession.profile)
    [void](Assert-VerifierPhysicalOwnedPath $evidenceNamespaceRoot $evidenceDirectory)
    if (-not (Test-VerifierCanonicalWindowsPathValue $serializedSession.profile $lease.ProfilePath)) {
        Throw-VerifierInfrastructure 'Retained browser recovery session and lease disagreed on the owned profile path.'
    }

    $receipt = $receiptProperty.Value
    $liveReceipt = [pscustomobject]@{
        Protocol = [string]$receipt.protocol
        AuthorityToken = [string]$receipt.authorityToken
        IssuedUtc = ConvertTo-VerifierStrictTimestampText $receipt.issuedUtc $false `
            'retained browser recovery receipt issuedUtc' -AllowJsonDateTime
        State = [string]$receipt.state
        BoundUtc = ConvertTo-VerifierStrictTimestampText $receipt.boundUtc $true `
            'retained browser recovery receipt boundUtc' -AllowJsonDateTime
        ClosedUtc = ConvertTo-VerifierStrictTimestampText $receipt.closedUtc $true `
            'retained browser recovery receipt closedUtc' -AllowJsonDateTime
        CloseAttempted = [bool]$receipt.closeAttempted
        CloseAttemptedUtc = ConvertTo-VerifierStrictTimestampText `
            $receipt.closeAttemptedUtc $true `
            'retained browser recovery receipt closeAttemptedUtc' -AllowJsonDateTime
        RunId = [string]$receipt.runId
        RepositoryIdentity = [string]$receipt.repositoryIdentity
        WorktreeRoot = [string]$receipt.worktreeRoot
        RouteId = [string]$receipt.routeId
        RouteName = [string]$receipt.routeName
        BrowserPath = [string]$receipt.browserPath
        Profile = [string]$receipt.profile
        LeaseId = [string]$receipt.leaseId
        CdpPort = [int]$receipt.cdpPort
        RootProcessId = [int]$receipt.rootProcessId
        RootProcessStartTicks = [long]$receipt.rootProcessStartTicks
        RootParentProcessId = [int]$receipt.rootParentProcessId
        RootParentProcessStartTicks = [long]$receipt.rootParentProcessStartTicks
        RootProcessCommandLine = [string]$receipt.rootProcessCommandLine
        ListenerProcessId = [int]$receipt.listenerProcessId
        ListenerProcessStartTicks = [long]$receipt.listenerProcessStartTicks
    }
    $liveLease = [pscustomobject]@{
        LeaseId = [string]$lease.LeaseId; Kind = [string]$lease.Kind
        Port = [int]$lease.Port; Path = [string]$lease.Path
        RunId = [string]$lease.RunId; RepositoryIdentity = [string]$lease.RepositoryIdentity
        WorktreeRoot = [string]$lease.WorktreeRoot; Status = [string]$lease.Status
        Registered = [bool]$lease.Registered; ClaimName = [string]$lease.ClaimName
        ClaimState = [string]$lease.ClaimState; ClaimOwnerPid = [int]$lease.ClaimOwnerPid
        ClaimOwnerStartTicks = [long]$lease.ClaimOwnerStartTicks
        BoundProcessId = [int]$lease.BoundProcessId
        BoundProcessStartTicks = [long]$lease.BoundProcessStartTicks
        ListenerProcessId = [int]$lease.ListenerProcessId
        ListenerProcessStartTicks = $lease.ListenerProcessStartTicks
        ListenerOwnerKind = [string]$lease.ListenerOwnerKind
        ListenerOwnerProof = [string]$lease.ListenerOwnerProof
        ListenerOwnerEvidence = [string]$lease.ListenerOwnerEvidence
        BindValidatedUtc = [string]$lease.BindValidatedUtc
        ReleasedUtc = [string]$lease.ReleasedUtc
        ClaimMutex = $null; AuthorizationProof = $null
        ProfilePath = [string]$lease.ProfilePath; OwnerType = 'none'
        ProfileInspectionFailed = $false; BrowserPath = [string]$lease.BrowserPath
        ReleaseState = [string]$lease.ReleaseState
        ReleaseJournalState = [string]$lease.ReleaseJournalState
        ReleaseBlocked = [bool]$lease.ReleaseBlocked
        ReleaseBlockReason = [string]$lease.ReleaseBlockReason
        MutexReleased = [bool]$lease.MutexReleased
        ListenerInspectionSuccess = [bool]$lease.ListenerInspectionSuccess
        ListenerInspectionKnown = [bool]$lease.ListenerInspectionKnown
        ListenerHasListeners = $lease.ListenerHasListeners
        ListenerAbsent = $lease.ListenerAbsent
        ListenerInspectionUtc = [string]$lease.ListenerInspectionUtc
        ProcessProofRequired = [bool]$lease.ProcessProofRequired
        ProcessTerminationProven = [bool]$lease.ProcessTerminationProven
        ProcessAbsent = [bool]$lease.ProcessAbsent
        RetainedRecoveryAuthority = $null
    }
    $liveContainmentLaunch = if ($null -eq $serializedSession.PSObject.Properties['containmentLaunch'] -or
            $null -eq $serializedSession.containmentLaunch) {
        $null
    } else {
        $serializedLaunch = $serializedSession.containmentLaunch
        [pscustomobject]@{
            Protocol = [string]$serializedLaunch.protocol
            JobName = [string]$serializedLaunch.jobName
            State = [string]$serializedLaunch.state
            LaunchProcessId = [int]$serializedLaunch.launchProcessId
            LaunchedUtc = ConvertTo-VerifierStrictTimestampText $serializedLaunch.launchedUtc `
                $true 'retained browser recovery containment launch launchedUtc' -AllowJsonDateTime
        }
    }
    $liveSession = [pscustomobject]@{
        RunId = [string]$serializedSession.runId
        RepositoryIdentity = [string]$serializedSession.repositoryIdentity
        WorktreeRoot = [string]$serializedSession.worktreeRoot
        RouteId = [string]$serializedSession.routeId
        RouteName = [string]$serializedSession.routeName
        CdpPort = [int]$serializedSession.cdpPort
        BrowserPath = [string]$serializedSession.browserPath
        Lease = $liveLease; Profile = [string]$serializedSession.profile
        ProcessId = [int]$serializedSession.processId
        ProcessStartTicks = [long]$serializedSession.processStartTicks
        ProcessParentProcessId = [int]$serializedSession.processParentProcessId
        ProcessParentProcessStartTicks = [long]$serializedSession.processParentProcessStartTicks
        ProcessCommandLine = [string]$serializedSession.processCommandLine
        TargetId = [string]$serializedSession.targetId
        ExpectedUrl = [string]$serializedSession.expectedUrl
        Status = [string]$serializedSession.status
        CleanupResult = [string]$serializedSession.cleanupResult
        Error = [string]$serializedSession.error
        ProfileInspectionFailed = [bool]$serializedSession.profileInspectionFailed
        ProfileProcessScanCompleted = [bool]$serializedSession.profileProcessScanCompleted
        RecoveryReceipt = $liveReceipt
        ContainmentLaunch = $liveContainmentLaunch
        Runtime = [pscustomobject]@{ Browser = $null; Socket = $null; ContainmentJob = $null }
    }
    $server = $serverProperty.Value
    $liveServer = [pscustomobject]@{
        Owner = [string]$server.owner; BaseUrl = [string]$server.baseUrl
        Port = [int]$server.port; ProcessId = [int]$server.processId
        ProcessStartTicks = [long]$server.processStartTicks
        ProcessParentProcessId = [int]$server.processParentProcessId
        ProcessParentProcessStartTicks = [long]$server.processParentProcessStartTicks
        ProcessCommandLine = [string]$server.processCommandLine
        Script = [string]$server.script; State = [string]$server.state
        RepositoryRoot = [string]$server.repositoryRoot; WebRoot = [string]$server.webRoot
        IdentityProtocol = [string]$server.identityProtocol
        IdentityVerified = [bool]$server.identityVerified
        CallerOwned = [bool]$server.callerOwned
        StdoutLog = [string]$server.stdoutLog; StderrLog = [string]$server.stderrLog
        CleanupResult = [string]$server.cleanupResult; Error = [string]$server.error
        Nonce = [string]$server.nonce; RunId = [string]$server.runId
        Lease = $null; Process = $null
        ProcessIdentityKnown = [bool]$server.processIdentityKnown
        OwnershipUncertain = [bool]$server.ownershipUncertain
        ProcessTerminationProven = [bool]$server.processTerminationProven
        ProcessAbsent = [bool]$server.processAbsent
        ListenerInspectionProven = [bool]$server.listenerInspectionProven
        ListenerAbsent = $server.listenerAbsent
    }
    $liveLeases = New-Object Collections.ArrayList
    [void]$liveLeases.Add($liveLease)
    $liveSessions = New-Object Collections.ArrayList
    [void]$liveSessions.Add($liveSession)
    $liveArtifacts = New-Object Collections.ArrayList
    foreach ($artifact in @($manifest.artifacts)) { [void]$liveArtifacts.Add([string]$artifact) }
    $liveErrors = New-Object Collections.ArrayList
    foreach ($errorText in @($cleanup.errors)) { [void]$liveErrors.Add([string]$errorText) }
    $context = [pscustomobject]@{
        Protocol = 'troubleshootjs-verifier-run-v1'; RunId = [string]$manifest.runId
        PreviewNonce = [string]$manifest.previewNonce
        RepositoryIdentity = [string]$manifest.repositoryIdentity
        WorktreeRoot = $worktreeRoot; RunRoot = $runRoot
        RunNamespaceRoot = $runNamespaceRoot; EvidenceDirectory = $evidenceDirectory
        EvidenceNamespaceRoot = $evidenceNamespaceRoot; ManifestPath = $expectedManifestPath
        PortLeaseRoot = $expectedPortLeaseRoot
        CreatedUtc = ConvertTo-VerifierStrictTimestampText $manifest.createdUtc $false `
            'retained browser recovery manifest createdUtc' -AllowJsonDateTime
        BaseUrl = ''; Server = $liveServer; LeaseRecords = $liveLeases
        BrowserSessions = $liveSessions; Artifacts = $liveArtifacts
        CleanupState = [string]$cleanup.state
        CleanupCompletedUtc = ConvertTo-VerifierStrictTimestampText `
            $cleanup.completedUtc $true `
            'retained browser recovery cleanup completedUtc' -AllowJsonDateTime
        CleanupErrors = $liveErrors
        TestHooks = [pscustomobject]@{
            FailNextManifestWrite = $false; FailNextClaimWrite = $false
            FailNextLeaseRelease = $false; FailNextLeaseMutexDispose = $false
            FailNextFinalManifestWrite = $false; FailNextPostDeleteFinalManifestWrite = $false
            FailNextPostDeleteJournalWrite = $false; FailNextPostDeleteBeforeFinalState = $false
            FailNextBrowserLeaseManifestWrite = $false; FailNextPreviewIdentityCapture = $false
            FailNextBrowserContainmentHandleAcquire = $false
            FailNextBrowserContainmentLaunchLedgerPublish = $false
            FailNextIssuedContainedRecovery = $false
            FailNextIssuedContainedRecoveryListenerBind = $false
            BrowserDrainAfterInitialGraphSignalPath = ''
            BrowserDrainAfterInitialGraphReadyPath = ''
            BrowserDrainAfterInitialGraphSignalWritten = $false
        }
        ManifestWritePhase = ''
    }
    return [pscustomobject]@{
        Context = $context; Lease = $liveLease; Session = $liveSession; Mode = $recoveryMode
    }
}

function Assert-VerifierRetainedBrowserRecoveryClaimRecord($Context, $Lease) {
    [void](Assert-VerifierPhysicalOwnedPath $Context.PortLeaseRoot $Lease.Path)
    if (-not (Test-Path -LiteralPath $Lease.Path -PathType Leaf -ErrorAction Stop)) {
        Throw-VerifierInfrastructure 'Retained browser recovery exact claim record was missing before Browser.close.'
    }
    try {
        $claim = ConvertFrom-VerifierDurableJson (Get-Content -LiteralPath $Lease.Path -Raw -ErrorAction Stop)
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not read retained browser recovery exact claim record: ' +
            (Get-VerifierErrorMessage $_))
    }
    Assert-VerifierSerializedObject $claim 'retained browser recovery exact claim record'
    foreach ($name in @('protocol', 'runId', 'repositoryIdentity', 'worktreeRoot',
            'leaseId', 'path', 'kind', 'mutexName')) {
        [void](Assert-VerifierSerializedString $claim $name `
            'retained browser recovery exact claim record')
    }
    foreach ($number in @('port', 'ownerPid', 'ownerStartTicks')) {
        $maximum = if ($number -like '*Ticks') { [long]::MaxValue } else { [int]::MaxValue }
        if ($null -eq $claim.PSObject.Properties[$number] -or
                -not (Test-VerifierStrictIntegralValue $claim.$number 1 $maximum)) {
            Throw-VerifierInfrastructure "Retained browser recovery exact claim record omitted '$number'."
        }
    }
    if ($claim.protocol -cne 'troubleshootjs-verifier-port-claim-v1' -or
            $claim.runId -cne $Context.RunId -or
            $claim.repositoryIdentity -cne $Context.RepositoryIdentity -or
            -not (Test-VerifierCanonicalWindowsPathValue $claim.worktreeRoot $Context.WorktreeRoot) -or
            $claim.leaseId -cne $Lease.LeaseId -or
            -not (Test-VerifierCanonicalWindowsPathValue $claim.path $Lease.Path) -or
            $claim.kind -cne $Lease.Kind -or [int]$claim.port -ne [int]$Lease.Port -or
            $claim.mutexName -cne $Lease.ClaimName -or
            [int]$claim.ownerPid -ne [int]$Lease.ClaimOwnerPid -or
            [long]$claim.ownerStartTicks -ne [long]$Lease.ClaimOwnerStartTicks) {
        Throw-VerifierInfrastructure 'Retained browser recovery exact claim record changed or belonged to another run.'
    }
}

function Enter-VerifierRetainedBrowserRecoveryLease($Context, $Lease) {
    $originalOwner = [pscustomobject]@{
        ProcessId = [int]$Lease.ClaimOwnerPid
        ProcessStartTicks = [long]$Lease.ClaimOwnerStartTicks
    }
    $beforeLock = Confirm-VerifierRecordedProcessAbsent $originalOwner `
        'retained browser recovery original claim owner'
    if (-not $beforeLock.QueryProven -or -not $beforeLock.Absent -or $beforeLock.Replaced) {
        Throw-VerifierInfrastructure 'Retained browser recovery cannot acquire a claim while its original owner is live or PID-reused.'
    }
    if ($script:VerifierHeldPortClaims.ContainsKey([string]$Lease.ClaimName) -or
            $script:VerifierHeldPortMutexes.ContainsKey([string]$Lease.ClaimName)) {
        Throw-VerifierInfrastructure 'Retained browser recovery found an in-process claim/mutex collision for its exact port.'
    }
    $mutex = $null
    $lockHeld = $false
    $mapsRegistered = $false
    try {
        $created = $false
        $mutex = [Threading.Mutex]::new($false, [string]$Lease.ClaimName, [ref]$created)
        try {
            $lockHeld = [bool]$mutex.WaitOne(0)
        } catch [Threading.AbandonedMutexException] {
            # The old owner was independently proved absent above. The OS has
            # transferred this exact abandoned named mutex to this recovery
            # process; retain and release it through the ordinary lease path.
            $lockHeld = $true
        }
        if (-not $lockHeld) {
            Throw-VerifierInfrastructure 'Retained browser recovery could not acquire its exact named claim mutex immediately.'
        }
        $afterLock = Confirm-VerifierRecordedProcessAbsent $originalOwner `
            'retained browser recovery original claim owner'
        if (-not $afterLock.QueryProven -or -not $afterLock.Absent -or $afterLock.Replaced) {
            Throw-VerifierInfrastructure 'Retained browser recovery original claim owner changed while the exact mutex was acquired.'
        }
        $Lease.ClaimMutex = $mutex
        $script:VerifierHeldPortClaims[[string]$Lease.ClaimName] = [string]$Context.RunId
        $script:VerifierHeldPortMutexes[[string]$Lease.ClaimName] = $mutex
        $mapsRegistered = $true
        $Lease.RetainedRecoveryAuthority = [pscustomobject]@{
            Protocol = 'troubleshootjs-verifier-retained-browser-recovery-v1'
            ManifestPath = [string]$Context.ManifestPath
            RecoveryProcessId = [int]$PID
            RecoveryProcessStartTicks = Get-VerifierCurrentProcessStartTicks
            ClaimOwnerPid = [int]$Lease.ClaimOwnerPid
            ClaimOwnerStartTicks = [long]$Lease.ClaimOwnerStartTicks
            ClaimMutex = $mutex; LockHeld = $true
        }
        [void](Get-VerifierRetainedBrowserRecoveryLeaseAuthority $Context $Lease)
        Assert-VerifierRetainedBrowserRecoveryClaimRecord $Context $Lease
        Assert-VerifierDurableManifestContext $Context
        return
    } catch {
        if ($mapsRegistered) {
            [void]$script:VerifierHeldPortClaims.Remove([string]$Lease.ClaimName)
            [void]$script:VerifierHeldPortMutexes.Remove([string]$Lease.ClaimName)
        }
        $Lease.RetainedRecoveryAuthority = $null
        $Lease.ClaimMutex = $null
        if ($null -ne $mutex) {
            if ($lockHeld) { try { $mutex.ReleaseMutex() } catch { } }
            try { $mutex.Dispose() } catch { }
        }
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not enter retained browser recovery: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function Exit-VerifierRetainedBrowserRecoveryLease($Lease) {
    if ($null -eq $Lease -or
            $null -eq $Lease.PSObject.Properties['RetainedRecoveryAuthority'] -or
            $null -eq $Lease.RetainedRecoveryAuthority) {
        return
    }
    $authority = $Lease.RetainedRecoveryAuthority
    $cleanupErrors = New-Object Collections.ArrayList
    $mutex = if ($authority.PSObject.Properties['ClaimMutex']) { $authority.ClaimMutex } else { $null }
    if ($null -ne $mutex -and $mutex.GetType() -eq [Threading.Mutex] -and
            -not $Lease.MutexReleased) {
        try { $mutex.ReleaseMutex() } catch {
            [void]$cleanupErrors.Add('could not release retained browser recovery mutex: ' +
                (Get-VerifierErrorMessage $_))
        }
    }
    if ($null -ne $mutex -and $mutex.GetType() -eq [Threading.Mutex] -and
            -not $Lease.MutexReleased) {
        try { $mutex.Dispose() } catch {
            [void]$cleanupErrors.Add('could not dispose retained browser recovery mutex: ' +
                (Get-VerifierErrorMessage $_))
        }
        $Lease.ClaimMutex = $null
    }
    if ($script:VerifierHeldPortClaims.ContainsKey([string]$Lease.ClaimName) -and
            [string]$script:VerifierHeldPortClaims[[string]$Lease.ClaimName] -eq [string]$Lease.RunId) {
        [void]$script:VerifierHeldPortClaims.Remove([string]$Lease.ClaimName)
    }
    if ($script:VerifierHeldPortMutexes.ContainsKey([string]$Lease.ClaimName) -and
            [object]::ReferenceEquals($script:VerifierHeldPortMutexes[[string]$Lease.ClaimName], $mutex)) {
        [void]$script:VerifierHeldPortMutexes.Remove([string]$Lease.ClaimName)
    }
    if ($authority.PSObject.Properties['LockHeld']) { $authority.LockHeld = $false }
    $Lease.RetainedRecoveryAuthority = $null
    if ($cleanupErrors.Count -gt 0) {
        Throw-VerifierInfrastructure ($cleanupErrors -join '; ')
    }
}

function Invoke-VerifierRetainedBrowserRecovery([string]$ManifestPath,
        [switch]$TestFailAfterIssuedContainedRootReattestation) {
    $rehydrated = New-VerifierRetainedBrowserRecoveryContext $ManifestPath
    $context = $rehydrated.Context
    $lease = $rehydrated.Lease
    $session = $rehydrated.Session
    $recoveryMode = [string]$rehydrated.Mode
    if ($TestFailAfterIssuedContainedRootReattestation) {
        if ($recoveryMode -ne 'issued-contained') {
            Throw-VerifierInfrastructure 'Issued-contained reattestation test hook requires exactly one issued contained recovery record.'
        }
        # Gate B uses this one-shot failure only to prove that a real retained
        # root reattestation cannot publish a partial tuple before listener
        # binding. It removes authority rather than widening it.
        $context.TestHooks.FailNextIssuedContainedRecoveryListenerBind = $true
    }
    $failure = $null
    $cleanupResult = $null
    try {
        Enter-VerifierRetainedBrowserRecoveryLease $context $lease
        # Persist the recovery intent before Browser.close. An interruption
        # here remains an eligible bound/unattempted receipt; an interruption
        # after the close-attempt journal remains safely non-retriable.
        $context.CleanupState = 'pending'
        $context.CleanupCompletedUtc = ''
        if ($recoveryMode -eq 'bound') {
            $session.Status = 'attached'
        } elseif ($recoveryMode -eq 'issued-contained') {
            # The durable PID/job record is intentionally not a close
            # capability. Complete-VerifierBrowserSession must first re-prove
            # the exact contained root and listener, bind a receipt, and only
            # then reach its Browser.close-only shutdown lane.
            $session.Status = 'startup-failed'
        } else {
            Throw-VerifierInfrastructure 'Retained browser recovery selected an unknown durable recovery mode.'
        }
        $session.CleanupResult = 'pending'
        $session.Error = ''
        $lease.ReleaseBlocked = $false
        $lease.ReleaseBlockReason = ''
        Assert-VerifierDurableManifestContext $context
        Write-VerifierManifest $context
        $cleanupResult = Complete-VerifierRun $context
        if ($null -eq $cleanupResult -or
                $null -eq $cleanupResult.PSObject.Properties['Success'] -or
                -not $cleanupResult.Success -or
                $session.CleanupResult -ne 'complete' -or
                $session.RecoveryReceipt.State -ne 'closed') {
            Throw-VerifierInfrastructure 'Retained browser recovery did not reach complete browser, receipt, lease, and run cleanup proof.'
        }
    } catch {
        $failure = $_
    }
    $exitFailure = $null
    try { Exit-VerifierRetainedBrowserRecoveryLease $lease } catch { $exitFailure = $_ }
    if ($null -ne $failure) {
        if ($null -ne $exitFailure) {
            Throw-VerifierInfrastructure ('Retained browser recovery failed and recovery-mutex cleanup was unproven: ' +
                (Get-VerifierErrorMessage $failure) + '; ' + (Get-VerifierErrorMessage $exitFailure))
        }
        if (Test-VerifierInfrastructureError $failure) { throw $failure }
        Throw-VerifierInfrastructure ('Retained browser recovery failed: ' +
            (Get-VerifierErrorMessage $failure))
    }
    if ($null -ne $exitFailure) { throw $exitFailure }
    return [pscustomobject]@{
        Success = $true; ManifestPath = [string]$context.ManifestPath
        RunId = [string]$context.RunId; Port = [int]$lease.Port
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

function New-VerifierBrowserRecoveryAuthorityToken() {
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 32
    try {
        $generator.GetBytes($bytes)
        return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
    } finally {
        $generator.Dispose()
    }
}

function Test-VerifierBrowserRecoveryAuthorityToken($Value) {
    return (Test-VerifierStrictStringValue $Value) -and
        ([regex]::IsMatch([string]$Value, '\A[0-9a-f]{64}\z'))
}

function Assert-VerifierDurableBrowserRecoveryReceipt($Session, $Context,
        [string]$Label = 'browser recovery receipt') {
    if ($null -eq $Session -or $null -eq $Session.PSObject.Properties['RecoveryReceipt'] -or
            $null -eq $Session.RecoveryReceipt) {
        # Existing retained sessions predate the recovery capability. They may
        # use only the original parent-anchored cleanup path; recovery never
        # treats a legacy marker tuple as an authority.
        return $null
    }
    $receipt = $Session.RecoveryReceipt
    if ($receipt -is [array]) {
        Throw-VerifierInfrastructure "$Label was not one exact receipt object."
    }
    foreach ($propertyName in @('Protocol', 'AuthorityToken', 'IssuedUtc', 'State',
            'BoundUtc', 'ClosedUtc', 'CloseAttemptedUtc', 'RunId',
            'RepositoryIdentity', 'WorktreeRoot', 'RouteId', 'RouteName',
            'BrowserPath', 'Profile', 'LeaseId', 'RootProcessCommandLine')) {
        $property = $receipt.PSObject.Properties[$propertyName]
        if ($null -eq $property -or -not (Test-VerifierStrictStringValue $property.Value)) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its exact string '$propertyName'."
        }
    }
    foreach ($propertyName in @('CdpPort', 'RootProcessId', 'RootProcessStartTicks',
            'RootParentProcessId', 'RootParentProcessStartTicks',
            'ListenerProcessId', 'ListenerProcessStartTicks')) {
        $property = $receipt.PSObject.Properties[$propertyName]
        $maximum = if ($propertyName -like '*Ticks') { [long]::MaxValue } else { [int]::MaxValue }
        if ($null -eq $property -or -not (Test-VerifierStrictIntegralValue $property.Value 0 $maximum)) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its exact integral '$propertyName'."
        }
    }
    $closeAttemptedProperty = $receipt.PSObject.Properties['CloseAttempted']
    if ($null -eq $closeAttemptedProperty -or
            -not (Test-VerifierStrictBooleanValue $closeAttemptedProperty.Value)) {
        Throw-VerifierInfrastructure "$Label omitted its exact CloseAttempted Boolean."
    }
    if ($receipt.Protocol -cne 'troubleshootjs-verifier-browser-recovery-v1' -or
            -not (Test-VerifierBrowserRecoveryAuthorityToken $receipt.AuthorityToken) -or
            $receipt.State -cnotin @('issued', 'bound', 'closed')) {
        Throw-VerifierInfrastructure "$Label carried an unknown protocol, authority token, or lifecycle state."
    }
    foreach ($timestamp in @(
            [pscustomobject]@{ Name = 'IssuedUtc'; Required = $true },
            [pscustomobject]@{ Name = 'BoundUtc'; Required = ($receipt.State -eq 'bound' -or
                ($receipt.State -eq 'closed' -and -not [String]::IsNullOrWhiteSpace($receipt.BoundUtc))) },
            [pscustomobject]@{ Name = 'ClosedUtc'; Required = ($receipt.State -eq 'closed') },
            [pscustomobject]@{ Name = 'CloseAttemptedUtc'; Required = [bool]$receipt.CloseAttempted }
        )) {
        $value = [string]$receipt.$($timestamp.Name)
        if ($timestamp.Required -and [String]::IsNullOrWhiteSpace($value)) {
            Throw-VerifierInfrastructure "$Label omitted required timestamp '$($timestamp.Name)'."
        }
        if (-not [String]::IsNullOrWhiteSpace($value)) {
            [void](ConvertTo-VerifierStrictTimestampText $value $false ($Label + ' ' + $timestamp.Name))
        }
    }
    if (($receipt.State -eq 'issued' -and
            (-not [String]::IsNullOrWhiteSpace($receipt.BoundUtc) -or
             -not [String]::IsNullOrWhiteSpace($receipt.ClosedUtc) -or
             $receipt.CloseAttempted)) -or
            ($receipt.State -eq 'bound' -and -not [String]::IsNullOrWhiteSpace($receipt.ClosedUtc))) {
        Throw-VerifierInfrastructure "$Label carried contradictory lifecycle timestamps."
    }
    if ($null -eq $Session.PSObject.Properties['Lease'] -or $null -eq $Session.Lease) {
        Throw-VerifierInfrastructure "$Label omitted the session's exact lease binding."
    }
    if ($receipt.RunId -cne $Session.RunId -or
            $receipt.RepositoryIdentity -cne $Session.RepositoryIdentity -or
            -not (Test-VerifierCanonicalWindowsPathValue $receipt.WorktreeRoot $Session.WorktreeRoot) -or
            $receipt.RouteId -cne $Session.RouteId -or
            $receipt.RouteName -cne $Session.RouteName -or
            -not (Test-VerifierCanonicalWindowsPathValue $receipt.BrowserPath $Session.BrowserPath) -or
            -not (Test-VerifierCanonicalWindowsPathValue $receipt.Profile $Session.Profile) -or
            $receipt.LeaseId -cne $Session.Lease.LeaseId -or
            [int]$receipt.CdpPort -ne [int]$Session.CdpPort) {
        Throw-VerifierInfrastructure "$Label did not match the exact browser session identity."
    }
    if ($null -ne $Context -and
            ($receipt.RunId -cne $Context.RunId -or
             $receipt.RepositoryIdentity -cne $Context.RepositoryIdentity -or
             -not (Test-VerifierCanonicalWindowsPathValue $receipt.WorktreeRoot $Context.WorktreeRoot))) {
        Throw-VerifierInfrastructure "$Label did not match the exact verifier context identity."
    }
    $hasRootTuple = ([int]$receipt.RootProcessId -gt 0 -or
        [long]$receipt.RootProcessStartTicks -gt 0 -or
        [int]$receipt.RootParentProcessId -gt 0 -or
        [long]$receipt.RootParentProcessStartTicks -gt 0 -or
        -not [String]::IsNullOrWhiteSpace($receipt.RootProcessCommandLine))
    $hasListenerTuple = ([int]$receipt.ListenerProcessId -gt 0 -or
        [long]$receipt.ListenerProcessStartTicks -gt 0)
    if ($receipt.State -eq 'issued') {
        if ($hasRootTuple -or $hasListenerTuple) {
            Throw-VerifierInfrastructure "$Label issued state carried a partial root or listener binding."
        }
    } elseif ($receipt.State -eq 'bound' -or
            ($receipt.State -eq 'closed' -and
             -not [String]::IsNullOrWhiteSpace($receipt.BoundUtc))) {
        if (-not $hasRootTuple -or -not $hasListenerTuple) {
            Throw-VerifierInfrastructure "$Label $($receipt.State) state omitted its bound root or listener identity."
        }
        [void](Assert-VerifierDurableProcessIdentityTuple $receipt `
            -ProcessIdPropertyName 'RootProcessId' `
            -ProcessStartPropertyName 'RootProcessStartTicks' `
            -ParentProcessIdPropertyName 'RootParentProcessId' `
            -ParentProcessStartPropertyName 'RootParentProcessStartTicks' `
            -CommandLinePropertyName 'RootProcessCommandLine' `
            -Label ($Label + ' bound root'))
        if ([int]$receipt.ListenerProcessId -le 0 -or
                [long]$receipt.ListenerProcessStartTicks -le 0) {
            Throw-VerifierInfrastructure "$Label $($receipt.State) state omitted a positive listener PID/start identity."
        }
        if ([int]$Session.ProcessId -le 0 -or [long]$Session.ProcessStartTicks -le 0 -or
                [int]$Session.ProcessParentProcessId -le 0 -or
                [long]$Session.ProcessParentProcessStartTicks -le 0 -or
                [String]::IsNullOrWhiteSpace([string]$Session.ProcessCommandLine) -or
                [int]$receipt.RootProcessId -ne [int]$Session.ProcessId -or
                [long]$receipt.RootProcessStartTicks -ne [long]$Session.ProcessStartTicks -or
                [int]$receipt.RootParentProcessId -ne [int]$Session.ProcessParentProcessId -or
                [long]$receipt.RootParentProcessStartTicks -ne
                    [long]$Session.ProcessParentProcessStartTicks -or
                -not (Test-VerifierCommandLineEquivalent $receipt.RootProcessCommandLine `
                    $Session.ProcessCommandLine) -or
                [int]$receipt.ListenerProcessId -ne [int]$Session.Lease.ListenerProcessId -or
                [long]$receipt.ListenerProcessStartTicks -ne
                    [long]$Session.Lease.ListenerProcessStartTicks -or
                [int]$Session.Lease.BoundProcessId -ne [int]$Session.ProcessId -or
                [long]$Session.Lease.BoundProcessStartTicks -ne
                    [long]$Session.ProcessStartTicks) {
            Throw-VerifierInfrastructure "$Label bound tuple did not match the exact session and lease identity."
        }
        if ($receipt.State -eq 'closed' -and -not $receipt.CloseAttempted) {
            Throw-VerifierInfrastructure "$Label closed a bound receipt without a durable close-attempt journal."
        }
    } elseif ($receipt.State -eq 'closed') {
        # A lease can be rolled back before the browser ever binds.  Preserve
        # that revocation explicitly as a closed *unbound* receipt instead of
        # inventing a root/listener tuple. It cannot authorize shutdown: it
        # carries no bound identity and no close attempt.
        if ($hasRootTuple -or $hasListenerTuple -or $receipt.CloseAttempted -or
                -not [String]::IsNullOrWhiteSpace($receipt.BoundUtc)) {
            Throw-VerifierInfrastructure "$Label closed an unbound receipt with root/listener or close-attempt evidence."
        }
    }
    if ($Session.CleanupResult -eq 'complete' -and $receipt.State -ne 'closed') {
        Throw-VerifierInfrastructure "$Label was not durably closed with its completed browser session."
    }
    return $receipt
}

function Assert-VerifierDurableBrowserContainmentLaunch($Session, $Context,
        [string]$Label = 'browser containment launch') {
    if ($null -eq $Session) {
        Throw-VerifierInfrastructure "$Label omitted its browser session."
    }
    $receipt = Assert-VerifierDurableBrowserRecoveryReceipt $Session $Context `
        ($Label + ' recovery receipt')
    $launchProperty = $Session.PSObject.Properties['ContainmentLaunch']
    if ($null -eq $launchProperty -or $null -eq $launchProperty.Value) {
        # Bound historical sessions have an independent, complete receipt/root
        # tuple. They remain readable, but an issued session must carry the
        # post-CreateProcess launch ledger before it can ever be resumed.
        $legacyIdentity = Assert-VerifierDurableProcessIdentityTuple $Session `
            -ProcessIdPropertyName 'ProcessId' `
            -ProcessStartPropertyName 'ProcessStartTicks' `
            -ParentProcessIdPropertyName 'ProcessParentProcessId' `
            -ParentProcessStartPropertyName 'ProcessParentProcessStartTicks' `
            -CommandLinePropertyName 'ProcessCommandLine' `
            -Label ($Label + ' legacy process identity')
        if ($null -ne $receipt -and $receipt.State -in @('bound', 'closed') -and
                $legacyIdentity.State -eq 'positive') {
            return $null
        }
        Throw-VerifierInfrastructure "$Label omitted its durable containment-launch record."
    }
    $launch = $launchProperty.Value
    if ($launch -is [array]) {
        Throw-VerifierInfrastructure "$Label was not one exact containment-launch object."
    }
    foreach ($propertyName in @('Protocol', 'JobName', 'State', 'LaunchedUtc')) {
        $property = $launch.PSObject.Properties[$propertyName]
        if ($null -eq $property -or -not (Test-VerifierStrictStringValue $property.Value)) {
            Throw-VerifierInfrastructure "$Label omitted or malformed its exact string '$propertyName'."
        }
    }
    $launchPidProperty = $launch.PSObject.Properties['LaunchProcessId']
    if ($null -eq $launchPidProperty -or
            -not (Test-VerifierStrictIntegralValue $launchPidProperty.Value 0 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure "$Label omitted or malformed its exact launch PID."
    }
    if ($launch.Protocol -cne 'troubleshootjs-verifier-browser-containment-launch-v1' -or
            $launch.State -cnotin @('unlaunched', 'launch-pending', 'launched')) {
        Throw-VerifierInfrastructure "$Label carried an unknown protocol or lifecycle state."
    }
    if ($null -eq $receipt) {
        Throw-VerifierInfrastructure "$Label cannot derive a containment job without one durable recovery receipt."
    }
    $expectedJobName = Get-VerifierBrowserContainmentJobName $Context $Session
    if ([String]::IsNullOrWhiteSpace([string]$launch.JobName) -or
            $launch.JobName -cne $expectedJobName) {
        Throw-VerifierInfrastructure "$Label did not retain the exact derived containment-job identity."
    }
    $sessionIdentity = Assert-VerifierDurableProcessIdentityTuple $Session `
        -ProcessIdPropertyName 'ProcessId' `
        -ProcessStartPropertyName 'ProcessStartTicks' `
        -ParentProcessIdPropertyName 'ProcessParentProcessId' `
        -ParentProcessStartPropertyName 'ProcessParentProcessStartTicks' `
        -CommandLinePropertyName 'ProcessCommandLine' `
        -Label ($Label + ' browser process identity')
    if ($launch.State -in @('unlaunched', 'launch-pending')) {
        if ([int]$launch.LaunchProcessId -ne 0 -or
                -not [String]::IsNullOrWhiteSpace([string]$launch.LaunchedUtc) -or
                $sessionIdentity.State -ne 'absent') {
            Throw-VerifierInfrastructure "$Label unlaunched/pending state carried a root identity or timestamp."
        }
        if ($launch.State -eq 'launch-pending' -and
                ($receipt.State -ne 'issued' -or $receipt.CloseAttempted -or
                 $Session.Status -notin @('leased', 'startup-failed', 'cleanup-failed'))) {
            Throw-VerifierInfrastructure "$Label pending state did not retain one exact issued, rootless recovery lifecycle."
        }
    } else {
        if ([int]$launch.LaunchProcessId -le 0 -or
                [String]::IsNullOrWhiteSpace([string]$launch.LaunchedUtc)) {
            Throw-VerifierInfrastructure "$Label launched state omitted its exact root PID or timestamp."
        }
        [void](ConvertTo-VerifierStrictTimestampText $launch.LaunchedUtc $false `
            ($Label + ' LaunchedUtc'))
        if ($sessionIdentity.State -eq 'positive' -and
                [int]$launch.LaunchProcessId -ne [int]$sessionIdentity.ProcessId) {
            Throw-VerifierInfrastructure "$Label launch PID disagreed with the complete browser root identity."
        }
    }
    # A closed receipt can record either a completed bound shutdown or an
    # explicit pre-bind revocation.  Only the former has a root/listener tuple
    # that must match a launched containment record.
    $receiptHasBoundRoot = ($receipt.State -eq 'bound' -or
        ($receipt.State -eq 'closed' -and
         -not [String]::IsNullOrWhiteSpace([string]$receipt.BoundUtc)))
    if ($receiptHasBoundRoot -and
            ($launch.State -ne 'launched' -or $sessionIdentity.State -ne 'positive' -or
             [int]$launch.LaunchProcessId -ne [int]$sessionIdentity.ProcessId)) {
        Throw-VerifierInfrastructure "$Label bound/closed receipt did not retain its exact launched containment root."
    }
    return $launch
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
    [void](Assert-VerifierDurableBrowserRecoveryReceipt $Session $Context ($Label + ' recovery receipt'))
    [void](Assert-VerifierDurableBrowserContainmentLaunch $Session $Context `
        ($Label + ' containment launch'))
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
            recoveryReceipt = if ($null -eq $_.PSObject.Properties['RecoveryReceipt'] -or
                    $null -eq $_.RecoveryReceipt) { $null } else {
                [ordered]@{
                    protocol = $_.RecoveryReceipt.Protocol
                    authorityToken = $_.RecoveryReceipt.AuthorityToken
                    issuedUtc = $_.RecoveryReceipt.IssuedUtc
                    state = $_.RecoveryReceipt.State
                    boundUtc = $_.RecoveryReceipt.BoundUtc
                    closedUtc = $_.RecoveryReceipt.ClosedUtc
                    closeAttempted = $_.RecoveryReceipt.CloseAttempted
                    closeAttemptedUtc = $_.RecoveryReceipt.CloseAttemptedUtc
                    runId = $_.RecoveryReceipt.RunId
                    repositoryIdentity = $_.RecoveryReceipt.RepositoryIdentity
                    worktreeRoot = $_.RecoveryReceipt.WorktreeRoot
                    routeId = $_.RecoveryReceipt.RouteId
                    routeName = $_.RecoveryReceipt.RouteName
                    browserPath = $_.RecoveryReceipt.BrowserPath
                    profile = $_.RecoveryReceipt.Profile
                    leaseId = $_.RecoveryReceipt.LeaseId
                    cdpPort = $_.RecoveryReceipt.CdpPort
                    rootProcessId = $_.RecoveryReceipt.RootProcessId
                    rootProcessStartTicks = $_.RecoveryReceipt.RootProcessStartTicks
                    rootParentProcessId = $_.RecoveryReceipt.RootParentProcessId
                    rootParentProcessStartTicks = $_.RecoveryReceipt.RootParentProcessStartTicks
                    rootProcessCommandLine = $_.RecoveryReceipt.RootProcessCommandLine
                    listenerProcessId = $_.RecoveryReceipt.ListenerProcessId
                    listenerProcessStartTicks = $_.RecoveryReceipt.ListenerProcessStartTicks
                }
            }
            containmentLaunch = if ($null -eq $_.PSObject.Properties['ContainmentLaunch'] -or
                    $null -eq $_.ContainmentLaunch) { $null } else {
                [ordered]@{
                    protocol = $_.ContainmentLaunch.Protocol
                    jobName = $_.ContainmentLaunch.JobName
                    state = $_.ContainmentLaunch.State
                    launchProcessId = $_.ContainmentLaunch.LaunchProcessId
                    launchedUtc = $_.ContainmentLaunch.LaunchedUtc
                }
            }
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
                   FailNextBrowserContainmentHandleAcquire = $false
                   FailNextBrowserContainmentLaunchLedgerPublish = $false
                   FailNextIssuedContainedRecovery = $false
                   FailNextIssuedContainedRecoveryListenerBind = $false
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

function Test-VerifierSameRootBrowserLiveIdentity($OwnerRecord, $CurrentRoot,
        $ProcessId, $ProcessStartTicks) {
    # The same-root CDP listener is a narrow proof path. It deliberately
    # reuses the already fresh root record from the listener boundary instead
    # of starting another full root WMI census, then independently proves the
    # recorded launch parent. This keeps the existing 500 ms ownership budget
    # intact without making a PID/start tuple sufficient on its own.
    if ($null -eq $OwnerRecord -or $null -eq $CurrentRoot -or
            -not (Test-VerifierStrictIntegralValue $ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $ProcessStartTicks 1)) {
        return $false
    }
    foreach ($propertyName in @('ProcessId', 'ProcessStartTicks',
            'ProcessParentProcessId', 'ProcessParentProcessStartTicks',
            'ProcessCommandLine', 'BrowserPath', 'Profile', 'RunId',
            'RepositoryIdentity', 'CdpPort')) {
        if (-not $OwnerRecord.PSObject.Properties[$propertyName]) { return $false }
    }
    if (-not (Test-VerifierStrictIntegralValue $OwnerRecord.ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $OwnerRecord.ProcessStartTicks 1) -or
            -not (Test-VerifierStrictIntegralValue $OwnerRecord.ProcessParentProcessId `
                1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $OwnerRecord.ProcessParentProcessStartTicks 1) -or
            -not (Test-VerifierStrictIntegralValue $OwnerRecord.CdpPort 1 65535) -or
            -not (Test-VerifierStrictStringValue $OwnerRecord.ProcessCommandLine) -or
            -not (Test-VerifierStrictStringValue $OwnerRecord.BrowserPath) -or
            -not (Test-VerifierStrictStringValue $OwnerRecord.Profile) -or
            -not (Test-VerifierStrictStringValue $OwnerRecord.RunId) -or
            -not (Test-VerifierStrictStringValue $OwnerRecord.RepositoryIdentity) -or
            [String]::IsNullOrWhiteSpace([string]$OwnerRecord.ProcessCommandLine) -or
            [String]::IsNullOrWhiteSpace([string]$OwnerRecord.BrowserPath) -or
            [String]::IsNullOrWhiteSpace([string]$OwnerRecord.Profile) -or
            [String]::IsNullOrWhiteSpace([string]$OwnerRecord.RunId) -or
            [String]::IsNullOrWhiteSpace([string]$OwnerRecord.RepositoryIdentity)) {
        return $false
    }
    foreach ($propertyName in @('ProcessId', 'ProcessStartTicks',
            'ParentProcessId', 'CommandLine', 'Name', 'ExecutablePath')) {
        if (-not $CurrentRoot.PSObject.Properties[$propertyName]) { return $false }
    }
    if (-not (Test-VerifierStrictIntegralValue $CurrentRoot.ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $CurrentRoot.ProcessStartTicks 1) -or
            -not (Test-VerifierStrictIntegralValue $CurrentRoot.ParentProcessId `
                1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictStringValue $CurrentRoot.CommandLine) -or
            [String]::IsNullOrWhiteSpace([string]$CurrentRoot.CommandLine) -or
            [int]$CurrentRoot.ProcessId -ne [int]$ProcessId -or
            [int]$CurrentRoot.ProcessId -ne [int]$OwnerRecord.ProcessId -or
            [long]$CurrentRoot.ProcessStartTicks -ne [long]$ProcessStartTicks -or
            [long]$CurrentRoot.ProcessStartTicks -ne [long]$OwnerRecord.ProcessStartTicks -or
            [int]$CurrentRoot.ParentProcessId -ne [int]$OwnerRecord.ProcessParentProcessId -or
            -not (Test-VerifierConfiguredExecutableIdentity $OwnerRecord.BrowserPath `
                $CurrentRoot -RequireExecutablePath) -or
            -not (Test-VerifierCommandLineEquivalent $CurrentRoot.CommandLine `
                $OwnerRecord.ProcessCommandLine)) {
        return $false
    }
    foreach ($switch in @(
            [pscustomobject]@{ Name = '--user-data-dir'; Value = [string]$OwnerRecord.Profile }
            [pscustomobject]@{ Name = '--tsj-verifier-run'; Value = [string]$OwnerRecord.RunId }
            [pscustomobject]@{ Name = '--tsj-verifier-worktree'; Value = [string]$OwnerRecord.RepositoryIdentity }
            [pscustomobject]@{ Name = '--remote-debugging-port'; Value = [string]$OwnerRecord.CdpPort }
        )) {
        if (-not (Test-VerifierCommandLineSwitch $CurrentRoot.CommandLine `
                $switch.Name $switch.Value)) {
            return $false
        }
    }
    try {
        $currentParent = Get-VerifierCurrentProcessRecordById `
            ([int]$CurrentRoot.ParentProcessId)
        return ($null -ne $currentParent -and
            [long]$currentParent.ProcessStartTicks -eq
                [long]$OwnerRecord.ProcessParentProcessStartTicks)
    } catch {
        return $false
    }
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
            $currentRoot = $null
            try {
                $currentRoot = Get-VerifierCurrentProcessRecordById `
                    ([int]$AuthorizedProcessId)
            } catch { return $false }
            if ($retryBudget.ElapsedTicks -lt $retryBudgetTicks -and
                    $null -ne $currentRoot -and
                    (Test-VerifierSameRootBrowserLiveIdentity $PreviewOwner `
                        $currentRoot $AuthorizedProcessId $AuthorizedProcessStartTicks) -and
                    $retryBudget.ElapsedTicks -lt $retryBudgetTicks) {
                return $true
            }
            # Retain the pre-existing PID-scoped proof for a constrained
            # synthetic/current-process view. It never reaches the complete
            # descendant snapshot, and a failed proof remains fail closed.
            foreach ($listener in $attemptListeners) {
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                    return $false
                }
                $belongs = $false
                try {
                    $belongs = Test-VerifierListenerBelongsToOwner $PreviewOwner `
                        $listener $AuthorizedProcessId $AuthorizedProcessStartTicks $null
                } catch { return $false }
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks -or -not $belongs) {
                    return $false
                }
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
        $currentRoot = $null
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
                $currentListener = if ([int]$listener.ProcessId -eq
                        [int]$currentRoot.ProcessId -and
                        [long]$listener.ProcessStartTicks -eq
                            [long]$currentRoot.ProcessStartTicks) {
                    $currentRoot
                } else {
                    Get-VerifierCurrentProcessRecordById ([int]$listener.ProcessId)
                }
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
        if ($sameRootBrowserListenerSet) {
            $proofStage = 'browser-root-listener-fast-identity'
            if ($null -ne $currentRoot -and
                    (Test-VerifierSameRootBrowserLiveIdentity $OwnerRecord `
                        $currentRoot $ProcessId $ProcessStartTicks)) {
                if ($retryBudget.ElapsedTicks -ge $retryBudgetTicks) {
                    Throw-VerifierInfrastructure ('Port lease ownership proof exceeded its bounded monotonic deadline. ' +
                        "[stage=$proofStage elapsedMs=$([long]$proofStageStopwatch.ElapsedMilliseconds)]")
                }
                return [pscustomobject]@{
                    Inspection = $inspection; Listeners = @($listeners); Snapshot = @()
                    AuthorizationProof = $kernelAuthorizationProof
                    ProofStage = $proofStage
                    ProofElapsedMilliseconds = [long]$proofStageStopwatch.ElapsedMilliseconds
                }
            }
            # A synthetic/test host can expose only a PID/start current view.
            # Keep the original PID-scoped proof below as a fail-closed
            # compatibility fallback; it still receives Snapshot=$null and
            # never broadens the same-root listener into a descendant census.
        }

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
        $ProcessStartTicks, $OwnerRecord = $null, [switch]$DeferManifestWrite) {
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
    if (-not $DeferManifestWrite) {
        Write-VerifierManifest $Context
    }
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
    $retainedRecoveryAuthority = Get-VerifierRetainedBrowserRecoveryLeaseAuthority `
        $Context $Lease 'port lease release retained browser recovery authority'
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
            # A normal lease may only be released by the process that acquired
            # it. The one narrow exception is a retained browser session whose
            # original claimant is positively absent, whose exact mutex was
            # reacquired by this process, and whose in-memory capability was
            # created by the durable manifest recovery entry point above.
            if ($null -eq $retainedRecoveryAuthority) {
                Throw-VerifierInfrastructure "Refusing to release port $($Lease.Port): claim process identity changed."
            }
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
        # This durable receipt is deliberately distinct from the ordinary
        # parent/child identity. It is a fresh capability embedded in the
        # launched browser command and is not sufficient on its own to stop a
        # process. Recovery requires its later root/listener binding as well as
        # current PID/start, executable, command, profile, and parent-absence
        # proofs.
        $recoveryReceipt = [pscustomobject]@{
            Protocol = 'troubleshootjs-verifier-browser-recovery-v1'
            AuthorityToken = (New-VerifierBrowserRecoveryAuthorityToken)
            IssuedUtc = (Get-VerifierUtcText)
            State = 'issued'; BoundUtc = ''; ClosedUtc = ''
            CloseAttempted = $false; CloseAttemptedUtc = ''
            RunId = $Context.RunId; RepositoryIdentity = $Context.RepositoryIdentity
            WorktreeRoot = $Context.WorktreeRoot; RouteId = $routeId; RouteName = $RouteName
            BrowserPath = [string]$lease.BrowserPath; Profile = Get-VerifierFullPath $profile
            LeaseId = [string]$lease.LeaseId; CdpPort = [int]$lease.Port
            RootProcessId = 0; RootProcessStartTicks = 0L
            RootParentProcessId = 0; RootParentProcessStartTicks = 0L
            RootProcessCommandLine = ''
            ListenerProcessId = 0; ListenerProcessStartTicks = 0L
        }
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
            RecoveryReceipt = $recoveryReceipt
            ContainmentLaunch = $null
            Runtime = [pscustomobject]@{ Browser = $null; Socket = $null; ContainmentJob = $null }
        }
        $browserLeaseSessionRecord.ContainmentLaunch = [pscustomobject]@{
            Protocol = 'troubleshootjs-verifier-browser-containment-launch-v1'
            JobName = Get-VerifierBrowserContainmentJobName $Context $browserLeaseSessionRecord
            State = 'unlaunched'; LaunchProcessId = 0; LaunchedUtc = ''
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

function Set-VerifierBrowserRecoveryReceiptBound($Context, $SessionRecord) {
    [void](Assert-VerifierDurableBrowserSession $SessionRecord $Context `
        'browser recovery receipt bind session')
    $receiptProperty = $SessionRecord.PSObject.Properties['RecoveryReceipt']
    if ($null -eq $receiptProperty -or $null -eq $receiptProperty.Value) {
        Throw-VerifierInfrastructure 'A newly launched browser session omitted its recovery receipt.'
    }
    $receipt = $receiptProperty.Value
    if ($receipt.State -ne 'issued' -or $receipt.CloseAttempted -or
            [int]$SessionRecord.ProcessId -le 0 -or
            [long]$SessionRecord.ProcessStartTicks -le 0 -or
            [int]$SessionRecord.ProcessParentProcessId -le 0 -or
            [long]$SessionRecord.ProcessParentProcessStartTicks -le 0 -or
            [String]::IsNullOrWhiteSpace([string]$SessionRecord.ProcessCommandLine) -or
            $SessionRecord.Status -ne 'attached' -or
            $SessionRecord.Lease.Status -ne 'bound' -or
            $SessionRecord.Lease.ClaimState -ne 'bound' -or
            [int]$SessionRecord.Lease.BoundProcessId -ne [int]$SessionRecord.ProcessId -or
            [long]$SessionRecord.Lease.BoundProcessStartTicks -ne
                [long]$SessionRecord.ProcessStartTicks -or
            [int]$SessionRecord.Lease.ListenerProcessId -ne [int]$SessionRecord.ProcessId -or
            [long]$SessionRecord.Lease.ListenerProcessStartTicks -ne
                [long]$SessionRecord.ProcessStartTicks -or
            -not (Test-VerifierCommandLineSwitch $SessionRecord.ProcessCommandLine `
                '--tsj-verifier-recovery-token' $receipt.AuthorityToken)) {
        Throw-VerifierInfrastructure 'Browser recovery receipt could not bind the exact launched root/listener tuple.'
    }
    $receipt.RootProcessId = [int]$SessionRecord.ProcessId
    $receipt.RootProcessStartTicks = [long]$SessionRecord.ProcessStartTicks
    $receipt.RootParentProcessId = [int]$SessionRecord.ProcessParentProcessId
    $receipt.RootParentProcessStartTicks = [long]$SessionRecord.ProcessParentProcessStartTicks
    $receipt.RootProcessCommandLine = [string]$SessionRecord.ProcessCommandLine
    $receipt.ListenerProcessId = [int]$SessionRecord.Lease.ListenerProcessId
    $receipt.ListenerProcessStartTicks = [long]$SessionRecord.Lease.ListenerProcessStartTicks
    $receipt.BoundUtc = Get-VerifierUtcText
    $receipt.State = 'bound'
    [void](Assert-VerifierDurableBrowserRecoveryReceipt $SessionRecord $Context `
        'browser recovery receipt bound state')
}

function Get-VerifierBindingPropertySnapshot($Object, [string[]]$PropertyNames) {
    if ($null -eq $Object -or $null -eq $PropertyNames) {
        Throw-VerifierInfrastructure 'Browser bind transaction snapshot omitted its exact object/property set.'
    }
    $snapshot = @{}
    foreach ($propertyName in $PropertyNames) {
        $property = $Object.PSObject.Properties[$propertyName]
        $snapshot[$propertyName] = [pscustomobject]@{
            Present = ($null -ne $property)
            Value = if ($null -eq $property) { $null } else { $property.Value }
        }
    }
    return $snapshot
}

function Restore-VerifierBindingPropertySnapshot($Object, $Snapshot) {
    if ($null -eq $Object -or $null -eq $Snapshot) {
        Throw-VerifierInfrastructure 'Browser bind transaction restore omitted its exact object/property snapshot.'
    }
    foreach ($propertyName in @($Snapshot.Keys)) {
        $entry = $Snapshot[$propertyName]
        if ($null -eq $entry -or -not (Test-VerifierStrictBooleanValue $entry.Present)) {
            Throw-VerifierInfrastructure 'Browser bind transaction restore carried a malformed property snapshot.'
        }
        $property = $Object.PSObject.Properties[$propertyName]
        if ($entry.Present) {
            if ($null -eq $property) {
                Add-Member -InputObject $Object -MemberType NoteProperty -Name $propertyName `
                    -Value $entry.Value
            } else {
                $property.Value = $entry.Value
            }
        } elseif ($null -ne $property) {
            [void]$Object.PSObject.Properties.Remove($propertyName)
        }
    }
}

function New-VerifierBrowserBindTransactionSnapshot($SessionRecord) {
    if ($null -eq $SessionRecord -or $null -eq $SessionRecord.Lease -or
            $null -eq $SessionRecord.RecoveryReceipt -or
            $null -eq $SessionRecord.ContainmentLaunch) {
        Throw-VerifierInfrastructure 'Browser bind transaction snapshot omitted one exact session lifecycle record.'
    }
    return [pscustomobject]@{
        Session = Get-VerifierBindingPropertySnapshot $SessionRecord @(
            'ProcessId', 'ProcessStartTicks', 'ProcessParentProcessId',
            'ProcessParentProcessStartTicks', 'ProcessCommandLine', 'Status',
            'CleanupResult', 'Error', 'TargetId')
        Lease = Get-VerifierBindingPropertySnapshot $SessionRecord.Lease @(
            'ListenerInspectionSuccess', 'ListenerInspectionKnown', 'ListenerHasListeners',
            'ListenerAbsent', 'ListenerProcessId', 'ListenerProcessStartTicks',
            'ListenerOwnerKind', 'ListenerOwnerProof', 'ListenerOwnerEvidence',
            'ListenerInspectionUtc', 'AuthorizationProof', 'BoundProcessId',
            'BoundProcessStartTicks', 'BindValidatedUtc', 'ClaimState', 'Status',
            'ProcessProofRequired')
        Receipt = Get-VerifierBindingPropertySnapshot $SessionRecord.RecoveryReceipt @(
            'RootProcessId', 'RootProcessStartTicks', 'RootParentProcessId',
            'RootParentProcessStartTicks', 'RootProcessCommandLine', 'ListenerProcessId',
            'ListenerProcessStartTicks', 'BoundUtc', 'State')
        Launch = Get-VerifierBindingPropertySnapshot $SessionRecord.ContainmentLaunch @(
            'LaunchProcessId', 'LaunchedUtc', 'State')
    }
}

function Restore-VerifierBrowserBindTransactionSnapshot($SessionRecord, $Snapshot) {
    if ($null -eq $SessionRecord -or $null -eq $Snapshot) {
        Throw-VerifierInfrastructure 'Browser bind transaction restore omitted its exact session snapshot.'
    }
    Restore-VerifierBindingPropertySnapshot $SessionRecord $Snapshot.Session
    Restore-VerifierBindingPropertySnapshot $SessionRecord.Lease $Snapshot.Lease
    Restore-VerifierBindingPropertySnapshot $SessionRecord.RecoveryReceipt $Snapshot.Receipt
    Restore-VerifierBindingPropertySnapshot $SessionRecord.ContainmentLaunch $Snapshot.Launch
}

function Invoke-VerifierBrowserBindTransaction($Context, $SessionRecord, $RootRecord,
        [scriptblock]$BindLease, [string]$TargetId = '') {
    if ($null -eq $RootRecord -or $null -eq $BindLease -or
            -not (Test-VerifierStrictStringValue $TargetId) -or
            -not (Test-VerifierStrictIntegralValue $RootRecord.ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $RootRecord.ProcessStartTicks 1) -or
            -not (Test-VerifierStrictIntegralValue $RootRecord.ParentProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $RootRecord.ParentProcessStartTicks 1) -or
            -not (Test-VerifierStrictStringValue $RootRecord.CommandLine) -or
            [String]::IsNullOrWhiteSpace([string]$RootRecord.CommandLine)) {
        Throw-VerifierInfrastructure 'Browser bind transaction omitted one complete exact root/listener proof input.'
    }
    $snapshot = New-VerifierBrowserBindTransactionSnapshot $SessionRecord
    try {
        # No tuple, lease, or receipt mutation is durable until the complete
        # root/listener proof has run and the bound receipt is ready to commit
        # in the same manifest replacement. A crash before that write leaves
        # the prior issued containment ledger as the only recovery authority.
        $SessionRecord.ProcessId = [int]$RootRecord.ProcessId
        $SessionRecord.ProcessStartTicks = [long]$RootRecord.ProcessStartTicks
        $SessionRecord.ProcessParentProcessId = [int]$RootRecord.ParentProcessId
        $SessionRecord.ProcessParentProcessStartTicks = [long]$RootRecord.ParentProcessStartTicks
        $SessionRecord.ProcessCommandLine = [string]$RootRecord.CommandLine
        $SessionRecord.Status = 'started'
        $SessionRecord.CleanupResult = 'pending'
        $SessionRecord.Error = ''
        [void](& $BindLease $SessionRecord)
        $SessionRecord.TargetId = $TargetId
        $SessionRecord.Status = 'attached'
        Set-VerifierBrowserRecoveryReceiptBound $Context $SessionRecord
        [void](Assert-VerifierDurableBrowserSession $SessionRecord $Context `
            'browser bind transaction committed state')
        Write-VerifierManifest $Context
    } catch {
        Restore-VerifierBrowserBindTransactionSnapshot $SessionRecord $snapshot
        throw
    }
}

function Record-VerifierBrowserRecoveryCloseAttempt($Context, $SessionRecord) {
    $receiptProperty = if ($null -eq $SessionRecord) { $null } else {
        $SessionRecord.PSObject.Properties['RecoveryReceipt']
    }
    if ($null -eq $receiptProperty -or $null -eq $receiptProperty.Value) { return }
    [void](Assert-VerifierDurableBrowserRecoveryReceipt $SessionRecord $Context `
        'browser recovery close-attempt receipt')
    $receipt = $receiptProperty.Value
    if ($receipt.State -ne 'bound' -or $receipt.CloseAttempted) {
        Throw-VerifierInfrastructure 'Browser recovery receipt does not authorize another uncertain close attempt.'
    }
    $receipt.CloseAttempted = $true
    $receipt.CloseAttemptedUtc = Get-VerifierUtcText
    [void](Assert-VerifierDurableBrowserRecoveryReceipt $SessionRecord $Context `
        'browser recovery close-attempt state')
    Write-VerifierManifest $Context
}

function Close-VerifierBrowserRecoveryReceipt($SessionRecord) {
    $receiptProperty = if ($null -eq $SessionRecord) { $null } else {
        $SessionRecord.PSObject.Properties['RecoveryReceipt']
    }
    if ($null -eq $receiptProperty -or $null -eq $receiptProperty.Value) { return }
    $receipt = $receiptProperty.Value
    if ($receipt.State -notin @('issued', 'bound')) {
        Throw-VerifierInfrastructure 'Browser recovery receipt was already closed or carried an unknown lifecycle state.'
    }
    $receipt.State = 'closed'
    $receipt.ClosedUtc = Get-VerifierUtcText
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
        # A same-named browser alone is not an ownership claim. Treating every
        # unrelated Edge/Chrome peer as relevant both blocks user processes on
        # transient inspection races and tempts later cleanup code to reason
        # about an unowned graph. The root must carry its exact profile/route
        # markers here; markerless same-executable helpers are admitted only
        # after the verified root's PPID traversal below.
        if (-not ($profileMatch -or $identityMatch)) { continue }
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

function Initialize-VerifierBrowserContainmentJobApi() {
    # A sampled PPID graph cannot prove that a short-lived direct child did not
    # leave an otherwise uninspectable orphan behind.  Every newly launched
    # browser is therefore created atomically in one named Windows job with
    # breakaway disabled.  The kernel retains every live descendant in that
    # job, including a child whose command line cannot be read, so an empty
    # member list after Browser.close is a stronger containment proof than a
    # filtered process/profile scan.
    $typeName = 'VerifierBrowserContainmentJobApi'
    $loadedType = ([System.Management.Automation.PSTypeName]$typeName).Type
    if ($null -ne $loadedType) { return $loadedType }
    $source = @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;

public sealed class VerifierBrowserContainmentJobApi : IDisposable {
    private const uint EXTENDED_STARTUPINFO_PRESENT = 0x00080000;
    private const uint CREATE_NO_WINDOW = 0x08000000;
    private const uint CREATE_SUSPENDED = 0x00000004;
    private const uint JOB_OBJECT_QUERY = 0x0004;
    private const int JOB_OBJECT_BASIC_PROCESS_ID_LIST = 3;
    private const int JOB_OBJECT_EXTENDED_LIMIT_INFORMATION = 9;
    private const int ERROR_ALREADY_EXISTS = 183;
    private const int ERROR_MORE_DATA = 234;
    private const uint JOB_OBJECT_LIMIT_BREAKAWAY_OK = 0x00000800;
    private const uint JOB_OBJECT_LIMIT_SILENT_BREAKAWAY_OK = 0x00001000;
    private const uint JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x00002000;
    private const int PROCESS_ID_LIST_HEADER_BYTES = 8;
    private const uint WAIT_OBJECT_0 = 0x00000000;
    private const uint INVALID_RESUME_COUNT = 0xFFFFFFFF;
    private static readonly IntPtr PROC_THREAD_ATTRIBUTE_JOB_LIST =
        (IntPtr)0x0002000D;

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct STARTUPINFO {
        public int cb;
        public string lpReserved;
        public string lpDesktop;
        public string lpTitle;
        public int dwX;
        public int dwY;
        public int dwXSize;
        public int dwYSize;
        public int dwXCountChars;
        public int dwYCountChars;
        public int dwFillAttribute;
        public int dwFlags;
        public short wShowWindow;
        public short cbReserved2;
        public IntPtr lpReserved2;
        public IntPtr hStdInput;
        public IntPtr hStdOutput;
        public IntPtr hStdError;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct STARTUPINFOEX {
        public STARTUPINFO StartupInfo;
        public IntPtr lpAttributeList;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct PROCESS_INFORMATION {
        public IntPtr hProcess;
        public IntPtr hThread;
        public int dwProcessId;
        public int dwThreadId;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct JOBOBJECT_BASIC_LIMIT_INFORMATION {
        public long PerProcessUserTimeLimit;
        public long PerJobUserTimeLimit;
        public uint LimitFlags;
        public UIntPtr MinimumWorkingSetSize;
        public UIntPtr MaximumWorkingSetSize;
        public uint ActiveProcessLimit;
        public UIntPtr Affinity;
        public uint PriorityClass;
        public uint SchedulingClass;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct IO_COUNTERS {
        public ulong ReadOperationCount;
        public ulong WriteOperationCount;
        public ulong OtherOperationCount;
        public ulong ReadTransferCount;
        public ulong WriteTransferCount;
        public ulong OtherTransferCount;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct JOBOBJECT_EXTENDED_LIMIT_INFORMATION {
        public JOBOBJECT_BASIC_LIMIT_INFORMATION BasicLimitInformation;
        public IO_COUNTERS IoInfo;
        public UIntPtr ProcessMemoryLimit;
        public UIntPtr JobMemoryLimit;
        public UIntPtr PeakProcessMemoryUsed;
        public UIntPtr PeakJobMemoryUsed;
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr CreateJobObject(IntPtr attributes, string name);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr OpenJobObject(uint desiredAccess,
        [MarshalAs(UnmanagedType.Bool)] bool inheritHandle, string name);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CloseHandle(IntPtr handle);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool InitializeProcThreadAttributeList(IntPtr list,
        int attributeCount, int flags, ref IntPtr size);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool UpdateProcThreadAttribute(IntPtr list, uint flags,
        IntPtr attribute, IntPtr value, IntPtr size, IntPtr previousValue,
        IntPtr returnSize);

    [DllImport("kernel32.dll")]
    private static extern void DeleteProcThreadAttributeList(IntPtr list);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CreateProcess(string applicationName,
        StringBuilder commandLine, IntPtr processAttributes, IntPtr threadAttributes,
        [MarshalAs(UnmanagedType.Bool)] bool inheritHandles, uint creationFlags,
        IntPtr environment, string currentDirectory, ref STARTUPINFOEX startupInfo,
        out PROCESS_INFORMATION processInformation);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool QueryInformationJobObject(IntPtr job,
        int informationClass, IntPtr information, uint informationLength,
        out uint returnLength);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool QueryInformationJobObject(IntPtr job,
        int informationClass, out JOBOBJECT_EXTENDED_LIMIT_INFORMATION information,
        uint informationLength, out uint returnLength);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetInformationJobObject(IntPtr job,
        int informationClass, ref JOBOBJECT_EXTENDED_LIMIT_INFORMATION information,
        uint informationLength);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool DuplicateHandle(IntPtr sourceProcess,
        IntPtr sourceHandle, IntPtr targetProcess, out IntPtr targetHandle,
        uint desiredAccess, [MarshalAs(UnmanagedType.Bool)] bool inheritHandle,
        uint options);

    [DllImport("kernel32.dll")]
    private static extern IntPtr GetCurrentProcess();

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern uint ResumeThread(IntPtr thread);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool TerminateProcess(IntPtr process, uint exitCode);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern uint WaitForSingleObject(IntPtr handle, uint milliseconds);

    private IntPtr handle;
    public string Name { get; private set; }

    private VerifierBrowserContainmentJobApi(IntPtr value, string name) {
        handle = value;
        Name = name;
    }

    private static bool IsInvalid(IntPtr value) {
        return value == IntPtr.Zero || value == new IntPtr(-1);
    }

    private void AssertOpen() {
        if (IsInvalid(handle)) {
            throw new ObjectDisposedException("VerifierBrowserContainmentJobApi");
        }
    }

    private void AssertNoBreakaway() {
        AssertOpen();
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION information;
        uint returned;
        if (!QueryInformationJobObject(handle,
                JOB_OBJECT_EXTENDED_LIMIT_INFORMATION, out information,
                (uint)Marshal.SizeOf(typeof(JOBOBJECT_EXTENDED_LIMIT_INFORMATION)),
                out returned)) {
            throw new Win32Exception(Marshal.GetLastWin32Error(),
                "Could not inspect browser containment job limits.");
        }
        uint breakaway = JOB_OBJECT_LIMIT_BREAKAWAY_OK |
            JOB_OBJECT_LIMIT_SILENT_BREAKAWAY_OK;
        if ((information.BasicLimitInformation.LimitFlags & breakaway) != 0) {
            throw new InvalidOperationException(
                "Browser containment job permits process breakaway.");
        }
        if ((information.BasicLimitInformation.LimitFlags &
                JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE) == 0) {
            throw new InvalidOperationException(
                "Browser containment job does not kill contained remnants when its final handle closes.");
        }
    }

    private void ConfigureNoBreakawayKillOnClose() {
        AssertOpen();
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION information =
            new JOBOBJECT_EXTENDED_LIMIT_INFORMATION();
        information.BasicLimitInformation.LimitFlags =
            JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        if (!SetInformationJobObject(handle,
                JOB_OBJECT_EXTENDED_LIMIT_INFORMATION, ref information,
                (uint)Marshal.SizeOf(typeof(JOBOBJECT_EXTENDED_LIMIT_INFORMATION)))) {
            throw new Win32Exception(Marshal.GetLastWin32Error(),
                "Could not configure browser containment job limits.");
        }
        AssertNoBreakaway();
    }

    public static VerifierBrowserContainmentJobApi CreateNew(string name) {
        if (String.IsNullOrWhiteSpace(name)) {
            throw new ArgumentException("A browser containment job needs a name.",
                "name");
        }
        IntPtr created = CreateJobObject(IntPtr.Zero, name);
        int error = Marshal.GetLastWin32Error();
        if (IsInvalid(created)) {
            throw new Win32Exception(error,
                "Could not create browser containment job.");
        }
        if (error == ERROR_ALREADY_EXISTS) {
            CloseHandle(created);
            throw new InvalidOperationException(
                "Browser containment job name was already present.");
        }
        VerifierBrowserContainmentJobApi result =
            new VerifierBrowserContainmentJobApi(created, name);
        try {
            result.ConfigureNoBreakawayKillOnClose();
            return result;
        } catch {
            result.Dispose();
            throw;
        }
    }

    public static VerifierBrowserContainmentJobApi OpenExisting(string name) {
        if (String.IsNullOrWhiteSpace(name)) {
            throw new ArgumentException("A browser containment job needs a name.",
                "name");
        }
        IntPtr opened = OpenJobObject(JOB_OBJECT_QUERY, false, name);
        if (IsInvalid(opened)) {
            throw new Win32Exception(Marshal.GetLastWin32Error(),
                "Could not open browser containment job.");
        }
        VerifierBrowserContainmentJobApi result =
            new VerifierBrowserContainmentJobApi(opened, name);
        try {
            result.AssertNoBreakaway();
            return result;
        } catch {
            result.Dispose();
            throw;
        }
    }

    public int Launch(string applicationName, string commandLine,
        string currentDirectory) {
        AssertOpen();
        AssertNoBreakaway();
        if (String.IsNullOrWhiteSpace(applicationName) ||
                String.IsNullOrWhiteSpace(commandLine)) {
            throw new ArgumentException(
                "Browser containment launch requires an application and command line.");
        }
        IntPtr attributeList = IntPtr.Zero;
        IntPtr attributeListSize = IntPtr.Zero;
        IntPtr jobValue = IntPtr.Zero;
        PROCESS_INFORMATION processInformation = new PROCESS_INFORMATION();
        bool initialized = false;
        bool createdProcess = false;
        bool resumed = false;
        try {
            InitializeProcThreadAttributeList(IntPtr.Zero, 1, 0,
                ref attributeListSize);
            int initialError = Marshal.GetLastWin32Error();
            if (attributeListSize == IntPtr.Zero) {
                throw new Win32Exception(initialError,
                    "Could not size browser containment launch attributes.");
            }
            attributeList = Marshal.AllocHGlobal(attributeListSize);
            if (!InitializeProcThreadAttributeList(attributeList, 1, 0,
                    ref attributeListSize)) {
                throw new Win32Exception(Marshal.GetLastWin32Error(),
                    "Could not initialize browser containment launch attributes.");
            }
            initialized = true;
            jobValue = Marshal.AllocHGlobal(IntPtr.Size);
            Marshal.WriteIntPtr(jobValue, handle);
            if (!UpdateProcThreadAttribute(attributeList, 0,
                    PROC_THREAD_ATTRIBUTE_JOB_LIST, jobValue,
                    (IntPtr)IntPtr.Size, IntPtr.Zero, IntPtr.Zero)) {
                throw new Win32Exception(Marshal.GetLastWin32Error(),
                    "Could not bind the browser launch to its containment job.");
            }
            STARTUPINFOEX startupInfo = new STARTUPINFOEX();
            startupInfo.StartupInfo.cb = Marshal.SizeOf(typeof(STARTUPINFOEX));
            startupInfo.lpAttributeList = attributeList;
            if (!CreateProcess(applicationName, new StringBuilder(commandLine),
                    IntPtr.Zero, IntPtr.Zero, false,
                    EXTENDED_STARTUPINFO_PRESENT | CREATE_NO_WINDOW | CREATE_SUSPENDED, IntPtr.Zero,
                    currentDirectory, ref startupInfo, out processInformation)) {
                throw new Win32Exception(Marshal.GetLastWin32Error(),
                    "Could not atomically launch browser in its containment job.");
            }
            if (processInformation.dwProcessId <= 0) {
                throw new InvalidOperationException(
                    "Browser containment launch returned an invalid PID.");
            }
            createdProcess = true;
            IntPtr childJobHandle;
            if (!DuplicateHandle(GetCurrentProcess(), handle,
                    processInformation.hProcess, out childJobHandle,
                    JOB_OBJECT_QUERY, false, 0)) {
                throw new Win32Exception(Marshal.GetLastWin32Error(),
                    "Could not retain the browser containment job in its exact root process.");
            }
            // childJobHandle belongs to the suspended root process. It is a
            // query-only, non-inheritable lifetime reference; the root cannot
            // use it to assign, terminate, or break away other job members.
            if (ResumeThread(processInformation.hThread) == INVALID_RESUME_COUNT) {
                throw new Win32Exception(Marshal.GetLastWin32Error(),
                    "Could not resume the atomically contained browser root.");
            }
            resumed = true;
            return processInformation.dwProcessId;
        } catch (Exception launchFailure) {
            if (createdProcess && !resumed &&
                    processInformation.hProcess != IntPtr.Zero) {
                bool terminated = TerminateProcess(processInformation.hProcess, 2);
                uint waited = WaitForSingleObject(processInformation.hProcess, 5000);
                if (!terminated || waited != WAIT_OBJECT_0) {
                    throw new InvalidOperationException(
                        "Browser containment launch rollback could not prove suspended-root termination.",
                        launchFailure);
                }
            }
            throw;
        } finally {
            if (processInformation.hThread != IntPtr.Zero) {
                CloseHandle(processInformation.hThread);
            }
            if (processInformation.hProcess != IntPtr.Zero) {
                CloseHandle(processInformation.hProcess);
            }
            if (jobValue != IntPtr.Zero) { Marshal.FreeHGlobal(jobValue); }
            if (initialized) { DeleteProcThreadAttributeList(attributeList); }
            if (attributeList != IntPtr.Zero) { Marshal.FreeHGlobal(attributeList); }
        }
    }

    public int[] GetMemberProcessIds() {
        AssertOpen();
        AssertNoBreakaway();
        uint capacity = (uint)(PROCESS_ID_LIST_HEADER_BYTES +
            (IntPtr.Size * 16));
        for (int attempt = 0; attempt < 8; attempt++) {
            IntPtr buffer = Marshal.AllocHGlobal((int)capacity);
            try {
                uint required;
                bool success = QueryInformationJobObject(handle,
                    JOB_OBJECT_BASIC_PROCESS_ID_LIST, buffer, capacity,
                    out required);
                if (!success) {
                    int error = Marshal.GetLastWin32Error();
                    if (error != ERROR_MORE_DATA) {
                        throw new Win32Exception(error,
                            "Could not inspect browser containment job members.");
                    }
                    uint next = required > capacity ? required :
                        capacity + (uint)(IntPtr.Size * 16);
                    if (next > 1024 * 1024) {
                        throw new InvalidOperationException(
                            "Browser containment job member list was unreasonably large.");
                    }
                    capacity = next;
                    continue;
                }
                int assigned = Marshal.ReadInt32(buffer, 0);
                int count = Marshal.ReadInt32(buffer, 4);
                if (assigned < 0 || count < 0 || count > assigned ||
                        PROCESS_ID_LIST_HEADER_BYTES +
                        ((long)count * IntPtr.Size) > capacity) {
                    throw new InvalidOperationException(
                        "Browser containment job returned a malformed member list.");
                }
                List<int> members = new List<int>();
                HashSet<int> seen = new HashSet<int>();
                for (int index = 0; index < count; index++) {
                    long raw = IntPtr.Size == 8 ? Marshal.ReadInt64(buffer,
                        PROCESS_ID_LIST_HEADER_BYTES + (index * IntPtr.Size)) :
                        Marshal.ReadInt32(buffer,
                        PROCESS_ID_LIST_HEADER_BYTES + (index * IntPtr.Size));
                    if (raw <= 0 || raw > Int32.MaxValue ||
                            !seen.Add((int)raw)) {
                        throw new InvalidOperationException(
                            "Browser containment job returned an invalid or duplicate member PID.");
                    }
                    members.Add((int)raw);
                }
                return members.ToArray();
            } finally {
                Marshal.FreeHGlobal(buffer);
            }
        }
        throw new InvalidOperationException(
            "Browser containment job membership changed too quickly to inspect safely.");
    }

    public void Dispose() {
        if (!IsInvalid(handle)) {
            IntPtr closing = handle;
            handle = IntPtr.Zero;
            if (!CloseHandle(closing)) {
                throw new Win32Exception(Marshal.GetLastWin32Error(),
                    "Could not close browser containment job handle.");
            }
        }
    }
}
'@
    try {
        Add-Type -TypeDefinition $source -Language CSharp `
            -ReferencedAssemblies @('System.dll', 'System.Core.dll') -ErrorAction Stop | Out-Null
    } catch {
        Throw-VerifierInfrastructure ('Could not load the browser containment-job helper: ' +
            (Get-VerifierErrorMessage $_))
    }
    $loadedType = ([System.Management.Automation.PSTypeName]$typeName).Type
    if ($null -eq $loadedType) {
        Throw-VerifierInfrastructure 'The browser containment-job helper did not load a usable type.'
    }
    return $loadedType
}

function Get-VerifierBrowserContainmentJobName($Context, $SessionRecord) {
    [void](Assert-VerifierDurableBrowserRecoveryReceipt $SessionRecord $Context `
        'browser containment-job receipt')
    $receipt = $SessionRecord.RecoveryReceipt
    if ($null -eq $receipt -or -not (Test-VerifierBrowserRecoveryAuthorityToken `
            $receipt.AuthorityToken)) {
        Throw-VerifierInfrastructure 'Browser containment-job derivation requires an exact durable recovery receipt.'
    }
    $material = ([string]$Context.RunId + "`n" + [string]$Context.RepositoryIdentity +
        "`n" + [string]$SessionRecord.RouteId + "`n" +
        [string]$receipt.AuthorityToken)
    $hasher = [Security.Cryptography.SHA256]::Create()
    try {
        $digest = [BitConverter]::ToString($hasher.ComputeHash(
            [Text.Encoding]::UTF8.GetBytes($material))).Replace('-', '').ToLowerInvariant()
        return 'Local\TroubleshootJS.Verifier.BrowserContainment.' + $digest
    } finally {
        $hasher.Dispose()
    }
}

function New-VerifierBrowserContainmentJob($Context, $SessionRecord) {
    $type = Initialize-VerifierBrowserContainmentJobApi
    $name = Get-VerifierBrowserContainmentJobName $Context $SessionRecord
    try {
        $job = $type::CreateNew($name)
        if ($null -eq $job -or $job.GetType() -ne $type -or $job.Name -cne $name) {
            Throw-VerifierInfrastructure 'Browser containment-job construction returned an invalid exact job identity.'
        }
        return $job
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not create the exact browser containment job: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function Prepare-VerifierBrowserContainmentLaunch($Context, $SessionRecord,
        $ContainmentJob) {
    [void](Assert-VerifierDurableBrowserSession $SessionRecord $Context `
        'browser containment launch preparation session')
    $launch = Assert-VerifierDurableBrowserContainmentLaunch $SessionRecord $Context `
        'browser containment launch preparation record'
    $type = Initialize-VerifierBrowserContainmentJobApi
    $expectedJobName = Get-VerifierBrowserContainmentJobName $Context $SessionRecord
    if ($null -eq $launch -or $launch.State -ne 'unlaunched' -or
            [int]$launch.LaunchProcessId -ne 0 -or
            -not [String]::IsNullOrWhiteSpace([string]$launch.LaunchedUtc) -or
            $SessionRecord.RecoveryReceipt.State -ne 'issued' -or
            $SessionRecord.RecoveryReceipt.CloseAttempted -or
            $SessionRecord.Status -ne 'leased' -or
            $null -eq $ContainmentJob -or $ContainmentJob.GetType() -ne $type -or
            $ContainmentJob.Name -cne $expectedJobName -or
            $launch.JobName -cne $expectedJobName) {
        Throw-VerifierInfrastructure 'Browser containment launch preparation was not one exact unlaunched issued state.'
    }
    # Persist this intent before native CreateProcess. The atomic job launch can
    # succeed immediately after the call boundary; a crash before its PID is
    # published must remain recoverable by reopening this exact named job and
    # reattesting a root that carries the opaque receipt capability.
    $launch.State = 'launch-pending'
    try {
        [void](Assert-VerifierDurableBrowserContainmentLaunch $SessionRecord $Context `
            'browser containment launch pending state')
        Write-VerifierManifest $Context
    } catch {
        $launch.State = 'unlaunched'
        throw
    }
}

function Set-VerifierBrowserContainmentLaunch($Context, $SessionRecord,
        $ContainmentJob, $ProcessId) {
    [void](Assert-VerifierDurableBrowserSession $SessionRecord $Context `
        'browser containment launch session')
    if (-not (Test-VerifierStrictIntegralValue $ProcessId 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'Browser containment launch returned a malformed positive process ID.'
    }
    $launch = Assert-VerifierDurableBrowserContainmentLaunch $SessionRecord $Context `
        'browser containment launch record'
    if ($null -eq $launch -or $launch.State -ne 'launch-pending' -or
            [int]$launch.LaunchProcessId -ne 0 -or
            -not [String]::IsNullOrWhiteSpace([string]$launch.LaunchedUtc)) {
        Throw-VerifierInfrastructure 'Browser containment launch was not in the exact durable launch-pending state.'
    }
    $type = Initialize-VerifierBrowserContainmentJobApi
    $expectedJobName = Get-VerifierBrowserContainmentJobName $Context $SessionRecord
    if ($null -eq $ContainmentJob -or $ContainmentJob.GetType() -ne $type -or
            $ContainmentJob.Name -cne $expectedJobName -or
            $launch.JobName -cne $expectedJobName) {
        Throw-VerifierInfrastructure 'Browser containment launch did not retain its exact typed job identity.'
    }
    # Publish the atomically assigned CreateProcess PID before the first
    # fallible Process handle, WMI identity, CDP, or listener operation. The
    # job name plus this PID are recovery evidence, not a termination grant.
    $launch.LaunchProcessId = [int]$ProcessId
    $launch.LaunchedUtc = Get-VerifierUtcText
    $launch.State = 'launched'
    [void](Assert-VerifierDurableBrowserContainmentLaunch $SessionRecord $Context `
        'browser containment launched state')
}

function Get-VerifierBrowserContainmentJobForSession($Context, $SessionRecord) {
    $type = Initialize-VerifierBrowserContainmentJobApi
    $name = Get-VerifierBrowserContainmentJobName $Context $SessionRecord
    if ($null -ne $SessionRecord -and $SessionRecord.PSObject.Properties['Runtime'] -and
            $null -ne $SessionRecord.Runtime -and
            $SessionRecord.Runtime.PSObject.Properties['ContainmentJob'] -and
            $null -ne $SessionRecord.Runtime.ContainmentJob) {
        $runtimeJob = $SessionRecord.Runtime.ContainmentJob
        if ($runtimeJob.GetType() -ne $type -or $runtimeJob.Name -cne $name) {
            Throw-VerifierInfrastructure 'Browser session carried a foreign or malformed runtime containment-job handle.'
        }
        try { [void]$runtimeJob.GetMemberProcessIds() } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ('Could not inspect the runtime browser containment job: ' +
                (Get-VerifierErrorMessage $_))
        }
        return [pscustomobject]@{ Job = $runtimeJob; DisposeAfterUse = $false }
    }
    try {
        $opened = $type::OpenExisting($name)
        if ($null -eq $opened -or $opened.GetType() -ne $type -or $opened.Name -cne $name) {
            Throw-VerifierInfrastructure 'Browser containment-job recovery opened an invalid exact job identity.'
        }
        return [pscustomobject]@{ Job = $opened; DisposeAfterUse = $true }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not reopen the exact browser containment job: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function Assert-VerifierBrowserContainmentJobContainsRoot($ContainmentJob,
        $RootRecord) {
    if ($null -eq $ContainmentJob -or $null -eq $RootRecord -or
            -not $RootRecord.PSObject.Properties['ProcessId'] -or
            -not (Test-VerifierStrictIntegralValue $RootRecord.ProcessId 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'Browser containment membership proof omitted an exact root identity.'
    }
    try {
        $members = @($ContainmentJob.GetMemberProcessIds())
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not inspect browser containment membership: ' +
            (Get-VerifierErrorMessage $_))
    }
    $rootPid = [int]$RootRecord.ProcessId
    if ($members.Count -eq 0 -or $members -notcontains $rootPid) {
        Throw-VerifierInfrastructure "Browser containment job did not retain exact root PID $rootPid before Browser.close."
    }
    return @($members)
}

function Wait-VerifierBrowserContainmentJobEmpty($ContainmentJob, $Budget,
        [long]$BudgetTicks) {
    if ($null -eq $ContainmentJob -or $null -eq $Budget) {
        Throw-VerifierInfrastructure 'Browser containment quiescence omitted its exact job or shutdown budget.'
    }
    while ($true) {
        Assert-VerifierBrowserShutdownDeadline $Budget $BudgetTicks
        try {
            $members = @($ContainmentJob.GetMemberProcessIds())
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ('Could not prove browser containment job quiescence: ' +
                (Get-VerifierErrorMessage $_))
        }
        if ($members.Count -eq 0) { return }
        Start-Sleep -Milliseconds 25
        Assert-VerifierBrowserShutdownDeadline $Budget $BudgetTicks
    }
}

function Dispose-VerifierBrowserContainmentJob($SessionRecord) {
    if ($null -eq $SessionRecord -or -not $SessionRecord.PSObject.Properties['Runtime'] -or
            $null -eq $SessionRecord.Runtime -or
            -not $SessionRecord.Runtime.PSObject.Properties['ContainmentJob'] -or
            $null -eq $SessionRecord.Runtime.ContainmentJob) {
        return
    }
    $job = $SessionRecord.Runtime.ContainmentJob
    try {
        $job.Dispose()
        $SessionRecord.Runtime.ContainmentJob = $null
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not dispose the browser containment-job handle: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function Start-VerifierBrowserProcessInContainmentJob($ContainmentJob,
        $FilePath, $Arguments, [string]$WorkingDirectory) {
    $invocation = Assert-VerifierProcessInvocationBoundary $FilePath $Arguments
    if ($null -eq $ContainmentJob -or
            $ContainmentJob.GetType() -ne (Initialize-VerifierBrowserContainmentJobApi)) {
        Throw-VerifierInfrastructure 'Browser containment launch omitted the exact typed job handle.'
    }
    $working = Get-VerifierFullPath $WorkingDirectory
    $commandLine = (ConvertTo-VerifierWindowsArgument $invocation.FilePath)
    if (@($invocation.Arguments).Count -gt 0) {
        $commandLine += ' ' + (ConvertTo-VerifierArgumentString $invocation.Arguments)
    }
    try {
        $processId = [int]$ContainmentJob.Launch($invocation.FilePath,
            $commandLine, $working)
        if ($processId -le 0) {
            Throw-VerifierInfrastructure 'Atomically contained browser launch returned an invalid process ID.'
        }
        return $processId
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not atomically launch the browser in its containment job: ' +
            (Get-VerifierErrorMessage $_))
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

function Get-VerifierRetainedBrowserRecoveryLeaseAuthority($Context, $Lease,
        [string]$Label = 'retained browser recovery lease authority') {
    # Ordinary live leases deliberately carry no recovery authority.  A
    # retained-session recovery installs this in-memory capability only after
    # it has acquired the exact named mutex and proved the original claimer
    # absent without PID reuse.  It is never serialized or inferred from a
    # marker/profile/PID alone.
    if ($null -eq $Lease -or
            $null -eq $Lease.PSObject.Properties['RetainedRecoveryAuthority'] -or
            $null -eq $Lease.RetainedRecoveryAuthority) {
        return $null
    }
    $authority = $Lease.RetainedRecoveryAuthority
    if ($authority -is [array]) {
        Throw-VerifierInfrastructure "$Label was not one exact recovery authority object."
    }
    foreach ($propertyName in @('Protocol', 'ManifestPath')) {
        $property = $authority.PSObject.Properties[$propertyName]
        if ($null -eq $property -or -not (Test-VerifierStrictStringValue $property.Value) -or
                [String]::IsNullOrWhiteSpace([string]$property.Value)) {
            Throw-VerifierInfrastructure "$Label omitted its exact non-empty '$propertyName' field."
        }
    }
    foreach ($propertyName in @('RecoveryProcessId', 'ClaimOwnerPid')) {
        $property = $authority.PSObject.Properties[$propertyName]
        if ($null -eq $property -or
                -not (Test-VerifierStrictIntegralValue $property.Value 1 ([int]::MaxValue))) {
            Throw-VerifierInfrastructure "$Label omitted its exact positive '$propertyName' field."
        }
    }
    foreach ($propertyName in @('RecoveryProcessStartTicks', 'ClaimOwnerStartTicks')) {
        $property = $authority.PSObject.Properties[$propertyName]
        if ($null -eq $property -or
                -not (Test-VerifierStrictIntegralValue $property.Value 1 ([long]::MaxValue))) {
            Throw-VerifierInfrastructure "$Label omitted its exact positive '$propertyName' field."
        }
    }
    foreach ($propertyName in @('ClaimMutex', 'LockHeld')) {
        if ($null -eq $authority.PSObject.Properties[$propertyName]) {
            Throw-VerifierInfrastructure "$Label omitted '$propertyName'."
        }
    }
    if ($authority.Protocol -cne 'troubleshootjs-verifier-retained-browser-recovery-v1' -or
            -not (Test-VerifierStrictBooleanValue $authority.LockHeld) -or
            -not $authority.LockHeld -or
            [int]$authority.RecoveryProcessId -ne [int]$PID -or
            [long]$authority.RecoveryProcessStartTicks -ne
                (Get-VerifierCurrentProcessStartTicks) -or
            [int]$authority.ClaimOwnerPid -ne [int]$Lease.ClaimOwnerPid -or
            [long]$authority.ClaimOwnerStartTicks -ne
                [long]$Lease.ClaimOwnerStartTicks -or
            $null -eq $Lease.PSObject.Properties['ClaimMutex'] -or
            $null -eq $Lease.ClaimMutex -or
            $Lease.ClaimMutex.GetType() -ne [Threading.Mutex] -or
            -not [object]::ReferenceEquals($authority.ClaimMutex, $Lease.ClaimMutex) -or
            $null -eq $Context -or
            -not (Test-VerifierCanonicalWindowsPathValue $authority.ManifestPath `
                $Context.ManifestPath)) {
        Throw-VerifierInfrastructure "$Label did not retain the exact current recovery lock and original claim association."
    }
    $originalClaimOwner = [pscustomobject]@{
        ProcessId = [int]$authority.ClaimOwnerPid
        ProcessStartTicks = [long]$authority.ClaimOwnerStartTicks
    }
    $absence = Confirm-VerifierRecordedProcessAbsent $originalClaimOwner `
        'retained browser recovery original claim owner'
    if (-not $absence.QueryProven -or -not $absence.Absent -or $absence.Replaced) {
        Throw-VerifierInfrastructure "$Label could not prove the original claim owner absent without PID reuse."
    }
    return $authority
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
            # The bounded startup window governs publication of the first
            # complete executable-path observation.  The proof below re-reads
            # every exact identity field and may itself take longer than that
            # observation window on a busy WMI provider; it is not another
            # path-publication retry and it still rejects every changed,
            # missing, malformed, or exited process result.
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

function Get-VerifierFreshLaunchedBrowserIdentity($BrowserProcess, [int]$CdpPort,
        [string]$RunId, [string]$BrowserPath, [DateTime]$Deadline) {
    # Browser startup may publish a complete root/listener before the first
    # Win32_Process identity query can finish inside its 500 ms proof budget.
    # Keep that per-proof deadline unchanged. This launch-only helper retains
    # one Process handle and permits at most three fresh proofs while the
    # route deadline remains open; it never publishes a partial tuple or
    # substitutes a process found by PID.
    if ($null -eq $BrowserProcess -or $BrowserProcess.GetType() -ne
            [Diagnostics.Process] -or
            -not (Test-VerifierStrictIntegralValue $CdpPort 1 65535) -or
            -not (Test-VerifierStrictStringValue $RunId) -or
            [String]::IsNullOrWhiteSpace($RunId) -or
            -not (Test-VerifierStrictStringValue $BrowserPath) -or
            [String]::IsNullOrWhiteSpace($BrowserPath) -or
            $Deadline -eq [DateTime]::MinValue) {
        Throw-VerifierInfrastructure 'Fresh browser identity startup received malformed retained-process or route identity input.'
    }
    $browserProcessId = 0
    try { $browserProcessId = [int]$BrowserProcess.Id } catch {
        Throw-VerifierInfrastructure ('Could not read the retained launched browser PID: ' +
            (Get-VerifierErrorMessage $_))
    }
    if ($browserProcessId -le 0) {
        Throw-VerifierInfrastructure 'Fresh browser identity startup received a non-positive retained browser PID.'
    }
    $browserProcessStartTicks = Get-VerifierProcessStartTicks $BrowserProcess
    $lastError = $null
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        if ([DateTime]::UtcNow -ge $Deadline) {
            Throw-VerifierInfrastructure 'Fresh browser identity startup exhausted its route deadline before an exact proof.'
        }
        try {
            [void]$BrowserProcess.Refresh()
            if ([bool]$BrowserProcess.HasExited -or
                    [int]$BrowserProcess.Id -ne $browserProcessId -or
                    (Get-VerifierProcessStartTicks $BrowserProcess) -ne
                        $browserProcessStartTicks) {
                Throw-VerifierInfrastructure 'Fresh browser identity startup lost its exact retained root PID/start identity.'
            }
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ('Could not refresh the retained launched browser identity: ' +
                (Get-VerifierErrorMessage $_))
        }
        try {
            return (Get-VerifierCurrentProcessIdentityWithRetry $browserProcessId `
                $browserProcessStartTicks 0 '' '' $CdpPort $RunId '' 0 $BrowserPath)
        } catch {
            $lastError = $_
            $message = Get-VerifierErrorMessage $_
            $transientPublicationDelay = $message -match
                'Current process identity retry exceeded its monotonic startup budget\.'
            if (-not $transientPublicationDelay -or $attempt -ge 3) {
                if (Test-VerifierInfrastructureError $_) { throw }
                Throw-VerifierInfrastructure ('Fresh browser identity startup failed: ' + $message)
            }
            if ([DateTime]::UtcNow.AddMilliseconds(100) -ge $Deadline) {
                Throw-VerifierInfrastructure ('Fresh browser identity startup could not retry within the route deadline: ' +
                    $message)
            }
            Start-Sleep -Milliseconds 100
        }
    }
    Throw-VerifierInfrastructure ('Fresh browser identity startup exhausted its bounded proof attempts: ' +
        (Get-VerifierErrorMessage $lastError))
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

function New-VerifierBrowserDrainAttestation($DrainScope, $Record, $Process,
        [switch]$ObservationOnly) {
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
            -not (Test-VerifierStrictStringValue $Record.ExecutablePath) -or
            -not (Test-VerifierStrictStringValue $Record.CommandLine) -or
            ((-not $ObservationOnly) -and
                ([String]::IsNullOrWhiteSpace([string]$Record.Name) -or
                 [String]::IsNullOrWhiteSpace([string]$Record.ExecutablePath) -or
                 [String]::IsNullOrWhiteSpace([string]$Record.CommandLine)))) {
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
        Mode = if ($ObservationOnly) { 'natural-close-observation' } else { 'termination' }
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
            -not $attestation.PSObject.Properties['Mode'] -or
            -not (Test-VerifierStrictStringValue $attestation.Mode) -or
            $attestation.Mode -cnotin @('termination', 'natural-close-observation') -or
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
    $observationOnly = ($attestation.Mode -ceq 'natural-close-observation')
    if (-not (Test-VerifierStrictIntegralValue $attestation.ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $attestation.ProcessStartTicks 1 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $attestation.ParentProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $attestation.ParentProcessStartTicks 1 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictStringValue $attestation.Name) -or
            -not (Test-VerifierStrictStringValue $attestation.ExecutablePath) -or
            -not (Test-VerifierStrictStringValue $attestation.CommandLine) -or
            ((-not $observationOnly) -and
                ([String]::IsNullOrWhiteSpace([string]$attestation.Name) -or
                 [String]::IsNullOrWhiteSpace([string]$attestation.ExecutablePath) -or
                 [String]::IsNullOrWhiteSpace([string]$attestation.CommandLine))) -or
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
            ((-not $observationOnly) -and
                ([String]::IsNullOrWhiteSpace([string]$Record.Name) -or
                 [String]::IsNullOrWhiteSpace([string]$Record.ExecutablePath) -or
                 [String]::IsNullOrWhiteSpace([string]$Record.CommandLine))) -or
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

function Assert-VerifierBrowserDrainTerminationAttestation($DrainScope, $Record) {
    $attestation = Get-VerifierBrowserDrainAttestation $DrainScope $Record
    if ($attestation.Mode -cne 'termination') {
        Throw-VerifierInfrastructure 'An observation-only browser descendant can never enter a termination-capable drain path.'
    }
    return $attestation
}

function Set-VerifierBrowserDrainPendingNaturalExit($DrainScope, $Record) {
    # This state is a reference-bound capability on the original attested
    # record, never a claim that a PID is absent. It records only that its
    # retained native handle has observed an exit while the independent WMI
    # view is still transient. Callers must retry the existing bounded drain.
    [void](Get-VerifierBrowserDrainAttestation $DrainScope $Record)
    if ($Record.PSObject.Properties['VerifierPendingNaturalExit']) {
        if (-not [object]::ReferenceEquals($Record.VerifierPendingNaturalExit,
                $script:VerifierBrowserDrainPendingNaturalExitMarker)) {
            Throw-VerifierInfrastructure 'Browser drain pending natural-exit state was copied, forged, or replaced.'
        }
        return
    }
    $Record | Add-Member -NotePropertyName VerifierPendingNaturalExit `
        -NotePropertyValue $script:VerifierBrowserDrainPendingNaturalExitMarker
}

function Test-VerifierBrowserDrainPendingNaturalExit($DrainScope, $Record) {
    if ($null -eq $Record -or $Record -is [array] -or
            -not $Record.PSObject.Properties['VerifierPendingNaturalExit']) {
        return $false
    }
    [void](Get-VerifierBrowserDrainAttestation $DrainScope $Record)
    if (-not [object]::ReferenceEquals($Record.VerifierPendingNaturalExit,
            $script:VerifierBrowserDrainPendingNaturalExitMarker)) {
        Throw-VerifierInfrastructure 'Browser drain pending natural-exit state was copied, forged, or replaced.'
    }
    return $true
}

function Clear-VerifierBrowserDrainPendingNaturalExit($DrainScope, $Record) {
    if (-not (Test-VerifierBrowserDrainPendingNaturalExit $DrainScope $Record)) {
        return
    }
    [void]$Record.PSObject.Properties.Remove('VerifierPendingNaturalExit')
}

function Get-VerifierNaturalExitCurrentAbsenceObservation($ProcessId) {
    # This observation is deliberately narrower than the general current-process
    # lookup. It is reachable only after a retained, attested native handle has
    # already proved its own exact PID/start identity and exited. Windows can
    # retain that just-exited PID briefly in Win32_Process. That lag is neither
    # absence nor a new ownership target: return a nonterminal observation and
    # let the caller retry within its existing whole-drain deadline.
    if (-not (Test-VerifierStrictIntegralValue $ProcessId 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'Natural-exit absence observation requires an exact positive PID.'
    }
    $ProcessId = [int]$ProcessId
    $nativeAbsentOrExited = $false
    try {
        $nativeRecords = @(Get-Process -Id $ProcessId -ErrorAction Stop)
    } catch {
        $category = if ($_.PSObject.Properties['CategoryInfo'] -and
                $_.CategoryInfo.PSObject.Properties['Category']) {
            [string]$_.CategoryInfo.Category
        } else { '' }
        if ($category -ne 'ObjectNotFound') {
            Throw-VerifierInfrastructure "Could not query natural-exit native PID ${ProcessId}: $(Get-VerifierErrorMessage $_)"
        }
        $nativeRecords = @()
        $nativeAbsentOrExited = $true
    }
    if (-not $nativeAbsentOrExited) {
        if ($nativeRecords.Count -ne 1 -or $null -eq $nativeRecords[0] -or
                $nativeRecords[0].GetType() -ne [Diagnostics.Process]) {
            Throw-VerifierInfrastructure "Natural-exit native PID $ProcessId was missing or ambiguous."
        }
        $native = $nativeRecords[0]
        try {
            $native.Refresh()
            if ([int]$native.Id -ne $ProcessId) {
                Throw-VerifierInfrastructure "Natural-exit native PID $ProcessId changed during observation."
            }
            $nativeAbsentOrExited = [bool]$native.HasExited
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure "Could not refresh natural-exit native PID ${ProcessId}: $(Get-VerifierErrorMessage $_)"
        }
        if (-not $nativeAbsentOrExited) {
            # A live native process is never inferred from its PID alone. Let
            # the shared full current-identity boundary establish whether it is
            # a retained identity or a reused/mismatched PID, then make the
            # caller reject it as a present process.
            $current = Get-VerifierCurrentProcessRecordById $ProcessId
            if ($null -eq $current) {
                Throw-VerifierInfrastructure "Natural-exit native PID $ProcessId was live while its current identity was absent."
            }
            return [pscustomobject]@{ Absent = $false; Transient = $false; Current = $current }
        }
    }
    try {
        $wmiRecords = @(Get-VerifierProcessRecordsByIdWithFallback $ProcessId `
            'natural-exit post-handle absence')
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure "Could not query natural-exit Win32_Process PID ${ProcessId}: $(Get-VerifierErrorMessage $_)"
    }
    if ($wmiRecords.Count -eq 0) {
        return [pscustomobject]@{ Absent = $true; Transient = $false; Current = $null }
    }
    if ($wmiRecords.Count -ne 1 -or $null -eq $wmiRecords[0] -or
            -not $wmiRecords[0].PSObject.Properties['ProcessId'] -or
            -not (Test-VerifierStrictIntegralValue $wmiRecords[0].ProcessId 1 ([int]::MaxValue)) -or
            [int]$wmiRecords[0].ProcessId -ne $ProcessId) {
        Throw-VerifierInfrastructure "Natural-exit Win32_Process PID $ProcessId was malformed or ambiguous."
    }
    # A well-formed WMI record alongside an absent/exited fresh native process
    # is an unproven transition, not a permission to accept absence or stop a
    # process. A later independent observation must show both sources empty.
    return [pscustomobject]@{ Absent = $false; Transient = $true; Current = $null }
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
    # PID/WMI absence observations. A transient post-exit WMI record returns to
    # the caller's existing bounded drain loop; a present/reused PID or either
    # unreadable view remains infrastructure failure and never reaches
    # Stop-Process.
    for ($observation = 1; $observation -le 2; $observation++) {
        if ($proofBudget.ElapsedTicks -ge $proofBudgetTicks) {
            Throw-VerifierInfrastructure 'Browser drain natural-exit proof expired before a current absence observation.'
        }
        $absence = $null
        try {
            $absence = Get-VerifierNaturalExitCurrentAbsenceObservation $handlePid
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not confirm natural exit for browser drain PID ${handlePid}: " +
                (Get-VerifierErrorMessage $_))
        }
        if ($proofBudget.ElapsedTicks -ge $proofBudgetTicks) {
            Throw-VerifierInfrastructure 'Browser drain natural-exit current absence observation exceeded its bounded interval.'
        }
        if ($null -eq $absence -or
                -not $absence.PSObject.Properties['Absent'] -or
                -not $absence.PSObject.Properties['Transient'] -or
                $absence.Absent.GetType() -ne [bool] -or
                $absence.Transient.GetType() -ne [bool]) {
            Throw-VerifierInfrastructure 'Browser drain natural-exit absence observation was malformed.'
        }
        if ([bool]$absence.Transient) { return $null }
        if (-not [bool]$absence.Absent) {
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
        [void](Assert-VerifierBrowserDrainTerminationAttestation $DrainScope $priorRecord)
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
    if ($null -eq $naturalExit) {
        # Do not turn a post-exit WMI lag into either absence or a termination
        # target. Preserve only the original capability and let the caller's
        # existing whole-drain deadline obtain a fresh, complete proof.
        Set-VerifierBrowserDrainPendingNaturalExit $DrainScope $retainedRecord
        return $retainedRecord
    }
    Clear-VerifierBrowserDrainPendingNaturalExit $DrainScope $retainedRecord
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

function Assert-VerifierBoundReceiptObservationRawRecord($Record,
        [string]$Label = 'bound-receipt browser descendant observation') {
    # This is deliberately an observation boundary, not a termination
    # boundary. A protected Chromium helper may expose a null command line,
    # but it must still present an exact current PID/PPID/creation record
    # before the receipt lane is allowed to ask the exact browser root to
    # close itself. CreationDate is mandatory in this special lane: native
    # fallback records lack an atomic creation observation, so accepting them
    # would permit a PID reuse to masquerade as the earlier snapshot child.
    if ($null -eq $Record -or $Record -is [array]) {
        Throw-VerifierInfrastructure "$Label was null or an array before protected-helper observation."
    }
    foreach ($propertyName in @('ProcessId', 'ParentProcessId', 'Name',
            'ExecutablePath', 'CommandLine', 'CreationDate')) {
        if (-not $Record.PSObject.Properties[$propertyName]) {
            Throw-VerifierInfrastructure "$Label omitted '$propertyName' before protected-helper observation."
        }
    }
    if (-not (Test-VerifierStrictIntegralValue $Record.ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $Record.ParentProcessId 1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure "$Label carried malformed positive PID/parent identity."
    }
    foreach ($propertyName in @('Name', 'ExecutablePath', 'CommandLine')) {
        $value = $Record.PSObject.Properties[$propertyName].Value
        if ($null -ne $value -and -not (Test-VerifierStrictStringValue $value)) {
            Throw-VerifierInfrastructure "$Label carried malformed '$propertyName' observation data."
        }
    }
    $creationDate = $Record.PSObject.Properties['CreationDate'].Value
    if ($null -eq $creationDate -or $creationDate.GetType() -ne [DateTime]) {
        Throw-VerifierInfrastructure "$Label omitted or malformed its exact CreationDate observation."
    }
    try {
        if ([long]$creationDate.ToUniversalTime().Ticks -le 0) {
            Throw-VerifierInfrastructure "$Label carried a non-positive CreationDate observation."
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not normalize $Label CreationDate observation: " +
            (Get-VerifierErrorMessage $_))
    }
    return $Record
}

function Get-VerifierBoundReceiptObservationCreationTicks($Record,
        [string]$Label = 'bound-receipt browser descendant observation') {
    [void](Assert-VerifierBoundReceiptObservationRawRecord $Record $Label)
    try {
        $ticks = [long]$Record.CreationDate.ToUniversalTime().Ticks
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not read $Label CreationDate ticks: " +
            (Get-VerifierErrorMessage $_))
    }
    if ($ticks -le 0) {
        Throw-VerifierInfrastructure "$Label carried a non-positive CreationDate identity."
    }
    return $ticks
}

function Assert-VerifierBoundReceiptObservationMatchesRetainedProcess($Record,
        [long]$RetainedProcessStartTicks,
        [string]$Label = 'bound-receipt browser descendant observation') {
    $creationTicks = Get-VerifierBoundReceiptObservationCreationTicks $Record $Label
    if (-not (Test-VerifierStrictIntegralValue $RetainedProcessStartTicks 1 ([long]::MaxValue))) {
        Throw-VerifierInfrastructure "$Label omitted a positive retained Process start identity."
    }
    # Win32_Process CreationDate is represented at microsecond precision while
    # Process.StartTime retains 100 ns ticks. A difference of at most nine ticks
    # is the exact conversion remainder, not a broad time tolerance. Anything
    # outside that one representational unit is a PID/start replacement.
    $difference = [Math]::Abs([long]$RetainedProcessStartTicks - [long]$creationTicks)
    if ($difference -gt 9L) {
        Throw-VerifierInfrastructure "$Label PID $($Record.ProcessId) changed its exact creation/start identity before retained-handle attestation."
    }
    return
}

function Test-VerifierBoundReceiptObservationRawEquivalent($Expected, $Current) {
    try {
        [void](Assert-VerifierBoundReceiptObservationRawRecord $Expected `
            'expected bound-receipt browser descendant observation')
        [void](Assert-VerifierBoundReceiptObservationRawRecord $Current `
            'current bound-receipt browser descendant observation')
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not validate protected-helper observation equivalence: ' +
            (Get-VerifierErrorMessage $_))
    }
    foreach ($propertyName in @('ProcessId', 'ParentProcessId', 'Name',
            'ExecutablePath', 'CommandLine')) {
        if (-not [object]::Equals(
                $Expected.PSObject.Properties[$propertyName].Value,
                $Current.PSObject.Properties[$propertyName].Value)) {
            return $false
        }
    }
    return ((Get-VerifierBoundReceiptObservationCreationTicks $Expected `
            'expected bound-receipt browser descendant observation') -eq
        (Get-VerifierBoundReceiptObservationCreationTicks $Current `
            'current bound-receipt browser descendant observation'))
}

function Confirm-VerifierBoundReceiptObservedChildAbsent($Candidate,
        $ParentRecord) {
    # A direct protected helper can finish while its native handle is being
    # acquired. That race is acceptable only after two fresh native/WMI absence
    # views prove both the exact helper and every child still attributed to its
    # original PID are gone. Windows retains a child's creator PID after that
    # parent exits, so querying that PID closes the otherwise-untracked
    # grandchild branch. A reuse, live helper, late grandchild, malformed view,
    # or unresolved WMI transition never authorizes Browser.close.
    [void](Assert-VerifierBoundReceiptObservationRawRecord $Candidate `
        'bound-receipt disappearing protected-helper candidate')
    if ($null -eq $ParentRecord -or
            -not $ParentRecord.PSObject.Properties['Process'] -or
            $null -eq $ParentRecord.Process -or
            $ParentRecord.Process.GetType() -ne [Diagnostics.Process] -or
            -not $ParentRecord.PSObject.Properties['ProcessId'] -or
            -not $ParentRecord.PSObject.Properties['ProcessStartTicks'] -or
            -not (Test-VerifierStrictIntegralValue $ParentRecord.ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $ParentRecord.ProcessStartTicks 1 ([long]::MaxValue))) {
        Throw-VerifierInfrastructure 'Bound-receipt disappearing protected-helper candidate omitted its exact live parent handle.'
    }
    $candidatePid = [int]$Candidate.ProcessId
    $parentPid = [int]$ParentRecord.ProcessId
    $proofBudget = [Diagnostics.Stopwatch]::StartNew()
    $proofBudgetTicks = [long][Math]::Ceiling(
        ([double][Diagnostics.Stopwatch]::Frequency * 500) / 1000.0)
    $absenceViews = 0
    while ($absenceViews -lt 2) {
        if ($proofBudget.ElapsedTicks -ge $proofBudgetTicks) {
            Throw-VerifierInfrastructure "Bound-receipt disappearing protected-helper PID $candidatePid did not reach two complete helper-and-child absence views."
        }
        try {
            $ParentRecord.Process.Refresh()
            if ([bool]$ParentRecord.Process.HasExited -or
                    [int]$ParentRecord.Process.Id -ne $parentPid -or
                    [long](Get-VerifierProcessStartTicks $ParentRecord.Process) -ne
                        [long]$ParentRecord.ProcessStartTicks) {
                Throw-VerifierInfrastructure "Bound-receipt parent PID $parentPid changed while child PID $candidatePid disappearance was being proven."
            }
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not inspect bound-receipt parent PID ${parentPid} while child PID ${candidatePid} disappeared: " +
                (Get-VerifierErrorMessage $_))
        }
        $current = Get-VerifierProcessById $candidatePid
        if ($null -ne $current) {
            if ($current.GetType() -ne [Diagnostics.Process]) {
                Throw-VerifierInfrastructure "Bound-receipt disappearing protected-helper PID $candidatePid returned a malformed current Process object."
            }
            try {
                $current.Refresh()
                if (-not [bool]$current.HasExited) {
                    Throw-VerifierInfrastructure "Bound-receipt protected-helper PID $candidatePid remained live or was reused during disappearance proof."
                }
                if ([int]$current.Id -ne $candidatePid) {
                    Throw-VerifierInfrastructure "Bound-receipt protected-helper PID $candidatePid changed during disappearance proof."
                }
            } catch {
                if (Test-VerifierInfrastructureError $_) { throw }
                Throw-VerifierInfrastructure ("Could not refresh disappearing bound-receipt protected-helper PID ${candidatePid}: " +
                    (Get-VerifierErrorMessage $_))
            }
        }
        $byId = @()
        $byParent = @()
        $byCandidate = @()
        try {
            $byId = @(Get-VerifierProcessRecordsByIdWithFallback $candidatePid `
                'bound-receipt disappearing protected-helper identity')
            $byParent = @(Get-VerifierProcessRecordsByParentWithFallback $parentPid `
                'bound-receipt disappearing protected-helper parent')
            $byCandidate = @(Get-VerifierProcessRecordsByParentWithFallback $candidatePid `
                'bound-receipt disappearing protected-helper children')
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not confirm disappearing bound-receipt protected-helper PID ${candidatePid}: " +
                (Get-VerifierErrorMessage $_))
        }
        if ($byId.Count -gt 1) {
            Throw-VerifierInfrastructure "Bound-receipt disappearing protected-helper PID $candidatePid had ambiguous current WMI identity."
        }
        $completeAbsence = ($byId.Count -eq 0)
        if ($byId.Count -eq 1 -and
                -not (Test-VerifierBoundReceiptObservationRawEquivalent $Candidate $byId[0])) {
            Throw-VerifierInfrastructure "Bound-receipt disappearing protected-helper PID $candidatePid changed or was reused during absence proof."
        }
        foreach ($child in $byParent) {
            [void](Assert-VerifierBoundReceiptObservationRawRecord $child `
                "bound-receipt disappearing protected-helper child query under PID $parentPid")
            if ([int]$child.ParentProcessId -ne $parentPid) {
                Throw-VerifierInfrastructure "Bound-receipt disappearing protected-helper child query returned an invalid parent for PID $($child.ProcessId)."
            }
            if ([int]$child.ProcessId -eq $candidatePid) {
                if (-not (Test-VerifierBoundReceiptObservationRawEquivalent $Candidate $child)) {
                    Throw-VerifierInfrastructure "Bound-receipt disappearing protected-helper PID $candidatePid changed in its parent query."
                }
                $completeAbsence = $false
            }
        }
        foreach ($grandchild in $byCandidate) {
            [void](Assert-VerifierBoundReceiptObservationRawRecord $grandchild `
                "bound-receipt disappearing protected-helper child query under PID $candidatePid")
            if ([int]$grandchild.ParentProcessId -ne $candidatePid) {
                Throw-VerifierInfrastructure "Bound-receipt disappearing protected-helper child query returned an invalid parent for PID $($grandchild.ProcessId)."
            }
            # Even an already-exited WMI residue prevents this view from being
            # an absence proof. A surviving markerless grandchild remains in
            # this set and causes the bounded loop to fail closed.
            $completeAbsence = $false
        }
        if ($completeAbsence) {
            $absenceViews++
        } else {
            $absenceViews = 0
        }
        if ($absenceViews -lt 2) {
            Start-Sleep -Milliseconds 25
        }
    }
    return $true
}

function Confirm-VerifierBoundReceiptObservedLeafNaturalExit($DrainScope,
        $ParentRecord, $SnapshotByParent) {
    # A protected helper can be retained while live, then naturally exit
    # before its own breadth-first child query. This is safe only for a leaf:
    # its retained native handle must prove the exact exit, and both the
    # original complete snapshot and a fresh query must show no child branch.
    # A root, a snapshot child, a late child, a transient WMI record, or a
    # reused PID remains fail-closed and never authorizes Browser.close.
    Assert-VerifierBrowserDrainScope $DrainScope `
        'bound-receipt exited protected-helper leaf scope'
    if ($null -eq $ParentRecord -or $ParentRecord -is [array] -or
            $null -eq $SnapshotByParent -or -not ($SnapshotByParent -is [hashtable]) -or
            -not $ParentRecord.PSObject.Properties['ProcessId'] -or
            -not (Test-VerifierStrictIntegralValue $ParentRecord.ProcessId `
                1 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'Bound-receipt exited protected-helper leaf omitted its exact retained identity or snapshot graph.'
    }
    $parentPid = [int]$ParentRecord.ProcessId
    if ($SnapshotByParent.ContainsKey($parentPid) -and
            @($SnapshotByParent[$parentPid]).Count -gt 0) {
        Throw-VerifierInfrastructure "Bound-receipt exited protected-helper PID $parentPid retained a snapshot child branch during census."
    }
    $proofBudget = [Diagnostics.Stopwatch]::StartNew()
    $proofBudgetTicks = [long][Math]::Ceiling(
        ([double][Diagnostics.Stopwatch]::Frequency * 500) / 1000.0)
    while ($true) {
        $natural = Get-VerifierBrowserDrainNaturalExitResult $DrainScope $ParentRecord
        if ($null -ne $natural) {
            $lateChildren = @()
            try {
                $lateChildren = @(Get-VerifierProcessRecordsByParentWithFallback $parentPid `
                    'bound-receipt exited protected-helper leaf children')
            } catch {
                if (Test-VerifierInfrastructureError $_) { throw }
                Throw-VerifierInfrastructure ("Could not inspect children of exited bound-receipt protected-helper PID ${parentPid}: " +
                    (Get-VerifierErrorMessage $_))
            }
            foreach ($lateChild in $lateChildren) {
                [void](Assert-VerifierBoundReceiptObservationRawRecord $lateChild `
                    "bound-receipt exited protected-helper child under PID $parentPid")
                if ([int]$lateChild.ParentProcessId -ne $parentPid) {
                    Throw-VerifierInfrastructure "Bound-receipt exited protected-helper PID $parentPid returned a child with a mismatched parent."
                }
            }
            if ($lateChildren.Count -gt 0) {
                Throw-VerifierInfrastructure "Bound-receipt exited protected-helper PID $parentPid retained a late child branch during census."
            }
            return $true
        }
        if ($proofBudget.ElapsedTicks -ge $proofBudgetTicks) {
            Throw-VerifierInfrastructure "Bound-receipt exited protected-helper PID $parentPid did not reach a complete natural-exit proof during census."
        }
        Start-Sleep -Milliseconds 25
    }
}

function Get-VerifierBoundReceiptNaturalShutdownDescendants($RootRecord,
        $Snapshot, $DrainScope) {
    # The bound receipt authorizes Browser.close for its exact root only. It
    # never authorizes a PID/PPID stop of a helper. Before that close, retain
    # every live current descendant under the exact root (including a helper
    # whose command line is inaccessible) behind a native handle. Each must
    # later exit naturally and pass two fresh absence observations.
    Assert-VerifierBrowserDrainScope $DrainScope `
        'bound-receipt protected-helper observation scope'
    if ($null -eq $RootRecord -or $null -eq $Snapshot) {
        Throw-VerifierInfrastructure 'Bound-receipt natural shutdown omitted its root or complete process snapshot.'
    }
    [void](Assert-VerifierRawProcessIdentityRecord $RootRecord `
        'bound-receipt natural shutdown root' -RequireParentStart)
    if (-not $RootRecord.PSObject.Properties['Process'] -or
            $null -eq $RootRecord.Process -or
            $RootRecord.Process.GetType() -ne [Diagnostics.Process]) {
        Throw-VerifierInfrastructure 'Bound-receipt natural shutdown root omitted its retained Process handle.'
    }
    try {
        $RootRecord.Process.Refresh()
        if ([bool]$RootRecord.Process.HasExited -or
                [int]$RootRecord.Process.Id -ne [int]$RootRecord.ProcessId -or
                [long](Get-VerifierProcessStartTicks $RootRecord.Process) -ne
                    [long]$RootRecord.ProcessStartTicks) {
            Throw-VerifierInfrastructure 'Bound-receipt natural shutdown root changed before protected-helper census.'
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not inspect the bound-receipt natural shutdown root: ' +
            (Get-VerifierErrorMessage $_))
    }

    $snapshotByParent = @{}
    foreach ($snapshotRecord in @($Snapshot)) {
        if ($null -eq $snapshotRecord -or $snapshotRecord -is [array] -or
                -not $snapshotRecord.PSObject.Properties['ProcessId'] -or
                -not $snapshotRecord.PSObject.Properties['ParentProcessId']) {
            Throw-VerifierInfrastructure 'Complete bound-receipt process snapshot contained a malformed raw record.'
        }
        if (-not (Test-VerifierStrictIntegralValue $snapshotRecord.ProcessId 0 ([int]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $snapshotRecord.ParentProcessId 0 ([int]::MaxValue))) {
            Throw-VerifierInfrastructure 'Complete bound-receipt process snapshot contained malformed PID/parent data.'
        }
        if ([int]$snapshotRecord.ProcessId -eq 0 -or
                [int]$snapshotRecord.ParentProcessId -eq 0) {
            continue
        }
        [void](Assert-VerifierBoundReceiptObservationRawRecord $snapshotRecord `
            'complete bound-receipt process snapshot record')
        $parentPid = [int]$snapshotRecord.ParentProcessId
        if (-not $snapshotByParent.ContainsKey($parentPid)) {
            $snapshotByParent[$parentPid] = New-Object Collections.ArrayList
        }
        [void]$snapshotByParent[$parentPid].Add($snapshotRecord)
    }

    $observedByPid = @{}
    $observedByPid[[int]$RootRecord.ProcessId] = $RootRecord
    $pendingParents = New-Object Collections.Generic.Queue[int]
    $pendingParents.Enqueue([int]$RootRecord.ProcessId)
    $descendants = New-Object Collections.ArrayList
    $depthByPid = @{}
    $depthByPid[[int]$RootRecord.ProcessId] = 0
    while ($pendingParents.Count -gt 0) {
        $parentPid = $pendingParents.Dequeue()
        if (-not $observedByPid.ContainsKey($parentPid) -or
                -not $depthByPid.ContainsKey($parentPid)) {
            Throw-VerifierInfrastructure 'Bound-receipt protected-helper census lost a verified parent identity.'
        }
        $parentRecord = $observedByPid[$parentPid]
        $parentExited = $false
        try {
            $parentRecord.Process.Refresh()
            if ([bool]$parentRecord.Process.HasExited) {
                $parentExited = $true
            } elseif ([int]$parentRecord.Process.Id -ne $parentPid -or
                    [long](Get-VerifierProcessStartTicks $parentRecord.Process) -ne
                        [long]$parentRecord.ProcessStartTicks) {
                Throw-VerifierInfrastructure "Bound-receipt protected-helper parent PID $parentPid changed during census."
            }
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not inspect bound-receipt protected-helper parent PID ${parentPid}: " +
                (Get-VerifierErrorMessage $_))
        }
        if ($parentExited) {
            if ($parentPid -eq [int]$RootRecord.ProcessId) {
                Throw-VerifierInfrastructure 'Bound-receipt natural shutdown root exited during protected-helper census.'
            }
            [void](Confirm-VerifierBoundReceiptObservedLeafNaturalExit $DrainScope `
                $parentRecord $snapshotByParent)
            continue
        }

        $candidatesByPid = @{}
        if ($snapshotByParent.ContainsKey($parentPid)) {
            foreach ($snapshotChild in @($snapshotByParent[$parentPid])) {
                [void](Assert-VerifierBoundReceiptObservationRawRecord $snapshotChild `
                    "bound-receipt snapshot child under PID $parentPid")
                $childPid = [int]$snapshotChild.ProcessId
                if ([int]$snapshotChild.ParentProcessId -ne $parentPid -or
                        $candidatesByPid.ContainsKey($childPid)) {
                    Throw-VerifierInfrastructure "Bound-receipt snapshot child graph was ambiguous under PID $parentPid."
                }
                $candidatesByPid[$childPid] = $snapshotChild
            }
        }
        $queriedChildren = @()
        try {
            $queriedChildren = @(Get-VerifierProcessRecordsByParentWithFallback $parentPid `
                'bound-receipt exact browser descendant')
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not query bound-receipt children of PID ${parentPid}: " +
                (Get-VerifierErrorMessage $_))
        }
        foreach ($queriedChild in $queriedChildren) {
            [void](Assert-VerifierBoundReceiptObservationRawRecord $queriedChild `
                "bound-receipt exact child under PID $parentPid")
            $childPid = [int]$queriedChild.ProcessId
            if ([int]$queriedChild.ParentProcessId -ne $parentPid) {
                Throw-VerifierInfrastructure "Bound-receipt exact child query returned a mismatched parent for PID $childPid."
            }
            if ($candidatesByPid.ContainsKey($childPid)) {
                if (-not (Test-VerifierBoundReceiptObservationRawEquivalent `
                        $candidatesByPid[$childPid] $queriedChild)) {
                    Throw-VerifierInfrastructure "Bound-receipt snapshot and exact child query disagreed for PID $childPid."
                }
            } else {
                $candidatesByPid[$childPid] = $queriedChild
            }
        }

        foreach ($candidate in @($candidatesByPid.Values | Sort-Object { [int]$_.ProcessId })) {
            [void](Assert-VerifierBoundReceiptObservationRawRecord $candidate `
                "bound-receipt protected-helper candidate under PID $parentPid")
            $childPid = [int]$candidate.ProcessId
            if ($childPid -eq $parentPid -or $observedByPid.ContainsKey($childPid)) {
                Throw-VerifierInfrastructure "Bound-receipt protected-helper graph reused or cycled PID $childPid."
            }
            $childProcess = Get-VerifierProcessById $childPid
            if ($null -eq $childProcess -or $childProcess.GetType() -ne [Diagnostics.Process]) {
                if ($snapshotByParent.ContainsKey($childPid) -and
                        @($snapshotByParent[$childPid]).Count -gt 0) {
                    Throw-VerifierInfrastructure "Bound-receipt protected-helper PID $childPid disappeared before native retention but had an unretained snapshot descendant."
                }
                [void](Confirm-VerifierBoundReceiptObservedChildAbsent $candidate $parentRecord)
                continue
            }
            try {
                $childProcess.Refresh()
                if ([bool]$childProcess.HasExited -or [int]$childProcess.Id -ne $childPid) {
                    if ($snapshotByParent.ContainsKey($childPid) -and
                            @($snapshotByParent[$childPid]).Count -gt 0) {
                        Throw-VerifierInfrastructure "Bound-receipt protected-helper PID $childPid exited before native retention but had an unretained snapshot descendant."
                    }
                    [void](Confirm-VerifierBoundReceiptObservedChildAbsent $candidate $parentRecord)
                    continue
                }
            } catch {
                if (Test-VerifierInfrastructureError $_) { throw }
                Throw-VerifierInfrastructure ("Could not inspect bound-receipt protected-helper PID ${childPid}: " +
                    (Get-VerifierErrorMessage $_))
            }
            $childStart = 0L
            try {
                $childStart = [long](Get-VerifierProcessStartTicks $childProcess)
            } catch {
                $startCaptureError = $_
                $childExitedDuringStartCapture = $false
                try {
                    $childProcess.Refresh()
                    if ([int]$childProcess.Id -ne $childPid) {
                        Throw-VerifierInfrastructure "Bound-receipt protected-helper PID $childPid changed during start-identity capture."
                    }
                    $childExitedDuringStartCapture = [bool]$childProcess.HasExited
                } catch {
                    if (Test-VerifierInfrastructureError $_) { throw }
                    Throw-VerifierInfrastructure ("Could not recheck bound-receipt protected-helper PID ${childPid} after start-identity capture failed: " +
                        (Get-VerifierErrorMessage $_))
                }
                if (-not $childExitedDuringStartCapture) {
                    if (Test-VerifierInfrastructureError $startCaptureError) { throw $startCaptureError }
                    Throw-VerifierInfrastructure ("Could not establish bound-receipt protected-helper PID ${childPid} start identity: " +
                        (Get-VerifierErrorMessage $startCaptureError))
                }
                # The handle was live at the preceding refresh but exited
                # before its start identity could be pinned. It remains a
                # disappearing candidate, never an implicit leaf: an original
                # snapshot child still blocks Browser.close and the live parent
                # plus two fresh absence views must prove the whole branch.
                if ($snapshotByParent.ContainsKey($childPid) -and
                        @($snapshotByParent[$childPid]).Count -gt 0) {
                    Throw-VerifierInfrastructure "Bound-receipt protected-helper PID $childPid exited during start-identity capture but had an unretained snapshot descendant."
                }
                [void](Confirm-VerifierBoundReceiptObservedChildAbsent $candidate $parentRecord)
                continue
            }
            if ($childStart -le 0) {
                Throw-VerifierInfrastructure "Bound-receipt protected-helper PID $childPid had no positive start identity."
            }
            Assert-VerifierBoundReceiptObservationMatchesRetainedProcess $candidate `
                $childStart "bound-receipt protected-helper PID $childPid"
            $currentRecords = @()
            try {
                $currentRecords = @(Get-VerifierProcessRecordsByIdWithFallback $childPid `
                    'bound-receipt protected-helper current identity')
            } catch {
                if (Test-VerifierInfrastructureError $_) { throw }
                Throw-VerifierInfrastructure ("Could not re-query bound-receipt protected-helper PID ${childPid}: " +
                    (Get-VerifierErrorMessage $_))
            }
            if ($currentRecords.Count -ne 1 -or $null -eq $currentRecords[0] -or
                    -not (Test-VerifierBoundReceiptObservationRawEquivalent `
                        $candidate $currentRecords[0])) {
                Throw-VerifierInfrastructure "Bound-receipt protected-helper PID $childPid changed or became ambiguous during census."
            }
            $recheckedChildStart = 0L
            try {
                $recheckedChildStart = [long](Get-VerifierProcessStartTicks $childProcess)
            } catch {
                $recheckError = $_
                $childExitedDuringRecheck = $false
                try {
                    $childProcess.Refresh()
                    if ([int]$childProcess.Id -ne $childPid) {
                        Throw-VerifierInfrastructure "Bound-receipt protected-helper PID $childPid changed during recheck identity capture."
                    }
                    $childExitedDuringRecheck = [bool]$childProcess.HasExited
                } catch {
                    if (Test-VerifierInfrastructureError $_) { throw }
                    Throw-VerifierInfrastructure ("Could not recheck bound-receipt protected-helper PID ${childPid} after its identity recheck failed: " +
                        (Get-VerifierErrorMessage $_))
                }
                if (-not $childExitedDuringRecheck) {
                    if (Test-VerifierInfrastructureError $recheckError) { throw $recheckError }
                    Throw-VerifierInfrastructure ("Could not recheck bound-receipt protected-helper PID ${childPid} start identity: " +
                        (Get-VerifierErrorMessage $recheckError))
                }
                if ($snapshotByParent.ContainsKey($childPid) -and
                        @($snapshotByParent[$childPid]).Count -gt 0) {
                    Throw-VerifierInfrastructure "Bound-receipt protected-helper PID $childPid exited during identity recheck but had an unretained snapshot descendant."
                }
                [void](Confirm-VerifierBoundReceiptObservedChildAbsent $candidate $parentRecord)
                continue
            }
            if ($recheckedChildStart -ne $childStart) {
                Throw-VerifierInfrastructure "Bound-receipt protected-helper PID $childPid changed its retained PID/start identity during census."
            }
            $childRecord = [pscustomobject]@{
                Process = $childProcess
                ProcessId = $childPid
                ProcessStartTicks = $childStart
                ParentProcessId = $parentPid
                ParentProcessStartTicks = [long]$parentRecord.ProcessStartTicks
                Name = if ($null -eq $candidate.Name) { '' } else { [string]$candidate.Name }
                ExecutablePath = if ($null -eq $candidate.ExecutablePath) { '' } else {
                    [string]$candidate.ExecutablePath }
                CommandLine = if ($null -eq $candidate.CommandLine) { '' } else {
                    [string]$candidate.CommandLine }
                VerifierDepth = [int]$depthByPid[$parentPid] + 1
                ObservationOnly = $true
            }
            $attestation = New-VerifierBrowserDrainAttestation $DrainScope `
                $childRecord $childProcess -ObservationOnly
            $childRecord | Add-Member -NotePropertyName VerifierDrainAttestation `
                -NotePropertyValue $attestation -Force
            $observedByPid[$childPid] = $childRecord
            $depthByPid[$childPid] = [int]$childRecord.VerifierDepth
            [void]$descendants.Add($childRecord)
            $pendingParents.Enqueue($childPid)
        }
    }
    return @($descendants)
}

function Wait-VerifierBoundReceiptNaturalShutdownDescendants($DrainScope,
        $RootRecord, $DescendantRecords, $Budget, [long]$BudgetTicks) {
    Assert-VerifierBrowserDrainScope $DrainScope `
        'bound-receipt natural shutdown wait scope'
    if ($null -eq $RootRecord -or $null -eq $DescendantRecords) {
        Throw-VerifierInfrastructure 'Bound-receipt natural shutdown wait omitted its root or protected-helper census.'
    }
    $records = @($RootRecord) + @($DescendantRecords)
    $seen = @{}
    while ($true) {
        Assert-VerifierBrowserShutdownDeadline $Budget $BudgetTicks
        $allAbsent = $true
        foreach ($record in $records) {
            if ($null -eq $record -or -not $record.PSObject.Properties['ProcessId'] -or
                    -not (Test-VerifierStrictIntegralValue $record.ProcessId 1 ([int]::MaxValue))) {
                Throw-VerifierInfrastructure 'Bound-receipt natural shutdown wait carried an invalid retained process record.'
            }
            $pid = [int]$record.ProcessId
            if ($seen.ContainsKey($pid) -and -not [object]::ReferenceEquals($seen[$pid], $record)) {
                Throw-VerifierInfrastructure "Bound-receipt natural shutdown wait carried duplicate retained PID $pid."
            }
            $seen[$pid] = $record
            $natural = Get-VerifierBrowserDrainNaturalExitResult $DrainScope $record
            if ($null -eq $natural) { $allAbsent = $false }
            Assert-VerifierBrowserShutdownDeadline $Budget $BudgetTicks
        }
        if ($allAbsent) { return }
        Start-Sleep -Milliseconds 25
        Assert-VerifierBrowserShutdownDeadline $Budget $BudgetTicks
    }
}

function Assert-VerifierBoundReceiptNaturalShutdownNoLateDescendants($RootRecord,
        $DescendantRecords) {
    if ($null -eq $RootRecord -or $null -eq $DescendantRecords) {
        Throw-VerifierInfrastructure 'Bound-receipt post-close proof omitted its root or protected-helper census.'
    }
    $knownPids = @{}
    foreach ($record in @($RootRecord) + @($DescendantRecords)) {
        if ($null -eq $record -or -not $record.PSObject.Properties['ProcessId'] -or
                -not (Test-VerifierStrictIntegralValue $record.ProcessId 1 ([int]::MaxValue))) {
            Throw-VerifierInfrastructure 'Bound-receipt post-close proof carried an invalid retained PID.'
        }
        $knownPids[[int]$record.ProcessId] = $true
    }
    foreach ($parentPid in @($knownPids.Keys)) {
        $lateChildren = @()
        try {
            $lateChildren = @(Get-VerifierProcessRecordsByParentWithFallback ([int]$parentPid `
                ) 'bound-receipt post-close browser descendant')
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not query bound-receipt post-close children of PID ${parentPid}: " +
                (Get-VerifierErrorMessage $_))
        }
        foreach ($lateChild in $lateChildren) {
            [void](Assert-VerifierBoundReceiptObservationRawRecord $lateChild `
                "bound-receipt post-close child under PID $parentPid")
            if ([int]$lateChild.ParentProcessId -ne [int]$parentPid) {
                Throw-VerifierInfrastructure "Bound-receipt post-close child query returned a mismatched parent for PID $($lateChild.ProcessId)."
            }
            Throw-VerifierInfrastructure "A protected, late, or uninspectable browser helper PID $($lateChild.ProcessId) remained after Browser.close; profile and lease cleanup are retained."
        }
    }
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
            $pendingNaturalExit = $false
            foreach ($childToStop in @($children | Sort-Object `
                    @{Expression={ [int]$_.VerifierDepth }; Descending=$true},
                    @{Expression={ [int]$_.ProcessId }; Descending=$true})) {
                $childPid = [int]$childToStop.ProcessId
                # First consult only the exact, scope-bound retained handle for
                # the child that was fully proved during discovery. If that
                # handle has naturally exited, prove two independent current
                # PID/WMI absences and skip Stop-Process entirely.
                $knownChild = $knownByPid[$childPid]
                [void](Assert-VerifierBrowserDrainTerminationAttestation `
                    $DrainScope $knownChild)
                if (Test-VerifierBrowserDrainPendingNaturalExit $DrainScope $knownChild) {
                    $naturalExit = Get-VerifierBrowserDrainNaturalExitResult `
                        $DrainScope $knownChild
                    if ($null -ne $naturalExit) {
                        Clear-VerifierBrowserDrainPendingNaturalExit `
                            $DrainScope $knownChild
                        continue
                    }
                    # A retained exact handle is already exited, but WMI has
                    # not yet supplied two empty current observations. It is
                    # never eligible for Stop-Process; retry the bounded outer
                    # drain rather than treating the stale record as absence.
                    $pendingNaturalExit = $true
                    continue
                }
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
            if ($pendingNaturalExit) {
                Start-Sleep -Milliseconds 25
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

function Test-VerifierBrowserLaunchParentAbsentForRecovery($SessionRecord) {
    if ($null -eq $SessionRecord -or
            -not $SessionRecord.PSObject.Properties['ProcessParentProcessId'] -or
            -not $SessionRecord.PSObject.Properties['ProcessParentProcessStartTicks'] -or
            -not (Test-VerifierStrictIntegralValue $SessionRecord.ProcessParentProcessId `
                1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $SessionRecord.ProcessParentProcessStartTicks 1)) {
        Throw-VerifierInfrastructure 'Browser parent-exit recovery requires the recorded launch parent PID/start tuple.'
    }
    # Get-VerifierCurrentProcessRecordById accepts absence only after both the
    # Process and Win32_Process views agree. Presence (including PID reuse) is
    # deliberately left on the original parent-anchored cleanup path.
    return ($null -eq (Get-VerifierCurrentProcessRecordById ([int]$SessionRecord.ProcessParentProcessId)))
}

function Get-VerifierBrowserReceiptNaturalShutdownRoot($Context, $SessionRecord,
        $OwnerRoot, [switch]$RequireLaunchParentAbsent,
        [switch]$AfterCloseAttemptJournal) {
    [void](Assert-VerifierDurableBrowserSession $SessionRecord $Context `
        'browser parent-exit recovery session')
    Assert-VerifierBrowserShutdownOwner $SessionRecord $OwnerRoot
    $receipt = Assert-VerifierDurableBrowserRecoveryReceipt $SessionRecord $Context `
        'browser parent-exit recovery receipt'
    # The public cleanup entry always requires a fresh receipt.  The one
    # post-journal revalidation below is deliberately narrower: it is reached
    # only by the same stack frame after the durable pre-send journal succeeds,
    # and it proves that the root/listener did not change in that tiny interval.
    # It never reopens an already attempted receipt to a new cleanup entry.
    $receiptAttemptStateMatches = if ($AfterCloseAttemptJournal) {
        [bool]$receipt.CloseAttempted
    } else {
        -not [bool]$receipt.CloseAttempted
    }
    if ($null -eq $receipt -or $receipt.State -ne 'bound' -or
            -not $receiptAttemptStateMatches -or $SessionRecord.Status -ne 'attached') {
        Throw-VerifierInfrastructure 'Browser parent-exit recovery requires one fresh, bound, unattempted recovery receipt.'
    }
    $parentRecord = [pscustomobject]@{
        ProcessId = [int]$receipt.RootParentProcessId
        ProcessStartTicks = [long]$receipt.RootParentProcessStartTicks
    }
    if ($RequireLaunchParentAbsent) {
        $parentAbsence = Confirm-VerifierRecordedProcessAbsent $parentRecord `
            'browser parent-exit recovery launch parent'
        if (-not $parentAbsence.QueryProven -or -not $parentAbsence.Absent -or
                $parentAbsence.Replaced) {
            Throw-VerifierInfrastructure 'Browser parent-exit recovery could not prove the exact launch parent absent without PID reuse.'
        }
    } else {
        # The ordinary receipt lane retains the historical parent anchor. It
        # is allowed only while that exact launcher identity is still live;
        # a vanished or reused parent selects the separate recovery-only
        # branch above, never a PID-only fallback.
        $currentParent = Get-VerifierCurrentProcessRecordById `
            ([int]$receipt.RootParentProcessId)
        if ($null -eq $currentParent -or
                [long]$currentParent.ProcessStartTicks -ne
                    [long]$receipt.RootParentProcessStartTicks) {
            Throw-VerifierInfrastructure 'Browser receipt natural shutdown could not prove the exact live launch parent identity.'
        }
    }
    $current = Get-VerifierCurrentProcessRecordById ([int]$receipt.RootProcessId)
    if ($null -eq $current -or
            [int]$current.ProcessId -ne [int]$SessionRecord.ProcessId -or
            [long]$current.ProcessStartTicks -ne [long]$SessionRecord.ProcessStartTicks -or
            [int]$current.ParentProcessId -ne [int]$SessionRecord.ProcessParentProcessId -or
            -not (Test-VerifierConfiguredExecutableIdentity $SessionRecord.BrowserPath $current `
                -RequireExecutablePath) -or
            -not (Test-VerifierCommandLineEquivalent $current.CommandLine `
                $SessionRecord.ProcessCommandLine) -or
            -not (Test-VerifierCommandLineEquivalent $current.CommandLine `
                $receipt.RootProcessCommandLine)) {
        Throw-VerifierInfrastructure 'Browser parent-exit recovery root no longer matches its exact PID/start/executable/command tuple.'
    }
    foreach ($switch in @(
            [pscustomobject]@{ Name = '--user-data-dir'; Value = [string]$SessionRecord.Profile }
            [pscustomobject]@{ Name = '--remote-debugging-port'; Value = [string]$SessionRecord.CdpPort }
            [pscustomobject]@{ Name = '--tsj-verifier-run'; Value = [string]$SessionRecord.RunId }
            [pscustomobject]@{ Name = '--tsj-verifier-route'; Value = [string]$SessionRecord.RouteId }
            [pscustomobject]@{ Name = '--tsj-verifier-worktree'; Value = [string]$SessionRecord.RepositoryIdentity }
            [pscustomobject]@{ Name = '--tsj-verifier-recovery-token'; Value = [string]$receipt.AuthorityToken }
        )) {
        if (-not (Test-VerifierCommandLineSwitch $current.CommandLine $switch.Name $switch.Value)) {
            Throw-VerifierInfrastructure "Browser parent-exit recovery root omitted its exact $($switch.Name) authority marker."
        }
    }
    return [pscustomobject]@{
        ProcessId = [int]$current.ProcessId
        ProcessStartTicks = [long]$current.ProcessStartTicks
        ParentProcessId = [int]$current.ParentProcessId
        ParentProcessStartTicks = [long]$receipt.RootParentProcessStartTicks
        CommandLine = [string]$current.CommandLine
        Name = [string]$current.Name
        ExecutablePath = [string]$current.ExecutablePath
        VerifierDepth = 0
        Process = $current.Process
    }
}

function Get-VerifierBrowserParentExitRecoveryRoot($Context, $SessionRecord,
        $OwnerRoot) {
    return (Get-VerifierBrowserReceiptNaturalShutdownRoot $Context $SessionRecord `
        $OwnerRoot -RequireLaunchParentAbsent)
}

function Assert-VerifierBrowserParentExitRecoveryListener($Context, $SessionRecord,
        $RootRecord) {
    $receipt = Assert-VerifierDurableBrowserRecoveryReceipt $SessionRecord $Context `
        'browser parent-exit listener receipt'
    $lease = $SessionRecord.Lease
    Assert-VerifierDurableLeaseRecord $lease $Context 'browser parent-exit listener lease'
    if ($null -eq $receipt -or $receipt.State -ne 'bound' -or
            $lease.Status -ne 'bound' -or $lease.ClaimState -ne 'bound' -or
            [int]$lease.BoundProcessId -ne [int]$RootRecord.ProcessId -or
            [long]$lease.BoundProcessStartTicks -ne [long]$RootRecord.ProcessStartTicks -or
            [int]$lease.ListenerProcessId -ne [int]$RootRecord.ProcessId -or
            [long]$lease.ListenerProcessStartTicks -ne [long]$RootRecord.ProcessStartTicks -or
            [int]$receipt.ListenerProcessId -ne [int]$RootRecord.ProcessId -or
            [long]$receipt.ListenerProcessStartTicks -ne [long]$RootRecord.ProcessStartTicks) {
        Throw-VerifierInfrastructure 'Browser parent-exit recovery listener did not retain its exact bound root/listener tuple.'
    }
    $inspection = Get-VerifierLoopbackListenerRecords ([int]$SessionRecord.CdpPort) -PreferNetstat
    if (-not (Test-VerifierListenerInspectionSchema $inspection) -or
            -not $inspection.Success -or -not $inspection.Known -or
            -not $inspection.HasListeners) {
        Throw-VerifierInfrastructure 'Browser parent-exit recovery could not prove a current listener for its exact CDP port.'
    }
    $listeners = @($inspection.Listeners)
    if ($listeners.Count -eq 0) {
        Throw-VerifierInfrastructure 'Browser parent-exit recovery listener inspection returned no exact listener records.'
    }
    foreach ($listener in $listeners) {
        if (-not (Test-VerifierListenerRecordSchema $listener) -or
                $listener.ListenerOwnerKind -ne $script:VerifierUserProcessOwnerKind -or
                [int]$listener.Port -ne [int]$SessionRecord.CdpPort -or
                [int]$listener.ProcessId -ne [int]$RootRecord.ProcessId -or
                [long]$listener.ProcessStartTicks -ne [long]$RootRecord.ProcessStartTicks) {
            Throw-VerifierInfrastructure 'Browser parent-exit recovery rejected a foreign, replaced, or malformed CDP listener owner.'
        }
        $currentListener = Get-VerifierCurrentProcessRecordById ([int]$listener.ProcessId)
        if ($null -eq $currentListener -or
                [long]$currentListener.ProcessStartTicks -ne [long]$listener.ProcessStartTicks -or
                -not (Test-VerifierCommandLineEquivalent $currentListener.CommandLine `
                    $RootRecord.CommandLine)) {
            Throw-VerifierInfrastructure 'Browser parent-exit recovery listener owner changed during exact current identity validation.'
        }
    }
    return $inspection
}

function Assert-VerifierBrowserParentExitRecoveryListenerAbsent($SessionRecord) {
    $inspection = Get-VerifierLoopbackListenerRecords ([int]$SessionRecord.CdpPort) -PreferNetstat
    if (-not (Test-VerifierListenerInspectionSchema $inspection) -or
            -not $inspection.Success -or -not $inspection.Known -or
            $inspection.HasListeners) {
        Throw-VerifierInfrastructure 'Browser parent-exit recovery did not prove its exact CDP listener absent after Browser.close.'
    }
}

function Wait-VerifierBrowserParentExitRecoveryProfileQuiescence($SessionRecord,
        $Budget, [long]$BudgetTicks) {
    while ($true) {
        Assert-VerifierBrowserShutdownDeadline $Budget $BudgetTicks
        $snapshot = @(Get-VerifierBrowserProcessSnapshot $SessionRecord.BrowserPath `
            $SessionRecord.Profile $SessionRecord.RunId $SessionRecord.RepositoryIdentity `
            $SessionRecord.CdpPort)
        $references = @(Get-VerifierProfileReferenceRecords $snapshot $SessionRecord.Profile)
        if ($references.Count -eq 0) { return }
        Assert-VerifierBrowserShutdownDeadline $Budget $BudgetTicks
        Start-Sleep -Milliseconds 50
    }
}

function Close-VerifierBrowserSessionWithBoundRecoveryReceipt($Context, $SessionRecord,
        $OwnerRoot, [switch]$RequireLaunchParentAbsent) {
    # This receipt-bound natural shutdown lane never terminates by PID and
    # never walks an unanchored child tree. With a live parent it retains that
    # exact parent identity; after the launcher exits it requires positive
    # parent absence without PID reuse. In both modes a fresh receipt binds
    # the still-live root to its exact listener before Browser.close.
    $budget = [Diagnostics.Stopwatch]::StartNew()
    $budgetTicks = [long][Math]::Ceiling(
        ([double][Diagnostics.Stopwatch]::Frequency * 15000) / 1000.0)
    $scope = New-VerifierBrowserDrainScope
    $browserSocket = $null
    $protectedDescendants = @()
    $containmentLease = $null
    $failure = $null
    $disposeErrors = New-Object Collections.ArrayList
    try {
        $rootRecord = Get-VerifierBrowserReceiptNaturalShutdownRoot $Context `
            $SessionRecord $OwnerRoot -RequireLaunchParentAbsent:$RequireLaunchParentAbsent
        $rootAttestation = New-VerifierBrowserDrainAttestation $scope $rootRecord $rootRecord.Process
        $rootRecord | Add-Member -NotePropertyName VerifierDrainAttestation `
            -NotePropertyValue $rootAttestation -Force
        $containmentLease = Get-VerifierBrowserContainmentJobForSession `
            $Context $SessionRecord
        [void](Assert-VerifierBrowserContainmentJobContainsRoot `
            $containmentLease.Job $rootRecord)
        [void](Assert-VerifierBrowserParentExitRecoveryListener $Context $SessionRecord $rootRecord)
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        $version = Invoke-RestMethod -Uri (
            'http://127.0.0.1:' + [string]$SessionRecord.CdpPort + '/json/version') -TimeoutSec 2
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        $endpoint = Get-VerifierBrowserShutdownEndpoint $version $SessionRecord.CdpPort
        $remainingMs = [int][Math]::Floor(
            (($budgetTicks - $budget.ElapsedTicks) * 1000.0) / [Diagnostics.Stopwatch]::Frequency)
        if ($remainingMs -le 0) {
            Throw-VerifierInfrastructure 'Browser parent-exit recovery exhausted its budget before CDP connection.'
        }
        $browserSocket = Connect-VerifierCdpSocket $endpoint (
            [DateTime]::UtcNow.AddMilliseconds([Math]::Min(2000, $remainingMs)))
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        $currentRoot = Get-VerifierBrowserReceiptNaturalShutdownRoot $Context `
            $SessionRecord $OwnerRoot -RequireLaunchParentAbsent:$RequireLaunchParentAbsent
        if (-not (Test-VerifierCurrentProcessRecordMatches $rootRecord $currentRoot)) {
            Throw-VerifierInfrastructure 'Browser parent-exit recovery root changed immutable identity before Browser.close.'
        }
        [void](Assert-VerifierBrowserParentExitRecoveryListener $Context $SessionRecord $currentRoot)
        # A receipt never grants helper termination. It does require a full
        # current PPID census before Browser.close so protected/null-command
        # Edge helpers are retained behind native handles and must naturally
        # disappear with the exact root. A helper that cannot later prove that
        # exit blocks profile/lease cleanup; it is never guessed or stopped.
        # Use the raw complete snapshot here, not the marker-selection helper:
        # a current protected descendant can disappear between that helper's
        # marker filter and its live Process lookup. The root/listener has
        # already passed the receipt authority proof above; this census owns
        # no process and validates every reachable child itself.
        $precloseSnapshot = @(Get-VerifierProcessSnapshotWithFallback `
            'bound-receipt protected-helper census')
        $protectedDescendants = @(Get-VerifierBoundReceiptNaturalShutdownDescendants `
            $rootRecord $precloseSnapshot $scope)
        $rootBeforeJournal = Get-VerifierBrowserReceiptNaturalShutdownRoot $Context `
            $SessionRecord $OwnerRoot -RequireLaunchParentAbsent:$RequireLaunchParentAbsent
        if (-not (Test-VerifierCurrentProcessRecordMatches $rootRecord $rootBeforeJournal)) {
            Throw-VerifierInfrastructure 'Browser parent-exit recovery root changed during protected-helper census.'
        }
        [void](Assert-VerifierBrowserParentExitRecoveryListener $Context $SessionRecord $rootBeforeJournal)
        Record-VerifierBrowserRecoveryCloseAttempt $Context $SessionRecord
        $rootAtSend = Get-VerifierBrowserReceiptNaturalShutdownRoot $Context `
            $SessionRecord $OwnerRoot -RequireLaunchParentAbsent:$RequireLaunchParentAbsent `
            -AfterCloseAttemptJournal
        if (-not (Test-VerifierCurrentProcessRecordMatches $rootRecord $rootAtSend)) {
            Throw-VerifierInfrastructure 'Browser parent-exit recovery root changed after close-attempt journaling.'
        }
        [void](Assert-VerifierBrowserParentExitRecoveryListener $Context $SessionRecord $rootAtSend)
        # This is the final authorization point. The exact root is still a
        # member of the no-breakaway job, which remains authoritative through
        # Browser.close and root termination even if a helper is too brief or
        # too protected for a PPID snapshot to retain.
        [void](Assert-VerifierBrowserContainmentJobContainsRoot `
            $containmentLease.Job $rootAtSend)
        $attemptKey = Get-VerifierBrowserCloseAttemptKey $SessionRecord
        $script:VerifierBrowserCloseAttempts[$attemptKey] = $true
        $SessionRecord.Runtime | Add-Member -NotePropertyName GracefulCloseAttempted `
            -NotePropertyValue $true -Force
        Send-VerifierBrowserCloseAndReadAcknowledgement $browserSocket $budget $budgetTicks
        Wait-VerifierBoundReceiptNaturalShutdownDescendants $scope $rootRecord `
            $protectedDescendants $budget $budgetTicks
        Assert-VerifierBoundReceiptNaturalShutdownNoLateDescendants $rootRecord `
            $protectedDescendants
        # An empty kernel job membership is the final containment proof. If a
        # direct helper started and exited between sampled PPID queries while
        # leaving a markerless descendant, that descendant remains a job
        # member and blocks profile/claim cleanup without any PID stop.
        Wait-VerifierBrowserContainmentJobEmpty $containmentLease.Job `
            $budget $budgetTicks
        Wait-VerifierBrowserParentExitRecoveryProfileQuiescence $SessionRecord $budget $budgetTicks
        Assert-VerifierBrowserParentExitRecoveryListenerAbsent $SessionRecord
        Assert-VerifierBrowserShutdownDeadline $budget $budgetTicks
        return [pscustomobject]@{
            ProcessTerminationProven = $true
            NaturalShutdownProven = $true
            ParentExitRecovery = $true
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
        if ($null -ne $containmentLease -and $containmentLease.DisposeAfterUse) {
            try { $containmentLease.Job.Dispose() } catch {
                [void]$disposeErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
    }
    if ($disposeErrors.Count -gt 0) {
        Throw-VerifierInfrastructure ('Browser parent-exit recovery resource disposal was unproven: ' +
            ($disposeErrors -join '; ') + '; ' + (Get-VerifierErrorMessage $failure))
    }
    if ($null -ne $failure) {
        if (Test-VerifierInfrastructureError $failure) { throw $failure }
        Throw-VerifierInfrastructure ('Browser parent-exit recovery failed: ' +
            (Get-VerifierErrorMessage $failure))
    }
}

function Close-VerifierBrowserSessionAfterLaunchParentExit($Context, $SessionRecord,
        $OwnerRoot) {
    return (Close-VerifierBrowserSessionWithBoundRecoveryReceipt $Context `
        $SessionRecord $OwnerRoot -RequireLaunchParentAbsent)
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
        Record-VerifierBrowserRecoveryCloseAttempt $Context $SessionRecord
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

function Get-VerifierIssuedContainedBrowserIdentity($Context, $SessionRecord,
        $ContainmentJob, $RecoveryAuthority = $null) {
    [void](Assert-VerifierDurableBrowserSession $SessionRecord $Context `
        'issued contained-browser identity session')
    $receipt = Assert-VerifierDurableBrowserRecoveryReceipt $SessionRecord $Context `
        'issued contained-browser identity receipt'
    $launch = Assert-VerifierDurableBrowserContainmentLaunch $SessionRecord $Context `
        'issued contained-browser identity launch'
    $identity = Assert-VerifierDurableProcessIdentityTuple $SessionRecord `
        -ProcessIdPropertyName 'ProcessId' `
        -ProcessStartPropertyName 'ProcessStartTicks' `
        -ParentProcessIdPropertyName 'ProcessParentProcessId' `
        -ParentProcessStartPropertyName 'ProcessParentProcessStartTicks' `
        -CommandLinePropertyName 'ProcessCommandLine' `
        -Label 'issued contained-browser identity session root'
    if ($null -eq $receipt -or $receipt.State -ne 'issued' -or
            $receipt.CloseAttempted -or $null -eq $launch -or
            $launch.State -notin @('launch-pending', 'launched') -or
            ($launch.State -eq 'launch-pending' -and
             ([int]$launch.LaunchProcessId -ne 0 -or
              -not [String]::IsNullOrWhiteSpace([string]$launch.LaunchedUtc))) -or
            ($launch.State -eq 'launched' -and [int]$launch.LaunchProcessId -le 0) -or
            $identity.State -ne 'absent' -or $null -eq $ContainmentJob) {
        Throw-VerifierInfrastructure 'Issued contained-browser recovery did not begin from one exact issued, unbound launch record.'
    }
    $lease = $SessionRecord.Lease
    $expectedParentId = [int]$lease.ClaimOwnerPid
    $expectedParentStartTicks = [long]$lease.ClaimOwnerStartTicks
    $candidateProcessId = 0
    if ($launch.State -eq 'launched') {
        $candidateProcessId = [int]$launch.LaunchProcessId
    } else {
        # `launch-pending` was durably written before atomic CreateProcess. A
        # crash can therefore leave no PID in the manifest even though the
        # root's query-only job handle keeps this exact job open. Derive one
        # candidate only from current members whose immutable parent and image
        # identity match the original launch tuple; the full opaque command
        # capability is checked again below before any bind mutation.
        $members = @($ContainmentJob.GetMemberProcessIds())
        if ($members.Count -eq 0) {
            Throw-VerifierInfrastructure 'Issued pending containment launch had no current job member to reattest.'
        }
        $candidates = New-Object Collections.ArrayList
        foreach ($memberProcessId in $members) {
            $member = Get-VerifierCurrentProcessRecordById ([int]$memberProcessId)
            if ($null -eq $member -or [int]$member.ProcessId -ne [int]$memberProcessId -or
                    [long]$member.ProcessStartTicks -le 0) {
                Throw-VerifierInfrastructure 'Issued pending containment launch had an uninspectable job member.'
            }
            if ([int]$member.ParentProcessId -eq $expectedParentId -and
                    (Test-VerifierConfiguredExecutableIdentity `
                        ([string]$SessionRecord.BrowserPath) $member -RequireExecutablePath)) {
                [void]$candidates.Add($member)
            }
        }
        if ($candidates.Count -ne 1) {
            Throw-VerifierInfrastructure 'Issued pending containment launch did not retain exactly one current root candidate.'
        }
        $candidateProcessId = [int]$candidates[0].ProcessId
    }
    $record = $null
    $process = $null
    if ($null -eq $RecoveryAuthority) {
        $current = Get-VerifierCurrentProcessIdentityWithRetry `
            $candidateProcessId 0 $expectedParentId '' '' `
            ([int]$SessionRecord.CdpPort) ([string]$SessionRecord.RunId) '' `
            $expectedParentStartTicks ([string]$SessionRecord.BrowserPath)
        $record = $current.Record
        $process = $current.Process
    } else {
        # The original launcher was positively proved absent while the exact
        # retained claim mutex was reacquired. Its PID/start tuple is therefore
        # the durable parent attestation; do not demand a live parent merely to
        # rehydrate the already contained root.
        $current = Get-VerifierCurrentProcessRecordById $candidateProcessId
        if ($null -eq $current -or
                [int]$current.ProcessId -ne $candidateProcessId -or
                [long]$current.ProcessStartTicks -le 0 -or
                [int]$current.ParentProcessId -ne $expectedParentId -or
                -not (Test-VerifierConfiguredExecutableIdentity `
                    ([string]$SessionRecord.BrowserPath) $current -RequireExecutablePath)) {
            Throw-VerifierInfrastructure 'Issued contained-browser recovery root did not retain its exact live PID/start/parent/executable identity.'
        }
        $parentAbsence = Confirm-VerifierRecordedProcessAbsent ([pscustomobject]@{
            ProcessId = $expectedParentId
            ProcessStartTicks = $expectedParentStartTicks
        }) 'issued contained-browser recovery original launch parent'
        if (-not $parentAbsence.QueryProven -or -not $parentAbsence.Absent -or
                $parentAbsence.Replaced) {
            Throw-VerifierInfrastructure 'Issued contained-browser recovery could not re-prove its original launch parent absent without PID reuse.'
        }
        $currentAgain = Get-VerifierCurrentProcessRecordById $candidateProcessId
        if ($null -eq $currentAgain -or
                [int]$currentAgain.ProcessId -ne [int]$current.ProcessId -or
                [long]$currentAgain.ProcessStartTicks -ne [long]$current.ProcessStartTicks -or
                [int]$currentAgain.ParentProcessId -ne [int]$current.ParentProcessId -or
                -not (Test-VerifierCommandLineEquivalent $currentAgain.CommandLine $current.CommandLine) -or
                -not (Test-VerifierConfiguredExecutableIdentity `
                    ([string]$SessionRecord.BrowserPath) $currentAgain -RequireExecutablePath)) {
            Throw-VerifierInfrastructure 'Issued contained-browser recovery root changed during its current identity recheck.'
        }
        $record = [pscustomobject]@{
            ProcessId = [int]$currentAgain.ProcessId
            ProcessStartTicks = [long]$currentAgain.ProcessStartTicks
            ParentProcessId = [int]$currentAgain.ParentProcessId
            ParentProcessStartTicks = $expectedParentStartTicks
            CommandLine = [string]$currentAgain.CommandLine
            Name = [string]$currentAgain.Name
            ExecutablePath = [string]$currentAgain.ExecutablePath
        }
        $process = $currentAgain.Process
    }
    foreach ($switch in @(
            [pscustomobject]@{ Name = '--user-data-dir'; Value = [string]$SessionRecord.Profile }
            [pscustomobject]@{ Name = '--remote-debugging-port'; Value = [string]$SessionRecord.CdpPort }
            [pscustomobject]@{ Name = '--tsj-verifier-run'; Value = [string]$SessionRecord.RunId }
            [pscustomobject]@{ Name = '--tsj-verifier-route'; Value = [string]$SessionRecord.RouteId }
            [pscustomobject]@{ Name = '--tsj-verifier-worktree'; Value = [string]$SessionRecord.RepositoryIdentity }
            [pscustomobject]@{ Name = '--tsj-verifier-recovery-token'; Value = [string]$receipt.AuthorityToken }
        )) {
        if (-not (Test-VerifierCommandLineSwitch $record.CommandLine $switch.Name $switch.Value)) {
            Throw-VerifierInfrastructure "Issued contained-browser recovery root omitted its exact $($switch.Name) authority marker."
        }
    }
    return [pscustomobject]@{ Record = $record; Process = $process }
}

function Confirm-VerifierIssuedContainedBrowserLeaseBound($Context, $SessionRecord,
        $RootRecord, $ContainmentJob, [switch]$DeferManifestWrite) {
    [void](Assert-VerifierDurableBrowserSession $SessionRecord $Context `
        'issued contained-browser bind session')
    $recoveryAuthority = Get-VerifierRetainedBrowserRecoveryLeaseAuthority `
        $Context $SessionRecord.Lease 'issued contained-browser bind recovery authority'
    if ($null -eq $recoveryAuthority -or $null -eq $RootRecord -or
            -not (Test-VerifierStrictIntegralValue $RootRecord.ProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $RootRecord.ProcessStartTicks 1) -or
            -not (Test-VerifierStrictIntegralValue $RootRecord.ParentProcessId 1 ([int]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $RootRecord.ParentProcessStartTicks 1) -or
            -not (Test-VerifierStrictStringValue $RootRecord.CommandLine) -or
            [String]::IsNullOrWhiteSpace([string]$RootRecord.CommandLine)) {
        Throw-VerifierInfrastructure 'Issued contained-browser bind omitted its exact recovery authority or complete root tuple.'
    }
    $lease = $SessionRecord.Lease
    if ($lease.Kind -ne 'cdp' -or $lease.Status -ne 'leased' -or
            $lease.ClaimState -ne 'held' -or $lease.ReleaseState -ne 'active' -or
            $lease.ReleaseJournalState -ne 'active' -or -not $lease.Registered -or
            $lease.ProcessProofRequired -or $lease.ReleaseBlocked -or
            $lease.MutexReleased -or [int]$lease.BoundProcessId -ne 0 -or
            [long]$lease.BoundProcessStartTicks -ne 0 -or
            [int]$lease.ListenerProcessId -ne 0 -or
            [long]$lease.ListenerProcessStartTicks -ne 0 -or
            [int]$RootRecord.ParentProcessId -ne [int]$lease.ClaimOwnerPid -or
            [long]$RootRecord.ParentProcessStartTicks -ne [long]$lease.ClaimOwnerStartTicks) {
        Throw-VerifierInfrastructure 'Issued contained-browser bind did not retain the exact unbound claim/root lifecycle.'
    }
    [void](Assert-VerifierBrowserContainmentJobContainsRoot $ContainmentJob $RootRecord)
    if ($Context.PSObject.Properties['TestHooks'] -and
            $Context.TestHooks.PSObject.Properties['FailNextIssuedContainedRecoveryListenerBind'] -and
            [bool]$Context.TestHooks.FailNextIssuedContainedRecoveryListenerBind) {
        $Context.TestHooks.FailNextIssuedContainedRecoveryListenerBind = $false
        Throw-VerifierInfrastructure 'Injected issued-contained browser recovery listener-bind failure after root reattestation.'
    }
    $inspection = Get-VerifierLoopbackListenerRecords ([int]$SessionRecord.CdpPort) -PreferNetstat
    if (-not (Test-VerifierListenerInspectionSchema $inspection) -or
            -not $inspection.Success -or -not $inspection.Known -or
            -not $inspection.HasListeners) {
        Throw-VerifierInfrastructure 'Issued contained-browser bind could not prove one current CDP listener inspection.'
    }
    $listeners = @($inspection.Listeners)
    if ($listeners.Count -eq 0) {
        Throw-VerifierInfrastructure 'Issued contained-browser bind returned no current CDP listener records.'
    }
    foreach ($listener in $listeners) {
        if (-not (Test-VerifierListenerRecordSchema $listener) -or
                $listener.ListenerOwnerKind -ne $script:VerifierUserProcessOwnerKind -or
                [int]$listener.Port -ne [int]$SessionRecord.CdpPort -or
                [int]$listener.ProcessId -ne [int]$RootRecord.ProcessId -or
                [long]$listener.ProcessStartTicks -ne [long]$RootRecord.ProcessStartTicks) {
            Throw-VerifierInfrastructure 'Issued contained-browser bind rejected a foreign, replaced, or malformed CDP listener owner.'
        }
        $currentListener = Get-VerifierCurrentProcessRecordById ([int]$listener.ProcessId)
        if ($null -eq $currentListener -or
                [long]$currentListener.ProcessStartTicks -ne [long]$listener.ProcessStartTicks -or
                [int]$currentListener.ParentProcessId -ne [int]$RootRecord.ParentProcessId -or
                -not (Test-VerifierCommandLineEquivalent $currentListener.CommandLine $RootRecord.CommandLine) -or
                -not (Test-VerifierConfiguredExecutableIdentity `
                    ([string]$SessionRecord.BrowserPath) $currentListener -RequireExecutablePath)) {
            Throw-VerifierInfrastructure 'Issued contained-browser bind listener changed during exact root identity validation.'
        }
    }
    # This is deliberately narrower than the ordinary bind path: the original
    # launch parent is absent and was authenticated by the retained recovery
    # capability above. The exact no-breakaway job, launch PID, current root,
    # and listener are all re-proven before mutating the held lease.
    $lease.ListenerInspectionSuccess = $inspection.Success
    $lease.ListenerInspectionKnown = $inspection.Known
    $lease.ListenerHasListeners = $inspection.HasListeners
    $lease.ListenerAbsent = -not $inspection.HasListeners
    $lease.ListenerProcessId = [int]$listeners[0].ProcessId
    $lease.ListenerProcessStartTicks = [long]$listeners[0].ProcessStartTicks
    $lease.ListenerOwnerKind = [string]$inspection.ListenerOwnerKind
    $lease.ListenerOwnerProof = [string]$inspection.ListenerOwnerProof
    $lease.ListenerOwnerEvidence = [string]$inspection.ListenerOwnerEvidence
    $lease.ListenerInspectionUtc = Get-VerifierUtcText
    $lease.BoundProcessId = [int]$RootRecord.ProcessId
    $lease.BoundProcessStartTicks = [long]$RootRecord.ProcessStartTicks
    $lease.BindValidatedUtc = Get-VerifierUtcText
    $lease.ClaimState = 'bound'
    $lease.Status = 'bound'
    $lease.ProcessProofRequired = $true
    if (-not $DeferManifestWrite) {
        Write-VerifierManifest $Context
    }
}

function Resume-VerifierIssuedContainedBrowserSession($Context, $SessionRecord) {
    [void](Assert-VerifierDurableBrowserSession $SessionRecord $Context `
        'issued contained-browser resume session')
    $receipt = Assert-VerifierDurableBrowserRecoveryReceipt $SessionRecord $Context `
        'issued contained-browser resume receipt'
    $launch = Assert-VerifierDurableBrowserContainmentLaunch $SessionRecord $Context `
        'issued contained-browser resume launch'
    if ($null -eq $receipt -or $receipt.State -ne 'issued' -or
            $receipt.CloseAttempted -or $null -eq $launch -or
            $launch.State -notin @('launch-pending', 'launched') -or
            ($launch.State -eq 'launch-pending' -and
             ([int]$launch.LaunchProcessId -ne 0 -or
              -not [String]::IsNullOrWhiteSpace([string]$launch.LaunchedUtc))) -or
            ($launch.State -eq 'launched' -and [int]$launch.LaunchProcessId -le 0) -or
            $SessionRecord.Status -notin @('leased', 'startup-failed', 'cleanup-failed')) {
        Throw-VerifierInfrastructure 'Issued contained-browser resume was not an eligible unbound launch failure.'
    }
    if ($Context.PSObject.Properties['TestHooks'] -and
            $Context.TestHooks.PSObject.Properties['FailNextIssuedContainedRecovery'] -and
            [bool]$Context.TestHooks.FailNextIssuedContainedRecovery) {
        $Context.TestHooks.FailNextIssuedContainedRecovery = $false
        Throw-VerifierInfrastructure 'Injected issued-contained browser recovery failure before root reattestation.'
    }
    $containmentLease = Get-VerifierBrowserContainmentJobForSession $Context $SessionRecord
    if ($null -eq $SessionRecord.Runtime.ContainmentJob) {
        $SessionRecord.Runtime.ContainmentJob = $containmentLease.Job
    }
    if ($launch.State -eq 'launched') {
        $placeholderRoot = [pscustomobject]@{ ProcessId = [int]$launch.LaunchProcessId }
        [void](Assert-VerifierBrowserContainmentJobContainsRoot $containmentLease.Job $placeholderRoot)
    }
    $recoveryAuthority = Get-VerifierRetainedBrowserRecoveryLeaseAuthority `
        $Context $SessionRecord.Lease 'issued contained-browser resume recovery authority'
    $identity = Get-VerifierIssuedContainedBrowserIdentity $Context $SessionRecord `
        $containmentLease.Job $recoveryAuthority
    $rootRecord = $identity.Record
    [void](Assert-VerifierBrowserContainmentJobContainsRoot $containmentLease.Job $rootRecord)
    $resumeSnapshot = New-VerifierBrowserBindTransactionSnapshot $SessionRecord
    try {
        if ($launch.State -eq 'launch-pending') {
            Set-VerifierBrowserContainmentLaunch $Context $SessionRecord `
                $containmentLease.Job ([int]$rootRecord.ProcessId)
        }
        $bindLease = if ($null -eq $recoveryAuthority) {
            {
                param($boundSession)
                Confirm-VerifierPortLeaseBound $Context $boundSession.Lease `
                    $boundSession.ProcessId $boundSession.ProcessStartTicks $boundSession `
                    -DeferManifestWrite
            }
        } else {
            {
                param($boundSession)
                Confirm-VerifierIssuedContainedBrowserLeaseBound $Context $boundSession `
                    $rootRecord $containmentLease.Job -DeferManifestWrite
            }
        }
        Invoke-VerifierBrowserBindTransaction $Context $SessionRecord $rootRecord $bindLease
    } catch {
        Restore-VerifierBrowserBindTransactionSnapshot $SessionRecord $resumeSnapshot
        throw
    }
    $SessionRecord.Runtime.Browser = $identity.Process
}

function Complete-VerifierBrowserSession($Context, $SessionRecord) {
    # Durable validation is the cleanup entry boundary.  This must run before
    # even reading CleanupResult: a malformed terminal-looking record is not a
    # no-op and cannot authorize skipping process, profile, or lease proof.
    Assert-VerifierDurableManifestContext $Context
    Assert-VerifierDurableBrowserSession $SessionRecord $Context `
        'browser cleanup session'
    # The present verifier creates a receipt before a browser profile/claim is
    # allocated. A record without it is historical retained evidence, not a
    # legacy authority to issue Browser.close or to fall back to PID/PPID
    # cleanup. Keep its resources intact and report typed infrastructure.
    $requiredRecoveryReceipt = Assert-VerifierDurableBrowserRecoveryReceipt `
        $SessionRecord $Context 'browser cleanup required recovery receipt'
    if ($null -eq $requiredRecoveryReceipt) {
        Throw-VerifierInfrastructure 'Browser cleanup requires one durable recovery receipt; missing receipts are retained and never legacy-fallback cleaned.'
    }
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
            $launch = Assert-VerifierDurableBrowserContainmentLaunch $SessionRecord $Context `
                'browser cleanup issued containment launch'
            if ($null -ne $launch -and
                    (($launch.State -eq 'launched' -and
                      [int]$launch.LaunchProcessId -gt 0) -or
                     ($launch.State -eq 'launch-pending' -and
                      [int]$launch.LaunchProcessId -eq 0)) -and
                    $requiredRecoveryReceipt.State -eq 'issued' -and
                    -not $requiredRecoveryReceipt.CloseAttempted) {
                Resume-VerifierIssuedContainedBrowserSession $Context $SessionRecord
            }
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

        # A fresh, bound receipt gives an exact root/listener authority for a
        # Browser.close-only natural exit only after its protected-helper
        # census has retained native observations. Unbound/failed startup
        # sessions retain the original complete-census path. Missing receipts
        # were rejected at this cleanup entry boundary and never fall back.
        $recoveryReceipt = $requiredRecoveryReceipt
        $useBoundReceiptNaturalShutdown = ($SessionRecord.Status -ceq 'attached' -and
            $null -ne $recoveryReceipt -and $recoveryReceipt.State -eq 'bound' -and
            -not $recoveryReceipt.CloseAttempted)
        $snapshot = $null
        if (-not $useBoundReceiptNaturalShutdown) {
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
        }

        if ([int]$SessionRecord.ProcessId -gt 0) {
            # A receipt-bound Browser.close path proves the exact root/listener
            # immediately before send and proves root exit, listener absence,
            # and profile quiescence afterward. The older route retains its
            # complete graph/descendant proof and fixed-point termination.
            $drainResult = if ($SessionRecord.Status -ceq 'attached') {
                if ($useBoundReceiptNaturalShutdown) {
                    if (Test-VerifierBrowserLaunchParentAbsentForRecovery $SessionRecord) {
                        Close-VerifierBrowserSessionAfterLaunchParentExit $Context $SessionRecord $ownerRoot
                    } else {
                        Close-VerifierBrowserSessionWithBoundRecoveryReceipt `
                            $Context $SessionRecord $ownerRoot
                    }
                } elseif (Test-VerifierBrowserLaunchParentAbsentForRecovery $SessionRecord) {
                    Close-VerifierBrowserSessionAfterLaunchParentExit $Context $SessionRecord $ownerRoot
                } else {
                    Close-VerifierBrowserSessionNaturally $Context $SessionRecord $ownerRoot
                }
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
        # Bound-receipt cleanup reached an empty no-breakaway job before the
        # profile deletion above. Release this in-memory handle only after the
        # terminal filesystem proof; a failed cleanup keeps the named kernel
        # containment object recoverable while any member remains alive.
        Dispose-VerifierBrowserContainmentJob $SessionRecord
        Close-VerifierBrowserRecoveryReceipt $SessionRecord
        $SessionRecord.Status = 'cleaned'
        $SessionRecord.CleanupResult = 'complete'
        $SessionRecord.Error = ''
        $SessionRecord.Runtime.Browser = $null
        Write-VerifierManifest $Context
    } catch {
        $priorFailure = if ($SessionRecord.PSObject.Properties['Error'] -and
                (Test-VerifierStrictStringValue $SessionRecord.Error) -and
                -not [String]::IsNullOrWhiteSpace([string]$SessionRecord.Error)) {
            [string]$SessionRecord.Error
        } else { '' }
        $cleanupFailure = Get-VerifierErrorMessage $_
        $SessionRecord.Status = 'cleanup-failed'
        $SessionRecord.CleanupResult = 'infrastructure-failure'
        $SessionRecord.Error = if ([String]::IsNullOrWhiteSpace($priorFailure)) {
            $cleanupFailure
        } else {
            'prior: ' + $priorFailure + '; cleanup: ' + $cleanupFailure
        }
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
    $containmentJob = $null
    $containedLaunchProcessId = 0
    try {
        if (-not (Test-Path -LiteralPath $BrowserPath -PathType Leaf)) {
            Throw-VerifierInfrastructure "Browser executable was not found: $BrowserPath"
        }
        $arguments = @('--headless=new', '--disable-gpu', '--no-first-run', '--disable-sync',
            '--window-size=1440,1000', "--user-data-dir=$($browserSessionRecord.Profile)",
            "--remote-debugging-port=$($browserSessionRecord.CdpPort)",
            "--tsj-verifier-run=$($Context.RunId)",
            "--tsj-verifier-route=$($browserSessionRecord.RouteId)",
            "--tsj-verifier-worktree=$($Context.RepositoryIdentity)",
            "--tsj-verifier-recovery-token=$($browserSessionRecord.RecoveryReceipt.AuthorityToken)",
            'about:blank')
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        # The browser root enters its named no-breakaway job in the same
        # CreateProcess operation that starts it.  Assigning a job after a
        # normal launch would leave a child-creation gap and cannot prove
        # containment of a transient, markerless helper.
        $containmentJob = New-VerifierBrowserContainmentJob $Context $browserSessionRecord
        $browserSessionRecord.Runtime.ContainmentJob = $containmentJob
        Prepare-VerifierBrowserContainmentLaunch $Context $browserSessionRecord $containmentJob
        $containedLaunchProcessId = Start-VerifierBrowserProcessInContainmentJob $containmentJob `
            $BrowserPath $arguments $Context.WorktreeRoot
        if ($Context.PSObject.Properties['TestHooks'] -and
                $Context.TestHooks.PSObject.Properties['FailNextBrowserContainmentLaunchLedgerPublish'] -and
                [bool]$Context.TestHooks.FailNextBrowserContainmentLaunchLedgerPublish) {
            $Context.TestHooks.FailNextBrowserContainmentLaunchLedgerPublish = $false
            Throw-VerifierInfrastructure 'Injected post-CreateProcess containment launch-ledger publication failure.'
        }
        Set-VerifierBrowserContainmentLaunch $Context $browserSessionRecord `
            $containmentJob $containedLaunchProcessId
        # Persist the only durable post-CreateProcess identity before trying to
        # obtain a Process handle. If that read, WMI, CDP, or listener binding
        # fails, the named no-breakaway job and the launch PID remain available
        # to the exact issued-session recovery path.
        Write-VerifierManifest $Context
        $containmentJob = $null
        if ($Context.PSObject.Properties['TestHooks'] -and
                $Context.TestHooks.PSObject.Properties['FailNextBrowserContainmentHandleAcquire'] -and
                [bool]$Context.TestHooks.FailNextBrowserContainmentHandleAcquire) {
            $Context.TestHooks.FailNextBrowserContainmentHandleAcquire = $false
            Throw-VerifierInfrastructure 'Injected post-containment-launch Process-handle acquisition failure.'
        }
        $browser = Get-Process -Id $containedLaunchProcessId -ErrorAction Stop
        $browser.Refresh()
        if ([bool]$browser.HasExited -or [int]$browser.Id -ne $containedLaunchProcessId) {
            Throw-VerifierInfrastructure "Atomically contained browser PID $containedLaunchProcessId exited before its retained handle could be established."
        }
        $browserSessionRecord.Runtime.Browser = $browser
        # Keep the durable session at its exact absent tuple until the live
        # process has passed every identity check.  A failed WMI/parent/command
        # read must not leave ProcessId/StartTicks partially published for the
        # catch block's manifest write; the retained runtime handle remains
        # diagnostic evidence, while cleanup fails closed without a root proof.
        $browserIdentity = Get-VerifierFreshLaunchedBrowserIdentity $browser `
            $browserSessionRecord.CdpPort $Context.RunId $BrowserPath $deadline
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
        # Prove the exact loopback listener and commit root/lease/receipt as one
        # manifest replacement. A failure before that commit restores the
        # issued containment ledger, rather than publishing a tuple that cannot
        # independently authorize either recovery mode.
        $bindLease = {
            param($boundSession)
            Confirm-VerifierPortLeaseBound $Context $boundSession.Lease `
                $boundSession.ProcessId $boundSession.ProcessStartTicks $boundSession `
                -DeferManifestWrite
        }
        Invoke-VerifierBrowserBindTransaction $Context $browserSessionRecord `
            $browserIdentity.Record $bindLease ([string]$target.id)
        $socket = Connect-VerifierCdpSocket $targetUri $deadline
        $browserSessionRecord.Runtime.Socket = $socket
        return [pscustomobject]@{
            Browser = $browser; Socket = $socket; Profile = $browserSessionRecord.Profile
            CdpPort = $browserSessionRecord.CdpPort; RouteId = $browserSessionRecord.RouteId
            Deadline = $deadline; Record = $browserSessionRecord
        }
    } catch {
        $startupError = $_
        if ($null -ne $containmentJob -and $containedLaunchProcessId -le 0) {
            try {
                $containmentJob.Dispose()
                $browserSessionRecord.Runtime.ContainmentJob = $null
            } catch { }
        }
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
    'Invoke-VerifierRetainedBrowserRecovery',
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

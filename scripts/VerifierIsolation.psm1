Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:VerifierHeldPortClaims = @{}

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

function Get-VerifierFullPath([string]$Path) {
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

function Get-VerifierProcessStartTicks($Process) {
    try {
        return [long]$Process.StartTime.ToUniversalTime().Ticks
    } catch {
        Throw-VerifierInfrastructure ("Could not read process start identity: " +
            (Get-VerifierErrorMessage $_))
    }
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

function Remove-VerifierOwnedTree([string]$Root, [string]$Target) {
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

function ConvertTo-VerifierWindowsArgument([string]$Value) {
    # Start-Process on Windows PowerShell 5.1 ultimately receives one Windows
    # command line. Always quote with CommandLineToArgvW-compatible backslash
    # handling so a worktree/profile/script path containing spaces is one
    # argument, including when it ends in a backslash.
    if ($null -eq $Value -or $Value.Length -eq 0) { return '""' }
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

function ConvertTo-VerifierArgumentString([string[]]$Arguments) {
    return [string]::Join(' ', @($Arguments | ForEach-Object {
        ConvertTo-VerifierWindowsArgument ([string]$_)
    }))
}

function Start-VerifierProcess([string]$FilePath, [string[]]$Arguments,
        [string]$RedirectStandardOutput = '', [string]$RedirectStandardError = '') {
    try {
        if ([String]::IsNullOrWhiteSpace($FilePath)) {
            Throw-VerifierInfrastructure 'A verifier process path was empty.'
        }
        $startParameters = @{
            FilePath = $FilePath
            ArgumentList = ConvertTo-VerifierArgumentString $Arguments
            PassThru = $true
            WindowStyle = 'Hidden'
            ErrorAction = 'Stop'
        }
        if (-not [String]::IsNullOrWhiteSpace($RedirectStandardOutput)) {
            $startParameters.RedirectStandardOutput = $RedirectStandardOutput
        }
        if (-not [String]::IsNullOrWhiteSpace($RedirectStandardError)) {
            $startParameters.RedirectStandardError = $RedirectStandardError
        }
        return (Start-Process @startParameters)
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not start verifier process '$FilePath': " +
            (Get-VerifierErrorMessage $_))
    }
}

function Stop-VerifierBoundedProcessExactly($Process, [long]$ExpectedStartTicks,
        [int]$WaitMilliseconds = 5000) {
    if ($null -eq $Process -or [int]$Process.Id -le 0) {
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

function Invoke-VerifierBoundedProcess([string]$FilePath, [string[]]$Arguments,
        [int]$TimeoutMilliseconds = 5000) {
    if ($TimeoutMilliseconds -lt 1) {
        Throw-VerifierInfrastructure 'A bounded process timeout must be positive.'
    }
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
        $startInfo.FileName = $FilePath
        $startInfo.Arguments = ConvertTo-VerifierArgumentString $Arguments
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
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        try {
            $startTicks = Get-VerifierProcessStartTicks $process
        } catch {
            # If identity cannot be captured, only accept natural exit; never
            # guess at a reused PID that could later be killed.
            if (-not $process.WaitForExit($TimeoutMilliseconds)) {
                Throw-VerifierInfrastructure "Could not capture bounded process identity and PID $processId did not exit within the bound."
            }
            $process.Refresh()
            if (-not [bool]$process.HasExited) {
                Throw-VerifierInfrastructure "Could not prove bounded process PID $processId terminated after identity capture failed."
            }
            $terminationProven = $true
            Throw-VerifierInfrastructure "Could not capture bounded process start identity for PID $processId."
        }

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
        $exitText = if ($null -eq $rawExitCode) { '' } else { [string]$rawExitCode }
        if ([String]::IsNullOrWhiteSpace($exitText) -or $exitText -notmatch '^-?\d+$') {
            Throw-VerifierInfrastructure "Bounded process '$FilePath' did not expose a numeric exit code."
        }
        try { $exitCode = [int]$exitText } catch {
            Throw-VerifierInfrastructure "Bounded process '$FilePath' exposed an out-of-range exit code '$exitText'."
        }
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

function Get-VerifierPortMutexName($Context, [int]$Port) {
    # The mutex is intentionally independent of repository/worktree identity.
    # Worktree/run metadata belongs in the claim record; the OS-level claim must
    # serialize the same loopback port across every worktree and process.
    return 'Global\TroubleshootJS.Verifier.Port.' + [string]$Port
}

function Test-VerifierLoopbackAddress([string]$Address) {
    $normalized = ([string]$Address).Trim().Trim('[', ']')
    return $normalized -in @('127.0.0.1', '0.0.0.0', '::1', '::')
}

function Get-VerifierListenerProcessRecord([int]$ProcessId, [string]$LocalAddress,
        [int]$Port, [string]$Source) {
    if ($ProcessId -le 0) {
        Throw-VerifierInfrastructure "Loopback listener query returned an invalid PID for port $Port."
    }
    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        Throw-VerifierInfrastructure "Loopback port $Port is listening under a process that no longer exists; ownership is not provable."
    }
    $startTicks = Get-VerifierProcessStartTicks $process
    return [pscustomobject]@{
        LocalAddress = $LocalAddress
        Port = $Port
        ProcessId = $ProcessId
        ProcessStartTicks = $startTicks
        Source = $Source
    }
}

function New-VerifierListenerInspection([bool]$Success, [bool]$Known,
        [object[]]$Listeners, [string]$Source, [string]$ErrorMessage = '') {
    $normalized = @($Listeners)
    return [pscustomobject]@{
        Success = $Success
        Known = $Known
        HasListeners = ($normalized.Count -gt 0)
        Listeners = $normalized
        Source = $Source
        Error = $ErrorMessage
    }
}

function Parse-VerifierNetstatListenerOutput([int]$Port, [object[]]$Output,
        [int]$ExitCode = 0) {
    if ($Port -le 0 -or $Port -gt 65535) {
        Throw-VerifierInfrastructure "Cannot inspect invalid loopback port $Port."
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
    foreach ($rawLine in $lines) {
        $line = ([string]$rawLine).Trim()
        if ([String]::IsNullOrWhiteSpace($line)) { continue }
        if ($line -match '^(?i:Active Connections)$' -or
                $line -match '^(?i:Proto\s+Local Address\s+Foreign Address\s+State(?:\s+PID)?)$') {
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
        $sawDataLine = $true
        if ([int]$match.Groups['localPort'].Value -ne $Port -or
                $match.Groups['state'].Value -notmatch '^(?i:LISTENING)$') { continue }
        $address = $match.Groups['local'].Value
        if (-not (Test-VerifierLoopbackAddress $address)) { continue }
        [void]$records.Add((Get-VerifierListenerProcessRecord `
            ([int]$match.Groups['pid'].Value) $address $Port 'netstat'))
    }
    if (-not $sawDataLine) {
        Throw-VerifierInfrastructure "netstat.exe returned no complete TCP records while inspecting loopback port $Port."
    }
    $unique = @($records | Sort-Object ProcessId, LocalAddress -Unique)
    return (New-VerifierListenerInspection $true $true $unique 'netstat')
}

function Get-VerifierLoopbackListenerRecords([int]$Port) {
    if ($Port -le 0 -or $Port -gt 65535) {
        Throw-VerifierInfrastructure "Cannot inspect invalid loopback port $Port."
    }
    $records = New-Object Collections.ArrayList
    $netConnectionCommand = Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue
    $netConnectionError = $null
    $netConnectionSucceeded = $false
    if ($null -ne $netConnectionCommand) {
        try {
            $connections = @(& $netConnectionCommand -LocalPort $Port -State Listen -ErrorAction Stop)
            $netConnectionSucceeded = $true
        } catch {
            $netConnectionError = Get-VerifierErrorMessage $_
        }
    }
    if ($netConnectionSucceeded -and $connections.Count -gt 0) {
        foreach ($connection in $connections) {
            if ($null -eq $connection -or
                    -not $connection.PSObject.Properties['LocalAddress'] -or
                    -not $connection.PSObject.Properties['LocalPort'] -or
                    -not $connection.PSObject.Properties['OwningProcess'] -or
                    -not $connection.PSObject.Properties['State']) {
                Throw-VerifierInfrastructure "Get-NetTCPConnection returned an incomplete listener record for port $Port."
            }
            if ([int]$connection.LocalPort -ne $Port -or
                    [string]$connection.State -notmatch '^(?i:Listen|Listening)$') {
                Throw-VerifierInfrastructure "Get-NetTCPConnection returned an inconsistent listener record for port $Port."
            }
            $address = ([string]$connection.LocalAddress).Trim()
            if (-not (Test-VerifierLoopbackAddress $address)) { continue }
            [void]$records.Add((Get-VerifierListenerProcessRecord $connection.OwningProcess `
                $address $Port 'Get-NetTCPConnection'))
        }
        $unique = @($records | Sort-Object ProcessId, LocalAddress -Unique)
        return (New-VerifierListenerInspection $true $true $unique 'Get-NetTCPConnection')
    }

    # A successful query with no records is still not accepted as a free-port
    # proof at this boundary. Fall through to the complete netstat output so
    # headers/data-line integrity is checked and a malformed/empty result is
    # classified as unknown infrastructure rather than absence.

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
        return (Parse-VerifierNetstatListenerOutput $Port @($netstatOutputList) $netstatResult.ExitCode)
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

function Get-VerifierManifestBooleanValue($Object, [string]$Name, $Default = $null) {
    if ($null -eq $Object -or [String]::IsNullOrWhiteSpace($Name) -or
            $null -eq $Object.PSObject.Properties[$Name]) {
        return $Default
    }
    $value = $Object.PSObject.Properties[$Name].Value
    # Preserve null/non-Boolean values in the durable view.  Casting a null
    # to [bool] would manufacture `false` and allow a terminal tombstone to
    # look as though listener/process proof had actually been recorded.
    if ($null -eq $value -or $value.GetType() -ne [bool]) { return $null }
    return [bool]$value
}

function Get-VerifierManifestView($Context) {
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
            profile = if ($_.PSObject.Properties['ProfilePath']) { $_.ProfilePath } else { '' }
            browserPath = if ($_.PSObject.Properties['BrowserPath']) { $_.BrowserPath } else { '' }
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
            registered = Get-VerifierManifestBooleanValue $_ 'Registered' $false
            boundProcessId = $_.BoundProcessId
            boundProcessStartTicks = $_.BoundProcessStartTicks
            listenerProcessId = if ($_.PSObject.Properties['ListenerProcessId']) {
                $_.ListenerProcessId
            } else { 0 }
            listenerProcessStartTicks = if ($_.PSObject.Properties['ListenerProcessStartTicks']) {
                $_.ListenerProcessStartTicks
            } else { 0 }
            bindValidatedUtc = $_.BindValidatedUtc
             releasedUtc = $_.ReleasedUtc
             releaseState = if ($_.PSObject.Properties['ReleaseState']) {
                 $_.ReleaseState
             } else { 'active' }
             releaseJournalState = if ($_.PSObject.Properties['ReleaseJournalState']) {
                 $_.ReleaseJournalState
             } else { $null }
             releaseBlocked = Get-VerifierManifestBooleanValue $_ 'ReleaseBlocked' $false
             releaseBlockReason = if ($_.PSObject.Properties['ReleaseBlockReason']) {
                 $_.ReleaseBlockReason
             } else { '' }
             mutexReleased = Get-VerifierManifestBooleanValue $_ 'MutexReleased' $false
             listenerInspectionSuccess = Get-VerifierManifestBooleanValue $_ 'ListenerInspectionSuccess' $false
             listenerInspectionKnown = Get-VerifierManifestBooleanValue $_ 'ListenerInspectionKnown' $false
             listenerHasListeners = Get-VerifierManifestBooleanValue $_ 'ListenerHasListeners' $null
             listenerAbsent = Get-VerifierManifestBooleanValue $_ 'ListenerAbsent' $null
             listenerInspectionUtc = if ($_.PSObject.Properties['ListenerInspectionUtc']) {
                 $_.ListenerInspectionUtc
             } else { '' }
             processTerminationProven = Get-VerifierManifestBooleanValue $_ 'ProcessTerminationProven' $false
             processAbsent = Get-VerifierManifestBooleanValue $_ 'ProcessAbsent' $false
             processProofRequired = Get-VerifierManifestBooleanValue $_ 'ProcessProofRequired' $null
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
             browserPath = if ($_.PSObject.Properties['BrowserPath']) { $_.BrowserPath } else { '' }
             cdpLeasePath = $_.Lease.Path
             profile = $_.Profile
             processId = $_.ProcessId
             processStartTicks = $_.ProcessStartTicks
             processParentProcessId = if ($_.PSObject.Properties['ProcessParentProcessId']) {
                 $_.ProcessParentProcessId
             } else { 0 }
             processParentProcessStartTicks = if ($_.PSObject.Properties['ProcessParentProcessStartTicks']) {
                 $_.ProcessParentProcessStartTicks
             } else { 0 }
            processCommandLine = if ($_.PSObject.Properties['ProcessCommandLine']) {
                $_.ProcessCommandLine
            } else { '' }
            targetId = $_.TargetId
            expectedUrl = $_.ExpectedUrl
            expectedRunMarker = 'tsjVerifierRun=' + $_.RunId
            expectedRouteMarker = 'tsjVerifierRoute=' + $_.RouteId
            status = $_.Status
            cleanupResult = $_.CleanupResult
            profileProcessScanCompleted = Get-VerifierManifestBooleanValue $_ 'ProfileProcessScanCompleted' $false
            profileInspectionFailed = Get-VerifierManifestBooleanValue $_ 'ProfileInspectionFailed' $false
            error = $_.Error
        }
    })
    $server = if ($null -eq $Context.Server) { $null } else {
        [ordered]@{
            owner = $Context.Server.Owner
            baseUrl = $Context.Server.BaseUrl
            repositoryIdentity = $Context.RepositoryIdentity
            worktreeRoot = $Context.WorktreeRoot
            port = $Context.Server.Port
             processId = $Context.Server.ProcessId
             processStartTicks = $Context.Server.ProcessStartTicks
             repositoryRoot = if ($Context.Server.PSObject.Properties['RepositoryRoot']) {
                 $Context.Server.RepositoryRoot
             } else { $Context.WorktreeRoot }
             webRoot = if ($Context.Server.PSObject.Properties['WebRoot']) {
                 $Context.Server.WebRoot
             } else { '' }
             identityProtocol = if ($Context.Server.PSObject.Properties['IdentityProtocol']) {
                 $Context.Server.IdentityProtocol
             } else { '' }
             identityVerified = Get-VerifierManifestBooleanValue $Context.Server 'IdentityVerified' $false
             callerOwned = Get-VerifierManifestBooleanValue $Context.Server 'CallerOwned' $false
              processParentProcessId = if ($Context.Server.PSObject.Properties['ProcessParentProcessId']) {
                  $Context.Server.ProcessParentProcessId
              } else { 0 }
              processParentProcessStartTicks = if ($Context.Server.PSObject.Properties['ProcessParentProcessStartTicks']) {
                  $Context.Server.ProcessParentProcessStartTicks
              } else { 0 }
            processCommandLine = if ($Context.Server.PSObject.Properties['ProcessCommandLine']) {
                $Context.Server.ProcessCommandLine
            } else { '' }
             script = $Context.Server.Script
             runId = $Context.Server.RunId
             nonce = $Context.Server.Nonce
             leaseId = if ($Context.Server.PSObject.Properties['Lease'] -and
                     $null -ne $Context.Server.Lease) {
                 $Context.Server.Lease.LeaseId
             } else { '' }
             leaseKind = if ($Context.Server.PSObject.Properties['Lease'] -and
                     $null -ne $Context.Server.Lease) {
                 $Context.Server.Lease.Kind
             } else { '' }
             leaseClaimName = if ($Context.Server.PSObject.Properties['Lease'] -and
                     $null -ne $Context.Server.Lease) {
                 $Context.Server.Lease.ClaimName
             } else { '' }
             leaseClaimState = if ($Context.Server.PSObject.Properties['Lease'] -and
                     $null -ne $Context.Server.Lease) {
                 $Context.Server.Lease.ClaimState
             } else { '' }
             leaseReleaseState = if ($Context.Server.PSObject.Properties['Lease'] -and
                     $null -ne $Context.Server.Lease) {
                 $Context.Server.Lease.ReleaseState
             } else { '' }
             leaseReleaseJournalState = if ($Context.Server.PSObject.Properties['Lease'] -and
                     $null -ne $Context.Server.Lease -and
                     $Context.Server.Lease.PSObject.Properties['ReleaseJournalState']) {
                 $Context.Server.Lease.ReleaseJournalState
             } else { '' }
             leaseOwnerPid = if ($Context.Server.PSObject.Properties['Lease'] -and
                     $null -ne $Context.Server.Lease) {
                 $Context.Server.Lease.ClaimOwnerPid
             } else { 0 }
             leaseOwnerStartTicks = if ($Context.Server.PSObject.Properties['Lease'] -and
                     $null -ne $Context.Server.Lease) {
                 $Context.Server.Lease.ClaimOwnerStartTicks
             } else { 0 }
             leasePath = if ($Context.Server.PSObject.Properties['Lease'] -and
                     $null -ne $Context.Server.Lease) {
                $Context.Server.Lease.Path
            } else { '' }
             leaseListenerAbsent = if ($Context.Server.PSObject.Properties['Lease'] -and
                     $null -ne $Context.Server.Lease -and
                     $Context.Server.Lease.PSObject.Properties['ListenerAbsent']) {
                 $Context.Server.Lease.ListenerAbsent
             } else { $null }
             leaseProcessProofRequired = if ($Context.Server.PSObject.Properties['Lease'] -and
                     $null -ne $Context.Server.Lease -and
                     $Context.Server.Lease.PSObject.Properties['ProcessProofRequired']) {
                 $Context.Server.Lease.ProcessProofRequired
             } else { $null }
            state = $Context.Server.State
            stdoutLog = $Context.Server.StdoutLog
            stderrLog = $Context.Server.StderrLog
             cleanupResult = $Context.Server.CleanupResult
             error = $Context.Server.Error
             processIdentityKnown = Get-VerifierManifestBooleanValue $Context.Server 'ProcessIdentityKnown' $false
             ownershipUncertain = Get-VerifierManifestBooleanValue $Context.Server 'OwnershipUncertain' $false
             processTerminationProven = Get-VerifierManifestBooleanValue $Context.Server 'ProcessTerminationProven' $false
             processAbsent = Get-VerifierManifestBooleanValue $Context.Server 'ProcessAbsent' $false
             listenerInspectionProven = Get-VerifierManifestBooleanValue $Context.Server 'ListenerInspectionProven' $false
             listenerAbsent = Get-VerifierManifestBooleanValue $Context.Server 'ListenerAbsent' $false
         }
    }
    return [ordered]@{
        protocol = 'troubleshootjs-verifier-run-v1'
        runId = $Context.RunId
        repositoryIdentity = $Context.RepositoryIdentity
        worktreeRoot = $Context.WorktreeRoot
            runRoot = $Context.RunRoot
        runNamespaceRoot = if ($Context.PSObject.Properties['RunNamespaceRoot']) {
            $Context.RunNamespaceRoot
        } else { $Context.RunRoot }
        evidenceDirectory = $Context.EvidenceDirectory
        evidenceNamespaceRoot = if ($Context.PSObject.Properties['EvidenceNamespaceRoot']) {
            $Context.EvidenceNamespaceRoot
        } else { $Context.RunRoot }
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
    if ($null -eq $Context) {
        Throw-VerifierInfrastructure 'Cannot validate physical verifier resources without a context.'
    }
    [void](Assert-VerifierPhysicalOwnedPath $Context.WorktreeRoot $Context.WorktreeRoot `
        -AllowRoot)
    [void](Assert-VerifierPhysicalOwnedPath $Context.RunNamespaceRoot $Context.RunRoot `
        -ValidateTree)
    [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $Context.ManifestPath)
    [void](Assert-VerifierPhysicalOwnedPath $Context.EvidenceNamespaceRoot $Context.EvidenceDirectory `
        -ValidateTree)
    [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $Context.PortLeaseRoot `
        -ValidateTree)
    if (Test-Path -LiteralPath $Context.RunRoot -PathType Container -ErrorAction Stop) {
        Assert-VerifierNoReparseTree $Context.RunRoot
    }
    foreach ($lease in @($Context.LeaseRecords)) {
        if ($null -eq $lease) { continue }
        if (-not [String]::IsNullOrWhiteSpace([string]$lease.Path) -and
                -not (Assert-VerifierPhysicalOwnedPath $Context.PortLeaseRoot $lease.Path)) {
            Throw-VerifierInfrastructure "Verifier lease path escaped its physical claim namespace: $($lease.Path)"
        }
        if ($lease.PSObject.Properties['ProfilePath'] -and
                -not [String]::IsNullOrWhiteSpace([string]$lease.ProfilePath) -and
                -not (Assert-VerifierPhysicalOwnedPath $Context.RunRoot $lease.ProfilePath `
                    -ValidateTree)) {
            Throw-VerifierInfrastructure "Verifier lease profile escaped its physical run namespace: $($lease.ProfilePath)"
        }
    }
    foreach ($session in @($Context.BrowserSessions)) {
        if ($null -ne $session -and
                -not [String]::IsNullOrWhiteSpace([string]$session.Profile) -and
                -not (Assert-VerifierPhysicalOwnedPath $Context.RunRoot $session.Profile `
                    -ValidateTree)) {
            Throw-VerifierInfrastructure "Verifier browser profile escaped its physical run namespace: $($session.Profile)"
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
    if ($null -eq $Context -or [String]::IsNullOrWhiteSpace([string]$Context.RunRoot)) {
        Throw-VerifierInfrastructure 'Cannot persist a verifier manifest without a run root.'
    }
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

function New-VerifierRunContext([string]$WorktreeRoot, [string]$EvidenceParent = '',
        [string]$RunNamespaceRoot = '') {
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
                 FailNextPostDeleteBeforeFinalState = $false
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

function New-VerifierPortLease($Context, [string]$Kind, [int]$RequestedPort = 0,
        [string]$BrowserPath = '') {
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
                Status = 'leased'; Registered = $false; ClaimName = $claimName
                ClaimState = 'held'; ClaimOwnerPid = [int]$PID
                ClaimOwnerStartTicks = $claimOwnerStartTicks
                BoundProcessId = 0; BoundProcessStartTicks = 0
                 ListenerProcessId = 0; ListenerProcessStartTicks = 0
                 BindValidatedUtc = ''; ReleasedUtc = ''; ClaimMutex = $mutex
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
    if ($null -eq $PreviewRecord -or [int]$PreviewRecord.ProcessId -le 0) { return $false }
    $commandRecord = @($Snapshot | Where-Object {
        [int]$_.ProcessId -eq [int]$PreviewRecord.ProcessId
    } | Select-Object -First 1)
    if ($commandRecord.Count -eq 0 -or
            -not $commandRecord[0].PSObject.Properties['CommandLine'] -or
            [String]::IsNullOrWhiteSpace([string]$commandRecord[0].CommandLine)) { return $false }
    $command = $commandRecord[0].CommandLine
    return (Test-VerifierCommandLinePath $command ([string]$PreviewRecord.Script)) -and
        (Test-VerifierCommandLineSwitch $command '-Port' ([string]$PreviewRecord.Port)) -and
        (Test-VerifierCommandLineSwitch $command '-VerifierRunId' ([string]$PreviewRecord.RunId)) -and
        (Test-VerifierCommandLineSwitch $command '-VerifierNonce' ([string]$PreviewRecord.Nonce))
}

function Test-VerifierListenerBelongsToOwner($OwnerRecord, $Listener,
        [int]$ProcessId, [long]$ProcessStartTicks, $Snapshot) {
    if ([int]$Listener.ProcessId -eq $ProcessId -and
            [long]$Listener.ProcessStartTicks -eq $ProcessStartTicks) {
        if ($null -eq $OwnerRecord) { return $true }
        if ($OwnerRecord.PSObject.Properties['Profile']) {
            return (Test-VerifierProcessIdentity $OwnerRecord $Snapshot)
        }
        if ($OwnerRecord.PSObject.Properties['Script']) {
            return (Test-VerifierPreviewProcessIdentity $OwnerRecord $Snapshot)
        }
        return $false
    }
    if ($null -eq $OwnerRecord -or [int]$OwnerRecord.ProcessId -le 0) { return $false }
    $rootIsOwned = if ($OwnerRecord.PSObject.Properties['Profile']) {
        Test-VerifierProcessIdentity $OwnerRecord $Snapshot
    } elseif ($OwnerRecord.PSObject.Properties['Script']) {
        Test-VerifierPreviewProcessIdentity $OwnerRecord $Snapshot
    } else { $false }
    if (-not $rootIsOwned) { return $false }
    $descendants = @(Get-VerifierDescendantProcessRecords $OwnerRecord $Snapshot)
    $candidate = @($descendants | Where-Object {
        [int]$_.ProcessId -eq [int]$Listener.ProcessId -and
            [long]$_.ProcessStartTicks -eq [long]$Listener.ProcessStartTicks
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

function Set-VerifierLeaseListenerInspection($Lease, $Inspection) {
    if ($null -eq $Lease -or $null -eq $Inspection) { return }
    if ($Lease.PSObject.Properties['ListenerInspectionSuccess']) {
        $Lease.ListenerInspectionSuccess = [bool]$Inspection.Success
    }
    if ($Lease.PSObject.Properties['ListenerInspectionKnown']) {
        $Lease.ListenerInspectionKnown = [bool]$Inspection.Known
    }
    if ($Lease.PSObject.Properties['ListenerHasListeners']) {
        $Lease.ListenerHasListeners = if ($Inspection.PSObject.Properties['HasListeners']) {
            [bool]$Inspection.HasListeners
        } else { $null }
    }
    if ($Lease.PSObject.Properties['ListenerAbsent']) {
        $Lease.ListenerAbsent = if ($Inspection.PSObject.Properties['HasListeners']) {
            [bool](-not [bool]$Inspection.HasListeners)
        } else { $null }
    }
    if ($Lease.PSObject.Properties['ListenerInspectionUtc']) {
        $Lease.ListenerInspectionUtc = Get-VerifierUtcText
    }
}

function Confirm-VerifierPortLeaseBound($Context, $Lease, [int]$ProcessId,
        [long]$ProcessStartTicks, $OwnerRecord = $null) {
    if ($null -eq $Lease -or $Lease.Status -eq 'released') {
        Throw-VerifierInfrastructure 'Cannot validate a missing or released port claim.'
    }
    if ([int]$Lease.ClaimOwnerPid -ne [int]$PID -or
            [long]$Lease.ClaimOwnerStartTicks -ne (Get-VerifierCurrentProcessStartTicks)) {
        Throw-VerifierInfrastructure "Verifier port claim $($Lease.Port) is not held by this run process."
    }
    if ($ProcessId -le 0 -or $ProcessStartTicks -le 0) {
        Throw-VerifierInfrastructure "Owned port $($Lease.Port) did not provide a valid bound process identity."
    }
    $inspection = Get-VerifierLoopbackListenerRecords ([int]$Lease.Port)
    Set-VerifierLeaseListenerInspection $Lease $inspection
    if (-not $inspection.Success -or -not $inspection.Known) {
        Throw-VerifierInfrastructure "Could not positively determine listener state for owned port $($Lease.Port)."
    }
    $listeners = @($inspection.Listeners)
    if (-not $inspection.HasListeners) {
        Throw-VerifierInfrastructure "Owned port $($Lease.Port) has no exact loopback listener; bind was not proven."
    }
    $snapshot = $null
    if ($null -ne $OwnerRecord) {
        try {
            $ownerBrowserPath = if ($OwnerRecord.PSObject.Properties['BrowserPath']) {
                [string]$OwnerRecord.BrowserPath
            } else { '' }
            $ownerProfile = if ($OwnerRecord.PSObject.Properties['Profile']) {
                [string]$OwnerRecord.Profile
            } else { '' }
            $ownerRunId = if ($OwnerRecord.PSObject.Properties['RunId']) {
                [string]$OwnerRecord.RunId
            } else { '' }
            $ownerRepository = if ($OwnerRecord.PSObject.Properties['RepositoryIdentity']) {
                [string]$OwnerRecord.RepositoryIdentity
            } else { '' }
            $ownerPort = if ($OwnerRecord.PSObject.Properties['CdpPort']) {
                [int]$OwnerRecord.CdpPort
            } elseif ($OwnerRecord.PSObject.Properties['Port']) {
                [int]$OwnerRecord.Port
            } else { 0 }
            $ownerScript = if ($OwnerRecord.PSObject.Properties['Script']) {
                [string]$OwnerRecord.Script
            } else { '' }
            $ownerNonce = if ($OwnerRecord.PSObject.Properties['Nonce']) {
                [string]$OwnerRecord.Nonce
            } else { '' }
            $snapshot = @(Get-VerifierBrowserOwnershipSnapshot `
                $ownerBrowserPath $ownerProfile $ownerRunId $ownerRepository `
                $ownerPort $ownerScript $ownerNonce)
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure "Could not inspect the owned process for port $($Lease.Port): $(Get-VerifierErrorMessage $_)"
        }
    }
    foreach ($listener in $listeners) {
        if (-not (Test-VerifierListenerBelongsToOwner $OwnerRecord $listener `
                $ProcessId $ProcessStartTicks $snapshot)) {
            Throw-VerifierInfrastructure ("Loopback port $($Lease.Port) is listening under foreign " +
                "PID $($listener.ProcessId)/start $($listener.ProcessStartTicks), not the recorded owner.")
        }
    }
    $Lease.BoundProcessId = $ProcessId
    $Lease.BoundProcessStartTicks = $ProcessStartTicks
    $Lease.ListenerProcessId = [int]$listeners[0].ProcessId
    $Lease.ListenerProcessStartTicks = [long]$listeners[0].ProcessStartTicks
    $Lease.BindValidatedUtc = Get-VerifierUtcText
    $Lease.ClaimState = 'bound'
    $Lease.Status = 'bound'
    if ($Lease.PSObject.Properties['ProcessProofRequired']) {
        $Lease.ProcessProofRequired = $true
    }
    Write-VerifierManifest $Context
}

function Release-VerifierPortLease($Context, $Lease) {
    if ($null -eq $Lease) { return }
    $claimRemains = $false
    if ($Lease.PSObject.Properties['Path'] -and
            -not [String]::IsNullOrWhiteSpace([string]$Lease.Path)) {
        try {
            $claimRemains = Test-Path -LiteralPath $Lease.Path -PathType Leaf -ErrorAction Stop
        } catch {
            $claimRemains = $true
        }
    }
    $liveHandleRemains = ($Lease.PSObject.Properties['ClaimMutex'] -and
        $null -ne $Lease.ClaimMutex)
    $releaseState = if ($Lease.PSObject.Properties['ReleaseState']) {
        [string]$Lease.ReleaseState
    } else { 'active' }
    # Once the manifest durably records that this exact lease's OS mutex was
    # released, a later run may legitimately have rebound the same port. A
    # recovery retry may therefore skip only the *current listener absence*
    # check after exact claim validation; it must never skip claim identity or
    # profile ownership checks and must never remove a different claim.
    $durableOsRelease = (-not $liveHandleRemains -and
        $Lease.PSObject.Properties['MutexReleased'] -and
        [bool]$Lease.MutexReleased -and
        $releaseState -in @('os-released', 'complete', 'claim-delete-failed'))

    # `complete` is the durable pre-delete/final journal state.  A claim may
    # still be present when the process was interrupted between the journal
    # write and claim deletion; it is still safe to finish only after exact
    # claim validation below.  A missing claim with this state is already
    # complete, and recovery only needs to persist the terminal ClaimState.
    if ($Lease.Status -eq 'released' -and
            $Lease.PSObject.Properties['ReleaseState'] -and
            [string]$Lease.ReleaseState -eq 'complete') {
        if ($liveHandleRemains) {
            Throw-VerifierInfrastructure "Port $($Lease.Port) is marked complete but still retains a live claim handle."
        }
        if (-not $claimRemains) {
            if (-not $Lease.PSObject.Properties['ClaimState'] -or
                    [string]$Lease.ClaimState -ne 'released' -or
                    -not $Lease.PSObject.Properties['ReleaseJournalState'] -or
                    [string]$Lease.ReleaseJournalState -ne 'complete') {
                try {
                    $Lease.Status = 'released'
                    $Lease.ClaimState = 'released'
                    $Lease.MutexReleased = $true
                    if ($Lease.PSObject.Properties['ReleaseJournalState']) {
                        $Lease.ReleaseJournalState = 'complete'
                    }
                    $Context.ManifestWritePhase = 'lease-post-delete'
                    Write-VerifierManifest $Context
                } catch {
                    if (Test-VerifierInfrastructureError $_) { throw }
                    Throw-VerifierInfrastructure (Get-VerifierErrorMessage $_)
                } finally {
                    $Context.ManifestWritePhase = ''
                }
            }
            if ($script:VerifierHeldPortClaims.ContainsKey([string]$Lease.ClaimName) -and
                    [string]$script:VerifierHeldPortClaims[[string]$Lease.ClaimName] -eq [string]$Context.RunId) {
                [void]$script:VerifierHeldPortClaims.Remove([string]$Lease.ClaimName)
            }
            return
        }
        # A durable claim left behind despite the complete marker is repaired
        # through the exact claim validation path below; never delete by path
        # or PID alone.
    }
    try {
        if ($Lease.PSObject.Properties['ReleaseBlocked'] -and [bool]$Lease.ReleaseBlocked) {
            Throw-VerifierInfrastructure "Port $($Lease.Port) release is blocked because ownership remains uncertain: $($Lease.ReleaseBlockReason)"
        }
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
        if ([string]$claim.protocol -ne 'troubleshootjs-verifier-port-claim-v1' -or
                [string]$claim.runId -ne [string]$Context.RunId -or
                [string]$claim.repositoryIdentity -ne [string]$Context.RepositoryIdentity -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string]$claim.worktreeRoot) `
                    ([string]$Context.WorktreeRoot)) -or
                 [string]$claim.kind -ne [string]$Lease.Kind -or
                 [string]$claim.leaseId -ne [string]$Lease.LeaseId -or
                 -not (Test-VerifierCanonicalWindowsPathValue ([string]$claim.path) `
                     ([string]$Lease.Path)) -or
                 [int]$claim.port -ne [int]$Lease.Port -or
                [string]$claim.mutexName -ne [string]$Lease.ClaimName -or
                [int]$claim.ownerPid -ne [int]$Lease.ClaimOwnerPid -or
                [long]$claim.ownerStartTicks -ne [long]$Lease.ClaimOwnerStartTicks) {
            Throw-VerifierInfrastructure "Port claim '$($Lease.Path)' is owned by another run or claim identity."
        }
        # Before the durable release marker, the current process must still
        # own the mutex/claim.  Once the marker is persisted, recovery may
        # finish an exact claim after the original process has gone away.
        if ($releaseState -notin @('complete', 'manifested', 'os-released', 'claim-delete-failed') -and
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
            $inspection = Get-VerifierLoopbackListenerRecords ([int]$Lease.Port)
            Set-VerifierLeaseListenerInspection $Lease $inspection
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
            if ($null -eq $Lease.ClaimMutex -and -not [bool]$Lease.MutexReleased) {
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

            if (-not [bool]$Lease.MutexReleased) {
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
                $Lease.ClaimMutex = $null
            }
            $releaseState = [string]$Lease.ReleaseState
        } elseif ($releaseState -notin @('os-released', 'claim-delete-failed', 'manifested')) {
            if ($releaseState -ne 'complete') {
                Throw-VerifierInfrastructure "Port $($Lease.Port) has an unrecognized durable release state '$releaseState'."
            }
        }

        if ($null -ne $Lease.ClaimMutex -and [bool]$Lease.MutexReleased) {
            if ($Context.TestHooks.PSObject.Properties['FailNextLeaseMutexDispose'] -and
                    [bool]$Context.TestHooks.FailNextLeaseMutexDispose) {
                $Context.TestHooks.FailNextLeaseMutexDispose = $false
                Throw-VerifierInfrastructure 'Injected port-lease mutex-dispose failure.'
            }
            try { $Lease.ClaimMutex.Dispose() } catch {
                Throw-VerifierInfrastructure "Could not dispose the owned port $($Lease.Port) claim handle: $(Get-VerifierErrorMessage $_)"
            }
            $Lease.ClaimMutex = $null
        }

        # Persist a durable pre-delete tombstone.  `complete` means that the
        # mutex is released and all listener/profile/claim identity checks
        # passed, while ClaimState=delete-pending means the exact claim still
        # has to be removed.  A crash in the following window is therefore
        # recoverable without contradictory ownership state.
        $oldStatus = [string]$Lease.Status
        $oldClaimState = [string]$Lease.ClaimState
        $oldReleaseState = [string]$Lease.ReleaseState
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
            $Lease.ClaimState = 'delete-pending'
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

function New-VerifierBrowserLease($Context, [string]$RouteName, [string]$BrowserPath = '') {
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
    try {
        [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $routeRoot)
        New-Item -ItemType Directory -Path $profile -ErrorAction Stop | Out-Null
        [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $routeRoot -ValidateTree)
        [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $profile -ValidateTree)
    } catch {
        Release-VerifierPortLease $Context $lease
        Throw-VerifierInfrastructure "Could not create isolated browser profile: $(Get-VerifierErrorMessage $_)"
    }
    $lease.ProfilePath = Get-VerifierFullPath $profile
    $lease.OwnerType = 'browser'
    $lease.BrowserPath = [string]$BrowserPath
    $browserLeaseSessionRecord = [pscustomobject]@{
        RunId = $Context.RunId; RepositoryIdentity = $Context.RepositoryIdentity
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
    [void]$Context.BrowserSessions.Add($browserLeaseSessionRecord)
    Write-VerifierManifest $Context
    return $browserLeaseSessionRecord
}

function Select-VerifierRelevantProcessRecords($Snapshot, [string]$BrowserPath = '',
        [string]$Profile = '', [string]$RunId = '', [string]$RepositoryIdentity = '',
        [int]$Port = 0, [string]$Script = '', [string]$Nonce = '') {
    $executableName = ''
    if (-not [String]::IsNullOrWhiteSpace($BrowserPath)) {
        try { $executableName = [IO.Path]::GetFileName($BrowserPath) } catch {
            Throw-VerifierInfrastructure "Could not derive the configured browser executable name: $(Get-VerifierErrorMessage $_)"
        }
    }
    $selected = New-Object Collections.ArrayList
    $seen = @{}
    foreach ($item in @($Snapshot)) {
        # PID 0/System Idle and records with no usable PID are irrelevant until
        # a readable exact marker identifies them as a browser candidate. This
        # keeps transient unrelated WMI records from invalidating a healthy run.
        if ($null -eq $item -or -not $item.PSObject.Properties['ProcessId']) { continue }
        $processId = 0
        try { $processId = [int]$item.ProcessId } catch { continue }
        if ($processId -le 0) { continue }
        $name = if ($item.PSObject.Properties['Name']) { [string]$item.Name } else { '' }
        $command = if ($item.PSObject.Properties['CommandLine'] -and
                $null -ne $item.CommandLine) { [string]$item.CommandLine } else { '' }
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
        if (-not ($nameMatch -or $profileMatch -or $identityMatch)) { continue }
        if (-not $item.PSObject.Properties['CommandLine'] -or
                [String]::IsNullOrWhiteSpace($command)) {
            Throw-VerifierInfrastructure "A relevant browser candidate PID $processId had an inaccessible command line."
        }
        if (-not $item.PSObject.Properties['ParentProcessId'] -or
                [String]::IsNullOrWhiteSpace([string]$item.ParentProcessId)) {
            Throw-VerifierInfrastructure "A relevant browser candidate PID $processId had an inaccessible parent PID."
        }
        $parentProcessId = 0
        try { $parentProcessId = [int]$item.ParentProcessId } catch {
            Throw-VerifierInfrastructure "A relevant browser candidate PID $processId had an invalid parent PID."
        }
        if ($parentProcessId -lt 0) {
            Throw-VerifierInfrastructure "A relevant browser candidate PID $processId had an invalid parent identity."
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
        $processId = 0
        try { $processId = [int]$item.ProcessId } catch {
            Throw-VerifierInfrastructure "The $Purpose process snapshot contained an invalid PID record."
        }
        if ($processId -le 0 -or [int]$item.ParentProcessId -lt 0 -or
                [String]::IsNullOrWhiteSpace([string]$item.CommandLine)) {
            Throw-VerifierInfrastructure "The $Purpose process snapshot contained incomplete identity data for PID $processId."
        }
        if ($seen.ContainsKey($processId)) {
            Throw-VerifierInfrastructure "The $Purpose process snapshot contained duplicate PID $processId."
        }
        $seen[$processId] = $true
        $process = Get-VerifierProcessById $processId
        if ($null -eq $process) {
            Throw-VerifierInfrastructure "The $Purpose process snapshot contained PID $processId that could not be inspected."
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

function Get-VerifierBrowserProcessSnapshot([string]$BrowserPath = '', [string]$Profile = '',
        [string]$RunId = '', [string]$RepositoryIdentity = '', [int]$Port = 0,
        [string]$Script = '', [string]$Nonce = '') {
    try {
        $snapshot = @(Get-CimInstance Win32_Process -ErrorAction Stop)
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
        [string]$RunId = '', [string]$RepositoryIdentity = '', [int]$Port = 0,
        [string]$Script = '', [string]$Nonce = '') {
    try {
        # The relevant snapshot remains the admission/identity check for
        # unrelated OS records. Once an owned root is being cleaned, however,
        # PPID traversal must use the complete WMI candidate set so a helper
        # with a different executable name cannot disappear from the graph.
        $snapshot = @(Get-CimInstance Win32_Process -ErrorAction Stop)
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

function Get-VerifierProcessById([int]$ProcessId) {
    return Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
}

function Get-VerifierCurrentProcessRecordById([int]$ProcessId) {
    if ($ProcessId -le 0) {
        Throw-VerifierInfrastructure 'Current process inspection requires a positive PID.'
    }
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
            $wmiAfterMiss = @(Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction Stop)
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
            $wmiAfterExit = @(Get-CimInstance Win32_Process `
                -Filter "ProcessId = $ProcessId" -ErrorAction Stop)
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
        $records = @(Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction Stop)
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
    $parentId = 0
    try { $parentId = [int]$currentWmiRecord.ParentProcessId } catch {
        Throw-VerifierInfrastructure "Current process PID $ProcessId had an invalid parent identity."
    }
    if ([int]$currentWmiRecord.ProcessId -ne $ProcessId -or $parentId -le 0 -or
            [String]::IsNullOrWhiteSpace([string]$currentWmiRecord.CommandLine)) {
        Throw-VerifierInfrastructure "Current process PID $ProcessId had incomplete command, parent, or PID identity."
    }
    return [pscustomobject]@{
        Process = $process
        ProcessId = [int]$currentWmiRecord.ProcessId
        ParentProcessId = $parentId
        ProcessStartTicks = [long]$startTicks
        CommandLine = [string]$currentWmiRecord.CommandLine
        Name = if ($currentWmiRecord.PSObject.Properties['Name']) { [string]$currentWmiRecord.Name } else { '' }
        ExecutablePath = if ($currentWmiRecord.PSObject.Properties['ExecutablePath']) {
            [string]$currentWmiRecord.ExecutablePath
        } else { '' }
    }
}

function Get-VerifierCurrentParentStartTicks([int]$ParentProcessId) {
    if ($ParentProcessId -le 0) {
        Throw-VerifierInfrastructure 'Parent-process start proof requires a positive parent PID.'
    }
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
        $parentRecords = @(Get-CimInstance Win32_Process `
            -Filter "ProcessId = $ParentProcessId" -ErrorAction Stop)
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure "Could not query parent process PID ${ParentProcessId}: $(Get-VerifierErrorMessage $_)"
    }
    if ($parentRecords.Count -ne 1 -or
            -not $parentRecords[0].PSObject.Properties['ProcessId'] -or
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
        [string]$ExpectedCommandLine = '', [string]$Script = '', [int]$Port = 0,
        [string]$RunId = '', [string]$Nonce = '', [string]$Profile = '') {
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
        [string]$ExpectedCommandLine = '', [int]$Port = 0, [string]$Script = '',
        [string]$RunId = '', [string]$Nonce = '', [string]$Profile = '') {
    if ($null -eq $Recorded -or -not $Recorded.PSObject.Properties['ProcessId'] -or
            [int]$Recorded.ProcessId -le 0) {
        return [pscustomobject]@{ QueryProven = $true; Absent = $true; Replaced = $false; Current = $null }
    }
    if (-not $Recorded.PSObject.Properties['ProcessStartTicks'] -or
            [long]$Recorded.ProcessStartTicks -le 0) {
        Throw-VerifierInfrastructure "$Role recorded a PID without a positive start identity."
    }
    $current = Get-VerifierCurrentProcessRecordById ([int]$Recorded.ProcessId)
    if ($null -eq $current) {
        return [pscustomobject]@{ QueryProven = $true; Absent = $true; Replaced = $false; Current = $null }
    }
    if ($Recorded.PSObject.Properties['ParentProcessStartTicks'] -and
            [long]$Recorded.ParentProcessStartTicks -gt 0) {
        $current | Add-Member -NotePropertyName ParentProcessStartTicks `
            -NotePropertyValue (Get-VerifierCurrentParentStartTicks ([int]$current.ParentProcessId)) -Force
    }
    if ([long]$current.ProcessStartTicks -ne [long]$Recorded.ProcessStartTicks) {
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

function Confirm-VerifierReleasedListener([int]$Port, [int]$ExpectedProcessId = 0,
        [long]$ExpectedStartTicks = 0, [string]$ExpectedCommandLine = '',
        [int]$ExpectedParentProcessId = 0, [string]$Script = '',
        [string]$RunId = '', [string]$Nonce = '', [string]$Profile = '',
        [long]$ExpectedParentProcessStartTicks = 0) {
    if ($Port -le 0 -or $Port -gt 65535) {
        Throw-VerifierInfrastructure 'Released listener proof requires a valid port.'
    }
    if ($ExpectedProcessId -gt 0 -and $ExpectedStartTicks -le 0) {
        Throw-VerifierInfrastructure "Released listener proof for port $Port omitted the expected process start identity."
    }
    $inspection = Get-VerifierLoopbackListenerRecords $Port
    if ($null -eq $inspection -or -not [bool]$inspection.Success -or
            -not [bool]$inspection.Known) {
        Throw-VerifierInfrastructure "Loopback listener inspection for released port $Port was not positively proven."
    }
    $replacementObserved = $false
    foreach ($listener in @($inspection.Listeners)) {
        if ($null -eq $listener -or [int]$listener.Port -ne $Port -or
                [int]$listener.ProcessId -le 0 -or
                [long]$listener.ProcessStartTicks -le 0) {
            Throw-VerifierInfrastructure "Loopback listener inspection for port $Port returned an incomplete listener identity."
        }
        $current = Get-VerifierCurrentProcessRecordById ([int]$listener.ProcessId)
        if ($null -eq $current -or
                [long]$current.ProcessStartTicks -ne [long]$listener.ProcessStartTicks) {
            Throw-VerifierInfrastructure "Loopback listener process identity for port $Port changed during independent proof."
        }
        if ($ExpectedProcessId -le 0) {
            Throw-VerifierInfrastructure "Port $Port has a listener but the completed ledger recorded no bound owner identity."
        }
        if ([int]$current.ProcessId -eq $ExpectedProcessId -and
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
        if ([int]$current.ProcessId -eq $ExpectedProcessId) {
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
        PortReused = ([bool]$inspection.HasListeners)
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
        [string]$RepositoryIdentity = '', [int]$Port = 0) {
    if ([String]::IsNullOrWhiteSpace($Profile)) {
        Throw-VerifierInfrastructure 'Cannot prove ownership of an empty browser profile path.'
    }
    $snapshot = @(Get-VerifierBrowserProcessSnapshot $BrowserPath $Profile $RunId `
        $RepositoryIdentity $Port)
    Assert-VerifierProfileSnapshotQuiescent $snapshot $Profile
}

function Test-VerifierPortInUse([int]$Port) {
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

function Test-VerifierProcessIdentity($ProcessRecord, $Snapshot = $null,
        [string]$ExpectedBrowserPath = '') {
    if ($null -eq $ProcessRecord -or [int]$ProcessRecord.ProcessId -le 0) { return $false }
    $process = Get-VerifierProcessById ([int]$ProcessRecord.ProcessId)
    if ($null -eq $process) { return $false }
    try {
        if ((Get-VerifierProcessStartTicks $process) -ne [long]$ProcessRecord.ProcessStartTicks) {
            return $false
        }
    } catch { return $false }
    if ($null -eq $Snapshot) {
        try {
            $Snapshot = @(Get-CimInstance Win32_Process -Filter "ProcessId = $($ProcessRecord.ProcessId)" -ErrorAction Stop)
        } catch { return $false }
    }
    $command = $Snapshot | Where-Object { [int]$_.ProcessId -eq [int]$ProcessRecord.ProcessId } |
        Select-Object -First 1
    if ($null -eq $command -or
            -not $command.PSObject.Properties['CommandLine'] -or
            [String]::IsNullOrWhiteSpace([string]$command.CommandLine)) { return $false }
    $line = $command.CommandLine
    if ($ProcessRecord.PSObject.Properties['ProcessParentProcessId'] -and
            [int]$ProcessRecord.ProcessParentProcessId -gt 0 -and
            (-not $command.PSObject.Properties['ParentProcessId'] -or
             [int]$command.ParentProcessId -ne [int]$ProcessRecord.ProcessParentProcessId)) {
        return $false
    }
    if ($ProcessRecord.PSObject.Properties['ProcessParentProcessStartTicks'] -and
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
    } elseif ($ProcessRecord.PSObject.Properties['BrowserPath']) {
        [string]$ProcessRecord.BrowserPath
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
    if ([int]$Recorded.ProcessId -le 0 -or
            [int]$Current.ProcessId -ne [int]$Recorded.ProcessId -or
            [int]$Current.ParentProcessId -ne [int]$Recorded.ParentProcessId -or
            [long]$Recorded.ProcessStartTicks -le 0 -or
            [long]$Current.ProcessStartTicks -ne [long]$Recorded.ProcessStartTicks -or
            [String]::IsNullOrWhiteSpace([string]$Recorded.CommandLine) -or
            [String]::IsNullOrWhiteSpace([string]$Current.CommandLine)) {
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

function Get-VerifierCurrentOwnedProcess($OwnerRecord, $Recorded, [switch]$Root) {
    if ($null -eq $Recorded -or [int]$Recorded.ProcessId -le 0) {
        Throw-VerifierInfrastructure 'Cannot revalidate an owned process without a positive PID.'
    }
    $pidValue = [int]$Recorded.ProcessId
    $process = Get-VerifierProcessById $pidValue
    if ($null -eq $process) {
        Throw-VerifierInfrastructure "Owned process PID $pidValue disappeared before termination proof."
    }
    $currentStart = Get-VerifierProcessStartTicks $process
    $records = @()
    try {
        $records = @(Get-CimInstance Win32_Process -Filter "ProcessId = $pidValue" -ErrorAction Stop)
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure "Could not re-query current process identity for PID ${pidValue}: $(Get-VerifierErrorMessage $_)"
    }
    if ($records.Count -ne 1) {
        Throw-VerifierInfrastructure "Current process identity for PID $pidValue was missing or ambiguous before termination."
    }
    $currentWmi = $records[0]
    if (-not $currentWmi.PSObject.Properties['ProcessId'] -or
            -not $currentWmi.PSObject.Properties['ParentProcessId'] -or
            -not $currentWmi.PSObject.Properties['CommandLine'] -or
            [String]::IsNullOrWhiteSpace([string]$currentWmi.CommandLine)) {
        Throw-VerifierInfrastructure "Current process record for PID $pidValue was incomplete before termination."
    }
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
        Throw-VerifierInfrastructure "Owned process PID $pidValue disappeared after current identity validation."
    }
    try { $verifiedProcess.Refresh() } catch {
        Throw-VerifierInfrastructure "Could not refresh owned process PID $pidValue immediately before termination: $(Get-VerifierErrorMessage $_)"
    }
    if ([bool]$verifiedProcess.HasExited -or
            (Get-VerifierProcessStartTicks $verifiedProcess) -ne [long]$current.ProcessStartTicks) {
        Throw-VerifierInfrastructure "Owned process PID $pidValue changed or exited after current identity validation."
    }
    return [pscustomobject]@{ Process = $verifiedProcess; Record = $current }
}

function Stop-VerifierVerifiedProcessExactly($Process, [long]$ExpectedStartTicks,
        [int]$WaitMilliseconds = 5000, $ExpectedRecord = $null) {
    if ($null -eq $Process -or [int]$Process.Id -le 0 -or
            $ExpectedStartTicks -le 0 -or $WaitMilliseconds -lt 1) {
        Throw-VerifierInfrastructure 'Exact process termination requires a process object, positive start identity, and positive wait bound.'
    }
    $pidValue = [int]$Process.Id
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
            $currentWmiRecords = @(Get-CimInstance Win32_Process `
                -Filter "ProcessId = $pidValue" -ErrorAction Stop)
            if ($currentWmiRecords.Count -ne 1 -or
                    -not $currentWmiRecords[0].PSObject.Properties['ProcessId'] -or
                    -not $currentWmiRecords[0].PSObject.Properties['ParentProcessId'] -or
                    -not $currentWmiRecords[0].PSObject.Properties['CommandLine'] -or
                    [String]::IsNullOrWhiteSpace([string]$currentWmiRecords[0].CommandLine)) {
                Throw-VerifierInfrastructure "Process PID $pidValue current identity was incomplete at the termination boundary."
            }
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

function Get-VerifierCurrentProcessIdentity([int]$ProcessId,
        [long]$ExpectedStartTicks = 0, [int]$ExpectedParentProcessId = 0,
        [string]$ExpectedCommandLine = '', [string]$Script = '',
        [int]$Port = 0, [string]$RunId = '', [string]$Nonce = '',
        [long]$ExpectedParentProcessStartTicks = 0,
        [string]$ExpectedBrowserPath = '') {
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
        $records = @(Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction Stop)
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
    if ([int]$wmi.ProcessId -ne $ProcessId -or
            [int]$wmi.ParentProcessId -le 0 -or
            [String]::IsNullOrWhiteSpace([string]$wmi.CommandLine)) {
        Throw-VerifierInfrastructure "Current process PID $ProcessId identity was incomplete."
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
        Name = if ($wmi.PSObject.Properties['Name']) { [string]$wmi.Name } else { '' }
        ExecutablePath = if ($wmi.PSObject.Properties['ExecutablePath']) {
            [string]$wmi.ExecutablePath
        } else { '' }
    }
    if (-not [String]::IsNullOrWhiteSpace($ExpectedBrowserPath) -and
            -not (Test-VerifierConfiguredExecutableIdentity $ExpectedBrowserPath $current `
                -RequireExecutablePath)) {
        Throw-VerifierInfrastructure "Process PID $ProcessId does not match the configured browser executable identity."
    }
    return [pscustomobject]@{ Process = $verifiedProcess; Record = $current }
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
        return (Test-VerifierConfiguredExecutableIdentity $expectedPath `
            $CandidateChildRecord -RequireExecutablePath)
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
    if ($null -eq $rootOwner -or $null -eq $candidateChild -or
            [int]$candidateChild.ProcessId -le 0 -or
            [int]$candidateChild.ParentProcessId -le 0 -or
            [long]$candidateChild.ProcessStartTicks -le 0 -or
            [String]::IsNullOrWhiteSpace([string]$candidateChild.CommandLine)) {
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
    if ($null -eq $Snapshot) {
        Throw-VerifierInfrastructure 'Cannot inspect browser descendants without a complete process snapshot.'
    }
    $byParent = @{}
    foreach ($item in @($Snapshot)) {
        if ($null -eq $item -or -not $item.PSObject.Properties['ParentProcessId']) {
            # A record without a usable parent cannot be associated with an
            # owned root from this snapshot. Exact child queries below remain
            # authoritative for every owned parent and fail closed if they
            # return such an incomplete record.
            continue
        }
        $parent = 0
        try { $parent = [int]$item.ParentProcessId } catch { continue }
        if ($parent -le 0) { continue }
        if (-not $byParent.ContainsKey($parent)) {
            $byParent[$parent] = New-Object Collections.ArrayList
        }
        # Keep even a malformed PID/command record in the parent bucket. If
        # that bucket is reachable from an owned PID, it is not unrelated
        # data anymore and must become an infrastructure failure rather than
        # silently disappearing from the ownership proof.
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
                if ($null -eq $child -or
                        -not $child.PSObject.Properties['ProcessId'] -or
                        -not $child.PSObject.Properties['ParentProcessId'] -or
                        -not $child.PSObject.Properties['CommandLine'] -or
                        [String]::IsNullOrWhiteSpace([string]$child.CommandLine)) {
                    Throw-VerifierInfrastructure "Browser descendant record under owned PID $parent was incomplete."
                }
                $childId = 0
                try { $childId = [int]$child.ProcessId } catch {
                    Throw-VerifierInfrastructure "Browser descendant under owned PID $parent had an invalid PID."
                }
                if ($childId -le 0 -or [int]$child.ParentProcessId -ne $parent) {
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
                $queriedChildren = @(Get-CimInstance Win32_Process `
                    -Filter "ParentProcessId = $parent" -ErrorAction Stop)
            } catch {
                if (Test-VerifierInfrastructureError $_) { throw }
                Throw-VerifierInfrastructure ("Could not query descendants of owned PID " +
                    [string]$parent + ': ' + (Get-VerifierErrorMessage $_))
            }
            foreach ($child in $queriedChildren) {
                if ($null -eq $child -or
                        -not $child.PSObject.Properties['ProcessId'] -or
                        -not $child.PSObject.Properties['ParentProcessId'] -or
                        -not $child.PSObject.Properties['CommandLine'] -or
                        [String]::IsNullOrWhiteSpace([string]$child.CommandLine)) {
                    Throw-VerifierInfrastructure "Exact browser descendant query under owned PID $parent returned an incomplete record."
                }
                $childId = 0
                try { $childId = [int]$child.ProcessId } catch {
                    Throw-VerifierInfrastructure "Exact browser descendant query under owned PID $parent returned an invalid PID."
                }
                if ($childId -le 0 -or [int]$child.ParentProcessId -ne $parent) {
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

function Get-VerifierDescendantProcessRecords($RootOwnerRecord, $Snapshot) {
    # The full snapshot is supplemented with an exact WMI child query for each
    # owned PID. This candidate expansion keeps a differently named helper in
    # the graph even if a broad snapshot was filtered or incomplete.
    # Preserve the root owner under a name that cannot collide with a child
    # local in PowerShell's case-insensitive variable scope.
    $rootOwner = $RootOwnerRecord
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
        [void](Get-VerifierCurrentOwnedProcess $rootOwner $ownerRootRecord -Root)
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
        # Candidate discovery itself must carry the positive executable proof;
        # the current-process requery below repeats the same check immediately
        # before any possible stop. This prevents a blank/inaccessible WMI
        # ExecutablePath from being treated as an admissible markerless Edge
        # helper merely because its name or ancestry looks plausible.
        if (-not (Test-VerifierDescendantExecutableIdentity $rootOwner `
                $childIdentityRecord)) {
            Throw-VerifierInfrastructure "Browser descendant PID $id omitted or mismatched the configured executable identity."
        }
        $childProcess = Get-VerifierProcessById $id
        if ($null -eq $childProcess) {
            Throw-VerifierInfrastructure "Could not inspect browser descendant PID $id during ownership cleanup."
        }
        $childIdentityRecord.ProcessStartTicks = Get-VerifierProcessStartTicks $childProcess
        if ([long]$childIdentityRecord.ProcessStartTicks -le 0) {
            Throw-VerifierInfrastructure "Browser descendant PID $id has unknown start identity."
        }
        # `$rootOwner` is the root owner and must remain untouched. PowerShell
        # variable names are case-insensitive, so a child local can never
        # replace it and make the child validate against itself.
        $verified = Get-VerifierCurrentOwnedProcess $rootOwner $childIdentityRecord
        $verified.Record | Add-Member -NotePropertyName ParentProcessStartTicks `
            -NotePropertyValue ([long]$currentParent.ProcessStartTicks) -Force
        $verified.Record | Add-Member -NotePropertyName VerifierDepth `
            -NotePropertyValue ([int]$childIdentityRecord.VerifierDepth) -Force
        $verified.Record | Add-Member -NotePropertyName Process -NotePropertyValue $verified.Process
        $verifiedStartByPid[$id] = [long]$verified.Record.ProcessStartTicks
        $verifiedRecordByPid[$id] = $verified.Record
        [void]$result.Add($verified.Record)
    }
    return @($result)
}

function Remove-VerifierBrowserProfile($Context, $ProfileRecord) {
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
        $connectTask = $socket.ConnectAsync($TargetUri, $timeout.Token)
        if (-not $connectTask.Wait($remainingMilliseconds)) {
            Throw-VerifierInfrastructure "CDP WebSocket handshake exceeded the route deadline of $remainingMilliseconds milliseconds."
        }
        $connectTask.GetAwaiter().GetResult()
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
    $rootPid = [int]$OwnerRoot.ProcessId
    if ($rootPid -le 0) {
        Throw-VerifierInfrastructure 'Browser cleanup cannot validate a root without a positive PID.'
    }
    $rootCandidates = @($Snapshot | Where-Object {
        $null -ne $_ -and $_.PSObject.Properties['ProcessId'] -and
            [int]$_.ProcessId -eq $rootPid
    })
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
        if ($null -eq $knownDescendant -or
                -not $knownDescendant.PSObject.Properties['ProcessId'] -or
                -not $knownDescendant.PSObject.Properties['ProcessStartTicks']) {
            Throw-VerifierInfrastructure 'The retained browser descendant graph contained an incomplete process record.'
        }
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
        if ($null -eq $candidate -or
                -not $candidate.PSObject.Properties['ParentProcessId']) {
            continue
        }
        $candidateParent = 0
        try { $candidateParent = [int]$candidate.ParentProcessId } catch {
            continue
        }
        if (-not $ownedPidSet.ContainsKey($candidateParent)) { continue }
        if (-not $candidate.PSObject.Properties['ProcessId'] -or
                -not $candidate.PSObject.Properties['CommandLine'] -or
                [String]::IsNullOrWhiteSpace([string]$candidate.CommandLine)) {
            Throw-VerifierInfrastructure "A residual browser descendant under owned PID $candidateParent had incomplete identity data."
        }
        Throw-VerifierInfrastructure "A late or unknown browser descendant PID $($candidate.ProcessId) remained under owned PID $candidateParent after root termination."
    }

    # Re-query each previously owned parent after root termination. This closes
    # the window where a same-executable markerless helper appears after the
    # last broad snapshot but before the root is stopped.
    foreach ($ownedParentPid in @($ownedPidSet.Keys)) {
        $lateChildren = @()
        try {
            $lateChildren = @(Get-CimInstance Win32_Process `
                -Filter "ParentProcessId = $ownedParentPid" -ErrorAction Stop)
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure ("Could not re-query exact children of owned PID " +
                [string]$ownedParentPid + ': ' + (Get-VerifierErrorMessage $_))
        }
        foreach ($lateChild in $lateChildren) {
            if ($null -eq $lateChild -or
                    -not $lateChild.PSObject.Properties['ProcessId'] -or
                    -not $lateChild.PSObject.Properties['ParentProcessId'] -or
                    -not $lateChild.PSObject.Properties['CommandLine'] -or
                    [String]::IsNullOrWhiteSpace([string]$lateChild.CommandLine)) {
                Throw-VerifierInfrastructure "Exact post-stop child inspection under owned PID $ownedParentPid was incomplete."
            }
            $lateChildPid = 0
            try { $lateChildPid = [int]$lateChild.ProcessId } catch {
                Throw-VerifierInfrastructure "Exact post-stop child inspection under owned PID $ownedParentPid returned an invalid PID."
            }
            if ($lateChildPid -le 0 -or
                    [int]$lateChild.ParentProcessId -ne [int]$ownedParentPid) {
                Throw-VerifierInfrastructure "Exact post-stop child inspection under owned PID $ownedParentPid returned an invalid parent identity."
            }
            Throw-VerifierInfrastructure "A late or unknown browser descendant PID $lateChildPid was found by exact post-stop parent inspection."
        }
    }
}

function Stop-VerifierBrowserProcessTreeToFixedPoint($Context, $OwnerRoot,
        [int]$DrainMilliseconds = 15000) {
    if ($null -eq $Context -or $null -eq $OwnerRoot -or
            $DrainMilliseconds -lt 1) {
        Throw-VerifierInfrastructure 'Browser cleanup requires a bounded root-anchored process drain.'
    }
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
        $children = @(Get-VerifierDescendantProcessRecords $OwnerRoot $snapshot $true)

        foreach ($child in $children) {
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
                # The discovery object is never used as the termination
                # target. Re-query the exact current object and all identity
                # fields immediately before stopping it.
                $verifiedChild = Get-VerifierCurrentOwnedProcess $OwnerRoot $childToStop
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

function Complete-VerifierBrowserSession($Context, $SessionRecord) {
    if ($null -eq $SessionRecord) { return }
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
        [void](Assert-VerifierPhysicalOwnedPath $sessionRoot $sessionProfile -ValidateTree)
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
            $drainResult = Stop-VerifierBrowserProcessTreeToFixedPoint `
                $Context $ownerRoot 15000
            $processTerminationProven = [bool]$drainResult.ProcessTerminationProven
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
        # Release performs its own fail-closed profile/process and listener
        # inspection while the profile still exists. Only after the claim is
        # safely released may the owned profile be deleted; an inspection
        # failure therefore always preserves the profile and evidence.
        $SessionRecord.Lease.ProcessTerminationProven = [bool]$processTerminationProven
        $SessionRecord.Lease.ProcessAbsent = [bool]$processTerminationProven
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

function New-VerifierBrowserSession($Context, [string]$RouteName, [string]$Url,
        [string]$BrowserPath, [int]$TimeoutSeconds) {
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
        $browserSessionRecord.ProcessId = [int]$browser.Id
        $browserSessionRecord.ProcessStartTicks = Get-VerifierProcessStartTicks $browser
        $browserIdentity = Get-VerifierCurrentProcessIdentity $browserSessionRecord.ProcessId `
            $browserSessionRecord.ProcessStartTicks 0 '' '' $browserSessionRecord.CdpPort $Context.RunId '' `
            0 $BrowserPath
        $browserSessionRecord.ProcessParentProcessId = [int]$browserIdentity.Record.ParentProcessId
        $browserSessionRecord.ProcessParentProcessStartTicks = [long]$browserIdentity.Record.ParentProcessStartTicks
        $browserSessionRecord.ProcessCommandLine = [string]$browserIdentity.Record.CommandLine
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
        Write-VerifierManifest $Context
        $cleanupError = $null
        try { Complete-VerifierBrowserSession $Context $browserSessionRecord } catch {
            $cleanupError = $_
        }
        if ($null -ne $cleanupError) {
            Throw-VerifierInfrastructure ("Browser session startup failed and exact cleanup was not proven: " +
                (Get-VerifierErrorMessage $cleanupError))
        }
        if (Test-VerifierInfrastructureError $startupError) { throw $startupError }
        Throw-VerifierInfrastructure ("Browser session startup failed: " + (Get-VerifierErrorMessage $startupError))
    }
}

function Register-VerifierEvidenceArtifact($Context, [string]$Path) {
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

function Set-VerifierCallerOwnedPreview($Context, [string]$BaseUrl) {
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
         $expectedScript = Get-VerifierFullPath (Join-Path $Context.WorktreeRoot 'scripts\preview.ps1')
         $expectedWebRoot = Get-VerifierFullPath (Join-Path $Context.WorktreeRoot 'war')
        [void](Assert-VerifierPhysicalOwnedPath $Context.WorktreeRoot $expectedScript)
        [void](Assert-VerifierPhysicalOwnedPath $Context.WorktreeRoot $expectedWebRoot -ValidateTree)
        if ([string]$identity.protocol -ne 'troubleshootjs-preview-identity-v1' -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string]$identity.repositoryRoot) $Context.WorktreeRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string]$identity.previewScript) $expectedScript) -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string]$identity.webRoot) $expectedWebRoot) -or
                [int]$identity.previewPort -ne [int]$uri.Port) {
            Throw-VerifierInfrastructure "Caller-owned preview identity does not match this worktree."
        }
        $runOwnedHandshake = ($Context.Server.Owner -eq 'run' -and
            $Context.Server.BaseUrl -eq $normalized)
        $identityRunId = [string]$identity.verifierRunId
        $identityNonce = [string]$identity.verifierNonce
        if ($runOwnedHandshake) {
            if ($identityRunId -ne [string]$Context.RunId -or
                    $identityNonce -ne [string]$Context.PreviewNonce -or
                    [int]$identity.processId -ne [int]$Context.Server.ProcessId -or
                    [long]$identity.processStartTicks -ne [long]$Context.Server.ProcessStartTicks -or
                    [int]$identity.previewPort -ne [int]$Context.Server.Port) {
                Throw-VerifierInfrastructure "Run-owned preview identity did not match its recorded run, process, nonce, or port."
            }
            $recordedProcess = Get-VerifierProcessById ([int]$Context.Server.ProcessId)
            if ($null -eq $recordedProcess -or
                    (Get-VerifierProcessStartTicks $recordedProcess) -ne [long]$Context.Server.ProcessStartTicks) {
                Throw-VerifierInfrastructure "Run-owned preview identity process PID $($Context.Server.ProcessId) was not the recorded process instance."
            }
            $snapshot = @(Get-CimInstance Win32_Process -Filter "ProcessId = $($Context.Server.ProcessId)" -ErrorAction Stop)
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

function Start-VerifierOwnedPreview($Context, [string]$PreviewScript, [int]$TimeoutSeconds) {
    $lease = New-VerifierPortLease $Context 'preview'
    $canonicalPreviewScript = Get-VerifierFullPath $PreviewScript
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
        $process = Start-VerifierProcess $powershell @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $Context.Server.Script,
            '-Port', [string]$lease.Port, '-VerifierRunId', $Context.RunId,
            '-VerifierNonce', $Context.PreviewNonce) $stdoutLog $stderrLog
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
        if ([int]$Context.Server.ProcessId -gt 0 -and
                (-not $Context.Server.PSObject.Properties['ProcessIdentityKnown'] -or
                 -not [bool]$Context.Server.ProcessIdentityKnown)) {
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
        [bool]$ProcessTerminationProven, [bool]$ListenerInspectionProven,
        [bool]$ListenerAbsent) {
    if ($null -eq $Server) { return $false }
    $identityKnown = ($Server.PSObject.Properties['ProcessIdentityKnown'] -and
        [bool]$Server.ProcessIdentityKnown -and
        [long]$Server.ProcessStartTicks -gt 0)
    if ([int]$Server.ProcessId -gt 0 -and
            (-not $identityKnown) -and -not $ProcessTerminationProven) {
        return $false
    }
    return $ProcessTerminationProven -and $ListenerInspectionProven -and $ListenerAbsent
}

function Complete-VerifierPreview($Context) {
    if ($null -eq $Context.Server -or $Context.Server.Owner -ne 'run' -or
            $Context.Server.CleanupResult -eq 'complete') { return }
    try {
        $processTerminationProven = ([int]$Context.Server.ProcessId -le 0)
        $processId = [int]$Context.Server.ProcessId
        if ($processId -gt 0) {
            $identityKnown = ($Context.Server.PSObject.Properties['ProcessIdentityKnown'] -and
                [bool]$Context.Server.ProcessIdentityKnown -and
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
        $previewInspection = Get-VerifierLoopbackListenerRecords ([int]$Context.Server.Port)
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
        $Context.Server.ProcessTerminationProven = [bool]$processTerminationProven
        $Context.Server.ProcessAbsent = ($processId -le 0 -or
            $null -eq (Get-VerifierProcessById $processId))
        $Context.Server.ListenerInspectionProven = ([bool]$previewInspection.Success -and
            [bool]$previewInspection.Known)
        $Context.Server.ListenerAbsent = (-not [bool]$previewInspection.HasListeners)
        if (-not (Test-VerifierPreviewCleanupReadiness $Context.Server $processTerminationProven `
                ([bool]$previewInspection.Success -and [bool]$previewInspection.Known) `
                (-not [bool]$previewInspection.HasListeners))) {
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
        $Context.Server.Lease.ProcessTerminationProven = [bool]$processTerminationProven
        $Context.Server.Lease.ProcessAbsent = [bool]$Context.Server.ProcessAbsent
        Release-VerifierPortLease $Context $Context.Server.Lease
        Write-VerifierManifest $Context
    } catch {
        $Context.Server.State = 'cleanup-failed'
        $Context.Server.CleanupResult = 'infrastructure-failure'
        $Context.Server.Error = Get-VerifierErrorMessage $_
        if ([int]$Context.Server.ProcessId -gt 0 -and
                (-not $Context.Server.PSObject.Properties['ProcessIdentityKnown'] -or
                 -not [bool]$Context.Server.ProcessIdentityKnown)) {
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
    if ($null -eq $Lease) { return $false }
    if ([string]$Lease.Status -ne 'released') { return $true }
    if ($Lease.PSObject.Properties['ReleaseState'] -and
            [string]$Lease.ReleaseState -ne 'complete') { return $true }
    if ($Lease.PSObject.Properties['ReleaseJournalState'] -and
            [string]$Lease.ReleaseJournalState -ne 'complete') { return $true }
    # The durable pre-delete tombstone is intentionally incomplete until the
    # claim has been removed and the final released state has been persisted.
    # This also makes recovery after a process interruption idempotent.
    if ($Lease.PSObject.Properties['ClaimState'] -and
            [string]$Lease.ClaimState -ne 'released') { return $true }
    if ($Lease.PSObject.Properties['ClaimMutex'] -and $null -ne $Lease.ClaimMutex) {
        return $true
    }
    # A persisted released/manifested tombstone can outlive the process that
    # wrote it. The exact claim path is still owned evidence until deletion is
    # proven, so never let Status alone make Complete-VerifierRun skip it.
    if ($Lease.PSObject.Properties['Path'] -and
            -not [String]::IsNullOrWhiteSpace([string]$Lease.Path)) {
        try {
            if (Test-Path -LiteralPath $Lease.Path -PathType Leaf -ErrorAction Stop) {
                return $true
            }
        } catch {
            return $true
        }
    }
    return $false
}

function Complete-VerifierRun($Context) {
    if ($null -eq $Context) {
        return [pscustomobject]@{ Success = $true; Errors = @() }
    }
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
        $skipCurrentListenerCheck = $false
        if ($lease.PSObject.Properties['MutexReleased'] -and
                [bool]$lease.MutexReleased -and
                $lease.PSObject.Properties['ReleaseState'] -and
                [string]$lease.ReleaseState -in @('os-released', 'complete', 'claim-delete-failed') -and
                $lease.PSObject.Properties['ReleaseJournalState'] -and
                [string]$lease.ReleaseJournalState -eq 'complete' -and
                $lease.PSObject.Properties['ClaimState'] -and
                [string]$lease.ClaimState -eq 'released' -and
                $claimAbsenceKnown -and $claimAbsent) {
            # The old run's exact OS claim is durably released and its exact
            # tombstone is absent. A newer legitimate run may now be listening
            # on the same port, so do not treat that newer listener as an old
            # cleanup failure.
            $skipCurrentListenerCheck = $true
        }
        if (-not $skipCurrentListenerCheck) {
            try {
                $finalInspection = Get-VerifierLoopbackListenerRecords ([int]$lease.Port)
                if (-not $finalInspection.Success -or -not $finalInspection.Known -or
                        $finalInspection.HasListeners) {
                    [void]$errors.Add("Port $($lease.Port) listener absence was not positively proven after cleanup.")
                }
            } catch {
                [void]$errors.Add("Port $($lease.Port) listener inspection failed after cleanup: $(Get-VerifierErrorMessage $_)")
            }
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
    if ($InvocationSucceeded) { return 0 }
    if ($null -ne $LastExitCode -and ([int]$LastExitCode -eq 1 -or [int]$LastExitCode -eq 2)) {
        return [int]$LastExitCode
    }
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
    'Get-VerifierCanonicalWindowsPath', 'Get-VerifierFullPath',
    'Resolve-VerifierBrowserPath',
    'Get-VerifierPhysicalExistingPath', 'Get-VerifierCanonicalPhysicalPath',
    'Test-VerifierPhysicalChildPath',
    'Assert-VerifierPhysicalOwnedPath',
    'Assert-VerifierNoReparseAncestors', 'Assert-VerifierNoReparseTree',
    'Remove-VerifierOwnedTree',
    'Get-VerifierRepositoryIdentity', 'Get-VerifierUtcText',
    'Get-VerifierPortMutexName',
    'ConvertTo-VerifierWindowsArgument', 'ConvertTo-VerifierArgumentString',
    'Start-VerifierProcess', 'Invoke-VerifierBoundedProcess',
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
    'Write-VerifierManifest', 'Test-VerifierCanonicalWindowsPathValue',
    'Get-VerifierProfileReferenceRecords', 'Assert-VerifierProfileSnapshotQuiescent',
    'Assert-VerifierBrowserProfileIsQuiescent'
)

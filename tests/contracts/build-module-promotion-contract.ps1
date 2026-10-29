[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -ne 5 -or
        $PSVersionTable.PSVersion.Minor -ne 1) {
    throw 'Run this selected-path contract with Windows PowerShell 5.1.'
}

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $repo 'scripts/VerifierIsolation.psm1') -Force -DisableNameChecking
$tokens = $null; $errors = $null
$buildScript = Join-Path $repo 'scripts/build.ps1'
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    $buildScript, [ref]$tokens, [ref]$errors)
if ($errors.Count -ne 0) { throw 'Build script must parse before testing promotion and ownership.' }
$functionNames = @(
    'Get-BuildErrorMessage', 'Assert-BuildInputIdentityStable',
    'New-BuildInfrastructureException',
    'Throw-BuildInfrastructure', 'ConvertTo-BuildWindowsArgument',
    'ConvertTo-BuildArgumentString', 'Get-BuildExpectedCommandLine',
    'Publish-BuildModule', 'Get-BuildOwnershipGuardPath',
    'Enter-BuildOwnershipGuard', 'Exit-BuildOwnershipGuard'
)
$definitions = @($ast.FindAll({ param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
        $functionNames -ccontains $node.Name
}, $true))
foreach ($functionName in $functionNames) {
    $matches = @($definitions | Where-Object { $_.Name -ceq $functionName })
    if ($matches.Count -ne 1) {
        throw "Missing unique production build function '$functionName'."
    }
}
foreach ($definition in $definitions) {
    . ([scriptblock]::Create($definition.Extent.Text))
}
$identityStabilityCalls = @($ast.FindAll({ param($node)
    $node -is [System.Management.Automation.Language.CommandAst] -and
        $node.GetCommandName() -ceq 'Assert-BuildInputIdentityStable'
}, $true))
$receiptWrites = @($ast.FindAll({ param($node)
    $node -is [System.Management.Automation.Language.CommandAst] -and
        $node.GetCommandName() -ceq 'Set-Content' -and
        $node.Extent.Text.Contains('production-build-identity.json')
}, $true))
$promotionCalls = @($ast.FindAll({ param($node)
    $node -is [System.Management.Automation.Language.CommandAst] -and
        $node.GetCommandName() -ceq 'Publish-BuildModule'
}, $true))
if ($identityStabilityCalls.Count -ne 2 -or $receiptWrites.Count -ne 1 -or
        $promotionCalls.Count -ne 1 -or
        $promotionCalls[0].Extent.StartOffset -ge $identityStabilityCalls[1].Extent.StartOffset -or
        $identityStabilityCalls[1].Extent.StartOffset -ge $receiptWrites[0].Extent.StartOffset) {
    throw 'Production build must revalidate compiler inputs after promotion and before writing its receipt.'
}

$taskRoot = Join-Path ([IO.Path]::GetTempPath()) `
    ('TroubleshootJS-promotion-' + [guid]::NewGuid().ToString('N'))
$fixture = Join-Path $taskRoot 'repo'
$toolRoot = Join-Path $fixture '.tools/gwt-2.7.0'
$scratch = Join-Path $toolRoot ('promotion-contract-' + [guid]::NewGuid().ToString('N'))
$stage = Join-Path $scratch 'generated/circuitjs1'
$installed = Join-Path $fixture 'war/circuitjs1'
$otherFixture = Join-Path $taskRoot 'other-repo'
$otherToolRoot = Join-Path $otherFixture '.tools/gwt-2.7.0'
$childScriptPath = Join-Path $taskRoot 'guard-contender.ps1'
$assertions = 0
$crossVolumeStatus = 'NOT RUN'
$crossVolumeRoot = ''
$guard = $null

function Require-Promotion([bool]$condition, [string]$message) {
    $script:assertions++
    if (-not $condition) { throw $message }
}

function Invoke-GuardChild([string]$Mode, [string]$RepositoryRoot,
        [string]$ChildToolRoot, [string]$ScriptPath, $PowershellCommand) {
    $arguments = @('-NoProfile', '-NonInteractive', '-File', $ScriptPath,
        '-Mode', $Mode, '-RepositoryRoot', $RepositoryRoot,
        '-ToolRoot', $ChildToolRoot)
    $expectedCommandLine = Get-BuildExpectedCommandLine `
        $PowershellCommand.Source $arguments
    $startInfo = New-Object Diagnostics.ProcessStartInfo
    $startInfo.FileName = $PowershellCommand.Source
    $startInfo.Arguments = ConvertTo-BuildArgumentString $arguments
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) { throw 'Could not start the PowerShell ownership contender.' }
        $processId = [int]$process.Id
        $startTicks = [long]$process.StartTime.ToUniversalTime().Ticks
        $executable = [string]$startInfo.FileName
        if ($processId -le 0 -or $startTicks -le 0 -or
                [String]::IsNullOrWhiteSpace($executable) -or
                [String]::IsNullOrWhiteSpace($expectedCommandLine)) {
            throw 'PowerShell ownership contender launch identity was incomplete.'
        }
        if (-not $process.WaitForExit(15000)) {
            $process.Refresh()
            if (-not [bool]$process.HasExited) {
                $current = Get-Process -Id $processId -ErrorAction SilentlyContinue
                if ($null -eq $current -or
                        [long](Get-VerifierProcessStartTicks $current) -ne $startTicks -or
                        -not ([string]$current.Path).Equals($executable,
                            [StringComparison]::OrdinalIgnoreCase)) {
                    throw "Timed out with unproven PowerShell contender identity PID=$processId; it was left running."
                }
                $records = @(Get-CimInstance Win32_Process `
                    -Filter "ProcessId = $processId" -ErrorAction Stop)
                if ($records.Count -ne 1 -or
                        [int]$records[0].ProcessId -ne $processId -or
                        -not ([string]$records[0].ExecutablePath).Equals($executable,
                            [StringComparison]::OrdinalIgnoreCase) -or
                        -not (Test-VerifierCommandLineEquivalent `
                            ([string]$records[0].CommandLine) $expectedCommandLine)) {
                    throw "Timed out without complete PowerShell contender command identity PID=$processId; it was left running."
                }
                $process.Kill()
                if (-not $process.WaitForExit(5000)) {
                    throw "Exact PowerShell contender PID=$processId did not terminate after timeout cleanup."
                }
            }
            throw "PowerShell ownership contender exceeded 15000ms (PID=$processId, startTicks=$startTicks)."
        }
        $process.Refresh()
        if (-not [bool]$process.HasExited) {
            throw "PowerShell contender PID=$processId has no proven terminal state."
        }
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        return [pscustomobject]@{
            ProcessId = $processId
            ProcessStartTicks = $startTicks
            Executable = $executable
            CommandLine = $expectedCommandLine
            TerminationProven = [bool]$process.HasExited
            ExitCode = [int]$process.ExitCode
            Stdout = $stdout
            Stderr = $stderr
        }
    } finally {
        $process.Dispose()
    }
}

try {
    $stableIdentity = [pscustomobject]@{ InputDigest = ('a' * 64); InputFileCount = 37 }
    $changedIdentity = [pscustomobject]@{ InputDigest = ('b' * 64); InputFileCount = 37 }
    Assert-BuildInputIdentityStable $stableIdentity $stableIdentity 'in the matching-input canary'
    Require-Promotion $true 'Unchanged compiler input identity should remain certifiable.'
    $prePromotionRejected = $false
    try {
        Assert-BuildInputIdentityStable $stableIdentity $changedIdentity `
            'during compilation before module promotion'
    } catch { $prePromotionRejected = $true }
    Require-Promotion $prePromotionRejected 'Changed compiler input digest must fail before module promotion.'
    $receiptWriteReached = $false
    $postPromotionMessage = ''
    try {
        Assert-BuildInputIdentityStable $stableIdentity $changedIdentity `
            'during module promotion before receipt writing'
        $receiptWriteReached = $true
    } catch { $postPromotionMessage = [string]$_.Exception.Message }
    Require-Promotion (-not $receiptWriteReached -and
        $postPromotionMessage.Contains('during module promotion before receipt writing')) `
        'A post-promotion source change must prevent receipt writing and identify the failed boundary.'

    $powershellCommand = Get-Command 'powershell.exe' -ErrorAction SilentlyContinue
    if ($null -eq $powershellCommand -or
            [String]::IsNullOrWhiteSpace([string]$powershellCommand.Source)) {
        throw 'Windows PowerShell 5.1 executable is required for process ownership canaries.'
    }
    New-Item -ItemType Directory -Path $fixture, $otherFixture, $toolRoot, `
        $scratch, $installed, $stage -Force | Out-Null

    $artifactHashes = @($ast.FindAll({ param($node)
        $node -is [System.Management.Automation.Language.CommandAst] -and
            $node.GetCommandName() -ceq 'Get-VerifierFileSha256'
    }, $true))
    $ambientHashes = @($ast.FindAll({ param($node)
        $node -is [System.Management.Automation.Language.CommandAst] -and
            $node.GetCommandName() -ceq 'Get-FileHash'
    }, $true))
    Require-Promotion ($artifactHashes.Count -eq 2 -and $ambientHashes.Count -eq 0) `
        'Both production artifact checks must use the shared cryptographic hash owner.'
    $hashProbe = Join-Path $taskRoot 'hash-probe.bin'
    [IO.File]::WriteAllBytes($hashProbe, [byte[]]@(97, 98, 99))
    $abcHash = 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'
    Require-Promotion ((Get-VerifierFileSha256 $hashProbe) -ceq $abcHash) `
        'The selected Windows PowerShell hash implementation must match the literal abc SHA256.'
    [IO.File]::WriteAllBytes($hashProbe, [byte[]]@())
    $emptyHash = Get-VerifierFileSha256 $hashProbe
    Require-Promotion ($emptyHash -ceq `
        'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855') `
        'The selected implementation must read changed file bytes and match the literal empty SHA256.'
    Require-Promotion ($emptyHash -ne $abcHash.ToUpperInvariant()) `
        'The production checksum comparison must reject mismatched bytes with an uppercase pin.'
    $exclusiveProbe = [IO.File]::Open($hashProbe, [IO.FileMode]::Open,
        [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    try {
        Require-Promotion ($exclusiveProbe.Length -eq 0) `
            'Hashing must release the file handle before an exclusive successor opens it.'
    } finally {
        $exclusiveProbe.Dispose()
    }

    $childFunctions = [string]::Join([Environment]::NewLine,
        @($definitions | ForEach-Object { $_.Extent.Text }))
    $childSource = @'
param(
    [ValidateSet('blocked', 'allowed')]
    [string]$Mode,
    [string]$RepositoryRoot,
    [string]$ToolRoot
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module __VERIFIER_MODULE__ -Force -DisableNameChecking
__BUILD_FUNCTIONS__
$guard = $null
try {
    $guard = Enter-BuildOwnershipGuard $RepositoryRoot $ToolRoot
} catch {
    if ($Mode -eq 'blocked' -and
            $_.Exception.Message.Contains('Another build already owns this worktree compiler and cache namespace.')) {
        exit 0
    }
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 21
}
if ($Mode -eq 'blocked') {
    Exit-BuildOwnershipGuard $guard
    $guard = $null
    [Console]::Error.WriteLine('Contender unexpectedly acquired the held worktree guard.')
    exit 20
}
Exit-BuildOwnershipGuard $guard
$guard = $null
exit 0
'@
    $moduleLiteral = "'" + (Join-Path $repo 'scripts/VerifierIsolation.psm1').Replace("'", "''") + "'"
    $childSource = $childSource.Replace('__VERIFIER_MODULE__', $moduleLiteral).Replace(
        '__BUILD_FUNCTIONS__', $childFunctions)
    [IO.File]::WriteAllText($childScriptPath, $childSource,
        [Text.UTF8Encoding]::new($false))

    New-Item -ItemType Directory -Path (Split-Path $installed -Parent) -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $installed 'old-developer.cache.js') -Value 'developer'
    Set-Content -LiteralPath (Join-Path $stage 'circuitjs1.nocache.js') -Value 'player'
    Set-Content -LiteralPath (Join-Path $stage 'new-player.cache.js') -Value 'player'

    $compileVolume = [IO.Path]::GetPathRoot(
        (Get-VerifierCanonicalWindowsPath $scratch))
    $installedVolume = [IO.Path]::GetPathRoot(
        (Get-VerifierCanonicalWindowsPath $installed))
    Require-Promotion ($compileVolume.Equals($installedVolume,
        [StringComparison]::OrdinalIgnoreCase) -and
        (Test-VerifierPhysicalChildPath $fixture $scratch)) `
        'Production staging fixture must be physically contained in the repository on the installed module volume.'

    Publish-BuildModule $fixture $scratch $stage
    Require-Promotion ((Get-ChildItem -LiteralPath $installed -File).Count -eq 2 -and
        -not (Test-Path -LiteralPath (Join-Path $installed 'old-developer.cache.js')) -and
        (Test-Path -LiteralPath (Join-Path $scratch 'previous-module/old-developer.cache.js'))) `
        'Promotion must install only the new compiler output and preserve the prior tree until success.'
    Remove-VerifierOwnedTree $scratch (Join-Path $scratch 'previous-module')
    New-Item -ItemType Directory -Path $stage -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $stage 'circuitjs1.nocache.js') -Value 'next'
    Set-Content -LiteralPath (Join-Path $stage 'next.cache.js') -Value 'next'
    $lock = [IO.File]::Open((Join-Path $stage 'circuitjs1.nocache.js'),
        [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::None)
    $rejected = $false
    try { Publish-BuildModule $fixture $scratch $stage } catch { $rejected = $true }
    finally { $lock.Dispose() }
    Require-Promotion $rejected 'The selected Windows directory promotion must reject a locked staged output.'
    Require-Promotion ((Test-Path -LiteralPath (Join-Path $installed 'new-player.cache.js')) -and
        -not (Test-Path -LiteralPath (Join-Path $scratch 'previous-module'))) `
        'Failed native promotion must roll back the exact prior working module.'
    $rejected = $false
    try { Publish-BuildModule $fixture $scratch $installed } catch { $rejected = $true }
    Require-Promotion ($rejected -and (Test-Path -LiteralPath (Join-Path $installed 'new-player.cache.js'))) `
        'A staged path outside the compile namespace must reject without moving the installed output.'

    $crossVolumeStatus = 'NOT AVAILABLE: no writable second volume was present.'
    $currentVolume = [IO.Path]::GetPathRoot(
        (Get-VerifierCanonicalWindowsPath $fixture))
    foreach ($drive in @(Get-PSDrive -PSProvider FileSystem)) {
        $candidateVolume = [IO.Path]::GetPathRoot([IO.Path]::GetFullPath([string]$drive.Root))
        if ([String]::IsNullOrWhiteSpace($candidateVolume) -or
                $candidateVolume.Equals($currentVolume,
                    [StringComparison]::OrdinalIgnoreCase)) { continue }
        $candidateRoot = Join-Path ([string]$drive.Root) `
            ('TroubleshootJS-promotion-cross-volume-' + [guid]::NewGuid().ToString('N'))
        try { New-Item -ItemType Directory -Path $candidateRoot -ErrorAction Stop | Out-Null }
        catch { continue }
        $crossVolumeRoot = $candidateRoot
        $crossScratch = Join-Path $crossVolumeRoot 'compile'
        $crossStage = Join-Path $crossScratch 'generated/circuitjs1'
        New-Item -ItemType Directory -Path $crossStage -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $crossStage 'circuitjs1.nocache.js') -Value 'foreign'
        Set-Content -LiteralPath (Join-Path $crossStage 'foreign.cache.js') -Value 'foreign'
        $rejected = $false
        $crossVolumeError = ''
        try { Publish-BuildModule $fixture $crossScratch $crossStage } catch {
            $rejected = $true
            $crossVolumeError = [string]$_.Exception.Message
        }
        Require-Promotion ($rejected -and
            ($crossVolumeError.Contains('escaped the physical namespace') -or
                $crossVolumeError.Contains('must be on the same volume')) -and
            (Test-Path -LiteralPath (Join-Path $installed 'new-player.cache.js'))) `
            'A compiler tree on another volume must be rejected before changing the installed output.'
        $crossVolumeStatus = 'PASS: foreign-volume staging rejected before promotion.'
        break
    }

    $guard = Enter-BuildOwnershipGuard $fixture $toolRoot
    try {
        $guard.Stream.Position = 0
        $guardBuffer = New-Object byte[] ([int]$guard.Stream.Length)
        $guardBytesRead = $guard.Stream.Read($guardBuffer, 0, $guardBuffer.Length)
        if ($guardBytesRead -ne $guardBuffer.Length) {
            throw 'The held worktree guard metadata was not fully readable by its owner.'
        }
        $guardMetadataText = [Text.UTF8Encoding]::new($false).GetString($guardBuffer)
        $guardMetadata = $guardMetadataText | ConvertFrom-Json
        Require-Promotion ([int]$guardMetadata.processId -eq $PID -and
            [long]$guardMetadata.processStartTicks -eq
                [long](Get-VerifierProcessStartTicks (Get-Process -Id $PID)) -and
            [string]$guardMetadata.executable -eq
                [string][Diagnostics.Process]::GetCurrentProcess().MainModule.FileName -and
            -not [String]::IsNullOrWhiteSpace([string]$guardMetadata.commandLine) -and
            [string]$guardMetadata.repositoryIdentity -eq
                (Get-VerifierCanonicalWindowsPath $fixture)) `
            'The exclusive worktree guard must record exact owner PID, creation time, executable, and repository identity.'

        $sameWorktree = Invoke-GuardChild 'blocked' $fixture $toolRoot `
            $childScriptPath $powershellCommand
        Require-Promotion ($sameWorktree.TerminationProven -and
            $sameWorktree.ProcessId -gt 0 -and $sameWorktree.ProcessStartTicks -gt 0 -and
            $sameWorktree.Executable -eq $powershellCommand.Source -and
            $sameWorktree.ExitCode -eq 0) `
            ('A second Windows PowerShell contender must not enter the held worktree guard; ' +
                "exit=$($sameWorktree.ExitCode), stdout=$($sameWorktree.Stdout), stderr=$($sameWorktree.Stderr)")

        $otherWorktree = Invoke-GuardChild 'allowed' $otherFixture $otherToolRoot `
            $childScriptPath $powershellCommand
        Require-Promotion ($otherWorktree.TerminationProven -and
            $otherWorktree.ProcessId -gt 0 -and $otherWorktree.ProcessStartTicks -gt 0 -and
            $otherWorktree.Executable -eq $powershellCommand.Source -and
            $otherWorktree.ExitCode -eq 0) `
            ('A distinct worktree must acquire and release its own guard independently; ' +
                "exit=$($otherWorktree.ExitCode), stdout=$($otherWorktree.Stdout), stderr=$($otherWorktree.Stderr)")
    } finally {
        Exit-BuildOwnershipGuard $guard
        $guard = $null
    }

    $afterRelease = Invoke-GuardChild 'allowed' $fixture $toolRoot `
        $childScriptPath $powershellCommand
    Require-Promotion ($afterRelease.TerminationProven -and
        $afterRelease.ProcessId -gt 0 -and $afterRelease.ProcessStartTicks -gt 0 -and
        $afterRelease.Executable -eq $powershellCommand.Source -and
        $afterRelease.ExitCode -eq 0) `
        ('A second PowerShell process must acquire and release after the original owner releases; ' +
            "exit=$($afterRelease.ExitCode), stdout=$($afterRelease.Stdout), stderr=$($afterRelease.Stderr)")

    Write-Output ('PASS: production build module promotion assertions=' + $assertions +
        ' worktree ownership canaries=3')
    Write-Output ('CROSS_VOLUME_CANARY ' + $crossVolumeStatus)
} finally {
    if ($null -ne $guard) {
        Exit-BuildOwnershipGuard $guard
    }
    if (-not [String]::IsNullOrWhiteSpace($crossVolumeRoot) -and
            (Test-Path -LiteralPath $crossVolumeRoot)) {
        $crossParent = [IO.Path]::GetPathRoot($crossVolumeRoot)
        Remove-VerifierOwnedTree $crossParent $crossVolumeRoot
    }
    if (Test-Path -LiteralPath $taskRoot) {
        Remove-VerifierOwnedTree ([IO.Path]::GetTempPath()) $taskRoot
    }
}

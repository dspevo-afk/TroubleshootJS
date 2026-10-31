param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$ModulePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:Checks = 0

$expectedCandidateSha256 = '8a7eff1639e973e6e4ade9a72716274fcd95caece0074f19ede72817d01f2e77'
$resolvedModulePath = [IO.Path]::GetFullPath($ModulePath)
if (-not (Test-Path -LiteralPath $resolvedModulePath -PathType Leaf)) {
    throw 'The prospective verifier module does not exist.'
}
$sha256 = [Security.Cryptography.SHA256]::Create()
try {
    $moduleStream = [IO.File]::OpenRead($resolvedModulePath)
    try {
        $moduleSha256 = ([BitConverter]::ToString($sha256.ComputeHash($moduleStream))).Replace('-', '').ToLowerInvariant()
    } finally {
        $moduleStream.Dispose()
    }
} finally {
    $sha256.Dispose()
}
if ($moduleSha256 -cne $expectedCandidateSha256) {
    throw 'The prospective verifier module differs from the registered candidate.'
}

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$verifyRoot = Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS/verify'
$taskRoot = Join-Path $verifyRoot ('aq-census-reparse-' + [Guid]::NewGuid().ToString('N'))
$runNamespaceRoot = Join-Path $taskRoot 'run-namespace'
$descendantTarget = Join-Path $taskRoot 'outside-descendant-target'
$runRootTarget = Join-Path $taskRoot 'outside-runroot-target'
$namespaceTarget = Join-Path $taskRoot 'outside-namespace-target'
$context = $null
$primaryFailure = $null
$cleanupFailure = $null
$sentinelValue = 'aq-namespace-census-reparse-sentinel-v1'
$module = Import-Module -Name $resolvedModulePath -Force -DisableNameChecking -PassThru

function Assert-Contract([bool]$Condition, [string]$Name) {
    if (-not $Condition) {
        throw ('Contract assertion failed: ' + $Name)
    }
    $script:Checks++
}

function Move-OwnedFixtureDirectory([string]$Source, [string]$Destination) {
    if (Test-Path -LiteralPath $Destination) { throw 'Fixture move destination exists.' }
    [void](& $module {
        param($rootValue, $sourceValue, $destinationValue)
        [void](Assert-VerifierPhysicalOwnedPath $rootValue $sourceValue -ValidateTree)
        [void](Assert-VerifierPhysicalOwnedPath $rootValue $destinationValue)
    } $taskRoot $Source $Destination)
    [IO.Directory]::Move([IO.Path]::GetFullPath($Source), [IO.Path]::GetFullPath($Destination))
}

function Invoke-ContextCensus($VerifierModule, $RunContext) {
    [void](& $VerifierModule {
        param($contextValue)
        Assert-VerifierContextPhysicalResources $contextValue
    } $RunContext)
}

function Invoke-EarlyOwnershipProof($VerifierModule, [string]$Root, [string]$Candidate) {
    [void](& $VerifierModule {
        param($rootValue, $candidateValue)
        Assert-VerifierPhysicalOwnedPath $rootValue $candidateValue
    } $Root $Candidate)
}

function Assert-ExpectedInfrastructureRejection($VerifierModule, [string]$Name, [scriptblock]$Action) {
    $rejectedAsInfrastructure = $false
    try {
        & $Action
    } catch {
        $rejectedAsInfrastructure = [bool](& $VerifierModule {
            param($errorValue)
            Test-VerifierInfrastructureError $errorValue
        } $_)
    }
    Assert-Contract $rejectedAsInfrastructure $Name
}

function New-SentinelDirectory([string]$DirectoryPath) {
    [void][IO.Directory]::CreateDirectory($DirectoryPath)
    $sentinelPath = Join-Path $DirectoryPath 'sentinel.txt'
    [IO.File]::WriteAllText($sentinelPath, $sentinelValue, [Text.UTF8Encoding]::new($false))
    return $sentinelPath
}

function Assert-SentinelUnchanged([string]$SentinelPath) {
    Assert-Contract ([IO.File]::ReadAllText($SentinelPath) -ceq $sentinelValue) 'external-sentinel-preserved'
}

function Assert-ExactJunction(
    [string]$JunctionPath,
    [string]$ExpectedTarget,
    [string]$ExpectedParent,
    [string]$SentinelPath
) {
    $junctionFullPath = [IO.Path]::GetFullPath($JunctionPath)
    $targetFullPath = [IO.Path]::GetFullPath($ExpectedTarget)
    $parentFullPath = [IO.Path]::GetFullPath($ExpectedParent)
    $actualParentPath = [IO.Path]::GetFullPath((Split-Path -Parent $junctionFullPath))
    Assert-Contract ($actualParentPath.Equals(
        $parentFullPath, [StringComparison]::OrdinalIgnoreCase
    )) 'exact-junction-parent'
    [void](& $module {
        param($pathValue)
        Assert-VerifierNoReparseAncestors $pathValue
    } $parentFullPath)
    $item = Get-Item -LiteralPath $junctionFullPath -Force -ErrorAction Stop
    Assert-Contract (
        [IO.Path]::GetFullPath([string]$item.FullName).Equals(
            $junctionFullPath, [StringComparison]::OrdinalIgnoreCase) -and
        (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)
    ) 'exact-junction-identity'
    if ($item.PSObject.Properties['LinkType'] -and
            -not [String]::IsNullOrWhiteSpace([string]$item.LinkType)) {
        Assert-Contract ([string]$item.LinkType -ieq 'Junction') 'exact-junction-type'
    }
    if ($item.PSObject.Properties['Target'] -and $null -ne $item.Target) {
        $targets = @($item.Target | ForEach-Object { [IO.Path]::GetFullPath([string]$_) })
        Assert-Contract (
            $targets.Count -eq 1 -and
            $targets[0].Equals($targetFullPath, [StringComparison]::OrdinalIgnoreCase)
        ) 'exact-junction-target'
    }
    Assert-Contract (
        [IO.File]::ReadAllText((Join-Path $junctionFullPath 'sentinel.txt')) -ceq $sentinelValue
    ) 'junction-target-sentinel'
    Assert-SentinelUnchanged $SentinelPath
}

function Remove-ExactJunction(
    [string]$JunctionPath,
    [string]$ExpectedTarget,
    [string]$ExpectedParent,
    [string]$SentinelPath
) {
    Assert-ExactJunction $JunctionPath $ExpectedTarget $ExpectedParent $SentinelPath
    [IO.Directory]::Delete([IO.Path]::GetFullPath($JunctionPath), $false)
    Assert-Contract (-not (Test-Path -LiteralPath $JunctionPath -ErrorAction Stop)) 'junction-removed-nonrecursively'
    Assert-SentinelUnchanged $SentinelPath
}

try {
    [void](& $module {
        param($pathValue)
        Assert-VerifierNoReparseAncestors $pathValue
    } $verifyRoot)
    [void][IO.Directory]::CreateDirectory($taskRoot)
    [void](& $module {
        param($pathValue)
        Assert-VerifierNoReparseAncestors $pathValue
    } $taskRoot)

    $descendantSentinel = New-SentinelDirectory $descendantTarget
    $runRootSentinel = New-SentinelDirectory $runRootTarget
    $namespaceSentinel = New-SentinelDirectory $namespaceTarget

    $context = New-VerifierRunContext $repoRoot '' $runNamespaceRoot
    Assert-Contract ($context.Server.Owner -ceq 'none') 'ownerless-server-state'
    Assert-Contract (
        @($context.BrowserSessions).Count -eq 0 -and
        @($context.LeaseRecords).Count -eq 0 -and
        @($context.Artifacts).Count -eq 0
    ) 'ownerless-session-lease-artifact-state'
    Invoke-ContextCensus $module $context

    $profileRoot = Join-Path $context.RunRoot 'browser/fixture/profile'
    [void][IO.Directory]::CreateDirectory($profileRoot)
    $descendantJunction = Join-Path $profileRoot 'task-owned-junction'
    [void](& $module {
        param($rootValue, $candidateValue)
        Assert-VerifierPhysicalOwnedPath $rootValue $candidateValue -ValidateTree
    } $context.RunRoot $profileRoot)
    [void](& $module {
        param($rootValue, $candidateValue)
        Assert-VerifierPhysicalOwnedPath $rootValue $candidateValue -ValidateTree
    } $taskRoot $descendantTarget)
    [void](New-Item -ItemType Junction -Path $descendantJunction -Target $descendantTarget -ErrorAction Stop)
    Assert-ExactJunction $descendantJunction $descendantTarget $profileRoot $descendantSentinel

    Invoke-EarlyOwnershipProof $module $context.RunNamespaceRoot $context.RunRoot
    Assert-ExpectedInfrastructureRejection $module 'descendant-junction-rejected-by-later-tree-proof' {
        & $module {
            param($pathValue)
            Assert-VerifierNoReparseTree $pathValue
        } $context.RunRoot
    }
    Assert-ExpectedInfrastructureRejection $module 'descendant-junction-rejected-by-context-census' {
        Invoke-ContextCensus $module $context
    }
    Assert-SentinelUnchanged $descendantSentinel
    Remove-ExactJunction $descendantJunction $descendantTarget $profileRoot $descendantSentinel
    Invoke-ContextCensus $module $context

    $runRootStash = Join-Path $context.RunNamespaceRoot (
        '.aq-runroot-stash-' + [Guid]::NewGuid().ToString('N'))
    Assert-Contract (-not (Test-Path -LiteralPath $runRootStash)) 'runroot-stash-is-unique'
    [void](& $module {
        param($rootValue, $candidateValue)
        Assert-VerifierPhysicalOwnedPath $rootValue $candidateValue -ValidateTree
    } $context.RunNamespaceRoot $context.RunRoot)
    [void](& $module {
        param($rootValue, $candidateValue)
        Assert-VerifierPhysicalOwnedPath $rootValue $candidateValue -ValidateTree
    } $taskRoot $runRootTarget)
    Move-OwnedFixtureDirectory $context.RunRoot $runRootStash
    $runRootMoved = $true
    $runRootLinkCreated = $false
    try {
        [void](New-Item -ItemType Junction -Path $context.RunRoot -Target $runRootTarget -ErrorAction Stop)
        $runRootLinkCreated = $true
        Assert-ExactJunction $context.RunRoot $runRootTarget $context.RunNamespaceRoot $runRootSentinel
        Assert-ExpectedInfrastructureRejection $module 'replaced-runroot-rejected-at-early-ownership' {
            Invoke-EarlyOwnershipProof $module $context.RunNamespaceRoot $context.RunRoot
        }
        Assert-ExpectedInfrastructureRejection $module 'replaced-runroot-rejected-by-context-census' {
            Invoke-ContextCensus $module $context
        }
        Assert-SentinelUnchanged $runRootSentinel
    } finally {
        if ($runRootLinkCreated) {
            Remove-ExactJunction $context.RunRoot $runRootTarget $context.RunNamespaceRoot $runRootSentinel
        } elseif (Test-Path -LiteralPath $context.RunRoot -ErrorAction Stop) {
            throw 'The replaced RunRoot path changed unexpectedly; the owned stash was retained.'
        }
        if ($runRootMoved -and (Test-Path -LiteralPath $runRootStash -PathType Container)) {
            if (Test-Path -LiteralPath $context.RunRoot -ErrorAction Stop) {
                throw 'RunRoot was occupied before restoration; the owned stash was retained.'
            }
            [void](& $module {
                param($rootValue, $candidateValue)
                Assert-VerifierPhysicalOwnedPath $rootValue $candidateValue -ValidateTree
            } $context.RunNamespaceRoot $runRootStash)
            Move-OwnedFixtureDirectory $runRootStash $context.RunRoot
            $runRootMoved = $false
        }
    }
    Invoke-ContextCensus $module $context
    Assert-SentinelUnchanged $runRootSentinel

    $namespaceStash = Join-Path $taskRoot (
        '.aq-namespace-stash-' + [Guid]::NewGuid().ToString('N'))
    Assert-Contract (-not (Test-Path -LiteralPath $namespaceStash)) 'namespace-stash-is-unique'
    [void](& $module {
        param($rootValue, $candidateValue)
        Assert-VerifierPhysicalOwnedPath $rootValue $candidateValue -ValidateTree
    } $verifyRoot $context.RunNamespaceRoot)
    [void](& $module {
        param($rootValue, $candidateValue)
        Assert-VerifierPhysicalOwnedPath $rootValue $candidateValue -ValidateTree
    } $taskRoot $namespaceTarget)
    Move-OwnedFixtureDirectory $context.RunNamespaceRoot $namespaceStash
    $namespaceMoved = $true
    $namespaceLinkCreated = $false
    try {
        [void](New-Item -ItemType Junction -Path $context.RunNamespaceRoot -Target $namespaceTarget -ErrorAction Stop)
        $namespaceLinkCreated = $true
        Assert-ExactJunction $context.RunNamespaceRoot $namespaceTarget $taskRoot $namespaceSentinel
        Assert-ExpectedInfrastructureRejection $module 'ancestor-junction-rejected-at-early-ownership' {
            Invoke-EarlyOwnershipProof $module $context.RunNamespaceRoot $context.RunRoot
        }
        Assert-ExpectedInfrastructureRejection $module 'ancestor-junction-rejected-by-context-census' {
            Invoke-ContextCensus $module $context
        }
        Assert-SentinelUnchanged $namespaceSentinel
    } finally {
        if ($namespaceLinkCreated) {
            Remove-ExactJunction $context.RunNamespaceRoot $namespaceTarget $taskRoot $namespaceSentinel
        } elseif (Test-Path -LiteralPath $context.RunNamespaceRoot -ErrorAction Stop) {
            throw 'The replaced namespace path changed unexpectedly; the owned stash was retained.'
        }
        if ($namespaceMoved -and (Test-Path -LiteralPath $namespaceStash -PathType Container)) {
            if (Test-Path -LiteralPath $context.RunNamespaceRoot -ErrorAction Stop) {
                throw 'The verifier namespace was occupied before restoration; the owned stash was retained.'
            }
            [void](& $module {
                param($rootValue, $candidateValue)
                Assert-VerifierPhysicalOwnedPath $rootValue $candidateValue -ValidateTree
            } $verifyRoot $namespaceStash)
            Move-OwnedFixtureDirectory $namespaceStash $context.RunNamespaceRoot
            $namespaceMoved = $false
        }
    }
    Invoke-ContextCensus $module $context
    Assert-SentinelUnchanged $namespaceSentinel
} catch {
    $primaryFailure = $_.Exception.Message
} finally {
    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    try {
        if ($null -ne $context) {
            $completion = Complete-VerifierRun $context
            if ($null -eq $completion -or -not [bool]$completion.Success) {
                throw 'The restored ownerless verifier context did not complete safely.'
            }
        }
        if (-not $primaryFailure -and (Test-Path -LiteralPath $taskRoot)) {
            & $module {
                Remove-VerifierOwnedTree $args[0].Root $args[0].Target
            } ([pscustomobject]@{ Root = $verifyRoot; Target = $taskRoot })
        }
    } catch {
        $cleanupFailure = $_.Exception.Message
    }
}

if ($cleanupFailure) {
    throw ('Junction contract cleanup was not proven; owned fixture retained: ' + $cleanupFailure)
}
if ($primaryFailure) {
    Write-Output ('RETAINED_WORKSPACE: ' + (Split-Path -Leaf $taskRoot))
    throw $primaryFailure
}

Write-Output ('PASS: namespace census junction safety source=' + $moduleSha256 +
    ' assertions=' + $script:Checks + ' cases=descendant,runroot,ancestor cleanupMs=' +
    $cleanupWatch.ElapsedMilliseconds)

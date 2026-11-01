[CmdletBinding()]
param(
    [string]$ModulePath = '',
    [string]$ExpectedModuleSha256 = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:Assertions = 0
$script:ModuleInfo = $null
$script:RepositoryIdentity = [Guid]::NewGuid().ToString('N')
$script:RunId = [Guid]::NewGuid().ToString('N')
$script:PreviewNonce = ('d' * 64)
$script:CreatedUtc = '2026-11-01T12:00:00.0000000Z'
$script:FixtureWorktree = Join-Path $env:TEMP 'TroubleshootJS-active-preview-fixture-worktree'
$script:BrowserPath = 'C:\synthetic\browser.exe'
$script:PreviewScript = Join-Path $script:FixtureWorktree 'scripts\preview.ps1'
$script:PreviewPid = 7201
$script:PreviewStart = 720100L
$script:CdpPid = 7101
$script:CdpStart = 710100L
$script:OriginalParentPid = 8001
$script:OriginalParentStart = 800100L
$script:PreviewPort = 45222
$script:CdpPort = 45111

function Assert-Contract([bool]$Condition, [string]$Code) {
    if (-not $Condition) { throw [InvalidOperationException]::new($Code) }
    $script:Assertions++
}

function Get-Sha256Hex([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    try {
        $sha = [Security.Cryptography.SHA256]::Create()
        try { return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
        finally { $sha.Dispose() }
    } finally { $stream.Dispose() }
}

function Get-ModuleMutexName([int]$Port) {
    return [string](& $script:ModuleInfo {
        param($requestedPort)
        Get-VerifierPortMutexName $null ([int]$requestedPort)
    } $Port)
}

function New-Claim([string]$Kind, [int]$Port, [string]$LeaseId,
        [string]$Path, [string]$MutexName) {
    return [pscustomobject]@{
        protocol = 'troubleshootjs-verifier-port-claim-v1'
        runId = $script:RunId
        repositoryIdentity = $script:RepositoryIdentity
        worktreeRoot = $script:FixtureWorktree
        leaseId = $LeaseId
        path = $Path
        kind = $Kind
        port = $Port
        mutexName = $MutexName
        ownerPid = $script:OriginalParentPid
        ownerStartTicks = $script:OriginalParentStart
    }
}

function New-ActiveGraph {
    $runNamespaceRoot = [IO.Path]::GetFullPath((Join-Path `
        (Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS\verify') `
        $script:RepositoryIdentity))
    $runRoot = [IO.Path]::GetFullPath((Join-Path $runNamespaceRoot $script:RunId))
    $leaseRoot = [IO.Path]::GetFullPath((Join-Path $runRoot 'port-leases'))
    $evidenceNamespaceRoot = [IO.Path]::GetFullPath((Join-Path `
        ([IO.Path]::GetTempPath()) 'TroubleshootJS\verify\evidence'))
    $evidenceDirectory = [IO.Path]::GetFullPath((Join-Path `
        $evidenceNamespaceRoot $script:RunId))
    $manifestPath = [IO.Path]::GetFullPath((Join-Path $runRoot 'manifest.json'))
    $cdpLeaseId = [Guid]::NewGuid().ToString('N')
    $previewLeaseId = [Guid]::NewGuid().ToString('N')
    $cdpPath = Join-Path $leaseRoot ($script:CdpPort.ToString() + '-' + $cdpLeaseId + '.lease')
    $previewPath = Join-Path $leaseRoot ($script:PreviewPort.ToString() + '-' + $previewLeaseId + '.lease')
    $cdpMutex = Get-ModuleMutexName $script:CdpPort
    $previewMutex = Get-ModuleMutexName $script:PreviewPort
    $profile = Join-Path $runRoot 'browser\profile-cdp'
    $previewUrl = 'http://127.0.0.1:' + $script:PreviewPort
    $previewCommandLine = 'powershell.exe -File ' + $script:PreviewScript +
        ' -Port ' + $script:PreviewPort + ' -VerifierRunId ' + $script:RunId +
        ' -VerifierNonce ' + $script:PreviewNonce
    $browserCommandLine = $script:BrowserPath + ' --user-data-dir=' + $profile +
        ' --remote-debugging-port=' + $script:CdpPort

    $cdpClaim = New-Claim 'cdp' $script:CdpPort $cdpLeaseId $cdpPath $cdpMutex
    $previewClaim = New-Claim 'preview' $script:PreviewPort $previewLeaseId $previewPath $previewMutex
    $cdpLease = [pscustomobject]@{
        leaseId = $cdpLeaseId; kind = 'cdp'; port = $script:CdpPort; path = $cdpPath
        runId = $script:RunId; repositoryIdentity = $script:RepositoryIdentity
        worktreeRoot = $script:FixtureWorktree; claimName = $cdpMutex
        claimOwnerPid = $script:OriginalParentPid; claimOwnerStartTicks = $script:OriginalParentStart
        status = 'bound'; claimState = 'bound'; releaseState = 'active'; releaseJournalState = 'active'
        registered = $true; releaseBlocked = $false; releaseBlockReason = ''; mutexReleased = $false
        bindValidatedUtc = $script:CreatedUtc; releasedUtc = ''
        profile = $profile; browserPath = $script:BrowserPath
        listenerInspectionSuccess = $true; listenerInspectionKnown = $true
        listenerInspectionUtc = $script:CreatedUtc; listenerHasListeners = $true; listenerAbsent = $false
        processTerminationProven = $false; processAbsent = $false; processProofRequired = $true
        boundProcessId = $script:CdpPid; boundProcessStartTicks = $script:CdpStart
        listenerProcessId = $script:CdpPid; listenerProcessStartTicks = $script:CdpStart
        listenerOwnerKind = 'user-process'; listenerOwnerProof = 'diagnostics-process-start-v1'
        listenerOwnerEvidence = 'system-diagnostics-process-starttime'; claim = $cdpClaim
    }
    $previewLease = [pscustomobject]@{
        leaseId = $previewLeaseId; kind = 'preview'; port = $script:PreviewPort; path = $previewPath
        runId = $script:RunId; repositoryIdentity = $script:RepositoryIdentity
        worktreeRoot = $script:FixtureWorktree; claimName = $previewMutex
        claimOwnerPid = $script:OriginalParentPid; claimOwnerStartTicks = $script:OriginalParentStart
        status = 'bound'; claimState = 'bound'; releaseState = 'active'; releaseJournalState = 'active'
        registered = $true; releaseBlocked = $false; releaseBlockReason = ''; mutexReleased = $false
        bindValidatedUtc = $script:CreatedUtc; releasedUtc = ''
        profile = ''; browserPath = 'powershell.exe'
        listenerInspectionSuccess = $true; listenerInspectionKnown = $true
        listenerInspectionUtc = $script:CreatedUtc; listenerHasListeners = $true; listenerAbsent = $false
        processTerminationProven = $false; processAbsent = $false; processProofRequired = $true
        boundProcessId = $script:PreviewPid; boundProcessStartTicks = $script:PreviewStart
        listenerProcessId = 4; listenerProcessStartTicks = $null
        listenerOwnerKind = 'kernel-transport'; listenerOwnerProof = 'run-owned-preview-http-sys-v1'
        listenerOwnerEvidence = 'pid-4-system-http-sys'; claim = $previewClaim
    }
    $receipt = [pscustomobject]@{
        protocol = 'troubleshootjs-verifier-browser-recovery-v1'
        authorityToken = ('c' * 64); issuedUtc = $script:CreatedUtc; state = 'bound'
        boundUtc = $script:CreatedUtc; closedUtc = ''; closeAttempted = $false; closeAttemptedUtc = ''
        runId = $script:RunId; repositoryIdentity = $script:RepositoryIdentity
        worktreeRoot = $script:FixtureWorktree; routeId = 'active-preview-fixture'
        routeName = 'active-preview-fixture'; browserPath = $script:BrowserPath; profile = $profile
        leaseId = $cdpLeaseId; cdpPort = $script:CdpPort
        rootProcessId = $script:CdpPid; rootProcessStartTicks = $script:CdpStart
        rootParentProcessId = $script:OriginalParentPid
        rootParentProcessStartTicks = $script:OriginalParentStart
        rootProcessCommandLine = $browserCommandLine
        listenerProcessId = $script:CdpPid; listenerProcessStartTicks = $script:CdpStart
    }
    $session = [pscustomobject]@{
        owner = 'run'; runId = $script:RunId; repositoryIdentity = $script:RepositoryIdentity
        worktreeRoot = $script:FixtureWorktree; routeId = 'active-preview-fixture'
        routeName = 'active-preview-fixture'; profile = $profile; cdpLeasePath = $cdpPath
        browserPath = $script:BrowserPath; processCommandLine = $browserCommandLine
        targetId = 'synthetic-target'; expectedUrl = $previewUrl
        expectedRunMarker = 'tsjVerifierRun=' + $script:RunId
        expectedRouteMarker = 'tsjVerifierRoute=active-preview-fixture'
        cdpPort = $script:CdpPort; processId = $script:CdpPid; processStartTicks = $script:CdpStart
        processParentProcessId = $script:OriginalParentPid
        processParentProcessStartTicks = $script:OriginalParentStart
        status = 'cleanup-failed'; cleanupResult = 'infrastructure-failure'
        error = 'synthetic active preview retained failure'
        profileProcessScanCompleted = $false; profileInspectionFailed = $false
        recoveryReceipt = $receipt
        containmentLaunch = [pscustomobject]@{
            protocol = 'troubleshootjs-verifier-browser-containment-launch-v1'
            jobName = 'Local\synthetic-active-preview-job'; state = 'launched'
            launchProcessId = $script:CdpPid; launchedUtc = $script:CreatedUtc
        }
    }
    $server = [pscustomobject]@{
        owner = 'run'; baseUrl = $previewUrl; repositoryIdentity = $script:RepositoryIdentity
        worktreeRoot = $script:FixtureWorktree; repositoryRoot = $script:FixtureWorktree
        webRoot = Join-Path $script:FixtureWorktree 'war'
        identityProtocol = 'troubleshootjs-preview-identity-v2'
        processCommandLine = $previewCommandLine; script = $script:PreviewScript
        runId = $script:RunId; nonce = $script:PreviewNonce
        leaseId = $previewLeaseId; leaseKind = 'preview'; leaseClaimName = $previewMutex
        leaseClaimState = 'bound'; leaseReleaseState = 'active'; leaseReleaseJournalState = 'active'
        leasePath = $previewPath; port = $script:PreviewPort
        processId = $script:PreviewPid; processStartTicks = $script:PreviewStart
        processParentProcessId = $script:OriginalParentPid
        processParentProcessStartTicks = $script:OriginalParentStart
        leaseOwnerPid = $script:OriginalParentPid; leaseOwnerStartTicks = $script:OriginalParentStart
        state = 'run-owned-verified'; stdoutLog = ''; stderrLog = ''
        cleanupResult = 'infrastructure-failure'; error = 'synthetic active preview retained failure'
        identityVerified = $true; callerOwned = $false; processIdentityKnown = $true
        ownershipUncertain = $false; processTerminationProven = $false; processAbsent = $false
        # Match Start-VerifierOwnedPreview's live-server startup state. The
        # kernel lease already proves an active listener; server-level
        # inspection is only recorded later by Complete-VerifierPreview.
        listenerInspectionProven = $false; listenerAbsent = $false
        leaseListenerAbsent = $false; leaseProcessProofRequired = $true
        leaseListenerOwnerKind = 'kernel-transport'
        leaseListenerOwnerProof = 'run-owned-preview-http-sys-v1'
        leaseListenerOwnerEvidence = 'pid-4-system-http-sys'
    }
    $manifest = [pscustomobject]@{
        protocol = 'troubleshootjs-verifier-run-v1'; runId = $script:RunId
        repositoryIdentity = $script:RepositoryIdentity; worktreeRoot = $script:FixtureWorktree
        runRoot = $runRoot; runNamespaceRoot = $runNamespaceRoot
        evidenceDirectory = $evidenceDirectory; evidenceNamespaceRoot = $evidenceNamespaceRoot
        manifestPath = $manifestPath; createdUtc = $script:CreatedUtc
        baseUrl = $previewUrl; previewNonce = $script:PreviewNonce
        server = $server; leases = @($cdpLease, $previewLease)
        browserSessions = @($session); artifacts = @()
        cleanup = [pscustomobject]@{ state = 'infrastructure-failure'; completedUtc = ''; errors = @('synthetic retained failure') }
    }
    return [pscustomobject]@{
        WorktreeRoot = $script:FixtureWorktree; Manifest = $manifest
        CdpLease = $cdpLease; PreviewLease = $previewLease; Server = $server
        Session = $session; Receipt = $receipt
    }
}

function Get-ModuleErrorIsInfrastructure($ErrorRecord) {
    return [bool](& $script:ModuleInfo {
        param($failure)
        Test-VerifierInfrastructureError $failure
    } $ErrorRecord)
}

function Invoke-ActiveGraphAssociation($Graph) {
    [void](& $script:ModuleInfo {
        param($candidate)
        Assert-VerifierSerializedActivePreviewRecoveryAssociation `
            $candidate.CdpLease $candidate.PreviewLease $candidate.Server `
            $candidate.Session $candidate.Receipt $candidate.Manifest $candidate.WorktreeRoot
    } $Graph)
}

function Assert-AssociationRejected([string]$Name, $Graph) {
    $rejected = $false
    try { Invoke-ActiveGraphAssociation $Graph }
    catch { $rejected = Get-ModuleErrorIsInfrastructure $_ }
    Assert-Contract $rejected ('ACTIVE_GRAPH_NEGATIVE_ACCEPTED_' + $Name)
}

function Get-ModuleFunctionAst([System.Management.Automation.Language.ScriptBlockAst]$Root,
        [string]$Name) {
    $matches = @($Root.FindAll({
        param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -ceq $Name
    }, $true))
    if ($matches.Count -ne 1) { throw ('SOURCE_FUNCTION_COUNT_' + $Name) }
    return $matches[0]
}

function Get-FunctionCommandNames($FunctionAst) {
    $commands = @($FunctionAst.Body.FindAll({
        param($node) $node -is [System.Management.Automation.Language.CommandAst]
    }, $true) | Sort-Object { $_.Extent.StartOffset })
    return @($commands | ForEach-Object { $_.GetCommandName() } |
        Where-Object { -not [String]::IsNullOrWhiteSpace([string]$_) })
}

function Test-CommandIsVoidSuppressed($CommandAst) {
    $parent = $CommandAst.Parent
    while ($null -ne $parent) {
        if ($parent -is [System.Management.Automation.Language.ConvertExpressionAst]) {
            return ([string]$parent.Type.TypeName.FullName -ceq 'void')
        }
        if ($parent -is [System.Management.Automation.Language.StatementBlockAst]) {
            return $false
        }
        $parent = $parent.Parent
    }
    return $false
}

function Assert-CommandSequence([string]$Name, [string[]]$Observed,
        [string[]]$Expected) {
    $cursor = -1
    foreach ($command in $Expected) {
        $found = -1
        for ($index = $cursor + 1; $index -lt $Observed.Count; $index++) {
            if ($Observed[$index] -ceq $command) { $found = $index; break }
        }
        if ($found -lt 0) { throw ('SOURCE_ORDER_MISSING_' + $Name + '_' + $command) }
        $cursor = $found
    }
    $script:Assertions++
}

function Assert-TextSequence([string]$Name, [string]$Observed,
        [string[]]$Expected) {
    $cursor = -1
    foreach ($text in $Expected) {
        $found = $Observed.IndexOf($text, $cursor + 1, [StringComparison]::Ordinal)
        if ($found -lt 0) { throw ('SOURCE_TEXT_ORDER_MISSING_' + $Name + '_' + $text) }
        $cursor = $found
    }
    $script:Assertions++
}

function Assert-HasParameter($FunctionAst, [string]$Name, [string]$Code) {
    $found = $false
    foreach ($parameterAst in $FunctionAst.Parameters) {
        if ($null -ne $parameterAst.Name -and
                $parameterAst.Name.VariablePath.UserPath -ceq $Name) {
            $found = $true
            break
        }
    }
    Assert-Contract $found $Code
}

function Invoke-EnterRollbackFixture([ValidateSet('throw-second', 'deny-second',
        'wrong-type', 'deny-second-dispose-fails', 'first-release-fails',
        'first-dispose-fails')][string]$Mode) {
    $state = [hashtable]::Synchronized(@{
        Mode = $Mode; FactoryCalls = 0; FactoryNames = (New-Object Collections.ArrayList)
        Fakes = (New-Object Collections.ArrayList); ManifestWrites = 0
        Calls = (New-Object Collections.ArrayList)
    })
    $prefix = 'Q100Fixture-' + [Guid]::NewGuid().ToString('N')
    $cdp = [pscustomobject]@{
        Kind='cdp'; ClaimName=($prefix + '-Z'); ClaimOwnerPid=9101
        ClaimOwnerStartTicks=910100L; RunId=$prefix; ClaimMutex=$null
        RetainedRecoveryAuthority=$null
    }
    $preview = [pscustomobject]@{
        Kind='preview'; ClaimName=($prefix + '-A'); ClaimOwnerPid=9101
        ClaimOwnerStartTicks=910100L; RunId=$prefix; ClaimMutex=$null
        RetainedRecoveryAuthority=$null
    }
    $context = [pscustomobject]@{
        RunId=$prefix; ManifestPath=('C:\synthetic\' + $prefix + '\manifest.json')
        Server=[pscustomobject]@{
            ProcessParentProcessId=9101; ProcessParentProcessStartTicks=910100L
            Lease=$preview
        }
        LeaseRecords=@($cdp, $preview); BrowserSessions=@([pscustomobject]@{})
        ActivePreviewRecoveryAuthority=$null
    }
    $contextBefore = ConvertTo-Json -InputObject $context -Depth 8 -Compress
    $budget = [Diagnostics.Stopwatch]::StartNew()

    $overrides = @{}
    $overrides['Assert-VerifierRetainedActivePreviewBudget'] = {
        param($Budget, [string]$Phase)
        [void]$state.Calls.Add('budget:' + $Phase)
    }.GetNewClosure()
    $overrides['Assert-VerifierContextPhysicalResources'] = {
        param($Context)
        [void]$state.Calls.Add('physical')
    }.GetNewClosure()
    $overrides['Assert-VerifierRetainedBrowserRecoveryClaimRecord'] = {
        param($Context, $Lease)
        [void]$state.Calls.Add('claim:' + [string]$Lease.ClaimName)
    }.GetNewClosure()
    $overrides['Get-VerifierRetainedActivePreviewProcessIdentity'] = {
        param($Context, [switch]$RequireRecoveryAuthority)
        [void]$state.Calls.Add('preview-identity')
        return [pscustomobject]@{ Process=$null; Record=$null; ParentAbsence=$null }
    }.GetNewClosure()
    $overrides['Assert-VerifierActivePreviewRecoveryBrowserPreflight'] = {
        param($Context, $SessionRecord)
        [void]$state.Calls.Add('browser-preflight')
        return [pscustomobject]@{ Root=$null; Inspection=$null }
    }.GetNewClosure()
    $overrides['Assert-VerifierActivePreviewRecoveryParentAbsent'] = {
        param($Context, [string]$Role)
        [void]$state.Calls.Add('parent-absent:' + $Role)
        return [pscustomobject]@{ QueryProven=$true; Absent=$true; Replaced=$false }
    }.GetNewClosure()
    $overrides['Confirm-VerifierRecordedProcessAbsent'] = {
        param($Recorded, [string]$Role)
        [void]$state.Calls.Add('recorded-parent-absent:' + $Role)
        return [pscustomobject]@{ QueryProven=$true; Absent=$true; Replaced=$false }
    }.GetNewClosure()
    $overrides['Assert-VerifierDurableManifestContext'] = {
        param($Context)
        [void]$state.Calls.Add('durable-context')
    }.GetNewClosure()
    $overrides['Write-VerifierManifest'] = {
        param($Context)
        $state.ManifestWrites = [int]$state.ManifestWrites + 1
        [void]$state.Calls.Add('manifest-write')
    }.GetNewClosure()
    $overrides['New-VerifierRetainedActivePreviewRecoveryMutex'] = {
        param([string]$ClaimName)
        $state.FactoryCalls = [int]$state.FactoryCalls + 1
        [void]$state.FactoryNames.Add($ClaimName)
        if ($state.Mode -cin @('throw-second', 'first-release-fails', 'first-dispose-fails') -and
                $state.FactoryCalls -eq 2) {
            throw [InvalidOperationException]::new('fixture-second-mutex-construction-failure')
        }
        $waitResult = -not ($state.Mode -cin @('deny-second', 'deny-second-dispose-fails') -and
            $state.FactoryCalls -eq 2)
        $fake = [pscustomobject]@{
            WaitResult=$waitResult; WaitCalls=0; ReleaseCalls=0; DisposeCalls=0
            FailRelease=$false; FailDispose=$false
        }
        Add-Member -InputObject $fake -MemberType ScriptMethod -Name WaitOne -Value {
            param([int]$Milliseconds)
            $this.WaitCalls = [int]$this.WaitCalls + 1
            return [bool]$this.WaitResult
        }
        Add-Member -InputObject $fake -MemberType ScriptMethod -Name ReleaseMutex -Value {
            $this.ReleaseCalls = [int]$this.ReleaseCalls + 1
            if ($this.FailRelease) { throw [InvalidOperationException]::new('fixture-release-failure') }
        }
        Add-Member -InputObject $fake -MemberType ScriptMethod -Name Dispose -Value {
            $this.DisposeCalls = [int]$this.DisposeCalls + 1
            if ($this.FailDispose) { throw [InvalidOperationException]::new('fixture-dispose-failure') }
        }
        $fake.FailRelease = ($state.Mode -ceq 'first-release-fails' -and $state.FactoryCalls -eq 1)
        $fake.FailDispose = (($state.Mode -ceq 'deny-second-dispose-fails' -and $state.FactoryCalls -eq 2) -or
            ($state.Mode -ceq 'first-dispose-fails' -and $state.FactoryCalls -eq 1))
        [void]$state.Fakes.Add($fake)
        return $fake
    }.GetNewClosure()

    $result = & $script:ModuleInfo {
        param($FixtureContext, $CdpLease, $PreviewLease, $Budget, $Overrides,
            $State, $ContextBefore)
        $names = @($Overrides.Keys)
        $originals = @{}
        foreach ($name in $names) {
            $functionPath = 'Function:\' + $name
            $originals[$name] = (Get-Item -LiteralPath $functionPath -ErrorAction Stop).ScriptBlock
            Set-Item -LiteralPath $functionPath -Value $Overrides[$name] -ErrorAction Stop
        }
        $mapSignature = {
            param($Map)
            return (@($Map.GetEnumerator() | ForEach-Object {
                [string]$_.Key + '=' + [string]$_.Value
            } | Sort-Object) -join "`n")
        }.GetNewClosure()
        $claimsBefore = & $mapSignature $script:VerifierHeldPortClaims
        $mutexesBefore = & $mapSignature $script:VerifierHeldPortMutexes
        $failure = $null
        $isInfrastructure = $false
        try {
            [void](Enter-VerifierRetainedActivePreviewRecoveryClaims `
                $FixtureContext $CdpLease $PreviewLease `
                $FixtureContext.BrowserSessions[0] $Budget)
        } catch {
            $failure = $_.Exception.Message
            $isInfrastructure = [bool](Test-VerifierInfrastructureError $_)
        } finally {
            foreach ($name in $names) {
                Set-Item -LiteralPath ('Function:\' + $name) `
                    -Value $originals[$name] -ErrorAction Stop
            }
        }
        $claimsAfter = & $mapSignature $script:VerifierHeldPortClaims
        $mutexesAfter = & $mapSignature $script:VerifierHeldPortMutexes
        $contextAfter = ConvertTo-Json -InputObject $FixtureContext -Depth 8 -Compress
        return [pscustomobject]@{
            Failed=($null -ne $failure); Infrastructure=$isInfrastructure; Error=$failure
            FactoryCalls=$State.FactoryCalls; FactoryNames=@($State.FactoryNames.ToArray())
            Fakes=@($State.Fakes.ToArray()); ManifestWrites=$State.ManifestWrites
            MapClaimsUnchanged=($claimsBefore -ceq $claimsAfter)
            MapMutexesUnchanged=($mutexesBefore -ceq $mutexesAfter)
            ContextUnchanged=($contextAfter -ceq $contextBefore)
            CdpClaimMutexNull=($null -eq $CdpLease.ClaimMutex)
            PreviewClaimMutexNull=($null -eq $PreviewLease.ClaimMutex)
            CdpAuthorityNull=($null -eq $CdpLease.RetainedRecoveryAuthority)
            PreviewAuthorityNull=($null -eq $PreviewLease.RetainedRecoveryAuthority)
            ContextAuthorityNull=($null -eq $FixtureContext.ActivePreviewRecoveryAuthority)
        }
    } $context $cdp $preview $budget $overrides $state $contextBefore
    $budget.Stop()
    return $result
}

if ([String]::IsNullOrWhiteSpace($ModulePath)) {
    $ModulePath = Join-Path $PSScriptRoot '..\..\scripts\VerifierIsolation.psm1'
}
$moduleFullPath = [IO.Path]::GetFullPath($ModulePath)
if (-not (Test-Path -LiteralPath $moduleFullPath -PathType Leaf)) {
    throw 'MODULE_FILE_MISSING'
}
$sha = Get-Sha256Hex $moduleFullPath
if ([String]::IsNullOrWhiteSpace($ExpectedModuleSha256)) { throw 'MODULE_SOURCE_PIN_REQUIRED' }
if ($ExpectedModuleSha256 -notmatch '^[0-9a-fA-F]{64}$') { throw 'MODULE_SOURCE_PIN_MALFORMED' }
if ($sha -cne $ExpectedModuleSha256.ToLowerInvariant()) { throw 'MODULE_SOURCE_PIN_MISMATCH' }
$script:ModuleInfo = Import-Module -Name $moduleFullPath -Force -PassThru

$moduleTokens = $null
$moduleParseErrors = $null
$moduleAst = [System.Management.Automation.Language.Parser]::ParseFile(
    $moduleFullPath, [ref]$moduleTokens, [ref]$moduleParseErrors)
Assert-Contract ($moduleParseErrors.Count -eq 0) 'MODULE_CANDIDATE_PARSE_ERRORS'
$runContextAst = Get-ModuleFunctionAst $moduleAst 'New-VerifierRunContext'
$startOwnedPreviewAst = Get-ModuleFunctionAst $moduleAst 'Start-VerifierOwnedPreview'
$activeEntryAst = Get-ModuleFunctionAst $moduleAst `
    'Invoke-VerifierRetainedActivePreviewRecovery'
$activeFactoryAst = Get-ModuleFunctionAst $moduleAst `
    'New-VerifierRetainedBrowserRecoveryContext'
$activeEnterAst = Get-ModuleFunctionAst $moduleAst `
    'Enter-VerifierRetainedActivePreviewRecoveryClaims'
$activeMutexFactoryAst = Get-ModuleFunctionAst $moduleAst `
    'New-VerifierRetainedActivePreviewRecoveryMutex'
$authorityAst = Get-ModuleFunctionAst $moduleAst `
    'Get-VerifierRetainedActivePreviewRecoveryAuthority'
$terminalLeaseAssertionAst = Get-ModuleFunctionAst $moduleAst `
    'Assert-VerifierDurableLeaseTerminal'
$previewCleanupAst = Get-ModuleFunctionAst $moduleAst 'Complete-VerifierPreview'
$legacyEntryAst = Get-ModuleFunctionAst $moduleAst `
    'Invoke-VerifierRetainedBrowserRecovery'
Assert-Contract ($script:ModuleInfo.ExportedCommands.ContainsKey(
    'Invoke-VerifierRetainedActivePreviewRecovery')) 'ACTIVE_RECOVERY_ENTRY_NOT_EXPORTED'
Assert-Contract (-not $script:ModuleInfo.ExportedCommands.ContainsKey(
    'New-VerifierRetainedActivePreviewRecoveryMutex')) 'ACTIVE_MUTEX_FACTORY_EXPORTED'
Assert-HasParameter $activeFactoryAst 'AllowActivePreviewRecovery' `
    'ACTIVE_RECOVERY_FACTORY_SWITCH_MISSING'
$activeFactoryText = $activeFactoryAst.Extent.Text
Assert-Contract ($activeFactoryText.Contains("`$AllowActivePreviewRecovery -and -not `$runOwnedPreviewRecovery") -and
    $activeFactoryText.Contains('Assert-VerifierSerializedActivePreviewRecoveryAssociation')) `
    'ACTIVE_RECOVERY_FACTORY_NOT_NARROWLY_SELECTED'
Assert-Contract ($activeFactoryText.Contains('$sessions.Count -ne 1') -and
    $activeFactoryText.Contains('$leases.Count -ne 2') -and
    $activeFactoryText.Contains('$cdpLeaseRecords.Count -ne 1') -and
    $activeFactoryText.Contains('$previewLeaseRecords.Count -ne 1') -and
    $activeFactoryText.Contains('$serializedLease.leaseId -ceq $serializedPreviewLease.leaseId') -and
    $activeFactoryText.Contains('[int]$serializedLease.port -eq [int]$serializedPreviewLease.port') -and
    $activeFactoryText.Contains('$serializedLease.claimName -ceq $serializedPreviewLease.claimName') -and
    $activeFactoryText.Contains('Test-VerifierCanonicalWindowsPathValue $serializedLease.path $serializedPreviewLease.path')) `
    'ACTIVE_RECOVERY_FACTORY_CARDINALITY_OR_CLAIM_DISTINCTNESS_WEAKENED'
$factoryCommands = Get-FunctionCommandNames $activeFactoryAst
Assert-CommandSequence 'active-factory' $factoryCommands @(
    'Assert-VerifierSerializedBrowserSessionRecord',
    'Assert-VerifierSerializedServerRecord',
    'Get-VerifierRepositoryIdentity',
    'Assert-VerifierSerializedActivePreviewRecoveryAssociation',
    'Get-VerifierRetainedActivePreviewProcessIdentity'
)
$legacyEntryText = $legacyEntryAst.Extent.Text
Assert-Contract (-not $legacyEntryText.Contains('AllowActivePreviewRecovery') -and
    -not $legacyEntryText.Contains('Invoke-VerifierRetainedActivePreviewRecovery') -and
    $legacyEntryText.Contains('New-VerifierRetainedBrowserRecoveryContext $ManifestPath')) `
    'LEGACY_RECOVERY_ENTRY_CROSSES_ACTIVE_MODE'
$activeCommands = Get-FunctionCommandNames $activeEntryAst
$activeEntryText = $activeEntryAst.Extent.Text
Assert-Contract ($activeEntryText.Contains('New-VerifierRetainedBrowserRecoveryContext $ManifestPath') -and
    $activeEntryText.Contains('-AllowActivePreviewRecovery') -and
    $activeEntryText.Contains("Lane -cne 'boundReceiptParentExit'") -and
    $activeEntryText.Contains("BrowserCloseLane = [string]`$shutdownRecords[0].Lane")) `
    'ACTIVE_ENTRY_MODE_OR_BOUND_RECEIPT_PARENT_EXIT_PROOF_MISSING'
Assert-CommandSequence 'active-entry' $activeCommands @(
    'New-VerifierRetainedBrowserRecoveryContext',
    'Enter-VerifierRetainedActivePreviewRecoveryClaims',
    'Write-VerifierManifest',
    'Complete-VerifierPreview',
    'Assert-VerifierFreshTerminalPreviewProcessAndListenerAbsence',
    'Test-VerifierRetainedTerminalPreviewRecoveryScope',
    'Complete-VerifierBrowserSession',
    'Get-VerifierBrowserShutdownTimingRecords',
    'Complete-VerifierRun',
    'Exit-VerifierRetainedBrowserRecoveryLease'
)
Assert-TextSequence 'active-capability-transitions' $activeEntryText @(
    'Complete-VerifierPreview $context',
    'Assert-VerifierFreshTerminalPreviewProcessAndListenerAbsence $context',
    "`$authority.PreviewState = 'terminal'",
    "`$authority.CdpState = 'releasing'",
    'Complete-VerifierBrowserSession $context $session',
    "`$authority.CdpState = 'terminal'",
    'Complete-VerifierRun $context'
)
$enterText = $activeEnterAst.Extent.Text
Assert-Contract (-not $enterText.Contains('Write-VerifierManifest') -and
    $enterText.Contains('[StringComparer]::Ordinal.Compare') -and
    $enterText.Contains('WaitOne(0)') -and
    $enterText.Contains('ReleaseMutex') -and
    $enterText.Contains('.Dispose()')) 'PAIRED_LOCK_PREFLIGHT_OR_ROLLBACK_SOURCE_CONTRACT'
Assert-CommandSequence 'paired-lock-constructor' `
    (Get-FunctionCommandNames $activeEnterAst) @(
        'New-VerifierRetainedActivePreviewRecoveryMutex',
        'Assert-VerifierActivePreviewRecoveryParentAbsent'
    )
$activeMutexFactoryText = $activeMutexFactoryAst.Extent.Text
Assert-Contract ($activeMutexFactoryText.Contains('[Threading.Mutex]::new') -and
    $activeMutexFactoryText.Contains('$false') -and
    $activeMutexFactoryText.Contains('$ClaimName')) 'PRODUCTION_MUTEX_FACTORY_NOT_EXACT_NAMED_MUTEX'
Assert-TextSequence 'lock-type-before-map' $enterText @(
    'New-VerifierRetainedActivePreviewRecoveryMutex',
    '$item.Mutex.GetType() -ne [Threading.Mutex]',
    '$script:VerifierHeldPortClaims[[string]$lease.ClaimName] = [string]$Context.RunId',
    '$script:VerifierHeldPortMutexes[[string]$lease.ClaimName] = $item.Mutex'
)
$enterCommands = Get-FunctionCommandNames $activeEnterAst
Assert-CommandSequence 'paired-lock-entry' $enterCommands @(
    'Get-VerifierRetainedActivePreviewProcessIdentity',
    'Assert-VerifierActivePreviewRecoveryBrowserPreflight',
    'Confirm-VerifierRecordedProcessAbsent',
    'Get-VerifierRetainedActivePreviewRecoveryAuthority',
    'Get-VerifierRetainedBrowserRecoveryLeaseAuthority',
    'Assert-VerifierDurableManifestContext',
    'Assert-VerifierActivePreviewRecoveryHttpSysProof'
)
$authorityText = $authorityAst.Extent.Text
 $terminalLeaseCalls = @($authorityAst.Body.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.CommandAst] -and
        $node.GetCommandName() -ceq 'Assert-VerifierDurableLeaseTerminal'
}, $true))
$voidSuppressedTerminalLeaseCalls = @($terminalLeaseCalls | Where-Object {
    Test-CommandIsVoidSuppressed $_
})
Assert-Contract ($terminalLeaseAssertionAst.Extent.Text.Contains('return $Lease') -and
    $terminalLeaseCalls.Count -eq 2 -and
    $voidSuppressedTerminalLeaseCalls.Count -eq 2) `
    'SOURCE_ONLY_TERMINAL_LEASE_RETURN_NOT_SUPPRESSED_AT_BOTH_AUTHORITY_CALLS'
Assert-Contract ($authorityText.Contains('PreviewLockHeld') -and
    $authorityText.Contains('CdpLockHeld') -and
    $authorityText.Contains('PreviewLease') -and
    $authorityText.Contains('CdpLease') -and
    $authorityText.Contains('VerifierHeldPortClaims') -and
    $authorityText.Contains('VerifierHeldPortMutexes') -and
    $authorityText.Contains('[object]::ReferenceEquals')) `
    'ACTIVE_CAPABILITY_NOT_PAIRED_OR_EXACT'
Assert-Contract ($authorityText.Contains("PreviewState -eq 'active'") -and
    $authorityText.Contains("PreviewState -eq 'releasing'") -and
    $authorityText.Contains("PreviewState -cnotin @('active', 'releasing', 'terminal')") -and
    $authorityText.Contains("CdpState -in @('active', 'releasing')") -and
    $authorityText.Contains("CdpState -eq 'releasing'") -and
    $authorityText.Contains("CdpState -cnotin @('active', 'releasing', 'terminal')") -and
    $authorityText.Contains('previewOsReleased') -and
    $authorityText.Contains('previewTombstone') -and
    $authorityText.Contains('cdpOsReleased') -and
    $authorityText.Contains('cdpTombstone') -and
    $authorityText.Contains('Assert-VerifierDurableLeaseTerminal') -and
    $authorityText.Contains('terminal capability retained a live preview claim handle or map') -and
    $authorityText.Contains('terminal CDP state retained a live claim handle or map') -and
    $authorityText.Contains('OriginalParentProcessStartTicks')) `
    'ACTIVE_CAPABILITY_RELEASE_OR_TERMINAL_STATE_CHECKS_MISSING'
$previewCleanupText = $previewCleanupAst.Extent.Text
Assert-Contract ($runContextAst.Extent.Text.Contains(
        'ListenerInspectionProven = $false; ListenerAbsent = $null') -and
    $startOwnedPreviewAst.Extent.Text.Contains(
        'ListenerInspectionProven = $false; ListenerAbsent = $false') -and
    $previewCleanupText.Contains(
        '$Context.Server.ListenerInspectionProven = ($previewInspection.Success -and') -and
    $previewCleanupText.Contains('$Context.Server.ListenerAbsent = (-not $previewInspection.HasListeners)')) `
    'ACTIVE_PREVIEW_FIXTURE_LISTENER_STATE_NOT_BOUND_TO_STARTUP_AND_CLEANUP_OWNERS'
Assert-Contract ($previewCleanupText.Contains('Get-VerifierRetainedActivePreviewProcessIdentity') -and
    $previewCleanupText.Contains('PreviewState = ''releasing''') -and
    $previewCleanupText.Contains('Release-VerifierPortLease') -and
    -not $previewCleanupText.Contains('Stop-Process')) `
    'PREVIEW_RELEASE_NOT_SCOPED_TO_EXACT_NORMAL_CLEANUP'

$valid = New-ActiveGraph
Assert-Contract ($valid.Manifest -is [pscustomobject] -and
    $valid.Server -is [pscustomobject] -and $valid.CdpLease -is [pscustomobject] -and
    $valid.PreviewLease -is [pscustomobject] -and $valid.Session -is [pscustomobject] -and
    $valid.Receipt -is [pscustomobject] -and
    [object]::ReferenceEquals($valid.Server, $valid.Manifest.server) -and
    [object]::ReferenceEquals($valid.CdpLease, $valid.Manifest.leases[0]) -and
    [object]::ReferenceEquals($valid.PreviewLease, $valid.Manifest.leases[1]) -and
    [object]::ReferenceEquals($valid.Session, $valid.Manifest.browserSessions[0]) -and
    [object]::ReferenceEquals($valid.Receipt, $valid.Session.recoveryReceipt)) `
    'ACTIVE_GRAPH_NOT_SERIALIZED_CONSUMER_SHAPE_OR_ASSOCIATIONS_NOT_SHARED'
$positiveAccepted = $false
try { Invoke-ActiveGraphAssociation $valid; $positiveAccepted = $true } catch { }
Assert-Contract $positiveAccepted 'ACTIVE_GRAPH_POSITIVE_NOT_ACCEPTED'
Assert-Contract ($valid.Manifest.server.state -ceq 'run-owned-verified' -and
    $valid.Manifest.leases.Count -eq 2 -and $valid.Manifest.browserSessions.Count -eq 1) `
    'ACTIVE_GRAPH_FIXTURE_SCOPE_CHANGED'

$negativeCases = @(
    [pscustomobject]@{ Name='caller-owned-server'; Mutate={ param($g) $g.Server.owner='caller' } }
    [pscustomobject]@{ Name='terminal-server'; Mutate={ param($g) $g.Server.state='cleaned' } }
    [pscustomobject]@{ Name='server-cleanup-already-complete'; Mutate={ param($g) $g.Server.cleanupResult='complete' } }
    [pscustomobject]@{ Name='server-identity-not-verified'; Mutate={ param($g) $g.Server.identityVerified=$false } }
    [pscustomobject]@{ Name='server-caller-owned'; Mutate={ param($g) $g.Server.callerOwned=$true } }
    [pscustomobject]@{ Name='server-ownership-uncertain'; Mutate={ param($g) $g.Server.ownershipUncertain=$true } }
    [pscustomobject]@{ Name='server-claimed-process-absent'; Mutate={ param($g) $g.Server.processAbsent=$true } }
    [pscustomobject]@{ Name='server-listener-inspection-already-proven'; Mutate={ param($g) $g.Server.listenerInspectionProven=$true } }
    [pscustomobject]@{ Name='server-listener-absent'; Mutate={ param($g) $g.Server.listenerAbsent=$true } }
    [pscustomobject]@{ Name='server-run-id'; Mutate={ param($g) $g.Server.runId=[Guid]::NewGuid().ToString('N') } }
    [pscustomobject]@{ Name='manifest-run-id'; Mutate={ param($g) $g.Manifest.runId=[Guid]::NewGuid().ToString('N') } }
    [pscustomobject]@{ Name='manifest-preview-nonce'; Mutate={ param($g) $g.Manifest.previewNonce=('e' * 64) } }
    [pscustomobject]@{ Name='server-nonce'; Mutate={ param($g) $g.Server.nonce=('e' * 64) } }
    [pscustomobject]@{ Name='server-base-url'; Mutate={ param($g) $g.Server.baseUrl='http://127.0.0.1:1' } }
    [pscustomobject]@{ Name='server-repository-root'; Mutate={ param($g) $g.Server.repositoryRoot=$env:TEMP } }
    [pscustomobject]@{ Name='server-web-root'; Mutate={ param($g) $g.Server.webRoot=$env:TEMP } }
    [pscustomobject]@{ Name='server-preview-lease-id'; Mutate={ param($g) $g.Server.leaseId=[Guid]::NewGuid().ToString('N') } }
    [pscustomobject]@{ Name='server-preview-lease-kind'; Mutate={ param($g) $g.Server.leaseKind='cdp' } }
    [pscustomobject]@{ Name='server-preview-lease-path'; Mutate={ param($g) $g.Server.leasePath+='-alias' } }
    [pscustomobject]@{ Name='server-preview-lease-owner'; Mutate={ param($g) $g.Server.leaseOwnerPid++ } }
    [pscustomobject]@{ Name='server-preview-lease-owner-start'; Mutate={ param($g) $g.Server.leaseOwnerStartTicks++ } }
    [pscustomobject]@{ Name='server-listener-evidence'; Mutate={ param($g) $g.Server.leaseListenerOwnerEvidence='wrong-proof' } }
    [pscustomobject]@{ Name='server-process-id'; Mutate={ param($g) $g.Server.processId++ } }
    [pscustomobject]@{ Name='server-process-start'; Mutate={ param($g) $g.Server.processStartTicks++ } }
    [pscustomobject]@{ Name='preview-parent-id'; Mutate={ param($g) $g.Server.processParentProcessId++ } }
    [pscustomobject]@{ Name='preview-parent-start'; Mutate={ param($g) $g.PreviewLease.claimOwnerStartTicks++ } }
    [pscustomobject]@{ Name='preview-claim-parent'; Mutate={ param($g) $g.PreviewLease.claim.ownerPid++ } }
    [pscustomobject]@{ Name='cdp-claim-parent'; Mutate={ param($g) $g.CdpLease.claim.ownerStartTicks++ } }
    [pscustomobject]@{ Name='cdp-lease-not-bound'; Mutate={ param($g) $g.CdpLease.status='leased' } }
    [pscustomobject]@{ Name='cdp-lease-kind'; Mutate={ param($g) $g.CdpLease.kind='preview' } }
    [pscustomobject]@{ Name='cdp-lease-owner-start'; Mutate={ param($g) $g.CdpLease.claimOwnerStartTicks++ } }
    [pscustomobject]@{ Name='cdp-lease-release-not-active'; Mutate={ param($g) $g.CdpLease.releaseState='complete' } }
    [pscustomobject]@{ Name='cdp-lease-not-registered'; Mutate={ param($g) $g.CdpLease.registered=$false } }
    [pscustomobject]@{ Name='cdp-process-proof-not-required'; Mutate={ param($g) $g.CdpLease.processProofRequired=$false } }
    [pscustomobject]@{ Name='cdp-port-collision'; Mutate={ param($g) $g.CdpLease.port=$g.PreviewLease.port } }
    [pscustomobject]@{ Name='cdp-guid-case'; Mutate={ param($g) $g.CdpLease.leaseId=$g.CdpLease.leaseId.ToUpperInvariant() } }
    [pscustomobject]@{ Name='cdp-empty-guid'; Mutate={ param($g) $g.CdpLease.leaseId='00000000000000000000000000000000' } }
    [pscustomobject]@{ Name='cdp-claim-path'; Mutate={ param($g) $g.CdpLease.path+='-alias' } }
    [pscustomobject]@{ Name='cdp-claim-owner'; Mutate={ param($g) $g.CdpLease.claimOwnerPid++ } }
    [pscustomobject]@{ Name='preview-lease-owner'; Mutate={ param($g) $g.PreviewLease.claimOwnerPid++ } }
    [pscustomobject]@{ Name='preview-lease-owner-start'; Mutate={ param($g) $g.PreviewLease.claimOwnerStartTicks++ } }
    [pscustomobject]@{ Name='session-parent'; Mutate={ param($g) $g.Session.processParentProcessId++ } }
    [pscustomobject]@{ Name='session-parent-start'; Mutate={ param($g) $g.Session.processParentProcessStartTicks++ } }
    [pscustomobject]@{ Name='session-process-id'; Mutate={ param($g) $g.Session.processId++ } }
    [pscustomobject]@{ Name='session-process-start'; Mutate={ param($g) $g.Session.processStartTicks++ } }
    [pscustomobject]@{ Name='session-cdp-lease-path'; Mutate={ param($g) $g.Session.cdpLeasePath+='-alias' } }
    [pscustomobject]@{ Name='session-cdp-port'; Mutate={ param($g) $g.Session.cdpPort++ } }
    [pscustomobject]@{ Name='session-status-terminal'; Mutate={ param($g) $g.Session.status='complete' } }
    [pscustomobject]@{ Name='session-cleanup-terminal'; Mutate={ param($g) $g.Session.cleanupResult='complete' } }
    [pscustomobject]@{ Name='session-cleanup-terminal'; Mutate={ param($g) $g.Session.cleanupResult='complete' } }
    [pscustomobject]@{ Name='receipt-parent-start'; Mutate={ param($g) $g.Receipt.rootParentProcessStartTicks++ } }
    [pscustomobject]@{ Name='receipt-root-start'; Mutate={ param($g) $g.Receipt.rootProcessStartTicks++ } }
    [pscustomobject]@{ Name='receipt-cdp-port'; Mutate={ param($g) $g.Receipt.cdpPort++ } }
    [pscustomobject]@{ Name='receipt-lease-id'; Mutate={ param($g) $g.Receipt.leaseId=[Guid]::NewGuid().ToString('N') } }
    [pscustomobject]@{ Name='cdp-root-lineage'; Mutate={ param($g) $g.Receipt.rootProcessId++ } }
    [pscustomobject]@{ Name='close-already-attempted'; Mutate={ param($g) $g.Receipt.closeAttempted=$true } }
    [pscustomobject]@{ Name='receipt-not-bound'; Mutate={ param($g) $g.Receipt.state='issued' } }
    [pscustomobject]@{ Name='preview-not-bound'; Mutate={ param($g) $g.PreviewLease.status='leased' } }
    [pscustomobject]@{ Name='preview-claim-not-registered'; Mutate={ param($g) $g.PreviewLease.registered=$false } }
    [pscustomobject]@{ Name='preview-release-not-active'; Mutate={ param($g) $g.PreviewLease.releaseState='complete' } }
    [pscustomobject]@{ Name='preview-process-not-required'; Mutate={ param($g) $g.PreviewLease.processProofRequired=$false } }
    [pscustomobject]@{ Name='preview-listener-not-known'; Mutate={ param($g) $g.PreviewLease.listenerInspectionKnown=$false } }
    [pscustomobject]@{ Name='preview-listener-not-owned-http-sys'; Mutate={ param($g) $g.PreviewLease.listenerOwnerProof='diagnostics-process-start-v1' } }
    [pscustomobject]@{ Name='preview-listener-evidence'; Mutate={ param($g) $g.PreviewLease.listenerOwnerEvidence='wrong-proof' } }
    [pscustomobject]@{ Name='preview-listener-process-id'; Mutate={ param($g) $g.PreviewLease.listenerProcessId=5 } }
    [pscustomobject]@{ Name='preview-bound-process-id'; Mutate={ param($g) $g.PreviewLease.boundProcessId++ } }
    [pscustomobject]@{ Name='preview-bound-process-start'; Mutate={ param($g) $g.PreviewLease.boundProcessStartTicks++ } }
    [pscustomobject]@{ Name='preview-port'; Mutate={ param($g) $g.PreviewLease.port++ } }
    [pscustomobject]@{ Name='preview-empty-guid'; Mutate={ param($g) $g.PreviewLease.leaseId='00000000000000000000000000000000' } }
    [pscustomobject]@{ Name='preview-separator-guid'; Mutate={ param($g) $g.PreviewLease.leaseId='01234567-89ab-cdef-0123-456789abcdef' } }
    [pscustomobject]@{ Name='preview-mutex-name'; Mutate={ param($g) $g.PreviewLease.claimName='wrong-mutex' } }
    [pscustomobject]@{ Name='preview-guid-case'; Mutate={ param($g) $g.PreviewLease.leaseId=$g.PreviewLease.leaseId.ToUpperInvariant() } }
    [pscustomobject]@{ Name='preview-claim-path'; Mutate={ param($g) $g.PreviewLease.path+='-alias' } }
)
foreach ($case in $negativeCases) {
    $candidate = New-ActiveGraph
    & $case.Mutate $candidate
    Assert-AssociationRejected $case.Name $candidate
}

foreach ($mode in @('throw-second', 'deny-second', 'wrong-type',
        'deny-second-dispose-fails', 'first-release-fails', 'first-dispose-fails')) {
    $rollback = Invoke-EnterRollbackFixture $mode
    Assert-Contract $rollback.Failed ('PAIR_LOCK_ROLLBACK_DID_NOT_FAIL_' + $mode)
    Assert-Contract $rollback.Infrastructure ('PAIR_LOCK_ROLLBACK_NOT_INFRASTRUCTURE_' + $mode)
    Assert-Contract ($rollback.FactoryCalls -eq 2 -and
        $rollback.FactoryNames.Count -eq 2 -and
        [StringComparer]::Ordinal.Compare($rollback.FactoryNames[0], $rollback.FactoryNames[1]) -lt 0) `
        ('PAIR_LOCK_ORDER_OR_ATTEMPT_COUNT_CHANGED_' + $mode)
    Assert-Contract ($rollback.ManifestWrites -eq 0 -and
        $rollback.MapClaimsUnchanged -and $rollback.MapMutexesUnchanged -and
        $rollback.ContextUnchanged -and $rollback.CdpClaimMutexNull -and
        $rollback.PreviewClaimMutexNull -and $rollback.CdpAuthorityNull -and
        $rollback.PreviewAuthorityNull -and $rollback.ContextAuthorityNull) `
        ('PAIR_LOCK_FAILURE_LEFT_DURABLE_OR_AUTHORITY_STATE_' + $mode)
    if ($mode -cin @('throw-second', 'first-release-fails', 'first-dispose-fails')) {
        Assert-Contract ($rollback.Fakes.Count -eq 1 -and
            $rollback.Fakes[0].WaitCalls -eq 1 -and
            $rollback.Fakes[0].ReleaseCalls -eq 1) `
            ('FIRST_LOCK_ROLLBACK_CALL_COUNTS_CHANGED_' + $mode)
        if ($mode -ceq 'throw-second') {
            Assert-Contract ($rollback.Fakes[0].DisposeCalls -eq 1) `
                'SECOND_CONSTRUCTOR_FAILURE_DID_NOT_DISPOSE_FIRST_FAKE_LOCK'
        } elseif ($mode -ceq 'first-release-fails') {
            Assert-Contract ($rollback.Fakes[0].DisposeCalls -eq 0 -and
                $rollback.Error -match 'could not release a partially acquired') `
                'FIRST_LOCK_RELEASE_FAILURE_WAS_NOT_RETAINED_AS_UNPROVEN'
        } else {
            Assert-Contract ($rollback.Fakes[0].DisposeCalls -eq 1 -and
                $rollback.Error -match 'could not dispose a partially acquired') `
                'FIRST_LOCK_DISPOSE_FAILURE_WAS_NOT_RETAINED_AS_UNPROVEN'
        }
    } elseif ($mode -cin @('deny-second', 'deny-second-dispose-fails')) {
        Assert-Contract ($rollback.Fakes.Count -eq 2 -and
            $rollback.Fakes[0].WaitCalls -eq 1 -and
            $rollback.Fakes[0].ReleaseCalls -eq 1 -and
            $rollback.Fakes[0].DisposeCalls -eq 1 -and
            $rollback.Fakes[1].WaitCalls -eq 1 -and
            $rollback.Fakes[1].ReleaseCalls -eq 0 -and
            $rollback.Fakes[1].DisposeCalls -eq 1) `
            'SECOND_LOCK_DENIAL_DID_NOT_DISPOSE_UNOWNED_AND_ROLLBACK_FIRST'
        if ($mode -ceq 'deny-second-dispose-fails') {
            Assert-Contract ($rollback.Error -match 'could not dispose its unowned handle') `
                'UNOWNED_SECOND_HANDLE_DISPOSE_FAILURE_WAS_NOT_REPORTED'
        }
    } else {
        Assert-Contract ($rollback.Fakes.Count -eq 2 -and
            @($rollback.Fakes | Where-Object {
                $_.WaitCalls -eq 1 -and $_.ReleaseCalls -eq 1 -and $_.DisposeCalls -eq 1
            }).Count -eq 2) 'NONMUTEX_HANDLES_WERE_NOT_ROLLED_BACK_BEFORE_MAP_REGISTRATION'
    }
}

$moduleShaAfter = Get-Sha256Hex $moduleFullPath
if ($moduleShaAfter -cne $sha) { throw 'MODULE_SOURCE_CHANGED_DURING_FIXTURE' }
Write-Output ('ACTIVE_PREVIEW_RECOVERY_MODULE_SHA256 before=' + $sha + ' after=' + $moduleShaAfter)
Write-Output ('PURE_FIXTURE_ASSERTIONS=' + $script:Assertions)
Write-Output ('PASS: active preview recovery synthetic admission/source-order fixtures; assertions=' +
    $script:Assertions + '; scope=synthetic-source-contract-only; host-recovery=NOT_RUN')

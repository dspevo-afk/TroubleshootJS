[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ModulePath,
    [Parameter(Mandatory=$true)][string]$ExpectedModuleSha256,
    [Parameter(Mandatory=$true)][long]$ExpectedModuleBytes,
    [Parameter(Mandatory=$true)][string]$FixtureSourcePath,
    [Parameter(Mandatory=$true)][string]$ExpectedFixtureSourceSha256,
    [string]$ExpectedConsoleHostSha256 = 'dd5c3514132d92052004d302c66c756ffb6beef572de6538b4fc55f62579eb11',
    [Parameter(Mandatory=$true)][string]$EvidenceRoot,
    [ValidateSet('Smoke','Suite')][string]$Mode = 'Suite'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$cases = New-Object 'System.Collections.Generic.List[object]'
$rootReady = $false

function Require([bool]$Condition, [string]$Reason) {
    if (-not $Condition) { throw $Reason }
}
function Sha([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-','').ToLowerInvariant() }
    finally { $algorithm.Dispose(); $stream.Dispose() }
}
function Save([string]$Name, $Value) {
    $body = [Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 20) + "`n")
    $stream = [IO.File]::Open((Join-Path $EvidenceRoot $Name), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($body,0,$body.Length) } finally { $stream.Dispose() }
}
function Save-Case([string]$Directory, [string]$Name, $Value) {
    $body = [Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 20) + "`n")
    $stream = [IO.File]::Open((Join-Path $Directory $Name), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($body,0,$body.Length) } finally { $stream.Dispose() }
}
function Get-ExceptionMessageChain($ErrorRecord) {
    $messages = New-Object 'System.Collections.Generic.List[string]'
    $exception = $ErrorRecord.Exception
    while ($null -ne $exception) {
        [void]$messages.Add([string]$exception.Message)
        $exception = $exception.InnerException
    }
    return @($messages.ToArray())
}
function Require-ExceptionMessage($ErrorRecord, [string]$ExpectedMessage, [string]$Label) {
    $messages = @(Get-ExceptionMessageChain $ErrorRecord)
    Require (@($messages | Where-Object { $_ -ceq $ExpectedMessage }).Count -gt 0) ($Label + ' reason')
}
function Read-State([string]$Path, [Diagnostics.Stopwatch]$Deadline) {
    $lastError = $null
    while ($Deadline.ElapsedMilliseconds -lt 10000) {
        if ([IO.File]::Exists($Path)) {
            try {
                $text = [IO.File]::ReadAllText($Path)
                if (-not [String]::IsNullOrWhiteSpace($text)) {
                    return ($text | ConvertFrom-Json -ErrorAction Stop)
                }
            } catch { $lastError = $_ }
        }
        Start-Sleep -Milliseconds 40
    }
    if ($null -ne $lastError) { throw 'containment child state did not become complete JSON before the deadline' }
    throw 'containment child state was not created before the deadline'
}
function Equal-Strings($Actual, $Expected, [string]$Label) {
    Require (@($Actual).Count -eq @($Expected).Count) ($Label + ' count')
    for ($i=0; $i -lt @($Expected).Count; $i++) {
        Require ($Actual[$i] -is [string] -and $Actual[$i] -ceq $Expected[$i]) ($Label + ' value')
    }
}
function Read-IdentityState([string]$Name, [string]$ModeName, [string]$Nonce,
                            [string[]]$ExpectedCommandArgs, [bool]$ExpectedInJob, [long]$ExpectedPid) {
    $path = Join-Path $stateDirectory ($ModeName + '.state.json')
    $deadline = [Diagnostics.Stopwatch]::StartNew()
    $state = Read-State $path $deadline
    Require ($state.mode -ceq $ModeName) ($Name + ' mode')
    Require ($state.nonce -ceq $Nonce) ($Name + ' nonce')
    Require ($state.inExpectedJob -is [bool] -and $state.inExpectedJob -eq $ExpectedInJob) ($Name + ' job membership witness')
    Require ([long]$state.pid -gt 0 -and [long]$state.creationFileTime -gt 0) ($Name + ' process identity')
    Require ($state.imageSha256 -ceq $childImageSha256) ($Name + ' actual image SHA')
    Require ([String]::Equals([IO.Path]::GetFullPath($state.imagePath), $ChildExecutable, [StringComparison]::OrdinalIgnoreCase)) ($Name + ' actual image path')
    if ($ExpectedPid -gt 0) { Require ([long]$state.pid -eq $ExpectedPid) ($Name + ' launch PID') }
    $expectedCommand = @($ChildExecutable) + @($ExpectedCommandArgs)
    Equal-Strings @($state.argv) $expectedCommand ($Name + ' observed argv')
    $parsed = @(& $owner { param($raw) ConvertFrom-VerifierWindowsCommandLine $raw } $state.commandLine)
    Equal-Strings $parsed $expectedCommand ($Name + ' raw command line')
    return [pscustomobject]@{
        State=$state
        Path=$path
        ProcessId=[long]$state.pid
        StartTicks=([long]$state.creationFileTime + 504911232000000000L)
    }
}
function Assert-MemberIds($Actual, [long[]]$Expected, [string]$Label) {
    $actualSorted = @($Actual | ForEach-Object { [long]$_ } | Sort-Object)
    $expectedSorted = @($Expected | Sort-Object)
    Require ($actualSorted.Count -eq $expectedSorted.Count) ($Label + ' count')
    for ($i=0; $i -lt $expectedSorted.Count; $i++) {
        Require ($actualSorted[$i] -eq $expectedSorted[$i]) ($Label + ' pid')
    }
}
function Assert-ConsoleHostArtifact {
    $expectedPath = [IO.Path]::GetFullPath((Join-Path ([Environment]::SystemDirectory) 'conhost.exe'))
    Require (Test-Path -LiteralPath $expectedPath -PathType Leaf) 'system console host image missing'
    $imageSha256 = Sha $expectedPath
    Require ($imageSha256 -ceq $ExpectedConsoleHostSha256) 'console host image SHA'
    $signature = Get-AuthenticodeSignature -FilePath $expectedPath
    Require ($signature.Status -eq [Management.Automation.SignatureStatus]::Valid) 'console host signature validity'
    Require ($null -ne $signature.SignerCertificate) 'console host signing certificate'
    Require ($signature.SignerCertificate.Thumbprint -ceq '667BBA1E1F478221AA65DE4F75187A751B03A453') 'console host signer thumbprint'
    Require ($signature.SignerCertificate.Subject.StartsWith('CN=Microsoft Windows, O=Microsoft Corporation',[StringComparison]::Ordinal)) 'console host signer subject'
    return [pscustomobject]@{ imagePath=$expectedPath; imageSha256=$imageSha256; signerThumbprint=$signature.SignerCertificate.Thumbprint; signatureStatus=[string]$signature.Status }
}
function Get-ConsoleHostIdentity([long]$ProcessId, [long]$ParentStartTicks) {
    $process = [Diagnostics.Process]::GetProcessById([int]$ProcessId)
    try {
        $startTicks = [long]$process.StartTime.ToUniversalTime().Ticks
        $imagePath = [IO.Path]::GetFullPath($process.MainModule.FileName)
    } finally { $process.Dispose() }
    $artifact = Assert-ConsoleHostArtifact
    Require ([String]::Equals($imagePath,$artifact.imagePath,[StringComparison]::OrdinalIgnoreCase)) 'extra job member is not the system console host'
    Require ($startTicks -ge $ParentStartTicks) 'console host creation ordering'
    return [pscustomobject]@{ processId=$ProcessId; startTicks=$startTicks; imagePath=$imagePath; imageSha256=$artifact.imageSha256; signerThumbprint=$artifact.signerThumbprint; signatureStatus=$artifact.signatureStatus }
}
function Observe-JobMembers($Job, [long]$ParentPid, [long]$ParentStartTicks,
                            [long]$DescendantPid, [long]$DescendantStartTicks,
                            [string]$Label) {
    $members = @($Job.GetMemberProcessIds() | ForEach-Object { [long]$_ })
    Require ($members.Count -ge 2 -and $members.Count -le 4) ($Label + ' member count')
    $unique = @($members | Select-Object -Unique)
    Require ($unique.Count -eq $members.Count) ($Label + ' duplicate member pid')
    Require ($members -contains $ParentPid) ($Label + ' parent membership')
    Require ($members -contains $DescendantPid) ($Label + ' descendant membership')
    $extras = @($members | Where-Object { $_ -ne $ParentPid -and $_ -ne $DescendantPid })
    Require ($extras.Count -le 2) ($Label + ' additional member count')
    $support = New-Object 'System.Collections.Generic.List[object]'
    foreach ($processId in $extras) {
        $identity = Get-ConsoleHostIdentity $processId $ParentStartTicks
        [void]$support.Add($identity)
    }
    return [pscustomobject]@{ memberIds=$members; consoleHosts=@($support.ToArray()) }
}
function New-MemberObservation([string]$Role, [long]$ProcessId, [long]$StartTicks,
                               [string]$ExpectedImagePath, [string]$ExpectedImageSha256) {
    $process = [Diagnostics.Process]::GetProcessById([int]$ProcessId)
    try {
        $handle = $process.Handle
        Require ($handle -ne [IntPtr]::Zero -and $handle -ne [IntPtr]::new(-1)) ($Role + ' observation handle')
        $observedTicks = [long]$process.StartTime.ToUniversalTime().Ticks
        $imagePath = [IO.Path]::GetFullPath($process.MainModule.FileName)
        $imageSha256 = Sha $imagePath
        Require ($observedTicks -eq $StartTicks) ($Role + ' observation identity')
        Require ([String]::Equals($imagePath,$ExpectedImagePath,[StringComparison]::OrdinalIgnoreCase)) ($Role + ' observation image path')
        Require ($imageSha256 -ceq $ExpectedImageSha256) ($Role + ' observation image SHA')
        return [pscustomobject]@{ role=$Role; processId=$ProcessId; startTicks=$observedTicks; imagePath=$imagePath; imageSha256=$imageSha256; Process=$process; Handle=$handle }
    } catch {
        $process.Dispose()
        throw
    }
}
function Require-PidLive([long]$ProcessId, [long]$StartTicks, [string]$Label) {
    $process = [Diagnostics.Process]::GetProcessById([int]$ProcessId)
    try {
        Require ([long]$process.StartTime.ToUniversalTime().Ticks -eq $StartTicks) ($Label + ' creation identity')
    } finally { $process.Dispose() }
}
function Wait-JobAndHandles($Job, $MemberObservations, [Diagnostics.Stopwatch]$Deadline, [int]$TimeoutMilliseconds) {
    while ($Deadline.ElapsedMilliseconds -lt $TimeoutMilliseconds) {
        $members = @($Job.GetMemberProcessIds())
        $allExited = $true
        foreach ($observation in $MemberObservations) {
            $process = $observation.Process
            $null = $process.WaitForExit(0)
            $process.Refresh()
            if (-not $process.HasExited) { $allExited = $false }
        }
        if ($members.Count -eq 0 -and $allExited) {
            $exitStatuses = New-Object 'System.Collections.Generic.List[object]'
            foreach ($observation in $MemberObservations) {
                $exitCode = [int]$observation.Process.ExitCode
                Require ($exitCode -eq 2) ($observation.role + ' termination exit code')
                [void]$exitStatuses.Add([ordered]@{ role=$observation.role; processId=$observation.processId; startTicks=$observation.startTicks; exitCode=$exitCode; status='EXIT_OBSERVED' })
            }
            Require ($Deadline.ElapsedMilliseconds -lt $TimeoutMilliseconds) 'containment job closure exceeded the shared bound'
            return [pscustomobject]@{ closureMilliseconds=$Deadline.Elapsed.TotalMilliseconds; memberIds=$members; allRetainedHandlesSignaled=$true; exitStatuses=@($exitStatuses.ToArray()) }
        }
        Start-Sleep -Milliseconds 40
    }
    throw 'containment job and retained member handles did not close within the shared bound'
}
function Finish-Case([string]$Name, $Watch, $Facts) {
    $Watch.Stop()
    $row = [ordered]@{ name=$Name; status='PASS'; elapsedMilliseconds=$Watch.Elapsed.TotalMilliseconds; facts=$Facts }
    $cases.Add($row)
    Save ($Name + '.case.json') $row
}
function Assert-BaseException($ErrorRecord, [string]$ExpectedType, [string]$ExpectedMessage,
                               [string]$Label, [string]$MessageMode = 'Exact',
                               [int]$NativeErrorCode = -1, [string]$ParameterName = '',
                               [string]$ObjectName = '') {
    Require ($null -ne $ErrorRecord -and $null -ne $ErrorRecord.Exception) ($Label + ' missing exception')
    $exception = $ErrorRecord.Exception.GetBaseException()
    Require ($exception.GetType().FullName -ceq $ExpectedType) ($Label + ' exception type')
    if ($MessageMode -ceq 'Exact') { Require ($exception.Message -ceq $ExpectedMessage) ($Label + ' exception message') }
    elseif ($MessageMode -ceq 'Prefix') { Require ($exception.Message.StartsWith($ExpectedMessage,[StringComparison]::Ordinal)) ($Label + ' exception message prefix') }
    else { throw 'unsupported exception message comparison mode' }
    if ($NativeErrorCode -ge 0) { Require ($exception -is [ComponentModel.Win32Exception] -and $exception.NativeErrorCode -eq $NativeErrorCode) ($Label + ' native error code') }
    if ($ParameterName.Length -gt 0) { Require ($exception.ParamName -ceq $ParameterName) ($Label + ' parameter name') }
    if ($ObjectName.Length -gt 0) { Require ($exception.ObjectName -ceq $ObjectName) ($Label + ' disposed object name') }
    $actualNativeErrorCode = $null
    $actualParameterName = $null
    $actualObjectName = $null
    if ($exception -is [ComponentModel.Win32Exception]) { $actualNativeErrorCode = $exception.NativeErrorCode }
    if ($exception -is [ArgumentException]) { $actualParameterName = $exception.ParamName }
    if ($exception -is [ObjectDisposedException]) { $actualObjectName = $exception.ObjectName }
    return [ordered]@{
        exceptionType=$exception.GetType().FullName; exceptionMessage=$exception.Message
        nativeErrorCode=$actualNativeErrorCode; parameterName=$actualParameterName
        objectName=$actualObjectName; exceptionMessageChain=@(Get-ExceptionMessageChain $ErrorRecord)
    }
}
function Run-GuardCase([string]$Name, [scriptblock]$Action) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $context = [pscustomobject]@{
        phase='initialize'; jobName=$null; job=$null; observer=$null; facts=$null
        memberIds=@(); terminationRequested=$false; disposedByCase=$false
    }
    $primaryFailure = $null
    $primaryFailurePhase = $null
    $cleanupFailures = New-Object 'System.Collections.Generic.List[string]'
    try { & $Action $context }
    catch {
        $primaryFailure = $_
        $primaryFailurePhase = $context.phase
        try { Save ($Name + '.primary-failure.json') @{ status='FAIL_NOT_PASS'; phase=$primaryFailurePhase; jobName=$context.jobName; knownMemberProcessIds=@($context.memberIds); exceptionType=$primaryFailure.Exception.GetType().FullName; exceptionMessageChain=@(Get-ExceptionMessageChain $primaryFailure) } }
        catch { [void]$cleanupFailures.Add('primary failure evidence write failed') }
    }
    if ($null -ne $context.observer) {
        try { $context.observer.Dispose() } catch { [void]$cleanupFailures.Add('query observer dispose failed') }
    }
    $cleanupMemberIds = @($context.memberIds)
    if ($null -ne $context.job -and -not $context.disposedByCase) {
        try {
            $cleanupMemberIds = @($context.job.GetMemberProcessIds())
            Require ($cleanupMemberIds.Count -eq 0) 'negative guard case left unexpected job members'
        } catch { [void]$cleanupFailures.Add('empty-job cleanup membership proof failed') }
        try { $context.job.Dispose() } catch { [void]$cleanupFailures.Add('creator job dispose failed') }
    }
    $cleanupStatus = 'PASS'
    if ($cleanupFailures.Count -gt 0) { $cleanupStatus = 'FAIL_NOT_PASS' }
    try {
        Save ($Name + '.cleanup-result.json') @{
            status=$cleanupStatus; jobName=$context.jobName; knownMemberProcessIds=$cleanupMemberIds
            disposedByCase=$context.disposedByCase; terminationRequested=$context.terminationRequested
            cleanupFailures=@($cleanupFailures.ToArray())
        }
    } catch { [void]$cleanupFailures.Add('cleanup result evidence write failed'); $cleanupStatus='FAIL_NOT_PASS' }
    if ($cleanupFailures.Count -gt 0) {
        if ($null -ne $primaryFailure) { throw ('guard cleanup failed after primary failure in phase ' + $primaryFailurePhase) }
        throw ('guard cleanup failed: ' + [String]::Join('; ',$cleanupFailures.ToArray()))
    }
    if ($null -ne $primaryFailure) { throw $primaryFailure.Exception }
    Finish-Case $Name $watch $context.facts
}
function Run-EmptyCreatorGuardCase {
    Run-GuardCase 'empty-creator-termination' {
        param($context)
        $context.phase = 'create empty creator job'
        $context.jobName = 'Local\TroubleshootJS.Verifier.ContainmentContract.' + [guid]::NewGuid().ToString('N')
        $context.job = $jobType::CreateNew($context.jobName)
        $context.memberIds = @($context.job.GetMemberProcessIds())
        Require ($context.memberIds.Count -eq 0) 'new creator job was not empty'
        $context.phase = 'open empty query observer'
        $context.observer = $jobType::OpenExisting($context.jobName)
        Require (@($context.observer.GetMemberProcessIds()).Count -eq 0) 'query observer did not report empty job'
        $context.phase = 'reject query-only termination request on empty job'
        $observerFailure = $null
        try { $context.observer.TerminateOwned([uint32]2) } catch { $observerFailure = $_ }
        $observerFacts = Assert-BaseException $observerFailure 'System.InvalidOperationException' 'Only a creator-owned containment job may request termination.' 'empty query observer' 'Exact'
        Require (@($context.observer.GetMemberProcessIds()).Count -eq 0) 'query denial changed empty job membership'
        $context.phase = 'request and retry creator termination on empty job'
        $context.job.TerminateOwned([uint32]2)
        $context.terminationRequested = $true
        $context.job.TerminateOwned([uint32]2)
        Require (@($context.job.GetMemberProcessIds()).Count -eq 0) 'creator retry changed empty job membership'
        Require (@($context.observer.GetMemberProcessIds()).Count -eq 0) 'query observer after creator retry reported members'
        $context.facts = [ordered]@{ jobName=$context.jobName; emptyBefore=$true; queryOnlyTermination=$observerFacts; creatorRequests=2; creatorAndObserverRemainEmpty=$true }
    }
}
function Run-BlankNameGuardCase {
    Run-GuardCase 'blank-job-names' {
        param($context)
        $context.phase = 'reject blank creator name'
        $blankCreate = $null
        try { $null = $jobType::CreateNew(' ') } catch { $blankCreate = $_ }
        $expectedArgumentMessage = 'A browser containment job needs a name.' + [Environment]::NewLine + 'Parameter name: name'
        $createFacts = Assert-BaseException $blankCreate 'System.ArgumentException' $expectedArgumentMessage 'blank creator name' 'Exact' -1 'name'
        $context.phase = 'reject blank query name'
        $blankOpen = $null
        try { $null = $jobType::OpenExisting([String]::Empty) } catch { $blankOpen = $_ }
        $openFacts = Assert-BaseException $blankOpen 'System.ArgumentException' $expectedArgumentMessage 'blank query name' 'Exact' -1 'name'
        $context.facts = [ordered]@{ createNew=$createFacts; openExisting=$openFacts; jobsCreated=0 }
    }
}
function Run-DuplicateNameGuardCase {
    Run-GuardCase 'duplicate-job-name' {
        param($context)
        $context.phase = 'create original empty owner'
        $context.jobName = 'Local\TroubleshootJS.Verifier.ContainmentContract.' + [guid]::NewGuid().ToString('N')
        $context.job = $jobType::CreateNew($context.jobName)
        $context.memberIds = @($context.job.GetMemberProcessIds())
        Require ($context.memberIds.Count -eq 0) 'original duplicate-test job was not empty'
        $context.phase = 'reject duplicate creator name'
        $duplicateFailure = $null
        try { $null = $jobType::CreateNew($context.jobName) } catch { $duplicateFailure = $_ }
        $duplicateFacts = Assert-BaseException $duplicateFailure 'System.InvalidOperationException' 'Browser containment job name was already present.' 'duplicate creator name' 'Exact'
        Require (@($context.job.GetMemberProcessIds()).Count -eq 0) 'original owner changed after duplicate denial'
        $context.phase = 'prove original owner remains usable'
        $context.job.TerminateOwned([uint32]2)
        $context.terminationRequested = $true
        Require (@($context.job.GetMemberProcessIds()).Count -eq 0) 'original owner not empty after duplicate denial'
        $context.facts = [ordered]@{ jobName=$context.jobName; duplicateCreate=$duplicateFacts; originalOwnerUsable=$true; originalOwnerEmpty=$true }
    }
}
function Run-MissingOpenGuardCase {
    Run-GuardCase 'missing-open-name' {
        param($context)
        $context.phase = 'reject missing query name'
        $context.jobName = 'Local\TroubleshootJS.Verifier.ContainmentContract.' + [guid]::NewGuid().ToString('N')
        $missingFailure = $null
        try { $null = $jobType::OpenExisting($context.jobName) } catch { $missingFailure = $_ }
        $missingFacts = Assert-BaseException $missingFailure 'System.ComponentModel.Win32Exception' 'Could not open browser containment job.' 'missing query name' 'Prefix' 2
        $context.facts = [ordered]@{ jobName=$context.jobName; openExisting=$missingFacts; handleCreated=$false }
    }
}
function Run-MissingExecutableGuardCase {
    Run-GuardCase 'missing-executable' {
        param($context)
        $context.phase = 'create empty owner for failed executable launch'
        $context.jobName = 'Local\TroubleshootJS.Verifier.ContainmentContract.' + [guid]::NewGuid().ToString('N')
        $context.job = $jobType::CreateNew($context.jobName)
        $missingExecutable = Join-Path $EvidenceRoot ('VerifierMissingExecutable.' + [guid]::NewGuid().ToString('N') + '.exe')
        Require (-not [IO.File]::Exists($missingExecutable) -and -not [IO.Directory]::Exists($missingExecutable)) 'missing executable fixture path unexpectedly exists'
        $missingCommand = ConvertTo-VerifierArgumentString @($missingExecutable,'unused')
        $context.phase = 'observe native missing executable launch failure'
        $launchFailure = $null
        try { $null = $context.job.Launch($missingExecutable,$missingCommand,$EvidenceRoot) } catch { $launchFailure = $_ }
        $launchFacts = Assert-BaseException $launchFailure 'System.ComponentModel.Win32Exception' 'Could not atomically launch browser in its containment job.' 'missing executable launch' 'Prefix' 2
        $context.memberIds = @($context.job.GetMemberProcessIds())
        Require ($context.memberIds.Count -eq 0) 'failed executable launch added a job member'
        $context.phase = 'prove creator remains usable after failed launch'
        $context.job.TerminateOwned([uint32]2)
        $context.terminationRequested = $true
        Require (@($context.job.GetMemberProcessIds()).Count -eq 0) 'creator not empty after failed launch'
        $context.facts = [ordered]@{ jobName=$context.jobName; missingExecutableLaunch=$launchFacts; ownerUsableAfterFailure=$true; ownerEmpty=$true }
    }
}
function Run-DisposedCreatorGuardCase {
    Run-GuardCase 'disposed-creator-guards' {
        param($context)
        $context.phase = 'create then dispose empty creator'
        $context.jobName = 'Local\TroubleshootJS.Verifier.ContainmentContract.' + [guid]::NewGuid().ToString('N')
        $context.job = $jobType::CreateNew($context.jobName)
        $context.memberIds = @($context.job.GetMemberProcessIds())
        Require ($context.memberIds.Count -eq 0) 'disposed-creator job was not empty'
        $context.job.Dispose()
        $context.disposedByCase = $true
        $expectedDisposedPrefix = 'Cannot access a disposed object.'
        $expectedObjectName = 'VerifierBrowserContainmentJobApi'
        $missingExecutable = Join-Path $EvidenceRoot ('VerifierDisposedMissingExecutable.' + [guid]::NewGuid().ToString('N') + '.exe')
        $missingCommand = ConvertTo-VerifierArgumentString @($missingExecutable,'unused')
        $context.phase = 'reject terminated operation on disposed creator'
        $terminateFailure = $null
        try { $context.job.TerminateOwned([uint32]2) } catch { $terminateFailure = $_ }
        $terminateFacts = Assert-BaseException $terminateFailure 'System.ObjectDisposedException' $expectedDisposedPrefix 'disposed creator termination' 'Prefix' -1 '' $expectedObjectName
        $context.phase = 'reject launch operation on disposed creator'
        $launchFailure = $null
        try { $null = $context.job.Launch($missingExecutable,$missingCommand,$EvidenceRoot) } catch { $launchFailure = $_ }
        $launchFacts = Assert-BaseException $launchFailure 'System.ObjectDisposedException' $expectedDisposedPrefix 'disposed creator launch' 'Prefix' -1 '' $expectedObjectName
        $context.phase = 'reject query operation on disposed creator'
        $queryFailure = $null
        try { $null = $context.job.GetMemberProcessIds() } catch { $queryFailure = $_ }
        $queryFacts = Assert-BaseException $queryFailure 'System.ObjectDisposedException' $expectedDisposedPrefix 'disposed creator query' 'Prefix' -1 '' $expectedObjectName
        $context.facts = [ordered]@{ jobName=$context.jobName; terminated=$terminateFacts; launch=$launchFacts; query=$queryFacts; disposedHandleRejectedAllOperations=$true }
    }
}

function Run-SmokeCase([string]$Name) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $phase = 'create case directory'
    $job = $null
    $observer = $null
    $sentinel = $null
    $sentinelStartTicks = 0L
    $sentinelCleanupProven = $true
    $terminationRequested = $false
    $jobClosureWatch = $null
    $jobEmpty = $false
    $primaryFailure = $null
    $primaryFailurePhase = $null
    $failureSnapshot = $null
    $jobName = $null
    $cleanupFailures = New-Object 'System.Collections.Generic.List[string]'
    $memberObservations = New-Object 'System.Collections.Generic.List[object]'
    $facts = $null
    $parentState = $null
    $descendantState = $null
    $memberSnapshot = $null
    $memberEvidence = New-Object 'System.Collections.Generic.List[object]'
    $rootPid = 0L
    $observerFailureMessages = @()
    $launchFailureMessages = @()
    $closureFacts = $null
    $caseDirectory = Join-Path $EvidenceRoot $Name
    $stateDirectory = Join-Path $caseDirectory 'states'
    $sentinelOutput = Join-Path $caseDirectory 'sentinel.stdout.log'
    $sentinelError = Join-Path $caseDirectory 'sentinel.stderr.log'
    try {
        $phase = 'create state directory'
        $null = [IO.Directory]::CreateDirectory($stateDirectory)
        Assert-VerifierNoReparseAncestors $caseDirectory
        $phase = 'create creator-owned job'
        $jobName = 'Local\TroubleshootJS.Verifier.ContainmentContract.' + [guid]::NewGuid().ToString('N')
        $job = $jobType::CreateNew($jobName)
        Require ($null -ne $job -and $job.GetType() -eq $jobType -and $job.Name -ceq $jobName) 'creator job identity'

        $parentNonce = [guid]::NewGuid().ToString('N')
        $descendantCommandArgs = @('descendant',$stateDirectory,$parentNonce,$jobName)
        $descendantArgumentString = ConvertTo-VerifierArgumentString $descendantCommandArgs
        $parentCommandArgs = @('parent',$stateDirectory,$parentNonce,$jobName,$descendantArgumentString)
        $parentCommand = ConvertTo-VerifierArgumentString (@($ChildExecutable) + $parentCommandArgs)
        $phase = 'launch contained parent and read witnesses'
        $rootPid = [long]$job.Launch($ChildExecutable,$parentCommand,$caseDirectory)
        Require ($rootPid -gt 0) 'contained launch PID'
        $parentState = Read-IdentityState 'parent' 'parent' $parentNonce $parentCommandArgs $true $rootPid

        $descendantNonce = $parentNonce
        $descendantCommandArgs = @('descendant',$stateDirectory,$descendantNonce,$jobName)
        $descendantState = Read-IdentityState 'descendant' 'descendant' $descendantNonce $descendantCommandArgs $true 0
        $phase = 'verify initial job membership'
        $memberSnapshot = Observe-JobMembers $job $parentState.ProcessId $parentState.StartTicks $descendantState.ProcessId $descendantState.StartTicks 'creator initial'
        $expectedMembers = @($memberSnapshot.memberIds)
        Require-PidLive $parentState.ProcessId $parentState.StartTicks 'parent live before request'
        Require-PidLive $descendantState.ProcessId $descendantState.StartTicks 'descendant live before request'

        $sentinelNonce = [guid]::NewGuid().ToString('N')
        $sentinelCommandArgs = @('sentinel',$stateDirectory,$sentinelNonce,$jobName)
        $sentinelArgv = $sentinelCommandArgs
        $phase = 'launch outside-job sentinel'
        $sentinel = Start-VerifierProcess $ChildExecutable $sentinelArgv $sentinelOutput $sentinelError
        $sentinelCleanupProven = $false
        $sentinelStartTicks = [long](Get-VerifierLaunchedProcessStartTicks $sentinel)
        Require ($sentinelStartTicks -gt 0) 'retained sentinel start identity'
        $sentinelState = Read-IdentityState 'sentinel' 'sentinel' $sentinelNonce $sentinelCommandArgs $false 0
        Require ($sentinel.Id -eq [long]$sentinelState.ProcessId) 'sentinel PID matches retained process'
        Require ($sentinelStartTicks -eq $sentinelState.StartTicks) 'sentinel start identity matches witness'
        Require-PidLive $sentinel.Id $sentinelStartTicks 'sentinel outside target job'
        $phase = 'verify stable creator membership with sentinel alive'
        $sourceAfterSentinel = Observe-JobMembers $job $parentState.ProcessId $parentState.StartTicks $descendantState.ProcessId $descendantState.StartTicks 'creator after sentinel'
        Assert-MemberIds $sourceAfterSentinel.memberIds $expectedMembers 'source snapshot after sentinel'

        $phase = 'open query-only observer'
        $observer = $jobType::OpenExisting($jobName)
        Require ($observer.GetType() -eq $jobType -and $observer.Name -ceq $jobName) 'query observer identity'
        Assert-MemberIds @($observer.GetMemberProcessIds()) $expectedMembers 'query observer members'
        $observerDenied = $false
        $observerFailure = $null
        $phase = 'verify query-only termination denial'
        try { $observer.TerminateOwned([uint32]2) }
        catch { $observerDenied = $true; $observerFailure = $_; $observerFailureMessages = @(Get-ExceptionMessageChain $_) }
        Require $observerDenied 'query-only observer termination rejected'
        Require-ExceptionMessage $observerFailure 'Only a creator-owned containment job may request termination.' 'query-only denial'
        Assert-MemberIds @($observer.GetMemberProcessIds()) $expectedMembers 'observer denial preserved members'
        Assert-MemberIds @($job.GetMemberProcessIds()) $expectedMembers 'observer denial preserved creator members'
        Require-PidLive $parentState.ProcessId $parentState.StartTicks 'parent remains live after observer denial'
        Require-PidLive $descendantState.ProcessId $descendantState.StartTicks 'descendant remains live after observer denial'
        $observer.Dispose()
        $observer = $null

        $phase = 'retain exact job-member process handles'
        $memberInputs = New-Object 'System.Collections.Generic.List[object]'
        [void]$memberInputs.Add([pscustomobject]@{ role='parent'; processId=$parentState.ProcessId; startTicks=$parentState.StartTicks; imagePath=$ChildExecutable; imageSha256=$childImageSha256 })
        [void]$memberInputs.Add([pscustomobject]@{ role='descendant'; processId=$descendantState.ProcessId; startTicks=$descendantState.StartTicks; imagePath=$ChildExecutable; imageSha256=$childImageSha256 })
        foreach ($support in $memberSnapshot.consoleHosts) {
            [void]$memberInputs.Add([pscustomobject]@{ role='consoleHost'; processId=$support.processId; startTicks=$support.startTicks; imagePath=$support.imagePath; imageSha256=$support.imageSha256 })
        }
        Assert-MemberIds @($memberInputs | ForEach-Object { $_.processId }) $expectedMembers 'vetted member identity set'
        foreach ($member in $memberInputs) {
            $observation = New-MemberObservation $member.role $member.processId $member.startTicks $member.imagePath $member.imageSha256
            [void]$memberObservations.Add($observation)
            [void]$memberEvidence.Add([ordered]@{ role=$member.role; processId=$observation.processId; startTicks=$observation.startTicks; imagePath=$observation.imagePath; imageSha256=$observation.imageSha256; handleRetained=$true })
            $observation.Process.Refresh()
            Require (-not $observation.Process.HasExited) ($member.role + ' live at retained-handle boundary')
        }

        $phase = 'request creator-owned termination'
        $jobClosureWatch = [Diagnostics.Stopwatch]::StartNew()
        $job.TerminateOwned([uint32]2)
        $terminationRequested = $true
        $requestMilliseconds = $jobClosureWatch.Elapsed.TotalMilliseconds

        $phase = 'verify post-request launch denial'
        $deniedDirectory = Join-Path $caseDirectory 'after-request'
        $null = [IO.Directory]::CreateDirectory($deniedDirectory)
        $deniedState = Join-Path $deniedDirectory 'sentinel.state.json'
        $deniedNonce = [guid]::NewGuid().ToString('N')
        $deniedArgs = @('sentinel',$deniedDirectory,$deniedNonce,$jobName)
        $deniedCommand = ConvertTo-VerifierArgumentString (@($ChildExecutable) + $deniedArgs)
        $launchDenied = $false
        $launchFailure = $null
        try { $null = $job.Launch($ChildExecutable,$deniedCommand,$caseDirectory) }
        catch { $launchDenied = $true; $launchFailure = $_; $launchFailureMessages = @(Get-ExceptionMessageChain $_) }
        Require $launchDenied 'launch after termination request rejected'
        Require-ExceptionMessage $launchFailure 'A containment job cannot launch after its termination request.' 'post-request launch denial'
        Require (-not [IO.File]::Exists($deniedState)) 'post-request launch did not create child state'

        $phase = 'wait for creator job and retained handles to close'
        $closureFacts = Wait-JobAndHandles $job $memberObservations $jobClosureWatch 5000
        $closureMilliseconds = $closureFacts.closureMilliseconds
        Assert-MemberIds @($job.GetMemberProcessIds()) @() 'creator empty after request'
        $jobEmpty = $true
        $memberExitStatuses = @($closureFacts.exitStatuses)
        Require (@($memberExitStatuses | Where-Object { $_.status -cne 'EXIT_OBSERVED' }).Count -eq 0) 'all retained member handles signaled'
        $phase = 'verify outside sentinel survives termination'
        $sentinel.Refresh()
        Require (-not $sentinel.HasExited) 'outside sentinel survives creator termination'
        Require ($sentinel.Id -eq [long]$sentinelState.ProcessId) 'outside sentinel PID unchanged'
        Require ([long](Get-VerifierLaunchedProcessStartTicks $sentinel) -eq $sentinelStartTicks) 'outside sentinel identity unchanged'
        $facts = @{
            jobName=$jobName; rootPid=$rootPid; rootStartTicks=$parentState.StartTicks
            descendantPid=$descendantState.ProcessId; descendantStartTicks=$descendantState.StartTicks
            sentinelPid=$sentinel.Id; sentinelStartTicks=$sentinelStartTicks
            expectedMembersBeforeRequest=$expectedMembers; vettedMemberIdentities=@($memberEvidence.ToArray()); memberExitStatuses=$memberExitStatuses; observerTermination='REJECTED'
            terminationRequestMilliseconds=$requestMilliseconds; closureMilliseconds=$closureMilliseconds
            membersEmpty=$true; allRetainedMemberHandlesSignaled=$true; sourceSnapshotStable=$true; observerSnapshotMatched=$true
            observerFailureMessages=$observerFailureMessages; postRequestLaunchFailureMessages=$launchFailureMessages
            sentinelSurvived=$true; postRequestLaunch='REJECTED'; stateWitnesses=@('parent','descendant','sentinel')
        }
    } catch {
        $primaryFailure = $_
        $primaryFailurePhase = $phase
        $failureParentPid = $null
        $failureParentStartTicks = $null
        $failureDescendantPid = $null
        $failureDescendantStartTicks = $null
        $failureSentinelPid = $null
        $failureSentinelStartTicks = $null
        $knownMembers = New-Object 'System.Collections.Generic.List[object]'
        if ($null -ne $parentState) {
            $failureParentPid = $parentState.ProcessId; $failureParentStartTicks = $parentState.StartTicks
            [void]$knownMembers.Add([ordered]@{ role='parent'; processId=$failureParentPid; startTicks=$failureParentStartTicks; imagePath=$ChildExecutable; imageSha256=$childImageSha256 })
        }
        if ($null -ne $descendantState) {
            $failureDescendantPid = $descendantState.ProcessId; $failureDescendantStartTicks = $descendantState.StartTicks
            [void]$knownMembers.Add([ordered]@{ role='descendant'; processId=$failureDescendantPid; startTicks=$failureDescendantStartTicks; imagePath=$ChildExecutable; imageSha256=$childImageSha256 })
        }
        if ($null -ne $memberSnapshot) { foreach ($support in $memberSnapshot.consoleHosts) { [void]$knownMembers.Add($support) } }
        if ($null -ne $sentinel) { $failureSentinelPid = $sentinel.Id; $failureSentinelStartTicks = $sentinelStartTicks }
        $failureSnapshot = [ordered]@{
            phase=$primaryFailurePhase; jobName=$jobName; rootPid=$rootPid
            parentPid=$failureParentPid; parentStartTicks=$failureParentStartTicks
            descendantPid=$failureDescendantPid; descendantStartTicks=$failureDescendantStartTicks
            sentinelPid=$failureSentinelPid; sentinelStartTicks=$failureSentinelStartTicks
            observedCreatorMemberIds=@(); knownMemberIdentities=@($knownMembers.ToArray()); retainedMemberIdentities=@($memberEvidence.ToArray())
            terminationRequested=$terminationRequested; jobEmpty=$jobEmpty
        }
        if ($null -ne $memberSnapshot) { $failureSnapshot.observedCreatorMemberIds=@($memberSnapshot.memberIds) }
        try {
            Save-Case $caseDirectory 'primary-failure.json' @{
                status='FAIL_NOT_PASS'; phase=$primaryFailurePhase
                exceptionType=$primaryFailure.Exception.GetType().FullName
                exceptionMessageChain=@(Get-ExceptionMessageChain $primaryFailure)
                failureSnapshot=$failureSnapshot
            }
        } catch { [void]$cleanupFailures.Add('primary failure evidence write failed') }
    }

    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    if ($null -ne $observer) {
        try { $observer.Dispose() } catch { [void]$cleanupFailures.Add('query observer dispose failed') }
    }
    if ($null -ne $job) {
        try {
            if (-not $jobEmpty) {
                if (-not $terminationRequested) {
                    if ($null -eq $jobClosureWatch) { $jobClosureWatch = [Diagnostics.Stopwatch]::StartNew() }
                    $job.TerminateOwned([uint32]2)
                    $terminationRequested = $true
                }
                if ($memberObservations.Count -eq 0) {
                    [void]$cleanupFailures.Add('no retained job-member process handles were available for closure proof')
                }
                if ($null -eq $jobClosureWatch) { throw 'termination request deadline was not initialized' }
                $cleanupClosure = Wait-JobAndHandles $job $memberObservations $jobClosureWatch 5000
                $jobEmpty = $true
                $closureFacts = $cleanupClosure
                if ($null -eq $facts) { $facts = @{} }
                $facts.cleanupTerminationRequested=$terminationRequested
                $facts.cleanupClosureMilliseconds=$cleanupClosure.closureMilliseconds
            }
        } catch { [void]$cleanupFailures.Add('creator job cleanup or closure proof failed') }
        foreach ($observation in $memberObservations) {
            try { $observation.Process.Dispose() } catch { [void]$cleanupFailures.Add('job-member observation handle dispose failed') }
        }
        try { $job.Dispose() } catch { [void]$cleanupFailures.Add('creator job handle dispose failed') }
    }
    if ($null -ne $sentinel) {
        try {
            $sentinel.Refresh()
            if (-not $sentinel.HasExited) {
                & $owner { param($process,$ticks) Stop-VerifierBoundedProcessExactly $process $ticks 5000 } $sentinel $sentinelStartTicks
            }
            $sentinel.Refresh()
            Require $sentinel.HasExited 'sentinel exact stop did not reach terminal state'
            & $owner { param($process) Complete-VerifierProcessOutputCapture $process 5000 } $sentinel
            Require ([IO.File]::Exists($sentinelOutput) -and [IO.File]::Exists($sentinelError)) 'sentinel cleanup logs retained'
            Require (([IO.FileInfo]::new($sentinelOutput).Length -eq 0) -and ([IO.FileInfo]::new($sentinelError).Length -eq 0)) 'sentinel had no output'
            $sentinelCleanupProven = $true
        } catch { [void]$cleanupFailures.Add('sentinel exact cleanup or output drain failed') }
        try { $sentinel.Dispose() } catch { [void]$cleanupFailures.Add('sentinel process handle dispose failed') }
    }
    $cleanupWatch.Stop()
    if ($null -ne $facts) {
        $facts.cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        $facts.sentinelOutputBytes = if ([IO.File]::Exists($sentinelOutput)) { [IO.FileInfo]::new($sentinelOutput).Length } else { $null }
        $facts.sentinelErrorBytes = if ([IO.File]::Exists($sentinelError)) { [IO.FileInfo]::new($sentinelError).Length } else { $null }
    }
    $cleanupStatus = 'PASS'
    if ($cleanupFailures.Count -gt 0 -or ($null -ne $job -and (-not $jobEmpty -or $null -eq $closureFacts -or -not $closureFacts.allRetainedHandlesSignaled)) -or -not $sentinelCleanupProven) {
        $cleanupStatus = 'FAIL_NOT_PASS'
    }
    $knownCleanupMembers = @()
    if ($null -ne $failureSnapshot) { $knownCleanupMembers = @($failureSnapshot.knownMemberIdentities) }
    elseif ($memberEvidence.Count -gt 0) { $knownCleanupMembers = @($memberEvidence.ToArray()) }
    $cleanupExitStatuses = @()
    if ($null -ne $closureFacts) { $cleanupExitStatuses = @($closureFacts.exitStatuses) }
    $cleanupResult = [ordered]@{
        status=$cleanupStatus; name=$Name; jobName=$jobName; rootPid=$rootPid
        knownMemberIdentities=$knownCleanupMembers; retainedMemberIdentities=@($memberEvidence.ToArray())
        terminationRequested=$terminationRequested; jobEmpty=$jobEmpty; memberExitStatuses=$cleanupExitStatuses
        sentinelCleanupProven=$sentinelCleanupProven; cleanupFailures=@($cleanupFailures.ToArray())
    }
    try { Save-Case $caseDirectory 'cleanup-result.json' $cleanupResult }
    catch { [void]$cleanupFailures.Add('cleanup result evidence write failed'); $cleanupStatus='FAIL_NOT_PASS' }
    if ($cleanupFailures.Count -gt 0) {
        if ($null -ne $primaryFailure) { throw ('containment cleanup failed after primary failure in phase ' + $primaryFailurePhase) }
        throw ('containment cleanup failed: ' + [String]::Join('; ',$cleanupFailures.ToArray()))
    }
    if ($null -ne $primaryFailure) { throw $primaryFailure.Exception }
    Finish-Case $Name $watch $facts
}

try {
    Require ($PSVersionTable.PSVersion.Major -eq 5 -and $PSVersionTable.PSVersion.Minor -eq 1) 'Windows PowerShell 5.1 required'
    Require (Test-Path -LiteralPath $ModulePath -PathType Leaf) 'module file missing'
    Require ([IO.FileInfo]::new($ModulePath).Length -eq $ExpectedModuleBytes -and $ExpectedModuleSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $ModulePath) -ceq $ExpectedModuleSha256) 'module pin'
    Require (Test-Path -LiteralPath $FixtureSourcePath -PathType Leaf) 'fixture source missing'
    Require ($ExpectedFixtureSourceSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $FixtureSourcePath) -ceq $ExpectedFixtureSourceSha256) 'fixture source pin'
    Require ($ExpectedConsoleHostSha256 -cmatch '^[0-9a-f]{64}$') 'console host hash parameter'

    $owner = Import-Module -Name $ModulePath -Force -DisableNameChecking -PassThru
    $ModulePath = [IO.Path]::GetFullPath($ModulePath)
    $FixtureSourcePath = [IO.Path]::GetFullPath($FixtureSourcePath)
    $EvidenceRoot = [IO.Path]::GetFullPath($EvidenceRoot)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $compilerHostProcess = [Diagnostics.Process]::GetCurrentProcess()
    try {
        $compilerHostPath = [IO.Path]::GetFullPath($compilerHostProcess.MainModule.FileName)
        $compilerHostSha256 = Sha $compilerHostPath
        $compilerHostBytes = [IO.FileInfo]::new($compilerHostPath).Length
    } finally { $compilerHostProcess.Dispose() }
    $consoleHostArtifact = Assert-ConsoleHostArtifact
    Assert-VerifierNoReparseAncestors $FixtureSourcePath
    Assert-VerifierNoReparseAncestors $EvidenceRoot
    Require (Test-VerifierPhysicalChildPath $tempRoot $EvidenceRoot) 'evidence root must be a physical temp child'
    Require (-not [IO.Directory]::Exists($EvidenceRoot) -and -not [IO.File]::Exists($EvidenceRoot)) 'fresh evidence root required'
    $null = [IO.Directory]::CreateDirectory($EvidenceRoot)
    $rootReady = $true
    Assert-VerifierNoReparseAncestors $EvidenceRoot
    $compileDirectory = Join-Path $EvidenceRoot 'compiled-fixture'
    $null = [IO.Directory]::CreateDirectory($compileDirectory)
    Assert-VerifierNoReparseAncestors $compileDirectory
    $ChildExecutable = [IO.Path]::GetFullPath((Join-Path $compileDirectory 'VerifierContainmentFixture.exe'))
    Require (-not [IO.File]::Exists($ChildExecutable) -and -not [IO.Directory]::Exists($ChildExecutable)) 'fresh fixture output required'
    $null = Add-Type -Path $FixtureSourcePath -OutputAssembly $ChildExecutable -OutputType ConsoleApplication `
        -ReferencedAssemblies @('System.dll','System.Core.dll','System.Web.Extensions.dll') -ErrorAction Stop
    Require ([IO.File]::Exists($ChildExecutable)) 'compiled fixture artifact missing'
    Assert-VerifierNoReparseAncestors $ChildExecutable
    $childImageSha256 = Sha $ChildExecutable
    $childImageBytes = [IO.FileInfo]::new($ChildExecutable).Length
    $sourceHashes = @{ module=(Sha $ModulePath); fixtureSource=(Sha $FixtureSourcePath); childArtifact=$childImageSha256; compilerHost=$compilerHostSha256; consoleHost=$consoleHostArtifact.imageSha256 }
    Save 'inputs.json' @{ hashes=$sourceHashes; moduleBytes=$ExpectedModuleBytes; fixtureSourceBytes=([IO.FileInfo]::new($FixtureSourcePath).Length); childArtifactBytes=$childImageBytes; compilerHostPath=$compilerHostPath; compilerHostBytes=$compilerHostBytes; consoleHostPath=$consoleHostArtifact.imagePath; consoleHostSigner=$consoleHostArtifact.signerThumbprint; consoleHostSignature=$consoleHostArtifact.signatureStatus; powershellVersion=$PSVersionTable.PSVersion.ToString(); compiler='Windows PowerShell Add-Type'; toolClosure='NOT_ATTESTED'; mode=$Mode; containmentScope='EXPLICIT_JOB_TERMINATION_ONLY' }
    $jobType = & $owner { Initialize-VerifierBrowserContainmentJobApi }
    Require ($jobType -is [type] -and $jobType.Name -ceq 'VerifierBrowserContainmentJobApi') 'native job API type'

    if ($Mode -ceq 'Smoke') {
        Run-SmokeCase 'containment-smoke'
        $expectedCaseNames = @('containment-smoke')
    }
    else {
        Run-SmokeCase 'containment-smoke'
        Run-EmptyCreatorGuardCase
        Run-BlankNameGuardCase
        Run-DuplicateNameGuardCase
        Run-MissingOpenGuardCase
        Run-MissingExecutableGuardCase
        Run-DisposedCreatorGuardCase
        $expectedCaseNames = @('containment-smoke','empty-creator-termination','blank-job-names','duplicate-job-name','missing-open-name','missing-executable','disposed-creator-guards')
    }
    Require ($cases.Count -eq $expectedCaseNames.Count) 'complete case count'
    for ($i=0; $i -lt $expectedCaseNames.Count; $i++) { Require ($cases[$i].name -ceq $expectedCaseNames[$i]) 'case order' }
    $consoleHostArtifactFinal = Assert-ConsoleHostArtifact
    Require ((Sha $ModulePath) -ceq $sourceHashes.module -and (Sha $FixtureSourcePath) -ceq $sourceHashes.fixtureSource -and (Sha $ChildExecutable) -ceq $sourceHashes.childArtifact -and ([IO.FileInfo]::new($ChildExecutable).Length -eq $childImageBytes) -and (Sha $compilerHostPath) -ceq $sourceHashes.compilerHost -and $consoleHostArtifactFinal.imageSha256 -ceq $sourceHashes.consoleHost -and $consoleHostArtifactFinal.signerThumbprint -ceq $consoleHostArtifact.signerThumbprint) 'final source, compiler host, console host, and artifact pins'
    Save 'cases.json' @{ status='PASS'; mode=$Mode; caseCount=$cases.Count; cases=@($cases.ToArray()); scope='JOB_LIFECYCLE_ONLY'; capturedPipes='NOT_QUALIFIED'; creatorDeathProtection='NOT_QUALIFIED' }
    Write-Output ('Verifier containment contract cases=' + $cases.Count + ' status=PASS')
    exit 0
} catch {
    if ($rootReady) {
        try { Save 'failure.json' @{ status='FAIL_NOT_PASS'; type=$_.Exception.GetType().FullName; message=$_.Exception.Message; completedCases=@($cases.ToArray()) } }
        catch { }
    }
    [Console]::Error.WriteLine('Verifier containment contract FAIL: ' + $_.Exception.GetType().FullName)
    exit 2
}

[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ModulePath,
    [Parameter(Mandatory=$true)][string]$ExpectedModuleSha256,
    [Parameter(Mandatory=$true)][long]$ExpectedModuleBytes,
    [Parameter(Mandatory=$true)][string]$FixtureSourcePath,
    [Parameter(Mandatory=$true)][string]$ExpectedFixtureSourceSha256,
    [Parameter(Mandatory=$true)][string]$EvidenceRoot,
    [ValidateSet('Smoke','Suite')][string]$Mode = 'Smoke',
    [string]$JavaExecutable = '',
    [string]$ExpectedJavaSha256 = 'e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$cases = New-Object 'System.Collections.Generic.List[object]'
$owner = $null
$jobType = $null
$rootReady = $false
$script:activeEvidence = $null
$script:activeContext = $null

function Require([bool]$Condition, [string]$Reason) {
    if (-not $Condition) { throw $Reason }
}

function Sha([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-','').ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
        $stream.Dispose()
    }
}

function Save([string]$Name, $Value) {
    $body = [Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 24) + "`n")
    $stream = [IO.File]::Open((Join-Path $EvidenceRoot $Name), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($body, 0, $body.Length) }
    finally { $stream.Dispose() }
}

function Save-Case([string]$Directory, [string]$Name, $Value) {
    $body = [Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 24) + "`n")
    $stream = [IO.File]::Open((Join-Path $Directory $Name), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($body, 0, $body.Length) }
    finally { $stream.Dispose() }
}

function Equal-Strings($Actual, $Expected, [string]$Label) {
    Require (@($Actual).Count -eq @($Expected).Count) ($Label + ' count')
    for ($i = 0; $i -lt @($Expected).Count; $i++) {
        Require ($Actual[$i] -is [string] -and $Actual[$i] -ceq $Expected[$i]) ($Label + ' value')
    }
}

function Assert-ByteRun([byte[]]$Actual, [byte]$Value, [int]$Count, [string]$Label) {
    Require ($Actual.Length -eq $Count) ($Label + ' byte count')
    for ($i = 0; $i -lt $Actual.Length; $i++) {
        if ($Actual[$i] -ne $Value) { throw ($Label + ' raw byte mismatch at offset ' + $i) }
    }
}

function Convert-ToWindowsArgumentString($Arguments) {
    $spec = [pscustomobject]@{ Values = [string[]]$Arguments }
    return [string](& $owner {
        param($ArgumentSpec)
        ConvertTo-VerifierArgumentString $ArgumentSpec.Values
    } $spec)
}

function Get-CaptureFacts($Context) {
    if ($null -eq $Context.Capture) { return $null }
    $startTicks = [long]$Context.Lease.CreationFileTime + 504911232000000000L
    return & $owner {
        param($State, $ProcessId, $Ticks)
        Get-VerifierBoundedCaptureFacts $State $ProcessId $Ticks
    } $Context.Capture $Context.Lease.ProcessId $startTicks
}

function Test-StreamHandlesClosed($Context) {
    if ($null -eq $Context.Lease) { return $false }
    if ($null -eq $Context.StdoutSafeHandle -or $null -eq $Context.StderrSafeHandle) { return $false }
    return [bool]($Context.StdoutSafeHandle.IsClosed -and $Context.StderrSafeHandle.IsClosed)
}

function Get-ConsoleHostArtifact {
    $path = [IO.Path]::GetFullPath((Join-Path ([Environment]::SystemDirectory) 'conhost.exe'))
    Require (Test-Path -LiteralPath $path -PathType Leaf) 'canonical system console host is missing'
    $sha256 = Sha $path
    Require ($sha256 -ceq 'dd5c3514132d92052004d302c66c756ffb6beef572de6538b4fc55f62579eb11') 'canonical system console host image SHA changed'
    $signature = Get-AuthenticodeSignature -FilePath $path
    Require ($signature.Status -eq [Management.Automation.SignatureStatus]::Valid) 'canonical system console host signature is not valid'
    Require ($null -ne $signature.SignerCertificate) 'canonical system console host signer certificate is missing'
    Require ($signature.SignerCertificate.Thumbprint -ceq '667BBA1E1F478221AA65DE4F75187A751B03A453') 'canonical system console host signer thumbprint changed'
    Require ($signature.SignerCertificate.Subject.StartsWith('CN=Microsoft Windows, O=Microsoft Corporation', [StringComparison]::Ordinal)) 'canonical system console host signer subject changed'
    return [pscustomobject]@{
        imagePath = $path
        imageSha256 = $sha256
        signerThumbprint = $signature.SignerCertificate.Thumbprint
        signatureStatus = [string]$signature.Status
    }
}

function Observe-CaptureJobMembers($Context, $KnownActors = @()) {
    $before = [bool]$Context.Lease.WaitForExit(0)
    $ids = @($Context.Job.GetMemberProcessIds() | ForEach-Object { [long]$_ })
    $after = [bool]$Context.Lease.WaitForExit(0)
    $Context.MemberIdsAtLaunch = @($ids)
    $knownActorIds = @($KnownActors | ForEach-Object { [long]$_.Witness.pid })
    $unique = @($ids | Select-Object -Unique)
    $facts = [ordered]@{
        memberIds = @($ids)
        uniqueMemberIds = @($unique)
        knownActorProcessIds = @($knownActorIds)
        rootPid = [long]$Context.Lease.ProcessId
        rootCreationFileTime = [long]$Context.Lease.CreationFileTime
        rootExitedBeforeSnapshot = $before
        rootExitedAfterSnapshot = $after
        rootPresent = (@($ids) -contains [long]$Context.Lease.ProcessId)
        knownActors = (New-Object 'System.Collections.Generic.List[object]')
        extraMembers = (New-Object 'System.Collections.Generic.List[object]')
        emptySnapshotRequiresActorProof = ($ids.Count -eq 0)
    }
    $Context.MemberIdentityFacts = $facts
    Require ($ids.Count -eq $unique.Count) 'capture job member snapshot contained duplicate PIDs'
    $expectedKnownIds = @($knownActorIds | Select-Object -Unique)
    Require ($knownActorIds.Count -eq $expectedKnownIds.Count) 'known actor set contained duplicate PIDs'
    foreach ($actor in $KnownActors) {
        $actorPid = [long]$actor.Witness.pid
        Require ($actorPid -ne [long]$Context.Lease.ProcessId) 'known child actor PID duplicated the retained root lease'
        Require ($ids -contains $actorPid) ('known child actor was absent from its containment job: ' + $actor.Role)
        $identityRow = [ordered]@{ role = [string]$actor.Role; processId = $actorPid; status = 'IDENTITY_PENDING' }
        [void]$facts.knownActors.Add($identityRow)
        $observer = $null
        try {
            $observer = [Diagnostics.Process]::GetProcessById([int]$actorPid)
            $handle = $observer.Handle
            Require ($handle -ne [IntPtr]::Zero -and $handle -ne [IntPtr]::new(-1)) 'known child actor observer handle was invalid'
            $observer.Refresh()
            $startTime = $observer.StartTime.ToUniversalTime()
            $startTicks = [long]$startTime.Ticks
            $creationFileTime = [long]$startTime.ToFileTimeUtc()
            $imagePath = [IO.Path]::GetFullPath($observer.MainModule.FileName)
            $imageSha256 = Sha $imagePath
            Require (-not [bool]$observer.HasExited) ('known child actor was not live at membership observation: ' + $actor.Role)
            $identity = [pscustomobject]@{
                role = [string]$actor.Role
                processId = [long]$observer.Id
                startTicks = $startTicks
                creationFileTime = $creationFileTime
                imagePath = $imagePath
                imageSha256 = $imageSha256
                handleRetained = $true
            }
            $identityRow.identity = $identity
            $identityRow.status = 'IDENTITY_OBSERVED'
            [void]$Context.MemberObservers.Add([pscustomobject]@{ Process = $observer; Handle = $handle; Identity = $identity })
            $observer = $null
            Require ($identity.processId -eq $actorPid) ('known child actor PID changed: ' + $actor.Role)
            Require ($creationFileTime -eq [long]$actor.Witness.creationFileTime) ('known child actor creation identity changed: ' + $actor.Role)
            Require ([String]::Equals($identity.imagePath, [IO.Path]::GetFullPath($actor.ImagePath), [StringComparison]::OrdinalIgnoreCase) -and $identity.imageSha256 -ceq $actor.ImageSha256) ('known child actor image identity mismatch: ' + $actor.Role)
        } catch {
            $identityRow.status = 'IDENTITY_FAILED'
            $identityRow.failure = $_.Exception.Message
            if ($null -ne $observer) {
                try { $observer.Dispose() } catch { }
            }
            throw
        }
    }
    if ($ids.Count -eq 0) {
        Require $after 'capture job was empty while its original process was still live'
        Require ($knownActorIds.Count -eq 0) 'capture job was empty despite a known child actor'
        return [pscustomobject]$facts
    }
    $maximumMembers = 1 + $knownActorIds.Count + 2
    Require ($ids.Count -ge 1 -and $ids.Count -le $maximumMembers) 'capture job member count was outside known actors plus at most two vetted console hosts'
    if (-not $after) {
        Require ($ids -contains [long]$Context.Lease.ProcessId) 'live native lease PID was absent from its job member snapshot'
    }

    $extras = @($ids | Where-Object { [long]$_ -ne [long]$Context.Lease.ProcessId -and $knownActorIds -notcontains [long]$_ })
    Require ($extras.Count -le 2) 'capture job had more than two extra members'
    $consoleHost = $null
    if ($extras.Count -gt 0) { $consoleHost = Get-ConsoleHostArtifact }
    foreach ($processId in $extras) {
        $member = [ordered]@{ processId = [long]$processId; status = 'IDENTITY_PENDING' }
        [void]$Context.MemberIdentityFacts.extraMembers.Add($member)
        $observer = $null
        try {
            $observer = [Diagnostics.Process]::GetProcessById([int]$processId)
            $handle = $observer.Handle
            Require ($handle -ne [IntPtr]::Zero -and $handle -ne [IntPtr]::new(-1)) 'extra job member observer handle was invalid'
            $observer.Refresh()
            $startTime = $observer.StartTime.ToUniversalTime()
            $startTicks = [long]$startTime.Ticks
            $creationFileTime = [long]$startTime.ToFileTimeUtc()
            $imagePath = [IO.Path]::GetFullPath($observer.MainModule.FileName)
            $imageSha256 = Sha $imagePath
            $identity = [pscustomobject]@{
                role = 'vetted-system-console-host'
                processId = [long]$observer.Id
                startTicks = $startTicks
                creationFileTime = $creationFileTime
                imagePath = $imagePath
                imageSha256 = $imageSha256
                signerThumbprint = $consoleHost.signerThumbprint
                signatureStatus = $consoleHost.signatureStatus
                handleRetained = $true
            }
            $member.identity = $identity
            $member.status = 'IDENTITY_OBSERVED'
            [void]$Context.MemberObservers.Add([pscustomobject]@{ Process = $observer; Handle = $handle; Identity = $identity })
            $observer = $null
            Require ($identity.processId -eq [long]$processId) 'extra member PID changed during identity observation'
            Require ([String]::Equals($identity.imagePath, $consoleHost.imagePath, [StringComparison]::OrdinalIgnoreCase)) 'extra job member was not the canonical system console host'
            Require ($identity.imageSha256 -ceq $consoleHost.imageSha256) 'extra job member console host image SHA mismatch'
            $rootStartTicks = [long]$Context.Lease.CreationFileTime + 504911232000000000L
            Require ($identity.startTicks -ge $rootStartTicks) 'extra console host creation preceded its native lease root'
        } catch {
            $member.status = 'IDENTITY_FAILED'
            $member.failure = $_.Exception.Message
            if ($null -ne $observer) {
                try { $observer.Dispose() } catch { }
            }
            throw
        }
    }
    return [pscustomobject]$facts
}

function Close-CaptureReaders($Context) {
    if ($null -ne $Context.Capture) {
        & $owner {
            param($State)
            Close-VerifierBoundedCapture $State
        } $Context.Capture
    } elseif ($null -ne $Context.Lease) {
        # Only a setup-failure fallback: no capture state exists yet.
        $Context.Lease.StandardOutput.BaseStream.Close()
        $Context.Lease.StandardError.BaseStream.Close()
    }
}

function Wait-RootAndJobEmpty($Context, [int]$TimeoutMilliseconds) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.ElapsedMilliseconds -lt $TimeoutMilliseconds) {
        $rootExited = $true
        if ($null -ne $Context.Lease) { $rootExited = $Context.Lease.WaitForExit(0) }
        $members = @()
        if ($null -ne $Context.Job) { $members = @($Context.Job.GetMemberProcessIds()) }
        $observersExited = $true
        foreach ($observer in $Context.MemberObservers) {
            $observerSignaled = [bool]$observer.Process.WaitForExit(0)
            $observer.Process.Refresh()
            if ($observer.Process.Handle -ne $observer.Handle -or
                    [long]$observer.Process.StartTime.ToUniversalTime().Ticks -ne [long]$observer.Identity.startTicks) {
                throw 'retained extra-member observer identity changed before closure proof'
            }
            if (-not $observerSignaled -or -not $observer.Process.HasExited) { $observersExited = $false }
        }
        if ($rootExited -and $members.Count -eq 0 -and $observersExited) {
            return [pscustomobject]@{
                elapsedMilliseconds = $watch.Elapsed.TotalMilliseconds
                rootExited = $true
                memberIds = $members
                retainedMemberObserversExited = $observersExited
            }
        }
        Start-Sleep -Milliseconds 20
    }
    throw 'captured process original handle and containment job did not close within the shared bound'
}

function Assert-ActorWitness($Context) {
    $statePath = Join-Path $Context.StateDirectory 'emit.state.json'
    Require ([IO.File]::Exists($statePath)) 'captured fixture state witness missing'
    $body = [IO.File]::ReadAllText($statePath)
    $witness = $body | ConvertFrom-Json -ErrorAction Stop
    Require ($witness.mode -ceq 'emit') 'captured fixture mode witness mismatch'
    Require ($witness.nonce -ceq $Context.Nonce) 'captured fixture nonce mismatch'
    Require ([bool]$witness.inExpectedJob) 'captured fixture did not witness containment membership'
    Require ([long]$witness.pid -eq $Context.Lease.ProcessId) 'captured fixture PID did not match native lease'
    Require ([long]$witness.creationFileTime -eq $Context.Lease.CreationFileTime) 'captured fixture creation time did not match native lease'
    Require ([String]::Equals([IO.Path]::GetFullPath($witness.imagePath), $Context.ChildExecutable, [StringComparison]::OrdinalIgnoreCase)) 'captured fixture image path mismatch'
    Require ($witness.imageSha256 -ceq $Context.ChildImageSha256) 'captured fixture image SHA mismatch'
    Require ([String]::Equals([IO.Path]::GetFullPath($Context.Lease.ImagePath), [IO.Path]::GetFullPath($witness.imagePath), [StringComparison]::OrdinalIgnoreCase)) 'native lease image path differs from independent witness'
    $expectedArgv = @(
        $Context.ChildExecutable, 'emit', $Context.StateDirectory, $Context.Nonce,
        $Context.JobName, [string]$Context.StdoutBytes, [string]$Context.StderrBytes, '0'
    )
    Equal-Strings @($witness.argv) $expectedArgv 'independent actor argv'
    $parsed = @(& $owner {
        param($RawCommandLine)
        ConvertFrom-VerifierWindowsCommandLine $RawCommandLine
    } $witness.commandLine)
    Equal-Strings $parsed $expectedArgv 'independent actor raw command line'
    return [pscustomobject]@{ body = $body; witness = $witness }
}

function Read-FixtureActorWitness([string]$StatePath, [string]$ModeName,
        [string]$Nonce, [string]$JobName, [string]$ImagePath,
        [string]$ImageSha256, [string[]]$ExpectedArgv, [bool]$ExpectedInJob) {
    Require ([IO.File]::Exists($StatePath)) ($ModeName + ' fixture state witness missing')
    $body = [IO.File]::ReadAllText($StatePath)
    $witness = $body | ConvertFrom-Json -ErrorAction Stop
    Require ($witness.mode -ceq $ModeName) ($ModeName + ' fixture mode mismatch')
    Require ($witness.nonce -ceq $Nonce) ($ModeName + ' fixture nonce mismatch')
    Require ([bool]$witness.inExpectedJob -eq $ExpectedInJob) ($ModeName + ' fixture job-membership witness mismatch')
    Require ([long]$witness.pid -gt 0 -and [long]$witness.creationFileTime -gt 0) ($ModeName + ' fixture process identity invalid')
    Require ([String]::Equals([IO.Path]::GetFullPath($witness.imagePath), [IO.Path]::GetFullPath($ImagePath), [StringComparison]::OrdinalIgnoreCase)) ($ModeName + ' fixture image path mismatch')
    Require ($witness.imageSha256 -ceq $ImageSha256) ($ModeName + ' fixture image SHA mismatch')
    Equal-Strings @($witness.argv) $ExpectedArgv ($ModeName + ' independent argv')
    $parsed = @(& $owner {
        param($RawCommandLine)
        ConvertFrom-VerifierWindowsCommandLine $RawCommandLine
    } $witness.commandLine)
    Equal-Strings $parsed $ExpectedArgv ($ModeName + ' independent raw command line')
    return [pscustomobject]@{ body = $body; witness = $witness; expectedJobName = $JobName }
}

function Run-DuplexSmoke {
    $caseDirectory = Join-Path $EvidenceRoot 'duplex-smoke'
    $null = [IO.Directory]::CreateDirectory($caseDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $caseDirectory
    $stateDirectory = Join-Path $caseDirectory 'states'
    $null = [IO.Directory]::CreateDirectory($stateDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $stateDirectory
    $jobName = 'Local\TroubleshootJS.Verifier.CapturedPipeContract.' + [guid]::NewGuid().ToString('N')
    $nonce = [guid]::NewGuid().ToString('N')
    $stdoutBytes = 65536
    $stderrBytes = 65536
    $fixtureArgs = @('emit', $stateDirectory, $nonce, $jobName, [string]$stdoutBytes, [string]$stderrBytes, '0')
    $fullCommandLine = Convert-ToWindowsArgumentString (@($ChildExecutable) + $fixtureArgs)
    $stdoutCap = 65536
    $stderrCap = 65536
    $timeoutMilliseconds = 10000
    $cancelToken = [Threading.CancellationToken]::None
    $context = [pscustomobject]@{
        Job = $null
        Lease = $null
        Capture = $null
        JobName = $jobName
        Nonce = $nonce
        StateDirectory = $stateDirectory
        ChildExecutable = $ChildExecutable
        ChildImageSha256 = $ChildImageSha256
        StdoutBytes = $stdoutBytes
        StderrBytes = $stderrBytes
        StdoutCap = $stdoutCap
        StderrCap = $stderrCap
        TimeoutMilliseconds = $timeoutMilliseconds
        OperationWatch = $null
        OperationFacts = $null
        CleanupFacts = $null
        StdoutSafeHandle = $null
        StderrSafeHandle = $null
        LeaseDisposeProven = $false
        JobDisposeProven = $false
        StreamFacts = $null
        MemberIdsAtLaunch = @()
        MemberIdentityFacts = $null
        MemberObservers = (New-Object 'System.Collections.Generic.List[object]')
        MemberObserverDisposeProven = $false
        Phase = 'create-job'
        ActorFacts = $null
        CaptureFacts = $null
        TerminationRequested = $false
    }
    $script:activeContext = $context
    $primaryFailure = $null
    $cleanupFailures = New-Object 'System.Collections.Generic.List[string]'

    try {
        $jobType = & $owner { Initialize-VerifierBrowserContainmentJobApi }
        Require ($jobType -is [type] -and $jobType.Name -ceq 'VerifierBrowserContainmentJobApi') 'native containment job type unavailable'
        $context.Job = $jobType::CreateNew($jobName)
        Require (@($context.Job.GetMemberProcessIds()).Count -eq 0) 'new capture job was not empty'

        $context.Phase = 'launch-captured-fixture'
        $context.OperationWatch = [Diagnostics.Stopwatch]::StartNew()
        $context.Lease = $context.Job.LaunchCaptured($ChildExecutable, $fullCommandLine, $caseDirectory)
        Require ($null -ne $context.Lease -and $context.Lease.GetType().Name -ceq 'OwnedProcessLease') 'native capture launch did not return an owned process lease'
        Require (-not [bool]$context.Lease.IsClosed) 'native capture lease was closed at launch'
        $stdoutReader = $context.Lease.StandardOutput
        $stderrReader = $context.Lease.StandardError
        $stdoutStream = $stdoutReader.BaseStream
        $stderrStream = $stderrReader.BaseStream
        Require ($stdoutReader.GetType() -eq [IO.StreamReader] -and $stderrReader.GetType() -eq [IO.StreamReader]) 'native capture readers were not exact StreamReader instances'
        Require ($stdoutStream.GetType() -eq [IO.FileStream] -and $stderrStream.GetType() -eq [IO.FileStream]) 'native capture base streams were not exact FileStream instances'
        Require (-not [bool]$stdoutStream.IsAsync -and -not [bool]$stderrStream.IsAsync) 'native capture did not expose the expected synchronous-handle stream contract'
        $context.StdoutSafeHandle = $stdoutStream.SafeFileHandle
        $context.StderrSafeHandle = $stderrStream.SafeFileHandle
        Require (-not $context.StdoutSafeHandle.IsClosed -and -not $context.StderrSafeHandle.IsClosed) 'native captured read handle was closed before capture'
        $context.MemberIdentityFacts = Observe-CaptureJobMembers $context
        $context.StreamFacts = [ordered]@{
            stdoutReaderType = $stdoutReader.GetType().FullName
            stderrReaderType = $stderrReader.GetType().FullName
            stdoutBaseStreamType = $stdoutStream.GetType().FullName
            stderrBaseStreamType = $stderrStream.GetType().FullName
            stdoutIsAsync = [bool]$stdoutStream.IsAsync
            stderrIsAsync = [bool]$stderrStream.IsAsync
            stdoutHandleClosedBefore = [bool]$context.StdoutSafeHandle.IsClosed
            stderrHandleClosedBefore = [bool]$context.StderrSafeHandle.IsClosed
        }

        $context.Phase = 'arm-and-receive-both-capture-streams'
        $context.Capture = & $owner {
            param($Lease, $StdoutLimit, $StderrLimit)
            New-VerifierBoundedCaptureState $Lease $StdoutLimit $StderrLimit
        } $context.Lease $stdoutCap $stderrCap
        & $owner {
            param($Lease, $State, $Watch, $Timeout, $Token)
            Receive-VerifierBoundedCapture $Lease $State $Watch $Timeout $Token
        } $context.Lease $context.Capture $context.OperationWatch $timeoutMilliseconds $cancelToken
        $context.OperationFacts = [ordered]@{
            operationMilliseconds = $context.OperationWatch.Elapsed.TotalMilliseconds
            exitObserved = [bool]$context.Lease.WaitForExit(0)
            exitCode = [long]$context.Lease.GetExitCode()
        }
        Require ($context.OperationFacts.exitObserved) 'captured process lease did not signal after both pipe EOFs'
        Require ($context.OperationFacts.exitCode -eq 0) 'finite duplex fixture exit code was not zero'
        $context.ActorFacts = Assert-ActorWitness $context
        Require ([String]::Equals([IO.Path]::GetFullPath($context.Lease.ImagePath), [IO.Path]::GetFullPath($context.ActorFacts.witness.imagePath), [StringComparison]::OrdinalIgnoreCase)) 'lease image path did not match actor witness'
        if ($context.MemberIdsAtLaunch.Count -eq 0) {
            Require ($context.Lease.WaitForExit(0) -and [bool]$context.ActorFacts.witness.inExpectedJob) 'empty launch-time job snapshot lacked original-handle exit and independent in-job actor proof'
        }
        Require ($context.Capture.Lanes[0].Eof -and $context.Capture.Lanes[1].Eof) 'bounded capture did not observe EOF on both streams'
        Assert-ByteRun $context.Capture.Lanes[0].Bytes.ToArray() ([byte]0x4f) $stdoutBytes 'stdout'
        Assert-ByteRun $context.Capture.Lanes[1].Bytes.ToArray() ([byte]0x45) $stderrBytes 'stderr'
        Require ($context.Capture.Lanes[0].Observed -eq $stdoutBytes -and $context.Capture.Lanes[1].Observed -eq $stderrBytes) 'observed byte totals did not match the finite fixture'
        Require ($context.Capture.Lanes[0].Discarded -eq 0 -and $context.Capture.Lanes[1].Discarded -eq 0) 'successful exact-cap smoke discarded bytes during cleanup'
        $context.Phase = 'verify-root-and-job-closure'
        $context.CleanupFacts = Wait-RootAndJobEmpty $context 5000
        $prematureDisposeFailure = $null
        try { $context.Lease.Dispose() } catch { $prematureDisposeFailure = $_ }
        Require ($null -ne $prematureDisposeFailure) 'captured lease disposed before both read handles closed'
        $prematureDisposeBase = $prematureDisposeFailure.Exception.GetBaseException()
        Require ($prematureDisposeBase.GetType().FullName -ceq 'System.InvalidOperationException') 'premature captured lease disposal exception type changed'
        Require ($prematureDisposeBase.Message -ceq 'Captured process lease cannot close before both read handles are closed.') 'premature captured lease disposal guard changed'
        Require (-not [bool]$context.Lease.IsClosed) 'premature lease disposal changed lease state'
        Require (-not $context.StdoutSafeHandle.IsClosed -and -not $context.StderrSafeHandle.IsClosed) 'premature dispose guard closed a read handle'
        $context.Phase = 'operation-complete'
        $context.OperationWatch.Stop()
    } catch {
        $primaryFailure = $_
        if ($null -ne $context.OperationWatch) { $context.OperationWatch.Stop() }
        if ($null -ne $context.Lease) {
        try {
            $context.StreamFacts = [ordered]@{
                stdoutReaderType = $context.Lease.StandardOutput.GetType().FullName
                stderrReaderType = $context.Lease.StandardError.GetType().FullName
                stdoutBaseStreamType = $context.Lease.StandardOutput.BaseStream.GetType().FullName
                stderrBaseStreamType = $context.Lease.StandardError.BaseStream.GetType().FullName
                stdoutIsAsync = [bool]$context.Lease.StandardOutput.BaseStream.IsAsync
                stderrIsAsync = [bool]$context.Lease.StandardError.BaseStream.IsAsync
                stdoutHandleClosedAfterCapture = if ($null -ne $context.StdoutSafeHandle) { [bool]$context.StdoutSafeHandle.IsClosed } else { $null }
                stderrHandleClosedAfterCapture = if ($null -ne $context.StderrSafeHandle) { [bool]$context.StderrSafeHandle.IsClosed } else { $null }
            }
        } catch { [void]$cleanupFailures.Add('stream metadata could not be retained after operation failure: ' + $_.Exception.Message) }
        }
    }

    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    if ($null -ne $context.Job) {
        try {
            $context.Phase = 'request-owned-job-stop-if-needed-and-prove-closure'
            $members = @($context.Job.GetMemberProcessIds())
            $rootExited = $true
            if ($null -ne $context.Lease) { $rootExited = $context.Lease.WaitForExit(0) }
            if ($members.Count -gt 0 -or -not $rootExited) {
                $context.Job.TerminateOwned([uint32]2)
                $context.TerminationRequested = $true
            }
            $context.CleanupFacts = Wait-RootAndJobEmpty $context 5000
        } catch {
            [void]$cleanupFailures.Add('owned-job stop or closure proof failed: ' + $_.Exception.Message)
        }
    }
    if ($null -ne $context.Lease) {
        $closureProven = ($null -ne $context.CleanupFacts -and $context.CleanupFacts.rootExited -and @($context.CleanupFacts.memberIds).Count -eq 0)
        if ($closureProven) {
            $context.Phase = 'bounded-close-both-readers'
            try {
                Close-CaptureReaders $context
                $context.CaptureFacts = Get-CaptureFacts $context
                if (-not (Test-StreamHandlesClosed $context)) {
                    throw 'both captured read handles were not closed after bounded drain'
                }
                if ($null -ne $context.CaptureFacts) {
                    Require ([bool]$context.CaptureFacts.ReaderTasksTerminal) 'capture reader task remained non-terminal'
                    Require ([bool]$context.CaptureFacts.ReadersClosed) 'capture readers did not close'
                    Require ([string]::IsNullOrEmpty([string]$context.CaptureFacts.CaptureFailureKind)) 'successful duplex capture reported an operation failure kind'
                    Require ($context.CaptureFacts.StdoutByteCount -eq $stdoutBytes -and $context.CaptureFacts.StderrByteCount -eq $stderrBytes) 'capture facts byte counts differed from raw buffers'
                    Require ($context.CaptureFacts.StdoutObservedBytes -eq $stdoutBytes -and $context.CaptureFacts.StderrObservedBytes -eq $stderrBytes) 'capture facts observed counts differed from fixture output'
                    Require ($context.CaptureFacts.StdoutDiscardedDuringCleanup -eq 0 -and $context.CaptureFacts.StderrDiscardedDuringCleanup -eq 0) 'capture facts recorded cleanup discard on finite success'
                }
            } catch {
                [void]$cleanupFailures.Add('bounded reader close failed: ' + $_.Exception.Message)
            }
        } else {
            [void]$cleanupFailures.Add('reader close was withheld because original-process and job closure were not proven')
        }
        if ($null -ne $context.StdoutSafeHandle -and $null -ne $context.StreamFacts) {
            $context.StreamFacts.stdoutHandleClosedAfterCapture = [bool]$context.StdoutSafeHandle.IsClosed
            $context.StreamFacts.stderrHandleClosedAfterCapture = [bool]$context.StderrSafeHandle.IsClosed
        }
        if ($null -ne $context.Capture -and $null -eq $context.CaptureFacts) {
            try { $context.CaptureFacts = Get-CaptureFacts $context }
            catch { [void]$cleanupFailures.Add('capture facts could not be retained: ' + $_.Exception.Message) }
        }
        if ($closureProven -and (Test-StreamHandlesClosed $context)) {
            $context.Phase = 'dispose-closed-process-lease'
            try {
                $context.Lease.Dispose()
                $context.LeaseDisposeProven = [bool]$context.Lease.IsClosed
                Require ($context.LeaseDisposeProven) 'captured process lease handle remained open after disposal'
                $context.Lease.Dispose()
                Require ([bool]$context.Lease.IsClosed) 'captured lease disposal was not idempotent'
            } catch {
                [void]$cleanupFailures.Add('captured process lease disposal failed: ' + $_.Exception.Message)
            }
        } else {
            [void]$cleanupFailures.Add('lease disposal was withheld until process/job closure and both reader handles are proven')
        }
    }
    if ($null -ne $context.CleanupFacts -and $context.CleanupFacts.retainedMemberObserversExited) {
        $observersDisposed = $true
        foreach ($observer in $context.MemberObservers) {
            try { $observer.Process.Dispose() }
            catch { $observersDisposed = $false; [void]$cleanupFailures.Add('retained extra-member observer disposal failed: ' + $_.Exception.Message) }
        }
        $context.MemberObserverDisposeProven = $observersDisposed
    } elseif ($context.MemberObservers.Count -gt 0) {
        [void]$cleanupFailures.Add('retained extra-member observer disposal was withheld without exit proof')
    }
    if ($null -ne $context.Job) {
        $context.Phase = 'dispose-empty-containment-job'
        try {
            Require (@($context.Job.GetMemberProcessIds()).Count -eq 0) 'containment job retained members at disposal'
            $context.Job.Dispose()
            $context.JobDisposeProven = $true
        } catch {
            [void]$cleanupFailures.Add('containment job disposal failed: ' + $_.Exception.Message)
        }
    }
    $cleanupWatch.Stop()
    if ($null -ne $context.CaptureFacts) {
        $context.CaptureFacts.OperationMilliseconds = if ($null -ne $context.OperationWatch) { $context.OperationWatch.Elapsed.TotalMilliseconds } else { 0.0 }
        $context.CaptureFacts.CleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        $context.CaptureFacts.CleanupProven = ($null -eq $primaryFailure -and $cleanupFailures.Count -eq 0 -and $null -ne $context.CleanupFacts -and $context.CleanupFacts.rootExited -and @($context.CleanupFacts.memberIds).Count -eq 0 -and $context.CleanupFacts.retainedMemberObserversExited -and $context.MemberObserverDisposeProven -and $context.LeaseDisposeProven -and $context.JobDisposeProven -and $context.CaptureFacts.ReaderTasksTerminal -and $context.CaptureFacts.ReadersClosed)
    }

    $active = [ordered]@{
        mode = $Mode
        case = 'duplex-smoke'
        jobName = $context.JobName
        memberIdsAtLaunch = @($context.MemberIdsAtLaunch)
        memberIdentityFacts = $context.MemberIdentityFacts
        memberObserverDisposeProven = $context.MemberObserverDisposeProven
        processId = if ($null -ne $context.Lease) { $context.Lease.ProcessId } else { $null }
        creationFileTime = if ($null -ne $context.Lease) { $context.Lease.CreationFileTime } else { $null }
        imagePath = if ($null -ne $context.Lease) { $context.Lease.ImagePath } else { $ChildExecutable }
        childImageSha256 = $ChildImageSha256
        nonce = $context.Nonce
        statePath = (Join-Path $context.StateDirectory 'emit.state.json')
        stdoutLimit = $context.StdoutCap
        stderrLimit = $context.StderrCap
        stdoutPrefixBase64 = if (($null -ne $primaryFailure -or $cleanupFailures.Count -gt 0) -and $null -ne $context.Capture) { [Convert]::ToBase64String($context.Capture.Lanes[0].Bytes.ToArray()) } else { $null }
        stderrPrefixBase64 = if (($null -ne $primaryFailure -or $cleanupFailures.Count -gt 0) -and $null -ne $context.Capture) { [Convert]::ToBase64String($context.Capture.Lanes[1].Bytes.ToArray()) } else { $null }
        captureFacts = $context.CaptureFacts
        streamFacts = $context.StreamFacts
        actorFacts = $context.ActorFacts
        operationFacts = $context.OperationFacts
        cleanupFacts = $context.CleanupFacts
        leaseDisposed = $context.LeaseDisposeProven
        jobDisposed = $context.JobDisposeProven
        phase = $context.Phase
        terminationRequested = $context.TerminationRequested
        primaryFailure = if ($null -ne $primaryFailure) { $primaryFailure.Exception.Message } else { $null }
        cleanupFailures = @($cleanupFailures.ToArray())
        operationMilliseconds = if ($null -ne $context.OperationWatch) { $context.OperationWatch.Elapsed.TotalMilliseconds } else { $null }
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
    }
    $script:activeEvidence = $active
    if ($null -ne $primaryFailure -or $cleanupFailures.Count -gt 0) {
        $summary = 'captured-pipe smoke failed'
        if ($null -ne $primaryFailure) { $summary += ': ' + $primaryFailure.Exception.Message }
        if ($cleanupFailures.Count -gt 0) { $summary += '; cleanup: ' + [String]::Join('; ', $cleanupFailures.ToArray()) }
        throw [InvalidOperationException]::new($summary)
    }

    $row = [ordered]@{
        name = 'duplex-smoke'
        status = 'PASS'
        operationMilliseconds = $context.OperationFacts.operationMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        facts = $active
    }
    $cases.Add($row)
    Save-Case $caseDirectory 'case.json' $row
    $script:activeContext = $null
}

function Run-FixtureCaptureCase {
    param(
        [Parameter(Mandatory=$true)][string]$Name,
        [Parameter(Mandatory=$true)][ValidateSet('emit','hold')][string]$FixtureMode,
        [Parameter(Mandatory=$true)][int]$StdoutBytes,
        [Parameter(Mandatory=$true)][int]$StderrBytes,
        [Parameter(Mandatory=$true)][int]$ExitCode,
        [Parameter(Mandatory=$true)][int]$StdoutCap,
        [Parameter(Mandatory=$true)][int]$StderrCap,
        [Parameter(Mandatory=$true)][int]$TimeoutMilliseconds,
        [Parameter(Mandatory=$true)][ValidateSet('EXIT_ZERO','EXIT_SEVEN','OUTPUT_LIMIT','TIMEOUT','CANCELLED')][string]$ExpectedOutcome,
        [string]$FailureLane = '',
        [int]$CancelAfterMilliseconds = 0
    )
    $caseDirectory = Join-Path $EvidenceRoot $Name
    $null = [IO.Directory]::CreateDirectory($caseDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $caseDirectory
    $stateDirectory = Join-Path $caseDirectory 'states'
    $null = [IO.Directory]::CreateDirectory($stateDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $stateDirectory
    $jobName = 'Local\TroubleshootJS.Verifier.CapturedPipeContract.' + [guid]::NewGuid().ToString('N')
    $nonce = [guid]::NewGuid().ToString('N')
    $fixtureArgs = @($FixtureMode, $stateDirectory, $nonce, $jobName, [string]$StdoutBytes, [string]$StderrBytes, [string]$ExitCode)
    $expectedArgv = @($ChildExecutable) + $fixtureArgs
    $fullCommandLine = Convert-ToWindowsArgumentString $expectedArgv
    $context = [pscustomobject]@{
        Name = $Name
        Job = $null
        Lease = $null
        Capture = $null
        JobName = $jobName
        Nonce = $nonce
        StateDirectory = $stateDirectory
        FixtureMode = $FixtureMode
        ExpectedArgv = $expectedArgv
        ChildExecutable = $ChildExecutable
        ChildImageSha256 = $ChildImageSha256
        StdoutBytes = $StdoutBytes
        StderrBytes = $StderrBytes
        StdoutCap = $StdoutCap
        StderrCap = $StderrCap
        TimeoutMilliseconds = $TimeoutMilliseconds
        OperationWatch = $null
        OperationFacts = $null
        CleanupFacts = $null
        StdoutSafeHandle = $null
        StderrSafeHandle = $null
        LeaseDisposeProven = $false
        JobDisposeProven = $false
        StreamFacts = $null
        MemberIdsAtLaunch = @()
        MemberIdentityFacts = $null
        MemberObservers = (New-Object 'System.Collections.Generic.List[object]')
        MemberObserverDisposeProven = $false
        Phase = 'create-job'
        ActorFacts = $null
        CaptureFacts = $null
        TerminationRequested = $false
        ExpectedOutcome = $ExpectedOutcome
        ExpectedFailureKind = if ($ExpectedOutcome -eq 'OUTPUT_LIMIT') { 'OUTPUT_LIMIT' } elseif ($ExpectedOutcome -eq 'TIMEOUT') { 'TIMEOUT' } elseif ($ExpectedOutcome -eq 'CANCELLED') { 'CANCELLED' } else { '' }
        LeaseLiveAtCaptureFailure = $false
    }
    $script:activeContext = $context
    $primaryFailure = $null
    $captureFailure = $null
    $cleanupFailures = New-Object 'System.Collections.Generic.List[string]'
    $cancelSource = $null

    try {
        $jobType = & $owner { Initialize-VerifierBrowserContainmentJobApi }
        Require ($jobType -is [type] -and $jobType.Name -ceq 'VerifierBrowserContainmentJobApi') 'native containment job type unavailable'
        $context.Job = $jobType::CreateNew($jobName)
        Require (@($context.Job.GetMemberProcessIds()).Count -eq 0) 'new suite capture job was not empty'

        $context.Phase = 'launch-captured-fixture'
        $context.OperationWatch = [Diagnostics.Stopwatch]::StartNew()
        $context.Lease = $context.Job.LaunchCaptured($ChildExecutable, $fullCommandLine, $caseDirectory)
        Require ($null -ne $context.Lease -and $context.Lease.GetType().Name -ceq 'OwnedProcessLease') 'native capture launch did not return an owned process lease'
        Require (-not [bool]$context.Lease.IsClosed) 'native capture lease was closed at launch'
        $stdoutReader = $context.Lease.StandardOutput
        $stderrReader = $context.Lease.StandardError
        $stdoutStream = $stdoutReader.BaseStream
        $stderrStream = $stderrReader.BaseStream
        Require ($stdoutReader.GetType() -eq [IO.StreamReader] -and $stderrReader.GetType() -eq [IO.StreamReader]) 'native capture readers were not exact StreamReader instances'
        Require ($stdoutStream.GetType() -eq [IO.FileStream] -and $stderrStream.GetType() -eq [IO.FileStream]) 'native capture base streams were not exact FileStream instances'
        Require (-not [bool]$stdoutStream.IsAsync -and -not [bool]$stderrStream.IsAsync) 'native capture exposed an unexpected asynchronous handle stream'
        $context.StdoutSafeHandle = $stdoutStream.SafeFileHandle
        $context.StderrSafeHandle = $stderrStream.SafeFileHandle
        Require (-not $context.StdoutSafeHandle.IsClosed -and -not $context.StderrSafeHandle.IsClosed) 'native captured read handle was closed before capture'
        $context.StreamFacts = [ordered]@{
            stdoutReaderType = $stdoutReader.GetType().FullName
            stderrReaderType = $stderrReader.GetType().FullName
            stdoutBaseStreamType = $stdoutStream.GetType().FullName
            stderrBaseStreamType = $stderrStream.GetType().FullName
            stdoutIsAsync = [bool]$stdoutStream.IsAsync
            stderrIsAsync = [bool]$stderrStream.IsAsync
            stdoutHandleClosedBefore = [bool]$context.StdoutSafeHandle.IsClosed
            stderrHandleClosedBefore = [bool]$context.StderrSafeHandle.IsClosed
        }
        $context.MemberIdentityFacts = Observe-CaptureJobMembers $context
        $context.Phase = 'arm-and-receive-both-capture-streams'
        $context.Capture = & $owner {
            param($Lease, $OutLimit, $ErrLimit)
            New-VerifierBoundedCaptureState $Lease $OutLimit $ErrLimit
        } $context.Lease $StdoutCap $StderrCap
        if ($CancelAfterMilliseconds -gt 0) {
            $cancelSource = New-Object Threading.CancellationTokenSource
            $cancelSource.CancelAfter($CancelAfterMilliseconds)
            $cancelToken = $cancelSource.Token
        } else {
            $cancelToken = [Threading.CancellationToken]::None
        }
        try {
            & $owner {
                param($Lease, $State, $Watch, $Timeout, $Token)
                Receive-VerifierBoundedCapture $Lease $State $Watch $Timeout $Token
            } $context.Lease $context.Capture $context.OperationWatch $TimeoutMilliseconds $cancelToken
        } catch { $captureFailure = $_ }

        if ($ExpectedOutcome -eq 'EXIT_ZERO' -or $ExpectedOutcome -eq 'EXIT_SEVEN') {
            Require ($null -eq $captureFailure) ($Name + ' capture operation failed unexpectedly')
            Require ([bool]$context.Lease.WaitForExit(0)) ($Name + ' lease did not signal after both EOFs')
            $observedExitCode = [long]$context.Lease.GetExitCode()
            Require ($observedExitCode -eq $ExitCode) ($Name + ' observed exit code mismatch')
            if ($ExpectedOutcome -eq 'EXIT_ZERO') { Require ($observedExitCode -eq 0) ($Name + ' expected a successful exit') }
            else { Require ($observedExitCode -eq 7) ($Name + ' did not observe the intended nonzero exit') }
        } else {
            Require ($null -ne $captureFailure) ($Name + ' bounded capture unexpectedly succeeded')
            $infra = & $owner { param($ErrorRecord) Test-VerifierInfrastructureError $ErrorRecord } $captureFailure
            Require ([bool]$infra) ($Name + ' failure was not an infrastructure capture failure')
            Require ($context.Capture.FailureKind -ceq $context.ExpectedFailureKind) ($Name + ' capture failure kind mismatch')
            $expectedMessage = if ($ExpectedOutcome -eq 'OUTPUT_LIMIT') {
                $limit = if ($FailureLane -ceq 'stdout') { $StdoutCap } else { $StderrCap }
                'VERIFIER_INFRASTRUCTURE: Bounded process exceeded ' + $FailureLane + ' byte limit=' + $limit + '.'
            } elseif ($ExpectedOutcome -eq 'TIMEOUT') {
                'VERIFIER_INFRASTRUCTURE: Bounded process exceeded ' + $TimeoutMilliseconds + 'ms during byte capture.'
            } else {
                'VERIFIER_INFRASTRUCTURE: Bounded process capture cancelled.'
            }
            Require ($captureFailure.Exception.Message -ceq $expectedMessage) ($Name + ' exact failure message changed')
            $context.LeaseLiveAtCaptureFailure = -not [bool]$context.Lease.WaitForExit(0)
            Require ($context.LeaseLiveAtCaptureFailure) ($Name + ' fixture was not live when the bounded capture failed')
        }

        $context.ActorFacts = Read-FixtureActorWitness (Join-Path $stateDirectory ($FixtureMode + '.state.json')) $FixtureMode $nonce $jobName $ChildExecutable $ChildImageSha256 $expectedArgv $true
        if ($context.MemberIdsAtLaunch.Count -eq 0) {
            Require ($context.Lease.WaitForExit(0) -and [bool]$context.ActorFacts.witness.inExpectedJob) ($Name + ' empty member snapshot lacked original-handle exit and independent in-job actor proof')
        }
        if ($ExpectedOutcome -eq 'EXIT_ZERO' -or $ExpectedOutcome -eq 'EXIT_SEVEN') {
            Require ($context.Capture.Lanes[0].Eof -and $context.Capture.Lanes[1].Eof) ($Name + ' did not observe EOF on both streams')
            Assert-ByteRun $context.Capture.Lanes[0].Bytes.ToArray() ([byte]0x4f) $StdoutBytes ($Name + ' stdout')
            Assert-ByteRun $context.Capture.Lanes[1].Bytes.ToArray() ([byte]0x45) $StderrBytes ($Name + ' stderr')
            Require ($context.Capture.Lanes[0].Observed -eq $StdoutBytes -and $context.Capture.Lanes[1].Observed -eq $StderrBytes) ($Name + ' observed byte totals mismatch')
            Require ($context.Capture.Lanes[0].Discarded -eq 0 -and $context.Capture.Lanes[1].Discarded -eq 0) ($Name + ' successful capture discarded bytes')
        } elseif ($ExpectedOutcome -eq 'OUTPUT_LIMIT') {
            foreach ($lane in $context.Capture.Lanes) {
                Require ($lane.Bytes.Length -le $lane.Limit) ($Name + ' retained bytes past a stream cap')
                $byte = if ($lane.Name -ceq 'stdout') { [byte]0x4f } else { [byte]0x45 }
                Assert-ByteRun $lane.Bytes.ToArray() $byte $lane.Bytes.Length ($Name + ' retained stream prefix')
            }
            if ($FailureLane -ceq 'stdout') {
                Assert-ByteRun $context.Capture.Lanes[0].Bytes.ToArray() ([byte]0x4f) $StdoutCap ($Name + ' retained stdout prefix')
                Require ($context.Capture.Lanes[0].Observed -eq ($StdoutCap + 1)) ($Name + ' did not stop at the first stdout excess byte')
                Require ($context.Capture.Lanes[1].Observed -le $StderrBytes) ($Name + ' stderr observed beyond its declared output')
                if ($StderrBytes -eq 0 -or $context.Capture.Lanes[1].Eof) {
                    Require ($context.Capture.Lanes[1].Observed -eq $StderrBytes) ($Name + ' completed stderr count differed from its declared output')
                }
            } else {
                Assert-ByteRun $context.Capture.Lanes[1].Bytes.ToArray() ([byte]0x45) $StderrCap ($Name + ' retained stderr prefix')
                Require ($context.Capture.Lanes[1].Observed -eq ($StderrCap + 1)) ($Name + ' did not stop at the first stderr excess byte')
                Require ($context.Capture.Lanes[0].Observed -le $StdoutBytes) ($Name + ' stdout observed beyond its declared output')
                if ($StdoutBytes -eq 0 -or $context.Capture.Lanes[0].Eof) {
                    Require ($context.Capture.Lanes[0].Observed -eq $StdoutBytes) ($Name + ' completed stdout count differed from its declared output')
                }
            }
        } else {
            foreach ($lane in $context.Capture.Lanes) {
                Require ($lane.Bytes.Length -le $lane.Limit) ($Name + ' retained bytes past a stream cap')
                $byte = if ($lane.Name -ceq 'stdout') { [byte]0x4f } else { [byte]0x45 }
                Assert-ByteRun $lane.Bytes.ToArray() $byte $lane.Bytes.Length ($Name + ' retained prefix')
            }
        }
        $context.OperationFacts = [ordered]@{
            operationMilliseconds = $context.OperationWatch.Elapsed.TotalMilliseconds
            operationName = if ($ExpectedOutcome -eq 'OUTPUT_LIMIT') { 'OUTPUT_LIMIT' } elseif ($ExpectedOutcome -eq 'TIMEOUT') { 'TIMEOUT' } elseif ($ExpectedOutcome -eq 'CANCELLED') { 'CANCELLED' } elseif ($ExpectedOutcome -eq 'EXIT_SEVEN') { 'EXIT_7' } else { 'CAPTURE_SUCCESS' }
            operationStatus = if ($ExpectedOutcome -in @('OUTPUT_LIMIT','TIMEOUT','CANCELLED')) { 'EXPECTED_FAILURE_NOT_PASS' } elseif ($ExpectedOutcome -eq 'EXIT_SEVEN') { 'EXPECTED_NONZERO_EXIT_NOT_SUCCESS' } else { 'PASS' }
            captureFailureKind = $context.Capture.FailureKind
            failureMessage = if ($null -ne $captureFailure) { $captureFailure.Exception.Message } else { $null }
            exitCode = if ($context.Lease.WaitForExit(0)) { [long]$context.Lease.GetExitCode() } else { $null }
            rootLiveAtFailure = $context.LeaseLiveAtCaptureFailure
        }
        $context.Phase = 'operation-complete'
        $context.OperationWatch.Stop()
    } catch {
        $primaryFailure = $_
        if ($null -ne $context.OperationWatch) { $context.OperationWatch.Stop() }
    } finally {
        if ($null -ne $cancelSource) { $cancelSource.Dispose() }
    }

    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    if ($null -ne $context.Job) {
        try {
            $context.Phase = 'request-owned-job-stop-if-needed-and-prove-closure'
            $members = @($context.Job.GetMemberProcessIds())
            $rootExited = $true
            if ($null -ne $context.Lease) { $rootExited = $context.Lease.WaitForExit(0) }
            if ($members.Count -gt 0 -or -not $rootExited) {
                $context.Job.TerminateOwned([uint32]2)
                $context.TerminationRequested = $true
            }
            $context.CleanupFacts = Wait-RootAndJobEmpty $context 5000
            if ($null -ne $context.Lease) {
                Add-Member -InputObject $context.CleanupFacts -MemberType NoteProperty -Name exitCode -Value ([long]$context.Lease.GetExitCode()) -Force
                if ($context.TerminationRequested -and $context.LeaseLiveAtCaptureFailure) {
                    Require ($context.CleanupFacts.exitCode -eq 2) ($Name + ' proven-live hold did not receive the requested job exit code')
                }
            }
        } catch {
            [void]$cleanupFailures.Add('owned-job stop or closure proof failed: ' + $_.Exception.Message)
        }
    }
    if ($null -ne $context.Lease) {
        $closureProven = ($null -ne $context.CleanupFacts -and $context.CleanupFacts.rootExited -and @($context.CleanupFacts.memberIds).Count -eq 0 -and $context.CleanupFacts.retainedMemberObserversExited)
        if ($closureProven) {
            $context.Phase = 'bounded-close-both-readers'
            try {
                Close-CaptureReaders $context
                $context.CaptureFacts = Get-CaptureFacts $context
                Require (Test-StreamHandlesClosed $context) ($Name + ' both captured read handles did not close')
                Require ([bool]$context.CaptureFacts.ReaderTasksTerminal) ($Name + ' reader task remained non-terminal')
                Require ([bool]$context.CaptureFacts.ReadersClosed) ($Name + ' capture readers did not close')
                $expectedFactFailure = if ($ExpectedOutcome -in @('OUTPUT_LIMIT','TIMEOUT','CANCELLED')) { $context.ExpectedFailureKind } else { '' }
                Require ([string]$context.CaptureFacts.CaptureFailureKind -ceq $expectedFactFailure) ($Name + ' finalized capture failure kind mismatch')
                Require ($context.CaptureFacts.StdoutByteCount -eq $context.Capture.Lanes[0].Bytes.Length -and $context.CaptureFacts.StderrByteCount -eq $context.Capture.Lanes[1].Bytes.Length) ($Name + ' finalized capture byte counts mismatch')
            } catch {
                [void]$cleanupFailures.Add('bounded reader close failed: ' + $_.Exception.Message)
            }
        } else {
            [void]$cleanupFailures.Add('reader close was withheld because original-process and job closure were not proven')
        }
        if ($null -ne $context.CaptureFacts -and $null -ne $context.StreamFacts) {
            $context.StreamFacts.stdoutHandleClosedAfterCapture = [bool]$context.StdoutSafeHandle.IsClosed
            $context.StreamFacts.stderrHandleClosedAfterCapture = [bool]$context.StderrSafeHandle.IsClosed
        }
        if ($closureProven -and (Test-StreamHandlesClosed $context)) {
            $context.Phase = 'dispose-closed-process-lease'
            try {
                $context.Lease.Dispose()
                $context.LeaseDisposeProven = [bool]$context.Lease.IsClosed
                Require ($context.LeaseDisposeProven) ($Name + ' lease handle remained open after disposal')
            } catch {
                [void]$cleanupFailures.Add('captured process lease disposal failed: ' + $_.Exception.Message)
            }
        } else {
            [void]$cleanupFailures.Add('lease disposal was withheld until process/job closure and both reader handles are proven')
        }
    }
    if ($null -ne $context.CleanupFacts -and $context.CleanupFacts.retainedMemberObserversExited) {
        $observersDisposed = $true
        foreach ($observer in $context.MemberObservers) {
            try { $observer.Process.Dispose() }
            catch { $observersDisposed = $false; [void]$cleanupFailures.Add('retained extra-member observer disposal failed: ' + $_.Exception.Message) }
        }
        $context.MemberObserverDisposeProven = $observersDisposed
    } elseif ($context.MemberObservers.Count -gt 0) {
        [void]$cleanupFailures.Add('retained extra-member observer disposal was withheld without exit proof')
    } else {
        $context.MemberObserverDisposeProven = ($null -ne $context.CleanupFacts -and $context.CleanupFacts.retainedMemberObserversExited)
    }
    if ($null -ne $context.Job) {
        $context.Phase = 'dispose-empty-containment-job'
        try {
            Require (@($context.Job.GetMemberProcessIds()).Count -eq 0) ($Name + ' job retained members at disposal')
            $context.Job.Dispose()
            $context.JobDisposeProven = $true
        } catch {
            [void]$cleanupFailures.Add('containment job disposal failed: ' + $_.Exception.Message)
        }
    }
    $cleanupWatch.Stop()
    if ($null -ne $context.Capture) {
        if ($null -eq $context.CaptureFacts) {
            try { $context.CaptureFacts = Get-CaptureFacts $context }
            catch { [void]$cleanupFailures.Add('capture facts could not be retained: ' + $_.Exception.Message) }
        }
        if ($null -ne $context.CaptureFacts) {
            $context.CaptureFacts.OperationMilliseconds = if ($null -ne $context.OperationWatch) { $context.OperationWatch.Elapsed.TotalMilliseconds } else { 0.0 }
            $context.CaptureFacts.CleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
            $context.CaptureFacts.CleanupProven = ($null -eq $primaryFailure -and $cleanupFailures.Count -eq 0 -and $null -ne $context.CleanupFacts -and $context.CleanupFacts.rootExited -and @($context.CleanupFacts.memberIds).Count -eq 0 -and $context.CleanupFacts.retainedMemberObserversExited -and $context.MemberObserverDisposeProven -and $context.LeaseDisposeProven -and $context.JobDisposeProven -and $context.CaptureFacts.ReaderTasksTerminal -and $context.CaptureFacts.ReadersClosed)
        }
    }

    $active = [ordered]@{
        case = $Name
        fixtureMode = $FixtureMode
        jobName = $jobName
        nonce = $nonce
        processId = if ($null -ne $context.Lease) { $context.Lease.ProcessId } else { $null }
        creationFileTime = if ($null -ne $context.Lease) { $context.Lease.CreationFileTime } else { $null }
        imagePath = if ($null -ne $context.Lease) { $context.Lease.ImagePath } else { $ChildExecutable }
        imageSha256 = $ChildImageSha256
        expectedArgv = $expectedArgv
        statePath = Join-Path $stateDirectory ($FixtureMode + '.state.json')
        memberIdsAtLaunch = @($context.MemberIdsAtLaunch)
        memberIdentityFacts = $context.MemberIdentityFacts
        stdoutLimit = $StdoutCap
        stderrLimit = $StderrCap
        stdoutPrefixBase64 = if ($null -ne $context.Capture -and ($null -ne $primaryFailure -or $null -ne $captureFailure -or $cleanupFailures.Count -gt 0)) { [Convert]::ToBase64String($context.Capture.Lanes[0].Bytes.ToArray()) } else { $null }
        stderrPrefixBase64 = if ($null -ne $context.Capture -and ($null -ne $primaryFailure -or $null -ne $captureFailure -or $cleanupFailures.Count -gt 0)) { [Convert]::ToBase64String($context.Capture.Lanes[1].Bytes.ToArray()) } else { $null }
        operationFacts = $context.OperationFacts
        captureFacts = $context.CaptureFacts
        streamFacts = $context.StreamFacts
        actorFacts = $context.ActorFacts
        cleanupFacts = $context.CleanupFacts
        leaseDisposed = $context.LeaseDisposeProven
        memberObserversDisposed = $context.MemberObserverDisposeProven
        jobDisposed = $context.JobDisposeProven
        terminationRequested = $context.TerminationRequested
        phase = $context.Phase
        primaryFailure = if ($null -ne $primaryFailure) { $primaryFailure.Exception.Message } else { $null }
        captureFailure = if ($null -ne $captureFailure) { $captureFailure.Exception.Message } else { $null }
        cleanupFailures = @($cleanupFailures.ToArray())
        operationMilliseconds = if ($null -ne $context.OperationWatch) { $context.OperationWatch.Elapsed.TotalMilliseconds } else { $null }
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
    }
    $script:activeEvidence = $active
    if ($null -ne $primaryFailure -or $cleanupFailures.Count -gt 0) {
        $summary = 'captured-pipe case ' + $Name + ' failed'
        if ($null -ne $primaryFailure) { $summary += ': ' + $primaryFailure.Exception.Message }
        if ($cleanupFailures.Count -gt 0) { $summary += '; cleanup: ' + [String]::Join('; ', $cleanupFailures.ToArray()) }
        throw [InvalidOperationException]::new($summary)
    }

    $operationStatus = if ($ExpectedOutcome -in @('OUTPUT_LIMIT','TIMEOUT','CANCELLED')) { 'EXPECTED_FAILURE_NOT_PASS' } elseif ($ExpectedOutcome -eq 'EXIT_SEVEN') { 'EXPECTED_NONZERO_EXIT_NOT_SUCCESS' } else { 'PASS' }
    $row = [ordered]@{
        name = $Name
        status = 'PASS'
        operationStatus = $operationStatus
        operationMilliseconds = $context.OperationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        facts = $active
    }
    $cases.Add($row)
    Save-Case $caseDirectory 'case.json' $row
    $script:activeContext = $null
}

function Run-DescendantWriterCase {
    $Name = 'descendant-writer-job-stop'
    $caseDirectory = Join-Path $EvidenceRoot $Name
    $null = [IO.Directory]::CreateDirectory($caseDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $caseDirectory
    $stateDirectory = Join-Path $caseDirectory 'states'
    $null = [IO.Directory]::CreateDirectory($stateDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $stateDirectory
    $jobName = 'Local\TroubleshootJS.Verifier.CapturedPipeContract.' + [guid]::NewGuid().ToString('N')
    $nonce = [guid]::NewGuid().ToString('N')
    $childFixtureArgs = @('hold', $stateDirectory, $nonce, $jobName, '1024', '1024', '0')
    $childExpectedArgv = @($ChildExecutable) + $childFixtureArgs
    $childArgumentString = Convert-ToWindowsArgumentString $childFixtureArgs
    $parentFixtureArgs = @('parent-writer', $stateDirectory, $nonce, $jobName, $childArgumentString)
    $parentExpectedArgv = @($ChildExecutable) + $parentFixtureArgs
    $fullCommandLine = Convert-ToWindowsArgumentString $parentExpectedArgv
    $stdoutCap = 4096
    $stderrCap = 4096
    $timeoutMilliseconds = 1500
    $context = [pscustomobject]@{
        Name = $Name
        Job = $null
        Lease = $null
        Capture = $null
        JobName = $jobName
        Nonce = $nonce
        StateDirectory = $stateDirectory
        ChildExecutable = $ChildExecutable
        ChildImageSha256 = $ChildImageSha256
        ParentExpectedArgv = $parentExpectedArgv
        ChildExpectedArgv = $childExpectedArgv
        StdoutBytes = 1024
        StderrBytes = 1024
        StdoutCap = $stdoutCap
        StderrCap = $stderrCap
        TimeoutMilliseconds = $timeoutMilliseconds
        OperationWatch = $null
        OperationFacts = $null
        CleanupFacts = $null
        StdoutSafeHandle = $null
        StderrSafeHandle = $null
        LeaseDisposeProven = $false
        JobDisposeProven = $false
        StreamFacts = $null
        MemberIdsAtLaunch = @()
        MemberIdentityFacts = $null
        MemberObservers = (New-Object 'System.Collections.Generic.List[object]')
        MemberObserverDisposeProven = $false
        Phase = 'create-job'
        ParentActorFacts = $null
        ChildActorFacts = $null
        ChildActor = $null
        ChildObserver = $null
        ChildClosureFacts = $null
        CaptureFacts = $null
        PreStopCaptureFacts = $null
        TerminationRequested = $false
        LeaseLiveAtCaptureFailure = $false
        SentinelProcess = $null
        SentinelProcessId = $null
        SentinelStartTicks = 0L
        SentinelHandle = [IntPtr]::Zero
        SentinelWitnessFacts = $null
        SentinelJobMemberIdsBeforeStop = @()
        SentinelJobMemberIdsAfterJobStop = @()
        SentinelCleanupProven = $false
        SentinelCleanupFacts = $null
        ChildExitCodeAfterJobStop = $null
    }
    $script:activeContext = $context
    $script:activeEvidence = [ordered]@{ case = $Name; phase = 'create-job'; jobName = $jobName }
    $primaryFailure = $null
    $captureFailure = $null
    $cleanupFailures = New-Object 'System.Collections.Generic.List[string]'
    $childWasLiveBeforeStop = $false
    $sentinelWasLiveAfterJobStop = $false

    try {
        $jobType = & $owner { Initialize-VerifierBrowserContainmentJobApi }
        Require ($jobType -is [type] -and $jobType.Name -ceq 'VerifierBrowserContainmentJobApi') 'native containment job type unavailable'
        $context.Job = $jobType::CreateNew($jobName)
        Require (@($context.Job.GetMemberProcessIds()).Count -eq 0) 'new descendant capture job was not empty'

        $context.Phase = 'launch-parent-writer-with-captured-pipes'
        $context.OperationWatch = [Diagnostics.Stopwatch]::StartNew()
        $context.Lease = $context.Job.LaunchCaptured($ChildExecutable, $fullCommandLine, $caseDirectory)
        Require ($null -ne $context.Lease -and $context.Lease.GetType().Name -ceq 'OwnedProcessLease') 'parent-writer capture launch did not return an owned process lease'
        Require (-not [bool]$context.Lease.IsClosed) 'parent-writer native capture lease was closed at launch'
        $stdoutReader = $context.Lease.StandardOutput
        $stderrReader = $context.Lease.StandardError
        $stdoutStream = $stdoutReader.BaseStream
        $stderrStream = $stderrReader.BaseStream
        Require ($stdoutReader.GetType() -eq [IO.StreamReader] -and $stderrReader.GetType() -eq [IO.StreamReader]) 'parent-writer capture readers were not exact StreamReader instances'
        Require ($stdoutStream.GetType() -eq [IO.FileStream] -and $stderrStream.GetType() -eq [IO.FileStream]) 'parent-writer capture base streams were not exact FileStream instances'
        Require (-not [bool]$stdoutStream.IsAsync -and -not [bool]$stderrStream.IsAsync) 'parent-writer capture exposed an unexpected asynchronous handle stream'
        $context.StdoutSafeHandle = $stdoutStream.SafeFileHandle
        $context.StderrSafeHandle = $stderrStream.SafeFileHandle
        Require (-not $context.StdoutSafeHandle.IsClosed -and -not $context.StderrSafeHandle.IsClosed) 'parent-writer captured read handle was closed before capture'
        $context.StreamFacts = [ordered]@{
            stdoutReaderType = $stdoutReader.GetType().FullName
            stderrReaderType = $stderrReader.GetType().FullName
            stdoutBaseStreamType = $stdoutStream.GetType().FullName
            stderrBaseStreamType = $stderrStream.GetType().FullName
            stdoutIsAsync = [bool]$stdoutStream.IsAsync
            stderrIsAsync = [bool]$stderrStream.IsAsync
            stdoutHandleClosedBefore = [bool]$context.StdoutSafeHandle.IsClosed
            stderrHandleClosedBefore = [bool]$context.StderrSafeHandle.IsClosed
        }
        $context.Capture = & $owner {
            param($Lease, $OutLimit, $ErrLimit)
            New-VerifierBoundedCaptureState $Lease $OutLimit $ErrLimit
        } $context.Lease $stdoutCap $stderrCap
        try {
            & $owner {
                param($Lease, $State, $Watch, $Timeout)
                Receive-VerifierBoundedCapture $Lease $State $Watch $Timeout ([Threading.CancellationToken]::None)
            } $context.Lease $context.Capture $context.OperationWatch $timeoutMilliseconds
        } catch { $captureFailure = $_ }

        Require ($null -ne $captureFailure) 'descendant-held capture unexpectedly reached both pipe EOFs'
        $infra = & $owner { param($ErrorRecord) Test-VerifierInfrastructureError $ErrorRecord } $captureFailure
        Require ([bool]$infra) 'descendant-held capture timeout was not an infrastructure capture failure'
        Require ($context.Capture.FailureKind -ceq 'TIMEOUT') 'descendant-held capture did not report TIMEOUT'
        $expectedTimeoutMessage = 'VERIFIER_INFRASTRUCTURE: Bounded process exceeded ' + $timeoutMilliseconds + 'ms during byte capture.'
        Require ($captureFailure.Exception.Message -ceq $expectedTimeoutMessage) 'descendant-held capture timeout message changed'
        Require ([bool]$context.Lease.WaitForExit(0)) 'parent-writer original handle had not exited before the pipe wait timed out'
        Require ([long]$context.Lease.GetExitCode() -eq 0) 'parent-writer did not exit naturally with code zero'

        $context.ParentActorFacts = Read-FixtureActorWitness (Join-Path $stateDirectory 'parent-writer.state.json') 'parent-writer' $nonce $jobName $ChildExecutable $ChildImageSha256 $parentExpectedArgv $true
        Require ([long]$context.ParentActorFacts.witness.pid -eq [long]$context.Lease.ProcessId) 'parent-writer witness PID differed from the retained original process'
        Require ([long]$context.ParentActorFacts.witness.creationFileTime -eq [long]$context.Lease.CreationFileTime) 'parent-writer witness creation identity differed from the retained original process'
        $context.ChildActorFacts = Read-FixtureActorWitness (Join-Path $stateDirectory 'hold.state.json') 'hold' $nonce $jobName $ChildExecutable $ChildImageSha256 $childExpectedArgv $true
        Require ([long]$context.ChildActorFacts.witness.pid -ne [long]$context.Lease.ProcessId) 'descendant writer PID duplicated the parent process'
        $context.ChildActor = [pscustomobject]@{
            Role = 'fixture-descendant-writer'
            Witness = $context.ChildActorFacts.witness
            ImagePath = $ChildExecutable
            ImageSha256 = $ChildImageSha256
        }
        $context.MemberIdentityFacts = Observe-CaptureJobMembers $context @($context.ChildActor)
        $context.MemberIdsAtLaunch = @($context.MemberIdentityFacts.memberIds)
        $childObserver = @($context.MemberObservers | Where-Object { [long]$_.Identity.processId -eq [long]$context.ChildActor.Witness.pid })
        Require ($childObserver.Count -eq 1 -and -not [bool]$childObserver[0].Process.HasExited) 'descendant original observer did not prove a live child before job stop'
        $context.ChildObserver = $childObserver[0]
        $childWasLiveBeforeStop = $true
        $context.PreStopCaptureFacts = Get-CaptureFacts $context
        Require (-not [bool]$context.PreStopCaptureFacts.ReaderTasksTerminal -and -not [bool]$context.PreStopCaptureFacts.ReadersClosed) 'capture reader tasks were not pending while descendant writer held the pipe handles'
        Require (-not [bool]$context.Capture.Lanes[0].Eof -and -not [bool]$context.Capture.Lanes[1].Eof) 'descendant pipe EOF arrived before the owned-job stop'
        Require ($context.PreStopCaptureFacts.StdoutObservedBytes -eq 1024 -and $context.PreStopCaptureFacts.StderrObservedBytes -eq 1024) 'descendant output was incomplete before the pipe wait timed out'
        Assert-ByteRun $context.Capture.Lanes[0].Bytes.ToArray() ([byte]0x4f) 1024 'descendant pre-stop stdout'
        Assert-ByteRun $context.Capture.Lanes[1].Bytes.ToArray() ([byte]0x45) 1024 'descendant pre-stop stderr'

        $sentinelDirectory = Join-Path $caseDirectory 'outside-sentinel'
        $null = [IO.Directory]::CreateDirectory($sentinelDirectory)
        & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $sentinelDirectory
        $sentinelNonce = [guid]::NewGuid().ToString('N')
        $sentinelArgs = @('sentinel', $sentinelDirectory, $sentinelNonce, $jobName)
        $sentinelExpectedArgv = @($ChildExecutable) + $sentinelArgs
        $context.Phase = 'launch-and-identify-outside-sentinel'
        $context.SentinelProcess = & $owner {
            param($Executable, $Arguments)
            Start-VerifierProcess $Executable $Arguments
        } $ChildExecutable $sentinelArgs
        Require ($context.SentinelProcess.GetType() -eq [Diagnostics.Process]) 'outside sentinel launch did not return its original Process object'
        $context.SentinelProcessId = [long]$context.SentinelProcess.Id
        $sentinelHandle = $context.SentinelProcess.Handle
        Require ($sentinelHandle -ne [IntPtr]::Zero -and $sentinelHandle -ne [IntPtr]::new(-1)) 'outside sentinel original process handle was invalid'
        $context.SentinelHandle = $sentinelHandle
        $context.SentinelStartTicks = [long]$context.SentinelProcess.StartTime.ToUniversalTime().Ticks
        $sentinelCreationFileTime = [long]$context.SentinelProcess.StartTime.ToUniversalTime().ToFileTimeUtc()
        $sentinelImagePath = [IO.Path]::GetFullPath($context.SentinelProcess.MainModule.FileName)
        $sentinelImageSha256 = Sha $sentinelImagePath
        $sentinelStatePath = Join-Path $sentinelDirectory 'sentinel.state.json'
        $sentinelDeadline = [Diagnostics.Stopwatch]::StartNew()
        while (-not [IO.File]::Exists($sentinelStatePath) -and $sentinelDeadline.ElapsedMilliseconds -lt 3000) {
            Start-Sleep -Milliseconds 20
        }
        $context.SentinelWitnessFacts = Read-FixtureActorWitness $sentinelStatePath 'sentinel' $sentinelNonce $jobName $ChildExecutable $ChildImageSha256 $sentinelExpectedArgv $false
        Require ([long]$context.SentinelWitnessFacts.witness.pid -eq [long]$context.SentinelProcess.Id) 'outside sentinel witness PID differed from its retained launch handle'
        Require ([long]$context.SentinelWitnessFacts.witness.creationFileTime -eq $sentinelCreationFileTime) 'outside sentinel witness creation identity differed from its retained launch handle'
        Require ([long]$context.SentinelProcess.StartTime.ToUniversalTime().Ticks -eq $context.SentinelStartTicks) 'outside sentinel start identity changed during witness validation'
        Require ([String]::Equals($sentinelImagePath, $ChildExecutable, [StringComparison]::OrdinalIgnoreCase) -and $sentinelImageSha256 -ceq $ChildImageSha256) 'outside sentinel launched image identity mismatch'
        Require (-not [bool]$context.SentinelProcess.HasExited) 'outside sentinel exited before containment stop'
        $context.SentinelJobMemberIdsBeforeStop = @($context.Job.GetMemberProcessIds() | ForEach-Object { [long]$_ })
        Require ($context.SentinelJobMemberIdsBeforeStop -notcontains [long]$context.SentinelProcess.Id) 'outside sentinel unexpectedly belonged to the capture job before termination'

        $context.OperationFacts = [ordered]@{
            operationName = 'DESCENDANT_WRITER_EOF_WAIT'
            operationStatus = 'EXPECTED_FAILURE_NOT_PASS'
            failureKind = $context.Capture.FailureKind
            failureMessage = $captureFailure.Exception.Message
            timeoutMilliseconds = $timeoutMilliseconds
            operationMilliseconds = $context.OperationWatch.Elapsed.TotalMilliseconds
            parentExitObservedBeforeJobStop = [bool]$context.Lease.WaitForExit(0)
            parentExitCodeBeforeJobStop = [long]$context.Lease.GetExitCode()
            childLiveBeforeJobStop = $childWasLiveBeforeStop
            readerTasksPendingBeforeJobStop = (-not [bool]$context.PreStopCaptureFacts.ReaderTasksTerminal -and -not [bool]$context.PreStopCaptureFacts.ReadersClosed)
            outsideSentinelOutsideExpectedJob = ([bool]$context.SentinelWitnessFacts.witness.inExpectedJob -eq $false)
        }
        $context.OperationWatch.Stop()
    } catch {
        $primaryFailure = $_
        if ($null -ne $context.OperationWatch) { $context.OperationWatch.Stop() }
    }

    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    if ($null -ne $context.Job) {
        try {
            $context.Phase = 'request-owned-job-stop-and-prove-descendant-closure'
            $members = @($context.Job.GetMemberProcessIds())
            if ($members.Count -gt 0) {
                $context.Job.TerminateOwned([uint32]2)
                $context.TerminationRequested = $true
            }
            $context.CleanupFacts = Wait-RootAndJobEmpty $context 5000
            if ($null -ne $context.Lease) {
                Add-Member -InputObject $context.CleanupFacts -MemberType NoteProperty -Name parentExitCode -Value ([long]$context.Lease.GetExitCode()) -Force
                Require ($context.CleanupFacts.parentExitCode -eq 0) 'parent-writer original handle did not retain its natural zero exit'
            }
            if ($childWasLiveBeforeStop -and $context.TerminationRequested) {
                $childObserver = $context.ChildObserver
                $context.ChildClosureFacts = [ordered]@{
                    processId = [long]$childObserver.Identity.processId
                    expectedProcessId = [long]$context.ChildActor.Witness.pid
                    exitSignaled = [bool]$childObserver.Process.WaitForExit(0)
                    originalHandleStable = ($childObserver.Process.Handle -eq $childObserver.Handle)
                    originalStartStable = ([long]$childObserver.Process.StartTime.ToUniversalTime().Ticks -eq [long]$childObserver.Identity.startTicks)
                }
                Require ($context.ChildClosureFacts.processId -eq $context.ChildClosureFacts.expectedProcessId -and $context.ChildClosureFacts.exitSignaled -and $context.ChildClosureFacts.originalHandleStable -and $context.ChildClosureFacts.originalStartStable) 'retained descendant observer did not signal with stable original identity after job termination'
                $childObserver.Process.Refresh()
                $context.ChildExitCodeAfterJobStop = [long]$childObserver.Process.ExitCode
                Require ($context.ChildExitCodeAfterJobStop -eq 2) 'live descendant did not receive the requested job termination code'
            }
        } catch {
            [void]$cleanupFailures.Add('owned-job stop or descendant closure proof failed: ' + $_.Exception.Message)
        }
    }

    if ($null -ne $context.SentinelProcess) {
        try {
            Require ($null -ne $context.CleanupFacts -and $context.CleanupFacts.rootExited -and @($context.CleanupFacts.memberIds).Count -eq 0) 'outside sentinel survival check lacked target-job closure proof'
            $context.SentinelProcess.Refresh()
            Require (-not [bool]$context.SentinelProcess.HasExited) 'outside sentinel did not survive the target-job termination request'
            Require ($context.SentinelProcess.Handle -eq $context.SentinelHandle) 'outside sentinel process handle changed after target-job termination'
            Require ([long]$context.SentinelProcess.StartTime.ToUniversalTime().Ticks -eq $context.SentinelStartTicks) 'outside sentinel start identity changed after target-job termination'
            $context.SentinelJobMemberIdsAfterJobStop = @($context.Job.GetMemberProcessIds() | ForEach-Object { [long]$_ })
            Require ($context.SentinelJobMemberIdsAfterJobStop -notcontains [long]$context.SentinelProcess.Id) 'outside sentinel entered the capture job during termination'
            $sentinelWasLiveAfterJobStop = $true
        } catch {
            [void]$cleanupFailures.Add('outside sentinel survival proof failed: ' + $_.Exception.Message)
        }
    }

    if ($null -ne $context.Lease) {
        $closureProven = ($null -ne $context.CleanupFacts -and $context.CleanupFacts.rootExited -and @($context.CleanupFacts.memberIds).Count -eq 0 -and $context.CleanupFacts.retainedMemberObserversExited)
        if ($closureProven) {
            try {
                $context.Phase = 'bounded-drain-and-close-captured-readers'
                Close-CaptureReaders $context
                $context.CaptureFacts = Get-CaptureFacts $context
                Require (Test-StreamHandlesClosed $context) 'descendant capture read handles did not close after job drain'
                Require ([bool]$context.CaptureFacts.ReaderTasksTerminal -and [bool]$context.CaptureFacts.ReadersClosed) 'descendant capture readers did not settle after job drain'
                Require ($context.Capture.Lanes[0].Eof -and $context.Capture.Lanes[1].Eof) 'descendant writer pipes did not reach EOF after owned-job stop'
                Assert-ByteRun $context.Capture.Lanes[0].Bytes.ToArray() ([byte]0x4f) 1024 'descendant stdout'
                Assert-ByteRun $context.Capture.Lanes[1].Bytes.ToArray() ([byte]0x45) 1024 'descendant stderr'
                Require ([string]$context.CaptureFacts.CaptureFailureKind -ceq 'TIMEOUT') 'descendant final capture facts lost the expected timeout'
                Require ($context.CaptureFacts.StdoutByteCount -eq 1024 -and $context.CaptureFacts.StderrByteCount -eq 1024) 'descendant final retained byte totals changed'
                Require ($context.CaptureFacts.StdoutObservedBytes -eq 1024 -and $context.CaptureFacts.StderrObservedBytes -eq 1024) 'descendant final observed byte totals changed'
                Require ($context.CaptureFacts.StdoutDiscardedDuringCleanup -eq 0 -and $context.CaptureFacts.StderrDiscardedDuringCleanup -eq 0) 'descendant cleanup unexpectedly discarded fixture output'
                $context.StreamFacts.stdoutHandleClosedAfterCapture = [bool]$context.StdoutSafeHandle.IsClosed
                $context.StreamFacts.stderrHandleClosedAfterCapture = [bool]$context.StderrSafeHandle.IsClosed
            } catch {
                [void]$cleanupFailures.Add('descendant capture reader close failed: ' + $_.Exception.Message)
            }
        } else {
            [void]$cleanupFailures.Add('descendant reader close was withheld because original-process/job closure was not proven')
        }
        if ($closureProven -and (Test-StreamHandlesClosed $context)) {
            try {
                $context.Lease.Dispose()
                $context.LeaseDisposeProven = [bool]$context.Lease.IsClosed
                Require $context.LeaseDisposeProven 'descendant parent lease did not dispose after reader closure'
            } catch { [void]$cleanupFailures.Add('descendant parent lease disposal failed: ' + $_.Exception.Message) }
        } else { [void]$cleanupFailures.Add('descendant parent lease disposal was withheld without closure proof') }
    }

    if ($context.MemberObservers.Count -gt 0 -and $null -ne $context.CleanupFacts -and $context.CleanupFacts.retainedMemberObserversExited) {
        $disposed = $true
        foreach ($observer in $context.MemberObservers) {
            try { $observer.Process.Dispose() }
            catch { $disposed = $false; [void]$cleanupFailures.Add('retained descendant/job-member observer disposal failed: ' + $_.Exception.Message) }
        }
        $context.MemberObserverDisposeProven = $disposed
    } elseif ($context.MemberObservers.Count -gt 0) {
        [void]$cleanupFailures.Add('retained descendant/job-member observers were not disposed without signal proof')
    } else {
        $context.MemberObserverDisposeProven = ($null -ne $context.CleanupFacts -and $context.CleanupFacts.retainedMemberObserversExited)
    }

    if ($null -ne $context.SentinelProcess) {
        try {
            $context.Phase = 'stop-outside-sentinel-through-retained-original-handle'
            & $owner {
                param($Process, $StartTicks)
                Stop-VerifierBoundedProcessExactly $Process $StartTicks 5000
            } $context.SentinelProcess $context.SentinelStartTicks
            Require ([bool]$context.SentinelProcess.WaitForExit(0)) 'outside sentinel original handle did not signal after exact cleanup'
            Require ($context.SentinelProcess.Handle -eq $context.SentinelHandle) 'outside sentinel cleanup changed its retained original handle'
            Require ([long]$context.SentinelProcess.StartTime.ToUniversalTime().Ticks -eq $context.SentinelStartTicks) 'outside sentinel cleanup changed its original start identity'
            $context.SentinelCleanupFacts = [ordered]@{
                processId = $context.SentinelProcessId
                startTicks = $context.SentinelStartTicks
                exitSignaled = [bool]$context.SentinelProcess.WaitForExit(0)
                originalHandleStable = ($context.SentinelProcess.Handle -eq $context.SentinelHandle)
                originalStartStable = ([long]$context.SentinelProcess.StartTime.ToUniversalTime().Ticks -eq $context.SentinelStartTicks)
                exitCode = [long]$context.SentinelProcess.ExitCode
            }
            $context.SentinelProcess.Dispose()
            $context.SentinelCleanupProven = $true
        } catch { [void]$cleanupFailures.Add('outside sentinel exact cleanup failed: ' + $_.Exception.Message) }
    }

    if ($null -ne $context.Job) {
        try {
            Require (@($context.Job.GetMemberProcessIds()).Count -eq 0) 'descendant containment job retained members at disposal'
            $context.Job.Dispose()
            $context.JobDisposeProven = $true
        } catch { [void]$cleanupFailures.Add('descendant containment job disposal failed: ' + $_.Exception.Message) }
    }
    $cleanupWatch.Stop()
    if ($null -ne $context.Capture -and $null -eq $context.CaptureFacts) {
        try { $context.CaptureFacts = Get-CaptureFacts $context }
        catch { [void]$cleanupFailures.Add('descendant capture facts could not be retained: ' + $_.Exception.Message) }
    }
    if ($null -ne $context.CaptureFacts) {
        $context.CaptureFacts.OperationMilliseconds = if ($null -ne $context.OperationWatch) { $context.OperationWatch.Elapsed.TotalMilliseconds } else { 0.0 }
        $context.CaptureFacts.CleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        $context.CaptureFacts.CleanupProven = ($null -eq $primaryFailure -and $cleanupFailures.Count -eq 0 -and $null -ne $context.CleanupFacts -and $context.CleanupFacts.rootExited -and @($context.CleanupFacts.memberIds).Count -eq 0 -and $context.CleanupFacts.retainedMemberObserversExited -and $context.MemberObserverDisposeProven -and $context.LeaseDisposeProven -and $context.JobDisposeProven -and $childWasLiveBeforeStop -and $context.ChildExitCodeAfterJobStop -eq 2 -and $sentinelWasLiveAfterJobStop -and $context.SentinelCleanupProven -and $context.CaptureFacts.ReaderTasksTerminal -and $context.CaptureFacts.ReadersClosed)
    }

    $active = [ordered]@{
        case = $Name
        jobName = $jobName
        nonce = $nonce
        parent = $context.ParentActorFacts
        descendant = $context.ChildActorFacts
        parentPid = if ($null -ne $context.Lease) { $context.Lease.ProcessId } else { $null }
        parentCreationFileTime = if ($null -ne $context.Lease) { $context.Lease.CreationFileTime } else { $null }
        parentExitBeforeStop = if ($null -ne $context.OperationFacts) { $context.OperationFacts.parentExitObservedBeforeJobStop } else { $null }
        parentExitCode = if ($null -ne $context.CleanupFacts -and $null -ne $context.CleanupFacts.PSObject.Properties['parentExitCode']) { [long]$context.CleanupFacts.parentExitCode } else { $null }
        memberIdsAtChildSnapshot = @($context.MemberIdsAtLaunch)
        memberIdentityFacts = $context.MemberIdentityFacts
        timeoutMilliseconds = $timeoutMilliseconds
        stdoutLimit = $stdoutCap
        stderrLimit = $stderrCap
        stdoutPrefixBase64 = if ($null -ne $context.Capture) { [Convert]::ToBase64String($context.Capture.Lanes[0].Bytes.ToArray()) } else { $null }
        stderrPrefixBase64 = if ($null -ne $context.Capture) { [Convert]::ToBase64String($context.Capture.Lanes[1].Bytes.ToArray()) } else { $null }
        operationFacts = $context.OperationFacts
        preStopCaptureFacts = $context.PreStopCaptureFacts
        captureFacts = $context.CaptureFacts
        streamFacts = $context.StreamFacts
        childExitCodeAfterJobStop = $context.ChildExitCodeAfterJobStop
        childClosureFacts = $context.ChildClosureFacts
        outsideSentinel = $context.SentinelWitnessFacts
        outsideSentinelPid = $context.SentinelProcessId
        outsideSentinelJobMemberIdsBeforeStop = @($context.SentinelJobMemberIdsBeforeStop)
        outsideSentinelJobMemberIdsAfterStop = @($context.SentinelJobMemberIdsAfterJobStop)
        outsideSentinelCleanup = $context.SentinelCleanupFacts
        outsideSentinelAliveAfterJobStop = $sentinelWasLiveAfterJobStop
        outsideSentinelCleanedByRetainedOriginalHandle = $context.SentinelCleanupProven
        cleanupFacts = $context.CleanupFacts
        leaseDisposed = $context.LeaseDisposeProven
        memberObserversDisposed = $context.MemberObserverDisposeProven
        jobDisposed = $context.JobDisposeProven
        terminationRequested = $context.TerminationRequested
        phase = $context.Phase
        primaryFailure = if ($null -ne $primaryFailure) { $primaryFailure.Exception.Message } else { $null }
        captureFailure = if ($null -ne $captureFailure) { $captureFailure.Exception.Message } else { $null }
        cleanupFailures = @($cleanupFailures.ToArray())
        operationMilliseconds = if ($null -ne $context.OperationWatch) { $context.OperationWatch.Elapsed.TotalMilliseconds } else { $null }
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
    }
    $script:activeEvidence = $active
    if ($null -ne $primaryFailure -or $cleanupFailures.Count -gt 0) {
        $summary = 'captured-pipe descendant-writer case failed'
        if ($null -ne $primaryFailure) { $summary += ': ' + $primaryFailure.Exception.Message }
        if ($cleanupFailures.Count -gt 0) { $summary += '; cleanup: ' + [String]::Join('; ', $cleanupFailures.ToArray()) }
        throw [InvalidOperationException]::new($summary)
    }

    $row = [ordered]@{
        name = $Name
        status = 'PASS'
        operationStatus = 'EXPECTED_FAILURE_NOT_PASS'
        operationMilliseconds = $context.OperationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        facts = $active
    }
    $cases.Add($row)
    Save-Case $caseDirectory 'case.json' $row
    $script:activeContext = $null
}

function Run-CaptureAuthorityGuards {
    $Name = 'capture-authority-guards'
    $caseDirectory = Join-Path $EvidenceRoot $Name
    $null = [IO.Directory]::CreateDirectory($caseDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $caseDirectory
    $jobName = 'Local\TroubleshootJS.Verifier.CapturedPipeContract.' + [guid]::NewGuid().ToString('N')
    $missingExecutable = Join-Path $caseDirectory 'missing-capture-fixture.exe'
    Require (-not [IO.File]::Exists($missingExecutable) -and -not [IO.Directory]::Exists($missingExecutable)) 'missing-executable negative was not absent before the native call'
    $invalidCommandLine = Convert-ToWindowsArgumentString @($missingExecutable)
    $guardFacts = [ordered]@{
        case = $Name
        jobName = $jobName
        captureStreams = 'N/A: no captured lease/readers are returned by the authority and failed-launch negatives'
        queryOnly = $null
        missingExecutable = $null
        disposedCreator = $null
        membersAfterEachGuard = [ordered]@{}
        cleanupProven = $false
    }
    $script:activeEvidence = $guardFacts
    $creator = $null
    $query = $null
    $creatorDisposed = $false
    $queryDisposed = $false
    try {
        $jobType = & $owner { Initialize-VerifierBrowserContainmentJobApi }
        $creator = $jobType::CreateNew($jobName)
        $query = $jobType::OpenExisting($jobName)

        $queryFailure = $null
        try { $null = $query.LaunchCaptured($ChildExecutable, (Convert-ToWindowsArgumentString @($ChildExecutable)), $caseDirectory) }
        catch { $queryFailure = $_ }
        Require ($null -ne $queryFailure) 'query-only capture launch unexpectedly returned a lease'
        $queryBase = $queryFailure.Exception.GetBaseException()
        Require ($queryBase.GetType() -eq [System.InvalidOperationException]) 'query-only capture launch failure type changed'
        Require ($queryBase.Message -ceq 'Only a creator-owned containment job may capture process pipes.') 'query-only capture launch denial changed'
        $queryMembers = @($query.GetMemberProcessIds())
        $creatorMembers = @($creator.GetMemberProcessIds())
        Require ($queryMembers.Count -eq 0 -and $creatorMembers.Count -eq 0) 'query-only capture guard changed job membership'
        $guardFacts.queryOnly = [ordered]@{ deniedBeforeLease = $true; failureType = $queryBase.GetType().FullName; failureMessage = $queryBase.Message; memberIdsAfter = @($queryMembers) }
        $guardFacts.membersAfterEachGuard.queryOnly = @($queryMembers)

        $missingFailure = $null
        try { $null = $creator.LaunchCaptured($missingExecutable, $invalidCommandLine, $caseDirectory) }
        catch { $missingFailure = $_ }
        Require ($null -ne $missingFailure) 'missing executable capture launch unexpectedly succeeded'
        $missingBase = $missingFailure.Exception.GetBaseException()
        Require ($missingBase.GetType() -eq [System.ComponentModel.Win32Exception]) 'missing executable did not fail at the native process creation boundary'
        $queryMembers = @($query.GetMemberProcessIds())
        $creatorMembers = @($creator.GetMemberProcessIds())
        Require ($queryMembers.Count -eq 0 -and $creatorMembers.Count -eq 0) 'failed native launch left a process in the capture job'
        $guardFacts.missingExecutable = [ordered]@{ nativeCreationRejected = $true; failureType = $missingBase.GetType().FullName; nativeErrorCode = $missingBase.NativeErrorCode; memberIdsAfter = @($creatorMembers); leaseOrReadersReturned = $false }
        $guardFacts.membersAfterEachGuard.missingExecutable = @($creatorMembers)

        $creator.Dispose()
        $creatorDisposed = $true
        $closedFailure = $null
        try { $null = $creator.LaunchCaptured($ChildExecutable, (Convert-ToWindowsArgumentString @($ChildExecutable)), $caseDirectory) }
        catch { $closedFailure = $_ }
        Require ($null -ne $closedFailure) 'disposed creator accepted a captured launch'
        $closedBase = $closedFailure.Exception.GetBaseException()
        Require ($closedBase.GetType() -eq [System.ObjectDisposedException]) 'disposed creator guard failure type changed'
        $queryMembers = @($query.GetMemberProcessIds())
        Require ($queryMembers.Count -eq 0) 'disposed creator guard changed capture job membership'
        $guardFacts.disposedCreator = [ordered]@{ rejectedBeforeLease = $true; failureType = $closedBase.GetType().FullName; failureMessage = $closedBase.Message; memberIdsAfter = @($queryMembers) }
        $guardFacts.membersAfterEachGuard.disposedCreator = @($queryMembers)
    } finally {
        if ($null -ne $query) {
            try { $query.Dispose(); $queryDisposed = $true }
            catch { $guardFacts.queryDisposeFailure = $_.Exception.Message }
        }
        if ($null -ne $creator -and -not $creatorDisposed) {
            try { $creator.Dispose(); $creatorDisposed = $true }
            catch { $guardFacts.creatorDisposeFailure = $_.Exception.Message }
        }
        $guardFacts.cleanupProven = ($creatorDisposed -and $queryDisposed)
    }
    Require ([bool]$guardFacts.cleanupProven) 'capture authority guard job handles did not both dispose'
    $row = [ordered]@{ name = $Name; status = 'PASS'; operationStatus = 'EXPECTED_DENIALS_NOT_PASS'; captureStreams = 'N/A'; facts = $guardFacts }
    $cases.Add($row)
    Save-Case $caseDirectory 'case.json' $row
    $script:activeContext = $null
}

function Run-PinnedJavaCapture {
    $Name = 'pinned-jdk-version-capture'
    $javaPath = [IO.Path]::GetFullPath($JavaExecutable)
    Require ([IO.File]::Exists($javaPath)) 'pinned Java executable is missing'
    Require ([IO.Path]::GetFileName($javaPath) -ieq 'java.exe') 'pinned Java executable leaf name changed'
    Require ($ExpectedJavaSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $javaPath) -ceq $ExpectedJavaSha256) 'pinned Java executable SHA changed'
    $javaBytes = [IO.FileInfo]::new($javaPath).Length
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $javaPath
    $caseDirectory = Join-Path $EvidenceRoot $Name
    $null = [IO.Directory]::CreateDirectory($caseDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $caseDirectory
    $jobName = 'Local\TroubleshootJS.Verifier.CapturedPipeContract.' + [guid]::NewGuid().ToString('N')
    $nonce = [guid]::NewGuid().ToString('N')
    $expectedArgv = @($javaPath, '-version')
    $fullCommandLine = Convert-ToWindowsArgumentString $expectedArgv
    $stdoutCap = 1024
    $stderrCap = 4096
    $timeoutMilliseconds = 10000
    $context = [pscustomobject]@{
        Name = $Name
        Job = $null
        Lease = $null
        Capture = $null
        JobName = $jobName
        Nonce = $nonce
        StateDirectory = $caseDirectory
        ChildExecutable = $javaPath
        ChildImageSha256 = $ExpectedJavaSha256
        ExpectedArgv = $expectedArgv
        StdoutCap = $stdoutCap
        StderrCap = $stderrCap
        OperationWatch = $null
        OperationFacts = $null
        CleanupFacts = $null
        StdoutSafeHandle = $null
        StderrSafeHandle = $null
        LeaseDisposeProven = $false
        JobDisposeProven = $false
        StreamFacts = $null
        MemberIdsAtLaunch = @()
        MemberIdentityFacts = $null
        MemberObservers = (New-Object 'System.Collections.Generic.List[object]')
        MemberObserverDisposeProven = $false
        CaptureFacts = $null
        TerminationRequested = $false
        Phase = 'create-job'
    }
    $script:activeContext = $context
    $script:activeEvidence = [ordered]@{ case = $Name; phase = 'create-job'; javaPath = $javaPath; javaSha256 = $ExpectedJavaSha256; jobName = $jobName }
    $primaryFailure = $null
    $cleanupFailures = New-Object 'System.Collections.Generic.List[string]'
    try {
        $jobType = & $owner { Initialize-VerifierBrowserContainmentJobApi }
        $context.Job = $jobType::CreateNew($jobName)
        Require (@($context.Job.GetMemberProcessIds()).Count -eq 0) 'new pinned Java capture job was not empty'
        $context.Phase = 'launch-pinned-java-version-with-captured-pipes'
        $context.OperationWatch = [Diagnostics.Stopwatch]::StartNew()
        $context.Lease = $context.Job.LaunchCaptured($javaPath, $fullCommandLine, $caseDirectory)
        Require ($null -ne $context.Lease -and $context.Lease.GetType().Name -ceq 'OwnedProcessLease') 'pinned Java captured launch did not return a native lease'
        $stdoutReader = $context.Lease.StandardOutput
        $stderrReader = $context.Lease.StandardError
        $stdoutStream = $stdoutReader.BaseStream
        $stderrStream = $stderrReader.BaseStream
        Require ($stdoutReader.GetType() -eq [IO.StreamReader] -and $stderrReader.GetType() -eq [IO.StreamReader]) 'pinned Java readers were not exact StreamReader instances'
        Require ($stdoutStream.GetType() -eq [IO.FileStream] -and $stderrStream.GetType() -eq [IO.FileStream]) 'pinned Java base streams were not exact FileStream instances'
        Require (-not [bool]$stdoutStream.IsAsync -and -not [bool]$stderrStream.IsAsync) 'pinned Java capture exposed an unexpected asynchronous handle stream'
        $context.StdoutSafeHandle = $stdoutStream.SafeFileHandle
        $context.StderrSafeHandle = $stderrStream.SafeFileHandle
        Require (-not $context.StdoutSafeHandle.IsClosed -and -not $context.StderrSafeHandle.IsClosed) 'pinned Java captured handles were closed before receive'
        $context.StreamFacts = [ordered]@{
            stdoutReaderType = $stdoutReader.GetType().FullName
            stderrReaderType = $stderrReader.GetType().FullName
            stdoutBaseStreamType = $stdoutStream.GetType().FullName
            stderrBaseStreamType = $stderrStream.GetType().FullName
            stdoutIsAsync = [bool]$stdoutStream.IsAsync
            stderrIsAsync = [bool]$stderrStream.IsAsync
            stdoutHandleClosedBefore = [bool]$context.StdoutSafeHandle.IsClosed
            stderrHandleClosedBefore = [bool]$context.StderrSafeHandle.IsClosed
        }
        $context.MemberIdentityFacts = Observe-CaptureJobMembers $context
        $context.MemberIdsAtLaunch = @($context.MemberIdentityFacts.memberIds)
        $context.Capture = & $owner {
            param($Lease, $OutLimit, $ErrLimit)
            New-VerifierBoundedCaptureState $Lease $OutLimit $ErrLimit
        } $context.Lease $stdoutCap $stderrCap
        & $owner {
            param($Lease, $State, $Watch, $Timeout)
            Receive-VerifierBoundedCapture $Lease $State $Watch $Timeout ([Threading.CancellationToken]::None)
        } $context.Lease $context.Capture $context.OperationWatch $timeoutMilliseconds
        Require ([bool]$context.Lease.WaitForExit(0)) 'pinned Java original handle did not signal after captured EOF'
        $exitCode = [long]$context.Lease.GetExitCode()
        Require ($exitCode -eq 0) 'pinned Java -version exit code was not zero'
        Require ($context.Capture.Lanes[0].Eof -and $context.Capture.Lanes[1].Eof) 'pinned Java capture did not observe both EOFs'
        Require ($context.Capture.Lanes[0].Bytes.Length -eq 0) 'pinned Java -version unexpectedly wrote stdout bytes'
        $stderrText = [Text.Encoding]::UTF8.GetString($context.Capture.Lanes[1].Bytes.ToArray())
        $expectedVersionText = 'openjdk version "1.8.0_502"' + "`r`n" +
            'OpenJDK Runtime Environment (Temurin)(build 1.8.0_502-b07)' + "`r`n" +
            'OpenJDK 64-Bit Server VM (Temurin)(build 25.502-b07, mixed mode)' + "`r`n"
        Require ($stderrText -ceq $expectedVersionText) 'pinned Java stderr did not match the selected Temurin 8u502-b07 version reference'
        Require ($context.Capture.Lanes[0].Bytes.Length -le $stdoutCap -and $context.Capture.Lanes[1].Bytes.Length -le $stderrCap) 'pinned Java capture exceeded its explicit byte caps'
        Require ($context.Capture.Lanes[0].Discarded -eq 0 -and $context.Capture.Lanes[1].Discarded -eq 0) 'pinned Java successful capture discarded bytes'
        $context.OperationFacts = [ordered]@{
            operationName = 'JAVA_VERSION_CAPTURE'
            operationStatus = 'PASS'
            operationMilliseconds = $context.OperationWatch.Elapsed.TotalMilliseconds
            exitObserved = [bool]$context.Lease.WaitForExit(0)
            exitCode = $exitCode
            stdoutBytes = $context.Capture.Lanes[0].Bytes.Length
            stderrBytes = $context.Capture.Lanes[1].Bytes.Length
            stderrVersionText = $stderrText
        }
        $context.OperationWatch.Stop()
    } catch {
        $primaryFailure = $_
        if ($null -ne $context.OperationWatch) { $context.OperationWatch.Stop() }
    }

    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    if ($null -ne $context.Job) {
        try {
            $members = @($context.Job.GetMemberProcessIds())
            $rootExited = $true
            if ($null -ne $context.Lease) { $rootExited = $context.Lease.WaitForExit(0) }
            if ($members.Count -gt 0 -or -not $rootExited) {
                $context.Job.TerminateOwned([uint32]2)
                $context.TerminationRequested = $true
            }
            $context.CleanupFacts = Wait-RootAndJobEmpty $context 5000
        } catch { [void]$cleanupFailures.Add('pinned Java job closure failed: ' + $_.Exception.Message) }
    }
    if ($null -ne $context.Lease) {
        $closureProven = ($null -ne $context.CleanupFacts -and $context.CleanupFacts.rootExited -and @($context.CleanupFacts.memberIds).Count -eq 0 -and $context.CleanupFacts.retainedMemberObserversExited)
        if ($closureProven) {
            try {
                Close-CaptureReaders $context
                $context.CaptureFacts = Get-CaptureFacts $context
                Require (Test-StreamHandlesClosed $context) 'pinned Java capture handles did not close after drain'
                Require ([bool]$context.CaptureFacts.ReaderTasksTerminal -and [bool]$context.CaptureFacts.ReadersClosed) 'pinned Java capture readers did not settle'
                Require ($context.Capture.Lanes[0].Eof -and $context.Capture.Lanes[1].Eof) 'pinned Java drain did not retain both EOFs'
                $context.StreamFacts.stdoutHandleClosedAfterCapture = [bool]$context.StdoutSafeHandle.IsClosed
                $context.StreamFacts.stderrHandleClosedAfterCapture = [bool]$context.StderrSafeHandle.IsClosed
            } catch { [void]$cleanupFailures.Add('pinned Java reader close failed: ' + $_.Exception.Message) }
        } else { [void]$cleanupFailures.Add('pinned Java readers were not closed without original-process/job closure proof') }
        if ($closureProven -and (Test-StreamHandlesClosed $context)) {
            try {
                $context.Lease.Dispose()
                $context.LeaseDisposeProven = [bool]$context.Lease.IsClosed
                Require $context.LeaseDisposeProven 'pinned Java native lease did not dispose after reader closure'
            } catch { [void]$cleanupFailures.Add('pinned Java lease disposal failed: ' + $_.Exception.Message) }
        } else { [void]$cleanupFailures.Add('pinned Java lease disposal was withheld without closure proof') }
    }
    if ($context.MemberObservers.Count -gt 0 -and $null -ne $context.CleanupFacts -and $context.CleanupFacts.retainedMemberObserversExited) {
        $disposed = $true
        foreach ($observer in $context.MemberObservers) {
            try { $observer.Process.Dispose() }
            catch { $disposed = $false; [void]$cleanupFailures.Add('pinned Java member observer disposal failed: ' + $_.Exception.Message) }
        }
        $context.MemberObserverDisposeProven = $disposed
    } elseif ($context.MemberObservers.Count -gt 0) {
        [void]$cleanupFailures.Add('pinned Java member observers were not disposed without signal proof')
    } else {
        $context.MemberObserverDisposeProven = ($null -ne $context.CleanupFacts -and $context.CleanupFacts.retainedMemberObserversExited)
    }
    if ($null -ne $context.Job) {
        try {
            Require (@($context.Job.GetMemberProcessIds()).Count -eq 0) 'pinned Java job retained members at disposal'
            $context.Job.Dispose()
            $context.JobDisposeProven = $true
        } catch { [void]$cleanupFailures.Add('pinned Java job disposal failed: ' + $_.Exception.Message) }
    }
    $cleanupWatch.Stop()
    if ($null -ne $context.Capture -and $null -eq $context.CaptureFacts) {
        try { $context.CaptureFacts = Get-CaptureFacts $context }
        catch { [void]$cleanupFailures.Add('pinned Java capture facts could not be retained: ' + $_.Exception.Message) }
    }
    if ($null -ne $context.CaptureFacts) {
        $context.CaptureFacts.OperationMilliseconds = if ($null -ne $context.OperationWatch) { $context.OperationWatch.Elapsed.TotalMilliseconds } else { 0.0 }
        $context.CaptureFacts.CleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        $context.CaptureFacts.CleanupProven = ($null -eq $primaryFailure -and $cleanupFailures.Count -eq 0 -and $null -ne $context.CleanupFacts -and $context.CleanupFacts.rootExited -and @($context.CleanupFacts.memberIds).Count -eq 0 -and $context.CleanupFacts.retainedMemberObserversExited -and $context.MemberObserverDisposeProven -and $context.LeaseDisposeProven -and $context.JobDisposeProven -and $context.CaptureFacts.ReaderTasksTerminal -and $context.CaptureFacts.ReadersClosed)
    }
    $active = [ordered]@{
        case = $Name
        javaPath = $javaPath
        javaSha256 = $ExpectedJavaSha256
        javaBytes = $javaBytes
        argv = $expectedArgv
        jobName = $jobName
        processId = if ($null -ne $context.Lease) { $context.Lease.ProcessId } else { $null }
        creationFileTime = if ($null -ne $context.Lease) { $context.Lease.CreationFileTime } else { $null }
        memberIdsAtLaunch = @($context.MemberIdsAtLaunch)
        memberIdentityFacts = $context.MemberIdentityFacts
        stdoutLimit = $stdoutCap
        stderrLimit = $stderrCap
        operationFacts = $context.OperationFacts
        captureFacts = $context.CaptureFacts
        streamFacts = $context.StreamFacts
        cleanupFacts = $context.CleanupFacts
        nativeToolClosure = 'NOT_ATTESTED'
        leaseDisposed = $context.LeaseDisposeProven
        memberObserversDisposed = $context.MemberObserverDisposeProven
        jobDisposed = $context.JobDisposeProven
        terminationRequested = $context.TerminationRequested
        phase = $context.Phase
        primaryFailure = if ($null -ne $primaryFailure) { $primaryFailure.Exception.Message } else { $null }
        cleanupFailures = @($cleanupFailures.ToArray())
        operationMilliseconds = if ($null -ne $context.OperationWatch) { $context.OperationWatch.Elapsed.TotalMilliseconds } else { $null }
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
    }
    $script:activeEvidence = $active
    if ($null -ne $primaryFailure -or $cleanupFailures.Count -gt 0) {
        $summary = 'captured-pipe pinned Java case failed'
        if ($null -ne $primaryFailure) { $summary += ': ' + $primaryFailure.Exception.Message }
        if ($cleanupFailures.Count -gt 0) { $summary += '; cleanup: ' + [String]::Join('; ', $cleanupFailures.ToArray()) }
        throw [InvalidOperationException]::new($summary)
    }
    $row = [ordered]@{ name = $Name; status = 'PASS'; operationStatus = 'PASS'; operationMilliseconds = $context.OperationWatch.Elapsed.TotalMilliseconds; cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds; facts = $active }
    $cases.Add($row)
    Save-Case $caseDirectory 'case.json' $row
    $script:activeContext = $null
}

try {
    Require ($PSVersionTable.PSVersion.Major -eq 5 -and $PSVersionTable.PSVersion.Minor -eq 1) 'Windows PowerShell 5.1 required'
    Require (Test-Path -LiteralPath $ModulePath -PathType Leaf) 'module file missing'
    Require ($ExpectedModuleBytes -gt 0 -and $ExpectedModuleSha256 -cmatch '^[0-9a-f]{64}$' -and ([IO.FileInfo]::new($ModulePath).Length -eq $ExpectedModuleBytes) -and (Sha $ModulePath) -ceq $ExpectedModuleSha256) 'module pin'
    Require (Test-Path -LiteralPath $FixtureSourcePath -PathType Leaf) 'fixture source missing'
    Require ($ExpectedFixtureSourceSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $FixtureSourcePath) -ceq $ExpectedFixtureSourceSha256) 'fixture source pin'
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

    $javaPath = ''
    $javaBytes = $null
    if (-not [String]::IsNullOrWhiteSpace($JavaExecutable)) {
        $javaPath = [IO.Path]::GetFullPath($JavaExecutable)
        Require ([IO.File]::Exists($javaPath) -and [IO.Path]::GetFileName($javaPath) -ieq 'java.exe') 'pinned Java executable path is missing or changed'
        Require ($ExpectedJavaSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $javaPath) -ceq $ExpectedJavaSha256) 'pinned Java executable SHA changed'
        & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $javaPath
        $javaBytes = [IO.FileInfo]::new($javaPath).Length
    }
    if ($Mode -ceq 'Suite' -and [String]::IsNullOrWhiteSpace($JavaExecutable)) {
        $JavaExecutable = ''
    } elseif (-not [String]::IsNullOrWhiteSpace($JavaExecutable)) {
        $JavaExecutable = $javaPath
    }

    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $FixtureSourcePath
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $EvidenceRoot
    $insideTemp = & $owner { param($Base, $Path) Test-VerifierPhysicalChildPath $Base $Path } $tempRoot $EvidenceRoot
    Require ([bool]$insideTemp) 'evidence root must be a physical temp child'
    Require (-not [IO.Directory]::Exists($EvidenceRoot) -and -not [IO.File]::Exists($EvidenceRoot)) 'fresh evidence root required'
    $null = [IO.Directory]::CreateDirectory($EvidenceRoot)
    $rootReady = $true
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $EvidenceRoot

    $compileDirectory = Join-Path $EvidenceRoot 'compiled-fixture'
    $null = [IO.Directory]::CreateDirectory($compileDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $compileDirectory
    $ChildExecutable = [IO.Path]::GetFullPath((Join-Path $compileDirectory 'VerifierContainmentFixture.exe'))
    Require (-not [IO.File]::Exists($ChildExecutable) -and -not [IO.Directory]::Exists($ChildExecutable)) 'fresh fixture output required'
    $null = Add-Type -Path $FixtureSourcePath -OutputAssembly $ChildExecutable -OutputType ConsoleApplication `
        -ReferencedAssemblies @('System.dll', 'System.Core.dll', 'System.Web.Extensions.dll') -ErrorAction Stop
    Require ([IO.File]::Exists($ChildExecutable)) 'compiled fixture artifact missing'
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $ChildExecutable
    $ChildImageSha256 = Sha $ChildExecutable
    $ChildImageBytes = [IO.FileInfo]::new($ChildExecutable).Length
    $jobType = & $owner { Initialize-VerifierBrowserContainmentJobApi }
    Require ($jobType -is [type] -and $jobType.Name -ceq 'VerifierBrowserContainmentJobApi') 'native containment job type unavailable'

    Save 'inputs.json' @{
        hashes = [ordered]@{ module = (Sha $ModulePath); fixtureSource = (Sha $FixtureSourcePath); childArtifact = $ChildImageSha256; compilerHost = $compilerHostSha256 }
        bytes = [ordered]@{ module = $ExpectedModuleBytes; fixtureSource = ([IO.FileInfo]::new($FixtureSourcePath).Length); childArtifact = $ChildImageBytes; compilerHost = $compilerHostBytes }
        pinnedJava = if ($javaPath.Length -gt 0) { [ordered]@{ path = $javaPath; sha256 = $ExpectedJavaSha256; bytes = $javaBytes } } else { 'NOT_PROVIDED' }
        compilerHostPath = $compilerHostPath
        powershellVersion = $PSVersionTable.PSVersion.ToString()
        compiler = 'Windows PowerShell Add-Type'
        mode = $Mode
        scope = if ($Mode -ceq 'Suite') { 'NATIVE_CAPTURED_PIPE_CONTRACT_SUITE' } else { 'NATIVE_CAPTURED_PIPE_DUPLEX_SMOKE_ONLY' }
        captureLimits = if ($Mode -ceq 'Suite') { @{ smoke = @{ stdout = 65536; stderr = 65536 }; suiteExplicit = @{ stdout = 4096; stderr = 4096 }; javaVersion = @{ stdout = 1024; stderr = 4096 } } } else { @{ stdout = 65536; stderr = 65536 } }
        nativeToolClosure = 'NOT_ATTESTED'
        productionCoordinatorWiring = 'NOT_TESTED'
    }

    Run-DuplexSmoke
    if ($Mode -ceq 'Suite') {
        Run-FixtureCaptureCase -Name 'below-cap-exit0' -FixtureMode 'emit' -StdoutBytes 4095 -StderrBytes 4095 -ExitCode 0 -StdoutCap 4096 -StderrCap 4096 -TimeoutMilliseconds 10000 -ExpectedOutcome 'EXIT_ZERO'
        Run-FixtureCaptureCase -Name 'cap-boundary-exit7' -FixtureMode 'emit' -StdoutBytes 4095 -StderrBytes 4096 -ExitCode 7 -StdoutCap 4096 -StderrCap 4096 -TimeoutMilliseconds 10000 -ExpectedOutcome 'EXIT_SEVEN'
        Run-FixtureCaptureCase -Name 'stdout-first-excess' -FixtureMode 'hold' -StdoutBytes 65536 -StderrBytes 0 -ExitCode 0 -StdoutCap 4096 -StderrCap 4096 -TimeoutMilliseconds 10000 -ExpectedOutcome 'OUTPUT_LIMIT' -FailureLane 'stdout'
        Run-FixtureCaptureCase -Name 'stderr-first-excess' -FixtureMode 'hold' -StdoutBytes 0 -StderrBytes 65536 -ExitCode 0 -StdoutCap 4096 -StderrCap 4096 -TimeoutMilliseconds 10000 -ExpectedOutcome 'OUTPUT_LIMIT' -FailureLane 'stderr'
        Run-FixtureCaptureCase -Name 'hold-timeout' -FixtureMode 'hold' -StdoutBytes 32 -StderrBytes 32 -ExitCode 0 -StdoutCap 4096 -StderrCap 4096 -TimeoutMilliseconds 700 -ExpectedOutcome 'TIMEOUT'
        Run-FixtureCaptureCase -Name 'hold-cancel' -FixtureMode 'hold' -StdoutBytes 32 -StderrBytes 32 -ExitCode 0 -StdoutCap 4096 -StderrCap 4096 -TimeoutMilliseconds 5000 -ExpectedOutcome 'CANCELLED' -CancelAfterMilliseconds 300
        Run-DescendantWriterCase
        Run-CaptureAuthorityGuards
        if (-not [String]::IsNullOrWhiteSpace($JavaExecutable)) {
            Run-PinnedJavaCapture
        }
    }
    $expectedCaseCount = if ($Mode -ceq 'Smoke') { 1 } elseif ([String]::IsNullOrWhiteSpace($JavaExecutable)) { 9 } else { 10 }
    Require ($cases.Count -eq $expectedCaseCount) 'complete captured-pipe case count'
    Require ((Sha $ModulePath) -ceq $ExpectedModuleSha256 -and (Sha $FixtureSourcePath) -ceq $ExpectedFixtureSourceSha256 -and (Sha $ChildExecutable) -ceq $ChildImageSha256 -and (Sha $compilerHostPath) -ceq $compilerHostSha256) 'final module, source, child image, and compiler host pins'
    if ($javaPath.Length -gt 0) { Require ((Sha $javaPath) -ceq $ExpectedJavaSha256 -and ([IO.FileInfo]::new($javaPath).Length -eq $javaBytes)) 'final pinned Java executable SHA or size changed' }
    $suiteResult = if ($Mode -ceq 'Suite') { 'PASS' } else { 'NOT_RUN' }
    $descendantResult = if ($Mode -ceq 'Suite') { 'PASS' } else { 'NOT_TESTED' }
    $cancellationResult = if ($Mode -ceq 'Suite') { 'PASS' } else { 'NOT_TESTED' }
    $javaResult = if ($Mode -ceq 'Suite' -and $javaPath.Length -gt 0) { 'PASS' } elseif ($Mode -ceq 'Suite') { 'NOT_PROVIDED' } else { 'NOT_RUN' }
    $finalScope = if ($Mode -ceq 'Suite') { 'NATIVE_CAPTURED_PIPE_CONTRACT_SUITE' } else { 'NATIVE_CAPTURED_PIPE_DUPLEX_SMOKE_ONLY' }
    Save 'cases.json' @{ status = 'PASS'; mode = $Mode; caseCount = $cases.Count; cases = @($cases.ToArray()); scope = $finalScope; suite = $suiteResult; descendantWriter = $descendantResult; cancellation = $cancellationResult; pinnedJavaVersionCapture = $javaResult; nativeToolClosure = 'NOT_ATTESTED'; productionCoordinatorWiring = 'NOT_TESTED' }
    Write-Output ('Verifier captured-pipe contract mode=' + $Mode + ' cases=' + $cases.Count + ' status=PASS')
    exit 0
} catch {
    if ($rootReady) {
        try {
            Save 'failure.json' @{
                status = 'FAIL_NOT_PASS'
                type = $_.Exception.GetType().FullName
                message = $_.Exception.Message
                scriptStackTrace = $_.ScriptStackTrace
                position = $_.InvocationInfo.PositionMessage
                active = $script:activeEvidence
                completedCases = @($cases.ToArray())
            }
        } catch { }
    }
    [Console]::Error.WriteLine('Verifier captured-pipe contract FAIL: ' + $_.Exception.GetType().FullName)
    exit 2
}

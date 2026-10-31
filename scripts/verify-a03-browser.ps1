[CmdletBinding()]
param(
    [ValidateSet('Current', 'A08', 'A07', 'A06', 'A05', 'A03', 'A04', 'Task41', 'Task46', 'Task47', 'Task48', 'Task49', 'A02', 'Q100', 'Q100Faults', 'Q100Player', 'E06State')]
    [string]$Gate = 'Current',
    [switch]$Smoke,
    [switch]$ForceTcpListener,
    [switch]$Headed,
    [AllowEmptyString()]
    [string]$BrowserPath = '',
    [AllowEmptyString()]
    [string]$WorktreeRoot = '',
    [AllowEmptyString()]
    [string]$OutputPath = '',
    [int]$TimeoutSeconds = 90,
    [switch]$TraceRouteTiming
)

# This is a small developer-report reader. It deliberately uses the same
# run/preview/browser ownership module as the larger browser verifier, while
# keeping the browser side limited to Page.navigate and Runtime.evaluate.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$context = $null
$session = $null
$cleanupResult = $null
$previewIdentity = $null
$reports = $null
$definition = $null
$requestedExitCode = 2
$routeResults = New-Object Collections.Generic.List[object]
$script:Phase = 'setup'
$script:Operation = 'initialization'
$script:OperationStartedUtc = [DateTime]::UtcNow
$script:OperationDeadline = [DateTime]::MinValue
$script:FailureDiagnostic = $null
$script:CleanupDiagnostic = $null
$script:NextCdpId = 1
$script:CdpEvents = New-Object Collections.Generic.List[string]
$script:RouteTimingEnabled = [bool]$TraceRouteTiming
$script:RouteTimingRecordLimit = 64
$script:RouteTimingRecords = New-Object Collections.Generic.List[object]
$script:RouteTimingRecordCount = 0
$script:RouteTimingTruncatedCount = 0
$script:RouteTimingOrdinal = 0
$script:CurrentRouteTiming = $null
$script:CurrentRouteTimingClock = $null
$script:RouteTimingRecorderFailureCount = 0
$script:IsQ100Gate = ($Gate -ieq 'Q100' -or $Gate -ieq 'Q100Faults' -or $Gate -ieq 'Q100Player' -or $Gate -ieq 'E06State') -and -not $Smoke
$script:Q100MatrixStartedUtc = if ($script:IsQ100Gate) { [DateTime]::UtcNow } else { [DateTime]::MinValue }
$script:Q100MatrixDeadlineUtc = if ($script:IsQ100Gate) { $script:Q100MatrixStartedUtc.AddSeconds(6000) } else { [DateTime]::MinValue }
$script:Q100MatrixOperationElapsedMs = $null
$script:Q100CleanupElapsedMs = $null
$script:Q100PlayerArtifactProof = $null

function Get-SafeText([object]$Value, [int]$Limit = 20000) {
    if ($null -eq $Value) { return '' }
    $text = [string]$Value
    $text = [Text.RegularExpressions.Regex]::Replace($text,
        '[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]', ' ')
    if ($text.Length -gt $Limit) {
        return $text.Substring(0, $Limit) + '...[truncated]'
    }
    return $text
}

function Get-EarlyErrorText($ErrorRecord) {
    if ($null -eq $ErrorRecord) { return '' }
    if ($ErrorRecord.PSObject.Properties['Exception'] -and
            $null -ne $ErrorRecord.Exception) {
        return [string]$ErrorRecord.Exception.Message
    }
    return [string]$ErrorRecord
}

function Get-DiagnosticText([object]$Value, [int]$Limit = 4000) {
    $text = Get-SafeText $Value $Limit
    # Diagnostics may mention an owned temp root. Keep those paths out of the
    # report while leaving canonical compiled report payloads untouched.
    return [Text.RegularExpressions.Regex]::Replace($text,
        '(?i)(?<![A-Z0-9])[A-Z]:[\\/][^;\r\n ]+', '<owned-path>')
}

function Get-SafeDigest([string]$Path) {
    try {
        if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
        return [string](Get-VerifierFileSha256 $Path)
    } catch { return '' }
}

function Get-ExactReportText([object]$Value) {
    if ($null -eq $Value) { return '' }
    if ($Value -isnot [string]) {
        Throw-VerifierInfrastructure 'A compiled DOM report was not an exact string.'
    }
    # Keep the report byte-for-byte as published by the compiled verifier.
    # Eight MiB is a bounded fail-closed ceiling, well above the current
    # Task48/Task49 receipts, and avoids silently changing evidence.
    if ($Value.Length -gt 8388608) {
        Throw-VerifierInfrastructure 'A compiled DOM report exceeded the bounded 8 MiB evidence limit.'
    }
    return [string]$Value
}

function Get-PreviewIdentity([string]$BaseUrl, $Context, [string]$Root,
        [string]$PreviewPath) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri ($BaseUrl + '/__tsj/verify-identity') `
            -TimeoutSec 5
        if ($response.StatusCode -ne 200) {
            Throw-VerifierInfrastructure 'Owned preview identity endpoint did not return HTTP 200.'
        }
        $identity = $response.Content | ConvertFrom-Json -ErrorAction Stop
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Owned preview identity read failed: ' +
            (Get-VerifierErrorMessage $_))
    }
    foreach ($name in @('protocol','repositoryIdentity','previewScriptIdentity','webRootIdentity',
            'sourceRootIdentity','scriptRootIdentity','sourceDigest','scriptDigest','webDigest',
            'executionDigest','verifierRunId','verifierNonce')) {
        if ($null -eq $identity.PSObject.Properties[$name] -or
                $identity.$name -isnot [string] -or
                [String]::IsNullOrWhiteSpace([string]$identity.$name)) {
            Throw-VerifierInfrastructure "Owned preview identity omitted exact string '$name'."
        }
    }
    foreach ($digest in @('repositoryIdentity','previewScriptIdentity','webRootIdentity',
            'sourceRootIdentity','scriptRootIdentity','sourceDigest','scriptDigest','webDigest','executionDigest')) {
        if ([string]$identity.$digest -notmatch '^[0-9a-f]{64}$') {
            Throw-VerifierInfrastructure "Owned preview identity carried malformed '$digest'."
        }
    }
    foreach ($name in @('executionFileCount','previewPort','processId','processStartTicks')) {
        if ($null -eq $identity.PSObject.Properties[$name] -or
                -not (Test-VerifierStrictIntegralValue $identity.$name 0L ([long]::MaxValue))) {
            Throw-VerifierInfrastructure "Owned preview identity omitted exact integral '$name'."
        }
    }
    $expectedScript = Get-VerifierFullPath $PreviewPath
    $expectedWeb = Get-VerifierFullPath (Join-Path $Root 'war')
    $expectedSource = Get-VerifierFullPath (Join-Path $Root 'src')
    $expectedScripts = Get-VerifierFullPath (Join-Path $Root 'scripts')
    if ([string]$identity.protocol -cne 'troubleshootjs-preview-identity-v2' -or
            [string]$identity.repositoryIdentity -cne (Get-VerifierOpaquePathIdentity 'repository' $Context.WorktreeRoot) -or
            [string]$identity.previewScriptIdentity -cne (Get-VerifierOpaquePathIdentity 'preview-script' $expectedScript) -or
            [string]$identity.webRootIdentity -cne (Get-VerifierOpaquePathIdentity 'web-root' $expectedWeb) -or
            [string]$identity.sourceRootIdentity -cne (Get-VerifierOpaquePathIdentity 'source-root' $expectedSource) -or
            [string]$identity.scriptRootIdentity -cne (Get-VerifierOpaquePathIdentity 'script-root' $expectedScripts) -or
            [string]$identity.verifierRunId -cne [string]$Context.RunId -or
            [string]$identity.verifierNonce -cne [string]$Context.PreviewNonce -or
            [int]$identity.previewPort -ne [int]$Context.Server.Port -or
            [int]$identity.processId -ne [int]$Context.Server.ProcessId -or
            [long]$identity.processStartTicks -ne [long]$Context.Server.ProcessStartTicks) {
        Throw-VerifierInfrastructure 'Owned preview identity did not match the durable verifier context.'
    }
    if ([long]$identity.executionFileCount -le 0 -or
            [int]$identity.previewPort -lt 1 -or [int]$identity.previewPort -gt 65535) {
        Throw-VerifierInfrastructure 'Owned preview identity carried an invalid file count or port.'
    }
    return [ordered]@{
        protocol = [string]$identity.protocol
        verifierRunId = [string]$identity.verifierRunId
        previewPort = [int]$identity.previewPort
        processId = [int]$identity.processId
        processStartTicks = [long]$identity.processStartTicks
        sourceDigest = [string]$identity.sourceDigest
        scriptDigest = [string]$identity.scriptDigest
        webDigest = [string]$identity.webDigest
        executionDigest = [string]$identity.executionDigest
        executionFileCount = [long]$identity.executionFileCount
    }
}

function Throw-AppFailure([string]$Message) {
    $failure = [InvalidOperationException]::new($Message)
    $failure.Data['VerifierExitCode'] = 1
    throw $failure
}

function Set-Operation([string]$Phase, [string]$Operation, [DateTime]$Deadline = [DateTime]::MinValue) {
    $script:Phase = $Phase
    $script:Operation = $Operation
    $script:OperationStartedUtc = [DateTime]::UtcNow
    $script:OperationDeadline = $Deadline
}

function New-FailureDiagnostic([string]$Message, [string]$Kind) {
    $now = [DateTime]::UtcNow
    $elapsed = [long][Math]::Max(0, [Math]::Floor(($now - $script:OperationStartedUtc).TotalMilliseconds))
    $remaining = $null
    if ($script:OperationDeadline -ne [DateTime]::MinValue) {
        $remaining = [long][Math]::Max(0, [Math]::Floor(($script:OperationDeadline - $now).TotalMilliseconds))
    }
    return [ordered]@{
        kind = $Kind
        phase = Get-DiagnosticText $script:Phase 200
        operation = Get-DiagnosticText $script:Operation 400
        elapsedMs = $elapsed
        remainingDeadlineMs = $remaining
        error = Get-DiagnosticText $Message
    }
}

function Send-Cdp($Socket, [int]$Id, [string]$Method, $Parameters) {
    $message = @{ id = $Id; method = $Method; params = $Parameters } |
        ConvertTo-Json -Compress -Depth 12
    $bytes = [Text.Encoding]::UTF8.GetBytes($message)
    $timeout = [Threading.CancellationTokenSource]::new(5000)
    try {
        $Socket.SendAsync(
            (New-Object ArraySegment[byte] -ArgumentList (,$bytes)),
            [Net.WebSockets.WebSocketMessageType]::Text, $true,
            $timeout.Token).GetAwaiter().GetResult()
    } catch {
        Throw-VerifierInfrastructure ("CDP send failed for $Method`: " +
            (Get-VerifierErrorMessage $_))
    } finally {
        $timeout.Dispose()
    }
}

function Receive-Cdp($Socket, [int]$WantedId, [DateTime]$Deadline) {
    while ($true) {
        $stream = [IO.MemoryStream]::new()
        try {
            do {
                $remaining = [int][Math]::Floor(($Deadline - [DateTime]::UtcNow).TotalMilliseconds)
                if ($remaining -le 0) {
                    Throw-VerifierInfrastructure 'CDP receive deadline expired.'
                }
                # A compiled verifier can spend several seconds inside one
                # synchronous GWT turn. Let the existing route deadline,
                # rather than a short transport timeout, bound this receive.
                $receiveTimeout = [Threading.CancellationTokenSource]::new($remaining)
                $buffer = New-Object byte[] 65536
                try {
                    $received = $Socket.ReceiveAsync(
                        (New-Object ArraySegment[byte] -ArgumentList (,$buffer)),
                        $receiveTimeout.Token).GetAwaiter().GetResult()
                } catch {
                    Throw-VerifierInfrastructure ('CDP receive failed: ' +
                        (Get-VerifierErrorMessage $_))
                } finally {
                    $receiveTimeout.Dispose()
                }
                if ($null -eq $received -or $received.Count -lt 0) {
                    Throw-VerifierInfrastructure 'CDP returned an invalid receive result.'
                }
                if ($received.MessageType -eq [Net.WebSockets.WebSocketMessageType]::Close) {
                    Throw-VerifierInfrastructure 'CDP closed the owned browser socket.'
                }
                $stream.Write($buffer, 0, $received.Count)
            } while (-not $received.EndOfMessage)

            try {
                $message = [Text.Encoding]::UTF8.GetString($stream.ToArray()) |
                    ConvertFrom-Json -ErrorAction Stop
            } catch {
                Throw-VerifierInfrastructure ('CDP returned invalid JSON: ' +
                    (Get-VerifierErrorMessage $_))
            }
            if ($message.PSObject.Properties['method']) {
                $method = [string]$message.method
                if ($method -eq 'Runtime.exceptionThrown') {
                    [void]$script:CdpEvents.Add((Get-DiagnosticText ('exception: ' +
                        [string]$message.params.exceptionDetails.text) 4000))
                } elseif ($method -eq 'Runtime.consoleAPICalled') {
                    $parts = @($message.params.args | ForEach-Object {
                        if ($_.PSObject.Properties['value']) { [string]$_.value }
                        elseif ($_.PSObject.Properties['description']) { [string]$_.description }
                        else { [string]$_.type }
                    })
                    [void]$script:CdpEvents.Add((Get-DiagnosticText ('console: ' +
                        ($parts -join ' ')) 4000))
                }
            }
            if ($message.PSObject.Properties['id'] -and
                    [int]$message.id -eq $WantedId) {
                return $message
            }
        } finally {
            $stream.Dispose()
        }
    }
}

function Get-RouteTimingElapsedMilliseconds([long]$StartTicks) {
    $elapsedTicks = [Diagnostics.Stopwatch]::GetTimestamp() - $StartTicks
    return [long][Math]::Max(0, [Math]::Floor(
        ($elapsedTicks * 1000.0) / [Diagnostics.Stopwatch]::Frequency))
}

function Add-RouteTimingEntry($Route, [string]$ListName, $Entry,
        [string]$CountName, [string]$TruncatedName) {
    $list = $Route[$ListName]
    $Route[$CountName] = [int]$Route[$CountName] + 1
    if ($list.Count -ge $script:RouteTimingRecordLimit) {
        $list.RemoveAt(0)
        $Route[$TruncatedName] = [int]$Route[$TruncatedName] + 1
    }
    [void]$list.Add($Entry)
}

function Set-RouteTimingPhase([string]$Phase) {
    if (-not $script:RouteTimingEnabled -or $null -eq $script:CurrentRouteTiming -or
            -not (Test-VerifierStrictStringValue $Phase) -or
            [String]::IsNullOrWhiteSpace($Phase)) { return }
    $route = $script:CurrentRouteTiming
    $now = [Diagnostics.Stopwatch]::GetTimestamp()
    if ([string]$route['phase'] -ceq $Phase) { return }
    if (-not [String]::IsNullOrWhiteSpace([string]$route['phase'])) {
        $phaseElapsed = Get-RouteTimingElapsedMilliseconds ([long]$route['phaseStartTicks'])
        Add-RouteTimingEntry $route 'phases' ([ordered]@{
            phase = [string]$route['phase']
            elapsedMs = $phaseElapsed
            routeElapsedMs = [long]$script:CurrentRouteTimingClock.ElapsedMilliseconds
        }) 'phaseCount' 'phaseTruncatedCount'
    }
    $route['phase'] = $Phase
    $route['phaseStartTicks'] = $now
}

function Start-RouteTiming($Definition, [int]$Ordinal, [DateTime]$Deadline) {
    if (-not $script:RouteTimingEnabled) { return }
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $script:CurrentRouteTimingClock = $clock
    $remainingDeadlineMs = if ($Deadline -eq [DateTime]::MinValue) { $null } else {
        [long][Math]::Max(0, [Math]::Floor(($Deadline - [DateTime]::UtcNow).TotalMilliseconds))
    }
    $route = [ordered]@{
        ordinal = $Ordinal
        name = [string]$Definition.name
        kind = [string]$Definition.kind
        deadlineRemainingAtStartMs = $remainingDeadlineMs
        phase = 'before-navigation-boundary-sample'
        phaseStartTicks = [Diagnostics.Stopwatch]::GetTimestamp()
        phases = New-Object Collections.Generic.List[object]
        phaseCount = 0
        phaseTruncatedCount = 0
        cdpCalls = New-Object Collections.Generic.List[object]
        cdpCallCount = 0
        cdpCallTruncatedCount = 0
        pendingCdpRequest = $null
        lastCdpRequest = $null
        requestAtFailure = $null
        samples = New-Object Collections.Generic.List[object]
        sampleCount = 0
        sampleTruncatedCount = 0
        sampleFailureCount = 0
        sampleSkippedCount = 0
        beforeNavigationSampleRecorded = $false
        afterRouteSampleRecorded = $false
        finalized = $false
    }
    $script:CurrentRouteTiming = $route
}

function Test-RouteTimingFiniteNonnegativeNumber($Value) {
    if ($null -eq $Value) { return $false }
    if ($Value -isnot [byte] -and $Value -isnot [sbyte] -and
            $Value -isnot [int16] -and $Value -isnot [uint16] -and
            $Value -isnot [int32] -and $Value -isnot [uint32] -and
            $Value -isnot [int64] -and $Value -isnot [uint64] -and
            $Value -isnot [single] -and $Value -isnot [double] -and
            $Value -isnot [decimal]) { return $false }
    try {
        $number = [double]$Value
        return (-not [double]::IsNaN($number) -and
            -not [double]::IsInfinity($number) -and $number -ge 0)
    } catch { return $false }
}

function Get-RouteTimingRequestSnapshot($Request) {
    if ($null -eq $Request) { return $null }
    $requestId = 0
    $method = ''
    $state = ''
    $phase = ''
    $ageMs = 0L
    $sendElapsedMs = 0L
    $waitElapsedMs = 0L
    $failureKind = ''
    if ($Request -is [System.Collections.IDictionary]) {
        $requestId = [int]$Request['requestId']
        $method = [string]$Request['method']
        $state = [string]$Request['state']
        $phase = [string]$Request['phase']
        $ageMs = if ($Request.Contains('startedTicks')) {
            Get-RouteTimingElapsedMilliseconds ([long]$Request['startedTicks'])
        } else { [long]$Request['ageMs'] }
        if ($Request.Contains('sendElapsedMs')) { $sendElapsedMs = [long]$Request['sendElapsedMs'] }
        if ($Request.Contains('waitElapsedMs')) { $waitElapsedMs = [long]$Request['waitElapsedMs'] }
        if ($Request.Contains('failureKind')) { $failureKind = [string]$Request['failureKind'] }
    } else {
        $requestId = [int]$Request.requestId
        $method = [string]$Request.method
        $state = [string]$Request.state
        $phase = [string]$Request.phase
        $ageMs = [long]$Request.ageMs
        if ($Request.PSObject.Properties['sendElapsedMs']) { $sendElapsedMs = [long]$Request.sendElapsedMs }
        if ($Request.PSObject.Properties['waitElapsedMs']) { $waitElapsedMs = [long]$Request.waitElapsedMs }
        if ($Request.PSObject.Properties['failureKind']) { $failureKind = [string]$Request.failureKind }
    }
    return [ordered]@{
        requestId = $requestId
        method = $method
        state = $state
        ageMs = $ageMs
        phase = $phase
        sendElapsedMs = $sendElapsedMs
        waitElapsedMs = $waitElapsedMs
        failureKind = $failureKind
    }
}

function Add-RouteHeapSample([string]$Boundary, $Socket, [DateTime]$Deadline) {
    if (-not $script:RouteTimingEnabled -or $null -eq $script:CurrentRouteTiming) { return }
    $route = $script:CurrentRouteTiming
    $sampleClock = [Diagnostics.Stopwatch]::StartNew()
    $sample = [ordered]@{
        boundary = $Boundary
        method = 'Runtime.getHeapUsage'
        status = 'failed'
        elapsedMs = 0L
        failureKind = ''
        metrics = [ordered]@{}
    }
    if ($Boundary -ceq 'before-navigation') { $route['beforeNavigationSampleRecorded'] = $true }
    if ($Boundary -ceq 'after-route') { $route['afterRouteSampleRecorded'] = $true }
    try {
        if ($Deadline -ne [DateTime]::MinValue -and [DateTime]::UtcNow -ge $Deadline) {
            $sample.status = 'skipped'
            $sample.failureKind = 'route-deadline-expired'
            $route['sampleSkippedCount'] = [int]$route['sampleSkippedCount'] + 1
        } else {
            $response = Invoke-Cdp $Socket 'Runtime.getHeapUsage' @{} $Deadline
            if ($null -eq $response -or -not $response.PSObject.Properties['result'] -or
                    $null -eq $response.result) {
                throw 'Runtime heap response omitted its result object.'
            }
            $metrics = [ordered]@{}
            foreach ($metricName in @('usedSize', 'totalSize',
                    'embedderHeapUsedSize', 'backingStorageSize')) {
                $property = $response.result.PSObject.Properties[$metricName]
                if ($null -eq $property) {
                    if ($metricName -in @('usedSize', 'totalSize')) {
                        throw 'Runtime heap response omitted a required size metric.'
                    }
                    continue
                }
                if (-not (Test-RouteTimingFiniteNonnegativeNumber $property.Value)) {
                    throw 'Runtime heap response contained a malformed size metric.'
                }
                $metrics[$metricName] = [double]$property.Value
            }
            $sample.status = 'collected'
            $sample.metrics = $metrics
        }
    } catch {
        $sample.status = 'failed'
        $sample.failureKind = 'runtime-heap-query-or-response-failure'
        $route['sampleFailureCount'] = [int]$route['sampleFailureCount'] + 1
    } finally {
        $sample.elapsedMs = [long]$sampleClock.ElapsedMilliseconds
        Add-RouteTimingEntry $route 'samples' $sample 'sampleCount' 'sampleTruncatedCount'
        $sampleClock.Stop()
    }
}

function Complete-RouteTiming([string]$Outcome, [string]$FailureKind = '') {
    if (-not $script:RouteTimingEnabled -or $null -eq $script:CurrentRouteTiming) { return }
    $route = $script:CurrentRouteTiming
    if ([bool]$route['finalized']) { return }
    if ($Outcome -ceq 'FAILED' -and -not [bool]$route['afterRouteSampleRecorded']) {
        $skippedSample = [ordered]@{
            boundary = 'after-route'
            method = 'Runtime.getHeapUsage'
            status = 'skipped'
            elapsedMs = 0L
            failureKind = 'route-failed-before-boundary'
            metrics = [ordered]@{}
        }
        $route['afterRouteSampleRecorded'] = $true
        $route['sampleSkippedCount'] = [int]$route['sampleSkippedCount'] + 1
        Add-RouteTimingEntry $route 'samples' $skippedSample 'sampleCount' 'sampleTruncatedCount'
    }
    $activePhase = [string]$route['phase']
    if (-not [String]::IsNullOrWhiteSpace($activePhase)) {
        $phaseElapsed = Get-RouteTimingElapsedMilliseconds ([long]$route['phaseStartTicks'])
        Add-RouteTimingEntry $route 'phases' ([ordered]@{
            phase = $activePhase
            elapsedMs = $phaseElapsed
            routeElapsedMs = [long]$script:CurrentRouteTimingClock.ElapsedMilliseconds
        }) 'phaseCount' 'phaseTruncatedCount'
    }
    $pending = $route['pendingCdpRequest']
    if ($Outcome -ceq 'FAILED') {
        $failureRequest = $pending
        if ($null -eq $failureRequest -and $null -ne $route['lastCdpRequest'] -and
                [string]$route['lastCdpRequest']['state'] -ceq 'failed') {
            $failureRequest = $route['lastCdpRequest']
        }
        if ($null -ne $failureRequest) { $route['requestAtFailure'] = Get-RouteTimingRequestSnapshot $failureRequest }
    }
    $traceStatus = if ([int]$route['sampleFailureCount'] -gt 0 -or
            [int]$route['sampleSkippedCount'] -gt 0 -or
            [int]$route['phaseTruncatedCount'] -gt 0 -or
            [int]$route['cdpCallTruncatedCount'] -gt 0 -or
            [int]$route['sampleTruncatedCount'] -gt 0) { 'PARTIAL' } else { 'COMPLETE' }
    $record = [ordered]@{
        ordinal = [int]$route['ordinal']
        name = [string]$route['name']
        kind = [string]$route['kind']
        outcome = $Outcome
        elapsedMs = [long]$script:CurrentRouteTimingClock.ElapsedMilliseconds
        failureKind = $FailureKind
        activePhaseAtEnd = if ($Outcome -ceq 'FAILED') { $activePhase } else { '' }
        collectionStatus = $traceStatus
        phases = $route['phases'].ToArray()
        phaseCount = [int]$route['phaseCount']
        phaseTruncatedCount = [int]$route['phaseTruncatedCount']
        cdpCalls = $route['cdpCalls'].ToArray()
        cdpCallCount = [int]$route['cdpCallCount']
        cdpCallTruncatedCount = [int]$route['cdpCallTruncatedCount']
        lastCdpRequest = $route['lastCdpRequest']
        requestAtFailure = $route['requestAtFailure']
        deadlineRemainingAtStartMs = $route['deadlineRemainingAtStartMs']
        samples = $route['samples'].ToArray()
        sampleCount = [int]$route['sampleCount']
        sampleTruncatedCount = [int]$route['sampleTruncatedCount']
        sampleFailureCount = [int]$route['sampleFailureCount']
        sampleSkippedCount = [int]$route['sampleSkippedCount']
    }
    $script:RouteTimingRecordCount++
    if ($script:RouteTimingRecords.Count -ge $script:RouteTimingRecordLimit) {
        $script:RouteTimingRecords.RemoveAt(0)
        $script:RouteTimingTruncatedCount++
    }
    [void]$script:RouteTimingRecords.Add($record)
    $route['finalized'] = $true
    $script:CurrentRouteTimingClock.Stop()
    $script:CurrentRouteTimingClock = $null
    $script:CurrentRouteTiming = $null
}

function Invoke-Cdp($Socket, [string]$Method, $Parameters, [DateTime]$Deadline) {
    $id = $script:NextCdpId
    $script:NextCdpId++
    if (-not $script:RouteTimingEnabled -or $null -eq $script:CurrentRouteTiming) {
        [void](Send-Cdp $Socket $id $Method $Parameters)
        $response = Receive-Cdp $Socket $id $Deadline
        if ($response.PSObject.Properties['error']) {
            Throw-VerifierInfrastructure ("CDP protocol error for $Method`: " +
                ($response.error | ConvertTo-Json -Compress -Depth 8))
        }
        return $response
    }
    $route = $script:CurrentRouteTiming
    $isNavigation = $Method -ceq 'Page.navigate'
    if ($isNavigation) { Set-RouteTimingPhase 'navigation-send' }
    $request = [ordered]@{
        requestId = [int]$id
        method = $Method
        phase = if ($isNavigation) { 'navigation' } else { [string]$route['phase'] }
        sendPhase = if ($isNavigation) { 'navigation-send' } else { [string]$route['phase'] }
        waitPhase = if ($isNavigation) { 'navigation-readiness' } else { [string]$route['phase'] }
        state = 'pending'
        startedTicks = [Diagnostics.Stopwatch]::GetTimestamp()
        routeStartedMs = [long]$script:CurrentRouteTimingClock.ElapsedMilliseconds
        sendElapsedMs = 0L
        waitElapsedMs = 0L
        ageMs = 0L
        failureKind = ''
    }
    $route['pendingCdpRequest'] = $request
    $response = $null
    try {
        $sendClock = [Diagnostics.Stopwatch]::StartNew()
        try {
            [void](Send-Cdp $Socket $id $Method $Parameters)
        } finally {
            try {
                $request.sendElapsedMs = [long]$sendClock.ElapsedMilliseconds
            } catch {
                try { $script:RouteTimingRecorderFailureCount++ } catch { }
            } finally {
                try { $sendClock.Stop() } catch { }
            }
        }
        if ($isNavigation) { Set-RouteTimingPhase 'navigation-readiness' }
        $waitClock = [Diagnostics.Stopwatch]::StartNew()
        try {
            $response = Receive-Cdp $Socket $id $Deadline
        } finally {
            try {
                $request.waitElapsedMs = [long]$waitClock.ElapsedMilliseconds
            } catch {
                try { $script:RouteTimingRecorderFailureCount++ } catch { }
            } finally {
                try { $waitClock.Stop() } catch { }
            }
        }
        if ($response.PSObject.Properties['error']) {
            $request.state = 'failed'
            $request.failureKind = 'cdp-response-error'
            Throw-VerifierInfrastructure ("CDP protocol error for $Method`: " +
                ($response.error | ConvertTo-Json -Compress -Depth 8))
        }
        $request.state = 'completed'
        return $response
    } catch {
        if ($request.state -ceq 'pending') {
            $request.state = 'failed'
            $request.failureKind = 'cdp-send-or-wait-failed'
        }
        throw
    } finally {
        try {
            $request.ageMs = Get-RouteTimingElapsedMilliseconds ([long]$request.startedTicks)
            $safeRequest = [ordered]@{
                requestId = [int]$request.requestId
                method = [string]$request.method
                phase = [string]$request.phase
                sendPhase = [string]$request.sendPhase
                waitPhase = [string]$request.waitPhase
                state = [string]$request.state
                routeStartedMs = [long]$request.routeStartedMs
                ageMs = [long]$request.ageMs
                sendElapsedMs = [long]$request.sendElapsedMs
                waitElapsedMs = [long]$request.waitElapsedMs
                failureKind = [string]$request.failureKind
            }
            $route['lastCdpRequest'] = $safeRequest
            $route['pendingCdpRequest'] = $null
            Add-RouteTimingEntry $route 'cdpCalls' $safeRequest 'cdpCallCount' 'cdpCallTruncatedCount'
        } catch {
            try { $script:RouteTimingRecorderFailureCount++ } catch { }
            try { $route['lastCdpRequest'] = Get-RouteTimingRequestSnapshot $request } catch { }
            try { $route['pendingCdpRequest'] = $null } catch { }
        }
    }
}

function Evaluate-Cdp($Socket, [string]$Expression, [DateTime]$Deadline) {
    $response = Invoke-Cdp $Socket 'Runtime.evaluate' @{
        expression = $Expression; returnByValue = $true
    } $Deadline
    if (-not $response.PSObject.Properties['result'] -or
            -not $response.result.PSObject.Properties['result']) {
        Throw-VerifierInfrastructure 'Runtime.evaluate returned a malformed result.'
    }
    if ($response.result.PSObject.Properties['exceptionDetails']) {
        Throw-VerifierInfrastructure ('Runtime.evaluate page exception: ' +
            [string]$response.result.exceptionDetails.text)
    }
    if ($response.result.result.PSObject.Properties['value']) {
        return $response.result.result.value
    }
    return $null
}

function Wait-Cdp($Socket, [string]$Expression, [DateTime]$Deadline,
        [string]$Description, [int]$PollMilliseconds = 200) {
    do {
        if (Evaluate-Cdp $Socket $Expression $Deadline) { return }
        Start-Sleep -Milliseconds $PollMilliseconds
    } while ([DateTime]::UtcNow -lt $Deadline)
    Throw-VerifierInfrastructure ("Timed out waiting for $Description.")
}

function Get-RouteReports($Socket, [DateTime]$Deadline) {
    $expression = @'
(()=>{const d=document.documentElement;const get=n=>d.getAttribute(n)||'';const developerAttributes={};
  const publicPreviewAttributes=new Set(['data-tsj-preview-identity-protocol','data-tsj-preview-source-digest',
    'data-tsj-preview-script-digest','data-tsj-preview-web-digest','data-tsj-preview-execution-digest',
    'data-tsj-preview-file-count']);
  const visible=e=>{const r=e.getBoundingClientRect(),s=window.getComputedStyle(e);return r.width>0&&r.height>0&&e.getClientRects().length>0&&s.display!=='none'&&s.visibility!=='hidden'&&e.getAttribute('aria-hidden')!=='true';};
  const enabled=e=>!e.disabled&&e.getAttribute('aria-disabled')!=='true';
  const accessibleName=e=>(e.getAttribute('aria-label')||e.innerText||e.textContent||'').trim().replace(/\s+/g,' ');
  for(let i=0;i<d.attributes.length;i++){
    const n=d.attributes[i].name.toLowerCase();
    if(n.startsWith('data-tsj-')&&!publicPreviewAttributes.has(n))
      developerAttributes[n]=true;
  }
  return {
  url:location.href,ready:document.readyState,
  verification:get('data-tsj-verification'),
  a08:get('data-tsj-a08-report'),a07:get('data-tsj-a07-report'),a06:get('data-tsj-a06-report'),a04:get('data-tsj-a04-report'),a03:get('data-tsj-a03-report'),task41:get('data-tsj-task41-evidence'),
  task46Parity:get('data-tsj-task46-parity'),
  task46Descriptor:get('data-tsj-task46-descriptor'),
  task46Snapshot:get('data-tsj-task46-snapshot'),
  task46Replay:get('data-tsj-task46-replay'),
  task47:get('data-tsj-task47-report'),task48:get('data-tsj-task48-report'),
  task49:get('data-tsj-task49-report'),a02:get('data-tsj-a02-report'),
  q100:get('data-tsj-q100-report'),q100Present:d.hasAttribute('data-tsj-q100-report'),
  e06Acceptance:get('data-tsj-e06-acceptance-canary'),
  e06AcceptancePresent:d.hasAttribute('data-tsj-e06-acceptance-canary'),
  verificationPresent:d.hasAttribute('data-tsj-verification'),
  developerAttributes,
  normalReady:(()=>{
    const nav=[...document.querySelectorAll('nav[aria-label="Workbench navigation"]')].filter(visible);
    const retest=[...document.querySelectorAll('button[aria-label="Run customer retest"]')].filter(x=>visible(x)&&enabled(x));
    const power=[...document.querySelectorAll('button.tsj-power-button')].filter(visible);
    const powerLabel=power.length===1?accessibleName(power[0]):'';
    const boardPowerReady=power.length===1&&/^Board Power: (?:ON|OFF)$/.test(powerLabel);
    return {workbenchNavigationReady:nav.length===1,programReady:nav.length===1&&boardPowerReady,
      retestCustomerReady:retest.length===1,boardPowerReady,boardPowerLabel:powerLabel};})()
};})()
'@
    return Evaluate-Cdp $Socket $expression $Deadline
}

function Test-RouteRequiresTicketEntry([string]$Kind) {
    return $Kind -ceq 'q100-player' -or $Kind -ceq 'q100-debugfalse' -or
        $Kind -match '-debugoff$'
}

function Get-OrdinaryTicketEntryTarget($Socket, [DateTime]$Deadline) {
    $expression = @'
(()=>{const name=e=>(e.getAttribute('aria-label')||e.innerText||e.textContent||'').trim().replace(/\s+/g,' ');
  const visible=e=>{const r=e.getBoundingClientRect(),s=window.getComputedStyle(e);return r.width>0&&r.height>0&&e.getClientRects().length>0&&s.display!=='none'&&s.visibility!=='hidden'&&e.getAttribute('aria-hidden')!=='true';};
  const enabled=e=>!e.disabled&&e.getAttribute('aria-disabled')!=='true';
  const matches=[...document.querySelectorAll('button')].filter(e=>name(e)==='Accept ticket and start');
  const ready=matches.filter(e=>visible(e)&&enabled(e));
  const complaint=[...document.querySelectorAll('.tsj-product-complaint')].filter(e=>visible(e)&&e.textContent.trim()==='Indicator does not light.');
  let point=null;
  if(ready.length===1){const r=ready[0].getBoundingClientRect();point={x:r.left+(r.width/2),y:r.top+(r.height/2)};}
  return {labelledCount:matches.length,visibleEnabledCount:ready.length,complaintVisible:complaint.length===1,point};})()
'@
    return Evaluate-Cdp $Socket $expression $Deadline
}

function Invoke-OrdinaryTicketEntry($Socket, [DateTime]$Deadline) {
    $target = $null
    do {
        $target = Get-OrdinaryTicketEntryTarget $Socket $Deadline
        if ($null -ne $target -and [int]$target.visibleEnabledCount -gt 1) {
            Throw-AppFailure 'Ordinary service ticket exposed more than one visible enabled Accept ticket and start control.'
        }
        if ($null -ne $target -and [int]$target.visibleEnabledCount -eq 1 -and
                $target.complaintVisible -is [bool] -and $target.complaintVisible -and
                $null -ne $target.point -and
                -not [double]::IsNaN([double]$target.point.x) -and
                -not [double]::IsInfinity([double]$target.point.x) -and
                -not [double]::IsNaN([double]$target.point.y) -and
                -not [double]::IsInfinity([double]$target.point.y)) { break }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $Deadline)
    if ($null -eq $target -or [int]$target.visibleEnabledCount -ne 1 -or
            $target.complaintVisible -isnot [bool] -or -not $target.complaintVisible -or
            $null -eq $target.point) {
        Throw-VerifierInfrastructure 'Timed out waiting for the expected visible LED complaint and one visible enabled Accept ticket and start control.'
    }
    $x = [double]$target.point.x
    $y = [double]$target.point.y
    [void](Invoke-Cdp $Socket 'Input.dispatchMouseEvent' @{ type='mouseMoved'; x=$x; y=$y } $Deadline)
    [void](Invoke-Cdp $Socket 'Input.dispatchMouseEvent' @{ type='mousePressed'; x=$x; y=$y;
        button='left'; clickCount=1 } $Deadline)
    [void](Invoke-Cdp $Socket 'Input.dispatchMouseEvent' @{ type='mouseReleased'; x=$x; y=$y;
        button='left'; clickCount=1 } $Deadline)
    return [ordered]@{
        protocol = 'tsj-ordinary-ticket-entry-v1'
        status = 'PASS'
        buttonLabel = 'Accept ticket and start'
        labelledCount = [int]$target.labelledCount
        visibleEnabledCount = [int]$target.visibleEnabledCount
        expectedComplaintVisible = [bool]$target.complaintVisible
        clickPoint = [ordered]@{ x = $x; y = $y }
        inputEvents = @('mouseMoved','mousePressed:left','mouseReleased:left')
    }
}

function Navigate-Cdp($Socket, [string]$Url, [string]$RunId,
        [string]$RouteId, [DateTime]$Deadline) {
    $marker = [Guid]::NewGuid().ToString('N')
    $separator = if ($Url.IndexOf('?') -ge 0) { '&' } else { '?' }
    $navigationUrl = $Url + $separator +
        'tsjVerifierNavigation=' + $marker +
        '&tsjVerifierRun=' + $RunId + '&tsjVerifierRoute=' + $RouteId
    [void](Invoke-Cdp $Socket 'Page.navigate' @{ url = $navigationUrl } $Deadline)
    $escapedMarker = $marker.Replace("'", "\\'")
    Wait-Cdp $Socket ("document.readyState==='complete'&&location.href.includes('$escapedMarker')") `
        $Deadline 'compiled page navigation'
    return $navigationUrl
}

function Test-JsonReport([object]$Value, [string]$Protocol) {
    $text = Get-ExactReportText $Value
    if ([String]::IsNullOrWhiteSpace($text)) { return $false }
    try { $parsed = $text | ConvertFrom-Json -ErrorAction Stop }
    catch { return $false }
    return $null -ne $parsed -and
        $parsed.PSObject.Properties['protocol'] -and
        [string]$parsed.protocol -ceq $Protocol -and
        $parsed.PSObject.Properties['status'] -and
        [string]$parsed.status -ceq 'PASS'
}

function Test-Task41Report([object]$Value) {
    $text = Get-ExactReportText $Value
    if ([String]::IsNullOrWhiteSpace($text) -or
            $text -notmatch '(?m)(?:^|;)routes=([0-9]+)(?:;|$)' -or
            [int]$Matches[1] -le 0) { return $false }
    if ($text -notmatch '(?m)(?:^|;)solverSamples=([0-9]+)(?:;|$)' -or
            [int]$Matches[1] -le 0) { return $false }
    # GWT 2.7 emits the Java boolean StringBuilder value as 1, whereas the
    # JVM's diagnostic form is true. Both are the sole affirmative encodings;
    # false/0 and every other value remain a failed report.
    return $text -match '(?m)(?:^|;)retest=(?:true|1)(?:;|$)' -and
        $text -match '(?m)(?:^|;)sampleToleranceEvidence=[^;\r\n]+' -and
        $text -match '(?m);result=PASS$'
}

function Test-A07FiniteNumber($Value, [double]$Minimum, [double]$Maximum) {
    if ($Value -isnot [int] -and $Value -isnot [long] -and $Value -isnot [double] -and
            $Value -isnot [decimal]) { return $false }
    $n = [double]$Value
    return -not [double]::IsNaN($n) -and -not [double]::IsInfinity($n) -and
        $n -ge $Minimum -and $n -le $Maximum
}

function Test-A08Report([object]$Value) {
    try {
        $p = (Get-ExactReportText $Value) | ConvertFrom-Json -ErrorAction Stop
        if ($p.version -cne 'TSJ-A08-MUTATION-1' -or $p.status -cne 'PASS' -or $p.cleanup -cne 'PASS' -or
                -not (Test-VerifierStrictIntegralValue $p.assertions 600L ([long]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $p.diodeScenarios 3L 3L) -or
                -not (Test-VerifierStrictIntegralValue $p.compensatedWrites 90L 90L) -or
                -not (Test-VerifierStrictIntegralValue $p.freshFailures 5L 5L)) { return $false }
        foreach ($field in @('wallMs','maxSettlementMs','maxFailedMutationMs')) {
            if (-not (Test-VerifierStrictIntegralValue $p.$field 0L ([long]::MaxValue))) { return $false }
        }
        if ($p.maxSettlementMs -gt $p.wallMs -or $p.maxFailedMutationMs -gt $p.wallMs) { return $false }
        $cases = @('resistor-compensation-and-damage','diode-open-0','diode-open-3','diode-short-0',
            'diode-failed-compensation-isolated','stale-scope-successor-preserved','same-owner-graph-replacement-preserved','fresh-owner-no-alias',
            'fresh-install-failure-matrix','restricted-scope-write-guards','fresh-mutable-holder-aliases','fresh-callback-owner-aliases','fresh-family-captured-owner-aliases','scoped-admission-identity','proof-owner-restored')
        if ($p.cases -isnot [array] -or $p.cases.Count -ne $cases.Count) { return $false }
        foreach ($name in $cases) {
            $found = @($p.cases | Where-Object { $_.case -ceq $name -and $_.status -ceq 'PASS' })
            if ($found.Count -ne 1) { return $false }
        }
        # Independently enumerate each actual write and repeated endpoint/conductor write.
        $operations = [ordered]@{
            remove=@('GRAPH_DISCONNECT:1','GRAPH_DISCONNECT:2','SLOT_CLEAR:1','COMMIT:1')
            lift=@('GRAPH_DISCONNECT:1')
            reconnect=@('GRAPH_CONNECT:1')
            restore=@('GRAPH_CONNECT:1','GRAPH_CONNECT:2','GRAPH_RESTORE:1')
            catalog=@('INVENTORY_ACQUIRE:1','CANONICAL_REGISTER:1','GRAPH_APPEND:1','PRIMARY_BINDING:1',
                'ENDPOINT_RETARGET:1','ENDPOINT_RETARGET:2','ATTACHMENT:1','SLOT_MOUNT:1',
                'GRAPH_CONNECT:1','GRAPH_CONNECT:2','GRAPH_RESTORE:1','COMMIT:1')
            install=@('PRIMARY_BINDING:1','ENDPOINT_RETARGET:1','ENDPOINT_RETARGET:2','ATTACHMENT:1',
                'SLOT_MOUNT:1','GRAPH_CONNECT:1','GRAPH_CONNECT:2','GRAPH_RESTORE:1','COMMIT:1')
        }
        if ($p.failures -isnot [array] -or $p.failures.Count -ne 90) { return $false }
        foreach ($scenario in @('diode-open-0','diode-open-3','diode-short-0')) {
            foreach ($operation in $operations.Keys) {
                foreach ($write in $operations[$operation]) {
                    $parts = $write.Split(':'); $stage = 'AFTER_' + $parts[0]; $occurrence = [long]$parts[1]
                    $rows = @($p.failures | Where-Object { $_.scenario -ceq $scenario -and
                        $_.operation -ceq $operation -and $_.stage -ceq $stage -and
                        (Test-VerifierStrictIntegralValue $_.occurrence $occurrence $occurrence) -and
                        $_.status -ceq 'COMPENSATED' })
                    if ($rows.Count -ne 1) { return $false }
                }
            }
        }
        return $true
    } catch { return $false }
}

function Test-A07Report([object]$Value) {
    try {
        $p = (Get-ExactReportText $Value) | ConvertFrom-Json -ErrorAction Stop
        if ($p.version -cne 'TSJ-A07-SOLVER-1' -or $p.status -cne 'PASS' -or $p.cleanup -cne 'PASS' -or
                -not (Test-VerifierStrictIntegralValue $p.pureAssertions 38L ([long]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $p.runtimeAssertions 73L ([long]::MaxValue)) -or
                -not (Test-A07FiniteNumber $p.wallMs 0 ([double]::MaxValue))) { return $false }
        if ($p.latencies -isnot [array] -or $p.latencies.Count -ne 6) { return $false }
        foreach ($kind in @('completed','cancel-0','cancel-1','cancel-2','cancel-3','cancel-4')) {
            $rows=@($p.latencies | Where-Object case -ceq $kind)
            if ($rows.Count -ne 1) { return $false }
            $row=$rows[0]
            foreach ($field in @('slices','acceptedSteps','wallMs','maxSliceMs','maxYieldMs')) {
                if (-not (Test-VerifierStrictIntegralValue $row.$field 0L ([long]::MaxValue))) { return $false }
            }
            if ($kind -ceq 'completed') {
                if ($row.slices -lt 2 -or $row.acceptedSteps -ne 200 -or $null -ne $row.cancelToTerminalMs) { return $false }
            } elseif (-not (Test-VerifierStrictIntegralValue $row.cancelToTerminalMs 0L ([long]::MaxValue))) { return $false }
            if ($row.maxSliceMs -gt $row.wallMs -or $row.maxYieldMs -gt $row.wallMs -or
                    $row.cancelToTerminalMs -gt $row.wallMs) { return $false }
        }
        $cases = @('accepted-state-source-and-time-identity',
            'exclusive-private-graph-and-reentrant-model', 'real-nonfinite', 'real-matrix-nonfinite',
            'real-matrix-overflow', 'real-singular',
            'real-nonconvergent', 'real-accepted-step-scheduling', 'real-finite-event-feedback',
            'real-stale-owner-callback-and-restore-refusal', 'real-injected-cleanup-failure-and-explicit-recovery',
            'real-browser-yield-and-accepted-publication', 'cancel-0-and-obsolete-callback',
            'cancel-1-and-obsolete-callback', 'cancel-2-and-obsolete-callback',
            'cancel-3-and-obsolete-callback', 'cancel-4-and-obsolete-callback',
            'schematic-reload-empty-event-clock',
            'schematic-reload-discards-old-events',
            'schematic-reanalysis-preserves-events',
            'schematic-retaining-import-preserves-events',
            'schematic-undo-retires-event-clock',
            'schematic-redo-retires-event-clock')
        if ($p.runtimeCases -isnot [array] -or $p.runtimeCases.Count -ne $cases.Count) { return $false }
        foreach ($name in $cases) {
            $found = @($p.runtimeCases | Where-Object { $_.case -ceq $name -and $_.status -ceq 'PASS' })
            if ($found.Count -ne 1) { return $false }
        }
        if ($p.models.rows -isnot [array] -or $p.models.rows.Count -ne 21 -or
                -not (Test-VerifierStrictIntegralValue $p.models.assertions 59L ([long]::MaxValue)) -or
                $null -ne $p.models.physicalPackages -or $p.models.playableQualification -isnot [bool] -or
                $p.models.playableQualification -or $p.models.limits -isnot [array] -or
                $p.models.limits.Count -lt 4) { return $false }
        foreach ($limit in $p.models.limits) {
            if ($limit -isnot [string] -or [String]::IsNullOrWhiteSpace($limit)) { return $false }
        }
        $rl = 0.05 * (1 - [Math]::Exp(-5))
        # Reader-side physical envelopes, not a report-provided tolerance or success counter.
        $models = @(
            @{name='relay-energize';dt=@(.0001,.00005);lo=$rl-.0001;hi=$rl+.0001},
            @{name='relay-release';dt=@(.0001,.00005);lo=-.0001;hi=.0001},
            @{name='transformer-loaded';dt=@(.0001,.00005);lo=.48;hi=.52},
            @{name='transformer-load-step';dt=@(.0001,.00005);lo=.48;hi=.52},
            @{name='diode-forward';dt=@(.000005,.0000025);lo=.3;hi=1.0},
            @{name='diode-reverse-current';dt=@(.000005,.0000025);lo=-.000005;hi=.000005},
            @{name='converter-20ohm-mean';dt=@(.000005,.0000025);lo=4.8;hi=6.5},
            @{name='converter-20ohm-ripple';dt=@(.000005,.0000025);lo=0;hi=.5},
            @{name='converter-10ohm-mean';dt=@(.000005,.0000025);lo=4.8;hi=6.5},
            @{name='reference-0';dt=@(.000005);lo=2.499999;hi=2.500001},
            @{name='reference-1';dt=@(.000005);lo=2.499999;hi=2.500001},
            @{name='reference-2';dt=@(.000005);lo=2.499999;hi=2.500001})
        foreach ($model in $models) {
            foreach ($dt in $model.dt) {
                $found = @($p.models.rows | Where-Object { $_.model -ceq $model.name -and
                    (Test-A07FiniteNumber $_.timeStep $dt $dt) })
                if ($found.Count -ne 1) { return $false }
                $row = $found[0]
                if (-not (Test-A07FiniteNumber $row.value $model.lo $model.hi) -or
                        -not (Test-A07FiniteNumber $row.simulatedSeconds $dt ([double]::MaxValue)) -or
                        -not (Test-A07FiniteNumber $row.wallMs 0 ([double]::MaxValue))) { return $false }
                foreach ($metric in @('solverElements','matrixFull','acceptedSteps','nonlinearTrials',
                        'analyses','restamps','factorizations','solves')) {
                    if (-not (Test-VerifierStrictIntegralValue $row.$metric 1L ([long]::MaxValue))) { return $false }
                }
                if (-not (Test-VerifierStrictIntegralValue $row.matrixReduced 0L $row.matrixFull) -or
                        $row.nonlinearTrials -lt $row.acceptedSteps) { return $false }
            }
        }
        if ($p.scale -isnot [array] -or $p.scale.Count -ne 16) { return $false }
        foreach ($size in @(20,40,60,100)) { foreach ($seed in @(0,1)) { foreach ($replicate in @(1,2)) {
            $found = @($p.scale | Where-Object {
                (Test-VerifierStrictIntegralValue $_.size $size $size) -and
                (Test-VerifierStrictIntegralValue $_.seed $seed $seed) -and
                (Test-VerifierStrictIntegralValue $_.replicate $replicate $replicate) })
            if ($found.Count -ne 1) { return $false }
            $row = $found[0]
            if ($row.status -cne 'PASS' -or $row.stageStatus -cne 'SOLVER_PASS' -or
                    $row.corpus -cne 'a07' -or $row.contextReuse -isnot [bool] -or $row.contextReuse -or
                    $row.fixtureVersion -cne 'a01-series-ladder-v1' -or $null -ne $row.physicalPackages -or
                    $row.identityIndependentOfTiming -isnot [bool] -or -not $row.identityIndependentOfTiming -or
                    -not (Test-VerifierStrictIntegralValue $row.solverElements ($size+2) ($size+2)) -or
                    -not (Test-VerifierStrictIntegralValue $row.matrixFullSize ($size+3) ($size+3)) -or
                    -not (Test-VerifierStrictIntegralValue $row.matrixReducedSize 0L ($size+3)) -or
                    -not (Test-VerifierStrictIntegralValue $row.acceptedStepCount 2L 2L) -or
                    -not (Test-A07FiniteNumber $row.elapsedMs 0 ([double]::MaxValue)) -or
                    -not (Test-A07FiniteNumber $row.simulatedSeconds .000000001 ([double]::MaxValue)) -or
                    -not (Test-VerifierStrictIntegralValue $row.matrixDoubleStorageProxyBytes 1L ([long]::MaxValue))) {
                return $false
            }
            foreach ($metric in @('analysisCount','stampCount','factorizationCount','solveCount','iterationCount','subIterationCount')) {
                if (-not (Test-VerifierStrictIntegralValue $row.$metric 1L ([long]::MaxValue))) { return $false }
            }
            # Independent series-ladder equations, including every node, for both seeds.
            $resistances = @(for ($i=0; $i -lt $size; $i++) { 100.0 + 10.0 * (($i + $seed) % 7) })
            $total = ($resistances | Measure-Object -Sum).Sum
            $current = 10.0 / $total
            if (-not (Test-A07FiniteNumber $row.sourceVoltage 10 10) -or
                    -not (Test-A07FiniteNumber $row.totalResistance $total $total) -or
                    -not (Test-A07FiniteNumber $row.observedCurrent ($current-1e-9) ($current+1e-9)) -or
                    $row.nodeVoltages -isnot [array] -or $row.nodeVoltages.Count -ne $size+1) { return $false }
            $voltage=10.0
            for ($i=0; $i -le $size; $i++) {
                if (-not (Test-A07FiniteNumber $row.nodeVoltages[$i] ($voltage-1e-6) ($voltage+1e-6))) { return $false }
                if ($i -lt $size) { $voltage -= $current * $resistances[$i] }
            }
        } } }
        return $true
    } catch { return $false }
}

function Test-A06Report([object]$Value) {
    if (-not (Test-JsonReport $Value 'TSJ-A06-POWER-1')) { return $false }
    try {
        $p = $Value | ConvertFrom-Json -ErrorAction Stop
        if (-not (Test-VerifierStrictIntegralValue $p.pureAssertions 60L ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $p.runtimeAssertions 20L ([long]::MaxValue))) { return $false }
        foreach ($name in @('originalOwnerRestored','sourceIsolation','staleOwnerRejected',
                'differentialReference','earthConnectionNotInvented')) {
            if ($p.$name -isnot [bool] -or -not $p.$name) { return $false }
        }
        if ($p.candidateCleanup -cne 'PASS' -or $p.vectors -isnot [string] -or
                -not $p.vectors.StartsWith('A06-POWER-VECTORS-1;')) { return $false }
        if ($p.backfeedVolts -isnot [double] -and $p.backfeedVolts -isnot [decimal]) { return $false }
        return -not [double]::IsNaN($p.backfeedVolts) -and -not [double]::IsInfinity($p.backfeedVolts) -and
            [Math]::Abs([double]$p.backfeedVolts - 2.5) -lt 0.000001
    } catch { return $false }
}

function Test-A04Report([object]$Value) {
    $text = Get-ExactReportText $Value
    if (-not (Test-JsonReport $text 'TSJ-A04-CONSTRUCTION-1')) { return $false }
    try { $parsed = $text | ConvertFrom-Json -ErrorAction Stop } catch { return $false }
    foreach ($name in @('contractAssertions', 'runtimeAssertions')) {
        if (-not $parsed.PSObject.Properties[$name] -or
                -not (Test-VerifierStrictIntegralValue $parsed.$name 1L ([long]::MaxValue))) {
            return $false
        }
    }
    foreach ($name in @('coordinateIsolation', 'explicitJoins', 'terminalCorrespondence',
            'failureIsolation', 'recipeIdentity', 'multiUnitPackage', 'originalOwnerRestored')) {
        if (-not $parsed.PSObject.Properties[$name] -or
                $parsed.$name -isnot [bool] -or -not $parsed.$name) { return $false }
    }
    if (-not $parsed.PSObject.Properties['candidateCleanup'] -or
            $parsed.candidateCleanup -isnot [string] -or
            $parsed.candidateCleanup -cne 'PASS' -or
            -not $parsed.PSObject.Properties['cases'] -or
            $parsed.cases -isnot [array] -or $parsed.cases.Count -ne 6) { return $false }
    $caseKeys = @()
    foreach ($case in $parsed.cases) {
        if ($null -eq $case -or -not $case.PSObject.Properties['route'] -or
                $case.route -isnot [string] -or
                @('resistive', 'controlled') -notcontains $case.route -or
                -not $case.PSObject.Properties['seed'] -or $case.seed -isnot [string] -or
                $case.seed -notin @('1', '2', '3') -or
                -not $case.PSObject.Properties['generatorVersion'] -or
                -not (Test-VerifierStrictIntegralValue $case.generatorVersion 6L 6L)) {
            return $false
        }
        foreach ($name in @('replayVerified', 'ownerRestored')) {
            if (-not $case.PSObject.Properties[$name] -or
                    $case.$name -isnot [bool] -or -not $case.$name) {
                return $false
            }
        }
        foreach ($name in @('elapsedMs', 'assemblyMs')) {
            if (-not $case.PSObject.Properties[$name] -or
                    -not (Test-VerifierStrictIntegralValue $case.$name 0L ([long]::MaxValue))) {
                return $false
            }
        }
        $caseKeys += ($case.route + ':' + $case.seed)
    }
    return (($caseKeys | Sort-Object -Unique) -join ',') -ceq
        'controlled:1,controlled:2,controlled:3,resistive:1,resistive:2,resistive:3'
}

function Test-ControlledReport([object]$Value, [string]$Protocol) {
    if (-not (Test-JsonReport $Value $Protocol)) { return $false }
    # This reader checks the executed coverage, not only a top-level PASS word.
    # Pure selection, actual solver cases, repair outcomes and cleanup are
    # separate fields so a partial run cannot masquerade as qualification.
    try {
        $parsed = (Get-ExactReportText $Value) | ConvertFrom-Json -ErrorAction Stop
        $requestedSeedValue = 0L
        if ($parsed.requestedSeed -isnot [string] -or
                -not [long]::TryParse($parsed.requestedSeed, [ref]$requestedSeedValue) -or
                $requestedSeedValue.ToString([Globalization.CultureInfo]::InvariantCulture) -cne
                    $parsed.requestedSeed) { return $false }
        $hasTrueFlags = { param($Object, [string[]]$Fields)
            if ($null -eq $Object) { return $false }
            foreach ($field in $Fields) {
                if (-not $Object.PSObject.Properties[$field] -or
                        $Object.$field -isnot [bool] -or -not $Object.$field) { return $false }
            }
            return $true
        }
        if (-not (& $hasTrueFlags $parsed @('originalOwnerRestored')) -or
                $parsed.candidateCleanup -cne 'PASS' -or
                -not (Test-VerifierStrictIntegralValue $parsed.assertions 1L ([long]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $parsed.measurementCases 4L ([long]::MaxValue))) {
            return $false
        }
        $valueSeeds = @('-1','0','1','2','3','-9223372036854775808','9223372036854775807',
            '9007199254740993','-9007199254740993')
        $solvedSeeds = @('-1','0','1','-9223372036854775808')
        $roleSeeds = @('-1','0','1','2','-9223372036854775808','9223372036854775807')
        if ($parsed.valueCases -isnot [array] -or $parsed.valueCases.Count -ne 9 -or
                $parsed.cases -isnot [array] -or $parsed.cases.Count -ne 4 -or
                $parsed.roleSelectionVectors -isnot [array] -or
                $parsed.roleSelectionVectors.Count -ne 6) { return $false }
        $actualValueSeeds = @()
        foreach ($case in $parsed.valueCases) {
            if ($case.seed -isnot [string] -or $case.seed -cnotin $valueSeeds -or
                    $case.descriptor -isnot [string] -or
                    [String]::IsNullOrWhiteSpace($case.descriptor) -or
                    $case.faultDecision -isnot [string] -or
                    [String]::IsNullOrWhiteSpace($case.faultDecision) -or
                    $case.channels -isnot [array] -or $case.channels.Count -ne 2) { return $false }
            $actualValueSeeds += $case.seed
            $channelIds = @()
            foreach ($channel in $case.channels) {
                if ($channel.channel -isnot [string] -or
                        $channel.channel -cnotin @('channel-a','channel-b') -or
                        $channel.provider -cnotin @('nmos-low-side-driver','nmos-low-side-driver-alt','npn-low-side-driver') -or
                        $channel.catalog -isnot [string] -or
                        [String]::IsNullOrWhiteSpace($channel.catalog) -or
                        -not (Test-VerifierStrictIntegralValue $channel.resistanceOhms 270L 330L) -or
                        $channel.resistanceOhms -notin @(270,330) -or
                        -not (Test-VerifierStrictIntegralValue $channel.tolerancePercent 5L 5L)) {
                    return $false
                }
                $channelIds += $channel.channel
            }
            if (($channelIds | Sort-Object -Unique) -join ',' -cne 'channel-a,channel-b') { return $false }
        }
        if (($actualValueSeeds -join ',') -cne ($valueSeeds -join ',')) { return $false }
        for ($i = 0; $i -lt $roleSeeds.Count; $i++) {
            $vector = $parsed.roleSelectionVectors[$i]
            if ($vector -isnot [string] -or
                    $vector -cnotmatch ('^seed=' + [regex]::Escape($roleSeeds[$i]) +
                        ';a=(nmos-low-side-driver(?:-alt)?|npn-low-side-driver);b=(nmos-low-side-driver(?:-alt)?|npn-low-side-driver);fault=[A-Za-z0-9_.-]+$')) {
                return $false
            }
        }
        $actualSolvedSeeds = @()
        foreach ($case in $parsed.cases) {
            if ($case.seed -isnot [string] -or $case.seed -cnotin $solvedSeeds -or
                    -not (& $hasTrueFlags $case @('freshReplay','inputOrderIndependent',
                        'independentControls','repairReachable')) -or
                    -not (Test-VerifierStrictIntegralValue $case.assertions 1L ([long]::MaxValue)) -or
                    $case.physicalCorrespondence.status -cne 'PASS' -or
                    $case.normalAdmission.status -cne 'EXECUTED' -or
                    $case.normalAdmission.routes -isnot [array] -or
                    $case.normalAdmission.routes.Count -ne 4 -or
                    $case.faultRepairs -isnot [array] -or $case.faultRepairs.Count -ne 4 -or
                    -not (& $hasTrueFlags $case.support @('brokenHealthyRejected',
                        'brokenRetestRejected','restoredPassed'))) { return $false }
            $actualSolvedSeeds += $case.seed
            $owners = @()
            foreach ($repair in $case.faultRepairs) {
                if ($repair.owner -isnot [string] -or [String]::IsNullOrWhiteSpace($repair.owner) -or
                        -not (& $hasTrueFlags $repair @('wrongRejected','correctPassed',
                            'alternativePassed','otherOwnersUnchanged'))) { return $false }
                $owners += $repair.owner
                $matched = @($case.normalAdmission.routes | Where-Object {
                    $_.route -is [string] -and $_.route.EndsWith('/' + $repair.owner,
                        [StringComparison]::Ordinal)
                })
                if ($matched.Count -ne 1) { return $false }
            }
            if ((@($owners | Sort-Object -Unique)).Count -ne 4 -or
                    $case.faultOwner -cnotin $owners) { return $false }
            foreach ($route in $case.normalAdmission.routes) {
                if (-not (& $hasTrueFlags $route @('repair','retest')) -or
                        -not (Test-VerifierStrictIntegralValue $route.measuredDepth 1L ([long]::MaxValue)) -or
                        -not (Test-VerifierStrictIntegralValue $route.samples 1L ([long]::MaxValue))) {
                    return $false
                }
            }
            if ($case.mutations.status -cne 'PASS' -or
                    -not (Test-VerifierStrictIntegralValue $case.mutations.owners 4L 4L)) { return $false }
        }
        return ($actualSolvedSeeds -join ',') -ceq ($solvedSeeds -join ',') -and
            (& $hasTrueFlags $parsed.construction @('originalOwnerPreserved')) -and
            $parsed.construction.failedStages -is [array] -and
            ($parsed.construction.failedStages -join ',') -ceq 'MAPPING,ELECTRICAL,LAYOUT,REGISTRATION,VALIDATION' -and
            (& $hasTrueFlags $parsed.succession @('boardReplacement','staleCompletion'))
    } catch { return $false }
}

function Test-Q100FiniteNumber([object]$Value) {
    return ($Value -is [int] -or $Value -is [long] -or $Value -is [double] -or $Value -is [decimal]) -and
        -not [double]::IsNaN([double]$Value) -and -not [double]::IsInfinity([double]$Value)
}

function Test-Q100Number([object]$Value, [double]$Minimum, [double]$Maximum) {
    return (Test-Q100FiniteNumber $Value) -and
        [double]$Value -ge $Minimum -and [double]$Value -le $Maximum
}

function Test-Q100TemporalBatchMetadata([object]$Reference,
        [long]$ExpectedAcceptedSteps, [long]$ExpectedBatches,
        [long]$ExpectedMaxBatchAcceptedSteps) {
    try {
        $fixedStepVerified = if ($null -ne $Reference) {
            $Reference.PSObject.Properties['fixedStepVerified']
        } else { $null }
        return $null -ne $Reference -and
            $Reference.advanceMode -is [string] -and
            $Reference.advanceMode -ceq 'TEMPORAL_BATCH' -and
            (Test-VerifierStrictIntegralValue $Reference.operationLimitMs 5000L 5000L) -and
            (Test-VerifierStrictIntegralValue $Reference.trialBoundaryLimitMs 500L 500L) -and
            (Test-VerifierStrictIntegralValue $Reference.maxBatchSteps 64L 64L) -and
            $null -ne $fixedStepVerified -and
            $fixedStepVerified.Value -is [bool] -and $fixedStepVerified.Value -and
            (Test-VerifierStrictIntegralValue $Reference.acceptedSteps `
                $ExpectedAcceptedSteps $ExpectedAcceptedSteps) -and
            (Test-VerifierStrictIntegralValue $Reference.stepBatches `
                $ExpectedBatches $ExpectedBatches) -and
            (Test-VerifierStrictIntegralValue $Reference.maxBatchAcceptedSteps `
                $ExpectedMaxBatchAcceptedSteps $ExpectedMaxBatchAcceptedSteps)
    } catch { return $false }
}

function Get-Q100FaultOracle() {
    # Independent frozen symptom oracle copied from the Q100 pilot plan, not
    # read from the producer's fault table or candidate report.
    return @(
        [pscustomobject]@{ id='RENAC_RESISTANCE'; context='HIGH'; witness='RAIL12'; above=$false; threshold=1.0 },
        [pscustomobject]@{ id='REN_RESISTANCE'; context='HIGH'; witness='RAIL5'; above=$false; threshold=1.0 },
        [pscustomobject]@{ id='RSA_RESISTANCE'; context='HIGH'; witness='OUT_A'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='RDA_RESISTANCE'; context='HIGH'; witness='OUT_A'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='KB_COIL_OPEN'; context='HIGH'; witness='OUT_B'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='RSC_RESISTANCE'; context='HIGH'; witness='OUT_C'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='RSD_RESISTANCE'; context='HIGH'; witness='OUT_D'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='RDC_RESISTANCE'; context='HIGH'; witness='OUT_C'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='RDD_RESISTANCE'; context='HIGH'; witness='OUT_D'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='KC_COIL_OPEN'; context='HIGH'; witness='OUT_C'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='KD_COIL_OPEN'; context='HIGH'; witness='OUT_D'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='RDE_RESISTANCE'; context='HIGH'; witness='OUT_E'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='RDF_RESISTANCE'; context='HIGH'; witness='OUT_F'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='RSH_E_RESISTANCE'; context='HIGH'; witness='OUT_E'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='RSH_F_RESISTANCE'; context='HIGH'; witness='OUT_F'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='DE_SHUNT'; context='HIGH'; witness='OUT_E'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='DF_SHUNT'; context='HIGH'; witness='OUT_F'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='CSTORE_E_SHUNT'; context='HIGH'; witness='OUT_E'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='CSTORE_F_SHUNT'; context='HIGH'; witness='OUT_F'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='C33_SHUNT'; context='HIGH'; witness='RAIL3V3'; above=$false; threshold=1.0 },
        [pscustomobject]@{ id='CIN33_SHUNT'; context='HIGH'; witness='RAIL5'; above=$false; threshold=1.0 },
        [pscustomobject]@{ id='RMON_RESISTANCE'; context='HIGH'; witness='OUT_C'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='RMON_H_RESISTANCE'; context='OVERLOAD'; witness='OUT_C'; above=$true; threshold=9.0 },
        [pscustomobject]@{ id='RMON_L_RESISTANCE'; context='OVERLOAD'; witness='OUT_C'; above=$true; threshold=9.0 },
        [pscustomobject]@{ id='CMON_SHUNT'; context='OVERLOAD'; witness='OUT_C'; above=$true; threshold=9.0 },
        [pscustomobject]@{ id='RBAUX_RESISTANCE'; context='OVERLOAD'; witness='OUT_C'; above=$true; threshold=9.0 },
        [pscustomobject]@{ id='RPUAUX_RESISTANCE'; context='OVERLOAD'; witness='AUX_GATE'; above=$true; threshold=1.0 },
        [pscustomobject]@{ id='QAUX_SHUNT'; context='OVERLOAD'; witness='OUT_C'; above=$true; threshold=9.0 },
        [pscustomobject]@{ id='CFLT_C_SHUNT'; context='HIGH'; witness='OUT_C'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='CFLT_D_SHUNT'; context='HIGH'; witness='OUT_D'; above=$false; threshold=0.5 },
        [pscustomobject]@{ id='RMON_IN_RESISTANCE'; context='OVERLOAD'; witness='OUT_C'; above=$true; threshold=9.0 }
    )
}

function Get-Q100FaultIds() {
    return @((Get-Q100FaultOracle) | ForEach-Object { [string]$_.id })
}

function ConvertFrom-Q100ReportText([object]$Value) {
    $text = Get-ExactReportText $Value
    if ([String]::IsNullOrWhiteSpace($text)) { return $null }
    try { return ($text | ConvertFrom-Json -ErrorAction Stop) }
    catch { return $null }
}

function Test-Q100HealthyObservation([object]$Observation) {
    if ($null -eq $Observation -or $Observation.values -isnot [array] -or
            $Observation.values.Count -ne 14) { return $false }
    foreach ($value in $Observation.values) {
        if (-not (Test-Q100FiniteNumber $value)) { return $false }
    }
    $values = $Observation.values
    if (-not (Test-Q100Number $values[0] 10.0 12.5) -or
            -not (Test-Q100Number $values[1] 4.65 5.25) -or
            -not (Test-Q100Number $values[2] 3.05 3.5) -or
            -not (Test-Q100Number $values[11] 0.14 0.19) -or
            [Math]::Abs([double]$values[10] - [double]$values[9]) -ge 0.01) { return $false }
    $enabled = switch ([string]$Observation.context) {
        'LOW' { @() }
        'HIGH' { @(0, 1, 2, 3, 4, 5) }
        'AC_ONLY' { @(0, 2, 4) }
        'OVERLOAD' { @(0, 1, 4, 5) }
        'RECOVERED' { @(0, 1, 2, 3, 4, 5) }
        'LOW_AGAIN' { @() }
        default { return $false }
    }
    for ($output = 0; $output -lt 6; $output++) {
        if ($output -in $enabled) {
            if ([double]$values[3 + $output] -le 9.0) { return $false }
        } elseif ([Math]::Abs([double]$values[3 + $output]) -ge 0.5) { return $false }
    }
    if ([string]$Observation.context -ceq 'OVERLOAD') {
        return [double]$values[12] -gt 2.0 -and [Math]::Abs([double]$values[13]) -lt 0.5
    }
    return [Math]::Abs([double]$values[12]) -lt 0.5 -and [double]$values[13] -gt 3.5
}

function Test-Q100SeparatedLoadShortFaultRails([object]$Observation) {
    if ($null -eq $Observation -or $Observation.values -isnot [array] -or
            $Observation.values.Count -ne 14) { return $false }
    $values = $Observation.values
    return (Test-Q100Number $values[0] 10.0 12.5) -and
        (Test-Q100Number $values[1] 4.65 5.25) -and
        (Test-Q100Number $values[2] 3.05 3.5)
}

function Test-Q100Report([object]$Value, [string]$ExpectedVariant,
        [string]$ExpectedFault, [double]$ExpectedDt) {
    try {
        $outer = ConvertFrom-Q100ReportText $Value
        if ($null -eq $outer -or $outer.status -cne 'PASS_ELECTRICAL_REFERENCE_ONLY' -or
                $outer.cleanup -cne 'PASS' -or $outer.canary -ne $null -or
                $outer.error -ne $null -or $outer.cleanupError -ne $null -or
                -not (Test-VerifierStrictIntegralValue $outer.operationMs 0L 90000L) -or
                -not (Test-VerifierStrictIntegralValue $outer.cleanupMs 0L 120000L) -or
                $null -eq $outer.PSObject.Properties['reference']) { return $false }
        $reference = $outer.reference
        $referenceDt = if ($ExpectedFault -ceq 'HEALTHY') { $ExpectedDt } else { 0.00005 }
        $isHealthy = $ExpectedFault -ceq 'HEALTHY'
        $expectedSimulatedSeconds = if ($isHealthy) { 0.6 } else { 1.2 }
        $phaseCount = if ($isHealthy) { 1L } else { 2L }
        $expectedAcceptedSteps = [long][Math]::Round($expectedSimulatedSeconds / $referenceDt)
        $longBatchCount = [long][Math]::Ceiling((0.4 / $referenceDt) / 64.0)
        $shortBatchCount = [long][Math]::Ceiling((0.04 / $referenceDt) / 64.0)
        $expectedStepBatches = $phaseCount * ($longBatchCount + (5L * $shortBatchCount))
        $expectedWork = [long]($expectedStepBatches + (6L * $phaseCount) + 1L)
        if ($null -eq $reference -or $reference.protocol -cne 'q100-electrical-reference-v3' -or
                $reference.status -cne 'PASS_ELECTRICAL_REFERENCE_ONLY' -or
                $reference.seed -isnot [string] -or $reference.seed -cne '9007199254740993' -or
                $reference.variant -isnot [string] -or $reference.variant -cne $ExpectedVariant -or
                -not (Test-VerifierStrictIntegralValue $reference.modelVersion 4L 4L) -or
                -not (Test-VerifierStrictIntegralValue $reference.planVersion 1L 1L) -or
                -not (Test-VerifierStrictIntegralValue $reference.faultPopulationVersion 1L 1L) -or
                $reference.fault -isnot [string] -or $reference.fault -cne $ExpectedFault -or
                -not (Test-Q100Number $reference.dt $referenceDt $referenceDt) -or
                -not (Test-VerifierStrictIntegralValue $reference.packages 100L 100L) -or
                -not (Test-VerifierStrictIntegralValue $reference.solverElements 1L ([long]::MaxValue)) -or
                -not (Test-Q100Number $reference.simulatedSeconds 0.0 90000.0) -or
                -not (Test-VerifierStrictIntegralValue $reference.matrixFull 1L ([long]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $reference.matrixReduced 0L $reference.matrixFull) -or
                -not (Test-VerifierStrictIntegralValue $reference.work 1L 640L) -or
                -not (Test-VerifierStrictIntegralValue $reference.work $expectedWork $expectedWork) -or
                -not (Test-VerifierStrictIntegralValue $reference.wallMs 0L 90000L) -or
                -not (Test-VerifierStrictIntegralValue $reference.maxUnitMs 0L 5000L) -or
                -not (Test-Q100TemporalBatchMetadata $reference $expectedAcceptedSteps `
                    $expectedStepBatches 64L) -or
                -not (Test-Q100Number $reference.maxStepTimeError 0.0 0.00000001) -or
                [double]$reference.maxStepTimeError -ge 0.00000001 -or
                $reference.sampleNames -isnot [array] -or $reference.sampleNames.Count -ne 14) { return $false }
        $expectedNames = @('RAIL12','RAIL5','RAIL3V3','OUT_A','OUT_B','OUT_C','OUT_D','OUT_E','OUT_F',
            'MON_SENSE','MON_FILTER','MON_REF','MON_CMD','AUX_GATE')
        for ($i = 0; $i -lt $expectedNames.Count; $i++) {
            if ($reference.sampleNames[$i] -isnot [string] -or
                    $reference.sampleNames[$i] -cne $expectedNames[$i]) { return $false }
        }
        $contexts = @('LOW','HIGH','AC_ONLY','OVERLOAD','RECOVERED','LOW_AGAIN')
        $checkSeparatedLoadShortFaultRails = -not $isHealthy -and $ExpectedFault -cin @(
            'DE_SHUNT', 'DF_SHUNT', 'CSTORE_E_SHUNT', 'CSTORE_F_SHUNT')
        if ([Math]::Abs([double]$reference.simulatedSeconds - $expectedSimulatedSeconds) -gt 0.00001) {
            return $false
        }
        $expectedObservationCount = if ($isHealthy) { 6 } else { 12 }
        if ($reference.observations -isnot [array] -or
                $reference.observations.Count -ne $expectedObservationCount) { return $false }
        $lastTime = [double]::NegativeInfinity
        for ($i = 0; $i -lt $expectedObservationCount; $i++) {
            $observation = $reference.observations[$i]
            $contextIndex = $i % 6
            $expectedPhase = if ($isHealthy) { 'HEALTHY' } elseif ($i -lt 6) { 'FAULT' } else { 'REPAIRED' }
            $expectedSampleTime = if (-not $isHealthy -and $i -ge 6) {
                1.0 + ($contextIndex * 0.04)
            } else { 0.4 + ($contextIndex * 0.04) }
            if ($null -eq $observation -or $observation.context -isnot [string] -or
                    $observation.context -cne $contexts[$contextIndex] -or
                    $observation.phase -isnot [string] -or $observation.phase -cne $expectedPhase -or
                    -not (Test-Q100FiniteNumber $observation.time) -or
                    [double]$observation.time -le $lastTime -or
                    [Math]::Abs([double]$observation.time - $expectedSampleTime) -gt 0.000001 -or
                    $observation.values -isnot [array] -or $observation.values.Count -ne 14) { return $false }
            $lastTime = [double]$observation.time
            foreach ($sample in $observation.values) {
                if (-not (Test-Q100FiniteNumber $sample)) { return $false }
            }
            if ($checkSeparatedLoadShortFaultRails -and $i -lt 6 -and
                    -not (Test-Q100SeparatedLoadShortFaultRails $observation)) { return $false }
            if ($isHealthy -or $i -ge 6) {
                if (-not (Test-Q100HealthyObservation $observation)) { return $false }
            }
        }
        if (-not $isHealthy) {
            $oracle = @(Get-Q100FaultOracle | Where-Object { $_.id -ceq $ExpectedFault })
            if ($oracle.Count -ne 1) { return $false }
            $faultContextIndex = if ($oracle[0].context -ceq 'OVERLOAD') { 3 } else { 1 }
            $faultObservation = $reference.observations[$faultContextIndex]
            if ($faultObservation.context -cne $oracle[0].context) { return $false }
            $witnessIndex = -1
            for ($sampleIndex = 0; $sampleIndex -lt $expectedNames.Count; $sampleIndex++) {
                if ($expectedNames[$sampleIndex] -ceq $oracle[0].witness) { $witnessIndex = $sampleIndex; break }
            }
            if ($witnessIndex -lt 0) { return $false }
            $witnessValue = [double]$faultObservation.values[$witnessIndex]
            if ($oracle[0].above) {
                if ($witnessValue -le [double]$oracle[0].threshold) { return $false }
            } elseif ($witnessValue -ge [double]$oracle[0].threshold) { return $false }
        }
        return $true
    } catch { return $false }
}

function Test-Q100PreflightReport([object]$Value, [string]$ExpectedError) {
    try {
        $report = ConvertFrom-Q100ReportText $Value
        $propertyNames = if ($null -ne $report) {
            @($report.PSObject.Properties | ForEach-Object { [string]$_.Name } | Sort-Object -CaseSensitive)
        } else { @() }
        return $null -ne $report -and
            $propertyNames.Count -eq 3 -and ($propertyNames -join ',') -ceq 'cleanup,error,status' -and
            $report.status -ceq 'REJECTED_BEFORE_PRIVATE_GRAPH' -and
            $report.error -is [string] -and $report.error -ceq $ExpectedError -and
            $report.cleanup -ceq 'NOT_APPLICABLE' -and
            $null -eq $report.PSObject.Properties['reference'] -and
            $null -eq $report.PSObject.Properties['operationMs'] -and
            $null -eq $report.PSObject.Properties['cleanupMs']
    } catch { return $false }
}

function Test-Q100InjectedFailureReport([object]$Value, [string]$Canary) {
    try {
        if ($Canary -cnotin @('after-step','cancel')) { return $false }
        $report = ConvertFrom-Q100ReportText $Value
        $expectedStatus = if ($Canary -ceq 'cancel') { 'CANCELLED_REFERENCE' } else { 'FAIL_REFERENCE' }
        $expectedError = 'q100-explicit-' + $Canary + '-after-accepted-step'
        return $null -ne $report -and $report.status -ceq $expectedStatus -and
            $report.cleanup -ceq 'PASS' -and
            (Test-VerifierStrictIntegralValue $report.operationMs 0L 90000L) -and
            (Test-VerifierStrictIntegralValue $report.cleanupMs 0L 120000L) -and
            $report.error -is [string] -and $report.error -ceq $expectedError -and
            $report.cleanupError -eq $null -and $report.canary -ceq $Canary -and
            $null -ne $report.PSObject.Properties['reference'] -and
            $null -ne $report.reference -and
            $report.reference.protocol -ceq 'q100-electrical-reference-v3' -and
            $report.reference.status -ceq 'INCOMPLETE_ELECTRICAL_REFERENCE' -and
            $report.reference.seed -is [string] -and $report.reference.seed -ceq '9007199254740993' -and
            $report.reference.variant -ceq 'SHARED_HYSTERETIC_LINEAR' -and
            (Test-VerifierStrictIntegralValue $report.reference.modelVersion 4L 4L) -and
            $report.reference.fault -ceq 'HEALTHY' -and
            (Test-VerifierStrictIntegralValue $report.reference.planVersion 1L 1L) -and
            (Test-VerifierStrictIntegralValue $report.reference.faultPopulationVersion 1L 1L) -and
            (Test-Q100Number $report.reference.dt 0.0001 0.0001) -and
            (Test-VerifierStrictIntegralValue $report.reference.packages 100L 100L) -and
            (Test-Q100Number $report.reference.simulatedSeconds 0.0063 0.0065) -and
            (Test-VerifierStrictIntegralValue $report.reference.work 3L 3L) -and
            (Test-VerifierStrictIntegralValue $report.reference.wallMs 0L 90000L) -and
            (Test-VerifierStrictIntegralValue $report.reference.maxUnitMs 0L 5000L) -and
            (Test-Q100TemporalBatchMetadata $report.reference 64L 1L 64L) -and
            $report.reference.observations -is [array] -and $report.reference.observations.Count -eq 0
    } catch { return $false }
}

function Test-Q100InteractionLockReport([object]$Value) {
    try {
        $report = ConvertFrom-Q100ReportText $Value
        $expectedReportProperties = @('status','operationMs','cleanupMs','cleanup','error',
            'cleanupError','canary','reference','interactionLock')
        $reportPropertyNames = if ($null -ne $report) {
            @($report.PSObject.Properties | ForEach-Object { [string]$_.Name } | Sort-Object -CaseSensitive)
        } else { @() }
        $expectedSortedReportProperties = @($expectedReportProperties | Sort-Object -CaseSensitive)
        if ($null -eq $report -or $report -is [array] -or $report -isnot [pscustomobject] -or
                ($reportPropertyNames -join ',') -cne ($expectedSortedReportProperties -join ',') -or
                $report.status -cne 'CANCELLED_REFERENCE' -or $report.cleanup -cne 'PASS' -or
                -not (Test-VerifierStrictIntegralValue $report.operationMs 0L 90000L) -or
                -not (Test-VerifierStrictIntegralValue $report.cleanupMs 0L 120000L) -or
                $report.error -isnot [string] -or
                $report.error -cne 'q100-explicit-interaction-lock-after-accepted-step' -or
                $report.cleanupError -ne $null -or $report.canary -cne 'interaction-lock') { return $false }

        # These wrapper and native-preview event checks are lifecycle evidence,
        # not evidence of human-operated canvas input.
        $lock = $report.interactionLock
        $expectedLockProperties = @('status','selectionAttemptBlocked','deletionAttemptBlocked',
            'runToggleBlocked','graphIdentityStable','elementIdentityStable','elementCountBefore',
            'elementCountAfter','selectionStable','undoDepthBefore','undoDepthAfter',
            'simulationTimeBefore','simulationTimeAfter','acceptedStepCountBefore',
            'acceptedStepCountAfter','runningBefore','runningAfter','privateOwnerStillUnavailable',
            'wheelAttemptBlocked','nativeWheelPreviewBlocked','nativeChangePreviewBlocked',
            'privateCircuitStable','wheelRBefore','wheelRAfter','wheelDelta','popupUnchanged')
        $lockPropertyNames = if ($null -ne $lock) {
            @($lock.PSObject.Properties | ForEach-Object { [string]$_.Name } | Sort-Object -CaseSensitive)
        } else { @() }
        $expectedSortedLockProperties = @($expectedLockProperties | Sort-Object -CaseSensitive)
        if ($null -eq $lock -or $lock -is [array] -or $lock -isnot [pscustomobject] -or
                ($lockPropertyNames -join ',') -cne ($expectedSortedLockProperties -join ',') -or
                $lock.status -cne 'PASS') { return $false }
        foreach ($name in @('selectionAttemptBlocked','deletionAttemptBlocked','runToggleBlocked',
                'graphIdentityStable','elementIdentityStable','selectionStable',
                'privateOwnerStillUnavailable','wheelAttemptBlocked','nativeWheelPreviewBlocked',
                'nativeChangePreviewBlocked','privateCircuitStable','popupUnchanged')) {
            if ($lock.PSObject.Properties[$name].Value -isnot [bool] -or
                    -not $lock.PSObject.Properties[$name].Value) { return $false }
        }
        if (-not (Test-VerifierStrictIntegralValue $lock.elementCountBefore 1L ([long]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $lock.elementCountAfter 1L ([long]::MaxValue)) -or
                [long]$lock.elementCountBefore -ne [long]$lock.elementCountAfter -or
                -not (Test-VerifierStrictIntegralValue $lock.undoDepthBefore 0L ([long]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $lock.undoDepthAfter 0L ([long]::MaxValue)) -or
                [long]$lock.undoDepthBefore -ne [long]$lock.undoDepthAfter -or
                -not (Test-VerifierStrictIntegralValue $lock.acceptedStepCountBefore 0L ([long]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $lock.acceptedStepCountAfter 0L ([long]::MaxValue)) -or
                [long]$lock.acceptedStepCountBefore -ne [long]$lock.acceptedStepCountAfter -or
                -not (Test-Q100Number $lock.simulationTimeBefore 0.0 0.0) -or
                -not (Test-Q100Number $lock.simulationTimeAfter 0.0 0.0) -or
                [double]$lock.simulationTimeBefore -ne [double]$lock.simulationTimeAfter -or
                -not (Test-Q100FiniteNumber $lock.wheelRBefore) -or
                -not (Test-Q100FiniteNumber $lock.wheelRAfter) -or
                [double]$lock.wheelRBefore -ne [double]$lock.wheelRAfter -or
                -not (Test-Q100FiniteNumber $lock.wheelDelta) -or
                [double]$lock.wheelDelta -eq 0.0 -or
                $lock.runningBefore -isnot [bool] -or $lock.runningAfter -isnot [bool] -or
                $lock.runningBefore -ne $lock.runningAfter) { return $false }

        $reference = $report.reference
        $expectedSampleNames = @('RAIL12','RAIL5','RAIL3V3','OUT_A','OUT_B','OUT_C','OUT_D','OUT_E','OUT_F',
            'MON_SENSE','MON_FILTER','MON_REF','MON_CMD','AUX_GATE')
        if ($null -eq $reference -or $reference.protocol -cne 'q100-electrical-reference-v3' -or
                $reference.status -cne 'INCOMPLETE_ELECTRICAL_REFERENCE' -or
                $reference.seed -isnot [string] -or $reference.seed -cne '9007199254740993' -or
                $reference.variant -cne 'SHARED_HYSTERETIC_LINEAR' -or
                -not (Test-VerifierStrictIntegralValue $reference.modelVersion 4L 4L) -or
                -not (Test-VerifierStrictIntegralValue $reference.planVersion 1L 1L) -or
                -not (Test-VerifierStrictIntegralValue $reference.faultPopulationVersion 1L 1L) -or
                $reference.fault -cne 'HEALTHY' -or
                -not (Test-Q100Number $reference.dt 0.0001 0.0001) -or
                -not (Test-VerifierStrictIntegralValue $reference.packages 100L 100L) -or
                -not (Test-VerifierStrictIntegralValue $reference.solverElements 1L ([long]::MaxValue)) -or
                -not (Test-Q100Number $reference.simulatedSeconds 0.00639999 0.00640001) -or
                -not (Test-VerifierStrictIntegralValue $reference.matrixFull 1L ([long]::MaxValue)) -or
                -not (Test-VerifierStrictIntegralValue $reference.matrixReduced 0L $reference.matrixFull) -or
                -not (Test-VerifierStrictIntegralValue $reference.work 3L 3L) -or
                -not (Test-VerifierStrictIntegralValue $reference.wallMs 0L 90000L) -or
                -not (Test-VerifierStrictIntegralValue $reference.maxUnitMs 0L 5000L) -or
                -not (Test-Q100TemporalBatchMetadata $reference 64L 1L 64L) -or
                -not (Test-Q100Number $reference.maxStepTimeError 0.0 0.00000001) -or
                [double]$reference.maxStepTimeError -ge 0.00000001 -or
                $reference.sampleNames -isnot [array] -or
                $reference.sampleNames.Count -ne $expectedSampleNames.Count -or
                $reference.observations -isnot [array] -or $reference.observations.Count -ne 0) { return $false }
        for ($index = 0; $index -lt $expectedSampleNames.Count; $index++) {
            if ($reference.sampleNames[$index] -isnot [string] -or
                    $reference.sampleNames[$index] -cne $expectedSampleNames[$index]) { return $false }
        }
        return $true
    } catch { return $false }
}

function Get-Q100RecordField([object]$Record, [string]$Name) {
    if ($null -eq $Record) { return $null }
    if ($Record -is [Collections.IDictionary]) {
        if ($Record.Contains($Name)) { return $Record[$Name] }
        return $null
    }
    $property = $Record.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Test-Q100HealthyMatrix([object[]]$Routes) {
    $healthy = @($Routes | Where-Object { (Get-Q100RecordField $_ 'kind') -ceq 'q100-healthy' })
    $variants = @('SHARED_HYSTERETIC_LINEAR','SEPARATE_DIRECT_SWITCHING')
    $dts = @(
        [pscustomobject]@{ value=0.0001; token='100us'; key='0.0001' },
        [pscustomobject]@{ value=0.00005; token='50us'; key='0.00005' }
    )
    $expectedNames = @()
    foreach ($variant in $variants) {
        foreach ($dt in $dts) { $expectedNames += ('q100-healthy-' + $variant + '-' + $dt.token) }
    }
    if ($healthy.Count -ne 4) { return $false }
    $references = @{}
    for ($i = 0; $i -lt 4; $i++) {
        $route = $healthy[$i]
        $variantIndex = [int][Math]::Floor($i / 2.0)
        $routeReports = Get-Q100RecordField $route 'reports'
        $q100Text = Get-Q100RecordField $routeReports 'q100'
        if ((Get-Q100RecordField $route 'name') -cne $expectedNames[$i] -or
                -not (Get-Q100RecordField $route 'q100ReportAttributePresent') -or
                -not (Test-Q100Report $q100Text $variants[$variantIndex] 'HEALTHY' $dts[$i % 2].value)) {
            return $false
        }
        $parsed = ConvertFrom-Q100ReportText $q100Text
        $references[$variants[$variantIndex] + '|' + $dts[$i % 2].key] = $parsed.reference
    }
    foreach ($variant in $variants) {
        $fine = $references[$variant + '|0.0001']
        $coarse = $references[$variant + '|0.00005']
        if ($null -eq $fine -or $null -eq $coarse) { return $false }
        for ($context = 0; $context -lt 6; $context++) {
            for ($sample = 0; $sample -lt 9; $sample++) {
                if ([Math]::Abs([double]$fine.observations[$context].values[$sample] -
                        [double]$coarse.observations[$context].values[$sample]) -gt 0.15) { return $false }
            }
        }
    }
    return $true
}

function Test-Q100FaultMatrix([object[]]$Routes) {
    $faults = @($Routes | Where-Object { (Get-Q100RecordField $_ 'kind') -ceq 'q100-fault' })
    $variants = @('SHARED_HYSTERETIC_LINEAR','SEPARATE_DIRECT_SWITCHING')
    $faultIds = @(Get-Q100FaultIds)
    $expectedNames = @()
    $expectedKeys = @()
    foreach ($variant in $variants) {
        foreach ($faultId in $faultIds) {
            $expectedNames += ('q100-fault-' + $variant + '-' + $faultId)
            $expectedKeys += ($variant + '|' + $faultId)
        }
    }
    if ($Routes.Count -ne 62 -or $faults.Count -ne 62) { return $false }
    $actualKeys = @()
    for ($i = 0; $i -lt 62; $i++) {
        $route = $faults[$i]
        $variantIndex = [int][Math]::Floor($i / 31.0)
        $variant = $variants[$variantIndex]
        $faultId = $faultIds[($i % 31)]
        $routeReports = Get-Q100RecordField $route 'reports'
        $q100Text = Get-Q100RecordField $routeReports 'q100'
        if ((Get-Q100RecordField $route 'name') -cne $expectedNames[$i] -or
                -not (Get-Q100RecordField $route 'q100ReportAttributePresent') -or
                -not (Test-Q100Report $q100Text $variant $faultId 0.00005)) { return $false }
        $actualKeys += ($variant + '|' + $faultId)
    }
    return ($actualKeys -join "`n") -ceq ($expectedKeys -join "`n") -and
        (@($actualKeys | Sort-Object -Unique)).Count -eq 62
}

function Test-Q100PlayerDeveloperLeak([object]$Reports) {
    if ($null -eq $Reports) { return $false }
    if ($Reports.verificationPresent -is [bool] -and $Reports.verificationPresent) { return $true }
    if ($Reports.q100Present -is [bool] -and $Reports.q100Present) { return $true }
    if (-not [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.verification)) -or
            -not [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.q100))) { return $true }
    $attributes = $Reports.developerAttributes
    if ($null -eq $attributes) { return $false }
    $attributeProperties = @($attributes.PSObject.Properties)
    return $attributes -is [array] -or $attributeProperties.Count -gt 0
}

function Test-NormalWorkbenchReady([object]$NormalReady) {
    try {
        if ($null -eq $NormalReady -or
                $NormalReady.workbenchNavigationReady -isnot [bool] -or
                -not $NormalReady.workbenchNavigationReady -or
                $NormalReady.programReady -isnot [bool] -or
                -not $NormalReady.programReady -or
                $NormalReady.retestCustomerReady -isnot [bool] -or
                -not $NormalReady.retestCustomerReady -or
                $NormalReady.boardPowerReady -isnot [bool] -or
                -not $NormalReady.boardPowerReady -or
                $NormalReady.boardPowerLabel -isnot [string] -or
                [string]$NormalReady.boardPowerLabel -cnotin @('Board Power: ON','Board Power: OFF')) {
            return $false
        }
        return $NormalReady.programReady -eq
            ($NormalReady.workbenchNavigationReady -and $NormalReady.boardPowerReady)
    } catch { return $false }
}

function Test-Q100PlayerPrivacy([object]$Reports) {
    try {
        if ($null -eq $Reports -or
                $Reports.ready -cne 'complete' -or
                $Reports.verificationPresent -isnot [bool] -or $Reports.verificationPresent -or
                $Reports.q100Present -isnot [bool] -or $Reports.q100Present -or
                -not [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.verification)) -or
                -not [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.q100)) -or
                -not (Test-NormalWorkbenchReady $Reports.normalReady)) { return $false }
        $attributes = $Reports.developerAttributes
        if ($null -eq $attributes -or $attributes -is [array]) { return $false }
        $attributeProperties = @($attributes.PSObject.Properties)
        return $attributeProperties.Count -eq 0
    } catch { return $false }
}

function Test-Q100PlayerPrivacyCanaryEvidence([object]$Canary, [object]$SentinelsCleanup) {
    try {
        $expectedSentinels = @('data-tsj-q100-fault','data-tsj-future-answer')
        if ($null -eq $Canary -or
                $Canary.protocol -isnot [string] -or
                $Canary.protocol -cne 'tsj-q100-player-dom-privacy-canary-v1' -or
                $Canary.status -isnot [string] -or $Canary.status -cne 'PASS' -or
                $Canary.sentinels -isnot [array] -or $Canary.sentinels.Count -ne 2 -or
                $Canary.cases -isnot [array] -or $Canary.cases.Count -ne 2 -or
                $Canary.finalPlayerPrivacy -isnot [string] -or $Canary.finalPlayerPrivacy -cne 'PASS' -or
                $null -eq $SentinelsCleanup -or
                $SentinelsCleanup.status -isnot [string] -or $SentinelsCleanup.status -cne 'PASS' -or
                $SentinelsCleanup.removed -isnot [array] -or $SentinelsCleanup.removed.Count -ne 2 -or
                $SentinelsCleanup.cases -isnot [array] -or $SentinelsCleanup.cases.Count -ne 2) { return $false }
        for ($i = 0; $i -lt $expectedSentinels.Count; $i++) {
            $sentinel = $expectedSentinels[$i]
            if ($Canary.sentinels[$i] -isnot [string] -or
                    $Canary.sentinels[$i] -cne $sentinel -or
                    $SentinelsCleanup.removed[$i] -isnot [string] -or
                    $SentinelsCleanup.removed[$i] -cne $sentinel) { return $false }
            $case = $Canary.cases[$i]
            if ($null -eq $case -or $case.attribute -isnot [string] -or
                    $case.attribute -cne $sentinel -or
                    $case.sentinelValue -isnot [string] -or $case.sentinelValue -cne 'CANARY_ONLY' -or
                    $case.reportContainsAttribute -isnot [bool] -or -not $case.reportContainsAttribute -or
                    $case.developerLeakDetected -isnot [bool] -or -not $case.developerLeakDetected -or
                    $case.playerPrivacyRejected -isnot [bool] -or -not $case.playerPrivacyRejected) { return $false }
            $cleanupCase = $SentinelsCleanup.cases[$i]
            if ($null -eq $cleanupCase -or $cleanupCase.attribute -isnot [string] -or
                    $cleanupCase.attribute -cne $sentinel -or
                    $cleanupCase.removed -isnot [bool] -or -not $cleanupCase.removed) { return $false }
        }
        return $true
    } catch { return $false }
}

function Invoke-Q100PlayerPrivacyNegativeCanary($Socket, [DateTime]$Deadline) {
    $sentinels = @('data-tsj-q100-fault','data-tsj-future-answer')
    $cases = New-Object Collections.Generic.List[object]
    $cleanupCases = New-Object Collections.Generic.List[object]
    foreach ($sentinel in $sentinels) {
        $jsName = ConvertTo-Json -InputObject $sentinel -Compress
        $absentExpression = "(()=>{const d=document.documentElement,n=$jsName;return !!d&&!d.hasAttribute(n);})()"
        $wasAbsent = Evaluate-Cdp $Socket $absentExpression $Deadline
        if ($wasAbsent -isnot [bool] -or -not $wasAbsent) {
            Throw-AppFailure "Q100Player privacy canary sentinel '$sentinel' already exists before the test."
        }
        $setAttempted = $false
        try {
            $setAttempted = $true
            $setExpression = "(()=>{const d=document.documentElement,n=$jsName;if(!d||d.hasAttribute(n))return false;d.setAttribute(n,'CANARY_ONLY');return d.getAttribute(n)==='CANARY_ONLY';})()"
            $set = Evaluate-Cdp $Socket $setExpression $Deadline
            if ($set -isnot [bool] -or -not $set) {
                Throw-AppFailure "Q100Player privacy canary could not set sentinel '$sentinel'."
            }
            $canaryReports = Get-RouteReports $Socket $Deadline
            $leakDetected = Test-Q100PlayerDeveloperLeak $canaryReports
            $privacyRejected = -not (Test-Q100PlayerPrivacy $canaryReports)
            $reportedSentinel = if ($null -ne $canaryReports.developerAttributes) {
                $canaryReports.developerAttributes.PSObject.Properties[$sentinel]
            } else { $null }
            $reportContainsAttribute = ($null -ne $canaryReports.developerAttributes) -and
                ($null -ne $reportedSentinel)
            if (-not $reportContainsAttribute -or -not $leakDetected -or -not $privacyRejected) {
                Throw-AppFailure "Q100Player privacy reader did not reject sentinel '$sentinel'."
            }
            [void]$cases.Add([ordered]@{
                attribute = $sentinel
                sentinelValue = 'CANARY_ONLY'
                reportContainsAttribute = $reportContainsAttribute
                developerLeakDetected = $leakDetected
                playerPrivacyRejected = $privacyRejected
            })
        } finally {
            if ($setAttempted) {
                $removeExpression = "(()=>{const d=document.documentElement,n=$jsName;if(!d)return false;d.removeAttribute(n);return !d.hasAttribute(n);})()"
                $removed = Evaluate-Cdp $Socket $removeExpression $Deadline
                $removedOk = $removed -is [bool] -and $removed
                [void]$cleanupCases.Add([ordered]@{ attribute=$sentinel; removed=$removedOk })
                if (-not $removedOk) {
                    Throw-VerifierInfrastructure "Q100Player privacy canary failed to remove sentinel '$sentinel'."
                }
            }
        }
    }
    $finalReports = Get-RouteReports $Socket $Deadline
    if (-not (Test-Q100PlayerPrivacy $finalReports)) {
        Throw-AppFailure 'Q100Player privacy did not return to PASS after sentinel cleanup.'
    }
    $canary = [ordered]@{
        protocol = 'tsj-q100-player-dom-privacy-canary-v1'
        status = 'PASS'
        sentinels = $sentinels
        cases = $cases.ToArray()
        finalPlayerPrivacy = 'PASS'
    }
    $cleanup = [ordered]@{
        status = 'PASS'
        removed = $sentinels
        cases = $cleanupCases.ToArray()
    }
    if (-not (Test-Q100PlayerPrivacyCanaryEvidence $canary $cleanup)) {
        Throw-VerifierInfrastructure 'Q100Player privacy canary returned malformed reader or cleanup evidence.'
    }
    return [pscustomobject]@{ privacyNegativeCanary=$canary; sentinelsCleanup=$cleanup; finalReports=$finalReports }
}

function Test-Q100PlayerArtifactMarkers([object]$Text) {
    if ($Text -isnot [string] -or [String]::IsNullOrEmpty($Text)) { return $false }
    $markers = @('data-tsj-q100-report','q100-electrical-reference-v1',
        'q100-electrical-reference-v2','q100-electrical-reference-v3',
        'RB100_CONTROL_PILOT','Rb100ElectricalPilot','Rb100PilotReference',
        'Rb100PilotFaults','Rb100Plan','tsjQ100Fault','tsjVerifyQ100','tsjQ100Variant',
        'tsjQ100Canary','tsjQ100Dt','Q100ElectricalPilotDeveloperVerifier',
        'data-tsj-e06-acceptance-canary','tsj-e06-acceptance-boundary-canary-v1',
        'e06-explicit-late-step-finished','e06-explicit-accepted-event')
    foreach ($marker in $markers) {
        if ($Text.IndexOf($marker, [StringComparison]::Ordinal) -ge 0) { return $false }
    }
    return $true
}

function Get-Q100PlayerArtifactProof([string]$Root) {
    $receiptPath = Join-Path $Root '.tools\gwt-2.7.0\production-build-identity.json'
    if (-not (Test-Path -LiteralPath $receiptPath -PathType Leaf)) {
        Throw-VerifierInfrastructure 'Q100Player requires a current Player production-build identity receipt.'
    }
    $currentObf = Get-VerifierProductionBuildIdentity -RepositoryRoot $Root `
        -Style 'OBF' -Variant 'Player'
    try {
        $receiptText = [IO.File]::ReadAllText($receiptPath)
        $receipt = $receiptText | ConvertFrom-Json -ErrorAction Stop
    } catch {
        Throw-VerifierInfrastructure ('Q100Player production-build receipt could not be read: ' +
            (Get-EarlyErrorText $_))
    }
    $expectedProperties = @('Protocol','Variant','Style','InputDigest','InputFileCount',
        'OutputDigest','OutputFileCount')
    $actualProperties = if ($null -ne $receipt -and $receipt -is [pscustomobject]) {
        @($receipt.PSObject.Properties | ForEach-Object { [string]$_.Name } | Sort-Object -CaseSensitive)
    } else { @() }
    $sortedExpectedProperties = @($expectedProperties | Sort-Object -CaseSensitive)
    if ($null -eq $receipt -or $receipt -is [array] -or $receipt -isnot [pscustomobject] -or
            $actualProperties.Count -ne $expectedProperties.Count -or
            ($actualProperties -join ',') -cne ($sortedExpectedProperties -join ',')) {
        Throw-VerifierInfrastructure 'Q100Player production-build receipt has an unsupported shape.'
    }
    foreach ($name in $expectedProperties) {
        if ($null -eq $receipt.PSObject.Properties[$name]) {
            Throw-VerifierInfrastructure "Q100Player production-build receipt omitted '$name'."
        }
    }
    if (-not (Test-VerifierStrictStringValue $receipt.Protocol) -or
            -not (Test-VerifierStrictStringValue $receipt.Variant) -or
            -not (Test-VerifierStrictStringValue $receipt.Style) -or
            $receipt.Protocol -cne 'troubleshootjs-production-build-identity-v1' -or
            $receipt.Variant -cne 'Player' -or
            $receipt.Style -cnotin @('OBF','PRETTY','DETAILED') -or
            -not (Test-VerifierStrictStringValue $receipt.InputDigest) -or
            [string]$receipt.InputDigest -notmatch '^[0-9a-f]{64}$' -or
            -not (Test-VerifierStrictStringValue $receipt.OutputDigest) -or
            [string]$receipt.OutputDigest -notmatch '^[0-9a-f]{64}$' -or
            -not (Test-VerifierStrictIntegralValue $receipt.InputFileCount 1L ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $receipt.OutputFileCount 1L ([long]::MaxValue))) {
        Throw-VerifierInfrastructure 'Q100Player production-build receipt did not identify a complete Player build.'
    }
    $current = if ([string]$receipt.Style -ceq 'OBF') { $currentObf } else {
        Get-VerifierProductionBuildIdentity -RepositoryRoot $Root `
            -Style ([string]$receipt.Style) -Variant 'Player'
    }
    if ($current.Protocol -cne [string]$receipt.Protocol -or
            $current.Variant -cne 'Player' -or $current.Style -cne [string]$receipt.Style -or
            $current.InputDigest -cne [string]$receipt.InputDigest -or
            $current.OutputDigest -cne [string]$receipt.OutputDigest -or
            [long]$current.InputFileCount -ne [long]$receipt.InputFileCount -or
            [long]$current.OutputFileCount -ne [long]$receipt.OutputFileCount) {
        Throw-VerifierInfrastructure 'Q100Player build receipt does not match current production inputs and war output.'
    }

    $moduleRoot = Join-Path $Root 'war\circuitjs1'
    if (-not (Test-Path -LiteralPath (Join-Path $moduleRoot 'circuitjs1.nocache.js') -PathType Leaf) -or
            @(Get-ChildItem -LiteralPath $moduleRoot -Filter '*.cache.js' -File -ErrorAction Stop).Count -eq 0) {
        Throw-VerifierInfrastructure 'Q100Player current war output is not a complete compiled GWT module.'
    }
    $javascriptFiles = @(Get-ChildItem -LiteralPath $moduleRoot -Recurse -File -Filter '*.js' -ErrorAction Stop)
    if ($javascriptFiles.Count -eq 0) {
        Throw-VerifierInfrastructure 'Q100Player current GWT module contains no JavaScript to scan.'
    }
    foreach ($file in $javascriptFiles) {
        if ([long]$file.Length -le 0) {
            Throw-VerifierInfrastructure 'Q100Player current GWT module contains an empty JavaScript file.'
        }
        $javascript = [IO.File]::ReadAllText($file.FullName)
        if (-not (Test-Q100PlayerArtifactMarkers $javascript)) {
            Throw-AppFailure 'Developer-only Q100 marker found in the compiled Player JavaScript module.'
        }
    }
    return [ordered]@{
        protocol = 'troubleshootjs-q100-player-artifact-proof-v1'
        buildIdentityProtocol = [string]$current.Protocol
        variant = [string]$current.Variant
        style = [string]$current.Style
        inputDigest = [string]$current.InputDigest
        inputFileCount = [long]$current.InputFileCount
        outputDigest = [string]$current.OutputDigest
        outputFileCount = [long]$current.OutputFileCount
        compiledJavaScriptFileCount = [long]$javascriptFiles.Count
        developerMarkerScan = 'PASS'
    }
}

function Test-RouteReady($Kind, $Reports, $Definition = $null) {
    $verification = Get-ExactReportText $Reports.verification
    switch ($Kind) {
        'a08' { return $verification -ceq 'PASS:a08' -and (Test-A08Report $Reports.a08) }
        'a08-forced' { return $verification -ceq 'FAIL:a08:a08-explicit-failure-canary' -and
            [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a08)) }
        'a08-debugoff' { return (Test-NormalWorkbenchReady $Reports.normalReady) -and
            $verification -notmatch '(?i)(?:^|:)a08(?:$|:)' -and
            [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a08)) }
        'diode-lifecycle' { return $verification -ceq 'PASS:diode' }
        'a07' { return $verification -ceq 'PASS:a07' -and (Test-A07Report $Reports.a07) }
        'a07-forced' { return $verification -ceq 'FAIL:a07:a07-explicit-failure-canary' -and
            [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a07)) }
        'a07-debugoff' { return (Test-NormalWorkbenchReady $Reports.normalReady) -and
            $verification -notmatch '(?i)(?:^|:)a07(?:$|:)' -and
            [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a07)) }
        'a06' { return $verification -ceq 'PASS:a06' -and (Test-A06Report $Reports.a06) }
        'a06-forced' { return $verification -ceq 'FAIL:a06:a06-explicit-failure-canary' -and
            [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a06)) }
        'a06-debugoff' { return (Test-NormalWorkbenchReady $Reports.normalReady) -and
            $verification -notmatch '(?i)(?:^|:)a06(?:$|:)' -and
            [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a06)) }
        'stored-energy' { return $verification -ceq 'PASS:stored-energy' }

        'a03' {
            $report = Get-ExactReportText $Reports.a03
            return $verification -ceq 'PASS:a03' -and
                (Test-JsonReport $report 'TSJ-A03-IDENTITY-1') -and
                $report -match 'A03_IDENTITY_VECTORS_BEGIN' -and
                $report -match 'A03_IDENTITY_VECTORS_END' -and
                $report -match 'assertions='
        }
        'a03-forced' {
            return $verification -ceq 'FAIL:a03:a03-explicit-failure-canary' -and
                [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a03))
        }
        'a03-debugoff' {
            return (Test-NormalWorkbenchReady $Reports.normalReady) -and
                $verification -notmatch '(?i)(?:^|:)a03(?:$|:)' -and
                [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a03))
        }
        'a04' {
            $report = Get-ExactReportText $Reports.a04
            return $verification -ceq 'PASS:a04' -and
                (Test-A04Report $report)
        }
        'a04-forced' {
            return $verification -ceq 'FAIL:a04:a04-explicit-failure-canary' -and
                [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a04))
        }
        'a04-debugoff' {
            return (Test-NormalWorkbenchReady $Reports.normalReady) -and
                $verification -notmatch '(?i)(?:^|:)a04(?:$|:)' -and
                [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a04))
        }
        'task41' {
            return $verification -ceq 'PASS:task41' -and
                (Test-Task41Report $Reports.task41)
        }
        'task46' {
            return $verification -ceq 'PASS:task46' -and
                (Get-ExactReportText $Reports.task46Parity) -match 'assertions=' -and
                -not [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.task46Descriptor)) -and
                -not [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.task46Snapshot)) -and
                -not [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.task46Replay))
        }
        'task47' {
            return $verification -ceq 'PASS:task47' -and
                (Test-JsonReport $Reports.task47 'TSJ-TASK47-ASSEMBLY-1')
        }
        'task48' {
            return $verification -ceq 'PASS:task48' -and
                (Test-ControlledReport $Reports.task48 'TSJ-TASK48-2')
        }
        'task49' {
            return $verification -ceq 'PASS:task49' -and
                (Test-ControlledReport $Reports.task49 'TSJ-TASK49-2')
        }
        'a02' {
            return $verification -ceq 'PASS:a02' -and
                (Test-JsonReport $Reports.a02 'TSJ-A02-2')
        }
        'q100-healthy' {
            return $Reports.q100Present -is [bool] -and $Reports.q100Present -and
                $verification -ceq 'PASS:q100' -and
                (Test-Q100Report $Reports.q100 $Definition.variant 'HEALTHY' $Definition.dt)
        }
        'q100-fault' {
            return $Reports.q100Present -is [bool] -and $Reports.q100Present -and
                $verification -ceq 'PASS:q100' -and
                (Test-Q100Report $Reports.q100 $Definition.variant $Definition.fault 0.00005)
        }
        { $_ -like 'q100-preflight-*' } {
            return $Reports.q100Present -is [bool] -and $Reports.q100Present -and
                $verification -ceq ('FAIL:q100:preflight:' + [string]$Definition.expectedError) -and
                (Test-Q100PreflightReport $Reports.q100 $Definition.expectedError)
        }
        'q100-after-step' {
            return $Reports.q100Present -is [bool] -and $Reports.q100Present -and
                $verification -ceq 'FAIL:q100' -and
                (Test-Q100InjectedFailureReport $Reports.q100 'after-step')
        }
        { $_ -in @('q100-cancel','q100-interaction-lock') } {
            if ($Reports.q100Present -isnot [bool] -or -not $Reports.q100Present -or
                    $verification -cne 'CANCELLED:q100') { return $false }
            if ($Kind -ceq 'q100-interaction-lock') {
                return (Test-Q100InteractionLockReport $Reports.q100)
            }
            return (Test-Q100InjectedFailureReport $Reports.q100 'cancel')
        }
        'q100-debugfalse' { return (Test-Q100PlayerPrivacy $Reports) }
        'q100-player' { return (Test-Q100PlayerPrivacy $Reports) }
        { $_ -in @('e06-late-callback','e06-event-failure') } {
            return $Reports.e06AcceptancePresent -is [bool] -and $Reports.e06AcceptancePresent -and
                $verification -ceq 'PASS:e06-acceptance-canary' -and
                (Test-E06AcceptanceCanaryReport $Reports.e06Acceptance $Kind)
        }
        default { return $false }
    }
}

function Get-Q100PlayerRouteDefinition() {
    return [pscustomobject]@{
        name='q100-player-debug-query'; kind='q100-player'
        query='tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=SHARED_HYSTERETIC_LINEAR&tsjQ100Fault=RSA_RESISTANCE&tsjQ100Dt=0.0001&tsjQ100Canary=after-step&running=true'
        reportNames=@()
    }
}

function Get-Q100RouteDefinitions([bool]$FaultMatrix) {
    $variants = @('SHARED_HYSTERETIC_LINEAR','SEPARATE_DIRECT_SWITCHING')
    $routes = New-Object Collections.Generic.List[object]
    if ($FaultMatrix) {
        foreach ($variant in $variants) {
            foreach ($faultId in (Get-Q100FaultIds)) {
                [void]$routes.Add([pscustomobject]@{
                    name = 'q100-fault-' + $variant + '-' + $faultId
                    kind = 'q100-fault'; variant = $variant; fault = $faultId; dt = 0.00005
                    query = 'tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=' +
                        $variant + '&tsjQ100Fault=' + $faultId + '&tsjQ100Dt=0.00005&running=true'
                    reportNames = @('q100')
                })
            }
        }
        return $routes.ToArray()
    }
    foreach ($variant in $variants) {
        foreach ($step in @(
            [pscustomobject]@{ value=0.0001; token='100us'; query='0.0001' },
            [pscustomobject]@{ value=0.00005; token='50us'; query='0.00005' }
        )) {
            [void]$routes.Add([pscustomobject]@{
                name = 'q100-healthy-' + $variant + '-' + $step.token
                kind = 'q100-healthy'; variant = $variant; fault = 'HEALTHY'; dt = $step.value
                query = 'tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=' +
                    $variant + '&tsjQ100Dt=' + $step.query + '&running=true'
                reportNames = @('q100')
            })
        }
    }
    [void]$routes.Add([pscustomobject]@{
        name='q100-preflight-invalidvariant'; kind='q100-preflight-variant'
        expectedError='Unknown RB100 structural variant'
        query='tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=INVALID&tsjQ100Dt=0.0001&running=true'
        reportNames=@('q100')
    })
    [void]$routes.Add([pscustomobject]@{
        name='q100-preflight-invalidfault'; kind='q100-preflight-fault'
        expectedError='Unknown RB100 electrical-reference fault'
        query='tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=SHARED_HYSTERETIC_LINEAR&tsjQ100Fault=INVALID_FAULT&tsjQ100Dt=0.0001&running=true'
        reportNames=@('q100')
    })
    [void]$routes.Add([pscustomobject]@{
        name='q100-preflight-invaliddt'; kind='q100-preflight-dt'
        expectedError='RB100 reference: unsupported reference timestep'
        query='tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=SHARED_HYSTERETIC_LINEAR&tsjQ100Dt=0.0002&running=true'
        reportNames=@('q100')
    })
    # These routes exercise explicit producer canaries, not external user
    # cancellation or player-input evidence.
    foreach ($canary in @('after-step','cancel','interaction-lock')) {
        [void]$routes.Add([pscustomobject]@{
            name='q100-' + $canary; kind='q100-' + $canary
            query='tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=SHARED_HYSTERETIC_LINEAR&tsjQ100Dt=0.0001&tsjQ100Canary=' + $canary + '&running=true'
            reportNames=@('q100')
        })
    }
    [void]$routes.Add([pscustomobject]@{
        name='q100-debugfalse'; kind='q100-debugfalse'
        query='tsjChallenge=led&seed=3&tsjDebug=false&tsjVerifyQ100=true&running=true'
        reportNames=@('q100')
    })
    return $routes.ToArray()
}

function Test-E06AcceptanceCanaryReport([object]$Text, [string]$Canary) {
    try {
        if ($Canary -cnotin @('e06-late-callback','e06-event-failure')) { return $false }
        $report = ConvertFrom-Q100ReportText $Text
        if ($null -eq $report -or $report -is [array] -or
                $report.protocol -cne 'tsj-e06-acceptance-boundary-canary-v1' -or
                $report.status -cne 'PASS_E06_ACCEPTANCE_BOUNDARY_CANARY' -or
                $report.epoch -cne 'tsj-generation-dependencies-v26' -or
                $report.canary -cne $Canary -or $report.family -cne 'RB56_CONTROL' -or
                $report.seed -isnot [string] -or $report.seed -cne '-8360266000248569150' -or
                $report.cleanup -cne 'PASS' -or $null -ne $report.cleanupError -or
                $null -ne $report.error) { return $false }
        foreach ($field in @('quickPlaySelectionVerified','fixedStepVerified',
                'exactCountVerified','expectedFailureObserved')) {
            if ($report.$field -isnot [bool] -or -not $report.$field) { return $false }
        }
        if (-not (Test-Q100Number $report.operationMs 0 90000) -or
                -not (Test-Q100Number $report.cleanupMs 0 120000) -or
                -not (Test-Q100Number $report.maxUnitMs 0 5000) -or
                -not (Test-Q100Number $report.operationLimitMs 90000 90000) -or
                -not (Test-Q100Number $report.cleanupLimitMs 120000 120000) -or
                -not (Test-Q100Number $report.maxUnitLimitMs 5000 5000) -or
                -not (Test-Q100Number $report.interTrialLimitMs 500 500) -or
                -not (Test-Q100Number $report.maxBatchLimitSteps 64 64) -or
                -not (Test-Q100Number $report.maxBatchSteps 64 64) -or
                -not (Test-Q100Number $report.maxStepTimeError 0 0.00000001) -or
                -not (Test-VerifierStrictIntegralValue $report.work 1L 640L) -or
                -not (Test-VerifierStrictIntegralValue $report.acceptedSteps 1200L 8000L) -or
                [long]$report.acceptedSteps % 64L -ne 0L -or
                [long]$report.work -ne (3L + [long]$report.acceptedSteps / 64L)) { return $false }
        foreach ($field in @('timeStepAtRejection','minTimeStepAtRejection','maxTimeStepAtRejection')) {
            if (-not (Test-Q100Number $report.$field 0.000025 0.000025)) { return $false }
        }
        if ($report.adjustTimeStepAtRejection -isnot [bool] -or
                $report.adjustTimeStepAtRejection) { return $false }
        $late = $Canary -ceq 'e06-late-callback'
        $message = if ($late) { 'e06-explicit-late-step-finished' } else { 'e06-explicit-accepted-event' }
        $phase = if ($late) { 'STEP_FINISHED_AFTER_E06_STAGE' } else { 'ACCEPTED_EVENT_AFTER_E06_STAGE' }
        if ($report.expectedFailureType -cne 'java.lang.IllegalStateException' -or
                $report.observedFailureType -cne 'java.lang.IllegalStateException' -or
                $report.expectedFailureMessage -cne $message -or
                $report.observedFailureMessage -cne $message -or
                $report.failurePhase -cne $phase) { return $false }
        $predecessor = $report.predecessor
        $candidate = $report.candidate
        if ($null -eq $predecessor -or $predecessor -is [array] -or
                $null -eq $candidate -or $candidate -is [array] -or
                -not (Test-VerifierStrictIntegralValue $predecessor.e06CountBefore 1L 1L) -or
                -not (Test-VerifierStrictIntegralValue $predecessor.e06CountAfter 1L 1L) -or
                -not (Test-VerifierStrictIntegralValue $candidate.e06Count 1L 1L)) { return $false }
        foreach ($field in @('graphIdentityStable','ownerIdentityStable','exactDumpRestored',
                'quickPlaySessionIdentityStable','quickPlaySelectionIdentityStable',
                'modelIdentityStable','acceptedSnapshotUnchangedBefore','acceptedSnapshotUnchangedAfter',
                'acceptedPredictorAvailableBefore','acceptedPredictorAvailableAfter')) {
            if ($predecessor.$field -isnot [bool] -or -not $predecessor.$field) { return $false }
        }
        foreach ($field in @('acceptedPredictorAvailableBefore','privateCandidatePendingObserved',
                'privateCandidatePendingAfterRejection','committedSnapshotUnchanged',
                'simulatedSecondsAdvanced','candidateOutOfLiveGraph','privateAuthorityClosed')) {
            if ($candidate.$field -isnot [bool] -or -not $candidate.$field) { return $false }
        }
        if ($candidate.acceptedStepAtRejectedTime -isnot [bool] -or
                $candidate.acceptedStepAtRejectedTime -or
                $candidate.postStepCallbackObserved -isnot [bool] -or
                $candidate.postStepCallbackObserved -ne $late -or
                $candidate.eventCallbackObserved -isnot [bool] -or
                $candidate.eventCallbackObserved -eq $late -or
                $candidate.eventCandidateObserved -isnot [bool] -or
                $candidate.eventCandidateObserved -eq $late -or
                -not (Test-VerifierStrictIntegralValue $candidate.rejectedAttemptSteps 1L 1L) -or
                -not (Test-VerifierStrictIntegralValue $candidate.rejectedAttemptTimeStepCount 1L 1L) -or
                -not (Test-Q100Number $candidate.simulatedSecondsBefore 0.03 0.2) -or
                -not (Test-Q100Number $candidate.simulatedSecondsAfter 0.030025 0.200025)) { return $false }
        return [Math]::Abs([double]$candidate.simulatedSecondsBefore -
                [long]$report.acceptedSteps * 0.000025) -le 0.00000001 -and
            [Math]::Abs(([double]$candidate.simulatedSecondsAfter -
                [double]$candidate.simulatedSecondsBefore) - 0.000025) -le 0.00000001
    } catch { return $false }
}

function Get-E06AcceptanceRouteDefinitions() {
    $routes = New-Object Collections.Generic.List[object]
    foreach ($canary in @('e06-late-callback','e06-event-failure')) {
        [void]$routes.Add([pscustomobject]@{
            name=$canary; kind=$canary
            query='tsjQuickPlay=true&tsjQuickPlayTestFamily=11&tsjQuickPlayTestSeed=-8360266000248569150&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Canary=' + $canary + '&running=true'
            reportNames=@('e06Acceptance')
        })
    }
    foreach ($route in @(Get-Q100RouteDefinitions $false | Where-Object kind -CEQ 'q100-healthy')) {
        [void]$routes.Add($route)
    }
    foreach ($route in @(Get-Q100RouteDefinitions $true | Where-Object {
            $_.fault -cin @('RENAC_RESISTANCE','CIN33_SHUNT') })) {
        [void]$routes.Add($route)
    }
    return $routes.ToArray()
}

function Test-E06AcceptanceMatrix([object[]]$Routes) {
    if ($null -eq $Routes -or $Routes.Count -ne 10 -or
            -not (Test-Q100HealthyMatrix $Routes)) { return $false }
    $canaries = @('e06-late-callback','e06-event-failure')
    for ($index = 0; $index -lt 2; $index++) {
        $route = $Routes[$index]
        $reports = Get-Q100RecordField $route 'reports'
        $canaryText = Get-Q100RecordField $reports 'e06Acceptance'
        $validCanary = Test-E06AcceptanceCanaryReport $canaryText $canaries[$index]
        if ((Get-Q100RecordField $route 'name') -cne $canaries[$index] -or
                (Get-Q100RecordField $route 'kind') -cne $canaries[$index] -or
                (Get-Q100RecordField $route 'status') -cne 'PASS' -or
                (Get-Q100RecordField $route 'verification') -cne 'PASS:e06-acceptance-canary' -or
                (Get-Q100RecordField $route 'e06AcceptanceReportAttributePresent') -isnot [bool] -or
                -not (Get-Q100RecordField $route 'e06AcceptanceReportAttributePresent') -or
                -not $validCanary) { return $false }
    }
    # Independent, predeclared finite population. This is not the 2-by-31 gate.
    $expected = @(
        @('SHARED_HYSTERETIC_LINEAR','HEALTHY',0.0001,'100us'),
        @('SHARED_HYSTERETIC_LINEAR','HEALTHY',0.00005,'50us'),
        @('SEPARATE_DIRECT_SWITCHING','HEALTHY',0.0001,'100us'),
        @('SEPARATE_DIRECT_SWITCHING','HEALTHY',0.00005,'50us'),
        @('SHARED_HYSTERETIC_LINEAR','RENAC_RESISTANCE',0.00005,''),
        @('SHARED_HYSTERETIC_LINEAR','CIN33_SHUNT',0.00005,''),
        @('SEPARATE_DIRECT_SWITCHING','RENAC_RESISTANCE',0.00005,''),
        @('SEPARATE_DIRECT_SWITCHING','CIN33_SHUNT',0.00005,'')
    )
    for ($index = 0; $index -lt $expected.Count; $index++) {
        $item = $expected[$index]
        $route = $Routes[$index + 2]
        $reports = Get-Q100RecordField $route 'reports'
        $kind = if ($item[1] -ceq 'HEALTHY') { 'q100-healthy' } else { 'q100-fault' }
        $name = if ($item[1] -ceq 'HEALTHY') {
            'q100-healthy-' + $item[0] + '-' + $item[3]
        } else { 'q100-fault-' + $item[0] + '-' + $item[1] }
        $referenceText = Get-Q100RecordField $reports 'q100'
        $validReference = Test-Q100Report $referenceText $item[0] $item[1] ([double]$item[2])
        if ((Get-Q100RecordField $route 'name') -cne $name -or
                (Get-Q100RecordField $route 'kind') -cne $kind -or
                (Get-Q100RecordField $route 'status') -cne 'PASS' -or
                (Get-Q100RecordField $route 'verification') -cne 'PASS:q100' -or
                (Get-Q100RecordField $route 'q100ReportAttributePresent') -isnot [bool] -or
                -not (Get-Q100RecordField $route 'q100ReportAttributePresent') -or
                -not $validReference) { return $false }
    }
    return $true
}

function Get-RouteDefinitions([string]$SelectedGate, [bool]$SmokeOnly) {
    if ($SmokeOnly) {
        return @([pscustomobject]@{ name = 'transport-smoke'; kind = 'smoke';
            query = 'tsjDebug=false'; reportNames = @() })
    }
    if ($SelectedGate -ieq 'Q100Player') { return @(Get-Q100PlayerRouteDefinition) }
    if ($SelectedGate -ieq 'Q100') { return @(Get-Q100RouteDefinitions $false) }
    if ($SelectedGate -ieq 'Q100Faults') { return @(Get-Q100RouteDefinitions $true) }
    if ($SelectedGate -ieq 'E06State') { return @(Get-E06AcceptanceRouteDefinitions) }
    $all = @(
        [pscustomobject]@{ name = 'a03'; kind = 'a03';
            query = 'tsjChallenge=led&seed=3&tsjVerifyA03=true&tsjDebug=true&running=true';
            reportNames = @('a03') },
        [pscustomobject]@{ name = 'a03-forcedfailure'; kind = 'a03-forced';
            query = 'tsjChallenge=led&seed=3&tsjVerifyA03=true&tsjA03Fail=true&tsjDebug=true&running=true';
            reportNames = @('a03') },
        [pscustomobject]@{ name = 'a03-debugoff'; kind = 'a03-debugoff';
            query = 'tsjChallenge=led&seed=3&tsjVerifyA03=true&tsjA03Fail=true&running=true';
            reportNames = @('a03') },
        [pscustomobject]@{ name = 'task41'; kind = 'task41';
            query = 'tsjChallenge=npn&seed=0&tsjVerifyTask41=true&running=true';
            reportNames = @('task41') },
        [pscustomobject]@{ name = 'task46'; kind = 'task46';
            query = 'tsjChallenge=led&seed=3&tsjVerifyTask46=true&tsjDebug=true&running=true';
            reportNames = @('task46Parity','task46Descriptor','task46Snapshot','task46Replay') },
        [pscustomobject]@{ name = 'task47'; kind = 'task47';
            query = 'tsjChallenge=led&seed=3&tsjVerifyTask47=true&tsjTask47Seed=3&tsjDebug=true&running=true';
            reportNames = @('task47') },
        [pscustomobject]@{ name = 'task48'; kind = 'task48';
            query = 'tsjChallenge=led&seed=3&tsjVerifyTask48=true&tsjTask48Seed=3&tsjDebug=true&running=true';
            reportNames = @('task48') },
        [pscustomobject]@{ name = 'task49'; kind = 'task49';
            query = 'tsjChallenge=led&seed=3&tsjVerifyTask49=true&tsjTask49Seed=3&tsjDebug=true&running=true';
            reportNames = @('task49') },
        [pscustomobject]@{ name = 'a02'; kind = 'a02';
            query = 'tsjChallenge=led&seed=3&tsjVerifyA02=true&tsjDebug=true&running=true';
            reportNames = @('a02') }
    )
    # Current is the one maintained browser corpus.  It selects the affected
    # identity/seed, assembly, geometry and physical routes and adds the A04
    # forced-failure/debug-off canaries.
    if ($SelectedGate -ceq 'Current' -or $SelectedGate -ceq 'A05') {
        $currentNames = if ($SelectedGate -ceq 'A05') {
            @('a03', 'task46', 'task49')
        } else { @('a03', 'task46', 'task47', 'task48', 'task49', 'a02') }
        $current = New-Object Collections.Generic.List[object]
        foreach ($currentName in $currentNames) {
            $matches = @($all | Where-Object { $_.name -ceq $currentName })
            if ($matches.Count -ne 1) {
                throw ("Current browser corpus route '$currentName' was not uniquely defined.")
            }
            [void]$current.Add($matches[0])
        }
        [void]$current.Add([pscustomobject]@{ name = 'a04'; kind = 'a04';
            query = 'tsjChallenge=led&seed=3&tsjVerifyA04=true&tsjDebug=true&running=true';
            reportNames = @('a04') })
        [void]$current.Add([pscustomobject]@{ name = 'a04-forcedfailure'; kind = 'a04-forced';
            query = 'tsjChallenge=led&seed=3&tsjVerifyA04=true&tsjA04Fail=true&tsjDebug=true&running=true';
            reportNames = @('a04') })
        [void]$current.Add([pscustomobject]@{ name = 'a04-debugoff'; kind = 'a04-debugoff';
            query = 'tsjChallenge=led&seed=3&tsjVerifyA04=true&tsjA04Fail=true&running=true';
            reportNames = @('a04') })
        return $current.ToArray()
    }
    # A04 is explicitly selected for its positive and negative physical paths.
    if ($SelectedGate -ceq 'A04') {
        return @(
            [pscustomobject]@{ name = 'a04'; kind = 'a04';
                query = 'tsjChallenge=led&seed=3&tsjVerifyA04=true&tsjDebug=true&running=true';
                reportNames = @('a04') },
            [pscustomobject]@{ name = 'a04-forcedfailure'; kind = 'a04-forced';
                query = 'tsjChallenge=led&seed=3&tsjVerifyA04=true&tsjA04Fail=true&tsjDebug=true&running=true';
                reportNames = @('a04') },
            [pscustomobject]@{ name = 'a04-debugoff'; kind = 'a04-debugoff';
                query = 'tsjChallenge=led&seed=3&tsjVerifyA04=true&tsjA04Fail=true&running=true';
                reportNames = @('a04') }
        )
    }
    if ($SelectedGate -ceq 'A08') {
        return @(
            [pscustomobject]@{name='a08';kind='a08';
                query='tsjChallenge=led&seed=3&tsjVerifyA08=true&tsjDebug=true&running=true';reportNames=@('a08')},
            [pscustomobject]@{name='a08-forcedfailure';kind='a08-forced';
                query='tsjChallenge=led&seed=3&tsjVerifyA08=true&tsjA08Fail=true&tsjDebug=true&running=true';reportNames=@('a08')},
            [pscustomobject]@{name='a08-debugoff';kind='a08-debugoff';
                query='tsjChallenge=led&seed=3&tsjVerifyA08=true&tsjA08Fail=true&running=true';reportNames=@('a08')},
            [pscustomobject]@{name='a08-diode-open';kind='diode-lifecycle';
                query='tsjChallenge=diode&seed=3&tsjVerifyDiode=true&running=true';reportNames=@()},
            [pscustomobject]@{name='a08-diode-short';kind='diode-lifecycle';
                query='tsjChallenge=diode&seed=0&tsjVerifyDiode=true&tsjDiodeShort=true&running=true';reportNames=@()}
        ) + @(Get-RouteDefinitions 'A07' $false)
    }
    if ($SelectedGate -ceq 'A07') {
        return @(
            [pscustomobject]@{name='a07';kind='a07';
                query='tsjChallenge=led&seed=3&tsjVerifyA07=true&tsjDebug=true&running=true';reportNames=@('a07')},
            [pscustomobject]@{name='a07-forcedfailure';kind='a07-forced';
                query='tsjChallenge=led&seed=3&tsjVerifyA07=true&tsjA07Fail=true&tsjDebug=true&running=true';reportNames=@('a07')},
            [pscustomobject]@{name='a07-debugoff';kind='a07-debugoff';
                query='tsjChallenge=led&seed=3&tsjVerifyA07=true&tsjA07Fail=true&running=true';reportNames=@('a07')}
        ) + @(Get-RouteDefinitions 'A06' $false)
    }
    if ($SelectedGate -ceq 'A06') {
        return @(
            [pscustomobject]@{ name='a06'; kind='a06';
                query='tsjChallenge=led&seed=3&tsjVerifyA06=true&tsjDebug=true&running=true'; reportNames=@('a06') },
            [pscustomobject]@{ name='a06-forcedfailure'; kind='a06-forced';
                query='tsjChallenge=led&seed=3&tsjVerifyA06=true&tsjA06Fail=true&tsjDebug=true&running=true'; reportNames=@('a06') },
            [pscustomobject]@{ name='a06-debugoff'; kind='a06-debugoff';
                query='tsjChallenge=led&seed=3&tsjVerifyA06=true&tsjA06Fail=true&running=true'; reportNames=@('a06') },
            @($all | Where-Object name -CEQ 'a03')[0],
            @($all | Where-Object name -CEQ 'task49')[0],
            [pscustomobject]@{ name='stored-energy'; kind='stored-energy';
                query='tsjChallenge=rc&seed=3&tsjVerifyStoredEnergy=true&running=true'; reportNames=@() }
        )
    }
    if ($SelectedGate -ceq 'A03') { return $all[0..2] }
    $name = $SelectedGate.ToLowerInvariant()
    return @($all | Where-Object { $_.name -ceq $name })
}

function Write-RunArtifact($Context, [string]$Name, $Value) {
    $path = Join-Path $Context.EvidenceDirectory $Name
    $json = $Value | ConvertTo-Json -Depth 20
    [IO.File]::WriteAllText($path, $json, [Text.UTF8Encoding]::new($false))
    Register-VerifierEvidenceArtifact $Context $path
    return [ordered]@{ name = $Name; sha256 = (Get-VerifierFileSha256 $path) }
}

function Write-ReportFile([string]$Path, $Report) {
    $parent = Split-Path -Parent $Path
    if (-not [String]::IsNullOrWhiteSpace($parent) -and
            -not (Test-Path -LiteralPath $parent -PathType Container)) {
        New-Item -ItemType Directory -Path $parent -Force -ErrorAction Stop | Out-Null
    }
    $json = $Report | ConvertTo-Json -Depth 20
    [IO.File]::WriteAllText($Path, $json, [Text.UTF8Encoding]::new($false))
}

try {
    Set-Operation 'setup' 'resolve-worktree'
    $effectiveTimeoutSeconds = $TimeoutSeconds
    if ($script:IsQ100Gate) {
        $effectiveTimeoutSeconds = [int][Math]::Min(90, $TimeoutSeconds)
    }
    $root = if ([String]::IsNullOrWhiteSpace($WorktreeRoot)) {
        [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    } else { [IO.Path]::GetFullPath($WorktreeRoot) }
    if (-not (Test-Path -LiteralPath $root -PathType Container)) {
        Throw-VerifierInfrastructure "Worktree root does not exist: $root"
    }
    # WorktreeRoot selects the product tree being served. Isolation remains
    # verifier-owned so a historical baseline cannot downgrade process proof.
    $modulePath = Join-Path $PSScriptRoot 'VerifierIsolation.psm1'
    $previewPath = Join-Path $root 'scripts\preview.ps1'
    Import-Module $modulePath -Force -ErrorAction Stop
    if ($Gate -ieq 'Q100Player' -and $Smoke) {
        Throw-VerifierInfrastructure 'Q100Player requires the production Player artifact/privacy route and cannot use transport-only smoke.'
    }
    if ($Gate -ieq 'E06State' -and $Smoke) {
        Throw-VerifierInfrastructure 'E06State requires its actual acceptance-boundary and reference routes; transport-only smoke cannot qualify it.'
    }
    if ($script:IsQ100Gate) {
        if ($TimeoutSeconds -le 0) { Throw-VerifierInfrastructure 'Q100 route timeout must be positive.' }
        if ([DateTime]::UtcNow -ge $script:Q100MatrixDeadlineUtc) {
            Throw-VerifierInfrastructure 'Q100 matrix operation deadline expired before setup.'
        }
    }
    if ($Gate -ieq 'Q100Player' -and -not $Smoke) {
        Set-Operation 'setup' 'verify-current-player-build-artifact'
        $script:Q100PlayerArtifactProof = Get-Q100PlayerArtifactProof $root
    }
    Set-Operation 'setup' 'New-VerifierRunContext'
    $context = New-VerifierRunContext $root ''
    Set-Operation 'setup' 'Resolve-VerifierBrowserPath'
    $resolvedBrowser = Resolve-VerifierBrowserPath $BrowserPath
    Set-Operation 'preview' 'Start-VerifierOwnedPreview'
    $baseUrl = [string](Start-VerifierOwnedPreview $context $previewPath $effectiveTimeoutSeconds ([bool]$ForceTcpListener))
    Set-Operation 'preview' 'read-preview-identity'
    $previewIdentity = Get-PreviewIdentity $baseUrl $context $root $previewPath
    Set-Operation 'setup' 'define-routes'
    $definitions = @(Get-RouteDefinitions $Gate ([bool]$Smoke))
    $expectedRouteCount = if ($Smoke) { 1 } else {
        switch ($Gate) {
            'Current' { 9 }
            'A05' { 6 }
            'A06' { 6 }
            'A08' { 14 }
            'A07' { 9 }
            'A03' { 3 }
            'A04' { 3 }
            'Q100' { 11 }
            'Q100Faults' { 62 }
            'Q100Player' { 1 }
            'E06State' { 10 }
            default { 1 }
        }
    }
    if ($definitions.Count -ne $expectedRouteCount) {
        Throw-VerifierInfrastructure ("Gate '$Gate' registered $($definitions.Count) route(s); " +
            "expected $expectedRouteCount.")
    }
    $firstUrl = $baseUrl + '/circuitjs.html?' + [string]$definitions[0].query
    Set-Operation 'browser' 'New-VerifierBrowserSession'
    $session = New-VerifierBrowserSession $context 'a03-browser-report' $firstUrl `
        $resolvedBrowser $effectiveTimeoutSeconds ([bool]$Headed)
    Set-Operation 'browser' 'enable-cdp-domains' $session.Deadline
    [void](Invoke-Cdp $session.Socket 'Page.enable' @{} $session.Deadline)
    [void](Invoke-Cdp $session.Socket 'Runtime.enable' @{} $session.Deadline)

    foreach ($definition in $definitions) {
        if ($script:IsQ100Gate -and [DateTime]::UtcNow -ge $script:Q100MatrixDeadlineUtc) {
            Throw-VerifierInfrastructure 'Q100 matrix operation exceeded its 6000-second budget before the next route.'
        }
        $routeTimeoutSeconds = $effectiveTimeoutSeconds
        $routeDeadline = [DateTime]::UtcNow.AddSeconds($routeTimeoutSeconds)
        if ($script:IsQ100Gate -and $script:Q100MatrixDeadlineUtc -lt $routeDeadline) {
            $routeDeadline = $script:Q100MatrixDeadlineUtc
        }
        Set-Operation 'browser' ('route-' + [string]$definition.name) $routeDeadline
        if ($script:RouteTimingEnabled) {
            $script:RouteTimingOrdinal++
            Start-RouteTiming $definition $script:RouteTimingOrdinal $routeDeadline
            Add-RouteHeapSample 'before-navigation' $session.Socket $routeDeadline
            Set-RouteTimingPhase 'navigation-send'
        }
        $routeUrl = $baseUrl + '/circuitjs.html?' + [string]$definition.query
        $navigationUrl = Navigate-Cdp $session.Socket $routeUrl $context.RunId `
            $session.RouteId $routeDeadline
        if ($definition.kind -eq 'smoke') {
            if ($script:RouteTimingEnabled) { Set-RouteTimingPhase 'smoke-validation' }
            $transportSmoke = Evaluate-Cdp $session.Socket `
                '({ready:document.readyState,url:location.href})' $routeDeadline
            if ($null -eq $transportSmoke -or $transportSmoke.ready -cne 'complete' -or
                    [string]$transportSmoke.url -notmatch 'tsjVerifierNavigation=') {
                Throw-VerifierInfrastructure 'Transport smoke did not return the owned completed document.'
            }
            $result = [ordered]@{ name = $definition.name; kind = 'transport';
                status = 'PASS'; navigationUrl = (Get-SafeText $navigationUrl 2000);
                observed = [ordered]@{ ready = [string]$transportSmoke.ready;
                    url = (Get-SafeText $transportSmoke.url 2000) }; events = $script:CdpEvents.ToArray() }
            if ($script:RouteTimingEnabled) {
                Set-RouteTimingPhase 'after-route-boundary-sample'
                Add-RouteHeapSample 'after-route' $session.Socket $routeDeadline
            }
            [void]$routeResults.Add($result)
            if ($script:RouteTimingEnabled) { Complete-RouteTiming 'COMPLETE' }
            continue
        }
        $ticketEntryAudit = $null
        if (Test-RouteRequiresTicketEntry ([string]$definition.kind)) {
            if ($script:RouteTimingEnabled) { Set-RouteTimingPhase 'ticket-entry' }
            $ticketEntryAudit = Invoke-OrdinaryTicketEntry $session.Socket $routeDeadline
        }
        $reports = $null
        do {
            if ($script:RouteTimingEnabled) { Set-RouteTimingPhase 'report-poll' }
            $reports = Get-RouteReports $session.Socket $routeDeadline
            if (Test-RouteReady $definition.kind $reports $definition) { break }
            if ($definition.kind -ceq 'q100-player' -and (Test-Q100PlayerDeveloperLeak $reports)) {
                Throw-AppFailure 'Compiled Player page exposed a developer verification or report attribute.'
            }
            if ($definition.kind -ceq 'q100-debugfalse' -and (Test-Q100PlayerDeveloperLeak $reports)) {
                Throw-AppFailure 'Compiled debug-false page exposed a developer verification or report attribute.'
            }
            $currentVerification = Get-ExactReportText $reports.verification
            $q100ExpectedTerminal = $definition.kind -like 'q100-preflight-*' -or
                $definition.kind -in @('q100-after-step','q100-cancel','q100-interaction-lock')
            $q100ExpectedVerification = switch ([string]$definition.kind) {
                { $_ -like 'q100-preflight-*' } {
                    'FAIL:q100:preflight:' + [string]$definition.expectedError
                }
                'q100-after-step' { 'FAIL:q100' }
                { $_ -in @('q100-cancel','q100-interaction-lock') } { 'CANCELLED:q100' }
                default { '' }
            }
            if ($script:IsQ100Gate -and
                    ($currentVerification -like 'FAIL:*' -or $currentVerification -like 'CANCELLED:*') -and
                    $currentVerification -cne $q100ExpectedVerification) {
                Throw-AppFailure ("$($definition.name) reported an unexpected Q100 terminal result: " +
                    $currentVerification)
            }
            if ($currentVerification -like 'FAIL:*' -and
                    $definition.kind -notin @('a03-forced', 'a04-forced', 'a06-forced', 'a07-forced', 'a08-forced') -and
                    -not $q100ExpectedTerminal) {
                Throw-AppFailure ("$($definition.name) reported " +
                    $currentVerification)
            }
            Start-Sleep -Milliseconds 250
        } while ([DateTime]::UtcNow -lt $routeDeadline)
        if (-not (Test-RouteReady $definition.kind $reports $definition)) {
            Throw-VerifierInfrastructure ("$($definition.name) did not provide its required compiled report before the bounded deadline.")
        }
        $privacyNegativeCanaryEvidence = $null
        if ($definition.kind -in @('q100-player','q100-debugfalse')) {
            if ($script:RouteTimingEnabled) { Set-RouteTimingPhase 'privacy-negative-canary' }
            $privacyNegativeCanaryEvidence = Invoke-Q100PlayerPrivacyNegativeCanary `
                $session.Socket $routeDeadline
            $reports = $privacyNegativeCanaryEvidence.finalReports
            if (-not (Test-RouteReady $definition.kind $reports $definition)) {
                if ($definition.kind -ceq 'q100-player') {
                    Throw-AppFailure 'Q100Player ordinary privacy/readiness changed after the negative DOM canary cleanup.'
                }
                Throw-AppFailure 'Q100 debug-false ordinary privacy/readiness changed after the negative DOM canary cleanup.'
            }
        }
        $record = [ordered]@{ name = $definition.name; kind = $definition.kind;
            status = 'PASS'; navigationUrl = (Get-SafeText $navigationUrl 2000);
            verification = (Get-ExactReportText $reports.verification);
            readiness = [ordered]@{ workbenchNavigationReady = [bool]$reports.normalReady.workbenchNavigationReady;
                programReady = [bool]$reports.normalReady.programReady;
                retestCustomerReady = [bool]$reports.normalReady.retestCustomerReady;
                boardPowerReady = [bool]$reports.normalReady.boardPowerReady;
                boardPowerLabel = [string]$reports.normalReady.boardPowerLabel };
            reports = [ordered]@{}; events = @($script:CdpEvents | Select-Object -Last 40) }
        if ($null -ne $ticketEntryAudit) { $record.ordinaryTicketEntry = $ticketEntryAudit }
        if ($null -ne $privacyNegativeCanaryEvidence) {
            $record.privacyNegativeCanary = $privacyNegativeCanaryEvidence.privacyNegativeCanary
            $record.sentinelsCleanup = $privacyNegativeCanaryEvidence.sentinelsCleanup
        }
        if ($script:IsQ100Gate) {
            $record.q100ReportAttributePresent = [bool]$reports.q100Present
            if ($Gate -ieq 'E06State') {
                $record.e06AcceptanceReportAttributePresent = [bool]$reports.e06AcceptancePresent
            }
        }
        if ($definition.kind -in @('q100-player','q100-debugfalse')) {
            $record.developerAttributes = $reports.developerAttributes
        }
        foreach ($reportName in @($definition.reportNames)) {
            $property = $reports.PSObject.Properties[$reportName]
            if ($null -eq $property) {
                Throw-VerifierInfrastructure "Route '$($definition.name)' omitted report '$reportName'."
            }
            $record.reports[$reportName] = Get-ExactReportText $property.Value
        }
        if ($script:RouteTimingEnabled) { Set-RouteTimingPhase 'route-artifact-write' }
        $artifactName = 'route-' + ($definition.name -replace '[^A-Za-z0-9._-]', '-') + '.json'
        $artifact = Write-RunArtifact $context $artifactName $record
        $record.artifactSha256 = $artifact.sha256
        [void]$routeResults.Add($record)
        if ($script:RouteTimingEnabled) {
            Set-RouteTimingPhase 'after-route-boundary-sample'
            Add-RouteHeapSample 'after-route' $session.Socket $routeDeadline
            Complete-RouteTiming 'COMPLETE'
        }
    }
    if ($Gate -ieq 'Q100' -and -not $Smoke -and
            -not (Test-Q100HealthyMatrix $routeResults.ToArray())) {
        Throw-VerifierInfrastructure 'Q100 healthy variant/timestep matrix did not satisfy the independent reader.'
    }
    if ($Gate -ieq 'Q100Faults' -and -not $Smoke -and
            -not (Test-Q100FaultMatrix $routeResults.ToArray())) {
        Throw-VerifierInfrastructure 'Q100 fault matrix did not contain the exact independent 2x31 coverage.'
    }
    if ($Gate -ieq 'E06State' -and -not $Smoke -and
            -not (Test-E06AcceptanceMatrix $routeResults.ToArray())) {
        Throw-VerifierInfrastructure 'E06 acceptance canaries and finite eight-case regression did not satisfy the independent reader.'
    }
    if ($script:IsQ100Gate -and [DateTime]::UtcNow -gt $script:Q100MatrixDeadlineUtc) {
        Throw-VerifierInfrastructure 'Q100 matrix operation exceeded its 6000-second budget.'
    }
    $requestedExitCode = 0
} catch {
    $failureErrorRecord = $_
    $exception = if ($failureErrorRecord.PSObject.Properties['Exception']) {
        $failureErrorRecord.Exception
    } else { $failureErrorRecord }
    $requestedExitCode = if ($exception.Data -and $exception.Data.Contains('VerifierExitCode')) {
        [int]$exception.Data['VerifierExitCode']
    } else { 2 }
    $failureKind = if ($requestedExitCode -eq 1) { 'application' } else { 'infrastructure' }
    if ($script:RouteTimingEnabled -and $null -ne $script:CurrentRouteTiming) {
        try {
            Complete-RouteTiming 'FAILED' $failureKind
        } catch {
            $script:RouteTimingRecorderFailureCount++
            $clock = $script:CurrentRouteTimingClock
            $active = $script:CurrentRouteTiming
            if ($null -ne $clock -and $null -ne $active) {
                try {
                    $failureRequest = $active['pendingCdpRequest']
                    if ($null -eq $failureRequest -and $null -ne $active['lastCdpRequest'] -and
                            [string]$active['lastCdpRequest']['state'] -ceq 'failed') {
                        $failureRequest = $active['lastCdpRequest']
                    }
                    $requestSnapshot = Get-RouteTimingRequestSnapshot $failureRequest
                    $fallbackRecord = [ordered]@{
                        ordinal = [int]$active['ordinal']
                        name = [string]$active['name']
                        kind = [string]$active['kind']
                        outcome = 'INCOMPLETE'
                        elapsedMs = [long]$clock.ElapsedMilliseconds
                        failureKind = 'route-timing-finalization-failed'
                        activePhaseAtEnd = [string]$active['phase']
                        collectionStatus = 'INCOMPLETE'
                        phases = $active['phases'].ToArray()
                        phaseCount = [int]$active['phaseCount']
                        phaseTruncatedCount = [int]$active['phaseTruncatedCount']
                        cdpCalls = $active['cdpCalls'].ToArray()
                        cdpCallCount = [int]$active['cdpCallCount']
                        cdpCallTruncatedCount = [int]$active['cdpCallTruncatedCount']
                        lastCdpRequest = $active['lastCdpRequest']
                        requestAtFailure = $requestSnapshot
                        deadlineRemainingAtStartMs = $active['deadlineRemainingAtStartMs']
                        samples = $active['samples'].ToArray()
                        sampleCount = [int]$active['sampleCount']
                        sampleTruncatedCount = [int]$active['sampleTruncatedCount']
                        sampleFailureCount = [int]$active['sampleFailureCount']
                        sampleSkippedCount = [int]$active['sampleSkippedCount']
                    }
                    $script:RouteTimingRecordCount++
                    if ($script:RouteTimingRecords.Count -ge $script:RouteTimingRecordLimit) {
                        $script:RouteTimingRecords.RemoveAt(0)
                        $script:RouteTimingTruncatedCount++
                    }
                    [void]$script:RouteTimingRecords.Add($fallbackRecord)
                } catch {
                    $script:RouteTimingRecorderFailureCount++
                } finally {
                    $clock.Stop()
                    $script:CurrentRouteTimingClock = $null
                    $script:CurrentRouteTiming = $null
                }
            }
        }
    }
    $script:FailureDiagnostic = New-FailureDiagnostic `
        (Get-EarlyErrorText $failureErrorRecord) $failureKind
    Write-Host ('FAIL verify-a03-browser: ' + [string]$script:FailureDiagnostic.error +
        ' phase=' + [string]$script:FailureDiagnostic.phase +
        ' operation=' + [string]$script:FailureDiagnostic.operation +
        ' elapsedMs=' + [string]$script:FailureDiagnostic.elapsedMs +
        ' remainingDeadlineMs=' + [string]$script:FailureDiagnostic.remainingDeadlineMs)
} finally {
    if ($null -ne $context) {
        if ($script:IsQ100Gate) {
            $script:Q100MatrixOperationElapsedMs = [long][Math]::Max(0,
                [Math]::Floor(([DateTime]::UtcNow - $script:Q100MatrixStartedUtc).TotalMilliseconds))
            if ($script:Q100MatrixOperationElapsedMs -gt 6000000L) {
                $requestedExitCode = 2
                if ($null -eq $script:FailureDiagnostic) {
                    Set-Operation 'browser' 'q100-matrix-operation-budget' $script:Q100MatrixDeadlineUtc
                    $script:FailureDiagnostic = New-FailureDiagnostic `
                        'Q100 matrix operation exceeded its 6000-second budget.' 'infrastructure'
                }
            }
        }
        $cleanupStartedUtc = [DateTime]::UtcNow
        $cleanupDeadline = if ($script:IsQ100Gate) { $cleanupStartedUtc.AddSeconds(120) } else { [DateTime]::MinValue }
        # Complete-VerifierRun owns teardown and has no safe cancellation API.
        # Finish its ownership proof, then fail closed if Q100 cleanup exceeded
        # its separately measured acceptance bound.
        Set-Operation 'cleanup' 'Complete-VerifierRun' $cleanupDeadline
        try { $cleanupResult = Complete-VerifierRun $context }
        catch {
            $cleanupResult = [pscustomobject]@{ Success = $false; Errors = @((Get-EarlyErrorText $_)) }
            $requestedExitCode = 2
            $script:CleanupDiagnostic = New-FailureDiagnostic (Get-EarlyErrorText $_) 'infrastructure'
        }
        if ($script:IsQ100Gate) {
            $script:Q100CleanupElapsedMs = [long][Math]::Max(0,
                [Math]::Floor(([DateTime]::UtcNow - $cleanupStartedUtc).TotalMilliseconds))
            if ($script:Q100CleanupElapsedMs -gt 120000L) {
                $requestedExitCode = 2
                if ($null -eq $script:CleanupDiagnostic) {
                    $script:CleanupDiagnostic = New-FailureDiagnostic `
                        'Q100 verifier cleanup exceeded its 120-second acceptance budget.' 'infrastructure'
                }
            }
        }
        if ($null -eq $cleanupResult -or -not [bool]$cleanupResult.Success) {
            $requestedExitCode = 2
        }
        if ($null -eq $script:CleanupDiagnostic -and
                $cleanupResult -and -not [bool]$cleanupResult.Success) {
            $cleanupMessage = if ($cleanupResult.Errors) {
                @($cleanupResult.Errors | Select-Object -First 1) -join '; '
            } else { 'Complete-VerifierRun did not prove cleanup.' }
            $script:CleanupDiagnostic = New-FailureDiagnostic $cleanupMessage 'infrastructure'
        }
        $cleanupErrors = if ($cleanupResult -and $cleanupResult.Errors) {
            @($cleanupResult.Errors | ForEach-Object { Get-DiagnosticText $_ 4000 })
        } else { @() }
        if ($script:IsQ100Gate -and $script:Q100CleanupElapsedMs -gt 120000L) {
            $cleanupErrors += 'Q100 verifier cleanup exceeded its 120-second acceptance budget.'
        }
        $reportPath = if ([String]::IsNullOrWhiteSpace($OutputPath)) {
            Join-Path $context.EvidenceDirectory 'verify-a03-browser.json'
        } else { [IO.Path]::GetFullPath($OutputPath) }
        $sourceDigests = [ordered]@{
            verifierIsolation = Get-SafeDigest (Join-Path $root 'scripts\VerifierIsolation.psm1')
            previewScript = Get-SafeDigest (Join-Path $root 'scripts\preview.ps1')
            sourceDigest = if ($previewIdentity) { [string]$previewIdentity.sourceDigest } else { '' }
            scriptDigest = if ($previewIdentity) { [string]$previewIdentity.scriptDigest } else { '' }
            webDigest = if ($previewIdentity) { [string]$previewIdentity.webDigest } else { '' }
            executionDigest = if ($previewIdentity) { [string]$previewIdentity.executionDigest } else { '' }
            executionFileCount = if ($previewIdentity) { [long]$previewIdentity.executionFileCount } else { 0L }
        }
        $cleanupSuccess = if ($cleanupResult) {
            [bool]$cleanupResult.Success -and
                (-not $script:IsQ100Gate -or $script:Q100CleanupElapsedMs -le 120000L)
        } else { $false }
        $cleanupReport = [ordered]@{
            success = $cleanupSuccess; errors = $cleanupErrors
            diagnostic = if ($script:CleanupDiagnostic) { $script:CleanupDiagnostic } else { [ordered]@{} }
        }
        if ($null -ne $cleanupResult -and $cleanupResult.PSObject.Properties['ExtraPhaseTimings']) {
            $cleanupReport.phaseTimings = $cleanupResult.ExtraPhaseTimings
        }
        if ($script:IsQ100Gate) {
            $cleanupReport.resourceSuccess = if ($cleanupResult) { [bool]$cleanupResult.Success } else { $false }
            $cleanupReport.elapsedMs = $script:Q100CleanupElapsedMs
            $cleanupReport.limitMs = 120000L
            $cleanupReport.status = if ($cleanupSuccess) { 'PASS' } else { 'FAIL' }
        }
        $report = [ordered]@{
            protocol = 'troubleshootjs-a03-browser-report-v1'
            gate = $Gate; smoke = [bool]$Smoke; headed = [bool]$Headed; runId = $context.RunId
            repositoryIdentity = $context.RepositoryIdentity
            sourceDigests = $sourceDigests
            previewIdentity = if ($previewIdentity) { $previewIdentity } else { [ordered]@{} }
            status = if ($requestedExitCode -eq 0) { 'PASS' } elseif ($requestedExitCode -eq 1) { 'FAIL' } else { 'INFRASTRUCTURE_FAILURE' }
            routes = $routeResults.ToArray()
            failure = if ($script:FailureDiagnostic) { $script:FailureDiagnostic } else { [ordered]@{} }
            cleanup = $cleanupReport
        }
        if ($script:RouteTimingEnabled) {
            $routeTimingStatus = if ($script:RouteTimingRecordCount -eq 0) {
                'NO_ROUTE_STARTED'
            } elseif ($script:RouteTimingRecorderFailureCount -gt 0 -or
                    $script:RouteTimingTruncatedCount -gt 0 -or
                    @($script:RouteTimingRecords | Where-Object outcome -CEQ 'INCOMPLETE').Count -gt 0) {
                'INCOMPLETE'
            } elseif (@($script:RouteTimingRecords | Where-Object outcome -CEQ 'FAILED').Count -gt 0) {
                'INCOMPLETE'
            } elseif (@($script:RouteTimingRecords | Where-Object collectionStatus -CEQ 'PARTIAL').Count -gt 0) {
                'PARTIAL'
            } else { 'COMPLETE' }
            $report['routeTiming'] = [ordered]@{
                protocol = 'a03-route-timing-v1'
                status = $routeTimingStatus
                timebase = 'monotonic-stopwatch'
                sampleKind = 'Runtime.getHeapUsage'
                perRouteRecordLimit = [int]$script:RouteTimingRecordLimit
                routeCount = [int]$script:RouteTimingRecordCount
                routeTruncatedCount = [int]$script:RouteTimingTruncatedCount
                recorderFailureCount = [int]$script:RouteTimingRecorderFailureCount
                routes = $script:RouteTimingRecords.ToArray()
            }
        }
        if ($script:IsQ100Gate) {
            $report.q100Matrix = [ordered]@{
                operationElapsedMs = $script:Q100MatrixOperationElapsedMs
                operationLimitMs = 6000000L
            }
        }
        if ($Gate -ieq 'E06State' -and $requestedExitCode -ne 0 -and
                $null -ne $reports -and $null -ne $definition) {
            $report.failedRoute = [ordered]@{
                name = [string]$definition.name; kind = [string]$definition.kind
                lastReports = $reports; eventCount = $script:CdpEvents.Count
                events = @($script:CdpEvents | Select-Object -Last 20)
            }
        }
        if ($Gate -ieq 'Q100Player' -and $script:Q100PlayerArtifactProof) {
            $report.q100PlayerArtifact = $script:Q100PlayerArtifactProof
        }
        try { Write-ReportFile $reportPath $report }
        catch {
            $requestedExitCode = 2
            Write-Host ('FAIL verify-a03-browser output: ' + (Get-DiagnosticText (Get-EarlyErrorText $_) 4000))
        }
        Write-Host ('A03_BROWSER_REPORT path=' + (Get-SafeText $reportPath 2000) +
            ' status=' + [string]$report.status + ' cleanup=' +
            [string]$report.cleanup.success)
    }
}
exit ([int]$requestedExitCode)

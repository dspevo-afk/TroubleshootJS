[CmdletBinding()]
param(
    [ValidateSet('Current', 'A08', 'A07', 'A06', 'A05', 'A03', 'A04', 'Task41', 'Task46', 'Task47', 'Task48', 'Task49', 'A02')]
    [string]$Gate = 'Current',
    [switch]$Smoke,
    [switch]$ForceTcpListener,
    [AllowEmptyString()]
    [string]$BrowserPath = '',
    [AllowEmptyString()]
    [string]$WorktreeRoot = '',
    [AllowEmptyString()]
    [string]$OutputPath = '',
    [int]$TimeoutSeconds = 90
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
        '(?i)[A-Z]:[^;\r\n ]+', '<owned-path>')
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
    foreach ($name in @('protocol','repositoryRoot','previewScript','webRoot',
            'sourceRoot','scriptRoot','sourceDigest','scriptDigest','webDigest',
            'executionDigest','verifierRunId','verifierNonce')) {
        if ($null -eq $identity.PSObject.Properties[$name] -or
                $identity.$name -isnot [string] -or
                [String]::IsNullOrWhiteSpace([string]$identity.$name)) {
            Throw-VerifierInfrastructure "Owned preview identity omitted exact string '$name'."
        }
    }
    foreach ($digest in @('sourceDigest','scriptDigest','webDigest','executionDigest')) {
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
    if ([string]$identity.protocol -cne 'troubleshootjs-preview-identity-v1' -or
            -not (Test-VerifierCanonicalWindowsPathValue $identity.repositoryRoot $Context.WorktreeRoot) -or
            -not (Test-VerifierCanonicalWindowsPathValue $identity.previewScript $expectedScript) -or
            -not (Test-VerifierCanonicalWindowsPathValue $identity.webRoot $expectedWeb) -or
            -not (Test-VerifierCanonicalWindowsPathValue $identity.sourceRoot $expectedSource) -or
            -not (Test-VerifierCanonicalWindowsPathValue $identity.scriptRoot $expectedScripts) -or
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

function Invoke-Cdp($Socket, [string]$Method, $Parameters, [DateTime]$Deadline) {
    $id = $script:NextCdpId
    $script:NextCdpId++
    [void](Send-Cdp $Socket $id $Method $Parameters)
    $response = Receive-Cdp $Socket $id $Deadline
    if ($response.PSObject.Properties['error']) {
        Throw-VerifierInfrastructure ("CDP protocol error for $Method`: " +
            ($response.error | ConvertTo-Json -Compress -Depth 8))
    }
    return $response
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
(()=>{const d=document.documentElement;const get=n=>d.getAttribute(n)||'';return {
  url:location.href,ready:document.readyState,
  verification:get('data-tsj-verification'),
  a08:get('data-tsj-a08-report'),a07:get('data-tsj-a07-report'),a06:get('data-tsj-a06-report'),a04:get('data-tsj-a04-report'),a03:get('data-tsj-a03-report'),task41:get('data-tsj-task41-evidence'),
  task46Parity:get('data-tsj-task46-parity'),
  task46Descriptor:get('data-tsj-task46-descriptor'),
  task46Snapshot:get('data-tsj-task46-snapshot'),
  task46Replay:get('data-tsj-task46-replay'),
  task47:get('data-tsj-task47-report'),task48:get('data-tsj-task48-report'),
  task49:get('data-tsj-task49-report'),a02:get('data-tsj-a02-report'),
  normalReady:(()=>{const b=document.body?document.body.innerText:'';
    const retest=[...document.querySelectorAll('button')].filter(x=>
      x.innerText.trim()==='Retest Customer');
    return {programReady:b.includes('Indicator does not light.'),
      retestCustomerReady:retest.length===1&&!retest[0].disabled};})()
};})()
'@
    return Evaluate-Cdp $Socket $expression $Deadline
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
                -not (Test-VerifierStrictIntegralValue $p.runtimeAssertions 71L ([long]::MaxValue)) -or
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
            'exclusive-private-graph-and-reentrant-model', 'real-nonfinite', 'real-singular',
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
                -not (Test-VerifierStrictIntegralValue $case.generatorVersion 5L 5L)) {
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

function Test-RouteReady($Kind, $Reports) {
    $verification = Get-ExactReportText $Reports.verification
    switch ($Kind) {
        'a08' { return $verification -ceq 'PASS:a08' -and (Test-A08Report $Reports.a08) }
        'a08-forced' { return $verification -ceq 'FAIL:a08:a08-explicit-failure-canary' -and
            [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a08)) }
        'a08-debugoff' { return $Reports.normalReady.programReady -is [bool] -and
            $Reports.normalReady.programReady -and $Reports.normalReady.retestCustomerReady -is [bool] -and
            $Reports.normalReady.retestCustomerReady -and
            $verification -notmatch '(?i)(?:^|:)a08(?:$|:)' -and
            [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a08)) }
        'diode-lifecycle' { return $verification -ceq 'PASS:diode' }
        'a07' { return $verification -ceq 'PASS:a07' -and (Test-A07Report $Reports.a07) }
        'a07-forced' { return $verification -ceq 'FAIL:a07:a07-explicit-failure-canary' -and
            [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a07)) }
        'a07-debugoff' { return [bool]$Reports.normalReady.programReady -and
            [bool]$Reports.normalReady.retestCustomerReady -and
            $verification -notmatch '(?i)(?:^|:)a07(?:$|:)' -and
            [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a07)) }
        'a06' { return $verification -ceq 'PASS:a06' -and (Test-A06Report $Reports.a06) }
        'a06-forced' { return $verification -ceq 'FAIL:a06:a06-explicit-failure-canary' -and
            [String]::IsNullOrWhiteSpace((Get-ExactReportText $Reports.a06)) }
        'a06-debugoff' { return [bool]$Reports.normalReady.programReady -and
            [bool]$Reports.normalReady.retestCustomerReady -and
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
            return [bool]$Reports.normalReady.programReady -and
                [bool]$Reports.normalReady.retestCustomerReady -and
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
            return [bool]$Reports.normalReady.programReady -and
                [bool]$Reports.normalReady.retestCustomerReady -and
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
        default { return $false }
    }
}

function Get-RouteDefinitions([string]$SelectedGate, [bool]$SmokeOnly) {
    if ($SmokeOnly) {
        return @([pscustomobject]@{ name = 'transport-smoke'; kind = 'smoke';
            query = 'tsjDebug=false'; reportNames = @() })
    }
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
    Set-Operation 'setup' 'New-VerifierRunContext'
    $context = New-VerifierRunContext $root ''
    Set-Operation 'setup' 'Resolve-VerifierBrowserPath'
    $resolvedBrowser = Resolve-VerifierBrowserPath $BrowserPath
    Set-Operation 'preview' 'Start-VerifierOwnedPreview'
    $baseUrl = [string](Start-VerifierOwnedPreview $context $previewPath $TimeoutSeconds ([bool]$ForceTcpListener))
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
        $resolvedBrowser $TimeoutSeconds
    Set-Operation 'browser' 'enable-cdp-domains' $session.Deadline
    [void](Invoke-Cdp $session.Socket 'Page.enable' @{} $session.Deadline)
    [void](Invoke-Cdp $session.Socket 'Runtime.enable' @{} $session.Deadline)

    foreach ($definition in $definitions) {
        $routeDeadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        Set-Operation 'browser' ('route-' + [string]$definition.name) $routeDeadline
        $routeUrl = $baseUrl + '/circuitjs.html?' + [string]$definition.query
        $navigationUrl = Navigate-Cdp $session.Socket $routeUrl $context.RunId `
            $session.RouteId $routeDeadline
        if ($definition.kind -eq 'smoke') {
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
            [void]$routeResults.Add($result)
            continue
        }
        $reports = $null
        do {
            $reports = Get-RouteReports $session.Socket $routeDeadline
            if (Test-RouteReady $definition.kind $reports) { break }
            if ((Get-ExactReportText $reports.verification) -like 'FAIL:*' -and
                    $definition.kind -notin @('a03-forced', 'a04-forced', 'a06-forced', 'a07-forced', 'a08-forced')) {
                Throw-AppFailure ("$($definition.name) reported " +
                    (Get-ExactReportText $reports.verification))
            }
            Start-Sleep -Milliseconds 250
        } while ([DateTime]::UtcNow -lt $routeDeadline)
        if (-not (Test-RouteReady $definition.kind $reports)) {
            Throw-VerifierInfrastructure ("$($definition.name) did not provide its required compiled report before the bounded deadline.")
        }
        $record = [ordered]@{ name = $definition.name; kind = $definition.kind;
            status = 'PASS'; navigationUrl = (Get-SafeText $navigationUrl 2000);
            verification = (Get-ExactReportText $reports.verification);
            readiness = [ordered]@{ programReady = [bool]$reports.normalReady.programReady;
                retestCustomerReady = [bool]$reports.normalReady.retestCustomerReady };
            reports = [ordered]@{}; events = @($script:CdpEvents | Select-Object -Last 40) }
        foreach ($reportName in @($definition.reportNames)) {
            $property = $reports.PSObject.Properties[$reportName]
            if ($null -eq $property) {
                Throw-VerifierInfrastructure "Route '$($definition.name)' omitted report '$reportName'."
            }
            $record.reports[$reportName] = Get-ExactReportText $property.Value
        }
        $artifactName = 'route-' + ($definition.name -replace '[^A-Za-z0-9._-]', '-') + '.json'
        $artifact = Write-RunArtifact $context $artifactName $record
        $record.artifactSha256 = $artifact.sha256
        [void]$routeResults.Add($record)
    }
    $requestedExitCode = 0
} catch {
    $exception = if ($_.PSObject.Properties['Exception']) { $_.Exception } else { $_ }
    $requestedExitCode = if ($exception.Data -and $exception.Data.Contains('VerifierExitCode')) {
        [int]$exception.Data['VerifierExitCode']
    } else { 2 }
    $failureKind = if ($requestedExitCode -eq 1) { 'application' } else { 'infrastructure' }
    $script:FailureDiagnostic = New-FailureDiagnostic (Get-EarlyErrorText $_) $failureKind
    Write-Host ('FAIL verify-a03-browser: ' + [string]$script:FailureDiagnostic.error +
        ' phase=' + [string]$script:FailureDiagnostic.phase +
        ' operation=' + [string]$script:FailureDiagnostic.operation +
        ' elapsedMs=' + [string]$script:FailureDiagnostic.elapsedMs +
        ' remainingDeadlineMs=' + [string]$script:FailureDiagnostic.remainingDeadlineMs)
} finally {
    if ($null -ne $context) {
        Set-Operation 'cleanup' 'Complete-VerifierRun'
        try { $cleanupResult = Complete-VerifierRun $context }
        catch {
            $cleanupResult = [pscustomobject]@{ Success = $false; Errors = @((Get-EarlyErrorText $_)) }
            $requestedExitCode = 2
            $script:CleanupDiagnostic = New-FailureDiagnostic (Get-EarlyErrorText $_) 'infrastructure'
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
        $report = [ordered]@{
            protocol = 'troubleshootjs-a03-browser-report-v1'
            gate = $Gate; smoke = [bool]$Smoke; runId = $context.RunId
            repositoryIdentity = $context.RepositoryIdentity
            sourceDigests = $sourceDigests
            previewIdentity = if ($previewIdentity) { $previewIdentity } else { [ordered]@{} }
            status = if ($requestedExitCode -eq 0) { 'PASS' } elseif ($requestedExitCode -eq 1) { 'FAIL' } else { 'INFRASTRUCTURE_FAILURE' }
            routes = $routeResults.ToArray()
            failure = if ($script:FailureDiagnostic) { $script:FailureDiagnostic } else { [ordered]@{} }
            cleanup = [ordered]@{ success = if ($cleanupResult) { [bool]$cleanupResult.Success } else { $false }; errors = $cleanupErrors; diagnostic = if ($script:CleanupDiagnostic) { $script:CleanupDiagnostic } else { [ordered]@{} } }
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

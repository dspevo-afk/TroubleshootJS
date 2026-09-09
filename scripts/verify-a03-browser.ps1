[CmdletBinding()]
param(
    [ValidateSet('A03', 'Task41', 'Task46', 'Task47', 'Task48', 'Task49', 'A02', 'All')]
    [string]$Gate = 'A03',
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
  a03:get('data-tsj-a03-report'),task41:get('data-tsj-task41-evidence'),
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

function Test-RouteReady($Kind, $Reports) {
    $verification = Get-ExactReportText $Reports.verification
    switch ($Kind) {
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
                (Test-JsonReport $Reports.task48 'TSJ-TASK48-1')
        }
        'task49' {
            return $verification -ceq 'PASS:task49' -and
                (Test-JsonReport $Reports.task49 'TSJ-TASK49-1')
        }
        'a02' {
            return $verification -ceq 'PASS:a02' -and
                (Test-JsonReport $Reports.a02 'TSJ-A02-1')
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
    if ($SelectedGate -ceq 'All') { return $all }
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
            'A03' { 3 }
            'All' { 9 }
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
                    $definition.kind -notin @('a03-forced')) {
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

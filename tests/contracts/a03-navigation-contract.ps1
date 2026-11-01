[CmdletBinding()]
param(
    [switch]$RealBrowser
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ContractAssertions = 0
$script:LoadedFunctionHashes = [ordered]@{}
$script:RepositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$script:BrowserScriptPath = Join-Path $script:RepositoryRoot 'scripts\verify-a03-browser.ps1'
$script:IsolationModulePath = Join-Path $script:RepositoryRoot 'scripts\VerifierIsolation.psm1'
$script:PreviewScriptPath = Join-Path $script:RepositoryRoot 'scripts\preview.ps1'
$script:ContractScriptPath = $PSCommandPath
$script:LoadedSourceFileHashes = $null

function Assert-Contract([bool]$Condition, [string]$Description) {
    if (-not $Condition) {
        throw [InvalidOperationException]::new(('A03 navigation contract failed: ' + $Description))
    }
    $script:ContractAssertions++
}

function Get-ContractErrorText($Record) {
    if ($null -eq $Record) { return '' }
    $exception = if ($Record.PSObject.Properties['Exception'] -and $null -ne $Record.Exception) {
        $Record.Exception
    } else { $Record }
    $messages = New-Object Collections.Generic.List[string]
    while ($null -ne $exception) {
        if ($exception -is [Exception] -and -not [String]::IsNullOrWhiteSpace($exception.Message)) {
            [void]$messages.Add([string]$exception.Message)
            $exception = $exception.InnerException
        } else {
            break
        }
    }
    if ($messages.Count -eq 0) { return [string]$Record }
    return $messages -join ' | '
}

function Assert-ContractThrows([scriptblock]$Action, [string]$ExpectedText,
        [string]$Description) {
    $caught = $null
    try { & $Action | Out-Null } catch { $caught = $_ }
    if ($null -eq $caught) {
        throw [InvalidOperationException]::new(('A03 navigation contract did not reject: ' + $Description))
    }
    $text = Get-ContractErrorText $caught
    if ($text.IndexOf($ExpectedText, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw [InvalidOperationException]::new(('A03 navigation contract rejected for the wrong reason (' +
            $Description + '): ' + $text))
    }
    $script:ContractAssertions++
}

function Get-ContractSha256([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant()
    } finally { $sha.Dispose() }
}

function Get-ContractFileSha256([string]$Path) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read,
        [IO.FileShare]::Read)
    try {
        $sha = [Security.Cryptography.SHA256]::Create()
        try {
            return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
        } finally { $sha.Dispose() }
    } finally { $stream.Dispose() }
}

function Get-ContractSourceFileHashes {
    return [ordered]@{
        browserScript = Get-ContractFileSha256 $script:BrowserScriptPath
        isolationModule = Get-ContractFileSha256 $script:IsolationModulePath
        previewScript = Get-ContractFileSha256 $script:PreviewScriptPath
        contractScript = Get-ContractFileSha256 $script:ContractScriptPath
    }
}

function Get-OwnedShutdownTimingEvidence($ExtraPhaseTimings) {
    $rawRecords = $null
    if ($null -ne $ExtraPhaseTimings) {
        if ($ExtraPhaseTimings -is [System.Collections.IDictionary]) {
            if ($ExtraPhaseTimings.Contains('BrowserShutdown')) {
                $rawRecords = $ExtraPhaseTimings['BrowserShutdown']
            }
        } else {
            $property = $ExtraPhaseTimings.PSObject.Properties['BrowserShutdown']
            if ($null -ne $property) { $rawRecords = $property.Value }
        }
    }

    $records = New-Object Collections.Generic.List[object]
    if ($null -ne $rawRecords) {
        $isRecordCollection = ($rawRecords -is [Array]) -or
            ($rawRecords -is [System.Collections.IList] -and
             $rawRecords -isnot [string] -and
             $rawRecords -isnot [System.Collections.IDictionary])
        if ($isRecordCollection) {
            foreach ($record in $rawRecords) { [void]$records.Add($record) }
        } else {
            [void]$records.Add($rawRecords)
        }
    }

    if ($records.Count -eq 0) {
        return [pscustomobject]@{ Status = 'NOT_PROVEN'; RecordCount = 0; TotalMs = $null }
    }
    if ($records.Count -ne 1) {
        return [pscustomobject]@{ Status = 'AMBIGUOUS'; RecordCount = [int]$records.Count; TotalMs = $null }
    }
    $record = $records[0]
    $totalProperty = $null
    if ($null -ne $record) {
        if ($record -is [System.Collections.IDictionary]) {
            if ($record.Contains('TotalMs')) { $totalProperty = [pscustomobject]@{ Value = $record['TotalMs'] } }
        } else {
            $totalProperty = $record.PSObject.Properties['TotalMs']
        }
    }
    if ($null -eq $totalProperty) {
        return [pscustomobject]@{ Status = 'INVALID_RECORD'; RecordCount = 1; TotalMs = $null }
    }
    $total = $totalProperty.Value
    $isInteger = $total -is [sbyte] -or $total -is [byte] -or
        $total -is [int16] -or $total -is [uint16] -or
        $total -is [int32] -or $total -is [uint32] -or
        $total -is [int64] -or $total -is [uint64]
    if (-not $isInteger -or [decimal]$total -lt 0 -or
            [decimal]$total -gt [decimal][long]::MaxValue) {
        return [pscustomobject]@{ Status = 'INVALID_RECORD'; RecordCount = 1; TotalMs = $null }
    }
    $totalMs = [long]$total
    if ($totalMs -gt 15000L) {
        return [pscustomobject]@{ Status = 'OVER_LIMIT'; RecordCount = 1; TotalMs = $totalMs }
    }
    return [pscustomobject]@{ Status = 'PASS'; RecordCount = 1; TotalMs = $totalMs }
}

function Get-ContractCollectionCount($Value) {
    if ($null -eq $Value) { return 0 }
    if ($Value -is [Array]) { return [int]$Value.Length }
    if ($Value -is [System.Collections.ICollection]) { return [int]$Value.Count }
    return 1
}

function Set-ContractFunction([string]$Name, [scriptblock]$Body) {
    Set-Item -Path ('Function:script:' + $Name) -Value $Body
}

function Import-A03NavigationFunctions {
    if (-not (Test-Path -LiteralPath $script:BrowserScriptPath -PathType Leaf) -or
            -not (Test-Path -LiteralPath $script:IsolationModulePath -PathType Leaf) -or
            -not (Test-Path -LiteralPath $script:PreviewScriptPath -PathType Leaf)) {
        throw [IO.FileNotFoundException]::new('A03 navigation source, isolation module, or owned preview script is missing.')
    }
    $script:LoadedSourceFileHashes = Get-ContractSourceFileHashes
    Import-Module -Name $script:IsolationModulePath -Force -ErrorAction Stop
    foreach ($name in @('New-VerifierRunContext', 'Start-VerifierOwnedPreview',
            'New-VerifierBrowserSession', 'Complete-VerifierRun', 'Get-VerifierErrorMessage',
            'Throw-VerifierInfrastructure')) {
        if (-not (Get-Command -Name $name -CommandType Function -ErrorAction SilentlyContinue)) {
            throw [InvalidOperationException]::new(('VerifierIsolation did not export required function ' + $name))
        }
    }

    $tokens = $null
    $parseErrors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile(
        $script:BrowserScriptPath, [ref]$tokens, [ref]$parseErrors)
    if ($parseErrors.Count -gt 0) {
        throw [InvalidOperationException]::new('The maintained A03 browser script did not parse cleanly.')
    }
    $required = @(
        'Get-SafeText', 'Get-DiagnosticText', 'Get-OwnedNavigationValue',
        'Test-OwnedNavigationInteger', 'Get-OwnedLoopbackOrigin',
        'Test-OwnedBeforeUnloadAuthorization', 'New-OwnedNavigationResponseState',
        'Set-OwnedNavigationAckRequestId', 'Add-OwnedNavigationResponse',
        'Test-OwnedNavigationResponsesComplete', 'Test-OwnedNavigationReadiness',
        'Wait-OwnedNavigationReady', 'Send-Cdp', 'Set-OwnedNavigationDialogRejection',
        'Receive-Cdp', 'Invoke-Cdp', 'Evaluate-Cdp', 'Navigate-Cdp'
    )
    $matchesByName = @{}
    $functionNodes = @($ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
            $required -ccontains [string]$node.Name
    }, $true))
    foreach ($functionNode in $functionNodes) {
        if ($matchesByName.ContainsKey([string]$functionNode.Name)) {
            throw [InvalidOperationException]::new(('Maintained A03 script defines duplicate function ' +
                [string]$functionNode.Name))
        }
        $matchesByName[[string]$functionNode.Name] = $functionNode
    }
    $functionSources = New-Object Collections.Generic.List[string]
    $functionHashes = [ordered]@{}
    foreach ($name in $required) {
        if (-not $matchesByName.ContainsKey($name)) {
            throw [InvalidOperationException]::new(('Maintained A03 script omitted function ' + $name))
        }
        $node = $matchesByName[$name]
        [void]$functionSources.Add([string]$node.Extent.Text)
        $functionHashes[$name] = Get-ContractSha256 `
            ([Text.Encoding]::UTF8.GetBytes([string]$node.Extent.Text))
    }

    $script:NextCdpId = 1
    $script:CdpEvents = New-Object 'System.Collections.Generic.List[string]'
    $script:OwnedPreviewOrigin = $null
    $script:LastCompletedOwnedNavigation = $null
    $script:ActiveOwnedNavigation = $null
    $script:LastNavigationEvidence = $null
    $script:LastNavigationDialogRejection = $null
    $script:RouteTimingEnabled = $false
    $script:CurrentRouteTiming = $null
    $script:CurrentRouteTimingClock = $null
    return [pscustomobject]@{ FunctionSources = $functionSources.ToArray();
        FunctionHashes = $functionHashes }
}

function Copy-ContractOrderedMap($Value) {
    $copy = [ordered]@{}
    foreach ($key in $Value.Keys) { $copy[$key] = $Value[$key] }
    return ,$copy
}

function New-ContractAuthorizationFixture {
    $origin = 'http://127.0.0.1:41817'
    $socket = [object]::new()
    $previous = [ordered]@{
        socket = $socket
        url = $origin + '/source.html?tsjVerifierNavigation=prior'
        origin = $origin
        runId = 'pure-run-1'
        routeId = 'pure-route-1'
        completed = $true
    }
    $state = New-OwnedNavigationResponseState 41
    $active = [ordered]@{
        socket = $socket
        previousCompletion = $previous
        priorUrl = [string]$previous.url
        targetUrl = $origin + '/target.html?tsjVerifierNavigation=next'
        ownedOrigin = $origin
        origin = $origin
        runId = 'pure-run-1'
        routeId = 'pure-route-1'
        deadline = [DateTime]::UtcNow.AddSeconds(30)
        navigationRequestId = 41
        dialogAckRequestId = $null
        dialogHandledCount = 0
        navigationResponseReceived = $false
        responseState = $state
    }
    $event = [pscustomobject]@{
        method = 'Page.javascriptDialogOpening'
        params = [pscustomobject]@{
            type = 'beforeunload'
            url = [string]$previous.url
            hasBrowserHandler = $true
            message = 'fixture'
        }
    }
    return [pscustomobject]@{ Origin = $origin; Socket = $socket; Previous = $previous;
        State = $state; Active = $active; Event = $event }
}

function Test-PureNavigationContracts {
    $fixture = New-ContractAuthorizationFixture
    $script:OwnedPreviewOrigin = $fixture.Origin
    $script:LastCompletedOwnedNavigation = $fixture.Previous
    Assert-Contract (Test-OwnedBeforeUnloadAuthorization $fixture.Event $fixture.Socket `
        $fixture.Active 41 ([DateTime]::UtcNow)) 'exact pending owned beforeunload is authorized'

    $wrongEvent = [pscustomobject]@{ method = 'Runtime.consoleAPICalled'; params = $fixture.Event.params }
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $wrongEvent $fixture.Socket `
        $fixture.Active 41 ([DateTime]::UtcNow))) 'non-dialog CDP event is rejected'
    $wrongType = [pscustomobject]@{ method = 'Page.javascriptDialogOpening';
        params = [pscustomobject]@{ type = 'alert'; url = $fixture.Previous.url } }
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $wrongType $fixture.Socket `
        $fixture.Active 41 ([DateTime]::UtcNow))) 'alert is not authorized as beforeunload'
    $wrongSource = [pscustomobject]@{ method = 'Page.javascriptDialogOpening';
        params = [pscustomobject]@{ type = 'beforeunload';
            url = $fixture.Origin + '/changed-source.html'; hasBrowserHandler = $true } }
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $wrongSource $fixture.Socket `
        $fixture.Active 41 ([DateTime]::UtcNow))) 'changed source URL is rejected'
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $fixture.Event ([object]::new()) `
        $fixture.Active 41 ([DateTime]::UtcNow))) 'different WebSocket is rejected'
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $fixture.Event $fixture.Socket `
        $fixture.Active 42 ([DateTime]::UtcNow))) 'different pending request ID is rejected'
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $fixture.Event $fixture.Socket `
        $fixture.Active 41 ([DateTime]$fixture.Active.deadline))) 'expired navigation is rejected'

    $variant = Copy-ContractOrderedMap $fixture.Active
    $variant.dialogHandledCount = 1
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $fixture.Event $fixture.Socket `
        $variant 41 ([DateTime]::UtcNow))) 'second dialog is rejected'
    $variant = Copy-ContractOrderedMap $fixture.Active
    $variant.dialogAckRequestId = 42
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $fixture.Event $fixture.Socket `
        $variant 41 ([DateTime]::UtcNow))) 'dialog with an existing acknowledgement ID is rejected'
    $variant = Copy-ContractOrderedMap $fixture.Active
    $variant.responseState = New-OwnedNavigationResponseState 41
    $variant.responseState.navigationResponse = [pscustomobject]@{
        id = 41; result = [pscustomobject]@{ frameId = 'prior-response' }
    }
    $variant.navigationResponseReceived = $true
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $fixture.Event $fixture.Socket `
        $variant 41 ([DateTime]::UtcNow))) 'dialog after the navigation response is rejected'
    $variant = Copy-ContractOrderedMap $fixture.Active
    $variant.previousCompletion = Copy-ContractOrderedMap $fixture.Previous
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $fixture.Event $fixture.Socket `
        $variant 41 ([DateTime]::UtcNow))) 'copied stale completion record is rejected'
    $variant = Copy-ContractOrderedMap $fixture.Active
    $variant.previousCompletion.completed = $false
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $fixture.Event $fixture.Socket `
        $variant 41 ([DateTime]::UtcNow))) 'incomplete previous navigation is rejected'
    $variant = Copy-ContractOrderedMap $fixture.Active
    $variant.routeId = 'different-route'
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $fixture.Event $fixture.Socket `
        $variant 41 ([DateTime]::UtcNow))) 'route mismatch is rejected'
    $variant = Copy-ContractOrderedMap $fixture.Active
    $variant.runId = 'different-run'
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $fixture.Event $fixture.Socket `
        $variant 41 ([DateTime]::UtcNow))) 'run mismatch is rejected'
    $variant = Copy-ContractOrderedMap $fixture.Active
    $variant.ownedOrigin = 'http://127.0.0.1:41818'
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $fixture.Event $fixture.Socket `
        $variant 41 ([DateTime]::UtcNow))) 'different owned origin is rejected'
    $variant = Copy-ContractOrderedMap $fixture.Active
    $variant.targetUrl = 'http://example.invalid/target.html'
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $fixture.Event $fixture.Socket `
        $variant 41 ([DateTime]::UtcNow))) 'non-loopback target origin is rejected'
    $userinfoPrevious = Copy-ContractOrderedMap $fixture.Previous
    $userinfoPrevious.url = 'http://user@127.0.0.1:41817/source.html'
    $variant = Copy-ContractOrderedMap $fixture.Active
    $variant.previousCompletion = $userinfoPrevious
    $variant.priorUrl = [string]$userinfoPrevious.url
    $script:LastCompletedOwnedNavigation = $userinfoPrevious
    $userinfoEvent = [pscustomobject]@{ method = 'Page.javascriptDialogOpening';
        params = [pscustomobject]@{ type = 'beforeunload'; url = $userinfoPrevious.url } }
    Assert-Contract (-not (Test-OwnedBeforeUnloadAuthorization $userinfoEvent $fixture.Socket `
        $variant 41 ([DateTime]::UtcNow))) 'URL userinfo is rejected as an owned origin'
    $script:LastCompletedOwnedNavigation = $fixture.Previous

    Assert-Contract (-not (Test-OwnedNavigationReadiness `
        ([pscustomobject]@{ ready = 'interactive'; url = $fixture.Active.targetUrl }) `
        $fixture.Active.targetUrl)) 'non-complete document is not ready'
    Assert-Contract (-not (Test-OwnedNavigationReadiness `
        ([pscustomobject]@{ ready = 'complete'; url = $fixture.Origin + '/wrong.html' }) `
        $fixture.Active.targetUrl)) 'marker-preserving wrong URL is not ready'
    Assert-Contract (Test-OwnedNavigationReadiness `
        ([pscustomobject]@{ ready = 'complete'; url = $fixture.Active.targetUrl }) `
        $fixture.Active.targetUrl) 'exact completed target URL is ready'
    $warRoot = Join-Path $script:RepositoryRoot 'war'
    $ownedProbe = Join-Path $warRoot 'a03-navigation-contract-probe\fixture.html'
    Assert-RealFixturePath $ownedProbe $warRoot
    Assert-Contract $true 'fixture containment accepts a child path under war'
    $scriptProbe = Join-Path $script:RepositoryRoot 'scripts\not-an-owned-fixture.html'
    Assert-ContractThrows { Assert-RealFixturePath $scriptProbe $warRoot } `
        'escaped the task-owned web-root child' 'fixture containment rejects a path outside war'
    $reportLaneList = New-Object Collections.Generic.List[object]
    foreach ($lane in @(
            [pscustomobject]@{ lane = 'positive'; status = 'PASS' },
            [pscustomobject]@{ lane = 'alert'; status = 'PASS_EXPECTED_REJECTION' },
            [pscustomobject]@{ lane = 'source-mismatch'; status = 'PASS_EXPECTED_REJECTION' },
            [pscustomobject]@{ lane = 'wrong-destination'; status = 'FAIL'; failureCode = 'CLEANUP_NOT_PROVEN' })) {
        [void]$reportLaneList.Add($lane)
    }
    $serializedLaneReport = [ordered]@{ lanes = $reportLaneList.ToArray() } |
        ConvertTo-Json -Compress -Depth 5
    $parsedLaneReport = $serializedLaneReport | ConvertFrom-Json -ErrorAction Stop
    Assert-Contract (@($parsedLaneReport.lanes).Count -eq 4) `
        'real report conversion retains four Generic.List lane rows'
    Assert-Contract ([string]$parsedLaneReport.lanes[3].status -ceq 'FAIL' -and
        [string]$parsedLaneReport.lanes[3].failureCode -ceq 'CLEANUP_NOT_PROVEN') `
        'real report conversion retains failed lane evidence'

    $shutdownZero = Get-OwnedShutdownTimingEvidence `
        ([pscustomobject]@{ BrowserShutdown = [object[]]@() })
    Assert-Contract ([string]$shutdownZero.Status -ceq 'NOT_PROVEN' -and
        [int]$shutdownZero.RecordCount -eq 0 -and $null -eq $shutdownZero.TotalMs) `
        'empty shutdown timing array remains explicit and unproven'
    $shutdownMissing = Get-OwnedShutdownTimingEvidence ([pscustomobject]@{})
    Assert-Contract ([string]$shutdownMissing.Status -ceq 'NOT_PROVEN' -and
        [int]$shutdownMissing.RecordCount -eq 0) 'missing shutdown timing property is unproven'
    $shutdownScalar = Get-OwnedShutdownTimingEvidence `
        ([pscustomobject]@{ BrowserShutdown = [pscustomobject]@{ TotalMs = [long]15000 } })
    Assert-Contract ([string]$shutdownScalar.Status -ceq 'PASS' -and
        [int]$shutdownScalar.RecordCount -eq 1 -and [long]$shutdownScalar.TotalMs -eq 15000) `
        'scalar single shutdown record passes the exact 15-second bound'
    $shutdownArray = Get-OwnedShutdownTimingEvidence ([pscustomobject]@{
        BrowserShutdown = [object[]]@([pscustomobject]@{ TotalMs = [int]0 })
    })
    Assert-Contract ([string]$shutdownArray.Status -ceq 'PASS' -and
        [long]$shutdownArray.TotalMs -eq 0) 'single-element shutdown array is handled'
    $shutdownMultiple = Get-OwnedShutdownTimingEvidence ([pscustomobject]@{
        BrowserShutdown = [object[]]@([pscustomobject]@{ TotalMs = [long]2 },
            [pscustomobject]@{ TotalMs = [long]3 })
    })
    Assert-Contract ([string]$shutdownMultiple.Status -ceq 'AMBIGUOUS' -and
        [int]$shutdownMultiple.RecordCount -eq 2 -and $null -eq $shutdownMultiple.TotalMs) `
        'multiple shutdown records are not collapsed to one timing'
    $shutdownMissingTotal = Get-OwnedShutdownTimingEvidence ([pscustomobject]@{
        BrowserShutdown = [pscustomobject]@{ Lane = 'natural' }
    })
    Assert-Contract ([string]$shutdownMissingTotal.Status -ceq 'INVALID_RECORD') `
        'shutdown record without TotalMs is invalid'
    $shutdownWrongType = Get-OwnedShutdownTimingEvidence ([pscustomobject]@{
        BrowserShutdown = [pscustomobject]@{ TotalMs = '14999' }
    })
    Assert-Contract ([string]$shutdownWrongType.Status -ceq 'INVALID_RECORD') `
        'string shutdown timing is not accepted as measured numeric data'
    $shutdownNegative = Get-OwnedShutdownTimingEvidence ([pscustomobject]@{
        BrowserShutdown = [pscustomobject]@{ TotalMs = [long]-1 }
    })
    Assert-Contract ([string]$shutdownNegative.Status -ceq 'INVALID_RECORD') `
        'negative shutdown timing is invalid'
    $shutdownOver = Get-OwnedShutdownTimingEvidence ([pscustomobject]@{
        BrowserShutdown = [pscustomobject]@{ TotalMs = [long]15001 }
    })
    Assert-Contract ([string]$shutdownOver.Status -ceq 'OVER_LIMIT' -and
        [long]$shutdownOver.TotalMs -eq 15001) 'shutdown above 15 seconds fails without rounding'

    $navigationResponse = [pscustomobject]@{
        id = 41; result = [pscustomobject]@{ frameId = 'frame-1' }
    }
    $ackResponse = [pscustomobject]@{ id = 42; result = [pscustomobject]@{} }
    $state = New-OwnedNavigationResponseState 41
    Set-OwnedNavigationAckRequestId $state 42
    Assert-Contract (-not (Test-OwnedNavigationResponsesComplete $state)) `
        'both known responses are required after a dialog acknowledgement is issued'
    Add-OwnedNavigationResponse $state $navigationResponse
    Assert-Contract (-not (Test-OwnedNavigationResponsesComplete $state)) `
        'navigation response alone does not complete an acknowledged navigation'
    Add-OwnedNavigationResponse $state $ackResponse
    Assert-Contract (Test-OwnedNavigationResponsesComplete $state) `
        'navigation response followed by acknowledgement completes'
    Assert-Contract ([int]$state.acknowledgementSuccessCount -eq 1) `
        'successful acknowledgement is counted once'

    $state = New-OwnedNavigationResponseState 51
    Set-OwnedNavigationAckRequestId $state 52
    Add-OwnedNavigationResponse $state ([pscustomobject]@{ id = 52; result = [pscustomobject]@{} })
    Assert-Contract (-not (Test-OwnedNavigationResponsesComplete $state)) `
        'acknowledgement response alone does not complete navigation'
    Add-OwnedNavigationResponse $state ([pscustomobject]@{ id = 51; result = [pscustomobject]@{ frameId = 'frame-2' } })
    Assert-Contract (Test-OwnedNavigationResponsesComplete $state) `
        'acknowledgement followed by navigation response completes'

    $state = New-OwnedNavigationResponseState 61
    Add-OwnedNavigationResponse $state ([pscustomobject]@{ id = 61; result = [pscustomobject]@{} })
    Assert-Contract (Test-OwnedNavigationResponsesComplete $state) `
        'navigation without a dialog completes on its sole response'
    Assert-ContractThrows { Add-OwnedNavigationResponse $state `
        ([pscustomobject]@{ id = 61; result = [pscustomobject]@{} }) } `
        'duplicated' 'duplicate navigation response'
    Assert-ContractThrows { Set-OwnedNavigationAckRequestId $state 61 } `
        'invalid or duplicated' 'ack ID equal to navigation ID'
    $ackIdState = New-OwnedNavigationResponseState 62
    Set-OwnedNavigationAckRequestId $ackIdState 63
    Assert-ContractThrows { Set-OwnedNavigationAckRequestId $ackIdState 64 } `
        'invalid or duplicated' 'duplicate dialog acknowledgement request ID'
    $state = New-OwnedNavigationResponseState 71
    Set-OwnedNavigationAckRequestId $state 72
    Assert-ContractThrows { Add-OwnedNavigationResponse $state `
        ([pscustomobject]@{ id = 73; result = [pscustomobject]@{} }) } `
        'unknown' 'unknown CDP response ID'
    Assert-ContractThrows { Add-OwnedNavigationResponse $state `
        ([pscustomobject]@{ id = 0; result = [pscustomobject]@{} }) } `
        'malformed response ID' 'invalid response ID'
    Assert-ContractThrows { Add-OwnedNavigationResponse $state `
        ([pscustomobject]@{ id = 71; error = [pscustomobject]@{ code = -1 }; result = [pscustomobject]@{} }) } `
        'error response' 'CDP error response'
    Assert-ContractThrows { Add-OwnedNavigationResponse $state `
        ([pscustomobject]@{ id = 71 }) } 'malformed CDP result' 'missing result object'
    Assert-ContractThrows { Add-OwnedNavigationResponse $state `
        ([pscustomobject]@{ id = 71; result = 'not-an-object' }) } `
        'malformed CDP result' 'non-object response result'
    $state = New-OwnedNavigationResponseState 81
    Set-OwnedNavigationAckRequestId $state 82
    Add-OwnedNavigationResponse $state ([pscustomobject]@{ id = 82; result = [pscustomobject]@{} })
    Assert-ContractThrows { Add-OwnedNavigationResponse $state `
        ([pscustomobject]@{ id = 82; result = [pscustomobject]@{} }) } `
        'duplicated' 'duplicate dialog acknowledgement response'
    Assert-ContractThrows { New-OwnedNavigationResponseState 0 } `
        'invalid' 'zero navigation request ID'
    Assert-ContractThrows { Set-OwnedNavigationAckRequestId `
        (New-OwnedNavigationResponseState 91) 0 } 'invalid or duplicated' 'zero dialog acknowledgement ID'

    # Exercise the real Page.navigate request fence while only replacing the
    # transport endpoints in this pure-contract process. RealBrowser uses the
    # untouched functions loaded from the maintained source in a fresh process.
    $actualSend = (Get-Command -Name 'Send-Cdp' -CommandType Function).ScriptBlock
    $actualReceive = (Get-Command -Name 'Receive-Cdp' -CommandType Function).ScriptBlock
    $script:TransportSendCount = 0
    $script:TransportCaptured = $null
    Set-ContractFunction 'Send-Cdp' {
        param($Socket, [int]$Id, [string]$Method, $Parameters, [DateTime]$Deadline)
        $script:TransportSendCount++
        $script:TransportCaptured = [pscustomobject]@{ Id = $Id; Method = $Method;
            Parameters = $Parameters; Deadline = $Deadline; Socket = $Socket }
    }
    Set-ContractFunction 'Receive-Cdp' {
        param($Socket, [int]$WantedId, [DateTime]$Deadline, $NavigationState = $null)
        $script:TransportReceivedState = $NavigationState
        return [pscustomobject]@{ id = $WantedId; result = [pscustomobject]@{ frameId = 'frame-pure' } }
    }
    try {
        $socket = [object]::new()
        $deadline = [DateTime]::UtcNow.AddSeconds(20)
        $targetUrl = $fixture.Origin + '/navigation-target.html?run=pure'
        $script:ActiveOwnedNavigation = [ordered]@{
            socket = $socket; targetUrl = $targetUrl; navigationRequestId = 0
            deadline = $deadline; responseState = $null
        }
        $script:NextCdpId = 101
        $response = Invoke-Cdp $socket 'Page.navigate' @{ url = $targetUrl } $deadline
        Assert-Contract ([int]$script:TransportSendCount -eq 1) 'exact Page.navigate is sent once'
        Assert-Contract ([string]$script:TransportCaptured.Parameters.url -ceq $targetUrl) `
            'transport receives exact owned target URL'
        Assert-Contract ([int]$script:ActiveOwnedNavigation.navigationRequestId -eq 101 -and
            [int]$script:TransportReceivedState.navigationRequestId -eq 101) `
            'navigation request and bounded response state share the actual issued ID'
        Assert-Contract ([int]$response.id -eq 101) 'issued navigation returns only its response'
        $script:ActiveOwnedNavigation = [ordered]@{
            socket = $socket; targetUrl = $targetUrl; navigationRequestId = 0
            deadline = $deadline; responseState = $null
        }
        Assert-ContractThrows { Invoke-Cdp $socket 'Page.navigate' `
            @{ url = $fixture.Origin + '/different-target.html' } $deadline } `
            'exact owned navigation scope' 'Page.navigate URL does not match owned target'
        Assert-Contract ([int]$script:TransportSendCount -eq 1) `
            'mismatched Page.navigate is rejected before transport send'
    } finally {
        Set-ContractFunction 'Send-Cdp' $actualSend
        Set-ContractFunction 'Receive-Cdp' $actualReceive
        $script:ActiveOwnedNavigation = $null
    }

    # A failed owned navigation must clear transient authorization and stale
    # completion/evidence, even when the real Invoke-Cdp transport throws.
    $actualInvoke = (Get-Command -Name 'Invoke-Cdp' -CommandType Function).ScriptBlock
    Set-ContractFunction 'Invoke-Cdp' {
        param($Socket, [string]$Method, $Parameters, [DateTime]$Deadline)
        throw [InvalidOperationException]::new('INJECTED_A03_NAVIGATION_TRANSPORT_FAILURE')
    }
    try {
        $fixture = New-ContractAuthorizationFixture
        $script:OwnedPreviewOrigin = $fixture.Origin
        $script:LastCompletedOwnedNavigation = $fixture.Previous
        $script:LastNavigationEvidence = [ordered]@{ handledCount = 1; acknowledgementSuccessCount = 1 }
        Assert-ContractThrows { Navigate-Cdp $fixture.Socket `
            ($fixture.Origin + '/cleanup-target.html') 'pure-run-1' 'pure-route-1' `
            ([DateTime]::UtcNow.AddSeconds(20)) } 'INJECTED_A03_NAVIGATION_TRANSPORT_FAILURE' `
            'navigation failure cleanup fixture'
        Assert-Contract ($null -eq $script:ActiveOwnedNavigation) `
            'active navigation authorization clears in finally'
        Assert-Contract ($null -eq $script:LastCompletedOwnedNavigation) `
            'failed navigation clears last completed navigation'
        Assert-Contract ($null -eq $script:LastNavigationEvidence) `
            'failed navigation clears retained success evidence'
    } finally {
        Set-ContractFunction 'Invoke-Cdp' $actualInvoke
        $script:ActiveOwnedNavigation = $null
        $script:LastCompletedOwnedNavigation = $null
        $script:LastNavigationEvidence = $null
    }
}

function Assert-RealFixturePath([string]$Path, [string]$ExpectedParent) {
    $fullPath = [IO.Path]::GetFullPath($Path)
    $parent = [IO.Path]::GetFullPath($ExpectedParent).TrimEnd('\')
    $prefix = $parent + '\'
    if (-not $fullPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw [InvalidOperationException]::new('Fixture path escaped the task-owned web-root child.')
    }
    $driveRoot = [IO.Path]::GetPathRoot($fullPath)
    $current = $driveRoot
    $relative = $fullPath.Substring($driveRoot.Length)
    foreach ($segment in @($relative.Split([char]'\', [StringSplitOptions]::RemoveEmptyEntries))) {
        $current = Join-Path $current $segment
        if (Test-Path -LiteralPath $current) {
            $attributes = [IO.File]::GetAttributes($current)
            if (($attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw [InvalidOperationException]::new('A fixture ancestor or fixture path is a reparse point.')
            }
        }
    }
}

function Write-OwnedFixtureFile([string]$Path, [string]$Text,
        [string]$FixtureRoot) {
    Assert-RealFixturePath $Path $FixtureRoot
    if (Test-Path -LiteralPath $Path) {
        throw [InvalidOperationException]::new('A task-owned navigation fixture path already exists.')
    }
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Text)
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write,
        [IO.FileShare]::None)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    $digest = Get-ContractFileSha256 $Path
    if ($digest -cne (Get-ContractSha256 $bytes)) {
        throw [InvalidOperationException]::new('A task-owned navigation fixture did not retain its exact bytes.')
    }
    return [pscustomobject]@{ Name = [IO.Path]::GetFileName($Path); Path = $Path;
        Bytes = [long]$bytes.Length; Sha256 = $digest }
}

function Get-RealBrowserOwnership($Context, $Session) {
    $browserId = 0
    $browserStartTicks = '0'
    $browserPathHash = ''
    $cdpPort = 0
    $sessionRecord = $null
    if ($null -ne $Session -and $Session.PSObject.Properties['Record'] -and
            $null -ne $Session.Record) {
        $sessionRecord = $Session.Record
    } elseif ($null -ne $Context -and $Context.PSObject.Properties['BrowserSessions'] -and
            $Context.BrowserSessions.Count -gt 0) {
        $sessionRecord = $Context.BrowserSessions[$Context.BrowserSessions.Count - 1]
    }
    if ($null -ne $sessionRecord) {
        if ($sessionRecord.PSObject.Properties['ProcessId']) {
            $browserId = [int]$sessionRecord.ProcessId
        }
        if ($sessionRecord.PSObject.Properties['ProcessStartTicks']) {
            $browserStartTicks = [string]$sessionRecord.ProcessStartTicks
        }
        if ($sessionRecord.PSObject.Properties['BrowserPath'] -and
                -not [String]::IsNullOrWhiteSpace([string]$sessionRecord.BrowserPath)) {
            $browserPathHash = Get-ContractSha256 ([Text.Encoding]::UTF8.GetBytes(
                [string]$sessionRecord.BrowserPath))
        }
        if ($sessionRecord.PSObject.Properties['CdpPort']) {
            $cdpPort = [int]$sessionRecord.CdpPort
        }
    }
    $previewId = 0
    $previewStartTicks = '0'
    $previewPort = 0
    if ($null -ne $Context -and $Context.PSObject.Properties['Server'] -and
            $null -ne $Context.Server) {
        $previewId = [int]$Context.Server.ProcessId
        $previewStartTicks = [string]$Context.Server.ProcessStartTicks
        $previewPort = [int]$Context.Server.Port
    }
    $proven = $browserId -gt 0 -and [long]$browserStartTicks -gt 0 -and
        $cdpPort -gt 0 -and $previewId -gt 0 -and [long]$previewStartTicks -gt 0 -and
        $previewPort -gt 0 -and -not [String]::IsNullOrWhiteSpace($browserPathHash)
    return [ordered]@{
        ownershipProven = [bool]$proven
        browserProcessId = $browserId
        browserProcessStartTicks = $browserStartTicks
        browserExecutablePathSha256 = $browserPathHash
        cdpPort = $cdpPort
        previewProcessId = $previewId
        previewProcessStartTicks = $previewStartTicks
        previewPort = $previewPort
    }
}

function Invoke-OwnedMouseClick($Session, [double]$X, [double]$Y,
        [DateTime]$Deadline) {
    [void](Invoke-Cdp $Session.Socket 'Input.dispatchMouseEvent' `
        @{ type = 'mouseMoved'; x = $X; y = $Y } $Deadline)
    [void](Invoke-Cdp $Session.Socket 'Input.dispatchMouseEvent' `
        @{ type = 'mousePressed'; x = $X; y = $Y; button = 'left'; clickCount = 1 } $Deadline)
    [void](Invoke-Cdp $Session.Socket 'Input.dispatchMouseEvent' `
        @{ type = 'mouseReleased'; x = $X; y = $Y; button = 'left'; clickCount = 1 } $Deadline)
}

function Get-SanitizedFailureCode([string]$Message) {
    if ([String]::IsNullOrWhiteSpace($Message)) { return 'UNEXPECTED_FAILURE' }
    if ($Message -match 'A03_NAVIGATION_DIALOG_REJECTED:type=alert;') { return 'UNSUPPORTED_ALERT_REJECTED' }
    if ($Message -match 'A03_NAVIGATION_DIALOG_REJECTED:type=beforeunload;') { return 'BEFOREUNLOAD_REJECTED' }
    if ($Message -match 'Timed out waiting for the exact owned navigation URL to complete\.') {
        return 'EXACT_DESTINATION_NOT_REACHED'
    }
    $safe = [Text.RegularExpressions.Regex]::Replace($Message,
        '(?i)(?<![A-Z0-9])[A-Z]:[\\/][^;\r\n ]+', '<path>')
    $safe = [Text.RegularExpressions.Regex]::Replace($safe, 'https?://[^;\s]+', '<url>')
    $safe = [Text.RegularExpressions.Regex]::Replace($safe, '[\x00-\x1F\x7F]', ' ')
    if ($safe.Length -gt 300) { $safe = $safe.Substring(0, 300) }
    return $safe
}

function Invoke-A03RealLane([string]$LaneName, [string]$SourceMode,
        [string]$FixtureBaseUrl, [string]$TargetName) {
    $context = $null
    $session = $null
    $cleanupResult = $null
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $operationMs = $null
    $cleanupMs = $null
    $shutdownMs = $null
    $shutdownStatus = 'NOT_PROVEN'
    $operationStatus = 'FAIL'
    $failureCode = ''
    $expectedRejected = $false
    $dialogEvidence = $null
    $targetObservation = $null
    $sourceObservation = $null
    $ownership = [ordered]@{}
    $cleanupSuccess = $false
    $cleanupErrorsCount = 0
    $runIdHash = ''
    $routeIdHash = ''
    $operationErrorText = ''
    $ownershipCaptureDiagnostic = ''
    $cleanupDiagnostic = ''
    $cleanupDiagnostics = New-Object Collections.Generic.List[string]
    try {
        $context = New-VerifierRunContext $script:RepositoryRoot ''
        $baseUrl = [string](Start-VerifierOwnedPreview $context $script:PreviewScriptPath 30 $false)
        $script:OwnedPreviewOrigin = [string](Get-OwnedLoopbackOrigin $baseUrl)
        if ([String]::IsNullOrWhiteSpace($script:OwnedPreviewOrigin)) {
            Throw-VerifierInfrastructure 'Owned preview did not expose a loopback origin.'
        }
        $initialUrl = $baseUrl.TrimEnd('/') + '/' + $FixtureBaseUrl.TrimStart('/') +
            '/source.html?mode=' + [Uri]::EscapeDataString($SourceMode)
        $session = New-VerifierBrowserSession $context ('a03-navigation-' + $LaneName) `
            'about:blank' '' 30 $false
        $ownership = Get-RealBrowserOwnership $context $session
        if (-not [bool]$ownership.ownershipProven) {
            throw [InvalidOperationException]::new('BROWSER_AND_PREVIEW_PROCESS_OWNERSHIP_NOT_PROVEN')
        }
        $runIdHash = Get-ContractSha256 ([Text.Encoding]::UTF8.GetBytes([string]$context.RunId))
        $routeIdHash = Get-ContractSha256 ([Text.Encoding]::UTF8.GetBytes([string]$session.RouteId))
        $script:NextCdpId = 1
        $script:LastCompletedOwnedNavigation = $null
        $script:ActiveOwnedNavigation = $null
        $script:LastNavigationEvidence = $null
        $script:LastNavigationDialogRejection = $null
        $script:OwnedPreviewOrigin = [string](Get-OwnedLoopbackOrigin $baseUrl)
        $deadline = [DateTime]::UtcNow.AddSeconds(12)
        [void](Invoke-Cdp $session.Socket 'Page.enable' @{} $deadline)
        [void](Invoke-Cdp $session.Socket 'Runtime.enable' @{} $deadline)
        [void](Navigate-Cdp $session.Socket $initialUrl $context.RunId $session.RouteId $deadline)

        if ($LaneName -eq 'alert') {
            $buttonExpression = @'
(()=>{const e=document.getElementById('arm');if(!e)return null;const r=e.getBoundingClientRect();return {count:1,x:r.left+r.width/2,y:r.top+r.height/2,width:r.width,height:r.height,visible:r.width>0&&r.height>0,top:document.elementFromPoint(r.left+r.width/2,r.top+r.height/2)===e};})()
'@
            $button = Evaluate-Cdp $session.Socket $buttonExpression $deadline
            if ($null -eq $button -or -not $button.visible -or -not $button.top -or
                    [double]$button.width -le 0 -or [double]$button.height -le 0) {
                throw [InvalidOperationException]::new('ALERT_BUTTON_NOT_VISIBLE_AND_HITTESTABLE')
            }
            try {
                Invoke-OwnedMouseClick $session ([double]$button.x) ([double]$button.y) $deadline
            } catch {
                $operationErrorText = Get-ContractErrorText $_
                if ($operationErrorText -notmatch 'A03_NAVIGATION_DIALOG_REJECTED:type=alert;') { throw }
                $dialogEvidence = $script:LastNavigationDialogRejection
                if ($null -eq $dialogEvidence -or [string]$dialogEvidence.type -cne 'alert' -or
                        $dialogEvidence.hasBrowserHandler -isnot [bool] -or -not $dialogEvidence.hasBrowserHandler -or
                        [string]$dialogEvidence.reason -cne 'outside-owned-beforeunload-scope') {
                    throw [InvalidOperationException]::new('UNSUPPORTED_ALERT_REJECTION_EVIDENCE_MISMATCH')
                }
                $trustedEventLog = @($script:CdpEvents | Where-Object {
                    [string]$_ -match 'console: A03_TRUSTED_MOUSE_EVENT'
                })
                if ($trustedEventLog.Count -lt 1) {
                    throw [InvalidOperationException]::new('ALERT_FIXTURE_DID_NOT_PROVE_TRUSTED_MOUSE_HANDLER')
                }
                $expectedRejected = $true
                $operationStatus = 'PASS_EXPECTED_REJECTION'
                $failureCode = 'UNSUPPORTED_ALERT_REJECTED'
            }
            if (-not $expectedRejected) {
                throw [InvalidOperationException]::new('UNSUPPORTED_ALERT_WAS_NOT_REJECTED')
            }
        } else {
            $buttonExpression = @'
(()=>{const e=document.getElementById('arm');if(!e)return null;const r=e.getBoundingClientRect();return {count:1,x:r.left+r.width/2,y:r.top+r.height/2,width:r.width,height:r.height,visible:r.width>0&&r.height>0,top:document.elementFromPoint(r.left+r.width/2,r.top+r.height/2)===e};})()
'@
            $button = Evaluate-Cdp $session.Socket $buttonExpression $deadline
            if ($null -eq $button -or -not $button.visible -or -not $button.top -or
                    [double]$button.width -le 0 -or [double]$button.height -le 0) {
                throw [InvalidOperationException]::new('NAVIGATION_BUTTON_NOT_VISIBLE_AND_HITTESTABLE')
            }
            Invoke-OwnedMouseClick $session ([double]$button.x) ([double]$button.y) $deadline
            $sourceObservation = Evaluate-Cdp $session.Socket `
                '({trusted:window.__trusted===true,clicked:window.__clicked===true,href:location.href,changed:window.__changedSource||""})' `
                $deadline
            if ($sourceObservation.trusted -isnot [bool] -or -not $sourceObservation.trusted -or
                    $sourceObservation.clicked -isnot [bool] -or -not $sourceObservation.clicked) {
                throw [InvalidOperationException]::new('REAL_TRUSTED_MOUSE_ACTIVATION_NOT_OBSERVED')
            }
            if ($LaneName -eq 'source-mismatch') {
                $changedPath = '/' + $FixtureBaseUrl.Trim('/') + '/changed-source.html'
                $sourceUri = [Uri][string]$sourceObservation.href
                if ($sourceUri.AbsolutePath -cne $changedPath -or
                        [string]$sourceObservation.changed -cne [string]$sourceObservation.href) {
                    throw [InvalidOperationException]::new('SOURCE_CHANGE_FIXTURE_DID_NOT_CHANGE_EXACT_SOURCE_URL')
                }
                try {
                    [void](Navigate-Cdp $session.Socket ($baseUrl.TrimEnd('/') + '/' +
                        $FixtureBaseUrl.Trim('/') + '/' + $TargetName) $context.RunId $session.RouteId $deadline)
                } catch {
                    $operationErrorText = Get-ContractErrorText $_
                    if ($operationErrorText -notmatch 'A03_NAVIGATION_DIALOG_REJECTED:type=beforeunload;') { throw }
                    $dialogEvidence = $script:LastNavigationDialogRejection
                    if ($null -eq $dialogEvidence -or [string]$dialogEvidence.type -cne 'beforeunload' -or
                            [string]$dialogEvidence.reason -cne 'outside-owned-beforeunload-scope') {
                        throw [InvalidOperationException]::new('WRONG_SOURCE_BEFOREUNLOAD_REJECTION_EVIDENCE_MISMATCH')
                    }
                    $expectedRejected = $true
                    $operationStatus = 'PASS_EXPECTED_REJECTION'
                    $failureCode = 'WRONG_SOURCE_BEFOREUNLOAD_REJECTED'
                }
                if (-not $expectedRejected) {
                    throw [InvalidOperationException]::new('WRONG_SOURCE_BEFOREUNLOAD_WAS_NOT_REJECTED')
                }
            } elseif ($LaneName -eq 'wrong-destination') {
                $navigationReturned = $false
                try {
                    [void](Navigate-Cdp $session.Socket ($baseUrl.TrimEnd('/') + '/' +
                        $FixtureBaseUrl.Trim('/') + '/redirect.html') $context.RunId $session.RouteId `
                        ([DateTime]::UtcNow.AddSeconds(5)))
                    $navigationReturned = $true
                } catch {
                    $operationErrorText = Get-ContractErrorText $_
                    if ($operationErrorText -notmatch 'Timed out waiting for the exact owned navigation URL to complete\.') {
                        throw
                    }
                    $failureCode = 'EXACT_DESTINATION_NOT_REACHED'
                    $expectedRejected = $true
                }
                if ($navigationReturned) {
                    throw [InvalidOperationException]::new('MARKER_PRESERVING_WRONG_DESTINATION_WAS_ACCEPTED')
                }
                $targetObservation = Evaluate-Cdp $session.Socket `
                    '({ready:document.readyState,url:location.href,fixtureApplied:window.__wrongDestinationFixtureApplied===true})' `
                    ([DateTime]::UtcNow.AddSeconds(4))
                $wrongUri = [Uri][string]$targetObservation.url
                $requiredPath = '/' + $FixtureBaseUrl.Trim('/') + '/wrong.html'
                $queryValues = @{}
                foreach ($pair in @($wrongUri.Query.TrimStart('?') -split '&')) {
                    if ([String]::IsNullOrWhiteSpace([string]$pair)) { continue }
                    $parts = @(([string]$pair).Split([char[]]@('='), 2))
                    $key = [Uri]::UnescapeDataString(([string]$parts[0]).Replace('+', ' '))
                    $value = if ($parts.Count -gt 1) {
                        [Uri]::UnescapeDataString(([string]$parts[1]).Replace('+', ' '))
                    } else { '' }
                    $queryValues[$key] = $value
                }
                if ([string]$targetObservation.ready -cne 'complete' -or
                        $targetObservation.fixtureApplied -isnot [bool] -or
                        -not $targetObservation.fixtureApplied -or
                        $wrongUri.AbsolutePath -cne $requiredPath -or
                        [String]::IsNullOrWhiteSpace([string]$queryValues['tsjVerifierNavigation']) -or
                        [string]$queryValues['tsjVerifierRun'] -cne [string]$context.RunId -or
                        [string]$queryValues['tsjVerifierRoute'] -cne [string]$session.RouteId) {
                    throw [InvalidOperationException]::new('WRONG_DESTINATION_DID_NOT_PRESERVE_NAVIGATION_LINEAGE')
                }
                $operationStatus = 'PASS_EXPECTED_REJECTION'
            } else {
                $targetUrl = $baseUrl.TrimEnd('/') + '/' + $FixtureBaseUrl.Trim('/') + '/' + $TargetName
                $navigationUrl = Navigate-Cdp $session.Socket $targetUrl $context.RunId $session.RouteId $deadline
                $targetObservation = Evaluate-Cdp $session.Socket `
                    '({ready:document.readyState,url:location.href})' ([DateTime]::UtcNow.AddSeconds(5))
                if (-not (Test-OwnedNavigationReadiness $targetObservation $navigationUrl)) {
                    throw [InvalidOperationException]::new('POSITIVE_OWNED_NAVIGATION_DID_NOT_REACH_EXACT_TARGET')
                }
                if ($null -eq $script:LastNavigationEvidence -or
                        [int]$script:LastNavigationEvidence.handledCount -ne 1 -or
                        [int]$script:LastNavigationEvidence.acknowledgementSuccessCount -ne 1) {
                    throw [InvalidOperationException]::new('POSITIVE_BEFOREUNLOAD_ACKNOWLEDGEMENT_NOT_PROVEN')
                }
                $dialogEvidence = [ordered]@{
                    handledCount = [int]$script:LastNavigationEvidence.handledCount
                    acknowledgementSuccessCount = [int]$script:LastNavigationEvidence.acknowledgementSuccessCount
                }
                $operationStatus = 'PASS'
                $failureCode = ''
            }
        }
    } catch {
        if ([String]::IsNullOrWhiteSpace($operationErrorText)) {
            $operationErrorText = Get-ContractErrorText $_
        }
        if ($operationStatus -notlike 'PASS*') {
            $operationStatus = 'FAIL'
            $failureCode = Get-SanitizedFailureCode $operationErrorText
        }
    } finally {
        $clock.Stop()
        $operationMs = [long]$clock.ElapsedMilliseconds
        if ($null -ne $context) {
            try {
                $ownership = Get-RealBrowserOwnership $context $session
                if (-not [bool]$ownership.ownershipProven) {
                    $ownershipCaptureDiagnostic = 'PROCESS_OWNERSHIP_NOT_PROVEN'
                    [void]$cleanupDiagnostics.Add($ownershipCaptureDiagnostic)
                }
            } catch {
                $ownershipCaptureDiagnostic = Get-SanitizedFailureCode (Get-ContractErrorText $_)
                [void]$cleanupDiagnostics.Add($ownershipCaptureDiagnostic)
                $ownership = [ordered]@{ ownershipProven = $false }
            }
            $cleanupClock = [Diagnostics.Stopwatch]::StartNew()
            try {
                $cleanupResult = Complete-VerifierRun $context
                $errorsValue = $null
                $hasErrorsField = $false
                if ($null -ne $cleanupResult -and $cleanupResult.PSObject.Properties['Errors']) {
                    $hasErrorsField = $true
                    $errorsValue = $cleanupResult.Errors
                }
                $cleanupErrorsCount = Get-ContractCollectionCount $errorsValue
                $hasCleanupState = $context.PSObject.Properties['CleanupState'] -and
                    [string]$context.CleanupState -ceq 'complete'
                $cleanupSuccess = $null -ne $cleanupResult -and [bool]$cleanupResult.Success -and
                    $hasErrorsField -and $cleanupErrorsCount -eq 0 -and $hasCleanupState
                if (-not $cleanupSuccess) {
                    if (-not $hasErrorsField) {
                        [void]$cleanupDiagnostics.Add('CLEANUP_ERRORS_FIELD_NOT_PROVEN')
                    } elseif ($cleanupErrorsCount -gt 0) {
                        [void]$cleanupDiagnostics.Add('COMPLETE_VERIFIER_REPORTED_CLEANUP_ERRORS')
                    } elseif (-not $hasCleanupState) {
                        [void]$cleanupDiagnostics.Add('VERIFIER_CLEANUP_STATE_NOT_COMPLETE')
                    } else {
                        [void]$cleanupDiagnostics.Add('COMPLETE_VERIFIER_RUN_NOT_SUCCESSFUL')
                    }
                }
                $phaseTimings = $null
                if ($cleanupResult -and $cleanupResult.PSObject.Properties['ExtraPhaseTimings']) {
                    $phaseTimings = $cleanupResult.ExtraPhaseTimings
                }
                try {
                    $shutdownEvidence = Get-OwnedShutdownTimingEvidence $phaseTimings
                    $shutdownStatus = [string]$shutdownEvidence.Status
                    $shutdownMs = $shutdownEvidence.TotalMs
                    if ($shutdownStatus -cne 'PASS') {
                        [void]$cleanupDiagnostics.Add(('SHUTDOWN_TIMING_' + $shutdownStatus))
                    }
                } catch {
                    $shutdownStatus = 'NOT_PROVEN'
                    $shutdownMs = $null
                    [void]$cleanupDiagnostics.Add('SHUTDOWN_TIMING_ADAPTER_FAILED')
                }
            } catch {
                $cleanupSuccess = $false
                $cleanupErrorsCount++
                [void]$cleanupDiagnostics.Add(('COMPLETE_VERIFIER_RUN_FAILED:' +
                    (Get-SanitizedFailureCode (Get-ContractErrorText $_))))
            } finally {
                $cleanupClock.Stop()
                $cleanupMs = [long]$cleanupClock.ElapsedMilliseconds
            }
            if ($cleanupMs -gt 120000) {
                $cleanupSuccess = $false
                [void]$cleanupDiagnostics.Add('CLEANUP_LIMIT_EXCEEDED')
            }
        } else {
            [void]$cleanupDiagnostics.Add('RUN_CONTEXT_NOT_CREATED')
        }
    }
    if (-not [String]::IsNullOrWhiteSpace($ownershipCaptureDiagnostic)) {
        $cleanupSuccess = $false
    }
    if ($shutdownStatus -cne 'PASS') {
        if (-not ($cleanupDiagnostics | Where-Object { [string]$_ -like 'SHUTDOWN_TIMING_*' })) {
            [void]$cleanupDiagnostics.Add(('SHUTDOWN_TIMING_' + $shutdownStatus))
        }
    }
    $cleanupDiagnostic = $cleanupDiagnostics.ToArray() -join '|'
    $ownershipProven = $ownership -and $ownership.Contains('ownershipProven') -and
        [bool]$ownership.ownershipProven
    $completedStatus = if ($operationStatus -like 'PASS*' -and $cleanupSuccess -and
            $shutdownStatus -ceq 'PASS' -and $ownershipProven) {
        $operationStatus
    } else { 'FAIL' }
    return [ordered]@{
        lane = $LaneName
        status = $completedStatus
        operationStatus = $operationStatus
        failureCode = $failureCode
        operationMs = $operationMs
        cleanupMs = $cleanupMs
        shutdownMs = $shutdownMs
        shutdownLimitMs = 15000
        shutdownStatus = $shutdownStatus
        cleanupStatus = if ($cleanupSuccess) { 'PASS' } else { 'FAIL_OR_NOT_PROVEN' }
        cleanupDiagnostic = $cleanupDiagnostic
        cleanupErrorsCount = $cleanupErrorsCount
        runIdSha256 = $runIdHash
        routeIdSha256 = $routeIdHash
        ownership = $ownership
        dialogEvidence = $dialogEvidence
        trustedClickObserved = if ($LaneName -eq 'alert') { $null -ne $dialogEvidence } `
            else { $null -ne $sourceObservation -and [bool]$sourceObservation.trusted }
        exactSourceChangeObserved = if ($LaneName -eq 'source-mismatch') { $null -ne $sourceObservation }
            else { $null }
        redirectedWrongUrlObserved = if ($LaneName -eq 'wrong-destination') { $null -ne $targetObservation }
            else { $null }
        operationDiagnostic = if ($operationStatus -notlike 'PASS*') {
            Get-SanitizedFailureCode $operationErrorText
        } else { '' }
    }
}

function Invoke-A03RealBrowserCanaries {
    $fixtures = New-Object Collections.Generic.List[object]
    $lanes = New-Object Collections.Generic.List[object]
    $fixtureCleanupStatus = 'NOT_RUN'
    $fixtureCleanupMs = $null
    $fixtureCleanupDiagnostic = ''
    try {
    $warRoot = Join-Path $script:RepositoryRoot 'war'
    if (-not (Test-Path -LiteralPath $warRoot -PathType Container)) {
        throw [IO.DirectoryNotFoundException]::new('Owned A03 preview web root is missing.')
    }
    $runName = 'a03-owned-navigation-' + [Guid]::NewGuid().ToString('N')
    $fixtureRoot = Join-Path $warRoot $runName
    Assert-RealFixturePath $fixtureRoot $warRoot
    if (Test-Path -LiteralPath $fixtureRoot) {
        throw [InvalidOperationException]::new('Unique A03 navigation fixture directory already exists.')
    }
    New-Item -ItemType Directory -Path $fixtureRoot -ErrorAction Stop | Out-Null
    Assert-RealFixturePath $fixtureRoot $warRoot

    $sourceHtml = @'
<!doctype html><html><head><meta charset="utf-8"><title>A03 owned navigation source</title>
<style>body{font:16px sans-serif;margin:32px}button{width:260px;height:64px;font-size:18px}</style></head>
<body><main><h1>Owned navigation canary</h1><button id="arm" type="button">Arm navigation</button></main>
<script>
(()=>{const mode=new URLSearchParams(location.search).get('mode');window.__trusted=false;window.__clicked=false;window.__changedSource='';
document.getElementById('arm').addEventListener('click',event=>{window.__trusted=event.isTrusted;window.__clicked=true;if(!event.isTrusted)return;
if(mode==='alert'){console.info('A03_TRUSTED_MOUSE_EVENT');window.alert('unsupported-owned-dialog');return;}
if(mode==='source-mismatch'){const changed=new URL('changed-source.html',location.href);changed.search=location.search;history.replaceState(null,'',changed.href);window.__changedSource=location.href;}
window.onbeforeunload=()=> 'unsaved changes';
});})();
</script></body></html>
'@
    $targetHtml = @'
<!doctype html><html><head><meta charset="utf-8"><title>A03 target</title></head><body><main id="target">owned destination</main></body></html>
'@
    $redirectHtml = @'
<!doctype html><html><head><meta charset="utf-8"><title>A03 redirect</title></head><body>
<script>(()=>{const destination=new URL('wrong.html',location.href);destination.search=location.search;const beforeComplete=document.readyState!=='complete';history.replaceState(null,'',destination.href);window.__wrongDestinationFixtureApplied=beforeComplete&&location.href===destination.href;})();</script>
</body></html>
'@
    $wrongHtml = @'
<!doctype html><html><head><meta charset="utf-8"><title>A03 wrong destination</title></head><body><main id="wrong">wrong destination</main></body></html>
'@
        foreach ($entry in @(
                [pscustomobject]@{ Name = 'source.html'; Text = $sourceHtml },
                [pscustomobject]@{ Name = 'target.html'; Text = $targetHtml },
                [pscustomobject]@{ Name = 'redirect.html'; Text = $redirectHtml },
                [pscustomobject]@{ Name = 'wrong.html'; Text = $wrongHtml })) {
            [void]$fixtures.Add((Write-OwnedFixtureFile (Join-Path $fixtureRoot $entry.Name) `
                [string]$entry.Text $fixtureRoot))
        }

        foreach ($case in @(
                [pscustomobject]@{ Name = 'positive'; Mode = 'positive'; Target = 'target.html' },
                [pscustomobject]@{ Name = 'alert'; Mode = 'alert'; Target = '' },
                [pscustomobject]@{ Name = 'source-mismatch'; Mode = 'source-mismatch'; Target = 'target.html' },
                [pscustomobject]@{ Name = 'wrong-destination'; Mode = 'wrong-destination'; Target = 'redirect.html' })) {
            $lane = Invoke-A03RealLane $case.Name $case.Mode $runName $case.Target
            [void]$lanes.Add($lane)
            $laneReceipt = [ordered]@{
                protocol = 'a03-owned-navigation-lane-v1'
                lane = [string]$lane.lane
                status = [string]$lane.status
                operationStatus = [string]$lane.operationStatus
                failureCode = [string]$lane.failureCode
                operationMs = $lane.operationMs
                cleanupMs = $lane.cleanupMs
                shutdownMs = $lane.shutdownMs
                shutdownLimitMs = [int]$lane.shutdownLimitMs
                shutdownStatus = [string]$lane.shutdownStatus
                cleanupStatus = [string]$lane.cleanupStatus
                cleanupDiagnostic = [string]$lane.cleanupDiagnostic
                cleanupErrorsCount = [int]$lane.cleanupErrorsCount
                ownership = $lane.ownership
                dialogEvidence = $lane.dialogEvidence
                diagnostic = [string]$lane.operationDiagnostic
            }
            [Console]::Out.WriteLine('A03_NAVIGATION_LANE_RECEIPT ' +
                ($laneReceipt | ConvertTo-Json -Compress -Depth 10))
            if ([string]$lane.cleanupStatus -cne 'PASS') { break }
        }

        $allCleanupProven = $lanes.Count -eq 4
        foreach ($lane in $lanes) {
            if ([string]$lane.cleanupStatus -cne 'PASS') { $allCleanupProven = $false }
        }
        if (-not $allCleanupProven) { $fixtureCleanupStatus = 'NOT_RUN_CLEANUP_UNPROVEN' }
        if ($allCleanupProven) {
            $fixtureClock = [Diagnostics.Stopwatch]::StartNew()
            try {
                Assert-RealFixturePath $fixtureRoot $warRoot
                $children = @(Get-ChildItem -LiteralPath $fixtureRoot -Force)
                $expectedNames = @($fixtures | ForEach-Object { [string]$_.Name } | Sort-Object)
                $actualNames = @($children | ForEach-Object { [string]$_.Name } | Sort-Object)
                if (($actualNames -join "`n") -cne ($expectedNames -join "`n")) {
                    throw [InvalidOperationException]::new('Owned fixture directory contains an unexpected or missing path.')
                }
                foreach ($fixture in $fixtures) {
                    Assert-RealFixturePath ([string]$fixture.Path) $fixtureRoot
                    if ((Get-ContractFileSha256 ([string]$fixture.Path)) -cne [string]$fixture.Sha256) {
                        throw [InvalidOperationException]::new('Owned fixture bytes changed before exact removal.')
                    }
                }
                foreach ($fixture in $fixtures) {
                    Assert-RealFixturePath ([string]$fixture.Path) $fixtureRoot
                    if ((Get-ContractFileSha256 ([string]$fixture.Path)) -cne [string]$fixture.Sha256) {
                        throw [InvalidOperationException]::new('Owned fixture changed between verification and removal.')
                    }
                    Remove-Item -LiteralPath ([string]$fixture.Path) -Force -ErrorAction Stop
                    if (Test-Path -LiteralPath ([string]$fixture.Path)) {
                        throw [InvalidOperationException]::new('Exact owned fixture file remained after removal.')
                    }
                }
                if (@(Get-ChildItem -LiteralPath $fixtureRoot -Force).Count -ne 0) {
                    throw [InvalidOperationException]::new('Owned navigation fixture directory is not empty after exact file removal.')
                }
                Assert-RealFixturePath $fixtureRoot $warRoot
                Remove-Item -LiteralPath $fixtureRoot -Force -ErrorAction Stop
                if (Test-Path -LiteralPath $fixtureRoot) {
                    throw [InvalidOperationException]::new('Exact owned navigation fixture directory remained after removal.')
                }
                $fixtureCleanupStatus = 'PASS'
            } catch {
                $fixtureCleanupStatus = 'FAIL_RETAINED_OR_PARTIAL'
                $fixtureCleanupDiagnostic = Get-SanitizedFailureCode (Get-ContractErrorText $_)
            } finally {
                $fixtureClock.Stop()
                $fixtureCleanupMs = [long]$fixtureClock.ElapsedMilliseconds
            }
        }

        $allLanesPass = $lanes.Count -eq 4
        foreach ($lane in $lanes) {
            if ([string]$lane.status -notlike 'PASS*') { $allLanesPass = $false }
        }
        $finalSourceFileHashes = Get-ContractSourceFileHashes
        $sourceInputsUnchanged = $true
        foreach ($name in $script:LoadedSourceFileHashes.Keys) {
            if ([string]$finalSourceFileHashes[$name] -cne [string]$script:LoadedSourceFileHashes[$name]) {
                $sourceInputsUnchanged = $false
            }
        }
        $allPass = $allLanesPass -and $fixtureCleanupStatus -ceq 'PASS' -and $sourceInputsUnchanged
        $report = [ordered]@{
            protocol = 'a03-owned-navigation-canary-v1'
            status = if ($allPass) { 'PASS' } else { 'FAIL' }
            mode = 'real-owned-browser'
            assertions = [int]$script:ContractAssertions
            sourceSha256Before = $script:LoadedSourceFileHashes
            sourceSha256After = $finalSourceFileHashes
            sourceInputsUnchanged = [bool]$sourceInputsUnchanged
            functionSha256 = $script:LoadedFunctionHashes
            fixtures = @($fixtures.ToArray() | ForEach-Object { [ordered]@{
                name = [string]$_.Name; bytes = [long]$_.Bytes; sha256 = [string]$_.Sha256
            } })
            fixtureCleanupStatus = $fixtureCleanupStatus
            fixtureCleanupMs = $fixtureCleanupMs
            fixtureCleanupDiagnostic = $fixtureCleanupDiagnostic
            lanes = $lanes.ToArray()
            routeTimeoutSeconds = 12
            wrongDestinationTimeoutSeconds = 5
            cleanupLimitMs = 120000
            realMouse = 'Input.dispatchMouseEvent mouseMoved/mousePressed/mouseReleased at hit-tested visible button bounds'
            layoutQualification = $false
            electricalQualification = $false
        }
        return $report
    } catch {
        $fixtureCleanupDiagnostic = Get-SanitizedFailureCode (Get-ContractErrorText $_)
        $finalSourceFileHashes = $null
        try { $finalSourceFileHashes = Get-ContractSourceFileHashes } catch { }
        $report = [ordered]@{
            protocol = 'a03-owned-navigation-canary-v1'
            status = 'FAIL'
            mode = 'real-owned-browser'
            assertions = [int]$script:ContractAssertions
            sourceSha256Before = $script:LoadedSourceFileHashes
            sourceSha256After = $finalSourceFileHashes
            sourceInputsUnchanged = $false
            functionSha256 = $script:LoadedFunctionHashes
            fixtures = @($fixtures.ToArray() | ForEach-Object { [ordered]@{
                name = [string]$_.Name; bytes = [long]$_.Bytes; sha256 = [string]$_.Sha256
            } })
            fixtureCleanupStatus = if ($fixtureCleanupStatus -eq 'NOT_RUN') { 'NOT_PROVEN' } else { $fixtureCleanupStatus }
            fixtureCleanupMs = $fixtureCleanupMs
            fixtureCleanupDiagnostic = [string]$fixtureCleanupDiagnostic
            lanes = $lanes.ToArray()
            routeTimeoutSeconds = 12
            wrongDestinationTimeoutSeconds = 5
            cleanupLimitMs = 120000
            layoutQualification = $false
            electricalQualification = $false
        }
        return $report
    }
}

try {
    $loadedA03 = Import-A03NavigationFunctions
    foreach ($functionSource in $loadedA03.FunctionSources) {
        Invoke-Expression $functionSource
    }
    $script:LoadedFunctionHashes = $loadedA03.FunctionHashes
    if ($RealBrowser) {
        $report = Invoke-A03RealBrowserCanaries
        Write-Output ('A03_NAVIGATION_CANARY_REPORT ' +
            ($report | ConvertTo-Json -Compress -Depth 16))
        if ([string]$report.status -cne 'PASS') { exit 2 }
        exit 0
    }
    Test-PureNavigationContracts
    Write-Output ('PASS: A03 owned navigation contracts assertions=' +
        [string]$script:ContractAssertions)
    exit 0
} catch {
    $diagnostic = Get-SanitizedFailureCode (Get-ContractErrorText $_)
    Write-Output ('FAIL: A03 owned navigation contracts assertions=' +
        [string]$script:ContractAssertions + '; reason=' + $diagnostic)
    exit 2
}

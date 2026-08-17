[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8899',
    [ValidateRange(10, 300)]
    [int]$TimeoutSeconds = 90,
    [string]$BrowserPath = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
    [int[]]$Seeds = @(0, 2, 3),
    [string]$Route,
    [switch]$NormalPlayer,
    [switch]$Diode,
    [switch]$DiodeShort,
    [switch]$DiodeNormalPlayer,
    [switch]$Parallel,
    [switch]$ParallelNormalPlayer,
    [switch]$LedParts,
    [switch]$LedNormalPlayer,
    [switch]$WrongRepair,
    [switch]$WrongRepairNormalPlayer,
    [switch]$StressDamage,
    [switch]$StressDamageNormalPlayer,
    [switch]$QuickPlay,
    [switch]$Layout,
    [switch]$Architecture,
    [int]$PlayerSeed = 3,
    [string]$EvidenceDirectory,
    [switch]$PersistentPreviewEvidence
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$CdpReceiveTimeoutMilliseconds = 30000
$CdpSendTimeoutMilliseconds = 5000

function sendCdp($socket, [int]$id, [string]$method, $parameters) {
    $message = @{ id = $id; method = $method; params = $parameters } | ConvertTo-Json -Compress -Depth 8
    $bytes = [Text.Encoding]::UTF8.GetBytes($message)
    $sendTimeout = New-Object Threading.CancellationTokenSource $CdpSendTimeoutMilliseconds
    try {
        $socket.SendAsync((New-Object ArraySegment[byte] -ArgumentList (,$bytes)),
            [Net.WebSockets.WebSocketMessageType]::Text, $true,
            $sendTimeout.Token).GetAwaiter().GetResult()
    } finally {
        $sendTimeout.Dispose()
    }
}

function getEdgeProcessSnapshot() {
    try {
        return @(Get-CimInstance Win32_Process -Filter "Name = 'msedge.exe'" -ErrorAction Stop)
    } catch {
        throw "Could not query Edge processes: $($_.Exception.Message)"
    }
}

function getEdgeProcessesForProfile($browser, [string]$profile) {
    if (-not $profile) { throw 'Temporary browser profile was not supplied' }
    $processes = @(getEdgeProcessSnapshot)
    $unquotedMarker = '--user-data-dir=' + $profile
    $quotedMarker = '--user-data-dir="' + $profile + '"'
    $ids = @{}
    $pending = New-Object Collections.Generic.Queue[int]
    if ($null -ne $browser) {
        try { $pending.Enqueue([int]$browser.Id) } catch {
            throw "Could not read browser process ID: $($_.Exception.Message)"
        }
    }
    foreach ($process in $processes) {
        $commandLine = [string]$process.CommandLine
        if ($commandLine -and
                ($commandLine.IndexOf($unquotedMarker, [StringComparison]::OrdinalIgnoreCase) -ge 0 -or
                 $commandLine.IndexOf($quotedMarker, [StringComparison]::OrdinalIgnoreCase) -ge 0)) {
            $pending.Enqueue([int]$process.ProcessId)
        }
    }
    while ($pending.Count -gt 0) {
        $id = $pending.Dequeue()
        if ($ids.ContainsKey($id)) { continue }
        $ids[$id] = $true
        foreach ($process in $processes) {
            if ([int]$process.ParentProcessId -eq $id) {
                $pending.Enqueue([int]$process.ProcessId)
            }
        }
    }
    return @($processes | Where-Object { $ids.ContainsKey([int]$_.ProcessId) })
}

function cleanupBrowser($browser, $socket, [string]$profile) {
    if ($null -eq $browser -and $null -eq $socket -and
            -not (Test-Path -LiteralPath $profile -ErrorAction Stop)) { return }
    if ($null -ne $socket) {
        try { $socket.Abort() } catch { throw "Could not close browser debugging socket: $($_.Exception.Message)" }
        try { $socket.Dispose() } catch { throw "Could not dispose browser debugging socket: $($_.Exception.Message)" }
    }
    if (-not $profile) { throw 'Temporary browser profile was not supplied' }
    $processDeadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        $running = @(getEdgeProcessesForProfile $browser $profile)
        if ($running.Count -eq 0) { break }
        foreach ($process in $running) {
            try {
                Stop-Process -Id ([int]$process.ProcessId) -Force -ErrorAction Stop
            } catch {
                $stillPresent = @(getEdgeProcessesForProfile $browser $profile |
                    Where-Object { [int]$_.ProcessId -eq [int]$process.ProcessId })
                if ($stillPresent.Count -ne 0) {
                    throw "Could not terminate Edge process $($process.ProcessId): $($_.Exception.Message)"
                }
            }
        }
        if ([DateTime]::UtcNow -ge $processDeadline) {
            throw "Timed out terminating Edge descendants for temporary profile '$profile'"
        }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $processDeadline)
    $remaining = @(getEdgeProcessesForProfile $browser $profile)
    if ($remaining.Count -ne 0) {
        throw "Timed out terminating Edge descendants for temporary profile '$profile'"
    }
    $removeError = $null
    for ($attempt = 0; $attempt -lt 25; $attempt++) {
        if (-not (Test-Path -LiteralPath $profile -ErrorAction Stop)) { break }
        try {
            Remove-Item -LiteralPath $profile -Recurse -Force -ErrorAction Stop
            $removeError = $null
        } catch {
            $removeError = $_.Exception.Message
            if ($attempt -eq 24) { break }
            Start-Sleep -Milliseconds 200
        }
    }
    if (Test-Path -LiteralPath $profile -ErrorAction Stop) {
        throw "Could not remove temporary browser profile '$profile': $removeError"
    }
}

function receiveCdp($socket, [int]$wantedId, [ref]$failures) {
    while ($true) {
        $stream = New-Object IO.MemoryStream
        do {
            $buffer = New-Object byte[] 65536
            $receiveTimeout = New-Object Threading.CancellationTokenSource $CdpReceiveTimeoutMilliseconds
            try {
                $result = $socket.ReceiveAsync((New-Object ArraySegment[byte] -ArgumentList (,$buffer)),
                    $receiveTimeout.Token).GetAwaiter().GetResult()
            } finally {
                $receiveTimeout.Dispose()
            }
            $stream.Write($buffer, 0, $result.Count)
        } while (-not $result.EndOfMessage)
        $message = [Text.Encoding]::UTF8.GetString($stream.ToArray()) | ConvertFrom-Json
        $method = if ($message.PSObject.Properties['method']) { $message.method } else { $null }
        if ($method -eq 'Runtime.exceptionThrown') {
            $details = $message.params.exceptionDetails
            $description = if ($details.exception -and $details.exception.description) {
                $details.exception.description
            } else { $details.text }
            $failures.Value += 'JavaScript exception: ' + $description
        }
        if ($method -eq 'Runtime.consoleAPICalled') {
            $text = ($message.params.args | ForEach-Object { $_.value }) -join ' '
            if ($text -match '(?i)verification failed|generated board verification failed|pcb_generator_failure|parallel_generator_failure|uncaught|exception') {
                $failures.Value += 'Console failure: ' + $text
            }
        }
        if ($message.PSObject.Properties['id'] -and $message.id -eq $wantedId) { return $message }
    }
}

function invokeCdp($socket, [ref]$nextId, [string]$method, $parameters, [ref]$failures) {
    $id = $nextId.Value
    $nextId.Value++
    [void](sendCdp $socket $id $method $parameters)
    return receiveCdp $socket $id $failures
}

function navigateAndWaitForDocument($socket, [ref]$nextId, [string]$url,
        [DateTime]$deadline, [ref]$failures) {
    $marker = [Guid]::NewGuid().ToString('N')
    $separator = if ($url.IndexOf('?') -ge 0) { '&' } else { '?' }
    $navigationUrl = $url + $separator + 'tsjVerifierNavigation=' + $marker
    [void](invokeCdp $socket $nextId 'Page.navigate' @{ url = $navigationUrl } $failures)
    $escapedMarker = $marker.Replace("'", "\\'")
    waitForCdp $socket $nextId "location.href.includes('$escapedMarker')&&document.readyState==='complete'" $deadline $failures 'synchronized page navigation'
    return evaluateCdp $socket $nextId 'performance.timeOrigin' $failures
}

function verifyRoute([string]$name, [string]$url, [string]$expected, [int]$debugPort,
        [string]$expectedComplaint = '') {
    $profile = Join-Path $env:TEMP ("tsj-browser-" + [Guid]::NewGuid().ToString('N'))
    $arguments = @('--headless=new', '--disable-gpu', '--no-first-run', '--disable-sync',
        '--window-size=1440,1000', "--user-data-dir=$profile", "--remote-debugging-port=$debugPort", 'about:blank')
    $browser = Start-Process -FilePath $BrowserPath -ArgumentList $arguments -PassThru -WindowStyle Hidden
    $socket = $null
    $success = $false
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        do {
            Start-Sleep -Milliseconds 200
            try {
                $targets = Invoke-RestMethod "http://127.0.0.1:$debugPort/json/list" -TimeoutSec 2
                $target = $targets | Where-Object { $_.type -eq 'page' } | Select-Object -First 1
            } catch { $target = $null }
        } while ($null -eq $target -and [DateTime]::UtcNow -lt $deadline)
        if ($null -eq $target) { throw 'browser target did not become available' }

        $socket = New-Object Net.WebSockets.ClientWebSocket
        $socket.ConnectAsync([Uri]$target.webSocketDebuggerUrl,
            [Threading.CancellationToken]::None).GetAwaiter().GetResult()
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures))
        if ($expectedComplaint) {
            $escapedComplaint = $expectedComplaint.Replace("'", "\\'")
            $ticketExpression = "(()=>{const title=[...document.querySelectorAll('.tsj-component-title')].find(e=>e.textContent.trim()==='Service Ticket');if(!title||!title.parentElement)return false;const lines=title.parentElement.innerText.split(/\r?\n+/).map(x=>x.trim()).filter(Boolean);return lines.length===2&&lines[0]==='Service Ticket'&&lines[1]==='$escapedComplaint';})()"
            waitForCdp $socket ([ref]$nextId) $ticketExpression $deadline ([ref]$failures) 'solver-validated Service Ticket complaint'
        }
        do {
            $response = invokeCdp $socket ([ref]$nextId) 'Runtime.evaluate' @{
                expression = "document.documentElement.getAttribute('data-tsj-verification') || ''"; returnByValue = $true
            } ([ref]$failures)
            $verificationResult = [string]$response.result.result.value
            if ($verificationResult.StartsWith('FAIL:')) { throw $verificationResult }
            if ($verificationResult -eq $expected) { break }
            Start-Sleep -Milliseconds 250
        } while ([DateTime]::UtcNow -lt $deadline)
        if ($verificationResult -ne $expected) {
            $diagnostic = evaluateCdp $socket ([ref]$nextId) "({status:document.documentElement.getAttribute('data-tsj-verification')||'',body:(document.body&&document.body.innerText||'').slice(-900)})" ([ref]$failures)
            Write-Host ("VERIFIER DIAGNOSTIC status=$($diagnostic.status) body=$($diagnostic.body.Replace("`n", ' | '))")
            if ($failures.Count -gt 0) { Write-Host ("VERIFIER CDP FAILURES: " + ($failures -join '; ')) }
            throw "timed out waiting for '$expected'"
        }
        if ($name -eq 'quick-play selector/session') {
            $quickPlayReport = evaluateCdp $socket ([ref]$nextId) "document.documentElement.getAttribute('data-tsj-quick-play-report') || ''" ([ref]$failures)
            if ($quickPlayReport -ne 'unrepaired-finish-blocked;correct-finish-passed;fresh-session-isolated') {
                throw "Quick Play focused report was incomplete: $quickPlayReport"
            }
        }
        if ($name -eq 'seed=3 stress-damage') {
            $stressReport = evaluateCdp $socket ([ref]$nextId) "document.documentElement.getAttribute('data-tsj-stress-report') || ''" ([ref]$failures)
            if (-not $stressReport) { throw 'stress verifier did not publish its developer electrical report' }
            Write-Host "TASK34 ELECTRICAL REPORT: $stressReport"
        }
        Start-Sleep -Milliseconds 100
        [void](evaluateCdp $socket ([ref]$nextId) "document.readyState" ([ref]$failures))
        if ($EvidenceDirectory -and $name -match '^seed=(0|2) parallel$') {
            [IO.Directory]::CreateDirectory($EvidenceDirectory) | Out-Null
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory ("parallel-seed-" + $Matches[1] + ".png")) ([ref]$failures)
        }
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host "PASS $name"
        $success = $true
    } catch {
        Write-Host "FAIL $name - $($_.Exception.Message)"
    } finally {
        cleanupBrowser $browser $socket $profile
    }
    return $success
}

function evaluateCdp($socket, [ref]$nextId, [string]$expression, [ref]$failures) {
    $response = invokeCdp $socket $nextId 'Runtime.evaluate' @{
        expression = $expression; returnByValue = $true
    } $failures
    if ($response.result.PSObject.Properties['exceptionDetails']) {
        throw $response.result.exceptionDetails.text
    }
    return $response.result.result.value
}

function waitForCdp($socket, [ref]$nextId, [string]$expression, [DateTime]$deadline,
        [ref]$failures, [string]$description) {
    do {
        if (evaluateCdp $socket $nextId $expression $failures) { return }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "timed out waiting for $description"
}

function clickPoint($socket, [ref]$nextId, $point, [string]$button, [ref]$failures) {
    [void](invokeCdp $socket $nextId 'Input.dispatchMouseEvent' @{
        type = 'mousePressed'; x = [double]$point.x; y = [double]$point.y; button = $button; clickCount = 1
    } $failures)
    [void](invokeCdp $socket $nextId 'Input.dispatchMouseEvent' @{
        type = 'mouseReleased'; x = [double]$point.x; y = [double]$point.y; button = $button; clickCount = 1
    } $failures)
}

function clickButton($socket, [ref]$nextId, [string]$text, [ref]$failures) {
    $escaped = $text.Replace("'", "\'")
    $point = $null
    for ($scrollAttempt = 0; $scrollAttempt -lt 20; $scrollAttempt++) {
        $point = evaluateCdp $socket $nextId "(()=>{const e=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='$escaped');if(!e)return null;const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,visible:r.top>=0&&r.bottom<=innerHeight};})()" $failures
        if ($null -eq $point -or $point.visible) { break }
        $wheelY = if ([double]$point.y -gt 0) { 640 } else { -640 }
        [void](invokeCdp $socket $nextId 'Input.dispatchMouseEvent' @{
            type = 'mouseWheel'; x = [Math]::Max(1, [Math]::Min(1439, [double]$point.x));
            y = 500; deltaX = 0; deltaY = $wheelY
        } $failures)
        Start-Sleep -Milliseconds 80
    }
    if ($null -eq $point) { throw "button not found: $text" }
    if (-not $point.visible) { throw "button did not scroll into view: $text" }
    clickPoint $socket $nextId $point 'left' $failures
}

function clickButtonAndWaitForPredicate($socket, [ref]$nextId, [string]$text,
        [string]$successExpression, [DateTime]$deadline, [ref]$failures,
        [string]$description) {
    $escaped = $text.Replace("'", "\'")
    $lastDiagnostic = ''
    for ($clickAttempt = 0; $clickAttempt -lt 5; $clickAttempt++) {
        try {
            $point = $null
            for ($visibilityAttempt = 0; $visibilityAttempt -lt 5; $visibilityAttempt++) {
                $point = evaluateCdp $socket $nextId "(()=>{const e=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='$escaped');if(!e)return null;const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,visible:r.top>=0&&r.bottom<=innerHeight,enabled:!e.disabled};})()" $failures
                if ($null -eq $point) { break }
                if ($point.visible -and $point.enabled) { break }
                if (-not $point.visible) {
                    $wheelY = if ([double]$point.y -gt 0) { 640 } else { -640 }
                    [void](invokeCdp $socket $nextId 'Input.dispatchMouseEvent' @{
                        type = 'mouseWheel'; x = [Math]::Max(1, [Math]::Min(1439, [double]$point.x));
                        y = 500; deltaX = 0; deltaY = $wheelY
                    } $failures)
                }
                Start-Sleep -Milliseconds 80
            }
            if ($null -eq $point) { throw "button not found: $text" }
            if (-not $point.visible) { throw "button did not scroll into view: $text" }
            if (-not $point.enabled) { throw "button is disabled: $text" }

            clickPoint $socket $nextId $point 'left' $failures
            $settleDeadline = [DateTime]::UtcNow.AddSeconds(3)
            if ($settleDeadline -gt $deadline) { $settleDeadline = $deadline }
            waitForCdp $socket $nextId $successExpression $settleDeadline ([ref]$failures) $description
            return
        } catch {
            $lastDiagnostic = $_.Exception.Message
            try {
                $state = evaluateCdp $socket $nextId "(()=>{const button=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='$escaped');return {button:button?{visible:(()=>{const r=button.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight;})(),enabled:!button.disabled}:null,body:(document.body.innerText||'').slice(-900)};})()" $failures
                $lastDiagnostic = $lastDiagnostic + ' state=' + ($state | ConvertTo-Json -Compress)
            } catch {
                $lastDiagnostic = $lastDiagnostic + ' state-diagnostic-failed=' + $_.Exception.Message
            }
            if ([DateTime]::UtcNow -ge $deadline) { break }
            Start-Sleep -Milliseconds 120
        }
    }
    throw "button interaction did not settle for $text after 5 mouse-click attempts: $lastDiagnostic"
}

function clickTrayPartAndWaitForSelection($socket, [ref]$nextId, [string]$buttonText,
        [DateTime]$deadline, [ref]$failures) {
    $escapedButtonText = $buttonText.Replace("'", "\'")
    $escapedSelectedText = ("Selected: " + $buttonText).Replace("'", "\'")
    $lastDiagnostic = ''
    for ($selectionAttempt = 0; $selectionAttempt -lt 5; $selectionAttempt++) {
        try {
            $button = $null
            for ($visibilityAttempt = 0; $visibilityAttempt -lt 5; $visibilityAttempt++) {
                $button = evaluateCdp $socket $nextId "(()=>{const e=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='$escapedButtonText');if(!e)return null;const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,visible:r.top>=0&&r.bottom<=innerHeight,enabled:!e.disabled};})()" $failures
                if ($null -eq $button) { break }
                if ($button.visible -and $button.enabled) { break }
                if (-not $button.visible) {
                    $wheelY = if ([double]$button.y -gt 0) { 640 } else { -640 }
                    [void](invokeCdp $socket $nextId 'Input.dispatchMouseEvent' @{
                        type = 'mouseWheel'; x = [Math]::Max(1, [Math]::Min(1439, [double]$button.x));
                        y = 500; deltaX = 0; deltaY = $wheelY
                    } $failures)
                }
                Start-Sleep -Milliseconds 80
            }
            if ($null -eq $button) { throw "tray button not found: $buttonText" }
            if (-not $button.visible) { throw "tray button did not scroll into view: $buttonText" }
            if (-not $button.enabled) { throw "tray button is disabled: $buttonText" }
            clickPoint $socket $nextId $button 'left' $failures
            $selectionDeadline = [DateTime]::UtcNow.AddSeconds(3)
            if ($selectionDeadline -gt $deadline) { $selectionDeadline = $deadline }
            waitForCdp $socket $nextId "[...document.querySelectorAll('.tsj-component-panel')].some(p=>p.innerText.includes('$escapedSelectedText')&&p.innerText.includes('State: Loose'))" $selectionDeadline ([ref]$failures) 'selected loose tray part panel'
            return evaluateCdp $socket $nextId "(()=>{const panel=[...document.querySelectorAll('.tsj-component-panel')].find(p=>p.innerText.includes('$escapedSelectedText')&&p.innerText.includes('State: Loose'));return panel?panel.innerText:'';})()" $failures
        } catch {
            $lastDiagnostic = $_.Exception.Message
            $state = evaluateCdp $socket $nextId "(()=>{const button=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='$escapedButtonText');return {button:button?{visible:(()=>{const r=button.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight;})(),enabled:!button.disabled}:null,panels:[...document.querySelectorAll('.tsj-component-panel')].map(p=>p.innerText).slice(-4)};})()" $failures
            $lastDiagnostic = $lastDiagnostic + ' state=' + ($state | ConvertTo-Json -Compress)
            if ([DateTime]::UtcNow -ge $deadline) { break }
            Start-Sleep -Milliseconds 120
        }
    }
    throw "tray part selection did not settle for $buttonText after 5 mouse-click attempts: $lastDiagnostic"
}

function getCanvasPoint($socket, [ref]$nextId, [string]$targetKey, [ref]$failures) {
    $escaped = $targetKey.Replace("'", "\\'")
    return evaluateCdp $socket $nextId "(()=>{const c=[...document.querySelectorAll('canvas')].find(x=>{const r=x.getBoundingClientRect();return r.width>100&&r.height>100});const g=window.__tsjPcbGeometry;if(!c||!g||!g.points||!g.points['$escaped'])return null;const r=c.getBoundingClientRect(),p=g.points['$escaped'];return {x:r.left+p.x*r.width/c.width,y:r.top+p.y*r.height/c.height};})()" $failures
}

function getPlayerValueLeakDiagnostics($socket, [ref]$nextId, [string]$originalValue,
        [ref]$failures) {
    $escaped = $originalValue.Replace('\', '\\').Replace('"', '\"')
    $expression = '(()=>{const pattern=new RegExp("' + $escaped + '\\s*ohm","i");' +
        'const outside=e=>e&&!e.closest("select");let text=[],walker=document.createTreeWalker(' +
        'document.body,NodeFilter.SHOW_TEXT),node;while((node=walker.nextNode())!==null)' +
        'if(outside(node.parentElement)&&pattern.test(node.nodeValue))text.push(node.nodeValue.trim());' +
        'let attributes=[];for(const e of document.querySelectorAll("*"))if(outside(e))' +
        'for(const a of e.attributes)if(pattern.test(a.value))attributes.push(a.name+":"+a.value);' +
        'return {safe:text.length===0&&attributes.length===0,text:text,attributes:attributes};})()'
    return evaluateCdp $socket $nextId $expression $failures
}

function getResistorBandColors($socket, [ref]$nextId, [string]$firstPad,
        [string]$secondPad, [ref]$failures) {
    $first = $firstPad.Replace('"', '\"')
    $second = $secondPad.Replace('"', '\"')
    $expression = '(()=>{const c=[...document.querySelectorAll("canvas")].find(x=>{const r=x.getBoundingClientRect();' +
        'return r.width>100&&r.height>100}),r=c.getBoundingClientRect(),g=c.getContext("2d"),' +
        'points=window.__tsjPcbGeometry.points,a=points["' + $first + '"],b=points["' + $second + '"],' +
        'x1=Math.min(a.x,b.x),x2=Math.max(a.x,b.x),y=(a.y+b.y)/2,colors={brown:"125,74,45",' +
        'black:"34,34,34",red:"181,35,45",gold:"199,163,59"},found={};' +
        'for(let x=x1;x<=x2;x++){const p=[...g.getImageData(Math.round(x*c.width/r.width),' +
        'Math.round(y*c.height/r.height),1,1).data].slice(0,3).join(",");for(const name in colors)' +
        'if(p===colors[name])found[name]=true;}return found;})()'
    return evaluateCdp $socket $nextId $expression $failures
}

function sendKey($socket, [ref]$nextId, [string]$key, [int]$code, [ref]$failures) {
    [void](invokeCdp $socket $nextId 'Input.dispatchKeyEvent' @{
        type = 'keyDown'; key = $key; code = $key; windowsVirtualKeyCode = $code;
        nativeVirtualKeyCode = $code
    } $failures)
    [void](invokeCdp $socket $nextId 'Input.dispatchKeyEvent' @{
        type = 'keyUp'; key = $key; code = $key; windowsVirtualKeyCode = $code;
        nativeVirtualKeyCode = $code
    } $failures)
    Start-Sleep -Milliseconds 30
}

function selectOptionWithKeyboard($socket, [ref]$nextId, [int]$selectIndex,
        [string]$optionText, [ref]$failures) {
    $escaped = $optionText.Replace("'", "\'")
    $info = $null
    for ($scrollAttempt = 0; $scrollAttempt -lt 20; $scrollAttempt++) {
        $info = evaluateCdp $socket $nextId "(()=>{const e=document.querySelectorAll('select')[$selectIndex];if(!e)return null;const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,index:[...e.options].findIndex(o=>o.text==='$escaped'),visible:r.top>=0&&r.bottom<=innerHeight};})()" $failures
        if ($null -eq $info -or $info.visible) { break }
        $wheelY = if ([double]$info.y -gt 0) { 640 } else { -640 }
        [void](invokeCdp $socket $nextId 'Input.dispatchMouseEvent' @{
            type = 'mouseWheel'; x = [Math]::Max(1, [Math]::Min(1439, [double]$info.x));
            y = 500; deltaX = 0; deltaY = $wheelY
        } $failures)
        Start-Sleep -Milliseconds 80
    }
    if ($null -eq $info -or [int]$info.index -lt 0) { throw "catalog option not found: $optionText" }
    if (-not $info.visible) { throw "catalog did not scroll into view: $optionText" }
    $actual = ''
    $lastSelectionFailure = ''
    for ($selectionAttempt = 0; $selectionAttempt -lt 3 -and $actual -ne $optionText;
            $selectionAttempt++) {
        try {
            $attemptInfo = evaluateCdp $socket $nextId "(()=>{const e=document.querySelectorAll('select')[$selectIndex];if(!e)return null;const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,index:[...e.options].findIndex(o=>o.text==='$escaped'),visible:r.top>=0&&r.bottom<=innerHeight};})()" $failures
            if ($null -eq $attemptInfo -or [int]$attemptInfo.index -lt 0) {
                throw "catalog option not found: $optionText"
            }
            if (-not $attemptInfo.visible) { throw "catalog did not scroll into view: $optionText" }
            $focusPlan = evaluateCdp $socket $nextId "(()=>{const target=document.querySelectorAll('select')[$selectIndex];return {found:!!target&&!target.disabled&&target.getClientRects().length>0};})()" $failures
            if (-not $focusPlan.found) { throw "catalog is not keyboard reachable: $optionText" }
            clickPoint $socket $nextId $attemptInfo 'left' ([ref]$failures)
            $selectionDeadline = [DateTime]::UtcNow.AddSeconds(5)
            waitForCdp $socket $nextId "document.activeElement===document.querySelectorAll('select')[$selectIndex]" $selectionDeadline ([ref]$failures) 'catalog select focus'
            sendKey $socket $nextId 'Escape' 27 $failures
            waitForCdp $socket $nextId "document.activeElement===document.querySelectorAll('select')[$selectIndex]" $selectionDeadline ([ref]$failures) 'catalog select focus after closing popup'
            sendKey $socket $nextId 'Home' 36 $failures
            waitForCdp $socket $nextId "(()=>{const e=document.querySelectorAll('select')[$selectIndex];return document.activeElement===e&&e.selectedIndex===0&&e.selectedOptions[0].text===e.options[0].text;})()" $selectionDeadline ([ref]$failures) 'catalog first option after Home'
            for ($index = 1; $index -le [int]$attemptInfo.index; $index++) {
                sendKey $socket $nextId 'ArrowDown' 40 $failures
                $stepIndex = $index
                waitForCdp $socket $nextId "(()=>{const e=document.querySelectorAll('select')[$selectIndex],i=$stepIndex;return document.activeElement===e&&e.selectedIndex===i&&e.selectedOptions[0].text===e.options[i].text;})()" $selectionDeadline ([ref]$failures) "catalog option $stepIndex after ArrowDown"
            }
            $actual = evaluateCdp $socket $nextId "document.querySelectorAll('select')[$selectIndex].selectedOptions[0].text" $failures
            if ($actual -ne $optionText) {
                throw "catalog keyboard selection chose $actual instead of $optionText"
            }
        } catch {
            $lastSelectionFailure = $_.Exception.Message
            $actual = ''
        }
    }
    if ($actual -ne $optionText) {
        $focus = evaluateCdp $socket $nextId "(()=>{const e=document.querySelectorAll('select')[$selectIndex];return {active:document.activeElement===e,tag:document.activeElement&&document.activeElement.tagName,y:e.getBoundingClientRect().top};})()" $failures
        throw "catalog keyboard selection chose $actual instead of $optionText (active=$($focus.active), tag=$($focus.tag), y=$($focus.y), lastFailure=$lastSelectionFailure)"
    }
}

function waitForAnimationFrames($socket, [ref]$nextId, [DateTime]$deadline, [ref]$failures) {
    [void](evaluateCdp $socket $nextId "window.__tsjFrameWait=0;requestAnimationFrame(()=>requestAnimationFrame(()=>window.__tsjFrameWait=1));true" $failures)
    waitForCdp $socket $nextId "window.__tsjFrameWait===1" $deadline $failures 'two rendered animation frames'
}

function captureBrowserScreenshot($socket, [ref]$nextId, [string]$path, [ref]$failures) {
    $result = invokeCdp $socket $nextId 'Page.captureScreenshot' @{
        format = 'png'; fromSurface = $true; captureBeyondViewport = $false
    } $failures
    [IO.File]::WriteAllBytes($path, [Convert]::FromBase64String($result.result.data))
}

function verifyQuickPlayNormalPlayer([string]$url, [int]$debugPort, [bool]$finishProof) {
    if (-not $finishProof) { throw 'Quick Play fresh-page check did not follow finish-success proof' }
    $profile = Join-Path $env:TEMP ("tsj-quick-play-" + [Guid]::NewGuid().ToString('N'))
    $arguments = @('--headless=new', '--disable-gpu', '--no-first-run', '--disable-sync',
        '--window-size=1440,1000', "--user-data-dir=$profile", "--remote-debugging-port=$debugPort", 'about:blank')
    $browser = Start-Process -FilePath $BrowserPath -ArgumentList $arguments -PassThru -WindowStyle Hidden
    $socket = $null
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        do {
            Start-Sleep -Milliseconds 200
            try {
                $targets = Invoke-RestMethod "http://127.0.0.1:$debugPort/json/list" -TimeoutSec 2
                $target = $targets | Where-Object { $_.type -eq 'page' } | Select-Object -First 1
            } catch { $target = $null }
        } while ($null -eq $target -and [DateTime]::UtcNow -lt $deadline)
        if ($null -eq $target) { throw 'browser target did not become available' }
        $socket = New-Object Net.WebSockets.ClientWebSocket
        $socket.ConnectAsync([Uri]$target.webSocketDebuggerUrl,
            [Threading.CancellationToken]::None).GetAwaiter().GetResult()
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        $firstDocumentTimeOrigin = navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Service Ticket')&&document.body.innerText.includes('Finish Job')&&document.querySelectorAll('canvas').length>0" $deadline ([ref]$failures) 'normal Quick Play PCB and Finish Job'
        $privacy = evaluateCdp $socket ([ref]$nextId) "(()=>{const body=document.body.innerText||'',lower=body.toLowerCase(),root=document.documentElement;return {body:body,hidden:/fault|stress|damage|rating|specification|answer|wattage/.test(lower),family:root.getAttribute('data-tsj-quick-play-family'),seed:root.getAttribute('data-tsj-quick-play-seed'),report:root.getAttribute('data-tsj-quick-play-report'),cleanParts:body.includes('No removed parts')&&!body.includes('State: Loose'),failure:body.includes('Functional check failed.'),finish:[...document.querySelectorAll('button')].filter(x=>x.innerText.trim()==='Finish Job').length};})()" ([ref]$failures)
        if ($privacy.hidden -or $privacy.family -or $privacy.seed -or $privacy.report -or -not $privacy.cleanParts -or $privacy.failure -or $privacy.finish -ne 1) {
            throw "Quick Play normal-player privacy or control boundary failed: $($privacy | ConvertTo-Json -Compress)"
        }
        $priorDocumentMarker = [Guid]::NewGuid().ToString('N')
        [void](evaluateCdp $socket ([ref]$nextId) "window.__tsjVerifierPriorDocument='$priorDocumentMarker';true" ([ref]$failures))
        if ($EvidenceDirectory) {
            [IO.Directory]::CreateDirectory($EvidenceDirectory) | Out-Null
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'quick-play-initial.png') ([ref]$failures)
        }
        $secondDocumentTimeOrigin = navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Service Ticket')&&document.body.innerText.includes('Finish Job')&&!document.body.innerText.includes('Functional check failed.')" $deadline ([ref]$failures) 'fresh Quick Play reload state'
        $freshState = evaluateCdp $socket ([ref]$nextId) "(()=>{const body=document.body.innerText||'',root=document.documentElement;return {prior:typeof window.__tsjVerifierPriorDocument==='undefined'?'':window.__tsjVerifierPriorDocument,cleanParts:body.includes('No removed parts')&&!body.includes('State: Loose'),failure:body.includes('Functional check failed.'),family:root.getAttribute('data-tsj-quick-play-family'),seed:root.getAttribute('data-tsj-quick-play-seed'),report:root.getAttribute('data-tsj-quick-play-report'),finish:[...document.querySelectorAll('button')].filter(x=>x.innerText.trim()==='Finish Job').length};})()" ([ref]$failures)
        if ([double]$firstDocumentTimeOrigin -eq [double]$secondDocumentTimeOrigin -or
                $freshState.prior -ne '' -or -not $freshState.cleanParts -or
                $freshState.failure -or $freshState.family -or $freshState.seed -or
                $freshState.report -or $freshState.finish -ne 1) {
            throw "Quick Play reload did not create a clean new document: first=$firstDocumentTimeOrigin second=$secondDocumentTimeOrigin state=$($freshState | ConvertTo-Json -Compress)"
        }
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host 'PASS quick-play-normal-player fresh-reload privacy=clean finish-job=visible'
        return $true
    } catch {
        Write-Host "FAIL quick-play-normal-player - $($_.Exception.Message)"
        return $false
    } finally {
        cleanupBrowser $browser $socket $profile
    }
}

function verifyNormalPlayer([string]$url, [int]$debugPort) {
    $profile = Join-Path $env:TEMP ("tsj-player-" + [Guid]::NewGuid().ToString('N'))
    $arguments = @('--headless=new', '--disable-gpu', '--no-first-run', '--disable-sync',
        '--window-size=1440,1000', "--user-data-dir=$profile", "--remote-debugging-port=$debugPort", 'about:blank')
    $browser = Start-Process -FilePath $BrowserPath -ArgumentList $arguments -PassThru -WindowStyle Hidden
    $socket = $null
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        do {
            Start-Sleep -Milliseconds 200
            try {
                $targets = Invoke-RestMethod "http://127.0.0.1:$debugPort/json/list" -TimeoutSec 2
                $target = $null
                foreach ($candidate in $targets) {
                    if ($candidate.type -eq 'page') { $target = $candidate; break }
                }
            } catch { $target = $null }
        } while ($null -eq $target -and [DateTime]::UtcNow -lt $deadline)
        if ($null -eq $target) { throw 'browser target did not become available' }
        $socket = New-Object Net.WebSockets.ClientWebSocket
        $socket.ConnectAsync([Uri]([string]$target.webSocketDebuggerUrl),
            [Threading.CancellationToken]::None).GetAwaiter().GetResult()
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.navigate' @{ url = $url } ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Indicator does not light.')" $deadline ([ref]$failures) 'ready seed-3 challenge'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry&&!!window.__tsjPcbGeometry.points" $deadline ([ref]$failures) 'procedural PCB geometry bridge'
        Write-Host 'PLAYER ready'
        $initial = evaluateCdp $socket ([ref]$nextId) "({canvas:!!document.querySelector('canvas'),meter:!!document.querySelector('.tsj-meter-panel'),power:[...document.querySelectorAll('button')].some(x=>x.innerText.includes('Board Power')),catalog:document.body.innerText.includes('Replacement Catalog'),empty:document.body.innerText.includes('No removed parts')})" ([ref]$failures)
        if (-not ($initial.canvas -and $initial.meter -and $initial.power -and $initial.catalog -and $initial.empty)) {
            throw 'initial PCB, meter, power, catalog, or empty tray UI was missing'
        }
        $installedBands = getResistorBandColors $socket ([ref]$nextId) 'pad:R1.1' 'pad:R1.2' ([ref]$failures)
        foreach ($band in @('brown', 'black', 'red', 'gold')) {
            if (-not ($installedBands.PSObject.Properties.Name -contains $band)) {
                throw "installed R1 color band was not visible: $($installedBands | ConvertTo-Json -Compress)"
            }
        }
        $r1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'R1 component controls'
        $r1Panel = evaluateCdp $socket ([ref]$nextId) "document.querySelectorAll('.tsj-component-panel')[1].innerText" ([ref]$failures)
        if (-not ($r1Panel -match 'R1' -and $r1Panel -match 'Type: resistor' -and
                $r1Panel -match 'State: Installed' -and $r1Panel -match 'Markings: Color bands' -and
                $r1Panel -notmatch 'Value: 1000 Ohm')) {
            throw "original R1 panel did not preserve physical identity without its numeric value: $r1Panel"
        }
        $r1Leak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '1000' ([ref]$failures)
        if (-not $r1Leak.safe) {
            throw "original R1 value leaked into ordinary UI: $($r1Leak | ConvertTo-Json -Compress)"
        }
        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "[...document.querySelectorAll('button')].some(x=>x.innerText.trim()==='R1_ORIGINAL - Removed resistor')" $deadline ([ref]$failures) 'faulted original in tray'
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $removedLeak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '1000' ([ref]$failures)
        if (-not $removedLeak.safe) {
            throw "removed original R1 value leaked into ordinary UI: $($removedLeak | ConvertTo-Json -Compress)"
        }
        $originalBands = getResistorBandColors $socket ([ref]$nextId) 'loose:R1_ORIGINAL:0' 'loose:R1_ORIGINAL:1' ([ref]$failures)
        foreach ($band in @('brown', 'black', 'red', 'gold')) {
            if (-not ($originalBands.PSObject.Properties.Name -contains $band)) {
                throw "removed R1 color band was not visible: $($originalBands | ConvertTo-Json -Compress)"
            }
        }
        $selectedOriginal = clickTrayPartAndWaitForSelection $socket ([ref]$nextId) 'R1_ORIGINAL - Removed resistor' $deadline ([ref]$failures)
        if ($selectedOriginal -notmatch 'Selected: R1_ORIGINAL - Removed resistor' -or
                $selectedOriginal -notmatch 'State: Loose') {
            throw "selected original R1 lost its privacy-safe identity: $selectedOriginal"
        }
        $selectedOriginalLeak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '1000' ([ref]$failures)
        if (-not $selectedOriginalLeak.safe) {
            throw "selected original R1 exposed its numeric value: $($selectedOriginalLeak | ConvertTo-Json -Compress)"
        }
        Write-Host 'PLAYER original removed'

        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '1000 Ohm +/-5%' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new resistor' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "[...document.querySelectorAll('button')].some(x=>x.innerText.trim()==='R1_ORIGINAL - Removed resistor')&&[...document.querySelectorAll('select option')].some(x=>x.text==='1000 Ohm +/-5%')" $deadline ([ref]$failures) 'installed replacement excluded from tray while catalog value remains available'
        clickButton $socket ([ref]$nextId) 'Board Power: OFF' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Repair verified. Indicator operating normally.')" $deadline ([ref]$failures) 'solver-backed repair completion'
        Write-Host 'PLAYER repair verified'
        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'healthy replacement component controls'
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "[...document.querySelectorAll('button')].some(x=>x.innerText.trim()==='R1_ORIGINAL - Removed resistor')&&[...document.querySelectorAll('button')].some(x=>x.innerText.trim()==='R1_CATALOG_PART_0 - 1000 Ohm +/-5%')" $deadline ([ref]$failures) 'original and replacement loose resistor identities'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry.points['loose:R1_CATALOG_PART_0:0']&&!!window.__tsjPcbGeometry.points['loose:R1_CATALOG_PART_0:1']" $deadline ([ref]$failures) 'loose resistor geometry'
        Write-Host 'PLAYER healthy replacement removed'
        clickButton $socket ([ref]$nextId) 'R1_ORIGINAL - Removed resistor' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'OHM' ([ref]$failures)

        $healthyLeft = getCanvasPoint $socket ([ref]$nextId) 'loose:R1_CATALOG_PART_0:0' ([ref]$failures)
        $healthyRight = getCanvasPoint $socket ([ref]$nextId) 'loose:R1_CATALOG_PART_0:1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $healthyLeft 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $healthyRight 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "(()=>{const t=document.querySelector('.tsj-meter-display').innerText;return t!=='OL'&&t!=='--- Ohm';})()" $deadline ([ref]$failures) 'healthy forward resistance'
        $forward = evaluateCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText" ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $healthyRight 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "(()=>{const t=document.querySelector('.tsj-meter-display').innerText;return t!=='OL'&&t!=='--- Ohm';})()" $deadline ([ref]$failures) 'intermediate reversed-probe measurement'
        clickPoint $socket ([ref]$nextId) $healthyLeft 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "(()=>{const t=document.querySelector('.tsj-meter-display').innerText;return t!=='OL'&&t!=='--- Ohm';})()" $deadline ([ref]$failures) 'healthy reverse resistance'
        $reverse = evaluateCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText" ([ref]$failures)
        if ($forward -ne $reverse -or $forward -notmatch '(?i)1(\.0+)?\s*kOhm') {
            throw "healthy resistance mismatch: forward=$forward reverse=$reverse"
        }
        $originalLeft = getCanvasPoint $socket ([ref]$nextId) 'loose:R1_ORIGINAL:0' ([ref]$failures)
        $originalRight = getCanvasPoint $socket ([ref]$nextId) 'loose:R1_ORIGINAL:1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalLeft 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalRight 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "(()=>/^100(?:\.0+)?\s*kOhm$/i.test(document.querySelector('.tsj-meter-display').innerText))()" $deadline ([ref]$failures) 'faulted original effective resistance'
        $originalReading = evaluateCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText" ([ref]$failures)
        if ($originalReading -notmatch '(?i)^100(\.0+)?\s*kOhm$') {
            throw "faulted original effective resistance was unexpected: $originalReading"
        }
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host "PASS normal-player seed=3 forward=$forward reverse=$reverse original=$originalReading"
        return $true
    } catch {
        Write-Host "FAIL normal-player seed=3 - $($_.Exception.Message)"
        if ($null -ne $socket) {
            try {
                $snapshot = evaluateCdp $socket ([ref]$nextId) "document.body.innerText" ([ref]$failures)
                $start = [Math]::Max(0, $snapshot.Length - 1400)
                Write-Host ("NORMAL PLAYER UI SNAPSHOT: " + $snapshot.Substring($start).Replace("`n", ' | '))
            } catch { }
        }
        return $false
    } finally {
        cleanupBrowser $browser $socket $profile
    }
}

function verifyNormalParallelPlayer([string]$url, [int]$debugPort) {
    $profile = Join-Path $env:TEMP ("tsj-parallel-player-" + [Guid]::NewGuid().ToString('N'))
    $arguments = @('--headless=new', '--disable-gpu', '--no-first-run', '--disable-sync',
        '--window-size=1440,1000', "--user-data-dir=$profile", "--remote-debugging-port=$debugPort", 'about:blank')
    $browser = Start-Process -FilePath $BrowserPath -ArgumentList $arguments -PassThru -WindowStyle Hidden
    $socket = $null
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        do {
            Start-Sleep -Milliseconds 200
            try {
                $targets = Invoke-RestMethod "http://127.0.0.1:$debugPort/json/list" -TimeoutSec 2
                $target = $targets | Where-Object { $_.type -eq 'page' } | Select-Object -First 1
            } catch { $target = $null }
        } while ($null -eq $target -and [DateTime]::UtcNow -lt $deadline)
        if ($null -eq $target) { throw 'browser target did not become available' }
        $socket = New-Object Net.WebSockets.ClientWebSocket
        $socket.ConnectAsync([Uri]$target.webSocketDebuggerUrl,
            [Threading.CancellationToken]::None).GetAwaiter().GetResult()
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.navigate' @{ url = $url } ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('The two indicators do not behave the same.')" $deadline ([ref]$failures) 'ready parallel challenge'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry&&!!window.__tsjPcbGeometry.points" $deadline ([ref]$failures) 'parallel PCB geometry bridge'
        $initial = evaluateCdp $socket ([ref]$nextId) "({canvas:!!document.querySelector('canvas'),catalog:document.body.innerText.includes('Resistor Replacement Catalog'),noLedCatalog:!document.body.innerText.includes('LED Replacement Catalog'),empty:document.body.innerText.includes('No removed parts'),complaint:document.body.innerText.includes('The two indicators do not behave the same.')})" ([ref]$failures)
        if (-not ($initial.canvas -and $initial.catalog -and $initial.noLedCatalog -and $initial.empty -and $initial.complaint)) {
            throw 'initial parallel PCB, resistor catalog, tray, or complaint UI was incorrect'
        }
        $parallelR1Bands = getResistorBandColors $socket ([ref]$nextId) 'pad:R1.1' 'pad:R1.2' ([ref]$failures)
        foreach ($band in @('brown', 'black', 'red', 'gold')) {
            if (-not ($parallelR1Bands.PSObject.Properties.Name -contains $band)) {
                throw "parallel R1 color band was not visible: $($parallelR1Bands | ConvertTo-Json -Compress)"
            }
        }
        $r1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Markings: Color bands')" $deadline ([ref]$failures) 'parallel original R1 markings'
        $parallelR1Panel = evaluateCdp $socket ([ref]$nextId) "document.querySelectorAll('.tsj-component-panel')[1].innerText" ([ref]$failures)
        if ($parallelR1Panel -match 'Value: 1000 Ohm') { throw "parallel original R1 value leaked: $parallelR1Panel" }
        $parallelR1Leak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '1000' ([ref]$failures)
        if (-not $parallelR1Leak.safe) {
            throw "parallel original R1 value leaked into ordinary UI: $($parallelR1Leak | ConvertTo-Json -Compress)"
        }
        $r2 = getCanvasPoint $socket ([ref]$nextId) 'component:R2' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r2 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Markings: Color bands')" $deadline ([ref]$failures) 'parallel original R2 markings'
        $parallelR2Panel = evaluateCdp $socket ([ref]$nextId) "document.querySelectorAll('.tsj-component-panel')[1].innerText" ([ref]$failures)
        if (-not ($parallelR2Panel -match 'R2' -and $parallelR2Panel -match 'Type: resistor' -and
                $parallelR2Panel -match 'State: Installed' -and $parallelR2Panel -notmatch 'Value: 2200 Ohm')) {
            throw "parallel original R2 panel did not preserve identity without its numeric value: $parallelR2Panel"
        }
        $parallelR2Leak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '2200' ([ref]$failures)
        if (-not $parallelR2Leak.safe) {
            throw "parallel original R2 value leaked into ordinary UI: $($parallelR2Leak | ConvertTo-Json -Compress)"
        }
        $parallelR2Bands = getResistorBandColors $socket ([ref]$nextId) 'pad:R2.1' 'pad:R2.2' ([ref]$failures)
        foreach ($band in @('red', 'gold')) {
            if (-not ($parallelR2Bands.PSObject.Properties.Name -contains $band)) {
                throw "parallel R2 color band was not visible: $($parallelR2Bands | ConvertTo-Json -Compress)"
            }
        }
        if ($EvidenceDirectory) {
            [IO.Directory]::CreateDirectory($EvidenceDirectory) | Out-Null
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'parallel-seed-3.png') ([ref]$failures)
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'parallel-faulted.png') ([ref]$failures)
        }
        clickButton $socket ([ref]$nextId) 'DC V' ([ref]$failures)
        $vin = getCanvasPoint $socket ([ref]$nextId) 'pad:J1.1' ([ref]$failures)
        $ground = getCanvasPoint $socket ([ref]$nextId) 'pad:J1.2' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $vin 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $ground 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText.includes('V')" $deadline ([ref]$failures) 'parallel supply voltage reading'
        if ($EvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'parallel-measurement.png') ([ref]$failures)
        }
        Write-Host 'PARALLEL PLAYER supply measurement complete'
        clickButton $socket ([ref]$nextId) 'DC V' ([ref]$failures)
        Write-Host 'PARALLEL PLAYER DC mode exited'
        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        Write-Host 'PARALLEL PLAYER power on'
        $currentR1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        if ($null -eq $currentR1) { throw 'parallel current R1 geometry was unavailable after power transition' }
        clickPoint $socket ([ref]$nextId) $currentR1 'left' ([ref]$failures)
        $currentR1Panel = evaluateCdp $socket ([ref]$nextId) "document.querySelectorAll('.tsj-component-panel')[1].innerText" ([ref]$failures)
        if ($currentR1Panel -notmatch '(?m)^R1$' -or $currentR1Panel -notmatch 'Remove component') {
            throw "parallel post-power contextual panel did not identify R1 with expected action: $currentR1Panel"
        }
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'parallel R1 component controls'
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "[...document.querySelectorAll('button')].some(x=>x.innerText.trim()==='R1_ORIGINAL - Removed resistor')" $deadline ([ref]$failures) 'parallel faulted original tray part'
        $parallelRemovedLeak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '1000' ([ref]$failures)
        if (-not $parallelRemovedLeak.safe) {
            throw "parallel removed original R1 value leaked into ordinary UI: $($parallelRemovedLeak | ConvertTo-Json -Compress)"
        }
        $parallelSelectedOriginal = clickTrayPartAndWaitForSelection $socket ([ref]$nextId) 'R1_ORIGINAL - Removed resistor' $deadline ([ref]$failures)
        if ($parallelSelectedOriginal -notmatch 'Selected: R1_ORIGINAL - Removed resistor' -or
                $parallelSelectedOriginal -notmatch 'State: Loose') {
            throw "parallel selected original R1 lost its privacy-safe identity: $parallelSelectedOriginal"
        }
        $parallelSelectedOriginalLeak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '1000' ([ref]$failures)
        if (-not $parallelSelectedOriginalLeak.safe) {
            throw "parallel selected original R1 exposed its numeric value: $($parallelSelectedOriginalLeak | ConvertTo-Json -Compress)"
        }
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '1000 Ohm +/-5%' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new resistor' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Board Power: OFF' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Repair verified. Both indicators operating normally.')" $deadline ([ref]$failures) 'parallel functional repair'
        if ($EvidenceDirectory) {
            waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'parallel-repaired.png') ([ref]$failures)
        }
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host 'PASS parallel-normal-player seed=3 supply=solver-backed repair=verified'
        return $true
    } catch {
        Write-Host "FAIL parallel-normal-player seed=3 - $($_.Exception.Message)"
        if ($null -ne $socket) {
            try {
                $snapshot = evaluateCdp $socket ([ref]$nextId) "document.body.innerText" ([ref]$failures)
                $start = [Math]::Max(0, $snapshot.Length - 1800)
                Write-Host ("PARALLEL PLAYER UI SNAPSHOT: " + $snapshot.Substring($start).Replace([Environment]::NewLine, ' | '))
            } catch { }
        }
        return $false
    } finally {
        cleanupBrowser $browser $socket $profile
    }
}

function verifyNormalDiodePlayer([string]$url, [int]$debugPort) {
    $profile = Join-Path $env:TEMP ("tsj-diode-player-" + [Guid]::NewGuid().ToString('N'))
    $arguments = @('--headless=new', '--disable-gpu', '--no-first-run', '--disable-sync',
        '--window-size=1440,1000', "--user-data-dir=$profile", "--remote-debugging-port=$debugPort", 'about:blank')
    $browser = Start-Process -FilePath $BrowserPath -ArgumentList $arguments -PassThru -WindowStyle Hidden
    $socket = $null
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        do {
            Start-Sleep -Milliseconds 200
            try {
                $targets = Invoke-RestMethod "http://127.0.0.1:$debugPort/json/list" -TimeoutSec 2
                $target = $targets | Where-Object { $_.type -eq 'page' } | Select-Object -First 1
            } catch { $target = $null }
        } while ($null -eq $target -and [DateTime]::UtcNow -lt $deadline)
        if ($null -eq $target) { throw 'browser target did not become available' }
        $socket = New-Object Net.WebSockets.ClientWebSocket
        $socket.ConnectAsync([Uri]$target.webSocketDebuggerUrl,
            [Threading.CancellationToken]::None).GetAwaiter().GetResult()
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.navigate' @{ url = $url } ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Indicator does not light.')" $deadline ([ref]$failures) 'ready diode challenge'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry&&!!window.__tsjPcbGeometry.points" $deadline ([ref]$failures) 'procedural diode PCB geometry bridge'
        $initial = evaluateCdp $socket ([ref]$nextId) "({canvas:!!document.querySelector('canvas'),catalog:document.body.innerText.includes('Replacement Catalog'),empty:document.body.innerText.includes('No removed parts'),disclosed:/D1 failed|diode is open/i.test(document.body.innerText)})" ([ref]$failures)
        if (-not ($initial.canvas -and $initial.catalog -and $initial.empty) -or $initial.disclosed) {
            throw 'initial diode workbench, catalog, tray, or vague complaint was incorrect'
        }
        $diodeR1Bands = getResistorBandColors $socket ([ref]$nextId) 'pad:R1.1' 'pad:R1.2' ([ref]$failures)
        foreach ($band in @('brown', 'black', 'red', 'gold')) {
            if (-not ($diodeR1Bands.PSObject.Properties.Name -contains $band)) {
                throw "diode-family R1 color band was not visible: $($diodeR1Bands | ConvertTo-Json -Compress)"
            }
        }
        $diodeR1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $diodeR1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Markings: Color bands')" $deadline ([ref]$failures) 'diode-family original R1 markings'
        $diodeR1Panel = evaluateCdp $socket ([ref]$nextId) "document.querySelectorAll('.tsj-component-panel')[1].innerText" ([ref]$failures)
        if (-not ($diodeR1Panel -match 'R1' -and $diodeR1Panel -match 'Type: resistor' -and
                $diodeR1Panel -match 'State: Installed' -and $diodeR1Panel -notmatch 'Value: 1000 Ohm')) {
            throw "diode-family original R1 panel did not preserve identity without its numeric value: $diodeR1Panel"
        }
        $diodeR1Leak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '1000' ([ref]$failures)
        if (-not $diodeR1Leak.safe) {
            throw "diode-family original R1 value leaked into ordinary UI: $($diodeR1Leak | ConvertTo-Json -Compress)"
        }
        if ($EvidenceDirectory) {
            [IO.Directory]::CreateDirectory($EvidenceDirectory) | Out-Null
            $initialEvidenceName = if ($PersistentPreviewEvidence) {
                'persistent-preview-fresh-load.png'
            } else { 'initial-board.png' }
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory $initialEvidenceName) ([ref]$failures)
        }
        if ($PersistentPreviewEvidence) {
            cleanupBrowser $browser $socket $profile
            $socket = $null
            $browser = $null
            Write-Host 'PASS persistent-preview fresh diode normal-player load'
            return $true
        }
        $pixels = evaluateCdp $socket ([ref]$nextId) "(()=>{const c=[...document.querySelectorAll('canvas')].find(x=>{const r=x.getBoundingClientRect();return r.width>100&&r.height>100}),r=c.getBoundingClientRect(),g=c.getContext('2d'),p=window.__tsjPcbGeometry.points,at=(x,y)=>[...g.getImageData(Math.round(x*c.width/r.width),Math.round(y*c.height/r.height),1,1).data].slice(0,3).join(','),a=p['pad:D1.A'],k=p['pad:D1.K'],la=p['pad:LED1.A'],lk=p['pad:LED1.K'];return {body:at((a.x+k.x)/2,a.y),band:at(k.x-45,k.y),led:at((la.x+lk.x)/2,la.y-33)};})()" ([ref]$failures)
        if ($pixels.body -eq $pixels.led -or $pixels.band -eq $pixels.body) {
            throw "D1 body, cathode band, and LED1 were not visibly distinct: $($pixels | ConvertTo-Json -Compress)"
        }
        Write-Host "DIODE PLAYER rendered body=$($pixels.body) band=$($pixels.band) led=$($pixels.led)"

        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        $d1 = getCanvasPoint $socket ([ref]$nextId) 'component:D1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $d1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'D1 component controls'
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        $d1Anode = getCanvasPoint $socket ([ref]$nextId) 'pad:D1.A' ([ref]$failures)
        $d1Cathode = getCanvasPoint $socket ([ref]$nextId) 'pad:D1.K' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $d1Anode 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $d1Cathode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'installed open D1 forward OL'
        clickPoint $socket ([ref]$nextId) $d1Cathode 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText!=='OL'" $deadline ([ref]$failures) 'installed D1 intermediate measurement'
        clickPoint $socket ([ref]$nextId) $d1Anode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'installed open D1 reverse OL'
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "(()=>{const button=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='DIODE');return !!button&&!button.className.includes('chsel');})()" $deadline ([ref]$failures) 'diode mode exit cleanup'
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('D1_ORIGINAL - Generic silicon diode')" $deadline ([ref]$failures) 'loose original diode'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry.points['loose:D1_ORIGINAL:0']&&!!window.__tsjPcbGeometry.points['loose:D1_ORIGINAL:1']" $deadline ([ref]$failures) 'loose original diode geometry'

        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        $originalAnode = getCanvasPoint $socket ([ref]$nextId) 'loose:D1_ORIGINAL:0' ([ref]$failures)
        $originalCathode = getCanvasPoint $socket ([ref]$nextId) 'loose:D1_ORIGINAL:1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalAnode 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalCathode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'loose original forward OL'
        clickPoint $socket ([ref]$nextId) $originalCathode 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText!=='OL'" $deadline ([ref]$failures) 'loose original intermediate measurement'
        clickPoint $socket ([ref]$nextId) $originalAnode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'loose original reverse OL'
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new diode' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove D1 before installing a replacement.')" $deadline ([ref]$failures) 'installed catalog diode'
        clickButton $socket ([ref]$nextId) 'Board Power: OFF' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Repair verified. Indicator operating normally.')" $deadline ([ref]$failures) 'diode functional repair'
        Write-Host 'DIODE PLAYER repair verified'

        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $d1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'healthy D1 component controls'
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('D1_CATALOG_PART_0 - Generic silicon diode')&&document.body.innerText.includes('D1_ORIGINAL - Generic silicon diode')" $deadline ([ref]$failures) 'separate loose diode identities'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry.points['loose:D1_CATALOG_PART_0:0']&&!!window.__tsjPcbGeometry.points['loose:D1_CATALOG_PART_0:1']" $deadline ([ref]$failures) 'healthy loose diode geometry'
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        $healthyAnode = getCanvasPoint $socket ([ref]$nextId) 'loose:D1_CATALOG_PART_0:0' ([ref]$failures)
        $healthyCathode = getCanvasPoint $socket ([ref]$nextId) 'loose:D1_CATALOG_PART_0:1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $healthyAnode 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $healthyCathode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "(()=>{const t=document.querySelector('.tsj-meter-display').innerText;return t!=='OL'&&t!=='--- V';})()" $deadline ([ref]$failures) 'healthy diode forward drop'
        $forward = evaluateCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText" ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $healthyCathode 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText!== '$forward'" $deadline ([ref]$failures) 'healthy diode intermediate measurement'
        clickPoint $socket ([ref]$nextId) $healthyAnode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'healthy diode reverse OL'
        clickPoint $socket ([ref]$nextId) $originalAnode 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalCathode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'original remains OL'
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host "PASS diode-normal-player seed=3 healthy-forward=$forward reverse=OL original=OL"
        return $true
    } catch {
        Write-Host "FAIL diode-normal-player seed=3 - $($_.Exception.Message)"
        if ($null -ne $socket) {
            try {
                $snapshot = evaluateCdp $socket ([ref]$nextId) "document.body.innerText" ([ref]$failures)
                $start = [Math]::Max(0, $snapshot.Length - 1200)
                Write-Host ("DIODE PLAYER UI SNAPSHOT: " + $snapshot.Substring($start).Replace("`n", " | "))
            } catch { }
        }
        return $false
    } finally {
        cleanupBrowser $browser $socket $profile
    }
}

function verifyWrongRepairNormalPlayer([string]$url, [int]$debugPort) {
    $profile = Join-Path $env:TEMP ("tsj-wrong-repair-player-" + [Guid]::NewGuid().ToString('N'))
    $arguments = @('--headless=new', '--disable-gpu', '--no-first-run', '--disable-sync',
        '--window-size=1440,1000', "--user-data-dir=$profile", "--remote-debugging-port=$debugPort", 'about:blank')
    $browser = Start-Process -FilePath $BrowserPath -ArgumentList $arguments -PassThru -WindowStyle Hidden
    $socket = $null
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        do {
            Start-Sleep -Milliseconds 200
            try {
                $targets = Invoke-RestMethod "http://127.0.0.1:$debugPort/json/list" -TimeoutSec 2
                $target = $targets | Where-Object { $_.type -eq 'page' } | Select-Object -First 1
            } catch { $target = $null }
        } while ($null -eq $target -and [DateTime]::UtcNow -lt $deadline)
        if ($null -eq $target) { throw 'browser target did not become available' }
        $socket = New-Object Net.WebSockets.ClientWebSocket
        $socket.ConnectAsync([Uri]$target.webSocketDebuggerUrl,
            [Threading.CancellationToken]::None).GetAwaiter().GetResult()
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.navigate' @{ url = $url } ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Indicator does not light.')" $deadline ([ref]$failures) 'ready LED seed-3 wrong-repair challenge'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry&&!!window.__tsjPcbGeometry.points" $deadline ([ref]$failures) 'procedural LED PCB geometry bridge'
        $initial = evaluateCdp $socket ([ref]$nextId) "({canvas:!!document.querySelector('canvas'),catalog:document.body.innerText.includes('Resistor Replacement Catalog'),empty:document.body.innerText.includes('No removed parts'),complaint:document.body.innerText.includes('Indicator does not light.')})" ([ref]$failures)
        if (-not ($initial.canvas -and $initial.catalog -and $initial.empty -and $initial.complaint)) {
            throw 'initial LED workbench or original complaint was missing'
        }
        if ($EvidenceDirectory) {
            [IO.Directory]::CreateDirectory($EvidenceDirectory) | Out-Null
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'initial-board.png') ([ref]$failures)
        }

        $r1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('R1')&&document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'R1 component controls'
        if ($EvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'r1-selected.png') ([ref]$failures)
        }
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: ON' "document.body.innerText.includes('Board Power: OFF')" $deadline ([ref]$failures) 'board power off before R1 removal'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('R1_ORIGINAL - Removed resistor')" $deadline ([ref]$failures) 'faulted original R1 removal'
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '2200 Ohm +/-5%' ([ref]$failures)
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Install new resistor' "document.body.innerText.includes('Value: 2200 Ohm +/-5%')" $deadline ([ref]$failures) '2.2 kOhm physical resistor installation'
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $wrongBands = getResistorBandColors $socket ([ref]$nextId) 'pad:R1.1' 'pad:R1.2' ([ref]$failures)
        $wrongBandNames = if ($null -eq $wrongBands) { @() } else { @($wrongBands.PSObject.Properties | ForEach-Object { $_.Name }) }
        foreach ($band in @('red', 'gold')) {
            if (-not ($wrongBandNames -contains $band)) {
                throw "2.2 kOhm installed resistor markings were not visible: $($wrongBands | ConvertTo-Json -Compress)"
            }
        }
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'power on wrong replacement'
        $wrongUi = evaluateCdp $socket ([ref]$nextId) "(()=>{const body=document.body.innerText||'',lower=body.toLowerCase();return {complaint:body.includes('Indicator does not light.'),power:body.includes('Board Power: ON'),value:body.includes('Value: 2200 Ohm +/-5%'),completed:body.includes('Repair verified. Indicator operating normally.'),diagnostic:lower.includes('wrong resistor')||lower.includes('incorrect resistor')||lower.includes('diagnos')};})()" ([ref]$failures)
        if (-not ($wrongUi.complaint -and $wrongUi.power -and $wrongUi.value) -or $wrongUi.completed -or $wrongUi.diagnostic) {
            throw "wrong powered repair UI did not preserve complaint without a diagnostic: $($wrongUi | ConvertTo-Json -Compress)"
        }
        if ($EvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'wrong-repair-powered.png') ([ref]$failures)
        }

        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: ON' "document.body.innerText.includes('Board Power: OFF')" $deadline ([ref]$failures) 'board power off before wrong replacement removal'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('R1_CATALOG_PART_0 - 2200 Ohm +/-5%')" $deadline ([ref]$failures) '2.2 kOhm replacement removal'
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '1000 Ohm +/-5%' ([ref]$failures)
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Install new resistor' "document.body.innerText.includes('Value: 1000 Ohm +/-5%')" $deadline ([ref]$failures) '1 kOhm physical resistor installation'
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $correctBands = getResistorBandColors $socket ([ref]$nextId) 'pad:R1.1' 'pad:R1.2' ([ref]$failures)
        $correctBandNames = if ($null -eq $correctBands) { @() } else { @($correctBands.PSObject.Properties | ForEach-Object { $_.Name }) }
        foreach ($band in @('brown', 'black', 'red', 'gold')) {
            if (-not ($correctBandNames -contains $band)) {
                throw "1 kOhm installed resistor markings were not visible: $($correctBands | ConvertTo-Json -Compress)"
            }
        }
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'power on correct replacement'
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Repair verified. Indicator operating normally.')" $deadline ([ref]$failures) 'generic functional completion text'
        if ($EvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'completed.png') ([ref]$failures)
        }
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host 'PASS wrong-repair-normal-player seed=3 2200-ohm-degraded 1000-ohm-restored'
        return $true
    } catch {
        Write-Host "FAIL wrong-repair-normal-player seed=3 - $($_.Exception.Message)"
        if ($null -ne $socket) {
            try {
                $snapshot = evaluateCdp $socket ([ref]$nextId) "document.body.innerText" ([ref]$failures)
                $start = [Math]::Max(0, $snapshot.Length - 1800)
                Write-Host ("WRONG REPAIR PLAYER UI SNAPSHOT: " + $snapshot.Substring($start).Replace("`n", ' | '))
            } catch { }
        }
        return $false
    } finally {
        cleanupBrowser $browser $socket $profile
    }
}

function verifyStressDamageNormalPlayer([string]$url, [int]$debugPort) {
    $profile = Join-Path $env:TEMP ("tsj-stress-player-" + [Guid]::NewGuid().ToString('N'))
    $arguments = @('--headless=new', '--disable-gpu', '--no-first-run', '--disable-sync',
        '--window-size=1440,1000', "--user-data-dir=$profile", "--remote-debugging-port=$debugPort", 'about:blank')
    $browser = Start-Process -FilePath $BrowserPath -ArgumentList $arguments -PassThru -WindowStyle Hidden
    $socket = $null
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        do {
            Start-Sleep -Milliseconds 200
            try {
                $targets = Invoke-RestMethod "http://127.0.0.1:$debugPort/json/list" -TimeoutSec 2
                $target = $targets | Where-Object { $_.type -eq 'page' } | Select-Object -First 1
            } catch { $target = $null }
        } while ($null -eq $target -and [DateTime]::UtcNow -lt $deadline)
        if ($null -eq $target) { throw 'browser target did not become available' }
        $socket = New-Object Net.WebSockets.ClientWebSocket
        $socket.ConnectAsync([Uri]$target.webSocketDebuggerUrl,
            [Threading.CancellationToken]::None).GetAwaiter().GetResult()
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.navigate' @{ url = $url } ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Indicator does not light.')" $deadline ([ref]$failures) 'ready LED stress challenge'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry&&!!window.__tsjPcbGeometry.points" $deadline ([ref]$failures) 'stress PCB geometry bridge'
        $evidence = if ($EvidenceDirectory) { $EvidenceDirectory } else { 'docs/task-evidence/task-34' }
        [IO.Directory]::CreateDirectory($evidence) | Out-Null
        captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $evidence 'initial-board.png') ([ref]$failures)

        $r1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('R1')&&document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'stress R1 component controls'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: ON' "document.body.innerText.includes('Board Power: OFF')" $deadline ([ref]$failures) 'stress initial board power-off'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('R1_ORIGINAL - Removed resistor')" $deadline ([ref]$failures) 'stress original R1 removal'
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '220 Ohm +/-5%' ([ref]$failures)
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Install new resistor' "document.body.innerText.includes('Value: 220 Ohm +/-5%')" $deadline ([ref]$failures) 'stress severe replacement installation'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'stress severe replacement power-on'
        $initialUi = evaluateCdp $socket ([ref]$nextId) "(()=>{const b=(document.body.innerText||''),l=b.toLowerCase();return {complaint:b.includes('Indicator does not light.'),value:b.includes('Value: 220 Ohm +/-5%'),power:b.includes('Board Power: ON'),diagnostic:/watt|stress|damage|overheat/.test(l),complete:b.includes('Repair verified. Indicator operating normally.')}})()" ([ref]$failures)
        if (-not ($initialUi.complaint -and $initialUi.value -and $initialUi.power) -or $initialUi.diagnostic -or $initialUi.complete) {
            throw "severe-overload player UI leaked diagnostics or completed unexpectedly: $($initialUi | ConvertTo-Json -Compress)"
        }
        captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $evidence 'severe-overload-powered.png') ([ref]$failures)

        $advance = evaluateCdp $socket ([ref]$nextId) "(()=>{if(typeof window.__tsjAdvanceResistorServiceTime!=='function')return false;window.__tsjAdvanceResistorServiceTime(0.5);return true;})()" ([ref]$failures)
        if (-not $advance) { throw 'developer service-time bridge was unavailable' }
        Write-Host ("TASK34 PLAYER AFTER POWERED ADVANCE: " + (evaluateCdp $socket ([ref]$nextId) "typeof window.__tsjGetResistorStressState==='function'?window.__tsjGetResistorStressState():''" ([ref]$failures)))
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: ON' "document.body.innerText.includes('Board Power: OFF')" $deadline ([ref]$failures) 'stress power-off pause'
        $paused = evaluateCdp $socket ([ref]$nextId) "(()=>{window.__tsjAdvanceResistorServiceTime(5);return true;})()" ([ref]$failures)
        if (-not $paused) { throw 'powered-off service-time pause did not run' }
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'stress resume power-on'
        [void](evaluateCdp $socket ([ref]$nextId) "window.__tsjAdvanceResistorServiceTime(3);true" ([ref]$failures))
        Write-Host ("TASK34 PLAYER AFTER RESUME ADVANCE: " + (evaluateCdp $socket ([ref]$nextId) "typeof window.__tsjGetResistorStressState==='function'?window.__tsjGetResistorStressState():''" ([ref]$failures)))
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $failureState = evaluateCdp $socket ([ref]$nextId) "typeof window.__tsjGetResistorStressState==='function'?window.__tsjGetResistorStressState():''" ([ref]$failures)
        Write-Host "TASK34 PLAYER SECONDARY STATE: $failureState"
        if ([string]$failureState -notmatch 'failed=true' -or [string]$failureState -notmatch 'open=true') {
            throw "developer service-time advance did not reach the owned secondary-open state: $failureState"
        }
        $failureUi = evaluateCdp $socket ([ref]$nextId) "(()=>{const b=(document.body.innerText||''),l=b.toLowerCase();return {complaint:b.includes('Indicator does not light.'),value:b.includes('Value: 220 Ohm +/-5%'),power:b.includes('Board Power: ON'),diagnostic:/watt|stress|damage|overheat/.test(l),complete:b.includes('Repair verified. Indicator operating normally.')}})()" ([ref]$failures)
        if (-not ($failureUi.complaint -and $failureUi.value -and $failureUi.power) -or $failureUi.diagnostic -or $failureUi.complete) {
            throw "secondary-failure player UI leaked diagnostics or completed unexpectedly: $($failureUi | ConvertTo-Json -Compress)"
        }
        if ($failures.Count -gt 0) {
            throw ("secondary-failure normal-player path emitted console/page exceptions: " +
                ($failures -join '; '))
        }
        captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $evidence 'secondary-failure.png') ([ref]$failures)

        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: ON' "document.body.innerText.includes('Board Power: OFF')" $deadline ([ref]$failures) 'stress power-off before repair'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('R1_CATALOG_PART_0 - 220 Ohm +/-5%')" $deadline ([ref]$failures) 'stress severe replacement removal'
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '1000 Ohm +/-5%' ([ref]$failures)
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Install new resistor' "document.body.innerText.includes('Value: 1000 Ohm +/-5%')" $deadline ([ref]$failures) 'stress correct replacement installation'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'stress correct replacement power-on'
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Repair verified. Indicator operating normally.')" $deadline ([ref]$failures) 'stress natural solver-backed repair completion'
        captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $evidence 'correct-restored.png') ([ref]$failures)
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host 'PASS stress-damage-normal-player seed=3 severe-open natural-behavior no-diagnostic-ui no-console-or-page-exceptions'
        return $true
    } catch {
        Write-Host "FAIL stress-damage-normal-player seed=3 - $($_.Exception.Message)"
        if ($null -ne $socket) {
            try {
                $snapshot = evaluateCdp $socket ([ref]$nextId) "document.body.innerText" ([ref]$failures)
                $start = [Math]::Max(0, $snapshot.Length - 1800)
                Write-Host ("STRESS PLAYER UI SNAPSHOT: " + $snapshot.Substring($start).Replace("`n", ' | '))
            } catch { }
        }
        return $false
    } finally {
        cleanupBrowser $browser $socket $profile
    }
}

function verifyNormalLedPlayer([string]$url, [int]$debugPort) {
    $profile = Join-Path $env:TEMP ("tsj-led-player-" + [Guid]::NewGuid().ToString('N'))
    $arguments = @('--headless=new', '--disable-gpu', '--no-first-run', '--disable-sync',
        '--window-size=1440,1000', "--user-data-dir=$profile", "--remote-debugging-port=$debugPort", 'about:blank')
    $browser = Start-Process -FilePath $BrowserPath -ArgumentList $arguments -PassThru -WindowStyle Hidden
    $socket = $null
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        do {
            Start-Sleep -Milliseconds 200
            try {
                $targets = Invoke-RestMethod "http://127.0.0.1:$debugPort/json/list" -TimeoutSec 2
                $target = $targets | Where-Object { $_.type -eq 'page' } | Select-Object -First 1
            } catch { $target = $null }
        } while ($null -eq $target -and [DateTime]::UtcNow -lt $deadline)
        if ($null -eq $target) { throw 'browser target did not become available' }
        $socket = New-Object Net.WebSockets.ClientWebSocket
        $socket.ConnectAsync([Uri]$target.webSocketDebuggerUrl,
            [Threading.CancellationToken]::None).GetAwaiter().GetResult()
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.navigate' @{ url = $url } ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Indicator does not light.')" $deadline ([ref]$failures) 'ready LED challenge'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry&&!!window.__tsjPcbGeometry.points" $deadline ([ref]$failures) 'procedural LED PCB geometry bridge'
        $initial = evaluateCdp $socket ([ref]$nextId) "({canvas:!!document.querySelector('canvas'),ledCatalog:document.body.innerText.includes('LED Replacement Catalog'),empty:document.body.innerText.includes('No removed parts')})" ([ref]$failures)
        if (-not ($initial.canvas -and $initial.ledCatalog -and $initial.empty)) {
            throw 'initial LED workbench, catalog, or empty tray was missing'
        }
        if ($EvidenceDirectory) {
            [IO.Directory]::CreateDirectory($EvidenceDirectory) | Out-Null
            $initialEvidenceName = if ($PersistentPreviewEvidence) {
                'persistent-preview-fresh-load.png'
            } else { 'initial-board.png' }
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory $initialEvidenceName) ([ref]$failures)
        }
        if ($PersistentPreviewEvidence) {
            cleanupBrowser $browser $socket $profile
            $socket = $null
            $browser = $null
            Write-Host 'PASS persistent-preview fresh normal-player load'
            return $true
        }
        $led = getCanvasPoint $socket ([ref]$nextId) 'component:LED1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $led 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('LED1')&&document.body.innerText.includes('Lead A: LED1.A')&&document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'LED1 component controls'
        if ($EvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'led-selected.png') ([ref]$failures)
        }
        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('LED1_ORIGINAL - Generic red LED')&&!document.body.innerText.includes('No removed parts')" $deadline ([ref]$failures) 'loose original LED state'
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('LED1_ORIGINAL - Generic red LED')" $deadline ([ref]$failures) 'loose original LED'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry.points['loose:LED1_ORIGINAL:0']&&!!window.__tsjPcbGeometry.points['loose:LED1_ORIGINAL:1']" $deadline ([ref]$failures) 'loose original LED geometry'
        if ($EvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'led-removed-parts-tray.png') ([ref]$failures)
        }
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        $originalAnode = getCanvasPoint $socket ([ref]$nextId) 'loose:LED1_ORIGINAL:0' ([ref]$failures)
        $originalCathode = getCanvasPoint $socket ([ref]$nextId) 'loose:LED1_ORIGINAL:1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalAnode 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalCathode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "(()=>{const t=document.querySelector('.tsj-meter-display').innerText;return t!=='OL'&&t!=='--- V';})()" $deadline ([ref]$failures) 'loose original LED forward drop'
        $forward = evaluateCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText" ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalCathode 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText!=='$forward'" $deadline ([ref]$failures) 'LED probe reversal transition'
        clickPoint $socket ([ref]$nextId) $originalAnode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'loose original LED reverse OL'
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)

        clickButton $socket ([ref]$nextId) 'Install new LED' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Board Power: OFF' ([ref]$failures)
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $stillFaulted = evaluateCdp $socket ([ref]$nextId) "!document.body.innerText.includes('Repair verified.')&&document.body.innerText.includes('Indicator does not light.')" ([ref]$failures)
        if (-not $stillFaulted) { throw 'healthy LED incorrectly bypassed the original R1 fault' }
        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $led = getCanvasPoint $socket ([ref]$nextId) 'component:LED1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $led 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'healthy LED component controls'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('LED1_CATALOG_PART_0 - Generic red LED')&&!document.body.innerText.includes('No removed parts')" $deadline ([ref]$failures) 'healthy loose LED state'

        selectOptionWithKeyboard $socket ([ref]$nextId) 1 'Generic red LED (reversed)' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new LED' ([ref]$failures)
        $r1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('R1_ORIGINAL - Removed resistor')&&!document.body.innerText.includes('No removed parts')" $deadline ([ref]$failures) 'loose original R1 state'
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '1000 Ohm +/-5%' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new resistor' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Board Power: OFF' ([ref]$failures)
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $reversedBlocked = evaluateCdp $socket ([ref]$nextId) "!document.body.innerText.includes('Repair verified.')&&document.body.innerText.includes('Indicator does not light.')" ([ref]$failures)
        if (-not $reversedBlocked) { throw 'reversed LED incorrectly completed the challenge' }
        Write-Host 'LED PLAYER reversed installation remained nonfunctional'

        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        $led = getCanvasPoint $socket ([ref]$nextId) 'component:LED1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $led 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'reversed LED component controls'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('LED1_CATALOG_PART_0 - Generic red LED')&&!document.body.innerText.includes('No removed parts')" $deadline ([ref]$failures) 'reversed LED loose state'
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('LED1_CATALOG_PART_0 - Generic red LED')" $deadline ([ref]$failures) 'healthy loose LED identity'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry.points['loose:LED1_CATALOG_PART_0:0']&&!!window.__tsjPcbGeometry.points['loose:LED1_CATALOG_PART_0:1']" $deadline ([ref]$failures) 'healthy loose LED geometry'
        clickButton $socket ([ref]$nextId) 'LED1_CATALOG_PART_0 - Generic red LED' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install as LED1' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Board Power: OFF' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Repair verified. Indicator operating normally.')" $deadline ([ref]$failures) 'LED and R1 functional repair'
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        if ($EvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'repaired-board.png') ([ref]$failures)
        }
        Write-Host 'LED PLAYER repair verified'

        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        $led = getCanvasPoint $socket ([ref]$nextId) 'component:LED1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $led 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'repaired LED component controls'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('LED1_ORIGINAL - Generic red LED')&&document.body.innerText.includes('LED1_CATALOG_PART_0 - Generic red LED')&&!document.body.innerText.includes('No removed parts')" $deadline ([ref]$failures) 'separate original and replacement LED state'
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('LED1_ORIGINAL - Generic red LED')&&document.body.innerText.includes('LED1_CATALOG_PART_0 - Generic red LED')" $deadline ([ref]$failures) 'separate original and replacement LEDs'
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host "PASS led-normal-player seed=3 forward=$forward reverse=OL identities=separate"
        return $true
    } catch {
        Write-Host "FAIL led-normal-player seed=3 - $($_.Exception.Message)"
        if ($null -ne $socket) {
            try {
                $snapshot = evaluateCdp $socket ([ref]$nextId) "document.body.innerText" ([ref]$failures)
                $start = [Math]::Max(0, $snapshot.Length - 1800)
                Write-Host ("LED PLAYER UI SNAPSHOT: " + $snapshot.Substring($start).Replace("`n", " | "))
            } catch { }
        }
        return $false
    } finally {
        cleanupBrowser $browser $socket $profile
    }
}

if (-not (Test-Path $BrowserPath -PathType Leaf)) { throw "Browser not found: $BrowserPath" }
if ($QuickPlay) {
    $selectorPassed = verifyRoute 'quick-play selector/session' "$BaseUrl/circuitjs.html?tsjQuickPlay=true&tsjVerifyQuickPlay=true&tsjQuickPlayTestSeed=3" 'PASS:quick-play' 9495 | Select-Object -Last 1
    if (-not $selectorPassed) { exit 1 }
    $explicitPassed = verifyRoute 'quick-play explicit precedence' "$BaseUrl/circuitjs.html?tsjQuickPlay=true&tsjChallenge=led&seed=3&tsjVerifyQuickPlay=true&tsjQuickPlayTestSeed=3" 'PASS:quick-play-explicit' 9496 | Select-Object -Last 1
    if (-not $explicitPassed) { exit 1 }
    $normalPassed = verifyQuickPlayNormalPlayer "$BaseUrl/circuitjs.html?tsjQuickPlay=true" 9497 ([bool]$selectorPassed) | Select-Object -Last 1
    if (-not $normalPassed) { exit 1 }
    exit 0
}
if ($Layout) {
    if (-not (verifyRoute "procedural-layout" "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=$PlayerSeed&tsjVerifyLayout=true&tsjVerifyGeometry=true" 'PASS:layout' 9440)) { exit 1 }
    exit 0
}
if ($Architecture) {
    if (-not (verifyRoute 'architecture seams' "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyArchitecture=true" 'PASS:architecture' 9494)) { exit 1 }
    exit 0
}
if ($WrongRepair) {
    if (-not (verifyRoute 'seed=3 wrong-repair' "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyWrongRepair=true" 'PASS:wrong-repair' 9491)) { exit 1 }
    exit 0
}
if ($NormalPlayer) {
    if (-not (verifyNormalPlayer "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=$PlayerSeed&tsjVerifyGeometry=true" 9450)) { exit 1 }
    exit 0
}
if ($WrongRepairNormalPlayer) {
    if (-not (verifyWrongRepairNormalPlayer "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyGeometry=true" 9490)) { exit 1 }
    exit 0
}
if ($StressDamage) {
    if (-not (verifyRoute 'seed=3 stress-damage' "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyStress=true" 'PASS:stress' 9492)) { exit 1 }
    exit 0
}
if ($StressDamageNormalPlayer) {
    if (-not (verifyStressDamageNormalPlayer "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyStress=true&tsjStressDeferred=true&tsjVerifyGeometry=true" 9493)) { exit 1 }
    exit 0
}
if ($DiodeNormalPlayer) {
    if (-not (verifyNormalDiodePlayer "$BaseUrl/circuitjs.html?tsjChallenge=diode&seed=$PlayerSeed&tsjVerifyGeometry=true" 9460)) { exit 1 }
    exit 0
}
if ($ParallelNormalPlayer) {
    if (-not (verifyNormalParallelPlayer "$BaseUrl/circuitjs.html?tsjChallenge=parallel&seed=3&tsjVerifyGeometry=true" 9480)) { exit 1 }
    exit 0
}
if ($LedNormalPlayer) {
    if (-not (verifyNormalLedPlayer "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=$PlayerSeed&tsjVerifyGeometry=true" 9470)) { exit 1 }
    exit 0
}
$family = 'led'
$routes = @(
    @{ Name = 'resistance'; Query = 'tsjVerifyResistance=true'; Expected = 'PASS:resistance'; Complaint = 'Indicator does not light.' },
    @{ Name = 'meter'; Query = 'tsjVerifyMeter=true'; Expected = 'PASS:meter'; Complaint = 'Indicator does not light.' },
    @{ Name = 'challenge'; Query = 'tsjVerifyChallenge=true'; Expected = 'PASS:challenge'; Complaint = 'Indicator does not light.' },
    @{ Name = 'replacement'; Query = 'tsjVerifyReplacement=true'; Expected = 'PASS:replacement'; Complaint = 'Indicator does not light.' },
    @{ Name = 'challenge+replacement'; Query = 'tsjVerifyChallenge=true&tsjVerifyReplacement=true'; Expected = 'PASS:replacement'; Complaint = 'Indicator does not light.' }
)
if ($DiodeShort) {
    $family = 'diode'
    $routes = @(@{ Name = 'diode-short'; Query = 'tsjVerifyDiode=true&tsjDiodeShort=true'; Expected = 'PASS:diode'; Complaint = 'The indicator is brighter than expected.' })
} elseif ($Diode) {
    $family = 'diode'
    $routes = @(@{ Name = 'diode'; Query = 'tsjVerifyDiode=true'; Expected = 'PASS:diode'; Complaint = 'Indicator does not light.' })
}
if ($Parallel) {
    $family = 'parallel'
    $routes = @(@{ Name = 'parallel'; Query = 'tsjVerifyParallel=true'; Expected = 'PASS:parallel'; Complaint = 'The two indicators do not behave the same.' })
}
if ($LedParts) {
    $family = 'led'
    $routes = @(@{ Name = 'led-parts'; Query = 'tsjVerifyLedParts=true'; Expected = 'PASS:led-parts'; Complaint = 'Indicator does not light.' })
}
$passed = $true
$index = 0
if ($Route) { $routes = @($routes | Where-Object { $_.Name -eq $Route }) }
if ($routes.Count -eq 0) { throw "Unknown route: $Route" }
$expectedCount = $Seeds.Count * $routes.Count
foreach ($seed in $Seeds) {
    foreach ($routeDefinition in $routes) {
        $url = "$BaseUrl/circuitjs.html?tsjChallenge=$family&seed=$seed&$($routeDefinition.Query)"
        $complaint = if ($routeDefinition.PSObject.Properties['Complaint']) {
            [string]$routeDefinition.Complaint
        } else { '' }
        $routePassed = verifyRoute "seed=$seed $($routeDefinition.Name)" $url $routeDefinition.Expected (9350 + $index) $complaint |
            Select-Object -Last 1
        if (-not $routePassed) { $passed = $false }
        $index++
    }
}
if (-not $passed) { exit 1 }
Write-Host "All $expectedCount browser verifier routes passed."

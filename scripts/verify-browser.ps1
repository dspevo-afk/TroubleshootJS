[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8899',
    [ValidateRange(10, 300)]
    [int]$TimeoutSeconds = 90,
    [string]$BrowserPath = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
    [int[]]$Seeds = @(0, 2, 3),
    [string]$Route,
    [switch]$NormalPlayer
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function sendCdp($socket, [int]$id, [string]$method, $parameters) {
    $message = @{ id = $id; method = $method; params = $parameters } | ConvertTo-Json -Compress -Depth 8
    $bytes = [Text.Encoding]::UTF8.GetBytes($message)
    $socket.SendAsync((New-Object ArraySegment[byte] -ArgumentList (,$bytes)),
        [Net.WebSockets.WebSocketMessageType]::Text, $true, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
}

function receiveCdp($socket, [int]$wantedId, [ref]$failures) {
    while ($true) {
        $stream = New-Object IO.MemoryStream
        do {
            $buffer = New-Object byte[] 65536
            $receiveTimeout = New-Object Threading.CancellationTokenSource 5000
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
            $failures.Value += 'JavaScript exception: ' + $message.params.exceptionDetails.text
        }
        if ($method -eq 'Runtime.consoleAPICalled') {
            $text = ($message.params.args | ForEach-Object { $_.value }) -join ' '
            if ($text -match '(?i)verification failed|generated board verification failed|uncaught|exception') {
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

function verifyRoute([string]$name, [string]$url, [string]$expected, [int]$debugPort) {
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
        [void](invokeCdp $socket ([ref]$nextId) 'Page.navigate' @{ url = $url } ([ref]$failures))
        do {
            $response = invokeCdp $socket ([ref]$nextId) 'Runtime.evaluate' @{
                expression = "document.documentElement.getAttribute('data-tsj-verification') || ''"; returnByValue = $true
            } ([ref]$failures)
            $verificationResult = [string]$response.result.result.value
            if ($verificationResult.StartsWith('FAIL:')) { throw $verificationResult }
            if ($verificationResult -eq $expected) { break }
            Start-Sleep -Milliseconds 250
        } while ([DateTime]::UtcNow -lt $deadline)
        if ($verificationResult -ne $expected) { throw "timed out waiting for '$expected'" }
        Start-Sleep -Milliseconds 100
        [void](evaluateCdp $socket ([ref]$nextId) "document.readyState" ([ref]$failures))
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        Write-Host "PASS $name"
        $success = $true
    } catch {
        Write-Host "FAIL $name - $($_.Exception.Message)"
    } finally {
        if ($null -ne $socket) { $socket.Dispose() }
        if (-not $browser.HasExited) { Stop-Process -Id $browser.Id -Force }
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
    $point = evaluateCdp $socket $nextId "(()=>{const e=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='$escaped');if(!e)return null;const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2};})()" $failures
    if ($null -eq $point) { throw "button not found: $text" }
    clickPoint $socket $nextId $point 'left' $failures
}

function getCanvasPoint($socket, [ref]$nextId, [double]$logicalX, [double]$logicalY,
        [ref]$failures) {
    return evaluateCdp $socket $nextId "(()=>{const c=[...document.querySelectorAll('canvas')].find(x=>{const r=x.getBoundingClientRect();return r.width>100&&r.height>100});if(!c)return null;const r=c.getBoundingClientRect();const s=Math.min(1.35,Math.min((c.width-30)/1040,(c.height-30)/520));const ox=(c.width-s*1040)/2;const oy=(c.height-s*520)/2;return {x:r.left+(ox+s*$logicalX)*r.width/c.width,y:r.top+(oy+s*$logicalY)*r.height/c.height};})()" $failures
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
        Write-Host 'PLAYER ready'
        $initial = evaluateCdp $socket ([ref]$nextId) "({canvas:!!document.querySelector('canvas'),meter:!!document.querySelector('.tsj-meter-panel'),power:[...document.querySelectorAll('button')].some(x=>x.innerText.includes('Board Power')),catalog:document.body.innerText.includes('Replacement Catalog'),empty:document.body.innerText.includes('No removed parts')})" ([ref]$failures)
        if (-not ($initial.canvas -and $initial.meter -and $initial.power -and $initial.catalog -and $initial.empty)) {
            throw 'initial PCB, meter, power, catalog, or empty tray UI was missing'
        }
        $r1 = getCanvasPoint $socket ([ref]$nextId) 445 220 ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'R1 component controls'
        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "[...document.querySelectorAll('button')].filter(x=>x.innerText.trim()==='1000 Ohm +/-5%').length===1" $deadline ([ref]$failures) 'faulted original in tray'
        Write-Host 'PLAYER original removed'

        $selectInfo = evaluateCdp $socket ([ref]$nextId) "(()=>{const e=document.querySelector('select');const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,index:[...e.options].findIndex(o=>o.text==='1000 Ohm +/-5%')};})()" ([ref]$failures)
        $selectedText = ''
        for ($selectionAttempt = 0; $selectionAttempt -lt 3 -and
                $selectedText -ne '1000 Ohm +/-5%'; $selectionAttempt++) {
            clickPoint $socket ([ref]$nextId) $selectInfo 'left' ([ref]$failures)
            Start-Sleep -Milliseconds 100
            sendKey $socket ([ref]$nextId) 'Escape' 27 ([ref]$failures)
            sendKey $socket ([ref]$nextId) 'Home' 36 ([ref]$failures)
            for ($keyIndex = 0; $keyIndex -lt [int]$selectInfo.index; $keyIndex++) {
                sendKey $socket ([ref]$nextId) 'ArrowDown' 40 ([ref]$failures)
            }
            sendKey $socket ([ref]$nextId) 'Enter' 13 ([ref]$failures)
            $selectedText = evaluateCdp $socket ([ref]$nextId) "document.querySelector('select').selectedOptions[0].text" ([ref]$failures)
        }
        if ($selectedText -ne '1000 Ohm +/-5%') { throw "catalog keyboard selection chose $selectedText" }
        clickButton $socket ([ref]$nextId) 'Install new resistor' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "[...document.querySelectorAll('button')].filter(x=>x.innerText.trim()==='1000 Ohm +/-5%').length===1" $deadline ([ref]$failures) 'installed replacement excluded from tray'
        clickButton $socket ([ref]$nextId) 'Board Power: OFF' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Repair verified. Indicator operating normally.')" $deadline ([ref]$failures) 'solver-backed repair completion'
        Write-Host 'PLAYER repair verified'
        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "[...document.querySelectorAll('button')].filter(x=>x.innerText.trim()==='1000 Ohm +/-5%').length===2" $deadline ([ref]$failures) 'two distinct loose 1 kOhm parts'
        Write-Host 'PLAYER healthy replacement removed'
        clickButton $socket ([ref]$nextId) 'OHM' ([ref]$failures)

        $healthyLeft = getCanvasPoint $socket ([ref]$nextId) 868 243 ([ref]$failures)
        $healthyRight = getCanvasPoint $socket ([ref]$nextId) 982 243 ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $healthyLeft 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $healthyRight 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "(()=>{const t=document.querySelector('.tsj-meter-display').innerText;return t!=='OL'&&t!=='--- Ohm';})()" $deadline ([ref]$failures) 'healthy forward resistance'
        $forward = evaluateCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText" ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $healthyRight 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $healthyLeft 'right' ([ref]$failures)
        $reverse = evaluateCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText" ([ref]$failures)
        if ($forward -ne $reverse -or $forward -notmatch '(?i)1(\.0+)?\s*kOhm') {
            throw "healthy resistance mismatch: forward=$forward reverse=$reverse"
        }
        $originalLeft = getCanvasPoint $socket ([ref]$nextId) 868 195 ([ref]$failures)
        $originalRight = getCanvasPoint $socket ([ref]$nextId) 982 195 ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalLeft 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalRight 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'faulted original OL'
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        Write-Host "PASS normal-player seed=3 forward=$forward reverse=$reverse original=OL"
        return $true
    } catch {
        Write-Host "FAIL normal-player seed=3 - $($_.Exception.Message)"
        return $false
    } finally {
        if ($null -ne $socket) { $socket.Dispose() }
        if (-not $browser.HasExited) { Stop-Process -Id $browser.Id -Force }
    }
}

if (-not (Test-Path $BrowserPath -PathType Leaf)) { throw "Browser not found: $BrowserPath" }
if ($NormalPlayer) {
    if (-not (verifyNormalPlayer "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3" 9450)) { exit 1 }
    exit 0
}
$routes = @(
    @{ Name = 'resistance'; Query = 'tsjVerifyResistance=true'; Expected = 'PASS:resistance' },
    @{ Name = 'meter'; Query = 'tsjVerifyMeter=true'; Expected = 'PASS:meter' },
    @{ Name = 'challenge'; Query = 'tsjVerifyChallenge=true'; Expected = 'PASS:challenge' },
    @{ Name = 'replacement'; Query = 'tsjVerifyReplacement=true'; Expected = 'PASS:replacement' },
    @{ Name = 'challenge+replacement'; Query = 'tsjVerifyChallenge=true&tsjVerifyReplacement=true'; Expected = 'PASS:replacement' }
)
$passed = $true
$index = 0
if ($Route) { $routes = @($routes | Where-Object { $_.Name -eq $Route }) }
if ($routes.Count -eq 0) { throw "Unknown route: $Route" }
foreach ($seed in $Seeds) {
    foreach ($routeDefinition in $routes) {
        $url = "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=$seed&$($routeDefinition.Query)"
        $routePassed = verifyRoute "seed=$seed $($routeDefinition.Name)" $url $routeDefinition.Expected (9350 + $index) |
            Select-Object -Last 1
        if (-not $routePassed) { $passed = $false }
        $index++
    }
}
if (-not $passed) { exit 1 }
Write-Host "All $($Seeds.Count * $routes.Count) browser verifier routes passed."

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
    [switch]$DiodeNormalPlayer,
    [switch]$LedParts,
    [switch]$LedNormalPlayer,
    [string]$EvidenceDirectory,
    [switch]$PersistentPreviewEvidence
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function sendCdp($socket, [int]$id, [string]$method, $parameters) {
    $message = @{ id = $id; method = $method; params = $parameters } | ConvertTo-Json -Compress -Depth 8
    $bytes = [Text.Encoding]::UTF8.GetBytes($message)
    $socket.SendAsync((New-Object ArraySegment[byte] -ArgumentList (,$bytes)),
        [Net.WebSockets.WebSocketMessageType]::Text, $true, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
}

function cleanupBrowser($browser, $socket, [string]$profile) {
    if ($null -ne $socket) {
        try { [void](sendCdp $socket 2147483000 'Browser.close' @{}) } catch { }
        $socket.Dispose()
    }
    if (-not $browser.WaitForExit(5000)) {
        Stop-Process -Id $browser.Id -Force -ErrorAction SilentlyContinue
        [void]$browser.WaitForExit(5000)
    }
    $cleanupError = $null
    for ($attempt = 0; $attempt -lt 10 -and (Test-Path -LiteralPath $profile); $attempt++) {
        try {
            Remove-Item -LiteralPath $profile -Recurse -Force -ErrorAction Stop
            $cleanupError = $null
        } catch {
            $cleanupError = $_.Exception.Message
            Start-Sleep -Milliseconds 200
        }
    }
    if (Test-Path -LiteralPath $profile) {
        Write-Warning "Could not remove temporary browser profile '$profile': $cleanupError"
    }
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
    for ($selectionAttempt = 0; $selectionAttempt -lt 3 -and $actual -ne $optionText;
            $selectionAttempt++) {
        $focusPlan = evaluateCdp $socket $nextId "(()=>{const target=document.querySelectorAll('select')[$selectIndex];const focusable=[...document.querySelectorAll('*')].filter(x=>x.tabIndex>=0&&!x.disabled&&x.getClientRects().length);const targetIndex=focusable.indexOf(target),currentIndex=focusable.indexOf(document.activeElement);return {found:targetIndex>=0,tabs:currentIndex<0?targetIndex+1:(targetIndex-currentIndex+focusable.length)%focusable.length};})()" $failures
        if (-not $focusPlan.found) { throw "catalog is not keyboard reachable: $optionText" }
        for ($tabIndex = 0; $tabIndex -lt [int]$focusPlan.tabs; $tabIndex++) {
            sendKey $socket $nextId 'Tab' 9 $failures
        }
        $focused = evaluateCdp $socket $nextId "document.activeElement===document.querySelectorAll('select')[$selectIndex]" $failures
        if (-not $focused) { continue }
        sendKey $socket $nextId 'Home' 36 $failures
        for ($index = 0; $index -lt [int]$info.index; $index++) {
            sendKey $socket $nextId 'ArrowDown' 40 $failures
        }
        $actual = evaluateCdp $socket $nextId "document.querySelectorAll('select')[$selectIndex].selectedOptions[0].text" $failures
    }
    if ($actual -ne $optionText) {
        $focus = evaluateCdp $socket $nextId "(()=>{const e=document.querySelectorAll('select')[$selectIndex];return {active:document.activeElement===e,tag:document.activeElement&&document.activeElement.tagName,y:e.getBoundingClientRect().top};})()" $failures
        throw "catalog keyboard selection chose $actual instead of $optionText (active=$($focus.active), tag=$($focus.tag), y=$($focus.y))"
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
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText!=='$forward'" $deadline ([ref]$failures) 'intermediate reversed-probe measurement'
        clickPoint $socket ([ref]$nextId) $healthyLeft 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "(()=>{const t=document.querySelector('.tsj-meter-display').innerText;return t!=='OL'&&t!=='--- Ohm';})()" $deadline ([ref]$failures) 'healthy reverse resistance'
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
        $initial = evaluateCdp $socket ([ref]$nextId) "({canvas:!!document.querySelector('canvas'),catalog:document.body.innerText.includes('Replacement Catalog'),empty:document.body.innerText.includes('No removed parts'),disclosed:/D1 failed|diode is open/i.test(document.body.innerText)})" ([ref]$failures)
        if (-not ($initial.canvas -and $initial.catalog -and $initial.empty) -or $initial.disclosed) {
            throw 'initial diode workbench, catalog, tray, or vague complaint was incorrect'
        }
        $pixels = evaluateCdp $socket ([ref]$nextId) "(()=>{const c=[...document.querySelectorAll('canvas')].find(x=>{const r=x.getBoundingClientRect();return r.width>100&&r.height>100});const g=c.getContext('2d');const s=Math.min(1.35,Math.min((c.width-30)/1040,(c.height-30)/520));const ox=(c.width-s*1040)/2,oy=(c.height-s*520)/2;const p=(x,y)=>[...g.getImageData(Math.round(ox+s*x),Math.round(oy+s*y),1,1).data].slice(0,3).join(',');return {body:p(330,220),band:p(367,220),led:p(710,187)};})()" ([ref]$failures)
        if ($pixels.body -eq $pixels.led -or $pixels.band -eq $pixels.body) {
            throw "D1 body, cathode band, and LED1 were not visibly distinct: $($pixels | ConvertTo-Json -Compress)"
        }
        Write-Host "DIODE PLAYER rendered body=$($pixels.body) band=$($pixels.band) led=$($pixels.led)"

        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        $d1 = getCanvasPoint $socket ([ref]$nextId) 330 220 ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $d1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'D1 component controls'
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        $d1Anode = getCanvasPoint $socket ([ref]$nextId) 250 220 ([ref]$failures)
        $d1Cathode = getCanvasPoint $socket ([ref]$nextId) 410 220 ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $d1Anode 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $d1Cathode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'installed open D1 forward OL'
        clickPoint $socket ([ref]$nextId) $d1Cathode 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText!=='OL'" $deadline ([ref]$failures) 'installed D1 intermediate measurement'
        clickPoint $socket ([ref]$nextId) $d1Anode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'installed open D1 reverse OL'
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('D1_ORIGINAL - Generic silicon diode')" $deadline ([ref]$failures) 'loose original diode'

        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        $originalAnode = getCanvasPoint $socket ([ref]$nextId) 868 195 ([ref]$failures)
        $originalCathode = getCanvasPoint $socket ([ref]$nextId) 982 195 ([ref]$failures)
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
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('D1_CATALOG_PART_0 - Generic silicon diode')&&document.body.innerText.includes('D1_ORIGINAL - Generic silicon diode')" $deadline ([ref]$failures) 'separate loose diode identities'
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        $healthyAnode = getCanvasPoint $socket ([ref]$nextId) 868 243 ([ref]$failures)
        $healthyCathode = getCanvasPoint $socket ([ref]$nextId) 982 243 ([ref]$failures)
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
            Write-Host 'PASS persistent-preview fresh normal-player load'
            return $true
        }
        $led = getCanvasPoint $socket ([ref]$nextId) 710 187 ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $led 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('LED1')&&document.body.innerText.includes('Lead A: LED1.A')&&document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'LED1 component controls'
        if ($EvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'led-selected.png') ([ref]$failures)
        }
        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('LED1_ORIGINAL - Generic red LED')" $deadline ([ref]$failures) 'loose original LED'
        if ($EvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $EvidenceDirectory 'led-removed-parts-tray.png') ([ref]$failures)
        }
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        $originalAnode = getCanvasPoint $socket ([ref]$nextId) 868 195 ([ref]$failures)
        $originalCathode = getCanvasPoint $socket ([ref]$nextId) 982 195 ([ref]$failures)
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
        $led = getCanvasPoint $socket ([ref]$nextId) 710 200 ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $led 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'healthy LED component controls'
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)

        selectOptionWithKeyboard $socket ([ref]$nextId) 1 'Generic red LED (reversed)' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new LED' ([ref]$failures)
        $r1 = getCanvasPoint $socket ([ref]$nextId) 445 220 ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '1000 Ohm +/-5%' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new resistor' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Board Power: OFF' ([ref]$failures)
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $reversedBlocked = evaluateCdp $socket ([ref]$nextId) "!document.body.innerText.includes('Repair verified.')&&document.body.innerText.includes('Indicator does not light.')" ([ref]$failures)
        if (-not $reversedBlocked) { throw 'reversed LED incorrectly completed the challenge' }
        Write-Host 'LED PLAYER reversed installation remained nonfunctional'

        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        $led = getCanvasPoint $socket ([ref]$nextId) 710 200 ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $led 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'reversed LED component controls'
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('LED1_CATALOG_PART_0 - Generic red LED')" $deadline ([ref]$failures) 'healthy loose LED identity'
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
        $led = getCanvasPoint $socket ([ref]$nextId) 710 200 ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $led 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'repaired LED component controls'
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('LED1_ORIGINAL - Generic red LED')&&document.body.innerText.includes('LED1_CATALOG_PART_0 - Generic red LED')" $deadline ([ref]$failures) 'separate original and replacement LEDs'
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
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
if ($NormalPlayer) {
    if (-not (verifyNormalPlayer "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3" 9450)) { exit 1 }
    exit 0
}
if ($DiodeNormalPlayer) {
    if (-not (verifyNormalDiodePlayer "$BaseUrl/circuitjs.html?tsjChallenge=diode&seed=3" 9460)) { exit 1 }
    exit 0
}
if ($LedNormalPlayer) {
    if (-not (verifyNormalLedPlayer "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3" 9470)) { exit 1 }
    exit 0
}
$family = 'led'
$routes = @(
    @{ Name = 'resistance'; Query = 'tsjVerifyResistance=true'; Expected = 'PASS:resistance' },
    @{ Name = 'meter'; Query = 'tsjVerifyMeter=true'; Expected = 'PASS:meter' },
    @{ Name = 'challenge'; Query = 'tsjVerifyChallenge=true'; Expected = 'PASS:challenge' },
    @{ Name = 'replacement'; Query = 'tsjVerifyReplacement=true'; Expected = 'PASS:replacement' },
    @{ Name = 'challenge+replacement'; Query = 'tsjVerifyChallenge=true&tsjVerifyReplacement=true'; Expected = 'PASS:replacement' }
)
if ($Diode) {
    $family = 'diode'
    $routes = @(@{ Name = 'diode'; Query = 'tsjVerifyDiode=true'; Expected = 'PASS:diode' })
}
if ($LedParts) {
    $family = 'led'
    $routes = @(@{ Name = 'led-parts'; Query = 'tsjVerifyLedParts=true'; Expected = 'PASS:led-parts' })
}
$passed = $true
$index = 0
if ($Route) { $routes = @($routes | Where-Object { $_.Name -eq $Route }) }
if ($routes.Count -eq 0) { throw "Unknown route: $Route" }
$expectedCount = $Seeds.Count * $routes.Count
foreach ($seed in $Seeds) {
    foreach ($routeDefinition in $routes) {
        $url = "$BaseUrl/circuitjs.html?tsjChallenge=$family&seed=$seed&$($routeDefinition.Query)"
        $routePassed = verifyRoute "seed=$seed $($routeDefinition.Name)" $url $routeDefinition.Expected (9350 + $index) |
            Select-Object -Last 1
        if (-not $routePassed) { $passed = $false }
        $index++
    }
}
if (-not $passed) { exit 1 }
Write-Host "All $expectedCount browser verifier routes passed."

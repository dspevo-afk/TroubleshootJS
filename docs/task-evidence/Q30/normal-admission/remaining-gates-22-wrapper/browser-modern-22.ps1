#requires -Version 5.1
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$RepoRoot)
. (Join-Path $PSScriptRoot 'Q30GateWrapper.Common.ps1')
$env:PYTHONDONTWRITEBYTECODE = '1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
$runDirectory = Join-Path $PSScriptRoot ('browser-modern-22-run-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runDirectory | Out-Null
$rootResultPath = Join-Path $runDirectory 'browser-modern-22-root-result.json'
$run = [ordered]@{
    gate = 'browser-modern-22'; status = 'NOT_RUN'; startedUtc = [DateTime]::UtcNow.ToString('o'); finishedUtc = $null
    repoRoot = $null; runDirectory = $runDirectory; driverOutputDirectory = $null; resultJsonPath = $null
    processExitCode = $null; inputAudit = [ordered]@{}; checks = [ordered]@{}; errors = @()
}
$repo = $null; $webManifest = $null; $buildManifest = $null; $webBefore = $null; $buildBefore = $null; $browserBefore = $null
$driverRelative = 'tests/browser/quickplay_procedural_acceptance.py'
try {
    $repo = [IO.Path]::GetFullPath($RepoRoot); $run.repoRoot = $repo; Set-TaskPointer $repo '.tools/q30-modern-22-wrapper-path.txt' $runDirectory
    $webManifest = Get-InputMapFromManifest $repo 'docs/task-evidence/Q30/normal-admission/web-final-21-inputs.json' 392
    $buildManifest = Get-InputMapFromManifest $repo 'docs/task-evidence/Q30/normal-admission/build-final-21-inputs.json' 1133
    if ($webManifest.Document.buildInputsUnchanged -ne $true) { throw 'web-final-21 does not declare unchanged build inputs.' }
    if ($webManifest.Files.Contains($driverRelative)) { throw 'Browser driver overlaps the 392 web paths.' }
    $webManifestShaBefore = (Get-FileHash $webManifest.Path -Algorithm SHA256).Hash.ToLowerInvariant()
    $buildManifestShaBefore = (Get-FileHash $buildManifest.Path -Algorithm SHA256).Hash.ToLowerInvariant()
    $webBefore = Get-CurrentInputHashes $repo @($webManifest.Files.Keys)
    $buildBefore = Get-CurrentInputHashes $repo @($buildManifest.Files.Keys)
    $driverBefore = Get-CurrentInputHashes $repo @($driverRelative)
    $browserBefore = [ordered]@{}
    foreach ($path in $webBefore.Keys) { $browserBefore[$path] = $webBefore[$path] }
    $browserBefore[$driverRelative] = $driverBefore[$driverRelative]
    if ($browserBefore.Count -ne 393) { throw ('Expected 393 browser inputs, got ' + $browserBefore.Count) }
    Assert-MapMatchesManifest $webManifest.Files $webBefore 'web-final-21'
    Assert-MapMatchesManifest $buildManifest.Files $buildBefore 'build-final-21'
    $run.inputAudit.beforeSha256 = Get-InputMapSha256 $browserBefore
    $run.inputAudit.beforePath = Join-Path $runDirectory 'browser-modern-22-inputs-before.json'
    Save-JsonUtf8 $run.inputAudit.beforePath ([ordered]@{
        capturedUtc = [DateTime]::UtcNow.ToString('o')
        browserCount = $browserBefore.Count; browserSha256 = Get-InputMapSha256 $browserBefore; browserFiles = $browserBefore
        webManifestSha256 = $webManifestShaBefore; webCount = $webBefore.Count; webFiles = $webBefore
        buildManifestSha256 = $buildManifestShaBefore; buildCount = $buildBefore.Count; buildFiles = $buildBefore
    })
    $run.checks.webManifestMatchedBefore = $true; $run.checks.buildManifestMatchedBefore = $true

    $driver = Get-RepoAbsolutePath $repo $driverRelative
    $pythonExe = (Get-Command python.exe -CommandType Application -ErrorAction Stop).Source
    $driverOutput = Join-Path $runDirectory 'browser-driver-output'
    $run.driverOutputDirectory = $driverOutput; $run.resultJsonPath = Join-Path $driverOutput 'result.json'; Set-TaskPointer $repo '.tools/q30-modern-22-path.txt' $driverOutput
    $log = Join-Path $runDirectory 'browser-modern-22.log'
    Write-Host ('START browser-modern-22; log=' + $log)
    $invocation = Invoke-CapturedCommand $pythonExe @('-B', $driver, $repo, $driverOutput) $log $repo
    $run.processExitCode = $invocation.ExitCode
    if (-not (Test-Path -LiteralPath $run.resultJsonPath -PathType Leaf)) { throw 'Browser driver did not write result.json; log retained.' }
    $result = [IO.File]::ReadAllText($run.resultJsonPath,[Text.Encoding]::UTF8) | ConvertFrom-Json
    $cases = @($result.cases); $replays = @($result.replays)
    $expected = @(
        @{id='LED_INDICATOR';profile='EASY'}, @{id='DIODE_PROTECTED_INDICATOR';profile='EASY'},
        @{id='PARALLEL_DUAL_INDICATOR';profile='EASY'}, @{id='RC_DELAY';profile='EASY'},
        @{id='NPN_LOW_SIDE_SWITCH';profile='EASY'}, @{id='NMOS_LOW_SIDE_SWITCH';profile='EASY'},
        @{id='RELAY_OUTPUT';profile='EASY'}, @{id='SENSOR_CONTROL';profile='EASY'},
        @{id='RB15_CONTROL';profile='EASY'}, @{id='COMPOSED_CONTROLLED_INDICATOR';profile='MEDIUM'},
        @{id='RB30_CONTROL';profile='MEDIUM'}
    )
    $catalogPass = ($cases.Count -eq 33)
    foreach ($family in $expected) {
        $rows = @($cases | Where-Object { $_.family -ceq $family.id -and $_.profile -ceq $family.profile })
        if ($rows.Count -ne 3 -or (@($rows | ForEach-Object { [int]$_.ordinal } | Sort-Object) -join ',') -cne '0,1,2' -or
            @($rows | ForEach-Object { [string]$_.replay } | Select-Object -Unique).Count -ne 3) { $catalogPass = $false }
    }
    $casePass = ($cases.Count -eq 33 -and @($cases | Where-Object { $_.outcome -cne 'PASS' }).Count -eq 0)
    $replayFamilies = @($replays | ForEach-Object { [string]$_.family })
    $replayPass = ($replays.Count -eq 3 -and ($replayFamilies -join ',') -ceq 'RELAY_OUTPUT,COMPOSED_CONTROLLED_INDICATOR,RB30_CONTROL' -and
        @($replays | Where-Object { $_.outcome -cne 'PASS' -or $_.isolated -ne $true }).Count -eq 0)
    $errorCount = 0; if ($null -ne $result.errors) { $errorCount = @($result.errors).Count }
    $survivors = 0; if ($null -ne $result.cleanup.ownedSurvivors) { $survivors = @($result.cleanup.ownedSurvivors).Count }
    $cleanupPass = ($result.cleanup.serverStopped -eq $true -and $survivors -eq 0)
    $run.checks.driverExitZero = ($invocation.ExitCode -eq 0)
    $run.checks.resultPass = ($result.outcome -ceq 'PASS')
    $run.checks.thirtyThreeCasesPass = $casePass
    $run.checks.catalogAndUniqueReplays = $catalogPass
    $run.checks.threeSavedReplaysPass = $replayPass
    $run.checks.noPageErrors = ($errorCount -eq 0)
    $run.checks.cleanupPass = $cleanupPass
    $run.driverSummary = [ordered]@{ outcome=$result.outcome; caseCount=$cases.Count; replayCount=$replays.Count; errors=$errorCount; serverStopped=$result.cleanup.serverStopped; ownedSurvivors=$survivors }
    if ($invocation.ExitCode -ne 0 -or $result.outcome -cne 'PASS' -or -not $casePass -or -not $catalogPass -or -not $replayPass -or $errorCount -ne 0 -or -not $cleanupPass) {
        $run.errors += 'Browser result failed its 33-case, three-replay, error and cleanup requirements.'
    }
} catch { $run.errors += $_.Exception.ToString(); Write-Host ('FAIL browser-modern-22: ' + $_.Exception.Message) }
finally {
    if ($null -ne $repo -and $null -ne $webBefore -and $null -ne $buildBefore -and $null -ne $browserBefore) {
        try {
            $webAfter = Get-CurrentInputHashes $repo @($webBefore.Keys); $buildAfter = Get-CurrentInputHashes $repo @($buildBefore.Keys)
            $browserAfter = Get-CurrentInputHashes $repo @($browserBefore.Keys)
            Assert-InputMapUnchanged $browserBefore $browserAfter '393 browser inputs'
            Assert-InputMapUnchanged $buildBefore $buildAfter '1133 build inputs'
            Assert-MapMatchesManifest $webManifest.Files $webAfter 'web-final-21 after browser'
            Assert-MapMatchesManifest $buildManifest.Files $buildAfter 'build-final-21 after browser'
            $webManifestShaAfter = (Get-FileHash $webManifest.Path -Algorithm SHA256).Hash.ToLowerInvariant()
            $buildManifestShaAfter = (Get-FileHash $buildManifest.Path -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($webManifestShaAfter -cne $webManifestShaBefore -or $buildManifestShaAfter -cne $buildManifestShaBefore) { throw 'Input manifest file changed during browser gate.' }
            $run.inputAudit.afterSha256 = Get-InputMapSha256 $browserAfter
            $run.inputAudit.afterPath = Join-Path $runDirectory 'browser-modern-22-inputs-after.json'
            Save-JsonUtf8 $run.inputAudit.afterPath ([ordered]@{
                capturedUtc = [DateTime]::UtcNow.ToString('o')
                browserCount=$browserAfter.Count; browserSha256=Get-InputMapSha256 $browserAfter; browserFiles=$browserAfter
                webManifestSha256=$webManifestShaAfter; webFiles=$webAfter
                buildManifestSha256=$buildManifestShaAfter; buildFiles=$buildAfter
            })
            $run.checks.browserInputsUnchanged = $true; $run.checks.buildInputsUnchanged = $true
            $run.checks.manifestFilesUnchanged = $true
        } catch { $run.errors += ('Input audit failed: ' + $_.Exception.ToString()); $run.checks.browserInputsUnchanged = $false }
    }
    $run.finishedUtc = [DateTime]::UtcNow.ToString('o')
    if ($run.errors.Count -eq 0 -and $run.checks.Count -ge 10 -and @($run.checks.Values | Where-Object { $_ -ne $true }).Count -eq 0) { $run.status = 'PASS' } else { $run.status = 'FAIL' }
    Save-JsonUtf8 $rootResultPath $run
    Write-Host ('ROOT_RESULT browser-modern-22 ' + $run.status + ' ' + $rootResultPath)
}
if ($run.status -ne 'PASS') { exit 1 }
exit 0
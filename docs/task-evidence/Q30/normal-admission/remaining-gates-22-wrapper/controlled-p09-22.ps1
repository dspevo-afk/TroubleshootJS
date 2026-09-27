#requires -Version 5.1
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$RepoRoot)
. (Join-Path $PSScriptRoot 'Q30GateWrapper.Common.ps1')
$env:PYTHONDONTWRITEBYTECODE = '1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
$runDirectory = Join-Path $PSScriptRoot ('controlled-p09-22-run-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runDirectory | Out-Null
$rootResultPath = Join-Path $runDirectory 'compiled-p09-controlled-22-root-result.json'
$run = [ordered]@{
    gate = 'compiled-p09-controlled-22'; status = 'NOT_RUN'; startedUtc = [DateTime]::UtcNow.ToString('o'); finishedUtc = $null
    repoRoot = $null; runDirectory = $runDirectory; evidenceDirectory = $null; resultJsonPath = $null
    processExitCode = $null; inputAudit = [ordered]@{}; checks = [ordered]@{}; errors = @()
    qualificationLimit = 'No full P09 claim; root must combine native05, population22 and controlled22 through the strict reader.'
}
$repo = $null; $webManifest = $null; $buildManifest = $null; $webBefore = $null; $buildBefore = $null; $controlledBefore = $null
$driverRelative = 'docs/task-evidence/QuickPlayGate/verify_admission.py'
$specRelative = 'docs/task-evidence/Q30/normal-admission/p09-controlled-negative-01-spec.json'
try {
    $repo = [IO.Path]::GetFullPath($RepoRoot); $run.repoRoot = $repo; Set-TaskPointer $repo '.tools/q30-controlled-22-wrapper-path.txt' $runDirectory
    $webManifest = Get-InputMapFromManifest $repo 'docs/task-evidence/Q30/normal-admission/web-final-21-inputs.json' 392
    $buildManifest = Get-InputMapFromManifest $repo 'docs/task-evidence/Q30/normal-admission/build-final-21-inputs.json' 1133
    if ($webManifest.Document.buildInputsUnchanged -ne $true) { throw 'web-final-21 does not declare unchanged build inputs.' }
    if ($webManifest.Files.Contains($driverRelative) -or $webManifest.Files.Contains($specRelative)) { throw 'Controlled driver/spec overlaps the web input set.' }
    $webManifestShaBefore = (Get-FileHash $webManifest.Path -Algorithm SHA256).Hash.ToLowerInvariant()
    $buildManifestShaBefore = (Get-FileHash $buildManifest.Path -Algorithm SHA256).Hash.ToLowerInvariant()
    $webBefore = Get-CurrentInputHashes $repo @($webManifest.Files.Keys)
    $buildBefore = Get-CurrentInputHashes $repo @($buildManifest.Files.Keys)
    $driverBefore = Get-CurrentInputHashes $repo @($driverRelative); $specBefore = Get-CurrentInputHashes $repo @($specRelative)
    $controlledBefore = [ordered]@{}
    foreach ($path in $webBefore.Keys) { $controlledBefore[$path] = $webBefore[$path] }
    $controlledBefore[$driverRelative] = $driverBefore[$driverRelative]; $controlledBefore[$specRelative] = $specBefore[$specRelative]
    if ($controlledBefore.Count -ne 394) { throw ('Expected 394 controlled P09 inputs, got ' + $controlledBefore.Count) }
    Assert-MapMatchesManifest $webManifest.Files $webBefore 'web-final-21'
    Assert-MapMatchesManifest $buildManifest.Files $buildBefore 'build-final-21'
    $specPath = Get-RepoAbsolutePath $repo $specRelative
    $spec = [IO.File]::ReadAllText($specPath,[Text.Encoding]::UTF8) | ConvertFrom-Json
    $expectedIds = @('p09-controlled-exact','p09-controlled-search-retry','p09-replay-1','p09-replay-2','p09-cancel-healthy','p09-cancel-hypotheses')
    if ($spec.status -cne 'PREDECLARED_NOT_RUN' -or $spec.id -cne 'q30-p09-controlled-negative-01' -or
        $spec.cases.Count -ne 6 -or (@($spec.cases | ForEach-Object { [string]$_.caseId }) -join ',') -cne ($expectedIds -join ',')) {
        throw 'Controlled P09 spec is not the predeclared six-case candidate.'
    }
    $run.inputAudit.beforeSha256 = Get-InputMapSha256 $controlledBefore
    $run.inputAudit.beforePath = Join-Path $runDirectory 'compiled-p09-controlled-22-inputs-before.json'
    Save-JsonUtf8 $run.inputAudit.beforePath ([ordered]@{
        capturedUtc=[DateTime]::UtcNow.ToString('o'); controlledCount=$controlledBefore.Count
        controlledSha256=Get-InputMapSha256 $controlledBefore; controlledFiles=$controlledBefore
        webManifestSha256=$webManifestShaBefore; webFiles=$webBefore; buildManifestSha256=$buildManifestShaBefore; buildFiles=$buildBefore
        specId=$spec.id
    })
    $run.inputAudit.specId=$spec.id; $run.inputAudit.specSha256=$specBefore[$specRelative]
    $run.checks.webManifestMatchedBefore = $true; $run.checks.buildManifestMatchedBefore = $true; $run.checks.specPredeclaredSixCases = $true
    $driver = Get-RepoAbsolutePath $repo $driverRelative
    $pythonExe = (Get-Command python.exe -CommandType Application -ErrorAction Stop).Source
    $evidenceDirectory = Join-Path $runDirectory ('tsj-q30-p09-controlled-22-guid-' + [guid]::NewGuid().ToString('N'))
    $run.evidenceDirectory = $evidenceDirectory; $run.resultJsonPath = Join-Path $evidenceDirectory 'result.json'; Set-TaskPointer $repo '.tools/q30-p09-controlled-22-path.txt' $evidenceDirectory
    $log = Join-Path $runDirectory 'compiled-p09-controlled-22.log'
    Write-Host ('START controlled P09-22; run only after population22 PASS; log=' + $log)
    $invocation = Invoke-CapturedCommand $pythonExe @('-B',$driver,$repo,$evidenceDirectory,$specPath) $log $repo
    $run.processExitCode = $invocation.ExitCode
    if (-not (Test-Path -LiteralPath $run.resultJsonPath -PathType Leaf)) { throw 'Controlled P09 driver did not write result.json; log retained.' }
    $result = [IO.File]::ReadAllText($run.resultJsonPath,[Text.Encoding]::UTF8) | ConvertFrom-Json
    $cases = @($result.cases); $expectedOutcomes = @('EXPECTED_REJECTION','PASS','PASS','PASS','CANCELLED','CANCELLED')
    $casesPass = ($cases.Count -eq 6); $semanticsPass = ($cases.Count -eq 6)
    if ($cases.Count -eq 6) {
        for ($index=0; $index -lt 6; $index++) {
            if ([string]$cases[$index].caseId -cne $expectedIds[$index] -or [string]$cases[$index].outcome -cne $expectedOutcomes[$index]) { $semanticsPass = $false }
        }
        foreach ($index in @(0,1)) {
            $control = $cases[$index].controlledNegative
            if ($null -eq $control -or $control.kind -cne 'CONTROLLED_COPY_P09_ENVELOPE_REJECTION' -or
                $control.applied -ne $true -or $control.predicateRejected -ne $true -or $control.reason -cne 'BOARD_SIZE' -or
                $control.originalGeometryFingerprintBefore -cne $control.originalGeometryFingerprintAfter -or $control.malformedCopyNotInstalled -ne $true) { $semanticsPass = $false }
        }
    }
    $errorCount=0; if ($null -ne $result.errors) { $errorCount=@($result.errors).Count }
    $httpErrorCount=0; if ($null -ne $result.httpErrors) { $httpErrorCount=@($result.httpErrors).Count }
    $survivors=0; if ($null -ne $result.cleanup.ownedSurvivors) { $survivors=@($result.cleanup.ownedSurvivors).Count }
    $cleanupPass=($result.cleanup.serverThreadStopped -eq $true -and $survivors -eq 0)
    $run.checks.driverExitZero=($invocation.ExitCode -eq 0); $run.checks.resultPass=($result.outcome -ceq 'PASS')
    $run.checks.sixCases=($cases.Count -eq 6); $run.checks.expectedCanaryAndReplaySemantics=$semanticsPass
    $run.checks.noBrowserOrHttpErrors=($errorCount -eq 0 -and $httpErrorCount -eq 0); $run.checks.cleanupPass=$cleanupPass
    $run.driverSummary=[ordered]@{ outcome=$result.outcome; caseCount=$cases.Count; caseIds=@($cases | ForEach-Object { $_.caseId }); errors=$errorCount; httpErrors=$httpErrorCount; serverThreadStopped=$result.cleanup.serverThreadStopped; ownedSurvivors=$survivors }
    if ($invocation.ExitCode -ne 0 -or $result.outcome -cne 'PASS' -or -not $casesPass -or -not $semanticsPass -or $errorCount -ne 0 -or $httpErrorCount -ne 0 -or -not $cleanupPass) {
        $run.errors += 'Controlled P09 did not satisfy the six-case result and cleanup requirements.'
    }
} catch { $run.errors += $_.Exception.ToString(); Write-Host ('FAIL controlled P09-22: ' + $_.Exception.Message) }
finally {
    if ($null -ne $repo -and $null -ne $webBefore -and $null -ne $buildBefore -and $null -ne $controlledBefore) {
        try {
            $webAfter=Get-CurrentInputHashes $repo @($webBefore.Keys); $buildAfter=Get-CurrentInputHashes $repo @($buildBefore.Keys)
            $controlledAfter=Get-CurrentInputHashes $repo @($controlledBefore.Keys)
            Assert-InputMapUnchanged $controlledBefore $controlledAfter '394 controlled P09 inputs'
            Assert-InputMapUnchanged $buildBefore $buildAfter '1133 build inputs'
            Assert-MapMatchesManifest $webManifest.Files $webAfter 'web-final-21 after controlled P09'
            Assert-MapMatchesManifest $buildManifest.Files $buildAfter 'build-final-21 after controlled P09'
            $webManifestShaAfter=(Get-FileHash $webManifest.Path -Algorithm SHA256).Hash.ToLowerInvariant()
            $buildManifestShaAfter=(Get-FileHash $buildManifest.Path -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($webManifestShaAfter -cne $webManifestShaBefore -or $buildManifestShaAfter -cne $buildManifestShaBefore) { throw 'web/build input manifest file changed during controlled P09.' }
            $run.inputAudit.afterSha256=Get-InputMapSha256 $controlledAfter
            $run.inputAudit.afterPath=Join-Path $runDirectory 'compiled-p09-controlled-22-inputs-after.json'
            Save-JsonUtf8 $run.inputAudit.afterPath ([ordered]@{
                capturedUtc=[DateTime]::UtcNow.ToString('o'); controlledCount=$controlledAfter.Count
                controlledSha256=Get-InputMapSha256 $controlledAfter; controlledFiles=$controlledAfter
                webManifestSha256=$webManifestShaAfter; webFiles=$webAfter; buildManifestSha256=$buildManifestShaAfter; buildFiles=$buildAfter
                specId=$run.inputAudit.specId
            })
            $run.checks.consumedInputsUnchanged=$true; $run.checks.webManifestUnchanged=$true; $run.checks.buildManifestUnchanged=$true
        } catch { $run.errors += ('Input audit failed: ' + $_.Exception.ToString()); $run.checks.consumedInputsUnchanged=$false }
    }
    $run.finishedUtc=[DateTime]::UtcNow.ToString('o')
    if ($run.errors.Count -eq 0 -and $run.checks.Count -ge 9 -and @($run.checks.Values | Where-Object { $_ -ne $true }).Count -eq 0) { $run.status='PASS' } else { $run.status='FAIL' }
    Save-JsonUtf8 $rootResultPath $run
    Write-Host ('ROOT_RESULT compiled-p09-controlled-22 ' + $run.status + ' ' + $rootResultPath)
}
if ($run.status -ne 'PASS') { exit 1 }
exit 0
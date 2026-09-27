#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$RepoRoot,
    [string]$JavaHome = '<worktree>\.tools\jdk8-download\jdk8u502-b07'
)
. (Join-Path $PSScriptRoot 'Q30GateWrapper.Common.ps1')
$env:PYTHONDONTWRITEBYTECODE = '1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
$runDirectory = Join-Path $PSScriptRoot ('native-final-05-run-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runDirectory | Out-Null
$rootResultPath = Join-Path $runDirectory 'native-final-05-root-result.json'
$run = [ordered]@{
    gate='native-final-05'; status='NOT_RUN'; startedUtc=[DateTime]::UtcNow.ToString('o'); finishedUtc=$null
    repoRoot=$null; runDirectory=$runDirectory; javaHome=$JavaHome; runnerLog=$null; receiptPath=$null
    processExitCodes=[ordered]@{nativeRunner=$null;nativeServiceReader=$null;corpusReader=$null}
    inputAudit=[ordered]@{}; readerInputAudit=[ordered]@{}; baselineDifferences=@(); checks=[ordered]@{}; errors=@()
    qualificationLimit='Native05 only. Full P09 requires the root strict-reader combination of native05, population22 and controlled22.'
}
$repo=$null; $nativeManifest=$null; $nativeBefore=$null; $nativePrior=$null; $readerBefore=$null; $runnerQualified=$false; $log=$null
$readerPaths=@('docs/task-evidence/Q30/normal-admission/check_native_service.py','docs/task-evidence/Q30/normal-admission/check_corpus.py','docs/task-evidence/Q30/normal-admission/README.md')
try {
    $repo=[IO.Path]::GetFullPath($RepoRoot); $run.repoRoot=$repo; Set-TaskPointer $repo '.tools/q30-native-22-wrapper-path.txt' $runDirectory
    $nativeManifest=Get-InputMapFromManifest $repo 'docs/task-evidence/Q30/normal-admission/native-final-04-inputs.json' 1248
    $nativePrior=$nativeManifest.Files
    $manifestShaBefore=(Get-FileHash $nativeManifest.Path -Algorithm SHA256).Hash.ToLowerInvariant()
    $nativeBefore=Get-CurrentInputHashes $repo @($nativePrior.Keys)
    $differences=@(Get-ChangedInputHashes $nativePrior $nativeBefore); $run.baselineDifferences=$differences
    $expectedDrift=@('src/com/lushprojects/circuitjs1/client/U04CatalogMutationVerifier.java','tests/contracts/Q30NormalExecutionPolicyContractTest.java') | Sort-Object -CaseSensitive
    $observedDrift=@($differences | ForEach-Object { [string]$_.path } | Sort-Object -CaseSensitive)
    $run.inputAudit.beforePath=Join-Path $runDirectory 'native-final-05-inputs-before.json'
    $run.inputAudit.count=$nativeBefore.Count; $run.inputAudit.sha256Before=Get-InputMapSha256 $nativeBefore
    $run.inputAudit.manifestSha256Before=$manifestShaBefore
    Save-JsonUtf8 $run.inputAudit.beforePath ([ordered]@{capturedUtc=[DateTime]::UtcNow.ToString('o'); manifestSha256=$manifestShaBefore; count=$nativeBefore.Count; sha256=Get-InputMapSha256 $nativeBefore; expectedBaselineDifferences=$expectedDrift; observedBaselineDifferences=$differences; files=$nativeBefore})
    if (($observedDrift -join "`n") -cne ($expectedDrift -join "`n")) { throw ('native-final-04 differences were not exactly the expected U04 and test-reflection paths: ' + ($observedDrift -join ', ')) }
    $run.checks.expectedBaselineDifferences=$true

    $receiptRelative='docs/task-evidence/Q30/normal-admission/native-final-05-receipt.txt'
    $receiptPath=Get-RepoAbsolutePath $repo $receiptRelative; $run.receiptPath=$receiptPath
    $receiptSuffixes=@('', '.vectors.txt', '.manifest-resistive.txt', '.manifest-controlled.txt', '.manifest-controlled-alt.txt', '.seeds.txt', '.roles.txt', '.values.txt')
    $existing=@($receiptSuffixes | ForEach-Object { $receiptPath + $_ } | Where-Object { Test-Path -LiteralPath $_ })
    if ($existing.Count) { throw ('Refusing to overwrite native05 receipt artifact: ' + ($existing -join ', ')) }
    $pythonExe=(Get-Command python.exe -CommandType Application -ErrorAction Stop).Source
    $powershellExe=Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
    $verify=Get-RepoAbsolutePath $repo 'scripts/verify-current-contracts.ps1'
    $log=Join-Path $runDirectory 'native-final-05.log'; $run.runnerLog=$log
    Write-Host ('START unfiltered native-final-05; log=' + $log)
    $native=Invoke-CapturedCommand $powershellExe @('-NoProfile','-ExecutionPolicy','Bypass','-File',$verify,'-JavaHome',$JavaHome,'-PythonExe',$pythonExe,'-ReceiptOutputPath',$receiptPath) $log $repo
    $run.processExitCodes.nativeRunner=$native.ExitCode
    $passCount=[regex]::Matches($native.Output,'(?m)^PASS: current contracts; \d+ Java suites, independent seed/value/role oracles and report protocol\.\r?$').Count
    $cleanupCount=[regex]::Matches($native.Output,'(?m)^CLEANUP: current JVM contract classes and task-owned scratch removed\.\r?$').Count
    $failureCount=[regex]::Matches($native.Output,'(?m)^CURRENT_CONTRACT_FAILURE:').Count
    $cleanupFailureCount=[regex]::Matches($native.Output,'(?m)^CURRENT_CONTRACT_CLEANUP:').Count
    $receiptExists=(Test-Path -LiteralPath $receiptPath -PathType Leaf) -and ((Get-Item $receiptPath).Length -gt 0)
    $runnerQualified=($native.ExitCode -eq 0 -and $passCount -eq 1 -and $cleanupCount -eq 1 -and $failureCount -eq 0 -and $cleanupFailureCount -eq 0 -and $receiptExists)
    $run.checks.runnerExitZero=($native.ExitCode -eq 0); $run.checks.fullPassMarkerExactlyOnce=($passCount -eq 1)
    $run.checks.cleanupExactlyOnce=($cleanupCount -eq 1); $run.checks.noFailureMarkers=($failureCount -eq 0 -and $cleanupFailureCount -eq 0)
    $run.checks.receiptNonempty=$receiptExists
    $run.runnerSummary=[ordered]@{actualExitCode=$native.ExitCode;fullPassMarkerCount=$passCount;cleanupMarkerCount=$cleanupCount;failureMarkerCount=$failureCount;cleanupFailureMarkerCount=$cleanupFailureCount;receiptExists=$receiptExists;qualified=$runnerQualified}
    if (-not $runnerQualified) { $run.errors+='Native runner did not provide exit 0, full PASS, cleanup and a nonempty receipt; strict readers skipped.' }

    if ($runnerQualified) {
        $readerBefore=Get-CurrentInputHashes $repo $readerPaths
        $run.readerInputAudit.beforePath=Join-Path $runDirectory 'native-final-05-reader-inputs-before.json'
        $run.readerInputAudit.count=$readerBefore.Count; $run.readerInputAudit.sha256Before=Get-InputMapSha256 $readerBefore
        Save-JsonUtf8 $run.readerInputAudit.beforePath ([ordered]@{capturedUtc=[DateTime]::UtcNow.ToString('o'); purpose='Reader scripts and frozen README hashed immediately before strict readers.'; count=$readerBefore.Count; sha256=Get-InputMapSha256 $readerBefore; files=$readerBefore})
        $serviceScript=Get-RepoAbsolutePath $repo 'docs/task-evidence/Q30/normal-admission/check_native_service.py'
        $serviceReport=Join-Path $runDirectory 'native-final-05-service-report.json'
        $serviceLog=Join-Path $runDirectory 'native-final-05-service-reader.log'
        try {
            Write-Host 'START check_native_service.py --runner-scope full'
            $service=Invoke-CapturedCommand $pythonExe @('-B',$serviceScript,'--runner-scope','full','--seeds','7,13,4,14,43,3,10,64','--log',$log,'--receipt',$receiptPath,'--report',$serviceReport) $serviceLog $repo
            $run.processExitCodes.nativeServiceReader=$service.ExitCode
            $run.checks.nativeServiceReaderExitZero=($service.ExitCode -eq 0)
            if (Test-Path -LiteralPath $serviceReport -PathType Leaf) {
                $report=[IO.File]::ReadAllText($serviceReport,[Text.Encoding]::UTF8) | ConvertFrom-Json
                $actualSeeds=@($report.requestedSeeds | ForEach-Object { [string]$_ })
                $seedOrderPass=(($actualSeeds -join ',') -ceq '7,13,4,14,43,3,10,64')
                $readerPass=($report.status -ceq 'PASS' -and $report.runnerScope -ceq 'full' -and $report.fullMatrixStatus -ceq 'PASS' -and $seedOrderPass)
                $run.checks.nativeServiceReaderPass=($service.ExitCode -eq 0 -and $readerPass)
                $run.serviceReaderSummary=[ordered]@{status=$report.status;runnerScope=$report.runnerScope;fullMatrixStatus=$report.fullMatrixStatus;observedTotalRows=$report.observedTotalRows;requestedSeeds=$actualSeeds}
            } else { $run.checks.nativeServiceReaderPass=$false; $run.errors+='check_native_service.py did not write its report.' }
            if ($service.ExitCode -ne 0) { $run.errors+=('check_native_service.py actual exit code: ' + $service.ExitCode) }
        } catch { $run.checks.nativeServiceReaderExitZero=$false; $run.checks.nativeServiceReaderPass=$false; $run.errors+=('check_native_service.py failed: ' + $_.Exception.ToString()) }

        $corpusScript=Get-RepoAbsolutePath $repo 'docs/task-evidence/Q30/normal-admission/check_corpus.py'
        $readme=Get-RepoAbsolutePath $repo 'docs/task-evidence/Q30/normal-admission/README.md'
        $corpusLog=Join-Path $runDirectory 'native-final-05-corpus-reader.log'
        try {
            Write-Host 'START check_corpus.py against full native05 log'
            $corpus=Invoke-CapturedCommand $pythonExe @('-B',$corpusScript,'--log',$log,'--readme',$readme) $corpusLog $repo
            $run.processExitCodes.corpusReader=$corpus.ExitCode; $run.checks.corpusReaderExitZero=($corpus.ExitCode -eq 0)
            if ($corpus.ExitCode -ne 0) { $run.errors+=('check_corpus.py actual exit code: ' + $corpus.ExitCode) }
            else { $run.corpusReaderSummary=$corpus.Output }
        } catch { $run.checks.corpusReaderExitZero=$false; $run.errors+=('check_corpus.py failed: ' + $_.Exception.ToString()) }
    } else {
        $run.checks.nativeServiceReaderExitZero=$false; $run.checks.nativeServiceReaderPass=$false; $run.checks.corpusReaderExitZero=$false
    }
} catch { $run.errors+=$_.Exception.ToString(); Write-Host ('FAIL native-final-05: ' + $_.Exception.Message) }
finally {
    if ($null -ne $repo -and $null -ne $nativeBefore -and $null -ne $nativePrior) {
        try {
            $nativeAfter=Get-CurrentInputHashes $repo @($nativePrior.Keys); Assert-InputMapUnchanged $nativeBefore $nativeAfter '1248 native inputs'
            $manifestShaAfter=(Get-FileHash $nativeManifest.Path -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($manifestShaAfter -cne $manifestShaBefore) { throw 'native-final-04 manifest changed during native gate.' }
            $run.inputAudit.afterPath=Join-Path $runDirectory 'native-final-05-inputs-after.json'
            $run.inputAudit.sha256After=Get-InputMapSha256 $nativeAfter; $run.inputAudit.unchanged=$true
            Save-JsonUtf8 $run.inputAudit.afterPath ([ordered]@{capturedUtc=[DateTime]::UtcNow.ToString('o');manifestSha256=$manifestShaAfter;count=$nativeAfter.Count;sha256=Get-InputMapSha256 $nativeAfter;files=$nativeAfter})
            $run.checks.nativeInputsUnchanged=$true; $run.checks.nativeManifestUnchanged=$true
        } catch { $run.errors+=('Native input audit failed: ' + $_.Exception.ToString()); $run.checks.nativeInputsUnchanged=$false }
    }
    if ($null -ne $repo -and $null -ne $readerBefore) {
        try {
            $readerAfter=Get-CurrentInputHashes $repo $readerPaths; Assert-InputMapUnchanged $readerBefore $readerAfter 'three strict-reader inputs'
            $run.readerInputAudit.afterPath=Join-Path $runDirectory 'native-final-05-reader-inputs-after.json'
            $run.readerInputAudit.sha256After=Get-InputMapSha256 $readerAfter; $run.readerInputAudit.unchanged=$true
            Save-JsonUtf8 $run.readerInputAudit.afterPath ([ordered]@{capturedUtc=[DateTime]::UtcNow.ToString('o');count=$readerAfter.Count;sha256=Get-InputMapSha256 $readerAfter;files=$readerAfter})
            $run.checks.readerInputsUnchanged=$true
        } catch { $run.errors+=('Reader input audit failed: ' + $_.Exception.ToString()); $run.checks.readerInputsUnchanged=$false }
    } elseif ($runnerQualified) { $run.checks.readerInputsUnchanged=$false; $run.errors+='Reader inputs were not frozen before the strict readers.' }
    $run.finishedUtc=[DateTime]::UtcNow.ToString('o')
    if ($run.errors.Count -eq 0 -and $run.checks.Count -ge 12 -and @($run.checks.Values | Where-Object { $_ -ne $true }).Count -eq 0) { $run.status='PASS' } else { $run.status='FAIL' }
    Save-JsonUtf8 $rootResultPath $run
    Write-Host ('ROOT_RESULT native-final-05 ' + $run.status + ' ' + $rootResultPath)
}
if ($run.status -ne 'PASS') { exit 1 }
exit 0
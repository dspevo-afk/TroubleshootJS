param([ValidateSet('shipping','isolated')][string]$Tag='shipping')
$ErrorActionPreference='Stop'
$binding=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'epoch15-task-pointer.json') -Raw | ConvertFrom-Json
$integration=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'integration-pointer.json') -Raw | ConvertFrom-Json
if($binding.baseHead -ne $integration.baseHead -or $binding.taskRoot -ne $integration.taskRoot){throw 'Candidate pointer mismatch'}
$taskRoot=$binding.taskRoot
$app=Join-Path $taskRoot ($Tag+'\app')
$prepare=Get-Content -LiteralPath (Join-Path $taskRoot ($Tag+'\prepare-receipt.json')) -Raw | ConvertFrom-Json
$snapshot=Get-Content -LiteralPath (Join-Path $taskRoot 'source-snapshot.json') -Raw | ConvertFrom-Json
if($prepare.baseHead -ne $binding.baseHead -or $prepare.sourceIdentity -ne $binding.sourceIdentity -or $prepare.sourceFileCount -ne 1355 -or $snapshot.sourceIdentity -ne $binding.sourceIdentity){throw 'Fresh preparation identity mismatch'}
$patchPaths=@('src/com/lushprojects/circuitjs1/circuitjs1.gwt.xml','src/com/lushprojects/circuitjs1/client/GenerationCoordinator.java','src/com/lushprojects/circuitjs1/client/GenerationJob.java','scripts/verify-current-contracts.ps1')
foreach($row in $snapshot.files){
    if($Tag -eq 'isolated' -and $patchPaths -contains $row.path){continue}
    if((Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $app $row.path)).Hash.ToLowerInvariant() -ne $row.sha256){throw ('Prepared input changed: '+$row.path)}
}
foreach($row in $prepare.overlayFiles){if((Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $app $row.path)).Hash.ToLowerInvariant() -ne $row.sha256){throw 'Prepared overlay changed'}}
$log=Join-Path $taskRoot ($Tag+'-build.log')
$result=Join-Path $taskRoot ($Tag+'-build-result.json')
$ownership=Join-Path $taskRoot ($Tag+'-build-ownership.json')
if((Test-Path -LiteralPath $log) -or (Test-Path -LiteralPath $result) -or (Test-Path -LiteralPath $ownership)){throw 'Preserve existing build output'}
$self=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
@{schema=1;pid=$PID;createdUtc=$self.CreationDate.ToUniversalTime().ToString('o');executable=$self.ExecutablePath;command=$self.CommandLine;taskRoot=$taskRoot;app=$app} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $ownership
$started=[DateTime]::UtcNow
$timer=[Diagnostics.Stopwatch]::StartNew()
Push-Location -LiteralPath $app
try {
    & .\scripts\build.ps1 -JavaHome '<USER_HOME>\AppData\Local\Temp\<JDK_TASK_ID>\jdk\jdk8u502-b07' *> $log
    $ok=$?
    $code=$LASTEXITCODE
    if($null -eq $code){$code=if($ok){0}else{1}}
} catch {$_ | Out-String | Add-Content -LiteralPath $log;$code=1}
finally {Pop-Location}
$timer.Stop()
$content=Get-Content -LiteralPath $log -Raw
$permutations=@(Get-ChildItem -LiteralPath (Join-Path $app 'war\circuitjs1') -Filter '*.cache.js' -File -ErrorAction SilentlyContinue)
$confirmed=$content.Contains('Compilation succeeded --') -and $content.Contains('Linking succeeded --') -and -not $content.Contains('BUILD_INFRASTRUCTURE:') -and $permutations.Count -eq 5
@{schema=1;head=$binding.baseHead;consumedSourceIdentity=$binding.sourceIdentity;consumedInputsVerified=1355;app=$app;startedUtc=$started.ToString('o');finishedUtc=[DateTime]::UtcNow.ToString('o');elapsedSeconds=$timer.Elapsed.TotalSeconds;exitCode=$code;compilationConfirmed=$confirmed;permutations=$permutations.Count;status=if($code -eq 0 -and $confirmed){'PASS'}else{'FAIL'};scope=('Fresh '+$Tag+' JDK8/GWT5 app; tracked Q30 catalog disabled');prepareReceipt=($Tag+'/prepare-receipt.json')} | ConvertTo-Json | Set-Content -LiteralPath $result
Get-Content -LiteralPath $log -Tail 7
if($code -eq 0 -and -not $confirmed){exit 1}
exit $code

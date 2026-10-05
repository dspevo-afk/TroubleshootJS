$ErrorActionPreference='Stop'
$repo='<USER_HOME>\.codex\worktrees\<REPOSITORY_NAME>\TroubleshootJS'
$pin=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'epoch15-task-pointer.json') -Raw|ConvertFrom-Json
$taskRoot=$pin.taskRoot
$snapshot=Get-Content -LiteralPath (Join-Path $taskRoot 'source-snapshot.json') -Raw|ConvertFrom-Json
function CheckSource {
 foreach($row in $snapshot.files){if((Get-FileHash -LiteralPath (Join-Path $repo $row.path) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $row.sha256){throw ('Source changed: '+$row.path)}}
}
CheckSource
$self=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
@{pid=$PID;createdUtc=$self.CreationDate.ToUniversalTime().ToString('o');executable=$self.ExecutablePath;command=$self.CommandLine;taskRoot=$taskRoot;scope='Focused maintained native2; one declared verifier-only source change'}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $taskRoot 'focused-native-ownership.json')
$started=[DateTime]::UtcNow;$watch=[Diagnostics.Stopwatch]::StartNew();$log=Join-Path $taskRoot 'focused-native.log';$receipt=Join-Path $taskRoot 'focused-native.txt';$resultPath=Join-Path $taskRoot 'focused-native-result.json'
foreach($path in @($log,$receipt,$resultPath)){if(Test-Path -LiteralPath $path){throw 'Preserve prior focused output'}}
try {
 & (Join-Path $repo 'scripts\verify-current-contracts.ps1') -JavaHome '<USER_HOME>\AppData\Local\Temp\<JDK_TASK_ID>\jdk\jdk8u502-b07' -PythonExe '<USER_HOME>\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -Suite @('DiagnosticServicePreparationContractTest','Q30PowerReadinessContractTest') -ReceiptOutputPath $receipt *> $log
 $code=$LASTEXITCODE
 if($null -eq $code){$code=0}
 CheckSource
} catch {$_|Out-String|Add-Content -LiteralPath $log;$code=1}
$watch.Stop();$content=Get-Content -LiteralPath $log -Raw
$complete=$content.Contains('PASS: focused current contracts; 2 Java suites. Full matrix and independent oracles NOT RUN.') -and $content.Contains('CLEANUP: current JVM contract classes and task-owned scratch removed.') -and -not $content.Contains('CURRENT_CONTRACT_FAILURE:')
@{schema=1;status=if($code -eq 0 -and $complete){'PASS_FOCUSED_NATIVE2'}else{'FAIL'};exitCode=$code;baseHead=$pin.baseHead;consumedSourceIdentity=$pin.sourceIdentity;consumedInputsVerified=$snapshot.files.Count;declaredModifiedPaths=$pin.declaredModifiedPaths;startedUtc=$started.ToString('o');finishedUtc=[DateTime]::UtcNow.ToString('o');operationSeconds=$watch.Elapsed.TotalSeconds;cleanupElapsedSeconds='NOT RECORDED separately';complete=$complete;suites=@('DiagnosticServicePreparationContractTest','Q30PowerReadinessContractTest')}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath $resultPath
Get-Content -LiteralPath $log -Tail 8
if($code -eq 0 -and -not $complete){exit 1}
exit $code

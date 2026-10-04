$ErrorActionPreference='Stop'
$workspace=$PSScriptRoot
$repo='<repo>'
$taskRoot=Join-Path ([IO.Path]::GetTempPath()) ('q30-power-fix-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskRoot | Out-Null
$taskRoot | Set-Content -LiteralPath (Join-Path $workspace 'power-fix-root.txt')
$self=Get-CimInstance Win32_Process -Filter "ProcessId=$PID"
@{pid=$PID;createdUtc=$self.CreationDate.ToUniversalTime().ToString('o');executable=$self.ExecutablePath;commandLine=$self.CommandLine;taskRoot=$taskRoot;scope='Focused five suites, shared Q30 active readiness; no full matrix or cold77'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRoot 'native-controller-identity.json')
$started=[DateTime]::UtcNow; $timer=[Diagnostics.Stopwatch]::StartNew()
$suites=@('Q30PowerReadinessContractTest','Q30PlanContractTest','A06PowerContractTest','U02MeasurementContractTest','GeneratedExternalPowerBindingsControlObservationContractTest')
$code=0
try {
 & (Join-Path $repo 'scripts\verify-current-contracts.ps1') -JavaHome '<user-home>\AppData\Local\Temp\q30-finish-0fa2512465\jdk\jdk8u502-b07' -PythonExe '<user-home>\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -Suite $suites -ReceiptOutputPath (Join-Path $taskRoot 'native.txt') *> (Join-Path $taskRoot 'native.log')
 $code=if($null -eq $LASTEXITCODE){0}else{$LASTEXITCODE}
} catch { $_ | Out-String | Add-Content -LiteralPath (Join-Path $taskRoot 'native.log'); $code=1 }
$timer.Stop()
$log=[IO.File]::ReadAllText((Join-Path $taskRoot 'native.log'))
$complete=$code -eq 0 -and $log.Contains('PASS: focused current contracts; 5 Java suites. Full matrix and independent oracles NOT RUN.') -and $log.Contains('CLEANUP: current JVM contract classes and task-owned scratch removed.')
@{status=if($complete){'COMMAND_PASS_PENDING_RECEIPT_REVIEW'}else{'FAIL'};startedUtc=$started.ToString('o');finishedUtc=[DateTime]::UtcNow.ToString('o');elapsedSeconds=$timer.Elapsed.TotalSeconds;exitCode=$code;suites=$suites;fullMatrix=$false;cold77=$false;cleanupElapsedSeconds='NOT_RECORDED separately'} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $taskRoot 'native-result.json')
Get-Content -LiteralPath (Join-Path $taskRoot 'native.log') -Tail 16
if(-not $complete){exit 1}

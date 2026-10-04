$ErrorActionPreference='Stop'
$repo='<repo>'
$taskRoot=Join-Path ([IO.Path]::GetTempPath()) ('q30-power-context-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskRoot | Out-Null
$taskRoot | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'power-context-root.txt')
$self=Get-CimInstance Win32_Process -Filter "ProcessId=$PID"
@{pid=$PID;createdUtc=$self.CreationDate.ToUniversalTime().ToString('o');executable=$self.ExecutablePath;commandLine=$self.CommandLine;taskRoot=$taskRoot;scope='Affected dependency/cache, diagnostic preparation and Q30 temporal lifecycle contracts; no full matrix or cold77'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRoot 'controller-identity.json')
$started=[DateTime]::UtcNow; $timer=[Diagnostics.Stopwatch]::StartNew(); $code=0
$suites=@('A10DependencyContractTest','D01DiagnosticContractTest','DiagnosticServicePreparationContractTest','Q30TemporalWorkContractTest')
try {
 & (Join-Path $repo 'scripts\verify-current-contracts.ps1') -JavaHome '<user-home>\AppData\Local\Temp\q30-finish-0fa2512465\jdk\jdk8u502-b07' -PythonExe '<user-home>\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -Suite $suites -ReceiptOutputPath (Join-Path $taskRoot 'native.txt') *> (Join-Path $taskRoot 'native.log')
 $code=if($null -eq $LASTEXITCODE){0}else{$LASTEXITCODE}
} catch { $_ | Out-String | Add-Content -LiteralPath (Join-Path $taskRoot 'native.log'); $code=1 }
$timer.Stop()
$log=[IO.File]::ReadAllText((Join-Path $taskRoot 'native.log'))
$complete=$code -eq 0 -and $log.Contains('PASS: focused current contracts; 4 Java suites. Full matrix and independent oracles NOT RUN.') -and $log.Contains('CLEANUP: current JVM contract classes and task-owned scratch removed.')
@{status=if($complete){'PASS_FOCUSED_AFFECTED_CONTRACTS'}else{'FAIL'};startedUtc=$started.ToString('o');finishedUtc=[DateTime]::UtcNow.ToString('o');elapsedSeconds=$timer.Elapsed.TotalSeconds;exitCode=$code;suites=$suites;fullMatrix=$false;cold77=$false;cleanupElapsedSeconds='NOT_RECORDED separately'} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $taskRoot 'result.json')
Get-Content -LiteralPath (Join-Path $taskRoot 'native.log') -Tail 16
if(-not $complete){exit 1}

$ErrorActionPreference='Stop'
$workspace=$PSScriptRoot
$repo='<repo>'
$taskRoot=(Get-Content -LiteralPath (Join-Path $workspace 'power-fix-root.txt') -Raw).Trim()
if(Test-Path -LiteralPath (Join-Path $taskRoot 'build-result.json')){throw 'Preserve previous build receipt'}
if(-not ([IO.File]::ReadAllText((Join-Path $repo 'src\com\lushprojects\circuitjs1\client\PlayerFamilyCatalog.java'))).Contains('registerStagedFamily(new Rb30PlayerFamilyCapability(), false);')){throw 'Public Q30 must stay disabled'}
$self=Get-CimInstance Win32_Process -Filter "ProcessId=$PID"
@{pid=$PID;createdUtc=$self.CreationDate.ToUniversalTime().ToString('o');executable=$self.ExecutablePath;commandLine=$self.CommandLine;taskRoot=$taskRoot;scope='Actual maintained final-source JDK8/GWT OBF build; public Q30 disabled'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRoot 'build-controller-identity.json')
$started=[DateTime]::UtcNow; $timer=[Diagnostics.Stopwatch]::StartNew(); $code=0
try {
 & (Join-Path $repo 'scripts\build.ps1') -JavaHome '<user-home>\AppData\Local\Temp\q30-finish-0fa2512465\jdk\jdk8u502-b07' -Target Compile -Style OBF -ProcessTimeoutSeconds 900 *> (Join-Path $taskRoot 'build.log')
 $code=if($null -eq $LASTEXITCODE){0}else{$LASTEXITCODE}
} catch { $_ | Out-String | Add-Content -LiteralPath (Join-Path $taskRoot 'build.log'); $code=1 }
$timer.Stop()
$log=[IO.File]::ReadAllText((Join-Path $taskRoot 'build.log'))
$complete=$code -eq 0 -and $log.Contains('Compilation succeeded')
@{status=if($complete){'PASS_ACTUAL_FINAL_SOURCE_JDK8_GWT_BUILD'}else{'FAIL'};startedUtc=$started.ToString('o');finishedUtc=[DateTime]::UtcNow.ToString('o');elapsedSeconds=$timer.Elapsed.TotalSeconds;exitCode=$code;style='OBF';target='Compile';publicQ30Enabled=$false;processTimeoutSeconds=900;cleanupElapsedSeconds='NOT_RECORDED separately'} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $taskRoot 'build-result.json')
Get-Content -LiteralPath (Join-Path $taskRoot 'build.log') -Tail 12
if(-not $complete){exit 1}

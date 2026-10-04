$ErrorActionPreference='Stop'
$workspace=$PSScriptRoot
$taskRoot=[IO.File]::ReadAllText((Join-Path $workspace 'active-native-root.txt'))
$repo='<repo>'
$jdk='<user-home>\AppData\Local\Temp\q30-finish-0fa2512465\jdk\jdk8u502-b07'
$self=Get-CimInstance Win32_Process -Filter "ProcessId=$PID"
@{pid=$PID;createdUtc=$self.CreationDate.ToUniversalTime().ToString('o');executable=$self.ExecutablePath;commandLine=$self.CommandLine;taskRoot=$taskRoot;scope='Serial compile and three observation-only real-solver cases, no product changes'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRoot 'controller-identity.json')
Import-Module (Join-Path $repo 'scripts\VerifierIsolation.psm1') -Force
$cp=(Join-Path $taskRoot 'classes')+';'+(Join-Path $repo '.tools\gwt-2.7.0\gwt-user-2.7.0.jar')+';'+(Join-Path $repo '.tools\gwt-2.7.0\gwt-dev-2.7.0.jar')
$timer=[Diagnostics.Stopwatch]::StartNew(); $started=[DateTime]::UtcNow
$compiled=Invoke-VerifierBoundedProcess (Join-Path $jdk 'bin\javac.exe') @('-source','7','-target','7','-encoding','UTF-8','-classpath',$cp,'-sourcepath',(Join-Path $taskRoot 'empty-sourcepath'),'-d',(Join-Path $taskRoot 'classes'),('@'+(Join-Path $taskRoot 'sources.txt'))) 60000
$compiled | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $taskRoot 'compile-result.json')
if(-not $compiled.TerminationProven -or $compiled.ExitCode -ne 0){Write-Output $compiled.Stderr;throw 'Focused diagnostic compile failed'}
Write-Output ('COMPILE_COMPLETE '+$taskRoot)
$cases=@()
foreach($seed in @('10387','10226','10014')){
 $case=Invoke-VerifierBoundedProcess (Join-Path $jdk 'bin\java.exe') @('-ea','-cp',$cp,'com.lushprojects.circuitjs1.client.Q30ActivePowerDiagnostic',$seed) 60000
 $case | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $taskRoot ('case-'+$seed+'.json'))
 [IO.File]::WriteAllText((Join-Path $taskRoot ('case-'+$seed+'.stdout.txt')),$case.Stdout)
 [IO.File]::WriteAllText((Join-Path $taskRoot ('case-'+$seed+'.stderr.txt')),$case.Stderr)
 $done=$case.TerminationProven -and $case.ExitCode -eq 0 -and $case.Stdout.Contains('Q30_ACTIVE_DIAGNOSTIC_COMPLETE seed='+$seed)
 $cases+=@{seed=$seed;exitCode=$case.ExitCode;terminationProven=$case.TerminationProven;complete=$done}
 Write-Output ('DIAGNOSTIC_CASE seed='+$seed+' complete='+$done+' exit='+$case.ExitCode)
 if(-not $done){Write-Output $case.Stderr;break}
}
$timer.Stop()
$complete=$cases.Count -eq 3 -and @($cases | Where-Object {-not $_.complete}).Count -eq 0
@{status=if($complete){'DIAGNOSTIC_COMPLETED_NOT_PRODUCT_ACCEPTANCE'}else{'FAIL'};startedUtc=$started.ToString('o');finishedUtc=[DateTime]::UtcNow.ToString('o');elapsedSeconds=$timer.Elapsed.TotalSeconds;cases=$cases;productionSourceChanged=$false;cold77Run=$false;buildRun=$false} | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $taskRoot 'result.json')
if(-not $complete){exit 1}

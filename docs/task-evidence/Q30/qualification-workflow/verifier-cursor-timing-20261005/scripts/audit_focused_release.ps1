$ErrorActionPreference='Stop'
$pin=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'epoch15-task-pointer.json') -Raw|ConvertFrom-Json -DateKind String
$taskRoot=$pin.taskRoot;$watch=[Diagnostics.Stopwatch]::StartNew();$started=[DateTime]::UtcNow.ToString('o');$selected=@()
foreach($name in @('focused-native-ownership.json','shipping-build-ownership.json')){
 $o=Get-Content -LiteralPath (Join-Path $taskRoot $name) -Raw|ConvertFrom-Json -DateKind String
 $selected+=@{pid=$o.pid;created=$o.createdUtc;executable=$o.executable;source=$name}
}
$identity=Get-Content -LiteralPath (Join-Path $taskRoot 'focused-d01\identity.json') -Raw|ConvertFrom-Json -DateKind String
$selected+=@{pid=$identity.runner.pid;created=$identity.runner.created;executable=$identity.runner.executable;source='focused-d01-host'}
$beforeClose=Get-Content -LiteralPath (Join-Path $taskRoot 'focused-d01\owned-processes-before-close.json') -Raw|ConvertFrom-Json -DateKind String
foreach($edge in @($identity.edgeProcessesBeforeCases)+@($beforeClose)){$selected+=@{pid=$edge.pid;created=$edge.created;executable=$edge.ExecutablePath;source='recorded-focused-edge'}}
$unique=@($selected|Sort-Object pid,created,executable -Unique);$rows=@()
foreach($owner in $unique){
 $live=Get-CimInstance Win32_Process -Filter ('ProcessId='+$owner.pid) -OperationTimeoutSec 5;$same=$false
 if($null -ne $live){$same=$live.CreationDate.ToUniversalTime().Ticks -eq [DateTime]::Parse($owner.created).ToUniversalTime().Ticks -and [StringComparer]::OrdinalIgnoreCase.Equals($live.ExecutablePath,$owner.executable)}
 $rows+=@{pid=$owner.pid;createdUtc=$owner.created;executable=$owner.executable;source=$owner.source;pidPresent=$null -ne $live;exactInstancePresent=$same}
}
$listeners=@(Get-NetTCPConnection -State Listen -ErrorAction Stop|Where-Object LocalPort -eq $identity.port);$survivors=@($rows|Where-Object exactInstancePresent)
$result=@{schema=1;status=if($survivors.Count -eq 0 -and $listeners.Count -eq 0){'PASS_EXACT_RECORDED_FOCUSED_INSTANCES_AND_PORT_RELEASED'}else{'BLOCKED_RECORDED_FOCUSED_RESOURCE_SURVIVOR'};baseHead=$pin.baseHead;sourceIdentity=$pin.sourceIdentity;declaredModifiedPaths=$pin.declaredModifiedPaths;startedUtc=$started;finishedUtc=[DateTime]::UtcNow.ToString('o');operationSeconds=$watch.Elapsed.TotalSeconds;terminationAttempted=$false;scope='Recorded native/build/focused host/owned Edge identities and exact listener only; full qualification not established';processes=$rows;port=$identity.port;listenerCount=$listeners.Count;profilesRetained=$true}
$output=Join-Path $taskRoot 'focused-independent-release.json';if(Test-Path -LiteralPath $output){throw 'Preserve prior audit'}
$result|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $output -Encoding utf8NoBOM
@{status=$result.status;observations=$rows.Count;survivors=$survivors.Count;port=$identity.port;listenerCount=$listeners.Count;operationSeconds=$result.operationSeconds}|ConvertTo-Json -Compress
if($survivors.Count -gt 0 -or $listeners.Count -gt 0){exit 2}

$ErrorActionPreference='Stop'
$active='<diagnostic-root>'
$fixed='<focused-root>'
$failed='<failed-fixture-root>'
$context=(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'power-context-root.txt') -Raw).Trim()
$rows=@()
foreach($file in @((Join-Path $active 'controller-identity.json'),(Join-Path $fixed 'native-controller-identity.json'),(Join-Path $fixed 'build-controller-identity.json'),(Join-Path $failed 'native-controller-identity.json'),(Join-Path $context 'controller-identity.json'))){
 $identity=Get-Content -LiteralPath $file -Raw | ConvertFrom-Json -DateKind String
 $live=Get-CimInstance Win32_Process -Filter "ProcessId=$($identity.pid)"
 $same=$null -ne $live -and $live.CreationDate.ToUniversalTime().ToString('o') -eq $identity.createdUtc -and $live.ExecutablePath -eq $identity.executable
 $rows+=@{kind='controller';identityFile=$file;pid=$identity.pid;recordedCreatedUtc=$identity.createdUtc;exactInstancePresent=$same;pidPresent=$null -ne $live;terminationAttempted=$false}
}
foreach($file in @((Join-Path $active 'compile-result.json'),(Join-Path $active 'case-10387.json'),(Join-Path $active 'case-10226.json'),(Join-Path $active 'case-10014.json'))){
 $identity=Get-Content -LiteralPath $file -Raw | ConvertFrom-Json -DateKind String
 $live=Get-CimInstance Win32_Process -Filter "ProcessId=$($identity.ProcessId)"
 $same=$null -ne $live -and $live.CreationDate.ToUniversalTime().Ticks -eq $identity.ProcessStartTicks -and $live.ExecutablePath -eq $identity.FilePath
 $rows+=@{kind='bounded-native-child';identityFile=$file;pid=$identity.ProcessId;recordedStartTicks=$identity.ProcessStartTicks;exactInstancePresent=$same;pidPresent=$null -ne $live;boundedTerminationProven=$identity.TerminationProven;terminationAttempted=$false}
}
$present=@($rows | Where-Object exactInstancePresent)
@{status=if($present.Count -eq 0){'PASS_RECORDED_EXACT_INSTANCES_ABSENT'}else{'FAIL_RECORDED_INSTANCE_PRESENT'};observedUtc=[DateTime]::UtcNow.ToString('o');processes=$rows;terminationAttempted=$false;scope='Five recorded controllers and four bounded diagnostic children only; no global process discovery; native/build harness scratch cleanup recorded separately';cleanupElapsedSeconds='NOT_RECORDED separately';retainedScratch=$active} | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $fixed 'release-audit.json')
Get-Content -LiteralPath (Join-Path $fixed 'release-audit.json') -TotalCount 7
if($present.Count -gt 0){exit 1}

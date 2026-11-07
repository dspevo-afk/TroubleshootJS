param(
 [Parameter(Mandatory=$true)][string]$RepositoryRoot,
 [Parameter(Mandatory=$true)][string]$PythonExe,
 [Parameter(Mandatory=$true)][string]$JavaHome,
 [Parameter(Mandatory=$true)][string]$InputPinsPath,
 [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{64}$')][string]$InputPinsSha256,
 [Parameter(Mandatory=$true)][string]$OutputRoot,
 [Parameter(Mandatory=$true)][ValidateSet('CANARY','SUITE')][string]$Mode
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
foreach($p in @($RepositoryRoot,$PythonExe,$JavaHome)){if(-not[IO.Path]::IsPathRooted($p)){throw 'ABSOLUTE_REQUIRED'}}
$repo=[IO.Path]::GetFullPath($RepositoryRoot)
$python=[IO.Path]::GetFullPath($PythonExe)
$selectedJavaHome=[IO.Path]::GetFullPath($JavaHome)
$selectedPs=(Get-Process -Id $PID).Path
$utf8=[Text.UTF8Encoding]::new($false)
$outer=[Diagnostics.Stopwatch]::StartNew()
function Need([bool]$ok,[string]$why){if(-not $ok){throw $why}}
function Hash([string]$p){(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()}
function Save([string]$path,$value){
 $bytes=$utf8.GetBytes((ConvertTo-Json -InputObject $value -Depth 45 -Compress)+[char]10)
 $s=[IO.File]::Open($path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
 try{$s.Write($bytes,0,$bytes.Length);$s.Flush()}finally{$s.Dispose()}
}
Need ($PSVersionTable.PSVersion.Major-eq5-and$PSVersionTable.PSVersion.Minor-eq1) 'SELECTED_PS51_REQUIRED'
Need ((Hash (Get-Process -Id $PID).Path)-ceq'60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f') 'PS51_PIN'
$module=Join-Path $repo 'scripts/VerifierIsolation.psm1'
Need ((Hash $module)-ceq'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c') 'ISOLATION_PIN'
Import-Module $module -Force -DisableNameChecking
foreach($p in @($InputPinsPath,$PSCommandPath,$python,$selectedPs,$selectedJavaHome)){Assert-VerifierNoReparseAncestors $p}
Need ([IO.Path]::IsPathRooted($InputPinsPath)-and[IO.Path]::IsPathRooted($OutputRoot)) 'ABSOLUTE_REQUIRED'
$InputPinsPath=[IO.Path]::GetFullPath($InputPinsPath);$OutputRoot=[IO.Path]::GetFullPath($OutputRoot)
Need ((Hash $InputPinsPath)-ceq$InputPinsSha256) 'INPUT_PINS_HASH'
$pins=Get-Content -LiteralPath $InputPinsPath -Raw -Encoding UTF8|ConvertFrom-Json
Need ($pins.schema-ceq'tsj-q100-geometric-prefix-study-inputs-v1'-and$pins.repositoryRoot-ceq[IO.Path]::GetFullPath($repo)) 'INPUT_CONTEXT'
Need ($pins.toolPaths.pythonExe-ceq$python-and$pins.toolPaths.javaHome-ceq$selectedJavaHome-and$pins.toolPaths.powershellExe-ceq$selectedPs) 'TOOL_CONTEXT'
$self=@($pins.files|Where-Object {$_.path-ceq$PSCommandPath})
Need ($self.Count-eq1-and$self[0].sha256-ceq(Hash $PSCommandPath)) 'CONTROLLER_SELF_PIN'
function PinnedFile([string]$path){
 Need (-not[String]::IsNullOrEmpty($path)-and[IO.Path]::IsPathRooted($path)-and[IO.Path]::GetFullPath($path)-ceq$path) 'PINNED_CHILD_PATH'
 Assert-VerifierNoReparseAncestors $path
 $row=@($pins.files|Where-Object {$_.path-ceq$path})
 Need ($row.Count-eq1) 'PINNED_CHILD_ROW'
 Need (($row[0].bytes-is[int]-or$row[0].bytes-is[long])-and$row[0].bytes-ge0-and$row[0].sha256-is[string]-and$row[0].sha256-cmatch'^[0-9a-f]{64}$') 'PINNED_CHILD_ROW_TYPE'
 $item=Get-Item -LiteralPath $path -Force
 Need (-not$item.PSIsContainer-and$item.Length-eq$row[0].bytes-and(Hash $path)-ceq$row[0].sha256) 'PINNED_CHILD_BYTES'
}
$readerPath=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'read_geometric_prefix.py'))
$semanticPath=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'test_read_geometric_prefix.py'))
Need ($pins.roles.process.reader-ceq$readerPath) 'READER_ROLE_PATH'
foreach($path in @($PSCommandPath,$selectedPs,$python,[string]$pins.roles.process.host,$readerPath,$semanticPath)){PinnedFile $path}
$temp=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')+'\'
Need ($OutputRoot.StartsWith($temp,[StringComparison]::OrdinalIgnoreCase)-and-not$OutputRoot.StartsWith([IO.Path]::GetFullPath($repo).TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) 'EXTERNAL_OUTPUT_REQUIRED'
Assert-VerifierNoReparseAncestors ([IO.Path]::GetDirectoryName($OutputRoot))
Need (-not(Test-Path -LiteralPath $OutputRoot)) 'OUTPUT_EXISTS'
[void][IO.Directory]::CreateDirectory($OutputRoot)
$results=New-Object 'System.Collections.Generic.List[object]'
$phase='NEGATIVE_CONTROLS'
$failure=$null
$consumerClosureMs=$null
function SaveCallFailure([string]$name,[string]$exe,[string[]]$arguments,[int]$limit,[double]$elapsed,$errorRecord){
 $data=[ordered]@{}
 foreach($key in @($errorRecord.Exception.Data.Keys)){$data[[string]$key]=[string]$errorRecord.Exception.Data[$key]}
 Save (Join-Path $OutputRoot ($name+'-call-failure.json')) ([ordered]@{
  schema='tsj-q100-geometric-failed-call-v1';status='CALL_FAILED_WITHOUT_RETURNED_PROCESS_RECEIPT';
  phase=$phase;attemptedExecutable=$exe;attemptedArguments=$arguments;limitMs=$limit;
  elapsedMs=$elapsed;exception=$errorRecord.Exception.ToString();exceptionData=$data;
  terminationProven=$false;process=$null
 })
}
function OutputFingerprint([string]$root){
 Assert-VerifierNoReparseAncestors $root
 $base=[IO.Path]::GetFullPath($root).TrimEnd('\')+'\'
 $pending=New-Object 'System.Collections.Generic.List[object]'
 $pending.Add([pscustomobject]@{path=$root;depth=0})
 $rows=New-Object 'System.Collections.Generic.List[object]'
 $total=0L
 for($i=0;$i-lt$pending.Count;$i++){
  $directory=$pending[$i]
  Assert-VerifierNoReparseAncestors $directory.path
  foreach($item in @(Get-ChildItem -LiteralPath $directory.path -Force|Sort-Object Name)){
   Assert-VerifierNoReparseAncestors $item.FullName
   $full=[IO.Path]::GetFullPath($item.FullName)
   Need ($full.StartsWith($base,[StringComparison]::OrdinalIgnoreCase)) 'FINGERPRINT_CONTAINMENT'
   $relative=$full.Substring($base.Length).Replace('\','/')
   Need ($rows.Count-lt256) 'GENUINE_OUTPUT_CENSUS'
   if($item.PSIsContainer){
    Need ($directory.depth-lt5) 'GENUINE_OUTPUT_DEPTH'
    $pending.Add([pscustomobject]@{path=$full;depth=($directory.depth+1)})
    $rows.Add([ordered]@{path=$relative;kind='DIRECTORY';bytes=0;sha256=$null})
   }else{
    $total+=$item.Length;Need ($total-le41943040) 'GENUINE_SUITE_OUTPUT_BYTES'
    $rows.Add([ordered]@{path=$relative;kind='FILE';bytes=$item.Length;sha256=(Hash $full)})
   }
  }
 }
 Need ($rows.Count-ge1) 'GENUINE_OUTPUT_EMPTY'
 return ConvertTo-Json -InputObject @($rows.ToArray()|Sort-Object path) -Depth 5 -Compress
}
function HostArgs([string]$specPath,[string]$specSha,[string]$target){
 return @('-NoLogo','-NoProfile','-NonInteractive','-File',[string]$pins.roles.process.host,
  '-RepositoryRoot',[string]$pins.repositoryRoot,'-InputPinsPath',$specPath,
  '-InputPinsSha256',$specSha,'-OutputRoot',$target,'-JavaHome',$selectedJavaHome,'-PythonExe',$python,'-Mode',$Mode)
}
function CallHost([string]$name,[string]$specPath,[string]$specSha,[string]$target,[int]$limit){
 $argsForHost=HostArgs $specPath $specSha $target
 PinnedFile $selectedPs
 PinnedFile ([string]$pins.roles.process.host)
 $watch=[Diagnostics.Stopwatch]::StartNew()
 try{$proc=Invoke-VerifierBoundedProcess $selectedPs $argsForHost $limit}
 catch{
  $originalCallFailure=$_;$watch.Stop()
  try{SaveCallFailure $name $selectedPs $argsForHost $limit $watch.Elapsed.TotalMilliseconds $originalCallFailure}
  catch{$originalCallFailure.Exception.Data['P8_FAILED_CALL_RECORD_WRITE_ERROR']=[string]$_.Exception.Message}
  throw $originalCallFailure
 }
 $watch.Stop()
 $consumerWatch=$null
 if($name-ceq'positive'){$consumerWatch=[Diagnostics.Stopwatch]::StartNew()}
 $record=[ordered]@{schema='tsj-q100-geometric-prefix-parent-v1';elapsedMs=$watch.Elapsed.TotalMilliseconds;process=$proc}
 $recordPath=Join-Path $OutputRoot ($name+'-parent.json');Save $recordPath $record
 Need ($proc.TerminationProven-and-not(Test-Path -LiteralPath $proc.ProcessRoot)) 'HOST_PROCESS_CLEANUP'
 return [pscustomobject]@{record=$record;path=$recordPath;target=$target;consumerWatch=$consumerWatch}
}
function Variant([string]$name,[string]$filePath){
 $copy=Get-Content -LiteralPath $InputPinsPath -Raw -Encoding UTF8|ConvertFrom-Json
 $rows=@($copy.files|Where-Object {$_.path-ceq$filePath});Need ($rows.Count-eq1) 'VARIANT_ROW_MISSING'
 $rows[0].sha256='0'*64
 $variantPath=Join-Path $OutputRoot ($name+'-pins.json');Save $variantPath $copy
 return [pscustomobject]@{path=$variantPath;sha=(Hash $variantPath)}
}
try {
 $selfVariant=Variant 'self-pin' ([string]$pins.roles.process.host)
 $extraVariant=Variant 'extra-pin' ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'PLAN_R1.md')))
 $preexisting=Join-Path $OutputRoot 'preexisting-output';[void][IO.Directory]::CreateDirectory($preexisting)
 $insideSource=Join-Path ([string]$pins.repositoryRoot) ('P8-SHOULD-NOT-EXIST-'+[Guid]::NewGuid().ToString('N'))
 Need (-not(Test-Path -LiteralPath $insideSource)) 'SOURCE_NEGATIVE_PATH_EXISTS'
 $cases=@(
  @{name='digest';spec=$InputPinsPath;sha=('0'*64);target=(Join-Path $OutputRoot 'digest-output');code='INPUT_PINS_HASH';preexisting=$false},
  @{name='self-pin';spec=$selfVariant.path;sha=$selfVariant.sha;target=(Join-Path $OutputRoot 'self-pin-output');code='SELF_PIN';preexisting=$false},
  @{name='extra-pin';spec=$extraVariant.path;sha=$extraVariant.sha;target=(Join-Path $OutputRoot 'extra-pin-output');code='EXTRA_INPUT_HASH';preexisting=$false},
  @{name='existing-output';spec=$InputPinsPath;sha=$InputPinsSha256;target=$preexisting;code='OUTPUT_EXISTS';preexisting=$true},
  @{name='source-output';spec=$InputPinsPath;sha=$InputPinsSha256;target=$insideSource;code='EXTERNAL_OUTPUT_REQUIRED';preexisting=$false}
 )
 foreach($case in $cases){
  Need ($outer.Elapsed.TotalMilliseconds+60000-lt1800000) 'OUTER_RESERVE'
  $child=CallHost $case.name $case.spec $case.sha $case.target 60000
  $proc=$child.record.process
  Need ($proc.ExitCode-ne0-and[String]::IsNullOrEmpty($proc.Stdout)) ('NEGATIVE_REJECTION_'+$case.name)
  $match=[regex]::Match($proc.Stderr,'\AP8_PREFIX_HOST_REJECT (?<json>[^\r\n]+)\r?\n\z')
  Need $match.Success ('NEGATIVE_FRAME_'+$case.name)
  $rejected=$match.Groups['json'].Value|ConvertFrom-Json
  $frameKeys=@($rejected.PSObject.Properties|ForEach-Object {$_.Name})
  $expectedKeys=@('code','phase','outputCreated','scratchCreated','compilerInvoked','nativeInvoked','cleanupError')
  Need ($frameKeys.Count-eq7-and@($frameKeys|Where-Object {$_-notin$expectedKeys}).Count-eq0) ('NEGATIVE_FRAME_KEYS_'+$case.name)
  Need ($rejected.code-ceq$case.code-and$rejected.phase-is[string]-and-not[string]::IsNullOrEmpty($rejected.phase)-and$null-eq$rejected.cleanupError) ('NEGATIVE_CODE_PHASE_'+$case.name)
  foreach($flag in @('outputCreated','scratchCreated','compilerInvoked','nativeInvoked')){
   Need ($rejected.$flag-is[bool]-and$rejected.$flag-eq$false) ('NEGATIVE_PHASE_BOUNDARY_'+$case.name)
  }
  Need ((Test-Path -LiteralPath $case.target)-eq$case.preexisting) ('NEGATIVE_MUTATED_OUTPUT_'+$case.name)
  if($case.preexisting){Need (@(Get-ChildItem -LiteralPath $case.target -Force).Count-eq0) 'EXISTING_OUTPUT_MUTATED'}
  $results.Add([ordered]@{id=$case.name;status='PASS_EXPECTED_REJECTION';code=$case.code;phase=$rejected.phase;beforeCompilerAndNative=$true;parentReceipt=[IO.Path]::GetFileName($child.path);elapsedMs=$child.record.elapsedMs;processId=$proc.ProcessId})
 }
 $phase='POSITIVE_'+$Mode
 Need ($outer.Elapsed.TotalMilliseconds+1260000-lt1800000) 'OUTER_POSITIVE_RESERVE'
 $positiveLimit=[int][Math]::Floor(1800000-$outer.Elapsed.TotalMilliseconds-540000)
 $run=CallHost 'positive' $InputPinsPath $InputPinsSha256 (Join-Path $OutputRoot 'positive') $positiveLimit
 $closure=$run.consumerWatch
 Need ($run.record.process.ExitCode-eq0) 'HOST_POSITIVE_FAILED'
 $genuineOutputFingerprint=OutputFingerprint $run.target
 $phase='INDEPENDENT_CONSUMER'
 $reader=$readerPath
 $readerArgs=@('-I','-B',$reader,$InputPinsPath,$InputPinsSha256,$run.target,$run.path)
 PinnedFile $python
 PinnedFile $reader
 $readerWatch=[Diagnostics.Stopwatch]::StartNew()
 try{$readerProcess=Invoke-VerifierBoundedProcess $python $readerArgs 180000}
 catch{
  $originalCallFailure=$_;$readerWatch.Stop()
  try{SaveCallFailure 'consumer' $python $readerArgs 180000 $readerWatch.Elapsed.TotalMilliseconds $originalCallFailure}
  catch{$originalCallFailure.Exception.Data['P8_FAILED_CALL_RECORD_WRITE_ERROR']=[string]$_.Exception.Message}
  throw $originalCallFailure
 }
 $readerWatch.Stop()
 Save (Join-Path $OutputRoot 'consumer-process.json') ([ordered]@{schema='tsj-q100-geometric-canary-consumer-process-v1';elapsedMs=$readerWatch.Elapsed.TotalMilliseconds;process=$readerProcess})
 Need ($readerProcess.ExitCode-eq0-and$readerProcess.TerminationProven-and[String]::IsNullOrEmpty($readerProcess.Stderr)-and-not(Test-Path -LiteralPath $readerProcess.ProcessRoot)) 'CONSUMER_PROCESS_FAILED'
 $accepted=$readerProcess.Stdout|ConvertFrom-Json
 $expectedConsumerStatus=if($Mode-ceq'CANARY'){'PASS_Q100_GEOMETRIC_PREFIX_HOST_CANARY_ONLY'}else{'PASS_Q100_GEOMETRIC_PREFIX_OBSERVATIONS_ONLY'}
 Need ($accepted.status-ceq$expectedConsumerStatus-and$accepted.layoutQualification-is[bool]-and$accepted.layoutQualification-eq$false) 'CONSUMER_MARKER'
 Need ((OutputFingerprint $run.target)-ceq$genuineOutputFingerprint) 'GENUINE_OUTPUT_CHANGED_BY_CONSUMER'
 Save (Join-Path $OutputRoot 'consumer-closure.json') ([ordered]@{schema='tsj-q100-geometric-consumer-closure-v1';status='SOURCE_OUTPUT_CHECKED_FINAL_WRITE_GUARD_PENDING';elapsedBeforeFinalWriteMs=$closure.Elapsed.TotalMilliseconds;parentReceiptWriteIncluded=$true;genuineOutputUnchanged=$true})
 $closure.Stop();$consumerClosureMs=$closure.Elapsed.TotalMilliseconds
 Need ($consumerClosureMs-le180000) 'CONSUMER_FULL_CLOSURE_LIMIT'
 $results.Add([ordered]@{id='positive';status=$expectedConsumerStatus;parentReceipt=[IO.Path]::GetFileName($run.path);consumer='consumer-process.json';elapsedMs=$run.record.elapsedMs;processId=$run.record.process.ProcessId})
 $phase='READER_NEGATIVE_CONTROLS'
 $hostResult=Get-Content -LiteralPath (Join-Path $run.target 'host-result.json') -Raw -Encoding UTF8|ConvertFrom-Json
 Need ($hostResult.timing.cleanupStartOffsetMs-gt1) 'TIMING_NEGATIVE_PREREQUISITE'
 $readerCases=@(
  @{name='reader-elapsed-type';code='PARENT_ELAPSED_TYPE_INVALID';mutate={param($v) $v.elapsedMs=$true}},
  @{name='reader-exit-two';code='PARENT_PROCESS_EXIT_INVALID';mutate={param($v) $v.process.ExitCode=2}},
  @{name='reader-termination';code='PARENT_PROCESS_TERMINATION_INVALID';mutate={param($v) $v.process.TerminationProven=$false}},
  @{name='reader-timing-order';code='PARENT_TIMING_ORDER_INVALID';mutate={param($v) $v.elapsedMs=1.0}},
  @{name='reader-input-pins';code='INPUT_PINS_HASH';mutate={param($v)}}
 )
 $genuineParentHash=Hash $run.path
 foreach($case in $readerCases){
  Need ($outer.Elapsed.TotalMilliseconds+30000-lt1800000) 'OUTER_READER_NEGATIVE_RESERVE'
  $variant=Get-Content -LiteralPath $run.path -Raw -Encoding UTF8|ConvertFrom-Json
  [void](& $case.mutate $variant)
  $variantPath=Join-Path $OutputRoot ($case.name+'-parent.json');Save $variantPath $variant
  $negativePinsSha=if($case.name-ceq'reader-input-pins'){'0'*64}else{$InputPinsSha256}
  $negativeArgs=@('-I','-B',$reader,$InputPinsPath,$negativePinsSha,$run.target,$variantPath)
  PinnedFile $python
  PinnedFile $reader
  $negativeWatch=[Diagnostics.Stopwatch]::StartNew()
  try{$negativeProcess=Invoke-VerifierBoundedProcess $python $negativeArgs 30000}
  catch{
   $originalCallFailure=$_;$negativeWatch.Stop()
   try{SaveCallFailure $case.name $python $negativeArgs 30000 $negativeWatch.Elapsed.TotalMilliseconds $originalCallFailure}
   catch{$originalCallFailure.Exception.Data['P8_FAILED_CALL_RECORD_WRITE_ERROR']=[string]$_.Exception.Message}
   throw $originalCallFailure
  }
  $negativeWatch.Stop()
  $negativeRecord=$case.name+'-process.json'
  Save (Join-Path $OutputRoot $negativeRecord) ([ordered]@{schema='tsj-q100-geometric-reader-negative-v1';syntheticNegativeFixture=$true;expectedCode=$case.code;elapsedMs=$negativeWatch.Elapsed.TotalMilliseconds;process=$negativeProcess})
  Need ($negativeProcess.ExitCode-ne0-and$negativeProcess.TerminationProven-and[String]::IsNullOrEmpty($negativeProcess.Stdout)-and$negativeProcess.Stderr-ceq('P8_PREFIX_READER_REJECT '+$case.code+[char]10)-and-not(Test-Path -LiteralPath $negativeProcess.ProcessRoot)) ('READER_NEGATIVE_FAILED_'+$case.name)
  Need ((Hash $run.path)-ceq$genuineParentHash) 'GENUINE_PARENT_CHANGED'
  Need ((OutputFingerprint $run.target)-ceq$genuineOutputFingerprint) 'GENUINE_OUTPUT_CHANGED_BY_NEGATIVE'
  $results.Add([ordered]@{id=$case.name;status='PASS_EXPECTED_REJECTION';syntheticNegativeFixture=$true;code=$case.code;processReceipt=$negativeRecord;elapsedMs=$negativeWatch.Elapsed.TotalMilliseconds;processId=$negativeProcess.ProcessId})
 }

 if($Mode-ceq'SUITE'){
  $phase='SYNTHETIC_REASON_CONTROLS'
  Need ($outer.Elapsed.TotalMilliseconds+180000-lt1800000) 'OUTER_SEMANTIC_RESERVE'
  $testPath=$semanticPath
  $testArgs=@('-I','-B',$testPath,$InputPinsPath,$InputPinsSha256,$run.target,$run.path)
  PinnedFile $python
  PinnedFile $testPath
  PinnedFile $reader
  $testWatch=[Diagnostics.Stopwatch]::StartNew()
  try{$testProcess=Invoke-VerifierBoundedProcess $python $testArgs 180000}
  catch{
   $originalCallFailure=$_;$testWatch.Stop()
   try{SaveCallFailure 'semantic' $python $testArgs 180000 $testWatch.Elapsed.TotalMilliseconds $originalCallFailure}
   catch{$originalCallFailure.Exception.Data['P8_FAILED_CALL_RECORD_WRITE_ERROR']=[string]$_.Exception.Message}
   throw $originalCallFailure
  }
  $testWatch.Stop()
  Save (Join-Path $OutputRoot 'semantic-process.json') ([ordered]@{schema='tsj-q100-geometric-semantic-process-v1';syntheticFixtures=$true;elapsedMs=$testWatch.Elapsed.TotalMilliseconds;process=$testProcess})
  Need ($testProcess.ExitCode-eq0-and$testProcess.TerminationProven-and[String]::IsNullOrEmpty($testProcess.Stderr)-and-not(Test-Path -LiteralPath $testProcess.ProcessRoot)) 'SEMANTIC_PROCESS_FAILED'
  $tested=$testProcess.Stdout|ConvertFrom-Json
  $summaryKeys=@($tested.PSObject.Properties|ForEach-Object {$_.Name})
  $expectedSummaryKeys=@('schema','status','syntheticFixtures','passed','positiveControl','cases','genuineOutputsUnchanged','layoutQualification')
  Need ($summaryKeys.Count-eq8-and@($summaryKeys|Where-Object {$_-notin$expectedSummaryKeys}).Count-eq0) 'SEMANTIC_SUMMARY_KEYS'
  Need ($tested.schema-is[string]-and$tested.status-is[string]-and$tested.schema-ceq'tsj-q100-geometric-prefix-semantic-controls-v1'-and$tested.status-ceq'PASS_SYNTHETIC_REASON_CONTROLS_ONLY'-and$tested.passed-is[int]-and$tested.passed-eq12) 'SEMANTIC_MARKER'
  foreach($flag in @('syntheticFixtures','positiveControl','genuineOutputsUnchanged')){Need ($tested.$flag-is[bool]-and$tested.$flag-eq$true) 'SEMANTIC_PROOF'}
  Need ($tested.layoutQualification-is[bool]-and$tested.layoutQualification-eq$false-and$tested.cases-is[array]-and$tested.cases.Count-eq12) 'SEMANTIC_CASE_CENSUS'
  $expectedCases=@(
   @('header-input','REASON_HEADER_BINDING'),@('header-source','REASON_HEADER_BINDING'),
   @('query-order','REASON_QUERY_ORDER'),@('query-identity','REASON_QUERY_IDENTITY'),
   @('event-coordinates','REASON_EVENT_CORRELATION'),@('owner-type','REASON_EVENT_FIELD_TYPE'),
   @('returned-null','REASON_QUERY_RESULT'),@('missing-footer','REASON_FRAMING'),
   @('units','REASON_UNIT_ARITHMETIC'),@('event-extra-key','REASON_EVENT_KEYS'),
   @('missing-fine','REASON_EVENT_COUNT'),@('null-fine','REASON_MODE_EVENTS')
  )
  for($caseIndex=0;$caseIndex-lt12;$caseIndex++){
   $case=$tested.cases[$caseIndex]
   $caseKeys=@($case.PSObject.Properties|ForEach-Object {$_.Name})
   Need ($caseKeys.Count-eq4-and@($caseKeys|Where-Object {$_-notin@('id','expectedCode','actualCode','status')}).Count-eq0) 'SEMANTIC_CASE_KEYS'
   foreach($field in @('id','expectedCode','actualCode','status')){Need ($case.$field-is[string]) 'SEMANTIC_CASE_FIELD_TYPE'}
   Need ($case.id-ceq$expectedCases[$caseIndex][0]-and$case.expectedCode-ceq$expectedCases[$caseIndex][1]-and$case.actualCode-ceq$expectedCases[$caseIndex][1]-and$case.status-ceq'PASS_EXPECTED_REJECTION') 'SEMANTIC_CASE_FAILURE'
  }
  Need ((Hash $run.path)-ceq$genuineParentHash-and(OutputFingerprint $run.target)-ceq$genuineOutputFingerprint) 'GENUINE_CHANGED_BY_SEMANTIC'
  $results.Add([ordered]@{id='semantic';status='PASS_SYNTHETIC_REASON_CONTROLS_ONLY';processReceipt='semantic-process.json';elapsedMs=$testWatch.Elapsed.TotalMilliseconds;processId=$testProcess.ProcessId})
 }
 $phase='COMPLETE'
}catch{
 $failure=$_.Exception.Message
}finally{
 $status=if($null-eq$failure-and$phase-ceq'COMPLETE'){if($Mode-ceq'CANARY'){'PASS_PREFIX_CANARY_CONTROLS_ONLY'}else{'PASS_PREFIX_SUITE_CONTROLS_AND_REASON_OBSERVATIONS_ONLY'}}else{'FAIL'}
 try{
  Save (Join-Path $OutputRoot 'controls-result.json') ([ordered]@{schema='tsj-q100-geometric-prefix-controls-v1';mode=$Mode;status=$status;phase=$phase;error=$failure;results=$results.ToArray();elapsedBeforeFinalWriteMs=$outer.Elapsed.TotalMilliseconds;consumerFullClosureMs=$consumerClosureMs;inputPinsSha256=$InputPinsSha256;layoutQualification=$false;finalOuterGuardPending=$true})
 }catch{
  if($null-eq$failure){throw}
  [Console]::Error.Write('P8_CONTROL_FINAL_RECORD_FAILURE '+$_.Exception.Message+[char]10)
 }
}
Need ($null-eq$failure) ('P8_PREFIX_CONTROLS_FAILED '+$failure)
Need ($outer.Elapsed.TotalMilliseconds-le1800000) 'OUTER_FINAL_WALL_LIMIT'
Write-Output $status
Need ($outer.Elapsed.TotalMilliseconds-le1800000) 'OUTER_FINAL_OUTPUT_WALL_LIMIT'

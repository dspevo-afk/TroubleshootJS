param(
 [Parameter(Mandatory=$true)][string]$RepositoryRoot,
 [Parameter(Mandatory=$true)][string]$PythonExe,
 [Parameter(Mandatory=$true)][string]$JavaHome,
 [Parameter(Mandatory=$true)][string]$InputPinsPath,
 [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{64}$')][string]$InputPinsSha256,
 [Parameter(Mandatory=$true)][string]$OutputRoot
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
 $bytes=$utf8.GetBytes((ConvertTo-Json -InputObject $value -Depth 45)+[char]10)
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
Need ($pins.schema-ceq'tsj-q100-geometric-canary-inputs-v1'-and$pins.repositoryRoot-ceq[IO.Path]::GetFullPath($repo)) 'INPUT_CONTEXT'
Need ($pins.pythonExe-ceq$python-and$pins.javaHome-ceq$selectedJavaHome) 'TOOL_CONTEXT'
$self=@($pins.files|Where-Object {$_.path-ceq$PSCommandPath})
Need ($self.Count-eq1-and$self[0].sha256-ceq(Hash $PSCommandPath)) 'CONTROLLER_SELF_PIN'
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
 $items=@(Get-ChildItem -LiteralPath $root -Force)
 Need ($items.Count-ge1-and$items.Count-le64) 'GENUINE_OUTPUT_CENSUS'
 $rows=New-Object 'System.Collections.Generic.List[object]'
 $total=0L
 foreach($item in @($items|Sort-Object Name)){
  Need (-not$item.PSIsContainer) 'GENUINE_OUTPUT_SUBDIRECTORY'
  Assert-VerifierNoReparseAncestors $item.FullName
  $total+=$item.Length;Need ($total-le8388608) 'GENUINE_OUTPUT_BYTES'
  $rows.Add([ordered]@{name=$item.Name;bytes=$item.Length;sha256=(Hash $item.FullName)})
 }
 return ConvertTo-Json -InputObject $rows.ToArray() -Depth 5 -Compress
}
function HostArgs([string]$specPath,[string]$specSha,[string]$target){
 return @('-NoLogo','-NoProfile','-NonInteractive','-File',[string]$pins.hostPath,
  '-RepositoryRoot',[string]$pins.repositoryRoot,'-InputPinsPath',$specPath,
  '-InputPinsSha256',$specSha,'-OutputRoot',$target,'-JavaHome',$selectedJavaHome,'-PythonExe',$python)
}
function CallHost([string]$name,[string]$specPath,[string]$specSha,[string]$target,[int]$limit){
 $argsForHost=HostArgs $specPath $specSha $target
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
 $record=[ordered]@{schema='tsj-q100-geometric-canary-parent-v1';elapsedMs=$watch.Elapsed.TotalMilliseconds;process=$proc}
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
 $selfVariant=Variant 'self-pin' ([string]$pins.hostPath)
 $extraVariant=Variant 'extra-pin' ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'test_overlay.py')))
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
  $match=[regex]::Match($proc.Stderr,'\AP8_CANARY_HOST_REJECT (?<json>[^\r\n]+)\r?\n\z')
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
 $phase='POSITIVE_CANARY'
 Need ($outer.Elapsed.TotalMilliseconds+1530000-lt1800000) 'OUTER_POSITIVE_RESERVE'
 $positiveLimit=[int][Math]::Floor(1800000-$outer.Elapsed.TotalMilliseconds-330000)
 $run=CallHost 'positive' $InputPinsPath $InputPinsSha256 (Join-Path $OutputRoot 'positive') $positiveLimit
 $closure=$run.consumerWatch
 Need ($run.record.process.ExitCode-eq0) 'HOST_POSITIVE_FAILED'
 $genuineOutputFingerprint=OutputFingerprint $run.target
 $phase='INDEPENDENT_CONSUMER'
 $reader=Join-Path $PSScriptRoot 'read_canary.py'
 $readerArgs=@('-I','-B',$reader,$InputPinsPath,$InputPinsSha256,$run.target,$run.path)
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
 Need ($accepted.status-ceq'PASS_Q100_GEOMETRIC_LITERAL_CANARY_ONLY') 'CONSUMER_MARKER'
 Need ((OutputFingerprint $run.target)-ceq$genuineOutputFingerprint) 'GENUINE_OUTPUT_CHANGED_BY_CONSUMER'
 Save (Join-Path $OutputRoot 'consumer-closure.json') ([ordered]@{schema='tsj-q100-geometric-consumer-closure-v1';status='SOURCE_OUTPUT_CHECKED_FINAL_WRITE_GUARD_PENDING';elapsedBeforeFinalWriteMs=$closure.Elapsed.TotalMilliseconds;parentReceiptWriteIncluded=$true;genuineOutputUnchanged=$true})
 $closure.Stop();$consumerClosureMs=$closure.Elapsed.TotalMilliseconds
 Need ($consumerClosureMs-le180000) 'CONSUMER_FULL_CLOSURE_LIMIT'
 $results.Add([ordered]@{id='positive';status='PASS_LITERAL_CANARY_ONLY';parentReceipt=[IO.Path]::GetFileName($run.path);consumer='consumer-process.json';elapsedMs=$run.record.elapsedMs;processId=$run.record.process.ProcessId})
 $phase='READER_NEGATIVE_CONTROLS'
 $hostResult=Get-Content -LiteralPath (Join-Path $run.target 'host-result.json') -Raw -Encoding UTF8|ConvertFrom-Json
 Need ($hostResult.timing.cleanupStartOffsetMs-gt1) 'TIMING_NEGATIVE_PREREQUISITE'
 $readerCases=@(
  @{name='reader-elapsed-type';code='PARENT_ELAPSED_TYPE_INVALID';mutate={param($v) $v.elapsedMs=$true}},
  @{name='reader-exit-two';code='PARENT_PROCESS_EXIT_INVALID';mutate={param($v) $v.process.ExitCode=2}},
  @{name='reader-termination';code='PARENT_PROCESS_TERMINATION_INVALID';mutate={param($v) $v.process.TerminationProven=$false}},
  @{name='reader-timing-order';code='PARENT_TIMING_ORDER_INVALID';mutate={param($v) $v.elapsedMs=1.0}}
 )
 $genuineParentHash=Hash $run.path
 foreach($case in $readerCases){
  Need ($outer.Elapsed.TotalMilliseconds+30000-lt1800000) 'OUTER_READER_NEGATIVE_RESERVE'
  $variant=Get-Content -LiteralPath $run.path -Raw -Encoding UTF8|ConvertFrom-Json
  [void](& $case.mutate $variant)
  $variantPath=Join-Path $OutputRoot ($case.name+'-parent.json');Save $variantPath $variant
  $negativeArgs=@('-I','-B',$reader,$InputPinsPath,$InputPinsSha256,$run.target,$variantPath)
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
  Need ($negativeProcess.ExitCode-ne0-and$negativeProcess.TerminationProven-and[String]::IsNullOrEmpty($negativeProcess.Stdout)-and$negativeProcess.Stderr-ceq('P8_CANARY_READER_REJECT '+$case.code+[char]10)-and-not(Test-Path -LiteralPath $negativeProcess.ProcessRoot)) ('READER_NEGATIVE_FAILED_'+$case.name)
  Need ((Hash $run.path)-ceq$genuineParentHash) 'GENUINE_PARENT_CHANGED'
  Need ((OutputFingerprint $run.target)-ceq$genuineOutputFingerprint) 'GENUINE_OUTPUT_CHANGED_BY_NEGATIVE'
  $results.Add([ordered]@{id=$case.name;status='PASS_EXPECTED_REJECTION';syntheticNegativeFixture=$true;code=$case.code;processReceipt=$negativeRecord;elapsedMs=$negativeWatch.Elapsed.TotalMilliseconds;processId=$negativeProcess.ProcessId})
 }
 $phase='COMPLETE'
}catch{
 $failure=$_.Exception.Message
}finally{
 $status=if($null-eq$failure-and$phase-ceq'COMPLETE'){'PASS_EARLY_HOST_CONTROLS_AND_LITERAL_CANARY_ONLY'}else{'FAIL'}
 try{
  Save (Join-Path $OutputRoot 'controls-result.json') ([ordered]@{schema='tsj-q100-geometric-canary-controls-v1';status=$status;phase=$phase;error=$failure;results=$results.ToArray();elapsedBeforeFinalWriteMs=$outer.Elapsed.TotalMilliseconds;consumerFullClosureMs=$consumerClosureMs;inputPinsSha256=$InputPinsSha256;layoutQualification=$false;finalOuterGuardPending=$true})
 }catch{
  if($null-eq$failure){throw}
  [Console]::Error.Write('P8_CONTROL_FINAL_RECORD_FAILURE '+$_.Exception.Message+[char]10)
 }
}
Need ($null-eq$failure) ('P8_CANARY_CONTROLS_FAILED '+$failure)
Need ($outer.Elapsed.TotalMilliseconds-le1800000) 'OUTER_FINAL_WALL_LIMIT'
Write-Output 'PASS_EARLY_HOST_CONTROLS_AND_LITERAL_CANARY_ONLY'
Need ($outer.Elapsed.TotalMilliseconds-le1800000) 'OUTER_FINAL_OUTPUT_WALL_LIMIT'

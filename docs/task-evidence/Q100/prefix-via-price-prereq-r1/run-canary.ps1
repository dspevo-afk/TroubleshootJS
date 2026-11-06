param(
 [Parameter(Mandatory=$true)][ValidateSet('BASE','RAMP')][string]$Mode,
 [Parameter(Mandatory=$true)][string]$RepositoryRoot,
 [Parameter(Mandatory=$true)][string]$BaseManifestPath,
 [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{64}$')][string]$BaseManifestSha256,
 [Parameter(Mandatory=$true)][string]$InputPinsPath,
 [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{64}$')][string]$InputPinsSha256,
 [Parameter(Mandatory=$true)][string]$OutputRoot,
 [Parameter(Mandatory=$true)][string]$JavaHome,
 [Parameter(Mandatory=$true)][string]$PythonExe
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if(-not [IO.Path]::IsPathRooted($RepositoryRoot)){throw 'REPO_ABSOLUTE_REQUIRED'}
$RepositoryRoot=[IO.Path]::GetFullPath($RepositoryRoot)
$utf8=[Text.UTF8Encoding]::new($false)
$outer=[Diagnostics.Stopwatch]::StartNew()
$Stage='CANARY'
function Hash([string]$p){(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()}
function Require([bool]$ok,[string]$why){if(-not $ok){throw $why}}
function Save([string]$name,$value){
 $path=Join-Path $OutputRoot $name
 $bytes=$utf8.GetBytes((ConvertTo-Json -InputObject $value -Depth 30)+[char]10)
 $s=[IO.File]::Open($path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
 try{$s.Write($bytes,0,$bytes.Length);$s.Flush()}finally{$s.Dispose()}
}
Require ($PSVersionTable.PSVersion.Major-eq5-and$PSVersionTable.PSVersion.Minor-eq1) 'SELECTED_PS51_REQUIRED'
$hostProcess=Get-Process -Id $PID
Require ((Hash $hostProcess.Path)-ceq'60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f') 'PS51_PIN'
Require ($BaseManifestSha256-ceq'8bc48095905b2e34b3315653c12e54262a8ef6aa4770f9db2c8a59239d4f40ab') 'BASE_PIN'
$module=Join-Path $RepositoryRoot 'scripts/VerifierIsolation.psm1'
Require ((Hash $module)-ceq'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c') 'ISOLATION_PIN'
Import-Module $module -Force -DisableNameChecking
$java=Join-Path $JavaHome 'bin/java.exe';$javac=Join-Path $JavaHome 'bin/javac.exe'
foreach($p in @($RepositoryRoot,$BaseManifestPath,$InputPinsPath,$JavaHome,$PythonExe,$PSCommandPath,$java,$javac)){
 Require ([IO.Path]::IsPathRooted($p)) 'ABSOLUTE_REQUIRED'
 Assert-VerifierNoReparseAncestors $p
}
Require ((Hash $BaseManifestPath)-ceq$BaseManifestSha256) 'BASE_HASH'
Require ((Hash $InputPinsPath)-ceq$InputPinsSha256) 'INPUT_PINS_HASH'
Require ((Hash $PythonExe)-ceq'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec') 'PYTHON_PIN'
Require ((Hash $java)-ceq'e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce') 'JAVA_PIN'
Require ((Hash $javac)-ceq'61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64') 'JAVAC_PIN'
$pins=Get-Content -LiteralPath $InputPinsPath -Raw -Encoding UTF8|ConvertFrom-Json
Require ($pins.schema-ceq'tsj-prefix-via-ramp-inputs-v1'-and$pins.repositoryRoot-ceq$RepositoryRoot) 'INPUT_CONTEXT'
Require ($pins.baseManifestPath-ceq[IO.Path]::GetFullPath($BaseManifestPath)) 'BASE_PATH_CONTEXT'
Require ($pins.planSha256-ceq'2d640b3d36cca2ae2613718e356e84ed603abf3ccf813f4a1372f35b28e72bce') 'PLAN_PIN'
Require (@($pins.files|Where-Object {$_.path-ceq$PSCommandPath-and$_.sha256-ceq(Hash $PSCommandPath)}).Count-eq1) 'SELF_PIN'
Require ($pins.boundarySha256-ceq'9fae9c7ab6e2404c9aa157122f4c0e331830766de336fc3ff6dfd0b36b6ca68f') 'BOUNDARY_PIN'
$pinnedInputs=@{}
foreach($row in $pins.files){
 Require ([IO.Path]::IsPathRooted($row.path)-and[IO.Path]::GetFullPath($row.path)-ceq$row.path) 'PIN_CANONICAL_ABSOLUTE'
 Require (-not$pinnedInputs.ContainsKey($row.path)) 'DUPLICATE_INPUT_PIN'
 Assert-VerifierNoReparseAncestors $row.path
 Require ((Get-Item -LiteralPath $row.path).Length-eq$row.bytes-and(Hash $row.path)-ceq$row.sha256) 'EXTRA_INPUT_HASH'
 $pinnedInputs.Add($row.path,$row)
}
function Require-Pinned([string]$path){
 Require ([IO.Path]::IsPathRooted($path)-and[IO.Path]::GetFullPath($path)-ceq$path-and$pinnedInputs.ContainsKey($path)) 'UNBOUND_CONSUMED_INPUT'
}
Require ($pins.preflight-ceq(Join-Path $PSScriptRoot 'audit_inputs.py')) 'PREFLIGHT_IDENTITY'
foreach($path in @($PSCommandPath,$pins.baseManifestPath,$pins.preflight,$pins.planPath,$pins.boundaryPath,$pins.overlayManifestPath,$pins.bridgeManifest)){Require-Pinned $path}
Require ((Hash $pins.planPath)-ceq$pins.planSha256-and(Hash $pins.boundaryPath)-ceq$pins.boundarySha256-and(Hash $pins.overlayManifestPath)-ceq$pins.overlayManifestSha256) 'CONTROL_DOCUMENT_HASH'
Require ([IO.Path]::IsPathRooted($pins.bridgeRoot)-and[IO.Path]::GetFullPath($pins.bridgeRoot)-ceq$pins.bridgeRoot) 'BRIDGE_ROOT'
Assert-VerifierNoReparseAncestors $pins.bridgeRoot
foreach($name in @('CirSim.java','ChipElm.java','PhysicalSpecificationDeveloperVerifier.java')){Require-Pinned (Join-Path $pins.bridgeRoot ('bridge-'+$name))}
foreach($profile in @('BASE','RAMP')){
 foreach($role in @('router','support','canary')){Require-Pinned ([string]$pins.modeFiles.$profile.$role)}
}
Require ([IO.Path]::IsPathRooted($OutputRoot)) 'OUTPUT_ABSOLUTE_REQUIRED'
$OutputRoot=[IO.Path]::GetFullPath($OutputRoot)
$temp=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')+'\'
Require ($OutputRoot.StartsWith($temp,[StringComparison]::OrdinalIgnoreCase)-and-not$OutputRoot.StartsWith($RepositoryRoot.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) 'EXTERNAL_OUTPUT_REQUIRED'
Assert-VerifierNoReparseAncestors ([IO.Path]::GetDirectoryName($OutputRoot))
Require (-not(Test-Path -LiteralPath $OutputRoot)) 'OUTPUT_EXISTS'
[void][IO.Directory]::CreateDirectory($OutputRoot)
$scratch=Join-Path ([IO.Path]::GetTempPath()) ('tsj-via-ramp-native-'+[Guid]::NewGuid().ToString('N'))
Assert-VerifierNoReparseAncestors $scratch
Require (-not(Test-Path -LiteralPath $scratch)) 'SCRATCH_EXISTS'
$scratchTicks=$null
$runId=[Guid]::NewGuid().ToString('D')
$phase='SETUP';$native=$null;$compiler=$null;$scratchRemoved=$false
try{
Save 'scratch-intent.json' ([ordered]@{status='INTENT_NOT_CREATION_PROOF';scratch=$scratch;hostPid=$PID;hostStartTicks=[string]$hostProcess.StartTime.ToUniversalTime().Ticks;executable=$hostProcess.Path;runId=$runId})
[void][IO.Directory]::CreateDirectory($scratch)
$scratchTicks=(Get-Item -LiteralPath $scratch).CreationTimeUtc.Ticks
Save 'host-launch.json' ([ordered]@{
 schema='tsj-prefix-via-ramp-host-v1';status='LAUNCHED_NOT_ACCEPTED';mode=$Mode;stage=$Stage;runId=$runId;
 pid=$PID;startTicks=[string]$hostProcess.StartTime.ToUniversalTime().Ticks;executable=$hostProcess.Path;
 script=$PSCommandPath;scriptSha256=(Hash $PSCommandPath);inputPinsSha256=$InputPinsSha256;
 scratch=$scratch;scratchCreatedTicks=[string]$scratchTicks;outputRoot=$OutputRoot;
 compileLimitMs=60000;nativeLimitMs=480000;cleanupLimitMs=120000;outerLimitMs=1800000
})
 $phase='SOURCE_PREFLIGHT'
 $auditArgs=@('-B',$pins.preflight,$RepositoryRoot,$BaseManifestPath,$BaseManifestSha256,$InputPinsPath,$InputPinsSha256)
 $pre=Invoke-VerifierBoundedProcess $PythonExe $auditArgs 60000
 Save 'preflight-process.json' $pre
 Require ($pre.ExitCode-eq0-and$pre.TerminationProven-and[String]::IsNullOrEmpty($pre.Stderr)) 'PREFLIGHT_FAILED'
 Require ((ConvertFrom-Json -InputObject $pre.Stdout).status-ceq'PASS_PRIVATE_OVERLAY_INPUT_CLOSURE_ONLY') 'PREFLIGHT_MARKER'
 $phase='COMPILE'
 $classes=Join-Path $scratch 'classes';$empty=Join-Path $scratch 'empty-sourcepath'
 [void][IO.Directory]::CreateDirectory($classes);[void][IO.Directory]::CreateDirectory($empty)
 $manifest=Get-Content -LiteralPath $BaseManifestPath -Raw -Encoding UTF8|ConvertFrom-Json
 $paths=@()
 $excluded=@('src/com/lushprojects/circuitjs1/client/CirSim.java','src/com/lushprojects/circuitjs1/client/ChipElm.java','src/com/lushprojects/circuitjs1/client/PhysicalSpecificationDeveloperVerifier.java','src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java')
 foreach($row in $manifest.files){
  if($row.role-ceq'BASE_SOURCE'-and$row.path.EndsWith('.java',[StringComparison]::Ordinal)-and$row.path-notin$excluded){$paths+=@(Join-Path $RepositoryRoot $row.path)}
 }
 $selected=$pins.modeFiles.$Mode
 foreach($name in @('router','support','canary')){$paths+=@([string]$selected.$name)}
 Require ((Hash $pins.bridgeManifest)-ceq'e31649f0ea2a5740c998c4859bdc595b2ca75651e689ff56b1d146e721b3cd8b') 'BRIDGE_MANIFEST_PIN'
 $bridgeCopies=@()
 $bridges=Get-Content -LiteralPath $pins.bridgeManifest -Raw -Encoding UTF8|ConvertFrom-Json
 Require (@($bridges).Count-eq3) 'BRIDGE_CENSUS'
 foreach($b in $bridges){
  Require ($b.path-in@('CirSim.java','ChipElm.java','PhysicalSpecificationDeveloperVerifier.java')) 'BRIDGE_NAME'
  $from=Join-Path $pins.bridgeRoot ('bridge-'+$b.path)
  Require ((Get-Item -LiteralPath $from).Length-eq$b.bytes-and(Hash $from)-ceq$b.sha256) 'BRIDGE_HASH'
  $to=Join-Path $scratch $b.path
  [IO.File]::Copy($from,$to,$false)
  Require ((Get-Item -LiteralPath $to).Length-eq$b.bytes-and(Hash $to)-ceq$b.sha256) 'COPIED_BRIDGE_HASH'
  $bridgeCopies+=@(@{path=$to;bytes=$b.bytes;sha256=$b.sha256})
  $paths+=@($to)
 }
 Save 'consumed-bridge-copies.json' $bridgeCopies
 Require ($paths.Count-eq1076) 'COMPILE_CENSUS'
 $argsFile=Join-Path $scratch 'sources.txt'
 [IO.File]::WriteAllLines($argsFile,@($paths|ForEach-Object{'"'+$_.Replace('\','/')+'"'}),$utf8)
 [IO.File]::Copy($argsFile,(Join-Path $OutputRoot 'compiler-sources.txt'),$false)
 $cp=[string]::Join(';',@($classes,(Join-Path $RepositoryRoot '.tools/gwt-2.7.0/gwt-user-2.7.0.jar'),(Join-Path $RepositoryRoot '.tools/gwt-2.7.0/gwt-dev-2.7.0.jar')))
 $compilerArgs=@('-source','7','-target','7','-encoding','UTF-8','-classpath',$cp,'-sourcepath',$empty,'-d',$classes,('@'+$argsFile))
 Save 'compiler-launch-intent.json' ([ordered]@{status='INTENT_NOT_START_PROOF';executable=$javac;sha256=(Hash $javac);arguments=$compilerArgs;sourceListSha256=(Hash $argsFile);sourceListPath=$argsFile;limitMs=60000})
 $compiler=Invoke-VerifierBoundedProcess $javac $compilerArgs 60000
 Save 'compiler-process.json' $compiler
 Require ($compiler.ExitCode-eq0-and$compiler.TerminationProven) 'COMPILE_FAILED'
 Require ((Hash $argsFile)-ceq(Hash (Join-Path $OutputRoot 'compiler-sources.txt'))) 'COMPILER_SOURCE_LIST_CHANGED'
 $preNative=Invoke-VerifierBoundedProcess $PythonExe $auditArgs 60000
 Save 'pre-native-process.json' $preNative
 Require ($preNative.ExitCode-eq0-and$preNative.TerminationProven-and[String]::IsNullOrEmpty($preNative.Stderr)) 'PRE_NATIVE_SOURCE_FAILED'
 foreach($b in $bridgeCopies){Require ((Get-Item -LiteralPath $b.path).Length-eq$b.bytes-and(Hash $b.path)-ceq$b.sha256) 'PRE_NATIVE_COPIED_BRIDGE_HASH'}
 $phase='NATIVE_CAPTURE_LIMIT_NEGATIVE'
 Save 'native-capture-negative-intent.json' ([ordered]@{status='INTENT_NOT_START_PROOF';executable=$java;sha256=(Hash $java);arguments=@('-ea','-cp',$cp,'com.lushprojects.circuitjs1.client.PrefixViaRampCanary','--capture-overflow');limitMs=15000})
 $captureNegativeWatch=[Diagnostics.Stopwatch]::StartNew()
 $captureNegative=Invoke-VerifierBoundedProcess $java @('-ea','-cp',$cp,'com.lushprojects.circuitjs1.client.PrefixViaRampCanary','--capture-overflow') 15000
 $captureNegativeWatch.Stop()
 Save 'native-capture-limit-negative.json' $captureNegative
 Require ($captureNegative.ExitCode-ne0-and$captureNegative.TerminationProven-and[String]::IsNullOrEmpty($captureNegative.Stdout)-and$captureNegative.Stderr.Contains('PREFIX_VIA_RAMP_CAPTURE_LIMIT')) 'CAPTURE_LIMIT_NEGATIVE_FAILED'
 Require ($utf8.GetByteCount($captureNegative.Stderr)-le8388608) 'NEGATIVE_CAPTURE_BYTES'
 $phase='NATIVE'
 $nativeArgs=@('-ea','-cp',$cp)
 $nativeArgs+=@('com.lushprojects.circuitjs1.client.PrefixViaRampCanary')
 Save 'native-launch-intent.json' ([ordered]@{status='INTENT_NOT_START_PROOF';arguments=$nativeArgs;executable=$java;sha256=(Hash $java);limitMs=90000})
 $nativeWatch=[Diagnostics.Stopwatch]::StartNew()
 $native=Invoke-VerifierBoundedProcess $java $nativeArgs 90000
 Require (($utf8.GetByteCount($native.Stdout)+$utf8.GetByteCount($native.Stderr))-le8388608) 'NATIVE_CAPTURE_BYTES'
 Save 'native-process.json' $native
 Require ($native.ExitCode-eq0-and$native.TerminationProven) 'NATIVE_FAILED'
 Require ($native.Stdout-match'(?m)^PASS_PREFIX_VIA_RAMP_CANARY\b') 'CANARY_MARKER'
 $nativeWatch.Stop()
 Require ($nativeWatch.Elapsed.TotalMilliseconds-le90000) 'CANARY_WORK_LIMIT'
 $phase='POST_SOURCE_AND_CLEANUP'
 $cleanupWatch=[Diagnostics.Stopwatch]::StartNew()
 $post=Invoke-VerifierBoundedProcess $PythonExe $auditArgs 60000
 Save 'post-source-process.json' $post
 Require ($post.ExitCode-eq0-and$post.TerminationProven-and[String]::IsNullOrEmpty($post.Stderr)) 'POST_SOURCE_FAILED'
 Require ((Hash $argsFile)-ceq(Hash (Join-Path $OutputRoot 'compiler-sources.txt'))) 'POST_COMPILER_SOURCE_LIST_CHANGED'
 foreach($b in $bridgeCopies){Require ((Get-Item -LiteralPath $b.path).Length-eq$b.bytes-and(Hash $b.path)-ceq$b.sha256) 'POST_NATIVE_COPIED_BRIDGE_HASH'}
 Require ((Get-Item -LiteralPath $scratch).CreationTimeUtc.Ticks-eq$scratchTicks) 'SCRATCH_IDENTITY_CHANGED'
 Remove-VerifierOwnedTree ([IO.Path]::GetTempPath()) $scratch
 $scratchRemoved=-not(Test-Path -LiteralPath $scratch);Require $scratchRemoved 'SCRATCH_CLEANUP_UNPROVEN'
 $cleanupWatch.Stop()
 $cleanupUpperMs=$nativeWatch.Elapsed.TotalMilliseconds+$captureNegativeWatch.Elapsed.TotalMilliseconds+$cleanupWatch.Elapsed.TotalMilliseconds
 Require ($cleanupUpperMs-le120000-and$outer.Elapsed.TotalMilliseconds-le1800000) 'CLEANUP_OR_OUTER_LIMIT'
 Save 'host-result.json' ([ordered]@{
 schema='tsj-prefix-via-ramp-host-result-v1';status='HOST_COMPLETE_READER_PENDING';
 mode=$Mode;stage=$Stage;runId=$runId;inputPinsSha256=$InputPinsSha256;
 sourceClosure='PASS';compilerProcessId=$compiler.ProcessId;compilerProcessStartTicks=[string]$compiler.ProcessStartTicks;compilerExitCode=$compiler.ExitCode;compilerTerminationProven=$compiler.TerminationProven;
 nativeExitCode=$native.ExitCode;nativeTerminationProven=$native.TerminationProven;
 nativeProcessId=$native.ProcessId;nativeProcessStartTicks=[string]$native.ProcessStartTicks;
 nativeCaptureLimitNegative='PASS';nativeOutputBytes=($utf8.GetByteCount($native.Stdout)+$utf8.GetByteCount($native.Stderr));captureNegativeWallMs=$captureNegativeWatch.Elapsed.TotalMilliseconds;
 nativeWallMs=$nativeWatch.Elapsed.TotalMilliseconds;postSourceAndScratchCleanupMs=$cleanupWatch.Elapsed.TotalMilliseconds;
 cleanupConservativeUpperMs=$cleanupUpperMs;cleanupBoundMs=120000;scratchRemoved=$scratchRemoved;
 outerElapsedMs=$outer.Elapsed.TotalMilliseconds;compilerSources=$paths.Count
 })
 Write-Output ('PREFIX_VIA_RAMP_HOST_COMPLETE '+$Mode+' '+$Stage)
}catch{
 $hostFailure=$_
 if($phase-ceq'SETUP'-and$null-ne$scratchTicks-and(Test-Path -LiteralPath $scratch)){
  try{
   Require ((Get-Item -LiteralPath $scratch).CreationTimeUtc.Ticks-eq$scratchTicks) 'SETUP_SCRATCH_IDENTITY_CHANGED'
   Remove-VerifierOwnedTree ([IO.Path]::GetTempPath()) $scratch
   $scratchRemoved=-not(Test-Path -LiteralPath $scratch)
  }catch{[Console]::Error.WriteLine('SETUP_SCRATCH_CLEANUP_FAILED '+$scratch+' creationTicks='+[string]$scratchTicks+' '+$_.Exception.Message)}
 }
 Save 'host-failure.json' ([ordered]@{schema='tsj-prefix-via-ramp-host-failure-v1';status='FAIL';phase=$phase;message=$hostFailure.Exception.Message;mode=$Mode;stage=$Stage;scratch=$scratch;scratchCreatedTicks=[string]$scratchTicks;scratchRemoved=$scratchRemoved;outerElapsedMs=$outer.Elapsed.TotalMilliseconds;native=$native;compiler=$compiler})
 throw $hostFailure
}

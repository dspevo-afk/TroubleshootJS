param(
    [Parameter(Mandatory=$true)][ValidateSet('TINY_CANARY','AUTHORED')][string]$Mode,
    [Parameter(Mandatory=$true)][string]$SourceManifestPath,
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{64}$')][string]$SourceManifestSha256,
    [Parameter(Mandatory=$true)][string]$OutputRoot,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$PythonExe,
    [ValidatePattern('^$|^[0-9a-f]{64}$')][string]$ExpectedBridgeManifestSha256=''
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$module=Join-Path $PSScriptRoot 'VerifierIsolation.psm1'
$reader=Join-Path $repo 'tests/qualification/rb100-layout/branch-tree-study/read_branch_order_study.py'
$wire=Join-Path $repo 'docs/task-evidence/Q100/outf-graph-r1/WIRE.md'
$java=Join-Path $JavaHome 'bin/java.exe'
$javac=Join-Path $JavaHome 'bin/javac.exe'
function Hash([string]$Path){(Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Require([bool]$Condition,[string]$Message){if(-not $Condition){throw $Message}}
function Write-NewJson([string]$Path,$Value){
    $bytes=$utf8.GetBytes((ConvertTo-Json -InputObject $Value -Depth 20)+"`n")
    $s=[IO.File]::Open($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try{$s.Write($bytes,0,$bytes.Length);$s.Flush()}finally{$s.Dispose()}
}
function Ref([string]$Path){[ordered]@{path=[IO.Path]::GetFileName($Path);bytes=(Get-Item -LiteralPath $Path).Length;sha256=(Hash $Path)}}
function Nanos($Watch){[long][Math]::Ceiling($Watch.Elapsed.TotalMilliseconds*1000000.0)}
foreach($p in @($SourceManifestPath,$OutputRoot,$JavaHome,$PythonExe)){
    Require ([IO.Path]::IsPathRooted($p)) 'ABSOLUTE_CALLER_PATH_REQUIRED'
}
if($Mode-ceq'AUTHORED'){Require ($ExpectedBridgeManifestSha256.Length-eq64) 'AUTHORED_REQUIRES_FROZEN_BRIDGES'}
Require ($PSVersionTable.PSVersion.Major-eq5-and$PSVersionTable.PSVersion.Minor-eq1) 'SELECTED_PS51_REQUIRED'
$self=Get-Process -Id $PID
$psSha=Hash $self.Path
Require ($psSha-ceq'60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f') 'SELECTED_PS51_PIN'
Require ((Hash $module)-ceq'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c') 'ISOLATION_PIN'
Import-Module $module -Force -DisableNameChecking
foreach($p in @($repo,$SourceManifestPath,$PythonExe,$java,$javac,$reader,$wire)){Assert-VerifierNoReparseAncestors $p}
Require ((Hash $PythonExe)-ceq'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec') 'SELECTED_PYTHON_PIN'
Require ((Hash $java)-ceq'e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce') 'SELECTED_JAVA_PIN'
Require ((Hash $javac)-ceq'61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64') 'SELECTED_JAVAC_PIN'
Require ((Hash $reader)-ceq'a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e') 'READER_PIN'
Require ((Hash $wire)-ceq'0bd76a4fe4261a8abe5c92b2b0b5f50000077e9541e7ca7e84e9af3ed1661cfb') 'WIRE_PIN'
Require ((Get-Item -LiteralPath $SourceManifestPath).Length-le4194304) 'SOURCE_MANIFEST_SIZE'
Require ((Hash $SourceManifestPath)-ceq$SourceManifestSha256) 'SOURCE_MANIFEST_PIN'
$outer=[Diagnostics.Stopwatch]::StartNew()
$runId=[Guid]::NewGuid().ToString('D')
$OutputRoot=[IO.Path]::GetFullPath($OutputRoot)
$temp=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')+'\'
Require ($OutputRoot.StartsWith($temp,[StringComparison]::OrdinalIgnoreCase)-and-not$OutputRoot.StartsWith($repo.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) 'OUTPUT_MUST_BE_EXTERNAL_OS_TEMP'
Assert-VerifierNoReparseAncestors ([IO.Path]::GetDirectoryName($OutputRoot))
Require (-not(Test-Path -LiteralPath $OutputRoot)) 'OUTPUT_EXISTS'
[void][IO.Directory]::CreateDirectory($OutputRoot)
$scratch=Join-Path ([IO.Path]::GetTempPath()) ('tsj-branch-order-native-'+[Guid]::NewGuid().ToString('N'))
Assert-VerifierNoReparseAncestors $scratch
Require (-not(Test-Path -LiteralPath $scratch)) 'SCRATCH_EXISTS'
[void][IO.Directory]::CreateDirectory($scratch)
$scratchCreated=(Get-Item -LiteralPath $scratch).CreationTimeUtc.Ticks
$data=Join-Path $OutputRoot 'data'
[void][IO.Directory]::CreateDirectory($data)
$phase='SOURCE_PREFLIGHT'
$kind=if($Mode-ceq'AUTHORED'){'AUTHORED'}else{'TINY_NATIVE'}
Write-NewJson (Join-Path $OutputRoot 'host-launch.json') ([ordered]@{
    schema='tsj-branch-order-host-launch-v1';runId=$runId;mode=$Mode;sourceManifestSha256=$SourceManifestSha256;
    pid=$PID;startTicks=[string]$self.StartTime.ToUniversalTime().Ticks;executable=$self.Path;
    script=$PSCommandPath;scriptSha256=(Hash $PSCommandPath);outputRoot=$OutputRoot;
    scratch=$scratch;scratchCreatedTicks=[string]$scratchCreated;outerLimitMs=1800000;
    compileLimitMs=60000;nativeLimitMs=480000;cleanupLimitMs=120000})
# This exact HOST-owned adapter delegates strict JSON, role/path/census and raw
# source checks to the accepted independent reader before any source body enters
# the compiler. It executes no plan, corpus selector or native fixture.
$preflight=@'
import hashlib, json, pathlib, sys
reader_path=pathlib.Path(sys.argv[1]); manifest_path=pathlib.Path(sys.argv[2])
assert hashlib.sha256(reader_path.read_bytes()).hexdigest()=='a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e'
sys.path.insert(0,str(reader_path.parent))
import read_branch_order_study as r
raw=r.stable_read(manifest_path,4*1024*1024)
r.need(r.sha(raw)==sys.argv[3],'SOURCE_MANIFEST_PIN')
m,w,pins=r.check_source_manifest(raw,manifest_path.parent)
r.need(m['kind']==sys.argv[4] and pathlib.Path(m['repositoryRoot']).resolve()==reader_path.resolve().parents[4],'HOST_SOURCE_IDENTITY')
r.need(len([x for x in m['files'] if x['role']=='WRITER'])==1,'HOST_ONE_WRITER')
for ref,root,maximum in pins:r.file_ref(ref,root,maximum)
r.need(r.stable_read(manifest_path,4*1024*1024)==raw,'HOST_MANIFEST_CHANGED')
print(json.dumps({'status':'PASS_SOURCE_PREFLIGHT_ONLY','files':len(m['files']),'kind':m['kind'],'wireSha256':w},sort_keys=True))
'@
$preflightPath=Join-Path $scratch 'source-preflight.py'
[IO.File]::WriteAllText($preflightPath,$preflight,$utf8)
$native=$null
$nativeWatch=$null
$nativeLaunchAttempted=$false
$scratchRemoved=$false
$priorDirectory=[Environment]::CurrentDirectory
[Environment]::CurrentDirectory=$repo
try{
    $pre=Invoke-VerifierBoundedProcess $PythonExe @('-B',$preflightPath,$reader,$SourceManifestPath,$SourceManifestSha256,$kind) 60000
    Write-NewJson (Join-Path $OutputRoot 'source-preflight-process.json') $pre
    Require ($pre.ExitCode-eq0-and$pre.TerminationProven-and[String]::IsNullOrEmpty($pre.Stderr)) 'SOURCE_PREFLIGHT_FAILED'
    $preResult=ConvertFrom-Json -InputObject $pre.Stdout
    Require ($preResult.status-ceq'PASS_SOURCE_PREFLIGHT_ONLY'-and$preResult.kind-ceq$kind) 'SOURCE_PREFLIGHT_MARKER'
    $manifest=Get-Content -LiteralPath $SourceManifestPath -Raw -Encoding UTF8|ConvertFrom-Json
    Require ((Hash $SourceManifestPath)-ceq$SourceManifestSha256) 'SOURCE_MANIFEST_CHANGED'
    $phase='TOOLCHAIN'
    foreach($tool in @(@{name='java';exe=$java},@{name='javac';exe=$javac})){
        $v=Invoke-VerifierBoundedProcess $tool.exe @('-version') 15000
        Write-NewJson (Join-Path $OutputRoot ($tool.name+'-version-process.json')) $v
        Require ($v.ExitCode-eq0-and$v.TerminationProven-and($v.Stdout+$v.Stderr)-match'1\.8\.') 'JDK8_VERSION_FAILED'
    }
    $phase='COMPILE'
    $classes=Join-Path $scratch 'classes';$empty=Join-Path $scratch 'empty-sourcepath'
    [void][IO.Directory]::CreateDirectory($classes);[void][IO.Directory]::CreateDirectory($empty)
    $client=Join-Path $repo 'src/com/lushprojects/circuitjs1/client'
    $logger=[IO.File]::ReadAllText((Join-Path $client 'CirSim.java'),$utf8)
    $pattern='public static native void console\(String text\)\s*/\*\-\{\s*console\.log\(text\);\s*\}\-\*/;'
    $rx=[regex]::new($pattern)
    Require ($rx.Matches($logger).Count-eq1) 'NATIVE_LOGGER_SEAM'
    $logger=$rx.Replace($logger,'public static void console(String text) { System.err.println(text); }',1)
    $factory='public static CircuitElm constructElement(String n, int x1, int y1){'
    Require ($logger.Split(@($factory),[StringSplitOptions]::None).Count-eq2) 'NATIVE_FACTORY_SEAM'
    $logger=$logger.Replace($factory,$factory+' n = (n == null ? null : n.intern());')
    $chip=[IO.File]::ReadAllText((Join-Path $client 'ChipElm.java'),$utf8)
    $grid='setSize(sim.smallGridCheckItem.getState() ? 1 : 2);'
    Require ($chip.Split(@($grid),[StringSplitOptions]::None).Count-eq2) 'NATIVE_GRID_SEAM'
    $chip=$chip.Replace($grid,'setSize(2);')
    $stub=@'
package com.lushprojects.circuitjs1.client;
final class PhysicalSpecificationDeveloperVerifier {
    static void verify(CirSim sim) {
        throw new UnsupportedOperationException(
            "Task35 verifier requires the separate GWT production gate");
    }
}
'@
    $bridges=@(@{name='CirSim.java';text=$logger},@{name='ChipElm.java';text=$chip},@{name='PhysicalSpecificationDeveloperVerifier.java';text=$stub})
    $bridgePins=@()
    foreach($b in $bridges){
        $p=Join-Path $scratch $b.name;[IO.File]::WriteAllText($p,$b.text,$utf8)
        # Preserve bridge source bytes before compilation and scratch deletion.
        [IO.File]::Copy($p,(Join-Path $OutputRoot ('bridge-'+$b.name)),$false)
        $bridgePins+=@(Ref $p)
    }
    Write-NewJson (Join-Path $OutputRoot 'scratch-bridge-inputs.json') $bridgePins
    if($ExpectedBridgeManifestSha256.Length){
        Require ((Hash (Join-Path $OutputRoot 'scratch-bridge-inputs.json'))-ceq$ExpectedBridgeManifestSha256) 'FROZEN_BRIDGE_MANIFEST_MISMATCH'
    }
    $paths=@()
    foreach($row in $manifest.files){
        if($row.role-ceq'BASE_SOURCE'-and$row.path.EndsWith('.java',[StringComparison]::Ordinal)){
            if($row.path-notin@('src/com/lushprojects/circuitjs1/client/CirSim.java','src/com/lushprojects/circuitjs1/client/ChipElm.java','src/com/lushprojects/circuitjs1/client/PhysicalSpecificationDeveloperVerifier.java')){
                $paths+=@(Join-Path $repo $row.path)
            }
        }elseif($row.role-ceq'WRITER'){$paths+=@(Join-Path $repo $row.path)}
    }
    foreach($b in $bridges){$paths+=@(Join-Path $scratch $b.name)}
    Require ($paths.Count-eq1075) 'COMPILER_SOURCE_CENSUS'
    $argsPath=Join-Path $scratch 'sources.txt'
    [IO.File]::WriteAllLines($argsPath,@($paths|ForEach-Object{'"'+$_.Replace('\','/')+'"'}),$utf8)
    [IO.File]::Copy($argsPath,(Join-Path $OutputRoot 'compiler-sources.txt'),$false)
    $cp=[String]::Join(';',@($classes,(Join-Path $repo '.tools/gwt-2.7.0/gwt-user-2.7.0.jar'),(Join-Path $repo '.tools/gwt-2.7.0/gwt-dev-2.7.0.jar')))
    $compiler=Invoke-VerifierBoundedProcess $javac @('-source','7','-target','7','-encoding','UTF-8','-classpath',$cp,'-sourcepath',$empty,'-d',$classes,('@'+$argsPath)) 60000
    Write-NewJson (Join-Path $OutputRoot 'compiler-process.json') $compiler
    Require ($compiler.ExitCode-eq0-and$compiler.TerminationProven) 'COMPILATION_FAILED'
    $phase='PRE_LAUNCH_SOURCE_AUDIT'
    $beforeNative=Invoke-VerifierBoundedProcess $PythonExe @('-B',$preflightPath,$reader,$SourceManifestPath,$SourceManifestSha256,$kind) 60000
    Write-NewJson (Join-Path $OutputRoot 'pre-launch-source-process.json') $beforeNative
    Require ($beforeNative.ExitCode-eq0-and$beforeNative.TerminationProven-and[String]::IsNullOrEmpty($beforeNative.Stderr)) 'PRE_LAUNCH_SOURCE_AUDIT_FAILED'
    foreach($b in $bridgePins){Require ((Hash (Join-Path $scratch $b.path))-ceq$b.sha256) 'PRE_LAUNCH_BRIDGE_CHANGED'}
    $phase='NATIVE'
    $nativeWatch=[Diagnostics.Stopwatch]::StartNew()
    $nativeArguments=@('-ea','-cp',$cp,'com.lushprojects.circuitjs1.client.ActualGeneratorBranchOrderStudy',$Mode,$runId,$SourceManifestSha256,(Hash $wire),$data)
    Write-NewJson (Join-Path $OutputRoot 'native-launch-intent.json') ([ordered]@{
        status='LAUNCH_INTENT_NOT_PROCESS_START_PROOF';executable=$java;executableSha256=(Hash $java);
        arguments=$nativeArguments;limitMs=480000;runId=$runId;sourceManifestSha256=$SourceManifestSha256})
    $nativeLaunchAttempted=$true
    $native=Invoke-VerifierBoundedProcess $java $nativeArguments 480000
    Write-NewJson (Join-Path $OutputRoot 'native-process.json') $native
    Require ($native.ExitCode-eq0-and$native.TerminationProven) 'NATIVE_EXECUTION_FAILED'
    Require ($native.Stdout-match'^BRANCH_ORDER_STUDY_COMPLETE\b') 'NATIVE_COMPLETE_MARKER'
    $phase='POST_EXIT_SOURCE_AUDIT'
    $post=Invoke-VerifierBoundedProcess $PythonExe @('-B',$preflightPath,$reader,$SourceManifestPath,$SourceManifestSha256,$kind) 60000
    Write-NewJson (Join-Path $OutputRoot 'post-source-process.json') $post
    Require ($post.ExitCode-eq0-and$post.TerminationProven-and[String]::IsNullOrEmpty($post.Stderr)) 'POST_SOURCE_AUDIT_FAILED'
    foreach($b in $bridgePins){Require ((Hash (Join-Path $scratch $b.path))-ceq$b.sha256) 'SCRATCH_BRIDGE_CHANGED'}
    $indexPath=Join-Path $data 'study-index.json'
    Assert-VerifierNoReparseAncestors $indexPath
    Require ((Get-Item -LiteralPath $indexPath).Length-le65536) 'INDEX_SIZE'
    $index=Get-Content -LiteralPath $indexPath -Raw -Encoding UTF8|ConvertFrom-Json
    Require (@($index.operations).Count-eq5-and$index.kind-ceq$kind-and$index.runId-ceq$runId) 'INDEX_IDENTITY'
    [long]$armNanos=0;[long]$armCleanup=0
    foreach($op in $index.operations){$armNanos+=[long]$op.elapsedToCloseNanos;$armCleanup+=[long]$op.cleanup.elapsedNanos}
    $phase='SCRATCH_CLEANUP'
    Require ((Get-Item -LiteralPath $scratch).CreationTimeUtc.Ticks-eq$scratchCreated) 'SCRATCH_IDENTITY_CHANGED'
    Remove-VerifierOwnedTree ([IO.Path]::GetTempPath()) $scratch
    $scratchRemoved=-not(Test-Path -LiteralPath $scratch)
    Require $scratchRemoved 'SCRATCH_CLEANUP_UNPROVEN'
    $nativeWatch.Stop()
    [long]$nativeNanos=Nanos $nativeWatch
    # Conservatively charge startup,post-exit checks and scratch removal to the
    # same cleanup envelope. The actual arm intervals come from the Java index.
    [long]$cleanupNanos=$nativeNanos-$armNanos
    Require ($nativeNanos-le480000000000-and$cleanupNanos-ge$armCleanup-and$cleanupNanos-le120000000000) 'NATIVE_OR_CLEANUP_ENVELOPE'
    $transportPath=Join-Path $OutputRoot 'transport.json'
    Write-NewJson $transportPath ([ordered]@{schema='tsj-branch-order-native-transport-v1';kind=$kind;
        sourceManifestSha256=$SourceManifestSha256;indexSha256=(Hash $indexPath);compileExitCode=$compiler.ExitCode;
        exitCode=$native.ExitCode;terminationProven=$native.TerminationProven;sourcesStable=$true;
        processId=$native.ProcessId;processStartTicks=[string]$native.ProcessStartTicks;
        executableSha256=(Hash $java);compilerExecutableSha256=(Hash $javac);powershellExecutableSha256=$psSha;
        isolationModuleSha256=(Hash $module);compilerTerminationProven=$compiler.TerminationProven;
        nativeElapsedNanos=$nativeNanos;cleanupElapsedNanos=$cleanupNanos;cleanupStatus='PASS'})
    $specPath=Join-Path $OutputRoot 'reader-input.json'
    Write-NewJson $specPath ([ordered]@{schema='tsj-branch-order-reader-input-v1';kind=$kind;
        sourceManifest=@{path=$SourceManifestPath;bytes=(Get-Item -LiteralPath $SourceManifestPath).Length;sha256=$SourceManifestSha256};
        studyIndex=@{path=$indexPath;bytes=(Get-Item -LiteralPath $indexPath).Length;sha256=(Hash $indexPath)};
        transport=(Ref $transportPath)})
    $phase='INDEPENDENT_READER'
    $read=Invoke-VerifierBoundedProcess $PythonExe @('-B',$reader,'--spec',$specPath,'--spec-sha256',(Hash $specPath),'--output',(Join-Path $OutputRoot 'reader-result.json')) 60000
    Write-NewJson (Join-Path $OutputRoot 'reader-process.json') $read
    Require ($read.ExitCode-eq0-and$read.TerminationProven-and[String]::IsNullOrEmpty($read.Stderr)) 'INDEPENDENT_READER_FAILED'
    $report=Get-Content -LiteralPath (Join-Path $OutputRoot 'reader-result.json') -Raw -Encoding UTF8|ConvertFrom-Json
    Require ($report.status-ceq'VALIDATED_NATIVE_REPORT_ONLY_ROOT_ACCEPTANCE_REQUIRED') 'READER_ACCEPTANCE_BOUNDARY'
    Require ($outer.Elapsed.TotalMilliseconds-le1800000) 'OUTER_LIMIT'
    Write-NewJson (Join-Path $OutputRoot 'host-result.json') ([ordered]@{status='REPORTED_NATIVE_STUDY_ROOT_ACCEPTANCE_REQUIRED';mode=$Mode;kind=$kind;runId=$runId;
        sourceManifestSha256=$SourceManifestSha256;nativeElapsedNanos=$nativeNanos;cleanupEnvelopeNanos=$cleanupNanos;
        cleanupEnvelope='CONSERVATIVE_NATIVE_MINUS_SUM_ARM_INTERVALS';scratchRemoved=$scratchRemoved;
        outerElapsedBeforeOwnReceiptNanos=(Nanos $outer);separateFinalReceiptCloseElapsed='NOT_EXPORTED';
        compiledProductionEvidence='FINAL_PRODUCTION_GATES_NOT_RUN_HERE';layoutQualification=$false;playable100=0;beta='HELD'})
    Write-Output 'REPORTED_NATIVE_STUDY_ROOT_ACCEPTANCE_REQUIRED'
}catch{
    $failure=$_
    # Retain failed scratch and all child output for diagnosis. Never claim a
    # timeout,exit2,unproven teardown or missing accounting as a passing study.
    if(-not(Test-Path -LiteralPath (Join-Path $OutputRoot 'host-result.json'))){
        Write-NewJson (Join-Path $OutputRoot 'host-result.json') ([ordered]@{status='FAIL';phase=$phase;
            error=[string]$failure;elapsedNanos=(Nanos $outer);scratchRemoved=$scratchRemoved;
            scratchRetained=(Test-Path -LiteralPath $scratch);nativeLaunchAttempted=$nativeLaunchAttempted;
            authoredStudy=$(if($Mode-ceq'AUTHORED'-and$nativeLaunchAttempted){'LAUNCH_REQUESTED_OR_STARTED_NOT_ACCEPTED'}else{'NOT_RUN'})})
    }
    throw $failure
}finally{
    [Environment]::CurrentDirectory=$priorDirectory
}

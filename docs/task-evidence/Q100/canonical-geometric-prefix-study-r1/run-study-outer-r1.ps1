param(
 [Parameter(Mandatory=$true)][string]$RepositoryRoot,
 [Parameter(Mandatory=$true)][string]$PythonExe,
 [Parameter(Mandatory=$true)][string]$JavaHome,
 [Parameter(Mandatory=$true)][string]$InputPinsPath,
 [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{64}$')][string]$InputPinsSha256,
 [Parameter(Mandatory=$true)][string]$OutputRoot,
 [Parameter(Mandatory=$true)][ValidateSet('CANARY','SUITE')][string]$Mode,
 [Parameter(Mandatory=$true)][string]$EvidenceStem
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$full=[Diagnostics.Stopwatch]::StartNew()
$utf8=[Text.UTF8Encoding]::new($false)
function Hash([string]$path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Need([bool]$ok,[string]$why){if(-not$ok){throw $why}}
function Save([string]$path,$value){
 $bytes=$utf8.GetBytes((ConvertTo-Json -InputObject $value -Depth 50)+[char]10)
 $stream=[IO.File]::Open($path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
 try{$stream.Write($bytes,0,$bytes.Length);$stream.Flush()}finally{$stream.Dispose()}
}
Need ($EvidenceStem-cmatch'^[a-z0-9-]+$'-and($Mode-ceq'CANARY'-or$Mode-ceq'SUITE')) 'EXACT_LAUNCH_ARGUMENTS'
$repo=[IO.Path]::GetFullPath($RepositoryRoot)
$python=[IO.Path]::GetFullPath($PythonExe)
$selectedJavaHome=[IO.Path]::GetFullPath($JavaHome)
$pinsPath=[IO.Path]::GetFullPath($InputPinsPath)
$target=[IO.Path]::GetFullPath($OutputRoot)
$controller=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'run-study-controls.ps1'))
$module=Join-Path $repo 'scripts/VerifierIsolation.psm1'
Need ((Hash $module)-ceq'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c') 'MODULE_PIN'
Import-Module $module -Force -DisableNameChecking
foreach($path in @($repo,$python,$selectedJavaHome,$pinsPath,$controller)){Assert-VerifierNoReparseAncestors $path}
Assert-VerifierNoReparseAncestors ([IO.Path]::GetDirectoryName($target))
Need (-not(Test-Path -LiteralPath $target)) 'OUTPUT_ALREADY_EXISTS'
Need ((Hash $pinsPath)-ceq$InputPinsSha256) 'INPUT_PINS_HASH'
$pins=Get-Content -LiteralPath $pinsPath -Raw -Encoding UTF8|ConvertFrom-Json
$row=@($pins.files|Where-Object {$_.path-ceq$controller})
Need ($row.Count-eq1-and$row[0].sha256-ceq(Hash $controller)-and$row[0].bytes-eq(Get-Item -LiteralPath $controller).Length) 'CONTROLLER_PIN'
$self=Get-Process -Id $PID
Need ((Hash $self.Path)-ceq'60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f') 'PS51_PIN'
$argsForController=@('-NoLogo','-NoProfile','-NonInteractive','-File',$controller,
 '-RepositoryRoot',$repo,'-PythonExe',$python,'-JavaHome',$selectedJavaHome,
 '-InputPinsPath',$pinsPath,'-InputPinsSha256',$InputPinsSha256,'-OutputRoot',$target,'-Mode',$Mode)
$ownerPath=Join-Path $PSScriptRoot ($EvidenceStem+'-owner.json')
$receiptPath=Join-Path $PSScriptRoot ($EvidenceStem+'-outer.json')
Need (-not(Test-Path -LiteralPath $ownerPath)-and-not(Test-Path -LiteralPath $receiptPath)) 'EVIDENCE_ALREADY_EXISTS'
Save $ownerPath ([ordered]@{schema='tsj-p8-prefix-owned-outer-v1';status='RUNNING_NOT_ACCEPTED';mode=$Mode;pid=$PID;startTicks=[string]$self.StartTime.ToUniversalTime().Ticks;executable=$self.Path;script=$PSCommandPath;scriptSha256=(Hash $PSCommandPath);controller=$controller;controllerSha256=$row[0].sha256;arguments=$argsForController;inputPinsSha256=$InputPinsSha256;outputRoot=$target;limitMs=1800000})
$watch=[Diagnostics.Stopwatch]::StartNew();$process=$null;$errorText=$null;$exceptionData=[ordered]@{}
try{$process=Invoke-VerifierBoundedProcess $self.Path $argsForController 1800000}
catch{
 $errorText=$_.Exception.ToString()
 foreach($key in @($_.Exception.Data.Keys)){$exceptionData[[string]$key]=[string]$_.Exception.Data[$key]}
}
$watch.Stop()
$same=(Hash $pinsPath)-ceq$InputPinsSha256-and(Hash $controller)-ceq$row[0].sha256
$expected=if($Mode-ceq'CANARY'){'PASS_PREFIX_CANARY_CONTROLS_ONLY'}else{'PASS_PREFIX_SUITE_CONTROLS_AND_REASON_OBSERVATIONS_ONLY'}
$pass=$null-ne$process-and$process.ExitCode-eq0-and$process.TerminationProven-and$watch.Elapsed.TotalMilliseconds-le1800000-and$same
if($null-ne$process){$pass=$pass-and$process.Stdout-ceq($expected+[char]13+[char]10)-and[String]::IsNullOrEmpty($process.Stderr)-and-not(Test-Path -LiteralPath $process.ProcessRoot)}
Save $receiptPath ([ordered]@{schema='tsj-p8-prefix-outer-v1';status=if($pass){'PASS_SELECTED_CONTROLLER_ONLY'}else{'FAIL'};mode=$Mode;elapsedMs=$watch.Elapsed.TotalMilliseconds;process=$process;error=$errorText;exceptionData=$exceptionData;attemptedExecutable=$self.Path;attemptedArguments=$argsForController;terminationProven=($null-ne$process-and$process.TerminationProven);inputsUnchanged=$same;inputPinsSha256=$InputPinsSha256;outputRoot=$target;layoutQualification=$false;canonicalBaseNotRun=($Mode-ceq'CANARY')})
Need $pass 'P8_PREFIX_CONTROLLER_FAILED'
Need ($full.Elapsed.TotalMilliseconds-le1800000) 'OUTER_FINAL_WALL_LIMIT'
Write-Output 'PASS_SELECTED_CONTROLLER_ONLY'
Need ($full.Elapsed.TotalMilliseconds-le1800000) 'OUTER_FINAL_OUTPUT_WALL_LIMIT'

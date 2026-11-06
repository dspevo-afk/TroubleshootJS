param(
 [Parameter(Mandatory=$true)][ValidateSet('CANARY','ANALYZE')][string]$Mode,
 [Parameter(Mandatory=$true)][string]$SpecPath,
 [Parameter(Mandatory=$true)][string]$SpecSha256,
 [Parameter(Mandatory=$true)][string]$OutputRoot,
 [Parameter(Mandatory=$true)][string]$RecordBase
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$encoding=[Text.UTF8Encoding]::new($false)
$SpecPath=[IO.Path]::GetFullPath($SpecPath)
$OutputRoot=[IO.Path]::GetFullPath($OutputRoot)
$RecordBase=[IO.Path]::GetFullPath($RecordBase)
function Save([string]$suffix,$value){
 $path=$RecordBase+$suffix
 $raw=$encoding.GetBytes((ConvertTo-Json -InputObject $value -Depth 30)+[char]10)
 $stream=[IO.File]::Open($path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
 try{$stream.Write($raw,0,$raw.Length)}finally{$stream.Dispose()}
}
$self=Get-Process -Id $PID
Save '-owner.json' ([ordered]@{pid=$PID;startTicks=[string]$self.StartTime.ToUniversalTime().Ticks;executable=$self.Path;script=$PSCommandPath;mode=$Mode;outputRoot=$OutputRoot})
$spec=Get-Content -LiteralPath $SpecPath -Raw -Encoding UTF8|ConvertFrom-Json
$module=Join-Path $spec.repositoryRoot 'scripts/VerifierIsolation.psm1'
if((Get-FileHash -LiteralPath $module -Algorithm SHA256).Hash.ToLowerInvariant()-cne'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c'){throw 'MODULE_HASH'}
Import-Module $module -Force -DisableNameChecking
$watch=[Diagnostics.Stopwatch]::StartNew()
$child=Invoke-VerifierBoundedProcess $spec.selectedPowerShell @('-NoProfile','-File',(Join-Path $PSScriptRoot 'run-cut-sensitivity.ps1'),'-Mode',$Mode,'-SpecPath',$SpecPath,'-SpecSha256',$SpecSha256,'-OutputRoot',$OutputRoot) 1800000
Save '-outer.json' ([ordered]@{wallMs=$watch.Elapsed.TotalMilliseconds;process=$child})
if($child.ExitCode-ne0-or-not$child.TerminationProven){throw 'SELECTED_HOST_FAILED'}
if($watch.Elapsed.TotalMilliseconds-gt120000){throw 'HOST_FULL_CLOSE_CLEANUP_LIMIT'}
$watch.Restart()
$reader=Invoke-VerifierBoundedProcess $spec.selectedPython @('-B',(Join-Path $PSScriptRoot 'read_cut_sensitivity.py'),$OutputRoot,$SpecPath,$SpecSha256,$Mode) 180000
Save '-reader-process.json' ([ordered]@{wallMs=$watch.Elapsed.TotalMilliseconds;process=$reader})
if($reader.ExitCode-ne0-or-not$reader.TerminationProven-or-not[String]::IsNullOrEmpty($reader.Stderr)){throw 'SELECTED_READER_FAILED'}
$reader.Stdout

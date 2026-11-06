param([Parameter(Mandatory=$true)][string]$SpecPath,[Parameter(Mandatory=$true)][string]$SpecSha256)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$SpecPath=[IO.Path]::GetFullPath($SpecPath)
$spec=Get-Content -LiteralPath $SpecPath -Raw|ConvertFrom-Json
$module=Join-Path $spec.repositoryRoot 'scripts/VerifierIsolation.psm1'
if((Get-FileHash -LiteralPath $module -Algorithm SHA256).Hash.ToLowerInvariant()-cne'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c'){throw 'MODULE_HASH'}
Import-Module $module -Force -DisableNameChecking
$watch=[Diagnostics.Stopwatch]::StartNew()
$process=Invoke-VerifierBoundedProcess $spec.selectedPython @('-B',(Join-Path $PSScriptRoot 'close_inputs.py'),$SpecPath,$SpecSha256,(Join-Path $PSScriptRoot 'final-source-close-r1.json')) 180000
$record=[ordered]@{wallMs=$watch.Elapsed.TotalMilliseconds;process=$process;sourceSha256=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'close_inputs.py') -Algorithm SHA256).Hash.ToLowerInvariant()}
$encoding=[Text.UTF8Encoding]::new($false)
$raw=$encoding.GetBytes((ConvertTo-Json $record -Depth 20 -Compress)+[char]10)
$stream=[IO.File]::Open((Join-Path $PSScriptRoot 'final-source-close-r1-process.json'),[IO.FileMode]::CreateNew)
try{$stream.Write($raw,0,$raw.Length)}finally{$stream.Dispose()}
if($process.ExitCode-ne0-or-not$process.TerminationProven-or-not[String]::IsNullOrEmpty($process.Stderr)){throw 'SOURCE_CLOSE_FAILED'}
$process.Stdout

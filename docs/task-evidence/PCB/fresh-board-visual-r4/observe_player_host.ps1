[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('before','after')][string]$Side,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
& $PythonExe -X utf8 -B (Join-Path $PSScriptRoot 'inputs.py') audit candidate *> (Join-Path $PSScriptRoot ('player-input-audit-'+$Side+'.txt'))
if($LASTEXITCODE -ne 0){throw 'Player source audit failed'}
Import-Module (Join-Path $root 'scripts/VerifierIsolation.psm1') -Force
$full=Get-VerifierExecutionTreeProvenance $root
$projection=[ordered]@{}
foreach($name in @('Protocol','SourceDigest','ScriptDigest','WebDigest','Digest','FileCount')){
    $projection[$name]=$full.$name
}
$reuse=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'reused-execution.json') -Raw|ConvertFrom-Json
foreach($name in @('Protocol','SourceDigest','ScriptDigest','WebDigest','Digest','FileCount')){
    if([string]$projection[$name] -cne [string]$reuse.$name){throw 'Independent compiled reuse boundary changed'}
}
$provenanceName=if($Side -eq 'before'){'player-expected-execution.json'}else{'post-player-provenance.json'}
$projection|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $PSScriptRoot $provenanceName)
$state=Get-Content -LiteralPath (Join-Path $root '.tools/preview/state.json') -Raw|ConvertFrom-Json
$script=(Resolve-Path -LiteralPath (Join-Path $root 'scripts/preview.ps1')).Path
if([IO.Path]::GetFullPath($state.repositoryRoot) -ine $root -or
   [IO.Path]::GetFullPath($state.previewScript) -ine $script -or [int]$state.port -ne 8907){
    throw 'Managed preview state belongs to another owner'
}
$process=Get-Process -Id $state.processId -ErrorAction Stop
$cim=Get-CimInstance Win32_Process -Filter ('ProcessId='+$state.processId)
if([string]$process.StartTime.ToUniversalTime().Ticks -ne [string]$state.processStartTicks -or
   $cim.CommandLine -cne $state.processCommandLine -or
   $cim.CommandLine.IndexOf($script,[StringComparison]::OrdinalIgnoreCase) -lt 0 -or
   @('powershell.exe','pwsh.exe') -notcontains [IO.Path]::GetFileName($cim.ExecutablePath)){
    throw 'Preview worker birth, executable or exact command changed'
}
$response=Invoke-WebRequest -Uri 'http://127.0.0.1:8907/circuitjs.html' -UseBasicParsing
if([int]$response.StatusCode -ne 200){throw 'Production preview unavailable'}
$identity=Invoke-RestMethod -Uri 'http://127.0.0.1:8907/__tsj/verify-identity'
if($identity.protocol -ne 'troubleshootjs-preview-identity-v1' -or
   [IO.Path]::GetFullPath($identity.repositoryRoot) -ine $root -or
   [IO.Path]::GetFullPath($identity.previewScript) -ine $script -or
   [int]$identity.processId -ne [int]$state.processId -or
   [string]$identity.processStartTicks -ne [string]$state.processStartTicks -or
   [int]$identity.previewPort -ne 8907){throw 'Live HTTP worker identity differs'}
$map=@{sourceDigest='SourceDigest';scriptDigest='ScriptDigest';webDigest='WebDigest';executionDigest='Digest';executionFileCount='FileCount'}
foreach($entry in $map.GetEnumerator()){
    if([string]$identity.($entry.Key) -cne [string]$projection[$entry.Value]){throw 'Live execution differs from independent CLI'}
}
@{status='PASS';side=$Side;pid=[int]$state.processId;startTicks=[string]$state.processStartTicks;
  executable=[IO.Path]::GetFileName($cim.ExecutablePath);port=8907;http200=$true;
  rootScriptCommandBirthExact=$true;servedIdentityEqualsIndependentProjection=$true;
  limits='Full local owner paths and command remain private; public projection contains six provenance fields only'}|
    ConvertTo-Json|Set-Content -LiteralPath (Join-Path $PSScriptRoot ('preview-owner-'+$Side+'.json'))
Write-Output ('PASS independent player source/provenance and actual preview owner '+$Side)

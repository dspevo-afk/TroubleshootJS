[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('before','after')][string]$Side)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$target=Join-Path $PSScriptRoot ('execution-'+$Side+'.json')
if(Test-Path -LiteralPath $target){throw 'Preserve existing execution projection'}
$root=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
Import-Module (Join-Path $root 'scripts/VerifierIsolation.psm1') -Force -DisableNameChecking
$full=Get-VerifierExecutionTreeProvenance $root
$expected=(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'baseline-inputs.json') -Raw|ConvertFrom-Json).expectedExecution
$projection=[ordered]@{}
foreach($name in @('Protocol','SourceDigest','ScriptDigest','WebDigest','Digest','FileCount')){
    if([string]$full.$name -cne [string]$expected.$name){throw ('Execution differs: '+$name)}
    $projection[$name]=$full.$name
}
$projection|ConvertTo-Json|Set-Content -LiteralPath $target
Write-Output ('PASS exact random diagnosis execution six fields unchanged '+$Side)

[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repo=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$plan=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'focused-plan.json') -Raw | ConvertFrom-Json
Push-Location $repo
try {
    & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite $plan.names -ReceiptOutputPath (Join-Path $PSScriptRoot 'focused-receipt.json')
    if(-not $?){exit 1}
} finally {Pop-Location}

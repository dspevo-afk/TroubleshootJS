[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$ReceiptOutputPath = ''
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Report contracts load pure functions only, with no preview/browser launch.
& (Join-Path $PSScriptRoot '../tests/contracts/a04-report-contract.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Reuse the existing JDK8 production-source compiler and owned scratch lifecycle.
# The closed A04 test set supplements, rather than replaces, A03/A02 contracts.
& (Join-Path $PSScriptRoot 'verify-a03-contracts.ps1') -JavaHome $JavaHome `
    -Contract All -A04 -ReceiptOutputPath $ReceiptOutputPath
exit $LASTEXITCODE

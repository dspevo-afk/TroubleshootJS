[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'suite_transport.ps1')
$results=[Collections.Generic.List[object]]::new()
$unit=@(Get-SuccessfulRoutePoolSuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-suites.json') -Raw))
if($unit.Count -ne 1 -or $unit[0] -cne 'PcbSuccessfulRoutePoolObserverContractTest'){throw 'Actual one-suite array boundary lost'}
$results.Add([ordered]@{case='Actual one-row nested manifest';result='PASS'})
$routes=@(Get-SuccessfulRoutePoolSuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'routes-suites.json') -Raw))
$expected=@('PcbSuccessfulRoutePoolSeed5ContractTest','PcbSuccessfulRoutePoolSeed2ContractTest')
if($routes.Count -ne 2){throw 'Actual two-row manifest count changed'}
for($i=0;$i -lt 2;$i++){if($routes[$i] -cne $expected[$i]){throw 'Actual suite ordering changed'}}
$results.Add([ordered]@{case='Actual two-row manifest in order';result='PASS'})
$negative=[ordered]@{
    'Flat pair'='["Name","marker "]'
    'Empty list'='[]'
    'Short pair'='[["Name"]]'
    'Long pair'='[["Name","marker ","extra"]]'
    'Malformed class name'='[["Bad.Name","marker "]]'
    'Boolean class name'='[[true,"marker "]]'
    'Boolean marker'='[["Name",true]]'
    'Duplicate class'='[["Name","one "],["Name","two "]]'
    'Duplicate marker'='[["First","same "],["Second","same "]]'
    'Multiline marker'='[["Name","line\nsecond"]]'
}
foreach($entry in $negative.GetEnumerator()){
    $rejected=$false
    try{$null=@(Get-SuccessfulRoutePoolSuiteNames -JsonText $entry.Value)}catch{$rejected=$true}
    if(-not $rejected){throw ('Invalid manifest accepted: '+$entry.Key)}
    $results.Add([ordered]@{case=$entry.Key;result='REJECTED'})
}
$pins=[ordered]@{}
foreach($name in @('suite_transport.ps1','check_suite_transport.ps1','unit-suites.json','routes-suites.json')){
    $pins[$name]=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $name) -Algorithm SHA256).Hash.ToLowerInvariant()
}
[ordered]@{status='PASS';scope='Real selected PowerShell suite transport only; no Java fixture proof';
    powerShellVersion=$PSVersionTable.PSVersion.ToString();caseCount=$results.Count;
    actualManifests=2;negativeCases=10;consumedSha256=$pins;cases=$results} |
    ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'suite-transport-canaries.json') -Encoding utf8
Write-Output ('PASS real PowerShell suite transport: '+$results.Count+' cases; one-row and two-row inputs exact')

[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'suite_transport.ps1')
$results=[Collections.Generic.List[object]]::new()
$unit=@(Get-RoutedSlideDescentSuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-suites.json') -Raw))
if($unit.Count -ne 4 -or $unit[0] -cne 'PcbRoutedPassiveLegCensusContractTest' -or $unit[1] -cne 'PcbRoutedPassiveFeasibilityCensusContractTest' -or  $unit[2] -cne 'PcbRoutedSlideCompactionContractTest' -or $unit[3] -cne 'PcbRoutedSlideDescentContractTest'){throw 'Unit four-row nested array/order lost'}
$results.Add([ordered]@{case='Actual unit four-row nested manifest';result='PASS'})
$routes=@(Get-RoutedSlideDescentSuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'routes-suites.json') -Raw))
if($routes.Count -ne 1 -or $routes[0] -cne 'PcbRoutedSlideDescentTrialContractTest'){throw 'Route single-row nested array lost'}
$results.Add([ordered]@{case='Actual route one-row nested manifest';result='PASS'})
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
    try{$null=@(Get-RoutedSlideDescentSuiteNames -JsonText $entry.Value)}catch{$rejected=$true}
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
Write-Output ('PASS real PowerShell suite transport: '+$results.Count+' cases; unit4/route1 nested inputs exact')

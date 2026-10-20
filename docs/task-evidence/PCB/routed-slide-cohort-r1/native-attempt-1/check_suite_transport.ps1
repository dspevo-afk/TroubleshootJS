[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'suite_transport.ps1')
$results=[Collections.Generic.List[object]]::new()
$unit=@(Get-RoutedSlideCohortSuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-suites.json') -Raw))
if($unit.Count -ne 5 -or $unit[0] -cne 'PcbRoutedPassiveLegCensusContractTest' -or $unit[1] -cne 'PcbRoutedPassiveFeasibilityCensusContractTest' -or  $unit[2] -cne 'PcbRoutedSlideCompactionContractTest' -or $unit[3] -cne 'PcbRoutedSlideDescentContractTest' -or $unit[4] -cne 'PcbRoutedSlideCohortContractTest'){throw 'Unit five-row nested array/order lost'}
$results.Add([ordered]@{case='Actual unit five-row nested manifest';result='PASS'})
$routes=@(Get-RoutedSlideCohortSuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'routes-suites.json') -Raw))
$expectedRoutes=@('PcbRoutedSlideCohortTrialContractTest','PcbRoutedSlideCohortTrialContractTestGroup2','PcbRoutedSlideCohortTrialContractTestGroup3','PcbRoutedSlideCohortTrialContractTestGroup4','PcbRoutedSlideCohortTrialContractTestGroup5','PcbRoutedSlideCohortTrialContractTestGroup6','PcbRoutedSlideCohortTrialContractTestGroup7')
if($routes.Count -ne 7){throw 'Route seven-row nested array lost'}
for($routeIndex=0;$routeIndex -lt 7;$routeIndex++){if($routes[$routeIndex] -cne $expectedRoutes[$routeIndex]){throw 'Route partition order changed'}}
$results.Add([ordered]@{case='Actual route seven-row nested manifest';result='PASS'})
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
    try{$null=@(Get-RoutedSlideCohortSuiteNames -JsonText $entry.Value)}catch{$rejected=$true}
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
Write-Output ('PASS real PowerShell suite transport: '+$results.Count+' cases; unit5/route7 nested inputs exact')

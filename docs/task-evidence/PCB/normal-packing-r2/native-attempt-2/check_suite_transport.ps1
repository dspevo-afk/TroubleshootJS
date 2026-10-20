[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'suite_transport.ps1')
$results=[Collections.Generic.List[object]]::new()
$unit=@(Get-NormalPackingR2SuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-suites.json') -Raw))
$expectedUnit=@('PcbConnectedPocketPackingContractTest','PcbNormalPackingIdentityContractTest','PcbPackingMetricsContractTest','PcbPackingProbeReceiptContractTest')
if($unit.Count -ne 4){throw 'Unit four-row nested array lost'}
for($unitIndex=0;$unitIndex -lt 4;$unitIndex++){if($unit[$unitIndex] -cne $expectedUnit[$unitIndex]){throw 'Unit suite partition order changed'}}
$results.Add([ordered]@{case='Actual unit four-row nested manifest';result='PASS'})
$routes=@(Get-NormalPackingR2SuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'routes-suites.json') -Raw))
$expectedRoutes=@('PcbNormalPackingCensusContractTest','PcbNormalPackingCensusContractTestGroup2','PcbNormalPackingCensusContractTestGroup3','PcbNormalPackingCensusContractTestGroup4','PcbNormalPackingCensusContractTestGroup5','PcbNormalPackingCensusContractTestGroup6','PcbNormalPackingCensusContractTestGroup7','PcbNormalPackingCensusContractTestGroup8')
if($routes.Count -ne 8){throw 'Route eight-row nested array lost'}
for($routeIndex=0;$routeIndex -lt 8;$routeIndex++){if($routes[$routeIndex] -cne $expectedRoutes[$routeIndex]){throw 'Route partition order changed'}}
$results.Add([ordered]@{case='Actual route eight-row nested manifest';result='PASS'})
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
    try{$null=@(Get-NormalPackingR2SuiteNames -JsonText $entry.Value)}catch{$rejected=$true}
    if(-not $rejected){throw ('Invalid manifest accepted: '+$entry.Key)}
    $results.Add([ordered]@{case=$entry.Key;result='REJECTED'})
}
$pins=[ordered]@{}
foreach($name in @('suite_transport.ps1','check_suite_transport.ps1','unit-suites.json','routes-suites.json')){
    $pins[$name]=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $name) -Algorithm SHA256).Hash.ToLowerInvariant()
}
$canary=[ordered]@{status='PASS';scope='Real selected PowerShell r2 suite transport only; no Java fixture proof';
    powerShellVersion=$PSVersionTable.PSVersion.ToString();caseCount=$results.Count;
    actualManifests=2;negativeCases=10;consumedSha256=$pins;cases=$results} |
    ConvertTo-Json -Depth 6 | ForEach-Object { $_ -replace "`r`n", "`n" }
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'suite-transport-canaries.json'),
    $canary+"`n",[Text.UTF8Encoding]::new($false))
Write-Output ('PASS real PowerShell r2 suite transport: '+$results.Count+' cases; unit4/route8 nested inputs exact')

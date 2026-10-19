[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'suite_transport.ps1')
$results=[Collections.Generic.List[object]]::new()
$unit=@(Get-GlobalPocketSuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-suites.json') -Raw))
if($unit.Count -ne 4 -or $unit[0] -cne 'PcbGlobalPocketDescentContractTest' -or $unit[1] -cne 'PcbMediumPlacementCostContractTest' -or $unit[2] -cne 'PcbVacancyTargetsContractTest' -or $unit[3] -cne 'RelBSessionIdentityContractTest'){throw 'Actual four-suite manifest lost its array boundary'}
$results.Add([ordered]@{case='Actual four-row manifest';result='PASS'})
$routes=@(Get-GlobalPocketSuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'routes-suites.json') -Raw))
$expected=@('PcbGlobalPocketDescentTrialContractTest')+@(2..7 | ForEach-Object {'PcbGlobalPocketDescentTrialContractTestGroup'+$_})
if($routes.Count -ne 7){throw 'Actual seven-row manifest count changed'}
for($i=0;$i -lt 7;$i++){if($routes[$i] -cne $expected[$i]){throw 'Actual suite ordering changed'}}
$results.Add([ordered]@{case='Actual seven-row manifest in order';result='PASS'})
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
    try{$null=@(Get-GlobalPocketSuiteNames -JsonText $entry.Value)}catch{$rejected=$true}
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
Write-Output ('PASS real PowerShell suite transport: '+$results.Count+' cases; four-row and seven-row inputs exact')

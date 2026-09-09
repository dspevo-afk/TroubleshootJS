[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $root 'scripts/VerifierIsolation.psm1') -Force
$source = Join-Path $root 'scripts/verify-a03-browser.ps1'
$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -ne 0) { throw 'Browser report reader has syntax errors.' }
# Load only pure report/route functions; never execute preview/browser setup.
foreach ($name in @('Get-ExactReportText', 'Test-JsonReport', 'Test-A04Report',
        'Test-RouteReady', 'Get-RouteDefinitions')) {
    $found = @($ast.FindAll({ param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -ceq $name
    }, $true))
    if ($found.Count -ne 1) { throw "Expected exactly one report function $name." }
    . ([ScriptBlock]::Create($found[0].Extent.Text))
}
$script:assertions = 0
function Assert-ReportContract([bool]$Condition, [string]$Message) {
    $script:assertions++
    if (-not $Condition) { throw $Message }
}
function Encode-Report($Value) { return $Value | ConvertTo-Json -Depth 12 -Compress }
function Copy-Report($Value) { return (Encode-Report $Value) | ConvertFrom-Json }
function Test-ReportRejected($Value) {
    try { return -not (Test-A04Report $Value) } catch { return $true }
}
$flags = @('coordinateIsolation', 'explicitJoins', 'terminalCorrespondence',
    'failureIsolation', 'recipeIdentity', 'multiUnitPackage', 'originalOwnerRestored')
$valid = [ordered]@{
    protocol = 'TSJ-A04-CONSTRUCTION-1'; status = 'PASS'
    contractAssertions = 1; runtimeAssertions = 1; candidateCleanup = 'PASS'
    cases = @(
        @{ generatorVersion = 1; seed = '1'; elementCount = 16; packageCount = 3;
            unitCount = 3; manifestBytes = 20121; elapsedMs = 1; assemblyMs = 0 },
        @{ generatorVersion = 2; seed = '2'; elementCount = 30; packageCount = 7;
            unitCount = 7; manifestBytes = 63897; elapsedMs = 2; assemblyMs = 1 },
        @{ generatorVersion = 3; seed = '3'; elementCount = 30; packageCount = 7;
            unitCount = 7; manifestBytes = 66531; elapsedMs = 3; assemblyMs = 2 })
}
foreach ($flag in $flags) { $valid[$flag] = $true }
Assert-ReportContract (Test-A04Report (Encode-Report $valid)) 'Valid report was rejected.'
foreach ($key in @($valid.Keys)) {
    $copy = Copy-Report $valid
    $copy.PSObject.Properties.Remove($key)
    Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) "Missing $key was accepted."
}
foreach ($flag in $flags) {
    foreach ($value in @($false, 'true', 'false', 1, $null)) {
        $copy = Copy-Report $valid
        $copy.$flag = $value
        Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) "Malformed $flag was accepted."
    }
}
foreach ($key in @('contractAssertions', 'runtimeAssertions')) {
    foreach ($value in @(0, -1, '1', 1.5, $null)) {
        $copy = Copy-Report $valid; $copy.$key = $value
        Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) "Invalid $key was accepted."
    }
}
foreach ($key in @('protocol', 'status', 'candidateCleanup')) {
    $copy = Copy-Report $valid; $copy.$key = 'FAIL'
    Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) "Invalid $key was accepted."
}
foreach ($field in @('seed', 'elementCount', 'packageCount', 'unitCount',
        'manifestBytes', 'elapsedMs', 'assemblyMs')) {
    $copy = Copy-Report $valid; $copy.cases[0].PSObject.Properties.Remove($field)
    Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) "Missing case $field was accepted."
    $copy = Copy-Report $valid; $copy.cases[0].$field = $true
    Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) "Boolean case $field was accepted."
}
$copy = Copy-Report $valid; $copy.cases[0].seed = '01'
Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) 'Noncanonical case seed was accepted.'
$copy = Copy-Report $valid; $copy.cases[0].packageCount = 4
Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) 'Incorrect package count was accepted.'
$copy = Copy-Report $valid; $copy.cases[1].elementCount = 31
Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) 'Incorrect modeled-element count was accepted.'
foreach ($value in @(0, 4, '1', 1.5, $null)) {
    $copy = Copy-Report $valid; $copy.cases[0].generatorVersion = $value
    Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) 'Invalid generator version was accepted.'
}
$copy = Copy-Report $valid; $copy.cases[0].generatorVersion = 2
Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) 'Duplicate version was accepted.'
$copy = Copy-Report $valid; $copy.cases = @($copy.cases[0], $copy.cases[1])
Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) 'Missing construction case was accepted.'
$copy = Copy-Report $valid; $copy.cases = $null
Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) 'Null construction cases were accepted.'
foreach ($value in @('', '{', 'null', '[]', '{}')) {
    Assert-ReportContract (Test-ReportRejected $value) 'Malformed report was accepted.'
}
$reports = [pscustomobject]@{
    verification = 'PASS:a04'; a04 = (Encode-Report $valid)
    normalReady = [pscustomobject]@{ programReady = $true; retestCustomerReady = $true }
}
Assert-ReportContract (Test-RouteReady 'a04' $reports) 'Positive A04 route was rejected.'
$reports.verification = 'FAIL:a04:a04-explicit-failure-canary'; $reports.a04 = ''
Assert-ReportContract (Test-RouteReady 'a04-forced' $reports) 'Forced negative was rejected.'
$reports.a04 = Encode-Report $valid
Assert-ReportContract (-not (Test-RouteReady 'a04-forced' $reports)) 'Forced failure accepted a success receipt.'
$reports.verification = ''; $reports.a04 = ''
Assert-ReportContract (Test-RouteReady 'a04-debugoff' $reports) 'Debug-off route was rejected.'
$reports.verification = 'RUNNING:a04'
Assert-ReportContract (-not (Test-RouteReady 'a04-debugoff' $reports)) 'Debug-off accepted A04 execution.'
$reports.verification = ''; $reports.a04 = Encode-Report $valid
Assert-ReportContract (-not (Test-RouteReady 'a04-debugoff' $reports)) 'Debug-off exposed a developer report.'
$routes = @(Get-RouteDefinitions 'A04' $false)
Assert-ReportContract ($routes.Count -eq 3) 'A04 must register three routes.'
Assert-ReportContract ((@($routes.name | Sort-Object -Unique)).Count -eq 3) 'A04 route names must be unique.'
Assert-ReportContract ((@(Get-RouteDefinitions 'All' $false)).Count -eq 9) 'Historical All route set changed.'
Assert-ReportContract ((@(Get-RouteDefinitions 'A03' $false)).Count -eq 3) 'A03 route set changed.'
Assert-ReportContract ((@(Get-RouteDefinitions 'A04' $true)).Count -eq 1) 'Smoke should contain only transport smoke.'
Write-Output ('PASS: A04 report contracts assertions=' + $script:assertions)
exit 0

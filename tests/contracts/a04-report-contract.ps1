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
foreach ($name in @('Get-ExactReportText', 'Test-JsonReport', 'Test-A04Report', 'Test-ControlledReport',
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
        @{ route = 'resistive'; seed = '1'; generatorVersion = 5;
            replayVerified = $true; ownerRestored = $true; elapsedMs = 1; assemblyMs = 0 },
        @{ route = 'resistive'; seed = '2'; generatorVersion = 5;
            replayVerified = $true; ownerRestored = $true; elapsedMs = 2; assemblyMs = 1 },
        @{ route = 'resistive'; seed = '3'; generatorVersion = 5;
            replayVerified = $true; ownerRestored = $true; elapsedMs = 3; assemblyMs = 2 },
        @{ route = 'controlled'; seed = '1'; generatorVersion = 5;
            replayVerified = $true; ownerRestored = $true; elapsedMs = 4; assemblyMs = 2 },
        @{ route = 'controlled'; seed = '2'; generatorVersion = 5;
            replayVerified = $true; ownerRestored = $true; elapsedMs = 5; assemblyMs = 3 },
        @{ route = 'controlled'; seed = '3'; generatorVersion = 5;
            replayVerified = $true; ownerRestored = $true; elapsedMs = 6; assemblyMs = 3 })
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
foreach ($field in @('route', 'seed', 'generatorVersion', 'replayVerified',
        'ownerRestored', 'elapsedMs', 'assemblyMs')) {
    $copy = Copy-Report $valid; $copy.cases[0].PSObject.Properties.Remove($field)
    Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) "Missing case $field was accepted."
    if ($field -notin @('replayVerified', 'ownerRestored')) {
        $copy = Copy-Report $valid; $copy.cases[0].$field = $true
        Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) "Malformed case $field was accepted."
    }
}
foreach ($field in @('replayVerified', 'ownerRestored')) {
    $copy = Copy-Report $valid; $copy.cases[0].$field = $false
    Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) "False case $field was accepted."
    $copy = Copy-Report $valid; $copy.cases[0].$field = 'true'
    Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) "String case $field was accepted."
}
$copy = Copy-Report $valid; $copy.cases[0].seed = '01'
Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) 'Noncanonical case seed was accepted.'
$copy = Copy-Report $valid; $copy.cases[0].route = 'legacy'
Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) 'Unknown route was accepted.'
$copy = Copy-Report $valid; $copy.cases[0].generatorVersion = 3
Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) 'Retired generator version was accepted.'
$copy = Copy-Report $valid; $copy.cases[0].replayVerified = $false
Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) 'Unverified replay was accepted.'
$copy = Copy-Report $valid; $copy.cases[0].ownerRestored = $false
Assert-ReportContract (Test-ReportRejected (Encode-Report $copy)) 'Unrestored owner was accepted.'
foreach ($value in @(0, 3, 4, 6, '5', 1.5, $null)) {
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
Assert-ReportContract ((@(Get-RouteDefinitions 'A03' $false)).Count -eq 3) 'A03 route set changed.'
Assert-ReportContract ((@(Get-RouteDefinitions 'A04' $true)).Count -eq 1) 'Smoke should contain only transport smoke.'
$currentRoutes = @(Get-RouteDefinitions 'Current' $false)
Assert-ReportContract ($currentRoutes.Count -eq 9) 'Current browser corpus must contain nine selected routes.'
Assert-ReportContract ((@($currentRoutes.name) -join ',') -ceq
    'a03,task46,task47,task48,task49,a02,a04,a04-forcedfailure,a04-debugoff') `
    'Current browser corpus route order changed.'
$a02Report = [pscustomobject]@{ protocol = 'TSJ-A02-2'; status = 'PASS' }
$a02Reports = [pscustomobject]@{
    verification = 'PASS:a02'; a02 = (Encode-Report $a02Report)
}
Assert-ReportContract (Test-RouteReady 'a02' $a02Reports) 'Current A02 report was rejected.'
foreach ($protocol in @('TSJ-A02-1', 'TSJ-A02-unknown')) {
    $a02Report.protocol = $protocol; $a02Reports.a02 = Encode-Report $a02Report
    Assert-ReportContract (-not (Test-RouteReady 'a02' $a02Reports)) 'Retired or unknown A02 report was accepted.'
}
$a02Report.protocol = 'TSJ-A02-2'; $a02Report.status = 'FAIL'
$a02Reports.a02 = Encode-Report $a02Report
Assert-ReportContract (-not (Test-RouteReady 'a02' $a02Reports)) 'Failed A02 report was accepted.'
$a02Report.status = 'PASS'; $a02Reports.a02 = Encode-Report $a02Report
$a02Reports.verification = 'RUNNING:a02'
Assert-ReportContract (-not (Test-RouteReady 'a02' $a02Reports)) 'Unfinished A02 execution was accepted.'
$a05Routes = @(Get-RouteDefinitions 'A05' $false)
Assert-ReportContract (($a05Routes.name -join ',') -ceq
    'a03,task46,task49,a04,a04-forcedfailure,a04-debugoff') 'A05 bounded route set changed.'
# Synthetic protocol fixtures exercise fail-closed parsing only. They are not
# CircuitJS or browser evidence and are never emitted by the product verifier.
$valueSeeds = @('-1','0','1','2','3','-9223372036854775808','9223372036854775807',
    '9007199254740993','-9007199254740993')
$solverSeeds = @('-1','0','1','-9223372036854775808')
$roleSeeds = @('-1','0','1','2','-9223372036854775808','9223372036854775807')
$controlled = [ordered]@{
    protocol='TSJ-TASK49-2'; status='PASS'; requestedSeed='3'; assertions=1; measurementCases=4
    originalOwnerRestored=$true; candidateCleanup='PASS'
    construction=@{ originalOwnerPreserved=$true;
        failedStages=@('MAPPING','ELECTRICAL','LAYOUT','REGISTRATION','VALIDATION') }
    succession=@{ boardReplacement=$true; staleCompletion=$true }
    roleSelectionVectors=@($roleSeeds | ForEach-Object {
        'seed=' + $_ + ';a=nmos-low-side-driver;b=npn-low-side-driver;fault=channel-a-load-RLOAD-OPEN'
    })
    valueCases=@($valueSeeds | ForEach-Object {
        @{ seed=$_; descriptor='current-fixture'; faultDecision='channel-a-load-RLOAD-OPEN'; channels=@(
            @{channel='channel-a';provider='nmos-low-side-driver';catalog='r330';resistanceOhms=330;tolerancePercent=5},
            @{channel='channel-b';provider='npn-low-side-driver';catalog='r270';resistanceOhms=270;tolerancePercent=5}) }
    })
    cases=@($solverSeeds | ForEach-Object {
        @{ seed=$_; faultOwner='owner-a-driver'; freshReplay=$true; inputOrderIndependent=$true
            independentControls=$true; repairReachable=$true; assertions=1
            physicalCorrespondence=@{status='PASS'}; mutations=@{status='PASS'; owners=4}
            support=@{brokenHealthyRejected=$true;brokenRetestRejected=$true;restoredPassed=$true}
            normalAdmission=@{status='EXECUTED'; routes=@(
                @('owner-a-driver','owner-a-load','owner-b-driver','owner-b-load') | ForEach-Object {
                    @{route=('fixture/' + $_); measuredDepth=1; samples=1; repair=$true; retest=$true}
                })}
            faultRepairs=@(@('owner-a-driver','owner-a-load','owner-b-driver','owner-b-load') | ForEach-Object {
                @{owner=$_;wrongRejected=$true;correctPassed=$true;alternativePassed=$true;otherOwnersUnchanged=$true}
            }) }
    })
}
function Test-ControlledFixture($Value) { return Test-ControlledReport (Encode-Report $Value) 'TSJ-TASK49-2' }
Assert-ReportContract (Test-ControlledFixture $controlled) 'Complete controlled fixture rejected.'
foreach ($field in @($controlled.Keys)) {
    $copy=Copy-Report $controlled; $copy.PSObject.Properties.Remove($field)
    Assert-ReportContract (-not (Test-ControlledFixture $copy)) "Missing controlled $field accepted."
}
foreach ($protocol in @('TSJ-TASK49-1','TSJ-TASK49-3')) {
    $copy=Copy-Report $controlled; $copy.protocol=$protocol
    Assert-ReportContract (-not (Test-ControlledFixture $copy)) 'Retired/unknown controlled report accepted.'
}
foreach ($field in @('freshReplay','inputOrderIndependent','independentControls','repairReachable')) {
    foreach ($value in @($false,'true',1,$null)) {
        $copy=Copy-Report $controlled; $copy.cases[0].$field=$value
        Assert-ReportContract (-not (Test-ControlledFixture $copy)) "Malformed case $field accepted."
    }
}
foreach ($field in @('wrongRejected','correctPassed','alternativePassed','otherOwnersUnchanged')) {
    foreach ($value in @($false,'true',1,$null)) {
        $copy=Copy-Report $controlled; $copy.cases[1].faultRepairs[2].$field=$value
        Assert-ReportContract (-not (Test-ControlledFixture $copy)) "Malformed repair $field accepted."
    }
}
foreach ($field in @('brokenHealthyRejected','brokenRetestRejected','restoredPassed')) {
    foreach ($value in @($false,'true',1,$null)) {
        $copy=Copy-Report $controlled; $copy.cases[2].support.$field=$value
        Assert-ReportContract (-not (Test-ControlledFixture $copy)) "Malformed support $field accepted."
    }
}
foreach ($field in @('cases','valueCases','roleSelectionVectors')) {
    $copy=Copy-Report $controlled; $copy.$field=@($copy.$field[0])
    Assert-ReportContract (-not (Test-ControlledFixture $copy)) "Missing $field coverage accepted."
    $copy=Copy-Report $controlled; $copy.$field[1]=$copy.$field[0]
    Assert-ReportContract (-not (Test-ControlledFixture $copy)) "Duplicate $field accepted."
}
$copy=Copy-Report $controlled; $copy.cases[0].faultRepairs[1]=$copy.cases[0].faultRepairs[0]
Assert-ReportContract (-not (Test-ControlledFixture $copy)) 'Duplicate repaired owner accepted.'
$copy=Copy-Report $controlled; $copy.cases[0].normalAdmission.routes[0].route='fixture/foreign'
Assert-ReportContract (-not (Test-ControlledFixture $copy)) 'Foreign admission repair owner accepted.'
$copy=Copy-Report $controlled; $copy.cases[0].normalAdmission.routes[0].samples=0
Assert-ReportContract (-not (Test-ControlledFixture $copy)) 'Unexecuted admission route accepted.'
$copy=Copy-Report $controlled; $copy.valueCases[0].channels[1]=$copy.valueCases[0].channels[0]
Assert-ReportContract (-not (Test-ControlledFixture $copy)) 'Duplicate physical channel accepted.'
$copy=Copy-Report $controlled; $copy.valueCases[0].channels[1].provider='switch-fallback'
Assert-ReportContract (-not (Test-ControlledFixture $copy)) 'Unknown role provider accepted.'
$copy=Copy-Report $controlled; $copy.valueCases[0].channels[1].resistanceOhms='330'
Assert-ReportContract (-not (Test-ControlledFixture $copy)) 'String recipe value accepted.'
$copy=Copy-Report $controlled; $copy.construction.failedStages=@('MAPPING')
Assert-ReportContract (-not (Test-ControlledFixture $copy)) 'Missing late construction failure accepted.'
$copy=Copy-Report $controlled; $copy.succession.staleCompletion=$false
Assert-ReportContract (-not (Test-ControlledFixture $copy)) 'Unproven stale cleanup accepted.'
foreach ($seed in @('03','9223372036854775808',3,$null)) {
    $copy=Copy-Report $controlled; $copy.requestedSeed=$seed
    Assert-ReportContract (-not (Test-ControlledFixture $copy)) 'Noncanonical requested seed accepted.'
}
$controlledReports=[pscustomobject]@{ verification='PASS:task49';task49=(Encode-Report $controlled) }
Assert-ReportContract (Test-RouteReady 'task49' $controlledReports) 'Current controlled route rejected.'
$controlledReports.verification='RUNNING:task49'
Assert-ReportContract (-not (Test-RouteReady 'task49' $controlledReports)) 'Unfinished controlled route accepted.'
Write-Output ('PASS: A04 report contracts assertions=' + $script:assertions)
exit 0

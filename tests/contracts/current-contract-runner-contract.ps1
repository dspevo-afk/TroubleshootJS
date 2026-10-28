Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runnerPath = Join-Path $repositoryRoot 'scripts/verify-current-contracts.ps1'
$tokens = $null
$parseErrors = $null
$runnerAst = [System.Management.Automation.Language.Parser]::ParseFile(
    $runnerPath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -ne 0) {
    throw ('Runner PowerShell parse failed: ' + $parseErrors[0].Message)
}
$helperDefinitions = @($runnerAst.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
        $node.Name -ceq 'Test-CurrentContractPassMarker'
}, $true))
if ($helperDefinitions.Count -ne 1) {
    throw ('Expected one runner PASS-marker helper, found ' + $helperDefinitions.Count)
}
. ([scriptblock]::Create($helperDefinitions[0].Extent.Text))

$assertions = 0
function Require-RunnerContract($condition, [string]$message) {
    $script:assertions++
    if (-not $condition) { throw $message }
}

$validOutput = "diagnostic`r`nPASS: fixture suite assertions=7`r`n"
Require-RunnerContract (Test-CurrentContractPassMarker -Stdout $validOutput `
    -Marker 'fixture suite assertions=' -TerminationProven $true -ExitCode 0) `
    'One exact expected PASS line with exit 0 and proven termination qualifies.'
Require-RunnerContract (-not (Test-CurrentContractPassMarker -Stdout 'diagnostic only' `
    -Marker 'fixture suite assertions=' -TerminationProven $true -ExitCode 0)) `
    'Missing PASS marker must fail.'
Require-RunnerContract (-not (Test-CurrentContractPassMarker `
    -Stdout 'PASS: another fixture suite assertions=7' `
    -Marker 'fixture suite assertions=' -TerminationProven $true -ExitCode 0)) `
    'A different PASS marker must fail.'
Require-RunnerContract (-not (Test-CurrentContractPassMarker `
    -Stdout 'diagnostic PASS: fixture suite assertions=7' `
    -Marker 'fixture suite assertions=' -TerminationProven $true -ExitCode 0)) `
    'A marker embedded in a diagnostic line must fail.'
Require-RunnerContract (-not (Test-CurrentContractPassMarker `
    -Stdout 'PASS: fixture suiteevil assertions=7' `
    -Marker 'fixture suite' -TerminationProven $true -ExitCode 0)) `
    'A longer token sharing the expected marker prefix must fail.'
Require-RunnerContract (-not (Test-CurrentContractPassMarker `
    -Stdout "PASS: fixture suite assertions=7`nPASS: fixture suite assertions=8" `
    -Marker 'fixture suite assertions=' -TerminationProven $true -ExitCode 0)) `
    'Duplicate expected PASS lines must fail.'
Require-RunnerContract (-not (Test-CurrentContractPassMarker -Stdout $validOutput `
    -Marker 'fixture suite assertions=' -TerminationProven $false -ExitCode 0)) `
    'Unproven process termination must fail even when output contains PASS.'
Require-RunnerContract (-not (Test-CurrentContractPassMarker -Stdout $validOutput `
    -Marker 'fixture suite assertions=' -TerminationProven $true -ExitCode 1)) `
    'A nonzero exit code must fail even when output contains PASS.'

$censusAssignments = @($runnerAst.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.AssignmentStatementAst] -and
        $node.Left.Extent.Text -match '\$expectedOutputNames$'
}, $true))
if ($censusAssignments.Count -ne 1) { throw 'Missing unique runner output-census assignment.' }
$censusAssignment = [scriptblock]::Create($censusAssignments[0].Extent.Text)
$testClasses = @('first', 'second')
$Suite = @('only')
. $censusAssignment
Require-RunnerContract ($expectedOutputNames -is [string[]] -and
    $expectedOutputNames.Count -eq 1 -and $expectedOutputNames[0] -ceq 'only') `
    'One selected suite must retain array identity on Windows PowerShell 5.1.'
$Suite = @('first', 'second')
. $censusAssignment
Require-RunnerContract ($expectedOutputNames.Count -eq 2) 'Multiple selected suites retain their exact output census.'
$Suite = @()
. $censusAssignment
Require-RunnerContract ($expectedOutputNames.Count -eq 2 -and
    ($expectedOutputNames -join ',') -ceq 'first,second') 'The full suite retains every declared output owner.'

$denseAssignments = @($runnerAst.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.AssignmentStatementAst] -and
        $node.Left.Extent.Text -in @('$denseFirstRequested', '$denseSecondRequested')
}, $true))
$denseBranches = @($runnerAst.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.IfStatementAst] -and
        $node.Clauses[0].Item1.Extent.Text -ceq '$denseFirstRequested -and $denseSecondRequested'
}, $true))
if ($denseAssignments.Count -ne 2 -or $denseBranches.Count -ne 1) {
    throw 'Missing unique actual dense census selection/receipt branch.'
}
$denseSelection = [scriptblock]::Create(($denseAssignments | ForEach-Object { $_.Extent.Text }) -join "`n")
$denseReceiptBranch = [scriptblock]::Create($denseBranches[0].Extent.Text)
$denseFirstName = 'DensePcbPackingContractTest'
$denseSecondName = 'DensePcbPackingContractTestGroup2'
$densePcbPackingReceipts = @{
    $denseFirstName = [pscustomobject]@{Cohort='cohort-1'; SeedCount=6; SeedCsv='0,1,2,3,17,42'}
    $denseSecondName = [pscustomobject]@{Cohort='cohort-2'; SeedCount=5; SeedCsv='101,-1,9007199254740993,-9223372036854775808,9223372036854775807'}
}
foreach ($case in @(
        @{Selected=@(); Expected='PASS: dense PCB packing census seeds=11 cohorts=2 '},
        @{Selected=@('densepcbpackingcontracttest'); Expected='PARTIAL: dense PCB packing seeds=6/11 '},
        @{Selected=@('DENSEPCBPACKINGCONTRACTTESTGROUP2'); Expected='PARTIAL: dense PCB packing seeds=5/11 '},
        @{Selected=@('densepcbpackingcontracttest','DensePcbPackingContractTestGroup2'); Expected='PASS: dense PCB packing census seeds=11 cohorts=2 '},
        @{Selected=@('unrelated'); Expected=''})) {
    $Suite = $case.Selected
    $receipts = [Collections.Generic.List[string]]::new()
    . $denseSelection
    $null = & $denseReceiptBranch 6>&1
    $expectedCount = if ($case.Expected) { 1 } else { 0 }
    Require-RunnerContract ($receipts.Count -eq $expectedCount -and
        ($expectedCount -eq 0 -or $receipts[0].StartsWith($case.Expected, [StringComparison]::Ordinal))) `
        ('Accepted suite casing must preserve the actual full/partial census receipt: ' + ($Suite -join ','))
}

Write-Output ('PASS: current-contract runner pass detection assertions=' + $assertions)

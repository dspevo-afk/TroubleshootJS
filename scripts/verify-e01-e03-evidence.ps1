[CmdletBinding()]
param([string]$EvidenceRoot = (Join-Path $PSScriptRoot '../docs/task-evidence'))
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'VerifierIsolation.psm1') -Force

# Reuse the maintained strict regression readers without starting a browser wrapper.
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $PSScriptRoot 'verify-a03-browser.ps1'), [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'Regression reader syntax error.' }
foreach ($name in @('Get-ExactReportText','Test-JsonReport','Test-ControlledReport','Test-A08Report','Test-Task41Report')) {
    $found = @($ast.FindAll({param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name
    }, $true))
    if ($found.Count -ne 1) { throw "Missing unique reader $name" }
    . ([ScriptBlock]::Create($found[0].Extent.Text))
}
function Read-Evidence([string]$Path) { Get-Content -LiteralPath (Join-Path $EvidenceRoot $Path) -Raw }
function Test-Number($Value, [double]$Minimum, [double]$Maximum) {
    return ($Value -is [int] -or $Value -is [long] -or $Value -is [double] -or $Value -is [decimal]) -and
        -not [double]::IsNaN([double]$Value) -and -not [double]::IsInfinity([double]$Value) -and
        $Value -ge $Minimum -and $Value -le $Maximum
}
function Test-Source($Report) {
    try {
        $p = $Report | ConvertFrom-Json
        return $p.schema -ceq 'e01-source-proof-v1' -and $p.status -ceq 'PASS' -and
            (Test-VerifierStrictIntegralValue $p.assertions 21L ([long]::MaxValue)) -and
            (Test-VerifierStrictIntegralValue $p.elapsedMs 0L ([long]::MaxValue)) -and
            (Test-Number $p.nominalVolts 4.9974 4.9976) -and
            (Test-Number $p.limitedVolts .10000 .10001) -and
            (Test-Number $p.shortAmps .10000 .10001) -and
            (Test-Number $p.backfeedVolts 11.999 12.001)
    } catch { return $false }
}
function Test-Relay($Report) {
    try {
        $p = $Report | ConvertFrom-Json
        if ($p.schema -cne 'e03-relay-proof-v1' -or $p.status -cne 'PASS' -or
            -not (Test-VerifierStrictIntegralValue $p.assertions 46L ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $p.mutationAssertions 113L ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $p.playerCases 6L 6L) -or
            -not (Test-VerifierStrictIntegralValue $p.elapsedMs 0L ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $p.rawElapsedMs 0L ([long]$p.elapsedMs)) -or
            $p.cases -isnot [array] -or $p.cases.Count -ne 2) { return $false }
        foreach ($driver in @('BJT','NMOS')) {
            $case = @($p.cases | Where-Object {$_.driver -ceq $driver})
            if ($case.Count -ne 1 -or
                -not (Test-Number $case[0].onVolts 11.978 11.989) -or
                -not (Test-Number $case[0].offVolts .00000210 .00000222) -or
                -not (Test-Number $case[0].coilAmps .037 .041) -or
                -not (Test-Number $case[0].flybackAmps .025 .041)) { return $false }
        }
        return $true
    } catch { return $false }
}
$source = Read-Evidence 'E01/compiled-source-proof.json'
$relay = Read-Evidence 'E03/compiled-relay-proof.json'
if (-not (Test-Source $source) -or -not (Test-Relay $relay)) { throw 'Incomplete electrical proof.' }
if (-not (Test-ControlledReport (Read-Evidence 'E01/task49-report.json') 'TSJ-TASK49-2')) { throw 'Task49 regression failed.' }
if (-not (Test-A08Report (Read-Evidence 'E01/a08-report.json'))) { throw 'A08 regression failed.' }
if (-not (Test-Task41Report (Read-Evidence 'E03/task41-report.txt'))) { throw 'Task41 diagnostic regression failed.' }
foreach ($id in @('E01','E03')) {
    $negative = (Read-Evidence "$id/forced-negative.json") | ConvertFrom-Json
    $lower = $id.ToLowerInvariant()
    if ($null -ne $negative.report -or $negative.status -cne "FAIL:${lower}:${lower}-explicit-failure-canary") {
        throw "Forced failure did not fail closed: $id"
    }
}
# Reader canaries reject truncated, fake, duplicated and string-valued proof.
if ((Test-Relay '{"status":"PASS"}') -or (Test-Source '{}') -or
    (Test-Relay ($relay.Replace('"NMOS"','"BJT"'))) -or
    (Test-Relay ($relay -replace '"playerCases"\s*:\s*6','"playerCases":"6"')) -or
    (Test-Source ($source -replace '"shortAmps"\s*:\s*[^,}]+','"shortAmps":0'))) {
    throw 'Electrical evidence reader accepted a negative canary.'
}
Write-Output 'PASS: E01/E03 electrical reports, forced failures, five reader canaries and maintained Task41/Task49/A08 readers.'

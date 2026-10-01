param(
    [Parameter(Mandatory = $true)][string]$VerifierPath,
    [Parameter(Mandatory = $true)][string]$InputLogPath,
    [string]$OutputPath = (Join-Path $PSScriptRoot 'scale-sensitivity-parser-canaries.json')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

foreach ($path in @($VerifierPath, $InputLogPath)) {
    if (-not [IO.File]::Exists($path)) { throw ('Required input not found: ' + [IO.Path]::GetFileName($path)) }
}

$tokens = $null
$parseErrors = $null
$verifierAst = [System.Management.Automation.Language.Parser]::ParseFile(
    $VerifierPath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -ne 0) {
    throw ('Verifier AST parse failed: ' +
        [String]::Join('; ', @($parseErrors | ForEach-Object { $_.Message })))
}
$checkerAst = $verifierAst.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
        $node.Name -eq 'Test-Q30SensitivityCaseOutput'
}, $true)
if ($null -eq $checkerAst) { throw 'Exact sensitivity output checker was not found in verifier AST.' }
Invoke-Expression $checkerAst.Extent.Text

$logLines = @(Get-Content -LiteralPath $InputLogPath)
$planLine = $null
$plan = $null
foreach ($line in $logLines) {
    if (-not $line.StartsWith('Q30_CASE_PLAN ')) { continue }
    $candidate = $line.Substring('Q30_CASE_PLAN '.Length) | ConvertFrom-Json -ErrorAction Stop
    if ($candidate.seed -ceq '7') {
        if ($null -ne $plan) { throw 'Duplicate seed-7 Q30_CASE_PLAN rows in source log.' }
        $plan = $candidate
        $planLine = $line
    }
}
if ($null -eq $plan) { throw 'Source log has no authenticated seed-7 Q30_CASE_PLAN row.' }

$baseAxisMatch = [regex]::Match($plan.canonical,
    ';topology=(RB30_CH[12]_(SEPARATE_DIRECT|SHARED_DIRECT|SHARED_HYSTERETIC)_A_(BJT|NMOS)(?:_B_(BJT|NMOS))?);')
if (-not $baseAxisMatch.Success) { throw 'Seed-7 canonical topology is malformed.' }
$supportIdentity = 'C12:' + $plan.support.C12.ToString().ToLowerInvariant() +
    ',S5:' + $plan.support.S5.ToString().ToLowerInvariant() +
    ',S12:' + $plan.support.S12.ToString().ToLowerInvariant() +
    ',filters:' + $plan.support.filters +
    ',outputIndicators:' + $plan.support.outputIndicators +
    ',bleeder5:' + $plan.support.bleeder5.ToString().ToLowerInvariant()
$expectedTopology = $baseAxisMatch.Groups[1].Value +
    '_C12_' + $(if ($plan.support.C12) { 'Y' } else { 'N' }) +
    '_S5_' + $(if ($plan.support.S5) { 'Y' } else { 'N' }) +
    '_S12_' + $(if ($plan.support.S12) { 'Y' } else { 'N' }) +
    '_F' + $plan.support.filters + '_O' + $plan.support.outputIndicators +
    '_B5_' + $(if ($plan.support.bleeder5) { 'Y' } else { 'N' })
if ($plan.topology -cne $expectedTopology -or
        $plan.canonical -notmatch (';support=' + [regex]::Escape($supportIdentity) + ';')) {
    throw 'Seed-7 support/topology metadata does not match the canonical identity.'
}
$relayFault = @($plan.faults | Where-Object { $_.id -match '^RELAY_[AB]_COIL_OPEN$' })
if ($relayFault.Count -ne 1) { throw 'Seed-7 plan must declare exactly one channel relay fault.' }
$faultId = [string]$relayFault[0].id

$pairIndex = -1
for ($index = 0; $index -lt $logLines.Count; $index++) {
    if ($logLines[$index].StartsWith('Q30_STEP_PAIR seed=7 ') -and
            $logLines[$index].Contains(' fault=' + $faultId + ' ')) {
        $pairIndex = $index
        break
    }
}
if ($pairIndex -lt 0) { throw 'Source log has no seed-7 relay Q30_STEP_PAIR row.' }
$passIndex = -1
for ($index = $pairIndex + 1; $index -lt $logLines.Count; $index++) {
    if ($logLines[$index].StartsWith('PASS: Q30 production solver step sensitivity assertions=') -and
            $logLines[$index].Contains(' seed=7 ')) {
        $passIndex = $index
        break
    }
}
if ($passIndex -lt 0) { throw 'Source log has no seed-7 sensitivity PASS row after the pair row.' }
$pairRow = $logLines[$pairIndex]
$passRow = $logLines[$passIndex]
$realCaseOutput = $pairRow + [Environment]::NewLine + $passRow

$badSupport = $supportIdentity.Replace(',filters:' + $plan.support.filters,
    ',filters:' + ($plan.support.filters + 1))
$badTopology = if ($expectedTopology.Contains('_C12_Y_')) {
    $expectedTopology.Replace('_C12_Y_', '_C12_N_')
} else {
    $expectedTopology.Replace('_C12_N_', '_C12_Y_')
}
$badCanonical = $plan.canonical.Replace(';packages=' + $plan.packageCount + ';',
    ';packages=' + ($plan.packageCount + 1) + ';')
if ($badSupport -ceq $supportIdentity -or $badTopology -ceq $expectedTopology -or
        $badCanonical -ceq $plan.canonical) {
    throw 'Unable to construct distinct metadata corruption canaries.'
}

$badPairSupport = $pairRow.Replace(
    ' support=' + $supportIdentity + ' topology=',
    ' support=' + $badSupport + ' topology=')
$badPairTopology = $pairRow.Replace(
    ' topology=' + $expectedTopology + ' canonicalPlan=',
    ' topology=' + $badTopology + ' canonicalPlan=')
$badPairCanonical = $pairRow.Replace(
    ' canonicalPlan=' + $plan.canonical + ' fault=',
    ' canonicalPlan=' + $badCanonical + ' fault=')
$badPairFault = $pairRow.Replace(' fault=' + $faultId + ' ', ' fault=DREV_OPEN ')
$badFinalSupport = $passRow.Replace(
    ' support=' + $supportIdentity + ' topology=',
    ' support=' + $badSupport + ' topology=')
$badFinalTopology = $passRow.Replace(
    ' topology=' + $expectedTopology + ' canonicalPlan=',
    ' topology=' + $badTopology + ' canonicalPlan=')
$badFinalCanonical = $passRow.Replace(
    ' canonicalPlan=' + $plan.canonical + ' faults=1 ',
    ' canonicalPlan=' + $badCanonical + ' faults=1 ')

$cases = @(
    [pscustomobject]@{ Name = 'real-seed7-pair-and-pass'; Stdout = $realCaseOutput; TerminationProven = $true; ExitCode = 0; Expected = $true },
    [pscustomobject]@{ Name = 'pair-support-corruption'; Stdout = $badPairSupport + [Environment]::NewLine + $passRow; TerminationProven = $true; ExitCode = 0; Expected = $false },
    [pscustomobject]@{ Name = 'pair-topology-corruption'; Stdout = $badPairTopology + [Environment]::NewLine + $passRow; TerminationProven = $true; ExitCode = 0; Expected = $false },
    [pscustomobject]@{ Name = 'pair-canonical-corruption'; Stdout = $badPairCanonical + [Environment]::NewLine + $passRow; TerminationProven = $true; ExitCode = 0; Expected = $false },
    [pscustomobject]@{ Name = 'pair-fault-corruption'; Stdout = $badPairFault + [Environment]::NewLine + $passRow; TerminationProven = $true; ExitCode = 0; Expected = $false },
    [pscustomobject]@{ Name = 'duplicate-pair-row'; Stdout = $pairRow + [Environment]::NewLine + $pairRow + [Environment]::NewLine + $passRow; TerminationProven = $true; ExitCode = 0; Expected = $false },
    [pscustomobject]@{ Name = 'final-support-corruption'; Stdout = $pairRow + [Environment]::NewLine + $badFinalSupport; TerminationProven = $true; ExitCode = 0; Expected = $false },
    [pscustomobject]@{ Name = 'final-topology-corruption'; Stdout = $pairRow + [Environment]::NewLine + $badFinalTopology; TerminationProven = $true; ExitCode = 0; Expected = $false },
    [pscustomobject]@{ Name = 'final-canonical-corruption'; Stdout = $pairRow + [Environment]::NewLine + $badFinalCanonical; TerminationProven = $true; ExitCode = 0; Expected = $false },
    [pscustomobject]@{ Name = 'unproven-termination'; Stdout = $realCaseOutput; TerminationProven = $false; ExitCode = 0; Expected = $false },
    [pscustomobject]@{ Name = 'nonzero-child-exit'; Stdout = $realCaseOutput; TerminationProven = $true; ExitCode = 1; Expected = $false }
)
$results = New-Object Collections.Generic.List[object]
foreach ($case in $cases) {
    $actual = [bool](Test-Q30SensitivityCaseOutput -Stdout $case.Stdout -Seed '7' -FaultId $faultId -SupportIdentity $supportIdentity -Topology $expectedTopology -Canonical $plan.canonical -TerminationProven $case.TerminationProven -ExitCode $case.ExitCode)
    $passed = $actual -eq $case.Expected
    $results.Add([pscustomobject]@{
        name = $case.Name
        expectedAccepted = $case.Expected
        actualAccepted = $actual
        canaryPassed = $passed
    })
}
$failed = @($results | Where-Object { -not $_.canaryPassed })
if ($failed.Count -gt 0) {
    throw ('Sensitivity parser canaries failed: ' +
        [String]::Join(',', @($failed | ForEach-Object { $_.name })))
}

$verifierHash = (Get-FileHash -LiteralPath $VerifierPath -Algorithm SHA256).Hash.ToUpperInvariant()
$logHash = (Get-FileHash -LiteralPath $InputLogPath -Algorithm SHA256).Hash.ToUpperInvariant()
$evidence = [ordered]@{
    schema = 'q30-sensitivity-parser-canaries@1'
    result = 'PASS'
    scope = 'Lightweight AST-extracted verifier parser check; no Java process, build, or native gate launched.'
    source = [ordered]@{
        label = 'scripts/verify-current-contracts.ps1'
        sha256 = $verifierHash
        astParseErrorCount = 0
        extractedFunction = 'Test-Q30SensitivityCaseOutput'
    }
    inputLog = [ordered]@{
        label = 'native-scale-service-focused-r4.log'
        sha256 = $logHash
        seed = '7'
        fault = $faultId
        planRow = $planLine
        pairRow = $pairRow
        passRow = $passRow
    }
    expectedMetadata = [ordered]@{
        support = $supportIdentity
        topology = $expectedTopology
        canonical = $plan.canonical
    }
    canaries = @($results.ToArray())
    summary = [ordered]@{
        positiveAccepted = [bool]$results[0].actualAccepted
        negativeRejected = (@($results | Select-Object -Skip 1 | Where-Object { -not $_.actualAccepted }).Count -eq 10)
        negativeCount = 10
        allPassed = $failed.Count -eq 0
    }
}
$outputFullPath = [IO.Path]::GetFullPath($OutputPath)
[IO.File]::WriteAllText($outputFullPath,
    (ConvertTo-Json -InputObject $evidence -Depth 8) + [Environment]::NewLine,
    (New-Object Text.UTF8Encoding($false)))
Write-Output ('PASS: sensitivity parser canaries=' + $results.Count +
    ' negative=' + ($results.Count - 1) + ' sourceSha256=' + $verifierHash +
    ' inputLogSha256=' + $logHash)
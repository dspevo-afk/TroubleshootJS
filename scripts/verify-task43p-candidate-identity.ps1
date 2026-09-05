[CmdletBinding()]
param([string]$EvidenceDirectory = '')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'VerifierIsolation.psm1') -Force
. (Join-Path $PSScriptRoot 'Task43PCandidateIdentity.ps1')

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$candidateSha = Get-Task43PCandidateSha $repositoryRoot
$tempRoot = Get-VerifierFullPath ([IO.Path]::GetTempPath())
$runId = [Guid]::NewGuid().ToString('N')
$testRoot = Join-Path $tempRoot ('TroubleshootJS-task43p-candidate-test-' + $runId)
$fixtureRoot = Join-Path $testRoot 'checkout'
$shell = (Get-Command powershell.exe -ErrorAction Stop).Source
$git = (Get-Command git -ErrorAction Stop).Source
$results = [Collections.Generic.List[object]]::new()
$passed = $false

function Assert-CandidateTest([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw ('Candidate identity regression: ' + $Message) }
}

function Invoke-CandidateTestGit([string]$Root, [string[]]$Arguments) {
    $result = Invoke-VerifierBoundedProcess $git (@('-C', $Root,
        '-c', 'core.autocrlf=false', '-c', 'commit.gpgsign=false',
        '-c', ('core.hooksPath=' + (Join-Path $testRoot 'empty-hooks')),
        '-c', 'user.name=Task43P identity fixture',
        '-c', 'user.email=fixture@example.invalid') + $Arguments) 10000
    Assert-CandidateTest ($result.ExitCode -eq 0) ('fixture Git failed: ' + $result.Stderr)
    return $result.Stdout.Trim()
}

function Invoke-CandidateTestChild([string]$Name, [string]$Path,
        [string[]]$Arguments, [int]$ExpectedExit, [string]$RequiredOutput = '') {
    $result = Invoke-VerifierBoundedProcess $shell (@('-NoLogo', '-NoProfile',
        '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $Path) + $Arguments) 15000
    Assert-CandidateTest ($result.ExitCode -eq $ExpectedExit) (
        "$Name returned $($result.ExitCode), expected $ExpectedExit. " + $result.Stdout + $result.Stderr)
    Assert-CandidateTest (($result.Stdout + $result.Stderr).Contains($RequiredOutput)) (
        "$Name did not prove '$RequiredOutput'. " + $result.Stdout + $result.Stderr)
    $results.Add([ordered]@{ name = $Name; exit = [int]$result.ExitCode
        expectedExit = $ExpectedExit; passed = $true })
    Write-Host "PASS:candidate identity $Name (exit $ExpectedExit)"
}

function Invoke-CandidateTestPreflight([string]$Name, [string]$Root,
        [string]$Head, [switch]$DefaultCandidate) {
    $evidence = Join-Path $testRoot $Name
    $arguments = @('-MutationPreflight', '-EvidenceDirectory', $evidence)
    if (-not $DefaultCandidate) { $arguments += @('-ExpectedCandidateSha', $Head) }
    Invoke-CandidateTestChild $Name (Join-Path $Root 'scripts/verify-task43p-source-experiments.ps1') `
        $arguments 0 'repositoryUnchanged=True'
    $files = @(Get-ChildItem -LiteralPath $evidence -Filter 'task43p-source-experiments-*.json' -File)
    Assert-CandidateTest ($files.Count -eq 1) "$Name did not produce exactly one preflight artifact"
    $record = Get-Content -LiteralPath $files[0].FullName -Raw | ConvertFrom-Json
    Assert-CandidateTest ($record.candidateSha -ceq $Head -and
        $record.repositoryBefore.headSha -ceq $Head -and $record.repositoryAfter.headSha -ceq $Head -and
        -not $record.repositoryBefore.dirty -and -not $record.repositoryAfter.dirty -and
        $record.baselineSha -ceq '8bf442416a2fa2c0c9d654d1efa14e754c2b7ee7' -and
        $record.candidateSha -cne $record.baselineSha -and $record.runRootRemoved -and
        $record.exit -eq 0 -and $record.mutationPreflightPassed) "$Name lost clean candidate or baseline identity"
    $expectedIds = @('renderer-only-j1-1-plus-20px', 'renderer-only-j1-1-lead-plus-20px',
        'raw-copper-j1-1-endpoint-gap', 'raw-net-mismatch', 'solver-binding-j1-1-post-mismatch',
        'solver-detachable-c1-plus-identity-mismatch', 'package-mirror-mismatch',
        'internally-self-consistent-wrong-mapping', 'omitted-manifest-terminal',
        'snapshot-restore-resistance-current-omitted', 'public-remove-action-disabled')
    Assert-CandidateTest (($record.mutationPreflight.definitions.id -join '|') -ceq
        ($expectedIds -join '|')) "$Name changed the eleven required mutation cases"
    foreach ($item in $record.mutationPreflight.definitions) {
        Assert-CandidateTest ($item.anchorCount -eq 1 -and $item.exit -eq 0 -and
            $item.restoredExactly -and $item.beforeSha256 -ceq $item.restoredSha256 -and
            $item.beforeSha256 -cne $item.afterSha256) "$Name lost exact mutation/restoration for $($item.id)"
    }
    Assert-CandidateTest (($record.mutationPreflight.canaries.name -join '|') -ceq
        'zero-anchor|duplicate-anchor|overlapping-anchor|positive-bom-mixed-restore') `
        "$Name changed the four restoration/rejection canaries"
    Assert-CandidateTest (@($record.mutationPreflight.canaries | Where-Object {
        $_.exit -ne 0 -or -not $_.restoredExactly }).Count -eq 0) "$Name lost restoration/rejection proof"
    for ($index = 0; $index -lt 3; $index++) {
        $canary = $record.mutationPreflight.canaries[$index]
        Assert-CandidateTest ($canary.anchorCount -eq @(0, 2, 2)[$index] -and
            $canary.rejectionReasonMatched -and -not $canary.mutationChanged) `
            "$Name accepted a missing or ambiguous mutation anchor"
    }
    $bom = $record.mutationPreflight.canaries[3]
    Assert-CandidateTest ($bom.withBom -and $bom.mutationChanged -and $bom.anchorCount -eq 1) `
        "$Name lost the positive BOM mutation/restoration canary"
}

try {
    [void](New-Item -ItemType Directory -Path $fixtureRoot)
    [void](New-Item -ItemType Directory -Path (Join-Path $testRoot 'empty-hooks'))
    Assert-VerifierNoReparseAncestors $testRoot
    [void](Assert-VerifierPhysicalOwnedPath $tempRoot $testRoot -ValidateTree)
    [void](New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'scripts'))
    [void](New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'war'))
    [IO.File]::WriteAllText((Join-Path $fixtureRoot 'war/fixture.txt'), 'provenance-only fixture')
    foreach ($name in @('VerifierIsolation.psm1', 'Task43PCandidateIdentity.ps1',
            'Task43PPublicActionEvidence.ps1', 'Task43PRuntimeEvidence.ps1',
            'verify-task43p-source-experiments.ps1', 'verify-browser.ps1', 'preview.ps1')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) `
            -Destination (Join-Path $fixtureRoot ('scripts/' + $name))
    }
    # Copy only the actual source files consumed by the eleven preflight cases.
    # This Git fixture deliberately contains no production build or private data.
    $tokens = $null; $parseErrors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile(
        (Join-Path $PSScriptRoot 'verify-task43p-source-experiments.ps1'), [ref]$tokens, [ref]$parseErrors)
    $definitionStatements = @($ast.EndBlock.Statements | Where-Object {
        $_ -is [Management.Automation.Language.AssignmentStatementAst] -and
        $_.Left.Extent.Text -ceq '$definitions' })
    Assert-CandidateTest ($parseErrors.Count -eq 0 -and $definitionStatements.Count -eq 1) `
        'source experiment definitions were not one parsed assignment'
    . ([scriptblock]::Create($definitionStatements[0].Extent.Text))
    foreach ($relativePath in @($definitions.relativePath | Select-Object -Unique)) {
        $target = Join-Path $fixtureRoot $relativePath
        [void](New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force)
        Copy-Item -LiteralPath (Join-Path $repositoryRoot $relativePath) -Destination $target
    }
    # A real script file preserves PSScriptRoot for the extracted production
    # readers. In-memory ScriptBlock.Create would give them an empty script root.
    $stateReaders = [Collections.Generic.List[string]]::new()
    foreach ($entry in @(
        @{ File = 'verify-task43p-source-experiments.ps1'; Names = @('Get-RepositoryState', 'Test-RepositoryStateEqual') },
        @{ File = 'verify-browser.ps1'; Names = @('Get-Task43PRepositoryState') })) {
        $tokens = $null; $errors = $null
        $ast = [Management.Automation.Language.Parser]::ParseFile(
            (Join-Path $PSScriptRoot $entry.File), [ref]$tokens, [ref]$errors)
        Assert-CandidateTest ($errors.Count -eq 0) 'state reader did not parse'
        foreach ($name in $entry.Names) {
            $functions = @($ast.EndBlock.Statements | Where-Object {
                $_ -is [Management.Automation.Language.FunctionDefinitionAst] -and $_.Name -ceq $name })
            Assert-CandidateTest ($functions.Count -eq 1) 'state reader was not unique'
            $stateReaders.Add($functions[0].Extent.Text)
        }
    }
    [IO.File]::WriteAllText((Join-Path $fixtureRoot 'scripts/state-readers.ps1'),
        ($stateReaders -join "`r`n"))
    $stateCanary = @'
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'VerifierIsolation.psm1') -Force
. (Join-Path $PSScriptRoot 'Task43PCandidateIdentity.ps1')
. (Join-Path $PSScriptRoot 'state-readers.ps1')
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$source = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'src') -Recurse -File)[0].FullName
$originalBytes = [IO.File]::ReadAllBytes($source)
$statusFile = Join-Path $repositoryRoot 'untracked-identity-canary.txt'
foreach ($mutation in @('source-bytes', 'repository-status', 'repository-head')) {
    $before = Get-RepositoryState
    $script:Task43PCandidateSha = Get-Task43PCandidateSha $repositoryRoot
    $script:Task43PInvocationRepositoryState = $null
    $script:Task43PInvocationRepositoryState = Get-Task43PRepositoryState
    try {
        switch ($mutation) {
            'source-bytes' { [IO.File]::WriteAllBytes($source, [byte[]]($originalBytes + [byte]10)) }
            'repository-status' { [IO.File]::WriteAllText($statusFile, 'owned mutation canary') }
            'repository-head' {
                $git = (Get-Command git -ErrorAction Stop).Source
                $commit = Invoke-VerifierBoundedProcess $git @('-C', $repositoryRoot,
                    '-c', 'commit.gpgsign=false', '-c', ('core.hooksPath=' + (Join-Path $repositoryRoot 'empty-hooks')),
                    '-c', 'user.name=Task43P fixture', '-c', 'user.email=fixture@example.invalid',
                    'commit', '--allow-empty', '-m', 'Candidate changed during state canary') 10000
                if ($commit.ExitCode -ne 0) { throw 'fixture candidate mutation commit failed' }
            }
        }
        $after = Get-RepositoryState
        if (Test-RepositoryStateEqual $before $after) { throw "source harness accepted $mutation" }
        $rejected = $false
        try { [void](Get-Task43PRepositoryState) } catch {
            $rejected = (Test-VerifierInfrastructureError $_) -and
                $_.Exception.Message.Contains('frozen invocation candidate')
        }
        if (-not $rejected) { throw "browser state capture accepted $mutation" }
        Write-Host "PASS:actual source/browser state readers reject $mutation"
    } finally {
        [IO.File]::WriteAllBytes($source, $originalBytes)
        if (Test-Path -LiteralPath $statusFile) { Remove-Item -LiteralPath $statusFile }
    }
}
'@
    [IO.File]::WriteAllText((Join-Path $fixtureRoot 'scripts/state-canary.ps1'), $stateCanary)
    [void](Invoke-CandidateTestGit $fixtureRoot @('init', '--quiet',
        ('--template=' + (Join-Path $testRoot 'empty-hooks'))))
    [void](Invoke-CandidateTestGit $fixtureRoot @('add', '--', 'scripts', 'src', 'war'))
    [void](Invoke-CandidateTestGit $fixtureRoot @('commit', '--quiet', '-m', 'Commit current candidate scripts and anchors'))
    $firstHead = Invoke-CandidateTestGit $fixtureRoot @('rev-parse', 'HEAD')
    Invoke-CandidateTestPreflight 'clean-committed' $fixtureRoot $firstHead
    [void](Invoke-CandidateTestGit $fixtureRoot @('commit', '--quiet', '--allow-empty', '-m', 'Authorized descendant checkpoint'))
    $secondHead = Invoke-CandidateTestGit $fixtureRoot @('rev-parse', 'HEAD')
    Assert-CandidateTest ($firstHead -cne $secondHead) 'fixture commit did not advance HEAD'
    Invoke-CandidateTestPreflight 'commit-does-not-invalidate' $fixtureRoot $secondHead -DefaultCandidate
    $ciRoot = Join-Path $testRoot 'shallow-ci'
    [void](Invoke-CandidateTestGit $testRoot @('clone', '--quiet', '--no-local', '--no-hardlinks', '--depth', '1',
        $fixtureRoot, $ciRoot))
    [void](Invoke-CandidateTestGit $ciRoot @('checkout', '--quiet', '--detach', 'HEAD'))
    Assert-CandidateTest ((Invoke-CandidateTestGit $ciRoot @('rev-parse', '--is-shallow-repository')) -ceq 'true') `
        'CI fixture was not an actual shallow checkout'
    Invoke-CandidateTestPreflight 'detached-shallow-ci' $ciRoot $secondHead
    foreach ($wrong in @($firstHead, ('0' * 40), 'not-a-sha')) {
        Invoke-CandidateTestChild ('reject-expected-' + $wrong) `
            (Join-Path $fixtureRoot 'scripts/verify-task43p-source-experiments.ps1') `
            @('-MutationPreflight', '-ExpectedCandidateSha', $wrong) 2 'explicit expected candidate SHA'
    }
    foreach ($wrong in @('', ' ', @($secondHead))) {
        $rejected = $false
        try { [void](Get-Task43PCandidateSha $fixtureRoot $wrong) } catch {
            $rejected = Test-VerifierInfrastructureError $_
        }
        Assert-CandidateTest $rejected 'empty, whitespace or array expected candidate was accepted'
    }
    Invoke-CandidateTestChild 'browser-rejects-parent-mismatch' `
        (Join-Path $fixtureRoot 'scripts/verify-browser.ps1') `
        @('-Task43PForcedNegative', '-ExpectedCandidateSha', $firstHead) 2 'explicit expected candidate SHA'
    Invoke-CandidateTestChild 'browser-rejects-provenance-mismatch' `
        (Join-Path $fixtureRoot 'scripts/verify-browser.ps1') @('-Task43P',
        '-ExpectedCandidateSha', $secondHead, '-ExpectedExecutionProvenanceDigest', ('0' * 64)) `
        2 'expected candidate provenance digest'
    Invoke-CandidateTestChild 'source-and-repository-mutation' `
        (Join-Path $fixtureRoot 'scripts/state-canary.ps1') @() 0 `
        'PASS:actual source/browser state readers reject repository-head'
    [void](Get-Task43PCandidateSha $repositoryRoot $candidateSha)
    $passed = $true
} catch {
    [Console]::Error.WriteLine('FAIL:candidate identity regression: ' + $_.Exception.Message)
} finally {
    if ($passed) {
        try { Remove-VerifierOwnedTree $tempRoot $testRoot } catch {
            $passed = $false
            [Console]::Error.WriteLine('FAIL:candidate fixture cleanup: ' + $_.Exception.Message)
        }
    }
    $evidenceRoot = if ([String]::IsNullOrWhiteSpace($EvidenceDirectory)) {
        Join-Path $tempRoot 'TroubleshootJS/verify/candidate-identity'
    } else { Get-VerifierFullPath $EvidenceDirectory }
    try {
        [void](New-Item -ItemType Directory -Path $evidenceRoot -Force)
        $evidencePath = Join-Path $evidenceRoot ('candidate-identity-' + $runId + '.json')
        $record = [ordered]@{ protocol = 'troubleshootjs-candidate-identity-regressions-v1'
            candidateSha = $candidateSha; runId = $runId; passed = $passed
            fixtureRemoved = -not (Test-Path -LiteralPath $testRoot); cases = @($results.ToArray()) }
        [IO.File]::WriteAllText($evidencePath, ($record | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
        Write-Host ('CANDIDATE IDENTITY evidence=' + $evidencePath)
    } catch {
        $passed = $false
        [Console]::Error.WriteLine('FAIL:candidate regression evidence: ' + $_.Exception.Message)
    }
}
if (-not $passed) { Write-Host ('Retained candidate fixture: ' + $testRoot); exit 2 }
Write-Host 'PASS:candidate identity committed/descendant/shallow-CI, explicit identity, provenance, mutation, and anchor contracts'
exit 0

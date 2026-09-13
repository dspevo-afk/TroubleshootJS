[CmdletBinding()]
param(
    [string]$EvidenceRoot = (Join-Path $PSScriptRoot '../docs/task-evidence/Q15'),
    [string]$Python = 'python'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'VerifierIsolation.psm1') -Force

# Use the maintained report readers without executing their browser wrappers.
$readers = @(
    @{ File='verify-a03-browser.ps1'; Names=@('Get-ExactReportText','Test-JsonReport',
        'Test-ControlledReport','Test-A08Report','Test-Task41Report') },
    @{ File='verify-e01-e03-evidence.ps1'; Names=@('Test-Number','Test-Relay') }
)
foreach ($reader in $readers) {
    $tokens=$null; $parseErrors=$null
    $ast=[Management.Automation.Language.Parser]::ParseFile(
        (Join-Path $PSScriptRoot $reader.File),[ref]$tokens,[ref]$parseErrors)
    if ($parseErrors.Count) { throw "Reader syntax error: $($reader.File)" }
    foreach ($name in $reader.Names) {
        $functions=@($ast.FindAll({param($node)
            $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name
        },$true))
        if ($functions.Count -ne 1) { throw "Missing unique reader: $name" }
        . ([ScriptBlock]::Create($functions[0].Extent.Text))
    }
}
$relay=Get-Content -LiteralPath (Join-Path $EvidenceRoot 'e03-report.json') -Raw
$mutation=Get-Content -LiteralPath (Join-Path $EvidenceRoot 'a08-report.json') -Raw
$diagnostic=Get-Content -LiteralPath (Join-Path $EvidenceRoot 'task41-report.txt') -Raw
$construction=Get-Content -LiteralPath (Join-Path $EvidenceRoot 'task49-report.json') -Raw
if (-not (Test-Relay $relay) -or -not (Test-A08Report $mutation) -or
        -not (Test-Task41Report $diagnostic) -or $diagnostic -notmatch '(?:^|;)routes=20(?:;|$)' -or
        -not (Test-ControlledReport $construction 'TSJ-TASK49-2')) {
    throw 'Incomplete current electrical, mutation, diagnostic or construction regression.'
}
if ((Test-Relay '{"status":"PASS"}') -or (Test-A08Report '{}') -or
        (Test-Task41Report 'routes=20;result=PASS') -or
        (Test-ControlledReport '{"protocol":"TSJ-TASK49-2","status":"PASS"}' 'TSJ-TASK49-2') -or
        (Test-Relay ($relay.Replace('"NMOS"','"BJT"')))) {
    throw 'Maintained regression reader accepted a negative canary.'
}
& $Python (Join-Path $PSScriptRoot '../tests/contracts/q15_evidence_contract.py') $EvidenceRoot
if ($LASTEXITCODE -ne 0) { throw 'Q15 evidence reader failed.' }
& $Python (Join-Path $PSScriptRoot '../tests/contracts/a10_generation_report.py') --report (Join-Path $EvidenceRoot 'a10-report.json')
if ($LASTEXITCODE -ne 0) { throw 'A10 evidence reader failed.' }
Write-Output 'PASS: Q15, A10, E03, A08, twenty Task41 routes, Task49 and negative report canaries.'

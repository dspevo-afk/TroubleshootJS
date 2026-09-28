param([string]$Repo,[string]$Report)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $Repo 'scripts/VerifierIsolation.psm1') -Force -DisableNameChecking
$parseErrors=$null; $tokens=$null
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $Repo 'scripts/verify-a03-browser.ps1'),[ref]$tokens,[ref]$parseErrors)
if($parseErrors.Count -ne 0){throw 'Reader parse failed'}
foreach($name in @('Get-ExactReportText','Test-A07FiniteNumber','Test-A07Report')) {
  $fn=$ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name},$true)
  if($null -eq $fn){throw "Missing maintained function $name"}
  Invoke-Expression $fn.Extent.Text
}
$raw=[IO.File]::ReadAllText($Report)
if(-not (Test-A07Report $raw)){throw 'Strict maintained A07 reader rejected report'}
$reportValue=$raw | ConvertFrom-Json
$reportValue.cleanup='FAIL'
if(Test-A07Report ($reportValue | ConvertTo-Json -Depth 100 -Compress)){throw 'Cleanup negative canary was accepted'}
$reportValue=$raw | ConvertFrom-Json
$reportValue.runtimeCases[0].status='FAIL'
if(Test-A07Report ($reportValue | ConvertTo-Json -Depth 100 -Compress)){throw 'Runtime negative canary was accepted'}
$reportValue=$raw | ConvertFrom-Json
$reportValue.models.rows[0].value=1000000
if(Test-A07Report ($reportValue | ConvertTo-Json -Depth 100 -Compress)){throw 'Physics negative canary was accepted'}
'PASS: exact maintained Test-A07Report; three negative corruption canaries'

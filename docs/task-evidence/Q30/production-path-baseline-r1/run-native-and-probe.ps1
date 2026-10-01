$ErrorActionPreference = 'Stop'
$audit = $PSScriptRoot
$baseline = Join-Path (Split-Path $audit -Parent) 'baseline'
$jdk = '<jdk8>'
Import-Module (Join-Path $baseline 'scripts/VerifierIsolation.psm1') -Force
$classes = Join-Path $audit 'classes-final'
$empty = Join-Path $audit 'empty-sourcepath'
New-Item -ItemType Directory -Path $classes,$empty -Force | Out-Null
$stub = Join-Path $audit 'src/PhysicalSpecificationDeveloperVerifier.java'
[IO.File]::WriteAllText($stub, @'
package com.lushprojects.circuitjs1.client;
final class PhysicalSpecificationDeveloperVerifier {
    static void verify(CirSim sim) {
        throw new UnsupportedOperationException("Task35 verifier requires the separate GWT production gate");
    }
}
'@)
$sources = @(Get-ChildItem -LiteralPath (Join-Path $baseline 'src/com/lushprojects/circuitjs1/client') -Filter '*.java' | Where-Object Name -ne 'PhysicalSpecificationDeveloperVerifier.java' | Sort-Object Name | ForEach-Object FullName)
$sources += $stub
$sources += Join-Path $audit 'src/com/lushprojects/circuitjs1/client/Rb30PlanAuditProbe.java'
$argsFile = Join-Path $audit 'sources.txt'
[IO.File]::WriteAllLines($argsFile, @($sources | ForEach-Object { '"' + $_.Replace('\','/') + '"' }), (New-Object Text.UTF8Encoding($false)))
$cp = (Join-Path $baseline '.tools/gwt-2.7.0/gwt-user-2.7.0.jar') + ';' + (Join-Path $baseline '.tools/gwt-2.7.0/gwt-dev-2.7.0.jar')
$compiled = Invoke-VerifierBoundedProcess (Join-Path $jdk 'bin/javac.exe') @('-source','7','-target','7','-encoding','UTF-8','-classpath',$cp,'-sourcepath',$empty,'-d',$classes,('@'+$argsFile)) 60000
$compiled | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $audit 'plan-probe-final-compile.json')
if ($compiled.ExitCode -ne 0 -or -not $compiled.TerminationProven) { throw ('Compile failed: ' + $compiled.Stderr) }
$tested = Invoke-VerifierBoundedProcess (Join-Path $jdk 'bin/java.exe') @('-ea','-cp',($classes+';'+$cp),'com.lushprojects.circuitjs1.client.Rb30PlanAuditProbe') 15000
$tested | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $audit 'plan-probe-final-run.json')
[IO.File]::WriteAllText((Join-Path $audit 'plan-probe-results.log'),$tested.Stdout)
if ($tested.ExitCode -ne 0 -or -not $tested.TerminationProven) { throw ('Probe failed: ' + $tested.Stderr) }
Write-Host $tested.Stdout
& (Join-Path $baseline 'scripts/verify-current-contracts.ps1') -JavaHome $jdk -PythonExe '<python>' -Suite @('Q30NormalExecutionPolicyContractTest','Q30GenerationRequestContractTest','Q30GenerationMeasurementBudgetContractTest','Q30PlanContractTest') *> (Join-Path $audit 'native-focused.log')
$nativeExit=$LASTEXITCODE
[IO.File]::WriteAllText((Join-Path $audit 'native-exit.txt'),[string]$nativeExit)
Get-Content (Join-Path $audit 'native-focused.log')
exit $nativeExit

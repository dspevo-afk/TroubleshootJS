[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('baseline','candidate','contracts','build')][string]$Phase,
      [Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$packetRoot=$PSScriptRoot
$repoRoot=(Resolve-Path -LiteralPath (Join-Path $packetRoot '../../../..')).Path
$inputPhase=if($Phase -eq 'baseline'){'baseline'}else{'candidate'}
foreach($suffix in @('-input-audit-before.txt','-input-audit-after.txt','-process-launch.json','-process-cleanup.json','-timing.json','.txt','-receipt.txt')){
    if(Test-Path -LiteralPath (Join-Path $packetRoot ($Phase+$suffix))){throw 'Preserve prior phase artifacts before retry'}
}
function Audit-Inputs([string]$Side){
    & $PythonExe -X utf8 -B (Join-Path $packetRoot 'inputs.py') audit $inputPhase *> (Join-Path $packetRoot ($Phase+'-input-audit-'+$Side+'.txt'))
    if($LASTEXITCODE -ne 0){throw ('Phase input audit failed: '+$Side)}
}
Audit-Inputs 'before'
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object {
    $p=Get-Process -Id $_ -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase=$Phase;drivers=$drivers;scope='Captured parent/current births before maintained reference phase'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $packetRoot ($Phase+'-process-launch.json'))
$watch=[Diagnostics.Stopwatch]::StartNew();$code=2;$failure=$null
try {
    Push-Location $repoRoot
    try {
        if($Phase -eq 'build'){
            & ./scripts/build.ps1 -JavaHome $JavaHome -Style OBF -Target Compile -ProcessTimeoutSeconds 900 *> (Join-Path $packetRoot ($Phase+'.txt'))
        } else {
            if($Phase -eq 'contracts'){
                $suites=@('A04PhysicalDeclarationContractTest','A04ConstructionContractTest','ControlledIndicatorAssemblyContractTest','PcbNormalPackingIdentityContractTest','U06SessionSaveContractTest','PlayerSessionFingerprintContractTest','PcbCompactnessContractTest','A02ReplayContractTest','PcbPackingProbeReceiptContractTest','PcbPackingMetricsContractTest','PcbSilkscreenScaleContractTest','PcbScreenLabelLayoutContractTest','E05IsolationGeometryContractTest','D01DiagnosticContractTest','Rb56ModelIdentityContractTest')
            } else {
                . (Join-Path $packetRoot '../normal-packing-r2/suite_transport.ps1')
                $suites=@(Get-NormalPackingR2SuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $packetRoot '../normal-packing-r2/routes-suites.json') -Raw))
                if($suites.Count -ne 8){throw 'Expected unchanged eight fixed44 JVM groups'}
            }
            & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite $suites -ReceiptOutputPath (Join-Path $packetRoot ($Phase+'-receipt.txt')) *> (Join-Path $packetRoot ($Phase+'.txt'))
        }
        $code=[int]$LASTEXITCODE
    } finally {Pop-Location}
} catch {$failure=$_.Exception.Message;$code=2} finally {
    $watch.Stop()
    @{phase=$Phase;status=$(if($code -eq 0){'PASS'}else{'FAIL'});exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED';failure=$failure}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $packetRoot ($Phase+'-timing.json'))
}
Audit-Inputs 'after'
if($code -ne 0){throw ('Maintained reference phase failed: '+$Phase+' exit='+$code)}
Write-Output ('PASS maintained reference phase '+$Phase)

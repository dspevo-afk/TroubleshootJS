[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('baseline','candidate')][string]$Phase,
      [Parameter(Mandatory=$true)][string]$PointerPath)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$packetRoot=$PSScriptRoot
$repoRoot=(Resolve-Path -LiteralPath (Join-Path $packetRoot '../../../..')).Path
$pointer=Get-Content -LiteralPath $PointerPath -Raw | ConvertFrom-Json
$python=[string]$pointer.tools.python
$javaHome=[string]$pointer.tools.javaHome
$nativePowerShell=[string]$pointer.tools.powershell
$inputAudit=Join-Path $packetRoot 'audit_phase_inputs.py'
$before=Join-Path $packetRoot ($Phase+'-input-audit-before.txt')
$after=Join-Path $packetRoot ($Phase+'-input-audit-after.txt')
$launch=Join-Path $packetRoot ($Phase+'-process-launch.json')
$timing=Join-Path $packetRoot ($Phase+'-timing.json')
$output=Join-Path $packetRoot ($Phase+'.txt')
$stderrOutput=Join-Path $packetRoot ($Phase+'-stderr.txt')
$receipt=Join-Path $packetRoot ($Phase+'-receipt.txt')
foreach($path in @($before,$after,$launch,$timing,$output,$stderrOutput,$receipt)) {
    if(Test-Path -LiteralPath $path) { throw ('Preserve prior phase artifact: '+(Split-Path -Leaf $path)) }
}
& $python -X utf8 -B $inputAudit $Phase *> $before
if($LASTEXITCODE -ne 0) { throw 'Phase input audit failed before native execution' }
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$driverIds=@([int]$mine.ParentProcessId,[int]$PID) | Select-Object -Unique
$drivers=@(foreach($id in $driverIds) {
    $p=Get-Process -Id $id -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName;role=$(if($id -eq $PID){'phase-driver'}else{'phase-parent'})}
})
$hashGen=(Get-FileHash -LiteralPath (Join-Path $repoRoot 'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java') -Algorithm SHA256).Hash.ToLowerInvariant()
$hashTest=(Get-FileHash -LiteralPath (Join-Path $repoRoot 'tests/contracts/PcbFunctionalGroupingContractTest.java') -Algorithm SHA256).Hash.ToLowerInvariant()
@{phase=$Phase;drivers=$drivers;generatorSha256=$hashGen;functionalGroupingSha256=$hashTest;
  command='scripts/verify-current-contracts.ps1 -Suite @(''PcbFunctionalGroupingContractTest'')';
  childBudgetMilliseconds=60000;scope='Parent/current and maintained verifier process births; no process termination'} |
    ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $launch -Encoding utf8
Write-Output ('ACTUAL_PHASE_START phase='+$Phase+' pid='+$PID+' generatorSha256='+$hashGen)
$watch=[Diagnostics.Stopwatch]::StartNew();$code=2;$failure=$null
$verifier=$null;$stdoutTask=$null;$stderrTask=$null;$stdout='';$stderr=''
$auditAfterCode=2
try {
    $psi=[Diagnostics.ProcessStartInfo]::new()
    $psi.FileName=$nativePowerShell
    $psi.UseShellExecute=$false
    $psi.CreateNoWindow=$true
    $psi.RedirectStandardOutput=$true
    $psi.RedirectStandardError=$true
    foreach($arg in @('-NoLogo','-NoProfile','-File',(Join-Path $repoRoot 'scripts/verify-current-contracts.ps1'),
                      '-JavaHome',$javaHome,'-PythonExe',$python,'-Suite','PcbFunctionalGroupingContractTest',
                      '-ReceiptOutputPath',$receipt)) {
        [void]$psi.ArgumentList.Add([string]$arg)
    }
    $verifier=[Diagnostics.Process]::new()
    $verifier.StartInfo=$psi
    if(-not $verifier.Start()) { throw 'Maintained verifier process did not start' }
    $verifierBirth=@{pid=$verifier.Id;startTicks=[string]$verifier.StartTime.ToUniversalTime().Ticks;
        executable=[IO.Path]::GetFileNameWithoutExtension($nativePowerShell);role='maintained-verifier'}
    $drivers=@($drivers)+@($verifierBirth)
    @{phase=$Phase;drivers=$drivers;generatorSha256=$hashGen;functionalGroupingSha256=$hashTest;
      command='scripts/verify-current-contracts.ps1 -Suite @(''PcbFunctionalGroupingContractTest'')';
      childBudgetMilliseconds=60000;scope='Parent/current/maintained verifier process births; no process termination'} |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $launch -Encoding utf8
    $stdoutTask=$verifier.StandardOutput.ReadToEndAsync()
    $stderrTask=$verifier.StandardError.ReadToEndAsync()
    $verifier.WaitForExit()
    $stdout=$stdoutTask.GetAwaiter().GetResult()
    $stderr=$stderrTask.GetAwaiter().GetResult()
    $code=[int]$verifier.ExitCode
} catch {
    $failure=$_.Exception.Message
    $code=2
} finally {
    $watch.Stop()
    $cleanupWatch=[Diagnostics.Stopwatch]::StartNew()
    [IO.File]::WriteAllText($output,$stdout,(New-Object Text.UTF8Encoding($false)))
    [IO.File]::WriteAllText($stderrOutput,$stderr,(New-Object Text.UTF8Encoding($false)))
    if($failure) {
        [IO.File]::AppendAllText($output,([Environment]::NewLine+'PHASE_DRIVER_FAILURE: '+$failure+[Environment]::NewLine),(New-Object Text.UTF8Encoding($false)))
    }
    & $python -X utf8 -B $inputAudit $Phase *> $after
    $auditAfterCode=[int]$LASTEXITCODE
    $receiptExists=Test-Path -LiteralPath $receipt
    $receiptHash=$null
    if($receiptExists) {$receiptHash=(Get-FileHash -LiteralPath $receipt -Algorithm SHA256).Hash.ToLowerInvariant()}
    $outputHash=(Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
    $stderrHash=(Get-FileHash -LiteralPath $stderrOutput -Algorithm SHA256).Hash.ToLowerInvariant()
    $cleanupWatch.Stop()
    @{phase=$Phase;nativeStatus=$(if($code -eq 0){'PASS'}else{'FAIL'});actualMaintainedDriverExitCode=$code;
      actualFunctionalJvmFailureExitCode=$(if($stdout -match 'Current contract did not provide a qualified result:.*exit 1'){1}else{$null});
      operationElapsedSeconds=$watch.Elapsed.TotalSeconds;postExitCaptureAndInputAuditSeconds=$cleanupWatch.Elapsed.TotalSeconds;
      maintainedVerifierOwnCleanupIncludedInOperation=$true;inputAuditAfterExitCode=$auditAfterCode;
      receiptExists=$receiptExists;receiptSha256=$receiptHash;stdoutSha256=$outputHash;stderrSha256=$stderrHash;failure=$failure} |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $timing -Encoding utf8
}
if($auditAfterCode -ne 0) { throw 'Phase input audit failed after native execution' }
exit $code

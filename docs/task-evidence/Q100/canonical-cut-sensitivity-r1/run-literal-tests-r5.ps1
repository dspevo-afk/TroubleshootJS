param([Parameter(Mandatory=$true)][string]$RepositoryRoot,[Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$encoding=[Text.UTF8Encoding]::new($false)
$self=Get-Process -Id $PID
$owner=[ordered]@{pid=$PID;startTicks=[string]$self.StartTime.ToUniversalTime().Ticks;executable=$self.Path;script=$PSCommandPath;scope='P7_LITERAL_ONLY'}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'literal-tests-r5-owner.json'),(ConvertTo-Json $owner)+[char]10,$encoding)
$module=Join-Path $RepositoryRoot 'scripts/VerifierIsolation.psm1'
if((Get-FileHash -LiteralPath $module -Algorithm SHA256).Hash.ToLowerInvariant()-cne'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c'){throw 'MODULE_HASH'}
if((Get-FileHash -LiteralPath $PythonExe -Algorithm SHA256).Hash.ToLowerInvariant()-cne'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec'){throw 'PYTHON_HASH'}
Import-Module $module -Force -DisableNameChecking
$test=Join-Path $PSScriptRoot 'test_cut_sensitivity.py'
$oracle=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../canonical-prefix-capture-r1/oracles-r1'))
$watch=[Diagnostics.Stopwatch]::StartNew()
$record=Invoke-VerifierBoundedProcess $PythonExe @('-B',$test,'--oracle-directory',$oracle,'-v') 180000
$value=[ordered]@{wallMs=$watch.Elapsed.TotalMilliseconds;process=$record;testSha256=(Get-FileHash -LiteralPath $test -Algorithm SHA256).Hash.ToLowerInvariant();coreSha256=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'cut_sensitivity_core.py') -Algorithm SHA256).Hash.ToLowerInvariant()}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'literal-tests-r5-process.json'),(ConvertTo-Json $value -Depth 15)+[char]10,$encoding)
if($record.ExitCode-ne0-or-not$record.TerminationProven){throw 'LITERAL_TESTS_FAILED'}
$record.Stderr

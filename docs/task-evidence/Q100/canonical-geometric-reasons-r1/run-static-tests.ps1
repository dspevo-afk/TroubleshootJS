param(
 [Parameter(Mandatory=$true)][string]$RepositoryRoot,
 [Parameter(Mandatory=$true)][string]$PythonExe,
 [Parameter(Mandatory=$true)][string]$OutputPath
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if(-not[IO.Path]::IsPathRooted($RepositoryRoot)-or-not[IO.Path]::IsPathRooted($PythonExe)){throw 'ABSOLUTE_REQUIRED'}
$repo=[IO.Path]::GetFullPath($RepositoryRoot)
$python=[IO.Path]::GetFullPath($PythonExe)
$utf8=[Text.UTF8Encoding]::new($false)
function Hash([string]$p){(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()}
function Need([bool]$ok,[string]$why){if(-not $ok){throw $why}}
Need ($PSVersionTable.PSVersion.Major-eq5-and$PSVersionTable.PSVersion.Minor-eq1) 'SELECTED_PS51_REQUIRED'
Need ((Hash (Get-Process -Id $PID).Path)-ceq'60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f') 'PS51_PIN'
$module=Join-Path $repo 'scripts/VerifierIsolation.psm1'
Need ((Hash $module)-ceq'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c') 'ISOLATION_PIN'
Import-Module $module -Force -DisableNameChecking
foreach($p in @($python,$PSCommandPath,(Join-Path $PSScriptRoot 'test_overlay.py'),(Join-Path $PSScriptRoot 'build_overlay.py'))){Assert-VerifierNoReparseAncestors $p}
Need ((Hash $python)-ceq'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec') 'PYTHON_PIN'
Need ((Hash (Join-Path $PSScriptRoot 'build_overlay.py'))-ceq'e62fbb833e3751ff2f53b38958b5b8ddf946ff210b18cfcf3b5052e19ab15ec0') 'BUILDER_PIN'
Need ([IO.Path]::IsPathRooted($OutputPath)) 'OUTPUT_ABSOLUTE'
$OutputPath=[IO.Path]::GetFullPath($OutputPath)
Assert-VerifierNoReparseAncestors ([IO.Path]::GetDirectoryName($OutputPath))
Need (-not(Test-Path -LiteralPath $OutputPath)) 'OUTPUT_EXISTS'
$before=[ordered]@{builder=(Hash (Join-Path $PSScriptRoot 'build_overlay.py'));tests=(Hash (Join-Path $PSScriptRoot 'test_overlay.py'));controller=(Hash $PSCommandPath)}
$watch=[Diagnostics.Stopwatch]::StartNew()
$proc=Invoke-VerifierBoundedProcess $python @('-I','-B',(Join-Path $PSScriptRoot 'test_overlay.py'),$repo) 60000
$watch.Stop()
$after=[ordered]@{builder=(Hash (Join-Path $PSScriptRoot 'build_overlay.py'));tests=(Hash (Join-Path $PSScriptRoot 'test_overlay.py'));controller=(Hash $PSCommandPath)}
$same=$before.builder-ceq$after.builder-and$before.tests-ceq$after.tests-and$before.controller-ceq$after.controller
$pass=$proc.ExitCode-eq0-and$proc.TerminationProven-and$same-and-not(Test-Path -LiteralPath $proc.ProcessRoot)
$result=[ordered]@{schema='tsj-q100-geometric-static-tests-v1';status=$(if($pass){'PASS_STATIC_ONLY'}else{'FAIL'});elapsedMs=$watch.Elapsed.TotalMilliseconds;sourceBefore=$before;sourceAfter=$after;process=$proc}
$bytes=$utf8.GetBytes((ConvertTo-Json -InputObject $result -Depth 20)+[char]10)
$out=[IO.File]::Open($OutputPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
try{$out.Write($bytes,0,$bytes.Length);$out.Flush()}finally{$out.Dispose()}
Need $pass 'STATIC_TESTS_FAILED'
Write-Output 'PASS_P8_STATIC_OVERLAY_TESTS_ONLY'

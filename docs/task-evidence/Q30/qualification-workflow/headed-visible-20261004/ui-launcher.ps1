$ErrorActionPreference='Stop'
$workspace='<WORKSPACE>'
$taskRoot=Join-Path ([IO.Path]::GetTempPath()) ('q30-headed-visible-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskRoot | Out-Null
$taskRoot | Set-Content -LiteralPath (Join-Path $workspace 'headed-ui-root.txt')
$driver=Join-Path $workspace 'headed_q30_ui.py'
Copy-Item -LiteralPath $driver -Destination (Join-Path $taskRoot 'executed-headed_q30_ui.py')
$controller=Get-CimInstance Win32_Process -Filter "ProcessId=$PID"
@{pid=$PID;created=$controller.CreationDate.ToUniversalTime().ToString('o');executable=$controller.ExecutablePath;commandLine=$controller.CommandLine;driverSha256=(Get-FileHash -LiteralPath $driver -Algorithm SHA256).Hash.ToLowerInvariant();headed=$true;userScope='Visible 20/30/40 repair/retest through ordinary UI only; no build/full matrix'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRoot 'bounded-controller-identity.json')
$env:PYTHONDONTWRITEBYTECODE='1'
$env:PYTHONPATH='<DEPS>'
Import-Module '<REPO>\scripts\VerifierIsolation.psm1' -Force
$started=[DateTime]::UtcNow
$result=Invoke-VerifierBoundedProcess 'C:\Program Files\WindowsApps\PythonSoftwareFoundation.Python.3.13_3.13.3824.0_x64__qbz5n2kfra8p0\python3.13.exe' @('-B','-u',$driver,$taskRoot,'<PRIVATE_ROOT>') 3700000
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $taskRoot 'bounded-process-result.json')
@{startedUtc=$started.ToString('o');finishedUtc=[DateTime]::UtcNow.ToString('o');exitCode=$result.ExitCode;terminationProven=$result.TerminationProven} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRoot 'bounded-command-result.json')
Write-Output $taskRoot
Write-Output $result.Stdout
if($result.ExitCode -ne 0){throw 'Headed UI host did not close successfully'}

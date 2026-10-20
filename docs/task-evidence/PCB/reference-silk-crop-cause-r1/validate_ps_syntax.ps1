[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$files = @('run_phase.ps1','observe_phase_exit.ps1')
foreach ($relative in $files) {
    $path = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot $relative))
    $tokens = $null
    $errors = $null
    [Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$errors) | Out-Null
    if ($errors.Count -gt 0) { throw ($relative + ': ' + (($errors | ForEach-Object Message) -join '; ')) }
}
Write-Output 'PASS PowerShell syntax files=2'

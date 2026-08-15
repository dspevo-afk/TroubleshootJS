[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateRange(1, 65535)]
    [int]$Port = 8888
)

& (Join-Path $PSScriptRoot 'build.ps1') -JavaHome $JavaHome -Target Dev -Port $Port
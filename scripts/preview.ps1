[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8899
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$webRoot = Join-Path $repositoryRoot 'war'
if (-not (Test-Path (Join-Path $webRoot 'circuitjs1\circuitjs1.nocache.js') -PathType Leaf)) {
    throw 'Production output is missing. Run .\scripts\build.ps1 with JDK 8 first.'
}

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
$listener.Start()
Write-Host "TroubleshootJS preview: http://127.0.0.1:$Port/circuitjs.html?tsjChallenge=led&seed=3"
try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $relativePath = [Uri]::UnescapeDataString($context.Request.Url.AbsolutePath.TrimStart('/'))
        if ([String]::IsNullOrEmpty($relativePath)) { $relativePath = 'circuitjs.html' }
        $file = [IO.Path]::GetFullPath((Join-Path $webRoot $relativePath))
        if (-not $file.StartsWith($webRoot, [StringComparison]::OrdinalIgnoreCase) -or
                -not (Test-Path $file -PathType Leaf)) {
            $context.Response.StatusCode = 404
        } else {
            $bytes = [IO.File]::ReadAllBytes($file)
            $context.Response.StatusCode = 200
            $context.Response.ContentLength64 = $bytes.Length
            $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
        }
        $context.Response.Close()
    }
} finally {
    $listener.Close()
}

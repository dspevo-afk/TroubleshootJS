[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8899
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$webRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot 'war'))
if (-not (Test-Path (Join-Path $webRoot 'circuitjs1\circuitjs1.nocache.js') -PathType Leaf)) {
    throw 'Production output is missing. Run .\scripts\build.ps1 with JDK 8 first.'
}

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
$listener.Start()
Write-Host "TroubleshootJS preview: http://127.0.0.1:$Port/circuitjs.html?tsjChallenge=led&seed=3"
try {
    while ($listener.IsListening) {
        $context = $null
        try {
            $context = $listener.GetContext()
            $relativePath = [Uri]::UnescapeDataString($context.Request.Url.AbsolutePath.TrimStart('/'))
            if ([String]::IsNullOrEmpty($relativePath)) { $relativePath = 'circuitjs.html' }
            $file = [IO.Path]::GetFullPath((Join-Path $webRoot $relativePath))
            $rootPrefix = $webRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) +
                [IO.Path]::DirectorySeparatorChar
            if (-not $file.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase) -or
                    -not (Test-Path $file -PathType Leaf)) {
                $context.Response.StatusCode = 404
            } else {
                $bytes = [IO.File]::ReadAllBytes($file)
                $context.Response.StatusCode = 200
                $context.Response.ContentType = getContentType $file
                $context.Response.ContentLength64 = $bytes.Length
                $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
            }
        } catch {
            if ($context -ne $null) { $context.Response.StatusCode = 400 }
        } finally {
            if ($context -ne $null) { $context.Response.Close() }
        }
    }
} finally {
    $listener.Close()
}

function getContentType([string]$file) {
    switch ([IO.Path]::GetExtension($file).ToLowerInvariant()) {
        '.html' { return 'text/html; charset=utf-8' }
        '.js' { return 'application/javascript; charset=utf-8' }
        '.css' { return 'text/css; charset=utf-8' }
        '.json' { return 'application/json; charset=utf-8' }
        '.png' { return 'image/png' }
        '.jpg' { return 'image/jpeg' }
        '.jpeg' { return 'image/jpeg' }
        '.svg' { return 'image/svg+xml' }
        '.woff' { return 'font/woff' }
        '.woff2' { return 'font/woff2' }
        '.ttf' { return 'font/ttf' }
        '.ico' { return 'image/x-icon' }
        default { return 'application/octet-stream' }
    }
}

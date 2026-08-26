[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8899,
    [AllowEmptyString()]
    [string]$VerifierRunId = '',
    [AllowEmptyString()]
    [string]$VerifierNonce = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'VerifierIsolation.psm1') -Force

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

$repositoryRoot = Get-VerifierCanonicalWindowsPath (Split-Path -Parent $PSScriptRoot)
$webRoot = Get-VerifierCanonicalWindowsPath (Join-Path $repositoryRoot 'war')
$previewScript = Get-VerifierCanonicalWindowsPath $PSCommandPath
$identityPath = '/__tsj/verify-identity'
[void](Assert-VerifierPhysicalOwnedPath $repositoryRoot $repositoryRoot -AllowRoot)
[void](Assert-VerifierPhysicalOwnedPath $repositoryRoot $webRoot)
[void](Assert-VerifierPhysicalOwnedPath $repositoryRoot $previewScript)
if ((-not [String]::IsNullOrWhiteSpace($VerifierRunId)) -xor
        (-not [String]::IsNullOrWhiteSpace($VerifierNonce))) {
    throw 'VerifierRunId and VerifierNonce must be supplied together.'
}
if (-not (Assert-VerifierPhysicalOwnedPath $webRoot `
        (Join-Path $webRoot 'circuitjs1\circuitjs1.nocache.js')) -or
        -not (Test-Path (Join-Path $webRoot 'circuitjs1\circuitjs1.nocache.js') -PathType Leaf)) {
    throw 'Production output is missing. Run .\scripts\build.ps1 with JDK 8 first.'
}

$processStartTicks = 0L
try {
    $processStartTicks = [long]((Get-Process -Id $PID -ErrorAction Stop).StartTime.ToUniversalTime().Ticks)
} catch {
    if (-not [String]::IsNullOrWhiteSpace($VerifierRunId)) {
        throw "Could not record run-owned preview process start identity: $($_.Exception.Message)"
    }
}

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
$listener.Start()
Write-Host "TroubleshootJS production preview listening on http://127.0.0.1:$Port/"
try {
    while ($listener.IsListening) {
        $context = $null
        try {
            $context = $listener.GetContext()
            $relativePath = [Uri]::UnescapeDataString($context.Request.Url.AbsolutePath.TrimStart('/'))
            if ($context.Request.Url.AbsolutePath -eq $identityPath) {
                $identity = [ordered]@{
                    protocol = 'troubleshootjs-preview-identity-v1'
                    repositoryRoot = $repositoryRoot
                    previewScript = $previewScript
                    webRoot = $webRoot
                    previewPort = $Port
                    processId = [int]$PID
                    processStartTicks = $processStartTicks
                    verifierRunId = $VerifierRunId
                    verifierNonce = $VerifierNonce
                }
                $bytes = [Text.Encoding]::UTF8.GetBytes(($identity | ConvertTo-Json -Compress))
                $context.Response.StatusCode = 200
                $context.Response.ContentType = 'application/json; charset=utf-8'
                $context.Response.ContentLength64 = $bytes.Length
                $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
            } else {
                if ([String]::IsNullOrEmpty($relativePath)) { $relativePath = 'circuitjs.html' }
                $file = Get-VerifierCanonicalWindowsPath (Join-Path $webRoot $relativePath)
                if (-not (Test-VerifierChildPath $webRoot $file) -or
                        -not (Assert-VerifierPhysicalOwnedPath $webRoot $file) -or
                        -not (Test-Path $file -PathType Leaf)) {
                    $context.Response.StatusCode = 404
                } else {
                    # Recheck the physical serving namespace immediately before
                    # reading. A junction inserted after route validation must
                    # fail closed rather than serve another worktree's file.
                    [void](Assert-VerifierPhysicalOwnedPath $webRoot $webRoot -AllowRoot)
                    [void](Assert-VerifierPhysicalOwnedPath $webRoot $file)
                    $bytes = [IO.File]::ReadAllBytes($file)
                    $context.Response.StatusCode = 200
                    $context.Response.ContentType = getContentType $file
                    $context.Response.ContentLength64 = $bytes.Length
                    $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
                }
            }
        } catch {
            if ($context -ne $null) { $context.Response.StatusCode = 400 }
        } finally {
            if ($context -ne $null) {
                try { $context.Response.Close() } catch { }
            }
        }
    }
} finally {
    $listener.Close()
}

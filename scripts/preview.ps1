[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8899,
    [AllowEmptyString()]
    [string]$VerifierRunId = '',
    [AllowEmptyString()]
    [string]$VerifierNonce = '',
    [switch]$ForceTcpListener
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
$executionProvenance = Get-VerifierExecutionTreeProvenance $repositoryRoot
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
    $processStartTicks = [long](Get-VerifierProcessStartTicks (
        Get-Process -Id $PID -ErrorAction Stop))
} catch {
    if (-not [String]::IsNullOrWhiteSpace($VerifierRunId)) {
        throw "Could not record run-owned preview process start identity: $($_.Exception.Message)"
    }
}

function Get-PreviewResponse([string]$AbsolutePath) {
    if (-not (Test-VerifierStrictStringValue $AbsolutePath) -or
            [String]::IsNullOrWhiteSpace($AbsolutePath) -or
            -not $AbsolutePath.StartsWith('/', [StringComparison]::Ordinal)) {
        throw 'Preview request path was malformed.'
    }
    if ($AbsolutePath -eq $identityPath) {
        $identity = [ordered]@{
            protocol = 'troubleshootjs-preview-identity-v1'
            repositoryRoot = $repositoryRoot
            previewScript = $previewScript
            webRoot = $webRoot
            sourceRoot = $executionProvenance.SourceRoot
            scriptRoot = $executionProvenance.ScriptRoot
            sourceDigest = $executionProvenance.SourceDigest
            scriptDigest = $executionProvenance.ScriptDigest
            webDigest = $executionProvenance.WebDigest
            executionDigest = $executionProvenance.Digest
            executionFileCount = $executionProvenance.FileCount
            previewPort = $Port
            processId = [int]$PID
            processStartTicks = $processStartTicks
            verifierRunId = $VerifierRunId
            verifierNonce = $VerifierNonce
        }
        return [pscustomobject]@{
            StatusCode = 200
            ContentType = 'application/json; charset=utf-8'
            Bytes = [Text.Encoding]::UTF8.GetBytes(($identity | ConvertTo-Json -Compress))
        }
    }
    $relativePath = [Uri]::UnescapeDataString($AbsolutePath.TrimStart('/'))
    if ([String]::IsNullOrEmpty($relativePath)) { $relativePath = 'circuitjs.html' }
    $file = Get-VerifierCanonicalWindowsPath (Join-Path $webRoot $relativePath)
    if (-not (Test-VerifierChildPath $webRoot $file) -or
            -not (Assert-VerifierPhysicalOwnedPath $webRoot $file) -or
            -not (Test-Path $file -PathType Leaf)) {
        return [pscustomobject]@{
            StatusCode = 404; ContentType = 'text/plain; charset=utf-8'; Bytes = [byte[]]@()
        }
    }
    # Recheck the physical serving namespace immediately before reading. A
    # junction inserted after route validation must fail closed rather than
    # serve another worktree's file.
    [void](Assert-VerifierPhysicalOwnedPath $webRoot $webRoot -AllowRoot)
    [void](Assert-VerifierPhysicalOwnedPath $webRoot $file)
    return [pscustomobject]@{
        StatusCode = 200
        ContentType = getContentType $file
        Bytes = [IO.File]::ReadAllBytes($file)
    }
}

function Write-PreviewHttpListenerResponse($Context, $Response) {
    $Context.Response.StatusCode = [int]$Response.StatusCode
    $Context.Response.ContentType = [string]$Response.ContentType
    $Context.Response.ContentLength64 = [long]$Response.Bytes.Length
    $Context.Response.KeepAlive = $false
    if ($Response.Bytes.Length -gt 0) {
        $Context.Response.OutputStream.Write($Response.Bytes, 0, $Response.Bytes.Length)
    }
}

function Read-PreviewTcpRequestPath($Client) {
    if ($null -eq $Client) { throw 'Preview TCP client was missing.' }
    $stream = $Client.GetStream()
    $stream.ReadTimeout = 5000
    $buffer = New-Object byte[] 16384
    $used = 0
    $headerEnd = -1
    while ($used -lt $buffer.Length) {
        $read = $stream.Read($buffer, $used, $buffer.Length - $used)
        if ($read -le 0) { break }
        $used += $read
        $headerEnd = [Text.Encoding]::ASCII.GetString($buffer, 0, $used).IndexOf(
            "`r`n`r`n", [StringComparison]::Ordinal)
        if ($headerEnd -ge 0) { break }
    }
    if ($headerEnd -lt 0) { throw 'Preview TCP request headers exceeded the bounded request limit.' }
    $headerText = [Text.Encoding]::ASCII.GetString($buffer, 0, $headerEnd)
    $requestLine = @($headerText -split "`r`n")[0]
    $match = [regex]::Match($requestLine, '^(?<method>[A-Z]+) (?<target>\S+) HTTP/(?<version>1\.[01])$')
    if (-not $match.Success -or $match.Groups['method'].Value -cne 'GET') {
        throw 'Preview TCP request was not a bounded GET request.'
    }
    $rawTarget = $match.Groups['target'].Value
    if (-not $rawTarget.StartsWith('/', [StringComparison]::Ordinal) -or
            $rawTarget.StartsWith('//', [StringComparison]::Ordinal)) {
        throw 'Preview TCP request target was not a relative loopback path.'
    }
    try {
        $uri = [Uri]::new(('http://127.0.0.1' + $rawTarget), [UriKind]::Absolute)
    } catch {
        throw 'Preview TCP request target was not a valid URI path.'
    }
    if ($uri.Host -cne '127.0.0.1' -or $uri.Scheme -cne 'http') {
        throw 'Preview TCP request target escaped the loopback URI namespace.'
    }
    return [string]$uri.AbsolutePath
}

function Get-PreviewHttpReason([int]$StatusCode) {
    switch ($StatusCode) {
        200 { return 'OK' }
        400 { return 'Bad Request' }
        404 { return 'Not Found' }
        default { return 'Error' }
    }
}

function Write-PreviewTcpResponse($Client, $Response) {
    $stream = $Client.GetStream()
    $bytes = [byte[]]$Response.Bytes
    $header = 'HTTP/1.1 ' + [string]$Response.StatusCode + ' ' +
        (Get-PreviewHttpReason ([int]$Response.StatusCode)) + "`r`n" +
        'Content-Type: ' + [string]$Response.ContentType + "`r`n" +
        'Content-Length: ' + [string]$bytes.Length + "`r`n" +
        'Connection: close' + "`r`n`r`n"
    $headerBytes = [Text.Encoding]::ASCII.GetBytes($header)
    $stream.Write($headerBytes, 0, $headerBytes.Length)
    if ($bytes.Length -gt 0) { $stream.Write($bytes, 0, $bytes.Length) }
    $stream.Flush()
}

$httpListener = $null
$tcpListener = $null
try {
    if (-not $ForceTcpListener) {
        try {
            $httpListener = New-Object System.Net.HttpListener
            $httpListener.Prefixes.Add("http://127.0.0.1:$Port/")
            $httpListener.Start()
        } catch {
            if ($null -ne $httpListener) {
                try { $httpListener.Close() } catch { }
                $httpListener = $null
            }
        }
    }
    if ($null -eq $httpListener) {
        # HttpListener is unavailable on some supported PowerShell/.NET
        # combinations (for example, an invalid HTTP.sys handle). TcpListener
        # remains loopback-only and is used with the same route and identity
        # handler rather than weakening the ownership handshake.
        $tcpListener = New-Object System.Net.Sockets.TcpListener(
            [Net.IPAddress]::Loopback, [int]$Port)
        $tcpListener.Start()
        if ([int]$tcpListener.LocalEndpoint.Port -ne $Port) {
            throw 'TcpListener did not bind the requested preview port.'
        }
    }
    $transportName = if ($null -ne $httpListener) { 'HttpListener' } else { 'TcpListener' }
    Write-Host "TroubleshootJS production preview listening on http://127.0.0.1:$Port/ transport=$transportName"
    if ($null -ne $httpListener) {
        while ($httpListener.IsListening) {
            $context = $null
            try {
                $context = $httpListener.GetContext()
                $response = Get-PreviewResponse ([string]$context.Request.Url.AbsolutePath)
                Write-PreviewHttpListenerResponse $context $response
            } catch {
                if ($null -ne $context) {
                    try {
                        Write-PreviewHttpListenerResponse $context ([pscustomobject]@{
                            StatusCode = 400; ContentType = 'text/plain; charset=utf-8'; Bytes = [byte[]]@()
                        })
                    } catch { }
                }
            } finally {
                if ($null -ne $context) {
                    try { $context.Response.Close() } catch { }
                }
            }
        }
    } else {
        while ($true) {
            $client = $null
            try {
                $client = $tcpListener.AcceptTcpClient()
                try {
                    $path = Read-PreviewTcpRequestPath $client
                    $response = Get-PreviewResponse $path
                } catch {
                    $response = [pscustomobject]@{
                        StatusCode = 400; ContentType = 'text/plain; charset=utf-8'; Bytes = [byte[]]@()
                    }
                }
                Write-PreviewTcpResponse $client $response
            } finally {
                if ($null -ne $client) {
                    try { $client.Close() } catch { }
                }
            }
        }
    }
} finally {
    if ($null -ne $httpListener) {
        try { $httpListener.Close() } catch { }
    }
    if ($null -ne $tcpListener) {
        try { $tcpListener.Stop() } catch { }
    }
}

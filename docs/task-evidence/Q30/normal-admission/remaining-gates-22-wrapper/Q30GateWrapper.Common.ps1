#requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-RepoAbsolutePath {
    param([string]$RepoRoot, [string]$RelativePath)
    $root = [IO.Path]::GetFullPath($RepoRoot).TrimEnd([char]92, [char]47)
    if ([IO.Path]::IsPathRooted($RelativePath) -or $RelativePath -match '(^|[\\/])\.\.?([\\/]|$)') { throw ('Unsafe relative path: ' + $RelativePath) }
    $full = [IO.Path]::GetFullPath([IO.Path]::Combine($root, $RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar)))
    if (-not $full.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw ('Path escapes repository: ' + $RelativePath) }
    $current = $root
    foreach ($segment in ($RelativePath -split '[\\/]')) {
        if (-not $segment) { continue }
        $current = [IO.Path]::Combine($current, $segment)
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw ('Reparse point in input path: ' + $current) }
        }
    }
    return $full
}

function Get-InputMapFromManifest {
    param([string]$RepoRoot, [string]$ManifestRelativePath, [int]$ExpectedCount)
    $manifestPath = Get-RepoAbsolutePath $RepoRoot $ManifestRelativePath
    $document = [IO.File]::ReadAllText($manifestPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
    if ($document.schema -ne 1 -or $null -eq $document.files) { throw ('Bad input manifest: ' + $ManifestRelativePath) }
    $files = [ordered]@{}
    foreach ($property in $document.files.PSObject.Properties) {
        $relative = [string]$property.Name
        $hash = ([string]$property.Value).ToLowerInvariant()
        if ($hash -notmatch '^[0-9a-f]{64}$' -or $files.Contains($relative)) { throw ('Invalid manifest entry: ' + $relative) }
        $files[$relative] = $hash
    }
    if ($files.Count -ne $ExpectedCount) { throw ('Expected ' + $ExpectedCount + ' manifest paths, got ' + $files.Count + ': ' + $ManifestRelativePath) }
    return [pscustomobject]@{ Path = $manifestPath; Document = $document; Files = $files }
}

function Get-CurrentInputHashes {
    param([string]$RepoRoot, [string[]]$RelativePaths)
    $hashes = [ordered]@{}
    foreach ($relative in ($RelativePaths | Sort-Object -Unique)) {
        $full = Get-RepoAbsolutePath $RepoRoot $relative
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { throw ('Missing input: ' + $relative) }
        $hashes[$relative] = (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    return ,$hashes
}

function Get-ChangedInputHashes {
    param([System.Collections.IDictionary]$Before, [System.Collections.IDictionary]$After)
    $changes = New-Object Collections.Generic.List[object]
    foreach ($relative in $Before.Keys) {
        if (-not $After.Contains($relative)) {
            $changes.Add([pscustomobject]@{ path = [string]$relative; before = [string]$Before[$relative]; after = $null })
        } elseif ([string]$Before[$relative] -cne [string]$After[$relative]) {
            $changes.Add([pscustomobject]@{ path = [string]$relative; before = [string]$Before[$relative]; after = [string]$After[$relative] })
        }
    }
    foreach ($relative in $After.Keys) {
        if (-not $Before.Contains($relative)) { $changes.Add([pscustomobject]@{ path = [string]$relative; before = $null; after = [string]$After[$relative] }) }
    }
    return $changes.ToArray()
}

function Assert-InputMapUnchanged {
    param([System.Collections.IDictionary]$Before, [System.Collections.IDictionary]$After, [string]$Label)
    $changes = @(Get-ChangedInputHashes $Before $After)
    if ($changes.Count) { throw ($Label + ' changed: ' + (($changes | ForEach-Object { $_.path }) -join ', ')) }
}

function Assert-MapMatchesManifest {
    param([System.Collections.IDictionary]$Manifest, [System.Collections.IDictionary]$Current, [string]$Label)
    $changes = @(Get-ChangedInputHashes $Manifest $Current)
    if ($changes.Count) { throw ($Label + ' differs from its manifest: ' + (($changes | ForEach-Object { $_.path }) -join ', ')) }
}

function Get-InputMapSha256 {
    param([System.Collections.IDictionary]$Map)
    $paths = [string[]]@($Map.Keys)
    [Array]::Sort($paths, [StringComparer]::Ordinal)
    $builder = New-Object Text.StringBuilder
    foreach ($relative in $paths) { [void]$builder.Append($relative).Append([char]0).Append(([string]$Map[$relative]).ToLowerInvariant()).Append("`n") }
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash((New-Object Text.UTF8Encoding($false)).GetBytes($builder.ToString()))).Replace('-', '').ToLowerInvariant()) }
    finally { $algorithm.Dispose() }
}

function Save-JsonUtf8 {
    param([string]$Path, $Object)
    [IO.File]::WriteAllText($Path, ((ConvertTo-Json -InputObject $Object -Depth 8) + "`n"), (New-Object Text.UTF8Encoding($false)))
}

function Set-TaskPointer {
    param([string]$RepoRoot, [string]$RelativePath, [string]$TargetPath)
    $pointer = Get-RepoAbsolutePath $RepoRoot $RelativePath
    if (Test-Path -LiteralPath $pointer -PathType Leaf) {
        $existing = [IO.File]::ReadAllText($pointer, [Text.Encoding]::UTF8).Trim()
        if ($existing -ceq $TargetPath) { return }
        throw ('Pointer already identifies another run; preserve it: ' + $pointer)
    }
    [IO.File]::WriteAllText($pointer, $TargetPath + "`n", (New-Object Text.UTF8Encoding($false)))
}
function Invoke-CapturedCommand {
    param([string]$FilePath, [string[]]$Arguments, [string]$LogPath, [string]$WorkingDirectory)
    if (Test-Path -LiteralPath $LogPath) { throw ('Refusing to overwrite log: ' + $LogPath) }
    if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) { $PSNativeCommandUseErrorActionPreference = $false }
    $utf8NoBom = New-Object Text.UTF8Encoding($false)
    $writer = New-Object IO.StreamWriter($LogPath, $false, $utf8NoBom)
    $writer.AutoFlush = $true
    try {
        Push-Location -LiteralPath $WorkingDirectory
        try {
            & $FilePath @Arguments 2>&1 | ForEach-Object {
                $line = $_.ToString()
                $writer.WriteLine($line)
                Write-Host $line
            }
            $actualExitCode = $LASTEXITCODE
        } finally {
            Pop-Location
        }
    } finally {
        $writer.Dispose()
    }
    $text = [IO.File]::ReadAllText($LogPath, [Text.Encoding]::UTF8)
    return [pscustomobject]@{ ExitCode = [int]$actualExitCode; LogPath = $LogPath; Output = $text; FilePath = $FilePath; Arguments = @($Arguments); WorkingDirectory = $WorkingDirectory }
}
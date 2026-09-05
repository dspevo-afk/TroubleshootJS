[CmdletBinding()]
param(
    [string]$JavaHome = '',
    [ValidateRange(30, 1800)]
    [int]$ProcessTimeoutSeconds = 900,
    [AllowEmptyString()]
    [string]$ExperimentId = '',
    [AllowEmptyString()]
    [string]$EvidenceDirectory = '',
    [switch]$IdentityCanary,
    [switch]$ContractProbe,
    [switch]$MutationPreflight
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Disposable previews use the same validated process boundary and exact
# termination proof as the integrated verifier.  Keeping this wrapper on the
# shared launcher is important when the host exposes both case variants of
# PATH; Start-Process can reject that inherited environment before a child is
# created.
Import-Module (Join-Path $PSScriptRoot 'VerifierIsolation.psm1') -Force
try {
    . (Join-Path $PSScriptRoot 'Task43PPublicActionEvidence.ps1')
} catch {
    [Console]::Error.WriteLine('SOURCE_EXPERIMENT_INFRASTRUCTURE: public-action evidence helper could not be loaded: ' + $_.Exception.Message)
    exit 2
}

$publishedBaselineSha = '8bf442416a2fa2c0c9d654d1efa14e754c2b7ee7'
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$runId = [Guid]::NewGuid().ToString('N')
$runRoot = Join-Path $tempRoot ('TroubleshootJS\task43p-source-experiments\' + $runId)
$evidenceRoot = if ([String]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    Join-Path $tempRoot 'TroubleshootJS\verify\task43p-source-experiments'
} else {
    [IO.Path]::GetFullPath($EvidenceDirectory)
}
$evidencePath = Join-Path $evidenceRoot ('task43p-source-experiments-' + $runId + '.json')
$script:cleanupErrors = @()
$script:contractProbeRemovalTarget = $null

function Get-ExperimentErrorMessage($ErrorRecord) {
    if ($null -eq $ErrorRecord) { return '' }
    if ($ErrorRecord.PSObject.Properties['Exception'] -and
            $null -ne $ErrorRecord.Exception) {
        return [string]$ErrorRecord.Exception.Message
    }
    return [string]$ErrorRecord
}

function Throw-SourceExperimentInfrastructure([string]$Message, $ErrorRecord = $null) {
    $detail = if ($null -eq $ErrorRecord) { '' } else {
        ' ' + (Get-ExperimentErrorMessage $ErrorRecord)
    }
    throw [System.InvalidOperationException]::new(
        ('SOURCE_EXPERIMENT_INFRASTRUCTURE: ' + $Message + $detail).Trim())
}

function Get-ExperimentCanonicalPath($Path) {
    if ($null -eq $Path -or $Path.GetType() -ne [string] -or
            [String]::IsNullOrWhiteSpace($Path)) {
        Throw-SourceExperimentInfrastructure 'Canonical path input was not one exact non-empty string.'
    }
    try {
        $full = [IO.Path]::GetFullPath($Path)
        $root = [IO.Path]::GetPathRoot($full)
        if ([String]::IsNullOrWhiteSpace($full) -or [String]::IsNullOrWhiteSpace($root)) {
            Throw-SourceExperimentInfrastructure "Could not canonicalize path '$Path'."
        }
        if ($full.Length -gt $root.Length) { return $full.TrimEnd([char]92) }
        return $root
    } catch {
        if ($_.Exception.Message -like 'SOURCE_EXPERIMENT_INFRASTRUCTURE:*') { throw }
        Throw-SourceExperimentInfrastructure "Could not canonicalize path '$Path'." $_
    }
}

function Test-ExperimentStrictIntegralValue($Value, [long]$Minimum = [long]::MinValue,
        [long]$Maximum = [long]::MaxValue) {
    if ($null -eq $Value) { return $false }
    $type = $Value.GetType()
    if ($type -notin @([byte], [sbyte], [int16], [uint16], [int32], [uint32],
            [int64], [uint64])) { return $false }
    try {
        $number = [long]$Value
        return $number -ge $Minimum -and $number -le $Maximum
    } catch { return $false }
}

function Get-ExperimentExecutionProvenance($Destination) {
    $root = Get-ExperimentCanonicalPath $Destination
    $definitions = @(
        [pscustomobject]@{ Name = 'src'; Path = (Join-Path $root 'src') }
        [pscustomobject]@{ Name = 'scripts'; Path = (Join-Path $root 'scripts') }
        [pscustomobject]@{ Name = 'war'; Path = (Join-Path $root 'war') }
    )
    $allRecords = New-Object Collections.Generic.List[string]
    $results = [ordered]@{}
    foreach ($definition in $definitions) {
        $categoryRoot = Get-ExperimentCanonicalPath $definition.Path
        if (-not (Test-Path -LiteralPath $categoryRoot -PathType Container -ErrorAction Stop)) {
            Throw-SourceExperimentInfrastructure "Execution provenance root '$($definition.Name)' was missing."
        }
        $records = New-Object Collections.Generic.List[string]
        $filePaths = New-Object Collections.Generic.List[string]
        foreach ($file in @(Get-ChildItem -LiteralPath $categoryRoot -Recurse -File -ErrorAction Stop)) {
            [void]$filePaths.Add([string]$file.FullName)
        }
        $filePaths.Sort([StringComparer]::OrdinalIgnoreCase)
        $previousPath = $null
        foreach ($filePath in $filePaths) {
            if ($null -ne $previousPath -and
                    [StringComparer]::OrdinalIgnoreCase.Equals($previousPath, $filePath)) {
                Throw-SourceExperimentInfrastructure "Execution provenance contained duplicate or case-colliding file path '$filePath'."
            }
            $previousPath = $filePath
            $relativePath = $filePath.Substring($categoryRoot.Length).TrimStart('\', '/')
            $hash = Get-VerifierFileSha256 $filePath
            [void]$records.Add($relativePath.Replace('\', '/') + '=' + $hash)
        }
        $categoryDigest = Get-BytesHash ([Text.UTF8Encoding]::new($false).GetBytes(($records -join "`n")))
        $results[$definition.Name] = [ordered]@{
            root = $categoryRoot
            digest = $categoryDigest
            fileCount = $records.Count
        }
        foreach ($record in $records) {
            [void]$allRecords.Add($definition.Name + '/' + $record)
        }
    }
    $digest = Get-BytesHash ([Text.UTF8Encoding]::new($false).GetBytes(($allRecords -join "`n")))
    return [pscustomobject]@{
        protocol = 'troubleshootjs-execution-provenance-v1'
        repositoryRoot = $root
        sourceRoot = $results.src.root
        scriptRoot = $results.scripts.root
        webRoot = $results.war.root
        sourceDigest = $results.src.digest
        scriptDigest = $results.scripts.digest
        webDigest = $results.war.digest
        digest = $digest
        fileCount = $allRecords.Count
    }
}

function Write-SourceExperimentText([string]$Path, [string]$Text, [string]$Phase) {
    try {
        [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($false))
    } catch {
        Throw-SourceExperimentInfrastructure ("Could not write $Phase at '$Path'.") $_
    }
}

function Write-SourceExperimentBytes([string]$Path, [byte[]]$Bytes, [string]$Phase) {
    try {
        [IO.File]::WriteAllBytes($Path, $Bytes)
    } catch {
        Throw-SourceExperimentInfrastructure ("Could not write $Phase bytes at '$Path'.") $_
    }
}

function New-SourceExperimentDirectory([string]$Path, [string]$Phase,
        [switch]$Force) {
    try {
        if ($Force) {
            New-Item -ItemType Directory -Path $Path -Force -ErrorAction Stop | Out-Null
        } else {
            New-Item -ItemType Directory -Path $Path -ErrorAction Stop | Out-Null
        }
    } catch {
        Throw-SourceExperimentInfrastructure ("Could not create $Phase directory '$Path'.") $_
    }
}

function Remove-SourceExperimentTree([string]$Path, [string]$Phase) {
    try {
        $safeRoot = Get-ExperimentCanonicalPath $tempRoot
        $target = Get-ExperimentCanonicalPath $Path
        $runRootTarget = Get-ExperimentCanonicalPath $runRoot
        $probeTarget = if ($null -eq $script:contractProbeRemovalTarget) {
            ''
        } else { Get-ExperimentCanonicalPath $script:contractProbeRemovalTarget }
        if (-not $target.Equals($runRootTarget, [StringComparison]::OrdinalIgnoreCase) -and
                ([String]::IsNullOrWhiteSpace($probeTarget) -or
                 -not $target.Equals($probeTarget, [StringComparison]::OrdinalIgnoreCase))) {
            Throw-SourceExperimentInfrastructure ("$Phase was not one of the two exact registered cleanup targets: $Path")
        }
        if (Test-Path -LiteralPath $target) {
            Assert-VerifierNoReparseAncestors $safeRoot
            Assert-VerifierNoReparseAncestors $target
            if (-not (Test-VerifierPhysicalChildPath $safeRoot $target)) {
                Throw-SourceExperimentInfrastructure ("$Phase escaped the task-owned temp namespace: $Path")
            }
            Assert-VerifierNoReparseTree $target
            # Revalidate both the target's existing ancestors and its complete
            # tree immediately before the narrow owned-tree deletion call.
            Assert-VerifierNoReparseAncestors $target
            Assert-VerifierNoReparseTree $target
            Assert-VerifierNoReparseAncestors $safeRoot
            if (-not (Test-VerifierPhysicalChildPath $safeRoot $target)) {
                Throw-SourceExperimentInfrastructure ("$Phase physical ownership changed before cleanup: $Path")
            }
            Remove-VerifierOwnedTree $safeRoot $target
        }
    } catch {
        if ($_.Exception.Message -like 'SOURCE_EXPERIMENT_INFRASTRUCTURE:*') { throw }
        Throw-SourceExperimentInfrastructure ("Could not remove $Phase '$Path'.") $_
    }
}

function Assert-DisposablePreviewIdentity($Destination, $Identity, $Port,
        $LaunchedProcessId, $LaunchedProcessStartTicks) {
    if ($null -eq $Destination -or $Destination.GetType() -ne [string] -or
            [String]::IsNullOrWhiteSpace($Destination) -or
            -not (Test-ExperimentStrictIntegralValue $Port 1 65535) -or
            -not (Test-ExperimentStrictIntegralValue $LaunchedProcessId 1 ([int]::MaxValue)) -or
            -not (Test-ExperimentStrictIntegralValue $LaunchedProcessStartTicks 1 ([long]::MaxValue))) {
        Throw-SourceExperimentInfrastructure 'Disposable preview identity validation received a malformed raw destination, port, or launched process tuple.'
    }
    if ($null -eq $Identity -or $Identity -is [System.Array] -or
            $Identity -isnot [pscustomobject]) {
        Throw-SourceExperimentInfrastructure 'Disposable preview returned a malformed identity object.'
    }
    $expectedRepositoryRoot = Get-ExperimentCanonicalPath $Destination
    $expectedWebRoot = Get-ExperimentCanonicalPath (Join-Path $Destination 'war')
    $expectedScriptRoot = Get-ExperimentCanonicalPath (Join-Path $Destination 'scripts')
    $expectedPreviewScript = Get-ExperimentCanonicalPath (Join-Path $expectedScriptRoot 'preview.ps1')
    $allowedProperties = @('protocol', 'repositoryRoot', 'previewScript', 'webRoot',
        'previewPort', 'processId', 'processStartTicks', 'verifierRunId',
        'verifierNonce', 'sourceRoot', 'scriptRoot', 'sourceDigest', 'scriptDigest',
        'webDigest', 'executionDigest', 'executionFileCount')
    foreach ($property in @($Identity.PSObject.Properties)) {
        if ($allowedProperties -cnotcontains [string]$property.Name) {
            Throw-SourceExperimentInfrastructure "Disposable preview identity contained unknown field '$($property.Name)'."
        }
    }
    foreach ($propertyName in $allowedProperties) {
        if ($null -eq $Identity.PSObject.Properties[$propertyName]) {
            Throw-SourceExperimentInfrastructure "Disposable preview identity omitted '$propertyName'."
        }
    }
    foreach ($propertyName in @('protocol', 'repositoryRoot', 'previewScript', 'webRoot',
            'verifierRunId', 'verifierNonce', 'sourceRoot', 'scriptRoot',
            'sourceDigest', 'scriptDigest', 'webDigest', 'executionDigest')) {
        $rawValue = $Identity.PSObject.Properties[$propertyName].Value
        if ($null -eq $rawValue -or $rawValue.GetType() -ne [string]) {
            Throw-SourceExperimentInfrastructure "Disposable preview identity field '$propertyName' was not an exact raw string."
        }
    }
    foreach ($propertyName in @('previewPort', 'processId', 'processStartTicks',
            'executionFileCount')) {
        if (-not (Test-ExperimentStrictIntegralValue $Identity.PSObject.Properties[$propertyName].Value `
                0 ([long]::MaxValue))) {
            Throw-SourceExperimentInfrastructure "Disposable preview identity field '$propertyName' was not an exact raw integral."
        }
    }
    foreach ($digestName in @('sourceDigest', 'scriptDigest', 'webDigest', 'executionDigest')) {
        if ([string]$Identity.PSObject.Properties[$digestName].Value -notmatch '^[0-9a-f]{64}$') {
            Throw-SourceExperimentInfrastructure "Disposable preview identity field '$digestName' was not a canonical SHA-256 digest."
        }
    }
    if ([string]$Identity.protocol -cne 'troubleshootjs-preview-identity-v1' -or
            -not ((Get-ExperimentCanonicalPath $Identity.repositoryRoot).Equals(
                $expectedRepositoryRoot, [StringComparison]::OrdinalIgnoreCase)) -or
            -not ((Get-ExperimentCanonicalPath $Identity.previewScript).Equals(
                $expectedPreviewScript, [StringComparison]::OrdinalIgnoreCase)) -or
            -not ((Get-ExperimentCanonicalPath $Identity.webRoot).Equals(
                $expectedWebRoot, [StringComparison]::OrdinalIgnoreCase)) -or
            -not ((Get-ExperimentCanonicalPath $Identity.sourceRoot).Equals(
                (Get-ExperimentCanonicalPath (Join-Path $Destination 'src')),
                [StringComparison]::OrdinalIgnoreCase)) -or
            -not ((Get-ExperimentCanonicalPath $Identity.scriptRoot).Equals(
                $expectedScriptRoot, [StringComparison]::OrdinalIgnoreCase)) -or
            [long]$Identity.previewPort -ne [long]$Port -or
            [long]$Identity.processId -ne [long]$LaunchedProcessId -or
            [long]$Identity.processStartTicks -ne [long]$LaunchedProcessStartTicks -or
            -not [String]::IsNullOrEmpty($Identity.verifierRunId) -or
            -not [String]::IsNullOrEmpty($Identity.verifierNonce)) {
        Throw-SourceExperimentInfrastructure 'Disposable preview identity did not match the disposable repository, script, web root, port, process, or caller-owned route identity.'
    }
    $provenance = Get-ExperimentExecutionProvenance $Destination
    if ($Identity.sourceDigest -cne $provenance.sourceDigest -or
            $Identity.scriptDigest -cne $provenance.scriptDigest -or
            $Identity.webDigest -cne $provenance.webDigest -or
            $Identity.executionDigest -cne $provenance.digest -or
            [long]$Identity.executionFileCount -ne [long]$provenance.fileCount) {
        Throw-SourceExperimentInfrastructure 'Disposable preview identity did not match the selected source/scripts/compiled-web provenance.'
    }
    return [ordered]@{
        protocol = [string]$Identity.protocol
        repositoryRoot = [string]$Identity.repositoryRoot
        previewScript = [string]$Identity.previewScript
        webRoot = [string]$Identity.webRoot
        previewPort = [int]$Identity.previewPort
        processId = [int]$Identity.processId
        processStartTicks = [long]$Identity.processStartTicks
        verifierRunId = [string]$Identity.verifierRunId
        verifierNonce = [string]$Identity.verifierNonce
        sourceRoot = [string]$Identity.sourceRoot
        scriptRoot = [string]$Identity.scriptRoot
        sourceDigest = [string]$Identity.sourceDigest
        scriptDigest = [string]$Identity.scriptDigest
        webDigest = [string]$Identity.webDigest
        executionDigest = [string]$Identity.executionDigest
        executionFileCount = [long]$Identity.executionFileCount
        executionProvenance = $provenance
        expectedRepositoryRoot = $expectedRepositoryRoot
        expectedWebRoot = $expectedWebRoot
        expectedScriptRoot = $expectedScriptRoot
        expectedPreviewScript = $expectedPreviewScript
    }
}

function Get-RepositoryState {
    $safeRoot = $repositoryRoot.Replace('\', '/')
    $headArgs = @('-c', "safe.directory=$safeRoot", '-C', $repositoryRoot,
        'rev-parse', 'HEAD')
    $head = (& git @headArgs 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -notmatch '^[0-9a-fA-F]{40}$') {
        throw "Could not prove repository HEAD: $head"
    }
    $statusArgs = @('-c', "safe.directory=$safeRoot", '-C', $repositoryRoot,
        'status', '--porcelain=v1', '--untracked-files=all')
    $status = (& git @statusArgs 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not prove repository status.'
    }
    $records = New-Object Collections.Generic.List[string]
    foreach ($relativeRoot in @('src', 'scripts')) {
        $root = Join-Path $repositoryRoot $relativeRoot
        if (-not (Test-Path -LiteralPath $root -PathType Container)) {
            throw "Could not inspect repository source root: $root"
        }
        foreach ($file in @(Get-ChildItem -LiteralPath $root -Recurse -File -ErrorAction Stop |
                Sort-Object FullName)) {
            $relativePath = $file.FullName.Substring($repositoryRoot.Length).TrimStart('\', '/')
            $hash = Get-VerifierFileSha256 $file.FullName
            [void]$records.Add($relativePath.Replace('\', '/') + '=' + $hash)
        }
    }
    $hasher = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($records -join "`n"))
        $digest = [BitConverter]::ToString($hasher.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant()
    } finally {
        $hasher.Dispose()
    }
    return [ordered]@{
        headSha = $head.ToLowerInvariant()
        sourceVerifierDigest = $digest
        sourceVerifierFileCount = $records.Count
        dirty = -not [String]::IsNullOrWhiteSpace($status)
        statusText = $status
    }
}

function Test-RepositoryStateEqual($Before, $After) {
    return $Before.headSha -eq $After.headSha -and
        $Before.sourceVerifierDigest -eq $After.sourceVerifierDigest -and
        $Before.sourceVerifierFileCount -eq $After.sourceVerifierFileCount -and
        $Before.dirty -eq $After.dirty -and $Before.statusText -eq $After.statusText
}

function Get-BytesHash([byte[]]$Bytes) {
    $hasher = [Security.Cryptography.SHA256]::Create()
    try {
        return [BitConverter]::ToString($hasher.ComputeHash($Bytes)).Replace('-', '').ToLowerInvariant()
    } finally {
        $hasher.Dispose()
    }
}

function Test-ByteArraysEqual([byte[]]$First, [byte[]]$Second) {
    if ($null -eq $First -or $null -eq $Second -or $First.Length -ne $Second.Length) {
        return $false
    }
    for ($index = 0; $index -lt $First.Length; $index++) {
        if ($First[$index] -ne $Second[$index]) { return $false }
    }
    return $true
}

function Throw-SourceNegativeUnproven([string]$Message) {
    throw [System.IO.InvalidDataException]::new(
        ('SOURCE_NEGATIVE_UNPROVEN: ' + $Message).Trim())
}

function Test-SourceNegativeUnprovenException($ErrorRecord) {
    if ($null -eq $ErrorRecord -or $null -eq $ErrorRecord.Exception) {
        return $false
    }
    $exception = $ErrorRecord.Exception
    return $exception.GetType() -eq [System.IO.InvalidDataException] -and
        ([string]$exception.Message).StartsWith(
            'SOURCE_NEGATIVE_UNPROVEN: ', [StringComparison]::Ordinal)
}

function Assert-SourceNegativeObject($Value, [string]$Path,
        [string[]]$AllowedProperties, [string[]]$RequiredProperties) {
    if ($null -eq $Value -or $Value -is [System.Array] -or
            $Value -isnot [pscustomobject]) {
        Throw-SourceNegativeUnproven "Expected an object at $Path."
    }
    foreach ($property in @($Value.PSObject.Properties)) {
        if ($AllowedProperties -cnotcontains [string]$property.Name) {
            Throw-SourceNegativeUnproven "Unknown field '$($property.Name)' at $Path."
        }
    }
    foreach ($propertyName in $RequiredProperties) {
        if ($null -eq $Value.PSObject.Properties[$propertyName]) {
            Throw-SourceNegativeUnproven "Missing field '$propertyName' at $Path."
        }
    }
}

function Assert-SourceNegativeString($Value, [string]$Path,
        [switch]$AllowEmpty) {
    if ($null -eq $Value -or $Value.GetType() -ne [string] -or
            (-not $AllowEmpty -and [String]::IsNullOrWhiteSpace([string]$Value))) {
        Throw-SourceNegativeUnproven "Expected a string at $Path."
    }
}

function Assert-SourceNegativeBoolean($Value, [string]$Path) {
    if ($null -eq $Value -or $Value.GetType() -ne [bool]) {
        Throw-SourceNegativeUnproven "Expected a Boolean at $Path."
    }
}

function Assert-SourceNegativeInteger($Value, [string]$Path,
        [long]$Minimum = [long]::MinValue, [long]$Maximum = [long]::MaxValue) {
    if (-not (Test-ExperimentStrictIntegralValue $Value $Minimum $Maximum)) {
        Throw-SourceNegativeUnproven "Expected an exact integer at $Path."
    }
}

function Assert-SourceNegativeSha256($Value, [string]$Path) {
    Assert-SourceNegativeString $Value $Path
    if ([string]$Value -cnotmatch '^[0-9a-f]{64}$') {
        Throw-SourceNegativeUnproven "Expected a lowercase SHA-256 digest at $Path."
    }
}

function Get-SourceNegativeField($Object, [string]$Name) {
    $property = if ($null -eq $Object) { $null } else {
        $Object.PSObject.Properties[$Name]
    }
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Assert-SourceNegativeExecutionProvenance($Value, [string]$Path) {
    $allowed = @('Protocol', 'RepositoryRoot', 'SourceRoot', 'ScriptRoot',
        'WebRoot', 'SourceDigest', 'ScriptDigest', 'WebDigest', 'Digest',
        'FileCount')
    Assert-SourceNegativeObject $Value $Path $allowed $allowed
    Assert-SourceNegativeString $Value.Protocol ($Path + '.Protocol')
    Assert-SourceNegativeString $Value.RepositoryRoot ($Path + '.RepositoryRoot')
    Assert-SourceNegativeString $Value.SourceRoot ($Path + '.SourceRoot')
    Assert-SourceNegativeString $Value.ScriptRoot ($Path + '.ScriptRoot')
    Assert-SourceNegativeString $Value.WebRoot ($Path + '.WebRoot')
    foreach ($name in @('SourceDigest', 'ScriptDigest', 'WebDigest', 'Digest')) {
        Assert-SourceNegativeSha256 $Value.$name ($Path + '.' + $name)
    }
    Assert-SourceNegativeInteger $Value.FileCount ($Path + '.FileCount') 0 ([long]::MaxValue)
}

function Assert-SourceNegativeRepositoryState($Value, [string]$Path) {
    $allowed = @('headSha', 'sourceVerifierDigest', 'sourceVerifierFileCount',
        'dirty', 'statusText', 'executionProvenance')
    Assert-SourceNegativeObject $Value $Path $allowed $allowed
    Assert-SourceNegativeString $Value.headSha ($Path + '.headSha')
    if ([string]$Value.headSha -cnotmatch '^[0-9a-f]{40}$') {
        Throw-SourceNegativeUnproven "Repository HEAD at $Path was not a lowercase SHA-1."
    }
    Assert-SourceNegativeSha256 $Value.sourceVerifierDigest `
        ($Path + '.sourceVerifierDigest')
    Assert-SourceNegativeInteger $Value.sourceVerifierFileCount `
        ($Path + '.sourceVerifierFileCount') 0 ([long]::MaxValue)
    Assert-SourceNegativeBoolean $Value.dirty ($Path + '.dirty')
    Assert-SourceNegativeString $Value.statusText ($Path + '.statusText') -AllowEmpty
    Assert-SourceNegativeExecutionProvenance $Value.executionProvenance `
        ($Path + '.executionProvenance')
}

function Test-SourceNegativeExecutionProvenanceEqual($Actual, $Expected) {
    if ($null -eq $Actual -or $null -eq $Expected) { return $false }
    try {
        $actualRoot = Get-ExperimentCanonicalPath $Actual.RepositoryRoot
        $expectedRoot = Get-ExperimentCanonicalPath $Expected.repositoryRoot
        $actualSource = Get-ExperimentCanonicalPath $Actual.SourceRoot
        $expectedSource = Get-ExperimentCanonicalPath $Expected.sourceRoot
        $actualScripts = Get-ExperimentCanonicalPath $Actual.ScriptRoot
        $expectedScripts = Get-ExperimentCanonicalPath $Expected.scriptRoot
        $actualWeb = Get-ExperimentCanonicalPath $Actual.WebRoot
        $expectedWeb = Get-ExperimentCanonicalPath $Expected.webRoot
    } catch { return $false }
    return [string]$Actual.Protocol -ceq 'troubleshootjs-execution-provenance-v1' -and
        $actualRoot.Equals($expectedRoot, [StringComparison]::OrdinalIgnoreCase) -and
        $actualSource.Equals($expectedSource, [StringComparison]::OrdinalIgnoreCase) -and
        $actualScripts.Equals($expectedScripts, [StringComparison]::OrdinalIgnoreCase) -and
        $actualWeb.Equals($expectedWeb, [StringComparison]::OrdinalIgnoreCase) -and
        [string]$Actual.SourceDigest -ceq [string]$Expected.sourceDigest -and
        [string]$Actual.ScriptDigest -ceq [string]$Expected.scriptDigest -and
        [string]$Actual.WebDigest -ceq [string]$Expected.webDigest -and
        [string]$Actual.Digest -ceq [string]$Expected.digest -and
        [long]$Actual.FileCount -eq [long]$Expected.fileCount
}

function Test-SourceNegativeRepositoryStateEqual($Actual, $Expected,
        $ExpectedExecutionProvenance) {
    if ($null -eq $Actual -or $null -eq $Expected) { return $false }
    return [string]$Actual.headSha -ceq [string]$Expected.headSha -and
        [string]$Actual.sourceVerifierDigest -ceq [string]$Expected.sourceVerifierDigest -and
        [long]$Actual.sourceVerifierFileCount -eq [long]$Expected.sourceVerifierFileCount -and
        [bool]$Actual.dirty -eq [bool]$Expected.dirty -and
        [string]$Actual.statusText -ceq [string]$Expected.statusText -and
        (Test-SourceNegativeExecutionProvenanceEqual `
            $Actual.executionProvenance $ExpectedExecutionProvenance)
}

function Get-SourceNegativeExpectedDiagnostic($Definition, $Proof) {
    return 'Console failure: exception in runCircuit java.lang.IllegalStateException: ' +
        'Generated board verification failed for ' + [string]$Definition.family + '/' +
        [string]$Definition.topology + ', seed ' + [string]$Definition.seed + ': ' +
        ([string]$Definition.expectedMarker).Substring(5) +
        ' nonce=' + [string]$Proof.nonce +
        ' route=' + [string]$Proof.routeId +
        ' request=' + [string]$Proof.requestId +
        ' execution=' + [string]$Proof.executionDigest +
        ' experiment=' + [string]$Definition.id +
        ' run=' + [string]$Proof.runId
}

function Get-SourceNegativeProofArtifact($EvidenceParent) {
    $parent = Get-ExperimentCanonicalPath $EvidenceParent
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
        Throw-SourceNegativeUnproven "Child evidence directory was missing: $parent"
    }
    Assert-VerifierNoReparseAncestors $parent
    Assert-VerifierNoReparseTree $parent
    $files = @(Get-ChildItem -LiteralPath $parent -Recurse -File -ErrorAction Stop |
        Where-Object { $_.Name -ceq 'task43p-source-negative-proof.json' })
    if ($files.Count -ne 1) {
        Throw-SourceNegativeUnproven ('Expected exactly one task43p-source-negative-proof.json; found ' +
            [string]$files.Count + '.')
    }
    $path = Get-ExperimentCanonicalPath $files[0].FullName
    if (-not (Test-VerifierPhysicalChildPath $parent $path)) {
        Throw-SourceNegativeUnproven 'Child proof artifact escaped its evidence namespace.'
    }
    $runDirectory = Get-ExperimentCanonicalPath (Split-Path -Parent $path)
    $runName = Split-Path -Leaf $runDirectory
    if ($runName -notmatch '^run-[0-9a-f]{32}$' -or
            -not (Get-ExperimentCanonicalPath (Split-Path -Parent $runDirectory)).Equals(
                $parent, [StringComparison]::OrdinalIgnoreCase)) {
        Throw-SourceNegativeUnproven 'Child proof artifact was not directly beneath one run-{childRunId} directory.'
    }
    try {
        $text = [IO.File]::ReadAllText($path)
        $artifact = $text | ConvertFrom-Json -ErrorAction Stop
    } catch {
        Throw-SourceNegativeUnproven ('Child proof artifact was not valid JSON: ' +
            (Get-ExperimentErrorMessage $_))
    }
    return [pscustomobject]@{ Path = $path; RunId = $runName.Substring(4); Value = $artifact }
}

function Assert-SourceNegativeProof($Artifact, [string]$ArtifactRunId,
        [string]$ArtifactPath, $Definition, $RepositoryBefore,
        $ExpectedExecutionProvenance, [string]$ExpectedBeforeHash,
        [string]$ExpectedAfterHash, [string]$ExpectedExecutionDigest,
        [int]$ChildExit, [int]$StartupSettleMilliseconds) {
    if ($ChildExit -ne 1) {
        Throw-SourceNegativeUnproven "Child exit was $ChildExit rather than exact expected exit 1."
    }
    $rootAllowed = @('protocol', 'exit', 'experiment',
        'sourceMutationBeforeSha256', 'sourceMutationAfterSha256',
        'compiledExecutionDigest', 'repositoryBefore', 'repositoryAfter',
        'browserStartupSettleMilliseconds', 'forcedNegativeProof')
    if ($Definition.id -ceq 'public-remove-action-disabled') {
        $rootAllowed += 'publicRemove'
    }
    Assert-SourceNegativeObject $Artifact 'proof' $rootAllowed $rootAllowed
    Assert-SourceNegativeString $Artifact.protocol 'proof.protocol'
    if ([string]$Artifact.protocol -cne 'troubleshootjs-task43p-source-negative-proof-v1') {
        Throw-SourceNegativeUnproven 'Child proof artifact carried an unexpected protocol.'
    }
    Assert-SourceNegativeInteger $Artifact.exit 'proof.exit' 0 2
    if ([int]$Artifact.exit -ne 1) { Throw-SourceNegativeUnproven 'Proof exit was not 1.' }
    $experimentAllowed = @('id', 'relativePath', 'family', 'topology', 'seed', 'expectedMarker')
    Assert-SourceNegativeObject $Artifact.experiment 'proof.experiment' `
        $experimentAllowed $experimentAllowed
    foreach ($name in @('id', 'relativePath', 'family', 'topology', 'expectedMarker')) {
        Assert-SourceNegativeString $Artifact.experiment.$name ('proof.experiment.' + $name)
    }
    $expectedRelativePath = ([string]$Definition.relativePath).Replace('\', '/')
    if ([string]$Artifact.experiment.id -cne [string]$Definition.id -or
            [string]$Artifact.experiment.relativePath -cne $expectedRelativePath -or
            [string]$Artifact.experiment.family -cne [string]$Definition.family -or
            [string]$Artifact.experiment.topology -cne [string]$Definition.topology -or
            [string]$Artifact.experiment.expectedMarker -cne [string]$Definition.expectedMarker) {
        Throw-SourceNegativeUnproven 'Child proof experiment identity did not match the selected source mutation.'
    }
    Assert-SourceNegativeInteger $Artifact.experiment.seed 'proof.experiment.seed' `
        $Definition.seed $Definition.seed
    Assert-SourceNegativeSha256 $Artifact.sourceMutationBeforeSha256 `
        'proof.sourceMutationBeforeSha256'
    Assert-SourceNegativeSha256 $Artifact.sourceMutationAfterSha256 `
        'proof.sourceMutationAfterSha256'
    Assert-SourceNegativeSha256 $Artifact.compiledExecutionDigest `
        'proof.compiledExecutionDigest'
    if ([string]$Artifact.sourceMutationBeforeSha256 -cne $ExpectedBeforeHash -or
            [string]$Artifact.sourceMutationAfterSha256 -cne $ExpectedAfterHash -or
            [string]$Artifact.compiledExecutionDigest -cne $ExpectedExecutionDigest) {
        Throw-SourceNegativeUnproven 'Child proof source or compiled execution digest did not match the mutated disposable image.'
    }
    Assert-SourceNegativeInteger $Artifact.browserStartupSettleMilliseconds `
        'proof.browserStartupSettleMilliseconds' 0 45000
    if ([int]$Artifact.browserStartupSettleMilliseconds -ne $StartupSettleMilliseconds) {
        Throw-SourceNegativeUnproven 'Child proof startup-settle value did not match the fixed 30000 ms route contract.'
    }
    Assert-SourceNegativeRepositoryState $Artifact.repositoryBefore 'proof.repositoryBefore'
    Assert-SourceNegativeRepositoryState $Artifact.repositoryAfter 'proof.repositoryAfter'
    if (-not (Test-SourceNegativeRepositoryStateEqual $Artifact.repositoryBefore `
            $RepositoryBefore $ExpectedExecutionProvenance) -or
            -not (Test-SourceNegativeRepositoryStateEqual $Artifact.repositoryAfter `
                $RepositoryBefore $ExpectedExecutionProvenance) -or
            -not (Test-SourceNegativeRepositoryStateEqual $Artifact.repositoryBefore `
                $Artifact.repositoryAfter $ExpectedExecutionProvenance)) {
        Throw-SourceNegativeUnproven 'Child proof repository before/after provenance was not unchanged and exact.'
    }

    $proofAllowed = @('protocol', 'invocation', 'expectedMarker', 'expectedRoute',
        'runId', 'routeId', 'nonce', 'requestId', 'executionDigest',
        'markerObserved', 'observedMarker', 'markerRunId', 'markerRouteId',
        'markerExpectedMarker', 'anchoredDiagnosticProven', 'anchoredJavaDiagnostic',
        'diagnosticExpectedMarker', 'diagnosticRunId', 'diagnosticRouteId',
        'diagnosticBaselineHead', 'routePassedAfterCleanup', 'finalCleanupProven',
        'routePassed', 'cleanupProven', 'invalidated')
    Assert-SourceNegativeObject $Artifact.forcedNegativeProof `
        'proof.forcedNegativeProof' $proofAllowed $proofAllowed
    Assert-SourceNegativeString $Artifact.forcedNegativeProof.protocol `
        'proof.forcedNegativeProof.protocol'
    if ([string]$Artifact.forcedNegativeProof.protocol -cne
            'troubleshootjs-forced-negative-proof-v1') {
        Throw-SourceNegativeUnproven 'Child proof carried an unexpected forced-negative proof protocol.'
    }
    foreach ($name in @('invocation', 'markerObserved', 'anchoredDiagnosticProven',
            'routePassedAfterCleanup', 'finalCleanupProven', 'routePassed',
            'cleanupProven', 'invalidated')) {
        Assert-SourceNegativeBoolean $Artifact.forcedNegativeProof.$name `
            ('proof.forcedNegativeProof.' + $name)
    }
    foreach ($name in @('expectedMarker', 'expectedRoute', 'runId', 'routeId', 'nonce',
            'requestId', 'executionDigest', 'observedMarker', 'markerRunId',
            'markerRouteId', 'markerExpectedMarker', 'anchoredJavaDiagnostic',
            'diagnosticExpectedMarker', 'diagnosticRunId', 'diagnosticRouteId',
            'diagnosticBaselineHead')) {
        Assert-SourceNegativeString $Artifact.forcedNegativeProof.$name `
            ('proof.forcedNegativeProof.' + $name)
    }
    $proof = $Artifact.forcedNegativeProof
    foreach ($name in @('runId', 'routeId', 'nonce', 'requestId')) {
        if ([string]$proof.$name -notmatch '^[A-Za-z0-9._-]{8,128}$') {
            Throw-SourceNegativeUnproven "Forced-negative proof field '$name' carried a malformed opaque token."
        }
    }
    $expectedRoute = 'task43p source-negative ' + [string]$Definition.id
    if (-not $proof.invocation -or
            [string]$proof.expectedMarker -cne [string]$Definition.expectedMarker -or
            [string]$proof.expectedRoute -cne $expectedRoute -or
            [string]$proof.runId -cne $ArtifactRunId -or
            [string]$proof.executionDigest -cne $ExpectedExecutionDigest -or
            -not $proof.markerObserved -or
            [string]$proof.observedMarker -cne [string]$Definition.expectedMarker -or
            [string]$proof.markerRunId -cne [string]$proof.runId -or
            [string]$proof.markerRouteId -cne [string]$proof.routeId -or
            [string]$proof.markerExpectedMarker -cne [string]$Definition.expectedMarker -or
            -not $proof.anchoredDiagnosticProven -or
            [string]$proof.diagnosticExpectedMarker -cne [string]$Definition.expectedMarker -or
            [string]$proof.diagnosticRunId -cne [string]$proof.runId -or
            [string]$proof.diagnosticRouteId -cne [string]$proof.routeId -or
            [string]$proof.diagnosticBaselineHead -cne $publishedBaselineSha -or
            -not $proof.routePassedAfterCleanup -or -not $proof.finalCleanupProven -or
            -not $proof.routePassed -or -not $proof.cleanupProven -or $proof.invalidated) {
        Throw-SourceNegativeUnproven 'Child forced-negative proof flags or identities were incomplete or mismatched.'
    }
    Assert-SourceNegativeSha256 $proof.executionDigest 'proof.forcedNegativeProof.executionDigest'
    if ([string]$proof.anchoredJavaDiagnostic -cne (Get-SourceNegativeExpectedDiagnostic $Definition $proof)) {
        Throw-SourceNegativeUnproven 'Child anchored Java diagnostic did not exactly match the source-negative contract.'
    }
    if ($Definition.id -ceq 'public-remove-action-disabled') {
        try {
            Assert-Task43PPublicRemoveEvidence $Artifact.publicRemove $proof.runId `
                $proof.routeId $proof.nonce $proof.requestId $proof.executionDigest
        } catch {
            Throw-SourceNegativeUnproven ('Public Remove input/controller proof was incomplete: ' +
                (Get-ExperimentErrorMessage $_))
        }
    }
    return $true
}

function Invoke-SourcePublicRemoveProofContractCases($Artifact, [string]$ArtifactRunId,
        [string]$ArtifactPath, $Repository, $Provenance, [string]$BeforeHash,
        [string]$AfterHash) {
    $value = $Artifact | ConvertTo-Json -Depth 25 | ConvertFrom-Json
    $definition = [pscustomobject]@{
        id = 'public-remove-action-disabled'
        relativePath = 'src/com/lushprojects/circuitjs1/client/PcbWorkbenchController.java'
        family = 'LED_INDICATOR'; topology = 'DIRECT_SERIES'; seed = 3
        expectedMarker = 'FAIL:task43p-public-remove-direct-control-passed'
    }
    $value.experiment = $definition
    $proof = $value.forcedNegativeProof
    foreach ($name in @('expectedMarker', 'observedMarker', 'markerExpectedMarker',
            'diagnosticExpectedMarker')) { $proof.$name = $definition.expectedMarker }
    $proof.expectedRoute = 'task43p source-negative ' + $definition.id
    $proof.anchoredJavaDiagnostic = Get-SourceNegativeExpectedDiagnostic $definition $proof
    $state = [pscustomobject]@{
        powerOff = $true; selectedInstalledR1 = $true; emptyTray = $true
        removedOriginalVisible = $false; removeButtonCount = 1
        removeButtonDisabled = $true; removeButtonVisible = $true
        removeButtonHitTest = $true; liftLeadEnabled = $true
    }
    $direct = [pscustomobject]@{
        protocol = 'TSJ-TASK43P-PUBLIC-REMOVE-CONTROL-1'; method = 'PcbWorkbenchController.dispatch'
        componentId = 'R1'; family = 'LED_INDICATOR'; seed = 3; partId = 'R1_ORIGINAL'
        availableBefore = $true; dispatchReturned = $true; removedAfter = $true
        cleanupDispatchReturned = $true; restoredAfter = $true; ownerRestored = $true
        runId = $proof.runId; routeId = $proof.routeId; nonce = $proof.nonce
        requestId = $proof.requestId; executionDigest = $proof.executionDigest
    }
    $public = [pscustomobject]@{
        protocol = 'TSJ-TASK43P-PUBLIC-REMOVE-1'; runId = $proof.runId; routeId = $proof.routeId
        navigationMarker = ('a' * 32)
        normalPlayerUrl = ('http://127.0.0.1:45678/circuitjs.html?tsjChallenge=led&seed=3&' +
            'tsjVerifyGeometry=true&tsjVerifierRun=' + $proof.runId + '&tsjVerifierRoute=' + $proof.routeId +
            '&tsjVerifierNavigation=' + ('a' * 32))
        inputMethod = 'Input.dispatchMouseEvent'; inputEventCount = 2
        directControllerPositive = $direct; before = $state; after = $state
    }
    $value | Add-Member -NotePropertyName publicRemove -NotePropertyValue $public
    [void](Assert-SourceNegativeProof $value $ArtifactRunId $ArtifactPath $definition `
        $Repository $Provenance $BeforeHash $AfterHash $Provenance.digest 1 30000)
    $cases = @(
        @{ Name = 'missing-player-proof'; Mutate = { param($v) $v.PSObject.Properties.Remove('publicRemove') } },
        @{ Name = 'missing-after-field'; Mutate = { param($v) $v.publicRemove.after.PSObject.Properties.Remove('emptyTray') } },
        @{ Name = 'string-boolean'; Mutate = { param($v) $v.publicRemove.before.powerOff = 'True' } },
        @{ Name = 'enabled-button'; Mutate = { param($v) $v.publicRemove.before.removeButtonDisabled = $false } },
        @{ Name = 'powered-board'; Mutate = { param($v) $v.publicRemove.before.powerOff = $false } },
        @{ Name = 'nonempty-tray'; Mutate = { param($v) $v.publicRemove.after.emptyTray = $false } },
        @{ Name = 'removed-part'; Mutate = { param($v) $v.publicRemove.after.removedOriginalVisible = $true } },
        @{ Name = 'blocked-hit-test'; Mutate = { param($v) $v.publicRemove.before.removeButtonHitTest = $false } },
        @{ Name = 'provider-only-control'; Mutate = { param($v) $v.publicRemove.directControllerPositive.method = 'ResistorSlotController.removeInstalledPart' } },
        @{ Name = 'dispatch-failed'; Mutate = { param($v) $v.publicRemove.directControllerPositive.dispatchReturned = $false } },
        @{ Name = 'owner-not-restored'; Mutate = { param($v) $v.publicRemove.directControllerPositive.ownerRestored = $false } },
        @{ Name = 'wrong-request'; Mutate = { param($v) $v.publicRemove.directControllerPositive.requestId = 'wrong-request' } },
        @{ Name = 'wrong-family'; Mutate = { param($v) $v.publicRemove.directControllerPositive.family = 'RC_DELAY' } },
        @{ Name = 'string-seed'; Mutate = { param($v) $v.publicRemove.directControllerPositive.seed = '3' } },
        @{ Name = 'fractional-event-count'; Mutate = { param($v) $v.publicRemove.inputEventCount = 2.1 } },
        @{ Name = 'developer-query'; Mutate = { param($v) $v.publicRemove.normalPlayerUrl += '&tsjVerifyTask43P=true' } },
        @{ Name = 'wrong-player-run'; Mutate = { param($v) $v.publicRemove.runId = 'wrong-player-run' } },
        @{ Name = 'wrong-navigation'; Mutate = { param($v) $v.publicRemove.navigationMarker = ('b' * 32) } },
        @{ Name = 'unknown-field'; Mutate = { param($v) $v.publicRemove.after | Add-Member -NotePropertyName unexpected -NotePropertyValue $true } }
    )
    foreach ($case in $cases) {
        $candidate = $value | ConvertTo-Json -Depth 25 | ConvertFrom-Json
        & $case.Mutate $candidate
        $rejected = $false
        try {
            [void](Assert-SourceNegativeProof $candidate $ArtifactRunId $ArtifactPath $definition `
                $Repository $Provenance $BeforeHash $AfterHash $Provenance.digest 1 30000)
        } catch {
            if (-not (Test-SourceNegativeUnprovenException $_)) { throw }
            $rejected = $true
        }
        if (-not $rejected) { throw ('Public Remove proof accepted malformed case ' + $case.Name) }
    }
    Write-Host 'PASS:public Remove requires separately bound dispatch, real mouse input, unpowered enabled context, and unchanged installed/tray state'
}

function Invoke-SourceNegativeProofContractProbe() {
    $probeRoot = Join-Path $tempRoot `
        ('TroubleshootJS\source-negative-contract-probe-' + [Guid]::NewGuid().ToString('N'))
    $probeRetain = $true
    try {
        New-SourceExperimentDirectory $probeRoot 'source-negative contract probe' -Force
        Assert-VerifierNoReparseAncestors $probeRoot
        [void](Assert-VerifierPhysicalOwnedPath $tempRoot $probeRoot -ValidateTree)
        $script:contractProbeRemovalTarget = Get-ExperimentCanonicalPath $probeRoot
        $runId = [Guid]::NewGuid().ToString('N')
        $routeId = 'route-' + [Guid]::NewGuid().ToString('N')
        $nonce = 'nonce-' + [Guid]::NewGuid().ToString('N')
        $requestId = 'request-' + [Guid]::NewGuid().ToString('N')
        $definition = [ordered]@{
            id = 'contract-probe'
            relativePath = 'src/com/example/ContractProbe.java'
            family = 'LED_INDICATOR'
            topology = 'DIRECT_SERIES'
            seed = 0
            expectedMarker = 'FAIL:task43p-contract-probe:fixture'
        }
        $repository = Get-RepositoryState
        $provenance = Get-ExperimentExecutionProvenance $repositoryRoot
        $artifactProvenance = [ordered]@{
            Protocol = [string]$provenance.protocol
            RepositoryRoot = [string]$provenance.repositoryRoot
            SourceRoot = [string]$provenance.sourceRoot
            ScriptRoot = [string]$provenance.scriptRoot
            WebRoot = [string]$provenance.webRoot
            SourceDigest = [string]$provenance.sourceDigest
            ScriptDigest = [string]$provenance.scriptDigest
            WebDigest = [string]$provenance.webDigest
            Digest = [string]$provenance.digest
            FileCount = [long]$provenance.fileCount
        }
        $artifactRepository = [ordered]@{
            headSha = [string]$repository.headSha
            sourceVerifierDigest = [string]$repository.sourceVerifierDigest
            sourceVerifierFileCount = [int]$repository.sourceVerifierFileCount
            dirty = [bool]$repository.dirty
            statusText = [string]$repository.statusText
            executionProvenance = $artifactProvenance
        }
        $beforeHash = Get-BytesHash ([Text.Encoding]::UTF8.GetBytes('contract-before'))
        $afterHash = Get-BytesHash ([Text.Encoding]::UTF8.GetBytes('contract-after'))
        $proof = [ordered]@{
            protocol = 'troubleshootjs-forced-negative-proof-v1'
            invocation = $true
            expectedMarker = $definition.expectedMarker
            expectedRoute = 'task43p source-negative ' + $definition.id
            runId = $runId
            routeId = $routeId
            nonce = $nonce
            requestId = $requestId
            executionDigest = [string]$provenance.digest
            markerObserved = $true
            observedMarker = $definition.expectedMarker
            markerRunId = $runId
            markerRouteId = $routeId
            markerExpectedMarker = $definition.expectedMarker
            anchoredDiagnosticProven = $true
            anchoredJavaDiagnostic = ''
            diagnosticExpectedMarker = $definition.expectedMarker
            diagnosticRunId = $runId
            diagnosticRouteId = $routeId
            diagnosticBaselineHead = $publishedBaselineSha
            routePassedAfterCleanup = $true
            finalCleanupProven = $true
            routePassed = $true
            cleanupProven = $true
            invalidated = $false
        }
        $proof.anchoredJavaDiagnostic = Get-SourceNegativeExpectedDiagnostic $definition $proof
        $artifact = [ordered]@{
            protocol = 'troubleshootjs-task43p-source-negative-proof-v1'
            exit = 1
            experiment = $definition
            sourceMutationBeforeSha256 = $beforeHash
            sourceMutationAfterSha256 = $afterHash
            compiledExecutionDigest = [string]$provenance.digest
            repositoryBefore = $artifactRepository
            repositoryAfter = $artifactRepository
            browserStartupSettleMilliseconds = 30000
            forcedNegativeProof = $proof
        }
        $artifactDirectory = Join-Path $probeRoot ('run-' + $runId)
        New-SourceExperimentDirectory $artifactDirectory 'source-negative contract proof run' -Force
        $artifactPath = Join-Path $artifactDirectory 'task43p-source-negative-proof.json'
        Write-SourceExperimentText $artifactPath ($artifact | ConvertTo-Json -Depth 20) `
            'source-negative contract fixture'
        $parsed = (Get-SourceNegativeProofArtifact $probeRoot).Value
        [void](Assert-SourceNegativeProof $parsed $runId $artifactPath $definition `
            $repository $provenance $beforeHash $afterHash $provenance.digest 1 30000)
        Invoke-SourcePublicRemoveProofContractCases $parsed $runId $artifactPath `
            $repository $provenance $beforeHash $afterHash

        $cases = @(
            [pscustomobject]@{ Name = 'raw1'; Mutate = { param($value) return $null } }
            [pscustomobject]@{ Name = 'wrong-id'; Mutate = { param($value) $value.experiment.id = 'other-id' } }
            [pscustomobject]@{ Name = 'wrong-hash'; Mutate = { param($value) $value.sourceMutationBeforeSha256 = ('0' * 64) } }
            [pscustomobject]@{ Name = 'wrong-digest'; Mutate = { param($value) $value.compiledExecutionDigest = ('1' * 64) } }
            [pscustomobject]@{ Name = 'wrong-anchor'; Mutate = { param($value) $value.forcedNegativeProof.anchoredJavaDiagnostic = 'wrong diagnostic' } }
            [pscustomobject]@{ Name = 'final-cleanup-false'; Mutate = { param($value) $value.forcedNegativeProof.finalCleanupProven = $false } }
            [pscustomobject]@{ Name = 'missing-field'; Mutate = { param($value) $value.forcedNegativeProof.PSObject.Properties.Remove('cleanupProven') } }
        )
        foreach ($case in $cases) {
            $candidate = if ($case.Name -eq 'raw1') { $null } else {
                $artifact | ConvertTo-Json -Depth 20 | ConvertFrom-Json
            }
            & $case.Mutate $candidate
            $rejected = $false
            try {
                [void](Assert-SourceNegativeProof $candidate $runId $artifactPath $definition `
                    $repository $provenance $beforeHash $afterHash $provenance.digest 1 30000)
            } catch {
                if (-not (Test-SourceNegativeUnprovenException $_)) {
                    throw "Malformed source-negative case '$($case.Name)' did not produce the exact InvalidDataException/SOURCE_NEGATIVE_UNPROVEN contract."
                }
                $rejected = $true
            }
            if (-not $rejected) {
                throw "Source-negative contract probe accepted malformed case '$($case.Name)'."
            }
        }
        $typedRejectionProven = $false
        try {
            Throw-SourceNegativeUnproven 'contract probe typed rejection'
        } catch {
            $typedRejectionProven = Test-SourceNegativeUnprovenException $_
        }
        if (-not $typedRejectionProven) {
            throw 'Source-negative contract probe could not prove the exact InvalidDataException and SOURCE_NEGATIVE_UNPROVEN prefix contract.'
        }
        $classificationCompile = [ordered]@{
            attempted = $true
            exit = 0
        }
        $classificationRuntime = [ordered]@{
            attempted = $true
            exit = 2
            underlyingExit = 1
            status = 'CAUGHT'
            reason = 'Disposable preview diagnostics could not be read.'
            childExit = 1
            childProofAccepted = $true
            previewProcessAbsentProven = $true
            previewListenerAbsenceProven = $true
            previewStopped = $true
        }
        if (Test-SourceExperimentCaught '' $classificationCompile $classificationRuntime `
                $true $true $true) {
            throw 'Source-negative contract probe accepted a late diagnostic-read infrastructure failure as CAUGHT.'
        }
        $unregisteredDeletionRejected = $false
        try {
            Remove-SourceExperimentTree (Join-Path $probeRoot 'unregistered-descendant') `
                'unregistered source-negative cleanup target'
        } catch { $unregisteredDeletionRejected = $true }
        if (-not $unregisteredDeletionRejected) {
            throw 'Source-negative contract probe accepted an unregistered cleanup descendant.'
        }
        Remove-SourceExperimentTree $probeRoot 'source-negative contract probe'
        $probeRetain = $false
        $script:contractProbeRemovalTarget = $null
        Write-Host 'PASS:source-negative proof contract complete proof accepted; raw1, wrong identity/hash/digest/anchor, cleanup, missing-field, late-diagnostic classification, and unregistered cleanup-target cases rejected'
    } catch {
        if ($probeRetain -and (Test-Path -LiteralPath $probeRoot)) {
            throw ((Get-ExperimentErrorMessage $_) + " Evidence retained at '$probeRoot'.")
        }
        throw
    }
}

function Test-SourceExperimentCaught($ErrorText, $Compile, $Runtime,
        [bool]$RuntimeAgainstMutatedSource, [bool]$Restored,
        [bool]$RepositoryUnchanged) {
    if ($null -eq $Compile -or $null -eq $Runtime -or
            -not [String]::IsNullOrWhiteSpace([string]$ErrorText)) {
        return $false
    }
    return [bool]$Compile.attempted -and
        (Test-ExperimentStrictIntegralValue $Compile.exit 0 0) -and
        [int]$Compile.exit -eq 0 -and
        [bool]$Runtime.attempted -and
        (Test-ExperimentStrictIntegralValue $Runtime.exit 0 1) -and
        [int]$Runtime.exit -eq 1 -and
        (Test-ExperimentStrictIntegralValue $Runtime.underlyingExit 0 1) -and
        [int]$Runtime.underlyingExit -eq 1 -and
        [string]$Runtime.status -ceq 'CAUGHT' -and
        [String]::IsNullOrWhiteSpace([string]$Runtime.reason) -and
        (Test-ExperimentStrictIntegralValue $Runtime.childExit 0 1) -and
        [int]$Runtime.childExit -eq 1 -and
        [bool]$Runtime.childProofAccepted -and
        [bool]$Runtime.previewProcessAbsentProven -and
        [bool]$Runtime.previewListenerAbsenceProven -and
        [bool]$Runtime.previewStopped -and
        $RuntimeAgainstMutatedSource -and $Restored -and $RepositoryUnchanged
}

function Get-ExactTextAnchorCount([string]$Text, [string]$Needle) {
    if ($null -eq $Text -or $null -eq $Needle -or
            [String]::IsNullOrEmpty($Needle)) {
        return 0
    }
    $count = 0
    $offset = 0
    while ($offset -le $Text.Length) {
        $index = $Text.IndexOf($Needle, $offset, [StringComparison]::Ordinal)
        if ($index -lt 0) { break }
        $count++
        # Advance one character so overlapping occurrences are counted too.
        # A duplicate overlapping anchor is just as ambiguous as two
        # separated anchors and must fail closed.
        $offset = $index + 1
    }
    return $count
}

function Invoke-ExactTextReplacement([string]$Path, [string]$Needle, [string]$Replacement) {
    try {
        $text = [IO.File]::ReadAllText($Path)
    } catch {
        Throw-SourceExperimentInfrastructure "Could not read source mutation target '$Path'." $_
    }
    $anchorCount = Get-ExactTextAnchorCount $text $Needle
    if ($anchorCount -ne 1) {
        throw "Expected exactly one source mutation anchor in $Path; found $anchorCount."
    }
    $first = $text.IndexOf($Needle, [StringComparison]::Ordinal)
    $updated = $text.Substring(0, $first) + $Replacement +
        $text.Substring($first + $Needle.Length)
    Write-SourceExperimentText $Path $updated 'source mutation'
    return [pscustomobject]@{ AnchorCount = $anchorCount }
}

function Invoke-MutationPreflightCase($Definition, [string]$PreflightRoot) {
    $id = [string]$Definition.id
    $relativePath = [string]$Definition.relativePath
    $sourcePath = Join-Path $repositoryRoot $relativePath
    $targetPath = Join-Path $PreflightRoot ($id + '.source')
    $beforeBytes = $null
    $afterBytes = $null
    $restoredBytes = $null
    $anchorCount = 0
    $restoreError = ''
    $errorText = ''
    $mutationApplied = $false
    $restoredExactly = $false
    try {
        $canonicalSource = Get-ExperimentCanonicalPath $sourcePath
        $canonicalRepository = Get-ExperimentCanonicalPath $repositoryRoot
        if (-not (Test-VerifierPhysicalChildPath $canonicalRepository $canonicalSource) -or
                -not (Test-Path -LiteralPath $canonicalSource -PathType Leaf -ErrorAction Stop)) {
            Throw-SourceExperimentInfrastructure "Mutation preflight source '$relativePath' was not a physical repository file."
        }
        $canonicalTarget = Get-ExperimentCanonicalPath $targetPath
        if (-not (Test-VerifierPhysicalChildPath $PreflightRoot $canonicalTarget)) {
            Throw-SourceExperimentInfrastructure "Mutation preflight target '$targetPath' escaped its task-owned namespace."
        }
        $beforeBytes = [IO.File]::ReadAllBytes($canonicalSource)
        $beforeHash = Get-BytesHash $beforeBytes
        # This is the only disposable file used by the case.  It is created
        # from bytes so BOMs and mixed line endings survive the restore path.
        Write-SourceExperimentBytes $canonicalTarget $beforeBytes 'mutation preflight disposable source'
        $targetBeforeBytes = [IO.File]::ReadAllBytes($canonicalTarget)
        if (-not (Test-ByteArraysEqual $beforeBytes $targetBeforeBytes)) {
            Throw-SourceExperimentInfrastructure "Mutation preflight disposable source did not match repository bytes for $id."
        }
        $text = [IO.File]::ReadAllText($canonicalTarget)
        $anchorCount = Get-ExactTextAnchorCount $text ([string]$Definition.needle)
        if ($anchorCount -ne 1) {
            throw "Expected exactly one source mutation anchor in $canonicalSource; found $anchorCount."
        }
        [void](Invoke-ExactTextReplacement $canonicalTarget ([string]$Definition.needle) `
            ([string]$Definition.replacement))
        $mutationApplied = $true
        $afterBytes = [IO.File]::ReadAllBytes($canonicalTarget)
        if (Test-ByteArraysEqual $beforeBytes $afterBytes) {
            throw "Mutation preflight did not change bytes for $id."
        }
    } catch {
        $errorText = Get-ExperimentErrorMessage $_
    } finally {
        if ($null -ne $beforeBytes -and (Test-Path -LiteralPath $targetPath -PathType Leaf)) {
            try {
                Write-SourceExperimentBytes $targetPath $beforeBytes 'mutation preflight source restoration'
                $restoredBytes = [IO.File]::ReadAllBytes($targetPath)
                $restoredExactly = Test-ByteArraysEqual $beforeBytes $restoredBytes
                if (-not $restoredExactly) {
                    throw "Mutation preflight restore bytes differed for $id."
                }
            } catch {
                $restoreError = Get-ExperimentErrorMessage $_
                $restoredExactly = $false
            }
        } elseif ($null -ne $beforeBytes) {
            $restoreError = "Mutation preflight disposable source was missing during restore for $id."
        }
    }
    $beforeHash = if ($null -eq $beforeBytes) { '' } else { Get-BytesHash $beforeBytes }
    $afterHash = if ($null -eq $afterBytes) { '' } else { Get-BytesHash $afterBytes }
    $restoredHash = if ($null -eq $restoredBytes) { '' } else { Get-BytesHash $restoredBytes }
    $passed = $anchorCount -eq 1 -and $mutationApplied -and
        $null -ne $afterBytes -and -not (Test-ByteArraysEqual $beforeBytes $afterBytes) -and
        $restoredExactly -and [String]::IsNullOrWhiteSpace($restoreError) -and
        [String]::IsNullOrWhiteSpace($errorText)
    $exit = if ($passed) { 0 } else { 2 }
    $result = [ordered]@{
        id = $id
        relativePath = $relativePath
        anchorCount = [int]$anchorCount
        beforeSha256 = [string]$beforeHash
        afterSha256 = [string]$afterHash
        restoredSha256 = [string]$restoredHash
        beforeByteLength = if ($null -eq $beforeBytes) { 0 } else { [int]$beforeBytes.Length }
        afterByteLength = if ($null -eq $afterBytes) { 0 } else { [int]$afterBytes.Length }
        restoredExactly = [bool]$restoredExactly
        restoreError = [string]$restoreError
        error = [string]$errorText
        exit = [int]$exit
    }
    Write-Host ("MUTATION_PREFLIGHT id=$id anchorCount=$($result.anchorCount) " +
        "beforeSha256=$($result.beforeSha256) afterSha256=$($result.afterSha256) " +
        "restoredSha256=$($result.restoredSha256) exit=$($result.exit)")
    return [pscustomobject]$result
}

function Invoke-MutationPreflightCanaries([string]$PreflightRoot) {
    $cases = @(
        [pscustomobject]@{ Name = 'zero-anchor'; Text = 'present'; Needle = 'missing'; Replacement = 'changed'; WithBom = $false; ExpectedCount = 0; Reject = $true },
        [pscustomobject]@{ Name = 'duplicate-anchor'; Text = "needle`r`nneedle`n"; Needle = 'needle'; Replacement = 'changed'; WithBom = $false; ExpectedCount = 2; Reject = $true },
        [pscustomobject]@{ Name = 'overlapping-anchor'; Text = 'aaaa'; Needle = 'aaa'; Replacement = 'b'; WithBom = $false; ExpectedCount = 2; Reject = $true },
        [pscustomobject]@{ Name = 'positive-bom-mixed-restore'; Text = "before`r`nneedle`nafter`r`n"; Needle = 'needle'; Replacement = 'changed'; WithBom = $true; ExpectedCount = 1; Reject = $false }
    )
    $results = @()
    foreach ($case in $cases) {
        $path = Join-Path $PreflightRoot ('__canary-' + $case.Name + '.source')
        $encoding = [Text.UTF8Encoding]::new($false)
        $payload = $encoding.GetBytes([string]$case.Text)
        if ($case.WithBom) {
            $preamble = ([Text.UTF8Encoding]::new($true)).GetPreamble()
            $bytes = New-Object byte[] ($preamble.Length + $payload.Length)
            [Array]::Copy($preamble, 0, $bytes, 0, $preamble.Length)
            [Array]::Copy($payload, 0, $bytes, $preamble.Length, $payload.Length)
        } else {
            $bytes = $payload
        }
        $sourceHadBom = $bytes.Length -ge 3 -and
            $bytes[0] -eq 0xef -and $bytes[1] -eq 0xbb -and $bytes[2] -eq 0xbf
        $beforeHash = Get-BytesHash $bytes
        $rejectionText = ''
        $restoreError = ''
        $anchorCount = Get-ExactTextAnchorCount ([string]$case.Text) ([string]$case.Needle)
        $mutationApplied = $false
        $afterBytes = $null
        try {
            Write-SourceExperimentBytes $path $bytes ("mutation preflight $($case.Name) canary")
            [void](Invoke-ExactTextReplacement $path ([string]$case.Needle) ([string]$case.Replacement))
            $mutationApplied = $true
            $afterBytes = [IO.File]::ReadAllBytes($path)
        } catch {
            $rejectionText = Get-ExperimentErrorMessage $_
        } finally {
            try {
                if (Test-Path -LiteralPath $path -PathType Leaf) {
                    Write-SourceExperimentBytes $path $bytes ("mutation preflight $($case.Name) canary restore")
                } else {
                    $restoreError = 'canary disposable source was missing during restore'
                }
            } catch {
                $restoreError = Get-ExperimentErrorMessage $_
            }
        }
        $restoredBytes = if (Test-Path -LiteralPath $path -PathType Leaf) {
            [IO.File]::ReadAllBytes($path)
        } else { $null }
        $restored = $null -ne $restoredBytes -and
            (Test-ByteArraysEqual $bytes $restoredBytes)
        $expectedRejection = "Expected exactly one source mutation anchor in $path; found $($case.ExpectedCount)."
        $rejectionReasonMatched = $rejectionText -ceq $expectedRejection
        $mutationChanged = $mutationApplied -and $null -ne $afterBytes -and
            -not (Test-ByteArraysEqual $bytes $afterBytes)
        $behaviorProven = if ($case.Reject) {
            -not $mutationApplied -and $rejectionReasonMatched
        } else {
            $mutationChanged -and [String]::IsNullOrWhiteSpace($rejectionText)
        }
        $passed = $sourceHadBom -eq [bool]$case.WithBom -and
            $anchorCount -eq [int]$case.ExpectedCount -and $behaviorProven -and
            $restored -and [String]::IsNullOrWhiteSpace($restoreError)
        $exit = if ($passed) { 0 } else { 2 }
        $result = [pscustomobject][ordered]@{
            name = [string]$case.Name
            anchorCount = [int]$anchorCount
            expectedAnchorCount = [int]$case.ExpectedCount
            beforeSha256 = $beforeHash
            afterSha256 = if ($null -eq $afterBytes) { '' } else { Get-BytesHash $afterBytes }
            restoredSha256 = if ($null -eq $restoredBytes) { '' } else { Get-BytesHash $restoredBytes }
            withBom = [bool]$sourceHadBom
            mutationChanged = [bool]$mutationChanged
            rejectionReasonMatched = [bool]$rejectionReasonMatched
            restoredExactly = [bool]$restored
            rejectionError = [string]$rejectionText
            restoreError = [string]$restoreError
            exit = [int]$exit
        }
        Write-Host ("MUTATION_PREFLIGHT_CANARY name=$($result.name) " +
            "anchorCount=$($result.anchorCount) beforeSha256=$($result.beforeSha256) " +
            "afterSha256=$($result.afterSha256) restoredSha256=$($result.restoredSha256) " +
            "exit=$($result.exit)")
        $results += $result
    }
    return @($results)
}

function Invoke-MutationPreflight($Definitions, [string]$PreflightRoot) {
    if ($null -eq $Definitions -or @($Definitions).Count -ne 11) {
        Throw-SourceExperimentInfrastructure 'Mutation preflight requires exactly the full 11 source experiment definitions.'
    }
    New-SourceExperimentDirectory $PreflightRoot 'mutation preflight disposable root' -Force
    Assert-VerifierNoReparseAncestors $PreflightRoot
    if (-not (Test-VerifierPhysicalChildPath $tempRoot $PreflightRoot)) {
        Throw-SourceExperimentInfrastructure 'Mutation preflight root escaped the task-owned temp namespace.'
    }
    $results = @()
    foreach ($definition in @($Definitions)) {
        $results += Invoke-MutationPreflightCase $definition $PreflightRoot
    }
    $canaries = @(Invoke-MutationPreflightCanaries $PreflightRoot)
    $passed = @($results).Count -eq 11 -and
        @($results | Where-Object { $_.exit -ne 0 }).Count -eq 0 -and
        @($canaries | Where-Object { $_.exit -ne 0 }).Count -eq 0
    Write-Host ("TASK43P MUTATION PREFLIGHT " +
        $(if ($passed) { 'PREFLIGHT_PASS' } else { 'PREFLIGHT_FAILED' }) +
        " definitions=$(@($results).Count) canaries=$(@($canaries).Count) exit=$(if ($passed) { 0 } else { 2 })")
    return [pscustomobject]@{
        requested = $true
        passed = [bool]$passed
        exit = if ($passed) { 0 } else { 2 }
        definitions = @($results)
        canaries = @($canaries)
    }
}

function Copy-DisposableBuildTree([string]$Destination) {
    try {
        New-SourceExperimentDirectory $Destination 'disposable build tree' -Force
        Copy-Item -LiteralPath (Join-Path $repositoryRoot 'src') -Destination $Destination -Recurse -Force -ErrorAction Stop | Out-Null
        Copy-Item -LiteralPath (Join-Path $repositoryRoot 'war') -Destination $Destination -Recurse -Force -ErrorAction Stop | Out-Null
        $scriptsDestination = Join-Path $Destination 'scripts'
        New-SourceExperimentDirectory $scriptsDestination 'disposable script tree' -Force
        Copy-Item -LiteralPath (Join-Path $repositoryRoot 'scripts\build.ps1') `
            -Destination $scriptsDestination -Force -ErrorAction Stop | Out-Null
        Copy-Item -LiteralPath (Join-Path $repositoryRoot 'scripts\VerifierIsolation.psm1') `
            -Destination $scriptsDestination -Force -ErrorAction Stop | Out-Null
        Copy-Item -LiteralPath (Join-Path $repositoryRoot 'scripts\preview.ps1') `
            -Destination $scriptsDestination -Force -ErrorAction Stop | Out-Null
        $toolDestination = Join-Path $Destination '.tools\gwt-2.7.0'
        New-SourceExperimentDirectory $toolDestination 'disposable GWT tool tree' -Force
        $toolSource = Join-Path $repositoryRoot '.tools\gwt-2.7.0'
        foreach ($toolFile in @(Get-ChildItem -LiteralPath $toolSource -File -ErrorAction Stop)) {
            Copy-Item -LiteralPath $toolFile.FullName -Destination $toolDestination -Force -ErrorAction Stop | Out-Null
        }
    } catch {
        Throw-SourceExperimentInfrastructure "Could not create the disposable build tree '$Destination'." $_
    }
}

function Invoke-DisposableCompile([string]$Destination, [string]$SelectedJavaHome) {
    $buildPath = Join-Path $Destination 'scripts\build.ps1'
    $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $buildPath,
        '-Target', 'Compile', '-Style', 'OBF',
        '-ProcessTimeoutSeconds', [string]$ProcessTimeoutSeconds)
    if (-not [String]::IsNullOrWhiteSpace($SelectedJavaHome)) {
        $arguments += @('-JavaHome', $SelectedJavaHome)
    }
    $logPath = Join-Path $Destination 'disposable-compile.log'
    try {
        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        # Keep the child build's bounded Java/process identity checks, but do
        # not collect its complete textual output in this parent runspace.
        & $powershell @arguments *> $logPath
        $exitCode = [int]$LASTEXITCODE
        $tail = if (Test-Path -LiteralPath $logPath -PathType Leaf) {
            @(Get-Content -LiteralPath $logPath -Tail 48 -ErrorAction Stop)
        } else { @() }
        return [ordered]@{ exit = $exitCode; outputTail = $tail }
    } catch {
        $tail = @()
        if (Test-Path -LiteralPath $logPath -PathType Leaf) {
            try { $tail = @(Get-Content -LiteralPath $logPath -Tail 48 -ErrorAction Stop) } catch { }
        }
        return [ordered]@{
            exit = 2
            outputTail = @($tail + ('Disposable compile infrastructure error: ' +
                (Get-ExperimentErrorMessage $_)))
        }
    }
}

function Get-DisposablePreviewPort() {
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return [int](($listener.LocalEndpoint).Port)
    } finally {
        try { $listener.Stop() } catch { }
    }
}

function Invoke-DisposableRuntimeExtraction([string]$Destination, $Definition,
        [string]$SelectedJavaHome, $Compile, $RepositoryBefore,
        [string]$ExpectedBeforeHash, [string]$ExpectedAfterHash) {
    $runtime = [ordered]@{
        attempted = $false
        status = 'UNPROVEN'
        exit = 2
        underlyingExit = 2
        route = 'verify-browser.ps1 -BaseUrl <disposable-preview> -Task43PForcedNegative -Task43PSourceExperiment <definition> -Task43PStartupSettleMilliseconds 30000 -TimeoutSeconds 90'
        executionRepositoryRoot = ''
        executionWebRoot = ''
        executionScriptRoot = ''
        executionPreviewScript = ''
        executionProvenance = $null
        compiledExecutionDigest = ''
        executionRootsValidated = $false
        previewIdentity = $null
        wrapperPath = ''
        previewPort = 0
        previewStarted = $false
        previewProcessAbsentProven = $false
        previewListenerAbsenceProven = $false
        previewListenerInspection = $null
        previewStopped = $false
        childEvidenceDirectory = ''
        childExit = 2
        childProofPath = ''
        childRunId = ''
        childProof = $null
        childProofAccepted = $false
        childProofReason = ''
        childOutputTail = @()
        childErrorTail = @()
        previewOutputTail = @()
        previewErrorTail = @()
        outputTail = @()
        reason = ''
    }
    $previewProcess = $null
    $previewProcessId = 0
    $previewProcessStartTicks = 0L
    try {
        if ($null -eq $Compile -or -not $Compile.attempted -or $Compile.exit -ne 0) {
            $runtime.reason = 'Compiled disposable route was not attempted because the source build did not prove exit 0.'
            return $runtime
        }
        $runtime.attempted = $true
        $runtime.executionRepositoryRoot = Get-ExperimentCanonicalPath $Destination
        $runtime.executionWebRoot = Get-ExperimentCanonicalPath (Join-Path $Destination 'war')
        $runtime.executionScriptRoot = Get-ExperimentCanonicalPath (Join-Path $Destination 'scripts')
        $runtime.executionPreviewScript = Get-ExperimentCanonicalPath (
            Join-Path $runtime.executionScriptRoot 'preview.ps1')
        $runtime.executionProvenance = Get-ExperimentExecutionProvenance $Destination
        $runtime.compiledExecutionDigest = [string]$runtime.executionProvenance.digest
        $runtime.previewPort = Get-DisposablePreviewPort
        # Child evidence is a sibling namespace owned by the caller, never a
        # descendant of the disposable source/build copy. It therefore remains
        # available after a successful build-tree cleanup and on any retained
        # unproven experiment.
        $childEvidenceParent = Join-Path $evidenceRoot `
            ($runId + '-' + [string]$Definition.id + '-child-evidence')
        $runtime.childEvidenceDirectory = Get-ExperimentCanonicalPath $childEvidenceParent
        New-SourceExperimentDirectory $runtime.childEvidenceDirectory `
            'source-negative child evidence parent' -Force
        Assert-VerifierNoReparseAncestors $runtime.childEvidenceDirectory
        [void](Assert-VerifierPhysicalOwnedPath (Get-ExperimentCanonicalPath $evidenceRoot) `
            $runtime.childEvidenceDirectory -ValidateTree)
        $previewScript = $runtime.executionPreviewScript
        $previewStdout = Join-Path $Destination 'runtime-preview.stdout.log'
        $previewStderr = Join-Path $Destination 'runtime-preview.stderr.log'
        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $previewArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $previewScript,
            '-Port', [string]$runtime.previewPort)
        $previewProcess = Start-VerifierProcess $powershell $previewArguments `
            $previewStdout $previewStderr
        $previewProcessId = [int]$previewProcess.Id
        $previewProcessStartTicks = [long](Get-VerifierProcessStartTicks $previewProcess)
        $startupDeadline = [DateTime]::UtcNow.AddSeconds(20)
        $baseUrl = 'http://127.0.0.1:' + [string]$runtime.previewPort
        $ready = $false
        do {
            if ($previewProcess.HasExited) { break }
            try {
                $page = Invoke-WebRequest -UseBasicParsing -Uri ($baseUrl + '/circuitjs.html') -TimeoutSec 2
                $bootstrap = Invoke-WebRequest -UseBasicParsing `
                    -Uri ($baseUrl + '/circuitjs1/circuitjs1.nocache.js') -TimeoutSec 2
                if ($page.StatusCode -eq 200 -and $bootstrap.StatusCode -eq 200) {
                    $ready = $true
                    break
                }
            } catch { }
            Start-Sleep -Milliseconds 250
        } while ([DateTime]::UtcNow -lt $startupDeadline)
        if (-not $ready) {
            throw 'Disposable compiled preview did not become reachable before the bounded startup deadline.'
        }
        $identityResponse = Invoke-WebRequest -UseBasicParsing `
            -Uri ($baseUrl + '/__tsj/verify-identity') -TimeoutSec 5
        try {
            $identity = $identityResponse.Content | ConvertFrom-Json -ErrorAction Stop
        } catch {
            Throw-SourceExperimentInfrastructure 'Disposable preview identity was not valid JSON.' $_
        }
        $runtime.previewIdentity = Assert-DisposablePreviewIdentity $Destination $identity `
            $runtime.previewPort $previewProcessId $previewProcessStartTicks
        # HTTP identity cannot choose the owner. Pin it to the exact launch
        # tuple, then prove that same process still serves the selected script
        # and port before handing the preview to the browser child.
        [void](Get-VerifierCurrentProcessIdentity $previewProcessId `
            $previewProcessStartTicks 0 '' $previewScript $runtime.previewPort '' '')
        $runtime.compiledExecutionDigest = [string]$runtime.previewIdentity.executionDigest
        if ($runtime.compiledExecutionDigest -cne [string]$runtime.executionProvenance.digest) {
            Throw-SourceExperimentInfrastructure 'Disposable preview identity execution digest changed before the child route.'
        }
        $runtime.executionRootsValidated = $true
        $runtime.previewStarted = $true
        $wrapper = Join-Path $repositoryRoot 'scripts\verify-browser.ps1'
        $runtime.wrapperPath = Get-ExperimentCanonicalPath $wrapper
        $wrapperArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $wrapper,
            '-BaseUrl', $baseUrl, '-Task43PForcedNegative',
            '-Task43PSourceExperiment', [string]$Definition.id,
            '-Task43PStartupSettleMilliseconds', '30000', '-TimeoutSeconds', '90',
            '-EvidenceDirectory', $runtime.childEvidenceDirectory,
            '-ExecutionRepositoryRoot', $runtime.executionRepositoryRoot,
            '-ExecutionWebRoot', $runtime.executionWebRoot,
            '-ExecutionScriptRoot', $runtime.executionScriptRoot,
            '-ExpectedExecutionProvenanceDigest', $runtime.executionProvenance.digest)
        $runtime.route = ($wrapperArguments -join ' ')
        $childResult = Invoke-VerifierBoundedProcess $powershell $wrapperArguments 125000
        $runtime.childExit = [int]$childResult.ExitCode
        $runtime.underlyingExit = $runtime.childExit
        $runtime.childOutputTail = @([string]$childResult.Stdout -split "`r?`n" |
            Select-Object -Last 48)
        $runtime.childErrorTail = @([string]$childResult.Stderr -split "`r?`n" |
            Select-Object -Last 48)
        $runtime.outputTail = @($runtime.childOutputTail + $runtime.childErrorTail |
            Select-Object -Last 64)
        try {
            $artifact = Get-SourceNegativeProofArtifact $runtime.childEvidenceDirectory
            $runtime.childProofPath = [string]$artifact.Path
            $runtime.childRunId = [string]$artifact.RunId
            $runtime.childProof = $artifact.Value
            [void](Assert-SourceNegativeProof $artifact.Value $artifact.RunId `
                $artifact.Path $Definition $RepositoryBefore `
                $runtime.executionProvenance $ExpectedBeforeHash $ExpectedAfterHash `
                $runtime.compiledExecutionDigest $runtime.childExit 30000)
            $runtime.childProofAccepted = $true
            $runtime.status = 'CAUGHT'
            $runtime.exit = 1
            $runtime.reason = ''
        } catch {
            $runtime.status = 'UNPROVEN'
            $runtime.exit = 2
            $runtime.childProofReason = Get-ExperimentErrorMessage $_
            $runtime.reason = $runtime.childProofReason
        }
    } catch {
        $runtime.status = 'UNPROVEN'
        $runtime.exit = 2
        $runtime.reason = 'SOURCE_EXPERIMENT_INFRASTRUCTURE: ' + (Get-ExperimentErrorMessage $_)
        $runtime.childProofReason = $runtime.reason
    } finally {
        if ($null -ne $previewProcess) {
            try {
                $previewProcess.Refresh()
                $identityPid = $previewProcessId
                $identityStart = $previewProcessStartTicks
                if (-not [bool]$previewProcess.HasExited) {
                    if ($identityStart -le 0) {
                        throw 'Disposable preview had no positive start identity for exact cleanup.'
                    }
                    $currentIdentity = Get-VerifierCurrentProcessIdentity $identityPid `
                        $identityStart 0 '' $previewScript $runtime.previewPort '' ''
                    [void](Stop-VerifierVerifiedProcessExactly $previewProcess $identityStart `
                        5000 $currentIdentity.Record)
                } else {
                    $absent = Get-VerifierCurrentProcessRecordById ([int]$previewProcess.Id)
                    if ($null -ne $absent) {
                        throw 'Disposable preview reported exited but retained a current process identity.'
                    }
                }
                [void](Complete-VerifierProcessOutputCapture $previewProcess 5000)
                $previewProcess.Refresh()
                if (-not [bool]$previewProcess.HasExited) {
                    throw 'Disposable preview process remained alive after exact cleanup.'
                }
                $currentAfterCleanup = Get-VerifierCurrentProcessRecordById $identityPid
                if ($null -ne $currentAfterCleanup) {
                    throw 'Disposable preview retained its original current process identity after exact cleanup.'
                }
                $runtime.previewProcessAbsentProven = $true
                # Listener absence is an independent OS proof. Keep the
                # query within its own fixed cleanup deadline and retain the
                # typed inspection for the source-experiment evidence.
                $listenerStopwatch = [Diagnostics.Stopwatch]::StartNew()
                $listenerInspection = Get-VerifierLoopbackListenerRecords `
                    ([int]$runtime.previewPort) -PreferNetstat
                $listenerBudgetTicks = [Diagnostics.Stopwatch]::Frequency * 5L
                if ($listenerStopwatch.ElapsedTicks -ge $listenerBudgetTicks) {
                    throw 'Disposable preview listener absence proof exceeded its bounded cleanup deadline.'
                }
                if ($null -eq $listenerInspection -or
                        $null -eq $listenerInspection.PSObject.Properties['Success'] -or
                        $null -eq $listenerInspection.PSObject.Properties['Known'] -or
                        $null -eq $listenerInspection.PSObject.Properties['HasListeners'] -or
                        $null -eq $listenerInspection.PSObject.Properties['Listeners'] -or
                        $listenerInspection.Success.GetType() -ne [bool] -or
                        $listenerInspection.Known.GetType() -ne [bool] -or
                        $listenerInspection.HasListeners.GetType() -ne [bool] -or
                        -not $listenerInspection.Success -or
                        -not $listenerInspection.Known -or
                        $listenerInspection.HasListeners -or
                        $null -eq $listenerInspection.Listeners -or
                        @($listenerInspection.Listeners).Count -ne 0) {
                    throw 'Disposable preview listener absence was not positively proven after exact cleanup.'
                }
                $runtime.previewListenerInspection = [ordered]@{
                    success = [bool]$listenerInspection.Success
                    known = [bool]$listenerInspection.Known
                    hasListeners = [bool]$listenerInspection.HasListeners
                    source = [string]$listenerInspection.Source
                    listenerCount = @($listenerInspection.Listeners).Count
                }
                if ($listenerStopwatch.ElapsedTicks -ge $listenerBudgetTicks) {
                    throw 'Disposable preview listener schema exceeded its bounded cleanup deadline.'
                }
                $runtime.previewListenerAbsenceProven = $true
                $runtime.previewStopped = $true
            } catch {
                $runtime.previewStopped = $false
                $runtime.previewProcessAbsentProven = $false
                $runtime.previewListenerAbsenceProven = $false
                $runtime.status = 'UNPROVEN'
                $runtime.exit = 2
                $runtime.reason = ($runtime.reason + ' Disposable preview cleanup failed: ' +
                    (Get-ExperimentErrorMessage $_)).Trim()
            }
        }
        if ($null -ne $previewProcess) {
            $previewStdoutPath = Join-Path $Destination 'runtime-preview.stdout.log'
            $previewStderrPath = Join-Path $Destination 'runtime-preview.stderr.log'
            try {
                if (Test-Path -LiteralPath $previewStdoutPath -PathType Leaf) {
                    $runtime.previewOutputTail = @(Get-Content -LiteralPath $previewStdoutPath -ErrorAction Stop |
                        Select-Object -Last 12)
                }
                if (Test-Path -LiteralPath $previewStderrPath -PathType Leaf) {
                    $runtime.previewErrorTail = @(Get-Content -LiteralPath $previewStderrPath -ErrorAction Stop |
                        Select-Object -Last 12)
                }
            } catch {
                $runtime.status = 'UNPROVEN'
                $runtime.exit = 2
                $runtime.reason = ($runtime.reason + ' Disposable preview diagnostics could not be read: ' +
                    (Get-ExperimentErrorMessage $_)).Trim()
            }
        }
        if (-not $runtime.previewStopped) {
            $runtime.status = 'UNPROVEN'
            $runtime.exit = 2
        }
    }
    return $runtime
}

function Invoke-DisposablePreviewIdentityCanary() {
    $provenance = Get-ExperimentExecutionProvenance $repositoryRoot
    $valid = [pscustomobject]@{
        protocol = 'troubleshootjs-preview-identity-v1'
        repositoryRoot = $provenance.repositoryRoot
        previewScript = Get-ExperimentCanonicalPath (Join-Path $repositoryRoot 'scripts\preview.ps1')
        webRoot = $provenance.webRoot
        previewPort = 40123
        processId = 1
        processStartTicks = 1L
        verifierRunId = ''
        verifierNonce = ''
        sourceRoot = $provenance.sourceRoot
        scriptRoot = $provenance.scriptRoot
        sourceDigest = $provenance.sourceDigest
        scriptDigest = $provenance.scriptDigest
        webDigest = $provenance.webDigest
        executionDigest = $provenance.digest
        executionFileCount = $provenance.fileCount
    }
    [void](Assert-DisposablePreviewIdentity $repositoryRoot $valid 40123 1 1L)
    $cases = @(
        [pscustomobject]@{ Name = 'unknown-field'; Mutate = { param($value) $value | Add-Member -MemberType NoteProperty -Name Unknown -Value 1 } }
        [pscustomobject]@{ Name = 'wrong-case-protocol'; Mutate = { param($value) $value.protocol = 'TroubleshootJS-preview-identity-v1' } }
        [pscustomobject]@{ Name = 'numeric-string-port'; Mutate = { param($value) $value.previewPort = '40123' } }
        [pscustomobject]@{ Name = 'negative-pid'; Mutate = { param($value) $value.processId = -1 } }
        [pscustomobject]@{ Name = 'different-positive-pid'; Mutate = { param($value) $value.processId = 2 } }
        [pscustomobject]@{ Name = 'different-positive-start'; Mutate = { param($value) $value.processStartTicks = 2L } }
        [pscustomobject]@{ Name = 'zero-start'; Mutate = { param($value) $value.processStartTicks = 0 } }
        [pscustomobject]@{ Name = 'foreign-source-root'; Mutate = { param($value) $value.sourceRoot = $value.repositoryRoot } }
        [pscustomobject]@{ Name = 'half-run-identity'; Mutate = { param($value) $value.verifierRunId = 'foreign-run' } }
    )
    foreach ($case in $cases) {
        $candidate = $valid | ConvertTo-Json -Depth 8 | ConvertFrom-Json
        & $case.Mutate $candidate
        $rejected = $false
        try { [void](Assert-DisposablePreviewIdentity $repositoryRoot $candidate 40123 1 1L) } catch {
            $rejected = $true
        }
        if (-not $rejected) {
            throw "Malformed disposable identity canary '$($case.Name)' was accepted."
        }
    }
    foreach ($launchTuple in @(
            [pscustomobject]@{ Id = $null; Start = 1L },
            [pscustomobject]@{ Id = 1; Start = $null },
            [pscustomobject]@{ Id = '1'; Start = 1L },
            [pscustomobject]@{ Id = 1; Start = '1' },
            [pscustomobject]@{ Id = $true; Start = 1L },
            [pscustomobject]@{ Id = 1; Start = $true },
            [pscustomobject]@{ Id = 2; Start = 1L },
            [pscustomobject]@{ Id = 1; Start = 2L })) {
        $rejected = $false
        try {
            [void](Assert-DisposablePreviewIdentity $repositoryRoot $valid 40123 `
                $launchTuple.Id $launchTuple.Start)
        } catch { $rejected = $true }
        if (-not $rejected) { throw 'Missing, malformed, or substituted launch identity was accepted.' }
    }
    Write-Host 'PASS:disposable preview requires exact raw launch PID/start and rejects malformed or substituted identity before browser launch'
}

function Invoke-SourceExperiment($Definition, [string]$SelectedJavaHome,
        $RepositoryBefore) {
    $experimentRoot = Join-Path $runRoot $Definition.id
    $sourcePath = Join-Path $experimentRoot $Definition.relativePath
    $sourceDirectory = Split-Path -Parent $sourcePath
    $restored = $false
    $beforeBytes = $null
    $afterBytes = $null
    $compile = [ordered]@{ exit = 2; outputTail = @(); attempted = $false }
    $runtime = $null
    $runtimeAgainstMutatedSource = $false
    $errorText = ''
    try {
        Write-Host ("SOURCE_STAGE " + $Definition.id + " copy-start")
        Copy-DisposableBuildTree $experimentRoot
        New-SourceExperimentDirectory $sourceDirectory 'disposable source mutation parent' -Force
        $repositorySourcePath = Join-Path $repositoryRoot $Definition.relativePath
        Copy-Item -LiteralPath $repositorySourcePath -Destination $sourcePath -Force -ErrorAction Stop | Out-Null
        $beforeBytes = [IO.File]::ReadAllBytes($sourcePath)
        $beforeHash = Get-BytesHash $beforeBytes
        [void](Invoke-ExactTextReplacement $sourcePath $Definition.needle $Definition.replacement)
        $afterBytes = [IO.File]::ReadAllBytes($sourcePath)
        $afterHash = Get-BytesHash $afterBytes
        if ($beforeHash -eq $afterHash) {
            throw "Disposable source mutation did not change bytes for $($Definition.id)."
        }
        Write-Host ("SOURCE_STAGE " + $Definition.id + " compile-start")
        $compile.attempted = $true
        $compileResult = Invoke-DisposableCompile $experimentRoot $SelectedJavaHome
        $compile.exit = $compileResult.exit
        $compile.outputTail = $compileResult.outputTail
        Write-Host ("SOURCE_STAGE " + $Definition.id + " compile-done exit=" + $compile.exit)

        # The compiled preview must be exercised while this disposable tree
        # still contains the producer-path mutation.  Restoring the source
        # before this call would leave the runtime proof ambiguously paired
        # with an unmutated source tree, even though compilation succeeded.
        Write-Host ("SOURCE_STAGE " + $Definition.id + " runtime-start")
        $runtimeBytes = [IO.File]::ReadAllBytes($sourcePath)
        $runtimeAgainstMutatedSource = (Test-ByteArraysEqual $afterBytes $runtimeBytes) -and
            -not (Test-ByteArraysEqual $beforeBytes $runtimeBytes)
        if (-not $runtimeAgainstMutatedSource) {
            throw "Disposable runtime source snapshot was not the mutated byte image for $($Definition.id)."
        }
        $runtime = Invoke-DisposableRuntimeExtraction $experimentRoot $Definition `
            $SelectedJavaHome $compile $RepositoryBefore $beforeHash $afterHash
        Write-Host ("SOURCE_STAGE " + $Definition.id + " runtime-done attempted=" + $runtime.attempted +
            " exit=" + $runtime.exit)
    } catch {
        $errorText = Get-ExperimentErrorMessage $_
    } finally {
        try {
            if ($null -ne $beforeBytes -and (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
                Write-SourceExperimentBytes $sourcePath $beforeBytes 'source restoration'
                $restoredBytes = [IO.File]::ReadAllBytes($sourcePath)
                $restored = Test-ByteArraysEqual $beforeBytes $restoredBytes
                if (-not $restored) {
                    throw "Disposable source restore bytes differed for $($Definition.id)."
                }
            }
        } catch {
            $script:cleanupErrors += ('SOURCE_EXPERIMENT_INFRASTRUCTURE: source restoration failed: ' +
                (Get-ExperimentErrorMessage $_))
        }
    }
    if ($null -eq $runtime) {
        # Copy/mutation/compile infrastructure can fail before the paired
        # runtime call.  Ask the runtime helper for its typed non-attempted
        # record; do not invoke a compiled preview after restoration, because
        # that would exercise an unmutated source image.
        $runtime = Invoke-DisposableRuntimeExtraction $experimentRoot $Definition `
            $SelectedJavaHome ([ordered]@{ attempted = $false; exit = 2; outputTail = @() }) `
            $RepositoryBefore '' ''
    }
    Write-Host ("SOURCE_STAGE " + $Definition.id + " restore-done exact=" + $restored)
    $repositoryAfter = Get-RepositoryState
    $repositoryUnchanged = Test-RepositoryStateEqual $RepositoryBefore $repositoryAfter
    if (-not $repositoryUnchanged) {
        $script:cleanupErrors += "Repository state changed during $($Definition.id)."
    }
    $targetAfterRestoreHash = if ($null -ne $beforeBytes -and
            (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        Get-BytesHash ([IO.File]::ReadAllBytes($sourcePath))
    } else { '' }
    $status = 'UNPROVEN'
    if ([String]::IsNullOrWhiteSpace($errorText) -and -not $restored) {
        $errorText = 'Disposable source mutation was not restored exactly.'
    }
    if ([String]::IsNullOrWhiteSpace($errorText) -and -not $repositoryUnchanged) {
        $errorText = 'Repository state was not unchanged after disposable experiment.'
    }
    if ([String]::IsNullOrWhiteSpace($errorText) -and $runtime.attempted -and
            -not $runtime.previewStopped) {
        $errorText = 'Disposable preview cleanup was not proven.'
    }
    $caught = Test-SourceExperimentCaught $errorText $compile $runtime `
        $runtimeAgainstMutatedSource $restored $repositoryUnchanged
    if ($caught) {
        $status = 'CAUGHT'
    } elseif ([String]::IsNullOrWhiteSpace($errorText)) {
        $errorText = if (-not [String]::IsNullOrWhiteSpace([string]$runtime.reason)) {
            [string]$runtime.reason
        } elseif (-not [String]::IsNullOrWhiteSpace([string]$runtime.childProofReason)) {
            [string]$runtime.childProofReason
        } else { 'Child source-negative proof was missing or incomplete.' }
    }
    return [ordered]@{
        id = $Definition.id
        relativePath = $Definition.relativePath
        family = $Definition.family
        topology = $Definition.topology
        seed = [int]$Definition.seed
        expectedMarker = $Definition.expectedMarker
        mutationKind = $Definition.mutationKind
        sourceMutationBeforeSha256 = if ($null -eq $beforeBytes) { '' } else { Get-BytesHash $beforeBytes }
        sourceMutationAfterSha256 = if ($null -eq $afterBytes) { '' } else { Get-BytesHash $afterBytes }
        sourceMutationRestoredSha256 = $targetAfterRestoreHash
        sourceMutationBeforeByteLength = if ($null -eq $beforeBytes) { 0 } else { $beforeBytes.Length }
        sourceMutationAfterByteLength = if ($null -eq $afterBytes) { 0 } else { $afterBytes.Length }
        sourceBytesRestoredExactly = $restored
        disposableCompileAttempted = $compile.attempted
        disposableCompileExit = $compile.exit
        disposableCompileOutputTail = $compile.outputTail
        runtimeExtractionAttempted = $runtime.attempted
        runtimeAgainstMutatedSource = $runtimeAgainstMutatedSource
        runtimeStatus = $runtime.status
        runtimeExit = $runtime.exit
        runtimeUnderlyingExit = $runtime.underlyingExit
        runtimeRoute = $runtime.route
        runtimeExecutionRepositoryRoot = [string]$runtime.executionRepositoryRoot
        runtimeExecutionWebRoot = [string]$runtime.executionWebRoot
        runtimeExecutionScriptRoot = [string]$runtime.executionScriptRoot
        runtimeExecutionPreviewScript = [string]$runtime.executionPreviewScript
        runtimeExecutionProvenance = $runtime.executionProvenance
        compiledExecutionDigest = [string]$runtime.compiledExecutionDigest
        runtimeExecutionRootsValidated = [bool]$runtime.executionRootsValidated
        runtimePreviewIdentity = $runtime.previewIdentity
        runtimeWrapperPath = [string]$runtime.wrapperPath
        runtimePreviewPort = $runtime.previewPort
        runtimePreviewStarted = $runtime.previewStarted
        runtimePreviewProcessAbsentProven = $runtime.previewProcessAbsentProven
        runtimePreviewListenerAbsenceProven = $runtime.previewListenerAbsenceProven
        runtimePreviewListenerInspection = $runtime.previewListenerInspection
        runtimePreviewStopped = $runtime.previewStopped
        childEvidenceDirectory = [string]$runtime.childEvidenceDirectory
        childExit = [int]$runtime.childExit
        childProofPath = [string]$runtime.childProofPath
        childRunId = [string]$runtime.childRunId
        childProof = $runtime.childProof
        childProofAccepted = [bool]$runtime.childProofAccepted
        childProofReason = [string]$runtime.childProofReason
        childOutputTail = @($runtime.childOutputTail | ForEach-Object { [string]$_ })
        childErrorTail = @($runtime.childErrorTail | ForEach-Object { [string]$_ })
        runtimePreviewOutputTail = $runtime.previewOutputTail
        runtimePreviewErrorTail = $runtime.previewErrorTail
        runtimeOutputTail = $runtime.outputTail
        runtimeReason = $runtime.reason
        status = $status
        exit = if ($caught) { 1 } else { 2 }
        repositoryUnchanged = $repositoryUnchanged
        error = $errorText
    }
}

function ConvertTo-SourceRepositoryEvidence($State) {
    if ($null -eq $State) { return $null }
    return [ordered]@{
        headSha = [string]$State.headSha
        sourceVerifierDigest = [string]$State.sourceVerifierDigest
        sourceVerifierFileCount = [int]$State.sourceVerifierFileCount
        dirty = [bool]$State.dirty
        statusText = [string]$State.statusText
    }
}

function ConvertTo-SourceExperimentEvidence($Experiment) {
    return [ordered]@{
        id = [string]$Experiment.id
        relativePath = ([string]$Experiment.relativePath).Replace('\', '/')
        family = [string]$Experiment.family
        topology = [string]$Experiment.topology
        seed = [int]$Experiment.seed
        expectedMarker = [string]$Experiment.expectedMarker
        mutationKind = [string]$Experiment.mutationKind
        sourceMutationBeforeSha256 = [string]$Experiment.sourceMutationBeforeSha256
        sourceMutationAfterSha256 = [string]$Experiment.sourceMutationAfterSha256
        sourceMutationRestoredSha256 = [string]$Experiment.sourceMutationRestoredSha256
        sourceMutationBeforeByteLength = [int]$Experiment.sourceMutationBeforeByteLength
        sourceMutationAfterByteLength = [int]$Experiment.sourceMutationAfterByteLength
        sourceBytesRestoredExactly = [bool]$Experiment.sourceBytesRestoredExactly
        disposableCompileAttempted = [bool]$Experiment.disposableCompileAttempted
        disposableCompileExit = [int]$Experiment.disposableCompileExit
        disposableCompileOutputTail = @($Experiment.disposableCompileOutputTail |
            ForEach-Object { [string]$_ })
        runtimeExtractionAttempted = [bool]$Experiment.runtimeExtractionAttempted
        runtimeAgainstMutatedSource = [bool]$Experiment.runtimeAgainstMutatedSource
        runtimeStatus = [string]$Experiment.runtimeStatus
        runtimeExit = [int]$Experiment.runtimeExit
        runtimeUnderlyingExit = [int]$Experiment.runtimeUnderlyingExit
        runtimeRoute = [string]$Experiment.runtimeRoute
        runtimeExecutionRepositoryRoot = [string]$Experiment.runtimeExecutionRepositoryRoot
        runtimeExecutionWebRoot = [string]$Experiment.runtimeExecutionWebRoot
        runtimeExecutionScriptRoot = [string]$Experiment.runtimeExecutionScriptRoot
        runtimeExecutionPreviewScript = [string]$Experiment.runtimeExecutionPreviewScript
        runtimeExecutionProvenance = $Experiment.runtimeExecutionProvenance
        compiledExecutionDigest = [string]$Experiment.compiledExecutionDigest
        runtimeExecutionRootsValidated = [bool]$Experiment.runtimeExecutionRootsValidated
        runtimePreviewIdentity = $Experiment.runtimePreviewIdentity
        runtimeWrapperPath = [string]$Experiment.runtimeWrapperPath
        runtimePreviewPort = [int]$Experiment.runtimePreviewPort
        runtimePreviewStarted = [bool]$Experiment.runtimePreviewStarted
        runtimePreviewProcessAbsentProven = [bool]$Experiment.runtimePreviewProcessAbsentProven
        runtimePreviewListenerAbsenceProven = [bool]$Experiment.runtimePreviewListenerAbsenceProven
        runtimePreviewListenerInspection = $Experiment.runtimePreviewListenerInspection
        runtimePreviewStopped = [bool]$Experiment.runtimePreviewStopped
        childEvidenceDirectory = [string]$Experiment.childEvidenceDirectory
        childExit = [int]$Experiment.childExit
        childProofPath = [string]$Experiment.childProofPath
        childRunId = [string]$Experiment.childRunId
        childProof = $Experiment.childProof
        childProofAccepted = [bool]$Experiment.childProofAccepted
        childProofReason = [string]$Experiment.childProofReason
        childOutputTail = @($Experiment.childOutputTail |
            ForEach-Object { [string]$_ })
        childErrorTail = @($Experiment.childErrorTail |
            ForEach-Object { [string]$_ })
        runtimePreviewOutputTail = @($Experiment.runtimePreviewOutputTail |
            ForEach-Object { [string]$_ })
        runtimePreviewErrorTail = @($Experiment.runtimePreviewErrorTail |
            ForEach-Object { [string]$_ })
        runtimeOutputTail = @($Experiment.runtimeOutputTail |
            ForEach-Object { [string]$_ })
        runtimeReason = [string]$Experiment.runtimeReason
        status = [string]$Experiment.status
        exit = [int]$Experiment.exit
        repositoryUnchanged = [bool]$Experiment.repositoryUnchanged
        error = [string]$Experiment.error
    }
}

if ($ContractProbe) {
    try {
        Invoke-SourceNegativeProofContractProbe
        exit 0
    } catch {
        [Console]::Error.WriteLine('FAIL:source-negative proof contract probe - ' +
            (Get-ExperimentErrorMessage $_))
        exit 2
    }
}

if ($IdentityCanary) {
    try {
        Invoke-DisposablePreviewIdentityCanary
        exit 0
    } catch {
        [Console]::Error.WriteLine('FAIL:malformed disposable preview identity canary - ' +
            (Get-ExperimentErrorMessage $_))
        exit 2
    }
}

$definitions = @(
    [ordered]@{
        id = 'renderer-only-j1-1-plus-20px'
        relativePath = 'src\com\lushprojects\circuitjs1\client\PcbWorkbenchRenderer.java'
        family = 'LED_INDICATOR'
        topology = 'DIRECT_SERIES'
        seed = 0
        expectedMarker = 'FAIL:task43p-renderer-pad-projection-mismatch:J1.1'
        mutationKind = 'renderer-source-pad-projection'
        needle = '        return pad == null ? null : new Point(screenX(pad.getX()), screenY(pad.getY()));'
        replacement = @"
        if (pad != null && "J1.1".equals(padId))
            return new Point(screenX(pad.getX()) + 20, screenY(pad.getY()));
        return pad == null ? null : new Point(screenX(pad.getX()), screenY(pad.getY()));
"@
    }
    [ordered]@{
        id = 'renderer-only-j1-1-lead-plus-20px'
        relativePath = 'src\com\lushprojects\circuitjs1\client\PhysicalPartRenderTerminal.java'
        family = 'LED_INDICATOR'
        topology = 'DIRECT_SERIES'
        seed = 0
        expectedMarker = 'FAIL:task43p-renderer-surface-mismatch:J1.1'
        mutationKind = 'renderer-source-lead-projection'
        needle = '    Point getLeadEndPoint() { return new Point(leadEndPoint.x, leadEndPoint.y); }'
        replacement = @"
    Point getLeadEndPoint() {
        if ("J1.1".equals(boardPadId))
            return new Point(leadEndPoint.x + 20, leadEndPoint.y);
        return new Point(leadEndPoint.x, leadEndPoint.y);
    }
"@
    }
    [ordered]@{
        id = 'raw-copper-j1-1-endpoint-gap'
        relativePath = 'src\com\lushprojects\circuitjs1\client\GeneratedBoardInstance.java'
        family = 'LED_INDICATOR'
        topology = 'DIRECT_SERIES'
        seed = 0
        expectedMarker = 'FAIL:task43p-raw-copper-start-gap:J1.1'
        mutationKind = 'raw-copper-producer-layout-endpoint'
        needle = '        this.pcbLayout = pcbLayout;'
        replacement = @"
        this.pcbLayout = pcbLayout;
        if (this.pcbLayout != null) {
            for (PcbTraceGeometry trace : this.pcbLayout.getTraces()) {
                int[] xPoints = trace.getXPoints();
                if ("J1.1".equals(trace.getStartPadId()))
                    xPoints[0] += 20;
                else if ("J1.1".equals(trace.getEndPadId()))
                    xPoints[xPoints.length - 1] += 20;
            }
        }
"@
    }
    [ordered]@{
        id = 'raw-net-mismatch'
        relativePath = 'src\com\lushprojects\circuitjs1\client\GeneratedBoardInstance.java'
        family = 'LED_INDICATOR'
        topology = 'DIRECT_SERIES'
        seed = 0
        expectedMarker = 'FAIL:task43p-set-mismatch:manifest/raw board nets:expected=[GND, LED_NODE, VIN]:actual=[GND, LED_NODE, TASK43P_UNMANIFESTED_EMPTY, VIN]'
        mutationKind = 'raw-logical-board-producer-net'
        needle = '        this.board = board;'
        replacement = @"
        this.board = board;
        if (this.board.getNet("TASK43P_UNMANIFESTED_EMPTY") == null)
            this.board.addNet(new BoardNet("TASK43P_UNMANIFESTED_EMPTY"));
"@
    }
    [ordered]@{
        id = 'solver-binding-j1-1-post-mismatch'
        relativePath = 'src\com\lushprojects\circuitjs1\client\BoardSimulationBindings.java'
        family = 'LED_INDICATOR'
        topology = 'DIRECT_SERIES'
        seed = 0
        expectedMarker = 'FAIL:task43p-solver-binding-endpoint-identity-mismatch:J1.1'
        mutationKind = 'solver-producer-binding-endpoint-redirect-after-fixed-capture'
        needle = '        return padEndpoints.get(padId);'
        replacement = @"
        CircuitMeasurementEndpoint originalEndpoint = padEndpoints.get(padId);
        if (developerVerificationReady && "J1.1".equals(padId)) {
            /* Preserve the first producer read for the fixed terminal owner. */
            String firstReadMarker = "__TASK43P_SOURCE_EXPERIMENT_ORIGINAL__" + padId;
            if (!padEndpoints.containsKey(firstReadMarker)) {
                padEndpoints.put(firstReadMarker, originalEndpoint);
                return originalEndpoint;
            }
            String[] mismatchCandidates = {"R1.1", "RLOAD.1", "J1.2"};
            for (String candidatePadId : mismatchCandidates)
                if (padEndpoints.containsKey(candidatePadId) &&
                        padEndpoints.get(candidatePadId) != originalEndpoint)
                    return padEndpoints.get(candidatePadId);
        }
        return originalEndpoint;
"@
    }
    [ordered]@{
        id = 'solver-detachable-c1-plus-identity-mismatch'
        relativePath = 'src\com\lushprojects\circuitjs1\client\BoardSimulationBindings.java'
        family = 'RC_DELAY'
        topology = 'RC_CHARGE_DELAY'
        seed = 0
        expectedMarker = 'FAIL:task43p-solver-binding-endpoint-identity-mismatch:C1.+'
        mutationKind = 'solver-producer-binding-endpoint-redirect-after-detachable-capture'
        needle = '        return padEndpoints.get(padId);'
        replacement = @"
        CircuitMeasurementEndpoint originalEndpoint = padEndpoints.get(padId);
        if (developerVerificationReady && "C1.+".equals(padId)) {
            /* Preserve the first producer read for the detachable binding owner. */
            String firstReadMarker = "__TASK43P_SOURCE_EXPERIMENT_DETACHABLE_C1_PLUS_ORIGINAL__" + padId;
            if (!padEndpoints.containsKey(firstReadMarker)) {
                padEndpoints.put(firstReadMarker, originalEndpoint);
                return originalEndpoint;
            }
            /* R1.2 uses a different solver element from C1.+. J2.1 and
             * R2.1 are aliases of C1.+'s exact rcOut/post-1 endpoint, so
             * choosing either alias first would not inject an identity fault. */
            String[] mismatchCandidates = {"R1.2", "J2.1", "R2.1"};
            for (String candidatePadId : mismatchCandidates)
                if (padEndpoints.containsKey(candidatePadId) &&
                        padEndpoints.get(candidatePadId) != originalEndpoint)
                    return padEndpoints.get(candidatePadId);
        }
        return originalEndpoint;
"@
    }
    [ordered]@{
        id = 'package-mirror-mismatch'
        relativePath = 'src\com\lushprojects\circuitjs1\client\PcbComponentPlacement.java'
        family = 'LED_INDICATOR'
        topology = 'DIRECT_SERIES'
        seed = 0
        expectedMarker = 'FAIL:task43p-physical-package-variant-mismatch:J1'
        mutationKind = 'package-source-transform-acceptance-catalog-mismatch'
        needle = '    String getGeometryTransformKey() { return geometryTransformKey; }'
        replacement = @"
    String getGeometryTransformKey() {
        if ("J1".equals(componentId))
            return "TASK43P_WRONG_MIRROR_X";
        return geometryTransformKey;
    }
"@
    }
    [ordered]@{
        id = 'internally-self-consistent-wrong-mapping'
        relativePath = 'src\com\lushprojects\circuitjs1\client\LedIndicatorGenerator.java'
        family = 'LED_INDICATOR'
        topology = 'DIRECT_SERIES'
        seed = 0
        expectedMarker = 'FAIL:task43p-solver-oracle-net-mismatch:R1.1'
        mutationKind = 'logical-board-producer-pad-net-swap'
        needle = ('        board.addPad(new BoardPad("R1.1", "R1", "1", "VIN"));' +
            [Environment]::NewLine +
            '        board.addPad(new BoardPad("R1.2", "R1", "2", "LED_NODE"));')
        replacement = ('        board.addPad(new BoardPad("R1.1", "R1", "1", "LED_NODE"));' +
            [Environment]::NewLine +
            '        board.addPad(new BoardPad("R1.2", "R1", "2", "VIN"));')
    }
    [ordered]@{
        id = 'omitted-manifest-terminal'
        relativePath = 'src\com\lushprojects\circuitjs1\client\Task43PPhysicalTruthDeveloperVerifier.java'
        family = 'LED_INDICATOR'
        topology = 'DIRECT_SERIES'
        seed = 0
        expectedMarker = 'FAIL:task43p-set-mismatch:manifest/raw board pads:expected=[J1.1, J1.2, LED1.A, R1.1, R1.2]:actual=[J1.1, J1.2, LED1.A, LED1.K, R1.1, R1.2]'
        mutationKind = 'manifest-source-terminal-omission'
        # The repository source uses LF here while this script is retained
        # with mixed line endings.  Anchor the exact terminal line so the
        # omission semantics remain identical without normalizing the file.
        needle = '            addTerminal(result, "LED1.K", "LED1", "K", "GND", "GroundElm", 0);'
        replacement = ''
    }
    [ordered]@{
        id = 'snapshot-restore-resistance-current-omitted'
        relativePath = 'src\com\lushprojects\circuitjs1\client\Task41SimulationSnapshot.java'
        family = 'LED_INDICATOR'
        topology = 'DIRECT_SERIES'
        seed = 0
        expectedMarker = 'FAIL:Task 41 restore changed lastResistanceTestCurrent'
        mutationKind = 'snapshot-source-restoration-field-omission'
        needle = '        sim.lastResistanceTestCurrent = lastResistanceTestCurrent;'
        replacement = '        // Task43P disposable falsifier: omit resistance-current restoration.'
    }
    [ordered]@{
        id = 'public-remove-action-disabled'
        relativePath = 'src\com\lushprojects\circuitjs1\client\PcbWorkbenchController.java'
        family = 'LED_INDICATOR'
        topology = 'DIRECT_SERIES'
        seed = 3
        expectedMarker = 'FAIL:task43p-public-remove-direct-control-passed'
        mutationKind = 'public-remove-button-disabled'
        # Anchor one unchanged line so mixed LF/CRLF source cannot hide the
        # real disabled-button mutation behind a pre-compilation mismatch.
        needle = '        addAction(operationLabel(part, operation, "Remove component"),'
        replacement = '        addAction(operationLabel(part, operation, "Remove component"), true ||'
    }
)

$allDefinitions = @($definitions)
$selectionError = ''
if (-not [String]::IsNullOrWhiteSpace($ExperimentId)) {
    $definitions = @($definitions | Where-Object { $_.id -eq $ExperimentId })
    if ($definitions.Count -ne 1) {
        $selectionError = "Unknown disposable source experiment id '$ExperimentId'."
    }
}

$repositoryBefore = $null
$experiments = @()
$finalRepositoryState = $null
$overallExit = 2
$runRootRetained = $true
$runRootRemoved = $false
$evidenceWritten = $false
$mutationPreflightResult = $null
$mutationPreflightPassed = $false
try {
    $repositoryBefore = Get-RepositoryState
    if (-not [String]::IsNullOrWhiteSpace($selectionError)) {
        throw $selectionError
    }
    if ($repositoryBefore.headSha -ne $publishedBaselineSha) {
        throw "Source experiment harness requires published repair baseline $publishedBaselineSha; found $($repositoryBefore.headSha)."
    }
    $selectedJavaHome = $JavaHome
    if ([String]::IsNullOrWhiteSpace($selectedJavaHome)) {
        $defaultJavaHome = Join-Path $repositoryRoot '.tools\jdk8-download\jdk8u502-b07'
        if (Test-Path -LiteralPath $defaultJavaHome -PathType Container) {
            $selectedJavaHome = $defaultJavaHome
        }
    }
    New-SourceExperimentDirectory $runRoot 'disposable experiment run root' -Force
    New-SourceExperimentDirectory $evidenceRoot 'source-experiment evidence' -Force
    $canonicalEvidenceRoot = Get-ExperimentCanonicalPath $evidenceRoot
    Assert-VerifierNoReparseAncestors $canonicalEvidenceRoot
    if ($canonicalEvidenceRoot.Equals(
            (Get-ExperimentCanonicalPath $runRoot), [StringComparison]::OrdinalIgnoreCase) -or
            (Test-VerifierPhysicalChildPath $runRoot $canonicalEvidenceRoot)) {
        Throw-SourceExperimentInfrastructure 'Source-negative child evidence must remain outside the disposable build namespace.'
    }
    $mutationPreflightResult = Invoke-MutationPreflight $allDefinitions `
        (Join-Path $runRoot 'mutation-preflight')
    $mutationPreflightPassed = [bool]$mutationPreflightResult.passed
    if (-not $mutationPreflightPassed) {
        throw 'Mutation preflight failed closed; no source build or compiled experiment was started.'
    }
    if (-not $MutationPreflight) {
        foreach ($definition in $definitions) {
            Write-Host ("SOURCE_STAGE " + $definition.id + " experiment-start")
            $experiment = Invoke-SourceExperiment $definition $selectedJavaHome $repositoryBefore
            $experiments += $experiment
            Write-Host ("SOURCE_STAGE " + $definition.id + " experiment-done")
            if ($experiment.exit -ne 1 -or
                    $experiment.runtimeExit -ne 1 -or
                    $experiment.runtimeUnderlyingExit -ne 1 -or
                    $experiment.childExit -ne 1 -or
                    $experiment.runtimeStatus -cne 'CAUGHT' -or
                    -not [String]::IsNullOrWhiteSpace([string]$experiment.runtimeReason) -or
                    -not $experiment.childProofAccepted -or
                    -not $experiment.runtimePreviewProcessAbsentProven -or
                    -not $experiment.runtimePreviewListenerAbsenceProven -or
                    -not $experiment.runtimePreviewStopped) {
                $script:cleanupErrors += "Stopped before next source case after an unproven child/proof/cleanup result for $($definition.id)."
                break
            }
        }
        Write-Host 'SOURCE_STAGE all-experiments-done'
    } else {
        $overallExit = 1
        Write-Host 'SOURCE_STAGE mutation-preflight-only-done'
    }
    $finalRepositoryState = Get-RepositoryState
    $allRestored = $true
    foreach ($experiment in $experiments) {
        if (-not $experiment.sourceBytesRestoredExactly -or
                -not $experiment.repositoryUnchanged -or $experiment.exit -ne 1 -or
                -not $experiment.childProofAccepted -or
                -not $experiment.runtimePreviewProcessAbsentProven -or
                -not $experiment.runtimePreviewListenerAbsenceProven -or
                -not $experiment.runtimePreviewStopped) {
            $allRestored = $false
        }
    }
    if (-not (Test-RepositoryStateEqual $repositoryBefore $finalRepositoryState)) {
        $allRestored = $false
        $script:cleanupErrors += 'Final repository state differed from the pre-experiment state.'
    }
    if ($allRestored -and $experiments.Count -eq $definitions.Count -and
            $script:cleanupErrors.Count -eq 0) {
        $overallExit = 1
    } else {
        $overallExit = 2
    }
} catch {
        $script:cleanupErrors += ('SOURCE_EXPERIMENT_INFRASTRUCTURE: experiment orchestration failed: ' +
            (Get-ExperimentErrorMessage $_))
    $overallExit = 2
} finally {
    try {
        if ($null -eq $finalRepositoryState) {
            $finalRepositoryState = Get-RepositoryState
        }
    } catch {
        $script:cleanupErrors += ('Final repository state could not be proven: ' +
            (Get-ExperimentErrorMessage $_))
    }
    $finalRepositoryEqual = $null -ne $repositoryBefore -and
        $null -ne $finalRepositoryState -and
        (Test-RepositoryStateEqual $repositoryBefore $finalRepositoryState)
    $allCaught = if ($MutationPreflight) {
        $mutationPreflightPassed -and $finalRepositoryEqual -and
            $script:cleanupErrors.Count -eq 0
    } else {
        $experiments.Count -eq $definitions.Count -and
            $experiments.Count -gt 0 -and
            $mutationPreflightPassed -and
            @($experiments | Where-Object {
                $_.exit -ne 1 -or -not $_.childProofAccepted -or
                    -not $_.sourceBytesRestoredExactly -or
                    -not $_.runtimePreviewProcessAbsentProven -or
                    -not $_.runtimePreviewListenerAbsenceProven -or
                    -not $_.runtimePreviewStopped -or -not $_.repositoryUnchanged
            }).Count -eq 0 -and $finalRepositoryEqual -and
            $script:cleanupErrors.Count -eq 0
    }
    if ($allCaught) {
        $overallExit = if ($MutationPreflight) { 0 } else { 1 }
    } else { $overallExit = 2 }
    if (($overallExit -eq 0 -or $overallExit -eq 1) -and
            (Test-Path -LiteralPath $runRoot)) {
        try {
            Remove-SourceExperimentTree $runRoot 'disposable experiment root'
            $runRootRetained = $false
            $runRootRemoved = $true
        } catch {
            $script:cleanupErrors += ('SOURCE_EXPERIMENT_INFRASTRUCTURE: disposable experiment root cleanup failed; retained evidence at ' +
                $runRoot + ': ' + (Get-ExperimentErrorMessage $_))
            $overallExit = 2
        }
    }
    try {
        Write-Host 'SOURCE_STAGE evidence-start'
        New-SourceExperimentDirectory $evidenceRoot 'source-experiment evidence' -Force
        $record = [ordered]@{
            protocol = 'troubleshootjs-task43p-source-experiments-v2'
            runId = $runId
            baselineSha = $publishedBaselineSha
            repositoryBefore = ConvertTo-SourceRepositoryEvidence $repositoryBefore
            repositoryAfter = ConvertTo-SourceRepositoryEvidence $finalRepositoryState
            runRoot = [string]$runRoot
            runRootRetained = [bool]$runRootRetained
            runRootRemoved = [bool]$runRootRemoved
            evidencePath = [string]$evidencePath
            mutationPreflightRequested = [bool]$MutationPreflight
            mutationPreflightPassed = [bool]$mutationPreflightPassed
            mutationPreflight = $mutationPreflightResult
            status = if ($MutationPreflight) {
                if ($overallExit -eq 0) { 'PREFLIGHT_PASS' } else { 'PREFLIGHT_FAILED' }
            } elseif ($overallExit -eq 1) { 'CAUGHT' } else { 'UNPROVEN' }
            exit = [int]$overallExit
            experiments = @($experiments | ForEach-Object {
                ConvertTo-SourceExperimentEvidence $_
            })
            cleanupErrors = @($script:cleanupErrors | ForEach-Object { [string]$_ })
            visibleBrowserRequired = -not [bool]$MutationPreflight
            note = if ($overallExit -eq 0 -or $overallExit -eq 1) {
                if ($MutationPreflight) {
                    'All 11 source mutation anchors changed disposable bytes and restored the original bytes exactly; no build or browser route was started.'
                } else {
                    'All selected producer-path source negatives were caught through the compiled disposable route with exact child proof and cleanup.'
                }
            } else {
                if ($MutationPreflight) {
                    'One or more source mutation preflight anchors or disposable restore checks failed; no build or browser route was started.'
                } else {
                    'One or more producer-path source negatives lacked complete compiled child proof or cleanup; acceptance remains unproven.'
                }
            }
        }
        Write-SourceExperimentText $evidencePath ($record | ConvertTo-Json -Depth 30) `
            'final source-experiment evidence'
        $evidenceWritten = $true
        Write-Host 'SOURCE_STAGE evidence-done'
    } catch {
        $script:cleanupErrors += ('SOURCE_EXPERIMENT_INFRASTRUCTURE: source experiment evidence persistence failed: ' +
            (Get-ExperimentErrorMessage $_))
        $overallExit = 2
    }
}
if ($MutationPreflight) {
    Write-Host ("TASK43P MUTATION PREFLIGHT " +
        $(if ($overallExit -eq 0) { 'PREFLIGHT_PASS' } else { 'PREFLIGHT_FAILED' }) +
        " exit=$overallExit evidence=$evidencePath " +
        "repositoryUnchanged=$([bool]($null -ne $repositoryBefore -and $null -ne $finalRepositoryState -and (Test-RepositoryStateEqual $repositoryBefore $finalRepositoryState)))")
} else {
    Write-Host ("TASK43P SOURCE EXPERIMENTS " +
        $(if ($overallExit -eq 1) { 'CAUGHT' } else { 'UNPROVEN' }) +
        " exit=$overallExit evidence=$evidencePath " +
        "repositoryUnchanged=$([bool]($null -ne $repositoryBefore -and $null -ne $finalRepositoryState -and (Test-RepositoryStateEqual $repositoryBefore $finalRepositoryState)))")
}
if ($script:cleanupErrors.Count -gt 0) {
    Write-Host ('TASK43P SOURCE EXPERIMENT CLEANUP/PROOF ERRORS: ' +
        ($script:cleanupErrors -join '; '))
}
exit $overallExit

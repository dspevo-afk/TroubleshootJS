[CmdletBinding()]
param(
    [string]$JavaHome = '',
    [ValidateRange(30, 1800)]
    [int]$ProcessTimeoutSeconds = 900,
    [AllowEmptyString()]
    [string]$ExperimentId = '',
    [AllowEmptyString()]
    [string]$EvidenceDirectory = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$publishedBaselineSha = '20f83535163070a0688fcc0958715e6bc827d445'
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

function Get-ExperimentCanonicalPath([string]$Path) {
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
        if (Test-Path -LiteralPath $Path) {
            Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction Stop
            if (Test-Path -LiteralPath $Path) {
                Throw-SourceExperimentInfrastructure ("$Phase remained after cleanup: $Path")
            }
        }
    } catch {
        if ($_.Exception.Message -like 'SOURCE_EXPERIMENT_INFRASTRUCTURE:*') { throw }
        Throw-SourceExperimentInfrastructure ("Could not remove $Phase '$Path'.") $_
    }
}

function Assert-DisposablePreviewIdentity([string]$Destination, $Identity, [int]$Port) {
    if ($null -eq $Identity -or $Identity -is [System.Array] -or
            $Identity -isnot [pscustomobject]) {
        Throw-SourceExperimentInfrastructure 'Disposable preview returned a malformed identity object.'
    }
    $expectedRepositoryRoot = Get-ExperimentCanonicalPath $Destination
    $expectedWebRoot = Get-ExperimentCanonicalPath (Join-Path $Destination 'war')
    $expectedScriptRoot = Get-ExperimentCanonicalPath (Join-Path $Destination 'scripts')
    $expectedPreviewScript = Get-ExperimentCanonicalPath (Join-Path $expectedScriptRoot 'preview.ps1')
    foreach ($propertyName in @('protocol', 'repositoryRoot', 'previewScript', 'webRoot',
            'previewPort')) {
        if ($null -eq $Identity.PSObject.Properties[$propertyName]) {
            Throw-SourceExperimentInfrastructure "Disposable preview identity omitted '$propertyName'."
        }
    }
    if ([string]$Identity.protocol -ne 'troubleshootjs-preview-identity-v1' -or
            -not ((Get-ExperimentCanonicalPath ([string]$Identity.repositoryRoot)).Equals(
                $expectedRepositoryRoot, [StringComparison]::OrdinalIgnoreCase)) -or
            -not ((Get-ExperimentCanonicalPath ([string]$Identity.previewScript)).Equals(
                $expectedPreviewScript, [StringComparison]::OrdinalIgnoreCase)) -or
            -not ((Get-ExperimentCanonicalPath ([string]$Identity.webRoot)).Equals(
                $expectedWebRoot, [StringComparison]::OrdinalIgnoreCase)) -or
            [int]$Identity.previewPort -ne $Port) {
        Throw-SourceExperimentInfrastructure 'Disposable preview identity did not match the disposable repository, script, web root, or port.'
    }
    return [ordered]@{
        protocol = [string]$Identity.protocol
        repositoryRoot = [string]$Identity.repositoryRoot
        previewScript = [string]$Identity.previewScript
        webRoot = [string]$Identity.webRoot
        previewPort = [int]$Identity.previewPort
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
            $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
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

function Invoke-ExactTextReplacement([string]$Path, [string]$Needle, [string]$Replacement) {
    try {
        $text = [IO.File]::ReadAllText($Path)
    } catch {
        Throw-SourceExperimentInfrastructure "Could not read source mutation target '$Path'." $_
    }
    $first = $text.IndexOf($Needle, [StringComparison]::Ordinal)
    if ($first -lt 0 -or $first -ne $text.LastIndexOf($Needle, [StringComparison]::Ordinal)) {
        throw "Expected exactly one source mutation anchor in $Path."
    }
    $updated = $text.Substring(0, $first) + $Replacement +
        $text.Substring($first + $Needle.Length)
    Write-SourceExperimentText $Path $updated 'source mutation'
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

function Invoke-DisposableRuntimeExtraction([string]$Destination, [int]$ExperimentIndex,
        [string]$SelectedJavaHome, $Compile) {
    $runtime = [ordered]@{
        attempted = $false
        status = 'UNPROVEN'
        exit = 2
        underlyingExit = 2
        route = 'verify-browser.ps1 -BaseUrl <disposable-preview> -Task43P -Seeds 0 -ExecutionRepositoryRoot <disposable> -ExecutionWebRoot <disposable>\\war -ExecutionScriptRoot <disposable>\\scripts'
        executionRepositoryRoot = ''
        executionWebRoot = ''
        executionScriptRoot = ''
        executionPreviewScript = ''
        executionRootsValidated = $false
        previewIdentity = $null
        wrapperPath = ''
        previewPort = 0
        previewStarted = $false
        previewStopped = $false
        previewOutputTail = @()
        previewErrorTail = @()
        outputTail = @()
        reason = ''
    }
    $previewProcess = $null
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
        $runtime.previewPort = Get-DisposablePreviewPort
        $previewScript = $runtime.executionPreviewScript
        $previewStdout = Join-Path $Destination 'runtime-preview.stdout.log'
        $previewStderr = Join-Path $Destination 'runtime-preview.stderr.log'
        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $previewArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $previewScript,
            '-Port', [string]$runtime.previewPort)
        $previewProcess = Start-Process -FilePath $powershell -ArgumentList $previewArguments `
            -WorkingDirectory $Destination -RedirectStandardOutput $previewStdout `
            -RedirectStandardError $previewStderr -WindowStyle Hidden -PassThru
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
            $runtime.previewPort
        $runtime.executionRootsValidated = $true
        $runtime.previewStarted = $true
        $wrapper = Join-Path $repositoryRoot 'scripts\verify-browser.ps1'
        $runtime.wrapperPath = Get-ExperimentCanonicalPath $wrapper
        $wrapperArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $wrapper,
            '-BaseUrl', $baseUrl, '-Task43P', '-Seeds', '0', '-TimeoutSeconds', '30',
            '-ExecutionRepositoryRoot', $runtime.executionRepositoryRoot,
            '-ExecutionWebRoot', $runtime.executionWebRoot,
            '-ExecutionScriptRoot', $runtime.executionScriptRoot)
        $output = @(& $powershell @wrapperArguments 2>&1)
        $runtime.underlyingExit = [int]$LASTEXITCODE
        $runtime.outputTail = @($output | ForEach-Object { [string]$_ } | Select-Object -Last 32)
        # A disposable negative route is never acceptance evidence.  Only an
        # anchored wrapper result could carry application exit 1; an ordinary
        # route rejection remains unproven and is normalized to infrastructure
        # exit 2 by this harness.
        if ($runtime.underlyingExit -eq 2) {
            $runtime.status = 'UNPROVEN'
            $runtime.exit = 2
            $runtime.reason = 'Compiled extraction route was attempted but browser/CDP/ownership or other infrastructure did not prove it.'
        } elseif ($runtime.underlyingExit -eq 1) {
            $runtime.status = 'REJECTED_UNANCHORED'
            $runtime.exit = 2
            $runtime.reason = 'Compiled route reached an application rejection without the wrapper anchor required for exit 1; not accepted as proof.'
        } else {
            $runtime.status = 'UNPROVEN'
            $runtime.exit = 2
            $runtime.reason = 'Compiled route returned an unexpected exit and was not accepted as proof.'
        }
    } catch {
        $runtime.status = 'UNPROVEN'
        $runtime.exit = 2
        $runtime.reason = 'SOURCE_EXPERIMENT_INFRASTRUCTURE: ' + (Get-ExperimentErrorMessage $_)
    } finally {
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
                $runtime.exit = 2
                $runtime.reason = ($runtime.reason + ' Disposable preview diagnostics could not be read: ' +
                    (Get-ExperimentErrorMessage $_)).Trim()
            }
        }
        if ($null -ne $previewProcess) {
            try {
                if (-not $previewProcess.HasExited) {
                    $previewProcess.Kill()
                    [void]$previewProcess.WaitForExit(5000)
                }
                if ($previewProcess.HasExited) {
                    $runtime.previewStopped = $true
                } else {
                    $runtime.reason = ($runtime.reason + ' Disposable preview process did not stop within the bounded cleanup wait.').Trim()
                }
            } catch {
                $runtime.previewStopped = $false
                $runtime.reason = ($runtime.reason + ' Disposable preview cleanup failed: ' +
                    (Get-ExperimentErrorMessage $_)).Trim()
            }
        }
        if (-not $runtime.previewStopped) {
            $runtime.exit = 2
        }
    }
    return $runtime
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
        Invoke-ExactTextReplacement $sourcePath $Definition.needle $Definition.replacement
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
        $runtime = Invoke-DisposableRuntimeExtraction $experimentRoot 0 $SelectedJavaHome $compile
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
        $runtime = Invoke-DisposableRuntimeExtraction $experimentRoot 0 $SelectedJavaHome `
            ([ordered]@{ attempted = $false; exit = 2; outputTail = @() })
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
    return [ordered]@{
        id = $Definition.id
        relativePath = $Definition.relativePath
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
        runtimeExecutionRootsValidated = [bool]$runtime.executionRootsValidated
        runtimePreviewIdentity = $runtime.previewIdentity
        runtimeWrapperPath = [string]$runtime.wrapperPath
        runtimePreviewPort = $runtime.previewPort
        runtimePreviewStarted = $runtime.previewStarted
        runtimePreviewStopped = $runtime.previewStopped
        runtimePreviewOutputTail = $runtime.previewOutputTail
        runtimePreviewErrorTail = $runtime.previewErrorTail
        runtimeOutputTail = $runtime.outputTail
        runtimeReason = $runtime.reason
        status = $status
        exit = 2
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
        relativePath = [string]$Experiment.relativePath
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
        runtimeExecutionRootsValidated = [bool]$Experiment.runtimeExecutionRootsValidated
        runtimePreviewIdentity = $Experiment.runtimePreviewIdentity
        runtimeWrapperPath = [string]$Experiment.runtimeWrapperPath
        runtimePreviewPort = [int]$Experiment.runtimePreviewPort
        runtimePreviewStarted = [bool]$Experiment.runtimePreviewStarted
        runtimePreviewStopped = [bool]$Experiment.runtimePreviewStopped
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

$definitions = @(
    [ordered]@{
        id = 'renderer-only-j1-1-plus-20px'
        relativePath = 'src\com\lushprojects\circuitjs1\client\PcbWorkbenchRenderer.java'
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
            /* J2.1 is deliberately first: on RC it is the same RC_OUT
             * WireElm/post/node shape as C1.+, so only exact retained element
             * identity can reject it. */
            String[] mismatchCandidates = {"J2.1", "R2.1", "R1.2"};
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
        mutationKind = 'manifest-source-terminal-omission'
        needle = @"
            addTerminal(result, "LED1.A", "LED1", "A", "LED_NODE", "WireElm", 1);
            addTerminal(result, "LED1.K", "LED1", "K", "GND", "GroundElm", 0);
"@
        replacement = @"
            addTerminal(result, "LED1.A", "LED1", "A", "LED_NODE", "WireElm", 1);
"@
    }
)

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
    foreach ($definition in $definitions) {
        Write-Host ("SOURCE_STAGE " + $definition.id + " experiment-start")
        $experiments += Invoke-SourceExperiment $definition $selectedJavaHome $repositoryBefore
        Write-Host ("SOURCE_STAGE " + $definition.id + " experiment-done")
    }
    Write-Host 'SOURCE_STAGE all-experiments-done'
    $finalRepositoryState = Get-RepositoryState
    $allRestored = $true
    foreach ($experiment in $experiments) {
        if (-not $experiment.sourceBytesRestoredExactly -or
                -not $experiment.repositoryUnchanged -or $experiment.exit -ne 2) {
            $allRestored = $false
        }
    }
    if (-not (Test-RepositoryStateEqual $repositoryBefore $finalRepositoryState)) {
        $allRestored = $false
        $script:cleanupErrors += 'Final repository state differed from the pre-experiment state.'
    }
    if ($allRestored -and $script:cleanupErrors.Count -eq 0) {
        $overallExit = 2
    }
    } catch {
        $script:cleanupErrors += ('SOURCE_EXPERIMENT_INFRASTRUCTURE: experiment orchestration failed: ' +
            (Get-ExperimentErrorMessage $_))
} finally {
    try {
        if (Test-Path -LiteralPath $runRoot) {
            Remove-SourceExperimentTree $runRoot 'disposable experiment root'
        }
    } catch {
        $script:cleanupErrors += ('SOURCE_EXPERIMENT_INFRASTRUCTURE: disposable experiment root cleanup failed: ' +
            (Get-ExperimentErrorMessage $_))
    }
    try {
        if ($null -eq $finalRepositoryState) {
            $finalRepositoryState = Get-RepositoryState
        }
    } catch {
        $script:cleanupErrors += ('Final repository state could not be proven: ' +
            (Get-ExperimentErrorMessage $_))
    }
    try {
        Write-Host 'SOURCE_STAGE evidence-start'
        New-SourceExperimentDirectory $evidenceRoot 'source-experiment evidence' -Force
        $record = [ordered]@{
            protocol = 'troubleshootjs-task43p-source-experiments-v1'
            runId = $runId
            baselineSha = $publishedBaselineSha
            repositoryBefore = ConvertTo-SourceRepositoryEvidence $repositoryBefore
            repositoryAfter = ConvertTo-SourceRepositoryEvidence $finalRepositoryState
            runRoot = [string]$runRoot
            evidencePath = [string]$evidencePath
            status = 'UNPROVEN'
            exit = 2
            experiments = @($experiments | ForEach-Object {
                ConvertTo-SourceExperimentEvidence $_
            })
            cleanupErrors = @($script:cleanupErrors | ForEach-Object { [string]$_ })
            visibleBrowserRequired = $true
            note = 'Disposable producer-path source mutations compiled and attempted a bounded compiled extraction route. Runtime/visible @Browser proof is UNPROVEN when infrastructure prevents it; no acceptance is claimed.'
        }
        Write-SourceExperimentText $evidencePath ($record | ConvertTo-Json -Depth 30) `
            'final source-experiment evidence'
        Write-Host 'SOURCE_STAGE evidence-done'
    } catch {
        $script:cleanupErrors += ('SOURCE_EXPERIMENT_INFRASTRUCTURE: source experiment evidence persistence failed: ' +
            (Get-ExperimentErrorMessage $_))
        $overallExit = 2
    }
}
Write-Host ("TASK43P SOURCE EXPERIMENTS UNPROVEN exit=$overallExit evidence=$evidencePath " +
    "repositoryUnchanged=$([bool]($null -ne $repositoryBefore -and $null -ne $finalRepositoryState -and (Test-RepositoryStateEqual $repositoryBefore $finalRepositoryState)))")
if ($script:cleanupErrors.Count -gt 0) {
    Write-Host ('TASK43P SOURCE EXPERIMENT CLEANUP/PROOF ERRORS: ' +
        ($script:cleanupErrors -join '; '))
}
exit $overallExit

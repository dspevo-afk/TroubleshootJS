[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateSet('OBF', 'PRETTY', 'DETAILED')]
    [string]$Style = 'OBF',
    [ValidateSet('Compile', 'Dev')]
    [string]$Target = 'Compile',
    [ValidateRange(1, 65535)]
    [int]$Port = 8888,
    [ValidateRange(1, 3600)]
    [int]$ProcessTimeoutSeconds = 900,
    [switch]$BuildProcessCanary
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

try {
    if ($null -eq ('TroubleshootJS.BuildOutputCollector' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Collections.Concurrent;
using System.Diagnostics;

namespace TroubleshootJS {
    public sealed class BuildOutputCollector {
        public readonly ConcurrentQueue<string> Lines = new ConcurrentQueue<string>();

        public void Handle(object sender, DataReceivedEventArgs args) {
            if (args != null && args.Data != null) {
                Lines.Enqueue(args.Data);
            }
        }

        public DataReceivedEventHandler GetHandler() {
            return new DataReceivedEventHandler(Handle);
        }
    }
}
'@
    }
} catch {
    Write-Host ('BUILD_INFRASTRUCTURE: could not initialize bounded build output capture: ' +
        [string]$_.Exception.Message)
    exit 2
}

$verifierIsolationModule = Join-Path $PSScriptRoot 'VerifierIsolation.psm1'
try {
    Import-Module $verifierIsolationModule -Force -ErrorAction Stop
} catch {
    Write-Host ('BUILD_INFRASTRUCTURE: could not import shared verifier identity helpers: ' +
        [string]$_.Exception.Message)
    exit 2
}

function Get-BuildErrorMessage($ErrorRecord) {
    if ($null -eq $ErrorRecord) { return '' }
    if ($ErrorRecord.PSObject.Properties['Exception'] -and
            $null -ne $ErrorRecord.Exception) {
        return [string]$ErrorRecord.Exception.Message
    }
    return [string]$ErrorRecord
}

function New-BuildInfrastructureException([string]$Message,
        [string]$ProcessRoot = '') {
    $exception = [InvalidOperationException]::new('BUILD_INFRASTRUCTURE: ' + $Message)
    $exception.Data['BuildFailureKind'] = 'infrastructure'
    if (-not [String]::IsNullOrWhiteSpace($ProcessRoot)) {
        $exception.Data['BuildProcessRoot'] = $ProcessRoot
    }
    return $exception
}

function Throw-BuildInfrastructure([string]$Message,
        [string]$ProcessRoot = '') {
    throw (New-BuildInfrastructureException $Message $ProcessRoot)
}

function Test-BuildInfrastructureError($ErrorRecord) {
    $exception = if ($ErrorRecord -and $ErrorRecord.PSObject.Properties['Exception'] -and
            $null -ne $ErrorRecord.Exception) { $ErrorRecord.Exception } else { $ErrorRecord }
    if ($exception -and $exception.Data -and
            $exception.Data.Contains('BuildFailureKind') -and
            [string]$exception.Data['BuildFailureKind'] -eq 'infrastructure') {
        return $true
    }
    return (Get-BuildErrorMessage $ErrorRecord).IndexOf(
        'BUILD_INFRASTRUCTURE:', [StringComparison]::OrdinalIgnoreCase) -ge 0
}

function ConvertTo-BuildWindowsArgument([string]$Value) {
    if ($null -eq $Value -or $Value.Length -eq 0) { return '""' }
    $builder = New-Object Text.StringBuilder
    [void]$builder.Append([char]34)
    $backslashes = 0
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq [char]92) { $backslashes++; continue }
        if ($character -eq [char]34) {
            for ($i = 0; $i -lt (2 * $backslashes + 1); $i++) {
                [void]$builder.Append([char]92)
            }
            [void]$builder.Append([char]34)
            $backslashes = 0
            continue
        }
        for ($i = 0; $i -lt $backslashes; $i++) {
            [void]$builder.Append([char]92)
        }
        [void]$builder.Append($character)
        $backslashes = 0
    }
    for ($i = 0; $i -lt (2 * $backslashes); $i++) {
        [void]$builder.Append([char]92)
    }
    [void]$builder.Append([char]34)
    return $builder.ToString()
}

function ConvertTo-BuildArgumentString([string[]]$Arguments) {
    return [string]::Join(' ', @($Arguments | ForEach-Object {
        ConvertTo-BuildWindowsArgument ([string]$_)
    }))
}

function Flush-BuildProcessLog([string]$Path, [string]$Label, [ref]$Offset) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return }
    try { $text = [IO.File]::ReadAllText($Path) } catch { return }
    if ($text.Length -lt $Offset.Value) { $Offset.Value = 0 }
    if ($text.Length -le $Offset.Value) { return }
    $delta = $text.Substring($Offset.Value)
    $Offset.Value = $text.Length
    foreach ($line in ($delta -split "`r?`n")) {
        if ($line.Length -gt 0) { Write-Host ('[' + $Label + '] ' + $line) }
    }
}

function Flush-BuildOutputCollector($Collector, [string]$Label,
        [Collections.ArrayList]$Emitted) {
    if ($null -eq $Collector) { return }
    $snapshot = @($Collector.Lines.ToArray())
    while ($Emitted.Count -lt $snapshot.Count) {
        $line = [string]$snapshot[$Emitted.Count]
        [void]$Emitted.Add($line)
        Write-Host ('[' + $Label + '] ' + $line)
    }
}

function Get-BuildExpectedCommandLine([string]$FilePath, [string[]]$Arguments) {
    return (ConvertTo-BuildArgumentString (@($FilePath) + @($Arguments)))
}

function Get-BuildExpectedPort([string[]]$Arguments) {
    for ($index = 0; $index -lt $Arguments.Count; $index++) {
        if ([string]$Arguments[$index] -ieq '-port' -and $index + 1 -lt $Arguments.Count) {
            $port = 0
            if ([int]::TryParse([string]$Arguments[$index + 1],
                    [Globalization.NumberStyles]::Integer,
                    [Globalization.CultureInfo]::InvariantCulture, [ref]$port)) {
                return $port
            }
        }
    }
    return 0
}

function Stop-BuildProcessExactly($Process, [long]$ExpectedStartTicks,
        [string]$ExpectedCommandLine = '', [int]$ExpectedPort = 0,
        [int]$WaitMilliseconds = 5000) {
    if ($null -eq $Process -or [int]$Process.Id -le 0) {
        Throw-BuildInfrastructure 'A bounded build process had no valid PID.'
    }
    try {
        $Process.Refresh()
        if (-not [bool]$Process.HasExited) {
            $current = Get-Process -Id ([int]$Process.Id) -ErrorAction SilentlyContinue
            if ($null -eq $current) {
                Throw-BuildInfrastructure "Build process PID $($Process.Id) disappeared before termination was proven."
            }
            $currentTicks = [long](Get-VerifierProcessStartTicks $current)
            if ($currentTicks -ne $ExpectedStartTicks) {
                Throw-BuildInfrastructure "Refusing to terminate reused build process PID $($Process.Id)."
            }
            $currentRecords = @(Get-CimInstance Win32_Process `
                -Filter "ProcessId = $($Process.Id)" -ErrorAction Stop)
            if ($currentRecords.Count -ne 1 -or
                    -not $currentRecords[0].PSObject.Properties['ProcessId'] -or
                    -not $currentRecords[0].PSObject.Properties['ParentProcessId'] -or
                    -not $currentRecords[0].PSObject.Properties['CommandLine'] -or
                    [String]::IsNullOrWhiteSpace([string]$currentRecords[0].CommandLine) -or
                    [int]$currentRecords[0].ParentProcessId -le 0 -or
                    [int]$currentRecords[0].ProcessId -ne [int]$Process.Id) {
                Throw-BuildInfrastructure "Build process PID $($Process.Id) had no complete current command-line identity."
            }
            $currentCommandLine = [string]$currentRecords[0].CommandLine
            if (-not [String]::IsNullOrWhiteSpace($ExpectedCommandLine) -and
                    -not (Test-VerifierCommandLineEquivalent $currentCommandLine $ExpectedCommandLine)) {
                Throw-BuildInfrastructure "Build process PID $($Process.Id) command-line identity changed before termination."
            }
            if ($ExpectedPort -gt 0 -and
                    -not (Test-VerifierCommandLineSwitch $currentCommandLine '-port' ([string]$ExpectedPort))) {
                Throw-BuildInfrastructure "Build process PID $($Process.Id) no longer carries port $ExpectedPort before termination."
            }
            $verifiedCurrent = Get-Process -Id ([int]$Process.Id) -ErrorAction Stop
            $verifiedCurrent.Refresh()
            if ([bool]$verifiedCurrent.HasExited -or
                    ([long](Get-VerifierProcessStartTicks $verifiedCurrent) -ne $ExpectedStartTicks)) {
                Throw-BuildInfrastructure "Build process PID $($Process.Id) changed or exited after current identity validation."
            }
            $buildCurrentRecord = [pscustomobject]@{
                ProcessId = [int]$currentRecords[0].ProcessId
                ParentProcessId = [int]$currentRecords[0].ParentProcessId
                ProcessStartTicks = $ExpectedStartTicks
                CommandLine = $currentCommandLine
            }
            [void](Stop-VerifierVerifiedProcessExactly $verifiedCurrent $ExpectedStartTicks `
                $WaitMilliseconds $buildCurrentRecord)
        }
        if (-not $Process.WaitForExit($WaitMilliseconds)) {
            Throw-BuildInfrastructure "Build process PID $($Process.Id) did not terminate within the cleanup bound."
        }
        $Process.Refresh()
        if (-not [bool]$Process.HasExited) {
            Throw-BuildInfrastructure "Build process PID $($Process.Id) remained alive after WaitForExit."
        }
        if ($null -ne (Get-Process -Id ([int]$Process.Id) -ErrorAction SilentlyContinue)) {
            Throw-BuildInfrastructure "Build process PID $($Process.Id) remained after exact termination."
        }
    } catch {
        if (Test-BuildInfrastructureError $_) { throw }
        Throw-BuildInfrastructure ('Could not prove exact build-process termination for PID ' +
            [string]$Process.Id + ': ' + (Get-BuildErrorMessage $_))
    }
}

function Invoke-BuildBoundedProcess([string]$FilePath, [string[]]$Arguments,
        [int]$TimeoutMilliseconds, [string]$Label) {
    if ($TimeoutMilliseconds -lt 1) {
        Throw-BuildInfrastructure 'A bounded build-process timeout must be positive.'
    }
    $processRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\build-process-' + [Guid]::NewGuid().ToString('N'))
    $stdoutLog = Join-Path $processRoot 'stdout.log'
    $stderrLog = Join-Path $processRoot 'stderr.log'
    $process = $null
    $startTicks = 0L
    $terminationProven = $false
    $stdoutCollector = $null
    $stderrCollector = $null
    $stdoutLines = New-Object Collections.ArrayList
    $stderrLines = New-Object Collections.ArrayList
    try {
        New-Item -ItemType Directory -Path $processRoot -Force -ErrorAction Stop | Out-Null
        # Use ProcessStartInfo instead of Start-Process: Windows PowerShell
        # 5.1 can return a process object with a null ExitCode when redirected
        # files are used. Direct redirected streams preserve exact exit proof.
        $startInfo = New-Object Diagnostics.ProcessStartInfo
        $startInfo.FileName = $FilePath
        $startInfo.Arguments = ConvertTo-BuildArgumentString $Arguments
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $process = New-Object Diagnostics.Process
        $process.StartInfo = $startInfo
        if (-not $process.Start()) {
            Throw-BuildInfrastructure "Could not start bounded build process '$FilePath'."
        }
        $processId = [int]$process.Id
        $expectedCommandLine = Get-BuildExpectedCommandLine $FilePath $Arguments
        $expectedPort = Get-BuildExpectedPort $Arguments
        $stdoutCollector = New-Object TroubleshootJS.BuildOutputCollector
        $stderrCollector = New-Object TroubleshootJS.BuildOutputCollector
        $stdoutHandler = $stdoutCollector.GetHandler()
        $stderrHandler = $stderrCollector.GetHandler()
        $process.add_OutputDataReceived($stdoutHandler)
        $process.add_ErrorDataReceived($stderrHandler)
        $process.BeginOutputReadLine()
        $process.BeginErrorReadLine()
        try {
            $startTicks = [long](Get-VerifierProcessStartTicks $process)
        } catch {
            if (-not $process.WaitForExit($TimeoutMilliseconds)) {
                Throw-BuildInfrastructure "Could not capture build process identity and PID $processId did not exit within the bound."
            }
            $process.Refresh()
            if (-not [bool]$process.HasExited) {
                Throw-BuildInfrastructure "Could not prove build process PID $processId terminated after identity capture failed."
            }
            Throw-BuildInfrastructure "Could not capture build process start identity for PID $processId."
        }
        $deadline = [DateTime]::UtcNow.AddMilliseconds($TimeoutMilliseconds)
        $exited = $false
        while ([DateTime]::UtcNow -lt $deadline) {
            Flush-BuildOutputCollector $stdoutCollector ($Label + ':stdout') $stdoutLines
            Flush-BuildOutputCollector $stderrCollector ($Label + ':stderr') $stderrLines
            $process.Refresh()
            if ([bool]$process.HasExited) { $exited = $true; break }
            Start-Sleep -Milliseconds 100
        }
        if (-not $exited) {
            Stop-BuildProcessExactly $process $startTicks $expectedCommandLine $expectedPort
            $terminationProven = $true
            Throw-BuildInfrastructure "Build process '$FilePath' exceeded ${TimeoutMilliseconds}ms; evidence retained at '$processRoot'."
        }
        $process.Refresh()
        if (-not [bool]$process.HasExited) {
            Stop-BuildProcessExactly $process $startTicks $expectedCommandLine $expectedPort
            $terminationProven = $true
            Throw-BuildInfrastructure "Build process '$FilePath' had an unproven exit after WaitForExit polling."
        }
        if (-not $process.WaitForExit(5000)) {
            Throw-BuildInfrastructure "Build process '$FilePath' output streams did not close within the bound."
        }
        $process.Refresh()
        if (-not [bool]$process.HasExited) {
            Throw-BuildInfrastructure "Build process '$FilePath' became non-terminal during output drain."
        }
        Flush-BuildOutputCollector $stdoutCollector ($Label + ':stdout') $stdoutLines
        Flush-BuildOutputCollector $stderrCollector ($Label + ':stderr') $stderrLines
        $stdout = if ($stdoutLines.Count -eq 0) { '' } else {
            [string]::Join([Environment]::NewLine, @($stdoutLines)) + [Environment]::NewLine
        }
        $stderr = if ($stderrLines.Count -eq 0) { '' } else {
            [string]::Join([Environment]::NewLine, @($stderrLines)) + [Environment]::NewLine
        }
        [IO.File]::WriteAllText($stdoutLog, $stdout, [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($stderrLog, $stderr, [Text.UTF8Encoding]::new($false))
        $rawExitCode = $process.ExitCode
        $exitText = if ($null -eq $rawExitCode) { '' } else { [string]$rawExitCode }
        if ([String]::IsNullOrWhiteSpace($exitText) -or $exitText -notmatch '^-?\d+$') {
            Throw-BuildInfrastructure "Build process '$FilePath' did not expose a numeric exit code."
        }
        try { $exitCode = [int]$exitText } catch {
            Throw-BuildInfrastructure "Build process '$FilePath' exposed an out-of-range exit code '$exitText'."
        }
        $terminationProven = $true
        return [pscustomobject]@{
            FilePath = $FilePath; Arguments = @($Arguments); ProcessId = $processId
            ProcessStartTicks = $startTicks; ExitCode = $exitCode
            Stdout = $stdout
            Stderr = $stderr
            StdoutLog = $stdoutLog; StderrLog = $stderrLog
            ProcessRoot = $processRoot; TerminationProven = $terminationProven
        }
    } catch {
        try {
            Flush-BuildOutputCollector $stdoutCollector ($Label + ':stdout') $stdoutLines
            Flush-BuildOutputCollector $stderrCollector ($Label + ':stderr') $stderrLines
            if ($stdoutLines.Count -gt 0) {
                [IO.File]::WriteAllText($stdoutLog,
                    ([string]::Join([Environment]::NewLine, @($stdoutLines)) + [Environment]::NewLine),
                    [Text.UTF8Encoding]::new($false))
            }
            if ($stderrLines.Count -gt 0) {
                [IO.File]::WriteAllText($stderrLog,
                    ([string]::Join([Environment]::NewLine, @($stderrLines)) + [Environment]::NewLine),
                    [Text.UTF8Encoding]::new($false))
            }
        } catch { }
        if ($process -and -not $terminationProven -and $startTicks -gt 0) {
            try {
                $process.Refresh()
                if (-not [bool]$process.HasExited) {
                    Stop-BuildProcessExactly $process $startTicks $expectedCommandLine $expectedPort
                    $terminationProven = $true
                }
            } catch {
                Throw-BuildInfrastructure ((Get-BuildErrorMessage $_) +
                    ". Build process evidence retained at '$processRoot'.")
            }
        }
        if (Test-BuildInfrastructureError $_) {
            throw (New-BuildInfrastructureException ((Get-BuildErrorMessage $_) +
                " Build process evidence retained at '$processRoot'.") $processRoot)
        }
        throw (New-BuildInfrastructureException ("Build process '$FilePath' failed: " +
            (Get-BuildErrorMessage $_) + ". Evidence retained at '$processRoot'.") $processRoot)
    }
}

function Remove-BuildOwnedProcessRoot([string]$Path) {
    if ([String]::IsNullOrWhiteSpace($Path)) { return }
    $full = Get-VerifierCanonicalWindowsPath $Path
    $tempRoot = Get-VerifierCanonicalWindowsPath ([IO.Path]::GetTempPath())
    if ([String]::IsNullOrWhiteSpace($full) -or
            [String]::IsNullOrWhiteSpace($tempRoot) -or
            -not (Test-VerifierPhysicalChildPath $tempRoot $full)) {
        Throw-BuildInfrastructure "Refusing to remove a build-process path outside the temporary root: $full"
    }
    if (Test-Path -LiteralPath $full) {
        Remove-VerifierOwnedTree $tempRoot $full
    }
}

try {
    if ($BuildProcessCanary) {
        $powershellCommand = Get-Command 'powershell.exe' -ErrorAction SilentlyContinue
        if ($null -eq $powershellCommand) {
            Throw-BuildInfrastructure 'PowerShell 5.1 is required for the bounded build-process canary.'
        }
        $exitProbe = Invoke-BuildBoundedProcess $powershellCommand.Source `
            @('-NoProfile', '-NonInteractive', '-Command', 'exit 7') 10000 'exit-probe'
        if (-not $exitProbe.TerminationProven -or [int]$exitProbe.ExitCode -ne 7) {
            Throw-BuildInfrastructure 'bounded build-process exit canary did not prove numeric exit 7.'
        }
        Remove-BuildOwnedProcessRoot $exitProbe.ProcessRoot
        $timeoutClassified = $false
        $timeoutProcessRoot = ''
        try {
            [void](Invoke-BuildBoundedProcess $powershellCommand.Source `
                @('-NoProfile', '-NonInteractive', '-Command', 'Start-Sleep -Seconds 60') 500 'timeout-probe')
        } catch {
            $timeoutClassified = Test-BuildInfrastructureError $_
            $timeoutException = if ($_.PSObject.Properties['Exception'] -and
                    $null -ne $_.Exception) { $_.Exception } else { $_ }
            if ($timeoutException -and $timeoutException.Data -and
                    $timeoutException.Data.Contains('BuildProcessRoot')) {
                $timeoutProcessRoot = [string]$timeoutException.Data['BuildProcessRoot']
            }
        }
        if (-not $timeoutClassified) {
            Throw-BuildInfrastructure 'bounded build-process timeout canary was not classified as infrastructure.'
        }
        if ([String]::IsNullOrWhiteSpace($timeoutProcessRoot)) {
            Throw-BuildInfrastructure 'bounded build-process timeout canary did not retain an exact process evidence root.'
        }
        # The timeout is an expected, successfully asserted canary outcome.
        # Only now may its exact temporary namespace be removed. Unexpected
        # failures retain the root and its logs for diagnosis.
        Remove-BuildOwnedProcessRoot $timeoutProcessRoot
        Write-Host 'PASS:bounded build-process exit and timeout canary'
        exit 0
    }

    $repositoryRoot = Split-Path -Parent $PSScriptRoot
$gwtVersion = '2.7.0'
$toolRoot = Join-Path $repositoryRoot ".tools\gwt-$gwtVersion"
$workDirectoryName = if ($Target -eq 'Dev') { 'dev-work' } else { 'compile-work' }
$cacheDirectoryName = if ($Target -eq 'Dev') { 'dev-cache' } else { 'compile-cache' }
$workDirectory = Join-Path $toolRoot $workDirectoryName
$persistentCacheRoot = Join-Path $toolRoot $cacheDirectoryName
$moduleName = 'com.lushprojects.circuitjs1.circuitjs1'

if ($JavaHome) {
    $java = Join-Path $JavaHome 'bin\java.exe'
} else {
    $javaCommand = Get-Command 'java.exe' -ErrorAction SilentlyContinue
    $java = if ($javaCommand) { $javaCommand.Source } else { $null }
}

if (-not $java -or -not (Test-Path $java -PathType Leaf)) {
    Throw-BuildInfrastructure 'Java was not found. Install a JDK 8 distribution and set JAVA_HOME, or pass -JavaHome.'
}

$versionResult = Invoke-BuildBoundedProcess $java @('-version') 30000 'java-version'
if (-not $versionResult.TerminationProven -or [int]$versionResult.ExitCode -ne 0) {
    Throw-BuildInfrastructure "JDK version probe failed with exit code $($versionResult.ExitCode)."
}
    $javaVersion = [string]$versionResult.Stdout + [string]$versionResult.Stderr
    if ($javaVersion -notmatch 'version "1\.8\.') {
        Throw-BuildInfrastructure "GWT $gwtVersion requires JDK 8. Selected Java reported: $($javaVersion.Trim())"
    }
    Remove-BuildOwnedProcessRoot $versionResult.ProcessRoot

$artifacts = @(
    [pscustomobject]@{ File = 'gwt-dev-2.7.0.jar'; Url = 'https://repo.maven.apache.org/maven2/com/google/gwt/gwt-dev/2.7.0/gwt-dev-2.7.0.jar'; Sha256 = 'C7321E367FC24E5C7AD97B74D1CF2980C45FBD7CBE1FD412F8A8BA128F685135' },
    [pscustomobject]@{ File = 'gwt-user-2.7.0.jar'; Url = 'https://repo.maven.apache.org/maven2/com/google/gwt/gwt-user/2.7.0/gwt-user-2.7.0.jar'; Sha256 = 'D3721BCDD7C6855A524801212DD74548967D8FB65CBC212A20890732FF2C6948' },
    [pscustomobject]@{ File = 'validation-api-1.0.0.GA.jar'; Url = 'https://repo.maven.apache.org/maven2/javax/validation/validation-api/1.0.0.GA/validation-api-1.0.0.GA.jar'; Sha256 = 'E459F313EBC6DB2483F8CEAAD39AF07086361B474FA92E40F442E8DE5D9895DC' },
    [pscustomobject]@{ File = 'validation-api-1.0.0.GA-sources.jar'; Url = 'https://repo.maven.apache.org/maven2/javax/validation/validation-api/1.0.0.GA/validation-api-1.0.0.GA-sources.jar'; Sha256 = 'A394D52A9B7FE2BB14F0718D2B3C8308FFE8F37E911956012398D55C9F9F9B54' },
    [pscustomobject]@{ File = 'asm-5.0.3.jar'; Url = 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm/5.0.3/asm-5.0.3.jar'; Sha256 = '71C4F78E437B8FDCD9CC0DFD2ABEA8C089EB677005A6A5CFF320206CC52B46CC' },
    [pscustomobject]@{ File = 'asm-analysis-5.0.3.jar'; Url = 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm-analysis/5.0.3/asm-analysis-5.0.3.jar'; Sha256 = 'E8FA2A63462C96557DCD36C25525E1264B77366FF851CF0B94EB7592B290849D' },
    [pscustomobject]@{ File = 'asm-commons-5.0.3.jar'; Url = 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm-commons/5.0.3/asm-commons-5.0.3.jar'; Sha256 = '18C1E092230233C9D29E46F21943D769BDB48130CC279E4B0E663F423948C2DA' },
    [pscustomobject]@{ File = 'asm-tree-5.0.3.jar'; Url = 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm-tree/5.0.3/asm-tree-5.0.3.jar'; Sha256 = '347A7A9400F9964E87C91D3980E48EEBDC8D024BC3B36F7F22189C662853A51C' },
    [pscustomobject]@{ File = 'asm-util-5.0.3.jar'; Url = 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm-util/5.0.3/asm-util-5.0.3.jar'; Sha256 = '2768EDBFA2681B5077F08151DE586A6D66B916703CDA3AB297E58B41AE8F2362' }
)

New-Item -ItemType Directory -Force -Path $toolRoot, $workDirectory, $persistentCacheRoot | Out-Null
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

foreach ($artifact in $artifacts) {
    $destination = Join-Path $toolRoot $artifact.File
    if ((Test-Path $destination -PathType Leaf) -and
        (Get-FileHash -Algorithm SHA256 -Path $destination).Hash -ne $artifact.Sha256) {
        Write-Host "Refreshing $($artifact.File) after a checksum mismatch..."
        Remove-Item -Force -Path $destination
    }
    if (-not (Test-Path $destination -PathType Leaf)) {
        Write-Host "Downloading $($artifact.File)..."
        $temporaryDestination = "$destination.download"
        Invoke-WebRequest -UseBasicParsing -Uri $artifact.Url -OutFile $temporaryDestination
        Move-Item -Force -Path $temporaryDestination -Destination $destination
    }
    if ((Get-FileHash -Algorithm SHA256 -Path $destination).Hash -ne $artifact.Sha256) {
        Remove-Item -Force -Path $destination
        throw "Checksum verification failed for $($artifact.File)."
    }
}

$classpathEntries = @((Join-Path $repositoryRoot 'src'))
$classpathEntries += $artifacts | ForEach-Object { Join-Path $toolRoot $_.File }
$classpath = [string]::Join([IO.Path]::PathSeparator, $classpathEntries)

if ($Target -eq 'Dev') {
    $javaArguments = @(
        '-Xmx1g',
        "-Dgwt.persistentunitcachedir=$persistentCacheRoot",
        '-cp', $classpath,
        'com.google.gwt.dev.DevMode',
        '-war', (Join-Path $repositoryRoot 'war'),
        '-workDir', $workDirectory,
        '-bindAddress', '127.0.0.1',
        '-port', $Port,
        '-startupUrl', 'circuitjs.html',
        '-logLevel', 'INFO',
        $moduleName
    )
    Write-Host "Starting CircuitJS at http://127.0.0.1:$Port/circuitjs.html"
} else {
    $javaArguments = @(
        '-Xmx1g',
        "-Dgwt.persistentunitcachedir=$persistentCacheRoot",
        '-cp', $classpath,
        'com.google.gwt.dev.Compiler',
        '-war', (Join-Path $repositoryRoot 'war'),
        '-workDir', $workDirectory,
        '-style', $Style,
        '-logLevel', 'INFO',
        $moduleName
    )
}

Push-Location $repositoryRoot
try {
    $buildResult = Invoke-BuildBoundedProcess $java $javaArguments `
        ([int]$ProcessTimeoutSeconds * 1000) ('gwt-' + $Target.ToLowerInvariant())
    if (-not $buildResult.TerminationProven -or [int]$buildResult.ExitCode -ne 0) {
        Throw-BuildInfrastructure "GWT $Target failed with exit code $($buildResult.ExitCode). Logs retained at '$($buildResult.ProcessRoot)'."
    }
    Remove-BuildOwnedProcessRoot $buildResult.ProcessRoot
} finally {
    Pop-Location
}
} catch {
    # Toolchain, setup, launch, timeout, and unknown child-exit failures are
    # infrastructure. Keep the build's failure class distinct from verifier
    # application FAIL results.
    if (Test-BuildInfrastructureError $_) {
        Write-Host (Get-BuildErrorMessage $_)
        exit 2
    }
    Write-Host ('BUILD_INFRASTRUCTURE: ' + (Get-BuildErrorMessage $_))
    exit 2
}

[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$PythonExe = 'python',
    [string]$ParityOutputPath = ''
)

# Focused, nonvisual Task 46 descriptor/constraint/named-stream checks.  The
# source list is explicit so this harness cannot accidentally pull the solver,
# runtime owner, or a different production path into the pure corpus.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$taskRoot = ''
$resultCode = 2
try {
    Import-Module (Join-Path $PSScriptRoot 'VerifierIsolation.psm1') -Force
    $repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    if ([String]::IsNullOrWhiteSpace($JavaHome)) {
        throw 'Select JDK 8 with -JavaHome or JAVA_HOME.'
    }
    $selectedJavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
    $java = Join-Path $selectedJavaHome 'bin/java.exe'
    $javac = Join-Path $selectedJavaHome 'bin/javac.exe'
    foreach ($executable in @($java, $javac)) {
        if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) {
            throw "Missing selected JDK executable: $executable"
        }
    }
    $javaVersion = Invoke-VerifierBoundedProcess $java @('-version') 15000
    $javacVersion = Invoke-VerifierBoundedProcess $javac @('-version') 15000
    if ($javaVersion.ExitCode -ne 0 -or -not $javaVersion.TerminationProven -or
            ($javaVersion.Stdout + $javaVersion.Stderr) -notmatch 'version\s+"1\.8\.') {
        throw 'The selected java executable is not a verified JDK 8 runtime.'
    }
    if ($javacVersion.ExitCode -ne 0 -or -not $javacVersion.TerminationProven -or
            ($javacVersion.Stdout + $javacVersion.Stderr) -notmatch 'javac\s+1\.8\.') {
        throw 'The selected javac executable is not a verified JDK 8 compiler.'
    }
    Write-Host ('JDK: ' + ($javacVersion.Stdout + $javacVersion.Stderr).Trim())

    $pythonCommand = Get-Command $PythonExe -ErrorAction Stop
    $python = if ($pythonCommand.PSObject.Properties['Source']) {
        [string]$pythonCommand.Source
    } else { [string]$pythonCommand.Path }
    if ([String]::IsNullOrWhiteSpace($python) -or
            -not (Test-Path -LiteralPath $python -PathType Leaf)) {
        throw "Could not resolve Python executable: $PythonExe"
    }
    $pythonVersion = Invoke-VerifierBoundedProcess $python @('--version') 15000
    if ($pythonVersion.ExitCode -ne 0 -or -not $pythonVersion.TerminationProven) {
        throw 'The selected Python executable did not provide a verified version result.'
    }
    Write-Host ('Python: ' + ($pythonVersion.Stdout + $pythonVersion.Stderr).Trim())

    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $taskRoot = Join-Path $tempRoot ('TroubleshootJS-challenge-contracts-' + [Guid]::NewGuid().ToString('N'))
    Assert-VerifierNoReparseAncestors $taskRoot
    if (Test-Path -LiteralPath $taskRoot) { throw 'Contract output directory already exists.' }
    New-Item -ItemType Directory -Path $taskRoot | Out-Null
    $classes = Join-Path $taskRoot 'classes'
    $emptySourcePath = Join-Path $taskRoot 'empty-sourcepath'
    New-Item -ItemType Directory -Path $classes, $emptySourcePath | Out-Null

    $referencePath = Join-Path $repositoryRoot 'tests/contracts/task46_seed_reference.py'
    if (-not (Test-Path -LiteralPath $referencePath -PathType Leaf)) {
        throw 'Missing Task 46 independent seed reference.'
    }
    $oracle = Invoke-VerifierBoundedProcess $python @($referencePath) 60000
    Write-Host $oracle.Stdout
    if ($oracle.Stderr) { Write-Host $oracle.Stderr }
    if (-not $oracle.TerminationProven -or $oracle.ExitCode -ne 0 -or
            $oracle.Stdout -notmatch '(?m)^PASS: Task46 independent seed oracle ') {
        throw "Independent Task 46 seed oracle failed, exit $($oracle.ExitCode)."
    }

    $sourceFiles = @(
        'src/com/lushprojects/circuitjs1/client/BlockContractException.java',
        'src/com/lushprojects/circuitjs1/client/FunctionalBlockDescriptor.java',
        'src/com/lushprojects/circuitjs1/client/BlockNamespace.java',
        'src/com/lushprojects/circuitjs1/client/FunctionalBlockExamples.java',
        'src/com/lushprojects/circuitjs1/client/PcbGeometryContractVersion.java',
        'src/com/lushprojects/circuitjs1/client/ChallengeContractException.java',
        'src/com/lushprojects/circuitjs1/client/GenerationConstraints.java',
        'src/com/lushprojects/circuitjs1/client/ChallengeDescriptor.java',
        'src/com/lushprojects/circuitjs1/client/NamedRandomStreams.java',
        'src/com/lushprojects/circuitjs1/client/Task46ContractVectors.java',
        'tests/contracts/ChallengeDescriptorContractTest.java'
    )
    $compileArguments = @('-source', '7', '-target', '7', '-encoding', 'UTF-8',
        '-classpath', $classes, '-sourcepath', $emptySourcePath, '-d', $classes)
    foreach ($relativePath in $sourceFiles) {
        $sourcePath = Join-Path $repositoryRoot $relativePath
        if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
            throw "Missing explicit contract source: $relativePath"
        }
        $compileArguments += $sourcePath
    }
    $compiled = Invoke-VerifierBoundedProcess $javac $compileArguments 60000
    Write-Host $compiled.Stdout
    if ($compiled.Stderr) { Write-Host $compiled.Stderr }
    if (-not $compiled.TerminationProven -or $compiled.ExitCode -ne 0) {
        throw "Task 46 contract compilation failed, exit $($compiled.ExitCode)."
    }

    $tested = Invoke-VerifierBoundedProcess $java @('-ea', '-cp', $classes,
        'com.lushprojects.circuitjs1.client.ChallengeDescriptorContractTest') 60000
    Write-Host $tested.Stdout
    if ($tested.Stderr) { Write-Host $tested.Stderr }
    if (-not $tested.TerminationProven) {
        throw 'Task 46 contract test termination was not proven.'
    }
    if ($tested.ExitCode -ne 0 -or
            $tested.Stdout -notmatch '(?m)^TASK46_PARITY_BEGIN\r?$' -or
            $tested.Stdout -notmatch '(?m)^TASK46_PARITY_END\r?$' -or
            $tested.Stdout -notmatch '(?m)^PASS: Task46 ') {
        throw "Task 46 contract test did not provide a qualified result, exit $($tested.ExitCode)."
    }
    if ($ParityOutputPath) {
        # Preserve the actual Java corpus bytes, without PowerShell console
        # redirection converting embedded LF to platform line endings.
        $parity = [regex]::Match($tested.Stdout,
            '(?m)^TASK46_PARITY_BEGIN\r?\n([\s\S]*?)^TASK46_PARITY_END\r?$')
        if (-not $parity.Success) { throw 'Task 46 canonical parity body is missing.' }
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($ParityOutputPath),
            $parity.Groups[1].Value, (New-Object Text.UTF8Encoding($false)))
    }
    $resultCode = 0
} catch {
    Write-Host ('CHALLENGE_CONTRACT_INFRASTRUCTURE: ' + $_.Exception.Message)
    $resultCode = 2
} finally {
    if ($taskRoot -and (Test-Path -LiteralPath $taskRoot)) {
        try {
            Remove-VerifierOwnedTree ([IO.Path]::GetFullPath([IO.Path]::GetTempPath())) $taskRoot
            Write-Host 'CLEANUP: challenge contract classes and task-owned scratch removed.'
        } catch {
            Write-Host ('CHALLENGE_CONTRACT_CLEANUP: ' + $_.Exception.Message)
            $resultCode = 2
        }
    }
}
exit $resultCode

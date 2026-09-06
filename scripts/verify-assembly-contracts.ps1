[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$PythonExe = 'python',
    [string]$ReceiptOutputPath = ''
)

# Focused, nonvisual Task 47 request/provider/plan checks.  This harness
# compiles an explicit pure source set and never starts CircuitJS or allocates
# a runtime board.
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

    # Keep the independent named-stream corpus in the same closed pure gate.
    $referencePath = Join-Path $repositoryRoot 'tests/contracts/task46_seed_reference.py'
    $oracle = Invoke-VerifierBoundedProcess $python @($referencePath) 60000
    Write-Host $oracle.Stdout
    if ($oracle.Stderr) { Write-Host $oracle.Stderr }
    if (-not $oracle.TerminationProven -or $oracle.ExitCode -ne 0 -or
            $oracle.Stdout -notmatch '(?m)^PASS: Task46 independent seed oracle ') {
        throw "Independent Task 46 seed oracle failed, exit $($oracle.ExitCode)."
    }

    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $taskRoot = Join-Path $tempRoot ('TroubleshootJS-assembly-contracts-' +
        [Guid]::NewGuid().ToString('N'))
    Assert-VerifierNoReparseAncestors $taskRoot
    if (Test-Path -LiteralPath $taskRoot) { throw 'Contract output directory already exists.' }
    New-Item -ItemType Directory -Path $taskRoot | Out-Null
    $classes = Join-Path $taskRoot 'classes'
    $emptySourcePath = Join-Path $taskRoot 'empty-sourcepath'
    New-Item -ItemType Directory -Path $classes, $emptySourcePath | Out-Null

    $sourceFiles = @(
        'src/com/lushprojects/circuitjs1/client/BlockContractException.java',
        'src/com/lushprojects/circuitjs1/client/FunctionalBlockDescriptor.java',
        'src/com/lushprojects/circuitjs1/client/BlockNamespace.java',
        'src/com/lushprojects/circuitjs1/client/ElectricalContractException.java',
        'src/com/lushprojects/circuitjs1/client/ElectricalPortContract.java',
        'src/com/lushprojects/circuitjs1/client/ElectricalBlockContract.java',
        'src/com/lushprojects/circuitjs1/client/ElectricalConnection.java',
        'src/com/lushprojects/circuitjs1/client/PortCompatibilityPreflight.java',
        'src/com/lushprojects/circuitjs1/client/PcbGeometryContractVersion.java',
        'src/com/lushprojects/circuitjs1/client/ChallengeContractException.java',
        'src/com/lushprojects/circuitjs1/client/GenerationConstraints.java',
        'src/com/lushprojects/circuitjs1/client/ChallengeDescriptor.java',
        'src/com/lushprojects/circuitjs1/client/NamedRandomStreams.java',
        'src/com/lushprojects/circuitjs1/client/GeneratedFaultLocusType.java',
        'src/com/lushprojects/circuitjs1/client/GeneratedFaultLocus.java',
        'src/com/lushprojects/circuitjs1/client/ComposedBlockContribution.java',
        'src/com/lushprojects/circuitjs1/client/ResistiveBlockContributions.java',
        'src/com/lushprojects/circuitjs1/client/BoundedAssemblyRequest.java',
        'src/com/lushprojects/circuitjs1/client/BoundedAssemblyPlan.java',
        'tests/contracts/BoundedAssemblyContractTest.java'
    )
    $compileArguments = @('-source', '7', '-target', '7', '-encoding', 'UTF-8',
        '-classpath', $classes, '-sourcepath', $emptySourcePath, '-d', $classes)
    foreach ($relativePath in $sourceFiles) {
        $sourcePath = Join-Path $repositoryRoot $relativePath
        if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
            throw "Missing explicit assembly contract source: $relativePath"
        }
        $compileArguments += $sourcePath
    }
    $compiled = Invoke-VerifierBoundedProcess $javac $compileArguments 60000
    Write-Host $compiled.Stdout
    if ($compiled.Stderr) { Write-Host $compiled.Stderr }
    if (-not $compiled.TerminationProven -or $compiled.ExitCode -ne 0) {
        throw "Assembly contract compilation failed, exit $($compiled.ExitCode)."
    }

    $tested = Invoke-VerifierBoundedProcess $java @('-ea', '-cp', $classes,
        'com.lushprojects.circuitjs1.client.BoundedAssemblyContractTest') 60000
    Write-Host $tested.Stdout
    if ($tested.Stderr) { Write-Host $tested.Stderr }
    if (-not $tested.TerminationProven) {
        throw 'Assembly contract test termination was not proven.'
    }
    if ($tested.ExitCode -ne 0 -or
            $tested.Stdout -notmatch '(?m)^TASK47_ASSEMBLY_RECEIPT_BEGIN\r?$' -or
            $tested.Stdout -notmatch '(?m)^TASK47_ASSEMBLY_RECEIPT_END\r?$' -or
            $tested.Stdout -notmatch '(?m)^PASS: Task47 pure assembly contracts ') {
        throw "Assembly contract test did not provide a qualified result, exit $($tested.ExitCode)."
    }
    $receipt = [regex]::Match($tested.Stdout,
        '(?m)^TASK47_ASSEMBLY_RECEIPT_BEGIN\r?\n([\s\S]*?)^TASK47_ASSEMBLY_RECEIPT_END\r?$')
    if (-not $receipt.Success) { throw 'Task 47 assembly receipt body is missing.' }
    $receiptPath = Join-Path $taskRoot 'assembly-receipt.txt'
    [IO.File]::WriteAllText($receiptPath, $receipt.Groups[1].Value,
        (New-Object Text.UTF8Encoding($false)))
    $assemblyReferencePath = Join-Path $repositoryRoot 'tests/contracts/task47_assembly_reference.py'
    $assemblyOracle = Invoke-VerifierBoundedProcess $python @(
        $assemblyReferencePath, $receiptPath) 60000
    Write-Host $assemblyOracle.Stdout
    if ($assemblyOracle.Stderr) { Write-Host $assemblyOracle.Stderr }
    if (-not $assemblyOracle.TerminationProven -or $assemblyOracle.ExitCode -ne 0 -or
            $assemblyOracle.Stdout -notmatch
            '(?m)^PASS: Task47 independent assembly oracle 8 seeds') {
        throw "Independent Task 47 assembly oracle failed, exit $($assemblyOracle.ExitCode)."
    }
    if ($ReceiptOutputPath) {
        $destination = [IO.Path]::GetFullPath($ReceiptOutputPath)
        [IO.File]::WriteAllText($destination, $receipt.Groups[1].Value,
            (New-Object Text.UTF8Encoding($false)))
        Write-Host ('RECEIPT: ' + $destination)
    }
    $resultCode = 0
} catch {
    Write-Host ('ASSEMBLY_CONTRACT_INFRASTRUCTURE: ' + $_.Exception.Message)
    $resultCode = 2
} finally {
    if ($taskRoot -and (Test-Path -LiteralPath $taskRoot)) {
        try {
            Remove-VerifierOwnedTree ([IO.Path]::GetFullPath([IO.Path]::GetTempPath())) $taskRoot
            Write-Host 'CLEANUP: assembly contract classes and task-owned scratch removed.'
        } catch {
            Write-Host ('ASSEMBLY_CONTRACT_CLEANUP: ' + $_.Exception.Message)
            $resultCode = 2
        }
    }
}
exit $resultCode

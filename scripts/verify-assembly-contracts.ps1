[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$PythonExe = 'python',
    [string]$ReceiptOutputPath = '',
    [string]$ControlledReceiptOutputPath = '',
    [string]$SynthesizedReceiptOutputPath = ''
)

# Focused, nonvisual Task 47/48/49 request/provider/plan checks. This harness
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
        'src/com/lushprojects/circuitjs1/client/BlockRealizationIdentity.java',
        'src/com/lushprojects/circuitjs1/client/DeviceBusBindings.java',
        'src/com/lushprojects/circuitjs1/client/ElectricalContractException.java',
        'src/com/lushprojects/circuitjs1/client/ElectricalPortContract.java',
        'src/com/lushprojects/circuitjs1/client/ElectricalBlockContract.java',
        'src/com/lushprojects/circuitjs1/client/ElectricalConnection.java',
        'src/com/lushprojects/circuitjs1/client/SwitchedLowSideContract.java',
        'src/com/lushprojects/circuitjs1/client/PortCompatibilityPreflight.java',
        'src/com/lushprojects/circuitjs1/client/PcbGeometryContractVersion.java',
        'src/com/lushprojects/circuitjs1/client/ChallengeContractException.java',
        'src/com/lushprojects/circuitjs1/client/GenerationConstraints.java',
        'src/com/lushprojects/circuitjs1/client/ChallengeDescriptor.java',
        'src/com/lushprojects/circuitjs1/client/NamedRandomStreams.java',
        'src/com/lushprojects/circuitjs1/client/GeneratedFaultLocusType.java',
        'src/com/lushprojects/circuitjs1/client/GeneratedFaultLocus.java',
        'src/com/lushprojects/circuitjs1/client/GeneratedDiagnosticPlan.java',
        'src/com/lushprojects/circuitjs1/client/PhysicalSpecification.java',
        'src/com/lushprojects/circuitjs1/client/PhysicalRating.java',
        'src/com/lushprojects/circuitjs1/client/PowerRating.java',
        'src/com/lushprojects/circuitjs1/client/PhysicalNameplate.java',
        'src/com/lushprojects/circuitjs1/client/PhysicalPartOrientation.java',
        'src/com/lushprojects/circuitjs1/client/PhysicalCatalogEntry.java',
        'src/com/lushprojects/circuitjs1/client/AbstractPhysicalCatalogEntry.java',
        'src/com/lushprojects/circuitjs1/client/PhysicalPartCatalog.java',
        'src/com/lushprojects/circuitjs1/client/ResistorNameplate.java',
        'src/com/lushprojects/circuitjs1/client/ResistorCatalogEntry.java',
        'src/com/lushprojects/circuitjs1/client/ResistorReplacementCatalog.java',
        'src/com/lushprojects/circuitjs1/client/ComposedBlockContribution.java',
        'src/com/lushprojects/circuitjs1/client/ControlledIndicatorValueSynthesis.java',
        'src/com/lushprojects/circuitjs1/client/ResistiveBlockContributions.java',
        'src/com/lushprojects/circuitjs1/client/DeviceAdapterContract.java',
        'src/com/lushprojects/circuitjs1/client/ControlledIndicatorBlockContributions.java',
        'src/com/lushprojects/circuitjs1/client/BoundedAssemblyRequest.java',
        'src/com/lushprojects/circuitjs1/client/BoundedAssemblyPlan.java',
        'tests/contracts/BoundedAssemblyContractTest.java',
        'tests/contracts/ControlledIndicatorAssemblyContractTest.java',
        'tests/contracts/SwitchedLowSideCompatibilityContractTest.java',
        'tests/contracts/Task49ValueSynthesisContractTest.java'
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
    $controlledTested = Invoke-VerifierBoundedProcess $java @('-ea', '-cp', $classes,
        'com.lushprojects.circuitjs1.client.ControlledIndicatorAssemblyContractTest') 60000
    Write-Host $controlledTested.Stdout
    if ($controlledTested.Stderr) { Write-Host $controlledTested.Stderr }
    if (-not $controlledTested.TerminationProven -or $controlledTested.ExitCode -ne 0 -or
            $controlledTested.Stdout -notmatch
            '(?m)^PASS: Task48 pure controlled-indicator contracts ') {
        throw "Task 48 pure controlled-indicator contract test failed, exit $($controlledTested.ExitCode)."
    }
    $switchedTested = Invoke-VerifierBoundedProcess $java @('-ea', '-cp', $classes,
        'com.lushprojects.circuitjs1.client.SwitchedLowSideCompatibilityContractTest') 60000
    Write-Host $switchedTested.Stdout
    if ($switchedTested.Stderr) { Write-Host $switchedTested.Stderr }
    if (-not $switchedTested.TerminationProven -or $switchedTested.ExitCode -ne 0 -or
            $switchedTested.Stdout -notmatch '(?m)^PASS: Task48 switched-low-side compatibility ') {
        throw "Task 48 switched-low-side compatibility test failed, exit $($switchedTested.ExitCode)."
    }
    $controlledReceipt = [regex]::Match($controlledTested.Stdout,
        '(?m)^TASK48_ASSEMBLY_RECEIPT_BEGIN\r?\n([\s\S]*?)^TASK48_ASSEMBLY_RECEIPT_END\r?$')
    if (-not $controlledReceipt.Success) { throw 'Task 48 assembly receipt body is missing.' }
    $synthesizedTested = Invoke-VerifierBoundedProcess $java @('-ea', '-cp', $classes,
        'com.lushprojects.circuitjs1.client.Task49ValueSynthesisContractTest') 60000
    Write-Host $synthesizedTested.Stdout
    if ($synthesizedTested.Stderr) { Write-Host $synthesizedTested.Stderr }
    if (-not $synthesizedTested.TerminationProven -or $synthesizedTested.ExitCode -ne 0 -or
            $synthesizedTested.Stdout -notmatch '(?m)^PASS: Task49 pure value synthesis ') {
        throw "Task 49 pure value synthesis test failed, exit $($synthesizedTested.ExitCode)."
    }
    $synthesizedReceipt = [regex]::Match($synthesizedTested.Stdout,
        '(?m)^TASK49_SYNTHESIZED_RECEIPT_BEGIN\r?\n([\s\S]*?)^TASK49_SYNTHESIZED_RECEIPT_END\r?$')
    if (-not $synthesizedReceipt.Success) { throw 'Task 49 synthesized receipt body is missing.' }
    $synthesizedReceiptPath = Join-Path $taskRoot 'synthesized-value-receipt.txt'
    [IO.File]::WriteAllText($synthesizedReceiptPath, $synthesizedReceipt.Groups[1].Value,
        (New-Object Text.UTF8Encoding($false)))
    $synthesizedReferencePath = Join-Path $repositoryRoot 'tests/contracts/task49_value_synthesis_reference.py'
    # The Task 49 reference imports Task 46; -B keeps that independent
    # oracle from creating a repository __pycache__ during the gate.
    $synthesizedOracle = Invoke-VerifierBoundedProcess $python @(
        '-B', $synthesizedReferencePath, $synthesizedReceiptPath) 60000
    Write-Host $synthesizedOracle.Stdout
    if ($synthesizedOracle.Stderr) { Write-Host $synthesizedOracle.Stderr }
    if (-not $synthesizedOracle.TerminationProven -or $synthesizedOracle.ExitCode -ne 0 -or
            $synthesizedOracle.Stdout -notmatch
            '(?m)^PASS: Task49 independent value synthesis oracle 8 seeds') {
        throw "Independent Task 49 value synthesis oracle failed, exit $($synthesizedOracle.ExitCode)."
    }
    $controlledReceiptPath = Join-Path $taskRoot 'controlled-assembly-receipt.txt'
    [IO.File]::WriteAllText($controlledReceiptPath, $controlledReceipt.Groups[1].Value,
        (New-Object Text.UTF8Encoding($false)))
    $controlledReferencePath = Join-Path $repositoryRoot 'tests/contracts/task48_assembly_reference.py'
    $controlledOracle = Invoke-VerifierBoundedProcess $python @(
        $controlledReferencePath, $controlledReceiptPath) 60000
    Write-Host $controlledOracle.Stdout
    if ($controlledOracle.Stderr) { Write-Host $controlledOracle.Stderr }
    if (-not $controlledOracle.TerminationProven -or $controlledOracle.ExitCode -ne 0 -or
            $controlledOracle.Stdout -notmatch '(?m)^PASS: Task48 independent assembly oracle 8 seeds') {
        throw "Independent Task 48 assembly oracle failed, exit $($controlledOracle.ExitCode)."
    }
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
    if ($ControlledReceiptOutputPath) {
        $controlledDestination = [IO.Path]::GetFullPath($ControlledReceiptOutputPath)
        [IO.File]::WriteAllText($controlledDestination, $controlledReceipt.Groups[1].Value,
            (New-Object Text.UTF8Encoding($false)))
        Write-Host ('CONTROLLED_RECEIPT: ' + $controlledDestination)
    }
    if ($SynthesizedReceiptOutputPath) {
        $synthesizedDestination = [IO.Path]::GetFullPath($SynthesizedReceiptOutputPath)
        [IO.File]::WriteAllText($synthesizedDestination, $synthesizedReceipt.Groups[1].Value,
            (New-Object Text.UTF8Encoding($false)))
        Write-Host ('SYNTHESIZED_RECEIPT: ' + $synthesizedDestination)
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

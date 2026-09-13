[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$PythonExe = 'python',
    [string]$ReceiptOutputPath = ''
)

# Maintained current seed, identity, geometry, recipe and construction contracts.
# Compiles the real client source once; the actual GWT solver/player gates remain separate.
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
    $javaVersion = Invoke-VerifierBoundedProcess $java @('-version') 15000
    $javacVersion = Invoke-VerifierBoundedProcess $javac @('-version') 15000
    if ($javaVersion.ExitCode -ne 0 -or -not $javaVersion.TerminationProven -or
            ($javaVersion.Stdout + $javaVersion.Stderr) -notmatch 'version\s+"1\.8\.' -or
            $javacVersion.ExitCode -ne 0 -or -not $javacVersion.TerminationProven -or
            ($javacVersion.Stdout + $javacVersion.Stderr) -notmatch 'javac\s+1\.8\.') {
        throw 'The selected toolchain is not a verified JDK 8 compiler/runtime.'
    }
    Write-Host ('JDK: ' + ($javacVersion.Stdout + $javacVersion.Stderr).Trim())

    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $taskRoot = Join-Path $tempRoot ('TroubleshootJS-current-contracts-' +
        [Guid]::NewGuid().ToString('N'))
    Assert-VerifierNoReparseAncestors $taskRoot
    if (Test-Path -LiteralPath $taskRoot) { throw 'Current-contract scratch already exists.' }
    New-Item -ItemType Directory -Path $taskRoot | Out-Null
    $classes = Join-Path $taskRoot 'classes'
    $emptySourcePath = Join-Path $taskRoot 'empty-sourcepath'
    New-Item -ItemType Directory -Path $classes, $emptySourcePath | Out-Null
    $gwtJars = @('gwt-user-2.7.0.jar', 'gwt-dev-2.7.0.jar') | ForEach-Object {
        $jar = Join-Path $repositoryRoot ('.tools/gwt-2.7.0/' + $_)
        if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
            throw ('Missing repository GWT dependency: ' + $_)
        }
        $jar
    }
    $classPath = [String]::Join(';', @($classes) + @($gwtJars))

    # The accepted Task35 developer verifier contains a generic reference
    # comparison accepted by GWT but rejected by javac8. Exclude only that
    # unrelated verifier, with a fail-closed replacement: invoking it fails.
    # All current production classes are compiled byte-for-byte from the checkout.
    $stub = Join-Path $taskRoot 'PhysicalSpecificationDeveloperVerifier.java'
    [IO.File]::WriteAllText($stub, @'
package com.lushprojects.circuitjs1.client;
final class PhysicalSpecificationDeveloperVerifier {
    static void verify(CirSim sim) {
        throw new UnsupportedOperationException(
            "Task35 verifier requires the separate GWT production gate");
    }
}
'@, (New-Object Text.UTF8Encoding($false)))
    $clientSource = Join-Path $repositoryRoot 'src/com/lushprojects/circuitjs1/client'
    $sourcePaths = @(Get-ChildItem -LiteralPath $clientSource -Filter '*.java' |
        Where-Object { $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' } |
        Sort-Object Name | ForEach-Object { $_.FullName })
    $sourcePaths += $stub
    $testDefinitions = @(
        @{ Name = 'E03RelayContractTest'; Marker = 'E03 relay contracts ' },
        @{ Name = 'ChallengeDescriptorContractTest'; Marker = 'Task46 ' },
        @{ Name = 'FunctionalBlockContractTest'; Marker = 'Task44 ' },
        @{ Name = 'ElectricalPortContractTest'; Marker = 'Task45 ' },
        @{ Name = 'BoundedAssemblyContractTest'; Marker = 'current resistive assembly contracts ' },
        @{ Name = 'ControlledIndicatorAssemblyContractTest'; Marker = 'current controlled-indicator contracts ' },
        @{ Name = 'SwitchedLowSideCompatibilityContractTest'; Marker = 'current switched-low-side compatibility ' },
        @{ Name = 'Task49ValueSynthesisContractTest'; Marker = 'current value synthesis ' },
        @{ Name = 'A05RoleContractTest'; Marker = 'A05 role providers ' },
        @{ Name = 'E01SourceContractTest'; Marker = 'E01 source contracts ' },
        @{ Name = 'A06PowerContractTest'; Marker = 'A06 power contracts ' },
        @{ Name = 'A07ExecutionContractTest'; Marker = 'A07 execution contracts ' },
        @{ Name = 'A09DiagnosticContractTest'; Marker = 'A09 diagnostic contracts ' },
        @{ Name = 'A10GenerationContractTest'; Marker = 'A10 generation contracts ' },
        @{ Name = 'A10RoutingContractTest'; Marker = 'A10 routing rejection contracts ' },
        @{ Name = 'A10DependencyContractTest'; Marker = 'A10 dependency contracts ' },
        @{ Name = 'A11ProviderConformanceTest'; Marker = 'A11 provider conformance' },
        @{ Name = 'A03IdentityContractTest'; Marker = 'A03IdentityContractTest ' },
        @{ Name = 'A02CandidateContractTest'; Marker = 'A02CandidateContractTest' },
        @{ Name = 'A02GeometryContractTest'; Marker = 'A02GeometryContractTest ' },
        @{ Name = 'P01PhysicalPoseContractTest'; Marker = 'P01 physical pose contracts ' },
        @{ Name = 'P02ConductorContractTest'; Marker = 'P02 conductor contracts ' },
        @{ Name = 'U01ViewportContractTest'; Marker = 'U01 viewport contracts ' },
        @{ Name = 'P03PlacementContractTest'; Marker = 'P03 placement contracts ' },
        @{ Name = 'P04RoutingContractTest'; Marker = 'P04 routing contracts ' },
        @{ Name = 'A02ReplayContractTest'; Marker = 'A02ReplayContractTest ' },
        @{ Name = 'ElectricalUnitPackageMapContractTest'; Marker = 'ElectricalUnitPackageMapContractTest ' },
        @{ Name = 'A04ConstructionContractTest'; Marker = 'A04ConstructionContractTest ' },
        @{ Name = 'A04PhysicalDeclarationContractTest'; Marker = 'A04PhysicalDeclarationContractTest ' })
    $testClasses = @($testDefinitions | ForEach-Object { $_.Name })
    foreach ($testClass in $testClasses) {
        $testSource = Join-Path $repositoryRoot ('tests/contracts/' + $testClass + '.java')
        if (-not (Test-Path -LiteralPath $testSource -PathType Leaf)) {
            throw ('Missing current contract: ' + $testClass)
        }
        $sourcePaths += $testSource
    }
    $argumentsFile = Join-Path $taskRoot 'sources.txt'
    $sourcePaths += (Join-Path $repositoryRoot 'tests/contracts/P03StructuralFixtures.java')
    [IO.File]::WriteAllLines($argumentsFile, @($sourcePaths | ForEach-Object {
        '"' + $_.Replace('\', '/') + '"'
    }), (New-Object Text.UTF8Encoding($false)))
    $compiled = Invoke-VerifierBoundedProcess $javac @('-source', '7', '-target', '7',
        '-encoding', 'UTF-8', '-classpath', $classPath, '-sourcepath', $emptySourcePath,
        '-d', $classes, ('@' + $argumentsFile)) 60000
    Write-Host $compiled.Stdout
    if ($compiled.Stderr) { Write-Host $compiled.Stderr }
    if (-not $compiled.TerminationProven -or $compiled.ExitCode -ne 0) {
        throw ('Current production-class compilation failed, exit ' + $compiled.ExitCode)
    }
    $receipts = New-Object Collections.Generic.List[string]
    $outputs = @{}
    foreach ($definition in $testDefinitions) {
        $testClass = $definition.Name
        $testArguments = @('-ea', '-cp', $classPath,
            ('com.lushprojects.circuitjs1.client.' + $testClass))
        if ($testClass -eq 'A03IdentityContractTest') {
            $testArguments += (Join-Path $taskRoot 'parity')
        }
        $tested = Invoke-VerifierBoundedProcess $java $testArguments 60000
        Write-Host $tested.Stdout
        if ($tested.Stderr) { Write-Host $tested.Stderr }
        $receipts.Add($tested.Stdout)
        $outputs[$testClass] = $tested.Stdout
        if (-not $tested.TerminationProven -or $tested.ExitCode -ne 0 -or
                $tested.Stdout -notmatch ('(?m)^PASS: ' + [regex]::Escape($definition.Marker))) {
            throw ('Current contract did not provide a qualified result: ' + $testClass +
                ', exit ' + $tested.ExitCode)
        }
    }
    $python = (Get-Command $PythonExe -ErrorAction Stop).Source
    if (-not $python -or -not (Test-Path -LiteralPath $python -PathType Leaf)) {
        throw 'Select an available Python executable with -PythonExe.'
    }
    $valueMatch = [regex]::Match($outputs['Task49ValueSynthesisContractTest'],
        '(?m)^CURRENT_VALUE_RECEIPT_BEGIN\r?\n([\s\S]*?)^CURRENT_VALUE_RECEIPT_END\r?$')
    $seedMatch = [regex]::Match($outputs['ChallengeDescriptorContractTest'],
        '(?m)^TASK46_PARITY_BEGIN\r?\n([\s\S]*?)^TASK46_PARITY_END\r?$')
    if (-not $valueMatch.Success -or -not $seedMatch.Success) {
        throw 'A current seed/value corpus is missing its complete frame.'
    }
    $valuePath = Join-Path $taskRoot 'current-values.txt'
    [IO.File]::WriteAllText($valuePath, $valueMatch.Groups[1].Value,
        (New-Object Text.UTF8Encoding($false)))
    $rolesPath = Join-Path $taskRoot 'a05-roles.txt'
    [IO.File]::WriteAllText($rolesPath, $outputs['A05RoleContractTest'],
        (New-Object Text.UTF8Encoding($false)))
    $providerMatch = [regex]::Match($outputs['A11ProviderConformanceTest'],
        '(?m)^A11_PROVIDER_REPORT (.+)\r?$')
    if (-not $providerMatch.Success) { throw 'Missing actual A11 declaration report.' }
    $providerPath = Join-Path $taskRoot 'a11-providers.json'
    [IO.File]::WriteAllText($providerPath, $providerMatch.Groups[1].Value.Trim(),
        (New-Object Text.UTF8Encoding($false)))
    $oracles = @(
        @{ File = 'task46_seed_reference.py'; Arguments = @(); Marker = 'Task46 independent seed oracle ' },
        @{ File = 'a11_provider_contract.py'; Arguments = @('--report', $providerPath); Marker = 'A11 provider boundaries/report ' },
        @{ File = 'a09_diagnostic_contract.py'; Arguments = @(); Marker = 'A09 independent diagnostic contracts ' },
        @{ File = 'task49_value_synthesis_reference.py'; Arguments = @($valuePath, $rolesPath); Marker = 'current value synthesis oracle ' })
    foreach ($oracle in $oracles) {
        $oracleArguments = @((Join-Path $repositoryRoot ('tests/contracts/' + $oracle.File))) + $oracle.Arguments
        $checked = Invoke-VerifierBoundedProcess $python $oracleArguments 60000
        Write-Host $checked.Stdout
        if ($checked.Stderr) { Write-Host $checked.Stderr }
        if (-not $checked.TerminationProven -or $checked.ExitCode -ne 0 -or
                $checked.Stdout -notmatch ('(?m)^PASS: ' + [regex]::Escape($oracle.Marker))) {
            throw ('Independent current oracle failed: ' + $oracle.File + ', exit ' + $checked.ExitCode)
        }
        $receipts.Add($checked.Stdout)
    }
    $powershell = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
    $protocol = Invoke-VerifierBoundedProcess $powershell @('-NoProfile', '-ExecutionPolicy',
        'Bypass', '-File', (Join-Path $repositoryRoot 'tests/contracts/a04-report-contract.ps1')) 60000
    Write-Host $protocol.Stdout
    if ($protocol.Stderr) { Write-Host $protocol.Stderr }
    if (-not $protocol.TerminationProven -or $protocol.ExitCode -ne 0 -or
            $protocol.Stdout -notmatch '(?m)^PASS: A04 report contracts assertions=') {
        throw ('Current browser report protocol failed, exit ' + $protocol.ExitCode)
    }
    $receipts.Add($protocol.Stdout)
    if ($ReceiptOutputPath) {
        foreach ($parityName in @('vectors', 'manifest-resistive', 'manifest-controlled', 'manifest-controlled-alt')) {
            $paritySource = Join-Path $taskRoot ('parity.' + $parityName + '.txt')
            if (-not (Test-Path -LiteralPath $paritySource -PathType Leaf)) {
                throw ('Missing exact JVM parity artifact: ' + $parityName)
            }
            [IO.File]::WriteAllBytes([IO.Path]::GetFullPath(
                    $ReceiptOutputPath + '.' + $parityName + '.txt'),
                [IO.File]::ReadAllBytes($paritySource))
        }
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptOutputPath + '.seeds.txt'),
            $seedMatch.Groups[1].Value, (New-Object Text.UTF8Encoding($false)))
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptOutputPath + '.roles.txt'),
            $outputs['A05RoleContractTest'], (New-Object Text.UTF8Encoding($false)))
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptOutputPath + '.values.txt'),
            $valueMatch.Groups[1].Value, (New-Object Text.UTF8Encoding($false)))
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptOutputPath),
            [String]::Join([Environment]::NewLine, $receipts),
            (New-Object Text.UTF8Encoding($false)))
    }
    Write-Host ('PASS: current contracts; ' + $testDefinitions.Count + ' Java suites, independent seed/value/role oracles and report protocol.')
    $resultCode = 0
} catch {
    Write-Host ('CURRENT_CONTRACT_FAILURE: ' + $_.Exception.Message)
    $resultCode = 2
} finally {
    if ($taskRoot -and (Test-Path -LiteralPath $taskRoot)) {
        try {
            Remove-VerifierOwnedTree ([IO.Path]::GetFullPath([IO.Path]::GetTempPath())) $taskRoot
            Write-Host 'CLEANUP: current JVM contract classes and task-owned scratch removed.'
        } catch {
            Write-Host ('CURRENT_CONTRACT_CLEANUP: ' + $_.Exception.Message)
            $resultCode = 2
        }
    }
}
exit $resultCode

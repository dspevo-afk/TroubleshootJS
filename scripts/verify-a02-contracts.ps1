[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateSet('All', 'Candidate', 'Geometry', 'Replay')]
    [string]$Contract = 'All',
    [string]$ReceiptOutputPath = ''
)

# Executes the real client classes on JDK8 for bounded A02 helper contracts.
# This is not a JVM solver substitute or production/browser qualification.
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
    $taskRoot = Join-Path $tempRoot ('TroubleshootJS-a02-contracts-' +
        [Guid]::NewGuid().ToString('N'))
    Assert-VerifierNoReparseAncestors $taskRoot
    if (Test-Path -LiteralPath $taskRoot) { throw 'A02 scratch already exists.' }
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
    # All A02 production classes are compiled byte-for-byte from the checkout.
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
    $testClasses = @()
    if ($Contract -eq 'All' -or $Contract -eq 'Candidate') {
        $testClasses += 'A02CandidateContractTest'
    }
    if ($Contract -eq 'All' -or $Contract -eq 'Geometry') {
        $testClasses += 'A02GeometryContractTest'
    }
    if ($Contract -eq 'All' -or $Contract -eq 'Replay') {
        $testClasses += 'A02ReplayContractTest'
    }
    foreach ($testClass in $testClasses) {
        $testSource = Join-Path $repositoryRoot ('tests/contracts/' + $testClass + '.java')
        if (-not (Test-Path -LiteralPath $testSource -PathType Leaf)) {
            throw ('Missing A02 contract: ' + $testClass)
        }
        $sourcePaths += $testSource
    }
    $argumentsFile = Join-Path $taskRoot 'sources.txt'
    [IO.File]::WriteAllLines($argumentsFile, @($sourcePaths | ForEach-Object {
        '"' + $_.Replace('\', '/') + '"'
    }), (New-Object Text.UTF8Encoding($false)))
    $compiled = Invoke-VerifierBoundedProcess $javac @('-source', '7', '-target', '7',
        '-encoding', 'UTF-8', '-classpath', $classPath, '-sourcepath', $emptySourcePath,
        '-d', $classes, ('@' + $argumentsFile)) 60000
    Write-Host $compiled.Stdout
    if ($compiled.Stderr) { Write-Host $compiled.Stderr }
    if (-not $compiled.TerminationProven -or $compiled.ExitCode -ne 0) {
        throw ('A02 production-class compilation failed, exit ' + $compiled.ExitCode)
    }
    $receipts = New-Object Collections.Generic.List[string]
    foreach ($testClass in $testClasses) {
        $tested = Invoke-VerifierBoundedProcess $java @('-ea', '-cp', $classPath,
            ('com.lushprojects.circuitjs1.client.' + $testClass)) 60000
        Write-Host $tested.Stdout
        if ($tested.Stderr) { Write-Host $tested.Stderr }
        $receipts.Add($tested.Stdout)
        if (-not $tested.TerminationProven -or $tested.ExitCode -ne 0 -or
                $tested.Stdout -notmatch ('(?m)^PASS: ' + $testClass + '\b')) {
            throw ('A02 contract did not provide a qualified result: ' + $testClass +
                ', exit ' + $tested.ExitCode)
        }
    }
    if ($ReceiptOutputPath) {
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptOutputPath),
            [String]::Join([Environment]::NewLine, $receipts),
            (New-Object Text.UTF8Encoding($false)))
    }
    $resultCode = 0
} catch {
    Write-Host ('A02_CONTRACT_FAILURE: ' + $_.Exception.Message)
    $resultCode = 2
} finally {
    if ($taskRoot -and (Test-Path -LiteralPath $taskRoot)) {
        try {
            Remove-VerifierOwnedTree ([IO.Path]::GetFullPath([IO.Path]::GetTempPath())) $taskRoot
            Write-Host 'CLEANUP: A02 JVM contract classes and scratch removed.'
        } catch {
            Write-Host ('A02_CONTRACT_CLEANUP: ' + $_.Exception.Message)
            $resultCode = 2
        }
    }
}
exit $resultCode

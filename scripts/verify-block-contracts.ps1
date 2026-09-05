[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME
)

# Focused, nonvisual Java contract checks. Reuse the established bounded process
# and owned-temp cleanup helpers; this script does not start a simulator.
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

    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $taskRoot = Join-Path $tempRoot ('TroubleshootJS-block-contracts-' + [Guid]::NewGuid().ToString('N'))
    Assert-VerifierNoReparseAncestors $taskRoot
    if (Test-Path -LiteralPath $taskRoot) { throw 'Contract output directory already exists.' }
    New-Item -ItemType Directory -Path $taskRoot | Out-Null
    $classes = Join-Path $taskRoot 'classes'
    $emptySourcePath = Join-Path $taskRoot 'empty-sourcepath'
    New-Item -ItemType Directory -Path $classes, $emptySourcePath | Out-Null

    # Explicit sources ensure new, not-yet-runtime-reachable code is compiled.
    # The empty source/class paths prevent an accidental solver dependency from
    # silently pulling the whole application into this pure-contract test.
    $sourceFiles = @(
        'src/com/lushprojects/circuitjs1/client/BlockContractException.java',
        'src/com/lushprojects/circuitjs1/client/FunctionalBlockDescriptor.java',
        'src/com/lushprojects/circuitjs1/client/BlockNamespace.java',
        'src/com/lushprojects/circuitjs1/client/FunctionalBlockExamples.java',
        'tests/contracts/FunctionalBlockContractTest.java'
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
        throw "Contract compilation failed, exit $($compiled.ExitCode)."
    }
    $tested = Invoke-VerifierBoundedProcess $java @('-ea', '-cp', $classes,
        'com.lushprojects.circuitjs1.client.FunctionalBlockContractTest') 60000
    Write-Host $tested.Stdout
    if ($tested.Stderr) { Write-Host $tested.Stderr }
    if (-not $tested.TerminationProven) { throw 'Contract test termination was not proven.' }
    if ($tested.ExitCode -eq 0 -and $tested.Stdout -match '(?m)^PASS: Task44 ') {
        $resultCode = 0
    } elseif ($tested.ExitCode -eq 1) {
        $resultCode = 1
    } else {
        throw "Contract tests did not provide a qualified result, exit $($tested.ExitCode)."
    }
} catch {
    Write-Host ('BLOCK_CONTRACT_INFRASTRUCTURE: ' + $_.Exception.Message)
    $resultCode = 2
} finally {
    if ($taskRoot -and (Test-Path -LiteralPath $taskRoot)) {
        try {
            Remove-VerifierOwnedTree ([IO.Path]::GetFullPath([IO.Path]::GetTempPath())) $taskRoot
            Write-Host 'CLEANUP: contract classes and task-owned scratch removed.'
        } catch {
            Write-Host ('BLOCK_CONTRACT_CLEANUP: ' + $_.Exception.Message)
            $resultCode = 2
        }
    }
}
exit $resultCode

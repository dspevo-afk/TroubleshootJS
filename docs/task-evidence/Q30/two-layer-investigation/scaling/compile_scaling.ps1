param(
    [Parameter(Mandatory=$true)][string]$SourceRoot,
    [string]$JavaHome = $env:JDK8_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Supply JDK8_HOME or -JavaHome.' }
$root = (Resolve-Path -LiteralPath $SourceRoot).Path
$java = Join-Path $JavaHome 'bin\java.exe'
$javac = Join-Path $JavaHome 'bin\javac.exe'
& $java -version
& $javac -version
$out = Join-Path $root 'scale-classes'
$empty = Join-Path $root 'scale-empty-sourcepath'
New-Item -ItemType Directory -Path $out,$empty -Force | Out-Null
$cp = "$out;$(Join-Path $root '.tools\gwt-2.7.0\gwt-user-2.7.0.jar');$(Join-Path $root '.tools\gwt-2.7.0\gwt-dev-2.7.0.jar')"
$stub = Join-Path $root 'scale-stub\PhysicalSpecificationDeveloperVerifier.java'
New-Item -ItemType Directory -Path (Split-Path -Parent $stub) -Force | Out-Null
@'
package com.lushprojects.circuitjs1.client;
final class PhysicalSpecificationDeveloperVerifier {
  static void verify(CirSim sim) { throw new UnsupportedOperationException("native-only scaling pilot"); }
}
'@ | Set-Content -Path $stub
$src = @(Get-ChildItem (Join-Path $root 'src\com\lushprojects\circuitjs1\client') -Filter '*.java' |
    Where-Object Name -ne 'PhysicalSpecificationDeveloperVerifier.java' | ForEach-Object FullName)
$src += $stub
$src += (Join-Path $root 'tests\contracts\P03StructuralFixtures.java')
$src += (Join-Path $PSScriptRoot 'Q30TwoLayerScaling.java')
$sources = Join-Path $root 'scale-sources.txt'
$src | ForEach-Object { '"' + $_.Replace('\','/') + '"' } | Set-Content -Path $sources
& $javac -source 7 -target 7 -encoding UTF-8 -classpath $cp -sourcepath $empty -d $out ('@' + $sources)
if ($LASTEXITCODE -ne 0) { throw "javac failed: $LASTEXITCODE" }
Write-Output 'PASS: accepted-source native scaling harness compiled; production GWT build NOT RUN'

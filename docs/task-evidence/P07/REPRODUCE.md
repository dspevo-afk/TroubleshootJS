# Reproduce P07

Use Windows, JDK 8, Python with Playwright, and installed Microsoft Edge. Start
from the desired published P07 revision. The archive below excludes unrelated
uncommitted visual work. Raw output stays in a fresh OS-temporary namespace.

```powershell
# Run from the repository root at the P07 revision being checked.
$repo = (Get-Location).Path
$jdk = Join-Path $repo '.tools\jdk8-download\jdk8u502-b07'
# Point $jdk to another installed JDK 8 when necessary.
$out = Join-Path $env:TEMP ('TroubleshootJS-P07-reproduce-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $out | Out-Null
git archive --format=zip --output="$out\source.zip" HEAD
if ($LASTEXITCODE -ne 0) { throw 'Source archive failed' }
Expand-Archive -LiteralPath "$out\source.zip" -DestinationPath "$out\source"
$clean = Join-Path $out 'source'
New-Item -ItemType Directory -Force -Path "$clean\.tools\gwt-2.7.0" | Out-Null
Copy-Item "$repo\.tools\gwt-2.7.0\*.jar" "$clean\.tools\gwt-2.7.0"
Set-Location $clean
[Environment]::CurrentDirectory = $clean
& .\scripts\verify-current-contracts.ps1 -JavaHome $jdk -ReceiptOutputPath "$out\native.txt"
if ($LASTEXITCODE -ne 0) { throw 'Native qualification failed' }
& .\scripts\build.ps1 -JavaHome $jdk
if ($LASTEXITCODE -ne 0) { throw 'GWT qualification failed' }
foreach ($mode in @('p07','negative','bench','layout','q15','q15negative')) {
    python .\docs\task-evidence\P07\verify_browser.py $clean "$out\$mode" $mode
    if ($LASTEXITCODE -ne 0) { throw "Browser qualification failed: $mode" }
}
foreach ($mode in @('p06','negative')) {
    python .\docs\task-evidence\P06\verify_browser.py $clean "$out\p06-$mode" $mode
    if ($LASTEXITCODE -ne 0) { throw "P06 regression failed: $mode" }
}
python .\docs\task-evidence\P07\verify_player.py $clean "$out\player" player
if ($LASTEXITCODE -ne 0) { throw 'Normal player / no-debug P07 guard failed' }
Write-Host "Raw evidence: $out"
```

The browser driver serves the just-compiled `war` on an owned loopback listener,
uses a fresh Edge profile and records actual cache artifacts. `bench` clicks the
meter, pads, via lands, underside trace and face controls; it does not inject
measurements. `negative` and `q15negative` must observe the explicit application
FAIL verdict and successful owner restoration; the enclosing test then passes.
The player driver uses normal menu/family/seed/ticket/navigation inputs while
P07 flags are present without `tsjDebug`, and requires those flags to stay inert.

For the preserved working combination, run the same build and P07/browser/player
checks on that tree and the focused native suites listed in `acceptance.json`.
Do not replace an existing profile, build output or another session's evidence
namespace. Keep failure logs; never turn an infrastructure failure into PASS by
loosening the existing ownership or verifier deadlines.

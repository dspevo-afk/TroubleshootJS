# TroubleshootJS development

TroubleshootJS currently builds the existing CircuitJS fork without changing its
Eclipse-era source layout. The command-line wrapper invokes the same GWT module
and compiler version recorded in the project metadata:

- GWT module: `com.lushprojects.circuitjs1.circuitjs1`
- GWT compiler: 2.7.0
- Java runtime: JDK 8
- Web application root: `war`
- Generated module name: `circuitjs1`

The original Eclipse workflow remains usable, but Eclipse is not required for
the commands below.

## Normal player launch

From any current working directory, double-click the root:

```text
Start TroubleshootJS.cmd
```

This starts or reuses the detached production preview, builds once with an
available JDK 8 only when the compiled bootstrap is missing, and opens the
default browser directly into Quick Play. The server stays running after the
launcher exits. A startup failure remains visible in the command window and
identifies the required build, JDK 8, or preview problem.

The player launcher is intentionally separate from the legacy `DevMode`
development path and from explicit developer challenge/verifier routes.

## Required software

- Windows PowerShell 5.1 or PowerShell 7+
- A 64-bit JDK 8 installation (a JRE alone is not sufficient)
- Internet access for the first build so the pinned GWT compiler jars can be
  downloaded from Maven Central

Eclipse Temurin 8 is the recommended JDK. The command-line build was verified
with Temurin OpenJDK 8u502. A newer installed JDK may remain the system default;
the build only requires `JAVA_HOME` to point to JDK 8.

GWT 2.7.0 does not discover its translatable source correctly when run on the
JDK 21 installation used during validation. The wrapper therefore rejects Java
versions other than Java 8 with a direct error instead of failing later with a
misleading GWT module-inheritance error.

## One-time setup

1. Install the x64 JDK package from the [Eclipse Temurin 8
   releases](https://adoptium.net/temurin/releases/?version=8).
2. Set `JAVA_HOME` to that JDK installation. For the current PowerShell session:

   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.502.7-hotspot'
   ```

   Adjust the directory for the installed update. The scripts also accept a
   one-off `-JavaHome 'C:\path\to\jdk8'` argument.
3. Confirm that the selected runtime is Java 8:

   ```powershell
   & "$env:JAVA_HOME\bin\java.exe" -version
   ```

   The first line must begin with `openjdk version "1.8` or `java version "1.8`.

No Maven, Gradle, Ant, Eclipse plugin, Node.js, or global GWT SDK installation is
needed for the browser build. On first use, the scripts download version-pinned,
SHA-256-verified jars into `.tools/gwt-2.7.0`. That directory is ignored by Git.

If local PowerShell execution policy blocks repository scripts, invoke a build
without changing the machine-wide policy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1
```

## Production build

From the repository root, run:

```powershell
.\scripts\build.ps1
```

To select JDK 8 without changing `JAVA_HOME`, run:

```powershell
.\scripts\build.ps1 -JavaHome 'C:\path\to\jdk8'
```

The default output style is GWT `OBF`, matching the saved Eclipse deployment
compiler settings. `-Style PRETTY` or `-Style DETAILED` may be useful when
inspecting generated JavaScript locally.

The build compiles all five GWT browser permutations. Generated files are
written to:

```text
war/circuitjs1/
```

The host page is `war/circuitjs.html`, and the generated module bootstrap is
`war/circuitjs1/circuitjs1.nocache.js`. Existing static files in `war` are source
assets and are not deleted by the build. Generated output and compiler caches
are ignored by Git.

## Visible local preview

For the reliable visible player preview, first run a production build and then
start the detached server:

```powershell
.\scripts\start-preview.ps1 -Challenge led -Seed 3
```

The launcher waits for both the host page and compiled bootstrap, prints the
complete URL, and exits while the preview remains available. It is safe to run
again when the repository preview is already healthy. Stop only that recorded
preview with:

```powershell
.\scripts\stop-preview.ps1
```

`scripts/preview.ps1` remains the blocking server implementation. The detached
launcher stores validated process identity under the ignored `.tools/preview`
directory and defaults to port 8899. Use `-Challenge diode -Seed 3` for the diode
family.

Task 27's parallel family uses the same production preview lifecycle:

```powershell
.\scripts\start-preview.ps1 -Challenge parallel -Seed 3
```

The generated board is `PARALLEL_DUAL_INDICATOR` /
`DUAL_PARALLEL_BRANCHES`: two real VIN-to-GND branches (`R1` + `LED1` and
`R2` + `LED2`) share stable three-pad VIN and GND nets. The normal challenge
starts with a real `R1 OPEN` fault and the complaint `One indicator does not
light.`.

Task 36's deterministic RC-delay family can be previewed directly with:

```powershell
.\scripts\start-preview.ps1 -Challenge rc -Seed 3
```

It is a real CircuitJS charge-delay topology with stable `VIN`, `RC_OUT`, and
`GND` nets: `VIN -> R1 -> RC_OUT`, C1 and R2 from `RC_OUT` to `GND`, and a
healthy 100 nF ceramic C2 from `VIN` to `GND`. C1 is the original 33 uF / 16 V
radial electrolytic. The documented deterministic envelope is seed 0: 5 V,
R1 = 12 kOhm, R2 = 10 kOhm, C1 positive-lead open; seed 2: 9 V, R1 = 15
kOhm, R2 = 10 kOhm, C1 short; and seed 3: 12 V, R1 = 15 kOhm, R2 = 10 kOhm,
C1 positive-lead open. The real R2-only C1 discharge time constant is `.330 s`;
the effective healthy charge constants are about `.180 s` (seed 0) and `.198 s`
(seeds 2/3). The deterministic solver profile isolates external power for
`1.000 s`, then samples at `.100 s` and `.800 s` after power-on. R1 limits the
selected C1 short. An optional generic live-temporal solver contract advances
only bounded CircuitJS simulation time during ordinary RC UI frames, so the
player sees the actual capacitor voltage rather than a JavaScript timer curve.
These values and fault bindings are developer documentation, not normal-player
UI metadata.

To open the normal Quick Play route manually while preserving the detached
preview lifecycle, run:

```powershell
.\scripts\start-preview.ps1 -QuickPlay -OpenBrowser
```

The root player launcher adds `-BuildIfMissing`; direct developer preview
commands do not force a production build when the compiled bootstrap already
exists.

## Legacy GWT development server

The legacy GWT 2.7 DevMode workflow is retained for compiler development only:

```powershell
.\scripts\dev.ps1
```

Then open:

```text
http://127.0.0.1:8888/circuitjs.html
```

The web server listens only on localhost. This legacy compiler-development path
also uses port 9876 and is not the recommended player preview. To use another
web port:

```powershell
.\scripts\dev.ps1 -Port 8890
```

Stop the development server with `Ctrl+C` in its terminal.

## Automated browser regression verification

With the production preview running, execute the complete 15-route matrix in an
installed Microsoft Edge browser:

```powershell
.\scripts\verify-browser.ps1
```

The runner covers resistance, meter, challenge, replacement, and combined
challenge-plus-replacement verification for seeds 0, 2, and 3. It uses a fresh
headless browser profile per route, waits for the existing electrical verifiers,
captures JavaScript exceptions and failure-class console messages, applies a
finite timeout, prints one PASS/FAIL line per route, and exits nonzero on failure.

Run the normal-player UI regression separately with:

```powershell
.\scripts\verify-browser.ps1 -NormalPlayer
```

That flow uses browser mouse and keyboard input against live DOM/canvas geometry
to replace R1, verify solver-backed repair, remove the replacement, and measure
the two loose physical parts. Browser verification is separate from the JDK 8
production build and adds no dependency to the simulator.

The diode-family electrical verifier and real-input normal-player flow are:

```powershell
.\scripts\verify-browser.ps1 -Diode
.\scripts\verify-browser.ps1 -DiodeNormalPlayer
```

The first command covers seeds 0, 2, and 3. The second uses visible workbench
controls and canvas probe geometry to remove the open original D1, install a
healthy diode, verify functional repair, and confirm forward/OL polarity on the
separate loose physical parts. Every route uses a unique temporary Edge profile;
cleanup closes Edge through DevTools and removes that profile with bounded
retries.

Run the Task 27 electrical and normal-player paths with:

```powershell
.\scripts\verify-browser.ps1 -Parallel
.\scripts\verify-browser.ps1 -ParallelNormalPlayer -EvidenceDirectory docs/task-evidence/task-27
```

The parallel verifier uses solved CircuitJS currents for branch plausibility,
source KCL, branch voltage sums, shared-node identity, and R1 fault isolation.
It also drives the existing DC meter and a developer-only real-resistor
`1 kOhm || 10 kOhm` active-resistance fixture. The normal-player flow uses
browser input to read VIN, turn power off, remove R1, install a 1 kOhm catalog
part, repower, and verify both indicators. R2 and both LEDs are fixed physical
components in this family, though their pads remain probeable.

The seeded procedural PCB verifier compares deterministic geometry for all
generated families across seeds 0, 2, and 3:

```powershell
.\scripts\verify-browser.ps1 -Layout
```

Task 35(A)'s focused Quick Play verifier is:

```powershell
.\scripts\verify-browser.ps1 -QuickPlay
```

It checks the eligible-family selector/session seam with an injected
deterministic selection, explicit-route precedence, generic Finish Job
behavior for unrepaired and correctly restored boards, fresh reload state, and
normal-player privacy. The existing explicit LED, diode, and parallel smoke
routes remain the adjacent regression set; the historical full matrix is not
required for every launcher or Quick Play change.

Task 36's focused transient and stored-energy routes are:

```powershell
.\scripts\verify-browser.ps1 -Rc -StoredEnergy -Seeds 0,2,3
.\scripts\verify-browser.ps1 -RcNormalPlayer -PlayerSeed 3 -EvidenceDirectory docs/task-evidence/task-36
.\scripts\verify-renderer-boundary.ps1
```

The RC verifier proves ordinary `CapacitorElm` charge/discharge samples,
both deterministic fault types, typed physical/package identity, original-part
remove/reinstall fault preservation, incorrect low/high timing replacements,
the correct replacement, and clean solver/overlay state. The stored-energy
route verifies the `.25 V` readiness threshold and blocks OHM/continuity/diode
transactions—including noncanonical R1 pads on a charged C1 net—until real
discharge. `-RcNormalPlayer` drives the normal workbench through browser input:
it captures a charged C1, a material residual after the ordinary power-off
latency, visible `DISCHARGE` refusals in OHM/continuity/diode modes, natural
decay below `.25 V`, a fresh active measurement, the required
post-measurement re-discharge, and a real power-on rise. It supplements, but
does not replace, the required visible in-app browser validation.

Normal-player flows accept `-PlayerSeed <seed>` for generated-geometry checks;
their canvas clicks use the explicit developer-only geometry bridge rather than
fixed board coordinates:

```powershell
.\scripts\verify-browser.ps1 -LedNormalPlayer -PlayerSeed 3
.\scripts\verify-browser.ps1 -DiodeNormalPlayer -PlayerSeed 3
```

Task 26's procedural-layout verifier is:

```powershell
.\scripts\verify-browser.ps1 -Layout
```

It regenerates the three simple PCB families for seeds 0, 2, and 3, checks
same-seed fingerprints and cross-seed variation, validates pad escape
corridors, route quality, and the shared 9-pixel trace / 15-pixel centerline
minimum-clearance rule, and exercises the seed-3 LED cathode endpoint
regression. For the parallel family it additionally validates root-to-each-pad
VIN/GND routing for the two three-pad nets. The verifier also checks generated
silkscreen labels. The
production browser runner uses a separate bounded CDP receive window so these
deterministic checks finish in seconds without changing the route timeout.
The old broad three-consecutive keep-out/clearance early abort was removed;
layout generation remains bounded by its maximum attempt count and retains the
last candidate failure for diagnostics.

Task 28 adds topology-aware compact placement for all three generated families.
`TopologyPlacementGraph` consumes stable board components, pads, and nets before
any coordinates are chosen. Direct two-pad connections receive a strong
attraction, while multi-pad VIN/GND nets encourage a compact shared region.
Candidates are bounded, seeded, and scored using connected-pad distance, routed
length, bends, spacing, congestion, board area, unused area, silkscreen fit,
and same-net trunk reuse.

Every footprint now exposes a routing courtyard in addition to its body
keep-out. Unrelated copper is intentionally forbidden beneath the practical
mounted footprint because this simulator prioritizes visible, probeable copper
and topology readability over the fact that some real through-hole boards route
under components. The installed component's own trace may cross its courtyard
only through the exact pad's legal escape corridor. Courtyard and escape rules
are enforced by both A* pathfinding and `PcbBoardLayout.validateGeometry`, with
direct resistor and diode regressions.

The generator routes inside a virtual working area, places silkscreen, then
derives the final outline from courtyards, pads, copper, and labels. A 26-pixel
edge margin gives modest breathing room; the external parts tray is excluded.
`getCompactnessMetric()` and the largest-edge-margin check reject obviously
sparse layouts. Same-net A* steps prefer joining existing copper without merging
the physical pad targets.

Task 28 evidence is under `docs/task-evidence/task-28/` and includes LED seeds 0
and 3, diode seeds 0 and 3, and parallel seeds 0, 2, and 3. Pixel inspection
confirmed that the boards are content-bounded, direct branches are readable,
shared rails form coherent trunks, labels remain clear, and no copper appears
to disappear beneath a resistor, diode, LED, or connector courtyard.

## Manual application verification

After a production build, confirm that the GWT bootstrap exists:

```powershell
Test-Path .\war\circuitjs1\circuitjs1.nocache.js
```

The result should be `True`. Start the production preview, open the URL above,
and verify that the default LRC circuit, menu bar, simulation controls, and
animated circuit canvas appear. During setup validation, the production build
completed all five permutations, and the development page rendered in Chromium
without console or page errors.

## Legacy toolchain limitations

- GWT 2.7.0 is intentionally retained to match this fork and its Eclipse
  metadata. Upgrading GWT is a separate compatibility task, not part of routine
  setup.
- The GWT compiler accepts source levels through Java 7; the Eclipse project
  metadata is even older and declares Java 5. Keep code compiled by GWT within
  that language subset until the toolchain is deliberately upgraded.
- The source uses legacy GWT APIs and JSNI. Modern JDK or JavaScript interop
  assumptions should not be introduced casually.
- Super Dev Mode performs an initial browser-specific compile and is slower than
  a modern hot-reload toolchain.
- `war/shortrelay.php` requires a PHP-capable deployment server. The embedded
  development server does not provide that optional URL-shortening backend.
- The Electron directory is a legacy packaging shell, not the web build system.
  It expects a compiled `war` directory to be copied beneath `app`, requires its
  own Node/Electron setup, and is not currently wired into these commands.

Do not edit generated files under `war/circuitjs1`; change the Java source or
module public resources under `src/com/lushprojects/circuitjs1` and rebuild.

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

For the reliable visible player preview, first run a production build and then:

```powershell
.\scripts\preview.ps1
```

Open `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=led&seed=3`. This serves
the compiled production output and is the recommended command when you simply
want to see and interact with the current application.

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

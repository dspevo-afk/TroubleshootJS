# TroubleshootJS

TroubleshootJS is an in-development electronics troubleshooting game and
simulator. You receive a customer complaint, inspect an unfamiliar printed
circuit board (PCB), decide what to measure, and try to get the board working
again. The aim is to practice the reasoning of a repair bench, not to click a
component after the game has already told you which one is bad.

The current project is a playable technical slice rather than a finished game.
It runs in a browser and presents a generated one-sided PCB, a service
complaint, a multimeter, and a workbench where supported parts can be isolated,
removed, measured, replaced, and tested again.

## The troubleshooting loop

A typical job is meant to feel like this:

1. Read an incomplete complaint, such as an indicator that no longer works.
2. Inspect the board, its parts, pads, leads, and visible copper traces.
3. Measure useful points instead of probing everything at random.
4. Isolate a part or lead when an in-circuit reading is ambiguous.
5. Repair the board with a physically available replacement or other supported
   action.
6. Power it up, operate the relevant input, and retest the customer's symptom.

Finding the originally failed part is not, by itself, a win. A replacement
with the wrong value can remain electrically wrong, and a job is complete only
when the simulated board behaves correctly. The current UI provides a
customer-retest and `Finish Job` flow on the routes that support those
operations; the controls are intentionally family-specific where the real
test is different.

## What makes the simulation realistic

CircuitJS is the electrical engine underneath the board. TroubleshootJS adds
the complaint, physical PCB, probes, parts, faults, and repair workflow around
it.

- The visible board is tied to the same electrical connections as the
  simulated circuit. The renderer is not a decorative picture that invents
  readings.
- When practical, a player action changes the active CircuitJS circuit, and
  the solver calculates the new voltages, currents, timing, or component
  behavior. Meter readings come from that changed circuit rather than from a
  table of scripted answers.
- Wrong but physically compatible replacements are allowed to have their real
  consequences. They can leave a board nonfunctional, degraded, or stressed.
- Completion is based on functional behavior and customer retest, not on
  matching a hidden fault label or clicking a particular part.

This is an educational approximation of electronics troubleshooting. It is
not a professional PCB design tool, a manufacturing checker, a general SPICE
workstation, or a calibrated laboratory instrument.

## Learning by doing

TroubleshootJS sits between a game, an electronics learning tool, and a
troubleshooting simulator. Ideas such as current paths, polarity, stored
energy, and transistor/MOSFET switching become useful because you must use
them to interpret measurements and repair behavior, rather than only read
their definitions.

This is informal, self-directed learning, not an accredited course.

## What is playable now

Quick Play currently chooses from six bounded generated families:

| Family | Plain-language focus |
| --- | --- |
| LED indicator | A simple indicator path that should light when powered. |
| Diode-protected indicator | An indicator with a one-way protective diode in its path. |
| Parallel dual indicator | Two real indicator branches sharing supply and return, useful for comparing paths. |
| RC delay / stored energy | A resistor-capacitor circuit where charging, discharging, and waiting matter. |
| NPN low-side switch | A transistor-controlled load, with a control signal separate from the load path. |
| NMOS low-side switch | A MOSFET-controlled load, where gate voltage determines whether the load turns on. |

Across the supported families, the current player-facing workbench includes:

- seeded, reproducible challenges and generated one-sided PCB layouts with
  recognizable parts, pads, labels, and visible copper;
- DC voltage, resistance, continuity, and diode-test meter modes;
- probes on exposed PCB pads and component leads;
- power-state and stored-energy safety rules, including appropriate power-off
  behavior for active resistance/continuity/diode measurements;
- supported physical actions such as lifting a lead, removing a part to the
  loose-parts tray, measuring it out of circuit, and installing a replacement;
- family-specific customer operations and retest controls, including the
  current Quick Play `Finish Job` boundary where it is exposed; and
- solver-derived wrong-repair behavior. In the supported resistor stress
  scenario, excessive power can accumulate into a real secondary resistor
  failure rather than a warning-only message.

The exact parts and controls vary by family. Hidden fault identity, expected
answers, original private values, and developer verification evidence are not
normal player features.

## Bounded generation and PCB verification

TroubleshootJS deliberately does not create arbitrary random netlists and hope
that they happen to make good troubleshooting puzzles. Each current family
starts from a bounded electrical pattern with supported player operations and
fault types. A seed then selects sensible values and variants so that a
challenge can be reproduced for a given generator version.

Electrical generation and PCB generation are separate stages. The logical
board owns stable component, pad, and net identities; the physical PCB layout
consumes those identities rather than redefining the circuit. Where the current
seeded PCB layout generator is used, it places connected components and pads,
then routes the already-defined nets with deterministic bounded placement and
Manhattan/A* routing. Component keep-outs, routing courtyards, pad escape paths,
and geometry validation are used to keep unrelated copper from cutting through
parts or silently connecting the wrong things. This is intentionally a
simplified, readable one-sided PCB model rather than fabrication-grade
autorouting.

A generated board also does not become playable merely because the renderer
managed to draw it. The admission pipeline is intended to reject boards that
are electrically invalid, physically unserviceable, or effectively impossible
to diagnose with the controls given to the player. In broad terms, the current
checks do the following:

1. Build and solve the healthy CircuitJS circuit and confirm its expected
   baseline behavior.
2. Choose a compatible hidden fault candidate, apply it to the active
   simulation, and confirm that it produces a meaningful customer-facing
   symptom.
3. Check scenario compatibility and required unaffected behavior so a fault
   does not accidentally invalidate the rest of the job.
4. Check the physical fault locus and serviceability path: the relevant fault
   must correspond to something the player can observe, isolate, remove,
   reconnect, or replace through supported workbench actions.
5. Run diagnostic-solvability verification through real rendered probe
   endpoints and CircuitJS-backed meter modes, board power and functional
   inputs, temporal waits where needed, isolation actions, repair, and customer
   retest. The goal is to prove that the challenge can be worked from player
   evidence rather than from private fault metadata.
6. Admit the candidate to the normal `READY` state only after the applicable
   checks pass. Unsupported candidates remain developer-only or are rejected
   rather than being papered over with a scripted answer.

The seed makes a challenge reproducible for a given generator version. That is
useful for debugging, tests, bug reports, and discussing a particular board.
The long-term goal is to use these bounded building blocks to create richer
troubleshooting situations with more realistic ambiguity, purposeful
supporting circuitry, and additional repair choices. Broad procedural
composition is not part of the current player surface.

## Current status and limitations

This repository is actively being built. The six families above and their
workbench flows are the current proof that a complaint, a physical board, real
measurements, physical changes, and functional retest can share one electrical
simulation.

The current limitation is scope: this is a deliberately bounded workbench
slice, not yet a complete multi-job game or a general electronics simulator.
Some developer-only routes and verification tools exist to test the electrical
and physical contracts. They are not presented as normal gameplay or as clues
to the hidden fault. The project also retains legacy CircuitJS/Eclipse-era
files; the command-line Windows workflow below is the supported way to build
and run the current TroubleshootJS preview.

The PCB/workbench and service ticket are the real gameplay surface today. The
surrounding shell is incomplete: Resources and Settings are placeholders, and
Shop is a local mock rather than a connected store or authoritative inventory.

## Longer-term direction

The following are future work, not current player capabilities:

- player-created jumper wires;
- cutting and repairing PCB traces;
- an oscilloscope or frequency instrument;
- a configurable bench supply and current limiting;
- broad procedural composition beyond the bounded families;
- selectable difficulty profiles, mature scoring, and a larger service history;
- a complete multi-job game, progression system, or finished product shell.

## Why I started it

I started TroubleshootJS from a personal interest in electronics and in the
difference between reading a clean schematic and finding a fault on an
unfamiliar board. Existing circuit examples often begin with a known circuit
and a known question. A repair bench begins with symptoms and incomplete
information. I wanted a small game that makes the player decide what to
measure, whether a reading is being affected by another path, and whether a
repair really fixed the customer's problem.

## AI-assisted development

TroubleshootJS is developed extensively with AI coding agents. AI is used to
explore the legacy codebase, implement features, write tests, produce
documentation, and perform code review. This repository should not be read as a
claim that the maintainer personally hand-wrote the Java.

The maintainer's role is defining what the simulator should do, deciding
whether the electronics and troubleshooting behavior makes sense, setting
architecture and acceptance requirements, and rejecting or revising work that
does not meet them. AI output is not treated as correct simply because an
agent produced it; changes are built, tested, reviewed, and checked against
CircuitJS-backed behavior before they are accepted.

## Build and run on Windows

The current build is a Windows PowerShell workflow. Eclipse is not required.

### Requirements

- Windows PowerShell 5.1 or PowerShell 7+;
- a 64-bit JDK 8 installation (a JRE alone is not enough); and
- internet access for the first build, which downloads the pinned GWT 2.7.0
  jars from Maven Central.

Eclipse Temurin 8 is recommended. A newer Java installation may remain the
system default, but `JAVA_HOME` must point to JDK 8 for this project. For the
current PowerShell session:

```powershell
$env:JAVA_HOME = 'C:\\Program Files\\Eclipse Adoptium\\jdk-8.0.502.7-hotspot'
& "$env:JAVA_HOME\\bin\\java.exe" -version
```

The first line of the version output should begin with `openjdk version "1.8`
or `java version "1.8`. Adjust the path for the JDK 8 update installed on your
machine.

### Quick Play

For the normal player launch, double-click [`Start TroubleshootJS.cmd`](Start%20TroubleshootJS.cmd)
in the repository. It starts or reuses the detached production preview and
opens the browser directly in Quick Play. The server remains running after the
launcher exits. If compiled output is missing, it builds once when a JDK 8 is
available; it does not install Java for you.

### Production build

From the repository root:

```powershell
.\\scripts\\build.ps1
```

To select JDK 8 without changing `JAVA_HOME`:

```powershell
.\\scripts\\build.ps1 -JavaHome 'C:\\path\\to\\jdk8'
```

On first use, the script downloads and SHA-256-verifies the pinned compiler
artifacts into `.tools/gwt-2.7.0`. The compiled browser output is written to
`war/circuitjs1/`, with `war/circuitjs.html` as the host page. No Maven,
Gradle, Ant, Node.js, or global GWT installation is required for this browser
build.

If PowerShell blocks repository scripts, use a process-local execution-policy
override:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\\scripts\\build.ps1
```

### Production preview and explicit challenges

After a build, start a visible production preview with Quick Play:

```powershell
.\\scripts\\start-preview.ps1 -QuickPlay -OpenBrowser
```

You can also open a particular seeded family while developing or reproducing a
challenge:

```powershell
.\\scripts\\start-preview.ps1 -Challenge led -Seed 3 -OpenBrowser
.\\scripts\\start-preview.ps1 -Challenge diode -Seed 3 -OpenBrowser
.\\scripts\\start-preview.ps1 -Challenge parallel -Seed 3 -OpenBrowser
.\\scripts\\start-preview.ps1 -Challenge rc -Seed 3 -OpenBrowser
.\\scripts\\start-preview.ps1 -Challenge npn -Seed 0 -OpenBrowser
.\\scripts\\start-preview.ps1 -Challenge nmos -Seed 0 -OpenBrowser
```

The detached preview defaults to port 8899 and prints the URL it is using. To
stop the preview managed by the repository scripts:

```powershell
.\\scripts\\stop-preview.ps1
```

### Checks for contributors

With the production preview running, the main browser regression matrix is:

```powershell
.\\scripts\\verify-browser.ps1
```

The normal-player replacement flow can be run separately:

```powershell
.\\scripts\\verify-browser.ps1 -NormalPlayer
```

The repository also contains focused developer routes for the six families,
layout, stored energy, replacement, and stress behavior. These routes support
development validation; they are not a promise that their private evidence is
visible during normal play.

### Optional legacy developer path

The legacy GWT DevMode server is retained for compiler development only. It is
not the recommended player preview:

```powershell
.\\scripts\\dev.ps1
```

Then open <http://127.0.0.1:8888/circuitjs.html>. Stop it with `Ctrl+C` in its
terminal. The older Eclipse project files remain for compatibility with the
upstream layout, but they are not the primary setup path documented here.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for issue and feature-request
guidance. A useful bug report includes one concrete problem, an example circuit
or challenge when applicable, and steps to reproduce it.

## CircuitJS heritage and credits

TroubleshootJS is built around an adapted CircuitJS1 codebase. CircuitJS1 was
originally written by Paul Falstad as a Java applet and was adapted by Iain
Sharp to run in the browser using GWT. For the upstream hosted versions, see:

- [Paul Falstad's page](http://www.falstad.com/circuit/)
- [Iain Sharp's page](http://lushprojects.com/circuitjs/)
- [Paul Falstad's CircuitJS1 source](https://github.com/pfalstad/circuitjs1)
- [Iain Sharp's CircuitJS1 source](https://github.com/sharpie7/circuitjs1)

Thanks to: Edward Calver for 15 new components and other improvements; Rodrigo Hausen for file import/export and many other UI improvements; J. Mike Rollins for the Zener diode code; Julius Schmidt for the spark gap code and some examples; Dustin Soodak for help with the user interface improvements; Jacob Calvert for the T Flip Flop; Ben Hayden for scope spectrum; Thomas Reitinger, Krystian Sławiński, Usevalad Khatkevich, Lucio Sciamanna, Mauro Hemerly Gazzani, J. Miguel Silva, and Franck Viard for translations; Andre Adrian for improved emitter coupled oscillator; Felthry for many examples; Colin Howell for code improvements. LZString (c) 2013 pieroxy.

Thanks also to @Immortalin for the initial work applying Electron to
CircuitJS1.

## License

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

The complete license text is in [`COPYING.txt`](COPYING.txt).

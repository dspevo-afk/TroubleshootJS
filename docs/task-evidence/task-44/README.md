# Task 44 — immutable functional block contract

Approved starting baseline:
`a5e8efa775b52914a87269103a316c10669a5305`, clean
`codex/task43p-final-recovery`. The enclosing publication commit identifies the
accepted candidate; the file manifest below identifies the tested new code.
The owner authorized Task 44 followed by Task 45, with review/publication between
them, and an explicit stop before Task 46.

| Gate | Command / oracle | Result |
| --- | --- | --- |
| Focused actual-source contracts | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-block-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07` | PASS, exit `0`; nine groups / 247 assertions; temporary classes removed. |
| Final production build | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF` | PASS, exit `0`; JDK8 `1.8.0_502`, pinned GWT2.7, all five permutations; compile 37.114 s / link 1.487 s. |
| Whitespace / scope | `git diff --check`; tracked/untracked inspection | PASS; intended new contracts/harness and documentation only. Final staged inspection is required at publication. |
| Independent review | Fresh read-only Luna MAX `task44_review`, actual diff/new files, original requirement, call/dependency boundary and focused validation | PASS, no findings; independent JDK8 run exit `0`, nine groups / 247 assertions and exact cleanup; whitespace check exit `0`. |

The focused harness uses an explicit production source list with empty
source/class paths and JDK8 `-source 7 -target 7 -encoding UTF-8`. It therefore
compiles and executes these new classes even though the application does not yet
call them. It cannot silently pull in `CirSim`, `CircuitElm`, a live board or a
second physics engine. The source-7 bootstrap warning is recorded; compilation
succeeded on the selected JDK8. The worker's separate preliminary JDK21 compile
is diagnostic, not acceptance evidence.

Literal expected IDs prove schema/version/instance/kind/local separation, and
fixture-owned expected errors prove rejected malformed IDs, delimiters/Unicode,
duplicate declarations, missing/dangling references, terminal ambiguity,
contradictory roles and invalid attachments. Reversed declarations and optional
block insert/remove/reorder leave unchanged IDs intact. Input-list mutation,
nested-list mutation attempts and map-entry mutation attempts cannot change a
descriptor. Both NMOS-driver and resistor/LED-load fixtures validate without
constructing a board or solver. Structural component/terminal tuples also
distinguish legal dot-containing IDs that would collide under naive concatenation.

The production GWT build generated five `.cache.js` artifacts. Bootstrap SHA256:
`95f40c7b7b4d830170da7176d46fb92a4410798d05005bf788edddab722ef028`.
Private build-log SHA256:
`08241a77f5c613d1d6e7816a8d8e3785191f97592334c1da1f13b04e7b0e3dc7`.

| Tested new file | SHA256 (working bytes) |
| --- | --- |
| `BlockContractException.java` | `fe01b4a0d5d4686733aaeaeaee55a76dcaefd430f2e5147f4160f17869c4a2cc` |
| `FunctionalBlockDescriptor.java` | `c60f3ca02893c816b52889f8b629ee5168ad17da8b151faa481e522db852518f` |
| `BlockNamespace.java` | `4498cdd536ac8d6de693b3615cc3ec1434c4921a6ae26eda29eb984cf1cf0b2f` |
| `FunctionalBlockExamples.java` | `b5452469a7ca75f785544e77e9315c74f479baab379b732cb802702c48347744` |
| `tests/contracts/FunctionalBlockContractTest.java` | `986a5a54af816026253d4a8f65bb98a0ad1b9c25d21f7d1ff23c43f96b9bfd15` |
| `scripts/verify-block-contracts.ps1` | `6509eaf67f2bd7198e11112a6843419ac0d44d864383dd1e8b5f9ab15ae035a8` |

The first four paths are under `src/com/lushprojects/circuitjs1/client/`.
No pre-existing Java, GWT module, web, build, preview, isolation, generation,
measurement, fault, repair, PCB or physical-runtime implementation changed.
Accepted 43P runtime evidence retains its original identity and limits; it is
not a fresh Task 44 runtime-composition claim. Task 44 has no visible player-flow
change and needs no screenshots. No source-falsifier mutations were selected.

The existing GitHub workflow is absent from the default `master` branch; its
default-branch installation remains separate CI preparation. Local acceptance
does not claim a CI dispatch succeeded. Root owns staging, commit, normal push,
remote-SHA verification and the post-push Gmail attempt. Exact publication and
notification receipts are recorded after those actions, without a SHA-update loop.

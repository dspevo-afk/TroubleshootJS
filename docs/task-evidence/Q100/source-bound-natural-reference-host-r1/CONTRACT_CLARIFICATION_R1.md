# U R1 artifact-bound clarification

The inherited32MiB owner-executable read cap applies to that host-kernel path;
it is not a cap for GWT dependency jars. Existing qualified GWT inputs measure
32024115B user and38276750B dev. Bind each jar to its exact known size and SHA
before classpath admission, using the existing checked regular-file helper with
that exact size as the read maximum. Do not route the jar through the1MiB packet
reader. This is fixed current artifact identity, not expanded packet/class/native
budgets. Tool executables similarly admit their fixed SHA under bounded existing
helper calls. Keep context/packet1MiB and all frozen class/time/native limits.
No ad hoc unbounded read or alternate I/O owner. Root measured lengths from the
unchanged pinned inputs before any U adapter runtime gate.

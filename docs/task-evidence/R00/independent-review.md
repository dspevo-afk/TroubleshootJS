# R00 independent review

Result: **PASS**, after integrated review and focused corrective reviews on
2026-09-09. Reviewer: Curie (`integrated_review`), requested as
`gpt-5.6-luna`, MAX reasoning, default speed. The interface provided no speed
control. The reviewer authored neither implementation nor test oracles and
remained read-only. Root adjudicated findings and owns qualification.

The reviewer received the original R00 requirement, actual integrated diff,
retirement inventory, replacement coverage and available evidence. Review
covered current generation and resolved recipes, semantic identity and device
buses, explicit packages, declaration-driven materialization, all three A04
hardening areas, and retained electrical/physical/repair/lifecycle invariants.

Initial findings prompted removal of remaining slot/probe accessor aliases and
the redundant runtime-provider identity field. A fresh delta review accepted
the integrated repair, including exact issued-receipt identity, foreign/equal
input attempts, same-board context abort isolation and pre-mutation rejection.
There are no unresolved blockers.

Compiled execution subsequently exposed stale Task47 verifier expectations for
local net IDs and a historical fault-ID suffix. Focused reviews accepted the
replacement oracles: explicit current device buses; independent pad, component,
terminal and endpoint relationships; phase-specific construction progress; and
the unique fault candidate for the actual target component. Fault type/effect,
serviceability, real value-mutation target, runtime/slot/inventory ownership,
solver equations, wrong-binding negatives, cleanup and repair/retest checks
remain. Duplicate or absent fault ownership now rejects explicitly.

The last reviewed production change is in
`Task47AssemblyDeveloperVerifier.java`. Final source SHA-256:
`e36de0c9eb252cf11ebc460e191773f31b59e1cae46f688bc8d24a0646d0b8d3`.
Root reran native checks, the actual production build and compiled checks;
these are separate evidence, not work claimed by the reviewer.

The final pure report-reader delta was independently reviewed against the
actual `A02CorrectnessDeveloperVerifier` emitter and captured nine-route
corpus. Current `TSJ-A02-2` replaces the retired expected protocol. Five new
cases retain fail-closed handling for old/unknown, failed and unfinished
reports. The reviewer independently reran the focused PowerShell contract:
**114 assertions, exit 0, PASS**. No Java or compiled web bytes changed;
reuse of the final build/native/browser evidence is valid with this fresh
reader validation. No further production repair is required.

The reviewer also accepted the dependency audit for reusing the ordinary
player playthrough from source `519ddfd6...`: later source edits affect only
the developer-only Task47 verifier. Normal player routes do not execute it.
The same reasoning covers leaf checks stamped `d6519d4d...`. This does not
reuse earlier failing Task47 results.

The unchanged isolated CLI launcher remains **BLOCKED** by its 500 ms
ownership deadline. Built-in Browser execution of the current product corpus
and maintained report predicates is separate product evidence; neither that
route nor manual play certifies the failed wrapper. No timeout, ownership or
cleanup contract was weakened.

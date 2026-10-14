# Connected PCB packing, private placement9

Base `368f3b0dfeff026c352f671cd45333c8cdfda50e`, continuing branch
`codex/q60-faulted-profile-counts`. User asked for generated boards to resemble
real PCBs more closely, continuing the packing priority.

The planner's single-control candidate0 now uses a square demand-derived outline
and connected electrical-neighbor targets, with soft functional-group scoring.
Its demand multiplier is1.35. Single-group1/narrow3 and all repeated-control
candidates retain their geometry. No component IDs, seed predicates or fault
predicates select this behavior. Complete package/access/probe envelopes,
common80 isolation, six placements, three routes per policy and all original
work/time/area caps remain authoritative. Actual legal routes determine selection.

Among the six predeclared roots,42/root0 changes3401x1376 ->2521x1729:
4679776 ->4358809 area (-6.8586%). Its largest protected empty rectangle falls
376200 ->171200 (-54.49%), and protected-free fraction47.31% ->38.31%.
Those cells include both copper faces, full access, vias, silk and isolation;
free does not prove packable. Its same-region centre distance improves772.75
->728.57, while copper length increases28810 ->30670 (+6.46%). This is a packing
tradeoff, not a claim of improved routing, global optimality or manufacturability.
The other five representative layouts retain their accepted8 literal geometry.
Predeclared holdout roots1/7 retain their accepted8 layouts and actual legal routes.
This is eight examples, not qualification of all seeds or normal RB56 admission.

Fresh private requests use placement9. Explicit NARROW8 preserves saved8 before
cache/proof lookup; publicalpha5, generationepoch16 and sessionmodel1 remain.
Six literal saved8/7/6 geometries are independently checked. Identity tests cover
exact signed-long transport and distinct8/9 framing under the existing codec.

Trial1 changed narrow3's connected target and aspect1.35;44 lost its prior density
gain: FAIL, rejected. Trial2 retained aspect1.55 but used connected3: same44 FAIL,
rejected. Trial3 preserves3 and uses connected0/aspect1.35: six-root PASS,40 gains
4.25% area,42 unchanged. Its40 copper28180 is longer than published8's26750;
the progress comment saying it shortened routing was incorrect. Trial4 uses
square connected0:42 gains6.86%, other five stay exact. This is the chosen scope.
Trials1/2 briefly overlapped only the predecessor's remaining scratch cleanup,
after its JVM failure; final gates are serial. No relaxed oracle/budget.
Initial trial invocation selected the wrong pointer property and failed before
compilation; corrected to the existing tools.javaHome/tools.python fields.

Real-world layout reference: [TI's op-amp PCB placement guidance](https://e2e.ti.com/blogs_/archives/b/precisionhub/posts/the-basics-how-to-layout-a-pcb-for-an-op-amp)
emphasizes local decoupling and placement around device pins. This iteration
uses the existing connectivity model rather than asserting analog/EMC/thermal
or manufacturing guarantees. Future realism work includes explicit package
orientations and pin-local passive placement under the same endpoint contracts.

Final native, GWT, visible flow and cleanup results are recorded below after
completion. Full corpus, human U05, normal RB56/HARD release, modest-host proof
and inherited60 startup timing are NOT RUN/HELD as previously recorded.
No main merge/email; unrelated work is preserved.

## Final gates and actual player evidence

PASS maintained native11,220.815s including proven scratch cleanup: current1507,
saved6/7/8 literal1494/1504/1507, all720 route rankings23772, holdouts1/7 legal
routes and no area regression, save34966, identity109, dependency1166 and policy59,
compaction858. Review strengthened five unchanged-layout literals and retained
version9's existing53 whitespace oracle after that run; fresh maintained final
geometry PASS1513,80.273s including cleanup. Only that test file changed; consumed
production sources/scripts and other tests audited exact. GWT consumes production
sources, not contract tests. Final actual JDK8/GWT five PASS109.268s compilation /
1.513s link /113.036s wall, selected `E813280114D4B57B4235DE3051A0371C`.
`final-inputs.json` freezes all final source/scripts/contracts/served files;
`input-audit.txt` confirms no consumed inputs changed during final player proof.

Fresh preview PID88460 startup manifest matches all five actual DOM identity
fields. Initial URL omitted a generated predecessor and did not start the verifier;
it was corrected to the maintained LED predecessor route, not a retry of failed
RB56 generation. Actual42 cold PASS42.643s/519work/max1.536s,55 solver samples,
complete normal physical/D01 checks under the private session route. Actual
1280x720 default viewport top/Fit/bottom captures inspected. UI combo and real
canvas left red/right black on the moved U1 terminals: output essentially0 V,
input11.191 V, enable10.178 mV. Unrepaired customer FAIL expected. The early voltage
text lookup incorrectly expected V rather than pV; actual recorded near-zero
reading was retained, with no measurement/oracle change.

PowerOFF; real right-click REN/guarded Remove, Part22 loose. Actual Shop10kOhm
medium spacing/Part43, compatible Install as5Vcontrolrail/REN; powerON output
4.996V/customerPASS. Save under OFF and actual FileReader9 restore PASS39.750s/
519work, freshOwner/priorOwnerRetired; savedOFF preserved. PowerON/customerPASS
again, ordinary Close session. WholeflowPASS291.821s inclcold,249.178s retained
<360; all seven cleanup flags true, cleanupPendingfalse; warning/error logs[].
Three actual JPEG captures decode/nonblank/hash PASS; no image edits. Evidence is
source-guided engineering input, not human U05 or proof of a generic CDP wrapper.

Root integrated code/replay/test/diff/input/three-image/actual-flow review PASS.
No independent agent review. No budget, assertion or electrical truth weakened.
Both native scratch directories cleaned by maintained verifier. Task preview
positively stopped by exact recorded PID/start-time owner and port8899 released;
owned tab closed, no viewport override used. Exact current browser download
creation/hash/reparse ancestry checked and removed; byte-exact saved file retained
with packet-local -text attribute. Reused toolchains preserved. Existing active
worktree five untracked entries and Desktop seven tracked edits preserved.
No main merge/email. Remaining42/other gaps, rotations and explicit pin-local
placement remain next; inherited60 timing/full population and admission limits
remain as before. Normal commit/push verification is recorded in the handoff.

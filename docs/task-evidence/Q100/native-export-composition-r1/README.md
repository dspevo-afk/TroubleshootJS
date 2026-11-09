# Actual R7 to current V2 export composition

Accepted for the bounded synthetic lifecycle in [PROOF_R1.json](PROOF_R1.json).
The actual current setup, root constructor, metadata, R7 work plan and receipt
ticket now pass directly through the existing V2 exporter on one native Meter.
No production/helper bytes, API, hook, budget, seed, epoch or oracle changed.
Normal assembly, full-D semantics and genuine producer authority remain separate.

The fresh fixture uses four distinct empty child maps, a controlled receipt and
synthetic prior-group credit. It creates no OriginalArm, ArmResult behavior,
electrical graph, Session or route. Its receipt is synthetic evidence, and its
empty children cannot establish an actual cohort or restored customer behavior.

## Behavior and independent expectations

The caller leaves the actual R7 plan unfinalized and the prepared receipt ticket
unused. `exportV2` owns their one-shot finalization and verification. It binds the
same body, snapshot, Meter and start; counts, writes and reads back `result.json`;
verifies the exact `op-1.jsonl`; closes one real group; admits and writes an exact
marker; and checks the closed publication state. The directory initially contains
only its receipt. See the [contract](EXPORT_COMPOSITION_CONTRACT_R1.md) and
[Source findings](SOURCE_FINDINGS_R1.md).

The Python oracle spells out the literal prior, thirteen-key result, all 34 work
equations and fourteen-key marker. It independently encodes compact printable
ASCII JSON plus LF, calculates the result digest and compares every actual byte.
It uses native group-start and group-measure clock facts, with bounded returned
elapsed time; other counters and metrics are calculated independently. Serialized
result/marker fields and Java-produced goldens do not supply expected values.

The [reference](REFERENCE_FREEZE_R1.json) was generated before oracle/canary code
from literal Source data and standard JSON. Driver drafting had already begun.
Its invented Python clock vector yields 3,168 result bytes and a 619-byte marker;
those clocks are never injected into Java. Actual native clocks yield different
bytes, which the independent oracle reconstructs per run.

| Counter | Before export | Successful export delta | Receipt-failure delta |
| --- | --- | --- | --- |
| Units | `28712 + 10P + 6` | `5C + 3` | `5C + 1` |
| Copied entries | `618 + 2P` | `2R + 32 + M` | `2R` |
| Copy chunks | 23 | `2C + 2` | `2C` |
| Closed wrapper groups | 2 | 1 | 0 |

P is independently derived from the actual receipt path; R includes final LF,
C=`ceil(R/4096)` and M is the exact independently encoded marker length. Setup and
ticket preparation are already in the baseline and are not charged again.

For actual P=11, the successful case has R=3,175, M=630, fee8, total28,836 units,
7,652 copied entries and27 chunks. Its three closed wrapper groups plus one
synthetic prior group give marker totalGroups4. All 34 work equations, returned
metrics, byte counts, digests and native measure maxima pass.

The second fresh case changes the receipt after result write, preserves its size
and restores mtime. It reaches `FINAL_SHA_MISMATCH` after readback and before the
digest/marker fees, export close or publication. Its 3,175 result bytes match the
independent expectation; no marker or metrics are returned. It retains28,834 units,
6,990 copies,25 chunks and two closed wrapper groups, with the partial export
group open at6 units. The failed Meter is discarded without `finish`; no accepted
export group is invented. Streams and owned files are cleaned separately.

## Final validation

| Gate | Result | Operation / wrapper ms |
| --- | --- | --- |
| Pure fixture and pre-code reference | PASS | 378.11 / 806.4859 |
| Initial independent oracle tests | PASS13 | 389.9046 / 633.7588 |
| Fresh final-source JDK8 target7 build | PASS863 Sources,2,486 classes,11,953,557B,all major51 | 5134.084 / 5375.0968 |
| Actual native smoke | PASS1, before suite | 427.3412 / 664.436 |
| Independent reader on actual smoke | PASS | 352.5819 / 583.6853 |
| Actual native receipt-tampering case | PASS1 expected rejection | 429.3414 / 676.5271 |
| Independent reader on actual failure | PASS | 351.1444 / 581.3181 |
| Final public oracle tests | PASS13 | 564.5078 / 811.4343 |
| Public reader on retained actual smoke | PASS | 442.162 / 673.5501 |
| Public reader on retained actual failure | PASS | 346.6311 / 584.0863 |

Ten real selected Python reader controls pass: two reference positives and eight
expected exit2 rejections for path count, result bytes, marker bytes, counters,
duplicate JSON keys, oversized input, altered Source and an existing system
junction. The junction is read-only; no link was created or deleted. Three actual
main protocol controls reject invalid mode, nonempty root and extra argument
with exit1, preserving the controlled marker. Expected exit1/2 controls are not
native producer success. The [case manifest](CASE_MANIFEST_R1.json),
[pure gate](PURE_ORACLE_GATE_R1.json) and [final gates](FINAL_GATES_R1.json) bind
the results.

The public entry points were run from their published directory because
their Source-loading path changed. The first staged whitespace check then failed:
three Python files had an extra final empty line. Their original raw bytes and
earlier gates remain retained. The correction trims terminal empty lines only;
the parsed AST is identical, and the adjacent Source pin metadata is updated.
Fresh final public tests13, two reference reader positives, eight expected reader
rejections and both actual native-output readers pass. See the
[final staged gate](FINAL_STAGED_GATES_R1.json),
[reader controls](STAGED_READER_GATE_R1.json) and
[correction record](STAGED_SOURCE_CORRECTION_R1.json). The earlier FINAL_GATES
file describes the initial candidate. Final audit reauthenticated the unchanged
native Source, tools, jars, classes, actual compiler/native argv and process
receipts. Native cases were not rerun. The [build inventory](BUILD_INVENTORY_R1.json) retains the exact closure;
only fresh classes and two pinned GWT jars enter the native classpath.

## Review, history and limits

Root reviewed the integrated Source/API/order/ledger/clock/oracle/loader and
cleanup boundaries. Leaf Source mapping and author reviews support that scope;
no separate independent native run is claimed. Before execution, Root corrected
an unbounded harness result read to a capped no-follow stream, and changed the
oracle loader to execute the exact authenticated raw Source rather than reread
Source or consume candidate bytecode caches. Raw released code is retained. An
old uncompiled canary-design note has only its recorded hash/size; no complete
unreleased history is claimed. Source-only author notes describe their original
stage, not the later final gate status. Public uncompiled notes have terminal
blank lines trimmed; originals remain. [History and limits](SOURCE_HISTORY_AND_LIMITS_R1.json)
records these boundaries. No unexpected executed Source/native gate failed.

Operation time includes fixture, exercise and stdout restoration. Cleanup time
separately covers fact capture, bounded result read, identity checks and owned leaf
removal, as clarified [here](TIMING_CLARIFICATION_R1.md). System.out is restored
in `finally` on both paths. Actual marker capture is a bounded test PrintStream;
it does not certify normal-host transport. Cleanup errors force FAIL. Both case
roots are empty; selected children terminated and their owned process roots/logs
are absent. Separate process cleanup duration was not measured. Source/classes/
evidence and prior uncertain resources remain preserved; no persistent process
or link was launched.

The failed-ticket five-second elapsed assertion is measured. That source path
skips the later exporter deadline check after the ticket throws; it does not
guarantee this bound for every failure. No bound was enlarged or error suppressed.
The32KiB result limit is only this small fixture's hex transport bound; global
result1MiB/marker656 and all existing caps remain.

Replay uses Python `-I -B`, optimize0, and the exact Source/control manifests.
The public pure tests accept `--reference-root` pointing to these three reference
artifacts. The independent CLI takes `--case`, `--input-json` and independently
derived `--expected-path-entries`. Native replay additionally needs the exact
private Source pack and pinned tool/jar closure; these files do not imply a
standalone full-D host.

GWT/visible Player checks are NOT APPLICABLE: no production or visible flow
changed. Electrical/population qualification is NOT RENEWED. No actual prior DATA,
old DATA class or protected/held-out DATA was accessed in this scope. Historical
normal20 reads and the UNCERTIFIED protected-unread boundary remain. Full30 is
EXCLUDED; HARD/PSYCHOTIC unavailable/unrun. Playable100=0,beta HELD; genuine
original then exact NULL/OBSERVE remains UNSPENT_GATED.

Next is semantic current-result child construction, followed by full-D,
normal assembly and actual host/lifecycle qualification.

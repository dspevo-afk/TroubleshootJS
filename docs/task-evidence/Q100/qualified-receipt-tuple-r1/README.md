# Qualified receipt tuple: finite metadata prerequisite accepted

The current metadata owner now compares the qualified receipt tuple with the
separate byte-count/SHA expectations supplied to receipt binding. The new call
runs after priorShape and before copying metadata, binding a receipt or replacing
the body slot. It checks exact LinkedHashMap kind, path op-1.jsonl, positive
Integer count within the binding's8MiB cap, and matching lowercase64-character
receipt/expected/operation1 SHA values. This proves metadata agreement only.

The successful helper prepays81 actual Meter units and adds no copied entries,
copy chunks or explicit group operation. Existing preparation fees/copies remain
in place. Every read/control is charged before observation; invalid fields stop
at the contract's declared debit. String.equals with a known String receiver
combines path/type or SHA equality safely in one bounded comparison. The8MiB
limit comes from the binding, not the input helper's positiveInt check.

| Gate | Result and scope |
| --- | --- |
| Pure Source API | PASS:12 private and12 final-public tests; pinned input, negative admission, call placement and exact inverse conservation |
| Final-source JDK8 | PASS:2 Sources,8 classes/30118 bytes,2567 dependencies/12292116 bytes, major51 throughout, no shadows |
| New native helper | PASS:113 cases/679 assertions;81-unit success, field/type/null/missing/boundary/overflow/hex-index errors, exact short-circuit debits, no copies or tree mutation |
| Admission/propagation | PASS:outer reads occur at2/80 units; lookup exceptions propagate;640 real charge/finish groups reject the next admission before reading any metadata |
| Retained native | PASS:60 body-correlation cases/210 assertions and63 codec cases/94 assertions, with exact unchanged summaries |
| JVM argument | Expected exit1 and exact CANARY_ARGUMENTS; not a positive run |
| Independent review | PASS:separate bounded Source/ledger review; root integrated Source/canary review |
| Final receipt/input audit | PASS:7 selected receipts reauthenticated,6 exit0 and1 exact argument rejection; source/class inputs unchanged |

Native case counts were independently calculated from Source before execution:
3 success,105 field rejections,2 propagated outer lookups,1 admitted-read guard,
1 real group-admission denial and1 null-Meter case. The105 rejections are6
prior/map,4 path,11 byte-count,80 SHA and4 operation cases. All64 invalid hex
positions have their own15+i debit. NATIVE_EXPECTED_R1.json freezes the ledger.

Selected operation times: compile790.76ms, new native369.52ms, retained body361.88ms,
retained codec568.68ms. The proof records operation and wrapper totals separately.
Termination and owned task-root/log absence are proven; isolated cleanup duration
is unmeasured. The selected host wrapper and inherited dependency classes were
reauthenticated from the accepted body-correlation closure. Only the metadata
class was replaced; the two retained canary Sources/classes are unchanged.

The compiler command used pinned JDK8 with `javac -source 7 -target 7 -encoding
UTF-8 -Xlint:-options -classpath <frozen-current-closure> -sourcepath <empty>
-d <output>` and the emitted CurrentAssemblyMetadata plus QualifiedReceiptTupleCanary.
Native no-argument main classes were QualifiedReceiptTupleCanary,
CurrentBodyCorrelationsCanary and CurrentV2CanonicalCodecCanary; the argument
rejection targeted the first. Python Source commands are in the component README.
The private host is not a standalone clean-clone build command.

Source input9970/a98ea929... becomes12486/49f9930a4e1d8aed297236dd4ee742233c73cffcded9f495fd5815f58d11687b.
The2443-byte/ad5cb569... fragment and one call are the only insertions. Their
removal recovers every original byte, preserving all other methods and payload
construction. Exact pins and public bytes are in SOURCE_MANIFEST_R1.json.

Limits: nested lookup exceptions at units4/6/11 cannot be injected while keeping
the required exact LinkedHashMap class; normal reads are covered by successful
debits and field failures, with propagation also reviewed in Source. Positive
prepare/binding and receipt-file execution were NOT RUN. The new native cases
call only the narrow helper with ordinary synthetic maps and the actual Meter.
No factory, graph, Session, actual/fake arm owner, positive original DTO, routing,
exportV2 or genuine envelope ran. Equal metadata does not establish source or
receipt authenticity, exclusive borrowed lifetime or whole-result validity.

Production/GWT/Player are NOT APPLICABLE; electrical/population evidence is NOT
RENEWED; CI is NOT RUN. Existing budgets, exact seeds/epochs, independent oracles
and data exclusions remain. Full30 EXCLUDED, HARD/PSYCHOTIC unavailable/unrun,
old normal20 not reread, protectedUnread UNCERTIFIED, playable100=0 and beta HELD.
Prior failures/scratch and uncertain historical resources remain preserved. A
pre-existing Notepad window was used for read-only Source inspection; no new
Notepad process was launched, and existing tabs/windows remain open.

Proof SHA256:a59bc063ea6c1049b7e47ead1235e8841fa357636a6beb2fe8acc6c89f33395d.
Publication and notification state is recorded in PUBLICATION_R1.json.
Next unstarted implementation depends on the bounded remaining-identity/caller
authority Source audit; no genuine-envelope admission is implied.

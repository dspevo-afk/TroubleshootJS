# Current arm read meter oracle R1

This packet checks only the bounded synthetic list and observation-metadata
component described by CONTRACT_R1.md. It does not qualify an owner result,
the producer, a genuine arm, or normal host acceptance.

## Pinned inputs

- Contract: 8,463 bytes, SHA-256
  7c1cac18553d9bd10e70cb7d40bc75b05acd84211132f20cb1c1886805fb3156.
- Metering fragment: 14,125 bytes, SHA-256
  5f5a569b10f3579de8f700e84496c8762450531e986e732e338ec79133c60222.
- Native source fixture: 37,690 bytes, SHA-256
  33e993e3697b0f46d6a276841ab5bb0d820f39a95e382079ab821dd24c1fc65a.

The CLI accepts only a reference root containing the pinned contract and
reference/CurrentArmReadMetering.java.fragment. It authenticates the four
Python/note files against ROOT_ORACLE_SOURCE_R1.json before compiling and
executing the oracle's authenticated bytes with optimization disabled.

## Independent ledger basis

armListRead charges immediately before List.get, so even the invalid index
case has one unit before its expected IndexOutOfBoundsException.
containsMetered charges one unit before size(), then prepays 2n+2 before
iteration. For a stable list this covers iterator creation, n+1 hasNext
calls, and n next calls. test at Study lines 972–974 charges once before
each comparison Guard.

For a three-item list, the tariff is 10 units for a first hit, 12 for a last
hit or miss, and 3 for an empty list. A last hit stops on the third
hasNext/next pair; a miss makes the fourth hasNext call. Group-guard
cases prefill 640 closed one-unit groups. The next admission fails before
get() or size() respectively, leaving 640 groups and no list observations.

The validator fixtures use one-item requested/traversed lists when callbacks
are present. The count reader records every CountingMap.get, including
lookups inside already-paid Guards; those reads do not receive another debit.
The common zero-callback NO_FAILURE case has 8 newly metered reads and 10
existing charges, totaling 18 units. The zero-callback quality-limit path has
10 newly metered reads and 14 existing charges, totaling 24. For callback
cases, the two contains traversals use the one-element tariff. The
FIRST_NON_FOUND case also retains its existing one-unit
terminalReasonEquals comparison copy.

The callback NO_FAILURE path adds five read debits, five bounds/total Guard
debits, and two one-item contains calls (six units each), totaling 40. The
quality-net path starts from the 24-unit quality-limit path, adds that same
callback work, then adds the suffix check, comparison copy, and eight proof
Guards while skipping the quality-limit-only terminal-null Guard, totaling 55.
FIRST_NON_FOUND adds five state-specific read debits, nine Guard debits versus
the NO_FAILURE branch, the comparison copy, and a third one-item contains
call, totaling 61.

The successful case ledger and read-counter vectors are literal expectations
in current_arm_read_oracle.py; they are derived from the admitted Study
control flow and each synthetic input shape. A Guard-contained map read still
appears in mapReads, even though its enclosing Guard pays the debit. Repeated
reads such as the nested lastNetOrdinal lookup in the quality-proof Guard are
counted independently. The synthetic validator map-read counters are 11 for
zero-callback NO_FAILURE, 15 for callback NO_FAILURE, 13 for zero-callback
quality-limit, 26 for FIRST_NON_FOUND, and 22 for quality-net callbacks.

## Reader boundaries

The bounded reader rejects duplicate JSON keys, floating-point/nonfinite
values, excess nesting/visits/key length, unknown or reordered fields, booleans
in integer counters, changed file contents, reparse components, and failed
close operations. It checks full signatures within each path/handle API,
compares path and handle identity without ctime, and checks directory identity
without sibling-content timestamps. A successful component summary is not
evidence of a successful producer or genuine arm.

ORACLE_FAILURE is an unexpected-error sentinel emitted as REJECTED with
exit code 2. It is never a valid case-specific negative control. The root gate
must require the exact known code for each negative control.

## Execution status

No unit tests, CLI cases, native/JVM code, or producer cases were executed by
this worker. Root owns source freezing, execution, and the actual reader
canaries. The component output retains actualPositiveOwnerResult: false,
normalHostAcceptance: false, and genuineEnvelope: UNSPENT_GATED.

# Original DTO meter oracle R1

This reader authenticates only the frozen R1 contract and the admitted
`OriginalDtoMetering.java.fragment` copy under `reference/`. It verifies the
synthetic meter canary's fixed fields, counters, payload markers, and bounded
clocks using independent literal expectations.

The 154-unit recipe prices 25 reflective reads, 14 map reads, eight container
allocations, the two-unit baseline, and 105 precharged copy entries. It is a
source-derived synthetic recipe, not evidence of a positive `originalDto`
result or an actual owner result. The only capture cases reject before any
owner or pad-proof object is supplied. A pass therefore does not enable host
acceptance or spend the genuine envelope.

The command-line reader runs under `python -I -B` at optimization level zero,
authenticates its four frozen Python files, rejects duplicate JSON keys, and
uses bounded no-follow file reads with stable file identity checks. Ancestor
directories are checked for reparse points and identity changes without
treating unrelated sibling creation as a directory identity mutation.
Unexpected internal failures return `REJECTED` with the explicit
`ORACLE_FAILURE` sentinel and exit 2. Root validation must not count that
sentinel as a successful case-specific negative control.

Before execution, Source review corrected draft closed-group groupUnits expectations:
finishGroup retains the last group units; only completed groups increment groups.
The pure synthetic CASE_FACTS already stated these retained values and are unchanged.

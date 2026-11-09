# Qualification stdout console framing correction

The first actual normal-only qualifier ran AFTER durable exclusive journal
da979300... at2026-11-09T04:45:28Z. It authenticated the fixed20 roles, reached
resources/normalization, and failed NORMALIZER_QUALIFICATION_STDOUT_FRAMING.
Actual prior acceptance FAIL; no context/Java generated; native producer0 and
genuine original+NULL/OBSERVE UNSPENT/GATED. Preserve original output root,
attempt/failure artifacts, config/manifest/journal, raw receipts and all failures.

Root inspected ONLY the already-authorized role D_PRIOR_QUALIFICATION_STDOUT_TXT
through the pinned reader/role SHA. Actual bytes812, SHA256
a08ca3ccfd3390d39ec37314a86f42f1a1b33c1b6d8104e417447e95f47d1d52,
exactlyone CRLF terminator/noBOM, a single valid JSON object with expected keys.
The current _validate_qualification_summary calls _json_load(line=True), whose
JSONL contract forbids CR. Existing _console_json accepts a single LF or CRLF
console record. Reuse that existing adapter at this ONE call site. Keep exact
812 bytes, role authentication/hash,1024 cap, fields/types, SHA joins, independent
prior/P07/PAD/work/original counters, elapsed repr/cleanup and every oracle.
Do not relax JSONL parsing globally or add fallback/version guessing.

SOURCE-only implementation first in AB/normalizer-crlf-r1. Owner may edit ONLY
normalize_prior.py and new test_normalizer_qualification_stdout.py. All other
current source/tests were copied for root gates and stay unchanged. Leaves do
not execute/spawn/stage/push/mail or open role bodies/journals. Synthetic tests
must exercise the full qualification-summary validator using independently
constructed role hashes/expected fields: exact-size CRLF positive, LF supported
by existing adapter, malformed/multirecord/no-terminator/oversize/changed claims
negative. Preserve existing strict JSONL guards and all earlier tests.

Root owns execution, actual role diagnosis, source proofs, caller pin migration,
fresh output/config/SOURCE/journal authority and any later actual read. Never
reuse the failed nonempty output root or rerun actual qualifier automatically.
Publish only a qualified coherent correction with normal commit/push/one Gmail.
Old D descriptor/result/generated-input DATA, old builders/CLIs, manifest-row
following and protected/held-out bodies remain denied. Full30EXCLUDED;
HARD/PSYCHOTIC unavailable/unrun; historical protected unreadUNCERTIFIED;
fullD/H1 unqualified/playable100=0/betaHELD; caps/seeds/epochs/CircuitJS truth unchanged.

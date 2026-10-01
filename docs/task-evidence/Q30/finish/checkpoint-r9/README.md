# Q30 paused checkpoint evidence

This packet is preliminary evidence; Q30 remains disabled and final release gates are pending.
`REPORT.md` describes the run inventory and failures. `manifest.json` records the
original, sanitized and packaged hashes of all 3,481 artifacts. `ARCHIVES.json`
records the two transport archives and their verified byte comparisons.

Extract both archives into this directory (or a new task-owned scratch directory
with these metadata files) to restore the ledger-relative `files/` tree. Their
member sets are disjoint: the full77 cold run and all retained earlier attempts,
profiles, build/canary receipts and input identities. No failed attempt is counted
as a passing population member. Personal filesystem prefixes were sanitized.
The original task-temp sources and raw profiles remain necessary for the pending
source-attempt reconstruction audit and are not replaced by this receipt packet.

The retained-attempts archive also preserves the three exact package metadata
files so Git line-ending conversion cannot affect the original ledger hashes.

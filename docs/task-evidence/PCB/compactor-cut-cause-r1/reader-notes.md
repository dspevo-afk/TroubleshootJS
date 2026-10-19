# Offline cut-cause reader

`assess.py` validates the frozen unit/routes phase receipts and, for routes,
the accepted15 cut-event stream. It checks all 14 roots in protocol order, pairs
axis calls by candidate, verifies event lifecycle and dimension arithmetic, and
requires each observed case to match all 42 sealed non-time baseline fields.
The per-root summary counts outcomes, rejection reasons, and proposed axis
removals. Proposed removals do not establish accepted overall shrinkage.

The reader consumes protocol SHA-256
`62f7f85d4511130b9d60c0b12e4ac1a73a7c23064ad1ec40b1779083198c040d`, unit suite
manifest SHA-256 `f62a967c3b44f5ee42be87ea60a32f7852344bc32c20584e2170f23d548741b1`,
routes suite manifest SHA-256
`7a62dd11beb86db19580bc9599302820d8f4dc5531b212c051b114d725592fd4`, and
accepted15 baseline SHA-256
`12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816`. The
baseline binds to
`docs/task-evidence/PCB/post-selection-edge-trim-r1/final-native.txt` with source
SHA-256 `db538fe4cca9aff18e33a974a6afa53ecb2fc244c7827c65201cc5408b65330d`.
Protocol and manifest content, root order, phase markers, and baseline row
schema are checked as part of those hash gates.

Run the synthetic reader canaries with:

```powershell
python -X utf8 -B docs/task-evidence/PCB/compactor-cut-cause-r1/check_assessment.py
```

The frozen canary receipt records 49 cases: pinned inputs 4, event census 1,
lifecycle 1, event order 9, arithmetic 5, outcomes 5, case proof 3, phase
receipts 11, cleanup boundary 6, and timing boundary 4. These are synthetic
offline reader checks only. The phase CLI, native unit phase, native routes
phase, and native runtime evidence are NOT RUN.

The evidence scope is limited to the 14 retained accepted15 roots. It makes no
all-seed, alternative-cut, smaller-board, changed-policy, or fresh electrical,
player, manufacturing, or solver proof claim.

# Plan-4 D01 one-edge browser evidence

This bundle preserves two actual compiled-browser D01 runs and fresh current strict-reader checks. The evidence is narrow: one 20-package case and one 40-package case, not the declared corpus or normal-player acceptance. Runner input/source manifests, process/runner identity, case reports, cleanup receipts and path-sanitized wrappers are retained per seed; provenance records original source hashes and archived artifact hashes. Browser profiles were not copied.

| Seed / packages | Application result | Host runner result | Cleanup | Independent readers |
| --- | --- | --- | --- | --- |
| 10387 / 20 | PASS; terminal reached and report matched; 53.228 s | PASS; no state-attribute read errors | PASS; 0.934 s, server stopped, no owned survivors | `check_d01.py --require-warm`: 10 cold+warm pairs PASS; canaries: 64 corruption/rejection + 7 power-context PASS |
| 10014 / 40 | PASS; terminal reached and report matched; 208.587 s | FAIL / limited: six 10-second timeouts waiting for the HTML state locator during host polling, despite the maintained runner's aggregate outcome being PASS | PASS; 1.354 s, server stopped, no owned survivors | `check_d01.py --require-warm`: 10 cold+warm pairs PASS; canaries: 62 corruption/rejection + 7 power-context PASS |

For the 40-package case, the report and its exact D01 proof are independently accepted, but the host polling timeout remains a failed/limited runner gate. Do not count its app PASS or reader PASS as a clean browser-run/timing PASS. The timeout count and all other host diagnostic counts are retained in `provenance.json` and `seed-10014-40pkg/result.json`.

Original source report SHA-256 values are `ca1811390765cb2e72906b5b1c235501b87b606f0eb4d1b2722c5f63e8a19b39` (seed 10387) and `f2d8433fcd634cfe3cd4e8f17252b1b0bdcac4bb1bd4b81292aefedeac2742c6` (seed 10014). Stored reports are deterministic gzip-compressed sanitized JSON; source and stored hashes are both listed in each seed's provenance. Reader receipts capture exact stdout, command shape, wrapper hash, reader hash and exit code.

The [source timing analysis](source-analysis.md) traces the six 40-package poll errors to long synchronous private route-construction calls as the likely phase-level cause, while documenting why the missing per-error timestamps prevent exact attribution. The strict D01 readers prove the declared cold/warm fault-pair evidence in these two assembled receipts. They do not certify the host polling implementation, broad corpus coverage, normal admission, player-visible repair flow, or production timing margin. The 40-package host polling timeout needs diagnosis before any clean 40-package timing claim.

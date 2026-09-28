# Owned-zero-row packager notes

`package_owned_zero_row.py` is prepared for the root to run after review. It writes only `docs/task-evidence/Q30/owned-zero-row-scan/`; it does not run gates or edit production source. Example invocation:

```powershell
python -B C:\path\to\scratch\package_owned_zero_row.py --repo C:\path\to\TroubleshootJS
```

It refuses to create a packet unless the exact eight-row sequence is complete with four passing pair receipts, R2 focused gates pass, final exact-index gates pass, and root runtime equality reports 1,525 inputs / 392 WAR files with no mismatches. It preserves the failed R1 result, retains raw application reports byte-for-byte in gzip, scrubs metadata, checks private paths in stored and decompressed bytes, and inventories the packet. The timed R2 source and staged-index source are recorded as distinct snapshots. It reuses the committed `validated-factor-input` source baseline and stores only the small changed-source overlays. Q30 acceptance is not claimed.

This script was not executed here; no packet, build, or gate was run.

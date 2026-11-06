# P7 final host closure check R4

Frozen before the corresponding outer-driver check and before any selected host
canary or captured-board case. This strengthens the final acceptance check while
retaining the original120s cleanup cap and all R1/R2 case/work semantics.

The host's internal cleanup receipt measures parent work, worker work, post-source
verification and output preparation before final receipt emission. The selected
outer driver separately measures the full host process through proven exit. Root
acceptance requires that entire host wall time <=120000ms. This includes source
preflight, all children, final JSON/output, process shutdown and transport overhead.
The separately recorded full-host bound therefore conservatively covers the last
receipt write and close without a self-referential final timestamp. It is stricter
than the internal phase sum; neither measurement substitutes for the other.

The bounded selected outer-process receipt remains private. Sanitized qualification
records its digest, exact wall time, exit and termination result. A timeout,
nonzero exit, missing process identity/termination or full wall above120s rejects
publication. This is a real process measurement, not a fabricated reserved group.

# R8: private actual resistance-reader diagnosis

The R7 seed-1022 failure has post LCD/readiness DISCHARGE. This does not distinguish
early refusal from a solver sample later discarded. Add an actual reader-entry
counter adjacent to existing solver diagnostics; capture per-attempt delta, actual
source current, transaction text and each current readiness capability only on
failure with both debug and active verifier enabled. Normal failures keep existing
safe LCD/readiness text. No numeric/control/physics/guard/retry behavior changes.

Literal solver fixtures prove numeric/over-range reader entry and early refusal
without entry. Native15 current suites then actual final-source JDK8/GWT5 and
compiled A07 precede a targeted -1022 diagnosis canary. It is not an ordered
cohort, protected regression or HARD decision. All previous failures retained.
Original90s/640work/5s limits unchanged. No energy reset, masked NaN or guard change.

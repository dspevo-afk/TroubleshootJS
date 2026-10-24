# R4: compiled bounded LU access and current calibration

Pre-capture scope: actual R3 CPU sampling identifies the bounded trailing kernel
as 23.119s self time in a 133.118s capture (includes initialization and idle).
This is diagnosis, not admission or a promised speedup. R2 failures remain.

One production change adds direct primitive-array access in the existing proved
bounded trailing kernel only. Distinct rows, finite magnitude <=1e100, dimension
<=128, reciprocal/scaling validation and unsafe/aliased fallback retain their
owners. Each cell retains ascending pivot order and its multiply/subtract/store.
No solver replacement, skipped accepted steps, changed waits, physics, budgets,
cohort substitution, HARD policy or public admission changes.

Validation: maintained A07/LU native independent Crout/failure-prefix/cleanup
contracts and current resistance/diagnostic native suites; actual final-source
JDK8/GWT; compiled A07 uses the same independent oracle with the script branch;
then recapture development -1017/-1067/-1022 in that order with original
90,000ms/640work/5,000ms-unit caps and three natural 1s OFF waits. Freeze build
and source before capture. Regression remains 9007199254741579/-1004/-1028 and
must not run before a successful development set and frozen profile decision.
No human trial is repeated. Normal HARD/catalog/public saves remain held.

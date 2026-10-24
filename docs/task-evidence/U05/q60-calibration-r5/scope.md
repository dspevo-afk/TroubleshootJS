# R5 bounded scalar reuse

R4 passed correctness but failed the unchanged current development cohort. This
candidate changes only its script trailing kernel to gather up to eight ascending
nonzero upper entries into scalars, read each distinct lower row/factor once per
group, and execute the exact1–7-column tail. It allocates no numeric buffers and
retains each cell's pivot order and multiply/subtract/store. No physics, readiness,
wait, guard, deadline, work, cohort, difficulty or public gate changes.

Acceptance requires fresh native13/current final-source JDK8/GWT and compiled A07
independent Crout/failure-prefix/cleanup checks, frozen same development cases and
caps, plus actual compiled CPU diagnosis if the performance candidate progresses.
R2/R3/R4 failures remain. Regression stays unrun until a complete development
candidate and frozen profile decision. No repeat human trial.

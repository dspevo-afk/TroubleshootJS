# Bounded formation-oracle review

Result: **PASS, source only**. The candidate is frozen by
`build-final-21-inputs.json` on base
`16a922b2b757dfcd1cbc3a0230ab2b97ac2fbb50`. Only
`U04CatalogMutationVerifier.java` differs from the GWT17 source manifest.
The earlier production review's unchanged-input boundary remains recorded in
`source-review-17-input-audit.json`.

Root inspected the integrated comparator, canaries, successful formation and
failed compensation paths. A separate read-only reviewer found no material
blocker. Same-class NPN dumps retain exact tokens 0–6 and 9–10; only finite
junction-voltage tokens 7–8 may differ. Malformed and nonfinite values reject.
Other elements retain exact dumps. Owner, graph, endpoint, attachment, part,
inventory, serial and fault comparisons remain exact. The intentional formed
geometry and simulation-time exceptions remain bounded to successful formation.

Failed mutation compensation still calls the original raw `state.equals()`
oracle. The failure diagnostic skips permitted transient differences and reports
the first disallowed change. Literal canaries use the actual GWT19 observations
and independently altered coordinates, flags, polarity, gain, model, malformed
shape and nonfinite values. They do not use the production dependency normalizer
as their expected result.

GWT20 retained a compile failure from an unsupported `java.util.StringTokenizer`
import. Root selected the existing package-local tokenizer and removed a raw
equality shortcut for NPN validation. GWT21 then passed all five permutations in
72.202 seconds, linking in 1.093 seconds; 1,133 build inputs were unchanged and
392 web files were frozen. Runtime Alpha and final Q30 acceptance are separate
gates; this source review does not certify them.

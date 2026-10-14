# Finite-copper compaction trial — rejected

**Disposition: `REJECTED_UNCHANGED`.** The private13 trial added a bounded copper-aware compaction pass after the existing placement and routed compaction. It kept the existing validation and work limits. The independent metric assessment found no change on any of the ten predeclared seeds (`-1017`, `-1004`, `-1022`, `0`, `17`, `42`, `1`, `7`, `5`, `14`): outline area, largest protected void, copper length, and via count all had zero delta. There was no strict improvement, so the trial failed its declared acceptance rule.

The focused native run passed 11 Java suites in **134.7516518 seconds**, including cleanup. The frozen-input audit passed with **1,440/1,440** paths unchanged. Native lifecycle, owner/profile, codec, and saved12 literal checks ran within that focused set; this does not certify the trial as a product build or player flow. The two-pass compactor was restored to the accepted source. Earlier packing and mask failures remain separate evidence.

The full matrix and independent oracles were not run. There was no trial13 JDK8/GWT build, browser or visible player test, production Save13/FileReader13 flow, or publication. These limits and the `FAIL` metric result stand despite the focused native `PASS`.

Evidence: [scope](scope.json), [focused native output](focused-native.txt), [timing](focused-timing.json), [frozen-input audit](focused-input-audit.json), [metric assessment](focused-metric-assessment.json), and [rejection record](rejection.json).
